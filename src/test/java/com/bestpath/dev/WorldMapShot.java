package com.bestpath.dev;

import com.bestpath.core.path.Tiles;
import com.bestpath.overlay.MapView;
import com.bestpath.overlay.WorldMapPainter;
import com.bestpath.overlay.WorldMapPath;
import com.bestpath.overlay.WorldMapPaths;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * DEV ONLY (src/test, never in the plugin jar): pictures of the world map route
 * overlay without the game. The plugin's own {@link WorldMapPath} (built as on
 * the route worker) and {@link WorldMapPainter} draw sample routes over a plain
 * grid (no map pictures: a dark background with a line every 8 tiles).
 * {@code gradlew worldMapShot} writes build/world-map-shots/*.png.
 */
public final class WorldMapShot
{
	private static final int W = 760;
	private static final int H = 500;

	private WorldMapShot()
	{
	}

	public static void main(String[] args) throws IOException
	{
		File dir = new File(args.length > 0 ? args[0] : "build/world-map-shots");
		if (!dir.isDirectory() && !dir.mkdirs())
		{
			throw new IOException("Can't make " + dir);
		}
		sceneClip(dir);
		RouteResult sample = FakeRoutes.eightSteps(1);
		WorldMapPath p = WorldMapPaths.of(sample);
		shot(dir, "wm-1-route-ahead", p, new MapView(0, 0, W, H, 3222, 3338, 1.75), 0, Progress.NONE,
			"Sample route ahead (zoom 1.75): walk, door, teleport (no line), walk to the manhole");
		shot(dir, "wm-2-sewers", p, new MapView(0, 0, W, H, 3195, 9880, 4), 0, Progress.NONE,
			"The same route in the sewers (zoom 4): walk, pipe shortcut, walk to the red destination");
		shot(dir, "wm-3-progress", p, new MapView(0, 0, W, H, 3222, 3338, 1.75), 0, new Progress(1, 3, 20),
			"Player on step 4 (walk after the teleport), 20 tiles along: done grey, current yellow");
		WorldMapPath ride = WorldMapPaths.of(rideRoute());
		shot(dir, "wm-4-boat-and-sail", ride, new MapView(0, 0, W, H, 3000, 3170, 3), 0, new Progress(2, 1, 0),
			"Walk, boat Port Sarim -> Karamja (dashed, current), short shortcut (dotted), sailing course (arrows)");
		shot(dir, "wm-5-zoomed-out", ride, new MapView(0, 0, W, H, 3000, 3170, 0.75), 0, Progress.NONE,
			"Zoomed out (0.75 px per tile): simplified lines, level " + WorldMapPath.levelFor(0.75));
		shot(dir, "wm-6-other-plane", p, new MapView(0, 0, W, H, 3222, 3338, 1.75), 1, Progress.NONE,
			"Player on plane 1: the whole (plane-0) route is still drawn, a little fainter");
		WorldMapPath up = WorldMapPaths.of(upstairsRoute());
		MapView close = new MapView(0, 0, W, H, 3212, 3214, 12);
		shot(dir, "wm-7-upstairs-from-0", up, close, 0, new Progress(3, 0, 6),
			"Zoom 12. Player on plane 0 (walk, current): stairs up, plane-1 walk over it fainter, ladder to 2");
		shot(dir, "wm-8-upstairs-on-1", up, close, 1, new Progress(3, 2, 4),
			"Same route, player on plane 1 (current walk): plane 0 (done) and plane 2 fainter");
		realSailing(dir);
		System.out.println("World map pictures in " + dir.getAbsolutePath());
	}

	/**
	 * A real sailing route (courses drawn as straight legs with arrow heads): the plugin's engine on
	 * the exported map data, the Maxed player at Lumbridge with the preview's test sloop at Port Sarim, to the first of
	 * a few island docks the route sails to. The ocean is drawn from the sea data (blue), not from map pictures; the
	 * close-up also shows the course's tiles faintly (the staircase the old drawing showed).
	 */
	private static void realSailing(File dir) throws IOException
	{
		com.bestpath.route.RouteEngine e = RealData.engine();
		if (!(e instanceof com.bestpath.route.CoreRouteEngine))
		{
			System.out.println("No exported map data: no real sailing picture (gradlew -p tool exportPlugin)");
			return;
		}
		com.bestpath.core.data.PluginData data = ((com.bestpath.route.CoreRouteEngine) e).getRouter().getData();
		if (data.getSea() == null)
		{
			return;
		}
		com.bestpath.core.sea.SeaGrid sea = data.getSea().getGrid();
		com.bestpath.route.PlayerSnapshot me = new com.bestpath.route.PlayerSnapshot(3222, 3218, 0,
			com.bestpath.core.req.PlayerStateBuilder.maxed(), RealData.TEST_BOATS);
		for (int id : new int[]{15, 36, 41, 44, 46, 52, 1})
		{
			for (com.bestpath.core.sea.Dock d : data.getSea().getDocks())
			{
				if (d.getId() != id)
				{
					continue;
				}
				int[] to = d.getLandTiles().get(0);
				RouteResult r = e.route(new com.bestpath.route.RouteJob(1, new RouteRequest(to[0], to[1], to[2]), me,
					com.bestpath.route.TransportSettings.defaults(), java.util.Set.of()));
				int sail = -1;
				for (int i = 0; i < r.getSteps().size(); i++)
				{
					sail = r.getSteps().get(i).getKind() == RouteStep.Kind.SAIL ? i : sail;
				}
				if (sail < 0)
				{
					continue;
				}
				RouteStep s = r.getSteps().get(sail);
				int x0 = Integer.MAX_VALUE;
				int y0 = Integer.MAX_VALUE;
				int x1 = Integer.MIN_VALUE;
				int y1 = Integer.MIN_VALUE;
				for (int i = 0; i < s.tileCount(); i++)
				{
					x0 = Math.min(x0, Tiles.x(s.tile(i)));
					y0 = Math.min(y0, Tiles.y(s.tile(i)));
					x1 = Math.max(x1, Tiles.x(s.tile(i)));
					y1 = Math.max(y1, Tiles.y(s.tile(i)));
				}
				double zoom = Math.min((W - 40.0) / (x1 - x0 + 1), (H - 40.0) / (y1 - y0 + 1));
				WorldMapPath p = WorldMapPaths.of(r);
				shot(dir, "wm-9-real-sailing", p, new MapView(0, 0, W, H, (x0 + x1) / 2, (y0 + y1) / 2, zoom), 0,
					Progress.NONE, d.getName() + ": " + s.getTitle() + ", " + s.getTicks() + " ticks, " + (s.cornerCount() - 1)
						+ " straight legs (zoom " + String.format(java.util.Locale.ROOT, "%.2f", zoom) + ")", sea, null);
				// close up on the start of the course, the player on it a little way along
				int c = s.tile(Math.min(s.tileCount() - 1, 30));
				shot(dir, "wm-10-real-sailing-close", p, new MapView(0, 0, W, H, Tiles.x(c), Tiles.y(c), 8), 0,
					new Progress(1, sail, 10), "Close-up (zoom 8): tiles faintly, legs as arrows, heads at the turns; player 10 tiles in",
					sea, s);
				realNne(dir, e, me, data, sea);
				return;
			}
		}
		System.out.println("No real route sailed: no real sailing picture");
	}

	/**
	 * A close-up of a real course's north-north-east leg (the longest found from
	 * Lumbridge to the island docks), with the old 1:2 tile ratio (26.6 degrees) drawn thin from the same start for
	 * comparison.
	 */
	private static void realNne(File dir, com.bestpath.route.RouteEngine e, com.bestpath.route.PlayerSnapshot me,
		com.bestpath.core.data.PluginData data, com.bestpath.core.sea.SeaGrid sea) throws IOException
	{
		RouteResult best = null;
		int bestStep = -1;
		int bestLeg = -1;
		double bestLen = 0;
		for (com.bestpath.core.sea.Dock d : data.getSea().getDocks())
		{
			int[] to = d.getLandTiles().get(0);
			RouteResult r = e.route(new com.bestpath.route.RouteJob(1, new RouteRequest(to[0], to[1], to[2]), me,
				com.bestpath.route.TransportSettings.defaults(), java.util.Set.of()));
			for (int i = 0; i < r.getSteps().size(); i++)
			{
				RouteStep s = r.getSteps().get(i);
				for (int c = 0; c + 1 < s.cornerCount(); c++)
				{
					double dx = s.cornerX(c + 1) - s.cornerX(c);
					double dy = s.cornerY(c + 1) - s.cornerY(c);
					double compass = Math.toDegrees(Math.atan2(dx, dy));
					if (Math.abs(compass - 22.5) < 1e-6 && Math.hypot(dx, dy) > bestLen)
					{
						best = r;
						bestStep = i;
						bestLeg = c;
						bestLen = Math.hypot(dx, dy);
					}
				}
			}
			if (bestLen > 40)
			{
				break;
			}
		}
		if (best == null)
		{
			System.out.println("No north-north-east leg on the routes tried: no NNE picture");
			return;
		}
		RouteStep s = best.getSteps().get(bestStep);
		double x0 = s.cornerX(bestLeg);
		double y0 = s.cornerY(bestLeg);
		double x1 = s.cornerX(bestLeg + 1);
		double y1 = s.cornerY(bestLeg + 1);
		double zoom = Math.min(14, (H - 60.0) / Math.max(8, y1 - y0));
		MapView v = new MapView(0, 0, W, H, (int) Math.round((x0 + x1) / 2), (int) Math.round((y0 + y1) / 2), zoom);
		WorldMapPath p = WorldMapPaths.of(best);
		double len = bestLen;
		shot(dir, "wm-11-real-nne-close", p, v, 0, Progress.NONE, String.format(java.util.Locale.ROOT,
			"%s: north-north-east leg %.1f tiles at exactly 22.5\u00b0 (thin white: the old 1:2 tiles, 26.6\u00b0)",
			s.getTitle(), len), sea, s, g ->
			{
				g.setColor(new Color(255, 255, 255, 170));
				g.setStroke(new java.awt.BasicStroke(1.5f));
				double ox = len * Math.sin(Math.atan2(1, 2));
				double oy = len * Math.cos(Math.atan2(1, 2));
				g.draw(new java.awt.geom.Line2D.Double(v.screenX(x0), v.screenY(y0), v.screenX(x0 + ox), v.screenY(y0 + oy)));
			});
	}

	/**
	 * DEV picture of the scene overlay's clipping (the lines used to disappear when the camera looked
	 * away from where they start), drawn top-down without the game: the loaded scene (104 x 104 tiles, grey), a course
	 * whose first leg starts far west of it (thin: the parts outside the scene), the samples the overlay keeps (dots),
	 * a pretend camera that sees only the box on the right (the leg's start is off screen), and what the overlay draws:
	 * the plugin's own {@link com.bestpath.overlay.CourseScene#draw} lines between neighbouring samples on screen and
	 * one arrow head per leg, on its end (the turn).
	 */
	private static void sceneClip(File dir) throws IOException
	{
		int baseX = 3000;
		int baseY = 3000;
		int size = 104;
		RouteStep s = exactCourse(2930, 3010, new int[][]{{1, 130}, {2, 25}, {4, 20}});
		com.bestpath.overlay.CourseScene cs = com.bestpath.overlay.CourseScene.of(s, baseX, baseY, size, size);
		double scale = 3.2;
		int ox = 240;
		int oy = 40;
		java.util.function.DoubleUnaryOperator sx = x -> ox + (x - baseX) * scale;
		java.util.function.DoubleUnaryOperator sy = y -> oy + (baseY + size - y) * scale;
		BufferedImage img = new BufferedImage(W, H + 24, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(28, 50, 92));
		g.fillRect(0, 0, W, H);
		g.setColor(new Color(60, 70, 90));
		g.fill(new java.awt.geom.Rectangle2D.Double(sx.applyAsDouble(baseX), sy.applyAsDouble(baseY + size), size * scale,
			size * scale));
		// the camera's view: only the east part of the scene
		double camX = baseX + 30;
		g.setColor(new Color(90, 110, 140));
		g.fill(new java.awt.geom.Rectangle2D.Double(sx.applyAsDouble(camX), sy.applyAsDouble(baseY + size),
			(baseX + size - camX) * scale, size * scale));
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString("loaded scene", (int) sx.applyAsDouble(baseX) + 4, (int) sy.applyAsDouble(baseY + size) + 14);
		g.drawString("on screen", (int) sx.applyAsDouble(camX) + 4, (int) sy.applyAsDouble(baseY + size) + 14);
		// the whole course thin
		g.setColor(new Color(255, 255, 255, 110));
		for (int c = 0; c + 1 < s.cornerCount(); c++)
		{
			g.draw(new java.awt.geom.Line2D.Double(sx.applyAsDouble(s.cornerX(c)), sy.applyAsDouble(s.cornerY(c)),
				sx.applyAsDouble(s.cornerX(c + 1)), sy.applyAsDouble(s.cornerY(c + 1))));
		}
		// the samples kept in the scene
		g.setColor(new Color(255, 230, 0, 160));
		for (int i = 0; i < cs.sampleCount(); i++)
		{
			double wx = baseX + (cs.localX(i) - 64) / 128.0;
			double wy = baseY + (cs.localY(i) - 64) / 128.0;
			g.fill(new java.awt.geom.Ellipse2D.Double(sx.applyAsDouble(wx) - 1.5, sy.applyAsDouble(wy) - 1.5, 3, 3));
		}
		// what the overlay draws through the pretend camera
		com.bestpath.overlay.CourseScene.Frame f = new com.bestpath.overlay.CourseScene.Frame();
		f.fit(cs);
		java.awt.geom.Path2D.Float line = new java.awt.geom.Path2D.Float();
		java.awt.geom.Path2D.Float heads = new java.awt.geom.Path2D.Float();
		int n = cs.draw(0, (i, out) ->
		{
			double wx = baseX + (cs.localX(i) - 64) / 128.0;
			double wy = baseY + (cs.localY(i) - 64) / 128.0;
			if (wx < camX)
			{
				return false;
			}
			out[0] = (int) Math.round(sx.applyAsDouble(wx));
			out[1] = (int) Math.round(sy.applyAsDouble(wy));
			return true;
		}, f, line, heads, 14, 7);
		g.setStroke(new java.awt.BasicStroke(5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 0, 0, 140));
		g.draw(line);
		g.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 200, 255));
		g.draw(line);
		g.fill(heads);
		g.setColor(Color.BLACK);
		g.fillRect(0, H, W, 24);
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		g.drawString("Scene clipping (dev): leg 1 starts 70 tiles west, off screen; drawn from where it shows; "
			+ n + " heads for 3 legs, on the turns", 6, H + 17);
		g.dispose();
		ImageIO.write(img, "png", new File(dir, "dev-scene-clip.png"));
	}

	/** Walk to Port Sarim, boat to Karamja, a short shortcut, then a sailing leg along the coast. */
	private static RouteResult rideRoute()
	{
		List<RouteStep> steps = new ArrayList<>();
		steps.add(walk(3045, 3235, 3029, 3217));
		steps.add(new RouteStep(RouteStep.Kind.BOAT, "Ship to Karamja", "", 10, List.of(), "t-ship", new int[0],
			Tiles.pack(3029, 3217, 0), Tiles.pack(2956, 3146, 0)));
		steps.add(walk(2956, 3146, 2940, 3140));
		steps.add(new RouteStep(RouteStep.Kind.TRAVERSAL, "Jump across", "", 4, List.of(), "t-jump", new int[0],
			Tiles.pack(2940, 3140, 0), Tiles.pack(2933, 3133, 0)));
		// a boat's course: straight legs in its exact headings (west, west-north-west, north-west, west; core SeaGraph.EX),
		// corner to corner (corners need not be whole tiles), the tiles sampled along them
		RouteStep course = exactCourse(2933, 3133, new int[][]{{8, 24}, {7, 11}, {6, 12}, {8, 30}});
		steps.add(course);
		int end = course.tile(course.tileCount() - 1);
		return RouteResults.ready(2, new RouteRequest(Tiles.x(end), Tiles.y(end), 0), steps);
	}

	/** A sail step from (x, y): legs {heading (core SeaGraph numbering), tiles}, sampled like core's courses. */
	private static RouteStep exactCourse(double x, double y, int[][] legs)
	{
		List<Integer> sea = new ArrayList<>();
		int[] corners = new int[legs.length + 1];
		double[] points = new double[2 * (legs.length + 1)];
		points[0] = x;
		points[1] = y;
		sea.add(Tiles.pack((int) Math.floor(x + 0.5), (int) Math.floor(y + 0.5), 0));
		for (int l = 0; l < legs.length; l++)
		{
			double ex = x + legs[l][1] * com.bestpath.core.sea.SeaGraph.EX[legs[l][0]];
			double ey = y + legs[l][1] * com.bestpath.core.sea.SeaGraph.EY[legs[l][0]];
			int m = (int) Math.ceil(Math.max(Math.abs(ex - x), Math.abs(ey - y)));
			for (int i = 1; i <= m; i++)
			{
				int t = Tiles.pack((int) Math.floor(x + (ex - x) * i / m + 0.5), (int) Math.floor(y + (ey - y) * i / m + 0.5), 0);
				if (t != sea.get(sea.size() - 1))
				{
					sea.add(t);
				}
			}
			x = ex;
			y = ey;
			corners[l + 1] = sea.size() - 1;
			points[2 * l + 2] = x;
			points[2 * l + 3] = y;
		}
		return new RouteStep(RouteStep.Kind.SAIL, "Sail", "", 40, List.of(), null,
			sea.stream().mapToInt(Integer::intValue).toArray(), -1).withCourse(corners, points);
	}

	/**
	 * Inside a building: walk on plane 0, stairs up (landing 3 tiles away), a plane-1 walk back over the plane-0
	 * one, a ladder up to plane 2 (same x / y), a short plane-2 walk to the destination.
	 */
	private static RouteResult upstairsRoute()
	{
		List<RouteStep> steps = new ArrayList<>();
		steps.add(walk(3204, 3210, 3218, 3216, 0));
		steps.add(new RouteStep(RouteStep.Kind.TRAVERSAL, "Climb-up Staircase", "", 3, List.of(), "t-stairs",
			new int[0], Tiles.pack(3218, 3216, 0), Tiles.pack(3218, 3219, 1)));
		steps.add(walk(3218, 3219, 3206, 3213, 1));
		steps.add(new RouteStep(RouteStep.Kind.TRAVERSAL, "Climb-up Ladder", "", 3, List.of(), "t-ladder",
			new int[0], Tiles.pack(3206, 3213, 1), Tiles.pack(3206, 3213, 2)));
		steps.add(walk(3206, 3213, 3210, 3209, 2));
		return RouteResults.ready(3, new RouteRequest(3210, 3209, 2), steps);
	}

	private static RouteStep walk(int x0, int y0, int x1, int y1)
	{
		return walk(x0, y0, x1, y1, 0);
	}

	private static RouteStep walk(int x0, int y0, int x1, int y1, int plane)
	{
		List<Integer> t = new ArrayList<>();
		int x = x0;
		int y = y0;
		t.add(Tiles.pack(x, y, plane));
		while (x != x1 || y != y1)
		{
			x += Integer.signum(x1 - x);
			y += Integer.signum(y1 - y);
			t.add(Tiles.pack(x, y, plane));
		}
		int[] a = t.stream().mapToInt(Integer::intValue).toArray();
		return new RouteStep(RouteStep.Kind.WALK, "Walk", "", a.length - 1, List.of(), null, a, -1);
	}

	private static void shot(File dir, String name, WorldMapPath p, MapView v, int plane, Progress progress,
		String caption) throws IOException
	{
		shot(dir, name, p, v, plane, progress, caption, null, null);
	}

	private static void shot(File dir, String name, WorldMapPath p, MapView v, int plane, Progress progress,
		String caption, com.bestpath.core.sea.SeaGrid sea, RouteStep tilesOf) throws IOException
	{
		shot(dir, name, p, v, plane, progress, caption, sea, tilesOf, null);
	}

	/**
	 * As above; {@code sea}: ocean tiles drawn blue; {@code tilesOf}: a step whose tiles are drawn faintly; {@code extra}:
	 * drawn after the route (may be null).
	 */
	private static void shot(File dir, String name, WorldMapPath p, MapView v, int plane, Progress progress,
		String caption, com.bestpath.core.sea.SeaGrid sea, RouteStep tilesOf,
		java.util.function.Consumer<Graphics2D> extra) throws IOException
	{
		BufferedImage img = new BufferedImage(W, H + 24, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(30, 52, 38));
		g.fillRect(0, 0, W, H);
		if (sea != null)
		{
			g.setColor(new Color(28, 50, 92));
			for (int tx = v.minTileX(); tx <= v.maxTileX(); tx++)
			{
				for (int ty = v.minTileY(); ty <= v.maxTileY(); ty++)
				{
					if (sea.isOcean(tx, ty))
					{
						int sx = (int) Math.floor(v.screenX(tx) - v.getZoom() / 2);
						int sy = (int) Math.floor(v.screenY(ty) - v.getZoom() / 2);
						g.fillRect(sx, sy, (int) Math.ceil(v.getZoom()) + 1, (int) Math.ceil(v.getZoom()) + 1);
					}
				}
			}
		}
		if (tilesOf != null)
		{
			g.setColor(new Color(255, 255, 255, 60));
			for (int i = 0; i < tilesOf.tileCount(); i++)
			{
				int t = tilesOf.tile(i);
				int sx = (int) Math.floor(v.screenX(Tiles.x(t)) - v.getZoom() / 2);
				int sy = (int) Math.floor(v.screenY(Tiles.y(t)) - v.getZoom() / 2);
				g.drawRect(sx, sy, (int) Math.round(v.getZoom()) - 1, (int) Math.round(v.getZoom()) - 1);
			}
		}
		g.setColor(new Color(48, 74, 56));
		for (int tx = v.minTileX(); tx <= v.maxTileX(); tx++)
		{
			if (Math.floorMod(tx, 8) == 0)
			{
				int sx = (int) Math.round(v.screenX(tx) - v.getZoom() / 2);
				g.drawLine(sx, 0, sx, H);
			}
		}
		for (int ty = v.minTileY(); ty <= v.maxTileY(); ty++)
		{
			if (Math.floorMod(ty, 8) == 0)
			{
				int sy = (int) Math.round(v.screenY(ty) + v.getZoom() / 2);
				g.drawLine(0, sy, W, sy);
			}
		}
		g.setClip(0, 0, W, H);
		new WorldMapPainter().paint(g, p, v, plane, progress, WorldMapPainter.EVERYWHERE);
		if (extra != null)
		{
			extra.accept(g);
		}
		g.setClip(null);
		g.setColor(Color.BLACK);
		g.fillRect(0, H, W, 24);
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		g.drawString(caption, 6, H + 17);
		g.dispose();
		ImageIO.write(img, "png", new File(dir, name + ".png"));
	}
}
