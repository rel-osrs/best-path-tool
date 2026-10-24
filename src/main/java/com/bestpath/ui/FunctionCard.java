package com.bestpath.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One function on the Functions tab's first screen: a card with the title, a grey summary line (what the function knows
 * now, e.g. "Kalphites: 87 left") and an arrow on the right; a click opens the function's own page. Swing thread only.
 */
final class FunctionCard extends JPanel
{
	private static final int PAD = 6;
	/** The arrow on the right and the gap before it. */
	private static final int ARROW_W = FoldArrow.SIZE + 6;

	final String title;
	final Runnable open;
	final WrapLabel summary = new WrapLabel("", Ui.CONTENT_WIDTH - 2 * PAD - ARROW_W, Ui.small(), Ui.MUTED);

	/** {@code open}: shows the function's page. */
	FunctionCard(String title, Runnable open)
	{
		super(new BorderLayout(6, 0));
		this.title = title;
		this.open = open;
		setBackground(Ui.CARD);
		setBorder(Ui.padding(6, PAD, 6, PAD));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("Open " + title);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		JLabel name = Ui.label(title, Ui.bold(), Ui.TITLE);
		name.setAlignmentX(LEFT_ALIGNMENT);
		text.add(name);
		summary.setAlignmentX(LEFT_ALIGNMENT);
		text.add(summary);
		add(text, BorderLayout.CENTER);
		JLabel arrow = new JLabel(FoldArrow.of(false)); // pointing right: opens a page
		arrow.setForeground(Ui.MUTED);
		add(arrow, BorderLayout.EAST);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				open.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(Ui.CARD_CURRENT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(Ui.CARD);
			}
		});
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); // never stretched
	}

	/** The line under the title (empty: none). */
	void setSummary(String text)
	{
		summary.setText(text);
		summary.setVisible(text != null && !text.isEmpty());
		revalidate();
		repaint();
	}
}
