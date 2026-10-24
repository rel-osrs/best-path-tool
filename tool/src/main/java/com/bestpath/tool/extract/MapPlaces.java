package com.bestpath.tool.extract;

import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.path.Tiles;
import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.region.Location;

/**
 * Where the game shows each map icon (for "go to nearest"). Verified in cache 2735: the icons are
 * invisible marker objects (no name, no model use to us) whose definition names a map icon ("area", config archive 35,
 * 369 kinds placed); the world map's own elements are labels and one-off icons only. An icon definition has a picture
 * (sprite) but <b>no name</b> in the cache: the names come from {@code data/manual/map-icon-kinds.tsv}.
 */
public final class MapPlaces
{
	/** The kinds' names (icon id, name, why), from the workspace. */
	public static final String NAMES = "data/manual/map-icon-kinds.tsv";

	private MapPlaces()
	{
	}

	/** The names file's lines read: icon id to kind name, in file order (# lines and the header skipped). */
	public static Map<Integer, String> readNames(List<String> lines)
	{
		Map<Integer, String> out = new java.util.LinkedHashMap<>();
		for (int i = 0; i < lines.size(); i++)
		{
			String l = lines.get(i);
			if (l.isBlank() || l.startsWith("#") || l.startsWith("icon\t"))
			{
				continue;
			}
			String[] c = l.split("\t", -1);
			if (c.length < 2 || c[1].isBlank())
			{
				throw new IllegalArgumentException(NAMES + " line " + (i + 1) + ": icon and kind needed: " + l);
			}
			if (out.put(Integer.parseInt(c[0].trim()), c[1].trim()) != null)
			{
				throw new IllegalArgumentException(NAMES + " line " + (i + 1) + ": icon " + c[0].trim() + " named twice");
			}
		}
		return out;
	}

	/** The names file's third column (why the name was given) by icon id; empty when a line has none. */
	public static Map<Integer, String> readWhy(List<String> lines)
	{
		Map<Integer, String> out = new java.util.LinkedHashMap<>();
		for (String l : lines)
		{
			if (l.isBlank() || l.startsWith("#") || l.startsWith("icon\t"))
			{
				continue;
			}
			String[] c = l.split("\t", -1);
			out.put(Integer.parseInt(c[0].trim()), c.length > 2 ? c[2].trim() : "");
		}
		return out;
	}

	/** A "why" that starts with this marks a name that is only a guess (its card asks for a check). */
	public static final String NOT_SURE = "Name not sure:";

	/**
	 * The names with the card answers: the {@code removed} icons left out, a new name from
	 * {@code renamed} used; in the same order.
	 */
	public static Map<Integer, String> answered(Map<Integer, String> names, java.util.Set<Integer> removed,
		Map<Integer, String> renamed)
	{
		Map<Integer, String> out = new java.util.LinkedHashMap<>();
		names.forEach((icon, name) ->
		{
			if (!removed.contains(icon))
			{
				out.put(icon, renamed.getOrDefault(icon, name));
			}
		});
		return out;
	}

	/** What a place kind's Keep / Remove card shows in the Data Tool. Immutable. */
	public static final class Card
	{
		public final int icon;
		public final String name;
		public final String why;
		public final int sprite;
		/** Its places, packed ({@link Tiles}); for the bank, the tiles a player banks from. */
		public final int[] tiles;
		/** The map icon's picture, or null. */
		public final java.awt.image.BufferedImage picture;

		public Card(int icon, String name, String why, int sprite, int[] tiles, java.awt.image.BufferedImage picture)
		{
			this.icon = icon;
			this.name = name;
			this.why = why;
			this.sprite = sprite;
			this.tiles = tiles.clone();
			this.picture = picture;
		}
	}

	/**
	 * One card per named kind (in name order, the names as the file has them): its places ({@code found}; the bank's
	 * from {@code bankTiles}), picture and why. A named icon with no places gets no card.
	 */
	public static List<Card> cards(Map<Integer, String> names, Map<Integer, String> why, Map<Integer, int[]> found,
		Map<Integer, Integer> sprites, int[] bankTiles, java.util.function.IntFunction<java.awt.image.BufferedImage> picture)
	{
		List<Card> out = new ArrayList<>();
		names.forEach((icon, name) ->
		{
			int[] tiles = name.equals(PlaceKinds.BANK) ? bankTiles : found.get(icon);
			if (tiles == null || tiles.length == 0)
			{
				return;
			}
			int sprite = sprites.getOrDefault(icon, -1);
			out.add(new Card(icon, name, why.getOrDefault(icon, ""), sprite, tiles, sprite < 0 ? null : picture.apply(sprite)));
		});
		out.sort(java.util.Comparator.comparing(c -> c.name.toLowerCase(java.util.Locale.ROOT)));
		return out;
	}

	/**
	 * The {@code count} places nearest to ({@code x}, {@code y}) on the surface (any plane, nearest first), to show on a
	 * card: the first is where the card points.
	 */
	public static int[] nearest(int[] tiles, int x, int y, int count)
	{
		Integer[] order = new Integer[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			order[i] = i;
		}
		java.util.Arrays.sort(order, java.util.Comparator.comparingLong((Integer i) -> distance(tiles[i], x, y)).thenComparingInt(i -> i));
		int[] out = new int[Math.min(count, tiles.length)];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = tiles[order[i]];
		}
		return out;
	}

	private static long distance(int t, int x, int y)
	{
		long dx = Tiles.x(t) - x;
		long dy = Tiles.y(t) - y;
		return dx * dx + dy * dy;
	}

	/**
	 * The places table: every named kind with its places ({@code found}, per icon id) and picture ({@code sprites}), by
	 * name; {@value PlaceKinds#BANK} without tiles (the plugin takes the banks from banks.tsv). A named icon the cache
	 * doesn't place is left out and noted in {@code problems}.
	 */
	public static PlaceKinds kinds(Map<Integer, String> names, Map<Integer, int[]> found, Map<Integer, Integer> sprites,
		List<String> problems)
	{
		List<PlaceKinds.Kind> out = new ArrayList<>();
		names.forEach((icon, name) ->
		{
			int sprite = sprites.getOrDefault(icon, -1);
			if (name.equals(PlaceKinds.BANK))
			{
				out.add(new PlaceKinds.Kind(name, sprite, new int[0]));
			}
			else if (found.containsKey(icon))
			{
				out.add(new PlaceKinds.Kind(name, sprite, found.get(icon)));
			}
			else
			{
				problems.add("map icon " + icon + " (" + name + ") is not placed anywhere in this cache: left out");
			}
		});
		out.sort(java.util.Comparator.comparing(k -> k.getName().toLowerCase(java.util.Locale.ROOT)));
		return new PlaceKinds(out);
	}

	/** Per map icon id: every tile an object carrying it is placed on (packed, {@link Tiles}), sorted, each once. */
	public static Map<Integer, int[]> find(LoadedCache cache) throws IOException
	{
		Map<Integer, TreeSet<Integer>> found = new TreeMap<>();
		cache.forEachRegion(r ->
		{
			for (Location l : r.getLocations())
			{
				ObjectDefinition d = cache.object(l.getId());
				if (d != null && d.getMapAreaId() >= 0)
				{
					found.computeIfAbsent(d.getMapAreaId(), k -> new TreeSet<>())
						.add(Tiles.pack(l.getPosition().getX(), l.getPosition().getY(), l.getPosition().getZ()));
				}
			}
		});
		Map<Integer, int[]> out = new TreeMap<>();
		found.forEach((id, tiles) -> out.put(id, tiles.stream().mapToInt(Integer::intValue).toArray()));
		return out;
	}

	/** The icon's picture (sprite id) per map icon id; -1 none. */
	public static Map<Integer, Integer> sprites(LoadedCache cache) throws IOException
	{
		Map<Integer, Integer> out = new TreeMap<>();
		cache.mapAreas().forEach((id, a) -> out.put(id, a.getSpriteId()));
		return out;
	}

	/** "x,y,plane x,y,plane ..." */
	public static String tiles(int[] tiles)
	{
		List<String> s = new ArrayList<>();
		for (int t : tiles)
		{
			s.add(Tiles.x(t) + "," + Tiles.y(t) + "," + Tiles.plane(t));
		}
		return String.join(" ", s);
	}
}
