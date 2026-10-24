package com.bestpath.tool.extract;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.cache.LoadedCache;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEVELOPMENT TIME ONLY: where each sea charting task is, from Quest Helper's charting list (used with
 * permission; BSD-2-Clause, data only, none of its code), into {@value ChartingExtractor#PLACES}.
 *
 * <p>Reads {@code helpers/activities/charting/ChartingTasksData.java} as text: each {@code new ChartingTaskDefinition(
 * kind, "text", new WorldPoint(x, y, plane)[, new WorldPoint(...)], "ocean", level, VarbitID.NAME ...)}. The task is
 * found by the varbit's RuneLite name (gameval {@code VarbitID}): task number = varbit - 18574. A task listed twice
 * keeps the entry with more places. Where Quest Helper's text starts with the game's, the rest is kept as a tip ("You'll
 * take 5 damage drinking it."). Prints what differs from the cache (text, level).
 * {@code gradlew -p tool chartingPlaces --args="<quest-helper folder> <cache folder> [commit]"}
 */
public final class ChartingPlacesCli
{
	private ChartingPlacesCli()
	{
	}

	static final String FILE = "src/main/java/com/questhelper/helpers/activities/charting/ChartingTasksData.java";
	private static final Pattern DEF = Pattern.compile("new ChartingTaskDefinition\\(ChartingType\\.(\\w+), \"((?:[^\"\\\\]|\\\\.)*)\", "
		+ "((?:new WorldPoint\\([^)]*\\), )+)\"([^\"]*)\", (\\d+), VarbitID\\.(\\w+)");
	private static final Pattern POINT = Pattern.compile("new WorldPoint\\((\\d+), (\\d+), (\\d+)\\)");

	/** One Quest Helper entry. */
	static final class Entry
	{
		final String text;
		final List<int[]> points = new ArrayList<>();
		final int level;
		final String varbitName;

		Entry(String text, int level, String varbitName)
		{
			this.text = text;
			this.level = level;
			this.varbitName = varbitName;
		}
	}

	/** Every entry of the list's source text, in order. */
	static List<Entry> entries(String source)
	{
		List<Entry> out = new ArrayList<>();
		Matcher m = DEF.matcher(source);
		while (m.find())
		{
			Entry e = new Entry(m.group(2).replace("\\\"", "\"").replace("\\'", "'"), Integer.parseInt(m.group(5)), m.group(6));
			Matcher p = POINT.matcher(m.group(3));
			while (p.find())
			{
				e.points.add(new int[]{Integer.parseInt(p.group(1)), Integer.parseInt(p.group(2)), Integer.parseInt(p.group(3))});
			}
			out.add(e);
		}
		return out;
	}

	/** Quest Helper's text past the game's ("[Bonus] " left out); empty when it adds nothing or says something else. */
	static String tip(String questHelper, String game)
	{
		String q = questHelper.startsWith("[Bonus] ") ? questHelper.substring(8) : questHelper;
		if (q.toLowerCase(Locale.ROOT).startsWith(game.toLowerCase(Locale.ROOT)))
		{
			return q.substring(game.length()).trim();
		}
		return "";
	}

	/**
	 * The places file's lines for these tasks, one per task Quest Helper lists; {@code varbitOf} gives a RuneLite varbit
	 * name's id (-1 unknown). Notes on what differs go to {@code notes}.
	 */
	static List<String> lines(List<Entry> entries, ChartingTasks tasks, java.util.function.ToIntFunction<String> varbitOf,
		String source, List<String> notes)
	{
		Map<Integer, ChartingTasks.Task> byNumber = new TreeMap<>();
		for (ChartingTasks.Task t : tasks.getTasks())
		{
			byNumber.put(t.getNumber(), t);
		}
		Map<Integer, Entry> chosen = new TreeMap<>();
		for (Entry e : entries)
		{
			int varbit = varbitOf.applyAsInt(e.varbitName);
			int number = varbit - ChartingExtractor.FIRST_VARBIT;
			if (varbit < 0 || !byNumber.containsKey(number))
			{
				notes.add("not a charting task varbit: " + e.varbitName);
				continue;
			}
			Entry before = chosen.get(number);
			if (before == null || e.points.size() > before.points.size())
			{
				chosen.put(number, e);
			}
		}
		List<String> out = new ArrayList<>();
		for (Map.Entry<Integer, Entry> c : chosen.entrySet())
		{
			ChartingTasks.Task t = byNumber.get(c.getKey());
			Entry e = c.getValue();
			StringBuilder places = new StringBuilder();
			for (int[] p : e.points)
			{
				places.append(places.length() > 0 ? " " : "").append(p[0]).append(',').append(p[1]).append(',').append(p[2]);
			}
			String q = e.text.startsWith("[Bonus] ") ? e.text.substring(8) : e.text;
			if (!q.toLowerCase(Locale.ROOT).startsWith(t.getText().toLowerCase(Locale.ROOT)))
			{
				notes.add("task " + t.getNumber() + " (" + e.varbitName + "): Quest Helper's text \"" + e.text
					+ "\", the game's \"" + t.getText() + "\" (places kept, no tip)");
			}
			if (e.level != t.getLevel())
			{
				notes.add("task " + t.getNumber() + ": Quest Helper's level " + e.level + ", the game's rule " + t.getLevel()
					+ " (the game's is used)");
			}
			out.add(t.getNumber() + "\t" + places + "\t" + source + "\t" + tip(e.text, t.getText()).replace('\t', ' '));
		}
		for (Integer n : byNumber.keySet())
		{
			if (!chosen.containsKey(n))
			{
				notes.add("task " + n + " is not in Quest Helper's list (no place)");
			}
		}
		return out;
	}

	/** RuneLite's id for a gameval varbit name, -1 if there is none. */
	static int varbitId(String name)
	{
		try
		{
			return net.runelite.api.gameval.VarbitID.class.getField(name).getInt(null);
		}
		catch (ReflectiveOperationException e)
		{
			return -1;
		}
	}

	public static void main(String[] args) throws Exception
	{
		File qh = new File(args.length > 0 ? args[0] : "reference/quest-helper");
		File cacheDir = args.length > 1 ? new File(args[1]) : com.bestpath.tool.cache.DataCache.folder(ToolSettings.findWorkspace());
		String commit = args.length > 2 ? args[2] : "a526461";
		File ws = ToolSettings.findWorkspace();
		String source = "Quest Helper ChartingTasksData.java " + commit;
		ChartingTasks tasks;
		try (LoadedCache cache = LoadedCache.open(cacheDir))
		{
			tasks = ChartingExtractor.extract(cache);
		}
		List<Entry> entries = entries(new String(Files.readAllBytes(new File(qh, FILE).toPath()), StandardCharsets.UTF_8));
		List<String> notes = new ArrayList<>();
		List<String> lines = lines(entries, tasks, ChartingPlacesCli::varbitId, source, notes);
		List<String> file = new ArrayList<>();
		file.add("# Where each sea charting task is (not in the game cache). Made by gradlew -p tool chartingPlaces from Quest");
		file.add("# Helper's charting list (" + FILE + ", commit " + commit + ", BSD-2-Clause; data only).");
		file.add("# Places: the start, then for a current duck or weather task its");
		file.add("# other place. tip: Quest Helper's text past the game's. Joined by the done varbit's RuneLite name.");
		file.add("task\tplaces\tsource\ttip");
		file.addAll(lines);
		File out = new File(ws, ChartingExtractor.PLACES);
		out.getParentFile().mkdirs();
		Files.write(out.toPath(), file, StandardCharsets.UTF_8);
		System.out.printf(Locale.ROOT, "%d Quest Helper entries, %d tasks with a place, written to %s%n", entries.size(), lines.size(),
			out);
		for (String n : notes)
		{
			System.out.println("  " + n);
		}
	}
}
