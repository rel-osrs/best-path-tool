package com.bestpath.tool.ui;

import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.Skill;
import com.bestpath.tool.req.CacheNames;
import com.bestpath.tool.req.HouseThings;
import com.bestpath.tool.req.ItemGroupFile;
import com.bestpath.tool.req.TestPlayer;
import com.bestpath.tool.req.TestPlayers;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

/**
 * Makes and edits the test players for the route check: levels, quests, unlocks (varbits / varps), what they carry,
 * wear and have in the bank, their answers and their player-owned house (where it is, what is built in it). Maxed and
 * Fresh account are built in (copy them to make your own). Saved to {@code data/manual/test-players.json}.
 */
final class TestPlayersDialog extends JDialog
{
	private final DefaultListModel<TestPlayer> model = new DefaultListModel<>();
	private final JList<TestPlayer> list = new JList<>(model);
	private final JTextField name = new JTextField(20);
	private final JCheckBox members = new JCheckBox("Members");
	private final JCheckBox allUnlocked = new JCheckBox("Every quest and unlock done");
	private final JCheckBox allItems = new JCheckBox("Carries every item and coins");
	private final Map<Skill, JSpinner> levels = new LinkedHashMap<>();
	private final JTextArea quests = area();
	private final JTextArea unlocks = area();
	private final JTextArea inventory = area();
	private final JTextArea worn = area();
	private final JTextArea bank = area();
	private final JTextArea answers = area();
	private final JTextArea houseBuilt = area();
	private final JTextArea pouch = area();
	private final JCheckBox pouchInBank = new JCheckBox("The rune pouch is in the bank (not carried)");
	private final JCheckBox cooldownsUsed = new JCheckBox("Cooldowns used");
	private final JComboBox<String> spellbook = new JComboBox<>(new String[]{"standard", "ancient", "lunar", "arceuus"});
	private final JComboBox<String> housePlace = new JComboBox<>();
	private final JComboBox<String> housePick = new JComboBox<>();
	private static final String NO_HOUSE = "No house";
	private final JComboBox<String> questPick = new JComboBox<>();
	private final JLabel status = new JLabel(" ");
	private final List<Component> editable = new ArrayList<>();
	private TestPlayer shown;
	private boolean switching;
	private boolean saved;

	private TestPlayersDialog(java.awt.Window owner, String select)
	{
		super(owner, "Test players (for the route check)", ModalityType.APPLICATION_MODAL);
		for (TestPlayer p : TestPlayers.load())
		{
			model.addElement(p);
		}
		JPanel left = listPanel();
		JPanel top = topRow();
		JPanel skills = skillsPanel();
		JTabbedPane tabs = tabs();

		JPanel right = new JPanel();
		right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
		right.add(top);
		right.add(skills);
		right.add(tabs);

		JPanel main = new JPanel(new BorderLayout(8, 8));
		main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		main.add(left, BorderLayout.WEST);
		main.add(right, BorderLayout.CENTER);
		getContentPane().add(main, BorderLayout.CENTER);
		getContentPane().add(bottomBar(), BorderLayout.SOUTH);

		int index = 0;
		for (int i = 0; i < model.size(); i++)
		{
			if (model.get(i).name.equals(select))
			{
				index = i;
			}
		}
		list.setSelectedIndex(index);
		pack();
		setLocationRelativeTo(owner);
	}

	/** The list of players with New, Copy and Delete below it. */
	private JPanel listPanel()
	{
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener(e ->
		{
			if (e.getValueIsAdjusting() || switching)
			{
				return;
			}
			TestPlayer next = list.getSelectedValue();
			if (shown != null && next != shown && !store())
			{
				switching = true;
				list.setSelectedValue(shown, true);
				switching = false;
				return;
			}
			show(next);
		});
		JButton add = new JButton("New");
		add.setToolTipText("A new player: every level 1 (Hitpoints 10), nothing done or carried");
		add.addActionListener(e -> addPlayer(new TestPlayer()));
		JButton copy = new JButton("Copy");
		copy.setToolTipText("A new player starting as a copy of the selected one (also Maxed or Fresh account)");
		copy.addActionListener(e ->
		{
			if (shown != null && store())
			{
				TestPlayer c = shown.copy();
				c.name = shown.name + " copy";
				addPlayer(c);
			}
		});
		JButton delete = new JButton("Delete");
		delete.addActionListener(e -> deleteShown());
		JPanel left = new JPanel(new BorderLayout(4, 4));
		JScrollPane listScroll = new JScrollPane(list);
		listScroll.setPreferredSize(new Dimension(180, 300));
		left.add(listScroll, BorderLayout.CENTER);
		JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		listButtons.add(add);
		listButtons.add(copy);
		listButtons.add(delete);
		left.add(listButtons, BorderLayout.SOUTH);
		return left;
	}

	private void deleteShown()
	{
		if (shown != null && !shown.builtIn)
		{
			int i = model.indexOf(shown);
			shown = null;
			model.remove(i);
			list.setSelectedIndex(Math.max(0, i - 1));
		}
	}

	/** Name, members, the "everything" boxes, cooldowns and spellbook. */
	private JPanel topRow()
	{
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		top.add(new JLabel("Name:"));
		top.add(name);
		top.add(members);
		top.add(allUnlocked);
		top.add(allItems);
		top.add(cooldownsUsed);
		top.add(new JLabel("Spellbook:"));
		top.add(spellbook);
		editable.add(spellbook);
		allUnlocked.setToolTipText("Like Maxed: every quest counts as done, every varbit / varp check and answer as met");
		allItems.setToolTipText("Like Maxed: every item counts as carried");
		cooldownsUsed.setToolTipText("The home and minigame teleports were just used: their 30 / 20-minute cooldowns are running, so "
			+ "routes leave them out (the plugin reads this from the game)");
		editable.add(name);
		editable.add(members);
		editable.add(allUnlocked);
		editable.add(allItems);
		editable.add(cooldownsUsed);
		return top;
	}

	/** A level spinner per skill. */
	private JPanel skillsPanel()
	{
		JPanel skills = new JPanel(new GridLayout(0, 6, 6, 2));
		skills.setBorder(BorderFactory.createTitledBorder("Levels (real, not boosted)"));
		for (Skill s : Skill.values())
		{
			JSpinner sp = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
			levels.put(s, sp);
			editable.add(sp);
			JPanel cell = new JPanel(new BorderLayout(4, 0));
			cell.add(new JLabel(s.getLabel()), BorderLayout.CENTER);
			cell.add(sp, BorderLayout.EAST);
			skills.add(cell);
		}
		return skills;
	}

	/** One tab per kind of text: quests, unlocks, items, answers, rune pouch and house. */
	private JTabbedPane tabs()
	{
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Quests", questTab());
		tabs.addTab("Unlocks", tab(unlocks, "One per line: varbit <id> = <value>  or  varp <id> = <value>  (others are 0)."));
		tabs.addTab("Inventory", tab(inventory, "One per line: <item id> [x<count>]  or  <item group name>  (e.g. 954 x2, light source). "
			+ "Text after # is ignored. Coins: 995 x<amount>."));
		tabs.addTab("Worn", tab(worn, "One per line: <slot> <item id>  (slots: head, cape, neck, weapon, body, shield, legs, hands, feet, ring, ammo)."));
		tabs.addTab("Bank", tab(bank, "One per line: <item id> [x<count>]  or  <item group name>. Text after # is ignored."));
		tabs.addTab("Answers", tab(answers, "One per line: an answer a requirement asks for (the text after 'answer')."));
		JPanel pouchTab = tab(pouch, "One per line: <rune item id> [x<count>] (e.g. 563 x200). Read from the game in the plugin.");
		pouchTab.add(pouchInBank, BorderLayout.NORTH);
		tabs.addTab("Rune pouch", pouchTab);
		editable.add(pouchInBank);
		tabs.addTab("House", houseTab());
		for (JTextArea a : new JTextArea[]{quests, unlocks, inventory, worn, bank, answers, houseBuilt, pouch})
		{
			editable.add(a);
		}
		return tabs;
	}

	/** The quests text with a quest list from the cache to add from. */
	private JPanel questTab()
	{
		JPanel questTab = tab(quests, "One per line: <quest name>: done | started (others: not started).");
		JPanel pick = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		for (String q : CacheNames.questNames())
		{
			questPick.addItem(q);
		}
		JButton addDone = new JButton("Add as done");
		JButton addStarted = new JButton("Add as started");
		addDone.addActionListener(e -> addQuest("done"));
		addStarted.addActionListener(e -> addQuest("started"));
		pick.add(new JLabel(CacheNames.hasQuests() ? "Quest (from the cache):" : "Quest list: open a cache first"));
		pick.add(questPick);
		pick.add(addDone);
		pick.add(addStarted);
		editable.add(questPick);
		editable.add(addDone);
		editable.add(addStarted);
		questTab.add(pick, BorderLayout.NORTH);
		return questTab;
	}

	/** Where the house is, and the things built in it with a list to add from. */
	private JPanel houseTab()
	{
		JPanel houseTab = tab(houseBuilt, "One per line: something built in the house (from data/manual/house-things.tsv).");
		JPanel houseTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		housePlace.addItem(NO_HOUSE);
		for (String place : HouseThings.places())
		{
			housePlace.addItem(place);
		}
		for (String thing : HouseThings.things())
		{
			housePick.addItem(thing);
		}
		JButton addThing = new JButton("Add");
		addThing.addActionListener(e ->
		{
			Object t = housePick.getSelectedItem();
			if (t != null)
			{
				String now = houseBuilt.getText();
				houseBuilt.setText(now + (now.isEmpty() || now.endsWith("\n") ? "" : "\n") + t + "\n");
			}
		});
		houseTop.add(new JLabel("House at:"));
		houseTop.add(housePlace);
		houseTop.add(new JLabel("   Built:"));
		houseTop.add(housePick);
		houseTop.add(addThing);
		houseTab.add(houseTop, BorderLayout.NORTH);
		editable.add(housePlace);
		editable.add(housePick);
		editable.add(addThing);
		return houseTab;
	}

	/** The status line with Save and Close. */
	private JPanel bottomBar()
	{
		JButton save = new JButton("Save");
		save.setToolTipText("Save your players to data/manual/test-players.json");
		save.addActionListener(e -> savePlayers());
		JButton close = new JButton("Close");
		close.setToolTipText("Close (unsaved changes are dropped)");
		close.addActionListener(e -> dispose());
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(status, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(save);
		buttons.add(close);
		bottom.add(buttons, BorderLayout.EAST);
		bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		return bottom;
	}

	/** Saves every player (after reading the form into the shown one). */
	private void savePlayers()
	{
		if (shown != null && !store())
		{
			return;
		}
		List<TestPlayer> all = new ArrayList<>();
		for (int i = 0; i < model.size(); i++)
		{
			all.add(model.get(i));
		}
		try
		{
			TestPlayers.save(all);
			saved = true;
			status.setForeground(new Color(0, 120, 0));
			status.setText("Saved " + (all.size() - 2) + " players.");
		}
		catch (Exception ex)
		{
			status.setForeground(Color.RED);
			status.setText("Could not save: " + ex.getMessage());
		}
	}

	private static JTextArea area()
	{
		JTextArea a = new JTextArea(8, 50);
		a.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		return a;
	}

	private static JPanel tab(JTextArea area, String hint)
	{
		JPanel p = new JPanel(new BorderLayout(2, 2));
		JPanel south = new JPanel(new BorderLayout());
		south.add(new JLabel(hint), BorderLayout.CENTER);
		p.add(new JScrollPane(area), BorderLayout.CENTER);
		p.add(south, BorderLayout.SOUTH);
		return p;
	}

	private void addPlayer(TestPlayer p)
	{
		if (shown != null && !store())
		{
			return;
		}
		model.addElement(p);
		list.setSelectedValue(p, true);
	}

	private void addQuest(String state)
	{
		Object q = questPick.getSelectedItem();
		if (q != null)
		{
			String t = quests.getText();
			quests.setText(t + (t.isEmpty() || t.endsWith("\n") ? "" : "\n") + q + ": " + state + "\n");
		}
	}

	/** Puts a player's data into the form. */
	private void show(TestPlayer p)
	{
		shown = p;
		if (p == null)
		{
			return;
		}
		name.setText(p.name);
		members.setSelected(p.members);
		allUnlocked.setSelected(p.allUnlocked);
		allItems.setSelected(p.allItems);
		for (Skill s : Skill.values())
		{
			Integer v = p.levels.get(s.getLabel());
			levels.get(s).setValue(v != null ? v : s == Skill.HITPOINTS ? 10 : 1);
		}
		StringBuilder sb = new StringBuilder();
		p.quests.forEach((k, v) -> sb.append(CacheNames.quest(k) != null ? CacheNames.quest(k) : k).append(": ").append(v).append('\n'));
		quests.setText(sb.toString());
		sb.setLength(0);
		p.varbits.forEach((k, v) -> sb.append("varbit ").append(k).append(" = ").append(v).append('\n'));
		p.varps.forEach((k, v) -> sb.append("varp ").append(k).append(" = ").append(v).append('\n'));
		unlocks.setText(sb.toString());
		inventory.setText(itemLines(p.inventory));
		sb.setLength(0);
		p.worn.forEach((k, v) -> sb.append(k).append(' ').append(v).append(itemName(v)).append('\n'));
		worn.setText(sb.toString());
		bank.setText(itemLines(p.bank));
		answers.setText(String.join("\n", p.answers) + (p.answers.isEmpty() ? "" : "\n"));
		housePlace.setSelectedItem(p.house == null || p.house.isEmpty() ? NO_HOUSE : HouseThings.canonical(p.house));
		if (p.house != null && !p.house.isEmpty() && !p.house.equalsIgnoreCase(String.valueOf(housePlace.getSelectedItem())))
		{
			housePlace.addItem(p.house);
			housePlace.setSelectedItem(p.house);
		}
		houseBuilt.setText(String.join("\n", p.houseThings) + (p.houseThings.isEmpty() ? "" : "\n"));
		pouch.setText(itemLines(p.pouch));
		pouchInBank.setSelected(p.pouchInBank);
		cooldownsUsed.setSelected(p.cooldownsUsed);
		spellbook.setSelectedItem(p.spellbook == null || p.spellbook.isEmpty() ? "standard" : p.spellbook);
		for (Component c : editable)
		{
			c.setEnabled(!p.builtIn);
		}
		status.setForeground(Color.DARK_GRAY);
		status.setText(p.builtIn ? p.name + " is built in: press Copy to make your own from it." : " ");
	}

	private static String itemLines(Map<String, Integer> items)
	{
		StringBuilder sb = new StringBuilder();
		items.forEach((k, v) -> sb.append(k).append(v > 1 ? " x" + v : "").append(itemName(Integer.parseInt(k))).append('\n'));
		return sb.toString();
	}

	private static String itemName(int id)
	{
		String n = CacheNames.item(id);
		return n == null ? "" : "  # " + n;
	}

	/** Reads the form into the shown player; false (with the problem shown) if something can't be read. */
	private boolean store()
	{
		TestPlayer p = shown;
		if (p == null || p.builtIn)
		{
			return true;
		}
		try
		{
			TestPlayer n = new TestPlayer();
			n.name = name.getText().trim().isEmpty() ? "Unnamed" : name.getText().trim();
			n.members = members.isSelected();
			n.allUnlocked = allUnlocked.isSelected();
			n.allItems = allItems.isSelected();
			for (Skill s : Skill.values())
			{
				int v = (Integer) levels.get(s).getValue();
				if (v != (s == Skill.HITPOINTS ? 10 : 1))
				{
					n.levels.put(s.getLabel(), v);
				}
			}
			List<String> unknown = new ArrayList<>();
			readQuests(n, unknown);
			readUnlocks(n);
			readItems(inventory, "Inventory", n.inventory);
			readItems(bank, "Bank", n.bank);
			readItems(pouch, "Rune pouch", n.pouch);
			n.pouchInBank = pouchInBank.isSelected();
			n.cooldownsUsed = cooldownsUsed.isSelected();
			n.spellbook = "standard".equals(spellbook.getSelectedItem()) ? null : String.valueOf(spellbook.getSelectedItem());
			readWorn(n);
			n.answers.addAll(lines(answers));
			readHouse(n, unknown);
			n.toState();
			copyInto(p, n);
			list.repaint();
			status.setForeground(unknown.isEmpty() ? Color.DARK_GRAY : new Color(190, 100, 0));
			status.setText(unknown.isEmpty() ? " " : "Kept, but unknown: " + String.join(", ", unknown));
			return true;
		}
		catch (IllegalArgumentException ex)
		{
			status.setForeground(Color.RED);
			status.setText(ex.getMessage());
			return false;
		}
	}

	/** The Quests text into {@code n}; quests the cache does not know are added to {@code unknown}. */
	private void readQuests(TestPlayer n, List<String> unknown)
	{
		for (String line : lines(quests))
		{
			int colon = line.lastIndexOf(':');
			if (colon < 1)
			{
				throw new IllegalArgumentException("Quests: '" + line + "' needs '<quest>: done' or '<quest>: started'");
			}
			String q = line.substring(0, colon).trim();
			String state = line.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
			if (!state.equals("done") && !state.equals("started") && !state.equals("not started"))
			{
				throw new IllegalArgumentException("Quests: '" + line + "': the state must be done or started");
			}
			if (CacheNames.hasQuests() && CacheNames.quest(q) == null)
			{
				unknown.add("quest '" + q + "'");
			}
			n.quests.put(CacheNames.quest(q) != null ? CacheNames.quest(q) : q, state);
		}
	}

	/** The Unlocks text (varbits and varps) into {@code n}. */
	private void readUnlocks(TestPlayer n)
	{
		for (String line : lines(unlocks))
		{
			String[] w = line.replace("=", " = ").trim().split("\\s+");
			if (w.length != 4 || !w[2].equals("=") || !(w[0].equalsIgnoreCase("varbit") || w[0].equalsIgnoreCase("varp")))
			{
				throw new IllegalArgumentException("Unlocks: '" + line + "' needs 'varbit <id> = <value>' or 'varp <id> = <value>'");
			}
			(w[0].equalsIgnoreCase("varp") ? n.varps : n.varbits).put(Integer.toString(number(w[1], line)), number(w[3], line));
		}
	}

	/** The Worn text (slot and item id) into {@code n}. */
	private void readWorn(TestPlayer n)
	{
		for (String line : lines(worn))
		{
			String[] w = line.split("\\s+");
			if (w.length != 2 || EquipSlot.byName(w[0]) == null)
			{
				throw new IllegalArgumentException("Worn: '" + line + "' needs '<slot> <item id>', e.g. 'head 5013'");
			}
			n.worn.put(EquipSlot.byName(w[0]).getLabel(), number(w[1], line));
		}
	}

	/** Where the house is and what is built in it into {@code n}; unknown things are added to {@code unknown}. */
	private void readHouse(TestPlayer n, List<String> unknown)
	{
		Object place = housePlace.getSelectedItem();
		n.house = place == null || NO_HOUSE.equals(place) ? null : place.toString();
		for (String t : lines(houseBuilt))
		{
			if (!HouseThings.isThing(t))
			{
				unknown.add("house thing '" + t + "'");
			}
			n.houseThings.add(HouseThings.canonical(t));
		}
		if (n.house == null && !n.houseThings.isEmpty())
		{
			throw new IllegalArgumentException("House: things are built but there is no house; pick where the house is");
		}
	}

	/** Gives player {@code p} everything read into {@code n}. */
	private static void copyInto(TestPlayer p, TestPlayer n)
	{
		p.name = n.name;
		p.members = n.members;
		p.allUnlocked = n.allUnlocked;
		p.allItems = n.allItems;
		p.levels = n.levels;
		p.quests = n.quests;
		p.varbits = n.varbits;
		p.varps = n.varps;
		p.inventory = n.inventory;
		p.worn = n.worn;
		p.bank = n.bank;
		p.answers = n.answers;
		p.house = n.house;
		p.houseThings = n.houseThings;
		p.pouch = n.pouch;
		p.pouchInBank = n.pouchInBank;
		p.cooldownsUsed = n.cooldownsUsed;
		p.spellbook = n.spellbook;
	}

	private static void readItems(JTextArea area, String what, Map<String, Integer> into)
	{
		for (String line : lines(area))
		{
			String[] w = line.split("\\s+");
			int count = 1;
			String item = line;
			if (w.length > 1 && w[w.length - 1].matches("x\\d+"))
			{
				count = Integer.parseInt(w[w.length - 1].substring(1));
				item = line.substring(0, line.lastIndexOf(w[w.length - 1])).trim();
			}
			int id;
			if (item.matches("\\d+"))
			{
				id = Integer.parseInt(item);
			}
			else
			{
				int[] group = ItemGroupFile.groups().get(item);
				if (group == null)
				{
					throw new IllegalArgumentException(what + ": '" + line + "' needs '<item id> [x<count>]' or an item group name");
				}
				id = group[0];
			}
			into.merge(Integer.toString(id), count, Integer::sum);
		}
	}

	/** The non-empty lines of a text area, without "# ..." comments. */
	private static List<String> lines(JTextArea area)
	{
		List<String> out = new ArrayList<>();
		for (String line : area.getText().split("\n"))
		{
			int hash = line.indexOf('#');
			String t = (hash >= 0 ? line.substring(0, hash) : line).trim();
			if (!t.isEmpty())
			{
				out.add(t);
			}
		}
		return out;
	}

	private static int number(String s, String line)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException("'" + line + "': '" + s + "' is not a number");
		}
	}

	/** Shows the dialog; true if the players were saved. */
	static boolean edit(Component parent, String select)
	{
		TestPlayersDialog d = new TestPlayersDialog(javax.swing.SwingUtilities.getWindowAncestor(parent), select);
		d.setVisible(true);
		return d.saved;
	}
}
