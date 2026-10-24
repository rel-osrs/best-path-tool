package com.bestpath.tool.clue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code data/clues/clue-overrides.tsv}: hand-authored corrections on top of the
 * generated {@code clue-steps.tsv} (which is never edited by hand), read by the
 * clue check ({@link ClueCheckCli}) and the plugin export ({@code ClueExport}).
 * Format and the rules behind each line: data/clues/README.md.
 *
 * <p>Columns {@code key <tab> change <tab> value <tab> source}; {@code #} lines are
 * comments, the first other line names the columns. Changes:
 * <ul>
 *   <li>{@code tile} {@code x,y,plane}: replaces the step's target tile;</li>
 *   <li>{@code retired} (value {@code -}): the step is dropped;</li>
 *   <li>{@code needs} a requirement line (docs/DATA_FORMATS.md): what the player
 *       needs for the step besides the clue itself (a spade, a light);</li>
 *   <li>{@code skip-area} a Wiki location name of {@code data/wiki/clue-kill-spawns.tsv}:
 *       a kill step does not route there (Slayer-task-only areas);</li>
 *   <li>{@code area-plane} {@code <plane> <Wiki location name>}: that kill area's Wiki
 *       tiles are on this game plane (the Wiki's dungeon map layers say 0);</li>
 *   <li>{@code area-shift} {@code <dx>,<dy>,<plane> <Wiki location name>}: that kill area's
 *       Wiki tiles are moved by dx, dy onto the game plane (a Wiki map layer drawn away from
 *       the game's own tiles);</li>
 *   <li>{@code note} text: a short note for the player about the step (what to do there),
 *       carried to the plugin export; several lines are joined with a space.</li>
 * </ul>
 * The key {@code *dig} stands for every dig step ({@link #isDigStep}). Immutable.
 */
public final class ClueOverrides
{
	public static final String[] COLUMNS = {"key", "change", "value", "source"};
	/** The key that applies a line to every dig step. */
	public static final String DIG_STEPS = "*dig";
	public static final Set<String> CHANGES = Set.of("tile", "retired", "needs", "skip-area", "area-plane", "area-shift", "note");

	/** One line of the file. */
	public static final class Entry
	{
		public final String key;
		public final String change;
		public final String value;
		public final String source;
		/** Line number in the file (1-based), for messages. */
		public final int line;

		Entry(String key, String change, String value, String source, int line)
		{
			this.key = key;
			this.change = change;
			this.value = value;
			this.source = source;
			this.line = line;
		}
	}

	/** No overrides. */
	public static final ClueOverrides NONE = new ClueOverrides(Collections.emptyList());

	private final List<Entry> entries;

	private ClueOverrides(List<Entry> entries)
	{
		this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
	}

	/** The file under a workspace folder (the repository root). */
	public static File file(File workspace)
	{
		return new File(workspace, "data/clues/clue-overrides.tsv");
	}

	/** Reads the file; a missing file gives {@link #NONE}. */
	public static ClueOverrides read(File f) throws IOException
	{
		if (!f.isFile())
		{
			return NONE;
		}
		return parse(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
	}

	/** Reads the file's lines; a bad line throws {@link IllegalArgumentException} naming the line. */
	public static ClueOverrides parse(List<String> lines)
	{
		List<Entry> out = new ArrayList<>();
		boolean header = true;
		int n = 0;
		for (String line : lines)
		{
			n++;
			if (line.startsWith("#") || line.isBlank())
			{
				continue;
			}
			if (header)
			{
				header = false;
				if (!line.equals(String.join("\t", COLUMNS)))
				{
					throw new IllegalArgumentException("clue-overrides line " + n + ": unexpected columns: " + line);
				}
				continue;
			}
			String[] c = line.split("\t", -1);
			if (c.length != COLUMNS.length)
			{
				throw new IllegalArgumentException("clue-overrides line " + n + ": " + c.length + " cells, expected " + COLUMNS.length);
			}
			String key = c[0].trim();
			String change = c[1].trim();
			String value = c[2].trim();
			String source = c[3].trim();
			if (!CHANGES.contains(change))
			{
				throw new IllegalArgumentException("clue-overrides line " + n + ": unknown change '" + change + "'");
			}
			if (source.isEmpty() || source.equals("-"))
			{
				throw new IllegalArgumentException("clue-overrides line " + n + ": no source");
			}
			if (key.equals(DIG_STEPS) && !change.equals("needs"))
			{
				throw new IllegalArgumentException("clue-overrides line " + n + ": " + DIG_STEPS + " only takes 'needs'");
			}
			switch (change)
			{
				case "tile":
					tile(value, n);
					break;
				case "retired":
					if (!value.isEmpty() && !value.equals("-"))
					{
						throw new IllegalArgumentException("clue-overrides line " + n + ": 'retired' takes no value (write -)");
					}
					break;
				case "area-plane":
					areaPlane(value, n);
					break;
				case "area-shift":
					areaShift(value, n);
					break;
				default: // needs, skip-area, note
					if (value.isEmpty() || value.equals("-"))
					{
						throw new IllegalArgumentException("clue-overrides line " + n + ": '" + change + "' needs a value");
					}
					break;
			}
			out.add(new Entry(key, change, value, source, n));
		}
		return new ClueOverrides(out);
	}

	private static int[] tile(String value, int n)
	{
		String[] p = value.split(",");
		try
		{
			if (p.length == 3)
			{
				int[] t = {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
				if (t[2] >= 0 && t[2] <= 3)
				{
					return t;
				}
			}
		}
		catch (NumberFormatException e)
		{
			// reported below
		}
		throw new IllegalArgumentException("clue-overrides line " + n + ": not a tile x,y,plane: " + value);
	}

	/** {plane, location} of an area-plane value. */
	private static Object[] areaPlane(String value, int n)
	{
		int sp = value.indexOf(' ');
		if (sp > 0)
		{
			try
			{
				int plane = Integer.parseInt(value.substring(0, sp));
				String loc = value.substring(sp + 1).trim();
				if (plane >= 0 && plane <= 3 && !loc.isEmpty())
				{
					return new Object[]{plane, loc};
				}
			}
			catch (NumberFormatException e)
			{
				// reported below
			}
		}
		throw new IllegalArgumentException("clue-overrides line " + n + ": area-plane is '<plane> <Wiki location name>': " + value);
	}

	/** {dx, dy, plane, location} of an area-shift value. */
	private static Object[] areaShift(String value, int n)
	{
		int sp = value.indexOf(' ');
		if (sp > 0)
		{
			String[] p = value.substring(0, sp).split(",");
			String loc = value.substring(sp + 1).trim();
			try
			{
				if (p.length == 3 && !loc.isEmpty())
				{
					int plane = Integer.parseInt(p[2].trim());
					if (plane >= 0 && plane <= 3)
					{
						return new Object[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), plane, loc};
					}
				}
			}
			catch (NumberFormatException e)
			{
				// reported below
			}
		}
		throw new IllegalArgumentException("clue-overrides line " + n + ": area-shift is '<dx>,<dy>,<plane> <Wiki location name>': " + value);
	}

	public List<Entry> entries()
	{
		return entries;
	}

	/**
	 * A dig step: the player digs at the target tile. The step's {@code action} says so:
	 * the game's own clue helper rule (client script 6651 of cache 2735, see {@link ClueExtractor#action}): every
	 * coordinate, hot/cold and fairy ring step, every map, cryptic or cipher step whose target is a tile, and the one
	 * anagram row 2299. Steps without an action (made by hand) use a narrower rule: coordinate, hot/cold, fairy ring,
	 * and map or cryptic steps whose target is a tile.
	 */
	public static boolean isDigStep(ClueStep s)
	{
		if (!s.action.isEmpty())
		{
			return s.action.equals("dig");
		}
		switch (s.type)
		{
			case "coordinate":
			case "hotcold":
			case "fairyring":
				return true;
			case "map":
			case "cryptic":
				return s.target.equals("coord");
			default:
				return false;
		}
	}

	private boolean applies(Entry e, ClueStep s)
	{
		return e.key.equals(s.key) || e.key.equals(DIG_STEPS) && isDigStep(s);
	}

	public boolean retired(String key)
	{
		for (Entry e : entries)
		{
			if (e.key.equals(key) && e.change.equals("retired"))
			{
				return true;
			}
		}
		return false;
	}

	/** The step's replacement target tile {x, y, plane}, or null. */
	public int[] tile(String key)
	{
		int[] t = null;
		for (Entry e : entries)
		{
			if (e.key.equals(key) && e.change.equals("tile"))
			{
				t = tile(e.value, e.line);
			}
		}
		return t;
	}

	/** The step's requirement lines (dig rule first, then the step's own), in file order. */
	public List<String> needs(ClueStep s)
	{
		List<String> out = new ArrayList<>();
		for (Entry e : entries)
		{
			if (e.change.equals("needs") && applies(e, s) && !out.contains(e.value))
			{
				out.add(e.value);
			}
		}
		return out;
	}

	/** The step's needs as one requirement line ("" when none). */
	public String needsLine(ClueStep s)
	{
		return String.join("; ", needs(s));
	}

	/** The step's note for the player (its note lines joined with a space), or "" when it has none. */
	public String note(String key)
	{
		List<String> out = new ArrayList<>();
		for (Entry e : entries)
		{
			if (e.key.equals(key) && e.change.equals("note") && !out.contains(e.value))
			{
				out.add(e.value);
			}
		}
		return String.join(" ", out);
	}

	/**
	 * How a kill step's Wiki area is moved onto the game's tiles: {dx, dy, plane} of its area-shift line, or null
	 * (location name as in clue-kill-spawns.tsv, any case).
	 */
	public int[] areaShift(String key, String location)
	{
		int[] shift = null;
		for (Entry e : entries)
		{
			if (e.key.equals(key) && e.change.equals("area-shift"))
			{
				Object[] v = areaShift(e.value, e.line);
				if (((String) v[3]).equalsIgnoreCase(location.trim()))
				{
					shift = new int[]{(Integer) v[0], (Integer) v[1], (Integer) v[2]};
				}
			}
		}
		return shift;
	}

	/** Whether a kill step skips this Wiki area (location name as in clue-kill-spawns.tsv, any case). */
	public boolean skipArea(String key, String location)
	{
		for (Entry e : entries)
		{
			if (e.key.equals(key) && e.change.equals("skip-area") && e.value.equalsIgnoreCase(location.trim()))
			{
				return true;
			}
		}
		return false;
	}

	/** The game plane of a kill step's Wiki area: the area-plane override, else {@code wikiPlane}. */
	public int areaPlane(String key, String location, int wikiPlane)
	{
		int plane = wikiPlane;
		for (Entry e : entries)
		{
			if (e.key.equals(key) && e.change.equals("area-plane"))
			{
				Object[] v = areaPlane(e.value, e.line);
				if (((String) v[1]).equalsIgnoreCase(location.trim()))
				{
					plane = (Integer) v[0];
				}
			}
		}
		return plane;
	}

	/**
	 * The steps with the overrides applied: retired steps dropped, replaced
	 * tiles in place (the source gains "; override tile: ..."). Needs, notes and kill
	 * areas are asked for separately ({@link #needs}, {@link #note}, {@link #skipArea},
	 * {@link #areaPlane}, {@link #areaShift}).
	 */
	public List<ClueStep> apply(List<ClueStep> steps)
	{
		List<ClueStep> out = new ArrayList<>();
		for (ClueStep s : steps)
		{
			if (retired(s.key))
			{
				continue;
			}
			int[] t = tile(s.key);
			if (t == null)
			{
				out.add(s);
				continue;
			}
			String why = "";
			for (Entry e : entries)
			{
				if (e.key.equals(s.key) && e.change.equals("tile"))
				{
					why = e.source;
				}
			}
			out.add(new ClueStep(s.key, s.type, s.tier, s.items, s.text, s.target, s.targetIds, s.targetName, t[0], t[1], t[2], s.description,
				s.places, s.needs, s.notes, s.source + "; override tile " + t[0] + "," + t[1] + "," + t[2] + " (was "
				+ (s.hasTile() ? s.x + "," + s.y + "," + s.plane : "none") + "): " + why, s.checks, s.action, s.requirement, s.stash));
		}
		return out;
	}

	/**
	 * Lines that don't fit the data: a key that is no step, a tile on a step that
	 * already had it, a kill-area line on a step without such an area. Empty = fine.
	 *
	 * @param areas step key -> its Wiki location names (from clue-kill-spawns.tsv)
	 */
	public List<String> problems(List<ClueStep> steps, Map<String, Set<String>> areas)
	{
		Map<String, ClueStep> byKey = new LinkedHashMap<>();
		for (ClueStep s : steps)
		{
			byKey.put(s.key, s);
		}
		List<String> out = new ArrayList<>();
		for (Entry e : entries)
		{
			if (e.key.equals(DIG_STEPS))
			{
				continue;
			}
			ClueStep s = byKey.get(e.key);
			if (s == null)
			{
				out.add("line " + e.line + ": no step " + e.key);
				continue;
			}
			if (e.change.equals("tile"))
			{
				int[] t = tile(e.value, e.line);
				if (s.hasTile() && s.x == t[0] && s.y == t[1] && s.plane == t[2])
				{
					out.add("line " + e.line + ": " + e.key + " already has tile " + e.value);
				}
			}
			if (e.change.equals("skip-area") || e.change.equals("area-plane") || e.change.equals("area-shift"))
			{
				String loc = e.change.equals("skip-area") ? e.value : e.change.equals("area-plane") ? (String) areaPlane(e.value, e.line)[1]
					: (String) areaShift(e.value, e.line)[3];
				Set<String> known = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
				known.addAll(areas.getOrDefault(e.key, Collections.emptySet()));
				if (!known.contains(loc))
				{
					out.add("line " + e.line + ": " + e.key + " has no Wiki area '" + loc + "'");
				}
			}
		}
		return out;
	}

	@Override
	public String toString()
	{
		return String.format(Locale.ROOT, "%d clue overrides", entries.size());
	}
}
