package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The Wilderness zones file (only where the 20 and 30 lines are): built from a zone per tile, read back
 * the same.
 */
class WildernessZonesTest
{
	/** A small Wilderness: rows 100-139 of x 10-29, zone by level (y - 100) + 1, with a safe hole at x 20-21, y 104-105. */
	private static WildernessZones small()
	{
		WildernessZonesBuilder b = new WildernessZonesBuilder(0, 90, 40, 60);
		for (int y = 100; y < 140; y++)
		{
			for (int x = 10; x < 30; x++)
			{
				boolean safe = x >= 20 && x <= 21 && y >= 104 && y <= 105;
				b.set(x, y, safe ? 0 : WildernessZones.zoneOfLevel(y - 100 + 1));
			}
		}
		return b.build();
	}

	@Test
	void zonesComeBackPerTile()
	{
		WildernessZones w = small();
		assertEquals(0, w.zone(10, 99));
		assertEquals(WildernessZones.TO_20, w.zone(10, 100), "level 1");
		assertEquals(WildernessZones.TO_20, w.zone(29, 119), "level 20");
		assertEquals(WildernessZones.TO_30, w.zone(29, 120), "level 21");
		assertEquals(WildernessZones.TO_30, w.zone(15, 129), "level 30");
		assertEquals(WildernessZones.DEEP, w.zone(15, 130), "level 31");
		assertEquals(0, w.zone(15, 140));
		assertEquals(0, w.zone(9, 105));
		assertEquals(0, w.zone(30, 105));
		assertEquals(0, w.zone(20, 104), "the safe hole");
		assertEquals(WildernessZones.TO_20, w.zone(22, 104));
		assertTrue(w.isWilderness(12, 112));
		assertFalse(w.isWilderness(3222, 3218));
		// rows with the same runs are one rectangle: 100-103, 104-105 (two runs, either side of the hole), 106-119,
		// 120-129, 130-139
		assertEquals(1 + 2 + 1 + 1 + 1, WildernessZonesDiagnostics.rectangleCount(w));
	}

	@Test
	void teleportLimitsAsZones()
	{
		assertEquals(0, WildernessZones.zoneOfLevel(0));
		assertEquals(WildernessZones.TO_20, WildernessZones.zoneOfLevel(20));
		assertEquals(WildernessZones.TO_30, WildernessZones.zoneOfLevel(21));
		assertEquals(WildernessZones.DEEP, WildernessZones.zoneOfLevel(31));
		assertEquals(0, WildernessZones.deepestZone(0), "a teleport that does not work in the Wilderness");
		assertEquals(WildernessZones.TO_20, WildernessZones.deepestZone(20));
		assertEquals(WildernessZones.TO_30, WildernessZones.deepestZone(30));
		assertEquals(WildernessZones.DEEP, WildernessZones.deepestZone(null), "no limit");
	}

	@Test
	void theTextReadsBackTheSame()
	{
		WildernessZones w = small();
		WildernessZones back = WildernessZones.parse(Arrays.asList(WildernessZonesWriter.write(w, "test").split("\n")));
		assertEquals(WildernessZonesWriter.write(w, "x"), WildernessZonesWriter.write(back, "x"));
		for (int y = 95; y < 145; y++)
		{
			for (int x = 5; x < 35; x++)
			{
				assertEquals(w.zone(x, y), back.zone(x, y), x + "," + y);
			}
		}
		assertTrue(WildernessZones.NONE.isEmpty());
		assertEquals(0, WildernessZones.NONE.zone(3100, 3600));
		assertThrows(IllegalArgumentException.class, () -> WildernessZones.parse(Arrays.asList("1\t2\t3")));
		assertThrows(IllegalArgumentException.class, () -> WildernessZones.parse(Arrays.asList("1\t2\t3\t4\t0")));
		assertThrows(IllegalArgumentException.class, () -> WildernessZones.parse(Arrays.asList("1\t2\t3\t4\t4")));
	}
}
