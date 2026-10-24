package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.path.Tiles;
import java.util.List;
import org.junit.Test;

/** The player saves 10 different custom locations and names them. */
public class SavedLocationsTest
{
	private static final int HOME = Tiles.pack(3222, 3218, 0);
	private static final int FARM = Tiles.pack(3052, 3305, 0);

	@Test
	public void namedPlacesInTheOrderSaved()
	{
		SavedLocations s = SavedLocations.NONE.with("Home", HOME).with("Farm", FARM);
		assertEquals(2, s.list().size());
		assertEquals("Home", s.list().get(0).getName());
		assertEquals("3052, 3305, 0", s.list().get(1).tileLabel());
		assertEquals(1, s.indexOf(FARM));
	}

	@Test
	public void savingASavedTileRenamesIt()
	{
		SavedLocations s = SavedLocations.NONE.with("Home", HOME).with("Farm", FARM).with("Lumbridge", HOME);
		assertEquals(2, s.list().size());
		assertEquals("Lumbridge", s.list().get(0).getName());
	}

	@Test
	public void tenAtMost()
	{
		SavedLocations s = SavedLocations.NONE;
		for (int i = 0; i < 12; i++)
		{
			s = s.with("P" + i, Tiles.pack(3200 + i, 3200, 0));
		}
		assertEquals(SavedLocations.MAX, s.list().size());
		assertTrue(s.isFull());
		assertEquals("P9", s.list().get(9).getName());
		assertFalse(s.without(0).isFull());
	}

	@Test
	public void namesAreTidied()
	{
		assertEquals("3222, 3218, 0", SavedLocations.NONE.with("  ", HOME).list().get(0).getName());
		assertEquals("a b", SavedLocations.NONE.with("a\tb\n", HOME).list().get(0).getName());
		String longName = "x".repeat(60);
		assertEquals(SavedLocations.NAME_MAX, SavedLocations.NONE.with(longName, HOME).list().get(0).getName().length());
	}

	@Test
	public void savedAndReadBackAsText()
	{
		SavedLocations s = SavedLocations.NONE.with("Home", HOME).with("My farm, east", Tiles.pack(3052, 3305, 1));
		assertEquals(s, SavedLocations.fromText(s.toText()));
		assertEquals(SavedLocations.NONE, SavedLocations.fromText(null));
		assertEquals(List.of("Home"), List.of(SavedLocations.fromText("3222 3218 0\tHome\nbroken\n1 2\tx\n99999 1 0\tfar\n")
			.list().get(0).getName()));
		assertEquals(1, SavedLocations.fromText("3222 3218 0\tHome\nbroken\n1 2\tx\n99999 1 0\tfar\n").list().size());
	}

	@Test
	public void theRouteIsTitledWithTheName()
	{
		RouteRequest r = SavedLocations.NONE.with("Home", HOME).list().get(0).request();
		assertEquals("Home", r.getTitle());
		assertEquals(HOME, r.getTargets()[0]);
		assertTrue(RecentDestinations.keeps(r));
	}
}
