package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.graph.CoarseGraph;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Walking tables worked out ahead of time ({@link WalkTables}): with them a search gives exactly
 * the same routes as without, on random worlds with doors, ladders, solid-ended rides and teleports; the file reads
 * back the same; tables made for another map are not used.
 */
class WalkTablesTest
{
	private static final int PENALTY = 2;

	private static int[] walkable(CollisionMap map, Random rnd)
	{
		while (true)
		{
			int x = TestWorld.X0 + rnd.nextInt(TestWorld.SIZE);
			int y = TestWorld.Y0 + rnd.nextInt(TestWorld.SIZE);
			for (int d = 0; d < 8; d++)
			{
				if (map.canStep(x, y, 0, Beside.DX[d], Beside.DY[d]))
				{
					return new int[]{x, y};
				}
			}
		}
	}

	/** Random transports: two-way and one-way walks between walkable tiles, rides from solid tiles, teleports. */
	private static List<Transport> transports(TestWorld w, CollisionMap map, Random rnd)
	{
		List<Transport> out = new ArrayList<>();
		for (int i = 0; i < 40; i++)
		{
			int[] a = walkable(map, rnd);
			int[] b = walkable(map, rnd);
			out.add(Transport.between("t" + i, "t" + i, a[0], a[1], 0, b[0], b[1], 0, 1 + rnd.nextInt(8), rnd.nextBoolean(), true));
		}
		for (int i = 0; i < 6; i++)
		{
			// a ride between solid middles (as fairy rings): the tile itself is blocked, used from beside it
			int[] a = walkable(map, rnd);
			int[] b = walkable(map, rnd);
			w.block(a[0], a[1]);
			w.block(b[0], b[1]);
			out.add(Transport.between("r" + i, "r" + i, a[0], a[1], 0, b[0], b[1], 0, 3, true, true).usedFromBeside());
		}
		for (int i = 0; i < 4; i++)
		{
			int[] a = walkable(map, rnd);
			out.add(Transport.teleport("p" + i, "p" + i, a[0], a[1], 0, 5, true));
		}
		return out;
	}

	@Test
	void tablesKeepEveryRouteTheSame() throws Exception
	{
		for (long seed = 1; seed <= 6; seed++)
		{
			Random rnd = new Random(seed);
			TestWorld w = TestWorld.random(rnd, 1 + (int) (seed % 3));
			List<Transport> ts = transports(w, w.map(), rnd);
			CollisionMap map = w.map(); // with the rides' solid tiles
			DoorMap doors = w.doorMap();
			Pathfinder plain = new Pathfinder(map, CoarseGraph.build(map), (x, y, p) -> p == 0 ? w.get(x, y) : 0);
			Pathfinder maker = new Pathfinder(map, CoarseGraph.build(map), (x, y, p) -> p == 0 ? w.get(x, y) : 0);
			WalkTables made = WalkTablesBuilder.build(maker, ts);
			ByteArrayOutputStream file = new ByteArrayOutputStream();
			WalkTablesWriter.write(made, file);
			WalkTables tables = WalkTables.read(new ByteArrayInputStream(file.toByteArray()));
			ByteArrayOutputStream again = new ByteArrayOutputStream();
			WalkTablesWriter.write(tables, again);
			assertArrayEquals(file.toByteArray(), again.toByteArray(), "the file reads back the same, seed " + seed);

			Pathfinder shipped = new Pathfinder(map, CoarseGraph.build(map), (x, y, p) -> p == 0 ? w.get(x, y) : 0);
			assertTrue(shipped.useWalkTables(tables), "made for this map, seed " + seed);
			for (int i = 0; i < 60; i++)
			{
				int[] a = walkable(map, rnd);
				int[] b = walkable(map, rnd);
				// half the routes with some of the transports only (another player's), always from empty caches
				List<Transport> some = i % 2 == 0 ? ts : ts.subList(0, ts.size() / 2);
				PathQuery q = new PathQuery(a[0], a[1], 0, b[0], b[1], 0, some, PENALTY, doors, 2);
				plain.clearCaches();
				shipped.clearCaches();
				Route r0 = plain.find(q);
				Route r1 = shipped.find(q);
				String where = "seed " + seed + " route " + i;
				assertEquals(r0.isFound(), r1.isFound(), where);
				assertEquals(r0.getTicks(), r1.getTicks(), where);
				assertEquals(r0.getSteps().size(), r1.getSteps().size(), where);
				for (int k = 0; k < r0.getSteps().size(); k++)
				{
					assertArrayEquals(r0.getSteps().get(k).getTiles(), r1.getSteps().get(k).getTiles(), where + " step " + k);
				}
			}
		}
	}

	@Test
	void tablesForAnotherMapAreNotUsed()
	{
		Random rnd = new Random(11);
		TestWorld a = TestWorld.random(rnd, 2);
		TestWorld b = TestWorld.random(rnd, 2);
		CollisionMap ma = a.map();
		CollisionMap mb = b.map();
		Pathfinder pa = new Pathfinder(ma, CoarseGraph.build(ma), null);
		WalkTables made = WalkTablesBuilder.build(pa, transports(a, ma, rnd));
		Pathfinder pb = new Pathfinder(mb, CoarseGraph.build(mb), null);
		assertFalse(pb.useWalkTables(made), "another map's sub-regions");
		assertFalse(pb.useWalkTables(null));
	}
}
