package com.bestpath.ui;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.route.ChartingProgress;
import com.bestpath.route.RouteRequest;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

/**
 * The Functions tab's "Sea charting" page. At the top three routes: every sea and bonus set the player can complete
 * now, every ocean likewise, and everything (only when every task left is doable). Then an ocean picked from the list,
 * what's left in it and what it gives, "Chart this ocean", and its seas, each with its own Go; a sea opens to its
 * tasks, which have no Go of their own. Every route goes to the quickest task the player can do now and on to the
 * next as each is done. Swing thread only.
 */
final class ChartingSection extends JPanel
{
	static final String NO_DATA = "No sea charting tasks in this version's map data.";
	static final String LOGGED_OUT = "Log in to see your charting tasks.";
	static final String NOTHING_TO_DO = "Nothing here you can do now.";
	/** How {@link ChartingProgress#EVERY_SEA} shows in the list. */
	static final String ALL_OCEANS = "All oceans";
	/**
	 * The list's choice that shows every sea and bonus set at once, under their oceans' names. It charts the same tasks
	 * as All oceans.
	 */
	static final String EVERYTHING = "Everything";

	private final PanelActions actions;
	private final Runnable showRoute;
	final WrapLabel status = new WrapLabel("", Ui.CONTENT_WIDTH, Ui.small(), Ui.TEXT);
	final JComboBox<String> setChoice = new JComboBox<>();
	final WrapLabel rewards = new WrapLabel("", Ui.CONTENT_WIDTH, Ui.small(), Ui.NOTE);
	/** The drinks the player holds; drinking one finishes its task. */
	final WrapLabel drinks = new WrapLabel("", Ui.CONTENT_WIDTH, Ui.small(), Ui.MUTED);
	final JCheckBox onlyCanDo = Ui.checkBox("Only what I can do now");
	final JButton chart = Ui.button("Chart this ocean",
		"Route to this ocean's tasks, quickest first");
	final JButton fullSeas = Ui.button("Every sea I can complete", "");
	final JButton fullOceans = Ui.button("Every ocean I can complete", "");
	final JButton everything = Ui.button("Everything", "");
	static final String CANT_COMPLETE = "some need a higher level";
	final JCheckBox hideDone = Ui.checkBox("Hide completed");
	final JPanel list = new JPanel();
	/** The sea and bonus sets the player opened in a list of several (kept while the progress changes). */
	private final Set<String> open = new HashSet<>();
	private ChartingProgress progress = ChartingProgress.NONE;
	private boolean filling;

	ChartingSection(PanelActions actions, Runnable showRoute)
	{
		this.actions = actions;
		this.showRoute = showRoute;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		Ui.addRow(this, status);
		Ui.addRow(this, drinks);
		Ui.addRow(this, Box.createVerticalStrut(4));
		fullSeas.addActionListener(e -> go(progress.route(ChartingProgress.FULL_SEAS)));
		fullOceans.addActionListener(e -> go(progress.route(ChartingProgress.FULL_OCEANS)));
		everything.addActionListener(e -> go(progress.route(ChartingProgress.EVERY_SEA)));
		Ui.addRow(this, fullSeas);
		Ui.addRow(this, Box.createVerticalStrut(3));
		Ui.addRow(this, fullOceans);
		Ui.addRow(this, Box.createVerticalStrut(3));
		Ui.addRow(this, everything);
		Ui.addRow(this, Box.createVerticalStrut(10));
		setChoice.setFont(Ui.small());
		setChoice.setPrototypeDisplayValue("Miscellaneous (bonus sets)"); // never wider than the sidebar
		setChoice.setToolTipText("Which seas to list");
		setChoice.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus)
			{
				super.getListCellRendererComponent(l, value == null ? "" : label(String.valueOf(value)), index, selected, focus);
				setFont(Ui.small());
				return this;
			}
		});
		setChoice.addActionListener(e ->
		{
			if (!filling)
			{
				showSet();
			}
		});
		Ui.addRow(this, setChoice);
		Ui.addRow(this, Box.createVerticalStrut(3));
		chart.addActionListener(e -> go(progress.route(plan())));
		Ui.addRow(this, chart);
		Ui.addRow(this, Box.createVerticalStrut(3));
		Ui.addRow(this, rewards);
		Ui.addRow(this, Box.createVerticalStrut(3));
		onlyCanDo.setToolTipText("Only what you can finish now");
		hideDone.setToolTipText("Hide what you have finished");
		for (JCheckBox box : new JCheckBox[]{onlyCanDo, hideDone})
		{
			box.addActionListener(e -> showSet());
			Ui.addRow(this, box);
		}
		Ui.addRow(this, Box.createVerticalStrut(6));
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		Ui.addRow(this, list);
		show(ChartingProgress.NONE);
	}

	/** The progress line for the Functions tab's card ("Charted 1 of 358 tasks. Sailing level 10."). */
	String summary()
	{
		return statusText(progress);
	}

	void show(ChartingProgress p)
	{
		boolean newTasks = p.getTasks() != progress.getTasks();
		progress = p;
		if (newTasks)
		{
			fillChoices();
		}
		status.setText(statusText(p));
		drinks.setText(drinksText(p));
		drinks.setVisible(!drinks.getText().isEmpty());
		boolean any = !p.getTasks().isEmpty();
		setChoice.setEnabled(any);
		onlyCanDo.setEnabled(p.isLoggedIn());
		// no tasks in the map data (or not loaded yet): only the line saying so, not an empty set list
		setChoice.setVisible(any);
		hideDone.setVisible(any);
		onlyCanDo.setVisible(any);
		chart.setVisible(any);
		fullSeas.setVisible(any);
		fullOceans.setVisible(any);
		everything.setVisible(any);
		int seas = p.completable(false).size();
		int oceans = p.completable(true).size();
		bigButton(fullSeas, "Chart every sea I can complete (" + seas + ")", seas > 0 && p.route(ChartingProgress.FULL_SEAS) != null,
			ChartingProgress.FULL_SEAS,
			p.isLoggedIn() ? "No sea or bonus set you can complete now: " + CANT_COMPLETE + "." : LOGGED_OUT);
		bigButton(fullOceans, "Chart every ocean I can complete (" + oceans + ")",
			oceans > 0 && p.route(ChartingProgress.FULL_OCEANS) != null, ChartingProgress.FULL_OCEANS,
			p.isLoggedIn() ? "No ocean you can complete now: " + CANT_COMPLETE + "." : LOGGED_OUT);
		boolean all = p.canComplete(ChartingProgress.EVERY_SEA) && p.route(ChartingProgress.EVERY_SEA) != null;
		bigButton(everything, "Chart everything", all, ChartingProgress.EVERY_SEA,
			p.isLoggedIn() ? p.doneCount() == p.total() ? "Every task is done." : "Not yet: " + CANT_COMPLETE + "." : LOGGED_OUT);
		showSet();
	}

	/** One of the three big routes: its words, on or off, and what it covers (or why it is off) as its tooltip. */
	private void bigButton(JButton b, String text, boolean on, String plan, String whyOff)
	{
		b.setText(text);
		b.setEnabled(on);
		b.setToolTipText(on ? progress.planText(plan) : whyOff);
	}

	private void fillChoices()
	{
		Object was = setChoice.getSelectedItem();
		filling = true;
		DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
		if (!progress.getTasks().isEmpty())
		{
			m.addElement(ChartingProgress.EVERY_SEA); // shown as "All oceans"
			m.addElement(EVERYTHING);
		}
		for (ChartingTasks.Set s : progress.getTasks().getSets())
		{
			// only oceans in the list; their seas are rows under it
			if (ChartingTasks.OCEAN.equals(s.getType()) && !progress.tasksIn(s.getName()).isEmpty())
			{
				m.addElement(s.getName());
			}
		}
		setChoice.setModel(m);
		if (was != null && m.getIndexOf(was) >= 0)
		{
			setChoice.setSelectedItem(was);
		}
		filling = false;
	}

	/** How an ocean shows in the list: "Ardent Ocean"; one holding only bonus sets: "Miscellaneous (bonus sets)". */
	String label(String ocean)
	{
		if (ChartingProgress.EVERY_SEA.equals(ocean))
		{
			return ALL_OCEANS;
		}
		if (EVERYTHING.equals(ocean))
		{
			return EVERYTHING;
		}
		boolean onlyBonus = false;
		for (ChartingTasks.Set s : progress.getTasks().getSets())
		{
			if (ocean.equals(s.getOcean()))
			{
				if (!ChartingTasks.BONUS.equals(s.getType()))
				{
					return ocean;
				}
				onlyBonus = true;
			}
		}
		return onlyBonus ? ocean + " (bonus sets)" : ocean;
	}

	/**
	 * "Drinks to drink when you like: Smuggled rum, Marrow wine (in your bank)."; empty when none is held. Only said
	 * here and on the task rows: routes skip these tasks, so the player can drink them all at once later.
	 */
	static String drinksText(ChartingProgress p)
	{
		List<String> names = new ArrayList<>();
		for (ChartingTasks.Task t : p.drinksToDrink())
		{
			names.add(t.getDrinkName() + (p.drinkInBank(t) ? " (in your bank)" : ""));
		}
		return names.isEmpty() ? "" : (names.size() == 1 ? "Drink when you like: " : "Drinks to drink when you like: ")
			+ String.join(", ", names) + ".";
	}

	static String statusText(ChartingProgress p)
	{
		if (p.getTasks().isEmpty())
		{
			return NO_DATA;
		}
		if (!p.isLoggedIn())
		{
			return LOGGED_OUT;
		}
		return String.format(Locale.ROOT, "Charted %d of %d tasks. Sailing level %d.", p.doneCount(), p.total(),
			p.getSailingLevel());
	}

	String selected()
	{
		Object o = setChoice.getSelectedItem();
		return o == null ? ChartingProgress.EVERY_SEA : String.valueOf(o);
	}

	/** The tasks the chosen line stands for: {@link #EVERYTHING} is every task, as All oceans. */
	private String plan()
	{
		String s = selected();
		return EVERYTHING.equals(s) ? ChartingProgress.EVERY_SEA : s;
	}

	/** The chosen set's rewards, button and tasks. */
	private void showSet()
	{
		list.removeAll();
		if (progress.getTasks().isEmpty())
		{
			rewards.setText("");
			chart.setEnabled(false);
			refresh();
			return;
		}
		String choice = selected();
		String set = plan();
		int[] c = progress.counts(set);
		rewards.setText(String.format(Locale.ROOT, "%d of %d done. %s", c[0], c[1], progress.rewards(set)));
		RouteRequest r = progress.isLoggedIn() ? progress.route(set) : null;
		chart.setText(ChartingProgress.EVERY_SEA.equals(set) ? "Chart all oceans" : "Chart this ocean");
		boolean everything = EVERYTHING.equals(choice);
		chart.setEnabled(r != null);
		if (progress.isLoggedIn() && r == null && c[0] < c[1])
		{
			Ui.addRow(list, Ui.wrapped(NOTHING_TO_DO, Ui.CONTENT_WIDTH, Ui.small(), Ui.MUTED));
			Ui.addRow(list, Box.createVerticalStrut(4));
		}
		boolean filter = onlyCanDo.isSelected() && progress.isLoggedIn();
		boolean hide = hideDone.isSelected();
		boolean all = ChartingProgress.EVERY_SEA.equals(choice);
		Map<String, List<ChartingTasks.Task>> bySet = new LinkedHashMap<>();
		for (ChartingTasks.Task t : progress.tasksIn(set))
		{
			// all oceans: a row per ocean (a click picks it); otherwise a row per sea and bonus set
			String group = all ? progress.getTasks().oceanOf(t) : t.getSet();
			// "Only what I can do now": only the rows the player can complete now, with their tasks left
			boolean shown = filter ? progress.canComplete(group) && !progress.isDone(t) : !hide || !progress.isDone(t);
			if (shown)
			{
				bySet.computeIfAbsent(group, k -> new ArrayList<>()).add(t);
			}
		}
		Map<String, List<ChartingTasks.Task>> ordered = new LinkedHashMap<>();
		for (ChartingTasks.Set s : progress.getTasks().getSets())
		{
			if (bySet.containsKey(s.getName()))
			{
				ordered.put(s.getName(), bySet.remove(s.getName()));
			}
		}
		ordered.putAll(bySet);
		bySet = ordered;
		if (bySet.size() == 1 && Objects.equals(bySet.keySet().iterator().next(), set))
		{
			taskList(list, bySet.get(set), 0);
		}
		else
		{
			String ocean = null;
			for (Map.Entry<String, List<ChartingTasks.Task>> e : bySet.entrySet())
			{
				ChartingTasks.Set info = progress.getTasks().set(e.getKey());
				String o = info == null ? "" : info.getOcean();
				if (everything && !o.equals(ocean))
				{
					// everything: each ocean's name over its seas
					if (ocean != null)
					{
						Ui.addRow(list, Box.createVerticalStrut(4));
					}
					Ui.addRow(list, Ui.label(label(o), Ui.bold(), Ui.MUTED));
					Ui.addRow(list, Box.createVerticalStrut(2));
					ocean = o;
				}
				setRow(e.getKey(), e.getValue());
			}
		}
		refresh();
	}

	/** A sea or bonus set inside a bigger set: its name and count; a click opens it to its tasks. */
	private void setRow(String name, List<ChartingTasks.Task> shown)
	{
		boolean isOpen = open.contains(name);
		int[] c = progress.counts(name);
		boolean complete = c[0] == c[1];
		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setOpaque(false);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		ChartingTasks.Set info = progress.getTasks().set(name);
		boolean ocean = info != null && ChartingTasks.OCEAN.equals(info.getType());
		header.setToolTipText((ocean ? "Click to show its seas" : "Click to show or hide its tasks") + (info == null ? ""
			: String.format(Locale.ROOT, ". Completing it: %,d XP", info.getXp())));
		// red: tasks left that can't all be done now
		boolean red = !complete && progress.isLoggedIn() && !progress.canComplete(name);
		JLabel title = Ui.label(name + (info != null && ChartingTasks.BONUS.equals(info.getType()) ? " (bonus)" : ""),
			Ui.small(), complete ? Ui.GOOD : red ? Ui.BAD : Ui.TITLE);
		title.setIcon(FoldArrow.of(isOpen && !ocean));
		title.setIconTextGap(5);
		header.add(title, BorderLayout.CENTER);
		// each sea or ocean row has its own Go; single tasks have none
		JPanel east = new JPanel(new BorderLayout(4, 0));
		east.setOpaque(false);
		east.add(Ui.label(c[0] + " / " + c[1], Ui.small(), complete ? Ui.GOOD : Ui.TEXT), BorderLayout.CENTER);
		RouteRequest r = progress.isLoggedIn() ? progress.route(name) : null;
		JButton go = Ui.button("Go", r == null ? complete ? "Every task done" : !progress.isLoggedIn() ? LOGGED_OUT
			: progress.canComplete(name) ? "Only drinks left: drink the ones you have" : "Nothing here you can do now"
			: "Chart " + name + (info == null ? "" : String.format(Locale.ROOT, " (%,d XP when complete)", info.getXp())));
		go.setEnabled(r != null);
		go.addActionListener(e -> go(r));
		east.add(go, BorderLayout.EAST);
		header.add(east, BorderLayout.EAST);
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (ocean)
				{
					setChoice.setSelectedItem(name); // all oceans: the ocean's own list
				}
				else
				{
					toggle(name);
				}
			}
		});
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
		Ui.addRow(list, header);
		Ui.addRow(list, Box.createVerticalStrut(3));
		if (isOpen && !ocean)
		{
			JPanel tasks = new JPanel();
			tasks.setLayout(new BoxLayout(tasks, BoxLayout.Y_AXIS));
			tasks.setOpaque(false);
			tasks.setBorder(Ui.padding(0, FoldArrow.SIZE + 5, 4, 0));
			taskList(tasks, shown, FoldArrow.SIZE + 5);
			Ui.addRow(list, tasks);
		}
	}

	void toggle(String name)
	{
		if (!open.remove(name))
		{
			open.add(name);
		}
		showSet();
	}

	private void taskList(JPanel into, List<ChartingTasks.Task> tasks, int indent)
	{
		for (ChartingTasks.Task t : tasks)
		{
			Ui.addRow(into, taskRow(t, indent));
			Ui.addRow(into, Box.createVerticalStrut(3));
		}
	}

	private JComponent taskRow(ChartingTasks.Task t, int indent)
	{
		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		boolean done = progress.isDone(t);
		int width = Ui.CONTENT_WIDTH - indent - 34;
		boolean drink = progress.holdsDrink(t);
		line.add(Ui.wrapped(kindLabel(t.getKind()) + ": " + t.getText() + details(t), width, Ui.small(),
			done ? Ui.GOOD : drink ? Ui.NOTE : progress.canDo(t) || !progress.isLoggedIn() ? Ui.TEXT : Ui.BAD), BorderLayout.CENTER);
		if (done)
		{
			line.add(Ui.label("Done", Ui.small(), Ui.GOOD), BorderLayout.EAST);
		}
		else if (drink)
		{
			// a drink the player holds (bank or inventory) is not routed to: drinking it finishes the task
			JLabel drinkIt = Ui.label("Drink it", Ui.small(), Ui.NOTE);
			drinkIt.setToolTipText("You have the " + t.getDrinkName() + (progress.drinkInBank(t) ? " in your bank" : "")
				+ ": drink it to finish this task");
			line.add(drinkIt, BorderLayout.EAST);
		}
		else if (!t.getTip().isEmpty())
		{
			line.setToolTipText(t.getTip()); // no Go per task: the sea's Go charts it
		}
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		return line;
	}

	/** " (35 XP)", " (35 XP, Sailing 45, Icy seas)": the reward, then what stands in the way. */
	private String details(ChartingTasks.Task t)
	{
		StringBuilder sb = new StringBuilder(" (").append(String.format(Locale.ROOT, "%,d XP", t.getXp()));
		if (progress.isLoggedIn() && !progress.isDone(t) && !progress.levelAllows(t))
		{
			sb.append(", Sailing ").append(t.getLevel());
		}
		if (progress.isLoggedIn() && !progress.isDone(t) && !progress.questAllows(t))
		{
			sb.append(", needs the quest Current Affairs");
		}
		if (!t.getTool().isEmpty())
		{
			sb.append(", ").append(t.getTool()); // tools are assumed owned
		}
		if (!t.getHazard().isEmpty())
		{
			sb.append(", ").append(t.getHazard());
			if (progress.isLoggedIn() && !progress.isDone(t) && !progress.boatCanEnter(t))
			{
				sb.append(": your boat needs ").append(ChartingProgress.hazardNeeds(t.getHazard()));
			}
		}
		return sb.append(')').toString();
	}

	static String kindLabel(String kind)
	{
		switch (kind)
		{
			case "spyglass":
				return "Spyglass";
			case "current":
				return "Current duck";
			case "crate":
				return "Drink crate";
			case "weather":
				return "Weather";
			case "mermaid":
				return "Mermaid";
			default:
				return "Find";
		}
	}

	private void go(RouteRequest r)
	{
		if (r != null)
		{
			actions.findRoute(r);
			showRoute.run();
		}
	}

	private void refresh()
	{
		revalidate();
		repaint();
	}
}
