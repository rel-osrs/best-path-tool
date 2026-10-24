package com.bestpath.clue;

import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Which game vars tell a clue step's STASH unit state ({@link StashState}), so the plugin reads them again only after
 * one of them changed. Every id is a RuneLite gameval name; what they mean comes from the game's client scripts:
 * <ul>
 * <li>the step's own built varbit ({@code VarbitID.HH_CONSTRUCTED_*}, from the clue data) and the six varps that hold
 * them ({@code VarPlayerID.HH_CONSTRUCTED_EASY} ... {@code _BEGINNER});</li>
 * <li>{@code VarbitID.CLUEHELPER_HIDEYHOLE_FILLED} (in varp 2855, {@code VarPlayerID.SETTINGS_TRACKING}, bit 19) and
 * the clue the game's clue helper tracks, {@code VarPlayerID.CLUEHELPER_INFOBOX_CLUE}.</li>
 * </ul>
 */
public final class StashVars
{
	/** The varps holding every STASH unit's built bit, one per tier (script 6472's order: easy ... master, beginner). */
	static final int[] BUILT_VARPS = {VarPlayerID.HH_CONSTRUCTED_EASY, VarPlayerID.HH_CONSTRUCTED_MEDIUM,
		VarPlayerID.HH_CONSTRUCTED_HARD, VarPlayerID.HH_CONSTRUCTED_ELITE, VarPlayerID.HH_CONSTRUCTED_MASTER,
		VarPlayerID.HH_CONSTRUCTED_BEGINNER};
	/** The varp holding {@link #FILLED} (varbit 14164 is bit 19 of varp 2855). */
	static final int FILLED_VARP = VarPlayerID.SETTINGS_TRACKING;
	/** "The clue helper's STASH unit is filled" (one bit for the clue it tracks). */
	public static final int FILLED = VarbitID.CLUEHELPER_HIDEYHOLE_FILLED;
	/** The clue the game's clue helper tracks: its cache row. */
	public static final int HELPER_CLUE = VarPlayerID.CLUEHELPER_INFOBOX_CLUE;

	private StashVars()
	{
	}

	/**
	 * True if this var change may change a STASH unit's state as shown.
	 *
	 * @param varbitId    the changed varbit, or -1 (a varp change)
	 * @param varpId      the changed varp
	 * @param stashVarbit the carried clue step's STASH varbit, or -1 (none)
	 */
	public static boolean matters(int varbitId, int varpId, int stashVarbit)
	{
		if (varbitId != -1 && (varbitId == FILLED || (stashVarbit != -1 && varbitId == stashVarbit)))
		{
			return true;
		}
		if (varpId == FILLED_VARP || varpId == HELPER_CLUE)
		{
			return true;
		}
		for (int v : BUILT_VARPS)
		{
			if (varpId == v)
			{
				return true;
			}
		}
		return false;
	}
}
