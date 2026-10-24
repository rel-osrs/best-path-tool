package com.bestpath.core.data;

/**
 * Writes the world map file ({@value WorldMaps#FILE}) that {@link WorldMaps#parse} reads (used when the data is made).
 */
public final class WorldMapsWriter
{
	private WorldMapsWriter()
	{
	}

	/**
	 * The text form: comment lines, then per map a "map" line (id, name) and its "area" lines (game x, y, width, height,
	 * shift x, y, lowest plane, highest plane).
	 */
	public static String write(WorldMaps maps, String header)
	{
		StringBuilder sb = new StringBuilder();
		for (String h : header.split("\n"))
		{
			sb.append("# ").append(h).append('\n');
		}
		for (int m = 0; m < maps.names.length; m++)
		{
			sb.append("map\t").append(m).append('\t').append(TransportLine.clean(maps.names[m])).append('\n');
			int[] r = maps.rects[m];
			for (int i = 0; i < r.length; i += WorldMaps.STRIDE)
			{
				sb.append("area");
				for (int k = 0; k < WorldMaps.STRIDE; k++)
				{
					sb.append('\t').append(r[i + k]);
				}
				sb.append('\n');
			}
		}
		return sb.toString();
	}
}
