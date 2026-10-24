package com.bestpath.tool.map;

import com.bestpath.core.graph.CoarseGraph;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The "Ignored sub-regions" layer: everything left out of the plugin's data.
 * Hidden sub-regions red, closed-off pockets grey (left out automatically),
 * ignored 64x64 regions hatched red (all planes). Pending changes are
 * included, so a hide shows at once.
 */
final class HiddenLayer
{
	private static final Color HIDDEN = new Color(0xe63946);
	private static final Color IGNORED = new Color(0xd00000);
	private static final Color POCKET = new Color(0x8a8f98);

	private CoarseGraph graph;
	private boolean[] hidden;
	private boolean[] pockets;
	private Set<Integer> ignored = Collections.emptySet();
	/** Per (cell, plane): red picture of its hidden sub-regions, or null if none. */
	private final Map<Integer, BufferedImage> images = new HashMap<>();
	private TexturePaint hatch;

	/** {@code pockets}: the pockets left out (kept ones not); null = all of them. */
	void setData(CoarseGraph graph, boolean[] hidden, boolean[] pockets, Set<Integer> ignored)
	{
		this.graph = graph;
		this.hidden = hidden;
		this.pockets = graph == null ? null : pockets != null ? pockets : graph.pockets();
		this.ignored = ignored == null ? Collections.emptySet() : ignored;
		images.clear();
	}

	void draw(Graphics2D g, MapViewport vp, int plane, int rx0, int rx1, int ry0, int ry1, Set<Integer> regionIds)
	{
		Composite old = g.getComposite();
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
				int x0 = (int) Math.floor(vp.screenX(rx * 64));
				int x1 = (int) Math.floor(vp.screenX((rx + 1) * 64));
				int yTop = (int) Math.floor(vp.screenY((ry + 1) * 64));
				int yBot = (int) Math.floor(vp.screenY(ry * 64));
				if (ignored.contains(id))
				{
					g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
					g.setPaint(hatch());
					g.fillRect(x0, yTop, x1 - x0, yBot - yTop);
					g.setComposite(old);
					g.setColor(IGNORED);
					g.setStroke(new BasicStroke(3f));
					g.drawRect(x0 + 1, yTop + 1, x1 - x0 - 2, yBot - yTop - 2);
					if (x1 - x0 >= 90)
					{
						g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
						String text = "Ignored region " + id;
						int w = g.getFontMetrics().stringWidth(text);
						g.setColor(new Color(0, 0, 0, 180));
						g.fillRect(x0 + 6, yTop + 6, w + 8, 18);
						g.setColor(Color.WHITE);
						g.drawString(text, x0 + 10, yTop + 20);
					}
					continue;
				}
				BufferedImage img = image(id, plane);
				if (img != null)
				{
					g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
					g.drawImage(img, x0, yTop, x1 - x0, yBot - yTop, null);
					g.setComposite(old);
				}
			}
		}
		g.setComposite(old);
		g.setStroke(new BasicStroke(1f));
		if (hint != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
		}
	}

	private BufferedImage image(int regionId, int plane)
	{
		if (graph == null || hidden == null)
		{
			return null;
		}
		int key = regionId << 2 | plane;
		if (images.containsKey(key))
		{
			return images.get(key);
		}
		BufferedImage img = null;
		short[] labels = graph.cellLabels(regionId, plane);
		if (labels != null)
		{
			int base = graph.cellComponentBase(regionId, plane);
			for (int lx = 0; lx < 64; lx++)
			{
				for (int ly = 0; ly < 64; ly++)
				{
					int l = labels[lx << 6 | ly];
					int comp = base + l - 1;
					boolean hid = l != 0 && comp < hidden.length && hidden[comp];
					boolean pocket = l != 0 && pockets != null && comp < pockets.length && pockets[comp];
					if (hid || pocket)
					{
						if (img == null)
						{
							img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
						}
						img.setRGB(lx, 63 - ly, (hid ? HIDDEN : POCKET).getRGB());
					}
				}
			}
		}
		images.put(key, img);
		return img;
	}

	private TexturePaint hatch()
	{
		if (hatch == null)
		{
			BufferedImage t = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
			Graphics2D tg = t.createGraphics();
			tg.setColor(new Color(IGNORED.getRed(), IGNORED.getGreen(), IGNORED.getBlue(), 120));
			tg.fillRect(0, 0, 12, 12);
			tg.setColor(IGNORED);
			tg.setStroke(new BasicStroke(2f));
			tg.drawLine(0, 12, 12, 0);
			tg.drawLine(-6, 6, 6, -6);
			tg.drawLine(6, 18, 18, 6);
			tg.dispose();
			hatch = new TexturePaint(t, new Rectangle2D.Double(0, 0, 12, 12));
		}
		return hatch;
	}
}
