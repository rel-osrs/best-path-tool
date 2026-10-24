package com.bestpath.core.collision;

import static com.bestpath.core.collision.CollisionFlags.BLOCKED;
import static com.bestpath.core.collision.CollisionFlags.EAST;
import static com.bestpath.core.collision.CollisionFlags.NORTH;
import static com.bestpath.core.collision.CollisionFlags.NORTH_EAST;
import static com.bestpath.core.collision.CollisionFlags.NORTH_WEST;
import static com.bestpath.core.collision.CollisionFlags.SOUTH;
import static com.bestpath.core.collision.CollisionFlags.SOUTH_EAST;
import static com.bestpath.core.collision.CollisionFlags.SOUTH_WEST;
import static com.bestpath.core.collision.CollisionFlags.WEST;

/**
 * Walking rules for a 1x1 walker (the player), from per-tile flags.
 *
 * <p>Entering a tile is blocked if it is blocked, or has a wall on the side
 * you come from. A diagonal step also needs both orthogonal neighbours to be
 * enterable from the start tile, and the destination must have no wall or
 * corner pillar facing the start tile (no corner cutting).
 *
 * <p>Our model is symmetric: a step is allowed only if the reverse step is
 * also allowed. (The client checks only the destination, which differs only
 * when standing on a blocked tile.)
 */
public final class MovementRules
{
	/** Supplies flags for a world tile; tiles with no data return {@link CollisionFlags#BLOCKED}. */
	public interface Flags
	{
		int get(int x, int y);
	}

	private MovementRules()
	{
	}

	/** Can a walker move from (x, y) by (dx, dy), each -1, 0 or 1, on one plane? */
	public static boolean canStep(Flags f, int x, int y, int dx, int dy)
	{
		return oneWay(f, x, y, dx, dy) && oneWay(f, x + dx, y + dy, -dx, -dy);
	}

	private static boolean oneWay(Flags f, int x, int y, int dx, int dy)
	{
		if (dx == 0 && dy == 0)
		{
			return true;
		}
		int tx = x + dx;
		int ty = y + dy;
		if (dx == 0 || dy == 0)
		{
			return (f.get(tx, ty) & (BLOCKED | facing(dx, dy))) == 0;
		}
		// diagonal: destination, then both orthogonal neighbours
		int destMask = BLOCKED | facing(dx, 0) | facing(0, dy) | facingCorner(dx, dy);
		return (f.get(tx, ty) & destMask) == 0
			&& (f.get(tx, y) & (BLOCKED | facing(dx, 0))) == 0
			&& (f.get(x, ty) & (BLOCKED | facing(0, dy))) == 0;
	}

	/** The wall side of the destination that faces a walker moving (dx, dy) orthogonally. */
	private static int facing(int dx, int dy)
	{
		if (dx > 0)
		{
			return WEST;
		}
		if (dx < 0)
		{
			return EAST;
		}
		return dy > 0 ? SOUTH : NORTH;
	}

	/** The corner pillar of the destination that faces a walker moving diagonally. */
	private static int facingCorner(int dx, int dy)
	{
		if (dx > 0)
		{
			return dy > 0 ? SOUTH_WEST : NORTH_WEST;
		}
		return dy > 0 ? SOUTH_EAST : NORTH_EAST;
	}
}
