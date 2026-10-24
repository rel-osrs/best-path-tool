package com.bestpath.core.req;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a requirement line. Parts are joined with ";" (all of them needed);
 * {@code one of (A | B)} needs one alternative, and an alternative may itself
 * hold several parts. An empty line is {@link Requirement#NONE}. Parts:
 * <pre>
 *   Agility 61                     a real skill level
 *   quest Regicide                 quest done ("started", "not started", "not done" after the name)
 *   varbit 1990 &gt; 39              also varp; = != &gt; &gt;= &lt; &lt;= and &amp; (any of these bits)
 *   item axe                       an item group, or ids: item 1755/1756
 *   item 6465 worn                 options after the item: x5 (count), worn, used up
 *   coins 200
 *   members
 *   empty head                     nothing worn in a slot (head, cape, neck, ...)
 *   answer battered door air rune  something only the player can say
 *   house                          owns a player-owned house
 *   house at Yanille               the house is at this place
 *   house has ornate jewellery box something built in the house
 *   spellbook ancient              the player's spellbook (standard, ancient, lunar, arceuus)
 *   rune air x3                    runes for a spell (combination runes and a worn staff count)
 *   Combat 40                      a combat level (from the real levels)
 *   collection log 6570            an item in the collection log (an item group, or ids: 6570/21295)
 *   spirit tree Port Sarim         the player's spirit tree at this place is grown
 *   canoe axe stored               an axe is stored at the canoe stations
 *   not answer ...                 the opposite of one part (also "not one of (...)")
 * </pre>
 * Example: {@code Agility 56; one of (quest Regicide | varbit 1990 > 39); item axe}.
 */
public final class RequirementText
{
	/** Item group names (any case) to item ids, e.g. "axe". */
	public interface ItemGroups
	{
		ItemGroups NONE = name -> null;

		/** The group's item ids, or null if there is no such group. */
		int[] ids(String name);

		/** The rune kind's sources ("air": air rune and combination runes, and the staves), or null. */
		default RuneSource rune(String name)
		{
			return null;
		}

		static ItemGroups of(Map<String, int[]> groups)
		{
			Map<String, int[]> m = new HashMap<>();
			groups.forEach((k, v) -> m.put(k.trim().toLowerCase(Locale.ROOT), v.clone()));
			return name -> m.get(name.trim().toLowerCase(Locale.ROOT));
		}
	}

	/** Where runes of one kind come from: the rune items (the rune and combination runes) and staves. */
	public static final class RuneSource
	{
		final int[] runeIds;
		final int[] staffIds;

		public RuneSource(int[] runeIds, int[] staffIds)
		{
			this.runeIds = runeIds.clone();
			this.staffIds = staffIds.clone();
		}
	}

	/** A line that can't be read, with the character position of the problem. */
	public static final class ParseException extends IllegalArgumentException
	{
		final int position;

		ParseException(String message, int position)
		{
			super(message + " (at character " + (position + 1) + ")");
			this.position = position;
		}

	}

	private static final Pattern VAR = Pattern.compile("(\\d+)\\s*(!=|>=|<=|=|>|<|&)\\s*(-?\\d+)");
	private static final Pattern IDS = Pattern.compile("\\d+(/\\d+)*");
	private static final Pattern COUNT = Pattern.compile("x(\\d+)");
	private static final Pattern RUNE = Pattern.compile("([A-Za-z][A-Za-z ]*?)\\s+x(\\d+)");

	private final String text;
	private final ItemGroups groups;
	private int pos;

	private RequirementText(String text, ItemGroups groups)
	{
		this.text = text;
		this.groups = groups;
	}

	/** Reads a line; null or blank gives {@link Requirement#NONE}. */
	public static Requirement parse(String text, ItemGroups groups)
	{
		if (text == null || text.trim().isEmpty())
		{
			return Requirement.NONE;
		}
		RequirementText p = new RequirementText(text, groups);
		Requirement r;
		try
		{
			r = p.all();
		}
		catch (NumberFormatException e)
		{
			throw new ParseException("number too big", p.pos);
		}
		p.skipSpaces();
		if (p.pos < text.length())
		{
			throw new ParseException("unexpected '" + text.charAt(p.pos) + "'", p.pos);
		}
		return r;
	}

	private Requirement all()
	{
		List<Requirement> parts = new ArrayList<>();
		parts.add(one());
		while (peek() == ';')
		{
			pos++;
			parts.add(one());
		}
		return Requirement.all(parts);
	}

	private Requirement one()
	{
		skipSpaces();
		if (text.regionMatches(true, pos, "not ", 0, 4))
		{
			pos += 4;
			return new Requirement.Not(one());
		}
		if (text.regionMatches(true, pos, "one of", 0, 6))
		{
			int at = pos;
			pos += 6;
			if (peek() != '(')
			{
				throw new ParseException("'one of' needs '(' after it", at);
			}
			pos++;
			List<Requirement> alts = new ArrayList<>();
			alts.add(all());
			while (peek() == '|')
			{
				pos++;
				alts.add(all());
			}
			if (peek() != ')')
			{
				throw new ParseException("'one of (' is not closed with ')'", at);
			}
			pos++;
			return Requirement.anyOf(alts);
		}
		int start = pos;
		int depth = 0;
		while (pos < text.length())
		{
			char c = text.charAt(pos);
			if (c == ';' || c == '|' || (c == ')' && depth == 0))
			{
				break;
			}
			if (c == '(')
			{
				depth++;
			}
			else if (c == ')')
			{
				depth--;
			}
			pos++;
		}
		String leaf = text.substring(start, pos).trim();
		if (leaf.isEmpty())
		{
			throw new ParseException("empty part", start);
		}
		return leaf(leaf, start);
	}

	private Requirement leaf(String leaf, int at)
	{
		String[] w = leaf.split("\\s+");
		String first = w[0].toLowerCase(Locale.ROOT);
		String rest = leaf.substring(w[0].length()).trim();
		switch (first)
		{
			case "quest":
				return quest(rest, at);
			case "varbit":
			case "varp":
			{
				Matcher m = VAR.matcher(rest);
				if (!m.matches())
				{
					throw new ParseException(first + " needs '<id> <op> <number>', e.g. '" + first + " 1990 > 39'", at);
				}
				return new Requirement.Var(first.equals("varp"), Integer.parseInt(m.group(1)),
					Requirement.Var.Op.bySymbol(m.group(2)), Integer.parseInt(m.group(3)));
			}
			case "item":
				return item(w, at);
			case "coins":
			{
				String n = rest.replace(",", "");
				if (!n.matches("\\d+"))
				{
					throw new ParseException("coins needs a number, e.g. 'coins 200'", at);
				}
				return new Requirement.Coins(Integer.parseInt(n));
			}
			case "members":
				if (!rest.isEmpty())
				{
					throw new ParseException("'members' takes nothing after it", at);
				}
				return Requirement.Members.INSTANCE;
			case "empty":
			{
				EquipSlot s = EquipSlot.byName(rest);
				if (s == null)
				{
					throw new ParseException("unknown slot '" + rest + "' (head, cape, neck, weapon, body, shield, legs, hands, feet, ring, ammo)", at);
				}
				return new Requirement.EmptySlot(s);
			}
			case "house":
			{
				String lower = rest.toLowerCase(Locale.ROOT);
				if (rest.isEmpty())
				{
					return new Requirement.House(Requirement.House.Kind.ANY, "");
				}
				if (lower.startsWith("at ") && rest.length() > 3)
				{
					return new Requirement.House(Requirement.House.Kind.AT, rest.substring(3));
				}
				if (lower.startsWith("has ") && rest.length() > 4)
				{
					return new Requirement.House(Requirement.House.Kind.HAS, rest.substring(4));
				}
				throw new ParseException("house needs nothing, 'at <place>' or 'has <thing>', e.g. 'house has ornate jewellery box'", at);
			}
			case "spellbook":
			{
				Spellbook b = Spellbook.byName(rest);
				if (b == null)
				{
					throw new ParseException("unknown spellbook '" + rest + "' (standard, ancient, lunar, arceuus)", at);
				}
				return new Requirement.OnSpellbook(b);
			}
			case "rune":
			{
				Matcher m = RUNE.matcher(rest);
				if (!m.matches())
				{
					throw new ParseException("rune needs a kind and a count, e.g. 'rune air x3'", at);
				}
				if (Integer.parseInt(m.group(2)) < 1)
				{
					throw new ParseException("rune count must be at least 1", at);
				}
				RuneSource src = groups.rune(m.group(1));
				if (src == null || src.runeIds.length == 0)
				{
					throw new ParseException("unknown rune '" + m.group(1) + "'", at);
				}
				return new Requirement.Runes(m.group(1), Integer.parseInt(m.group(2)), src.runeIds, src.staffIds);
			}
			case "answer":
				if (rest.isEmpty())
				{
					throw new ParseException("answer needs a name", at);
				}
				return new Requirement.Answer(rest);
			case "collection":
				if (w.length < 3 || !w[1].equalsIgnoreCase("log"))
				{
					throw new ParseException("collection log needs an item group or ids, e.g. 'collection log 6570'", at);
				}
				{
					String label = rest.substring(w[1].length()).trim();
					return new Requirement.CollectionLog(label, itemIds(label, at));
				}
			case "spirit":
				if (w.length < 3 || !w[1].equalsIgnoreCase("tree"))
				{
					throw new ParseException("spirit tree needs a place, e.g. 'spirit tree Port Sarim'", at);
				}
				return new Requirement.SpiritTree(rest.substring(w[1].length()).trim());
			case "canoe":
				if (w.length != 3 || !w[1].equalsIgnoreCase("axe") || !w[2].equalsIgnoreCase("stored"))
				{
					throw new ParseException("expected 'canoe axe stored'", at);
				}
				return new Requirement.CanoeAxeStored();
			case "combat":
				if (w.length != 2 || !w[1].matches("\\d+"))
				{
					throw new ParseException("combat needs a level, e.g. 'Combat 40'", at);
				}
				return new Requirement.CombatLevel(Integer.parseInt(w[1]));
			default:
			{
				Skill s = Skill.byName(w[0]);
				if (s == null)
				{
					throw new ParseException("unknown part '" + w[0] + "' (a skill name, Combat, quest, varbit, varp, item, coins, members, empty, answer, house, spellbook, rune, collection log, spirit tree, canoe axe stored, not, one of)", at);
				}
				if (w.length != 2 || !w[1].matches("\\d+"))
				{
					throw new ParseException("a skill needs a level, e.g. '" + s.getLabel() + " 61'", at);
				}
				return new Requirement.SkillLevel(s, Integer.parseInt(w[1]));
			}
		}
	}

	private static Requirement quest(String rest, int at)
	{
		String lower = rest.toLowerCase(Locale.ROOT);
		Requirement.Quest.Wanted wanted = Requirement.Quest.Wanted.DONE;
		String name = rest;
		String[][] endings = {{" not started", "NOT_STARTED"}, {" not done", "NOT_DONE"}, {" started", "STARTED"}};
		for (String[] e : endings)
		{
			if (lower.endsWith(e[0]))
			{
				wanted = Requirement.Quest.Wanted.valueOf(e[1]);
				name = rest.substring(0, rest.length() - e[0].length());
				break;
			}
		}
		if (name.trim().isEmpty())
		{
			throw new ParseException("quest needs a name", at);
		}
		return new Requirement.Quest(name, wanted);
	}

	private Requirement item(String[] w, int at)
	{
		int i = 1;
		StringBuilder name = new StringBuilder();
		while (i < w.length && !isItemOption(w, i))
		{
			name.append(name.length() == 0 ? "" : " ").append(w[i]);
			i++;
		}
		if (name.length() == 0)
		{
			throw new ParseException("item needs a group name or ids, e.g. 'item axe' or 'item 1755/1756'", at);
		}
		int count = 1;
		boolean worn = false;
		boolean usedUp = false;
		for (; i < w.length; i++)
		{
			String o = w[i].toLowerCase(Locale.ROOT);
			Matcher m = COUNT.matcher(o);
			if (m.matches())
			{
				count = Integer.parseInt(m.group(1));
			}
			else if (o.equals("worn"))
			{
				worn = true;
			}
			else if (o.equals("used") && i + 1 < w.length && w[i + 1].equalsIgnoreCase("up"))
			{
				usedUp = true;
				i++;
			}
			else
			{
				throw new ParseException("unknown item option '" + w[i] + "' (x<count>, worn, used up)", at);
			}
		}
		if (count < 1)
		{
			throw new ParseException("item count must be at least 1", at);
		}
		String label = name.toString();
		return new Requirement.Item(label, itemIds(label, at), count, worn, usedUp);
	}

	/** An item label's ids: ids joined with "/" (e.g. 1755/1756), or an item group's name. */
	private int[] itemIds(String label, int at)
	{
		if (IDS.matcher(label).matches())
		{
			String[] parts = label.split("/");
			int[] ids = new int[parts.length];
			for (int k = 0; k < parts.length; k++)
			{
				ids[k] = Integer.parseInt(parts[k]);
			}
			return ids;
		}
		int[] ids = groups.ids(label);
		if (ids == null || ids.length == 0)
		{
			throw new ParseException("unknown item group '" + label + "'", at);
		}
		return ids;
	}

	private static boolean isItemOption(String[] w, int i)
	{
		String o = w[i].toLowerCase(Locale.ROOT);
		return COUNT.matcher(o).matches() || o.equals("worn")
			|| (o.equals("used") && i + 1 < w.length && w[i + 1].equalsIgnoreCase("up"));
	}

	private char peek()
	{
		skipSpaces();
		return pos < text.length() ? text.charAt(pos) : 0;
	}

	private void skipSpaces()
	{
		while (pos < text.length() && Character.isWhitespace(text.charAt(pos)))
		{
			pos++;
		}
	}
}
