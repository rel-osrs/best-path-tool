package com.bestpath.overlay;

import com.bestpath.core.path.Tiles;

/**
 * Where a walk turns, for the {@link PathStyle#ARROWS} style: the walk is cut into straight runs, and each run gets
 * one arrow head on its last tile, pointing its way (as the boat's course has one per leg). A turn counts only when the
 * new heading holds for at least two steps, so a staircase (straight, diagonal, straight...) is one run, not an arrow
 * on every tile. Pure and allocation-free: worked out on the client thread per frame from tiles already published.
 */
public final class PathTurns
{
	private PathTurns()
	{
	}

	/** The heading of one step (0-7), or -1 when the two tiles are not neighbours (a jump: no heading). */
	static int heading(int a, int b)
	{
		int dx = Tiles.x(b) - Tiles.x(a);
		int dy = Tiles.y(b) - Tiles.y(a);
		if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || dx == 0 && dy == 0)
		{
			return -1;
		}
		return (dx + 1) * 3 + (dy + 1);
	}

	/**
	 * The indices (into {@code tiles}, from {@code from} on, below {@code n}) of the tiles where a straight run ends:
	 * every turn whose new heading holds at least two steps, and the walk's last tile. Written to {@code out}; returns
	 * how many (at most {@code out.length}).
	 */
	public static int runEnds(int[] tiles, int from, int n, int[] out)
	{
		int k = 0;
		if (n - from < 2)
		{
			return 0;
		}
		int run = heading(tiles[from], tiles[from + 1]);
		for (int i = from + 1; i + 1 < n && k < out.length; i++)
		{
			int h = heading(tiles[i], tiles[i + 1]);
			if (h == run)
			{
				continue;
			}
			boolean holds = i + 2 >= n || heading(tiles[i + 1], tiles[i + 2]) == h;
			if (holds)
			{
				out[k++] = i;
				run = h;
			}
		}
		if (k < out.length)
		{
			out[k++] = n - 1;
		}
		return k;
	}
}
