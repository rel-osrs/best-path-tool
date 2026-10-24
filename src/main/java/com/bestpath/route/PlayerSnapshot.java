package com.bestpath.route;

import com.bestpath.core.req.PlayerState;
import com.bestpath.core.sea.Boat;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What the route search needs to know about the player, copied on the client
 * thread at the moment a route is asked for. Immutable, so the worker thread
 * can read it while the game keeps changing.
 *
 * <p>Boats (Sailing): the player's boats as core {@link Boat}s (kind, parts,
 * the dock each is moored at). The boat the player stands on is marked
 * {@link Boat#AT_START}, and then x, y, plane are the boat's tile at sea: the
 * route starts from the boat, where it is.
 */
public final class PlayerSnapshot
{
	private final int x;
	private final int y;
	private final int plane;
	private final PlayerState state;
	private final List<Boat> boats;
	private final Map<String, String> nexusKeys;

	public PlayerSnapshot(int x, int y, int plane, PlayerState state, List<Boat> boats)
	{
		this(x, y, plane, state, boats, Map.of());
	}

	private PlayerSnapshot(int x, int y, int plane, PlayerState state, List<Boat> boats, Map<String, String> nexusKeys)
	{
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.state = state;
		this.boats = Collections.unmodifiableList(List.copyOf(boats));
		this.nexusKeys = nexusKeys == null ? Map.of() : Map.copyOf(nexusKeys);
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getPlane()
	{
		return plane;
	}

	/** Levels, items, members... (core requirements model; itself immutable). */
	public PlayerState getState()
	{
		return state;
	}

	/** The player's boats (Sailing); empty when none are known. */
	public List<Boat> getBoats()
	{
		return boats;
	}

	/**
	 * The keyboard key of each line of the player's house portal nexus list, by the line's text ("Senntisten" ->
	 * "M", for the step text "Portal Nexus -> Senntisten (M)"); empty when not known (no nexus, not scanned).
	 */
	public Map<String, String> getNexusKeys()
	{
		return nexusKeys;
	}

	/** This player standing on another tile (the public API's {@code query} from a {@code start}). */
	public PlayerSnapshot at(int atX, int atY, int atPlane)
	{
		return new PlayerSnapshot(atX, atY, atPlane, state, boats, nexusKeys);
	}

	/** This snapshot with another player state (e.g. with the Settings tab's coin limit). */
	public PlayerSnapshot withState(PlayerState other)
	{
		return other == state ? this : new PlayerSnapshot(x, y, plane, other, boats, nexusKeys);
	}

	/** This snapshot with the nexus list's keys. */
	public PlayerSnapshot withNexusKeys(Map<String, String> keys)
	{
		return new PlayerSnapshot(x, y, plane, state, boats, keys);
	}
}
