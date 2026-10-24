package com.bestpath.core.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Makes the walking tables ({@link WalkTables}) ahead of time, when the data is made: a {@link Pathfinder} works out
 * every slow-to-redo table, and they are packed as the walks file keeps them. Routes stay the same.
 */
public final class WalkTablesBuilder
{
	private WalkTablesBuilder()
	{
	}

	/**
	 * Works out every slow-to-redo walking table of {@code pf} ahead of time (the entrance-side distances of every
	 * component, and for the ends of these transports, as {@link Pathfinder#find} uses them, their distances to the
	 * entrance sides and to the other ends of their component), then packs every table {@code pf} has.
	 */
	public static WalkTables build(Pathfinder pf, List<Transport> transports)
	{
		synchronized (pf)
		{
			prepareAll(pf.walks, transports, pf.beside);
			return pack(pf.graphKey(), pf.walks);
		}
	}

	/**
	 * Works out every table ahead of time: the entrance-side distances of every component, and for the ends of these
	 * transports (as the coarse stage uses them) their distances to the entrance sides and to the other ends of their
	 * component.
	 */
	private static void prepareAll(ComponentWalks w, List<Transport> transports, Beside beside)
	{
		int[][] compSides = w.sides.compSides;
		for (int c = 0; c < compSides.length; c++)
		{
			if (compSides[c].length > 0)
			{
				w.matrix(c);
			}
		}
		// the transport ends and the tiles beside solid ends, as the coarse stage makes its nodes
		List<int[]> sets = new ArrayList<>();
		List<Integer> setEnd = new ArrayList<>();
		CoarseNodes.besideSets(w.graph, beside, transports, sets, setEnd, new LinkedHashSet<>(), new LinkedHashSet<>());
		List<Integer> single = new ArrayList<>();
		for (Transport t : transports)
		{
			if (t.hasStart())
			{
				single.add(Tiles.pack(t.getStartX(), t.getStartY(), t.getStartPlane()));
			}
			single.add(Tiles.pack(t.getEndX(), t.getEndY(), t.getEndPlane()));
		}
		w.ends = new EndWalks(w.shipped, w.cells);
		List<int[]> rows = new ArrayList<>(); // {component, number, tile}
		for (int tile : new LinkedHashSet<>(single))
		{
			int c = w.graph.componentAt(Tiles.x(tile), Tiles.y(tile), Tiles.plane(tile));
			if (c >= 0)
			{
				w.sideDists(tile, c);
				rows.add(new int[]{c, w.ends.number(c, tile), tile});
			}
		}
		List<Integer> setComp = new ArrayList<>();
		for (int i = 0; i < sets.size(); i++)
		{
			int[] set = sets.get(i);
			int c = w.graph.componentAt(Tiles.x(set[0]), Tiles.y(set[0]), Tiles.plane(set[0]));
			setComp.add(c);
			if (c >= 0)
			{
				w.besideSideDists(setEnd.get(i), set, c);
				for (int t : set)
				{
					w.ends.number(c, t);
				}
			}
		}
		// rows last, so each has every end of its component
		for (int[] r : rows)
		{
			w.ends.row(r[0], r[1], r[2], null, 0);
		}
		for (int i = 0; i < sets.size(); i++)
		{
			if (setComp.get(i) >= 0)
			{
				int[] set = sets.get(i);
				w.ends.row(setComp.get(i), -1, 0, set, (long) setEnd.get(i) << 32 | set[0]);
			}
		}
	}

	/**
	 * Packs the tables worked out or unpacked so far, for the given component layout: distances as the search keeps
	 * them, {@link WalkTables#INF} = none.
	 */
	private static WalkTables pack(long graphKey, ComponentWalks w)
	{
		Map<Integer, int[]> matrices = new HashMap<>(w.matrices);
		Map<Integer, int[]> sideDists = new HashMap<>(w.sideDists);
		Map<Long, int[]> beside = new HashMap<>(w.besideSideDists);
		Map<Integer, WalkTables.Ends> ends = endTables(w.ends);

		TreeMap<Integer, int[]> ms = new TreeMap<>(matrices);
		int[] mKeys = new int[ms.size()];
		int[] mK = new int[ms.size()];
		byte[] mSym = new byte[ms.size()];
		int[] mOff = new int[ms.size() + 1];
		ShortList pool = new ShortList();
		int i = 0;
		for (Map.Entry<Integer, int[]> e : ms.entrySet())
		{
			int[] m = e.getValue();
			int k = (int) Math.round(Math.sqrt(m.length));
			mKeys[i] = e.getKey();
			mK[i] = k;
			mSym[i] = (byte) (symmetric(m, k) ? 1 : 0);
			for (int a = 0; a < k; a++)
			{
				for (int b = mSym[i] == 1 ? a + 1 : 0; b < k; b++)
				{
					pool.add(steps(m[a * k + b]));
				}
			}
			mOff[++i] = pool.size;
		}
		short[] mPool = pool.toArray();

		TreeMap<Integer, int[]> ss = new TreeMap<>(sideDists);
		int[] sKeys = new int[ss.size()];
		int[] sOff = new int[ss.size() + 1];
		pool = new ShortList();
		i = 0;
		for (Map.Entry<Integer, int[]> e : ss.entrySet())
		{
			sKeys[i] = e.getKey();
			for (int v : e.getValue())
			{
				pool.add(steps(v));
			}
			sOff[++i] = pool.size;
		}
		short[] sPool = pool.toArray();

		TreeMap<Long, int[]> bs = new TreeMap<>(beside);
		long[] bKeys = new long[bs.size()];
		int[] bOff = new int[bs.size() + 1];
		pool = new ShortList();
		i = 0;
		for (Map.Entry<Long, int[]> e : bs.entrySet())
		{
			bKeys[i] = e.getKey();
			for (int v : e.getValue())
			{
				pool.add(steps(v));
			}
			bOff[++i] = pool.size;
		}
		short[] bPool = pool.toArray();

		TreeMap<Integer, WalkTables.Ends> es = new TreeMap<>(ends);
		int[] eKeys = new int[es.size()];
		int[] eTileOff = new int[es.size() + 1];
		int[] eRowOff = new int[es.size() + 1];
		List<Integer> tiles = new ArrayList<>();
		pool = new ShortList();
		TreeMap<Long, Object[]> sets = new TreeMap<>();
		i = 0;
		for (Map.Entry<Integer, WalkTables.Ends> e : es.entrySet())
		{
			WalkTables.Ends en = e.getValue();
			int n = en.tiles.length;
			eKeys[i] = e.getKey();
			for (int t : en.tiles)
			{
				tiles.add(t);
			}
			for (short[] r : en.rows)
			{
				for (int j = 0; j < n; j++)
				{
					pool.add(r[j]);
				}
			}
			for (Map.Entry<Long, short[]> s : en.setRows.entrySet())
			{
				sets.put(s.getKey(), new Object[]{e.getKey(), Arrays.copyOf(s.getValue(), n)});
			}
			i++;
			eTileOff[i] = tiles.size();
			eRowOff[i] = pool.size;
		}
		int[] eTiles = tiles.stream().mapToInt(Integer::intValue).toArray();
		short[] eRows = pool.toArray();
		long[] esKeys = new long[sets.size()];
		int[] esComp = new int[sets.size()];
		int[] esOff = new int[sets.size() + 1];
		pool = new ShortList();
		i = 0;
		for (Map.Entry<Long, Object[]> s : sets.entrySet())
		{
			esKeys[i] = s.getKey();
			esComp[i] = (Integer) s.getValue()[0];
			for (short v : (short[]) s.getValue()[1])
			{
				pool.add(v);
			}
			esOff[++i] = pool.size;
		}
		short[] esPool = pool.toArray();
		return new WalkTables(graphKey, mKeys, mK, mSym, mOff, mPool, sKeys, sOff, sPool, bKeys, bOff, bPool, eKeys, eTileOff, eTiles,
			eRowOff, eRows, esKeys, esComp, esOff, esPool);
	}

	/** Every component's end tiles and rows, cut to the ends known now, as the walking tables keep them. */
	private static Map<Integer, WalkTables.Ends> endTables(EndWalks walks)
	{
		Map<Integer, WalkTables.Ends> e = new HashMap<>();
		for (Map.Entry<Integer, EndWalks.Area> en : walks.areas.entrySet())
		{
			int c = en.getKey();
			EndWalks.Area a = en.getValue();
			int[] tiles = Arrays.copyOf(a.tiles, a.count);
			short[][] rows = new short[a.count][];
			for (int k = 0; k < a.count; k++)
			{
				rows[k] = Arrays.copyOf(walks.row(c, k, tiles[k], null, 0), a.count);
			}
			Map<Long, short[]> setRows = new HashMap<>();
			if (a.setRows != null)
			{
				for (Map.Entry<Long, short[]> r : a.setRows.entrySet())
				{
					if (r.getValue().length >= a.count)
					{
						setRows.put(r.getKey(), Arrays.copyOf(r.getValue(), a.count));
					}
				}
			}
			e.put(c, new WalkTables.Ends(tiles, rows, setRows));
		}
		return e;
	}

	private static boolean symmetric(int[] m, int k)
	{
		for (int a = 0; a < k; a++)
		{
			for (int b = a + 1; b < k; b++)
			{
				if (m[a * k + b] != m[b * k + a])
				{
					return false;
				}
			}
		}
		return true;
	}

	private static short steps(int v)
	{
		if (v >= WalkTables.INF)
		{
			return -1;
		}
		if (v > Short.MAX_VALUE)
		{
			throw new IllegalArgumentException("walk of " + v + " steps inside one cell");
		}
		return (short) v;
	}

	/** A growing list of shorts. */
	private static final class ShortList
	{
		short[] a = new short[1024];
		int size;

		void add(short v)
		{
			if (size == a.length)
			{
				a = Arrays.copyOf(a, size * 2);
			}
			a[size++] = v;
		}

		short[] toArray()
		{
			return Arrays.copyOf(a, size);
		}
	}
}
