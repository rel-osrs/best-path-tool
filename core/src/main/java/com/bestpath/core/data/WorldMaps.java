package com.bestpath.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the game's world maps draw each area. The game has several
 * world maps: "Gielinor Surface" and one per underground area ("Unquiet Ocean Underground", ...), chosen in the map
 * list at the world map's bottom left. Each map shows some game areas, and many underground maps draw an area
 * somewhere else than its real game tiles (the Unquiet Ocean lava caves, real tiles x 2624-2751, y 8832-8895, are drawn
 * 64 tiles east and 64 south), sometimes each plane of one area side by side (Dorgesh-Kaan). The surface map draws
 * everything where it is.
 *
 * <p>From the game cache's world map data (index 19, "compositemap": per map, 64x64 map squares and 8x8 zones, each
 * with its game place, its drawn place and its planes), stored in {@value #FILE}. Neighbouring blocks with the same
 * shift and planes are merged into rectangles.
 *
 * <p>Two directions: a drawn spot of a map to its game tile and planes ({@link #toGame}, for a world-map right click)
 * and a game tile to its drawn spot on a map ({@link #drawn}, to draw the route). Within one map no two areas share a
 * drawn spot (clashing blocks are left out when the data is made); across maps they do, so callers name the map. Pure
 * and immutable.
 */
public final class WorldMaps
{
	public static final String FILE = "world-maps.tsv";
	/** No maps: {@link #toGame} and {@link #drawn} find nothing. */
	public static final WorldMaps NONE = new WorldMaps(new String[0], new int[0][]);

	/** Ints per rectangle: game x, y (lowest), width, height (tiles), shift x, y (drawn = game + shift), planes. */
	private static final int GX = 0;
	private static final int GY = 1;
	private static final int W = 2;
	private static final int H = 3;
	private static final int SX = 4;
	private static final int SY = 5;
	private static final int MIN_PLANE = 6;
	private static final int MAX_PLANE = 7;
	static final int STRIDE = 8;

	final String[] names;
	/** Per map, its rectangles, {@value #STRIDE} ints each. */
	final int[][] rects;

	WorldMaps(String[] names, int[][] rects)
	{
		this.names = names;
		this.rects = rects;
	}

	/** How many maps (map ids are 0 to this minus 1, in the cache's order). */
	public int mapCount()
	{
		return names.length;
	}

	public String mapName(int map)
	{
		return names[map];
	}

	/** The map with this name (the game's own name, as on the map list; case and spaces at the ends ignored), or -1. */
	public int mapByName(String name)
	{
		if (name == null)
		{
			return -1;
		}
		String n = name.trim();
		for (int i = 0; i < names.length; i++)
		{
			if (names[i].equalsIgnoreCase(n))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * The game tile drawn at a spot of a map: {@code {x, y, planes}} where bit p of planes is set for each plane the map
	 * draws there (most spots: plane 0 only, or all four); null if the map draws nothing there.
	 */
	public int[] toGame(int map, int drawX, int drawY)
	{
		if (map < 0 || map >= rects.length)
		{
			return null;
		}
		int[] r = rects[map];
		int[] found = null;
		for (int i = 0; i < r.length; i += STRIDE)
		{
			int gx = drawX - r[i + SX];
			int gy = drawY - r[i + SY];
			if (inside(r, i, gx, gy))
			{
				int planes = planeBits(r[i + MIN_PLANE], r[i + MAX_PLANE]);
				if (found == null)
				{
					found = new int[] {gx, gy, planes};
				}
				else if (found[0] == gx && found[1] == gy)
				{
					found[2] |= planes;
				}
			}
		}
		return found;
	}

	/**
	 * Where a map draws a game tile: {@link #packed} drawn x and y (read them with {@link #x} and {@link #y}), or -1
	 * when the map does not show that tile on that plane.
	 */
	public int drawn(int map, int x, int y, int plane)
	{
		if (map < 0 || map >= rects.length)
		{
			return -1;
		}
		int[] r = rects[map];
		for (int i = 0; i < r.length; i += STRIDE)
		{
			if (plane >= r[i + MIN_PLANE] && plane <= r[i + MAX_PLANE] && inside(r, i, x, y))
			{
				return packed(x + r[i + SX], y + r[i + SY]);
			}
		}
		return -1;
	}

	/**
	 * What a right click on a spot of a map means as a destination: {@code {x, y, plane}}, plane -1 when the route
	 * search picks it (the spot shows several planes); null for no destination there.
	 * <ul>
	 * <li>A spot the map draws something at: the game tile drawn there (on the plane drawn there when only one is).</li>
	 * <li>An empty spot whose own game tiles this map draws somewhere else: null, a click there means nothing.</li>
	 * <li>Any other spot, and any spot of an unknown map ({@code map} -1): the spot itself.</li>
	 * </ul>
	 */
	public int[] mapClick(int map, int drawX, int drawY)
	{
		if (map < 0 || map >= rects.length)
		{
			return new int[] {drawX, drawY, -1};
		}
		int[] g = toGame(map, drawX, drawY);
		if (g != null)
		{
			return new int[] {g[0], g[1], Integer.bitCount(g[2]) == 1 ? Integer.numberOfTrailingZeros(g[2]) : -1};
		}
		for (int p = 0; p < 4; p++)
		{
			if (drawn(map, drawX, drawY, p) != -1)
			{
				return null;
			}
		}
		return new int[] {drawX, drawY, -1};
	}

	/** An x and y (each 0 to 32767) in one int, as {@link #drawn} gives them. */
	public static int packed(int x, int y)
	{
		return x << 15 | y;
	}

	public static int x(int packed)
	{
		return packed >>> 15;
	}

	public static int y(int packed)
	{
		return packed & 0x7FFF;
	}

	private static boolean inside(int[] r, int i, int x, int y)
	{
		return x >= r[i + GX] && x < r[i + GX] + r[i + W] && y >= r[i + GY] && y < r[i + GY] + r[i + H];
	}

	private static int planeBits(int min, int max)
	{
		int bits = 0;
		for (int p = min; p <= max; p++)
		{
			bits |= 1 << p;
		}
		return bits;
	}

	/**
	 * Reads the text form: comment lines, then per map a "map" line (id, name) and its "area" lines (game x, y, width,
	 * height, shift x, y, lowest plane, highest plane).
	 */
	public static WorldMaps parse(List<String> lines)
	{
		List<String> names = new ArrayList<>();
		List<int[]> rects = new ArrayList<>();
		List<Integer> current = null;
		for (String line : lines)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			if (c[0].equals("map"))
			{
				if (current != null)
				{
					rects.add(toArray(current));
				}
				if (Integer.parseInt(c[1]) != names.size())
				{
					throw new IllegalArgumentException("world map ids out of order: " + line);
				}
				names.add(c[2]);
				current = new ArrayList<>();
			}
			else if (c[0].equals("area") && current != null && c.length == STRIDE + 1)
			{
				for (int k = 1; k <= STRIDE; k++)
				{
					current.add(Integer.parseInt(c[k]));
				}
			}
			else
			{
				throw new IllegalArgumentException("bad world map line: " + line);
			}
		}
		if (current != null)
		{
			rects.add(toArray(current));
		}
		return new WorldMaps(names.toArray(new String[0]), rects.toArray(new int[0][]));
	}

	private static int[] toArray(List<Integer> l)
	{
		int[] a = new int[l.size()];
		for (int i = 0; i < a.length; i++)
		{
			a[i] = l.get(i);
		}
		return a;
	}
}
