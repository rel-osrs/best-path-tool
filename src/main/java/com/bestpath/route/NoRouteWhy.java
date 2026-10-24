package com.bestpath.route;

import com.bestpath.core.data.NoRouteReasons;
import com.bestpath.core.sea.BoatType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Why there is no route, as the Route tab shows it: the main reason in plain words, a few short lines after it, and
 * for a way that needs transports the player can't use yet, one card per such transport ({@link Blocker}) with the
 * marks on its needs and why it is out. Made on the route worker from core's {@link NoRouteReasons}. No coordinates
 * anywhere: players don't read them. Immutable.
 */
public final class NoRouteWhy
{
	/** One transport of the way the player can't use now: a card like a route's, with why it is out. Immutable. */
	public static final class Blocker
	{
		private final RouteStep step;
		private final List<String> missing;
		private final List<String> reasons;
		private final String restoreId;
		private final boolean skipped;
		private final List<Toggle> toggles;
		/** What "Skip" on the card skips for this route (null: the step's transport id). */
		private final String skipId;

		/**
		 * @param step      the transport as a route card shows it (title, kind, picture and needs with their marks)
		 * @param missing   what it needs that has no small picture on the card (a members world, a spellbook, quest
		 *                  progress...), each shown with the red X
		 * @param reasons   why else it is out, in plain words ("Turned off in Settings: Shortcuts.")
		 * @param restoreId the blacklist id to restore ("Restore" on the card), or null when not blacklisted
		 * @param skipped   skipped for this route (the Route tab's "Undo" allows it again)
		 * @param turnOn    the Settings category that is switched off and leaves it out (its tick box is on the card),
		 *                  or null
		 */
		public Blocker(RouteStep step, List<String> missing, List<String> reasons, String restoreId, boolean skipped,
			TransportSettings.Category turnOn)
		{
			this(step, missing, reasons, restoreId, skipped, turnOn == null ? List.of() : List.of(Toggle.type(turnOn)));
		}

		/**
		 * As above, with the Settings tick boxes the card shows ({@link Toggle}: a transport type, a boat, "A shipwright
		 * may bring my boat") whose being switched off leaves it out; empty for none.
		 */
		public Blocker(RouteStep step, List<String> missing, List<String> reasons, String restoreId, boolean skipped,
			List<Toggle> toggles)
		{
			this(step, missing, reasons, restoreId, skipped, toggles, null);
		}

		private Blocker(RouteStep step, List<String> missing, List<String> reasons, String restoreId, boolean skipped,
			List<Toggle> toggles, String skipId)
		{
			this.toggles = Collections.unmodifiableList(List.copyOf(toggles));
			this.step = step;
			this.missing = Collections.unmodifiableList(List.copyOf(missing));
			this.reasons = Collections.unmodifiableList(List.copyOf(reasons));
			this.restoreId = restoreId;
			this.skipped = skipped;
			this.skipId = skipId;
		}

		/**
		 * The same card whose "Skip" skips {@code id} for this route: the card's own key, so a card standing for every
		 * ride to one fairy ring skips them all ({@link NoRouteReasons.WayStep#getCardKey()}).
		 */
		public Blocker withSkipId(String id)
		{
			return new Blocker(step, missing, reasons, restoreId, skipped, toggles, id);
		}

		/** What "Skip" on the card skips for this route, or null (no Skip: not a transport, e.g. a boat). */
		public String getSkipId()
		{
			return skipId != null ? skipId : step.getTransportId();
		}

		public RouteStep getStep()
		{
			return step;
		}

		/** Needs without a small picture, each not met (shown with the red X). */
		public List<String> getMissing()
		{
			return missing;
		}

		/** Why else it is out (Settings, skipped, never used, cooling down, items in the bank). */
		public List<String> getReasons()
		{
			return reasons;
		}

		/** The id "Restore" takes off the persistent blacklist, or null. */
		public String getRestoreId()
		{
			return restoreId;
		}

		public boolean isSkipped()
		{
			return skipped;
		}

		/** The Settings tick boxes the card shows, in order; empty for none. */
		public List<Toggle> getToggles()
		{
			return toggles;
		}

		@Override
		public String toString()
		{
			return step.getTitle() + " " + missing + " " + reasons;
		}
	}

	/**
	 * A Settings tick box on a "no route" card: a transport type, one of the player's boats, or "A shipwright may bring
	 * my boat". It shows the current state; clicking flips it, saves it and searches again. It reads and changes the
	 * same settings the Settings tab does ({@link TransportSettings#withCategory},
	 * {@link TransportSettings#withBoatAllowed}, {@link TransportSettings#withShipwrights}), so both stay in step.
	 * Immutable.
	 */
	public static final class Toggle
	{
		/** What the tick box switches. */
		public enum What
		{
			/** A transport type ("Shortcuts"). */
			TYPE,
			/** One of the player's boats ("Boat 1: Sloop"). */
			BOAT,
			/** "A shipwright may bring my boat (costs coins)". */
			SHIPWRIGHTS
		}

		private final What what;
		private final TransportSettings.Category category;
		private final int slot;
		private final String label;
		private final BoatType boatType;

		private Toggle(What what, TransportSettings.Category category, int slot, String label, BoatType boatType)
		{
			this.boatType = boatType;
			this.what = what;
			this.category = category;
			this.slot = slot;
			this.label = label;
		}

		/** The tick box of a transport type (its Settings label). */
		public static Toggle type(TransportSettings.Category c)
		{
			return new Toggle(What.TYPE, Objects.requireNonNull(c), -1, c.getLabel(), null);
		}

		/**
		 * The tick box of the boat in {@code slot} (1-5), labelled {@code label} ("Boat 1: Sloop"); its kind for the
		 * picture.
		 */
		public static Toggle boat(int slot, String label, BoatType type)
		{
			return new Toggle(What.BOAT, null, slot, label, type);
		}

		/** "A shipwright may bring my boat (costs coins)", as in the Settings tab. */
		public static Toggle shipwrights()
		{
			return new Toggle(What.SHIPWRIGHTS, null, -1, SHIPWRIGHTS_LABEL, null);
		}

		/** The Settings tab's words for the shipwright tick box. */
		public static final String SHIPWRIGHTS_LABEL = "A shipwright may bring my boat (costs coins)";

		public What getWhat()
		{
			return what;
		}

		/** The transport type (TYPE), else null. */
		public TransportSettings.Category getCategory()
		{
			return category;
		}

		/** The boat's kind (BOAT; may be null), else null. */
		public BoatType getBoatType()
		{
			return boatType;
		}

		/** The words beside the tick box. */
		public String getLabel()
		{
			return label;
		}

		/** Ticked or not in these settings. */
		public boolean isOn(TransportSettings s)
		{
			switch (what)
			{
				case TYPE:
					return s.isEnabled(category);
				case BOAT:
					return s.isBoatAllowed(slot);
				case SHIPWRIGHTS:
				default:
					return s.isShipwrights();
			}
		}

		/** The settings with this tick box set to {@code on} (nothing else changes). */
		public TransportSettings set(TransportSettings s, boolean on)
		{
			switch (what)
			{
				case TYPE:
					return s.withCategory(category, on);
				case BOAT:
					return s.withBoatAllowed(slot, on);
				case SHIPWRIGHTS:
				default:
					return s.withShipwrights(on);
			}
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Toggle))
			{
				return false;
			}
			Toggle t = (Toggle) o;
			return what == t.what && category == t.category && slot == t.slot && label.equals(t.label) && boatType == t.boatType;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(what, category, slot, label);
		}

		@Override
		public String toString()
		{
			return "[" + label + "]";
		}
	}

	/** Core's kind of reason: members land, nowhere to stand, blocked, no known way (tests read it). */
	final NoRouteReasons.Kind kind;
	private final String headline;
	/** Short lines after the main reason, may be empty (tests read it). */
	final List<String> lines;
	/** Every way's cards, way after way, in each way's order; empty unless blocked (tests read it). */
	final List<Blocker> blockers;
	private final List<List<Blocker>> ways;

	/**
	 * Several ways, each needing as many things the player can't use (the Route tab puts "or" between them):
	 * {@code ways} each way's cards.
	 */
	public static NoRouteWhy ofWays(NoRouteReasons.Kind kind, String headline, List<String> lines, List<List<Blocker>> ways)
	{
		return new NoRouteWhy(ways, kind, headline, lines);
	}

	private NoRouteWhy(List<List<Blocker>> ways, NoRouteReasons.Kind kind, String headline, List<String> lines)
	{
		this.kind = kind;
		this.headline = headline;
		this.lines = Collections.unmodifiableList(List.copyOf(lines));
		List<List<Blocker>> w = new ArrayList<>();
		List<Blocker> all = new ArrayList<>();
		for (List<Blocker> way : ways)
		{
			if (!way.isEmpty())
			{
				w.add(List.copyOf(way));
				all.addAll(way);
			}
		}
		this.ways = Collections.unmodifiableList(w);
		this.blockers = Collections.unmodifiableList(all);
	}

	/** The main reason in plain words ("This is members-only land, and you're on a free-to-play world."). */
	public String getHeadline()
	{
		return headline;
	}

	/** Each way's cards (the Route tab puts "or" between ways); one way, or none, in most cases. */
	public List<List<Blocker>> getWays()
	{
		return ways;
	}

	/** The main reason and the lines after it, one per line (the Route tab's text under "No route"). */
	public String text()
	{
		StringBuilder sb = new StringBuilder(headline);
		for (String l : lines)
		{
			sb.append('\n').append(l);
		}
		return sb.toString();
	}

	@Override
	public String toString()
	{
		return kind + ": " + text().replace('\n', ' ') + " " + blockers;
	}
}
