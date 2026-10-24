package com.bestpath.tool.map;

import com.bestpath.core.sea.SeaArea;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The "Sea (sailing)" layer: sea tiles coloured by sea area (the rules in
 * docs/SAILING_SPEC.md). Open water is blue (coastal shallows paler), each
 * restricted area has its own colour, the water beyond the world edge is dark
 * navy, and sea tiles blocked for boats (rocks, border lines; in no
 * {@link SeaPieces piece}) are white. Sea a boat
 * cannot reach from the main ocean (cut off by blocked tiles) is striped dark.
 * Plane 0 only.
 */
final class SeaLayer
{
	private static final Map<SeaArea, Integer> AREA = new HashMap<>();

	static
	{
		AREA.put(SeaArea.OPEN, 0x3a86ff);
		AREA.put(SeaArea.TEMPOR_TANTRUM, 0xffb703);
		AREA.put(SeaArea.NORTHERN, 0x9b5de5);
		AREA.put(SeaArea.CRYSTALLINE, 0xf15bb5);
		AREA.put(SeaArea.FETID, 0x8ac926);
		AREA.put(SeaArea.KELP, 0x1b7a43);
		AREA.put(SeaArea.WORLD_EDGE, 0x1d3557);
	}

	private static final int SHALLOWS = 0x9bd8ff;
	private static final int BLOCKED = 0xffffff;

	private SeaMap sea;
	private SeaPieces pieces;
	/** Per region: its picture (64x64, 1 px per tile), or null when it has no sea. */
	private final Map<Integer, BufferedImage> images = new HashMap<>();

	void setData(SeaMap sea, SeaPieces pieces)
	{
		this.sea = sea;
		this.pieces = pieces;
		images.clear();
	}

	/** Colour of a sea tile on this layer. */
	private int colour(int x, int y, int texture)
	{
		// blocked for boats = in no piece (the walking data has every sea tile blocked)
		int piece = pieces == null ? -1 : pieces.pieceAt(x, y, 0);
		if (pieces != null && piece < 0)
		{
			return BLOCKED;
		}
		int c = texture == com.bestpath.tool.extract.SeaExtractor.SHALLOWS ? SHALLOWS : AREA.get(SeaMap.areaOf(texture));
		if (piece >= 0 && piece != pieces.mainPiece() && ((x + y) & 3) < 2)
		{
			// cut off from the main ocean: dark stripes
			c = (c >> 2) & 0x3f3f3f;
		}
		return c;
	}

	void draw(Graphics2D g, MapViewport vp, int plane, int rx0, int rx1, int ry0, int ry1, Set<Integer> regionIds)
	{
		if (sea == null || plane != 0)
		{
			return;
		}
		Composite old = g.getComposite();
		Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
		for (int rx = rx0; rx <= rx1; rx++)
		{
			for (int ry = ry0; ry <= ry1; ry++)
			{
				int id = rx << 8 | ry;
				if (!regionIds.contains(id) || !sea.hasSea(id))
				{
					continue;
				}
				int x0 = (int) Math.floor(vp.screenX(rx * 64));
				int x1 = (int) Math.floor(vp.screenX((rx + 1) * 64));
				int yTop = (int) Math.floor(vp.screenY((ry + 1) * 64));
				int yBot = (int) Math.floor(vp.screenY(ry * 64));
				g.drawImage(image(id), x0, yTop, x1 - x0, yBot - yTop, null);
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
		BufferedImage img = images.get(regionId);
		if (img != null)
		{
			return img;
		}
		img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		int bx = (regionId >> 8) << 6;
		int by = (regionId & 0xFF) << 6;
		for (int lx = 0; lx < 64; lx++)
		{
			for (int ly = 0; ly < 64; ly++)
			{
				int t = sea.texture(bx + lx, by + ly, 0);
				if (t >= 0)
				{
					img.setRGB(lx, 63 - ly, 0xff000000 | colour(bx + lx, by + ly, t));
				}
			}
		}
		images.put(regionId, img);
		return img;
	}
}
