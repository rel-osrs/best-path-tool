package com.bestpath.core.data;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.path.BankFirst;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.RouteDestination;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.BankItem;
import com.bestpath.core.req.PlayerState;
import java.util.List;
import java.util.function.Predicate;

/**
 * What one route question of {@link PluginRouter} needs besides its tiles: the search for the player's world type,
 * the transports the player may use (with what they carry, and with the bank's items when a bank-first route can be
 * tried), sailing and the bank; and the searches to a settled destination with them. Made and used inside one of the
 * router's synchronized calls.
 */
final class RouteSetup
{
	private final PluginRouter router;
	private final PluginData data;
	final PlayerState player;
	final boolean member;
	final FreeLand land;
	final Pathfinder pf;
	final PlayerTransports.Result usable;
	/** What the player carries or wears. */
	final List<Transport> allowed;
	/** Every transport the route may use: with the bank's items too when a bank-first route may be tried. */
	final List<Transport> widest;
	/** True when the bank's items change nothing ({@link #widest} is what the player carries). */
	final boolean onlyCarried;
	/**
	 * The teleports' Wilderness limits for the searches from where the player stands, or null (no Wilderness data).
	 */
	final PathQuery.Limits limits;
	/** Tiles the searches may not enter (the Wilderness, avoided), or null. */
	final PathQuery.Closed closed;
	final Sailing sail;
	final Sailing.Options sailOptions;
	final PluginRouter.BankSetup bank;
	final List<BankItem> destItems;
	final BankFirst.Banks banks;

	RouteSetup(PluginRouter router, PlayerState player, Predicate<String> excluded, PluginRouter.TransportFilter filter,
		SailingSetup boats, PluginRouter.BankSetup bank, PathQuery.Closed closed)
	{
		this.router = router;
		this.data = router.getData();
		this.closed = closed;
		this.player = player;
		this.member = player.isMembers();
		this.land = member ? null : data.getFreeLand();
		long t0 = System.nanoTime();
		boolean rebuilt = member != router.isMembersSearch();
		this.pf = router.searchFor(member);
		if (rebuilt)
		{
			router.addTime(member ? "Making the members search (world changed)" : "Making the free-world search (world changed)",
				System.nanoTime() - t0);
		}
		long t1 = System.nanoTime();
		this.usable = router.transports().build(player, 0, land, TransportFilters.ticksOf(filter));
		List<Transport> carried = TransportFilters.allowed(usable, usable.getTransports(), excluded, filter);
		this.bank = bank == null ? PluginRouter.BankSetup.DEFAULT : bank;
		this.destItems = this.bank.isOff() ? null : this.bank.neededFromBank(player);
		List<Transport> withBank = usable.bankMatters()
			? TransportFilters.allowed(usable, usable.getWithBank(), excluded, filter) : carried;
		boolean bankMayMatter = !this.bank.isOff() && (destItems != null || withBank.size() != carried.size());
		this.banks = bankMayMatter ? router.transports().banks(data.getBanks(), player, land) : BankFirst.Banks.NONE;
		this.widest = bankMayMatter && !banks.isEmpty() ? withBank : carried;
		this.onlyCarried = widest == carried;
		this.allowed = carried;
		// a teleport the player is too deep in the Wilderness for is offered by the search from every tile it
		// reaches that is shallow enough (walked to, or by a lever, an obelisk...)
		this.limits = data.getWilderness().isEmpty() ? null
			: data.getWilderness().limits(t -> TransportFilters.wildernessLimit(usable, t));
		router.addTime("Your usable transports", System.nanoTime() - t1);
		this.sail = router.sailingFor(member, boats);
		this.sailOptions = sail == null ? null : PluginRouter.sailOptions(player, boats);
	}

	/**
	 * The route's real destination tile, settled once before any search ({@link RouteDestination}); every search of
	 * the route goes there. Null when nothing near it can be reached.
	 */
	RouteDestination destination(int sx, int sy, int sp, int dx, int dy, int dp)
	{
		return destination(sx, sy, sp, dx, dy, dp, true);
	}

	/**
	 * As {@link #destination(int, int, int, int, int, int)}; without {@code bySea} only the spot reached on foot
	 * (the same tile as {@link RouteDestination#onFoot()} of the full one, which adds the boat's spots on top).
	 */
	RouteDestination destination(int sx, int sy, int sp, int dx, int dy, int dp, boolean bySea)
	{
		long t0 = System.nanoTime();
		PathQuery wide = new PathQuery(sx, sy, sp, dx, dy, dp, widest, PluginRouter.PLANE_CHANGE_PENALTY, data.getDoors(),
			PluginRouter.DOOR_COST).withClosed(closed).withLimits(limits);
		RouteDestination d = RouteDestination.resolve(pf, bySea ? sail : null, bySea ? sailOptions : null, wide,
			PluginRouter.NEAR_RANGE, data.getWalls(), dy >= PluginRouter.MAIN_MAP_TOP_Y);
		router.addTime("Destination tile", System.nanoTime() - t0);
		return d;
	}

	/**
	 * The route to a destination already settled ({@link #destination}; null means nothing near it can be
	 * reached, so no route). Uses what the player carries (sailing when boats are given), or goes to a bank first when
	 * that is quicker or the destination needs something only in the bank ({@link BankFirst}); both go to that same
	 * tile. When no route reaches a tile only a boat can bring the player to, its fallback
	 * ({@link RouteDestination#getFallback()}) is tried the same way.
	 */
	PluginRouter.Answer routeTo(int sx, int sy, int sp, RouteDestination end)
	{
		if (end == null)
		{
			return new PluginRouter.Answer(Route.notFound(0, 0), null, usable, !member);
		}
		PluginRouter.Answer a = routeToTile(sx, sy, sp, end);
		return a.getRoute().isFound() || !end.isBySea() ? a : routeTo(sx, sy, sp, end.getFallback());
	}

	private PluginRouter.Answer routeToTile(int sx, int sy, int sp, RouteDestination end)
	{
		PathQuery q = new PathQuery(sx, sy, sp, end.getX(), end.getY(), end.getPlane(), allowed,
			PluginRouter.PLANE_CHANGE_PENALTY, data.getDoors(), PluginRouter.DOOR_COST).withClosed(closed).withLimits(limits);
		int[] stop = end.getStop();
		if (sail != null)
		{
			// sailing and the bank start weighed together: the bank route first, then sailing only has to beat it.
			// For a destination on open sea, the stop is where the boat stops.
			BankFirst.Weighed w = BankFirst.withSailing(pf, sail, sailOptions, q, end.isAtSea(), stop, widest, banks,
				bank.getTicks(), destItems, usable::takeOut);
			// timed apart from sailing's own work, so the walking search every route needs is not counted as sailing
			router.addTime("Walking search", w.getWalkNanos());
			if (w.getPlan() != null)
			{
				router.addTime("Starting at a bank", w.getBankNanos());
			}
			router.addTime(PluginRouter.SAILING_PART, w.getSailNanos());
			return new PluginRouter.Answer(w.getRoute(), w.getStop(), usable, !member, w.getPlan());
		}
		long t0 = System.nanoTime();
		Route r = pf.find(q);
		router.addTime("Walking search", System.nanoTime() - t0);
		PluginRouter.Answer b = bankFirst(q, r, stop);
		return b != null ? b : new PluginRouter.Answer(r, stop, usable, !member);
	}

	/**
	 * Going to a bank first ({@link BankFirst}): null when it is not tried (nothing in the bank makes a difference
	 * for this route and the destination needs nothing from it, or no bank this player may use); else the answer,
	 * with the bank or without. The route on from the bank is a walking one (no sailing). Both go to {@code q}'s
	 * destination, the settled one.
	 *
	 * @param q       the normal route's question (what the player carries)
	 * @param a       the normal route, {@code stop} where it ends
	 */
	private PluginRouter.Answer bankFirst(PathQuery q, Route a, int[] stop)
	{
		if (onlyCarried && destItems == null)
		{
			return null; // nothing in the bank makes a difference: the route as always (same speed, same memory)
		}
		long t0 = System.nanoTime();
		BankFirst.Plan plan = BankFirst.tryBank(pf, q, a, stop, widest, banks, bank.getTicks(), destItems, usable::takeOut);
		router.addTime("Starting at a bank", System.nanoTime() - t0);
		return plan == null ? null : new PluginRouter.Answer(plan.getRoute(), plan.getStop(), usable, !member, plan);
	}
}
