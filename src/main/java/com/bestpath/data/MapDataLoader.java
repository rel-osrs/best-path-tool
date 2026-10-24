package com.bestpath.data;

import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.NoMapDataEngine;
import com.bestpath.route.RouteEngine;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the map data the plugin jar ships (the generated data files in
 * {@value #FOLDER}) and builds the route search. Pure Java (no RuneLite
 * classes). Slow (about a second): call it on the route
 * worker thread, never on the client thread.
 */
@Slf4j
public final class MapDataLoader
{
	/** Where the data files sit in the jar (src/main/resources/com/bestpath/data). */
	public static final String FOLDER = "/com/bestpath/data/";
	/** Opens the data files in the jar. */
	public static final PluginData.Source JAR = name -> MapDataLoader.class.getResourceAsStream(FOLDER + name);

	private MapDataLoader()
	{
	}

	/**
	 * The route engine: the real search when the jar has map data, else one
	 * that answers "No map data yet" (or why the data could not be read).
	 */
	public static RouteEngine engine()
	{
		return engine(JAR);
	}

	/** As {@link #engine()}, reading the files from {@code source}. */
	public static RouteEngine engine(PluginData.Source source)
	{
		long t0 = System.nanoTime();
		try
		{
			PluginData data = PluginData.read(source);
			if (data == null)
			{
				log.info("Best Path: no map data in the plugin; routes answer \"No map data yet\"");
				return new NoMapDataEngine();
			}
			PluginRouter router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
			log.info("Best Path: map data loaded in {} ms ({})", (System.nanoTime() - t0) / 1_000_000, data.describe());
			return new CoreRouteEngine(router);
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Best Path: the map data could not be loaded", e);
			return new NoMapDataEngine("The map data could not be loaded: " + e.getMessage());
		}
	}
}
