package com.bestpath.core.path;

import com.bestpath.core.sea.SeaGraph;
import java.util.List;

/**
 * A boat-only transport (e.g. the Wyrmscraig cave) as the sea search uses it: the boat sails to its start, goes
 * through it (its ticks, the player on the boat) and sails on from its end. Its ends join the sea graph as a
 * point at sea does.
 */
final class SeaLink
{
	final Transport t;
	/** Start and end tiles (plane 0), as boxes {x0, y0, x1, y1} for the lower bounds. */
	final int[] inBox;
	final int[] outBox;
	/** Hundredths of a tick. */
	final int cost;
	final List<SeaGraph.Join> in;
	final List<SeaGraph.Join> out;
	/** The graph pieces its start and its end join. */
	final int[] inPieces;
	final int[] outPieces;

	SeaLink(Transport t, int sx, int sy, int ex, int ey, int cost, List<SeaGraph.Join> in, List<SeaGraph.Join> out, int[] inPieces,
		int[] outPieces)
	{
		this.t = t;
		this.inBox = new int[]{sx, sy, sx, sy};
		this.outBox = new int[]{ex, ey, ex, ey};
		this.cost = cost;
		this.in = in;
		this.out = out;
		this.inPieces = inPieces;
		this.outPieces = outPieces;
	}

	boolean reverses(SeaLink o)
	{
		return inBox[0] == o.outBox[0] && inBox[1] == o.outBox[1] && outBox[0] == o.inBox[0] && outBox[1] == o.inBox[1];
	}
}
