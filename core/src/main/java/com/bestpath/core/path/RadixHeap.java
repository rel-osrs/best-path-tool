package com.bestpath.core.path;

import java.util.Arrays;

/**
 * A min-queue of non-negative longs. It always returns the smallest key,
 * exactly like {@link LongHeap}, so results and ties are the same; it is
 * faster on big queues whose keys mostly do not go below the last one taken
 * out (A* with a consistent estimate), because a key is only moved a few
 * times instead of along a whole heap path. Keys below the last one taken out
 * (a tie on cost with a lower state number) wait in a small
 * binary heap that is emptied first: they are smaller than everything else.
 *
 * <p>Bucket i holds keys whose highest bit differing from the last key taken
 * out is bit i - 1 (bucket 0: equal to it).
 */
public final class RadixHeap
{
	private final long[][] buckets = new long[65][];
	private final int[] sizes = new int[65];
	private long last;
	private final LongHeap early = new LongHeap();
	private int size;

	public RadixHeap()
	{
		for (int i = 0; i < buckets.length; i++)
		{
			buckets[i] = new long[16];
		}
	}

	public boolean isEmpty()
	{
		return size == 0;
	}

	public void push(long k)
	{
		size++;
		if (k < last)
		{
			early.push(k);
			return;
		}
		add(bucket(k), k);
	}

	public long pop()
	{
		if (!early.isEmpty())
		{
			size--;
			return early.pop();
		}
		if (sizes[0] == 0)
		{
			int i = 1;
			while (sizes[i] == 0)
			{
				i++;
			}
			// the smallest key of the first non-empty bucket becomes the new reference; spread the bucket out
			long[] b = buckets[i];
			int n = sizes[i];
			long min = b[0];
			for (int j = 1; j < n; j++)
			{
				min = Math.min(min, b[j]);
			}
			last = min;
			sizes[i] = 0;
			for (int j = 0; j < n; j++)
			{
				add(bucket(b[j]), b[j]);
			}
		}
		size--;
		return buckets[0][--sizes[0]];
	}

	private int bucket(long k)
	{
		return k == last ? 0 : 64 - Long.numberOfLeadingZeros(k ^ last);
	}

	private void add(int i, long k)
	{
		long[] b = buckets[i];
		if (sizes[i] == b.length)
		{
			b = Arrays.copyOf(b, b.length * 2);
			buckets[i] = b;
		}
		b[sizes[i]++] = k;
	}
}
