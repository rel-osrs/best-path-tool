package com.bestpath.clue;

import com.bestpath.core.path.Tiles;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Notices the player putting items into a STASH unit or taking them out. How it works in the game (the server does it
 * all; there is no client script or interface for it): "Search" on a built unit stashes the full set of that unit's
 * emote clue items from the inventory (and worn items with the game's "STASH units take equipped items" setting);
 * "Search" on a filled unit takes them all out into the inventory.
 *
 * <p>So: the player clicks "Search" on a built STASH unit ({@link #searched}); while they stand next to it, the next
 * change of what they carry and wear says what happened: items only gone = put in (the unit is filled), items only
 * come = taken out (empty). A mixed change, another click, or {@link #TIMEOUT_TICKS} without a change forgets the
 * click. Pure; client thread.
 */
public final class StashSearch
{
	/** A click on "Search" is forgotten after this many ticks without an item change (walk there and search). */
	public static final int TIMEOUT_TICKS = 50;
	/** Counted only while the player is within this many tiles of the unit (same plane). */
	public static final int NEAR_TILES = 2;

	/** What a search did. Immutable. */
	public static final class Result
	{
		private final int unit;
		private final boolean deposited;
		private final Map<Integer, Integer> items;

		Result(int unit, boolean deposited, Map<Integer, Integer> items)
		{
			this.unit = unit;
			this.deposited = deposited;
			this.items = Collections.unmodifiableMap(new TreeMap<>(items));
		}

		/** The unit's built varbit. */
		public int getUnit()
		{
			return unit;
		}

		/** True: put in (filled); false: taken out (empty). */
		public boolean isDeposited()
		{
			return deposited;
		}

		/** Item id -> count put in or taken out. */
		public Map<Integer, Integer> getItems()
		{
			return items;
		}

		@Override
		public String toString()
		{
			return (deposited ? "put into " : "taken out of ") + unit + ": " + items;
		}
	}

	private int unit = -1;
	private int tile = -1;
	private int clickTick;
	private Map<Integer, Integer> before = Map.of();

	/**
	 * The player clicked "Search" on a built STASH unit.
	 *
	 * @param unitVarbit the unit's built varbit
	 * @param unitTile   the unit's tile (packed, core Tiles)
	 * @param carried    what the player carries and wears now (item id -> count)
	 */
	public void searched(int unitVarbit, int unitTile, Map<Integer, Integer> carried, int tick)
	{
		unit = unitVarbit;
		tile = unitTile;
		clickTick = tick;
		before = new TreeMap<>(carried);
	}

	/** Another click (the player went to do something else): forget the search. */
	public void cancel()
	{
		unit = -1;
	}

	/** True while a search click waits for its item change. */
	public boolean isWaiting()
	{
		return unit != -1;
	}

	/**
	 * What the player carries and wears changed (or a tick passed).
	 *
	 * @param carried    what they carry and wear now
	 * @param playerTile where they stand (packed), or -1
	 * @return what the search did, or null (nothing yet, or not a STASH change)
	 */
	public Result onItems(Map<Integer, Integer> carried, int playerTile, int tick)
	{
		if (unit == -1)
		{
			return null;
		}
		if (tick - clickTick > TIMEOUT_TICKS)
		{
			cancel();
			return null;
		}
		if (playerTile == -1 || Tiles.plane(playerTile) != Tiles.plane(tile)
			|| Math.max(Math.abs(Tiles.x(playerTile) - Tiles.x(tile)), Math.abs(Tiles.y(playerTile) - Tiles.y(tile))) > NEAR_TILES)
		{
			before = new TreeMap<>(carried); // still on the way: a change there is something else
			return null;
		}
		Map<Integer, Integer> gone = new TreeMap<>();
		Map<Integer, Integer> come = new TreeMap<>();
		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(carried.keySet());
		for (int id : ids)
		{
			int d = carried.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			if (d < 0)
			{
				gone.put(id, -d);
			}
			else if (d > 0)
			{
				come.put(id, d);
			}
		}
		if (gone.isEmpty() && come.isEmpty())
		{
			return null;
		}
		int u = unit;
		cancel();
		if (!gone.isEmpty() && !come.isEmpty())
		{
			return null; // both: not a STASH search (or one we can't read); remember nothing
		}
		return gone.isEmpty() ? new Result(u, false, come) : new Result(u, true, gone);
	}
}
