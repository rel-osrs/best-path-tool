package com.bestpath.route;

import com.bestpath.core.data.NoRouteReasons;

/** Tests and previews outside this package: what a "no route" explanation keeps that the sidebar does not show. */
public final class NoRouteWhyTestAccess
{
	private NoRouteWhyTestAccess()
	{
	}

	/** Core's kind of reason (members land, nowhere to stand, blocked, by boat, no known way). */
	public static NoRouteReasons.Kind kind(NoRouteWhy why)
	{
		return why.kind;
	}
}
