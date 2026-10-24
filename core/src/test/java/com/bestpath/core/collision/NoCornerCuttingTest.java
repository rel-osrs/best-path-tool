package com.bestpath.core.collision;

import static com.bestpath.core.collision.CollisionFlags.EAST;
import static com.bestpath.core.collision.CollisionFlags.FLOOR;
import static com.bestpath.core.collision.CollisionFlags.FLOOR_DECORATION;
import static com.bestpath.core.collision.CollisionFlags.NORTH;
import static com.bestpath.core.collision.CollisionFlags.NO_FLOOR;
import static com.bestpath.core.collision.CollisionFlags.OBJECT;
import static com.bestpath.core.collision.CollisionFlags.SOUTH;
import static com.bestpath.core.collision.CollisionFlags.WEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * More movement rules: 8 directions, and a diagonal step is invalid if either
 * adjacent orthogonal step is blocked by a wall or object. Every diagonal, every kind of blocked tile, every
 * wall of the L-shaped way round; and the same rules in the compact {@link CollisionMap}.
 */
class NoCornerCuttingTest
{
	private static final int X = 10;
	private static final int Y = 10;
	private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
	private static final int[] BLOCKING = {OBJECT, FLOOR_DECORATION, FLOOR, NO_FLOOR};

	/** A wall between two orthogonally neighbouring tiles, recorded on both like the game does. */
	private static MovementRulesTest.Grid wallBetween(MovementRulesTest.Grid g, int x1, int y1, int x2, int y2)
	{
		if (x2 == x1 + 1)
		{
			return g.wallEastOf(x1, y1);
		}
		if (x2 == x1 - 1)
		{
			return g.wallEastOf(x2, y2);
		}
		if (y2 == y1 + 1)
		{
			return g.wallNorthOf(x1, y1);
		}
		return g.wallNorthOf(x2, y2);
	}

	@Test
	void exactlyEightStepsFromAnOpenTile()
	{
		MovementRulesTest.Grid g = new MovementRulesTest.Grid();
		int n = 0;
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if ((dx != 0 || dy != 0) && MovementRules.canStep(g, X, Y, dx, dy))
				{
					n++;
				}
			}
		}
		assertEquals(8, n);
	}

	@Test
	void everyKindOfBlockedTileBlocksEnteringAndCuttingPast()
	{
		for (int flag : BLOCKING)
		{
			for (int[] d : DIAGONALS)
			{
				// blocked tile beside the start, on the x side of the diagonal
				MovementRulesTest.Grid gx = new MovementRulesTest.Grid().set(X + d[0], Y, flag);
				assertFalse(MovementRules.canStep(gx, X, Y, d[0], 0), "into it: flag " + flag);
				assertFalse(MovementRules.canStep(gx, X, Y, d[0], d[1]), "diagonal past it (x side): flag " + flag);
				assertTrue(MovementRules.canStep(gx, X, Y, -d[0], d[1]), "diagonal away from it: flag " + flag);
				// and on the y side
				MovementRulesTest.Grid gy = new MovementRulesTest.Grid().set(X, Y + d[1], flag);
				assertFalse(MovementRules.canStep(gy, X, Y, d[0], d[1]), "diagonal past it (y side): flag " + flag);
				assertTrue(MovementRules.canStep(gy, X, Y, d[0], -d[1]), "diagonal away from it: flag " + flag);
				// the diagonal tile itself blocked
				MovementRulesTest.Grid gd = new MovementRulesTest.Grid().set(X + d[0], Y + d[1], flag);
				assertFalse(MovementRules.canStep(gd, X, Y, d[0], d[1]), "onto it: flag " + flag);
				assertTrue(MovementRules.canStep(gd, X, Y, d[0], 0), "beside it: flag " + flag);
				assertTrue(MovementRules.canStep(gd, X, Y, 0, d[1]), "beside it: flag " + flag);
			}
		}
	}

	@Test
	void anyWallOfTheWayRoundBlocksTheDiagonal()
	{
		for (int[] d : DIAGONALS)
		{
			int tx = X + d[0];
			int ty = Y + d[1];
			int[][][] walls = {
				{{X, Y}, {tx, Y}},   // start | x neighbour
				{{X, Y}, {X, ty}},   // start | y neighbour
				{{tx, Y}, {tx, ty}}, // x neighbour | destination
				{{X, ty}, {tx, ty}}, // y neighbour | destination
			};
			for (int[][] w : walls)
			{
				MovementRulesTest.Grid g = wallBetween(new MovementRulesTest.Grid(), w[0][0], w[0][1], w[1][0], w[1][1]);
				String where = "diagonal " + d[0] + "," + d[1] + " wall " + List.of(w[0][0], w[0][1], w[1][0], w[1][1]);
				assertFalse(MovementRules.canStep(g, X, Y, d[0], d[1]), where);
				assertFalse(MovementRules.canStep(g, tx, ty, -d[0], -d[1]), "and back: " + where);
			}
		}
	}

	@Test
	void wallsOutsideTheWayRoundDoNotBlockTheDiagonal()
	{
		// walls on the far sides of the destination and on the back sides of the start
		MovementRulesTest.Grid g = new MovementRulesTest.Grid();
		g.wallNorthOf(X + 1, Y + 1).wallEastOf(X + 1, Y + 1); // far sides of the NE destination
		g.wallEastOf(X - 1, Y).wallNorthOf(X, Y - 1); // west and south sides of the start
		assertTrue(MovementRules.canStep(g, X, Y, 1, 1));
		assertFalse(MovementRules.canStep(g, X, Y, -1, -1), "but the SW diagonal crosses them");
		assertFalse(MovementRules.canStep(g, X, Y, -1, 0));
		assertFalse(MovementRules.canStep(g, X, Y, 0, -1));
	}

	@Test
	void aWallFlagOnOnlyOneTileStillBlocks()
	{
		// a wall recorded only on the destination's side (some objects in the game do this)
		MovementRulesTest.Grid g = new MovementRulesTest.Grid().set(X + 1, Y, WEST);
		assertFalse(MovementRules.canStep(g, X, Y, 1, 0));
		assertFalse(MovementRules.canStep(g, X + 1, Y, -1, 0), "symmetric: the way back too");
		MovementRulesTest.Grid h = new MovementRulesTest.Grid().set(X, Y, NORTH);
		assertFalse(MovementRules.canStep(h, X, Y, 0, 1));
		assertFalse(MovementRules.canStep(h, X, Y + 1, 0, -1));
		MovementRulesTest.Grid e = new MovementRulesTest.Grid().set(X, Y, EAST).set(X, Y + 5, SOUTH);
		assertFalse(MovementRules.canStep(e, X + 1, Y, -1, 0));
		assertFalse(MovementRules.canStep(e, X, Y + 4, 0, 1));
	}

	@Test
	void compactMapAppliesTheSameRules()
	{
		// one region (50, 50): x 3200-3263, y 3200-3263; a small room of walls and objects
		MovementRulesTest.Grid g = new MovementRulesTest.Grid()
		{
			@Override
			public int get(int x, int y)
			{
				boolean inside = x >= 3200 && x < 3264 && y >= 3200 && y < 3264;
				return inside ? super.get(x, y) : CollisionFlags.BLOCKED;
			}
		};
		g.set(3210, 3210, OBJECT).set(3212, 3211, FLOOR_DECORATION).set(3214, 3214, FLOOR);
		g.wallNorthOf(3215, 3215).wallEastOf(3216, 3216);
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap map = CollisionMapBuilder.build(new MovementRules.Flags[]{g, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		for (int x = 3205; x <= 3220; x++)
		{
			for (int y = 3205; y <= 3220; y++)
			{
				for (int[] d : MovementRulesTest.DIRS)
				{
					assertEquals(MovementRules.canStep(g, x, y, d[0], d[1]), map.canStep(x, y, 0, d[0], d[1]),
						"step " + x + "," + y + " by " + d[0] + "," + d[1]);
				}
			}
		}
		// the map edge: nothing outside the region
		assertFalse(map.canStep(3263, 3230, 0, 1, 0));
		assertFalse(map.canStep(3230, 3230, 1, 1, 0), "no data on plane 1");
	}
}
