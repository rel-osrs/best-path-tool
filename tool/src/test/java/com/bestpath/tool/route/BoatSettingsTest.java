package com.bestpath.tool.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import org.junit.jupiter.api.Test;

class BoatSettingsTest
{
	@Test
	void boatsSurviveTheSettingsFile()
	{
		Boat b = new Boat(3, BoatType.SLOOP, 6, 4, 5, 4, true, true, Boat.Focus.GREATER, 23);
		String line = BoatSettings.encode(b);
		assertEquals("SLOOP,6,4,5,4,1,1,GREATER,23", line);
		Boat back = BoatSettings.decode(3, line);
		assertEquals(line, BoatSettings.encode(back));
		assertEquals(3, back.getSlot());
		assertEquals(Boat.AT_START, BoatSettings.decode(1, "RAFT,0,0,0,0,0,0,NONE,-2").getDockId());
		assertNull(BoatSettings.decode(1, ""));
		assertNull(BoatSettings.decode(1, "BARGE,1"));
		// capsized, lost and bottled boats survive too
		for (int place : new int[]{Boat.CAPSIZED, Boat.LOST, Boat.BOTTLED})
		{
			Boat p = new Boat(2, BoatType.SKIFF, 1, 1, 1, 1, false, false, Boat.Focus.NONE, place);
			assertEquals(place, BoatSettings.decode(2, BoatSettings.encode(p)).getDockId());
		}
	}

	@Test
	void theRouteCheckPlayerCarriesTheBottles()
	{
		// the Boats dialog's full boat bottles are added to the test player's own
		com.bestpath.tool.req.TestPlayer maxed = com.bestpath.tool.req.TestPlayer.maxed();
		assertEquals(0, RouteController.withBottles(maxed, 0).getInventory().getOrDefault(Boat.BOTTLE_FULL, 0));
		assertEquals(2, RouteController.withBottles(maxed, 2).getInventory().getOrDefault(Boat.BOTTLE_FULL, 0));
		com.bestpath.tool.req.TestPlayer one = maxed.copy();
		one.inventory.put(Integer.toString(Boat.BOTTLE_FULL), 1);
		assertEquals(3, RouteController.withBottles(one, 2).getInventory().getOrDefault(Boat.BOTTLE_FULL, 0));
		assertEquals(1, one.inventory.get(Integer.toString(Boat.BOTTLE_FULL)).intValue(), "the test player itself is unchanged");
	}
}
