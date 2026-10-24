package com.bestpath.tool.map;

import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.SeaArea;
import com.bestpath.core.sea.SeaGraph;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The sea graph's channels (core {@code SeaChannels}): crooked water the boats' straight links miss, such as the way
 * into the Ynysdail bay. Each is drawn as a dashed line along its stretches (yellow: every boat fits; orange: rafts only), with its
 * number and who fits when zoomed in; the Inspector describes the one under a clicked tile ({@link #describeAt}).
 * Drawn with the Sea, Transports or Docks layer, plane 0 only.
 */
public final class ChannelLayer
{
	private static final Color EVERY_BOAT = new Color(0xffe600);
	private static final Color RAFTS_ONLY = new Color(0xff7b00);
	private static final Color OUTLINE = new Color(0, 0, 0, 160);
	private static final Color LABEL_BG = new Color(0, 0, 0, 170);

	private SeaGraph graph;
	/** The channel clicked (highlighted, its name shown at every zoom), or -1. */
	private int selected = -1;

	void setData(SeaGraph graph)
	{
		this.graph = graph;
		selected = -1;
	}

	void setSelected(int channel)
	{
		selected = channel;
	}

	/** Channels deleted by changes not built in yet (their keys): not drawn. */
	private java.util.Set<String> deleted = java.util.Set.of();

	void setDeleted(java.util.Set<String> keys)
	{
		deleted = keys == null ? java.util.Set.of() : keys;
	}

	void draw(Graphics2D g, MapViewport vp, int plane, int tx0, int ty0, int tx1, int ty1)
	{
		if (graph == null || plane != 0 || graph.channelCount() == 0)
		{
			return;
		}
		double s = vp.getScale();
		Stroke old = g.getStroke();
		float w = (float) Math.max(2, Math.min(5, s / 2));
		Stroke outline = new BasicStroke(w + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		Stroke dashed = new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{3 * w, 2 * w}, 0f);
		for (int c = 0; c < graph.channelCount(); c++)
		{
			int[] p = graph.channelPoints(c);
			if (!deleted.isEmpty() && deleted.contains(com.bestpath.core.sea.SeaChannelsBuilder.key(p)))
			{
				continue;
			}
			if (!inView(p, tx0, ty0, tx1, ty1))
			{
				continue;
			}
			Path2D.Double path = path(vp, p);
			boolean every = everyBoat(c);
			boolean picked = c == selected;
			if (picked)
			{
				// the selected one: a wide white band under it, and its name at every zoom
				g.setStroke(new BasicStroke(w + 8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(Color.WHITE);
				g.draw(path);
			}
			g.setStroke(outline);
			g.setColor(OUTLINE);
			g.draw(path);
			g.setStroke(dashed);
			g.setColor(every ? EVERY_BOAT : RAFTS_ONLY);
			g.draw(path);
			if (s >= 3 || picked)
			{
				drawLabel(g, vp, p, c, every);
			}
		}
		g.setStroke(old);
	}

	/** True if the box around the channel's points (packed x << 14 | y) overlaps the visible tiles. */
	private static boolean inView(int[] p, int tx0, int ty0, int tx1, int ty1)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int t : p)
		{
			minX = Math.min(minX, t >>> 14);
			maxX = Math.max(maxX, t >>> 14);
			minY = Math.min(minY, t & 0x3FFF);
			maxY = Math.max(maxY, t & 0x3FFF);
		}
		return !(maxX < tx0 || minX > tx1 || maxY < ty0 || minY > ty1);
	}

	/** The line through the channel's points, on the screen. */
	private static Path2D.Double path(MapViewport vp, int[] p)
	{
		Path2D.Double path = new Path2D.Double();
		for (int i = 0; i < p.length; i++)
		{
			double x = vp.screenX((p[i] >>> 14) + 0.5);
			double y = vp.screenY((p[i] & 0x3FFF) + 0.5);
			if (i == 0)
			{
				path.moveTo(x, y);
			}
			else
			{
				path.lineTo(x, y);
			}
		}
		return path;
	}

	/** "Channel n (every boat / rafts only)" beside the channel's middle point. */
	private static void drawLabel(Graphics2D g, MapViewport vp, int[] p, int c, boolean every)
	{
		int mid = p[p.length / 2];
		String text = "Channel " + (c + 1) + " (" + (every ? "every boat" : "rafts only") + ")";
		FontMetrics fm = g.getFontMetrics();
		int x = (int) vp.screenX((mid >>> 14) + 0.5) + 6;
		int y = (int) vp.screenY((mid & 0x3FFF) + 0.5) - 6;
		g.setColor(LABEL_BG);
		g.fillRect(x - 2, y - fm.getAscent(), fm.stringWidth(text) + 4, fm.getHeight());
		g.setColor(every ? EVERY_BOAT : RAFTS_ONLY);
		g.drawString(text, x, y);
	}

	private boolean everyBoat(int c)
	{
		return fits(graph, c, BoatType.SLOOP) && fits(graph, c, BoatType.SKIFF);
	}

	private static boolean fits(SeaGraph g, int c, BoatType t)
	{
		return g.channelClearance(c) >= t.hardClearance();
	}

	/**
	 * The Inspector's text for the channels passing through or right next to the tile (empty when none): its number,
	 * its ends, who fits, the sea areas it crosses, its length and how long boats take.
	 */
	public static String describeAt(SeaGraph g, int x, int y, int plane)
	{
		if (g == null || plane != 0)
		{
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int c = 0; c < g.channelCount(); c++)
		{
			if (!near(g, c, x, y))
			{
				continue;
			}
			int[] p = g.channelPoints(c);
			int a = p[0];
			int b = p[p.length - 1];
			boolean crystal = (g.channelAreas(c) & 1 << SeaArea.CRYSTALLINE.ordinal()) != 0;
			List<String> who = new ArrayList<>();
			for (BoatType t : BoatType.values())
			{
				if (fits(g, c, t))
				{
					who.add(t.getLabel() + (crystal && !t.hasKeel() ? " (no keel: not here)" : ""));
				}
			}
			List<String> areas = new ArrayList<>();
			for (SeaArea area : SeaArea.values())
			{
				if ((g.channelAreas(c) & 1 << area.ordinal()) != 0)
				{
					areas.add(area.getLabel() + (area.getNeeds() != null ? " (" + area.getNeeds() + ")" : ""));
				}
			}
			double len = g.channelLength(c);
			sb.append('\n');
			sb.append("Sea channel ").append(c + 1).append(" of ").append(g.channelCount()).append('\n');
			sb.append("  crooked water the links miss\n");
			sb.append("  from         ").append(a >>> 14).append(", ").append(a & 0x3FFF).append(", 0\n");
			sb.append("  to           ").append(b >>> 14).append(", ").append(b & 0x3FFF).append(", 0\n");
			sb.append("  narrowest    ").append(g.channelClearance(c)).append(g.channelClearance(c) == 1 ? " (rocks beside)" : "").append('\n');
			sb.append("  boats fit    ").append(who.isEmpty() ? "none" : String.join(", ", who)).append('\n');
			sb.append("  sea areas    ").append(String.join(", ", areas)).append('\n');
			sb.append(String.format(java.util.Locale.ROOT, "  length       %.1f tiles%n", len));
			sb.append(String.format(java.util.Locale.ROOT, "  sailing      %.0f-%.0f ticks%n", len / 3.0, len / 1.5));
		}
		return sb.toString();
	}

	/** The channel through or next to the tile (the first one through it, else the first next to it), or -1. */
	public static int channelAt(SeaGraph g, int x, int y, int plane)
	{
		if (g == null || plane != 0)
		{
			return -1;
		}
		int next = -1;
		for (int c = 0; c < g.channelCount(); c++)
		{
			int d = distance(g, c, x, y);
			if (d == 0)
			{
				return c;
			}
			if (d == 1 && next < 0)
			{
				next = c;
			}
		}
		return next;
	}

	/** The bar under the map's text for a selected channel. */
	public static String title(SeaGraph g, int c)
	{
		int[] p = g.channelPoints(c);
		String who = g.channelClearance(c) >= BoatType.SLOOP.hardClearance() ? "every boat fits" : "rafts only";
		return String.format(java.util.Locale.ROOT, "Sea channel %d of %d (%s, %.0f tiles): %d, %d to %d, %d - details in the Inspector",
			c + 1, g.channelCount(), who, g.channelLength(c), p[0] >>> 14, p[0] & 0x3FFF, p[p.length - 1] >>> 14, p[p.length - 1] & 0x3FFF);
	}

	/** Tiles (Chebyshev) from the tile to the channel's stretches, counted up to 2. */
	private static int distance(SeaGraph g, int c, int x, int y)
	{
		int best = 2;
		int[] p = g.channelPoints(c);
		for (int i = 0; i + 1 < p.length && best > 0; i++)
		{
			for (int t : g.lineTiles(p[i] >>> 14, p[i] & 0x3FFF, p[i + 1] >>> 14, p[i + 1] & 0x3FFF))
			{
				best = Math.min(best, Math.max(Math.abs((t >>> 14) - x), Math.abs((t & 0x3FFF) - y)));
			}
		}
		return best;
	}

	/** Whether the channel's stretches pass through the tile or one of the 8 round it. */
	private static boolean near(SeaGraph g, int c, int x, int y)
	{
		int[] p = g.channelPoints(c);
		for (int i = 0; i + 1 < p.length; i++)
		{
			for (int t : g.lineTiles(p[i] >>> 14, p[i] & 0x3FFF, p[i + 1] >>> 14, p[i + 1] & 0x3FFF))
			{
				if (Math.abs((t >>> 14) - x) <= 1 && Math.abs((t & 0x3FFF) - y) <= 1)
				{
					return true;
				}
			}
		}
		return false;
	}
}
