package com.bestpath.tool.wiki;

import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.slayer.SlayerTables;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DEVELOPMENT-TIME ONLY (project rule 1: Wiki data is gathered only at development time): where each Slayer task's
 * monsters are, one entry per group of monsters. The cache has the tasks, which master gives which, and Konar's
 * areas ({@link SlayerTables}) but no NPC spawns, so the places come from the Wiki: each task page's location table
 * (one row per place: name, how many, multi-combat, cannon, safespot, notes) with its map pins, or, where a row's map
 * has none, the {@code {{LocLine}}} spawns of its monsters at that place. A task page without a location table gets
 * one place per LocLine location. Bosses use the boss's own page. Never used by the plugin at run time.
 *
 * <p>Polite like {@link WikiClueKillsCli}: the MediaWiki API only, a descriptive User-Agent, at least 3 s between
 * requests, 50 pages per request, every response kept in {@code <wikiCache>/slayer/}, counted in the shared daily
 * budget. A full run is about 10 requests.
 *
 * <p>Output (CC BY-NC-SA 3.0, page and revision per line): {@code data/wiki/slayer-places.tsv} and
 * {@code data/wiki/slayer-masters.tsv} (which master number is which master: the cache has no names, so each number is
 * matched to the Wiki's amounts per master). {@code gradlew -p tool wikiSlayer --args="<cacheDir> <repo>/cache/wiki"}
 */
public final class WikiSlayerCli
{
	static final String PLACES = "data/wiki/slayer-places.tsv";
	static final String MASTERS = "data/wiki/slayer-masters.tsv";
	static final String PLACES_HEADER = "task\ttask_id\ttask_name\tplace\tkonar_area\tamount\tmulti\tcannon\tsafespot\tmonsters\ttiles"
		+ "\tpage\trevision\ttile_pages\tnotes";

	/** Boss tasks whose Wiki page is not the task's name without "the" (cache name -> page). */
	private static final Map<String, String> BOSS_PAGES = Map.of(
		"Crazy Archaeologists", "Crazy archaeologist",
		"Cave Kraken boss", "Kraken",
		"Barrows brothers", "Barrows",
		"Thermonuclear Smoke Devil", "Thermonuclear smoke devil",
		"Dagannoth Kings", "Dagannoth Rex",
		"Shellbane Gryphon", "Shellbane gryphon");

	/** Tasks whose Wiki task page has another name (cache name -> page). */
	private static final Map<String, String> TASK_PAGES = Map.of("Mutated Zygomites", "Slayer task/Zygomites");

	/** Tasks whose monsters neither a task page nor the "Slayer task" list names (cache name -> monster pages). */
	private static final Map<String, List<String>> TASK_MONSTERS = Map.of("Revenants", List.of("Revenant imp", "Revenant goblin",
		"Revenant pyrefiend", "Revenant hobgoblin", "Revenant cyclops", "Revenant hellhound", "Revenant demon", "Revenant ork",
		"Revenant dark beast", "Revenant knight", "Revenant dragon"));

	/**
	 * Wiki place names that are a Konar area under another name (key of the area's name -> keys of other names), checked
	 * by the run's report.
	 */
	static final Map<String, List<String>> AREA_ALIASES = Map.of(
		"strongholdslayerdungeon", List.of("strongholdslayercave"),
		"karuulmslayerdungeon", List.of("mountkaruulm", "karuulm"),
		"fremennikisles", List.of("jatizso", "neitiznot"),
		"mythsguilddungeon", List.of("mythsguild"),
		"fossilisland", List.of("wyverncave", "fossilisland"),
		"brimhavendungeon", List.of("brimhaven"),
		"godwarsdungeon", List.of("godwars"),
		"forthosdungeon", List.of("forthos"),
		"lithkrenvault", List.of("lithkren"));

	/** One place of one task: one entry per group of monsters. */
	static final class Place
	{
		final SlayerTables.Task task;
		String name;
		String konarArea = "-";
		String amount = "?";
		String multi = "?";
		String cannon = "?";
		String safespot = "?";
		final Set<String> monsters = new LinkedHashSet<>();
		/** {x, y, plane}, no repeats, in Wiki order. */
		final List<int[]> tiles = new ArrayList<>();
		String page;
		String revision;
		final Set<String> tilePages = new LinkedHashSet<>();
		String notes = "";
		/** Text the Konar areas are matched against (row name, link, map location). */
		final List<String> names = new ArrayList<>();

		Place(SlayerTables.Task task, String name)
		{
			this.task = task;
			this.name = name;
			names.add(name);
		}

		void add(int x, int y, int plane)
		{
			for (int[] t : tiles)
			{
				if (t[0] == x && t[1] == y && t[2] == plane)
				{
					return;
				}
			}
			tiles.add(new int[]{x, y, plane});
		}
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			System.err.println("usage: wikiSlayer <cacheDir> <wikiCache>");
			System.exit(2);
		}
		SlayerTables tables;
		try (LoadedCache cache = LoadedCache.open(new File(args[0])))
		{
			tables = SlayerTables.read(cache);
		}
		WikiApi api = new WikiApi(new File(args[1]), "slayer", "Slayer monster places");
		new WikiSlayerCli().run(tables, api);
	}

	private void run(SlayerTables tables, WikiApi api) throws Exception
	{
		String[] list = api.pages(List.of("Slayer task")).get("Slayer task");
		Map<String, String> taskPages = SlayerPages.taskPages(list == null ? "" : list[2]);
		Map<String, List<String>> listed = SlayerPages.assignmentMonsters(list == null ? "" : list[2]);
		Map<SlayerTables.Task, String> pageOf = new LinkedHashMap<>();
		for (SlayerTables.Task t : tables.tasks.values())
		{
			if (t.id == SlayerTables.BOSS_TASK)
			{
				continue; // "a boss": the boss is named through table 116, each has its own row
			}
			pageOf.put(t, t.id < 0 ? bossPage(t.name) : taskPage(t.name, taskPages));
		}
		Map<String, String[]> pages = api.pages(new ArrayList<>(new LinkedHashSet<>(pageOf.values())));

		// the monsters each task page names, and their pages
		Set<String> monsters = new LinkedHashSet<>();
		Map<SlayerTables.Task, List<SlayerPages.Row>> rows = new LinkedHashMap<>();
		Map<SlayerTables.Task, List<String>> variants = new LinkedHashMap<>();
		readTaskPages(pageOf, pages, listed, monsters, rows, variants);
		Map<String, String[]> monsterPages = api.pages(new ArrayList<>(monsters));

		List<Place> places = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		for (Map.Entry<SlayerTables.Task, String> e : pageOf.entrySet())
		{
			places.addAll(taskPlaces(e.getKey(), e.getValue(), pages, list, rows, variants, monsterPages, tables, problems));
		}

		List<String[]> masters = masters(tables, pageOf, pages);

		Files.writeString(new File(PLACES).toPath(), placesText(places), StandardCharsets.UTF_8);
		Files.writeString(new File(MASTERS).toPath(), mastersText(masters), StandardCharsets.UTF_8);
		int withTiles = 0;
		for (Place pl : places)
		{
			withTiles += pl.tiles.isEmpty() ? 0 : 1;
		}
		for (String pr : problems)
		{
			System.out.println("  CHECK: " + pr);
		}
		for (String[] m : masters)
		{
			System.out.printf(Locale.ROOT, "  master %s = %s (%s of %s amounts match)%n", m[0], m[1], m[3], m[4]);
		}
		System.out.printf(Locale.ROOT, "%d tasks, %d places (%d with tiles) -> %s; %d web requests%n", pageOf.size(), places.size(),
			withTiles, PLACES, api.requests());
	}

	/**
	 * For each task that is not a boss: its monsters ({@code variants}, also added to {@code monsters}) from its page's
	 * variants table or else the "Slayer task" list, and its page's location table rows ({@code rows}; their map
	 * monsters added to {@code monsters} too).
	 */
	private static void readTaskPages(Map<SlayerTables.Task, String> pageOf, Map<String, String[]> pages, Map<String, List<String>> listed,
		Set<String> monsters, Map<SlayerTables.Task, List<SlayerPages.Row>> rows, Map<SlayerTables.Task, List<String>> variants)
	{
		for (Map.Entry<SlayerTables.Task, String> e : pageOf.entrySet())
		{
			String[] p = pages.get(e.getValue());
			if (e.getKey().id < 0)
			{
				continue;
			}
			if (p == null)
			{
				// no task page of its own: the monsters its row of the "Slayer task" list links
				List<String> v = listedMonsters(e.getKey().name, listed);
				variants.put(e.getKey(), v);
				monsters.addAll(v);
				continue;
			}
			List<String> v = SlayerPages.variants(p[2]);
			if (v.isEmpty())
			{
				v = listedMonsters(e.getKey().name, listed); // a task page without a variants table ("Drakes")
			}
			List<SlayerPages.Row> r = SlayerPages.locationRows(p[2]);
			variants.put(e.getKey(), v);
			rows.put(e.getKey(), r);
			monsters.addAll(v);
			for (SlayerPages.Row row : r)
			{
				String n = row.map.get("name");
				if (n != null && !n.isEmpty())
				{
					monsters.add(capital(WikiClueKillsCli.plainLocation(n)));
				}
			}
		}
	}

	/**
	 * One task's places: from its page's location table, else from its monsters' spawns (a boss: its page's), with
	 * their Konar areas and places for the Konar areas the page leaves out; none when the task has no page.
	 */
	private static List<Place> taskPlaces(SlayerTables.Task t, String pageName, Map<String, String[]> pages, String[] list,
		Map<SlayerTables.Task, List<SlayerPages.Row>> rows, Map<SlayerTables.Task, List<String>> variants, Map<String, String[]> monsterPages,
		SlayerTables tables, List<String> problems)
	{
		String[] p = pages.get(pageName);
		if (p == null && t.id >= 0 && list != null && !variants.get(t).isEmpty())
		{
			p = list; // the "Slayer task" list named its monsters
		}
		if (p == null)
		{
			problems.add(t.name + ": no Wiki page " + pageName);
			return List.of();
		}
		List<Place> found = t.id < 0 ? fromSpawns(t, List.of(pageName), pages, p)
			: fromTable(t, rows.getOrDefault(t, List.of()), variants.get(t), monsterPages, p, problems);
		if (found.isEmpty() && t.id >= 0)
		{
			found = fromSpawns(t, variants.get(t), monsterPages, p);
		}
		for (Place pl : found)
		{
			konarArea(pl, tables);
		}
		if (t.id >= 0)
		{
			found.addAll(konarPlaces(t, found, variants.get(t), monsterPages, p, tables));
		}
		checkKonar(t, found, tables, problems);
		if (found.stream().allMatch(pl -> pl.tiles.isEmpty()))
		{
			problems.add(t.name + ": no place with tiles (page " + p[0] + ")");
		}
		return found;
	}

	/** The task's page from the "Slayer task" page's list, or "Slayer task/<Name>" in sentence case. */
	static String taskPage(String name, Map<String, String> taskPages)
	{
		String page = TASK_PAGES.containsKey(name) ? TASK_PAGES.get(name) : taskPages.get(name.toLowerCase(Locale.ROOT));
		if (page != null)
		{
			return page;
		}
		for (Map.Entry<String, String> e : taskPages.entrySet())
		{
			if (SlayerPages.sameName(e.getKey(), name))
			{
				return e.getValue();
			}
		}
		return "Slayer task/" + name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
	}

	/** The monster pages the "Slayer task" list links for this task (matched by name), or none. */
	static List<String> listedMonsters(String name, Map<String, List<String>> listed)
	{
		if (TASK_MONSTERS.containsKey(name))
		{
			return TASK_MONSTERS.get(name);
		}
		for (Map.Entry<String, List<String>> e : listed.entrySet())
		{
			if (SlayerPages.sameName(e.getKey(), name) && !e.getValue().isEmpty())
			{
				List<String> out = new ArrayList<>();
				for (String m : e.getValue())
				{
					out.add(capital(m));
				}
				return out;
			}
		}
		return List.of(singular(name));
	}

	/** "Iron Dragons" -> "Iron dragon", "Wolves" -> "Wolf": the usual name of one monster's page. */
	static String singular(String name)
	{
		String n = name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
		if (n.endsWith("ves"))
		{
			return n.substring(0, n.length() - 3) + "f";
		}
		return n.endsWith("s") ? n.substring(0, n.length() - 1) : n;
	}

	/** A boss task's page: its name without "the" ("The Giant Mole" -> "Giant Mole"), or {@link #BOSS_PAGES}. */
	static String bossPage(String name)
	{
		String n = name.replaceFirst("(?i)^the ", "");
		String page = BOSS_PAGES.get(n);
		return page != null ? page : capital(n);
	}

	private static String capital(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** The places of a task page's location table, each row's tiles from its pins or its monsters' LocLines. */
	static List<Place> fromTable(SlayerTables.Task t, List<SlayerPages.Row> rows, List<String> variants, Map<String, String[]> monsterPages,
		String[] page, List<String> problems)
	{
		List<Place> out = new ArrayList<>();
		Set<String> names = new LinkedHashSet<>();
		for (SlayerPages.Row r : rows)
		{
			String name = r.location;
			for (int i = 2; !names.add(name.toLowerCase(Locale.ROOT)); i++)
			{
				name = r.location + " (" + i + ")";
			}
			Place pl = new Place(t, name);
			pl.names.add(r.link);
			pl.amount = r.amount.isEmpty() ? "?" : r.amount;
			pl.multi = r.multi;
			pl.cannon = r.cannon;
			pl.safespot = r.safespot;
			pl.notes = r.notes;
			pl.page = page[0];
			pl.revision = page[1];
			String mapName = r.map.getOrDefault("name", "");
			String mapLocation = WikiClueKillsCli.plainLocation(r.map.getOrDefault("location", ""));
			if (!mapLocation.isEmpty())
			{
				pl.names.add(mapLocation);
			}
			int mapPlane = intOr(r.map.get("plane"), 0);
			if (!r.pins.isEmpty())
			{
				for (int[] xy : r.pins)
				{
					pl.add(xy[0], xy[1], mapPlane);
				}
				pl.monsters.addAll(mapName.isEmpty() ? variants : List.of(capital(WikiClueKillsCli.plainLocation(mapName))));
			}
			else
			{
				List<String> from = mapName.isEmpty() ? variants : List.of(capital(WikiClueKillsCli.plainLocation(mapName)));
				if (!spawnsAt(pl, r, from, mapLocation, monsterPages) && !mapName.isEmpty())
				{
					spawnsAt(pl, r, variants, mapLocation, monsterPages);
				}
			}
			if (pl.tiles.isEmpty())
			{
				problems.add(t.name + " / " + pl.name + ": no tiles (no pins, no matching LocLine)");
			}
			out.add(pl);
		}
		return out;
	}

	/**
	 * Adds the LocLine spawns of these monster pages that are at this row's place: the map's location text exactly, or
	 * else a location naming the row's link (on the map's plane and map layer when it gives them). True if any.
	 */
	private static boolean spawnsAt(Place pl, SlayerPages.Row r, List<String> from, String mapLocation, Map<String, String[]> monsterPages)
	{
		List<Object[]> exact = new ArrayList<>();
		List<Object[]> loose = new ArrayList<>();
		String want = SlayerPages.key(mapLocation);
		String link = SlayerPages.key(r.link);
		for (String m : from)
		{
			String[] mp = monsterPages.get(m);
			if (mp == null)
			{
				continue;
			}
			for (WikiClueKillsCli.Spawn s : SlayerPages.spawns(mp[2]))
			{
				String at = SlayerPages.key(s.location);
				if (!want.isEmpty() && at.equals(want))
				{
					exact.add(new Object[]{m, mp, s});
				}
				else if (!link.isEmpty() && at.contains(link))
				{
					loose.add(new Object[]{m, mp, s});
				}
			}
		}
		List<Object[]> use = exact;
		if (use.isEmpty())
		{
			use = narrow(loose, r.map.get("plane"), r.map.get("mapid"));
		}
		for (Object[] o : use)
		{
			WikiClueKillsCli.Spawn s = (WikiClueKillsCli.Spawn) o[2];
			String[] mp = (String[]) o[1];
			pl.add(s.x, s.y, s.plane);
			pl.monsters.add((String) o[0]);
			pl.tilePages.add(mp[0] + "@" + mp[1]);
		}
		return !use.isEmpty();
	}

	/** The spawns on the map's plane and map layer, when it gives them and some are there. */
	private static List<Object[]> narrow(List<Object[]> spawns, String plane, String mapId)
	{
		List<Object[]> out = spawns;
		if (plane != null && !plane.isBlank())
		{
			List<Object[]> on = new ArrayList<>();
			for (Object[] o : out)
			{
				if (((WikiClueKillsCli.Spawn) o[2]).plane == intOr(plane, 0))
				{
					on.add(o);
				}
			}
			out = on.isEmpty() ? out : on;
		}
		if (mapId != null && !mapId.isBlank())
		{
			List<Object[]> on = new ArrayList<>();
			for (Object[] o : out)
			{
				if (((WikiClueKillsCli.Spawn) o[2]).mapId.trim().equals(mapId.trim()))
				{
					on.add(o);
				}
			}
			out = on.isEmpty() ? out : on;
		}
		return out;
	}

	/** One place per LocLine location (and plane) of these monster pages (task pages without a location table, bosses). */
	static List<Place> fromSpawns(SlayerTables.Task t, List<String> monsters, Map<String, String[]> monsterPages, String[] taskPage)
	{
		Map<String, Place> byPlace = new LinkedHashMap<>();
		for (String m : monsters == null ? List.<String>of() : monsters)
		{
			String[] mp = monsterPages.get(m);
			if (mp == null)
			{
				continue;
			}
			for (WikiClueKillsCli.Spawn s : SlayerPages.spawns(mp[2]))
			{
				String name = s.location.isEmpty() ? m : s.location;
				Place pl = byPlace.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new Place(t, name));
				pl.page = taskPage[0];
				pl.revision = taskPage[1];
				pl.add(s.x, s.y, s.plane);
				pl.monsters.add(m);
				pl.tilePages.add(mp[0] + "@" + mp[1]);
			}
		}
		return new ArrayList<>(byPlace.values());
	}

	/** The Konar area this place is in (an area Konar may name for the task, matched by name), or "-". */
	static void konarArea(Place pl, SlayerTables tables)
	{
		for (int row : tables.areasFor(pl.task.row))
		{
			SlayerTables.Area a = tables.areas.get(row);
			if (a != null && areaMatches(a.name, pl.names))
			{
				pl.konarArea = String.valueOf(a.id);
				return;
			}
		}
	}

	/** Whether a Konar area's name ("task-only Kalphite Cave") names one of these place names. */
	static boolean areaMatches(String areaName, List<String> names)
	{
		String a = SlayerPages.key(areaName.replaceFirst("(?i)^(in |on )?(the )?(task-only )?", ""));
		List<String> keys = new ArrayList<>();
		keys.add(a);
		keys.addAll(AREA_ALIASES.getOrDefault(a, List.of()));
		for (String n : names)
		{
			String k = SlayerPages.key(n);
			for (String want : keys)
			{
				if (!k.isEmpty() && !want.isEmpty() && (k.contains(want) || (k.length() >= 6 && want.contains(k))))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Places for the Konar areas of a task that no place of its page has (the Wiki's location tables leave some out): the
	 * task's monsters' LocLine spawns whose location names the area, one place per location.
	 */
	static List<Place> konarPlaces(SlayerTables.Task t, List<Place> found, List<String> monsters, Map<String, String[]> monsterPages,
		String[] page, SlayerTables tables)
	{
		List<Place> out = new ArrayList<>();
		for (int row : tables.areasFor(t.row))
		{
			SlayerTables.Area a = tables.areas.get(row);
			if (a == null || found.stream().anyMatch(pl -> pl.konarArea.equals(String.valueOf(a.id))))
			{
				continue;
			}
			for (Place pl : fromSpawns(t, monsters, monsterPages, page))
			{
				if (areaMatches(a.name, pl.names))
				{
					pl.konarArea = String.valueOf(a.id);
					out.add(pl);
				}
			}
		}
		return out;
	}

	/** Reports the Konar areas of a task that no place matched. */
	private static void checkKonar(SlayerTables.Task t, List<Place> places, SlayerTables tables, List<String> problems)
	{
		for (int row : tables.areasFor(t.row))
		{
			SlayerTables.Area a = tables.areas.get(row);
			boolean found = false;
			for (Place pl : places)
			{
				found |= pl.konarArea.equals(String.valueOf(a.id));
			}
			if (!found)
			{
				problems.add(t.name + ": Konar's area \"" + a.name + "\" (" + a.id + ") matches no place");
			}
		}
	}

	/**
	 * Which master number is which master: the cache's amounts per master (table 114) against the amounts each task
	 * page gives per master. Each row: {number, name, wiki key, amounts matched, amounts compared, pages}.
	 */
	static List<String[]> masters(SlayerTables tables, Map<SlayerTables.Task, String> pageOf, Map<String, String[]> pages)
	{
		Map<Integer, Map<String, int[]>> amountsByTask = new LinkedHashMap<>();
		for (Map.Entry<SlayerTables.Task, String> e : pageOf.entrySet())
		{
			String[] p = pages.get(e.getValue());
			if (p != null && e.getKey().id >= 0)
			{
				amountsByTask.put(e.getKey().row, SlayerPages.amounts(p[2]));
			}
		}
		Map<Integer, Map<String, Integer>> score = new LinkedHashMap<>();
		Map<Integer, Integer> compared = new LinkedHashMap<>();
		Map<Integer, Set<Integer>> given = new LinkedHashMap<>();
		for (SlayerTables.Assignment a : tables.assignments)
		{
			given.computeIfAbsent(a.master, k -> new LinkedHashSet<>()).add(a.task);
			Map<String, int[]> am = amountsByTask.get(a.task);
			if (am == null || a.min < 0)
			{
				continue;
			}
			compared.merge(a.master, 1, Integer::sum);
			for (Map.Entry<String, int[]> e : am.entrySet())
			{
				if (e.getValue()[0] == a.min && e.getValue()[1] == a.max)
				{
					score.computeIfAbsent(a.master, k -> new LinkedHashMap<>()).merge(e.getKey(), 1, Integer::sum);
				}
			}
		}
		List<String[]> out = new ArrayList<>();
		// each Wiki master once; on a tie (Turael and Spria give the same amounts) the lower number takes the first
		Set<String> taken = new LinkedHashSet<>();
		for (int m : new java.util.TreeSet<>(given.keySet()))
		{
			out.add(master(m, score.getOrDefault(m, Map.of()), compared.getOrDefault(m, 0), given.get(m), amountsByTask, taken));
		}
		return out;
	}

	/**
	 * The row of master number {@code m}: the Wiki master key not {@code taken} whose amounts match the most of this
	 * master's tasks; named only when it matches more than half the amounts compared ({@code taken} then gets it).
	 */
	private static String[] master(int m, Map<String, Integer> scores, int compared, Set<Integer> givenTasks,
		Map<Integer, Map<String, int[]>> amountsByTask, Set<String> taken)
	{
		String best = null;
		int bestScore = 0;
		int bestMatched = 0;
		for (Map.Entry<String, Integer> e : scores.entrySet())
		{
			if (taken.contains(e.getKey()))
			{
				continue;
			}
			// a page giving amounts for this master's key on a task this master never gives counts against it
			int against = 0;
			for (Map.Entry<Integer, Map<String, int[]>> t : amountsByTask.entrySet())
			{
				if (t.getValue().containsKey(e.getKey()) && !givenTasks.contains(t.getKey()))
				{
					against++;
				}
			}
			int s = e.getValue() - against;
			if (s > bestScore)
			{
				best = e.getKey();
				bestScore = s;
				bestMatched = e.getValue();
			}
		}
		boolean sure = best != null && bestMatched * 2 > compared;
		if (sure)
		{
			taken.add(best);
		}
		return new String[]{String.valueOf(m), sure ? capital(best) : "Slayer master " + m, sure ? best : "-",
			String.valueOf(bestMatched), String.valueOf(compared), "Slayer task/* (Infobox Slayer)"};
	}

	static String placesText(List<Place> places)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("# Where each Slayer task's monsters are: one line per place (one entry per group of monsters).\n");
		sb.append("# DEVELOPMENT-TIME data: tasks, task ids (varp 395) and Konar areas (varp 2096) from the game cache's Slayer DB tables\n");
		sb.append("# 113-116; places, amounts, multi-combat / cannon / safespot, notes and tiles from the Old School RuneScape Wiki (each task\n");
		sb.append("# page's location table and map pins, or its monsters' {{LocLine}} spawns), CC BY-NC-SA 3.0 (data/wiki/LICENSE-WIKI.txt):\n");
		sb.append("# page and revision per line, tile_pages = the monster pages the tiles came from.\n");
		sb.append("# GENERATED by `gradlew -p tool wikiSlayer`; do not edit by hand. tiles: x,y,plane separated by spaces.\n");
		sb.append(PLACES_HEADER).append('\n');
		for (Place p : places)
		{
			StringBuilder tiles = new StringBuilder();
			for (int[] t : p.tiles)
			{
				tiles.append(tiles.length() == 0 ? "" : " ").append(t[0]).append(',').append(t[1]).append(',').append(t[2]);
			}
			sb.append(p.task.row).append('\t').append(p.task.id).append('\t').append(p.task.name).append('\t').append(clean(p.name))
				.append('\t').append(p.konarArea).append('\t').append(clean(p.amount)).append('\t').append(clean(p.multi)).append('\t')
				.append(clean(p.cannon)).append('\t').append(clean(p.safespot)).append('\t').append(String.join(" / ", p.monsters))
				.append('\t').append(tiles.length() == 0 ? "-" : tiles).append('\t').append(p.page).append('\t').append(p.revision)
				.append('\t').append(p.tilePages.isEmpty() ? "-" : String.join(" ", p.tilePages)).append('\t')
				.append(p.notes.isEmpty() ? "-" : clean(p.notes)).append('\n');
		}
		return sb.toString();
	}

	static String mastersText(List<String[]> masters)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("# Which Slayer master each number is (varbit 4067, cache DB table 114 column 0). The cache has no names for them:\n");
		sb.append("# each number is matched to the master whose amounts on the Wiki's task pages ({{Infobox Slayer}}) match the cache's\n");
		sb.append("# amounts (table 114 columns 3-4). DEVELOPMENT-TIME data, OSRS Wiki CC BY-NC-SA 3.0 (data/wiki/LICENSE-WIKI.txt).\n");
		sb.append("# GENERATED by `gradlew -p tool wikiSlayer`; do not edit by hand.\n");
		sb.append("master\tname\twiki_key\tmatched\tcompared\tpages\n");
		for (String[] m : masters)
		{
			sb.append(String.join("\t", m)).append('\n');
		}
		return sb.toString();
	}

	private static String clean(String s)
	{
		return s.replaceAll("[\\t\\r\\n]+", " ").trim();
	}

	private static int intOr(String s, int none)
	{
		try
		{
			return s == null ? none : Integer.parseInt(s.trim());
		}
		catch (NumberFormatException e)
		{
			return none;
		}
	}
}
