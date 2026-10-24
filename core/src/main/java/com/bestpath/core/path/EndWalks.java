package com.bestpath.core.path;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Walking distances inside a cell between the transport ends of each component, kept instead of a whole 8 KB
 * walk table per end. Each component numbers the end tiles it has seen ({@link #number}); a row holds one end's
 * distances to every end numbered so far (-1: none), made again only when ends were numbered after it.
 */
final class EndWalks
{
	/** The kept walks between transport ends are dropped past this many numbers (8 MB). */
	private static final long MAX_END_SHORTS = 4_000_000;

	/** The walking tables made ahead of time, unpacked per component when first needed; null: none. */
	private final WalkTables shipped;
	private final CellWalks cells;
	/** Known end tiles by tile: their number in their component. */
	private final IntIntMap numbers = new IntIntMap();
	/**
	 * Per component: its known end tiles, in number order, and the rows by end number (or by solid tile and first
	 * tile beside it).
	 */
	final Map<Integer, Area> areas = new HashMap<>();
	private long shorts;

	/** One component's known end tiles and their rows. */
	static final class Area
	{
		int[] tiles = new int[4];
		int count;
		short[][] rows = new short[4][];
		Map<Long, short[]> setRows;
	}

	EndWalks(WalkTables shipped, CellWalks cells)
	{
		this.shipped = shipped;
		this.cells = cells;
	}

	int number(int c, int tile)
	{
		int k = numbers.get(tile);
		if (k < 0 && shipped != null && !areas.containsKey(c))
		{
			unpack(c);
			k = numbers.get(tile);
		}
		if (k >= 0)
		{
			return k;
		}
		Area a = areas.computeIfAbsent(c, x -> new Area());
		if (a.count == a.tiles.length)
		{
			a.tiles = Arrays.copyOf(a.tiles, a.count * 2);
		}
		a.tiles[a.count] = tile;
		numbers.put(tile, a.count);
		return a.count++;
	}

	/** Unpacks the end walks made ahead of time for component {@code c} (counted like rows made here). */
	private void unpack(int c)
	{
		WalkTables.Ends w = shipped.ends(c);
		if (w == null)
		{
			return;
		}
		Area a = new Area();
		int n = w.tiles.length;
		a.tiles = Arrays.copyOf(w.tiles, Math.max(4, n));
		a.count = n;
		a.rows = Arrays.copyOf(w.rows, Math.max(4, n));
		a.setRows = w.setRows.isEmpty() ? null : w.setRows;
		areas.put(c, a);
		for (int k = 0; k < n; k++)
		{
			numbers.put(w.tiles[k], k);
			shorts += n;
		}
		for (short[] r : w.setRows.values())
		{
			shorts += r.length;
		}
	}

	/** The row of one end (number {@code k}, tile {@code tile}) or of a set of tiles (key {@code setKey}). */
	short[] row(int c, int k, int tile, int[] set, long setKey)
	{
		Area a = areas.get(c);
		short[] r;
		if (set == null)
		{
			if (k >= a.rows.length)
			{
				a.rows = Arrays.copyOf(a.rows, Math.max(k + 1, a.rows.length * 2));
			}
			r = a.rows[k];
		}
		else
		{
			r = a.setRows == null ? null : a.setRows.get(setKey);
		}
		if (r != null && r.length >= a.count)
		{
			return r;
		}
		short[] from = set == null ? cells.walkFrom(tile, cells.scratch) : cells.walkFrom(set, cells.scratch);
		short[] made = new short[a.count];
		for (int i = 0; i < a.count; i++)
		{
			made[i] = from[Tiles.local(a.tiles[i])];
		}
		shorts += made.length - (r == null ? 0 : r.length);
		if (set == null)
		{
			a.rows[k] = made;
		}
		else
		{
			if (a.setRows == null)
			{
				a.setRows = new HashMap<>();
			}
			a.setRows.put(setKey, made);
		}
		return made;
	}

	/** True when the rows grew past {@link #MAX_END_SHORTS}: then they are dropped and worked out again. */
	boolean overLimit()
	{
		return shorts > MAX_END_SHORTS;
	}

	long bytes()
	{
		return 2L * shorts + 24L * numbers.size() + 96L * areas.size();
	}
}
