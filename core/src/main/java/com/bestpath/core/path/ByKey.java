package com.bestpath.core.path;

import java.util.Arrays;

/**
 * Values by key in one sorted array ({@code key << 32 | value}), for the coarse stage's "specials in this
 * component" and "specials on this tile", without boxed lists. In each key, values in the order added.
 */
final class ByKey
{
	private final long[] a;

	private ByKey(long[] a)
	{
		this.a = a;
	}

	int size()
	{
		return a.length;
	}

	int key(int i)
	{
		return (int) (a[i] >>> 32);
	}

	int value(int i)
	{
		return (int) a[i];
	}

	/** The first entry with this key, or -1. */
	int first(int key)
	{
		long probe = (long) key << 32;
		int lo = 0;
		int hi = a.length;
		while (lo < hi)
		{
			int mid = (lo + hi) >>> 1;
			if (a[mid] < probe)
			{
				lo = mid + 1;
			}
			else
			{
				hi = mid;
			}
		}
		return lo < a.length && key(lo) == key ? lo : -1;
	}

	/** How many entries have the key of entry {@code first}. */
	int count(int first)
	{
		int k = key(first);
		int i = first;
		while (i < a.length && key(i) == k)
		{
			i++;
		}
		return i - first;
	}

	static final class Builder
	{
		private long[] a;
		private int n;

		Builder(int room)
		{
			a = new long[Math.max(1, room)];
		}

		/** Adds a value under a key (both 0 or more); values of one key must be added in increasing order. */
		void add(int key, int value)
		{
			if (n == a.length)
			{
				a = Arrays.copyOf(a, n * 2);
			}
			a[n++] = (long) key << 32 | value;
		}

		ByKey build()
		{
			long[] out = Arrays.copyOf(a, n);
			Arrays.sort(out); // by key, then value: the order added, as values only grow
			return new ByKey(out);
		}
	}
}
