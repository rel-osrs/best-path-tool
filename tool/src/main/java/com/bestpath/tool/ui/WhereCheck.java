package com.bestpath.tool.ui;

/**
 * Checks the tiles typed in the "Where" row of the decision bar ({@link ToolOptionsBar}): each end must be a tile in
 * range with a floor on its plane. Only the tile itself is looked at, never the tiles around it: whether a transport
 * stands on walkable tiles is checked elsewhere.
 */
final class WhereCheck
{
	/** Largest x or y a tile can have (14 bits, as the plugin packs tiles). */
	static final int MAX_COORD = 0x3FFF;

	/** The map's walkable areas: a tile's area, or -1 when nothing can stand on it (core CoarseGraph). */
	interface Floor
	{
		int componentAt(int x, int y, int plane);
	}

	private WhereCheck()
	{
	}

	static boolean inRange(int x, int y, int plane)
	{
		return x >= 0 && y >= 0 && x <= MAX_COORD && y <= MAX_COORD && plane >= 0 && plane <= 3;
	}

	/**
	 * What is wrong with the typed tiles, or null when they can be used.
	 *
	 * @param hasStart whether the transport has a start of its own (a teleport has none)
	 * @param start    the typed start {x, y, plane}, or null when it is not a tile in range
	 * @param end      the typed end, or null likewise
	 * @param floor    the map, or null when none is loaded (then the floor is not checked)
	 */
	static String problem(boolean hasStart, int[] start, int[] end, Floor floor)
	{
		if (hasStart && start == null)
		{
			return "start: type x, y and a plane 0-3";
		}
		if (end == null)
		{
			return "end: type x, y and a plane 0-3";
		}
		if (floor == null)
		{
			return null;
		}
		boolean startBad = hasStart && floor.componentAt(start[0], start[1], start[2]) < 0;
		boolean endBad = floor.componentAt(end[0], end[1], end[2]) < 0;
		if (startBad && endBad)
		{
			return "no floor under the start or the end";
		}
		if (startBad)
		{
			return "no floor under the start (" + tile(start) + ")";
		}
		return endBad ? "no floor under the end (" + tile(end) + ")" : null;
	}

	private static String tile(int[] t)
	{
		return t[0] + ", " + t[1] + ", plane " + t[2];
	}
}
