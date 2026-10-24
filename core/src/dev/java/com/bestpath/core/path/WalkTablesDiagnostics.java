package com.bestpath.core.path;

import java.util.Locale;

/**
 * What development code reads from {@link WalkTables} that the plugin itself never needs: sizes and counts for the
 * data export's report.
 */
public final class WalkTablesDiagnostics
{
	private WalkTablesDiagnostics()
	{
	}

	/** Bytes the packed tables take in memory (arrays and their headers). */
	public static long bytes(WalkTables t)
	{
		return 16L * 21 + 4L * (t.mKeys.length + t.mK.length + t.mOff.length + t.sKeys.length + t.sOff.length + t.bOff.length
			+ t.eKeys.length + t.eTileOff.length + t.eTiles.length + t.eRowOff.length + t.esComp.length + t.esOff.length)
			+ t.mSym.length + 8L * (t.bKeys.length + t.esKeys.length)
			+ 2L * (t.mPool.length + t.sPool.length + t.bPool.length + t.eRows.length + t.esPool.length);
	}

	/** "components / end tiles" (counts), for the data export's summary. */
	public static String endCounts(WalkTables t)
	{
		return t.mKeys.length + " / " + t.eTiles.length;
	}

	/** Counts for the data export's report: components, end tiles, distance numbers, memory. */
	public static String describe(WalkTables t)
	{
		long numbers = t.mPool.length + t.sPool.length + t.bPool.length + t.eRows.length + t.esPool.length;
		return String.format(Locale.ROOT, "%,d sub-regions, %,d end tiles, %,d distances, %.2f MB in memory", t.mKeys.length,
			t.eTiles.length, numbers, bytes(t) / 1e6);
	}
}
