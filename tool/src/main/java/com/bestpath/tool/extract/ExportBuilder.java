package com.bestpath.tool.extract;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds the data that ships with the plugin. Differences from what the tool
 * shows: closed-off pockets and hidden sub-regions are left out, and
 * ignored 64x64 regions are left out entirely
 * (not stored at all, and no step leads into them). All of it stays in the
 * tool for troubleshooting.
 */
public final class ExportBuilder
{
	private ExportBuilder()
	{
	}

	/** The full movement data, as shown in the tool (pockets included). */
	public static CollisionMap fullCollision(FlagGrid grid, Set<Integer> regionIds, int cacheId, int build)
	{
		return CollisionMapBuilder.build(planes(grid, null, null, Collections.emptySet()), regionIds, cacheId, build);
	}

	/** As above, redoing only the regions in {@code redo} and taking the rest from {@code previous}. */
	public static CollisionMap fullCollision(FlagGrid grid, Set<Integer> regionIds, int cacheId, int build,
		CollisionMap previous, Set<Integer> redo)
	{
		return CollisionMapBuilder.build(planes(grid, null, null, Collections.emptySet()), regionIds, cacheId, build, previous, redo);
	}

	/**
	 * The movement data for the plugin: tiles of excluded sub-regions (closed-off
	 * pockets and sub-regions hidden in the tool) become unwalkable, so they cost no
	 * space; ignored regions are dropped.
	 */
	public static CollisionMap exportCollision(FlagGrid grid, CoarseGraph fullGraph, boolean[] excluded, Set<Integer> ignoredRegions,
		Set<Integer> regionIds, int cacheId, int build)
	{
		Set<Integer> kept = new TreeSet<>(regionIds);
		kept.removeAll(ignoredRegions);
		return CollisionMapBuilder.build(planes(grid, fullGraph, excluded, ignoredRegions), kept, cacheId, build);
	}

	private static MovementRules.Flags[] planes(FlagGrid grid, CoarseGraph g, boolean[] pockets, Set<Integer> ignored)
	{
		MovementRules.Flags[] out = new MovementRules.Flags[4];
		for (int p = 0; p < 4; p++)
		{
			int plane = p;
			if (pockets == null && ignored.isEmpty())
			{
				out[p] = grid.plane(p);
				continue;
			}
			out[p] = (x, y) ->
			{
				if (!ignored.isEmpty() && ignored.contains((x >> 6) << 8 | (y >> 6)))
				{
					return CollisionFlags.NO_FLOOR;
				}
				if (pockets != null)
				{
					int c = g.componentAt(x, y, plane);
					if (c >= 0 && pockets[c])
					{
						return CollisionFlags.NO_FLOOR;
					}
				}
				return grid.get(x, y, plane);
			};
		}
		return out;
	}
}
