package com.bestpath.dev;

import com.bestpath.core.data.NoRouteReasons;
import com.bestpath.core.data.NoRouteReasonsDiagnostics;
import com.bestpath.core.data.PlayerTransports;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.PluginRouterDiagnostics;
import com.bestpath.core.data.TransportLineWriter;
import com.bestpath.core.path.PathfinderDiagnostics;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.PlayerStateDiagnostics;
import com.bestpath.data.MapDataLoader;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.RouteJob;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.TransportRules;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * DEV ONLY (src/test, never in the plugin jar): "no route" explained on the exported map data
 * (src/main/resources/com/bestpath/data). A fresh account standing in Lumbridge asks for routes to random places
 * through the plugin's engine (default Settings); each "no route" is explained by {@link PluginRouter#explainNoRoute}
 * with the filter the engine's last search used. Prints how many were "no route", each kind's count, the explanation's
 * time (median, p95, worst) and short-lived memory (bytes this thread allocated), and a few examples. Then the same on
 * a free world, and random tiles anywhere (most can't be stood on). First checks that the explanation's transports
 * usable now are exactly the route search's.
 *
 * <p>{@code gradlew noRouteCheck [-Proutes=100]}
 */
public final class NoRouteCheck
{
	private static final int[] LUMBRIDGE = RealData.START;

	private NoRouteCheck()
	{
	}

	public static void main(String[] args) throws Exception
	{
		int n = args.length > 0 ? Integer.parseInt(args[0]) : 100;
		long base = DataMemoryCheck.usedAfterGc();
		PluginData data = PluginData.read(MapDataLoader.JAR);
		if (data == null)
		{
			System.out.println("No map data in src/main/resources/com/bestpath/data: run gradlew -p tool exportPlugin first.");
			return;
		}
		PluginRouter router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
		CoreRouteEngine engine = new CoreRouteEngine(router);
		TransportSettings settings = TransportSettings.defaults();
		PluginRouter.TransportFilter filter = new TransportRules(data.getTransports(), data.getTables()).filter(settings, false);

		sameAsTheRouteSearch(data);

		List<int[]> qs = DataMemoryCheck.randomRoutes(PluginRouterDiagnostics.pathfinder(router).getGraph(), n, new Random(7));
		List<int[]> places = new ArrayList<>();
		for (int[] q : qs)
		{
			places.add(new int[]{q[3], q[4], q[5]});
		}
		List<int[]> unknown = pass("fresh account (members world), random walkable places", engine, router, PlayerStateBuilder.fresh(), places,
			filter, settings);
		System.out.printf(Locale.ROOT, "heap kept after the members pass (data + search + caches): %.1f MB; caches %.1f MB, dropped %d times%n",
			(DataMemoryCheck.usedAfterGc() - base) / 1e6, PluginRouterDiagnostics.cacheBytes(router) / 1e6, PluginRouterDiagnostics.cacheClears(router));

		// anywhere on the map's cells: most of these tiles can't be walked on
		Random rnd = new Random(3);
		int[] cells = data.getMap().cellKeys();
		List<int[]> anywhere = new ArrayList<>();
		while (anywhere.size() < n / 2)
		{
			int k = cells[rnd.nextInt(cells.length)];
			int x = (k >> 10) << 6 | rnd.nextInt(64);
			int y = ((k >> 2) & 0xFF) << 6 | rnd.nextInt(64);
			anywhere.add(new int[]{x, y, k & 3});
		}
		pass("fresh account (members world), random tiles anywhere", engine, router, PlayerStateBuilder.fresh(), anywhere, filter, settings);
		severalPlaces(router, PlayerStateBuilder.fresh(), places, filter, data);

		withQuestOnly(data, unknown, filter);

		PlayerState free = new PlayerState.Builder("Fresh free-to-play").members(false).build();
		pass("fresh account on a free world, the same random walkable places", engine, router, free, places, filter, settings);
		System.out.printf(Locale.ROOT, "heap kept on a free world: %.1f MB; caches %.1f MB, dropped %d times in all%n",
			(DataMemoryCheck.usedAfterGc() - base) / 1e6, PluginRouterDiagnostics.cacheBytes(router) / 1e6, PluginRouterDiagnostics.cacheClears(router));
	}

	/**
	 * Several places (a clue step's): for each of the first 20 random places with no route, it and 3 more walkable
	 * tiles within 30 tiles of it (when the routes to all 4 fail), explained together.
	 */
	private static void severalPlaces(PluginRouter router, PlayerState p, List<int[]> places, PluginRouter.TransportFilter filter,
		PluginData data)
	{
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		long tid = Thread.currentThread().getId();
		com.bestpath.core.graph.CoarseGraph g = PluginRouterDiagnostics.pathfinder(router).getGraph();
		Random rnd = new Random(9);
		List<Long> nanos = new ArrayList<>();
		List<Long> alloc = new ArrayList<>();
		Map<NoRouteReasons.Kind, Integer> kinds = new EnumMap<>(NoRouteReasons.Kind.class);
		String slowest = "";
		long slowestNanos = 0;
		for (int[] d : places)
		{
			if (nanos.size() >= 20)
			{
				break;
			}
			int[] targets = new int[4];
			targets[0] = com.bestpath.core.path.Tiles.pack(d[0], d[1], d[2]);
			for (int i = 1; i < 4; i++)
			{
				int x;
				int y;
				do
				{
					x = d[0] + rnd.nextInt(61) - 30;
					y = d[1] + rnd.nextInt(61) - 30;
				}
				while (g.componentAt(x, y, d[2]) < 0);
				targets[i] = com.bestpath.core.path.Tiles.pack(x, y, d[2]);
			}
			PluginRouter.NearestAnswer a = PluginRouterDiagnostics.routeNearest(router, p, LUMBRIDGE[0], LUMBRIDGE[1], LUMBRIDGE[2], targets, id -> false, filter,
				CoreRouteEngine.MAX_SEARCHES);
			if (a.getAnswer().getRoute().isFound())
			{
				continue;
			}
			long a0 = threads.getThreadAllocatedBytes(tid);
			long s0 = System.nanoTime();
			NoRouteReasons why = PluginRouterDiagnostics.explainNoRoute(router, p, LUMBRIDGE[0], LUMBRIDGE[1], LUMBRIDGE[2], targets, id -> false, filter);
			long t = System.nanoTime() - s0;
			nanos.add(t);
			alloc.add(threads.getThreadAllocatedBytes(tid) - a0);
			kinds.merge(why.getKind(), 1, Integer::sum);
			if (t > slowestNanos)
			{
				slowestNanos = t;
				slowest = String.format(Locale.ROOT, "%d,%d,%d and 3 near: %s, place %d, %d looked at (%.0f ms) %s", d[0], d[1], d[2],
					why.getKind(), NoRouteReasonsDiagnostics.chosen(why), NoRouteReasonsDiagnostics.placesLookedAt(why), t / 1e6, String.join(" | ", why.getReasons()));
			}
		}
		System.out.printf(Locale.ROOT, "%n== several places: %d sets of 4 places with no route; kinds %s%n", nanos.size(), kinds);
		if (!nanos.isEmpty())
		{
			System.out.printf(Locale.ROOT, "explanation time: median %.0f ms, p95 %.0f ms, worst %.0f ms; short-lived memory median %.0f MB,"
				+ " p95 %.0f MB, worst %.0f MB%n", pct(nanos, 50) / 1e6, pct(nanos, 95) / 1e6, pct(nanos, 100) / 1e6, pct(alloc, 50) / 1e6,
				pct(alloc, 95) / 1e6, pct(alloc, 100) / 1e6);
			System.out.println("slowest: " + slowest);
		}
	}

	/** The candidates usable now are exactly the route search's transports (same order, ids, ends, costs). */
	private static void sameAsTheRouteSearch(PluginData data)
	{
		PlayerTransports pt = new PlayerTransports(data.getTransports(), data.getTables());
		PlayerState free = new PlayerState.Builder("Fresh free-to-play").members(false).build();
		for (PlayerState p : new PlayerState[]{PlayerStateBuilder.fresh(), PlayerStateBuilder.maxed(), free,
			RealData.players().get(RealData.MID_LEVEL).getState(), RealData.players().get(RealData.BANK_PLAYER).getState(),
			RealData.players().get(RealData.COOLDOWNS_USED).getState()})
		{
			com.bestpath.core.collision.FreeLand land = p.isMembers() ? null : data.getFreeLand();
			List<Transport> usable = pt.build(p, 0, land).getTransports();
			List<Transport> now = new ArrayList<>();
			PlayerTransports.Everything all = pt.everything(p, land, null);
			for (PlayerTransports.Candidate c : all.getCandidates())
			{
				if (c.isUsableNow())
				{
					now.add(c.getTransport());
				}
			}
			boolean same = usable.size() == now.size();
			for (int i = 0; same && i < usable.size(); i++)
			{
				Transport a = usable.get(i);
				Transport b = now.get(i);
				same = a.getId().equals(b.getId()) && a.getEndX() == b.getEndX() && a.getEndY() == b.getEndY()
					&& a.getEndPlane() == b.getEndPlane() && a.hasStart() == b.hasStart() && a.getStartX() == b.getStartX()
					&& a.getStartY() == b.getStartY() && a.getStartPlane() == b.getStartPlane() && a.isTwoWay() == b.isTwoWay()
					&& a.cost(2) == b.cost(2) && a.isUsedFromBeside() == b.isUsedFromBeside();
			}
			System.out.printf(Locale.ROOT, "%s: %,d transports for routes, %,d in all for explanations; usable now %s%n", PlayerStateDiagnostics.name(p),
				usable.size(), all.getCandidates().size(), same ? "the same" : "DIFFERENT (bug)");
		}
	}

	/**
	 * The places with no known way: how many a search with the quest-only transports too would reach (they are never
	 * used on routes; another router on the same data with their quest-only flag off, dev only).
	 */
	private static void withQuestOnly(PluginData data, List<int[]> unknown, PluginRouter.TransportFilter filter)
	{
		List<com.bestpath.core.data.TransportLine> lines = new ArrayList<>();
		int questOnly = 0;
		for (com.bestpath.core.data.TransportLine l : data.getTransports())
		{
			if (l.isQuestOnly())
			{
				questOnly++;
				String[] c = TransportLineWriter.write(l).split("\t", -1);
				c[7] = c[7].replace("q", "");
				l = com.bestpath.core.data.TransportLine.parse(String.join("\t", c));
			}
			lines.add(l);
		}
		PluginData all = PluginDataBuilder.of(data.getMap(), data.getDoors(), data.getWalls(), lines, data.getTables(), data.getFreeLand());
		PluginRouter r = new PluginRouter(all, PluginRouter.DEFAULT_CACHE_BUDGET);
		int reached = 0;
		List<String> examples = new ArrayList<>();
		for (int[] d : unknown)
		{
			NoRouteReasons why = PluginRouterDiagnostics.explainNoRoute(r, PlayerStateBuilder.fresh(), LUMBRIDGE[0], LUMBRIDGE[1], LUMBRIDGE[2], d[0], d[1], d[2], id -> false,
				filter);
			if (why.getKind() == NoRouteReasons.Kind.BLOCKED)
			{
				reached++;
				if (examples.size() < 5)
				{
					examples.add("  " + d[0] + "," + d[1] + "," + d[2] + ": " + String.join(" | ", why.getReasons().subList(1, why.getReasons().size())));
				}
			}
		}
		System.out.printf(Locale.ROOT, "no known way: %d places; with the %d quest-only transports too, %d of them have a way%n", unknown.size(),
			questOnly, reached);
		examples.forEach(System.out::println);
	}

	/** One pass; returns the places with no known way. */
	private static List<int[]> pass(String title, CoreRouteEngine engine, PluginRouter router, PlayerState p, List<int[]> places,
		PluginRouter.TransportFilter filter, TransportSettings settings)
	{
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		long tid = Thread.currentThread().getId();
		Map<NoRouteReasons.Kind, Integer> kinds = new EnumMap<>(NoRouteReasons.Kind.class);
		List<Long> nanos = new ArrayList<>();
		List<Long> alloc = new ArrayList<>();
		List<String> examples = new ArrayList<>();
		int noRoute = 0;
		int mostBlocked = 0;
		int[] blockedCounts = new int[8];
		String slowest = "";
		long slowestNanos = 0;
		List<int[]> unknown = new ArrayList<>();
		for (int i = 0; i < places.size(); i++)
		{
			int[] d = places.get(i);
			RouteResult r = engine.route(new RouteJob(i, new RouteRequest(d[0], d[1], d[2]),
				new PlayerSnapshot(LUMBRIDGE[0], LUMBRIDGE[1], LUMBRIDGE[2], p, List.of()), settings, Set.of()));
			if (r.getStatus() != RouteStatus.NO_ROUTE)
			{
				continue;
			}
			noRoute++;
			long a0 = threads.getThreadAllocatedBytes(tid);
			long s0 = System.nanoTime();
			NoRouteReasons why = PluginRouterDiagnostics.explainNoRoute(router, p, LUMBRIDGE[0], LUMBRIDGE[1], LUMBRIDGE[2], d[0], d[1], d[2], id -> false, filter);
			long t = System.nanoTime() - s0;
			nanos.add(t);
			alloc.add(threads.getThreadAllocatedBytes(tid) - a0);
			kinds.merge(why.getKind(), 1, Integer::sum);
			if (why.getKind() == NoRouteReasons.Kind.NO_KNOWN_WAY)
			{
				unknown.add(d);
			}
			int b = why.getThings(); // distinct things (requirements counted, not transports)
			mostBlocked = Math.max(mostBlocked, b);
			if (why.getKind() == NoRouteReasons.Kind.BLOCKED)
			{
				blockedCounts[Math.min(b, blockedCounts.length - 1)]++;
			}
			String line = String.format(Locale.ROOT, "  %d,%d,%d: %s (%.0f ms)%s", d[0], d[1], d[2], why.getKind(), t / 1e6,
				why.getKind() == NoRouteReasons.Kind.BLOCKED ? " " + NoRouteReasonsDiagnostics.ticks(why) + " ticks; " + String.join(" | ",
					why.getReasons().subList(1, why.getReasons().size())) : " " + String.join(" ", why.getReasons()));
			if (t > slowestNanos)
			{
				// the search's parts (its last search is the explanation's when it searched at all)
				long[] ph = PathfinderDiagnostics.lastPhases(PluginRouterDiagnostics.pathfinder(router));
				slowestNanos = t;
				slowest = line + String.format(Locale.ROOT, " [search parts: reachability %.0f, coarse %.0f, tiles %.0f, walk shapes %.0f ms]",
					ph[0] / 1e6, ph[1] / 1e6, ph[2] / 1e6, ph[3] / 1e6);
			}
			if (examples.size() < 8)
			{
				examples.add(line);
			}
		}
		System.out.printf(Locale.ROOT, "%n== %s: %d places, %d no route%n", title, places.size(), noRoute);
		System.out.println("kinds: " + kinds);
		StringBuilder bc = new StringBuilder("BLOCKED ways by distinct things the player can't use:");
		for (int k = 1; k < blockedCounts.length; k++)
		{
			if (blockedCounts[k] > 0)
			{
				bc.append(' ').append(k == blockedCounts.length - 1 ? k + "+" : String.valueOf(k)).append(": ").append(blockedCounts[k]);
			}
		}
		System.out.println(bc + " (most " + mostBlocked + ")");
		if (!nanos.isEmpty())
		{
			System.out.printf(Locale.ROOT, "explanation time: median %.0f ms, p95 %.0f ms, worst %.0f ms; short-lived memory median %.0f MB,"
				+ " p95 %.0f MB, worst %.0f MB%n", pct(nanos, 50) / 1e6, pct(nanos, 95) / 1e6, pct(nanos, 100) / 1e6, pct(alloc, 50) / 1e6,
				pct(alloc, 95) / 1e6, pct(alloc, 100) / 1e6);
			System.out.println("slowest:" + slowest.substring(1));
		}
		examples.forEach(System.out::println);
		return unknown;
	}

	private static double pct(List<Long> v, int p)
	{
		long[] a = new long[v.size()];
		for (int i = 0; i < a.length; i++)
		{
			a[i] = v.get(i);
		}
		java.util.Arrays.sort(a);
		int i = Math.min(a.length - 1, (int) Math.ceil(p / 100.0 * a.length) - 1);
		return a[Math.max(0, i)];
	}
}
