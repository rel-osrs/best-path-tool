package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The game's own click pathing, on our movement data: a breadth-first
 * search from the player's tile inside the 128 x 128 tiles around it (the
 * player at the north-east tile of the middle: x - 64 to x + 63), trying
 * the neighbours west, east, south, north, south-west, south-east,
 * north-west, north-east in that order; each tile keeps the
 * neighbour it was first found from, and the path is followed back from the
 * clicked tile. Closed doors block it: every step through a door is left out
 * (doors are shut in the game until opened).
 *
 * <p>What this order gives: of all the equally short ways, the one whose
 * directions, read from the start, come first in that order (fewest-tiles
 * first, then the west/east/south/north/... order step by step). Two things
 * follow that the walks rely on: the game's path to a tile on another game
 * path (from the same start) is the first part of it, and the path from a
 * tile partway along a game path to its end is the rest of it.
 *
 * <p>The game also stops a click after the path's 25th corner
 * ({@link #CHECKPOINTS}); clicking again from there continues on the same
 * tiles (the rest of a game path is the game path), so this only matters
 * when replaying clicks, never for the shape.
 *
 * <p>Not thread-safe: one search at a time (it keeps its arrays between
 * searches, and the step bits of the regions it last read).
 */
public final class GamePath
{
	/** Side of the searched square. */
	public static final int SIZE = 128;
	/** The player's place in it: {@code SIZE / 2} from the west and south edges. */
	public static final int HALF = SIZE / 2;
	/** The game walks a click's path only as far as its 25th corner ("checkpoint tile"). */
	public static final int CHECKPOINTS = 25;

	/** The game's neighbour order: W, E, S, N, SW, SE, NW, NE. */
	private static final int[] DX = {-1, 1, 0, 0, -1, 1, -1, 1};
	private static final int[] DY = {0, 0, -1, 1, -1, -1, 1, 1};
	/** The same directions as {@link DoorMap} numbers them (N=0, NE, E, SE, S, SW, W, NW=7). */
	private static final int[] DOOR_DIR = {6, 2, 4, 0, 5, 3, 7, 1};
	/** Step bits are kept for a border of one tile around the square (steps west and south read the neighbour's). */
	private static final int W = SIZE + 2;
	/** A search reads up to 16 regions (3 x 3 mostly); the last 32 are kept (about 130 KB). */
	private static final int REGIONS_KEPT = 32;

	private final CollisionMap map;
	private final DoorMap doors;
	/** Steps from the start per square tile (index lx * SIZE + ly), -1 = not reached. */
	private final int[] dist = new int[SIZE * SIZE];
	/** Per reached tile: the direction (0-7, game order) it was entered by; -1 for the start. */
	private final byte[] via = new byte[SIZE * SIZE];
	private final int[] queue = new int[SIZE * SIZE];
	/** Step bits (N, E, NE, NW) of the square and its border, index (lx + 1) * W + (ly + 1). */
	private final byte[] bits = new byte[W * W];
	/** Door bits (DoorMap directions) of the square's tiles, index lx * SIZE + ly. */
	private final byte[] doorBits = new byte[SIZE * SIZE];
	/** Recently read step and door bits per cell (regionId << 2 | plane); empty = nothing there. */
	private final Map<Integer, byte[]> regionBits = new LinkedHashMap<Integer, byte[]>(48, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> e)
		{
			return size() > REGIONS_KEPT;
		}
	};
	private final Map<Integer, byte[]> regionDoors = new LinkedHashMap<Integer, byte[]>(48, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> e)
		{
			return size() > REGIONS_KEPT;
		}
	};

	private int baseX;
	private int baseY;
	private int plane;

	/**
	 * @param doors steps through these doors are never taken (shut); {@link DoorMap#none()} for none
	 */
	public GamePath(CollisionMap map, DoorMap doors)
	{
		this.map = map;
		this.doors = doors == null ? DoorMap.none() : doors;
	}

	/** The doors it treats as shut. */
	public DoorMap getDoors()
	{
		return doors;
	}

	/** About how many bytes it keeps: its arrays and the step and door bits of the regions it read last. */
	public long cacheBytes()
	{
		long n = 4L * dist.length + via.length + 4L * queue.length + bits.length + doorBits.length;
		for (byte[] b : regionBits.values())
		{
			n += 16 + b.length + 64;
		}
		for (byte[] b : regionDoors.values())
		{
			n += 16 + b.length + 64;
		}
		return n;
	}

	/**
	 * Searches from {@code from} (packed, {@link Tiles}), at most {@code maxSteps} steps far (the search stops
	 * there; tiles further away count as not reached). The paths it finds do not depend on {@code maxSteps}.
	 */
	public void search(int from, int maxSteps)
	{
		search(from, maxSteps, -1);
	}

	/**
	 * As {@link #search(int, int)}, and stops as soon as {@code goal} is found (its path is then known; other
	 * tiles may be left out).
	 */
	public void search(int from, int maxSteps, int goal)
	{
		load(from);
		Arrays.fill(dist, -1);
		int sl = HALF * SIZE + HALF;
		dist[sl] = 0;
		via[sl] = -1;
		int goalLocal = goal == -1 ? -2 : local(goal);
		if (sl == goalLocal)
		{
			return;
		}
		int head = 0;
		int tail = 0;
		queue[tail++] = sl;
		while (head < tail)
		{
			int cur = queue[head++];
			int d = dist[cur] + 1;
			if (d > maxSteps)
			{
				break; // the queue holds tiles in order of steps: everything after this is as far
			}
			int lx = cur / SIZE;
			int ly = cur - lx * SIZE;
			int here = (lx + 1) * W + (ly + 1);
			int doorHere = doorBits[cur];
			for (int k = 0; k < 8; k++)
			{
				int nx = lx + DX[k];
				int ny = ly + DY[k];
				if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE)
				{
					continue;
				}
				int n = nx * SIZE + ny;
				if (dist[n] != -1 || !canStep(here, k) || (doorHere & (1 << DOOR_DIR[k])) != 0)
				{
					continue;
				}
				dist[n] = d;
				via[n] = (byte) k;
				if (n == goalLocal)
				{
					return;
				}
				queue[tail++] = n;
			}
		}
	}

	/** Can the step in direction k (game order) be taken from the square tile at bits index {@code i}? */
	private boolean canStep(int i, int k)
	{
		switch (k)
		{
			case 0: // west: the east bit of the tile to the west
				return (bits[i - W] & CollisionMap.E) != 0;
			case 1: // east
				return (bits[i] & CollisionMap.E) != 0;
			case 2: // south: the north bit of the tile to the south
				return (bits[i - 1] & CollisionMap.N) != 0;
			case 3: // north
				return (bits[i] & CollisionMap.N) != 0;
			case 4: // south-west: the north-east bit of the tile to the south-west
				return (bits[i - W - 1] & CollisionMap.NE) != 0;
			case 5: // south-east: the north-west bit of the tile to the south-east
				return (bits[i + W - 1] & CollisionMap.NW) != 0;
			case 6: // north-west
				return (bits[i] & CollisionMap.NW) != 0;
			default: // north-east
				return (bits[i] & CollisionMap.NE) != 0;
		}
	}

	/** Reads the step and door bits of the square around {@code from} (and its border). */
	private void load(int from)
	{
		int bx = Tiles.x(from) - HALF;
		int by = Tiles.y(from) - HALF;
		int p = Tiles.plane(from);
		if (bx == baseX && by == baseY && p == plane && bitsLoaded)
		{
			return;
		}
		baseX = bx;
		baseY = by;
		plane = p;
		bitsLoaded = true;
		Arrays.fill(bits, (byte) 0);
		Arrays.fill(doorBits, (byte) 0);
		// the square plus a border of one tile: x from bx - 1 to bx + SIZE
		for (int rx = Math.max(0, bx - 1) >> 6; rx <= (bx + SIZE) >> 6; rx++)
		{
			for (int ry = Math.max(0, by - 1) >> 6; ry <= (by + SIZE) >> 6; ry++)
			{
				int cell = (rx << 8 | ry) << 2 | p;
				byte[] rb = regionBits.computeIfAbsent(cell, c -> nonNull(map.cellStepBits(c >> 2, c & 3)));
				byte[] db = regionDoors.computeIfAbsent(cell, c -> nonNull(doors.cellBits(c)));
				if (rb.length == 0 && db.length == 0)
				{
					continue;
				}
				int x0 = Math.max(rx << 6, bx - 1);
				int x1 = Math.min((rx << 6) + 63, bx + SIZE);
				int y0 = Math.max(ry << 6, by - 1);
				int y1 = Math.min((ry << 6) + 63, by + SIZE);
				for (int x = x0; x <= x1; x++)
				{
					int lx = x - bx;
					for (int y = y0; y <= y1; y++)
					{
						int ly = y - by;
						int l = (x & 63) << 6 | (y & 63);
						if (rb.length != 0)
						{
							bits[(lx + 1) * W + (ly + 1)] = rb[l];
						}
						if (db.length != 0 && lx >= 0 && ly >= 0 && lx < SIZE && ly < SIZE)
						{
							doorBits[lx * SIZE + ly] = db[l];
						}
					}
				}
			}
		}
	}

	private boolean bitsLoaded;

	private static final byte[] NONE = new byte[0];

	private static byte[] nonNull(byte[] b)
	{
		return b == null ? NONE : b;
	}

	/** Index of a tile in the searched square, or -1 if it is outside it (or on another plane). */
	private int local(int tile)
	{
		int lx = Tiles.x(tile) - baseX;
		int ly = Tiles.y(tile) - baseY;
		if (Tiles.plane(tile) != plane || lx < 0 || ly < 0 || lx >= SIZE || ly >= SIZE)
		{
			return -1;
		}
		return lx * SIZE + ly;
	}

	/** Steps from the last search's start to this tile, or -1 if it was not reached. */
	public int steps(int tile)
	{
		int l = local(tile);
		return l < 0 ? -1 : dist[l];
	}

	/**
	 * The game's path from the last search's start to {@code tile}, both included (packed tiles), or null if it
	 * was not reached.
	 */
	public int[] pathTo(int tile)
	{
		int l = local(tile);
		if (l < 0 || dist[l] < 0)
		{
			return null;
		}
		int[] out = new int[dist[l] + 1];
		int lx = l / SIZE;
		int ly = l % SIZE;
		for (int i = out.length - 1; i >= 0; i--)
		{
			out[i] = Tiles.pack(baseX + lx, baseY + ly, plane);
			int k = via[lx * SIZE + ly];
			if (k >= 0)
			{
				lx -= DX[k];
				ly -= DY[k];
			}
		}
		return out;
	}

	/** True if this step (neighbouring tiles) is in our movement data and goes through no door. */
	public boolean isPlainStep(int from, int to)
	{
		int dx = Tiles.x(to) - Tiles.x(from);
		int dy = Tiles.y(to) - Tiles.y(from);
		if (Tiles.plane(from) != Tiles.plane(to) || Math.abs(dx) > 1 || Math.abs(dy) > 1 || (dx == 0 && dy == 0))
		{
			return false;
		}
		return map.canStep(Tiles.x(from), Tiles.y(from), Tiles.plane(from), dx, dy) && !doors.crosses(from, to);
	}
}
