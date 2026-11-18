package com.bestpath.tool.wiki;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads {@code {{TeleportLocationLine|name=..|x=..|y=..}}} lines from a
 * page's wikitext, and the {@code {{Map}}} templates under a destination
 * heading (on a page with such lines, only the maps that outline a landing
 * zone, since the lines already give the exact destinations), and the kind of
 * page from its first infobox (spell, item, or anything else). Only these
 * templates are read; nothing is guessed from prose.
 *
 * <p>Landing zones: a line's own {@code r} (with no {@code mtype}, or
 * {@code mtype=square}) is a square of that radius, and {@code mtype=pin} an
 * exact tile. A line with neither is the 3-tile square the template draws by
 * default ({@code r=3|mtype=square}), as the wiki shows it, and is flagged
 * {@code templateDefaultZone}. A map in a table row is named after the row's
 * first link (the destination). A map's outline ({@code mtype=polygon} and so
 * on, with several points) keeps its points and its middle.
 */
final class WikiText
{
	private WikiText()
	{
	}

	static List<WikiTeleportsCli.Record> records(String title, long revid, String text)
	{
		String kind = pageKind(text);
		List<WikiTeleportsCli.Record> out = new ArrayList<>();
		int from = 0;
		while (true)
		{
			int i = indexOfTemplate(text, "TeleportLocationLine", from);
			if (i < 0)
			{
				break;
			}
			int end = closing(text, i);
			if (end < 0)
			{
				break;
			}
			Map<String, String> p = params(text.substring(i + 2, end));
			from = end + 2;
			Integer x = number(p.get("x"));
			Integer y = number(p.get("y"));
			if (x == null || y == null)
			{
				continue;
			}
			String map = p.get("mapid");
			if (map != null && !map.isBlank() && !map.trim().equals("0") && !map.trim().equals("-1"))
			{
				continue; // another map (e.g. an instance), not the world map
			}
			WikiTeleportsCli.Record r = new WikiTeleportsCli.Record();
			r.group = title;
			r.groupKind = kind;
			r.name = plain(p.getOrDefault("name", title));
			r.x = x;
			r.y = y;
			Integer plane = number(p.containsKey("plane") ? p.get("plane") : p.get("z"));
			r.planeGiven = plane != null;
			r.plane = plane != null ? plane : 0;
			r.note = plain(p.getOrDefault("note", ""));
			r.page = title;
			r.revid = revid;
			r.how = "{{TeleportLocationLine}}";
			String shape = p.containsKey("mtype") && !p.get("mtype").isBlank() ? p.get("mtype").trim().toLowerCase(Locale.ROOT) : null;
			Double radius = decimal(p.get("r"));
			if (radius != null && (shape == null || shape.equals("square")))
			{
				square(r, radius);
			}
			else if (shape == null || (shape.equals("square") && radius == null))
			{
				square(r, 3); // the template's default, as the wiki draws it
				r.templateDefaultZone = true;
			}
			out.add(r);
		}
		boolean lines = !out.isEmpty();
		for (WikiTeleportsCli.Record m : mapsUnderDestination(title, revid, kind, text))
		{
			if (!lines || m.zone)
			{
				out.add(m);
			}
		}
		return out;
	}

	/**
	 * {{Map|x,y|...}} templates in a section whose heading says "destination"
	 * or "teleport" (spell pages keep their destination this way). Maps in
	 * other sections (where to find an item, for example) are not read.
	 */
	private static List<WikiTeleportsCli.Record> mapsUnderDestination(String title, long revid, String kind, String text)
	{
		List<WikiTeleportsCli.Record> out = new ArrayList<>();
		String heading = "";
		String previous = "";
		for (String line : text.split("\n", -1))
		{
			String row = previous;
			previous = line;
			String t = line.trim();
			if (t.startsWith("==") && t.endsWith("=="))
			{
				heading = t.replace("=", "").trim();
			}
			String h = heading.toLowerCase(Locale.ROOT);
			boolean destination = h.contains("destination") || h.contains("teleport");
			int from = 0;
			while (destination)
			{
				int i = indexOfTemplate(line, "Map", from);
				if (i < 0)
				{
					break;
				}
				String after = line.substring(i + 2).trim();
				if (!after.matches("(?is)map\\s*[|}].*"))
				{
					from = i + 2; // another template whose name starts with "Map"
					continue;
				}
				int end = closing(line, i);
				if (end < 0)
				{
					break;
				}
				from = end + 2;
				// in a table row, the destination is the row's first link: before the map on its line, or on the
				// line above when the map has a cell line of its own
				String name = t.startsWith("|") ? firstLink(line.substring(0, i)) : null;
				if (name == null && t.startsWith("|") && row.trim().startsWith("|") && !row.trim().startsWith("|-"))
				{
					name = firstLink(row);
				}
				addMapRecords(title, revid, kind, heading, name, line.substring(i + 2, end), out);
			}
		}
		return out;
	}

	/** The destinations of one {{Map}} template's body (none when it is on another map layer than the surface). */
	private static void addMapRecords(String title, long revid, String kind, String heading, String name, String body,
		List<WikiTeleportsCli.Record> out)
	{
		Map<String, String> p = params(body);
		String map = p.get("mapid");
		if (map != null && !map.isBlank() && !map.trim().equals("0") && !map.trim().equals("-1"))
		{
			return;
		}
		Integer plane = number(p.containsKey("plane") ? p.get("plane") : p.get("z"));
		List<String[]> points = new ArrayList<>();
		for (String part : body.split("\\|"))
		{
			String s = part.trim();
			if (s.matches("\\d{3,5}\\s*,\\s*\\d{3,5}(\\s*,\\s*\\d)?"))
			{
				points.add(s.split("\\s*,\\s*"));
			}
		}
		String mtype = p.getOrDefault("mtype", "pin").trim().toLowerCase(Locale.ROOT);
		String area = null;
		List<int[]> outline = null;
		Double radius = null;
		if (points.size() > 1 && !mtype.equals("pin"))
		{
			// an outline of the landing area (polygon, line...): one destination in its middle, the points kept
			area = "lands somewhere in an area (" + mtype + " of " + points.size() + " points); its middle is used";
			outline = new ArrayList<>();
			for (String[] c : points)
			{
				outline.add(new int[]{Integer.parseInt(c[0]), Integer.parseInt(c[1])});
			}
			String[] mid = middle(points);
			points.clear();
			points.add(mid);
		}
		else if (mtype.equals("square") && decimal(p.get("r")) != null)
		{
			radius = decimal(p.get("r"));
			area = "lands within " + p.get("r").trim() + " tiles of this point";
		}
		int k = 0;
		for (String[] c : points)
		{
			WikiTeleportsCli.Record r = new WikiTeleportsCli.Record();
			r.group = title;
			r.groupKind = kind;
			r.name = (name != null ? name : title) + (k > 0 ? " (" + (k + 1) + ")" : "");
			r.x = Integer.parseInt(c[0]);
			r.y = Integer.parseInt(c[1]);
			Integer pl = c.length > 2 ? Integer.valueOf(c[2]) : plane;
			r.planeGiven = pl != null;
			r.plane = pl != null ? pl : 0;
			r.note = area != null ? area : "";
			r.page = title;
			r.revid = revid;
			r.how = "{{Map}} under the heading \"" + heading + "\"";
			if (outline != null)
			{
				r.zone = true;
				r.zoneShape = mtype;
				r.zonePoints = outline;
				int reach = 0;
				for (int[] o : outline)
				{
					reach = Math.max(reach, Math.max(Math.abs(o[0] - r.x), Math.abs(o[1] - r.y)));
				}
				r.zoneReach = reach;
			}
			else if (radius != null)
			{
				square(r, radius);
			}
			out.add(r);
			k++;
		}
	}

	/** The label of the first link in the text that isn't a file, or null. */
	private static String firstLink(String text)
	{
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[\\[([^\\]|]*)(?:\\|([^\\]]*))?\\]\\]").matcher(text);
		while (m.find())
		{
			String target = m.group(1).trim();
			if (target.regionMatches(true, 0, "File:", 0, 5) || target.regionMatches(true, 0, "Image:", 0, 6))
			{
				continue;
			}
			return plain(m.group(2) != null ? m.group(2) : target);
		}
		return null;
	}

	/** Marks the record as landing within {@code radius} tiles of its point. */
	private static void square(WikiTeleportsCli.Record r, double radius)
	{
		r.zone = true;
		r.zoneShape = "square";
		r.zoneRadius = radius;
		r.zoneReach = (int) Math.floor(radius);
	}

	/** The rounded middle {x, y} of the points. */
	private static String[] middle(List<String[]> points)
	{
		long sx = 0;
		long sy = 0;
		for (String[] c : points)
		{
			sx += Integer.parseInt(c[0]);
			sy += Integer.parseInt(c[1]);
		}
		return new String[]{String.valueOf(Math.round((double) sx / points.size())),
			String.valueOf(Math.round((double) sy / points.size()))};
	}

	/** "spell" or "item" from the page's first infobox; anything else is "other". */
	static String pageKind(String text)
	{
		int i = text.indexOf("{{Infobox");
		if (i < 0)
		{
			return "other";
		}
		String rest = text.substring(i + 9).trim().toLowerCase(Locale.ROOT);
		if (rest.startsWith("spell"))
		{
			return "spell";
		}
		if (rest.startsWith("item") || rest.startsWith("bonuses"))
		{
			return "item";
		}
		return "other";
	}

	private static int indexOfTemplate(String text, String name, int from)
	{
		int i = from;
		while (true)
		{
			i = text.indexOf("{{", i);
			if (i < 0)
			{
				return -1;
			}
			String after = text.substring(i + 2, Math.min(text.length(), i + 2 + name.length() + 2)).trim();
			if (after.regionMatches(true, 0, name, 0, name.length()))
			{
				return i;
			}
			i += 2;
		}
	}

	/** Index of the "}}" closing the template that opens at {@code open}, or -1. */
	private static int closing(String text, int open)
	{
		int depth = 0;
		for (int i = open; i < text.length() - 1; i++)
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

	/** Named parameters of a template body, split at top-level '|' (not inside {{ }} or [[ ]]). */
	private static Map<String, String> params(String body)
	{
		Map<String, String> out = new LinkedHashMap<>();
		List<String> parts = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < body.length(); i++)
		{
			char c = body.charAt(i);
			if ((c == '{' || c == '[') && i + 1 < body.length() && body.charAt(i + 1) == c)
			{
				depth++;
				i++;
			}
			else if ((c == '}' || c == ']') && i + 1 < body.length() && body.charAt(i + 1) == c)
			{
				depth--;
				i++;
			}
			else if (c == '|' && depth == 0)
			{
				parts.add(body.substring(start, i));
				start = i + 1;
			}
		}
		parts.add(body.substring(start));
		for (int k = 1; k < parts.size(); k++)
		{
			String s = parts.get(k);
			int eq = s.indexOf('=');
			if (eq > 0)
			{
				out.put(s.substring(0, eq).trim().toLowerCase(Locale.ROOT), s.substring(eq + 1).trim());
			}
		}
		return out;
	}

	private static Integer number(String s)
	{
		if (s == null)
		{
			return null;
		}
		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static Double decimal(String s)
	{
		if (s == null)
		{
			return null;
		}
		try
		{
			double d = Double.parseDouble(s.trim());
			return d >= 0 && !Double.isInfinite(d) ? d : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Wikitext to plain text: links to their label, templates and markup removed. */
	static String plain(String s)
	{
		String t = s;
		// drop templates (innermost first)
		for (int guard = 0; guard < 20 && t.contains("{{"); guard++)
		{
			t = t.replaceAll("\\{\\{[^{}]*\\}\\}", "");
		}
		t = t.replaceAll("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]", "$1");
		t = t.replaceAll("\\[\\[([^\\]]*)\\]\\]", "$1");
		t = t.replaceAll("'{2,}", "");
		t = t.replaceAll("<[^>]*>", "");
		return t.replaceAll("\\s+", " ").trim();
	}
}
