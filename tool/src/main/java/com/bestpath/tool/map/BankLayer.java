package com.bestpath.tool.map;

import com.bestpath.core.path.Tiles;
import com.bestpath.tool.bank.ToolBanks;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "Banks" layer (routes may begin at a bank): each bank
 * booth, chest or banker from {@code data/banks/banks.tsv} as a gold mark
 * (square = booth, circle = chest, diamond = banker), the tiles a player
 * stands on to use it in green, joined to it by a line. Red = no tile to use
 * it from (never used by routes). Zoomed in: the bank's name once per bank,
 * and what it needs.
 */
final class BankLayer
{
	private static final Color MARK = new Color(0xf4c430);
	private static final Color USE = new Color(0x38e05a);
	private static final Color UNUSABLE = new Color(0xef233c);
	private static final Color LABEL_BG = new Color(0, 0, 0, 170);

	void draw(Graphics2D g, MapViewport vp, int plane, int tx0, int ty0, int tx1, int ty1)
	{
		List<ToolBanks.Row> rows = ToolBanks.rows();
		double s = vp.getScale();
		Stroke old = g.getStroke();
		Set<String> labelled = new HashSet<>();
		for (ToolBanks.Row b : rows)
		{
			if (b.plane != plane || b.x < tx0 - 2 || b.x > tx1 + 2 || b.y < ty0 - 2 || b.y > ty1 + 2)
			{
				continue;
			}
			double cx = vp.screenX(b.x + 0.5);
			double cy = vp.screenY(b.y + 0.5);
			boolean usable = b.use.length > 0;
			if (s >= 3)
			{
				g.setStroke(new BasicStroke(1.5f));
				for (int t : b.use)
				{
					if (Tiles.plane(t) != plane)
					{
						continue;
					}
					g.setColor(USE);
					g.fillRect((int) Math.floor(vp.screenX(Tiles.x(t))) + 1, (int) Math.floor(vp.screenY(Tiles.y(t) + 1)) + 1,
						(int) Math.ceil(s) - 2, (int) Math.ceil(s) - 2);
					g.setColor(Color.WHITE);
					g.drawLine((int) cx, (int) cy, (int) vp.screenX(Tiles.x(t) + 0.5), (int) vp.screenY(Tiles.y(t) + 0.5));
				}
			}
			int r = Math.max(3, (int) Math.min(8, s * 0.35));
			g.setColor(usable ? MARK : UNUSABLE);
			switch (b.kind)
			{
				case "chest":
					g.fillOval((int) cx - r, (int) cy - r, 2 * r, 2 * r);
					break;
				case "banker":
					g.fillPolygon(new int[]{(int) cx, (int) cx + r, (int) cx, (int) cx - r}, new int[]{(int) cy - r, (int) cy, (int) cy + r, (int) cy}, 4);
					break;
				default:
					g.fillRect((int) cx - r, (int) cy - r, 2 * r, 2 * r);
					break;
			}
			g.setColor(Color.BLACK);
			g.setStroke(new BasicStroke(1f));
			g.drawString("$", (int) cx - g.getFontMetrics().stringWidth("$") / 2, (int) cy + g.getFontMetrics().getAscent() / 2 - 1);
			if (s >= 0.6 && labelled.add(b.bank))
			{
				String text = b.bank + (b.needs.isEmpty() ? "" : " (" + b.needs + ")") + (usable ? "" : " - no tile to use it from");
				FontMetrics fm = g.getFontMetrics();
				int x = (int) cx + r + 4;
				int y = (int) cy - r - 2;
				g.setColor(LABEL_BG);
				g.fillRect(x - 2, y - fm.getAscent(), fm.stringWidth(text) + 4, fm.getHeight());
				g.setColor(usable ? Color.WHITE : UNUSABLE);
				g.drawString(text, x, y);
			}
		}
		g.setStroke(old);
	}
}
