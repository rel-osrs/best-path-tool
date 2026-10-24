package com.bestpath.core.sea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The compact ocean grid, sea graph and boat search against the first forms (reference copies
 * in this folder) on random hand-made seas that cross region borders: islands, rocks, walls between sea
 * tiles (odd boat steps), five sea areas, docks with harbours. Every answer must be the same.
 */
class CompactSeaTest
{
	/** The sea box: crosses the region borders at x 3200 / 3264 and y 3200 / 3264. */
	private static final int X0 = 3170;
	private static final int Y0 = 3176;
	private static final int W = 130;
	private static final int H = 120;

	private static long key(int x, int y)
	{
		return (long) x << 32 | (y & 0xFFFFFFFFL);
	}

	/** One random sea. */
	private static final class World
	{
		final SeaMap sea;
		final MovementRules.Flags flags;
		final List<Dock> docks = new ArrayList<>();

		World(long seed)
		{
			Random rnd = new Random(seed);
			boolean[][] land = new boolean[W][H];
			for (int k = 0; k < 14; k++)
			{
				// islands: filled discs
				int cx = rnd.nextInt(W);
				int cy = rnd.nextInt(H);
				int r = 2 + rnd.nextInt(9);
				for (int x = cx - r; x <= cx + r; x++)
				{
					for (int y = cy - r; y <= cy + r; y++)
					{
						if (x >= 0 && y >= 0 && x < W && y < H && (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r)
						{
							land[x][y] = true;
						}
					}
				}
			}
			// sea areas in bands; a couple of single tiles of another texture (shallows) here and there
			int[] bandTexture = {131, 136, 161, 171, 176, 181};
			SeaMap.Builder b = new SeaMap.Builder();
			for (int x = 0; x < W; x++)
			{
				for (int y = 0; y < H; y++)
				{
					if (!land[x][y])
					{
						int band = (x / 22 + y / 30) % bandTexture.length;
						b.set(X0 + x, Y0 + y, rnd.nextInt(200) == 0 ? 1 : bandTexture[band]);
					}
				}
			}
			sea = b.build();
			Map<Long, Integer> f = new HashMap<>();
			for (int k = 0; k < 40; k++)
			{
				f.put(key(X0 + rnd.nextInt(W), Y0 + rnd.nextInt(H)), CollisionFlags.OBJECT); // rocks
			}
			for (int k = 0; k < 30; k++)
			{
				// a wall between two sea tiles (east edge of one, west edge of the next)
				int x = X0 + rnd.nextInt(W - 1);
				int y = Y0 + rnd.nextInt(H);
				f.merge(key(x, y), CollisionFlags.EAST, (a, c) -> a | c);
				f.merge(key(x + 1, y), CollisionFlags.WEST, (a, c) -> a | c);
			}
			flags = (x, y) -> f.getOrDefault(key(x, y), 0);
			for (int k = 0; k < 4; k++)
			{
				int bx = X0 + 5 + rnd.nextInt(W - 10);
				int by = Y0 + 5 + rnd.nextInt(H - 10);
				docks.add(new Dock(k, "Dock " + k, 1, null, Dock.Kind.GANGPLANK, bx, by + 3, 0, 1, bx, by, 2, List.of()));
			}
		}

		SeaPieces pieces()
		{
			return SeaPieces.build(sea, flags);
		}
	}

	@Test
	void theGridGivesTheSameAnswers()
	{
		for (long seed = 1; seed <= 6; seed++)
		{
			World w = new World(seed);
			SeaPieces pieces = w.pieces();
			ReferenceSeaGrid ref = ReferenceSeaGrid.build(w.sea, pieces, w.flags);
			SeaGrid g = SeaGridBuilder.build(w.sea, pieces, w.flags);
			assertEquals(ref.size(), g.size());
			assertTrue(ref.size() > 5000, "a real sea");
			int odd = 0;
			for (int i = 0; i < ref.size(); i++)
			{
				int x = ref.x(i);
				int y = ref.y(i);
				assertEquals(i, g.index(x, y));
				assertEquals(x, SeaGridDiagnostics.x(g, i));
				assertEquals(y, SeaGridDiagnostics.y(g, i));
				assertEquals(ref.clearance(i), g.clearanceAt(x, y), x + "," + y);
				assertEquals(ref.clearance(i), SeaGridDiagnostics.clearance(g, i));
				assertEquals(ref.areaOrdinal(i), g.areaOrdinalAt(x, y));
				for (int d = 0; d < 8; d++)
				{
					assertEquals(ref.canMove(i, d), g.canMove(x, y, d), x + "," + y + " d" + d);
					assertEquals(ref.canMove(i, d), SeaGridDiagnostics.canMove(g, i, d));
				}
			}
			for (int x = X0 - 3; x < X0 + W + 3; x++)
			{
				for (int y = Y0 - 3; y < Y0 + H + 3; y++)
				{
					assertEquals(ref.index(x, y), g.index(x, y));
					assertEquals(ref.index(x, y) >= 0, g.isOcean(x, y));
				}
			}
			odd += SeaGridDiagnostics.oddTiles(g).length;
			assertTrue(odd > 0, "the walls make odd steps");
			// from its parts (as the plugin's map data stores them), and with the clearance table dropped
			int[] keys = g.regionIds();
			long[][] masks = new long[keys.length][];
			byte[] uniform = new byte[keys.length];
			byte[][] areas = new byte[keys.length][];
			for (int s = 0; s < keys.length; s++)
			{
				masks[s] = g.regionMask(s);
				uniform[s] = (byte) SeaGridDiagnostics.regionUniformArea(g, s);
				areas[s] = SeaGridDiagnostics.regionAreas(g, s);
			}
			SeaGrid back = SeaGrid.of(keys, masks, uniform, areas, SeaGridDiagnostics.oddTiles(g), SeaGridDiagnostics.oddMoves(g));
			assertTrue(back.sameAs(g));
			back.dropClearanceTable();
			for (int i = 0; i < ref.size(); i++)
			{
				int x = ref.x(i);
				int y = ref.y(i);
				assertEquals(ref.clearance(i), back.clearanceAt(x, y), "from the mask " + x + "," + y);
				assertEquals(SeaGridDiagnostics.moves(g, x, y), SeaGridDiagnostics.moves(back, x, y));
			}
		}
	}

	@Test
	void theGraphHasTheSameLinks()
	{
		for (long seed = 1; seed <= 6; seed++)
		{
			World w = new World(seed);
			SeaPieces pieces = w.pieces();
			ReferenceSeaGraph ref = ReferenceSeaGraph.build(ReferenceSeaGrid.build(w.sea, pieces, w.flags), w.docks);
			SeaGrid grid = SeaGridBuilder.build(w.sea, pieces, w.flags);
			grid.dropClearanceTable(); // building makes it again
			SeaGraph g = SeaGraph.build(grid, w.docks);
			assertEquals(ref.waypointCount(), g.waypointCount());
			int links = 0;
			for (int wp = 0; wp < ref.waypointCount(); wp++)
			{
				assertEquals(ref.x(wp), g.x(wp));
				assertEquals(ref.y(wp), g.y(wp));
				assertEquals(wp, g.waypointAt(ref.x(wp), ref.y(wp)));
				for (int d = 0; d < SeaGraph.HEADINGS; d++)
				{
					assertEquals(ref.hasLink(wp, d), g.hasLink(wp, d));
					if (ref.hasLink(wp, d))
					{
						links++;
						assertEquals(ref.linkTarget(wp, d), g.linkTarget(wp, d));
						assertEquals(ref.linkClearance(wp, d), g.linkClearance(wp, d));
						assertEquals(ref.linkAreas(wp, d), g.linkAreas(wp, d));
						assertEquals(ref.linkHarbour(wp, d), g.linkHarbour(wp, d));
					}
				}
			}
			assertTrue(links > 1000);
			// joins from points at sea
			Random rnd = new Random(seed);
			for (int k = 0; k < 50; k++)
			{
				int[][] from = {{X0 + rnd.nextInt(W), Y0 + rnd.nextInt(H)}, {X0 + rnd.nextInt(W), Y0 + rnd.nextInt(H)}};
				assertJoinsSame(ref.join(from), g.join(from));
			}
		}
	}

	private static void assertJoinsSame(List<ReferenceSeaGraph.Join> a, List<SeaGraph.Join> b)
	{
		assertEquals(a.size(), b.size());
		for (int i = 0; i < a.size(); i++)
		{
			ReferenceSeaGraph.Join x = a.get(i);
			SeaGraph.Join y = b.get(i);
			assertEquals(x.waypoint, y.waypoint);
			assertEquals(x.fromX, y.fromX);
			assertEquals(x.fromY, y.fromY);
			assertEquals(x.length, y.length, 0);
			assertEquals(x.clearance, y.clearance);
			assertEquals(x.areas, y.areas);
			assertEquals(x.harbour, y.harbour);
		}
	}

	@Test
	void theBoatSearchFindsTheSameWays()
	{
		int compared = 0;
		for (long seed = 1; seed <= 4; seed++)
		{
			World w = new World(seed);
			SeaPieces pieces = w.pieces();
			ReferenceSeaGraph rg = ReferenceSeaGraph.build(ReferenceSeaGrid.build(w.sea, pieces, w.flags), w.docks);
			SeaGraph g = SeaGraph.build(SeaGridBuilder.build(w.sea, pieces, w.flags), w.docks);
			ReferenceSeaRouter rr = new ReferenceSeaRouter(rg);
			SeaRouter nr = new SeaRouter(g);
			Random rnd = new Random(seed * 17);
			for (int k = 0; k < 60; k++)
			{
				BoatType type = BoatType.values()[rnd.nextInt(3)];
				Boat boat = new Boat(1, type, rnd.nextInt(7), rnd.nextInt(7), rnd.nextInt(7), rnd.nextInt(7), rnd.nextBoolean(),
					rnd.nextBoolean(), Boat.Focus.NONE, 0);
				int turn = rnd.nextInt(3) * 50;
				List<ReferenceSeaRouter.Start> rs = new ArrayList<>();
				List<SeaRouter.Start> ns = new ArrayList<>();
				for (int s = 0; s < 1 + rnd.nextInt(3); s++)
				{
					int[][] p = {{X0 + rnd.nextInt(W), Y0 + rnd.nextInt(H)}};
					int cost = rnd.nextInt(3000);
					rs.add(new ReferenceSeaRouter.Start(rg.join(p), cost));
					ns.add(new SeaRouter.Start(g.join(p), cost));
				}
				List<List<ReferenceSeaGraph.Join>> re = new ArrayList<>();
				List<List<SeaGraph.Join>> ne = new ArrayList<>();
				for (int e = 0; e < 1 + rnd.nextInt(6); e++)
				{
					int[][] p = {{X0 + rnd.nextInt(W), Y0 + rnd.nextInt(H)}};
					re.add(rg.join(p));
					ne.add(g.join(p));
				}
				int bound = rnd.nextBoolean() ? Integer.MAX_VALUE / 2 : 2000 + rnd.nextInt(20000);
				ReferenceSeaRouter.Result a = rr.search(new ReferenceSeaRouter.Rules(boat, turn), rs, re, bound);
				SeaRouter.Result b = SeaRouterDiagnostics.search(nr, new SeaRouter.Rules(boat, turn), ns, ne, bound);
				for (int e = 0; e < re.size(); e++)
				{
					// the aimed search finds the same least cost; it does not look past the bound for an end's
					// last join, and ties may come out another way (another start, another course of the same cost)
					if (a.cost(e) > bound)
					{
						continue;
					}
					assertEquals(a.cost(e), b.cost(e), "seed " + seed + " search " + k + " end " + e);
					if (a.cost(e) >= 0)
					{
						compared++;
						SeaRouter.Course c = b.course(e);
						SeaGraphTest.assertStraightLegs(c);
					}
				}
			}
		}
		assertTrue(compared > 100, "found ways compared: " + compared);
	}

	@Test
	void aSearchKeepsOnlyThePagesItReaches()
	{
		World w = new World(3);
		SeaGraph g = SeaGraph.build(SeaGridBuilder.build(w.sea, w.pieces(), w.flags), w.docks);
		SeaRouter r = new SeaRouter(g);
		Boat raft = new Boat(1, BoatType.RAFT, 0, 0, 0, 0, true, true, Boat.Focus.NONE, 0);
		int wp = g.waypointCount() / 2;
		List<SeaGraph.Join> here = g.join(new int[][]{{g.x(wp), g.y(wp)}});
		// a short bound: only a little of the sea is searched
		List<SeaGraph.Join> far = g.join(new int[][]{{g.x(0), g.y(0)}});
		SeaRouterDiagnostics.search(r, new SeaRouter.Rules(raft, 50), List.of(new SeaRouter.Start(here, 0)), List.of(far), 800);
		int[] small = r.lastSearchSize();
		SeaRouterDiagnostics.search(r, new SeaRouter.Rules(raft, 50), List.of(new SeaRouter.Start(here, 0)), List.of(here), Integer.MAX_VALUE / 2);
		assertTrue(small[1] < (g.waypointCount() + SeaRouter.PAGE - 1) / SeaRouter.PAGE, "pages " + small[1]);
		// with an end point right at the start, the search stops as soon as it is settled
		assertTrue(r.lastSearchSize()[0] < 100, "states " + r.lastSearchSize()[0]);
	}
}
