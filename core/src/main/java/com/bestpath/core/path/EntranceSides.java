package com.bestpath.core.path;

import com.bestpath.core.graph.CoarseGraph;
import java.util.ArrayList;
import java.util.List;

/**
 * The coarse graph's entrances, cut into the nodes the coarse stage searches over. Each entrance (where a
 * component touches a component of the next cell) is split into pieces of at most {@link #PIECE} border tiles,
 * and each piece becomes two "sides": the tiles on this side of the cell border and their neighbours across it.
 * Also lists, per component, its sides and the components it touches. Made once per map and never changed.
 */
final class EntranceSides
{
	/** Entrances are split into pieces of at most this many border tiles (smaller = sharper bounds, more nodes). */
	private static final int PIECE = 4;

	/** Per entrance-side piece (even = "from" side, odd = "to" side, pairs): its component and its tiles. */
	final int[] sideComp;
	final int[][] sideTiles;
	/** Per component: its entrance sides. */
	final int[][] compSides;
	/** Per component: the components it touches through an entrance. */
	final int[][] neighbours;

	EntranceSides(CoarseGraph graph)
	{
		List<Integer> comps = new ArrayList<>();
		List<int[]> tiles = new ArrayList<>();
		for (int i = 0; i < graph.entranceCount(); i++)
		{
			buildSides(graph, i, comps, tiles);
		}
		int n = comps.size();
		sideComp = new int[n];
		sideTiles = new int[n][];
		int[] count = new int[graph.componentCount()];
		for (int s = 0; s < n; s++)
		{
			sideComp[s] = comps.get(s);
			sideTiles[s] = tiles.get(s);
			count[sideComp[s]]++;
		}
		compSides = new int[count.length][];
		for (int c = 0; c < count.length; c++)
		{
			compSides[c] = new int[count[c]];
		}
		int[] fill = new int[count.length];
		for (int s = 0; s < n; s++)
		{
			int c = sideComp[s];
			compSides[c][fill[c]++] = s;
		}
		// components next to each other (through any entrance), for the quick "can it be reached at all" check
		List<List<Integer>> nb = new ArrayList<>();
		for (int c = 0; c < count.length; c++)
		{
			nb.add(new ArrayList<>());
		}
		for (int i = 0; i < graph.entranceCount(); i++)
		{
			int a = graph.entranceFrom(i);
			int b = graph.entranceTo(i);
			nb.get(a).add(b);
			nb.get(b).add(a);
		}
		neighbours = new int[count.length][];
		for (int c = 0; c < count.length; c++)
		{
			neighbours[c] = nb.get(c).stream().distinct().mapToInt(Integer::intValue).toArray();
		}
	}

	/** How many sides there are (both sides of every piece). */
	int count()
	{
		return sideComp.length;
	}

	/**
	 * The two sides of entrance {@code i}, in pieces of up to {@link #PIECE}
	 * border tiles: each piece is a pair of nodes (even = this cell's border
	 * tiles, odd = their neighbours across the border). Walking along a long
	 * entrance then still costs ticks between pieces.
	 */
	private static void buildSides(CoarseGraph graph, int i, List<Integer> comps, List<int[]> out)
	{
		int a = graph.entranceFrom(i);
		int b = graph.entranceTo(i);
		int[] t = graph.entranceTiles(i);
		int plane = t[4];
		List<Integer> as = new ArrayList<>();
		for (int x = Math.min(t[0], t[2]); x <= Math.max(t[0], t[2]); x++)
		{
			for (int y = Math.min(t[1], t[3]); y <= Math.max(t[1], t[3]); y++)
			{
				// only border tiles: a run that turns a corner would otherwise take in the cell's inside
				boolean border = (x & 63) == 0 || (x & 63) == 63 || (y & 63) == 0 || (y & 63) == 63;
				if (border && graph.componentAt(x, y, plane) == a)
				{
					as.add(Tiles.pack(x, y, plane));
				}
			}
		}
		for (int from = 0; from < as.size(); from += PIECE)
		{
			List<Integer> piece = as.subList(from, Math.min(as.size(), from + PIECE));
			List<Integer> bs = new ArrayList<>();
			for (int tile : piece)
			{
				int x = Tiles.x(tile);
				int y = Tiles.y(tile);
				for (int dx = -1; dx <= 1; dx++)
				{
					for (int dy = -1; dy <= 1; dy++)
					{
						int nx = x + dx;
						int ny = y + dy;
						int nt = Tiles.pack(nx, ny, plane);
						if (((nx >> 6) != (x >> 6) || (ny >> 6) != (y >> 6)) && graph.componentAt(nx, ny, plane) == b
							&& !bs.contains(nt))
						{
							bs.add(nt);
						}
					}
				}
			}
			comps.add(a);
			out.add(piece.stream().mapToInt(Integer::intValue).toArray());
			comps.add(b);
			out.add(bs.stream().mapToInt(Integer::intValue).toArray());
		}
	}
}
