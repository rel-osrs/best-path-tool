package com.bestpath.core.sea;

import com.bestpath.core.path.RadixHeap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One boat's search over the {@link SeaGraph}: from several start points at
 * once (each with its own starting cost, e.g. the ticks to walk to a dock),
 * to several end points. The state is a waypoint and the boat's heading, so
 * turning can cost extra.
 *
 * <p>Costs are in hundredths of a tick: a link takes its length / the boat's
 * speed; a link through tight water (below the boat's preferred clearance)
 * counts {@link #TIGHT_FACTOR} times; each 22.5-degree step of turning adds
 * the turn cost. Links the boat may not use (an area it may not enter,
 * narrower than its hard clearance outside harbours) are skipped. Besides
 * the cost a result gives the real sailing time (length / speed, no extras)
 * for the route's ticks.
 *
 * <p>Aimed search: an A* search. Each end may say what at least follows it (e.g. leaving the boat
 * and the shortest possible walk on, {@link End#after}); a waypoint's estimate
 * is the least, over the ends, of the straight distance to the end's water at
 * the boat's speed plus that. The estimate never overestimates and never
 * drops by more than a link costs, so every end's cost is still the least
 * cost; only ties may come out another way. States whose cost plus estimate
 * is above the bound are not searched, and the search stops once every end it
 * can reach is settled. Ends that cannot be reached within the bound even in
 * a straight line are left out at once.
 *
 * <p>Memory: nothing is kept between searches.
 * A search keeps, per state it reaches, its cost (an int) and the heading it
 * came from (a byte), and per waypoint its estimate, in pages of
 * {@value #PAGE} waypoints made only where the search goes; the real sailing
 * time and the course are worked out from the way back afterwards.
 *
 * <p>Not thread-safe: one search at a time.
 */
public final class SeaRouter
{
	public static final int TIGHT_FACTOR = 3;
	/** The heading "none yet" (a start point: the first link turns for free). */
	private static final int ANY = SeaGraph.HEADINGS;
	/**
	 * The heading "came through a channel" ({@link SeaChannels}): which one is kept beside the state
	 * ({@link States#channel}); the next link turns for free, as from a start.
	 */
	static final int CHANNEL = SeaGraph.HEADINGS + 1;
	static final int STATES = SeaGraph.HEADINGS + 2;
	/** Waypoints per page of search state. */
	static final int PAGE = 64;
	private static final int PAGE_STATES = PAGE * STATES;
	private static final int[] STEP_X = new int[SeaGraph.HEADINGS];
	private static final int[] STEP_Y = new int[SeaGraph.HEADINGS];

	static
	{
		for (int d = 0; d < SeaGraph.HEADINGS; d++)
		{
			STEP_X[d] = SeaGraph.OFF_X[d] * SeaGraph.SPACING / 2;
			STEP_Y[d] = SeaGraph.OFF_Y[d] * SeaGraph.SPACING / 2;
		}
	}

	private final SeaGraph g;
	/** States the last search reached (for checks). */
	private int lastReached;
	/** Pages the last search made (for checks). */
	private int lastPages;

	public SeaRouter(SeaGraph g)
	{
		this.g = g;
	}

	/** States the last search reached, and the pages of state it made. */
	public int[] lastSearchSize()
	{
		return new int[]{lastReached, lastPages};
	}

	/** What the search needs to know about the boat. */
	public static final class Rules
	{
		final int hard;
		final int preferred;
		final int areaMask;
		final double speed;
		final int turnCost;
		/** Per heading: a link's time, and its time in tight water (hundredths of a tick). */
		final int[] linkTime = new int[SeaGraph.HEADINGS];
		final int[] tightTime = new int[SeaGraph.HEADINGS];
		/** Per link byte: 1 allowed, 0 not, -1 look up its areas ({@link SeaGraph#byteAreas}). */
		final byte[] byByte = new byte[256];
		/** Hundredths of a tick per tile at least (each stretch is rounded on its own; stretches are 4 tiles or more). */
		final double perTile;

		/** @param turnCost hundredths of a tick per 22.5 degrees of turning */
		public Rules(Boat b, int turnCost)
		{
			this.hard = b.getType().hardClearance();
			this.preferred = b.getType().preferredClearance();
			this.areaMask = b.areaMask();
			this.speed = b.speed();
			this.turnCost = turnCost;
			for (int d = 0; d < SeaGraph.HEADINGS; d++)
			{
				linkTime[d] = time(SeaGraph.linkLength(d));
				tightTime[d] = linkTime[d] * TIGHT_FACTOR;
			}
			for (int v = 1; v < 256; v++)
			{
				int clear = SeaGraph.byteClearance(v);
				int areas = SeaGraph.byteAreas(v);
				byByte[v] = (byte) (clear == 0 ? 0 : areas < 0 ? -1 : allowed(clear, areas, SeaGraph.byteHarbour(v)) ? 1 : 0);
			}
			perTile = 100 / speed - 0.125;
		}

		boolean allowed(int clearance, int areas, boolean harbour)
		{
			return (areas & ~areaMask) == 0 && (clearance >= hard || harbour);
		}

		/** Time of a stretch (hundredths of a tick, rounded). */
		int time(double tiles)
		{
			return (int) Math.round(tiles * 100 / speed);
		}

		static int hundredths(double tiles)
		{
			return (int) Math.round(tiles * 100);
		}

		int cost(double tiles, int clearance)
		{
			return time(tiles) * (clearance < preferred ? TIGHT_FACTOR : 1);
		}
	}

	/** A start: joins into the graph, and the cost already spent before sailing (hundredths of a tick). */
	public static final class Start
	{
		final List<SeaGraph.Join> joins;
		final int cost;

		public Start(List<SeaGraph.Join> joins, int cost)
		{
			this.joins = joins;
			this.cost = cost;
		}
	}

	/**
	 * An end: its joins into the graph, and what at least follows arriving there (hundredths of a tick; e.g.
	 * leaving the boat and the shortest possible walk on), which only aims the search and counts against the bound.
	 */
	public static final class End
	{
		final List<SeaGraph.Join> joins;
		final int after;

		public End(List<SeaGraph.Join> joins, int after)
		{
			this.joins = joins;
			this.after = Math.max(0, after);
		}
	}

	/** The search's state, page by page: cost + 1 (0 = not reached) and the heading it came from; per waypoint its estimate + 1. */
	static final class States
	{
		final int[][] cost;
		final byte[][] from;
		final int[][] est;
		/** A {@link #CHANNEL} state -> the channel it came through (few: channels are rare). */
		final Map<Integer, Integer> channel = new HashMap<>();
		int pages;
		int reached;

		States(int waypoints)
		{
			int n = (waypoints + PAGE - 1) / PAGE;
			cost = new int[n][];
			from = new byte[n][];
			est = new int[n][];
		}

		/** Cost of a state, or -1 when not reached. */
		int cost(int state)
		{
			int[] p = cost[state / PAGE_STATES];
			return p == null ? -1 : p[state % PAGE_STATES] - 1;
		}

		int from(int state)
		{
			return from[state / PAGE_STATES][state % PAGE_STATES];
		}

		void set(int state, int c, int heading)
		{
			int page = state / PAGE_STATES;
			if (cost[page] == null)
			{
				cost[page] = new int[PAGE_STATES];
				from[page] = new byte[PAGE_STATES];
				pages++;
			}
			int[] p = cost[page];
			if (p[state % PAGE_STATES] == 0)
			{
				reached++;
			}
			p[state % PAGE_STATES] = c + 1;
			from[page][state % PAGE_STATES] = (byte) heading;
		}
	}

	/**
	 * A boat's course: straight legs in the boat's EXACT headings (22.5 degrees apart,
	 * {@link SeaGraph#EX}), corner to corner; the corners need not be whole tiles. Also the tiles the boat's centre
	 * passes, sampled along the exact legs (for route progress and following), packed x << 14 | y.
	 */
	public static final class Course
	{
		/** Tiles from the start to the end, packed x << 14 | y: the exact legs sampled at most a tile apart, rounded. */
		public final int[] tiles;
		/**
		 * Indexes into {@link #tiles} where a leg starts or ends: the first tile, every turn, the last tile (in order;
		 * two corners may share a tile when a leg is shorter than a tile). Leg i runs from corner i to corner i + 1.
		 */
		public final int[] corners;
		/** The corners exactly, in tiles (a tile's middle is its whole x / y): x0, y0, x1, y1, ... one pair per corner. */
		public final double[] points;
		/** Per leg its heading ({@link SeaGraph#EX}: 0 east, 1 east-north-east, ..., 3 north-north-east, 4 north, ...). */
		public final int[] headings;
		/** Its length in tiles (the legs' exact lengths added up). */
		public final double length;

		private Course(int[] tiles, int[] corners, double[] points, int[] headings, double length)
		{
			this.tiles = tiles;
			this.corners = corners;
			this.points = points;
			this.headings = headings;
			this.length = length;
		}

		/** Sailing it at the boat's speed (tiles per tick): hundredths of a tick, rounded up. */
		public int time(double speed)
		{
			return (int) Math.ceil(Rules.hundredths(length) / speed);
		}

		/**
		 * A course from legs {x0, y0, x1, y1, heading} one after the other (each starting where the last ended); legs
		 * in the same heading one after the other become one, empty ones are dropped. No legs: the course is the one
		 * point (x, y).
		 */
		static Course of(List<double[]> raw, double x, double y)
		{
			List<double[]> legs = new ArrayList<>();
			for (double[] l : raw)
			{
				if (Math.hypot(l[2] - l[0], l[3] - l[1]) < 1e-9)
				{
					continue;
				}
				double[] last = legs.isEmpty() ? null : legs.get(legs.size() - 1);
				if (last != null && (int) last[4] == (int) l[4])
				{
					last[2] = l[2];
					last[3] = l[3];
				}
				else
				{
					legs.add(l.clone());
				}
			}
			double[] points = new double[2 * (legs.size() + 1)];
			points[0] = legs.isEmpty() ? x : legs.get(0)[0];
			points[1] = legs.isEmpty() ? y : legs.get(0)[1];
			int[] headings = new int[legs.size()];
			int[] corners = new int[legs.size() + 1];
			int[] tiles = new int[16];
			int k = 0;
			tiles[k++] = tile(points[0], points[1]);
			double length = 0;
			for (int i = 0; i < legs.size(); i++)
			{
				double[] l = legs.get(i);
				double dx = l[2] - l[0];
				double dy = l[3] - l[1];
				length += Math.hypot(dx, dy);
				// samples at most one tile apart on each axis, so the rounded tiles step one at a time
				int m = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) - 1e-9));
				for (int s = 1; s <= m; s++)
				{
					int t = s == m ? tile(l[2], l[3]) : tile(l[0] + dx * s / m, l[1] + dy * s / m);
					if (t != tiles[k - 1])
					{
						if (k == tiles.length)
						{
							tiles = Arrays.copyOf(tiles, k * 2);
						}
						tiles[k++] = t;
					}
				}
				corners[i + 1] = k - 1;
				points[2 * i + 2] = l[2];
				points[2 * i + 3] = l[3];
				headings[i] = (int) l[4];
			}
			return new Course(Arrays.copyOf(tiles, k), corners, points, headings, length);
		}

		/** The tile a point is in (tile x covers x - 0.5 .. x + 0.5), packed x << 14 | y. */
		static int tile(double x, double y)
		{
			return (int) Math.floor(x + 0.5) << 14 | (int) Math.floor(y + 0.5);
		}
	}

	/** The result of {@link #search}: per end point the best cost, the real time, and where it came from. */
	public final class Result
	{
		private final int[] endCost;
		private final int[] endState;
		final SeaGraph.Join[] endJoin;
		private final Map<Integer, Integer> startOfState;
		final Map<Integer, SeaGraph.Join> joinOfState;
		final double rulesSpeed;
		private final Rules rules;
		final States states;

		Result(int ends, Map<Integer, Integer> startOfState, Map<Integer, SeaGraph.Join> joinOfState, Rules rules, States states)
		{
			this.rulesSpeed = rules.speed;
			this.rules = rules;
			endCost = new int[ends];
			endState = new int[ends];
			endJoin = new SeaGraph.Join[ends];
			Arrays.fill(endCost, -1);
			this.startOfState = startOfState;
			this.joinOfState = joinOfState;
			this.states = states;
		}

		/** Best cost to the end point (start cost included), or -1. */
		public int cost(int end)
		{
			return endCost[end];
		}

		/** Which start the best way to the end point began at. */
		public int startOf(int end)
		{
			return startOfState.get(path(end)[0]);
		}

		/** The states from the start to the end point's last waypoint. */
		int[] path(int end)
		{
			List<Integer> back = new ArrayList<>();
			for (int st = endState[end]; ; )
			{
				back.add(st);
				int h = st % STATES;
				if (h == ANY)
				{
					break;
				}
				int w = st / STATES;
				int prev = h == CHANNEL ? g.channelOther(states.channel.get(st), w)
					: g.waypointAt(g.x(w) - STEP_X[h], g.y(w) - STEP_Y[h]);
				st = prev * STATES + states.from(st);
			}
			int[] out = new int[back.size()];
			for (int i = 0; i < out.length; i++)
			{
				out[i] = back.get(out.length - 1 - i);
			}
			return out;
		}

		/**
		 * The course to the end point: straight legs in the boat's exact headings (22.5 degrees apart, corners where
		 * they meet need not be whole tiles). The way found (the start's join, the links, the end's join) is pulled
		 * straight where the boat may go ({@link #exactCourse}): the waypoints are 8 tiles apart, so a way along them
		 * may jog sideways from one row of waypoints to the next where one straight leg (or two) would do. Legs in the
		 * same heading one after the other are one leg.
		 */
		public Course course(int end)
		{
			int[] states = path(end);
			// the points the way passes, each stretch to the next straight in one heading; per stretch whether its
			// tiles were checked from the far end (the end's join)
			List<int[]> pts = new ArrayList<>();
			SeaGraph.Join in = joinOfState.get(states[0]);
			addPoint(pts, in.fromX, in.fromY, 0);
			addPoint(pts, in.cornerX, in.cornerY, 0);
			for (int k = 0; k < states.length; k++)
			{
				int w = states[k] / STATES;
				if (k > 0 && states[k] % STATES == CHANNEL)
				{
					// through a channel: its stretches (each straight, sailable both ways), from the waypoint before
					int c = this.states.channel.get(states[k]);
					int[] p = g.channelPoints(c);
					boolean forward = g.channelFrom(c) == states[k - 1] / STATES;
					for (int i = 0; i < p.length; i++)
					{
						int t = p[forward ? i : p.length - 1 - i];
						addPoint(pts, t >>> 14, t & 0x3FFF, 0);
					}
				}
				addPoint(pts, g.x(w), g.y(w), 0);
			}
			SeaGraph.Join out = endJoin[end];
			pts.get(pts.size() - 1)[2] = 1;
			addPoint(pts, out.cornerX, out.cornerY, 1);
			addPoint(pts, out.fromX, out.fromY, 0);
			return exactCourse(pts, rules);
		}
	}

	private static void addPoint(List<int[]> pts, int x, int y, int reverse)
	{
		int[] last = pts.isEmpty() ? null : pts.get(pts.size() - 1);
		if (last == null || last[0] != x || last[1] != y)
		{
			pts.add(new int[]{x, y, reverse});
		}
		else
		{
			last[2] = reverse;
		}
	}

	/**
	 * The way found (points, each stretch to the next straight on the search's grid) as a course of legs in the
	 * boat's exact headings (compass points 22.5 degrees apart), pulled straight: from each point, the furthest later
	 * point that at most two exact legs in neighbouring headings ({@link SeaGraph#exactTwoLegs}) reach, where this
	 * boat may go (its areas, its hard clearance outside harbours) and no tighter than the way they replace, is joined
	 * directly. Two such legs are the shortest way in the boat's headings between two points. A stretch that two exact
	 * legs can't follow (tight water, where the exact lines' corners stray up to about a tile from the grid's straight
	 * line) is split along its tiles until each part can, down to single steps along east, north-east, north...
	 * (exact headings themselves). Points: {x, y, tiles to the next one checked backwards}.
	 */
	private Course exactCourse(List<int[]> pts, Rules r)
	{
		int n = pts.size();
		List<double[]> legs = new ArrayList<>();
		if (n < 2)
		{
			return Course.of(legs, pts.get(0)[0], pts.get(0)[1]);
		}
		// each stretch's narrowest water, for "no tighter than the way it replaces"
		int[] clear = new int[n - 1];
		int[] line = new int[3];
		for (int i = 0; i + 1 < n; i++)
		{
			int[] a = pts.get(i);
			int[] b = pts.get(i + 1);
			boolean ok = a[2] != 0 ? g.line(b[0], b[1], a[0], a[1], line) : g.line(a[0], a[1], b[0], b[1], line);
			clear[i] = ok ? line[0] : 0;
		}
		double[] two = new double[7];
		int[] prev = {-1};
		int[] tight = new int[n];
		int i = 0;
		while (i < n - 1)
		{
			int tightest = Integer.MAX_VALUE;
			for (int j = i + 1; j < n; j++)
			{
				tightest = Math.min(tightest, clear[j - 1]);
				tight[j] = tightest;
			}
			int[] a = pts.get(i);
			int next = -1;
			for (int j = n - 1; j > i; j--)
			{
				int[] b = pts.get(j);
				if (fits(a[0], a[1], b[0], b[1], prev[0], Math.min(r.preferred, tight[j]), r, two))
				{
					next = j;
					break;
				}
			}
			if (next >= 0)
			{
				int[] b = pts.get(next);
				addTwo(legs, a[0], a[1], b[0], b[1], two, prev);
				i = next;
			}
			else
			{
				int[] b = pts.get(i + 1);
				int[] tiles = a[2] != 0 ? reversed(g.lineTiles(b[0], b[1], a[0], a[1])) : g.lineTiles(a[0], a[1], b[0], b[1]);
				split(legs, tiles, 0, tiles.length - 1, Math.min(r.preferred, clear[i]), r, two, prev);
				i++;
			}
		}
		return Course.of(legs, pts.get(0)[0], pts.get(0)[1]);
	}

	/** Whether at most two exact legs from (x0, y0) to (x1, y1) may be sailed by this boat, no tighter than {@code need}. */
	private boolean fits(int x0, int y0, int x1, int y1, int prevHeading, int need, Rules r, double[] two)
	{
		return g.exactTwoLegs(x0, y0, x1, y1, prevHeading, two) >= 0 && r.allowed((int) two[0], (int) two[1], two[2] != 0)
			&& two[0] >= need;
	}

	/** Adds the (one or two) legs {@link SeaGraph#exactTwoLegs} found ({@code two}) from (x0, y0) to (x1, y1). */
	private static void addTwo(List<double[]> legs, int x0, int y0, int x1, int y1, double[] two, int[] prev)
	{
		double cx = two[3];
		double cy = two[4];
		boolean corner = cx != x1 || cy != y1;
		if (corner)
		{
			legs.add(new double[]{x0, y0, cx, cy, two[5]});
		}
		legs.add(new double[]{corner ? cx : x0, corner ? cy : y0, x1, y1, two[6]});
		prev[0] = (int) two[6];
	}

	/**
	 * A stretch's tiles [lo, hi] as exact legs: two legs from its first to its last tile when they fit, else each half
	 * on its own; a single step (east, north-east, north, ...: an exact heading) is always taken as it is (the search
	 * already checked it).
	 */
	private void split(List<double[]> legs, int[] tiles, int lo, int hi, int need, Rules r, double[] two, int[] prev)
	{
		int ax = tiles[lo] >>> 14;
		int ay = tiles[lo] & 0x3FFF;
		int bx = tiles[hi] >>> 14;
		int by = tiles[hi] & 0x3FFF;
		if (hi - lo <= 1)
		{
			int h = SeaGraph.exactHeadingOf(bx - ax, by - ay);
			if (h >= 0)
			{
				legs.add(new double[]{ax, ay, bx, by, h});
				prev[0] = h;
			}
			return;
		}
		if (fits(ax, ay, bx, by, prev[0], need, r, two))
		{
			addTwo(legs, ax, ay, bx, by, two, prev);
			return;
		}
		int mid = (lo + hi) >>> 1;
		split(legs, tiles, lo, mid, need, r, two, prev);
		split(legs, tiles, mid, hi, need, r, two, prev);
	}

	private static int[] reversed(int[] a)
	{
		int[] r = new int[a.length];
		for (int i = 0; i < a.length; i++)
		{
			r[i] = a[a.length - 1 - i];
		}
		return r;
	}
	/** An end's water for the estimate: the box round its joins' first tiles (the boat arrives on one of them). */
	private static int[] box(List<SeaGraph.Join> joins, Rules r)
	{
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (SeaGraph.Join jn : joins)
		{
			if (r.allowed(jn.clearance, jn.areas, jn.harbour))
			{
				b[0] = Math.min(b[0], jn.fromX);
				b[1] = Math.min(b[1], jn.fromY);
				b[2] = Math.max(b[2], jn.fromX);
				b[3] = Math.max(b[3], jn.fromY);
			}
		}
		return b[0] == Integer.MAX_VALUE ? null : b;
	}

	/** Straight distance (tiles) between two boxes {x0, y0, x1, y1}. */
	private static double gap(int[] a, int[] b)
	{
		int dx = Math.max(0, Math.max(b[0] - a[2], a[0] - b[2]));
		int dy = Math.max(0, Math.max(b[1] - a[3], a[1] - b[3]));
		return Math.sqrt((double) dx * dx + (double) dy * dy);
	}

	/**
	 * From the starts to the ends, aimed at them (see the class notes). A state is searched only when its cost plus
	 * its estimate (which includes the end's {@link End#after}) is at most {@code bound} (hundredths of a tick).
	 */
	public Result searchTo(Rules r, List<Start> starts, List<End> ends, int bound)
	{
		States st = new States(g.waypointCount());
		Map<Integer, Integer> startOfState = new HashMap<>();
		Map<Integer, SeaGraph.Join> joinOfState = new HashMap<>();
		Result res = new Result(ends.size(), startOfState, joinOfState, r, st);
		// the starts' water, for leaving out ends that are out of reach even in a straight line
		List<int[]> startBoxes = new ArrayList<>();
		List<Integer> startCosts = new ArrayList<>();
		for (Start s : starts)
		{
			int[] b = box(s.joins, r);
			if (b != null)
			{
				startBoxes.add(b);
				startCosts.add(s.cost);
			}
		}
		// end points by waypoint; the ends that can be reached at all (an allowed join, within the bound in a line)
		Map<Integer, List<int[]>> endsAt = new HashMap<>(); // {end, join index}
		boolean[] live = new boolean[ends.size()];
		int liveCount = 0;
		List<int[]> aims = new ArrayList<>(); // {x0, y0, x1, y1, after}
		for (int e = 0; e < ends.size(); e++)
		{
			End end = ends.get(e);
			int[] b = box(end.joins, r);
			if (b == null)
			{
				continue;
			}
			long least = Long.MAX_VALUE;
			for (int s = 0; s < startBoxes.size(); s++)
			{
				least = Math.min(least, startCosts.get(s) + (long) Math.floor(gap(startBoxes.get(s), b) * r.perTile) - 1 + end.after);
			}
			if (least > bound)
			{
				continue;
			}
			for (int j = 0; j < end.joins.size(); j++)
			{
				SeaGraph.Join jn = end.joins.get(j);
				if (r.allowed(jn.clearance, jn.areas, jn.harbour))
				{
					endsAt.computeIfAbsent(jn.waypoint, k -> new ArrayList<>()).add(new int[]{e, j});
				}
			}
			live[e] = true;
			liveCount++;
			aims.add(new int[]{b[0], b[1], b[2], b[3], end.after});
		}
		int[][] aim = aims.toArray(new int[0][]);
		int found = 0;
		long worstEnd = -1;
		RadixHeap heap = new RadixHeap();
		if (liveCount > 0)
		{
			for (int s = 0; s < starts.size(); s++)
			{
				Start start = starts.get(s);
				for (SeaGraph.Join jn : start.joins)
				{
					if (!r.allowed(jn.clearance, jn.areas, jn.harbour))
					{
						continue;
					}
					int state = jn.waypoint * STATES + ANY;
					int c = start.cost + r.cost(jn.length, jn.clearance);
					int hv = estimate(st, jn.waypoint, aim, r.perTile);
					int old = st.cost(state);
					if ((long) c + hv <= bound && (old < 0 || c < old))
					{
						st.set(state, c, ANY);
						startOfState.put(state, s);
						joinOfState.put(state, jn);
						heap.push((long) (c + hv) << 32 | state);
					}
				}
			}
		}
		int hard = r.hard;
		int preferred = r.preferred;
		int turnCost = r.turnCost;
		while (!heap.isEmpty())
		{
			long key = heap.pop();
			int state = (int) key;
			int f = (int) (key >>> 32);
			int w = state / STATES;
			int c = st.cost(state);
			int hw = st.est[w / PAGE][w % PAGE] - 1;
			if (c + hw != f)
			{
				continue; // an older, dearer entry
			}
			if (found == liveCount && f >= worstEnd)
			{
				break; // every end that can be reached is settled: nothing later can improve one (estimates never overestimate)
			}
			int h = state % STATES;
			List<int[]> here = endsAt.get(w);
			if (here != null)
			{
				boolean better = false;
				for (int[] ej : here)
				{
					SeaGraph.Join jn = ends.get(ej[0]).joins.get(ej[1]);
					int ec = c + r.cost(jn.length, jn.clearance);
					if (res.endCost[ej[0]] < 0 || ec < res.endCost[ej[0]])
					{
						found += res.endCost[ej[0]] < 0 ? 1 : 0;
						res.endCost[ej[0]] = ec;
						res.endState[ej[0]] = state;
						res.endJoin[ej[0]] = jn;
						better = true;
					}
				}
				if (better && found == liveCount)
				{
					worstEnd = 0;
					for (int e = 0; e < live.length; e++)
					{
						worstEnd = live[e] ? Math.max(worstEnd, (long) res.endCost[e] + ends.get(e).after) : worstEnd;
					}
				}
			}
			int x = g.x(w);
			int y = g.y(w);
			for (int d = 0; d < SeaGraph.HEADINGS; d++)
			{
				int b = g.linkByte(w, d);
				if (b == 0)
				{
					continue;
				}
				int ok = r.byByte[b];
				if (ok == 0 || ok < 0 && (g.linkAreas(w, d) & ~r.areaMask) != 0 || ok < 0 && SeaGraph.byteClearance(b) < hard && !SeaGraph.byteHarbour(b))
				{
					continue;
				}
				int turn = h >= ANY ? 0 : Math.min(Math.abs(d - h), SeaGraph.HEADINGS - Math.abs(d - h));
				int nc = c + (SeaGraph.byteClearance(b) < preferred ? r.tightTime[d] : r.linkTime[d]) + turn * turnCost;
				int nw = g.waypointAt(x + STEP_X[d], y + STEP_Y[d]);
				int hv = estimate(st, nw, aim, r.perTile);
				if ((long) nc + hv > bound)
				{
					continue;
				}
				int ns = nw * STATES + d;
				int old = st.cost(ns);
				if (old < 0 || nc < old)
				{
					st.set(ns, nc, h);
					heap.push((long) (nc + hv) << 32 | ns);
				}
			}
			// channels: crooked water the links miss, for boats that fit it and may enter its areas
			for (int ch : g.channelsAt(w))
			{
				if (g.channelClearance(ch) < hard || (g.channelAreas(ch) & ~r.areaMask) != 0)
				{
					continue;
				}
				int nc = c + r.cost(g.channelLength(ch), g.channelClearance(ch));
				int nw = g.channelOther(ch, w);
				int hv = estimate(st, nw, aim, r.perTile);
				if ((long) nc + hv > bound)
				{
					continue;
				}
				int ns = nw * STATES + CHANNEL;
				int old = st.cost(ns);
				if (old < 0 || nc < old)
				{
					st.set(ns, nc, h);
					st.channel.put(ns, ch);
					heap.push((long) (nc + hv) << 32 | ns);
				}
			}
		}
		lastReached = st.reached;
		lastPages = st.pages;
		return res;
	}

	/**
	 * A waypoint's estimate (worked out once, kept in its page): the least, over the ends, of the straight distance to
	 * the end's water at the boat's speed (never more than sailing there costs) plus what follows the end; one
	 * hundredth less for the end's own join, which may be shorter than a link and is rounded on its own.
	 */
	private int estimate(States st, int w, int[][] aim, double perTile)
	{
		int[] page = st.est[w / PAGE];
		if (page == null)
		{
			page = new int[PAGE];
			st.est[w / PAGE] = page;
		}
		int v = page[w % PAGE];
		if (v != 0)
		{
			return v - 1;
		}
		int x = g.x(w);
		int y = g.y(w);
		long best = Long.MAX_VALUE;
		for (int[] a : aim)
		{
			int dx = Math.max(0, Math.max(a[0] - x, x - a[2]));
			int dy = Math.max(0, Math.max(a[1] - y, y - a[3]));
			long e = (long) Math.floor(Math.sqrt((double) dx * dx + (double) dy * dy) * perTile) + a[4];
			best = Math.min(best, e);
		}
		int h = best == Long.MAX_VALUE ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE / 4, best - 1));
		page[w % PAGE] = h + 1;
		return h;
	}
}
