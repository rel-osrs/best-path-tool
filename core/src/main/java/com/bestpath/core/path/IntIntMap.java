package com.bestpath.core.path;

import java.util.Arrays;

/** An int to int map without boxing (keys and values 0 or more), for the transport end numbers of {@link EndWalks}. */
final class IntIntMap
{
	private int[] keys = new int[1024];
	private int[] values = new int[1024];
	private int size;

	IntIntMap()
	{
		Arrays.fill(keys, -1);
	}

	int get(int key)
	{
		int m = keys.length - 1;
		for (int i = mix(key) & m; ; i = (i + 1) & m)
		{
			if (keys[i] == key)
			{
				return values[i];
			}
			if (keys[i] < 0)
			{
				return -1;
			}
		}
	}

	void put(int key, int value)
	{
		if (2 * (size + 1) > keys.length)
		{
			int[] ok = keys;
			int[] ov = values;
			keys = new int[ok.length * 2];
			values = new int[ok.length * 2];
			Arrays.fill(keys, -1);
			size = 0;
			for (int i = 0; i < ok.length; i++)
			{
				if (ok[i] >= 0)
				{
					put(ok[i], ov[i]);
				}
			}
		}
		int m = keys.length - 1;
		int i = mix(key) & m;
		while (keys[i] >= 0 && keys[i] != key)
		{
			i = (i + 1) & m;
		}
		if (keys[i] < 0)
		{
			size++;
		}
		keys[i] = key;
		values[i] = value;
	}

	int size()
	{
		return size;
	}

	private static int mix(int k)
	{
		int h = k * 0x9E3779B9;
		return h ^ (h >>> 16);
	}
}
