package com.bestpath.clue;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Reads the game's STASH chart (interface 493, RuneLite {@code InterfaceID.HIDEY_HOLES}: the S.T.A.S.H chart in a
 * player-owned house's study, and Watson's noticeboard in Hosidius, which opens the same chart) the way its own client
 * scripts do:
 * <ul>
 * <li>The server runs script {@value #OPEN_SCRIPT} with three ints when the chart opens; that script draws every
 * tier's list (scripts 1477, 1478) and sets script {@value #UPDATE_SCRIPT} (same three ints) to run again when a
 * STASH varp (1365-1369, 2202) or inventory {@value #HH_INV} ({@code InventoryID.HH_INV}) changes. The plugin sees the
 * three ints as the script's arguments (public RuneLite event {@code ScriptPreFired}, as RuneLite's own LootManager
 * reads server-run scripts).</li>
 * <li>Script 1479 decides each unit's two ticks (built, filled) from its object id: enum {@value #TIER_ENUM} gives
 * (tier, bit) of the built varp (not built: no ticks); enum {@value #INV_ENUM} gives a slot of inventory 576 (filled
 * when that slot holds an item; six units also count as filled from one bit of the ints); enum {@value #BITS_ENUM}
 * gives (which int 1-3, bit); object 29057 (Yanille Watchtower) has two bits of the third int.</li>
 * </ul>
 * {@link #read} repeats script 1479 for every unit. Pure; the plugin hands in the game's values (client thread).
 */
public final class StashChart
{
	/*
	 * The chart's scripts (1475, 1476, 1479) and enums (1368, 1525, 1440) are numbers: RuneLite's API names no game
	 * scripts or enums for them (gameval has neither; net.runelite.api.ScriptID and EnumID don't list these).
	 */

	/** Run by the server when the chart opens: its three int arguments carry the filled bits. */
	public static final int OPEN_SCRIPT = 1475;
	/** The chart's own redraw when a STASH varp or inventory 576 changes (same three ints). */
	public static final int UPDATE_SCRIPT = 1476;
	/** STASH object -> (tier 1 easy ... 5 master, 6 beginner; bit of that tier's built varp). */
	public static final int TIER_ENUM = 1368;
	/** STASH object -> (slot of inventory 576, last slot): filled when the first slot holds an item. */
	public static final int INV_ENUM = 1525;
	/** STASH object -> (which of the three ints 1-3, bit): filled when that bit is set. */
	public static final int BITS_ENUM = 1440;
	/** The inventory with some units' contents. */
	public static final int HH_INV = InventoryID.HH_INV;

	/** Built varp per tier code 1-6 (script 1479; the same varps as {@link StashVars#BUILT_VARPS}). */
	static final int[] TIER_VARPS = {VarPlayerID.HH_CONSTRUCTED_EASY, VarPlayerID.HH_CONSTRUCTED_MEDIUM,
		VarPlayerID.HH_CONSTRUCTED_HARD, VarPlayerID.HH_CONSTRUCTED_ELITE, VarPlayerID.HH_CONSTRUCTED_MASTER,
		VarPlayerID.HH_CONSTRUCTED_BEGINNER};

	/** Units of enum 1525 that also count as filled from a bit of the ints (script 1479): object, int (0-2), bit. */
	private static final int[][] EXTRA_BITS = {
		{ObjectID.HH_ELITE_EXP9, 1, 31}, // On top of Trollheim Mountain
		{ObjectID.HH_ELITE_EXP10, 2, 0}, // Fountain of Heroes
		{ObjectID.HH_MASTER021, 2, 11}, // North-western corner of the Enchanted Valley
		{ObjectID.HH_ELITE_EXP1, 1, 27}, // Chapel in West Ardougne
		{ObjectID.HH_ELITE_EXP12, 2, 1}, // Half-way down Trollweiss Mountain
		{ObjectID.HH_MASTER001, 2, 2}, // South-east corner of Lava Dragon Isle
	};
	/** Top floor of the Yanille Watchtower: filled from bit 10 or 12 of the third int (script 1479's last case). */
	private static final int WATCHTOWER = ObjectID.HH_MASTER018;

	private StashChart()
	{
	}

	/**
	 * Every unit's "filled" as the chart shows it.
	 *
	 * @param args     the script's three ints (event arguments 1-3)
	 * @param tierEnum enum {@value #TIER_ENUM} (object -> packed coord)
	 * @param invEnum  enum {@value #INV_ENUM}
	 * @param bitsEnum enum {@value #BITS_ENUM}
	 * @param varp     the game's varps (the built varps)
	 * @param invSlot  the item id in a slot of inventory 576 (-1 empty), or null when the game never sent that
	 *                 inventory (then the units it decides are left out, unless one of their bits says filled)
	 * @return object -> filled (true / false); a unit that is not built is false; units it can't tell are left out
	 */
	public static Map<Integer, Boolean> read(int[] args, Map<Integer, Integer> tierEnum, Map<Integer, Integer> invEnum,
		Map<Integer, Integer> bitsEnum, IntUnaryOperator varp, IntUnaryOperator invSlot)
	{
		Map<Integer, Boolean> out = new TreeMap<>();
		if (args == null || args.length < 3)
		{
			return out;
		}
		for (int obj : tierEnum.keySet())
		{
			Boolean f = filled(obj, args, tierEnum, invEnum, bitsEnum, varp, invSlot);
			if (f != null)
			{
				out.put(obj, f);
			}
		}
		return out;
	}

	/** Script 1479 for one unit: true / false, or null when it can't tell. */
	static Boolean filled(int obj, int[] args, Map<Integer, Integer> tierEnum, Map<Integer, Integer> invEnum,
		Map<Integer, Integer> bitsEnum, IntUnaryOperator varp, IntUnaryOperator invSlot)
	{
		Integer tier = tierEnum.get(obj);
		if (tier == null)
		{
			return null;
		}
		int code = coordX(tier);
		if (code < 1 || code > TIER_VARPS.length)
		{
			return null;
		}
		if (!bit(varp.applyAsInt(TIER_VARPS[code - 1]), coordY(tier)))
		{
			return false; // not built: holds nothing
		}
		Integer slot = invEnum.get(obj);
		if (slot != null)
		{
			if (invSlot != null && invSlot.applyAsInt(coordX(slot)) != -1)
			{
				return true;
			}
			for (int[] e : EXTRA_BITS)
			{
				if (e[0] == obj && bit(args[e[1]], e[2]))
				{
					return true;
				}
			}
			return invSlot == null ? null : Boolean.FALSE;
		}
		Integer bits = bitsEnum.get(obj);
		if (bits != null)
		{
			int which = coordX(bits);
			if (which < 1 || which > 3)
			{
				return null;
			}
			return bit(args[which - 1], coordY(bits));
		}
		if (obj == WATCHTOWER)
		{
			return bit(args[2], 10) || bit(args[2], 12);
		}
		return null;
	}

	/** A packed coord's x (the script's {@code coordx}). */
	static int coordX(int coord)
	{
		return (coord >> 14) & 0x3FFF;
	}

	/** A packed coord's y (the script's {@code coordy}). */
	static int coordY(int coord)
	{
		return coord & 0x3FFF;
	}

	private static boolean bit(int value, int bit)
	{
		return bit >= 0 && bit < 32 && (value & (1 << bit)) != 0;
	}

	/** The script arguments as ints (event arguments 1-3; index 0 is the script id), or null if they aren't ints. */
	public static int[] args(Object[] eventArgs)
	{
		if (eventArgs == null || eventArgs.length < 4)
		{
			return null;
		}
		int[] out = new int[3];
		for (int i = 0; i < 3; i++)
		{
			if (!(eventArgs[i + 1] instanceof Integer))
			{
				return null;
			}
			out[i] = (Integer) eventArgs[i + 1];
		}
		return out;
	}
}
