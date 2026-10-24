package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;
import java.util.List;

/**
 * Writes the Slayer file ({@value SlayerPlaces#FILE}) that {@link SlayerPlaces#parse} reads (used when the data is
 * made).
 */
public final class SlayerPlacesWriter
{
	private SlayerPlacesWriter()
	{
	}

	/** The text form, with these comment lines first. */
	public static String write(SlayerPlaces s, String comment)
	{
		StringBuilder sb = new StringBuilder();
		for (String c : comment.split("\n"))
		{
			sb.append("# ").append(c).append('\n');
		}
		s.masters.forEach((k, v) -> sb.append("master\t").append(k).append('\t').append(clean(v)).append('\n'));
		for (SlayerPlaces.Task t : s.tasksByRow.values())
		{
			sb.append("task\t").append(t.row).append('\t').append(t.id).append('\t').append(clean(t.name)).append('\n');
		}
		s.bosses.forEach((k, v) -> sb.append("boss\t").append(k).append('\t').append(v).append('\n'));
		s.areas.forEach((k, v) -> sb.append("area\t").append(k).append('\t').append(clean(v)).append('\n'));
		for (List<SlayerPlaces.Place> list : s.places.values())
		{
			for (SlayerPlaces.Place p : list)
			{
				sb.append(line(p)).append('\n');
			}
		}
		s.icons.forEach((k, v) -> sb.append("icon\t").append(k).append('\t').append(v).append('\n'));
		for (SlayerPlaces.Loot l : s.loot)
		{
			sb.append(line(l)).append('\n');
		}
		return sb.toString();
	}

	private static String line(SlayerPlaces.Place p)
	{
		StringBuilder sb = new StringBuilder();
		for (int t : p.points)
		{
			sb.append(sb.length() > 0 ? " " : "").append(Tiles.x(t)).append(',').append(Tiles.y(t)).append(',').append(Tiles.plane(t));
		}
		return "place\t" + p.task + '\t' + clean(p.name) + '\t' + p.area + '\t' + clean(p.amount) + '\t' + clean(p.multi) + '\t'
			+ clean(p.cannon) + '\t' + clean(p.safespot) + '\t' + sb + '\t' + (p.onMap ? "yes" : "no") + '\t' + dash(p.monsters) + '\t'
			+ dash(p.source) + '\t' + dash(p.notes);
	}

	private static String line(SlayerPlaces.Loot l)
	{
		StringBuilder sb = new StringBuilder();
		for (int i : l.items)
		{
			sb.append(sb.length() == 0 ? "" : " ").append(i);
		}
		return "loot\t" + l.kind + '\t' + clean(l.key) + '\t' + sb + '\t' + dash(l.note);
	}

	private static String clean(String s)
	{
		return s == null ? "" : s.replaceAll("[\\t\\r\\n]+", " ").trim();
	}

	private static String dash(String s)
	{
		return s == null || s.isEmpty() ? "-" : clean(s);
	}
}
