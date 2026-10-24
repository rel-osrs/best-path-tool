package com.bestpath.dev;

import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.PluginRouterDiagnostics;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.data.MapDataLoader;
import com.bestpath.player.QuestNames;
import com.bestpath.player.QuestWatch;
import com.bestpath.player.RequirementInputs;
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
import java.util.TreeMap;
import net.runelite.api.Quest;

/**
 * DEV ONLY (src/test, never in the plugin jar): the Settings tab's categories and "avoid" rules on the real exported
 * transports (how many each covers, by the Data Tool's category), what each one changes on random routes as the
 * Maxed player, and how many of the quests the requirements mention have a known progress var (quest-vars.tsv).
 * Needs the export (gradlew -p tool exportPlugin). {@code gradlew settingsCheck [-Proutes=60]}
 */
public final class SettingsCheck
{
	private SettingsCheck()
	{
	}

	public static void main(String[] args) throws Exception
	{
		int n = args.length > 0 ? Integer.parseInt(args[0]) : 60;
		PluginData data = PluginData.read(MapDataLoader.JAR);
		if (data == null)
		{
			System.out.println("No map data in src/main/resources/com/bestpath/data: run gradlew -p tool exportPlugin first.");
			return;
		}
		TransportRules rules = new TransportRules(data.getTransports(), data.getTables());
		Map<String, Map<String, Integer>> byCategory = new TreeMap<>();
		Map<TransportSettings.Avoid, Integer> avoid = new EnumMap<>(TransportSettings.Avoid.class);
		for (TransportLine l : data.getTransports())
		{
			Transport t = l.hasStart()
				? Transport.between(l.getId(), l.getName(), l.getStartX(), l.getStartY(), l.getStartPlane(), l.getEndX(),
				l.getEndY(), l.getEndPlane(), l.getTicks(), false, true)
				: Transport.teleport(l.getId(), l.getName(), l.getEndX(), l.getEndY(), l.getEndPlane(), l.getTicks(), true);
			TransportSettings.Category c = rules.category(t, l);
			String what = l.getKind() + " " + l.getCategory() + (l.getTeleportCategory().isEmpty() ? "" : " / " + l.getTeleportCategory());
			byCategory.computeIfAbsent(c == null ? "(none: always used)" : c.name(), k -> new TreeMap<>()).merge(what, 1, Integer::sum);
			for (TransportSettings.Avoid a : rules.avoidRules(l))
			{
				avoid.merge(a, 1, Integer::sum);
			}
		}
		System.out.println("Transport lines per Settings category (" + data.getTransports().size() + " lines):");
		byCategory.forEach((c, m) ->
		{
			int sum = m.values().stream().mapToInt(Integer::intValue).sum();
			System.out.println("  " + c + ": " + sum + "  " + m);
		});
		System.out.println("Lines per avoid rule: " + avoid);

		RequirementInputs in = RequirementInputs.collect(new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET).requirements());
		Map<String, List<int[]>> vars = QuestWatch.loadTable();
		List<String> unknown = new ArrayList<>();
		List<String> noVar = new ArrayList<>();
		for (String q : in.getQuests())
		{
			Quest rq = QuestNames.find(q);
			if (rq == null)
			{
				unknown.add(q);
			}
			else if (!vars.containsKey(rq.name()))
			{
				noVar.add(q + " (" + rq.name() + ")");
			}
		}
		System.out.printf(Locale.ROOT, "Quests the requirements mention: %d; with a progress var: %d; RuneLite quest without a var: %s;"
			+ " not a RuneLite quest: %s%n", in.getQuests().size(), in.getQuests().size() - unknown.size() - noVar.size(), noVar, unknown);

		PluginRouter router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
		CoreRouteEngine engine = new CoreRouteEngine(router);
		List<int[]> qs = DataMemoryCheck.randomRoutes(PluginRouterDiagnostics.pathfinder(router).getGraph(), n, new Random(5));
		int[] base = ticks(engine, qs, TransportSettings.defaults());
		System.out.println("Effect on " + n + " random routes as Maxed (changed / no route any more / extra ticks in all):");
		for (TransportSettings.Category c : TransportSettings.Category.values())
		{
			effect(c.name() + " off", base, ticks(engine, qs, TransportSettings.defaults().withCategory(c, false)));
		}
		for (TransportSettings.Avoid a : TransportSettings.Avoid.values())
		{
			effect("avoid " + a.name(), base, ticks(engine, qs, TransportSettings.defaults().withAvoid(a, true)));
		}
	}

	private static int[] ticks(CoreRouteEngine engine, List<int[]> qs, TransportSettings s)
	{
		int[] t = new int[qs.size()];
		for (int i = 0; i < qs.size(); i++)
		{
			int[] q = qs.get(i);
			RouteResult r = engine.route(new RouteJob(i, new RouteRequest(q[3], q[4], q[5]),
				new PlayerSnapshot(q[0], q[1], q[2], PlayerStateBuilder.maxed(), List.of()), s, Set.of()));
			t[i] = r.getStatus() == RouteStatus.READY ? r.getTotalTicks() : -1;
		}
		return t;
	}

	private static void effect(String what, int[] base, int[] now)
	{
		int changed = 0;
		int lost = 0;
		long extra = 0;
		for (int i = 0; i < base.length; i++)
		{
			if (base[i] != now[i])
			{
				changed++;
			}
			if (base[i] >= 0 && now[i] < 0)
			{
				lost++;
			}
			else if (base[i] >= 0)
			{
				extra += now[i] - base[i];
			}
		}
		System.out.printf(Locale.ROOT, "  %-22s %3d changed, %2d no route, +%d ticks%n", what, changed, lost, extra);
	}
}
