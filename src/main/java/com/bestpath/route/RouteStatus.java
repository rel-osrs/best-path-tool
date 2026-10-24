package com.bestpath.route;

/** What the Route tab's status line says. */
public enum RouteStatus
{
	/** No destination asked for. */
	IDLE,
	/** The worker is searching. */
	CALCULATING,
	/** A route was found. */
	READY,
	/** The destination cannot be reached with the transports allowed. */
	NO_ROUTE,
	/** The plugin has no map data to search with yet. */
	NO_DATA,
	/** No route could be asked for (not logged in, bad input) or the search failed. */
	ERROR,
	/** The player reached the destination; the route is cleared. */
	ARRIVED
}
