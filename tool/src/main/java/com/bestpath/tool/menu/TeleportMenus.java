package com.bestpath.tool.menu;

import static com.bestpath.tool.menu.DestinationMatch.afterColon;
import static com.bestpath.tool.menu.DestinationMatch.best;
import static com.bestpath.tool.menu.DestinationMatch.score;
import static com.bestpath.tool.menu.MenuMatcher.unknown;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.MenuTextBuilder;
import com.bestpath.core.req.Requirement;
import com.bestpath.tool.edit.TransportRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The menu texts of teleports ({@link MenuMatcher}): spells by the spell's name and its spellbook button's options,
 * minigame teleports by the cache's minigame list, items by the options that name the destination or by their one
 * teleport verb.
 */
final class TeleportMenus
{
	/** Item options that use a teleport item without naming the destination. */
	private static final Set<String> TELEPORT_VERBS = Set.of("break", "teleport", "commune", "read", "rub", "empty",
		"activate", "crush", "invoke", "blow", "play", "squash", "tele", "teleports", "travel", "eat", "drink", "scatter",
		"pray", "dig", "use", "summon", "operate", "reminisce", "consume", "smash", "whistle", "shake", "bury", "ring", "channel");

	/** Spellbook button options that are not destinations. */
	private static final Set<String> SPELL_NON_PLACES = Set.of("cast", "configure", "warnings", "animation", "info");

	private final MenuFacts facts;
	private final Function<String, Requirement> requirements;
	/** Teleport records per first item id (several destinations from one item need its options or a dialogue). */
	private final Map<Integer, Integer> teleportsPerItem = new HashMap<>();

	TeleportMenus(MenuFacts facts, Function<String, Requirement> requirements)
	{
		this.facts = facts;
		this.requirements = requirements;
	}

	/** Counts a teleport record for its first item (call for every teleport before matching any). */
	void count(TransportRecord r)
	{
		List<Integer> items = itemIds(r);
		if (!items.isEmpty())
		{
			teleportsPerItem.merge(items.get(0), 1, Integer::sum);
		}
	}

	/** The menu text of one teleport record. */
	MenuMatcher.Result match(TransportRecord r)
	{
		List<String> doubts = new ArrayList<>();
		String cat = r.teleportCategory == null ? "" : r.teleportCategory;
		if (cat.startsWith("grouping"))
		{
			return minigame(r, doubts);
		}
		// spells by name only for spell categories ("Varrock teleport (tablet)" is the tablet, not the spell)
		if (cat.equals("spell") || cat.equals("home teleport"))
		{
			MenuMatcher.Result spell = spell(r, doubts);
			if (spell != null)
			{
				return spell;
			}
		}
		List<Integer> items = itemIds(r);
		if (items.isEmpty())
		{
			return unknown("no spell of this name and no item in the requirements", doubts);
		}
		return itemTeleport(r, items, doubts);
	}

	/** A spell by the record's name, or null when no spell has it. */
	private MenuMatcher.Result spell(TransportRecord r, List<String> doubts)
	{
		String name = r.name;
		String dest = afterColon(name);
		if (dest != null)
		{
			name = name.substring(0, name.indexOf(": "));
		}
		String paren = null;
		Matcher m = Pattern.compile("^(.*?)\\s*\\(([^)]*)\\)$").matcher(name);
		if (m.matches())
		{
			name = m.group(1);
			paren = m.group(2);
		}
		MenuFacts.Spell s = facts.spells.get(name.toLowerCase(Locale.ROOT));
		if (s == null)
		{
			return null;
		}
		List<String> places = new ArrayList<>();
		for (String op : s.ops)
		{
			if (!SPELL_NON_PLACES.contains(op.toLowerCase(Locale.ROOT)))
			{
				places.add(op);
			}
		}
		String alt = null;
		for (String want : new String[]{dest, paren})
		{
			if (want != null && alt == null)
			{
				alt = best(want, places, doubts);
			}
		}
		if (alt != null)
		{
			return new MenuMatcher.Result(MenuText.named(alt, s.name), null, null, null, doubts, "spell '" + s.name + "' option " + alt);
		}
		return new MenuMatcher.Result(MenuText.verb("Cast", s.name), null, null, null, doubts, "spell '" + s.name + "'");
	}

	/**
	 * Minigame teleports: the spellbook's "Minigame Teleport" spell (no runes, 20 minutes, as the Grouping tab's
	 * button), then the minigame as the cache's minigame teleport list names it (DB table 214).
	 */
	private MenuMatcher.Result minigame(TransportRecord r, List<String> doubts)
	{
		String dest = afterColon(r.name);
		MenuFacts.Spell s = facts.spells.get(MINIGAME_SPELL.toLowerCase(Locale.ROOT));
		String entry = dest == null ? null : best(dest, facts.list(MenuFacts.MINIGAMES), doubts);
		if (s == null || entry == null)
		{
			return unknown(s == null ? "no '" + MINIGAME_SPELL + "' spell in the cache"
				: "minigame '" + dest + "' not in the cache's minigame teleport list", doubts);
		}
		return new MenuMatcher.Result(MenuTextBuilder.choice("Cast", s.name, entry), null, null, null, doubts,
			"spell '" + s.name + "', minigame teleport list (DB table 214)");
	}

	/**
	 * The spell (in every spellbook) that opens the minigame teleport list: "attempting to cast the spell opens an
	 * interface asking you to choose" (OSRS Wiki, Minigame Teleport rev 15359028).
	 */
	static final String MINIGAME_SPELL = "Minigame Teleport";

	/**
	 * Items whose first option opens one of the cache's destination lists, by a word of the item's name: quetzal
	 * whistles' "Signal" opens the quetzal transport map (OSRS Wiki, Basic quetzal whistle rev 15356472; a game
	 * setting can make it fly straight to the Hunter Guild instead).
	 */
	static final Map<String, String> ITEM_LISTS = Map.of("quetzal whistle", MenuFacts.QUETZALS);

	/**
	 * Achievement diary cape records are named after each diary's master ("Achievement diary cape: Two-pints"), while
	 * the cape's Teleport sub-menu names the diaries ("Ardougne"). The menu text uses the menu's name; the records
	 * keep theirs. The pairs: the cape's 16 sub-menu options in the cache (items 19476 / 13069) are in the same
	 * order as Shortest Path's numbered rows for the cape (data/shortest-path/teleportation_items.tsv, "1. Two-pints"
	 * ... "G. Twiggy O'Korn", the sub-menu keys), and each record lands in its diary's area.
	 */
	static final Map<String, String> DIARY_CAPE_OPTIONS = Map.ofEntries(
		Map.entry("Two-pints", "Ardougne"),
		Map.entry("Jarr", "Desert"),
		Map.entry("Sir Rebral", "Falador"),
		Map.entry("Thorodin", "Fremennik"),
		Map.entry("Flax keeper", "Kandarin"),
		Map.entry("Pirate Jackie the Fruit", "Karamja"),
		Map.entry("Kaleb Paramaya", "Karamja (Shilo)"),
		Map.entry("Jungle forester", "Karamja (Jungle)"),
		Map.entry("TzHaar-Mej", "Karamja (Mor Ul Rek)"),
		Map.entry("Elise", "Kourend & Kebos"),
		Map.entry("Hatius Cosaintus", "Lumbridge & Draynor"),
		Map.entry("Le-sabrè", "Morytania"),
		Map.entry("Toby", "Varrock"),
		Map.entry("Lesser Fanatic", "Wilderness"),
		Map.entry("Elder Gnome child", "Western Provinces"));

	/** The record's own words for its destination, as the cape's option says it (see {@link #DIARY_CAPE_OPTIONS}). */
	static String diaryCapeOption(MenuFacts.Item it, String dest)
	{
		return it != null && dest != null && it.name.toLowerCase(Locale.ROOT).startsWith("achievement diary cape")
			? DIARY_CAPE_OPTIONS.get(dest) : null;
	}

	/** The item ids of a record's requirement, in order, each once (empty if none or unreadable). */
	List<Integer> itemIds(TransportRecord r)
	{
		List<Integer> out = new ArrayList<>();
		try
		{
			Requirement q = requirements.apply(r.requirements == null ? "" : r.requirements);
			if (q != null)
			{
				q.visit(x ->
				{
					if (x instanceof Requirement.Item)
					{
						for (int id : ((Requirement.Item) x).getItemIds())
						{
							if (!out.contains(id))
							{
								out.add(id);
							}
						}
					}
				});
			}
		}
		catch (RuntimeException e)
		{
			// unreadable requirement: no items
		}
		return out;
	}

	/** An item's name without a charge count or trim mark: "Amulet of glory(4)" -> "Amulet of glory" (any charge works). */
	static String baseName(String name)
	{
		return name.replaceAll("\\s*\\((\\d+|t)\\)$", "").trim();
	}

	private MenuMatcher.Result itemTeleport(TransportRecord r, List<Integer> items, List<String> doubts)
	{
		String dest = afterColon(r.name);
		MenuMatcher.Result named = byNamedOption(r, dest, items, doubts);
		if (named != null)
		{
			return named;
		}
		MenuMatcher.Result listed = byItemList(dest, items, doubts);
		if (listed != null)
		{
			return listed;
		}
		MenuMatcher.Result verb = byTeleportVerb(dest, items, doubts);
		if (verb != null)
		{
			return verb;
		}
		MenuFacts.Item first = facts.items.get(items.get(0));
		return unknown(first == null ? "item " + items.get(0) + " is not in the cache"
			: "no option of '" + first.name + "' names '" + dest + "' (inventory " + list(first.invOps) + ", worn " + list(first.wornOps) + ")",
			doubts);
	}

	/**
	 * First choice: an option that names the destination (or what the name says in brackets: "(outside)"), first item
	 * first; or null.
	 */
	private MenuMatcher.Result byNamedOption(TransportRecord r, String dest, List<Integer> items, List<String> doubts)
	{
		for (String want : wantedNames(r, dest))
		{
			for (int id : items)
			{
				MenuFacts.Item it = facts.items.get(id);
				if (it == null)
				{
					continue;
				}
				MenuText t = namedOption(it, want, doubts);
				if (t != null)
				{
					return new MenuMatcher.Result(t, null, null, null, doubts, "item " + id + " options");
				}
				// an achievement diary cape: the record names the diary's master, the cape's option the diary
				String diary = diaryCapeOption(it, want);
				t = diary == null ? null : namedOption(it, diary, doubts);
				if (t != null)
				{
					return new MenuMatcher.Result(t, null, null, null, doubts, "item " + id + " options (diary master " + want + " = " + diary + ")");
				}
			}
		}
		return null;
	}

	/** The names an item option may give the destination: the part after ": ", then each part in brackets. */
	private static List<String> wantedNames(TransportRecord r, String dest)
	{
		List<String> wants = new ArrayList<>();
		if (dest != null)
		{
			wants.add(dest);
		}
		Matcher pm = Pattern.compile("\\(([^)]*)\\)").matcher(dest == null ? r.name : r.name.substring(0, r.name.indexOf(": ")));
		while (pm.find())
		{
			wants.add(pm.group(1));
		}
		return wants;
	}

	/** Second choice: an item that opens one of the cache's destination lists (quetzal whistles: the quetzal map). */
	private MenuMatcher.Result byItemList(String dest, List<Integer> items, List<String> doubts)
	{
		for (int id : items)
		{
			MenuFacts.Item it = facts.items.get(id);
			for (Map.Entry<String, String> e : ITEM_LISTS.entrySet())
			{
				if (it != null && dest != null && it.name.toLowerCase(Locale.ROOT).contains(e.getKey()) && it.invOps[0] != null)
				{
					String entry = best(dest, facts.list(e.getValue()), doubts);
					if (entry != null)
					{
						return new MenuMatcher.Result(MenuTextBuilder.choice(it.invOps[0], baseName(it.name), entry), null, null, null, doubts,
							"item " + id + " option " + it.invOps[0] + ", " + e.getValue() + " list");
					}
				}
			}
		}
		return null;
	}

	/** Last choice: the item's teleport verb when this is its only destination (or why not); null when no item has one. */
	private MenuMatcher.Result byTeleportVerb(String dest, List<Integer> items, List<String> doubts)
	{
		for (int id : items)
		{
			MenuFacts.Item it = facts.items.get(id);
			if (it == null)
			{
				continue;
			}
			for (int i = 0; i < it.invOps.length; i++)
			{
				String op = it.invOps[i];
				if (op == null || !TELEPORT_VERBS.contains(op.toLowerCase(Locale.ROOT)))
				{
					continue;
				}
				if (it.invSubOps.get(i) != null)
				{
					return unknown("'" + dest + "' is not among the options of '" + op + " " + it.name + "' ("
						+ String.join(", ", it.invSubOps.get(i)) + ")", doubts);
				}
				if (teleportsPerItem.getOrDefault(items.get(0), 0) > 1)
				{
					return unknown("destination chosen in a dialogue after '" + op + " " + baseName(it.name) + "'; its text is not in the cache",
						doubts);
				}
				return new MenuMatcher.Result(MenuText.verb(op, baseName(it.name)), null, null, null, doubts, "item " + id + " option " + op);
			}
			for (int n = 0; n < it.wornOps.length; n++)
			{
				String op = it.wornOps[n];
				if (op != null && TELEPORT_VERBS.contains(op.toLowerCase(Locale.ROOT)) && it.wornSubOps.get(n) == null
					&& teleportsPerItem.getOrDefault(items.get(0), 0) <= 1)
				{
					return new MenuMatcher.Result(MenuText.verb(op, baseName(it.name)), null, null, null, doubts, "item " + id + " worn option " + op);
				}
			}
		}
		return null;
	}

	private static String list(String[] ops)
	{
		List<String> out = new ArrayList<>();
		for (String s : ops)
		{
			if (s != null)
			{
				out.add(s);
			}
		}
		return out.toString();
	}

	/**
	 * The best option of an item that names the destination: inventory sub-menu (CHOICE), inventory option (NAMED),
	 * worn option (NAMED), worn sub-menu (CHOICE); on equal scores the inventory first.
	 */
	static MenuText namedOption(MenuFacts.Item it, String dest, List<String> doubts)
	{
		MenuText best = null;
		int bestScore = 0;
		Set<String> tied = new LinkedHashSet<>();
		List<Object[]> candidates = new ArrayList<>(); // {MenuText, text matched}
		addCandidates(candidates, it, it.invOps, it.invSubOps);
		addCandidates(candidates, it, it.wornOps, it.wornSubOps);
		for (Object[] c : candidates)
		{
			int s = score(dest, (String) c[1]);
			MenuText t = (MenuText) c[0];
			if (s > bestScore)
			{
				bestScore = s;
				best = t;
				tied.clear();
				tied.add(t.title(MenuText.ARROW));
			}
			else if (s == bestScore && s > 0)
			{
				tied.add(t.title(MenuText.ARROW));
			}
		}
		if (tied.size() > 1)
		{
			doubts.add("'" + dest + "' matches several options: " + String.join(" / ", tied) + " (first taken)");
		}
		if (bestScore == 1)
		{
			doubts.add("'" + dest + "' taken as '" + best.title(MenuText.ARROW) + "' (shortened or spelt differently)");
		}
		return best;
	}

	/** Each option's sub-menu options (CHOICE), or the option itself (NAMED) when it is not a teleport verb. */
	private static void addCandidates(List<Object[]> candidates, MenuFacts.Item it, String[] ops, List<List<String>> subOps)
	{
		for (int i = 0; i < ops.length; i++)
		{
			if (ops[i] == null)
			{
				continue;
			}
			if (subOps.get(i) != null)
			{
				for (String s : subOps.get(i))
				{
					candidates.add(new Object[]{MenuTextBuilder.choice(ops[i], baseName(it.name), s), s});
				}
			}
			else if (!TELEPORT_VERBS.contains(ops[i].toLowerCase(Locale.ROOT)))
			{
				candidates.add(new Object[]{MenuText.named(ops[i], baseName(it.name)), ops[i]});
			}
		}
	}
}
