package com.bestpath.tool.route;

import com.bestpath.core.path.Transport;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.req.Networks;
import com.bestpath.tool.transport.CacheTableTeleports;
import com.bestpath.tool.transport.Suggestion;
import com.bestpath.tool.transport.TransportCandidate;
import com.bestpath.tool.transport.TransportData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Turns the tool's transports into the pathfinder's: the confirmed ones
 * (records), and optionally every undecided suggestion (candidates' best
 * suggestions, suggested teleports and network stops), marked unconfirmed.
 * Network stops become one ride from every stop to every other stop of the
 * same network.
 */
public final class RouteTransports
{
	/**
	 * Default ticks for a transport whose cost nobody has set yet: 10 for entering, exiting, passing, travel (boats)
	 * and teleports, 7 for custom transports, 5 for the rest.
	 */
	public static int defaultTicks(String category)
	{
		if (category == null)
		{
			return 5;
		}
		switch (category)
		{
			case "ENTER":
			case "EXIT":
			case "PASS":
			case "TRAVEL":
			case "TELEPORT":
				return 10;
			case "CUSTOM":
				return 7;
			default:
				return 5;
		}
	}

	/** The extra ticks for a transport that changes plane, until the user changes it. */
	public static final int DEFAULT_PLANE_CHANGE_PENALTY = 5;

	/** Where a player stands to use an object: the tile next to it nearest the end tile. */
	public interface StandingTile
	{
		int[] of(TransportCandidate c, int towardX, int towardY);
	}

	/** One network stop: its tile, the ticks to ride to it, and whether it is confirmed. */
	private static final class Stop
	{
		final String name;
		final int x;
		final int y;
		final int plane;
		final int ticks;
		final boolean confirmed;
		/** What using this stop needs from the bank (empty: nothing). */
		final List<com.bestpath.core.req.BankItem> bank;

		Stop(String name, int x, int y, int plane, int ticks, boolean confirmed)
		{
			this(name, x, y, plane, ticks, confirmed, List.of());
		}

		Stop(String name, int x, int y, int plane, int ticks, boolean confirmed, List<com.bestpath.core.req.BankItem> bank)
		{
			this.name = name;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.ticks = ticks;
			this.confirmed = confirmed;
			this.bank = bank;
		}
	}

	private RouteTransports()
	{
	}

	/**
	 * The transports for a search, every one usable.
	 *
	 * @param records       transports in use (confirmed and pending, not deleted)
	 * @param testRecords   transports on the test list (used as unconfirmed, only with {@code unconfirmed})
	 * @param data          candidates and suggestions (null: none)
	 * @param unconfirmed   also use every undecided suggestion
	 * @param skip          candidates to leave out (decided, ignored region...)
	 * @param skipTeleport  suggested teleports to leave out, by id (decided...)
	 */
	public static List<Transport> build(Collection<TransportRecord> records, Collection<TransportRecord> testRecords,
		TransportData data, boolean unconfirmed, Predicate<TransportCandidate> skip, Predicate<String> skipTeleport,
		StandingTile standing)
	{
		return build(records, testRecords, data, unconfirmed, skip, skipTeleport, standing, null);
	}

	/**
	 * As above, for one player: {@code needs} leaves out what the player can't use (null: everything is usable,
	 * quest-only transports included). Transports usable only with items from the bank are in the list too, and
	 * {@code needs} knows which ({@link RequirementFilter#itemsFromBank}, {@link RequirementFilter#carried}): a route
	 * may begin with a bank visit.
	 */
	public static List<Transport> build(Collection<TransportRecord> records, Collection<TransportRecord> testRecords,
		TransportData data, boolean unconfirmed, Predicate<TransportCandidate> skip, Predicate<String> skipTeleport,
		StandingTile standing, RequirementFilter needs)
	{
		List<Transport> out = new ArrayList<>();
		Map<String, List<Stop>> networks = new LinkedHashMap<>();
		for (TransportRecord r : records)
		{
			add(r, true, out, networks, needs);
		}
		if (unconfirmed)
		{
			for (TransportRecord r : testRecords)
			{
				add(r, false, out, networks, needs);
			}
		}
		if (unconfirmed && data != null)
		{
			addSuggestions(data, skip, standing, out);
			addSuggestedTeleports(data, skipTeleport, out, networks);
		}
		addRides(networks, out, needs);
		return out;
	}

	/** Each undecided candidate's best suggestion, unconfirmed. */
	private static void addSuggestions(TransportData data, Predicate<TransportCandidate> skip, StandingTile standing, List<Transport> out)
	{
		for (TransportCandidate c : data.getCandidates())
		{
			List<Suggestion> s = data.suggestionsFor(c);
			if (s.isEmpty() || skip.test(c))
			{
				continue;
			}
			Suggestion best = s.get(0);
			int[] st = best.getStart() != null ? best.getStart() : standing.of(c, best.getDestX(), best.getDestY());
			if (st == null)
			{
				continue; // nowhere usable to stand next to the object
			}
			// crossings (shortcuts, barriers) work both ways; ladders etc. have their own object at each end
			boolean twoWay = c.getCategory() == TransportCandidate.Category.SHORTCUT
				|| c.getCategory() == TransportCandidate.Category.PASS;
			out.add(Transport.between("candidate:" + c.id(), c.getName() + " (unconfirmed)", st[0], st[1], st[2],
				best.getDestX(), best.getDestY(), best.getDestPlane(), defaultTicks(c.getCategory().name()), twoWay, false));
		}
	}

	/** The suggested teleports not decided yet, unconfirmed: network stops go to {@code networks}. */
	private static void addSuggestedTeleports(TransportData data, Predicate<String> skipTeleport, List<Transport> out,
		Map<String, List<Stop>> networks)
	{
		for (CacheTableTeleports.Destination d : data.getDestinations())
		{
			if (skipTeleport.test(d.id()))
			{
				continue;
			}
			if (d.isNetwork)
			{
				networks.computeIfAbsent(d.network, k -> new ArrayList<>())
					.add(new Stop(d.network + ": " + d.name, d.x, d.y, d.plane, defaultTicks("TRAVEL"), false));
			}
			else
			{
				out.add(Transport.teleport(d.id(), d.network + ": " + d.name + " (unconfirmed)", d.x, d.y, d.plane,
					defaultTicks("TELEPORT"), false));
			}
		}
	}

	/** A ride from every stop of a network to every other stop; a ride costs the destination stop's ticks. */
	private static void addRides(Map<String, List<Stop>> networks, List<Transport> out, RequirementFilter needs)
	{
		networks.forEach((net, stops) ->
		{
			for (Stop a : stops)
			{
				for (Stop b : stops)
				{
					if (a != b && (a.x != b.x || a.y != b.y || a.plane != b.plane))
					{
						boolean confirmed = a.confirmed && b.confirmed;
						// a stop on a solid tile (the middle of a fairy ring) is used from beside it
						Transport t = Transport.between(net + ":" + a.name + ">" + b.name, b.name + (confirmed ? "" : " (unconfirmed)"),
							a.x, a.y, a.plane, b.x, b.y, b.plane, b.ticks, false, confirmed).usedFromBeside();
						out.add(t);
						// a ride needs what both stops need from the bank (as the plugin's PlayerTransports)
						if (needs != null && (!a.bank.isEmpty() || !b.bank.isEmpty()))
						{
							needs.needsBank(t, bothBanks(a, b));
						}
					}
				}
			}
		});
	}

	private static List<com.bestpath.core.req.BankItem> bothBanks(Stop a, Stop b)
	{
		List<com.bestpath.core.req.BankItem> both = new ArrayList<>(a.bank);
		for (com.bestpath.core.req.BankItem s : b.bank)
		{
			if (!both.contains(s))
			{
				both.add(s);
			}
		}
		return both;
	}

	/** A teleport's ticks: its category's adjustable cost when it has a known category, else its own. */
	static int teleportTicks(TransportRecord r)
	{
		com.bestpath.tool.req.TeleportCategories.Category c = com.bestpath.tool.req.TeleportCategories.get(r.teleportCategory);
		return c != null ? c.ticks : r.ticks;
	}

	/** A ride's ticks: its own plus its network's adjustable extra. */
	static int rideTicks(TransportRecord r)
	{
		return r.ticks + Networks.extraTicks(r.network);
	}

	/** Adds a transport; one usable only with items from the bank is marked as such on {@code needs}. */
	private static void add(List<Transport> out, RequirementFilter needs, com.bestpath.core.req.Check c, Transport t)
	{
		out.add(t);
		List<com.bestpath.core.req.BankItem> b = RequirementFilter.bankItems(c);
		if (needs != null && !b.isEmpty())
		{
			needs.needsBank(t, b);
		}
	}

	/**
	 * Adds a record as the search's transport (a network stop goes to {@code networks}), unless the player can't use
	 * it; a two-way one whose directions differ for this player becomes one transport per usable direction.
	 */
	private static void add(TransportRecord r, boolean confirmed, List<Transport> out, Map<String, List<Stop>> networks,
		RequirementFilter needs)
	{
		String suffix = confirmed ? "" : " (unconfirmed)";
		com.bestpath.core.req.Check there = com.bestpath.core.req.Check.MET;
		com.bestpath.core.req.Check back = com.bestpath.core.req.Check.MET;
		if (needs != null)
		{
			if (r.questOnly)
			{
				needs.leftOut(r);
				return;
			}
			if (!needs.teleportWorksHere(r) || !needs.cooldownReady(r))
			{
				return;
			}
			// a transport of a network also needs the network's requirement
			there = needs.check(Networks.requirement(r.network, r.requirements));
			back = r.kind == TransportRecord.Kind.TWO_WAY ? needs.check(Networks.requirement(r.network, r.requirementsEndToStart())) : there;
			if (!RequirementFilter.usable(there) && !RequirementFilter.usable(back))
			{
				needs.leftOut(r);
				return;
			}
		}
		switch (r.kind)
		{
			case NETWORK:
				networks.computeIfAbsent(r.network, k -> new ArrayList<>())
					.add(new Stop(r.name, r.endX, r.endY, r.endPlane, rideTicks(r), confirmed, RequirementFilter.bankItems(there)));
				break;
			case TELEPORT:
				add(out, needs, there, Transport.teleport(r.id, r.name + suffix, r.endX, r.endY, r.endPlane, teleportTicks(r), confirmed));
				break;
			default:
				boolean twoWay = r.kind == TransportRecord.Kind.TWO_WAY;
				boolean forward = RequirementFilter.usable(there);
				boolean backward = twoWay && RequirementFilter.usable(back);
				if (forward && (backward || !twoWay) && there.getOutcome() == back.getOutcome())
				{
					add(out, needs, there, Transport.between(r.id, r.name + suffix, r.startX, r.startY, r.startPlane,
						r.endX, r.endY, r.endPlane, rideTicks(r), twoWay, confirmed).forBoats(r.largestBoat));
					break;
				}
				// the two directions differ for this player: one transport per usable direction
				if (twoWay && forward != backward)
				{
					needs.oneWayNow();
				}
				if (forward)
				{
					add(out, needs, there, Transport.between(r.id, r.name + suffix, r.startX, r.startY, r.startPlane,
						r.endX, r.endY, r.endPlane, rideTicks(r), false, confirmed).forBoats(r.largestBoat));
				}
				if (backward)
				{
					add(out, needs, back, Transport.between(r.id + ":back", r.name + suffix, r.endX, r.endY, r.endPlane,
						r.startX, r.startY, r.startPlane, rideTicks(r), false, confirmed).forBoats(r.largestBoat));
				}
				break;
		}
	}
}
