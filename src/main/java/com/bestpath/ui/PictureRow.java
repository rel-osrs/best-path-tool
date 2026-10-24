package com.bestpath.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

/** A picture and text that wraps beside it (a muted second text under it, or none); no tick box. */
final class PictureRow extends JPanel
{
	PictureRow(IconKey key, String text, String detail, IconCache icons)
	{
		super(new BorderLayout(IconRow.GAP, 0));
		setOpaque(false);
		JPanel pic = new JPanel(new BorderLayout());
		pic.setOpaque(false);
		pic.add(new IconBox(key, icons, true), BorderLayout.NORTH);
		add(pic, BorderLayout.WEST);
		int w = Ui.CONTENT_WIDTH - IconBox.W - IconRow.GAP - 10;
		JPanel words = new JPanel();
		words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
		words.setOpaque(false);
		words.add(Ui.left(Ui.wrapped(text, w, Ui.small(), Ui.TEXT)));
		if (detail != null)
		{
			words.add(Ui.left(Ui.wrapped(detail, w, Ui.small(), Ui.MUTED)));
		}
		add(words, BorderLayout.CENTER);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}
}
