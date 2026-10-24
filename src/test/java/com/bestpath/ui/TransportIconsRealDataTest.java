package com.bestpath.ui;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.TickGroups;
import com.bestpath.data.MapDataLoader;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.RouteEngine;
import com.bestpath.route.TransportFinder;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

/**
 * The icons are relevant: the Settings tab's "Transport ticks" rows show the icon
 * their step card has; how many get one, by type, on the real data.
 */
public class TransportIconsRealDataTest
{
	@Test
	public void almostEveryRowHasItsCardsIcon()
	{
		RouteEngine engine = MapDataLoader.engine();
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		PluginData pd = ((CoreRouteEngine) engine).getRouter().getData();
		TransportFinder f = TransportFinder.of(pd.getTransports(), pd.getTables(), TickGroups.of(pd.getTransports(), pd.getTables()),
			pd.getWilderness());
		Map<String, int[]> byType = new TreeMap<>();
		Map<String, Integer> missing = new TreeMap<>();
		int with = 0;
		for (TransportFinder.Entry e : f.getEntries())
		{
			IconKey k = IconChooser.main(e.getCard());
			String type = String.valueOf(e.getCategory()) + "/" + e.getCard().getInfo().getCategory();
			int[] c = byType.computeIfAbsent(type, x -> new int[2]);
			c[0]++;
			if (k != null)
			{
				c[1]++;
				with++;
			}
			else
			{
				missing.merge(type + ": " + e.getTitle().replaceAll(" .*", ""), 1, Integer::sum);
			}
		}
		StringBuilder sb = new StringBuilder();
		byType.forEach((t, c) -> sb.append(t).append(' ').append(c[1]).append('/').append(c[0]).append("; "));
		System.out.println("card icons: " + with + " of " + f.getEntries().size() + " | " + sb);
		System.out.println("no card icon (top): " + top(missing));
		assertTrue(with * 10 >= f.getEntries().size() * 9);
	}

	private static String top(Map<String, Integer> m)
	{
		StringBuilder sb = new StringBuilder();
		m.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25)
			.forEach(e -> sb.append(e.getKey()).append(" x").append(e.getValue()).append("; "));
		return sb.toString();
	}
}
