package com.bestpath.route;

import java.util.Arrays;
import java.util.List;

/** Tests and previews: route results made directly, the way the route engine and the route service make them. */
public final class RouteResults
{
	private RouteResults()
	{
	}

	/** A found route to the request's first place; the total is the sum of the steps' ticks. */
	public static RouteResult ready(long generation, RouteRequest request, List<RouteStep> steps)
	{
		return ready(generation, request, steps, -1, List.of());
	}

	/** A found route to one of the request's places (-1: the first), with notes for the header. */
	public static RouteResult ready(long generation, RouteRequest request, List<RouteStep> steps, int destination,
		List<String> notes)
	{
		return RouteResult.ready(generation, request, steps, destination, notes, (ArriveArea) null);
	}

	/**
	 * A found route arrived at on the tiles next to the destination from which one legal step reaches it, one step
	 * each (null: not known).
	 */
	public static RouteResult ready(long generation, RouteRequest request, List<RouteStep> steps, int destination,
		List<String> notes, int[] arriveTiles)
	{
		return RouteResult.ready(generation, request, steps, destination, notes, oneStep(arriveTiles));
	}

	/** "No route", with why the player can't get there ({@code why} may be null: not worked out). */
	public static RouteResult noRoute(long generation, RouteRequest request, String message, List<String> notes,
		NoRouteWhy why)
	{
		return RouteResult.noRouteFindingWhy(generation, request, message, notes, () -> why).explained();
	}

	private static ArriveArea oneStep(int[] tiles)
	{
		if (tiles == null)
		{
			return null;
		}
		int[] t = tiles.clone();
		Arrays.sort(t);
		byte[] s = new byte[t.length];
		Arrays.fill(s, (byte) 1);
		return new ArriveArea(t, s);
	}
}
