package com.bestpath.tool.clue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.clue.ClueCheck.Spot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The clue check's tile rules on a small made-up map. */
class ClueCheckTest
{
	/**
	 * Region (3200,3200) has map data; everything on plane 0 is component 0
	 * except the listed blocked tiles; (3210,3210) is a pocket (component 1),
	 * (3211,3210) hidden (component 2); x = 3230 is sea; planes 1-3 have no floor
	 * except (3205,3205) (component 3). Region (3264,3200) is ignored.
	 */
	private static final class Fake implements ClueCheck.MapView
	{
		final Set<Long> blocked = new HashSet<>();
		final Map<Long, Integer> special = new HashMap<>();

		Fake()
		{
			special.put(k(3210, 3210, 0), 1);
			special.put(k(3211, 3210, 0), 2);
			special.put(k(3205, 3205, 1), 3);
		}

		static long k(int x, int y, int p)
		{
			return ((long) x << 32) | ((long) y << 2) | p;
		}

		@Override
		public boolean hasRegion(int regionId)
		{
			return regionId == ClueCheck.regionId(3200, 3200) || regionId == ClueCheck.regionId(3264, 3200);
		}

		@Override
		public boolean ignored(int regionId)
		{
			return regionId == ClueCheck.regionId(3264, 3200);
		}

		@Override
		public boolean sea(int x, int y, int plane)
		{
			return plane == 0 && x == 3230;
		}

		@Override
		public int flags(int x, int y, int plane)
		{
			if (plane >= 1 && !special.containsKey(k(x, y, plane)))
			{
				return CollisionFlags.NO_FLOOR;
			}
			return blocked.contains(k(x, y, plane)) ? CollisionFlags.OBJECT : 0;
		}

		@Override
		public int component(int x, int y, int plane)
		{
			Integer c = special.get(k(x, y, plane));
			if (c != null)
			{
				return c;
			}
			return plane == 0 && !blocked.contains(k(x, y, plane)) && !sea(x, y, plane) ? 0 : -1;
		}

		@Override
		public boolean pocket(int component)
		{
			return component == 1;
		}

		@Override
		public boolean hidden(int component)
		{
			return component == 2;
		}
	}

	@Test
	void classifiesEveryKindOfTile()
	{
		Fake m = new Fake();
		m.blocked.add(Fake.k(3201, 3201, 0));
		assertEquals(Spot.WALKABLE, ClueCheck.classify(m, 3200, 3200, 0));
		assertEquals(Spot.BLOCKED, ClueCheck.classify(m, 3201, 3201, 0));
		assertEquals(Spot.POCKET, ClueCheck.classify(m, 3210, 3210, 0));
		assertEquals(Spot.HIDDEN, ClueCheck.classify(m, 3211, 3210, 0));
		assertEquals(Spot.AT_SEA, ClueCheck.classify(m, 3230, 3210, 0));
		assertEquals(Spot.NO_FLOOR, ClueCheck.classify(m, 3200, 3200, 1));
		assertEquals(Spot.WALKABLE, ClueCheck.classify(m, 3205, 3205, 1));
		assertEquals(Spot.IGNORED, ClueCheck.classify(m, 3270, 3210, 0));
		assertEquals(Spot.OFF_MAP, ClueCheck.classify(m, 2000, 9000, 0));
	}

	@Test
	void nearestStandableSkipsPocketsAndPrefersTheStraightNeighbour()
	{
		Fake m = new Fake();
		// a 3x3 block of objects round 3220,3220: the nearest standable tiles are 2 away;
		// 3222,3220 (straight east) beats 3222,3222 (diagonal)
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				m.blocked.add(Fake.k(3220 + dx, 3220 + dy, 0));
			}
		}
		int[] n = ClueCheck.nearestStandable(m, 3220, 3220, 0, 10);
		assertEquals(2, n[3]);
		assertEquals(0, Math.min(Math.abs(n[0] - 3220), Math.abs(n[1] - 3220))); // a straight neighbour, not a corner
		assertArrayEquals(new int[]{3218, 3220, 0, 2}, n); // the lowest x among the equally near
		// a standable tile is its own nearest
		assertArrayEquals(new int[]{3200, 3200, 0, 0}, ClueCheck.nearestStandable(m, 3200, 3200, 0, 10));
		// the pocket tile is not standable: its neighbour is
		assertEquals(1, ClueCheck.nearestStandable(m, 3210, 3210, 0, 10)[3]);
		// nothing standable near the sea column's far side within radius 0
		assertNull(ClueCheck.nearestStandable(m, 3230, 3210, 0, 0));
		assertNull(ClueCheck.nearestStandable(m, 2000, 9000, 0, 3));
	}

	@Test
	void anotherPlaneIsOnlyAHint()
	{
		Fake m = new Fake();
		assertEquals(0, ClueCheck.otherStandablePlane(m, 3205, 3205, 2)); // the lowest plane first
		m.blocked.add(Fake.k(3205, 3205, 0));
		assertEquals(1, ClueCheck.otherStandablePlane(m, 3205, 3205, 2));
		assertEquals(0, ClueCheck.otherStandablePlane(m, 3200, 3200, 1));
		assertEquals(-1, ClueCheck.otherStandablePlane(m, 2000, 9000, 0));
	}

	@Test
	void aPlaneIsSuggestedOnlyWhenItIsTheOnlyOneWithFloor()
	{
		Fake m = new Fake();
		// 3205,3205 is standable on planes 0 and 1: two choices, so nothing is suggested for plane 2
		assertEquals(-1, ClueCheck.onlyOtherStandablePlane(m, 3205, 3205, 2));
		assertNull(ClueCheck.suggest(m, 3205, 3205, 2));
		// with plane 0 blocked there, plane 1 is the only floor: suggested
		m.blocked.add(Fake.k(3205, 3205, 0));
		assertEquals(1, ClueCheck.onlyOtherStandablePlane(m, 3205, 3205, 2));
		ClueCheck.Suggestion s = ClueCheck.suggest(m, 3205, 3205, 2);
		assertEquals(3205, s.x);
		assertEquals(3205, s.y);
		assertEquals(1, s.plane);
		assertTrue(s.reason.startsWith("plane 1"));
		// a tile that has floor on its own plane gets no suggestion, even when blocked
		assertNull(ClueCheck.suggest(m, 3205, 3205, 0));
		assertNull(ClueCheck.suggest(m, 3200, 3200, 0));
		// floor on plane 0 only: plane 0 is suggested for plane 2; blocked there too: nothing to suggest
		assertEquals(0, ClueCheck.suggest(m, 3201, 3201, 2).plane);
		m.blocked.add(Fake.k(3201, 3201, 0));
		assertNull(ClueCheck.suggest(m, 3201, 3201, 2));
		assertEquals(-1, ClueCheck.onlyOtherStandablePlane(m, 2000, 9000, 0));
	}

	@Test
	void besideABlockedSpotOnlyForDigAndEmoteSpotsOneTileAway()
	{
		assertTrue(ClueCheck.besideBlockedSpot("coord", Spot.BLOCKED, 1));
		assertFalse(ClueCheck.besideBlockedSpot("coord", Spot.BLOCKED, 2));
		assertFalse(ClueCheck.besideBlockedSpot("coord", Spot.BLOCKED, -1));
		assertFalse(ClueCheck.besideBlockedSpot("coord", Spot.WALKABLE, 1)); // a free spot must be stood on
		assertFalse(ClueCheck.besideBlockedSpot("coord", Spot.POCKET, 1));
		assertFalse(ClueCheck.besideBlockedSpot("npc", Spot.BLOCKED, 1)); // NPCs and objects have their own rule
	}

	@Test
	void distanceToAFootprintCountsFromItsNearestTile()
	{
		// the Tree Gnome Village spirit tree: 4x4 at 2543-2546, 3168-3171; the clue names 2544,3170
		assertEquals(0, ClueCheck.distanceToFootprint(2544, 3170, 2543, 3168, 2546, 3171));
		assertEquals(1, ClueCheck.distanceToFootprint(2542, 3170, 2543, 3168, 2546, 3171)); // west side, next to it
		assertEquals(1, ClueCheck.distanceToFootprint(2547, 3172, 2543, 3168, 2546, 3171)); // north-east corner, diagonal
		assertEquals(2, ClueCheck.distanceToFootprint(2541, 3170, 2543, 3168, 2546, 3171));
		assertEquals(3, ClueCheck.distanceToFootprint(2545, 3165, 2543, 3168, 2546, 3171));
		// a 1x1 object: the plain ring distance
		assertEquals(2, ClueCheck.distanceToFootprint(3200, 3202, 3200, 3200, 3200, 3200));
	}

	@Test
	void closeEnoughDependsOnTheKindOfTarget()
	{
		assertTrue(ClueCheck.closeEnough("coord", 0));
		assertFalse(ClueCheck.closeEnough("coord", 1)); // a dig spot must be the tile itself
		assertTrue(ClueCheck.closeEnough("loc", 1));
		assertFalse(ClueCheck.closeEnough("loc", 2));
		assertTrue(ClueCheck.closeEnough("stash", 1));
		assertTrue(ClueCheck.closeEnough("npc", 2)); // across a counter
		assertFalse(ClueCheck.closeEnough("npc", 3));
		assertFalse(ClueCheck.closeEnough("npc", -1)); // no route
	}

	@Test
	void causes()
	{
		assertNull(ClueCheck.cause("coord", Spot.WALKABLE, 0, 0));
		assertNull(ClueCheck.cause("loc", Spot.BLOCKED, 1, 1));
		// a blocked dig/emote spot is done from the next tile
		assertNull(ClueCheck.cause("coord", Spot.BLOCKED, 1, 1));
		assertTrue(ClueCheck.cause("coord", Spot.BLOCKED, 1, 2).startsWith("dig/emote tile is blocked"));
		assertTrue(ClueCheck.reached("coord", Spot.BLOCKED, 1));
		assertFalse(ClueCheck.reached("coord", Spot.WALKABLE, 1)); // a free spot must be stood on
		assertTrue(ClueCheck.reached("coord", Spot.WALKABLE, 0));
		assertFalse(ClueCheck.reached("coord", Spot.BLOCKED, 2));
		assertTrue(ClueCheck.reached("npc", Spot.BLOCKED, 2));
		assertTrue(ClueCheck.cause("coord", Spot.WALKABLE, 0, -1).startsWith("no route"));
		assertTrue(ClueCheck.cause("npc", Spot.BLOCKED, -1, -1).startsWith("blocked, nothing standable"));
		assertTrue(ClueCheck.cause("coord", Spot.POCKET, 0, -1).contains("pocket"));
		assertTrue(ClueCheck.cause("coord", Spot.POCKET, 0, 0).contains("left out of the plugin"));
		assertTrue(ClueCheck.cause("npc", Spot.OFF_MAP, -1, -1).startsWith("off the walking map"));
		assertTrue(ClueCheck.cause("coord", Spot.AT_SEA, -1, -1).startsWith("at sea"));
		assertTrue(ClueCheck.cause("npc", Spot.WALKABLE, 0, 5).startsWith("route stops 5 tiles away"));
	}
}
