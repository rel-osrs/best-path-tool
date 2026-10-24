package com.bestpath.overlay;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.DrawPath;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStep;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the published route on the game scene: the walking tiles and the
 * tiles where transports are used. The current step is drawn lighter and
 * thicker; steps already done (and the walked part of the current walk) are
 * not drawn. Walks are drawn as {@link DrawPath} has them (redrawn from the
 * player's tile on the route worker). Reads only the precomputed, immutable
 * {@link RouteResult}, {@link DrawPath} and {@link Progress}; no searching or
 * allocation-heavy work in render().
 * Tiles outside the loaded scene (or in instances) are simply not drawn.
 *
 * <p>A boat's course is drawn as straight lines, one per leg in the boat's exact 16 headings (22.5 degrees apart),
 * each with one arrow head pointing the way ({@link CourseArrows}), not as tile squares (a staircase of tiles drifts
 * from the true travel angle). Every part of a leg that shows is drawn, wherever its start is: the leg is cut to the
 * loaded scene and sampled about a tile apart when the route or the scene changes ({@link SceneCourses}), and each
 * frame draws between neighbouring samples that land on the canvas.
 *
 * <p>Everything is drawn in the player's colour ({@link RouteColours}); walks in the player's {@link PathStyle}: tiles,
 * a line through the tiles' middles, or that line with an arrow head where it turns ({@link PathTurns}). Drawn under
 * the player's character (its outline is left out of the drawing) and, with "Path under objects", under the objects
 * standing on the route's tiles.
 */
public class RoutePathOverlay extends Overlay
{

	private static final Color OUTLINE = new Color(0, 0, 0, 140);
	/** The "Path thickness" setting. */
	private final IntSupplier thickness;
	/** The pens for this frame ({@link PathStrokes}). */
	private PathStrokes pens = PathStrokes.of(PathStrokes.USUAL);
	private static final double WALK_ARROW_LENGTH = 12;
	private static final double WALK_ARROW_HALF_WIDTH = 6;
	/** At most this many arrow heads per walk (a very long walk on screen). */
	private static final int MAX_TURNS = 256;
	private static final double ARROW_LENGTH = 14;
	private static final double ARROW_HALF_WIDTH = 7;

	private final Client client;
	private final Supplier<RouteResult> route;
	private final Supplier<Progress> progress;
	private final Supplier<DrawPath> drawn;
	private final Supplier<RouteColours> colours;
	private final Supplier<PathStyle> style;
	/** The "Path under objects" setting. */
	private final BooleanSupplier underObjects;
	/** In the house: the walk to the furniture ({@link HousePath}, drawn only), or null. */
	private final Supplier<int[]> houseWalk;
	/** The objects on the route's tiles in the loaded scene, made again when the route, scene, plane or objects change. */
	private final List<TileObject> pathObjects = new ArrayList<>();
	private RouteResult objectsFor;
	private Scene objectsScene;
	private int objectsPlane = -1;
	/** Reused each frame (render() runs on the client thread only): a walk's line, its tiles, its run ends. */
	private final Path2D.Float walk = new Path2D.Float();
	private final Path2D.Float walkHeads = new Path2D.Float();
	private int[] walkTiles = new int[64];
	private final int[] runEnds = new int[MAX_TURNS];
	/** Reused each frame (render() runs on the client thread only). */
	private final Path2D.Float heads = new Path2D.Float();
	private final Path2D.Float course = new Path2D.Float();
	private final SceneCourses courses = new SceneCourses();
	/** Projects onto the canvas at the plane being drawn (set each frame; render() runs on the client thread only). */
	private int drawPlane;
	private final SceneCourses.Projector toCanvas;

	public RoutePathOverlay(Client client, Supplier<RouteResult> route, Supplier<Progress> progress,
		Supplier<DrawPath> drawn, Supplier<RouteColours> colours, Supplier<PathStyle> style, BooleanSupplier underObjects,
		Supplier<int[]> houseWalk, IntSupplier thickness)
	{
		this.thickness = thickness;
		this.houseWalk = houseWalk;
		this.underObjects = underObjects;
		this.colours = colours;
		this.style = style;
		this.client = client;
		this.route = route;
		this.progress = progress;
		this.drawn = drawn;
		this.toCanvas = lp -> Perspective.localToCanvas(client, lp, drawPlane);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		RouteResult r = route.get();
		if (r.segmentCount() == 0 && r.markerCount() == 0 && houseWalk.get() == null)
		{
			return null;
		}
		WorldView wv = client.getTopLevelWorldView();
		int plane = wv.getPlane();
		Progress p = progress.get();
		int current = p.isFor(r) ? p.getStep() : -1;
		DrawPath d = drawn.get();
		boolean redrawn = d.isFor(r);
		RouteColours c = colours.get();
		PathStyle ps = style.get();
		pens = PathStrokes.of(thickness.getAsInt());
		Shape oldClip = g.getClip();
		Area clip = uncovered(g, wv, plane, r);
		if (clip != null)
		{
			g.setClip(clip);
		}

		g.setStroke(pens.thin);
		for (int s = 0; s < r.segmentCount(); s++)
		{
			int step = r.segmentStep(s);
			if (step < current)
			{
				continue; // done
			}
			boolean now = step == current;
			RouteStep st = r.getSteps().get(step);
			if (st.getKind() == RouteStep.Kind.SAIL && st.cornerCount() >= 2)
			{
				drawCourse(g, r, wv, plane, step, now ? p.getTileIndex() : 0, now ? c.currentLine : c.course);
				g.setStroke(pens.thin);
				continue;
			}
			boolean own = !redrawn || !d.hasTiles(step);
			int n = own ? r.segmentLength(s) : d.tileCount(step);
			int from = now ? p.getTileIndex() : 0;
			if (ps != PathStyle.TILES && st.getKind() != RouteStep.Kind.SAIL)
			{
				if (walkTiles.length < n)
				{
					walkTiles = new int[Math.max(n, walkTiles.length * 2)];
				}
				for (int i = 0; i < n; i++)
				{
					walkTiles[i] = own ? r.segmentTile(s, i) : d.tile(step, i);
				}
				drawWalkLine(g, wv, plane, walkTiles, from, n, now, ps == PathStyle.ARROWS, now ? c.currentLine : c.pathLine);
				g.setStroke(pens.thin);
				continue;
			}
			g.setStroke(now ? pens.thick : pens.thin);
			for (int i = from; i < n; i++)
			{
				drawTile(g, wv, plane, own ? r.segmentTile(s, i) : d.tile(step, i), now ? c.currentFill : c.pathFill,
					now ? c.currentLine : c.pathLine);
			}
			g.setStroke(pens.thin);
		}
		g.setStroke(pens.thick);
		for (int i = 0; i < r.markerCount(); i++)
		{
			int step = r.markerStep(i);
			if (step >= current)
			{
				boolean now = step == current;
				drawTile(g, wv, plane, r.marker(i), now ? c.currentMarkerFill : c.markerFill,
					now ? c.currentLine : c.markerLine);
			}
		}
		int[] house = houseWalk.get();
		if (house != null && house.length >= 2)
		{
			// the walk inside the house to the furniture: as the current step's walk, in the player's style
			if (ps == PathStyle.TILES)
			{
				g.setStroke(pens.thick);
				for (int i = 1; i < house.length; i++)
				{
					drawTile(g, wv, plane, house[i], c.currentFill, c.currentLine);
				}
			}
			else
			{
				drawWalkLine(g, wv, plane, house, 0, house.length, true, ps == PathStyle.ARROWS, c.currentLine);
			}
			g.setStroke(pens.thin);
		}
		g.setClip(oldClip);
		return null;
	}

	/**
	 * Where the route may be drawn: the canvas without the player's character, and with "Path under
	 * objects" without the objects on the route's tiles; null when nothing is left out (the character not on screen).
	 */
	private Area uncovered(Graphics2D g, WorldView wv, int plane, RouteResult r)
	{
		List<Shape> cover = new ArrayList<>();
		Player me = client.getLocalPlayer();
		Shape hull = me == null ? null : me.getConvexHull();
		if (hull != null)
		{
			cover.add(hull);
		}
		if (underObjects.getAsBoolean())
		{
			objectsOnRoute(wv, plane, r);
			for (TileObject o : pathObjects)
			{
				Shape s = hullOf(o);
				if (s != null)
				{
					cover.add(s);
				}
			}
		}
		if (cover.isEmpty())
		{
			return null;
		}
		Shape old = g.getClip();
		Area a = new Area(old != null ? old : new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight()));
		for (Shape s : cover)
		{
			a.subtract(new Area(s));
		}
		return a;
	}

	private static Shape hullOf(TileObject o)
	{
		if (o instanceof GameObject)
		{
			return ((GameObject) o).getConvexHull();
		}
		if (o instanceof WallObject)
		{
			return ((WallObject) o).getConvexHull();
		}
		if (o instanceof DecorativeObject)
		{
			return ((DecorativeObject) o).getConvexHull();
		}
		return null;
	}

	/**
	 * Client thread: an object spawned or despawned (a door opened, a tree cut down), so the objects on the route are
	 * looked up again on the next frame.
	 */
	public void objectsChanged()
	{
		objectsFor = null;
	}

	/**
	 * The standing objects (not floor decorations) on the route's tiles on this plane in the loaded scene; looked up only
	 * when the route, the scene, the plane or the scene's objects change (render() then only projects their outlines).
	 */
	private void objectsOnRoute(WorldView wv, int plane, RouteResult r)
	{
		Scene scene = wv.getScene();
		if (r == objectsFor && scene == objectsScene && plane == objectsPlane)
		{
			return;
		}
		objectsFor = r;
		objectsScene = scene;
		objectsPlane = plane;
		pathObjects.clear();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return;
		}
		for (int s = 0; s < r.segmentCount(); s++)
		{
			for (int i = 0; i < r.segmentLength(s); i++)
			{
				addObjects(wv, tiles, plane, r.segmentTile(s, i));
			}
		}
		for (int i = 0; i < r.markerCount(); i++)
		{
			addObjects(wv, tiles, plane, r.marker(i));
		}
	}

	private void addObjects(WorldView wv, Tile[][][] tiles, int plane, int tile)
	{
		if (Tiles.plane(tile) != plane)
		{
			return;
		}
		LocalPoint lp = LocalPoint.fromWorld(wv, Tiles.x(tile), Tiles.y(tile));
		Tile t = lp == null ? null : tiles[plane][lp.getSceneX()][lp.getSceneY()];
		if (t == null)
		{
			return;
		}
		for (GameObject o : t.getGameObjects())
		{
			if (o != null && !pathObjects.contains(o))
			{
				pathObjects.add(o);
			}
		}
		if (t.getWallObject() != null)
		{
			pathObjects.add(t.getWallObject());
		}
		if (t.getDecorativeObject() != null)
		{
			pathObjects.add(t.getDecorativeObject());
		}
	}

	/**
	 * A sail step's course from tile index {@code k} on: per leg its exact line where it shows on the canvas (cut to
	 * the loaded scene, sampled about a tile apart), and one arrow head pointing the way.
	 */
	private void drawCourse(Graphics2D g, RouteResult r, WorldView wv, int plane, int step, int k, Color colour)
	{
		RouteStep s = r.getSteps().get(step);
		if (Tiles.plane(s.tile(0)) != plane)
		{
			return;
		}
		courses.scene(r, wv, step);
		drawPlane = plane;
		course.reset();
		heads.reset();
		courses.draw(step, k, toCanvas, course, heads, ARROW_LENGTH * pens.arrowScale, ARROW_HALF_WIDTH * pens.arrowScale);
		g.setStroke(pens.courseOutline);
		g.setColor(OUTLINE);
		g.draw(course);
		g.setStroke(pens.course);
		g.setColor(colour);
		g.draw(course);
		g.setStroke(pens.thin);
		g.setColor(OUTLINE);
		g.draw(heads);
		g.setColor(colour);
		g.fill(heads);
	}

	/**
	 * A walk from tile index {@code from} as one line through the tiles' middles (broken where a tile is off the loaded
	 * scene or on another plane), outlined in dark; with {@code arrows} an arrow head on each straight run's last tile
	 * pointing its way ({@link PathTurns}).
	 */
	private void drawWalkLine(Graphics2D g, WorldView wv, int plane, int[] tiles, int from, int n, boolean now, boolean arrows,
		Color colour)
	{
		walk.reset();
		walkHeads.reset();
		boolean open = false;
		for (int i = from; i < n; i++)
		{
			Point pt = centre(wv, plane, tiles[i]);
			if (pt == null)
			{
				open = false;
				continue;
			}
			if (open)
			{
				walk.lineTo(pt.getX(), pt.getY());
			}
			else
			{
				walk.moveTo(pt.getX(), pt.getY());
				open = true;
			}
		}
		if (arrows)
		{
			int k = PathTurns.runEnds(tiles, from, n, runEnds);
			for (int j = 0; j < k; j++)
			{
				int e = runEnds[j];
				Point tip = centre(wv, plane, tiles[e]);
				Point back = e > from ? centre(wv, plane, tiles[e - 1]) : null;
				if (tip != null && back != null)
				{
					CourseArrows.head(walkHeads, back.getX(), back.getY(), tip.getX(), tip.getY(), WALK_ARROW_LENGTH * pens.arrowScale,
						WALK_ARROW_HALF_WIDTH * pens.arrowScale);
				}
			}
		}
		g.setStroke(now ? pens.walkNowOutline : pens.walkOutline);
		g.setColor(OUTLINE);
		g.draw(walk);
		g.setStroke(now ? pens.walkNow : pens.walk);
		g.setColor(colour);
		g.draw(walk);
		if (arrows)
		{
			g.setStroke(pens.thin);
			g.setColor(OUTLINE);
			g.draw(walkHeads);
			g.setColor(colour);
			g.fill(walkHeads);
		}
	}

	/** The middle of the tile on the canvas, or null (another plane, off the loaded scene, behind the camera). */
	private Point centre(WorldView wv, int plane, int tile)
	{
		if (Tiles.plane(tile) != plane)
		{
			return null;
		}
		LocalPoint lp = LocalPoint.fromWorld(wv, Tiles.x(tile), Tiles.y(tile));
		return lp == null ? null : Perspective.localToCanvas(client, lp, plane);
	}

	private void drawTile(Graphics2D g, WorldView wv, int plane, int tile, Color fill, Color line)
	{
		if (Tiles.plane(tile) != plane)
		{
			return;
		}
		LocalPoint lp = LocalPoint.fromWorld(wv, Tiles.x(tile), Tiles.y(tile));
		if (lp == null)
		{
			return;
		}
		Polygon poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly == null)
		{
			return;
		}
		g.setColor(fill);
		g.fill(poly);
		g.setColor(line);
		g.draw(poly);
	}
}
