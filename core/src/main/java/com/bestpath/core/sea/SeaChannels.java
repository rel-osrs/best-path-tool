package com.bestpath.core.sea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Narrow, crooked water the sea graph's straight links miss, so a boat that fits can still get through. For example,
 * the Ynysdail water joins the ocean only through a crooked channel among rocks (about 2139-2199, 3452-3455) where the
 * water is at most 2 tiles from an obstacle: a skiff or sloop fits tile by tile, but no straight link between
 * waypoints 8 tiles apart threads it.
 *
 * <p>A channel is a way tile by tile between two waypoints that the links don't join, straightened into a few
 * stretches (each a straight line the boat may sail, {@link SeaGraph#line}, both ways). The boat search uses it like
 * a link ({@link SeaGraph#withChannels}): only boats that fit (its clearance at least their hard clearance) and may
 * enter its areas.
 *
 * <p>How they are found, for the hard clearances boats have (2: skiffs and sloops, then 1: rafts): the waypoints are
 * put in pieces joined by the links such a boat may use (clearance at least that, or a harbour mark; areas left out)
 * and by the channels found before; then the water such a boat fits (clearance at least that) is flooded tile by tile
 * from every waypoint at once (nearest waypoint first, 8 directions, the boat's steps). Where two floods from pieces not
 * joined yet meet, the way from one waypoint to the other through the meeting point is a channel, and the two pieces
 * count as joined. So each place where two pieces meet gets one channel, the first one the floods reach.
 *
 * <p>Finding them takes about 1 s and 25 MB for the whole sea, so they are worked out ahead of time and stored in the
 * generated sea data; the plugin never floods the sea. The result depends only on the grid and the docks (their
 * harbours), so the stored channels match the sea the plugin loads.
 */
public final class SeaChannels
{
	/** The hard clearances boats have, widest first (sloops and skiffs 2, rafts 1). */
	private static final int[] NEEDS = {2, 1};
	private static final byte NONE = -1;

	private SeaChannels()
	{
	}

	/**
	 * The channels of the sea graph (made without channels), each its points packed x << 14 | y: the first and the
	 * last are waypoints' tiles, each stretch between two points a straight line the boat may sail both ways.
	 */
	public static List<int[]> find(SeaGraph g)
	{
		SeaGrid grid = g.getGrid();
		int n = g.waypointCount();
		int size = grid.size();
		int[] seed = new int[size];
		byte[] back = new byte[size];
		int[] queue = new int[size];
		List<int[]> out = new ArrayList<>();
		for (int need : NEEDS)
		{
			int[] piece = pieces(g, need, out);
			int[] joined = new int[n];
			for (int w = 0; w < n; w++)
			{
				joined[w] = w;
			}
			Arrays.fill(seed, -1);
			int head = 0;
			int tail = 0;
			for (int w = 0; w < n; w++)
			{
				int x = g.x(w);
				int y = g.y(w);
				if (grid.clearanceAt(x, y) >= need)
				{
					int i = grid.index(x, y);
					seed[i] = w;
					back[i] = NONE;
					queue[tail++] = x << 14 | y;
				}
			}
			while (head < tail)
			{
				int t = queue[head++];
				int x = t >>> 14;
				int y = t & 0x3FFF;
				int i = grid.index(x, y);
				int a = seed[i];
				for (int d = 0; d < 8; d++)
				{
					if (!grid.canMove(x, y, d))
					{
						continue;
					}
					int nx = x + SeaGrid.DX[d];
					int ny = y + SeaGrid.DY[d];
					if (grid.clearanceAt(nx, ny) < need)
					{
						continue;
					}
					int j = grid.index(nx, ny);
					if (seed[j] < 0)
					{
						seed[j] = a;
						back[j] = (byte) d;
						queue[tail++] = nx << 14 | ny;
						continue;
					}
					int pa = root(joined, piece[a]);
					int pb = root(joined, piece[seed[j]]);
					if (pa == pb)
					{
						continue;
					}
					int[] way = way(grid, back, x, y, nx, ny);
					int[] points = straighten(g, way, need);
					if (points != null)
					{
						out.add(points);
						joined[Math.max(pa, pb)] = Math.min(pa, pb);
					}
				}
			}
		}
		return out;
	}

	/**
	 * Per waypoint its piece (the least waypoint in it): waypoints joined by links a boat with this hard clearance may
	 * use (clearance at least {@code need}, or a harbour mark) or by one of {@code channels}.
	 */
	static int[] pieces(SeaGraph g, int need, List<int[]> channels)
	{
		int n = g.waypointCount();
		int[] p = new int[n];
		for (int w = 0; w < n; w++)
		{
			p[w] = w;
		}
		for (int w = 0; w < n; w++)
		{
			for (int d = 0; d < SeaGraph.HEADINGS; d++)
			{
				if (g.hasLink(w, d) && (g.linkClearance(w, d) >= need || g.linkHarbour(w, d)))
				{
					union(p, w, g.linkTarget(w, d));
				}
			}
		}
		for (int[] c : channels)
		{
			int a = g.waypointAt(c[0] >>> 14, c[0] & 0x3FFF);
			int b = g.waypointAt(c[c.length - 1] >>> 14, c[c.length - 1] & 0x3FFF);
			union(p, a, b);
		}
		for (int w = 0; w < n; w++)
		{
			p[w] = root(p, w);
		}
		return p;
	}

	private static void union(int[] p, int a, int b)
	{
		int ra = root(p, a);
		int rb = root(p, b);
		if (ra != rb)
		{
			p[Math.max(ra, rb)] = Math.min(ra, rb);
		}
	}

	private static int root(int[] p, int a)
	{
		while (p[a] != a)
		{
			p[a] = p[p[a]];
			a = p[a];
		}
		return a;
	}

	/** The tiles from the waypoint flooding (x0, y0) to it, the step on, and on to the waypoint flooding (x1, y1). */
	private static int[] way(SeaGrid grid, byte[] back, int x0, int y0, int x1, int y1)
	{
		int[] first = trace(grid, back, x0, y0);
		int[] second = trace(grid, back, x1, y1);
		int[] w = new int[first.length + second.length];
		for (int k = 0; k < first.length; k++)
		{
			w[k] = first[first.length - 1 - k];
		}
		System.arraycopy(second, 0, w, first.length, second.length);
		return w;
	}

	/** The tiles from (x, y) back to the waypoint its flood came from (packed x << 14 | y). */
	private static int[] trace(SeaGrid grid, byte[] back, int x, int y)
	{
		int[] t = new int[16];
		int k = 0;
		while (true)
		{
			if (k == t.length)
			{
				t = Arrays.copyOf(t, k * 2);
			}
			t[k++] = x << 14 | y;
			int d = back[grid.index(x, y)];
			if (d == NONE)
			{
				return Arrays.copyOf(t, k);
			}
			x -= SeaGrid.DX[d];
			y -= SeaGrid.DY[d];
		}
	}

	/**
	 * The way as few straight stretches: from each point the furthest tile on (looking on tile by tile, stopping at
	 * the first that fails) that a straight line reaches both ways with clearance at least {@code need}. Null when a
	 * single step can't be taken back (a wall one way only).
	 */
	static int[] straighten(SeaGraph g, int[] way, int need)
	{
		int[] pts = new int[way.length];
		int k = 0;
		pts[k++] = way[0];
		int i = 0;
		while (i < way.length - 1)
		{
			int j = i + 1;
			if (!both(g, way[i], way[j], need))
			{
				return null;
			}
			while (j + 1 < way.length && both(g, way[i], way[j + 1], need))
			{
				j++;
			}
			pts[k++] = way[j];
			i = j;
		}
		return Arrays.copyOf(pts, k);
	}

	private static boolean both(SeaGraph g, int a, int b, int need)
	{
		int[] line = new int[3];
		int ax = a >>> 14;
		int ay = a & 0x3FFF;
		int bx = b >>> 14;
		int by = b & 0x3FFF;
		return g.line(ax, ay, bx, by, line) && line[0] >= need && g.line(bx, by, ax, ay, line) && line[0] >= need;
	}
}
