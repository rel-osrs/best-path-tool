package com.bestpath.core.collision;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Makes {@link CollisionMap}s from per-tile flags ({@link MovementRules}), when the data is made.
 */
public final class CollisionMapBuilder
{
	private CollisionMapBuilder()
	{
	}

	/** The regions and the eight regions around each. */
	public static Set<Integer> withNeighbours(Set<Integer> regionIds)
	{
		Set<Integer> out = new HashSet<>();
		for (int r : regionIds)
		{
			int rx = r >> 8;
			int ry = r & 0xFF;
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if (rx + dx >= 0 && rx + dx < 256 && ry + dy >= 0 && ry + dy < 256)
					{
						out.add((rx + dx) << 8 | (ry + dy));
					}
				}
			}
		}
		return out;
	}

	/** Builds a map from per-tile flags. */
	public static CollisionMap build(MovementRules.Flags[] planes, Iterable<Integer> regionIds, int cacheId, int cacheBuild)
	{
		return build(planes, regionIds, cacheId, cacheBuild, null, null);
	}

	/**
	 * Builds a map, taking the blocks of regions not in {@code redo} from
	 * {@code previous} (made from the same regions). A step at a region's edge
	 * reads the next region's tiles, so {@code redo} must hold the changed
	 * regions and the eight around each ({@link #withNeighbours}).
	 */
	public static CollisionMap build(MovementRules.Flags[] planes, Iterable<Integer> regionIds, int cacheId, int cacheBuild,
		CollisionMap previous, Set<Integer> redo)
	{
		TreeMap<Integer, long[]> out = new TreeMap<>();
		for (int regionId : regionIds)
		{
			if (previous != null && !redo.contains(regionId))
			{
				for (int plane = 0; plane < 4; plane++)
				{
					int i = Arrays.binarySearch(previous.keys, CollisionMap.key(regionId, plane));
					if (i >= 0)
					{
						out.put(CollisionMap.key(regionId, plane), previous.blocks[i]); // blocks are never changed after building
					}
				}
				continue;
			}
			int bx = (regionId >> 8) << 6;
			int by = (regionId & 0xFF) << 6;
			for (int plane = 0; plane < 4; plane++)
			{
				MovementRules.Flags f = planes[plane];
				long[] block = new long[CollisionMap.LONGS_PER_BLOCK];
				boolean any = false;
				for (int lx = 0; lx < 64; lx++)
				{
					for (int ly = 0; ly < 64; ly++)
					{
						int x = bx + lx;
						int y = by + ly;
						int n = 0;
						if (MovementRules.canStep(f, x, y, 0, 1))
						{
							n |= CollisionMap.N;
						}
						if (MovementRules.canStep(f, x, y, 1, 0))
						{
							n |= CollisionMap.E;
						}
						if (MovementRules.canStep(f, x, y, 1, 1))
						{
							n |= CollisionMap.NE;
						}
						if (MovementRules.canStep(f, x, y, -1, 1))
						{
							n |= CollisionMap.NW;
						}
						if (n != 0)
						{
							any = true;
							int tile = lx << 6 | ly;
							block[tile >> 4] |= (long) n << ((tile & 15) << 2);
						}
					}
				}
				if (any)
				{
					out.put(CollisionMap.key(regionId, plane), block);
				}
			}
		}
		int[] keys = new int[out.size()];
		long[][] blocks = new long[out.size()][];
		int i = 0;
		for (Map.Entry<Integer, long[]> e : out.entrySet())
		{
			keys[i] = e.getKey();
			blocks[i++] = e.getValue();
		}
		return new CollisionMap(keys, blocks, cacheId, cacheBuild);
	}
}
