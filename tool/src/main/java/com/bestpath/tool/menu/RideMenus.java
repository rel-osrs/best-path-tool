package com.bestpath.tool.menu;

import static com.bestpath.tool.menu.DestinationMatch.afterColon;
import static com.bestpath.tool.menu.DestinationMatch.best;
import static com.bestpath.tool.menu.DestinationMatch.destination;
import static com.bestpath.tool.menu.MenuMatcher.twoWay;
import static com.bestpath.tool.menu.MenuMatcher.unknown;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.MenuTextBuilder;
import com.bestpath.tool.edit.TransportRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The menu texts of networks ({@link MenuMatcher}): the click at a network stop or a ride's start (a placed object,
 * or a Shortest Path row's NPC there, checked in the cache) and the choice in the network's list that names the
 * destination.
 */
final class RideMenus
{
	/** A stop of a network is this close (tiles) to the object clicked there. */
	static final int STOP_RADIUS = 4;
	/** A Shortest Path row for a stop or ride starts at most this far from ours (its NPC stands near the stop). */
	static final int STOP_HINT_RADIUS = 6;

	private static final Pattern FAIRY_CODE = Pattern.compile("\\b([A-D][I-L][P-S])\\b");

	/** Options that open a network's destination list, best first. */
	private static final List<String> OPENS_LIST = List.of("configure", "travel", "glider", "fly", "charter", "board", "ride", "use",
		"quick-travel", "teleport", "activate", "touch");

	/** The thing clicked at a stop or a ride's start: its option that opens the list, and the thing itself. */
	static final class Click
	{
		final MenuFacts.Thing thing;
		final String op;
		final String name;
		final String how;

		Click(MenuFacts.Thing thing, String op, String name, String how)
		{
			this.thing = thing;
			this.op = op;
			this.name = name;
			this.how = how;
		}
	}

	private final MenuFacts facts;
	private final List<MenuMatcher.Hint> hints;
	private final HouseMenus house;
	/** Rides per click place (network, object id and start): several destinations chosen after one click. */
	private final Map<String, Integer> ridesPerClick = new HashMap<>();
	/** Network stops by network (a house stop borrows the others' click). */
	private final Map<String, List<TransportRecord>> stops = new HashMap<>();

	RideMenus(MenuFacts facts, List<MenuMatcher.Hint> hints)
	{
		this.facts = facts;
		this.hints = hints;
		this.house = new HouseMenus(facts);
	}

	/** Remembers a network stop (call for every stop before matching any). */
	void addStop(TransportRecord r)
	{
		stops.computeIfAbsent(r.network, k -> new ArrayList<>()).add(r);
	}

	/** Counts a ride for its click place (call for every ride before matching any). */
	void countRide(TransportRecord r)
	{
		ridesPerClick.merge(clickKey(r), 1, Integer::sum);
	}

	/** How many rides start with the same click as this one. */
	int ridesFrom(TransportRecord r)
	{
		return ridesPerClick.getOrDefault(clickKey(r), 0);
	}

	private static String clickKey(TransportRecord r)
	{
		return r.network + "|" + (r.objectId >= 0 ? r.objectId + "@" + r.objectX + "," + r.objectY + "," + r.objectPlane
			: r.startX + "," + r.startY + "," + r.startPlane);
	}

	/** The cache's destination list of a network, by its name (empty for networks without one). */
	List<String> networkList(String network)
	{
		String net = network == null ? "" : network.toLowerCase(Locale.ROOT);
		if (net.startsWith("quetzal"))
		{
			return facts.list(MenuFacts.QUETZALS);
		}
		if (net.startsWith("gnome glider"))
		{
			return facts.list(MenuFacts.GLIDERS);
		}
		if (net.startsWith("charter"))
		{
			return facts.list(MenuFacts.CHARTER);
		}
		if (net.startsWith("canoe"))
		{
			return facts.list(MenuFacts.CANOES);
		}
		return List.of();
	}

	/** The option of a thing that opens its destination list (best of {@link #OPENS_LIST}), or null. */
	static String listOption(MenuFacts.Thing t)
	{
		if (t == null)
		{
			return null;
		}
		List<String> ops = t.ops();
		for (String want : OPENS_LIST)
		{
			for (String op : ops)
			{
				if (op.equalsIgnoreCase(want))
				{
					return op;
				}
			}
		}
		return null;
	}

	/** What is clicked at (x, y, plane): a placed object there with a list option, else a Shortest Path NPC / object there. */
	private Click clickAt(int x, int y, int plane, String network)
	{
		Click best = null;
		int bestDist = Integer.MAX_VALUE;
		for (MenuFacts.Placed p : facts.near(x, y, plane, STOP_RADIUS))
		{
			MenuFacts.Thing t = facts.object(p.id);
			String op = listOption(t);
			if (op != null && p.distance(x, y) < bestDist)
			{
				bestDist = p.distance(x, y);
				best = new Click(t, op, t.nameFor(op), "object " + p.id + " at " + p.x + "," + p.y + "," + p.plane);
			}
		}
		if (best != null)
		{
			return best;
		}
		return hintedClickAt(x, y, plane);
	}

	/**
	 * The nearest Shortest Path row starting at (x, y, plane) whose id the cache confirms: an NPC with that name and
	 * option, or a placed object with them (NPCs stand a few tiles from the stop tile); null for none.
	 */
	private Click hintedClickAt(int x, int y, int plane)
	{
		Click best = null;
		int bestDist = Integer.MAX_VALUE;
		for (MenuMatcher.Hint h : hints)
		{
			int d = h.origin == null || h.origin[2] != plane ? Integer.MAX_VALUE
				: Math.max(Math.abs(h.origin[0] - x), Math.abs(h.origin[1] - y));
			if (d > STOP_HINT_RADIUS || d >= bestDist)
			{
				continue;
			}
			MenuFacts.Thing npc = facts.npcs.get(h.id);
			if (npc != null && npc.ops().contains(h.option) && h.target.equalsIgnoreCase(String.valueOf(npc.nameFor(h.option))))
			{
				bestDist = d;
				best = new Click(npc, h.option, npc.nameFor(h.option), "NPC " + h.id + " (from Shortest Path " + h.file + ", checked in the cache)");
				continue;
			}
			for (MenuFacts.Placed p : facts.near(x, y, plane, STOP_HINT_RADIUS))
			{
				MenuFacts.Thing t = facts.object(p.id);
				if (p.id == h.id && t != null && (t.ops().contains(h.option) || t.conditionalOps().contains(h.option)))
				{
					bestDist = d;
					best = new Click(t, h.option, t.nameFor(h.option), "object " + p.id + " (from Shortest Path " + h.file + ", checked in the cache)");
					break;
				}
			}
		}
		return best;
	}

	/** The menu text of a network stop: the click there and the choice that picks this stop. */
	MenuMatcher.Result networkStop(TransportRecord r)
	{
		List<String> doubts = new ArrayList<>();
		String stop = afterColon(r.name);
		Click own = clickAt(r.endX, r.endY, r.endPlane, r.network);
		Click lists = own == null ? otherStopsClick(r) : own;
		String choice = stop == null ? null : stopChoice(r, stop, lists, doubts);
		if (choice == null)
		{
			String why = lists == null ? "nothing to click found at the stop (no placed object with a travel option, no checked NPC)"
				: "the choice for '" + stop + "' after '" + lists.op + " " + lists.name + "' is not in the cache";
			return unknown(why, doubts);
		}
		if (own != null)
		{
			return new MenuMatcher.Result(MenuTextBuilder.choice(own.op, own.name, choice), null, null, null, doubts, own.how);
		}
		if (lists != null && stop.toLowerCase(Locale.ROOT).contains("your house"))
		{
			doubts.add("in the house (no placed object): the click of the network's other stops is used");
			return new MenuMatcher.Result(MenuTextBuilder.choice(lists.op, lists.name, choice), null, null, null, doubts,
				"as the other stops (" + lists.how + ")");
		}
		doubts.add("nothing to click found at this stop: rides to it have a text, rides from it do not");
		return new MenuMatcher.Result(MenuTextBuilder.choiceOnly(choice), null, null, null, doubts, "choice only");
	}

	/**
	 * The click at the first other stop of the network that has one: its destination texts (a spirit tree's), and in
	 * the house its click too; null for none.
	 */
	private Click otherStopsClick(TransportRecord r)
	{
		for (TransportRecord o : stops.getOrDefault(r.network, List.of()))
		{
			Click c = o == r ? null : clickAt(o.endX, o.endY, o.endPlane, o.network);
			if (c != null)
			{
				return c;
			}
		}
		return null;
	}

	/** The text that picks this stop in the network's list. */
	private String stopChoice(TransportRecord r, String stop, Click click, List<String> doubts)
	{
		String net = r.network.toLowerCase(Locale.ROOT);
		if (net.startsWith("fairy ring"))
		{
			return fairyRingCode(stop, doubts);
		}
		String name = stop.replaceAll("\\s*\\([^)]*\\)$", "");
		List<String> options = new ArrayList<>(networkList(r.network));
		if (click != null)
		{
			options.addAll(facts.listed(click.op, click.name)); // a list written by hand (list-keys.tsv)
			options.addAll(click.thing.subOps(click.op));
			for (String c : click.thing.conditionalOps())
			{
				if (OptionPicker.kindOf(c) == null)
				{
					options.add(c);
				}
			}
		}
		String best = best(name, options, doubts);
		if (best == null && !name.equals(stop))
		{
			best = best(stop, options, doubts);
		}
		return best;
	}

	/** A fairy ring stop's code ("CKS"), when the cache's list has it; null for a sequence of codes or none. */
	private String fairyRingCode(String stop, List<String> doubts)
	{
		Matcher m = FAIRY_CODE.matcher(stop);
		if (stop.matches("^[A-D][I-L][P-S]-.*"))
		{
			doubts.add("a sequence of fairy ring codes");
			return null;
		}
		if (m.find() && facts.list(MenuFacts.FAIRY_RINGS).contains(m.group(1)))
		{
			return m.group(1);
		}
		return null;
	}

	/** A ride (network set, no object): the click at its start and the choice that names its destination. */
	MenuMatcher.Result ride(TransportRecord r)
	{
		List<String> doubts = new ArrayList<>();
		if (r.network.toLowerCase(Locale.ROOT).startsWith("player-owned house"))
		{
			return house.house(r, doubts);
		}
		Click click = clickAt(r.startX, r.startY, r.startPlane, r.network);
		String dest = destination(r);
		if (click == null)
		{
			return new MenuMatcher.Result(null, null, "nothing to click found at the start (no placed object with a travel option, no checked NPC)",
				twoWay(r) ? "not looked for (the start is not known)" : null, doubts, "");
		}
		String choice = dest == null ? null : stopChoice(r, dest, click, doubts);
		MenuText there;
		String why = null;
		if (choice != null)
		{
			there = MenuTextBuilder.choice(click.op, click.name, choice);
		}
		else if (ridesFrom(r) <= 1 && dest != null && click.thing.subOps(click.op).isEmpty())
		{
			// the only ride from this click: the click itself goes there
			there = MenuText.verb(click.op, click.name);
			doubts.add("the only ride from '" + click.op + " " + click.name + "' (a dialogue may still ask where to)");
		}
		else
		{
			there = null;
			why = "the choice for '" + dest + "' after '" + click.op + " " + click.name + "' is not in the cache";
		}
		String whyBack = null;
		MenuText back = null;
		if (twoWay(r))
		{
			Click c = clickAt(r.endX, r.endY, r.endPlane, r.network);
			String from = afterColon(r.name);
			String origin = from == null ? null : from.contains(" <-> ") ? from.substring(0, from.indexOf(" <-> "))
				: from.contains(" to ") ? from.substring(0, from.lastIndexOf(" to ")) : null;
			String cb = c == null || origin == null ? null : stopChoice(r, origin, c, doubts);
			if (cb != null)
			{
				back = MenuTextBuilder.choice(c.op, c.name, cb);
			}
			else
			{
				whyBack = c == null ? "nothing to click found at the end" : "the choice for '" + origin + "' after '" + c.op + " " + c.name
					+ "' is not in the cache";
			}
		}
		return new MenuMatcher.Result(there, back, why, whyBack, doubts, click.how);
	}
}
