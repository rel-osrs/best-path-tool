package com.bestpath.core.path;

import static com.bestpath.core.path.TestWorld.tile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Redrawing the walk ahead from the player's tile (LineRedraw): nothing changes when nothing differs, a click on the
 * line is drawn as the game walks it, a player beside the line gets a line through their tile, and the line never
 * flips back to a shape it had (no shimmer or wobble).
 */
class LineRedrawTest
{
	@Test
	void nothingDiffersNothingChanges()
	{
		TestWorld w = new TestWorld();
		GamePath game = new GamePath(w.map(), DoorMap.none());
		int[] line = GameWalk.shape(game, GameWalkTest.walk(3220, 3220, "NE NE NE NE E E E E E E E E E E"));
		LineRedraw r = new LineRedraw();
		assertSame(line, r.redraw(game, line, 0, line[0], -1), "standing at the start");
		assertSame(line, r.redraw(game, line, 3, line[3], line[12]), "walking to a tile of the line");
		assertSame(line, r.redraw(game, line, 12, line[12], -1), "standing further on");
	}

	@Test
	void aClickOnTheLineIsDrawnAsTheGameWalksIt()
	{
		TestWorld w = new TestWorld();
		GamePath game = new GamePath(w.map(), DoorMap.none());
		// a line that is not the game's shape from its start (north-east first)
		int[] line = GameWalkTest.walk(3220, 3220, "NE NE NE NE E E E E E E E E E E");
		LineRedraw r = new LineRedraw();
		int[] now = r.redraw(game, line, 0, line[0], line[10]);
		assertNotSame(line, now);
		// up to the clicked tile (6 east and 4 north of the start): east first, as the game walks; the rest kept
		assertEquals("6xE 4xNE 4xE", TestWorld.runs(now));
		assertArrayEquals(Arrays.copyOfRange(line, 10, line.length), Arrays.copyOfRange(now, 10, now.length));
		// walking it: nothing changes on the way
		for (int i = 1; i < 10; i++)
		{
			assertSame(now, r.redraw(game, now, i, now[i], line[10]), "step " + i);
		}
	}

	@Test
	void aPlayerBesideTheLineGetsALineThroughTheirTile()
	{
		TestWorld w = new TestWorld();
		GamePath game = new GamePath(w.map(), DoorMap.none());
		int[] line = GameWalk.shape(game, GameWalkTest.walk(3220, 3220, "E E E E E E E E E E NE NE NE NE"));
		// the player went another equally short way: 3 steps along, one tile north of the line
		int player = tile(3223, 3221);
		int[] now = new LineRedraw().redraw(game, line, 0, player, -1);
		assertEquals(line.length, now.length);
		assertEquals(player, now[3]);
		assertEquals(line[line.length - 1], now[now.length - 1]);
		game.search(player, 100);
		assertArrayEquals(game.pathTo(line[line.length - 1]), Arrays.copyOfRange(now, 3, now.length));
		// too far off the line: left to the route's own "off the route" check
		assertSame(line, new LineRedraw().redraw(game, line, 0, tile(3223, 3230), -1));
	}

	@Test
	void walkingSomewhereElseChangesNothingUntilThePlayerStops()
	{
		TestWorld w = new TestWorld();
		GamePath game = new GamePath(w.map(), DoorMap.none());
		int[] line = GameWalk.shape(game, GameWalkTest.walk(3220, 3220, "E E E E E E E E E E"));
		LineRedraw r = new LineRedraw();
		assertSame(line, r.redraw(game, line, 0, tile(3221, 3221), tile(3229, 3225)), "flag off the line");
		assertNotSame(line, r.redraw(game, line, 0, tile(3221, 3221), -1), "stopped beside it");
	}

	/**
	 * Players clicking K tiles ahead on the line (K from 3 to 40, also across doors and over 25 corners), walking the
	 * game's path with its flag, stopping, clicking again: the line never goes back to a shape it had, changes at
	 * most once per click and once per stop, and never while a click is walked (after its first tile).
	 */
	@Test
	void theLineNeverFlipsBackToAShapeItHad()
	{
		Random rnd = new Random(1014);
		int walks = 0;
		int changes = 0;
		int tilesWalked = 0;
		for (int world = 0; world < 10; world++)
		{
			TestWorld w = TestWorld.random(rnd, world < 3 ? world : 2 + rnd.nextInt(16));
			CollisionMap map = w.map();
			DoorMap doors = w.doorMap();
			GamePath shaping = new GamePath(map, doors);
			GamePath game = new GamePath(map, doors);
			for (int i = 0; i < 20; i++)
			{
				int fx = TestWorld.X0 + rnd.nextInt(TestWorld.SIZE);
				int fy = TestWorld.Y0 + rnd.nextInt(TestWorld.SIZE);
				Route best = BruteForce.find(map, new PathQuery(fx, fy, 0, TestWorld.X0 + rnd.nextInt(TestWorld.SIZE),
					TestWorld.Y0 + rnd.nextInt(TestWorld.SIZE), 0, List.of(), 0, doors, 2));
				if (!best.isFound() || best.getSteps().isEmpty() || best.getSteps().get(0).getTiles().length < 5)
				{
					continue;
				}
				// half the time today's found walk, half the time the game's shape: both must behave
				int[] walk = best.getSteps().get(0).getTiles();
				int[] line = rnd.nextBoolean() ? walk : GameWalk.shape(shaping, walk);
				int k = 3 + rnd.nextInt(38);
				int[] c = replay(game, line, k, "world " + world + " walk " + i + " click " + k);
				changes += c[0];
				tilesWalked += c[1];
				walks++;
			}
		}
		assertTrue(walks > 120, "walks " + walks);
		System.out.printf("line changes: %d in %d walked tiles (%d walks)%n", changes, tilesWalked, walks);
	}

	/** One player walking the line by clicks k ahead; checks the rules; returns {changes, tiles walked}. */
	private static int[] replay(GamePath game, int[] line, int k, String where)
	{
		LineRedraw r = new LineRedraw();
		List<int[]> shapes = new ArrayList<>();
		int[] drawn = line;
		shapes.add(drawn);
		int at = 0; // the player's index along the walk
		int player = line[0];
		int changes = 0;
		int walked = 0;
		int guard = 0;
		while (at < line.length - 1 && guard++ < 1000)
		{
			// standing still: at most one change
			int[] now = r.redraw(game, drawn, at, player, -1);
			changes += record(shapes, drawn, now, where + " stop at " + at);
			drawn = now;
			// the next piece's end (a door step or a step off a solid tile is taken on its own)
			int end = at;
			while (end < drawn.length - 1 && game.isPlainStep(drawn[end], drawn[end + 1]))
			{
				end++;
			}
			if (end == at)
			{
				player = drawn[at + 1];
				at++;
				walked++;
				continue;
			}
			int target = Math.min(at + k, end);
			int flag = drawn[target];
			// the click: the game's flag appears before the player moves
			now = r.redraw(game, drawn, at, player, flag);
			changes += record(shapes, drawn, now, where + " click at " + at);
			drawn = now;
			game.search(player, 500, flag);
			int[] path = game.pathTo(flag);
			assertTrue(path != null && path.length - 1 == target - at, "the click is walked with no extra steps " + where);
			int stop = GamePathDiagnostics.clickEnd(path);
			for (int i = 1; i <= stop; i++)
			{
				assertEquals(drawn[at + i], path[i], "the player walks the drawn line " + where + " step " + (at + i));
				player = path[i];
				walked++;
				now = r.redraw(game, drawn, at + i, player, i < stop ? flag : -1);
				if (i < stop)
				{
					assertSame(drawn, now, "no change while a click is walked " + where);
				}
				changes += record(shapes, drawn, now, where + " tile " + (at + i));
				drawn = now;
			}
			at += stop;
		}
		assertTrue(at >= line.length - 1, "reached the end " + where);
		return new int[]{changes, walked};
	}

	/** 1 if the line changed (it must not go back to an earlier shape), else 0. */
	private static int record(List<int[]> shapes, int[] before, int[] after, String where)
	{
		if (after == before)
		{
			return 0;
		}
		assertFalse(Arrays.equals(before, after), "a new array only when something differs " + where);
		for (int[] s : shapes)
		{
			assertFalse(Arrays.equals(s, after), "never back to an earlier shape " + where);
		}
		shapes.add(after);
		return 1;
	}
}
