package com.bestpath.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteStep;
import java.util.List;
import org.junit.Test;

/**
 * A course in the loaded scene (its lines stay visible when the camera looks away from where
 * they start): legs cut to the scene and sampled about a tile apart, whatever part of them is in it.
 */
public class CourseSceneTest
{
	private static final int BASE_X = 3000;
	private static final int BASE_Y = 3000;
	private static final int SIZE = 104;

	/** A sail step through these exact corners (x, y pairs), its tiles one per whole tile along x (enough here). */
	private static RouteStep course(double... xy)
	{
		int n = xy.length / 2;
		int[] corners = new int[n];
		int[] tiles = new int[n * 200];
		int k = 0;
		for (int c = 0; c < n; c++)
		{
			if (c > 0)
			{
				double x0 = xy[2 * c - 2];
				double y0 = xy[2 * c - 1];
				int m = (int) Math.ceil(Math.max(Math.abs(xy[2 * c] - x0), Math.abs(xy[2 * c + 1] - y0)));
				for (int i = 1; i <= m; i++)
				{
					tiles[k++] = Tiles.pack((int) Math.round(x0 + (xy[2 * c] - x0) * i / m),
						(int) Math.round(y0 + (xy[2 * c + 1] - y0) * i / m), 0);
				}
			}
			else
			{
				tiles[k++] = Tiles.pack((int) Math.round(xy[0]), (int) Math.round(xy[1]), 0);
			}
			corners[c] = k - 1;
		}
		return new RouteStep(RouteStep.Kind.SAIL, "Sail", "", 10, List.of(), null, java.util.Arrays.copyOf(tiles, k), -1)
			.withCourse(corners, xy);
	}

	@Test
	public void aLegWhoseStartIsOutsideTheSceneIsDrawnFromTheSceneEdge()
	{
		// from 60 tiles west of the scene into it, north-north-east-ish: its samples start at the scene's west edge
		RouteStep s = course(2940.3, 3010, 3060, 3070.7);
		CourseScene cs = CourseScene.of(s, BASE_X, BASE_Y, SIZE, SIZE);
		assertEquals(1, cs.legCount());
		int from = cs.legFrom(0);
		int to = cs.legTo(0);
		assertTrue(to - from > 60);
		assertEquals(64, cs.localX(from)); // the middle of the scene's first column (local x 64)
		assertEquals((3060 - BASE_X) * 128 + 64, cs.localX(to - 1));
		for (int i = from; i < to; i++)
		{
			// inside the scene, on the exact line, at most a tile apart
			assertTrue(cs.localX(i) >= 64 && cs.localX(i) <= (SIZE - 1) * 128 + 64);
			assertTrue(cs.localY(i) >= 64 && cs.localY(i) <= (SIZE - 1) * 128 + 64);
			double wx = BASE_X + (cs.localX(i) - 64) / 128.0;
			double wy = BASE_Y + (cs.localY(i) - 64) / 128.0;
			double off = Math.abs((wx - 2940.3) * (3070.7 - 3010) - (wy - 3010) * (3060 - 2940.3))
				/ Math.hypot(3060 - 2940.3, 3070.7 - 3010);
			assertTrue("off the line by " + off, off < 0.01);
			if (i > from)
			{
				assertTrue(Math.abs(cs.localX(i) - cs.localX(i - 1)) <= 128 && Math.abs(cs.localY(i) - cs.localY(i - 1)) <= 128);
				assertTrue(cs.at[i] > cs.at[i - 1]);
			}
		}
		// the part behind the scene edge is a little way along the step's tiles already
		assertTrue(cs.at[from] > 40);
		assertEquals(s.corner(1), cs.at[to - 1], 1e-3);
	}

	@Test
	public void legsOutsideOrAcrossTheScene()
	{
		// leg 0 wholly west of the scene, leg 1 right across it (both ends outside), leg 2 ends inside
		RouteStep s = course(2900, 3050, 2950, 3050, 3150, 3050, 3050, 3050);
		CourseScene cs = CourseScene.of(s, BASE_X, BASE_Y, SIZE, SIZE);
		assertEquals(3, cs.legCount());
		assertEquals(cs.legFrom(0), cs.legTo(0));
		int a = cs.legFrom(1);
		int b = cs.legTo(1) - 1;
		assertTrue(b > a);
		// enters at the west edge, leaves at the east edge
		assertEquals(64, cs.localX(a));
		assertEquals((SIZE - 1) * 128 + 64, cs.localX(b));
		assertTrue(cs.legTo(2) - cs.legFrom(2) > 40);
		assertEquals(cs.sampleCount(), cs.legTo(2));
		// only a leg whose end (the turn) lies in the scene can have an arrow head
		assertFalse(cs.endIn[0]);
		assertFalse(cs.endIn[1]);
		assertTrue(cs.endIn[2]);
		// a course nowhere near the scene: no samples at all
		assertEquals(0, CourseScene.of(course(2000, 2000, 2050, 2000), BASE_X, BASE_Y, SIZE, SIZE).sampleCount());
	}

	@Test
	public void everyPartOnTheScreenIsDrawnWithOneHeadPerLeg()
	{
		// leg 0 starts 60 tiles west of the scene, leg 1 goes north; the "camera" sees only local x >= 30 tiles (the
		// start of leg 0 is off screen): the part of leg 0 that shows is still drawn, and each leg gets one head
		RouteStep s = course(2940.3, 3010, 3060, 3070.7, 3060, 3090);
		CourseScene cs = CourseScene.of(s, BASE_X, BASE_Y, SIZE, SIZE);
		CourseScene.Frame f = new CourseScene.Frame();
		f.fit(cs);
		CourseScene.SampleProjector camera = (i, out) ->
		{
			if (cs.localX(i) < 30 * 128)
			{
				return false;
			}
			out[0] = cs.localX(i) / 16;
			out[1] = 2000 - cs.localY(i) / 16;
			return true;
		};
		java.awt.geom.Path2D.Float line = new java.awt.geom.Path2D.Float();
		java.awt.geom.Path2D.Float heads = new java.awt.geom.Path2D.Float();
		assertEquals(2, cs.draw(0, camera, f, line, heads, 10, 4));
		// one straight line per leg: from its first sample on screen to its last (the part of leg 0 that shows)
		assertEquals(2, segments(line));
		double[] c = new double[6];
		java.awt.geom.PathIterator it = line.getPathIterator(null);
		it.currentSegment(c);
		assertEquals(30 * 128 / 16, c[0], 8.0 / 16 + 128.0 / 16); // leg 0 drawn from where the screen begins
		// the heads' tips are on the legs' ends: the turn at 3060,3070.7 and the course's end at 3060,3090
		List<double[]> tips = tips(heads);
		assertEquals(2, tips.size());
		assertEquals(((3060 - BASE_X) * 128 + 64) / 16, tips.get(0)[0], 1);
		assertEquals(2000 - Math.round((3070.7 - BASE_Y) * 128 + 64) / 16, tips.get(0)[1], 1);
		assertEquals(2000 - ((3090 - BASE_Y) * 128 + 64) / 16, tips.get(1)[1], 1);
		// the player on leg 1: leg 0 is behind him, only leg 1 is drawn
		line.reset();
		heads.reset();
		assertEquals(1, cs.draw(s.corner(1) + 1, camera, f, line, heads, 10, 4));
		// a camera that sees nothing: no line, no heads
		line.reset();
		assertEquals(0, cs.draw(0, (i, out) -> false, f, line, heads, 10, 4));
		assertEquals(0, segments(line));
	}

	private static int segments(java.awt.geom.Path2D p)
	{
		int n = 0;
		double[] c = new double[6];
		for (java.awt.geom.PathIterator it = p.getPathIterator(null); !it.isDone(); it.next())
		{
			n += it.currentSegment(c) == java.awt.geom.PathIterator.SEG_LINETO ? 1 : 0;
		}
		return n;
	}

	/** The tip (first point) of each arrow head in the path. */
	private static List<double[]> tips(java.awt.geom.Path2D p)
	{
		List<double[]> out = new java.util.ArrayList<>();
		double[] c = new double[6];
		for (java.awt.geom.PathIterator it = p.getPathIterator(null); !it.isDone(); it.next())
		{
			if (it.currentSegment(c) == java.awt.geom.PathIterator.SEG_MOVETO)
			{
				out.add(new double[]{c[0], c[1]});
			}
		}
		return out;
	}

	@Test
	public void theHeadSitsOnTheTurnAndOnlyWhenTheTurnShows()
	{
		// the arrow head is at the turn, only at turns
		RouteStep s = course(3010, 3010, 3050, 3050, 3050, 3090);
		CourseScene cs = CourseScene.of(s, BASE_X, BASE_Y, SIZE, SIZE);
		CourseScene.Frame f = new CourseScene.Frame();
		f.fit(cs);
		java.awt.geom.Path2D.Float line = new java.awt.geom.Path2D.Float();
		java.awt.geom.Path2D.Float heads = new java.awt.geom.Path2D.Float();
		int[] projected = new int[1];
		// a camera that shows only the south part of the scene (local y below 40 tiles): leg 0's turn at y 50 is off
		// screen, so leg 0 is drawn without a head, and leg 1 (wholly off screen) has neither line nor head
		CourseScene.SampleProjector south = (i, out) ->
		{
			projected[0]++;
			out[0] = cs.localX(i) / 16;
			out[1] = 2000 - cs.localY(i) / 16;
			return cs.localY(i) < 40 * 128;
		};
		assertEquals(0, cs.draw(0, south, f, line, heads, 10, 4));
		assertEquals(1, segments(line));
		// a camera that sees everything: one head per leg, on the turn and on the course's end
		CourseScene.SampleProjector all = (i, out) ->
		{
			projected[0]++;
			out[0] = cs.localX(i) / 16;
			out[1] = 2000 - cs.localY(i) / 16;
			return true;
		};
		line.reset();
		assertEquals(2, cs.draw(0, all, f, line, heads, 10, 4));
		List<double[]> tips = tips(heads);
		assertEquals((50 * 128 + 64) / 16, tips.get(0)[0], 1e-6);
		assertEquals(2000 - (50 * 128 + 64) / 16, tips.get(0)[1], 1e-6);
		assertEquals(2000 - (90 * 128 + 64) / 16, tips.get(1)[1], 1e-6);
		// the boat a tile before the turn: its leg still has its head on the turn (the leg behind the boat gives the
		// head's direction), and only a few samples behind it are looked at
		line.reset();
		heads.reset();
		projected[0] = 0;
		assertEquals(2, cs.draw(s.corner(1) - 1, all, f, line, heads, 10, 4));
		assertEquals((50 * 128 + 64) / 16, tips(heads).get(0)[0], 1e-6);
		assertTrue("projected " + projected[0], projected[0] < (cs.legTo(1) - cs.legFrom(1)) + 6);
		// past the turn: leg 0 is behind, no head for it
		heads.reset();
		assertEquals(1, cs.draw(s.corner(1) + 1, all, f, line, heads, 10, 4));
		// a leg shorter than the head gets none
		RouteStep tiny = course(3010, 3010, 3011, 3010, 3011, 3030);
		CourseScene ts = CourseScene.of(tiny, BASE_X, BASE_Y, SIZE, SIZE);
		f.fit(ts);
		heads.reset();
		assertEquals(1, ts.draw(0, (i, out) ->
		{
			out[0] = ts.localX(i) / 16;
			out[1] = 2000 - ts.localY(i) / 16;
			return true;
		}, f, line, heads, 10, 4));
	}
}
