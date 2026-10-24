package com.bestpath.core.path;

import static com.bestpath.core.path.TestWorld.tile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionMap;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Walks shaped the game's way (GameWalk): same ticks as the brute-force checker's shortest walk, legal, the game's
 * own path piece by piece, split at doors, cut at the furthest tile the game's path reaches with no extra steps.
 */
class GameWalkTest
{
	@Test
	void shapedWalksKeepTheTicksOfTheShortestAndAreTheGamesPaths()
	{
		Random rnd = new Random(20261014);
		int walks = 0;
		int doorWalks = 0;
		int reshaped = 0;
		for (int world = 0; world < 12; world++)
		{
			TestWorld w = TestWorld.random(rnd, world < 3 ? world : 3 + rnd.nextInt(20));
			CollisionMap map = w.map();
			DoorMap doors = w.doorMap();
			GamePath game = new GamePath(map, doors);
			for (int i = 0; i < 30; i++)
			{
				int fx = TestWorld.X0 + rnd.nextInt(TestWorld.SIZE);
				int fy = TestWorld.Y0 + rnd.nextInt(TestWorld.SIZE);
				int tx = TestWorld.X0 + rnd.nextInt(TestWorld.SIZE);
				int ty = TestWorld.Y0 + rnd.nextInt(TestWorld.SIZE);
				int doorCost = new int[]{0, 2, 5}[rnd.nextInt(3)];
				Route best = BruteForce.find(map, new PathQuery(fx, fy, 0, tx, ty, 0, List.of(), 0, doors, doorCost));
				if (!best.isFound() || best.getSteps().isEmpty())
				{
					continue;
				}
				int[] walk = best.getSteps().get(0).getTiles();
				int[] shaped = GameWalk.shape(game, walk);
				String where = "world " + world + " walk " + i + ": " + TestWorld.dirs(walk);
				assertEquals(walk.length, shaped.length, where);
				assertEquals(walk[0], shaped[0], where);
				assertEquals(walk[walk.length - 1], shaped[shaped.length - 1], where);
				assertEquals(best.getTicks(), WalkCost.cost(shaped, doors, doorCost), "same ticks as the shortest " + where);
				boolean door = false;
				for (int k = 1; k < shaped.length; k++)
				{
					int dx = Tiles.x(shaped[k]) - Tiles.x(shaped[k - 1]);
					int dy = Tiles.y(shaped[k]) - Tiles.y(shaped[k - 1]);
					assertTrue(map.canStep(Tiles.x(shaped[k - 1]), Tiles.y(shaped[k - 1]), 0, dx, dy), "legal step " + where);
					// door steps stay exactly where they were
					boolean wasDoor = doors.crosses(walk[k - 1], walk[k]);
					assertEquals(wasDoor, doors.crosses(shaped[k - 1], shaped[k]), "door step " + k + " " + where);
					if (wasDoor)
					{
						assertEquals(walk[k - 1], shaped[k - 1], where);
						assertEquals(walk[k], shaped[k], where);
						door = true;
					}
				}
				// a walk with no door that fits the game's square is exactly the game's path from its start
				int far = 0;
				for (int t : walk)
				{
					far = Math.max(far, Math.max(Math.abs(Tiles.x(t) - fx), Math.abs(Tiles.y(t) - fy)));
				}
				if (!door && far < 60)
				{
					game.search(walk[0], 500);
					assertArrayEquals(game.pathTo(walk[walk.length - 1]), shaped, "the game's path " + where);
				}
				walks++;
				doorWalks += door ? 1 : 0;
				reshaped += Arrays.equals(walk, shaped) ? 0 : 1;
			}
		}
		assertTrue(walks > 200, "walks " + walks);
		assertTrue(doorWalks > 10, "walks through doors " + doorWalks);
		assertTrue(reshaped > walks / 4, "most found walks are not already in the game's shape: " + reshaped);
	}

	@Test
	void doorsSplitTheWalkAndEachPieceIsShapedOnItsOwn()
	{
		TestWorld w = new TestWorld();
		// a long wall at x 3250 with one door at y 3250
		for (int y = 3200; y < 3392; y++)
		{
			if (y != 3250)
			{
				w.wallEast(3250, y);
			}
		}
		w.doorEast(3250, 3250);
		GamePath game = new GamePath(w.map(), w.doorMap());
		// found walk: north-east first, then east to the door; through it; east, then north-east
		int[] walk = walk(3240, 3245, "NE NE NE NE NE E E E E E E E E E E E E NE NE NE");
		assertEquals(tile(3250, 3250), walk[10]);
		assertEquals(tile(3251, 3250), walk[11]);
		int[] shaped = GameWalk.shape(game, walk);
		// before the door: the game's path to the door's tile (east first, then north-east); the door step kept;
		// after it: the game's path from the far side (east first, then north-east)
		assertEquals("5xE 5xNE 1xE 6xE 3xNE", runsWithDoor(shaped, 10));
		assertEquals(tile(3250, 3250), shaped[10]);
		assertEquals(tile(3251, 3250), shaped[11]);
	}

	@Test
	void longWalksAreCutAtTheFurthestTileTheGameReaches()
	{
		TestWorld w = new TestWorld();
		GamePath game = new GamePath(w.map(), DoorMap.none());
		// 20 north-east, then 130 east: longer than the game's square (63 tiles east of the start)
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 150; i++)
		{
			b.append(i < 20 ? "NE " : "E ");
		}
		int[] walk = walk(3210, 3250, b.toString().trim());
		int[] shaped = GameWalk.shape(game, walk);
		// first click: the walk's tile 63 east of the start (as far as the square goes), reached the game's way
		// (east first); then on east from there; one cut where the second part begins
		assertEquals("43xE 20xNE 87xE", TestWorld.runs(shaped));
		assertEquals(walk[63], shaped[63]);
		assertEquals(WalkCost.cost(walk, DoorMap.none(), 0), WalkCost.cost(shaped, DoorMap.none(), 0));
	}

	@Test
	void aStepOffASolidTileIsKept()
	{
		TestWorld w = new TestWorld();
		w.block(3250, 3250); // e.g. a fairy ring's middle: the route steps off it
		GamePath game = new GamePath(w.map(), DoorMap.none());
		int[] walk = walk(3250, 3250, "N NE NE E E E");
		int[] shaped = GameWalk.shape(game, walk);
		assertEquals(walk[0], shaped[0]);
		assertEquals(walk[1], shaped[1]);
		assertEquals("1xN 3xE 2xNE", TestWorld.runs(shaped));
	}

	/** A walk from (x, y) by these steps ("N", "NE", "E"...). */
	static int[] walk(int x, int y, String steps)
	{
		String[] s = steps.split(" ");
		int[] out = new int[s.length + 1];
		out[0] = tile(x, y);
		for (int i = 0; i < s.length; i++)
		{
			x += s[i].contains("E") ? 1 : s[i].contains("W") ? -1 : 0;
			y += s[i].contains("N") ? 1 : s[i].contains("S") ? -1 : 0;
			out[i + 1] = tile(x, y);
		}
		return out;
	}

	/** Runs before the door step at index {@code door}, the door step, runs after it. */
	private static String runsWithDoor(int[] t, int door)
	{
		return TestWorld.runs(Arrays.copyOfRange(t, 0, door + 1)) + " 1x" + TestWorld.dirs(Arrays.copyOfRange(t, door, door + 2))
			+ " " + TestWorld.runs(Arrays.copyOfRange(t, door + 1, t.length));
	}
}
