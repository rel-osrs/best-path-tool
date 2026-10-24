package com.bestpath.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** The sidebar button's 16x16 icon, drawn in code (no image file in the jar): a dotted path between two pins. */
public final class NavIcon
{
	private NavIcon()
	{
	}

	public static BufferedImage create()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0, 200, 255));
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{2f, 2.5f}, 0f));
		g.drawLine(3, 13, 7, 8);
		g.drawLine(7, 8, 12, 4);
		g.setColor(new Color(220, 138, 0));
		g.fillOval(0, 10, 6, 6);
		g.fillOval(10, 0, 6, 6);
		g.dispose();
		return img;
	}
}
