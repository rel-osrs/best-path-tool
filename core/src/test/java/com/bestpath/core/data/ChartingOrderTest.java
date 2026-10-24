package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.sea.CoarseSea;
import com.bestpath.core.sea.SeaGridBuilder;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The sea charting order: never straight across land, a duck left from its pick-up spot, points on land used. */
class ChartingOrderTest
{
	private static long key(int x, int y)
	{
		return (long) x << 32 | y;
	}

	/** 96 x 96 open water from 3200,3200 minus the land tiles. */
	private static CoarseSea sea(Set<Long> land)
	{
		SeaMap.Builder b = new SeaMap.Builder();
		for (int x = 3200; x < 3296; x++)
		{
			for (int y = 3200; y < 3296; y++)
			{
				if (!land.contains(key(x, y)))
				{
					b.set(x, y, 131);
				}
			}
		}
		SeaMap sea = b.build();
		MovementRules.Flags f = (x, y) -> land.contains(key(x, y)) ? CollisionFlags.FLOOR : 0;
		return CoarseSea.build(SeaGridBuilder.build(sea, SeaPieces.build(sea, f), f));
	}

	private static ChartingTasks.Task task(int number, String kind, int... xy)
	{
		int[] places = new int[xy.length / 2];
		for (int i = 0; i < places.length; i++)
		{
			places[i] = Tiles.pack(xy[2 * i], xy[2 * i + 1], 0);
		}
		return new ChartingTasks.Task(number, 18574 + number, kind, "Test Sea", "", 1, 10, "", "", false, places,
			"Task " + number, "", "", new int[0], -1, "");
	}

	private static List<Integer> numbers(List<ChartingTasks.Task> l)
	{
		List<Integer> out = new ArrayList<>();
		for (ChartingTasks.Task t : l)
		{
			out.add(t.getNumber());
		}
		return out;
	}

	@Test
	void twoPointsWithLandBetweenAreNotPutNextToEachOther()
	{
		// a wall of land at x = 3248 from the bottom up to y = 3280: 1 and 3 west of it, 2 and 4 east, 1 and 2 only 8
		// tiles apart in a straight line but about 150 by water (round the wall's top end)
		Set<Long> land = new HashSet<>();
		for (int y = 3200; y < 3280; y++)
		{
			land.add(key(3248, y));
		}
		CoarseSea sea = sea(land);
		List<ChartingTasks.Task> tasks = List.of(task(1, "generic", 3244, 3210), task(2, "generic", 3252, 3210),
			task(3, "generic", 3240, 3250), task(4, "generic", 3256, 3250));
		int start = Tiles.pack(3240, 3205, 0);
		List<Integer> plan = numbers(ChartingOrder.plan(sea, start, tasks));
		assertEquals(List.of(1, 3, 4, 2), plan, "the west side first, round the wall's end, then the east side");
		// straight lines alone would cross the wall
		List<Integer> straight = numbers(ChartingOrder.plan(null, start, tasks));
		assertEquals(2, straight.get(1));
	}

	@Test
	void theNextTaskAfterADuckIsFromWhereTheDuckIsPickedUp()
	{
		CoarseSea sea = sea(Set.of());
		// the duck is let go next to the start and picked up far east, beside task 3; task 2 is near the start
		List<ChartingTasks.Task> tasks = List.of(task(1, ChartingTasks.CURRENT, 3206, 3205, 3290, 3205),
			task(2, "generic", 3210, 3220), task(3, "generic", 3288, 3212));
		List<Integer> plan = numbers(ChartingOrder.plan(sea, Tiles.pack(3204, 3204, 0), tasks));
		assertEquals(List.of(2, 1, 3), plan);
	}

	@Test
	void aWeatherTaskEndsBackAtTheMeteorologist()
	{
		ChartingTasks.Task w = task(1, ChartingTasks.WEATHER, 3206, 3205, 3290, 3290);
		assertEquals(w.getPlace(), w.getEndPlace());
		assertEquals(Tiles.pack(3290, 3290, 0), w.getSecondPlace());
		ChartingTasks.Task d = task(2, ChartingTasks.CURRENT, 3206, 3205, 3290, 3205);
		assertEquals(Tiles.pack(3290, 3205, 0), d.getEndPlace());
	}

	@Test
	void aTaskOnLandIsPlannedFromItsNearestWater()
	{
		// an island from 3220 to 3229 east-west, 3260 to 3269 north-south, with a task in its middle
		Set<Long> land = new HashSet<>();
		for (int x = 3220; x < 3230; x++)
		{
			for (int y = 3260; y < 3270; y++)
			{
				land.add(key(x, y));
			}
		}
		CoarseSea sea = sea(land);
		List<ChartingTasks.Task> tasks = List.of(task(1, "generic", 3270, 3280), task(2, "generic", 3224, 3264),
			task(3, "generic", 3215, 3230), task(4, "generic", 3290, 3290));
		List<Integer> plan = numbers(ChartingOrder.plan(sea, Tiles.pack(3205, 3205, 0), tasks));
		assertEquals(List.of(3, 2, 1, 4), plan);
		assertTrue(sea.nodeNear(3224, 3264) >= 0);
	}
}
