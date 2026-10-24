package com.bestpath.core.path;

import com.bestpath.core.collision.CollisionFlags;

/**
 * Can one tile be seen from another? Only walls block the view (by design):
 * objects, trees and the like do not. The line
 * runs from tile centre to tile centre, one tile step at a time; a straight
 * step is blocked by a wall on the edge it crosses, a diagonal step only if
 * both ways around the corner are walled.
 */
public final class LineOfSight
{
	/** Raw tile flags (only the wall bits are used). */
	public interface WallFlags
	{
		int get(int x, int y, int plane);
	}

	private LineOfSight()
	{
	}

	public static boolean clear(WallFlags w, int plane, int x0, int y0, int x1, int y1)
	{
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx = Integer.signum(x1 - x0);
		int sy = Integer.signum(y1 - y0);
		int x = x0;
		int y = y0;
		int err = dx - dy;
		while (x != x1 || y != y1)
		{
			int e2 = 2 * err;
			int stepX = 0;
			int stepY = 0;
			if (e2 > -dy)
			{
				err -= dy;
				stepX = sx;
			}
			if (e2 < dx)
			{
				err += dx;
				stepY = sy;
			}
			if (stepX != 0 && stepY != 0)
			{
				boolean viaX = !edge(w, plane, x, y, stepX, 0) && !edge(w, plane, x + stepX, y, 0, stepY);
				boolean viaY = !edge(w, plane, x, y, 0, stepY) && !edge(w, plane, x, y + stepY, stepX, 0);
				if (!viaX && !viaY)
				{
					return false;
				}
			}
			else if (edge(w, plane, x, y, stepX, stepY))
			{
				return false;
			}
			x += stepX;
			y += stepY;
		}
		return true;
	}

	/** Is there a wall between (x, y) and its neighbour (x + dx, y + dy), a straight step? */
	private static boolean edge(WallFlags w, int plane, int x, int y, int dx, int dy)
	{
		int here = w.get(x, y, plane);
		int there = w.get(x + dx, y + dy, plane);
		if (dx == 1)
		{
			return (here & CollisionFlags.EAST) != 0 || (there & CollisionFlags.WEST) != 0;
		}
		if (dx == -1)
		{
			return (here & CollisionFlags.WEST) != 0 || (there & CollisionFlags.EAST) != 0;
		}
		if (dy == 1)
		{
			return (here & CollisionFlags.NORTH) != 0 || (there & CollisionFlags.SOUTH) != 0;
		}
		return (here & CollisionFlags.SOUTH) != 0 || (there & CollisionFlags.NORTH) != 0;
	}
}
