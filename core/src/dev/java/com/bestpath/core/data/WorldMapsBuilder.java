package com.bestpath.core.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes {@link WorldMaps} when the data is made: collects the cache's blocks and merges them into rectangles. Blocks
 * that clash within one map (two game places drawn on one spot, or one game tile and plane drawn at two spots) are
 * left out and listed in {@link #problems()}.
 */
public final class WorldMapsBuilder
{
	private final List<String> names = new ArrayList<>();
	/** Per map: 8x8 block key (shift, planes) to the set of game block corners (x / 8, y / 8). */
	private final List<Map<List<Integer>, Set<Long>>> blocks = new ArrayList<>();
	private final List<String> problems = new ArrayList<>();

	/** Adds the next map (ids count up from 0 in the order maps are added). */
	public int addMap(String name)
	{
		names.add(name);
		blocks.add(new LinkedHashMap<>());
		return names.size() - 1;
	}

	/**
	 * A block of a map: {@code size} x {@code size} tiles (8 or 64) at game x, y drawn at drawX, drawY, planes
	 * minPlane to minPlane + planes - 1 (kept within 0 to 3).
	 */
	public void add(int map, int gameX, int gameY, int size, int drawX, int drawY, int minPlane, int planes)
	{
		if (size % 8 != 0 || gameX % 8 != 0 || gameY % 8 != 0 || drawX % 8 != 0 || drawY % 8 != 0)
		{
			throw new IllegalArgumentException("blocks must be whole 8x8 zones");
		}
		int lo = Math.max(0, minPlane);
		int hi = Math.min(3, minPlane + planes - 1);
		if (hi < lo)
		{
			return;
		}
		List<Integer> key = Arrays.asList(drawX - gameX, drawY - gameY, lo, hi);
		Set<Long> set = blocks.get(map).computeIfAbsent(key, k -> new HashSet<>());
		for (int x = gameX; x < gameX + size; x += 8)
		{
			for (int y = gameY; y < gameY + size; y += 8)
			{
				set.add(zone(x / 8, y / 8));
			}
		}
	}

	/** What was left out (empty when every block fits). */
	public List<String> problems()
	{
		return Collections.unmodifiableList(problems);
	}

	public WorldMaps build()
	{
		int[][] out = new int[names.size()][];
		for (int m = 0; m < names.size(); m++)
		{
			out[m] = merge(m, blocks.get(m));
		}
		return new WorldMaps(names.toArray(new String[0]), out);
	}

	private int[] merge(int map, Map<List<Integer>, Set<Long>> groups)
	{
		// clashes first: per drawn block the game block it shows, per game block and plane the drawn block
		Map<Long, Long> drawnToGame = new HashMap<>();
		Map<Long, Long> gameToDrawn = new HashMap<>();
		List<int[]> rects = new ArrayList<>();
		for (Map.Entry<List<Integer>, Set<Long>> e : groups.entrySet())
		{
			int sx = e.getKey().get(0);
			int sy = e.getKey().get(1);
			int lo = e.getKey().get(2);
			int hi = e.getKey().get(3);
			Set<Long> cells = new HashSet<>();
			List<Long> sorted = new ArrayList<>(e.getValue());
			Collections.sort(sorted);
			for (long z : sorted)
			{
				int zx = zoneX(z);
				int zy = zoneY(z);
				long drawnZone = zone(zx + sx / 8, zy + sy / 8);
				Long shown = drawnToGame.get(drawnZone);
				if (shown != null && shown != z)
				{
					problems.add(names.get(map) + ": game " + zx * 8 + ", " + zy * 8 + " and " + zoneX(shown) * 8 + ", "
						+ zoneY(shown) * 8 + " both drawn at " + (zx * 8 + sx) + ", " + (zy * 8 + sy) + " (left out the first)");
					continue;
				}
				boolean clash = false;
				for (int p = lo; p <= hi; p++)
				{
					Long at = gameToDrawn.get(z * 4 + p);
					if (at != null && at != drawnZone)
					{
						problems.add(names.get(map) + ": game " + zx * 8 + ", " + zy * 8 + ", plane " + p
							+ " drawn at two spots (left out the second)");
						clash = true;
						break;
					}
				}
				if (clash)
				{
					continue;
				}
				drawnToGame.put(drawnZone, z);
				for (int p = lo; p <= hi; p++)
				{
					gameToDrawn.put(z * 4 + p, drawnZone);
				}
				cells.add(z);
			}
			// greedy rectangles: from the lowest row's first block, as wide as the row goes, then as high as whole rows go
			Set<Long> used = new HashSet<>();
			for (long z : sorted)
			{
				if (!cells.contains(z) || used.contains(z))
				{
					continue;
				}
				int zx = zoneX(z);
				int zy = zoneY(z);
				int w = 1;
				while (cells.contains(zone(zx + w, zy)) && !used.contains(zone(zx + w, zy)))
				{
					w++;
				}
				int h = 1;
				while (wholeRow(cells, used, zx, zy + h, w))
				{
					h++;
				}
				for (int dx = 0; dx < w; dx++)
				{
					for (int dy = 0; dy < h; dy++)
					{
						used.add(zone(zx + dx, zy + dy));
					}
				}
				rects.add(new int[] {zx * 8, zy * 8, w * 8, h * 8, sx, sy, lo, hi});
			}
		}
		int[] flat = new int[rects.size() * WorldMaps.STRIDE];
		for (int i = 0; i < rects.size(); i++)
		{
			System.arraycopy(rects.get(i), 0, flat, i * WorldMaps.STRIDE, WorldMaps.STRIDE);
		}
		return flat;
	}

	private static boolean wholeRow(Set<Long> cells, Set<Long> used, int zx, int zy, int w)
	{
		for (int dx = 0; dx < w; dx++)
		{
			long z = zone(zx + dx, zy);
			if (!cells.contains(z) || used.contains(z))
			{
				return false;
			}
		}
		return true;
	}

	/** A block corner as one number, sorted by row (y) then x. */
	private static long zone(int zx, int zy)
	{
		return (long) zy << 20 | zx;
	}

	private static int zoneX(long z)
	{
		return (int) (z & 0xFFFFF);
	}

	private static int zoneY(long z)
	{
		return (int) (z >> 20);
	}
}
