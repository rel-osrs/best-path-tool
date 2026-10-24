package com.bestpath.route;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.path.BankFirst;
import com.bestpath.core.path.Tiles;
import org.junit.Test;

/** The route request for "go to nearest", and the nearest bank's usable banks. */
public class NearestPlacesTest
{
	@Test
	public void theRequestHasEveryPlaceOfTheKind()
	{
		int[] tiles = {Tiles.pack(3210, 3210, 0), Tiles.pack(3250, 3250, 1)};
		RouteRequest r = NearestPlaces.request(new PlaceKinds.Kind("Cooking range", 1488, tiles));
		assertEquals("nearest cooking range", r.getKey());
		assertEquals("Nearest cooking range", r.getTitle());
		assertArrayEquals(tiles, r.getTargets());
		assertEquals(NearestPlaces.BANK_KEY, NearestPlaces.request(new PlaceKinds.Kind("Bank", 1, tiles)).getKey());
	}

	@Test
	public void theRecentDestinationsKeepANearestRoute()
	{
		int[] tiles = {Tiles.pack(3210, 3210, 0), Tiles.pack(3250, 3250, 1)};
		RouteRequest r = NearestPlaces.request(new PlaceKinds.Kind("Altar", 1467, tiles));
		RecentDestinations back = RecentDestinations.fromText(RecentDestinations.NONE.with(r).toText());
		assertEquals(1, back.list().size());
		assertEquals("nearest altar", back.list().get(0).getKey());
		assertEquals("Nearest altar", back.list().get(0).getTitle());
		assertArrayEquals(tiles, back.list().get(0).getTargets());
	}

	@Test
	public void namesWithCapitalsStayAsTheyAre()
	{
		assertEquals("Nearest altar", NearestPlaces.title("Altar"));
		assertEquals("Nearest Makeover Mage", NearestPlaces.title("Makeover Mage"));
		assertEquals("Nearest Death's Domain", NearestPlaces.title("Death's Domain"));
	}

	@Test
	public void theNearestBankKeepsTheBanksThePlayerMayUse()
	{
		int a = Tiles.pack(3205, 3201, 0);
		int b = Tiles.pack(3220, 3220, 0);
		int c = Tiles.pack(3240, 3240, 0);
		int[] all = {a, b, c};
		assertArrayEquals(new int[]{0, 2}, NearestPlaces.usable(all, new BankFirst.Banks(new int[]{a, c}, new String[]{"A", "C"})));
		assertNull("all usable: nothing left out", NearestPlaces.usable(all, new BankFirst.Banks(all, new String[]{"A", "B", "C"})));
		assertNull("none usable: every bank tried", NearestPlaces.usable(all, BankFirst.Banks.NONE));
	}
}
