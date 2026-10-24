package com.bestpath.tool.wiki;

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
 * DEVELOPMENT-TIME ONLY (project rule 1: Wiki data is gathered only at development time): the banks from the OSRS
 * Wiki, for the bank data ({@code gradlew -p tool banks}). Reads the page
 * "List of banks" (its main table: name, map area, members, requirements)
 * into {@link WikiBanks#BANKS_FILE}, and for the banks that have no bank
 * booth or chest in the cache, the banker NPCs' tiles from the
 * {@code {{LocLine}}} templates on their pages into
 * {@link WikiBanks#BANKERS_FILE}. Never used by the plugin at run time.
 *
 * <p>{@code gradlew -p tool wikiBanks --args="<wikiCache>"}; pass the main checkout's {@code cache/wiki} so the
 * shared daily request budget is counted.
 */
public final class WikiBanksCli
{
	/**
	 * Banks of the list with no booth or chest in the cache (see {@code gradlew -p tool banks}) and the Wiki pages of
	 * their bankers (found on each bank's Wiki page). Only LocLines within {@link #NEAR} tiles of the bank's map
	 * area are kept.
	 */
	static final Map<String, List<String>> BANKER_PAGES = new LinkedHashMap<>();

	static
	{
		// the banks check found no booth or chest for these; each bank's Wiki page names its banker
		// ("tellers" in its infobox, or the bank's own page in the list is the NPC's)
		BANKER_PAGES.put("Shilo Village bank", List.of("Banker (Shilo Village)"));
		BANKER_PAGES.put("Piscatoris bank", List.of("Arnold Lydspor"));
		BANKER_PAGES.put("Jatizso bank", List.of("Magnus Gram"));
		BANKER_PAGES.put("Ourania bank", List.of("Eniola"));
		BANKER_PAGES.put("Ancient Prison bank", List.of("Ashuelot Reis"));
		BANKER_PAGES.put("Tarn's Lair bank", List.of("Odovacar"));
	}

	/** LocLines further than this from the bank's Wiki map area are another place of the same NPC. */
	static final int NEAR = 12;

	private WikiBanksCli()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File cache = new File(args.length > 0 ? args[0] : "cache/wiki");
		WikiApi wiki = new WikiApi(cache, "banks");
		String[] list = wiki.pages(List.of("List of banks")).get("List of banks");
		if (list == null)
		{
			throw new IllegalStateException("no Wiki page \"List of banks\"");
		}
		List<WikiBanks.Entry> entries = WikiBanks.parseList(list[2], list[1]);
		File bf = new File(WikiBanks.BANKS_FILE);
		bf.getParentFile().mkdirs();
		Files.writeString(bf.toPath(), WikiBanks.banksText(entries), StandardCharsets.UTF_8);
		System.out.printf(Locale.ROOT, "%d banks (List of banks, revision %s) -> %s%n", entries.size(), list[1], bf);

		Map<String, WikiBanks.Entry> byName = new LinkedHashMap<>();
		for (WikiBanks.Entry e : entries)
		{
			byName.put(e.name, e);
		}
		Set<String> titles = new LinkedHashSet<>();
		BANKER_PAGES.values().forEach(titles::addAll);
		Map<String, String[]> pages = titles.isEmpty() ? Map.of() : wiki.pages(new ArrayList<>(titles));
		List<WikiBanks.Banker> bankers = new ArrayList<>();
		for (Map.Entry<String, List<String>> b : BANKER_PAGES.entrySet())
		{
			WikiBanks.Entry bank = byName.get(b.getKey());
			if (bank == null)
			{
				System.out.println("  BANKERS: " + b.getKey() + " is not in the list any more");
				continue;
			}
			int found = 0;
			for (String t : b.getValue())
			{
				String[] p = pages.get(t);
				if (p == null)
				{
					System.out.println("  BANKERS: no Wiki page " + t);
					continue;
				}
				for (WikiClueKillsCli.Spawn s : WikiClueKillsCli.spawns(p[2]))
				{
					if (bank.distance(s.x, s.y) <= NEAR)
					{
						bankers.add(new WikiBanks.Banker(bank.name, t, p[0], p[1], s.x, s.y, s.plane, "LocLine"));
						found++;
					}
				}
				if (found == 0)
				{
					// no LocLine: the infobox's "map" (a pin on the NPC, or the small square it stands in): its middle
					java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\|\\s*map\\s*=\\s*(\\{\\{Map[^\\n]*}})").matcher(p[2]);
					int[] box = m.find() ? WikiBanks.mapBox(m.group(1)) : null;
					if (box != null)
					{
						int x = (box[0] + box[2]) / 2;
						int y = (box[1] + box[3]) / 2;
						if (bank.distance(x, y) <= NEAR)
						{
							bankers.add(new WikiBanks.Banker(bank.name, t, p[0], p[1], x, y, box[4], "infobox map"));
							found++;
						}
					}
				}
			}
			System.out.printf(Locale.ROOT, "  %-40s %d banker tiles%n", b.getKey(), found);
		}
		File kf = new File(WikiBanks.BANKERS_FILE);
		Files.writeString(kf.toPath(), WikiBanks.bankersText(bankers), StandardCharsets.UTF_8);
		System.out.printf(Locale.ROOT, "%d banker tiles -> %s; %d web requests%n", bankers.size(), kf, wiki.requests());
	}
}
