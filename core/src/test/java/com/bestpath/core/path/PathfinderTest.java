package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The pathfinder against the brute-force checker (plain Dijkstra over tiles,
 * no coarse graph): the tick totals must be identical, on hand-made cases
 * and on many random worlds and queries. Every route is also replayed step
 * by step to prove it is legal and costs what it says.
 */
class PathfinderTest
{
	/** World: regions (50..52, 50..52), planes 0 and 1; x 3200-3391, y 3200-3391. */
	private static final int X0 = 3200;
	private static final int Y0 = 3200;
	private static final int SIZE = 192;

	private static final class World implements MovementRules.Flags
	{
		final int plane;
		final Map<Long, Integer> flags = new HashMap<>();

		World(int plane)
		{
			this.plane = plane;
		}

		void set(int x, int y, int f)
		{
			flags.merge((long) x << 32 | y, f, (p, q) -> p | q);
		}

		void block(int x, int y)
		{
			set(x, y, CollisionFlags.OBJECT);
		}

		/** A wall on the east side of (x, y). */
		void wallEast(int x, int y)
		{
			set(x, y, CollisionFlags.EAST);
			set(x + 1, y, CollisionFlags.WEST);
		}

		void wallNorth(int x, int y)
		{
			set(x, y, CollisionFlags.NORTH);
			set(x, y + 1, CollisionFlags.SOUTH);
		}

		@Override
		public int get(int x, int y)
		{
			boolean inside = x >= X0 && x < X0 + SIZE && y >= Y0 && y < Y0 + SIZE;
			return inside ? flags.getOrDefault((long) x << 32 | y, 0) : CollisionFlags.BLOCKED;
		}
	}

	private static List<Integer> regions()
	{
		List<Integer> r = new ArrayList<>();
		for (int rx = 50; rx <= 52; rx++)
		{
			for (int ry = 50; ry <= 52; ry++)
			{
				r.add(rx << 8 | ry);
			}
		}
		return r;
	}

	private static CollisionMap map(World p0, World p1)
	{
		MovementRules.Flags empty = (x, y) -> CollisionFlags.BLOCKED;
		return CollisionMapBuilder.build(new MovementRules.Flags[]{p0, p1, empty, empty}, regions(), 0, 0);
	}

	private static PathQuery q(int sx, int sy, int sp, int dx, int dy, int dp, List<Transport> t)
	{
		return new PathQuery(sx, sy, sp, dx, dy, dp, t, 2);
	}

	/** Runs both searches, checks they agree, replays the route; returns the ticks (-1 if none). */
	private static int both(CollisionMap m, Pathfinder pf, PathQuery q)
	{
		Route fast = pf.find(q);
		Route brute = BruteForce.find(m, q);
		String where = String.format("from %d,%d,%d to %d,%d,%d", q.getStartX(), q.getStartY(), q.getStartPlane(),
			q.getDestX(), q.getDestY(), q.getDestPlane());
		assertEquals(brute.isFound(), fast.isFound(), "found? " + where);
		assertEquals(brute.getTicks(), fast.getTicks(), "ticks " + where);
		if (fast.isFound())
		{
			replay(m, q, fast, where);
		}
		return fast.getTicks();
	}

	/** Checks that a route is legal: steps are walkable, transports are used correctly, ticks add up. */
	private static void replay(CollisionMap m, PathQuery q, Route r, String where)
	{
		int at = -1;
		int total = 0;
		for (Route.Step s : r.getSteps())
		{
			int[] t = s.getTiles();
			switch (s.getKind())
			{
				case WALK:
					if (at >= 0)
					{
						assertEquals(at, t[0], "walk starts where we are " + where);
					}
					else
					{
						assertEquals(Tiles.pack(q.getStartX(), q.getStartY(), q.getStartPlane()), t[0], "starts at the player " + where);
					}
					for (int i = 1; i < t.length; i++)
					{
						int dx = Tiles.x(t[i]) - Tiles.x(t[i - 1]);
						int dy = Tiles.y(t[i]) - Tiles.y(t[i - 1]);
						assertEquals(Tiles.plane(t[i]), Tiles.plane(t[i - 1]));
						assertTrue(m.canStep(Tiles.x(t[i - 1]), Tiles.y(t[i - 1]), Tiles.plane(t[i - 1]), dx, dy), "legal step " + where);
					}
					assertEquals(WalkCost.cost(t, q.getDoors(), q.getDoorCost()), s.getTicks(), "walk ticks (steps + doors) " + where);
					at = t[t.length - 1];
					break;
				case TRANSPORT:
				{
					Transport tr = s.getTransport();
					int st = Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane());
					int en = Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane());
					int from = at >= 0 ? at : Tiles.pack(q.getStartX(), q.getStartY(), q.getStartPlane());
					boolean forward = from == st && t[1] == en;
					boolean backward = tr.isTwoWay() && from == en && t[1] == st;
					assertTrue(forward || backward, "transport used from its start (or end if two-way) " + where);
					at = t[1];
					break;
				}
				case TELEPORT:
					assertEquals(-1, at, "a teleport only starts a route " + where);
					at = t[0];
					break;
				default:
					fail();
			}
			total += s.getTicks();
		}
		if (at < 0)
		{
			at = Tiles.pack(q.getStartX(), q.getStartY(), q.getStartPlane());
		}
		assertEquals(Tiles.pack(q.getDestX(), q.getDestY(), q.getDestPlane()), at, "ends at the destination " + where);
		assertEquals(r.getTicks(), total, "ticks add up " + where);
	}

	@Test
	void walkingStraightAndDiagonalCostsOneTickPerTile()
	{
		World w0 = new World(0);
		CollisionMap m = map(w0, new World(1));
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		assertEquals(10, both(m, pf, q(3210, 3210, 0, 3220, 3210, 0, List.of())));
		assertEquals(10, both(m, pf, q(3210, 3210, 0, 3220, 3220, 0, List.of())));
		assertEquals(0, both(m, pf, q(3210, 3210, 0, 3210, 3210, 0, List.of())));
		// across region borders
		assertEquals(100, both(m, pf, q(3210, 3210, 0, 3310, 3250, 0, List.of())));
	}

	@Test
	void longOpenWalksHaveFewTurns()
	{
		CollisionMap m = map(new World(0), new World(1));
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		// longer than the game's search square (63 tiles): shaped as clicks, each one straight run and one diagonal run;
		// the last two fit in the square: one click
		int[][] cases = {{3385, 3300, 3205, 3290}, {3385, 3250, 3210, 3330}, {3300, 3385, 3290, 3205}, {3210, 3210, 3380, 3260},
			{3300, 3300, 3250, 3270}, {3220, 3230, 3275, 3240}};
		for (int[] c : cases)
		{
			Route r = pf.find(q(c[0], c[1], 0, c[2], c[3], 0, List.of()));
			int[] t = r.getSteps().get(0).getTiles();
			int turns = 0;
			StringBuilder runs = new StringBuilder();
			int run = 0;
			for (int i = 1; i < t.length; i++)
			{
				run++;
				boolean last = i == t.length - 1;
				if (last || Tiles.x(t[i + 1]) - Tiles.x(t[i]) != Tiles.x(t[i]) - Tiles.x(t[i - 1])
					|| Tiles.y(t[i + 1]) - Tiles.y(t[i]) != Tiles.y(t[i]) - Tiles.y(t[i - 1]))
				{
					runs.append(Tiles.x(t[i]) - Tiles.x(t[i - 1])).append(',').append(Tiles.y(t[i]) - Tiles.y(t[i - 1])).append('x').append(run).append(' ');
					run = 0;
					turns += last ? 0 : 1;
				}
			}
			System.out.println("open walk " + Arrays.toString(c) + ": " + turns + " turns: " + runs);
			int far = Math.max(Math.abs(c[2] - c[0]), Math.abs(c[3] - c[1]));
			if (far <= 63)
			{
				assertEquals(1, turns, "open ground: one straight run and one diagonal run " + Arrays.toString(c) + ": " + runs);
			}
			else
			{
				// at most a straight run and a diagonal run per click of up to 63 tiles
				int clicks = (far + 62) / 63;
				assertTrue(turns <= 2 * clicks - 1, "open ground: few turns " + Arrays.toString(c) + ": " + runs);
			}
		}
	}

	@Test
	void walksHaveTheGamesShapeStraightFirstThenDiagonal()
	{
		CollisionMap m = map(new World(0), new World(1));
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		Route r = pf.find(q(3210, 3210, 0, 3220, 3213, 0, List.of()));
		assertEquals(10, r.getTicks());
		int[] t = r.getSteps().get(0).getTiles();
		StringBuilder dirs = new StringBuilder();
		for (int i = 1; i < t.length; i++)
		{
			int dx = Tiles.x(t[i]) - Tiles.x(t[i - 1]);
			int dy = Tiles.y(t[i]) - Tiles.y(t[i - 1]);
			dirs.append(dx == 1 && dy == 0 ? "E" : dx == 1 && dy == 1 ? "NE" : "?").append(' ');
		}
		assertEquals("E E E E E E E NE NE NE", dirs.toString().trim());
	}

	@Test
	void wallsBlockTheLineOfSightButObjectsDoNot()
	{
		World w = new World(0);
		w.block(3215, 3210); // an object in between: does not block the view
		LineOfSight.WallFlags flags = (x, y, p) -> w.get(x, y);
		assertTrue(LineOfSight.clear(flags, 0, 3210, 3210, 3220, 3210));
		w.wallEast(3217, 3210);
		assertFalse(LineOfSight.clear(flags, 0, 3210, 3210, 3220, 3210));
		assertTrue(LineOfSight.clear(flags, 0, 3210, 3210, 3217, 3214), "the wall is not on this line");
	}

	@Test
	void unreachableDestinationEndsAtTheNearestTileInSight()
	{
		World w0 = new World(0);
		// a 3x3 rock at 3300-3302, 3300-3302: its middle cannot be reached
		for (int x = 3300; x <= 3302; x++)
		{
			for (int y = 3300; y <= 3302; y++)
			{
				w0.block(x, y);
			}
		}
		CollisionMap m0 = map(w0, new World(1));
		Pathfinder pf0 = new Pathfinder(m0, CoarseGraph.build(m0), null);
		LineOfSight.WallFlags flags = (x, y, p) -> p == 0 ? w0.get(x, y) : 0;
		PathQuery from = q(3290, 3301, 0, 3301, 3301, 0, List.of());
		// nearest reachable tiles: 3299,3301 / 3303,3301 / 3301,3299 / 3301,3303; the first is nearest the player
		assertArrayEquals(new int[]{3299, 3301, 0, 1}, pf0.routeEnd(from, 5, flags, false));

		// a wall on the rock's west side: 3299,3301 can no longer see the middle
		World w1 = new World(0);
		w1.flags.putAll(w0.flags);
		for (int y = 3298; y <= 3304; y++)
		{
			w1.wallEast(3299, y);
		}
		CollisionMap m1 = map(w1, new World(1));
		Pathfinder pf1 = new Pathfinder(m1, CoarseGraph.build(m1), null);
		LineOfSight.WallFlags flags1 = (x, y, p) -> p == 0 ? w1.get(x, y) : 0;
		assertArrayEquals(new int[]{3301, 3299, 0, 1}, pf1.routeEnd(from, 5, flags1, false), "nearest tile that sees it");

		// walls all round: nothing sees it, so the nearest reachable tile anyway (marked not in sight)
		World w2 = new World(0);
		w2.flags.putAll(w1.flags);
		for (int i = 3298; i <= 3304; i++)
		{
			w2.wallEast(3302, i);
			w2.wallNorth(i, 3302);
			w2.wallNorth(i, 3299);
		}
		CollisionMap m2 = map(w2, new World(1));
		Pathfinder pf2 = new Pathfinder(m2, CoarseGraph.build(m2), null);
		LineOfSight.WallFlags flags2 = (x, y, p) -> p == 0 ? w2.get(x, y) : 0;
		assertArrayEquals(new int[]{3299, 3301, 0, 0}, pf2.routeEnd(from, 5, flags2, false), "fallback: nearest, not in sight");

		// out of range: none; a reachable destination is kept
		assertEquals(null, pf0.routeEnd(from, 1, flags, false));
		assertArrayEquals(new int[]{3290, 3301, 0, 1}, pf0.routeEnd(q(3280, 3301, 0, 3290, 3301, 0, List.of()), 5, flags, false));
	}

	@Test
	void stopTileCanBeKeptInsideTheDestinationsRegion()
	{
		World w0 = new World(0);
		// region 51 starts at x = 3264; block everything of it near the destination
		for (int x = 3264; x <= 3272; x++)
		{
			for (int y = 3292; y <= 3308; y++)
			{
				w0.block(x, y);
			}
		}
		CollisionMap m = map(w0, new World(1));
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		LineOfSight.WallFlags flags = (x, y, p) -> 0;
		PathQuery from = q(3240, 3300, 0, 3264, 3300, 0, List.of());
		assertArrayEquals(new int[]{3263, 3300, 0, 1}, pf.routeEnd(from, 3, flags, false), "next region allowed");
		assertEquals(null, pf.routeEnd(from, 3, flags, true), "own region only: nothing reachable in range");
	}

	@Test
	void doorCostSendsRoutesAroundWhenThatIsCheaper()
	{
		// a fence east of x = 3215 from y 3200 to 3240, with a gate (door) at y = 3220
		World open = new World(0);
		World shut = new World(0);
		for (int y = 3200; y <= 3240; y++)
		{
			shut.wallEast(3215, y);
			if (y != 3220)
			{
				open.wallEast(3215, y);
			}
		}
		CollisionMap m = map(open, new World(1));
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		DoorMap doors = DoorMapBuilder.build(new MovementRules.Flags[]{open, none, none, none},
			new MovementRules.Flags[]{shut, none, none, none}, List.of(new int[]{3215, 3220, 0}));
		assertTrue(doors.crosses(Tiles.pack(3215, 3220, 0), Tiles.pack(3216, 3220, 0)));
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		PathQuery cheap = new PathQuery(3210, 3220, 0, 3220, 3220, 0, List.of(), 2, doors, 2);
		PathQuery dear = new PathQuery(3210, 3220, 0, 3220, 3220, 0, List.of(), 2, doors, 60);
		// through the gate: 10 steps + 2
		assertEquals(12, both(m, pf, cheap));
		// round the end of the fence instead: no door crossed, and cheaper than 10 + 60
		int around = both(m, pf, dear);
		assertTrue(around < 70 && around > 12, "walks round: " + around);
		for (Route.Step s : pf.find(dear).getSteps())
		{
			int[] t = s.getTiles();
			for (int i = 1; i < t.length; i++)
			{
				assertFalse(doors.crosses(t[i - 1], t[i]), "no door on the way round");
			}
		}
	}

	@Test
	void wallsForceADetour()
	{
		World w0 = new World(0);
		for (int y = 3200; y < 3230; y++)
		{
			w0.wallEast(3215, y); // wall with a gap at y >= 3230
		}
		CollisionMap m = map(w0, new World(1));
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		int t = both(m, pf, q(3210, 3210, 0, 3220, 3210, 0, List.of()));
		assertTrue(t > 10, "detour around the wall: " + t);
	}

	@Test
	void ladderAddsItsTicksAndThePlaneChangePenalty()
	{
		World w0 = new World(0);
		World w1 = new World(1);
		CollisionMap m = map(w0, w1);
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		Transport ladder = Transport.between("l", "Ladder", 3215, 3210, 0, 3215, 3210, 1, 2, true, true);
		// walk 5, climb 2 + penalty 2, walk 5
		assertEquals(14, both(m, pf, q(3210, 3210, 0, 3220, 3210, 1, List.of(ladder))));
		// and back down (two-way)
		assertEquals(14, both(m, pf, q(3220, 3210, 1, 3210, 3210, 0, List.of(ladder))));
	}

	@Test
	void oneWayTransportCannotBeUsedBackwards()
	{
		World w0 = new World(0);
		World w1 = new World(1);
		CollisionMap m = map(w0, w1);
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		Transport drop = Transport.between("d", "Drop", 3215, 3210, 1, 3215, 3210, 0, 1, false, true);
		assertEquals(-1, both(m, pf, q(3215, 3210, 0, 3215, 3210, 1, List.of(drop))));
		assertEquals(3, both(m, pf, q(3215, 3210, 1, 3215, 3210, 0, List.of(drop))));
	}

	@Test
	void teleportIsUsedWhenCheaper()
	{
		World w0 = new World(0);
		CollisionMap m = map(w0, new World(1));
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		Transport tele = Transport.teleport("t", "Teleport", 3380, 3380, 0, 5, true);
		Route r = pf.find(q(3201, 3201, 0, 3385, 3380, 0, List.of(tele)));
		assertEquals(10, r.getTicks());
		assertEquals(Route.Step.Kind.TELEPORT, r.getSteps().get(0).getKind());
		assertEquals(10, both(m, pf, q(3201, 3201, 0, 3385, 3380, 0, List.of(tele))));
	}

	@Test
	void enclosedDestinationIsNotFound()
	{
		World w0 = new World(0);
		for (int x = 3300; x <= 3304; x++)
		{
			for (int y = 3300; y <= 3304; y++)
			{
				if (x == 3300 || x == 3304 || y == 3300 || y == 3304)
				{
					w0.block(x, y);
				}
			}
		}
		CollisionMap m = map(w0, new World(1));
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		assertFalse(pf.find(q(3210, 3210, 0, 3302, 3302, 0, List.of())).isFound());
		assertEquals(-1, both(m, pf, q(3210, 3210, 0, 3302, 3302, 0, List.of())));
	}

	@Test
	void randomWorldsAgreeWithBruteForce()
	{
		Random rnd = new Random(20261009);
		int queries = 0;
		for (int world = 0; world < 12; world++)
		{
			World w0 = new World(0);
			World w1 = new World(1);
			for (World w : new World[]{w0, w1})
			{
				// plane 1 is mostly empty: only a few "buildings"
				if (w == w1)
				{
					for (int x = X0; x < X0 + SIZE; x++)
					{
						for (int y = Y0; y < Y0 + SIZE; y++)
						{
							boolean building = ((x - X0) / 24 + (y - Y0) / 24) % 3 == 0;
							if (!building)
							{
								w.set(x, y, CollisionFlags.NO_FLOOR);
							}
						}
					}
				}
				int walls = 600 + rnd.nextInt(1500);
				for (int i = 0; i < walls; i++)
				{
					int x = X0 + rnd.nextInt(SIZE);
					int y = Y0 + rnd.nextInt(SIZE);
					int len = 1 + rnd.nextInt(20);
					boolean vertical = rnd.nextBoolean();
					for (int k = 0; k < len; k++)
					{
						if (vertical)
						{
							w.wallEast(x, y + k);
						}
						else
						{
							w.wallNorth(x + k, y);
						}
					}
				}
				int blocks = 300 + rnd.nextInt(1500);
				for (int i = 0; i < blocks; i++)
				{
					w.block(X0 + rnd.nextInt(SIZE), Y0 + rnd.nextInt(SIZE));
				}
			}
			CollisionMap m = map(w0, w1);
			Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
			List<Transport> tr = new ArrayList<>();
			int n = rnd.nextInt(25);
			for (int i = 0; i < n; i++)
			{
				int sp = rnd.nextInt(2);
				int ep = rnd.nextInt(10) < 6 ? 1 - sp : sp;
				int sx = X0 + rnd.nextInt(SIZE);
				int sy = Y0 + rnd.nextInt(SIZE);
				boolean far = rnd.nextInt(4) == 0;
				int ex = far ? X0 + rnd.nextInt(SIZE) : Math.min(X0 + SIZE - 1, Math.max(X0, sx + rnd.nextInt(9) - 4));
				int ey = far ? Y0 + rnd.nextInt(SIZE) : Math.min(Y0 + SIZE - 1, Math.max(Y0, sy + rnd.nextInt(9) - 4));
				if (rnd.nextInt(8) == 0)
				{
					tr.add(Transport.teleport("tp" + i, "Teleport", ex, ey, ep, 3 + rnd.nextInt(20), true));
				}
				else
				{
					tr.add(Transport.between("t" + i, "T", sx, sy, sp, ex, ey, ep, rnd.nextInt(12), rnd.nextBoolean(), true));
				}
			}
			for (int i = 0; i < 40; i++)
			{
				both(m, pf, q(X0 + rnd.nextInt(SIZE), Y0 + rnd.nextInt(SIZE), rnd.nextInt(2),
					X0 + rnd.nextInt(SIZE), Y0 + rnd.nextInt(SIZE), rnd.nextInt(2), tr));
				queries++;
			}
		}
		assertEquals(480, queries);
	}
}
