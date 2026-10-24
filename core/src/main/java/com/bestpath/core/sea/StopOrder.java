package com.bestpath.core.sea;

import java.util.Random;

/**
 * An order for visiting many stops from a start, made without any route search: nearest first, then improved until
 * it is good enough. One stop may end somewhere else than it starts (a current duck is picked up away from where it
 * was let go), so the costs are one-way: {@code cost[from][to]}, where row 0 is the start and row {@code s + 1} is
 * where stop {@code s} ends, and column {@code s + 1} is where stop {@code s} starts (column 0 is not used). The route
 * is open: it ends at the last stop.
 *
 * <p>Improving: each pass tries every turn-round of a stretch of the order (2-opt) and every move of one, two or
 * three stops in a row to another place (or-opt), keeping each change that makes the whole shorter; passes stop when
 * one gains less than {@link #GOOD_ENOUGH} of the length (in practice after one or two). Then {@link #plan} shakes the
 * order up and improves it again, keeping what is shorter, until {@link #IDLE_SHAKES} shake-ups in a row find
 * nothing.
 *
 * <p><b>Good enough</b> (trial, the six oceans' charting tasks with the {@link CoarseSea}'s distances,
 * scored on true sailing distances): {@link #plan} averages under 1 % (worst about 1.5 %) longer than the best order
 * found with far more work, against 22 % (worst 44 %) for always going to the nearest task; 30 shake-ups instead of
 * 100 average 2 % (worst 7 %). An ocean takes a few tens of milliseconds; every sea at once (358 tasks) stops at
 * {@link #SHAKE_MILLIS}.
 */
public final class StopOrder
{
	/** A pass that shortens the order by less than this share of its length is the last. */
	public static final double GOOD_ENOUGH = 0.005;
	/** At most this many passes. */
	public static final int MAX_PASSES = 25;
	/** {@link #plan} stops after this many shake-ups in a row that found nothing shorter ... */
	public static final int IDLE_SHAKES = 100;
	/** ... or after this many in all ... */
	public static final int MAX_SHAKES = 2000;
	/** ... or once shaking up has taken this long (a big set: every sea's 358 tasks). */
	public static final long SHAKE_MILLIS = 250;

	private StopOrder()
	{
	}

	/** The stops' order (stop numbers 0 .. n - 1) from the start: good enough (see the class notes). */
	public static int[] plan(double[][] cost)
	{
		return orderShaken(cost, IDLE_SHAKES, MAX_SHAKES, SHAKE_MILLIS);
	}

	/** Nearest first, improved by passes only (no shaking up). */
	public static int[] order(double[][] cost)
	{
		return order(cost, GOOD_ENOUGH, MAX_PASSES);
	}

	/** As {@link #order(double[][])} with its own stopping rule (for trials). */
	public static int[] order(double[][] cost, double goodEnough, int maxPasses)
	{
		return improve(nearestFirst(cost), cost, goodEnough, maxPasses);
	}

	/**
	 * {@link #order(double[][])}, then shaken up and improved again: the best order so far has three cuts swapped round
	 * (a "double bridge", which the passes can't undo in one move) and is improved; a shorter result is kept. Stops
	 * after {@code idleKicks} shake-ups in a row that found nothing shorter, or after {@code maxKicks}. Also stops once
	 * shaking up has taken {@code millis} ({@link Long#MAX_VALUE}: no time limit). Always the same answer for the same costs
	 * (fixed seed).
	 */
	public static int[] orderShaken(double[][] cost, int idleKicks, int maxKicks, long millis)
	{
		int[] best = order(cost);
		int n = best.length;
		if (n < 8)
		{
			return best;
		}
		double bestLen = length(best, cost);
		Random rnd = new Random(1);
		int idle = 0;
		long until = millis == Long.MAX_VALUE ? Long.MAX_VALUE : System.nanoTime() + millis * 1_000_000;
		for (int kick = 0; kick < maxKicks && idle < idleKicks && System.nanoTime() < until; kick++)
		{
			int a = 1 + rnd.nextInt(n - 3);
			int b = a + 1 + rnd.nextInt(n - a - 2);
			int c = b + 1 + rnd.nextInt(n - b - 1);
			int[] q = new int[n];
			int w = 0;
			for (int i = 0; i < a; i++)
			{
				q[w++] = best[i];
			}
			for (int i = b; i < c; i++)
			{
				q[w++] = best[i];
			}
			for (int i = a; i < b; i++)
			{
				q[w++] = best[i];
			}
			for (int i = c; i < n; i++)
			{
				q[w++] = best[i];
			}
			q = improve(q, cost, GOOD_ENOUGH, MAX_PASSES);
			double len = length(q, cost);
			if (len < bestLen - 1e-9)
			{
				best = q;
				bestLen = len;
				idle = 0;
			}
			else
			{
				idle++;
			}
		}
		return best;
	}

	/** Passes of {@link #twoOpt} and {@link #orOpt} from the given order until one gains less than {@code goodEnough}. */
	private static int[] improve(int[] start, double[][] cost, double goodEnough, int maxPasses)
	{
		int[] p = start;
		double len = length(p, cost);
		for (int pass = 0; pass < maxPasses && p.length > 2; pass++)
		{
			double before = len;
			twoOpt(p, cost);
			p = orOpt(p, cost);
			len = length(p, cost);
			if (before - len < goodEnough * before)
			{
				break;
			}
		}
		return p;
	}

	/** Each next stop the nearest one left. */
	public static int[] nearestFirst(double[][] cost)
	{
		int n = cost.length - 1;
		int[] p = new int[n];
		boolean[] used = new boolean[n];
		int at = 0;
		for (int k = 0; k < n; k++)
		{
			int best = -1;
			for (int s = 0; s < n; s++)
			{
				if (!used[s] && (best < 0 || cost[at][s + 1] < cost[at][best + 1]))
				{
					best = s;
				}
			}
			p[k] = best;
			used[best] = true;
			at = best + 1;
		}
		return p;
	}

	/** The whole order's cost from the start. */
	public static double length(int[] p, double[][] cost)
	{
		double s = 0;
		int at = 0;
		for (int stop : p)
		{
			s += cost[at][stop + 1];
			at = stop + 1;
		}
		return s;
	}

	/** Row of where the stop at position k ends (-1: the start). */
	private static int from(int[] p, int k)
	{
		return k < 0 ? 0 : p[k] + 1;
	}

	/** One pass of turning stretches round: positions i..j reversed whenever that is shorter. */
	private static void twoOpt(int[] p, double[][] c)
	{
		int n = p.length;
		for (int i = 0; i < n - 1; i++)
		{
			double fwd = 0; // the stretch's legs as they are
			double bwd = 0; // the same legs the other way round
			for (int j = i + 1; j < n; j++)
			{
				fwd += c[p[j - 1] + 1][p[j] + 1];
				bwd += c[p[j] + 1][p[j - 1] + 1];
				int before = from(p, i - 1);
				double old = c[before][p[i] + 1] + fwd + (j + 1 < n ? c[p[j] + 1][p[j + 1] + 1] : 0);
				double now = c[before][p[j] + 1] + bwd + (j + 1 < n ? c[p[i] + 1][p[j + 1] + 1] : 0);
				if (now < old - 1e-9)
				{
					for (int a = i, b = j; a < b; a++, b--)
					{
						int t = p[a];
						p[a] = p[b];
						p[b] = t;
					}
					// the stretch changed: start this i again
					fwd = 0;
					bwd = 0;
					j = i;
				}
			}
		}
	}

	/** One pass of moving one to three stops in a row elsewhere (in the same direction), whenever that is shorter. */
	private static int[] orOpt(int[] p, double[][] c)
	{
		int n = p.length;
		for (int seg = 1; seg <= 3 && seg < n; seg++)
		{
			boolean any = true;
			while (any)
			{
				any = false;
				for (int i = 0; i + seg <= n; i++)
				{
					boolean moved = false;
					int first = p[i] + 1;
					int last = p[i + seg - 1] + 1;
					int before = from(p, i - 1);
					int after = i + seg < n ? p[i + seg] + 1 : -1;
					double out = c[before][first] + (after >= 0 ? c[last][after] - c[before][after] : 0);
					// put the stretch after position k of the order without it (k = -1: right after the start)
					for (int k = -1; k < n && !moved; k++)
					{
						if (k >= i - 1 && k < i + seg)
						{
							continue; // where it is now, or inside it
						}
						int u = from(p, k);
						int v = k + 1 < n ? p[k + 1] + 1 : -1;
						double in = c[u][first] + (v >= 0 ? c[last][v] - c[u][v] : 0);
						if (in < out - 1e-9)
						{
							p = moveStretch(p, i, seg, k);
							moved = true;
							any = true;
						}
					}
				}
			}
		}
		return p;
	}

	/** The order with positions i .. i + seg - 1 moved to right after position k (k as in the order before the move). */
	private static int[] moveStretch(int[] p, int i, int seg, int k)
	{
		int n = p.length;
		int[] r = new int[n];
		int w = 0;
		if (k == -1)
		{
			for (int s = 0; s < seg; s++)
			{
				r[w++] = p[i + s];
			}
		}
		for (int a = 0; a < n; a++)
		{
			if (a >= i && a < i + seg)
			{
				continue;
			}
			r[w++] = p[a];
			if (a == k)
			{
				for (int s = 0; s < seg; s++)
				{
					r[w++] = p[i + s];
				}
			}
		}
		return r;
	}
}
