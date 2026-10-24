package com.bestpath.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * What development code reads from {@link SlayerPlaces} that the plugin itself never needs.
 */
public final class SlayerPlacesDiagnostics
{
	private SlayerPlacesDiagnostics()
	{
	}

	/** Every task, in the file's order. */
	public static List<SlayerPlaces.Task> tasks(SlayerPlaces s)
	{
		return new ArrayList<>(s.tasksByRow.values());
	}

	/** {@link SlayerPlaces#LOOT_AREA}, {@link SlayerPlaces#LOOT_MASTER} or {@link SlayerPlaces#LOOT_PLACE}. */
	public static String kind(SlayerPlaces.Loot l)
	{
		return l.kind;
	}

	/** The start of a place's name, a master's number, or a place's key, as the kind says. */
	public static String key(SlayerPlaces.Loot l)
	{
		return l.key;
	}
}
