package com.bestpath.core.collision;

import static com.bestpath.core.collision.CollisionFlags.EAST;
import static com.bestpath.core.collision.CollisionFlags.FLOOR;
import static com.bestpath.core.collision.CollisionFlags.NORTH;
import static com.bestpath.core.collision.CollisionFlags.NORTH_EAST;
import static com.bestpath.core.collision.CollisionFlags.OBJECT;
import static com.bestpath.core.collision.CollisionFlags.SOUTH;
import static com.bestpath.core.collision.CollisionFlags.SOUTH_WEST;
import static com.bestpath.core.collision.CollisionFlags.WEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Movement rules: orthogonal, diagonal, walls, corners, no corner cutting. */
class MovementRulesTest
{
	/** A small test world: flags per tile, everything else open floor. */
	static class Grid implements MovementRules.Flags
	{
		final Map<Long, Integer> flags = new HashMap<>();

		Grid set(int x, int y, int f)
		{
			flags.merge(((long) x << 32) | (y & 0xFFFFFFFFL), f, (a, b) -> a | b);
			return this;
		}

		/** A wall between (x, y) and the tile north of it, recorded on both tiles like the game does. */
		Grid wallNorthOf(int x, int y)
		{
			return set(x, y, NORTH).set(x, y + 1, SOUTH);
		}

		Grid wallEastOf(int x, int y)
		{
			return set(x, y, EAST).set(x + 1, y, WEST);
		}

		@Override
		public int get(int x, int y)
		{
			return flags.getOrDefault(((long) x << 32) | (y & 0xFFFFFFFFL), 0);
		}
	}

	static final int[][] DIRS = {{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};

	@Test
	void openGroundAllowsAllEightDirections()
	{
		Grid g = new Grid();
		for (int[] d : DIRS)
		{
			assertTrue(MovementRules.canStep(g, 10, 10, d[0], d[1]), "direction " + d[0] + "," + d[1]);
		}
	}

	@Test
	void standingStillIsAlwaysAllowed()
	{
		assertTrue(MovementRules.canStep(new Grid().set(5, 5, OBJECT), 5, 5, 0, 0));
	}

	@Test
	void wallBlocksStepAcrossItButNotAlongIt()
	{
		Grid g = new Grid().wallNorthOf(10, 10);
		assertFalse(MovementRules.canStep(g, 10, 10, 0, 1), "north through the wall");
		assertFalse(MovementRules.canStep(g, 10, 11, 0, -1), "south through the wall");
		assertTrue(MovementRules.canStep(g, 10, 10, 1, 0), "east along the wall");
		assertTrue(MovementRules.canStep(g, 10, 10, -1, 0), "west along the wall");
	}

	@Test
	void eastWestWallsWorkTheSameWay()
	{
		Grid g = new Grid().wallEastOf(10, 10);
		assertFalse(MovementRules.canStep(g, 10, 10, 1, 0));
		assertFalse(MovementRules.canStep(g, 11, 10, -1, 0));
		assertTrue(MovementRules.canStep(g, 10, 10, 0, 1));
	}

	@Test
	void diagonalCannotCutPastAWallEnd()
	{
		// wall on the north side of (10,10) only
		Grid g = new Grid().wallNorthOf(10, 10);
		assertFalse(MovementRules.canStep(g, 10, 10, 1, 1), "NE from under the wall");
		assertFalse(MovementRules.canStep(g, 10, 10, -1, 1), "NW from under the wall");
		assertFalse(MovementRules.canStep(g, 11, 10, -1, 1), "NW from the east side crosses the wall end");
		assertTrue(MovementRules.canStep(g, 11, 10, 1, 1), "NE away from the wall is fine");
	}

	@Test
	void diagonalCannotCutPastABlockedTile()
	{
		Grid g = new Grid().set(11, 10, OBJECT);
		assertFalse(MovementRules.canStep(g, 10, 10, 1, 0), "into the object");
		assertFalse(MovementRules.canStep(g, 10, 10, 1, 1), "NE cuts the object's corner");
		assertFalse(MovementRules.canStep(g, 10, 10, 1, -1), "SE cuts the object's corner");
		assertTrue(MovementRules.canStep(g, 10, 10, 0, 1), "north is fine");
	}

	@Test
	void cornerPillarBlocksOnlyTheDiagonalThroughIt()
	{
		// a pillar at the shared corner of (10,10) and (11,11)
		Grid g = new Grid().set(11, 11, SOUTH_WEST).set(10, 10, NORTH_EAST);
		assertFalse(MovementRules.canStep(g, 10, 10, 1, 1));
		assertFalse(MovementRules.canStep(g, 11, 11, -1, -1));
		assertTrue(MovementRules.canStep(g, 10, 10, 1, 0));
		assertTrue(MovementRules.canStep(g, 10, 10, 0, 1));
	}

	@Test
	void blockedFloorCannotBeEnteredOrLeft()
	{
		Grid g = new Grid().set(10, 11, FLOOR);
		assertFalse(MovementRules.canStep(g, 10, 10, 0, 1));
		assertFalse(MovementRules.canStep(g, 10, 11, 0, -1), "symmetric model: no stepping off a blocked tile");
	}

	@Test
	void stepsAreSymmetric()
	{
		Random rnd = new Random(42);
		Grid g = new Grid();
		int[] choices = {0, NORTH, EAST, SOUTH, WEST, OBJECT, SOUTH_WEST, NORTH_EAST, FLOOR};
		for (int i = 0; i < 400; i++)
		{
			g.set(rnd.nextInt(20), rnd.nextInt(20), choices[rnd.nextInt(choices.length)]);
		}
		for (int x = 0; x < 20; x++)
		{
			for (int y = 0; y < 20; y++)
			{
				for (int[] d : DIRS)
				{
					assertEquals(MovementRules.canStep(g, x, y, d[0], d[1]),
						MovementRules.canStep(g, x + d[0], y + d[1], -d[0], -d[1]),
						"step " + x + "," + y + " by " + d[0] + "," + d[1]);
				}
			}
		}
	}
}
