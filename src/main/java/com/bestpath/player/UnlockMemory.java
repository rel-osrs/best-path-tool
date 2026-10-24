package com.bestpath.player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unlocks the plugin detects instead of asking the player, remembered per account (RuneLite account
 * profile, like the bank): the collection log items seen (only the ones some requirement mentions) and, per spirit
 * tree patch, whether its tree was last seen grown. Pure Java, one thread (the client thread). Saved only when
 * something changed (diff before saving).
 */
public final class UnlockMemory
{
	/** The game's message when an item is added to the collection log (needs the game's notification setting). */
	private static final Pattern NEW_ITEM = Pattern.compile("New item added to your collection log: (.+)");

	private final AccountSettings.Store logStore;
	private final AccountSettings.Store treeStore;
	private Set<Integer> log = Collections.emptySet();
	/** Place (as the data names it) -> last seen grown; not in it = not seen by this plugin. */
	private Map<String, Boolean> trees = Collections.emptyMap();

	public UnlockMemory(AccountSettings.Store logStore, AccountSettings.Store treeStore)
	{
		this.logStore = logStore;
		this.treeStore = treeStore;
	}

	/** Loads the account's saved values (startup, account switch). */
	public void load()
	{
		log = Collections.unmodifiableSet(new TreeSet<>(ProfileText.decodeIds(logStore.read())));
		trees = Collections.unmodifiableMap(decodeTrees(treeStore.read()));
	}

	/** The collection log items seen (immutable, sorted). */
	public Set<Integer> getLog()
	{
		return log;
	}

	/** Place -> last seen grown, for the patches this plugin has seen (immutable). */
	public Map<String, Boolean> getTrees()
	{
		return trees;
	}

	/**
	 * These items were seen in the collection log.
	 *
	 * @return true if one was new (then saved; routes may change)
	 */
	public boolean addToLog(Set<Integer> ids)
	{
		if (log.containsAll(ids))
		{
			return false;
		}
		Set<Integer> now = new TreeSet<>(log);
		now.addAll(ids);
		log = Collections.unmodifiableSet(now);
		logStore.write(ProfileText.encodeIds(now));
		return true;
	}

	/**
	 * The spirit tree at this place was seen grown or not.
	 *
	 * @return true if that differs from what was known (then saved; routes may change)
	 */
	public boolean seeTree(String place, boolean grown)
	{
		if (Boolean.valueOf(grown).equals(trees.get(place)))
		{
			return false;
		}
		Map<String, Boolean> now = new TreeMap<>(trees);
		now.put(place, grown);
		trees = Collections.unmodifiableMap(now);
		treeStore.write(encodeTrees(now));
		return true;
	}

	/** The item name in a "New item added to your collection log: X" message (tags removed first), or null. */
	public static String newLogItem(String plainMessage)
	{
		Matcher m = plainMessage == null ? null : NEW_ITEM.matcher(plainMessage.trim());
		return m != null && m.matches() ? m.group(1).trim() : null;
	}

	/** One patch per line: place, a tab, "grown" or "not grown". */
	static String encodeTrees(Map<String, Boolean> trees)
	{
		StringBuilder sb = new StringBuilder();
		new TreeMap<>(trees).forEach((place, grown) ->
			sb.append(sb.length() == 0 ? "" : "\n").append(place).append('\t').append(grown ? "grown" : "not grown"));
		return sb.toString();
	}

	static Map<String, Boolean> decodeTrees(String text)
	{
		Map<String, Boolean> out = new LinkedHashMap<>();
		if (text == null)
		{
			return out;
		}
		for (String line : text.split("\n"))
		{
			int tab = line.indexOf('\t');
			if (tab > 0)
			{
				String state = line.substring(tab + 1).trim().toLowerCase(Locale.ROOT);
				if (state.equals("grown") || state.equals("not grown"))
				{
					out.put(line.substring(0, tab).trim(), state.equals("grown"));
				}
			}
		}
		return out;
	}
}
