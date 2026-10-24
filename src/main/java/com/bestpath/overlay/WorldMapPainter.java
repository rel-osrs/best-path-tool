package com.bestpath.overlay;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.Progress;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.Arrays;

/**
 * Draws a precomputed {@link WorldMapPath} with a {@link MapView}: only picks
 * the zoom's level, transforms points and draws (no searching, no building).
 * Plain Java2D without RuneLite classes, so it also works without a client
 * (in tests). One instance per overlay, used on one thread (it reuses one
 * Path2D so a frame allocates next to nothing).
 *
 * <p>Colours match the scene and minimap overlays ({@link RouteColours}): the
 * route ahead, the current step lighter, the transport marks; steps already
 * done grey (the scene overlay leaves them out; on the map they show where the
 * player came from). The destination is a red ring.
 *
 * <p>Sailing: a boat's course is solid straight legs in the boat's exact headings (corners need not be whole tiles),
 * each with one arrow head on the leg's end, where the boat turns (or the course ends), pointing the way the boat
 * goes; a leg whose end is off the map shown has none ({@link CourseArrows}).
 *
 * <p>Planes: the world map is flat, so every line and mark
 * of the route is drawn at its x / y whatever its plane, never hidden. Lines
 * and marks on another plane than the player's are drawn a little fainter (and
 * under the player's plane), so stairs inside a building stay readable. The
 * destination is always full strength.
 */
public final class WorldMapPainter
{
	/** Whether the shown map has this world tile (RuneLite: WorldMapData.surfaceContainsPosition). */
	public interface MapArea
	{
		boolean contains(int x, int y);
	}

	/** Every tile is on the map (for drawing without a client, such as in tests). */
	public static final MapArea EVERYWHERE = (x, y) -> true;

	static final Color DONE = new Color(150, 150, 150, 200);
	static final Color DESTINATION = new Color(255, 60, 60, 255);
	private static final Color OUTLINE = new Color(0, 0, 0, 150);
	/** Opacity of lines and marks on another plane than the player's (1 = full). */
	static final float OTHER_PLANE_ALPHA = 0.55f;
	private static final Color DONE_FAINT = faint(DONE);
	/**
	 * The route's colours ({@link RouteColours}): the line ahead, the current step's (lighter) and the transport marks;
	 * each also faint for another plane.
	 */
	private RouteColours colours;
	private Color ahead;
	private Color current;
	private Color mark;
	private Color aheadFaint;
	private Color currentFaint;
	private Color markFaint;

	public WorldMapPainter()
	{
		setColours(RouteColours.defaults());
	}

	/** Paints with these colours from now on (the client thread, before {@link #paint}). */
	public void setColours(RouteColours c)
	{
		if (c == colours)
		{
			return;
		}
		colours = c;
		ahead = c.mapAhead;
		current = c.mapCurrent;
		mark = c.mapMark;
		aheadFaint = faint(ahead);
		currentFaint = faint(current);
		markFaint = faint(mark);
	}
	private static final Color OUTLINE_FAINT = faint(OUTLINE);

	private static final float[] WIDTHS = {2f, 3f, 4f};
	/** [width][style][0 = line, 1 = its dark outline] */
	private static final Stroke[][][] STROKES = new Stroke[WIDTHS.length][WorldMapPath.Style.values().length][2];
	private static final Stroke MARK_STROKE = new BasicStroke(1.5f);
	private static final Stroke RING_STROKE = new BasicStroke(2.5f);
	private static final Stroke RING_OUTLINE = new BasicStroke(4.5f);

	static
	{
		for (int w = 0; w < WIDTHS.length; w++)
		{
			for (WorldMapPath.Style s : WorldMapPath.Style.values())
			{
				for (int o = 0; o < 2; o++)
				{
					float width = WIDTHS[w] + (o == 1 ? 2f : 0f);
					float[] dash = dash(s, WIDTHS[w]);
					STROKES[w][s.ordinal()][o] = dash == null
						? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
						: new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f);
				}
			}
		}
	}

	private static Color faint(Color c)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * OTHER_PLANE_ALPHA));
	}

	private static float[] dash(WorldMapPath.Style s, float w)
	{
		switch (s)
		{
			case BOAT:
				return new float[]{w * 3, w * 2.5f};
			case HOP:
				return new float[]{0.1f, w * 1.8f};
			case SAIL:
			case WALK:
			default:
				return null;
		}
	}

	private enum State
	{
		DONE, AHEAD, CURRENT
	}

	private final Path2D.Float shape = new Path2D.Float();
	/** Arrow heads of the line being drawn, and its points on screen (reused; grown when a line is longer). */
	private final Path2D.Float heads = new Path2D.Float();
	private float[] px = new float[64];
	private float[] py = new float[64];
	private final double[] buf = new double[6];
	/** Arrow heads the last {@link #paint} drew (tests read it). */
	int headsDrawn;
	/** The area drawn into (the caller's clip), for putting each leg's arrow head where the leg shows. */
	private double clipX0;
	private double clipY0;
	private double clipX1;
	private double clipY1;
	/**
	 * The screen point where the first segment of the line being drawn really starts as a leg (the player is part way
	 * along it: the leg's corner behind him), for its head's direction and length; NaN when the first segment is a
	 * whole leg.
	 */
	private double legStartX = Double.NaN;
	private double legStartY = Double.NaN;

	/**
	 * Draws the route. The caller has set the clip to the map's visible area.
	 *
	 * @param plane    the player's plane: lines and marks on it full strength, the others fainter (none hidden)
	 * @param progress the player's progress (ignored unless it is about this path's route)
	 * @param area     whether the shown map has a tile (long ride lines and the destination need both ends on it)
	 */
	public void paint(Graphics2D g, WorldMapPath path, MapView view, int plane, Progress progress, MapArea area)
	{
		if (path.lineCount() == 0 && path.markCount() == 0 && path.getDestination() == -1)
		{
			return;
		}
		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		Stroke oldStroke = g.getStroke();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int current = progress != null && progress.getStep() >= 0 && progress.getGeneration() == path.getGeneration()
			? progress.getStep() : -1;
		int level = WorldMapPath.levelFor(view.getZoom());
		int width = view.getZoom() < 2 ? 0 : view.getZoom() < 5 ? 1 : 2;
		headsDrawn = 0;
		Rectangle clip = g.getClipBounds();
		clipX0 = clip == null ? -1e9 : clip.getMinX();
		clipY0 = clip == null ? -1e9 : clip.getMinY();
		clipX1 = clip == null ? 1e9 : clip.getMaxX();
		clipY1 = clip == null ? 1e9 : clip.getMaxY();

		// other planes (fainter) under the player's plane; within each: done first, then ahead, then the current step
		for (int round = 0; round < 2; round++)
		{
			boolean faint = round == 0;
			for (State pass : State.values())
			{
				for (int i = 0; i < path.lineCount(); i++)
				{
					WorldMapPath.Line line = path.line(i);
					if ((line.getPlane() != plane) != faint || !visible(line, view, area))
					{
						continue;
					}
					State st = state(line.getStep(), current);
					if (st == State.CURRENT && line.tileIndex(0) >= 0)
					{
						drawCurrentWalk(g, path, line, view, level, width, progress.getTileIndex(), pass, faint);
					}
					else if (st == pass)
					{
						drawPoints(g, line, view, level, width, colour(st, faint), faint, 0, line.level(level).length,
							Double.NaN, Double.NaN);
					}
				}
			}
			for (int i = 0; i < path.markCount(); i++)
			{
				WorldMapPath.Mark m = path.mark(i);
				int t = m.getTile();
				if ((Tiles.plane(t) != plane) != faint
					|| !view.overlaps(Tiles.x(t), Tiles.y(t), Tiles.x(t), Tiles.y(t), 1))
				{
					continue;
				}
				State st = state(m.getStep(), current);
				drawMark(g, m.getKind(), view.screenX(Tiles.x(t)), view.screenY(Tiles.y(t)),
					st == State.AHEAD ? (faint ? markFaint : mark) : colour(st, faint), faint);
			}
		}
		int d = path.getDestination();
		if (d != -1 && area.contains(Tiles.x(d), Tiles.y(d))
			&& view.overlaps(Tiles.x(d), Tiles.y(d), Tiles.x(d), Tiles.y(d), 2))
		{
			drawDestination(g, view.screenX(Tiles.x(d)), view.screenY(Tiles.y(d)));
		}
		g.setStroke(oldStroke);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : aa);
	}

	private static boolean visible(WorldMapPath.Line line, MapView view, MapArea area)
	{
		if (!view.overlaps(line.getMinX(), line.getMinY(), line.getMaxX(), line.getMaxY(), 2))
		{
			return false;
		}
		if (line.getStyle() == WorldMapPath.Style.BOAT)
		{
			// a ride into another map area (a dungeon, Zanaris...) would draw a line off into nothing
			int last = line.pointCount() - 1;
			return area.contains(line.x(0), line.y(0)) && area.contains(line.x(last), line.y(last));
		}
		return true;
	}

	private static State state(int step, int current)
	{
		if (current < 0 || step > current)
		{
			return State.AHEAD;
		}
		return step < current ? State.DONE : State.CURRENT;
	}

	private Color colour(State s, boolean faint)
	{
		switch (s)
		{
			case DONE:
				return faint ? DONE_FAINT : DONE;
			case CURRENT:
				return faint ? currentFaint : current;
			case AHEAD:
			default:
				return faint ? aheadFaint : ahead;
		}
	}

	/**
	 * The current walk / sail line: grey up to the player's tile, the current step's colour from there on (only the
	 * part for this pass).
	 */
	private void drawCurrentWalk(Graphics2D g, WorldMapPath path, WorldMapPath.Line line, MapView view, int level,
		int width, int k, State pass, boolean faint)
	{
		int[] pts = line.level(level);
		int first = line.tileIndex(pts[0]);
		int last = line.tileIndex(pts[pts.length - 1]);
		if (last <= k)
		{
			if (pass == State.DONE)
			{
				drawPoints(g, line, view, level, width, colour(State.DONE, faint), faint, 0, pts.length, Double.NaN,
					Double.NaN);
			}
			return;
		}
		if (first >= k)
		{
			if (pass == State.CURRENT)
			{
				drawPoints(g, line, view, level, width, colour(State.CURRENT, faint), faint, 0, pts.length, Double.NaN,
					Double.NaN);
			}
			return;
		}
		// split at the player's tile: [points before k] + tile k, then tile k + [points after k]
		int split = 0;
		while (split < pts.length && line.tileIndex(pts[split]) < k)
		{
			split++;
		}
		// where the player is: on a boat's course the point that far along its exact leg, else the step's tile
		double kx;
		double ky;
		if (path.coursePoint(line.getStep(), k, buf))
		{
			kx = buf[0];
			ky = buf[1];
		}
		else
		{
			int at = path.stepTile(line.getStep(), k);
			kx = Tiles.x(at);
			ky = Tiles.y(at);
		}
		int after = split < pts.length && line.tileIndex(pts[split]) == k ? split + 1 : split;
		if (pass == State.DONE)
		{
			drawPoints(g, line, view, level, width, colour(State.DONE, faint), faint, 0, split, kx, ky);
		}
		else if (pass == State.CURRENT)
		{
			drawFrom(g, line, view, level, width, colour(State.CURRENT, faint), faint, kx, ky, after);
		}
	}

	/**
	 * Points [from, to) of the level, then (endX, endY) if it is a number (the player's place: the part of a leg up to
	 * it gets no arrow head, the leg's head goes on its part ahead).
	 */
	private void drawPoints(Graphics2D g, WorldMapPath.Line line, MapView view, int level, int width, Color c,
		boolean faint, int from, int to, double endX, double endY)
	{
		int[] pts = line.level(level);
		shape.reset();
		int n = 0;
		for (int i = from; i < to; i++)
		{
			add(view, line.exactX(pts[i]), line.exactY(pts[i]), n++);
		}
		boolean partial = !Double.isNaN(endX);
		if (partial)
		{
			add(view, endX, endY, n++);
		}
		stroke(g, line.getStyle(), width, c, faint, n, partial);
	}

	/** (startX, startY), then points [from, end) of the level. */
	private void drawFrom(Graphics2D g, WorldMapPath.Line line, MapView view, int level, int width, Color c,
		boolean faint, double startX, double startY, int from)
	{
		int[] pts = line.level(level);
		shape.reset();
		int n = 0;
		add(view, startX, startY, n++);
		for (int i = from; i < pts.length; i++)
		{
			add(view, line.exactX(pts[i]), line.exactY(pts[i]), n++);
		}
		if (from > 0 && from < pts.length)
		{
			legStartX = view.screenX(line.exactX(pts[from - 1])); // the corner where the player's leg began
			legStartY = view.screenY(line.exactY(pts[from - 1]));
		}
		stroke(g, line.getStyle(), width, c, faint, n, false);
		legStartX = Double.NaN;
		legStartY = Double.NaN;
	}

	private void add(MapView view, double x, double y, int n)
	{
		float sx = (float) view.screenX(x);
		float sy = (float) view.screenY(y);
		if (n >= px.length)
		{
			px = Arrays.copyOf(px, n * 2);
			py = Arrays.copyOf(py, n * 2);
		}
		px[n] = sx;
		py[n] = sy;
		if (n == 0)
		{
			shape.moveTo(sx, sy);
		}
		else
		{
			shape.lineTo(sx, sy);
		}
	}

	/** @param lastPartial the last segment is only part of a leg (up to the player): no arrow head on it */
	private void stroke(Graphics2D g, WorldMapPath.Style style, int width, Color c, boolean faint, int points,
		boolean lastPartial)
	{
		if (points < 2)
		{
			return;
		}
		Stroke[] s = STROKES[width][style.ordinal()];
		g.setStroke(s[1]);
		g.setColor(faint ? OUTLINE_FAINT : OUTLINE);
		g.draw(shape);
		g.setStroke(s[0]);
		g.setColor(c);
		g.draw(shape);
		if (style == WorldMapPath.Style.SAIL)
		{
			drawHeads(g, width, c, faint, lastPartial ? points - 1 : points);
		}
	}

	/**
	 * The arrow heads of the course just drawn (its points in px / py; each segment is one leg, or the part of the
	 * player's leg ahead of him): one per leg, its tip on the leg's end (the turn, or the course's end) when that end
	 * is in the map's area. Legs shorter than the head get none (the player's leg counts from the corner where it
	 * began).
	 */
	private void drawHeads(Graphics2D g, int width, Color c, boolean faint, int points)
	{
		heads.reset();
		double len = 7 + 2 * width;
		double half = 3.5 + width;
		for (int i = 1; i < points; i++)
		{
			boolean partOfLeg = i == 1 && !Double.isNaN(legStartX);
			double ax = partOfLeg ? legStartX : px[i - 1];
			double ay = partOfLeg ? legStartY : py[i - 1];
			if (CourseArrows.endHead(heads, ax, ay, px[i], py[i], clipX0, clipY0, clipX1, clipY1, len, half, buf))
			{
				headsDrawn++;
			}
		}
		g.setStroke(MARK_STROKE);
		g.setColor(faint ? OUTLINE_FAINT : OUTLINE);
		g.draw(heads);
		g.setColor(c);
		g.fill(heads);
	}

	/** A transport's start: a small square; a teleport's landing: a small ring. */
	private static void drawMark(Graphics2D g, WorldMapPath.MarkKind kind, double x, double y, Color c,
		boolean faint)
	{
		int cx = (int) Math.round(x);
		int cy = (int) Math.round(y);
		Color outline = faint ? OUTLINE_FAINT : OUTLINE;
		g.setStroke(MARK_STROKE);
		if (kind == WorldMapPath.MarkKind.TELEPORT)
		{
			g.setColor(outline);
			g.drawOval(cx - 5, cy - 5, 10, 10);
			g.setColor(c);
			g.drawOval(cx - 4, cy - 4, 8, 8);
			return;
		}
		g.setColor(outline);
		g.fillRect(cx - 4, cy - 4, 8, 8);
		g.setColor(c);
		g.fillRect(cx - 3, cy - 3, 6, 6);
	}

	/** A red ring with a dot, always full strength (whatever its plane). */
	private static void drawDestination(Graphics2D g, double x, double y)
	{
		int cx = (int) Math.round(x);
		int cy = (int) Math.round(y);
		Color c = DESTINATION;
		g.setStroke(RING_OUTLINE);
		g.setColor(OUTLINE);
		g.drawOval(cx - 7, cy - 7, 14, 14);
		g.setStroke(RING_STROKE);
		g.setColor(c);
		g.drawOval(cx - 7, cy - 7, 14, 14);
		g.fillOval(cx - 2, cy - 2, 5, 5);
	}
}
