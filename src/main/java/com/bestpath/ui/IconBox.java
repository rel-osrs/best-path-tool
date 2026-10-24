package com.bestpath.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/** A Settings row's picture (from the {@link IconCache}; a dim square until it arrives; faded when disabled). */
final class IconBox extends JComponent
{
	static final int W = NeedsRow.ICON_W;
	static final int H = NeedsRow.ICON_H;

	private final IconKey key;
	private final IconCache icons;
	private final boolean enabled;

	IconBox(IconKey key, IconCache icons, boolean enabled)
	{
		this.key = key;
		this.icons = icons;
		this.enabled = enabled;
		if (key != null && icons != null)
		{
			icons.get(key, W, H, this::repaint); // starts loading now; repaints when it arrives
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(W, H);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		if (key == null || icons == null)
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		BufferedImage img = icons.get(key, W, H, null);
		if (!enabled)
		{
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
		}
		if (img != null)
		{
			g2.drawImage(img, (W - img.getWidth()) / 2, (H - img.getHeight()) / 2, null);
		}
		else
		{
			g2.setColor(new Color(255, 255, 255, 30));
			g2.fillRect(2, 1, W - 4, H - 2);
		}
		g2.dispose();
	}
}
