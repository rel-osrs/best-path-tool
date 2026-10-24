package com.bestpath.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteStep;
import com.bestpath.route.RouteSteps;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** A boat's course drawn as arrows: the heads' geometry, one per leg where it shows, the player's place on it. */
public class CourseArrowsTest
{
	/** The points of the path's first sub-path. */
	private static List<double[]> points(Path2D p)
	{
		List<double[]> out = new ArrayList<>();
		double[] c = new double[6];
		for (PathIterator it = p.getPathIterator(null); !it.isDone(); it.next())
		{
			int t = it.currentSegment(c);
			if (t == PathIterator.SEG_MOVETO || t == PathIterator.SEG_LINETO)
			{
				out.add(new double[]{c[0], c[1]});
			}
		}
		return out;
	}

	@Test
	public void aHeadPointsTheWayOfTheLeg()
	{
		// a leg going right on screen: the tip at its end, the base 10 behind, 8 wide
		Path2D.Float p = new Path2D.Float();
		CourseArrows.head(p, 0, 0, 100, 0, 10, 4);
		List<double[]> pts = points(p);
		assertEquals(3, pts.size());
		assertArrayEquals(new double[]{100, 0}, pts.get(0), 1e-4);
		assertEquals(90, pts.get(1)[0], 1e-4);
		assertEquals(90, pts.get(2)[0], 1e-4);
		assertEquals(8, Math.abs(pts.get(1)[1] - pts.get(2)[1]), 1e-4);
		// a 2:1 leg (east-north-east; screen y grows down): the base lies back along the leg
		Path2D.Float q = new Path2D.Float();
		CourseArrows.head(q, 0, 0, 20, -10, 10, 4);
		List<double[]> qs = points(q);
		double bx = (qs.get(1)[0] + qs.get(2)[0]) / 2;
		double by = (qs.get(1)[1] + qs.get(2)[1]) / 2;
		assertEquals(-10.0 / 20, (by - (-10)) / (bx - 20), 1e-4); // same slope as the leg
		assertTrue(bx < 20);
		// no direction: nothing drawn
		Path2D.Float none = new Path2D.Float();
		CourseArrows.head(none, 5, 5, 5, 5, 10, 4);
		assertEquals(0, points(none).size());
	}

	@Test
	public void oneHeadPerLegAtItsEnd()
	{
		// one arrowhead per leg, at its end (the turn); a leg from x 0 to 200 on a screen 0..400: the tip on its end (the turn), the base a head's length back
		double[] buf = new double[6];
		Path2D.Float p = new Path2D.Float();
		assertTrue(CourseArrows.endHead(p, 0, 50, 200, 50, 0, 0, 400, 300, 10, 4, buf));
		List<double[]> pts = points(p);
		assertEquals(3, pts.size());
		assertArrayEquals(new double[]{200, 50}, pts.get(0), 1e-4);
		assertEquals(190, pts.get(1)[0], 1e-4);
		assertEquals(190, pts.get(2)[0], 1e-4);
		// the same leg starting off screen (x -200 .. 200): still its end
		Path2D.Float q = new Path2D.Float();
		assertTrue(CourseArrows.endHead(q, -200, 50, 200, 50, 0, 0, 400, 300, 10, 4, buf));
		assertArrayEquals(new double[]{200, 50}, points(q).get(0), 1e-4);
		// the end off screen (west of it, or past the east / south edge although most of the leg shows): no head (the
		// next turn that shows has one); a leg shorter than the head: none
		assertFalse(CourseArrows.endHead(q, -200, 50, -10, 50, 0, 0, 400, 300, 10, 4, buf));
		assertFalse(CourseArrows.endHead(q, 0, 50, 401, 50, 0, 0, 400, 300, 10, 4, buf));
		assertFalse(CourseArrows.endHead(q, 0, 50, 200, 301, 0, 0, 400, 300, 10, 4, buf));
		assertFalse(CourseArrows.endHead(q, 195, 50, 200, 50, 0, 0, 400, 300, 10, 4, buf));
		assertEquals(3, points(q).size());
		// on the edge counts as showing
		assertTrue(CourseArrows.endHead(q, 0, 50, 400, 50, 0, 0, 400, 300, 10, 4, buf));
	}

	@Test
	public void thePlayersPlaceOnTheExactCourse()
	{
		// two legs: 10 tiles exactly north-north-east from 3000,3000 (corner not on a whole tile), then 6 north
		double ex = 10 * Math.sin(Math.toRadians(22.5));
		double ey = 10 * Math.cos(Math.toRadians(22.5));
		int[] tiles = new int[17];
		for (int i = 0; i <= 10; i++)
		{
			tiles[i] = Tiles.pack((int) Math.round(3000 + ex * i / 10), (int) Math.round(3000 + ey * i / 10), 0);
		}
		for (int i = 1; i <= 6; i++)
		{
			tiles[10 + i] = Tiles.pack((int) Math.round(3000 + ex), (int) Math.round(3000 + ey + i), 0);
		}
		RouteStep s = new RouteStep(RouteStep.Kind.SAIL, "Sail", "", 10, List.of(), null, tiles, -1)
			.withCourse(new int[]{0, 10, 16}, new double[]{3000, 3000, 3000 + ex, 3000 + ey, 3000 + ex, 3000 + ey + 6});
		assertEquals(3000 + ex, s.cornerX(1), 0);
		double[] at = new double[2];
		CourseArrows.pointAt(s, 5, at);
		assertEquals(3000 + ex / 2, at[0], 1e-9);
		assertEquals(3000 + ey / 2, at[1], 1e-9);
		CourseArrows.pointAt(s, 13, at);
		assertEquals(3000 + ex, at[0], 1e-9);
		assertEquals(3000 + ey + 3, at[1], 1e-9);
		CourseArrows.pointAt(s, 99, at);
		assertEquals(3000 + ey + 6, at[1], 1e-9);
	}

	@Test(expected = IllegalArgumentException.class)
	public void cornersMustBeTileIndexesInOrder()
	{
		int[] tiles = {Tiles.pack(3000, 3000, 0), Tiles.pack(3001, 3000, 0)};
		RouteSteps.withCorners(new RouteStep(RouteStep.Kind.SAIL, "Sail", "", 1, List.of(), null, tiles, -1),
			new int[]{1, 0});
	}
}
