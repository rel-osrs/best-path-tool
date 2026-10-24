package com.bestpath.dev;

import com.bestpath.core.data.PlayerTransportsDiagnostics;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataDiagnostics;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.PluginRouterDiagnostics;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.SailingDiagnostics;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.data.MapDataLoader;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.RouteJob;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * DEV ONLY (src/test, never in the plugin jar): how much memory the plugin's
 * map data and route search keep, and how long a route takes, with the
 * exported data in src/main/resources/com/bestpath/data (make it with
 * {@code gradlew -p tool exportPlugin}). Measures the heap after a full
 * garbage collection: before loading, after loading (data + coarse graph +
 * search set-up), and after N random routes (half within 200 tiles) as the
 * Maxed player through the plugin's real engine; then the same routes once
 * more with no cache budget, for comparison. The plugin limit is
 * 40 MB for the whole plugin.
 *
 * <p>{@code gradlew memoryCheck [-Proutes=100]}
 */
public final class DataMemoryCheck
{
	/** A free-to-play account (free worlds only). */
	private static final PlayerState FREE = new PlayerState.Builder("Free-to-play").members(false).build();

	private DataMemoryCheck()
	{
	}

	public static void main(String[] args) throws Exception
	{
		int routes = args.length > 0 ? Integer.parseInt(args[0]) : 100;
		long cold = usedAfterGc();
		// first load as the plugin does it at start (cold: classes loaded, nothing compiled yet), then dropped
		long c0 = System.nanoTime();
		PluginData first = PluginData.read(MapDataLoader.JAR);
		if (first == null)
		{
			System.out.println("No map data in src/main/resources/com/bestpath/data: run gradlew -p tool exportPlugin first.");
			return;
		}
		PluginRouter firstRouter = new PluginRouter(first, PluginRouter.DEFAULT_CACHE_BUDGET);
		long c1 = System.nanoTime();
		long coldKept = usedAfterGc() - cold;
		System.out.printf(Locale.ROOT, "first load (as at plugin start): %.0f ms; heap kept %.1f MB (includes the classes loaded on the way)%n",
			(c1 - c0) / 1e6, coldKept / 1e6);
		first = null;
		firstRouter = null;

		long base = usedAfterGc();
		long t0 = System.nanoTime();
		PluginData data = PluginData.read(MapDataLoader.JAR);
		if (data == null)
		{
			System.out.println("No map data in src/main/resources/com/bestpath/data: run gradlew -p tool exportPlugin first.");
			return;
		}
		long t1 = System.nanoTime();
		long afterRead = usedAfterGc() - base;
		PluginRouter router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
		long t2 = System.nanoTime();
		long afterLoad = usedAfterGc() - base;
		System.out.printf(Locale.ROOT, "map data: %s; %,d bytes as stored%n", data.describe(), PluginDataDiagnostics.bytes(data));
		System.out.printf(Locale.ROOT, "load: read %.0f ms, coarse graph + search set-up %.0f ms (total %.0f ms)%n",
			(t1 - t0) / 1e6, (t2 - t1) / 1e6, (t2 - t0) / 1e6);
		System.out.printf(Locale.ROOT, "heap kept: after reading the files %.1f MB, after building the search %.1f MB%n",
			afterRead / 1e6, afterLoad / 1e6);

		parts();
		com.bestpath.player.RequirementInputs inputs = com.bestpath.player.RequirementInputs.collect(router.requirements());
		System.out.printf(Locale.ROOT, "requirement inputs the plugin watches: %d varbits, %d varps, %d quests, %d items%n",
			inputs.getVarbits().size(), inputs.getVarps().size(), inputs.getQuests().size(), inputs.getItems().size());

		List<int[]> qs = randomRoutes(PluginRouterDiagnostics.pathfinder(router).getGraph(), routes, new Random(7));
		long b0 = System.nanoTime();
		com.bestpath.core.data.PlayerTransports pt = new com.bestpath.core.data.PlayerTransports(data.getTransports(), data.getTables());
		pt.build(PlayerStateBuilder.maxed(), 0, null);
		long b1 = System.nanoTime();
		for (int i = 0; i < 20; i++)
		{
			pt.build(PlayerStateBuilder.maxed(), 0, null);
		}
		long b2 = System.nanoTime();
		System.out.printf(Locale.ROOT, "transports for one player: first %.0f ms (reads %d requirement lines), then %.1f ms each%n",
			(b1 - b0) / 1e6, PlayerTransportsDiagnostics.parsedLineCount(pt), (b2 - b1) / 20 / 1e6);
		CoreRouteEngine engine = new CoreRouteEngine(router);
		long[] nanos = run(engine, qs);
		long afterRoutes = usedAfterGc() - base;
		System.out.printf(Locale.ROOT, "%d routes as Maxed (cache budget %.0f MB): median %.0f ms, p95 %.0f ms, worst %.0f ms; caches dropped %d times, "
				+ "caches now %.1f MB%n", qs.size(), PluginRouter.DEFAULT_CACHE_BUDGET / 1e6, pct(nanos, 50), pct(nanos, 95), pct(nanos, 100),
			PluginRouterDiagnostics.cacheClears(router), PluginRouterDiagnostics.cacheBytes(router) / 1e6);
		System.out.printf(Locale.ROOT, "heap kept after the routes (data + search + caches): %.1f MB (limit for the whole plugin: 40 MB)%n",
			afterRoutes / 1e6);

		// a player on a free world (one search, for the world type the player is on): the first
		// free-world route lets the members search (and the sea routes) go and makes the free-world one
		List<int[]> freeQs = new ArrayList<>();
		for (int[] q : randomRoutes(PluginRouterDiagnostics.pathfinder(router).getGraph(), routes * 20, new Random(11)))
		{
			if (freeQs.size() < routes && data.getFreeLand().isFree(q[0], q[1]) && data.getFreeLand().isFree(q[3], q[4]))
			{
				freeQs.add(q);
			}
		}
		long f0 = System.nanoTime();
		engine.route(new RouteJob(0, new RouteRequest(freeQs.get(0)[3], freeQs.get(0)[4], freeQs.get(0)[5]),
			new PlayerSnapshot(freeQs.get(0)[0], freeQs.get(0)[1], freeQs.get(0)[2], FREE, List.of()), TransportSettings.defaults(), Set.of()));
		long f1 = System.nanoTime();
		long[] fn = new long[freeQs.size()];
		for (int i = 0; i < freeQs.size(); i++)
		{
			int[] q = freeQs.get(i);
			long s = System.nanoTime();
			engine.route(new RouteJob(i, new RouteRequest(q[3], q[4], q[5]), new PlayerSnapshot(q[0], q[1], q[2], FREE, List.of()),
				TransportSettings.defaults(), Set.of()));
			fn[i] = System.nanoTime() - s;
		}
		long afterFree = usedAfterGc() - base;
		System.out.printf(Locale.ROOT, "free-world pass: first free-world route %.0f ms (replaces the members search); then %d free-world"
				+ " routes: median %.0f ms, worst %.0f ms; caches now %.1f MB, dropped %d times in all%n", (f1 - f0) / 1e6,
			freeQs.size(), pct(fn, 50), pct(fn, 100), PluginRouterDiagnostics.cacheBytes(router) / 1e6, PluginRouterDiagnostics.cacheClears(router));
		System.out.printf(Locale.ROOT, "heap kept on a free world (data + free-world search + caches): %.1f MB"
			+ " (limit for the whole plugin: 40 MB)%n", afterFree / 1e6);

		// back on a members world for the passes below: the first members route makes the members search and the sea
		// routes again
		int[] back = qs.get(0);
		long h0 = System.nanoTime();
		engine.route(new RouteJob(0, new RouteRequest(back[3], back[4], back[5]), new PlayerSnapshot(back[0], back[1], back[2],
			PlayerStateBuilder.maxed(), List.of()), TransportSettings.defaults(), Set.of()));
		System.out.printf(Locale.ROOT, "back on a members world: first route %.0f ms (makes the members search and the sea routes"
			+ " again)%n", (System.nanoTime() - h0) / 1e6);

		// sailing: the same engine with a test sloop moored at Port Sarim, on routes that may sail
		sailingPass(engine, router, data, routes, base);

		// a route may begin with a bank visit: a player who keeps runes, coins and jewellery in the bank
		bankPass(engine, router, qs, base);

		// the same again with no budget: what the caches would grow to
		engine = null;
		router = null;
		usedAfterGc();
		PluginRouter unbounded = new PluginRouter(data, Long.MAX_VALUE);
		long[] n2 = run(new CoreRouteEngine(unbounded), qs);
		long afterUnbounded = usedAfterGc() - base;
		System.out.printf(Locale.ROOT, "for comparison, no cache budget: median %.0f ms, p95 %.0f ms, worst %.0f ms; caches %.1f MB; heap kept %.1f MB%n",
			pct(n2, 50), pct(n2, 95), pct(n2, 100), PluginRouterDiagnostics.cacheBytes(unbounded) / 1e6, afterUnbounded / 1e6);
	}

	/**
	 * Going to a bank first: the same random routes for the Data Tool export check's "Mid-level with a bank" player
	 * ({@link RealData#BANK_PLAYER}: carries nothing; runes, coins and jewellery in the bank), and for the same player
	 * with an empty bank (the cost of weighing the bank), and for the banker going to a clue whose spade is only in the
	 * bank (the route must begin at a bank: every bank is looked for, no limit from the route without the bank). Time,
	 * short-lived memory per route, routes that start at a bank, heap kept.
	 */
	private static void bankPass(CoreRouteEngine engine, PluginRouter router, List<int[]> qs, long base)
	{
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		long tid = Thread.currentThread().getId();
		PlayerState banker = RealData.players().get(RealData.BANK_PLAYER).getState();
		PlayerState empty = new PlayerState.Builder("Mid-level, empty bank").members(true).level(com.bestpath.core.req.Skill.MAGIC, 45)
			.level(com.bestpath.core.req.Skill.AGILITY, 55).build();
		PlayerState clue = new PlayerState.Builder("Banker, spade in the bank").members(true).level(com.bestpath.core.req.Skill.MAGIC, 45)
			.level(com.bestpath.core.req.Skill.AGILITY, 55).bank(563, 200).bank(556, 1000).bank(555, 500).bank(557, 500).bank(554, 500)
			.bank(PlayerState.COINS, 1_000_000).bank(3853, 1).bank(2552, 1).bank(1712, 1).bank(952, 1).build();
		com.bestpath.route.DestinationNeeds spade = new com.bestpath.route.DestinationNeeds(List.of(
			new com.bestpath.route.DestinationNeeds.Item("Spade", new int[]{952}, 1)));
		for (PlayerState p : new PlayerState[]{empty, banker, clue})
		{
			long[] nanos = new long[qs.size()];
			long[] alloc = new long[qs.size()];
			int found = 0;
			int banked = 0;
			for (int i = 0; i < qs.size(); i++)
			{
				int[] q = qs.get(i);
				long a0 = threads.getThreadAllocatedBytes(tid);
				long s0 = System.nanoTime();
				RouteRequest req = new RouteRequest(q[3], q[4], q[5]);
				RouteResult r = engine.route(new RouteJob(i, p == clue ? req.withNeeds(spade) : req, new PlayerSnapshot(q[0], q[1], q[2], p, List.of()),
					TransportSettings.defaults(), Set.of()));
				nanos[i] = System.nanoTime() - s0;
				alloc[i] = threads.getThreadAllocatedBytes(tid) - a0;
				found += r.getStatus() == RouteStatus.READY ? 1 : 0;
				for (com.bestpath.route.RouteStep st : r.getSteps())
				{
					if (st.getKind() == com.bestpath.route.RouteStep.Kind.BANK)
					{
						banked++;
						break;
					}
				}
			}
			System.out.printf(Locale.ROOT, "bank pass, %s: %d routes (%d found, %d start at a bank): median %.0f ms, p95 %.0f ms, worst %.0f ms;"
					+ " short-lived memory per route median %.0f MB, p95 %.0f MB, worst %.0f MB%n", p == banker ? RealData.BANK_PLAYER
					: p == clue ? "the same going to a clue whose spade is only in the bank (must begin at a bank)" : "same player, empty bank",
				qs.size(), found, banked, pct(nanos, 50), pct(nanos, 95), pct(nanos, 100), pct(alloc, 50), pct(alloc, 95), pct(alloc, 100));
		}
		System.out.printf(Locale.ROOT, "heap kept after the bank pass (data + searches + caches): %.1f MB (limit for the whole plugin: 40 MB);"
			+ " caches dropped %d times in all%n", (usedAfterGc() - base) / 1e6, PluginRouterDiagnostics.cacheClears(router));
	}

	/**
	 * Sailing: {@code routes} routes as Maxed with the preview's test sloop (teak hull, oak sails, moored at Port
	 * Sarim): a quarter each near one dock to near another, near a dock to the sea, near a dock to near another,
	 * near a dock to anywhere (as the export's check). Time, short-lived memory made per route (bytes allocated by
	 * this thread), and the heap kept afterwards.
	 */
	private static void sailingPass(CoreRouteEngine engine, PluginRouter router, PluginData data, int routes, long base)
	{
		if (router.getSailing() == null)
		{
			System.out.println("sailing pass: the map data has no sea file (export made before sailing was added?)");
			return;
		}
		com.bestpath.core.path.Sailing sailing = router.getSailing();
		List<com.bestpath.core.sea.Dock> docks = sailing.getDocks();
		com.bestpath.core.sea.SeaGrid sea = data.getSea().getGrid();
		CoarseGraph g = PluginRouterDiagnostics.pathfinder(router).getGraph();
		boolean[] pockets = g.pockets();
		Random rnd = new Random(5);
		List<int[]> qs = new ArrayList<>();
		while (qs.size() < routes)
		{
			int kind = qs.size() % 4;
			int[] from = nearLand(g, pockets, docks.get(rnd.nextInt(docks.size())), rnd);
			com.bestpath.core.sea.Dock c = docks.get(rnd.nextInt(docks.size()));
			int[] to = kind == 1 ? nearSea(sea, c, rnd) : kind == 3 ? tileOf(g, anyComponent(g, pockets, rnd), rnd) : nearLand(g, pockets, c, rnd);
			if (from != null && to != null)
			{
				qs.add(new int[]{from[0], from[1], from[2], to[0], to[1], to[2]});
			}
		}
		com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		long tid = Thread.currentThread().getId();
		PlayerState maxed = PlayerStateBuilder.maxed();
		long[] nanos = new long[qs.size()];
		long[] alloc = new long[qs.size()];
		int found = 0;
		int sails = 0;
		int dockSearches = 0;
		int skipped = 0;
		// -PsailDump=<file>: one line per route (status, ticks, step kinds and titles, end tile) to compare two versions
		String dumpTo = System.getProperty("sailDump", "");
		List<String> dump = new ArrayList<>();
		for (int i = 0; i < qs.size(); i++)
		{
			int[] q = qs.get(i);
			PlayerSnapshot me = new PlayerSnapshot(q[0], q[1], q[2], maxed, RealData.TEST_BOATS);
			long a0 = threads.getThreadAllocatedBytes(tid);
			long s0 = System.nanoTime();
			RouteResult r = engine.route(new RouteJob(i, new RouteRequest(q[3], q[4], q[5]), me, TransportSettings.defaults(), Set.of()));
			nanos[i] = System.nanoTime() - s0;
			alloc[i] = threads.getThreadAllocatedBytes(tid) - a0;
			found += r.getStatus() == RouteStatus.READY ? 1 : 0;
			dockSearches += SailingDiagnostics.lastDockSearches(sailing)[0];
			skipped += SailingDiagnostics.lastDockSearches(sailing)[1];
			for (com.bestpath.route.RouteStep st : r.getSteps())
			{
				if (st.getKind() == com.bestpath.route.RouteStep.Kind.SAIL)
				{
					sails++;
					break;
				}
			}
			if (!dumpTo.isEmpty())
			{
				StringBuilder sb = new StringBuilder().append(i).append(' ').append(java.util.Arrays.toString(q)).append(' ')
					.append(r.getStatus()).append(' ').append(r.getTotalTicks());
				int end = -1;
				for (com.bestpath.route.RouteStep st : r.getSteps())
				{
					sb.append(" | ").append(st.getKind()).append(' ').append(st.getTicks()).append(' ').append(st.getTitle());
					end = st.getEndTile();
				}
				sb.append(" | end ").append(end < 0 ? "-" : com.bestpath.core.path.Tiles.x(end) + "," + com.bestpath.core.path.Tiles.y(end)
					+ "," + com.bestpath.core.path.Tiles.plane(end));
				dump.add(sb.toString());
			}
		}
		if (!dumpTo.isEmpty())
		{
			try
			{
				java.nio.file.Files.write(java.nio.file.Paths.get(dumpTo), dump);
			}
			catch (java.io.IOException e)
			{
				throw new java.io.UncheckedIOException(e);
			}
		}
		long kept = usedAfterGc() - base;
		System.out.printf(Locale.ROOT, "sailing pass: %d routes as Maxed with a sloop at Port Sarim (%d found, %d sail): median %.0f ms, p95 %.0f ms,"
				+ " worst %.0f ms; short-lived memory per route median %.0f MB, p95 %.0f MB, worst %.0f MB; walking searches to docks %d"
				+ " (%d more ruled out by lower bounds); caches dropped %d times in all%n", qs.size(), found, sails, pct(nanos, 50), pct(nanos, 95),
			pct(nanos, 100), pct(alloc, 50), pct(alloc, 95), pct(alloc, 100), dockSearches, skipped,
			PluginRouterDiagnostics.cacheClears(router));
		System.out.printf(Locale.ROOT, "heap kept after the sailing pass (data + sea + searches + caches): %.1f MB (limit for the whole plugin: 40 MB)%n",
			kept / 1e6);
	}

	private static int[] nearLand(CoarseGraph g, boolean[] pockets, com.bestpath.core.sea.Dock d, Random rnd)
	{
		int[] t = d.getLandTiles().get(0);
		for (int tries = 0; tries < 200; tries++)
		{
			int x = t[0] + rnd.nextInt(61) - 30;
			int y = t[1] + rnd.nextInt(61) - 30;
			int c = g.componentAt(x, y, t[2]);
			if (c >= 0 && !pockets[c] && g.componentSize(c) >= 30)
			{
				return new int[]{x, y, t[2]};
			}
		}
		return null;
	}

	private static int[] nearSea(com.bestpath.core.sea.SeaGrid sea, com.bestpath.core.sea.Dock d, Random rnd)
	{
		for (int tries = 0; tries < 200; tries++)
		{
			int x = d.getBuoyX() + rnd.nextInt(161) - 80;
			int y = d.getBuoyY() + rnd.nextInt(161) - 80;
			if (sea.isOcean(x, y))
			{
				return new int[]{x, y, 0};
			}
		}
		return null;
	}

	private static int anyComponent(CoarseGraph g, boolean[] pockets, Random rnd)
	{
		while (true)
		{
			int c = rnd.nextInt(g.componentCount());
			if (!pockets[c] && g.componentSize(c) >= 30)
			{
				return c;
			}
		}
	}

	/** Each part on its own (decoded again and kept while measuring), for the breakdown. */
	private static void parts() throws Exception
	{
		List<Object> keep = new ArrayList<>();
		StringBuilder sb = new StringBuilder("heap per part:");
		String[] names = {PluginData.MOVEMENT, PluginData.DOORS, PluginData.WALLS, PluginData.TRANSPORTS, PluginData.FREE_LAND, PluginData.SEA};
		for (String name : names)
		{
			byte[] b;
			try (java.io.InputStream in = MapDataLoader.class.getResourceAsStream(MapDataLoader.FOLDER + name))
			{
				if (in == null)
				{
					continue; // an optional file this export does not have
				}
				b = in.readAllBytes();
			}
			long before = usedAfterGc();
			Object o;
			switch (name)
			{
				case PluginData.MOVEMENT:
					o = com.bestpath.core.collision.CollisionMapCodec.read(new java.io.ByteArrayInputStream(b));
					break;
				case PluginData.DOORS:
					o = com.bestpath.core.data.DoorBits.read(new java.io.ByteArrayInputStream(b));
					break;
				case PluginData.WALLS:
					o = com.bestpath.core.data.WallBits.read(new java.io.ByteArrayInputStream(b));
					break;
				case PluginData.TRANSPORTS:
				{
					List<com.bestpath.core.data.TransportLine> l = new ArrayList<>();
					for (String line : PluginData.lines(b))
					{
						if (!line.startsWith("#"))
						{
							l.add(com.bestpath.core.data.TransportLine.parse(line));
						}
					}
					o = l;
					break;
				}
				case PluginData.SEA:
				{
					long t0 = System.nanoTime();
					o = com.bestpath.core.data.SeaBits.read(new java.io.ByteArrayInputStream(b));
					long t1 = System.nanoTime();
					sb.append(String.format(Locale.ROOT, " (sea read with clearance %.0f ms)", (t1 - t0) / 1e6));
					break;
				}
				default:
					o = com.bestpath.core.collision.FreeLand.parse(PluginData.lines(b));
					break;
			}
			b = null;
			keep.add(o);
			sb.append(String.format(Locale.ROOT, " %s %.2f MB;", name, (usedAfterGc() - before) / 1e6));
		}
		// the sea graph and the docks' joins (built when the router is made)
		for (Object o : keep)
		{
			if (o instanceof com.bestpath.core.data.SeaBits.Data)
			{
				com.bestpath.core.data.SeaBits.Data sea = (com.bestpath.core.data.SeaBits.Data) o;
				PluginData data = PluginData.read(MapDataLoader.JAR);
				CoarseGraph g = CoarseGraph.build(data.getMap());
				data = null;
				long before = usedAfterGc();
				long t0 = System.nanoTime();
				com.bestpath.core.path.Sailing s = new com.bestpath.core.path.Sailing(sea.getGrid(), sea.getDocks(), null, g, null);
				long t1 = System.nanoTime();
				long after = usedAfterGc();
				sb.append(String.format(Locale.ROOT, " sea graph + docks %.2f MB (built in %.0f ms; %,d waypoints, %d docks used)",
					(after - before) / 1e6, (t1 - t0) / 1e6, s.getSeaGraph().waypointCount(), s.getDocks().size()));
				keep.add(s);
				keep.add(g);
				break;
			}
		}
		System.out.println(sb);
		keep.clear();
	}

	private static long[] run(CoreRouteEngine engine, List<int[]> qs)
	{
		PlayerState maxed = PlayerStateBuilder.maxed();
		long[] nanos = new long[qs.size()];
		int found = 0;
		for (int i = 0; i < qs.size(); i++)
		{
			int[] q = qs.get(i);
			PlayerSnapshot me = new PlayerSnapshot(q[0], q[1], q[2], maxed, List.of());
			long s = System.nanoTime();
			RouteResult r = engine.route(new RouteJob(i, new RouteRequest(q[3], q[4], q[5]), me, TransportSettings.defaults(), Set.of()));
			nanos[i] = System.nanoTime() - s;
			found += r.getStatus() == RouteStatus.READY ? 1 : 0;
		}
		System.out.printf(Locale.ROOT, "  (%d of %d found)%n", found, qs.size());
		return nanos;
	}

	/** Random walkable tiles in sub-regions of 30+ tiles that are not closed-off pockets; every other route within 200 tiles. */
	static List<int[]> randomRoutes(CoarseGraph g, int n, Random rnd)
	{
		boolean[] pockets = g.pockets();
		List<Integer> comps = new ArrayList<>();
		for (int c = 0; c < g.componentCount(); c++)
		{
			if (!pockets[c] && g.componentSize(c) >= 30)
			{
				comps.add(c);
			}
		}
		List<int[]> out = new ArrayList<>();
		while (out.size() < n)
		{
			int[] a = tileOf(g, comps.get(rnd.nextInt(comps.size())), rnd);
			boolean near = out.size() % 2 == 0;
			for (int tries = 0; tries < 200; tries++)
			{
				int[] b = tileOf(g, comps.get(rnd.nextInt(comps.size())), rnd);
				if (!near || (b[2] == a[2] && Math.abs(b[0] - a[0]) < 200 && Math.abs(b[1] - a[1]) < 200))
				{
					out.add(new int[]{a[0], a[1], a[2], b[0], b[1], b[2]});
					break;
				}
			}
		}
		return out;
	}

	private static int[] tileOf(CoarseGraph g, int comp, Random rnd)
	{
		int cell = g.componentCell(comp);
		int bx = (cell >> 10) << 6;
		int by = ((cell >> 2) & 0xFF) << 6;
		int p = cell & 3;
		while (true)
		{
			int x = bx + rnd.nextInt(64);
			int y = by + rnd.nextInt(64);
			if (g.componentAt(x, y, p) == comp)
			{
				return new int[]{x, y, p};
			}
		}
	}

	static long usedAfterGc()
	{
		Runtime rt = Runtime.getRuntime();
		for (int i = 0; i < 4; i++)
		{
			System.gc();
			try
			{
				Thread.sleep(150);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}
		return rt.totalMemory() - rt.freeMemory();
	}

	private static double pct(long[] v, int p)
	{
		long[] s = v.clone();
		Arrays.sort(s);
		int i = Math.min(s.length - 1, (int) Math.ceil(p / 100.0 * s.length) - 1);
		return s[Math.max(0, i)] / 1e6;
	}
}
