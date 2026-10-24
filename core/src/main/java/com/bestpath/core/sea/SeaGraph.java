package com.bestpath.core.sea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sea graph every boat uses (one shared structure, no work per boat):
 * waypoints on the main ocean every {@link #SPACING} tiles
 * and straight links between them in the boat's 16 headings. Each link
 * records what decides whether a boat may use it: the smallest clearance
 * along it, the sea areas it crosses, and whether it touches a harbour (water
 * near a buoy, where tight water is allowed). A boat's rules switch links on
 * or off during a search ({@link SeaRouter}). Immutable.
 *
 * <p>Headings are numbered anticlockwise from east: east, east-north-east
 * (2:1), north-east, north-north-east (1:2), north, ... Link d goes
 * {@link #OFF_X}[d] * SPACING / 2 tiles east and {@link #OFF_Y}[d] * SPACING / 2
 * north: straight headings and diagonals one waypoint on, the in-between
 * headings a knight's move (two waypoints along, one across). The links are
 * only the search's coarse grid: 2:1 is 26.6 degrees, not the boat's 22.5.
 *
 * <p>Exact headings: a boat's 16 headings are exact compass points 22.5
 * degrees apart ({@link #EX}, {@link #EY}); the course drawn and followed is
 * made of legs in those exact headings (real corners, {@link #exactTwoLegs}),
 * each checked over every tile the exact line passes ({@link #exactLine}, a
 * supercover).
 *
 * <p>Points that are not waypoints (docks, a start or a destination at sea)
 * join the graph by at most two straight legs in the boat's headings to
 * waypoints within {@link #JOIN_RANGE} tiles ({@link #join}), so a whole course
 * is straight legs in the 16 headings.
 *
 * <p>Spacing: 8 tiles keeps the search small (a quarter of the waypoints of a
 * 4-tile spacing, with every dock still reaching the same docks for every
 * boat); legs come out about 1 % longer. Because links are long, a link's
 * harbour mark means its tight water is all harbour water
 * ({@link #HARBOUR_EXEMPT}).
 *
 * <p>Compact form: waypoints are numbered region by region, column by column,
 * and found by a bit set per region; a link is one byte (clearance, harbour,
 * and the areas it crosses as "open water and at most one other area", the
 * rare rest in a small table); harbour water is a bit set only in the regions
 * that have some.
 */
public final class SeaGraph
{
	public static final int SPACING = 8;
	/** log2 of {@link #SPACING}. */
	private static final int SPACING_SHIFT = Integer.numberOfTrailingZeros(SPACING);
	/** Waypoint places per region side (64 / SPACING), and its log2. */
	private static final int PLACES = 64 / SPACING;
	private static final int PLACES_SHIFT = Integer.numberOfTrailingZeros(PLACES);
	/** Longs per region's waypoint bit set. */
	private static final int WORDS = (PLACES * PLACES + 63) / 64;
	public static final int HEADINGS = 16;
	/** Link offsets per heading, in half-spacings. */
	public static final int[] OFF_X = {2, 4, 2, 2, 0, -2, -2, -4, -2, -4, -2, -2, 0, 2, 2, 4};
	public static final int[] OFF_Y = {0, 2, 2, 4, 2, 4, 2, 2, 0, -2, -2, -4, -2, -4, -2, -2};
	/** Points join waypoints this far away (tiles, each axis). */
	public static final int JOIN_RANGE = 2 * SPACING + 2;
	/**
	 * A link's harbour mark (the boat may pass below its hard clearance) means: it touches harbour water and all its
	 * water narrower than this is harbour water. Every boat's hard clearance is at most this (raft 1, skiff and sloop
	 * 2), so the mark lets a boat through tight water only inside harbours.
	 */
	static final int HARBOUR_EXEMPT = 2;
	/** Water this close to a buoy is harbour (tiles; 4 is already enough for every boat to reach every dock). */
	public static final int HARBOUR_RADIUS = 5;

	/** Link byte: clearance (bits 0-2, 0 = no link), harbour (bit 3), open water crossed (bit 4), the other area (bits 5-7). */
	private static final int CLEAR_MASK = 0x07;
	private static final int HARBOUR = 0x08;
	private static final int OPEN = 0x10;
	private static final int OTHER_SHIFT = 5;
	/** "Other area" value: more than one area besides open water; the full set is in {@link #manyAreas}. */
	private static final int MANY = 7;

	private final SeaGrid grid;
	/**
	 * Grid slot (the grid's regions, {@code SeaGrid.slot}) -> which of its PLACES x PLACES waypoint places are waypoints
	 * (place = (localX / SPACING) * PLACES + localY / SPACING).
	 */
	private final long[][] wpBits;
	/** Slot -> waypoints in the slots before it. */
	private final int[] wpBase;
	/** Waypoint -> packed x << 14 | y. */
	private final int[] wpPos;
	/** Per waypoint and heading (wp * 16 + d): the link byte. */
	private final byte[] links;
	/** wp * 16 + d -> areas (bit per SeaArea ordinal) for links with {@link #MANY}. */
	private final Map<Integer, Integer> manyAreas;
	/** Slot -> harbour tiles (64 columns as the grid's masks), or null when the region has none. */
	private final long[][] harbour;

	/**
	 * Channels ({@link SeaChannels}): per channel its points (packed x << 14 | y, the first and last
	 * waypoints' tiles), its two waypoints, clearance, areas and length; per waypoint (a bit set, then a map) the
	 * channels that end there. Empty when made without channels.
	 */
	private final int[][] chPoints;
	private final int[] chFrom;
	private final int[] chTo;
	private final int[] chClear;
	private final int[] chAreas;
	private final double[] chLength;
	private final long[] chAt;
	private final Map<Integer, int[]> chOf;

	private SeaGraph(SeaGrid grid, long[][] wpBits, int[] wpBase, int[] wpPos, byte[] links,
		Map<Integer, Integer> manyAreas, long[][] harbour)
	{
		this.grid = grid;
		this.wpBits = wpBits;
		this.wpBase = wpBase;
		this.wpPos = wpPos;
		this.links = links;
		this.manyAreas = manyAreas;
		this.harbour = harbour;
		chPoints = new int[0][];
		chFrom = new int[0];
		chTo = new int[0];
		chClear = new int[0];
		chAreas = new int[0];
		chLength = new double[0];
		chAt = new long[0];
		chOf = Map.of();
	}

	private SeaGraph(SeaGraph g, List<int[]> points, List<int[]> stats, List<Double> lengths)
	{
		this.grid = g.grid;
		this.wpBits = g.wpBits;
		this.wpBase = g.wpBase;
		this.wpPos = g.wpPos;
		this.links = g.links;
		this.manyAreas = g.manyAreas;
		this.harbour = g.harbour;
		int n = points.size();
		chPoints = points.toArray(new int[0][]);
		chFrom = new int[n];
		chTo = new int[n];
		chClear = new int[n];
		chAreas = new int[n];
		chLength = new double[n];
		chAt = new long[(wpPos.length + 63) / 64];
		Map<Integer, List<Integer>> of = new HashMap<>();
		for (int c = 0; c < n; c++)
		{
			int[] s = stats.get(c);
			chFrom[c] = s[0];
			chTo[c] = s[1];
			chClear[c] = s[2];
			chAreas[c] = s[3];
			chLength[c] = lengths.get(c);
			for (int w : new int[]{s[0], s[1]})
			{
				chAt[w >> 6] |= 1L << (w & 63);
				of.computeIfAbsent(w, k -> new ArrayList<>()).add(c);
			}
		}
		Map<Integer, int[]> m = new HashMap<>();
		of.forEach((w, cs) -> m.put(w, cs.stream().mapToInt(Integer::intValue).toArray()));
		chOf = m;
	}

	/**
	 * This graph with channels ({@link SeaChannels#find}: per channel its points, packed x << 14 | y). Each is checked
	 * again: its ends must be waypoints and each stretch a straight line a boat may sail both ways; one that isn't
	 * (from a file made for another sea) is left out. Its clearance and areas are those of its stretches, its length
	 * their straight lengths added up.
	 */
	public SeaGraph withChannels(List<int[]> channels)
	{
		List<int[]> points = new ArrayList<>();
		List<int[]> stats = new ArrayList<>();
		List<Double> lengths = new ArrayList<>();
		int[] line = new int[3];
		int[] back = new int[3];
		for (int[] p : channels)
		{
			if (p.length < 2)
			{
				continue;
			}
			int a = waypointAt(p[0] >>> 14, p[0] & 0x3FFF);
			int b = waypointAt(p[p.length - 1] >>> 14, p[p.length - 1] & 0x3FFF);
			if (a < 0 || b < 0 || a == b)
			{
				continue;
			}
			int clear = SeaGrid.MAX_CLEARANCE;
			int areas = 0;
			double length = 0;
			boolean ok = true;
			for (int i = 0; ok && i + 1 < p.length; i++)
			{
				int x0 = p[i] >>> 14;
				int y0 = p[i] & 0x3FFF;
				int x1 = p[i + 1] >>> 14;
				int y1 = p[i + 1] & 0x3FFF;
				// a line drawn from the other end can pass other tiles: keep the smaller clearance and the areas of both
				ok = line(x1, y1, x0, y0, back) && line(x0, y0, x1, y1, line);
				clear = Math.min(clear, Math.min(line[0], back[0]));
				areas |= line[1] | back[1];
				length += Math.hypot(x1 - x0, y1 - y0);
			}
			if (ok)
			{
				points.add(p.clone());
				stats.add(new int[]{a, b, clear, areas});
				lengths.add(length);
			}
		}
		return new SeaGraph(this, points, stats, lengths);
	}

	/** Number of channels. */
	public int channelCount()
	{
		return chPoints.length;
	}

	/** The channels that end at the waypoint (empty when none). */
	public int[] channelsAt(int wp)
	{
		if (chAt.length == 0 || (chAt[wp >> 6] & 1L << (wp & 63)) == 0)
		{
			return NO_CHANNELS;
		}
		return chOf.get(wp);
	}

	private static final int[] NO_CHANNELS = new int[0];

	/** The channel's other end from waypoint {@code wp} (one of its ends). */
	public int channelOther(int c, int wp)
	{
		return chFrom[c] == wp ? chTo[c] : chFrom[c];
	}

	/** The waypoint the channel's points start at. */
	public int channelFrom(int c)
	{
		return chFrom[c];
	}

	public int channelTo(int c)
	{
		return chTo[c];
	}

	/** The narrowest water along it (1 = an obstacle next to the boat's centre). */
	public int channelClearance(int c)
	{
		return chClear[c];
	}

	/** Bit per SeaArea ordinal. */
	public int channelAreas(int c)
	{
		return chAreas[c];
	}

	/** Tiles (its stretches' straight lengths). */
	public double channelLength(int c)
	{
		return chLength[c];
	}

	/** Its points (packed x << 14 | y), from {@link #channelFrom} to {@link #channelTo}; a copy. */
	public int[] channelPoints(int c)
	{
		return chPoints[c].clone();
	}

	/** The channels' points as stored (for the sea file). */
	public List<int[]> channels()
	{
		List<int[]> out = new ArrayList<>(chPoints.length);
		for (int[] p : chPoints)
		{
			out.add(p.clone());
		}
		return out;
	}

	/** Builds the graph (about 0.1-0.2 s for the whole ocean). */
	public static SeaGraph build(SeaGrid grid, List<Dock> docks)
	{
		grid.keepClearanceTable();
		int[] keys = grid.regionIds();
		long[][] harbour = new long[keys.length][];
		for (Dock d : docks)
		{
			for (int x = d.getBuoyX() - HARBOUR_RADIUS; x < d.getBuoyX() + d.getBuoySize() + HARBOUR_RADIUS; x++)
			{
				for (int y = d.getBuoyY() - HARBOUR_RADIUS; y < d.getBuoyY() + d.getBuoySize() + HARBOUR_RADIUS; y++)
				{
					if (grid.isOcean(x, y))
					{
						int s = grid.slot(x, y);
						if (harbour[s] == null)
						{
							harbour[s] = new long[64];
						}
						harbour[s][x & 63] |= 1L << (y & 63);
					}
				}
			}
		}
		// waypoints: main-ocean tiles whose x and y are multiples of SPACING, numbered region by region
		long[][] wpBits = new long[keys.length][];
		int[] wpBase = new int[keys.length];
		int n = 0;
		for (int s = 0; s < keys.length; s++)
		{
			long[] m = grid.regionMask(s);
			long[] w = new long[WORDS];
			for (int px = 0; px < PLACES; px++)
			{
				long col = m[px * SPACING];
				for (int py = 0; py < PLACES; py++)
				{
					if ((col & 1L << (py * SPACING)) != 0)
					{
						int place = px << PLACES_SHIFT | py;
						w[place >> 6] |= 1L << (place & 63);
					}
				}
			}
			wpBits[s] = w;
			wpBase[s] = n;
			for (long word : w)
			{
				n += Long.bitCount(word);
			}
		}
		int[] wpPos = new int[n];
		int k = 0;
		for (int s = 0; s < keys.length; s++)
		{
			int bx = (keys[s] >> 8) << 6;
			int by = (keys[s] & 0xFF) << 6;
			for (int word = 0; word < WORDS; word++)
			{
				long w = wpBits[s][word];
				while (w != 0)
				{
					int place = word << 6 | Long.numberOfTrailingZeros(w);
					w &= w - 1;
					wpPos[k++] = (bx + (place >> PLACES_SHIFT) * SPACING) << 14 | (by + (place & (PLACES - 1)) * SPACING);
				}
			}
		}
		byte[] links = new byte[n * HEADINGS];
		Map<Integer, Integer> many = new HashMap<>();
		SeaGraph g = new SeaGraph(grid, wpBits, wpBase, wpPos, links, many, harbour);
		int[] line = new int[3];
		for (int w = 0; w < n; w++)
		{
			int x = wpPos[w] >>> 14;
			int y = wpPos[w] & 0x3FFF;
			for (int d = 0; d < HEADINGS; d++)
			{
				int tx = x + OFF_X[d] * SPACING / 2;
				int ty = y + OFF_Y[d] * SPACING / 2;
				if (!grid.isOcean(tx, ty) || !g.line(x, y, tx, ty, line))
				{
					continue;
				}
				links[w * HEADINGS + d] = encode(w * HEADINGS + d, line[0], line[1], line[2] != 0, many);
			}
		}
		return g;
	}

	private static byte encode(int at, int clearance, int areas, boolean harbour, Map<Integer, Integer> many)
	{
		if (clearance < 1 || clearance > CLEAR_MASK)
		{
			throw new IllegalStateException("clearance out of range: " + clearance);
		}
		int others = areas & ~1; // every area but open water (ordinal 0)
		int other;
		if (others == 0)
		{
			other = 0;
		}
		else if (Integer.bitCount(others) == 1 && Integer.numberOfTrailingZeros(others) < MANY)
		{
			other = Integer.numberOfTrailingZeros(others);
		}
		else
		{
			other = MANY;
			many.put(at, areas);
		}
		return (byte) (clearance | (harbour ? HARBOUR : 0) | ((areas & 1) != 0 ? OPEN : 0) | other << OTHER_SHIFT);
	}

	/**
	 * Walks a straight line tile by tile (8-connected, as the boat's centre
	 * passes): {smallest clearance, areas crossed, 1 if it touches harbour}
	 * into {@code out}; false if a step is not allowed (land, a wall, a corner).
	 */
	boolean line(int x0, int y0, int x1, int y1, int[] out)
	{
		int s = grid.slot(x0, y0);
		int l = (x0 & 63) << 6 | (y0 & 63);
		if (s < 0 || !grid.oceanIn(s, l))
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
		int clear = grid.clearanceIn(s, l);
		int area = 1 << grid.areaIn(s, l);
		boolean port = harbour[s] != null && (harbour[s][l >> 6] & 1L << (l & 63)) != 0;
		// the narrowest water outside harbours: a link that only touches a harbour must not take a boat through a gap
		// elsewhere along it
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
			if (!grid.canMoveIn(s, x, y, direction(mx, my)))
			{
				return false;
			}
			x += mx;
			y += my;
			// main ocean (the step was allowed)
			s = grid.slot(x, y);
			l = (x & 63) << 6 | (y & 63);
			int c = grid.clearanceIn(s, l);
			clear = Math.min(clear, c);
			area |= 1 << grid.areaIn(s, l);
			if (harbour[s] != null && (harbour[s][l >> 6] & 1L << (l & 63)) != 0)
			{
				port = true;
			}
			else
			{
				clearOut = Math.min(clearOut, c);
			}
		}
		out[0] = clear;
		out[1] = area;
		out[2] = port && clearOut >= HARBOUR_EXEMPT ? 1 : 0;
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

	/**
	 * The boat's 16 headings as exact unit vectors, numbered like the links: anticlockwise from east,
	 * 22.5 degrees apart (east, east-north-east, north-east, north-north-east, north, ...). Built from one octant's
	 * values so mirrored headings are exactly mirrored (a north-east leg has equal x and y steps).
	 */
	public static final double[] EX = new double[HEADINGS];
	public static final double[] EY = new double[HEADINGS];
	/** sin(22.5 degrees): the determinant of two neighbouring headings. */
	private static final double SIN_STEP;
	/** Crossings this close (as a fraction of a line) count as at the same time: the line passes a tile corner. */
	private static final double CORNER_EPS = 1e-9;

	static
	{
		double c = Math.cos(Math.PI / 8);
		double s = Math.sin(Math.PI / 8);
		double h = Math.sqrt(0.5);
		double[] ox = {1, c, h, s};
		double[] oy = {0, s, h, c};
		for (int q = 0; q < 4; q++)
		{
			for (int i = 0; i < 4; i++)
			{
				// quarter q turns the first quarter by q * 90 degrees anticlockwise: (x, y) -> (-y, x)
				double x = ox[i];
				double y = oy[i];
				for (int t = 0; t < q; t++)
				{
					double nx = -y;
					y = x;
					x = nx;
				}
				EX[q * 4 + i] = x;
				EY[q * 4 + i] = y;
			}
		}
		SIN_STEP = s;
	}

	/**
	 * The heading a whole-tile step points along exactly (east, north-east, north, ... : only the 8 headings a whole
	 * step can have exactly; the in-between headings are at irrational slopes), or -1.
	 */
	public static int exactHeadingOf(int dx, int dy)
	{
		if (dx == 0 && dy == 0 || dx != 0 && dy != 0 && Math.abs(dx) != Math.abs(dy))
		{
			return -1;
		}
		int sx = Integer.signum(dx);
		int sy = Integer.signum(dy);
		for (int d = 0; d < HEADINGS; d += 2)
		{
			if (Integer.signum((int) Math.round(EX[d] * 2)) == sx && Integer.signum((int) Math.round(EY[d] * 2)) == sy)
			{
				return d;
			}
		}
		return -1;
	}

	/**
	 * Walks an exact straight line between two points in tiles (a tile's middle is its whole x / y; tile (x, y)
	 * covers x - 0.5 .. x + 0.5) over EVERY tile it passes (a supercover: where it passes a tile's corner exactly,
	 * the step is diagonal and both tiles beside the corner count too): {smallest clearance, areas crossed, 1 if it
	 * touches harbour} into {@code out} as {@link #line} does; false if a step is not allowed (land, a wall, a corner
	 * a boat can't pass). The boat's clearance rules are the same as {@link #line}'s.
	 */
	public boolean exactLine(double x0, double y0, double x1, double y1, int[] out)
	{
		int x = (int) Math.floor(x0 + 0.5);
		int y = (int) Math.floor(y0 + 0.5);
		int ex = (int) Math.floor(x1 + 0.5);
		int ey = (int) Math.floor(y1 + 0.5);
		int s = grid.slot(x, y);
		int l = (x & 63) << 6 | (y & 63);
		if (s < 0 || !grid.oceanIn(s, l))
		{
			return false;
		}
		double dx = x1 - x0;
		double dy = y1 - y0;
		int sx = ex == x ? 0 : Integer.signum(ex - x);
		int sy = ey == y ? 0 : Integer.signum(ey - y);
		double tMaxX = sx == 0 ? Double.POSITIVE_INFINITY : (x + 0.5 * sx - x0) / dx;
		double tMaxY = sy == 0 ? Double.POSITIVE_INFINITY : (y + 0.5 * sy - y0) / dy;
		double tDeltaX = sx == 0 ? 0 : 1 / Math.abs(dx);
		double tDeltaY = sy == 0 ? 0 : 1 / Math.abs(dy);
		int clear = grid.clearanceIn(s, l);
		int area = 1 << grid.areaIn(s, l);
		boolean port = isHarbour(s, l);
		int clearOut = port ? SeaGrid.MAX_CLEARANCE : clear;
		int steps = Math.abs(ex - x) + Math.abs(ey - y);
		while (x != ex || y != ey)
		{
			if (steps-- <= 0)
			{
				return false; // rounding took the walk past the end tile (never seen; refuse the line)
			}
			int mx = 0;
			int my = 0;
			if (sx != 0 && sy != 0 && Math.abs(tMaxX - tMaxY) <= CORNER_EPS)
			{
				mx = sx;
				my = sy;
				tMaxX += tDeltaX;
				tMaxY += tDeltaY;
				steps--; // a diagonal step covers one x and one y
			}
			else if (sy == 0 || sx != 0 && tMaxX < tMaxY)
			{
				mx = sx;
				tMaxX += tDeltaX;
			}
			else
			{
				my = sy;
				tMaxY += tDeltaY;
			}
			if (mx != 0 && my != 0)
			{
				if ((x + mx - ex) * mx > 0 || (y + my - ey) * my > 0)
				{
					return false; // a corner beyond the end tile (rounding): refuse
				}
				// the two tiles beside the corner the line passes count too (when they are water; the step checks them)
				for (int side = 0; side < 2; side++)
				{
					int bx = side == 0 ? x + mx : x;
					int by = side == 0 ? y : y + my;
					int bs = grid.slot(bx, by);
					int bl = (bx & 63) << 6 | (by & 63);
					if (bs >= 0 && grid.oceanIn(bs, bl))
					{
						int c = grid.clearanceIn(bs, bl);
						clear = Math.min(clear, c);
						area |= 1 << grid.areaIn(bs, bl);
						if (isHarbour(bs, bl))
						{
							port = true;
						}
						else
						{
							clearOut = Math.min(clearOut, c);
						}
					}
				}
			}
			else if ((x + mx - ex) * mx > 0 || (y + my - ey) * my > 0)
			{
				return false;
			}
			if (!grid.canMoveIn(s, x, y, direction(mx, my)))
			{
				return false;
			}
			x += mx;
			y += my;
			s = grid.slot(x, y);
			l = (x & 63) << 6 | (y & 63);
			int c = grid.clearanceIn(s, l);
			clear = Math.min(clear, c);
			area |= 1 << grid.areaIn(s, l);
			if (isHarbour(s, l))
			{
				port = true;
			}
			else
			{
				clearOut = Math.min(clearOut, c);
			}
		}
		out[0] = clear;
		out[1] = area;
		out[2] = port && clearOut >= HARBOUR_EXEMPT ? 1 : 0;
		return true;
	}

	private boolean isHarbour(int s, int l)
	{
		return harbour[s] != null && (harbour[s][l >> 6] & 1L << (l & 63)) != 0;
	}

	/**
	 * The best way of at most two legs in the boat's EXACT headings (22.5 degrees apart, {@link #EX}) from tile
	 * (x0, y0) to tile (x1, y1) that the tiles allow ({@link #exactLine}): a step along east, north-east, north, ...
	 * is one leg; any other is a leg in each of the two headings either side of it, meeting at a corner that is
	 * not a whole tile. Of the two corners (which heading first) the one with the wider clearance; a tie: the one
	 * that goes on in {@code prevHeading} (the leg before, so they join into one), else the first heading first.
	 * {clearance, areas, harbour, cornerX, cornerY, first heading, second heading} into {@code out}; returns the
	 * length in tiles, or -1 when neither way is allowed.
	 */
	public double exactTwoLegs(int x0, int y0, int x1, int y1, int prevHeading, double[] out)
	{
		int dx = x1 - x0;
		int dy = y1 - y0;
		int[] a = new int[3];
		int[] b = new int[3];
		if (dx == 0 && dy == 0 || exactHeadingOf(dx, dy) >= 0)
		{
			if (!exactLine(x0, y0, x1, y1, a))
			{
				return -1;
			}
			int h = exactHeadingOf(dx, dy);
			fill(out, a[0], a[1], a[2], x1, y1, h, h);
			return Math.hypot(dx, dy);
		}
		double theta = Math.atan2(dy, dx);
		if (theta < 0)
		{
			theta += 2 * Math.PI;
		}
		int k = (int) Math.floor(theta / (Math.PI / 8)) % HEADINGS;
		int e = (k + 1) % HEADINGS;
		// (dx, dy) = p * unit(k) + q * unit(e), both positive (the step lies strictly between the two headings)
		double p = (dx * EY[e] - dy * EX[e]) / SIN_STEP;
		double q = (EX[k] * dy - EY[k] * dx) / SIN_STEP;
		if (p <= 0 || q <= 0)
		{
			return -1; // never for whole-tile steps (their slopes are never a heading's); the caller splits the way
		}
		int best = -1;
		boolean bestGoesOn = false;
		for (int order = 0; order < 2; order++)
		{
			int h1 = order == 0 ? k : e;
			int h2 = order == 0 ? e : k;
			double along = order == 0 ? p : q;
			double cx = x0 + along * EX[h1];
			double cy = y0 + along * EY[h1];
			if (!exactLine(x0, y0, cx, cy, a) || !exactLine(cx, cy, x1, y1, b))
			{
				continue;
			}
			int clear = Math.min(a[0], b[0]);
			boolean goesOn = h1 == prevHeading;
			if (clear > best || clear == best && goesOn && !bestGoesOn)
			{
				best = clear;
				bestGoesOn = goesOn;
				fill(out, clear, a[1] | b[1], harbourOf(a, b), cx, cy, h1, h2);
			}
		}
		return best < 0 ? -1 : p + q;
	}

	private static void fill(double[] out, int clear, int areas, int harbour, double cx, double cy, int h1, int h2)
	{
		out[0] = clear;
		out[1] = areas;
		out[2] = harbour;
		out[3] = cx;
		out[4] = cy;
		out[5] = h1;
		out[6] = h2;
	}

	/** Direction (see {@link SeaGrid#DX}) of a one-tile step. */
	private static int direction(int mx, int my)
	{
		// west, east, south, north, south-west, south-east, north-west, north-east
		if (my == 0)
		{
			return mx < 0 ? 0 : 1;
		}
		if (mx == 0)
		{
			return my < 0 ? 2 : 3;
		}
		return my < 0 ? (mx < 0 ? 4 : 5) : (mx < 0 ? 6 : 7);
	}

	public SeaGrid getGrid()
	{
		return grid;
	}

	public int waypointCount()
	{
		return wpPos.length;
	}

	public int x(int wp)
	{
		return wpPos[wp] >>> 14;
	}

	public int y(int wp)
	{
		return wpPos[wp] & 0x3FFF;
	}

	/** The waypoint on this tile, or -1. */
	public int waypointAt(int x, int y)
	{
		if (x < 0 || y < 0 || x >= 1 << 14 || y >= 1 << 14 || (x & (SPACING - 1)) != 0 || (y & (SPACING - 1)) != 0)
		{
			return -1;
		}
		int s = grid.slot(x, y);
		if (s < 0)
		{
			return -1;
		}
		int place = ((x & 63) >> SPACING_SHIFT) << PLACES_SHIFT | ((y & 63) >> SPACING_SHIFT);
		long[] w = wpBits[s];
		long word = w[place >> 6];
		long bit = 1L << (place & 63);
		if ((word & bit) == 0)
		{
			return -1;
		}
		int r = wpBase[s] + Long.bitCount(word & (bit - 1));
		for (int i = 0; i < place >> 6; i++)
		{
			r += Long.bitCount(w[i]);
		}
		return r;
	}

	/** Whether waypoint {@code wp} has a link in heading {@code d}. */
	public boolean hasLink(int wp, int d)
	{
		return (links[wp * HEADINGS + d] & CLEAR_MASK) != 0;
	}

	/** The link's byte (0 = no link; decode with {@link #byteClearance} and friends): for the boat search's inner loop. */
	int linkByte(int wp, int d)
	{
		return links[wp * HEADINGS + d] & 0xFF;
	}

	static int byteClearance(int b)
	{
		return b & CLEAR_MASK;
	}

	static boolean byteHarbour(int b)
	{
		return (b & HARBOUR) != 0;
	}

	/** The areas a link byte names, or -1 when they are in the side table ({@link #linkAreas} has them). */
	static int byteAreas(int b)
	{
		int other = b >>> OTHER_SHIFT;
		return other == MANY ? -1 : ((b & OPEN) != 0 ? 1 : 0) | (other == 0 ? 0 : 1 << other);
	}

	/** The waypoint a link leads to. */
	public int linkTarget(int wp, int d)
	{
		return waypointAt(x(wp) + OFF_X[d] * SPACING / 2, y(wp) + OFF_Y[d] * SPACING / 2);
	}

	public int linkClearance(int wp, int d)
	{
		return links[wp * HEADINGS + d] & CLEAR_MASK;
	}

	public boolean linkHarbour(int wp, int d)
	{
		return (links[wp * HEADINGS + d] & HARBOUR) != 0;
	}

	/** Bit per SeaArea ordinal. */
	public int linkAreas(int wp, int d)
	{
		int b = links[wp * HEADINGS + d] & 0xFF;
		if ((b & CLEAR_MASK) == 0)
		{
			return 0;
		}
		int other = b >>> OTHER_SHIFT;
		if (other == MANY)
		{
			return manyAreas.get(wp * HEADINGS + d);
		}
		return ((b & OPEN) != 0 ? 1 : 0) | (other == 0 ? 0 : 1 << other);
	}

	/** Length of a link in heading d, in tiles. */
	public static double linkLength(int d)
	{
		return Math.hypot(OFF_X[d], OFF_Y[d]) * SPACING / 2;
	}

	/**
	 * A way from a point to a waypoint in at most two straight legs, each in one of the boat's 16 headings: from the
	 * point to the corner, then on to the waypoint. A point straight along a heading has no corner (the corner is the
	 * waypoint).
	 */
	public static final class Join
	{
		public final int waypoint;
		/** The tile the way starts from. */
		public final int fromX;
		public final int fromY;
		/** Where the two legs meet (the waypoint's tile when it is one leg). */
		public final int cornerX;
		public final int cornerY;
		/** Tiles (both legs). */
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

	/** Heading unit steps (a link is {@link #SPACING} / 2 of them): east (1, 0), east-north-east (2, 1), ... */
	static final int[] UX = new int[HEADINGS];
	static final int[] UY = new int[HEADINGS];

	static
	{
		for (int d = 0; d < HEADINGS; d++)
		{
			UX[d] = OFF_X[d] / 2;
			UY[d] = OFF_Y[d] / 2;
		}
	}

	/**
	 * The step (dx, dy) as a whole number of unit steps of two neighbouring headings: {a, d, b, d + 1} with
	 * (dx, dy) = a * unit(d) + b * unit(d + 1), a, b >= 0. Neighbouring unit steps always span the plane with whole
	 * numbers (each pair's determinant is 1), so every step has one.
	 */
	static int[] split(int dx, int dy)
	{
		for (int d = 0; d < HEADINGS; d++)
		{
			int e = (d + 1) % HEADINGS;
			int det = UX[d] * UY[e] - UY[d] * UX[e];
			int a = (dx * UY[e] - dy * UX[e]) / det;
			int b = (UX[d] * dy - UY[d] * dx) / det;
			if (a >= 0 && b >= 0 && a * UX[d] + b * UX[e] == dx && a * UY[d] + b * UY[e] == dy)
			{
				return new int[]{a, d, b, e};
			}
		}
		throw new IllegalStateException("no split for " + dx + "," + dy);
	}

	/**
	 * The best way of at most two 16-heading legs from (x0, y0) to (x1, y1) that the tiles allow: {clearance, areas,
	 * harbour, cornerX, cornerY} into {@code out} (of the two corners the one with the wider clearance; a tie: the
	 * first heading first), with its length returned; -1 when neither is allowed.
	 */
	double twoLegs(int x0, int y0, int x1, int y1, int[] out)
	{
		int dx = x1 - x0;
		int dy = y1 - y0;
		int[] line = new int[3];
		if (dx == 0 && dy == 0)
		{
			if (!line(x0, y0, x1, y1, line))
			{
				return -1;
			}
			out[0] = line[0];
			out[1] = line[1];
			out[2] = line[2];
			out[3] = x1;
			out[4] = y1;
			return 0;
		}
		int[] s = split(dx, dy);
		double len = s[0] * Math.hypot(UX[s[1]], UY[s[1]]) + s[2] * Math.hypot(UX[s[3]], UY[s[3]]);
		int bestClear = -1;
		int[] other = new int[3];
		for (int order = 0; order < 2; order++)
		{
			if (order == 1 && (s[0] == 0 || s[2] == 0))
			{
				break; // one leg: the same corner either way
			}
			int ka = order == 0 ? s[0] : s[2];
			int da = order == 0 ? s[1] : s[3];
			int cx = x0 + ka * UX[da];
			int cy = y0 + ka * UY[da];
			if (!line(x0, y0, cx, cy, line) || !line(cx, cy, x1, y1, other))
			{
				continue;
			}
			int clear = Math.min(line[0], other[0]);
			if (clear > bestClear)
			{
				bestClear = clear;
				out[0] = clear;
				out[1] = line[1] | other[1];
				out[2] = harbourOf(line, other);
				out[3] = cx;
				out[4] = cy;
			}
		}
		return bestClear < 0 ? -1 : len;
	}

	/**
	 * The harbour mark of two legs one after the other ({@link #line}'s {clearance, areas, harbour}): one of them
	 * touches harbour water and each one's water narrower than {@link #HARBOUR_EXEMPT} is harbour water.
	 */
	static int harbourOf(int[] a, int[] b)
	{
		boolean ok = (a[2] != 0 || a[0] >= HARBOUR_EXEMPT) && (b[2] != 0 || b[0] >= HARBOUR_EXEMPT);
		return ok && (a[2] != 0 || b[2] != 0) ? 1 : 0;
	}

	/**
	 * Waypoints a point at sea reaches (within {@link #JOIN_RANGE} tiles) by at most two straight legs in the
	 * boat's headings ({@link #twoLegs}), from any of the given start tiles (e.g. the water around a buoy); for each
	 * waypoint the shortest such way.
	 */
	public List<Join> join(int[][] fromTiles)
	{
		List<Join> out = new ArrayList<>();
		int[] line = new int[5];
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
					double len = twoLegs(t[0], t[1], x, y, line);
					if (len >= 0 && (best == null || len < best.length))
					{
						best = new Join(wp, t[0], t[1], line[3], line[4], len, line[0], line[1], line[2] != 0);
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
}
