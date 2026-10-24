package com.bestpath.tool.extract;

import com.bestpath.core.collision.CollisionMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Safe areas inside the Wilderness (such as Ferox Enclave): the game's level script counts them as
 * the Wilderness, the server makes them safe. Each is named by one tile inside it in {@value #FILE}
 * ({@code name <tab> x,y,plane <tab> source}); the area is the walled ground around that tile: every tile reachable from
 * it by walking (straight steps, the export's walking map: walls and objects, barriers are transports and not walked
 * through), with the holes inside closed (buildings, trees: every tile its outside can't reach), on any plane. An area
 * that grows past {@value #MAX_TILES} tiles is refused (its walls leak).
 */
public final class SafeAreas
{
	public static final String FILE = "data/manual/wilderness-safe.tsv";
	static final int MAX_TILES = 5000;
	private static final int[] DX = {0, 1, 0, -1};
	private static final int[] DY = {1, 0, -1, 0};

	/** One safe area as the file names it. */
	public static final class Anchor
	{
		final String name;
		final int x;
		final int y;
		final int plane;

		public Anchor(String name, int x, int y, int plane)
		{
			this.name = name;
			this.x = x;
			this.y = y;
			this.plane = plane;
		}
	}

	private final Set<Long> tiles;
	private final List<String> found;

	private SafeAreas(Set<Long> tiles, List<String> found)
	{
		this.tiles = tiles;
		this.found = found;
	}

	/** The file's areas; none when there is no file. */
	public static List<Anchor> read(File file) throws IOException
	{
		List<Anchor> out = new ArrayList<>();
		if (!file.isFile())
		{
			return out;
		}
		for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
		{
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] p = line.split("\t");
			String[] t = p.length > 1 ? p[1].trim().split(",") : new String[0];
			if (t.length != 3)
			{
				throw new IOException(FILE + ": bad line: " + line);
			}
			out.add(new Anchor(p[0].trim(), Integer.parseInt(t[0].trim()), Integer.parseInt(t[1].trim()), Integer.parseInt(t[2].trim())));
		}
		return out;
	}

	/** The areas around these tiles on this walking map. */
	public static SafeAreas of(CollisionMap map, List<Anchor> anchors)
	{
		Set<Long> all = new HashSet<>();
		List<String> found = new ArrayList<>();
		for (Anchor a : anchors)
		{
			Set<Long> area = area(map, a);
			all.addAll(area);
			found.add(a.name + " " + area.size() + " tiles");
		}
		return new SafeAreas(all, found);
	}

	private static long key(int x, int y)
	{
		return (long) x << 32 | y & 0xffffffffL;
	}

	/** The walled ground around the anchor, holes closed. */
	static Set<Long> area(CollisionMap map, Anchor a)
	{
		int[] box = {a.x, a.y, a.x, a.y};
		Set<Long> ground = ground(map, a, box);
		Set<Long> outside = outside(ground, box[0], box[1], box[2], box[3]);
		Set<Long> area = new HashSet<>();
		for (int x = box[0]; x <= box[2]; x++)
		{
			for (int y = box[1]; y <= box[3]; y++)
			{
				if (!outside.contains(key(x, y)))
				{
					area.add(key(x, y));
				}
			}
		}
		return area;
	}

	/** Every tile walked to from the anchor by straight steps; {@code box} ({x0, y0, x1, y1}) grows to hold them. */
	private static Set<Long> ground(CollisionMap map, Anchor a, int[] box)
	{
		Set<Long> ground = new HashSet<>();
		ArrayDeque<int[]> q = new ArrayDeque<>();
		ground.add(key(a.x, a.y));
		q.add(new int[]{a.x, a.y});
		while (!q.isEmpty())
		{
			int[] t = q.poll();
			for (int d = 0; d < 4; d++)
			{
				if (!map.canStep(t[0], t[1], a.plane, DX[d], DY[d]))
				{
					continue;
				}
				int nx = t[0] + DX[d];
				int ny = t[1] + DY[d];
				if (ground.add(key(nx, ny)))
				{
					if (ground.size() > MAX_TILES)
					{
						throw new IllegalStateException("safe area " + a.name + " is not walled in (over " + MAX_TILES + " tiles)");
					}
					q.add(new int[]{nx, ny});
					box[0] = Math.min(box[0], nx);
					box[1] = Math.min(box[1], ny);
					box[2] = Math.max(box[2], nx);
					box[3] = Math.max(box[3], ny);
				}
			}
		}
		return ground;
	}

	/** The outside: tiles of the box one wider that are not the ground, reached from its edge. */
	private static Set<Long> outside(Set<Long> ground, int x0, int y0, int x1, int y1)
	{
		ArrayDeque<int[]> q = new ArrayDeque<>();
		Set<Long> outside = new HashSet<>();
		for (int x = x0 - 1; x <= x1 + 1; x++)
		{
			for (int y : new int[]{y0 - 1, y1 + 1})
			{
				if (outside.add(key(x, y)))
				{
					q.add(new int[]{x, y});
				}
			}
		}
		for (int y = y0; y <= y1; y++)
		{
			for (int x : new int[]{x0 - 1, x1 + 1})
			{
				if (outside.add(key(x, y)))
				{
					q.add(new int[]{x, y});
				}
			}
		}
		while (!q.isEmpty())
		{
			int[] t = q.poll();
			for (int d = 0; d < 4; d++)
			{
				int nx = t[0] + DX[d];
				int ny = t[1] + DY[d];
				if (nx < x0 || nx > x1 || ny < y0 || ny > y1 || ground.contains(key(nx, ny)) || !outside.add(key(nx, ny)))
				{
					continue;
				}
				q.add(new int[]{nx, ny});
			}
		}
		return outside;
	}

	/** True when this tile is in a safe area (any plane). */
	public boolean contains(int x, int y)
	{
		return tiles.contains(key(x, y));
	}

	/** "Ferox Enclave 812 tiles", for the export's report. */
	public String describe()
	{
		return found.isEmpty() ? "none" : String.join(", ", found);
	}
}
