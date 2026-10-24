package com.bestpath.core.path;

import java.util.HashMap;
import java.util.Map;

/**
 * What the coarse stage found for one query ({@link CoarseSearch#bounds}): per component, a lower bound of the
 * ticks still needed to reach the destination, plus what the tile search needs to work out sharper bounds for the
 * tiles of each component it enters ({@link CoarseSearch#field}).
 */
final class CoarseBounds
{
	/** Per coarse node: ticks to the destination when the way on starts with a crossing or a transport (state 0). */
	int[] dist0;
	int[] lbComp;
	/** Bound for nodes the coarse search did not reach. */
	int unsettled;
	int lbPlayer;
	/** The least over the teleports of their ticks plus the bound from where they land (INF: none leads there). */
	int lbTele;
	/** Per query: the special nodes (their tiles and components). */
	int[] spTile;
	/** Per special: its tiles when it stands for several (the tiles beside a solid end), else null. */
	int[][] spSet;
	/** The specials by component ({@link ByKey}: component, special), in special order. */
	ByKey specialsIn;
	/**
	 * Per solid tile that a transport used from beside lands on (see {@link Beside}): the least state-0 value
	 * of the nodes on that tile (a lower bound of the ticks from standing there), so far-off landings are
	 * never expanded.
	 */
	Map<Integer, Integer> solidBound = Map.of();
	/** Per component (by id), made when the tile search first enters it: a bound for each of its tiles. */
	int[][] fieldCache;
	/** Per cell ({@code regionId << 2 | plane}): its tiles' component labels, unpacked when first needed. */
	final Map<Integer, short[]> labelCache = new HashMap<>();

	/** Component {@code c}'s bound: its least settled value, or the stopping bound when none of it was settled. */
	int component(int c)
	{
		return lbComp[c] == CoarseSearch.INF ? unsettled : lbComp[c];
	}
}
