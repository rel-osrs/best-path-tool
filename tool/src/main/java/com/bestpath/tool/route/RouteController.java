package com.bestpath.tool.route;

import com.bestpath.core.collision.FreeLandDiagnostics;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.PathfinderDiagnostics;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.RouteDiagnostics;
import com.bestpath.core.path.Transport;
import com.bestpath.tool.bank.ToolBanks;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.transport.TransportCandidate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;

/**
 * The route check: a start and a destination (set with the Route tool), the
 * options, and the latest result. Searches run on their own thread; results
 * come back on the Swing thread. Everything else is Swing-thread only.
 */
public final class RouteController
{
	/** How far from an unreachable destination the route may stop, in tiles, until the user changes it. */
	public static final int DEFAULT_NEAR_RANGE = 10;
	/** Extra ticks for each step through a door or gate, so routes go round the simple way. */
	public static final int DEFAULT_DOOR_COST = 5;
	/**
	 * The first y above the main map (a fixed value the user can change): the top of the main map's solid block of
	 * regions in cache #2735 (region rows 32-65, so y 4224). Above it, dungeons, Zanaris and other areas are packed
	 * side by side.
	 */
	public static final int DEFAULT_MAIN_MAP_TOP_Y = 4224;

	private final EditorContext ctx;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "route-search");
		t.setDaemon(true);
		return t;
	});
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private int generation;

	private int[] start;
	private int[] end;
	private boolean useUnconfirmed;
	private int planePenalty = RouteTransports.DEFAULT_PLANE_CHANGE_PENALTY;
	/** How far from an unreachable destination the route may stop (tiles); set by the user. */
	private int nearRange = DEFAULT_NEAR_RANGE;
	private volatile int doorCost = DEFAULT_DOOR_COST;
	private int mainMapTopY = DEFAULT_MAIN_MAP_TOP_Y;
	/** The bank stop's ticks (Route tab "Bank stop"). */
	private int bankTicks = com.bestpath.core.path.BankFirst.DEFAULT_TICKS;

	// sailing: the player's boats, Sailing level, and whether shipwrights may bring a boat
	private boolean sailingOn;
	private int sailingLevel = 99;
	private boolean shipwrights = true;
	/** Hundredths of a tick per 22.5 degrees a boat turns (turning costs time). */
	private int turnCost = com.bestpath.core.path.Sailing.DEFAULT_TURN_COST;
	private List<com.bestpath.core.sea.Boat> boats = List.of();
	/** Full boat bottles added to the test player's inventory for the route check (Boats dialog). */
	private int bottles;

	/** The test player routes are checked for (requirements); Maxed until another is picked. */
	private volatile com.bestpath.tool.req.TestPlayer player = com.bestpath.tool.req.TestPlayer.maxed();
	/** The Wilderness level where the route starts (Route tab box). */
	private volatile int startWilderness;

	/** The free-world search (walking only between free tiles) and what it was made from. */
	private Pathfinder freePf;
	private Pathfinder freePfFrom;
	private com.bestpath.core.collision.FreeLand freePfLand;

	// the last search and its answer
	private Route route;
	/** Where the route stops instead of the destination {x, y, plane, inSight}, or null (at the destination). */
	private int[] stopTile;
	/** True if the destination could not be reached and nothing in range could either. */
	private boolean nothingInRange;
	/** True if the last search kept the stop tile inside the destination's region (above the main map). */
	private boolean confined;
	/** How going to a bank first was weighed for the last route, or null (not tried). */
	private com.bestpath.core.path.BankFirst.Plan bankPlan;
	/** The test player's requirement check of the last search (which transports need the bank). */
	private RequirementFilter lastNeeds;
	/** What the last search left out for the player's requirements (status line). */
	private volatile String requirementSummary;
	/** Why a free-world route may fail (start or destination off free land), or null. */
	private String freeHint;
	/** What is missing for a route to or from the sea (sailing off, no boat), or null. */
	private String seaHint;
	/** Sub-regions the last search's tile stage entered, and whether to show them. */
	private int[] searched = new int[0];
	private boolean showSearched;
	private int transportCount;
	/** Sailing transports offered to the last search (nothing counts them: always 0, so the status never shows them). */
	private int sailTransports;
	private String status = "Route tool (R): left-click the start, right-click the destination.";
	private boolean busy;

	public RouteController(EditorContext ctx)
	{
		this.ctx = ctx;
	}

	public int getBankTicks()
	{
		return bankTicks;
	}

	/** The bank stop's ticks (never below 0); searches again. */
	public void setBankTicks(int ticks)
	{
		bankTicks = Math.max(0, ticks);
		recompute();
	}

	public void addListener(Runnable r)
	{
		listeners.add(r);
	}

	private void changed()
	{
		listeners.forEach(Runnable::run);
		ctx.repaintAll();
	}

	public void setStart(int x, int y, int plane)
	{
		start = new int[]{x, y, plane};
		recompute();
	}

	public void setEnd(int x, int y, int plane)
	{
		end = new int[]{x, y, plane};
		recompute();
	}

	public void clear()
	{
		start = null;
		end = null;
		route = null;
		stopTile = null;
		searched = new int[0];
		generation++;
		busy = false;
		status = "Route tool (R): left-click the start, right-click the destination.";
		changed();
	}

	public boolean isSailingOn()
	{
		return sailingOn;
	}

	public void setSailingOn(boolean on)
	{
		sailingOn = on;
		recompute();
	}

	public int getSailingLevel()
	{
		return sailingLevel;
	}

	public void setSailingLevel(int level)
	{
		sailingLevel = Math.max(1, Math.min(99, level));
		recompute();
	}

	public int getTurnCost()
	{
		return turnCost;
	}

	public void setTurnCost(int hundredths)
	{
		turnCost = Math.max(0, hundredths);
		recompute();
	}

	public boolean isShipwrights()
	{
		return shipwrights;
	}

	public void setShipwrights(boolean on)
	{
		shipwrights = on;
		recompute();
	}

	/** The player's boats (up to five, slots 1-5). */
	public List<com.bestpath.core.sea.Boat> getBoats()
	{
		return boats;
	}

	public void setBoats(List<com.bestpath.core.sea.Boat> list)
	{
		boats = List.copyOf(list);
		recompute();
	}

	public int getBottles()
	{
		return bottles;
	}

	/** How many full boat bottles the player carries (on top of any in the test player's own inventory). */
	public void setBottles(int n)
	{
		bottles = Math.max(0, n);
		recompute();
	}

	/**
	 * The player sailing checks: the test player, with the Boats dialog's full boat bottles added to the inventory
	 * (a bottled boat is used only with its bottle carried, {@link com.bestpath.core.path.Sailing#bottlesInHand}).
	 */
	static com.bestpath.core.req.PlayerState withBottles(com.bestpath.tool.req.TestPlayer p, int bottles)
	{
		if (bottles <= 0)
		{
			return p.toState();
		}
		com.bestpath.tool.req.TestPlayer c = p.copy();
		c.inventory.merge(Integer.toString(com.bestpath.core.sea.Boat.BOTTLE_FULL), bottles, Integer::sum);
		return c.toState();
	}

	/** The docks sailing can use (for choosing where a boat is), or an empty list before the data is built. */
	public List<com.bestpath.core.sea.Dock> sailingDocks()
	{
		com.bestpath.core.path.Sailing s = ctx.sailing;
		return s == null ? List.of() : s.getDocks();
	}

	public int getStartWilderness()
	{
		return startWilderness;
	}

	public void setStartWilderness(int level)
	{
		startWilderness = level;
		recompute();
	}

	public com.bestpath.tool.req.TestPlayer getPlayer()
	{
		return player;
	}

	public void setPlayer(com.bestpath.tool.req.TestPlayer p)
	{
		player = p;
		recompute();
	}

	public void setUseUnconfirmed(boolean on)
	{
		useUnconfirmed = on;
		recompute();
	}

	public void setPlanePenalty(int ticks)
	{
		planePenalty = ticks;
		recompute();
	}

	public void setNearRange(int tiles)
	{
		nearRange = tiles;
		recompute();
	}

	public int getNearRange()
	{
		return nearRange;
	}

	/** Where the route stops instead of the destination {x, y, plane, inSight 1/0}, or null. */
	public int[] getStopTile()
	{
		return stopTile == null ? null : stopTile.clone();
	}

	// ---- free-to-play worlds: a player without members walks on free land only ----

	private boolean isMembers()
	{
		try
		{
			return player.toState().isMembers();
		}
		catch (IllegalArgumentException e)
		{
			return true;
		}
	}

	/** The search for a free world: the same map with every step onto members land taken out (made once per map and free land). */
	private Pathfinder freeWorld(Pathfinder full)
	{
		if (full == null)
		{
			return null;
		}
		com.bestpath.core.collision.FreeLand land = ctx.freeLand();
		if (freePf == null || freePfFrom != full || freePfLand != land)
		{
			com.bestpath.core.collision.CollisionMap m = PathfinderDiagnostics.map(full).restrictTo((x, y, p) -> land.isFree(x, y));
			freePf = new Pathfinder(m, com.bestpath.core.graph.CoarseGraph.build(m), ctx.grid == null ? null : ctx.grid::get);
			freePfFrom = full;
			freePfLand = land;
		}
		return freePf;
	}

	private String freeWorldHint()
	{
		com.bestpath.core.collision.FreeLand land = ctx.freeLand();
		if (FreeLandDiagnostics.isEmpty(land))
		{
			return "no free-to-play land is marked (" + com.bestpath.tool.edit.FreeLandFile.FILE.getName()
				+ (com.bestpath.tool.edit.FreeLandFile.problem() != null ? ": " + com.bestpath.tool.edit.FreeLandFile.problem() : "") + ")";
		}
		boolean s = start != null && land.isFree(start[0], start[1]);
		boolean e = end != null && land.isFree(end[0], end[1]);
		return s && e ? null : (!s && !e ? "the start and the destination are" : !s ? "the start is" : "the destination is")
			+ " on members-only land";
	}

	/** Searches again (after any change to the points, the options or the data). */
	public void recompute()
	{
		int gen = ++generation;
		route = null;
		stopTile = null;
		searched = new int[0];
		bankPlan = null;
		boolean members = isMembers();
		Pathfinder pf = members ? ctx.pathfinder : freeWorld(ctx.pathfinder);
		freeHint = members ? null : freeWorldHint();
		if (start == null || end == null || pf == null)
		{
			busy = false;
			status = start == null ? "Left-click the start." : end == null ? "Right-click the destination." : "Waiting for the data to be built ...";
			changed();
			return;
		}
		List<Transport> all = transports();
		// what the player carries; a route may begin with a bank visit, then the bank's items count too
		List<Transport> transports = lastNeeds.carried(all);
		BankSearch bankSearch = all.size() == transports.size() ? null
			: new BankSearch(all, lastNeeds::takeOutFromBank, lastNeeds.banks(ToolBanks.list(), members ? null : ctx.freeLand()), bankTicks);
		transportCount = transports.size();
		PathQuery q = new PathQuery(start[0], start[1], start[2], end[0], end[1], end[2], transports, planePenalty,
			doorsOrNone(), doorCost);
		// Sailing is a members skill: a free-world player never sails
		com.bestpath.core.path.Sailing sail = members && sailingOn && !boats.isEmpty() ? ctx.sailing : null;
		seaHint = seaHint(sail);
		com.bestpath.core.path.Sailing.Options sailOptions = sailOptions();
		int[] sailCount = new int[1];
		int range = nearRange;
		com.bestpath.tool.extract.FlagGrid grid = ctx.grid;
		// above the main map unrelated places sit side by side: the stop tile stays in the destination's region
		boolean aboveMainMap = end[1] >= mainMapTopY;
		com.bestpath.core.path.LineOfSight.WallFlags walls = grid == null ? (x, y, p) -> 0 : grid::get;
		busy = true;
		status = sail != null ? "Searching (with sailing) ..." : "Searching ...";
		changed();
		worker.submit(() -> runSearch(gen, pf, sail, sailOptions, q, range, walls, aboveMainMap, bankSearch, sailCount));
	}

	/** A start or destination at sea needs a boat: what is missing, for the status line instead of only "no route". */
	private String seaHint(com.bestpath.core.path.Sailing sail)
	{
		com.bestpath.core.path.Sailing anySail = ctx.sailing;
		boolean seaEnd = anySail != null && (anySail.atSea(start[0], start[1], start[2]) || anySail.atSea(end[0], end[1], end[2]));
		return !seaEnd || sail != null ? null
			: sailingOn ? "add a boat with Boats ..." : boats.isEmpty() ? "tick Sailing and add a boat with Boats ..." : "tick Sailing";
	}

	/**
	 * The sailing options: docks check the Route tab's Sailing level and the test player's quests, as the plugin does;
	 * bottled boats need their bottles in the inventory (the Boats dialog's count is added to it).
	 */
	private com.bestpath.core.path.Sailing.Options sailOptions()
	{
		com.bestpath.core.req.PlayerState questsOf;
		try
		{
			questsOf = withBottles(player, bottles);
		}
		catch (IllegalArgumentException e)
		{
			questsOf = com.bestpath.core.req.PlayerStateBuilder.maxed();
		}
		return new com.bestpath.core.path.Sailing.Options(sailingLevel, questsOf, boats, 0, shipwrights, turnCost);
	}

	/** The search on the worker thread; its answer goes back to the Swing thread. */
	private void runSearch(int gen, Pathfinder pf, com.bestpath.core.path.Sailing sail, com.bestpath.core.path.Sailing.Options sailOptions,
		PathQuery q, int range, com.bestpath.core.path.LineOfSight.WallFlags walls, boolean aboveMainMap, BankSearch bankSearch, int[] sailCount)
	{
		Searched s;
		int[] sc;
		try
		{
			s = search(pf, sail, sailOptions, q, range, walls, aboveMainMap, bankSearch);
			sc = PathfinderDiagnostics.lastSearchedComponents(pf);
		}
		catch (RuntimeException | OutOfMemoryError ex)
		{
			String msg = "Search failed: " + ex;
			SwingUtilities.invokeLater(() ->
			{
				if (gen == generation)
				{
					busy = false;
					status = msg;
					changed();
				}
			});
			return;
		}
		int sailed = sailCount[0];
		SwingUtilities.invokeLater(() -> searchDone(gen, s, sailed, sc, aboveMainMap));
	}

	/** Takes a finished search's answer (Swing thread), unless a newer search was started meanwhile. */
	private void searchDone(int gen, Searched s, int sailed, int[] sc, boolean aboveMainMap)
	{
		if (gen != generation)
		{
			return; // a newer search was started meanwhile
		}
		int[] st = s.stop;
		busy = false;
		route = s.route;
		bankPlan = s.plan;
		sailTransports = sailed;
		stopTile = st != null && (st[0] != end[0] || st[1] != end[1] || st[2] != end[2]) ? st : null;
		nothingInRange = st == null;
		confined = aboveMainMap;
		searched = sc != null ? sc : new int[0];
		status = describe();
		changed();
	}

	/**
	 * A Route tab search's answer: the route, where it stops ({x, y, plane, inSight} or null) and how a bank first was
	 * weighed (null: not tried).
	 */
	public static final class Searched
	{
		public final Route route;
		public final int[] stop;
		public final com.bestpath.core.path.BankFirst.Plan plan;

		Searched(Route route, int[] stop, com.bestpath.core.path.BankFirst.Plan plan)
		{
			this.route = route;
			this.stop = stop;
			this.plan = plan;
		}
	}

	/**
	 * Going to a bank first for the Route tab's search (the plugin does the same through PluginRouter): the
	 * transports with the bank's items, what each needs from the bank, the banks the test player may use, and the
	 * bank stop's ticks.
	 */
	public static final class BankSearch
	{
		final List<Transport> withBank;
		final java.util.function.Function<Transport, List<com.bestpath.core.req.BankItem>> itemsOf;
		final com.bestpath.core.path.BankFirst.Banks banks;
		final int ticks;

		public BankSearch(List<Transport> withBank, java.util.function.Function<Transport, List<com.bestpath.core.req.BankItem>> itemsOf,
			com.bestpath.core.path.BankFirst.Banks banks, int ticks)
		{
			this.withBank = withBank;
			this.itemsOf = itemsOf;
			this.banks = banks;
			this.ticks = ticks;
		}
	}

	/**
	 * The Route tab's search (also used by the plugin export's check and the clue check, so "the tool's route" is this code): the
	 * Fast search, with sailing when {@code sail} is set (walking, or walking to a dock, sailing one of the boats
	 * and walking on, or ending at sea); an unreachable destination ends at the nearest reachable tile that sees
	 * it (or, failing that, the nearest).
	 */
	public static Searched search(Pathfinder pf, com.bestpath.core.path.Sailing sail, com.bestpath.core.path.Sailing.Options sailOptions,
		PathQuery q, int range, com.bestpath.core.path.LineOfSight.WallFlags walls, boolean aboveMainMap)
	{
		return search(pf, sail, sailOptions, q, range, walls, aboveMainMap, null);
	}

	/**
	 * As above, and going to a bank first when that is quicker ({@code bank}; null: not tried), exactly as the
	 * plugin does ({@link com.bestpath.core.path.BankFirst#tryBank}).
	 */
	public static Searched search(Pathfinder pf, com.bestpath.core.path.Sailing sail, com.bestpath.core.path.Sailing.Options sailOptions,
		PathQuery q, int range, com.bestpath.core.path.LineOfSight.WallFlags walls, boolean aboveMainMap, BankSearch bank)
	{
		// the real destination, settled once before any search: the route with what the player carries, sailing and
		// the bank-first route all go to this one tile (core RouteDestination, the same as the plugin's PluginRouter)
		boolean bankMatters = bank != null && bank.withBank.size() != q.getTransports().size() && !bank.banks.isEmpty();
		PathQuery wide = bankMatters ? q.withTransports(bank.withBank) : q;
		com.bestpath.core.path.RouteDestination end = com.bestpath.core.path.RouteDestination.resolve(pf, sail, sailOptions, wide, range,
			walls, aboveMainMap);
		if (end == null)
		{
			// nothing near the destination can be reached: no route (the search proves that at once)
			return new Searched(sail == null ? pf.find(q) : Route.notFound(0, 0), null, null);
		}
		// a tile only a boat may bring the player to that no route reaches: its fallback, settled with it (which may have
		// its own: open water by a rock at sea -> a tile reached through a dock -> the walking rule's tile)
		while (true)
		{
			Searched s = searchTo(pf, sail, sailOptions, q, end, bank);
			if (s.route.isFound() || !end.isBySea())
			{
				return s;
			}
			if (end.getFallback() == null)
			{
				return new Searched(Route.notFound(0, 0), null, null);
			}
			end = end.getFallback();
		}
	}

	/** The route (and the bank-first one) to a settled destination ({@link com.bestpath.core.path.RouteDestination}). */
	private static Searched searchTo(Pathfinder pf, com.bestpath.core.path.Sailing sail, com.bestpath.core.path.Sailing.Options sailOptions,
		PathQuery q, com.bestpath.core.path.RouteDestination end, BankSearch bank)
	{
		PathQuery used = end.getX() == q.getDestX() && end.getY() == q.getDestY() && end.getPlane() == q.getDestPlane() ? q
			: q.withDestination(end.getX(), end.getY(), end.getPlane());
		int[] stop = end.getStop();
		if (sail != null)
		{
			// sailing: walking, or walking to a dock, sailing one of the boats, and walking on (or ending at sea); weighed
			// with the bank start as the plugin does (core BankFirst.withSailing: the bank route first, then sailing only
			// has to beat it); a destination on open sea: where the boat stops
			com.bestpath.core.path.BankFirst.Weighed w = bank == null
				? com.bestpath.core.path.BankFirst.withSailing(pf, sail, sailOptions, used, end.isAtSea(), stop, used.getTransports(),
				com.bestpath.core.path.BankFirst.Banks.NONE, 0, null, t -> java.util.List.of())
				: com.bestpath.core.path.BankFirst.withSailing(pf, sail, sailOptions, used, end.isAtSea(), stop, bank.withBank, bank.banks,
				bank.ticks, null, bank.itemsOf);
			return new Searched(w.getRoute(), w.getStop(), w.getPlan());
		}
		// the Fast search (core Pathfinder) finds every route
		Route r = pf.find(used);
		if (bank != null)
		{
			com.bestpath.core.path.BankFirst.Plan plan = com.bestpath.core.path.BankFirst.tryBank(pf, used, r, stop,
				bank.withBank, bank.banks, bank.ticks, null, bank.itemsOf);
			if (plan != null)
			{
				return new Searched(plan.getRoute(), plan.getStop(), plan);
			}
		}
		return new Searched(r, stop, null);
	}

	public int getDoorCost()
	{
		return doorCost;
	}

	public void setDoorCost(int ticks)
	{
		doorCost = Math.max(0, ticks);
		recompute();
	}

	private com.bestpath.core.path.DoorMap doorsOrNone()
	{
		com.bestpath.core.path.DoorMap d = ctx.doors;
		return d != null ? d : com.bestpath.core.path.DoorMap.none();
	}

	public int[] getSearched()
	{
		return searched;
	}

	public boolean isShowSearched()
	{
		return showSearched;
	}

	public void setShowSearched(boolean show)
	{
		showSearched = show;
		changed();
	}

	public com.bestpath.core.graph.CoarseGraph graph()
	{
		return ctx.graph;
	}

	public int getMainMapTopY()
	{
		return mainMapTopY;
	}

	public void setMainMapTopY(int y)
	{
		mainMapTopY = y;
		recompute();
	}

	/** The transports for the search: those in use (not built in yet included) and, if chosen, unconfirmed suggestions. */
	private List<Transport> transports()
	{
		List<TransportRecord> inUse = new ArrayList<>();
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			if (!t.deleted)
			{
				inUse.add(t.record);
			}
		}
		Map<String, TransportRecord> testLater = ctx.effectiveTestLater();
		Set<Integer> ignored = ctx.effectiveIgnoredRegions();
		// suggested teleports decided in changes not built in yet (built ones are no longer suggested)
		Set<String> decidedTeleports = new java.util.HashSet<>(testLater.keySet());
		for (com.bestpath.tool.edit.Edit e : ctx.edits.getUnbuilt())
		{
			if (e.type == com.bestpath.tool.edit.Edit.Type.REJECT && e.on)
			{
				decidedTeleports.add(e.target);
			}
			else if (e.type == com.bestpath.tool.edit.Edit.Type.TRANSPORT && e.transport.suggestionId != null)
			{
				decidedTeleports.add(e.transport.suggestionId);
			}
		}
		List<TransportRecord> tests = new ArrayList<>();
		for (TransportRecord r : testLater.values())
		{
			if (r != null)
			{
				tests.add(r); // as saved (perhaps moved), still unconfirmed
			}
		}
		com.bestpath.core.req.PlayerState state;
		try
		{
			state = player.toState();
		}
		catch (IllegalArgumentException e)
		{
			state = com.bestpath.core.req.PlayerStateBuilder.maxed();
		}
		RequirementFilter needs = new RequirementFilter(state, startWilderness);
		lastNeeds = needs;
		List<Transport> ours = RouteTransports.build(inUse, tests, ctx.transports, useUnconfirmed,
			c -> ctx.decided(c, testLater) || ignored.contains((c.getX() >> 6) << 8 | (c.getY() >> 6)),
			decidedTeleports::contains,
			(TransportCandidate c, int tx, int ty) -> ctx.standingTile(c, tx, ty), needs);
		requirementSummary = needs.summary();
		List<Transport> all = new ArrayList<>(ours);
		if (!state.isMembers())
		{
			// a free world: only transports from free land to free land (a teleport: its landing)
			com.bestpath.core.collision.FreeLand land = ctx.freeLand();
			int before = all.size();
			all.removeIf(t -> !land.isFree(t.getEndX(), t.getEndY()) || t.hasStart() && !land.isFree(t.getStartX(), t.getStartY()));
			requirementSummary = (requirementSummary == null || requirementSummary.isEmpty() ? "" : requirementSummary + "; ")
				+ (before - all.size()) + " left out: an end on members-only land";
		}
		return all;
	}

	private String describe()
	{
		StringBuilder sb = new StringBuilder();
		if (freeHint != null)
		{
			sb.append("Free-to-play player: ").append(freeHint).append(".   ");
		}
		if (seaHint != null && (!route.isFound() || stopTile != null))
		{
			sb.append("The start or destination is at sea: ").append(seaHint).append(" (Route tab, Sailing row).   ");
		}
		if (stopTile != null)
		{
			int away = Math.max(Math.abs(stopTile[0] - end[0]), Math.abs(stopTile[1] - end[1]));
			sb.append("Destination can't be reached: stops ").append(away).append(away == 1 ? " tile" : " tiles")
				.append(" away at ").append(stopTile[0]).append(", ").append(stopTile[1])
				.append(stopTile[3] == 1 ? " (in sight)" : stopTile[3] == com.bestpath.core.path.RouteDestination.OPEN_WATER
					? " (the spot itself is blocked: on open water)" : " (nearest; nothing in range has it in sight)")
				.append(confined ? ", kept inside its region (above the main map)" : "").append(".   ");
		}
		if (!route.isFound())
		{
			sb.append(nothingInRange
				? "No route: nothing within " + nearRange + " tiles of the destination can be reached"
				+ (confined ? " (inside its region: above the main map)" : "") : "No route found");
		}
		else
		{
			sb.append(route.getTicks()).append(" ticks, ").append(route.getSteps().size()).append(" steps");
			if (bankPlan != null && bankPlan.isBanked())
			{
				Route without = bankPlan.getWithoutBank();
				for (Route.Step st : route.getSteps())
				{
					if (st.getKind() == Route.Step.Kind.BANK)
					{
						sb.append(", starts at ").append(st.getBankName()).append(" (").append(st.getTicks()).append(" ticks there");
					}
				}
				sb.append(without.isFound() ? "; without the bank " + without.getTicks() + " ticks)" : "; no route without the bank)");
			}
			int u = RouteDiagnostics.unconfirmedCount(route);
			if (u > 0)
			{
				sb.append(", ").append(u).append(" unconfirmed transport").append(u == 1 ? "" : "s");
			}
		}
		if (requirementSummary != null)
		{
			sb.append("   (").append(requirementSummary).append(")");
		}
		sb.append(String.format(java.util.Locale.ROOT, "   (%.0f ms, %,d tiles searched, %,d transports available%s)",
			route.getNanos() / 1e6, route.getExpanded(), transportCount + sailTransports,
			sailTransports > 0 ? String.format(java.util.Locale.ROOT, ", %,d of them sailing", sailTransports) : ""));
		return sb.toString();
	}

	public int[] getStart()
	{
		return start == null ? null : start.clone();
	}

	public int[] getEnd()
	{
		return end == null ? null : end.clone();
	}

	/** The latest result, or null (none yet, or searching). */
	public Route getRoute()
	{
		return route;
	}

	public String getStatus()
	{
		return status;
	}

	public boolean isBusy()
	{
		return busy;
	}

	public boolean isUseUnconfirmed()
	{
		return useUnconfirmed;
	}

	public int getPlanePenalty()
	{
		return planePenalty;
	}

	public void shutdown()
	{
		worker.shutdownNow();
	}
}
