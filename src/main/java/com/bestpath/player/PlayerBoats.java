package com.bestpath.player;

import com.bestpath.core.sea.Boat;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The player's boats as read from the game: the boats routes may use (core {@link Boat}s;
 * the one the player stands on marked {@link Boat#AT_START}) and the Settings tab's rows. Immutable.
 */
public final class PlayerBoats
{
	/** Why there may be no rows. */
	public enum Status
	{
		/** Not logged in (boats are read once logged in). */
		NOT_LOGGED_IN,
		/** The map data has no boat table (or has not loaded yet): boats can't be read. */
		NO_DATA,
		/** Read from the game. */
		READ
	}

	public static final PlayerBoats NOT_LOGGED_IN = new PlayerBoats(Status.NOT_LOGGED_IN, List.of(), List.of(), null);
	public static final PlayerBoats NO_DATA = new PlayerBoats(Status.NO_DATA, List.of(), List.of(), null);

	private final Status status;
	private final List<Boat> boats;
	private final List<BoatInfo> rows;
	private final String aboardNote;

	/**
	 * @param boats      the boats routes can use (Settings choices not applied yet)
	 * @param aboardNote when on a boat routes can't sail from (not the player's, or not known which): why; else null
	 */
	public PlayerBoats(Status status, List<Boat> boats, List<BoatInfo> rows, String aboardNote)
	{
		this.status = status;
		this.boats = Collections.unmodifiableList(List.copyOf(boats));
		this.rows = Collections.unmodifiableList(List.copyOf(rows));
		this.aboardNote = aboardNote;
	}

	public Status getStatus()
	{
		return status;
	}

	/** The boats routes can use; the one the player is on (if known) has {@link Boat#AT_START}. */
	public List<Boat> getBoats()
	{
		return boats;
	}

	/** One row per boat the player owns (slot order). */
	public List<BoatInfo> getRows()
	{
		return rows;
	}

	/** Why routes can't sail from the boat the player is on, or null. */
	public String getAboardNote()
	{
		return aboardNote;
	}

	/** Same Settings tab content (status, rows, note). */
	public boolean sameView(PlayerBoats o)
	{
		return o != null && status == o.status && rows.equals(o.rows) && Objects.equals(aboardNote, o.aboardNote);
	}
}
