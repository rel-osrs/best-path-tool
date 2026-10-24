package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.data.BankList;
import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.RecentDestinations;
import com.bestpath.route.RouteRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** The Functions tab's "Nearest": a drop-down of place kinds and Go. */
public class NearestSectionTest
{
	private final List<RouteRequest> routes = new ArrayList<>();
	private int routeTabShown;

	private static final PlaceKinds KINDS = PlaceKinds.parse(PlaceKinds.HEADER + "\n"
		+ "Altar\t1467\t3210,3210,0 3250,3250,0\n"
		+ "Bank\t1453\t\n"
		+ "Furnace\t1457\t3220,3201,1\n").withBanks(BankList.parse(BankList.HEADER + "\nA bank\tbooth\t\t3205,3201,0\n"));

	private FunctionsTab tab()
	{
		return new FunctionsTab(new PanelActions()
		{
			@Override
			public void findRoute(RouteRequest request)
			{
				routes.add(request);
			}

			@Override
			public void clearRoute()
			{
			}

			@Override
			public void useClue()
			{
			}

			@Override
			public void skipForThisRoute(String transportId)
			{
			}

			@Override
			public void clearSkips()
			{
			}

			@Override
			public void settingsChanged(com.bestpath.route.TransportSettings settings)
			{
			}
		}, () -> routeTabShown++, new IconCache(IconProvider.NONE));
	}

	@Test
	public void nothingToPickWhileTheMapDataLoads()
	{
		NearestSection n = tab().nearest;
		assertFalse(n.kinds.isEnabled());
		assertFalse(n.go.isEnabled());
		assertEquals(NearestSection.LOADING, n.text.getText());
	}

	@Test
	public void noPlacesInTheData()
	{
		FunctionsTab t = tab();
		t.showPlaces(PlaceKinds.NONE, true);
		assertFalse(t.nearest.go.isEnabled());
		assertEquals(NearestSection.NO_PLACES, t.nearest.text.getText());
	}

	@Test
	public void everyKindIsOfferedTheBankFirstPicked()
	{
		FunctionsTab t = tab();
		t.showPlaces(KINDS, true);
		NearestSection n = t.nearest;
		assertEquals(3, n.kinds.getItemCount());
		assertEquals("Altar", n.kinds.getItemAt(0).getName());
		assertEquals("Bank", ((PlaceKinds.Kind) n.kinds.getSelectedItem()).getName());
		assertTrue(n.go.isEnabled());
		assertEquals(NearestSection.HINT, n.text.getText());
	}

	@Test
	public void goRoutesToEveryPlaceOfTheKindAndShowsTheRoute()
	{
		FunctionsTab t = tab();
		t.showPlaces(KINDS, true);
		NearestSection n = t.nearest;
		n.kinds.setSelectedIndex(0);
		n.go.doClick();
		assertEquals(1, routes.size());
		RouteRequest r = routes.get(0);
		assertEquals("nearest altar", r.getKey());
		assertEquals("Nearest altar", r.getTitle());
		assertEquals(2, r.targetCount());
		assertEquals(Tiles.pack(3250, 3250, 0), r.getTargets()[1]);
		assertEquals(1, routeTabShown);
		assertTrue("kept in the recent destinations", RecentDestinations.keeps(r));
		assertEquals("Nearest altar", FunctionsTab.label(r));
	}

	@Test
	public void theKindPickedStaysPickedWhenTheDataComesAgain()
	{
		FunctionsTab t = tab();
		t.showPlaces(KINDS, true);
		NearestSection n = t.nearest;
		n.kinds.setSelectedIndex(2);
		t.showPlaces(KINDS, true);
		assertEquals("Furnace", ((PlaceKinds.Kind) n.kinds.getSelectedItem()).getName());
	}
}
