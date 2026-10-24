package com.bestpath.tool.clue;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.DoorMapBuilder;
import com.bestpath.core.path.LineOfSight;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.FreeLandFile;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.extract.CollisionExtractor;
import com.bestpath.tool.extract.ExportBuilder;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.extract.ToolMap;
import com.bestpath.tool.req.TestPlayer;
import com.bestpath.tool.route.RequirementFilter;
import com.bestpath.tool.route.RouteController;
import com.bestpath.tool.route.RouteTransports;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Developer aid: can every clue location be reached? Loads the real map the
 * way the Data Tool does (hand edits, sea blocked for walking, doors
 * at 2 ticks, the confirmed transports; the Maxed player also sails:
 * {@link #maxedBoats}, the Route tab's search with Sailing ticked), reads
 * {@code data/clues/clue-steps.tsv} and {@code data/wiki/clue-kill-spawns.tsv},
 * and for every location (target, alternative target, STASH unit, key NPC,
 * one tile per kill-NPC area) classifies the tile ({@link ClueCheck}) and
 * runs the core Pathfinder from fixed starts for a few test players, ending
 * at the tile or, when it can't be reached, at the nearest reachable tile
 * that sees it (the Route tab's route-end rule, 10 tiles). Objects count as
 * reached next to any tile of their footprint; a dig/emote spot inside an
 * object counts as reached from the tile next to it. The hand-made
 * corrections ({@code data/clues/clue-overrides.tsv}, {@link ClueOverrides})
 * are applied first: retired steps dropped, fixed tiles, task-only kill areas
 * left out, kill-area planes. Separately, as suggestions for a person to
 * confirm (the data is never changed), it counts a tile with no floor reached
 * on the only other plane that has floor there (docs/CLUES.md section 5).
 *
 * <p>Output ({@link ClueCheckReport}): {@code cache/clue-check/report.tsv} (one line per location) and
 * {@code cache/clue-check/summary.md}. Nothing in the map data is changed.
 * {@code gradlew -p tool clueCheck --args="<cacheDir> [threads] [limit]"}
 */
public final class ClueCheckCli
{
	private static final int NEAR_RANGE = RouteController.DEFAULT_NEAR_RANGE;
	private static final int STAND_RADIUS = 10;

	/** A fixed start: name and tile (moved to the nearest standable tile if needed). */
	static final class Start
	{
		final String name;
		int x;
		int y;
		int plane;
		final boolean free;

		Start(String name, int x, int y, int plane, boolean free)
		{
			this.name = name;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.free = free;
		}
	}

	/** One test player: state, transports, free world or not, and how it sails (null: walking only). */
	static final class Player
	{
		final String name;
		final boolean members;
		final List<Transport> transports;
		final Sailing.Options sailing;

		Player(String name, boolean members, List<Transport> transports, Sailing.Options sailing)
		{
			this.name = name;
			this.members = members;
			this.transports = transports;
			this.sailing = sailing;
		}
	}

	/**
	 * The Maxed test player's boats (the maxed player sails; fresh players walk): one of each kind
	 * (raft, skiff, sloop), every part at the top tier (rosewood hull and sails, dragon keel and helm), an Inoculation
	 * station and an Eternal brazier (every restricted water), a greater teleport focus, all moored at Port Sarim
	 * (dock 0, as the uiCheck and export sailing cases). A maxed player can own up to five boats; three kinds let
	 * the route pick the fastest boat that fits.
	 */
	static List<Boat> maxedBoats()
	{
		List<Boat> out = new ArrayList<>();
		BoatType[] kinds = {BoatType.SLOOP, BoatType.SKIFF, BoatType.RAFT};
		for (int i = 0; i < kinds.length; i++)
		{
			out.add(new Boat(i + 1, kinds[i], 6, 6, 6, 6, true, true, Boat.Focus.GREATER, 0));
		}
		return out;
	}

	/** One place to check for one step. */
	static final class Location
	{
		final ClueStep step;
		final String role;
		final String kind;
		final String label;
		final int x;
		final int y;
		final int plane;
		/** Object ids that may stand here (loc and key targets, STASH units); empty for NPCs and spots. */
		final List<Integer> objectIds;
		/** The placed object's whole footprint {x0, y0, x1, y1} on this plane, or null (not an object, or none found). */
		int[] footprint;
		/** Where the check also looks when this tile has no floor (never written to the data), or null. */
		ClueCheck.Suggestion suggestion;

		Location(ClueStep step, String role, String kind, String label, int x, int y, int plane)
		{
			this(step, role, kind, label, x, y, plane, Collections.emptyList());
		}

		Location(ClueStep step, String role, String kind, String label, int x, int y, int plane, List<Integer> objectIds)
		{
			this.step = step;
			this.role = role;
			this.kind = kind;
			this.label = label;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.objectIds = objectIds;
		}

		/**
		 * Ring distance from where a route stopped to this location: to the
		 * object's whole footprint when it has one, else to the tile; -1 without
		 * a route, 99 on another plane.
		 */
		int distance(Outcome o)
		{
			if (o == null || !o.found)
			{
				return -1;
			}
			if (o.stopPlane != plane)
			{
				return 99;
			}
			if (footprint != null)
			{
				return ClueCheck.distanceToFootprint(o.stopX, o.stopY, footprint[0], footprint[1], footprint[2], footprint[3]);
			}
			return Math.max(Math.abs(o.stopX - x), Math.abs(o.stopY - y));
		}
	}

	/** One search's outcome. */
	static final class Outcome
	{
		boolean found;
		int ticks;
		int steps;
		/** Ring distance from the location's tile to where the route stops; -1 without a route. */
		int stop = -1;
		/** Where the route stops. */
		int stopX;
		int stopY;
		int stopPlane;
		/** The route has a sailing leg. */
		boolean sailed;
	}

	/** Every search for one tile. */
	static final class TileResult
	{
		ClueCheck.Spot spot;
		int[] nearest;
		/** Another plane where this x,y is standable when the tile itself is not (-1: none or not needed). */
		int otherPlane = -1;
		/** [player][start]; null where not searched. */
		Outcome[][] outcomes;
		boolean free;
	}

	private ClueCheckCli()
	{
	}

	public static void main(String[] args) throws Exception
	{
		long t0 = System.nanoTime();
		int threads = args.length > 1 ? Integer.parseInt(args[1]) : 4;
		int limit = args.length > 2 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;
		File outDir = new File("cache/clue-check");
		outDir.mkdirs();
		List<ClueStep> raw = ClueFile.read(new File("data/clues/clue-steps.tsv"));
		ClueOverrides overrides = ClueOverrides.read(ClueOverrides.file(new File(".")));
		List<String[]> spawnLines = KillAreas.readLines(new File("data/wiki/clue-kill-spawns.tsv"));
		for (String p : overrides.problems(raw, KillAreas.locations(spawnLines)))
		{
			System.out.println("clue-overrides: " + p);
		}
		List<ClueStep> steps = overrides.apply(raw);
		Map<String, List<KillAreas.Group>> spawns = KillAreas.groups(spawnLines, overrides);
		System.out.printf(Locale.ROOT, "%s: %,d of %,d steps left after retired ones, %,d with needs%n", overrides, steps.size(), raw.size(),
			steps.stream().filter(s -> !overrides.needs(s).isEmpty()).count());

		try (LoadedCache cache = LoadedCache.open(new File(args[0])))
		{
			CheckMap m = new CheckMap(cache);
			List<Player> players = players(m);
			List<Start> starts = starts(m.view);

			// the locations, the footprints of their objects, and a suggested plane where the tile has no floor
			List<Location> locs = locations(steps, spawns, m.view);
			addFootprints(cache, locs);
			Map<Long, TileResult> tiles = tilesToSearch(locs, m.view, limit);
			System.out.printf(Locale.ROOT, "%,d steps, %,d locations, %,d distinct tiles; map ready in %.1f s%n", steps.size(), locs.size(), tiles.size(),
				(System.nanoTime() - t0) / 1e9);

			long s0 = System.nanoTime();
			int searches = searchAll(m, players, starts, tiles, threads, s0);
			double secs = (System.nanoTime() - s0) / 1e9;
			System.out.printf(Locale.ROOT, "%,d searches in %.0f s (%d threads)%n", searches, secs, threads);

			ClueCheckReport.writeReport(new File(outDir, "report.tsv"), locs, tiles, players, starts);
			String summary = ClueCheckReport.summary(steps, locs, tiles, players, starts, secs, searches, threads, cache.getInfo().getOpenrs2Id());
			Files.writeString(new File(outDir, "summary.md").toPath(), summary, StandardCharsets.UTF_8);
			System.out.println(summary);
			System.out.printf(Locale.ROOT, "whole run %.0f s%n", (System.nanoTime() - t0) / 1e9);
		}
	}

	/** The map as the Data Tool builds it (as the plugin export), with what the searches need. */
	static final class CheckMap
	{
		final FlagGrid grid;
		final ManualState manual;
		final CollisionMap map;
		final CoarseGraph graph;
		/** Sailing (Maxed only): one Sailing per search thread, sharing the sea graph of {@link #sailing}. */
		final Sailing sailing;
		final ThreadLocal<Sailing> sails;
		final ClueCheck.MapView view;
		/** Doors: open, each crossing 2 ticks (as the Route tab). */
		final DoorMap doors;
		final FreeLand land;
		final CollisionMap freeMap;
		final CoarseGraph freeGraph;

		CheckMap(LoadedCache cache) throws Exception
		{
			ToolMap toolMap = ToolMap.withSea(cache, ToolSettings.findWorkspace());
			EditStore edits = toolMap.edits;
			grid = toolMap.grid;
			manual = toolMap.manual;
			SeaGrid seaGrid = toolMap.seaGrid;
			List<Dock> docks = toolMap.docks;
			CollisionMap fullMap = ExportBuilder.fullCollision(grid, cache.getRegionIds(), 0, 0);
			CoarseGraph fullGraph = CoarseGraph.build(fullMap);
			map = fullMap;
			graph = fullGraph;
			Sailing shared = new Sailing(seaGrid, docks, null, fullGraph, null);
			sailing = shared;
			sails = ThreadLocal.withInitial(() -> new Sailing(seaGrid, docks, null, fullGraph, shared));
			view = mapView(cache, manual, toolMap.sea, grid, fullGraph);
			doors = doors(grid);
			FreeLand freeLand = FreeLandFile.effective(edits.active());
			land = freeLand;
			freeMap = fullMap.restrictTo((x, y, p) -> freeLand.isFree(x, y));
			freeGraph = CoarseGraph.build(freeMap);
		}
	}

	/** The map as the clue check sees it: regions, sea, collision flags and the coarse graph's pockets and hidden areas. */
	private static ClueCheck.MapView mapView(LoadedCache cache, ManualState manual, SeaMap sea, FlagGrid grid, CoarseGraph graph)
	{
		boolean[] pockets = graph.pockets();
		for (int[] a : manual.getKeptAnchors())
		{
			int c = graph.componentAt(a[0], a[1], a[2]);
			if (c >= 0)
			{
				pockets[c] = false;
			}
		}
		boolean[] hidden = new boolean[graph.componentCount()];
		for (int[] a : manual.getHiddenAnchors())
		{
			int c = graph.componentAt(a[0], a[1], a[2]);
			if (c >= 0)
			{
				hidden[c] = true;
			}
		}
		Set<Integer> regions = cache.getRegionIds();
		Set<Integer> ignored = manual.getIgnoredRegions();
		return new ClueCheck.MapView()
		{
			@Override
			public boolean hasRegion(int regionId)
			{
				return regions.contains(regionId);
			}

			@Override
			public boolean ignored(int regionId)
			{
				return ignored.contains(regionId);
			}

			@Override
			public boolean sea(int x, int y, int plane)
			{
				return sea.isSea(x, y, plane);
			}

			@Override
			public int flags(int x, int y, int plane)
			{
				return grid.get(x, y, plane);
			}

			@Override
			public int component(int x, int y, int plane)
			{
				return graph.componentAt(x, y, plane);
			}

			@Override
			public boolean pocket(int component)
			{
				return pockets[component];
			}

			@Override
			public boolean hidden(int component)
			{
				return hidden[component];
			}
		};
	}

	/** The doors as the Route tab has them: open, each crossing costs 2 ticks. */
	private static DoorMap doors(FlagGrid grid)
	{
		FlagGrid shutGrid = grid.copy();
		List<int[]> doorTiles = new ArrayList<>();
		for (int[] door : grid.getDoors())
		{
			CollisionExtractor.addDoorWalls(shutGrid, door);
			doorTiles.add(new int[]{door[0], door[1], door[2]});
		}
		MovementRules.Flags[] open = new MovementRules.Flags[4];
		MovementRules.Flags[] shut = new MovementRules.Flags[4];
		for (int p = 0; p < 4; p++)
		{
			open[p] = grid.plane(p);
			shut[p] = shutGrid.plane(p);
		}
		return DoorMapBuilder.build(open, shut, doorTiles);
	}

	/** The test players: the confirmed transports, filtered by each player's requirements. */
	private static List<Player> players(CheckMap m)
	{
		List<Player> players = new ArrayList<>();
		TestPlayer f2p = TestPlayer.fresh();
		f2p.name = "Fresh free-to-play";
		f2p.members = false;
		TestPlayer maxed = TestPlayer.maxed();
		for (TestPlayer tp : new TestPlayer[]{maxed, TestPlayer.fresh(), f2p})
		{
			PlayerState state = tp.toState();
			// what the player carries (these test players keep nothing in the bank)
			RequirementFilter needs = new RequirementFilter(state);
			List<Transport> ts = new ArrayList<>(needs.carried(RouteTransports.build(m.manual.getTransports().values(), Collections.emptyList(),
				null, false, c -> false, id -> false, (c, tx, ty) -> null, needs)));
			if (!tp.members)
			{
				ts.removeIf(t -> !m.land.isFree(t.getEndX(), t.getEndY()) || t.hasStart() && !m.land.isFree(t.getStartX(), t.getStartY()));
			}
			// the Maxed player sails (Sailing 99, its quests for the docks, shipwrights on); fresh players walk
			Sailing.Options sailOptions = tp == maxed ? new Sailing.Options(99, state, maxedBoats(), 0, true, Sailing.DEFAULT_TURN_COST) : null;
			players.add(new Player(tp.name, tp.members, ts, sailOptions));
			System.out.printf(Locale.ROOT, "%-20s %,d transports%s%n", tp.name, ts.size(), sailOptions == null ? ""
				: String.format(Locale.ROOT, ", sails (%d boats, %d docks used for sailing)", maxedBoats().size(), m.sailing.getDocks().size()));
		}
		return players;
	}

	/** The fixed starts, each moved to the nearest standable tile. */
	private static List<Start> starts(ClueCheck.MapView view)
	{
		List<Start> starts = new ArrayList<>();
		starts.add(new Start("Lumbridge", 3222, 3218, 0, true));
		starts.add(new Start("Grand Exchange", 3164, 3487, 0, true));
		starts.add(new Start("Kourend Castle", 1639, 3673, 0, false));
		for (Start s : starts)
		{
			int[] n = ClueCheck.nearestStandable(view, s.x, s.y, s.plane, STAND_RADIUS);
			if (n == null)
			{
				throw new IllegalStateException("start " + s.name + " has no standable tile nearby");
			}
			s.x = n[0];
			s.y = n[1];
			s.plane = n[2];
		}
		return starts;
	}

	/**
	 * The distinct tiles to search (at most {@code limit}, more only for tiles already in): each location's tile and
	 * its suggested tile, which this sets on the location.
	 */
	private static Map<Long, TileResult> tilesToSearch(List<Location> locs, ClueCheck.MapView view, int limit)
	{
		Map<Long, TileResult> tiles = new LinkedHashMap<>();
		for (Location l : locs)
		{
			if (tiles.size() >= limit && !tiles.containsKey(key(l.x, l.y, l.plane)))
			{
				continue;
			}
			tiles.computeIfAbsent(key(l.x, l.y, l.plane), k -> new TileResult());
			l.suggestion = ClueCheck.suggest(view, l.x, l.y, l.plane);
			if (l.suggestion != null)
			{
				tiles.computeIfAbsent(key(l.suggestion.x, l.suggestion.y, l.suggestion.plane), k -> new TileResult());
			}
		}
		return tiles;
	}

	/** Searches every tile on {@code threads} threads; returns how many searches ran. */
	private static int searchAll(CheckMap m, List<Player> players, List<Start> starts, Map<Long, TileResult> tiles, int threads, long s0)
		throws Exception
	{
		LineOfSight.WallFlags walls = m.grid::get;
		ThreadLocal<Pathfinder[]> finders = ThreadLocal.withInitial(() -> new Pathfinder[]{new Pathfinder(m.map, m.graph, walls), null});
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		AtomicInteger done = new AtomicInteger();
		AtomicInteger searches = new AtomicInteger();
		List<Future<?>> jobs = new ArrayList<>();
		for (Map.Entry<Long, TileResult> e : tiles.entrySet())
		{
			int x = (int) (e.getKey() >> 32);
			int y = (int) ((e.getKey() >> 2) & 0x3FFFFFFF);
			int plane = (int) (e.getKey() & 3);
			TileResult tr = e.getValue();
			jobs.add(pool.submit(() ->
			{
				searchTile(m, finders.get(), walls, players, starts, x, y, plane, tr, searches);
				int n = done.incrementAndGet();
				if (n % 100 == 0)
				{
					System.out.printf(Locale.ROOT, "  %,d of %,d tiles (%.0f s)%n", n, tiles.size(), (System.nanoTime() - s0) / 1e9);
				}
			}));
		}
		for (Future<?> f : jobs)
		{
			f.get();
		}
		pool.shutdown();
		return searches.get();
	}

	/**
	 * Classifies one tile and searches to it for every player from every start ({@code pf}: this thread's members
	 * and free-to-play pathfinders, the second made when first needed).
	 */
	private static void searchTile(CheckMap m, Pathfinder[] pf, LineOfSight.WallFlags walls, List<Player> players, List<Start> starts, int x, int y,
		int plane, TileResult tr, AtomicInteger searches)
	{
		tr.spot = ClueCheck.classify(m.view, x, y, plane);
		tr.nearest = ClueCheck.nearestStandable(m.view, x, y, plane, STAND_RADIUS);
		tr.free = m.land.isFree(x, y);
		tr.otherPlane = tr.spot == ClueCheck.Spot.WALKABLE ? -1 : ClueCheck.otherStandablePlane(m.view, x, y, plane);
		tr.outcomes = new Outcome[players.size()][starts.size()];
		for (int pi = 0; pi < players.size(); pi++)
		{
			Player pl = players.get(pi);
			if (!pl.members && !tr.free)
			{
				continue; // a free-to-play player can't go to members land
			}
			if (!pl.members && pf[1] == null)
			{
				pf[1] = new Pathfinder(m.freeMap, m.freeGraph, walls);
			}
			Pathfinder finder = pl.members ? pf[0] : pf[1];
			for (int si = 0; si < starts.size(); si++)
			{
				Start st = starts.get(si);
				if (!pl.members && !st.free)
				{
					continue;
				}
				tr.outcomes[pi][si] = search(finder, walls, pl, st, x, y, plane, m.doors, pl.sailing == null ? null : m.sails.get(),
					tr.spot == ClueCheck.Spot.WALKABLE, tr.nearest);
				searches.incrementAndGet();
			}
		}
	}

	static long key(int x, int y, int plane)
	{
		return ((long) x << 32) | ((long) y << 2) | plane;
	}

	/**
	 * The Route tab's way: the route-end step (nearest reachable tile that sees it, 10 tiles), then the search. With
	 * {@code sail} (the Maxed player) the searches are the Route tab's search with Sailing ticked
	 * ({@link RouteController#search}): first to the route-end tile found without boats (so a tile next to a crate
	 * stays the end, sailing only where it is quicker); when that is not close enough, or nothing is reached without a
	 * boat (an island), also to the location itself and to its nearest standable tile, keeping the closest stop.
	 */
	private static Outcome search(Pathfinder pf, LineOfSight.WallFlags walls, Player pl, Start st, int x, int y, int plane, DoorMap doors,
		Sailing sail, boolean standable, int[] nearest)
	{
		PathQuery q = new PathQuery(st.x, st.y, st.plane, x, y, plane, pl.transports, RouteTransports.DEFAULT_PLANE_CHANGE_PENALTY,
			doors, RouteController.DEFAULT_DOOR_COST);
		Outcome o = new Outcome();
		if (sail != null)
		{
			boolean above = y >= RouteController.DEFAULT_MAIN_MAP_TOP_Y;
			int[] end = pf.routeEnd(q, NEAR_RANGE, walls, above);
			Outcome best = end == null ? null : sailed(pf, sail, pl, q.withDestination(end[0], end[1], end[2]), walls, above, x, y, plane);
			if (best == null || !best.found || best.stop > (standable ? 0 : 1))
			{
				best = closer(best, sailed(pf, sail, pl, q, walls, above, x, y, plane));
				if (nearest != null && (nearest[0] != x || nearest[1] != y || nearest[2] != plane))
				{
					best = closer(best, sailed(pf, sail, pl, q.withDestination(nearest[0], nearest[1], nearest[2]), walls, above, x, y, plane));
				}
			}
			return best == null ? o : best;
		}
		int[] stop = pf.routeEnd(q, NEAR_RANGE, walls, y >= RouteController.DEFAULT_MAIN_MAP_TOP_Y);
		if (stop == null)
		{
			return o;
		}
		Route r = pf.find(q.withDestination(stop[0], stop[1], stop[2]));
		if (r.isFound())
		{
			o.found = true;
			o.ticks = r.getTicks();
			o.steps = r.getSteps().size();
			o.stopX = stop[0];
			o.stopY = stop[1];
			o.stopPlane = stop[2];
			o.stop = stop[2] != plane ? 99 : Math.max(Math.abs(stop[0] - x), Math.abs(stop[1] - y));
		}
		return o;
	}

	/** The Route tab's search with Sailing ticked to {@code q}'s destination; the stop is measured to the location x, y, plane. */
	private static Outcome sailed(Pathfinder pf, Sailing sail, Player pl, PathQuery q, LineOfSight.WallFlags walls, boolean above, int x, int y,
		int plane)
	{
		Outcome o = new Outcome();
		RouteController.Searched s = RouteController.search(pf, sail, pl.sailing, q, NEAR_RANGE, walls, above);
		if (s.stop != null && s.route.isFound())
		{
			o.found = true;
			o.ticks = s.route.getTicks();
			o.steps = s.route.getSteps().size();
			for (Route.Step step : s.route.getSteps())
			{
				o.sailed |= step.getKind() == Route.Step.Kind.SAIL;
			}
			o.stopX = s.stop[0];
			o.stopY = s.stop[1];
			o.stopPlane = s.stop[2];
			o.stop = s.stop[2] != plane ? 99 : Math.max(Math.abs(s.stop[0] - x), Math.abs(s.stop[1] - y));
		}
		return o;
	}

	/** The outcome that stops closer to the location (then the quicker one); a found route beats none. */
	private static Outcome closer(Outcome a, Outcome b)
	{
		if (a == null || !a.found)
		{
			return b.found || a == null ? b : a;
		}
		if (!b.found)
		{
			return a;
		}
		return b.stop < a.stop || b.stop == a.stop && b.ticks < a.ticks ? b : a;
	}

	/**
	 * Every place to check: the target, alternative targets, STASH units, key
	 * NPCs, and for a kill step one tile per group of Wiki spawns
	 * ({@link KillAreas}: the first standable spawn tile of the group, else the
	 * first one with a suggested plane, else the first).
	 */
	static List<Location> locations(List<ClueStep> steps, Map<String, List<KillAreas.Group>> spawns, ClueCheck.MapView view)
	{
		List<Location> out = new ArrayList<>();
		for (ClueStep s : steps)
		{
			List<Integer> objects = s.target.equals("loc") || s.target.equals("key") ? objectIds(s) : Collections.emptyList();
			if (s.hasTile())
			{
				out.add(new Location(s, "target", s.target, s.targetName.isEmpty() ? s.description : s.targetName, s.x, s.y, s.plane, objects));
			}
			for (ClueStep.Place p : s.places)
			{
				String kind = p.role.equals("alt-target") ? s.target : p.role;
				List<Integer> ids = p.role.equals("stash") ? p.ids : p.role.equals("alt-target") ? objects : Collections.emptyList();
				out.add(new Location(s, p.role, kind, p.name, p.x, p.y, p.plane, ids));
			}
			if (s.target.equals("kill"))
			{
				for (KillAreas.Group g : spawns.getOrDefault(s.key, Collections.emptyList()))
				{
					int[] best = null;
					for (int[] xy : g.tiles)
					{
						int[] t = {xy[0], xy[1], g.plane};
						if (best == null || killTileRank(view, t) < killTileRank(view, best))
						{
							best = t;
						}
					}
					out.add(new Location(s, "kill-area", "kill", g.label(), best[0], best[1], best[2]));
				}
			}
		}
		return out;
	}

	/** 0: standable; 1: no floor but one other plane is standable (a suggestion); 2: neither. Lower is better. */
	static int killTileRank(ClueCheck.MapView view, int[] t)
	{
		if (ClueCheck.standable(view, t[0], t[1], t[2]))
		{
			return 0;
		}
		return ClueCheck.suggest(view, t[0], t[1], t[2]) != null ? 1 : 2;
	}

	/** The target's object ids: the cache's loc ids and its fallback locs (from the "fallback loc ..." note). */
	static List<Integer> objectIds(ClueStep s)
	{
		List<Integer> ids = new ArrayList<>(s.targetIds);
		for (String n : s.notes)
		{
			if (n.startsWith("fallback loc "))
			{
				String first = n.substring("fallback loc ".length()).split(" ", 2)[0];
				for (String id : first.split(","))
				{
					try
					{
						ids.add(Integer.parseInt(id.trim()));
					}
					catch (NumberFormatException ex)
					{
						// not a number list: nothing to add
					}
				}
			}
		}
		return ids;
	}

	/**
	 * Gives each object location the footprint of the placed object it names:
	 * a placed object with one of its ids (or whose other states include one),
	 * on the location's plane (one lower on bridge tiles, as for collision),
	 * whose footprint holds the location's tile.
	 */
	private static void addFootprints(LoadedCache cache, List<Location> locs) throws java.io.IOException
	{
		Map<Integer, List<Location>> byId = new java.util.HashMap<>();
		for (Location l : locs)
		{
			for (int id : l.objectIds)
			{
				byId.computeIfAbsent(id, k -> new ArrayList<>()).add(l);
			}
		}
		if (byId.isEmpty())
		{
			return;
		}
		cache.forEachRegion(r ->
		{
			for (net.runelite.cache.region.Location o : r.getLocations())
			{
				net.runelite.cache.definitions.ObjectDefinition d = cache.object(o.getId());
				Set<Location> wanting = new java.util.LinkedHashSet<>(byId.getOrDefault(o.getId(), Collections.emptyList()));
				if (d != null && d.getConfigChangeDest() != null)
				{
					for (int child : d.getConfigChangeDest())
					{
						wanting.addAll(byId.getOrDefault(child, Collections.emptyList()));
					}
				}
				if (wanting.isEmpty() || d == null)
				{
					continue;
				}
				int ox = o.getPosition().getX();
				int oy = o.getPosition().getY();
				int lx = ox - r.getBaseX();
				int ly = oy - r.getBaseY();
				int plane = o.getPosition().getZ();
				if (lx >= 0 && lx < 64 && ly >= 0 && ly < 64 && (r.getTileSetting(1, lx, ly) & 2) != 0)
				{
					plane--;
				}
				int w = o.getType() >= 9 && o.getType() != 22 ? d.getSizeX() : 1;
				int h = o.getType() >= 9 && o.getType() != 22 ? d.getSizeY() : 1;
				if ((o.getOrientation() & 1) == 1)
				{
					int t = w;
					w = h;
					h = t;
				}
				for (Location l : wanting)
				{
					if (l.plane == plane && l.x >= ox && l.x < ox + w && l.y >= oy && l.y < oy + h)
					{
						l.footprint = new int[]{ox, oy, ox + w - 1, oy + h - 1};
					}
				}
			}
		});
	}
}
