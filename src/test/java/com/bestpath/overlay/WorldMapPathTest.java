package com.bestpath.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.RouteStep;
import com.bestpath.route.RouteSteps;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class WorldMapPathTest
{
	private static final RouteRequest TO = new RouteRequest(3300, 3300, 0);

	/** A walk through these tiles (x, y pairs, plane 0), filled in tile by tile between them. */
	private static RouteStep walk(int... xy)
	{
		return along(RouteStep.Kind.WALK, 0, xy);
	}

	private static RouteStep along(RouteStep.Kind kind, int plane, int... xy)
	{
		List<Integer> tiles = new ArrayList<>();
		tiles.add(Tiles.pack(xy[0], xy[1], plane));
		for (int i = 2; i < xy.length; i += 2)
		{
			int x = xy[i - 2];
			int y = xy[i - 1];
			while (x != xy[i] || y != xy[i + 1])
			{
				x += Integer.signum(xy[i] - x);
				y += Integer.signum(xy[i + 1] - y);
				tiles.add(Tiles.pack(x, y, plane));
			}
		}
		int[] t = tiles.stream().mapToInt(Integer::intValue).toArray();
		return new RouteStep(kind, "Walk", "", t.length - 1, List.of(), null, t, -1);
	}

	private static RouteStep transport(RouteStep.Kind kind, int from, int to)
	{
		return new RouteStep(kind, "Use", "", 2, List.of(), "t", new int[0], from, to);
	}

	private static WorldMapPath build(RouteStep... steps)
	{
		return WorldMapPaths.of(RouteResults.ready(9, TO, List.of(steps)));
	}

	private static int[] xs(WorldMapPath.Line l)
	{
		int[] a = new int[l.pointCount()];
		for (int i = 0; i < a.length; i++)
		{
			a[i] = l.x(i);
		}
		return a;
	}

	private static int[] ys(WorldMapPath.Line l)
	{
		int[] a = new int[l.pointCount()];
		for (int i = 0; i < a.length; i++)
		{
			a[i] = l.y(i);
		}
		return a;
	}

	@Test
	public void aStraightWalkIsTwoPoints()
	{
		WorldMapPath p = build(walk(3200, 3200, 3230, 3200));
		assertEquals(9, p.getGeneration());
		assertEquals(1, p.lineCount());
		WorldMapPath.Line l = p.line(0);
		assertEquals(WorldMapPath.Style.WALK, l.getStyle());
		assertArrayEquals(new int[]{3200, 3230}, xs(l));
		assertEquals(0, l.tileIndex(0));
		assertEquals(30, l.tileIndex(1));
		assertEquals(Tiles.pack(3300, 3300, 0), p.getDestination());
	}

	@Test
	public void cornersAreKeptWithTheirTileIndex()
	{
		// east 10, diagonal north-east 5, north 10
		WorldMapPath.Line l = build(walk(3200, 3200, 3210, 3200, 3215, 3205, 3215, 3215)).line(0);
		assertArrayEquals(new int[]{3200, 3210, 3215, 3215}, xs(l));
		assertArrayEquals(new int[]{3200, 3200, 3205, 3215}, ys(l));
		assertEquals(10, l.tileIndex(1));
		assertEquals(15, l.tileIndex(2));
		assertEquals(25, l.tileIndex(3));
		assertEquals(3200, l.getMinX());
		assertEquals(3215, l.getMaxX());
		assertEquals(3215, l.getMaxY());
	}

	@Test
	public void aWalkSplitsWhereThePlaneChanges()
	{
		int[] t = {Tiles.pack(3200, 3200, 0), Tiles.pack(3201, 3200, 0), Tiles.pack(3202, 3200, 0),
			Tiles.pack(3202, 3200, 1), Tiles.pack(3203, 3200, 1)};
		WorldMapPath p = build(new RouteStep(RouteStep.Kind.WALK, "Walk", "", 4, List.of(), null, t, -1));
		assertEquals(2, p.lineCount());
		assertEquals(0, p.line(0).getPlane());
		assertEquals(1, p.line(1).getPlane());
		assertEquals(3, p.line(1).tileIndex(0)); // indexes stay the step's own
	}

	@Test
	public void teleportsGetNoLineOnlyALandingMark()
	{
		RouteStep tele = new RouteStep(RouteStep.Kind.TELEPORT, "Teleport", "", 5, List.of(), "t-v", new int[0], -1,
			Tiles.pack(3212, 3424, 0));
		WorldMapPath p = build(walk(3200, 3200, 3205, 3200), tele, walk(3212, 3424, 3220, 3430));
		assertEquals(2, p.lineCount());
		assertEquals(0, p.line(0).getStep());
		assertEquals(2, p.line(1).getStep());
		assertEquals(1, p.markCount());
		assertEquals(WorldMapPath.MarkKind.TELEPORT, p.mark(0).getKind());
		assertEquals(Tiles.pack(3212, 3424, 0), p.mark(0).getTile());
	}

	@Test
	public void boatsAreAStraightDashedLineFromStartToLanding()
	{
		int sarim = Tiles.pack(3029, 3217, 0);
		int karamja = Tiles.pack(2956, 3146, 0);
		WorldMapPath p = build(transport(RouteStep.Kind.BOAT, sarim, karamja));
		assertEquals(1, p.lineCount());
		assertEquals(WorldMapPath.Style.BOAT, p.line(0).getStyle());
		assertArrayEquals(new int[]{3029, 2956}, xs(p.line(0)));
		assertEquals(-1, p.line(0).tileIndex(0));
		assertEquals(WorldMapPath.MarkKind.TRANSPORT, p.mark(0).getKind());
		assertEquals(sarim, p.mark(0).getTile());
	}

	@Test
	public void farLaddersGetOnlyAMarkNearOnesADottedHop()
	{
		int manhole = Tiles.pack(3237, 3458, 0);
		int sewer = Tiles.pack(3237, 9858, 0); // same plane, far away in map coordinates
		int pipeIn = Tiles.pack(3150, 9906, 0);
		int pipeOut = Tiles.pack(3153, 9910, 0);
		int up = Tiles.pack(3203, 3200, 1);
		WorldMapPath p = build(transport(RouteStep.Kind.TRAVERSAL, manhole, sewer),
			transport(RouteStep.Kind.TRAVERSAL, pipeIn, pipeOut),
			transport(RouteStep.Kind.TRAVERSAL, Tiles.pack(3203, 3200, 0), up));
		// the ladder up lands on the very same x / y: nothing to draw as a line on the flat map
		assertEquals(1, p.lineCount());
		assertEquals(WorldMapPath.Style.HOP, p.line(0).getStyle());
		assertEquals(1, p.line(0).getStep());
		assertEquals(3, p.markCount());
	}

	@Test
	public void everyPlaneIsKeptForTheFlatMap()
	{
		// walk on plane 0, stairs up landing 3 tiles away on plane 1, walk on plane 1, ladder up to plane 2
		int stairs = Tiles.pack(3205, 3200, 0);
		int landing = Tiles.pack(3205, 3203, 1);
		WorldMapPath p = build(walk(3200, 3200, 3205, 3200),
			transport(RouteStep.Kind.TRAVERSAL, stairs, landing),
			along(RouteStep.Kind.WALK, 1, 3205, 3203, 3200, 3203),
			transport(RouteStep.Kind.TRAVERSAL, Tiles.pack(3200, 3203, 1), Tiles.pack(3200, 3203, 2)));
		assertEquals(3, p.lineCount());
		assertEquals(0, p.line(0).getPlane());
		assertEquals(WorldMapPath.Style.HOP, p.line(1).getStyle()); // a hop across planes, drawn at its x / y
		assertEquals(0, p.line(1).getPlane()); // the plane it starts on
		assertArrayEquals(new int[]{3205, 3205}, xs(p.line(1)));
		assertEquals(1, p.line(2).getPlane());
		assertEquals(2, p.markCount());
		assertEquals(1, Tiles.plane(p.mark(1).getTile()));
	}

	@Test
	public void sailingWithoutCornersFollowsItsTiles()
	{
		WorldMapPath p = build(along(RouteStep.Kind.SAIL, 0, 3000, 3100, 3040, 3100, 3060, 3080));
		assertEquals(WorldMapPath.Style.SAIL, p.line(0).getStyle());
		assertEquals(3, p.line(0).pointCount());
	}

	@Test
	public void aCourseIsDrawnCornerToCornerNotAlongItsTiles()
	{
		// east-north-east (2:1) for 12 tiles across: its tiles step like a staircase, the line goes straight
		List<Integer> t = new ArrayList<>();
		int x = 3000;
		int y = 3100;
		t.add(Tiles.pack(x, y, 0));
		for (int i = 0; i < 12; i++)
		{
			x++;
			y += i % 2;
			t.add(Tiles.pack(x, y, 0));
		}
		for (int i = 0; i < 5; i++)
		{
			y++; // then north
			t.add(Tiles.pack(x, y, 0));
		}
		int[] tiles = t.stream().mapToInt(Integer::intValue).toArray();
		RouteStep sail = RouteSteps.withCorners(new RouteStep(RouteStep.Kind.SAIL, "Sail", "", 9, List.of(), null, tiles, -1),
			new int[]{0, 12, 17});
		WorldMapPath p = build(sail);
		WorldMapPath.Line l = p.line(0);
		assertEquals(WorldMapPath.Style.SAIL, l.getStyle());
		assertArrayEquals(new int[]{3000, 3012, 3012}, xs(l));
		assertEquals(3106, l.y(1));
		assertEquals(12, l.tileIndex(1));
		assertEquals(17, l.tileIndex(2));
	}

	@Test
	public void resultsWithoutARouteKeepOnlyTheDestination()
	{
		WorldMapPath p = WorldMapPaths.of(RouteResult.status(4, TO, RouteStatus.CALCULATING, "Calculating..."));
		assertEquals(0, p.lineCount());
		assertEquals(0, p.markCount());
		assertEquals(Tiles.pack(3300, 3300, 0), p.getDestination());
		assertEquals(-1, WorldMapPaths.of(RouteResult.idle(5)).getDestination());
		assertEquals(-1, WorldMapPath.EMPTY.getDestination());
	}

	@Test
	public void simplifyDropsOnlyPointsWithinTheTolerance()
	{
		// a zigzag 1 tile off the straight line, then a real corner 20 tiles off
		int[] xs = {0, 10, 20, 30, 40, 50};
		int[] ys = {0, 1, 0, 1, 0, 20};
		assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, WorldMapPath.simplify(xs, ys, 0));
		assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, WorldMapPath.simplify(xs, ys, 0.5));
		assertArrayEquals(new int[]{0, 4, 5}, WorldMapPath.simplify(xs, ys, 1.5));
		int[] two = WorldMapPath.simplify(xs, ys, 100);
		assertArrayEquals(new int[]{0, 5}, two);
	}

	@Test
	public void simplifiedLevelsAlwaysKeepTheEnds()
	{
		// a long wiggly walk
		int[] xy = new int[202];
		for (int i = 0; i < 101; i++)
		{
			xy[2 * i] = 3000 + i * 3;
			xy[2 * i + 1] = 3000 + (i % 2) * 2;
		}
		WorldMapPath.Line l = build(walk(xy)).line(0);
		int previous = Integer.MAX_VALUE;
		for (int level = 0; level < WorldMapPath.TOLERANCES.length; level++)
		{
			int[] pts = l.level(level);
			assertEquals(0, pts[0]);
			assertEquals(l.pointCount() - 1, pts[pts.length - 1]);
			assertTrue("levels only get smaller", pts.length <= previous);
			previous = pts.length;
		}
		assertEquals(l.pointCount(), l.level(0).length);
		assertEquals(2, l.level(WorldMapPath.TOLERANCES.length - 1).length);
	}

	@Test
	public void aRedrawnWalkIsDrawnAsRedrawn()
	{
		// the route's walk: north-east 5, then east 5; redrawn (the game's way): east 5, then north-east 5
		RouteResult r = RouteResults.ready(9, TO, List.of(walk(3200, 3200, 3205, 3205, 3210, 3205)));
		int[] line = new int[11];
		for (int i = 0; i <= 10; i++)
		{
			line[i] = Tiles.pack(3200 + i, 3200 + Math.max(0, i - 5), 0);
		}
		com.bestpath.route.DrawPath d = com.bestpath.route.DrawPath.of(r).withTiles(0, line);
		WorldMapPath p = WorldMapPaths.of(r, d);
		assertEquals(1, p.lineCount());
		assertArrayEquals(new int[]{3200, 3205, 3210}, xs(p.line(0)));
		assertArrayEquals(new int[]{3200, 3200, 3205}, ys(p.line(0)));
		assertEquals(Tiles.pack(3203, 3200, 0), p.stepTile(0, 3));
		// a draw list of another route is not used
		WorldMapPath own = WorldMapPaths.of(r, com.bestpath.route.DrawPath.NONE);
		assertArrayEquals(new int[]{3200, 3205, 3210}, xs(own.line(0)));
		assertArrayEquals(new int[]{3200, 3205, 3205}, ys(own.line(0)));
		assertEquals(Tiles.pack(3203, 3203, 0), own.stepTile(0, 3));
	}

	@Test
	public void zoomPicksTheLevel()
	{
		assertEquals(0, WorldMapPath.levelFor(8));
		assertEquals(0, WorldMapPath.levelFor(2));
		assertEquals(1, WorldMapPath.levelFor(1));
		assertEquals(2, WorldMapPath.levelFor(0.4));
		assertEquals(WorldMapPath.TOLERANCES.length - 1, WorldMapPath.levelFor(0.01));
	}

	@Test
	public void straightMeansSameDirection()
	{
		assertTrue(WorldMapPath.straight(0, 0, 1, 1, 2, 2));
		assertTrue(!WorldMapPath.straight(0, 0, 1, 1, 2, 1));
		assertTrue(!WorldMapPath.straight(0, 0, 1, 0, 0, 0)); // turning back
	}
}
