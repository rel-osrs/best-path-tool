package com.bestpath.core.sea;

/**
 * What the sea tests read from a sea search's result that the plugin itself never needs.
 */
final class TestSeaRoutes
{
	private TestSeaRoutes()
	{
	}

	/**
	 * Real sailing time to the end point (without the start cost and extras): length / speed, hundredths of a tick.
	 * {@code g} is the sea graph the result was searched on.
	 */
	static int realTime(SeaGraph g, SeaRouter.Result r, int end)
	{
		// the real length, hundredths of a tile: each stretch rounded on its own, as it was added up on the way
		int[] path = r.path(end);
		int real = SeaRouter.Rules.hundredths(r.joinOfState.get(path[0]).length);
		for (int k = 1; k < path.length; k++)
		{
			int h = path[k] % SeaRouter.STATES;
			real += SeaRouter.Rules.hundredths(h == SeaRouter.CHANNEL ? g.channelLength(r.states.channel.get(path[k]))
				: SeaGraph.linkLength(h));
		}
		real += SeaRouter.Rules.hundredths(r.endJoin[end].length);
		return (int) Math.ceil(real / r.rulesSpeed);
	}
}
