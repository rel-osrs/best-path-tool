package com.bestpath.core.path;

/**
 * Shapes a found walk the way the game walks it: its
 * tiles become the game's own click path ({@link GamePath}) from the
 * walk's first tile, as far as that path reaches a tile of the walk with no
 * extra steps; a walk longer than the game's search square is cut there, at
 * the furthest such tile, and the next part is shaped from that tile (like a
 * player clicking that far ahead, then again). Cuts are as rare as possible.
 *
 * <p>Steps the game's click path cannot take are kept as they are and split
 * the walk: a step through a door (a closed door blocks the click; the
 * player opens it, then clicks on beyond it), and a step off a tile that is
 * not in the movement data (a fairy ring's middle). Each piece between them
 * is shaped on its own, with every door shut.
 *
 * <p>Same number of steps in every piece, and the kept steps unchanged, so
 * a shaped walk has the same ticks (door costs included) as the found one.
 */
final class GameWalk
{
	private GameWalk()
	{
	}

	/** The walk's tiles in the game's shape (a new array of the same length; the walk itself is not changed). */
	static int[] shape(GamePath game, int[] walk)
	{
		int[] out = walk.clone();
		int n = walk.length;
		int a = 0;
		while (a < n - 1)
		{
			int b = a;
			while (b < n - 1 && game.isPlainStep(walk[b], walk[b + 1]))
			{
				b++;
			}
			shapePiece(game, walk, a, b, out);
			a = b + 1; // the step b -> b + 1 (a door or a step off a solid tile) is kept
		}
		return out;
	}

	/** Tiles a..b of the walk (plain steps only) into out[a..b]: game paths between as few cut tiles as possible. */
	private static void shapePiece(GamePath game, int[] walk, int a, int b, int[] out)
	{
		int at = a;
		while (at < b)
		{
			game.search(walk[at], b - at);
			// the furthest tile of the walk the game's path reaches with no extra steps (the next tile always is)
			int to = at + 1;
			for (int k = b; k > at + 1; k--)
			{
				if (game.steps(walk[k]) == k - at)
				{
					to = k;
					break;
				}
			}
			int[] path = game.pathTo(walk[to]);
			System.arraycopy(path, 1, out, at + 1, to - at);
			at = to;
		}
	}
}
