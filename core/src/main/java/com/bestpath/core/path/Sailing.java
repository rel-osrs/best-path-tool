package com.bestpath.core.path;

import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaChannels;
import com.bestpath.core.sea.SeaGraph;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaRouter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Routes that mix walking and sailing. One sea graph ({@link SeaGraph}) serves every boat. Each route is found in
 * three parts, which is exact because a route sails at most once (the boat stays where the player leaves it):
 * <ol>
 * <li>the ticks from the start to each dock a boat can be boarded at, walking and with any transport or teleport
 * (one walking search per dock, and only for the docks a sea leg that may end the route in time starts from; until
 * then a lower bound stands in, see below);</li>
 * <li>per boat, one search over the sea graph from every dock the player can board it at (starting with those
 * ticks) to the docks the destination may be reached from and to a destination at sea ({@link SeaRouter}: 16
 * headings, turning costs extra, aimed at the ends);</li>
 * <li>the walk from wherever the boat is left to the destination: the normal search, with each dock offered as a
 * starting point at its arrival ticks.</li>
 * </ol>
 * A route that sails becomes: (walk to the dock), (bring the boat), Board, Sail (the boat's course: straight legs in
 * its 16 exact headings, 22.5 degrees apart, {@link Route.Step#getCorners()}, {@link Route.Step#getCoursePoints()}),
 * Leave, (walk on). When a shipwright brings the boat and the shipwright's place is known, the player walks to the
 * shipwright first: (walk to the shipwright), Recover-boat there, (walk to the dock), Board, ...
 *
 * <p>To keep sailing fast, parts 1 and 2 go round until they agree. The sea search starts each boarding at a lower
 * bound of its ticks (no search) and knows the least each end can still add (leaving, the shortest possible walk on,
 * {@link SailingBounds#lowerBoundsOn}); legs that cannot end the route before the walk does are not searched. Whenever the best
 * leg to an end that may still end the route in time starts at a boarding whose real ticks are not known, those are
 * searched and the sea searched again. In the end every leg that matters starts at real ticks, so the legs and their
 * ticks are those of searching every dock first; most routes need no walking search to a dock at all.
 *
 * <p>Rules: a boat is boarded where it is moored. A shipwright brings it to the shipwright's dock for coins and
 * {@link #RECOVER_TICKS} more (the player first walks to the shipwright, see {@link SailingWorld#standTile}, then to the dock:
 * both walks counted). A teleport focus brings it to any dock (a teleport, {@link #TELEPORT_TICKS}); a greater focus
 * also takes the player to the boat's dock (a teleport from anywhere). Boarding and leaving take the options'
 * boarding ticks each ({@link Dock#BOARD_TICKS} unless the player changes them). Docks need their Sailing level and
 * their quest finished, for the player the options name; a boat is still boarded where it is moored. A boat's course
 * ends only at a dock (the player gets off and walks on) or on a destination of open sea, the boat over it; never
 * beside a rock, the coast or a tile no one can stand on ({@link RouteDestination} settles such places first). A
 * start at sea means the player is on a boat (the one marked {@link Boat#AT_START}, else the first that is at a dock
 * or unknown).
 *
 * <p>Boats that are not at a dock: a capsized or lost boat is brought by a shipwright like any other (same coins,
 * {@link #RECOVER_TICKS} more; only when shipwrights are allowed; no teleport focus, the game's lists leave such
 * boats out). A bottled boat is used only while the player carries a full boat bottle for it in the inventory
 * ({@link #bottlesInHand}); it is then released at any dock the player may use, {@link #RELEASE_TICKS} (like
 * boarding), no coins, and boarded there.
 *
 * <p>The work is shared out: {@link SailingWorld} holds the docks and the sea graph every route shares,
 * {@link SailingSearch} searches one route (parts 1 to 3 above), and {@link SailingBounds} gives the lower bounds that
 * aim it. This class is the front the plugin and the other searches use.
 *
 * <p>Not thread-safe: use from one search thread.
 */
public final class Sailing
{
	/** Extra ticks when a shipwright brings the boat. */
	public static final int RECOVER_TICKS = 10;
	/** Ticks of a teleport (a teleport focus bringing the boat, a greater focus taking the player to it). */
	public static final int TELEPORT_TICKS = 10;
	/** Releasing a bottled boat at a dock takes as long as boarding. */
	public static final int RELEASE_TICKS = Dock.BOARD_TICKS;
	/** Sea tiles further than this from an asked-for point are not used instead of it. */
	public static final int NEAR_TILES = 10;
	/**
	 * "Ocean only" routes: the boat ends at a dock or on open sea at most this many tiles (Chebyshev) from the
	 * destination; a destination farther from both has no ocean-only route.
	 */
	public static final int OCEAN_ONLY_RANGE = 15;
	/** Default turn cost: half a tick (50 hundredths) per 22.5 degrees turned; a tuning value. */
	public static final int DEFAULT_TURN_COST = 50;

	/** "No route" / "no bound": larger than any real number of ticks. */
	static final int NONE = Integer.MAX_VALUE / 4;

	/** The docks and the sea graph, shared by every route. */
	final SailingWorld world;
	/** The search of one route at a time, and what it last worked out. */
	final SailingSearch search;

	/**
	 * @param docks    all docks; those off the main ocean, without water the sea
	 *                 graph joins, or without a land tile on the walking map are left out
	 * @param channels the sea graph's channels ({@link SeaChannels}): the plugin reads them from its sea file (finding
	 *                 them floods the whole sea, too slow for the plugin). Null: found here.
	 * @param graph    the walking map's components (to pick each dock's best land tile)
	 * @param previous a search whose sea graph is kept when the sea and the buoys are the same; null: none
	 */
	public Sailing(SeaGrid grid, List<Dock> docks, List<int[]> channels, CoarseGraph graph, Sailing previous)
	{
		world = new SailingWorld(grid, docks, channels, graph, previous == null ? null : previous.world);
		search = new SailingSearch(world);
	}

	/** The docks sailing uses (on the main ocean, joined to the land and the sea graph). */
	public List<Dock> getDocks()
	{
		return Collections.unmodifiableList(world.docks);
	}

	public SeaGraph getSeaGraph()
	{
		return world.seaGraph;
	}

	/** What one route may use. */
	public static final class Options
	{
		final int sailingLevel;
		/** Whose quests the docks check; null = every dock quest counts as done (for hand-made docks). */
		final PlayerState player;
		final List<Boat> boats;
		/** How many of the player's boats are bottled, counting those routes may not use (see {@link #bottlesInHand}). */
		final int bottledBoats;
		final boolean shipwrights;
		final int turnCost;
		/**
		 * Never leave the boat the player is on at sea (a setting, off by default): a route starting on it begins by
		 * sailing it to a dock that has a shipwright, or to an open-sea destination; after leaving it there the player
		 * may go on any way. Off: a route may leave it at sea when that is quicker (it is then lost: a shipwright may
		 * bring it back).
		 */
		final boolean keepBoat;
		/**
		 * Ticks to board a boat, and again to leave it or release it from its bottle (player adjustable;
		 * {@link Dock#BOARD_TICKS} by default).
		 */
		final int boardTicks;

		/**
		 * @param player       whose finished quests the docks check (null = all done), and whose
		 *                     inventory holds the boat bottles (null = none)
		 * @param boats        the boats routes may use
		 * @param bottledBoats how many of ALL the player's boats are bottled (also those routes may not use: a bottle
		 *                     carried could hold one of those)
		 * @param shipwrights  whether shipwrights may bring a boat (costs coins)
		 * @param turnCost     hundredths of a tick per 22.5 degrees the boat turns
		 */
		public Options(int sailingLevel, PlayerState player, List<Boat> boats, int bottledBoats, boolean shipwrights, int turnCost)
		{
			this.sailingLevel = sailingLevel;
			this.player = player;
			this.boats = List.copyOf(boats);
			this.bottledBoats = Math.max(bottledBoats, bottled(this.boats));
			this.shipwrights = shipwrights;
			this.turnCost = Math.max(0, turnCost);
			this.keepBoat = false;
			this.boardTicks = Dock.BOARD_TICKS;
		}

		private Options(Options o, boolean keepBoat, int boardTicks)
		{
			this.sailingLevel = o.sailingLevel;
			this.player = o.player;
			this.boats = o.boats;
			this.bottledBoats = o.bottledBoats;
			this.shipwrights = o.shipwrights;
			this.turnCost = o.turnCost;
			this.keepBoat = keepBoat;
			this.boardTicks = Math.max(0, boardTicks);
		}

		/** The same options with "never leave my boat at sea" on or off. */
		public Options withKeepBoat(boolean keep)
		{
			return keep == keepBoat ? this : new Options(this, keep, boardTicks);
		}

		/** The same options with these ticks to board or leave a boat. */
		public Options withBoardTicks(int ticks)
		{
			return ticks == boardTicks ? this : new Options(this, keepBoat, ticks);
		}
	}

	/** How many of these boats are bottled. */
	public static int bottled(List<Boat> boats)
	{
		int n = 0;
		for (Boat b : boats)
		{
			n += b.isBottled() ? 1 : 0;
		}
		return n;
	}

	/**
	 * Whether routes may use the player's bottled boats (only with the boat's bottle in the inventory). Every full
	 * boat bottle is the same item ({@link Boat#BOTTLE_FULL}), so a bottle can't be matched to its boat: the bottled
	 * boats are used only when the inventory holds at least one full bottle per bottled boat (then each one's bottle is
	 * surely carried); with fewer, none of them (never guessed which).
	 *
	 * @param bottledBoats how many of the player's boats are bottled (all of them, also those routes may not use)
	 */
	public static boolean bottlesInHand(PlayerState player, int bottledBoats)
	{
		return player != null && bottledBoats > 0 && player.getInventory().getOrDefault(Boat.BOTTLE_FULL, 0) >= bottledBoats;
	}

	/**
	 * The boat slot a sailing step's transport id names ("sail:2", "sail-board:2", "sail-bring:2", "sail-release:2",
	 * "sail-leave:2", "sail-teleport:2"), or -1 (not a sailing step).
	 */
	public static int boatSlot(String transportId)
	{
		if (transportId == null || !transportId.startsWith("sail:") && !transportId.startsWith("sail-")
			|| transportId.startsWith("sail-arrive:"))
		{
			return -1;
		}
		int colon = transportId.lastIndexOf(':');
		try
		{
			return colon < 0 ? -1 : Integer.parseInt(transportId.substring(colon + 1));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	/**
	 * Whether a route starting here starts on a boat: the tile is open sea (main ocean), or an unwalkable tile with
	 * main ocean within {@link #NEAR_TILES} (the boat entity's tile may lie on a rock or the shore beside the water).
	 * Not for destinations: a boat ends only at a dock or on open sea ({@link #isOpenSea}).
	 */
	public boolean atSea(int x, int y, int plane)
	{
		return world.atSea(x, y, plane);
	}

	/**
	 * Whether the tile is open sea (the main ocean): besides a dock, the only place a boat may end.
	 */
	public boolean isOpenSea(int x, int y, int plane)
	{
		return world.isOpenSea(x, y, plane);
	}

	/**
	 * The walking rule's tile for a destination on land ({@link Pathfinder#routeEnd}) when the boats count too: the
	 * land tile of every dock the player may leave a boat at ({@code o}: level, quest) is a place the player can be,
	 * as if a teleport landed there (part 3 of {@link #route} offers each dock as such a starting point). So the tile
	 * itself when it can be reached on foot from such a dock, else the nearest tile in range that can and sees it (an
	 * island's shore seen from a rock in the water too), else the nearest such tile; null when none. The boat itself
	 * never ends next to land: a destination on land is reached from a dock. No search, the same coarse rules as
	 * {@link Pathfinder#routeEnd}; used to settle the route's real destination ({@link RouteDestination}).
	 */
	public int[] routeEndByDocks(PathQuery q, Options o, Pathfinder pf, int range, LineOfSight.WallFlags walls, boolean ownRegionOnly)
	{
		List<Transport> all = new ArrayList<>(q.getTransports());
		for (int k = 0; k < world.docks.size(); k++)
		{
			if (SailingWorld.usable(world.docks.get(k), o))
			{
				int[] t = world.land.get(k);
				all.add(Transport.teleport("sail-arrive:" + k, "Sail to " + world.docks.get(k).getName(), t[0], t[1], t[2], o.boardTicks, true));
			}
		}
		return all.size() == q.getTransports().size() ? pf.routeEnd(q, range, walls, ownRegionOnly)
			: pf.routeEnd(q.withTransports(all), range, walls, ownRegionOnly);
	}

	/**
	 * For a blocked spot out in the open ocean (a rock with no land in sight), which should still get a route: the
	 * nearest main-ocean tile (straight distance, within {@link #NEAR_TILES} Chebyshev; ties: lower x, then lower y)
	 * that one of {@code boats} can be at, by the sea search's own data: the boat fits there (its hard clearance), its
	 * parts allow the tile's sea area, and the tile joins the sea graph. Null when none (no boats, or no such water
	 * near). {x, y}.
	 */
	public int[] openWaterNear(int x, int y, int plane, List<Boat> boats)
	{
		return world.openWaterNear(x, y, plane, boats);
	}

	/**
	 * The best route, walking or sailing, with {@code pf} as the walking search; it skips sailing to docks from which
	 * the destination cannot be reached at all.
	 */
	public Route route(PathQuery q, Options o, Pathfinder pf)
	{
		return route(q, o, pf::find, pf);
	}

	/**
	 * As {@link #route(PathQuery, Options, Pathfinder)} to a destination already settled ({@link RouteDestination}):
	 * {@code seaEnd} says whether the route ends on the boat (the destination is open sea: the boat over it,
	 * {@link SailingWorld#destinationFit}); false: the route ends on the destination tile itself, on foot (the boat only to a dock,
	 * then walking on), never on a boat near it.
	 */
	public Route route(PathQuery q, Options o, Pathfinder pf, boolean seaEnd)
	{
		return route(q, o, pf::find, pf, seaEnd);
	}

	/**
	 * As {@link #route(PathQuery, Options, Pathfinder, boolean)}, looking only for sea routes of fewer than {@code beat}
	 * ticks (the ticks of a route already found, e.g. one starting at a bank, that sailing has to beat; a sea route
	 * of {@code beat} ticks or more is never searched for). {@code walk}: {@link #walkingRoute} of the same question,
	 * already searched (not searched again). Returns the sea route when one is quicker than both {@code beat} and the
	 * walk, else the walk. Not for a route that keeps the boat ({@link #keepsBoat}).
	 */
	public Route route(PathQuery q, Options o, Pathfinder pf, boolean seaEnd, int beat, Route walk)
	{
		return route(q, o, pf::find, pf, seaEnd, beat, walk);
	}

	/**
	 * Where a route stops: for a destination on open sea ({@code seaEnd}) where the boat stops, the last tile of the
	 * route (the destination under its hull; for a rock out at sea, open water near it), keeping {@code stop}'s in-sight
	 * flag; else {@code stop} itself.
	 */
	public static int[] seaStop(Route r, int[] stop, boolean seaEnd)
	{
		if (!seaEnd || !r.isFound() || r.getSteps().isEmpty())
		{
			return stop;
		}
		int[] t = r.getSteps().get(r.getSteps().size() - 1).getTiles();
		int last = t[t.length - 1];
		return new int[]{Tiles.x(last), Tiles.y(last), Tiles.plane(last), stop[3]};
	}

	/**
	 * The walking route sailing weighs its sea routes against (the first search of {@link #route}): with what the
	 * player may use, and a greater teleport focus's teleport to its boat's dock.
	 */
	public Route walkingRoute(PathQuery q, Options o, Pathfinder pf)
	{
		return pf.find(world.withFocus(q, o, world.aboardAt(q, o)));
	}

	/**
	 * True when a route from {@code q}'s start must keep the boat the player is on ({@link Options#keepBoat}): its
	 * walking route is then no route at all.
	 */
	public boolean keepsBoat(PathQuery q, Options o)
	{
		return o.keepBoat && world.aboardAt(q, o) != null;
	}

	/** The greater teleport focus teleports a route from {@code q}'s start may use (as {@link #walkingRoute} does). */
	List<Transport> focusTeleports(PathQuery q, Options o)
	{
		return world.focusTeleports(o, world.aboardAt(q, o));
	}

	/**
	 * For lower bounds only, never in a route (a route from a bank that sails): per dock a boat can be boarded at and
	 * each other dock it may be left at, a transport between their land tiles costing the least such a sea leg can
	 * take, whichever boat: bringing the boat there, boarding, sailing the straight distance between the buoys' water
	 * at the boat's speed (rounded down, as {@link SailingSearch#useless}) and leaving. A coarse search with these added never says
	 * more than the ticks of a route that sails once (the walk to the dock, by a shipwright too, is never shorter than
	 * the shortest walk there). Not for a start or destination at sea. Through the boat-only transports of
	 * {@code boatTransports} too (their ticks), each boat those it fits.
	 */
	List<Transport> seaBounds(Options o, int planeChangePenalty, List<Transport> boatTransports)
	{
		List<SeaLink> all = world.links(boatTransports, planeChangePenalty);
		int n = world.docks.size();
		int[] least = new int[n * n];
		Arrays.fill(least, NONE);
		boolean bottles = bottlesInHand(o.player, o.bottledBoats);
		for (Boat b : o.boats)
		{
			double perTile = 100 / b.speed() - 0.125;
			List<SeaLink> mine = SailingWorld.linksFor(all, b);
			for (SeaBoarding bd : world.boardings(b, false, bottles, o))
			{
				if (bd.dock < 0)
				{
					continue;
				}
				for (int j = 0; j < n; j++)
				{
					if (j == bd.dock || !SailingWorld.usable(world.docks.get(j), o))
					{
						continue; // a leg back to the same dock is never used
					}
					long sail = world.dockSail(bd.dock, j, perTile, mine);
					if (sail >= SailingBounds.FAR)
					{
						continue;
					}
					int ticks = bd.extra + o.boardTicks + (int) (sail / 100) + o.boardTicks;
					least[bd.dock * n + j] = Math.min(least[bd.dock * n + j], ticks);
				}
			}
		}
		List<Transport> out = new ArrayList<>();
		for (int k = 0; k < n; k++)
		{
			for (int j = 0; j < n; j++)
			{
				int c = least[k * n + j];
				if (c < NONE)
				{
					int[] a = world.land.get(k);
					int[] e = world.land.get(j);
					int ticks = Math.max(0, c - (a[2] != e[2] ? planeChangePenalty : 0)); // its cost is the bound itself
					out.add(Transport.between("sail-bound:" + k + ":" + j, "Sail from " + world.docks.get(k).getName() + " to "
						+ world.docks.get(j).getName() + " (at least)", a[0], a[1], a[2], e[0], e[1], e[2], ticks, false, true));
				}
			}
		}
		return out;
	}

	/**
	 * The best route on from the banks that may sail ({@link BankFirst}; e.g. bank, then a teleport to the dock where
	 * the boat is moored, then sail): {@code from} starts off the walking map with each bank as a teleport at its head
	 * start and the teleports shifted to the nearest bank's head start (the greater teleport focus's ones among them,
	 * {@link #focusTeleports}: none are added here); the player is on no boat. Only routes of fewer than {@code beat}
	 * ticks; not found when there is none. Its walking route (from the banks, without a boat) was weighed already, so
	 * it is not searched first.
	 */
	Route routeFromBanks(PathQuery from, Options o, Pathfinder pf, int beat, Pathfinder.StartBounds bounds)
	{
		return search.routeFromBanks(from, o, pf, beat, bounds);
	}

	Route route(PathQuery q, Options o, Function<PathQuery, Route> finder, Pathfinder reach)
	{
		return route(q, o, finder, reach, isOpenSea(q.getDestX(), q.getDestY(), q.getDestPlane()));
	}

	private Route route(PathQuery q, Options o, Function<PathQuery, Route> finder, Pathfinder reach, boolean destSea)
	{
		return route(q, o, finder, reach, destSea, NONE, null);
	}

	private Route route(PathQuery q, Options o, Function<PathQuery, Route> finder, Pathfinder reach, boolean destSea, int beat,
		Route knownWalk)
	{
		return search.route(q, o, finder, reach, destSea, beat, knownWalk, false);
	}

	/**
	 * True when an "ocean only" route to this destination may be tried: the player is on a boat at {@code q}'s start
	 * and a dock or open sea where it can be is within {@link #OCEAN_ONLY_RANGE} tiles of the destination. No search
	 * (the boat may still be unable to get there: {@link #oceanOnly} then finds no route).
	 */
	public boolean oceanOnlyNear(PathQuery q, Options o)
	{
		Boat b = world.aboardAt(q, o);
		return b != null && !world.oceanEnds(q.getDestX(), q.getDestY(), q.getDestPlane(), b, o).isEmpty();
	}

	/**
	 * The "ocean only" route (e.g. for courier tasks): the player stays on the boat they are on at {@code q}'s start
	 * the whole way, through boat-only transports too, to the first of {@link SailingWorld#oceanEnds} the boat can reach: the
	 * destination's open sea, else the nearest dock within {@link #OCEAN_ONLY_RANGE} tiles (the boat stops there, the
	 * player still on it: no leaving step), else the nearest open sea within that range. Not found when the player is
	 * not on a boat or the boat reaches none of them.
	 */
	public Route oceanOnly(PathQuery q, Options o)
	{
		return search.oceanOnly(q, o);
	}

	/**
	 * How long the last route's walking route took, to report it apart from sailing's own work; 0 when it was given
	 * ({@link #route(PathQuery, Options, Pathfinder, boolean, int, Route)}).
	 */
	public long lastWalkNanos()
	{
		return search.lastWalkNanos();
	}
}
