package com.bestpath.core.path;

import com.bestpath.core.req.BankItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Going to a bank first: a route may begin with a bank visit, but never
 * stops at a bank part-way, and every bank is compared exactly.
 * Two routes are compared:
 * <ol>
 * <li>(a) the normal route, with only what the player carries or wears;</li>
 * <li>(b) the best route that begins at a bank: a search from the player's
 * tile with what they carry gives the ticks to the bank tiles
 * ({@link Pathfinder#ticksTo}); then one search starts from those banks at
 * once, each with its own head start (the ticks to get there plus the bank
 * stop, player-adjustable, {@value #DEFAULT_TICKS} by default), with the
 * bank's items too. Teleports can be used right after the bank stop: the
 * earliest is at the bank reached first, so each teleport starts at that
 * bank's head start. This is the way Sailing starts its last search from the
 * docks: start points as teleports costing their arrival ticks, here from a
 * tile off the walking map so the route can only begin at a bank.</li>
 * </ol>
 * The quicker one is shown; (b) must be strictly quicker, so a tie keeps the
 * route without the bank. When the destination needs something only in the
 * bank ({@code preferBank}), (a) is not acceptable: (b) is taken whenever a
 * bank can be reached.
 *
 * <p>Exact: (b) is the minimum over every bank of (the shortest way there +
 * the stop + the shortest way on from it), each part a normal search. Only
 * banks that can't win are left out, by lower bounds that never overestimate
 * (one coarse search with the bank's items, {@link Pathfinder#startBounds}):
 * (b) is not tried when even a route with the bank's items from where the
 * player stands, plus the stop, is no quicker than (a); a bank is left out
 * when the way there, the stop and its bound on from it are no quicker than
 * the best route so far; teleports after the stop when the nearest bank's
 * head start and their bound are not. To make "the best so far" small early,
 * the nearest bank is found first and the best (b) from it alone is searched: a real (b).
 * Then only the banks that could still be at least as quick are looked for,
 * and the search on from the banks is done again only when there are new
 * ones (else the route stands: the same start points give the same route).
 * Banks (and teleports) from which nothing leads to the destination at all
 * are never looked for. The way to the chosen bank is the one the search to
 * the banks found, and a search on from the banks is aimed by the same coarse
 * bounds (with the bank's items, so never more than the ticks really left)
 * when they reach one of its start points.
 *
 * <p>With boats ({@link #withSailing}) the route on from the banks may also sail: the same search
 * from the banks, the sailing search's in place of the walking one, its lower bounds counting each sea leg at the
 * least it could take ({@link #bankThenSail}).
 */
public final class BankFirst
{
	/** The bank stop's ticks unless the player changes them. */
	public static final int DEFAULT_TICKS = 30;
	/** Where the search on from the banks starts: off the walking map, so the route can only begin at a bank. */
	static final int NOWHERE = Tiles.pack(0, 0, 0);
	private static final int NONE = Integer.MAX_VALUE / 4;
	private static final String ARRIVE = "bank-arrive:";


	private BankFirst()
	{
	}

	/** Where the player may bank: the tiles they stand on, each with its bank's name. Immutable. */
	public static final class Banks
	{
		public static final Banks NONE = new Banks(new int[0], new String[0]);

		private final int[] tiles;
		private final String[] names;
		private final Map<Integer, String> byTile;

		/**
		 * @param tiles packed tiles ({@link Tiles}) a player stands on to bank
		 * @param bank  per tile its bank's name (a tile listed twice counts once, with its first name)
		 */
		public Banks(int[] tiles, String[] bank)
		{
			Map<Integer, String> m = new LinkedHashMap<>();
			for (int i = 0; i < tiles.length; i++)
			{
				m.putIfAbsent(tiles[i], bank[i]);
			}
			this.byTile = Collections.unmodifiableMap(m);
			this.tiles = new int[m.size()];
			this.names = new String[m.size()];
			int i = 0;
			for (Map.Entry<Integer, String> e : m.entrySet())
			{
				this.tiles[i] = e.getKey();
				this.names[i++] = e.getValue();
			}
		}

		public boolean isEmpty()
		{
			return tiles.length == 0;
		}

		/** The bank a tile belongs to, or null. */
		public String bankAt(int tile)
		{
			return byTile.get(tile);
		}

		int[] tiles()
		{
			return tiles;
		}

		String name(int i)
		{
			return names[i];
		}
	}

	/** The route to show, and how it was chosen. Immutable. */
	public static final class Plan
	{
		private final Route route;
		private final boolean banked;
		private final Route withoutBank;
		private final int banksReached;
		private final int[] stop;

		Plan(Route route, boolean banked, Route withoutBank, int banksReached)
		{
			this(route, banked, withoutBank, banksReached, null);
		}

		private Plan(Route route, boolean banked, Route withoutBank, int banksReached, int[] stop)
		{
			this.route = route;
			this.banked = banked;
			this.withoutBank = withoutBank;
			this.banksReached = banksReached;
			this.stop = stop;
		}

		Plan withStop(int[] s)
		{
			return new Plan(route, banked, withoutBank, banksReached, s);
		}

		/** The route to show: (b) when it starts at a bank, else (a). Never null. */
		public Route getRoute()
		{
			return route;
		}

		/** True when the route starts at a bank (its steps have a {@link Route.Step.Kind#BANK} step). */
		public boolean isBanked()
		{
			return banked;
		}

		/** Route (a), with only what the player carries; never null (not found when there is none). */
		public Route getWithoutBank()
		{
			return withoutBank;
		}

		/**
		 * Bank tiles the route on from the banks started from: those that could still beat (a) (each got a head start)
		 * and the nearest one when only the teleports start there.
		 */
		public int getBanksReached()
		{
			return banksReached;
		}

		/** From {@link #tryBank}: where the route ends, the settled destination ({@link RouteDestination#resolve}), or null. */
		public int[] getStop()
		{
			return stop == null ? null : stop.clone();
		}
	}

	/**
	 * Compares (a) and (b) (see the class comment).
	 *
	 * @param pf         the search (to the banks, on from them, lower bounds)
	 * @param a          route (a), already searched (null or not found: none)
	 * @param toBank     from the player's tile with the transports they can use with what they carry (its
	 *                   destination is not used)
	 * @param onward     the transports with the bank's items too, and where (b) ends; its start is the player's
	 *                   tile (only for the lower bound); null: (b) is not tried
	 * @param banks      where the player may bank
	 * @param bankTicks  the bank stop
	 * @param preferBank the destination needs something only in the bank: (b) whenever a bank can be reached
	 * @param itemsOf    what a transport of {@code onward} needs from the bank (empty: nothing)
	 * @param destItems  what the destination needs from the bank
	 */
	public static Plan plan(Pathfinder pf, Route a, PathQuery toBank, PathQuery onward, Banks banks, int bankTicks,
		boolean preferBank, Function<Transport, List<BankItem>> itemsOf, List<BankItem> destItems)
	{
		return plan(pf, a, toBank, onward, onward, banks, bankTicks, preferBank, itemsOf, destItems, BankFirst::walkOn);
	}

	/** The search on from the banks ({@link #onward}): only a route of fewer than {@code best} ticks, else not found. */
	private interface OnSearch
	{
		/**
		 * @param from  starts off the walking map, the banks and the teleports as start points
		 * @param known the lower bounds (b) was weighed by; {@code sharp}: a start point is within them
		 */
		Route find(Pathfinder pf, PathQuery from, int best, Pathfinder.StartBounds known, boolean sharp);
	}

	/** The route on from the banks walking (and with transports): aimed by the coarse bounds when they say much there. */
	private static Route walkOn(Pathfinder pf, PathQuery from, int best, Pathfinder.StartBounds known, boolean sharp)
	{
		int limit = best < NONE ? best - 1 : Integer.MAX_VALUE;
		return sharp ? pf.find(from, limit, known) : pf.find(from, limit);
	}

	/**
	 * As {@link #plan(Pathfinder, Route, PathQuery, PathQuery, Banks, int, boolean, Function, List)}, the route on
	 * found by {@code search}, and the lower bounds worked out with {@code bounded}'s transports ({@code onward}'s,
	 * same start and destination, and maybe more that only make the bounds lower: they must stay lower bounds of what
	 * {@code search} finds).
	 */
	private static Plan plan(Pathfinder pf, Route a, PathQuery toBank, PathQuery onward, PathQuery bounded, Banks banks, int bankTicks,
		boolean preferBank, Function<Transport, List<BankItem>> itemsOf, List<BankItem> destItems, OnSearch search)
	{
		Route none = a != null ? a : Route.notFound(0, 0);
		if (onward == null || banks.isEmpty())
		{
			return new Plan(none, false, none, 0);
		}
		int best = preferBank || !none.isFound() ? NONE : none.getTicks();
		int[] tiles = banks.tiles();
		// lower bounds with the bank's items: from here, for a way on from a bank that begins with a teleport, and from
		// each bank without one. Any (b) costs at least a route with the bank's items from here (the way to the bank is
		// a part of one), plus the stop
		Pathfinder.StartBounds lb = pf.startBounds(bounded, tiles);
		if (lb == null || lb.whole + bankTicks >= best)
		{
			return new Plan(none, false, none, 0);
		}
		// per bank the least ticks after reaching it (the stop and the bound on from it); the same for a teleport.
		// None when it can't lead to the destination at all
		int[] after = new int[tiles.length];
		int least = NONE;
		for (int i = 0; i < tiles.length; i++)
		{
			after[i] = lb.at(i) >= FastSearch.NEVER ? NONE : lb.at(i) + bankTicks;
			least = Math.min(least, after[i]);
		}
		int teleAfter = lb.teleport >= FastSearch.NEVER ? NONE : lb.teleport + bankTicks;
		if (least == NONE && teleAfter == NONE)
		{
			return new Plan(none, false, none, 0);
		}
		// 1. the nearest bank (and every bank as near) with what the player carries, then the best (b) from those
		// alone: a real (b), so no other bank needs looking for unless it can beat that
		int[] never = new int[tiles.length];
		Arrays.fill(never, NONE);
		Route[] nearWays = new Route[tiles.length];
		int[] near = pf.ticksTo(toBank, tiles, never, Math.min(least, teleAfter), best, nearWays);
		Onward first = onward(pf, lb, onward, banks, near, after, teleAfter, bankTicks, best, itemsOf, search);
		if (first == null)
		{
			return new Plan(none, false, none, 0);
		}
		int bound = first.on.isFound() ? first.on.getTicks() + 1 : best; // only what is at most as quick as that
		// 2. every other bank that can still beat it (the way there, the stop and the bound on from it under it)
		Route[] ways = new Route[tiles.length];
		// aimed by the coarse bounds with the bank's items (unaimed, it spreads out from the player and every teleport
		// carried, which is slow): a tile is gone on from only while its ticks, the stop and the
		// bound on from it can still be under the bound. From a bank other than the nearest a route on never teleports
		// (each teleport starts at the nearest bank's head start), so that bound never says too much
		int[] to = pf.ticksTo(toBank, tiles, after, teleAfter, bound, ways, lb, bankTicks);
		for (int i = 0; i < tiles.length; i++)
		{
			if (to[i] < 0 && near[i] >= 0)
			{
				to[i] = near[i]; // the nearest banks, found exactly by the first search (the aimed one may not go there)
				ways[i] = nearWays[i];
			}
		}
		Onward all = onward(pf, lb, null, banks, to, after, teleAfter, bankTicks, bound, itemsOf, search);
		Onward used;
		if (all == null || first.starts.containsAll(all.starts))
		{
			used = first; // nothing new can beat it: its route stands (the same search again would find the same)
			ways = nearWays;
		}
		else
		{
			used = onward(pf, lb, onward, banks, to, after, teleAfter, bankTicks, bound, itemsOf, search);
			if (first.on.isFound() && !(used.on.isFound() && used.on.getTicks() <= first.on.getTicks()))
			{
				// a route on that may sail looks for routes under the bound by its sea legs' own estimates, which may be
				// longer than the course drawn: it may miss the first route (walking, it never does). That one stands
				used = first;
				ways = nearWays;
			}
		}
		Route on = used.on;
		if (!on.isFound() || on.getTicks() >= best || on.getSteps().isEmpty() || on.getSteps().get(0).getKind() != Route.Step.Kind.TELEPORT)
		{
			return new Plan(none, false, none, used.reached());
		}
		// which bank: the start point the route began at, or (a teleport first) the bank reached first
		int penalty = onward.getPlaneChangePenalty();
		Route.Step head = on.getSteps().get(0);
		Integer at = used.arrivalOf.get(head.getTransport());
		int bank = at != null ? at : used.first;
		List<Route.Step> rest = new ArrayList<>();
		if (at == null)
		{
			Transport t = used.shifted.get(head.getTransport());
			rest.add(new Route.Step(Route.Step.Kind.TELEPORT, head.getTiles(), t, t.cost(penalty)));
		}
		rest.addAll(on.getSteps().subList(1, on.getSteps().size()));
		// the way there as the search to the banks found it
		Route toIt = ways[bank] != null ? pf.shaped(ways[bank], toBank) : Route.notFound(0, 0);
		if (!toIt.isFound())
		{
			return new Plan(none, false, none, used.reached()); // cannot happen: the search to the banks kept it
		}
		int onTicks = on.getTicks() - (used.to[bank] + bankTicks);
		Route b = assemble(toIt, tiles[bank], banks.name(bank), bankTicks, rest, itemsOf, destItems,
			toIt.getTicks() + bankTicks + onTicks, on.getExpanded(), on.getNanos());
		return b.getTicks() < best ? new Plan(b, true, none, used.reached()) : new Plan(none, false, none, used.reached());
	}

	/** One search on from the banks ({@link #onward}): its start points and its route. */
	private static final class Onward
	{
		/** The banks (by index) that got a start point, and -1 when the teleports start at the nearest bank. */
		final Set<Integer> starts = new HashSet<>();
		final Map<Transport, Integer> arrivalOf = new IdentityHashMap<>();
		final Map<Transport, Transport> shifted = new IdentityHashMap<>();
		int[] to;
		/** The bank reached first (lowest index among the nearest). */
		int first = -1;
		Route on;

		/** How many bank tiles it started from (the nearest one too when only the teleports start there). */
		int reached()
		{
			return arrivalOf.size() + (starts.contains(-1) && !starts.contains(first) ? 1 : 0);
		}
	}

	/**
	 * On from every bank at once: each bank found that can still lead to fewer than {@code best} ticks (the way there
	 * plus {@code after}) a start point at its head start (way there + stop), every teleport from the bank reached
	 * first while it can; nothing else leads on from the start (off the walking map). Null when nothing can.
	 *
	 * @param known  the coarse bounds with the bank's items (the search is aimed by them, not worked out again)
	 * @param onward the transports with the bank's items and where (b) ends; null: only the start points, no search
	 */
	private static Onward onward(Pathfinder pf, Pathfinder.StartBounds known, PathQuery onward, Banks banks, int[] to, int[] after,
		int teleAfter, int bankTicks, int best, Function<Transport, List<BankItem>> itemsOf, OnSearch search)
	{
		int[] tiles = banks.tiles();
		Onward o = new Onward();
		o.to = to;
		List<Transport> list = new ArrayList<>();
		boolean sharp = false; // a start point with a real coarse bound (see below)
		for (int i = 0; i < tiles.length; i++)
		{
			if (to[i] < 0)
			{
				continue;
			}
			if (o.first < 0 || to[i] < to[o.first])
			{
				o.first = i;
			}
			if (to[i] + after[i] >= best)
			{
				continue; // even the bound on from it is too long
			}
			o.starts.add(i);
			sharp |= known.sharp(known.at(i));
			if (onward != null)
			{
				Transport arrive = Transport.teleport(ARRIVE + i, "Bank at " + banks.name(i), Tiles.x(tiles[i]), Tiles.y(tiles[i]),
					Tiles.plane(tiles[i]), to[i] + bankTicks, true);
				list.add(arrive);
				o.arrivalOf.put(arrive, i);
			}
		}
		boolean teleports = o.first >= 0 && to[o.first] + teleAfter < best;
		if (teleports)
		{
			o.starts.add(-1);
			sharp |= known.sharp(known.teleport);
		}
		if (o.starts.isEmpty())
		{
			return null;
		}
		if (onward == null)
		{
			return o;
		}
		int penalty = onward.getPlaneChangePenalty();
		int firstHead = teleports ? to[o.first] + bankTicks : 0;
		Map<Long, Integer> carried = carriedCosts(onward.getTransports(), itemsOf, penalty);
		for (Transport t : onward.getTransports())
		{
			if (rebuys(t, carried, itemsOf, penalty))
			{
				continue; // what the player carries already does this as quickly: never take it out of the bank
			}
			if (t.hasStart())
			{
				list.add(t);
				continue;
			}
			if (!teleports)
			{
				continue; // none can lead to fewer ticks from the bank reached first
			}
			Transport s = Transport.teleport(t.getId(), t.getName(), t.getEndX(), t.getEndY(), t.getEndPlane(), firstHead + t.cost(penalty),
				t.isConfirmed());
			list.add(s);
			o.shifted.put(s, t);
		}
		// no Wilderness teleport limits: they only matter where the player stands in the Wilderness, and no bank is there
		PathQuery from = new PathQuery(Tiles.x(NOWHERE), Tiles.y(NOWHERE), Tiles.plane(NOWHERE), onward.getDestX(), onward.getDestY(),
			onward.getDestPlane(), list, penalty, onward.getDoors(), onward.getDoorCost()).withClosed(onward.getClosed())
			.withBoatTransports(onward.getBoatTransports());
		// only a route of fewer ticks; aimed by the coarse bounds with the bank's items when a start point is within
		// them (else they say little there, and a coarse search of its own aims much better)
		o.on = search.find(pf, from, best, known, sharp);
		return o;
	}

	/**
	 * Weighs a bank start for a route: not tried (null) when nothing in the bank makes a difference ({@code withBank}
	 * is no longer than the normal route's transports) and the destination needs nothing from it, or when the player
	 * may use no bank. Both routes go to the same tile, the route's real destination settled before any search
	 * ({@link RouteDestination}), so they can be compared. When only the bank's items reach that tile, (a) is not
	 * found and (b) is taken whenever found.
	 *
	 * @param q         the normal route's question: the player's tile, the settled destination, what they carry
	 * @param a         the normal route (on {@code q}), {@code stop} the settled destination
	 * @param withBank  the transports with the bank's items too (the normal ones and more, same order)
	 * @param destItems what the destination needs from the bank; null when it needs nothing from it
	 */
	public static Plan tryBank(Pathfinder pf, PathQuery q, Route a, int[] stop, List<Transport> withBank, Banks banks, int bankTicks,
		List<BankItem> destItems, Function<Transport, List<BankItem>> itemsOf)
	{
		boolean must = destItems != null;
		if (!must && withBank.size() == q.getTransports().size() || banks.isEmpty())
		{
			return null; // nothing in the bank makes a difference: the route as always (same speed, same memory)
		}
		Plan p = plan(pf, a, q, q.withTransports(withBank), banks, bankTicks, must, itemsOf, must ? destItems : List.of());
		return p.withStop(stop);
	}

	/** What {@link #withSailing} found: the route, where it ends, the bank plan, and where its time went. */
	public static final class Weighed
	{
		private final Route route;
		private final int[] stop;
		private final Plan plan;
		/** Nanoseconds: the walking route, sailing's own work on top of it, and the bank start. */
		long walkNanos;
		long sailNanos;
		long bankNanos;

		Weighed(Route route, int[] stop, Plan plan)
		{
			this.route = route;
			this.stop = stop;
			this.plan = plan;
		}

		/** Never null; not found when there is no route. */
		public Route getRoute()
		{
			return route;
		}

		/** Where the route ends (as {@link Plan#getStop()}), or null. */
		public int[] getStop()
		{
			return stop == null ? null : stop.clone();
		}

		/** How the bank start was weighed, or null when it was not tried (as {@link #tryBank} returning null). */
		public Plan getPlan()
		{
			return plan;
		}

		/** Nanoseconds spent on the walking route (the route without a boat or the bank, which every route needs). */
		public long getWalkNanos()
		{
			return walkNanos;
		}

		/** Nanoseconds spent on sailing's own work, on top of the walking route. */
		public long getSailNanos()
		{
			return sailNanos;
		}

		/** Nanoseconds spent on the bank start. */
		public long getBankNanos()
		{
			return bankNanos;
		}
	}

	/**
	 * The route with sailing ({@link Sailing#route(PathQuery, Sailing.Options, Pathfinder, boolean)}) and the bank start
	 * ({@link #tryBank}) weighed together: the same route as sailing first and then {@code tryBank} with the sailing
	 * route as (a), found with less work. The bank start is weighed first, against the walking route; then sailing
	 * looks only for sea routes of at most the bank route's ticks (a tie keeps the route without the bank), and not at
	 * all when the destination needs something from the bank and a bank route was found (that route is taken whatever
	 * else is quicker). Last, a bank start whose route on sails ({@link #bankThenSail}), only when strictly quicker.
	 * For a route that keeps the boat ({@link Sailing#keepsBoat}: its walk is no route) or ends at sea: sailing first,
	 * then {@code tryBank} (the route on from a bank never sails there).
	 *
	 * @param stop where the route to {@code q}'s destination (the settled one) ends
	 * @see #tryBank for the other parameters
	 */
	public static Weighed withSailing(Pathfinder pf, Sailing sail, Sailing.Options o, PathQuery q, boolean seaEnd, int[] stop,
		List<Transport> withBank, Banks banks, int bankTicks, List<BankItem> destItems, Function<Transport, List<BankItem>> itemsOf)
	{
		boolean tried = (destItems != null || withBank.size() != q.getTransports().size()) && !banks.isEmpty();
		long t0 = System.nanoTime();
		if (!tried || seaEnd || sail.keepsBoat(q, o))
		{
			Route r = sail.route(q, o, pf, seaEnd);
			int[] at = Sailing.seaStop(r, stop, seaEnd);
			long t1 = System.nanoTime();
			Plan p = tried ? tryBank(pf, q, r, at, withBank, banks, bankTicks, destItems, itemsOf) : null;
			Weighed w = p != null ? new Weighed(p.getRoute(), p.getStop(), p) : new Weighed(r, at, null);
			w.walkNanos = sail.lastWalkNanos();
			w.sailNanos = t1 - t0 - w.walkNanos;
			w.bankNanos = System.nanoTime() - t1;
			return w;
		}
		Route walk = sail.walkingRoute(q, o, pf);
		long t1 = System.nanoTime();
		// the greater teleport focus teleports, as the walk has them: to the bank and on from it too
		List<Transport> focus = sail.focusTeleports(q, o);
		PathQuery qf = q;
		if (!focus.isEmpty())
		{
			List<Transport> carried = new ArrayList<>(q.getTransports());
			carried.addAll(focus);
			qf = q.withTransports(carried);
			withBank = new ArrayList<>(withBank);
			withBank.addAll(focus);
		}
		Plan p = tryBank(pf, qf, walk, stop, withBank, banks, bankTicks, destItems, itemsOf);
		long t2 = System.nanoTime();
		boolean banked = p.isBanked();
		Weighed out;
		if (banked && destItems != null)
		{
			out = new Weighed(p.getRoute(), p.getStop(), p); // it must begin at a bank: nothing else counts
		}
		else
		{
			int beat = banked ? p.getRoute().getTicks() + 1 : NONE;
			Route r = sail.route(q, o, pf, false, beat, walk);
			if (banked && !(r.isFound() && r.getTicks() < beat))
			{
				out = new Weighed(p.getRoute(), p.getStop(), p);
			}
			else
			{
				// sailing (or the walk) is at most as quick: the route without the bank, as tryBank gives it
				out = new Weighed(r, stop, new Plan(r, false, r, p.getBanksReached(), stop));
			}
		}
		long t3 = System.nanoTime();
		Weighed sailed = bankThenSail(pf, sail, o, qf, stop, withBank, banks, bankTicks, destItems, itemsOf, out);
		if (sailed != null)
		{
			out = sailed;
		}
		long t4 = System.nanoTime();
		out.walkNanos = t1 - t0;
		out.sailNanos = t3 - t2;
		out.bankNanos = t2 - t1 + t4 - t3;
		return out;
	}

	/**
	 * A route that starts at a bank and then sails (for example: bank, take out the sailors' amulet, teleport to the
	 * dock where the boat is moored, sail). Weighed after the other three (the walk, the bank start walking on, sailing
	 * with what is carried) and taken only when strictly quicker than the route they gave ({@code shown}; a tie keeps
	 * it), or, when the destination needs something from the bank and no route starting at a bank was found, whenever
	 * found. The same bank start as
	 * {@link #plan} (every bank compared exactly, teleports right after the stop at the nearest one), with the route on
	 * from the banks found by {@link Sailing#routeFromBanks}. Not tried at all (null) when even the quickest sea leg
	 * there could be ({@link Sailing#seaBounds}) and the stop are no quicker than {@code shown}; the coarse bounds that
	 * aim it count every sea leg at that least too, so they stay lower bounds. Null also when nothing beats it, and for a
	 * start at sea (the player is already on a boat).
	 */
	private static Weighed bankThenSail(Pathfinder pf, Sailing sail, Sailing.Options o, PathQuery q, int[] stop, List<Transport> withBank,
		Banks banks, int bankTicks, List<BankItem> destItems, Function<Transport, List<BankItem>> itemsOf, Weighed shown)
	{
		if (sail.atSea(q.getStartX(), q.getStartY(), q.getStartPlane()))
		{
			return null;
		}
		boolean must = destItems != null;
		boolean prefer = must && !shown.getPlan().isBanked(); // the route shown lacks what the destination needs
		Route r = shown.getRoute();
		int best = prefer || !r.isFound() ? NONE : r.getTicks();
		int penalty = q.getPlaneChangePenalty();
		List<Transport> sea = sail.seaBounds(o, penalty, q.getBoatTransports());
		int least = NONE;
		for (Transport t : sea)
		{
			least = Math.min(least, t.cost(penalty));
		}
		if ((long) bankTicks + least >= best)
		{
			return null; // no search: even the quickest sea leg and the stop are no quicker
		}
		List<Transport> bounded = new ArrayList<>(withBank);
		bounded.addAll(sea);
		Plan p = plan(pf, r, q, q.withTransports(withBank), q.withTransports(bounded), banks, bankTicks, prefer, itemsOf,
			must ? destItems : List.of(), (f, from, beat, known, sharp) -> sail.routeFromBanks(from, o, f, beat, known));
		if (!p.isBanked())
		{
			return null;
		}
		return new Weighed(p.getRoute(), stop, new Plan(p.getRoute(), true, shown.getPlan().getWithoutBank(), p.getBanksReached(), stop));
	}

	/**
	 * Per way (start, end, two-way, used from beside): the least cost of the transports that need nothing from the
	 * bank. A transport that needs bank items and does exactly what one of these does, no quicker, is left out of the
	 * route on from the bank (so a route never takes a house tablet out of the bank when the Construction cape the
	 * player wears teleports into the house just as quickly).
	 */
	private static Map<Long, Integer> carriedCosts(List<Transport> list, Function<Transport, List<BankItem>> itemsOf, int penalty)
	{
		Map<Long, Integer> out = new HashMap<>();
		for (Transport t : list)
		{
			if (itemsOf.apply(t).isEmpty())
			{
				out.merge(wayOf(t), t.cost(penalty), Math::min);
			}
		}
		return out;
	}

	/** True when {@code t} needs bank items and a carried transport does the same way at most as dear ({@link #carriedCosts}). */
	private static boolean rebuys(Transport t, Map<Long, Integer> carried, Function<Transport, List<BankItem>> itemsOf, int penalty)
	{
		if (itemsOf.apply(t).isEmpty())
		{
			return false;
		}
		Integer c = carried.get(wayOf(t));
		return c != null && c <= t.cost(penalty);
	}

	/** The way a transport goes: its start (none for a teleport), its end, two-way and used-from-beside, as one key. */
	private static long wayOf(Transport t)
	{
		long start = t.hasStart() ? Tiles.pack(t.getStartX(), t.getStartY(), t.getStartPlane()) & 0xffffffffL : 0xffffffffL;
		long end = Tiles.pack(t.getEndX(), t.getEndY(), t.getEndPlane()) & 0x3fffffffL;
		return start << 32 | end << 2 | (t.isTwoWay() ? 2 : 0) | (t.isUsedFromBeside() ? 1 : 0);
	}

	/** The way to the bank, the bank step (with what to take out), then the route on. */
	private static Route assemble(Route toBank, int tile, String bank, int bankTicks, List<Route.Step> on,
		Function<Transport, List<BankItem>> itemsOf, List<BankItem> destItems, int total, int expanded, long nanos)
	{
		List<BankItem> take = new ArrayList<>();
		for (Route.Step s : on)
		{
			if (s.getTransport() != null)
			{
				for (BankItem it : itemsOf.apply(s.getTransport()))
				{
					BankItem.addTo(take, it); // a kept item one on the list stands for is not listed again
				}
			}
		}
		for (BankItem it : destItems)
		{
			BankItem.addTo(take, it);
		}
		List<Route.Step> steps = new ArrayList<>(toBank.getSteps());
		steps.add(new Route.Step(tile, bankTicks, bank, take));
		steps.addAll(on);
		return new Route(true, total, steps, toBank.getExpanded() + expanded, toBank.getNanos() + nanos);
	}
}
