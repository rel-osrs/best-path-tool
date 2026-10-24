package com.bestpath.core.data;

/**
 * What development code reads from {@link WildernessZones} that the plugin itself never needs.
 */
public final class WildernessZonesDiagnostics
{
	private WildernessZonesDiagnostics()
	{
	}

	/** How many rectangles. */
	public static int rectangleCount(WildernessZones z)
	{
		return z.rects.length / WildernessZones.STRIDE;
	}
}
