package com.bestpath.tool.ui;

import com.bestpath.tool.req.Networks;
import com.bestpath.tool.req.TeleportCategories;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * The tick cost of each teleport category, saved to
 * {@code data/manual/teleport-categories.tsv}; every teleport in a category
 * costs that many ticks. Below them, each transport network's extra ticks on
 * top of every ride's own, saved to {@code data/manual/networks.tsv}.
 */
final class TeleportCostsDialog extends JDialog
{
	private boolean saved;
	private final List<TeleportCategories.Category> cats = TeleportCategories.all();
	/** One spinner per category, in the order of {@link #cats}. */
	private final List<JSpinner> spinners = new ArrayList<>();
	private final List<Networks.Network> nets;
	/** One spinner per network, in the order of {@link #nets}. */
	private final List<JSpinner> extras = new ArrayList<>();
	private final JLabel status;

	private TeleportCostsDialog(java.awt.Window owner)
	{
		super(owner, "Teleport and travel costs (ticks)", ModalityType.APPLICATION_MODAL);
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 4, 2, 4);
		c.anchor = GridBagConstraints.WEST;
		c.gridy = 0;
		for (TeleportCategories.Category cat : cats)
		{
			JSpinner sp = new JSpinner(new SpinnerNumberModel(cat.ticks, 0, 200, 1));
			spinners.add(sp);
			addRow(grid, c, cat.name, sp, new JLabel(cat.description));
		}
		nets = Networks.all();
		addNetworkRows(grid, c);
		status = new JLabel(cats.isEmpty() ? "No categories: data/manual/teleport-categories.tsv is missing." : " ");
		JButton ok = new JButton("Save");
		ok.setEnabled(!cats.isEmpty());
		ok.addActionListener(e -> save());
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(e -> dispose());
		JPanel bottom = new JPanel(new BorderLayout());
		bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		bottom.add(status, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(ok);
		buttons.add(cancel);
		bottom.add(buttons, BorderLayout.EAST);
		JLabel note = new JLabel("<html>Every teleport in a category costs its ticks. Starting values come from Shortest Path; "
			+ "change them after checking in game.<br>A network ride costs its own time (from the Wiki or Shortest Path) plus its "
			+ "network's extra.</html>");
		note.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
		getContentPane().add(note, BorderLayout.NORTH);
		getContentPane().add(grid, BorderLayout.CENTER);
		getContentPane().add(bottom, BorderLayout.SOUTH);
		pack();
		setLocationRelativeTo(owner);
	}

	/** One row of the grid: a name, its ticks and a grey description; {@code c} moves to the next row. */
	private static void addRow(JPanel grid, GridBagConstraints c, String name, JSpinner ticks, JLabel what)
	{
		c.gridx = 0;
		grid.add(new JLabel(name), c);
		c.gridx = 1;
		grid.add(ticks, c);
		c.gridx = 2;
		what.setForeground(Color.DARK_GRAY);
		grid.add(what, c);
		c.gridy++;
	}

	/** A heading, then each network's extra ticks. */
	private void addNetworkRows(JPanel grid, GridBagConstraints c)
	{
		if (!nets.isEmpty())
		{
			c.gridx = 0;
			c.gridwidth = 3;
			JLabel head = new JLabel("<html><br><b>Networks</b>: extra ticks on top of each ride's own time</html>");
			grid.add(head, c);
			c.gridwidth = 1;
			c.gridy++;
		}
		for (Networks.Network n : nets)
		{
			JSpinner sp = new JSpinner(new SpinnerNumberModel(n.extraTicks, 0, 200, 1));
			extras.add(sp);
			JLabel what = new JLabel(n.description);
			what.setToolTipText(n.requirement.isEmpty() ? null : "Every ride needs: " + n.requirement);
			addRow(grid, c, n.name, sp, what);
		}
	}

	/** Saves the categories and networks with the new ticks, and closes; a problem shows in the status line. */
	private void save()
	{
		List<TeleportCategories.Category> out = new ArrayList<>();
		for (int i = 0; i < cats.size(); i++)
		{
			TeleportCategories.Category old = cats.get(i);
			out.add(new TeleportCategories.Category(old.name, (Integer) spinners.get(i).getValue(), old.description));
		}
		List<Networks.Network> netsOut = new ArrayList<>();
		for (int i = 0; i < nets.size(); i++)
		{
			Networks.Network old = nets.get(i);
			netsOut.add(new Networks.Network(old.name, (Integer) extras.get(i).getValue(), old.requirement, old.description));
		}
		try
		{
			TeleportCategories.save(out);
			if (!nets.isEmpty())
			{
				Networks.save(netsOut);
			}
			saved = true;
			dispose();
		}
		catch (Exception ex)
		{
			status.setForeground(Color.RED);
			status.setText("Could not save: " + ex.getMessage());
		}
	}

	/** Shows the dialog; true if new costs were saved. */
	static boolean edit(Component parent)
	{
		TeleportCostsDialog d = new TeleportCostsDialog(javax.swing.SwingUtilities.getWindowAncestor(parent));
		d.setVisible(true);
		return d.saved;
	}
}
