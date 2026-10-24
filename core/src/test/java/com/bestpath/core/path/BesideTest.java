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
 * Transports used from beside their ends ({@link Transport#usedFromBeside()}, {@link Beside}): fairy ring
 * stops sit on the ring's solid middle tile. On small hand-made maps: boarding from the four sides (not the
 * corners, not through a wall), landing on the solid tile and stepping off it (1 tick, diagonals only when
 * both ways round the corner are open), nothing changes for ends that can be walked on or for transports
 * without the mark, and the fast search agrees with the brute-force checker on random worlds with rings.
 */
class BesideTest
{
	/** World: regions (50..52, 50..52), planes 0 and 1; x 3200-3391, y 3200-3391. */
	private static final int X0 = 3200;
	private static final int Y0 = 3200;
	private static final int SIZE = 192;
	private static final int PENALTY = 2;

	private static final class World implements MovementRules.Flags
	{
		final Map<Long, Integer> flags = new HashMap<>();

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

	private static final class Setup
	{
		final World w0 = new World();
		final World w1 = new World();
		CollisionMap map;
		Pathfinder pf;
		LineOfSight.WallFlags walls;

		Setup build()
		{
			List<Integer> regions = new ArrayList<>();
			for (int rx = 50; rx <= 52; rx++)
			{
				for (int ry = 50; ry <= 52; ry++)
				{
					regions.add(rx << 8 | ry);
				}
			}
			MovementRules.Flags empty = (x, y) -> CollisionFlags.BLOCKED;
			map = CollisionMapBuilder.build(new MovementRules.Flags[]{w0, w1, empty, empty}, regions, 0, 0);
			walls = (x, y, p) -> p == 0 ? w0.get(x, y) : p == 1 ? w1.get(x, y) : 0;
			pf = new Pathfinder(map, CoarseGraph.build(map), walls);
			return this;
		}
	}

	/** A fairy ring ride between two ring middles (solid tiles), as the network stops make it. */
	private static Transport ring(String name, int sx, int sy, int sp, int ex, int ey, int ep, int ticks)
	{
		return Transport.between(name, name, sx, sy, sp, ex, ey, ep, ticks, false, true).usedFromBeside();
	}

	private static PathQuery q(int sx, int sy, int sp, int dx, int dy, int dp, List<Transport> t)
	{
		return new PathQuery(sx, sy, sp, dx, dy, dp, t, PENALTY);
	}

	private static int tile(int x, int y, int p)
	{
		return Tiles.pack(x, y, p);
	}

	/** Runs the fast search and the brute-force checker, checks they agree, replays the route; returns it. */
	private static Route both(Setup s, PathQuery q)
	{
		Route fast = s.pf.find(q);
		Route brute = BruteForce.find(s.map, q, s.walls);
		String where = String.format("from %d,%d,%d to %d,%d,%d", q.getStartX(), q.getStartY(), q.getStartPlane(),
			q.getDestX(), q.getDestY(), q.getDestPlane());
		assertEquals(brute.isFound(), fast.isFound(), "found? " + where);
		assertEquals(brute.getTicks(), fast.getTicks(), "ticks " + where);
		if (fast.isFound())
		{
			replay(s, q, fast, where);
			replay(s, q, brute, where);
		}
		return fast;
	}

	/**
	 * A route is legal: walks are legal steps (the first step of a walk may leave a solid tile the route just
	 * landed on, as {@link Beside} allows), transports are used from their start or, for marked ones, from a
	 * boarding tile beside it, and the ticks add up.
	 */
	private static void replay(Setup s, PathQuery q, Route r, String where)
	{
		int at = tile(q.getStartX(), q.getStartY(), q.getStartPlane());
		int total = 0;
		Beside beside = new Beside(s.map, s.walls);
		for (Route.Step st : r.getSteps())
		{
			int[] t = st.getTiles();
			switch (st.getKind())
			{
				case WALK:
					assertEquals(at, t[0], "walk starts where we are " + where);
					for (int i = 1; i < t.length; i++)
					{
						int dx = Tiles.x(t[i]) - Tiles.x(t[i - 1]);
						int dy = Tiles.y(t[i]) - Tiles.y(t[i - 1]);
						boolean legal = s.map.canStep(Tiles.x(t[i - 1]), Tiles.y(t[i - 1]), Tiles.plane(t[i - 1]), dx, dy);
						boolean stepOff = i == 1 && Arrays.stream(beside.leaveTiles(t[0])).anyMatch(n -> n == t[1]);
						assertTrue(legal || stepOff, "legal step " + where);
					}
					assertEquals(t.length - 1, st.getTicks(), "walk ticks " + where);
					at = t[t.length - 1];
					break;
				case TRANSPORT:
				{
					Transport tr = st.getTransport();
					int start = tile(tr.getStartX(), tr.getStartY(), tr.getStartPlane());
					int end = tile(tr.getEndX(), tr.getEndY(), tr.getEndPlane());
					assertEquals(at, t[0], "transport used where we are " + where);
					boolean fromStart = at == start
						|| tr.isUsedFromBeside() && Arrays.stream(beside.boardTiles(start)).anyMatch(n -> n == t[0]);
					assertTrue(fromStart && t[1] == end, "transport used from its start or beside it " + where);
					assertEquals(tr.cost(PENALTY), st.getTicks());
					at = t[1];
					break;
				}
				case TELEPORT:
					at = t[0];
					break;
				default:
					fail();
			}
			total += st.getTicks();
		}
		assertEquals(tile(q.getDestX(), q.getDestY(), q.getDestPlane()), at, "ends at the destination " + where);
		assertEquals(r.getTicks(), total, "ticks add up " + where);
	}

	@Test
	void ringIsBoardedFromBesideAndSteppedOff()
	{
		Setup s = new Setup();
		s.w0.block(3210, 3210); // ring A's middle
		s.w1.block(3250, 3250); // ring B's middle, on plane 1 (only the ring leads there)
		s.build();
		List<Transport> t = List.of(ring("A>B", 3210, 3210, 0, 3250, 3250, 1, 5));
		Route r = both(s, q(3205, 3210, 0, 3255, 3250, 1, t));
		// walk 4 to the ring's west side, ride 5 + plane change 2, step off east 1, walk 4
		assertEquals(4 + 7 + 1 + 4, r.getTicks());
		assertEquals(3, r.getSteps().size());
		Route.Step ride = r.getSteps().get(1);
		assertEquals(Route.Step.Kind.TRANSPORT, ride.getKind());
		assertArrayEquals(new int[]{tile(3209, 3210, 0), tile(3250, 3250, 1)}, ride.getTiles(), "boarded beside, lands on the middle");
		Route.Step off = r.getSteps().get(2);
		assertEquals(tile(3250, 3250, 1), off.getTiles()[0], "the walk starts on the ring's middle");
		assertEquals(tile(3251, 3250, 1), off.getTiles()[1], "one step off it");
	}

	@Test
	void withoutTheMarkASolidStopStaysUnusable()
	{
		Setup s = new Setup();
		s.w0.block(3210, 3210);
		s.w1.block(3250, 3250);
		s.build();
		Transport plain = Transport.between("A>B", "A>B", 3210, 3210, 0, 3250, 3250, 1, 5, false, true);
		assertFalse(both(s, q(3205, 3210, 0, 3255, 3250, 1, List.of(plain))).isFound());
	}

	@Test
	void boardingIsFromTheFourSidesNotTheCorners()
	{
		Setup s = new Setup();
		s.w0.block(3210, 3210);
		s.w1.block(3250, 3250);
		s.build();
		List<Transport> t = List.of(ring("A>B", 3210, 3210, 0, 3250, 3250, 1, 5));
		int[][] at = PathfinderDiagnostics.besideTiles(s.pf, 3210, 3210, 0);
		assertEquals(4, at[0].length, "north, east, south, west");
		assertEquals(8, at[1].length, "steps off in all eight directions");
		// from the south-west corner one step to a side first
		Route r = both(s, q(3209, 3209, 0, 3250, 3251, 1, t));
		assertEquals(1 + 7 + 1, r.getTicks());
		int boarded = r.getSteps().get(1).getTiles()[0];
		assertTrue(boarded == tile(3209, 3210, 0) || boarded == tile(3210, 3209, 0), "boarded from a side");
		// from a side: no walk at all
		r = both(s, q(3211, 3210, 0, 3250, 3251, 1, t));
		assertEquals(7 + 1, r.getTicks());
		assertEquals(Route.Step.Kind.TRANSPORT, r.getSteps().get(0).getKind());
	}

	@Test
	void aWallBetweenTheRingAndATileStopsBoardingAndSteppingOffThere()
	{
		Setup s = new Setup();
		s.w0.block(3210, 3210);
		s.w0.wallEast(3209, 3210); // wall between the west side and the ring
		s.w1.block(3250, 3250);
		s.w1.wallEast(3250, 3250); // wall between the ring and its east side
		s.build();
		int[][] a = PathfinderDiagnostics.besideTiles(s.pf, 3210, 3210, 0);
		assertEquals(3, a[0].length);
		assertFalse(Arrays.stream(a[0]).anyMatch(n -> n == tile(3209, 3210, 0)), "not through the wall");
		int[][] b = PathfinderDiagnostics.besideTiles(s.pf, 3250, 3250, 1);
		assertFalse(Arrays.stream(b[1]).anyMatch(n -> n == tile(3251, 3250, 1)), "no step off through the wall");
		assertFalse(Arrays.stream(b[1]).anyMatch(n -> n == tile(3251, 3251, 1)), "nor diagonally past it");
		assertFalse(Arrays.stream(b[1]).anyMatch(n -> n == tile(3251, 3249, 1)), "nor diagonally past it");
		assertEquals(5, b[1].length);
		List<Transport> t = List.of(ring("A>B", 3210, 3210, 0, 3250, 3250, 1, 5));
		// from the west: round to the north (or south) side; at the far end round the wall too
		Route r = both(s, q(3208, 3210, 0, 3252, 3250, 1, t));
		assertEquals(2 + 7 + 1 + 2, r.getTicks());
	}

	@Test
	void diagonalStepOffNeedsBothWaysRoundTheCornerOpen()
	{
		Setup s = new Setup();
		s.w0.block(3210, 3210);
		s.w1.block(3250, 3250);
		s.w1.block(3251, 3250); // east of the ring: solid
		s.build();
		int[] off = PathfinderDiagnostics.besideTiles(s.pf, 3250, 3250, 1)[1];
		assertFalse(Arrays.stream(off).anyMatch(n -> n == tile(3251, 3251, 1)), "north-east: the east tile is solid");
		assertFalse(Arrays.stream(off).anyMatch(n -> n == tile(3251, 3249, 1)), "south-east: the east tile is solid");
		assertTrue(Arrays.stream(off).anyMatch(n -> n == tile(3249, 3251, 1)), "north-west is open");
		assertEquals(5, off.length);
	}

	@Test
	void anEndThatCanBeWalkedOnIsUsedAsUsual()
	{
		Setup s = new Setup();
		s.w1.block(3250, 3250);
		s.build();
		assertEquals(0, PathfinderDiagnostics.besideTiles(s.pf, 3210, 3210, 0)[0].length, "an open tile has no boarding tiles");
		List<Transport> marked = List.of(ring("A>B", 3210, 3210, 0, 3250, 3251, 1, 5));
		List<Transport> plain = List.of(Transport.between("A>B", "A>B", 3210, 3210, 0, 3250, 3251, 1, 5, false, true));
		Route a = both(s, q(3205, 3212, 0, 3255, 3251, 1, marked));
		Route b = both(s, q(3205, 3212, 0, 3255, 3251, 1, plain));
		assertEquals(b.getTicks(), a.getTicks());
		assertEquals(b.getSteps().size(), a.getSteps().size());
		for (int i = 0; i < a.getSteps().size(); i++)
		{
			assertArrayEquals(b.getSteps().get(i).getTiles(), a.getSteps().get(i).getTiles());
		}
	}

	@Test
	void transportsOnTheSameSolidTileStillChain()
	{
		// as the house: a ring ride lands on a solid tile with nothing to step off to, and a ride from that tile goes on
		Setup s = new Setup();
		s.w0.block(3210, 3210);
		for (int x = 3289; x <= 3291; x++)
		{
			for (int y = 3289; y <= 3291; y++)
			{
				s.w1.block(x, y); // the "house": a solid spot, solid all round
			}
		}
		s.build();
		List<Transport> t = List.of(ring("A>house", 3210, 3210, 0, 3290, 3290, 1, 5),
			Transport.between("house>C", "house>C", 3290, 3290, 1, 3220, 3260, 0, 4, false, true));
		Route r = both(s, q(3209, 3210, 0, 3222, 3260, 0, t));
		assertEquals(7 + 6 + 2, r.getTicks());
		assertEquals(0, PathfinderDiagnostics.besideTiles(s.pf, 3290, 3290, 1)[1].length, "nothing beside the house spot to step off to");
	}

	@Test
	void aPlayerStandingInTheRingStepsOff()
	{
		Setup s = new Setup();
		s.w0.block(3210, 3210);
		s.w1.block(3250, 3250);
		s.build();
		List<Transport> t = List.of(ring("A>B", 3210, 3210, 0, 3250, 3250, 1, 5));
		Route r = both(s, q(3250, 3250, 1, 3253, 3250, 1, t)); // just arrived by ring
		assertEquals(3, r.getTicks());
	}

	@Test
	void fastSearchAgreesWithBruteForceOnRandomWorldsWithRings()
	{
		Random rnd = new Random(42);
		for (int world = 0; world < 12; world++)
		{
			Setup s = new Setup();
			for (int i = 0; i < 3000; i++)
			{
				int x = X0 + rnd.nextInt(SIZE);
				int y = Y0 + rnd.nextInt(SIZE);
				World w = rnd.nextBoolean() ? s.w0 : s.w1;
				int k = rnd.nextInt(4);
				if (k == 0)
				{
					w.block(x, y);
				}
				else if (k == 1)
				{
					w.wallEast(x, y);
				}
				else
				{
					w.wallNorth(x, y);
				}
			}
			int[][] rings = new int[8][];
			for (int i = 0; i < rings.length; i++)
			{
				int p = i % 2;
				rings[i] = new int[]{X0 + 5 + rnd.nextInt(SIZE - 10), Y0 + 5 + rnd.nextInt(SIZE - 10), p};
				(p == 0 ? s.w0 : s.w1).block(rings[i][0], rings[i][1]);
			}
			s.build();
			List<Transport> t = new ArrayList<>();
			for (int[] a : rings)
			{
				for (int[] b : rings)
				{
					if (a != b)
					{
						t.add(ring(Arrays.toString(a) + ">" + Arrays.toString(b), a[0], a[1], a[2], b[0], b[1], b[2], 5));
					}
				}
			}
			for (int n = 0; n < 15; n++)
			{
				int[] a = rings[rnd.nextInt(rings.length)];
				int[] b = rings[rnd.nextInt(rings.length)];
				// near one ring to near another (often across planes: only the rings connect them)
				int sx = a[0] + rnd.nextInt(9) - 4;
				int sy = a[1] + rnd.nextInt(9) - 4;
				int dx = b[0] + rnd.nextInt(9) - 4;
				int dy = b[1] + rnd.nextInt(9) - 4;
				both(s, q(sx, sy, a[2], dx, dy, b[2], t));
			}
		}
	}
}
