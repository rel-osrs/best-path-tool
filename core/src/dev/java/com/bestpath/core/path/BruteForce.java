package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionMap;

/**
 * The brute-force checker: a plain tile-by-tile Dijkstra that does not use
 * the coarse graph at all. Slow on long routes, but simple enough to trust,
 * so the real pathfinder can be checked against it.
 */
public final class BruteForce
{
	private BruteForce()
	{
	}

	public static Route find(CollisionMap map, PathQuery q)
	{
		return find(map, q, null);
	}

	/** As above, with these walls for transports used from beside their ends ({@link Transport#usedFromBeside()}). */
	public static Route find(CollisionMap map, PathQuery q, LineOfSight.WallFlags walls)
	{
		return TileSearch.search(map, q, TileSearch.ZERO, new Beside(map, walls));
	}
}
