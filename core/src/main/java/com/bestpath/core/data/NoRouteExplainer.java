package com.bestpath.core.data;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * One "no route" explanation ({@link NoRouteReasons#explain}), worked out in named steps: the places on the player's
 * land, every transport (those the player can't use now at extra ticks), the places something can stand near, which
 * of them to search, the way needing the fewest things, and that way's steps with the other ways. Used once.
 */
final class NoRouteExplainer
{
	private final Pathfinder pf;
	private final Sailing sailing;
	private final SailingSetup boats;
	private final PluginData data;
	private final PlayerTransports transports;
	private final PlayerState player;
	private final int sx;
	private final int sy;
	private final int sp;
	private final int[] targets;
	private final Predicate<String> leftOut;
	private final PlayerTransports.TickChanges ticks;
	private final Predicate<String> ex;
	private final PluginRouter.TransportFilter fl;
	private final boolean member;
	private final FreeLand land;
	private final int single;
	private final String what;
	private final List<String> reasons = new ArrayList<>();

	/** The places on the player's land (all of them for members). */
	private final List<Integer> onLand = new ArrayList<>();
	private int onMembersLand;
	/** Every transport of this world type; {@link #search} the same with extra ticks on the {@link #blocked} ones. */
	private List<PlayerTransports.Candidate> cands;
	private List<Transport> search;
	private boolean[] blocked;
	private CoarseGraph graph;
	/** The places with something to stand on near them (or a transport landing there). */
	private final List<Integer> standable = new ArrayList<>();
	private boolean anyAtSea;
	/** The places searched: nearest to the player first, one per component, at most {@value NoRouteReasons#MAX_PLACES}. */
	private final List<Integer> pick = new ArrayList<>();
	private final int penalty = PluginRouter.PLANE_CHANGE_PENALTY;
	/** Per candidate: the distinct things the player can't use that it needs ({@link WaySteps#things}). */
	private List<Set<String>> things;
	/** How many transports the player can't use need each thing (the second search's discount). */
	private final Map<String, Integer> shared = new HashMap<>();
	private FewestThings.Way best;
	private int looked;

	NoRouteExplainer(Pathfinder pf, Sailing sailing, SailingSetup boats, PluginData data, PlayerTransports transports,
		PlayerState player, int sx, int sy, int sp, int[] targets, Predicate<String> excluded, Predicate<String> leftOut,
		PluginRouter.TransportFilter filter, PlayerTransports.TickChanges ticks)
	{
		this.pf = pf;
		this.sailing = sailing;
		this.boats = boats;
		this.data = data;
		this.transports = transports;
		this.player = player;
		this.sx = sx;
		this.sy = sy;
		this.sp = sp;
		this.targets = targets;
		this.leftOut = leftOut;
		this.ticks = ticks;
		this.ex = excluded == null ? id -> false : excluded;
		this.fl = filter == null ? PluginRouter.TransportFilter.NONE : filter;
		this.member = player.isMembers();
		this.land = member ? null : data.getFreeLand();
		this.single = targets.length == 1 ? 0 : -1;
		this.what = targets.length == 1 ? "the destination" : "any of the " + targets.length + " places";
	}

	/** The explanation (see {@link NoRouteReasons}). */
	NoRouteReasons explain()
	{
		NoRouteReasons early = membersLand();
		if (early != null)
		{
			return early;
		}
		candidates();
		early = standablePlaces();
		if (early != null)
		{
			return early;
		}
		pickPlaces();
		early = fewestThings();
		if (early != null)
		{
			return early;
		}
		return blockedWay();
	}

	/** 1. A free world: the places on members land are out; none left is {@link NoRouteReasons.Kind#MEMBERS_LAND}. */
	private NoRouteReasons membersLand()
	{
		for (int i = 0; i < targets.length; i++)
		{
			if (land == null || land.isFree(Tiles.x(targets[i]), Tiles.y(targets[i])))
			{
				onLand.add(i);
			}
		}
		if (onLand.isEmpty())
		{
			reasons.add(targets.length == 1 ? "Members only: the destination is not on free-to-play land."
				: "Members only: none of the " + targets.length + " places is on free-to-play land.");
			return new NoRouteReasons(NoRouteReasons.Kind.MEMBERS_LAND, List.of(), List.of(), 0, -1, single, null, !member, false, 0,
				reasons);
		}
		onMembersLand = targets.length - onLand.size();
		return null;
	}

	/** 2. Every transport of this world type; those the player can't use now cost the extra ticks. */
	private void candidates()
	{
		PlayerTransports.Everything all = transports.everything(player, land, ticks);
		cands = all.getCandidates();
		if (leftOut != null)
		{
			List<PlayerTransports.Candidate> kept = new ArrayList<>(cands.size());
			for (PlayerTransports.Candidate c : cands)
			{
				// by its id, or by its card (its line's id: fairy ring DLR from every ring)
				if (!WaySteps.isExcluded(leftOut, c.getTransport().getId()) && (c.getLine() == null || !leftOut.test(c.getLine().getId())))
				{
					kept.add(c);
				}
			}
			cands = kept;
		}
		search = new ArrayList<>(cands.size());
		blocked = new boolean[cands.size()];
		for (int i = 0; i < cands.size(); i++)
		{
			PlayerTransports.Candidate c = cands.get(i);
			Transport t = c.getTransport();
			blocked[i] = !c.isUsableNow() || WaySteps.isExcluded(ex, t.getId()) || fl.excluded(t, c.getLine());
			Transport s = blocked[i] ? t.withTickChange(t.getTickChange() + NoRouteReasons.BLOCKED_EXTRA) : t;
			search.add(s);
		}
	}

	/**
	 * 3. The places with something to stand on near them (or a transport landing there). None: open sea may be
	 * {@link NoRouteReasons.Kind#BY_BOAT}, else {@link NoRouteReasons.Kind#NOT_STANDABLE}.
	 */
	private NoRouteReasons standablePlaces()
	{
		graph = pf.getGraph();
		for (int i : onLand)
		{
			int t = targets[i];
			if (NoRoutePlaces.standable(graph, search, Tiles.x(t), Tiles.y(t), Tiles.plane(t)))
			{
				standable.add(i);
			}
			else
			{
				anyAtSea |= NoRoutePlaces.atSea(data, Tiles.x(t), Tiles.y(t), Tiles.plane(t));
			}
		}
		if (standable.isEmpty() && anyAtSea && member)
		{
			// open sea: a boat can be there
			int at = NoRoutePlaces.firstAtSea(data, targets, onLand);
			SeaWay sea = at < 0 ? null : SeaWay.find(sailing, boats, pf, data, cands, blocked, player, sx, sy, sp, targets[at]);
			if (sea != null)
			{
				return byBoat(sea, at, true, 1);
			}
		}
		if (standable.isEmpty())
		{
			reasons.add("Nowhere to stand: nothing within " + PluginRouter.NEAR_RANGE + " tiles of " + what + " can be walked on"
				+ (!member ? " on free-to-play land" : "") + ".");
			if (anyAtSea)
			{
				reasons.add("It is out at sea: only a boat can be there.");
			}
			if (onMembersLand > 0)
			{
				reasons.add(onMembersLand + (onMembersLand == 1 ? " place is" : " places are") + " on members land.");
			}
			return new NoRouteReasons(NoRouteReasons.Kind.NOT_STANDABLE, List.of(), List.of(), 0, -1, single, null, !member, anyAtSea,
				0, reasons);
		}
		return null;
	}

	/** {@link NoRouteReasons.Kind#BY_BOAT} for the way by boat to place {@code at}. */
	private NoRouteReasons byBoat(SeaWay sea, int at, boolean atSea, int placesLookedAt)
	{
		reasons.add(NoRouteReasons.byBoatLine(sea.getThings(), targets.length == 1 ? "there" : "to the nearest place"));
		for (NoRouteReasons.SailStep s : sea.getSteps())
		{
			reasons.add(s.describe());
		}
		return new NoRouteReasons(NoRouteReasons.Kind.BY_BOAT, List.of(), sea.getSteps(), sea.getThings(), sea.getTicks(),
			targets.length == 1 ? 0 : at, null, !member, atSea, placesLookedAt, reasons);
	}

	/** 4. Which places to search: nearest to the player first, one per component, at most MAX_PLACES. */
	private void pickPlaces()
	{
		List<Integer> order = new ArrayList<>(standable);
		order.sort((a, b) ->
		{
			long da = NoRoutePlaces.distance(sx, sy, sp, targets[a]);
			long db = NoRoutePlaces.distance(sx, sy, sp, targets[b]);
			return da != db ? Long.compare(da, db) : Integer.compare(a, b);
		});
		Set<Integer> places = new LinkedHashSet<>();
		for (int i : order)
		{
			int t = targets[i];
			int c = graph.componentAt(Tiles.x(t), Tiles.y(t), Tiles.plane(t));
			if (places.add(c >= 0 ? c : -1 - t) && pick.size() < NoRouteReasons.MAX_PLACES)
			{
				pick.add(i);
			}
		}
	}

	/**
	 * 5. The way to each with every transport: the fewest distinct things the player can't use (e.g. obstacles all
	 * needing Agility 30 are one thing), then the fewest ticks. No way on land: {@link NoRouteReasons.Kind#BY_BOAT} or
	 * {@link NoRouteReasons.Kind#NO_KNOWN_WAY}.
	 */
	private NoRouteReasons fewestThings()
	{
		things = new ArrayList<>(cands.size());
		// many transports share one parsed requirement: its things are worked out once (short-lived memory)
		Map<Requirement, List<String>> byRequirement = new IdentityHashMap<>();
		// a card counts as one thing, however many of its needs are missing; transports needing exactly the same things
		// still count once together (e.g. the Agility Pyramid's obstacles)
		Map<Set<String>, Set<String>> asOne = new HashMap<>();
		for (int i = 0; i < cands.size(); i++)
		{
			Set<String> th = blocked[i]
				? WaySteps.things(cands.get(i), player, ex, fl, transports, data.getTables(), byRequirement) : Set.of();
			things.add(th.size() <= 1 ? th : asOne.computeIfAbsent(th, k -> Set.of(String.join(" + ", new TreeSet<>(k)))));
		}
		for (Set<String> th : things)
		{
			for (String t : th)
			{
				shared.merge(t, 1, Integer::sum);
			}
		}
		for (int i : pick)
		{
			FewestThings.Way w = FewestThings.fewestThings(pf, data, cands, blocked, things, shared, i, sx, sy, sp, targets[i], penalty,
				Set.of());
			if (w != null && w.betterThan(best))
			{
				best = w;
			}
		}
		looked = pick.size();
		return best == null ? noWayOnLand() : null;
	}

	/** No way on land: maybe one by boat, else no known way. */
	private NoRouteReasons noWayOnLand()
	{
		SeaWay sea = SeaWay.find(sailing, boats, pf, data, cands, blocked, player, sx, sy, sp, targets[pick.get(0)]);
		if (sea != null)
		{
			return byBoat(sea, pick.get(0), false, looked);
		}
		reasons.add("No known way there: the map data has no way to " + what + (!member ? " on free-to-play land" : "")
			+ ", even with every transport.");
		if (graph.componentAt(sx, sy, sp) < 0 && NoRoutePlaces.atSea(data, sx, sy, sp))
		{
			reasons.add("You are at sea: ways by boat are not looked at here.");
		}
		if (onMembersLand > 0)
		{
			reasons.add(onMembersLand + (onMembersLand == 1 ? " place is" : " places are") + " on members land.");
		}
		if (standable.size() > looked)
		{
			reasons.add("Looked at the nearest " + looked + " of " + standable.size() + " places.");
		}
		return new NoRouteReasons(NoRouteReasons.Kind.NO_KNOWN_WAY, List.of(), List.of(), 0, -1, single, null, !member, false, looked,
			reasons);
	}

	/** 6. The way's transports and why each is out; other ways needing as many things. */
	private NoRouteReasons blockedWay()
	{
		List<NoRouteReasons.WayStep> steps = WaySteps.stepsOf(best, cands, blocked, player, ex, fl, transports, data, penalty);
		List<String> blockedLines = WaySteps.blockedLines(steps);
		NoRouteReasons.Kind kind = blockedLines.isEmpty() ? NoRouteReasons.Kind.REACHABLE : NoRouteReasons.Kind.BLOCKED;
		String to = targets.length == 1 ? "there" : "to the nearest place";
		List<List<NoRouteReasons.WayStep>> others = new ArrayList<>();
		if (kind == NoRouteReasons.Kind.BLOCKED)
		{
			for (FewestThings.Way w : FewestThings.otherWays(best, pf, data, cands, blocked, things, shared, sx, sy, sp,
				targets[best.place], penalty))
			{
				others.add(WaySteps.stepsOf(w, cands, blocked, player, ex, fl, transports, data, penalty));
			}
			String n = best.blocked == 1 ? "1 thing" : best.blocked + " things";
			if (others.isEmpty())
			{
				reasons.add("The way " + to + " needs " + n + " you can't use yet:");
				reasons.addAll(blockedLines);
			}
			else
			{
				reasons.add("There are " + (others.size() + 1) + " ways " + to + ", each needing " + n + " you can't use yet:");
				reasons.addAll(blockedLines);
				for (List<NoRouteReasons.WayStep> o : others)
				{
					reasons.add("or");
					reasons.addAll(WaySteps.blockedLines(o));
				}
			}
		}
		else
		{
			reasons.add("A way " + to + " exists with what you can use now; search again.");
		}
		return new NoRouteReasons(kind, steps, others, List.of(), kind == NoRouteReasons.Kind.BLOCKED ? best.blocked : 0, best.ticks,
			targets.length == 1 ? 0 : best.place, best.stop, !member, false, looked, reasons);
	}
}
