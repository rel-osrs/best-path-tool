package com.bestpath.player;

/**
 * The raw boat values read from the game (client thread) before {@link SnapshotMapper#boats} turns them into boats.
 * Plain data, filled by the tracker and mapped at once.
 */
public final class BoatReading
{
	/** Per slot (index slot - 1), the {@link BoatVars} fields' varbit values. */
	public final int[][] slots = new int[BoatVars.SLOTS][BoatVars.FIELDS];
	/** The player stands on a boat (a world view other than the top-level one). */
	public boolean aboard;
	/** That boat is the player's own (RuneLite's {@code WorldEntity.getOwnerType() == OWNER_TYPE_SELF_PLAYER}). */
	public boolean ownBoat;
	/** {@code SAILING_BOARDED_BOAT_TYPE} and {@code SAILING_BOARDED_BOAT_NAME_1..3}. */
	public int boardedType;
	public final int[] boardedName = new int[3];
}
