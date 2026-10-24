package com.bestpath.ui;

import com.bestpath.core.data.TickGroups;
import com.bestpath.route.TransportSettings;
import java.awt.BorderLayout;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The Settings tab's "Teleport and travel ticks": every teleport category, the extra ticks for paid transports, then
 * every network, each with its icon (the Settings type row's), its total ticks and - / + right under them. Only totals
 * are shown (a network: its rides' lowest to highest ticks), never a breakdown.
 *
 * <p>A click changes the whole group by 1 tick for this player at once on screen; quick clicks are sent together
 * {@value RouteTab#TICK_SEND_DELAY_MS} ms after the last one as one settings change (one save, one quiet re-route),
 * like the step cards' - / +. Swing thread only.
 */
final class TickGroupList extends JPanel
{
	static final String LOADING = "Shows once the map data has loaded.";
	/** The width the totals and - / + get, so every row's name wraps at the same place. */
	private static final String WIDEST = "888-888 ticks";

	/** One row: a teleport category or network, its key in the settings and its - / +. */
	static final class Row
	{
		final TickGroups.Group group;
		final String key;
		final TickStepper stepper;
		/** "Reset" under the - / +, shown while the group is changed. */
		final JButton reset;

		Row(TickGroups.Group group, String key, TickStepper stepper, JButton reset)
		{
			this.group = group;
			this.key = key;
			this.stepper = stepper;
			this.reset = reset;
		}
	}

	private final IconCache icons;
	private final Supplier<TransportSettings> current;
	private final Consumer<TransportSettings> send;
	final Map<String, Row> rows = new LinkedHashMap<>();
	/** Clicked and not sent yet: key -> the group's new change. */
	final Map<String, Integer> waiting = new LinkedHashMap<>();
	private final Timer timer = new Timer(RouteTab.TICK_SEND_DELAY_MS, e -> sendChanges());
	private TickGroups groups = TickGroups.EMPTY;

	/**
	 * @param current the newest settings (the clicks are added to them when sent)
	 * @param send    takes the settings with the clicks (saves them and searches again)
	 */
	TickGroupList(IconCache icons, Supplier<TransportSettings> current, Consumer<TransportSettings> send)
	{
		this.icons = icons;
		this.current = current;
		this.send = send;
		timer.setRepeats(false);
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		rebuild();
	}

	/** The map data's teleport categories and networks (once it has loaded). */
	void setGroups(TickGroups g)
	{
		groups = g == null ? TickGroups.EMPTY : g;
		rebuild();
	}

	/** Shows these settings' changes (saved, or account switch); clicks not sent yet stay shown. */
	void show(TransportSettings s)
	{
		for (Row r : rows.values())
		{
			showRow(r, s);
		}
	}

	/** The key a group's change is saved under. */
	static String key(TickGroups.Group g)
	{
		return g.getKind() == TickGroups.Kind.TELEPORT_CATEGORY ? TransportSettings.teleportCategoryKey(g.getName())
			: g.getKind() == TickGroups.Kind.COINS ? TransportSettings.COINS_KEY : TransportSettings.networkKey(g.getName());
	}

	/** "Spell", "Jewellery (infinite)", "Fairy ring": the name with a capital first letter. */
	static String label(TickGroups.Group g)
	{
		String n = g.getName();
		return n.isEmpty() ? n : n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
	}

	/** The group's total ticks with this change: "4 ticks", a network "5-20 ticks". */
	static String total(TickGroups.Group g, int change)
	{
		return TickStepper.ticks(g.lowest(change), g.highest(change));
	}

	private void rebuild()
	{
		removeAll();
		rows.clear();
		if (groups.getGroups().isEmpty())
		{
			add(Ui.left(Ui.wrapped(LOADING, Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.MUTED)));
		}
		boolean ridesHeading = false;
		boolean costHeading = false;
		TransportSettings s = current.get();
		for (TickGroups.Group g : groups.getGroups())
		{
			if (g.getKind() == TickGroups.Kind.COINS && !costHeading)
			{
				costHeading = true;
				add(Box.createVerticalStrut(4));
				add(Ui.left(Ui.label("Paid (every transport that costs coins)", Ui.small(), Ui.MUTED)));
				add(Box.createVerticalStrut(3));
			}
			if (g.getKind() == TickGroups.Kind.NETWORK && !ridesHeading)
			{
				ridesHeading = true;
				add(Box.createVerticalStrut(4));
				add(Ui.left(Ui.label("Rides (each network)", Ui.small(), Ui.MUTED)));
				add(Box.createVerticalStrut(3));
			}
			String key = key(g);
			JButton reset = Ui.button("Reset", "Use their usual ticks again");
			reset.setFont(Ui.small());
			reset.addActionListener(e -> resetRow(key));
			Row row = new Row(g, key, new TickStepper("1 tick less for all",
				"1 tick more for all", by -> click(key, by), n -> typed(key, n)), reset);
			rows.put(row.key, row);
			add(Ui.left(rowPanel(row)));
			add(Box.createVerticalStrut(3));
			showRow(row, s);
		}
		revalidate();
		repaint();
	}

	private JPanel rowPanel(Row r)
	{
		String hint = hint(r.group);
		r.stepper.setHint(hint);
		JPanel line = Ui.cardLine(4, 0);
		line.setToolTipText(hint);
		IconBox icon = new IconBox(IconChooser.tickGroup(r.group), icons, true);
		icon.setToolTipText(hint);
		line.add(centred(icon, GridBagConstraints.CENTER), BorderLayout.WEST);
		FontMetrics fm = getFontMetrics(Ui.small());
		// the - / + with Reset under them while the type is changed
		JPanel stack = new JPanel();
		stack.setOpaque(false);
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		r.stepper.setAlignmentX(CENTER_ALIGNMENT);
		r.reset.setAlignmentX(CENTER_ALIGNMENT);
		stack.add(r.stepper);
		stack.add(r.reset);
		JPanel right = centred(stack, GridBagConstraints.CENTER);
		int rightW = Math.max(fm.stringWidth(WIDEST), Math.max(r.stepper.getPreferredSize().width, r.reset.getPreferredSize().width));
		line.add(right, BorderLayout.EAST);
		int textW = Ui.CONTENT_WIDTH - 5 - 3 - IconBox.W - rightW - 2 * 4;
		WrapLabel name = Ui.wrapped(label(r.group), textW, Ui.small(), Ui.TEXT);
		name.setToolTipText(hint);
		line.add(centred(name, GridBagConstraints.WEST), BorderLayout.CENTER);
		return line;
	}

	/** What a row covers (its mouse-over). */
	static String hint(TickGroups.Group g)
	{
		if (g.getKind() == TickGroups.Kind.TELEPORT_CATEGORY)
		{
			return "Ticks for every \"" + g.getName() + "\" teleport (" + g.getCount() + ")";
		}
		if (g.getKind() == TickGroups.Kind.COINS)
		{
			return "Extra ticks for every transport that costs coins (" + g.getCount() + ")";
		}
		return "Ticks for each " + g.getName() + " ride";
	}

	private static JPanel centred(JComponent c, int anchor)
	{
		JPanel p = new JPanel(new GridBagLayout());
		p.setOpaque(false);
		GridBagConstraints k = new GridBagConstraints();
		k.anchor = anchor;
		k.weightx = 1;
		p.add(c, k);
		return p;
	}

	/** The change shown for a group: a click not sent yet, else the saved one. */
	private int change(String key, TransportSettings s)
	{
		Integer w = waiting.get(key);
		return w != null ? w : s.groupTickChange(key);
	}

	private void showRow(Row r, TransportSettings s)
	{
		int c = Math.max(r.group.lowestChange(), change(r.key, s));
		r.stepper.show(total(r.group, c), r.group.highest(c) > 0, r.group.highest(c) < TickStepper.MAX);
		if (r.reset.isVisible() != (c != 0))
		{
			r.reset.setVisible(c != 0);
			revalidate();
			repaint();
		}
	}

	/** "Reset": the type's usual ticks again (shown at once, sent after the pause like a click). */
	private void resetRow(String key)
	{
		Row r = rows.get(key);
		if (r == null)
		{
			return;
		}
		TransportSettings s = current.get();
		setChange(r, s, Math.max(r.group.lowestChange(), change(key, s)), 0);
	}

	/** A - or + click: shown at once (no member goes below 0 ticks), sent after the pause. */
	private void click(String key, int by)
	{
		Row r = rows.get(key);
		if (r == null)
		{
			return;
		}
		TransportSettings s = current.get();
		int now = Math.max(r.group.lowestChange(), change(key, s));
		setChange(r, s, now, now + by);
	}

	/**
	 * A typed total (already 0 to {@value TickStepper#MAX}): the whole type takes it (a network: its quickest ride; the
	 * others keep their difference), shown at once, sent after the pause.
	 */
	void typed(String key, int total)
	{
		Row r = rows.get(key);
		if (r == null)
		{
			return;
		}
		TransportSettings s = current.get();
		int now = Math.max(r.group.lowestChange(), change(key, s));
		setChange(r, s, now, total - r.group.lowest(0));
	}

	/** Queues a group's change, clamped so no member goes below 0 ticks or above {@value TickStepper#MAX}. */
	private void setChange(Row r, TransportSettings s, int now, int want)
	{
		int next = Math.max(r.group.lowestChange(), Math.min(TickStepper.MAX - r.group.highest(0), want));
		if (next == now)
		{
			return;
		}
		waiting.put(r.key, next);
		showRow(r, s);
		timer.restart();
	}

	/**
	 * Sends the waiting clicks now (the timer does this {@value RouteTab#TICK_SEND_DELAY_MS} ms after the last click):
	 * one settings change, added to the newest settings.
	 */
	void sendChanges()
	{
		timer.stop();
		if (waiting.isEmpty())
		{
			return;
		}
		TransportSettings before = current.get();
		TransportSettings s = before;
		for (Map.Entry<String, Integer> e : waiting.entrySet())
		{
			s = s.withGroupTickChange(e.getKey(), e.getValue());
		}
		waiting.clear();
		if (!s.equals(before))
		{
			send.accept(s);
		}
	}
}
