package com.bestpath.ui;

import com.bestpath.route.TransportFinder;
import com.bestpath.route.TransportRules;
import com.bestpath.route.TransportSettings;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The Settings tab's "Transport ticks", to find transports and change their ticks: a search box, then Show (all /
 * changed by me / not changed / never used), Type (the Settings types), Tag (the Avoid rules: costs something, uses
 * runes...) and Sort (recently changed, name, ticks, biggest change, nearest to me), then the transports {@value #PAGE}
 * at a time, each with its icon, its name, what kind it is and its total ticks above - / + (click the total to type
 * it), and "Reset" once changed. Only totals, never a breakdown.
 *
 * <p>Clicks show at once and are sent together {@value RouteTab#TICK_SEND_DELAY_MS} ms after the last one as one
 * settings change, like the step cards'. The list keeps its order while the player changes ticks in it; a change made
 * elsewhere (a step card), or a new search, filter or sort, orders it again. Swing thread only.
 */
final class TransportTickList extends JPanel
{
	static final int PAGE = 10;
	static final String LOADING = "Shows once the map data has loaded.";
	static final String SEARCH_HINT = "Search a name or place";
	static final String ANY_TYPE = "All types";
	static final String OTHER_TYPE = "In no type";
	static final String ANY_TAG = "Any";
	/** How long after the last key press the list is searched again. */
	static final int SEARCH_DELAY_MS = 250;

	private final IconCache icons;
	private final Supplier<TransportSettings> current;
	private final Consumer<TransportSettings> send;
	private final IntSupplier playerTile;
	final JTextField search = new JTextField();
	final JComboBox<TransportFinder.Show> show = new JComboBox<>(TransportFinder.Show.values());
	private final JComboBox<Object> type = new JComboBox<>();
	private final JComboBox<Object> tag = new JComboBox<>();
	final JComboBox<TransportFinder.Sort> sort = new JComboBox<>(TransportFinder.Sort.values());
	private final WrapLabel count = Ui.wrapped(" ", Ui.CONTENT_WIDTH - 10, Ui.small(), Ui.MUTED);
	private final JPanel rows = new JPanel();
	private final JButton more = Ui.button("Show " + PAGE + " more", "Show more of the list");
	private final Timer searchTimer = new Timer(SEARCH_DELAY_MS, e -> requery());
	private final Timer sendTimer = new Timer(RouteTab.TICK_SEND_DELAY_MS, e -> sendChanges());
	/** Clicked or typed and not sent yet: id -> the transport's new change, and the name it is saved with. */
	private final Map<String, Integer> waiting = new LinkedHashMap<>();
	private final Map<String, String> waitingNames = new LinkedHashMap<>();
	private TransportFinder finder = TransportFinder.EMPTY;
	private List<TransportFinder.Entry> found = List.of();
	private int shown = PAGE;
	/** True while this list's own change is on its way (the order is kept when it comes back). */
	private boolean ownChange;

	/**
	 * @param current    the newest settings (the clicks are added to them when sent)
	 * @param send       takes the settings with the clicks (saves them and searches the route again)
	 * @param playerTile where the player stands (packed tile, -1: not known), for "Nearest to me" and "tiles away"
	 */
	TransportTickList(IconCache icons, Supplier<TransportSettings> current, Consumer<TransportSettings> send, IntSupplier playerTile)
	{
		this.icons = icons;
		this.current = current;
		this.send = send;
		this.playerTile = playerTile;
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		searchTimer.setRepeats(false);
		sendTimer.setRepeats(false);

		search.setFont(Ui.small());
		search.setToolTipText(SEARCH_HINT);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}
		});
		add(line(Ui.label("Search", Ui.small(), Ui.TEXT), search));
		add(Box.createVerticalStrut(3));

		type.addItem(ANY_TYPE);
		for (TransportSettings.Category c : TransportSettings.Category.values())
		{
			type.addItem(c);
		}
		type.addItem(OTHER_TYPE);
		tag.addItem(ANY_TAG);
		for (TransportSettings.Avoid a : TransportSettings.Avoid.values())
		{
			if (TransportRules.isSupported(a))
			{
				tag.addItem(a);
			}
		}
		ListCellRenderer<Object> labels = labelRenderer();
		type.setRenderer(labels);
		tag.setRenderer(labels);
		combo(show, "Show", "Which transports to list");
		combo(type, "Type", "Only this type");
		combo(tag, "Tag", "Only transports an \"Avoid\" rule covers");
		combo(sort, "Sort", "Sort order");

		add(Box.createVerticalStrut(3));
		count.setAlignmentX(LEFT_ALIGNMENT);
		add(count);
		add(Box.createVerticalStrut(3));
		rows.setOpaque(false);
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setAlignmentX(LEFT_ALIGNMENT);
		add(rows);
		more.setAlignmentX(LEFT_ALIGNMENT);
		more.addActionListener(e ->
		{
			shown += PAGE;
			showRows();
		});
		add(more);
		requery();
	}

	/** A small label on the left, the control filling the rest of the line. */
	private static JPanel line(JComponent label, JComponent control)
	{
		JPanel p = new JPanel(new BorderLayout(4, 0));
		p.setOpaque(false);
		label.setPreferredSize(new Dimension(38, label.getPreferredSize().height));
		p.add(label, BorderLayout.WEST);
		p.add(control, BorderLayout.CENTER);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	private void combo(JComboBox<?> box, String name, String tip)
	{
		box.setFont(Ui.small());
		box.setToolTipText(tip);
		box.setFocusable(false);
		box.addActionListener(e -> requery());
		add(line(Ui.label(name, Ui.small(), Ui.TEXT), box));
		add(Box.createVerticalStrut(2));
	}

	/** Types and tags by their Settings labels. */
	private static ListCellRenderer<Object> labelRenderer()
	{
		DefaultListCellRenderer r = new DefaultListCellRenderer();
		return (list, value, index, selected, focus) -> r.getListCellRendererComponent(list, label(value), index, selected, focus);
	}

	static String label(Object v)
	{
		if (v instanceof TransportSettings.Category)
		{
			return ((TransportSettings.Category) v).getLabel();
		}
		if (v instanceof TransportSettings.Avoid)
		{
			return ((TransportSettings.Avoid) v).getLabel();
		}
		return String.valueOf(v);
	}

	/** The plugin data's transports (once the map data has loaded). */
	void setFinder(TransportFinder f)
	{
		finder = f == null ? TransportFinder.EMPTY : f;
		requery();
	}

	/** The settings changed (here or elsewhere): totals again; a change from elsewhere orders the list again. */
	void settingsChanged()
	{
		if (ownChange && waiting.isEmpty())
		{
			ownChange = false;
			showRows();
			return;
		}
		if (waiting.isEmpty())
		{
			requery();
		}
	}

	/** Searches, filters and sorts again from the top of the list. */
	void requery()
	{
		searchTimer.stop();
		Object t = type.getSelectedItem();
		Object g = tag.getSelectedItem();
		TransportFinder.Query q = new TransportFinder.Query(search.getText(), (TransportFinder.Show) show.getSelectedItem(),
			t instanceof TransportSettings.Category ? (TransportSettings.Category) t : null,
			g instanceof TransportSettings.Avoid ? (TransportSettings.Avoid) g : null,
			(TransportFinder.Sort) sort.getSelectedItem(), playerTile.getAsInt());
		List<TransportFinder.Entry> f = finder.find(q, current.get());
		if (OTHER_TYPE.equals(t))
		{
			List<TransportFinder.Entry> none = new ArrayList<>();
			for (TransportFinder.Entry e : f)
			{
				if (e.getCategory() == null)
				{
					none.add(e);
				}
			}
			f = none;
		}
		found = f;
		shown = PAGE;
		showRows();
	}

	private void showRows()
	{
		rows.removeAll();
		TransportSettings s = current.get();
		if (finder.getEntries().isEmpty() && found.isEmpty())
		{
			count.setText(LOADING);
		}
		else
		{
			int changed = s.getTickChanges().size();
			count.setText(found.size() + (found.size() == 1 ? " transport" : " transports")
				+ (changed == 0 ? "" : " (" + changed + " changed by you)"));
		}
		int me = playerTile.getAsInt();
		for (int i = 0; i < Math.min(shown, found.size()); i++)
		{
			rows.add(row(found.get(i), s, me));
			rows.add(Box.createVerticalStrut(3));
		}
		more.setVisible(found.size() > shown);
		revalidate();
		repaint();
	}

	/** The change shown for a transport: one clicked and not sent yet, else the settings'. */
	private int changeOf(TransportFinder.Entry e, TransportSettings s)
	{
		Integer w = waiting.get(e.getId());
		return w != null ? w : s.tickChange(e.getId());
	}

	private JPanel row(TransportFinder.Entry e, TransportSettings s, int me)
	{
		JPanel line = Ui.cardLine(4, 0);
		String tip = e.getBackTitle() == null ? e.getTitle() : e.getTitle() + " / " + e.getBackTitle();
		line.setToolTipText(tip);
		// the icon its step card has (the spell, the teleport item, the skill of a shortcut, up / down...),
		// else its type's
		IconKey card = e.getCard() == null ? null : IconChooser.main(e.getCard());
		IconKey icon = card != null ? card
			: IconChooser.category(e.getCategory() == null ? TransportSettings.Category.BOATS : e.getCategory());
		line.add(new IconBox(icon, icons, true), BorderLayout.WEST);

		int change = changeOf(e, s);
		int data = finder.ticks(e, s.withTickChange(e.getId(), 0, null, 0));
		int total = data < 0 ? -1 : Math.max(0, data + change);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		JComponent name = Ui.wrapped(tip, Ui.CONTENT_WIDTH - 95, Ui.small(), change != 0 ? Ui.CURRENT : Ui.TEXT);
		name.setAlignmentX(LEFT_ALIGNMENT);
		text.add(name);
		JComponent detail = Ui.wrapped(detail(e, me), Ui.CONTENT_WIDTH - 95, Ui.small(), Ui.MUTED);
		detail.setAlignmentX(LEFT_ALIGNMENT);
		text.add(detail);
		line.add(text, BorderLayout.CENTER);

		TickStepper stepper = null;
		if (total >= 0)
		{
			stepper = new TickStepper("1 tick less for this transport", "1 tick more for this transport",
				by -> setChange(e, Math.max(-data, changeOf(e, current.get()) + by)), typed -> setChange(e, typed - data));
			stepper.setHint("Ticks routes count for " + e.getTitle() + ".");
			stepper.show(TickStepper.ticks(total), total > 0, total < TickStepper.MAX);
			stepper.setName("ticks:" + e.getId());
		}
		// the - / + with Reset under them once changed
		JPanel right = new JPanel();
		right.setOpaque(false);
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		if (stepper != null)
		{
			stepper.setAlignmentX(CENTER_ALIGNMENT);
			right.add(stepper);
		}
		if (change != 0)
		{
			JButton reset = Ui.button("Reset", "Use this transport's usual ticks again");
			reset.setFont(Ui.small());
			reset.setAlignmentX(CENTER_ALIGNMENT);
			reset.addActionListener(ev -> setChange(e, 0));
			right.add(reset);
		}
		line.add(right, BorderLayout.EAST);
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		line.setName("transport:" + e.getId());
		return line;
	}

	/** "Teleport · spell", "Two-way · Shortcuts · 12 tiles away". */
	static String detail(TransportFinder.Entry e, int me)
	{
		StringBuilder sb = new StringBuilder(e.getKind());
		if (!e.getGroup().isEmpty())
		{
			sb.append(" · ").append(e.getGroup());
		}
		else if (e.getCategory() != null)
		{
			sb.append(" · ").append(e.getCategory().getLabel());
		}
		long d = TransportFinder.distance(e, me);
		if (e.getStart() != -1 && d < 100_000L)
		{
			sb.append(" · ").append(d == 1 ? "1 tile away" : d + " tiles away");
		}
		return sb.toString();
	}

	/** A click, a typed total or "Reset": shown at once, sent after the pause. */
	private void setChange(TransportFinder.Entry e, int change)
	{
		waiting.put(e.getId(), change);
		waitingNames.put(e.getId(), e.getTitle());
		sendTimer.restart();
		showRows();
	}

	/** Sends every waiting change as one settings change. */
	void sendChanges()
	{
		sendTimer.stop();
		if (waiting.isEmpty())
		{
			return;
		}
		TransportSettings before = current.get();
		TransportSettings s = before;
		for (Map.Entry<String, Integer> w : waiting.entrySet())
		{
			s = s.withTickChange(w.getKey(), w.getValue(), waitingNames.get(w.getKey()));
		}
		waiting.clear();
		waitingNames.clear();
		if (s.equals(before))
		{
			// the clicks cancelled out: nothing is sent, so no change of this list's own will come back
			showRows();
			return;
		}
		ownChange = true;
		send.accept(s);
	}

	// tests
}
