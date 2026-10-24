package com.bestpath.tool.map;

import com.bestpath.core.graph.CoarseGraph;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws the coarse graph: every component ("sub-region") filled with its own
 * colour, and entrances as white bars on the cell borders they cross.
 * Colour pictures are made once per cell (64x64 px, 1 px per tile) and kept
 * in a small cache, so this works at any zoom.
 */
final class CoarseLayer
{
	private static final int IMAGE_CACHE = 3000;
	private static final Color ENTRANCE = Color.WHITE;
	private static final Color ENTRANCE_OUTLINE = new Color(0, 0, 0, 160);

	private CoarseGraph graph;
	private boolean[] pockets;
	private final Map<Integer, BufferedImage> images = new LinkedHashMap<Integer, BufferedImage>(512, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> e)
		{
			return size() > IMAGE_CACHE;
		}
	};

	/** {@code grey}: sub-regions left out of this layer (shown on the ignored layer instead); null = the pockets. */
	void setGraph(CoarseGraph graph, boolean[] grey)
	{
		this.graph = graph;
		this.pockets = graph == null ? null : grey != null ? grey : graph.pockets();
		images.clear();
	}

	/** Hidden sub-regions (pending included) and ignored regions: left out of this layer. */
	private boolean[] hidden;
	private java.util.Set<Integer> ignored = java.util.Collections.emptySet();

	/** {@code pockets}: the pockets left out (kept ones are drawn); null = leave as set by {@link #setGraph}. */
	void setExcluded(boolean[] hidden, boolean[] pockets, java.util.Set<Integer> ignored)
	{
		this.hidden = hidden;
		if (pockets != null && graph != null && pockets.length == graph.componentCount())
		{
			this.pockets = pockets;
		}
		this.ignored = ignored == null ? java.util.Collections.emptySet() : ignored;
		images.clear();
	}

	/** True if a component is not drawn here (pocket, hidden or in an ignored region). */
	private boolean excluded(int comp)
	{
		if (pockets[comp] || (hidden != null && comp < hidden.length && hidden[comp]))
		{
			return true;
		}
		return !ignored.isEmpty() && ignored.contains(graph.componentCell(comp) >> 2);
	}

	/** A distinct, stable colour for a component id (golden-ratio hue steps). */
	static Color colour(int component)
	{
		float hue = (float) ((component * 0.6180339887) % 1.0);
		return Color.getHSBColor(hue, 0.70f, 0.95f);
	}

	void drawComponents(Graphics2D g, MapViewport vp, int plane, int rx0, int rx1, int ry0, int ry1, java.util.Set<Integer> regionIds)
	{
		if (graph == null)
		{
			return;
		}
		Composite old = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
		Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		for (int rx = rx0; rx <= rx1; rx++)
		{
			for (int ry = ry0; ry <= ry1; ry++)
			{
				int id = rx << 8 | ry;
				if (!regionIds.contains(id))
				{
					continue;
				}
				BufferedImage img = image(id, plane);
				if (img == null)
				{
					continue;
				}
				int x0 = (int) Math.floor(vp.screenX(rx * 64));
				int x1 = (int) Math.floor(vp.screenX((rx + 1) * 64));
				int yTop = (int) Math.floor(vp.screenY((ry + 1) * 64));
				int yBot = (int) Math.floor(vp.screenY(ry * 64));
				g.drawImage(img, x0, yTop, x1 - x0, yBot - yTop, null);
			}
		}
		g.setComposite(old);
		if (hint != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
		}
	}

	private BufferedImage image(int regionId, int plane)
	{
		int key = regionId << 2 | plane;
		BufferedImage img = images.get(key);
		if (img != null || images.containsKey(key))
		{
			return img;
		}
		short[] labels = graph.cellLabels(regionId, plane);
		if (labels != null)
		{
			int base = graph.cellComponentBase(regionId, plane);
			img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			for (int lx = 0; lx < 64; lx++)
			{
				for (int ly = 0; ly < 64; ly++)
				{
					int l = labels[lx << 6 | ly];
					int comp = base + l - 1;
					// pockets, hidden and ignored ones are not drawn here: they are on the "Ignored sub-regions" layer
					if (l != 0 && !excluded(comp))
					{
						img.setRGB(lx, 63 - ly, colour(comp).getRGB());
					}
				}
			}
		}
		images.put(key, img);
		return img;
	}

	void drawEntrances(Graphics2D g, MapViewport vp, int plane, double minX, double maxX, double minY, double maxY)
	{
		if (graph == null)
		{
			return;
		}
		float w = (float) Math.max(3, vp.getScale() / 2.5);
		BasicStroke outline = new BasicStroke(w + 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
		BasicStroke bar = new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
		for (int e = 0; e < graph.entranceCount(); e++)
		{
			int[] t = graph.entranceTiles(e);
			if (t[4] != plane || Math.max(t[0], t[2]) < minX - 1 || Math.min(t[0], t[2]) > maxX + 1
				|| Math.max(t[1], t[3]) < minY - 1 || Math.min(t[1], t[3]) > maxY + 1)
			{
				continue;
			}
			if (excluded(graph.entranceFrom(e)) || excluded(graph.entranceTo(e)))
			{
				continue; // leads into something left out of the data
			}
			int fromCell = graph.componentCell(graph.entranceFrom(e)) >> 2;
			int toCell = graph.componentCell(graph.entranceTo(e)) >> 2;
			int dx = (toCell >> 8) - (fromCell >> 8);
			int dy = (toCell & 0xFF) - (fromCell & 0xFF);
			int xa = Math.min(t[0], t[2]);
			int xb = Math.max(t[0], t[2]);
			int ya = Math.min(t[1], t[3]);
			int yb = Math.max(t[1], t[3]);
			double sx0;
			double sy0;
			double sx1;
			double sy1;
			if (dx != 0 && dy == 0)
			{
				// vertical bar on the east (or west) edge of the column of tiles
				double ex = dx > 0 ? xa + 1 : xa;
				sx0 = vp.screenX(ex);
				sx1 = sx0;
				sy0 = vp.screenY(yb + 1);
				sy1 = vp.screenY(ya);
			}
			else if (dy != 0 && dx == 0)
			{
				double ey = dy > 0 ? ya + 1 : ya;
				sy0 = vp.screenY(ey);
				sy1 = sy0;
				sx0 = vp.screenX(xa);
				sx1 = vp.screenX(xb + 1);
			}
			else
			{
				// diagonal step through a cell corner: a short mark on the corner
				double cx = dx > 0 ? xa + 1 : xa;
				double cy = dy > 0 ? ya + 1 : ya;
				sx0 = vp.screenX(cx) - w;
				sy0 = vp.screenY(cy) - w;
				sx1 = vp.screenX(cx) + w;
				sy1 = vp.screenY(cy) + w;
			}
			g.setStroke(outline);
			g.setColor(ENTRANCE_OUTLINE);
			g.drawLine((int) sx0, (int) sy0, (int) sx1, (int) sy1);
			g.setStroke(bar);
			g.setColor(ENTRANCE);
			g.drawLine((int) sx0, (int) sy0, (int) sx1, (int) sy1);
		}
		g.setStroke(new BasicStroke(1f));
	}
}
