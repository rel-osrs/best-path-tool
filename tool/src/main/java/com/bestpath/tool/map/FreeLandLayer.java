package com.bestpath.tool.map;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.FreeLandDiagnostics;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The "Free-to-play land" layer: free tiles tinted green (every plane), so the members-only rest of the
 * map stays untinted. Its border is drawn darker so the edge is easy to follow when zoomed in.
 */
final class FreeLandLayer
{
	private static final int FREE = 0x33cc55;
	private static final int EDGE = 0x0b6b22;

	private FreeLand land = FreeLand.NONE;
	/** Per region: its picture (64x64, 1 px per tile), or null for a region with no free tile. */
	private final Map<Integer, BufferedImage> images = new HashMap<>();
	private final Set<Integer> empty = new java.util.HashSet<>();

	void setData(FreeLand land)
	{
		if (land == this.land)
		{
			return;
		}
		this.land = land == null ? FreeLand.NONE : land;
		images.clear();
		empty.clear();
	}

	void draw(Graphics2D g, MapViewport vp, int rx0, int rx1, int ry0, int ry1)
	{
		if (FreeLandDiagnostics.isEmpty(land))
		{
			return;
		}
		Composite old = g.getComposite();
		Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
		for (int rx = rx0; rx <= rx1; rx++)
		{
			for (int ry = ry0; ry <= ry1; ry++)
			{
				BufferedImage img = image(rx << 8 | ry);
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

	private BufferedImage image(int regionId)
	{
		if (empty.contains(regionId))
		{
			return null;
		}
		BufferedImage img = images.get(regionId);
		if (img != null)
		{
			return img;
		}
		int bx = (regionId >> 8) << 6;
		int by = (regionId & 0xFF) << 6;
		boolean any = false;
		img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		for (int lx = 0; lx < 64; lx++)
		{
			for (int ly = 0; ly < 64; ly++)
			{
				int x = bx + lx, y = by + ly;
				if (!land.isFree(x, y))
				{
					continue;
				}
				any = true;
				boolean edge = !land.isFree(x + 1, y) || !land.isFree(x - 1, y) || !land.isFree(x, y + 1) || !land.isFree(x, y - 1);
				img.setRGB(lx, 63 - ly, 0xff000000 | (edge ? EDGE : FREE));
			}
		}
		if (!any)
		{
			empty.add(regionId);
			return null;
		}
		images.put(regionId, img);
		return img;
	}
}
