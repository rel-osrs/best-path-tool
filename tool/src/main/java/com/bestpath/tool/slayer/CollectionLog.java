package com.bestpath.tool.slayer;

import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.definitions.EnumDefinition;
import net.runelite.cache.definitions.StructDefinition;

/**
 * The game's collection log, from the cache (a Slayer place's special loot is the collection log items its monsters
 * drop). Checked in cache #2735: enum {@value #TABS} lists the log's tabs (structs: param {@value #TAB_NAME} its name,
 * "Bosses", "Raids", "Clues", "Minigames", "Other"; param {@value #TAB_ENTRIES} the enum of its entries); an entry struct
 * has param {@value #ENTRY_NAME} its name ("Slayer", "Abyssal Sire") and {@value #ENTRY_ITEMS} the enum of its items.
 * Immutable.
 */
public final class CollectionLog
{
	static final int TABS = 2102;
	static final int TAB_NAME = 682;
	static final int TAB_ENTRIES = 683;
	static final int ENTRY_NAME = 689;
	static final int ENTRY_ITEMS = 690;
	/** The tab whose entries are bosses. */
	public static final String BOSSES_TAB = "Bosses";

	public static final CollectionLog NONE = new CollectionLog(Map.of());

	/** "tab / entry" -> its items, in the log's order. */
	private final Map<String, List<Integer>> entries;
	private final Set<Integer> items = new LinkedHashSet<>();

	/** {@code entries}: "tab / entry" -> item ids. */
	public CollectionLog(Map<String, List<Integer>> entries)
	{
		Map<String, List<Integer>> copy = new LinkedHashMap<>();
		entries.forEach((k, v) -> copy.put(k, List.copyOf(v)));
		this.entries = Collections.unmodifiableMap(copy);
		for (List<Integer> v : copy.values())
		{
			items.addAll(v);
		}
	}

	public static CollectionLog read(LoadedCache cache) throws IOException
	{
		Map<String, List<Integer>> out = new LinkedHashMap<>();
		EnumDefinition tabs = cache.enumDefinition(TABS);
		if (tabs == null || tabs.getIntVals() == null)
		{
			return NONE;
		}
		for (int tabId : tabs.getIntVals())
		{
			StructDefinition tab = cache.struct(tabId);
			if (tab == null || tab.getParams() == null || !(tab.getParams().get(TAB_ENTRIES) instanceof Integer))
			{
				continue;
			}
			EnumDefinition list = cache.enumDefinition((Integer) tab.getParams().get(TAB_ENTRIES));
			for (int entryId : list == null || list.getIntVals() == null ? new int[0] : list.getIntVals())
			{
				StructDefinition entry = cache.struct(entryId);
				if (entry == null || entry.getParams() == null || !(entry.getParams().get(ENTRY_ITEMS) instanceof Integer))
				{
					continue;
				}
				EnumDefinition items = cache.enumDefinition((Integer) entry.getParams().get(ENTRY_ITEMS));
				List<Integer> ids = new ArrayList<>();
				for (int i : items == null || items.getIntVals() == null ? new int[0] : items.getIntVals())
				{
					ids.add(i);
				}
				out.put(tab.getParams().get(TAB_NAME) + " / " + entry.getParams().get(ENTRY_NAME), ids);
			}
		}
		return new CollectionLog(out);
	}

	/** The item is in the log (in any entry). */
	public boolean has(int item)
	{
		return items.contains(item);
	}

	/** The names of the entries in {@code tab} ("Abyssal Sire", "Callisto and Artio", ...). */
	public List<String> entryNames(String tab)
	{
		List<String> out = new ArrayList<>();
		for (String k : entries.keySet())
		{
			if (k.startsWith(tab + " / "))
			{
				out.add(k.substring(tab.length() + 3));
			}
		}
		return out;
	}

	public boolean isEmpty()
	{
		return entries.isEmpty();
	}

	/** Number of items in the log. */
	public int size()
	{
		return items.size();
	}
}
