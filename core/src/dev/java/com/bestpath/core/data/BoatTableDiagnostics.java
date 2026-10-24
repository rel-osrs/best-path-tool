package com.bestpath.core.data;

import java.util.Map;

/**
 * What development code reads from a {@link BoatTable} that the plugin itself never needs (the Data Tool's summary
 * of it).
 */
public final class BoatTableDiagnostics
{
	private BoatTableDiagnostics()
	{
	}

	/** Every boat kind, by its {@code _TYPE} value. */
	public static Map<Integer, BoatTable.Kind> kinds(BoatTable t)
	{
		return t.kinds;
	}

	/** The {@code _PORT} values that are no dock, with the game's label. */
	public static Map<Integer, String> ports(BoatTable t)
	{
		return t.ports;
	}

	/** Every dock of the table: id to name. */
	public static Map<Integer, String> docks(BoatTable t)
	{
		return t.docks;
	}

	/** The kind's id (its row of table 166). */
	public static int id(BoatTable.Kind k)
	{
		return k.id;
	}
}
