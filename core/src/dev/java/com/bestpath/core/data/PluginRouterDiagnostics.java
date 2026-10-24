package com.bestpath.core.data;

import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import java.util.function.Predicate;

/**
 * What development code asks of a {@link PluginRouter} that the plugin itself never needs: its search and cache
 * counts, and route questions with the defaults filled in (no filter, no boats, the default bank setup).
 */
public final class PluginRouterDiagnostics
{
	private PluginRouterDiagnostics()
	{
	}

	/** The search kept now: the members one unless the latest route was on a free world. */
	public static Pathfinder pathfinder(PluginRouter r)
	{
		synchronized (r)
		{
			return r.search;
		}
	}

	/** Bytes the search keeps between routes right now. */
	public static long cacheBytes(PluginRouter r)
	{
		synchronized (r)
		{
			return r.search.cacheBytes();
		}
	}

	/** How often the caches were dropped for going over the budget (the big ones, or all). */
	public static int cacheClears(PluginRouter r)
	{
		synchronized (r)
		{
			return r.cacheClears;
		}
	}

	/**
	 * The route from the player's tile to a destination.
	 *
	 * @param excluded transport ids the player skipped or blacklisted (a split two-way transport's way back is
	 *                 {@code id + ":back"}; it is excluded with its id too)
	 */
	public static PluginRouter.Answer route(PluginRouter r, PlayerState player, int sx, int sy, int sp, int dx, int dy, int dp,
		Predicate<String> excluded)
	{
		return route(r, player, sx, sy, sp, dx, dy, dp, excluded, PluginRouter.TransportFilter.NONE);
	}

	/** As above, also leaving out what {@code filter} says. */
	public static PluginRouter.Answer route(PluginRouter r, PlayerState player, int sx, int sy, int sp, int dx, int dy, int dp,
		Predicate<String> excluded, PluginRouter.TransportFilter filter)
	{
		return route(r, player, sx, sy, sp, dx, dy, dp, excluded, filter, SailingSetup.NONE);
	}

	/** As above, and sailing the boats {@code boats} allows (none: walking only). */
	public static PluginRouter.Answer route(PluginRouter r, PlayerState player, int sx, int sy, int sp, int dx, int dy, int dp,
		Predicate<String> excluded, PluginRouter.TransportFilter filter, SailingSetup boats)
	{
		return r.route(player, sx, sy, sp, dx, dy, dp, excluded, filter, boats, PluginRouter.BankSetup.DEFAULT);
	}

	/**
	 * The route to the nearest of several places ({@link PluginRouter#routeNearest(PlayerState, int, int, int, int[],
	 * Predicate, PluginRouter.TransportFilter, int, SailingSetup, PluginRouter.BankSetup)} with no filter, no boats and
	 * the default bank setup).
	 */
	public static PluginRouter.NearestAnswer routeNearest(PluginRouter r, PlayerState player, int sx, int sy, int sp,
		int[] targets, Predicate<String> excluded, int maxSearches)
	{
		return routeNearest(r, player, sx, sy, sp, targets, excluded, PluginRouter.TransportFilter.NONE, maxSearches);
	}

	/** As above, also leaving out what {@code filter} says. */
	public static PluginRouter.NearestAnswer routeNearest(PluginRouter r, PlayerState player, int sx, int sy, int sp,
		int[] targets, Predicate<String> excluded, PluginRouter.TransportFilter filter, int maxSearches)
	{
		return routeNearest(r, player, sx, sy, sp, targets, excluded, filter, maxSearches, SailingSetup.NONE);
	}

	/** As above, with sailing. */
	public static PluginRouter.NearestAnswer routeNearest(PluginRouter r, PlayerState player, int sx, int sy, int sp,
		int[] targets, Predicate<String> excluded, PluginRouter.TransportFilter filter, int maxSearches, SailingSetup boats)
	{
		return r.routeNearest(player, sx, sy, sp, targets, excluded, filter, maxSearches, boats, PluginRouter.BankSetup.DEFAULT);
	}

	/** Why there is no route to one destination (no boats). See {@link NoRouteReasons}. */
	public static NoRouteReasons explainNoRoute(PluginRouter r, PlayerState player, int sx, int sy, int sp, int dx, int dy,
		int dp, Predicate<String> excluded, PluginRouter.TransportFilter filter)
	{
		return explainNoRoute(r, player, sx, sy, sp, new int[]{Tiles.pack(dx, dy, dp)}, excluded, filter);
	}

	/** Why there is no route to any of several places (no boats). */
	public static NoRouteReasons explainNoRoute(PluginRouter r, PlayerState player, int sx, int sy, int sp, int[] targets,
		Predicate<String> excluded, PluginRouter.TransportFilter filter)
	{
		return r.explainNoRoute(player, sx, sy, sp, targets, excluded, filter, SailingSetup.NONE);
	}
}
