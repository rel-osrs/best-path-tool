package com.bestpath.tool.clue;

import com.bestpath.core.collision.CollisionFlags;

/**
 * The clue pathability check's rules, apart from the map loading and the
 * searches ({@link ClueCheckCli}): what kind of tile a clue location is, the
 * nearest tile a player can stand on, and the likely cause when a location
 * can't be reached. Pure functions over {@link MapView}, so tests can use a
 * small made-up map.
 */
public final class ClueCheck
{
	/** What the location's tile is on our walking map. */
	public enum Spot
	{
		/** In a usable sub-region: a player can stand here. */
		WALKABLE,
		/** Something solid (object, blocked floor), but the region has floor here. */
		BLOCKED,
		/** In a small closed-off pocket (left out of the plugin's data unless kept by hand). */
		POCKET,
		/** In a sub-region hidden by hand (left out of the plugin's data). */
		HIDDEN,
		/** In a 64x64 region marked ignored by hand (left out of the plugin's data). */
		IGNORED,
		/** No floor at all on this plane (empty upper plane). */
		NO_FLOOR,
		/** On the sea (walking can't reach it; a boat might). */
		AT_SEA,
		/** The cache has no map for this region (an instance, or outside the map). */
		OFF_MAP
	}

	/** The walking map as the check sees it. */
	public interface MapView
	{
		/** Whether the cache has map data for the 64x64 region. */
		boolean hasRegion(int regionId);

		boolean ignored(int regionId);

		boolean sea(int x, int y, int plane);

		/** Movement flags of the tile ({@link CollisionFlags}). */
		int flags(int x, int y, int plane);

		/** The walking sub-region (component) of the tile, or -1 when nobody can stand there. */
		int component(int x, int y, int plane);

		/** Pocket not kept by hand. */
		boolean pocket(int component);

		boolean hidden(int component);
	}

	private ClueCheck()
	{
	}

	public static int regionId(int x, int y)
	{
		return (x >> 6) << 8 | (y >> 6);
	}

	public static Spot classify(MapView m, int x, int y, int plane)
	{
		int region = regionId(x, y);
		if (!m.hasRegion(region))
		{
			return Spot.OFF_MAP;
		}
		if (m.sea(x, y, plane))
		{
			return Spot.AT_SEA;
		}
		if (m.ignored(region))
		{
			return Spot.IGNORED;
		}
		int c = m.component(x, y, plane);
		if (c < 0)
		{
			return (m.flags(x, y, plane) & CollisionFlags.NO_FLOOR) != 0 ? Spot.NO_FLOOR : Spot.BLOCKED;
		}
		if (m.hidden(c))
		{
			return Spot.HIDDEN;
		}
		return m.pocket(c) ? Spot.POCKET : Spot.WALKABLE;
	}

	/** Whether a player can stand on the tile in the plugin's data (walkable, not left out). */
	public static boolean standable(MapView m, int x, int y, int plane)
	{
		return classify(m, x, y, plane) == Spot.WALKABLE;
	}

	/**
	 * The nearest standable tile on the same plane within {@code radius}
	 * (square rings outward; within a ring the straight-line nearest, then the
	 * lowest x, then the lowest y), as {x, y, plane, ring distance}; null if none.
	 */
	public static int[] nearestStandable(MapView m, int x, int y, int plane, int radius)
	{
		for (int r = 0; r <= radius; r++)
		{
			int[] best = null;
			int bestD2 = Integer.MAX_VALUE;
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r)
					{
						continue;
					}
					int d2 = dx * dx + dy * dy;
					if (d2 < bestD2 && standable(m, x + dx, y + dy, plane))
					{
						bestD2 = d2;
						best = new int[]{x + dx, y + dy, plane, r};
					}
				}
			}
			if (best != null)
			{
				return best;
			}
		}
		return null;
	}

	/**
	 * Another plane (lowest first) where the same x,y is standable, or -1. Only a
	 * hint for the report (a source may give the wrong plane, e.g. the Wiki's
	 * dungeon map layers all say plane 0); the location itself is never moved.
	 */
	public static int otherStandablePlane(MapView m, int x, int y, int plane)
	{
		for (int p = 0; p < 4; p++)
		{
			if (p != plane && standable(m, x, y, p))
			{
				return p;
			}
		}
		return -1;
	}

	/**
	 * The only other plane where this x,y is standable, or -1 when there is none
	 * or more than one (then nothing can be suggested without guessing).
	 */
	public static int onlyOtherStandablePlane(MapView m, int x, int y, int plane)
	{
		int found = -1;
		for (int p = 0; p < 4; p++)
		{
			if (p != plane && standable(m, x, y, p))
			{
				if (found >= 0)
				{
					return -1;
				}
				found = p;
			}
		}
		return found;
	}

	/**
	 * A place the check also tries when the source's own tile has no floor on
	 * its plane. Only a suggestion for a person to confirm: the clue data is never changed.
	 */
	public static final class Suggestion
	{
		public final int x;
		public final int y;
		public final int plane;
		public final String reason;

		public Suggestion(int x, int y, int plane, String reason)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.reason = reason;
		}
	}

	/**
	 * The suggestion for a location, or null: when the tile has no floor on its
	 * plane and exactly one other plane is standable at the same x,y (e.g. the
	 * Wiki's God Wars Dungeon map layer says plane 0, the game's floor is on
	 * plane 2), that plane. Several standable planes (a windmill's floors) or
	 * none: no suggestion.
	 */
	public static Suggestion suggest(MapView m, int x, int y, int plane)
	{
		if (classify(m, x, y, plane) != Spot.NO_FLOOR)
		{
			return null;
		}
		int q = onlyOtherStandablePlane(m, x, y, plane);
		return q < 0 ? null : new Suggestion(x, y, q, "plane " + q + " (the only plane with floor at this x,y)");
	}

	/**
	 * A dig or emote spot whose own tile is blocked on our map (inside a rock,
	 * a tree, a STASH unit) and a route that stops right next to it. The game
	 * accepts digging or the emote from the next tile, so this counts as
	 * reached ({@link #reached}).
	 */
	public static boolean besideBlockedSpot(String kind, Spot spot, int stopDistance)
	{
		return kind.equals("coord") && spot == Spot.BLOCKED && stopDistance == 1;
	}

	/**
	 * Whether a route that stops {@code stopDistance} from a location of this
	 * kind reaches it: {@link #closeEnough}, or a blocked dig/emote spot from
	 * the next tile ({@link #besideBlockedSpot}).
	 */
	public static boolean reached(String kind, Spot spot, int stopDistance)
	{
		return closeEnough(kind, stopDistance) || besideBlockedSpot(kind, spot, stopDistance);
	}

	/**
	 * Ring distance from a tile to a rectangle of tiles (0 inside it): how far a
	 * route stop is from an object's whole footprint. A big object (a 4x4 spirit
	 * tree) is used from next to any of its tiles, not only from next to the tile
	 * the clue names.
	 */
	public static int distanceToFootprint(int x, int y, int x0, int y0, int x1, int y1)
	{
		int dx = x < x0 ? x0 - x : x > x1 ? x - x1 : 0;
		int dy = y < y0 ? y0 - y : y > y1 ? y - y1 : 0;
		return Math.max(dx, dy);
	}

	/**
	 * Whether a location of this kind is fine at this distance from where the
	 * route stops: a dig or emote spot ("coord") must be the tile itself; an
	 * NPC or object is used from next to it (an NPC across a counter: 2).
	 */
	public static boolean closeEnough(String targetKind, int distance)
	{
		if (distance < 0)
		{
			return false;
		}
		switch (targetKind)
		{
			case "coord":
				return distance == 0;
			case "npc":
			case "kill":
			case "key-npc":
				return distance <= 2;
			default: // loc, key chest, stash
				return distance <= 1;
		}
	}

	/**
	 * The likely cause for the summary, or null when the location is fine.
	 *
	 * @param kind         coord, npc, loc, key, kill, stash, key-npc, alt-target
	 * @param nearest      ring distance to the nearest standable tile (-1: none within the radius)
	 * @param stopDistance where the best player's route stops, ring distance from the location (-1: no route)
	 */
	public static String cause(String kind, Spot spot, int nearest, int stopDistance)
	{
		switch (spot)
		{
			case OFF_MAP:
				return "off the walking map (no map data for the region: an instance or a missing area)";
			case AT_SEA:
				return "at sea (walking can't reach it)";
			case IGNORED:
				return "in a region you ignored";
			case HIDDEN:
				return "in a sub-region you hid";
			case NO_FLOOR:
				return "no floor on that plane (the source's plane or tile doesn't match our map)";
			default:
				break;
		}
		if (stopDistance < 0)
		{
			if (spot == Spot.POCKET)
			{
				return "in a closed-off pocket, no route in";
			}
			return nearest < 0 ? "blocked, nothing standable nearby" : "no route (no walking way or transport leads there)";
		}
		if (reached(kind, spot, stopDistance))
		{
			return spot == Spot.POCKET ? "in a closed-off pocket (reached, but pockets are left out of the plugin's data)" : null;
		}
		if (kind.equals("coord") && spot == Spot.BLOCKED)
		{
			return "dig/emote tile is blocked on our map (route stops " + stopDistance + " away)";
		}
		return "route stops " + stopDistance + " tiles away (" + spot.name().toLowerCase(java.util.Locale.ROOT) + " target)";
	}
}
