package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A small hand-made or random world for the walk tests: regions 50..52 x
 * 50..52 on plane 0 (x and y 3200-3391), walls, blocked tiles and doors (open
 * in the world, a wall when shut).
 */
final class TestWorld implements MovementRules.Flags
{
	static final int X0 = 3200;
	static final int Y0 = 3200;
	static final int SIZE = 192;
	private static final MovementRules.Flags NONE = (x, y) -> CollisionFlags.BLOCKED;

	private final Map<Long, Integer> flags = new HashMap<>();
	private final Map<Long, Integer> shutFlags = new HashMap<>();
	private final List<int[]> doors = new ArrayList<>();

	void set(int x, int y, int f)
	{
		flags.merge(key(x, y), f, (p, q) -> p | q);
	}

	void block(int x, int y)
	{
		set(x, y, CollisionFlags.OBJECT);
	}

	/** A wall on the east side of (x, y). */
	void wallEast(int x, int y)
	{
		set(x, y, CollisionFlags.EAST);
		set(x + 1, y, CollisionFlags.WEST);
	}

	/** A wall on the north side of (x, y). */
	void wallNorth(int x, int y)
	{
		set(x, y, CollisionFlags.NORTH);
		set(x, y + 1, CollisionFlags.SOUTH);
	}

	/** A door on the east side of (x, y): open in the world, a wall when shut. */
	void doorEast(int x, int y)
	{
		shutFlags.merge(key(x, y), CollisionFlags.EAST, (p, q) -> p | q);
		shutFlags.merge(key(x + 1, y), CollisionFlags.WEST, (p, q) -> p | q);
		doors.add(new int[]{x, y, 0});
	}

	/** A door on the north side of (x, y). */
	void doorNorth(int x, int y)
	{
		shutFlags.merge(key(x, y), CollisionFlags.NORTH, (p, q) -> p | q);
		shutFlags.merge(key(x, y + 1), CollisionFlags.SOUTH, (p, q) -> p | q);
		doors.add(new int[]{x, y, 0});
	}

	private static long key(int x, int y)
	{
		return (long) x << 32 | y;
	}

	@Override
	public int get(int x, int y)
	{
		boolean inside = x >= X0 && x < X0 + SIZE && y >= Y0 && y < Y0 + SIZE;
		return inside ? flags.getOrDefault(key(x, y), 0) : CollisionFlags.BLOCKED;
	}

	CollisionMap map()
	{
		return CollisionMapBuilder.build(new MovementRules.Flags[]{this, NONE, NONE, NONE}, regions(), 0, 0);
	}

	DoorMap doorMap()
	{
		MovementRules.Flags shut = (x, y) -> get(x, y) | shutFlags.getOrDefault(key(x, y), 0);
		return DoorMapBuilder.build(new MovementRules.Flags[]{this, NONE, NONE, NONE},
			new MovementRules.Flags[]{shut, NONE, NONE, NONE}, doors);
	}

	private static List<Integer> regions()
	{
		List<Integer> r = new ArrayList<>();
		for (int rx = 50; rx <= 52; rx++)
		{
			for (int ry = 50; ry <= 52; ry++)
			{
				r.add(rx << 8 | ry);
			}
		}
		return r;
	}

	/** A random world: {@code busy} 0 = open ground (many equally short walks), larger = more walls and blocks. */
	static TestWorld random(Random rnd, int busy)
	{
		TestWorld w = new TestWorld();
		int walls = busy * 60;
		for (int i = 0; i < walls; i++)
		{
			int x = X0 + rnd.nextInt(SIZE);
			int y = Y0 + rnd.nextInt(SIZE);
			int len = 1 + rnd.nextInt(20);
			boolean vertical = rnd.nextBoolean();
			for (int k = 0; k < len; k++)
			{
				if (vertical)
				{
					w.wallEast(x, y + k);
				}
				else
				{
					w.wallNorth(x + k, y);
				}
			}
		}
		for (int i = 0; i < busy * 50; i++)
		{
			w.block(X0 + rnd.nextInt(SIZE), Y0 + rnd.nextInt(SIZE));
		}
		for (int i = 0; i < busy * 8; i++)
		{
			int x = X0 + rnd.nextInt(SIZE - 1);
			int y = Y0 + rnd.nextInt(SIZE - 1);
			if (rnd.nextBoolean())
			{
				w.doorEast(x, y);
			}
			else
			{
				w.doorNorth(x, y);
			}
		}
		return w;
	}

	static int tile(int x, int y)
	{
		return Tiles.pack(x, y, 0);
	}

	/** "E E NE ..." for a path's steps. */
	static String dirs(int[] path)
	{
		StringBuilder b = new StringBuilder();
		for (int i = 1; i < path.length; i++)
		{
			int dx = Tiles.x(path[i]) - Tiles.x(path[i - 1]);
			int dy = Tiles.y(path[i]) - Tiles.y(path[i - 1]);
			String d = (dy > 0 ? "N" : dy < 0 ? "S" : "") + (dx > 0 ? "E" : dx < 0 ? "W" : "");
			b.append(b.length() == 0 ? "" : " ").append(d.isEmpty() ? "?" : d);
		}
		return b.toString();
	}

	/** "7xE 3xNE": runs of the same direction. */
	static String runs(int[] path)
	{
		String[] d = dirs(path).split(" ");
		StringBuilder b = new StringBuilder();
		int run = 0;
		for (int i = 0; i < d.length; i++)
		{
			run++;
			if (i == d.length - 1 || !d[i + 1].equals(d[i]))
			{
				b.append(b.length() == 0 ? "" : " ").append(run).append('x').append(d[i]);
				run = 0;
			}
		}
		return b.toString();
	}
}
