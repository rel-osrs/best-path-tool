package com.bestpath.tool.ui;

import com.bestpath.core.req.RequirementText;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.req.CacheNames;
import com.bestpath.tool.req.ItemGroupFile;
import com.bestpath.tool.req.Networks;
import com.bestpath.tool.req.RequirementWords;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

/**
 * Bottom panel: the transport networks (fairy rings, charter ships, canoes...).
 * Left: every network with its extra ticks and what every ride needs, and a
 * form to change, add or delete one ({@code data/manual/networks.tsv}).
 * Right: the selected network's stops and rides; click one to show it on the
 * map, add the transport selected on the map, or take a ride out.
 */
final class NetworksPanel extends JPanel
{
	private static final String NEW = "(new network)";

	private final EditorContext ctx;
	private final Runnable recompute;

	private final NetModel nets = new NetModel();
	private final JTable netTable = new JTable(nets);
	private final MemberModel members = new MemberModel();
	private final JTable memberTable = new JTable(members);
	private final JLabel membersTitle = new JLabel(" ");

	// the form for the selected network
	private final JTextField name = new JTextField(20);
	private final JSpinner extra = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
	private final JTextField needs = new JTextField(50);
	private final JLabel needsCheck = new JLabel(" ");
	private final JTextField description = new JTextField(50);
	private final JButton save = new JButton("Save network");
	private final JButton newNet = new JButton("New network");
	private final JButton deleteNet = new JButton("Delete network...");
	private final JLabel status = new JLabel(" ");

	private final JButton addStop = new JButton("Add a stop...");
	private final JButton addSelected = new JButton("Add the selected transport");
	private final JButton removeRow = new JButton("Take out of this network");

	/** The last tile clicked on the map (fills in a new stop's tile), or null. */
	private int[] clickedTile;

	/** The network the form shows (its name as saved), NEW for a new one, or null. */
	private String editing;
	private boolean refreshing;

	NetworksPanel(EditorContext ctx, Runnable recompute)
	{
		super(new BorderLayout(4, 4));
		this.ctx = ctx;
		this.recompute = recompute;
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		netTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		netTable.getSelectionModel().addListSelectionListener(e ->
		{
			int row = netTable.getSelectedRow();
			if (!refreshing && !e.getValueIsAdjusting() && row >= 0)
			{
				load(nets.rows.get(row).name);
			}
		});
		netTable.getColumnModel().getColumn(0).setPreferredWidth(140);
		netTable.getColumnModel().getColumn(1).setPreferredWidth(50);
		netTable.getColumnModel().getColumn(2).setPreferredWidth(70);
		netTable.getColumnModel().getColumn(3).setPreferredWidth(420);

		JPanel left = new JPanel(new BorderLayout(4, 4));
		left.add(new JLabel("Networks (data/manual/networks.tsv): click one to see and change it"), BorderLayout.NORTH);
		left.add(new JScrollPane(netTable), BorderLayout.CENTER);
		left.add(form(), BorderLayout.SOUTH);

		memberTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		memberTable.getSelectionModel().addListSelectionListener(e ->
		{
			int row = memberTable.getSelectedRow();
			if (!refreshing && !e.getValueIsAdjusting() && row >= 0)
			{
				ctx.showRecord(members.rows.get(row));
			}
			updateButtons();
		});
		memberTable.getColumnModel().getColumn(0).setPreferredWidth(220);
		memberTable.getColumnModel().getColumn(5).setPreferredWidth(300);
		membersTitle.setFont(membersTitle.getFont().deriveFont(Font.BOLD));
		JPanel right = new JPanel(new BorderLayout(4, 4));
		right.add(membersTitle, BorderLayout.NORTH);
		right.add(new JScrollPane(memberTable), BorderLayout.CENTER);
		JPanel memberButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		memberButtons.add(addStop);
		memberButtons.add(addSelected);
		memberButtons.add(removeRow);
		memberButtons.add(new JLabel("(click a row to show it on the map; Ctrl+Z undoes)"));
		right.add(memberButtons, BorderLayout.SOUTH);
		addStop.addActionListener(e -> addStopDialog());
		addSelected.addActionListener(e -> addSelectedTransport());
		removeRow.addActionListener(e -> removeSelectedRow());

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		split.setResizeWeight(0.5);
		split.setBorder(null);
		add(split, BorderLayout.CENTER);
		ctx.addSelectionListener(this::updateButtons);
		refresh();
	}

	private JPanel form()
	{
		JPanel p = new JPanel(new GridBagLayout());
		p.setBorder(BorderFactory.createTitledBorder("Selected network"));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 4, 2, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		nameRow.add(name);
		nameRow.add(new JLabel("   Extra ticks on every ride:"));
		nameRow.add(extra);
		row(p, c, 0, "Name:", nameRow);
		row(p, c, 1, "Every ride needs:", needs);
		row(p, c, 2, "", needsCheck);
		row(p, c, 3, "What belongs in it:", description);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		buttons.add(save);
		buttons.add(newNet);
		buttons.add(deleteNet);
		buttons.add(status);
		row(p, c, 4, "", buttons);

		extra.setToolTipText("Added to each ride's own time (waiting, talking, the interface)");
		needs.setToolTipText("A requirement line, as in the Needs box: e.g. members; quest Fairytale I - Growing Pains. Empty: nothing.");
		save.setToolTipText("Saves the network to data/manual/networks.tsv. Renaming also renames it on every stop and ride.");
		save.addActionListener(e -> saveNetwork());
		newNet.addActionListener(e ->
		{
			netTable.clearSelection();
			editing = NEW;
			name.setText("");
			extra.setValue(0);
			needs.setText("members");
			description.setText("");
			status.setText("Type a name and press Save network");
			showMembers();
			name.requestFocusInWindow();
		});
		deleteNet.addActionListener(e -> deleteNetwork());
		needs.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				checkNeeds();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				checkNeeds();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				checkNeeds();
			}
		});
		return p;
	}

	/** One labelled row of a GridBag form. */
	static void row(JPanel p, GridBagConstraints c, int y, String label, java.awt.Component field)
	{
		c.gridy = y;
		c.gridx = 0;
		c.weightx = 0;
		p.add(new JLabel(label), c);
		c.gridx = 1;
		c.weightx = 1;
		p.add(field, c);
	}

	/** Re-reads the networks and the transports (after every change). */
	void refresh()
	{
		refreshing = true;
		Map<String, List<TransportRecord>> byNet = membersByNetwork();
		List<NetRow> rows = new ArrayList<>();
		for (Networks.Network n : Networks.all())
		{
			List<TransportRecord> m = byNet.remove(n.name.toLowerCase(Locale.ROOT));
			rows.add(new NetRow(n.name, n, m == null ? List.of() : m));
		}
		// used by transports but missing from networks.tsv: shown so they can be added
		for (List<TransportRecord> m : byNet.values())
		{
			rows.add(new NetRow(m.get(0).network, null, m));
		}
		nets.rows = rows;
		nets.fireTableDataChanged();
		int sel = -1;
		for (int i = 0; i < rows.size() && editing != null; i++)
		{
			if (rows.get(i).name.equalsIgnoreCase(editing))
			{
				sel = i;
			}
		}
		if (sel >= 0)
		{
			netTable.setRowSelectionInterval(sel, sel);
			netTable.scrollRectToVisible(netTable.getCellRect(sel, 0, true));
		}
		refreshing = false;
		showMembers();
	}

	private Map<String, List<TransportRecord>> membersByNetwork()
	{
		Map<String, List<TransportRecord>> m = new LinkedHashMap<>();
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			if (!t.deleted && t.record.network != null && !t.record.network.isBlank())
			{
				m.computeIfAbsent(t.record.network.trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(t.record);
			}
		}
		return m;
	}

	/** UI check: shows one network as if its row was clicked. */
	void devShow(String netName)
	{
		editing = netName;
		refresh();
		load(netName);
	}

	/** UI check: the two member buttons, enabled or not, with their text. */
	String devButtons()
	{
		return addSelected.getText() + "=" + addSelected.isEnabled() + ", " + removeRow.getText() + "=" + removeRow.isEnabled();
	}

	/** Fills the form from a network (or a name only used by transports). */
	private void load(String netName)
	{
		editing = netName;
		Networks.Network n = Networks.get(netName);
		name.setText(netName);
		extra.setValue(n == null ? 0 : n.extraTicks);
		needs.setText(n == null ? "members" : n.requirement);
		needs.setCaretPosition(0);
		description.setText(n == null ? "" : n.description);
		description.setCaretPosition(0);
		status.setForeground(Color.DARK_GRAY);
		status.setText(n == null ? "Not in networks.tsv yet: Save network adds it" : " ");
		showMembers();
	}

	private void showMembers()
	{
		boolean wasRefreshing = refreshing;
		refreshing = true;
		List<TransportRecord> m = new ArrayList<>();
		if (editing != null && !NEW.equals(editing))
		{
			List<TransportRecord> found = membersByNetwork().get(editing.toLowerCase(Locale.ROOT));
			if (found != null)
			{
				m.addAll(found);
			}
			m.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		}
		members.rows = m;
		members.extra = Networks.extraTicks(editing);
		members.fireTableDataChanged();
		long stops = m.stream().filter(r -> r.kind == TransportRecord.Kind.NETWORK).count();
		membersTitle.setText(editing == null ? "Click a network on the left to see its stops and rides"
			: NEW.equals(editing) ? "A new network: save it, then add rides to it"
			: editing + ": " + stops + " stop(s) (any stop to any other), " + (m.size() - stops) + " ride(s)");
		refreshing = wasRefreshing;
		updateButtons();
		checkNeeds();
	}

	private void updateButtons()
	{
		boolean real = editing != null && !NEW.equals(editing);
		deleteNet.setEnabled(real);
		addStop.setEnabled(real && Networks.get(editing) != null);
		addStop.setToolTipText(!real ? "Pick a network first" : Networks.get(editing) == null ? "Save the network first"
			: "A new stop of " + editing + " (any stop to any other): its name, tile, ride ticks and what it needs. "
			+ "Click the stop's tile on the map first (for a fairy ring: the middle of the ring).");
		EditorContext.Selection s = ctx.getSelection();
		TransportRecord sel = s == null ? null : s.record;
		boolean canAdd = real && Networks.get(editing) != null && sel != null && sel.kind != TransportRecord.Kind.NETWORK
			&& sel.kind != TransportRecord.Kind.TELEPORT && !editing.equalsIgnoreCase(sel.network == null ? "" : sel.network.trim());
		addSelected.setEnabled(canAdd);
		addSelected.setText(sel != null && canAdd ? "Add \"" + shorten(sel.name) + "\"" : "Add the selected transport");
		addSelected.setToolTipText(!real ? "Pick a network first"
			: Networks.get(editing) == null ? "Save the network first"
			: sel == null ? "Click a transport on the map (or in a list) first"
			: sel.kind == TransportRecord.Kind.NETWORK ? "A network stop belongs to its own network"
			: sel.kind == TransportRecord.Kind.TELEPORT ? "Teleports use a teleport category, not a network"
			: "Make the transport selected on the map a ride of " + editing
				+ (sel.network == null || sel.network.isBlank() ? "" : " (it leaves " + sel.network + ")"));
		// take out: the transport selected on the map (or by clicking a row), when it is a ride of this network
		boolean inThis = real && sel != null && sel.network != null && editing.equalsIgnoreCase(sel.network.trim());
		boolean canRemove = inThis && sel.kind != TransportRecord.Kind.NETWORK;
		removeRow.setEnabled(canRemove);
		removeRow.setText(canRemove ? "Take \"" + shorten(sel.name) + "\" out" : "Take out of this network");
		removeRow.setToolTipText(!real ? "Pick a network first"
			: sel == null ? "Click one of its rides (in the list or on the map) first"
			: !inThis ? "The selected transport is not in " + editing
			: !canRemove ? "A network stop cannot leave its network: delete the stop instead"
			: "The selected ride leaves " + editing + ": it no longer needs the network's requirement or costs its extra ticks");
	}

	/** The map tile last clicked (MainWindow): a new stop's tile starts there. */
	void tileClicked(int x, int y, int plane)
	{
		clickedTile = new int[]{x, y, plane};
	}

	/**
	 * A new stop of a network (any stop to any other), as the "Add a stop..." dialog makes it: kind NETWORK,
	 * category TRAVEL, start and end on {@code tile}, the ride ticks (the network's extra ticks are added
	 * when routes use it), its own requirement line (may be empty) and where it comes from.
	 */
	static TransportRecord newStop(String network, String name, int[] tile, int ticks, String requirements, String source)
	{
		TransportRecord r = new TransportRecord();
		r.id = com.bestpath.tool.edit.EditStore.newId("t");
		r.kind = TransportRecord.Kind.NETWORK;
		r.name = name.trim();
		r.network = network;
		r.category = "TRAVEL";
		r.startX = tile[0];
		r.startY = tile[1];
		r.startPlane = tile[2];
		r.endX = tile[0];
		r.endY = tile[1];
		r.endPlane = tile[2];
		r.ticks = ticks;
		r.ticksEstimate = false;
		r.requirements = requirements == null ? "" : requirements.trim();
		String from = source == null ? "" : source.replace('\t', ' ').trim();
		r.source = "manual: network stop added in the Networks tab" + (from.isEmpty() ? "" : "; " + from);
		return r;
	}

	/** The ride ticks most stops of a network have (a new stop starts with them); 10 if it has none. */
	private int usualTicks(String netName)
	{
		Map<Integer, Integer> count = new LinkedHashMap<>();
		for (TransportRecord r : membersByNetwork().getOrDefault(netName.toLowerCase(Locale.ROOT), List.of()))
		{
			if (r.kind == TransportRecord.Kind.NETWORK)
			{
				count.merge(r.ticks, 1, Integer::sum);
			}
		}
		int best = 10;
		int most = 0;
		for (Map.Entry<Integer, Integer> e : count.entrySet())
		{
			if (e.getValue() > most)
			{
				most = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	/** "Add a stop...": a form for a new stop of the selected network. */
	private void addStopDialog()
	{
		Networks.Network net = editing == null ? null : Networks.get(editing);
		if (net == null)
		{
			return;
		}
		TransportRecord r = new NetworkStopDialog(ctx, net, clickedTile, usualTicks(net.name)).ask(this);
		if (r == null)
		{
			return;
		}
		ctx.pendTransport(r);
		ctx.showRecord(r);
		showMembers();
		status.setForeground(new Color(0, 120, 0));
		status.setText("Added " + shorten(r.name) + ": check it with a route in the Route tab");
	}

	private static String shorten(String s)
	{
		return s.length() > 40 ? s.substring(0, 39) + "..." : s;
	}

	private void checkNeeds()
	{
		try
		{
			com.bestpath.core.req.Requirement r = ItemGroupFile.parse(needs.getText());
			List<String> unknown = CacheNames.unknownNames(r);
			List<String> words = RequirementWords.parts(r);
			needsCheck.setForeground(unknown.isEmpty() ? new Color(0, 120, 0) : new Color(190, 100, 0));
			needsCheck.setText((unknown.isEmpty() ? "" : "unknown " + String.join(", ", unknown) + ": ")
				+ (words.isEmpty() ? "nothing needed" : String.join("   •   ", words)));
			save.setEnabled(editing != null);
		}
		catch (RequirementText.ParseException ex)
		{
			needsCheck.setForeground(Color.RED);
			needsCheck.setText(ex.getMessage());
			save.setEnabled(false);
		}
	}

	private void saveNetwork()
	{
		String newName = name.getText().trim();
		if (newName.isEmpty() || newName.contains("\t") || newName.contains("|"))
		{
			fail("Give the network a name (no tabs or |)");
			return;
		}
		Networks.Network clash = Networks.get(newName);
		boolean renaming = editing != null && !NEW.equals(editing) && !newName.equalsIgnoreCase(editing);
		if (clash != null && (NEW.equals(editing) || renaming))
		{
			fail("There is already a network called " + clash.name);
			return;
		}
		Networks.Network n = new Networks.Network(newName, (Integer) extra.getValue(),
			CacheNames.tidyQuestNames(needs.getText()).trim(), description.getText().replace('\t', ' ').trim());
		List<Networks.Network> list = Networks.all();
		boolean replaced = false;
		for (int i = 0; i < list.size(); i++)
		{
			if (editing != null && list.get(i).name.equalsIgnoreCase(editing))
			{
				list.set(i, n);
				replaced = true;
			}
		}
		if (!replaced)
		{
			list.add(n);
		}
		try
		{
			Networks.save(list);
		}
		catch (Exception ex)
		{
			fail("Could not save: " + ex.getMessage());
			return;
		}
		String old = editing;
		editing = newName;
		if (renaming || (old != null && !NEW.equals(old) && !old.equals(newName)))
		{
			// every stop and ride follows the new name
			List<TransportRecord> m = membersByNetwork().getOrDefault(old.toLowerCase(Locale.ROOT), List.of());
			ctx.edits.beginGesture();
			for (TransportRecord r : m)
			{
				TransportRecord c = r.copy();
				c.network = newName;
				ctx.pendTransport(c);
			}
			ctx.edits.endGesture();
		}
		status.setForeground(new Color(0, 120, 0));
		status.setText("Saved" + (renaming ? ", and renamed on its stops and rides" : ""));
		recompute.run();
		refresh();
	}

	private void deleteNetwork()
	{
		if (editing == null || NEW.equals(editing))
		{
			return;
		}
		List<TransportRecord> m = membersByNetwork().getOrDefault(editing.toLowerCase(Locale.ROOT), List.of());
		long stops = m.stream().filter(r -> r.kind == TransportRecord.Kind.NETWORK).count();
		if (stops > 0)
		{
			fail("It still has " + stops + " stop(s): delete those first (a stop cannot be without its network)");
			return;
		}
		String q = "Delete the network " + editing + "?"
			+ (m.isEmpty() ? "" : "\nIts " + m.size() + " ride(s) stay, but no longer need its requirement or cost its extra ticks.");
		if (JOptionPane.showConfirmDialog(this, q, "Delete network", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
		{
			return;
		}
		List<Networks.Network> list = Networks.all();
		list.removeIf(n -> n.name.equalsIgnoreCase(editing));
		try
		{
			Networks.save(list);
		}
		catch (Exception ex)
		{
			fail("Could not save: " + ex.getMessage());
			return;
		}
		ctx.edits.beginGesture();
		for (TransportRecord r : m)
		{
			TransportRecord c = r.copy();
			c.network = "";
			ctx.pendTransport(c);
		}
		ctx.edits.endGesture();
		editing = null;
		name.setText("");
		needs.setText("");
		description.setText("");
		status.setForeground(Color.DARK_GRAY);
		status.setText("Deleted");
		recompute.run();
		refresh();
	}

	private void addSelectedTransport()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null || s.record == null || !addSelected.isEnabled())
		{
			return;
		}
		TransportRecord c = s.record.copy();
		c.network = Networks.get(editing).name;
		ctx.pendTransport(c);
		ctx.select(EditorContext.Selection.ofRecord(c, true), null, false);
		status.setForeground(new Color(0, 120, 0));
		status.setText("Added " + shorten(c.name));
	}

	private void removeSelectedRow()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null || s.record == null || !removeRow.isEnabled())
		{
			return;
		}
		TransportRecord c = s.record.copy();
		c.network = "";
		ctx.pendTransport(c);
		ctx.select(EditorContext.Selection.ofRecord(c, true), null, false);
		status.setForeground(Color.DARK_GRAY);
		status.setText("Took out " + shorten(c.name));
	}

	private void fail(String why)
	{
		status.setForeground(Color.RED);
		status.setText(why);
	}

	/** A requirement line in words, for showing. */
	static String inWords(String line)
	{
		if (line == null || line.isBlank())
		{
			return "nothing";
		}
		try
		{
			List<String> parts = RequirementWords.parts(ItemGroupFile.parse(line));
			return parts.isEmpty() ? "nothing" : String.join("  •  ", parts);
		}
		catch (RequirementText.ParseException ex)
		{
			return line + "  (CANNOT BE READ)";
		}
	}

	private static final class NetRow
	{
		final String name;
		/** Null when the name is only used by transports (not in networks.tsv). */
		final Networks.Network network;
		final List<TransportRecord> members;

		NetRow(String name, Networks.Network network, List<TransportRecord> members)
		{
			this.name = name;
			this.network = network;
			this.members = members;
		}
	}

	private static final class NetModel extends AbstractTableModel
	{
		private static final String[] COLS = {"Network", "Extra ticks", "Stops / rides", "Every ride needs"};
		List<NetRow> rows = new ArrayList<>();

		@Override
		public int getRowCount()
		{
			return rows.size();
		}

		@Override
		public int getColumnCount()
		{
			return COLS.length;
		}

		@Override
		public String getColumnName(int c)
		{
			return COLS[c];
		}

		@Override
		public Object getValueAt(int row, int col)
		{
			NetRow r = rows.get(row);
			switch (col)
			{
				case 0:
					return r.network == null ? r.name + "  (NOT IN networks.tsv)" : r.name;
				case 1:
					return r.network == null ? "" : String.valueOf(r.network.extraTicks);
				case 2:
					long stops = r.members.stream().filter(m -> m.kind == TransportRecord.Kind.NETWORK).count();
					return stops + " / " + (r.members.size() - stops);
				default:
					return r.network == null ? "" : inWords(r.network.requirement);
			}
		}
	}

	private static final class MemberModel extends AbstractTableModel
	{
		private static final String[] COLS = {"Name", "Type", "From", "To", "Ticks (own + extra)", "Also needs (its own line)"};
		List<TransportRecord> rows = new ArrayList<>();
		int extra;

		@Override
		public int getRowCount()
		{
			return rows.size();
		}

		@Override
		public int getColumnCount()
		{
			return COLS.length;
		}

		@Override
		public String getColumnName(int c)
		{
			return COLS[c];
		}

		@Override
		public Object getValueAt(int row, int col)
		{
			TransportRecord r = rows.get(row);
			switch (col)
			{
				case 0:
					return r.name;
				case 1:
					return r.kind == TransportRecord.Kind.NETWORK ? "stop" : r.kind == TransportRecord.Kind.TWO_WAY ? "ride, two-way" : "ride, one-way";
				case 2:
					return r.kind == TransportRecord.Kind.NETWORK || !r.hasStart() ? "" : r.startX + ", " + r.startY + ", " + r.startPlane;
				case 3:
					return r.endX + ", " + r.endY + ", " + r.endPlane;
				case 4:
					return r.ticks + " + " + extra + " = " + (r.ticks + extra);
				default:
					String own = r.requirements == null || r.requirements.isBlank() ? "" : inWords(r.requirements);
					String back = r.kind == TransportRecord.Kind.TWO_WAY && r.requirementsBack != null
						? "   | back: " + inWords(r.requirementsBack) : "";
					return own + back;
			}
		}
	}
}
