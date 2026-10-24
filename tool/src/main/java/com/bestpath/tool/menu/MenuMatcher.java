package com.bestpath.tool.menu;

import static com.bestpath.tool.menu.OptionPicker.backCategory;
import static com.bestpath.tool.menu.OptionPicker.dir;
import static com.bestpath.tool.menu.OptionPicker.pick;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.MenuTextBuilder;
import com.bestpath.core.data.MenuTextDiagnostics;
import com.bestpath.core.req.Requirement;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.menu.OptionPicker.Dir;
import com.bestpath.tool.menu.OptionPicker.Pick;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Works out what the player clicks in the game to use a transport: the exact text of the menu click, from the game
 * cache's facts ({@link MenuFacts}), as a {@link MenuText} per direction. Never guesses a text the cache does not
 * have: anything it can't find is left unknown with the reason.
 *
 * <p>Rules (docs/DATA_FORMATS.md, "menu"):
 * <ul>
 * <li><b>Objects</b> (a transport with an object): the object's name and the option for the direction: up a plane
 * "Climb-up" / "Go-up" ..., down "Climb-down" ..., into a cave (y from below 6400 to above) like down, out of one
 * like up, otherwise the option of the transport's category (shortcut, pass, enter, exit, travel), else its only
 * transport option. The way back of a two-way transport uses the same object when its end is next to the object on
 * the same plane, else the nearest placed object near the end with an option for that direction (the other end of
 * a ladder or staircase, a cave's exit), the same name first.</li>
 * <li><b>Spells</b>: the spell's name (spell obj param 601); "Cast", or the spellbook button's other option when it
 * names the destination ("Grand Exchange", "Seers'", "Yanille", "Outside").</li>
 * <li><b>Items</b> (the item ids of the requirement, first that fits): an inventory sub-menu option, inventory
 * option, worn option or worn sub-menu option that names the destination; else the item's one teleport verb
 * ("Break", "Commune", "Teleport"...) when the item has only this destination.</li>
 * <li><b>Network stops</b>: the click at the stop (a placed object near the stop, or the Shortest Path row's NPC
 * there, checked in the cache) and the choice that leads to this stop: fairy ring codes, quetzal sites, glider
 * names, charter ports from the cache's tables; spirit trees' destinations from their "Last-destination" option
 * texts.</li>
 * <li><b>Rides out of the player's house</b>: the jewellery box's and the portal nexus's list entries, mounted
 * jewellery's options.</li>
 * <li>Shortest Path rows ({@code data/shortest-path}) are hints only: an NPC id or object id at a transport's start,
 * used only after the cache confirms the name and the option; and a cross-check of object options.</li>
 * </ul>
 *
 * <p>Objects and Shortest Path hints are matched here; teleports in {@link TeleportMenus}, network stops and rides in
 * {@link RideMenus} (the house in {@link HouseMenus}). {@link OptionPicker} chooses an object's option for a
 * direction, {@link DestinationMatch} the option text that names a destination.
 */
public final class MenuMatcher
{
	/** An end this close to an object's footprint (same plane) uses that object. */
	static final int NEAR = 2;
	/** Partner objects (the other end of a ladder, a cave's exit) are looked for this far from the end. */
	static final int PARTNER_RADIUS = 6;
	/** A Shortest Path row's start within this many tiles of ours is the same place. */
	static final int HINT_RADIUS = 3;

	/** A Shortest Path row with a menu column ("Climb-down Ladder 16679"): a hint, never ground truth. */
	public static final class Hint
	{
		public final String file;
		public final int[] origin;
		public final int[] destination;
		public final String option;
		public final String target;
		public final int id;

		public Hint(String file, int[] origin, int[] destination, String option, String target, int id)
		{
			this.file = file;
			this.origin = origin;
			this.destination = destination;
			this.option = option;
			this.target = target;
			this.id = id;
		}

		/** Reads "option target... id"; null when the text is not that shape. */
		public static Hint parse(String file, int[] origin, int[] destination, String menu)
		{
			if (menu == null)
			{
				return null;
			}
			String[] w = menu.trim().split("\\s+");
			if (w.length < 3)
			{
				return null;
			}
			try
			{
				int id = Integer.parseInt(w[w.length - 1]);
				return new Hint(file, origin, destination, w[0], String.join(" ", java.util.Arrays.asList(w).subList(1, w.length - 1)), id);
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}
	}

	/** The menu texts of one transport, with why a direction is unknown and what is doubtful. */
	public static final class Result
	{
		/** Start to end (null = unknown). */
		public final MenuText there;
		/** End to start, two-way only (null = unknown or not two-way). */
		public final MenuText back;
		/** Why start to end is unknown (null when known). */
		public final String whyThere;
		/** Two-way: why end to start is unknown (null when known or not two-way). */
		public final String whyBack;
		/** Things a person should check (empty = none). */
		public final List<String> doubts;
		/** Where the texts came from, in short ("object 52614 Climb-up; back: object 52615 at 1552,3033,1"). */
		public final String how;

		Result(MenuText there, MenuText back, String whyThere, String whyBack, List<String> doubts, String how)
		{
			this.there = there;
			this.back = back;
			this.whyThere = whyThere;
			this.whyBack = whyBack;
			this.doubts = List.copyOf(doubts);
			this.how = how;
		}
	}

	/** A one-way result without a text: why it is unknown. */
	static Result unknown(String why, List<String> doubts)
	{
		return new Result(null, null, why, null, doubts, "");
	}

	private final MenuFacts facts;
	private final List<Hint> hints;
	private final TeleportMenus teleports;
	private final RideMenus rides;

	/**
	 * @param requirements reads a requirement line (with the item groups), or throws
	 * @param hints        Shortest Path rows with a menu column (hints only)
	 * @param all          every transport to be matched (to count destinations per item and per click)
	 */
	public MenuMatcher(MenuFacts facts, Function<String, Requirement> requirements, List<Hint> hints, Collection<TransportRecord> all)
	{
		this.facts = facts;
		this.hints = hints;
		this.teleports = new TeleportMenus(facts, requirements);
		this.rides = new RideMenus(facts, hints);
		for (TransportRecord r : all)
		{
			if (r.kind == TransportRecord.Kind.TELEPORT)
			{
				teleports.count(r);
			}
			else if (r.kind == TransportRecord.Kind.NETWORK)
			{
				rides.addStop(r);
			}
			else if (!r.network.isEmpty())
			{
				rides.countRide(r);
			}
		}
	}

	/** The menu texts of one transport. */
	public Result match(TransportRecord r)
	{
		switch (r.kind)
		{
			case TELEPORT:
				return teleports.match(r);
			case NETWORK:
				return rides.networkStop(r);
			default:
				if (r.objectId >= 0)
				{
					return object(r);
				}
				if (!r.network.isEmpty())
				{
					return rides.ride(r);
				}
				return hinted(r);
		}
	}

	static boolean twoWay(TransportRecord r)
	{
		return r.kind == TransportRecord.Kind.TWO_WAY;
	}

	// ------------------------------------------------------------------------------------------------ objects

	/** One direction's menu text, or why it is unknown. */
	private static final class Way
	{
		final MenuText text;
		final String why;

		Way(MenuText text, String why)
		{
			this.text = text;
			this.why = why;
		}
	}

	/** The record's own object as placed (its footprint), or a 1-tile one at the record's object tile. */
	private MenuFacts.Placed self(TransportRecord r)
	{
		for (MenuFacts.Placed p : facts.near(r.objectX, r.objectY, r.objectPlane, 0))
		{
			if (p.id == r.objectId && p.x == r.objectX && p.y == r.objectY)
			{
				return p;
			}
		}
		return new MenuFacts.Placed(r.objectId, r.objectX, r.objectY, r.objectPlane, 1, 1);
	}

	private Result object(TransportRecord r)
	{
		List<String> doubts = new ArrayList<>();
		MenuFacts.Thing t = facts.object(r.objectId);
		if (t == null || t.name() == null)
		{
			String why = t == null ? "object " + r.objectId + " is not in the cache" : "object " + r.objectId + " has no name in the cache";
			return new Result(null, null, why, twoWay(r) ? why : null, doubts, "");
		}
		MenuFacts.Placed self = self(r);
		boolean atStart = self.plane == r.startPlane && self.distance(r.startX, r.startY) <= NEAR;
		boolean atEnd = self.plane == r.endPlane && self.distance(r.endX, r.endY) <= NEAR;
		StringBuilder how = new StringBuilder();
		Way there = objectThere(r, t, self, atStart, atEnd, how, doubts);
		crossCheck(r.objectId, r.startX, r.startY, r.startPlane, r.endX, r.endY, r.endPlane, there.text, doubts);
		Way back = twoWay(r) ? objectBack(r, t, atEnd, how, doubts) : new Way(null, null);
		return new Result(there.text, back.text, there.why, back.why, doubts, how.toString());
	}

	/** Start to end: the object's option, or a partner's near the start when the object is at the end. */
	private Way objectThere(TransportRecord r, MenuFacts.Thing t, MenuFacts.Placed self, boolean atStart, boolean atEnd, StringBuilder how,
		List<String> doubts)
	{
		Dir d = dir(r.startY, r.startPlane, r.endY, r.endPlane);
		Way there = atStart || !atEnd ? ownObjectThere(r, t, self, d, atStart, how, doubts) : partnerThere(r, t, d, how, doubts);
		if (there.text != null && !r.network.isEmpty() && rides.ridesFrom(r) > 1)
		{
			// one click, several destinations: the destination is chosen afterwards
			MenuText c = choiceAfter(t, there.text, r.network, DestinationMatch.destination(r), doubts);
			if (c == null)
			{
				return new Way(null, "destination chosen in a list or dialogue after '" + MenuTextDiagnostics.menuLine(there.text)
					+ "'; its text is not in the cache");
			}
			return new Way(c, null);
		}
		return there;
	}

	/** Start to end on the transport's own object. */
	private Way ownObjectThere(TransportRecord r, MenuFacts.Thing t, MenuFacts.Placed self, Dir d, boolean atStart, StringBuilder how,
		List<String> doubts)
	{
		Pick p = pick(t, d, r.category, false);
		if (p == null)
		{
			return new Way(null, "object " + r.objectId + " '" + t.name() + "' has no option for this way (" + String.join(", ", t.ops()) + ")");
		}
		how.append("object ").append(r.objectId).append(' ').append(p.op);
		if (p.loose)
		{
			doubts.add("'" + p.op + " " + p.name + "': option chosen without a match for the way (" + d + ", " + r.category + ")");
		}
		if (!atStart)
		{
			doubts.add("the object is " + (self.plane != r.startPlane ? "on another plane than" : self.distance(r.startX, r.startY) + " tiles from")
				+ " the start");
		}
		return new Way(MenuText.verb(p.op, p.name), null);
	}

	/** Start to end when the object is at the end: a partner object near the start. */
	private Way partnerThere(TransportRecord r, MenuFacts.Thing t, Dir d, StringBuilder how, List<String> doubts)
	{
		Partner pt = partner(r.startX, r.startY, r.startPlane, d, r.category, t.name(), doubts);
		if (pt == null)
		{
			return new Way(null, "the object is at the end; no object near the start with an option for this way");
		}
		how.append("object ").append(pt.placed.id).append(" near the start ").append(pt.pick.op);
		return new Way(MenuText.verb(pt.pick.op, pt.pick.name), null);
	}

	/** End to start of a two-way transport: the same object when it is at the end, else a partner near the end. */
	private Way objectBack(TransportRecord r, MenuFacts.Thing t, boolean atEnd, StringBuilder how, List<String> doubts)
	{
		Dir b = dir(r.endY, r.endPlane, r.startY, r.startPlane);
		Way back = atEnd ? sameObjectBack(r, t, b, how, doubts) : partnerBack(r, t, b, how, doubts);
		crossCheck(-1, r.endX, r.endY, r.endPlane, r.startX, r.startY, r.startPlane, back.text, doubts);
		return back;
	}

	private Way sameObjectBack(TransportRecord r, MenuFacts.Thing t, Dir b, StringBuilder how, List<String> doubts)
	{
		Pick p = pick(t, b, backCategory(r.category), false);
		if (p == null)
		{
			return new Way(null, "object " + r.objectId + " '" + t.name() + "' has no option for the way back");
		}
		how.append("; back: same object ").append(p.op);
		if (p.loose)
		{
			doubts.add("back '" + p.op + " " + p.name + "': option chosen without a match for the way (" + b + ")");
		}
		return new Way(MenuText.verb(p.op, p.name), null);
	}

	private Way partnerBack(TransportRecord r, MenuFacts.Thing t, Dir b, StringBuilder how, List<String> doubts)
	{
		Partner pt = partner(r.endX, r.endY, r.endPlane, b, backCategory(r.category), t.name(), doubts);
		if (pt == null)
		{
			return new Way(null, "no object near the end (" + PARTNER_RADIUS + " tiles, plane " + r.endPlane + ") with an option for the way back");
		}
		how.append("; back: object ").append(pt.placed.id).append(" at ").append(pt.placed.x).append(',').append(pt.placed.y)
			.append(',').append(pt.placed.plane).append(' ').append(pt.pick.op);
		if (pt.pick.loose)
		{
			doubts.add("back '" + pt.pick.op + " " + pt.pick.name + "': option chosen without a match for the way (" + b + ")");
		}
		return new Way(MenuText.verb(pt.pick.op, pt.pick.name), null);
	}

	/** A placed object near a tile with an option for a direction. */
	static final class Partner
	{
		final MenuFacts.Placed placed;
		final Pick pick;

		Partner(MenuFacts.Placed placed, Pick pick)
		{
			this.placed = placed;
			this.pick = pick;
		}
	}

	/** The nearest placed object near (x, y, plane) with an option for the direction; the same name first when as near. */
	private Partner partner(int x, int y, int plane, Dir d, String category, String name, List<String> doubts)
	{
		Partner best = null;
		int bestScore = Integer.MAX_VALUE;
		Set<String> tied = new LinkedHashSet<>();
		for (MenuFacts.Placed p : facts.near(x, y, plane, PARTNER_RADIUS))
		{
			MenuFacts.Thing t = facts.object(p.id);
			Pick k = pick(t, d, category, true);
			if (k == null)
			{
				continue;
			}
			// how well the option fits first, then how near, then the same name
			int score = k.rank * 6 + p.distance(x, y) * 4 + (k.name != null && k.name.equals(name) ? 0 : 5);
			String text = k.op + " " + k.name;
			if (score < bestScore)
			{
				bestScore = score;
				best = new Partner(p, k);
				tied.clear();
				tied.add(text);
			}
			else if (score == bestScore)
			{
				tied.add(text);
			}
		}
		if (tied.size() > 1)
		{
			doubts.add("several objects as near: " + String.join(" / ", tied));
		}
		return best;
	}

	/**
	 * Shortest Path's option for the same way (its row starts near ours and ends near ours; the same object when
	 * known, else the same name), when it differs from ours: a doubt.
	 */
	private void crossCheck(int objectId, int x, int y, int plane, int toX, int toY, int toPlane, MenuText ours, List<String> doubts)
	{
		if (ours == null || ours.getStyle() != MenuText.Style.VERB)
		{
			return;
		}
		for (Hint h : hints)
		{
			if (h.origin == null || h.destination == null || h.origin[2] != plane || h.destination[2] != toPlane
				|| Math.max(Math.abs(h.origin[0] - x), Math.abs(h.origin[1] - y)) > HINT_RADIUS
				|| Math.max(Math.abs(h.destination[0] - toX), Math.abs(h.destination[1] - toY)) > HINT_RADIUS
				|| objectId >= 0 && h.id != objectId || objectId < 0 && !h.target.equalsIgnoreCase(ours.getTarget()))
			{
				continue;
			}
			if (!h.option.equalsIgnoreCase(ours.getOption()) || !h.target.equalsIgnoreCase(ours.getTarget()))
			{
				doubts.add("Shortest Path (" + h.file + ") says '" + h.option + " " + h.target + "'");
				hintsDiffer++;
			}
			else
			{
				hintsAgree++;
			}
			return;
		}
	}

	/** Object menu texts that Shortest Path's rows for the same way agree / disagree with (a cross-check only). */
	private int hintsAgree;
	private int hintsDiffer;

	public int getHintsAgree()
	{
		return hintsAgree;
	}

	public int getHintsDiffer()
	{
		return hintsDiffer;
	}

	/**
	 * After one click, the choice that names {@code dest}: the thing's sub-menu or conditional option texts, or the
	 * network's list in the cache (canoes: the canoe map's names).
	 */
	private MenuText choiceAfter(MenuFacts.Thing t, MenuText click, String network, String dest, List<String> doubts)
	{
		if (dest == null)
		{
			return null;
		}
		List<String> options = new ArrayList<>(t.subOps(click.getOption()));
		for (String c : t.conditionalOps())
		{
			if (OptionPicker.kindOf(c) == null)
			{
				options.add(c);
			}
		}
		options.addAll(rides.networkList(network));
		options.addAll(facts.listed(click.getOption(), click.getTarget()));
		String best = DestinationMatch.best(dest.replaceAll("\\s*\\([^)]*\\)$", ""), options, doubts);
		if (best != null && network.toLowerCase(Locale.ROOT).startsWith("canoe"))
		{
			doubts.add("a log canoe says 'Paddle Log' (the other canoes '" + click.getOption() + "')");
		}
		return best == null ? null : MenuTextBuilder.choice(click.getOption(), click.getTarget(), best);
	}

	// ------------------------------------------------------------------------------------------------ the rest

	/** A transport without object or network: a Shortest Path row at the same place, checked in the cache. */
	private Result hinted(TransportRecord r)
	{
		List<String> doubts = new ArrayList<>();
		MenuText there = hintAt(r.startX, r.startY, r.startPlane, r.endX, r.endY, r.endPlane);
		MenuText back = twoWay(r) ? hintAt(r.endX, r.endY, r.endPlane, r.startX, r.startY, r.startPlane) : null;
		String why = "no object or NPC in the data" + (r.category.equals("CUSTOM") ? " (a custom transport)" : "")
			+ " and no checked Shortest Path row at the start";
		if (there != null || back != null)
		{
			doubts.add("object or NPC id from Shortest Path (checked in the cache)");
		}
		return new Result(there, back, there == null ? why : null, twoWay(r) && back == null ? why : null, doubts,
			there != null || back != null ? "Shortest Path id, checked in the cache" : "");
	}

	private MenuText hintAt(int sx, int sy, int sp, int ex, int ey, int ep)
	{
		for (Hint h : hints)
		{
			if (h.origin == null || h.destination == null || h.origin[2] != sp || h.destination[2] != ep
				|| Math.max(Math.abs(h.origin[0] - sx), Math.abs(h.origin[1] - sy)) > HINT_RADIUS
				|| Math.max(Math.abs(h.destination[0] - ex), Math.abs(h.destination[1] - ey)) > HINT_RADIUS)
			{
				continue;
			}
			for (MenuFacts.Placed p : facts.near(sx, sy, sp, PARTNER_RADIUS))
			{
				MenuFacts.Thing t = facts.object(p.id);
				if (p.id == h.id && t != null && t.ops().contains(h.option))
				{
					return MenuText.verb(h.option, t.nameFor(h.option));
				}
			}
			MenuFacts.Thing npc = facts.npcs.get(h.id);
			if (npc != null && npc.ops().contains(h.option) && h.target.equalsIgnoreCase(String.valueOf(npc.nameFor(h.option))))
			{
				return MenuText.verb(h.option, npc.nameFor(h.option));
			}
		}
		return null;
	}
}
