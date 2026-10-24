package com.bestpath.core.sea;

/**
 * What development code works out about the sea graph ({@link SeaGraph}) that the plugin itself never needs.
 */
public final class SeaGraphDiagnostics
{
	private SeaGraphDiagnostics()
	{
	}

	/** Degrees clockwise from north of heading d (north 0, north-north-east 22.5, east 90, ...). */
	public static double compassDegrees(int d)
	{
		return ((4 - d) % SeaGraph.HEADINGS + SeaGraph.HEADINGS) % SeaGraph.HEADINGS * 22.5;
	}
}
