package com.bestpath.overlay;

import com.bestpath.core.data.WorldMaps;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.DrawPath;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.RouteStep;
import java.util.ArrayList;
import java.util.List;

/**
 * The route as the world map draws it: lines in world tiles, already cut down
 * to their corners and simplified for each zoom level, plus the points to mark.
 * Built ONCE on the route worker when a result is published ({@link #of}), and
 * again there when a walk is redrawn from the player's tile ({@link DrawPath}),
 * so the world map overlay's render() only picks a level, transforms and draws.
 * Immutable.
 *
 * <ul>
 * <li>Walks: a solid line along the walked tiles.</li>
 * <li>Sailing: the boat's course as straight legs in its exact 16 headings (22.5 degrees
 * apart), corner to corner at the exact corners (the step's {@link RouteStep#cornerX(int)}; not simplified), drawn
 * with one arrow head per leg pointing the way; a course without corners: along the sailed tiles.</li>
 * <li>Boats and other rides (charter ships, carts, fairy rings...): a dashed straight line from where the ride
 * starts to where it lands, plus a mark where it starts.</li>
 * <li>Ladders, doors, shortcuts: a mark where they are used, and a dotted line to where they land only when that
 * is near (at most {@link #HOP_MAX_TILES}, any plane, not the very same x / y); a cave entrance into a dungeon far
 * away in map coordinates gets no line.</li>
 * <li>Teleports: never a line, only a mark where they land.</li>
 * </ul>
 *
 * <p>Planes: the world map is flat, so lines and marks of every plane are kept; each line still has one plane (a walk
 * is split where the plane changes, a hop takes its start's plane) so the painter can draw the player's own plane at
 * full strength and the others a little fainter.
 */
public final class WorldMapPath
{
	/** How a line is drawn. */
	public enum Style
	{
		/** Solid, along the tiles. */
		WALK,
		/** A boat's course: straight legs corner to corner, with arrow heads. */
		SAIL,
		/** Dashed, straight from the ride's start to its landing. */
		BOAT,
		/** Dotted, a short ladder / door / shortcut jump. */
		HOP
	}

	/** What a mark shows. */
	public enum MarkKind
	{
		/** Where a transport (ladder, door, boat...) is used. */
		TRANSPORT,
		/** Where a teleport lands. */
		TELEPORT
	}

	/** Simplification per zoom level, in tiles (level 0 = every corner, nothing lost). */
	static final double[] TOLERANCES = {0, 1, 2.5, 6, 15};
	/** Simplify only as far as this many pixels of error on screen. */
	static final double MAX_ERROR_PIXELS = 1.0;
	/** A gap this large inside one walk or sail step starts a new line (never a line across the map). */
	static final int MAX_GAP_TILES = 64;
	/** A ladder / door / shortcut gets a dotted line only when it lands this near. */
	static final int HOP_MAX_TILES = 16;

	public static final WorldMapPath EMPTY = new WorldMapPath(-1, new Line[0], new Mark[0], -1, List.of(), null, -1,
		Placement.AS_IS);

	/**
	 * Where the open world map draws a game tile (underground maps draw many areas elsewhere, see {@link WorldMaps}):
	 * the packed drawn tile (same plane), or -1 when that map does not show the tile.
	 */
	public interface Placement
	{
		/** Every tile where it is (an unknown map, or map data without the world maps). */
		Placement AS_IS = t -> t;

		int place(int tile);

		/** The tiles as map {@code map} of {@code maps} draws them ({@link #AS_IS} for map -1). */
		static Placement on(WorldMaps maps, int map)
		{
			if (map < 0 || maps == null || map >= maps.mapCount())
			{
				return AS_IS;
			}
			return t ->
			{
				int plane = Tiles.plane(t);
				int d = maps.drawn(map, Tiles.x(t), Tiles.y(t), plane);
				return d < 0 ? -1 : Tiles.pack(WorldMaps.x(d), WorldMaps.y(d), plane);
			};
		}
	}

	/** One line on one plane. */
	public static final class Line
	{
		private final Style style;
		private final int step;
		private final int plane;
		private final int[] xs;
		private final int[] ys;
		/** For walk / sail lines: the step's tile index of each point; for the others -1. */
		private final int[] tileIndex;
		/** A boat's course: its points exactly (world tiles, not whole ones); null for every other line. */
		private final double[] exactX;
		private final double[] exactY;
		/** Per level of {@link #TOLERANCES}: which points to draw (positions into xs / ys). */
		private final int[][] levels;
		private final int minX;
		private final int minY;
		private final int maxX;
		private final int maxY;

		Line(Style style, int step, int plane, int[] xs, int[] ys, int[] tileIndex)
		{
			this(style, step, plane, xs, ys, tileIndex, null, null);
		}

		/** With {@code exactX} / {@code exactY}: a boat's course, its exact corners (every level keeps them all). */
		Line(Style style, int step, int plane, int[] xs, int[] ys, int[] tileIndex, double[] exactX, double[] exactY)
		{
			this.style = style;
			this.step = step;
			this.plane = plane;
			this.xs = xs;
			this.ys = ys;
			this.tileIndex = tileIndex;
			this.exactX = exactX;
			this.exactY = exactY;
			int[][] lv = new int[TOLERANCES.length][];
			for (int i = 0; i < lv.length; i++)
			{
				lv[i] = simplify(xs, ys, exactX != null ? 0 : TOLERANCES[i]);
			}
			this.levels = lv;
			int ax = Integer.MAX_VALUE;
			int ay = Integer.MAX_VALUE;
			int bx = Integer.MIN_VALUE;
			int by = Integer.MIN_VALUE;
			for (int i = 0; i < xs.length; i++)
			{
				ax = Math.min(ax, xs[i]);
				ay = Math.min(ay, ys[i]);
				bx = Math.max(bx, xs[i]);
				by = Math.max(by, ys[i]);
			}
			minX = ax;
			minY = ay;
			maxX = bx;
			maxY = by;
		}

		public Style getStyle()
		{
			return style;
		}

		/** The index of the route step this line draws. */
		public int getStep()
		{
			return step;
		}

		public int getPlane()
		{
			return plane;
		}

		/** Corner points (every one; levels pick from these). */
		public int pointCount()
		{
			return xs.length;
		}

		public int x(int i)
		{
			return xs[i];
		}

		public int y(int i)
		{
			return ys[i];
		}

		/** Point i's x exactly (a course's corner need not be a whole tile; other lines: {@link #x}). */
		public double exactX(int i)
		{
			return exactX != null ? exactX[i] : xs[i];
		}

		/** Point i's y exactly. */
		public double exactY(int i)
		{
			return exactY != null ? exactY[i] : ys[i];
		}

		/** The step's tile index of point i (walk / sail lines), else -1. */
		public int tileIndex(int i)
		{
			return tileIndex[i];
		}

		/** The points to draw at this level: positions into the corner points, first and last always kept. */
		public int[] level(int level)
		{
			return levels[level];
		}

		public int getMinX()
		{
			return minX;
		}

		public int getMinY()
		{
			return minY;
		}

		public int getMaxX()
		{
			return maxX;
		}

		public int getMaxY()
		{
			return maxY;
		}
	}

	/** One marked tile. */
	public static final class Mark
	{
		private final MarkKind kind;
		private final int step;
		private final int tile;

		Mark(MarkKind kind, int step, int tile)
		{
			this.kind = kind;
			this.step = step;
			this.tile = tile;
		}

		public MarkKind getKind()
		{
			return kind;
		}

		public int getStep()
		{
			return step;
		}

		/** Packed tile (core Tiles). */
		public int getTile()
		{
			return tile;
		}
	}

	private final long generation;
	private final Line[] lines;
	private final Mark[] marks;
	private final int destination;
	private final List<RouteStep> steps;
	/** The walks as drawn (redrawn lines), or null: the steps' own tiles. */
	private final DrawPath drawn;
	/** The world map this was built for (-1: unknown, every tile where it is) and where that map draws tiles. */
	private final int map;
	private final Placement placement;

	private WorldMapPath(long generation, Line[] lines, Mark[] marks, int destination, List<RouteStep> steps,
		DrawPath drawn, int map, Placement placement)
	{
		this.map = map;
		this.placement = placement;
		this.generation = generation;
		this.lines = lines;
		this.marks = marks;
		this.destination = destination;
		this.steps = steps;
		this.drawn = drawn;
	}

	/**
	 * Builds the world map's draw lists for a published result (call on the route worker). A result without a found
	 * route has no lines but keeps its destination mark (e.g. while calculating). The walks are as {@code drawn} has them
	 * when it is for this result (a walk redrawn from the player's tile; null: the steps' own tiles). For world map
	 * {@code map} (-1: unknown) which draws tiles as {@code placement} says: every line, mark and the destination where
	 * that map draws them, left out where it does not show them, and a walk cut where the map's shift changes (two areas
	 * drawn apart).
	 */
	public static WorldMapPath of(RouteResult r, DrawPath drawn, int map, Placement placement)
	{
		if (r == null)
		{
			return map == -1 ? EMPTY : new WorldMapPath(-1, new Line[0], new Mark[0], -1, List.of(), null, map, placement);
		}
		int dest = r.getRequest() == null || r.getDestination() == -1 ? -1 : placement.place(r.getDestination());
		if (r.getStatus() != RouteStatus.READY)
		{
			return new WorldMapPath(r.getGeneration(), new Line[0], new Mark[0], dest, List.of(), null, map, placement);
		}
		DrawPath d = drawn != null && drawn.isFor(r) ? drawn : null;
		List<RouteStep> steps = r.getSteps();
		List<Line> lines = new ArrayList<>();
		List<Mark> marks = new ArrayList<>();
		for (int si = 0; si < steps.size(); si++)
		{
			RouteStep s = steps.get(si);
			switch (s.getKind())
			{
				case SAIL:
					if (s.cornerCount() >= 2)
					{
						addCourse(lines, s, si, placement);
					}
					else
					{
						addAlongTiles(lines, tiles(s, si, d), si, Style.SAIL, placement);
					}
					break;
				case WALK:
					addAlongTiles(lines, tiles(s, si, d), si, Style.WALK, placement);
					break;
				case TELEPORT:
					int lands = s.getEndTile() == -1 ? -1 : placement.place(s.getEndTile());
					if (lands != -1)
					{
						marks.add(new Mark(MarkKind.TELEPORT, si, lands));
					}
					break;
				case BOAT:
				case TRAVERSAL:
				case DOOR:
				default:
					int from = s.getMarkerTile() == -1 ? -1 : placement.place(s.getMarkerTile());
					int to = s.getEndTile() == -1 ? -1 : placement.place(s.getEndTile());
					if (from != -1)
					{
						marks.add(new Mark(MarkKind.TRANSPORT, si, from));
					}
					if (from != -1 && to != -1 && (Tiles.x(from) != Tiles.x(to) || Tiles.y(from) != Tiles.y(to)))
					{
						boolean boat = s.getKind() == RouteStep.Kind.BOAT;
						if (boat || distance(from, to) <= HOP_MAX_TILES)
						{
							lines.add(new Line(boat ? Style.BOAT : Style.HOP, si, Tiles.plane(from),
								new int[]{Tiles.x(from), Tiles.x(to)}, new int[]{Tiles.y(from), Tiles.y(to)},
								new int[]{-1, -1}));
						}
					}
					break;
			}
		}
		return new WorldMapPath(r.getGeneration(), lines.toArray(new Line[0]), marks.toArray(new Mark[0]), dest,
			steps, d, map, placement);
	}

	/** A walk / sail step's tiles: as drawn (redrawn) when known, else the step's own. */
	private static int[] tiles(RouteStep s, int step, DrawPath d)
	{
		if (d != null && d.hasTiles(step))
		{
			return d.tiles(step);
		}
		int[] t = new int[s.tileCount()];
		for (int i = 0; i < t.length; i++)
		{
			t[i] = s.tile(i);
		}
		return t;
	}

	/**
	 * A sail step's course as one line through its exact corners (each point's tile index is the corner's; the whole
	 * x / y for the line's box are the corners' tiles). Left out unless the map draws every corner with one shift
	 * (the sea is on the surface map, which draws everything where it is).
	 */
	private static void addCourse(List<Line> out, RouteStep s, int step, Placement placement)
	{
		int n = s.cornerCount();
		int plane = Tiles.plane(s.tile(0));
		int[] xs = new int[n];
		int[] ys = new int[n];
		int[] idx = new int[n];
		double[] ex = new double[n];
		double[] ey = new double[n];
		int shiftX = 0;
		int shiftY = 0;
		for (int i = 0; i < n; i++)
		{
			int tx = (int) Math.floor(s.cornerX(i) + 0.5);
			int ty = (int) Math.floor(s.cornerY(i) + 0.5);
			int at = placement.place(Tiles.pack(tx, ty, plane));
			if (at == -1 || i > 0 && (Tiles.x(at) - tx != shiftX || Tiles.y(at) - ty != shiftY))
			{
				return;
			}
			shiftX = Tiles.x(at) - tx;
			shiftY = Tiles.y(at) - ty;
			ex[i] = s.cornerX(i) + shiftX;
			ey[i] = s.cornerY(i) + shiftY;
			xs[i] = tx + shiftX;
			ys[i] = ty + shiftY;
			idx[i] = s.corner(i);
		}
		out.add(new Line(Style.SAIL, step, plane, xs, ys, idx, ex, ey));
	}

	/**
	 * Splits a walk / sail step's tiles into lines, corners only, where the map draws them: a new line at a plane
	 * change, a big gap or where the map's shift changes; tiles the map does not show are left out.
	 */
	private static void addAlongTiles(List<Line> out, int[] s, int step, Style style, Placement placement)
	{
		int n = s.length;
		int[] at = new int[n];
		for (int i = 0; i < n; i++)
		{
			at[i] = placement.place(s[i]);
		}
		int start = -1;
		for (int i = 0; i < n; i++)
		{
			if (at[i] == -1)
			{
				if (start >= 0)
				{
					addRun(out, at, step, style, start, i);
				}
				start = -1;
				continue;
			}
			if (start >= 0 && (Tiles.plane(at[i]) != Tiles.plane(at[i - 1]) || distance(at[i], at[i - 1]) > MAX_GAP_TILES
				|| Tiles.x(at[i]) - Tiles.x(s[i]) != Tiles.x(at[i - 1]) - Tiles.x(s[i - 1])
				|| Tiles.y(at[i]) - Tiles.y(s[i]) != Tiles.y(at[i - 1]) - Tiles.y(s[i - 1])))
			{
				addRun(out, at, step, style, start, i);
				start = i;
			}
			if (start < 0)
			{
				start = i;
			}
		}
		if (start >= 0)
		{
			addRun(out, at, step, style, start, n);
		}
	}

	/** Tiles [from, to) of the step as one line of its corners (points where the direction changes). */
	private static void addRun(List<Line> out, int[] s, int step, Style style, int from, int to)
	{
		int[] xs = new int[to - from];
		int[] ys = new int[to - from];
		int[] idx = new int[to - from];
		int n = 0;
		for (int i = from; i < to; i++)
		{
			int t = s[i];
			int x = Tiles.x(t);
			int y = Tiles.y(t);
			if (n > 0 && xs[n - 1] == x && ys[n - 1] == y)
			{
				continue; // the same tile twice
			}
			// drop the previous point when it lies straight between its neighbours (same direction on and on)
			if (n >= 2 && straight(xs[n - 2], ys[n - 2], xs[n - 1], ys[n - 1], x, y))
			{
				n--;
			}
			xs[n] = x;
			ys[n] = y;
			idx[n] = i;
			n++;
		}
		if (n < 2)
		{
			return; // a single tile: nothing to draw as a line
		}
		out.add(new Line(style, step, Tiles.plane(s[from]), trim(xs, n), trim(ys, n), trim(idx, n)));
	}

	/** True if b lies on the way from a to c in the same direction (a -> b -> c is one straight run). */
	static boolean straight(int ax, int ay, int bx, int by, int cx, int cy)
	{
		long dx1 = bx - ax;
		long dy1 = by - ay;
		long dx2 = cx - bx;
		long dy2 = cy - by;
		return dx1 * dy2 - dy1 * dx2 == 0 && dx1 * dx2 + dy1 * dy2 > 0;
	}

	/**
	 * Douglas-Peucker: the positions of the points to keep so that no dropped point is further than {@code tolerance}
	 * tiles from the drawn line. First and last are always kept; 0 keeps every point.
	 */
	static int[] simplify(int[] xs, int[] ys, double tolerance)
	{
		int n = xs.length;
		if (tolerance <= 0 || n <= 2)
		{
			int[] all = new int[n];
			for (int i = 0; i < n; i++)
			{
				all[i] = i;
			}
			return all;
		}
		boolean[] keep = new boolean[n];
		keep[0] = true;
		keep[n - 1] = true;
		double tol2 = tolerance * tolerance;
		int[] stack = new int[2 * n];
		int top = 0;
		stack[top++] = 0;
		stack[top++] = n - 1;
		while (top > 0)
		{
			int b = stack[--top];
			int a = stack[--top];
			double worst = -1;
			int at = -1;
			for (int i = a + 1; i < b; i++)
			{
				double d = distance2(xs[i], ys[i], xs[a], ys[a], xs[b], ys[b]);
				if (d > worst)
				{
					worst = d;
					at = i;
				}
			}
			if (at != -1 && worst > tol2)
			{
				keep[at] = true;
				stack[top++] = a;
				stack[top++] = at;
				stack[top++] = at;
				stack[top++] = b;
			}
		}
		int count = 0;
		for (boolean k : keep)
		{
			if (k)
			{
				count++;
			}
		}
		int[] out = new int[count];
		int j = 0;
		for (int i = 0; i < n; i++)
		{
			if (keep[i])
			{
				out[j++] = i;
			}
		}
		return out;
	}

	/** Squared distance from point p to the segment a-b. */
	private static double distance2(int px, int py, int ax, int ay, int bx, int by)
	{
		double dx = bx - ax;
		double dy = by - ay;
		double len2 = dx * dx + dy * dy;
		double t = len2 == 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
		t = Math.max(0, Math.min(1, t));
		double ex = ax + t * dx - px;
		double ey = ay + t * dy - py;
		return ex * ex + ey * ey;
	}

	/** The most simplified level whose error stays within {@link #MAX_ERROR_PIXELS} at this zoom (pixels per tile). */
	public static int levelFor(double zoom)
	{
		int best = 0;
		for (int i = 1; i < TOLERANCES.length; i++)
		{
			if (TOLERANCES[i] * zoom <= MAX_ERROR_PIXELS)
			{
				best = i;
			}
		}
		return best;
	}

	private static int distance(int a, int b)
	{
		return Math.max(Math.abs(Tiles.x(a) - Tiles.x(b)), Math.abs(Tiles.y(a) - Tiles.y(b)));
	}

	private static int[] trim(int[] a, int n)
	{
		if (a.length == n)
		{
			return a;
		}
		int[] c = new int[n];
		System.arraycopy(a, 0, c, 0, n);
		return c;
	}

	/** The {@link RouteResult#getGeneration()} this was built from (-1 for {@link #EMPTY}). */
	public long getGeneration()
	{
		return generation;
	}

	public int lineCount()
	{
		return lines.length;
	}

	public Line line(int i)
	{
		return lines[i];
	}

	public int markCount()
	{
		return marks.length;
	}

	public Mark mark(int i)
	{
		return marks[i];
	}

	/** The packed destination tile, or -1 (nothing asked for, or arrived). */
	public int getDestination()
	{
		return destination;
	}

	/**
	 * Where on step {@code step}'s exact course the player at its tile index {@code k} is (a sail step with corners),
	 * x and y in world tiles into {@code out}; false for other steps.
	 */
	public boolean coursePoint(int step, int k, double[] out)
	{
		RouteStep s = steps.get(step);
		if (s.cornerCount() < 2)
		{
			return false;
		}
		CourseArrows.pointAt(s, k, out);
		// where the map draws the course (its line is only kept when one shift draws all of it)
		int tx = (int) Math.floor(s.cornerX(0) + 0.5);
		int ty = (int) Math.floor(s.cornerY(0) + 0.5);
		int at = placement.place(Tiles.pack(tx, ty, Tiles.plane(s.tile(0))));
		if (at != -1)
		{
			out[0] += Tiles.x(at) - tx;
			out[1] += Tiles.y(at) - ty;
		}
		return true;
	}

	/**
	 * The packed tile at index {@code i} of step {@code step} (walk / sail steps: where the player has got to), where
	 * the map draws it (as it is when the map does not show it).
	 */
	public int stepTile(int step, int i)
	{
		int t;
		if (drawn != null && drawn.hasTiles(step))
		{
			t = drawn.tile(step, Math.max(0, Math.min(i, drawn.tileCount(step) - 1)));
		}
		else
		{
			RouteStep s = steps.get(step);
			t = s.tile(Math.max(0, Math.min(i, s.tileCount() - 1)));
		}
		int at = placement.place(t);
		return at == -1 ? t : at;
	}

	/** The world map this was built for: -1 when unknown (every tile where it is). */
	public int getMap()
	{
		return map;
	}
}
