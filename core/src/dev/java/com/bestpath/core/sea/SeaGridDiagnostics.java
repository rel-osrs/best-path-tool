package com.bestpath.core.sea;

/**
 * What development code reads from a {@link SeaGrid} that the plugin itself never needs: the parts the sea file
 * stores, a tile's steps, and tiles by number.
 */
public final class SeaGridDiagnostics
{
	private SeaGridDiagnostics()
	{
	}

	/** The x of tile number i. */
	public static int x(SeaGrid grid, int i)
	{
		return tileOf(grid, i) >>> 14;
	}

	/** The y of tile number i. */
	public static int y(SeaGrid grid, int i)
	{
		return tileOf(grid, i) & 0x3FFF;
	}

	/** The sea area ordinal of tile number i. */
	public static int areaOrdinal(SeaGrid grid, int i)
	{
		int t = tileOf(grid, i);
		return grid.areaOrdinalAt(t >>> 14, t & 0x3FFF);
	}

	/** The clearance of tile number i: 1 = an obstacle is next to it, up to {@link SeaGrid#MAX_CLEARANCE}. */
	public static int clearance(SeaGrid grid, int i)
	{
		int t = tileOf(grid, i);
		return grid.clearanceAt(t >>> 14, t & 0x3FFF);
	}

	/** Whether a boat can step from tile number i in direction d (see {@link SeaGrid#DX}). */
	public static boolean canMove(SeaGrid grid, int i, int d)
	{
		int t = tileOf(grid, i);
		return grid.canMove(t >>> 14, t & 0x3FFF, d);
	}

	/** The steps of a main-ocean tile, bit per direction. */
	public static int moves(SeaGrid grid, int x, int y)
	{
		int m = 0;
		for (int d = 0; d < 8; d++)
		{
			m |= grid.canMove(x, y, d) ? 1 << d : 0;
		}
		return m;
	}

	/** A region's area ordinal when it is one area throughout, else -1. */
	public static int regionUniformArea(SeaGrid grid, int slot)
	{
		return grid.uniformArea[slot];
	}

	/** A region's areas, 4 bits per local tile (a copy), or null when uniform. */
	public static byte[] regionAreas(SeaGrid grid, int slot)
	{
		return grid.areaNibbles[slot] == null ? null : grid.areaNibbles[slot].clone();
	}

	/** Tiles whose steps differ from the mask's rule, packed x << 14 | y (a copy). */
	public static int[] oddTiles(SeaGrid grid)
	{
		return grid.oddTiles.clone();
	}

	/** Their steps (a copy). */
	public static byte[] oddMoves(SeaGrid grid)
	{
		return grid.oddMoves.clone();
	}

	/** Packed x << 14 | y of tile number i (a small search: prefer the tile methods in loops). */
	private static int tileOf(SeaGrid g, int i)
	{
		if (i < 0 || i >= g.size())
		{
			throw new IndexOutOfBoundsException("sea tile " + i);
		}
		int lo = 0;
		int hi = g.keys.length - 1;
		while (lo < hi)
		{
			int mid = (lo + hi + 1) >>> 1;
			if (g.base[mid] <= i)
			{
				lo = mid;
			}
			else
			{
				hi = mid - 1;
			}
		}
		int s = lo;
		int r = i - g.base[s];
		int col = 63;
		while (g.colRank[s][col] > r)
		{
			col--;
		}
		long w = g.mask[s][col];
		for (int k = r - g.colRank[s][col]; k > 0; k--)
		{
			w &= w - 1;
		}
		int y = Long.numberOfTrailingZeros(w);
		return ((g.keys[s] >> 8) << 6 | col) << 14 | ((g.keys[s] & 0xFF) << 6 | y);
	}
}
