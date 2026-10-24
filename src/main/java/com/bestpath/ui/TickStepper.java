package com.bestpath.ui;

import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.function.IntConsumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * The total ticks with a small - and + right under it: on a transport's step card, on the Settings tab's teleport
 * category and network rows, and the bank stop. It shows only the total; whoever made it decides what a click changes.
 * Totals go from 0 to {@value #MAX} ticks. A click on the total opens a box to type one (Enter or clicking away takes
 * it, Escape keeps the old one; not a whole number: nothing changes; below 0 or above {@value #MAX}: the nearest of
 * the two). Swing thread only.
 */
final class TickStepper extends JPanel
{
	static final String NO_LESS = "It can't take fewer than 0 ticks";
	static final String NO_MORE = "It can't take more than 10000 ticks";
	/** The highest total a tick setting can have. */
	static final int MAX = 10000;
	static final String TYPE_HINT = "Click to type the ticks (0 to 10000)";

	final JLabel total;
	final JTextField field;
	private final JPanel totalCard;
	final JButton minus;
	final JButton plus;
	private final String minusTip;
	private final String plusTip;
	private final IntConsumer typed;

	/**
	 * @param click {@code -1} for "-", {@code +1} for "+"
	 * @param typed a typed total, already between 0 and {@value #MAX}
	 */
	TickStepper(String minusTip, String plusTip, IntConsumer click, IntConsumer typed)
	{
		this.minusTip = minusTip;
		this.plusTip = plusTip;
		this.typed = typed;
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		total = Ui.label(" ", Ui.small(), Ui.TEXT);
		total.setHorizontalAlignment(SwingConstants.CENTER);
		total.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
		total.setToolTipText(TYPE_HINT);
		total.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				startTyping();
			}
		});
		field = new JTextField(5);
		field.setFont(Ui.small());
		field.setHorizontalAlignment(SwingConstants.CENTER);
		field.setToolTipText("Type the ticks, then Enter");
		field.addActionListener(e -> finishTyping(true));
		field.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					finishTyping(false);
				}
			}
		});
		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				finishTyping(true);
			}
		});
		totalCard = new JPanel(new CardLayout());
		totalCard.setOpaque(false);
		totalCard.add(total, "show");
		totalCard.add(field, "type");
		totalCard.setAlignmentX(CENTER_ALIGNMENT);
		minus = button("-", minusTip);
		minus.addActionListener(e -> click.accept(-1));
		plus = button("+", plusTip);
		plus.addActionListener(e -> click.accept(1));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		buttons.setOpaque(false);
		buttons.add(minus);
		buttons.add(Box.createHorizontalStrut(2));
		buttons.add(plus);
		buttons.setAlignmentX(CENTER_ALIGNMENT);
		buttons.setMaximumSize(buttons.getPreferredSize());
		add(totalCard);
		add(Box.createVerticalStrut(1));
		add(buttons);
	}

	/** A small square-ish - or + button. */
	private static JButton button(String text, String tooltip)
	{
		JButton b = Ui.button(text, tooltip);
		b.setMargin(new Insets(2, 4, 2, 4));
		return b;
	}

	/**
	 * Shows a total ("8 ticks", "5-20 ticks"); {@code canGoDown} false greys "-" (already at 0 ticks), {@code canGoUp}
	 * false greys "+" (already at {@value #MAX}).
	 */
	void show(String text, boolean canGoDown, boolean canGoUp)
	{
		total.setText(text);
		minus.setEnabled(canGoDown);
		minus.setToolTipText(canGoDown ? minusTip : NO_LESS);
		plus.setEnabled(canGoUp);
		plus.setToolTipText(canGoUp ? plusTip : NO_MORE);
		Dimension d = total.getPreferredSize();
		// the box to type in takes the total's place: as wide as the total, and room for 10000
		Insets in = field.getInsets();
		int typeWidth = field.getFontMetrics(field.getFont()).stringWidth("10000") + in.left + in.right + 4;
		totalCard.setPreferredSize(new Dimension(Math.max(d.width, typeWidth), d.height + 4));
		totalCard.setMaximumSize(totalCard.getPreferredSize());
		revalidate();
		repaint();
	}

	/** Sets the mouse-over of the whole control and its total (not the buttons, which say what they do). */
	void setHint(String hint)
	{
		setToolTipText(hint);
		total.setToolTipText(hint + " " + TYPE_HINT + ".");
	}

	/** Opens the box to type a total, filled with the total shown (its first number). */
	void startTyping()
	{
		String digits = total.getText().replaceAll("^\\D*(\\d+).*$", "$1");
		field.setText(digits.matches("\\d+") ? digits : "");
		((CardLayout) totalCard.getLayout()).show(totalCard, "type");
		field.selectAll();
		field.requestFocusInWindow();
	}

	private boolean finishing;

	/** Closes the box; with {@code take}, a whole number becomes the new total (0 to {@value #MAX}). */
	void finishTyping(boolean take)
	{
		if (finishing || !field.isVisible())
		{
			return;
		}
		finishing = true;
		try
		{
			((CardLayout) totalCard.getLayout()).show(totalCard, "show");
			Integer n = take ? parse(field.getText()) : null;
			if (n != null)
			{
				typed.accept(n);
			}
		}
		finally
		{
			finishing = false;
		}
	}

	/**
	 * A typed total: a whole number (spaces, commas and a trailing "ticks" allowed) clamped to 0..{@value #MAX}; null
	 * if none.
	 */
	static Integer parse(String text)
	{
		String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s*ticks?$", "").replace(",", "")
			.replace(" ", "");
		if (!t.matches("-?\\d{1,9}"))
		{
			return null;
		}
		return clamp(Integer.parseInt(t));
	}

	static int clamp(int n)
	{
		return Math.max(0, Math.min(MAX, n));
	}

	@Override
	public Dimension getMaximumSize()
	{
		return getPreferredSize();
	}

	/** "1 tick", "8 ticks". */
	static String ticks(int n)
	{
		return n == 1 ? "1 tick" : n + " ticks";
	}

	/** "4 ticks", or for a range "5-20 ticks". */
	static String ticks(int lowest, int highest)
	{
		return lowest == highest ? ticks(lowest) : lowest + "-" + highest + " ticks";
	}
}
