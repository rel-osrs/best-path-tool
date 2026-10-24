package com.bestpath.overlay;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * The walk inside the player's house to the furniture the next step uses. It is only drawn, never used for
 * pathfinding: the route's ticks keep the house's flat cost. The house is an instance the game assembles from room
 * templates; once the player is inside, the client holds the assembled rooms' walls like any loaded scene (the same
 * bits as our map: {@link CollisionFlags}), so the walk is searched on those, with the same step rules as every walk
 * ({@link MovementRules}: 8 directions, no corner cutting), its neighbours tried in the game's own order so the line
 * is the path the player really walks. Pure: scene coordinates in, scene coordinates out.
 */
public final class HousePath
{
	/**
	 * The game's neighbour order, as in {@link com.bestpath.core.path.GamePath}: W, E, S, N, SW, SE, NW, NE. Searching
	 * in this order gives the very path the game walks the player along.
	 */
	private static final int[] DX = {-1, 1, 0, 0, -1, 1, -1, 1};
	private static final int[] DY = {0, 0, -1, 1, -1, -1, 1, 1};

	private HousePath()
	{
	}

	/**
	 * The shortest walk from (sx, sy) to a tile the furniture can be used from: a free tile of its own footprint (a
	 * mounted item on a wall), or one next to the footprint (north, south, east or west) with no wall between them.
	 *
	 * @param f      the scene's flags ({@link CollisionFlags#BLOCKED} outside it)
	 * @param size   the scene's width and height in tiles (104)
	 * @param box    the furniture's footprint {x0, y0, x1, y1} (inclusive)
	 * @return the tiles walked, start first, as {@code x << 16 | y}; empty when the player already stands at it; null
	 *         when no walk reaches it (a closed door, another plane)
	 */
	public static int[] find(MovementRules.Flags f, int size, int sx, int sy, int[] box)
	{
		if (usable(f, sx, sy, box))
		{
			return new int[0];
		}
		int[] prev = new int[size * size];
		Arrays.fill(prev, -2);
		ArrayDeque<Integer> open = new ArrayDeque<>();
		int start = sx * size + sy;
		if (sx < 0 || sy < 0 || sx >= size || sy >= size)
		{
			return null;
		}
		prev[start] = -1;
		open.add(start);
		while (!open.isEmpty())
		{
			int c = open.poll();
			int x = c / size;
			int y = c % size;
			for (int k = 0; k < 8; k++)
			{
				int nx = x + DX[k];
				int ny = y + DY[k];
				if (nx < 0 || ny < 0 || nx >= size || ny >= size || prev[nx * size + ny] != -2
					|| !MovementRules.canStep(f, x, y, DX[k], DY[k]))
				{
					continue;
				}
				int n = nx * size + ny;
				prev[n] = c;
				if (usable(f, nx, ny, box))
				{
					return walk(prev, n, size);
				}
				open.add(n);
			}
		}
		return null;
	}

	/** Whether the furniture can be used from this tile. */
	static boolean usable(MovementRules.Flags f, int x, int y, int[] box)
	{
		if ((f.get(x, y) & CollisionFlags.BLOCKED) != 0)
		{
			return false;
		}
		if (x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3])
		{
			return true;
		}
		// next to the footprint on one side, with no wall on that side of either tile
		if (y >= box[1] && y <= box[3])
		{
			if (x == box[0] - 1)
			{
				return (f.get(x, y) & CollisionFlags.EAST) == 0 && (f.get(x + 1, y) & CollisionFlags.WEST) == 0;
			}
			if (x == box[2] + 1)
			{
				return (f.get(x, y) & CollisionFlags.WEST) == 0 && (f.get(x - 1, y) & CollisionFlags.EAST) == 0;
			}
		}
		if (x >= box[0] && x <= box[2])
		{
			if (y == box[1] - 1)
			{
				return (f.get(x, y) & CollisionFlags.NORTH) == 0 && (f.get(x, y + 1) & CollisionFlags.SOUTH) == 0;
			}
			if (y == box[3] + 1)
			{
				return (f.get(x, y) & CollisionFlags.SOUTH) == 0 && (f.get(x, y - 1) & CollisionFlags.NORTH) == 0;
			}
		}
		return false;
	}

	private static int[] walk(int[] prev, int end, int size)
	{
		int n = 0;
		for (int c = end; c != -1; c = prev[c])
		{
			n++;
		}
		int[] out = new int[n];
		for (int c = end, i = n - 1; c != -1; c = prev[c], i--)
		{
			out[i] = (c / size) << 16 | (c % size);
		}
		return out;
	}
}
