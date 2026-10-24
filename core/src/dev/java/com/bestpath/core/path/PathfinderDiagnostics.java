package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionMap;

/**
 * What development code reads from a {@link Pathfinder} that the plugin itself never needs: the last search's
 * timings and the parts it entered, raw search speed, and the tiles beside a solid transport end.
 */
public final class PathfinderDiagnostics
{
	private PathfinderDiagnostics()
	{
	}

	/** The walking map the search uses. */
	public static CollisionMap map(Pathfinder pf)
	{
		return pf.map;
	}

	/** Walks kept as found because their game shape would cost a different number of ticks; should stay 0. */
	public static int walksKeptAsFound(Pathfinder pf)
	{
		synchronized (pf)
		{
			return pf.walksKept;
		}
	}

	/**
	 * Where a transport end on this tile is used from and stepped off to when the transport is used from
	 * beside its ends ({@link Transport#usedFromBeside()}, e.g. a fairy ring's middle): {tiles it is boarded
	 * from, tiles a player who lands there steps off to}, packed ({@link Tiles}). Both empty when the tile can
	 * be walked on (then it is used as usual); both empty too when nothing beside it can be walked on.
	 */
	public static int[][] besideTiles(Pathfinder pf, int x, int y, int plane)
	{
		synchronized (pf)
		{
			int t = Tiles.pack(x, y, plane);
			return new int[][]{pf.beside.boardTiles(t).clone(), pf.beside.leaveTiles(t).clone()};
		}
	}

	/** The fast tile search with no bound at all (plain Dijkstra), to measure its raw speed. */
	public static Route findWithoutBounds(Pathfinder pf, PathQuery q)
	{
		synchronized (pf)
		{
			return pf.fast.search(q, (ci, l) -> 0, pf.beside, null);
		}
	}

	/** The components (ids) the last search's tile stage entered. */
	public static int[] lastSearchedComponents(Pathfinder pf)
	{
		synchronized (pf)
		{
			return pf.lastSearched.clone();
		}
	}

	/** Nanoseconds of the last search in its parts: reachability, coarse, tiles, walk shapes (game's shape). */
	public static long[] lastPhases(Pathfinder pf)
	{
		synchronized (pf)
		{
			return pf.lastPhases.clone();
		}
	}
}
