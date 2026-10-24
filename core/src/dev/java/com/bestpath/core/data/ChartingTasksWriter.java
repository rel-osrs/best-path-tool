package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;

/**
 * Writes the sea charting file ({@value ChartingTasks#FILE}) that {@link ChartingTasks#parse} reads (used when the data
 * is made).
 */
public final class ChartingTasksWriter
{
	private ChartingTasksWriter()
	{
	}

	/** The text form: {@code comment} (each line written as a # line), the sets, the header, one line per task. */
	public static String write(ChartingTasks tasks, String comment)
	{
		StringBuilder sb = new StringBuilder();
		if (comment != null)
		{
			for (String c : comment.split("\n"))
			{
				sb.append("# ").append(c).append('\n');
			}
		}
		sb.append(ChartingTasks.SET_HEADER).append('\n');
		for (ChartingTasks.Set s : tasks.sets)
		{
			sb.append(line(s)).append('\n');
		}
		sb.append(ChartingTasks.HEADER).append('\n');
		for (ChartingTasks.Task t : tasks.tasks)
		{
			sb.append(line(t)).append('\n');
		}
		return sb.toString();
	}

	private static String line(ChartingTasks.Set s)
	{
		return "set\t" + clean(s.name) + '\t' + s.type + '\t' + clean(s.ocean) + '\t' + s.xp;
	}

	private static String line(ChartingTasks.Task t)
	{
		StringBuilder sb = new StringBuilder();
		for (int p : t.places)
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Tiles.x(p)).append(',').append(Tiles.y(p)).append(',').append(Tiles.plane(p));
		}
		return t.number + "\t" + t.varbit + '\t' + t.kind + '\t' + clean(t.set) + '\t' + (ChartingTasksDiagnostics.isBonus(t) ? clean(t.sea) : "") + '\t'
			+ t.level + '\t' + t.xp + '\t' + clean(t.hazard) + '\t' + clean(t.dock) + '\t' + (t.repeat ? "yes" : "") + '\t' + sb
			+ '\t' + clean(t.text) + '\t' + clean(t.tip) + '\t' + clean(t.tool) + '\t' + ids(t.toolItems) + '\t'
			+ (t.drink < 0 ? "" : t.drink) + '\t' + clean(t.drinkName);
	}

	private static String ids(int[] ids)
	{
		StringBuilder sb = new StringBuilder();
		for (int id : ids)
		{
			sb.append(sb.length() > 0 ? " " : "").append(id);
		}
		return sb.toString();
	}

	private static String clean(String s)
	{
		return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}
}
