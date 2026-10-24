package com.bestpath.route;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import com.bestpath.core.data.BankList;
import com.bestpath.core.data.BankListDiagnostics;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.path.Tiles;
import com.bestpath.data.MapDataLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * No bank is in the Wilderness. The search on from a bank stop (core BankFirst) relies on it: it
 * leaves out the Wilderness teleport limits, which only matter where the player stands in the Wilderness. If a game
 * update ever adds a Wilderness bank, this fails and BankFirst must pass the limits on. Skipped without the export.
 */
public class WildernessBanksRealDataTest
{
	@Test
	public void noShippedBankIsInTheWilderness()
	{
		RouteEngine engine = MapDataLoader.engine();
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		PluginData data = ((CoreRouteEngine) engine).getRouter().getData();
		WildernessZones wilderness = data.getWilderness();
		assumeTrue("no Wilderness data", !wilderness.isEmpty());
		List<String> inside = new ArrayList<>();
		int tiles = 0;
		for (BankList.Bank b : data.getBanks().getBanks())
		{
			for (int t : b.getUse())
			{
				tiles++;
				if (wilderness.isWilderness(Tiles.x(t), Tiles.y(t)))
				{
					inside.add(BankListDiagnostics.name(b) + " " + Tiles.x(t) + "," + Tiles.y(t));
				}
			}
		}
		assertTrue("the shipped banks have tiles", tiles > 0);
		assertTrue("banks in the Wilderness: " + inside, inside.isEmpty());
	}
}
