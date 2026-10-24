package com.bestpath.core.path;

import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaGraph;
import com.bestpath.core.sea.SeaRouter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The search of a route that may sail ({@link Sailing}): the walking searches to the docks and shipwrights, each
 * boat's sea search, and the steps of the route found. Keeps what one route has worked out (the boat-only transports
 * it may sail through, the ticks to each shipwright and the walks from them) and counts its work for diagnostics.
 * Not thread-safe: use from one search thread.
 */
final class SailingSearch
{
	/** Ticks to the docks are not searched beyond this when no land route bounds them. */
	private static final int LIMIT = 20_000;
	private static final int NONE = Sailing.NONE;
	/** Ticks not searched yet (a boarding's, a dock's or a shipwright's). */
	static final int UNSEARCHED = -2;
	/** How many searches to docks one at a time before the rest all at once ({@link #searchDocksAtOnce}): for a few, one each is quicker. */
	private static final int AT_ONCE_AFTER = 3;
	private static final SeaRouter.End NO_END = new SeaRouter.End(List.of(), 0);

	private final SailingWorld world;
	private final List<Dock> docks;
	private final List<List<SeaGraph.Join>> dockJoins;
	private final List<int[]> land;
	private final List<int[]> wrightStand;
	private final SeaGraph seaGraph;
	private final SeaRouter router;

	/** This route's boat-only transports the sea search may use, whichever boat. */
	private List<SeaLink> links = List.of();
	/** The last route's walking searches to docks, boardings a lower bound ruled out, and sea search states. */
	int dockSearches;
	int skipped;
	int seaStates;
	/** This route's ticks from the start to each dock's shipwright (UNSEARCHED / -1 / ticks). */
	private int[] toWright;
	/** This route's walk from each dock's shipwright to the dock (null: not searched yet). */
	private Route[] wrightWalk;
	/** Nanoseconds the last route spent on its walking route (the first search of {@link #route}; 0 when it was given). */
	private long lastWalkNanos;
	/** For {@link #routeFromBanks}: the bounds its ticks to the docks are searched with, all at once ({@link #searchDocksAtOnce}). */
	private Pathfinder.StartBounds docksAtOnce;

	SailingSearch(SailingWorld world)
	{
		this.world = world;
		this.docks = world.docks;
		this.dockJoins = world.dockJoins;
		this.land = world.land;
		this.wrightStand = world.wrightStand;
		this.seaGraph = world.seaGraph;
		this.router = world.router;
	}

	/** How long the last route's walking route took (0 when it was given). */
	long lastWalkNanos()
	{
		return lastWalkNanos;
	}

	/**
	 * The best route on from the banks that may sail ({@link BankFirst}; e.g. bank, then a teleport to the dock where
	 * the boat is moored, then sail): {@code from} starts off the walking map with each bank as a teleport at its head
	 * start and the teleports shifted to the nearest bank's head start (the greater teleport focus's ones among them,
	 * {@link Sailing#focusTeleports}: none are added here); the player is on no boat. Only routes of fewer than {@code beat}
	 * ticks; not found when there is none. Its walking route (from the banks, without a boat) was weighed already, so
	 * it is not searched first.
	 */
	Route routeFromBanks(PathQuery from, Sailing.Options o, Pathfinder pf, int beat, Pathfinder.StartBounds bounds)
	{
		docksAtOnce = beat < NONE ? bounds : null;
		try
		{
			// the searches are not limited to beat: a sea leg the sea search puts at beat or more may still be drawn quicker
			return route(from, o, pf::find, pf, false, beat, Route.notFound(0, 0), true);
		}
		finally
		{
			docksAtOnce = null;
		}
	}

	/**
	 * The ticks from the start to every dock and every shipwright's place at once (for the route on from the banks,
	 * whose start is every bank and every teleport: one walking search per dock is slow there): one search, aimed by
	 * {@code bounds} (lower bounds to the destination that count each sea leg at its least, {@link Sailing#seaBounds}),
	 * looking only for those that can still lead to a route of fewer than {@code best} ticks: a dock when its ticks,
	 * boarding and the least sea leg on from it (to another dock, leaving, the least walk on) are under it; a
	 * shipwright's place when its ticks, the shipwright bringing the boat, boarding and that are. Each one that can
	 * gets its exact ticks (within {@code limit}), the others -1: they can never be part of such a route. The same
	 * ticks as searching each one when needed ({@link #readyToBoard}), in one search; those searched already keep
	 * theirs.
	 */
	private void searchDocksAtOnce(PathQuery qx, Sailing.Options o, int[] onFoot, boolean[] dead, int[] toDock, Pathfinder pf, int best,
		int limit, Pathfinder.StartBounds bounds)
	{
		int n = docks.size();
		int[] seaOn = new int[n];
		Arrays.fill(seaOn, NONE);
		for (Boat b : o.boats)
		{
			double perTile = 100 / b.speed() - 0.125;
			List<SeaLink> mine = linksFor(b);
			for (int k = 0; k < n; k++)
			{
				for (int j = 0; j < n; j++)
				{
					if (j == k || !SailingWorld.usable(docks.get(j), o) || dead != null && dead[j] || onFoot[j] >= NONE)
					{
						continue;
					}
					long sail = world.dockSail(k, j, perTile, mine);
					if (sail >= SailingBounds.FAR)
					{
						continue;
					}
					seaOn[k] = (int) Math.min(seaOn[k], sail / 100 + o.boardTicks + onFoot[j]);
				}
			}
		}
		Map<Integer, Integer> need = new LinkedHashMap<>();
		for (int k = 0; k < n; k++)
		{
			if (seaOn[k] < NONE)
			{
				need.merge(pack(land.get(k)), o.boardTicks + seaOn[k], Math::min);
				if (wrightStand.get(k) != null)
				{
					need.merge(pack(wrightStand.get(k)), Sailing.RECOVER_TICKS + o.boardTicks + seaOn[k], Math::min);
				}
			}
		}
		int[] tiles = new int[need.size()];
		int[] after = new int[need.size()];
		int i = 0;
		for (Map.Entry<Integer, Integer> e : need.entrySet())
		{
			tiles[i] = e.getKey();
			after[i++] = e.getValue();
		}
		int[] ticks = tiles.length == 0 ? new int[0] : pf.ticksTo(qx, tiles, after, NONE, best, null, bounds, 0);
		dockSearches++;
		Map<Integer, Integer> found = new HashMap<>();
		for (i = 0; i < tiles.length; i++)
		{
			found.put(tiles[i], ticks[i] >= 0 && ticks[i] <= limit ? ticks[i] : -1);
		}
		for (int k = 0; k < n; k++)
		{
			if (toDock[k] == UNSEARCHED)
			{
				toDock[k] = found.getOrDefault(pack(land.get(k)), -1); // those searched already stay
			}
			if (wrightStand.get(k) != null && toWright[k] == UNSEARCHED)
			{
				toWright[k] = found.getOrDefault(pack(wrightStand.get(k)), -1);
			}
		}
	}

	/** {@code fromBanks}: {@code q} starts off the map at the banks ({@link #routeFromBanks}). */
	Route route(PathQuery q, Sailing.Options o, Function<PathQuery, Route> finder, Pathfinder reach, boolean destSea, int beat,
		Route knownWalk, boolean fromBanks)
	{
		return new Run(q, o, finder, reach, destSea, fromBanks).search(beat, knownWalk);
	}

	/**
	 * One route's search ({@link #route}) in the three parts {@link Sailing} describes: the question, and what the
	 * search has worked out so far. Used once.
	 */
	private final class Run
	{
		private final PathQuery q;
		private final Sailing.Options o;
		/** The walking search. */
		private final Function<PathQuery, Route> finder;
		/** The walking search's own coarse checks (null: none, only {@link #finder}). */
		private final Pathfinder reach;
		/** The destination is open sea (the route may end on the boat). */
		private final boolean destSea;
		/** {@link #q} starts off the map at the banks ({@link #routeFromBanks}). */
		private final boolean fromBanks;
		private long t0;
		/** The boat the player is on at the start (null: none), and whether the route must keep it. */
		private Boat aboard;
		private boolean keep;
		/** The question with a greater teleport focus's teleports, and its transports. */
		private PathQuery qx;
		private List<Transport> all;
		/** The walking route, and the ticks a sea route has to beat. */
		private Route walk;
		private int best;
		/** Per dock: the ticks from the start ({@link #UNSEARCHED} until needed), searched up to {@link #limit}. */
		private int[] toDock;
		private int limit;
		/** Per dock: a lower bound of the ticks from the start, and of the walk on to the destination (null: at sea). */
		private int[] lower;
		private int[] onFoot;
		/** Per dock: nothing leads from it to the destination (null: not known). */
		private boolean[] dead;
		/** Per boat: its boardings, its ends and the graph pieces of each end. */
		private final List<List<SeaBoarding>> boardingsOf = new ArrayList<>();
		private final List<List<SeaRouter.End>> endsOf = new ArrayList<>();
		private final List<List<int[]>> endPiecesOf = new ArrayList<>();
		/** The best sea leg to each dock, and to the destination at sea. */
		private SeaLeg[] arriveAt;
		private SeaLeg toDest;
		/** The ticks to the docks were searched all at once ({@link #searchDocksAtOnce}). */
		private boolean atOnce;

		Run(PathQuery q, Sailing.Options o, Function<PathQuery, Route> finder, Pathfinder reach, boolean destSea, boolean fromBanks)
		{
			this.q = q;
			this.o = o;
			this.finder = finder;
			this.reach = reach;
			this.destSea = destSea;
			this.fromBanks = fromBanks;
		}

		Route search(int beat, Route knownWalk)
		{
			t0 = System.nanoTime();
			if (!start(beat, knownWalk))
			{
				return walk.isFound() ? new Route(true, walk.getTicks(), walk.getSteps(), walk.getExpanded(), System.nanoTime() - t0) : walk;
			}
			planBoats();
			sail();
			return finish();
		}

		/**
		 * The walking route, and the lower bounds that aim part 1 and the sea search. False when no sea route can help
		 * at all (the walking route is the answer).
		 */
		private boolean start(int beat, Route knownWalk)
		{
			dockSearches = 0;
			skipped = 0;
			seaStates = 0;
			toWright = new int[docks.size()];
			Arrays.fill(toWright, UNSEARCHED);
			wrightWalk = new Route[docks.size()];
			// the boat-only transports the sea search may go through
			links = world.links(q.getBoatTransports(), q.getPlaneChangePenalty());
			aboard = fromBanks ? null : world.aboardAt(q, o);
			// "never leave the boat at sea" (a setting): only routes that start by sailing the boat the player is on
			keep = o.keepBoat && aboard != null;
			// greater teleport focus: a teleport to the boat's dock (from the banks: already among q's, shifted)
			qx = fromBanks ? q : world.withFocus(q, o, aboard);
			all = qx.getTransports();
			// walking only (bounds everything else); kept on the boat: not a route (it would leave the boat at sea)
			long w0 = System.nanoTime();
			walk = knownWalk != null ? knownWalk : finder.apply(qx);
			lastWalkNanos = knownWalk != null ? 0 : System.nanoTime() - w0;
			best = walk.isFound() && !keep ? walk.getTicks() : NONE;
			boolean noWalk = best >= NONE;
			if (!keep)
			{
				best = Math.min(best, beat); // only sea routes quicker than the route already found matter
			}
			// 1. ticks from the start until the boat is ready at each dock it can be boarded at: searched only when a sea
			// leg that may end the route needs them (see below); until then the lower bounds (no search) stand in
			toDock = new int[docks.size()];
			Arrays.fill(toDock, UNSEARCHED);
			limit = Math.min(best, LIMIT);
			lower = SailingBounds.lowerBounds(qx, land);
			// the least the walk on from each dock to the destination can take (no search): aims the sea search
			onFoot = destSea ? null : SailingBounds.lowerBoundsOn(qx, land);
			if (onFoot != null && reach != null && !keep)
			{
				// the walk on from where the boat is left (part 3) never teleports (the search takes teleports only at the
				// start: one used later is always quicker straight away), so its least ticks come from a coarse search
				// without them; much sharper than lowerBoundsOn, which lets every dock teleport
				sharpenOnFoot(qx, onFoot, reach, limit);
			}
			// no walk at all: only docks from which the destination can be reached on foot are worth sailing to (a leg to
			// another dock can never be part of a route). None, and no destination at sea: no sailing at all
			dead = noWalk && !destSea && reach != null ? deadDocks(qx, reach) : null;
			return !(dead != null && allTrue(dead) && !keep);
		}

		/**
		 * The boardings of every boat that may still give a leg ending the route before the walk does (see useless()),
		 * the destination at sea for each boat, and each boat's ends: the docks the player may leave the boat at
		 * (level, quest) from which the destination may be reached (none when the destination is at sea: no walk ends
		 * there) and the destination at sea; each says what at least follows it (leaving, the least walk on).
		 */
		private void planBoats()
		{
			boolean bottles = Sailing.bottlesInHand(o.player, o.bottledBoats);
			for (Boat b : o.boats)
			{
				// the boat ends only on open sea (the destination under its hull) or at a dock
				int[] destTile = destSea ? world.destinationFit(q.getDestX(), q.getDestY(), b) : null;
				List<SeaGraph.Join> destJoins = destTile == null ? List.of() : seaGraph.join(new int[][]{destTile});
				int[] destPieces = world.piecesOf(destJoins);
				List<SeaBoarding> boardings = new ArrayList<>();
				for (SeaBoarding bd : keep && b != aboard ? List.<SeaBoarding>of() : world.boardings(b, b == aboard, bottles, o))
				{
					if (bd.dock >= 0 && (destSea && destTile == null
						|| best < NONE && useless(bd, lower[bd.dock], b, best, destTile, destPieces, onFoot, dead, o)))
					{
						skipped++;
						continue;
					}
					if (bd.dock >= 0 && lower[bd.dock] >= NONE)
					{
						bd.ready = -1; // the dock can't be reached at all (not even by the lower bounds' rules)
					}
					boardings.add(bd);
				}
				List<SeaRouter.End> ends = new ArrayList<>();
				List<int[]> endPieces = new ArrayList<>();
				for (int k = 0; k < docks.size(); k++)
				{
					// kept on the boat (a setting): it is left only at a dock with a shipwright; the sea
					// search stops at the best end, so the rule is in its ends, not applied afterwards
					boolean end = !destSea && SailingWorld.usable(docks.get(k), o) && (dead == null || !dead[k]) && onFoot[k] < NONE
						&& (!keep || docks.get(k).getShipwright() != null);
					ends.add(new SeaRouter.End(end ? dockJoins.get(k) : List.of(), end ? (o.boardTicks + onFoot[k]) * 100 : 0));
					endPieces.add(end ? world.dockPieces(k) : null);
				}
				ends.add(new SeaRouter.End(destJoins, 0));
				endPieces.add(destTile == null ? null : destPieces);
				boardingsOf.add(boardings);
				endsOf.add(ends);
				endPiecesOf.add(endPieces);
			}
		}

		/**
		 * 2. Per boat, one sea search from its boardings to its ends. A boarding starts at its lower bound until its
		 * real ticks are known; whenever the best leg to an end that may still end the route in time starts at such a
		 * boarding, those ticks are searched and the sea searched again. At the end every such leg starts at real
		 * ticks, so its cost is the real least cost (the others' costs only ever went up): the same legs as when every
		 * boarding's ticks are searched first, with far fewer walking searches (often none: the walk is the route).
		 */
		private void sail()
		{
			// per boat its last sea search: searched again only when one of its boardings' real ticks
			// became known since (the same start points, ends and bound give the same result)
			SeaSearch[] searched = new SeaSearch[o.boats.size()];
			while (true)
			{
				arriveAt = new SeaLeg[docks.size()];
				toDest = null;
				// the boardings the legs that may still end the route in time start at, without their real ticks yet
				List<SeaBoarding> unknown = new ArrayList<>();
				bestLegs(searched, unknown);
				if (unknown.isEmpty())
				{
					break;
				}
				searchBoardings(unknown, searched);
			}
		}

		/**
		 * The best leg of any boat to each end ({@link #arriveAt}, {@link #toDest}), searching the sea again for the
		 * boats not searched since their start points moved; {@code unknown} gets the boardings those legs start at
		 * whose real ticks are not known yet.
		 */
		private void bestLegs(SeaSearch[] searched, List<SeaBoarding> unknown)
		{
			int destEnd = docks.size();
			for (int bi = 0; bi < o.boats.size(); bi++)
			{
				Boat b = o.boats.get(bi);
				if (searched[bi] == null)
				{
					searched[bi] = seaSearch(b, boardingsOf.get(bi), endsOf.get(bi), endPiecesOf.get(bi), q, lower, best, o);
				}
				SeaSearch res = searched[bi];
				if (res.none())
				{
					continue;
				}
				for (int e = 0; e <= docks.size(); e++)
				{
					if (res.cost(e) < 0 || e < destEnd && !SailingWorld.usable(docks.get(e), o))
					{
						continue;
					}
					int arrive = (res.cost(e) + 99) / 100 + (e < destEnd ? o.boardTicks : 0);
					SeaLeg old = e < destEnd ? arriveAt[e] : toDest;
					if (old != null && old.arrive <= arrive)
					{
						continue;
					}
					SeaLeg leg = new SeaLeg();
					leg.boat = b;
					leg.from = res.from(e);
					leg.to = e < destEnd ? e : -1;
					leg.search = res;
					leg.end = e;
					leg.arrive = arrive;
					if (e < destEnd)
					{
						if (leg.from.dock != e)
						{
							arriveAt[e] = leg;
						}
						else if (leg.from.ready == UNSEARCHED && !unknown.contains(leg.from) && (long) arrive + onFoot[e] < best)
						{
							// a leg back to the same dock is never used; at its lower bound it may hide a real leg from
							// another dock: its real ticks are searched (usually: the dock can't be reached on foot)
							unknown.add(leg.from);
						}
					}
					else
					{
						toDest = leg;
					}
				}
			}
			for (int k = 0; k <= docks.size(); k++)
			{
				SeaLeg leg = k < destEnd ? arriveAt[k] : toDest;
				if (leg != null && leg.from.dock >= 0 && leg.from.ready == UNSEARCHED && !unknown.contains(leg.from)
					&& (long) leg.arrive + (k < destEnd ? onFoot[k] : 0) < best)
				{
					unknown.add(leg.from);
				}
			}
		}

		/** Searches the real ticks of these boardings; the boats that board there are searched again at sea. */
		private void searchBoardings(List<SeaBoarding> unknown, SeaSearch[] searched)
		{
			for (SeaBoarding bd : unknown)
			{
				// from the banks: after a few searches to docks one at a time, the rest all at once
				if (fromBanks && docksAtOnce != null && !atOnce && dockSearches >= AT_ONCE_AFTER && onFoot != null && reach != null
					&& best < NONE)
				{
					searchDocksAtOnce(qx, o, onFoot, dead, toDock, reach, best, limit, docksAtOnce);
					atOnce = true;
				}
				bd.ready = readyToBoard(bd, toDock, qx, limit, finder);
				for (int bi = 0; bi < o.boats.size(); bi++)
				{
					if (searched[bi] != null && boardingsOf.get(bi).contains(bd))
					{
						searched[bi] = null; // its start point moved: search this boat's sea again
					}
				}
			}
		}

		/** The legs' courses, then the route: kept on the boat, or 3. the walk on from where a boat is left. */
		private Route finish()
		{
			for (SeaLeg leg : arriveAt)
			{
				if (leg != null && leg.arrive < best)
				{
					leg.finish();
				}
			}
			if (toDest != null)
			{
				toDest.finish();
			}
			if (keep)
			{
				Route out = keptOnBoat(q, qx, all, arriveAt, toDest, onFoot, finder, o.boardTicks);
				return out.isFound() ? new Route(true, out.getTicks(), out.getSteps(), out.getExpanded(), System.nanoTime() - t0)
					: Route.notFound(walk.getExpanded(), System.nanoTime() - t0);
			}
			// 3. the walk on from where a boat is left: each dock a starting point at its arrival ticks
			List<Transport> withArrivals = new ArrayList<>(all);
			for (int k = 0; k < docks.size(); k++)
			{
				SeaLeg leg = arriveAt[k];
				if (leg != null && leg.arrive < best)
				{
					int[] t = land.get(k);
					withArrivals.add(Transport.teleport("sail-arrive:" + k, "Sail to " + docks.get(k).getName(), t[0], t[1], t[2], leg.arrive, true));
				}
			}
			Route last = withArrivals.size() == all.size() ? walk
				: finder.apply(with(q, withArrivals, q.getDestX(), q.getDestY(), q.getDestPlane()));
			Route out;
			if (toDest != null && (!last.isFound() || toDest.arrive < last.getTicks()))
			{
				out = build(qx, toDest, Collections.emptyList(), finder, o.boardTicks);
			}
			else if (last.isFound() && !last.getSteps().isEmpty() && last.getSteps().get(0).getTransport() != null
				&& last.getSteps().get(0).getTransport().getId().startsWith("sail-arrive:"))
			{
				int k = Integer.parseInt(last.getSteps().get(0).getTransport().getId().substring("sail-arrive:".length()));
				out = build(qx, arriveAt[k], last.getSteps().subList(1, last.getSteps().size()), finder, o.boardTicks);
			}
			else
			{
				out = last;
			}
			return out.isFound() ? new Route(true, out.getTicks(), out.getSteps(), out.getExpanded(), System.nanoTime() - t0) : out;
		}
	}

	/**
	 * The best route that starts by sailing the boat the player is on ({@link Sailing.Options#keepBoat}): to the open-sea
	 * destination, or to a dock with a shipwright and on from there any way (teleports too: the boat is moored). Docks
	 * are tried by their least possible total (arrival + the least walk on), one walking search each, until none can
	 * beat the best.
	 */
	private Route keptOnBoat(PathQuery q, PathQuery qx, List<Transport> all, SeaLeg[] arriveAt, SeaLeg toDest, int[] onFoot,
		Function<PathQuery, Route> finder, int board)
	{
		int bestTicks = toDest != null ? toDest.arrive : NONE;
		SeaLeg bestLeg = toDest;
		Route bestOn = null;
		List<Integer> order = new ArrayList<>();
		for (int k = 0; k < arriveAt.length; k++)
		{
			if (arriveAt[k] != null && onFoot != null && onFoot[k] < NONE && docks.get(k).getShipwright() != null)
			{
				order.add(k);
			}
		}
		order.sort((a, b) -> Long.compare((long) arriveAt[a].arrive + onFoot[a], (long) arriveAt[b].arrive + onFoot[b]));
		for (int k : order)
		{
			SeaLeg leg = arriveAt[k];
			if ((long) leg.arrive + onFoot[k] >= bestTicks)
			{
				break;
			}
			int[] t = land.get(k);
			Route on = finder.apply(new PathQuery(t[0], t[1], t[2], q.getDestX(), q.getDestY(), q.getDestPlane(), all,
				q.getPlaneChangePenalty(), q.getDoors(), q.getDoorCost()).withClosed(q.getClosed()));
			dockSearches++;
			if (on.isFound() && leg.arrive + on.getTicks() < bestTicks)
			{
				bestTicks = leg.arrive + on.getTicks();
				bestLeg = leg;
				bestOn = on;
			}
		}
		if (bestLeg == null)
		{
			return Route.notFound(0, 0);
		}
		if (bestLeg.courses == null)
		{
			bestLeg.finish();
		}
		return build(qx, bestLeg, bestOn == null ? Collections.emptyList() : bestOn.getSteps(), finder, board);
	}

	/**
	 * The "ocean only" route (e.g. for courier tasks): the player stays on the boat they are on at {@code q}'s start
	 * the whole way, through boat-only transports too, to the first of {@link SailingWorld#oceanEnds} the boat can reach: the
	 * destination's open sea, else the nearest dock within {@link Sailing#OCEAN_ONLY_RANGE} tiles (the boat stops there, the
	 * player still on it: no leaving step), else the nearest open sea within that range. Not found when the player is
	 * not on a boat or the boat reaches none of them.
	 */
	Route oceanOnly(PathQuery q, Sailing.Options o)
	{
		long t0 = System.nanoTime();
		seaStates = 0;
		Boat b = world.aboardAt(q, o);
		if (b == null)
		{
			return Route.notFound(0, System.nanoTime() - t0);
		}
		links = world.links(q.getBoatTransports(), q.getPlaneChangePenalty());
		List<SeaBoarding> here = List.of(new SeaBoarding(-1, 0, null, null));
		for (SailingWorld.OceanEnd e : world.oceanEnds(q.getDestX(), q.getDestY(), q.getDestPlane(), b, o))
		{
			// one search per end, in order: the sea search stops at its best end, so the nearest is never passed over
			SeaSearch res = seaSearch(b, here, List.of(new SeaRouter.End(e.joins, 0)), Collections.singletonList(e.pieces), q, null,
				NONE, o);
			if (res.none() || res.cost(0) < 0)
			{
				continue;
			}
			SeaLeg leg = new SeaLeg();
			leg.boat = b;
			leg.from = res.from(0);
			leg.to = -1;
			leg.search = res;
			leg.end = 0;
			leg.arrive = (res.cost(0) + 99) / 100;
			leg.endName = e.name;
			leg.finish();
			Route out = build(q, leg, Collections.emptyList(), null, o.boardTicks);
			return new Route(true, out.getTicks(), out.getSteps(), 0, System.nanoTime() - t0);
		}
		return Route.notFound(0, System.nanoTime() - t0);
	}

	/**
	 * Ticks from the query's start until the player stands at the boarding's dock with the boat there (boarding not
	 * included), or -1: to the dock, plus bringing the boat; when the dock's shipwright brings it and its place is
	 * known, to the shipwright, Recover-boat, then the walk on to the dock ({@link #walkFromWright}). The docks' lower
	 * bounds ({@link SailingBounds#lowerBounds}) stay lower bounds of this: going by the shipwright is never shorter than the
	 * shortest way to the dock.
	 */
	private int readyToBoard(SeaBoarding bd, int[] toDock, PathQuery qx, int limit, Function<PathQuery, Route> finder)
	{
		int k = bd.dock;
		if (bd.wright && wrightStand.get(k) != null)
		{
			Route on = walkFromWright(k, qx, finder);
			if (on.isFound())
			{
				if (toWright[k] == UNSEARCHED)
				{
					int[] t = wrightStand.get(k);
					Route r = finder.apply(with(qx, qx.getTransports(), t[0], t[1], t[2]));
					toWright[k] = r.isFound() && r.getTicks() <= limit ? r.getTicks() : -1;
					dockSearches++;
				}
				return toWright[k] < 0 ? -1 : toWright[k] + bd.extra + on.getTicks();
			}
			// no walk from the shipwright to the dock on the walking map: the boat is brought at the dock
		}
		return ticksTo(k, toDock, qx, limit, finder) < 0 ? -1 : toDock[k] + bd.extra;
	}

	/**
	 * The walk from dock k's shipwright to the dock's land tile (this route's doors; on foot, no transports: the two
	 * are at most a few dozen tiles apart), searched once per route.
	 */
	private Route walkFromWright(int k, PathQuery qx, Function<PathQuery, Route> finder)
	{
		if (wrightWalk[k] == null)
		{
			int[] s = wrightStand.get(k);
			int[] d = land.get(k);
			wrightWalk[k] = s[0] == d[0] && s[1] == d[1] && s[2] == d[2] ? new Route(true, 0, List.of(), 0, 0)
				: finder.apply(new PathQuery(s[0], s[1], s[2], d[0], d[1], d[2], List.of(), qx.getPlaneChangePenalty(), qx.getDoors(),
				qx.getDoorCost()).withClosed(qx.getClosed()));
		}
		return wrightWalk[k];
	}

	/** Ticks from the query's start to dock k (one walking search, kept in toDock), or -1 when not within the limit. */
	private int ticksTo(int k, int[] toDock, PathQuery qx, int limit, Function<PathQuery, Route> finder)
	{
		if (toDock[k] == UNSEARCHED)
		{
			int[] t = land.get(k);
			Route r = finder.apply(with(qx, qx.getTransports(), t[0], t[1], t[2]));
			toDock[k] = r.isFound() && r.getTicks() <= limit ? r.getTicks() : -1;
			dockSearches++;
		}
		return toDock[k];
	}

	/**
	 * The sea search of boat {@code b} from its boardings (each at its real ticks when known, else at its lower bound;
	 * none that can't be reached) to its ends ({@code endPieces}: the graph pieces each one's joins reach, null for an
	 * end with none), through the boat-only transports it fits ({@link SeaSearch}). Only legs that can end the route
	 * before {@code best} are ever used: a leg to a dock ends ceil(cost / 100) + the boarding ticks (leaving) in and is
	 * followed by at least the least walk on; a leg to the destination at sea ends ceil(cost / 100) ticks in.
	 */
	private SeaSearch seaSearch(Boat b, List<SeaBoarding> boardings, List<SeaRouter.End> ends, List<int[]> endPieces, PathQuery q,
		int[] lower, int best, Sailing.Options o)
	{
		List<SeaRouter.Start> starts = new ArrayList<>();
		List<SeaBoarding> used = new ArrayList<>();
		List<SeaGraph.Join> startJoins = new ArrayList<>();
		for (SeaBoarding bd : boardings)
		{
			if (bd.dock < 0)
			{
				int[] here = world.nearestFit(q.getStartX(), q.getStartY(), b);
				if (here != null)
				{
					List<SeaGraph.Join> j = seaGraph.join(new int[][]{here});
					starts.add(new SeaRouter.Start(j, 0));
					startJoins.addAll(j);
					used.add(bd);
				}
			}
			else if (bd.ready != -1)
			{
				int ready = bd.ready >= 0 ? bd.ready : lower[bd.dock] + bd.extra;
				starts.add(new SeaRouter.Start(dockJoins.get(bd.dock), (ready + o.boardTicks) * 100));
				startJoins.addAll(dockJoins.get(bd.dock));
				used.add(bd);
			}
		}
		List<SeaLink> mine = linksFor(b);
		SeaSearch s = new SeaSearch(ends.size(), mine, used);
		if (starts.isEmpty())
		{
			return s;
		}
		int bound = (int) Math.min(best >= NONE ? Integer.MAX_VALUE / 2 : (long) best * 100 - 1, Integer.MAX_VALUE / 2);
		SeaRouter.Rules rules = new SeaRouter.Rules(b, o.turnCost);
		s.record(-1, router.searchTo(rules, starts, stageEnds(ends, endPieces, mine, world.piecesOf(startJoins), null), bound));
		seaStates += router.lastSearchSize()[0];
		for (int l = s.next(bound); l >= 0; l = s.next(bound))
		{
			s.done[l] = true;
			SeaLink k = mine.get(l);
			SeaRouter.Start from = new SeaRouter.Start(k.out, (int) s.exitCost[l]);
			s.record(l, router.searchTo(rules, List.of(from), stageEnds(ends, endPieces, mine, k.outPieces, k), bound));
			seaStates += router.lastSearchSize()[0];
		}
		return s;
	}

	/**
	 * One stage's ends: the real ends in the graph pieces it starts in ({@code pieces}), then each boat-only
	 * transport's start there (its ticks follow it), but the way back through {@code from} (the transport the stage
	 * starts from: never quicker).
	 */
	private static List<SeaRouter.End> stageEnds(List<SeaRouter.End> ends, List<int[]> endPieces, List<SeaLink> links, int[] pieces,
		SeaLink from)
	{
		List<SeaRouter.End> out = new ArrayList<>(ends.size() + links.size());
		for (int e = 0; e < ends.size(); e++)
		{
			out.add(endPieces.get(e) != null && SailingWorld.shares(endPieces.get(e), pieces) ? ends.get(e) : NO_END);
		}
		for (SeaLink l : links)
		{
			out.add(SailingWorld.shares(l.inPieces, pieces) && (from == null || !l.reverses(from)) ? new SeaRouter.End(l.in, l.cost) : NO_END);
		}
		return out;
	}

	/** This route's boat-only transports that boat {@code b} fits through. */
	private List<SeaLink> linksFor(Boat b)
	{
		return SailingWorld.linksFor(links, b);
	}

	/**
	 * Raises each dock's least walk on ({@code onFoot}, from {@link SailingBounds#lowerBoundsOn}) to the coarse bound of a walk on
	 * without teleports ({@link Pathfinder#boundsFrom}, searched up to {@code cap}): part 3 offers each dock as a start
	 * point at its arrival ticks, and the walking search uses teleports only from the route's start, never after such an
	 * arrival. Still a lower bound of part 3's walk on, so the legs it rules out could not have been used.
	 */
	private void sharpenOnFoot(PathQuery qx, int[] onFoot, Pathfinder reach, int cap)
	{
		List<Transport> noTeleports = new ArrayList<>(qx.getTransports().size());
		for (Transport t : qx.getTransports())
		{
			if (t.hasStart())
			{
				noTeleports.add(t);
			}
		}
		int[] tiles = new int[docks.size()];
		for (int k = 0; k < tiles.length; k++)
		{
			tiles[k] = pack(land.get(k));
		}
		int[] coarse = reach.boundsFrom(with(qx, noTeleports, qx.getDestX(), qx.getDestY(), qx.getDestPlane()), tiles, cap);
		for (int k = 0; k < tiles.length; k++)
		{
			onFoot[k] = Math.max(onFoot[k], Math.min(coarse[k], NONE));
		}
	}

	/**
	 * Whether boarding {@code bd} cannot give a sea leg that ends the route before {@code best} (the walk's ticks):
	 * other legs are never used (part 3 offers only earlier arrivals; a leg to the destination at sea must beat the
	 * route found). A leg from it costs at least: the ticks to the dock ({@code lowerToDock}), bringing the boat,
	 * boarding, sailing at least the straight distance from the buoy's water at the boat's speed, and (to a dock)
	 * leaving and the least walk on from there ({@code onFoot}). Rounding is allowed for (each stretch of a sea route
	 * is rounded to a hundredth of a tick on its own; stretches are at least 4 tiles, apart from the two joins).
	 *
	 * @param destTile the destination at sea (the boat's tile there), or null when the destination is on land
	 */
	private boolean useless(SeaBoarding bd, int lowerToDock, Boat b, int best, int[] destTile, int[] destPieces, int[] onFoot, boolean[] dead,
		Sailing.Options o)
	{
		long before = ((long) lowerToDock + bd.extra + o.boardTicks) * 100;
		Dock from = docks.get(bd.dock);
		double perTile = 100 / b.speed() - 0.125;
		List<SeaLink> mine = linksFor(b);
		if (destTile != null)
		{
			long sail = SailingBounds.leastSail(SailingBounds.buoyBox(from), world.dockPieces(bd.dock),
				new int[]{destTile[0], destTile[1], destTile[0], destTile[1]}, destPieces, perTile, mine);
			return sail >= SailingBounds.FAR || before + sail >= (long) best * 100;
		}
		for (int j = 0; j < docks.size(); j++)
		{
			if (j == bd.dock || !SailingWorld.usable(docks.get(j), o) || dead != null && dead[j] || onFoot[j] >= NONE)
			{
				continue; // a leg back to the same dock is never used
			}
			long sail = world.dockSail(bd.dock, j, perTile, mine);
			if (sail < SailingBounds.FAR && before + sail + (o.boardTicks + (long) onFoot[j]) * 100L < (long) best * 100)
			{
				return false;
			}
		}
		return true;
	}

	private static boolean allTrue(boolean[] b)
	{
		for (boolean v : b)
		{
			if (!v)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Per dock: true when the destination cannot be reached from its land tile at all, walking and with the
	 * query's transports (costs ignored; the walking search's own check, {@link Pathfinder#leadsToDestination}).
	 * Sailing there can never be part of a route, and leaving it out changes nothing else: the walking search
	 * never gets from such a place to anything that leads to the destination. Null when a teleport leads there.
	 */
	private boolean[] deadDocks(PathQuery q, Pathfinder reach)
	{
		int[] tiles = new int[docks.size()];
		for (int k = 0; k < tiles.length; k++)
		{
			tiles[k] = pack(land.get(k));
		}
		boolean[] leads = reach.leadsToDestination(q, tiles);
		if (leads == null)
		{
			return null;
		}
		boolean[] dead = new boolean[leads.length];
		for (int k = 0; k < dead.length; k++)
		{
			dead[k] = !leads[k];
		}
		return dead;
	}

	private static int pack(int[] t)
	{
		return Tiles.pack(t[0], t[1], t[2]);
	}

	static PathQuery with(PathQuery q, List<Transport> transports, int dx, int dy, int dp)
	{
		return new PathQuery(q.getStartX(), q.getStartY(), q.getStartPlane(), dx, dy, dp, transports, q.getPlaneChangePenalty(),
			q.getDoors(), q.getDoorCost()).withClosed(q.getClosed());
	}

	/** The course's tiles, packed with plane 0. */
	private static int[] seaTiles(SeaRouter.Course c)
	{
		int[] seaTiles = new int[c.tiles.length];
		for (int i = 0; i < seaTiles.length; i++)
		{
			seaTiles[i] = Tiles.pack(c.tiles[i] >>> 14, c.tiles[i] & 0x3FFF, 0);
		}
		return seaTiles;
	}

	/**
	 * The steps of a route that sails: to the dock, (bring the boat), Board, Sail, Leave, then {@code after}. Through a
	 * boat-only transport: Sail to its start, the transport (the player on the boat), Sail on.
	 */
	private Route build(PathQuery qx, SeaLeg leg, List<Route.Step> after, Function<PathQuery, Route> finder, int board)
	{
		List<Route.Step> steps = new ArrayList<>();
		String boat = leg.boat.label();
		if (leg.from.dock >= 0)
		{
			int[] t = land.get(leg.from.dock);
			int from = pack(t);
			// a shipwright brings the boat: walk to the shipwright, Recover-boat there, then walk on to the dock
			int[] stand = leg.from.wright ? wrightStand.get(leg.from.dock) : null;
			Route on = stand == null ? null : walkFromWright(leg.from.dock, qx, finder);
			if (on != null && !on.isFound())
			{
				stand = null;
				on = null;
			}
			int[] first = stand != null ? stand : t;
			if (qx.getStartX() != first[0] || qx.getStartY() != first[1] || qx.getStartPlane() != first[2])
			{
				Route head = finder.apply(with(qx, qx.getTransports(), first[0], first[1], first[2]));
				if (!head.isFound())
				{
					return Route.notFound(0, 0);
				}
				steps.addAll(head.getSteps());
			}
			if (leg.from.extra > 0)
			{
				int at = pack(first);
				steps.add(new Route.Step(Route.Step.Kind.TRANSPORT, new int[]{at, at},
					here(leg.from.id, leg.from.how, at, leg.from.extra), leg.from.extra));
			}
			if (on != null)
			{
				steps.addAll(on.getSteps());
			}
			steps.add(new Route.Step(Route.Step.Kind.TRANSPORT, new int[]{from, from},
				here("sail-board:" + leg.boat.getSlot(), "Board " + boat + " at " + docks.get(leg.from.dock).getName(), from,
					board), board));
		}
		String where = leg.to >= 0 ? docks.get(leg.to).getName() : leg.endName != null ? leg.endName : "the destination";
		for (int i = 0; i < leg.courses.size(); i++)
		{
			SeaRouter.Course c = leg.courses.get(i);
			int[] seaTiles = seaTiles(c);
			SeaLink next = i < leg.through.size() ? leg.through.get(i) : null;
			int ticks = leg.pieceTicks[i];
			Transport sail = Transport.between("sail:" + leg.boat.getSlot(), "Sail " + boat + " to " + (next != null ? next.t.getName() : where),
				Tiles.x(seaTiles[0]), Tiles.y(seaTiles[0]), 0, Tiles.x(seaTiles[seaTiles.length - 1]), Tiles.y(seaTiles[seaTiles.length - 1]), 0,
				ticks, false, true);
			steps.add(new Route.Step(Route.Step.Kind.SAIL, seaTiles, sail, ticks, c.corners.clone(), c.points.clone()));
			if (next != null)
			{
				// the boat-only transport itself, as a transport step (its own id: it can be skipped or blacklisted)
				int a = Tiles.pack(next.inBox[0], next.inBox[1], 0);
				int b = Tiles.pack(next.outBox[0], next.outBox[1], 0);
				steps.add(new Route.Step(Route.Step.Kind.TRANSPORT, new int[]{a, b}, next.t, next.cost / 100));
			}
		}
		if (leg.to >= 0)
		{
			int to = pack(land.get(leg.to));
			steps.add(new Route.Step(Route.Step.Kind.TRANSPORT, new int[]{to, to},
				here("sail-leave:" + leg.boat.getSlot(), "Leave " + boat + " at " + where, to, board), board));
		}
		steps.addAll(after);
		int total = 0;
		for (Route.Step s : steps)
		{
			total += s.getTicks();
		}
		return new Route(true, total, steps, 0, 0);
	}

	private static Transport here(String id, String name, int tile, int ticks)
	{
		return Transport.between(id, name, Tiles.x(tile), Tiles.y(tile), Tiles.plane(tile), Tiles.x(tile), Tiles.y(tile), Tiles.plane(tile),
			ticks, false, true);
	}
}
