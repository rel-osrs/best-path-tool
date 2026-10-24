package com.bestpath.core.data;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.sea.Boat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Why a destination has no route, to show the player. Made by {@link PluginRouter#explainNoRoute} after a route came
 * back "no route", with the same player, start, places, exclusions and filter. One of:
 * <ul>
 * <li>{@link Kind#MEMBERS_LAND}: a player on a free world, and no place is on free-to-play land;</li>
 * <li>{@link Kind#NOT_STANDABLE}: no tile within {@value PluginRouter#NEAR_RANGE} tiles of the place can be walked on
 * (inside rock, a wall, water; {@link #isAtSea()}: open sea, where only a boat can be);</li>
 * <li>{@link Kind#BLOCKED}: a way exists when transports the player can't use now are allowed. The way is the one
 * needing the fewest distinct things the player can't use ({@link #getThings()}), then the fewest ticks: every
 * transport is allowed, and each thing costs extra ticks in the search (a few searches, see {@link FewestThings}).
 * The way's steps list every transport of that way ({@link #getBlocked()}: those the player can't use), each with
 * why it is out (requirements not met, items only in the bank, left out by the {@link PluginRouter.TransportFilter}
 * (Settings), skipped or blacklisted by id, cooling down). Other ways needing as many things, but other things, are listed too ({@link #getOtherWays()});</li>
 * <li>{@link Kind#BY_BOAT}: no way on land even with every transport (or the place is open sea), but one by boat
 * ({@link SeaWay}): {@link #getSailSteps()} says what the boat or its docks need that the player can't use (no boat,
 * the boat or shipwrights turned off in Settings, a bottled boat's bottle, a dock's Sailing level or quest);</li>
 * <li>{@link Kind#NO_KNOWN_WAY}: not even with every transport, and none by boat (the map data has no way there);</li>
 * <li>{@link Kind#REACHABLE}: a way exists with what the player can use now (should not happen after "no route";
 * kept so a caller never gets a wrong reason).</li>
 * </ul>
 * Only the search for the player's world type is used, so a free-world player's way is looked for on free land only.
 * Nothing is kept between calls. Ways by boat are looked for only when there is none on land, for members, with the
 * sea in the data. Immutable.
 */
public final class NoRouteReasons
{
	/** Extra ticks for each thing the player can't use now: the way with the fewest of them wins, then the fewest ticks. */
	public static final int BLOCKED_EXTRA = 10_000;
	/**
	 * Ways dearer than this (the extra ticks included, so about 48 things the player can't use) are not looked for:
	 * keeps the tile search's tick sums (ticks << 12 in an int) in range. Such a way counts as no known way.
	 */
	static final int SEARCH_LIMIT = 480_000;
	/** With several places (a clue's): at most this many are looked at, nearest to the player first, one per component. */
	public static final int MAX_PLACES = 4;

	/** What kind of reason. */
	public enum Kind
	{
		/** A free-world player; no place is on free-to-play land. */
		MEMBERS_LAND,
		/** Nothing within {@value PluginRouter#NEAR_RANGE} tiles of the place can be walked on. */
		NOT_STANDABLE,
		/** A way exists with transports the player can't use now ({@link #getBlocked()}). */
		BLOCKED,
		/**
		 * No way on land even with every transport, but one by boat with things the player can't use yet
		 * ({@link #getSailSteps()}: no boat, the boat or shipwrights turned off in Settings, a dock's Sailing level
		 * or quest, a bottled boat's bottle).
		 */
		BY_BOAT,
		/** No way at all in the map data, even with every transport (and none by boat). */
		NO_KNOWN_WAY,
		/** A way exists with what the player can use now (the route search should have found it). */
		REACHABLE
	}

	/** Why one transport is out for this player now. */
	public enum Why
	{
		/** Its requirement is not met ({@link WayStep#getUnmet()}). */
		REQUIREMENTS,
		/** Met only with items from the bank: "no route" means no bank visit helped. */
		BANK,
		/** Left out by the route's {@link PluginRouter.TransportFilter} (the plugin: turned off in Settings). */
		FILTER,
		/** Left out by id (the plugin: skipped for this route, or blacklisted). */
		EXCLUDED,
		/** A home or minigame teleport whose cooldown runs ({@link WayStep#getReadyAtMinute()}: when it is ready). */
		COOLDOWN
	}

	/** One transport of the way, usable now or not, and why not. Immutable. */
	public static final class WayStep
	{
		private final Transport transport;
		private final TransportLine line;
		private final TransportLine fromStop;
		private final boolean reverse;
		private final int ticks;
		/** The tile used from, then the landing (a teleport: the landing), as a route step's. */
		final int[] tiles;
		private final Set<Why> why;
		private final Requirement requirement;
		private final List<Requirement> unmet;
		/** What the unmet parts say is missing, as text ("Agility 30 (has 1)"). */
		final List<String> unmetTexts;
		/** The parts met only with items in the bank (leaves), and what they need from it, as text ("coins 30 from the bank"). */
		final List<Requirement> inBank;
		final List<String> inBankTexts;
		/** The cooldown that runs ({@link Why#COOLDOWN}), else null. */
		final Cooldown cooldown;
		private final long readyAtMinute;

		WayStep(Transport transport, TransportLine line, TransportLine fromStop, boolean reverse, int ticks, int[] tiles,
			Set<Why> why, Requirement requirement, List<Requirement> unmet, List<String> unmetTexts, List<Requirement> inBank,
			List<String> inBankTexts, Cooldown cooldown, long readyAtMinute)
		{
			this.transport = transport;
			this.line = line;
			this.fromStop = fromStop;
			this.reverse = reverse;
			this.ticks = ticks;
			this.tiles = tiles.clone();
			this.why = why.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(why));
			this.requirement = requirement;
			this.unmet = List.copyOf(unmet);
			this.unmetTexts = List.copyOf(unmetTexts);
			this.inBank = List.copyOf(inBank);
			this.inBankTexts = List.copyOf(inBankTexts);
			this.cooldown = cooldown;
			this.readyAtMinute = readyAtMinute;
		}

		/** The transport as a route would use it (its data ticks, the player's tick change; never the extra ticks). */
		public Transport getTransport()
		{
			return transport;
		}

		/**
		 * Which transport card this step is, so each is shown once: its exported line's id. A network ride's is the
		 * stop it goes to, so fairy ring DLR from any ring is one card.
		 */
		public String getCardKey()
		{
			return line.getId();
		}

		/** The exported line it was made from (a network ride: the stop it goes to), as {@link PlayerTransports.Result#lineOf}. */
		public TransportLine getLine()
		{
			return line;
		}

		/** A network ride: the stop it leaves from; null otherwise. */
		public TransportLine getFromStop()
		{
			return fromStop;
		}

		/** True when a two-way transport is used from its end to its start (its "way back" requirement applies). */
		public boolean isReverse()
		{
			return reverse;
		}

		/** Ticks for this step as a route would count them (with the player's tick changes). */
		public int getTicks()
		{
			return ticks;
		}

		/** True when the player can't use it now (then {@link #getWhy()} is not empty). */
		public boolean isBlocked()
		{
			return !why.isEmpty();
		}

		/** Every reason it is out now; empty when the player can use it. */
		public Set<Why> getWhy()
		{
			return why;
		}

		/**
		 * Its requirement for the way it is used (with its network's; a ride: both stops' together), or null when the
		 * line can't be read (counts as met). {@link Requirement#NONE} when it needs nothing.
		 */
		public Requirement getRequirement()
		{
			return requirement;
		}

		/**
		 * The parts of {@link #getRequirement()} the player does not meet: leaves (skill level, quest, item, coins,
		 * varbit, members, ...; a "not" counts as one). Of a "one of (...)" none of whose choices is met, the choice
		 * nearest to being met ({@link #closestChoice}).
		 */
		public List<Requirement> getUnmet()
		{
			return unmet;
		}

		/** With a cooldown that runs ({@link Why#COOLDOWN}): the minute (since 1970 UTC) it is ready again; 0 = not known. */
		public long getReadyAtMinute()
		{
			return readyAtMinute;
		}

		/** Its name and every reason it is out: "Boat or ship: ...: quest Sea Slug; turned off in Settings". */
		public String describe()
		{
			List<String> parts = new ArrayList<>(unmetTexts);
			for (String b : inBankTexts)
			{
				parts.add("in the bank: " + b.replace(" from the bank", ""));
			}
			if (why.contains(Why.FILTER))
			{
				parts.add("turned off in Settings");
			}
			if (why.contains(Why.EXCLUDED))
			{
				parts.add("skipped or blacklisted");
			}
			if (why.contains(Why.COOLDOWN))
			{
				parts.add(cooldown.getLabel() + " cooling down");
			}
			return transport.getName() + (parts.isEmpty() ? "" : ": " + String.join("; ", parts));
		}
	}

	/**
	 * One part of a way by boat the player can't use now ({@link Kind#BY_BOAT}): the boat, or the dock where it is
	 * boarded or left, with why. Only rules the route search itself has ({@link Sailing}): routes
	 * use only the boats allowed in Settings, a shipwright brings a boat only while shipwrights are allowed, a bottled
	 * boat needs its full bottle in the inventory, a dock needs its Sailing level and its quest. Immutable.
	 */
	public static final class SailStep
	{
		/** Which part of the way by boat. */
		public enum What
		{
			/** The boat (the player's, or none at all). */
			BOAT,
			/** The dock the boat is boarded at (or released from its bottle at). */
			BOARD,
			/** The dock the boat is left at. */
			LEAVE
		}

		/** Why it is out. */
		public enum SailWhy
		{
			/** The player has no boat (a way exists with one). */
			NO_BOAT,
			/** The boat is turned off in Settings (routes may not use it). */
			BOAT_OFF,
			/** It needs a shipwright to bring the boat, and shipwrights are turned off in Settings. */
			SHIPWRIGHTS_OFF,
			/** A bottled boat: its full bottle is not in the inventory ({@link SailStep#getUnmet()}: the bottle). */
			BOTTLE,
			/** The dock's Sailing level or quest is not met ({@link SailStep#getUnmet()}). */
			REQUIREMENTS,
			/**
			 * The boat's parts keep it out of water on the way (e.g. Crystalline waters need an adamant keel):
			 * {@link SailStep#getUnmetTexts()} names each area and what it needs.
			 */
			PARTS
		}

		private final What what;
		private final Boat boat;
		private final String dock;
		private final Set<SailWhy> why;
		private final Requirement requirement;
		/** The parts of {@link #getRequirement()} the player does not meet (leaves). */
		final List<Requirement> unmet;
		private final List<String> unmetTexts;

		SailStep(What what, Boat boat, String dock, Set<SailWhy> why, Requirement requirement, List<Requirement> unmet,
			List<String> unmetTexts)
		{
			this.what = what;
			this.boat = boat;
			this.dock = dock;
			this.why = Collections.unmodifiableSet(why.isEmpty() ? EnumSet.noneOf(SailWhy.class) : EnumSet.copyOf(why));
			this.requirement = requirement == null ? Requirement.NONE : requirement;
			this.unmet = List.copyOf(unmet);
			this.unmetTexts = List.copyOf(unmetTexts);
		}

		public What getWhat()
		{
			return what;
		}

		/** The boat the way uses (the player's), or null: the player has none ({@link SailWhy#NO_BOAT}). */
		public Boat getBoat()
		{
			return boat;
		}

		/** {@link What#BOARD} / {@link What#LEAVE}: the dock's name; else null. */
		public String getDock()
		{
			return dock;
		}

		public Set<SailWhy> getWhy()
		{
			return why;
		}

		/**
		 * What it needs, for the marks on its card: a dock's Sailing level and quest, the bottled boat's full bottle;
		 * {@link Requirement#NONE} when nothing.
		 */
		public Requirement getRequirement()
		{
			return requirement;
		}

		/** What the unmet parts of {@link #getRequirement()} say is missing ("Sailing 40 (has 12)"). */
		public List<String> getUnmetTexts()
		{
			return unmetTexts;
		}

		/** Its name and why: "Board at Port Sarim: Sailing 40 (has 12)", "A boat: you have none". */
		public String describe()
		{
			String name = what == What.BOAT ? (boat == null ? "A boat" : boat.label())
				: (what == What.BOARD ? "Board at " : "Leave at ") + dock;
			List<String> parts = new ArrayList<>(unmetTexts);
			if (why.contains(SailWhy.NO_BOAT))
			{
				parts.add("you have none");
			}
			if (why.contains(SailWhy.BOAT_OFF))
			{
				parts.add("turned off in Settings");
			}
			if (why.contains(SailWhy.SHIPWRIGHTS_OFF))
			{
				parts.add("shipwrights turned off in Settings");
			}
			return name + (parts.isEmpty() ? "" : ": " + String.join("; ", parts));
		}

		@Override
		public String toString()
		{
			return describe();
		}
	}

	private final Kind kind;
	/** {@link Kind#BLOCKED} (and REACHABLE): every transport of the way, in order, usable or not; else empty. */
	final List<WayStep> steps;
	private final List<List<WayStep>> otherWays;
	private final List<SailStep> sailSteps;
	private final int things;
	final int ticks;
	final int chosen;
	/** Where the way ends {x, y, plane, inSight 1/0} (the place itself or the tile chosen instead), or null. */
	final int[] stop;
	private final boolean freeWorld;
	private final boolean atSea;
	final int placesLookedAt;
	private final List<String> reasons;

	NoRouteReasons(Kind kind, List<WayStep> steps, List<SailStep> sailSteps, int things, int ticks, int chosen, int[] stop,
		boolean freeWorld, boolean atSea, int placesLookedAt, List<String> reasons)
	{
		this(kind, steps, List.of(), sailSteps, things, ticks, chosen, stop, freeWorld, atSea, placesLookedAt, reasons);
	}

	NoRouteReasons(Kind kind, List<WayStep> steps, List<List<WayStep>> otherWays, List<SailStep> sailSteps, int things,
		int ticks, int chosen, int[] stop, boolean freeWorld, boolean atSea, int placesLookedAt, List<String> reasons)
	{
		this.kind = kind;
		this.steps = List.copyOf(steps);
		List<List<WayStep>> others = new ArrayList<>();
		for (List<WayStep> w : otherWays)
		{
			others.add(List.copyOf(w));
		}
		this.otherWays = Collections.unmodifiableList(others);
		this.sailSteps = List.copyOf(sailSteps);
		this.things = things;
		this.ticks = ticks;
		this.chosen = chosen;
		this.stop = stop == null ? null : stop.clone();
		this.freeWorld = freeWorld;
		this.atSea = atSea;
		this.placesLookedAt = placesLookedAt;
		this.reasons = List.copyOf(reasons);
	}

	public Kind getKind()
	{
		return kind;
	}

	/** The transports of the way the player can't use now, in order (never empty for {@link Kind#BLOCKED}). */
	public List<WayStep> getBlocked()
	{
		List<WayStep> out = new ArrayList<>();
		for (WayStep s : steps)
		{
			if (s.isBlocked())
			{
				out.add(s);
			}
		}
		return out;
	}

	/**
	 * {@link Kind#BLOCKED}: the other ways to the same place needing as many things the player can't use (but other
	 * things), each listed as the way's steps are (every transport, usable or not), the quickest first;
	 * empty when there is only one. At most {@value #MAX_WAYS} ways in all; found by searching again with each of a
	 * way's things left out.
	 */
	public List<List<WayStep>> getOtherWays()
	{
		return otherWays;
	}

	/** {@link Kind#BY_BOAT}: what the way by boat needs that the player can't use now, in order; else empty. */
	public List<SailStep> getSailSteps()
	{
		return sailSteps;
	}

	/**
	 * {@link Kind#BLOCKED} and {@link Kind#BY_BOAT}: how many distinct things the way needs that the player can't use
	 * now; 0 otherwise. On land each transport (card) the player can't use is one, however many of its needs are
	 * missing, and transports needing exactly the same things are one together (e.g. two gates needing Agility 30).
	 */
	public int getThings()
	{
		return things;
	}

	/** True when it was looked for on free-to-play land only (the player is not a member). */
	public boolean isFreeWorld()
	{
		return freeWorld;
	}

	/** {@link Kind#NOT_STANDABLE}: the place is in or next to the open sea, where only a boat can be. */
	public boolean isAtSea()
	{
		return atSea;
	}

	/** Short reasons in plain words, first the main one ("The way there needs 2 transports you can't use yet."). */
	public List<String> getReasons()
	{
		return reasons;
	}

	@Override
	public String toString()
	{
		return kind + " " + reasons;
	}

	/**
	 * The choice of a "one of (...)" to show the player: the one routes use when one is met ({@link
	 * Requirement.AnyOf#chosen}); when none is, the one with the fewest parts not met (then the fewest from the bank;
	 * the first of equals).
	 */
	public static int closestChoice(Requirement.AnyOf any, PlayerState player)
	{
		int c = any.chosen(player);
		if (c >= 0)
		{
			return c;
		}
		int best = 0;
		long bestScore = Long.MAX_VALUE;
		List<Requirement> alts = any.getAlternatives();
		for (int i = 0; i < alts.size(); i++)
		{
			List<Requirement> unmet = new ArrayList<>();
			List<Requirement> bank = new ArrayList<>();
			collect(alts.get(i), player, unmet, new ArrayList<>(), bank, new ArrayList<>());
			long score = (long) unmet.size() << 16 | bank.size();
			if (score < bestScore)
			{
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	/** The leaves of {@code r} the player does not meet ({@code unmet}) and meets only with the bank ({@code bank}). */
	static void collect(Requirement r, PlayerState player, List<Requirement> unmet, List<String> unmetTexts,
		List<Requirement> bank, List<String> bankTexts)
	{
		if (r instanceof Requirement.All)
		{
			for (Requirement p : ((Requirement.All) r).getParts())
			{
				collect(p, player, unmet, unmetTexts, bank, bankTexts);
			}
			return;
		}
		if (r instanceof Requirement.AnyOf)
		{
			Requirement.AnyOf any = (Requirement.AnyOf) r;
			collect(any.getAlternatives().get(closestChoice(any, player)), player, unmet, unmetTexts, bank, bankTexts);
			return;
		}
		Check c = r.check(player);
		if (c.getOutcome() == Check.Outcome.NOT_MET)
		{
			unmet.add(r);
			unmetTexts.addAll(c.getMissing());
		}
		else if (c.getOutcome() == Check.Outcome.NEEDS_BANK)
		{
			bank.add(r);
			bankTexts.addAll(c.getMissing());
		}
	}

	/**
	 * The explanation (see the class comment). {@code pf} is the router's search for the player's world type. With no
	 * way on land (or a place out at sea) a way by boat is looked for ({@link SeaWay}): {@code sailing} the router's
	 * sea routes (null: none, a free world), {@code boats} the player's boats and the Settings choices.
	 * The transports {@code leftOut} names (by id, a ":back" way by its transport's id too, or by card:
	 * {@link WayStep#getCardKey()}, so every network ride to that stop) are never on a way, not even as a thing the
	 * player can't use ("Skip" on a "no route" card uses this to show the next way without that transport).
	 * Null: none.
	 *
	 * @param ticks the player's tick changes as {@link PlayerTransports#build(PlayerState, int, FreeLand,
	 *              PlayerTransports.TickChanges)} takes them (null = none)
	 */
	static NoRouteReasons explain(Pathfinder pf, Sailing sailing, SailingSetup boats, PluginData data, PlayerTransports transports,
		PlayerState player, int sx, int sy, int sp, int[] targets, Predicate<String> excluded, Predicate<String> leftOut,
		PluginRouter.TransportFilter filter, PlayerTransports.TickChanges ticks)
	{
		return new NoRouteExplainer(pf, sailing, boats, data, transports, player, sx, sy, sp, targets, excluded, leftOut,
			filter, ticks).explain();
	}

	/** At most this many ways needing as many things are shown, the first included. */
	public static final int MAX_WAYS = 4;

	/** "A way there by boat needs 2 things you can't use yet:". */
	static String byBoatLine(int things, String to)
	{
		return "A way " + to + " by boat needs " + (things == 1 ? "1 thing" : things + " things") + " you can't use yet:";
	}
}
