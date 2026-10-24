package com.bestpath.core.collision;

/**
 * Writes {@link FreeLand} in its text form, which {@link FreeLand#parse} reads (used when the data is made).
 */
public final class FreeLandWriter
{
	private FreeLandWriter()
	{
	}

	/** The text form (see {@link FreeLand}), without comments. */
	public static String write(FreeLand land)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < land.rows.length; i++)
		{
			sb.append(land.rows[i]);
			for (int k = 0; k < land.runs[i].length; k += 2)
			{
				sb.append(' ').append(land.runs[i][k]).append('-').append(land.runs[i][k + 1]);
			}
			sb.append('\n');
		}
		return sb.toString();
	}
}
