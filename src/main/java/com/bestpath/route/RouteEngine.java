package com.bestpath.route;

import com.bestpath.core.path.GamePath;

/**
 * Finds a route. Called only on the route worker thread, never on the client
 * or Swing thread. Implementations: {@link NoMapDataEngine} (when the map data
 * is missing) and {@link CoreRouteEngine} (the core pathfinder).
 */
public interface RouteEngine
{
	/** Returns a READY, NO_ROUTE, NO_DATA or ERROR result carrying {@code job.getGeneration()}. */
	RouteResult route(RouteJob job);

	/**
	 * The game's own click pathing on this engine's map, for redrawing the walk ahead ({@link RouteRedraw}); null
	 * when there is no map. Route worker thread only (it is not thread-safe).
	 */
	default GamePath gamePath()
	{
		return null;
	}
}
