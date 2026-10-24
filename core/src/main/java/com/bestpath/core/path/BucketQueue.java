package com.bestpath.core.path;

import java.util.Arrays;

/**
 * The tile search's queue: one bucket per f value in ticks (small whole
 * numbers, so finding the next bucket is quick); inside a bucket a small heap
 * that hands out the entry with the fewest diagonal steps first. Entries are
 * diagonals << 26 | cellIndex << 12 | local.
 */
final class BucketQueue
{
	/**
	 * Room for this many entries in a new bucket. Most buckets hold only a few entries and a long search uses many
	 * buckets, so they start small and are kept for the next search ({@link #reset}).
	 */
	private static final int FIRST_ROOM = 16;
	private LongHeap[] heaps = new LongHeap[1024];
	private int cur;
	private int count;
	private int lastF;
	/** One past the highest bucket used since the last {@link #reset}. */
	private int used;

	/** Empties every bucket for a new search; their room is kept. */
	void reset()
	{
		for (int f = 0; f < used; f++)
		{
			if (heaps[f] != null)
			{
				heaps[f].clear();
			}
		}
		used = 0;
		cur = 0;
		count = 0;
		lastF = 0;
	}

	/** Bytes the buckets keep between searches (0 before the first search). */
	long bytes()
	{
		long n = 0;
		for (LongHeap h : heaps)
		{
			n += h == null ? 0 : h.bytes();
		}
		return n == 0 ? 0 : n + 16 + 8L * heaps.length;
	}

	void push(int f, int g, int ci, int l)
	{
		if (f >= FastSearch.NEVER)
		{
			return; // no way on from this tile: it can never be on a route
		}
		if (f < 0)
		{
			f = 0;
		}
		if (f >= heaps.length)
		{
			heaps = Arrays.copyOf(heaps, Math.max(f + 1, heaps.length * 2));
		}
		LongHeap h = heaps[f];
		if (h == null)
		{
			h = new LongHeap(FIRST_ROOM);
			heaps[f] = h;
		}
		if (f >= used)
		{
			used = f + 1;
		}
		h.push((long) (g & FastSearch.DIAG_MASK) << 26 | (long) ci << 12 | l);
		if (f < cur)
		{
			cur = f; // a bound that is admissible but not consistent can lower f: stay correct
		}
		count++;
	}

	boolean isEmpty()
	{
		return count == 0;
	}

	long pop()
	{
		while (heaps[cur] == null || heaps[cur].isEmpty())
		{
			cur++;
		}
		count--;
		lastF = cur;
		return heaps[cur].pop();
	}

	/** The f value (ticks) of the entry popped last. */
	int lastF()
	{
		return lastF;
	}
}
