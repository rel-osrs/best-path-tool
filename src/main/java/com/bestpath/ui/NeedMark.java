package com.bestpath.ui;

import com.bestpath.route.NeedStatus;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * The mark beside a thing a card needs: a green check (the player has it), a red X (they don't), a question mark (not
 * known for sure), or the world map's bank symbol (it is in the bank; the same sprite as the bank card's,
 * {@link IconChooser#BANK}). The check and X are drawn as small shapes (no picture files). One helper for the step
 * cards' needs ({@link NeedsRow}), the bank card's box and the clue card's rows. Swing thread only.
 */
final class NeedMark
{
	/** The mark's box, in pixels (square). */
	static final int SIZE = 12;
	/** Space between a mark and the picture after it. */
	static final int GAP = 2;

	private NeedMark()
	{
	}

	/** True if this status gets a mark (an unknown one gets none). */
	static boolean shown(NeedStatus s)
	{
		return s != null && s != NeedStatus.UNKNOWN;
	}

	/** The mouse-over of a thing with its mark: "Needs Agility 51: you have it" ({@code what} alone when unknown). */
	static String tooltip(String what, NeedStatus s)
	{
		return shown(s) ? what + ": " + s.getWords() : what;
	}

	/**
	 * Draws the mark in the {@link #SIZE} box at ({@code x}, {@code y}). The bank symbol may not be loaded yet: then
	 * nothing is drawn and {@code onLoad} (may be null: when painting, ask with {@link #preload} beforehand) runs when
	 * it arrives.
	 */
	static void paint(Graphics2D g, NeedStatus s, int x, int y, IconCache icons, Runnable onLoad)
	{
		if (!shown(s))
		{
			return;
		}
		if (s == NeedStatus.BANK)
		{
			BufferedImage img = icons.get(IconKey.sprite(IconChooser.BANK), SIZE, SIZE, onLoad);
			if (img != null)
			{
				g.drawImage(img, x + (SIZE - img.getWidth()) / 2, y + (SIZE - img.getHeight()) / 2, null);
			}
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (s == NeedStatus.MAYBE)
		{
			// a question mark: not known for sure
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(Ui.NOTE);
			g2.setFont(Ui.bold());
			FontMetrics fm = g2.getFontMetrics();
			g2.drawString("?", x + (SIZE - fm.stringWidth("?")) / 2, y + (SIZE + fm.getAscent() - fm.getDescent()) / 2);
			g2.dispose();
			return;
		}
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.setStroke(new BasicStroke(SIZE / 6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		double u = SIZE / 12.0;
		Path2D.Double p = new Path2D.Double();
		if (s == NeedStatus.HAVE)
		{
			g2.setColor(Ui.GOOD);
			p.moveTo(x + 1.8 * u, y + 6.4 * u);
			p.lineTo(x + 4.8 * u, y + 9.4 * u);
			p.lineTo(x + 10.4 * u, y + 2.8 * u);
		}
		else
		{
			g2.setColor(Ui.BAD);
			p.moveTo(x + 2.6 * u, y + 2.6 * u);
			p.lineTo(x + 9.4 * u, y + 9.4 * u);
			p.moveTo(x + 9.4 * u, y + 2.6 * u);
			p.lineTo(x + 2.6 * u, y + 9.4 * u);
		}
		g2.draw(p);
		g2.dispose();
	}

	/** Starts loading the bank symbol (so it is there when the card is first painted). */
	static void preload(NeedStatus s, IconCache icons, Runnable onLoad)
	{
		if (s == NeedStatus.BANK)
		{
			icons.get(IconKey.sprite(IconChooser.BANK), SIZE, SIZE, onLoad);
		}
	}

	/** A mark as its own component (the clue card's rows, the bank card's box), centred in a {@code w} x {@code h} box. */
	static final class Box extends JComponent
	{
		final NeedStatus status;
		private final IconCache icons;

		Box(NeedStatus status, int w, int h, IconCache icons)
		{
			this.status = status;
			this.icons = icons;
			Dimension d = new Dimension(w, h);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
			setToolTipText(shown(status) ? capital(status.getWords()) : null);
			preload(status, icons, this::repaint);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			// the bank symbol was asked for when made (repaints when it arrives): nothing more to wait for here
			NeedMark.paint((Graphics2D) g, status, (getWidth() - SIZE) / 2, (getHeight() - SIZE) / 2, icons, null);
		}
	}

	private static String capital(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
