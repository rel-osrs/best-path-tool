package com.bestpath.core.path;

import java.util.Arrays;

/**
 * The route's real destination tile, settled once before any search. Every search for that route (the route with what
 * the player carries, the bank-first route's parts, sailing, each place of a several-places route) goes to this one
 * tile; none picks its own end afterwards. Otherwise routes that end in different places (say, a sailing route beside
 * a tile no one can stand on and a walking route to the nearest tile on land) would be compared as if they went to the
 * same place. Immutable.
 *
 * <p>The rules (no search, only the coarse "can it be reached" checks):
 * <ol>
 * <li>the tile itself when it can be reached (walking and the given transports), else the nearest tile within
 * range that can and has it in sight, else the nearest such tile ({@link Pathfinder#routeEnd}); without sailing,
 * that is all;</li>
 * <li>with sailing, a tile of open sea (the main ocean): the tile itself, reached on the boat, with rule 1's tile as
 * the {@link #getFallback() fallback} (the boat may not get there: its seas, its parts);</li>
 * <li>with sailing, any other tile: the boat only goes where the player can get off (a dock) or to the open ocean, so
 * this is rule 1 with the land tile of every dock the player may use counted as reachable ({@link
 * Sailing#routeEndByDocks}): the boat takes part only through docks, sailing to one and walking on. When that tile
 * differs from rule 1's, it is only reached by boat, with rule 1's tile as the fallback;</li>
 * <li>with sailing, a blocked spot out in the open ocean (a rock with no tile that can be stood on within range
 * having it in sight), which still gets a route: when rule 3 gives no tile in sight,
 * the nearest open water one of the player's boats can be at ({@link Sailing#openWaterNear}), the route ending there
 * on the boat ({@link #OPEN_WATER} in {@link #getStop()}), with rule 3's tile (if any) as the fallback (which may
 * have its own fallback).</li>
 * </ol>
 * The boat never ends beside a rock, the coast or a tile no one can stand on: only at a dock or on open sea. The
 * transports given should be every one the route may use (with a bank first: the bank's too), so the tile is the
 * same for the route with and without the bank.
 */
public final class RouteDestination
{
	private final int[] tile;
	private final boolean atSea;
	private final boolean bySea;
	private final RouteDestination fallback;

	private RouteDestination(int[] tile, boolean atSea, boolean bySea, RouteDestination fallback)
	{
		this.tile = tile;
		this.atSea = atSea;
		this.bySea = bySea;
		this.fallback = fallback;
	}

	/**
	 * {@link #getStop()}'s last value for rule 4: the spot itself is blocked (a rock out at sea), and the route stops on
	 * open water near it.
	 */
	public static final int OPEN_WATER = 2;

	/** {x, y, plane, inSight 1/0, or {@link #OPEN_WATER}}: where every search of the route goes. */
	public int[] getStop()
	{
		return tile.clone();
	}

	public int getX()
	{
		return tile[0];
	}

	public int getY()
	{
		return tile[1];
	}

	public int getPlane()
	{
		return tile[2];
	}

	/** True when the route ends on the boat on open sea (rules 2 and 4); never beside land. */
	public boolean isAtSea()
	{
		return atSea;
	}

	/**
	 * True for a tile only a boat may bring the player to (rules 2, 3 and 4): when no route reaches it, the route goes to
	 * the {@link #getFallback() fallback} instead (or, without one, there is no route).
	 */
	public boolean isBySea()
	{
		return bySea;
	}

	/**
	 * Settled with it, before any search: where the route goes instead when no route at all reaches this tile (only
	 * for a tile a boat might bring the player to, {@link #isBySea()}), or null.
	 */
	public RouteDestination getFallback()
	{
		return fallback;
	}

	/**
	 * The destination without a boat: this one, or the first of its fallbacks that is not only reached by boat (the
	 * walking rule's tile); null when there is none.
	 */
	public RouteDestination onFoot()
	{
		RouteDestination e = this;
		while (e != null && e.bySea)
		{
			e = e.fallback;
		}
		return e;
	}

	/**
	 * @param q             the player's tile, the asked-for destination, and every transport the route may use
	 * @param sail          sea routes, or null (walking only)
	 * @param o             sailing's options (with {@code sail})
	 * @param ownRegionOnly see {@link Pathfinder#routeEnd(PathQuery, int, LineOfSight.WallFlags, boolean)}
	 * @return the destination, or null when nothing near it can be reached
	 */
	public static RouteDestination resolve(Pathfinder pf, Sailing sail, Sailing.Options o, PathQuery q, int range,
		LineOfSight.WallFlags walls, boolean ownRegionOnly)
	{
		int dx = q.getDestX();
		int dy = q.getDestY();
		int dp = q.getDestPlane();
		int[] itself = {dx, dy, dp, 1};
		int[] stop = pf.routeEnd(q, range, walls, ownRegionOnly);
		RouteDestination near = stop == null ? null : new RouteDestination(stop, false, false, null);
		if (sail == null)
		{
			return near;
		}
		// 2. open sea: on the boat, there
		if (sail.isOpenSea(dx, dy, dp))
		{
			return new RouteDestination(itself, true, true, near);
		}
		// 3. anything else is reached on land; the boat only through docks
		RouteDestination land = near;
		if (stop == null || stop[0] != dx || stop[1] != dy || stop[2] != dp)
		{
			int[] byDocks = sail.routeEndByDocks(q, o, pf, range, walls, ownRegionOnly);
			if (byDocks != null && !Arrays.equals(byDocks, stop))
			{
				land = new RouteDestination(byDocks, false, true, near);
			}
		}
		// 4. a blocked spot out in the ocean, nothing that can be stood on in sight of it: the nearest open water
		if ((land == null || land.tile[3] != 1) && pf.getGraph().componentAt(dx, dy, dp) < 0
			&& !standableInSight(pf, walls, dx, dy, dp, range, ownRegionOnly))
		{
			int[] water = o == null ? null : sail.openWaterNear(dx, dy, dp, o.boats);
			if (water != null)
			{
				return new RouteDestination(new int[]{water[0], water[1], dp, OPEN_WATER}, true, true, land);
			}
		}
		return land;
	}

	/** Whether a tile within range that can be stood on (on the walking map) has the spot in sight. */
	private static boolean standableInSight(Pathfinder pf, LineOfSight.WallFlags walls, int dx, int dy, int dp, int range,
		boolean ownRegionOnly)
	{
		for (int x = dx - range; x <= dx + range; x++)
		{
			for (int y = dy - range; y <= dy + range; y++)
			{
				if (ownRegionOnly && ((x >> 6) != (dx >> 6) || (y >> 6) != (dy >> 6)))
				{
					continue;
				}
				if (pf.getGraph().componentAt(x, y, dp) >= 0 && LineOfSight.clear(walls, dp, x, y, dx, dy))
				{
					return true;
				}
			}
		}
		return false;
	}
}
