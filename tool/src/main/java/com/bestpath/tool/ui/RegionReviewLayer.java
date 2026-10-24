package com.bestpath.tool.ui;

import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import com.bestpath.tool.review.RegionReview;
import com.bestpath.tool.review.RegionReview.Category;
import com.bestpath.tool.review.RegionReview.Overall;
import com.bestpath.tool.review.RegionReview.Status;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.function.IntSupplier;

/**
 * Map layer for the region review: each region is tinted by its overall
 * state (green complete, orange needs work, yellow in progress; untouched
 * regions are left clear). Zoomed in far enough, four small boxes in the
 * region's corner show the four checks (T V I S). The region picked in the
 * Region review tab gets a white dashed outline. Only reads the review.
 */
final class RegionReviewLayer implements MapView.Overlay
{
	private static final String[] LETTERS = {"T", "V", "I", "S"};
	private static final Color NOT_CHECKED = new Color(90, 90, 90);

	private final RegionReview review;
	private final IntSupplier selectedRegion;
	private boolean show;

	RegionReviewLayer(RegionReview review, IntSupplier selectedRegion)
	{
		this.review = review;
		this.selectedRegion = selectedRegion;
	}

	void setShow(boolean show)
	{
		this.show = show;
	}

	boolean isShown()
	{
		return show;
	}

	@Override
	public void paint(Graphics2D g, MapView view)
	{
		if (!show)
		{
			return;
		}
		MapViewport vp = view.getViewport();
		double regionPx = 64 * vp.getScale();
		int rx0 = (int) Math.floor(vp.worldX(0) / 64);
		int rx1 = (int) Math.floor(vp.worldX(vp.getWidth()) / 64);
		int ry0 = (int) Math.floor(vp.worldY(vp.getHeight()) / 64);
		int ry1 = (int) Math.floor(vp.worldY(0) / 64);
		Font font = g.getFont().deriveFont(Font.BOLD, 10f);
		for (int rx = Math.max(0, rx0); rx <= Math.min(255, rx1); rx++)
		{
			for (int ry = Math.max(0, ry0); ry <= Math.min(255, ry1); ry++)
			{
				int id = rx << 8 | ry;
				int x0 = (int) Math.round(vp.screenX(rx * 64));
				int x1 = (int) Math.round(vp.screenX((rx + 1) * 64));
				int yTop = (int) Math.round(vp.screenY((ry + 1) * 64));
				int yBot = (int) Math.round(vp.screenY(ry * 64));
				Overall o = review.overall(id);
				Color tint = tint(o);
				if (tint != null)
				{
					g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 90));
					g.fillRect(x0, yTop, x1 - x0, yBot - yTop);
					g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 220));
					g.drawRect(x0, yTop, x1 - x0, yBot - yTop);
				}
				if (regionPx >= 70 && o != Overall.NOT_STARTED)
				{
					drawChecks(g, font, id, x0 + 4, yTop + 4);
				}
			}
		}
		int sel = selectedRegion.getAsInt();
		if (sel >= 0)
		{
			int rx = sel >> 8;
			int ry = sel & 0xFF;
			int x0 = (int) Math.round(vp.screenX(rx * 64));
			int x1 = (int) Math.round(vp.screenX((rx + 1) * 64));
			int yTop = (int) Math.round(vp.screenY((ry + 1) * 64));
			int yBot = (int) Math.round(vp.screenY(ry * 64));
			g.setColor(new Color(255, 255, 255, 220));
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 6f}, 0f));
			g.drawRect(x0, yTop, x1 - x0, yBot - yTop);
			g.setStroke(new BasicStroke(1f));
		}
	}

	/** Four small boxes, one per category, coloured by its status. */
	private void drawChecks(Graphics2D g, Font font, int id, int x, int y)
	{
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int size = 14;
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++)
		{
			Status s = review.status(id, cats[i]);
			Color c = s == Status.COMPLETE ? RegionReviewPanel.COMPLETE : s == Status.NEEDS_WORK ? RegionReviewPanel.NEEDS_WORK : NOT_CHECKED;
			int bx = x + i * (size + 2);
			g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 220));
			g.fillRect(bx, y, size, size);
			g.setColor(Color.BLACK);
			g.drawRect(bx, y, size, size);
			g.setColor(Color.WHITE);
			g.drawString(LETTERS[i], bx + (size - fm.stringWidth(LETTERS[i])) / 2 + 1, y + (size + fm.getAscent()) / 2 - 1);
		}
	}

	private static Color tint(Overall o)
	{
		switch (o)
		{
			case COMPLETE:
				return RegionReviewPanel.COMPLETE;
			case NEEDS_WORK:
				return RegionReviewPanel.NEEDS_WORK;
			case IN_PROGRESS:
				return RegionReviewPanel.IN_PROGRESS;
			default:
				return null;
		}
	}
}
