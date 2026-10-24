package com.bestpath.player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Quest;

/**
 * Finds RuneLite's {@link Quest} for a quest name in our requirement data
 * (the cache's display names, e.g. "The Dig Site"). Exact name first (any
 * case), then the name without "The", punctuation and spaces ("Dig Site, The"
 * and "Digsite" find "The Dig Site"), then a RuneLite name that is the only
 * one starting with it ("Desert Treasure II" finds "Desert Treasure II - The
 * Fallen Empire"). Names still not found count as "not started"; the plugin
 * logs them once.
 */
public final class QuestNames
{
	private static final Map<String, Quest> EXACT = new HashMap<>();
	private static final Map<String, Quest> LOOSE = new HashMap<>();

	static
	{
		for (Quest q : Quest.values())
		{
			EXACT.put(q.getName().toLowerCase(Locale.ROOT), q);
			LOOSE.put(loose(q.getName()), q);
		}
	}

	private QuestNames()
	{
	}

	/** RuneLite's quest for this name, or null. */
	public static Quest find(String name)
	{
		String n = name.trim().toLowerCase(Locale.ROOT);
		Quest q = EXACT.get(n);
		if (q != null)
		{
			return q;
		}
		String l = loose(name);
		q = LOOSE.get(l);
		if (q != null || l.isEmpty())
		{
			return q;
		}
		Quest only = null;
		for (Map.Entry<String, Quest> e : LOOSE.entrySet())
		{
			if (e.getKey().startsWith(l))
			{
				if (only != null)
				{
					return null; // more than one: don't pick
				}
				only = e.getValue();
			}
		}
		return only;
	}

	/** Lower case, a leading "the " or trailing ", the" dropped, only letters and digits kept. */
	static String loose(String name)
	{
		String n = name.trim().toLowerCase(Locale.ROOT);
		if (n.endsWith(", the"))
		{
			n = n.substring(0, n.length() - 5);
		}
		if (n.startsWith("the "))
		{
			n = n.substring(4);
		}
		StringBuilder sb = new StringBuilder(n.length());
		for (int i = 0; i < n.length(); i++)
		{
			char c = n.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))
			{
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
