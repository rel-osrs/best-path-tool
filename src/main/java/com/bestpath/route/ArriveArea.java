package com.bestpath.route;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.path.Tiles;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The tiles around a route's end that count as arrived, so the route clears itself there (the range is a player
 * setting): each tile with how many legal steps (walls, no corner cutting) reach the destination or the route's last
 * tile from it, up to {@link #MAX_STEPS}. Steps, not tiles apart, so a tile behind a wall never counts. Worked out once
 * per route on the route worker; immutable, read on the client thread.
 *
 * <p>Why steps from the route's last tile too: a destination the player can't stand on (a table, a statue, scenery)
 * has no tile one legal step from it, so counting from the destination alone the player would never arrive.
 */
public final class ArriveArea
{
	/** The largest arrival range the setting allows (tiles walked). */
	public static final int MAX_STEPS = 20;

	/** Sorted packed tiles. */
	private final int[] tiles;
	/** Steps from each tile (same order). */
	private final byte[] steps;

	/** Sorted packed tiles and the steps from each, same order (tests make areas with it too). */
	ArriveArea(int[] tiles, byte[] steps)
	{
		this.tiles = tiles;
		this.steps = steps;
	}

	/**
	 * Every tile on the end's plane from which at most {@code maxSteps} legal steps reach {@code destination} or
	 * {@code lastTile} (-1 = none); both themselves at 0 steps.
	 */
	public static ArriveArea walk(CollisionMap map, int destination, int lastTile, int maxSteps)
	{
		Map<Integer, Integer> found = new HashMap<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int seed : new int[]{destination, lastTile})
		{
			if (seed != -1 && found.putIfAbsent(seed, 0) == null)
			{
				queue.add(seed);
			}
		}
		while (!queue.isEmpty())
		{
			int t = queue.poll();
			int d = found.get(t);
			if (d >= maxSteps)
			{
				continue;
			}
			int x = Tiles.x(t);
			int y = Tiles.y(t);
			int p = Tiles.plane(t);
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					// the tile one step back from t: can the player step from it to t?
					if ((dx != 0 || dy != 0) && map.canStep(x - dx, y - dy, p, dx, dy))
					{
						int from = Tiles.pack(x - dx, y - dy, p);
						if (found.putIfAbsent(from, d + 1) == null)
						{
							queue.add(from);
						}
					}
				}
			}
		}
		int[] t = new int[found.size()];
		int n = 0;
		for (int k : found.keySet())
		{
			t[n++] = k;
		}
		Arrays.sort(t);
		byte[] s = new byte[t.length];
		for (int i = 0; i < t.length; i++)
		{
			s[i] = (byte) (int) found.get(t[i]);
		}
		return new ArriveArea(t, s);
	}

	/** Legal steps from this packed tile to the route's end, or -1 when more than this area holds. */
	public int steps(int packed)
	{
		int i = Arrays.binarySearch(tiles, packed);
		return i < 0 ? -1 : steps[i];
	}
}
