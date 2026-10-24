package com.bestpath.core.sea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The coarse sea: patches of water per cell, joined only where a boat can step, so no way crosses land. */
class CoarseSeaTest
{
	private static long key(int x, int y)
	{
		return (long) x << 32 | y;
	}

	/** Open water over the box, minus the land tiles. */
	static SeaGrid sea(int x0, int y0, int w, int h, Set<Long> land)
	{
		SeaMap.Builder b = new SeaMap.Builder();
		for (int x = x0; x < x0 + w; x++)
		{
			for (int y = y0; y < y0 + h; y++)
			{
				if (!land.contains(key(x, y)))
				{
					b.set(x, y, 131);
				}
			}
		}
		SeaMap sea = b.build();
		MovementRules.Flags f = (x, y) -> land.contains(key(x, y)) ? CollisionFlags.FLOOR : 0;
		return SeaGridBuilder.build(sea, SeaPieces.build(sea, f), f);
	}

	/** A wall of land at x = wallX from y0 up to (not including) y1. */
	static Set<Long> wall(int wallX, int y0, int y1)
	{
		Set<Long> land = new HashSet<>();
		for (int y = y0; y < y1; y++)
		{
			land.add(key(wallX, y));
		}
		return land;
	}

	@Test
	void waterOnTwoSidesOfLandInOneCellStaysApart()
	{
		// the 16-tile cell at 3200..3215 is split by a wall at x = 3207 its whole height: two patches, joined only
		// through the open cell above it (y 3216..3231), so the way between them goes up and back down
		SeaGrid g = sea(3200, 3200, 16, 32, wall(3207, 3200, 3216));
		CoarseSea c = CoarseSea.build(g);
		int west = c.nodeOf(3203, 3205);
		int east = c.nodeOf(3211, 3205);
		assertTrue(west >= 0 && east >= 0);
		assertNotEquals(west, east);
		double way = c.distancesFrom(west)[east];
		assertTrue(way > 15 && !Double.isInfinite(way), "through the cell above, not across the wall: " + way);
	}

	@Test
	void theWayGoesRoundTheLandNotAcrossIt()
	{
		// 96 x 96 water with a wall at x = 3248 from the bottom up to y = 3280: across it the line is short, by water
		// the boat goes round its top end
		SeaGrid g = sea(3200, 3200, 96, 96, wall(3248, 3200, 3280));
		CoarseSea c = CoarseSea.build(g);
		int a = c.nodeOf(3240, 3210);
		int b = c.nodeOf(3256, 3210);
		double way = c.distancesFrom(a)[b];
		assertTrue(way > 2 * 70, "round the wall's end, about 70 tiles up and back: " + way);
		// open water: about the straight line
		int d = c.nodeOf(3240, 3290);
		int e = c.nodeOf(3256, 3290);
		assertTrue(c.distancesFrom(d)[e] < 30);
	}

	@Test
	void aPointOnLandUsesItsNearestWater()
	{
		Set<Long> land = new HashSet<>();
		for (int x = 3200; x < 3210; x++)
		{
			for (int y = 3200; y < 3232; y++)
			{
				land.add(key(x, y));
			}
		}
		SeaGrid g = sea(3200, 3200, 32, 32, land);
		CoarseSea c = CoarseSea.build(g);
		int[] w = c.nearestWater(3205, 3216);
		assertEquals(3210, w[0]);
		assertEquals(3216, w[1]);
		assertEquals(c.nodeOf(3210, 3216), c.nodeNear(3205, 3216));
		assertEquals(-1, c.nodeNear(3205 - 100, 3216), "no water within reach");
	}
}
