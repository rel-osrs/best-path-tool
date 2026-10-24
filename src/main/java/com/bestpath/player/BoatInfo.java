package com.bestpath.player;

import com.bestpath.core.sea.BoatType;
import java.util.Objects;

/**
 * One of the player's boats as the Settings tab lists it: slot, kind, name, where it is, and whether routes can use
 * it (and why not). Immutable.
 */
public final class BoatInfo
{
	private final int slot;
	private final BoatType type;
	private final String title;
	private final String where;
	private final boolean usable;
	private final String details;

	/**
	 * @param type    our kind, or null (a kind routes don't know)
	 * @param title   "Sloop: Jolly Raven"
	 * @param where   "At Port Sarim", "At sea, you are on it", "Bottled"...
	 * @param usable  routes can use it (its kind, parts and place are known)
	 * @param details the parts in words, or why routes can't use it
	 */
	public BoatInfo(int slot, BoatType type, String title, String where, boolean usable, String details)
	{
		this.slot = slot;
		this.type = type;
		this.title = title;
		this.where = where;
		this.usable = usable;
		this.details = details;
	}

	public int getSlot()
	{
		return slot;
	}

	/** Our kind, or null. */
	public BoatType getType()
	{
		return type;
	}

	public String getTitle()
	{
		return title;
	}

	public String getWhere()
	{
		return where;
	}

	public boolean isUsable()
	{
		return usable;
	}

	public String getDetails()
	{
		return details;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof BoatInfo))
		{
			return false;
		}
		BoatInfo b = (BoatInfo) o;
		return slot == b.slot && type == b.type && title.equals(b.title) && where.equals(b.where) && usable == b.usable
			&& details.equals(b.details);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(slot, type, title, where, usable, details);
	}

	@Override
	public String toString()
	{
		return "Boat " + slot + ": " + title + ", " + where + (usable ? "" : " (not for routes)");
	}
}
