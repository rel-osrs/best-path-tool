package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What steps cost (the movement rules): one tick per tile in any of the 8 directions, a step through
 * a door or gate costs the door cost extra (2 ticks), a transport between planes adds the plane-change
 * penalty (2 ticks) and nothing on the same plane or for a teleport; and a free-world map keeps routes
 * on free land. Small hand-made worlds; every total is also checked against the brute-force checker.
 */
class StepCostTest
{
	private static final int DOOR_COST = 2;
	private static final int PLANE_PENALTY = 2;

	/** Regions (50..51, 50..51): x 3200-3327, y 3200-3327; everything else blocked. */
	private static final class World implements MovementRules.Flags
	{
		final Map<Long, Integer> flags = new HashMap<>();

		World set(int x, int y, int f)
		{
			flags.merge((long) x << 32 | y, f, (p, q) -> p | q);
			return this;
		}

		World wallEast(int x, int y)
		{
			return set(x, y, CollisionFlags.EAST).set(x + 1, y, CollisionFlags.WEST);
		}

		World wallNorth(int x, int y)
		{
			return set(x, y, CollisionFlags.NORTH).set(x, y + 1, CollisionFlags.SOUTH);
		}

		World copy()
		{
			World w = new World();
			w.flags.putAll(flags);
			return w;
		}

		@Override
		public int get(int x, int y)
		{
			boolean inside = x >= 3200 && x < 3328 && y >= 3200 && y < 3328;
			return inside ? flags.getOrDefault((long) x << 32 | y, 0) : CollisionFlags.BLOCKED;
		}
	}

	private static final MovementRules.Flags NONE = (x, y) -> CollisionFlags.BLOCKED;

	private static CollisionMap map(MovementRules.Flags p0, MovementRules.Flags p1)
	{
		List<Integer> regions = new ArrayList<>();
		for (int rx = 50; rx <= 51; rx++)
		{
			for (int ry = 50; ry <= 51; ry++)
			{
				regions.add(rx << 8 | ry);
			}
		}
		return CollisionMapBuilder.build(new MovementRules.Flags[]{p0, p1, NONE, NONE}, regions, 0, 0);
	}

	/** The pathfinder's ticks, after checking the brute-force checker agrees; -1 if no route. */
	private static int ticks(CollisionMap m, PathQuery q)
	{
		Route fast = new Pathfinder(m, CoarseGraph.build(m), null).find(q);
		Route brute = BruteForce.find(m, q);
		assertEquals(brute.isFound(), fast.isFound(), "found?");
		assertEquals(brute.getTicks(), fast.getTicks(), "same ticks as the checker");
		int sum = 0;
		for (Route.Step s : fast.getSteps())
		{
			sum += s.getTicks();
		}
		if (fast.isFound())
		{
			assertEquals(fast.getTicks(), sum, "steps add up");
		}
		return fast.getTicks();
	}

	/** A north-south fence east of x = 3215 (y 3200-3327, the whole map) with gates at the given y. */
	private static World[] fence(int... gates)
	{
		World open = new World();
		World shut = new World();
		for (int y = 3200; y < 3328; y++)
		{
			shut.wallEast(3215, y);
			boolean gate = false;
			for (int g : gates)
			{
				gate |= g == y;
			}
			if (!gate)
			{
				open.wallEast(3215, y);
			}
		}
		return new World[]{open, shut};
	}

	private static DoorMap doors(World open, World shut, int... gates)
	{
		List<int[]> tiles = new ArrayList<>();
		for (int g : gates)
		{
			tiles.add(new int[]{3215, g, 0});
		}
		return DoorMapBuilder.build(new MovementRules.Flags[]{open, NONE, NONE, NONE}, new MovementRules.Flags[]{shut, NONE, NONE, NONE}, tiles);
	}

	// ---- walking ----

	@Test
	void everyDirectionCostsOneTickPerTile()
	{
		CollisionMap m = map(new World(), NONE);
		for (int[] d : new int[][]{{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}})
		{
			assertEquals(7, ticks(m, new PathQuery(3250, 3250, 0, 3250 + 7 * d[0], 3250 + 7 * d[1], 0, List.of(), PLANE_PENALTY)),
				"7 tiles towards " + d[0] + "," + d[1]);
		}
		// a knight's-move shape: max(dx, dy) ticks (diagonal steps are as cheap as straight ones)
		assertEquals(12, ticks(m, new PathQuery(3250, 3250, 0, 3262, 3255, 0, List.of(), PLANE_PENALTY)));
	}

	@Test
	void cornerCuttingIsNotAShortcut()
	{
		// (3250,3250) -> (3251,3251) with an object east of the start: the diagonal would cut past it, so north then east
		World w = new World().set(3251, 3250, CollisionFlags.OBJECT);
		assertEquals(2, ticks(map(w, NONE), new PathQuery(3250, 3250, 0, 3251, 3251, 0, List.of(), PLANE_PENALTY)));
		// objects on both sides of the diagonal: no squeezing between them; round one of them (6 ticks, not 1)
		World both = new World().set(3251, 3250, CollisionFlags.OBJECT).set(3250, 3251, CollisionFlags.OBJECT);
		assertEquals(6, ticks(map(both, NONE), new PathQuery(3250, 3250, 0, 3251, 3251, 0, List.of(), PLANE_PENALTY)));
		// walls on both sides of the diagonal (an inside corner): the same
		World corner = new World().wallNorth(3250, 3250).wallEast(3250, 3250);
		assertEquals(4, ticks(map(corner, NONE), new PathQuery(3250, 3250, 0, 3251, 3251, 0, List.of(), PLANE_PENALTY)),
			"out round the corner: west/south, then round");
	}

	// ---- doors ----

	@Test
	void aDoorStepCostsTwoTicksExtra()
	{
		World[] f = fence(3220);
		DoorMap doors = doors(f[0], f[1], 3220);
		CollisionMap m = map(f[0], NONE);
		// straight through the gate: 10 steps + 2
		assertEquals(10 + DOOR_COST, ticks(m, new PathQuery(3210, 3220, 0, 3220, 3220, 0, List.of(), PLANE_PENALTY, doors, DOOR_COST)));
		// and back the same
		assertEquals(10 + DOOR_COST, ticks(m, new PathQuery(3220, 3220, 0, 3210, 3220, 0, List.of(), PLANE_PENALTY, doors, DOOR_COST)));
		// with no door cost, the same walk is just its steps
		assertEquals(10, ticks(m, new PathQuery(3210, 3220, 0, 3220, 3220, 0, List.of(), PLANE_PENALTY, doors, 0)));
		// a walk that does not use the gate costs nothing extra
		assertEquals(5, ticks(m, new PathQuery(3216, 3220, 0, 3221, 3220, 0, List.of(), PLANE_PENALTY, doors, DOOR_COST)));
		// from off the gate's row: diagonal steps up to the gate, through it, on: max(10, 4) + 2
		assertEquals(10 + DOOR_COST, ticks(m, new PathQuery(3210, 3216, 0, 3220, 3220, 0, List.of(), PLANE_PENALTY, doors, DOOR_COST)));
	}

	@Test
	void twoDoorsCostTwiceAndWalkCostCountsEachCrossing()
	{
		// a second fence east of x = 3225 with a gate at y = 3220
		World[] f = fence(3220);
		World open = f[0].copy();
		World shut = f[1].copy();
		for (int y = 3200; y < 3328; y++)
		{
			shut.wallEast(3225, y);
			if (y != 3220)
			{
				open.wallEast(3225, y);
			}
		}
		List<int[]> gates = List.of(new int[]{3215, 3220, 0}, new int[]{3225, 3220, 0});
		DoorMap doors = DoorMapBuilder.build(new MovementRules.Flags[]{open, NONE, NONE, NONE}, new MovementRules.Flags[]{shut, NONE, NONE, NONE}, gates);
		CollisionMap m = map(open, NONE);
		assertEquals(20 + 2 * DOOR_COST, ticks(m, new PathQuery(3210, 3220, 0, 3230, 3220, 0, List.of(), PLANE_PENALTY, doors, DOOR_COST)));

		int[] walk = new int[21];
		for (int i = 0; i <= 20; i++)
		{
			walk[i] = Tiles.pack(3210 + i, 3220, 0);
		}
		assertEquals(20 + 2 * DOOR_COST, WalkCost.cost(walk, doors, DOOR_COST));
		assertEquals(20, WalkCost.cost(walk, doors, 0));
		assertEquals(20, WalkCost.cost(walk, DoorMap.none(), DOOR_COST));
		assertEquals(0, WalkCost.cost(new int[]{walk[0]}, doors, DOOR_COST));
	}

	@Test
	void doorCrossingsAreTheGateStepOnlyBothWays()
	{
		World[] f = fence(3220);
		DoorMap doors = doors(f[0], f[1], 3220);
		int w = Tiles.pack(3215, 3220, 0);
		int e = Tiles.pack(3216, 3220, 0);
		assertTrue(doors.crosses(w, e));
		assertTrue(doors.crosses(e, w));
		// along the fence, beside the gate, or away from it: not a door step
		assertFalse(doors.crosses(w, Tiles.pack(3215, 3221, 0)));
		assertFalse(doors.crosses(Tiles.pack(3214, 3220, 0), w));
		assertFalse(doors.crosses(e, Tiles.pack(3217, 3220, 0)));
		assertFalse(doors.crosses(Tiles.pack(3215, 3221, 0), Tiles.pack(3216, 3221, 0)), "the fence next to the gate is shut both ways");
		// a diagonal through the gate is blocked by the fence on either side, so it is no door step either
		assertFalse(doors.crosses(Tiles.pack(3215, 3219, 0), e));
		assertTrue(DoorMap.none().isEmpty());
		assertFalse(doors.isEmpty());
		// no doors: the query drops the door cost
		assertEquals(0, new PathQuery(1, 1, 0, 2, 2, 0, List.of(), PLANE_PENALTY, DoorMap.none(), DOOR_COST).getDoorCost());
		assertEquals(DOOR_COST, new PathQuery(1, 1, 0, 2, 2, 0, List.of(), PLANE_PENALTY, doors, DOOR_COST).getDoorCost());
	}

	// ---- plane changes ----

	@Test
	void transportCostAddsThePenaltyOnlyBetweenPlanes()
	{
		Transport ladder = Transport.between("l", "Ladder", 3215, 3210, 0, 3215, 3210, 1, 2, true, true);
		Transport stile = Transport.between("s", "Stile", 3215, 3210, 0, 3217, 3210, 0, 3, true, true);
		Transport tele = Transport.teleport("t", "Teleport", 3215, 3210, 1, 5, true);
		assertEquals(2 + PLANE_PENALTY, ladder.cost(PLANE_PENALTY));
		assertEquals(2 + 7, ladder.cost(7));
		assertEquals(2, ladder.cost(0));
		assertEquals(3, stile.cost(PLANE_PENALTY), "same plane: no penalty");
		assertEquals(5, tele.cost(PLANE_PENALTY), "teleports: no penalty, whatever the plane");
	}

	@Test
	void routesPayThePenaltyForEachPlaneChange()
	{
		CollisionMap m = map(new World(), new World());
		Transport ladder = Transport.between("l", "Ladder", 3215, 3210, 0, 3215, 3210, 1, 2, true, true);
		for (int penalty : new int[]{0, 2, 5})
		{
			// walk 5, climb 2 + penalty, walk 5
			assertEquals(12 + penalty, ticks(m, new PathQuery(3210, 3210, 0, 3220, 3210, 1, List.of(ladder), penalty)), "penalty " + penalty);
		}
		// up and back down (a wall on plane 0 east of x = 3220 forces it): two plane changes
		World walled = new World();
		for (int y = 3200; y < 3328; y++)
		{
			walled.wallEast(3220, y);
		}
		CollisionMap two = map(walled, new World());
		Transport down = Transport.between("d", "Stairs", 3225, 3210, 1, 3225, 3210, 0, 2, false, true);
		assertEquals(5 + (2 + PLANE_PENALTY) + 10 + (2 + PLANE_PENALTY) + 5,
			ticks(two, new PathQuery(3210, 3210, 0, 3230, 3210, 0, List.of(ladder, down), PLANE_PENALTY)));
	}

	@Test
	void sameplaneTransportsAndTeleportsPayNoPenalty()
	{
		// a wall from y 3200 to 3327 east of x = 3215 with a stile over it
		World w = new World();
		for (int y = 3200; y < 3328; y++)
		{
			w.wallEast(3215, y);
		}
		CollisionMap m = map(w, new World());
		Transport stile = Transport.between("s", "Stile", 3215, 3220, 0, 3216, 3220, 0, 3, true, true);
		assertEquals(5 + 3 + 4, ticks(m, new PathQuery(3210, 3220, 0, 3220, 3220, 0, List.of(stile), 50)));
		Transport tele = Transport.teleport("t", "Teleport", 3300, 3300, 1, 5, true);
		assertEquals(5 + 3, ticks(m, new PathQuery(3210, 3220, 0, 3303, 3300, 1, List.of(tele), 50)));
	}

	// ---- free-to-play worlds (members on free land) ----

	@Test
	void aFreeWorldMapKeepsRoutesOnFreeLand()
	{
		CollisionMap full = map(new World(), NONE);
		// free: everything west of x = 3240, everything east of x = 3299, and a strip along the south edge (y 3200-3202)
		// joining them; members land: x 3240-3299, y 3203-3327
		FreeLand land = new FreeLand.Builder().set(3200, 3200, 3239, 3327, true).set(3240, 3200, 3299, 3202, true)
			.set(3300, 3200, 3327, 3327, true).build();
		CollisionMap free = full.restrictTo((x, y, p) -> land.isFree(x, y));
		// members world: straight across
		assertEquals(40, ticks(full, new PathQuery(3230, 3250, 0, 3270, 3250, 0, List.of(), PLANE_PENALTY)));
		// free world: (3270, 3250) is members land: no route
		assertEquals(-1, ticks(free, new PathQuery(3230, 3250, 0, 3270, 3250, 0, List.of(), PLANE_PENALTY)));
		// a free destination east of the members block: round by the free strip in the south
		assertEquals(80, ticks(full, new PathQuery(3230, 3250, 0, 3310, 3250, 0, List.of(), PLANE_PENALTY)));
		int round = ticks(free, new PathQuery(3230, 3250, 0, 3310, 3250, 0, List.of(), PLANE_PENALTY));
		assertTrue(round > 80, "the free world detours: " + round);
		for (Route.Step s : new Pathfinder(free, CoarseGraph.build(free), null).find(new PathQuery(3230, 3250, 0, 3310, 3250, 0, List.of(),
			PLANE_PENALTY)).getSteps())
		{
			for (int t : s.getTiles())
			{
				assertTrue(land.isFree(Tiles.x(t), Tiles.y(t)), "on free land: " + Tiles.x(t) + "," + Tiles.y(t));
			}
		}
	}
}
