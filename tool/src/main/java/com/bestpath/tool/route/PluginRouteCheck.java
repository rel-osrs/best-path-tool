package com.bestpath.tool.route;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.data.BoatTableWriter;
import com.bestpath.core.data.ClueDataWriter;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.PluginRouterDiagnostics;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.BankFirst;
import com.bestpath.core.path.LineOfSight;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Skill;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.edit.FreeLandFile;
import com.bestpath.tool.edit.TransportRecord;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The check of {@link PluginExportCli}: reads the written files back as the plugin does and compares the plugin's
 * routes with the tool's own search on the same data. Walking routes here, for seven test players (four plain ones,
 * two with a bank, one with a bank whose home and minigame teleports are cooling down); sailing routes in
 * {@link PluginSailingCheck}. Any difference ends the program with exit code 2.
 */
final class PluginRouteCheck
{
	/** A test player for the check. */
	private static final class Player
	{
		final String name;
		final PlayerState state;

		Player(String name, PlayerState state)
		{
			this.name = name;
			this.state = state;
		}
	}

	private final PluginExportCli.Built b;
	/** The files read back, and the plugin's search on them. */
	private final PluginData data;
	private final PluginRouter router;
	/** The tool's side, on the same export movement table. */
	private final CoarseGraph graph;
	private final Pathfinder toolPf;
	private final LineOfSight.WallFlags toolWalls;
	/** The tool's free-world search (made for the first free-to-play player). */
	private Pathfinder toolFree;
	/** Every route checked, as {from x, y, plane, to x, y, plane}; the route examples come first. */
	private final List<int[]> routes = new ArrayList<>();
	private int examples;
	/** The teleports' Wilderness limits, for a route that starts deeper than them (as the plugin). */
	private final PathQuery.Limits wildQ;
	/** Every tool route in words (to compare two runs line by line). */
	private final List<String> routeLines = new ArrayList<>();
	private int differ;
	private int total;

	private PluginRouteCheck(PluginExportCli.Built b, PluginData data, PluginRouter router)
	{
		this.b = b;
		this.data = data;
		this.router = router;
		// with the plugin's hub cells (the house spot), as PluginRouter searches
		CollisionMap toolMap = PluginRouter.searchMap(b.export, data.getTransports());
		graph = CoarseGraph.build(toolMap);
		toolPf = new Pathfinder(toolMap, graph, b.grid::get);
		toolWalls = b.grid::get;
		wildQ = wildernessLimits(b);
	}

	/**
	 * Reads the files back as the plugin does and compares routes with the tool's own search on the same data
	 * (export movement table, the tool's door map and wall flags, the tool's requirement filter), for the route
	 * examples and random routes, for each test player, then the sailing routes. The plugin side drops its caches
	 * whenever they pass the budget; the tool side never does.
	 */
	static void check(PluginExportCli.Built b, File outDir, int randomRoutes, int sailRoutes) throws IOException
	{
		long t0 = System.nanoTime();
		PluginData data = PluginData.read(name ->
		{
			File f = new File(outDir, name);
			return f.isFile() ? new FileInputStream(f) : null;
		});
		long t1 = System.nanoTime();
		PluginRouter router = new PluginRouter(data, PluginExportCli.CACHE_BUDGET);
		long t2 = System.nanoTime();
		System.out.printf(Locale.ROOT, "read back in %.0f ms (%s); coarse graph + search set up in %.0f ms%n",
			(t1 - t0) / 1e6, data.describe(), (t2 - t1) / 1e6);
		checkWalkTables(data, router);
		if (b.clues != null && b.clues.steps > 0)
		{
			checkClues(b, data);
		}
		if (b.boats != null && !BoatTableWriter.write(data.getBoats(), "").equals(BoatTableWriter.write(b.boats, "")))
		{
			throw new IllegalStateException("the boat table does not read back the same");
		}

		PluginRouteCheck c = new PluginRouteCheck(b, data, router);
		c.pickRoutes(randomRoutes);
		for (Player pl : players())
		{
			c.checkPlayer(pl);
		}
		c.writeRouteLines();
		int[] sea = PluginSailingCheck.check(b, data, router, c.graph, c.toolPf, c.toolWalls, sailRoutes);
		c.differ += sea[0];
		c.total += sea[1];
		System.out.printf(Locale.ROOT, "check: %d of %d routes differ between the tool's search and the plugin data (%s)%n",
			c.differ, c.total, c.differ == 0 ? "OK, the same routes" : "PROBLEM");
		if (c.differ > 0)
		{
			System.exit(2);
		}
	}

	private static void checkWalkTables(PluginData data, PluginRouter router)
	{
		if (data.getWalks() != null && !PluginRouterDiagnostics.pathfinder(router).hasWalkTables())
		{
			// made from these very files: a search that does not take them has another sub-region layout (a bug)
			throw new IllegalStateException("the plugin's search does not use the walk tables made for it (another sub-region layout)");
		}
		System.out.println("walk tables: " + (data.getWalks() == null ? "none" : "used by the plugin's search"));
	}

	/** The clue steps must read back the same, and every step's needs must read as a requirement line. */
	private static void checkClues(PluginExportCli.Built b, PluginData data)
	{
		List<com.bestpath.core.data.ClueData.Step> mine = b.clues.data.getSteps();
		List<com.bestpath.core.data.ClueData.Step> back = data.getClues().getSteps();
		int same = 0;
		for (int i = 0; i < Math.min(mine.size(), back.size()); i++)
		{
			same += ClueDataWriter.write(mine.get(i)).equals(ClueDataWriter.write(back.get(i))) ? 1 : 0;
		}
		if (same != mine.size() || back.size() != mine.size())
		{
			throw new IllegalStateException("clue steps do not read back the same: " + same + " of " + mine.size());
		}
		System.out.printf(Locale.ROOT, "clue steps read back the same: %,d%n", same);
		// every step's needs must read as a requirement line with the exported item groups (the plugin's clue card)
		int readable = 0;
		for (com.bestpath.core.data.ClueData.Step s : back)
		{
			try
			{
				com.bestpath.core.req.RequirementText.parse(s.getNeeds(), data.getTables().itemGroups());
				readable++;
			}
			catch (com.bestpath.core.req.RequirementText.ParseException e)
			{
				throw new IllegalStateException("clue " + s.getKey() + ": needs can't be read: " + e.getMessage());
			}
		}
		System.out.printf(Locale.ROOT, "clue needs readable as requirements: %,d%n", readable);
	}

	/** The teleports' Wilderness limits, for a route that starts deeper than them (as the plugin); null without zones. */
	private static PathQuery.Limits wildernessLimits(PluginExportCli.Built b)
	{
		Map<String, Integer> wildLimits = new java.util.HashMap<>();
		for (TransportRecord r : b.records)
		{
			if (r.wildernessLimit != null)
			{
				wildLimits.put(r.id, r.wildernessLimit);
			}
		}
		return b.wilderness == null ? null : b.wilderness.limits(t -> wildLimits.get(t.getId()));
	}

	/** The route examples, then random routes between big sub-regions (every other one within 200 tiles, same plane). */
	private void pickRoutes(int randomRoutes) throws IOException
	{
		for (CorpusExample e : examples(new File(ToolSettings.findWorkspace(), "data/route-examples.txt")))
		{
			routes.add(e.tiles);
		}
		examples = routes.size();
		boolean[] pockets = graph.pockets();
		List<Integer> comps = new ArrayList<>();
		for (int c = 0; c < graph.componentCount(); c++)
		{
			if (!pockets[c] && graph.componentSize(c) >= 30)
			{
				comps.add(c);
			}
		}
		Random rnd = new Random(7);
		while (routes.size() < examples + randomRoutes)
		{
			int[] a = PluginExportCli.randomTile(graph, comps.get(rnd.nextInt(comps.size())), rnd);
			int[] c = null;
			boolean near = routes.size() % 2 == 0;
			for (int tries = 0; tries < 200 && c == null; tries++)
			{
				int[] d = PluginExportCli.randomTile(graph, comps.get(rnd.nextInt(comps.size())), rnd);
				if (!near || (d[2] == a[2] && Math.abs(d[0] - a[0]) < 200 && Math.abs(d[1] - a[1]) < 200))
				{
					c = d;
				}
			}
			if (c != null)
			{
				// every 10th destination a little off a walkable tile (exercises the route-end step and the walls)
				int off = routes.size() % 10 == 3 ? 2 : 0;
				routes.add(new int[]{a[0], a[1], a[2], c[0] + off, c[1] + off, c[2]});
			}
		}
	}

	private static List<Player> players()
	{
		List<Player> players = new ArrayList<>();
		players.add(new Player("Maxed", PlayerStateBuilder.maxed()));
		players.add(new Player("Fresh account", PlayerStateBuilder.fresh()));
		players.add(new Player("Mid-level (Magic 45, Agility 55, members)",
			new PlayerState.Builder("Mid-level").members(true).level(Skill.MAGIC, 45).level(Skill.AGILITY, 55).build()));
		players.add(new Player("Free-to-play (Magic 30)", new PlayerState.Builder("F2P").members(false).level(Skill.MAGIC, 30).build()));
		// a route may begin with a bank visit; these players carry nothing and keep things in the bank
		players.add(new Player("Mid-level with a bank (runes, coins, jewellery in the bank)", withBank(new PlayerState.Builder("Banker")
			.members(true).level(Skill.MAGIC, 45).level(Skill.AGILITY, 55))));
		players.add(new Player("Free-to-play with a bank (runes, coins in the bank)", withBank(new PlayerState.Builder("F2P banker")
			.members(false).level(Skill.MAGIC, 30))));
		// home and minigame teleports work only while ready; this player has just used both (the Data Tool's
		// test-player option "Cooldowns used"), so routes must leave them out on both sides
		players.add(new Player("Mid-level with a bank, cooldowns used", withBank(new PlayerState.Builder("Banker, cooldowns used")
			.members(true).level(Skill.MAGIC, 45).level(Skill.AGILITY, 55)
			.coolingDown(com.bestpath.core.req.Cooldown.HOME, 0).coolingDown(com.bestpath.core.req.Cooldown.MINIGAME, 0))));
		return players;
	}

	/**
	 * A test player's bank (nothing carried): law, air, water, earth and fire runes, coins, and (members) charged
	 * games necklace, ring of dueling and amulet of glory (ids as in data/manual/runes.tsv and item-groups.tsv).
	 */
	static PlayerState withBank(PlayerState.Builder b)
	{
		b.bank(563, 200).bank(556, 1000).bank(555, 500).bank(557, 500).bank(554, 500).bank(PlayerState.COINS, 1_000_000);
		b.bank(3853, 1).bank(2552, 1).bank(1712, 1);
		return b.build();
	}

	/** Every route for one test player: the tool's search against the plugin's; prints a line for the player. */
	private void checkPlayer(Player pl)
	{
		boolean member = pl.state.isMembers();
		Pathfinder pf = member ? toolPf : toolFreeWorld();
		RequirementFilter needs = new RequirementFilter(pl.state, 0);
		List<Transport> all = new ArrayList<>(RouteTransports.build(b.records, Collections.emptyList(), null, false,
			c -> false, id -> false, (c, tx, ty) -> null, needs));
		FreeLand freeLand = member ? null : FreeLandFile.effective(b.edits.active());
		if (!member)
		{
			all.removeIf(t -> !freeLand.isFree(t.getEndX(), t.getEndY()) || t.hasStart() && !freeLand.isFree(t.getStartX(), t.getStartY()));
		}
		// the Route tab's search: what the player carries, and a bank first when that is quicker (as the plugin)
		List<Transport> toolTransports = needs.carried(all);
		RouteController.BankSearch bankSearch = all.size() == toolTransports.size() ? null
			: new RouteController.BankSearch(all, needs::takeOutFromBank, needs.banks(com.bestpath.tool.bank.ToolBanks.list(), freeLand),
			BankFirst.DEFAULT_TICKS);
		Tally tally = new Tally(routes.size());
		int banked = 0;
		int found = 0;
		int clearsBefore = PluginRouterDiagnostics.cacheClears(router);
		for (int i = 0; i < routes.size(); i++)
		{
			int[] r = routes.get(i);
			// the teleports' Wilderness limits, as the plugin (a start too deep: offered from shallow enough tiles)
			PathQuery q = new PathQuery(r[0], r[1], r[2], r[3], r[4], r[5], toolTransports,
				RouteTransports.DEFAULT_PLANE_CHANGE_PENALTY, b.doors, RouteController.DEFAULT_DOOR_COST).withLimits(wildQ);
			RouteController.Searched ts = RouteController.search(pf, null, null, q, RouteController.DEFAULT_NEAR_RANGE, toolWalls,
				r[4] >= RouteController.DEFAULT_MAIN_MAP_TOP_Y, bankSearch);
			banked += ts.plan != null && ts.plan.isBanked() ? 1 : 0;
			long s0 = System.nanoTime();
			PluginRouter.Answer a = PluginRouterDiagnostics.route(router, pl.state, r[0], r[1], r[2], r[3], r[4], r[5], id -> false);
			tally.nanos[i] = System.nanoTime() - s0;
			found += a.getRoute().isFound() ? 1 : 0;
			if (!tally.same(ts.route, a.getRoute()) || !Arrays.equals(ts.stop, a.getStop()))
			{
				differ++;
				System.out.printf(Locale.ROOT, "  DIFFERENT for %s: %d,%d,%d -> %d,%d,%d: tool %s ticks (stop %s), plugin data %s ticks (stop %s)%n",
					pl.name, r[0], r[1], r[2], r[3], r[4], r[5], ticks(ts.route), Arrays.toString(ts.stop),
					ticks(a.getRoute()), Arrays.toString(a.getStop()));
			}
			routeLines.add(pl.name + "\t" + r[0] + "," + r[1] + "," + r[2] + " -> " + r[3] + "," + r[4] + "," + r[5] + "\t" + describe(ts.route)
				+ (ts.plan != null && ts.plan.isBanked() ? "\twithout the bank: " + describe(ts.plan.getWithoutBank()) : ""));
			total++;
		}
		System.out.printf(Locale.ROOT, "  %-42s %3d routes (%d examples), %3d found, %d start at a bank; fingerprints tool %016x, plugin data %016x %s; plugin search median %.0f ms, worst %.0f ms; caches dropped %d times%n",
			pl.name + ":", routes.size(), examples, found, banked, tally.fpTool, tally.fpPlugin, tally.verdict(),
			tally.median(), tally.worst(), PluginRouterDiagnostics.cacheClears(router) - clearsBefore);
	}

	/** The tool's search for a free world: walking between free tiles only (made once). */
	private Pathfinder toolFreeWorld()
	{
		if (toolFree == null)
		{
			FreeLand land = FreeLandFile.effective(b.edits.active());
			CollisionMap m = PluginRouter.searchMap(b.export.restrictTo((x, y, p) -> land.isFree(x, y)), data.getTransports());
			toolFree = new Pathfinder(m, CoarseGraph.build(m), b.grid::get);
		}
		return toolFree;
	}

	private void writeRouteLines() throws IOException
	{
		File routesFile = new File(ToolSettings.findWorkspace(), "tool/build/export-routes.txt");
		if (routesFile.getParentFile().isDirectory())
		{
			Files.write(routesFile.toPath(), routeLines, StandardCharsets.UTF_8);
			System.out.println("every checked route in words: " + routesFile.getAbsolutePath());
		}
	}

	/** A route's ticks for a DIFFERENT line, or "no route". */
	static Object ticks(Route r)
	{
		return r.isFound() ? r.getTicks() : "no route";
	}

	/** One player's or boat's routes: both sides' fingerprints so far, and how long each plugin search took. */
	static final class Tally
	{
		long fpTool;
		long fpPlugin;
		final long[] nanos;

		Tally(int routes)
		{
			nanos = new long[routes];
		}

		/** Adds both routes' fingerprints; true when they are the same. */
		boolean same(Route tool, Route plugin)
		{
			long ft = fingerprint(tool);
			long fp = fingerprint(plugin);
			fpTool = fpTool * 31 + ft;
			fpPlugin = fpPlugin * 31 + fp;
			return ft == fp;
		}

		String verdict()
		{
			return fpTool == fpPlugin ? "SAME" : "DIFFERENT";
		}

		/** The median plugin search (ms). */
		double median()
		{
			long[] sorted = nanos.clone();
			Arrays.sort(sorted);
			return sorted[sorted.length / 2] / 1e6;
		}

		/** The slowest plugin search (ms). */
		double worst()
		{
			long[] sorted = nanos.clone();
			Arrays.sort(sorted);
			return sorted[sorted.length - 1] / 1e6;
		}
	}

	/** A route in words: ticks, then per step its kind, transport name, ticks and tiles (first and last). */
	static String describe(Route r)
	{
		if (r == null || !r.isFound())
		{
			return "no route";
		}
		StringBuilder sb = new StringBuilder().append(r.getTicks()).append(" ticks");
		for (Route.Step s : r.getSteps())
		{
			int[] t = s.getTiles();
			sb.append(" | ").append(s.getKind()).append(s.getTransport() != null ? " " + s.getTransport().getName() : "")
				.append(s.getKind() == Route.Step.Kind.BANK ? " " + s.getBankName() + " take out " + s.getTakeOut() : "")
				.append(' ').append(s.getTicks()).append(" (").append(tile(t[0])).append(" .. ").append(tile(t[t.length - 1])).append(')');
		}
		return sb.toString();
	}

	private static String tile(int packed)
	{
		return com.bestpath.core.path.Tiles.x(packed) + "," + com.bestpath.core.path.Tiles.y(packed) + "," + com.bestpath.core.path.Tiles.plane(packed);
	}

	/** Ticks, then per step its kind, tiles, ticks and the transport's ends (ids may differ: rides are named by stop). */
	static long fingerprint(Route r)
	{
		if (r == null || !r.isFound())
		{
			return -1;
		}
		long f = r.getTicks();
		for (Route.Step s : r.getSteps())
		{
			f = f * 31 + s.getKind().ordinal();
			f = f * 31 + Arrays.hashCode(s.getTiles());
			f = f * 31 + s.getTicks();
			if (s.getKind() == Route.Step.Kind.BANK)
			{
				f = f * 31 + s.getBankName().hashCode();
				f = f * 31 + s.getTakeOut().hashCode();
			}
			Transport t = s.getTransport();
			if (t != null)
			{
				f = f * 31 + (t.hasStart() ? t.getStartX() * 7919L + t.getStartY() * 31L + t.getStartPlane() : -1);
				f = f * 31 + t.getEndX() * 7919L + t.getEndY() * 31L + t.getEndPlane();
				f = f * 31 + (t.isTwoWay() ? 1 : 0);
			}
		}
		return f;
	}

	/** A line of data/route-examples.txt: name | start | destination [| options]. */
	private static final class CorpusExample
	{
		final int[] tiles;

		CorpusExample(int[] tiles)
		{
			this.tiles = tiles;
		}
	}

	private static List<CorpusExample> examples(File file) throws IOException
	{
		List<CorpusExample> out = new ArrayList<>();
		if (!file.isFile())
		{
			return out;
		}
		for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
		{
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] w = line.split("\\|");
			if (w.length < 3)
			{
				continue;
			}
			String[] a = w[1].trim().split(",");
			String[] c = w[2].trim().split(",");
			out.add(new CorpusExample(new int[]{Integer.parseInt(a[0].trim()), Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()),
				Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim())}));
		}
		return out;
	}
}
