package com.bestpath.tool.ui;

import com.bestpath.tool.edit.ChangesCompactor;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.EditStore;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/**
 * Every change, newest first, from data/manual/changes.jsonl (each is saved the moment it is made).
 * "Undo selected" undoes the selected changes at once (Ctrl+Z takes that back). "Compact..." takes the undone and
 * replaced changes out of the file ({@link ChangesCompactor}).
 */
final class ChangeLogPanel extends JPanel
{
	interface JumpTo
	{
		void jumpTo(int x, int y, int plane);
	}

	private final EditStore store;
	private final File workspace;
	private final Model model = new Model();
	private final JTable table = new JTable(model);

	ChangeLogPanel(EditStore store, File workspace, JumpTo jump, java.util.function.Supplier<String> author,
		Runnable changeAuthor)
	{
		super(new BorderLayout(4, 4));
		this.store = store;
		this.workspace = workspace;
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		JButton undo = new JButton("Undo selected");
		undo.setToolTipText("Undoes the selected changes at once (recorded as an undo; Ctrl+Z takes it back)");
		undo.addActionListener(e ->
		{
			// the targets first: each undo adds a row at the top
			List<Edit> targets = new ArrayList<>();
			for (int row : table.getSelectedRows())
			{
				targets.add(model.rows.get(row));
			}
			for (Edit target : targets)
			{
				if (target.type != Edit.Type.REVERT && !store.isReverted(target))
				{
					store.revert(target);
				}
			}
		});
		top.add(undo);
		JButton compact = new JButton("Compact...");
		compact.setToolTipText("Makes the change list smaller: takes out undone changes and changes a later change replaced"
			+ " (a backup is made first; the data stays exactly the same)");
		compact.addActionListener(e -> compact());
		top.add(compact);
		JButton name = new JButton("Change name...");
		name.setToolTipText("The name recorded with your changes");
		JLabel info = new JLabel();
		name.addActionListener(e ->
		{
			changeAuthor.run();
			info.setText(infoText(author.get()));
		});
		info.setText(infoText(author.get()));
		top.add(name);
		top.add(info);
		add(top, BorderLayout.NORTH);
		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int row = table.getSelectedRow();
				if (e.getClickCount() == 2 && row >= 0)
				{
					Edit ed = model.rows.get(row);
					jump.jumpTo(ed.x, ed.y, ed.plane);
				}
			}
		});
		add(new JScrollPane(table), BorderLayout.CENTER);
		store.addListener(this::refresh);
		refresh();
	}

	private String infoText(String author)
	{
		return "   Recorded as \"" + author + "\". Every change is saved at once; Ctrl+Z undoes your last action. Newest first, stored in "
			+ store.getFile().getPath();
	}

	void refresh()
	{
		// newest first by time (after compacting, a kept change sits where the first change to the same thing was)
		List<Edit> applied = store.getApplied();
		List<Integer> order = new ArrayList<>();
		List<Instant> times = new ArrayList<>();
		for (int i = 0; i < applied.size(); i++)
		{
			order.add(i);
			times.add(time(applied.get(i)));
		}
		order.sort(Comparator.<Integer, Instant>comparing(times::get).thenComparing(i -> i).reversed());
		List<Edit> rows = new ArrayList<>();
		order.forEach(i -> rows.add(applied.get(i)));
		model.rows = rows;
		model.fireTableDataChanged();
	}

	private static Instant time(Edit e)
	{
		try
		{
			return e.time == null || e.time.isBlank() ? Instant.EPOCH : Instant.parse(e.time);
		}
		catch (DateTimeParseException ex)
		{
			return Instant.EPOCH;
		}
	}

	/**
	 * Compacts the change list after asking (on the Swing thread, so no change can be made while the file is
	 * replaced; a few seconds).
	 */
	private void compact()
	{
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		try
		{
			ChangesCompactor.Plan plan = ChangesCompactor.plan(
				java.nio.file.Files.readAllLines(store.getFile().toPath(), java.nio.charset.StandardCharsets.UTF_8));
			setCursor(null);
			if (plan.nothingToDo())
			{
				JOptionPane.showMessageDialog(this, "Nothing to take out: every change in the list is in effect.",
					"Compact the change list", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			int ok = JOptionPane.showConfirmDialog(this, "<html><body style='width:420px'>"
				+ plan.summary().replace(System.lineSeparator(), "<br>")
				+ "<br><br>The data stays exactly the same: the tool checks this before it changes anything, and stops if"
				+ " anything would differ. The whole old file is copied to cache/backups first."
				+ "<br><br>Gone from this list afterwards: the undone changes, the older versions of things changed again,"
				+ " and the notes written on those older versions. Ctrl+Z can't undo earlier changes after this.</body></html>",
				"Compact the change list", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (ok != JOptionPane.OK_OPTION)
			{
				return;
			}
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			StringBuilder log = new StringBuilder();
			ChangesCompactor.compact(workspace, store.getFile(), line -> log.append(line).append('\n'));
			store.reload();
			setCursor(null);
			JOptionPane.showMessageDialog(this, log.toString().trim(), "Compact the change list",
				JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception ex)
		{
			setCursor(null);
			JOptionPane.showMessageDialog(this, "Not compacted: " + ex.getMessage(), "Compact the change list",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	private final class Model extends AbstractTableModel
	{
		private final String[] cols = {"When", "Who", "Change", "Status"};
		List<Edit> rows = new ArrayList<>();

		@Override
		public int getRowCount()
		{
			return rows.size();
		}

		@Override
		public int getColumnCount()
		{
			return cols.length;
		}

		@Override
		public String getColumnName(int c)
		{
			return cols[c];
		}

		@Override
		public Object getValueAt(int r, int c)
		{
			Edit e = rows.get(r);
			switch (c)
			{
				case 0:
					return e.time.replace('T', ' ').replaceAll("\\..*$", "").replace("Z", "");
				case 1:
					return e.author;
				case 2:
					return e.describe();
				default:
					return e.type == Edit.Type.REVERT ? "" : store.isReverted(e) ? "undone" : "in effect";
			}
		}
	}
}
