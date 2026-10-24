package com.bestpath.player;

import java.util.List;
import net.runelite.api.gameval.VarbitID;

/**
 * The spirit tree patches a player can plant in, for "spirit tree &lt;place&gt;" requirements (detected, never
 * asked). Pure Java.
 *
 * <p>From the game cache: each patch is a multiloc whose state is a farming varbit; state 20 is object 8355 "Spirit
 * Tree" with the "Travel" option, the only state a player can travel from (44 = 8356, fully grown before its health
 * is checked; 8-19 growing, 21-31 diseased, 32-43 dead, others an empty patch). The farming varbits are shared by
 * patches in other places, so a value means this patch only while the player is in the patch's map region (the same
 * rule RuneLite's Time Tracking plugin uses).
 *
 * <p>RuneLite's own Time Tracking plugin remembers each patch per account under its config key
 * "&lt;its region&gt;.&lt;varbit&gt;" = "&lt;value&gt;:&lt;unix time&gt;" (group "timetracking"); a patch our plugin has not
 * seen yet is taken from there, so trees planted before Best Path was installed count too.
 */
public final class SpiritTreePatches
{
	/** The patch state with the "Travel" option (object 8355). */
	public static final int GROWN = 20;
	/** RuneLite's Time Tracking plugin's config group. */
	public static final String TIME_TRACKING_GROUP = "timetracking";

	/** One patch: the place's name in the requirement data, its region, its varbit, Time Tracking's key. */
	public static final class Patch
	{
		private final String place;
		private final int region;
		private final int varbit;
		private final String timeTrackingKey;

		Patch(String place, int region, int varbit, int timeTrackingRegion)
		{
			this.place = place;
			this.region = region;
			this.varbit = varbit;
			this.timeTrackingKey = timeTrackingRegion + "." + varbit;
		}

		/** The place as the requirement data names it ("spirit tree Port Sarim"). */
		public String getPlace()
		{
			return place;
		}

		public int getVarbit()
		{
			return varbit;
		}

		public String getTimeTrackingKey()
		{
			return timeTrackingKey;
		}
	}

	/**
	 * Patch objects and varbits from the cache: 8338 at 3059,3257 (varbit 4771), 8382 at 2612,3857 (4772), 8383 at
	 * 2801,3202 (4772), 27116 at 1692,3541 (7904), 33733 at 1252,3749 (4771). Time Tracking files the Hosidius patch
	 * under its Kourend region 6967.
	 */
	public static final List<Patch> PATCHES = List.of(
		new Patch("Port Sarim", 12082, VarbitID.FARMING_TRANSMIT_A, 12082),
		new Patch("Etceteria", 10300, VarbitID.FARMING_TRANSMIT_B, 10300),
		new Patch("Brimhaven", 11058, VarbitID.FARMING_TRANSMIT_B, 11058),
		new Patch("Hosidius", 6711, VarbitID.FARMING_TRANSMIT_F, 6967),
		new Patch("Farming Guild", 4922, VarbitID.FARMING_TRANSMIT_A, 4922));

	private SpiritTreePatches()
	{
	}

	/** True if this varbit value is a spirit tree the player can travel from. */
	public static boolean grown(int value)
	{
		return value == GROWN;
	}

	/** True if a varbit belongs to some spirit tree patch. */
	public static boolean isPatchVarbit(int varbit)
	{
		for (Patch p : PATCHES)
		{
			if (p.varbit == varbit)
			{
				return true;
			}
		}
		return false;
	}

	/** The patch in this map region, or null. */
	public static Patch inRegion(int region)
	{
		for (Patch p : PATCHES)
		{
			if (p.region == region)
			{
				return p;
			}
		}
		return null;
	}

	/** Time Tracking's stored "&lt;value&gt;:&lt;unix time&gt;": grown (true), not grown (false), or null if unreadable. */
	public static Boolean fromTimeTracking(String stored)
	{
		if (stored == null)
		{
			return null;
		}
		String[] parts = stored.trim().split(":");
		if (parts.length != 2)
		{
			return null;
		}
		try
		{
			return grown(Integer.parseInt(parts[0].trim()));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
