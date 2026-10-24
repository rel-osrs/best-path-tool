package com.bestpath.tool.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * What a {@link QuestionCards} card looks like: its words and its buttons, which depend on the card's kind. A button
 * press goes to the answer handler given. Swing thread only.
 */
final class QuestionCardPanels
{
	/** Called with the card and its answer word when a button is pressed. */
	private final BiConsumer<QuestionCards.Question, String> answer;
	/** The typed names of the place kind cards, by card id. */
	private final Map<String, JTextField> nameFields = new HashMap<>();

	QuestionCardPanels(BiConsumer<QuestionCards.Question, String> answer)
	{
		this.answer = answer;
	}

	/** The name typed in a place kind card's box, or null (no such card). */
	String typedName(String id)
	{
		JTextField field = nameFields.get(id);
		return field == null ? null : field.getText();
	}

	/** Forgets a closed card. */
	void forget(String id)
	{
		nameFields.remove(id);
	}

	/** The card's panel: its words, and its answer buttons by kind (or its own choices and a typing box). */
	JPanel build(QuestionCards.Question q)
	{
		JPanel card = new JPanel(new BorderLayout(4, 4));
		card.setBackground(QuestionCards.CARD);
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(QuestionCards.LEADER, 2),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		JLabel text = new JLabel("<html><div style='width:220px'>" + escape(q.text) + "</div></html>");
		card.add(text, BorderLayout.CENTER);
		if (q.action == QuestionCards.Action.PLACE_KIND)
		{
			placeCard(q, card);
		}
		else if (q.action == QuestionCards.Action.CACHE_CHECK)
		{
			text.setText("<html><div style='width:260px'>" + escape(q.text) + "</div></html>");
			card.add(cacheCheckButtons(q), BorderLayout.SOUTH);
		}
		else if (q.action == QuestionCards.Action.CHARTING_SPOT)
		{
			card.add(chartingButtons(q), BorderLayout.SOUTH);
		}
		else if ((q.choices != null && !q.choices.isEmpty()) || q.typeHint != null)
		{
			card.add(choicePanel(q), BorderLayout.SOUTH);
		}
		else if (q.action == QuestionCards.Action.CHANNEL)
		{
			card.add(channelButtons(q), BorderLayout.SOUTH);
		}
		else
		{
			card.add(yesNoButtons(q), BorderLayout.SOUTH);
		}
		return card;
	}

	/** An empty row for a card's buttons, on the right. */
	private static JPanel buttonRow()
	{
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttons.setOpaque(false);
		return buttons;
	}

	/** A button that answers the card with {@code word}. */
	private JButton answerButton(QuestionCards.Question q, String label, String tip, String word)
	{
		JButton button = new JButton(label);
		button.setToolTipText(tip);
		button.addActionListener(e -> answer.accept(q, word));
		return button;
	}

	/** The buttons of a game update card's kind. */
	private JPanel cacheCheckButtons(QuestionCards.Question q)
	{
		JPanel buttons = buttonRow();
		for (CacheCheckCards.Button b : CacheCheckCards.buttons(q.checkKind))
		{
			buttons.add(answerButton(q, b.label, b.tip, b.word));
		}
		return buttons;
	}

	/** Boat and Land, the guess marked. */
	private JPanel chartingButtons(QuestionCards.Question q)
	{
		JPanel buttons = buttonRow();
		buttons.add(answerButton(q, q.guessBoat ? "Boat (guess)" : "Boat",
			"Done from the boat: routes end on the boat at the nearest open sea", "BOAT"));
		buttons.add(answerButton(q, q.guessBoat ? "Land" : "Land (guess)",
			"Done on foot: routes walk to the spot (Ctrl+Z or the Change log takes it back)", "LAND"));
		return buttons;
	}

	/** Keep and Delete. */
	private JPanel channelButtons(QuestionCards.Question q)
	{
		JPanel buttons = buttonRow();
		buttons.add(answerButton(q, "Keep", "Approve it: boats may sail this way", "KEEP"));
		buttons.add(answerButton(q, "Delete", "Boats never sail this way (Ctrl+Z or the Change log takes it back)", "DELETE"));
		return buttons;
	}

	/** Yes and No, and "I built it" on transport cards. */
	private JPanel yesNoButtons(QuestionCards.Question q)
	{
		JPanel buttons = buttonRow();
		buttons.add(answerButton(q, "Yes",
			q.action == QuestionCards.Action.HIDE ? "Hide this sub-region" : q.action == QuestionCards.Action.REJECT ? "Reject this transport" : "Answer yes", "YES"));
		buttons.add(answerButton(q, "No", "Leave it as it is", "NO"));
		if (q.action != QuestionCards.Action.HIDE)
		{
			// the transport may be made by hand instead
			buttons.add(answerButton(q, "I built it", "You made this transport yourself; nothing changes, no transport is built for it", "BUILT"));
		}
		return buttons;
	}

	/** A place kind card: its map icon on the left, then the name box and Keep / Remove. */
	private void placeCard(QuestionCards.Question q, JPanel card)
	{
		if (q.picture != null)
		{
			java.awt.Image big = q.picture.getScaledInstance(q.picture.getWidth() * 2, q.picture.getHeight() * 2, java.awt.Image.SCALE_FAST);
			JLabel icon = new JLabel(new javax.swing.ImageIcon(big));
			icon.setVerticalAlignment(JLabel.TOP);
			card.add(icon, BorderLayout.WEST);
		}
		JPanel south = new JPanel(new GridLayout(0, 1, 0, 3));
		south.setOpaque(false);
		JPanel nameRow = new JPanel(new BorderLayout(4, 0));
		nameRow.setOpaque(false);
		nameRow.add(new JLabel("Name:"), BorderLayout.WEST);
		JTextField name = new JTextField(q.kindName == null ? "" : q.kindName);
		name.setToolTipText("The name the sidebar shows; change it before Keep to rename the kind");
		nameRow.add(name, BorderLayout.CENTER);
		nameFields.put(q.id, name);
		south.add(nameRow);
		JPanel buttons = buttonRow();
		buttons.add(answerButton(q, "Keep", "Keep it in the plugin's Nearest list (with the name above)", "KEEP"));
		buttons.add(answerButton(q, "Remove", "Leave it out of the plugin's Nearest list (Ctrl+Z or the Change log takes it back)", "REMOVE"));
		south.add(buttons);
		card.add(south, BorderLayout.SOUTH);
	}

	/** One button per choice, one under the other, then the typing box with Send. */
	private JPanel choicePanel(QuestionCards.Question q)
	{
		JPanel panel = new JPanel(new GridLayout(0, 1, 0, 3));
		panel.setOpaque(false);
		if (q.choices != null)
		{
			for (int k = 0; k < q.choices.size(); k++)
			{
				String choice = q.choices.get(k);
				JButton b = new JButton("<html>" + escape(choice) + "</html>");
				b.setHorizontalAlignment(JButton.LEFT);
				int number = k + 1;
				b.addActionListener(e -> answer.accept(q, "CHOICE " + number + " " + choice));
				panel.add(b);
			}
		}
		if (q.typeHint != null)
		{
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setOpaque(false);
			JTextField field = new JTextField();
			field.setToolTipText(q.typeHint);
			JButton send = new JButton("Send");
			send.setToolTipText("Send what you typed");
			Runnable sendTyped = () ->
			{
				String typed = field.getText().trim().replace('\n', ' ');
				if (!typed.isEmpty())
				{
					answer.accept(q, "TYPED " + typed);
				}
			};
			send.addActionListener(e -> sendTyped.run());
			field.addActionListener(e -> sendTyped.run());
			row.add(field, BorderLayout.CENTER);
			row.add(send, BorderLayout.EAST);
			JLabel hint = new JLabel("<html><div style='width:220px'><i>" + escape(q.typeHint) + "</i></div></html>");
			panel.add(hint);
			panel.add(row);
		}
		return panel;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
	}
}
