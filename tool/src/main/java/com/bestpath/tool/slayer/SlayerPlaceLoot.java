package com.bestpath.tool.slayer;

import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.core.data.SlayerPlacesDiagnostics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each Slayer place's own special loot ({@link SlayerExport}): the collection log items the monsters at a place drop
 * that not every place of the same task has.
 */
final class SlayerPlaceLoot
{
	private SlayerPlaceLoot()
	{
	}

	/**
	 * Bosses the collection log names only as a group ("Grotesque Guardians", "Dagannoth Kings"); the others are its
	 * "Bosses" entries ({@link CollectionLog#BOSSES_TAB}) and the boss tasks' monsters.
	 */
	static final Set<String> GROUP_BOSSES = Set.of("Dawn", "Dusk", "Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme");

	/**
	 * Each place's own special loot: what is worth knowing about one place compared with the task's others (some
	 * spiritual creatures drop dragon boots, some do not). Collection log items only, never on a boss task's card, long /
	 * curved bones and champion scrolls left out ({@link #leftOut}).
	 *
	 * <p>For each task that is not a boss task: at each place whose monsters are known
	 * ({@value SlayerExport#PLACE_MONSTERS}), the collection log items its monsters that are not bosses drop
	 * ({@value SlayerExport#DROPS}; a boss: a boss task's monster, a "Bosses" entry of the log, {@link #GROUP_BOSSES}),
	 * less the items an area line already shows there; then only the items not every such place of the task has. A
	 * drop table's version counts where {@link #versionAt} says; a drop its Wiki note limits to one place ("Only
	 * dropped in Ruins of Tapoyauik") only there. One line per group of monsters dropping the same items there: "From
	 * Vyrewatch Sentinel.".
	 */
	static List<SlayerPlaces.Loot> placeLoot(SlayerTables tables, List<SlayerPlaces.Place> places, List<Map<String, String>> monsterRows,
		List<Map<String, String>> dropRows, CollectionLog log, Map<String, List<Integer>> itemIds, List<SlayerPlaces.Loot> other,
		SlayerExport.Result r)
	{
		Map<String, List<String>> at = new HashMap<>();
		Map<String, List<String>> placesOf = new HashMap<>();
		Set<String> bosses = bosses(log);
		readMonsters(tables, monsterRows, at, placesOf, bosses);
		Map<String, List<String[]>> drops = drops(dropRows, log, itemIds, r);
		Map<Integer, List<SlayerPlaces.Place>> byTask = new LinkedHashMap<>();
		for (SlayerPlaces.Place p : places)
		{
			byTask.computeIfAbsent(p.getTask(), k -> new ArrayList<>()).add(p);
		}
		List<SlayerPlaces.Loot> out = new ArrayList<>();
		for (Map.Entry<Integer, List<SlayerPlaces.Place>> e : byTask.entrySet())
		{
			SlayerTables.Task task = tables.tasks.get(e.getKey());
			if (task == null || task.id < 0)
			{
				continue; // a boss task: no uniques on its card
			}
			Map<SlayerPlaces.Place, Map<Integer, Set<String>>> itemsAt = new LinkedHashMap<>();
			for (SlayerPlaces.Place p : e.getValue())
			{
				List<String> here = new ArrayList<>(at.getOrDefault(SlayerPlaces.placeKey(p), List.of()));
				here.removeIf(bosses::contains);
				if (here.isEmpty())
				{
					continue; // monsters not known, or only bosses (Vorkath on Ungael): neither shown nor compared
				}
				itemsAt.put(p, itemsAt(p, here, drops, placesOf, shownAt(p, other)));
			}
			addDifferences(itemsAt, out);
		}
		return out;
	}

	/** The bosses: {@link #GROUP_BOSSES} and each name of the log's "Bosses" entries ("Dawn and Dusk"), any case. */
	private static Set<String> bosses(CollectionLog log)
	{
		Set<String> bosses = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER); // "Shellbane Gryphon" / "Shellbane gryphon"
		bosses.addAll(GROUP_BOSSES);
		for (String e : log.entryNames(CollectionLog.BOSSES_TAB))
		{
			for (String part : e.split(" and |, "))
			{
				bosses.add(part.trim());
			}
		}
		return bosses;
	}

	/**
	 * Reads the monsters at each place into {@code at} ("task:place" -> monsters) and the places of each monster into
	 * {@code placesOf}; a boss task's monsters are added to {@code bosses}.
	 */
	private static void readMonsters(SlayerTables tables, List<Map<String, String>> monsterRows, Map<String, List<String>> at,
		Map<String, List<String>> placesOf, Set<String> bosses)
	{
		for (Map<String, String> row : monsterRows)
		{
			String m = row.getOrDefault("monsters", "-");
			List<String> list = m.equals("-") || m.isEmpty() ? List.of() : List.of(m.split(" / "));
			at.put(row.get("task") + ":" + row.get("place"), list);
			SlayerTables.Task t = tables.tasks.get(Integer.parseInt(row.get("task")));
			for (String one : list)
			{
				placesOf.computeIfAbsent(one, k -> new ArrayList<>()).add(row.get("place"));
				if (t != null && t.id < 0)
				{
					bosses.add(one);
				}
			}
		}
	}

	/**
	 * The drops per monster page, each {version, item id, plain_drops, only_in}: the items the collection log has (an
	 * item it has under none of its numbers is a problem); the items left out ({@link #leftOut}) skipped.
	 */
	private static Map<String, List<String[]>> drops(List<Map<String, String>> dropRows, CollectionLog log, Map<String, List<Integer>> itemIds,
		SlayerExport.Result r)
	{
		Map<String, List<String[]>> drops = new HashMap<>();
		Set<String> unknownItems = new LinkedHashSet<>();
		for (Map<String, String> row : dropRows)
		{
			String name = row.get("item");
			if (leftOut(name))
			{
				continue;
			}
			Integer id = null;
			for (int i : itemIds.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), List.of()))
			{
				if (id == null && log.has(i))
				{
					id = i;
				}
			}
			if (id == null)
			{
				unknownItems.add(name);
				continue;
			}
			drops.computeIfAbsent(row.get("monster"), k -> new ArrayList<>()).add(new String[]{row.get("version"), String.valueOf(id),
				row.getOrDefault("plain_drops", "yes"), row.getOrDefault("only_in", "-")});
		}
		for (String u : unknownItems)
		{
			r.problems.add("special loot: " + u + " is not in this cache's collection log");
		}
		return drops;
	}

	/** The items an area loot line already shows at this place. */
	private static Set<Integer> shownAt(SlayerPlaces.Place p, List<SlayerPlaces.Loot> other)
	{
		Set<Integer> shown = new LinkedHashSet<>();
		for (SlayerPlaces.Loot l : other)
		{
			if (SlayerPlaces.LOOT_AREA.equals(SlayerPlacesDiagnostics.kind(l)) && p.getName().startsWith(SlayerPlacesDiagnostics.key(l)))
			{
				for (int i : l.getItems())
				{
					shown.add(i);
				}
			}
		}
		return shown;
	}

	/** The items the monsters {@code here} drop at this place (not already {@code shown}), each with the monsters dropping it. */
	private static Map<Integer, Set<String>> itemsAt(SlayerPlaces.Place p, List<String> here, Map<String, List<String[]>> drops,
		Map<String, List<String>> placesOf, Set<Integer> shown)
	{
		Map<Integer, Set<String>> items = new LinkedHashMap<>();
		for (String m : here)
		{
			for (String[] d : drops.getOrDefault(m, List.of()))
			{
				int item = Integer.parseInt(d[1]);
				if (!shown.contains(item) && versionAt(d[0], "yes".equals(d[2]), p.getName(), placesOf.getOrDefault(m, List.of()))
					&& (d[3].equals("-") || letters(p.getName()).contains(letters(d[3]))))
				{
					items.computeIfAbsent(item, k -> new LinkedHashSet<>()).add(monsterName(m));
				}
			}
		}
		return items;
	}

	/**
	 * Adds a loot line per place and group of monsters for the items not every compared place of the task has:
	 * "From Vyrewatch Sentinel.".
	 */
	private static void addDifferences(Map<SlayerPlaces.Place, Map<Integer, Set<String>>> itemsAt, List<SlayerPlaces.Loot> out)
	{
		Set<Integer> common = null;
		for (Map<Integer, Set<String>> items : itemsAt.values())
		{
			if (common == null)
			{
				common = new LinkedHashSet<>(items.keySet());
			}
			else
			{
				common.retainAll(items.keySet());
			}
		}
		for (Map.Entry<SlayerPlaces.Place, Map<Integer, Set<String>>> pe : itemsAt.entrySet())
		{
			Map<String, List<Integer>> byMonsters = new LinkedHashMap<>();
			for (Map.Entry<Integer, Set<String>> ie : pe.getValue().entrySet())
			{
				if (!common.contains(ie.getKey()))
				{
					byMonsters.computeIfAbsent("From " + String.join(", ", ie.getValue()) + ".", k -> new ArrayList<>()).add(ie.getKey());
				}
			}
			for (Map.Entry<String, List<Integer>> g : byMonsters.entrySet())
			{
				out.add(new SlayerPlaces.Loot(SlayerPlaces.LOOT_PLACE, SlayerPlaces.placeKey(pe.getKey()),
					g.getValue().stream().mapToInt(Integer::intValue).toArray(), g.getKey()));
			}
		}
	}

	/** Collection log items left out: nearly worthless, and dropped at 50 to 80 places each. */
	static boolean leftOut(String item)
	{
		String n = item.toLowerCase(java.util.Locale.ROOT);
		return n.equals("long bone") || n.equals("curved bone") || n.endsWith("champion scroll");
	}

	/**
	 * A drop table counts at this place: the plain "Drops" ({@code version} "-") everywhere; "Drops (X)" where the place's
	 * name has X ("Wilderness Slayer Cave"); an X no place of the monster has counts everywhere when the page has no plain
	 * table (levels: "Level 21", "Level 23"), else nowhere ("Ancient Prison": the spiritual creatures there are no Slayer
	 * place yet).
	 */
	static boolean versionAt(String version, boolean pagePlain, String place, List<String> monsterPlaces)
	{
		if (version == null || version.equals("-") || version.isEmpty())
		{
			return true;
		}
		String v = letters(version);
		if (letters(place).contains(v))
		{
			return true;
		}
		for (String other : monsterPlaces)
		{
			if (letters(other).contains(v))
			{
				return false;
			}
		}
		return !pagePlain;
	}

	private static String letters(String s)
	{
		return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/** "Skeleton (Ape Atoll)" -> "Skeleton". */
	static String monsterName(String page)
	{
		return page.replaceFirst("\\s*\\([^)]*\\)$", "");
	}
}
