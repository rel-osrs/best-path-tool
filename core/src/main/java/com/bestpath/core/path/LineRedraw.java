package com.bestpath.core.path;

/**
 * Redraws the walk ahead of the player with the game's own pathing from the
 * tile they actually stand on, so that a click on
 * the drawn line walks along it. Only the drawn line moves: the route, its
 * ticks and its steps stay as they are, and the line keeps the walk's length
 * (tile i of the line is always i steps from the walk's start).
 *
 * <p>Rules, each time the player's tile (or the game's destination flag)
 * changes:
 * <ul>
 * <li>Walking to a tile of the line (the game's flag is on it): the line up
 * to that tile becomes the game's path from here to it, which is exactly
 * what the game walks (the rest of a game path is the game path from any
 * tile on it). Nothing beyond the flag changes.</li>
 * <li>Walking somewhere else (the flag is off the line, or beyond what the
 * line reaches with no extra steps): nothing changes until the player
 * stops.</li>
 * <li>Standing still: the line from here becomes the game's path from here
 * to the furthest tile of the walk it reaches with no extra steps, so the
 * next click on it is walked as drawn. That far tile is kept while it is at
 * least {@link #LOOK_AHEAD} steps ahead; re-choosing it at every stop would
 * shift a long walk's far part a little at each stop.</li>
 * </ul>
 * When the new tiles are the same as the drawn ones, the very same array is
 * returned (nothing to publish). A player more than {@link #MAX_OFF} tiles
 * from the line is left to the route's own "off the route" check.
 *
 * <p>Why this never flips between two shapes: while the player walks a
 * click, the line to the flag is the path the game walks, which stays the
 * same from every tile along it; it changes only when the player clicks or
 * stops, and then to the game's path from where they are.
 *
 * <p>One per walk being followed (it remembers that far tile). Not
 * thread-safe.
 */
public final class LineRedraw
{
	/** Standing still, the stretch known to be the game's path is kept at least this many steps ahead. */
	public static final int LOOK_AHEAD = 32;
	/** Further than this (tiles, either axis) from the line's tile for where they are: no redraw. */
	public static final int MAX_OFF = 5;

	/** Index of the line up to which it is the game's path from the player's tile; -1 = not known. */
	private int sameTo = -1;

	/**
	 * @param line   the drawn tiles of the walk (packed); not changed
	 * @param from   index of the first tile still ahead (the ones before it are walked)
	 * @param player the player's tile (packed)
	 * @param flag   the tile the game is walking the player to (its destination flag), or -1 when standing still
	 * @return the tiles to draw: {@code line} itself when nothing differs, else a new array of the same length
	 */
	public int[] redraw(GamePath game, int[] line, int from, int player, int flag)
	{
		int n = line.length;
		if (n < 2 || from >= n - 1 || Tiles.plane(player) != Tiles.plane(line[n - 1]))
		{
			return line;
		}
		game.search(player, n - 1 - from); // the player is at least at "from": nothing ahead is further
		// where the player is along the line: index s such that some tile j ahead is j - s steps away
		int s = Integer.MIN_VALUE;
		for (int j = from; j < n; j++)
		{
			int d = game.steps(line[j]);
			if (d >= 0)
			{
				s = Math.max(s, j - d);
			}
		}
		if (s < from || s >= n - 1 || off(line[s], player) > MAX_OFF)
		{
			return line;
		}
		int to = -1;
		if (flag != -1)
		{
			for (int j = s + 1; j < n && to == -1; j++)
			{
				if (line[j] == flag)
				{
					to = game.steps(flag) == j - s ? j : -2;
				}
			}
			if (to < 0)
			{
				return line; // walking somewhere the line does not go (yet): redraw when the player stops
			}
		}
		else if (sameTo - s >= LOOK_AHEAD && sameTo < n && game.steps(line[sameTo]) == sameTo - s)
		{
			to = sameTo;
		}
		else
		{
			for (int j = n - 1; j > s && to == -1; j--)
			{
				if (game.steps(line[j]) == j - s)
				{
					to = j;
				}
			}
			if (to == -1)
			{
				return line;
			}
		}
		int[] path = game.pathTo(line[to]);
		boolean same = true;
		for (int i = 0; i < path.length && same; i++)
		{
			same = path[i] == line[s + i];
		}
		if (same)
		{
			sameTo = Math.max(sameTo, to);
			return line;
		}
		int[] out = line.clone();
		System.arraycopy(path, 0, out, s, path.length);
		sameTo = to;
		return out;
	}

	private static int off(int a, int b)
	{
		return Math.max(Math.abs(Tiles.x(a) - Tiles.x(b)), Math.abs(Tiles.y(a) - Tiles.y(b)));
	}
}
