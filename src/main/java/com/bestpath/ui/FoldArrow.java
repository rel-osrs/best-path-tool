package com.bestpath.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The arrow left of a foldable heading in the Settings tab: pointing right when folded, down when open. Only the
 * heading gets the arrow; the section's content is not indented. Drawn, not a font character (RuneLite's fonts have no
 * arrows).
 */
final class FoldArrow implements Icon
{
	static final int SIZE = 9;
	private static final FoldArrow OPEN = new FoldArrow(true);
	private static final FoldArrow FOLDED = new FoldArrow(false);
	/** The client property of a heading made by {@link #heading}: Boolean.TRUE when open. */
	static final String OPEN_KEY = "bestpath.fold.open";

	private final boolean open;

	private FoldArrow(boolean open)
	{
		this.open = open;
	}

	static FoldArrow of(boolean open)
	{
		return open ? OPEN : FOLDED;
	}

	/** A heading: the title in bold with the arrow on its left; click it to fold or unfold (the caller listens). */
	static JLabel heading(String title, boolean open)
	{
		JLabel l = Ui.label(title, Ui.bold(), Ui.TITLE);
		l.setIconTextGap(5);
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		set(l, open);
		return l;
	}

	/**
	 * The panel for the heading of a block with its own fold ("Your answers", "Teleport and travel ticks"): a hand
	 * cursor and "Show or hide" on hover; the heading goes on its left, the caller listens for clicks.
	 */
	static JPanel header()
	{
		JPanel p = new JPanel(new BorderLayout(4, 0));
		p.setOpaque(false);
		p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		p.setToolTipText("Show or hide");
		return p;
	}

	/** Points the heading's arrow open or folded. */
	static void set(JLabel heading, boolean open)
	{
		heading.setIcon(of(open));
		heading.putClientProperty(OPEN_KEY, open);
	}

	static boolean isOpen(JLabel heading)
	{
		return Boolean.TRUE.equals(heading.getClientProperty(OPEN_KEY));
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Color colour = c == null ? Ui.TITLE : c.getForeground();
		g2.setColor(colour);
		Polygon p = new Polygon();
		if (open)
		{
			// pointing down
			p.addPoint(x, y + 2);
			p.addPoint(x + SIZE, y + 2);
			p.addPoint(x + SIZE / 2, y + SIZE - 1);
		}
		else
		{
			// pointing right
			p.addPoint(x + 2, y);
			p.addPoint(x + SIZE - 1, y + SIZE / 2);
			p.addPoint(x + 2, y + SIZE);
		}
		g2.fillPolygon(p);
		g2.dispose();
	}

	@Override
	public int getIconWidth()
	{
		return SIZE;
	}

	@Override
	public int getIconHeight()
	{
		return SIZE;
	}
}
