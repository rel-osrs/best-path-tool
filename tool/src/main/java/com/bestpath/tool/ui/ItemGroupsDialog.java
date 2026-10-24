package com.bestpath.tool.ui;

import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.req.ItemGroupDefaults;
import com.bestpath.tool.req.ItemGroupFile;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/**
 * Every item group with a picture of each of its items, drawn from the open cache. Clicking an item makes it the
 * group's default, saved at once to {@code data/manual/item-group-defaults.tsv} ({@link ItemGroupDefaults}); the
 * plugin shows that item's picture when a step needs the group and the player has none of it. Items can also be
 * added and removed by id, and groups made and deleted, saved to {@code data/manual/item-groups.tsv}; each change is
 * noted in the group's source with the author's name. A group a requirement uses can't be deleted or emptied (the
 * requirement could no longer be read).
 */
final class ItemGroupsDialog extends JDialog
{
	private static final int SCALE = 2; // inventory pictures are 36x32: twice as big is easier to tell apart
	private static final Color CHOSEN = new Color(0x2E, 0x9E, 0x3E);

	private final LoadedCache cache;
	private final Supplier<String> author;
	private final Function<String, List<String>> usesOf;
	private final Runnable onChange;
	private final Map<Integer, ImageIcon> icons = new HashMap<>(); // Swing thread only
	private final Map<Integer, String> names = new HashMap<>(); // Swing thread only
	private final Map<String, ItemGroupFile.Line> groups = new LinkedHashMap<>();
	private Map<String, ItemGroupDefaults.Choice> defaults = ItemGroupDefaults.all();

	private final DefaultListModel<String> groupModel = new DefaultListModel<>();
	private final JList<String> groupList = new JList<>(groupModel);
	private final JPanel grid = new JPanel(new GridLayout(0, 5, 6, 6));
	private final JLabel title = new JLabel(" ");
	private final JLabel source = new JLabel(" ");
	private final JTextField addIds = new JTextField(18);
	private final JButton add = new JButton("Add");
	private final JLabel status = new JLabel(" ");
	private String shown;
	private int loadToken;

	/**
	 * @param usesOf what uses a group in a requirement (transports, networks, test players), for the
	 *               delete and empty checks
	 * @param onChange run after the groups change (routes are worked out again)
	 */
	ItemGroupsDialog(java.awt.Window owner, LoadedCache cache, Supplier<String> author, Function<String, List<String>> usesOf,
		Runnable onChange)
	{
		super(owner, "Item groups", ModalityType.MODELESS);
		this.cache = cache;
		this.author = author;
		this.usesOf = usesOf;
		this.onChange = onChange;
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setUpGroupList();
		JPanel right = rightPanel();
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel(), right);
		split.setResizeWeight(0);
		split.setDividerLocation(260);

		JLabel help = new JLabel("<html>Pick a group on the left, then click the item whose picture the plugin should show "
			+ "when the player has none of the group. Every change is saved at once.</html>");
		help.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
		bottom.add(status, BorderLayout.CENTER);
		bottom.add(close, BorderLayout.EAST);

		getContentPane().add(help, BorderLayout.NORTH);
		getContentPane().add(split, BorderLayout.CENTER);
		getContentPane().add(bottom, BorderLayout.SOUTH);
		setSize(1180, 800);
		setLocationRelativeTo(owner);
		reload(null);
	}

	/** The group list: each group's name, item count and default; picking one shows its items. */
	private void setUpGroupList()
	{
		groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		groupList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				String g = (String) value;
				ItemGroupDefaults.Choice c = defaults.get(g);
				String second = c == null
					? "<span style='color:" + (selected ? "white" : "#c06000") + "'>no default yet</span>"
					: "<span style='color:" + (selected ? "white" : "#2e7d32") + "'>default: " + esc(c.itemName) + "</span>";
				ItemGroupFile.Line l = groups.get(g);
				super.getListCellRendererComponent(list, "<html><b>" + esc(g) + "</b> (" + (l == null ? 0 : l.ids.length) + ")<br>"
					+ second + "</html>", index, selected, focus);
				setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
				return this;
			}
		});
		groupList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting() && groupList.getSelectedValue() != null)
			{
				showGroup(groupList.getSelectedValue());
			}
		});
	}

	/** The shown group: its title, source, the "Add item ids" row, and the grid of item pictures. */
	private JPanel rightPanel()
	{
		title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 15f));
		source.setForeground(Color.DARK_GRAY);
		source.setVerticalAlignment(SwingConstants.TOP);
		addIds.setToolTipText("Item ids from the cache, separated by commas or spaces, e.g. 1351, 1349");
		addIds.addActionListener(e -> addItems());
		add.addActionListener(e -> addItems());
		JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		addRow.add(new JLabel("Add item ids:"));
		addRow.add(addIds);
		addRow.add(add);
		JLabel removeHint = new JLabel("    Right-click an item to remove it from the group.");
		removeHint.setForeground(Color.DARK_GRAY);
		addRow.add(removeHint);
		JPanel head = new JPanel(new BorderLayout(0, 4));
		head.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		head.add(title, BorderLayout.NORTH);
		head.add(source, BorderLayout.CENTER);
		head.add(addRow, BorderLayout.SOUTH);
		JPanel gridHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); // keeps the tiles their own size
		gridHolder.add(grid);
		grid.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
		JScrollPane gridScroll = new JScrollPane(gridHolder);
		gridScroll.getVerticalScrollBar().setUnitIncrement(24);
		JPanel right = new JPanel(new BorderLayout());
		right.add(head, BorderLayout.NORTH);
		right.add(gridScroll, BorderLayout.CENTER);
		return right;
	}

	/** The group list with New group and Delete group below it. */
	private JPanel leftPanel()
	{
		JButton newGroup = new JButton("New group...");
		newGroup.setToolTipText("Make an empty group, then add its items by id");
		newGroup.addActionListener(e -> newGroup());
		JButton deleteGroup = new JButton("Delete group...");
		deleteGroup.setToolTipText("Delete the selected group (not while a requirement uses it)");
		deleteGroup.addActionListener(e -> deleteGroup());
		JPanel listButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		listButtons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		listButtons.add(newGroup);
		listButtons.add(deleteGroup);
		JScrollPane listScroll = new JScrollPane(groupList);
		JPanel left = new JPanel(new BorderLayout());
		left.add(listScroll, BorderLayout.CENTER);
		left.add(listButtons, BorderLayout.SOUTH);
		left.setPreferredSize(new Dimension(250, 600));
		left.setMinimumSize(new Dimension(220, 100));
		return left;
	}

	/** Reads the groups file again and fills the list, selecting {@code select} (or the first group). */
	private void reload(String select)
	{
		groups.clear();
		try
		{
			for (ItemGroupFile.Line l : ItemGroupFile.lines())
			{
				groups.put(l.name, l);
			}
		}
		catch (Exception ex)
		{
			error("Could not read the item groups: " + ex.getMessage());
		}
		defaults = ItemGroupDefaults.all();
		groupModel.clear();
		groups.keySet().forEach(groupModel::addElement);
		if (groupModel.isEmpty())
		{
			shown = null;
			grid.removeAll();
			title.setText("No item groups yet: make one with New group...");
			source.setText(" ");
			grid.revalidate();
			grid.repaint();
			return;
		}
		String pick = select != null && groups.containsKey(select) ? select : groupModel.get(0);
		groupList.setSelectedValue(pick, true);
		showGroup(pick); // also when the selection did not change
	}

	/** Shows a group's items; their pictures are drawn in the background the first time. */
	void showGroup(String group)
	{
		shown = group;
		ItemGroupFile.Line line = groups.get(group);
		title.setText(group + "  (" + line.ids.length + " items)");
		source.setText("<html><div style='width:720px'>" + esc(line.source) + "</div></html>");
		List<Integer> missing = new ArrayList<>();
		for (int id : line.ids)
		{
			if (!icons.containsKey(id))
			{
				missing.add(id);
			}
		}
		fillGrid();
		if (!missing.isEmpty())
		{
			draw(missing, () ->
			{
			});
		}
	}

	/** Draws pictures and reads names in the background, then runs {@code then} on the Swing thread. */
	private void draw(List<Integer> ids, Runnable then)
	{
		int token = ++loadToken;
		status.setForeground(Color.DARK_GRAY);
		status.setText("Drawing " + ids.size() + " pictures from the cache...");
		new SwingWorker<Void, Object[]>()
		{
			@Override
			protected Void doInBackground() throws Exception
			{
				for (int id : ids)
				{
					BufferedImage img = cache.itemIcon(id);
					String name = cache.itemName(id);
					publish(new Object[]{id, img, name});
				}
				return null;
			}

			@Override
			protected void process(List<Object[]> chunks)
			{
				for (Object[] c : chunks)
				{
					int id = (Integer) c[0];
					icons.put(id, icon((BufferedImage) c[1]));
					if (c[2] != null)
					{
						names.put(id, (String) c[2]);
					}
				}
				if (token == loadToken)
				{
					fillGrid();
				}
			}

			@Override
			protected void done()
			{
				try
				{
					get();
					if (token == loadToken)
					{
						status.setText(" ");
					}
					then.run();
				}
				catch (Exception ex)
				{
					error("Could not draw the pictures: " + ex.getMessage());
				}
			}
		}.execute();
	}

	private void fillGrid()
	{
		grid.removeAll();
		String group = shown;
		ItemGroupDefaults.Choice chosen = defaults.get(group);
		for (int id : groups.get(group).ids)
		{
			boolean isDefault = chosen != null && chosen.itemId == id;
			String name = names.getOrDefault(id, icons.containsKey(id) ? "(not in this cache)" : "...");
			JButton tile = new JButton("<html><div style='text-align:center;width:120px'>" + esc(name) + "<br><span style='color:gray'>"
				+ id + "</span>" + (isDefault ? "<br><b style='color:#2e7d32'>default</b>" : "") + "</div></html>",
				icons.getOrDefault(id, BLANK));
			tile.setVerticalTextPosition(SwingConstants.BOTTOM);
			tile.setHorizontalTextPosition(SwingConstants.CENTER);
			tile.setFocusPainted(false);
			tile.setBorder(isDefault
				? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(CHOSEN, 3), BorderFactory.createEmptyBorder(4, 4, 4, 4))
				: BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xC8, 0xC8, 0xC8), 1), BorderFactory.createEmptyBorder(6, 6, 6, 6)));
			tile.setPreferredSize(new Dimension(150, 150));
			tile.setToolTipText("Click: make " + name + " (" + id + ") the default.  Right-click: remove it from the group.");
			tile.addActionListener(e -> choose(group, id));
			JPopupMenu menu = new JPopupMenu();
			JMenuItem remove = new JMenuItem("Remove " + name + " (" + id + ") from " + group);
			remove.addActionListener(e -> removeItem(group, id));
			menu.add(remove);
			tile.setComponentPopupMenu(menu);
			grid.add(tile);
		}
		grid.revalidate();
		grid.repaint();
	}

	private void choose(String group, int id)
	{
		String name = names.get(id);
		try
		{
			ItemGroupDefaults.set(group, id, name, author.get());
			defaults = ItemGroupDefaults.all();
			saved("Saved: the default for " + group + " is " + name + " (" + id + ").");
		}
		catch (Exception ex)
		{
			error("Could not save: " + ex.getMessage());
		}
		groupList.repaint();
		fillGrid();
	}

	/** Adds the typed ids to the shown group, once each is found in the cache. */
	private void addItems()
	{
		String group = shown;
		if (group == null)
		{
			return;
		}
		Set<Integer> wanted = typedIds();
		if (wanted == null)
		{
			return;
		}
		for (int id : groups.get(group).ids)
		{
			wanted.remove(id);
		}
		if (wanted.isEmpty())
		{
			error(addIds.getText().isBlank() ? "Type one or more item ids first." : "Those items are in the group already.");
			return;
		}
		add.setEnabled(false);
		List<Integer> ids = new ArrayList<>(wanted);
		draw(ids, () ->
		{
			add.setEnabled(true);
			addDrawn(group, ids);
		});
	}

	/** The item ids typed in the "Add item ids" field; null (with the problem shown) when one is not a number. */
	private Set<Integer> typedIds()
	{
		Set<Integer> wanted = new LinkedHashSet<>();
		for (String w : addIds.getText().trim().split("[\\s,;]+"))
		{
			if (w.isEmpty())
			{
				continue;
			}
			if (!w.matches("\\d{1,6}"))
			{
				error("'" + w + "' is not an item id: type numbers only, e.g. 1351, 1349");
				return null;
			}
			wanted.add(Integer.parseInt(w));
		}
		return wanted;
	}

	/** Adds the items, now drawn, to the group; nothing when one of them is not in the cache. */
	private void addDrawn(String group, List<Integer> ids)
	{
		List<String> unknown = new ArrayList<>();
		List<Integer> found = new ArrayList<>();
		for (int id : ids)
		{
			if (names.containsKey(id) && !names.get(id).equals("null"))
			{
				found.add(id);
			}
			else
			{
				unknown.add(Integer.toString(id));
			}
		}
		if (!unknown.isEmpty())
		{
			error("No item " + String.join(", ", unknown) + " in this cache: nothing added.");
			return;
		}
		ItemGroupFile.Line l = groups.get(group);
		int[] newIds = java.util.Arrays.copyOf(l.ids, l.ids.length + found.size());
		StringBuilder nameList = new StringBuilder(l.names);
		List<String> what = new ArrayList<>();
		for (int k = 0; k < found.size(); k++)
		{
			int id = found.get(k);
			newIds[l.ids.length + k] = id;
			String entry = names.get(id) + " " + id;
			nameList.append(nameList.length() == 0 ? "" : "; ").append(entry);
			what.add(entry);
		}
		if (save(group, new ItemGroupFile.Line(group, newIds, note(l.source, "added " + String.join(", ", what)), nameList.toString())))
		{
			addIds.setText("");
			saved("Added to " + group + ": " + String.join(", ", what) + ".");
		}
	}

	private void removeItem(String group, int id)
	{
		ItemGroupFile.Line l = groups.get(group);
		if (l.ids.length == 1)
		{
			List<String> uses = usesOf.apply(group);
			if (!uses.isEmpty())
			{
				showUses("This is the last item of " + group + ", and a group in use can't be empty.", uses);
				return;
			}
		}
		int[] newIds = java.util.Arrays.stream(l.ids).filter(v -> v != id).toArray();
		List<String> kept = new ArrayList<>();
		for (String entry : l.names.split(";\\s*"))
		{
			if (!entry.isBlank() && !entry.trim().endsWith(" " + id))
			{
				kept.add(entry.trim());
			}
		}
		String entry = names.getOrDefault(id, "item") + " " + id;
		try
		{
			if (defaults.containsKey(group) && defaults.get(group).itemId == id)
			{
				ItemGroupDefaults.remove(group);
			}
		}
		catch (Exception ex)
		{
			error("Could not save the defaults: " + ex.getMessage());
			return;
		}
		if (save(group, new ItemGroupFile.Line(group, newIds, note(l.source, "removed " + entry), String.join("; ", kept))))
		{
			saved("Removed from " + group + ": " + entry + ".");
		}
	}

	private void newGroup()
	{
		String name = JOptionPane.showInputDialog(this, "<html>Name of the new group, as requirement lines will write it<br>"
			+ "(e.g. <b>axe</b> for <i>item axe</i>; small letters, numbers, spaces, ' and -):</html>", "New item group",
			JOptionPane.PLAIN_MESSAGE);
		if (name != null)
		{
			makeGroup(name);
		}
	}

	private void makeGroup(String name)
	{
		name = name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		String problem = nameProblem(name);
		if (problem != null)
		{
			error(problem);
			return;
		}
		if (save(name, new ItemGroupFile.Line(name, new int[0], author.get() + " " + LocalDate.now() + ": made in the Data Tool", "")))
		{
			saved("Made the group " + name + ": add its items by id.");
			addIds.requestFocusInWindow();
		}
	}

	/** Why a new group can't have this name, or null. */
	private String nameProblem(String name)
	{
		if (name.isEmpty())
		{
			return "The name is empty.";
		}
		if (!name.matches("[a-z0-9'\\-]+( [a-z0-9'\\-]+)*") || !name.matches(".*[a-z].*"))
		{
			return "Use small letters, numbers, spaces, ' and - only (at least one letter).";
		}
		for (String w : name.split(" "))
		{
			if (w.equals("worn") || w.equals("used") || w.matches("x\\d+"))
			{
				return "'" + w + "' can't be part of a group name: requirement lines use it after the group (worn, used up, x2).";
			}
		}
		if (groups.containsKey(name))
		{
			return "There is a group called " + name + " already.";
		}
		return null;
	}

	private void deleteGroup()
	{
		deleteGroup(true);
	}

	private void deleteGroup(boolean ask)
	{
		String group = shown;
		if (group == null)
		{
			return;
		}
		List<String> uses = usesOf.apply(group);
		if (!uses.isEmpty())
		{
			showUses("The group " + group + " is still used, so it can't be deleted.", uses);
			return;
		}
		int n = groups.get(group).ids.length;
		if (ask && JOptionPane.showConfirmDialog(this, "Delete the group " + group + " (" + n + " items)?\nNo requirement uses it.",
			"Delete item group", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
		{
			return;
		}
		try
		{
			ItemGroupDefaults.remove(group);
			List<ItemGroupFile.Line> lines = new ArrayList<>(groups.values());
			lines.removeIf(l -> l.name.equals(group));
			ItemGroupFile.write(lines);
		}
		catch (Exception ex)
		{
			error("Could not save: " + ex.getMessage());
			return;
		}
		int at = groupModel.indexOf(group);
		String next = at + 1 < groupModel.size() ? groupModel.get(at + 1) : at > 0 ? groupModel.get(at - 1) : null;
		reload(next);
		saved("Deleted the group " + group + ".");
		onChange.run();
	}

	private void showUses(String head, List<String> uses)
	{
		StringBuilder sb = new StringBuilder(head).append("\nRemove it from these requirements first:\n");
		for (int i = 0; i < uses.size() && i < 20; i++)
		{
			sb.append("  ").append(uses.get(i)).append('\n');
		}
		if (uses.size() > 20)
		{
			sb.append("  ... and ").append(uses.size() - 20).append(" more\n");
		}
		JOptionPane.showMessageDialog(this, sb.toString(), "Item group in use", JOptionPane.WARNING_MESSAGE);
	}

	/** Replaces (or adds) one group's line and writes the file; false (and a red status) if it failed. */
	private boolean save(String group, ItemGroupFile.Line line)
	{
		List<ItemGroupFile.Line> lines = new ArrayList<>();
		boolean replaced = false;
		for (ItemGroupFile.Line l : groups.values())
		{
			if (l.name.equals(group))
			{
				lines.add(line);
				replaced = true;
			}
			else
			{
				lines.add(l);
			}
		}
		if (!replaced)
		{
			lines.add(line);
		}
		try
		{
			ItemGroupFile.write(lines);
		}
		catch (Exception ex)
		{
			error("Could not save: " + ex.getMessage());
			return false;
		}
		reload(group);
		onChange.run();
		return true;
	}

	/** The source with a dated note of a change by the author. */
	private String note(String sourceText, String change)
	{
		String n = author.get() + " " + LocalDate.now() + ": " + change;
		return sourceText.isBlank() ? n : sourceText + "; " + n;
	}

	private void saved(String text)
	{
		status.setForeground(CHOSEN.darker());
		status.setText(text);
	}

	private void error(String text)
	{
		status.setForeground(Color.RED);
		status.setText(text);
	}

	private static final ImageIcon BLANK = new ImageIcon(new BufferedImage(36 * SCALE, 32 * SCALE, BufferedImage.TYPE_INT_ARGB));

	private static ImageIcon icon(BufferedImage img)
	{
		if (img == null)
		{
			return BLANK;
		}
		BufferedImage big = new BufferedImage(img.getWidth() * SCALE, img.getHeight() * SCALE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = big.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR); // crisp pixels
		g.drawImage(img, 0, 0, big.getWidth(), big.getHeight(), null);
		g.dispose();
		return new ImageIcon(big);
	}

	private static String esc(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Test hook (UiCheck): the group list. */
	JList<String> devGroupList()
	{
		return groupList;
	}

	/** Test hook (UiCheck): makes a group without the name question. */
	void devMakeGroup(String name)
	{
		makeGroup(name);
	}

	/** Test hook (UiCheck): types ids into the add box and presses Add. */
	void devAdd(String ids)
	{
		addIds.setText(ids);
		addItems();
	}

	/** Test hook (UiCheck): the right-click Remove. */
	void devRemove(String group, int id)
	{
		removeItem(group, id);
	}

	/** Test hook (UiCheck): deletes the shown group without the OK question (the in-use check still runs). */
	void devDeleteShown()
	{
		deleteGroup(false);
	}

	/** Test hook (UiCheck): what uses a group. */
	List<String> devUses(String group)
	{
		return usesOf.apply(group);
	}

	/** Test hook (UiCheck): the status line. */
	String devStatus()
	{
		return status.getText();
	}

	/** Test hook (UiCheck): true while pictures are still being drawn. */
	boolean devLoading()
	{
		return status.getText().startsWith("Drawing");
	}
}
