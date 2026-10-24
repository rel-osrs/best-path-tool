package com.bestpath.core.path;

import com.bestpath.core.graph.CoarseGraph;
import java.util.HashMap;
import java.util.Map;

/**
 * Walking distances inside components that are small but slow to work out again, kept between queries: between
 * each component's entrance sides, from single tiles (transport ends, players, destinations) to their component's
 * entrance sides, from the tiles beside a solid transport end to them, and between the transport ends of each
 * component ({@link EndWalks}). Walking tables made ahead of time ({@link WalkTables}) are read first, before
 * anything is worked out. One query at a time.
 */
final class ComponentWalks
{
	/** Per-tile entrance distances kept before the cache starts again. */
	private static final int MAX_CACHED_TILES = 200_000;

	final CoarseGraph graph;
	final EntranceSides sides;
	final CellWalks cells;
	/** Per component: walking distances between its entrance sides (k x k), made when first needed. */
	final Map<Integer, int[]> matrices = new HashMap<>();
	/** Per tile: walks to its component's entrance sides (see {@link #sideDists(int, int)}). */
	final Map<Integer, int[]> sideDists = new HashMap<>();
	/**
	 * The tiles beside a solid end in one component (one coarse node): walking distances from them to the
	 * component's entrance sides; cached by (solid tile, first tile).
	 */
	final Map<Long, int[]> besideSideDists = new HashMap<>();
	/** The walking tables made ahead of time (the plugin data's walks file), read before the caches; null: none. Never dropped. */
	WalkTables shipped;
	/** Walks between transport ends inside each component, kept (small: a few numbers each). */
	EndWalks ends;

	ComponentWalks(CoarseGraph graph, EntranceSides sides, CellWalks cells)
	{
		this.graph = graph;
		this.sides = sides;
		this.cells = cells;
		ends = new EndWalks(null, cells);
	}

	/** The kept walks between transport ends (replaced when they are dropped: ask again after any change). */
	EndWalks ends()
	{
		return ends;
	}

	/** Before a query: drops the walks between transport ends when they grew past their limit. */
	void keepEndsWithinLimit()
	{
		if (ends.overLimit())
		{
			ends = new EndWalks(shipped, cells);
		}
	}

	/** True when walking tables made ahead of time are in use. */
	boolean hasShipped()
	{
		return shipped != null;
	}

	/** Uses walking tables made ahead of time (already checked to fit this map's layout). */
	void useShipped(WalkTables tables)
	{
		shipped = tables;
		ends = new EndWalks(shipped, cells);
	}

	/** Walking distances between the entrance sides of a component (row-major k x k), cached. */
	int[] matrix(int c)
	{
		int[] m = matrices.get(c);
		if (m == null && shipped != null)
		{
			m = shipped.matrix(c);
			if (m != null)
			{
				matrices.put(c, m);
			}
		}
		if (m != null)
		{
			return m;
		}
		int[] cs = sides.compSides[c];
		int[][] sideTiles = sides.sideTiles;
		int k = cs.length;
		m = new int[k * k];
		for (int i = 0; i < k; i++)
		{
			short[] from = cells.walkFrom(sideTiles[cs[i]], cells.scratch);
			for (int j = 0; j < k; j++)
			{
				m[i * k + j] = i == j ? 0 : minOver(from, sideTiles[cs[j]]);
			}
		}
		matrices.put(c, m);
		return m;
	}

	/**
	 * Walking distance inside the cell from a tile of component {@code c} to each of that component's
	 * entrance sides ({@link EntranceSides#compSides} order; INF if none). Cached by tile and kept: a few numbers per
	 * tile (transport ends, players, destinations), where a whole distance table is 8 KB.
	 */
	int[] sideDists(int tile, int c)
	{
		int[] r = sideDists.get(tile);
		if (r == null)
		{
			r = shipped == null ? null : shipped.sideDists(tile);
			if (r == null)
			{
				short[] from = cells.walkFrom(tile, cells.scratch);
				int[] cs = sides.compSides[c];
				r = new int[cs.length];
				for (int j = 0; j < cs.length; j++)
				{
					r[j] = minOver(from, sides.sideTiles[cs[j]]);
				}
			}
			if (sideDists.size() >= MAX_CACHED_TILES)
			{
				sideDists.clear(); // a different world of transports and destinations: start again
			}
			sideDists.put(tile, r);
		}
		return r;
	}

	/**
	 * The tiles {@code set} beside solid end {@code end} (one component): walking distances to its entrance sides,
	 * cached.
	 */
	int[] besideSideDists(int end, int[] set, int c)
	{
		long key = (long) end << 32 | set[0];
		return besideSideDists.computeIfAbsent(key, k ->
		{
			int[] made = shipped == null ? null : shipped.besideSideDists(key);
			if (made != null)
			{
				return made;
			}
			short[] from = cells.walkFrom(set, cells.scratch);
			int[] cs = sides.compSides[c];
			int[] out = new int[cs.length];
			for (int j = 0; j < cs.length; j++)
			{
				out[j] = minOver(from, sides.sideTiles[cs[j]]);
			}
			return out;
		});
	}

	/** The least walking distance in {@code dist} to any of {@code tiles} (INF: none reached). */
	static int minOver(short[] dist, int[] tiles)
	{
		int best = CoarseSearch.INF;
		for (int t : tiles)
		{
			int v = dist[Tiles.local(t)];
			if (v >= 0 && v < best)
			{
				best = v;
			}
		}
		return best;
	}

	/** About how many bytes these tables take (see {@link Pathfinder#slowToRedoBytes()}). */
	long bytes()
	{
		long n = 0;
		for (int[] m : matrices.values())
		{
			n += 16 + 4L * m.length + 64;
		}
		for (int[] d : sideDists.values())
		{
			n += 16 + 4L * d.length + 64;
		}
		for (int[] d : besideSideDists.values())
		{
			n += 16 + 4L * d.length + 64;
		}
		return n + ends.bytes();
	}

	/** Forgets every table worked out (the tables made ahead of time stay in use). */
	void clear()
	{
		besideSideDists.clear();
		matrices.clear();
		sideDists.clear();
		ends = new EndWalks(shipped, cells);
	}
}
