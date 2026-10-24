package com.bestpath.tool.ui;

import com.bestpath.core.req.RequirementText;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.req.CacheNames;
import com.bestpath.tool.req.ItemGroupFile;
import com.bestpath.tool.req.Networks;
import com.bestpath.tool.req.TeleportCategories;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Edits what a player needs to use the selected transport: the requirement
 * line (and a separate one for end to start when a two-way transport differs),
 * its source, the "quest only" / "can fail" marks, and whether only a boat
 * can use it. Mistakes in a line show while typing; OK is only possible when
 * every line can be read.
 */
final class RequirementsDialog extends JDialog
{
	private final TransportRecord record;
	private final JTextField forward = new JTextField(60);
	private final JCheckBox backDiffers = new JCheckBox("End to start needs something else:");
	private final JTextField back = new JTextField(60);
	private final JTextField source = new JTextField(60);
	private final JCheckBox questOnly = new JCheckBox("Quest only (a quest instance or battle: never used for normal routes)");
	private final JCheckBox canFail = new JCheckBox("Can fail (an obstacle the player can fail; still its normal ticks)");
	private final javax.swing.JComboBox<String> category = new javax.swing.JComboBox<>();
	private final javax.swing.JComboBox<String> wilderness = new javax.swing.JComboBox<>(new String[]{"no limit", "up to level 20",
		"up to level 30", "not in the Wilderness"});
	private final JCheckBox cooldown = new JCheckBox("Cooldown (home / grouping teleports)");
	private final JTextField playerNote = new JTextField(60);
	private final javax.swing.JComboBox<String> network = new javax.swing.JComboBox<>();
	/** Index = {@link TransportRecord#largestBoat}: 0 walked, else only a boat that wide at most (1 raft, 2 skiff, 3 sloop). */
	private final javax.swing.JComboBox<String> boats = new javax.swing.JComboBox<>(new String[]{"No: the player walks to it",
		"Yes, rafts only", "Yes, rafts and skiffs (no sloops)", "Yes, every boat"});
	private final JLabel status = new JLabel(" ");
	private final JButton ok = new JButton("OK");
	private TransportRecord result;

	private RequirementsDialog(java.awt.Window owner, TransportRecord r)
	{
		super(owner, "Requirements: " + r.name, ModalityType.APPLICATION_MODAL);
		this.record = r;
		boolean twoWay = r.kind == TransportRecord.Kind.TWO_WAY;
		forward.setText(r.requirements == null ? "" : r.requirements);
		backDiffers.setSelected(twoWay && r.requirementsBack != null);
		back.setText(r.requirementsBack == null ? "" : r.requirementsBack);
		source.setText(r.requirementsSource == null ? "" : r.requirementsSource);
		questOnly.setSelected(r.questOnly);
		canFail.setSelected(r.canFail);
		JPanel grid = buildGrid(r, twoWay);

		DocumentListener recheck = new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				check();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				check();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				check();
			}
		};
		forward.getDocument().addDocumentListener(recheck);
		back.getDocument().addDocumentListener(recheck);
		backDiffers.addActionListener(e -> check());

		ok.addActionListener(e -> accept(twoWay));
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(e -> dispose());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(ok);
		buttons.add(cancel);
		getContentPane().add(helpLabel(), BorderLayout.NORTH);
		getContentPane().add(grid, BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);
		check();
		pack();
		setLocationRelativeTo(owner);
	}

	/** The form: requirement lines, source, marks, the teleport or network row, the boat row and the player note. */
	private JPanel buildGrid(TransportRecord r, boolean twoWay)
	{
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 4, 3, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		row(grid, c, 0, new JLabel(twoWay ? "Needs (start to end, and back unless set below):" : "Needs:"), forward);
		if (twoWay)
		{
			row(grid, c, 1, backDiffers, back);
		}
		row(grid, c, 2, new JLabel("Source:"), source);
		c.gridx = 1;
		c.gridy = 3;
		grid.add(questOnly, c);
		c.gridy = 4;
		grid.add(canFail, c);
		category.addItem("(own ticks)");
		for (TeleportCategories.Category tc : TeleportCategories.all())
		{
			category.addItem(tc.name);
		}
		category.setSelectedItem(r.teleportCategory == null || r.teleportCategory.isEmpty() ? "(own ticks)" : r.teleportCategory);
		wilderness.setSelectedIndex(r.wildernessLimit == null ? 0 : r.wildernessLimit == 0 ? 3 : r.wildernessLimit <= 20 ? 1 : 2);
		cooldown.setSelected(r.cooldown);
		if (r.kind == TransportRecord.Kind.TELEPORT)
		{
			c.gridy = 5;
			grid.add(teleportRow(), c);
		}
		else
		{
			c.gridy = 5;
			grid.add(networkRow(r), c);
			if (r.kind == TransportRecord.Kind.TWO_WAY || r.kind == TransportRecord.Kind.ONE_WAY)
			{
				// e.g. the Wyrmscraig cave: a boat sails through it from water to water, the player stays on the boat
				boats.setSelectedIndex(Math.max(0, Math.min(3, r.largestBoat)));
				boats.setToolTipText("Only the sailing search uses a boat-only transport: both ends on water, the player stays on the boat");
				row(grid, c, 6, new JLabel("Only a boat can use it:"), boats);
			}
		}
		playerNote.setText(r.playerNote == null ? "" : r.playerNote);
		playerNote.setToolTipText("Shown to the player on this step; for things the route search does not track (weight, no weapons, landing damage)");
		row(grid, c, 7, new JLabel("Note for the player:"), playerNote);
		c.gridx = 1;
		c.gridy = 8;
		grid.add(status, c);
		return grid;
	}

	/** A teleport's category, Wilderness limit and cooldown. */
	private JPanel teleportRow()
	{
		JPanel tp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tp.add(new JLabel("Teleport category:"));
		tp.add(category);
		tp.add(new JLabel("  Wilderness:"));
		tp.add(wilderness);
		tp.add(cooldown);
		return tp;
	}

	/**
	 * The network the transport is a ride of (charter ship, canoe...): it also needs the network's requirement, and costs
	 * its extra ticks.
	 */
	private JPanel networkRow(TransportRecord r)
	{
		network.addItem("(none)");
		for (Networks.Network n : Networks.all())
		{
			network.addItem(n.name);
		}
		if (r.network != null && !r.network.isEmpty() && Networks.get(r.network) == null)
		{
			network.addItem(r.network); // not in data/manual/networks.tsv (yet)
		}
		network.setSelectedItem(r.network == null || r.network.isEmpty() ? "(none)" : r.network);
		Networks.Network known = Networks.get(r.network);
		JPanel np = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		np.add(new JLabel("Network:"));
		np.add(network);
		JLabel netNeeds = new JLabel(known == null || known.requirement.isEmpty() ? "" : "  every ride needs: " + known.requirement);
		network.addActionListener(e ->
		{
			Networks.Network n = Networks.get((String) network.getSelectedItem());
			netNeeds.setText(n == null || n.requirement.isEmpty() ? "" : "  every ride needs: " + n.requirement);
			pack();
		});
		np.add(netNeeds);
		return np;
	}

	/** How to write a requirement line, with the rune kinds and item groups there are. */
	private static JLabel helpLabel()
	{
		JLabel help = new JLabel("<html>Parts joined with <b>;</b> are all needed; <b>one of (A | B)</b> needs one. Parts:<br>"
			+ "<tt>Agility 61</tt> &nbsp; <tt>quest Regicide</tt> (or add <tt>started</tt>, <tt>not started</tt>, <tt>not done</tt>) &nbsp; "
			+ "<tt>varbit 1990 &gt; 39</tt> / <tt>varp 139 &gt;= 7</tt> (= != &gt; &gt;= &lt; &lt;= &amp;)<br>"
			+ "<tt>item light source</tt> / <tt>item 1755/1756 x2 worn used up</tt> &nbsp; <tt>coins 200</tt> &nbsp; <tt>members</tt> &nbsp; "
			+ "<tt>empty head</tt> &nbsp; <tt>answer &lt;what the player says&gt;</tt> &nbsp; "
			+ "<tt>house</tt> / <tt>house at Yanille</tt> / <tt>house has ornate jewellery box</tt><br>"
			+ "<tt>spellbook ancient</tt> &nbsp; <tt>rune law x1</tt> (runes: " + String.join(", ", ItemGroupFile.runeKinds())
			+ "; combination runes and worn staves count)<br>"
			+ "Item groups (data/manual/item-groups.tsv): " + String.join(", ", ItemGroupFile.groups().keySet()) + "</html>");
		help.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
		return help;
	}

	/** OK: the result is a copy of the transport with the form's values. */
	private void accept(boolean twoWay)
	{
		TransportRecord out = record.copy();
		out.requirements = CacheNames.tidyQuestNames(forward.getText());
		out.requirementsBack = twoWay && backDiffers.isSelected() ? CacheNames.tidyQuestNames(back.getText()) : null;
		out.requirementsSource = source.getText().trim();
		out.questOnly = questOnly.isSelected();
		out.canFail = canFail.isSelected();
		out.playerNote = playerNote.getText().trim();
		if (record.kind == TransportRecord.Kind.TELEPORT)
		{
			Object cat = category.getSelectedItem();
			out.teleportCategory = cat == null || "(own ticks)".equals(cat) ? "" : cat.toString();
			int wi = wilderness.getSelectedIndex();
			out.wildernessLimit = wi == 0 ? null : wi == 1 ? Integer.valueOf(20) : wi == 2 ? Integer.valueOf(30) : Integer.valueOf(0);
			out.cooldown = cooldown.isSelected();
		}
		else
		{
			Object net = network.getSelectedItem();
			out.network = net == null || "(none)".equals(net) ? "" : net.toString();
			out.largestBoat = record.kind == TransportRecord.Kind.TWO_WAY || record.kind == TransportRecord.Kind.ONE_WAY
				? boats.getSelectedIndex() : 0;
		}
		result = out;
		dispose();
	}

	private static void row(JPanel grid, GridBagConstraints c, int y, Component label, Component field)
	{
		c.gridy = y;
		c.gridx = 0;
		c.weightx = 0;
		grid.add(label, c);
		c.gridx = 1;
		c.weightx = 1;
		grid.add(field, c);
	}

	/** Reads both lines; shows the first mistake, or how they will be saved. */
	private void check()
	{
		back.setEnabled(backDiffers.isSelected());
		String which = "Needs: ";
		try
		{
			com.bestpath.core.req.Requirement fr = ItemGroupFile.parse(forward.getText());
			String f = fr.toText();
			which = "End to start: ";
			com.bestpath.core.req.Requirement br = backDiffers.isSelected() ? ItemGroupFile.parse(back.getText()) : null;
			String b = br == null ? null : br.toText();
			java.util.List<String> unknown = new java.util.ArrayList<>(CacheNames.unknownNames(fr));
			if (br != null)
			{
				unknown.addAll(CacheNames.unknownNames(br));
			}
			status.setForeground(unknown.isEmpty() ? new Color(0, 120, 0) : new Color(190, 100, 0));
			status.setText((unknown.isEmpty() ? "OK: " : "OK, but unknown " + String.join(", ", unknown) + ": ")
				+ (f.isEmpty() ? "nothing needed" : f) + (b == null ? "" : "   |   back: " + (b.isEmpty() ? "nothing" : b)));
			ok.setEnabled(true);
		}
		catch (RequirementText.ParseException ex)
		{
			status.setForeground(Color.RED);
			status.setText(which + ex.getMessage());
			ok.setEnabled(false);
		}
	}

	/** Shows the dialog for a transport; a changed copy, or null when cancelled. */
	static TransportRecord edit(Component parent, TransportRecord r)
	{
		RequirementsDialog d = new RequirementsDialog(javax.swing.SwingUtilities.getWindowAncestor(parent), r);
		d.setVisible(true);
		return d.result;
	}
}
