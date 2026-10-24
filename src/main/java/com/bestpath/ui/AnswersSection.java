package com.bestpath.ui;

import com.bestpath.route.AnswerQuestions;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The Settings tab's "Your answers": every Yes / No answer the player gave, to change or clear, and under them the
 * questions the map data asks that are not answered yet, to answer in advance. It has its own fold (folded at first,
 * not saved): a click on its header folds or unfolds it. The tab adds the header and the body as two of its rows.
 * Swing thread only.
 */
final class AnswersSection
{
	static final String TITLE = "Your answers";
	static final String HINT = "Your Yes / No answers. Clear one to be asked again.";
	/** The heading above the questions not answered yet. */
	static final String NOT_ANSWERED = "Not answered yet";

	/** The tab holding the section: laid out again after each change. */
	private final JComponent tab;
	/** Takes a changed (yes / no) or cleared (null) answer, to save it for the account. */
	private final BiConsumer<String, Boolean> onAnswer;
	/** The foldable header ("Your answers (3)" with the fold arrow on its left), the fold and its rows. */
	private final JPanel header = FoldArrow.header();
	private final JLabel title = FoldArrow.heading(TITLE, false);
	final JPanel body = new JPanel();
	private final JPanel list = new JPanel();
	/** The answers shown (the data's words -> yes / no), sorted by their question. */
	Map<String, Boolean> answers = Map.of();
	/** Every question the map data asks (its answer words, sorted by question); empty until the data has loaded. */
	private List<String> questions = List.of();
	/** Folded unless the player opened it (not saved: it starts folded). */
	private boolean open;

	AnswersSection(JComponent tab, BiConsumer<String, Boolean> onAnswer)
	{
		this.tab = tab;
		this.onAnswer = onAnswer;
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		Ui.addRow(body, Ui.wrapped(HINT, Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.MUTED));
		Ui.addRow(body, Box.createVerticalStrut(3));
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		Ui.addRow(body, list);
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggle();
			}
		});
		showList();
	}

	/** The header row: the title with its fold arrow. */
	JPanel header()
	{
		return header;
	}

	/** The fold under the header: the hint and the rows. */
	JPanel body()
	{
		return body;
	}

	/** Folds or unfolds the section (kept until the plugin restarts; the route stays). */
	void toggle()
	{
		open = !open;
		showList();
	}

	/** The player's answers (account switch, and after each answer): the data's words -> yes / no. */
	void showAnswers(Map<String, Boolean> given)
	{
		Map<String, Boolean> sorted = new TreeMap<>((a, b) ->
		{
			int c = AnswerQuestions.question(a).compareToIgnoreCase(AnswerQuestions.question(b));
			return c != 0 ? c : a.compareTo(b);
		});
		if (given != null)
		{
			sorted.putAll(given);
		}
		answers = Collections.unmodifiableMap(sorted);
		showList();
	}

	/**
	 * Every question the map data asks (its answer words, {@link AnswerQuestions#answersIn}), once it has loaded. The
	 * ones not answered yet are listed under the answers, with Yes / No.
	 */
	void showQuestions(List<String> all)
	{
		questions = all == null ? List.of() : List.copyOf(all);
		showList();
	}

	/** The questions not answered yet, in the order shown. */
	List<String> getUnanswered()
	{
		List<String> out = new ArrayList<>();
		for (String q : questions)
		{
			boolean given = false;
			for (String a : answers.keySet())
			{
				given |= AnswerQuestions.same(a, q);
			}
			if (!given)
			{
				out.add(q);
			}
		}
		return out;
	}

	/** Changes (yes / no) or clears (null) one answer: shown at once, then saved by the plugin (the route follows). */
	void setAnswer(String answer, Boolean yes)
	{
		Map<String, Boolean> next = new LinkedHashMap<>(answers);
		if (yes == null)
		{
			next.remove(answer);
		}
		else
		{
			next.put(answer, yes);
		}
		showAnswers(next);
		onAnswer.accept(answer, yes);
	}

	/** Rebuilds the title's count, the fold and the rows. */
	private void showList()
	{
		if (header.getComponentCount() == 0)
		{
			header.add(title, BorderLayout.WEST);
			header.setToolTipText("Show or hide");
		}
		List<String> unanswered = getUnanswered();
		int asked = answers.size() + unanswered.size();
		// "Your answers (3 of 27)": answered of all asked (just "(3)" until the map data has loaded)
		title.setText(asked == 0 ? TITLE : questions.isEmpty() ? TITLE + " (" + answers.size() + ")"
			: TITLE + " (" + answers.size() + " of " + asked + ")");
		FoldArrow.set(title, open);
		body.setVisible(open);
		list.removeAll();
		if (answers.isEmpty() && unanswered.isEmpty())
		{
			list.add(Ui.left(Ui.wrapped("Nothing here yet.", Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.TEXT)));
		}
		for (Map.Entry<String, Boolean> e : answers.entrySet())
		{
			list.add(Ui.left(answerRow(e.getKey(), e.getValue())));
			list.add(Box.createVerticalStrut(3));
		}
		if (!unanswered.isEmpty())
		{
			// the questions not answered yet, to answer in advance
			list.add(Box.createVerticalStrut(4));
			list.add(Ui.left(Ui.label(NOT_ANSWERED + " (" + unanswered.size() + ")", Ui.bold(), Ui.TITLE)));
			list.add(Box.createVerticalStrut(3));
			for (String q : unanswered)
			{
				list.add(Ui.left(unansweredRow(q)));
				list.add(Box.createVerticalStrut(3));
			}
		}
		list.revalidate();
		list.repaint();
		tab.revalidate();
		tab.repaint();
	}

	/** One answer: its question, then "Yes" / "No" with its mark, "Change" and "Clear". */
	private JPanel answerRow(String answer, boolean yes)
	{
		JPanel line = Ui.cardLine(0, 2);
		WrapLabel q = Ui.wrapped(AnswerQuestions.question(answer), Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.TEXT);
		q.setToolTipText(answer);
		line.add(q, BorderLayout.NORTH);
		JPanel low = new JPanel(new BorderLayout(4, 0));
		low.setOpaque(false);
		JLabel said = Ui.label(yes ? "Yes" : "No", Ui.bold(), yes ? Ui.GOOD : Ui.BAD);
		low.add(said, BorderLayout.WEST);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		buttons.setOpaque(false);
		JButton change = Ui.button("Change", "Change your answer to " + (yes ? "No" : "Yes") + " (routes follow it)");
		change.addActionListener(ev -> setAnswer(answer, !yes));
		JButton clear = Ui.button("Clear", "Forget this answer");
		clear.addActionListener(ev -> setAnswer(answer, null));
		buttons.add(change);
		buttons.add(Box.createHorizontalStrut(4));
		buttons.add(clear);
		low.add(buttons, BorderLayout.EAST);
		line.add(low, BorderLayout.SOUTH);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		return line;
	}

	/** A question not answered yet: the question, then "Not answered" with "Yes" and "No". */
	private JPanel unansweredRow(String answer)
	{
		JPanel line = Ui.cardLine(0, 2);
		WrapLabel q = Ui.wrapped(AnswerQuestions.question(answer), Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.TEXT);
		q.setToolTipText(answer);
		line.add(q, BorderLayout.NORTH);
		JPanel low = new JPanel(new BorderLayout(4, 0));
		low.setOpaque(false);
		low.add(Ui.label("Not answered", Ui.small(), Ui.MUTED), BorderLayout.WEST);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		buttons.setOpaque(false);
		JButton yes = Ui.button("Yes", "Answer Yes");
		yes.addActionListener(ev -> setAnswer(answer, true));
		JButton no = Ui.button("No", "Answer No");
		no.addActionListener(ev -> setAnswer(answer, false));
		buttons.add(yes);
		buttons.add(Box.createHorizontalStrut(4));
		buttons.add(no);
		low.add(buttons, BorderLayout.EAST);
		line.add(low, BorderLayout.SOUTH);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		return line;
	}
}
