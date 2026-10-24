package com.bestpath.core.sea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoatTest
{
	private static Boat boat(BoatType type, int hull, int sail, int keel, int helm, boolean inoculation, boolean brazier)
	{
		return new Boat(1, type, hull, sail, keel, helm, inoculation, brazier, Boat.Focus.NONE, 0);
	}

	@Test
	void speedComesFromTheHull()
	{
		double[] expected = {1.5, 1.5, 2.0, 2.0, 2.5, 2.5, 3.0};
		for (int hull = 0; hull < 7; hull++)
		{
			for (BoatType t : BoatType.values())
			{
				assertEquals(expected[hull], boat(t, hull, 0, 0, 0, false, false).speed(), t + " hull " + hull);
			}
		}
	}

	@Test
	void restrictedAreasNeedTheirParts()
	{
		Boat basic = boat(BoatType.SKIFF, 0, 0, 0, 0, false, false);
		assertTrue(basic.canEnter(SeaArea.OPEN));
		assertTrue(basic.canEnter(SeaArea.WORLD_EDGE));
		for (SeaArea a : new SeaArea[]{SeaArea.TEMPOR_TANTRUM, SeaArea.NORTHERN, SeaArea.CRYSTALLINE, SeaArea.FETID, SeaArea.KELP})
		{
			assertFalse(basic.canEnter(a), a.name());
		}
		assertTrue(boat(BoatType.SKIFF, 0, 1, 0, 0, false, false).canEnter(SeaArea.TEMPOR_TANTRUM)); // oak sails
		assertFalse(boat(BoatType.SKIFF, 0, 0, 3, 3, false, false).canEnter(SeaArea.CRYSTALLINE)); // mithril keel
		assertTrue(boat(BoatType.SKIFF, 0, 0, 4, 0, false, false).canEnter(SeaArea.CRYSTALLINE)); // adamant keel
		assertTrue(boat(BoatType.SKIFF, 0, 0, 0, 4, false, false).canEnter(SeaArea.KELP)); // adamant helm
		assertTrue(boat(BoatType.SKIFF, 0, 0, 0, 0, true, false).canEnter(SeaArea.FETID));
		assertTrue(boat(BoatType.SKIFF, 0, 0, 0, 0, false, true).canEnter(SeaArea.NORTHERN));
		// a raft has no keel, whatever is asked for
		Boat raft = boat(BoatType.RAFT, 0, 0, 6, 0, false, false);
		assertEquals(-1, raft.getKeel());
		assertFalse(raft.canEnter(SeaArea.CRYSTALLINE));
		int mask = boat(BoatType.SLOOP, 0, 1, 0, 0, true, false).areaMask();
		assertTrue((mask & 1 << SeaArea.TEMPOR_TANTRUM.ordinal()) != 0);
		assertTrue((mask & 1 << SeaArea.FETID.ordinal()) != 0);
		assertFalse((mask & 1 << SeaArea.KELP.ordinal()) != 0);
	}

	@Test
	void placesThatAreNoDock()
	{
		for (int place : new int[]{Boat.CAPSIZED, Boat.LOST, Boat.BOTTLED, Boat.AT_START, Boat.NOWHERE, 0, 23})
		{
			Boat b = new Boat(1, BoatType.SLOOP, 0, 0, 0, 0, false, false, Boat.Focus.NONE, place);
			assertEquals(place == Boat.CAPSIZED || place == Boat.LOST, b.needsShipwright(), "place " + place);
			assertEquals(place == Boat.BOTTLED, b.isBottled(), "place " + place);
			assertEquals(place == Boat.CAPSIZED ? "capsized" : place == Boat.LOST ? "lost" : place == Boat.BOTTLED ? "bottled" : null,
				b.placeWord());
		}
		// the full boat bottle (cache #2735 item 31992)
		assertEquals(31992, Boat.BOTTLE_FULL);
	}

	@Test
	void clearanceByWidth()
	{
		assertEquals(1, BoatType.RAFT.hardClearance());
		assertEquals(2, BoatType.SKIFF.hardClearance());
		assertEquals(2, BoatType.SLOOP.hardClearance());
		assertEquals(2, BoatType.RAFT.preferredClearance());
		assertEquals(3, BoatType.SKIFF.preferredClearance());
		assertEquals(4, BoatType.SLOOP.preferredClearance());
		assertTrue(BoatType.SLOOP.preferredClearance() <= SeaGrid.MAX_CLEARANCE);
	}

	@Test
	void partTiersAreChecked()
	{
		assertThrows(IllegalArgumentException.class, () -> boat(BoatType.SKIFF, 7, 0, 0, 0, false, false));
	}
}
