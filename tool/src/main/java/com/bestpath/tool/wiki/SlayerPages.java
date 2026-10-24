package com.bestpath.tool.wiki;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEVELOPMENT-TIME ONLY: reads the Wiki's "Slayer task/..." pages, for where each task's monsters are. A task page has an
 * {@code {{Infobox Slayer}}} (how many each master gives), a "Monster variants" table (the monsters that count) and a
 * location table ("Location comparison" or "Locations": one row per place, with a {@code {{Map}}} of its spawns, how
 * many there are, multi-combat, cannon, safespot and notes). Pure text work, no web: {@link WikiSlayerCli} fetches.
 */
final class SlayerPages
{
	private SlayerPages()
	{
	}

	/** One row of a task page's location table. */
	static final class Row
	{
		/** The place as plain text ("Slayer Tower (floor 2)"). */
		final String location;
		/** The row's first link ("Slayer Tower"), or the plain text when it has none. */
		final String link;
		/** The row's {@code {{Map}}} parameters, names in lower case ("name", "location", "mapid", "plane"). */
		final Map<String, String> map;
		/** Pins written in the row's map, {x, y}. */
		final List<int[]> pins;
		final String amount;
		final String multi;
		final String cannon;
		final String safespot;
		final String notes;

		Row(String location, String link, Map<String, String> map, List<int[]> pins, String amount, String multi, String cannon,
			String safespot, String notes)
		{
			this.location = location;
			this.link = link;
			this.map = map;
			this.pins = pins;
			this.amount = amount;
			this.multi = multi;
			this.cannon = cannon;
			this.safespot = safespot;
			this.notes = notes;
		}
	}

	/** One wiki table: its column headings (plain text) and its rows' cells (wikitext). */
	static final class Table
	{
		final List<String> headers = new ArrayList<>();
		final List<List<String>> rows = new ArrayList<>();

		int column(String... names)
		{
			for (int i = 0; i < headers.size(); i++)
			{
				for (String n : names)
				{
					if (headers.get(i).equalsIgnoreCase(n))
					{
						return i;
					}
				}
			}
			return -1;
		}

		String cell(List<String> row, int column)
		{
			return column >= 0 && column < row.size() ? row.get(column) : "";
		}
	}

	/** Every wikitable of the page, in page order (tables inside tables are read as part of their cell). */
	static List<Table> tables(String text)
	{
		List<Table> out = new ArrayList<>();
		int from = 0;
		while (true)
		{
			int start = text.indexOf("{|", from);
			if (start < 0)
			{
				break;
			}
			if (start > 0 && text.charAt(start - 1) != '\n')
			{
				from = start + 2;
				continue;
			}
			int end = tableEnd(text, start);
			out.add(table(text.substring(start, end)));
			from = end;
		}
		return out;
	}

	/** Index just past the "|}" that closes the table opening at {@code start} (or the text's end). */
	private static int tableEnd(String text, int start)
	{
		int depth = 0;
		int i = start;
		while (i < text.length())
		{
			if (text.startsWith("\n{|", i))
			{
				depth++;
				i += 3;
			}
			else if (i == start && text.startsWith("{|", i))
			{
				depth++;
				i += 2;
			}
			else if (text.startsWith("\n|}", i) && !text.startsWith("\n|}}", i))
			{
				depth--;
				i += 3;
				if (depth == 0)
				{
					return i;
				}
			}
			else
			{
				i++;
			}
		}
		return text.length();
	}

	/** Splits one table's wikitext into headings and rows of cells, minding {{templates}} and [[links]]. */
	static Table table(String src)
	{
		Table t = new Table();
		List<String> cells = null;
		boolean header = false;
		int depth = 0;
		StringBuilder cell = null;
		String[] lines = src.split("\n", -1);
		for (int li = 1; li < lines.length; li++)
		{
			String line = lines[li];
			if (depth == 0 && (line.startsWith("|-") || line.startsWith("|}")))
			{
				finish(t, cells, cell, header);
				cell = null;
				if (cells != null && !cells.isEmpty() && !header)
				{
					t.rows.add(cells);
				}
				cells = new ArrayList<>();
				header = false;
				if (line.startsWith("|}"))
				{
					break;
				}
				continue;
			}
			if (depth == 0 && (line.startsWith("|") || line.startsWith("!")))
			{
				finish(t, cells, cell, header);
				header = line.startsWith("!");
				if (cells == null)
				{
					cells = new ArrayList<>();
				}
				String rest = line.substring(1);
				List<String> parts = splitTop(rest, header ? "!!" : "||");
				for (int p = 0; p < parts.size() - 1; p++)
				{
					finish(t, cells, new StringBuilder(parts.get(p)), header);
				}
				cell = new StringBuilder(parts.get(parts.size() - 1));
			}
			else if (cell != null)
			{
				cell.append('\n').append(line);
			}
			depth = depthAfter(cell == null ? "" : cell.toString());
		}
		finish(t, cells, cell, header);
		if (cells != null && !cells.isEmpty() && !header)
		{
			t.rows.add(cells);
		}
		return t;
	}

	private static void finish(Table t, List<String> cells, StringBuilder cell, boolean header)
	{
		if (cell == null)
		{
			return;
		}
		String c = withoutAttributes(cell.toString()).trim();
		if (header)
		{
			t.headers.add(plain(c));
		}
		else if (cells != null)
		{
			cells.add(c);
		}
		cell.setLength(0);
	}

	/** "style=... | text" -> "text" (a cell's attributes before its first top-level "|"). */
	private static String withoutAttributes(String c)
	{
		List<String> parts = splitTop(c, "|");
		if (parts.size() > 1 && parts.get(0).contains("=") && !parts.get(0).contains("[[") && !parts.get(0).contains("{{"))
		{
			return c.substring(parts.get(0).length() + 1);
		}
		return c;
	}

	/** How many {{ / [[ are still open at the end of {@code s}. */
	private static int depthAfter(String s)
	{
		int depth = 0;
		for (int i = 0; i + 1 < s.length(); i++)
		{
			char c = s.charAt(i);
			if ((c == '{' || c == '[') && s.charAt(i + 1) == c)
			{
				depth++;
				i++;
			}
			else if ((c == '}' || c == ']') && s.charAt(i + 1) == c)
			{
				depth = Math.max(0, depth - 1);
				i++;
			}
		}
		return depth;
	}

	/** {@code s} split at {@code sep} where no {{ or [[ is open. */
	static List<String> splitTop(String s, String sep)
	{
		List<String> out = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (i + 1 < s.length() && (c == '{' || c == '[') && s.charAt(i + 1) == c)
			{
				depth++;
				i++;
			}
			else if (i + 1 < s.length() && (c == '}' || c == ']') && s.charAt(i + 1) == c)
			{
				depth = Math.max(0, depth - 1);
				i++;
			}
			else if (depth == 0 && s.startsWith(sep, i))
			{
				out.add(s.substring(start, i));
				start = i + sep.length();
				i += sep.length() - 1;
			}
		}
		out.add(s.substring(start));
		return out;
	}

	/** The rows of every location table on a task page (a table with a "Location" column and a map or amount column). */
	static List<Row> locationRows(String text)
	{
		List<Row> out = new ArrayList<>();
		for (Table t : tables(text))
		{
			int loc = t.column("Location", "Locations", "Area");
			int map = t.column("Maplink", "Map", "Map link");
			int amount = t.column("Amount", "Number", "Count", "Spawns");
			if (loc < 0 || (map < 0 && amount < 0) || t.column("Monster", "Combat level") >= 0)
			{
				continue;
			}
			int multi = t.column("Multicombat", "Multi-combat", "Multi");
			int cannon = t.column("Cannonable", "Cannon");
			int safe = t.column("Safespottable", "Safespot");
			int notes = t.column("Notes", "Note");
			for (List<String> r : t.rows)
			{
				String place = t.cell(r, loc);
				if (plain(place).isEmpty())
				{
					continue;
				}
				String mapCell = t.cell(r, map);
				Map<String, String> params = new LinkedHashMap<>();
				List<int[]> pins = new ArrayList<>();
				mapTemplate(mapCell, params, pins);
				String link = WikiBanks.firstLink(place);
				out.add(new Row(plain(place), link == null ? plain(place) : link, params, pins, plain(t.cell(r, amount)).replace(" ,", ","),
					yesNo(t.cell(r, multi)), yesNo(t.cell(r, cannon)), yesNo(t.cell(r, safe)), plain(t.cell(r, notes))));
			}
		}
		return out;
	}

	private static final Pattern PIN = Pattern.compile("^\\s*(?:x:\\s*)?(\\d{3,5})\\s*,\\s*(?:y:\\s*)?(\\d{3,5})\\s*(?:,.*)?$", Pattern.DOTALL);

	/** Reads the first {{Map ...}} in {@code cell}: its parameters (lower-case names) and the pins written in it. */
	static void mapTemplate(String cell, Map<String, String> params, List<int[]> pins)
	{
		int i = cell.toLowerCase(Locale.ROOT).indexOf("{{map");
		if (i < 0)
		{
			return;
		}
		int end = closing(cell, i);
		if (end < 0)
		{
			return;
		}
		List<String> parts = WikiClueKillsCli.topLevelParts(cell.substring(i + 2, end));
		for (int p = 1; p < parts.size(); p++)
		{
			String part = parts.get(p).trim();
			Matcher m = PIN.matcher(part);
			if (m.matches())
			{
				pins.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
				continue;
			}
			int eq = part.indexOf('=');
			if (eq > 0)
			{
				params.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), part.substring(eq + 1).trim());
			}
		}
	}

	/** Index of the "}}" closing the template opening at {@code start}, or -1. */
	private static int closing(String text, int start)
	{
		int depth = 0;
		for (int i = start; i + 1 < text.length(); i++)
		{
			if (text.startsWith("{{", i))
			{
				depth++;
				i++;
			}
			else if (text.startsWith("}}", i))
			{
				depth--;
				if (depth == 0)
				{
					return i;
				}
				i++;
			}
		}
		return -1;
	}

	/**
	 * The page's {@code {{LocLine}}} spawns, written either "|x:3202,y:3253" or as bare pairs ("|3157,9698|3163,9699"),
	 * which many monster pages use. The clue kill steps and the banks read their tiles here too.
	 */
	static List<WikiClueKillsCli.Spawn> spawns(String text)
	{
		List<WikiClueKillsCli.Spawn> out = new ArrayList<>();
		String lower = text.toLowerCase(Locale.ROOT);
		int from = 0;
		while (true)
		{
			int i = lower.indexOf("{{locline", from);
			if (i < 0)
			{
				break;
			}
			int end = closing(text, i);
			if (end < 0)
			{
				break;
			}
			from = end + 2;
			Map<String, String> p = new LinkedHashMap<>();
			List<int[]> tiles = new ArrayList<>();
			List<String> parts = WikiClueKillsCli.topLevelParts(text.substring(i + 2, end));
			for (int k = 1; k < parts.size(); k++)
			{
				String part = parts.get(k).trim();
				Matcher m = PIN.matcher(part);
				if (m.matches())
				{
					tiles.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
					continue;
				}
				int eq = part.indexOf('=');
				if (eq > 0)
				{
					p.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), part.substring(eq + 1).trim());
				}
			}
			int plane = 0;
			try
			{
				plane = Integer.parseInt(p.getOrDefault("plane", "0").trim());
			}
			catch (NumberFormatException ignored)
			{
				// no plane given: the ground
			}
			String location = WikiClueKillsCli.plainLocation(p.getOrDefault("location", ""));
			for (int[] t : tiles)
			{
				out.add(new WikiClueKillsCli.Spawn(location, t[0], t[1], plane, p.getOrDefault("mapid", "").trim()));
			}
		}
		return out;
	}

	/** {{Yes}} -> "yes", {{No}} -> "no", empty -> "?", anything else as plain text ("Partially"). */
	static String yesNo(String cell)
	{
		String c = cell.trim();
		if (c.matches("(?is)\\{\\{\\s*yes\\s*(\\|[^}]*)?}}.*"))
		{
			return "yes";
		}
		if (c.matches("(?is)\\{\\{\\s*no\\s*(\\|[^}]*)?}}.*"))
		{
			return "no";
		}
		String p = plain(c);
		if (p.isEmpty() || p.equalsIgnoreCase("n/a"))
		{
			return "?";
		}
		return p.equalsIgnoreCase("yes") || p.equalsIgnoreCase("no") ? p.toLowerCase(Locale.ROOT) : p;
	}

	/** The monster pages of the page's "Monster variants" table (first column's links, sections dropped), in order. */
	static List<String> variants(String text)
	{
		Set<String> out = new LinkedHashSet<>();
		for (Table t : tables(text))
		{
			int col = t.column("Monster", "Monsters", "Creature", "Name");
			if (col < 0)
			{
				continue;
			}
			for (List<String> r : t.rows)
			{
				String link = WikiBanks.firstLink(t.cell(r, col));
				if (link != null)
				{
					out.add(Character.toUpperCase(link.charAt(0)) + link.substring(1));
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*[-\u2013]\\s*(\\d+)");

	/** {@code {{Infobox Slayer}}}: master key ("turael") -> {min, max} it gives; masters without a range are left out. */
	static Map<String, int[]> amounts(String text)
	{
		Map<String, int[]> out = new LinkedHashMap<>();
		int i = text.toLowerCase(Locale.ROOT).indexOf("{{infobox slayer");
		if (i < 0)
		{
			return out;
		}
		int end = closing(text, i);
		if (end < 0)
		{
			return out;
		}
		for (String part : WikiClueKillsCli.topLevelParts(text.substring(i + 2, end)))
		{
			int eq = part.indexOf('=');
			if (eq < 0)
			{
				continue;
			}
			String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
			Matcher m = RANGE.matcher(part.substring(eq + 1));
			if (m.find())
			{
				out.put(key, new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
			}
		}
		return out;
	}

	/** The task pages linked from the "Slayer task" page's list: link text (lower case) -> page. */
	static Map<String, String> taskPages(String text)
	{
		Map<String, String> out = new LinkedHashMap<>();
		Matcher m = Pattern.compile("\\[\\[(Slayer task/[^]|#]+)(?:\\|([^]]*))?]]").matcher(text);
		while (m.find())
		{
			String page = m.group(1).trim();
			String label = m.group(2) == null ? page.substring("Slayer task/".length()) : m.group(2);
			out.putIfAbsent(plain(label).toLowerCase(Locale.ROOT), page);
			out.putIfAbsent(page.substring("Slayer task/".length()).toLowerCase(Locale.ROOT), page);
		}
		return out;
	}

	/**
	 * The "Slayer task" page's list of assignments: the task's link text (lower case) -> the monster pages its row links
	 * (the task's own link when it is a monster page, then the alternatives; superiors, in bold, left out). For tasks
	 * without a task page of their own.
	 */
	static Map<String, List<String>> assignmentMonsters(String text)
	{
		Map<String, List<String>> out = new LinkedHashMap<>();
		for (Table t : tables(text))
		{
			int task = t.column("Task");
			int alt = t.column("Superiors and alternatives", "Alternatives");
			if (task < 0)
			{
				continue;
			}
			for (List<String> r : t.rows)
			{
				String cell = t.cell(r, task);
				String label = plain(cell).toLowerCase(Locale.ROOT);
				List<String> pages = new ArrayList<>();
				String own = WikiBanks.firstLink(cell);
				if (own != null && !own.startsWith("Slayer task/"))
				{
					pages.add(own);
				}
				Matcher m = Pattern.compile("('''\\s*)?\\[\\[([^]|#]+)[^]]*]]").matcher(t.cell(r, alt));
				while (m.find())
				{
					if (m.group(1) == null && !m.group(2).startsWith("File:"))
					{
						pages.add(m.group(2).trim());
					}
				}
				if (!label.isEmpty())
				{
					out.putIfAbsent(label, pages);
				}
			}
		}
		return out;
	}

	/** Lower-case letters only ("Kalphite Lair" -> "kalphitelair"), for comparing names. */
	static String key(String s)
	{
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/** Whether two names match as names of a task: the same letters, or one is the other plus "s" / "es". */
	static boolean sameName(String a, String b)
	{
		String x = key(a);
		String y = key(b);
		return x.equals(y) || x.equals(y + "s") || y.equals(x + "s") || x.equals(y + "es") || y.equals(x + "es");
	}

	/** Links as their text, templates and references removed; {{FloorNumber|uk=2}} -> "floor 2". */
	static String plain(String s)
	{
		return WikiBanks.plain(s.replaceAll("\\{\\{FloorNumber\\|(?:uk=)?(\\d+)[^}]*}}", "floor $1")
			.replaceAll("\\{\\{Fairycode\\|([^}]*)}}", "$1").replaceAll("(?i)<br\\s*/?>|</br>", ", ")).replaceAll("\\s*,\\s*$", "");
	}
}
