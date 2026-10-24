package com.bestpath.ui;

import com.bestpath.clue.ClueCard;
import com.bestpath.clue.DetectedClue;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.NoMapDataEngine;
import com.bestpath.route.NoRouteWhy;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.RouteStep;
import com.bestpath.route.TransportSettings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;

/**
 * The Route tab: destination entry, status line, route summary and the
 * list of step cards. Swing thread only.
 */
final class RouteTab extends JPanel
{
	private final PanelActions actions;
	private final Supplier<TransportSettings> settings;

	final JTextField destination = new JTextField();
	private final JButton clueButton = Ui.button("Use clue", "Route to the clue scroll you carry");
	/** Routes to Quest Helper's current step (enabled while one is known). */
	private final JButton questButton = Ui.button("Quest", FunctionsTab.NO_QUEST_STEP);
	/** What the plugin knows about the carried clue (hidden without one). */
	private final WrapLabel clueText = new WrapLabel("", Ui.CONTENT_WIDTH, Ui.small(), Ui.NOTE);
	/** The route's header for a clue (or other titled) destination. */
	private final JLabel routeTitle = new JLabel();
	private final WrapLabel routeNotes = new WrapLabel("", Ui.CONTENT_WIDTH, Ui.small(), Ui.TEXT);
	private final JLabel status = new JLabel();
	private final WrapLabel summary = new WrapLabel("", Ui.CONTENT_WIDTH, Ui.small(), Ui.TEXT);
	private final JPanel skippedRow = new JPanel(new BorderLayout(4, 0));
	private final WrapLabel skippedText = new WrapLabel("", Ui.CONTENT_WIDTH - 60, Ui.small(), Ui.NOTE);
	private final JPanel steps = new ScrollablePanel();
	/**
	 * Developer details (RuneLite config "Developer details", off by default): a box at the top with
	 * where the player stands (game tile, map tile, region, instance) and a Copy button, and detail lines on every card.
	 */
	private boolean developer;
	private final JPanel devBox = new JPanel()
	{
		@Override
		public Dimension getMaximumSize()
		{
			// as tall as its lines now (where the player stands, the search times), never stretched
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	};
	private final JPanel devLines = new JPanel();
	List<String> whereLines = List.of();
	/** Where the shown route's search time went (developer details). */
	private final JPanel devTimes = new JPanel();
	private List<String> timeLines = List.of();
	/** The result shown last and its skipped transports (shown again when developer details are switched). */
	private RouteResult lastResult;
	private Set<String> lastSkipped = Set.of();
	/** Names of transports skipped for this route (the service only keeps ids). */
	private final Map<String, String> skippedNames = new LinkedHashMap<>();
	/** The cards of the shown route, in step order, and which result they show (for progress). */
	final List<StepCard> cards = new ArrayList<>();
	/** The shown route's steps and which of them have a card (walks have none). */
	private List<RouteStep> shownSteps = List.of();
	private int[] cardSteps = new int[0];
	private long shownGeneration = -1;
	private Progress progress = Progress.NONE;
	private final IconCache icons;

	RouteTab(PanelActions actions, Supplier<TransportSettings> settings, IconCache icons)
	{
		this.actions = actions;
		this.settings = settings;
		this.icons = icons;
		tickTimer.setRepeats(false);
		setLayout(new BorderLayout());
		setBackground(Ui.BACKGROUND);

		// the header, top to bottom; the step cards scroll under it
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(Ui.BACKGROUND);
		header.setBorder(Ui.padding(Ui.PAD, Ui.PAD, 4, Ui.PAD));
		addDeveloperBox(header);
		addDestinationEntry(header);
		addButtons(header);
		addRouteLines(header);
		addSkippedRow(header);
		add(header, BorderLayout.NORTH);

		steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
		steps.setBackground(Ui.BACKGROUND);
		steps.setBorder(Ui.padding(2, Ui.PAD, Ui.PAD, Ui.PAD));
		add(Ui.verticalScroll(steps), BorderLayout.CENTER);

		show(RouteResult.idle(0), Set.of());
		showClue(DetectedClue.NONE);
	}

	/** The developer details box (hidden unless they are on): where the player stands, the search times and Copy. */
	private void addDeveloperBox(JPanel header)
	{
		devBox.setLayout(new BorderLayout(4, 0));
		devBox.setBackground(Ui.CARD);
		devBox.setBorder(Ui.padding(4, 4, 4, 4));
		devLines.setLayout(new BoxLayout(devLines, BoxLayout.Y_AXIS));
		devLines.setOpaque(false);
		devTimes.setLayout(new BoxLayout(devTimes, BoxLayout.Y_AXIS));
		devTimes.setOpaque(false);
		JPanel devText = new JPanel();
		devText.setLayout(new BoxLayout(devText, BoxLayout.Y_AXIS));
		devText.setOpaque(false);
		devLines.setAlignmentX(LEFT_ALIGNMENT);
		devTimes.setAlignmentX(LEFT_ALIGNMENT);
		devText.add(devLines);
		devText.add(devTimes);
		devBox.add(devText, BorderLayout.CENTER);
		JButton copy = Ui.button("Copy", "Copy these lines (to paste them in a message)");
		copy.addActionListener(e -> copyWhere());
		JPanel copyTop = new JPanel(new BorderLayout());
		copyTop.setOpaque(false);
		copyTop.add(copy, BorderLayout.NORTH);
		devBox.add(copyTop, BorderLayout.EAST);
		devBox.setVisible(false);
		addHeaderRow(header, devBox);
		header.add(Box.createVerticalStrut(4));
	}

	/** "Destination tile (x, y, plane)", the box to type it in and Go (Enter in the box goes too). */
	private void addDestinationEntry(JPanel header)
	{
		JLabel title = Ui.label("Destination tile (x, y, plane)", Ui.small(), Ui.TEXT);
		addHeaderRow(header, title);
		header.add(Box.createVerticalStrut(3));

		JPanel entry = new JPanel(new BorderLayout(4, 0));
		entry.setOpaque(false);
		destination.putClientProperty("JTextField.placeholderText", "e.g. 3222, 3218, 0");
		destination.setToolTipText("x, y and plane (0-3)");
		destination.addActionListener(e -> go());
		entry.add(destination, BorderLayout.CENTER);
		JButton goButton = Ui.button("Go", "Find the best route to this tile");
		goButton.addActionListener(e -> go());
		entry.add(goButton, BorderLayout.EAST);
		addHeaderRow(header, entry);
		header.add(Box.createVerticalStrut(4));
	}

	/** "Use clue", "Quest" and "Clear" side by side, and the line about the carried clue under them. */
	private void addButtons(JPanel header)
	{
		JPanel more = new JPanel(new GridLayout(1, 3, 4, 0));
		more.setOpaque(false);
		JButton clue = clueButton;
		clue.setEnabled(false);
		clue.addActionListener(e -> actions.useClue());
		JButton quest = questButton;
		quest.setEnabled(false);
		quest.addActionListener(e -> actions.useQuestStep());
		JButton clear = Ui.button("Clear", "Forget the destination and hide the path");
		clear.addActionListener(e ->
		{
			skippedNames.clear();
			actions.clearRoute();
		});
		more.add(clue);
		more.add(quest);
		more.add(clear);
		addHeaderRow(header, more);
		header.add(Box.createVerticalStrut(3));
		addHeaderRow(header, clueText);
		header.add(Box.createVerticalStrut(8));
	}

	/** The route's title and notes (a clue or other titled destination), the status line and the summary. */
	private void addRouteLines(JPanel header)
	{
		routeTitle.setFont(Ui.bold());
		routeTitle.setForeground(Ui.TITLE);
		addHeaderRow(header, routeTitle);
		addHeaderRow(header, routeNotes);
		header.add(Box.createVerticalStrut(2));

		status.setFont(Ui.bold());
		status.setText(" ");
		addHeaderRow(header, status);
		addHeaderRow(header, summary);
	}

	/** "Skipped for this route: ..." with Undo (hidden while nothing is skipped). */
	private void addSkippedRow(JPanel header)
	{
		skippedRow.setOpaque(false);
		skippedRow.add(skippedText, BorderLayout.CENTER);
		JButton undo = Ui.button("Undo", "Allow the skipped transports again for this route");
		undo.addActionListener(e ->
		{
			skippedNames.clear();
			actions.clearSkips();
		});
		skippedRow.add(undo, BorderLayout.EAST);
		header.add(Box.createVerticalStrut(4));
		addHeaderRow(header, skippedRow);
	}

	/** Developer details on or off (RuneLite config): the box at the top, and the cards made again with or without them. */
	void setDeveloper(boolean on)
	{
		if (on == developer)
		{
			return;
		}
		developer = on;
		devBox.setVisible(on);
		if (lastResult != null)
		{
			show(lastResult, lastSkipped);
		}
		revalidate();
		repaint();
	}

	/** Where the player stands, for the developer box (game tile, map tile, region, instance, ...). */
	void showWhere(List<String> lines)
	{
		if (lines.equals(whereLines))
		{
			return;
		}
		whereLines = List.copyOf(lines);
		devLines.removeAll();
		for (String l : whereLines)
		{
			WrapLabel w = Ui.wrapped(l, Ui.CONTENT_WIDTH - 60, Ui.small(), Ui.TEXT);
			w.setAlignmentX(LEFT_ALIGNMENT);
			devLines.add(w);
		}
		devBox.revalidate();
		devBox.repaint();
	}

	/** The search time by part, for the developer box: the engine's lines, then what the parts leave out. */
	static List<String> timeLines(RouteResult r)
	{
		if (r == null || r.getTimeLines().isEmpty())
		{
			return List.of();
		}
		List<String> out = new ArrayList<>();
		out.add("Search time by part" + (r.getSearchMillis() >= 0 ? " (all: " + RouteResult.searchTime(r.getSearchMillis()) + ")" : "") + ":");
		for (String l : r.getTimeLines())
		{
			out.add("- " + l);
		}
		long other = r.getSearchMillis() - r.getTimedMillis();
		if (r.getSearchMillis() >= 0 && other > 0)
		{
			out.add("- Other (waiting for the map data, choosing the plane, rough bounds): " + other + " ms");
		}
		return out;
	}

	private void showTimes(RouteResult r)
	{
		List<String> lines = timeLines(r);
		if (lines.equals(timeLines))
		{
			return;
		}
		timeLines = lines;
		devTimes.removeAll();
		for (String l : lines)
		{
			WrapLabel w = Ui.wrapped(l, Ui.CONTENT_WIDTH - 60, Ui.small(), Ui.TEXT);
			w.setAlignmentX(LEFT_ALIGNMENT);
			devTimes.add(w);
		}
		devBox.revalidate();
		devBox.repaint();
	}

	private void copyWhere()
	{
		List<String> all = new ArrayList<>(whereLines);
		if (lastResult != null && lastResult.getRequest() != null)
		{
			all.add("Destination: " + lastResult.getRequest().label());
		}
		all.addAll(timeLines);
		Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(String.join("\n", all)), null);
	}

	/** Whether Quest Helper's current step is known: the "Quest" button can route there. */
	void showQuestStep(boolean known)
	{
		questButton.setEnabled(known);
		questButton.setToolTipText(known ? "Route to Quest Helper's current step" : FunctionsTab.NO_QUEST_STEP);
	}

	/** The carried clue changed: the line under the buttons and the "Use clue" button. */
	void showClue(DetectedClue c)
	{
		clueButton.setEnabled(c.isRoutable());
		switch (c.getKind())
		{
			case NONE:
				clueText.setText("");
				clueButton.setToolTipText("Route to the clue scroll you carry (none carried)");
				break;
			case STEP:
			case HOT_COLD:
				clueText.setText(c.isRoutable() ? c.title() + " detected." + (c.getKind() == DetectedClue.Kind.STEP
					? " Use clue routes there." : " Use clue routes to the nearest possible spot.")
					: c.title() + ": " + String.join(" ", c.lines()));
				clueButton.setToolTipText(c.isRoutable() ? "Route to: " + c.title() : "No place to route to");
				break;
			default:
				clueText.setText(c.title() + ": " + String.join(" ", c.lines()));
				clueButton.setToolTipText("No clue step to route to");
				break;
		}
		revalidate();
		repaint();
	}

	/**
	 * Adds a header row, left-aligned. A panel never grows taller than it is when added; a label keeps its own sizes
	 * (unlike {@link Ui#addRow}, which caps everything but panels).
	 */
	private static void addHeaderRow(JPanel p, JComponent c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
		if (c instanceof JPanel)
		{
			c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		}
		p.add(c);
	}

	private void go()
	{
		RouteRequest r;
		try
		{
			r = RouteRequest.parse(destination.getText());
		}
		catch (IllegalArgumentException e)
		{
			setStatus("Check the tile", Ui.BAD);
			summary.setText(e.getMessage());
			return;
		}
		actions.findRoute(r);
	}

	/** True if a NO_DATA result says why the map data could not be read (not just "there is none"). */
	static boolean isLoadFailure(String message)
	{
		return message != null && !message.isEmpty() && !message.equals(NoMapDataEngine.MESSAGE);
	}

	/** Shows a published result. {@code skipped}: this route's session exclusions. */
	void show(RouteResult r, Set<String> skipped)
	{
		lastResult = r;
		showTimes(r);
		lastSkipped = skipped;
		RouteRequest req = r.getRequest();
		showDestination(r, req);
		showTitle(r, req);
		showStatus(r, req);
		showSkipped(skipped);

		// the cards: the boat card, a card per step but walks, the "no route" cards, a walk-only line, the clue's card
		steps.removeAll();
		cards.clear();
		addBoatChoice(r);
		shownGeneration = r.getGeneration();
		shownSteps = r.getSteps();
		cardSteps = CardPlan.cardSteps(shownSteps);
		addStepCards();
		addBlockedCards(r);
		addWalkOnlyLine();
		// on arrival the result has no request any more; a clue's card stays: "Dig here" is what the player needs now
		boolean arrivedAtClue = r.getStatus() == RouteStatus.ARRIVED && req == null && isClueRoute(shownRequest, clueCard);
		shownRequest = arrivedAtClue ? shownRequest : req;
		clueCardShown = null;
		addClueCard();
		applyProgress(false);
		steps.revalidate();
		steps.repaint();
		revalidate();
	}

	/** The route's destination in the destination box (unless the player is typing there); a titled place as its tile. */
	private void showDestination(RouteResult r, RouteRequest req)
	{
		if (req != null && !destination.isFocusOwner())
		{
			int d = r.getDestination();
			destination.setText(req.hasTitle() && d != -1 ? RouteRequest.tileLabel(Tiles.x(d), Tiles.y(d), Tiles.plane(d))
				: req.label());
		}
	}

	/** The title of a clue (or other titled) destination and the notes under it (the search's own notes too). */
	private void showTitle(RouteResult r, RouteRequest req)
	{
		boolean titled = req != null && req.hasTitle() && r.getStatus() != RouteStatus.ARRIVED;
		routeTitle.setText(titled ? req.getTitle() : "");
		routeTitle.setVisible(titled);
		List<String> notes = new ArrayList<>();
		if (titled)
		{
			notes.addAll(req.getNotes());
		}
		if (req != null && r.getStatus() != RouteStatus.ARRIVED)
		{
			notes.addAll(r.getNotes()); // the search's own notes (e.g. avoided transports used) for typed tiles too
		}
		routeNotes.setText(String.join("\n", notes));
	}

	/** The status line ("Route ready", "No route", ...) and the summary under it. */
	private void showStatus(RouteResult r, RouteRequest req)
	{
		switch (r.getStatus())
		{
			case IDLE:
				setStatus("No destination", Ui.MUTED);
				summary.setText("Type a tile above and press Go.");
				break;
			case CALCULATING:
				setStatus("Calculating...", Ui.NOTE);
				summary.setText(req.hasTitle() ? (req.targetCount() == 1 ? "To its place"
					: "Comparing " + req.targetCount() + " places") : "To your destination"); // no coordinates
				break;
			case READY:
				setStatus("Route ready", Ui.GOOD);
				summary.setText(summaryText(r.getSteps(), r.getTotalTicks(), r.getSearchMillis()));
				break;
			case NO_ROUTE:
				setStatus(r.isFindingWhy() ? FINDING_WHY : "No route", Ui.BAD);
				summary.setText(noRouteText(r)); // why, in plain words; no coordinates
				break;
			case NO_DATA:
				if (isLoadFailure(r.getMessage()))
				{
					setStatus("Map data problem", Ui.BAD);
					summary.setText(r.getMessage()); // why the data could not be read
				}
				else
				{
					setStatus("No map data yet", Ui.NOTE);
					summary.setText("The map data isn't ready yet.");
				}
				break;
			case ARRIVED:
				setStatus("Arrived", Ui.GOOD);
				summary.setText(r.getMessage());
				break;
			case ERROR:
			default:
				setStatus("Can't route", Ui.BAD);
				summary.setText(r.getMessage());
				break;
		}
	}

	/** "Skipped for this route: ..." with the skipped transports' names (hidden when none). */
	private void showSkipped(Set<String> skipped)
	{
		skippedNames.keySet().retainAll(skipped);
		List<String> names = new ArrayList<>();
		for (String id : skipped)
		{
			names.add(skippedNames.getOrDefault(id, id));
		}
		skippedRow.setVisible(!names.isEmpty());
		skippedText.setText(names.isEmpty() ? "" : ("Skipped for this route: " + String.join(", ", names)));
		skippedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, skippedRow.getPreferredSize().height));
	}

	/** On the boat: "Park my boat" / "Ocean only path", above the step cards. */
	private void addBoatChoice(RouteResult r)
	{
		boatChoice = null;
		if (r.getBoatCard() != null && (r.getStatus() == RouteStatus.READY || r.getStatus() == RouteStatus.NO_ROUTE))
		{
			boatChoice = new BoatChoiceCard(r.getBoatCard(), icons, actions::boatMode);
			boatChoice.setAlignmentX(LEFT_ALIGNMENT);
			steps.add(boatChoice);
			steps.add(Box.createVerticalStrut(4));
		}
	}

	/** A card for each step of the shown route but walks (they get none); the cards are numbered 1..n. */
	private void addStepCards()
	{
		for (int k = 0; k < cardSteps.length; k++)
		{
			StepCard card = new StepCard(k + 1, shownSteps.get(cardSteps[k]), icons, this::skip, this::neverUse, tickControl,
				this::answer);
			if (developer)
			{
				card.showDeveloper();
			}
			card.setAlignmentX(LEFT_ALIGNMENT);
			steps.add(card);
			steps.add(Box.createVerticalStrut(4));
			cards.add(card);
		}
	}

	/** "No route": a card for each thing a way needs that the player can't use yet, "or" between the ways. */
	private void addBlockedCards(RouteResult r)
	{
		blockedCards.clear();
		orLabels = 0;
		NoRouteWhy why = r.getStatus() == RouteStatus.NO_ROUTE ? r.getNoRouteWhy() : null;
		if (why != null)
		{
			boolean firstWay = true;
			for (List<NoRouteWhy.Blocker> way : why.getWays())
			{
				if (!firstWay)
				{
					// "or" between the alternative ways
					JLabel or = Ui.label("or", Ui.bold(), Ui.TITLE);
					or.setAlignmentX(LEFT_ALIGNMENT);
					or.setBorder(Ui.padding(0, 4, 4, 0));
					steps.add(or);
					orLabels++;
				}
				firstWay = false;
				for (NoRouteWhy.Blocker b : way)
				{
					BlockedCard card = new BlockedCard(b, icons, this::restore, settings.get(), this::toggle, this::skipCard);
					if (developer)
					{
						card.showDeveloper();
					}
					card.setAlignmentX(LEFT_ALIGNMENT);
					steps.add(card);
					steps.add(Box.createVerticalStrut(4));
					blockedCards.add(card);
				}
			}
		}
	}

	/** A route that is only walking: one short line instead of an empty list. */
	private void addWalkOnlyLine()
	{
		if (cardSteps.length == 0 && !shownSteps.isEmpty())
		{
			WrapLabel walk = Ui.wrapped(walkOnlyText(shownSteps), Ui.CONTENT_WIDTH, Ui.normal(), Ui.TITLE);
			walk.setAlignmentX(LEFT_ALIGNMENT);
			steps.add(walk);
			steps.add(Box.createVerticalStrut(4));
		}
	}

	/** The boat card shown at the top, or null. */
	BoatChoiceCard boatChoice;

	/** How many "or" lines stand between the "no route" cards' ways (tests). */
	int orLabels;

	/** The "no route" cards shown (tests). */
	final List<BlockedCard> blockedCards = new ArrayList<>();

	/** The status line while the why of a "no route" is worked out. */
	static final String FINDING_WHY = "No route, finding out why...";

	/**
	 * The text under "No route": how long it took (as for a route), then why in plain words
	 * ({@link NoRouteWhy#text()}); without the why, the engine's own line.
	 */
	static String noRouteText(RouteResult r)
	{
		NoRouteWhy why = r.getNoRouteWhy();
		String time = r.getSearchMillis() < 0 ? "" : "Calculated in " + RouteResult.searchTime(r.getSearchMillis()) + "\n";
		return time + (why == null ? r.getMessage() : why.text());
	}

	/** "Restore" on a "no route" card: off the persistent blacklist, as the Settings tab's Restore does (then a new search). */
	private void restore(String transportId)
	{
		actions.settingsChanged(settings.get().withoutBlacklisted(transportId));
	}

	/**
	 * A Settings tick box on a "no route" card clicked (a transport type, a boat, or "A shipwright may bring my boat"):
	 * the same change as in the Settings tab (which then shows it), saved, then a new search.
	 */
	private void toggle(NoRouteWhy.Toggle t, boolean on)
	{
		TransportSettings now = settings.get();
		TransportSettings next = t.set(now, on);
		if (!next.equals(now))
		{
			actions.settingsChanged(next);
		}
	}

	/** The request of the shown result (null: none). */
	private RouteRequest shownRequest;
	/** The carried clue's card (null: none), and the component showing it (null: not shown). */
	private ClueCard clueCard;
	ClueStepCard clueCardShown;

	/**
	 * The carried clue's own card changed (the client thread made it after an item, STASH or clue change; null = no
	 * clue): shown as the last card while the route is that clue's route.
	 */
	void showClueCard(ClueCard card)
	{
		if (Objects.equals(card, clueCard) && (clueCardShown != null) == isClueRoute(shownRequest, card))
		{
			return;
		}
		clueCard = card;
		if (clueCardShown != null)
		{
			// the spacer after it goes too
			int i = Arrays.asList(steps.getComponents()).indexOf(clueCardShown);
			steps.remove(clueCardShown);
			if (i >= 0 && i < steps.getComponentCount())
			{
				steps.remove(i);
			}
			clueCardShown = null;
		}
		addClueCard();
		steps.revalidate();
		steps.repaint();
	}

	/** True if the route shown is the route to this clue (same request key). */
	static boolean isClueRoute(RouteRequest req, ClueCard card)
	{
		return req != null && card != null && req.getKey() != null && req.getKey().equals(card.getRequestKey());
	}

	private void addClueCard()
	{
		if (!isClueRoute(shownRequest, clueCard))
		{
			return;
		}
		clueCardShown = new ClueStepCard(cards.size() + 1, clueCard, icons,
			() -> actions.findRoute(ClueCard.watsonRequest()));
		clueCardShown.setAlignmentX(LEFT_ALIGNMENT);
		steps.add(clueCardShown);
		steps.add(Box.createVerticalStrut(4));
	}

	/** Highlights the player's current step (the route follower's progress); ignored for another route. */
	void showProgress(Progress p)
	{
		progress = p;
		applyProgress(true);
	}

	private void applyProgress(boolean scrollToCurrent)
	{
		int current = progress.getGeneration() == shownGeneration ? progress.getStep() : -1;
		CardPlan.State[] states = CardPlan.states(shownSteps, cardSteps, current);
		int toGo = current >= 0 ? CardPlan.walkToNext(shownSteps, current) : 0;
		StepCard focus = null;
		for (int k = 0; k < cards.size(); k++)
		{
			cards.get(k).setProgress(states[k], toGo);
			if (states[k] == CardPlan.State.CURRENT || states[k] == CardPlan.State.NEXT)
			{
				focus = cards.get(k);
			}
		}
		if (scrollToCurrent && focus != null)
		{
			steps.validate();
			steps.scrollRectToVisible(focus.getBounds());
		}
	}

	/**
	 * "3 steps, 236 ticks (2 min 22 s)": the cards' count and the route's total ticks only, never broken down; then how
	 * long the search took on its own line so it never breaks apart ("Calculated in 45 ms"; none when -1).
	 */
	static String summaryText(List<RouteStep> routeSteps, int totalTicks, long searchMillis)
	{
		String time = searchMillis < 0 ? "" : "\nCalculated in " + RouteResult.searchTime(searchMillis);
		return travelText(routeSteps, totalTicks) + time;
	}

	private static String travelText(List<RouteStep> routeSteps, int totalTicks)
	{
		int n = CardPlan.cardSteps(routeSteps).length;
		String total = totalTicks + " ticks (" + RouteResult.duration(totalTicks) + ")";
		if (n == 0)
		{
			return "Only walking, " + total;
		}
		return n + (n == 1 ? " step, " : " steps, ") + total;
	}

	/** The one line of a route that is only walking: "Walk there (35 tiles)". */
	static String walkOnlyText(List<RouteStep> routeSteps)
	{
		int tiles = CardPlan.walkTiles(routeSteps);
		return "Walk there (" + tiles + (tiles == 1 ? " tile)" : " tiles)");
	}

	private void setStatus(String text, Color color)
	{
		status.setText(text);
		status.setForeground(color);
	}

	private void skip(RouteStep s)
	{
		skippedNames.put(s.getTransportId(), shortName(s));
		actions.skipForThisRoute(s.getTransportId());
	}

	/** "Skip" on a "no route" card: skips what the card stands for, then looks for the next way without it. */
	private void skipCard(NoRouteWhy.Blocker b)
	{
		skippedNames.put(b.getSkipId(), shortName(b.getStep()));
		actions.skipForThisRoute(b.getSkipId());
	}

	/**
	 * Yes / No under a card: every card asking the same question shows it answered; the plugin saves the answer for
	 * the account and the route is worked out again (the availability check sees the new answer).
	 */
	private void answer(String answer, boolean yes)
	{
		for (StepCard c : cards)
		{
			c.answered(answer, yes);
		}
		actions.answer(answer, yes);
	}

	private void neverUse(RouteStep s)
	{
		actions.settingsChanged(settings.get().withBlacklisted(s.getTransportId(), shortName(s)));
	}

	/** Quick - / + clicks are sent together after this pause: one save and one re-route. */
	static final int TICK_SEND_DELAY_MS = 700;
	/**
	 * Tick changes clicked and not sent yet: the card's key ({@link RouteStep#getTickKey}: a transport's
	 * {@link TransportSettings#tickKey}, or a network ride's whole network, {@link TransportSettings#networkKey}) ->
	 * change, and names.
	 */
	final Map<String, Integer> waitingTicks = new LinkedHashMap<>();
	private final Map<String, String> waitingNames = new LinkedHashMap<>();
	private final Timer tickTimer = new Timer(TICK_SEND_DELAY_MS, e -> sendTickChanges());

	/** The cards' - / +: the latest change per transport (waiting or saved); a click waits, then is sent. */
	private final StepCard.TickControl tickControl = new StepCard.TickControl()
	{
		@Override
		public int change(RouteStep s)
		{
			String key = s.getTickKey();
			Integer w = waitingTicks.get(key);
			return w != null ? w : s.isGroupTicks() ? settings.get().groupTickChange(key) : settings.get().tickChange(key);
		}

		@Override
		public void changed(RouteStep s, int change)
		{
			String key = s.getTickKey();
			waitingTicks.put(key, change);
			waitingNames.put(key, shortName(s));
			for (StepCard c : cards)
			{
				if (key.equals(c.getStep().getTickKey()))
				{
					c.showTickChange(change); // the same transport, or another ride of the same network, on another card
				}
			}
			tickTimer.restart();
		}
	};

	/**
	 * Sends the waiting - / + changes now (the timer does this {@value #TICK_SEND_DELAY_MS} ms after the last click):
	 * one settings change, so the plugin saves once and re-routes once. Added to the newest settings, so a Settings
	 * tab change made meanwhile is kept.
	 */
	void sendTickChanges()
	{
		tickTimer.stop();
		if (waitingTicks.isEmpty())
		{
			return;
		}
		TransportSettings before = settings.get();
		TransportSettings s = before;
		for (Map.Entry<String, Integer> e : waitingTicks.entrySet())
		{
			// a network card's - / + changes the whole network, as its Settings row does
			s = isNetworkKey(e.getKey()) ? s.withGroupTickChange(e.getKey(), e.getValue())
				: s.withTickChange(e.getKey(), e.getValue(), waitingNames.get(e.getKey()));
		}
		waitingTicks.clear();
		waitingNames.clear();
		if (!s.equals(before))
		{
			actions.settingsChanged(s);
		}
	}

	private static boolean isNetworkKey(String key)
	{
		return key.startsWith(TransportSettings.networkKey(""));
	}

	private static String shortName(RouteStep s)
	{
		return s.getTitle();
	}
}
