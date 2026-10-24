package com.bestpath.tool.editor;

import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.transport.Suggestion;
import com.bestpath.tool.transport.TransportCandidate;
import com.bestpath.tool.transport.TransportData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared editing state for both map views: the current data (with applied
 * changes), the changes not built in yet, the selection and the tool options. Used
 * on the Swing thread only.
 */
public final class EditorContext
{
	/** A selected transport: a cache candidate (with a suggestion) or a created/confirmed transport. */
	public static final class Selection
	{
		public final TransportCandidate candidate;
		public final Suggestion suggestion;
		public final TransportRecord record;
		/** True if {@link #record} is a change the data was not rebuilt with yet. */
		public final boolean pending;

		/**
		 * A candidate's suggestion after you dragged its ends (or as saved on the
		 * test list): not decided yet, nothing is recorded. Null otherwise.
		 */
		public final TransportRecord draft;

		/** A suggested teleport destination (cache table or Wiki), or null. Its draft, if any, is {@link #draft}. */
		public final com.bestpath.tool.transport.CacheTableTeleports.Destination teleport;

		Selection(TransportCandidate candidate, Suggestion suggestion, TransportRecord record, boolean pending, TransportRecord draft)
		{
			this(candidate, suggestion, record, pending, draft, null);
		}

		Selection(TransportCandidate candidate, Suggestion suggestion, TransportRecord record, boolean pending, TransportRecord draft,
			com.bestpath.tool.transport.CacheTableTeleports.Destination teleport)
		{
			this.candidate = candidate;
			this.suggestion = suggestion;
			this.record = record;
			this.pending = pending;
			this.draft = draft;
			this.teleport = teleport;
		}

		/** A suggested teleport destination; {@code draft} is it after dragging (or as saved on the test list), or null. */
		public static Selection ofTeleport(com.bestpath.tool.transport.CacheTableTeleports.Destination d, TransportRecord draft)
		{
			return new Selection(null, null, null, false, draft, d);
		}

		public static Selection ofCandidate(TransportCandidate c, Suggestion s)
		{
			return new Selection(c, s, null, false, null);
		}

		public static Selection ofDraft(TransportCandidate c, Suggestion s, TransportRecord draft)
		{
			return new Selection(c, s, null, false, draft);
		}

		public static Selection ofRecord(TransportRecord r, boolean pending)
		{
			return new Selection(null, null, r, pending, null);
		}

		/** Start tile {x, y, plane}, or null (teleport, or a candidate without a suggestion). */
		public int[] start(EditorContext ctx)
		{
			TransportRecord r = record != null ? record : draft;
			if (r != null)
			{
				return r.hasStart() ? new int[]{r.startX, r.startY, r.startPlane} : null;
			}
			if (teleport != null)
			{
				return teleport.isNetwork ? new int[]{teleport.x, teleport.y, teleport.plane} : null;
			}
			int[] end = end();
			if (suggestion != null && suggestion.getStart() != null)
			{
				return suggestion.getStart();
			}
			return end == null ? null : ctx.standingTile(candidate, end[0], end[1]);
		}

		/** End tile {x, y, plane}, or null. */
		public int[] end()
		{
			TransportRecord r = record != null ? record : draft;
			if (r != null)
			{
				return new int[]{r.endX, r.endY, r.endPlane};
			}
			if (teleport != null)
			{
				return new int[]{teleport.x, teleport.y, teleport.plane};
			}
			return suggestion == null ? null : new int[]{suggestion.getDestX(), suggestion.getDestY(), suggestion.getDestPlane()};
		}

		public String name()
		{
			return record != null ? record.name : teleport != null ? teleport.label() : candidate.getName();
		}
	}

	/** A transport after all changes: the record, and whether it is not built in yet (pending) or deleted. */
	public static final class EffectiveTransport
	{
		public final TransportRecord record;
		public final boolean pending;
		public final boolean deleted;

		EffectiveTransport(TransportRecord record, boolean pending, boolean deleted)
		{
			this.record = record;
			this.pending = pending;
			this.deleted = deleted;
		}
	}

	public final EditStore edits;

	// current data (with applied changes); replaced as a whole after every rebuild
	public FlagGrid grid;
	public CoarseGraph graph;
	/** Per component: hidden with a Hide change. */
	public boolean[] hidden;
	/** Per component: a closed-off pocket not kept with a Keep change (a transport starts or lands there only if nothing else is near). */
	public boolean[] pockets;
	public ManualState state;
	public TransportData transports;
	/** The route finder for the current data (with applied changes), or null. */
	public com.bestpath.core.path.Pathfinder pathfinder;
	/** Steps through doors and gates (for the door cost of routes), or null. */
	public com.bestpath.core.path.DoorMap doors;
	/** Routes over the sea (docks, boats, the main ocean), or null; kept between builds while the sea is unchanged. */
	public volatile com.bestpath.core.path.Sailing sailing;

	// tool options
	public int brushSize = 1;
	public TransportRecord.Kind newKind = TransportRecord.Kind.TWO_WAY;
	public int newTicks = 2;
	/** Start tile of a transport being created with two clicks: {x, y, plane}, or null. */
	public int[] creationStart;

	private Selection selection;
	private final List<MapView> views = new ArrayList<>();
	private final List<Runnable> selectionListeners = new CopyOnWriteArrayList<>();
	private final SelectionActions actions = new SelectionActions(this);

	public EditorContext(EditStore edits)
	{
		this.edits = edits;
	}

	public void addView(MapView v)
	{
		views.add(v);
	}

	public List<MapView> views()
	{
		return Collections.unmodifiableList(views);
	}

	public void repaintAll()
	{
		views.forEach(MapView::repaint);
	}

	public boolean hasData()
	{
		return grid != null && graph != null;
	}

	public Selection getSelection()
	{
		return selection;
	}

	public void addSelectionListener(Runnable r)
	{
		selectionListeners.add(r);
	}

	/**
	 * Selects the selected candidate's next suggestion (same plane, up, down...)
	 * and shows its end in the destination view.
	 */
	public void nextSuggestion()
	{
		Selection s = selection;
		if (s == null || s.candidate == null || transports == null)
		{
			return;
		}
		List<Suggestion> all = transports.suggestionsFor(s.candidate);
		if (all.size() < 2)
		{
			return;
		}
		int i = all.indexOf(s.suggestion);
		select(Selection.ofCandidate(s.candidate, all.get((i + 1) % all.size())), views.isEmpty() ? null : views.get(0), false);
	}

	/**
	 * Selects a transport. The other view(s) centre on its opposite end: the
	 * end if you picked it at its start, the start if you picked it at its end.
	 * With {@code source} null (e.g. a button), no view moves.
	 */
	public void select(Selection s, MapView source, boolean pickedAtEnd)
	{
		selection = s;
		for (MapView v : views)
		{
			// a draft is drawn by the edit overlay; the map only outlines its object
			v.setSelectedTransport(s != null ? s.candidate : null, s != null && s.draft == null ? s.suggestion : null);
		}
		if (s != null && source != null)
		{
			int[] target = pickedAtEnd ? s.start(this) : s.end();
			if (target == null)
			{
				target = s.end();
			}
			if (target != null)
			{
				for (MapView v : views)
				{
					if (v != source)
					{
						v.setPlane(target[2]);
						v.showTile(target[0], target[1], Math.max(v.getViewport().getScale(), 8));
					}
				}
			}
		}
		selectionListeners.forEach(Runnable::run);
		repaintAll();
	}

	/** Per component: hidden after all changes (including those not built in yet). Null without data. */
	public boolean[] effectiveHidden()
	{
		if (graph == null || hidden == null)
		{
			return null;
		}
		boolean[] out = hidden.clone();
		for (Edit e : edits.getUnbuilt())
		{
			if (e.type == Edit.Type.HIDE)
			{
				int comp = graph.componentAt(e.x, e.y, e.plane);
				if (comp >= 0)
				{
					out[comp] = e.on;
				}
			}
		}
		return out;
	}

	/**
	 * Per component: a closed-off pocket left out of the data after all changes (including those not built
	 * in yet; kept pockets are not). Null without data.
	 */
	public boolean[] effectivePockets()
	{
		if (graph == null || pockets == null)
		{
			return null;
		}
		boolean[] out = pockets.clone();
		boolean[] all = null;
		for (Edit e : edits.getUnbuilt())
		{
			if (e.type == Edit.Type.KEEP)
			{
				int comp = graph.componentAt(e.x, e.y, e.plane);
				if (comp >= 0)
				{
					if (all == null)
					{
						all = graph.pockets();
					}
					out[comp] = all[comp] && !e.on;
				}
			}
		}
		return out;
	}

	/** Free-to-play land after all changes (the drawn file with the Free land changes on top). */
	public com.bestpath.core.collision.FreeLand freeLand()
	{
		return com.bestpath.tool.edit.FreeLandFile.effective(edits.active());
	}

	/** 64x64 regions ignored after all changes. */
	public java.util.Set<Integer> effectiveIgnoredRegions()
	{
		java.util.Set<Integer> out = new java.util.HashSet<>(state != null ? state.getIgnoredRegions() : Collections.emptySet());
		for (Edit e : edits.getUnbuilt())
		{
			if (e.type == Edit.Type.IGNORE_REGION)
			{
				if (e.on)
				{
					out.add(e.regionId());
				}
				else
				{
					out.remove(e.regionId());
				}
			}
		}
		return out;
	}

	/** The "test in game" list after all changes: id -> transport to test (may be null). */
	public Map<String, TransportRecord> effectiveTestLater()
	{
		Map<String, TransportRecord> out = new LinkedHashMap<>(state != null ? state.getTestLater() : Collections.emptyMap());
		for (Edit e : edits.getUnbuilt())
		{
			if (e.type == Edit.Type.TEST_LATER)
			{
				if (e.on)
				{
					out.put(e.target, e.transport);
				}
				else
				{
					out.remove(e.target);
				}
			}
		}
		return out;
	}

	/** Candidates skipped since the tool was opened (Skip does not come back to them until the tool restarts). */
	private final java.util.Set<String> skipped = new java.util.HashSet<>();

	/**
	 * Candidate and teleport ids deleted ("not a transport") or made into a transport by changes not built
	 * in yet. The built data still has them; the map hides them at once instead of until the rebuild.
	 */
	public java.util.Set<String> decidedSuggestions()
	{
		java.util.Set<String> out = new java.util.HashSet<>();
		for (Edit e : edits.getUnbuilt())
		{
			if (e.type == Edit.Type.REJECT)
			{
				if (e.on)
				{
					out.add(e.target);
				}
				else
				{
					out.remove(e.target);
				}
			}
			else if (e.type == Edit.Type.TRANSPORT && (e.transport.suggestionId != null || e.transport.objectId >= 0))
			{
				out.add(objectKey(e.transport));
			}
		}
		return out;
	}

	/** True if a candidate is already decided: rejected, made into a transport or on the test list (built in or not). */
	public boolean decided(TransportCandidate c, Map<String, TransportRecord> testLater)
	{
		String id = c.id();
		if (testLater.containsKey(id))
		{
			return true;
		}
		for (Edit e : edits.getUnbuilt())
		{
			if ((e.type == Edit.Type.REJECT && id.equals(e.target)) || (e.type == Edit.Type.TRANSPORT
				&& e.transport.objectId == c.getObjectId() && e.transport.objectX == c.getX()
				&& e.transport.objectY == c.getY() && e.transport.objectPlane == c.getPlane()))
			{
				return true;
			}
		}
		return false; // applied confirms and rejects are already left out of the candidates
	}

	/**
	 * Skip: leaves the selected candidate undecided and selects the nearest
	 * undecided candidate (same plane first) outside ignored regions. The main
	 * map centres on it, the second view on its end. Returns false if none is left.
	 */
	public boolean skipToNext()
	{
		if (transports == null)
		{
			return false;
		}
		int x = 0;
		int y = 0;
		int plane = 0;
		Selection s = selection;
		if (s != null && s.candidate != null)
		{
			skipped.add(s.candidate.id());
			x = s.candidate.getX();
			y = s.candidate.getY();
			plane = s.candidate.getPlane();
		}
		else if (!views.isEmpty())
		{
			MapView v = views.get(0);
			x = (int) Math.floor(v.getViewport().worldX(v.getWidth() / 2.0));
			y = (int) Math.floor(v.getViewport().worldY(v.getHeight() / 2.0));
			plane = v.getPlane();
		}
		java.util.Set<Integer> ignored = effectiveIgnoredRegions();
		Map<String, TransportRecord> testLater = effectiveTestLater();
		TransportCandidate best = null;
		long bestD = Long.MAX_VALUE;
		for (TransportCandidate c : transports.getCandidates())
		{
			if (skipped.contains(c.id()) || ignored.contains((c.getX() >> 6) << 8 | (c.getY() >> 6)))
			{
				continue;
			}
			long d = (long) c.distanceTo(x, y) + (c.getPlane() != plane ? 100_000 : 0);
			if (d < bestD && !decided(c, testLater))
			{
				bestD = d;
				best = c;
			}
		}
		if (best == null)
		{
			return false;
		}
		showCandidate(best);
		return true;
	}

	/** Transports after all changes: the built ones, replaced or deleted by changes not built in yet, plus new ones. */
	public List<EffectiveTransport> effectiveTransports()
	{
		Map<String, EffectiveTransport> out = new LinkedHashMap<>();
		if (transports != null)
		{
			for (TransportRecord r : transports.getConfirmed().values())
			{
				out.put(r.id, new EffectiveTransport(r, false, false));
			}
		}
		for (Edit e : edits.getUnbuilt())
		{
			if (e.type == Edit.Type.TRANSPORT)
			{
				out.put(e.transport.id, new EffectiveTransport(e.transport, true, false));
			}
			else if (e.type == Edit.Type.TRANSPORT_DELETE && out.containsKey(e.target))
			{
				out.put(e.target, new EffectiveTransport(out.get(e.target).record, true, true));
			}
		}
		return new ArrayList<>(out.values());
	}

	/**
	 * Finds a transport at a tile: right on its start or end tile (a click on the tile beside it selects
	 * nothing), or right on a candidate's outline. An undecided candidate under the click wins, even where a
	 * transport ends on it (two rocks side by side, one confirmed, its end on the other).
	 * Returns {selection, picked-at-end?} or null.
	 */
	public Object[] hitTransport(int x, int y, int plane)
	{
		java.util.Set<String> gone = transports == null ? Collections.emptySet() : decidedSuggestions(); // deleted or confirmed, not built in yet
		if (transports != null)
		{
			List<TransportCandidate> on = transports.near(x, y, plane, 0);
			on.removeIf(c -> gone.contains(c.id()));
			if (!on.isEmpty())
			{
				return new Object[]{candidateSelection(on.get(0)), false};
			}
		}
		for (EffectiveTransport t : effectiveTransports())
		{
			if (t.deleted)
			{
				continue;
			}
			TransportRecord r = t.record;
			if (r.hasStart() && r.startPlane == plane && r.startX == x && r.startY == y)
			{
				return new Object[]{Selection.ofRecord(r, t.pending), false};
			}
			if (r.endPlane == plane && r.endX == x && r.endY == y)
			{
				return new Object[]{Selection.ofRecord(r, t.pending), true};
			}
		}
		if (transports != null)
		{
			// suggested teleport destinations (pink dots), when that layer is shown
			if (teleportsShown)
			{
				for (com.bestpath.tool.transport.CacheTableTeleports.Destination d : transports.getDestinations())
				{
					if (!gone.contains(d.id()) && d.plane == plane && d.x == x && d.y == y)
					{
						return new Object[]{teleportSelection(d), true};
					}
				}
			}
			// candidates are picked only right on their outline (checked first), so the tiles beside them stay
			// free for starting a new transport
		}
		return null;
	}

	/** One thing that can be picked at a tile (for the right-click menu). */
	public static final class Hit
	{
		public final Selection selection;
		/** True if picked at its end (the end handle is active). */
		public final boolean atEnd;
		public final String label;

		Hit(Selection selection, boolean atEnd, String label)
		{
			this.selection = selection;
			this.atEnd = atEnd;
			this.label = label;
		}
	}

	/**
	 * Everything {@link #hitTransport} could pick at a tile (same reach), for choosing one when several
	 * overlap: transports (start or end), teleport suggestions (when shown) and undecided candidates.
	 */
	public List<Hit> hitsAt(int x, int y, int plane)
	{
		List<Hit> out = new ArrayList<>();
		for (EffectiveTransport t : effectiveTransports())
		{
			if (t.deleted)
			{
				continue;
			}
			TransportRecord r = t.record;
			boolean atStart = r.hasStart() && r.startPlane == plane && r.startX == x && r.startY == y;
			boolean atEnd = r.endPlane == plane && r.endX == x && r.endY == y;
			if (atStart || atEnd)
			{
				out.add(new Hit(Selection.ofRecord(r, t.pending), !atStart, "Transport: " + r.describe() + r.requirementNote()));
			}
		}
		if (transports != null)
		{
			java.util.Set<String> gone = decidedSuggestions();
			if (teleportsShown)
			{
				for (com.bestpath.tool.transport.CacheTableTeleports.Destination d : transports.getDestinations())
				{
					if (!gone.contains(d.id()) && d.plane == plane && d.x == x && d.y == y)
					{
						out.add(new Hit(teleportSelection(d), true, "Teleport suggestion: " + d.label()));
					}
				}
			}
			for (TransportCandidate c : transports.near(x, y, plane, 0))
			{
				if (!gone.contains(c.id()))
				{
					out.add(new Hit(candidateSelection(c), false, "Suggestion: " + c.getName() + " (" + c.getCategory().getLabel()
						+ ") at " + c.getX() + "," + c.getY() + "," + c.getPlane()));
				}
			}
		}
		return out;
	}

	/** True while the "Teleport destinations" layer is shown: only then can its dots be clicked. */
	public boolean teleportsShown;

	/** A suggested teleport; one on the test list as it was saved (perhaps moved). */
	public Selection teleportSelection(com.bestpath.tool.transport.CacheTableTeleports.Destination d)
	{
		TransportRecord saved = effectiveTestLater().get(d.id());
		return Selection.ofTeleport(d, saved != null ? saved.copy() : null);
	}

	/** A new transport record from a suggested teleport destination (a network stop or a teleport). */
	public TransportRecord recordFromTeleport(com.bestpath.tool.transport.CacheTableTeleports.Destination d)
	{
		TransportRecord r = new TransportRecord();
		r.id = EditStore.newId("t");
		r.kind = d.isNetwork ? TransportRecord.Kind.NETWORK : TransportRecord.Kind.TELEPORT;
		r.name = d.label();
		r.network = d.isNetwork ? d.network : "";
		r.category = d.isNetwork ? "TRAVEL" : "TELEPORT";
		r.startX = d.x;
		r.startY = d.y;
		r.startPlane = d.plane;
		r.endX = d.x;
		r.endY = d.y;
		r.endPlane = d.plane;
		r.ticks = defaultTicks(r.category);
		r.ticksEstimate = true;
		r.source = d.source;
		r.suggestionId = d.id();
		return r;
	}

	/** A candidate with its best suggestion; one on the test list as it was saved (perhaps with dragged ends). */
	public Selection candidateSelection(TransportCandidate c)
	{
		List<Suggestion> s = transports != null ? transports.suggestionsFor(c) : Collections.emptyList();
		Suggestion best = s.isEmpty() ? null : s.get(0);
		TransportRecord saved = effectiveTestLater().get(c.id());
		return saved != null ? Selection.ofDraft(c, best, saved.copy()) : Selection.ofCandidate(c, best);
	}

	/** Selects a candidate and shows it: main map on the object, the second view on its end. */
	public void showCandidate(TransportCandidate c)
	{
		if (!views.isEmpty())
		{
			MapView main = views.get(0);
			main.setPlane(c.getPlane());
			main.showTile(c.getX(), c.getY(), Math.max(main.getViewport().getScale(), 8));
		}
		select(candidateSelection(c), views.isEmpty() ? null : views.get(0), false);
	}

	/** Selects one of your transports and shows it: main map on its start (a teleport: its end). */
	public void showRecord(TransportRecord r)
	{
		if (!views.isEmpty())
		{
			MapView main = views.get(0);
			boolean start = r.hasStart();
			main.setPlane(start ? r.startPlane : r.endPlane);
			main.showTile(start ? r.startX : r.endX, start ? r.startY : r.endY, Math.max(main.getViewport().getScale(), 8));
		}
		boolean pending = false;
		for (EffectiveTransport t : effectiveTransports())
		{
			if (t.record.id.equals(r.id))
			{
				pending = t.pending;
			}
		}
		select(Selection.ofRecord(r, pending), views.isEmpty() ? null : views.get(0), false);
	}

	/**
	 * The tile a player stands on to use an object: the usable tile next to its
	 * outline (or up to 3 tiles out) nearest to {@code (towardX, towardY)}; null if none.
	 */
	public int[] standingTile(TransportCandidate c, int towardX, int towardY)
	{
		return StandingTiles.standingTile(this, c, towardX, towardY);
	}

	/** The walkable tiles beside a solid object's footprint (N/E/S/W, no wall between); empty if it does not block its tiles. */
	public java.util.List<int[]> facingTiles(TransportCandidate c)
	{
		return StandingTiles.facingTiles(this, c);
	}

	/** Can a transport start or land on this tile: walkable, not hidden, and not in a pocket unless allowed? */
	public boolean usable(int x, int y, int plane, boolean pocketsAllowed)
	{
		return StandingTiles.usable(this, x, y, plane, pocketsAllowed);
	}

	/** Default tick cost for a category (an estimate until changed). */
	public static int defaultTicks(String category)
	{
		return com.bestpath.tool.route.RouteTransports.defaultTicks(category);
	}

	/** A new transport record from a candidate and an end tile, with a known start tile {x, y, plane} (or null: stand next to the object). */
	public TransportRecord recordFromCandidate(TransportCandidate c, int[] knownStart, int ex, int ey, int ep, String source)
	{
		TransportRecord r = new TransportRecord();
		r.id = EditStore.newId("t");
		r.kind = newKind == TransportRecord.Kind.TELEPORT ? TransportRecord.Kind.TWO_WAY : newKind;
		r.name = c.getName();
		r.category = c.getCategory().name();
		int[] start = knownStart != null ? knownStart : standingTile(c, ex, ey);
		if (start == null)
		{
			start = new int[]{ex, ey, ep}; // nowhere usable near the object: start at the end, to be dragged into place
		}
		r.startX = start[0];
		r.startY = start[1];
		r.startPlane = start[2];
		r.endX = ex;
		r.endY = ey;
		r.endPlane = ep;
		r.ticks = defaultTicks(r.category);
		r.ticksEstimate = true;
		r.objectId = c.getObjectId();
		r.objectX = c.getX();
		r.objectY = c.getY();
		r.objectPlane = c.getPlane();
		r.source = source;
		return r;
	}

	/** The key a candidate (or a transport made from one) has on the test list and in rejections. */
	public static String objectKey(TransportRecord r)
	{
		if (r.suggestionId != null)
		{
			return r.suggestionId;
		}
		return r.objectId >= 0 ? r.objectId + "@" + r.objectX + "," + r.objectY + "," + r.objectPlane : r.id;
	}

	/** Confirm: a selected suggestion becomes a transport. Takes it off the test list. */
	public void confirmSelected()
	{
		actions.confirm();
	}

	/** The sea channel clicked (its points, packed x << 14 | y), or null: what Delete acts on when no transport is selected. */
	private int[] channel;

	/** A sea channel clicked on the map (null: none). */
	public void selectChannel(int[] points)
	{
		channel = points;
	}

	/**
	 * Deletes the sea channel clicked (for channels that are of no use): one change, undone with Ctrl+Z or from the
	 * Change log; the tool and the plugin export leave it out once the data is built again.
	 */
	void deleteChannel()
	{
		int[] p = channel;
		if (p == null)
		{
			return;
		}
		int mid = p[p.length / 2];
		Edit e = new Edit();
		e.type = Edit.Type.SEA_CHANNEL;
		e.target = com.bestpath.core.sea.SeaChannelsBuilder.key(p);
		e.on = true;
		e.x = mid >>> 14;
		e.y = mid & 0x3FFF;
		e.plane = 0;
		edits.add(e);
		channel = null;
	}

	/**
	 * Delete: a suggestion is marked "not a transport"; a transport is
	 * deleted (one not built in yet is simply taken back).
	 */
	public void deleteSelected()
	{
		actions.delete();
	}

	/**
	 * Test in game later: puts the selected suggestion (as it is now, also
	 * after dragging its ends) on the test list instead of confirming it.
	 * A transport not built in yet is taken back and goes on the list instead.
	 */
	public void testLaterSelected()
	{
		actions.testLater();
	}

	/**
	 * One-way / two-way: switches the selected transport between working in
	 * one direction (start to end) and both directions. A transport becomes a
	 * new change; a suggestion only gets a new draft (still undecided until
	 * Confirm or Test later). Teleports and candidates without a suggestion are
	 * left alone.
	 */
	public void toggleTwoWaySelected()
	{
		actions.toggleTwoWay();
	}

	/** Records a TRANSPORT change (in effect at once). */
	public void pendTransport(TransportRecord r)
	{
		Edit e = new Edit();
		e.type = Edit.Type.TRANSPORT;
		e.transport = r;
		e.x = r.endX;
		e.y = r.endY;
		e.plane = r.endPlane;
		edits.add(e);
	}
}
