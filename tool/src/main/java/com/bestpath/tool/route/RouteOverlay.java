package com.bestpath.tool.route;

import com.bestpath.core.path.Route;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.sea.CourseLines;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Draws the route check on every map view: the start (green) and
 * destination (red), the walks (blue line) and the transports (orange,
 * dashed; "up/down to plane N" where the other end is on another plane), sailing
 * courses (pink, straight legs with one arrow head each).
 * Shown whatever the active tool, until cleared.
 */
public final class RouteOverlay implements MapView.Overlay
{
	private static final Color WALK = new Color(0x4cc9f0);
	private static final Color HOP = new Color(0xff9f1c);
	private static final Color UNCONFIRMED = new Color(0xffd166);
	private static final Color START = new Color(0x2dc653);
	private static final Color END = new Color(0xef233c);
	/** A bank stop at the start of a route (gold, like the bank symbol). */
	private static final Color BANK = new Color(0xf4c430);

	private final RouteController route;

	public RouteOverlay(RouteController route)
	{
		this.route = route;
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		MapViewport vp = view.getViewport();
		int plane = view.getPlane();
		g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
		if (route.isShowSearched())
		{
			drawSearched(g, vp, plane);
		}
		Route r = route.getRoute();
		if (r != null && r.isFound())
		{
			int sameTileRow = 0; // labels of transports used on one tile (bring the boat, board) go one under the other
			int sameTile = -1;
			for (Route.Step s : r.getSteps())
			{
				int[] t = s.getTiles();
				switch (s.getKind())
				{
					case WALK:
						walk(g, vp, plane, t);
						break;
					case TRANSPORT:
						sameTileRow = t[0] == t[1] && t[0] == sameTile ? sameTileRow + 1 : 0;
						sameTile = t[0] == t[1] ? t[0] : -1;
						hop(g, vp, plane, s.getTransport(), t[0], t[1], s.getTicks(), sameTileRow * 16);
						break;
					case SAIL:
						sail(g, vp, plane, s, s.getTransport().getName() + " (" + s.getTicks() + "t)");
						break;
					case TELEPORT:
						if (Tiles.plane(t[0]) == plane)
						{
							double x = vp.screenX(Tiles.x(t[0]) + 0.5);
							double y = vp.screenY(Tiles.y(t[0]) + 0.5);
							ring(g, x, y, 9, HOP);
							label(g, "Teleport: " + s.getTransport().getName() + " (" + s.getTicks() + "t)", x + 12, y - 8, HOP);
						}
						break;
					case BANK:
						if (Tiles.plane(t[0]) == plane)
						{
							// the route begins with a bank visit
							double x = vp.screenX(Tiles.x(t[0]) + 0.5);
							double y = vp.screenY(Tiles.y(t[0]) + 0.5);
							ring(g, x, y, 9, BANK);
							label(g, "Bank (" + s.getTicks() + "t)" + (s.getTakeOut().isEmpty() ? "" : ": take out "
								+ String.join(", ", s.getTakeOut())), x + 12, y - 8, BANK);
						}
						break;
					default:
						break;
				}
			}
		}
		marker(g, vp, plane, route.getStart(), START, "Start");
		// destination out of reach: a dotted line from where the route stops to the destination
		int[] stop = route.getStopTile();
		int[] dest = route.getEnd();
		if (stop != null && dest != null && stop[2] == plane && dest[2] == plane)
		{
			double sx = vp.screenX(stop[0] + 0.5);
			double sy = vp.screenY(stop[1] + 0.5);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{2f, 5f}, 0f));
			g.setColor(stop[3] == 1 ? Color.WHITE : new Color(0xff9f1c));
			g.draw(new java.awt.geom.Line2D.Double(sx, sy, vp.screenX(dest[0] + 0.5), vp.screenY(dest[1] + 0.5)));
			ring(g, sx, sy, 6, stop[3] == 1 ? Color.WHITE : new Color(0xff9f1c));
			label(g, stop[3] == 1 ? "Stops here (in sight)" : stop[3] == com.bestpath.core.path.RouteDestination.OPEN_WATER
				? "Stops here (open water)" : "Stops here (not in sight)", sx + 10, sy + 18,
				stop[3] == 1 ? Color.WHITE : new Color(0xff9f1c));
		}
		marker(g, vp, plane, dest, END, "Destination");
		g.setStroke(new BasicStroke(1f));
	}

	private static final Color SEARCHED = new Color(0xb15cff);
	/** Tinted pictures of the searched sub-regions, per cell (regionId << 2 | plane); remade when the route changes. */
	private int[] imagesFor;
	private final java.util.Map<Integer, java.awt.image.BufferedImage> searchedImages = new java.util.HashMap<>();

	/** The sub-regions the tile search entered, tinted (one 64x64 picture per cell). */
	private void drawSearched(Graphics2D g, MapViewport vp, int plane)
	{
		int[] comps = route.getSearched();
		com.bestpath.core.graph.CoarseGraph graph = route.graph();
		if (graph == null || comps.length == 0)
		{
			return;
		}
		if (comps != imagesFor)
		{
			searchedImages.clear();
			imagesFor = comps;
			int rgb = new Color(SEARCHED.getRed(), SEARCHED.getGreen(), SEARCHED.getBlue(), 110).getRGB();
			for (int c : comps)
			{
				int cell = graph.componentCell(c);
				short[] labels = graph.cellLabels(cell >> 2, cell & 3);
				int label = c - graph.cellComponentBase(cell >> 2, cell & 3) + 1;
				java.awt.image.BufferedImage img = searchedImages.computeIfAbsent(cell,
					k -> new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB));
				for (int l = 0; l < 4096; l++)
				{
					if (labels[l] == label)
					{
						img.setRGB(l >> 6, 63 - (l & 63), rgb);
					}
				}
			}
		}
		Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		for (java.util.Map.Entry<Integer, java.awt.image.BufferedImage> e : searchedImages.entrySet())
		{
			int cell = e.getKey();
			if ((cell & 3) != plane)
			{
				continue;
			}
			int rx = cell >> 10;
			int ry = (cell >> 2) & 0xFF;
			int x0 = (int) Math.floor(vp.screenX(rx * 64));
			int x1 = (int) Math.floor(vp.screenX((rx + 1) * 64));
			int yTop = (int) Math.floor(vp.screenY((ry + 1) * 64));
			int yBot = (int) Math.floor(vp.screenY(ry * 64));
			g.drawImage(e.getValue(), x0, yTop, x1 - x0, yBot - yTop, null);
		}
		if (hint != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
		}
	}

	private static final Color SAIL = new Color(0xf72585);

	/**
	 * A sailing leg, with its name half way: the boat's course as straight legs in its exact headings (22.5 degrees
	 * apart; corners need not be whole tiles), each with one arrow head pointing the way, the same legs as the plugin
	 * draws; a course without corners: its sea tiles.
	 */
	private static void sail(Graphics2D g, MapViewport vp, int plane, Route.Step s, String what)
	{
		int[] t = s.getTiles();
		if (Tiles.plane(t[0]) != plane)
		{
			return;
		}
		double[] p = s.getCoursePoints();
		if (p.length >= 4)
		{
			drawCourse(g, vp, p, g.getClipBounds());
		}
		else
		{
			line(g, vp, t, SAIL);
		}
		int mid = t[t.length / 2];
		label(g, what, vp.screenX(Tiles.x(mid) + 0.5) + 10, vp.screenY(Tiles.y(mid) + 0.5) - 8, SAIL);
	}

	/**
	 * A course on the map: its legs corner to corner ({@code points}: x, y pairs in tiles, a tile's middle at its whole
	 * x / y) and one arrow head per leg, its tip on the leg's end (the turn, or the course's end) when that end is
	 * inside {@code clip} (null: everywhere); legs shorter than the head get none. Returns how many heads were drawn.
	 */
	static int drawCourse(Graphics2D g, MapViewport vp, double[] points, java.awt.Rectangle clip)
	{
		Path2D p = new Path2D.Double();
		int n = points.length / 2;
		double[] sx = new double[n];
		double[] sy = new double[n];
		for (int i = 0; i < n; i++)
		{
			sx[i] = vp.screenX(points[2 * i] + 0.5);
			sy[i] = vp.screenY(points[2 * i + 1] + 0.5);
			if (i == 0)
			{
				p.moveTo(sx[i], sy[i]);
			}
			else
			{
				p.lineTo(sx[i], sy[i]);
			}
		}
		float w = (float) Math.max(3, Math.min(8, vp.getScale() * 0.35));
		Path2D heads = new Path2D.Double();
		double[] buf = new double[6];
		int count = 0;
		double x0 = clip == null ? -1e9 : clip.getMinX();
		double y0 = clip == null ? -1e9 : clip.getMinY();
		double x1 = clip == null ? 1e9 : clip.getMaxX();
		double y1 = clip == null ? 1e9 : clip.getMaxY();
		for (int i = 1; i < n; i++)
		{
			if (CourseLines.endHead(sx[i - 1], sy[i - 1], sx[i], sy[i], x0, y0, x1, y1, 4 * w + 6, 2 * w + 3, buf))
			{
				heads.moveTo(buf[0], buf[1]);
				heads.lineTo(buf[2], buf[3]);
				heads.lineTo(buf[4], buf[5]);
				heads.closePath();
				count++;
			}
		}
		g.setStroke(new BasicStroke(w + 3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 0, 0, 160));
		g.draw(p);
		g.setStroke(new BasicStroke(2f));
		g.draw(heads);
		g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(SAIL);
		g.draw(p);
		g.fill(heads);
		return count;
	}

	private static void walk(Graphics2D g, MapViewport vp, int plane, int[] t)
	{
		if (Tiles.plane(t[0]) != plane)
		{
			return;
		}
		line(g, vp, t, WALK);
	}

	private static void line(Graphics2D g, MapViewport vp, int[] t, Color colour)
	{
		Path2D p = new Path2D.Double();
		p.moveTo(vp.screenX(Tiles.x(t[0]) + 0.5), vp.screenY(Tiles.y(t[0]) + 0.5));
		for (int i = 1; i < t.length; i++)
		{
			p.lineTo(vp.screenX(Tiles.x(t[i]) + 0.5), vp.screenY(Tiles.y(t[i]) + 0.5));
		}
		float w = (float) Math.max(3, Math.min(8, vp.getScale() * 0.35));
		g.setStroke(new BasicStroke(w + 3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 0, 0, 160));
		g.draw(p);
		g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(colour);
		g.draw(p);
	}

	private static void hop(Graphics2D g, MapViewport vp, int plane, Transport tr, int from, int to, int ticks, int down)
	{
		Color c = tr.isConfirmed() ? HOP : UNCONFIRMED;
		boolean here = Tiles.plane(from) == plane;
		boolean there = Tiles.plane(to) == plane;
		double sx = vp.screenX(Tiles.x(from) + 0.5);
		double sy = vp.screenY(Tiles.y(from) + 0.5);
		double ex = vp.screenX(Tiles.x(to) + 0.5);
		double ey = vp.screenY(Tiles.y(to) + 0.5);
		String what = tr.getName() + " (" + ticks + "t)";
		if (here && there)
		{
			g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 6f}, 0f));
			g.setColor(c);
			g.draw(new java.awt.geom.Line2D.Double(sx, sy, ex, ey));
			ring(g, sx, sy, 7, c);
			ring(g, ex, ey, 7, c);
			label(g, what, (sx + ex) / 2 + 10, (sy + ey) / 2 - 8 + down, c);
		}
		else if (here)
		{
			ring(g, sx, sy, 8, c);
			label(g, (Tiles.plane(to) > plane ? "Up to plane " : "Down to plane ") + Tiles.plane(to) + ": " + what, sx + 12, sy - 8, c);
		}
		else if (there)
		{
			ring(g, ex, ey, 8, c);
			label(g, "From plane " + Tiles.plane(from) + ": " + what, ex + 12, ey - 8, c);
		}
	}

	private static void marker(Graphics2D g, MapViewport vp, int plane, int[] t, Color c, String text)
	{
		if (t == null || t[2] != plane)
		{
			return;
		}
		double x = vp.screenX(t[0] + 0.5);
		double y = vp.screenY(t[1] + 0.5);
		int r = (int) Math.max(6, Math.min(12, vp.getScale() * 0.5));
		g.setColor(Color.BLACK);
		g.fillOval((int) x - r - 2, (int) y - r - 2, 2 * r + 4, 2 * r + 4);
		g.setColor(c);
		g.fillOval((int) x - r, (int) y - r, 2 * r, 2 * r);
		label(g, text, x + r + 5, y + 4, c);
	}

	private static void ring(Graphics2D g, double x, double y, int r, Color c)
	{
		g.setStroke(new BasicStroke(3f));
		g.setColor(Color.BLACK);
		g.drawOval((int) x - r - 1, (int) y - r - 1, 2 * r + 2, 2 * r + 2);
		g.setStroke(new BasicStroke(2f));
		g.setColor(c);
		g.drawOval((int) x - r, (int) y - r, 2 * r, 2 * r);
	}

	private static void label(Graphics2D g, String text, double x, double y, Color c)
	{
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text);
		g.setColor(new Color(0, 0, 0, 180));
		g.fillRect((int) x - 3, (int) y - fm.getAscent(), w + 6, fm.getHeight());
		g.setColor(c);
		g.drawString(text, (int) x, (int) y);
	}
}
