package com.bestpath.ui;

import com.bestpath.clue.ClueCard;
import com.bestpath.route.NeedStatus;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The last card of a clue route: the clue step itself. The clue scroll's picture and the clue's tier and type, its
 * text and place, every item the step needs with its picture and where the player has it, levels and quests, the
 * STASH unit, and a coloured status line when there is something to do (take from the bank, or what is missing).
 * Built from a {@link ClueCard} (made on the client thread); Swing thread only. No map images.
 */
final class ClueStepCard extends JPanel
{
	private static final int GAP = 6;
	/** The scroll's picture column on the left. */
	private static final int BADGE_COLUMN_W = StepCard.BADGE_W;
	/** A row's small picture box (an item's 36x32 picture at half size). */
	static final int ROW_ICON_W = NeedsRow.ICON_W;
	static final int ROW_ICON_H = NeedsRow.ICON_H;

	final ClueCard card;
	/** The lines shown, in order (tests read them). */
	final List<String> lines = new ArrayList<>();
	/** The rows' marks, in order (tests read them). */
	final List<NeedStatus> marks = new ArrayList<>();

	/** "Take me there" to Watson's noticeboard (STASH unit not known); runs {@code onWatson}; null: none. */
	JButton watson;

	ClueStepCard(int number, ClueCard card, IconCache icons, Runnable onWatson)
	{
		this.card = card;
		setLayout(new BorderLayout(GAP, 0));
		setBackground(Ui.CARD);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 2, 0, 0, stateColor(card.getState())), Ui.padding(GAP, GAP - 2, GAP, GAP)));

		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		Picture scroll = new Picture(card.getClueItemId() < 0 ? null : IconKey.item(card.getClueItemId()), StepCard.BADGE_W,
			StepCard.ICON_H, icons);
		scroll.setToolTipText(card.getTitle());
		scroll.setAlignmentX(LEFT_ALIGNMENT);
		left.add(scroll);
		left.add(Box.createVerticalStrut(2));
		JLabel num = Ui.label("#" + number, Ui.small(), Ui.MUTED);
		num.setAlignmentX(LEFT_ALIGNMENT);
		left.add(num);
		add(left, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		line(text, card.getTitle(), Ui.bold(), Ui.TITLE);
		if (!card.getText().isEmpty())
		{
			line(text, "\"" + card.getText() + "\"", Ui.small(), Ui.TEXT);
		}
		if (!card.getPlace().isEmpty() && !card.getPlace().equals(card.getText()))
		{
			line(text, card.getPlace(), Ui.small(), Ui.MUTED);
		}
		// no row for what to do at the spot (emotes and their order): RuneLite's clue scroll plugin shows that
		if (!card.getNote().isEmpty())
		{
			line(text, "Note: " + card.getNote(), Ui.small(), Ui.TEXT); // a hand-written note on the step
		}
		// a level or quest already met stays met, so its row (a green check) is left out
		List<ClueCard.OtherRow> others = new ArrayList<>();
		for (ClueCard.OtherRow o : card.getOthers())
		{
			// what can change (the slots that must be empty: "Wear: nothing") always shows
			if (card.mark(o) != NeedStatus.HAVE || !o.isLasting())
			{
				others.add(o);
			}
		}
		if (!card.getItems().isEmpty() || !others.isEmpty())
		{
			text.add(Box.createVerticalStrut(3));
		}
		for (ClueCard.ItemRow r : card.getItems())
		{
			row(text, r.getItemId() < 0 ? null : IconKey.item(r.getItemId()), (r.isWear() ? "Wear: " : "") + r.label(), Ui.small(),
				Ui.TEXT, where(r), whereColor(r.getWhere()), card.mark(r), icons);
		}
		for (ClueCard.OtherRow o : others)
		{
			row(text, IconChooser.clueOther(o.getText()), o.getText(), Ui.small(), Ui.TEXT, o.isMet() ? "Done" : "Not yet",
				o.isMet() ? Ui.GOOD : Ui.BAD, card.mark(o), icons);
		}
		if (!card.stashLine().isEmpty())
		{
			text.add(Box.createVerticalStrut(3));
			line(text, card.stashLine(), Ui.small(), Ui.TEXT).setToolTipText(ClueCard.STASH_TOOLTIP);
		}
		// the status line shows only when there is something to act on (missing items, take from the bank)
		if (!card.getStatus().isEmpty() && card.getState() != ClueCard.State.READY)
		{
			text.add(Box.createVerticalStrut(3));
			line(text, card.getStatus(), Ui.small(), stateColor(card.getState()));
		}
		add(text, BorderLayout.CENTER);
		if (card.learnsFromWatson())
		{
			add(watsonBox(onWatson), BorderLayout.SOUTH);
		}
	}

	/** The Watson box's text width: the card's whole width (under the scroll's picture too), less padding and edge. */
	static final int WATSON_TEXT_W = BADGE_COLUMN_W + GAP + StepCard.TEXT_W - 2 * GAP - 2;

	/**
	 * A box across the card's whole width under everything else: the words sending the player to Watson's house, and a
	 * wide, filled "Take me there" button.
	 */
	private JPanel watsonBox(Runnable onWatson)
	{
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(Ui.BACKGROUND);
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(Ui.padding(GAP, 0, 0, 0),
				BorderFactory.createMatteBorder(0, 2, 0, 0, Ui.NOTE)),
			Ui.padding(GAP, GAP, GAP, GAP)));
		WrapLabel words = Ui.wrapped(ClueCard.LEARN_FILLED_HINT, WATSON_TEXT_W, Ui.small(), Ui.NOTE);
		words.setToolTipText("So does the STASH chart in your house.");
		words.setAlignmentX(LEFT_ALIGNMENT);
		box.add(words);
		lines.add(ClueCard.LEARN_FILLED_HINT);
		if (onWatson != null)
		{
			watson = Ui.button("Take me there", "A route to Watson's noticeboard in Hosidius");
			watson.setFont(Ui.bold());
			watson.setBackground(Ui.NOTE);
			watson.setForeground(Color.BLACK);
			watson.setOpaque(true);
			watson.setMargin(new Insets(5, 6, 5, 6));
			watson.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			watson.setAlignmentX(LEFT_ALIGNMENT);
			watson.setMaximumSize(new Dimension(Integer.MAX_VALUE, watson.getPreferredSize().height));
			watson.addActionListener(e -> onWatson.run());
			box.add(Box.createVerticalStrut(GAP));
			box.add(watson);
		}
		return box;
	}

	/** Where the player has an item, in words ("In your inventory (wear it)"). */
	static String where(ClueCard.ItemRow r)
	{
		if (r.getWhere() == ClueCard.Where.CARRIED && r.isWear())
		{
			return "In your inventory (wear it there)";
		}
		return r.getWhere().getLabel();
	}

	static Color whereColor(ClueCard.Where w)
	{
		switch (w)
		{
			case WORN:
			case CARRIED:
			case STASH:
				return Ui.GOOD;
			case BANK:
			case MAYBE_STASH:
				return Ui.NOTE;
			case MISSING:
			default:
				return Ui.BAD;
		}
	}

	static Color stateColor(ClueCard.State s)
	{
		switch (s)
		{
			case READY:
				return Ui.GOOD;
			case BANK:
				return Ui.NOTE;
			case MISSING:
			default:
				return Ui.BAD;
		}
	}

	private WrapLabel line(JPanel p, String s, Font font, Color color)
	{
		WrapLabel l = Ui.wrapped(s, StepCard.TEXT_W, font, color);
		l.setAlignmentX(LEFT_ALIGNMENT);
		p.add(l);
		lines.add(s);
		return l;
	}

	/**
	 * A small picture with a line of text beside it, and (optionally) a coloured second line under the text; in front
	 * of the picture the row's mark (a check, the bank symbol or an X; none when unknown).
	 */
	private void row(JPanel p, IconKey key, String s, Font font, Color color, String second, Color secondColor,
		NeedStatus mark, IconCache icons)
	{
		JPanel r = new JPanel(new BorderLayout(4, 0));
		r.setOpaque(false);
		r.setBorder(Ui.padding(1, 0, 1, 0));
		boolean marked = NeedMark.shown(mark);
		if (key != null || marked)
		{
			JPanel lead = new JPanel();
			lead.setOpaque(false);
			lead.setLayout(new BoxLayout(lead, BoxLayout.X_AXIS));
			if (marked)
			{
				NeedMark.Box box = new NeedMark.Box(mark, NeedMark.SIZE, ROW_ICON_H, icons);
				lead.add(box);
				marks.add(mark);
				if (key != null)
				{
					lead.add(Box.createHorizontalStrut(NeedMark.GAP));
				}
			}
			if (key != null)
			{
				lead.add(new Picture(key, ROW_ICON_W, ROW_ICON_H, icons));
			}
			JPanel iconBox = new JPanel(new BorderLayout());
			iconBox.setOpaque(false);
			iconBox.add(lead, BorderLayout.NORTH);
			r.add(iconBox, BorderLayout.WEST);
		}
		int w = StepCard.TEXT_W - (key == null ? 0 : ROW_ICON_W + 4) - (marked ? NeedMark.SIZE + NeedMark.GAP + (key == null ? 4 : 0) : 0);
		JPanel words = new JPanel();
		words.setOpaque(false);
		words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
		WrapLabel first = Ui.wrapped(s, w, font, color);
		first.setAlignmentX(LEFT_ALIGNMENT);
		words.add(first);
		String shown = s;
		if (second != null)
		{
			WrapLabel l2 = Ui.wrapped(second, w, Ui.small(), secondColor);
			l2.setAlignmentX(LEFT_ALIGNMENT);
			words.add(l2);
			shown = s + " | " + second;
		}
		r.add(words, BorderLayout.CENTER);
		r.setAlignmentX(LEFT_ALIGNMENT);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
		p.add(r);
		lines.add(shown);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	/** A picture fitted into a box; empty until it has arrived (then repainted). */
	static final class Picture extends JComponent
	{
		private final IconKey key;
		private final IconCache icons;
		private final int w;
		private final int h;

		Picture(IconKey key, int w, int h, IconCache icons)
		{
			this.key = key;
			this.icons = icons;
			this.w = w;
			this.h = h;
			Dimension d = new Dimension(w, h);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
			if (key != null)
			{
				icons.get(key, w, h, this::repaint); // starts loading now; repaints when it arrives
			}
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			BufferedImage img = key == null ? null : icons.get(key, w, h, null);
			if (img != null)
			{
				g.drawImage(img, (getWidth() - img.getWidth()) / 2, (getHeight() - img.getHeight()) / 2, null);
			}
		}
	}
}
