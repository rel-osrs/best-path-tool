package com.bestpath.core.sea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeaMapTest
{
	@Test
	void answersWhatWasSet()
	{
		SeaMap sea = new SeaMap.Builder().set(3048, 3186, 131).set(3049, 3186, 208).set(3200, 3200, 0).build();
		assertEquals(131, sea.texture(3048, 3186, 0));
		assertEquals(208, sea.texture(3049, 3186, 0));
		assertEquals(0, sea.texture(3200, 3200, 0));
		assertTrue(sea.isSea(3048, 3186, 0));
		assertEquals(3, sea.tileCount());
		assertEquals(2, sea.regionCount());
		assertTrue(sea.hasSea((3048 >> 6) << 8 | (3186 >> 6)));
	}

	@Test
	void notSeaElsewhere()
	{
		SeaMap sea = new SeaMap.Builder().set(3048, 3186, 131).build();
		assertEquals(-1, sea.texture(3047, 3186, 0)); // same region, not set
		assertEquals(-1, sea.texture(3048, 3186, 1)); // sea is on plane 0 only
		assertEquals(-1, sea.texture(1000, 1000, 0)); // region without sea
		assertEquals(-1, sea.texture(-1, 5, 0));
		assertFalse(sea.hasSea(0));
		assertEquals(0, SeaMap.empty().tileCount());
	}

	@Test
	void uniformRegionsAnswerTheSame()
	{
		SeaMap.Builder b = new SeaMap.Builder();
		for (int x = 0; x < 64; x++)
		{
			for (int y = 0; y < 64; y++)
			{
				b.set(3200 + x, 3200 + y, 133);
				b.set(3264 + x, 3200 + y, 133);
			}
		}
		SeaMap sea = b.build();
		assertEquals(133, sea.texture(3200, 3263, 0));
		assertEquals(133, sea.texture(3327, 3200, 0));
		assertEquals(2 * 4096, sea.tileCount());
	}

	@Test
	void visitsEverySeaTileOnce()
	{
		SeaMap sea = new SeaMap.Builder().set(3048, 3186, 131).set(3049, 3186, 208).set(3200, 3200, 0).build();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		sea.forEachTile((x, y) -> assertTrue(seen.add((long) x << 32 | y)));
		assertEquals(3, seen.size());
		assertTrue(seen.contains(3049L << 32 | 3186));
	}

	@Test
	void rejectsTexturesThatDoNotFit()
	{
		assertThrows(IllegalArgumentException.class, () -> new SeaMap.Builder().set(0, 0, 255));
		assertThrows(IllegalArgumentException.class, () -> new SeaMap.Builder().set(0, 0, -1));
	}
}
