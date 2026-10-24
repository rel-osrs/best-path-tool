package com.bestpath.core.collision;

/**
 * What development code reads from {@link FreeLand} that the plugin itself never needs.
 */
public final class FreeLandDiagnostics
{
	private FreeLandDiagnostics()
	{
	}

	/** Number of free tiles (per plane). */
	public static long tileCount(FreeLand land)
	{
		long n = 0;
		for (int[] r : land.runs)
		{
			for (int i = 0; i < r.length; i += 2)
			{
				n += r[i + 1] - r[i] + 1;
			}
		}
		return n;
	}

	/** True when no tile is free. */
	public static boolean isEmpty(FreeLand land)
	{
		return land.rows.length == 0;
	}
}
