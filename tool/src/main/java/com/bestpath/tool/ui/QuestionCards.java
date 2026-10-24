package com.bestpath.tool.ui;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import com.bestpath.tool.transport.TransportCandidate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * Questions shown on the main map as small cards, each with a leader line to what it is about. Two sources:
 * <ul>
 * <li>Another program asks through the {@link RemoteLink}: "Yes" makes the change exactly as your own click would (hide
 * a sub-region, which is tinted, or reject a transport candidate); "No" only closes the card; "I built it" (transport
 * cards only) closes it and tells the asker the transport was made by hand. A question may instead carry its own
 * answer buttons and a box to type in; they only write the answer down, as {@code CHOICE <n> <label>} or
 * {@code TYPED <text>}.</li>
 * <li>After every data build the tool itself makes cards of four kinds: sea channels ({@link ChannelCards}), "go to
 * nearest" place kinds ({@link PlaceKindCards}), sea charting spots ({@link ChartingSpotCards}) and game update
 * findings ({@link CacheCheckCards}). Their answers are saved as changes, so a card is not made again.</li>
 * </ul>
 * Every answer is appended to {@code cache/remote-link/answers.txt}; open questions are kept in
 * {@code questions.json} so they survive a restart. Only the first few cards whose target is in view are shown. A
 * question whose id starts with {@code r<region>-} belongs to that region's sub-region check: when its last question is
 * answered, the check is set to Complete (by the person who answered). Swing thread only.
 */
final class QuestionCards implements MapView.Overlay
{
	/** The card's kind: what "Yes" does, or which buttons of its own it has. */
	enum Action
	{
		/** Nothing; the answer is only written down. */
		NONE,
		/** Hide the sub-region under the tile (like a click with the Hide tool). */
		HIDE,
		/** Reject the transport candidate (object id on the tile), like Delete. */
		REJECT,
		/** A sea channel to keep or delete: Keep and Delete, both saved as a {@code SEA_CHANNEL} change. */
		CHANNEL,
		/**
		 * A "go to nearest" place kind to keep or remove: Keep and Remove, and a box to correct the name; saved as a
		 * {@code PLACE_KIND} change.
		 */
		PLACE_KIND,
		/**
		 * A sea charting task done from a boat or on land: Boat and Land, the automatic guess marked; saved as a
		 * {@code CHARTING_SPOT} change.
		 */
		CHARTING_SPOT,
		/**
		 * A game update finding that needs a look: the buttons of its kind ({@link CacheCheckCards}); saved as a
		 * {@code CACHE_CHECK} change.
		 */
		CACHE_CHECK
	}

	/** One question. Plain data, serialised to JSON. */
	static final class Question
	{
		String id;
		Action action = Action.NONE;
		int x;
		int y;
		int plane;
		int objectId = -1;
		String text = "";
		/** Answer buttons in place of Yes / No / I built it (null or empty: those three); nothing changes, only the answer is written down. */
		List<String> choices;
		/** A hint for a box you can type an answer in (null: no box). */
		String typeHint;
		/** {@link Action#CHANNEL}: the channel's key ({@code SeaChannels.key}) and its points (packed x << 14 | y), drawn on the map. */
		String channel;
		int[] points;
		/** {@link Action#PLACE_KIND}: the map icon id, the kind's name and every place of it (packed {@code Tiles}), dotted on the map. */
		int icon = -1;
		String kindName;
		int[] places;
		/** The map icon's picture (not saved: put back by the next {@link #syncPlaceKinds}). */
		transient java.awt.image.BufferedImage picture;
		/** {@link Action#CHARTING_SPOT}: the task's number and the guess (boat or land); {@link #places}: its spot and the nearest sea. */
		int task = -1;
		boolean guessBoat;
		/** {@link Action#CACHE_CHECK}: the finding's key, its kind ({@code CacheCompare.Kind} name) and what it is about. */
		String checkKey;
		String checkKind;
		String checkRef;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	static final Color LEADER = new Color(255, 0, 200);
	static final Color CARD = new Color(255, 248, 205);
	/** The colours of the cards on screen at once, in turn (the first is the cards' pink). */
	private static final Color[] SLOTS = {LEADER, new Color(0xff7b00), new Color(0x00b4d8), new Color(0x2dc653)};
	/** At most this many cards on the map at once (many cards at once make the map slow). */
	private static final int MAX_SHOWN = 4;

	private final EditorContext ctx;
	private final MapView view;
	private final com.bestpath.tool.review.RegionReview review;
	private final java.util.function.Supplier<String> author;
	private final File file;
	private final File answers;
	private final List<Question> open = new ArrayList<>();
	private final Map<String, JPanel> cards = new HashMap<>();
	/** Card offset from its target tile on screen, per question (so cards don't sit on each other). */
	private final Map<String, int[]> offsets = new HashMap<>();
	/** The colour each card's border has now (set only when it changes). */
	private final Map<String, Color> cardColour = new HashMap<>();
	private final QuestionCardPanels panels = new QuestionCardPanels(this::answer);
	private final HidePatches hidePatches;
	/** Whether the cards were brought up to date once since the tool started (the map then goes to the first card once). */
	private boolean synced;

	QuestionCards(EditorContext ctx, MapView view, File linkDir, com.bestpath.tool.review.RegionReview review,
		java.util.function.Supplier<String> author)
	{
		this.hidePatches = new HidePatches(ctx);
		this.ctx = ctx;
		this.view = view;
		this.review = review;
		this.author = author;
		this.file = new File(linkDir, "questions.json");
		this.answers = new File(linkDir, "answers.txt");
		try
		{
			if (file.isFile())
			{
				List<Question> read = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8),
					new TypeToken<List<Question>>(){}.getType());
				if (read != null)
				{
					read.forEach(this::addCard);
				}
			}
		}
		catch (Exception ignored)
		{
			// a broken file only loses the open questions; the question can be asked again
		}
	}

	// --- bringing the cards of each kind up to date (after every data build) ---

	/**
	 * One Boat / Land card per sea charting task not on open sea and not answered yet ({@code answered}: task numbers with
	 * a {@code CHARTING_SPOT} change), in task order; cards answered some other way are taken back. Run after every data
	 * build. Returns the number added.
	 */
	int syncChartingSpots(List<com.bestpath.tool.extract.ChartingSpots.Spot> spots, Set<Integer> answered)
	{
		Set<String> now = new HashSet<>();
		for (com.bestpath.tool.extract.ChartingSpots.Spot s : spots)
		{
			if (!s.onSea && !answered.contains(s.task.getNumber()))
			{
				now.add(ChartingSpotCards.ID + s.task.getNumber());
			}
		}
		boolean wasEmpty = open.isEmpty();
		removeOpen(Action.CHARTING_SPOT, q -> !now.contains(q.id));
		int added = 0;
		for (com.bestpath.tool.extract.ChartingSpots.Spot s : spots)
		{
			String id = ChartingSpotCards.ID + s.task.getNumber();
			if (!now.contains(id))
			{
				continue;
			}
			Question q = ChartingSpotCards.question(s);
			if (isOpen(id, q.text))
			{
				continue;
			}
			remove(id); // older words: made again
			addCard(q);
			added++;
		}
		// the charting cards in task order, in the turns they have among the open cards
		sortInTheirTurns(Action.CHARTING_SPOT, Comparator.comparingInt(q -> q.task));
		endSync(added > 0 && wasEmpty);
		return added;
	}

	/**
	 * One Keep / Remove card per "go to nearest" place kind not answered yet ({@code decided}: map icon ids with a Keep
	 * or a Remove in the changes), by name; cards of kinds answered some other way or no longer named are taken back.
	 * Run after every data build. Returns the number added.
	 */
	int syncPlaceKinds(List<com.bestpath.tool.extract.MapPlaces.Card> kinds, Set<Integer> decided)
	{
		Set<String> now = new HashSet<>();
		for (com.bestpath.tool.extract.MapPlaces.Card k : kinds)
		{
			if (!decided.contains(k.icon))
			{
				now.add(PlaceKindCards.ID + k.icon);
			}
		}
		boolean wasEmpty = open.isEmpty();
		removeOpen(Action.PLACE_KIND, q -> !now.contains(q.id));
		int added = 0;
		for (com.bestpath.tool.extract.MapPlaces.Card k : kinds)
		{
			String id = PlaceKindCards.ID + k.icon;
			if (!now.contains(id))
			{
				continue;
			}
			Question q = PlaceKindCards.question(k);
			int at = indexOf(id);
			if (at >= 0)
			{
				Question was = open.get(at);
				if (!was.text.equals(q.text) || was.picture == null)
				{
					// older words, or the picture lost at a restart: made again in place
					replaceAt(at, q);
				}
				continue;
			}
			addCard(q);
			added++;
		}
		// the place kind cards by name, in the turns they have among the open cards (one put back after an undo goes
		// to its place, not the end)
		sortInTheirTurns(Action.PLACE_KIND, Comparator.comparing(q -> String.valueOf(q.kindName).toLowerCase(java.util.Locale.ROOT)));
		endSync(added > 0 && wasEmpty);
		return added;
	}

	/**
	 * One card per finding of the newest game update comparison {@code f} that needs a look and has no answer yet
	 * ({@code decided}: the keys of the answered ones), in the report's order; cards of other comparisons, or answered
	 * some other way, are taken back. Run after every data build and after a comparison. Returns the number added.
	 */
	int syncCacheChecks(com.bestpath.tool.update.CompareFile f, Set<String> decided)
	{
		Set<String> now = new java.util.LinkedHashSet<>();
		List<com.bestpath.tool.update.CompareFile.Item> wanted = new ArrayList<>();
		if (f != null)
		{
			for (com.bestpath.tool.update.CompareFile.Item i : f.looks())
			{
				if (!decided.contains(i.key) && i.x >= 0 && now.add(CacheCheckCards.ID + i.key))
				{
					wanted.add(i);
				}
			}
		}
		boolean wasEmpty = open.isEmpty();
		removeOpen(Action.CACHE_CHECK, q -> !now.contains(q.id));
		int added = 0;
		for (com.bestpath.tool.update.CompareFile.Item i : wanted)
		{
			Question q = CacheCheckCards.question(i, f.from, f.to);
			if (open.stream().anyMatch(o -> o.id.equals(q.id) && o.text.equals(q.text) && cards.containsKey(o.id)))
			{
				continue;
			}
			int at = indexOf(q.id);
			if (at >= 0)
			{
				// older words, or the card lost at a restart: made again in place
				replaceAt(at, q);
				continue;
			}
			addCard(q);
			added++;
		}
		// game update cards first (they are about data already in use): the map shows the first few cards on screen and
		// goes to the first one
		List<Question> checks = new ArrayList<>();
		for (Question q : open)
		{
			if (q.action == Action.CACHE_CHECK)
			{
				checks.add(q);
			}
		}
		open.removeAll(checks);
		open.addAll(0, checks);
		endSync(added > 0 && (wasEmpty || checks.size() == added));
		return added;
	}

	/**
	 * One card per sea channel not decided yet (no Keep or Delete in the changes, {@code decided}) and not already asked,
	 * in map order (region by region); cards of channels the sea no longer has, or decided some other way (the Del key),
	 * are taken back. Run after every data build. The map goes to the first card when cards were added and none was
	 * open. Returns the number added.
	 */
	int syncChannels(com.bestpath.core.sea.SeaGraph g, Set<String> decided)
	{
		if (g == null)
		{
			return 0;
		}
		Set<String> now = new HashSet<>();
		List<Integer> order = new ArrayList<>();
		for (int c = 0; c < g.channelCount(); c++)
		{
			now.add(com.bestpath.core.sea.SeaChannelsBuilder.key(g.channelPoints(c)));
			order.add(c);
		}
		boolean wasEmpty = open.isEmpty();
		removeOpen(Action.CHANNEL, q -> !now.contains(q.channel) || decided.contains(q.channel));
		order.sort(Comparator.comparingInt(c -> ChannelCards.mapOrder(g.channelPoints(c))));
		int added = 0;
		for (int c : order)
		{
			Question q = ChannelCards.question(g, c);
			if (decided.contains(q.channel) || isOpen(q.id, q.text))
			{
				continue;
			}
			// an open card with older words is made again in place (same id, same turn in the list)
			int at = indexOf(q.id);
			if (at >= 0)
			{
				replaceAt(at, q);
				continue;
			}
			addCard(q);
			added++;
		}
		// channel cards in map order, after any other open card (one put back later goes to its place, not the end)
		open.sort(Comparator.comparingInt(q -> q.action != Action.CHANNEL || q.points == null ? Integer.MIN_VALUE : ChannelCards.mapOrder(q.points)));
		// the map goes to the first card when cards were added to none, and once after the tool starts (cards saved
		// from last time are open but nowhere in sight)
		endSync(added > 0 && wasEmpty);
		return added;
	}

	/** Takes back the open cards of kind {@code action} that {@code stale} picks. */
	private void removeOpen(Action action, Predicate<Question> stale)
	{
		for (Question q : new ArrayList<>(open))
		{
			if (q.action == action && stale.test(q))
			{
				remove(q.id);
			}
		}
	}

	/** True when a card with this id and these words is open. */
	private boolean isOpen(String id, String text)
	{
		return open.stream().anyMatch(q -> q.id.equals(id) && q.text.equals(text));
	}

	/** The open card's place in the list, or -1. */
	private int indexOf(String id)
	{
		int at = -1;
		for (int i = 0; i < open.size(); i++)
		{
			at = open.get(i).id.equals(id) ? i : at;
		}
		return at;
	}

	/** Makes the card at place {@code at} again from {@code q} (same id), keeping its turn in the list. */
	private void replaceAt(int at, Question q)
	{
		remove(q.id);
		addCard(q);
		open.add(at, open.remove(open.size() - 1));
	}

	/** Sorts the open cards of one kind, each into one of the turns those cards have among all open cards. */
	private void sortInTheirTurns(Action action, Comparator<Question> order)
	{
		List<Integer> slots = new ArrayList<>();
		List<Question> sorted = new ArrayList<>();
		for (int i = 0; i < open.size(); i++)
		{
			if (open.get(i).action == action)
			{
				slots.add(i);
				sorted.add(open.get(i));
			}
		}
		sorted.sort(order);
		for (int i = 0; i < slots.size(); i++)
		{
			open.set(slots.get(i), sorted.get(i));
		}
	}

	/** Saves; goes to the first card when {@code goToFirst}, or once after the tool starts; repaints. */
	private void endSync(boolean goToFirst)
	{
		save();
		if (goToFirst || !synced && !open.isEmpty())
		{
			showFirst();
		}
		synced = true;
		view.repaint();
	}

	// --- asking, taking back, counting ---

	/** Adds a question (replacing one with the same id) and shows it if it is the first. */
	void ask(Question q)
	{
		remove(q.id);
		addCard(q);
		save();
		if (open.size() == 1)
		{
			showFirst();
		}
		view.repaint();
	}

	/** Takes a question back without an answer (e.g. the asker found the answer another way); true if it was open. */
	boolean withdraw(String id)
	{
		boolean was = open.stream().anyMatch(q -> q.id.equals(id));
		remove(id);
		save();
		showFirst();
		view.repaint();
		return was;
	}

	int count()
	{
		return open.size();
	}

	/** How many open cards are of this kind (UiCheck). */
	int count(Action action)
	{
		int n = 0;
		for (Question q : open)
		{
			n += q.action == action ? 1 : 0;
		}
		return n;
	}

	/** Moves the main map to the first open question of kind {@code a}. */
	void showFirst(Action a)
	{
		for (Question q : open)
		{
			if (q.action == a)
			{
				view.setPlane(q.plane);
				view.goTo(q.x, q.y, Math.max(view.getViewport().getScale(), 8));
				return;
			}
		}
	}

	/** Moves the main map to the first open question. */
	void showFirst()
	{
		if (!open.isEmpty())
		{
			Question q = open.get(0);
			view.setPlane(q.plane);
			view.goTo(q.x, q.y, Math.max(view.getViewport().getScale(), 8));
		}
	}

	private void addCard(Question q)
	{
		open.add(q);
		finishCard(q, panels.build(q));
	}

	private void finishCard(Question q, JPanel card)
	{
		card.setVisible(false);
		view.add(card);
		cards.put(q.id, card);
		// the four cards on screen at once go round their target (right-up, left-up, right-down, left-down) so they don't cover each other
		offsets.put(q.id, slotOffset(offsets.size()));
	}

	// --- answers ---

	/**
	 * Answers a card: makes the change of a "Yes" (or of a kind's own button), writes the answer down and closes the
	 * card. {@code word}: YES, NO, BUILT (the transport was made by hand), a kind's word (KEEP, DELETE, BOAT ...),
	 * {@code CHOICE <n> <label>} or {@code TYPED <text>}.
	 */
	private void answer(Question q, String word)
	{
		String done = word.equals("YES") ? act(q) : saveOwnAnswer(q, word);
		try
		{
			Files.writeString(answers.toPath(), String.format("%s %s %s%s%n", Instant.now(), q.id, word, done),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException ignored)
		{
			// the change itself is in the change log
		}
		remove(q.id);
		save();
		completeRegionCheck(q.id);
		showFirst();
		view.repaint();
	}

	/**
	 * Saves the answer of a card with buttons of its own kind (place kind, charting, game update, channel) as a change;
	 * a note for the answers file ("" for other cards).
	 */
	private String saveOwnAnswer(Question q, String word)
	{
		if (q.action == Action.PLACE_KIND)
		{
			Edit e = PlaceKindCards.answer(q, word, panels.typedName(q.id));
			addInOneStep(e);
			return e.on ? " removed" : e.name != null ? " kept, named " + e.name : " kept";
		}
		if (q.action == Action.CHARTING_SPOT)
		{
			Edit e = ChartingSpotCards.answer(q, word);
			addInOneStep(e);
			return e.on ? " boat" : " land";
		}
		if (q.action == Action.CACHE_CHECK)
		{
			ctx.edits.beginGesture();
			if (word.equals("TAKE_BACK"))
			{
				for (Edit e : ctx.edits.active())
				{
					if (e.id.equals(q.checkRef))
					{
						ctx.edits.revert(e);
						break;
					}
				}
			}
			for (Edit e : CacheCheckCards.answer(q, word))
			{
				ctx.edits.add(e);
			}
			ctx.edits.endGesture();
			return " " + word.toLowerCase(java.util.Locale.ROOT);
		}
		if (q.action == Action.CHANNEL)
		{
			// Keep or Delete: a SEA_CHANNEL change either way, so the channel's card is not made again
			Edit e = ChannelCards.answer(q, word);
			addInOneStep(e);
			return e.on ? " deleted" : " kept";
		}
		return "";
	}

	/** Adds one change as its own undo step. */
	private void addInOneStep(Edit e)
	{
		ctx.edits.beginGesture();
		ctx.edits.add(e);
		ctx.edits.endGesture();
	}

	/** When the last open question of a region's sub-region check was answered: that check is set to Complete. */
	private void completeRegionCheck(String id)
	{
		int region = regionOf(id);
		if (region < 0 || open.stream().anyMatch(o -> regionOf(o.id) == region))
		{
			return;
		}
		try
		{
			com.bestpath.tool.review.RegionReview.Check c = review.get(region, com.bestpath.tool.review.RegionReview.Category.SUBREGIONS);
			if (c.status == com.bestpath.tool.review.RegionReview.Status.NEEDS_WORK && c.note.startsWith("Waiting on hide question"))
			{
				// keep the rest of the note (e.g. "25 tiles at ... are kept")
				String note = c.note.replaceFirst("^Waiting on hide questions?", "Hide questions answered");
				review.set(region, com.bestpath.tool.review.RegionReview.Category.SUBREGIONS,
					com.bestpath.tool.review.RegionReview.Status.COMPLETE, note, author.get());
			}
		}
		catch (IOException ignored)
		{
			// the answer itself is saved; the check can be ticked by hand
		}
	}

	/** The region of a question id like {@code r4923-a}, or -1. */
	private static int regionOf(String id)
	{
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^r(\\d+)-").matcher(id);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	/** Makes the change for a "yes"; returns a note for the answers file. */
	private String act(Question q)
	{
		if (!ctx.hasData())
		{
			return " (no data: nothing changed)";
		}
		switch (q.action)
		{
			case HIDE:
				return " hidden " + hidePatch(q) + " sub-region(s)";
			case REJECT:
				for (TransportCandidate c : ctx.transports.getCandidates())
				{
					if (c.getObjectId() == q.objectId && c.getX() == q.x && c.getY() == q.y && c.getPlane() == q.plane)
					{
						ctx.select(ctx.candidateSelection(c), null, false);
						ctx.deleteSelected();
						return " rejected";
					}
				}
				return " (candidate not found)";
			default:
				return "";
		}
	}

	/** Hides every sub-region of the question's patch not hidden yet; how many. */
	private int hidePatch(Question q)
	{
		boolean[] hidden = ctx.effectiveHidden();
		int n = 0;
		for (int[] t : hidePatches.patch(q))
		{
			int comp = ctx.graph.componentAt(t[0], t[1], t[2]);
			if (comp < 0 || (hidden != null && comp < hidden.length && hidden[comp]))
			{
				continue;
			}
			Edit h = new Edit();
			h.type = Edit.Type.HIDE;
			h.x = t[0];
			h.y = t[1];
			h.plane = t[2];
			h.on = true;
			h.factSize = ctx.graph.componentSize(comp);
			ctx.edits.add(h);
			n++;
		}
		return n;
	}

	private void remove(String id)
	{
		open.removeIf(q -> q.id.equals(id));
		hidePatches.forget(id);
		panels.forget(id);
		JPanel card = cards.remove(id);
		if (card != null)
		{
			view.remove(card);
		}
	}

	private void save()
	{
		try
		{
			file.getParentFile().mkdirs();
			Files.writeString(file.toPath(), GSON.toJson(open), StandardCharsets.UTF_8);
		}
		catch (IOException ignored)
		{
			// open questions are lost on restart only
		}
	}

	// --- painting ---

	@Override
	public void paint(Graphics2D g, MapView v)
	{
		if (v != view)
		{
			return;
		}
		MapViewport vp = v.getViewport();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int shown = 0;
		for (Question q : open)
		{
			JPanel card = cards.get(q.id);
			double sx = vp.screenX(q.x + 0.5);
			double sy = vp.screenY(q.y + 0.5);
			// only the first few cards whose target is on screen; the rest wait their turn
			boolean here = q.plane == v.getPlane() && shown < MAX_SHOWN
				&& sx >= 0 && sx <= v.getWidth() && sy >= 0 && sy <= v.getHeight();
			if (!here)
			{
				if (card.isVisible())
				{
					card.setVisible(false);
				}
				continue;
			}
			shown++;
			// each card on screen in its own colour, its leader line and its channel too, so cards side by side can be
			// told apart
			Color colour = SLOTS[(shown - 1) % SLOTS.length];
			paintTarget(g, v, vp, q, colour);
			showCard(g, v, vp, q, card, colour, shown - 1);
		}
		if (shown < open.size())
		{
			paintMoreLabel(g, v, shown);
		}
	}

	/** What the card is about: the HIDE patch tinted, the channel's way, or the places dotted. */
	private void paintTarget(Graphics2D g, MapView v, MapViewport vp, Question q, Color colour)
	{
		if (q.action == Action.HIDE)
		{
			hidePatches.paint(g, vp, q);
		}
		if (q.action == Action.CHANNEL && q.points != null)
		{
			channelLine(g, vp, q.points, colour);
		}
		if ((q.action == Action.PLACE_KIND || q.action == Action.CHARTING_SPOT) && q.places != null)
		{
			placeDots(g, v, q.places, colour);
		}
	}

	/** Puts the card (the {@code slot}-th on screen) in place with a border in its colour, and draws its leader line. */
	private void showCard(Graphics2D g, MapView v, MapViewport vp, Question q, JPanel card, Color colour, int slot)
	{
		if (!colour.equals(cardColour.get(q.id)))
		{
			cardColour.put(q.id, colour);
			card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(colour, 3),
				BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		}
		int tx = (int) vp.screenX(q.x + 0.5);
		int ty = (int) vp.screenY(q.y + 0.5);
		// place kind cards point at places close together, so they would cover each other: each sits in a corner of
		// the map (top-left, top-right, bottom-left, bottom-right in turn), its line to its place
		Dimension d = card.getPreferredSize();
		Rectangle r = q.action == Action.PLACE_KIND ? corner(v, slot, d) : place(v, tx, ty, offsets.get(q.id), d);
		int cx = r.x;
		int cy = r.y;
		if (!r.equals(card.getBounds()))
		{
			card.setBounds(r); // repaints the card's area only; stable once placed
		}
		if (!card.isVisible())
		{
			card.setVisible(true);
		}
		g.setColor(colour);
		g.setStroke(new BasicStroke(2.5f));
		int lx = Math.max(cx, Math.min(cx + d.width, tx));
		int ly = Math.max(cy, Math.min(cy + d.height, ty));
		g.drawLine(lx, ly, tx, ty);
		g.fillOval(tx - 5, ty - 5, 10, 10);
	}

	/** "Cards: 4 shown, 12 more" at the bottom left. */
	private void paintMoreLabel(Graphics2D g, MapView v, int shown)
	{
		String more = "Cards: " + shown + " shown, " + (open.size() - shown) + " more (answer one to go to the next)";
		int w = g.getFontMetrics().stringWidth(more);
		g.setColor(CARD);
		g.fillRect(4, v.getHeight() - 24, w + 12, 20);
		g.setColor(LEADER);
		g.drawRect(4, v.getHeight() - 24, w + 12, 20);
		g.drawString(more, 10, v.getHeight() - 9);
	}

	/** The card's spot on screen: its target plus {@code off}, kept inside the map. */
	private static Rectangle place(MapView v, int tx, int ty, int[] off, Dimension d)
	{
		return new Rectangle(Math.max(4, Math.min(v.getWidth() - d.width - 4, tx + off[0])),
			Math.max(4, Math.min(v.getHeight() - d.height - 4, ty + off[1])), d.width, d.height);
	}

	/** Corner {@code n} of the map (top-left, top-right, bottom-left, bottom-right, in turn), above the bottom label. */
	private static Rectangle corner(MapView v, int n, Dimension d)
	{
		int k = n % 4;
		return new Rectangle(k % 2 == 0 ? 4 : Math.max(4, v.getWidth() - d.width - 4),
			k < 2 ? 4 : Math.max(4, v.getHeight() - d.height - 30), d.width, d.height);
	}

	/** Where a card sits from its target in corner {@code n} (right-up, left-up, right-down, left-down, in turn). */
	private static int[] slotOffset(int n)
	{
		int k = n % 4;
		return new int[]{k % 2 == 0 ? 60 : -380, k < 2 ? -280 : 60};
	}

	/** Every place of a kind on the plane shown: a dot in the card's colour with a white ring. */
	private static void placeDots(Graphics2D g, MapView v, int[] places, Color colour)
	{
		MapViewport vp = v.getViewport();
		for (int t : places)
		{
			if (com.bestpath.core.path.Tiles.plane(t) != v.getPlane())
			{
				continue;
			}
			int x = (int) vp.screenX(com.bestpath.core.path.Tiles.x(t) + 0.5);
			int y = (int) vp.screenY(com.bestpath.core.path.Tiles.y(t) + 0.5);
			if (x < -10 || y < -10 || x > v.getWidth() + 10 || y > v.getHeight() + 10)
			{
				continue;
			}
			g.setColor(Color.WHITE);
			g.fillOval(x - 8, y - 8, 16, 16);
			g.setColor(colour);
			g.fillOval(x - 6, y - 6, 12, 12);
		}
	}

	/** The channel's way, white under the card colour, so it shows whichever layers are on. */
	private static void channelLine(Graphics2D g, MapViewport vp, int[] p, Color colour)
	{
		java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
		for (int i = 0; i < p.length; i++)
		{
			double x = vp.screenX((p[i] >>> 14) + 0.5);
			double y = vp.screenY((p[i] & 0x3FFF) + 0.5);
			if (i == 0)
			{
				path.moveTo(x, y);
			}
			else
			{
				path.lineTo(x, y);
			}
		}
		g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Color.WHITE);
		g.draw(path);
		g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(colour);
		g.draw(path);
	}
}
