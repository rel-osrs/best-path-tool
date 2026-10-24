package com.bestpath.core.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A query's transports by the tile they are used from, for the tile search ({@link FastSearch}): per tile the
 * transports used there, a bit per tile that has any (per cell), and the solid landing tiles of transports used from
 * beside their ends with the steps off them. Made once per list of transports and only read by the searches.
 */
final class TransportIndex
{
	/** The transports it is for (compared one by one: a query copies its list). */
	private final Transport[] of;
	private final Beside beside;
	/** Transports by the tile they are used from: {index in the list, 1 when used from its end (backwards)}. */
	final Map<Integer, List<int[]>> from = new HashMap<>();
	/** Per cell index: a bit per tile that has a transport in {@link #from}. */
	final long[][] hasFrom;
	/** Transports used from beside their ends: the solid tiles they land on, with the steps off them. */
	final Map<Integer, Integer> stepOff = new HashMap<>();
	private int uses;
	private int cells;

	/** {@code search} numbers the cells (only its cell lookup is used). */
	TransportIndex(FastSearch search, int cellCount, List<Transport> transports, Beside beside)
	{
		of = transports.toArray(new Transport[0]);
		this.beside = beside;
		hasFrom = new long[cellCount][];
		for (int i = 0; i < transports.size(); i++)
		{
			Transport tr = transports.get(i);
			if (!tr.hasStart())
			{
				continue;
			}
			addUse(search, tr.getStartX(), tr.getStartY(), tr.getStartPlane(), new int[]{i, 0});
			boolean b = beside != null && tr.isUsedFromBeside();
			int st = Tiles.pack(tr.getStartX(), tr.getStartY(), tr.getStartPlane());
			int en = Tiles.pack(tr.getEndX(), tr.getEndY(), tr.getEndPlane());
			if (b)
			{
				addBeside(search, beside, st, en, new int[]{i, 0});
			}
			if (tr.isTwoWay())
			{
				addUse(search, tr.getEndX(), tr.getEndY(), tr.getEndPlane(), new int[]{i, 1});
				if (b)
				{
					addBeside(search, beside, en, st, new int[]{i, 1});
				}
			}
		}
		for (List<int[]> l : from.values())
		{
			uses += l.size();
		}
		for (long[] h : hasFrom)
		{
			cells += h == null ? 0 : 1;
		}
	}

	boolean isFor(List<Transport> list, Beside b)
	{
		if (b != beside || list.size() != of.length)
		{
			return false;
		}
		int i = 0;
		for (Transport t : list)
		{
			if (t != of[i++])
			{
				return false;
			}
		}
		return true;
	}

	/** About how many bytes it keeps (map entries and lists, a use each, the bits per cell). */
	long bytes()
	{
		return 16 + 8L * (of.length + hasFrom.length) + 96L * from.size() + 32L * uses + 528L * cells + 64L * stepOff.size();
	}

	private void addUse(FastSearch search, int x, int y, int p, int[] use)
	{
		int ci = search.cellIndex(x, y, p);
		if (ci < 0)
		{
			return;
		}
		from.computeIfAbsent(Tiles.pack(x, y, p), k -> new ArrayList<>()).add(use);
		int l = (x & 63) << 6 | (y & 63);
		if (hasFrom[ci] == null)
		{
			hasFrom[ci] = new long[64];
		}
		hasFrom[ci][l >> 6] |= 1L << (l & 63);
	}

	/**
	 * A transport used from beside its end {@code end} (see {@link Beside}): usable from the tiles beside a
	 * solid {@code end}, and a solid {@code other} end (where it lands) is stepped off.
	 */
	private void addBeside(FastSearch search, Beside beside, int end, int other, int[] use)
	{
		for (int t : beside.boardTiles(end))
		{
			addUse(search, Tiles.x(t), Tiles.y(t), Tiles.plane(t), use);
		}
		int off = beside.leaveMask(other);
		if (off != 0)
		{
			stepOff.put(other, off);
		}
	}
}
