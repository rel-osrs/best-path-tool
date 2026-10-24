package com.bestpath.route;

import com.bestpath.core.data.PlayerTransports;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Transport;

/**
 * The player's own tick changes on a route: in the search's filter ({@link #withTickChanges}) and on each step
 * card, what its - / + changes and by how much ({@link #withTicks}).
 */
final class StepTicks
{
	private StepTicks()
	{
	}

	/**
	 * The search's filter with the player's own tick changes (- / + on step cards, and whole teleport categories and
	 * networks in the Settings tab) and the "avoid the Wilderness" rule: the router builds the player's transports with
	 * them, so routes, totals and lower bounds use them.
	 */
	static PluginRouter.TransportFilter withTickChanges(PluginRouter.TransportFilter filter, TransportSettings settings)
	{
		boolean avoidWilderness = settings.avoids(TransportSettings.Avoid.WILDERNESS);
		if (!settings.hasTickChanges() && !avoidWilderness)
		{
			return filter;
		}
		return new PluginRouter.TransportFilter()
		{
			@Override
			public boolean excluded(Transport t, TransportLine line)
			{
				return filter.excluded(t, line);
			}

			@Override
			public int tickChange(String transportId)
			{
				return settings.tickChange(transportId);
			}

			@Override
			public int teleportCategoryTickChange(String category)
			{
				return settings.teleportCategoryTickChange(category);
			}

			@Override
			public int networkTickChange(String network)
			{
				return settings.networkTickChange(network);
			}

			@Override
			public int coinsTickChange()
			{
				return settings.coinsTickChange();
			}

			@Override
			public boolean avoidWilderness()
			{
				return avoidWilderness;
			}
		};
	}

	/**
	 * The step with what its card's - / + changes and how much that moved its ticks: a network ride's whole network,
	 * any other transport its own change.
	 */
	static RouteStep withTicks(RouteStep step, Transport t, PlayerTransports.Result usable, TransportSettings settings)
	{
		TransportLine line = t == null || usable == null ? null : usable.lineOf(t);
		String network = line == null ? "" : line.getNetwork();
		if (network == null || network.isEmpty())
		{
			return step.withTickChange(tickChange(t, settings));
		}
		// the network's change is the transport's whole change minus its own per-ride change (which is kept)
		int p = PluginRouter.PLANE_CHANGE_PENALTY;
		int own = settings.tickChange(t.getId());
		int moved = t.cost(p) - t.withTickChange(own).cost(p);
		return step.withGroupTickChange(moved, TransportSettings.networkKey(network));
	}

	/**
	 * How much the player's own card change (- / + on its step card) moved this transport's cost, after the floor at
	 * 0; 0 for none. A teleport category's or network's change is not part of it: the card's ticks without its own
	 * change ({@code ticks - change}) already have it.
	 */
	static int tickChange(Transport t, TransportSettings settings)
	{
		int own = t == null ? 0 : settings.tickChange(t.getId());
		if (own == 0)
		{
			return 0;
		}
		// the transport's change is its group's plus its own (they may add up to 0)
		int p = PluginRouter.PLANE_CHANGE_PENALTY;
		return t.cost(p) - t.withTickChange(t.getTickChange() - own).cost(p);
	}
}
