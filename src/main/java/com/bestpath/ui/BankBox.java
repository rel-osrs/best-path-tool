package com.bestpath.ui;

import com.bestpath.route.StepInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * The bank card's box: a framed list of everything to take out, one row each: its mark (the bank symbol,
 * {@link NeedMark}), the item's picture at its full size, and its name with how many ("Law rune x2"). The name is the
 * game's (RuneLite's item definition, asked through the {@link IconCache}); until it arrives, the route data's name.
 * Swing thread only.
 */
final class BankBox extends JPanel
{
	/** The frame's colour (a light grey line on the card). */
	static final Color FRAME = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final int PAD = 4;
	private static final int ROW_GAP = 4;
	/** An item's picture: the game's own inventory size (36 x 32), drawn unscaled. */
	static final int PICTURE_W = 36;
	static final int PICTURE_H = 32;

	private final List<WrapLabel> names = new ArrayList<>();
	private final List<JPanel> rows = new ArrayList<>();
	private final List<NeedMark.Box> marks = new ArrayList<>();
	final List<String> lines = new ArrayList<>();

	/** @param width the box's whole width (frame included) */
	BankBox(List<StepInfo.TakeOut> items, IconCache icons, int width)
	{
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(FRAME, 1),
			Ui.padding(PAD - 1, PAD, PAD - 1, PAD)));
		setAlignmentX(LEFT_ALIGNMENT);
		int inner = width - 2 - 2 * PAD;
		int textW = inner - NeedMark.SIZE - NeedMark.GAP - PICTURE_W - ROW_GAP;
		for (StepInfo.TakeOut t : items)
		{
			JPanel row = new Row();
			row.setOpaque(false);
			row.setBorder(Ui.padding(1, 0, 1, 0));
			JPanel lead = new JPanel();
			lead.setOpaque(false);
			lead.setLayout(new BoxLayout(lead, BoxLayout.X_AXIS));
			NeedMark.Box mark = new NeedMark.Box(t.getStatus(), NeedMark.SIZE, PICTURE_H, icons);
			lead.add(mark);
			lead.add(Box.createHorizontalStrut(NeedMark.GAP));
			lead.add(new ClueStepCard.Picture(IconKey.item(t.getItemId()), PICTURE_W, PICTURE_H, icons));
			JPanel leadTop = new JPanel(new BorderLayout());
			leadTop.setOpaque(false);
			leadTop.add(lead, BorderLayout.NORTH);
			row.add(leadTop, BorderLayout.WEST);
			WrapLabel name = Ui.wrapped(text(t, null), textW, Ui.normal(), Ui.TEXT);
			// the row's text sits level with the picture's middle
			JPanel words = new JPanel(new BorderLayout());
			words.setOpaque(false);
			words.setBorder(Ui.padding(Math.max(0, (PICTURE_H - name.getFontMetrics(Ui.normal()).getHeight()) / 2), 0, 0, 0));
			words.add(name, BorderLayout.NORTH);
			row.add(words, BorderLayout.CENTER);
			row.setAlignmentX(LEFT_ALIGNMENT);
			int index = names.size();
			names.add(name);
			rows.add(row);
			marks.add(mark);
			lines.add(text(t, null));
			String known = icons.itemName(t.getItemId(), () -> showName(index, t, icons));
			if (known != null)
			{
				putName(index, t, known);
			}
			row.setToolTipText(NeedMark.tooltip(lines.get(index), t.getStatus()));
			mark.setToolTipText(row.getToolTipText());
			add(row);
		}
	}

	/** One row: never taller than it needs (a name arriving later may wrap to two lines). */
	private static final class Row extends JPanel
	{
		Row()
		{
			super(new BorderLayout(ROW_GAP, 0));
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/** "Law rune x2", "20 coins", "Iron med helm": {@code name} (null: the fallback name) and how many. */
	static String text(StepInfo.TakeOut t, String name)
	{
		String n = name == null || name.isEmpty() ? t.fallbackName() : name;
		if (t.getItemId() == StepInfo.COINS_ITEM)
		{
			return t.getCount() + (t.getCount() == 1 ? " coin" : " coins");
		}
		return t.getCount() > 1 ? n + " x" + t.getCount() : n;
	}

	private void showName(int index, StepInfo.TakeOut t, IconCache icons)
	{
		String n = icons.itemName(t.getItemId(), null);
		if (n != null)
		{
			putName(index, t, n);
			revalidate();
			repaint();
		}
	}

	private void putName(int index, StepInfo.TakeOut t, String name)
	{
		String s = text(t, name);
		lines.set(index, s);
		names.get(index).setText(s);
		String tip = NeedMark.tooltip(s, t.getStatus());
		rows.get(index).setToolTipText(tip);
		marks.get(index).setToolTipText(tip);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
