package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Transport ends on objects, such as fairy rings. A fairy ring stop
 * is the ring's own tile, the middle of the ring, and that tile is solid on our map (the "Fairy ring" object,
 * e.g. 29495, is 1x1 and solid in the cache). For a transport marked {@link Transport#usedFromBeside()}, an end
 * on a tile nobody can walk onto works like this:
 * <ul>
 * <li><b>Boarding:</b> the player uses it from one of the four tiles beside the end (north, east, south,
 * west) that can be walked on and has no wall between it and the end. Four sides, not the corners: the cache
 * gives every fairy ring object an access mask of 0 (usable from all four sides), and the game uses an
 * object from beside one of its sides. No extra ticks: the ride's own ticks cover using it.</li>
 * <li><b>Arriving:</b> the player lands on the end's own tile (players stand in the centre of
 * the ring after teleporting) and walks off it: one step (1 tick, plus the door cost through a door) to any
 * of the eight tiles beside it that walking would allow if the end's tile were open (straight: the tile can
 * be walked on and no wall between; diagonal: both ways round the corner are open).</li>
 * </ul>
 * "Can be walked on" means what the walking map says (at least one step from the tile). Walls are read from
 * the wall flags the search was given (only the four edge walls are used). An end on a tile that can be walked
 * on is used as usual, so nothing changes for any other transport. Results are cached per tile (exact, a few
 * tiles in all).
 */
final class Beside
{
	/** Directions as in the searches: north, north-east, east, south-east, south, south-west, west, north-west. */
	static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
	static final int[] DY = {1, 1, 0, -1, -1, -1, 0, 1};
	/** Ticks of the step off the end's tile, as the coarse search counts it (a lower bound: doors may add more). */
	static final int STEP_OFF = 1;
	private static final int[] NONE = new int[0];

	/** No walls known: only the walking map decides. */
	static final LineOfSight.WallFlags NO_WALLS = (x, y, p) -> 0;

	private final CollisionMap map;
	private final LineOfSight.WallFlags walls;
	private final Map<Integer, int[]> board = new HashMap<>();
	private final Map<Integer, Integer> leave = new HashMap<>();
	private final Map<Integer, int[]> leaveTiles = new HashMap<>();

	Beside(CollisionMap map, LineOfSight.WallFlags walls)
	{
		this.map = map;
		this.walls = walls == null ? NO_WALLS : walls;
	}

	/** Can the tile be walked on (any step from it)? */
	boolean walkable(int x, int y, int p)
	{
		for (int d = 0; d < 8; d++)
		{
			if (map.canStep(x, y, p, DX[d], DY[d]))
			{
				return true;
			}
		}
		return false;
	}

	/** The tiles a transport end on {@code tile} is used from; empty when the tile itself can be walked on. */
	synchronized int[] boardTiles(int tile)
	{
		int[] b = board.get(tile);
		if (b == null)
		{
			int x = Tiles.x(tile);
			int y = Tiles.y(tile);
			int p = Tiles.plane(tile);
			b = NONE;
			if (!walkable(x, y, p))
			{
				int[] found = new int[4];
				int n = 0;
				for (int d = 0; d < 8; d += 2)
				{
					if (walkable(x + DX[d], y + DY[d], p) && !wall(x, y, p, DX[d], DY[d]))
					{
						found[n++] = Tiles.pack(x + DX[d], y + DY[d], p);
					}
				}
				b = Arrays.copyOf(found, n);
			}
			board.put(tile, b);
		}
		return b;
	}

	/** The directions (bit d = direction d) a player who landed on {@code tile} steps off it; 0 when it can be walked on. */
	synchronized int leaveMask(int tile)
	{
		Integer m = leave.get(tile);
		if (m == null)
		{
			int x = Tiles.x(tile);
			int y = Tiles.y(tile);
			int p = Tiles.plane(tile);
			int mask = 0;
			if (!walkable(x, y, p))
			{
				for (int d = 0; d < 8; d++)
				{
					int dx = DX[d];
					int dy = DY[d];
					boolean ok;
					if ((d & 1) == 0)
					{
						ok = walkable(x + dx, y + dy, p) && !wall(x, y, p, dx, dy);
					}
					else
					{
						// diagonal: both tiles round the corner open from here, and both onward steps legal
						ok = walkable(x + dx, y + dy, p) && walkable(x + dx, y, p) && walkable(x, y + dy, p)
							&& !wall(x, y, p, dx, 0) && !wall(x, y, p, 0, dy)
							&& map.canStep(x + dx, y, p, 0, dy) && map.canStep(x, y + dy, p, dx, 0);
					}
					if (ok)
					{
						mask |= 1 << d;
					}
				}
			}
			m = mask;
			leave.put(tile, m);
		}
		return m;
	}

	/** The tiles a player who landed on {@code tile} steps off to (see {@link #leaveMask}). */
	synchronized int[] leaveTiles(int tile)
	{
		int[] out = leaveTiles.get(tile);
		if (out == null)
		{
			int m = leaveMask(tile);
			out = new int[Integer.bitCount(m)];
			int n = 0;
			for (int d = 0; d < 8; d++)
			{
				if ((m & 1 << d) != 0)
				{
					out[n++] = Tiles.pack(Tiles.x(tile) + DX[d], Tiles.y(tile) + DY[d], Tiles.plane(tile));
				}
			}
			leaveTiles.put(tile, out);
		}
		return out;
	}

	synchronized void clear()
	{
		board.clear();
		leave.clear();
		leaveTiles.clear();
	}

	/** A wall on the edge between (x, y) and its straight neighbour (x + dx, y + dy), from either side? */
	private boolean wall(int x, int y, int p, int dx, int dy)
	{
		int here = walls.get(x, y, p);
		int there = walls.get(x + dx, y + dy, p);
		if (dx == 1)
		{
			return (here & CollisionFlags.EAST) != 0 || (there & CollisionFlags.WEST) != 0;
		}
		if (dx == -1)
		{
			return (here & CollisionFlags.WEST) != 0 || (there & CollisionFlags.EAST) != 0;
		}
		if (dy == 1)
		{
			return (here & CollisionFlags.NORTH) != 0 || (there & CollisionFlags.SOUTH) != 0;
		}
		return (here & CollisionFlags.SOUTH) != 0 || (there & CollisionFlags.NORTH) != 0;
	}
}
