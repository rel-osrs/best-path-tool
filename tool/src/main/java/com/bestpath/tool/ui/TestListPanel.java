package com.bestpath.tool.ui;

import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.transport.TransportCandidate;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Bottom panel: the "test in game" list (pending changes included). Click a
 * row to select it on the map; then Confirm or Delete it once tested.
 */
final class TestListPanel extends JPanel
{
	private final EditorContext ctx;
	private final Model model = new Model();
	private final JTable table = new JTable(model);
	private final JLabel count = new JLabel(" ");
	private boolean refreshing;

	TestListPanel(EditorContext ctx)
	{
		super(new BorderLayout(4, 4));
		this.ctx = ctx;
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		top.add(new JLabel("To test in game:"));
		top.add(count);
		top.add(new JLabel("   (click a row to show it; after testing use Confirm or Delete)"));
		add(top, BorderLayout.NORTH);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(e ->
		{
			int row = table.getSelectedRow();
			if (!refreshing && !e.getValueIsAdjusting() && row >= 0)
			{
				show(model.rows.get(row));
			}
		});
		add(new JScrollPane(table), BorderLayout.CENTER);
	}

	void refresh()
	{
		refreshing = true;
		List<Map.Entry<String, TransportRecord>> rows = new ArrayList<>(ctx.effectiveTestLater().entrySet());
		model.rows = rows;
		model.fireTableDataChanged();
		count.setText(String.format(Locale.ROOT, "%,d", rows.size()));
		refreshing = false;
	}

	int itemCount()
	{
		return model.rows.size();
	}

	private void show(Map.Entry<String, TransportRecord> row)
	{
		if (ctx.transports != null)
		{
			for (TransportCandidate c : ctx.transports.getCandidates())
			{
				if (c.id().equals(row.getKey()))
				{
					ctx.showCandidate(c);
					return;
				}
			}
			for (com.bestpath.tool.transport.CacheTableTeleports.Destination d : ctx.transports.getDestinations())
			{
				if (d.id().equals(row.getKey()))
				{
					ctx.select(ctx.teleportSelection(d), null, false);
					break;
				}
			}
			TransportRecord confirmed = ctx.transports.getConfirmed().get(row.getKey());
			if (confirmed != null)
			{
				ctx.select(EditorContext.Selection.ofRecord(confirmed, false), null, false);
			}
		}
		TransportRecord r = row.getValue();
		if (r != null && !ctx.views().isEmpty())
		{
			int x = r.hasStart() ? r.startX : r.endX;
			int y = r.hasStart() ? r.startY : r.endY;
			int p = r.hasStart() ? r.startPlane : r.endPlane;
			ctx.views().get(0).setPlane(p);
			ctx.views().get(0).showTile(x, y, Math.max(ctx.views().get(0).getViewport().getScale(), 8));
		}
	}

	private static final class Model extends AbstractTableModel
	{
		private static final String[] COLS = {"Name", "From", "To", "Ticks", "Id"};
		List<Map.Entry<String, TransportRecord>> rows = new ArrayList<>();

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
			Map.Entry<String, TransportRecord> e = rows.get(row);
			TransportRecord r = e.getValue();
			switch (col)
			{
				case 0:
					return r != null ? r.name : "";
				case 1:
					return r == null ? "" : r.hasStart() ? r.startX + ", " + r.startY + ", " + r.startPlane : "anywhere";
				case 2:
					return r == null ? "" : r.endX + ", " + r.endY + ", " + r.endPlane;
				case 3:
					return r == null ? "" : String.valueOf(r.ticks);
				default:
					return e.getKey();
			}
		}
	}
}
