package com.bestpath.tool.map;

import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.DockDiagnostics;
import com.bestpath.core.sea.SeaPieces;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.List;

/**
 * The "Docks" layer: each dock's buoy (cyan, sea side) joined by a line to its
 * gangplank or mooring point (orange, land side); the land tiles where the
 * player stands are green. A dock whose buoy is not on the main ocean is grey.
 * Labels (name, level, quest) show when zoomed in.
 */
final class DockLayer
{
	private static final Color BUOY = new Color(0x00d0ff);
	private static final Color LAND = new Color(0xff8c00);
	private static final Color STAND = new Color(0x38e05a);
	private static final Color CUT_OFF = new Color(0x9a9a9a);
	private static final Color LABEL_BG = new Color(0, 0, 0, 170);

	private List<Dock> docks = List.of();
	private SeaPieces pieces;

	void setData(List<Dock> docks, SeaPieces pieces)
	{
		this.docks = docks == null ? List.of() : docks;
		this.pieces = pieces;
	}

	void draw(Graphics2D g, MapViewport vp, int plane, int tx0, int ty0, int tx1, int ty1)
	{
		double s = vp.getScale();
		Stroke old = g.getStroke();
		Dock labelled = null; // one label per dock: Wyrmscraig has two gangplanks on one buoy
		for (Dock d : docks)
		{
			if (DockDiagnostics.landPlane(d) != plane)
			{
				continue;
			}
			int minX = Math.min(d.getBuoyX(), DockDiagnostics.landX(d)) - 2;
			int maxX = Math.max(d.getBuoyX(), DockDiagnostics.landX(d)) + 4;
			int minY = Math.min(d.getBuoyY(), DockDiagnostics.landY(d)) - 2;
			int maxY = Math.max(d.getBuoyY(), DockDiagnostics.landY(d)) + 4;
			if (maxX < tx0 || minX > tx1 || maxY < ty0 || minY > ty1)
			{
				continue;
			}
			boolean main = pieces == null || DockDiagnostics.onMainOcean(d, pieces);
			double bx = vp.screenX(d.getBuoyX() + d.getBuoySize() / 2.0);
			double by = vp.screenY(d.getBuoyY() + d.getBuoySize() / 2.0);
			double lx = vp.screenX(DockDiagnostics.landX(d) + DockDiagnostics.landSize(d) / 2.0);
			double ly = vp.screenY(DockDiagnostics.landY(d) + DockDiagnostics.landSize(d) / 2.0);
			g.setStroke(new BasicStroke(2f));
			g.setColor(main ? Color.WHITE : CUT_OFF);
			g.drawLine((int) bx, (int) by, (int) lx, (int) ly);
			if (s >= 3)
			{
				// footprints and the tiles the player stands on
				g.setColor(main ? STAND : CUT_OFF);
				for (int[] t : d.getLandTiles())
				{
					g.fillRect((int) Math.floor(vp.screenX(t[0])) + 1, (int) Math.floor(vp.screenY(t[1] + 1)) + 1, (int) Math.ceil(s) - 2, (int) Math.ceil(s) - 2);
				}
				outline(g, vp, d.getBuoyX(), d.getBuoyY(), d.getBuoySize(), main ? BUOY : CUT_OFF);
				outline(g, vp, DockDiagnostics.landX(d), DockDiagnostics.landY(d), DockDiagnostics.landSize(d), main ? LAND : CUT_OFF);
			}
			else
			{
				int r = 4;
				g.setColor(main ? BUOY : CUT_OFF);
				g.fillOval((int) bx - r, (int) by - r, 2 * r, 2 * r);
				g.setColor(main ? LAND : CUT_OFF);
				g.fillOval((int) lx - r, (int) ly - r, 2 * r, 2 * r);
			}
			boolean sameDock = labelled != null && labelled.getId() == d.getId() && labelled.getBuoyX() == d.getBuoyX() && labelled.getBuoyY() == d.getBuoyY();
			if (s >= 0.6 && !sameDock)
			{
				labelled = d;
				String text = d.getName() + " (" + DockDiagnostics.requirementText(d) + ")" + (d.getShipwright() != null ? ", " + d.getShipwright() : "") + (main ? "" : " - not on the main ocean");
				FontMetrics fm = g.getFontMetrics();
				int x = (int) lx + 6;
				int y = (int) ly - 6;
				g.setColor(LABEL_BG);
				g.fillRect(x - 2, y - fm.getAscent(), fm.stringWidth(text) + 4, fm.getHeight());
				g.setColor(main ? Color.WHITE : CUT_OFF);
				g.drawString(text, x, y);
			}
		}
		g.setStroke(old);
	}

	private static void outline(Graphics2D g, MapViewport vp, int x, int y, int size, Color c)
	{
		int sx = (int) Math.floor(vp.screenX(x));
		int sy = (int) Math.floor(vp.screenY(y + size));
		int w = (int) Math.floor(vp.screenX(x + size)) - sx;
		g.setColor(c);
		g.drawRect(sx, sy, w, w);
	}
}
