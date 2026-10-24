package com.bestpath.tool.ui;

import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.geo.Regions;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The patch of each HIDE card: the sub-region under the card's tile plus those joined to it by walking across
 * region borders. Worked out once per data build and kept per card; {@link #paint} tints it on the map. Swing
 * thread only.
 */
final class HidePatches
{
	private static final Color TINT = new Color(255, 0, 200, 110);

	private final EditorContext ctx;
	/** Per HIDE question: one tile in each sub-region of the patch (joined by walking), for the graph it was worked out from. */
	private final Map<String, List<int[]>> patches = new HashMap<>();
	private final Map<String, Set<Long>> tileSets = new HashMap<>();
	private CoarseGraph patchesFor;

	HidePatches(EditorContext ctx)
	{
		this.ctx = ctx;
	}

	/** Forgets a card's patch (the card was closed). */
	void forget(String id)
	{
		patches.remove(id);
		tileSets.remove(id);
	}

	/** Tints every tile of the target's patch and outlines it in white. */
	void paint(Graphics2D g, MapViewport vp, QuestionCards.Question q)
	{
		if (ctx.graph == null)
		{
			return;
		}
		Set<Long> tiles = patchTiles(q);
		g.setColor(TINT);
		for (long k : tiles)
		{
			int x = (int) (k >> 32);
			int y = (int) k;
			int sx0 = (int) Math.floor(vp.screenX(x));
			int sx1 = (int) Math.floor(vp.screenX(x + 1));
			int sy0 = (int) Math.floor(vp.screenY(y + 1));
			int sy1 = (int) Math.floor(vp.screenY(y));
			g.fillRect(sx0, sy0, Math.max(1, sx1 - sx0), Math.max(1, sy1 - sy0));
		}
		g.setColor(Color.WHITE);
		g.setStroke(new BasicStroke(2f));
		for (long k : tiles)
		{
			outlineEdges(g, vp, tiles, (int) (k >> 32), (int) k);
		}
	}

	/** The tile's edges where the next tile is not part of the patch. */
	private static void outlineEdges(Graphics2D g, MapViewport vp, Set<Long> tiles, int x, int y)
	{
		int sx0 = (int) Math.floor(vp.screenX(x));
		int sx1 = (int) Math.floor(vp.screenX(x + 1));
		int sy0 = (int) Math.floor(vp.screenY(y + 1));
		int sy1 = (int) Math.floor(vp.screenY(y));
		if (!tiles.contains(key(x, y + 1)))
		{
			g.drawLine(sx0, sy0, sx1, sy0);
		}
		if (!tiles.contains(key(x, y - 1)))
		{
			g.drawLine(sx0, sy1, sx1, sy1);
		}
		if (!tiles.contains(key(x - 1, y)))
		{
			g.drawLine(sx0, sy0, sx0, sy1);
		}
		if (!tiles.contains(key(x + 1, y)))
		{
			g.drawLine(sx1, sy0, sx1, sy1);
		}
	}

	private static long key(int x, int y)
	{
		return (long) x << 32 | (y & 0xffffffffL);
	}

	/** All tiles of a HIDE question's patch (on its plane), as {@link #key}s; worked out once per data build. */
	private Set<Long> patchTiles(QuestionCards.Question q)
	{
		patch(q); // clears the caches when the data was rebuilt
		Set<Long> got = tileSets.get(q.id);
		if (got != null)
		{
			return got;
		}
		got = new HashSet<>();
		CoarseGraph graph = ctx.graph;
		for (int[] t : patch(q))
		{
			int comp = graph.componentAt(t[0], t[1], t[2]);
			int region = Regions.idOfTile(t[0], t[1]);
			short[] labels = graph.cellLabels(region, t[2]);
			int base = graph.cellComponentBase(region, t[2]);
			if (comp < 0 || labels == null || base < 0 || t[2] != q.plane)
			{
				continue;
			}
			int label = comp - base + 1;
			for (int i = 0; i < labels.length; i++)
			{
				if (labels[i] == label)
				{
					got.add(key(Regions.baseX(region) + (i >> 6), Regions.baseY(region) + (i & 63)));
				}
			}
		}
		tileSets.put(q.id, got);
		return got;
	}

	/**
	 * The patch of a HIDE question: the target's sub-region plus those joined to it
	 * by walking across region borders, one tile in each. Worked out once per data build.
	 */
	List<int[]> patch(QuestionCards.Question q)
	{
		CoarseGraph graph = ctx.graph;
		if (graph != patchesFor)
		{
			patches.clear();
			tileSets.clear();
			patchesFor = graph;
		}
		List<int[]> got = patches.get(q.id);
		if (got != null)
		{
			return got;
		}
		got = new ArrayList<>();
		int start = graph == null ? -1 : graph.componentAt(q.x, q.y, q.plane);
		if (start >= 0)
		{
			for (int c : joinedByWalking(graph, start))
			{
				int[] t = anyTile(graph, c);
				if (t != null)
				{
					got.add(t);
				}
			}
		}
		patches.put(q.id, got);
		return got;
	}

	/** The sub-region {@code start} and those joined to it by walking entrances (at most 50). */
	private static Set<Integer> joinedByWalking(CoarseGraph graph, int start)
	{
		Set<Integer> seen = new HashSet<>();
		ArrayDeque<Integer> todo = new ArrayDeque<>();
		seen.add(start);
		todo.add(start);
		while (!todo.isEmpty() && seen.size() < 50)
		{
			int c = todo.poll();
			for (int e = 0; e < graph.entranceCount(); e++)
			{
				int a = graph.entranceFrom(e);
				int b = graph.entranceTo(e);
				int other = a == c ? b : b == c ? a : -1;
				if (other >= 0 && seen.add(other))
				{
					todo.add(other);
				}
			}
		}
		return seen;
	}

	/** One tile of a sub-region {x, y, plane}, or null. */
	private static int[] anyTile(CoarseGraph graph, int comp)
	{
		int cell = graph.componentCell(comp);
		int region = cell >> 2;
		int plane = cell & 3;
		short[] labels = graph.cellLabels(region, plane);
		int base = graph.cellComponentBase(region, plane);
		for (int i = 0; labels != null && base >= 0 && i < labels.length; i++)
		{
			if (labels[i] == comp - base + 1)
			{
				return new int[]{Regions.baseX(region) + (i >> 6), Regions.baseY(region) + (i & 63), plane};
			}
		}
		return null;
	}
}
