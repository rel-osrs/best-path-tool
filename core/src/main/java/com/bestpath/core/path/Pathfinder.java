package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.graph.CoarseGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The route finder. Two stages:
 * <ol>
 * <li><b>Coarse:</b> a backward search from the destination over the coarse
 * graph: entrance sides (where a component touches the next cell), the
 * player's tile, transport ends, and one virtual "player" node that also
 * leads to every teleport. Walking between two nodes of one component
 * costs the shortest walk inside the cell between them (a lower bound of
 * any real walk). This gives every component a lower bound of the ticks
 * still needed to reach the destination, and proves "no route" quickly.</li>
 * <li><b>Tiles:</b> an A* search over tiles from the player node, aimed by
 * those bounds. The bounds never overestimate, so the result is the true
 * shortest route; the coarse stage only makes it skip what cannot help.</li>
 * </ol>
 * The work is shared out: {@link Reachability} answers "can it be reached at all", {@link CoarseSearch} is the
 * coarse stage (with the walking tables of {@link ComponentWalks}), and {@link FastSearch} is the tile stage.
 * This class ties them together and keeps the caches they share.
 * <p>
 * Thread-safe: the public methods are synchronized, so queries run one at a time (the search keeps walking
 * distances and work arrays between them).
 */
public final class Pathfinder
{
	private static final int INF = CoarseSearch.INF;

	final CollisionMap map;
	private final CoarseGraph graph;
	final FastSearch fast;
	/** Transport ends on objects, used from beside them (fairy rings). */
	final Beside beside;
	/** The map's cell keys (the fast search's numbering). */
	private final int[] cellKeys;
	/** The coarse graph's entrances as coarse nodes. */
	private final EntranceSides sides;
	/** Walks inside one cell, with the step bits of recently used cells. */
	private final CellWalks cells;
	/** The walking tables that are slow to work out again, kept between queries. */
	final ComponentWalks walks;
	private final CoarseSearch coarse;
	private final Reachability reach;

	/**
	 * @param walls the walls of the map's tiles (only the four edge walls are read), for transport ends used
	 *              from beside them ({@link Transport#usedFromBeside()}: no wall between the end and the tile
	 *              beside it); null = no walls known
	 */
	public Pathfinder(CollisionMap map, CoarseGraph graph, LineOfSight.WallFlags walls)
	{
		this.map = map;
		this.graph = graph;
		this.fast = new FastSearch(map);
		this.beside = new Beside(map, walls);
		cellKeys = map.cellKeys();
		sides = new EntranceSides(graph);
		cells = new CellWalks(map);
		walks = new ComponentWalks(graph, sides, cells);
		coarse = new CoarseSearch(graph, map.cellKeys(), sides, walks, cells, beside);
		reach = new Reachability(graph, beside, sides);
	}

	/**
	 * One number for how this search numbers its components and their entrance sides (cells, components, sides and
	 * their tiles): walking tables made for another layout are not used ({@link #useWalkTables}).
	 */
	public long graphKey()
	{
		long h = 0xcbf29ce484222325L;
		h = mix(h, graph.componentCount());
		for (int k : cellKeys)
		{
			h = mix(h, k);
		}
		for (int i = 0; i < sides.sideComp.length; i++)
		{
			h = mix(h, sides.sideComp[i]);
			for (int t : sides.sideTiles[i])
			{
				h = mix(h, t);
			}
		}
		return h;
	}

	private static long mix(long h, int v)
	{
		return (h ^ v) * 0x100000001b3L;
	}

	/** True when this search uses walking tables made ahead of time ({@link #useWalkTables}). */
	public synchronized boolean hasWalkTables()
	{
		return walks.hasShipped();
	}

	/**
	 * Uses walking tables made ahead of time (the plugin data's walks file): unpacked into the caches before working
	 * anything out (the packed tables are never dropped). Ignored (false) when they were made for another component
	 * layout ({@link #graphKey()}); routes are the same either way, only the first ones through each part of the map
	 * are quicker with them.
	 */
	public synchronized boolean useWalkTables(WalkTables tables)
	{
		if (tables == null || tables.graphKey != graphKey())
		{
			return false;
		}
		walks.useShipped(tables);
		return true;
	}

	public CoarseGraph getGraph()
	{
		return graph;
	}

	/** The game's click pathing on this map, with the doors of the last query (made again when they change). */
	private GamePath game;
	int walksKept;

	/**
	 * The walk's tiles in the game's own shape ({@link GameWalk}: the path a player clicking along it walks; same
	 * ticks, as the walk is already shortest). If that were ever dearer, the walk is kept as found and counted
	 * (read by development code).
	 */
	private int[] gameShaped(int[] tiles, DoorMap doors, int doorCost, PathQuery.Closed closed)
	{
		if (game == null || (game.getDoors() != doors && !(game.getDoors().isEmpty() && doors.isEmpty())))
		{
			game = new GamePath(map, doors);
		}
		int[] shaped = GameWalk.shape(game, tiles);
		if (WalkCost.cost(shaped, doors, doorCost) != WalkCost.cost(tiles, doors, doorCost))
		{
			walksKept++;
			return tiles;
		}
		if (closed != null)
		{
			// the search kept out of closed tiles (the Wilderness, avoided): so does the walk's game shape
			for (int t : shaped)
			{
				int x = Tiles.x(t);
				int y = Tiles.y(t);
				int c = closed.cell(x & ~63, y & ~63);
				if (c == PathQuery.Closed.SHUT || c == PathQuery.Closed.PART && closed.tile(x, y))
				{
					return tiles;
				}
			}
		}
		return shaped;
	}

	/**
	 * About how many bytes the search keeps between routes: walking distances and step bits per cell, component tables,
	 * per-tile entrance distances and the tile search's per-cell arrays. They only grow while routes are searched; the
	 * plugin calls {@link #clearQuickToRedo()} when this passes its budget, and {@link #clearCaches()} when that is not
	 * enough.
	 */
	public synchronized long cacheBytes()
	{
		long n = fast.cacheBytes();
		n += cells.bytes();
		n += game == null ? 0 : game.cacheBytes();
		n += coarse.bytes();
		n += reach.bytes();
		return n + slowToRedoBytes();
	}

	/**
	 * The part of {@link #cacheBytes()} that {@link #clearQuickToRedo()} keeps: walking distances between each
	 * component's entrance sides, from transport ends (and the tiles beside solid ends) to them, and between the
	 * transport ends of each component ({@link EndWalks}). Small (a few numbers each, about 3 MB with every transport
	 * unlocked) but slow to work out again: one walk over the cell per entrance side or end. Dropping them after every
	 * route made the next route about 800 ms slower for an account with every transport.
	 */
	public synchronized long slowToRedoBytes()
	{
		return walks.bytes();
	}

	/**
	 * Forgets everything kept between routes. Every cache holds exact values
	 * that are worked out again when next needed, so routes stay exactly the
	 * same; only the next few searches are a little slower.
	 */
	public synchronized void clearCaches()
	{
		clearQuickToRedo();
		beside.clear();
		walks.clear();
	}

	/**
	 * Forgets the caches that are big and quick to work out again: the tile search's per-cell arrays, step bits per
	 * cell, the game-shaped walks, the reachability links and the coarse stage's work arrays. Keeps the small tables
	 * that are slow to redo ({@link #slowToRedoBytes()}) and the few tiles beside solid ends. Routes stay exactly the
	 * same, as with {@link #clearCaches()}.
	 */
	public synchronized void clearQuickToRedo()
	{
		fast.clearCaches();
		game = null;
		reach.clear();
		coarse.clear();
		cells.clear();
	}

	/**
	 * The ticks from the query's start (its destination is not used) to each of {@code tiles} (every bank a route may
	 * start at), with the query's transports: one plain Dijkstra search that stops when all are found or nothing
	 * within {@code limit} ticks is left. Exact: each is the shortest route's ticks; -1 = not within the limit (or
	 * not reachable).
	 */
	public synchronized int[] ticksTo(PathQuery q, int[] tiles, int limit)
	{
		Map<Integer, Integer> indexOf = new HashMap<>();
		for (int i = 0; i < tiles.length; i++)
		{
			indexOf.putIfAbsent(tiles[i], i);
		}
		SearchTargets t = new SearchTargets(indexOf, tiles.length, limit);
		if (!indexOf.isEmpty())
		{
			fast.search(q, (ci, l) -> 0, beside, t);
		}
		int[] out = new int[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			out[i] = t.ticks[indexOf.get(tiles[i])];
		}
		return out;
	}

	/**
	 * As {@link #ticksTo(PathQuery, int[], int)}, looking only for what can still lead to a route of fewer than
	 * {@code best} ticks: tile i when its ticks plus {@code after[i]} are under it, and the nearest tile of all when
	 * its ticks plus {@code anyAfter} are (with every tile as near). The search stops as soon as no tile left can, so
	 * a tile that can't may be -1 although it is reachable. Exact for the tiles that can.
	 *
	 * @param routes when not null (as long as {@code tiles}): filled with the way to each tile found that can, and to
	 *               the nearest ones (its walks as searched: {@link #shaped} gives the game's shape); null elsewhere
	 */
	synchronized int[] ticksTo(PathQuery q, int[] tiles, int[] after, int anyAfter, int best, Route[] routes)
	{
		return ticksTo(q, tiles, after, anyAfter, best, routes, null, 0);
	}

	/**
	 * As {@link #ticksTo(PathQuery, int[], int[], int, int, Route[])}, aimed: when {@code on} is given, the search goes
	 * on from a tile only while its ticks, plus {@code onAdd}, plus {@code on}'s coarse bound there are under
	 * {@code best}. For that, {@code on} must be a lower bound of the ticks from any tile on to the end of the whole
	 * route through any tile that helps, less {@code onAdd} (the bank: the bank stop plus the coarse bounds with the
	 * bank's items, no teleports). The tiles that help are then still found with their exact ticks; the nearest tile of
	 * all ({@code anyAfter}) may not be (the caller has it).
	 */
	synchronized int[] ticksTo(PathQuery q, int[] tiles, int[] after, int anyAfter, int best, Route[] routes, StartBounds on, int onAdd)
	{
		Map<Integer, Integer> indexOf = new HashMap<>();
		int[] need = new int[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			// a tile listed twice is looked for once, with the smallest of its afters
			Integer first = indexOf.putIfAbsent(tiles[i], i);
			need[i] = first == null ? after[i] : INF;
			if (first != null)
			{
				need[first] = Math.min(need[first], after[i]);
			}
		}
		SearchTargets t = new SearchTargets(indexOf, need, anyAfter, best, Integer.MAX_VALUE / 8);
		t.routes = routes == null ? null : new Route[tiles.length];
		if (on != null)
		{
			t.on = coarse.componentBound(on.bounds);
			t.onAdd = onAdd;
		}
		if (!indexOf.isEmpty())
		{
			fast.search(q, (ci, l) -> 0, beside, t);
		}
		int[] out = new int[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			int k = indexOf.get(tiles[i]);
			out[i] = t.ticks[k];
			if (routes != null)
			{
				routes[i] = t.routes[k];
			}
		}
		return out;
	}

	int[] lastSearched = new int[0];

	/** The components (ids) the last search's tile stage entered (read by development code). */
	/**
	 * Nanoseconds of the last search in its parts: reachability, coarse, tiles, walk shapes (game's shape); read by
	 * development code.
	 */
	long[] lastPhases = new long[4];

	/** Finds the shortest route (fewest ticks). */
	public synchronized Route find(PathQuery q)
	{
		return find(q, Integer.MAX_VALUE);
	}

	/**
	 * As {@link #find(PathQuery)}, only a route of at most {@code limit} ticks: not found when every route is longer
	 * (the search stops as soon as its bound says so, instead of finding the longer route).
	 */
	public synchronized Route find(PathQuery q, int limit)
	{
		return find(q, limit, 0);
	}

	/**
	 * As {@link #find(PathQuery, int)}, for a search whose transport costs carry large extras in units of
	 * {@code extraUnit} ticks ("no route" explained: each transport the player can't use costs that much more, see
	 * com.bestpath.core.data.NoRouteReasons): the coarse stage goes on past the player's bound by a quarter of that
	 * bound less its whole units ({@code (bound mod extraUnit) / 4}, at least {@value CoarseSearch#EXTRA_MIN}) instead
	 * of a quarter of the whole bound, which would settle nearly the whole world. The route is the same shortest route
	 * either way (every bound stays a lower bound); only the sharpness off the route changes. 0 = as
	 * {@link #find(PathQuery, int)}.
	 */
	public synchronized Route find(PathQuery q, int limit, int extraUnit)
	{
		return find(q, limit, extraUnit, null);
	}

	/**
	 * As {@link #find(PathQuery, int)}, aimed by bounds already worked out ({@link #startBounds}) for the same
	 * destination with the same transports or more (the coarse stage is not done again). They stay lower bounds, so
	 * the route is as short; only which of several equally short routes may differ.
	 */
	synchronized Route find(PathQuery q, int limit, StartBounds known)
	{
		return find(q, limit, 0, known.bounds);
	}

	private Route find(PathQuery q, int limit, int extraUnit, CoarseBounds known)
	{
		long t0 = System.nanoTime();
		lastSearched = new int[0];
		if (!reach.reachable(q))
		{
			return Route.notFound(0, System.nanoTime() - t0);
		}
		long[] phases = new long[4];
		phases[0] = System.nanoTime();
		CoarseBounds b = known != null ? known : bounds(q, extraUnit);
		if (b == null)
		{
			return Route.notFound(0, System.nanoTime() - t0); // the coarse graph proves there is no way
		}
		phases[1] = System.nanoTime();
		Route r = fast.search(q, coarse.tileBound(b), beside, null, limit);
		phases[2] = System.nanoTime();
		// the components the tile search entered (each got its per-tile bound when first entered)
		int n = 0;
		for (int[] fc : b.fieldCache)
		{
			n += fc != null ? 1 : 0;
		}
		lastSearched = new int[n];
		n = 0;
		for (int c = 0; c < b.fieldCache.length; c++)
		{
			if (b.fieldCache[c] != null)
			{
				lastSearched[n++] = c;
			}
		}
		Route shaped = shaped(r, q);
		phases[3] = System.nanoTime();
		lastPhases = new long[]{phases[0] - t0, phases[1] - phases[0], phases[2] - phases[1], phases[3] - phases[2]};
		return new Route(shaped.isFound(), shaped.getTicks(), shaped.getSteps(), shaped.getExpanded(), System.nanoTime() - t0);
	}

	/** The route with its walks in the game's own shape (same length; only which of the equally short walks). */
	synchronized Route shaped(Route r, PathQuery q)
	{
		List<Route.Step> steps = new ArrayList<>();
		for (Route.Step s : r.getSteps())
		{
			steps.add(s.getKind() == Route.Step.Kind.WALK
				? new Route.Step(Route.Step.Kind.WALK, gameShaped(s.getTiles(), q.getDoors(), q.getDoorCost(), q.getClosed()), null, s.getTicks())
				: s);
		}
		return new Route(r.isFound(), r.getTicks(), steps, r.getExpanded(), r.getNanos());
	}

	/**
	 * Where the route should end. The destination itself if it can be reached; otherwise (it is inside a wall, a rock,
	 * a closed-off spot...) the nearest tile within {@code range} that can be reached and from which the destination is
	 * in sight ({@link LineOfSight}: only walls block). If no reachable tile in range has it in sight, the nearest
	 * reachable tile in range. Nearest = shortest straight-line distance to the destination; among equally near tiles,
	 * the one nearest the player. Returns {x, y, plane, inSight (1/0)}, or null if no tile in range can be reached.
	 * With {@code ownRegionOnly} the search stays inside the destination's 64x64 region (for the areas above the main
	 * map, where unrelated places sit side by side).
	 */
	public synchronized int[] routeEnd(PathQuery q, int range, LineOfSight.WallFlags walls, boolean ownRegionOnly)
	{
		int dx0 = q.getDestX();
		int dy0 = q.getDestY();
		int p = q.getDestPlane();
		if (reach.reachable(q))
		{
			return new int[]{dx0, dy0, p, 1};
		}
		Reachability.Reach places = reach.reached(q, Reachability.ALL);
		long[] seen = {Long.MAX_VALUE, Long.MAX_VALUE};
		long[] any = {Long.MAX_VALUE, Long.MAX_VALUE};
		int[] bestSeen = null;
		int[] bestAny = null;
		for (int x = dx0 - range; x <= dx0 + range; x++)
		{
			for (int y = dy0 - range; y <= dy0 + range; y++)
			{
				long d = (long) (x - dx0) * (x - dx0) + (long) (y - dy0) * (y - dy0);
				// a walkable tile reached, or a tile reached only by a transport (e.g. a boat ending at sea)
				int c = reach.place(x, y, p);
				boolean otherRegion = (x >> 6) != (dx0 >> 6) || (y >> 6) != (dy0 >> 6);
				if (d == 0 || !places.contains(c) || (ownRegionOnly && otherRegion))
				{
					continue;
				}
				long fromPlayer = Math.max(Math.abs(x - q.getStartX()), Math.abs(y - q.getStartY()))
					+ (q.getStartPlane() != p ? 100_000 : 0);
				if (better(d, fromPlayer, any))
				{
					any[0] = d;
					any[1] = fromPlayer;
					bestAny = new int[]{x, y, p, 0};
				}
				if (better(d, fromPlayer, seen) && LineOfSight.clear(walls, p, x, y, dx0, dy0))
				{
					seen[0] = d;
					seen[1] = fromPlayer;
					bestSeen = new int[]{x, y, p, 1};
				}
			}
		}
		return bestSeen != null ? bestSeen : bestAny;
	}

	/** Nearer to the destination, or as near and nearer to the player, than {@code best} {distance, fromPlayer}? */
	private static boolean better(long d, long fromPlayer, long[] best)
	{
		return d < best[0] || (d == best[0] && fromPlayer < best[1]);
	}

	/**
	 * For sailing: the same rules as {@link Reachability#reachable} the other way round. For each tile (packed,
	 * {@link Tiles}), can the query's destination be reached from it at all, walking and with the query's transports
	 * (costs ignored)? Null when a teleport's landing leads to the destination (then it can be reached from everywhere).
	 */
	public synchronized boolean[] leadsToDestination(PathQuery q, int[] tiles)
	{
		Reachability.Reach seen = reach.leading(q);
		for (int l : seen.links.landings)
		{
			if (seen.contains(l))
			{
				return null;
			}
		}
		boolean[] out = new boolean[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			out[i] = seen.contains(reach.place(Tiles.x(tiles[i]), Tiles.y(tiles[i]), Tiles.plane(tiles[i])));
		}
		return out;
	}

	/**
	 * Diagnostics: the coarse lower bound of the whole route (the player
	 * node's value), and the bound used at the player's own tile; -1 if no route.
	 */
	public synchronized int[] lowerBounds(PathQuery q)
	{
		CoarseBounds b = bounds(q, 0);
		if (b == null)
		{
			return new int[]{-1, -1};
		}
		int c = graph.componentAt(q.getStartX(), q.getStartY(), q.getStartPlane());
		return new int[]{b.lbPlayer, c < 0 ? 0 : b.component(c)};
	}

	/**
	 * Lower bounds of the ticks from standing on each of {@code tiles} to the query's destination with its transports (one
	 * coarse search back from the destination, as {@link #lowerBounds}; teleports count only for a route from the
	 * query's start, so none of these bounds uses one). The search stops past {@code cap} ticks: a bound of more than
	 * {@code cap} only says "more than cap". {@link FastSearch#NEVER} or more: nothing leads there from the tile at all.
	 * Whether the query's start leads anywhere does not matter.
	 */
	public synchronized int[] boundsFrom(PathQuery q, int[] tiles, int cap)
	{
		CoarseBounds b = coarse.bounds(q, 0, cap, true);
		int[] out = new int[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			int c = graph.componentAt(Tiles.x(tiles[i]), Tiles.y(tiles[i]), Tiles.plane(tiles[i]));
			out[i] = c < 0 ? 0 : b.component(c);
		}
		return out;
	}

	/**
	 * Lower bounds of a route that may begin at one of several tiles ({@link #startBounds}). Use it only with the
	 * search that made it: {@link #find(PathQuery, int, StartBounds)} fills in its per-tile bounds as it goes.
	 */
	static final class StartBounds
	{
		/** The whole route's bound from the query's start (as {@link #lowerBounds}). */
		final int whole;
		/** A route that begins with a teleport: the least teleport's ticks plus the bound from where it lands. */
		final int teleport;
		/** Per tile: a route that begins on that tile without a teleport (walking, transports with a start). */
		private final int[] at;
		/** The coarse search's bounds, for {@link #find(PathQuery, int, StartBounds)}. */
		private final CoarseBounds bounds;

		private StartBounds(int whole, int teleport, int[] at, CoarseBounds bounds)
		{
			this.whole = whole;
			this.teleport = teleport;
			this.at = at;
			this.bounds = bounds;
		}

		int at(int i)
		{
			return at[i];
		}

		/** True when a bound comes from the coarse search itself, not from where it stopped (it sees the way on). */
		boolean sharp(int bound)
		{
			return bound < bounds.unsettled;
		}
	}

	/**
	 * Lower bounds of the ticks to the query's destination with its transports (one coarse search, as
	 * {@link #lowerBounds}): from its start, for a route that begins with a teleport, and from standing on each of
	 * {@code tiles} (the component's bound, as the tile search uses). {@link FastSearch#NEVER} or more: no route
	 * that way (exact, as {@link #leadsToDestination}: nothing leads there at all). Null: no route at all from the
	 * start.
	 */
	synchronized StartBounds startBounds(PathQuery q, int[] tiles)
	{
		CoarseBounds b = bounds(q, 0);
		if (b == null)
		{
			return null;
		}
		Reachability.Reach leads = reach.leading(q);
		int[] at = new int[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			int x = Tiles.x(tiles[i]);
			int y = Tiles.y(tiles[i]);
			int p = Tiles.plane(tiles[i]);
			int c = graph.componentAt(x, y, p);
			at[i] = !leads.contains(reach.place(x, y, p)) ? INF : c < 0 ? 0 : b.component(c);
		}
		boolean landingLeads = false;
		for (int l : leads.links.landings)
		{
			landingLeads |= leads.contains(l);
		}
		return new StartBounds(b.lbPlayer, landingLeads ? b.lbTele : INF, at, b);
	}

	/**
	 * The coarse stage's bounds for the query ({@link CoarseSearch#bounds}); {@code extraUnit} > 0: the stopping
	 * margin leaves out whole units (see {@link #find(PathQuery, int, int)}). Null: nothing leads from the start.
	 */
	private CoarseBounds bounds(PathQuery q, int extraUnit)
	{
		return coarse.bounds(q, extraUnit, INF, false);
	}
}
