package com.bestpath.core.data;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.path.LineOfSight;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * The walls that block line of sight (used only to choose where a route
 * stops when the destination itself can't be reached), as {@code walls.bin}
 * stores them: per cell (regionId &lt;&lt; 2 | plane) a 1 KB bitmap, 2 bits per
 * tile at bit {@code (localX << 6 | localY) * 2}: bit 0 = a wall on the
 * tile's north edge, bit 1 = a wall on its east edge (from either side).
 * South and west edges are the north / east edges of the neighbours.
 * Immutable after reading.
 *
 * <p>File (gzip): "BPWL", int version 0, int cell count, per cell: int key
 * then 128 longs, keys ascending.
 */
public final class WallBits implements LineOfSight.WallFlags
{
	public static final int LONGS_PER_CELL = 128;
	static final int MAGIC = 0x4250574C; // "BPWL"
	static final int VERSION = 0;

	final int[] keys;
	final long[][] cells;

	WallBits(int[] keys, long[][] cells)
	{
		this.keys = keys;
		this.cells = cells;
	}

	/** The 2 wall bits of a tile (bit 0 north edge, bit 1 east edge); 0 outside the stored cells. */
	public int bits(int x, int y, int plane)
	{
		if (x < 0 || y < 0 || plane < 0 || plane > 3)
		{
			return 0;
		}
		int i = Arrays.binarySearch(keys, ((x >> 6) << 8 | (y >> 6)) << 2 | plane);
		if (i < 0)
		{
			return 0;
		}
		int bit = ((x & 63) << 6 | (y & 63)) * 2;
		return (int) (cells[i][bit >> 6] >>> (bit & 63)) & 3;
	}

	/**
	 * Tile flags as {@link LineOfSight} reads them: NORTH / EAST from this
	 * tile's bits, SOUTH / WEST from the neighbours' (a wall between two tiles
	 * is the same wall seen from either side).
	 */
	@Override
	public int get(int x, int y, int plane)
	{
		int own = bits(x, y, plane);
		int f = 0;
		if ((own & 1) != 0)
		{
			f |= CollisionFlags.NORTH;
		}
		if ((own & 2) != 0)
		{
			f |= CollisionFlags.EAST;
		}
		if ((bits(x, y - 1, plane) & 1) != 0)
		{
			f |= CollisionFlags.SOUTH;
		}
		if ((bits(x - 1, y, plane) & 2) != 0)
		{
			f |= CollisionFlags.WEST;
		}
		return f;
	}

	public static WallBits read(InputStream in) throws IOException
	{
		DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in), 1 << 16));
		if (d.readInt() != MAGIC)
		{
			throw new IOException("not a BestPath wall file");
		}
		int version = d.readInt();
		if (version != VERSION)
		{
			throw new IOException("unsupported wall file version " + version);
		}
		int n = d.readInt();
		int[] k = new int[n];
		long[][] c = new long[n][LONGS_PER_CELL];
		for (int i = 0; i < n; i++)
		{
			k[i] = d.readInt();
			if (i > 0 && k[i] <= k[i - 1])
			{
				throw new IOException("wall file keys out of order");
			}
			for (int j = 0; j < LONGS_PER_CELL; j++)
			{
				c[i][j] = d.readLong();
			}
		}
		return new WallBits(k, c);
	}
}
