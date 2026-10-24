package com.bestpath.core.data;

import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.sea.SeaGrid;
import java.util.List;

/**
 * Facts about the places of a "no route" question ({@link NoRouteReasons}): whether anything near them can be stood
 * on, whether they are at sea, and how far they are from the player.
 */
final class NoRoutePlaces
{
	private NoRoutePlaces()
	{
	}

	/**
	 * True when something within {@link PluginRouter#NEAR_RANGE} tiles of the place (above the main map: inside its
	 * own 64x64 region, as the route end) can be walked on, or a transport lands there.
	 */
	static boolean standable(CoarseGraph graph, List<Transport> all, int dx, int dy, int dp)
	{
		int r = PluginRouter.NEAR_RANGE;
		boolean own = dy >= PluginRouter.MAIN_MAP_TOP_Y;
		for (int x = dx - r; x <= dx + r; x++)
		{
			for (int y = dy - r; y <= dy + r; y++)
			{
				if (own && ((x >> 6) != (dx >> 6) || (y >> 6) != (dy >> 6)))
				{
					continue;
				}
				if (graph.componentAt(x, y, dp) >= 0)
				{
					return true;
				}
			}
		}
		for (Transport t : all)
		{
			if (near(t.getEndX(), t.getEndY(), t.getEndPlane(), dx, dy, dp, own)
				|| t.isTwoWay() && near(t.getStartX(), t.getStartY(), t.getStartPlane(), dx, dy, dp, own))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean near(int x, int y, int p, int dx, int dy, int dp, boolean own)
	{
		return p == dp && Math.abs(x - dx) <= PluginRouter.NEAR_RANGE && Math.abs(y - dy) <= PluginRouter.NEAR_RANGE
			&& (!own || (x >> 6) == (dx >> 6) && (y >> 6) == (dy >> 6));
	}

	/** The first of the places (on the player's land) in or next to the open sea, or -1. */
	static int firstAtSea(PluginData data, int[] targets, List<Integer> onLand)
	{
		for (int i : onLand)
		{
			if (atSea(data, Tiles.x(targets[i]), Tiles.y(targets[i]), Tiles.plane(targets[i])))
			{
				return i;
			}
		}
		return -1;
	}

	/** The place is in the open sea, or open sea is within {@link PluginRouter#NEAR_RANGE} tiles (plane 0, the data's sea). */
	static boolean atSea(PluginData data, int dx, int dy, int dp)
	{
		SeaBits.Data sea = data.getSea();
		if (sea == null || dp != 0)
		{
			return false;
		}
		SeaGrid g = sea.getGrid();
		int r = PluginRouter.NEAR_RANGE;
		for (int x = dx - r; x <= dx + r; x++)
		{
			for (int y = dy - r; y <= dy + r; y++)
			{
				if (g.isOcean(x, y))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Straight-line distance from the player (another plane counts as far), for the order of several places. */
	static long distance(int sx, int sy, int sp, int target)
	{
		return Math.max(Math.abs(Tiles.x(target) - sx), Math.abs(Tiles.y(target) - sy)) + (Tiles.plane(target) != sp ? 100_000L : 0);
	}
}
