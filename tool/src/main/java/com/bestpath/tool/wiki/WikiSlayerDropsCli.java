package com.bestpath.tool.wiki;

import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.slayer.CollectionLog;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * DEVELOPMENT-TIME ONLY (project rule 1: Wiki data is gathered only at development time): which monsters live at each
 * Slayer place and which collection log items they drop, for each place's own special loot (some spiritual creatures
 * drop dragon boots, some do not). Reads {@code data/wiki/slayer-places.tsv} and
 * {@code data/manual/slayer-extra-places.tsv}: a place's monsters are the monsters its
 * {@code amount} names ("6 Vampyre Juvinates, 8 Vyrewatch, 19 Vyrewatch Sentinels") and the monster pages its tiles came
 * from ({@code tile_pages}: "Zombie@15373734"), else its {@code monsters} column. Their Wiki pages are the ones
 * {@code wikiSlayer} saved in {@code <wikiCache>/slayer/}; only pages not saved yet and the page-specific tables pages
 * take from elsewhere ({@code Template:Uniques/...}) are asked for (polite, like {@link WikiSlayerCli}).
 *
 * <p>Output (CC BY-NC-SA 3.0, page and revision per line): {@value #PLACE_MONSTERS} (task row, place, monster pages) and
 * {@value #DROPS}: every drop line of those pages whose item the game's collection log has (the cache's log,
 * {@link CollectionLog}), with its drop table's version ("Ancient Prison"). Which of them a
 * place card shows is decided by the plugin export ({@code SlayerExport}). Never used by the plugin at run time.
 * {@code gradlew -p tool wikiSlayerDrops --args="<cacheDir> <repo>/cache/wiki"}
 */
public final class WikiSlayerDropsCli
{
	static final String PLACES = "data/wiki/slayer-places.tsv";
	static final String EXTRA_PLACES = "data/manual/slayer-extra-places.tsv";
	public static final String PLACE_MONSTERS = "data/wiki/slayer-place-monsters.tsv";
	public static final String DROPS = "data/wiki/slayer-drops.tsv";

	private WikiSlayerDropsCli()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			System.err.println("usage: wikiSlayerDrops <cacheDir> <wikiCache>");
			System.exit(2);
		}
		CollectionLog log;
		Map<String, List<Integer>> ids = new HashMap<>();
		try (LoadedCache cache = LoadedCache.open(new File(args[0])))
		{
			log = CollectionLog.read(cache);
			for (Map.Entry<Integer, String> e : cache.itemNames().entrySet())
			{
				ids.computeIfAbsent(e.getValue().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(e.getKey());
			}
		}
		WikiApi api = new WikiApi(new File(args[1]), "slayer", "Slayer monster drops");
		Map<String, String[]> saved = api.savedPages();

		List<Map<String, String>> rows = rows(new File(PLACES));
		rows.addAll(rows(new File(EXTRA_PLACES)));
		askMonsterPages(rows, saved, api);

		List<Set<String>> perPlace = new ArrayList<>();
		for (Map<String, String> r : rows)
		{
			perPlace.add(monstersOf(r.getOrDefault("monsters", ""), r.getOrDefault("amount", ""), r.getOrDefault("tile_pages", "-"),
				r.getOrDefault("task_name", ""), saved));
		}
		Set<String> monsters = new LinkedHashSet<>();
		int placesWith = 0;
		for (Set<String> here : perPlace)
		{
			monsters.addAll(here);
			placesWith += here.isEmpty() ? 0 : 1;
		}

		Set<String> templates = askTemplates(monsters, saved, api);

		StringBuilder drops = new StringBuilder();
		drops.append("# The collection log items the monsters of the Slayer places drop (collection log entries),\n")
			.append("# one line per drop line of the monster's OSRS Wiki page (development time; CC BY-NC-SA 3.0, see\n")
			.append("# data/wiki/LICENSE-WIKI.txt). version: the drop table's (\"Ancient Prison\" for \"Drops (Ancient Prison)\"),\n")
			.append("# - for the plain \"Drops\"; plain_drops: whether the page has a plain \"Drops\" table. Collection log from\n")
			.append("# the game cache. Made by gradlew -p tool wikiSlayerDrops (WikiSlayerDropsCli); see docs/DATA_FORMATS.md.\n")
			.append("# only_in: the place a drop's note limits it to (\"Only dropped in Ruins of Tapoyauik\"), - none.\n")
			.append("monster\trevision\tversion\titem\trarity\tplain_drops\tonly_in\n");
		int lines = 0;
		for (String m : monsters)
		{
			lines += appendDrops(drops, m, saved, ids, log);
		}
		Set<String> missing = new LinkedHashSet<>();
		for (String t : templates)
		{
			if (!saved.containsKey(t))
			{
				missing.add(t);
			}
		}
		Files.writeString(new File(PLACE_MONSTERS).toPath(), placesText(rows, perPlace), StandardCharsets.UTF_8);
		Files.writeString(new File(DROPS).toPath(), drops.toString(), StandardCharsets.UTF_8);
		for (String t : missing)
		{
			System.out.println("  CHECK: no Wiki page " + t);
		}
		System.out.printf(Locale.ROOT, "%d places (%d with monster pages), %d monsters, %d collection log drops (log: %d items) -> %s, %s;"
			+ " %d web requests%n", rows.size(), placesWith, monsters.size(), lines, log.size(), PLACE_MONSTERS, DROPS, api.requests());
	}

	/** Asks the Wiki for the pages the monster pages take tables from ({@code Template:Uniques/...}) not saved yet; returns them. */
	private static Set<String> askTemplates(Set<String> monsters, Map<String, String[]> saved, WikiApi api) throws Exception
	{
		Set<String> templates = new LinkedHashSet<>();
		for (String m : monsters)
		{
			templates.addAll(SlayerDrops.included(saved.get(m)[2]));
		}
		templates.removeIf(saved::containsKey);
		if (!templates.isEmpty())
		{
			saved.putAll(api.pages(new ArrayList<>(templates)));
		}
		return templates;
	}

	/**
	 * Asks the Wiki for the monsters columns' pages not saved yet (names read from the amounts only match saved monster
	 * pages).
	 */
	private static void askMonsterPages(List<Map<String, String>> rows, Map<String, String[]> saved, WikiApi api) throws Exception
	{
		Set<String> wanted = new LinkedHashSet<>();
		for (Map<String, String> r : rows)
		{
			for (String m : r.getOrDefault("monsters", "").split(" / "))
			{
				if (!m.isBlank() && page(m.trim(), saved) == null)
				{
					wanted.add(m.trim());
				}
			}
		}
		if (!wanted.isEmpty())
		{
			saved.putAll(api.pages(new ArrayList<>(wanted)));
		}
	}

	/** The {@value #PLACE_MONSTERS} text: each place's monster pages ({@code perPlace}, in the rows' order). */
	private static String placesText(List<Map<String, String>> rows, List<Set<String>> perPlace)
	{
		StringBuilder places = new StringBuilder();
		places.append("# Which monsters live at each Slayer place: the place's monsters column and the monsters its amount names,\n")
			.append("# as OSRS Wiki pages (development time; CC BY-NC-SA 3.0, see data/wiki/LICENSE-WIKI.txt). Made by\n")
			.append("# gradlew -p tool wikiSlayerDrops (WikiSlayerDropsCli); see docs/DATA_FORMATS.md.\n")
			.append("task\tplace\tmonsters\n");
		for (int i = 0; i < rows.size(); i++)
		{
			Map<String, String> r = rows.get(i);
			Set<String> here = perPlace.get(i);
			places.append(r.get("task")).append('\t').append(r.get("place")).append('\t')
				.append(here.isEmpty() ? "-" : String.join(" / ", here)).append('\n');
		}
		return places.toString();
	}

	/** Adds the monster page's drop lines whose item the collection log has, each version and item once; returns how many. */
	private static int appendDrops(StringBuilder drops, String m, Map<String, String[]> saved, Map<String, List<Integer>> ids, CollectionLog log)
	{
		int lines = 0;
		String[] p = saved.get(m);
		Set<String> seen = new HashSet<>();
		boolean plain = false;
		for (String[] sec : SlayerDrops.sections(p[2]))
		{
			plain |= sec[0].isEmpty();
		}
		for (SlayerDrops.Drop d : SlayerDrops.parse(p[2], t -> saved.containsKey(t) ? saved.get(t)[2] : null))
		{
			if (!inLog(d.item, ids, log) || !seen.add(d.version + "\t" + d.item.toLowerCase(Locale.ROOT)))
			{
				continue;
			}
			drops.append(m).append('\t').append(p[1]).append('\t').append(d.version.isEmpty() ? "-" : clean(d.version)).append('\t')
				.append(clean(d.item)).append('\t').append(d.rarity.isEmpty() ? "-" : clean(d.rarity)).append('\t')
				.append(plain ? "yes" : "no").append('\t').append(d.onlyIn.isEmpty() ? "-" : clean(d.onlyIn)).append('\n');
			lines++;
		}
		return lines;
	}

	/** The item (by name, any of its cache numbers) is in the collection log. */
	static boolean inLog(String item, Map<String, List<Integer>> ids, CollectionLog log)
	{
		for (int id : ids.getOrDefault(item.toLowerCase(Locale.ROOT), List.of()))
		{
			if (log.has(id))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The monster pages of a place: each page its tiles came from ({@code tilePages}: "Zombie@15373734 Zombie
	 * rat@15199680") and each monster its amount names ("6 Vampyre Juvinates, 8 Vyrewatch, 19 Vyrewatch Sentinels"), when
	 * a saved monster page has that name (as written, or one of them: "Sentinels" -> "Sentinel", "Wolves" -> "Wolf");
	 * only when those name none, its monsters column ("Spiritual mage"; on many task pages every row repeats the task's
	 * whole list there, "Basilisk / Monstrous basilisk / Basilisk Knight / ...").
	 */
	static Set<String> monstersOf(String monsters, String amount, String tilePages, String task, Map<String, String[]> saved)
	{
		Set<String> out = new LinkedHashSet<>();
		java.util.regex.Matcher tp = java.util.regex.Pattern.compile("(.+?)@\\d+\\s*").matcher(tilePages);
		while (tp.find())
		{
			String p = page(tp.group(1).trim(), saved);
			if (p != null)
			{
				out.add(p);
			}
		}
		for (String part : amount.split(",|;| and |(?<=[A-Za-z)])\\s+(?=\\d)")) // "5 Adults 2 Babies" too
		{
			String n = part.replaceAll("\\(.*?\\)", "").replaceFirst("^\\s*[\\d,]+\\+?\\s*", "").trim();
			if (n.isEmpty() || !Character.isLetter(n.charAt(0)) || n.equals(part.trim()) && !part.trim().matches(".*\\d.*"))
			{
				continue; // only "<number> <monsters>" names a monster ("Many", "Some" don't)
			}
			String kind = task.isEmpty() ? null : kind(n, WikiSlayerCli.singular(task));
			for (String c : new String[]{kind, n, n.endsWith("ves") ? n.substring(0, n.length() - 3) + "f" : null,
				n.endsWith("es") ? n.substring(0, n.length() - 2) : null, n.endsWith("s") ? n.substring(0, n.length() - 1) : null})
			{
				String p = c == null ? null : page(c, saved);
				if (p != null && saved.get(p)[2].contains("{{Infobox Monster"))
				{
					out.add(p);
					break;
				}
			}
		}
		if (!out.isEmpty())
		{
			return out; // the place's own monsters (many task pages repeat the task's whole list in every row's monsters)
		}
		for (String m : monsters.split(" / "))
		{
			String p = page(m.trim(), saved);
			if (p != null)
			{
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * A dragon task's words for its kinds ("5 Adults, 3 Babies, 2 Brutals" on "Blue Dragons"): "Blue dragon", "Baby blue
	 * dragon", "Brutal blue dragon"; null for other words.
	 */
	static String kind(String word, String monster)
	{
		String w = word.toLowerCase(Locale.ROOT);
		String m = monster.toLowerCase(Locale.ROOT);
		if (w.equals("adult") || w.equals("adults"))
		{
			return monster;
		}
		if (w.equals("baby") || w.equals("babies"))
		{
			return "Baby " + m;
		}
		return w.equals("brutal") || w.equals("brutals") ? "Brutal " + m : null;
	}

	/**
	 * The saved page's title for this name, with a capital first letter, in sentence case ("Chilled Jelly" -> "Chilled
	 * jelly") or without a note in brackets ("Suqah (single-way combat)" -> "Suqah"), or null.
	 */
	static String page(String name, Map<String, String[]> saved)
	{
		if (name.isEmpty())
		{
			return null;
		}
		String bare = name.replaceFirst("\\s*\\([^)]*\\)$", "");
		for (String n : new String[]{name, Character.toUpperCase(name.charAt(0)) + name.substring(1),
			Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT), bare})
		{
			String[] p = saved.get(n);
			if (p != null)
			{
				return p[0];
			}
		}
		return null;
	}

	private static String clean(String s)
	{
		return s.replaceAll("[\\t\\r\\n]+", " ").trim();
	}

	/** Tab-separated rows after the header line ({@code #} lines skipped). */
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
			Map<String, String> m = new LinkedHashMap<>();
			for (int i = 0; i < names.length && i < w.length; i++)
			{
				m.put(names[i], w[i].trim());
			}
			out.add(m);
		}
		return out;
	}
}
