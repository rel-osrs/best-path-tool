package com.bestpath.core.sea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ocean grid (clearance, steps), the sea graph and the boat search on small hand-made seas. */
class SeaGraphTest
{
	private static long key(int x, int y)
	{
		return (long) x << 32 | y;
	}

	/** A sea: the box minus the land tiles; texture per row (null: all open water); blocked tiles are rocks. */
	private static SeaGrid sea(int x0, int y0, int w, int h, Set<Long> land, Set<Long> blocked, int[] textureByRow)
	{
		SeaMap.Builder b = new SeaMap.Builder();
		for (int x = x0; x < x0 + w; x++)
		{
			for (int y = y0; y < y0 + h; y++)
			{
				if (!land.contains(key(x, y)))
				{
					b.set(x, y, textureByRow == null ? 131 : textureByRow[y - y0]);
				}
			}
		}
		SeaMap sea = b.build();
		MovementRules.Flags f = (x, y) -> blocked.contains(key(x, y)) ? CollisionFlags.FLOOR : 0;
		return SeaGridBuilder.build(sea, SeaPieces.build(sea, f), f);
	}

	private static Boat boat(BoatType t, boolean inoculation)
	{
		return new Boat(1, t, 0, 0, 0, 0, inoculation, false, Boat.Focus.NONE, 0); // 1.5 tiles a tick
	}

	private static List<SeaGraph.Join> at(SeaGraph g, int x, int y)
	{
		return g.join(new int[][]{{x, y}});
	}

	/** Cost and real time from one point to another, or -1. */
	private static int[] sail(SeaGraph g, Boat b, int turnCost, int x0, int y0, int x1, int y1)
	{
		SeaRouter r = new SeaRouter(g);
		List<List<SeaGraph.Join>> ends = new ArrayList<>();
		ends.add(at(g, x1, y1));
		SeaRouter.Result res = SeaRouterDiagnostics.search(r, new SeaRouter.Rules(b, turnCost), List.of(new SeaRouter.Start(at(g, x0, y0), 0)), ends, Integer.MAX_VALUE / 2);
		return new int[]{res.cost(0), res.cost(0) < 0 ? -1 : TestSeaRoutes.realTime(g, res, 0)};
	}

	@Test
	void clearanceCountsTilesToTheNearestObstacle()
	{
		SeaGrid g = sea(3200, 3200, 11, 11, Set.of(), Set.of(), null);
		assertEquals(121, g.size());
		assertEquals(1, SeaGridDiagnostics.clearance(g, g.index(3200, 3205)));
		assertEquals(2, SeaGridDiagnostics.clearance(g, g.index(3201, 3205)));
		assertEquals(3, SeaGridDiagnostics.clearance(g, g.index(3202, 3203)));
		assertEquals(5, SeaGridDiagnostics.clearance(g, g.index(3205, 3205)));
		assertEquals(-1, g.index(3199, 3205));
	}

	@Test
	void noCuttingACornerOfLand()
	{
		SeaGrid g = sea(3200, 3200, 3, 3, Set.of(key(3201, 3200)), Set.of(), null);
		int i = g.index(3200, 3200);
		assertFalse(SeaGridDiagnostics.canMove(g, i, 7)); // north-east passes the land tile's corner
		assertTrue(SeaGridDiagnostics.canMove(g, i, 3));
		assertFalse(SeaGridDiagnostics.canMove(g, i, 1));
	}

	@Test
	void blockedTilesAndOtherSeasAreNotOcean()
	{
		Set<Long> blocked = new HashSet<>();
		for (int y = 3200; y < 3210; y++)
		{
			blocked.add(key(3205, y));
		}
		SeaGrid g = sea(3200, 3200, 20, 10, Set.of(), blocked, null);
		assertEquals(-1, g.index(3205, 3203));
		assertEquals(-1, g.index(3202, 3203)); // the smaller sea
		assertEquals(140, g.size());
	}

	@Test
	void linksGoInSixteenHeadings()
	{
		SeaGraph g = SeaGraph.build(sea(3200, 3200, 64, 64, Set.of(), Set.of(), null), List.of());
		int w = g.waypointAt(3232, 3232);
		for (int d = 0; d < SeaGraph.HEADINGS; d++)
		{
			assertTrue(g.hasLink(w, d), "heading " + d);
		}
		int sp = SeaGraph.SPACING;
		assertEquals(g.waypointAt(3232 + 2 * sp, 3232 + sp), g.linkTarget(w, 1)); // east-north-east: 2 along, 1 across
		assertEquals(g.waypointAt(3232, 3232 + sp), g.linkTarget(w, 4)); // north
		assertEquals(Math.hypot(2 * sp, sp), SeaGraph.linkLength(1), 1e-9);
	}

	@Test
	void aStraightCourseWhenItIsFree()
	{
		// open sea: 32 tiles east-north-east (2:1) is one heading the whole way
		SeaGraph g = SeaGraph.build(sea(3200, 3200, 80, 64, Set.of(), Set.of(), null), List.of());
		int[] straight = sail(g, boat(BoatType.RAFT, false), 50, 3208, 3208, 3240, 3224);
		assertTrue(Math.abs((int) Math.ceil(Math.hypot(32, 16) * 100 / 1.5) - straight[1]) <= 4, "real time " + straight[1]);
		assertTrue(Math.abs(straight[1] - straight[0]) <= 4, straight[0] + " vs " + straight[1]); // no turns, no tight water (rounding only)
	}

	@Test
	void turningCostsExtraButTheRealTimeIsSailingOnly()
	{
		// round a wall: the course must turn; with a turn cost the cost is above the real time
		Set<Long> land = new HashSet<>();
		for (int y = 3200; y < 3240; y++)
		{
			land.add(key(3232, y));
		}
		SeaGraph g = SeaGraph.build(sea(3200, 3200, 64, 64, land, Set.of(), null), List.of());
		int[] free = sail(g, boat(BoatType.RAFT, false), 0, 3220, 3216, 3244, 3216);
		int[] punished = sail(g, boat(BoatType.RAFT, false), 100, 3220, 3216, 3244, 3216);
		assertTrue(free[0] > 0 && punished[0] > free[0]);
		assertTrue(punished[1] >= free[1]); // fewer turns may take a slightly longer way, never shorter
	}

	@Test
	void boatsStayOutOfAreasTheyMayNotEnter()
	{
		int[] tex = new int[48];
		for (int r = 0; r < 48; r++)
		{
			tex[r] = r >= 20 && r <= 23 ? 176 : 131; // a fetid band right across
		}
		SeaGraph g = SeaGraph.build(sea(3200, 3200, 48, 48, Set.of(), Set.of(), tex), List.of());
		assertEquals(-1, sail(g, boat(BoatType.RAFT, false), 50, 3224, 3208, 3224, 3240)[0]);
		assertTrue(sail(g, boat(BoatType.RAFT, true), 50, 3224, 3208, 3224, 3240)[0] > 0);
	}

	@Test
	void narrowGapsAreForSmallBoatsOnlyExceptInHarbours()
	{
		// a land wall across the sea at x 3232 with a one-tile gap at y 3232
		Set<Long> land = new HashSet<>();
		for (int y = 3200; y < 3264; y++)
		{
			if (y != 3232)
			{
				land.add(key(3232, y));
			}
		}
		SeaGrid grid = sea(3200, 3200, 64, 64, land, Set.of(), null);
		SeaGraph g = SeaGraph.build(grid, List.of());
		assertTrue(sail(g, boat(BoatType.RAFT, false), 50, 3220, 3232, 3244, 3232)[0] > 0);
		assertEquals(-1, sail(g, boat(BoatType.SKIFF, false), 50, 3220, 3232, 3244, 3232)[0]);
		Dock d = new Dock(0, "Gap", 1, null, Dock.Kind.GANGPLANK, 3233, 3236, 0, 1, 3232, 3234, 1, List.of());
		SeaGraph withHarbour = SeaGraph.build(grid, List.of(d));
		assertTrue(sail(withHarbour, boat(BoatType.SKIFF, false), 50, 3220, 3232, 3244, 3232)[0] > 0);
	}

	@Test
	void theTilesFollowTheCourse()
	{
		SeaGraph g = SeaGraph.build(sea(3200, 3200, 64, 64, Set.of(), Set.of(), null), List.of());
		SeaRouter r = new SeaRouter(g);
		List<List<SeaGraph.Join>> ends = new ArrayList<>();
		ends.add(at(g, 3250, 3230));
		SeaRouter.Result res = SeaRouterDiagnostics.search(r, new SeaRouter.Rules(boat(BoatType.RAFT, false), 50),
			List.of(new SeaRouter.Start(at(g, 3210, 3211), 0)), ends, Integer.MAX_VALUE / 2);
		int[] t = res.course(0).tiles;
		assertEquals(3210 << 14 | 3211, t[0]);
		assertEquals(3250 << 14 | 3230, t[t.length - 1]);
		for (int i = 1; i < t.length; i++)
		{
			assertEquals(1, Math.max(Math.abs((t[i] >>> 14) - (t[i - 1] >>> 14)), Math.abs((t[i] & 0x3FFF) - (t[i - 1] & 0x3FFF))));
		}
		assertStraightLegs(res.course(0));
	}

	/**
	 * Each leg of a course runs straight in one of the boat's EXACT 16 headings (22.5 degrees apart),
	 * the tiles step one at a time along the exact legs, legs meet at corners (not always whole tiles).
	 */
	static void assertStraightLegs(SeaRouter.Course c)
	{
		int[] t = c.tiles;
		int[] k = c.corners;
		double[] p = c.points;
		assertEquals(0, k[0]);
		assertEquals(t.length - 1, k[k.length - 1]);
		assertEquals(2 * k.length, p.length);
		assertEquals(k.length - 1, c.headings.length);
		for (int i = 1; i < t.length; i++)
		{
			assertEquals(1, Math.max(Math.abs((t[i] >>> 14) - (t[i - 1] >>> 14)), Math.abs((t[i] & 0x3FFF) - (t[i - 1] & 0x3FFF))));
		}
		assertEquals(t[0], tileOf(p[0], p[1]));
		assertEquals(t[t.length - 1], tileOf(p[p.length - 2], p[p.length - 1]));
		int last = -1;
		double length = 0;
		for (int i = 1; i < k.length; i++)
		{
			assertTrue(k[i] >= k[i - 1]);
			double ax = p[2 * i - 2];
			double ay = p[2 * i - 1];
			double dx = p[2 * i] - ax;
			double dy = p[2 * i + 1] - ay;
			int h = c.headings[i - 1];
			double len = Math.hypot(dx, dy);
			length += len;
			// exactly along heading h: no sideways part, forwards
			assertEquals(0, dx * SeaGraph.EY[h] - dy * SeaGraph.EX[h], 1e-9 * Math.max(1, len),
				"leg " + dx + "," + dy + " is not along heading " + h);
			assertTrue(dx * SeaGraph.EX[h] + dy * SeaGraph.EY[h] > 0);
			assertTrue(h != last, "two legs in a row in heading " + h);
			last = h;
			// every tile of the leg lies on it (within the tile the boat's centre passes through)
			for (int j = k[i - 1]; j <= k[i]; j++)
			{
				double off = segmentDistance((t[j] >>> 14) - ax, (t[j] & 0x3FFF) - ay, dx, dy);
				assertTrue(off <= 0.75, "tile " + (t[j] >>> 14) + "," + (t[j] & 0x3FFF) + " is " + off + " off the leg");
			}
		}
		assertEquals(length, c.length, 1e-9);
	}

	private static int tileOf(double x, double y)
	{
		return (int) Math.floor(x + 0.5) << 14 | (int) Math.floor(y + 0.5);
	}

	/** Distance from (px, py) to the segment from (0, 0) to (dx, dy). */
	private static double segmentDistance(double px, double py, double dx, double dy)
	{
		double len2 = dx * dx + dy * dy;
		double f = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy) / len2));
		return Math.hypot(px - f * dx, py - f * dy);
	}

	@Test
	void theBoatsHeadingsAreExactCompassPoints()
	{
		// 16 headings 22.5 degrees apart, not tile ratios (1:2 is 26.6 degrees)
		for (int d = 0; d < SeaGraph.HEADINGS; d++)
		{
			assertEquals(1, Math.hypot(SeaGraph.EX[d], SeaGraph.EY[d]), 1e-12);
			double fromEast = Math.toDegrees(Math.atan2(SeaGraph.EY[d], SeaGraph.EX[d]));
			assertEquals(0, Math.IEEEremainder(fromEast - 22.5 * d, 360), 1e-9, "heading " + d);
			double compass = Math.toDegrees(Math.atan2(SeaGraph.EX[d], SeaGraph.EY[d]));
			assertEquals(0, Math.IEEEremainder(compass - SeaGraphDiagnostics.compassDegrees(d), 360), 1e-9);
		}
		assertEquals(22.5, SeaGraphDiagnostics.compassDegrees(3), 0); // north-north-east
		assertEquals(67.5, SeaGraphDiagnostics.compassDegrees(1), 0); // east-north-east
		assertEquals(0, SeaGraphDiagnostics.compassDegrees(4), 0); // north
		assertEquals(SeaGraph.EX[2], SeaGraph.EY[2], 0); // north-east: exactly equal steps
		assertEquals(0, SeaGraph.exactHeadingOf(5, 0));
		assertEquals(6, SeaGraph.exactHeadingOf(-3, 3));
		assertEquals(-1, SeaGraph.exactHeadingOf(1, 2)); // 26.6 degrees: no heading
	}

	@Test
	void exactLinesAreCheckedOnEveryTileTheyPass()
	{
		// from 3210,3210 to 3214,3211: the line crosses into the next row half way, at x 3212 (the middle of a tile),
		// so it passes 3212,3210 AND 3212,3211 (a supercover); 3211,3211 it never touches
		int[] out = new int[3];
		for (long rock : new long[]{key(3212, 3210), key(3212, 3211)})
		{
			SeaGraph g = SeaGraph.build(sea(3200, 3200, 32, 32, Set.of(rock), Set.of(), null), List.of());
			String at = (rock >> 32) + "," + (rock & 0xFFFFFFFFL);
			assertFalse(g.exactLine(3210, 3210, 3214, 3211, out), "rock " + at);
			assertFalse(g.exactLine(3214, 3211, 3210, 3210, out), "backwards, rock " + at);
		}
		SeaGraph aside = SeaGraph.build(sea(3200, 3200, 32, 32, Set.of(key(3211, 3211)), Set.of(), null), List.of());
		assertTrue(aside.exactLine(3210, 3210, 3214, 3211, out));
		assertEquals(1, out[0]); // the rock beside the line still narrows it
		// a diagonal passes tile corners exactly: the tiles beside each corner count, and land there stops it
		SeaGraph corner = SeaGraph.build(sea(3200, 3200, 32, 32, Set.of(key(3212, 3211)), Set.of(), null), List.of());
		assertFalse(corner.exactLine(3210, 3210, 3214, 3214, out));
		// an exact north-north-east line from a whole tile to a corner that is not one
		SeaGraph open = SeaGraph.build(sea(3200, 3200, 64, 64, Set.of(), Set.of(), null), List.of());
		double len = 20;
		assertTrue(open.exactLine(3220, 3210, 3220 + len * SeaGraph.EX[3], 3210 + len * SeaGraph.EY[3], out));
		assertTrue(out[0] >= 5);
	}

	@Test
	void aNorthNorthEastCourseKeepsTheExactAngle()
	{
		// 20 east and 48 north (22.6 degrees from north): nearly all of it one exact north-north-east leg (22.5 degrees),
		// the rest a short north-east leg; before, such legs went 1 east for 2 north (26.6 degrees) and the boat drifted
		SeaGraph g = SeaGraph.build(sea(3200, 3200, 64, 64, Set.of(), Set.of(), null), List.of());
		double[] two = new double[7];
		double len = g.exactTwoLegs(3210, 3205, 3230, 3253, -1, two);
		assertTrue(len > Math.hypot(20, 48) && len < Math.hypot(20, 48) + 0.05, "length " + len); // about 0.04 % longer
		assertTrue((int) two[5] == 3 || (int) two[6] == 3, "a north-north-east leg");
		// the search's course over the waypoints, pulled straight: its long leg is exactly north-north-east
		SeaRouter r = new SeaRouter(g);
		List<List<SeaGraph.Join>> ends = new ArrayList<>();
		ends.add(at(g, 3230, 3253));
		SeaRouter.Course c = SeaRouterDiagnostics.search(r, new SeaRouter.Rules(boat(BoatType.RAFT, false), 50),
			List.of(new SeaRouter.Start(at(g, 3210, 3205), 0)), ends, Integer.MAX_VALUE / 2).course(0);
		assertStraightLegs(c);
		double longest = 0;
		int heading = -1;
		double angle = 0;
		for (int i = 0; i + 1 < c.corners.length; i++)
		{
			double dx = c.points[2 * i + 2] - c.points[2 * i];
			double dy = c.points[2 * i + 3] - c.points[2 * i + 1];
			if (Math.hypot(dx, dy) > longest)
			{
				longest = Math.hypot(dx, dy);
				heading = c.headings[i];
				angle = Math.toDegrees(Math.atan2(dx, dy));
			}
		}
		assertEquals(3, heading);
		assertEquals(22.5, angle, 1e-9);
		assertTrue(longest > 40, "longest leg " + longest);
		assertEquals(len, c.length, 1e-6); // pulled straight into the two exact legs
	}

	@Test
	void exactLegsNeverCrossARock()
	{
		// a rock near the grid's straight line from 3208,3208 to 3248,3228 (2:1); the exact legs (22.5 and 45 degrees)
		// bow up to about a tile off that line: wherever the rock is, every leg is clear over every tile it passes
		for (int ry = 3224; ry <= 3232; ry++)
		{
			Set<Long> rock = Set.of(key(3240, ry));
			SeaGraph g = SeaGraph.build(sea(3200, 3200, 64, 64, rock, Set.of(), null), List.of());
			SeaRouter r = new SeaRouter(g);
			List<List<SeaGraph.Join>> ends = new ArrayList<>();
			ends.add(at(g, 3248, 3228));
			SeaRouter.Result res = SeaRouterDiagnostics.search(r, new SeaRouter.Rules(boat(BoatType.RAFT, false), 50),
				List.of(new SeaRouter.Start(at(g, 3208, 3208), 0)), ends, Integer.MAX_VALUE / 2);
			assertTrue(res.cost(0) > 0);
			SeaRouter.Course c = res.course(0);
			assertStraightLegs(c);
			int[] out = new int[3];
			for (int i = 0; i + 1 < c.corners.length; i++)
			{
				assertTrue(g.exactLine(c.points[2 * i], c.points[2 * i + 1], c.points[2 * i + 2], c.points[2 * i + 3], out),
					"leg " + i + " crosses the rock at 3240," + ry);
			}
		}
	}

	@Test
	void everyStepSplitsIntoTwoNeighbouringHeadings()
	{
		for (int dx = -20; dx <= 20; dx++)
		{
			for (int dy = -20; dy <= 20; dy++)
			{
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				int[] s = SeaGraph.split(dx, dy);
				assertTrue(s[0] >= 0 && s[2] >= 0);
				assertEquals((s[1] + 1) % SeaGraph.HEADINGS, s[3]);
				assertEquals(dx, s[0] * SeaGraph.UX[s[1]] + s[2] * SeaGraph.UX[s[3]]);
				assertEquals(dy, s[0] * SeaGraph.UY[s[1]] + s[2] * SeaGraph.UY[s[3]]);
			}
		}
		assertEquals(1, headingOf(6, 3)); // east-north-east
		assertEquals(-1, headingOf(3, 1)); // between two headings
	}

	@Test
	void coursesAreStraightLegsInSixteenHeadingsWithoutDrift()
	{
		// open sea and a sea with an island: from odd points (not waypoints) to odd points, every course is straight legs
		Set<Long> island = new HashSet<>();
		for (int x = 3230; x < 3250; x++)
		{
			for (int y = 3222; y < 3236; y++)
			{
				island.add(key(x, y));
			}
		}
		for (Set<Long> land : List.of(Set.<Long>of(), island))
		{
			SeaGraph g = SeaGraph.build(sea(3200, 3200, 96, 64, land, Set.of(), null), List.of());
			SeaRouter r = new SeaRouter(g);
			int[][] pts = {{3203, 3205}, {3285, 3251}, {3211, 3258}, {3290, 3203}, {3257, 3229}};
			for (int[] a : pts)
			{
				for (int[] b : pts)
				{
					if (a == b)
					{
						continue;
					}
					List<List<SeaGraph.Join>> ends = new ArrayList<>();
					ends.add(at(g, b[0], b[1]));
					SeaRouter.Result res = SeaRouterDiagnostics.search(r, new SeaRouter.Rules(boat(BoatType.RAFT, false), 50),
						List.of(new SeaRouter.Start(at(g, a[0], a[1]), 0)), ends, Integer.MAX_VALUE / 2);
					assertTrue(res.cost(0) > 0, a[0] + "," + a[1] + " -> " + b[0] + "," + b[1]);
					SeaRouter.Course c = res.course(0);
					assertEquals(a[0] << 14 | a[1], c.tiles[0]);
					assertEquals(b[0] << 14 | b[1], c.tiles[c.tiles.length - 1]);
					assertStraightLegs(c);
					for (int t : c.tiles)
					{
						assertFalse(land.contains(key(t >>> 14, t & 0x3FFF)), "over land");
					}
				}
			}
		}
		// a straight run along an exact heading is one leg: 40 tiles north-east from a waypoint
		SeaGraph g = SeaGraph.build(sea(3200, 3200, 96, 64, Set.of(), Set.of(), null), List.of());
		List<List<SeaGraph.Join>> ends = new ArrayList<>();
		ends.add(at(g, 3256, 3256));
		SeaRouter.Course c = SeaRouterDiagnostics.search(new SeaRouter(g), new SeaRouter.Rules(boat(BoatType.RAFT, false), 50),
			List.of(new SeaRouter.Start(at(g, 3216, 3216), 0)), ends, Integer.MAX_VALUE / 2).course(0);
		assertEquals(2, c.corners.length);
	}

	@Test
	void joinLegsKeepTheirClearance()
	{
		// from 3205,3204 to the waypoint 3216,3208 (11 east, 4 north = 3 east + 4 east-north-east steps, either order);
		// a rock where the first order turns: the other corner is taken
		assertEquals(8, SeaGraph.SPACING, "the case below is set up for waypoints 8 tiles apart");
		SeaGraph open = SeaGraph.build(sea(3200, 3200, 64, 64, Set.of(), Set.of(), null), List.of());
		SeaGraph.Join free = find(open.join(new int[][]{{3205, 3204}}), open.waypointAt(3216, 3208));
		assertEquals(3208, free.cornerX); // east first (3 tiles), then east-north-east (8, 4)
		assertEquals(3204, free.cornerY);
		assertEquals(3 + Math.hypot(8, 4), free.length, 1e-9);
		SeaGraph rock = SeaGraph.build(sea(3200, 3200, 64, 64, Set.of(key(3208, 3204)), Set.of(), null), List.of());
		SeaGraph.Join round = find(rock.join(new int[][]{{3205, 3204}}), rock.waypointAt(3216, 3208));
		assertEquals(3213, round.cornerX); // east-north-east first (8, 4), then east (3 tiles)
		assertEquals(3208, round.cornerY);
	}

	private static SeaGraph.Join find(List<SeaGraph.Join> joins, int wp)
	{
		for (SeaGraph.Join j : joins)
		{
			if (j.waypoint == wp)
			{
				return j;
			}
		}
		throw new AssertionError("no join to waypoint " + wp);
	}

	@Test
	void harboursLetBoatsThroughTightWaterOnlyInsideThem()
	{
		// a one-tile gap at 3240,3232, far from the buoy at 3220,3232: links that touch its harbour don't take a skiff
		// through the gap
		Set<Long> land = new HashSet<>();
		for (int y = 3200; y < 3264; y++)
		{
			if (y != 3232)
			{
				land.add(key(3240, y));
			}
		}
		SeaGrid grid = sea(3200, 3200, 64, 64, land, Set.of(), null);
		Dock farBuoy = new Dock(0, "Far", 1, null, Dock.Kind.GANGPLANK, 3200, 3201, 0, 1, 3220, 3232, 1, List.of());
		SeaGraph g = SeaGraph.build(grid, List.of(farBuoy));
		assertEquals(-1, sail(g, boat(BoatType.SKIFF, false), 50, 3226, 3236, 3252, 3232)[0]);
		assertTrue(sail(g, boat(BoatType.RAFT, false), 50, 3226, 3236, 3252, 3232)[0] > 0);
	}

	@Test
	void theAimedSearchFindsTheLeastCost()
	{
		// an island in the way: the cost found with the ends' "after" estimates equals a plain search's
		Set<Long> land = new HashSet<>();
		for (int x = 3230; x < 3260; x++)
		{
			for (int y = 3210; y < 3250; y++)
			{
				land.add(key(x, y));
			}
		}
		SeaGraph g = SeaGraph.build(sea(3200, 3200, 96, 64, land, Set.of(), null), List.of());
		SeaRouter r = new SeaRouter(g);
		SeaRouter.Rules rules = new SeaRouter.Rules(boat(BoatType.RAFT, false), 50);
		List<SeaRouter.Start> start = List.of(new SeaRouter.Start(at(g, 3210, 3230), 0));
		int[][] ends = {{3280, 3230}, {3245, 3258}, {3245, 3204}, {3212, 3256}};
		List<List<SeaGraph.Join>> plain = new ArrayList<>();
		List<SeaRouter.End> aimed = new ArrayList<>();
		for (int i = 0; i < ends.length; i++)
		{
			plain.add(at(g, ends[i][0], ends[i][1]));
			aimed.add(new SeaRouter.End(at(g, ends[i][0], ends[i][1]), i * 700));
		}
		SeaRouter.Result a = SeaRouterDiagnostics.search(r, rules, start, plain, Integer.MAX_VALUE / 2);
		SeaRouter.Result b = r.searchTo(rules, start, aimed, Integer.MAX_VALUE / 2);
		for (int i = 0; i < ends.length; i++)
		{
			assertTrue(a.cost(i) > 0);
			assertEquals(a.cost(i), b.cost(i), "end " + i);
		}
		// a bound only the nearest end fits: the far one is left out, and less of the sea is searched than for both
		int near = a.cost(3);
		r.searchTo(rules, start, List.of(new SeaRouter.End(at(g, 3212, 3256), 0), new SeaRouter.End(at(g, 3280, 3230), 0)),
			Integer.MAX_VALUE / 2);
		int both = r.lastSearchSize()[0];
		SeaRouter.Result c = r.searchTo(rules, start, List.of(new SeaRouter.End(at(g, 3212, 3256), 0),
			new SeaRouter.End(at(g, 3280, 3230), 0)), near + 50);
		assertEquals(near, c.cost(0));
		assertEquals(-1, c.cost(1));
		assertTrue(r.lastSearchSize()[0] < both, r.lastSearchSize()[0] + " vs " + both);
	}

	/** The heading a step (dx, dy) points along exactly (a whole number of its unit steps), or -1. */
	private static int headingOf(int dx, int dy)
	{
		if (dx == 0 && dy == 0)
		{
			return -1;
		}
		for (int d = 0; d < SeaGraph.HEADINGS; d++)
		{
			// parallel and the same way, a whole number of unit steps
			if (dx * SeaGraph.UY[d] == dy * SeaGraph.UX[d] && dx * SeaGraph.UX[d] + dy * SeaGraph.UY[d] > 0
				&& (SeaGraph.UX[d] == 0 || dx % SeaGraph.UX[d] == 0) && (SeaGraph.UY[d] == 0 || dy % SeaGraph.UY[d] == 0))
			{
				return d;
			}
		}
		return -1;
	}
}
