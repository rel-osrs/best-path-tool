package com.bestpath.core.graph;

import com.bestpath.core.collision.CollisionMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The coarse (abstract) graph's walking part: components and entrances.
 *
 * <p>A <b>cell</b> is one 64x64 region on one plane. A <b>component</b> is a
 * set of tiles in one cell that can all reach each other by walking without
 * leaving the cell. An <b>entrance</b> is
 * a run of neighbouring border tiles where one component can step into a
 * component of the next cell. Transports are not part of this graph.
 *
 * <p>Built from a {@link CollisionMap}; immutable afterwards.
 */
public final class CoarseGraph
{
	private static final int[][] DIRS = {{0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}};

	/** The movement data the graph was built from: it says which tiles have a component at all. */
	private final CollisionMap map;
	/** Cells with walkable tiles, as {@code regionId << 2 | plane}, ascending (the same order as the map's). */
	final int[] cellKeys;
	/**
	 * Per cell: the component of each tile with a step, bit-packed (label - 1,
	 * {@link #labelBits} bits per tile, index localX << 6 | localY); null when
	 * the cell has at most one component (nothing to tell apart). Whether a
	 * tile has a component at all comes from the movement data, so it is not
	 * stored: about 0.26 bytes per tile instead of 2, to keep memory low.
	 */
	final long[][] packed;
	private final byte[] labelBits;
	/** Per cell: global id of its first component. */
	private final int[] compBase;

	/** Per component (global id): its cell index and its number of tiles. */
	private final int[] compCell;
	private final int[] compSize;

	/** Per entrance: the two components, source tiles (first and last), and plane. */
	private final int[] entA;
	private final int[] entB;
	private final int[] entX0;
	private final int[] entY0;
	private final int[] entX1;
	private final int[] entY1;
	private final byte[] entPlane;
	final int linkCount;

	private CoarseGraph(CollisionMap map, int[] cellKeys, long[][] packed, byte[] labelBits, int[] compBase, int[] compCell, int[] compSize,
		List<int[]> entrances, int linkCount)
	{
		this.map = map;
		this.cellKeys = cellKeys;
		this.packed = packed;
		this.labelBits = labelBits;
		this.compBase = compBase;
		this.compCell = compCell;
		this.compSize = compSize;
		int n = entrances.size();
		entA = new int[n];
		entB = new int[n];
		entX0 = new int[n];
		entY0 = new int[n];
		entX1 = new int[n];
		entY1 = new int[n];
		entPlane = new byte[n];
		for (int i = 0; i < n; i++)
		{
			int[] e = entrances.get(i);
			entA[i] = e[0];
			entB[i] = e[1];
			entX0[i] = e[2];
			entY0[i] = e[3];
			entX1[i] = e[4];
			entY1[i] = e[5];
			entPlane[i] = (byte) e[6];
		}
		this.linkCount = linkCount;
	}

	/** Builds components and entrances for every cell of the map. */
	public static CoarseGraph build(CollisionMap map)
	{
		int[] keys = map.cellKeys();
		long[][] packed = new long[keys.length][];
		byte[] labelBits = new byte[keys.length];
		int[] base = new int[keys.length];
		List<Integer> cellOfComp = new ArrayList<>();
		List<Integer> sizeOfComp = new ArrayList<>();
		int next = 0;
		short[] lab = new short[4096];
		for (int c = 0; c < keys.length; c++)
		{
			byte[] bits = map.cellStepBits(keys[c] >> 2, keys[c] & 3);
			Arrays.fill(lab, (short) 0);
			int[] sizes = label(bits, lab, borderExits(map, keys[c]));
			for (int l = 0; l < 4096; l++)
			{
				if ((lab[l] != 0) != map.hasStep(c, l))
				{
					throw new IllegalStateException("component labels disagree with the movement data at cell " + keys[c] + " tile " + l);
				}
			}
			if (sizes.length > 1)
			{
				int b = 32 - Integer.numberOfLeadingZeros(sizes.length - 1);
				labelBits[c] = (byte) b;
				packed[c] = pack(lab, b);
			}
			base[c] = next;
			for (int s : sizes)
			{
				cellOfComp.add(c);
				sizeOfComp.add(s);
			}
			next += sizes.length;
		}
		int[] compCell = cellOfComp.stream().mapToInt(Integer::intValue).toArray();
		int[] compSize = sizeOfComp.stream().mapToInt(Integer::intValue).toArray();

		CoarseGraph partial = new CoarseGraph(map, keys, packed, labelBits, base, compCell, compSize, new ArrayList<>(), 0);
		List<int[]> entrances = new ArrayList<>();
		Set<Long> links = new HashSet<>();
		for (int c = 0; c < keys.length; c++)
		{
			findEntrances(map, partial, c, entrances, links);
		}
		return new CoarseGraph(map, keys, packed, labelBits, base, compCell, compSize, entrances, links.size());
	}

	/** Labels 1..n as (label - 1) in {@code b} bits per tile; tiles without a component store 0. */
	private static long[] pack(short[] lab, int b)
	{
		long[] out = new long[(4096 * b + 63) >> 6];
		for (int l = 0; l < 4096; l++)
		{
			if (lab[l] == 0)
			{
				continue;
			}
			long v = lab[l] - 1;
			int bit = l * b;
			out[bit >> 6] |= v << (bit & 63);
			int spill = (bit & 63) + b - 64;
			if (spill > 0)
			{
				out[(bit >> 6) + 1] |= v >>> (b - spill);
			}
		}
		return out;
	}

	/**
	 * Local label (1..n) of a tile in the cell at {@code cellIndex} (its
	 * position in the map's cell list), or 0 if it has no component.
	 */
	public int localLabel(int cellIndex, int local)
	{
		if (!map.hasStep(cellIndex, local))
		{
			return 0;
		}
		long[] p = packed[cellIndex];
		if (p == null)
		{
			return 1;
		}
		int b = labelBits[cellIndex];
		int bit = local * b;
		long v = p[bit >> 6] >>> (bit & 63);
		int spill = (bit & 63) + b - 64;
		if (spill > 0)
		{
			v |= p[(bit >> 6) + 1] << (b - spill);
		}
		return (int) (v & ((1L << b) - 1)) + 1;
	}

	/**
	 * Labels the tiles of one cell by flood fill over steps that stay inside
	 * the cell. Returns the size of each component, in label order.
	 */
	static int[] label(byte[] bits, short[] lab, boolean[] exits)
	{
		if (bits == null)
		{
			return new int[0];
		}
		List<Integer> sizes = new ArrayList<>();
		int[] queue = new int[4096];
		for (int start = 0; start < 4096; start++)
		{
			if (lab[start] != 0 || !(walkable(bits, start) || (exits != null && exits[start])))
			{
				continue;
			}
			short id = (short) (sizes.size() + 1);
			int head = 0;
			int tail = 0;
			queue[tail++] = start;
			lab[start] = id;
			while (head < tail)
			{
				int t = queue[head++];
				int lx = t >> 6;
				int ly = t & 63;
				for (int[] d : DIRS)
				{
					int nx = lx + d[0];
					int ny = ly + d[1];
					if (nx < 0 || ny < 0 || nx > 63 || ny > 63)
					{
						continue;
					}
					int n = nx << 6 | ny;
					if (lab[n] == 0 && canStepInCell(bits, lx, ly, d[0], d[1]))
					{
						lab[n] = id;
						queue[tail++] = n;
					}
				}
			}
			sizes.add(tail);
		}
		return sizes.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Border tiles that can step out of the cell (they may have no step inside it). */
	private static boolean[] borderExits(CollisionMap map, int key)
	{
		int region = key >> 2;
		int plane = key & 3;
		int bx = (region >> 8) << 6;
		int by = (region & 0xFF) << 6;
		boolean[] exits = new boolean[4096];
		for (int lx = 0; lx < 64; lx++)
		{
			for (int ly = 0; ly < 64; ly++)
			{
				if (lx != 0 && ly != 0 && lx != 63 && ly != 63)
				{
					continue;
				}
				for (int[] d : DIRS)
				{
					int nx = lx + d[0];
					int ny = ly + d[1];
					if ((nx < 0 || ny < 0 || nx > 63 || ny > 63) && map.canStep(bx + lx, by + ly, plane, d[0], d[1]))
					{
						exits[lx << 6 | ly] = true;
						break;
					}
				}
			}
		}
		return exits;
	}

	/** A tile belongs to a component if any step from it is possible (inside the cell or out of it). */
	private static boolean walkable(byte[] bits, int t)
	{
		if (bits[t] != 0)
		{
			return true;
		}
		int lx = t >> 6;
		int ly = t & 63;
		// steps that arrive from a neighbour are stored on the neighbour's tile
		return (ly > 0 && (bits[lx << 6 | (ly - 1)] & CollisionMap.N) != 0)
			|| (lx > 0 && (bits[(lx - 1) << 6 | ly] & CollisionMap.E) != 0)
			|| (lx > 0 && ly > 0 && (bits[(lx - 1) << 6 | (ly - 1)] & CollisionMap.NE) != 0)
			|| (lx < 63 && ly > 0 && (bits[(lx + 1) << 6 | (ly - 1)] & CollisionMap.NW) != 0);
	}

	/** Step (dx, dy) from local (lx, ly), with both tiles inside the cell. */
	public static boolean canStepInCell(byte[] bits, int lx, int ly, int dx, int dy)
	{
		switch (dx * 3 + dy)
		{
			case 1:
				return (bits[lx << 6 | ly] & CollisionMap.N) != 0;
			case -1:
				return (bits[lx << 6 | (ly - 1)] & CollisionMap.N) != 0;
			case 3:
				return (bits[lx << 6 | ly] & CollisionMap.E) != 0;
			case -3:
				return (bits[(lx - 1) << 6 | ly] & CollisionMap.E) != 0;
			case 4:
				return (bits[lx << 6 | ly] & CollisionMap.NE) != 0;
			case -4:
				return (bits[(lx - 1) << 6 | (ly - 1)] & CollisionMap.NE) != 0;
			case -2:
				return (bits[lx << 6 | ly] & CollisionMap.NW) != 0;
			case 2:
				return (bits[(lx + 1) << 6 | (ly - 1)] & CollisionMap.NW) != 0;
			default:
				return false;
		}
	}

	/**
	 * Finds steps from border tiles of cell {@code c} into neighbouring cells
	 * with a larger key (so each crossing is seen once), and merges
	 * neighbouring crossings between the same two components into entrances.
	 */
	private static void findEntrances(CollisionMap map, CoarseGraph g, int c, List<int[]> out, Set<Long> links)
	{
		int key = g.cellKeys[c];
		int region = key >> 2;
		int plane = key & 3;
		int bx = (region >> 8) << 6;
		int by = (region & 0xFF) << 6;
		// crossings per neighbour cell: [position along the border, compA, compB, x, y]
		Map<Integer, List<int[]>> byNeighbour = new TreeMap<>();
		for (int i = 0; i < 64 * 4; i++)
		{
			int lx;
			int ly;
			if (i < 64)
			{
				lx = i;
				ly = 0;
			}
			else if (i < 128)
			{
				lx = i - 64;
				ly = 63;
			}
			else if (i < 192)
			{
				lx = 0;
				ly = i - 128;
			}
			else
			{
				lx = 63;
				ly = i - 192;
			}
			int a = g.localLabel(c, lx << 6 | ly);
			if (a == 0)
			{
				continue;
			}
			int x = bx + lx;
			int y = by + ly;
			for (int[] d : DIRS)
			{
				int tx = x + d[0];
				int ty = y + d[1];
				int tRegion = (tx >> 6) << 8 | (ty >> 6);
				if (tRegion == region || (tRegion << 2 | plane) <= key)
				{
					continue;
				}
				if (!map.canStep(x, y, plane, d[0], d[1]))
				{
					continue;
				}
				int b = g.componentAt(tx, ty, plane);
				if (b < 0)
				{
					continue;
				}
				int pos = (tx >> 6) != (x >> 6) && (ty >> 6) == (y >> 6) ? ly : lx;
				byNeighbour.computeIfAbsent(tRegion, k -> new ArrayList<>())
					.add(new int[]{pos, g.compBase[c] + a - 1, b, x, y});
			}
		}
		for (List<int[]> crossings : byNeighbour.values())
		{
			crossings.sort((p, q) -> p[1] != q[1] ? Integer.compare(p[1], q[1])
				: p[2] != q[2] ? Integer.compare(p[2], q[2]) : Integer.compare(p[0], q[0]));
			int[] run = null;
			int lastPos = 0;
			for (int[] cr : crossings)
			{
				boolean continues = run != null && run[0] == cr[1] && run[1] == cr[2] && cr[0] - lastPos <= 1;
				if (!continues)
				{
					if (run != null)
					{
						out.add(run);
					}
					run = new int[]{cr[1], cr[2], cr[3], cr[4], cr[3], cr[4], plane};
					links.add((long) cr[1] << 32 | cr[2]);
				}
				else
				{
					run[4] = cr[3];
					run[5] = cr[4];
				}
				lastPos = cr[0];
			}
			if (run != null)
			{
				out.add(run);
			}
		}
	}

	/** Global component id of a tile, or -1 if it has none. */
	public int componentAt(int x, int y, int plane)
	{
		if (x < 0 || y < 0)
		{
			return -1;
		}
		int c = Arrays.binarySearch(cellKeys, ((x >> 6) << 8 | (y >> 6)) << 2 | plane);
		if (c < 0)
		{
			return -1;
		}
		int l = localLabel(c, (x & 63) << 6 | (y & 63));
		return l == 0 ? -1 : compBase[c] + l - 1;
	}

	/**
	 * Local labels of a cell (0 = none, 1..n), or null; index localX << 6 | localY.
	 * Unpacked into a new array on every call, so callers that need it more than once cache it
	 * (single tiles: {@link #localLabel}).
	 */
	public short[] cellLabels(int regionId, int plane)
	{
		int c = Arrays.binarySearch(cellKeys, regionId << 2 | plane);
		if (c < 0)
		{
			return null;
		}
		short[] out = new short[4096];
		for (int l = 0; l < 4096; l++)
		{
			out[l] = (short) localLabel(c, l);
		}
		return out;
	}

	/** Position of a cell in this graph's cell list (for {@link #localLabel}), or a negative number. */
	public int cellIndex(int regionId, int plane)
	{
		return Arrays.binarySearch(cellKeys, regionId << 2 | plane);
	}

	/** Global id of the first component of a cell (add local label - 1), or -1. */
	public int cellComponentBase(int regionId, int plane)
	{
		int c = Arrays.binarySearch(cellKeys, regionId << 2 | plane);
		return c < 0 ? -1 : compBase[c];
	}

	public int componentCount()
	{
		return compSize.length;
	}

	/** Number of tiles in a component. */
	public int componentSize(int comp)
	{
		return compSize[comp];
	}

	/** The cell of a component, as {@code regionId << 2 | plane}. */
	public int componentCell(int comp)
	{
		return cellKeys[compCell[comp]];
	}

	public int entranceCount()
	{
		return entA.length;
	}

	/** Component on the lower-key side of an entrance. */
	public int entranceFrom(int e)
	{
		return entA[e];
	}

	/** Component on the higher-key side of an entrance. */
	public int entranceTo(int e)
	{
		return entB[e];
	}

	/** First and last border tile of the entrance, on its "from" side: {x0, y0, x1, y1, plane}. */
	public int[] entranceTiles(int e)
	{
		return new int[]{entX0[e], entY0[e], entX1[e], entY1[e], entPlane[e]};
	}

	/** Largest size (tiles) of a closed-off pocket. */
	public static final int POCKET_MAX_TILES = 16;

	/**
	 * Closed-off pockets: components with no entrance and at most
	 * {@link #POCKET_MAX_TILES} tiles (e.g. the gap behind a counter). The
	 * generated movement data leaves them out.
	 */
	public boolean[] pockets()
	{
		int[] ents = entrancesPerComponent();
		boolean[] out = new boolean[compSize.length];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = ents[i] == 0 && compSize[i] <= POCKET_MAX_TILES;
		}
		return out;
	}

	/** Number of entrances each component has (either side). */
	public int[] entrancesPerComponent()
	{
		int[] n = new int[compSize.length];
		for (int e = 0; e < entA.length; e++)
		{
			n[entA[e]]++;
			n[entB[e]]++;
		}
		return n;
	}
}
