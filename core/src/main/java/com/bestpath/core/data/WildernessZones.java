package com.bestpath.core.data;

import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Transport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Which tiles are in the Wilderness, and which side of its level-20 and level-30 lines (the level itself is not
 * needed). Used for the Wilderness cards, for the teleports' Wilderness limits (none in the Wilderness, up to level 20,
 * up to level 30) and for avoiding the Wilderness.
 *
 * <p>Zones: 0 outside (or in a safe area: Ferox Enclave), {@value #TO_20} levels 1-20, {@value #TO_30} levels 21-30,
 * {@value #DEEP} levels 31 and deeper. From the game cache: client script 384 works out the level the game shows
 * ("Level: N") from the tile, as nine boxes in order (helper script 247 tests a box). When the data is generated that
 * script is run for every tile of its boxes, the safe areas (the walled ground around a given tile) are taken out, and
 * the zones are written to {@value #FILE} as rectangles (rows with the same runs merged). Every
 * box covers planes 0-3, so the zone does not depend on the plane. Pure and immutable.
 *
 * <p>File: comment lines start with {@code #}; one rectangle per line, {@code x <tab> y <tab> width <tab> height <tab>
 * zone} (x, y its lowest tile).
 */
public final class WildernessZones
{
	public static final String FILE = "wilderness.tsv";
	/** No Wilderness known (no {@value #FILE}): every tile is outside. */
	public static final WildernessZones NONE = new WildernessZones(new int[0]);
	/** Levels 1 to 20: most teleports still work. */
	public static final int TO_20 = 1;
	/** Levels 21 to 30: only the teleports that work up to level 30. */
	public static final int TO_30 = 2;
	/** Levels 31 and deeper: no teleport works. */
	public static final int DEEP = 3;

	static final int STRIDE = 5;
	/** x, y, width, height, zone per rectangle, sorted by y then x. */
	final int[] rects;
	private final int minX;
	private final int minY;
	private final int maxX;
	private final int maxY;

	private WildernessZones(int[] rects)
	{
		this.rects = rects;
		int x0 = Integer.MAX_VALUE;
		int y0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int y1 = Integer.MIN_VALUE;
		for (int i = 0; i < rects.length; i += STRIDE)
		{
			x0 = Math.min(x0, rects[i]);
			y0 = Math.min(y0, rects[i + 1]);
			x1 = Math.max(x1, rects[i] + rects[i + 2] - 1);
			y1 = Math.max(y1, rects[i + 1] + rects[i + 3] - 1);
		}
		this.minX = x0;
		this.minY = y0;
		this.maxX = x1;
		this.maxY = y1;
	}

	/** The zone a Wilderness level is in: 0 for 0 (outside), {@link #TO_20}, {@link #TO_30} or {@link #DEEP}. */
	public static int zoneOfLevel(int level)
	{
		return level <= 0 ? 0 : level <= 20 ? TO_20 : level <= 30 ? TO_30 : DEEP;
	}

	/**
	 * The deepest zone a teleport with this Wilderness limit works from (the data's limits: 0 = not in the Wilderness,
	 * 20 or 30; null = no limit).
	 */
	public static int deepestZone(Integer limit)
	{
		return limit == null ? DEEP : zoneOfLevel(limit);
	}

	/** This tile's zone, 0 outside (any plane: the game's boxes cover planes 0-3). */
	public int zone(int x, int y)
	{
		if (x < minX || x > maxX || y < minY || y > maxY)
		{
			return 0;
		}
		for (int i = 0; i < rects.length; i += STRIDE)
		{
			int ry = rects[i + 1];
			if (ry > y)
			{
				break; // sorted by y: none further down starts at or below this row
			}
			int rx = rects[i];
			if (x >= rx && x < rx + rects[i + 2] && y < ry + rects[i + 3])
			{
				return rects[i + 4];
			}
		}
		return 0;
	}

	/** {@link #closed()}, made once. */
	private PathQuery.Closed closed;

	/**
	 * The Wilderness as tiles a search may not enter (for avoiding the Wilderness): a 64x64 cell is shut when every
	 * tile of it is the Wilderness, open when none is, else asked per tile (Ferox Enclave's cells). Thread-safe (each
	 * cell's answer is worked out from the rectangles alone).
	 */
	public synchronized PathQuery.Closed closed()
	{
		if (closed == null)
		{
			closed = new PathQuery.Closed()
			{
				@Override
				public int cell(int x0, int y0)
				{
					long covered = 0;
					for (int i = 0; i < rects.length; i += STRIDE)
					{
						int w = Math.min(x0 + 64, rects[i] + rects[i + 2]) - Math.max(x0, rects[i]);
						int h = Math.min(y0 + 64, rects[i + 1] + rects[i + 3]) - Math.max(y0, rects[i + 1]);
						if (w > 0 && h > 0)
						{
							covered += (long) w * h;
						}
					}
					return covered == 0 ? OPEN : covered == 64 * 64 ? SHUT : PART;
				}

				@Override
				public boolean tile(int x, int y)
				{
					return isWilderness(x, y);
				}
			};
		}
		return closed;
	}

	/**
	 * The teleports' Wilderness limits for a search: a teleport the start is too deep for is offered from every tile
	 * the search reaches that is shallow enough ({@link com.bestpath.core.path.PathQuery.Limits}), so a route may first
	 * walk (or use a transport such as a lever) to lower Wilderness. {@code limitOf}: a teleport's limit (null = none).
	 */
	public PathQuery.Limits limits(Function<Transport, Integer> limitOf)
	{
		return new PathQuery.Limits()
		{
			@Override
			public int zone(int x, int y)
			{
				return WildernessZones.this.zone(x, y);
			}

			@Override
			public int cellZone(int x0, int y0)
			{
				long[] area = new long[DEEP + 1];
				for (int i = 0; i < rects.length; i += STRIDE)
				{
					int w = Math.min(x0 + 64, rects[i] + rects[i + 2]) - Math.max(x0, rects[i]);
					int h = Math.min(y0 + 64, rects[i + 1] + rects[i + 3]) - Math.max(y0, rects[i + 1]);
					if (w > 0 && h > 0)
					{
						area[rects[i + 4]] += (long) w * h;
					}
				}
				long inside = area[TO_20] + area[TO_30] + area[DEEP];
				if (inside == 0)
				{
					return 0;
				}
				for (int z = TO_20; z <= DEEP; z++)
				{
					if (area[z] == 64 * 64)
					{
						return z;
					}
				}
				return -1;
			}

			@Override
			public int deepest(Transport teleport)
			{
				return deepestZone(limitOf.apply(teleport));
			}
		};
	}

	/** True when this tile is in the Wilderness (not in a safe area). */
	public boolean isWilderness(int x, int y)
	{
		return zone(x, y) > 0;
	}

	public boolean isEmpty()
	{
		return rects.length == 0;
	}

	/** Reads the text form (see the class comment); a broken line is refused. */
	public static WildernessZones parse(List<String> lines)
	{
		List<int[]> out = new ArrayList<>();
		for (String line : lines)
		{
			String t = line.trim();
			if (t.isEmpty() || t.startsWith("#"))
			{
				continue;
			}
			String[] p = t.split("\t");
			if (p.length < STRIDE)
			{
				throw new IllegalArgumentException("wilderness: bad line: " + line);
			}
			int[] r = new int[STRIDE];
			for (int k = 0; k < STRIDE; k++)
			{
				r[k] = Integer.parseInt(p[k].trim());
			}
			if (r[2] <= 0 || r[3] <= 0 || r[4] < TO_20 || r[4] > DEEP)
			{
				throw new IllegalArgumentException("wilderness: bad rectangle: " + line);
			}
			out.add(r);
		}
		return of(out);
	}

	static WildernessZones of(List<int[]> list)
	{
		list.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0]));
		int[] flat = new int[list.size() * STRIDE];
		for (int i = 0; i < list.size(); i++)
		{
			System.arraycopy(list.get(i), 0, flat, i * STRIDE, STRIDE);
		}
		return new WildernessZones(flat);
	}
}
