package com.bestpath.core.path;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * What development code reads from a {@link DoorMap} that the plugin itself never needs.
 */
public final class DoorMapDiagnostics
{
	private DoorMapDiagnostics()
	{
	}

	/** The cells (regionId &lt;&lt; 2 | plane) that have door bits. */
	public static Set<Integer> cellKeys(DoorMap doors)
	{
		if (doors.sparseTiles != null)
		{
			Set<Integer> s = new TreeSet<>();
			for (int t : doors.sparseTiles)
			{
				s.add(Tiles.cell(t));
			}
			return Collections.unmodifiableSet(s);
		}
		return Collections.unmodifiableSet(doors.cells.keySet());
	}
}
