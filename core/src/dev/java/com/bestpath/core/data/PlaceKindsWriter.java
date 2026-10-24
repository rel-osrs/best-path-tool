package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;

/**
 * Writes the places file ({@value PlaceKinds#FILE}) that {@link PlaceKinds#parse} reads (used when the data is made).
 */
public final class PlaceKindsWriter
{
	private PlaceKindsWriter()
	{
	}

	/** The text form: {@code comment} (each line written as a # line), the header, one line per kind. */
	public static String write(PlaceKinds kinds, String comment)
	{
		StringBuilder sb = new StringBuilder();
		if (comment != null)
		{
			for (String c : comment.split("\n"))
			{
				sb.append("# ").append(c).append('\n');
			}
		}
		sb.append(PlaceKinds.HEADER).append('\n');
		for (PlaceKinds.Kind k : kinds.getKinds())
		{
			sb.append(line(k)).append('\n');
		}
		return sb.toString();
	}

	private static String line(PlaceKinds.Kind k)
	{
		StringBuilder sb = new StringBuilder();
		for (int t : k.tiles)
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Tiles.x(t)).append(',').append(Tiles.y(t)).append(',').append(Tiles.plane(t));
		}
		return k.name.replace('\t', ' ') + '\t' + k.sprite + '\t' + sb;
	}
}
