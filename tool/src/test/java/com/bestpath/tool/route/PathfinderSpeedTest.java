package com.bestpath.tool.route;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.graph.CoarseGraphDiagnostics;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.DoorMapBuilder;
import com.bestpath.core.path.LineOfSight;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.PathfinderDiagnostics;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Transport;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.cache.DataCache;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.extract.CollisionExtractor;
import com.bestpath.tool.extract.ExportBuilder;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.extract.ToolMap;
import com.bestpath.tool.transport.CandidateExtractor;
import com.bestpath.tool.transport.SuggestionEngine;
import com.bestpath.tool.transport.TransportCandidate;
import com.bestpath.tool.transport.TransportData;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Speed of the Fast search on the real map, set up the way the tool's Route
 * tab is (doors at 2 ticks, all transports, the route-end step first). Random
 * routes with a fixed seed, half within 200 tiles, half anywhere.
 * The limits are loose: they catch a real slow-down, not noise.
 * {@code gradlew -p tool speedTest}
 */
@Tag("speed")
class PathfinderSpeedTest
{
	private static final int ROUTES = 200;
	private static final int WARM_UP = 20;
	private static final long SEED = 7;

	private static Pathfinder pf;
	private static FlagGrid grid;
	private static final List<PathQuery> QUERIES = new ArrayList<>();

	@BeforeAll
	static void loadTheRealMap() throws Exception
	{
		File cacheDir = null;
		try
		{
			cacheDir = DataCache.folder(ToolSettings.findWorkspace()); // the cache the plugin data was made from
		}
		catch (IllegalStateException noCache)
		{
			// skipped below
		}
		assumeTrue(cacheDir != null, "no game cache in cache/");
		try (LoadedCache cache = LoadedCache.open(cacheDir))
		{
			ToolMap toolMap = ToolMap.walking(cache, ToolSettings.findWorkspace()); // the map as the Route tab has it
			EditStore edits = toolMap.edits;
			grid = toolMap.grid;
			ManualState manual = toolMap.manual;
			CollisionMap map = ExportBuilder.fullCollision(grid, cache.getRegionIds(), 0, 0);
			CoarseGraph graph = CoarseGraph.build(map);
			pf = new Pathfinder(map, graph, grid::get);

			// doors: possible with the doors open, not with every door shut (as in the tool)
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
			DoorMap doors = DoorMapBuilder.build(open, shut, doorTiles);

			List<TransportCandidate> cands = CandidateExtractor.extract(cache);
			TransportData td = new TransportData(cands, new SuggestionEngine(cands, graph::componentAt,
				(x, y, p, dx, dy) -> MovementRules.canStep(grid.plane(p), x, y, dx, dy)).avoiding(graph.pockets()).suggestAll(),
				com.bestpath.tool.wiki.WikiTeleports.withCache(com.bestpath.tool.transport.CacheTableTeleports.read(cache),
					new File("data/wiki/teleports.json")), manual.getTransports(), manual.getRejected());
			List<Transport> transports = RouteTransports.build(manual.getTransports().values(), Collections.emptyList(), td, true,
				c -> false, id -> false, (c, tx, ty) -> new int[]{c.getX(), c.getY(), c.getPlane()});

			System.out.printf(Locale.ROOT, "%,d transports%n", transports.size());
			System.out.printf(Locale.ROOT, "sub-region map: %.1f MB%n", CoarseGraphDiagnostics.labelBytes(graph) / 1e6);

			boolean[] pockets = graph.pockets();
			List<Integer> comps = new ArrayList<>();
			for (int c = 0; c < graph.componentCount(); c++)
			{
				if (!pockets[c] && graph.componentSize(c) >= 30)
				{
					comps.add(c);
				}
			}
			Random rnd = new Random(SEED);
			while (QUERIES.size() < ROUTES)
			{
				int[] a = PluginExportCli.randomTile(graph, comps.get(rnd.nextInt(comps.size())), rnd);
				int[] b = null;
				boolean near = QUERIES.size() % 2 == 0;
				for (int tries = 0; tries < 200 && b == null; tries++)
				{
					int[] c = PluginExportCli.randomTile(graph, comps.get(rnd.nextInt(comps.size())), rnd);
					if (!near || (c[2] == a[2] && Math.abs(c[0] - a[0]) < 200 && Math.abs(c[1] - a[1]) < 200))
					{
						b = c;
					}
				}
				if (b != null)
				{
					QUERIES.add(new PathQuery(a[0], a[1], a[2], b[0], b[1], b[2], transports,
						RouteTransports.DEFAULT_PLANE_CHANGE_PENALTY, doors, RouteController.DEFAULT_DOOR_COST));
				}
			}
		}
	}

	@Test
	void fastSearchIsFastEnough()
	{
		double[] t = timeAll();
		check("Fast search", t, 100, 1500, 5000);
	}

	/** Times every query the way the tool runs it; returns {median, p95, worst} in ms. */
	private static double[] timeAll()
	{
		LineOfSight.WallFlags walls = grid::get;
		for (int i = 0; i < WARM_UP; i++)
		{
			search(QUERIES.get(i), walls);
		}
		long[] nanos = new long[QUERIES.size()];
		// where the time goes: route end, then the search's stages (see Pathfinder.lastPhases)
		long[] stage = new long[5];
		long[][] perQuery = new long[QUERIES.size()][];
		// found routes only, split: within 200 tiles (even queries) and anywhere (odd)
		long[][] foundNanos = new long[2][QUERIES.size()];
		int[] found = new int[2];
		// the same routes give the same number (to check that a change leaves the routes alone)
		long fingerprint = 0;
		List<String> routeLines = new ArrayList<>();
		long slowest = -1;
		int slowestAt = 0;
		for (int i = 0; i < QUERIES.size(); i++)
		{
			long t0 = System.nanoTime();
			Route r = search(QUERIES.get(i), walls);
			nanos[i] = System.nanoTime() - t0;
			long[] ph = r != null && r.isFound() ? PathfinderDiagnostics.lastPhases(pf) : new long[4]; // stages of found routes only
			perQuery[i] = new long[]{lastRouteEndNanos, ph[0], ph[1], ph[2], ph[3]};
			for (int k = 0; k < 5; k++)
			{
				stage[k] += perQuery[i][k];
			}
			PathQuery qi = QUERIES.get(i);
			StringBuilder line = new StringBuilder().append(i).append('\t').append(qi.getStartX()).append(',').append(qi.getStartY())
				.append(',').append(qi.getStartPlane()).append(" -> ").append(qi.getDestX()).append(',').append(qi.getDestY())
				.append(',').append(qi.getDestPlane()).append('\t');
			if (r != null && r.isFound())
			{
				line.append(r.getTicks()).append(" ticks");
				for (Route.Step st : r.getSteps())
				{
					line.append(" | ").append(st.getKind()).append(st.getTransport() != null ? " " + st.getTransport().getName() : "")
						.append(' ').append(st.getTicks());
				}
			}
			else
			{
				line.append("no route");
			}
			routeLines.add(line.toString());
			if (r != null && r.isFound())
			{
				fingerprint = fingerprint * 31 + r.getTicks();
				for (Route.Step st : r.getSteps())
				{
					fingerprint = fingerprint * 31 + Arrays.hashCode(st.getTiles());
				}
				foundNanos[i % 2][found[i % 2]++] = nanos[i];
			}
			if (nanos[i] > slowest)
			{
				slowest = nanos[i];
				slowestAt = i;
			}
		}
		for (int k = 0; k < 2; k++)
		{
			long[] f = Arrays.copyOf(foundNanos[k], found[k]);
			System.out.printf(Locale.ROOT, "  %s: %d of %d have a route; those took median %.1f ms, p95 %.1f ms, worst %.1f ms%n",
				k == 0 ? "within 200 tiles" : "anywhere        ", found[k], QUERIES.size() / 2,
				f.length == 0 ? 0 : pct(f, 50), f.length == 0 ? 0 : pct(f, 95), f.length == 0 ? 0 : pct(f, 100));
		}
		System.out.printf(Locale.ROOT, "  total time per stage: route end %.0f ms, reachability %.0f, coarse %.0f, tiles %.0f, walk search %.0f%n",
			stage[0] / 1e6, stage[1] / 1e6, stage[2] / 1e6, stage[3] / 1e6, stage[4] / 1e6);
		Integer[] order = new Integer[QUERIES.size()];
		for (int i = 0; i < order.length; i++)
		{
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Long.compare(nanos[b], nanos[a]));
		for (int j = 0; j < 8; j++)
		{
			long[] p = perQuery[order[j]];
			PathQuery w = QUERIES.get(order[j]);
			System.out.printf(Locale.ROOT, "  slow %d,%d,%d -> %d,%d,%d: %.0f ms = route end %.0f, reachability %.0f, coarse %.0f, tiles %.0f, walk search %.0f%n",
				w.getStartX(), w.getStartY(), w.getStartPlane(), w.getDestX(), w.getDestY(), w.getDestPlane(), nanos[order[j]] / 1e6,
				p[0] / 1e6, p[1] / 1e6, p[2] / 1e6, p[3] / 1e6, p[4] / 1e6);
		}
		try
		{
			// every route in words, to compare two runs line by line when the fingerprint changes
			java.nio.file.Files.write(new File("tool/build/speed-routes.txt").toPath(), routeLines);
		}
		catch (java.io.IOException e)
		{
			System.out.println("  could not write tool/build/speed-routes.txt: " + e);
		}
		PathQuery s = QUERIES.get(slowestAt);
		System.out.printf(Locale.ROOT, "  slowest: %d,%d,%d -> %d,%d,%d; routes fingerprint %016x%n",
			s.getStartX(), s.getStartY(), s.getStartPlane(), s.getDestX(), s.getDestY(), s.getDestPlane(), fingerprint);
		return new double[]{pct(nanos, 50), pct(nanos, 95), pct(nanos, 100)};
	}

	/** As the Route tab: the route-end step, then the search to that tile. */
	private static Route search(PathQuery q, LineOfSight.WallFlags walls)
	{
		boolean aboveMainMap = q.getDestY() >= RouteController.DEFAULT_MAIN_MAP_TOP_Y;
		long r0 = System.nanoTime();
		int[] stop = pf.routeEnd(q, RouteController.DEFAULT_NEAR_RANGE, walls, aboveMainMap);
		lastRouteEndNanos = System.nanoTime() - r0;
		if (stop == null)
		{
			return null; // nothing near the destination can be reached
		}
		PathQuery used = q.withDestination(stop[0], stop[1], stop[2]);
		return pf.find(used);
	}

	private static long lastRouteEndNanos;

	private static void check(String what, double[] t, double medianMs, double p95Ms, double worstMs)
	{
		System.out.printf(Locale.ROOT, "%s, all searches: median %.1f ms, p95 %.1f ms, worst %.1f ms (limits %.0f / %.0f / %.0f)%n",
			what, t[0], t[1], t[2], medianMs, p95Ms, worstMs);
		assertTrue(t[0] < medianMs, what + " median " + t[0] + " ms");
		assertTrue(t[1] < p95Ms, what + " p95 " + t[1] + " ms");
		assertTrue(t[2] < worstMs, what + " worst " + t[2] + " ms");
	}

	private static double pct(long[] v, int p)
	{
		long[] s = v.clone();
		Arrays.sort(s);
		int i = Math.min(s.length - 1, (int) Math.ceil(p / 100.0 * s.length) - 1);
		return s[Math.max(0, i)] / 1e6;
	}
}
