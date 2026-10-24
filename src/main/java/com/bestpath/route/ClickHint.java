package com.bestpath.route;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * What the player clicks for a step, so the thing can glow with text above it: the game's menu words, the object the
 * data records (most transports have one), the items the step is done with (every variant, e.g. each charge of an
 * amulet of glory), and whether it is a spell. The plugin finds the object or NPC in the loaded scene near
 * {@link #getNear()} on the client thread. Immutable.
 */
public final class ClickHint
{
	public static final ClickHint NONE = new ClickHint("", "", null, -1, -1, new int[0], false, -1);

	private final String option;
	private final String target;
	private final String choice;
	private final int objectId;
	/** Where the data records the object (packed), or -1 (tests read it). */
	final int objectTile;
	/** The items the step is done with (any of them; tests read it). */
	final int[] itemIds;
	private final boolean spell;
	private final int near;
	/**
	 * House furniture the step uses, which glows like an object: the requirement's "house has ..." things in lower
	 * case ("ornate jewellery box", "portal nexus: Varrock Teleport"); empty for any other step.
	 */
	private final Set<String> houseThings;

	/** As below, no house furniture. */
	public ClickHint(String option, String target, String choice, int objectId, int objectTile, int[] itemIds, boolean spell,
		int near)
	{
		this(option, target, choice, objectId, objectTile, itemIds, spell, near, Set.of());
	}

	/**
	 * @param option     the menu option ("Climb-up", "Travel", "Cast"); "" = not known
	 * @param target     what is clicked as the game names it ("Ladder", "Captain Tobias", "Varrock Teleport");
	 *                   "" = not known
	 * @param choice     picked after the click in a list or dialogue ("Rimmington"), or null
	 * @param objectId   the object the data records, or -1 (an NPC, an item, a spell, or not recorded)
	 * @param objectTile where the data records the object (packed), or -1
	 * @param itemIds    the items the step is done with (any of them), empty when none
	 * @param spell      a spell: it glows in the spellbook
	 * @param near       the tile the step is used at (packed; where the object or NPC is looked for), or -1 (a teleport:
	 *                   nothing in the world to click)
	 * @param houseThings the house furniture the step uses ({@link #getHouseThings()}), or null for none
	 */
	public ClickHint(String option, String target, String choice, int objectId, int objectTile, int[] itemIds, boolean spell,
		int near, Set<String> houseThings)
	{
		this.houseThings = houseThings == null ? Set.of() : Set.copyOf(houseThings);
		this.option = option == null ? "" : option;
		this.target = target == null ? "" : target;
		this.choice = choice;
		this.objectId = objectId;
		this.objectTile = objectTile;
		this.itemIds = itemIds == null ? new int[0] : itemIds.clone();
		this.spell = spell;
		this.near = near;
	}

	public String getOption()
	{
		return option;
	}

	public String getTarget()
	{
		return target;
	}

	public String getChoice()
	{
		return choice;
	}

	public int getObjectId()
	{
		return objectId;
	}

	/** True when one of the step's items has this id. */
	public boolean usesItem(int id)
	{
		for (int i : itemIds)
		{
			if (i == id)
			{
				return true;
			}
		}
		return false;
	}

	public boolean isSpell()
	{
		return spell;
	}

	public int getNear()
	{
		return near;
	}

	/** The house furniture the step uses ("ornate jewellery box"...); empty when none. */
	public Set<String> getHouseThings()
	{
		return houseThings;
	}

	/**
	 * True when furniture of this kind (the house table's name for a built object: "ornate jewellery box", "portal
	 * nexus", "portal chamber: Varrock Teleport") is what the step uses; a portal nexus does every "portal nexus: ..."
	 */
	public boolean usesFurniture(String thing)
	{
		if (thing == null || houseThings.isEmpty())
		{
			return false;
		}
		String t = thing.toLowerCase(Locale.ROOT);
		for (String h : houseThings)
		{
			if (h.equals(t) || t.equals("portal nexus") && h.startsWith("portal nexus: "))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof ClickHint))
		{
			return false;
		}
		ClickHint h = (ClickHint) o;
		return option.equals(h.option) && target.equals(h.target) && Objects.equals(choice, h.choice) && objectId == h.objectId
			&& objectTile == h.objectTile && Arrays.equals(itemIds, h.itemIds) && spell == h.spell && near == h.near
			&& houseThings.equals(h.houseThings);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(option, target, choice, objectId, objectTile, Arrays.hashCode(itemIds), spell, near);
	}
}
