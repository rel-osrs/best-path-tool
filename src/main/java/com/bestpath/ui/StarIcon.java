package com.bestpath.ui;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * A five-pointed star drawn in code (RuneLite's font has no star character): filled yellow for a preferred Slayer
 * place, an outline for one that is not.
 */
final class StarIcon implements Icon
{
	private final int size;
	final boolean filled;

	StarIcon(int size, boolean filled)
	{
		this.size = size;
		this.filled = filled;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		double cx = x + size / 2.0;
		double cy = y + size / 2.0 + size * 0.05;
		double outer = size / 2.0;
		double inner = outer * 0.45;
		Path2D.Double star = new Path2D.Double();
		for (int i = 0; i < 10; i++)
		{
			double r = i % 2 == 0 ? outer : inner;
			double a = -Math.PI / 2 + i * Math.PI / 5;
			double px = cx + r * Math.cos(a);
			double py = cy + r * Math.sin(a);
			if (i == 0)
			{
				star.moveTo(px, py);
			}
			else
			{
				star.lineTo(px, py);
			}
		}
		star.closePath();
		if (filled)
		{
			g2.setColor(Ui.CURRENT);
			g2.fill(star);
		}
		else
		{
			g2.setColor(c == null || c.isEnabled() ? Ui.TEXT : Ui.MUTED);
			g2.setStroke(new BasicStroke(1.2f));
			g2.draw(star);
		}
		g2.dispose();
	}

	@Override
	public int getIconWidth()
	{
		return size;
	}

	@Override
	public int getIconHeight()
	{
		return size;
	}
}
