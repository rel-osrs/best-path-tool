package com.bestpath.core.path;

import com.bestpath.core.collision.MovementRules;

/**
 * Makes {@link DoorMap}s from per-tile flags with the doors open and shut, when the data is made.
 */
public final class DoorMapBuilder
{
	private DoorMapBuilder()
	{
	}

	/**
	 * @param open      flags per plane with the doors open (as the routes use them)
	 * @param shut      flags per plane with every door shut
	 * @param doorTiles the doors' tiles {x, y, plane}
	 */
	public static DoorMap build(MovementRules.Flags[] open, MovementRules.Flags[] shut, Iterable<int[]> doorTiles)
	{
		DoorMap m = new DoorMap();
		for (int[] door : doorTiles)
		{
			int p = door[2];
			// the door's own tile and its neighbours: every step between them that a shut door blocks
			for (int x = door[0] - 1; x <= door[0] + 1; x++)
			{
				for (int y = door[1] - 1; y <= door[1] + 1; y++)
				{
					for (int d = 0; d < 8; d++)
					{
						if (MovementRules.canStep(open[p], x, y, DoorMap.DX[d], DoorMap.DY[d])
							&& !MovementRules.canStep(shut[p], x, y, DoorMap.DX[d], DoorMap.DY[d]))
						{
							m.mark(x, y, p, d);
							m.mark(x + DoorMap.DX[d], y + DoorMap.DY[d], p, (d + 4) & 7); // and the way back
						}
					}
				}
			}
		}
		return m;
	}
}
