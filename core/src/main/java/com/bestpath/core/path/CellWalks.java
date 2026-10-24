package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.graph.CoarseGraph;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Breadth-first walks inside one 64x64 cell: the number of steps from some tiles to every other tile of the same
 * cell, following the walking map's step bits. The coarse stage builds all of its walking tables from these walks.
 * Keeps the step bits of the cells used most recently, and one table and queue that every walk uses again instead
 * of making new ones (the search makes thousands of walks; one query at a time).
 */
final class CellWalks
{
	private final CollisionMap map;
	/** Step bits by cell key ({@code regionId << 2 | plane}), the most recently used ones. */
	private final Map<Integer, byte[]> cellBits = new LinkedHashMap<Integer, byte[]>(256, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> e)
		{
			return size() > 2048;
		}
	};

	/**
	 * For walks only read at once (component tables, a tile's distances to its entrance sides): one table used again
	 * and again instead of a new one each time.
	 */
	final short[] scratch = new short[4096];
	private final int[] queue = new int[4096];

	CellWalks(CollisionMap map)
	{
		this.map = map;
	}

	/** The step bits of a cell (cached). */
	byte[] bits(int cell)
	{
		byte[] bits = cellBits.get(cell);
		if (bits == null)
		{
			bits = map.cellStepBits(cell >> 2, cell & 3);
			if (bits == null)
			{
				bits = new byte[4096];
			}
			cellBits.put(cell, bits);
		}
		return bits;
	}

	/** As {@link #walkFrom(int[], short[])} from one tile. */
	short[] walkFrom(int tile, short[] dist)
	{
		Arrays.fill(dist, (short) -1);
		int l = Tiles.local(tile);
		dist[l] = 0;
		queue[0] = l;
		return walkOn(bits(Tiles.cell(tile)), dist, 1);
	}

	/**
	 * Breadth-first walk inside one cell from some tiles (all in that cell), into {@code dist}: steps per local tile, -1 =
	 * not reached (overwritten; {@link #scratch} for a walk read at once).
	 */
	short[] walkFrom(int[] tiles, short[] dist)
	{
		byte[] bits = bits(Tiles.cell(tiles[0]));
		Arrays.fill(dist, (short) -1);
		int tail = 0;
		for (int t : tiles)
		{
			int l = Tiles.local(t);
			if (dist[l] < 0)
			{
				dist[l] = 0;
				queue[tail++] = l;
			}
		}
		return walkOn(bits, dist, tail);
	}

	/** The breadth-first walk itself, from the {@code tail} tiles already in {@link #queue}. */
	private short[] walkOn(byte[] bits, short[] dist, int tail)
	{
		int[] queue = this.queue;
		int head = 0;
		while (head < tail)
		{
			int l = queue[head++];
			int lx = l >> 6;
			int ly = l & 63;
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					int nx = lx + dx;
					int ny = ly + dy;
					if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx > 63 || ny > 63)
					{
						continue;
					}
					int n = nx << 6 | ny;
					if (dist[n] < 0 && CoarseGraph.canStepInCell(bits, lx, ly, dx, dy))
					{
						dist[n] = (short) (dist[l] + 1);
						queue[tail++] = n;
					}
				}
			}
		}
		return dist;
	}

	/** Forgets the kept step bits (they are read again when next needed). */
	void clear()
	{
		cellBits.clear();
	}

	/** About how many bytes the kept step bits take. */
	long bytes()
	{
		return cellBits.size() * (4112L + 64);
	}
}
