package com.bestpath.tool.extract;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.data.ChartingTasksBuilder;
import com.bestpath.core.data.ChartingTasksDiagnostics;
import com.bestpath.tool.cache.LoadedCache;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;

/**
 * The sea charting tasks and sets from the game cache.
 *
 * <p>Checked in cache #2735: DB table {@value #CORE} ({@code SailingChartingCore}) has 358 rows, one
 * per task: column 0 the task number (0-357), 1 the task's text, 2 the set the game counts it in (a row of table
 * {@value #SEAS}: a sea, or for the 33 bonus tasks a "Bonus ..." group, type 2), 3 the sea a bonus task is in, 4 "gives
 * repeat experience", 5 the kind (a table id, {@link #kind}), 6 a hazard (a row of table {@value #HAZARDS}), 7 a
 * needed dock (a row of table {@value #DOCKS}). The kind tables 155-160 only point back at table 154. Each task's
 * "done" varbit is {@value #FIRST_VARBIT} + its number (all 358 checked against RuneLite's names
 * {@code SAILING_CHARTING_*_COMPLETE}). The game's Captain's Log (client script 8706) shows:
 * <ul>
 * <li>the Sailing level (script {@value #LEVEL_SCRIPT}): the highest of a level by kind (a switch on column 5,
 * otherwise 1), a level by hazard (a switch on column 6, otherwise 1) and the dock's level (table 194 column 4);
 * the switches are read from the script, so a changed script is noticed. Matches Quest Helper's level for 357 of 358
 * tasks.</li>
 * <li>"XP awarded" (script 8999): enum {@value #TASK_XP_ENUM} by kind table, divided by 10.</li>
 * <li>"Sea / Ocean Completion (N XP)" (scripts 8700 / 8701, 9000): a sea or bonus group: enum {@value #SEA_XP_ENUM}
 * at its experience level (table 201 column 6 when above 1, else its level, column 5); an ocean: enum
 * {@value #OCEAN_XP_ENUM} by its row; both divided by 10.</li>
 * </ul>
 * No task has a tile in the cache: places come from {@value #PLACES} (Quest Helper's charting list, used with
 * permission).
 */
public final class ChartingExtractor
{
	private ChartingExtractor()
	{
	}

	static final int CORE = 154;
	static final int SEAS = 201;
	static final int HAZARDS = 200;
	static final int DOCKS = 194;
	/** The varbit of task 0 ({@code SAILING_CHARTING_GENERIC_BOARD_PORT_SARIM_COMPLETE}). */
	static final int FIRST_VARBIT = 18574;
	static final int LEVEL_SCRIPT = 9001;
	static final int TASK_XP_ENUM = 5868;
	static final int OCEAN_XP_ENUM = 5869;
	static final int SEA_XP_ENUM = 5870;
	/** DB table 161 ({@code SailingChartingToolRecovery}): each tool's name (column 0) and items (column 2). */
	static final int TOOLS = 161;

	/** Where each task is (not in the cache): {@code task <tab> places <tab> source <tab> tip}, see {@link #withPlaces}. */
	public static final String PLACES = "data/charting/charting-places.tsv";

	/** The kind a kind table stands for; null for another table. */
	static String kind(int table)
	{
		switch (table)
		{
			case 155:
				return "generic";
			case 156:
				return "spyglass";
			case 157:
				return "current";
			case 158:
				return "crate";
			case 159:
				return "weather";
			case 160:
				return "mermaid";
			default:
				return null;
		}
	}

	/**
	 * The tool a kind needs (client script 8706, "Tool needed": a switch on the task's kind table; the inventory is
	 * checked for item 31803, 31805 or 31807, and script 8868 checks the diving outfits), as its name in table
	 * {@value #TOOLS}; null for none.
	 */
	static String toolName(int kindTable)
	{
		switch (kindTable)
		{
			case 156:
				return "Spyglass";
			case 157:
				return "Current duck";
			case 158:
				return "Crowbar";
			case 160:
				return "Diving gear";
			default:
				return null;
		}
	}

	/** What the game's level script and experience enums say. */
	static final class Rules
	{
		/** Level by kind table and by hazard row (others: 1). */
		final Map<Integer, Integer> kindLevel;
		final Map<Integer, Integer> hazardLevel;
		/** Enum values, not yet divided by 10. */
		final IntFunction<Integer> taskXp;
		final IntFunction<Integer> seaXp;
		final IntFunction<Integer> oceanXp;

		Rules(Map<Integer, Integer> kindLevel, Map<Integer, Integer> hazardLevel, IntFunction<Integer> taskXp,
			IntFunction<Integer> seaXp, IntFunction<Integer> oceanXp)
		{
			this.kindLevel = kindLevel;
			this.hazardLevel = hazardLevel;
			this.taskXp = taskXp;
			this.seaXp = seaXp;
			this.oceanXp = oceanXp;
		}
	}

	/**
	 * Each drink crate's drink (the task only needs the player to get it): {@code varbit <tab> item},
	 * from RuneLite's public gameval names (varbit SAILING_CHARTING_DRINK_CRATE_X_COMPLETE -> item
	 * SAILING_CHARTING_DRINK_CRATE_X); the cache's tables don't link them.
	 */
	public static final String DRINKS = "data/manual/charting-drinks.tsv";

	/** The plugin's tasks: the cache's, with {@value #PLACES} and {@value #DRINKS} added. */
	public static ChartingTasks export(LoadedCache cache, File ws) throws IOException
	{
		ChartingTasks t = extract(cache);
		File f = new File(ws, PLACES);
		t = f.isFile() ? withPlaces(t, Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) : t;
		File d = new File(ws, DRINKS);
		return d.isFile() ? withDrinks(t, Files.readAllLines(d.toPath(), StandardCharsets.UTF_8), id ->
		{
			try
			{
				return cache.itemName(id);
			}
			catch (IOException e)
			{
				return null;
			}
		}) : t;
	}

	/** How far from a task's place a boat spot is looked for (tiles, Chebyshev rings). */
	public static final int BOAT_REACH = 30;

	/** Main ocean water (the sea grid's {@code isOcean}). */
	public interface Ocean
	{
		boolean at(int x, int y);
	}

	/** The nearest ocean tile by rings (ties: the one nearest as the crow flies), or null within {@value #BOAT_REACH}. */
	public static int[] nearestOcean(int x, int y, Ocean ocean)
	{
		if (ocean.at(x, y))
		{
			return new int[]{x, y};
		}
		for (int r = 1; r <= BOAT_REACH; r++)
		{
			int[] best = null;
			int bestD = Integer.MAX_VALUE;
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r || !ocean.at(x + dx, y + dy))
					{
						continue;
					}
					int d = dx * dx + dy * dy;
					if (d < bestD)
					{
						bestD = d;
						best = new int[]{x + dx, y + dy};
					}
				}
			}
			if (best != null)
			{
				return best;
			}
		}
		return null;
	}

	/**
	 * The drink crate tasks with their drinks ({@code varbit <tab> item} lines, # comments); an item the cache has no
	 * name for is left out (the task keeps no drink).
	 */
	static ChartingTasks withDrinks(ChartingTasks tasks, List<String> lines, IntFunction<String> itemName)
	{
		Map<Integer, Integer> byVarbit = new HashMap<>();
		for (String line : lines)
		{
			if (line.isBlank() || line.startsWith("#") || line.startsWith("varbit"))
			{
				continue;
			}
			String[] c = line.split("\t");
			byVarbit.put(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()));
		}
		List<ChartingTasks.Task> out = new ArrayList<>();
		for (ChartingTasks.Task t : tasks.getTasks())
		{
			Integer item = byVarbit.get(t.getVarbit());
			String name = item == null ? null : itemName.apply(item);
			out.add(name == null || name.equals("null") ? t : ChartingTasksBuilder.withDrink(t, item, name));
		}
		return new ChartingTasks(tasks.getSets(), out);
	}

	/** The sets and tasks from the cache, without places, in task number order. */
	public static ChartingTasks extract(LoadedCache cache) throws IOException
	{
		Map<Integer, Integer>[] levels = levelSwitches(cache.script(LEVEL_SCRIPT));
		Rules rules = new Rules(levels[0], levels[1], enumInts(cache, TASK_XP_ENUM), enumInts(cache, SEA_XP_ENUM),
			enumInts(cache, OCEAN_XP_ENUM));
		return build(cache.dbRowsById(CORE), cache.dbRowsById(SEAS), cache.dbRowsById(HAZARDS), cache.dbRowsById(DOCKS), rules,
			cache.dbRowsById(TOOLS));
	}

	private static IntFunction<Integer> enumInts(LoadedCache cache, int id)
	{
		net.runelite.cache.definitions.EnumDefinition e = cache.enumDefinition(id);
		if (e == null)
		{
			throw new IllegalStateException("enum " + id + " (sea charting experience) is not in the cache");
		}
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < e.getKeys().length; i++)
		{
			m.put(e.getKeys()[i], e.getIntVals()[i]);
		}
		int fallback = e.getDefaultInt();
		return k -> m.getOrDefault(k, fallback);
	}

	/**
	 * The level script's two switches: [0] level by kind table, [1] level by hazard row. Each case must set the level
	 * with a constant ({@code iconst}); a script shaped otherwise stops the export.
	 */
	@SuppressWarnings("unchecked")
	static Map<Integer, Integer>[] levelSwitches(net.runelite.cache.definitions.ScriptDefinition s)
	{
		if (s == null || s.getSwitches() == null || s.getSwitches().length != 2)
		{
			throw new IllegalStateException("client script " + LEVEL_SCRIPT + " (charting level) is not shaped as in cache 2735");
		}
		int[] ins = s.getInstructions();
		int[] ops = s.getIntOperands();
		Map<Integer, Integer>[] out = new Map[2];
		int table = 0;
		for (int pc = 0; pc < ins.length && table < 2; pc++)
		{
			if (ins[pc] != net.runelite.cache.script.Opcodes.SWITCH)
			{
				continue;
			}
			Map<Integer, Integer> m = new TreeMap<>();
			for (Map.Entry<Integer, Integer> e : s.getSwitches()[ops[pc]].entrySet())
			{
				int target = pc + 1 + e.getValue();
				if (target >= ins.length || ins[target] != net.runelite.cache.script.Opcodes.ICONST)
				{
					throw new IllegalStateException("client script " + LEVEL_SCRIPT + ": case " + e.getKey()
						+ " does not set a constant level");
				}
				m.put(e.getKey(), ops[target]);
			}
			out[table++] = m;
		}
		if (table != 2)
		{
			throw new IllegalStateException("client script " + LEVEL_SCRIPT + " has fewer than two switches");
		}
		return out;
	}

	/** {@link #extract} from the DB rows (by row id) and the game's rules, for tests. */
	static ChartingTasks build(Map<Integer, Object[][]> core, Map<Integer, Object[][]> seas, Map<Integer, Object[][]> hazards,
		Map<Integer, Object[][]> docks, Rules rules, Map<Integer, Object[][]> tools)
	{
		// each tool's items by its name; diving gear is left unchecked (the game checks several outfits, script 8868)
		Map<String, int[]> toolItems = new HashMap<>();
		for (Object[][] t : tools.values())
		{
			String name = stringAt(t, 0);
			int[] items = t.length > 2 && t[2] != null ? java.util.Arrays.stream(t[2]).mapToInt(o -> ((Number) o).intValue()).toArray()
				: new int[0];
			toolItems.put(name, name.equals("Diving gear") ? new int[0] : items);
		}
		// the sets in the game's order (table 201 column 0): each ocean, then its seas and groups
		List<Integer> seaRows = new ArrayList<>(seas.keySet());
		seaRows.sort(Comparator.comparingInt(r -> intAt(seas.get(r), 0)));
		List<ChartingTasks.Set> sets = new ArrayList<>();
		for (int r : seaRows)
		{
			Object[][] s = seas.get(r);
			int type = intAt(s, 3);
			if (type == 0 || type == 2)
			{
				int xpLevel = intAt(s, 6) > 1 ? intAt(s, 6) : intAt(s, 5);
				sets.add(new ChartingTasks.Set(stringAt(s, 2), type == 2 ? ChartingTasks.BONUS : ChartingTasks.SEA,
					stringAt(seas.get(intAt(s, 4)), 2), rules.seaXp.apply(xpLevel) / 10));
			}
			else
			{
				sets.add(new ChartingTasks.Set(stringAt(s, 2), ChartingTasks.OCEAN, "", rules.oceanXp.apply(r) / 10));
			}
		}
		List<ChartingTasks.Task> out = new ArrayList<>();
		for (Object[][] r : core.values())
		{
			int number = intAt(r, 0);
			int kindTable = intAt(r, 5);
			String kind = kind(kindTable);
			if (number < 0 || kind == null)
			{
				throw new IllegalStateException("charting task row without a number or a known kind: column 0 " + number
					+ ", column 5 " + kindTable);
			}
			Object[][] set = seas.get(intAt(r, 2));
			// a bonus task (its set a "Bonus ..." group, type 2) is in the sea of column 3
			Object[][] sea = intAt(set, 3) == 2 && intAt(r, 3) >= 0 ? seas.get(intAt(r, 3)) : set;
			Object[][] dock = docks.get(intAt(r, 7));
			int level = Math.max(rules.kindLevel.getOrDefault(kindTable, 1), Math.max(rules.hazardLevel.getOrDefault(intAt(r, 6), 1),
				dock == null ? 1 : Math.max(1, intAt(dock, 4))));
			String tool = toolName(kindTable);
			if (tool != null && !toolItems.containsKey(tool))
			{
				throw new IllegalStateException("charting tool \"" + tool + "\" is not in DB table " + TOOLS);
			}
			out.add(new ChartingTasks.Task(number, FIRST_VARBIT + number, kind, stringAt(set, 2), stringAt(sea, 2), level,
				rules.taskXp.apply(kindTable) / 10, stringAt(hazards.get(intAt(r, 6)), 1), stringAt(dock, 1), intAt(r, 4) == 1,
				new int[0], stringAt(r, 1), "", tool == null ? "" : tool, tool == null ? new int[0] : toolItems.get(tool), -1, ""));
		}
		out.sort(Comparator.comparingInt(ChartingTasks.Task::getNumber));
		return new ChartingTasks(sets, out);
	}

	/**
	 * The tasks with the places of a {@value #PLACES} file's lines ({@code task <tab> places <tab> source <tab> tip},
	 * places {@code x,y,plane} separated by spaces, {@code #} comment lines); a line that can't be read throws.
	 */
	static ChartingTasks withPlaces(ChartingTasks tasks, List<String> lines)
	{
		Map<Integer, String[]> byTask = new HashMap<>();
		for (int i = 0; i < lines.size(); i++)
		{
			String l = lines.get(i);
			if (l.isBlank() || l.startsWith("#") || l.startsWith("task\t"))
			{
				continue;
			}
			String[] c = l.split("\t", -1);
			if (c.length < 3)
			{
				throw new IllegalArgumentException(PLACES + " line " + (i + 1) + ": task, places and source needed: " + l);
			}
			byTask.put(Integer.parseInt(c[0].trim()), c);
			ChartingTasks.tiles(c[1], i); // checked here, so the line number is right
		}
		List<ChartingTasks.Task> out = new ArrayList<>();
		for (ChartingTasks.Task t : tasks.getTasks())
		{
			String[] c = byTask.get(t.getNumber());
			out.add(c == null ? t : ChartingTasksBuilder.withPlaces(t, ChartingTasks.tiles(c[1], 0), c.length > 3 ? c[3] : ""));
		}
		return new ChartingTasks(tasks.getSets(), out);
	}

	/** "358 tasks in 74 seas and bonus groups, 358 with a place". */
	public static String describe(ChartingTasks tasks)
	{
		int placed = 0;
		for (ChartingTasks.Task t : tasks.getTasks())
		{
			placed += t.hasPlace() ? 1 : 0;
		}
		return String.format(Locale.ROOT, "%d tasks in %d seas and bonus groups, %d with a place", tasks.getTasks().size(),
			ChartingTasksDiagnostics.bySet(tasks).size(), placed);
	}

	/** The plugin file's comment. */
	public static String comment(int cacheId)
	{
		return com.bestpath.core.data.PluginDataWriter.header("the sea charting tasks and sets")
			+ "\nFrom cache " + cacheId + " DB tables " + CORE
			+ " (tasks), " + SEAS + " (seas), " + HAZARDS + " (hazards), " + DOCKS + " (docks); varbit = " + FIRST_VARBIT
			+ " + task number; level by client script " + LEVEL_SCRIPT + "; experience from enums " + TASK_XP_ENUM + ", "
			+ SEA_XP_ENUM + ", " + OCEAN_XP_ENUM + ".\nPlaces and tips from " + PLACES + " (Quest Helper's charting list,"
			+ " BSD-2-Clause).";
	}

	private static int intAt(Object[][] row, int col)
	{
		if (row == null || col >= row.length || row[col] == null || row[col].length == 0)
		{
			return -1;
		}
		Object v = row[col][0];
		if (v instanceof Boolean)
		{
			return (Boolean) v ? 1 : 0;
		}
		return v instanceof Number ? ((Number) v).intValue() : -1;
	}

	private static String stringAt(Object[][] row, int col)
	{
		if (row == null || col >= row.length || row[col] == null || row[col].length == 0 || row[col][0] == null)
		{
			return "";
		}
		return String.valueOf(row[col][0]);
	}
}
