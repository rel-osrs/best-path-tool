package com.bestpath.route;

/**
 * The plugin's engine when it has no map data: the jar was built without the
 * generated data files, or they could not be read. Every request answers
 * "No map data yet" (or why the data could not be read) instead of guessing a path.
 */
public final class NoMapDataEngine implements RouteEngine
{
	public static final String MESSAGE = "No map data yet: the plugin has no movement data to search with.";

	private final String message;

	public NoMapDataEngine()
	{
		this(MESSAGE);
	}

	/** With another line for the sidebar (e.g. why the map data could not be read). */
	public NoMapDataEngine(String message)
	{
		this.message = message;
	}

	@Override
	public RouteResult route(RouteJob job)
	{
		return RouteResult.status(job.getGeneration(), job.getRequest(), RouteStatus.NO_DATA, message);
	}
}
