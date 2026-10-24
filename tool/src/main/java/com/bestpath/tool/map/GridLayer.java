package com.bestpath.tool.map;

import com.bestpath.tool.geo.Regions;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Set;

/** The "Tile grid" and "Region grid" layers: lines between tiles, and between 64x64 regions with their ids. */
final class GridLayer
{
	private static final Color GRID = new Color(255, 255, 255, 70);
	private static final Color TILE_GRID = new Color(255, 255, 255, 28);
	private static final Color LABEL_BG = new Color(0, 0, 0, 150);

	private GridLayer()
	{
	}

	/** A line between every two tiles across a view {@code width} x {@code height} pixels. */
	static void drawTileGrid(Graphics2D g, MapViewport vp, int width, int height)
	{
		g.setColor(TILE_GRID);
		int x0 = (int) Math.floor(vp.worldX(0));
		int x1 = (int) Math.ceil(vp.worldX(width));
		int y0 = (int) Math.floor(vp.worldY(height));
		int y1 = (int) Math.ceil(vp.worldY(0));
		for (int x = x0; x <= x1; x++)
		{
			int sx = (int) Math.floor(vp.screenX(x));
			g.drawLine(sx, 0, sx, height);
		}
		for (int y = y0; y <= y1; y++)
		{
			int sy = (int) Math.floor(vp.screenY(y));
			g.drawLine(0, sy, width, sy);
		}
	}

	/**
	 * Lines between the regions rx0..rx1, ry0..ry1 and, when a region is at least 56 pixels wide, the id of each
	 * region in {@code regionIds} in its top-left corner (with its x, y when 200 pixels or more).
	 */
	static void drawRegionGrid(Graphics2D g, MapViewport vp, int width, int height, Font font, Set<Integer> regionIds,
		int rx0, int rx1, int ry0, int ry1, double regionPx)
	{
		g.setColor(GRID);
		g.setStroke(new BasicStroke(regionPx >= 200 ? 2f : 1f));
		for (int rx = rx0; rx <= rx1 + 1; rx++)
		{
			int sx = (int) Math.floor(vp.screenX(rx * Regions.SIZE));
			g.drawLine(sx, 0, sx, height);
		}
		for (int ry = ry0; ry <= ry1 + 1; ry++)
		{
			int sy = (int) Math.floor(vp.screenY(ry * Regions.SIZE));
			g.drawLine(0, sy, width, sy);
		}
		g.setStroke(new BasicStroke(1f));
		if (regionPx < 56)
		{
			return;
		}
		g.setFont(font.deriveFont(Font.BOLD, regionPx >= 200 ? 13f : 11f));
		FontMetrics fm = g.getFontMetrics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		for (int rx = rx0; rx <= rx1; rx++)
		{
			for (int ry = ry0; ry <= ry1; ry++)
			{
				int id = Regions.id(rx, ry);
				if (!regionIds.contains(id))
				{
					continue;
				}
				String label = regionPx >= 200 ? id + "  (" + rx + ", " + ry + ")" : Integer.toString(id);
				int sx = (int) vp.screenX(rx * Regions.SIZE) + 4;
				int sy = (int) vp.screenY((ry + 1) * Regions.SIZE) + 4;
				g.setColor(LABEL_BG);
				g.fillRect(sx, sy, fm.stringWidth(label) + 6, fm.getHeight());
				g.setColor(Color.WHITE);
				g.drawString(label, sx + 3, sy + fm.getAscent());
			}
		}
	}
}
