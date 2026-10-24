package com.bestpath.core.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/** The compact map must answer exactly like the rules it was built from, and survive a save and load. */
class CollisionMapTest
{
	/** Two neighbouring regions: (50,50) and (51,50), world x 3200-3327, y 3200-3263. */
	private static final List<Integer> REGIONS = List.of(50 << 8 | 50, 51 << 8 | 50);

	private static MovementRulesTest.Grid randomWorld(long seed)
	{
		MovementRulesTest.Grid g = new MovementRulesTest.Grid()
		{
			@Override
			public int get(int x, int y)
			{
				// tiles outside the two regions count as blocked, like the extractor's grid
				boolean inside = x >= 3200 && x < 3328 && y >= 3200 && y < 3264;
				return inside ? super.get(x, y) : CollisionFlags.BLOCKED;
			}
		};
		Random rnd = new Random(seed);
		int[] choices = {CollisionFlags.NORTH, CollisionFlags.EAST, CollisionFlags.OBJECT, CollisionFlags.FLOOR,
			CollisionFlags.SOUTH_WEST, CollisionFlags.NORTH_EAST};
		for (int i = 0; i < 3000; i++)
		{
			g.set(3200 + rnd.nextInt(128), 3200 + rnd.nextInt(64), choices[rnd.nextInt(choices.length)]);
		}
		return g;
	}

	private static CollisionMap build(MovementRules.Flags f)
	{
		return CollisionMapBuilder.build(new MovementRules.Flags[]{f, f, f, f}, REGIONS, 2735, 241);
	}

	@Test
	void compactMapMatchesTheRulesEverywhereIncludingRegionBorders()
	{
		MovementRulesTest.Grid g = randomWorld(7);
		CollisionMap map = build(g);
		for (int x = 3199; x <= 3328; x++)
		{
			for (int y = 3199; y <= 3264; y++)
			{
				for (int[] d : MovementRulesTest.DIRS)
				{
					assertEquals(MovementRules.canStep(g, x, y, d[0], d[1]), map.canStep(x, y, 0, d[0], d[1]),
						"tile " + x + "," + y + " step " + d[0] + "," + d[1]);
				}
			}
		}
	}

	@Test
	void unknownAreasAreNotWalkable()
	{
		CollisionMap map = build(randomWorld(1));
		assertFalse(map.canStep(100, 100, 0, 1, 0));
		assertFalse(map.canStep(-5, 3200, 0, 1, 0));
	}

	@Test
	void saveAndLoadRoundTrip() throws IOException
	{
		CollisionMap map = build(randomWorld(3));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CollisionMapWriter.write(map, out);
		CollisionMap back = CollisionMapCodec.read(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(2735, CollisionMapDiagnostics.cacheId(back));
		assertEquals(241, CollisionMapDiagnostics.cacheBuild(back));
		assertEquals(map.size(), back.size());
		for (int x = 3200; x < 3328; x++)
		{
			for (int y = 3200; y < 3264; y++)
			{
				for (int[] d : MovementRulesTest.DIRS)
				{
					assertEquals(map.canStep(x, y, 2, d[0], d[1]), back.canStep(x, y, 2, d[0], d[1]));
				}
			}
		}
	}

	@Test
	void corruptedFileIsRejected() throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CollisionMapWriter.write(build(randomWorld(4)), out);
		byte[] raw;
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(out.toByteArray())))
		{
			raw = in.readAllBytes();
		}
		raw[100] ^= 0x55;
		ByteArrayOutputStream bad = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(bad))
		{
			gz.write(raw);
		}
		assertThrows(IOException.class, () -> CollisionMapCodec.read(new ByteArrayInputStream(bad.toByteArray())));
	}
}
