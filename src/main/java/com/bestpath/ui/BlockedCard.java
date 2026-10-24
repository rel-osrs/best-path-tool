package com.bestpath.ui;

import com.bestpath.route.NeedStatus;
import com.bestpath.route.NoRouteWhy;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import com.bestpath.route.TransportSettings;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A "no route" card: one transport (or part of a way by boat) the way needs that the player can't use yet. It looks
 * like a route's step card (the same picture, title and needs, each need with its {@link NeedMark}), with a red
 * outline, and says why: needs without a small picture with their X, then the reasons in words ("Turned off in
 * Settings: Shortcuts."). No "Never use" or - / +: it is not a route step. A transport's card has "Skip": skipped for
 * this route, the explanation then shows the next way without it (Undo at the top brings it back). A blacklisted
 * transport offers "Restore". A transport type, a boat or "A shipwright may bring my boat" switched off in Settings
 * gets that Settings tick box on the card: it shows the current state, and a click flips it, saves and reroutes. No
 * coordinates. Swing thread only.
 */
final class BlockedCard extends JPanel
{
	private static final int GAP = 6;
	private static final int TEXT_W = StepCard.TEXT_W;

	private final NoRouteWhy.Blocker blocker;
	final List<String> lines = new ArrayList<>();
	JButton restore;
	JButton skip;
	final List<JCheckBox> toggles = new ArrayList<>();
	/** The text column (developer details are added at its end). */
	private final JPanel textColumn;

	/**
	 * {@code settings}: the player's settings now (each tick box shows its state in them); {@code onToggle}: a tick box
	 * clicked, with its new state (the Route tab saves the settings and searches again); {@code onSkip}: "Skip" pressed
	 * (the Route tab skips {@link NoRouteWhy.Blocker#getSkipId()} for this route, then searches again).
	 */
	BlockedCard(NoRouteWhy.Blocker blocker, IconCache icons, Consumer<String> onRestore, TransportSettings settings,
		BiConsumer<NoRouteWhy.Toggle, Boolean> onToggle, Consumer<NoRouteWhy.Blocker> onSkip)
	{
		this.blocker = blocker;
		RouteStep step = blocker.getStep();
		StepInfo info = step.getInfo();
		setLayout(new BorderLayout(GAP, 0));
		setBackground(Ui.CARD);
		setBorder(Ui.cardEdge(Ui.OUTLINE_BAD, false, GAP));

		JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		IconKey mainKey = IconChooser.main(step);
		NeedStatus mainMark = info.getItemId() >= 0 && IconKey.item(info.getItemId()).equals(mainKey) ? info.getItemStatus()
			: NeedStatus.UNKNOWN;
		StepCard.StepIcon icon = new StepCard.StepIcon(step.getKind(), mainKey, icons, mainMark);
		icon.setAlignmentX(LEFT_ALIGNMENT);
		left.add(icon);
		add(left, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		Ui.addLine(text, Ui.wrapped(step.getTitle(), TEXT_W, Ui.bold(), Ui.TITLE));
		lines.add(step.getTitle());
		NeedsRow needs = new NeedsRow(info.getNeeds(), icons, TEXT_W);
		if (!needs.isEmpty())
		{
			text.add(Box.createVerticalStrut(2));
			Ui.addLine(text, needs);
		}
		for (String m : blocker.getMissing())
		{
			text.add(Box.createVerticalStrut(1));
			Ui.addLine(text, markedLine(m, icons));
			lines.add("X " + m);
		}
		for (String r : blocker.getReasons())
		{
			Ui.addLine(text, Ui.wrapped("• " + r, TEXT_W, Ui.small(), Ui.NOTE));
			lines.add(r);
		}
		for (NoRouteWhy.Toggle t : blocker.getToggles())
		{
			// the Settings tab's own tick box, here on the card: its state now; a click flips it
			JCheckBox box = Ui.checkBox("");
			box.setName(TOGGLE_NAME);
			box.setSelected(t.isOn(settings));
			box.addActionListener(e -> onToggle.accept(t, box.isSelected()));
			text.add(Box.createVerticalStrut(3));
			Ui.addLine(text, new IconRow(box, icon(t), t.getLabel(), null, tip(t), icons, TEXT_W));
			toggles.add(box);
		}
		// "Skip" leaves this transport out for this route and shows the next way without it (not on a boat's card,
		// which is no transport, nor on one already skipped)
		boolean canSkip = blocker.getSkipId() != null && !blocker.isSkipped();
		if (blocker.getRestoreId() != null || canSkip)
		{
			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			buttons.setOpaque(false);
			if (canSkip)
			{
				skip = Ui.button("Skip", "Find another way this time");
				skip.addActionListener(e -> onSkip.accept(blocker));
				buttons.add(skip);
			}
			if (blocker.getRestoreId() != null)
			{
				restore = Ui.button("Restore", "Use this transport again");
				restore.addActionListener(e -> onRestore.accept(blocker.getRestoreId()));
				if (canSkip)
				{
					buttons.add(Box.createHorizontalStrut(4));
				}
				buttons.add(restore);
			}
			text.add(Box.createVerticalStrut(4));
			Ui.addLine(text, buttons);
		}
		add(text, BorderLayout.CENTER);
		textColumn = text;
	}

	/** Developer details, as on a step card ({@link DeveloperLines}). */
	void showDeveloper()
	{
		DeveloperLines.addTo(textColumn, DeveloperLines.of(blocker.getStep()), TEXT_W);
		revalidate();
	}

	/** The component name of a card's tick boxes, so tools can find them by name. */
	static final String TOGGLE_NAME = "blocked-toggle";

	/** A tick box's picture: as in the Settings tab. */
	private static IconKey icon(NoRouteWhy.Toggle t)
	{
		switch (t.getWhat())
		{
			case TYPE:
				return IconChooser.category(t.getCategory());
			case BOAT:
				return IconChooser.boat(t.getBoatType());
			case SHIPWRIGHTS:
			default:
				return IconChooser.shipwright();
		}
	}

	/** A tick box's mouse-over. */
	private static String tip(NoRouteWhy.Toggle t)
	{
		String what;
		switch (t.getWhat())
		{
			case TYPE:
				what = "Tick to use " + t.getLabel().toLowerCase(Locale.ROOT) + " again";
				break;
			case BOAT:
				what = "Tick to let routes use " + t.getLabel();
				break;
			case SHIPWRIGHTS:
			default:
				what = "Let a shipwright bring your boat (costs coins)";
				break;
		}
		return what + " (also in Settings)";
	}

	/** A red X with a line of text beside it (a need without a small picture). */
	private static JComponent markedLine(String s, IconCache icons)
	{
		JPanel r = new JPanel(new BorderLayout(NeedMark.GAP + 2, 0));
		r.setOpaque(false);
		JPanel lead = new JPanel(new BorderLayout());
		lead.setOpaque(false);
		NeedMark.Box box = new NeedMark.Box(NeedStatus.MISSING, NeedMark.SIZE, NeedsRow.ICON_H, icons);
		box.setToolTipText(s + ": " + NeedStatus.MISSING.getWords());
		lead.add(box, BorderLayout.NORTH);
		r.add(lead, BorderLayout.WEST);
		r.add(Ui.wrapped(s, TEXT_W - NeedMark.SIZE - NeedMark.GAP - 2, Ui.small(), Ui.TEXT), BorderLayout.CENTER);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
		return r;
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
