package com.bestpath.tool.slayer;

import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The game's Slayer DB tables in the cache (checked in cache #2735). The cache stores only
 * numbers; the table and column names come from RuneLite's public gameval names ({@code DBTableID.Slayer*}):
 * <ul>
 * <li>113 {@code SlayerTask}: column 0 {@code ID} is the value of varp 395 ({@code VarPlayerID.SLAYER_TARGET}), column 10
 * {@code NAME_UPPERCASE} the name ("Kalphites"); boss tasks have id -1;</li>
 * <li>114 {@code SlayerMasterTask}: column 0 the master (varbit 4067 {@code VarbitID.SLAYER_MASTER}; the cache has no
 * names for them), 1 the task row, 3 / 4 the least and most given, 5 {@code AREAS}: the areas Konar may name (rows of
 * 115);</li>
 * <li>115 {@code SlayerArea}: column 0 {@code AREA_ID} is the value of varp 2096 ({@code VarPlayerID.SLAYER_AREA}),
 * column 3 {@code AREA_NAME_IN_HELPER} its name ("Kalphite Lair");</li>
 * <li>116 {@code SlayerTaskSublist}: a boss task (varp 395 = 98) names the boss in varbit 4723
 * ({@code VarbitID.SLAYER_TARGET_BOSSID}): the row with column 1 = that value gives the task row in column 4.</li>
 * </ul>
 * Immutable after {@link #read}.
 */
public final class SlayerTables
{
	public static final int TASK_TABLE = 113;
	public static final int MASTER_TASK_TABLE = 114;
	public static final int AREA_TABLE = 115;
	public static final int SUBLIST_TABLE = 116;
	/** Varp 395's value for "a boss" (the boss is in varbit 4723). */
	public static final int BOSS_TASK = 98;

	/** One row of table 113. */
	public static final class Task
	{
		public final int row;
		/** Varp 395's value for this task; -1 for a boss (named through table 116). */
		public final int id;
		public final String name;

		Task(int row, int id, String name)
		{
			this.row = row;
			this.id = id;
			this.name = name;
		}
	}

	/** One row of table 114: a master gives a task. */
	public static final class Assignment
	{
		public final int master;
		public final int task;
		public final int min;
		public final int max;
		/** Rows of table 115 (Konar's areas); empty for other masters. */
		public final List<Integer> areas;

		Assignment(int master, int task, int min, int max, List<Integer> areas)
		{
			this.master = master;
			this.task = task;
			this.min = min;
			this.max = max;
			this.areas = areas;
		}
	}

	/** One row of table 115. */
	public static final class Area
	{
		public final int row;
		/** Varp 2096's value for this area. */
		public final int id;
		public final String name;

		Area(int row, int id, String name)
		{
			this.row = row;
			this.id = id;
			this.name = name;
		}
	}

	/** Task row -> task, in row order. */
	public final Map<Integer, Task> tasks;
	public final List<Assignment> assignments;
	/** Area row -> area, in row order. */
	public final Map<Integer, Area> areas;
	/** Varbit 4723's value -> task row. */
	public final Map<Integer, Integer> bosses;

	private SlayerTables(Map<Integer, Task> tasks, List<Assignment> assignments, Map<Integer, Area> areas, Map<Integer, Integer> bosses)
	{
		this.tasks = Collections.unmodifiableMap(tasks);
		this.assignments = Collections.unmodifiableList(assignments);
		this.areas = Collections.unmodifiableMap(areas);
		this.bosses = Collections.unmodifiableMap(bosses);
	}

	public static SlayerTables read(LoadedCache cache) throws IOException
	{
		Map<Integer, Task> tasks = new LinkedHashMap<>();
		for (Map.Entry<Integer, Object[][]> e : cache.dbRowsById(TASK_TABLE).entrySet())
		{
			String name = string(e.getValue(), 10);
			if (name != null && !name.isEmpty())
			{
				tasks.put(e.getKey(), new Task(e.getKey(), integer(e.getValue(), 0, -1), name));
			}
		}
		List<Assignment> assignments = new ArrayList<>();
		for (Object[][] r : cache.dbRowsById(MASTER_TASK_TABLE).values())
		{
			List<Integer> areas = new ArrayList<>();
			if (r.length > 5 && r[5] != null)
			{
				for (Object o : r[5])
				{
					areas.add(((Number) o).intValue());
				}
			}
			assignments.add(new Assignment(integer(r, 0, -1), integer(r, 1, -1), integer(r, 3, -1), integer(r, 4, -1),
				Collections.unmodifiableList(areas)));
		}
		Map<Integer, Area> areas = new LinkedHashMap<>();
		for (Map.Entry<Integer, Object[][]> e : cache.dbRowsById(AREA_TABLE).entrySet())
		{
			areas.put(e.getKey(), new Area(e.getKey(), integer(e.getValue(), 0, -1), string(e.getValue(), 3)));
		}
		Map<Integer, Integer> bosses = new TreeMap<>();
		for (Object[][] r : cache.dbRowsById(SUBLIST_TABLE).values())
		{
			if (integer(r, 0, -1) == 1)
			{
				bosses.put(integer(r, 1, -1), integer(r, 4, -1));
			}
		}
		return new SlayerTables(tasks, assignments, areas, bosses);
	}

	/** A table made in code (tests). */
	public static SlayerTables of(Map<Integer, Task> tasks, List<Assignment> assignments, Map<Integer, Area> areas,
		Map<Integer, Integer> bosses)
	{
		return new SlayerTables(tasks, assignments, areas, bosses);
	}

	public static Task task(int row, int id, String name)
	{
		return new Task(row, id, name);
	}

	public static Area area(int row, int id, String name)
	{
		return new Area(row, id, name);
	}

	public static Assignment assignment(int master, int task, int min, int max, List<Integer> areas)
	{
		return new Assignment(master, task, min, max, areas);
	}

	/** The master that names areas (Konar: the only one whose assignments carry areas), or -1. */
	public int areaMaster()
	{
		for (Assignment a : assignments)
		{
			if (!a.areas.isEmpty())
			{
				return a.master;
			}
		}
		return -1;
	}

	/** The areas Konar may name for this task row (rows of table 115). */
	public List<Integer> areasFor(int taskRow)
	{
		for (Assignment a : assignments)
		{
			if (a.task == taskRow && !a.areas.isEmpty())
			{
				return a.areas;
			}
		}
		return List.of();
	}

	private static String string(Object[][] r, int column)
	{
		return r.length > column && r[column] != null && r[column].length > 0 ? String.valueOf(r[column][0]).trim() : null;
	}

	private static int integer(Object[][] r, int column, int none)
	{
		return r.length > column && r[column] != null && r[column].length > 0 && r[column][0] instanceof Number
			? ((Number) r[column][0]).intValue() : none;
	}
}
