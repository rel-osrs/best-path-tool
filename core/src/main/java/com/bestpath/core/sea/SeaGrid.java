package com.bestpath.core.sea;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The main ocean as a search grid for boats: which tiles are main ocean, each
 * tile's sea area, the steps a boat can take from it and its clearance (how
 * far the nearest obstacle is). Seas cut off from the main ocean are left out
 * (players cannot reach them). Immutable, apart from the clearance table that
 * can be dropped to save memory ({@link #dropClearanceTable()}).
 *
 * <p>Steps: 8 directions. A step must stay on the main ocean and be allowed
 * by the movement data (walls); a diagonal step also needs both orthogonal
 * tiles on the main ocean (no cutting a corner of land). Only a handful of
 * tiles have walls between ocean tiles: their steps are kept as exceptions,
 * every other tile's steps follow from the tile mask.
 *
 * <p>Clearance: 1 when a tile next to it (8 around) is not main ocean (land,
 * rocks, a border line, other seas), 2 when the nearest such tile is two
 * tiles away, and so on, up to {@link #MAX_CLEARANCE}. Worked out from the
 * tile mask alone.
 *
 * <p>Compact form, to keep the plugin's memory small: per 64x64 region a tile
 * mask (64 longs, a column of 64 tiles each), the area once per region when it
 * is one area throughout (else 4 bits per tile), clearance 4 bits per tile
 * (identical regions share one array), and the step exceptions. The clearance
 * table is only a quicker way to the same numbers: after the sea graph is
 * built the plugin drops it ({@link #dropClearanceTable()}) and clearance is
 * then worked out from the mask when asked (a few tiles per route), saving
 * about 1.7 MB. Tiles are numbered region by region (ascending region id),
 * column by column ({@link #index}); finding a number's tile
 * is a small search, so code that walks the sea uses tiles, not
 * numbers.
 */
public final class SeaGrid
{
	/** Directions: west, east, south, north, south-west, south-east, north-west, north-east. */
	public static final int[] DX = {-1, 1, 0, 0, -1, 1, -1, 1};
	public static final int[] DY = {0, 0, -1, 1, -1, -1, 1, 1};
	/** Clearance is counted up to this (enough for the widest boat's preferred clearance). */
	public static final int MAX_CLEARANCE = 5;

	/** Region id (x >> 6 << 8 | y >> 6) -> slot, or -1. */
	private final short[] slotOf;
	/** Slot -> region id, ascending. */
	final int[] keys;
	/** Slot -> 64 columns (word = local x, bit = local y): main ocean. Regions that are all ocean share one array. */
	final long[][] mask;
	/** Slot -> main-ocean tiles in the slots before it. */
	final int[] base;
	/** Slot -> per column: main-ocean tiles in the columns before it. */
	final char[][] colRank;
	/** Slot -> the area ordinal when the whole region is one area, else -1 (see {@link #areaNibbles}). */
	final byte[] uniformArea;
	/** Slot -> 4 bits per local tile (index localX << 6 | localY; even index = low bits), or null when uniform. */
	final byte[][] areaNibbles;
	/**
	 * Slot -> clearance, 4 bits per local tile as {@link #areaNibbles}; identical arrays are shared. Null once
	 * dropped ({@link #dropClearanceTable()}): then worked out from the mask.
	 */
	private volatile byte[][] clearance;
	/** Tiles whose steps are not the mask's rule (walls between ocean tiles): packed x << 14 | y, ascending. */
	final int[] oddTiles;
	/** Their steps, bit per direction (see {@link #DX}). */
	final byte[] oddMoves;
	/** Slot -> whether one of {@link #oddTiles} is in it. */
	private final boolean[] slotHasOdd;
	private final int size;

	SeaGrid(int[] keys, long[][] mask, byte[] uniformArea, byte[][] areaNibbles, int[] oddTiles, byte[] oddMoves)
	{
		this.keys = keys;
		this.mask = mask;
		this.uniformArea = uniformArea;
		this.areaNibbles = areaNibbles;
		this.oddTiles = oddTiles;
		this.oddMoves = oddMoves;
		int n = keys.length;
		slotOf = new short[1 << 16];
		Arrays.fill(slotOf, (short) -1);
		base = new int[n];
		colRank = new char[n][];
		int count = 0;
		for (int s = 0; s < n; s++)
		{
			if (s > 0 && keys[s] <= keys[s - 1])
			{
				throw new IllegalArgumentException("sea regions must be ascending");
			}
			slotOf[keys[s]] = (short) s;
			base[s] = count;
			char[] r = new char[64];
			int c = 0;
			for (int col = 0; col < 64; col++)
			{
				r[col] = (char) c;
				c += Long.bitCount(mask[s][col]);
			}
			colRank[s] = r;
			count += c;
		}
		size = count;
		slotHasOdd = new boolean[n];
		for (int i = 0; i < oddTiles.length; i++)
		{
			if (i > 0 && oddTiles[i] <= oddTiles[i - 1])
			{
				throw new IllegalArgumentException("odd step tiles must be ascending");
			}
			int s = slot(oddTiles[i] >>> 14, oddTiles[i] & 0x3FFF);
			if (s < 0 || !isOcean(oddTiles[i] >>> 14, oddTiles[i] & 0x3FFF))
			{
				throw new IllegalArgumentException("odd step tile is not main ocean");
			}
			slotHasOdd[s] = true;
		}
		clearance = clearances();
	}

	// ------------------------------------------------------------------------------------------- building

	/**
	 * A grid from its parts (as the plugin's map data stores them; see
	 * {@code PluginData}). Clearance is worked out here.
	 *
	 * @param keys         region ids, ascending
	 * @param masks        per region 64 columns (word = local x, bit = local y)
	 * @param uniformArea  per region the area ordinal, or -1 when {@code areaNibbles} has it per tile
	 * @param areaNibbles  per region null, or 2048 bytes: 4 bits per local tile (localX << 6 | localY, even = low bits)
	 * @param oddTiles     tiles whose steps differ from the mask's rule, packed x << 14 | y, ascending
	 * @param oddMoves     their steps, bit per direction of {@link #DX}
	 */
	public static SeaGrid of(int[] keys, long[][] masks, byte[] uniformArea, byte[][] areaNibbles, int[] oddTiles, byte[] oddMoves)
	{
		if (masks.length != keys.length || uniformArea.length != keys.length || areaNibbles.length != keys.length
			|| oddTiles.length != oddMoves.length)
		{
			throw new IllegalArgumentException("sea grid parts do not match in length");
		}
		long[][] m = new long[keys.length][];
		byte[][] a = new byte[keys.length][];
		for (int s = 0; s < keys.length; s++)
		{
			if (masks[s].length != 64 || (uniformArea[s] < 0) != (areaNibbles[s] != null)
				|| areaNibbles[s] != null && areaNibbles[s].length != 2048 || uniformArea[s] >= SeaArea.values().length)
			{
				throw new IllegalArgumentException("sea grid region " + keys[s] + " is malformed");
			}
			m[s] = masks[s].clone();
			a[s] = areaNibbles[s] == null ? null : areaNibbles[s].clone();
		}
		return new SeaGrid(keys.clone(), shareFull(m), uniformArea.clone(), a, oddTiles.clone(), oddMoves.clone());
	}

	private static final long[] FULL = full();

	private static long[] full()
	{
		long[] f = new long[64];
		Arrays.fill(f, -1L);
		return f;
	}

	/** Regions that are all ocean share one mask. */
	static long[][] shareFull(long[][] masks)
	{
		for (int s = 0; s < masks.length; s++)
		{
			if (Arrays.equals(masks[s], FULL))
			{
				masks[s] = FULL;
			}
		}
		return masks;
	}

	/**
	 * Clearance for every tile, from the mask: a tile has clearance k + 1 or
	 * more when every tile within k tiles (Chebyshev) is main ocean, i.e. the
	 * mask worn away k times by a 3x3 square. Done on whole columns at once.
	 */
	private byte[][] clearances()
	{
		int n = keys.length;
		long[][] level = mask;
		byte[][] out = new byte[n][];
		for (int s = 0; s < n; s++)
		{
			out[s] = new byte[2048];
		}
		for (int k = 1; k <= MAX_CLEARANCE; k++)
		{
			if (k > 1)
			{
				level = erode(level);
			}
			for (int s = 0; s < n; s++)
			{
				byte[] o = out[s];
				for (int col = 0; col < 64; col++)
				{
					long w = level[s][col];
					while (w != 0)
					{
						int y = Long.numberOfTrailingZeros(w);
						w &= w - 1;
						int l = col << 6 | y;
						// one more level: add 1 to the tile's 4 bits
						o[l >> 1] += (byte) ((l & 1) == 0 ? 1 : 1 << 4);
					}
				}
			}
		}
		// identical regions (open water far from land) share one array
		Map<ByteBuffer, byte[]> seen = new HashMap<>();
		for (int s = 0; s < n; s++)
		{
			byte[] c = out[s];
			out[s] = seen.computeIfAbsent(ByteBuffer.wrap(c), b -> c);
		}
		return out;
	}

	/** The mask worn away by a 3x3 square (a tile stays when all 9 tiles round it are set). */
	private long[][] erode(long[][] m)
	{
		int n = keys.length;
		// first up and down within each column (with the regions above and below)
		long[][] v = new long[n][64];
		for (int s = 0; s < n; s++)
		{
			int rx = keys[s] >> 8;
			int ry = keys[s] & 0xFF;
			int below = slotAt(rx, ry - 1);
			int above = slotAt(rx, ry + 1);
			for (int col = 0; col < 64; col++)
			{
				long c = m[s][col];
				long fromBelow = below < 0 ? 0 : m[below][col] >>> 63;
				long fromAbove = above < 0 ? 0 : (m[above][col] & 1L) << 63;
				v[s][col] = c & (c << 1 | fromBelow) & (c >>> 1 | fromAbove);
			}
		}
		// then across the columns (with the regions west and east)
		long[][] out = new long[n][];
		for (int s = 0; s < n; s++)
		{
			int rx = keys[s] >> 8;
			int ry = keys[s] & 0xFF;
			int west = slotAt(rx - 1, ry);
			int east = slotAt(rx + 1, ry);
			long[] o = new long[64];
			for (int col = 0; col < 64; col++)
			{
				long l = col > 0 ? v[s][col - 1] : west < 0 ? 0 : v[west][63];
				long r = col < 63 ? v[s][col + 1] : east < 0 ? 0 : v[east][0];
				o[col] = l & v[s][col] & r;
			}
			out[s] = o;
		}
		return out;
	}

	private int slotAt(int rx, int ry)
	{
		return rx < 0 || ry < 0 || rx > 255 || ry > 255 ? -1 : slotOf[rx << 8 | ry];
	}

	// ------------------------------------------------------------------------------------------- by tile

	/** The region slot of a tile, or -1 when its region has no main ocean (for the sea graph's fast walks). */
	int slot(int x, int y)
	{
		return x < 0 || y < 0 || x >= 1 << 14 || y >= 1 << 14 ? -1 : slotOf[(x >> 6) << 8 | (y >> 6)];
	}

	/** Whether local tile l (localX << 6 | localY) of slot s is main ocean. */
	boolean oceanIn(int s, int l)
	{
		return (mask[s][l >> 6] & 1L << (l & 63)) != 0;
	}

	/** Clearance of main-ocean tile l of slot s. */
	int clearanceIn(int s, int l)
	{
		byte[][] c = clearance;
		if (c == null)
		{
			int bx = (keys[s] >> 8) << 6;
			int by = (keys[s] & 0xFF) << 6;
			return clearanceFromMask(bx + (l >> 6), by + (l & 63));
		}
		return nibble(c[s], l);
	}

	/** Area ordinal of main-ocean tile l of slot s. */
	int areaIn(int s, int l)
	{
		return uniformArea[s] >= 0 ? uniformArea[s] : nibble(areaNibbles[s], l);
	}

	/** As {@link #canMove(int, int, int)} for a tile known to be main ocean, in slot s. */
	boolean canMoveIn(int s, int x, int y, int d)
	{
		if (slotHasOdd[s])
		{
			int o = Arrays.binarySearch(oddTiles, x << 14 | y);
			if (o >= 0)
			{
				return (oddMoves[o] & 1 << d) != 0;
			}
		}
		byte[][] c = clearance;
		if (c != null && nibble(c[s], (x & 63) << 6 | (y & 63)) >= 2)
		{
			return true; // all 8 tiles round it are main ocean
		}
		int dx = DX[d];
		int dy = DY[d];
		return isOcean(x + dx, y + dy) && (d < 4 || isOcean(x + dx, y) && isOcean(x, y + dy));
	}

	/** Whether the tile is main ocean (plane 0). */
	public boolean isOcean(int x, int y)
	{
		int s = slot(x, y);
		return s >= 0 && (mask[s][x & 63] & 1L << (y & 63)) != 0;
	}

	/** The tile's clearance (1 = an obstacle is next to it, up to {@link #MAX_CLEARANCE}); 0 when it is not main ocean. */
	public int clearanceAt(int x, int y)
	{
		int s = slot(x, y);
		if (s < 0)
		{
			return 0;
		}
		int l = (x & 63) << 6 | (y & 63);
		if ((mask[s][x & 63] & 1L << (y & 63)) == 0)
		{
			return 0;
		}
		return clearanceIn(s, l);
	}

	/**
	 * Clearance of a main-ocean tile from the mask alone: 1 + the largest k (up to MAX_CLEARANCE - 1) such
	 * that every tile within k tiles (Chebyshev) is main ocean; the same number the table holds (the table is
	 * the mask worn away by a 3x3 square k times, which keeps exactly those tiles).
	 */
	private int clearanceFromMask(int x, int y)
	{
		for (int k = 1; k < MAX_CLEARANCE; k++)
		{
			for (int dx = -k; dx <= k; dx++)
			{
				for (int dy = -k; dy <= k; dy++)
				{
					if ((Math.abs(dx) == k || Math.abs(dy) == k) && !isOcean(x + dx, y + dy))
					{
						return k;
					}
				}
			}
		}
		return MAX_CLEARANCE;
	}

	/**
	 * Forgets the clearance table (about 1.7 MB on the real map); clearance is then worked out from the mask
	 * when asked, with the same answers. For the plugin, once the sea graph is built (the graph keeps each
	 * link's clearance; only the few lines joining docks and points at sea to it ask again).
	 */
	public void dropClearanceTable()
	{
		clearance = null;
	}

	/** Makes the clearance table again if it was dropped (building a sea graph walks millions of tiles). */
	void keepClearanceTable()
	{
		if (clearance == null)
		{
			clearance = clearances();
		}
	}

	/** The tile's {@link SeaArea} ordinal; -1 when it is not main ocean. */
	public int areaOrdinalAt(int x, int y)
	{
		if (!isOcean(x, y))
		{
			return -1;
		}
		int s = slot(x, y);
		return uniformArea[s] >= 0 ? uniformArea[s] : nibble(areaNibbles[s], (x & 63) << 6 | (y & 63));
	}

	private static int nibble(byte[] b, int l)
	{
		int v = b[l >> 1];
		return (l & 1) == 0 ? v & 0x0F : (v >> 4) & 0x0F;
	}

	/** Whether a boat can step from the tile in direction d (see {@link #DX}); false when the tile is not main ocean. */
	public boolean canMove(int x, int y, int d)
	{
		int s = slot(x, y);
		return s >= 0 && (mask[s][x & 63] & 1L << (y & 63)) != 0 && canMoveIn(s, x, y, d);
	}

	/** Receives a main-ocean tile. */
	public interface TileVisitor
	{
		void visit(int x, int y);
	}

	/** Calls the visitor for every main-ocean tile, in tile-number order. */
	public void forEachTile(TileVisitor v)
	{
		for (int s = 0; s < keys.length; s++)
		{
			int bx = (keys[s] >> 8) << 6;
			int by = (keys[s] & 0xFF) << 6;
			for (int col = 0; col < 64; col++)
			{
				long w = mask[s][col];
				while (w != 0)
				{
					int y = Long.numberOfTrailingZeros(w);
					w &= w - 1;
					v.visit(bx + col, by + y);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------- by number

	/** Number of main-ocean tiles. */
	public int size()
	{
		return size;
	}

	/** The tile's number, or -1 when it is not main ocean (plane 0 only). */
	public int index(int x, int y)
	{
		int s = slot(x, y);
		if (s < 0)
		{
			return -1;
		}
		long w = mask[s][x & 63];
		long bit = 1L << (y & 63);
		if ((w & bit) == 0)
		{
			return -1;
		}
		return base[s] + colRank[s][x & 63] + Long.bitCount(w & (bit - 1));
	}

	// ------------------------------------------------------------------------------------------- parts (export)

	/** Regions with main ocean, ascending (a copy). */
	public int[] regionIds()
	{
		return keys.clone();
	}

	/** A region's mask (a copy): 64 columns, word = local x, bit = local y. */
	public long[] regionMask(int slot)
	{
		return mask[slot].clone();
	}

	/**
	 * Whether the other grid has exactly the same tiles, areas and steps (so search results carry over; clearance
	 * follows from the tiles).
	 */
	public boolean sameAs(SeaGrid o)
	{
		if (o == null || !Arrays.equals(keys, o.keys) || !Arrays.equals(uniformArea, o.uniformArea)
			|| !Arrays.equals(oddTiles, o.oddTiles) || !Arrays.equals(oddMoves, o.oddMoves))
		{
			return false;
		}
		for (int s = 0; s < keys.length; s++)
		{
			if (!Arrays.equals(mask[s], o.mask[s]) || !Arrays.equals(areaNibbles[s], o.areaNibbles[s]))
			{
				return false;
			}
		}
		return true;
	}
}
