package com.bestpath.core.sea;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The stop order: nearest first, then improved; one-way costs for stops that end elsewhere. */
class StopOrderTest
{
	/** Costs between points (straight lines); stop s starts at starts[s] and ends at ends[s]; start is point 0. */
	private static double[][] costs(double[][] start, double[][] starts, double[][] ends)
	{
		int n = starts.length;
		double[][] c = new double[n + 1][n + 1];
		for (int a = 0; a <= n; a++)
		{
			double[] from = a == 0 ? start[0] : ends[a - 1];
			for (int b = 1; b <= n; b++)
			{
				c[a][b] = Math.hypot(from[0] - starts[b - 1][0], from[1] - starts[b - 1][1]);
			}
		}
		return c;
	}

	private static double[][] points(double... xy)
	{
		double[][] p = new double[xy.length / 2][];
		for (int i = 0; i < p.length; i++)
		{
			p[i] = new double[]{xy[2 * i], xy[2 * i + 1]};
		}
		return p;
	}

	@Test
	void theLonerNearTheStartIsNotLeftForLast()
	{
		// start at 0; a loner at x = -12 just behind the start; a row of stops eastward from 10 to 100. Nearest first
		// goes east (10 is nearer than 12) and comes all the way back for the loner; the plan does the loner first.
		double[][] stops = points(-12, 0, 10, 0, 20, 0, 30, 0, 40, 0, 50, 0, 60, 0, 70, 0, 80, 0, 90, 0, 100, 0);
		double[][] c = costs(points(0, 0), stops, stops);
		int[] near = StopOrder.nearestFirst(c);
		assertEquals(0, near[near.length - 1], "nearest first leaves the loner for last");
		int[] plan = StopOrder.plan(c);
		assertEquals(0, plan[0], "the plan picks the loner up first");
		assertEquals(12 + 112, StopOrder.length(plan, c), 1e-9);
		assertTrue(StopOrder.length(plan, c) < StopOrder.length(near, c));
	}

	@Test
	void aStopThatEndsElsewhereIsLeftFromWhereItEnds()
	{
		// stop 0 starts beside the start but ends far east, next to stop 2; stop 1 is west. From the start: going
		// west first and then doing stop 0 (which carries the player east) and stop 2 beats doing stop 0 first.
		double[][] starts = points(2, 0, -30, 0, 102, 0);
		double[][] ends = points(100, 0, -30, 0, 102, 0);
		double[][] c = costs(points(0, 0), starts, ends);
		int[] plan = StopOrder.plan(c);
		assertArrayEquals(new int[]{1, 0, 2}, plan);
		assertEquals(30 + 32 + 2, StopOrder.length(plan, c), 1e-9);
	}

	@Test
	void neverLongerThanNearestFirstAndAlwaysTheSame()
	{
		Random r = new Random(7);
		for (int round = 0; round < 20; round++)
		{
			int n = 5 + r.nextInt(60);
			double[][] stops = new double[n][];
			for (int i = 0; i < n; i++)
			{
				stops[i] = new double[]{r.nextDouble() * 1000, r.nextDouble() * 1000};
			}
			double[][] c = costs(points(500, 500), stops, stops);
			int[] plan = StopOrder.orderShaken(c, StopOrder.IDLE_SHAKES, StopOrder.MAX_SHAKES, Long.MAX_VALUE);
			assertTrue(StopOrder.length(plan, c) <= StopOrder.length(StopOrder.nearestFirst(c), c) + 1e-9);
			int[] sorted = plan.clone();
			Arrays.sort(sorted);
			for (int i = 0; i < n; i++)
			{
				assertEquals(i, sorted[i], "every stop once");
			}
			assertArrayEquals(plan, StopOrder.orderShaken(c, StopOrder.IDLE_SHAKES, StopOrder.MAX_SHAKES, Long.MAX_VALUE));
		}
	}

	@Test
	void fewStops()
	{
		assertArrayEquals(new int[0], StopOrder.plan(new double[1][1]));
		double[][] one = costs(points(0, 0), points(5, 5), points(5, 5));
		assertArrayEquals(new int[]{0}, StopOrder.plan(one));
		double[][] two = costs(points(0, 0), points(10, 0, 5, 0), points(10, 0, 5, 0));
		assertArrayEquals(new int[]{1, 0}, StopOrder.plan(two));
	}
}
