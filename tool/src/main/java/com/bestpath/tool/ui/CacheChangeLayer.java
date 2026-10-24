package com.bestpath.tool.ui;

import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import com.bestpath.tool.update.CacheFacts;
import com.bestpath.tool.update.CompareFile;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;

/**
 * Map layer for the newest game update comparison: each region whose walls changed gets a dashed outline and a label
 * (red where walking changed in a region you reviewed, orange for the others), and its changed tiles are filled on the
 * plane shown. Only reads the comparison.
 */
final class CacheChangeLayer implements MapView.Overlay
{
	private static final Color LOOK = new Color(230, 30, 30);
	private static final Color INFO = new Color(255, 140, 0);
	private static final Stroke DASHED = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
		new float[]{8f, 5f}, 0f);

	private volatile CompareFile shown;
	private volatile boolean visible = true;

	void set(CompareFile f)
	{
		shown = f;
	}

	void setVisible(boolean on)
	{
		visible = on;
	}

	boolean isVisible()
	{
		return visible;
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		CompareFile f = shown;
		if (f == null || !visible)
		{
			return;
		}
		MapViewport vp = view.getViewport();
		Stroke was = g.getStroke();
		Font font = g.getFont().deriveFont(Font.BOLD, 11f);
		for (CompareFile.Item i : f.items)
		{
			boolean look = i.kind.equals("REVIEWED_REGION_CHANGED");
			if (!look && !i.kind.equals("REGION_CHANGED"))
			{
				continue;
			}
			int region;
			try
			{
				region = Integer.parseInt(i.ref);
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			Color c = look ? LOOK : INFO;
			int rx = region >> 8;
			int ry = region & 0xFF;
			int x0 = (int) Math.round(vp.screenX(rx * 64));
			int x1 = (int) Math.round(vp.screenX((rx + 1) * 64));
			int yTop = (int) Math.round(vp.screenY((ry + 1) * 64));
			int yBot = (int) Math.round(vp.screenY(ry * 64));
			if (x1 < 0 || x0 > view.getWidth() || yBot < 0 || yTop > view.getHeight())
			{
				continue;
			}
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
			g.fillRect(x0, yTop, x1 - x0, yBot - yTop);
			g.setColor(c);
			g.setStroke(DASHED);
			g.drawRect(x0, yTop, x1 - x0, yBot - yTop);
			g.setStroke(was);
			g.setFont(font);
			g.drawString("Changed in #" + f.to + (look ? " (look)" : ""), x0 + 4, yBot - 4);
			paintTiles(g, view, i, c);
		}
	}

	/** The changed tiles on the plane shown (at least 2 px, so they show zoomed out). */
	private static void paintTiles(Graphics2D g, MapView view, CompareFile.Item i, Color c)
	{
		MapViewport vp = view.getViewport();
		double px = Math.max(2, vp.getScale());
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200));
		for (long t : i.tiles)
		{
			if (CacheFacts.tilePlane(t) != view.getPlane())
			{
				continue;
			}
			int sx = (int) Math.floor(vp.screenX(CacheFacts.tileX(t)));
			int sy = (int) Math.floor(vp.screenY(CacheFacts.tileY(t) + 1));
			g.fillRect(sx, sy, (int) Math.ceil(px), (int) Math.ceil(px));
		}
	}
}
