package com.bestpath.tool.route;

import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.PluginRouterDiagnostics;
import com.bestpath.core.data.SailingSetup;
import com.bestpath.core.data.SeaBits;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.LineOfSight;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.SailingDiagnostics;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.DockDiagnostics;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaGridDiagnostics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * The sailing part of the {@link PluginExportCli} check: the tool's Route tab search with Sailing ticked
 * ({@link RouteController#search}, the sea as the tool's data build makes it) against the plugin's
 * ({@link PluginRouter} on the sea read back from the file), for test boats as the uiCheck sailing cases set them up
 * (teak hull, oak sails; moored at Port Sarim unless at sea): a skiff, a sloop, a raft, a boat at sea, a teleport
 * focus, a greater focus, no shipwrights, a lower Sailing level, no quests, a capsized boat, and a bottled boat with
 * its bottle carried. Routes: near one dock to near another, to the sea, from the sea (the boat at sea), and anywhere.
 */
final class PluginSailingCheck
{
	private final PluginExportCli.Built b;
	private final PluginRouter router;
	private final CoarseGraph graph;
	private final Pathfinder toolPf;
	private final LineOfSight.WallFlags toolWalls;
	private final Sailing toolSail;
	/** The tool's transports per Sailing level and quests (level * 2, + 1 without quests). */
	private final Map<Integer, List<Transport>> toolTransports = new TreeMap<>();
	private int differ;
	private int total;

	private PluginSailingCheck(PluginExportCli.Built b, PluginRouter router, CoarseGraph graph, Pathfinder toolPf,
		LineOfSight.WallFlags toolWalls, Sailing toolSail)
	{
		this.b = b;
		this.router = router;
		this.graph = graph;
		this.toolPf = toolPf;
		this.toolWalls = toolWalls;
		this.toolSail = toolSail;
	}

	/** Checks the sea read back, the shipwrights' tiles, then {@code perBoat} routes per test boat. Returns {differ, total}. */
	static int[] check(PluginExportCli.Built b, PluginData data, PluginRouter router, CoarseGraph graph, Pathfinder toolPf,
		LineOfSight.WallFlags toolWalls, int perBoat)
	{
		Sailing toolSail = checkSeaReadBack(b, data, router, graph);
		checkShipwrights(toolSail, router);
		if (perBoat <= 0)
		{
			return new int[]{0, 0};
		}
		// "99" = Maxed (every quest done); "99noquests" = Sailing 99 without any quest: the 16 docks that need a quest are
		// not used (docks check their quests); "40" = Sailing 40, no quests. "capsized": the boat has
		// capsized (a shipwright brings it); "bottled": it is bottled and the Maxed player carries one full boat bottle
		String[][] setups = {
			{"skiff", "99"}, {"sloop", "99"}, {"raft", "99"}, {"atsea", "99"}, {"focus", "99"}, {"greater", "99"}, {"nowright", "99"},
			{"sloop", "40"}, {"sloop", "99noquests"}, {"capsized", "99"}, {"bottled", "99"}};
		PluginSailingCheck c = new PluginSailingCheck(b, router, graph, toolPf, toolWalls, toolSail);
		for (String[] setup : setups)
		{
			c.checkBoat(setup[0], setup[1], perBoat);
		}
		return new int[]{c.differ, c.total};
	}

	/** The sea, its docks and its channels must read back the same; returns the tool's sailing on the same sea. */
	private static Sailing checkSeaReadBack(PluginExportCli.Built b, PluginData data, PluginRouter router, CoarseGraph graph)
	{
		long t0 = System.nanoTime();
		SeaBits.Data sea = data.getSea();
		if (sea == null || !sea.getGrid().sameAs(b.seaGrid) || !sameDocks(sea.getDocks(), b.docks))
		{
			throw new IllegalStateException("the sea does not read back the same");
		}
		Sailing toolSail = new Sailing(b.seaGrid, b.docks, b.channels, graph, null);
		// the channels the plugin reads (crooked water the sea graph's links miss) must be the tool's own
		List<int[]> toolChannels = SailingDiagnostics.channels(toolSail);
		boolean sameChannels = toolChannels.size() == sea.getChannels().size();
		for (int i = 0; sameChannels && i < toolChannels.size(); i++)
		{
			sameChannels = Arrays.equals(toolChannels.get(i), sea.getChannels().get(i));
		}
		if (!sameChannels || router.getSailing() == null
			|| router.getSailing().getSeaGraph().channelCount() != toolSail.getSeaGraph().channelCount())
		{
			throw new IllegalStateException("the sea's channels do not read back the same");
		}
		System.out.printf(Locale.ROOT, "sea read back the same: %,d main-ocean tiles, %d docks (%d used for sailing), %d odd boat steps,"
				+ " %d channels; tool sailing set up in %.0f ms%n", sea.getGrid().size(), sea.getDocks().size(), toolSail.getDocks().size(),
			SeaGridDiagnostics.oddTiles(sea.getGrid()).length, toolChannels.size(), (System.nanoTime() - t0) / 1e6);
		return toolSail;
	}

	/** Routes walk to the shipwright: the tool and the plugin must pick the same tile next to each one. Prints them. */
	private static void checkShipwrights(Sailing toolSail, PluginRouter router)
	{
		Sailing pluginSail = router.getSailing();
		if (pluginSail == null || pluginSail.getDocks().size() != toolSail.getDocks().size())
		{
			throw new IllegalStateException("the plugin's sailing docks differ from the tool's");
		}
		List<String> stands = new ArrayList<>();
		for (int i = 0; i < toolSail.getDocks().size(); i++)
		{
			int[] ts = SailingDiagnostics.shipwrightStand(toolSail, i);
			if (!Arrays.equals(ts, SailingDiagnostics.shipwrightStand(pluginSail, i)))
			{
				throw new IllegalStateException("shipwright tile differs at " + toolSail.getDocks().get(i).getName());
			}
			Dock d = toolSail.getDocks().get(i);
			if (d.getShipwright() != null)
			{
				int[] w = d.getShipwrightTile();
				stands.add(d.getShipwright() + " (" + d.getName() + "): " + (ts == null ? "not walked to"
					: (w == null ? "?" : w[0] + "," + w[1]) + " -> " + ts[0] + "," + ts[1] + "," + ts[2]));
			}
		}
		System.out.printf(Locale.ROOT, "shipwrights walked to (Wiki tile -> tile next to it; tool and plugin the same): %s%n",
			String.join("; ", stands));
	}

	/** One test boat's routes ({@code levelText}: "99", "40" or "99noquests"); prints a line for the boat. */
	private void checkBoat(String name, String levelText, int perBoat)
	{
		boolean noQuests = levelText.endsWith("noquests");
		int level = Integer.parseInt(levelText.replace("noquests", ""));
		PlayerState player = name.equals("bottled") ? maxedWithBottle() : level == 99 && !noQuests ? PlayerStateBuilder.maxed()
			: new PlayerState.Builder("Sailing " + level).members(true).level(Skill.SAILING, level).build();
		List<Transport> tt = toolTransports.computeIfAbsent(level * 2 + (noQuests ? 1 : 0), k -> new ArrayList<>(RouteTransports.build(b.records,
			Collections.emptyList(), null, false, c -> false, id -> false, (c, tx, ty) -> null, new RequirementFilter(player, 0))));
		Boat boat = testBoat(name);
		boolean wrights = !name.equals("nowright");
		Sailing.Options toolOptions = new Sailing.Options(level, player, List.of(boat), 0, wrights, Sailing.DEFAULT_TURN_COST);
		SailingSetup setupForPlugin = new SailingSetup(List.of(boat), null, wrights);
		List<int[]> routes = sailingRoutes(graph, toolSail, b.seaGrid, new Random(name.hashCode() * 31L + level + (noQuests ? 7 : 0)),
			perBoat, name.equals("atsea"));
		PluginRouteCheck.Tally tally = new PluginRouteCheck.Tally(routes.size());
		int sails = 0;
		int viaWright = 0;
		for (int i = 0; i < routes.size(); i++)
		{
			int[] r = routes.get(i);
			PathQuery q = new PathQuery(r[0], r[1], r[2], r[3], r[4], r[5], tt, RouteTransports.DEFAULT_PLANE_CHANGE_PENALTY, b.doors,
				RouteController.DEFAULT_DOOR_COST);
			RouteController.Searched ts = RouteController.search(toolPf, toolSail, toolOptions, q, RouteController.DEFAULT_NEAR_RANGE,
				toolWalls, r[4] >= RouteController.DEFAULT_MAIN_MAP_TOP_Y);
			long s0 = System.nanoTime();
			PluginRouter.Answer a = PluginRouterDiagnostics.route(router, player, r[0], r[1], r[2], r[3], r[4], r[5], id -> false, PluginRouter.TransportFilter.NONE,
				setupForPlugin);
			tally.nanos[i] = System.nanoTime() - s0;
			boolean same = tally.same(ts.route, a.getRoute());
			sails += sails(a.getRoute()) ? 1 : 0;
			viaWright += viaShipwright(a.getRoute()) ? 1 : 0;
			if (!same || !Arrays.equals(ts.stop, a.getStop()))
			{
				differ++;
				System.out.printf(Locale.ROOT, "  DIFFERENT for %s (Sailing %d): %d,%d,%d -> %d,%d,%d: tool %s ticks (stop %s), plugin data %s ticks (stop %s)%n",
					name, level, r[0], r[1], r[2], r[3], r[4], r[5], PluginRouteCheck.ticks(ts.route),
					Arrays.toString(ts.stop), PluginRouteCheck.ticks(a.getRoute()), Arrays.toString(a.getStop()));
			}
			total++;
		}
		System.out.printf(Locale.ROOT, "  %-28s %3d routes, %3d sail (%d via a shipwright); fingerprints tool %016x, plugin data %016x %s; plugin median %.0f ms, worst %.0f ms%n",
			"boat " + name + " (Sailing " + level + (noQuests ? ", no quests" : "") + "):", routes.size(), sails, viaWright, tally.fpTool, tally.fpPlugin, tally.verdict(),
			tally.median(), tally.worst());
	}

	/** Whether the route sails. */
	private static boolean sails(Route r)
	{
		boolean sailed = false;
		for (Route.Step st : r.getSteps())
		{
			sailed |= st.getKind() == Route.Step.Kind.SAIL;
		}
		return sailed;
	}

	/** Whether a shipwright brings the boat on the way. */
	private static boolean viaShipwright(Route r)
	{
		boolean wright = false;
		for (Route.Step st : r.getSteps())
		{
			wright |= st.getTransport() != null && st.getTransport().getId().startsWith("sail-bring:")
				&& st.getTransport().getName().contains("(Recover-boat");
		}
		return wright;
	}

	/**
	 * The uiCheck sailing cases' boat: a teak skiff with oak sails moored at Port Sarim (dock 0) unless the name says
	 * otherwise (at sea, capsized, bottled).
	 */
	static Boat testBoat(String name)
	{
		BoatType type = name.contains("sloop") ? BoatType.SLOOP : name.contains("raft") ? BoatType.RAFT : BoatType.SKIFF;
		Boat.Focus focus = name.contains("greater") ? Boat.Focus.GREATER : name.contains("focus") ? Boat.Focus.TELEPORT : Boat.Focus.NONE;
		int dock = name.contains("atsea") ? Boat.AT_START : name.contains("capsized") ? Boat.CAPSIZED
			: name.contains("bottled") ? Boat.BOTTLED : 0;
		return new Boat(1, type, 2, 1, 0, 0, false, false, focus, dock);
	}

	/** The Maxed player with one full boat bottle in the inventory (a bottled boat can then be used). */
	static PlayerState maxedWithBottle()
	{
		PlayerState.Builder b = PlayerStateBuilder.allItems(new PlayerState.Builder("Maxed + boat bottle").members(true).allUnlocked(true), true)
			.carry(Boat.BOTTLE_FULL, 1);
		for (Skill s : Skill.values())
		{
			b.level(s, 99);
		}
		return b.build();
	}

	private static boolean sameDocks(List<Dock> a, List<Dock> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			if (!sameDock(a.get(i), b.get(i)))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean sameDock(Dock x, Dock y)
	{
		if (x.getId() != y.getId() || !x.getName().equals(y.getName()) || x.getLevel() != y.getLevel()
			|| !java.util.Objects.equals(x.getQuest(), y.getQuest()) || DockDiagnostics.kind(x) != DockDiagnostics.kind(y) || DockDiagnostics.landX(x) != DockDiagnostics.landX(y)
			|| DockDiagnostics.landY(x) != DockDiagnostics.landY(y) || DockDiagnostics.landPlane(x) != DockDiagnostics.landPlane(y) || DockDiagnostics.landSize(x) != DockDiagnostics.landSize(y)
			|| x.getBuoyX() != y.getBuoyX() || x.getBuoyY() != y.getBuoyY() || x.getBuoySize() != y.getBuoySize()
			|| !java.util.Objects.equals(x.getShipwright(), y.getShipwright()) || x.getLandTiles().size() != y.getLandTiles().size()
			|| !Arrays.equals(x.getShipwrightTile(), y.getShipwrightTile()))
		{
			return false;
		}
		for (int t = 0; t < x.getLandTiles().size(); t++)
		{
			if (!Arrays.equals(x.getLandTiles().get(t), y.getLandTiles().get(t)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Routes that may sail, a quarter each: near one dock to near another (within 30 tiles of the land side), near a
	 * dock to the sea (within 80 tiles of a buoy), near one dock to near another again, and near a dock to anywhere.
	 * With {@code atSea} every route starts at sea.
	 */
	static List<int[]> sailingRoutes(CoarseGraph g, Sailing s, SeaGrid sg, Random rnd, int n, boolean atSea)
	{
		List<Dock> docks = s.getDocks();
		boolean[] pockets = g.pockets();
		List<int[]> out = new ArrayList<>();
		while (out.size() < n)
		{
			int kind = out.size() % 4;
			Dock a = docks.get(rnd.nextInt(docks.size()));
			Dock c = docks.get(rnd.nextInt(docks.size()));
			int[] from = atSea ? nearSea(sg, a, rnd) : nearLand(g, pockets, a, rnd);
			int[] to = kind == 1 ? nearSea(sg, c, rnd) : kind == 3 ? anywhere(g, pockets, rnd) : nearLand(g, pockets, c, rnd);
			if (from != null && to != null)
			{
				out.add(new int[]{from[0], from[1], from[2], to[0], to[1], to[2]});
			}
		}
		return out;
	}

	private static int[] nearLand(CoarseGraph g, boolean[] pockets, Dock d, Random rnd)
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

	private static int[] nearSea(SeaGrid sg, Dock d, Random rnd)
	{
		for (int tries = 0; tries < 200; tries++)
		{
			int x = d.getBuoyX() + rnd.nextInt(161) - 80;
			int y = d.getBuoyY() + rnd.nextInt(161) - 80;
			if (sg.isOcean(x, y))
			{
				return new int[]{x, y, 0};
			}
		}
		return null;
	}

	private static int[] anywhere(CoarseGraph g, boolean[] pockets, Random rnd)
	{
		for (int tries = 0; tries < 1000; tries++)
		{
			int c = rnd.nextInt(g.componentCount());
			if (!pockets[c] && g.componentSize(c) >= 30)
			{
				return PluginExportCli.randomTile(g, c, rnd);
			}
		}
		return null;
	}
}
