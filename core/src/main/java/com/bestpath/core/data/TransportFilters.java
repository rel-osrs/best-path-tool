package com.bestpath.core.data;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * How a route's {@link PluginRouter.TransportFilter} and exclusions turn into what the searches use: the transports
 * allowed, the tick changes for {@link PlayerTransports}, the filter without the Wilderness, and teleports'
 * Wilderness limits.
 */
final class TransportFilters
{
	private TransportFilters()
	{
	}

	/** The filter with every transport into, out of or inside the Wilderness left out too (its start or its end there). */
	static PluginRouter.TransportFilter outOfWilderness(PluginRouter.TransportFilter filter, WildernessZones w)
	{
		return new PluginRouter.TransportFilter()
		{
			@Override
			public boolean excluded(Transport t, TransportLine line)
			{
				return filter.excluded(t, line) || w.isWilderness(t.getEndX(), t.getEndY())
					|| t.hasStart() && w.isWilderness(t.getStartX(), t.getStartY());
			}

			@Override
			public int tickChange(String transportId)
			{
				return filter.tickChange(transportId);
			}

			@Override
			public int teleportCategoryTickChange(String category)
			{
				return filter.teleportCategoryTickChange(category);
			}

			@Override
			public int networkTickChange(String network)
			{
				return filter.networkTickChange(network);
			}

			@Override
			public int coinsTickChange()
			{
				return filter.coinsTickChange();
			}

			@Override
			public boolean avoidWilderness()
			{
				return true;
			}
		};
	}

	/** A teleport's Wilderness limit (null = none, or not a teleport line). */
	static Integer wildernessLimit(PlayerTransports.Result usable, Transport t)
	{
		TransportLine line = usable.lineOf(t);
		return line == null || line.getKind() != TransportLine.Kind.TELEPORT ? null : line.getWildernessLimit();
	}

	/** The filter's tick changes for {@link PlayerTransports#build(PlayerState, int, FreeLand, PlayerTransports.TickChanges)}. */
	static PlayerTransports.TickChanges ticksOf(PluginRouter.TransportFilter filter)
	{
		if (filter == null || filter == PluginRouter.TransportFilter.NONE)
		{
			return null;
		}
		return new PlayerTransports.TickChanges()
		{
			@Override
			public int transport(String transportId)
			{
				return filter.tickChange(transportId);
			}

			@Override
			public int teleportCategory(String category)
			{
				return filter.teleportCategoryTickChange(category);
			}

			@Override
			public int network(String network)
			{
				return filter.networkTickChange(network);
			}

			@Override
			public int coins()
			{
				return filter.coinsTickChange();
			}
		};
	}

	/**
	 * The transports of {@code list} (one of usable's lists) minus the excluded ones (a split two-way transport's way
	 * back goes with its id) and those the filter leaves out.
	 */
	static List<Transport> allowed(PlayerTransports.Result usable, List<Transport> list, Predicate<String> excluded,
		PluginRouter.TransportFilter filter)
	{
		List<Transport> allowed = new ArrayList<>(list.size());
		for (Transport t : list)
		{
			String id = t.getId();
			if (!excluded.test(id) && !(id.endsWith(":back") && excluded.test(id.substring(0, id.length() - 5)))
				&& !filter.excluded(t, usable.lineOf(t)))
			{
				allowed.add(t);
			}
		}
		return allowed;
	}
}
