package com.bestpath.core.sea;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.SeaBits;
import com.bestpath.core.data.SeaBitsWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Channels (the Ynysdail water used to say "No route" although a boat fits in): two
 * open basins joined only by a crooked channel that no straight link between waypoints threads.
 */
class SeaChannelsTest
{
	private static long key(int x, int y)
	{
		return (long) x << 32 | y;
	}

	/**
	 * Basin A (x 3200-3239) and basin B (x 3270-3299), y 3200-3263, joined by a zigzag channel {@code width} tiles
	 * wide whose middle runs east from (3240, 3230), north to 3236, east, south to 3224, east, north to 3236 and east
	 * into basin B.
	 */
	private static SeaGrid sea(int width)
	{
		Set<Long> water = new HashSet<>();
		for (int x = 3200; x < 3300; x++)
		{
			for (int y = 3200; y < 3264; y++)
			{
				if (x < 3240 || x >= 3270)
				{
					water.add(key(x, y));
				}
			}
		}
		int[][] middle = {{3238, 3230}, {3246, 3230}, {3246, 3236}, {3252, 3236}, {3252, 3224}, {3258, 3224}, {3258, 3236},
			{3271, 3236}};
		int half = width / 2;
		for (int i = 0; i + 1 < middle.length; i++)
		{
			int x = middle[i][0];
			int y = middle[i][1];
			while (true)
			{
				for (int dx = -half; dx <= half; dx++)
				{
					for (int dy = -half; dy <= half; dy++)
					{
						water.add(key(x + dx, y + dy));
					}
				}
				if (x == middle[i + 1][0] && y == middle[i + 1][1])
				{
					break;
				}
				x += Integer.signum(middle[i + 1][0] - x);
				y += Integer.signum(middle[i + 1][1] - y);
			}
		}
		SeaMap.Builder b = new SeaMap.Builder();
		for (long k : water)
		{
			b.set((int) (k >>> 32), (int) k, 131);
		}
		SeaMap sea = b.build();
		MovementRules.Flags f = (x, y) -> 0;
		return SeaGridBuilder.build(sea, SeaPieces.build(sea, f), f);
	}

	private static Boat boat(BoatType t)
	{
		return new Boat(1, t, 0, 0, 0, 0, false, false, Boat.Focus.NONE, 0);
	}

	private static SeaRouter.Result sail(SeaGraph g, Boat b)
	{
		List<List<SeaGraph.Join>> ends = new ArrayList<>();
		ends.add(g.join(new int[][]{{3285, 3245}}));
		return SeaRouterDiagnostics.search(new SeaRouter(g), new SeaRouter.Rules(b, 50),
			List.of(new SeaRouter.Start(g.join(new int[][]{{3215, 3215}}), 0)), ends, Integer.MAX_VALUE / 2);
	}

	@Test
	void aBoatThatFitsSailsThroughTheCrookedChannel()
	{
		SeaGrid grid = sea(3);
		assertEquals(2, grid.clearanceAt(3246, 3233), "the channel's middle: one tile from the rocks each side");
		SeaGraph plain = SeaGraph.build(grid, List.of());
		assertEquals(-1, sail(plain, boat(BoatType.SKIFF)).cost(0), "no straight link threads the channel");

		List<int[]> found = SeaChannels.find(plain);
		assertTrue(!found.isEmpty());
		SeaGraph g = plain.withChannels(found);
		assertEquals(found.size(), g.channelCount());
		SeaRouter.Result r = sail(g, boat(BoatType.SKIFF));
		assertTrue(r.cost(0) > 0, "a skiff (hard clearance 2) fits the 3-wide channel");
		SeaRouter.Course c = r.course(0);
		for (int i = 0; i < c.tiles.length; i++)
		{
			int x = c.tiles[i] >>> 14;
			int y = c.tiles[i] & 0x3FFF;
			assertTrue(grid.isOcean(x, y), "course on water: " + x + "," + y);
			if (i > 0)
			{
				int px = c.tiles[i - 1] >>> 14;
				int py = c.tiles[i - 1] & 0x3FFF;
				assertTrue(Math.abs(x - px) <= 1 && Math.abs(y - py) <= 1, "course steps a tile at a time");
			}
		}
		assertEquals(3215 << 14 | 3215, c.tiles[0]);
		assertEquals(3285 << 14 | 3245, c.tiles[c.tiles.length - 1]);
		// the real time is the drawn course's length at the boat's speed, give or take the straightening
		int real = TestSeaRoutes.realTime(g, r, 0);
		assertTrue(real >= c.time(boat(BoatType.SKIFF).speed()) - 5, real + " vs " + c.time(boat(BoatType.SKIFF).speed()));
	}

	@Test
	void aChannelTooTightForTheBoatIsNotUsed()
	{
		SeaGraph g = SeaGraph.build(sea(1), List.of());
		g = g.withChannels(SeaChannels.find(g));
		assertEquals(-1, sail(g, boat(BoatType.SKIFF)).cost(0), "a skiff does not fit a channel one tile wide");
		assertTrue(sail(g, boat(BoatType.RAFT)).cost(0) > 0, "a raft (hard clearance 1) does");
	}

	@Test
	void aDeletedChannelIsLeftOutAndTheBoatHasNoWayThrough()
	{
		// to delete a channel: it is named by its ends, either way round
		SeaGraph plain = SeaGraph.build(sea(3), List.of());
		List<int[]> found = SeaChannels.find(plain);
		int[] c = found.get(0);
		int[] back = new int[c.length];
		for (int i = 0; i < c.length; i++)
		{
			back[i] = c[c.length - 1 - i];
		}
		assertEquals(SeaChannelsBuilder.key(c), SeaChannelsBuilder.key(back));
		java.util.Set<String> deleted = new java.util.HashSet<>();
		for (int[] f : found)
		{
			deleted.add(SeaChannelsBuilder.key(f));
		}
		List<int[]> left = SeaChannelsBuilder.without(found, deleted);
		assertTrue(left.isEmpty());
		assertEquals(found.size(), SeaChannelsBuilder.without(found, java.util.Set.of()).size());
		assertEquals(-1, sail(plain.withChannels(left), boat(BoatType.SKIFF)).cost(0), "every channel deleted: no way through");
	}

	@Test
	void theSeaFileKeepsTheChannels() throws Exception
	{
		SeaGrid grid = sea(3);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SeaBitsWriter.write(grid, List.of(), out);
		SeaBits.Data back = SeaBits.read(new ByteArrayInputStream(out.toByteArray()));
		List<int[]> found = SeaChannels.find(SeaGraph.build(grid, List.of()));
		assertEquals(found.size(), back.getChannels().size());
		for (int i = 0; i < found.size(); i++)
		{
			assertArrayEquals(found.get(i), back.getChannels().get(i));
		}
	}

	/** Open water (3200-3263 both ways) with single rocks scattered over it, none on a waypoint's tile. */
	private static SeaGrid rocky()
	{
		SeaMap.Builder b = new SeaMap.Builder();
		for (int x = 3200; x < 3264; x++)
		{
			for (int y = 3200; y < 3264; y++)
			{
				boolean rock = (x * 31 + y * 17) % 53 == 0 && (x % SeaGraph.SPACING != 0 || y % SeaGraph.SPACING != 0);
				if (!rock)
				{
					b.set(x, y, 131);
				}
			}
		}
		SeaMap sea = b.build();
		MovementRules.Flags f = (x, y) -> 0;
		return SeaGridBuilder.build(sea, SeaPieces.build(sea, f), f);
	}

	/**
	 * A straight stretch drawn from its other end can pass other tiles. A channel is sailed both ways, so it keeps the
	 * smaller clearance of the two lines (and the areas of both), not just the last one walked.
	 */
	@Test
	void aChannelKeepsTheNarrowerOfItsTwoWays()
	{
		SeaGraph g = SeaGraph.build(rocky(), List.of());
		int[] there = new int[3];
		int[] back = new int[3];
		int checked = 0;
		for (int a = 0; a < g.waypointCount() && checked < 20; a++)
		{
			for (int b = a + 1; b < g.waypointCount() && checked < 20; b++)
			{
				int ax = g.x(a), ay = g.y(a), bx = g.x(b), by = g.y(b);
				if (Math.max(Math.abs(ax - bx), Math.abs(ay - by)) > 40 || !g.line(ax, ay, bx, by, there)
					|| !g.line(bx, by, ax, ay, back) || there[0] == back[0])
				{
					continue;
				}
				SeaGraph withOne = g.withChannels(List.of(new int[]{ax << 14 | ay, bx << 14 | by}));
				assertEquals(1, withOne.channelCount());
				assertEquals(Math.min(there[0], back[0]), withOne.channelClearance(0), "the narrower way");
				checked++;
			}
		}
		assertTrue(checked > 0, "the test sea has stretches whose two ways differ");
	}
}
