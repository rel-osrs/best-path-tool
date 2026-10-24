package com.bestpath.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import org.junit.Test;

/** The walk inside the house, on the scene's own walls. */
public class HousePathTest
{
	private static final int SIZE = 12;

	/** A 12 x 12 scene: open floor, a 2 x 2 jewellery box at (8..9, 8..9), and a wall line at x = 5 from y 0 to 9. */
	private static int[][] room(boolean doorOpen)
	{
		int[][] f = new int[SIZE][SIZE];
		for (int x = 8; x <= 9; x++)
		{
			for (int y = 8; y <= 9; y++)
			{
				f[x][y] = CollisionFlags.OBJECT;
			}
		}
		for (int y = 0; y <= 9; y++)
		{
			// a wall between x 4 and x 5, with a door gap at y 3 when open
			if (doorOpen && y == 3)
			{
				continue;
			}
			f[4][y] |= CollisionFlags.EAST;
			f[5][y] |= CollisionFlags.WEST;
		}
		return f;
	}

	private static MovementRules.Flags flags(int[][] f)
	{
		return (x, y) -> x < 0 || y < 0 || x >= SIZE || y >= SIZE ? CollisionFlags.BLOCKED : f[x][y];
	}

	private static final int[] BOX = {8, 8, 9, 9};

	@Test
	public void throughTheDoorToTheFurniture()
	{
		int[] w = HousePath.find(flags(room(true)), SIZE, 1, 3, BOX);
		assertTrue(w != null && w.length > 0);
		assertEquals(1 << 16 | 3, w[0]);
		int end = w[w.length - 1];
		assertTrue("ends next to the box: " + (end >>> 16) + "," + (end & 0xFFFF), HousePath.usable(flags(room(true)), end >>> 16, end & 0xFFFF, BOX));
		// it crossed the wall at the door
		boolean door = false;
		for (int i = 1; i < w.length; i++)
		{
			door |= (w[i - 1] >>> 16) == 4 && (w[i] >>> 16) == 5 && (w[i] & 0xFFFF) == 3;
		}
		assertTrue(door);
		// every step one tile
		for (int i = 1; i < w.length; i++)
		{
			assertTrue(Math.abs((w[i] >>> 16) - (w[i - 1] >>> 16)) <= 1 && Math.abs((w[i] & 0xFFFF) - (w[i - 1] & 0xFFFF)) <= 1);
		}
	}

	@Test
	public void aClosedDoorOrAlreadyThere()
	{
		// the door shut and the wall runs to y 9; round the top (y 10, 11) is still open
		int[] round = HousePath.find(flags(room(false)), SIZE, 1, 3, BOX);
		assertTrue(round != null);
		boolean high = false;
		for (int t : round)
		{
			high |= (t & 0xFFFF) >= 10;
		}
		assertTrue("round the wall's end", high);
		// standing next to the box: nothing to walk
		assertArrayEquals(new int[0], HousePath.find(flags(room(true)), SIZE, 7, 8, BOX));
		// boxed in completely: no walk
		int[][] shut = room(false);
		for (int x = 0; x < SIZE; x++)
		{
			shut[x][10] = CollisionFlags.OBJECT;
		}
		assertNull(HousePath.find(flags(shut), SIZE, 1, 3, BOX));
	}

	@Test
	public void theSameTilesAsTheGamesOwnWalk()
	{
		// the walk is the game's, so it looks like player movement without jaggies
		// (core GamePath, the rule checked against the game) on the same walls
		int[][] f = room(true);
		com.bestpath.core.collision.MovementRules.Flags world = (x, y) ->
		{
			int sx = x - 3200;
			int sy = y - 3200;
			return sx < 0 || sy < 0 || sx >= SIZE || sy >= SIZE ? CollisionFlags.BLOCKED : f[sx][sy];
		};
		com.bestpath.core.collision.MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		com.bestpath.core.collision.CollisionMap map = com.bestpath.core.collision.CollisionMapBuilder.build(
			new com.bestpath.core.collision.MovementRules.Flags[]{world, none, none, none}, java.util.List.of(50 << 8 | 50), 0, 0);
		com.bestpath.core.path.GamePath game = new com.bestpath.core.path.GamePath(map, com.bestpath.core.path.DoorMap.none());
		for (int[] start : new int[][]{{1, 3}, {0, 0}, {1, 10}, {11, 1}, {6, 2}})
		{
			int[] w = HousePath.find(flags(f), SIZE, start[0], start[1], BOX);
			int end = w[w.length - 1];
			game.search(com.bestpath.core.path.Tiles.pack(3200 + start[0], 3200 + start[1], 0), 200);
			int[] g = game.pathTo(com.bestpath.core.path.Tiles.pack(3200 + (end >>> 16), 3200 + (end & 0xFFFF), 0));
			assertEquals(start[0] + "," + start[1], g.length, w.length);
			for (int i = 0; i < w.length; i++)
			{
				assertEquals(start[0] + "," + start[1] + " step " + i, g[i],
					com.bestpath.core.path.Tiles.pack(3200 + (w[i] >>> 16), 3200 + (w[i] & 0xFFFF), 0));
			}
		}
	}

	@Test
	public void aMountedItemOnAWallIsUsedFromItsOwnTile()
	{
		// a mounted glory: the object's tile itself is free floor
		int[] glory = {2, 2, 2, 2};
		int[] w = HousePath.find(flags(new int[SIZE][SIZE]), SIZE, 2, 6, glory);
		assertEquals(2 << 16 | 3, w[w.length - 1]);
		// a wall between the player's side and the item: not from the other side
		int[][] f = new int[SIZE][SIZE];
		f[2][3] |= CollisionFlags.SOUTH;
		f[2][2] |= CollisionFlags.NORTH;
		assertTrue(!HousePath.usable(flags(f), 2, 3, glory));
	}
}
