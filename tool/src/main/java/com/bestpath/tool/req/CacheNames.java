package com.bestpath.tool.req;

import com.bestpath.tool.cache.LoadedCache;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Quest and item names from the open cache, for checking requirement lines
 * and test players (a misspelt quest name would never match). Empty until a
 * cache is open.
 */
public final class CacheNames
{
	/**
	 * DB table 0 "Quest" (cache #2735, 216 rows with the miniquests): column 1 is the sorting name ("Final Dawn,
	 * The", once even misspelt: "Perlious Moons"), column 2 the display name ("The Final Dawn"). Requirement
	 * lines use the display name; either is accepted when reading.
	 */
	private static final int QUEST_TABLE = 0;
	private static final int QUEST_NAME_COLUMN = 1;
	private static final int QUEST_DISPLAY_COLUMN = 2;

	private static volatile Map<String, String> quests = Collections.emptyMap();
	private static volatile Map<Integer, String> items = Collections.emptyMap();

	private CacheNames()
	{
	}

	/** Reads the names (off the Swing thread: it takes a moment); a failure leaves the lists empty. */
	public static void load(LoadedCache c)
	{
		try
		{
			Map<String, String> q = new TreeMap<>();
			for (Object[][] row : c.dbRows(QUEST_TABLE))
			{
				String sorting = cell(row, QUEST_NAME_COLUMN);
				String display = cell(row, QUEST_DISPLAY_COLUMN);
				String name = display != null ? display : sorting;
				if (name != null)
				{
					q.put(name.toLowerCase(Locale.ROOT), name);
					if (sorting != null)
					{
						q.putIfAbsent(sorting.toLowerCase(Locale.ROOT), name);
					}
				}
			}
			quests = Collections.unmodifiableMap(q);
			items = Collections.unmodifiableMap(c.itemNames());
		}
		catch (Exception e)
		{
			System.err.println("cache names: " + e.getMessage());
		}
	}

	private static String cell(Object[][] row, int column)
	{
		return row.length > column && row[column] != null && row[column].length > 0 ? String.valueOf(row[column][0]) : null;
	}

	/**
	 * The line with every quest name written as the cache's display name ("Dig Site, The" -> "The Dig Site");
	 * unchanged when no cache is open or nothing needs changing. Throws like {@link ItemGroupFile#parse}.
	 */
	public static String tidyQuestNames(String line)
	{
		com.bestpath.core.req.Requirement r = ItemGroupFile.parse(line);
		if (!hasQuests())
		{
			return r.toText();
		}
		String text = r.toText();
		java.util.List<String[]> swaps = new java.util.ArrayList<>();
		r.visit(x ->
		{
			if (x instanceof com.bestpath.core.req.Requirement.Quest)
			{
				String n = ((com.bestpath.core.req.Requirement.Quest) x).getQuest();
				String c = quest(n);
				if (c != null && !c.equals(n))
				{
					swaps.add(new String[]{"quest " + n, "quest " + c});
				}
			}
		});
		for (String[] sw : swaps)
		{
			text = text.replace(sw[0], sw[1]);
		}
		return ItemGroupFile.parse(text).toText();
	}

	public static boolean hasQuests()
	{
		return !quests.isEmpty();
	}

	/** The quest's display name (from its display or sorting name, any case), or null if the cache has no such quest. */
	public static String quest(String name)
	{
		return quests.get(name.trim().toLowerCase(Locale.ROOT));
	}

	/** Every quest name, sorted. */
	public static TreeSet<String> questNames()
	{
		return new TreeSet<>(quests.values());
	}

	/** Quest names in a requirement the cache doesn't know (empty when no cache is open). */
	public static java.util.List<String> unknownQuests(com.bestpath.core.req.Requirement r)
	{
		java.util.List<String> out = new java.util.ArrayList<>();
		if (hasQuests())
		{
			r.visit(x ->
			{
				if (x instanceof com.bestpath.core.req.Requirement.Quest && quest(((com.bestpath.core.req.Requirement.Quest) x).getQuest()) == null)
				{
					out.add(((com.bestpath.core.req.Requirement.Quest) x).getQuest());
				}
			});
		}
		return out;
	}

	/**
	 * Names in a requirement nothing knows: quests the cache doesn't have (when a cache is open), house places and
	 * built things not in data/manual/house-things.tsv. E.g. "quest 'Regicid'", "house place 'Yanile'".
	 */
	public static java.util.List<String> unknownNames(com.bestpath.core.req.Requirement r)
	{
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String q : unknownQuests(r))
		{
			out.add("quest '" + q + "'");
		}
		r.visit(x ->
		{
			if (x instanceof com.bestpath.core.req.Requirement.House)
			{
				com.bestpath.core.req.Requirement.House h = (com.bestpath.core.req.Requirement.House) x;
				if (h.getKind() == com.bestpath.core.req.Requirement.House.Kind.AT && !HouseThings.isPlace(h.getWhat()))
				{
					out.add("house place '" + h.getWhat() + "'");
				}
				else if (h.getKind() == com.bestpath.core.req.Requirement.House.Kind.HAS && !HouseThings.isThing(h.getWhat()))
				{
					out.add("house thing '" + h.getWhat() + "'");
				}
			}
		});
		return out;
	}

	/** The item's name, or null. */
	public static String item(int id)
	{
		return items.get(id);
	}
}
