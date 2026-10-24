package com.bestpath.core.collision;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * The compact, immutable movement data the pathfinder uses. Per tile it
 * stores four "can step" bits: north, east, north-east and north-west. The
 * other four directions are read from the neighbour's bits, because steps
 * are symmetric (see {@link MovementRules}).
 *
 * <p>Storage: one block of 256 longs (4 bits x 4096 tiles = 2 KB) per
 * region-plane that has any walkable tile.
 */
public final class CollisionMap
{
	/** Step bits, as returned by {@link #cellStepBits}. */
	public static final int N = 1;
	public static final int E = 2;
	public static final int NE = 4;
	public static final int NW = 8;
	static final int LONGS_PER_BLOCK = 256;

	/** Sorted keys (regionId << 2 | plane) and their blocks, for binary search. */
	final int[] keys;
	final long[][] blocks;
	final int cacheId;
	final int cacheBuild;

	CollisionMap(int[] keys, long[][] blocks, int cacheId, int cacheBuild)
	{
		this.keys = keys;
		this.blocks = blocks;
		this.cacheId = cacheId;
		this.cacheBuild = cacheBuild;
	}

	static int key(int regionId, int plane)
	{
		return regionId << 2 | plane;
	}

	/** Can the player step from (x, y, plane) by (dx, dy), each -1, 0 or 1? */
	public boolean canStep(int x, int y, int plane, int dx, int dy)
	{
		if (dx == 0 && dy == 0)
		{
			return true;
		}
		switch (dx * 3 + dy)
		{
			case 1: // (0, 1)
				return bit(x, y, plane, N);
			case -1: // (0, -1)
				return bit(x, y - 1, plane, N);
			case 3: // (1, 0)
				return bit(x, y, plane, E);
			case -3: // (-1, 0)
				return bit(x - 1, y, plane, E);
			case 4: // (1, 1)
				return bit(x, y, plane, NE);
			case -4: // (-1, -1)
				return bit(x - 1, y - 1, plane, NE);
			case -2: // (-1, 1)
				return bit(x, y, plane, NW);
			case 2: // (1, -1): the reverse of a north-west step from (x+1, y-1)
				return bit(x + 1, y - 1, plane, NW);
			default:
				throw new IllegalArgumentException("step must be -1..1 in each axis: " + dx + "," + dy);
		}
	}

	private boolean bit(int x, int y, int plane, int which)
	{
		return (nibble(x, y, plane) & which) != 0;
	}

	/** The four step bits of a tile (0 if the region-plane has no data). */
	int nibble(int x, int y, int plane)
	{
		if (x < 0 || y < 0)
		{
			return 0;
		}
		int i = Arrays.binarySearch(keys, key((x >> 6) << 8 | (y >> 6), plane));
		if (i < 0)
		{
			return 0;
		}
		int tile = (x & 63) << 6 | (y & 63);
		return (int) (blocks[i][tile >> 4] >>> ((tile & 15) << 2)) & 0xF;
	}

	/**
	 * The four step bits (N=1, E=2, NE=4, NW=8) of every tile in one cell
	 * (region-plane), indexed {@code localX << 6 | localY}; null if the cell
	 * has no walkable tile.
	 */
	public byte[] cellStepBits(int regionId, int plane)
	{
		int i = Arrays.binarySearch(keys, key(regionId, plane));
		if (i < 0)
		{
			return null;
		}
		byte[] out = new byte[4096];
		long[] b = blocks[i];
		for (int t = 0; t < 4096; t++)
		{
			out[t] = (byte) ((b[t >> 4] >>> ((t & 15) << 2)) & 0xF);
		}
		return out;
	}

	/**
	 * Can any of the eight steps be taken from this tile? {@code cellIndex} is
	 * the tile's position in {@link #cellKeys()}, {@code local} is
	 * {@code localX << 6 | localY}. Steps into the tile are stored on its
	 * south, west, south-west and south-east neighbours; inside the cell they
	 * are read from the same block, on the border through the neighbour cell.
	 */
	public boolean hasStep(int cellIndex, int local)
	{
		long[] b = blocks[cellIndex];
		if (nibble(b, local) != 0)
		{
			return true;
		}
		int lx = local >> 6;
		int ly = local & 63;
		if (lx > 0 && lx < 63 && ly > 0)
		{
			return (nibble(b, lx << 6 | (ly - 1)) & N) != 0
				|| (nibble(b, (lx - 1) << 6 | ly) & E) != 0
				|| (nibble(b, (lx - 1) << 6 | (ly - 1)) & NE) != 0
				|| (nibble(b, (lx + 1) << 6 | (ly - 1)) & NW) != 0;
		}
		int region = keys[cellIndex] >> 2;
		int plane = keys[cellIndex] & 3;
		int x = ((region >> 8) << 6) + lx;
		int y = ((region & 0xFF) << 6) + ly;
		return bit(x, y - 1, plane, N) || bit(x - 1, y, plane, E) || bit(x - 1, y - 1, plane, NE) || bit(x + 1, y - 1, plane, NW);
	}

	private static int nibble(long[] block, int tile)
	{
		return (int) (block[tile >> 4] >>> ((tile & 15) << 2)) & 0xF;
	}

	/** Which tiles a restricted copy keeps (see {@link #restrictTo}). */
	public interface TileFilter
	{
		boolean allows(int x, int y, int plane);
	}

	/**
	 * A copy that only steps between allowed tiles: a step is kept when the tile it starts from, the
	 * tile it ends on and (for a diagonal) both tiles it passes are allowed. Cells left with no step
	 * are dropped. Used for free-to-play worlds (only free land allowed).
	 */
	public CollisionMap restrictTo(TileFilter allowed)
	{
		int[] outKeys = new int[keys.length];
		long[][] outBlocks = new long[keys.length][];
		int n = 0;
		for (int i = 0; i < keys.length; i++)
		{
			int region = keys[i] >> 2;
			int plane = keys[i] & 3;
			int bx = (region >> 8) << 6;
			int by = (region & 0xFF) << 6;
			long[] in = blocks[i];
			long[] out = new long[LONGS_PER_BLOCK];
			boolean any = false;
			for (int t = 0; t < 4096; t++)
			{
				int bits = nibble(in, t);
				if (bits == 0)
				{
					continue;
				}
				int x = bx + (t >> 6);
				int y = by + (t & 63);
				if (!allowed.allows(x, y, plane))
				{
					continue;
				}
				boolean n1 = allowed.allows(x, y + 1, plane);
				boolean e1 = allowed.allows(x + 1, y, plane);
				boolean w1 = allowed.allows(x - 1, y, plane);
				int keep = 0;
				if ((bits & N) != 0 && n1)
				{
					keep |= N;
				}
				if ((bits & E) != 0 && e1)
				{
					keep |= E;
				}
				if ((bits & NE) != 0 && n1 && e1 && allowed.allows(x + 1, y + 1, plane))
				{
					keep |= NE;
				}
				if ((bits & NW) != 0 && n1 && w1 && allowed.allows(x - 1, y + 1, plane))
				{
					keep |= NW;
				}
				if (keep != 0)
				{
					out[t >> 4] |= (long) keep << ((t & 15) << 2);
					any = true;
				}
			}
			if (any)
			{
				outKeys[n] = keys[i];
				outBlocks[n++] = out;
			}
		}
		return new CollisionMap(Arrays.copyOf(outKeys, n), Arrays.copyOf(outBlocks, n), cacheId, cacheBuild);
	}

	/**
	 * A copy that also stores these cells ({@code regionId << 2 | plane}); cells that were missing get no steps. The
	 * search only uses tiles in a stored cell, so this lets transports chain through a tile nobody walks on, such as
	 * the player-owned house spot where house teleports land and the house's transports leave. Returns this map if
	 * nothing is missing.
	 */
	public CollisionMap withEmptyCells(Collection<Integer> cellKeys)
	{
		TreeMap<Integer, long[]> out = new TreeMap<>();
		for (int k : cellKeys)
		{
			if (Arrays.binarySearch(keys, k) < 0)
			{
				out.put(k, new long[LONGS_PER_BLOCK]);
			}
		}
		if (out.isEmpty())
		{
			return this;
		}
		for (int i = 0; i < keys.length; i++)
		{
			out.put(keys[i], blocks[i]);
		}
		int[] k = new int[out.size()];
		long[][] b = new long[out.size()][];
		int n = 0;
		for (Map.Entry<Integer, long[]> e : out.entrySet())
		{
			k[n] = e.getKey();
			b[n++] = e.getValue();
		}
		return new CollisionMap(k, b, cacheId, cacheBuild);
	}

	/** Every stored cell as {@code regionId << 2 | plane}, ascending. */
	public int[] cellKeys()
	{
		return keys.clone();
	}

	/** Number of region-planes stored. */
	public int size()
	{
		return keys.length;
	}
}
