package com.bestpath.ui;

import com.bestpath.route.RouteStep;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Shared sizes, colours and small helpers for the sidebar (RuneLite's own colour scheme and fonts). */
final class Ui
{
	/** Outer padding of a tab (RuneLite's PluginPanel uses the same). */
	static final int PAD = PluginPanel.BORDER_OFFSET;
	/**
	 * Width of a tab's content when its scrollbar shows: 242 - 17 - 2 * 6 = 213 px. Wrapped text is laid out for
	 * this width so nothing is cut off whether the scrollbar shows or not.
	 */
	static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 2 * PAD;

	static final Color TITLE = Color.WHITE;
	static final Color TEXT = ColorScheme.TEXT_COLOR;
	static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();
	static final Color NOTE = ColorScheme.PROGRESS_INPROGRESS_COLOR;
	static final Color GOOD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color BAD = ColorScheme.PROGRESS_ERROR_COLOR.brighter();
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	/** The step the player is on now (the same yellow as the path overlay's current step). */
	static final Color CURRENT = new Color(255, 230, 0);
	/** Travel times, in yellow-orange. */
	static final Color TRAVEL = new Color(255, 190, 60);
	static final Color CARD_CURRENT = ColorScheme.DARKER_GRAY_HOVER_COLOR;

	private Ui()
	{
	}

	static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	static Font bold()
	{
		return FontManager.getRunescapeBoldFont();
	}

	static Font normal()
	{
		return FontManager.getRunescapeFont();
	}

	/** Badge colour per step kind. */
	static Color kindColor(RouteStep.Kind k)
	{
		switch (k)
		{
			case WALK:
				return new Color(90, 110, 130);
			case TRAVERSAL:
				return new Color(60, 140, 75);
			case DOOR:
				return new Color(145, 100, 50);
			case TELEPORT:
				return new Color(130, 70, 170);
			case BOAT:
				return new Color(45, 105, 170);
			case BANK:
				return new Color(170, 135, 40);
			case KILL:
				return new Color(160, 60, 55);
			case SAIL:
			default:
				return new Color(30, 135, 135);
		}
	}

	/** Text that wraps at {@code width} px with this font (see {@link WrapLabel}). */
	static WrapLabel wrapped(String text, int width, Font font, Color color)
	{
		return new WrapLabel(text, width, font, color);
	}

	static JLabel label(String text, Font font, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		return l;
	}

	/** A compact button in RuneLite's style. */
	static JButton button(String text, String tooltip)
	{
		JButton b = new JButton(text);
		b.setFont(small());
		b.setFocusPainted(false);
		b.setToolTipText(tooltip);
		b.setMargin(new Insets(2, 6, 2, 6));
		return b;
	}

	static Border padding(int top, int left, int bottom, int right)
	{
		return BorderFactory.createEmptyBorder(top, left, bottom, right);
	}

	/**
	 * Adds a row to a top-to-bottom panel, left-aligned. A row that is not a panel (a label, a button, a gap) also
	 * stretches across but never grows taller than it needs; a panel keeps its own maximum size.
	 */
	static void addRow(JPanel p, Component c)
	{
		if (c instanceof JComponent)
		{
			JComponent j = (JComponent) c;
			j.setAlignmentX(Component.LEFT_ALIGNMENT);
			if (!(c instanceof JPanel))
			{
				j.setMaximumSize(new Dimension(Integer.MAX_VALUE, j.getPreferredSize().height));
			}
		}
		p.add(c);
	}

	/** Adds a line to a card's top-to-bottom text column, left-aligned (its sizes are left as they are). */
	static void addLine(JPanel p, JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(c);
	}

	/** Left-aligns a row and lets it stretch across, never taller than it needs; returns the same row. */
	static JComponent left(JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		return c;
	}

	/** A tick box in the sidebar's style: small font, the text colour, no background and no focus ring. */
	static JCheckBox checkBox(String text)
	{
		JCheckBox b = new JCheckBox(text);
		b.setFont(small());
		b.setForeground(TEXT);
		b.setOpaque(false);
		b.setFocusPainted(false);
		return b;
	}

	/**
	 * A mouse listener that ticks the box when clicked (unless the box is disabled), for the text and picture beside a
	 * tick box: a click on them ticks it, as a checkbox's own text would.
	 */
	static MouseAdapter clickTicks(JCheckBox box)
	{
		return new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (box.isEnabled())
				{
					box.doClick(0);
				}
			}
		};
	}

	/** A tab's scroll pane: scrolls up and down only, without a border, the tab's background around the view. */
	static JScrollPane verticalScroll(JComponent view)
	{
		JScrollPane scroll = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(BACKGROUND);
		return scroll;
	}

	/** Gives a number box the small font and a typing field {@code columns} wide, with the tooltip on both. */
	static void smallSpinner(JSpinner spinner, int columns, String tooltip)
	{
		spinner.setFont(small());
		JComponent editor = spinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor)
		{
			JFormattedTextField field = ((JSpinner.DefaultEditor) editor).getTextField();
			field.setFont(small());
			field.setColumns(columns);
			field.setToolTipText(tooltip);
		}
		spinner.setToolTipText(tooltip);
	}

	/** A Settings row on the card colour with a little padding, laid out with a BorderLayout with these gaps. */
	static JPanel cardLine(int hgap, int vgap)
	{
		JPanel line = new JPanel(new BorderLayout(hgap, vgap));
		line.setBackground(CARD);
		line.setBorder(padding(3, 5, 3, 3));
		return line;
	}

	/**
	 * A list line: a wide button with its text on the left (a place to route to) and a small button at the end (take
	 * it off the list), stretching across, never taller than it needs.
	 */
	static JPanel buttonLine(JButton main, JButton end)
	{
		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		main.setHorizontalAlignment(JButton.LEFT);
		line.add(main, BorderLayout.CENTER);
		line.add(end, BorderLayout.EAST);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		return line;
	}

	/** A bank step's card outline (yellow). */
	static final Color OUTLINE_BANK = new Color(255, 230, 0);
	/** The outline of a card holding a reason the player can't get somewhere (red). */
	static final Color OUTLINE_BAD = BAD;
	/** The outline of the boat card at the top of a route started on the boat (blue). */
	static final Color OUTLINE_BOAT = new Color(70, 150, 255);
	/** The current step's frame on a card without an outline colour of its own. */
	static final Color OUTLINE_CURRENT = Color.WHITE;

	/**
	 * A card's edge: its outline colour ({@code outline}; null = none) 1 px wide, and the current step's frame 2 px wide
	 * (in the outline colour, else {@link #OUTLINE_CURRENT}), then padding up to {@code gap} on each side, so the
	 * contents never move when the card becomes current.
	 */
	static Border cardEdge(Color outline, boolean current, int gap)
	{
		int w = current ? 2 : outline != null ? 1 : 0;
		if (w == 0)
		{
			return padding(gap, gap, gap, gap);
		}
		Color c = outline != null ? outline : OUTLINE_CURRENT;
		return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c, w), padding(gap - w, gap - w, gap - w, gap - w));
	}
}
