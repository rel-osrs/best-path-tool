package com.bestpath.core.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which steps go through a door or gate. Doors count as open, but going
 * through one costs a little extra so routes do not slip through odd gates
 * when walking round is about as quick. A step goes through a door when it
 * is possible with the doors open and not with them shut. Immutable after
 * building.
 */
public final class DoorMap
{
	/** Directions: north, north-east, east, south-east, south, south-west, west, north-west (as in the searches). */
	static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
	static final int[] DY = {1, 1, 0, -1, -1, -1, 0, 1};

	/** Per cell (regionId << 2 | plane): per tile a bit per direction whose step goes through a door. */
	final Map<Integer, byte[]> cells = new HashMap<>();
	/**
	 * The compact form ({@link #fromNorthHalf}; null otherwise): the tiles that have door bits (packed, ascending)
	 * and their bits. A few KB where the per-cell arrays take 4 KB per cell with a door.
	 */
	int[] sparseTiles;
	private byte[] sparseBits;

	DoorMap()
	{
	}

	/** No doors at all. */
	public static DoorMap none()
	{
		return new DoorMap();
	}

	/**
	 * Remakes a door map from its north half (as the shipped data file stores
	 * it): per tile the bits of directions north, north-east, east and
	 * south-east (bits 0-3). Every door step is marked on both of its tiles,
	 * so the other four directions are those steps backwards.
	 *
	 * @param tiles packed tiles ({@link Tiles#pack})
	 * @param bits  per tile its N/NE/E/SE bits (bit d = direction d, d &lt; 4)
	 */
	public static DoorMap fromNorthHalf(int[] tiles, byte[] bits)
	{
		DoorMap m = new DoorMap();
		for (int i = 0; i < tiles.length; i++)
		{
			int x = Tiles.x(tiles[i]);
			int y = Tiles.y(tiles[i]);
			int p = Tiles.plane(tiles[i]);
			for (int d = 0; d < 4; d++)
			{
				if ((bits[i] & (1 << d)) != 0)
				{
					m.mark(x, y, p, d);
					m.mark(x + DX[d], y + DY[d], p, d + 4);
				}
			}
		}
		// kept compact: a sorted list of the tiles with door bits
		List<Integer> keys = new ArrayList<>(m.cells.keySet());
		Collections.sort(keys);
		int n = 0;
		for (int k : keys)
		{
			for (byte b : m.cells.get(k))
			{
				n += b != 0 ? 1 : 0;
			}
		}
		DoorMap c = new DoorMap();
		c.sparseTiles = new int[n];
		c.sparseBits = new byte[n];
		int i = 0;
		for (int k : keys)
		{
			byte[] cb = m.cells.get(k);
			for (int l = 0; l < 4096; l++)
			{
				if (cb[l] != 0)
				{
					c.sparseTiles[i] = Tiles.fromCell(k, l);
					c.sparseBits[i] = cb[l];
					i++;
				}
			}
		}
		// sorted by packed tile, so a tile is found by binary search
		Integer[] order = new Integer[n];
		for (int j = 0; j < n; j++)
		{
			order[j] = j;
		}
		int[] t = c.sparseTiles;
		Arrays.sort(order, (a, b) -> Integer.compare(t[a], t[b]));
		int[] st = new int[n];
		byte[] sb = new byte[n];
		for (int j = 0; j < n; j++)
		{
			st[j] = t[order[j]];
			sb[j] = c.sparseBits[order[j]];
		}
		c.sparseTiles = st;
		c.sparseBits = sb;
		return c;
	}

	void mark(int x, int y, int p, int d)
	{
		int cell = ((x >> 6) << 8 | (y >> 6)) << 2 | p;
		cells.computeIfAbsent(cell, k -> new byte[4096])[(x & 63) << 6 | (y & 63)] |= (byte) (1 << d);
	}

	/** The door bits of every tile of a cell (index localX << 6 | localY), or null if the cell has no door. Do not modify. */
	public byte[] cellBits(int cell)
	{
		if (sparseTiles != null)
		{
			byte[] b = null;
			for (int i = 0; i < sparseTiles.length; i++)
			{
				if (Tiles.cell(sparseTiles[i]) == cell)
				{
					if (b == null)
					{
						b = new byte[4096];
					}
					b[Tiles.local(sparseTiles[i])] = sparseBits[i];
				}
			}
			return b;
		}
		return cells.get(cell);
	}

	/** The door bits (bit d = direction d) of a tile; 0 if none. */
	public int bits(int x, int y, int p)
	{
		if (sparseTiles != null)
		{
			if (x < 0 || y < 0 || x > 0x3FFF || y > 0x3FFF)
			{
				return 0;
			}
			int i = Arrays.binarySearch(sparseTiles, Tiles.pack(x, y, p));
			return i < 0 ? 0 : sparseBits[i] & 0xFF;
		}
		byte[] b = cells.get(((x >> 6) << 8 | (y >> 6)) << 2 | p);
		return b == null ? 0 : b[(x & 63) << 6 | (y & 63)] & 0xFF;
	}

	/** Does the step from one tile to a neighbouring tile go through a door? */
	public boolean crosses(int fromTile, int toTile)
	{
		int dx = Tiles.x(toTile) - Tiles.x(fromTile);
		int dy = Tiles.y(toTile) - Tiles.y(fromTile);
		for (int d = 0; d < 8; d++)
		{
			if (DX[d] == dx && DY[d] == dy)
			{
				return (bits(Tiles.x(fromTile), Tiles.y(fromTile), Tiles.plane(fromTile)) & (1 << d)) != 0;
			}
		}
		return false;
	}

	public boolean isEmpty()
	{
		return sparseTiles != null ? sparseTiles.length == 0 : cells.isEmpty();
	}
}
