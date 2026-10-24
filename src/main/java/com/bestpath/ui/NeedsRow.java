package com.bestpath.ui;

import com.bestpath.route.NeedStatus;
import com.bestpath.route.StepInfo;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * What a step needs, as small icons with short texts ("[Agility] 51",
 * "[air rune] x3", "[quest] Plague City"), each with its mark in front
 * ({@link NeedMark}: a green check when the player has it, the bank symbol
 * when it is in their bank, a red X when they don't have it), wrapping at a
 * fixed width like {@link WrapLabel}. The mouse-over says each one in words,
 * and its mark ("Needs Agility 51: you have it"). Pictures come from the
 * {@link IconCache} and fill in when they arrive.
 */
final class NeedsRow extends JComponent
{
	/** The box each small icon is fitted into (an item's 36x32 picture is shown at half size). */
	static final int ICON_W = 18;
	static final int ICON_H = 16;
	private static final int ICON_GAP = 2;
	private static final int CHIP_GAP = 7;

	static final class Chip
	{
		final IconKey key;
		final NeedStatus status;
		final String tooltip;
		final List<String> lines;
		/** The text's colour; null = the row's. */
		final Color color;
		final Rectangle bounds = new Rectangle();

		Chip(IconKey key, NeedStatus status, String tooltip, List<String> lines)
		{
			this(key, status, tooltip, lines, null);
		}

		Chip(IconKey key, NeedStatus status, String tooltip, List<String> lines, Color color)
		{
			this.key = key;
			this.status = status;
			this.tooltip = tooltip;
			this.lines = lines;
			this.color = color;
		}

		/** The width in front of the text: the mark and the icon. */
		int lead()
		{
			return (NeedMark.shown(status) ? NeedMark.SIZE + NeedMark.GAP : 0) + (key == null ? 0 : ICON_W);
		}
	}

	private final IconCache icons;
	private final int wrapWidth;
	final List<Chip> chips = new ArrayList<>();
	private int height;

	NeedsRow(List<StepInfo.Need> needs, IconCache icons, int wrapWidth)
	{
		this.icons = icons;
		this.wrapWidth = wrapWidth;
		setFont(Ui.small());
		setForeground(Ui.TEXT);
		setAlignmentX(LEFT_ALIGNMENT);
		FontMetrics fm = getFontMetrics(getFont());
		for (StepInfo.Need n : needs)
		{
			IconKey key = IconChooser.need(n);
			Chip probe = new Chip(key, n.getStatus(), "", List.of());
			int textW = wrapWidth - probe.lead() - (key == null ? 0 : ICON_GAP);
			List<String> lines = n.getText().isEmpty() ? List.of() : WrapLabel.wrap(n.getText(), fm, textW);
			// the Wilderness warning in red
			Color color = n.getType() == StepInfo.Need.Type.WILDERNESS ? Ui.BAD : null;
			chips.add(new Chip(key, n.getStatus(), NeedMark.tooltip(n.getTooltip(), n.getStatus()), lines, color));
			if (key != null)
			{
				icons.get(key, ICON_W, ICON_H, this::repaint); // starts loading now; repaints when it arrives
			}
			NeedMark.preload(n.getStatus(), icons, this::repaint);
		}
		layoutChips(fm);
		setToolTipText(""); // per chip, see getToolTipText(MouseEvent)
	}

	/** True if there is nothing to show. */
	boolean isEmpty()
	{
		return chips.isEmpty();
	}

	private void layoutChips(FontMetrics fm)
	{
		int lineH = Math.max(ICON_H, fm.getHeight());
		int x = 0;
		int y = 0;
		int rowH = 0;
		for (Chip c : chips)
		{
			int textW = 0;
			for (String l : c.lines)
			{
				textW = Math.max(textW, fm.stringWidth(l));
			}
			int w = c.lead() + (c.key != null && textW > 0 ? ICON_GAP : 0) + textW;
			int h = Math.max(lineH, fm.getHeight() * Math.max(1, c.lines.size()));
			if (x > 0 && x + w > wrapWidth)
			{
				x = 0;
				y += rowH + 1;
				rowH = 0;
			}
			c.bounds.setBounds(x, y, w, h);
			x += w + CHIP_GAP;
			rowH = Math.max(rowH, h);
		}
		height = chips.isEmpty() ? 0 : y + rowH;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		for (Chip c : chips)
		{
			if (c.bounds.contains(e.getPoint()))
			{
				return c.tooltip;
			}
		}
		return null;
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(chips.isEmpty() ? 0 : wrapWidth, height);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, height);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
		g2.setFont(getFont());
		FontMetrics fm = g2.getFontMetrics();
		int lineH = Math.max(ICON_H, fm.getHeight());
		for (Chip c : chips)
		{
			int x = c.bounds.x;
			if (NeedMark.shown(c.status))
			{
				NeedMark.paint(g2, c.status, x, c.bounds.y + (lineH - NeedMark.SIZE) / 2, icons, null);
				x += NeedMark.SIZE + NeedMark.GAP;
			}
			if (c.key != null)
			{
				BufferedImage img = icons.get(c.key, ICON_W, ICON_H, null);
				if (img != null)
				{
					g2.drawImage(img, x + (ICON_W - img.getWidth()) / 2, c.bounds.y + (lineH - img.getHeight()) / 2, null);
				}
				else
				{
					// not here yet: a dim square where it will be
					g2.setColor(new Color(255, 255, 255, 30));
					g2.fillRect(x + 2, c.bounds.y + (lineH - ICON_H) / 2 + 1, ICON_W - 4, ICON_H - 2);
				}
				x += ICON_W + ICON_GAP;
			}
			g2.setColor(c.color != null ? c.color : getForeground());
			int y = c.bounds.y + (lineH - fm.getHeight()) / 2 + fm.getAscent();
			for (String l : c.lines)
			{
				g2.drawString(l, x, y);
				y += fm.getHeight();
			}
		}
		g2.dispose();
	}
}
