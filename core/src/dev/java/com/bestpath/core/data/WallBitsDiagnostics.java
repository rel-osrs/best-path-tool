package com.bestpath.core.data;

/**
 * What development code reads from {@link WallBits} that the plugin itself never needs.
 */
public final class WallBitsDiagnostics
{
	private WallBitsDiagnostics()
	{
	}

	/** How many cells have walls. */
	public static int cellCount(WallBits w)
	{
		return w.keys.length;
	}
}
