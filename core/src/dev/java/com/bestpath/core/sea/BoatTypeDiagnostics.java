package com.bestpath.core.sea;

/**
 * What development code reads from a {@link BoatType} that the plugin itself never needs (the Data Tool checks the
 * boat kinds against the game cache).
 */
public final class BoatTypeDiagnostics
{
	private BoatTypeDiagnostics()
	{
	}

	/** Length in tiles. Not used by routes: turning is not modelled. */
	public static int length(BoatType t)
	{
		return t.length;
	}

	/** Sailing level to own one. */
	public static int level(BoatType t)
	{
		return t.level;
	}
}
