package com.bestpath.ui;

import com.bestpath.route.TransportSettings;
import java.awt.BorderLayout;
import java.util.function.IntConsumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A Settings row for a number of ticks: a picture, a name, and the total ticks above - / + (as on the step cards and
 * in "Teleport and travel ticks"). A click or a typed total is shown at once and sent after a short pause, so a few
 * quick clicks make one change (and one new route). Used for the bank stop and for boarding or leaving a boat, both
 * 0 to {@link TransportSettings#MAX_BANK_TICKS}. Swing thread only.
 */
final class TickSettingRow
{
	final TickStepper stepper;
	/** Sends the ticks shown once the pause after the last click is over. */
	private final Timer timer;
	/** Takes the ticks when they are sent (the tab saves them; the route is searched again). */
	private final IntConsumer onSend;
	private int shown;

	/**
	 * @param lessTip the "-" button's tooltip
	 * @param moreTip the "+" button's tooltip
	 * @param ticks   the ticks shown at first
	 * @param delayMs the pause after the last click before the ticks are sent
	 * @param onSend  takes the ticks when they are sent
	 */
	TickSettingRow(String lessTip, String moreTip, int ticks, int delayMs, IntConsumer onSend)
	{
		stepper = new TickStepper(lessTip, moreTip, this::click, this::type);
		shown = ticks;
		this.onSend = onSend;
		timer = new Timer(delayMs, e -> send());
		timer.setRepeats(false);
	}

	/** The row: the picture, the name and the total above - / +, each with {@code tip} on hover. */
	JPanel panel(IconKey picture, String name, String tip, IconCache icons)
	{
		stepper.setHint(tip);
		JPanel line = Ui.cardLine(4, 0);
		line.setToolTipText(tip);
		IconBox icon = new IconBox(picture, icons, true);
		icon.setToolTipText(tip);
		line.add(icon, BorderLayout.WEST);
		JLabel label = Ui.label(name, Ui.small(), Ui.TEXT);
		label.setToolTipText(tip);
		line.add(label, BorderLayout.CENTER);
		line.add(stepper, BorderLayout.EAST);
		showTicks();
		return line;
	}

	/** Shows saved ticks (account switch, a change made elsewhere); a click still waiting is dropped. */
	void show(int ticks)
	{
		timer.stop();
		shown = ticks;
		showTicks();
	}

	/** A - or + click: shown at once, sent after the pause. */
	void click(int by)
	{
		set(shown + by);
	}

	/** A typed total (already 0 to 10000): shown at once, sent after the pause. */
	void type(int ticks)
	{
		set(ticks);
	}

	private void set(int want)
	{
		int next = Math.max(0, Math.min(TransportSettings.MAX_BANK_TICKS, want));
		if (next == shown)
		{
			return;
		}
		shown = next;
		showTicks();
		timer.restart();
	}

	/** Sends the ticks shown now (the timer does this after the pause). */
	void send()
	{
		timer.stop();
		onSend.accept(shown);
	}

	private void showTicks()
	{
		stepper.show(TickStepper.ticks(shown), shown > 0, shown < TransportSettings.MAX_BANK_TICKS);
	}
}
