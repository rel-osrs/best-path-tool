package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The plane a world-map tile means ({@link PluginRouter#mapPlane(CoarseGraph, int, int)}): a destination set from the
 * world map used to get plane 0 where the ground is at plane 1.
 */
class MapPlaneTest
{
	private static final int A = 50 << 8 | 50;

	private static MovementRules.Flags ground(java.util.function.IntPredicate atX)
	{
		return (x, y) -> atX.test(x) ? 0 : CollisionFlags.BLOCKED;
	}

	/** Region (50,50), x and y 3200-3263: ground on plane 0 west of 3220, plane 1 from 3240, plane 2 at 3230-3234. */
	private static CoarseGraph world()
	{
		MovementRules.Flags[] planes = {
			ground(x -> x < 3220),
			ground(x -> x >= 3240),
			ground(x -> x >= 3230 && x <= 3234),
			ground(x -> false),
		};
		return CoarseGraph.build(CollisionMapBuilder.build(planes, List.of(A), 0, 0));
	}

	@Test
	void groundFloorWhereThereIsGround()
	{
		assertEquals(0, PluginRouter.mapPlane(world(), 3210, 3230));
	}

	@Test
	void groundFloorWhenItHasGroundWithinRangeEvenIfAnUpperFloorIsNearer()
	{
		// plane 0's ground 6 tiles away, plane 2's 5: still the ground floor (a table in a two-storey house)
		assertEquals(0, PluginRouter.mapPlane(world(), 3225, 3230));
	}

	@Test
	void anUpperPlaneWhenTheGroundFloorHasNothingNear()
	{
		// the Sisterhood Sanctuary case: nothing on plane 0, the ground on plane 1
		assertEquals(1, PluginRouter.mapPlane(world(), 3245, 3230));
	}

	@Test
	void theNearestUpperPlaneTheLowestOnATie()
	{
		assertEquals(2, PluginRouter.mapPlane(world(), 3236, 3230)); // plane 2 two tiles away, plane 1 four
		assertEquals(1, PluginRouter.mapPlane(world(), 3237, 3230)); // both three tiles away
	}

	@Test
	void groundFloorWhenNoPlaneHasGround()
	{
		assertEquals(0, PluginRouter.mapPlane(world(), 3100, 3100));
	}

	@Test
	void aboveTheMainMapOnlyTheTilesOwnAreaCounts()
	{
		// regions (50,70) and (51,70), y 4480-4543: plane 0's ground is only in the next area (x from 3264), plane 1's
		// in the tile's own one (x up to 3255)
		int own = 50 << 8 | 70;
		int next = 51 << 8 | 70;
		MovementRules.Flags[] planes = {ground(x -> x >= 3264), ground(x -> x <= 3255), ground(x -> false), ground(x -> false)};
		CoarseGraph g = CoarseGraph.build(CollisionMapBuilder.build(planes, List.of(own, next), 0, 0));
		assertEquals(1, PluginRouter.mapPlane(g, 3262, 4500));
	}
}
