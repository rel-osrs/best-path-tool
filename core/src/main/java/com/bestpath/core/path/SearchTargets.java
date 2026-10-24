package com.bestpath.core.path;

import java.util.Arrays;
import java.util.Map;

/**
 * Many targets for one tile search at once ({@link FastSearch}; every bank a route may start at): the search goes from
 * the query's start (its destination is not used) and finds the ticks to each target tile within {@code limit} ticks;
 * it stops when all are found or nothing within the limit is left. Give it a zero bound (plain Dijkstra), so the first
 * time a tile comes off the queue its ticks are final.
 */
final class SearchTargets
{
	/** Packed tile -> its index in the caller's list. */
	final Map<Integer, Integer> indexOf;
	final int limit;
	/** Per cell index: a bit per target tile (made by the search). */
	long[][] bits;
	/** Per target (the caller's order): ticks from the start, -1 = not within the limit. */
	final int[] ticks;
	int found;
	/** Per target: the least ticks still needed after reaching it (at least {@link FastSearch#NEVER}: it can't help). */
	private final int[] after;
	/** The nearest target of all (whichever it is) helps when its ticks plus this are under {@link #best}. */
	private final int anyAfter;
	/** Only what can lead to fewer ticks than this is looked for. */
	final int best;
	/** The targets by {@link #after}, least first; {@link #next}: the first not found yet. */
	private final int[] byAfter;
	private int next;
	/** Ticks of the first target found (-1: none yet). */
	int firstTicks = -1;
	/** When not null: per target, the way there (made as the search ends, for the targets that can help). */
	Route[] routes;
	/**
	 * When not null: a lower bound of the ticks from a tile to the end of the whole route through any target
	 * ({@link FastSearch.Bound#coarse}), plus {@link #onAdd}; the search goes on from a tile only while its ticks plus
	 * that are under {@link #best} (nothing on from it can lead to a target that helps). The targets found stay exact:
	 * a target that helps is reached only over tiles that pass.
	 */
	FastSearch.Bound on;
	int onAdd;

	SearchTargets(Map<Integer, Integer> indexOf, int count, int limit)
	{
		this(indexOf, new int[count], 0, Integer.MAX_VALUE / 2, limit);
	}

	/**
	 * Targets that matter only while they can lead to fewer than {@code best} ticks: target i when its ticks plus
	 * {@code after[i]} are, and the nearest target of all when its ticks plus {@code anyAfter} are. The search stops
	 * as soon as nothing left can (or nothing within {@code limit} is left).
	 */
	SearchTargets(Map<Integer, Integer> indexOf, int[] after, int anyAfter, int best, int limit)
	{
		this.indexOf = indexOf;
		this.limit = limit;
		this.ticks = new int[after.length];
		Arrays.fill(ticks, -1);
		this.after = after;
		this.anyAfter = anyAfter;
		this.best = best;
		Integer[] order = new Integer[after.length];
		for (int i = 0; i < order.length; i++)
		{
			order[i] = i;
		}
		Arrays.sort(order, (x, y) -> Integer.compare(after[x], after[y]));
		byAfter = new int[order.length];
		for (int i = 0; i < order.length; i++)
		{
			byAfter[i] = order[i];
		}
	}

	/** True when target i, reached in {@code t} ticks, can lead to fewer than {@link #best} ticks. */
	boolean helps(int i, int t)
	{
		return t >= 0 && (long) t + after[i] < best;
	}

	void reached(int i, int t)
	{
		ticks[i] = t;
		found++;
		if (firstTicks < 0)
		{
			firstTicks = t;
		}
	}

	/** True when nothing still to be found at {@code t} ticks or more can help (the search may stop). */
	boolean done(int t)
	{
		if (t > limit)
		{
			return true;
		}
		// the nearest target is looked for (all of them at its ticks, so ties keep the caller's first) while it can help
		if (firstTicks >= 0 ? t <= firstTicks : (long) t + anyAfter < best)
		{
			return false;
		}
		while (next < byAfter.length && ticks[byAfter[next]] >= 0)
		{
			next++;
		}
		return next == byAfter.length || (long) t + after[byAfter[next]] >= best;
	}
}
