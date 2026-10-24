package com.bestpath.core.path;

import static com.bestpath.core.path.TestWorld.tile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The game's click pathing (GamePath) against the verified rule: neighbour order
 * W, E, S, N, SW, SE, NW, NE; the 128 x 128 square; closed doors; no corner cutting; the 25-corner stop.
 */
class GamePathTest
{
	private static int[] path(TestWorld w, int fx, int fy, int tx, int ty)
	{
		GamePath g = new GamePath(w.map(), w.doorMap());
		g.search(tile(fx, fy), 200);
		return g.pathTo(tile(tx, ty));
	}

	@Test
	void openGroundIsStraightFirstThenDiagonal()
	{
		TestWorld w = new TestWorld();
		// the verified example: 10 east and 3 north = 7 east, then 3 north-east
		assertEquals("7xE 3xNE", TestWorld.runs(path(w, 3250, 3250, 3260, 3253)));
		assertEquals("7xW 3xSW", TestWorld.runs(path(w, 3250, 3250, 3240, 3247)));
		assertEquals("7xE 3xSE", TestWorld.runs(path(w, 3250, 3250, 3260, 3247)));
		assertEquals("7xN 3xNE", TestWorld.runs(path(w, 3250, 3250, 3253, 3260)));
		assertEquals("7xS 3xSW", TestWorld.runs(path(w, 3250, 3250, 3247, 3240)));
		assertEquals("10xE", TestWorld.runs(path(w, 3250, 3250, 3260, 3250)));
		assertEquals("10xNE", TestWorld.runs(path(w, 3250, 3250, 3260, 3260)));
	}

	@Test
	void wallsAreWalkedAroundWithoutCuttingCorners()
	{
		TestWorld w = new TestWorld();
		// a wall along the east side of x 3255, y 3245..3255: from the west of it to the east of it
		for (int y = 3245; y <= 3255; y++)
		{
			w.wallEast(3255, y);
		}
		int[] p = path(w, 3250, 3250, 3260, 3250);
		assertNotNull(p);
		for (int i = 1; i < p.length; i++)
		{
			int dx = Tiles.x(p[i]) - Tiles.x(p[i - 1]);
			int dy = Tiles.y(p[i]) - Tiles.y(p[i - 1]);
			assertTrue(w.map().canStep(Tiles.x(p[i - 1]), Tiles.y(p[i - 1]), 0, dx, dy), "legal step " + i);
		}
		// round the wall's end, as short as any walk (the brute-force checker's)
		Route best = BruteForce.find(w.map(), new PathQuery(3250, 3250, 0, 3260, 3250, 0, List.of(), 0));
		assertEquals(best.getTicks(), p.length - 1);
		assertEquals(13, p.length - 1);
		// the south end comes first in the game's order (south before north)
		assertTrue(Arrays.stream(p).anyMatch(t -> Tiles.y(t) == 3244), TestWorld.dirs(p));
	}

	@Test
	void closedDoorsBlockTheClickPath()
	{
		TestWorld w = new TestWorld();
		// a room wall with a door: the game walks round, our routes go through (doors open)
		for (int y = 3240; y <= 3260; y++)
		{
			if (y != 3250)
			{
				w.wallEast(3255, y);
			}
		}
		w.doorEast(3255, 3250);
		int[] p = path(w, 3253, 3250, 3258, 3250);
		assertNotNull(p);
		assertTrue(p.length - 1 > 5, "not through the door: " + TestWorld.dirs(p));
		GamePath noDoors = new GamePath(w.map(), DoorMap.none());
		noDoors.search(tile(3253, 3250), 200);
		assertEquals(5, noDoors.steps(tile(3258, 3250)), "through it when no door is known");
	}

	@Test
	void searchStaysInsideTheSquareAroundThePlayer()
	{
		TestWorld w = new TestWorld();
		GamePath g = new GamePath(w.map(), DoorMap.none());
		g.search(tile(3300, 3300), 500);
		// x from 3300 - 64 to 3300 + 63
		assertEquals(64, g.steps(tile(3236, 3300)));
		assertEquals(63, g.steps(tile(3363, 3300)));
		assertEquals(-1, g.steps(tile(3235, 3300)));
		assertEquals(-1, g.steps(tile(3364, 3300)));
		assertEquals(-1, g.steps(tile(3300, 3364)));
		assertNull(g.pathTo(tile(3364, 3300)));
		// a goal that ends the search early still has its path
		g.search(tile(3300, 3300), 500, tile(3310, 3303));
		assertEquals("7xE 3xNE", TestWorld.runs(g.pathTo(tile(3310, 3303))));
	}

	@Test
	void pathsThroughAGamePathAreItsBeginningAndItsRest()
	{
		Random rnd = new Random(14);
		int checked = 0;
		for (int world = 0; world < 6; world++)
		{
			TestWorld w = TestWorld.random(rnd, world * 2);
			GamePath g = new GamePath(w.map(), w.doorMap());
			for (int i = 0; i < 30; i++)
			{
				int from = tile(3240 + rnd.nextInt(110), 3240 + rnd.nextInt(110));
				int to = tile(Tiles.x(from) - 50 + rnd.nextInt(100), Tiles.y(from) - 50 + rnd.nextInt(100));
				g.search(from, 300);
				int[] p = g.pathTo(to);
				if (p == null || p.length < 3)
				{
					continue;
				}
				// to any tile on it: its beginning
				int k = 1 + rnd.nextInt(p.length - 2);
				assertArrayEquals(Arrays.copyOf(p, k + 1), g.pathTo(p[k]), "beginning, world " + world);
				// from any tile on it: its rest (when no equally short way can leave the first search's square)
				int away = Math.max(Math.abs(Tiles.x(p[k]) - Tiles.x(from)), Math.abs(Tiles.y(p[k]) - Tiles.y(from)));
				if (away + p.length - 1 - k > 63)
				{
					continue;
				}
				g.search(p[k], 300);
				assertArrayEquals(Arrays.copyOfRange(p, k, p.length), g.pathTo(to), "rest, world " + world);
				checked++;
			}
		}
		assertTrue(checked > 100, "checked " + checked);
	}

	@Test
	void clicksStopAtThe25thCorner()
	{
		// a zigzag: E, NE, E, NE... every tile a corner
		int[] p = new int[41];
		int x = 3210;
		int y = 3210;
		for (int i = 0; i < p.length; i++)
		{
			p[i] = tile(x, y);
			x++;
			y += i % 2;
		}
		assertEquals(25, GamePathDiagnostics.clickEnd(p));
		int[] straight = new int[30];
		for (int i = 0; i < straight.length; i++)
		{
			straight[i] = tile(3210 + i, 3210);
		}
		assertEquals(29, GamePathDiagnostics.clickEnd(straight));
		assertEquals(0, GamePathDiagnostics.clickEnd(new int[]{tile(3210, 3210)}));
	}
}
