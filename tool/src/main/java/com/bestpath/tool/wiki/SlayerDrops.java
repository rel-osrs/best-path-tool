package com.bestpath.tool.wiki;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEVELOPMENT-TIME ONLY: a monster page's drop tables, for each Slayer place's own special loot. Each
 * {@code ==Drops==} section and each {@code ==Drops (X)==} version ("Drops (Ancient Prison)", "Drops (Level 21)")
 * gives its {@code {{DropsLine|name=...|rarity=...}}} lines; a page-specific table a page takes from
 * another Wiki page ({@code {{Uniques/Vyrewatch Sentinel}}}) is read from that page. Shared tables every monster of a kind
 * rolls ({@code {{RareDropTable}}}, {@code {{GemDropTable}}}, herb and seed tables, the Catacombs' and Wilderness
 * Slayer tables) are not read: they don't tell one place from another.
 */
final class SlayerDrops
{
	/**
	 * One drop line: its version ("" for the plain "Drops" section), the item's name, the Wiki's rarity text, and the place
	 * its note limits it to ("Ruins of Tapoyauik" for "Only dropped in [[Ruins of Tapoyauik]]"; "" for none).
	 */
	static final class Drop
	{
		final String version;
		final String item;
		final String rarity;
		final String onlyIn;

		Drop(String version, String item, String rarity, String onlyIn)
		{
			this.version = version;
			this.item = item;
			this.rarity = rarity;
			this.onlyIn = onlyIn;
		}

		@Override
		public String toString()
		{
			return (version.isEmpty() ? "" : version + ": ") + item + " " + rarity;
		}
	}

	private static final Pattern HEADING = Pattern.compile("^(=+)\\s*(.*?)\\s*=+\\s*$");
	private static final Pattern DROPS_LINE = Pattern.compile("\\{\\{\\s*DropsLine\\s*\\|");
	/** A page-specific table on another page ("Uniques/Vyrewatch Sentinel"). */
	private static final Pattern INCLUDED = Pattern.compile("\\{\\{\\s*(Uniques/[^|}\\n]+?)\\s*(\\||\\}\\})");

	private SlayerDrops()
	{
	}

	/** The other pages ({@code "Template:Uniques/..."}) the page's drop sections take a table from. */
	static Set<String> included(String wikitext)
	{
		Set<String> out = new LinkedHashSet<>();
		for (String[] section : sections(wikitext))
		{
			Matcher m = INCLUDED.matcher(section[1]);
			while (m.find())
			{
				out.add("Template:" + m.group(1).trim());
			}
		}
		return out;
	}

	/**
	 * Every drop line of the page's drop sections; {@code template}: an included page's wikitext by its title
	 * ({@link #included}), null when not known (then left out).
	 */
	static List<Drop> parse(String wikitext, Function<String, String> template)
	{
		List<Drop> out = new ArrayList<>();
		for (String[] section : sections(wikitext))
		{
			String body = section[1];
			Matcher inc = INCLUDED.matcher(body);
			StringBuilder all = new StringBuilder(body);
			while (inc.find())
			{
				String t = template.apply("Template:" + inc.group(1).trim());
				if (t != null)
				{
					all.append('\n').append(t);
				}
			}
			for (String[] line : dropsLines(all.toString()))
			{
				out.add(new Drop(section[0], line[0], line[1], line[2]));
			}
		}
		return out;
	}

	/** {version, text} of each "Drops" section (level 2), its sub-headings included, up to the next level-2 heading. */
	static List<String[]> sections(String wikitext)
	{
		List<String[]> out = new ArrayList<>();
		String version = null;
		StringBuilder body = new StringBuilder();
		for (String line : wikitext.split("\n", -1))
		{
			Matcher h = HEADING.matcher(line);
			if (h.matches() && h.group(1).length() == 2)
			{
				if (version != null)
				{
					out.add(new String[]{version, body.toString()});
				}
				version = version(h.group(2));
				body.setLength(0);
				continue;
			}
			if (version != null)
			{
				body.append(line).append('\n');
			}
		}
		if (version != null)
		{
			out.add(new String[]{version, body.toString()});
		}
		out.removeIf(s -> s[0] == null);
		return out;
	}

	/** "Drops" -> "", "Drops (Ancient Prison)" -> "Ancient Prison", any other heading -> null. */
	static String version(String heading)
	{
		String h = heading.trim();
		if (h.equals("Drops"))
		{
			return "";
		}
		Matcher m = Pattern.compile("^Drops\\s*\\((.*)\\)$").matcher(h);
		return m.matches() ? m.group(1).trim() : null;
	}

	/** "Only dropped in [[Ruins of Tapoyauik]]", "Only dropped by those found in the [[Ruins of Tapoyauik]]." */
	private static final Pattern ONLY_IN = Pattern.compile("(?i)only dropped (?:by those found )?in (?:the )?\\[*([^\\]|.<{]+)");

	/** The place a drop line's note limits it to, or "". */
	static String onlyIn(String rarityNotes)
	{
		Matcher m = ONLY_IN.matcher(rarityNotes);
		return m.find() ? m.group(1).trim() : "";
	}

	/** {name, rarity, place its note limits it to ({@link #onlyIn})} of each DropsLine, arguments split at the top level only. */
	static List<String[]> dropsLines(String text)
	{
		List<String[]> out = new ArrayList<>();
		Matcher m = DROPS_LINE.matcher(text);
		while (m.find())
		{
			int start = m.end();
			int depth = 2;
			int i = start;
			List<String> args = new ArrayList<>();
			StringBuilder arg = new StringBuilder();
			for (; i < text.length() && depth > 0; i++)
			{
				char c = text.charAt(i);
				if (c == '{' || c == '[')
				{
					depth++;
				}
				else if (c == '}' || c == ']')
				{
					depth--;
					if (depth < 2)
					{
						break;
					}
				}
				if (c == '|' && depth == 2)
				{
					args.add(arg.toString());
					arg.setLength(0);
				}
				else
				{
					arg.append(c);
				}
			}
			args.add(arg.toString());
			String name = null;
			String rarity = "";
			String notes = "";
			for (String a : args)
			{
				int eq = a.indexOf('=');
				if (eq < 0)
				{
					continue;
				}
				String k = a.substring(0, eq).trim();
				if (k.equals("name"))
				{
					name = a.substring(eq + 1).trim();
				}
				else if (k.equals("rarity"))
				{
					rarity = a.substring(eq + 1).trim().replaceAll("\\s+", " ");
				}
				else if (k.equals("raritynotes"))
				{
					notes = a.substring(eq + 1);
				}
			}
			if (name != null && !name.isEmpty())
			{
				out.add(new String[]{name, rarity, onlyIn(notes)});
			}
		}
		return out;
	}
}
