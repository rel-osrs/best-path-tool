package com.bestpath.ui;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

/**
 * One Settings row: checkbox, icon, then the label wrapping under itself (never under the box or icon). The box
 * and icon sit centred on the first line of text. A click on the icon or text ticks the box, as a checkbox's own
 * text would.
 */
final class IconRow extends JPanel
{
	static final int GAP = 4;

	private final JCheckBox box;
	private final IconBox icon;
	private final WrapLabel label;
	/** A second, muted line under the label (a boat's place), or null. */
	private final WrapLabel sub;

	IconRow(JCheckBox box, IconKey key, String text, String tooltip, IconCache icons)
	{
		this(box, key, text, null, tooltip, icons);
	}

	IconRow(JCheckBox box, IconKey key, String text, String subText, String tooltip, IconCache icons)
	{
		this(box, key, text, subText, tooltip, icons, Ui.CONTENT_WIDTH);
	}

	/** As above, {@code width} wide (a "no route" card's tick box is narrower than a Settings row). */
	IconRow(JCheckBox box, IconKey key, String text, String subText, String tooltip, IconCache icons, int width)
	{
		super(null);
		this.box = box;
		setOpaque(false);
		box.setText("");
		box.setToolTipText(tooltip);
		icon = new IconBox(key, icons, box.isEnabled());
		int textW = width - box.getPreferredSize().width - IconBox.W - 2 * GAP;
		label = Ui.wrapped(text, textW, Ui.small(), box.isEnabled() ? Ui.TEXT : Ui.MUTED);
		sub = subText == null ? null : Ui.wrapped(subText, textW, Ui.small(), Ui.MUTED);
		add(box);
		add(icon);
		add(label);
		setToolTipText(tooltip);
		icon.setToolTipText(tooltip);
		label.setToolTipText(tooltip);
		if (sub != null)
		{
			add(sub);
			sub.setToolTipText(tooltip);
		}
		MouseAdapter click = Ui.clickTicks(box);
		addMouseListener(click);
		icon.addMouseListener(click);
		label.addMouseListener(click);
		if (sub != null)
		{
			sub.addMouseListener(click);
		}
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	/** The first line's height: the tallest of the box, the icon and one line of text. */
	private int firstLine()
	{
		return Math.max(Math.max(box.getPreferredSize().height, IconBox.H), lineHeight());
	}

	private int lineHeight()
	{
		return label.getFontMetrics(label.getFont()).getHeight();
	}

	@Override
	public Dimension getPreferredSize()
	{
		int first = firstLine();
		Dimension b = box.getPreferredSize();
		Dimension l = label.getPreferredSize();
		int textTop = (first - lineHeight()) / 2;
		int subH = sub == null ? 0 : sub.getPreferredSize().height;
		int w = Math.max(l.width, sub == null ? 0 : sub.getPreferredSize().width);
		return new Dimension(b.width + IconBox.W + 2 * GAP + w, Math.max(first, textTop + l.height + subH));
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}

	@Override
	public void doLayout()
	{
		int first = firstLine();
		Dimension b = box.getPreferredSize();
		box.setBounds(0, (first - b.height) / 2, b.width, b.height);
		int x = b.width + GAP;
		icon.setBounds(x, (first - IconBox.H) / 2, IconBox.W, IconBox.H);
		x += IconBox.W + GAP;
		Dimension l = label.getPreferredSize();
		int top = (first - lineHeight()) / 2;
		label.setBounds(x, top, Math.max(0, getWidth() - x), l.height);
		if (sub != null)
		{
			sub.setBounds(x, top + l.height, Math.max(0, getWidth() - x), sub.getPreferredSize().height);
		}
	}
}
