package com.bestpath.core.data;

/**
 * What development code reads from {@link NoRouteReasons} that the plugin itself never needs.
 */
public final class NoRouteReasonsDiagnostics
{
	private NoRouteReasonsDiagnostics()
	{
	}

	/** The way's ticks as a route would count them (no extra ticks); -1 when there is no way. */
	public static int ticks(NoRouteReasons r)
	{
		return r.ticks;
	}

	/** Index (in the places asked for) of the place this explains; -1 when it is about all of them. */
	public static int chosen(NoRouteReasons r)
	{
		return r.chosen;
	}

	/** How many places were searched (several places: at most {@value NoRouteReasons#MAX_PLACES}). */
	public static int placesLookedAt(NoRouteReasons r)
	{
		return r.placesLookedAt;
	}
}
