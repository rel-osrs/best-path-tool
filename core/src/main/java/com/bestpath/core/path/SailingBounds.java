package com.bestpath.core.path;

import com.bestpath.core.sea.Dock;
import java.util.List;

/**
 * Lower bounds for sailing routes, worked out without any search: the least ticks from a route's start to each dock,
 * the least walk on from each dock to the destination, and the least a sea leg between two stretches of water can
 * cost. Each never says more than the real searches find, so whatever they rule out could never have been part of
 * the best route.
 */
final class SailingBounds
{
	private static final int NONE = Sailing.NONE;

	private SailingBounds()
	{
	}

	/**
	 * A lower bound of the ticks from the query's start to each dock's land
	 * tile, without a search: walking takes at least one tick per tile
	 * (Chebyshev distance, same plane only); a route that uses transports
	 * walks at least to the nearest place one starts from (nothing for a
	 * teleport) and, after the last one, from where it lands (same plane as
	 * the dock) to the dock, besides that transport's own ticks. Never more
	 * than the walking search's ticks, so a dock it rules out could not have
	 * been used.
	 */
	static int[] lowerBounds(PathQuery q, List<int[]> land)
	{
		int sx = q.getStartX();
		int sy = q.getStartY();
		int sp = q.getStartPlane();
		int penalty = q.getPlaneChangePenalty();
		List<Transport> tr = q.getTransports();
		int first = NONE;
		for (Transport t : tr)
		{
			if (!t.hasStart())
			{
				first = 0;
				break;
			}
			int off = t.isUsedFromBeside() ? 1 : 0; // boarded from beside its end: one tile less to walk
			first = Math.min(first, Math.max(0, cheb(sx, sy, t.getStartX(), t.getStartY()) - off));
			if (t.isTwoWay())
			{
				first = Math.min(first, Math.max(0, cheb(sx, sy, t.getEndX(), t.getEndY()) - off));
			}
		}
		int[] out = new int[land.size()];
		for (int k = 0; k < out.length; k++)
		{
			int[] d = land.get(k);
			int lb = d[2] == sp ? cheb(sx, sy, d[0], d[1]) : NONE;
			int last = NONE;
			for (Transport t : tr)
			{
				int c = t.cost(penalty);
				if (t.getEndPlane() == d[2])
				{
					last = Math.min(last, c + cheb(t.getEndX(), t.getEndY(), d[0], d[1]));
				}
				if (t.isTwoWay() && t.hasStart() && t.getStartPlane() == d[2])
				{
					last = Math.min(last, c + cheb(t.getStartX(), t.getStartY(), d[0], d[1]));
				}
			}
			if (first < NONE && last < NONE)
			{
				lb = Math.min(lb, first + last);
			}
			out[k] = lb;
		}
		return out;
	}

	/**
	 * A lower bound of the ticks from each dock's land tile on to the query's destination, without a search (the
	 * other way round to {@link #lowerBounds}): walking takes at least one tick per tile (same plane only); a way
	 * with transports walks at least to the nearest place one starts from (nothing when a teleport can be used) and
	 * from the last one's landing (on the destination's plane) to the destination, besides that transport's own
	 * ticks. {@link #NONE} when even that can't reach it. Never more than the walking search's ticks from there.
	 */
	static int[] lowerBoundsOn(PathQuery q, List<int[]> land)
	{
		int dx = q.getDestX();
		int dy = q.getDestY();
		int dp = q.getDestPlane();
		int penalty = q.getPlaneChangePenalty();
		List<Transport> tr = q.getTransports();
		boolean teleport = false;
		int last = NONE;
		for (Transport t : tr)
		{
			teleport |= !t.hasStart();
			int c = t.cost(penalty);
			if (t.getEndPlane() == dp)
			{
				last = Math.min(last, c + cheb(t.getEndX(), t.getEndY(), dx, dy));
			}
			if (t.isTwoWay() && t.hasStart() && t.getStartPlane() == dp)
			{
				last = Math.min(last, c + cheb(t.getStartX(), t.getStartY(), dx, dy));
			}
		}
		int[] out = new int[land.size()];
		for (int k = 0; k < out.length; k++)
		{
			int[] d = land.get(k);
			int lb = d[2] == dp ? cheb(d[0], d[1], dx, dy) : NONE;
			if (last < NONE)
			{
				int first = teleport ? 0 : NONE;
				for (int i = 0; i < tr.size() && first > 0; i++)
				{
					Transport t = tr.get(i);
					int off = t.isUsedFromBeside() ? 1 : 0;
					if (t.hasStart() && t.getStartPlane() == d[2])
					{
						first = Math.min(first, Math.max(0, cheb(d[0], d[1], t.getStartX(), t.getStartY()) - off));
					}
					if (t.isTwoWay() && t.getEndPlane() == d[2])
					{
						first = Math.min(first, Math.max(0, cheb(d[0], d[1], t.getEndX(), t.getEndY()) - off));
					}
				}
				if (first < NONE)
				{
					lb = Math.min(lb, first + last);
				}
			}
			out[k] = lb;
		}
		return out;
	}

	/**
	 * The least a sea leg from water A to water B can cost (hundredths of a tick, boarding and leaving not included) at
	 * {@code perTile} (hundredths per tile, less a little for rounding): the straight distance between them when they
	 * are in one graph piece, or through boat-only transports ({@code ls}) each at the straight distance to its start,
	 * its ticks, and on from its end; each stretch less one hundredth (rounded on its own). {@link #FAR} when no way.
	 * Never more than the sea search can find.
	 */
	static long leastSail(int[] a, int[] pa, int[] b, int[] pb, double perTile, List<SeaLink> ls)
	{
		long best = SailingWorld.shares(pa, pb) ? stretch(a, b, perTile) : FAR;
		int n = ls.size();
		if (n == 0)
		{
			return best;
		}
		long[] d = new long[n];
		boolean[] done = new boolean[n];
		for (int l = 0; l < n; l++)
		{
			d[l] = SailingWorld.shares(pa, ls.get(l).inPieces) ? stretch(a, ls.get(l).inBox, perTile) : FAR;
		}
		for (int round = 0; round < n; round++)
		{
			int l = -1;
			for (int i = 0; i < n; i++)
			{
				if (!done[i] && d[i] < FAR && (l < 0 || d[i] < d[l]))
				{
					l = i;
				}
			}
			if (l < 0 || d[l] >= best)
			{
				break;
			}
			done[l] = true;
			SeaLink k = ls.get(l);
			long at = d[l] + k.cost;
			if (SailingWorld.shares(k.outPieces, pb))
			{
				best = Math.min(best, at + stretch(k.outBox, b, perTile));
			}
			for (int i = 0; i < n; i++)
			{
				if (!done[i] && SailingWorld.shares(k.outPieces, ls.get(i).inPieces))
				{
					d[i] = Math.min(d[i], at + stretch(k.outBox, ls.get(i).inBox, perTile));
				}
			}
		}
		return best;
	}

	/** No way at all (for {@link #leastSail}). */
	static final long FAR = Long.MAX_VALUE / 4;

	/** The least one stretch of sailing between two boxes costs: the straight distance, less one hundredth. */
	private static long stretch(int[] a, int[] b, double perTile)
	{
		int dx = Math.max(0, Math.max(b[0] - a[2], a[0] - b[2]));
		int dy = Math.max(0, Math.max(b[1] - a[3], a[1] - b[3]));
		return Math.max(0, (long) Math.floor(Math.hypot(dx, dy) * perTile) - 1);
	}

	/** The water next to a dock's buoy (the ring sea legs start and end on), {x0, y0, x1, y1}. */
	static int[] buoyBox(Dock d)
	{
		return new int[]{d.getBuoyX() - 1, d.getBuoyY() - 1, d.getBuoyX() + d.getBuoySize(), d.getBuoyY() + d.getBuoySize()};
	}

	static int cheb(int x0, int y0, int x1, int y1)
	{
		return Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
	}
}
