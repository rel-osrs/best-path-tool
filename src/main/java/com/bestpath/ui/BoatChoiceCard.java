package com.bestpath.ui;

import com.bestpath.route.BoatCard;
import com.bestpath.route.BoatMode;
import com.bestpath.route.RouteStep;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * The card at the top of a route started on the player's boat. When the route leaves the boat at sea (it teleports away
 * first): "Park my boat" (this route only sails the boat to a dock with a shipwright first) and "Ocean only path" (the
 * player stays on the boat; greyed out when no dock or open sea is near the destination). With one of them on: what it
 * does, and a button back to the normal route. The plugin drops the card once the player is off the boat. Swing thread
 * only.
 */
final class BoatChoiceCard extends JPanel
{
	private static final int GAP = 6;
	static final String PARK = "Park my boat";
	static final String OCEAN = "Ocean only path";
	static final String NORMAL = "Normal route";
	static final String OCEAN_OFF = "Turn off ocean only";

	private final BoatCard card;
	JButton park;
	JButton ocean;
	JButton back;

	/** {@code onChoice}: a button pressed, with the mode chosen (the Route tab hands it to the plugin, which reroutes). */
	BoatChoiceCard(BoatCard card, IconCache icons, Consumer<BoatMode> onChoice)
	{
		this.card = card;
		setLayout(new BorderLayout(GAP, 0));
		setBackground(Ui.CARD);
		setBorder(Ui.cardEdge(Ui.OUTLINE_BOAT, false, GAP));

		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		StepCard.StepIcon icon = new StepCard.StepIcon(RouteStep.Kind.SAIL,
			card.getType() == null ? null : IconChooser.boat(card.getType()), icons);
		icon.setAlignmentX(LEFT_ALIGNMENT);
		left.add(icon);
		add(left, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		Ui.addLine(text, Ui.wrapped(card.title(), StepCard.TEXT_W, Ui.bold(), Ui.TITLE));
		text.add(Box.createVerticalStrut(2));
		Ui.addLine(text, Ui.wrapped(card.text(), StepCard.TEXT_W, Ui.small(), Ui.NOTE));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.setOpaque(false);
		if (card.getMode() == BoatMode.NORMAL)
		{
			park = Ui.button(PARK, "Sail " + boat() + " to a shipwright's dock first");
			park.addActionListener(e -> onChoice.accept(BoatMode.PARK));
			buttons.add(park);
			buttons.add(Box.createHorizontalStrut(4));
			ocean = Ui.button(OCEAN, card.isOceanOnlyNear()
				? "Stay on " + boat() + " the whole way"
				: BoatCard.notNearTip());
			ocean.setEnabled(card.isOceanOnlyNear());
			ocean.addActionListener(e -> onChoice.accept(BoatMode.OCEAN_ONLY));
			buttons.add(ocean);
		}
		else
		{
			boolean oceanOn = card.getMode() == BoatMode.OCEAN_ONLY;
			back = Ui.button(oceanOn ? OCEAN_OFF : NORMAL, "Back to the quickest route (may leave " + boat() + " at sea)");
			back.addActionListener(e -> onChoice.accept(BoatMode.NORMAL));
			buttons.add(back);
		}
		text.add(Box.createVerticalStrut(4));
		Ui.addLine(text, buttons);
		add(text, BorderLayout.CENTER);
	}

	private String boat()
	{
		return card.getBoat() == null ? "your boat" : card.getBoat();
	}
}
