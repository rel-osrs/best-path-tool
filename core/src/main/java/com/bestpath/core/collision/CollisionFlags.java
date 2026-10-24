package com.bestpath.core.collision;

/**
 * Per-tile movement flags, using the same bit values as the game client
 * (and RuneLite's {@code CollisionDataFlag}), so our extracted data can be
 * compared directly with in-game captures. Only movement bits are used.
 *
 * <p>A wall flag on a tile means a wall on that side of the tile; walls are
 * recorded on both tiles they separate. Corner flags (e.g. NORTH_EAST) are
 * wall pillars that only block diagonal steps through that corner.
 */
public final class CollisionFlags
{
	public static final int NORTH_WEST = 0x1;
	public static final int NORTH = 0x2;
	public static final int NORTH_EAST = 0x4;
	public static final int EAST = 0x8;
	public static final int SOUTH_EAST = 0x10;
	public static final int SOUTH = 0x20;
	public static final int SOUTH_WEST = 0x40;
	public static final int WEST = 0x80;
	/** Solid object (scenery, diagonal wall, closed object footprint). */
	public static final int OBJECT = 0x100;
	/** Blocking floor decoration. */
	public static final int FLOOR_DECORATION = 0x40000;
	/** Blocked floor, e.g. water. */
	public static final int FLOOR = 0x200000;

	/**
	 * Our own bit (not used by the game): the tile has no floor at all (empty
	 * void, e.g. most of an upper plane), so nobody can stand there.
	 */
	public static final int NO_FLOOR = 0x40000000;

	/** Any of these means nothing can stand on the tile. */
	public static final int BLOCKED = OBJECT | FLOOR_DECORATION | FLOOR | NO_FLOOR;
	public static final int WALLS = 0xFF;
	/** All bits this project uses for movement. */
	public static final int MOVEMENT_MASK = WALLS | BLOCKED;

	private CollisionFlags()
	{
	}
}
