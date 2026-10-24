package com.bestpath.route;

import com.bestpath.core.path.GamePath;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * An engine made on first use: loading the map data and building the search
 * takes about a second, so it happens on the route worker thread (never on
 * the client thread). {@link RouteService#runOnWorker} starts it at plugin
 * start; a route asked for meanwhile waits behind it on the same worker.
 */
@Slf4j
public final class LoadingEngine implements RouteEngine
{
	private final Supplier<RouteEngine> loader;
	private volatile RouteEngine engine;

	/** @param loader makes the real engine (called once, on the worker thread) */
	public LoadingEngine(Supplier<RouteEngine> loader)
	{
		this.loader = loader;
	}

	/**
	 * Makes the real engine if that has not happened yet. If making it fails, even with an Error (out of memory,
	 * a class that can't be loaded), the engine answers why from then on: the load is never tried again in a loop.
	 */
	public synchronized void load()
	{
		if (engine == null)
		{
			try
			{
				RouteEngine e = loader.get();
				engine = e != null ? e : new NoMapDataEngine();
			}
			catch (Throwable e)
			{
				log.warn("Best Path: the map data could not be loaded", e);
				engine = new NoMapDataEngine("The map data could not be loaded: " + e);
			}
		}
	}

	@Override
	public RouteResult route(RouteJob job)
	{
		load();
		return engine.route(job);
	}

	@Override
	public GamePath gamePath()
	{
		RouteEngine e = engine;
		return e == null ? null : e.gamePath();
	}
}
