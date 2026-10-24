package com.bestpath.tool.map;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.extract.FlagGrid;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Set;

/**
 * The "Walls" layer: blocked tiles tinted by what blocks them (object red, floor blue, floor decoration orange)
 * and walls drawn as yellow lines on the tile sides, with small squares for the corner pillars.
 */
final class WallLayer
{
	private static final Color BLOCK_OBJECT = new Color(230, 57, 70, 110);
	private static final Color BLOCK_FLOOR = new Color(58, 134, 255, 110);
	private static final Color BLOCK_DECOR = new Color(255, 159, 28, 120);
	private static final Color WALL = new Color(0xffd60a);

	private WallLayer()
	{
	}

	/** Draws the movement flags of every visible tile in {@code regionIds} on a view {@code width} x {@code height} pixels. */
	static void draw(Graphics2D g, MapViewport vp, FlagGrid collision, int plane, Set<Integer> regionIds, int width, int height)
	{
		int x0 = (int) Math.floor(vp.worldX(0)) - 1;
		int x1 = (int) Math.ceil(vp.worldX(width)) + 1;
		int y0 = (int) Math.floor(vp.worldY(height)) - 1;
		int y1 = (int) Math.ceil(vp.worldY(0)) + 1;
		double s = vp.getScale();
		float stroke = (float) Math.max(1.5, s / 7);
		g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
		int pillar = (int) Math.max(2, s / 4);
		for (int x = x0; x <= x1; x++)
		{
			int sx0 = (int) Math.floor(vp.screenX(x));
			int sx1 = (int) Math.floor(vp.screenX(x + 1));
			for (int y = y0; y <= y1; y++)
			{
				if (!regionIds.contains((x >> 6) << 8 | (y >> 6)))
				{
					continue;
				}
				int f = collision.get(x, y, plane);
				if (f == 0)
				{
					continue;
				}
				int syTop = (int) Math.floor(vp.screenY(y + 1));
				int syBot = (int) Math.floor(vp.screenY(y));
				fillBlocked(g, f, sx0, syTop, sx1, syBot);
				if ((f & 0xFF) == 0)
				{
					continue;
				}
				drawWalls(g, f, sx0, syTop, sx1, syBot, pillar);
			}
		}
		g.setStroke(new BasicStroke(1f));
	}

	/** Tints a blocked tile by what blocks it: an object, the floor, or a floor decoration. */
	private static void fillBlocked(Graphics2D g, int f, int sx0, int syTop, int sx1, int syBot)
	{
		if ((f & CollisionFlags.OBJECT) != 0)
		{
			g.setColor(BLOCK_OBJECT);
			g.fillRect(sx0, syTop, sx1 - sx0, syBot - syTop);
		}
		else if ((f & CollisionFlags.FLOOR) != 0)
		{
			g.setColor(BLOCK_FLOOR);
			g.fillRect(sx0, syTop, sx1 - sx0, syBot - syTop);
		}
		else if ((f & CollisionFlags.FLOOR_DECORATION) != 0)
		{
			g.setColor(BLOCK_DECOR);
			g.fillRect(sx0, syTop, sx1 - sx0, syBot - syTop);
		}
	}

	/** The tile's walls: a line on each blocked side, a small square in each blocked corner. */
	private static void drawWalls(Graphics2D g, int f, int sx0, int syTop, int sx1, int syBot, int pillar)
	{
		g.setColor(WALL);
		if ((f & CollisionFlags.NORTH) != 0)
		{
			g.drawLine(sx0, syTop, sx1, syTop);
		}
		if ((f & CollisionFlags.SOUTH) != 0)
		{
			g.drawLine(sx0, syBot, sx1, syBot);
		}
		if ((f & CollisionFlags.WEST) != 0)
		{
			g.drawLine(sx0, syTop, sx0, syBot);
		}
		if ((f & CollisionFlags.EAST) != 0)
		{
			g.drawLine(sx1, syTop, sx1, syBot);
		}
		if ((f & CollisionFlags.NORTH_WEST) != 0)
		{
			g.fillRect(sx0, syTop, pillar, pillar);
		}
		if ((f & CollisionFlags.NORTH_EAST) != 0)
		{
			g.fillRect(sx1 - pillar, syTop, pillar, pillar);
		}
		if ((f & CollisionFlags.SOUTH_WEST) != 0)
		{
			g.fillRect(sx0, syBot - pillar, pillar, pillar);
		}
		if ((f & CollisionFlags.SOUTH_EAST) != 0)
		{
			g.fillRect(sx1 - pillar, syBot - pillar, pillar, pillar);
		}
	}
}
