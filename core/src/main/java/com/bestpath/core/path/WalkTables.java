package com.bestpath.core.path;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The search's slow-to-redo walking tables, worked out ahead of time and shipped with the data: without them the
 * first route through a part of the map spends most of its time working them out.
 * Per component: the walking distances between its entrance sides; per transport end tile: its distances to its
 * component's entrance sides (and the same for the tiles beside a solid end); per component: the distances between
 * its transport ends. Steps inside one cell only, so they depend on the walking map and the transport end tiles, never
 * on the player. Made when the data is made, used through {@link Pathfinder#useWalkTables}; valid only for a search whose components are numbered the same
 * (its graph key, {@link Pathfinder#graphKey()}).
 * <p>Kept packed (about 1 MB for the whole map: a few arrays of 2-byte steps, each side table stored once for both
 * directions when it is the same both ways); the search unpacks the part a route needs into its own caches, which
 * count in its cache budget. Immutable.
 * <p>File (gzip of the packed arrays, big-endian): "BPWK", int version 1, long graph key; side tables: int n, n int
 * components (ascending), n int k, n bytes (1 = one triangle i &lt; j for both directions, 0 = all k*k), n+1 int
 * offsets, int m, m shorts; tile rows: int n, n int tiles (ascending), n+1 int offsets, int m, m shorts; beside rows:
 * int n, n long keys (solid tile &lt;&lt; 32 | first tile beside it, ascending), n+1 int offsets, int m, m shorts; end
 * walks: int n, n int components (ascending), n+1 int tile offsets, int m, m int tiles, n+1 int row offsets, int m,
 * m shorts (per component its n x n rows); set rows: int n, n long keys, n int components, n+1 int offsets, int m,
 * m shorts. Shorts are steps, -1 = none.
 */
public final class WalkTables
{
	static final int MAGIC = 0x4250574B; // "BPWK"
	static final int VERSION = 1;
	/** Distances as the search reads them: unreachable is this. */
	static final int INF = Integer.MAX_VALUE / 4;

	final long graphKey;
	final int[] mKeys;
	final int[] mK;
	final byte[] mSym;
	final int[] mOff;
	final short[] mPool;
	final int[] sKeys;
	final int[] sOff;
	final short[] sPool;
	final long[] bKeys;
	final int[] bOff;
	final short[] bPool;
	final int[] eKeys;
	final int[] eTileOff;
	final int[] eTiles;
	final int[] eRowOff;
	final short[] eRows;
	final long[] esKeys;
	final int[] esComp;
	final int[] esOff;
	final short[] esPool;

	/** One component's end walks, unpacked: its end tiles (number order), a row per end, and the rows of tile sets. */
	static final class Ends
	{
		final int[] tiles;
		final short[][] rows;
		final Map<Long, short[]> setRows;

		Ends(int[] tiles, short[][] rows, Map<Long, short[]> setRows)
		{
			this.tiles = tiles;
			this.rows = rows;
			this.setRows = setRows;
		}
	}

	WalkTables(long graphKey, int[] mKeys, int[] mK, byte[] mSym, int[] mOff, short[] mPool, int[] sKeys, int[] sOff,
		short[] sPool, long[] bKeys, int[] bOff, short[] bPool, int[] eKeys, int[] eTileOff, int[] eTiles, int[] eRowOff,
		short[] eRows, long[] esKeys, int[] esComp, int[] esOff, short[] esPool)
	{
		this.graphKey = graphKey;
		this.mKeys = mKeys;
		this.mK = mK;
		this.mSym = mSym;
		this.mOff = mOff;
		this.mPool = mPool;
		this.sKeys = sKeys;
		this.sOff = sOff;
		this.sPool = sPool;
		this.bKeys = bKeys;
		this.bOff = bOff;
		this.bPool = bPool;
		this.eKeys = eKeys;
		this.eTileOff = eTileOff;
		this.eTiles = eTiles;
		this.eRowOff = eRowOff;
		this.eRows = eRows;
		this.esKeys = esKeys;
		this.esComp = esComp;
		this.esOff = esOff;
		this.esPool = esPool;
	}

	private static int distance(short s)
	{
		return s < 0 ? INF : s;
	}

	/** Component {@code c}'s distances between its entrance sides (k x k, row-major), unpacked; null if not there. */
	int[] matrix(int c)
	{
		int i = Arrays.binarySearch(mKeys, c);
		if (i < 0)
		{
			return null;
		}
		int k = mK[i];
		int[] m = new int[k * k];
		int p = mOff[i];
		for (int a = 0; a < k; a++)
		{
			if (mSym[i] == 1)
			{
				for (int b = a + 1; b < k; b++)
				{
					m[a * k + b] = m[b * k + a] = distance(mPool[p++]);
				}
			}
			else
			{
				for (int b = 0; b < k; b++)
				{
					m[a * k + b] = distance(mPool[p++]);
				}
			}
		}
		return m;
	}

	/** A transport end tile's distances to its component's entrance sides, unpacked; null if not in the tables. */
	int[] sideDists(int tile)
	{
		int i = Arrays.binarySearch(sKeys, tile);
		return i < 0 ? null : unpack(sPool, sOff[i], sOff[i + 1]);
	}

	/** The same for the tiles beside a solid end ({@code solid tile << 32 | first tile beside it}); null if not in the tables. */
	int[] besideSideDists(long key)
	{
		int i = Arrays.binarySearch(bKeys, key);
		return i < 0 ? null : unpack(bPool, bOff[i], bOff[i + 1]);
	}

	private static int[] unpack(short[] pool, int from, int to)
	{
		int[] out = new int[to - from];
		for (int j = from; j < to; j++)
		{
			out[j - from] = distance(pool[j]);
		}
		return out;
	}

	/** Component {@code c}'s end walks, unpacked (new arrays); null if not in the tables. */
	Ends ends(int c)
	{
		int i = Arrays.binarySearch(eKeys, c);
		if (i < 0)
		{
			return null;
		}
		int[] tiles = Arrays.copyOfRange(eTiles, eTileOff[i], eTileOff[i + 1]);
		int n = tiles.length;
		short[][] rows = new short[n][];
		for (int r = 0; r < n; r++)
		{
			rows[r] = Arrays.copyOfRange(eRows, eRowOff[i] + r * n, eRowOff[i] + (r + 1) * n);
		}
		Map<Long, short[]> setRows = new HashMap<>();
		for (int s = 0; s < esKeys.length; s++)
		{
			if (esComp[s] == c)
			{
				setRows.put(esKeys[s], Arrays.copyOfRange(esPool, esOff[s], esOff[s + 1]));
			}
		}
		return new Ends(tiles, rows, setRows);
	}

	public static WalkTables read(InputStream in) throws IOException
	{
		DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)));
		if (d.readInt() != MAGIC)
		{
			throw new IOException("not a walk tables file");
		}
		int version = d.readInt();
		if (version != VERSION)
		{
			throw new IOException("walk tables version " + version + ", expected " + VERSION);
		}
		long key = d.readLong();
		int n = d.readInt();
		int[] mKeys = readInts(d, n);
		int[] mK = readInts(d, n);
		byte[] mSym = new byte[n];
		d.readFully(mSym);
		int[] mOff = readInts(d, n + 1);
		short[] mPool = readShorts(d);
		n = d.readInt();
		int[] sKeys = readInts(d, n);
		int[] sOff = readInts(d, n + 1);
		short[] sPool = readShorts(d);
		n = d.readInt();
		long[] bKeys = readLongs(d, n);
		int[] bOff = readInts(d, n + 1);
		short[] bPool = readShorts(d);
		n = d.readInt();
		int[] eKeys = readInts(d, n);
		int[] eTileOff = readInts(d, n + 1);
		int[] eTiles = readInts(d, d.readInt());
		int[] eRowOff = readInts(d, n + 1);
		short[] eRows = readShorts(d);
		n = d.readInt();
		long[] esKeys = readLongs(d, n);
		int[] esComp = readInts(d, n);
		int[] esOff = readInts(d, n + 1);
		short[] esPool = readShorts(d);
		return new WalkTables(key, mKeys, mK, mSym, mOff, mPool, sKeys, sOff, sPool, bKeys, bOff, bPool, eKeys, eTileOff, eTiles, eRowOff,
			eRows, esKeys, esComp, esOff, esPool);
	}

	private static int[] readInts(DataInputStream d, int n) throws IOException
	{
		int[] v = new int[n];
		for (int i = 0; i < n; i++)
		{
			v[i] = d.readInt();
		}
		return v;
	}

	private static long[] readLongs(DataInputStream d, int n) throws IOException
	{
		long[] v = new long[n];
		for (int i = 0; i < n; i++)
		{
			v[i] = d.readLong();
		}
		return v;
	}

	private static short[] readShorts(DataInputStream d) throws IOException
	{
		short[] v = new short[d.readInt()];
		for (int i = 0; i < v.length; i++)
		{
			v[i] = d.readShort();
		}
		return v;
	}
}
