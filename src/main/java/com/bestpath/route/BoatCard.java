package com.bestpath.route;

import com.bestpath.core.sea.BoatType;
import java.util.Objects;

/**
 * The card at the top of a route started on a boat. With {@link BoatMode#NORMAL} it shows only when the route leaves
 * the boat at sea (it teleports away first), offering "Park my boat" and "Ocean only path"; with another mode, it says
 * which is on and offers to go back. Immutable. Off the boat: no card.
 */
public final class BoatCard
{
	private final BoatMode mode;
	private final String boat;
	private final BoatType type;
	private final boolean oceanOnlyNear;
	/** Why the chosen mode changed nothing for this route, or null (tests read it). */
	final String note;

	/**
	 * @param mode          the mode this route was searched with
	 * @param boat          the boat the player is on, as its cards name it ("Boat 1: Sloop")
	 * @param type          its kind (the card's picture), or null
	 * @param oceanOnlyNear true when a dock or open sea is near enough the destination for "Ocean only path"
	 * @param note          why the mode changed nothing for this route (e.g. ocean only, but nothing near the sea),
	 *                      or null
	 */
	public BoatCard(BoatMode mode, String boat, BoatType type, boolean oceanOnlyNear, String note)
	{
		this.mode = mode;
		this.boat = boat;
		this.type = type;
		this.oceanOnlyNear = oceanOnlyNear;
		this.note = note;
	}

	public BoatMode getMode()
	{
		return mode;
	}

	public String getBoat()
	{
		return boat;
	}

	/** The boat's kind (the card's picture), or null. */
	public BoatType getType()
	{
		return type;
	}

	/** True when "Ocean only path" can be used for this destination (else its button is greyed out). */
	public boolean isOceanOnlyNear()
	{
		return oceanOnlyNear;
	}

	/** The card's title. */
	public String title()
	{
		switch (mode)
		{
			case PARK:
				return "Parking your boat first";
			case OCEAN_ONLY:
				return "Ocean only path is on";
			case NORMAL:
			default:
				return "This route leaves your boat at sea";
		}
	}

	/** The card's text under its title. */
	public String text()
	{
		String b = boat == null ? "your boat" : boat;
		switch (mode)
		{
			case PARK:
				return note != null ? note : "Sails " + b + " to a shipwright's dock first.";
			case OCEAN_ONLY:
				return note != null ? note : "You stay on " + b + " the whole way.";
			case NORMAL:
			default:
				return "Leaves " + b + " at sea; a shipwright can bring it back (costs coins).";
		}
	}

	/** The tooltip of the greyed-out "Ocean only path" button. */
	public static String notNearTip()
	{
		return "No dock or open sea near the destination";
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof BoatCard))
		{
			return false;
		}
		BoatCard c = (BoatCard) o;
		return mode == c.mode && Objects.equals(boat, c.boat) && type == c.type && oceanOnlyNear == c.oceanOnlyNear && Objects.equals(note, c.note);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(mode, boat, type, oceanOnlyNear, note);
	}
}
