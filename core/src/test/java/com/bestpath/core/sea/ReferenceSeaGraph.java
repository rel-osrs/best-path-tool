package com.bestpath.core.sea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * REFERENCE COPY (tests only): the SeaGraph as it was before the compact forms, kept so the tests can
 * check the compact form gives exactly the same answers on hand-made seas. Not used by the tool or the plugin.
 *
 * <p>The sea graph every boat uses (one general structure, no work
 * per boat): waypoints on the main ocean every {@link #SPACING} tiles
 * and straight links between them in the boat's 16 headings. Each link
 * records what decides whether a boat may use it: the smallest clearance
 * along it, the sea areas it crosses, and whether it touches a harbour (water
 * near a buoy, where tight water is allowed). A boat's rules switch links on
 * or off during a search ({@link ReferenceSeaRouter}). Immutable.
 *
 * <p>Headings are numbered anticlockwise from east: east, east-north-east
 * (2:1), north-east, north-north-east (1:2), north, ... Link d goes
 * {@link #OFF_X}[d] * SPACING / 2 tiles east and {@link #OFF_Y}[d] * SPACING / 2
 * north: straight headings and diagonals one waypoint on, the in-between
 * headings a knight's move (two waypoints along, one across). The boat turns
 * 22.5 degrees per step (Turning Circles); 2:1 is 26.6 degrees, close enough.
 *
 * <p>Points that are not waypoints (docks, a start or a destination at sea)
 * join the graph by straight lines to waypoints within {@link #JOIN_RANGE}
 * tiles ({@link #join}).
 */
public final class ReferenceSeaGraph
{
	public static final int SPACING = SeaGraph.SPACING;
	public static final int HEADINGS = 16;
	/** Link offsets per heading, in half-spacings. */
	public static final int[] OFF_X = {2, 4, 2, 2, 0, -2, -2, -4, -2, -4, -2, -2, 0, 2, 2, 4};
	public static final int[] OFF_Y = {0, 2, 2, 4, 2, 4, 2, 2, 0, -2, -2, -4, -2, -4, -2, -2};
	/** Points join waypoints this far away (tiles, each axis). */
	public static final int JOIN_RANGE = 2 * SPACING + 2;
	/** Water this close to a buoy is harbour (cache #2735: 4 is enough for every dock to be reached by every boat). */
	public static final int HARBOUR_RADIUS = 5;

	/** Link flags: clearance (bits 0-3), harbour (bit 4), usable at all (bit 5). */
	private static final int CLEAR_MASK = 0x0F;
	private static final int HARBOUR = 0x10;
	private static final int EXISTS = 0x20;

	private final ReferenceSeaGrid grid;
	/** Waypoint -> sea grid tile number. */
	private final int[] wpTile;
	/** Sea grid tile number -> waypoint, or -1. */
	private final int[] wpOf;
	/** Per waypoint and heading (wp * 16 + d): flags, and the areas crossed (bit per SeaArea ordinal). */
	private final byte[] linkFlags;
	private final byte[] linkAreas;
	private final boolean[] harbour;

	private ReferenceSeaGraph(ReferenceSeaGrid grid, int[] wpTile, int[] wpOf, byte[] linkFlags, byte[] linkAreas, boolean[] harbour)
	{
		this.grid = grid;
		this.wpTile = wpTile;
		this.wpOf = wpOf;
		this.linkFlags = linkFlags;
		this.linkAreas = linkAreas;
		this.harbour = harbour;
	}

	/** Builds the graph (about 0.2 s for the whole ocean). */
	public static ReferenceSeaGraph build(ReferenceSeaGrid grid, List<Dock> docks)
	{
		boolean[] harbour = new boolean[grid.size()];
		for (Dock d : docks)
		{
			for (int x = d.getBuoyX() - HARBOUR_RADIUS; x < d.getBuoyX() + d.getBuoySize() + HARBOUR_RADIUS; x++)
			{
				for (int y = d.getBuoyY() - HARBOUR_RADIUS; y < d.getBuoyY() + d.getBuoySize() + HARBOUR_RADIUS; y++)
				{
					int i = grid.index(x, y);
					if (i >= 0)
					{
						harbour[i] = true;
					}
				}
			}
		}
		int[] wpOf = new int[grid.size()];
		Arrays.fill(wpOf, -1);
		int n = 0;
		for (int i = 0; i < grid.size(); i++)
		{
			if (grid.x(i) % SPACING == 0 && grid.y(i) % SPACING == 0)
			{
				wpOf[i] = n++;
			}
		}
		int[] wpTile = new int[n];
		for (int i = 0; i < grid.size(); i++)
		{
			if (wpOf[i] >= 0)
			{
				wpTile[wpOf[i]] = i;
			}
		}
		byte[] flags = new byte[n * HEADINGS];
		byte[] areas = new byte[n * HEADINGS];
		ReferenceSeaGraph g = new ReferenceSeaGraph(grid, wpTile, wpOf, flags, areas, harbour);
		int[] line = new int[3];
		for (int w = 0; w < n; w++)
		{
			int x = grid.x(wpTile[w]);
			int y = grid.y(wpTile[w]);
			for (int d = 0; d < HEADINGS; d++)
			{
				int tx = x + OFF_X[d] * SPACING / 2;
				int ty = y + OFF_Y[d] * SPACING / 2;
				int ti = grid.index(tx, ty);
				if (ti < 0 || wpOf[ti] < 0 || !g.line(x, y, tx, ty, line))
				{
					continue;
				}
				flags[w * HEADINGS + d] = (byte) (EXISTS | Math.min(CLEAR_MASK, line[0]) | (line[2] != 0 ? HARBOUR : 0));
				areas[w * HEADINGS + d] = (byte) line[1];
			}
		}
		return g;
	}

	/**
	 * Walks a straight line tile by tile (8-connected, as the boat's centre
	 * passes): {smallest clearance, areas crossed, 1 if it touches harbour}
	 * into {@code out}; false if a step is not allowed (land, a wall, a corner).
	 */
	boolean line(int x0, int y0, int x1, int y1, int[] out)
	{
		int i = grid.index(x0, y0);
		if (i < 0)
		{
			return false;
		}
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx = Integer.signum(x1 - x0);
		int sy = Integer.signum(y1 - y0);
		int err = dx - dy;
		int x = x0;
		int y = y0;
		int clear = grid.clearance(i);
		int area = 1 << grid.areaOrdinal(i);
		boolean port = harbour[i];
		int clearOut = port ? SeaGrid.MAX_CLEARANCE : clear;
		while (x != x1 || y != y1)
		{
			int e2 = 2 * err;
			int mx = 0;
			int my = 0;
			if (e2 > -dy)
			{
				err -= dy;
				mx = sx;
			}
			if (e2 < dx)
			{
				err += dx;
				my = sy;
			}
			if (!grid.canMove(i, direction(mx, my)))
			{
				return false;
			}
			x += mx;
			y += my;
			i = grid.index(x, y);
			clear = Math.min(clear, grid.clearance(i));
			area |= 1 << grid.areaOrdinal(i);
			port |= harbour[i];
			clearOut = harbour[i] ? clearOut : Math.min(clearOut, grid.clearance(i));
		}
		out[0] = clear;
		out[1] = area;
		out[2] = port && clearOut >= SeaGraph.HARBOUR_EXEMPT ? 1 : 0;
		return true;
	}

	/** The tiles of a straight line (packed x << 14 | y), first and last included; the line must be allowed. */
	public int[] lineTiles(int x0, int y0, int x1, int y1)
	{
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx = Integer.signum(x1 - x0);
		int sy = Integer.signum(y1 - y0);
		int err = dx - dy;
		int[] out = new int[Math.max(dx, dy) + 1];
		int k = 0;
		int x = x0;
		int y = y0;
		out[k++] = x << 14 | y;
		while (x != x1 || y != y1)
		{
			int e2 = 2 * err;
			if (e2 > -dy)
			{
				err -= dy;
				x += sx;
			}
			if (e2 < dx)
			{
				err += dx;
				y += sy;
			}
			out[k++] = x << 14 | y;
		}
		return Arrays.copyOf(out, k);
	}

	private static int direction(int mx, int my)
	{
		for (int d = 0; d < 8; d++)
		{
			if (ReferenceSeaGrid.DX[d] == mx && ReferenceSeaGrid.DY[d] == my)
			{
				return d;
			}
		}
		return -1;
	}

	public ReferenceSeaGrid getGrid()
	{
		return grid;
	}

	public int waypointCount()
	{
		return wpTile.length;
	}

	public int x(int wp)
	{
		return grid.x(wpTile[wp]);
	}

	public int y(int wp)
	{
		return grid.y(wpTile[wp]);
	}

	/** The waypoint on this tile, or -1. */
	public int waypointAt(int x, int y)
	{
		int i = grid.index(x, y);
		return i < 0 ? -1 : wpOf[i];
	}

	/** Whether waypoint {@code wp} has a link in heading {@code d}. */
	public boolean hasLink(int wp, int d)
	{
		return (linkFlags[wp * HEADINGS + d] & EXISTS) != 0;
	}

	/** The waypoint a link leads to. */
	public int linkTarget(int wp, int d)
	{
		return waypointAt(x(wp) + OFF_X[d] * SPACING / 2, y(wp) + OFF_Y[d] * SPACING / 2);
	}

	public int linkClearance(int wp, int d)
	{
		return linkFlags[wp * HEADINGS + d] & CLEAR_MASK;
	}

	public boolean linkHarbour(int wp, int d)
	{
		return (linkFlags[wp * HEADINGS + d] & HARBOUR) != 0;
	}

	/** Bit per SeaArea ordinal. */
	public int linkAreas(int wp, int d)
	{
		return linkAreas[wp * HEADINGS + d] & 0xFF;
	}

	/** Length of a link in heading d, in tiles. */
	public static double linkLength(int d)
	{
		return Math.hypot(OFF_X[d], OFF_Y[d]) * SPACING / 2;
	}

	/** A straight line from a point to a waypoint. */
	public static final class Join
	{
		public final int waypoint;
		/** The tile the line starts from. */
		public final int fromX;
		public final int fromY;
		public final int cornerX;
		public final int cornerY;
		/** Tiles. */
		public final double length;
		public final int clearance;
		public final int areas;
		public final boolean harbour;

		Join(int waypoint, int fromX, int fromY, int cornerX, int cornerY, double length, int clearance, int areas, boolean harbour)
		{
			this.waypoint = waypoint;
			this.fromX = fromX;
			this.fromY = fromY;
			this.cornerX = cornerX;
			this.cornerY = cornerY;
			this.length = length;
			this.clearance = clearance;
			this.areas = areas;
			this.harbour = harbour;
		}
	}

	/**
	 * Waypoints a point at sea reaches by a straight line (within
	 * {@link #JOIN_RANGE} tiles), from any of the given start tiles (e.g. the
	 * water around a buoy); for each waypoint the shortest such line.
	 */
	public List<Join> join(int[][] fromTiles)
	{
		List<Join> out = new ArrayList<>();
		int[] line = new int[3];
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int[] t : fromTiles)
		{
			minX = Math.min(minX, t[0]);
			minY = Math.min(minY, t[1]);
			maxX = Math.max(maxX, t[0]);
			maxY = Math.max(maxY, t[1]);
		}
		int x0 = Math.floorDiv(minX - JOIN_RANGE, SPACING) * SPACING;
		int y0 = Math.floorDiv(minY - JOIN_RANGE, SPACING) * SPACING;
		for (int x = x0; x <= maxX + JOIN_RANGE; x += SPACING)
		{
			for (int y = y0; y <= maxY + JOIN_RANGE; y += SPACING)
			{
				int wp = waypointAt(x, y);
				if (wp < 0)
				{
					continue;
				}
				Join best = null;
				for (int[] t : fromTiles)
				{
					if (Math.abs(t[0] - x) > JOIN_RANGE || Math.abs(t[1] - y) > JOIN_RANGE)
					{
						continue;
					}
					// at most two legs in the boat's headings: every corner that splits the way into two
					// neighbouring headings, the one with the widest clearance (a tie: the first heading's leg first)
					int[] sp = SeaGraph.split(x - t[0], y - t[1]);
					int[][] corners = sp[0] == 0 || sp[2] == 0 ? new int[][]{{x, y}}
						: new int[][]{{t[0] + sp[0] * SeaGraph.UX[sp[1]], t[1] + sp[0] * SeaGraph.UY[sp[1]]},
							{t[0] + sp[2] * SeaGraph.UX[sp[3]], t[1] + sp[2] * SeaGraph.UY[sp[3]]}};
					double len = sp[0] * Math.hypot(SeaGraph.UX[sp[1]], SeaGraph.UY[sp[1]]) + sp[2] * Math.hypot(SeaGraph.UX[sp[3]], SeaGraph.UY[sp[3]]);
					int[] second = new int[3];
					int bestClear = -1;
					int[] pick = null;
					for (int[] c : corners)
					{
						if (line(t[0], t[1], c[0], c[1], line) && line(c[0], c[1], x, y, second) && Math.min(line[0], second[0]) > bestClear)
						{
							bestClear = Math.min(line[0], second[0]);
							pick = new int[]{c[0], c[1], bestClear, line[1] | second[1], SeaGraph.harbourOf(line, second)};
						}
					}
					if (pick != null && (best == null || len < best.length))
					{
						best = new Join(wp, t[0], t[1], pick[0], pick[1], len, pick[2], pick[3], pick[4] != 0);
					}
				}
				if (best != null)
				{
					out.add(best);
				}
			}
		}
		return out;
	}

	/** Bytes held (rough). */
	public long bytes()
	{
		return 4L * wpTile.length + 4L * wpOf.length + 2L * linkFlags.length + harbour.length;
	}
}
