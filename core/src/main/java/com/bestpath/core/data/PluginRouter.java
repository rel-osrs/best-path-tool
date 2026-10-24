package com.bestpath.core.data;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.BankFirst;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.RouteDestination;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.BankItem;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.Dock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Routes on the exported data: the player's transports ({@link PlayerTransports}), doors at
 * {@value #DOOR_COST} extra ticks, plane changes +{@value #PLANE_CHANGE_PENALTY}. The route's end is
 * settled first: an unreachable destination becomes the nearest reachable tile within {@value #NEAR_RANGE}
 * tiles that has it in sight (above y {@value #MAIN_MAP_TOP_Y}, only inside its own 64x64 region). A player
 * who is not a member walks on free land only.
 *
 * <p>Only one search is kept: the one for the world type (members or free) of the latest route, members to
 * begin with. A route on the other world type drops it (and the sea routes) and builds the other one. A
 * free-world player gets "no route" for places only members can reach.
 *
 * <p>Memory: the search keeps caches between routes that only grow. When they pass the budget given here,
 * the big ones that are quick to redo are dropped after the route, and everything only when that is not
 * enough. Routes stay exactly the same (see {@link Pathfinder#clearQuickToRedo()}).
 *
 * <p>Transports can also be left out by what they are ({@link TransportFilter}, e.g. the Settings tab's
 * categories), not just by id. The same filter carries the player's own tick changes
 * ({@link TransportFilter#tickChange}, and per teleport category or network, {@link TickGroups}); routes,
 * their totals and every lower bound use the changed ticks.
 *
 * <p>Sailing: when the data has the sea and the route is given boats ({@link SailingSetup}), routes may sail
 * ({@link Sailing}): walk to a dock, bring the boat, board, sail, leave, walk on; or end at sea; or start on
 * the boat at sea. Members only, like the skill.
 *
 * <p>Making one builds the coarse graph and the search (about 0.3-0.5 s), and the sea graph when there is a
 * sea (about 0.2 s), so do it off the client thread. The first route after a change of world type does the
 * same again. One route at a time (synchronized).
 */
public final class PluginRouter
{
	/** Extra ticks per door or gate on the way. */
	public static final int DOOR_COST = 5;
	/** Extra ticks per plane change. */
	public static final int PLANE_CHANGE_PENALTY = 5;
	public static final int NEAR_RANGE = 10;
	public static final int MAIN_MAP_TOP_Y = 4224;
	/** The time breakdown's line for sailing's own work, on top of the walking search ({@link #timeLines()}). */
	public static final String SAILING_PART = "Sailing (on top of the walking search)";
	/** What the plugin lets the search keep between routes, in bytes. */
	public static final long DEFAULT_CACHE_BUDGET = 15_000_000L;

	/** The answer to one route question. */
	public static final class Answer
	{
		private final Route route;
		private final int[] stop;
		private final PlayerTransports.Result transports;
		private final boolean freeWorld;
		private final BankFirst.Plan bankPlan;

		Answer(Route route, int[] stop, PlayerTransports.Result transports, boolean freeWorld)
		{
			this(route, stop, transports, freeWorld, null);
		}

		Answer(Route route, int[] stop, PlayerTransports.Result transports, boolean freeWorld, BankFirst.Plan bankPlan)
		{
			this.route = route;
			this.stop = stop;
			this.transports = transports;
			this.freeWorld = freeWorld;
			this.bankPlan = bankPlan;
		}

		/**
		 * How going to a bank first was weighed, or null when it was not tried (nothing in the bank makes a difference,
		 * or no bank). {@link BankFirst.Plan#isBanked()} tells whether the route starts at a bank.
		 */
		public BankFirst.Plan getBankPlan()
		{
			return bankPlan;
		}

		/** Never null; not found when there is no route. */
		public Route getRoute()
		{
			return route;
		}

		/**
		 * Where the route ends {x, y, plane, inSight 1/0}: the destination itself
		 * or the tile chosen instead; null if nothing near the destination can be
		 * reached.
		 */
		public int[] getStop()
		{
			return stop == null ? null : stop.clone();
		}

		public PlayerTransports.Result getTransports()
		{
			return transports;
		}

		/** True if the route was searched on free land only (player not a member). */
		public boolean isFreeWorld()
		{
			return freeWorld;
		}
	}

	/**
	 * Leaves transports out by what they are (the line they were made from), not just by id. Also carries the
	 * player's own tick changes ({@link #tickChange(String)}, {@link #teleportCategoryTickChange},
	 * {@link #networkTickChange}).
	 */
	public interface TransportFilter
	{
		/** Nothing left out, no tick changes. */
		TransportFilter NONE = (t, line) -> false;

		/**
		 * @param line the exported line it was made from (for a network ride: the stop it goes to); null if unknown
		 * @return true if the transport may not be used
		 */
		boolean excluded(Transport t, TransportLine line);

		/**
		 * The player's own change to this transport's ticks (the - / + on its step card); 0 keeps the data's ticks.
		 * The route search and its lower bounds use the changed cost ({@link Transport#cost(int)}, never below 0).
		 * {@code transportId} as in {@link PlayerTransports} (including {@code ":back"} ids and network rides).
		 */
		default int tickChange(String transportId)
		{
			return 0;
		}

		/**
		 * The player's own change to every teleport in this category ("Teleport and travel ticks" in the Settings
		 * tab); 0 keeps the data's ticks. Added to each teleport's own change ({@link TickGroups}).
		 */
		default int teleportCategoryTickChange(String category)
		{
			return 0;
		}

		/** The player's own change of every ride of this network, as {@link #teleportCategoryTickChange}. */
		default int networkTickChange(String network)
		{
			return 0;
		}

		/** The player's own change of every transport that costs coins (the "GP" row), as above. */
		default int coinsTickChange()
		{
			return 0;
		}

		/**
		 * Avoid the Wilderness unless necessary (a player choice): the route is first searched without the Wilderness's
		 * tiles and the transports into or out of it, and searched again as usual only when that finds no route.
		 */
		default boolean avoidWilderness()
		{
			return false;
		}
	}

	/**
	 * Going to a bank first: the bank stop's ticks (the player can change them) and what the destination needs (a
	 * clue's items; null for nothing). Immutable.
	 */
	public static final class BankSetup
	{
		public static final BankSetup DEFAULT = new BankSetup(BankFirst.DEFAULT_TICKS, null);

		private final int ticks;
		private final Requirement needs;
		private final List<BankItem> fromBank;
		/** Bank detours turned off by the player: routes never begin at a bank. */
		private final boolean off;

		/** @param needs what the destination needs as a requirement line (null: nothing); checked against the player */
		public BankSetup(int ticks, Requirement needs)
		{
			this(ticks, needs, null, false);
		}

		private BankSetup(int ticks, Requirement needs, List<BankItem> fromBank, boolean off)
		{
			this.off = off;
			this.ticks = Math.max(0, ticks);
			this.needs = needs;
			this.fromBank = fromBank == null ? null : List.copyOf(fromBank);
		}

		/**
		 * For when what the destination needs is already worked out: {@code fromBank} are the items it needs that the
		 * player has only in the bank. Empty: the route begins at a bank only when that is quicker. Not empty: the
		 * route must begin at a bank, and the bank card lists them with how many to take out.
		 */
		public static BankSetup fromBank(int ticks, List<BankItem> fromBank)
		{
			return new BankSetup(ticks, null, fromBank, false);
		}

		/**
		 * This setup with bank detours allowed or not: not allowed, routes use only what the player carries and never
		 * begin at a bank, even when the destination needs something only in the bank.
		 */
		public BankSetup withDetour(boolean allowed)
		{
			return allowed == !off ? this : new BankSetup(ticks, needs, fromBank, !allowed);
		}

		/** True if routes may not begin at a bank. */
		public boolean isOff()
		{
			return off;
		}

		public int getTicks()
		{
			return ticks;
		}

		/**
		 * The items the destination needs that this player has only in the bank (what the bank card lists for it), or
		 * null when it needs nothing from the bank.
		 */
		List<BankItem> neededFromBank(PlayerState player)
		{
			if (fromBank != null)
			{
				return fromBank.isEmpty() ? null : fromBank;
			}
			Check need = needs == null ? Check.MET : needs.check(player);
			if (need.getOutcome() != Check.Outcome.NEEDS_BANK)
			{
				return null;
			}
			return need.getBankItems();
		}
	}

	private final PluginData data;
	private final PlayerTransports transports;
	private final long cacheBudget;
	/** The one search kept: for the world type of the latest route (see {@link #searchFor}). */
	Pathfinder search;
	/** True when {@link #search} is the members search (walks the whole map), false for free worlds. */
	private boolean membersSearch;
	/** Sea routes on the members search, or null: no sea in the data, or the search is the free-world one. */
	private Sailing sailing;
	/**
	 * How often the caches were dropped for going over the budget (the big ones, or all), and how often the small
	 * slow-to-redo tables had to go too ({@link Pathfinder#slowToRedoBytes()} alone over the budget); read by
	 * development code.
	 */
	int cacheClears;
	int fullClears;
	/** Where the time of the routes since {@link #startTimes()} went (shown in Developer details). */
	private RouteTimes times = new RouteTimes();

	/**
	 * @param cacheBudget bytes the search may keep between routes (see {@link Pathfinder#cacheBytes()})
	 */
	public PluginRouter(PluginData data, long cacheBudget)
	{
		this.data = data;
		this.transports = new PlayerTransports(data.getTransports(), data.getTables());
		this.cacheBudget = cacheBudget;
		build(true); // most players are on members worlds
	}

	/** Makes the search for members or free worlds; the one kept until now is let go first (only one is kept). */
	private void build(boolean member)
	{
		search = null;
		sailing = null;
		CollisionMap map = member ? data.getMap() : data.getMap().restrictTo((x, y, p) -> data.getFreeLand().isFree(x, y));
		// transports chaining on a tile no one walks on (the house spot) need its cell
		map = searchMap(map, data.getTransports());
		search = new Pathfinder(map, CoarseGraph.build(map), data.getWalls());
		if (member)
		{
			// the precomputed walking tables are made for the members map, so a free-world search works its own out
			// as it goes; ignored if made for another layout
			search.useWalkTables(data.getWalks());
		}
		membersSearch = member;
		if (member && data.getSea() != null)
		{
			// sailing is members only, like the skill
			// the channels found at export time (finding them floods the whole sea, too slow for the plugin)
			sailing = new Sailing(data.getSea().getGrid(), data.getSea().getDocks(), data.getSea().getChannels(), search.getGraph(), null);
			// the sea graph holds each link's clearance, so the table can go (the mask gives the same answers;
			// 1.7 MB less)
			data.getSea().getGrid().dropClearanceTable();
		}
	}

	/** Starts a new time breakdown ({@link #timeLines()}): the plugin calls it before each route it is asked for. */
	public synchronized void startTimes()
	{
		times = new RouteTimes();
	}

	/** Adds a part the caller timed itself (e.g. making the step cards) to the breakdown. */
	public synchronized void addTime(String part, long nanos)
	{
		times.add(part, nanos);
	}

	/** The time breakdown since {@link #startTimes()}, a copy. */
	public synchronized RouteTimes getTimes()
	{
		return times.copy();
	}

	/**
	 * Lines for Developer details after a route: each part's time, then whether the search uses the precomputed
	 * walking tables (walks.bin) and how big its caches are.
	 */
	public synchronized List<String> timeLines()
	{
		List<String> out = new ArrayList<>(times.lines());
		out.add("Walk tables (walks.bin): " + (search.hasWalkTables() ? "in use" : data.getWalks() == null ? "not in the data"
			: membersSearch ? "not used (made for another map)" : "not used on a free world"));
		out.add(String.format(Locale.ROOT, "Search caches: %.1f MB (budget %.0f MB)", search.cacheBytes() / 1e6, cacheBudget / 1e6));
		return out;
	}

	public PluginData getData()
	{
		return data;
	}

	/** The transports of the data, made per player ({@link RouteSetup}). */
	PlayerTransports transports()
	{
		return transports;
	}

	/**
	 * The map the plugin's search walks on: {@code map} with an empty cell for every transport hub off it
	 * ({@link SearchMaps#hubCells}). The data export checks routes on it too, so both search the same map.
	 */
	public static CollisionMap searchMap(CollisionMap map, List<TransportLine> lines)
	{
		return map.withEmptyCells(SearchMaps.hubCells(map, lines));
	}

	/** A full boat bottle in the inventory (sailing: a bottled boat; see {@link Sailing#bottlesInHand}). */
	static final Requirement BOTTLE = new Requirement.Item("Boat bottle (full)",
		new int[]{Boat.BOTTLE_FULL}, 1, false, false);

	/**
	 * Every requirement the exported transports can need (see {@link PlayerTransports#allRequirements()}), and with
	 * the sea the docks' quests and a full boat bottle: the inputs the plugin watches.
	 */
	public List<Requirement> requirements()
	{
		List<Requirement> out = new ArrayList<>(transports.allRequirements());
		if (data.getSea() != null)
		{
			// docks check their quests, so the plugin reads those quests too
			for (Dock d : data.getSea().getDocks())
			{
				if (!d.questRequirement().isNone())
				{
					out.add(d.questRequirement());
				}
			}
			// a bottled boat is used only with its bottle in the inventory, so the bottle is an input
			out.add(BOTTLE);
		}
		// the banks' needs (quests, bank-form varbits), so the plugin reads those too
		out.addAll(transports.bankRequirements(data.getBanks()));
		return out;
	}

	/**
	 * The banks this player may use now: their needs met and, on a free world, on free land. "Go to the nearest bank"
	 * uses only these, as a route starting at a bank does.
	 */
	public synchronized BankFirst.Banks usableBanks(PlayerState player)
	{
		return transports.banks(data.getBanks(), player, player.isMembers() ? null : data.getFreeLand());
	}

	/** Sea routes, or null when the data has no sea or the search kept is the free-world one. */
	public synchronized Sailing getSailing()
	{
		return sailing;
	}

	/** {@link #mapPlane(CoarseGraph, int, int)} on the search kept now. Route worker only, never the client thread. */
	public synchronized int mapPlane(int x, int y)
	{
		return mapPlane(search.getGraph(), x, y);
	}

	/**
	 * The plane a world-map tile means (the world map gives no plane). Plane 0 when it has ground (a tile someone can
	 * stand on) within {@value #NEAR_RANGE} tiles, so a table in a two-storey house still means plane 0. Otherwise the
	 * plane whose ground is nearest, the lowest on a tie (e.g. the Sisterhood Sanctuary, whose ground is on plane 1);
	 * plane 0 when no plane has any. Above y {@value #MAIN_MAP_TOP_Y} only the tile's own 64x64 area counts (areas side
	 * by side there are not near each other in the game).
	 */
	public static int mapPlane(CoarseGraph graph, int x, int y)
	{
		return SearchMaps.mapPlane(graph, x, y);
	}

	/** True when the search kept is the members one. */
	public synchronized boolean isMembersSearch()
	{
		return membersSearch;
	}

	/**
	 * The route from the player's tile to a destination, leaving out what {@code filter} says, sailing the boats
	 * {@code boats} allows (none: walking only; a start on the sea is on the boat marked
	 * {@link com.bestpath.core.sea.Boat#AT_START}, else the first boat: give the boat's tile as the start), and going to
	 * a bank first when that is quicker or the destination needs something only in the bank ({@link BankFirst}).
	 *
	 * @param excluded transport ids the player skipped or blacklisted (a split two-way transport's way back is
	 *                 {@code id + ":back"}; it is excluded with its id too)
	 */
	public synchronized Answer route(PlayerState player, int sx, int sy, int sp, int dx, int dy, int dp,
		Predicate<String> excluded, TransportFilter filter, SailingSetup boats, BankSetup bank)
	{
		try
		{
			if (withoutWildernessFirst(filter, sx, sy, dx, dy))
			{
				// first without the Wilderness's tiles and transports; with them only when that finds no route
				long t0 = System.nanoTime();
				TransportFilter outside = TransportFilters.outOfWilderness(filter, data.getWilderness());
				RouteSetup off = new RouteSetup(this, player, excluded, outside, boats, bank, data.getWilderness().closed());
				Answer a = off.routeTo(sx, sy, sp, off.destination(sx, sy, sp, dx, dy, dp));
				if (a.getRoute().isFound())
				{
					return a;
				}
				times.add("Without the Wilderness: no route, so with it", System.nanoTime() - t0);
			}
			RouteSetup s = new RouteSetup(this, player, excluded, filter, boats, bank, null);
			return s.routeTo(sx, sy, sp, s.destination(sx, sy, sp, dx, dy, dp));
		}
		finally
		{
			keepWithinBudget();
		}
	}

	/**
	 * The "ocean only" route (e.g. courier tasks; {@link Sailing#oceanOnly}): the player stays on the boat they are on
	 * and sails to a dock or open sea within {@link Sailing#OCEAN_ONLY_RANGE} tiles of the destination; with several
	 * places, the quickest such route. Nothing else is weighed (no bank, no Wilderness rule, no walking). The boat-only
	 * transports the player may use are allowed.
	 *
	 * @return the answer; {@link OceanAnswer#isNear()} is false, and no route was searched, when the player is not on a
	 *         boat or no place is near water a boat can reach
	 */
	public synchronized OceanAnswer routeOceanOnly(PlayerState player, int sx, int sy, int sp, int[] targets,
		Predicate<String> excluded, TransportFilter filter, SailingSetup boats)
	{
		try
		{
			RouteSetup s = new RouteSetup(this, player, excluded, filter, boats, BankSetup.DEFAULT, null);
			if (s.sail == null)
			{
				return new OceanAnswer(null, -1, false);
			}
			long t0 = System.nanoTime();
			boolean near = false;
			Answer best = null;
			int chosen = -1;
			for (int i = 0; i < targets.length; i++)
			{
				int t = targets[i];
				PathQuery q = new PathQuery(sx, sy, sp, Tiles.x(t), Tiles.y(t), Tiles.plane(t), s.allowed, PLANE_CHANGE_PENALTY,
					data.getDoors(), DOOR_COST);
				if (!s.sail.oceanOnlyNear(q, s.sailOptions))
				{
					continue;
				}
				near = true;
				Route r = s.sail.oceanOnly(q, s.sailOptions);
				if (r.isFound() && (best == null || r.getTicks() < best.getRoute().getTicks()))
				{
					best = new Answer(r, Sailing.seaStop(r, new int[]{Tiles.x(t), Tiles.y(t), Tiles.plane(t), 1}, true), s.usable,
						!s.member);
					chosen = i;
				}
			}
			times.add("Ocean only (sailing)", System.nanoTime() - t0);
			return new OceanAnswer(best, chosen, near);
		}
		finally
		{
			keepWithinBudget();
		}
	}

	/**
	 * True when "Ocean only path" may be offered ({@link Sailing#oceanOnlyNear}): the player is on a boat at the start
	 * and some place has a dock or open sea within {@link Sailing#OCEAN_ONLY_RANGE} tiles. No search.
	 */
	public synchronized boolean oceanOnlyNear(PlayerState player, int sx, int sy, int sp, int[] targets, SailingSetup boats)
	{
		Sailing sail = sailingFor(player.isMembers(), boats);
		if (sail == null)
		{
			return false;
		}
		Sailing.Options o = sailOptions(player, boats);
		for (int t : targets)
		{
			if (sail.oceanOnlyNear(new PathQuery(sx, sy, sp, Tiles.x(t), Tiles.y(t), Tiles.plane(t), List.of(), PLANE_CHANGE_PENALTY), o))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Sailing's options for this player and these boats: docks check the player's Sailing level and quests; a bottled
	 * boat is used only with its bottle in the inventory, counted over all the player's boats.
	 */
	static Sailing.Options sailOptions(PlayerState player, SailingSetup boats)
	{
		return new Sailing.Options(player.level(Skill.SAILING), player, boats.usableBoats(), Sailing.bottled(boats.getBoats()),
			boats.isShipwrights(), Sailing.DEFAULT_TURN_COST).withKeepBoat(boats.isKeepBoat()).withBoardTicks(boats.getBoardTicks());
	}

	/** {@link #routeOceanOnly}'s answer. */
	public static final class OceanAnswer
	{
		private final Answer answer;
		private final int chosen;
		private final boolean near;

		OceanAnswer(Answer answer, int chosen, boolean near)
		{
			this.answer = answer;
			this.chosen = chosen;
			this.near = near;
		}

		/** The route, or null: none (not near water a boat can be at, or the boat can't get there). */
		public Answer getAnswer()
		{
			return answer;
		}

		/** Index (in the places asked for) of the place the route goes to; -1 none. */
		public int getChosen()
		{
			return chosen;
		}

		/** True when the player is on a boat and some place has a dock or open sea near it (a route was searched). */
		public boolean isNear()
		{
			return near;
		}
	}

	/**
	 * True when this route is first searched without the Wilderness: the player chose to avoid it, the data knows the
	 * Wilderness, and neither the start nor any destination is in it (if one is, the Wilderness is necessary and one
	 * search is made as usual).
	 */
	private boolean withoutWildernessFirst(TransportFilter filter, int sx, int sy, int... destXy)
	{
		WildernessZones w = data.getWilderness();
		if (!filter.avoidWilderness() || w.isEmpty() || w.isWilderness(sx, sy))
		{
			return false;
		}
		for (int i = 0; i + 1 < destXy.length; i += 2)
		{
			if (w.isWilderness(destXy[i], destXy[i + 1]))
			{
				return false;
			}
		}
		return true;
	}

	/** The sea routes for this route, or null: no sea in the data, a free-world player, or no boat allowed. */
	Sailing sailingFor(boolean member, SailingSetup boats)
	{
		return member && sailing != null && boats != null && !boats.isEmpty() ? sailing : null;
	}

	/** The search for this player's world type; on a change of world type the kept search is replaced (see {@link #build}). */
	Pathfinder searchFor(boolean member)
	{
		if (member != membersSearch)
		{
			build(member);
		}
		return search;
	}

	/**
	 * After a route, when the search's caches are over the budget: drop the big ones that are quick to redo, and
	 * everything only when that is not enough (routes stay the same). Dropping everything each time would make an
	 * account with every transport redo about 800 ms of small tables on every route.
	 */
	private void keepWithinBudget()
	{
		if (search.cacheBytes() > cacheBudget)
		{
			search.clearQuickToRedo();
			cacheClears++;
			if (search.cacheBytes() > cacheBudget)
			{
				search.clearCaches();
				fullClears++;
			}
		}
	}

	/** The answer to a route question with several possible destinations. */
	public static final class NearestAnswer
	{
		private final Answer answer;
		private final int chosen;
		private final int searched;
		private final int reachable;
		private final boolean capped;

		NearestAnswer(Answer answer, int chosen, int searched, int reachable, boolean capped)
		{
			this.answer = answer;
			this.chosen = chosen;
			this.searched = searched;
			this.reachable = reachable;
			this.capped = capped;
		}

		/** The route to the chosen destination (not found if none can be reached). */
		public Answer getAnswer()
		{
			return answer;
		}

		/** Index (in the targets asked for) of the destination the route goes to; -1 if none. */
		public int getChosen()
		{
			return chosen;
		}

		/** Full searches made (the rest were ruled out by their lower bound). */
		public int getSearched()
		{
			return searched;
		}

		/** Destinations near which something can be reached (a stop tile exists and the coarse graph sees a way). */
		public int getReachable()
		{
			return reachable;
		}

		/** True if the search limit stopped the comparison early (the route found is then the best of those searched). */
		public boolean isCapped()
		{
			return capped;
		}
	}

	/** At most this many places are tried with sailing (or the bank) when none can be reached on foot. */
	public static final int MAX_SAILING_ONLY_SEARCHES = 4;

	/**
	 * The cheapest route to any of several destinations (a clue step's
	 * alternatives, kill areas, hot/cold spots). The core search has one
	 * destination per search, so this searches them one by one, cheapest
	 * first by the coarse graph's lower bound, and stops as soon as the next
	 * lower bound is above the best route found (those can't be cheaper), or
	 * after {@code maxSearches} full searches. Equally cheap routes: the
	 * earlier destination in the list wins. Each destination gets the
	 * route-end step like {@link #route}; what {@code filter} says is left out.
	 * <p>With sailing: the place is chosen on foot (the lower bounds are walking ones), then the route to it is searched
	 * again with the boats, so it sails when that is quicker. When no place can be reached on foot, the first
	 * {@value #MAX_SAILING_ONLY_SEARCHES} places are tried with the boats and the quickest is taken.
	 * <p>Going to a bank first ({@link BankFirst}) the same way as sailing: the place is chosen on foot with what the
	 * player carries, then the route to it is searched again with the bank (and the boats); when no place can be
	 * reached on foot, the first {@value #MAX_SAILING_ONLY_SEARCHES} places are tried that way. Only when the bank
	 * can make a difference (its items make more transports usable, or the places need something only in the bank).
	 * Each place's real destination is settled once, before any search ({@link RouteSetup#destination}), and every search
	 * for that place goes there.
	 *
	 * @param targets packed tiles ({@link com.bestpath.core.path.Tiles#pack})
	 */
	public synchronized NearestAnswer routeNearest(PlayerState player, int sx, int sy, int sp, int[] targets,
		Predicate<String> excluded, TransportFilter filter, int maxSearches, SailingSetup boats, BankSetup bank)
	{
		if (withoutWildernessFirst(filter, sx, sy))
		{
			// without the Wilderness first (places in it can't be reached then), with it only if none can
			long t0 = System.nanoTime();
			TransportFilter outside = TransportFilters.outOfWilderness(filter, data.getWilderness());
			NearestAnswer off = routeNearest(player, sx, sy, sp, targets, excluded, outside, maxSearches, boats, bank,
				data.getWilderness().closed());
			if (off.getChosen() >= 0 && off.getAnswer().getRoute().isFound())
			{
				return off;
			}
			times.add("Without the Wilderness: no route, so with it", System.nanoTime() - t0);
		}
		return routeNearest(player, sx, sy, sp, targets, excluded, filter, maxSearches, boats, bank, null);
	}

	private NearestAnswer routeNearest(PlayerState player, int sx, int sy, int sp, int[] targets, Predicate<String> excluded,
		TransportFilter filter, int maxSearches, SailingSetup boats, BankSetup bank, PathQuery.Closed closed)
	{
		RouteSetup s = new RouteSetup(this, player, excluded, filter, boats, bank, closed);
		try
		{
			// each place's spot on foot is enough to choose the place; the boat's spots (docks, open sea) are slow to
			// settle, so only for the places a route is searched to with the boats
			RouteDestination[] ends = new RouteDestination[targets.length];
			for (int i = 0; i < targets.length; i++)
			{
				ends[i] = s.destination(sx, sy, sp, Tiles.x(targets[i]), Tiles.y(targets[i]), Tiles.plane(targets[i]), false);
			}
			NearestAnswer walk = NearestOnFoot.search(this, s, sx, sy, sp, ends, maxSearches);
			if (s.sail == null && s.onlyCarried && s.destItems == null)
			{
				return walk;
			}
			if (walk.getChosen() >= 0)
			{
				int t = targets[walk.getChosen()];
				Answer a = s.routeTo(sx, sy, sp, s.sail == null ? ends[walk.getChosen()]
					: s.destination(sx, sy, sp, Tiles.x(t), Tiles.y(t), Tiles.plane(t)));
				return new NearestAnswer(a.getRoute().isFound() ? a : walk.getAnswer(), walk.getChosen(), walk.getSearched() + 1,
					walk.getReachable(), walk.isCapped());
			}
			Answer best = null;
			int bestIndex = -1;
			int searched = walk.getSearched();
			for (int i = 0; i < targets.length && i < MAX_SAILING_ONLY_SEARCHES; i++)
			{
				Answer a = s.routeTo(sx, sy, sp, s.sail == null ? ends[i]
					: s.destination(sx, sy, sp, Tiles.x(targets[i]), Tiles.y(targets[i]), Tiles.plane(targets[i])));
				searched++;
				if (a.getRoute().isFound() && (best == null || a.getRoute().getTicks() < best.getRoute().getTicks()))
				{
					best = a;
					bestIndex = i;
				}
			}
			return best == null ? walk : new NearestAnswer(best, bestIndex, searched, walk.getReachable(),
				walk.isCapped() || targets.length > MAX_SAILING_ONLY_SEARCHES);
		}
		finally
		{
			keepWithinBudget();
		}
	}

	/**
	 * Why there is no route: call after {@link #route} (or {@link #routeNearest}) said "no route", with the same player,
	 * start, places, exclusions and filter. See {@link NoRouteReasons}. Uses the search for the player's world type, as a
	 * route does; keeps nothing (its caches stay within the budget). For several places the explanation is about the
	 * place whose way needs the fewest things the player can't use (of at most {@value NoRouteReasons#MAX_PLACES},
	 * nearest first). With the player's boats and the Settings choices for them: with no way on land, or a place out at
	 * sea, a way by boat is looked for, and what it needs that the player can't use
	 * ({@link NoRouteReasons.Kind#BY_BOAT}). Members only, and only with the sea in the data.
	 *
	 * @param targets packed tiles ({@link Tiles#pack}), at least one
	 */
	public synchronized NoRouteReasons explainNoRoute(PlayerState player, int sx, int sy, int sp, int[] targets,
		Predicate<String> excluded, TransportFilter filter, SailingSetup boats)
	{
		return explainNoRoute(player, sx, sy, sp, targets, excluded, null, filter, boats);
	}

	/**
	 * As {@link #explainNoRoute(PlayerState, int, int, int, int[], Predicate, TransportFilter, SailingSetup)}, and the
	 * transports {@code leftOut} names are on no way at all ("Skip" on a "no route" card uses this to show the next way
	 * without it); {@code excluded} ones may still be shown as things the player can't use. Null: none.
	 */
	public synchronized NoRouteReasons explainNoRoute(PlayerState player, int sx, int sy, int sp, int[] targets,
		Predicate<String> excluded, Predicate<String> leftOut, TransportFilter filter, SailingSetup boats)
	{
		if (targets.length == 0)
		{
			throw new IllegalArgumentException("no places");
		}
		Pathfinder pf = searchFor(player.isMembers());
		long t0 = System.nanoTime();
		try
		{
			return NoRouteReasons.explain(pf, player.isMembers() ? sailing : null, boats, data, transports, player, sx, sy, sp,
				targets, excluded, leftOut, filter, TransportFilters.ticksOf(filter));
		}
		finally
		{
			times.add("Why no route", System.nanoTime() - t0);
			keepWithinBudget();
		}
	}
}
