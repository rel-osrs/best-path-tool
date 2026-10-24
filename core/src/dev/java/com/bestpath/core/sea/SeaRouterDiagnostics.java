package com.bestpath.core.sea;

import java.util.ArrayList;
import java.util.List;

/**
 * Sea searches that only development code asks of a {@link SeaRouter}.
 */
public final class SeaRouterDiagnostics
{
	private SeaRouterDiagnostics()
	{
	}

	/**
	 * From the starts to the ends (each end: its joins into the graph). Costs above {@code bound} (hundredths of a
	 * tick) are not searched.
	 */
	public static SeaRouter.Result search(SeaRouter router, SeaRouter.Rules r, List<SeaRouter.Start> starts,
		List<List<SeaGraph.Join>> ends, int bound)
	{
		List<SeaRouter.End> e = new ArrayList<>(ends.size());
		for (List<SeaGraph.Join> j : ends)
		{
			e.add(new SeaRouter.End(j, 0));
		}
		return router.searchTo(r, starts, e, bound);
	}
}
