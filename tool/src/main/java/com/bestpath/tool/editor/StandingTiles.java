package com.bestpath.tool.editor;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.transport.TransportCandidate;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a player stands to use an object, and which tiles a transport may start or land on, from the editor's
 * current data (the flag grid, the sub-regions and which of them are hidden or closed-off pockets).
 */
final class StandingTiles
{
	private StandingTiles()
	{
	}

	/** See {@link EditorContext#standingTile}. */
	static int[] standingTile(EditorContext ctx, TransportCandidate c, int towardX, int towardY)
	{
		// first a tile right beside a solid object with no wall in between, where a player stands to use it
		// (not diagonally past a wall corner, nor in the next building because the object's own room is a
		// pocket: a pocket tile here is taken anyway, so the check shows it as not usable instead of hiding it)
		int[] facing = facingTile(ctx, c, towardX, towardY);
		if (facing != null)
		{
			return facing;
		}
		// else the nearest usable tile next to the object, else up to 3 tiles out; never a blocked tile or a
		// hidden sub-region, and a closed-off pocket only if nothing else will do
		for (int pass = 0; pass < 2; pass++)
		{
			for (int ring = 1; ring <= 3; ring++)
			{
				int[] best = nearestInRing(ctx, c, ring, towardX, towardY, pass == 1);
				if (best != null)
				{
					return best;
				}
			}
		}
		return null;
	}

	/** The usable tile at most {@code ring} tiles from the object's outline nearest to (towardX, towardY), or null. */
	private static int[] nearestInRing(EditorContext ctx, TransportCandidate c, int ring, int towardX, int towardY,
		boolean pocketsAllowed)
	{
		int[] best = null;
		int bestD = Integer.MAX_VALUE;
		for (int x = c.getX() - ring; x <= c.getX() + c.getSizeX() - 1 + ring; x++)
		{
			for (int y = c.getY() - ring; y <= c.getY() + c.getSizeY() - 1 + ring; y++)
			{
				if (!usable(ctx, x, y, c.getPlane(), pocketsAllowed))
				{
					continue;
				}
				int d = Math.max(Math.abs(x - towardX), Math.abs(y - towardY));
				if (d < bestD)
				{
					bestD = d;
					best = new int[]{x, y, c.getPlane()};
				}
			}
		}
		return best;
	}

	/**
	 * For an object that blocks its own tiles (a ladder, not a door in a wall): the usable tile of
	 * {@link #facingTiles} nearest to (towardX, towardY), preferring one outside a pocket; null if none.
	 */
	private static int[] facingTile(EditorContext ctx, TransportCandidate c, int towardX, int towardY)
	{
		for (int pass = 0; pass < 2; pass++)
		{
			int[] best = null;
			int bestD = Integer.MAX_VALUE;
			for (int[] t : facingTiles(ctx, c))
			{
				if (!usable(ctx, t[0], t[1], t[2], pass == 1))
				{
					continue;
				}
				int dist = Math.max(Math.abs(t[0] - towardX), Math.abs(t[1] - towardY));
				if (dist < bestD)
				{
					bestD = dist;
					best = t;
				}
			}
			if (best != null)
			{
				return best;
			}
		}
		return null;
	}

	/** See {@link EditorContext#facingTiles}. */
	static List<int[]> facingTiles(EditorContext ctx, TransportCandidate c)
	{
		List<int[]> out = new ArrayList<>();
		if (ctx.grid == null)
		{
			return out;
		}
		int p = c.getPlane();
		int x0 = c.getX();
		int y0 = c.getY();
		int x1 = x0 + c.getSizeX() - 1;
		int y1 = y0 + c.getSizeY() - 1;
		for (int x = x0; x <= x1; x++)
		{
			for (int y = y0; y <= y1; y++)
			{
				if ((ctx.grid.get(x, y, p) & CollisionFlags.BLOCKED) == 0)
				{
					return out;
				}
			}
		}
		int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
		int[] wallOut = {CollisionFlags.NORTH, CollisionFlags.EAST, CollisionFlags.SOUTH, CollisionFlags.WEST};
		int[] wallIn = {CollisionFlags.SOUTH, CollisionFlags.WEST, CollisionFlags.NORTH, CollisionFlags.EAST};
		for (int x = x0; x <= x1; x++)
		{
			for (int y = y0; y <= y1; y++)
			{
				for (int d = 0; d < 4; d++)
				{
					int nx = x + dirs[d][0];
					int ny = y + dirs[d][1];
					if (nx >= x0 && nx <= x1 && ny >= y0 && ny <= y1)
					{
						continue; // inside the footprint
					}
					// no wall on the object tile's side facing out, nor on the neighbour's side facing in
					if ((ctx.grid.get(x, y, p) & wallOut[d]) != 0 || (ctx.grid.get(nx, ny, p) & wallIn[d]) != 0
						|| ctx.graph == null || ctx.graph.componentAt(nx, ny, p) < 0)
					{
						continue;
					}
					out.add(new int[]{nx, ny, p});
				}
			}
		}
		return out;
	}

	/** See {@link EditorContext#usable}. */
	static boolean usable(EditorContext ctx, int x, int y, int plane, boolean pocketsAllowed)
	{
		int comp = ctx.graph == null ? -1 : ctx.graph.componentAt(x, y, plane);
		if (comp < 0 || (ctx.hidden != null && comp < ctx.hidden.length && ctx.hidden[comp]))
		{
			return false;
		}
		return pocketsAllowed || ctx.pockets == null || comp >= ctx.pockets.length || !ctx.pockets[comp];
	}
}
