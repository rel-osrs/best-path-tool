package com.bestpath.core.data;

import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.Dock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What one route may do at sea: the player's boats and the Settings choices
 * (which boats routes may use, whether a shipwright may bring a boat, whether
 * the boat the player is on may be left at sea). Boats are read from the game:
 * kind, parts and where each is moored. The one the player stands on is marked
 * {@link Boat#AT_START} and the route then starts from it. Immutable.
 *
 * <p>The player's Sailing level and quests come from the player's state: docks
 * above the level, or whose quest is not finished, are not used to board or
 * leave a boat.
 */
public final class SailingSetup
{
	/** No boats: routes never sail. */
	public static final SailingSetup NONE = new SailingSetup(List.of(), null, true);

	private final List<Boat> boats;
	private final Set<Integer> allowedSlots;
	private final boolean shipwrights;
	private final boolean keepBoat;
	private final int boardTicks;

	/**
	 * @param boats        the player's boats (up to five, slots 1-5)
	 * @param allowedSlots the slots routes may use (Settings); null = every boat
	 * @param shipwrights  whether a shipwright may bring a boat to the player's dock (Settings; costs coins)
	 */
	public SailingSetup(List<Boat> boats, Set<Integer> allowedSlots, boolean shipwrights)
	{
		this(boats, allowedSlots, shipwrights, false);
	}

	/** {@code keepBoat}: never leave the boat the player is on at sea (Settings). */
	public SailingSetup(List<Boat> boats, Set<Integer> allowedSlots, boolean shipwrights, boolean keepBoat)
	{
		this(boats, allowedSlots, shipwrights, keepBoat, Dock.BOARD_TICKS);
	}

	/** {@code boardTicks}: ticks to board a boat and again to leave it (Settings; default {@link Dock#BOARD_TICKS}). */
	public SailingSetup(List<Boat> boats, Set<Integer> allowedSlots, boolean shipwrights, boolean keepBoat, int boardTicks)
	{
		this.boardTicks = Math.max(0, boardTicks);
		this.boats = Collections.unmodifiableList(new ArrayList<>(boats));
		this.allowedSlots = allowedSlots == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(allowedSlots));
		this.shipwrights = shipwrights;
		this.keepBoat = keepBoat;
	}

	/** Every boat the player has. */
	public List<Boat> getBoats()
	{
		return boats;
	}

	/** The slots routes may use, or null for every boat. */
	public Set<Integer> getAllowedSlots()
	{
		return allowedSlots;
	}

	public boolean isShipwrights()
	{
		return shipwrights;
	}

	/** True: a route from the boat the player is on starts by sailing it to a dock (never leaves it at sea). */
	public boolean isKeepBoat()
	{
		return keepBoat;
	}

	/** Ticks to board a boat, and again to leave it or release it from its bottle. */
	public int getBoardTicks()
	{
		return boardTicks;
	}

	/** The boats routes may use (the Settings choice applied), in the order given. */
	public List<Boat> usableBoats()
	{
		List<Boat> out = new ArrayList<>();
		for (Boat b : boats)
		{
			if (allowedSlots == null || allowedSlots.contains(b.getSlot()))
			{
				out.add(b);
			}
		}
		return out;
	}

	/** True if no boat may be used (routes never sail). */
	public boolean isEmpty()
	{
		return usableBoats().isEmpty();
	}

	/**
	 * The same setup with "never leave the boat at sea" on or off. "Park my boat" on the route's boat card does it for
	 * one route; the Settings choice does it always.
	 */
	public SailingSetup withKeepBoat(boolean keep)
	{
		return keep == keepBoat ? this : new SailingSetup(boats, allowedSlots, shipwrights, keep, boardTicks);
	}
}
