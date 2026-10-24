package com.bestpath.core.path;

import java.util.List;
import java.util.function.Function;

/**
 * What development code reads from {@link Sailing} that the plugin itself never needs: the sea graph's channels for
 * the sea file, the shipwrights' places, the last route's search counts, and routes with any walking search.
 */
public final class SailingDiagnostics
{
	private SailingDiagnostics()
	{
	}

	/**
	 * The tile the player walks to for the shipwright of a dock {@link Sailing#getDocks()} lists
	 * ({@link SailingWorld#standTile}), or null.
	 */
	public static int[] shipwrightStand(Sailing s, int dockIndex)
	{
		int[] t = s.world.wrightStand.get(dockIndex);
		return t == null ? null : t.clone();
	}

	/** The sea graph's channels ({@link com.bestpath.core.sea.SeaChannels}), for the plugin's sea file. */
	public static List<int[]> channels(Sailing s)
	{
		return s.world.seaGraph.channels();
	}

	/** The last route's walking searches to docks, boardings a lower bound made unnecessary, and sea search states. */
	public static int[] lastDockSearches(Sailing s)
	{
		SailingSearch search = s.search;
		return new int[]{search.dockSearches, search.skipped, search.seaStates};
	}

	/** The best route, walking or sailing ({@code finder}: the walking search). */
	public static Route route(Sailing s, PathQuery q, Sailing.Options o, Function<PathQuery, Route> finder)
	{
		return s.route(q, o, finder, null);
	}
}
