package com.bestpath.core.data;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.Tiles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The map facts {@link PluginRouter} works out before or beside a search: the transport hubs off the walking map
 * that need an empty cell ({@link PluginRouter#searchMap}), and the plane a world-map tile means
 * ({@link PluginRouter#mapPlane(CoarseGraph, int, int)}).
 */
final class SearchMaps
{
	private SearchMaps()
	{
	}

	/**
	 * The cells ({@code regionId << 2 | plane}) the map lacks that hold a transport hub: a tile off the walking map
	 * where one transport can arrive and another can leave. The export leaves out cells with no walkable tile, and the
	 * search only chains transports on a tile of a stored cell. Without this the player-owned house spot (1857,7065,0)
	 * would never be used: Teleport to House, the house tablet and the house portals land there, and the jewellery box,
	 * nexus, portals, mounted items and obelisk leave from there. A tile reached and left only by the same two-way
	 * transport changes nothing and is not added.
	 */
	static Set<Integer> hubCells(CollisionMap map, List<TransportLine> lines)
	{
		Set<Integer> stored = new HashSet<>();
		for (int k : map.cellKeys())
		{
			stored.add(k);
		}
		// per off-map tile: the lines that can arrive there and the lines that can leave from there
		Map<Integer, Set<String>> in = new HashMap<>();
		Map<Integer, Set<String>> out = new HashMap<>();
		for (TransportLine l : lines)
		{
			// a network stop is ridden to and from (rides go from every stop to every other)
			boolean both = l.getKind() == TransportLine.Kind.TWO_WAY || l.getKind() == TransportLine.Kind.NETWORK;
			int end = Tiles.pack(l.getEndX(), l.getEndY(), l.getEndPlane());
			if (!stored.contains(cellOf(end)))
			{
				in.computeIfAbsent(end, k -> new HashSet<>()).add(l.getId());
				if (both)
				{
					out.computeIfAbsent(end, k -> new HashSet<>()).add(l.getId());
				}
			}
			if (l.hasStart() && l.getKind() != TransportLine.Kind.NETWORK)
			{
				int start = Tiles.pack(l.getStartX(), l.getStartY(), l.getStartPlane());
				if (!stored.contains(cellOf(start)))
				{
					out.computeIfAbsent(start, k -> new HashSet<>()).add(l.getId());
					if (both)
					{
						in.computeIfAbsent(start, k -> new HashSet<>()).add(l.getId());
					}
				}
			}
		}
		Set<Integer> cells = new TreeSet<>();
		in.forEach((tile, arriving) ->
		{
			Set<String> leaving = out.get(tile);
			// a hub: something arrives and something else leaves (not only the same two-way transport back)
			if (leaving != null && (arriving.size() > 1 || leaving.size() > 1 || !arriving.equals(leaving)))
			{
				cells.add(cellOf(tile));
			}
		});
		return cells;
	}

	/** The cell key ({@code regionId << 2 | plane}) of a packed tile. */
	private static int cellOf(int tile)
	{
		int x = Tiles.x(tile);
		int y = Tiles.y(tile);
		return ((x >> 6) << 8 | (y >> 6)) << 2 | Tiles.plane(tile);
	}

	/** The plane a world-map tile means: see {@link PluginRouter#mapPlane(CoarseGraph, int, int)}. */
	static int mapPlane(CoarseGraph graph, int x, int y)
	{
		int best = 0;
		int bestDistance = Integer.MAX_VALUE;
		for (int p = 0; p < 4; p++)
		{
			int d = groundDistance(graph, x, y, p);
			if (d < 0)
			{
				continue;
			}
			if (p == 0)
			{
				return 0;
			}
			if (d < bestDistance)
			{
				best = p;
				bestDistance = d;
			}
		}
		return best;
	}

	/** How far (in tiles, either way) the nearest tile with ground on this plane is, up to the range; -1 if none. */
	private static int groundDistance(CoarseGraph graph, int x, int y, int plane)
	{
		boolean own = y >= PluginRouter.MAIN_MAP_TOP_Y;
		for (int r = 0; r <= PluginRouter.NEAR_RANGE; r++)
		{
			for (int tx = x - r; tx <= x + r; tx++)
			{
				// the ring r tiles away: its top and bottom rows whole, the columns between only at the two ends
				int step = tx == x - r || tx == x + r ? 1 : Math.max(1, 2 * r);
				for (int ty = y - r; ty <= y + r; ty += step)
				{
					if (own && ((tx >> 6) != (x >> 6) || (ty >> 6) != (y >> 6)))
					{
						continue;
					}
					if (graph.componentAt(tx, ty, plane) >= 0)
					{
						return r;
					}
				}
			}
		}
		return -1;
	}
}
