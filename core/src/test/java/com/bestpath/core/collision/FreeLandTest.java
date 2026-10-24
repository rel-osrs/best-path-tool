package com.bestpath.core.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Free-to-play land: rectangles marked free or members, the text form, and the free-world walking map. */
class FreeLandTest
{
	@Test
	void rectanglesJoinAndCut()
	{
		FreeLand f = new FreeLand.Builder().set(10, 5, 20, 6, true).set(21, 5, 25, 5, true).set(15, 5, 16, 6, false).build();
		assertEquals("5 10-14 17-25\n6 10-14 17-20\n", FreeLandWriter.write(f));
		assertTrue(f.isFree(10, 5));
		assertTrue(f.isFree(25, 5));
		assertFalse(f.isFree(15, 5));
		assertFalse(f.isFree(26, 5));
		assertFalse(f.isFree(9, 6));
		assertFalse(f.isFree(12, 7));
		assertEquals(5 + 9 + 5 + 4, FreeLandDiagnostics.tileCount(f));
		// marking it free again joins the runs
		assertEquals("5 10-25\n6 10-20\n", FreeLandWriter.write(FreeLandBuilder.of(f).set(15, 5, 16, 6, true).build()));
	}

	@Test
	void textRoundTrip()
	{
		FreeLand f = FreeLand.parse(List.of("# free land", "", "3200 3200-3210 3215", "3201 3199-3199"));
		assertEquals("3200 3200-3210 3215-3215\n3201 3199-3199\n", FreeLandWriter.write(f));
		assertEquals(FreeLandWriter.write(f), FreeLandWriter.write(FreeLand.parse(Arrays.asList(FreeLandWriter.write(f).split("\n")))));
		assertTrue(f.isFree(3215, 3200));
		assertFalse(f.isFree(3214, 3200));
	}

	@Test
	void freeWorldMapOnlyStepsBetweenFreeTiles()
	{
		MovementRulesTest.Grid open = new MovementRulesTest.Grid()
		{
			@Override
			public int get(int x, int y)
			{
				boolean inside = x >= 3200 && x < 3264 && y >= 3200 && y < 3264;
				return inside ? super.get(x, y) : CollisionFlags.BLOCKED;
			}
		};
		CollisionMap map = CollisionMapBuilder.build(new MovementRules.Flags[]{open, open, open, open}, List.of(50 << 8 | 50), 0, 0);
		// free: x 3200-3210 on every row of the region
		FreeLand f = new FreeLand.Builder().set(3200, 3200, 3210, 3263, true).build();
		CollisionMap free = map.restrictTo((x, y, p) -> f.isFree(x, y));
		assertTrue(free.canStep(3205, 3205, 0, 1, 0));
		assertTrue(free.canStep(3209, 3205, 0, 1, 1));
		assertFalse(free.canStep(3210, 3205, 0, 1, 0), "into members land");
		assertFalse(free.canStep(3211, 3205, 0, -1, 0), "out of members land");
		assertFalse(free.canStep(3210, 3205, 0, 1, 1), "diagonal into members land");
		assertFalse(free.canStep(3215, 3205, 0, 0, 1));
		assertEquals(map.canStep(3205, 3205, 0, 0, -1), free.canStep(3205, 3205, 0, 0, -1));
		// a map with no free tile keeps no cell
		assertEquals(0, map.restrictTo((x, y, p) -> false).size());
	}
}
