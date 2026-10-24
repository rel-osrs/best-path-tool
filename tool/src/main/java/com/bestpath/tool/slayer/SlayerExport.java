package com.bestpath.tool.slayer;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.core.data.WorldMaps;
import com.bestpath.tool.clue.KillAreas;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The plugin's Slayer file ({@link SlayerPlaces}): the cache's tasks, bosses and Konar areas ({@link SlayerTables})
 * with the places of {@code data/wiki/slayer-places.tsv} and the master names of {@code data/wiki/slayer-masters.tsv}
 * (both from {@code gradlew -p tool wikiSlayer}), task pictures and special loot.
 *
 * <p>A place's Wiki spawn tiles become route points ({@link SlayerPoints}): kept only where the exported map lets a
 * player stand (moved a little, put on another plane or where a world map draws them back, else dropped), then grouped
 * like the clue kill areas ({@link KillAreas#group}), one point per group. A place with no tile on the map data's
 * ground keeps its Wiki tiles (grouped the same way) and is marked off the map: the plugin shows it without a Go
 * (counted; the map data leaves some areas out, e.g. places reached only by transports not confirmed yet). The
 * hand-made {@value #OVERRIDES} moves a place's Wiki tiles first (place-shift: the Wiki's own drawing of the Chasm of
 * Fire matches no world map of the cache) or leaves a place out (skip-place: an island no one can reach). Each place's
 * own special loot comes from {@link SlayerPlaceLoot}.
 */
public final class SlayerExport
{
	public static final String PLACES = "data/wiki/slayer-places.tsv";
	public static final String MASTERS = "data/wiki/slayer-masters.tsv";
	/** The task pictures: the same items as the RuneLite Slayer plugin's. */
	public static final String ICONS = "data/manual/slayer-icons.tsv";
	/** Special loot shown per master or area, written by hand. */
	public static final String LOOT = "data/manual/slayer-loot.tsv";
	/** Hand-authored corrections of the Wiki's places: place-shift, skip-place. */
	public static final String OVERRIDES = "data/manual/slayer-overrides.tsv";
	/**
	 * Places added by hand in {@value #PLACES}'s columns, read after it (e.g. the Wilderness spider places the Wiki
	 * gives no place for on Krystilia's spider task).
	 */
	public static final String EXTRA_PLACES = "data/manual/slayer-extra-places.tsv";
	/** The monsters at each place, from the Wiki ({@code gradlew -p tool wikiSlayerDrops}). */
	public static final String PLACE_MONSTERS = "data/wiki/slayer-place-monsters.tsv";
	/** The collection log items those monsters drop, from their Wiki pages ({@code gradlew -p tool wikiSlayerDrops}). */
	public static final String DROPS = "data/wiki/slayer-drops.tsv";

	public static final class Result
	{
		public SlayerPlaces data = SlayerPlaces.NONE;
		public int tasks;
		public int tasksWithPlaces;
		public int places;
		public int points;
		/** Places none of whose tiles is ground in the map data (areas the map data leaves out, instances): kept, marked. */
		public int placesOffMap;
		public int tilesMoved;
		/** Tiles put on another plane (the Wiki's plane has no ground there, another plane has). */
		public int tilesOtherPlane;
		/** Tiles the Wiki gives where a world map draws them, put on their game tile ({@link SlayerPoints#drawnBack}). */
		public int tilesDrawnBack;
		public int tilesDropped;
		/** Places moved (place-shift) or left out (skip-place) by {@value #OVERRIDES}. */
		public int placesShifted;
		public int placesSkipped;
		/** Places from {@value #EXTRA_PLACES}. */
		public int placesAdded;
		public int icons;
		public int loot;
		/** Of {@link #loot}: the lines for one place ({@link SlayerPlaces#LOOT_PLACE}). */
		public int placeLoot;
		public final List<String> problems = new ArrayList<>();
	}

	private SlayerExport()
	{
	}

	/** Null when the workspace has no {@value #PLACES} (nothing to export). */
	public static Result build(SlayerTables tables, File ws, CollisionMap map) throws IOException
	{
		return build(tables, ws, map, id -> "?");
	}

	/**
	 * As {@link #build(SlayerTables, File, CollisionMap)}; {@code itemName}: the game cache's item names (null or "null":
	 * not an item), to check the icons' and the loot's item numbers.
	 */
	public static Result build(SlayerTables tables, File ws, CollisionMap map, java.util.function.IntFunction<String> itemName)
		throws IOException
	{
		return build(tables, ws, map, WorldMaps.NONE, itemName);
	}

	/**
	 * As {@link #build(SlayerTables, File, CollisionMap, java.util.function.IntFunction)}; {@code worldMaps}: where the
	 * game's world maps draw each area (from the cache), to put Wiki tiles given where a map draws them on their game tile.
	 */
	public static Result build(SlayerTables tables, File ws, CollisionMap map, WorldMaps worldMaps,
		java.util.function.IntFunction<String> itemName) throws IOException
	{
		return build(tables, ws, map, worldMaps, itemName, CollectionLog.NONE, Map.of());
	}

	/**
	 * As {@link #build(SlayerTables, File, CollisionMap, WorldMaps, java.util.function.IntFunction)}; {@code log}: the
	 * game's collection log and {@code itemIds}: the cache's item numbers by lower-case name, for each place's own special
	 * loot ({@link SlayerPlaceLoot#placeLoot}; none with an empty log).
	 */
	public static Result build(SlayerTables tables, File ws, CollisionMap map, WorldMaps worldMaps,
		java.util.function.IntFunction<String> itemName, CollectionLog log, Map<String, List<Integer>> itemIds) throws IOException
	{
		File placesFile = new File(ws, PLACES);
		if (!placesFile.isFile())
		{
			return null;
		}
		Result r = new Result();
		Map<Integer, String> masters = new LinkedHashMap<>();
		for (Map<String, String> row : rows(new File(ws, MASTERS)))
		{
			masters.put(Integer.parseInt(row.get("master")), row.get("name"));
		}
		List<SlayerPlaces.Task> tasks = new ArrayList<>();
		for (SlayerTables.Task t : tables.tasks.values())
		{
			tasks.add(new SlayerPlaces.Task(t.row, t.id, t.name));
		}
		Map<Integer, String> areas = new LinkedHashMap<>();
		for (SlayerTables.Area a : tables.areas.values())
		{
			areas.put(a.id, a.name);
		}
		List<SlayerPlaces.Place> places = places(tables, ws, placesFile, map, worldMaps, r);
		Set<Integer> withPlaces = new LinkedHashSet<>();
		for (SlayerPlaces.Place p : places)
		{
			withPlaces.add(p.getTask());
		}
		Map<Integer, Integer> icons = icons(tasks, rows(new File(ws, ICONS)), itemName, r);
		List<SlayerPlaces.Loot> loot = loot(masters, rows(new File(ws, LOOT)), itemName, r);
		if (!log.isEmpty())
		{
			List<SlayerPlaces.Loot> own = SlayerPlaceLoot.placeLoot(tables, places, rows(new File(ws, PLACE_MONSTERS)), rows(new File(ws, DROPS)),
				log, itemIds, loot, r);
			r.placeLoot = own.size();
			loot.addAll(own);
		}
		r.icons = icons.size();
		r.loot = loot.size();
		r.data = new SlayerPlaces(masters, tasks, tables.bosses, areas, places, icons, loot);
		r.tasks = tasks.size();
		r.tasksWithPlaces = withPlaces.size();
		r.places = places.size();
		for (SlayerTables.Task t : tables.tasks.values())
		{
			if (t.id != SlayerTables.BOSS_TASK && !withPlaces.contains(t.row))
			{
				r.problems.add("no place for " + t.name);
			}
		}
		return r;
	}

	/**
	 * The places of {@value #PLACES} and then {@value #EXTRA_PLACES}, with {@value #OVERRIDES} applied; an override that
	 * matches no place is a problem.
	 */
	private static List<SlayerPlaces.Place> places(SlayerTables tables, File ws, File placesFile, CollisionMap map, WorldMaps worldMaps,
		Result r) throws IOException
	{
		Map<String, Map<String, String>> overrides = new LinkedHashMap<>();
		for (Map<String, String> o : rows(new File(ws, OVERRIDES)))
		{
			overrides.put(o.get("task") + " " + o.get("place"), o);
		}
		Set<String> overridesUsed = new LinkedHashSet<>();
		List<Map<String, String>> placeRows = rows(placesFile);
		List<Map<String, String>> extra = rows(new File(ws, EXTRA_PLACES));
		r.placesAdded = extra.size();
		placeRows.addAll(extra);
		List<SlayerPlaces.Place> places = new ArrayList<>();
		for (Map<String, String> row : placeRows)
		{
			SlayerPlaces.Place p = place(row, tables, overrides, overridesUsed, map, worldMaps, r);
			if (p != null)
			{
				places.add(p);
			}
		}
		for (String k : overrides.keySet())
		{
			if (!overridesUsed.contains(k))
			{
				r.problems.add("override matches no place: " + k);
			}
		}
		return places;
	}

	/**
	 * One row's place with its points; null when it is left out: a task the cache does not have, a skip-place override,
	 * or no tiles at all.
	 */
	private static SlayerPlaces.Place place(Map<String, String> row, SlayerTables tables, Map<String, Map<String, String>> overrides,
		Set<String> overridesUsed, CollisionMap map, WorldMaps worldMaps, Result r)
	{
		int task = Integer.parseInt(row.get("task"));
		if (!tables.tasks.containsKey(task))
		{
			r.problems.add("place " + row.get("place") + ": task row " + task + " is not in the cache");
			return null;
		}
		String tiles = row.get("tiles");
		Map<String, String> o = overrides.get(task + " " + row.get("place"));
		if (o != null)
		{
			overridesUsed.add(task + " " + row.get("place"));
			if ("skip-place".equals(o.get("change")))
			{
				r.placesSkipped++;
				return null;
			}
			tiles = overridden(tiles, o, row, r);
		}
		int[] points = SlayerPoints.points(tiles, map, worldMaps, r);
		boolean onMap = points.length > 0;
		if (!onMap)
		{
			points = SlayerPoints.points(tiles, null, r);
			if (points.length == 0)
			{
				return null; // a Wiki place without tiles
			}
			r.placesOffMap++;
		}
		String area = row.get("konar_area");
		r.points += points.length;
		return new SlayerPlaces.Place(task, row.get("place"), area == null || area.equals("-") ? -1 : Integer.parseInt(area),
			row.get("amount"), row.get("multi"), row.get("cannon"), row.get("safespot"), points, onMap, row.get("monsters"),
			row.get("page") + "@" + row.get("revision"), SlayerPlaces.plainNotes(row.get("notes")));
	}

	/** The tiles after a place-shift override; a bad value or an unknown change is a problem (the tiles stay). */
	private static String overridden(String tiles, Map<String, String> o, Map<String, String> row, Result r)
	{
		if ("place-shift".equals(o.get("change")))
		{
			try
			{
				String moved = shifted(tiles, o.get("value"));
				r.placesShifted++;
				return moved;
			}
			catch (RuntimeException e)
			{
				r.problems.add("override for " + row.get("place") + ": bad place-shift value " + o.get("value"));
			}
		}
		else
		{
			r.problems.add("override for " + row.get("place") + ": unknown change " + o.get("change"));
		}
		return tiles;
	}

	/**
	 * Each task's picture: the icon row with the same name (ignoring case), else the row listing the task's monster
	 * ("Bronze dragons": "Bronze dragon" in "Metal dragons"); numbers the cache has no item for are left out.
	 */
	static Map<Integer, Integer> icons(List<SlayerPlaces.Task> tasks, List<Map<String, String>> rows,
		java.util.function.IntFunction<String> itemName, Result r)
	{
		Map<String, Integer> byName = new HashMap<>();
		Map<String, Integer> byTarget = new HashMap<>();
		for (Map<String, String> row : rows)
		{
			int item = Integer.parseInt(row.get("item"));
			String n = itemName.apply(item);
			if (n == null || n.equals("null"))
			{
				r.problems.add("icon for " + row.get("task") + ": no item " + item + " in the cache");
				continue;
			}
			byName.putIfAbsent(row.get("task").toLowerCase(java.util.Locale.ROOT), item);
			String targets = row.getOrDefault("targets", "-");
			for (String t : targets.equals("-") ? new String[0] : targets.split(" / "))
			{
				byTarget.putIfAbsent(t.trim().toLowerCase(java.util.Locale.ROOT), item);
			}
		}
		Map<Integer, Integer> out = new LinkedHashMap<>();
		for (SlayerPlaces.Task t : tasks)
		{
			String name = t.getName().toLowerCase(java.util.Locale.ROOT);
			Integer item = byName.get(name);
			if (item == null && name.endsWith("s"))
			{
				item = byTarget.get(name.substring(0, name.length() - 1));
			}
			if (item != null)
			{
				out.put(t.getRow(), item);
			}
		}
		return out;
	}

	/** The special loot lines; a master by name ("Konar" -> its number); item numbers checked in the cache. */
	static List<SlayerPlaces.Loot> loot(Map<Integer, String> masters, List<Map<String, String>> rows,
		java.util.function.IntFunction<String> itemName, Result r)
	{
		List<SlayerPlaces.Loot> out = new ArrayList<>();
		for (Map<String, String> row : rows)
		{
			String kind = row.get("kind");
			String key = row.get("key");
			if (SlayerPlaces.LOOT_MASTER.equals(kind))
			{
				String number = null;
				for (Map.Entry<Integer, String> m : masters.entrySet())
				{
					if (m.getValue().equalsIgnoreCase(key))
					{
						number = String.valueOf(m.getKey());
					}
				}
				if (number == null)
				{
					r.problems.add("loot: no master called " + key);
					continue;
				}
				key = number;
			}
			else if (!SlayerPlaces.LOOT_AREA.equals(kind))
			{
				r.problems.add("loot: unknown kind " + kind);
				continue;
			}
			List<Integer> items = new ArrayList<>();
			for (String s : row.get("items").trim().split(" +"))
			{
				int item = Integer.parseInt(s);
				String n = itemName.apply(item);
				if (n == null || n.equals("null"))
				{
					r.problems.add("loot " + row.get("key") + ": no item " + item + " in the cache");
				}
				else
				{
					items.add(item);
				}
			}
			if (!items.isEmpty())
			{
				out.add(new SlayerPlaces.Loot(kind, key, items.stream().mapToInt(Integer::intValue).toArray(), row.get("note")));
			}
		}
		return out;
	}

	/** The tiles ("x,y,p x,y,p") moved by a place-shift value "dx,dy,plane" and put on that plane. */
	static String shifted(String tiles, String value)
	{
		String[] v = value.split(",");
		int dx = Integer.parseInt(v[0].trim());
		int dy = Integer.parseInt(v[1].trim());
		int plane = Integer.parseInt(v[2].trim());
		List<String> out = new ArrayList<>();
		for (String s : tiles.trim().split(" +"))
		{
			String[] c = s.split(",");
			out.add((Integer.parseInt(c[0]) + dx) + "," + (Integer.parseInt(c[1]) + dy) + "," + plane);
		}
		return String.join(" ", out);
	}

	/** The rows of a generated tsv (header after the # lines), as column name -> value. */
	static List<Map<String, String>> rows(File f) throws IOException
	{
		List<Map<String, String>> out = new ArrayList<>();
		if (!f.isFile())
		{
			return out;
		}
		String[] names = null;
		for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
		{
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] w = line.split("\t", -1);
			if (names == null)
			{
				names = w;
				continue;
			}
			Map<String, String> m = new HashMap<>();
			for (int i = 0; i < names.length && i < w.length; i++)
			{
				m.put(names[i], w[i].trim());
			}
			out.add(m);
		}
		return out;
	}

	/** "slayer: 161 tasks (155 with places), 840 places, 1,234 points; 12 places left out ..." */
	public static String describe(Result r, int bytes)
	{
		return String.format(java.util.Locale.ROOT, "slayer: %d tasks (%d with places, %d with a picture), %d places, %,d points;"
				+ " %d places off the map data (no ground there yet: kept without a Go), %,d spawn tiles moved onto ground, %,d put on"
				+ " another plane, %,d put where a world map draws them back on their game tile, %,d dropped; %d places moved and %d left"
				+ " out by hand (" + OVERRIDES + "), %d added by hand (" + EXTRA_PLACES + "); %d special loot lines (%d for one place);"
				+ " %,d bytes", r.tasks,
			r.tasksWithPlaces, r.icons, r.places, r.points,
			r.placesOffMap, r.tilesMoved, r.tilesOtherPlane, r.tilesDrawnBack, r.tilesDropped, r.placesShifted, r.placesSkipped, r.placesAdded, r.loot,
			r.placeLoot, bytes);
	}
}
