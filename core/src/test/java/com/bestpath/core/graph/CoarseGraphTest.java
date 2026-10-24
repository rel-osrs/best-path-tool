package com.bestpath.core.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Components and entrances on a tiny world: two cells side by side, regions
 * (50,50) and (51,50), world x 3200-3327, y 3200-3263, plane 0.
 */
class CoarseGraphTest
{
	private static final int A = 50 << 8 | 50;
	private static final int B = 51 << 8 | 50;

	/** Open floor except for flags set explicitly; outside the two cells is blocked. */
	private static final class World implements MovementRules.Flags
	{
		private final Map<Long, Integer> flags = new HashMap<>();

		void set(int x, int y, int f)
		{
			flags.merge((long) x << 32 | y, f, (p, q) -> p | q);
		}

		/** A full-height wall line on the east side of column x. */
		void wallEastOfColumn(int x)
		{
			for (int y = 3200; y < 3264; y++)
			{
				set(x, y, CollisionFlags.EAST);
				set(x + 1, y, CollisionFlags.WEST);
			}
		}

		@Override
		public int get(int x, int y)
		{
			boolean inside = x >= 3200 && x < 3328 && y >= 3200 && y < 3264;
			return inside ? flags.getOrDefault((long) x << 32 | y, 0) : CollisionFlags.BLOCKED;
		}
	}

	private static CoarseGraph build(World w)
	{
		CollisionMap map = CollisionMapBuilder.build(new MovementRules.Flags[]{w, w, w, w}, List.of(A, B), 0, 0);
		return CoarseGraph.build(map);
	}

	@Test
	void openGroundIsOneComponentPerCellWithOneEntranceBetween()
	{
		CoarseGraph g = build(new World());
		// 2 cells x 4 planes, each fully open
		assertEquals(8, g.componentCount());
		int a = g.componentAt(3210, 3210, 0);
		int b = g.componentAt(3300, 3210, 0);
		assertNotEquals(a, b);
		assertEquals(4096, g.componentSize(a));
		// the whole shared border is one run: one entrance, one link per plane
		assertEquals(4, g.entranceCount());
		assertEquals(4, CoarseGraphDiagnostics.linkCount(g));
		int[] t = g.entranceTiles(0);
		assertEquals(3263, t[0], "entrance runs along the last column of the west cell");
		assertEquals(3200, Math.min(t[1], t[3]));
		assertEquals(3263, Math.max(t[1], t[3]));
	}

	@Test
	void wallSplitsACellIntoTwoComponents()
	{
		World w = new World();
		w.wallEastOfColumn(3230); // inside cell A
		CoarseGraph g = build(w);
		int west = g.componentAt(3210, 3210, 0);
		int east = g.componentAt(3250, 3210, 0);
		assertNotEquals(west, east);
		assertEquals(31 * 64, g.componentSize(west));
		assertEquals(33 * 64, g.componentSize(east));
		// only the east part of A touches cell B
		int[] perComp = g.entrancesPerComponent();
		assertEquals(0, perComp[west], "the west part has no way out of the cell");
		assertEquals(1, perComp[east]);
	}

	@Test
	void gapsInTheBorderMakeSeparateEntrances()
	{
		World w = new World();
		// wall along the shared border except two openings: y 3210-3212 and y 3240
		w.wallEastOfColumn(3263);
		for (int y : new int[]{3210, 3211, 3212, 3240})
		{
			w.flags.put((long) 3263 << 32 | y, 0);
			w.flags.put((long) 3264 << 32 | y, 0);
		}
		CoarseGraph g = build(w);
		int a = g.componentAt(3210, 3210, 0);
		int b = g.componentAt(3300, 3210, 0);
		int entrancesPlane0 = 0;
		for (int e = 0; e < g.entranceCount(); e++)
		{
			if (g.entranceTiles(e)[4] == 0)
			{
				entrancesPlane0++;
				assertEquals(a, g.entranceFrom(e));
				assertEquals(b, g.entranceTo(e));
			}
		}
		assertEquals(2, entrancesPlane0, "two separate openings");
	}

	@Test
	void smallClosedOffAreaIsAPocketButOpenGroundIsNot()
	{
		World w = new World();
		// a 2x2 area at (3220-3221, 3220-3221) boxed in by solid objects
		for (int x = 3219; x <= 3222; x++)
		{
			for (int y = 3219; y <= 3222; y++)
			{
				boolean inside = x >= 3220 && x <= 3221 && y >= 3220 && y <= 3221;
				if (!inside)
				{
					w.set(x, y, CollisionFlags.OBJECT);
				}
			}
		}
		CoarseGraph g = build(w);
		boolean[] pockets = g.pockets();
		int pocket = g.componentAt(3220, 3220, 0);
		assertEquals(4, g.componentSize(pocket));
		assertTrue(pockets[pocket]);
		assertTrue(!pockets[g.componentAt(3210, 3210, 0)], "open ground with entrances is not a pocket");
	}

	@Test
	void blockedTilesHaveNoComponent()
	{
		World w = new World();
		w.set(3220, 3220, CollisionFlags.OBJECT);
		w.set(3200, 3230, CollisionFlags.OBJECT); // on the west edge of the world
		CoarseGraph g = build(w);
		assertEquals(-1, g.componentAt(3220, 3220, 0));
		assertEquals(-1, g.componentAt(3200, 3230, 0));
		assertTrue(g.componentAt(3221, 3220, 0) >= 0);
		assertEquals(4096 - 2, g.componentSize(g.componentAt(3221, 3220, 0)));
	}

	@Test
	void packedLabelsSeparateManySmallRooms()
	{
		// walls every 3 columns and every 3 rows of cell A: 21 x 21 + edge rooms = several hundred components,
		// so the packed labels use 9 bits per tile and many tiles straddle two longs
		World w = new World();
		for (int x = 3202; x < 3263; x += 3)
		{
			w.wallEastOfColumn(x);
		}
		for (int y = 3202; y < 3263; y += 3)
		{
			for (int x = 3200; x < 3264; x++)
			{
				w.set(x, y, CollisionFlags.NORTH);
				w.set(x, y + 1, CollisionFlags.SOUTH);
			}
		}
		CoarseGraph g = build(w);
		short[] labels = g.cellLabels(A, 0);
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (int lx = 0; lx < 64; lx++)
		{
			for (int ly = 0; ly < 64; ly++)
			{
				int c = g.componentAt(3200 + lx, 3200 + ly, 0);
				assertTrue(c >= 0, "every tile has a step");
				assertEquals(labels[lx << 6 | ly], c - g.cellComponentBase(A, 0) + 1, "cellLabels and componentAt agree");
				// the next room east is a different component
				int other = g.componentAt(3200 + lx + (lx % 3 == 2 && lx < 63 ? 1 : 0), 3200 + ly, 0);
				if (lx % 3 == 2 && lx < 63)
				{
					assertNotEquals(c, other, "a wall separates rooms");
				}
				seen.add(c);
			}
		}
		assertEquals(22 * 22, seen.size(), "one component per room");
		assertTrue(CoarseGraphDiagnostics.labelBytes(g) > 0);
	}
}
