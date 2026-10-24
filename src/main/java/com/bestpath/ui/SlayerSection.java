package com.bestpath.ui;

import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.route.NeedStatus;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.SlayerTask;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The Functions tab's "Slayer task" page, for every Slayer master's tasks. At the top the task in its own card (the
 * picture RuneLite's Slayer plugin shows for it, how many are left, the master and a Wiki button), then "Go to the
 * nearest place" and "Update times". Below, every place to kill the task's monsters, fastest first, one short card
 * each: its name with Wiki and Go, its travel time, a line each for the number of monsters, multi-combat, cannon and
 * safespot with a check or a cross, its special loot as item pictures and the Wiki's notes without links. Konar's area
 * comes first and is marked; on Konar's and Krystilia's tasks, places where kills don't count are left out.
 * <p>
 * A stored task gets its own card under the current one, without a Go: stored tasks are never routed to. Three
 * filters (multi-combat, cannon, safespot) each keep only the places where that is a yes or "some spots"; the nearest
 * place's Go picks among those shown.
 * <p>
 * A star on each place's card marks it preferred for this task (saved per account, listed in the Settings tab).
 * Preferred places come first, and the nearest place's Go picks among them, or among all places when none of them can
 * be reached. Swing thread only.
 */
final class SlayerSection extends JPanel
{
	static final String LOGGED_OUT = "Log in to see your Slayer task.";
	static final String NO_TASK = "No Slayer task. A Slayer master gives you one.";
	static final String NO_DATA = "The map data has no Slayer places yet.";
	static final String NO_PLACES = "No places known for this task.";
	static final String NO_PLACES_COUNT = "No place known for this task where kills count.";
	/** The task's picture in its card, and a monster line's. */
	static final int TASK_ICON = 32;
	static final int LINE_ICON = 14;
	static final int LOOT_ICON = 24;

	private final PanelActions actions;
	private final Runnable showRoute;
	private final IconCache icons;
	/** The state line without a task (logged out, no task, no data); with one the task card says it. */
	private final WrapLabel status = new WrapLabel(LOGGED_OUT, Ui.CONTENT_WIDTH, Ui.small(), Ui.TEXT);
	private final JPanel taskCard = new JPanel(new BorderLayout(8, 0));
	private final WrapLabel rule = new WrapLabel("", Ui.CONTENT_WIDTH, Ui.small(), Ui.NOTE);
	final JButton nearest = Ui.button("Go to the nearest place", "Route to the nearest place where kills count");
	final JButton update = Ui.button("Update times", "Update the travel times");
	final JPanel storedCard = new JPanel(new BorderLayout(8, 0));
	static final String STORED_UNKNOWN = "A task is in storage";
	static final String STORED_NOTE = "Stored tasks are not routed to.";
	static final String STORED_HOW = "Store a task with Best Path on to see it here.";
	/** The filters: multi-combat, cannon, safespot. Kept while the sidebar is open. */
	final JCheckBox onlyMulti = Ui.checkBox("Only multi-combat places");
	final JCheckBox onlyCannon = Ui.checkBox("Only places for a cannon");
	final JCheckBox onlySafespot = Ui.checkBox("Only places with a safespot");
	static final String NONE_SHOWN = "No place has everything ticked.";
	private final JPanel list = new JPanel();
	SlayerTask task = SlayerTask.LOGGED_OUT;
	final List<JButton> goButtons = new ArrayList<>();
	final List<JButton> wikiButtons = new ArrayList<>();
	final List<JButton> starButtons = new ArrayList<>();
	/** Under the buttons when a preferred place is shown; %s is "preferred place" or "2 preferred places". */
	static final String PREFERRED_LINE = "Go to the nearest place and Automatic Slayer routes pick among your %s.";
	static final int STAR_ICON = 12;

	/** {@code showRoute}: shows the Route tab (after a click starts a route); {@code icons}: the sidebar's pictures. */
	SlayerSection(PanelActions actions, Runnable showRoute, IconCache icons)
	{
		this.actions = actions;
		this.showRoute = showRoute;
		this.icons = icons;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		Ui.addRow(this, status); // the page's title says "Slayer task"
		taskCard.setBackground(Ui.CARD);
		taskCard.setBorder(Ui.cardEdge(Ui.CURRENT, false, 6));
		Ui.addRow(this, taskCard);
		Ui.addRow(this, Box.createVerticalStrut(4));
		storedCard.setBackground(Ui.CARD);
		storedCard.setBorder(Ui.cardEdge(Ui.MUTED, false, 6));
		Ui.addRow(this, storedCard);
		Ui.addRow(this, rule);
		onlyMulti.setToolTipText("Only multi-combat places");
		onlyCannon.setToolTipText("Only places that allow a cannon");
		onlySafespot.setToolTipText("Show only the places with a safespot");
		for (JCheckBox box : new JCheckBox[]{onlyMulti, onlyCannon, onlySafespot})
		{
			box.addActionListener(e -> show(task));
		}
		nearest.addActionListener(e ->
		{
			RouteRequest r = task.nearest(this::shows);
			if (r != null)
			{
				actions.findSlayerNearest(r, task.nearestFallback(this::shows));
				showRoute.run();
			}
		});
		update.addActionListener(e -> actions.updateSlayerTimes());
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		Ui.addRow(this, list);
		show(SlayerTask.LOGGED_OUT);
	}

	/** The task line for the Functions tab's card ("Kalphites: 87 left (from Vannaka)."). */
	String summary()
	{
		String s = task.getState() == SlayerTask.State.TASK ? headline(task) : status.getText();
		SlayerTask.Stored st = task.getStored();
		return st == null ? s : s + " Stored: " + (st.isKnown() ? st.getName() : "a task") + ".";
	}

	void show(SlayerTask t)
	{
		task = t;
		list.removeAll();
		goButtons.clear();
		wikiButtons.clear();
		starButtons.clear();
		rule.setText(t.getRule() == null ? "" : t.getRule());
		rule.setVisible(t.getRule() != null);
		nearest.setEnabled(t.nearest(this::shows) != null);
		nearest.setToolTipText(t.nearestFallback(this::shows) != null
			? "Route to the nearest of your preferred places (all places if none of them can be reached)"
			: "Route to the nearest place where kills count");
		update.setEnabled(t.nearest() != null);
		boolean has = t.getState() == SlayerTask.State.TASK;
		status.setVisible(!has);
		taskCard.setVisible(has);
		storedCard.setVisible(t.getStored() != null);
		switch (t.getState())
		{
			case LOGGED_OUT:
				status.setText(LOGGED_OUT);
				break;
			case NO_DATA:
				status.setText(NO_DATA);
				break;
			case NO_TASK:
				status.setText(NO_TASK);
				break;
			default:
				status.setText(headline(t));
				fillTaskCard(t);
				Ui.addRow(list, Box.createVerticalStrut(4));
				JPanel buttons = new JPanel(new BorderLayout(4, 0));
				buttons.setOpaque(false);
				buttons.add(nearest, BorderLayout.CENTER);
				buttons.add(update, BorderLayout.EAST);
				buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
				Ui.addRow(list, buttons);
				Ui.addRow(list, Box.createVerticalStrut(4));
				if (t.getLocations().isEmpty())
				{
					// Konar / Krystilia: places where kills don't count are left out, so none may be left
					Ui.addRow(list, Ui.wrapped(t.getRule() != null ? NO_PLACES_COUNT : NO_PLACES, Ui.CONTENT_WIDTH, Ui.small(), Ui.MUTED));
				}
				else
				{
					Ui.addRow(list, onlyMulti);
					Ui.addRow(list, onlyCannon);
					Ui.addRow(list, onlySafespot);
					String count = shownLine(t);
					if (!count.isEmpty())
					{
						Ui.addRow(list, Ui.wrapped(count, Ui.CONTENT_WIDTH, Ui.small(), Ui.NOTE));
					}
					String pref = preferredLine(t);
					if (!pref.isEmpty())
					{
						Ui.addRow(list, Ui.wrapped(pref, Ui.CONTENT_WIDTH, Ui.small(), Ui.CURRENT));
					}
					Ui.addRow(list, Box.createVerticalStrut(4));
				}
				int shown = 0;
				for (SlayerTask.Location l : t.getLocations())
				{
					if (!shows(l))
					{
						continue;
					}
					if (shown++ > 0)
					{
						Ui.addRow(list, Box.createVerticalStrut(4));
					}
					Ui.addRow(list, card(t, l));
				}
		}
		if (t.getStored() != null)
		{
			fillStoredCard(t.getStored()); // its Wiki button after the task's and the places'
		}
		revalidate();
		repaint();
	}

	/** The place passes the ticked filters. */
	private boolean shows(SlayerTask.Location l)
	{
		return passes(l, onlyMulti.isSelected(), onlyCannon.isSelected(), onlySafespot.isSelected());
	}

	/**
	 * The place passes the filters: each ticked one needs a yes or a partial answer ("some spots") from the Wiki's
	 * comparison; a no or not known leaves the place out.
	 */
	static boolean passes(SlayerTask.Location l, boolean multi, boolean cannon, boolean safespot)
	{
		SlayerPlaces.Place p = l.getPlace();
		return (!multi || allows(p.getMulti())) && (!cannon || allows(p.getCannon())) && (!safespot || allows(p.getSafespot()));
	}

	private static boolean allows(String value)
	{
		return !(value.isEmpty() || value.equals("?") || value.equals("no"));
	}

	/** {@link #PREFERRED_LINE} for the preferred places the filters show ("your preferred place"); empty for none. */
	private String preferredLine(SlayerTask t)
	{
		int n = 0;
		for (SlayerTask.Location l : t.getLocations())
		{
			n += l.isPreferred() && l.canGo() && shows(l) ? 1 : 0;
		}
		return n == 0 ? "" : String.format(PREFERRED_LINE, n == 1 ? "preferred place" : n + " preferred places");
	}

	/** "Showing 3 of 8 places.", {@link #NONE_SHOWN}; empty without a filter ticked. */
	private String shownLine(SlayerTask t)
	{
		if (!onlyMulti.isSelected() && !onlyCannon.isSelected() && !onlySafespot.isSelected())
		{
			return "";
		}
		int n = 0;
		for (SlayerTask.Location l : t.getLocations())
		{
			n += shows(l) ? 1 : 0;
		}
		return n == 0 ? NONE_SHOWN : "Showing " + n + " of " + t.getLocations().size() + " places.";
	}

	/** The stored task's card: "Stored task", its picture, name, how many are left, the master and Wiki; no Go. */
	private void fillStoredCard(SlayerTask.Stored s)
	{
		storedCard.removeAll();
		JLabel picture = new JLabel();
		picture.setPreferredSize(new Dimension(TASK_ICON, TASK_ICON));
		setPicture(picture, s.getIcon(), TASK_ICON);
		storedCard.add(picture, BorderLayout.WEST);
		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		int inner = Ui.CONTENT_WIDTH - TASK_ICON - 70;
		for (JComponent c : new JComponent[]{
			Ui.label("Stored task", Ui.small(), Ui.MUTED),
			Ui.label(s.isKnown() ? s.getName() : STORED_UNKNOWN, Ui.bold(), Ui.TITLE),
			s.getLeft() > 0 ? Ui.label(s.getLeft() + " left", Ui.small(), Ui.TEXT) : null,
			s.getMaster() != null ? Ui.label("From " + s.getMaster(), Ui.small(), Ui.TEXT) : null,
			Ui.wrapped(s.isKnown() ? STORED_NOTE : STORED_HOW, inner, Ui.small(), Ui.MUTED)})
		{
			if (c != null)
			{
				c.setAlignmentX(LEFT_ALIGNMENT);
				text.add(c);
			}
		}
		storedCard.add(text, BorderLayout.CENTER);
		JPanel east = new JPanel(new BorderLayout());
		east.setOpaque(false);
		if (s.isKnown())
		{
			east.add(wikiButton(s.getWikiPage(), "Open the task's Wiki page"), BorderLayout.NORTH);
		}
		storedCard.add(east, BorderLayout.EAST);
		storedCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, storedCard.getPreferredSize().height));
	}

	/** "Kalphites: 87 left (from Konar)." */
	static String headline(SlayerTask t)
	{
		return t.getName() + ": " + t.getLeft() + " left" + (t.getMaster() == null ? "" : " (from " + t.getMaster() + ")") + ".";
	}

	/** The task's own card: its picture, name, how many are left, the master, and Wiki. */
	private void fillTaskCard(SlayerTask t)
	{
		taskCard.removeAll();
		JLabel picture = new JLabel();
		picture.setPreferredSize(new Dimension(TASK_ICON, TASK_ICON));
		setPicture(picture, t.getIcon(), TASK_ICON);
		taskCard.add(picture, BorderLayout.WEST);
		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		JLabel name = Ui.label(t.getName(), Ui.bold(), Ui.CURRENT);
		name.setAlignmentX(LEFT_ALIGNMENT);
		text.add(name);
		JLabel left = Ui.label(t.getLeft() + " left", Ui.normal(), Ui.TITLE);
		left.setAlignmentX(LEFT_ALIGNMENT);
		text.add(left);
		if (t.getMaster() != null)
		{
			JLabel master = Ui.label("From " + t.getMaster(), Ui.small(), Ui.TEXT);
			master.setAlignmentX(LEFT_ALIGNMENT);
			text.add(master);
		}
		taskCard.add(text, BorderLayout.CENTER);
		JButton wiki = wikiButton(t.getWikiPage(), "Open the task's Wiki page");
		JPanel east = new JPanel(new BorderLayout());
		east.setOpaque(false);
		east.add(wiki, BorderLayout.NORTH);
		taskCard.add(east, BorderLayout.EAST);
		taskCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, taskCard.getPreferredSize().height));
	}

	private JComponent card(SlayerTask t, SlayerTask.Location l)
	{
		SlayerPlaces.Place p = l.getPlace();
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(Ui.CARD);
		card.setBorder(Ui.padding(4, 6, 4, 6));
		int inner = Ui.CONTENT_WIDTH - 12;

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		JLabel name = Ui.label(p.getName(), Ui.bold(), l.canGo() ? Ui.TITLE : Ui.MUTED);
		top.add(name, BorderLayout.CENTER);
		JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		east.setOpaque(false);
		east.add(starButton(t, l));
		east.add(wikiButton(placePage(p), "Open the Wiki's page about " + placePage(p) + " in your browser"));
		if (l.canGo())
		{
			JButton go = Ui.button("Go", "Route to " + p.getName());
			go.addActionListener(e -> go(t.request(l)));
			goButtons.add(go);
			east.add(go);
		}
		top.add(east, BorderLayout.EAST);
		top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));
		Ui.addRow(card, top);
		String mark = mark(l);
		if (!mark.isEmpty())
		{
			Ui.addRow(card, Ui.wrapped(mark, inner, Ui.small(), l.canGo() ? Ui.CURRENT : Ui.MUTED));
		}
		String travel = travel(t, l);
		if (!travel.isEmpty())
		{
			Ui.addRow(card, Ui.wrapped(travel, inner, Ui.small(), l.getTicks() >= 0 ? Ui.TRAVEL : Ui.MUTED));
		}
		// the number of monsters, then multi-combat, cannon and safespot, each on its own line with a check or a cross
		String amount = amount(p);
		if (!amount.isEmpty())
		{
			JLabel monsters = Ui.label(amount, Ui.small(), l.canGo() ? Ui.TEXT : Ui.MUTED);
			monsters.setIconTextGap(4);
			setPicture(monsters, t.getIcon(), LINE_ICON);
			Ui.addRow(card, monsters);
		}
		for (Fact f : facts(l))
		{
			Ui.addRow(card, factLine(f, l.canGo()));
		}
		if (l.isWilderness())
		{
			Ui.addRow(card, Ui.label("Wilderness", Ui.small(), Ui.BAD));
		}
		if (!l.getLoot().isEmpty())
		{
			Ui.addRow(card, lootRow(l.getLoot(), inner));
		}
		for (String n : notes(p.getNotes()))
		{
			Ui.addRow(card, Ui.wrapped(n, inner, Ui.small(), Ui.MUTED));
		}
		String tip = tooltip(p);
		card.setToolTipText(tip);
		name.setToolTipText(tip);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/**
	 * "Special loot:", then each loot line's item pictures (their names on hover; as many rows as the card's width needs)
	 * with its note under them ("Ghosts don't drop ...", "From Spiritual mage.").
	 */
	private JComponent lootRow(List<SlayerPlaces.Loot> loot, int inner)
	{
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setOpaque(false);
		Ui.addRow(box, Ui.label("Special loot:", Ui.small(), Ui.NOTE));
		int perRow = Math.max(1, (inner - 2) / (LOOT_ICON + 2));
		for (SlayerPlaces.Loot l : loot)
		{
			int[] items = l.getItems();
			for (int start = 0; start < items.length; start += perRow)
			{
				JPanel pictures = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
				pictures.setOpaque(false);
				for (int i = start; i < Math.min(items.length, start + perRow); i++)
				{
					pictures.add(lootPicture(items[i]));
				}
				pictures.setMaximumSize(new Dimension(Integer.MAX_VALUE, pictures.getPreferredSize().height));
				Ui.addRow(box, pictures);
			}
			if (!l.getNote().isEmpty())
			{
				Ui.addRow(box, Ui.wrapped(l.getNote(), inner, Ui.small(), Ui.MUTED));
			}
		}
		return box;
	}

	/** One item's picture, its name on hover. */
	private JLabel lootPicture(int item)
	{
		JLabel pic = new JLabel();
		pic.setPreferredSize(new Dimension(LOOT_ICON, LOOT_ICON));
		setPicture(pic, item, LOOT_ICON);
		String known = icons.itemName(item, () ->
		{
			String later = icons.itemName(item, () -> { });
			if (later != null)
			{
				pic.setToolTipText(later);
			}
		});
		pic.setToolTipText(known != null ? known : "Item " + item);
		return pic;
	}

	/** An item's picture on a label, now or when it arrives. */
	private void setPicture(JLabel label, int item, int size)
	{
		if (item < 0)
		{
			label.setIcon(null);
			return;
		}
		BufferedImage img = icons.get(IconKey.item(item), size, size, () ->
		{
			BufferedImage later = icons.get(IconKey.item(item), size, size, null);
			if (later != null)
			{
				label.setIcon(new ImageIcon(later));
				label.repaint();
			}
		});
		label.setIcon(img == null ? null : new ImageIcon(img));
	}

	/**
	 * The star: marks the place preferred for this task or not (shown at once; the plugin saves it for the account and
	 * sends the page again).
	 */
	private JButton starButton(SlayerTask t, SlayerTask.Location l)
	{
		boolean on = l.isPreferred();
		JButton b = Ui.button("", on ? "Preferred for " + t.getName() + ": click to unmark"
			: "Mark as preferred for " + t.getName() + ": Go to the nearest place picks among your preferred places");
		b.setIcon(new StarIcon(STAR_ICON, on));
		b.setMargin(new Insets(2, 3, 2, 3));
		String key = l.key();
		b.addActionListener(e ->
		{
			show(task.withPreferred(key, !on));
			actions.preferSlayerPlace(t.getName(), key, !on);
		});
		starButtons.add(b);
		return b;
	}

	/** A "Wiki" button that opens the page in the player's browser (null page: none). */
	private JButton wikiButton(String page, String tooltip)
	{
		JButton b = Ui.button("Wiki", tooltip);
		b.setEnabled(page != null && !page.isEmpty());
		b.addActionListener(e -> actions.openWiki(page));
		wikiButtons.add(b);
		return b;
	}

	/** The Wiki page about a place: its name before any ":" ("Stronghold of Security: Catacomb of Famine"). */
	static String placePage(SlayerPlaces.Place p)
	{
		int c = p.getName().indexOf(':');
		return (c < 0 ? p.getName() : p.getName().substring(0, c)).trim();
	}

	static final String OFF_MAP = "Not in the map data yet: no route there";

	/** "Preferred", "Konar's area", {@link #OFF_MAP}; empty for none. */
	static String mark(SlayerTask.Location l)
	{
		List<String> out = new ArrayList<>();
		if (l.isPreferred())
		{
			out.add("Preferred");
		}
		if (l.isAssigned())
		{
			out.add("Konar's area");
		}
		if (!l.getPlace().isOnMap())
		{
			out.add(OFF_MAP);
		}
		return String.join(". ", out);
	}

	static final String TIMING = "Travel time: working it out...";
	static final String NOT_TIMED = "Travel time: not worked out (further away)";

	/**
	 * "Travel: 236 ticks (2 min 22 s)", "No route from here", {@link #TIMING} while the times are worked out,
	 * {@link #NOT_TIMED} for a place past the ones timed; empty for a place without a Go.
	 */
	static String travel(SlayerTask t, SlayerTask.Location l)
	{
		if (!l.canGo())
		{
			return "";
		}
		if (l.getTicks() >= 0)
		{
			return "Travel: " + l.getTicks() + " ticks (" + RouteResult.duration(l.getTicks()) + ")";
		}
		if (l.getTicks() == SlayerTask.NO_ROUTE)
		{
			return "No route from here";
		}
		for (SlayerTask.Location o : t.getLocations())
		{
			if (o.getTicks() != SlayerTask.NOT_TIMED)
			{
				return NOT_TIMED;
			}
		}
		return TIMING;
	}

	/** "43 monsters", "1 monster", the Wiki's words ("Many"); empty when not known. */
	static String amount(SlayerPlaces.Place p)
	{
		String a = p.getAmount();
		if (a.equals("?") || a.isEmpty())
		{
			return "";
		}
		return a.matches("\\d+") ? a + (a.equals("1") ? " monster" : " monsters") : a;
	}

	/** One line with a check, a cross or a question mark: "Multi-combat", "Cannon: some spots". */
	static final class Fact
	{
		final String text;
		final NeedStatus status;

		Fact(String text, NeedStatus status)
		{
			this.text = text;
			this.status = status;
		}

		@Override
		public String toString()
		{
			return (status == NeedStatus.HAVE ? "+ " : status == NeedStatus.MISSING ? "x " : "? ") + text;
		}
	}

	/** Multi-combat, cannon and safespot from the Wiki's comparison; unknown ones are left out. */
	static List<Fact> facts(SlayerTask.Location l)
	{
		SlayerPlaces.Place p = l.getPlace();
		List<Fact> out = new ArrayList<>();
		add(out, p.getMulti(), "Multi-combat");
		add(out, p.getCannon(), "Cannon");
		add(out, p.getSafespot(), "Safespot");
		return out;
	}

	private static void add(List<Fact> out, String value, String what)
	{
		switch (value)
		{
			case "yes":
				out.add(new Fact(what, NeedStatus.HAVE));
				break;
			case "no":
				out.add(new Fact(what, NeedStatus.MISSING));
				break;
			case "?":
			case "":
				break;
			default:
				out.add(new Fact(what + ": " + value, NeedStatus.MAYBE));
				break;
		}
	}

	private JComponent factLine(Fact f, boolean bright)
	{
		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		line.add(new NeedMark.Box(f.status, NeedMark.SIZE, NeedMark.SIZE + 2, icons), BorderLayout.WEST);
		line.add(Ui.label(f.text, Ui.small(), bright ? Ui.TEXT : Ui.MUTED), BorderLayout.CENTER);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		return line;
	}

	/** The Wiki's notes without links, one line per point ("*" starts a point on the Wiki). */
	static List<String> notes(String notes)
	{
		List<String> out = new ArrayList<>();
		for (String s : SlayerPlaces.plainNotes(notes).split("\\s*\\*\\s*"))
		{
			if (!s.isBlank())
			{
				out.add(s.trim());
			}
		}
		return out;
	}

	/** The monsters there, on hover (the Wiki's notes are on the card). */
	private static String tooltip(SlayerPlaces.Place p)
	{
		return p.getMonsters().isEmpty() ? null : p.getMonsters();
	}

	private void go(RouteRequest r)
	{
		if (r != null)
		{
			actions.findRoute(r);
			showRoute.run();
		}
	}
}
