package com.bestpath.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;

/** Paints onto a picture and looks at single pixels: which colour ended up where. */
public class WorldMapPainterTest
{
	private static final MapView VIEW = new MapView(0, 0, 400, 300, 3210, 3210, 4);
	private static final Color BACKGROUND = new Color(40, 90, 40); // a green-ish map

	private static RouteStep walk(int x0, int x1, int y)
	{
		int[] t = new int[x1 - x0 + 1];
		for (int i = 0; i < t.length; i++)
		{
			t[i] = Tiles.pack(x0 + i, y, 0);
		}
		return new RouteStep(RouteStep.Kind.WALK, "Walk", "", t.length - 1, List.of(), null, t, -1);
	}

	/** Walk east along y 3200, teleport, walk east along y 3220; destination 3240, 3220. */
	private static WorldMapPath path()
	{
		RouteStep tele = new RouteStep(RouteStep.Kind.TELEPORT, "Teleport", "", 5, List.of(), "t", new int[0], -1,
			Tiles.pack(3205, 3220, 0));
		return WorldMapPaths.of(RouteResults.ready(9, new RouteRequest(3240, 3220, 0),
			List.of(walk(3195, 3220, 3200), tele, walk(3205, 3225, 3220))));
	}

	private static BufferedImage paint(WorldMapPath p, int plane, Progress progress)
	{
		BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(BACKGROUND);
		g.fillRect(0, 0, 400, 300);
		new WorldMapPainter().paint(g, p, VIEW, plane, progress, WorldMapPainter.EVERYWHERE);
		g.dispose();
		return img;
	}

	private enum Seen
	{
		MAP, CYAN, LIGHT, GREY, RED, OTHER
	}

	private static Color pixel(BufferedImage img, int tileX, int tileY)
	{
		return new Color(img.getRGB((int) VIEW.screenX(tileX), (int) VIEW.screenY(tileY)));
	}

	private static Seen at(BufferedImage img, int tileX, int tileY)
	{
		Color c = new Color(img.getRGB((int) VIEW.screenX(tileX), (int) VIEW.screenY(tileY)));
		int r = c.getRed();
		int g = c.getGreen();
		int b = c.getBlue();
		if (c.equals(BACKGROUND))
		{
			return Seen.MAP;
		}
		if (r < 60 && g > 150 && b > 180)
		{
			return Seen.CYAN;
		}
		if (r >= 80 && r <= 140 && g > 200 && b > 230) // the current step: the route colour, lighter (blue everywhere)
		{
			return Seen.LIGHT;
		}
		if (r > 200 && g < 100 && b < 100)
		{
			return Seen.RED;
		}
		if (Math.abs(r - g) < 40 && Math.abs(g - b) < 40 && r > 90)
		{
			return Seen.GREY;
		}
		return Seen.OTHER;
	}

	@Test
	public void routeAheadIsCyanAndATeleportDrawsNoLine()
	{
		BufferedImage img = paint(path(), 0, Progress.NONE);
		assertEquals(Seen.CYAN, at(img, 3210, 3200));
		assertEquals(Seen.CYAN, at(img, 3215, 3220));
		// between the teleport's start and its landing: just the map
		assertEquals(Seen.MAP, at(img, 3205, 3210));
		assertEquals(Seen.MAP, at(img, 3220, 3210));
		assertEquals(Seen.RED, at(img, 3240, 3220));
	}

	@Test
	public void stepsDoneAreGreyAndTheCurrentOneLighter()
	{
		WorldMapPath p = path();
		BufferedImage img = paint(p, 0, new Progress(p.getGeneration(), 2, 0));
		assertEquals(Seen.GREY, at(img, 3210, 3200));
		assertEquals(Seen.LIGHT, at(img, 3215, 3220));
	}

	@Test
	public void theCurrentWalkSplitsAtThePlayer()
	{
		WorldMapPath p = path();
		BufferedImage img = paint(p, 0, new Progress(p.getGeneration(), 0, 15)); // at 3210, 3200
		assertEquals(Seen.GREY, at(img, 3200, 3200));
		assertEquals(Seen.LIGHT, at(img, 3216, 3200));
		assertEquals(Seen.CYAN, at(img, 3215, 3220)); // the later walk
	}

	@Test
	public void progressOfAnotherRouteIsIgnored()
	{
		BufferedImage img = paint(path(), 0, new Progress(1234, 2, 0));
		assertEquals(Seen.CYAN, at(img, 3210, 3200));
	}

	@Test
	public void otherPlanesAreDrawnFainterNeverHidden()
	{
		// the player is on plane 1, the whole route on plane 0: still drawn, a little fainter
		BufferedImage full = paint(path(), 0, Progress.NONE);
		BufferedImage other = paint(path(), 1, Progress.NONE);
		for (int[] t : new int[][]{{3210, 3200}, {3215, 3220}})
		{
			Color f = pixel(full, t[0], t[1]);
			Color o = pixel(other, t[0], t[1]);
			assertNotEquals(Seen.MAP, at(other, t[0], t[1]));
			assertNotEquals(f, o);
			// between the map and the full-strength cyan
			assertTrue(o.getBlue() > BACKGROUND.getBlue() && o.getBlue() < f.getBlue());
		}
		// the teleport still draws no line
		assertEquals(Seen.MAP, at(other, 3205, 3210));
	}

	@Test
	public void theDestinationIsAlwaysFullStrength()
	{
		assertEquals(Seen.RED, at(paint(path(), 1, Progress.NONE), 3240, 3220));
		assertEquals(Seen.RED, at(paint(path(), 3, Progress.NONE), 3240, 3220));
	}

	@Test
	public void aRouteUpstairsKeepsItsColoursFainter()
	{
		// a walk on plane 1 under the player on plane 0: the current one is still light-blue-ish, done grey-ish
		int[] t = new int[26];
		for (int i = 0; i < t.length; i++)
		{
			t[i] = Tiles.pack(3195 + i, 3200, 1);
		}
		WorldMapPath p = WorldMapPaths.of(RouteResults.ready(9, new RouteRequest(3220, 3200, 1),
			List.of(new RouteStep(RouteStep.Kind.WALK, "Walk", "", 25, List.of(), null, t, -1))));
		BufferedImage img = paint(p, 0, new Progress(p.getGeneration(), 0, 15)); // at 3210, 3200
		Color done = pixel(img, 3200, 3200);
		Color current = pixel(img, 3216, 3200);
		assertTrue(current.getBlue() > done.getBlue() + 40 && current.getGreen() > done.getGreen()); // light blue vs grey
		assertTrue(current.getBlue() > BACKGROUND.getBlue() + 60); // clearly drawn
	}

	/** A boat's course: east 20 tiles, north-east 15, north 30 (corners on whole tiles here), one tile per step. */
	private static WorldMapPath course()
	{
		List<Integer> t = new java.util.ArrayList<>();
		int x = 3180;
		int y = 3190;
		t.add(Tiles.pack(x, y, 0));
		int[][] legs = {{1, 0, 20}, {1, 1, 15}, {0, 1, 30}};
		int[] corners = new int[4];
		double[] points = new double[8];
		points[0] = x;
		points[1] = y;
		for (int l = 0; l < legs.length; l++)
		{
			for (int i = 0; i < legs[l][2]; i++)
			{
				x += legs[l][0];
				y += legs[l][1];
				t.add(Tiles.pack(x, y, 0));
			}
			corners[l + 1] = t.size() - 1;
			points[2 * l + 2] = x;
			points[2 * l + 3] = y;
		}
		RouteStep sail = new RouteStep(RouteStep.Kind.SAIL, "Sail", "", 40, List.of(), null,
			t.stream().mapToInt(Integer::intValue).toArray(), -1).withCourse(corners, points);
		return WorldMapPaths.of(RouteResults.ready(9, new RouteRequest(x, y, 0), List.of(sail)));
	}

	private static int heads(WorldMapPath p, MapView view, Progress progress)
	{
		BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setClip(0, 0, 400, 300);
		WorldMapPainter painter = new WorldMapPainter();
		painter.paint(g, p, view, 0, progress, WorldMapPainter.EVERYWHERE);
		g.dispose();
		return painter.headsDrawn;
	}

	@Test
	public void oneArrowHeadPerLeg()
	{
		// one arrowhead per leg, at its end (the turn)
		WorldMapPath p = course();
		assertEquals(3, heads(p, VIEW, Progress.NONE));
		// the player half way along the north-east leg: done part grey, the rest yellow; still one head per leg
		assertEquals(3, heads(p, VIEW, new Progress(p.getGeneration(), 0, 27)));
		// the player a tile before the turn (what is left of his leg is shorter than a head): the turn keeps its head
		assertEquals(3, heads(p, VIEW, new Progress(p.getGeneration(), 0, 34)));
		// zoomed in on the north leg only: its end (the course's end) shows, the turn before it doesn't: just its head
		assertEquals(1, heads(p, new MapView(0, 0, 400, 300, 3215, 3225, 12), Progress.NONE));
		// zoomed in on the north-east turn: the north leg's end is off the map (no head), the north-east leg's end shows
		assertEquals(1, heads(p, new MapView(0, 0, 400, 300, 3215, 3210, 12), Progress.NONE));
	}

	@Test
	public void nothingToDrawLeavesTheMapAlone()
	{
		BufferedImage img = paint(WorldMapPath.EMPTY, 0, Progress.NONE);
		for (int x = 0; x < 400; x += 10)
		{
			for (int y = 0; y < 300; y += 10)
			{
				assertEquals(BACKGROUND.getRGB(), img.getRGB(x, y));
			}
		}
	}
}
