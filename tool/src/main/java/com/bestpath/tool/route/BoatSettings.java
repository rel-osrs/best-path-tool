package com.bestpath.tool.route;

import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatDiagnostics;
import com.bestpath.core.sea.BoatType;

/**
 * A boat as one settings line: "SKIFF,hull,sail,keel,helm,inoculation,brazier,FOCUS,dockId" (tiers 0-6; dockId may be
 * one of Boat's places that are no dock: -2 at sea, -1 unknown, -3 capsized, -4 lost, -5 bottled).
 */
public final class BoatSettings
{
	private BoatSettings()
	{
	}

	public static String encode(Boat b)
	{
		return b.getType().name() + "," + b.getHull() + "," + b.getSail() + "," + Math.max(0, b.getKeel()) + "," + b.getHelm()
			+ "," + (BoatDiagnostics.hasInoculation(b) ? 1 : 0) + "," + (BoatDiagnostics.hasBrazier(b) ? 1 : 0) + "," + b.getFocus().name() + "," + b.getDockId();
	}

	/** The boat, or null for an empty or broken line. */
	public static Boat decode(int slot, String s)
	{
		if (s == null || s.trim().isEmpty())
		{
			return null;
		}
		try
		{
			String[] p = s.trim().split(",");
			return new Boat(slot, BoatType.valueOf(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
				Integer.parseInt(p[4]), p[5].equals("1"), p[6].equals("1"), Boat.Focus.valueOf(p[7]), Integer.parseInt(p[8]));
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}
}
