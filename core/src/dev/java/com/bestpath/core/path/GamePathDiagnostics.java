package com.bestpath.core.path;

/**
 * What development code works out about the game's click pathing ({@link GamePath}) that the plugin itself never
 * needs.
 */
public final class GamePathDiagnostics
{
	private GamePathDiagnostics()
	{
	}

	/**
	 * Where a player clicking the end of this path stops: the game walks to the path's 25th corner at most
	 * (a corner = a tile where the direction changes; the clicked tile is the last). Returns the index in the
	 * path of that tile (the last index when the path has at most 25).
	 */
	public static int clickEnd(int[] path)
	{
		int corners = 0;
		for (int i = 1; i < path.length - 1; i++)
		{
			int dx1 = Tiles.x(path[i]) - Tiles.x(path[i - 1]);
			int dy1 = Tiles.y(path[i]) - Tiles.y(path[i - 1]);
			int dx2 = Tiles.x(path[i + 1]) - Tiles.x(path[i]);
			int dy2 = Tiles.y(path[i + 1]) - Tiles.y(path[i]);
			if ((dx1 != dx2 || dy1 != dy2) && ++corners == GamePath.CHECKPOINTS)
			{
				return i;
			}
		}
		return path.length - 1;
	}
}
