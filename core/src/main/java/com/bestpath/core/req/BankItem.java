package com.bestpath.core.req;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Something to take out of the bank, shown on the bank card: its words ("law runes x2", "coins 30", "8007", a clue's
 * "Iron med helm"), the items any one of which will do, and how many to take out (what is needed minus what the
 * player already carries; at least 1). Made by a requirement's {@link Check} when it is met only with the bank, and by
 * the plugin for a destination's needs. Immutable.
 */
public final class BankItem
{
	private final String label;
	private final int[] itemIds;
	private final int count;
	/** Used up by the step (runes, coins, an item the data marks "used up"); else kept (a lantern, a key, a tool). */
	final boolean usedUp;
	/** How many the step needs in all (at least {@link #count}; the rest the player already carries). */
	private final int need;

	/** A kept item (not used up): as below. */
	public BankItem(String label, int[] itemIds, int count)
	{
		this(label, itemIds, count, false);
	}

	/** As below, with nothing of it carried ({@code need} = {@code count}). */
	public BankItem(String label, int[] itemIds, int count, boolean usedUp)
	{
		this(label, itemIds, count, usedUp, count);
	}

	/**
	 * @param label   the words as the bank card lists them (see {@link #labels})
	 * @param itemIds the items, any one of which will do (at least one)
	 * @param count   how many to take out (at least 1)
	 * @param usedUp  the step uses it up (runes, coins...); a kept one serves every step that needs it
	 * @param need    how many the step needs in all: {@code count} plus what the player already carries
	 */
	public BankItem(String label, int[] itemIds, int count, boolean usedUp, int need)
	{
		this.usedUp = usedUp;
		if (itemIds.length == 0)
		{
			throw new IllegalArgumentException("bank item: no ids");
		}
		this.label = label == null ? "" : label;
		this.itemIds = itemIds.clone();
		this.count = Math.max(1, count);
		this.need = Math.max(this.count, need);
	}

	/** The words as the bank card lists them ("law runes x2", "coins 30"). */
	public String getLabel()
	{
		return label;
	}

	/** The items, any one of which will do (a copy). */
	public int[] getItemIds()
	{
		return itemIds.clone();
	}

	/** How many to take out (what is needed minus what the player carries; at least 1). */
	public int getCount()
	{
		return count;
	}

	/**
	 * Adds what a step needs to a bank visit's list. Used-up things of the same items add up into one line: two steps
	 * that each pay 30 coins ask for "coins 60" (what the player carries counted once). A kept item that one already
	 * on the list can stand for (an item of both) is not listed again; the one on the list keeps only the items that
	 * do for both, and the larger count. So two steps that each need a light source ask for one lantern, as it is not
	 * used up. Anything else is added.
	 */
	public static void addTo(List<BankItem> take, BankItem it)
	{
		if (it.usedUp)
		{
			for (int i = 0; i < take.size(); i++)
			{
				BankItem e = take.get(i);
				if (e.usedUp && Arrays.equals(e.itemIds, it.itemIds))
				{
					take.set(i, e.plus(it));
					return;
				}
			}
			take.add(it);
			return;
		}
		if (take.contains(it))
		{
			return;
		}
		for (int i = 0; i < take.size(); i++)
		{
			BankItem e = take.get(i);
			int[] both = e.usedUp ? new int[0] : both(e.itemIds, it.itemIds);
			if (both.length > 0)
			{
				take.set(i, new BankItem(e.label, both, Math.max(e.count, it.count), false));
				return;
			}
		}
		take.add(it);
	}

	/** This used-up item and {@code other} (the same items) together: both needs, less what is carried once. */
	private BankItem plus(BankItem other)
	{
		int carried = need - count;
		int total = need + other.need;
		return new BankItem(relabel(label, need, total), itemIds, total - carried, true, total);
	}

	/** "coins 30" -> "coins 60", "law runes x1" -> "law runes x3", "Feather" -> "Feather x2". */
	private static String relabel(String label, int was, int now)
	{
		if (label.equals("coins " + was))
		{
			return "coins " + now;
		}
		String x = " x" + was;
		return (label.endsWith(x) ? label.substring(0, label.length() - x.length()) : label) + " x" + now;
	}

	/** The ids in both, in {@code a}'s order. */
	private static int[] both(int[] a, int[] b)
	{
		int[] out = new int[Math.min(a.length, b.length)];
		int n = 0;
		for (int x : a)
		{
			for (int y : b)
			{
				if (x == y)
				{
					out[n++] = x;
					break;
				}
			}
		}
		return Arrays.copyOf(out, n);
	}

	/** The words of each, in order (what the bank card's title lists). */
	public static List<String> labels(List<BankItem> items)
	{
		List<String> out = new ArrayList<>(items.size());
		for (BankItem b : items)
		{
			out.add(b.label);
		}
		return out;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof BankItem))
		{
			return false;
		}
		BankItem b = (BankItem) o;
		return b.label.equals(label) && b.count == count && b.usedUp == usedUp && Arrays.equals(b.itemIds, itemIds);
	}

	@Override
	public int hashCode()
	{
		return label.hashCode() * 31 + Arrays.hashCode(itemIds) * 7 + count;
	}

	@Override
	public String toString()
	{
		return label + " " + Arrays.toString(itemIds) + " take out " + count;
	}
}
