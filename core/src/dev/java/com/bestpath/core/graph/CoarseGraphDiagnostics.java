package com.bestpath.core.graph;

/**
 * What development code reads from a {@link CoarseGraph} that the plugin itself never needs: sizes and counts for
 * reports.
 */
public final class CoarseGraphDiagnostics
{
	private CoarseGraphDiagnostics()
	{
	}

	/** Bytes held by the packed labels. */
	public static long labelBytes(CoarseGraph g)
	{
		long n = 0;
		for (long[] p : g.packed)
		{
			n += p == null ? 0 : 8L * p.length;
		}
		return n;
	}

	public static int cellCount(CoarseGraph g)
	{
		return g.cellKeys.length;
	}

	/** Number of distinct component pairs joined by at least one entrance. */
	public static int linkCount(CoarseGraph g)
	{
		return g.linkCount;
	}
}
