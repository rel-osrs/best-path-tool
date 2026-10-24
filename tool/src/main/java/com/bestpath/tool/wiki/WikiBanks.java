package com.bestpath.tool.wiki;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEVELOPMENT-TIME Wiki data about banks (never read by the plugin): the
 * "List of banks" page's main table (name, map area, members, requirements)
 * and the tiles of bankers at banks that have no booth or chest. Kept in
 * {@code data/wiki/banks.tsv} and {@code data/wiki/bankers.tsv} (CC BY-NC-SA
 * 3.0, page and revision per line), made by {@link WikiBanksCli}.
 */
public final class WikiBanks
{
	public static final String BANKS_FILE = "data/wiki/banks.tsv";
	public static final String BANKERS_FILE = "data/wiki/bankers.tsv";
	static final String BANKS_HEADER = "name\tpage\trevision\tx1\ty1\tx2\ty2\tplane\tmembers\trequirements";
	static final String BANKERS_HEADER = "bank\tnpc\tpage\trevision\tx\ty\tplane\thow";

	private WikiBanks()
	{
	}

	/** One row of the "Banks and chests" table: where it is (a box of tiles) and what it needs. */
	public static final class Entry
	{
		public final String name;
		public final String page;
		public final String revision;
		public final int x1;
		public final int y1;
		public final int x2;
		public final int y2;
		/** The plane the Wiki map shows (0 when it says nothing). */
		public final int plane;
		public final boolean members;
		/** The table's requirement cell as plain text ("None", "quest:Lost City", "skill:Woodcutting 60", ...). */
		public final String requirements;

		public Entry(String name, String page, String revision, int x1, int y1, int x2, int y2, int plane, boolean members,
			String requirements)
		{
			this.name = name;
			this.page = page;
			this.revision = revision;
			this.x1 = Math.min(x1, x2);
			this.y1 = Math.min(y1, y2);
			this.x2 = Math.max(x1, x2);
			this.y2 = Math.max(y1, y2);
			this.plane = plane;
			this.members = members;
			this.requirements = requirements;
		}

		/** Tiles outside the box, at most (0 inside). */
		public int distance(int x, int y)
		{
			int dx = x < x1 ? x1 - x : x > x2 ? x - x2 : 0;
			int dy = y < y1 ? y1 - y : y > y2 ? y - y2 : 0;
			return Math.max(dx, dy);
		}

		String line()
		{
			return String.join("\t", name, page, revision, String.valueOf(x1), String.valueOf(y1), String.valueOf(x2), String.valueOf(y2),
				String.valueOf(plane), members ? "yes" : "no", requirements);
		}
	}

	/** A banker NPC's tile, for a bank with no booth or chest. */
	public static final class Banker
	{
		public final String bank;
		public final String npc;
		public final String page;
		public final String revision;
		public final int x;
		public final int y;
		public final int plane;
		/** Where the tile came from: "LocLine" or "infobox map" (the middle of the NPC's map marker). */
		public final String how;

		public Banker(String bank, String npc, String page, String revision, int x, int y, int plane, String how)
		{
			this.bank = bank;
			this.npc = npc;
			this.page = page;
			this.revision = revision;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.how = how;
		}

		String line()
		{
			return String.join("\t", bank, npc, page, revision, String.valueOf(x), String.valueOf(y), String.valueOf(plane), how);
		}
	}

	private static final Pattern MEMBERS = Pattern.compile("\\{\\{Members\\|\\s*(yes|no)\\s*}}", Pattern.CASE_INSENSITIVE);
	private static final Pattern PAIR = Pattern.compile("^(?:\\d+=)?\\s*(\\d{3,5})\\s*,\\s*(\\d{3,5})\\s*$");
	private static final Pattern PIN = Pattern.compile("^x:\\s*(\\d+)\\s*,\\s*y:\\s*(\\d+)\\s*$");

	/** The rows of the page's first table ("Banks and chests"); rows whose map can't be read are left out (and printed). */
	public static List<Entry> parseList(String wikitext, String revision)
	{
		List<Entry> out = new ArrayList<>();
		int s = wikitext.indexOf("==Banks and chests==");
		if (s < 0)
		{
			throw new IllegalArgumentException("no \"Banks and chests\" section");
		}
		int t = wikitext.indexOf("{|", s);
		int e = wikitext.indexOf("\n|}", t);
		String table = wikitext.substring(t, e);
		String[] rows = table.split("\n\\|-");
		for (int i = 1; i < rows.length; i++)
		{
			List<String> cells = new ArrayList<>();
			for (String line : rows[i].split("\n"))
			{
				if (line.startsWith("|") && !line.startsWith("|}"))
				{
					for (String c : line.substring(1).split("\\|\\|", -1))
					{
						cells.add(c.trim());
					}
				}
			}
			if (cells.size() < 4)
			{
				continue;
			}
			String nameCell = cells.get(0);
			String mapCell = cells.get(1);
			int membersAt = -1;
			for (int c = 2; c < cells.size(); c++)
			{
				if (MEMBERS.matcher(cells.get(c)).find())
				{
					membersAt = c;
				}
			}
			Matcher mm = membersAt < 0 ? null : MEMBERS.matcher(cells.get(membersAt));
			boolean members = mm != null && mm.find() && mm.group(1).equalsIgnoreCase("yes");
			String req = membersAt >= 0 && membersAt + 1 < cells.size() ? cells.get(membersAt + 1) : "";
			int[] box = mapBox(mapCell);
			String name = plain(nameCell);
			String page = firstLink(nameCell);
			if (box == null)
			{
				System.out.println("WIKI BANKS: no map area for " + name + ": " + mapCell);
				continue;
			}
			out.add(new Entry(name, page == null ? name : page, revision, box[0], box[1], box[2], box[3], box[4], members,
				requirementText(req)));
		}
		return out;
	}

	/** {x1, y1, x2, y2, plane} of a {{Map|...}} template, or null. */
	static int[] mapBox(String cell)
	{
		int i = cell.indexOf("{{Map");
		if (i < 0)
		{
			return null;
		}
		int j = cell.indexOf("}}", i);
		String body = cell.substring(i + 2, j < 0 ? cell.length() : j);
		MapTemplate m = new MapTemplate();
		for (String part : WikiClueKillsCli.topLevelParts(body))
		{
			m.read(part);
		}
		if (m.x != null && m.y != null)
		{
			m.points.add(new int[]{m.x, m.y});
		}
		if (m.points.isEmpty())
		{
			return null;
		}
		int x1 = Integer.MAX_VALUE;
		int y1 = Integer.MAX_VALUE;
		int x2 = Integer.MIN_VALUE;
		int y2 = Integer.MIN_VALUE;
		for (int[] pt : m.points)
		{
			x1 = Math.min(x1, pt[0]);
			y1 = Math.min(y1, pt[1]);
			x2 = Math.max(x2, pt[0]);
			y2 = Math.max(y2, pt[1]);
		}
		// a square of radius r round one point; a rectangle rectX x rectY round its middle; pins and polygons: their box
		int grow = m.mtype.equalsIgnoreCase("rectangle") ? (Math.max(m.rectX, m.rectY) + 1) / 2
			: m.points.size() == 1 ? (m.r >= 0 ? m.r : 2) : 0;
		return new int[]{x1 - grow, y1 - grow, x2 + grow, y2 + grow, m.plane};
	}

	/** The parameters of one {{Map|...}} template that {@link #mapBox} reads. */
	private static final class MapTemplate
	{
		Integer x;
		Integer y;
		int r = -1;
		int plane;
		int rectX;
		int rectY;
		String mtype = "";
		final List<int[]> points = new ArrayList<>();

		/** One parameter: a point ("3200,3200" or "x:3200,y:3200") or a named value; a value that is no number is not used. */
		void read(String part)
		{
			String p = part.trim();
			int eq = p.indexOf('=');
			String key = eq > 0 ? p.substring(0, eq).trim().toLowerCase(Locale.ROOT) : "";
			String value = eq > 0 ? p.substring(eq + 1).trim() : p;
			Matcher pair = PAIR.matcher(p);
			Matcher pin = PIN.matcher(value);
			if (pair.find())
			{
				points.add(new int[]{Integer.parseInt(pair.group(1)), Integer.parseInt(pair.group(2))});
			}
			else if (pin.find())
			{
				points.add(new int[]{Integer.parseInt(pin.group(1)), Integer.parseInt(pin.group(2))});
			}
			else
			{
				try
				{
					set(key, value);
				}
				catch (NumberFormatException ignored)
				{
					// not a number: not used
				}
			}
		}

		private void set(String key, String value)
		{
			switch (key)
			{
				case "x":
					x = Integer.parseInt(value);
					break;
				case "y":
					y = Integer.parseInt(value);
					break;
				case "r":
					r = Integer.parseInt(value);
					break;
				case "plane":
					plane = Integer.parseInt(value);
					break;
				case "rectx":
					rectX = Integer.parseInt(value);
					break;
				case "recty":
					rectY = Integer.parseInt(value);
					break;
				case "mtype":
					mtype = value;
					break;
				default:
					break;
			}
		}
	}

	/**
	 * The requirement cell in a simple form: "none", "quest:<name>", "started:<name>", "skill:<Skill> <level>", or
	 * "text:<plain text>" for anything else (kept as a note only).
	 */
	static String requirementText(String cell)
	{
		String c = cell.replaceAll("(?s)<ref[^>]*/>", "").replaceAll("(?s)<ref[^>]*>.*?</ref>", "").trim();
		if (c.isEmpty() || c.equalsIgnoreCase("none"))
		{
			return "none";
		}
		Matcher scp = Pattern.compile("^\\{\\{SCP\\|([A-Za-z]+)\\|(\\d+)[^}]*}}$").matcher(c);
		if (scp.find())
		{
			return "skill:" + scp.group(1) + " " + scp.group(2);
		}
		Matcher started = Pattern.compile("^Started \\[\\[([^]|]+)(?:\\|[^]]*)?]]$").matcher(c);
		if (started.find())
		{
			return "started:" + started.group(1).trim();
		}
		Matcher link = Pattern.compile("^\\[\\[([^]|]+)(?:\\|[^]]*)?]]$").matcher(c);
		if (link.find())
		{
			return "quest:" + link.group(1).trim(); // checked against the cache's quest list before it is used
		}
		return "text:" + plain(c);
	}

	/** Links as their text, templates and references removed, spaces tidied. */
	static String plain(String s)
	{
		return s.replaceAll("(?s)<ref[^>]*/>", "").replaceAll("(?s)<ref[^>]*>.*?</ref>", "")
			.replaceAll("\\{\\{SCP\\|([A-Za-z]+)\\|(\\d+)[^}]*}}", "$1 $2").replaceAll("\\{\\{[^}]*}}", "")
			.replaceAll("\\[\\[(?:[^]|]*\\|)?([^]]*)]]", "$1").replace('\u2019', '\'').replaceAll("[\\t\\n ]+", " ").trim();
	}

	/** The first link's page (without a section), or null. */
	static String firstLink(String s)
	{
		Matcher m = Pattern.compile("\\[\\[([^]|#]+)").matcher(s);
		return m.find() ? m.group(1).trim() : null;
	}

	// ---------------------------------------------------------------------------------------------- files

	public static List<Entry> readBanks(File f) throws IOException
	{
		List<Entry> out = new ArrayList<>();
		for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
		{
			if (line.startsWith("#") || line.isBlank() || line.startsWith("name\t"))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			out.add(new Entry(c[0], c[1], c[2], Integer.parseInt(c[3]), Integer.parseInt(c[4]), Integer.parseInt(c[5]),
				Integer.parseInt(c[6]), Integer.parseInt(c[7]), c[8].equals("yes"), c[9]));
		}
		return out;
	}

	public static List<Banker> readBankers(File f) throws IOException
	{
		List<Banker> out = new ArrayList<>();
		if (!f.isFile())
		{
			return out;
		}
		for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
		{
			if (line.startsWith("#") || line.isBlank() || line.startsWith("bank\t"))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			out.add(new Banker(c[0], c[1], c[2], c[3], Integer.parseInt(c[4]), Integer.parseInt(c[5]), Integer.parseInt(c[6]),
				c.length > 7 ? c[7] : "LocLine"));
		}
		return out;
	}

	static String banksText(List<Entry> entries)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("# Banks from the Old School RuneScape Wiki page \"List of banks\" (main table). DEVELOPMENT-TIME data, CC BY-NC-SA 3.0\n");
		sb.append("# (data/wiki/LICENSE-WIKI.txt); page and revision per line. GENERATED by `gradlew -p tool wikiBanks`; do not edit by hand.\n");
		sb.append("# x1 y1 x2 y2: the box the Wiki map marks (a square, rectangle or polygon round the bank); requirements: none,\n");
		sb.append("# quest:<page>, started:<page>, skill:<Skill> <level>, or text:<anything else> (not used by routes).\n");
		sb.append(BANKS_HEADER).append('\n');
		for (Entry e : entries)
		{
			sb.append(e.line()).append('\n');
		}
		return sb.toString();
	}

	static String bankersText(List<Banker> bankers)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("# Bankers at banks that have no bank booth or chest in the cache: the {{LocLine}} templates on the NPCs' Wiki pages,\n");
		sb.append("# else the middle of the infobox map (how). DEVELOPMENT-TIME data from the Old School RuneScape Wiki, CC BY-NC-SA 3.0\n");
		sb.append("# (data/wiki/LICENSE-WIKI.txt);\n");
		sb.append("# page and revision per line. GENERATED by `gradlew -p tool wikiBanks`; do not edit by hand.\n");
		sb.append(BANKERS_HEADER).append('\n');
		for (Banker b : bankers)
		{
			sb.append(b.line()).append('\n');
		}
		return sb.toString();
	}
}
