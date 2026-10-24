package com.bestpath.core.path;

import java.util.Arrays;

/** A binary min-heap of longs (no boxing): the search queue. */
final class LongHeap
{
	private long[] a;
	private int size;

	LongHeap()
	{
		this(1024);
	}

	/** Room for {@code capacity} entries to begin with (it grows as needed; the order entries come out is the same). */
	LongHeap(int capacity)
	{
		a = new long[Math.max(1, capacity)];
	}

	/** Empties the heap, keeping its room (used again by the next search). */
	void clear()
	{
		size = 0;
	}

	/** Bytes the heap keeps. */
	long bytes()
	{
		return 16 + 8L * a.length + 32;
	}

	boolean isEmpty()
	{
		return size == 0;
	}

	void push(long v)
	{
		if (size == a.length)
		{
			a = Arrays.copyOf(a, a.length * 2);
		}
		int i = size++;
		while (i > 0)
		{
			int p = (i - 1) >> 1;
			if (a[p] <= v)
			{
				break;
			}
			a[i] = a[p];
			i = p;
		}
		a[i] = v;
	}

	long pop()
	{
		long top = a[0];
		long v = a[--size];
		int i = 0;
		while (true)
		{
			int c = 2 * i + 1;
			if (c >= size)
			{
				break;
			}
			if (c + 1 < size && a[c + 1] < a[c])
			{
				c++;
			}
			if (a[c] >= v)
			{
				break;
			}
			a[i] = a[c];
			i = c;
		}
		a[i] = v;
		return top;
	}

	long peek()
	{
		return a[0];
	}
}
