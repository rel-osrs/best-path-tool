package com.bestpath.tool.ui;

import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatDiagnostics;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * The player's boats for the route check (up to five, as in the game): kind,
 * parts and where each one is moored, or capsized, lost or bottled, and how
 * many full boat bottles the player carries. The plugin reads all this from
 * the game (the SAILING_BOAT_n_* varbits, the inventory).
 */
final class BoatsDialog extends JDialog
{
	private static final String[] HEADERS = {"Owned", "Kind", "Hull", "Sails", "Keel", "Helm", "Inoculation station",
		"Eternal brazier", "Teleport focus", "Moored at"};
	private final List<Row> rows = new ArrayList<>();
	private Result result;

	/** What the dialog gives back: the boats, and the full boat bottles in the inventory. */
	static final class Result
	{
		final List<Boat> boats;
		final int bottles;

		Result(List<Boat> boats, int bottles)
		{
			this.boats = boats;
			this.bottles = bottles;
		}
	}

	/** One dock choice: a dock id, or "at sea where the route starts" / "unknown". */
	private static final class Where
	{
		final int id;
		final String text;

		Where(int id, String text)
		{
			this.id = id;
			this.text = text;
		}

		@Override
		public String toString()
		{
			return text;
		}
	}

	private static final class Row
	{
		final JCheckBox owned = new JCheckBox();
		final JComboBox<BoatType> type = new JComboBox<>(BoatType.values());
		final JComboBox<String> hull = new JComboBox<>(Boat.WOODS);
		final JComboBox<String> sail = new JComboBox<>(Boat.WOODS);
		final JComboBox<String> keel = new JComboBox<>(Boat.METALS);
		final JComboBox<String> helm = new JComboBox<>(Boat.METALS);
		final JCheckBox inoculation = new JCheckBox();
		final JCheckBox brazier = new JCheckBox();
		final JComboBox<Boat.Focus> focus = new JComboBox<>(Boat.Focus.values());
		final JComboBox<Where> dock = new JComboBox<>();

		Component[] cells()
		{
			return new Component[]{owned, type, hull, sail, keel, helm, inoculation, brazier, focus, dock};
		}

		void enable()
		{
			boolean on = owned.isSelected();
			for (Component c : cells())
			{
				c.setEnabled(on || c == owned);
			}
			keel.setEnabled(on && type.getSelectedItem() != BoatType.RAFT);
		}

		/** Fills the row from one of the player's boats. */
		void show(Boat b)
		{
			owned.setSelected(true);
			type.setSelectedItem(b.getType());
			hull.setSelectedIndex(b.getHull());
			sail.setSelectedIndex(b.getSail());
			keel.setSelectedIndex(Math.max(0, b.getKeel()));
			helm.setSelectedIndex(b.getHelm());
			inoculation.setSelected(BoatDiagnostics.hasInoculation(b));
			brazier.setSelected(BoatDiagnostics.hasBrazier(b));
			focus.setSelectedItem(b.getFocus());
			for (int i = 0; i < dock.getItemCount(); i++)
			{
				if (dock.getItemAt(i).id == b.getDockId())
				{
					dock.setSelectedIndex(i);
				}
			}
		}
	}

	/** The places a boat can be other than a dock; the docks follow them in the list. */
	private static List<Where> placesBeforeDocks()
	{
		List<Where> places = new ArrayList<>();
		places.add(new Where(Boat.NOWHERE, "Unknown (only brought by a shipwright or focus)"));
		places.add(new Where(Boat.AT_START, "At sea where the route starts (player on it)"));
		// a capsized or lost boat comes back through a shipwright; a bottled one with its bottle in the inventory
		places.add(new Where(Boat.CAPSIZED, "Capsized (a shipwright brings it, coins, +10 ticks)"));
		places.add(new Where(Boat.LOST, "Lost (a shipwright brings it, coins, +10 ticks)"));
		places.add(new Where(Boat.BOTTLED, "Bottled (released at any dock while its bottle is carried)"));
		return places;
	}

	private BoatsDialog(java.awt.Window owner, List<Boat> boats, int bottles, List<Dock> docks)
	{
		super(owner, "Boats (for the route check)", ModalityType.APPLICATION_MODAL);
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 4, 2, 4);
		c.anchor = GridBagConstraints.WEST;
		c.gridy = 0;
		c.gridx = 0;
		grid.add(new JLabel("Boat"), c);
		for (String h : HEADERS)
		{
			c.gridx++;
			grid.add(new JLabel(h), c);
		}
		List<Where> places = placesBeforeDocks();
		int firstDock = places.size();
		Set<Integer> seen = new HashSet<>();
		for (Dock d : docks)
		{
			if (seen.add(d.getId()))
			{
				places.add(new Where(d.getId(), d.getName() + " (level " + d.getLevel() + (d.getShipwright() != null ? ", shipwright" : "") + ")"));
			}
		}
		for (int slot = 1; slot <= 5; slot++)
		{
			Row r = newRow(slot, boats, places, docks.isEmpty() ? -1 : firstDock);
			rows.add(r);
			c.gridy++;
			c.gridx = 0;
			grid.add(new JLabel(Integer.toString(slot)), c);
			for (Component cell : r.cells())
			{
				c.gridx++;
				grid.add(cell, c);
			}
		}
		JLabel note = new JLabel("<html>Speed comes from the hull; sails, keel, helm and facilities decide the restricted waters "
			+ "(cache #2735). A shipwright brings a boat for coins: raft 250, skiff 3,750, sloop 50,000.<br>"
			+ "A capsized or lost boat comes only with a shipwright; a bottled one only while its bottle is carried "
			+ "(released at any dock, 10 ticks, no coins).</html>");
		note.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
		getContentPane().add(note, BorderLayout.NORTH);
		getContentPane().add(grid, BorderLayout.CENTER);
		getContentPane().add(bottomBar(bottles), BorderLayout.SOUTH);
		pack();
		setLocationRelativeTo(owner);
	}

	/**
	 * The row for boat {@code slot}: filled from the player's boat in that slot, or empty and moored at
	 * {@code places[firstDock]} (-1: leave the place as it is).
	 */
	private static Row newRow(int slot, List<Boat> boats, List<Where> places, int firstDock)
	{
		Row r = new Row();
		for (Where w : places)
		{
			r.dock.addItem(w);
		}
		Boat b = null;
		for (Boat x : boats)
		{
			if (x.getSlot() == slot)
			{
				b = x;
			}
		}
		if (b != null)
		{
			r.show(b);
		}
		else if (firstDock >= 0)
		{
			r.dock.setSelectedIndex(firstDock); // the first dock (Port Sarim)
		}
		r.owned.addActionListener(e -> r.enable());
		r.type.addActionListener(e -> r.enable());
		r.hull.setToolTipText("Sets the speed: wooden/oak 1.5, teak/mahogany 2.0, camphor/ironwood 2.5, rosewood 3.0 tiles per tick");
		r.sail.setToolTipText("Oak or better: may enter Tempor Tantrum");
		r.keel.setToolTipText("Adamant or better: may enter the Crystalline waters (rafts have no keel)");
		r.helm.setToolTipText("Adamant or better: may enter the kelp area");
		r.inoculation.setToolTipText("May enter fetid water");
		r.brazier.setToolTipText("May enter the northern icy seas");
		r.focus.setToolTipText("<html>Teleport focus: the boat can be brought to any dock (as a teleport).<br>"
			+ "Greater: also a teleport from anywhere to the boat's dock.</html>");
		r.enable();
		return r;
	}

	/** The boat bottles carried on the left, OK and Cancel on the right. */
	private JPanel bottomBar(int bottles)
	{
		// the full boat bottles in the inventory (every bottle is the same item: one per bottled boat is needed)
		JSpinner bottleCount = new JSpinner(new SpinnerNumberModel(Math.max(0, Math.min(5, bottles)), 0, 5, 1));
		bottleCount.setToolTipText("<html>Full boat bottles (item " + Boat.BOTTLE_FULL + ") in the test player's inventory, added to any it"
			+ " already carries.<br>Every bottle is the same item, so bottled boats are used only with one bottle per bottled"
			+ " boat.</html>");
		JPanel bottleRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		bottleRow.add(new JLabel("Full boat bottles in the inventory:"));
		bottleRow.add(bottleCount);
		JButton ok = new JButton("OK");
		JButton cancel = new JButton("Cancel");
		ok.addActionListener(e -> accept((Integer) bottleCount.getValue()));
		cancel.addActionListener(e -> dispose());
		JPanel buttons = new JPanel(new BorderLayout());
		JPanel okCancel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		okCancel.add(ok);
		okCancel.add(cancel);
		buttons.add(bottleRow, BorderLayout.WEST);
		buttons.add(okCancel, BorderLayout.EAST);
		return buttons;
	}

	/** OK: the owned boats as the form shows them, and the bottles. */
	private void accept(int bottles)
	{
		List<Boat> out = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++)
		{
			Row r = rows.get(i);
			if (!r.owned.isSelected())
			{
				continue;
			}
			out.add(new Boat(i + 1, (BoatType) r.type.getSelectedItem(), r.hull.getSelectedIndex(), r.sail.getSelectedIndex(),
				r.keel.getSelectedIndex(), r.helm.getSelectedIndex(), r.inoculation.isSelected(), r.brazier.isSelected(),
				(Boat.Focus) r.focus.getSelectedItem(), ((Where) r.dock.getSelectedItem()).id));
		}
		result = new Result(out, bottles);
		dispose();
	}

	/** Shows the dialog; the new boats and bottles, or null when cancelled. */
	static Result edit(Component parent, List<Boat> boats, int bottles, List<Dock> docks)
	{
		BoatsDialog d = new BoatsDialog(javax.swing.SwingUtilities.getWindowAncestor(parent), boats, bottles, docks);
		d.setVisible(true);
		return d.result;
	}
}
