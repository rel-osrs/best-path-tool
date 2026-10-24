package com.bestpath.tool.map;

import com.bestpath.tool.transport.CacheTableTeleports;
import com.bestpath.tool.transport.Suggestion;
import com.bestpath.tool.transport.TransportCandidate;
import com.bestpath.tool.transport.TransportData;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * Draws transport candidates (outlined in their category colour), each one's
 * best suggestion (a dashed arrow: dashed = only a suggestion, nothing is
 * accepted yet), and cache-table teleport destinations.
 */
final class TransportLayer
{
	/** Candidates are drawn from this zoom (px per tile). */
	static final double MIN_SCALE = 1.5;
	private static final Color NO_SUGGESTION = new Color(0xff9f1c);
	private static final Color TELEPORT = new Color(0xff4ecd);
	private static final BasicStroke DASHED = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f);

	private TransportData data;
	/** Candidate and teleport ids decided (deleted or confirmed) since the data was built: not drawn. */
	private java.util.Set<String> decided = java.util.Collections.emptySet();

	void setData(TransportData data)
	{
		this.data = data;
	}

	void setDecided(java.util.Set<String> decided)
	{
		this.decided = decided == null ? java.util.Collections.emptySet() : decided;
	}

	void drawCandidates(Graphics2D g, MapViewport vp, int plane, int x0, int y0, int x1, int y1)
	{
		if (data == null || vp.getScale() < MIN_SCALE)
		{
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
		FontMetrics fm = g.getFontMetrics();
		List<TransportCandidate> visible = data.inArea(x0 - 64, y0 - 64, x1 + 64, y1 + 64, plane);
		for (TransportCandidate c : visible)
		{
			if (decided.contains(c.id()))
			{
				continue;
			}
			Color col = new Color(c.getCategory().getRgb());
			double sx0 = vp.screenX(c.getX());
			double sx1 = vp.screenX(c.getX() + c.getSizeX());
			double syTop = vp.screenY(c.getY() + c.getSizeY());
			double syBot = vp.screenY(c.getY());
			double cx = (sx0 + sx1) / 2;
			double cy = (syTop + syBot) / 2;
			List<Suggestion> s = data.suggestionsFor(c);
			if (!s.isEmpty())
			{
				Suggestion best = s.get(0);
				boolean far = best.getDestPlane() != plane
					|| Math.max(Math.abs(best.getDestX() - c.getX()), Math.abs(best.getDestY() - c.getY())) > 64;
				if (!far)
				{
					double tx = vp.screenX(best.getDestX() + 0.5);
					double ty = vp.screenY(best.getDestY() + 0.5);
					int[] st = best.getStart();
					double fx = st != null ? vp.screenX(st[0] + 0.5) : cx;
					double fy = st != null ? vp.screenY(st[1] + 0.5) : cy;
					g.setColor(col);
					g.setStroke(DASHED);
					g.draw(new Line2D.Double(fx, fy, tx, ty));
					arrowHead(g, fx, fy, tx, ty);
				}
				else if (vp.getScale() >= 3)
				{
					String label = best.getDestPlane() > plane ? "↑ plane " + best.getDestPlane()
						: best.getDestPlane() < plane ? "↓ plane " + best.getDestPlane()
						: "→ " + best.getDestX() + "," + best.getDestY();
					drawLabel(g, fm, label, cx, syTop - 4, col);
				}
			}
			g.setStroke(new BasicStroke(2f));
			g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 70));
			g.fill(new java.awt.geom.Rectangle2D.Double(sx0, syTop, sx1 - sx0, syBot - syTop));
			g.setColor(s.isEmpty() ? NO_SUGGESTION : col);
			g.draw(new java.awt.geom.Rectangle2D.Double(sx0, syTop, sx1 - sx0, syBot - syTop));
			if (s.isEmpty() && vp.getScale() >= 3)
			{
				drawLabel(g, fm, "?", cx, syTop - 4, NO_SUGGESTION);
			}
		}
		g.setStroke(new BasicStroke(1f));
	}

	void drawDestinations(Graphics2D g, MapViewport vp, int plane, int x0, int y0, int x1, int y1)
	{
		if (data == null)
		{
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
		FontMetrics fm = g.getFontMetrics();
		int r = (int) Math.max(4, Math.min(10, vp.getScale()));
		for (CacheTableTeleports.Destination d : data.getDestinations())
		{
			if (decided.contains(d.id()) || d.plane != plane || d.x < x0 - 2 || d.x > x1 + 2 || d.y < y0 - 2 || d.y > y1 + 2)
			{
				continue;
			}
			double cx = vp.screenX(d.x + 0.5);
			double cy = vp.screenY(d.y + 0.5);
			g.setColor(Color.BLACK);
			g.fillOval((int) cx - r - 1, (int) cy - r - 1, 2 * r + 2, 2 * r + 2);
			g.setColor(TELEPORT);
			g.fillOval((int) cx - r, (int) cy - r, 2 * r, 2 * r);
			if (vp.getScale() >= 4)
			{
				drawLabel(g, fm, d.label(), cx, cy - r - 3, TELEPORT);
			}
		}
	}

	private TransportCandidate selected;
	private Suggestion selectedEnd;

	void setSelected(TransportCandidate c, Suggestion end)
	{
		selected = c;
		selectedEnd = end;
	}

	/**
	 * The selected transport: a solid line from the middle of the object to
	 * its end tile. Drawn on the start plane and on the end plane (with a
	 * "plane N" label for the end that is on another plane).
	 */
	void drawSelected(Graphics2D g, MapViewport vp, int plane)
	{
		TransportCandidate c = selected;
		Suggestion end = selectedEnd;
		if (c == null)
		{
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
		FontMetrics fm = g.getFontMetrics();
		double sx0 = vp.screenX(c.getX());
		double sx1 = vp.screenX(c.getX() + c.getSizeX());
		double syTop = vp.screenY(c.getY() + c.getSizeY());
		double syBot = vp.screenY(c.getY());
		double cx = (sx0 + sx1) / 2;
		double cy = (syTop + syBot) / 2;
		int[] knownStart = end != null ? end.getStart() : null;
		if (knownStart != null)
		{
			cx = vp.screenX(knownStart[0] + 0.5);
			cy = vp.screenY(knownStart[1] + 0.5);
		}
		boolean onStart = c.getPlane() == plane;
		boolean onEnd = end != null && end.getDestPlane() == plane;
		if (onStart)
		{
			g.setStroke(new BasicStroke(3f));
			g.setColor(Color.WHITE);
			g.draw(new java.awt.geom.Rectangle2D.Double(sx0 - 2, syTop - 2, sx1 - sx0 + 4, syBot - syTop + 4));
		}
		if (end == null || (!onStart && !onEnd))
		{
			g.setStroke(new BasicStroke(1f));
			return;
		}
		double tx = vp.screenX(end.getDestX() + 0.5);
		double ty = vp.screenY(end.getDestY() + 0.5);
		g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0, 0, 0, 200));
		g.draw(new Line2D.Double(cx, cy, tx, ty));
		g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(Color.WHITE);
		g.draw(new Line2D.Double(cx, cy, tx, ty));
		arrowHead(g, cx, cy, tx, ty);
		int r = (int) Math.max(6, vp.getScale() * 0.6);
		g.setStroke(new BasicStroke(3f));
		g.setColor(Color.WHITE);
		if (onEnd)
		{
			g.fillOval((int) tx - r, (int) ty - r, 2 * r, 2 * r);
		}
		else
		{
			g.drawOval((int) tx - r, (int) ty - r, 2 * r, 2 * r);
			drawLabel(g, fm, "end on plane " + end.getDestPlane(), tx, ty - r - 4, Color.WHITE);
		}
		if (!onStart)
		{
			drawLabel(g, fm, "start on plane " + c.getPlane(), cx, cy - 6, Color.WHITE);
		}
		g.setStroke(new BasicStroke(1f));
	}

	private static void drawLabel(Graphics2D g, FontMetrics fm, String text, double cx, double baseline, Color col)
	{
		int w = fm.stringWidth(text);
		int x = (int) (cx - w / 2.0);
		int y = (int) baseline;
		g.setColor(new Color(0, 0, 0, 170));
		g.fillRect(x - 3, y - fm.getAscent(), w + 6, fm.getHeight());
		g.setColor(col);
		g.drawString(text, x, y);
	}

	private static void arrowHead(Graphics2D g, double x0, double y0, double x1, double y1)
	{
		double a = Math.atan2(y1 - y0, x1 - x0);
		double len = 9;
		Path2D p = new Path2D.Double();
		p.moveTo(x1, y1);
		p.lineTo(x1 - len * Math.cos(a - 0.45), y1 - len * Math.sin(a - 0.45));
		p.lineTo(x1 - len * Math.cos(a + 0.45), y1 - len * Math.sin(a + 0.45));
		p.closePath();
		g.setStroke(new BasicStroke(1f));
		g.fill(p);
	}
}
