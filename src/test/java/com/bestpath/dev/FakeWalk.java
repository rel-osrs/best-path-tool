package com.bestpath.dev;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStep;
import java.util.ArrayList;
import java.util.List;

/**
 * DEV ONLY: a fake player walking along a route, one tile per game tick, for
 * the UI preview and pictures: every walk tile in turn; at a transport, a tick
 * where it is used, then the landing (or the next walk's first tile).
 */
final class FakeWalk
{
	private FakeWalk()
	{
	}

	/** The player's packed tile at each tick of walking the whole route. */
	static List<Integer> positions(RouteResult r)
	{
		List<Integer> out = new ArrayList<>();
		for (RouteStep s : r.getSteps())
		{
			if (s.getKind().isTransport())
			{
				if (s.getMarkerTile() != -1)
				{
					out.add(s.getMarkerTile()); // a tick to open the door / climb
				}
				else if (!out.isEmpty())
				{
					out.add(out.get(out.size() - 1)); // casting a teleport where one stands
				}
				if (s.getEndTile() != -1)
				{
					out.add(s.getEndTile());
				}
				continue;
			}
			for (int i = 0; i < s.tileCount(); i++)
			{
				out.add(s.tile(i));
			}
		}
		return out;
	}

	/** "3212, 3424, 0" */
	static String label(int tile)
	{
		return Tiles.x(tile) + ", " + Tiles.y(tile) + ", " + Tiles.plane(tile);
	}
}
