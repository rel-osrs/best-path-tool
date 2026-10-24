package com.bestpath.tool.menu;

import com.bestpath.tool.transport.CandidateExtractor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which option of an object or NPC the player clicks to go one way: what each option does ({@link Kind}), which way
 * a transport goes ({@link Dir}) and the option that fits both ({@link #pick}).
 */
final class OptionPicker
{
	private OptionPicker()
	{
	}

	/** What an option does (lower-case word lists, as the transport candidates' categories). */
	enum Kind
	{
		UP, DOWN, CLIMB, SHORTCUT, PASS, ENTER, EXIT, TRAVEL, OPEN
	}

	private static final Map<String, Kind> KINDS = new HashMap<>();

	static
	{
		words(Kind.UP, "climb-up", "go-up", "walk-up", "jump-up", "climb up");
		words(Kind.DOWN, "climb-down", "go-down", "walk-down", "jump-down", "climb down");
		words(Kind.CLIMB, "climb");
		words(Kind.SHORTCUT, "squeeze-through", "jump", "jump-over", "jump-across", "jump-to", "jump-on", "jump-from", "cross",
			"balance", "balance-across", "swing", "swing-on", "swing-across", "crawl-through", "vault", "grapple", "hop",
			"hop-across", "step-over", "leap", "climb-over", "climb-through", "climb-across", "climb-on", "walk-across",
			"run-across", "tightrope", "teeth-grip", "squeeze-past", "pass-through", "squeeze-under", "crawl-under",
			"walk-along", "jump-off", "slide", "duck-under", "go-under", "manoeuvre-past", "swing across", "climb through",
			"jump on", "jump-onto", "climb-off", "walk-on", "jump off", "swing-across", "squeeze through");
		words(Kind.PASS, "pass", "go-past", "pay-toll", "pay-toll(10gp)");
		words(Kind.ENTER, "enter", "go-through", "walk-through", "climb-into", "go-in", "jump-in", "jump-into", "slide-down",
			"crawl-into", "squeeze-into", "descend", "walk-into", "dive", "jump-down-into");
		words(Kind.EXIT, "exit", "leave", "climb-out", "go-out", "ascend", "crawl-out");
		words(Kind.TRAVEL, "board", "travel", "ride", "fly", "sail", "quick-travel", "last-destination", "teleport", "zanaris",
			"charter", "dock", "configure", "glider", "use", "paddle", "pay-fare", "quick-board", "embark", "row",
			"activate", "touch", "set-sail", "paddle canoe");
		words(Kind.OPEN, "open", "pull", "push", "operate", "unlock", "pick-lock", "picklock", "knock-at", "slash");
	}

	private static void words(Kind k, String... w)
	{
		for (String s : w)
		{
			KINDS.put(s, k);
		}
	}

	/** The kind of an option ("Climb-up" UP, "Squeeze-through" SHORTCUT), or null for anything else. */
	static Kind kindOf(String op)
	{
		return op == null ? null : KINDS.get(op.toLowerCase(Locale.ROOT).trim());
	}

	/** Which way the player goes: up a plane (or out of an underground area), down, or level. */
	enum Dir
	{
		UP, DOWN, FLAT
	}

	static Dir dir(int fromY, int fromPlane, int toY, int toPlane)
	{
		if (toPlane != fromPlane)
		{
			return toPlane > fromPlane ? Dir.UP : Dir.DOWN;
		}
		boolean fromUnder = fromY >= 6400;
		boolean toUnder = toY >= 6400;
		if (fromUnder != toUnder)
		{
			return toUnder ? Dir.DOWN : Dir.UP;
		}
		return Dir.FLAT;
	}

	/** An option picked on a thing: the option, the name shown with it, how well it fit (0 best), loose = no fit. */
	static final class Pick
	{
		final String op;
		final String name;
		final int rank;
		final boolean loose;

		Pick(String op, String name, int rank, boolean loose)
		{
			this.op = op;
			this.name = name;
			this.rank = rank;
			this.loose = loose;
		}
	}

	/** The way back of a category: entering becomes leaving, climbing up climbing down. */
	static String backCategory(String category)
	{
		switch (category == null ? "" : category)
		{
			case "ENTER":
				return "EXIT";
			case "EXIT":
				return "ENTER";
			case "CLIMB_UP":
				return "CLIMB_DOWN";
			case "CLIMB_DOWN":
				return "CLIMB_UP";
			default:
				return category;
		}
	}

	/**
	 * The option groups to try, best first, for a direction and the transport's category: a shortcut's, barrier's or
	 * ride's own kind first (the same option both ways: "Cross Gangplank" up onto a ship and back down), then the
	 * direction's (up: "Climb-up", "Climb", leaving; down: "Climb-down", "Climb", entering), then the category's.
	 */
	static List<Set<Kind>> preferences(Dir d, String category)
	{
		List<Set<Kind>> p = new ArrayList<>();
		String c = category == null ? "" : category;
		switch (c)
		{
			case "SHORTCUT":
				p.add(Set.of(Kind.SHORTCUT));
				break;
			case "PASS":
				p.add(Set.of(Kind.PASS));
				break;
			case "TRAVEL":
				p.add(Set.of(Kind.TRAVEL));
				break;
			default:
				break;
		}
		switch (d)
		{
			case UP:
				p.add(Set.of(Kind.UP));
				p.add(Set.of(Kind.CLIMB));
				p.add(Set.of(Kind.EXIT));
				break;
			case DOWN:
				p.add(Set.of(Kind.DOWN));
				p.add(Set.of(Kind.CLIMB));
				p.add(Set.of(Kind.ENTER));
				break;
			case FLAT:
			default:
				addLevelPreferences(p, c);
				break;
		}
		if (c.equals("PASS") || c.equals("ENTER") || c.equals("EXIT") || c.equals("TRAVEL"))
		{
			p.add(Set.of(Kind.OPEN)); // a door, gate or lever
		}
		return p;
	}

	/** The option groups of a level transport's category. */
	private static void addLevelPreferences(List<Set<Kind>> p, String c)
	{
		switch (c)
		{
			case "ENTER":
				p.add(Set.of(Kind.ENTER));
				break;
			case "EXIT":
				p.add(Set.of(Kind.EXIT));
				break;
			case "CLIMB_UP":
				p.add(Set.of(Kind.UP));
				p.add(Set.of(Kind.CLIMB));
				break;
			case "CLIMB_DOWN":
				p.add(Set.of(Kind.DOWN));
				p.add(Set.of(Kind.CLIMB));
				break;
			case "CLIMB_BOTH":
			case "SHORTCUT":
				p.add(Set.of(Kind.CLIMB));
				break;
			default:
				break;
		}
	}

	/**
	 * The option for a direction on a thing: the first option (plain options first, then conditional ones) of the
	 * best preferred kind; with {@code strict} false also any transport-like option (marked loose), then a door-like
	 * option, then the only option there is.
	 */
	static Pick pick(MenuFacts.Thing t, Dir d, String category, boolean strict)
	{
		if (t == null)
		{
			return null;
		}
		List<Set<Kind>> prefs = preferences(d, category);
		if (!strict)
		{
			// the transport's own object with one transport option: that is what the player clicks, whatever the way
			Pick only = onlyTransportOption(t);
			if (only != null)
			{
				return only;
			}
		}
		Pick preferred = preferredOption(t, prefs);
		if (preferred != null)
		{
			return preferred;
		}
		return strict ? partnerFallback(t, d, prefs.size()) : ownFallback(t, d, prefs.size());
	}

	/** The thing's one plain transport option (not a door's), or null when it has none or several. */
	private static Pick onlyTransportOption(MenuFacts.Thing t)
	{
		String only = null;
		int count = 0;
		for (String op : t.ops())
		{
			Kind k = kindOf(op);
			if (k != null && k != Kind.OPEN)
			{
				only = op;
				count++;
			}
		}
		return count == 1 ? new Pick(only, t.nameFor(only), 0, false) : null;
	}

	/** The first option of the best preferred kind: plain options first, then conditional ones; or null. */
	private static Pick preferredOption(MenuFacts.Thing t, List<Set<Kind>> prefs)
	{
		List<String> plain = t.ops();
		List<String> cond = t.conditionalOps();
		for (int rank = 0; rank < prefs.size(); rank++)
		{
			for (List<String> ops : List.of(plain, cond))
			{
				for (String op : ops)
				{
					Kind k = kindOf(op);
					if (k != null && prefs.get(rank).contains(k))
					{
						return new Pick(op, t.nameFor(op), rank, false);
					}
				}
			}
		}
		return null;
	}

	/** A partner object without a preferred option: any transport option that fits the way, or a shut trapdoor's "Open". */
	private static Pick partnerFallback(MenuFacts.Thing t, Dir d, int rank)
	{
		List<String> plain = t.ops();
		// a partner must at least have a transport option that is not a door's (and not the opposite climb)
		for (String op : plain)
		{
			Kind k = kindOf(op);
			if (k != null && k != Kind.OPEN && !opposite(d, k) && !(d == Dir.FLAT && (k == Kind.UP || k == Kind.DOWN)))
			{
				return new Pick(op, t.nameFor(op), rank, true);
			}
		}
		// a shut trapdoor or hatch on the way up or down: the first click opens it
		String name = t.name();
		if (d != Dir.FLAT && name != null && CandidateExtractor.verticalName(name))
		{
			for (String op : plain)
			{
				if (kindOf(op) == Kind.OPEN)
				{
					return new Pick(op, t.nameFor(op), rank + 1, true);
				}
			}
		}
		return null;
	}

	/** The transport's own object without a preferred option: any transport option, then a door's, then its only option. */
	private static Pick ownFallback(MenuFacts.Thing t, Dir d, int rank)
	{
		List<String> plain = t.ops();
		for (Kind want : new Kind[]{null, Kind.OPEN})
		{
			for (String op : plain)
			{
				Kind k = kindOf(op);
				if (k != null && (want == null ? k != Kind.OPEN && !opposite(d, k) : k == want))
				{
					return new Pick(op, t.nameFor(op), rank, true);
				}
			}
		}
		if (plain.size() == 1 && !LOOK_ONLY.contains(plain.get(0).toLowerCase(Locale.ROOT)))
		{
			return new Pick(plain.get(0), t.nameFor(plain.get(0)), rank + 1, true);
		}
		return null;
	}

	/** Options that only look at something: never taken as a transport's click ("Look-inside Mole hill": a spade digs). */
	private static final Set<String> LOOK_ONLY = Set.of("look-inside", "look-at", "look", "inspect", "investigate", "read", "examine",
		"check", "study", "talk-to", "peek");

	private static boolean opposite(Dir d, Kind k)
	{
		return d == Dir.UP && k == Kind.DOWN || d == Dir.DOWN && k == Kind.UP;
	}
}
