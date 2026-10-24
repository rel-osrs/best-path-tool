package com.bestpath.tool.menu;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.MenuTextDiagnostics;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The keyboard keys of the lists a click opens whose lines the server sends. They are stored with the data, not
 * worked out at run time: from the cache or the Wiki, else by hand. The cache has each list's words and order (an
 * item's sub-menu, the spirit trees' "Last-destination" texts) and how the game numbers a list's lines ("1".."9",
 * then "A".."Z"); whether the list the click opens shows those lines in that order, every one with its key, is
 * checked by hand: one row per list in {@code data/manual/list-keys.tsv}, used only when its "confirmed" column says
 * yes. The key of a choice is then its place in the order.
 */
public final class ListKeys
{
	/** The file's place in the workspace. */
	public static final String FILE = "data/manual/list-keys.tsv";

	/** How a list's order is known. */
	public enum Order
	{
		/** The click's sub-menu in the cache, the item's ("Rub" on a games necklace: Burthorpe, Barbarian Outpost...). */
		SUB_MENU("sub-menu"),
		/** The spirit trees' order ({@link MenuFacts#spiritTrees}). */
		SPIRIT_TREES("spirit trees"),
		/**
		 * The lines written in the file, "listed: Arceuus; Farming Guild; ...": a list the game server sends that the cache
		 * does not have (the Lovakengj minecart's "Minecart rides"), copied from a picture of it and checked by a person.
		 * Also gives the click its choice texts ({@link ListKeys#lend}).
		 */
		LISTED("listed");

		private final String word;

		Order(String word)
		{
			this.word = word;
		}

		static Order of(String s)
		{
			if (s.trim().toLowerCase(Locale.ROOT).startsWith("listed:"))
			{
				return LISTED;
			}
			for (Order o : values())
			{
				if (o.word.equalsIgnoreCase(s.trim()))
				{
					return o;
				}
			}
			throw new IllegalArgumentException("unknown list order '" + s + "' (expected 'sub-menu', 'spirit trees' or 'listed: a; b')");
		}
	}

	/** One list: the click that opens it, how its order is known, and whether a person confirmed it. Immutable. */
	public static final class Rule
	{
		public final String option;
		public final String target;
		public final Order order;
		public final boolean confirmed;
		public final String source;
		/** The written lines of a {@link Order#LISTED} list, in order; empty for the others. */
		public final List<String> listed;

		Rule(String option, String target, Order order, List<String> listed, boolean confirmed, String source)
		{
			this.option = option;
			this.target = target;
			this.order = order;
			this.listed = List.copyOf(listed);
			this.confirmed = confirmed;
			this.source = source;
		}

		boolean opens(MenuText t)
		{
			return t.getOption().equalsIgnoreCase(option) && t.getTarget().equalsIgnoreCase(target);
		}
	}

	private final List<Rule> rules;

	ListKeys(List<Rule> rules)
	{
		this.rules = List.copyOf(rules);
	}

	/** The rules of the workspace's file; none when there is no file. */
	public static ListKeys load(File ws) throws IOException
	{
		File f = new File(ws, FILE);
		return f.isFile() ? parse(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) : new ListKeys(List.of());
	}

	/**
	 * Reads the file's lines: {@code option, target, order, confirmed, source} tab-separated; "#" lines and empty lines
	 * are skipped. "confirmed" is "yes" or anything else (not used yet: "ask").
	 */
	static ListKeys parse(List<String> lines)
	{
		List<Rule> out = new ArrayList<>();
		int n = 0;
		for (String line : lines)
		{
			n++;
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] p = line.split("\t", -1);
			if (p.length < 5)
			{
				throw new IllegalArgumentException(FILE + " line " + n + ": expected 5 columns, found " + p.length);
			}
			Order o = Order.of(p[2]);
			List<String> listed = new ArrayList<>();
			if (o == Order.LISTED)
			{
				for (String s : p[2].substring(p[2].indexOf(':') + 1).split(";"))
				{
					if (!s.isBlank())
					{
						listed.add(s.trim());
					}
				}
				if (listed.isEmpty())
				{
					throw new IllegalArgumentException(FILE + " line " + n + ": 'listed:' without lines");
				}
			}
			out.add(new Rule(p[0].trim(), p[1].trim(), o, listed, "yes".equalsIgnoreCase(p[3].trim()), p[4].trim()));
		}
		return new ListKeys(out);
	}

	public List<Rule> rules()
	{
		return rules;
	}

	/**
	 * Gives {@code facts} the confirmed written lists, by their click ({@link MenuFacts#listed}), so the menu texts can
	 * name a stop's choice the cache does not have ("Travel Minecart" then "Arceuus"). Call before matching.
	 */
	public void lend(MenuFacts facts)
	{
		for (Rule r : rules)
		{
			if (r.confirmed && r.order == Order.LISTED)
			{
				facts.listed.put(MenuFacts.clickKey(r.option, r.target), r.listed);
			}
		}
	}

	/**
	 * {@code t} with the key of its choice when a confirmed rule covers its click and the choice is in the list's order;
	 * else {@code t} itself (a text that already has a key keeps it).
	 */
	public MenuText apply(MenuText t, MenuFacts facts)
	{
		if (t == null || t.getStyle() != MenuText.Style.CHOICE || MenuTextDiagnostics.key(t) != null || t.isChoiceOnly())
		{
			return t;
		}
		for (Rule r : rules)
		{
			if (r.confirmed && r.opens(t))
			{
				String key = key(order(r, facts), t.getChoice(), facts.listKeys);
				return key == null ? t : t.withKey(key);
			}
		}
		return t;
	}

	/** The key of {@code choice}: its place in {@code order} as the list numbers it; null when it is not there. */
	static String key(List<String> order, String choice, List<String> keys)
	{
		for (int i = 0; order != null && i < order.size(); i++)
		{
			if (order.get(i).equalsIgnoreCase(choice))
			{
				return i < keys.size() ? keys.get(i) : null;
			}
		}
		return null;
	}

	/**
	 * A rule's list in the game's order: the spirit trees, or the sub-menu of the rule's option on the items named as
	 * its target (every charge: "Games necklace(1)".."(8)"), null when they don't all give the same list.
	 */
	static List<String> order(Rule r, MenuFacts facts)
	{
		if (r.order == Order.SPIRIT_TREES)
		{
			return facts.spiritTrees;
		}
		if (r.order == Order.LISTED)
		{
			return r.listed;
		}
		List<String> found = null;
		for (MenuFacts.Item it : facts.items.values())
		{
			if (!TeleportMenus.baseName(it.name).toLowerCase(Locale.ROOT).equals(r.target.toLowerCase(Locale.ROOT)))
			{
				continue;
			}
			for (int i = 0; i < it.invOps.length; i++)
			{
				List<String> sub = it.invSubOps.get(i);
				if (it.invOps[i] != null && it.invOps[i].equalsIgnoreCase(r.option) && sub != null)
				{
					if (found != null && !found.equals(sub))
					{
						return null; // two forms list different places: no key rather than a wrong one
					}
					found = sub;
				}
			}
		}
		return found;
	}
}
