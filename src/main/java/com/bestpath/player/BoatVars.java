package com.bestpath.player;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.VarbitID;

/**
 * The game's varbits for the player's boats. Names are RuneLite's gameval {@code VarbitID} constants; what each value
 * means was read from the game's client scripts and DB tables (see {@link com.bestpath.core.data.BoatTable}). Only
 * these are watched for boats.
 */
public final class BoatVars
{
	/** Boat slots 1-5 (scripts switch on 1-5 for every per-boat varbit). */
	public static final int SLOTS = 5;

	/** {@code SAILING_BOAT_n_OWNED}: 1 = the slot holds a boat (script 9011 counts slots with value 1). */
	public static final int OWNED = 0;
	/** {@code _TYPE}: the boat kind (table 166 column 0). */
	public static final int TYPE = 1;
	/** {@code _PORT}: the dock it is at (table 194 id), or a status (table 147: 253 Lost, 254 Capsized, 255 Bottled). */
	public static final int PORT = 2;
	/** {@code _HULL}, {@code _SAIL}, {@code _KEEL}, {@code _STEERING}: index into the kind's option list. */
	public static final int HULL = 3;
	public static final int SAIL = 4;
	public static final int KEEL = 5;
	public static final int STEERING = 6;
	/** {@code _TELEPORT_FOCUS}: 0 none, 1 Teleport focus, 2 or more Greater teleport focus (script 8997). */
	public static final int FOCUS = 7;
	/** {@code _BRAZIER}: 0 none, else value - 1 indexes the kind's brazier list. */
	public static final int BRAZIER = 8;
	/** {@code _NAME_1..3}: the boat's name words. */
	public static final int NAME_1 = 9;
	public static final int NAME_2 = 10;
	public static final int NAME_3 = 11;
	/** {@code _HOTSPOT_0..12}: the facility built at each hotspot (0 none, else value - 1 indexes its option list). */
	public static final int HOTSPOT_0 = 12;
	public static final int HOTSPOTS = 13;
	public static final int FIELDS = HOTSPOT_0 + HOTSPOTS;

	/** Varbit id per slot (index slot - 1) and field. */
	static final int[][] IDS = {
		{VarbitID.SAILING_BOAT_1_OWNED, VarbitID.SAILING_BOAT_1_TYPE, VarbitID.SAILING_BOAT_1_PORT, VarbitID.SAILING_BOAT_1_HULL,
			VarbitID.SAILING_BOAT_1_SAIL, VarbitID.SAILING_BOAT_1_KEEL, VarbitID.SAILING_BOAT_1_STEERING,
			VarbitID.SAILING_BOAT_1_TELEPORT_FOCUS, VarbitID.SAILING_BOAT_1_BRAZIER, VarbitID.SAILING_BOAT_1_NAME_1,
			VarbitID.SAILING_BOAT_1_NAME_2, VarbitID.SAILING_BOAT_1_NAME_3, VarbitID.SAILING_BOAT_1_HOTSPOT_0,
			VarbitID.SAILING_BOAT_1_HOTSPOT_1, VarbitID.SAILING_BOAT_1_HOTSPOT_2, VarbitID.SAILING_BOAT_1_HOTSPOT_3,
			VarbitID.SAILING_BOAT_1_HOTSPOT_4, VarbitID.SAILING_BOAT_1_HOTSPOT_5, VarbitID.SAILING_BOAT_1_HOTSPOT_6,
			VarbitID.SAILING_BOAT_1_HOTSPOT_7, VarbitID.SAILING_BOAT_1_HOTSPOT_8, VarbitID.SAILING_BOAT_1_HOTSPOT_9,
			VarbitID.SAILING_BOAT_1_HOTSPOT_10, VarbitID.SAILING_BOAT_1_HOTSPOT_11, VarbitID.SAILING_BOAT_1_HOTSPOT_12},
		{VarbitID.SAILING_BOAT_2_OWNED, VarbitID.SAILING_BOAT_2_TYPE, VarbitID.SAILING_BOAT_2_PORT, VarbitID.SAILING_BOAT_2_HULL,
			VarbitID.SAILING_BOAT_2_SAIL, VarbitID.SAILING_BOAT_2_KEEL, VarbitID.SAILING_BOAT_2_STEERING,
			VarbitID.SAILING_BOAT_2_TELEPORT_FOCUS, VarbitID.SAILING_BOAT_2_BRAZIER, VarbitID.SAILING_BOAT_2_NAME_1,
			VarbitID.SAILING_BOAT_2_NAME_2, VarbitID.SAILING_BOAT_2_NAME_3, VarbitID.SAILING_BOAT_2_HOTSPOT_0,
			VarbitID.SAILING_BOAT_2_HOTSPOT_1, VarbitID.SAILING_BOAT_2_HOTSPOT_2, VarbitID.SAILING_BOAT_2_HOTSPOT_3,
			VarbitID.SAILING_BOAT_2_HOTSPOT_4, VarbitID.SAILING_BOAT_2_HOTSPOT_5, VarbitID.SAILING_BOAT_2_HOTSPOT_6,
			VarbitID.SAILING_BOAT_2_HOTSPOT_7, VarbitID.SAILING_BOAT_2_HOTSPOT_8, VarbitID.SAILING_BOAT_2_HOTSPOT_9,
			VarbitID.SAILING_BOAT_2_HOTSPOT_10, VarbitID.SAILING_BOAT_2_HOTSPOT_11, VarbitID.SAILING_BOAT_2_HOTSPOT_12},
		{VarbitID.SAILING_BOAT_3_OWNED, VarbitID.SAILING_BOAT_3_TYPE, VarbitID.SAILING_BOAT_3_PORT, VarbitID.SAILING_BOAT_3_HULL,
			VarbitID.SAILING_BOAT_3_SAIL, VarbitID.SAILING_BOAT_3_KEEL, VarbitID.SAILING_BOAT_3_STEERING,
			VarbitID.SAILING_BOAT_3_TELEPORT_FOCUS, VarbitID.SAILING_BOAT_3_BRAZIER, VarbitID.SAILING_BOAT_3_NAME_1,
			VarbitID.SAILING_BOAT_3_NAME_2, VarbitID.SAILING_BOAT_3_NAME_3, VarbitID.SAILING_BOAT_3_HOTSPOT_0,
			VarbitID.SAILING_BOAT_3_HOTSPOT_1, VarbitID.SAILING_BOAT_3_HOTSPOT_2, VarbitID.SAILING_BOAT_3_HOTSPOT_3,
			VarbitID.SAILING_BOAT_3_HOTSPOT_4, VarbitID.SAILING_BOAT_3_HOTSPOT_5, VarbitID.SAILING_BOAT_3_HOTSPOT_6,
			VarbitID.SAILING_BOAT_3_HOTSPOT_7, VarbitID.SAILING_BOAT_3_HOTSPOT_8, VarbitID.SAILING_BOAT_3_HOTSPOT_9,
			VarbitID.SAILING_BOAT_3_HOTSPOT_10, VarbitID.SAILING_BOAT_3_HOTSPOT_11, VarbitID.SAILING_BOAT_3_HOTSPOT_12},
		{VarbitID.SAILING_BOAT_4_OWNED, VarbitID.SAILING_BOAT_4_TYPE, VarbitID.SAILING_BOAT_4_PORT, VarbitID.SAILING_BOAT_4_HULL,
			VarbitID.SAILING_BOAT_4_SAIL, VarbitID.SAILING_BOAT_4_KEEL, VarbitID.SAILING_BOAT_4_STEERING,
			VarbitID.SAILING_BOAT_4_TELEPORT_FOCUS, VarbitID.SAILING_BOAT_4_BRAZIER, VarbitID.SAILING_BOAT_4_NAME_1,
			VarbitID.SAILING_BOAT_4_NAME_2, VarbitID.SAILING_BOAT_4_NAME_3, VarbitID.SAILING_BOAT_4_HOTSPOT_0,
			VarbitID.SAILING_BOAT_4_HOTSPOT_1, VarbitID.SAILING_BOAT_4_HOTSPOT_2, VarbitID.SAILING_BOAT_4_HOTSPOT_3,
			VarbitID.SAILING_BOAT_4_HOTSPOT_4, VarbitID.SAILING_BOAT_4_HOTSPOT_5, VarbitID.SAILING_BOAT_4_HOTSPOT_6,
			VarbitID.SAILING_BOAT_4_HOTSPOT_7, VarbitID.SAILING_BOAT_4_HOTSPOT_8, VarbitID.SAILING_BOAT_4_HOTSPOT_9,
			VarbitID.SAILING_BOAT_4_HOTSPOT_10, VarbitID.SAILING_BOAT_4_HOTSPOT_11, VarbitID.SAILING_BOAT_4_HOTSPOT_12},
		{VarbitID.SAILING_BOAT_5_OWNED, VarbitID.SAILING_BOAT_5_TYPE, VarbitID.SAILING_BOAT_5_PORT, VarbitID.SAILING_BOAT_5_HULL,
			VarbitID.SAILING_BOAT_5_SAIL, VarbitID.SAILING_BOAT_5_KEEL, VarbitID.SAILING_BOAT_5_STEERING,
			VarbitID.SAILING_BOAT_5_TELEPORT_FOCUS, VarbitID.SAILING_BOAT_5_BRAZIER, VarbitID.SAILING_BOAT_5_NAME_1,
			VarbitID.SAILING_BOAT_5_NAME_2, VarbitID.SAILING_BOAT_5_NAME_3, VarbitID.SAILING_BOAT_5_HOTSPOT_0,
			VarbitID.SAILING_BOAT_5_HOTSPOT_1, VarbitID.SAILING_BOAT_5_HOTSPOT_2, VarbitID.SAILING_BOAT_5_HOTSPOT_3,
			VarbitID.SAILING_BOAT_5_HOTSPOT_4, VarbitID.SAILING_BOAT_5_HOTSPOT_5, VarbitID.SAILING_BOAT_5_HOTSPOT_6,
			VarbitID.SAILING_BOAT_5_HOTSPOT_7, VarbitID.SAILING_BOAT_5_HOTSPOT_8, VarbitID.SAILING_BOAT_5_HOTSPOT_9,
			VarbitID.SAILING_BOAT_5_HOTSPOT_10, VarbitID.SAILING_BOAT_5_HOTSPOT_11, VarbitID.SAILING_BOAT_5_HOTSPOT_12}};

	/**
	 * The boat the player stands on: {@code SAILING_BOARDED_BOAT_TYPE} (a table 166 kind id, scripts 8757 / 9087) and
	 * its name words {@code SAILING_BOARDED_BOAT_NAME_1..3} (script 9087 names the boarded boat with them as 9086 names
	 * a slot's). Matched to a slot by kind and name.
	 */
	static final int BOARDED_TYPE = VarbitID.SAILING_BOARDED_BOAT_TYPE;
	static final int[] BOARDED_NAME = {VarbitID.SAILING_BOARDED_BOAT_NAME_1, VarbitID.SAILING_BOARDED_BOAT_NAME_2,
		VarbitID.SAILING_BOARDED_BOAT_NAME_3};

	/**
	 * Every varbit above, plus {@code SAILING_BOARDED_BOAT} (only as a "something changed" signal; its values are not
	 * used). Tests read it.
	 */
	static final Set<Integer> ALL;

	static
	{
		Set<Integer> s = new HashSet<>();
		for (int[] slot : IDS)
		{
			for (int id : slot)
			{
				s.add(id);
			}
		}
		s.add(BOARDED_TYPE);
		for (int id : BOARDED_NAME)
		{
			s.add(id);
		}
		s.add(VarbitID.SAILING_BOARDED_BOAT);
		ALL = Collections.unmodifiableSet(s);
	}

	private BoatVars()
	{
	}

	/** The varbit of a slot's field (slot 1-5, field one of the constants above). */
	public static int id(int slot, int field)
	{
		return IDS[slot - 1][field];
	}

	/** {@code SAILING_BOARDED_BOAT_TYPE}. */
	public static int boardedType()
	{
		return BOARDED_TYPE;
	}

	/** {@code SAILING_BOARDED_BOAT_NAME_1..3} (part 0-2). */
	public static int boardedName(int part)
	{
		return BOARDED_NAME[part];
	}

	/** True if this varbit is one of the boat varbits (so the boats are read again). */
	public static boolean isBoatVarbit(int varbitId)
	{
		return ALL.contains(varbitId);
	}
}
