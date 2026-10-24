package com.bestpath.core.sea;

/**
 * What development code reads from a {@link Boat} that the plugin itself never needs.
 */
public final class BoatDiagnostics
{
	private BoatDiagnostics()
	{
	}

	/** The boat has an inoculation station (it may enter fetid water). */
	public static boolean hasInoculation(Boat b)
	{
		return b.inoculation;
	}

	/** The boat has an Eternal brazier (it may enter icy seas). */
	public static boolean hasBrazier(Boat b)
	{
		return b.brazier;
	}
}
