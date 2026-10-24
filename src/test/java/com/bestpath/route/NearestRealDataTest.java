package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.data.MapDataLoader;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * "Go to nearest" with the exported data: the places table is there, the banks come from banks.tsv, and routes from
 * Lumbridge to the nearest place of each kind are found quickly (some kinds have hundreds of places). Skipped when the export is missing.
 */
public class NearestRealDataTest
{
	/** Lumbridge, outside the castle. */
	private static final int X = 3222;
	private static final int Y = 3218;

	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	private static PluginData data()
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		PluginData d = ((CoreRouteEngine) engine).getRouter().getData();
		assumeTrue("no places table", !d.getPlaces().isEmpty());
		return d;
	}

	private static RouteResult route(PlaceKinds.Kind kind, PlayerState p)
	{
		return engine.route(new RouteJob(1, NearestPlaces.request(kind), new PlayerSnapshot(X, Y, 0, p, List.of()), TransportSettings.defaults(),
			Set.of()));
	}

	private static PlayerState member()
	{
		return new PlayerState.Builder("p").members(true).build();
	}

	@Test
	public void theKindsAreThereWithTheBanks()
	{
		PluginData d = data();
		PlaceKinds kinds = d.getPlaces().withBanks(d.getBanks());
		for (String k : new String[]{"Bank", "Altar", "Anvil", "Furnace", "Cooking range", "Spinning wheel"})
		{
			assertNotNull(k, kinds.find(k));
			assertTrue(k, kinds.find(k).getTiles().length > 3);
		}
		assertEquals("the bank places are not repeated in places.tsv", 0, d.getPlaces().find(PlaceKinds.BANK).getTiles().length);
	}

	@Test
	public void eachKindIsReachedQuickly()
	{
		PluginData d = data();
		PlaceKinds kinds = d.getPlaces().withBanks(d.getBanks());
		route(kinds.find("Bank"), member()); // the first search makes the search tables
		long worst = 0;
		String worstKind = "";
		int found = 0;
		StringBuilder report = new StringBuilder();
		for (PlaceKinds.Kind k : kinds.getKinds())
		{
			long t0 = System.nanoTime();
			RouteResult r = route(k, member());
			long ms = (System.nanoTime() - t0) / 1_000_000;
			if (ms > worst)
			{
				worst = ms;
				worstKind = k.getName();
			}
			found += r.getStatus() == RouteStatus.READY ? 1 : 0;
			report.append(String.format(java.util.Locale.ROOT, "%-18s %4d places %-9s %5d ticks %5d ms%n", k.getName(), k.getTiles().length,
				r.getStatus(), r.getStatus() == RouteStatus.READY ? r.getTotalTicks() : -1, ms));
		}
		System.out.print(report);
		System.out.println("nearest: " + found + " of " + kinds.getKinds().size() + " kinds found from Lumbridge; slowest " + worstKind
			+ " " + worst + " ms");
		assertTrue("most kinds have a place a member can reach from Lumbridge: " + found, found >= kinds.getKinds().size() - 5);
		assertTrue("slowest " + worstKind + ": " + worst + " ms", worst < 10_000);
	}

	@Test
	public void theNearestAnvilIsInVarrockOrLumbridgeAndTheRouteEndsBesideIt()
	{
		PluginData d = data();
		PlaceKinds.Kind anvil = d.getPlaces().find("Anvil");
		RouteResult r = route(anvil, member());
		assertEquals(RouteStatus.READY, r.getStatus());
		assertEquals("Nearest anvil", r.getRequest().getTitle());
		int place = r.getDestination();
		assertTrue("an anvil place: " + Tiles.x(place) + "," + Tiles.y(place), contains(anvil.getTiles(), place));
		assertTrue("not far from Lumbridge: " + r.getTotalTicks(), r.getTotalTicks() < 150);
	}

	@Test
	public void theNearestBankLeavesOutBanksThePlayerCantUse()
	{
		PluginData d = data();
		PlaceKinds.Kind bank = d.getPlaces().withBanks(d.getBanks()).find("Bank");
		RouteResult free = route(bank, new PlayerState.Builder("p").build());
		assertEquals(RouteStatus.READY, free.getStatus());
		assertTrue(String.join(" | ", free.getNotes()), free.getNotes().stream().anyMatch(n -> n.startsWith("Only the banks you can use now")));
		int place = free.getDestination();
		assertFalse("a free-world bank on free land", d.getFreeLand() != null && !d.getFreeLand().isFree(Tiles.x(place), Tiles.y(place)));
	}

	private static boolean contains(int[] tiles, int t)
	{
		for (int x : tiles)
		{
			if (x == t)
			{
				return true;
			}
		}
		return false;
	}
}
