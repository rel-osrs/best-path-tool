package com.bestpath.route;

import com.bestpath.core.req.PlayerState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Items the player needs AT the destination (a clue step's outfit, spade, key...), carried with the route request.
 * Only items: skills and quests don't change the way there. Items the player already carries or wears count as
 * nothing to do, and so do items the plugin knows are waiting at the spot (a filled STASH unit: those are left out
 * when the request is made). An item only in the bank means the route must go to a bank first ({@link #fromBank});
 * a missing item does not change the route (the clue card says what is missing). Immutable.
 */
public final class DestinationNeeds
{
	/** Nothing needed (a typed tile, a clue without needs). */
	public static final DestinationNeeds NONE = new DestinationNeeds(List.of());

	/** One needed item: any one of {@code ids} (alternatives), {@code count} of them. */
	public static final class Item
	{
		private final String label;
		private final int[] ids;
		private final int count;

		/** @param label what the player is told ("Gold ring") */
		public Item(String label, int[] ids, int count)
		{
			if (ids.length == 0 || count < 1)
			{
				throw new IllegalArgumentException("a needed item has ids and a count of at least 1");
			}
			this.label = label == null ? "" : label;
			this.ids = ids.clone();
			this.count = count;
		}

		public int[] getIds()
		{
			return ids.clone();
		}

		public int getCount()
		{
			return count;
		}

		/** How many the player carries or wears (any of the ids). */
		int carried(PlayerState p)
		{
			int n = 0;
			for (int id : ids)
			{
				n += p.carried(id);
			}
			return n;
		}

		int banked(PlayerState p)
		{
			int n = 0;
			for (int id : ids)
			{
				n += p.banked(id);
			}
			return n;
		}

		/** The same items and count; the label is only words, so it is not compared. */
		@Override
		public boolean equals(Object o)
		{
			return o instanceof Item && ((Item) o).count == count && Arrays.equals(((Item) o).ids, ids);
		}

		@Override
		public int hashCode()
		{
			return Arrays.hashCode(ids) * 31 + count;
		}

		@Override
		public String toString()
		{
			return label + (count > 1 ? " x" + count : "");
		}
	}

	/** The needed items, in order (tests read it). */
	final List<Item> items;

	public DestinationNeeds(List<Item> items)
	{
		this.items = Collections.unmodifiableList(new ArrayList<>(items));
	}

	public boolean isEmpty()
	{
		return items.isEmpty();
	}

	/**
	 * The needed items the player has only with the bank's help (not enough carried, enough with the bank), in order.
	 * A player with "every item" ({@link PlayerState#isAllItems()}) has everything carried.
	 */
	public List<Item> fromBank(PlayerState p)
	{
		List<Item> out = new ArrayList<>();
		if (p == null || p.isAllItems())
		{
			return out;
		}
		for (Item it : items)
		{
			int carried = it.carried(p);
			if (carried < it.count && carried + it.banked(p) >= it.count)
			{
				out.add(it);
			}
		}
		return out;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof DestinationNeeds && ((DestinationNeeds) o).items.equals(items);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(items);
	}

	@Override
	public String toString()
	{
		return items.toString();
	}
}
