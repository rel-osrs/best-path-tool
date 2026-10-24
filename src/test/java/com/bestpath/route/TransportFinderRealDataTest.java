package com.bestpath.route;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.TickGroups;
import com.bestpath.data.MapDataLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The filtering criteria are generated properly. On the real data: every Type and every Tag the list offers (such as
 * "the wilderness") finds transports, and the rows have their card's icon.
 */
public class TransportFinderRealDataTest
{
	private static TransportFinder finder()
	{
		RouteEngine engine = MapDataLoader.engine();
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		PluginData pd = ((CoreRouteEngine) engine).getRouter().getData();
		return TransportFinder.of(pd.getTransports(), pd.getTables(), TickGroups.of(pd.getTransports(), pd.getTables()),
			pd.getWilderness());
	}

	private static int count(TransportFinder f, TransportSettings.Category type, TransportSettings.Avoid tag)
	{
		return f.find(new TransportFinder.Query("", TransportFinder.Show.ALL, type, tag, TransportFinder.Sort.NAME, -1),
			TransportSettings.defaults()).size();
	}

	@Test
	public void everyTypeAndTagFindsTransports()
	{
		TransportFinder f = finder();
		List<String> lines = new ArrayList<>();
		for (TransportSettings.Category c : TransportSettings.Category.values())
		{
			int n = count(f, c, null);
			lines.add(c + " " + n);
			assertTrue(c + " has none", n > 0);
		}
		for (TransportSettings.Avoid a : TransportSettings.Avoid.values())
		{
			if (!TransportRules.isSupported(a))
			{
				continue; // not offered as a tag (no data)
			}
			int n = count(f, null, a);
			lines.add(a + " " + n);
			assertTrue(a + " has none", n > 0);
		}
		int none = 0;
		for (TransportFinder.Entry e : f.getEntries())
		{
			none += e.getCategory() == null ? 1 : 0;
		}
		lines.add("in no type " + none);
		System.out.println("Transport ticks filters: " + lines);
		// a Wilderness transport: the obelisks, the Wilderness ditch's area, the Mage Arena...
		assertTrue(count(f, null, TransportSettings.Avoid.WILDERNESS) > 50);
	}

	@Test
	public void everyTransportHasItsCardStep()
	{
		TransportFinder f = finder();
		for (TransportFinder.Entry e : f.getEntries())
		{
			assertTrue(e.getTitle(), e.getCard() != null && e.getCard().getTransportId().equals(e.getId()));
		}
	}
}
