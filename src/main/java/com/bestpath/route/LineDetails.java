package com.bestpath.route;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.PlayerTransports;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.player.HouseFurniture;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a transport step's exported line says beyond its title: the card's icons and needs ({@link #info}), what the
 * player clicks ({@link #click}) and a house portal's words for this player's house ({@link #houseMenu}). Requirement
 * lines are read once and kept. Any thread.
 */
final class LineDetails
{
	private final PluginRouter router;
	private final TransportRules rules;
	/** The house table (cache): the portal chamber's portals, for the house portal steps' words. */
	private final HouseFurniture house = HouseFurniture.load();
	/** Parsed requirement lines (with their network's), by text; null = unreadable. Any thread. */
	private final Map<String, Optional<Requirement>> parsed = new ConcurrentHashMap<>();

	LineDetails(PluginRouter router, TransportRules rules)
	{
		this.router = router;
		this.rules = rules;
	}

	/**
	 * What the sidebar needs for a transport step's icons (its sort, what it needs); NONE for walks and sails.
	 * {@code player}: the player the route was searched for ("one of" shows the choice the router used for them).
	 */
	StepInfo info(Route.Step s, PlayerTransports.Result from, PlayerState player)
	{
		Transport t = s.getTransport();
		TransportLine line = t == null || from == null ? null : from.lineOf(t);
		if (line == null)
		{
			return StepInfo.NONE;
		}
		int[] tiles = s.getTiles();
		int last = tiles.length == 0 ? -1 : tiles[tiles.length - 1];
		// a two-way transport used from its end back to its start needs its "way back" requirement
		boolean back = line.getKind() == TransportLine.Kind.TWO_WAY && last != -1
			&& last == Tiles.pack(line.getStartX(), line.getStartY(), line.getStartPlane())
			&& last != Tiles.pack(line.getEndX(), line.getEndY(), line.getEndPlane());
		Tables tables = router.getData().getTables();
		Requirement req = parse(tables.networkRequirement(line.getNetwork(), back ? line.requirementsEndToStart() : line.getRequirements()));
		TransportLine fromStop = fromStop(line, t);
		Requirement fromReq = fromStop == null ? null : parse(tables.networkRequirement(fromStop.getNetwork(), fromStop.getRequirements()));
		if (req != null && fromReq != null && !fromReq.isNone())
		{
			// a network ride needs both stops (a spirit tree at each end: both answers are asked on its card)
			req = Requirement.all(List.of(fromReq, req));
		}
		return StepInfo.of(line, back, req, rules.category(t, line), player, tables);
	}

	/**
	 * What the player clicks for a transport or teleport step, from its line: the menu words its card title came from,
	 * the object the data records, and for an item teleport every item that does it (any charge), for a spell the spell.
	 * {@link ClickHint#NONE} for walks, sails and the sailing steps (no line). {@code menu}: the step's menu words when
	 * already worked out (a house portal's, {@link #houseMenu}).
	 */
	ClickHint click(Route.Step s, PlayerTransports.Result from, RouteStep step, PlayerState player, MenuText menu)
	{
		Transport t = s.getTransport();
		TransportLine line = t == null || from == null ? null : from.lineOf(t);
		if (line == null || s.getKind() == Route.Step.Kind.WALK || s.getKind() == Route.Step.Kind.SAIL)
		{
			return ClickHint.NONE;
		}
		String option = menu == null || menu.isChoiceOnly() ? "" : menu.getOption();
		String target = menu == null || menu.isChoiceOnly() ? "" : menu.getTarget();
		String choice = menu == null ? null : menu.getChoice();
		int[] items = new int[0];
		if (step.getInfo().getItemId() >= 0)
		{
			Requirement req = parse(router.getData().getTables().networkRequirement(line.getNetwork(), line.getRequirements()));
			Requirement.Item main = req == null ? null : StepInfo.firstItemLeaf(req, player);
			items = main == null ? new int[]{step.getInfo().getItemId()} : main.getItemIds();
		}
		boolean spell = line.getKind() == TransportLine.Kind.TELEPORT && items.length == 0
			&& ("spell".equals(step.getInfo().getTeleportCategory()) || "home teleport".equals(step.getInfo().getTeleportCategory()));
		// a network ride (a fairy ring, a spirit tree...) is clicked at the stop it leaves from, not the landing stop
		TransportLine stop = fromStop(line, t);
		TransportLine at = stop != null ? stop : line;
		int objectTile = at.getObjectId() < 0 ? -1 : Tiles.pack(at.getObjectX(), at.getObjectY(), at.getObjectPlane());
		// house furniture glows with text: what the requirement says is built
		Set<String> house = new HashSet<>();
		houseThings(parse(router.getData().getTables().networkRequirement(at.getNetwork(), at.getRequirements())), house);
		// a house portal (nexus or chamber): only the one the step's words name glows
		boolean nexus = menu != null && NEXUS.equals(menu.getTarget());
		Set<String> used = new HashSet<>();
		for (String h : house)
		{
			if (h.startsWith(nexus ? HouseFurniture.NEXUS_PREFIX : CHAMBER_PREFIX))
			{
				used.add(h);
			}
		}
		return new ClickHint(option, target, choice, at.getObjectId(), objectTile, items, spell, step.getMarkerTile(),
			used.isEmpty() ? house : used);
	}

	/** The portal nexus as the menu names it (from the cache); "Teleport Menu" opens its list. */
	static final String NEXUS = "Portal Nexus";
	/** The house things of the portal chamber: "portal chamber: Varrock Teleport". */
	static final String CHAMBER_PREFIX = "portal chamber: ";

	/**
	 * A house portal step's words as this player's house has it: the data's words are the portal nexus's list
	 * ("Portal Nexus -> Senntisten"); with the destination in the player's nexus (the house scan) they get the
	 * player's key ("Portal Nexus -> Senntisten (M)", when the scan knew it); with it only in the portal chamber, the
	 * chamber's portal ("Enter Senntisten Portal"). Not scanned or nothing found: the nexus words without a key. Any
	 * other step: {@code menu} itself.
	 */
	MenuText houseMenu(MenuText menu, TransportLine line, PlayerSnapshot p)
	{
		if (menu == null || line == null || p == null || menu.getStyle() != MenuText.Style.CHOICE
			|| !NEXUS.equals(menu.getTarget()))
		{
			return menu;
		}
		Set<String> things = new HashSet<>();
		houseThings(parse(router.getData().getTables().networkRequirement(line.getNetwork(), line.getRequirements())), things);
		Set<String> built = p.getState().getHouseThings();
		boolean inNexus = false;
		boolean inChamber = false;
		for (String t : things)
		{
			inNexus |= t.startsWith(HouseFurniture.NEXUS_PREFIX) && built.contains(t);
			inChamber |= t.startsWith(CHAMBER_PREFIX) && built.contains(t);
		}
		if (!inNexus && inChamber)
		{
			MenuText c = house.chamberMenu(menu.getChoice());
			return c == null ? menu : c;
		}
		String key = inNexus ? p.getNexusKeys().get(menu.getChoice()) : null;
		return key == null ? menu : menu.withKey(key);
	}

	/** The "house has ..." things anywhere in the requirement (lower case). */
	private static void houseThings(Requirement r, Set<String> out)
	{
		if (r instanceof Requirement.House && ((Requirement.House) r).getKind() == Requirement.House.Kind.HAS)
		{
			out.add(((Requirement.House) r).getWhat().toLowerCase(Locale.ROOT));
		}
		else if (r instanceof Requirement.All)
		{
			for (Requirement p : ((Requirement.All) r).getParts())
			{
				houseThings(p, out);
			}
		}
		else if (r instanceof Requirement.AnyOf)
		{
			for (Requirement p : ((Requirement.AnyOf) r).getAlternatives())
			{
				houseThings(p, out);
			}
		}
	}

	/** A requirement line read once (kept); null when it can't be read. */
	private Requirement parse(String text)
	{
		Tables tables = router.getData().getTables();
		return parsed.computeIfAbsent(text, k ->
		{
			try
			{
				return Optional.of(RequirementText.parse(k, tables.itemGroups()));
			}
			catch (RequirementText.ParseException e)
			{
				return Optional.empty();
			}
		}).orElse(null);
	}

	/** A network ride's stop it leaves from (the same network's stop at the ride's start), or null. */
	private TransportLine fromStop(TransportLine to, Transport ride)
	{
		if (to.getKind() != TransportLine.Kind.NETWORK || !ride.hasStart())
		{
			return null;
		}
		for (TransportLine l : router.getData().getTransports())
		{
			if (l != to && l.getKind() == TransportLine.Kind.NETWORK && l.getNetwork().equals(to.getNetwork())
				&& l.getEndX() == ride.getStartX() && l.getEndY() == ride.getStartY() && l.getEndPlane() == ride.getStartPlane())
			{
				return l;
			}
		}
		return null;
	}
}
