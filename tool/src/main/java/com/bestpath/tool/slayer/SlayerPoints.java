package com.bestpath.tool.slayer;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.data.WorldMaps;
import com.bestpath.core.path.Tiles;
import com.bestpath.tool.clue.KillAreas;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Slayer place's points ({@link SlayerExport}): its Wiki spawn tiles put where the exported map lets a player stand,
 * grouped like the clue kill areas, one point per group.
 */
final class SlayerPoints
{
	private SlayerPoints()
	{
	}

	/** How far a spawn tile the player can't stand on may be moved. */
	static final int NEAR = 3;

	/** As {@link #points(String, CollisionMap, WorldMaps, SlayerExport.Result)} without world maps. */
	static int[] points(String tiles, CollisionMap map, SlayerExport.Result r)
	{
		return points(tiles, map, WorldMaps.NONE, r);
	}

	/**
	 * One point per group of the usable tiles ("x,y,p x,y,p"), packed; {@code map} null: every tile as it is (not counted).
	 * When one tile of the place is usable only where a world map draws it back ({@link #drawnBack}), the Wiki gave the
	 * place on that map, so every tile of it that can be drawn back is (its other tiles may lie on some other area's
	 * ground, e.g. Taverley Dungeon's upper level is drawn over the Crandor and Karamja Dungeon).
	 */
	static int[] points(String tiles, CollisionMap map, WorldMaps worldMaps, SlayerExport.Result r)
	{
		if (tiles == null || tiles.equals("-") || tiles.isBlank())
		{
			return new int[0];
		}
		List<int[]> given = new ArrayList<>();
		for (String s : tiles.trim().split(" +"))
		{
			String[] c = s.split(",");
			given.add(new int[]{Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])});
		}
		boolean drawnPlace = false;
		for (int i = 0; map != null && !drawnPlace && i < given.size(); i++)
		{
			int[] g = given.get(i);
			drawnPlace = usable(map, g[0], g[1], g[2]) < 0 && drawnBack(map, worldMaps, g[0], g[1], g[2]) >= 0;
		}
		Map<Integer, List<int[]>> byPlane = new LinkedHashMap<>();
		Set<Integer> seen = new LinkedHashSet<>();
		for (int[] g : given)
		{
			int t = placed(g[0], g[1], g[2], map, worldMaps, drawnPlace, r);
			if (t >= 0 && seen.add(t))
			{
				byPlane.computeIfAbsent(Tiles.plane(t), k -> new ArrayList<>()).add(new int[]{Tiles.x(t), Tiles.y(t)});
			}
		}
		List<Integer> out = new ArrayList<>();
		for (Map.Entry<Integer, List<int[]>> e : byPlane.entrySet())
		{
			for (List<int[]> g : KillAreas.group(e.getValue(), KillAreas.GAP))
			{
				int[] m = middle(g);
				out.add(Tiles.pack(m[0], m[1], e.getKey()));
			}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * Where one Wiki tile goes, packed, counted in {@code r}: drawn back first on a {@code drawnPlace}, else usable
	 * where it is or moved ({@link #usable}), else drawn back; -1 when dropped.
	 */
	private static int placed(int x, int y, int p, CollisionMap map, WorldMaps worldMaps, boolean drawnPlace, SlayerExport.Result r)
	{
		int t = drawnPlace ? drawnBack(map, worldMaps, x, y, p) : -1;
		boolean drawn = t >= 0;
		if (!drawn)
		{
			t = map == null ? Tiles.pack(x, y, p) : usable(map, x, y, p);
		}
		if (t < 0 && map != null)
		{
			t = drawnBack(map, worldMaps, x, y, p);
			drawn = t >= 0;
		}
		if (t < 0)
		{
			r.tilesDropped++;
			return -1;
		}
		if (drawn)
		{
			r.tilesDrawnBack++;
		}
		else if (map != null && Tiles.plane(t) != p)
		{
			r.tilesOtherPlane++;
		}
		else if (map != null && t != Tiles.pack(x, y, p))
		{
			r.tilesMoved++;
		}
		return t;
	}

	/**
	 * The tile itself when a player can stand there (it has a step out), else the nearest such tile within {@link #NEAR},
	 * else the same spot (or a next one) on the first other plane that has ground there, else -1.
	 */
	static int usable(CollisionMap map, int x, int y, int plane)
	{
		int t = near(map, x, y, plane, NEAR);
		for (int p = 0; t < 0 && p < 4; p++)
		{
			t = p == plane ? -1 : near(map, x, y, p, 1);
		}
		return t;
	}

	/**
	 * The game tile a world map draws at this spot, when the spot itself has no ground: the Wiki's maps show underground
	 * areas where the game's world map draws them, often far from their game tiles (the Kalphite Lair's Wiki tile
	 * 3186,9424 is drawn by "Kharidian Desert Underground"; its game tile is 3474,9496). Per map that draws the spot
	 * elsewhere ({@link WorldMaps#toGame}; the surface map draws everything where it is): the game tile on the Wiki's
	 * plane if the map draws that plane there, else on the lowest plane drawn there, moved within {@link #NEAR} onto
	 * ground. -1 when no map gives ground, or when two maps give different ground (which one the Wiki meant is unknown).
	 */
	static int drawnBack(CollisionMap map, WorldMaps worldMaps, int x, int y, int plane)
	{
		int found = -1;
		for (int m = 0; m < worldMaps.mapCount(); m++)
		{
			int[] g = worldMaps.toGame(m, x, y);
			if (g == null || g[0] == x && g[1] == y)
			{
				continue; // the map draws nothing here, or draws it where it is (tried already)
			}
			int t = (g[2] >> plane & 1) != 0 ? near(map, g[0], g[1], plane, NEAR) : -1;
			for (int p = 0; t < 0 && p < 4; p++)
			{
				t = (g[2] >> p & 1) != 0 ? near(map, g[0], g[1], p, NEAR) : -1;
			}
			if (t >= 0 && found >= 0 && t != found)
			{
				return -1;
			}
			found = t >= 0 ? t : found;
		}
		return found;
	}

	/** The tile, or the nearest within {@code reach} a player can stand on, on this plane; -1 for none. */
	private static int near(CollisionMap map, int x, int y, int plane, int reach)
	{
		if (standable(map, x, y, plane))
		{
			return Tiles.pack(x, y, plane);
		}
		for (int d = 1; d <= reach; d++)
		{
			for (int dx = -d; dx <= d; dx++)
			{
				for (int dy = -d; dy <= d; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) == d && standable(map, x + dx, y + dy, plane))
					{
						return Tiles.pack(x + dx, y + dy, plane);
					}
				}
			}
		}
		return -1;
	}

	private static boolean standable(CollisionMap map, int x, int y, int plane)
	{
		if (x < 0 || y < 0 || x > 0x3FFF || y > 0x3FFF || plane < 0 || plane > 3)
		{
			return false;
		}
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if ((dx != 0 || dy != 0) && map.canStep(x, y, plane, dx, dy))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** The group's tile nearest its middle (a real spawn tile). */
	private static int[] middle(List<int[]> g)
	{
		double mx = 0;
		double my = 0;
		for (int[] t : g)
		{
			mx += t[0];
			my += t[1];
		}
		mx /= g.size();
		my /= g.size();
		int[] best = g.get(0);
		double bestD = Double.MAX_VALUE;
		for (int[] t : g)
		{
			double d = (t[0] - mx) * (t[0] - mx) + (t[1] - my) * (t[1] - my);
			if (d < bestD)
			{
				bestD = d;
				best = t;
			}
		}
		return best;
	}
}
