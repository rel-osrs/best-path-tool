package com.bestpath.core.collision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Free-to-play land: the tiles a player on a free world can stand on; everything else is members only.
 * The same on every plane (the planes above a free building are free too; dungeons have their own
 * coordinates). Stored as runs of free tiles per row.
 * Immutable; {@link Builder} makes and changes one.
 *
 * <p>Text form (one row per line, ascending x, runs not touching): {@code <y> <x1>-<x2> <x3>-<x4> ...};
 * {@code #} starts a comment line.
 */
public final class FreeLand
{
	public static final FreeLand NONE = new Builder().build();

	/** Rows with free tiles, ascending, and each row's runs as x1, x2 pairs (inclusive). */
	final int[] rows;
	final int[][] runs;

	private FreeLand(int[] rows, int[][] runs)
	{
		this.rows = rows;
		this.runs = runs;
	}

	/** Can a free-world player stand on (x, y) (any plane)? */
	public boolean isFree(int x, int y)
	{
		int i = Arrays.binarySearch(rows, y);
		if (i < 0)
		{
			return false;
		}
		int[] r = runs[i];
		// the last run starting at or before x
		int lo = 0, hi = r.length / 2 - 1, found = -1;
		while (lo <= hi)
		{
			int mid = (lo + hi) >>> 1;
			if (r[mid * 2] <= x)
			{
				found = mid;
				lo = mid + 1;
			}
			else
			{
				hi = mid - 1;
			}
		}
		return found >= 0 && x <= r[found * 2 + 1];
	}

	/** Reads the text form. */
	public static FreeLand parse(List<String> lines)
	{
		Builder b = new Builder();
		int n = 0;
		for (String line : lines)
		{
			n++;
			String s = line.trim();
			if (s.isEmpty() || s.startsWith("#"))
			{
				continue;
			}
			String[] w = s.split("\\s+");
			try
			{
				int y = Integer.parseInt(w[0]);
				for (int i = 1; i < w.length; i++)
				{
					int dash = w[i].indexOf('-', 1);
					int x1 = Integer.parseInt(dash < 0 ? w[i] : w[i].substring(0, dash));
					int x2 = dash < 0 ? x1 : Integer.parseInt(w[i].substring(dash + 1));
					b.set(x1, y, x2, y, true);
				}
			}
			catch (NumberFormatException e)
			{
				throw new IllegalArgumentException("free land line " + n + ": " + line, e);
			}
		}
		return b.build();
	}

	/** Collects free tiles: rectangles marked free or members, later ones win. */
	public static final class Builder
	{
		private final TreeMap<Integer, TreeMap<Integer, Integer>> rows = new TreeMap<>();

		/** Marks the rectangle (x1, y1)-(x2, y2) (inclusive, any corner order) free or members. */
		public Builder set(int x1, int y1, int x2, int y2, boolean free)
		{
			int xa = Math.min(x1, x2), xb = Math.max(x1, x2);
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
			{
				TreeMap<Integer, Integer> row = rows.computeIfAbsent(y, k -> new TreeMap<>());
				cut(row, xa, xb);
				if (free)
				{
					int a = xa, b = xb;
					// join the runs touching it
					Map.Entry<Integer, Integer> left = row.floorEntry(a - 1);
					if (left != null && left.getValue() >= a - 1)
					{
						a = left.getKey();
						row.remove(left.getKey());
					}
					Map.Entry<Integer, Integer> right = row.ceilingEntry(b + 1);
					if (right != null && right.getKey() == b + 1)
					{
						b = right.getValue();
						row.remove(right.getKey());
					}
					row.put(a, b);
				}
				if (row.isEmpty())
				{
					rows.remove(y);
				}
			}
			return this;
		}

		/** Removes [a, b] from the runs of one row. */
		private static void cut(TreeMap<Integer, Integer> row, int a, int b)
		{
			Map.Entry<Integer, Integer> e = row.floorEntry(b);
			List<int[]> keep = new ArrayList<>();
			while (e != null && e.getValue() >= a)
			{
				row.remove(e.getKey());
				if (e.getKey() < a)
				{
					keep.add(new int[]{e.getKey(), a - 1});
				}
				if (e.getValue() > b)
				{
					keep.add(new int[]{b + 1, e.getValue()});
				}
				e = row.floorEntry(b);
			}
			for (int[] k : keep)
			{
				row.put(k[0], k[1]);
			}
		}

		public FreeLand build()
		{
			int[] ys = new int[rows.size()];
			int[][] rs = new int[rows.size()][];
			int i = 0;
			for (Map.Entry<Integer, TreeMap<Integer, Integer>> e : rows.entrySet())
			{
				ys[i] = e.getKey();
				int[] r = new int[e.getValue().size() * 2];
				int k = 0;
				for (Map.Entry<Integer, Integer> run : e.getValue().entrySet())
				{
					r[k++] = run.getKey();
					r[k++] = run.getValue();
				}
				rs[i++] = r;
			}
			return new FreeLand(ys, rs);
		}
	}
}
