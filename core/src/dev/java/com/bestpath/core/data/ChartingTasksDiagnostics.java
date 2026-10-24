package com.bestpath.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sea charting facts the plugin itself never reads, for the Data Tool and the tests. */
public final class ChartingTasksDiagnostics
{
	private ChartingTasksDiagnostics()
	{
	}

	/** The tasks by set (seas and bonus groups, in the sets' order; a set without tasks left out). */
	public static Map<String, List<ChartingTasks.Task>> bySet(ChartingTasks tasks)
	{
		Map<String, List<ChartingTasks.Task>> out = new LinkedHashMap<>();
		for (ChartingTasks.Set s : tasks.sets)
		{
			if (!ChartingTasks.OCEAN.equals(s.type))
			{
				out.put(s.name, new ArrayList<>());
			}
		}
		for (ChartingTasks.Task t : tasks.tasks)
		{
			out.computeIfAbsent(t.set, k -> new ArrayList<>()).add(t);
		}
		out.values().removeIf(List::isEmpty);
		for (Map.Entry<String, List<ChartingTasks.Task>> e : out.entrySet())
		{
			e.setValue(Collections.unmodifiableList(e.getValue()));
		}
		return Collections.unmodifiableMap(out);
	}

	/** The sea the task is in ("Bay of Sarim"); the set's name unless the set is a bonus group. */
	public static String sea(ChartingTasks.Task t)
	{
		return t.sea;
	}

	/** A dock the task needs ("Port Roberts"); empty = none. */
	public static String dock(ChartingTasks.Task t)
	{
		return t.dock;
	}

	/** A task of a bonus group ("Bonus Currents"): its sea is not its set. */
	public static boolean isBonus(ChartingTasks.Task t)
	{
		return !t.sea.equals(t.set);
	}

	/** Whether doing the task again gives experience again. */
	public static boolean repeat(ChartingTasks.Task t)
	{
		return t.repeat;
	}

	/** Where to go, packed tiles (the first is where the task starts); empty: not known. */
	public static int[] places(ChartingTasks.Task t)
	{
		return t.places.clone();
	}
}
