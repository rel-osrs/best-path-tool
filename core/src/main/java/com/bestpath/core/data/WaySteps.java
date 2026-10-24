package com.bestpath.core.data;

import com.bestpath.core.path.Route;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What one transport of a "no route" way needs that the player can't use now: the distinct things the searches count
 * ({@link #things}), and the way's steps with why each is out ({@link NoRouteReasons.WayStep}).
 */
final class WaySteps
{
	private WaySteps()
	{
	}

	/** A way's transports (walks left out), each with why it is out. */
	static List<NoRouteReasons.WayStep> stepsOf(FewestThings.Way w, List<PlayerTransports.Candidate> cands, boolean[] blocked,
		PlayerState player, Predicate<String> ex, PluginRouter.TransportFilter fl, PlayerTransports transports, PluginData data,
		int penalty)
	{
		List<NoRouteReasons.WayStep> steps = new ArrayList<>();
		for (Route.Step s : w.route.getSteps())
		{
			Integer k = s.getTransport() == null ? null : w.index.get(s.getTransport());
			if (k == null)
			{
				continue; // a walk
			}
			steps.add(step(cands.get(k), s, blocked[k], player, ex, fl, transports, data.getTables(), penalty));
		}
		return steps;
	}

	/** Each step of the way the player can't use, as one line ({@link NoRouteReasons.WayStep#describe()}). */
	static List<String> blockedLines(List<NoRouteReasons.WayStep> steps)
	{
		List<String> out = new ArrayList<>();
		for (NoRouteReasons.WayStep s : steps)
		{
			if (s.isBlocked())
			{
				out.add(s.describe());
			}
		}
		return out;
	}

	/**
	 * The distinct things a transport the player can't use now needs, as keys: each requirement leaf not met ("req
	 * Agility 30"), met only from the bank ("bank ..."), its cooldown, and (by its id) skipped or blacklisted, or
	 * turned off by the filter. The same leaf of two transports is one thing.
	 */
	static Set<String> things(PlayerTransports.Candidate c, PlayerState player, Predicate<String> ex,
		PluginRouter.TransportFilter fl, PlayerTransports transports, Tables tables, Map<Requirement, List<String>> byRequirement)
	{
		Set<String> out = new LinkedHashSet<>();
		Transport t = c.getTransport();
		String id = t.getId();
		String base = id.endsWith(":back") ? id.substring(0, id.length() - 5) : id;
		if (c.getOutcome() != Check.Outcome.MET)
		{
			// a network ride: both stops' things (each stop's requirement worked out once)
			List<Requirement> reqs = new ArrayList<>(2);
			TransportLine line = c.getLine();
			reqs.add(transports.requirement(tables.networkRequirement(line.getNetwork(),
				id.endsWith(":back") ? line.requirementsEndToStart() : line.getRequirements())));
			TransportLine from = c.getFromStop();
			if (from != null)
			{
				reqs.add(transports.requirement(tables.networkRequirement(from.getNetwork(), from.getRequirements())));
			}
			for (Requirement req : reqs)
			{
				if (req == null)
				{
					continue;
				}
				out.addAll(byRequirement.computeIfAbsent(req, k ->
				{
					List<Requirement> unmet = new ArrayList<>();
					List<Requirement> bank = new ArrayList<>();
					NoRouteReasons.collect(k, player, unmet, new ArrayList<>(), bank, new ArrayList<>());
					List<String> keys = new ArrayList<>();
					for (Requirement r : unmet)
					{
						keys.add("req " + r.toText());
					}
					for (Requirement r : bank)
					{
						keys.add("bank " + r.toText());
					}
					return keys;
				}));
			}
		}
		if (c.isCoolingDown())
		{
			Cooldown cd = cooldownOf(c, player);
			out.add("cooldown " + (cd == null ? base : cd.name()));
		}
		if (isExcluded(ex, id))
		{
			out.add("excluded " + base);
		}
		if (fl.excluded(t, c.getLine()))
		{
			out.add("filter " + base);
		}
		if (out.isEmpty())
		{
			out.add("transport " + base);
		}
		return out;
	}

	/** A candidate's requirement for one way (with its network's; a ride: both stops'), or null when unreadable. */
	static Requirement requirementOf(PlayerTransports.Candidate c, boolean reverse, PlayerTransports transports, Tables tables)
	{
		TransportLine line = c.getLine();
		Requirement req = transports.requirement(tables.networkRequirement(line.getNetwork(),
			reverse ? line.requirementsEndToStart() : line.getRequirements()));
		TransportLine from = c.getFromStop();
		if (from != null)
		{
			// a network ride: both stops' requirements
			req = merge(transports.requirement(tables.networkRequirement(from.getNetwork(), from.getRequirements())), req);
		}
		return req;
	}

	/** One transport step of the way, with its reasons. */
	private static NoRouteReasons.WayStep step(PlayerTransports.Candidate c, Route.Step s, boolean blocked, PlayerState player,
		Predicate<String> ex, PluginRouter.TransportFilter fl, PlayerTransports transports, Tables tables, int penalty)
	{
		Transport t = c.getTransport();
		TransportLine line = c.getLine();
		int[] tiles = s.getTiles();
		int last = tiles.length == 0 ? -1 : tiles[tiles.length - 1];
		// a two-way transport used from its end back to its start needs its "way back" requirement (a split one: its id says)
		boolean reverse = t.getId().endsWith(":back") || line.getKind() == TransportLine.Kind.TWO_WAY && last != -1
			&& last == Tiles.pack(line.getStartX(), line.getStartY(), line.getStartPlane())
			&& last != Tiles.pack(line.getEndX(), line.getEndY(), line.getEndPlane());
		Requirement req = requirementOf(c, reverse, transports, tables);
		TransportLine from = c.getFromStop();
		List<Requirement> unmet = new ArrayList<>();
		List<String> unmetTexts = new ArrayList<>();
		List<Requirement> bank = new ArrayList<>();
		List<String> bankTexts = new ArrayList<>();
		if (req != null && c.getOutcome() != Check.Outcome.MET)
		{
			NoRouteReasons.collect(req, player, unmet, unmetTexts, bank, bankTexts);
		}
		Set<NoRouteReasons.Why> why = EnumSet.noneOf(NoRouteReasons.Why.class);
		if (blocked)
		{
			if (!unmet.isEmpty() || c.getOutcome() == Check.Outcome.NOT_MET)
			{
				why.add(NoRouteReasons.Why.REQUIREMENTS);
			}
			if (!bank.isEmpty() || c.getOutcome() == Check.Outcome.NEEDS_BANK && unmet.isEmpty())
			{
				why.add(NoRouteReasons.Why.BANK);
			}
			if (fl.excluded(t, line))
			{
				why.add(NoRouteReasons.Why.FILTER);
			}
			if (isExcluded(ex, t.getId()))
			{
				why.add(NoRouteReasons.Why.EXCLUDED);
			}
			if (c.isCoolingDown())
			{
				why.add(NoRouteReasons.Why.COOLDOWN);
			}
		}
		Cooldown cd = c.isCoolingDown() ? cooldownOf(c, player) : null;
		Long ready = cd == null ? null : player.getCoolingDown().get(cd);
		return new NoRouteReasons.WayStep(t, line, from, reverse, t.cost(penalty), tiles, why, req, unmet, unmetTexts, bank,
			bankTexts, cd, ready == null ? 0 : ready);
	}

	/** The cooldown that runs for a cooling-down candidate (its line's, or a ride's stop's). */
	private static Cooldown cooldownOf(PlayerTransports.Candidate c, PlayerState player)
	{
		for (TransportLine l : new TransportLine[]{c.getLine(), c.getFromStop()})
		{
			Cooldown cd = l == null ? null : Cooldown.of(l.isCooldown(), l.getTeleportCategory());
			if (cd != null && !player.cooldownReady(cd))
			{
				return cd;
			}
		}
		return null;
	}

	/** Two requirements together (a ride's two stops), each part once; null (unreadable) counts as met. */
	static Requirement merge(Requirement a, Requirement b)
	{
		if (a == null || a.equals(b))
		{
			return b;
		}
		if (b == null)
		{
			return a;
		}
		List<Requirement> parts = new ArrayList<>();
		for (Requirement r : new Requirement[]{a, b})
		{
			for (Requirement p : r instanceof Requirement.All ? ((Requirement.All) r).getParts() : List.of(r))
			{
				if (!parts.contains(p))
				{
					parts.add(p);
				}
			}
		}
		return parts.isEmpty() ? Requirement.NONE : Requirement.all(parts);
	}

	/** Excluded by id (a split two-way transport's way back goes with its id), as the router leaves them out. */
	static boolean isExcluded(Predicate<String> ex, String id)
	{
		return ex.test(id) || id.endsWith(":back") && ex.test(id.substring(0, id.length() - 5));
	}
}
