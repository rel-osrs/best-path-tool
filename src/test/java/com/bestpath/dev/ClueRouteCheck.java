package com.bestpath.dev;

import com.bestpath.clue.ClueWatcher;
import com.bestpath.clue.DetectedClue;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.data.ClueDataWriter;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.PluginRouterDiagnostics;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.data.MapDataLoader;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.RouteJob;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * DEV ONLY (src/test, never in the plugin jar): the plugin's clue detection and clue routes on the exported data
 * (gradlew -p tool exportPlugin first). For every clue step it does what the plugin would do with made-up game
 * input: easy to elite (and quest) steps by their clue item, beginner and master steps by reading their own text
 * off the scroll (coordinates also in the "degrees / minutes" wording), and the two hot/cold devices. Then it routes
 * each detection from Lumbridge with the plugin's engine for two test players and reports how many were found and
 * how long the searches took (several places: one search per place at most, see PluginRouter.routeNearest).
 *
 * <p>{@code gradlew clueRouteCheck}
 */
public final class ClueRouteCheck
{
	private static final int[] START = {3222, 3218, 0};

	private ClueRouteCheck()
	{
	}

	/** One thing the plugin could detect, and its route results. */
	private static final class Unit
	{
		final String name;
		final DetectedClue clue;

		Unit(String name, DetectedClue clue)
		{
			this.name = name;
			this.clue = clue;
		}
	}

	public static void main(String[] args) throws Exception
	{
		PluginData data = PluginData.read(MapDataLoader.JAR);
		if (data == null || data.getClues().size() == 0)
		{
			System.out.println("No map data with clues.tsv: run gradlew -p tool exportPlugin first.");
			return;
		}
		ClueData clues = data.getClues();
		long before = usedAfterGc();
		ClueData again = ClueData.parse(Arrays.asList(ClueDataWriter.write(clues, "x").split("\n")));
		long clueHeap = usedAfterGc() - before;
		System.out.printf(Locale.ROOT, "clue steps: %,d; heap kept by the clue data: %.2f MB (%d)%n", clues.size(), clueHeap / 1e6,
			again.size());

		// ---- detection, the plugin's way
		List<Unit> units = new ArrayList<>();
		Map<String, int[]> byTier = new TreeMap<>(); // tier -> {steps, identified alone, identified with others, not found}
		for (ClueData.Step s : clues.getSteps())
		{
			if (s.getType().equals("hotcold"))
			{
				continue;
			}
			int[] c = byTier.computeIfAbsent(s.getTier(), k -> new int[4]);
			c[0]++;
			DetectedClue d;
			ClueWatcher w = new ClueWatcher(clues);
			if (s.getItems().length > 0)
			{
				d = w.onInventory(List.of(s.getItems()[0]), id -> -1);
			}
			else if (s.getTier().equals("beginner") || s.getTier().equals("master"))
			{
				w.onInventory(List.of(s.getTier().equals("beginner") ? ClueWatcher.BEGINNER_CLUE : ClueWatcher.MASTER_CLUE), id -> -1);
				d = w.onClueText(shownText(s));
			}
			else
			{
				c[3]++; // no item points at it and no text detection for its tier (docs/CLUES.md: possibly retired)
				continue;
			}
			if (d == null || d.getKind() != DetectedClue.Kind.STEP || !d.getSteps().contains(s))
			{
				c[3]++;
				System.out.println("  not identified: " + s.getKey() + " (" + s.getTier() + ") " + (d == null ? "no detection" : d.getKind()));
				continue;
			}
			c[d.getSteps().size() == 1 ? 1 : 2]++;
			units.add(new Unit(s.getKey(), d));
		}
		for (String tier : List.of("beginner", "master"))
		{
			int dev = tier.equals("beginner") ? ClueWatcher.BEGINNER_DEVICE : ClueWatcher.MASTER_DEVICE;
			units.add(new Unit("hot/cold " + tier, new ClueWatcher(clues).onInventory(List.of(dev), id -> -1)));
		}
		System.out.println("detection per tier (steps; identified alone; with others of the same text; not identified):");
		byTier.forEach((t, c) -> System.out.printf(Locale.ROOT, "  %-8s %4d %4d %4d %4d%n", t, c[0], c[1], c[2], c[3]));

		// ---- routes
		PluginRouter router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
		CoreRouteEngine engine = new CoreRouteEngine(router);
		Map<String, PlayerState> players = new LinkedHashMap<>();
		players.put("Maxed", PlayerStateBuilder.maxed());
		players.put("Fresh account", PlayerStateBuilder.fresh());
		for (Map.Entry<String, PlayerState> p : players.entrySet())
		{
			int found = 0;
			int routable = 0;
			int capped = 0;
			List<Long> single = new ArrayList<>();
			List<Long> several = new ArrayList<>();
			List<String> slow = new ArrayList<>();
			for (Unit u : units)
			{
				if (!u.clue.isRoutable())
				{
					continue;
				}
				routable++;
				PlayerSnapshot me = new PlayerSnapshot(START[0], START[1], START[2], p.getValue(), List.of());
				long t0 = System.nanoTime();
				RouteResult r = engine.route(new RouteJob(1, u.clue.request(), me, TransportSettings.defaults(), Set.of()));
				long ms = (System.nanoTime() - t0) / 1_000_000;
				found += r.getStatus() == RouteStatus.READY ? 1 : 0;
				capped += String.join(" ", r.getNotes()).contains("compared") ? 1 : 0;
				(u.clue.getTargets().length > 1 ? several : single).add(ms);
				if (u.clue.getTargets().length > 1)
				{
					PluginRouter.NearestAnswer n = PluginRouterDiagnostics.routeNearest(router, p.getValue(), START[0], START[1], START[2], u.clue.getTargets(),
						id -> false, CoreRouteEngine.MAX_SEARCHES);
					System.out.printf(Locale.ROOT, "  %s: %d places, %d with a way, %d full searches, %d ms%n", u.name,
						u.clue.getTargets().length, n.getReachable(), n.getSearched(), ms);
				}
				if (ms > 400)
				{
					slow.add(u.name + " " + u.clue.getTargets().length + " places " + ms + " ms");
				}
			}
			System.out.printf(Locale.ROOT, "%s from Lumbridge: %d of %d clue detections routed; one place: %s; several places: %s;"
				+ " search limit reached %d times%n", p.getKey(), found, routable, stats(single), stats(several), capped);
			for (String s : slow)
			{
				System.out.println("  slow: " + s);
			}
		}
	}

	/**
	 * The text as the clue scroll would show it: the cache text; for coordinates the "degrees / minutes" wording
	 * (both wordings must work, the scroll's exact wording is not known here).
	 */
	private static String shownText(ClueData.Step s)
	{
		String key = ClueData.coordinateKey(s.getText());
		if (s.getType().equals("coordinate") && key != null)
		{
			String[] w = key.split(" ");
			return words(w[0], "north", "south") + "<br>" + words(w[1], "east", "west");
		}
		return s.getText();
	}

	private static String words(String part, String pos, String neg)
	{
		char dir = part.charAt(part.length() - 1);
		String[] dm = part.substring(0, part.length() - 1).split("\\.");
		return String.format(Locale.ROOT, "%02d degrees %s minutes %s", Integer.parseInt(dm[0]), dm[1],
			dir == 'N' || dir == 'E' ? pos : neg);
	}

	private static String stats(List<Long> ms)
	{
		if (ms.isEmpty())
		{
			return "none";
		}
		List<Long> s = new ArrayList<>(ms);
		s.sort(Long::compare);
		return String.format(Locale.ROOT, "%d, median %d ms, p95 %d ms, worst %d ms", s.size(), s.get(s.size() / 2),
			s.get(Math.min(s.size() - 1, (int) (s.size() * 0.95))), s.get(s.size() - 1));
	}

	private static long usedAfterGc()
	{
		for (int i = 0; i < 3; i++)
		{
			System.gc();
		}
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory() - rt.freeMemory();
	}
}
