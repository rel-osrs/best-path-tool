package com.bestpath.core.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Makes {@link WildernessZones} from a zone per tile, when the data is made: rows are cut into runs of one zone (0
 * left out) and rows whose runs are all the same are merged into rectangles.
 */
public final class WildernessZonesBuilder
{
	private final int x0;
	private final int y0;
	private final int width;
	private final int height;
	private final byte[] zones;

	/** A grid from (x0, y0), {@code width} x {@code height} tiles, every tile outside at first. */
	public WildernessZonesBuilder(int x0, int y0, int width, int height)
	{
		this.x0 = x0;
		this.y0 = y0;
		this.width = width;
		this.height = height;
		this.zones = new byte[width * height];
	}

	/** Sets a tile's zone (0 to {@link WildernessZones#DEEP}). */
	public WildernessZonesBuilder set(int x, int y, int zone)
	{
		if (zone < 0 || zone > WildernessZones.DEEP)
		{
			throw new IllegalArgumentException("zone " + zone);
		}
		zones[(y - y0) * width + (x - x0)] = (byte) zone;
		return this;
	}

	public WildernessZones build()
	{
		List<int[]> out = new ArrayList<>();
		int[] prevRuns = null;
		List<int[]> open = new ArrayList<>();
		for (int row = 0; row <= height; row++)
		{
			int[] runs = row < height ? runs(row) : new int[0];
			if (prevRuns != null && Arrays.equals(runs, prevRuns))
			{
				for (int[] r : open)
				{
					r[3]++;
				}
				continue;
			}
			out.addAll(open);
			open = new ArrayList<>();
			for (int i = 0; i < runs.length; i += 3)
			{
				open.add(new int[]{x0 + runs[i], y0 + row, runs[i + 1] - runs[i] + 1, 1, runs[i + 2]});
			}
			prevRuns = runs;
		}
		out.addAll(open);
		return WildernessZones.of(out);
	}

	/** One row's runs: start, end (grid x, inclusive), zone, for every run of a zone above 0. */
	private int[] runs(int row)
	{
		List<Integer> r = new ArrayList<>();
		int base = row * width;
		int x = 0;
		while (x < width)
		{
			int z = zones[base + x];
			int end = x;
			while (end + 1 < width && zones[base + end + 1] == z)
			{
				end++;
			}
			if (z > 0)
			{
				r.add(x);
				r.add(end);
				r.add(z);
			}
			x = end + 1;
		}
		int[] a = new int[r.size()];
		for (int i = 0; i < a.length; i++)
		{
			a[i] = r.get(i);
		}
		return a;
	}
}
