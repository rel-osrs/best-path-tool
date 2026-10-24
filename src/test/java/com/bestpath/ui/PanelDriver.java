package com.bestpath.ui;

import com.bestpath.route.TransportFinder;
import com.bestpath.route.TransportSettings;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/**
 * Tests and the UI previews: does to the sidebar what the player's clicks would (open a tab, unfold a section,
 * scroll, search, type), and reads what a part of it shows, so a test or a picture can reach any state.
 */
public final class PanelDriver
{
	private PanelDriver()
	{
	}

	/** The settings the Settings tab shows. */
	public static TransportSettings settings(BestPathPanel p)
	{
		return p.settingsTab.getSettings();
	}

	/**
	 * Sends the waiting - / + clicks (step cards, the Settings tab's teleport category and network rows) now instead
	 * of after their short pause (the plugin waits for the pause, so quick clicks make one save and one re-route).
	 */
	public static void sendWaitingChanges(BestPathPanel p)
	{
		p.routeTab.sendTickChanges();
		p.settingsTab.tickGroups.sendChanges();
	}

	/** Back to the Functions tab's function select screen. */
	public static void showFunctionMenu(BestPathPanel p)
	{
		p.functionsTab.showMenu();
	}

	public static void selectSettingsTab(BestPathPanel p)
	{
		p.tabs.select(p.settingsButton);
	}

	/** Unfolds the Settings tab's "Your answers". */
	public static void unfoldAnswers(BestPathPanel p)
	{
		if (!p.settingsTab.answers.body.isVisible())
		{
			p.settingsTab.answers.toggle();
		}
	}

	/** Folds or unfolds every Settings tab section (they start folded). */
	public static void setSettingsSectionsOpen(BestPathPanel p, boolean open)
	{
		p.settingsTab.setSectionsOpen(open);
	}

	/** Unfolds the Settings tab's "Advanced" section. */
	public static void unfoldAdvanced(BestPathPanel p)
	{
		ConfigMirror m = p.settingsTab.mirror;
		if (!m.isOpen(ConfigList.ADVANCED))
		{
			m.toggle(ConfigList.ADVANCED);
		}
	}

	/** Scrolls the Settings tab to "Teleport and travel ticks" (lay the panel out first). */
	public static void scrollSettingsToTickGroups(BestPathPanel p)
	{
		scrollTo(p.settingsTab.tickGroups, 64); // the title and hint above
	}

	/** Scrolls the Settings tab to "House (POH)" (lay the panel out first). */
	public static void scrollSettingsToHouse(BestPathPanel p)
	{
		scrollTo(p.settingsTab.houseList, 60); // the title and its two-line hint above
	}

	/** Scrolls the Settings tab to "Transport ticks" (lay the panel out first). */
	public static void scrollSettingsToTransportTicks(BestPathPanel p)
	{
		scrollTo(p.settingsTab.transportTicks, 90); // the title and hint above
	}

	/** Searches, filters and sorts "Transport ticks" as the player would, then scrolls there. */
	public static void previewTransportTicks(BestPathPanel p, String search, TransportFinder.Show show,
		TransportFinder.Sort sort)
	{
		TransportTickList list = p.settingsTab.transportTicks;
		list.search.setText(search);
		list.show.setSelectedItem(show);
		list.sort.setSelectedItem(sort);
		list.requery();
		scrollSettingsToTransportTicks(p);
	}

	/** The destination box. */
	public static JTextField destinationField(BestPathPanel p)
	{
		return p.routeTab.destination;
	}

	/** Scrolls the view holding {@code c} so its top, less {@code above} pixels, is at the top. */
	private static void scrollTo(Component c, int above)
	{
		JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, c);
		if (vp == null || vp.getView() == null)
		{
			return;
		}
		int y = SwingUtilities.convertPoint(c, 0, 0, vp.getView()).y - above;
		int max = Math.max(0, vp.getView().getHeight() - vp.getHeight());
		vp.setViewPosition(new Point(0, Math.max(0, Math.min(max, y))));
	}

	/** Each need chip's mouse-over text, in order. */
	static List<String> tooltips(NeedsRow row)
	{
		List<String> out = new ArrayList<>();
		for (NeedsRow.Chip c : row.chips)
		{
			out.add(c.tooltip);
		}
		return out;
	}

	/** Each need chip's place in the row, in order. */
	static List<Rectangle> chipBounds(NeedsRow row)
	{
		List<Rectangle> out = new ArrayList<>();
		for (NeedsRow.Chip c : row.chips)
		{
			out.add(new Rectangle(c.bounds));
		}
		return out;
	}

	/** The answers a step card's questions ask about, in order. */
	static List<String> answers(StepCard.QuestionBox box)
	{
		return new ArrayList<>(box.buttons.keySet());
	}

	/** A question's Yes (or No) button, or null. */
	static JButton button(StepCard.QuestionBox box, String answer, boolean yes)
	{
		JButton[] b = box.buttons.get(answer);
		return b == null ? null : b[yes ? 0 : 1];
	}

	/** The line under a question saying what was answered, or null. */
	static String stateText(StepCard.QuestionBox box, String answer)
	{
		WrapLabel l = box.states.get(answer);
		return l == null ? null : l.getText();
	}

	/** A teleport category's or network's reset button, or null. */
	static JButton resetButton(TickGroupList list, String key)
	{
		TickGroupList.Row r = list.rows.get(key);
		return r == null ? null : r.reset;
	}

	/** A teleport category's or network's total ("4 ticks"), or null. */
	static String totalText(TickGroupList list, String key)
	{
		TickGroupList.Row r = list.rows.get(key);
		return r == null ? null : r.stepper.total.getText();
	}

	/** A teleport category's or network's - / + control, or null. */
	static TickStepper stepper(TickGroupList list, String key)
	{
		TickGroupList.Row r = list.rows.get(key);
		return r == null ? null : r.stepper;
	}

	/** Types a number into the ticks box, as the player clicking the total and pressing Enter. */
	static void type(TickStepper s, String text)
	{
		s.startTyping();
		s.field.setText(text);
		s.finishTyping(true);
	}

	/** A step card's total ticks ("12 ticks"), or null when it has no - / + control. */
	static String ticksText(StepCard card)
	{
		return card.stepper == null ? null : card.stepper.total.getText();
	}

	/** A step card's - button, or null when it has no - / + control. */
	static JButton minusButton(StepCard card)
	{
		return card.stepper == null ? null : card.stepper.minus;
	}
}
