package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;
import com.bestpath.core.sea.CoarseSea;
import com.bestpath.core.sea.StopOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The order to do a sea charting set's tasks in (a salesman-style tour that may use points on land; it need not be perfect,
 * but never joins two points separated by land). Planned once, without any
 * route search, by {@link StopOrder} over rough distances:
 * <ul>
 * <li>a task is entered where it starts and left where the player ends up ({@link ChartingTasks.Task#getEndPlace}: a
 * current duck's pick-up spot; a weather task comes back to the meteorologist);</li>
 * <li>two points join by the {@link CoarseSea} (water only, so never straight across land) from the main-ocean water
 * nearest each; a point on land within {@link CoarseSea#MAX_SNAP} tiles of the sea adds its straight line to that
 * water, never less than the straight line between the two points;</li>
 * <li>a point with no main ocean near (far inland, a cave) and two patches of water that don't join count the straight
 * line, three times for water that doesn't join (something else gets there, a teleport or a walk).</li>
 * </ul>
 * Each leg is still routed by the real route search when the player gets to it (teleports, walking, the boat).
 */
public final class ChartingOrder
{
	/** Water that doesn't join: the straight line times this. */
	static final double NOT_JOINED = 3;

	private ChartingOrder()
	{
	}

	/** One point to plan with: where it is, and its nearest water's node (-1: none near). */
	private static final class Point
	{
		final int x;
		final int y;
		final int node;

		Point(int x, int y, int node)
		{
			this.x = x;
			this.y = y;
			this.node = node;
		}
	}

	/**
	 * The tasks in the order to do them from the player's tile (packed, {@link Tiles}). Tasks without a place keep
	 * their place at the end.
	 */
	public static List<ChartingTasks.Task> plan(CoarseSea sea, int start, List<ChartingTasks.Task> tasks)
	{
		List<ChartingTasks.Task> placed = new ArrayList<>();
		List<ChartingTasks.Task> unplaced = new ArrayList<>();
		for (ChartingTasks.Task t : tasks)
		{
			(t.hasPlace() ? placed : unplaced).add(t);
		}
		int n = placed.size();
		Map<Integer, Point> points = new HashMap<>();
		Point[] from = new Point[n + 1];
		Point[] to = new Point[n + 1];
		from[0] = point(sea, start, points);
		to[0] = from[0];
		for (int i = 0; i < n; i++)
		{
			ChartingTasks.Task t = placed.get(i);
			from[i + 1] = point(sea, t.getEndPlace(), points);
			to[i + 1] = point(sea, t.getPlace(), points);
		}
		// one row at a time: a node's distances to every node (about 90 KB) are read for the row, then let go
		double[][] cost = new double[n + 1][n + 1];
		for (int a = 0; a <= n; a++)
		{
			double[] through = from[a].node < 0 ? null : sea.distancesFrom(from[a].node);
			for (int b = 1; b <= n; b++)
			{
				cost[a][b] = distance(sea, from[a], to[b], through);
			}
		}
		int[] order = StopOrder.plan(cost);
		List<ChartingTasks.Task> out = new ArrayList<>(tasks.size());
		for (int s : order)
		{
			out.add(placed.get(s));
		}
		out.addAll(unplaced);
		return out;
	}

	private static Point point(CoarseSea sea, int tile, Map<Integer, Point> cache)
	{
		return cache.computeIfAbsent(tile,
			t -> new Point(Tiles.x(t), Tiles.y(t), sea == null ? -1 : sea.nodeNear(Tiles.x(t), Tiles.y(t))));
	}

	/** The rough distance between two points in tiles (see the class notes); {@code fromA}: a's node's distances. */
	static double distance(CoarseSea sea, Point a, Point b, double[] fromA)
	{
		double line = Math.hypot(a.x - b.x, a.y - b.y);
		if (a.node < 0 || b.node < 0 || a.node == b.node)
		{
			return line;
		}
		double through = fromA[b.node];
		if (Double.isInfinite(through))
		{
			return line * NOT_JOINED;
		}
		double way = Math.hypot(a.x - sea.midX(a.node), a.y - sea.midY(a.node)) + through
			+ Math.hypot(b.x - sea.midX(b.node), b.y - sea.midY(b.node));
		return Math.max(line, way);
	}
}
