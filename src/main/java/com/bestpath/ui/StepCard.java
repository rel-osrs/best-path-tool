package com.bestpath.ui;

import com.bestpath.route.AnswerQuestions;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.NeedStatus;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One step of the route (walks have no card): its picture (an item, spell,
 * skill or map icon; else a coloured kind label), what to do, its ticks, what
 * it needs as small icons, notes and, for a transport, "Skip", "Never use" and
 * - / + for the player's own change of its ticks ({@link TickControl}), with
 * the step's total ticks right above them (only the total, never broken
 * down). Each need, and the step's own item, has its mark (a check, the bank
 * symbol or an X, {@link NeedMark}); a bank card lists what to take out in a
 * box ({@link BankBox}). No coordinates and no map images.
 */
final class StepCard extends JPanel
{
	static final int BADGE_W = 34;
	/** The picture's box (an item's 36x32 picture is fitted into it). */
	static final int ICON_H = 30;
	private static final int GAP = 6;
	/** Wrapped text width: card content width minus padding and the picture column. */
	static final int TEXT_W = Ui.CONTENT_WIDTH - 2 * GAP - BADGE_W - GAP - 8;

	private final JLabel num;
	final WrapLabel title;
	/** "Next, after walking 23 tiles" while the player walks here; empty otherwise. */
	final WrapLabel nextLine;
	private final RouteStep step;
	final int number;
	CardPlan.State state = CardPlan.State.NONE;

	/**
	 * The - / + on a transport's card: each click changes that transport's ticks for this player by 1. The Route tab
	 * saves and re-routes.
	 */
	interface TickControl
	{
		/** The player's latest change of this step's transport (saved, or clicked and not sent yet). */
		int change(RouteStep s);

		/** A click: the transport's new change (the card shows it already). */
		void changed(RouteStep s, int change);
	}

	/** The hint on a transport card (tooltip). */
	static final String TICK_HINT = "Change this transport's ticks for you";
	static final String LESS = "1 tick less";
	static final String MORE = "1 tick more";
	/** A network ride's card: its - / + changes every ride of the network. */
	static final String NETWORK_LESS = "1 tick less for every ride";
	static final String NETWORK_MORE = "1 tick more for every ride";

	/** The detail line ("to 3212, 3424, 0"; a card without - / + adds its ticks: "· 5 ticks"). */
	final WrapLabel detailLine;
	/** The step's ticks without the player's change on this card. */
	private final int baseTicks;
	/** The card change shown (never taking the ticks below 0). */
	private int shownChange;
	/** The total ticks above the - / + (null without the control). */
	TickStepper stepper;
	private final TickControl tickControl;
	/** A bank card's box of things to take out (null: none). */
	BankBox bankBox;
	/** "Never use" (null on a card without it). */
	JButton neverButton;
	/** The questions under the card (null: none). */
	QuestionBox questionBox;
	/** The text column (developer details are added at its end). */
	private JPanel textColumn;
	List<String> developerLines = List.of();

	/**
	 * @param ticks   the - / + control for a transport's card (null = none)
	 * @param answers Yes / No for the questions the step rests on (null = the questions are not shown)
	 */
	StepCard(int number, RouteStep step, IconCache icons, Consumer<RouteStep> onSkip, Consumer<RouteStep> onNeverUse,
		TickControl ticks, AnswerControl answers)
	{
		this.step = step;
		this.number = number;
		setLayout(new BorderLayout(GAP, 0));
		setBackground(Ui.CARD);
		setBorder(Ui.cardEdge(outline(step), false, GAP));

		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		IconKey mainKey = IconChooser.main(step);
		// the step's own item (an item teleport's) gets its mark on the picture's corner, like the needs below
		StepInfo info = step.getInfo();
		NeedStatus mainMark = info.getItemId() >= 0 && IconKey.item(info.getItemId()).equals(mainKey) ? info.getItemStatus()
			: NeedStatus.UNKNOWN;
		StepIcon icon = new StepIcon(step.getKind(), mainKey, icons, mainMark);
		icon.setAlignmentX(LEFT_ALIGNMENT);
		left.add(icon);
		left.add(Box.createVerticalStrut(2));
		num = Ui.label("#" + number, Ui.small(), Ui.MUTED);
		num.setAlignmentX(LEFT_ALIGNMENT);
		left.add(num);
		add(left, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		nextLine = Ui.wrapped("", TEXT_W, Ui.small(), Ui.CURRENT);
		Ui.addLine(text, nextLine);
		// a bank card with things to take out: "Bank at <bank>" and the box lists them
		boolean box = step.getKind() == RouteStep.Kind.BANK && !info.getTakeOut().isEmpty();
		title = Ui.wrapped(box ? CoreRouteEngine.bankTitle(info.getName(), List.of()) : step.getTitle(),
			TEXT_W, Ui.bold(), Ui.TITLE);
		Ui.addLine(text, title);
		baseTicks = step.getTicks() - step.getTickChange();
		// a first stop (STASH put-back, kill for a key) has an id (for Skip) but no transport ticks to change, and is
		// never blacklisted
		boolean stash = step.getKind().isStop();
		tickControl = step.getTransportId() == null || stash ? null : ticks;
		shownChange = tickControl == null ? step.getTickChange() : Math.max(-baseTicks, tickControl.change(step));
		// no coordinates; an empty detail line takes no room
		detailLine = Ui.wrapped(detailText(), TEXT_W, Ui.small(), Ui.TEXT);
		Ui.addLine(text, detailLine);
		if (box)
		{
			bankBox = new BankBox(info.getTakeOut(), icons, TEXT_W);
			text.add(Box.createVerticalStrut(3));
			Ui.addLine(text, bankBox);
		}
		NeedsRow needs = new NeedsRow(step.getInfo().getNeeds(), icons, TEXT_W);
		if (!needs.isEmpty())
		{
			text.add(Box.createVerticalStrut(2));
			Ui.addLine(text, needs);
		}
		for (String note : step.getNotes())
		{
			Ui.addLine(text, Ui.wrapped("• " + note, TEXT_W, Ui.small(), Ui.NOTE));
		}
		if (step.getTransportId() != null)
		{
			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			buttons.setOpaque(false);
			JButton skip = Ui.button("Skip", step.getKind() == RouteStep.Kind.KILL ? "Skip this stop for this route"
				: stash ? "Skip this stop (no more reminders for it)"
				: "Find another way this time");
			skip.addActionListener(e -> onSkip.accept(step));
			buttons.add(skip);
			if (!stash)
			{
				neverButton = Ui.button("Never use", "Never use this (undo in Settings)");
				neverButton.addActionListener(e -> onNeverUse.accept(step));
				buttons.add(Box.createHorizontalStrut(4));
				buttons.add(neverButton);
			}
			text.add(Box.createVerticalStrut(4));
			JPanel row = buttons;
			if (tickControl != null)
			{
				// the total ticks right above - / +, at the right end of the button row (fits the 242 px sidebar);
				// Skip / Never use sit level with - / +
				stepper = step.isGroupTicks() ? new TickStepper(NETWORK_LESS, NETWORK_MORE, this::clickTicks, this::typeTicks)
					: new TickStepper(LESS, MORE, this::clickTicks, this::typeTicks);
				stepper.setHint(TICK_HINT);
				JPanel low = new JPanel(new BorderLayout());
				low.setOpaque(false);
				low.add(buttons, BorderLayout.SOUTH);
				row = new JPanel(new BorderLayout(2, 0));
				row.setOpaque(false);
				row.add(low, BorderLayout.WEST);
				row.add(stepper, BorderLayout.EAST);
				setToolTipText(TICK_HINT);
				showTicks();
			}
			row.setAlignmentX(LEFT_ALIGNMENT);
			text.add(row);
		}
		add(text, BorderLayout.CENTER);
		textColumn = text;
		if (answers != null && !info.getQuestions().isEmpty())
		{
			// the question with Yes / No under the card, as soon as the route is shown
			questionBox = new QuestionBox(info.getQuestions(), answers);
			add(questionBox, BorderLayout.SOUTH);
		}
	}

	/** Yes / No under a card: the player's answer to a question the route rests on. */
	interface AnswerControl
	{
		/** The player answered (the data's words of the answer; yes or no). Saved for the account; the route follows. */
		void answer(String answer, boolean yes);
	}

	/**
	 * Developer details (RuneLite config "Developer details"): the transport id, data name, ticks, landing tile and the
	 * requirement part by part, in small grey lines at the end of the text ({@link DeveloperLines}).
	 */
	void showDeveloper()
	{
		developerLines = DeveloperLines.of(step);
		DeveloperLines.addTo(textColumn, developerLines, TEXT_W);
		revalidate();
	}

	/** The player answered this on another card (the same question): this card's buttons say so too. */
	void answered(String answer, boolean yes)
	{
		if (questionBox != null)
		{
			questionBox.answered(answer, yes);
		}
	}

	/**
	 * The questions a step's route rests on (the answers not given yet), each with Yes / No, in a strip under the card.
	 * A click saves the answer (Settings, "Your answers", lists it to change or clear) and the route is worked out
	 * again; until the new route replaces the card the buttons are greyed and a line says what was saved.
	 */
	static final class QuestionBox extends JPanel
	{
		static final String SAVED_YES = "Saved: yes. Updating the route...";
		static final String SAVED_NO = "Saved: no. Finding a way without it...";
		static final String QUESTION_TIP = "Routes follow your answer (change it in Settings)";

		final Map<String, JButton[]> buttons = new LinkedHashMap<>();
		final Map<String, WrapLabel> states = new LinkedHashMap<>();

		QuestionBox(List<String> questions, AnswerControl answers)
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(false);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.NOTE), Ui.padding(4, 0, 0, 0)));
			int w = Ui.CONTENT_WIDTH - 2 * GAP - 4;
			for (String a : questions)
			{
				WrapLabel q = Ui.wrapped(AnswerQuestions.question(a), w, Ui.small(), Ui.TITLE);
				q.setToolTipText(QUESTION_TIP);
				Ui.addLine(this, q);
				add(Box.createVerticalStrut(2));
				JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
				row.setOpaque(false);
				JButton yes = Ui.button("Yes", "Yes: saved for this account, not asked again");
				JButton no = Ui.button("No", "No: routes don't use it");
				yes.addActionListener(e ->
				{
					answered(a, true);
					answers.answer(a, true);
				});
				no.addActionListener(e ->
				{
					answered(a, false);
					answers.answer(a, false);
				});
				row.add(yes);
				row.add(Box.createHorizontalStrut(4));
				row.add(no);
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
				Ui.addLine(this, row);
				WrapLabel state = Ui.wrapped("", w, Ui.small(), Ui.NOTE);
				Ui.addLine(this, state);
				buttons.put(a, new JButton[]{yes, no});
				states.put(a, state);
			}
		}

		/** Greys this question's buttons and says what was saved (the new route then replaces the card). */
		void answered(String answer, boolean yes)
		{
			for (Map.Entry<String, JButton[]> e : buttons.entrySet())
			{
				if (e.getKey().trim().equalsIgnoreCase(answer.trim()))
				{
					e.getValue()[0].setEnabled(false);
					e.getValue()[1].setEnabled(false);
					states.get(e.getKey()).setText(yes ? SAVED_YES : SAVED_NO);
				}
			}
			revalidate();
			repaint();
		}
	}

	/** A - or + click: the change shown moves by one (total 0 to 10000 ticks), then the Route tab is told. */
	private void clickTicks(int by)
	{
		setChange(shownChange + by);
	}

	/** A typed total (already 0 to 10000): the change that gives it, then the Route tab is told. */
	void typeTicks(int total)
	{
		setChange(total - baseTicks);
	}

	private void setChange(int change)
	{
		int next = Math.max(-baseTicks, Math.min(TickStepper.MAX - baseTicks, change));
		if (next == shownChange || tickControl == null)
		{
			return;
		}
		shownChange = next;
		showTicks();
		tickControl.changed(step, next);
	}

	/** Shows another change for this card's transport (the same transport's - / + was clicked on another card). */
	void showTickChange(int change)
	{
		int c = Math.max(-baseTicks, change);
		if (c != shownChange)
		{
			shownChange = c;
			showTicks();
		}
	}

	private void showTicks()
	{
		if (stepper != null)
		{
			int total = Math.max(0, baseTicks + shownChange);
			stepper.show(TickStepper.ticks(total), total > 0, total < TickStepper.MAX);
		}
	}

	/**
	 * "to 3212, 3424, 0": a card with - / + shows its total ticks above them; a card without them (a sailing step)
	 * adds its ticks here: "to 3212, 3424, 0 · 5 ticks". Never a breakdown.
	 */
	private String detailText()
	{
		if (tickControl != null)
		{
			return step.getDetail();
		}
		String t = TickStepper.ticks(Math.max(0, step.getTicks()));
		return step.getDetail().isEmpty() ? t : step.getDetail() + " · " + t;
	}

	/**
	 * The card's outline colour: red on a step that starts or ends in the Wilderness (even on a bank step), yellow on
	 * a bank step; null = none.
	 */
	static Color outline(RouteStep step)
	{
		if (step.getInfo().isWilderness())
		{
			return Ui.OUTLINE_BAD;
		}
		return step.getKind() == RouteStep.Kind.BANK ? Ui.OUTLINE_BANK : null;
	}

	/**
	 * Shows where the player is: the current step gets a lighter card, a 2 px frame (white, or yellow on a bank card,
	 * as yellow outlines mean bank steps) and a yellow number (the path overlay's current-step yellow);
	 * while the player walks to this step (walks have no card) it gets the frame and a yellow "Next" line; steps
	 * already done a grey title.
	 *
	 * @param walkTiles for {@link CardPlan.State#NEXT}: the length of the walk to it
	 */
	void setProgress(CardPlan.State state, int walkTiles)
	{
		this.state = state;
		boolean framed = state == CardPlan.State.CURRENT || state == CardPlan.State.NEXT;
		setBackground(framed ? Ui.CARD_CURRENT : Ui.CARD);
		setBorder(Ui.cardEdge(outline(step), framed, GAP));
		num.setForeground(framed ? Ui.CURRENT : Ui.MUTED);
		title.setForeground(state == CardPlan.State.DONE ? Ui.MUTED : Ui.TITLE);
		String next = state != CardPlan.State.NEXT ? "" : walkTiles > 0
			? "Next, after walking " + walkTiles + (walkTiles == 1 ? " tile" : " tiles") : "Next";
		if (!next.equals(nextLine.getText()))
		{
			nextLine.setText(next);
			revalidate();
		}
		repaint();
	}

	RouteStep getStep()
	{
		return step;
	}

	@Override
	public Dimension getMaximumSize()
	{
		// stretch across, never taller than needed (BoxLayout parent)
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	/**
	 * The step's picture, fitted into {@link #BADGE_W} x {@link #ICON_H}; until it has arrived (or without one) the
	 * coloured kind label ("Tele", "Climb"...).
	 */
	static final class StepIcon extends JComponent
	{
		private final RouteStep.Kind kind;
		private final IconKey key;
		private final IconCache icons;
		/** The mark of the step's own item (the picture), drawn on its lower right corner; UNKNOWN = none. */
		private final NeedStatus mark;

		StepIcon(RouteStep.Kind kind, IconKey key, IconCache icons)
		{
			this(kind, key, icons, NeedStatus.UNKNOWN);
		}

		StepIcon(RouteStep.Kind kind, IconKey key, IconCache icons, NeedStatus mark)
		{
			this.kind = kind;
			this.key = key;
			this.icons = icons;
			this.mark = key == null || mark == null ? NeedStatus.UNKNOWN : mark;
			setFont(Ui.small());
			Dimension d = new Dimension(BADGE_W, key == null ? 16 : ICON_H);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
			String name = kind.name().charAt(0) + kind.name().substring(1).toLowerCase();
			setToolTipText(NeedMark.shown(this.mark) ? name + " (its item: " + this.mark.getWords() + ")" : name);
			if (key != null)
			{
				icons.get(key, BADGE_W, ICON_H, this::repaint); // starts loading now; repaints when it arrives
			}
			NeedMark.preload(this.mark, icons, this::repaint);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			BufferedImage img = key == null ? null : icons.get(key, BADGE_W, ICON_H, null);
			if (img != null)
			{
				g.drawImage(img, (getWidth() - img.getWidth()) / 2, (getHeight() - img.getHeight()) / 2, null);
				if (NeedMark.shown(mark))
				{
					// on the lower right corner, over a dark disc so it shows on any picture
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					int x = getWidth() - NeedMark.SIZE - 1;
					int y = getHeight() - NeedMark.SIZE - 1;
					g2.setColor(new Color(0, 0, 0, 150));
					g2.fillOval(x - 1, y - 1, NeedMark.SIZE + 2, NeedMark.SIZE + 2);
					NeedMark.paint(g2, mark, x, y, icons, null);
					g2.dispose();
				}
				return;
			}
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(Ui.kindColor(kind));
			g2.fillRoundRect(0, 0, getWidth(), 16, 6, 6);
			g2.setColor(Color.WHITE);
			g2.setFont(getFont());
			FontMetrics fm = g2.getFontMetrics();
			String s = kind.getLabel();
			int x = (getWidth() - fm.stringWidth(s)) / 2;
			int y = (16 - fm.getHeight()) / 2 + fm.getAscent();
			g2.drawString(s, x, y);
			g2.dispose();
		}
	}
}
