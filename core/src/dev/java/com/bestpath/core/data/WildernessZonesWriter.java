package com.bestpath.core.data;

/**
 * Writes the Wilderness file ({@value WildernessZones#FILE}) that {@link WildernessZones#parse} reads (used when the
 * data is made).
 */
public final class WildernessZonesWriter
{
	private WildernessZonesWriter()
	{
	}

	/** The text form (see {@link WildernessZones}), with this header as a comment line. */
	public static String write(WildernessZones zones, String header)
	{
		int[] rects = zones.rects;
		StringBuilder sb = new StringBuilder();
		if (header != null && !header.isEmpty())
		{
			sb.append("# ").append(header.replace("\n", "\n# ")).append('\n');
		}
		sb.append("# x\ty\twidth\theight\tzone (1 = levels 1-20, 2 = 21-30, 3 = 31 and deeper)\n");
		for (int i = 0; i < rects.length; i += WildernessZones.STRIDE)
		{
			sb.append(rects[i]).append('\t').append(rects[i + 1]).append('\t').append(rects[i + 2]).append('\t')
				.append(rects[i + 3]).append('\t').append(rects[i + 4]).append('\n');
		}
		return sb.toString();
	}
}
