package com.bestpath.core.sea;

import com.bestpath.core.collision.MovementRules;

/**
 * REFERENCE COPY (tests only): the SeaGrid as it was before the compact forms, kept so the tests can
 * check the compact form gives exactly the same answers on hand-made seas. Not used by the tool or the plugin.
 *
 * <p>The main ocean as a search grid for boats: every tile of the main sea
 * piece gets a number, with its sea area, the steps a boat can take from it
 * and its clearance (how far the nearest obstacle is). Seas cut off from the
 * main ocean are left out (players cannot enter them). Immutable.
 *
 * <p>Steps: 8 directions. A step must stay on the main ocean and be allowed
 * by the movement data (walls); a diagonal step also needs both orthogonal
 * tiles on the main ocean (no cutting a corner of land).
 *
 * <p>Clearance: 1 when a tile next to it (8 around) is not main ocean (land,
 * rocks, a border line, other seas), 2 when the nearest such tile is two
 * tiles away, and so on, up to {@link #MAX_CLEARANCE}.
 */
public final class ReferenceSeaGrid
{
	/** Directions: west, east, south, north, south-west, south-east, north-west, north-east. */
	public static final int[] DX = {-1, 1, 0, 0, -1, 1, -1, 1};
	public static final int[] DY = {0, 0, -1, 1, -1, -1, 1, 1};
	/** Clearance is counted up to this (enough for the widest boat's preferred clearance). */
	public static final int MAX_CLEARANCE = 5;

	/** Per region id (x >> 6 << 8 | y >> 6): tile number per local tile (localX << 6 | localY), -1 = not main ocean; null = none. */
	private final int[][] byRegion;
	private final int[] pos;
	private final byte[] area;
	private final byte[] moves;
	private final byte[] clearance;

	private ReferenceSeaGrid(int[][] byRegion, int[] pos, byte[] area, byte[] moves, byte[] clearance)
	{
		this.byRegion = byRegion;
		this.pos = pos;
		this.area = area;
		this.moves = moves;
		this.clearance = clearance;
	}

	/**
	 * @param plane0 plane 0 of the movement data with the sea still open
	 *               (before it is closed to walking)
	 */
	public static ReferenceSeaGrid build(SeaMap sea, SeaPieces pieces, MovementRules.Flags plane0)
	{
		int main = pieces.mainPiece();
		int[][] byRegion = new int[1 << 16][];
		int n = main < 0 ? 0 : pieces.size(main);
		int[] pos = new int[n];
		byte[] area = new byte[n];
		int next = 0;
		if (main >= 0)
		{
			for (int key : sea.regionIds())
			{
				int bx = (key >> 8) << 6;
				int by = (key & 0xFF) << 6;
				int[] local = null;
				for (int l = 0; l < 4096; l++)
				{
					int x = bx + (l >> 6);
					int y = by + (l & 63);
					if (pieces.pieceAt(x, y, 0) != main)
					{
						continue;
					}
					if (local == null)
					{
						local = new int[4096];
						java.util.Arrays.fill(local, -1);
						byRegion[key] = local;
					}
					local[l] = next;
					pos[next] = x << 14 | y;
					area[next] = (byte) SeaMap.areaOf(sea.texture(x, y, 0)).ordinal();
					next++;
				}
			}
		}
		ReferenceSeaGrid g = new ReferenceSeaGrid(byRegion, pos, area, new byte[n], new byte[n]);
		// steps
		for (int i = 0; i < n; i++)
		{
			int x = g.x(i);
			int y = g.y(i);
			int m = 0;
			for (int d = 0; d < 8; d++)
			{
				int dx = DX[d];
				int dy = DY[d];
				if (g.index(x + dx, y + dy) < 0 || (d >= 4 && (g.index(x + dx, y) < 0 || g.index(x, y + dy) < 0)))
				{
					continue;
				}
				if (MovementRules.canStep(plane0, x, y, dx, dy))
				{
					m |= 1 << d;
				}
			}
			g.moves[i] = (byte) m;
		}
		// clearance: peel layer by layer (Chebyshev distance to the nearest tile that is not main ocean)
		java.util.Arrays.fill(g.clearance, (byte) 1);
		for (int k = 1; k < MAX_CLEARANCE; k++)
		{
			for (int i = 0; i < n; i++)
			{
				if (g.clearance[i] != k)
				{
					continue;
				}
				int x = g.x(i);
				int y = g.y(i);
				boolean all = true;
				for (int d = 0; d < 8 && all; d++)
				{
					int o = g.index(x + DX[d], y + DY[d]);
					all = o >= 0 && g.clearance[o] >= k;
				}
				if (all)
				{
					g.clearance[i] = (byte) (k + 1);
				}
			}
		}
		return g;
	}

	/** Number of main-ocean tiles. */
	public int size()
	{
		return pos.length;
	}

	/** The tile's number, or -1 when it is not main ocean (plane 0 only). */
	public int index(int x, int y)
	{
		if (x < 0 || y < 0 || x >= 1 << 14 || y >= 1 << 14)
		{
			return -1;
		}
		int[] local = byRegion[(x >> 6) << 8 | (y >> 6)];
		return local == null ? -1 : local[(x & 63) << 6 | (y & 63)];
	}

	public int x(int i)
	{
		return pos[i] >>> 14;
	}

	public int y(int i)
	{
		return pos[i] & 0x3FFF;
	}

	public SeaArea area(int i)
	{
		return SeaArea.values()[area[i]];
	}

	public int areaOrdinal(int i)
	{
		return area[i];
	}

	/** 1 = an obstacle is next to it, up to {@link #MAX_CLEARANCE}. */
	public int clearance(int i)
	{
		return clearance[i];
	}

	/** Whether a boat can step from tile i in direction d (see {@link #DX}). */
	public boolean canMove(int i, int d)
	{
		return (moves[i] & (1 << d)) != 0;
	}

	/** The tile one step from i in direction d (call {@link #canMove} first). */
	public int neighbour(int i, int d)
	{
		return index(x(i) + DX[d], y(i) + DY[d]);
	}

	/** Whether the other grid has exactly the same tiles, areas, steps and clearance (so search results carry over). */
	public boolean sameAs(ReferenceSeaGrid o)
	{
		return o != null && java.util.Arrays.equals(pos, o.pos) && java.util.Arrays.equals(area, o.area)
			&& java.util.Arrays.equals(moves, o.moves) && java.util.Arrays.equals(clearance, o.clearance);
	}

	/** Bytes held (rough). */
	public long bytes()
	{
		long b = (long) pos.length * (4 + 3);
		for (int[] r : byRegion)
		{
			b += r == null ? 0 : 4L * r.length;
		}
		return b;
	}
}
