package com.bestpath.core.data;

/**
 * What development code reads from {@link WorldMaps} that the plugin itself never needs.
 */
public final class WorldMapsDiagnostics
{
	private WorldMapsDiagnostics()
	{
	}

	/** How many rectangles a map has (for reports). */
	public static int rectangleCount(WorldMaps maps, int map)
	{
		return maps.rects[map].length / WorldMaps.STRIDE;
	}
}
