package com.bestpath.tool.ui;

import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.transport.Suggestion;
import com.bestpath.tool.transport.TransportCandidate;
import com.bestpath.tool.transport.TransportData;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

/**
 * Bottom panel: every transport candidate, filterable; clicking a row selects it and shows it on the map. "Your
 * transports (all)" lists every transport in use instead, so none can hide in the data; "Needs fixing" lists your
 * own transports in category {@link TransportRecord#NEEDS_FIX}.
 */
final class TransportListPanel extends JPanel
{
	interface Pick
	{
		void pick(TransportCandidate c);
	}

	interface PickRecord
	{
		void pick(TransportRecord r);
	}

	private static final String ALL = "All categories";
	private static final String NEEDS_FIX = "Needs fixing (your transports)";
	private static final String YOURS = "Your transports (all)";
	private final PickRecord pickRecord;
	private final Supplier<List<EditorContext.EffectiveTransport>> records;
	private final JComboBox<Object> category = new JComboBox<>();
	private final JComboBox<String> suggestionFilter = new JComboBox<>(new String[]{
		"With or without suggestion", "With a suggestion", "Without a suggestion"});
	private final JTextField search = new JTextField(14);
	private final JLabel count = new JLabel(" ");
	private final Model model = new Model();
	private final JTable table = new JTable(model);
	private TransportData data;

	TransportListPanel(Pick pick, PickRecord pickRecord, Supplier<List<EditorContext.EffectiveTransport>> records)
	{
		super(new BorderLayout(4, 4));
		this.pickRecord = pickRecord;
		this.records = records;
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		category.addItem(ALL);
		for (TransportCandidate.Category c : TransportCandidate.Category.values())
		{
			category.addItem(c);
		}
		category.addItem(YOURS);
		category.addItem(NEEDS_FIX);
		JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		filters.add(new JLabel("Transports:"));
		filters.add(category);
		filters.add(suggestionFilter);
		filters.add(new JLabel("Name:"));
		filters.add(search);
		filters.add(count);
		filters.add(new JLabel("   (click a row to select it and show it on the map)"));
		add(filters, BorderLayout.NORTH);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setAutoCreateRowSorter(true);
		// one click (or the arrow keys) selects the row and shows it
		table.getSelectionModel().addListSelectionListener(e ->
		{
			int viewRow = table.getSelectedRow();
			if (!e.getValueIsAdjusting() && viewRow >= 0)
			{
				pickRow(pick, model.rows.get(table.convertRowIndexToModel(viewRow)));
			}
		});
		// clicking the row that is already selected shows it again
		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				int viewRow = table.rowAtPoint(e.getPoint());
				if (viewRow >= 0 && viewRow == table.getSelectedRow())
				{
					pickRow(pick, model.rows.get(table.convertRowIndexToModel(viewRow)));
				}
			}
		});
		add(new JScrollPane(table), BorderLayout.CENTER);
		category.addActionListener(e -> refilter());
		suggestionFilter.addActionListener(e -> refilter());
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refilter();
			}
		});
	}

	void setData(TransportData data)
	{
		this.data = data;
		refilter();
	}

	/** Shows the "Needs fixing" list again after your transports changed (the candidate list follows {@link #setData}). */
	void refresh()
	{
		if (category.getSelectedItem() == NEEDS_FIX || category.getSelectedItem() == YOURS)
		{
			refilter();
		}
	}

	private void pickRow(Pick pick, Object row)
	{
		if (row instanceof TransportRecord)
		{
			pickRecord.pick((TransportRecord) row);
		}
		else
		{
			pick.pick((TransportCandidate) row);
		}
	}

	private void refilter()
	{
		List<Object> rows = new ArrayList<>();
		if (category.getSelectedItem() == NEEDS_FIX || category.getSelectedItem() == YOURS)
		{
			// your own transports: all of them, or those in the "needs fixing" category (the suggestion filter does not
			// apply); the name box also finds an id, a category or a network
			boolean all = category.getSelectedItem() == YOURS;
			String text = search.getText().trim().toLowerCase(Locale.ROOT);
			for (EditorContext.EffectiveTransport t : records.get())
			{
				if (!t.deleted && (all || TransportRecord.NEEDS_FIX.equals(t.record.category)) && (text.isEmpty() || matches(t.record, text)))
				{
					rows.add(t.record);
				}
			}
		}
		else if (data != null)
		{
			Object cat = category.getSelectedItem();
			int sug = suggestionFilter.getSelectedIndex();
			String text = search.getText().trim().toLowerCase(Locale.ROOT);
			for (TransportCandidate c : data.getCandidates())
			{
				boolean has = !data.suggestionsFor(c).isEmpty();
				if ((cat != ALL && c.getCategory() != cat) || (sug == 1 && !has) || (sug == 2 && has)
					|| (!text.isEmpty() && !c.getName().toLowerCase(Locale.ROOT).contains(text)))
				{
					continue;
				}
				rows.add(c);
			}
		}
		model.rows = rows;
		model.fireTableDataChanged();
		count.setText(String.format(Locale.ROOT, "%,d shown", rows.size()));
	}

	/** A transport's category as the dropdown names it ("CLIMB_UP": "Climb up"); others as stored (CUSTOM, TELEPORT). */
	private static String categoryLabel(String category)
	{
		for (TransportCandidate.Category c : TransportCandidate.Category.values())
		{
			if (c.name().equals(category))
			{
				return c.getLabel();
			}
		}
		return category == null ? "" : category.charAt(0) + category.substring(1).toLowerCase(Locale.ROOT);
	}

	private static boolean matches(TransportRecord r, String text)
	{
		return r.name.toLowerCase(Locale.ROOT).contains(text) || r.id.toLowerCase(Locale.ROOT).contains(text)
			|| r.category != null && (r.category.toLowerCase(Locale.ROOT).contains(text)
				|| categoryLabel(r.category).toLowerCase(Locale.ROOT).contains(text))
			|| r.network != null && r.network.toLowerCase(Locale.ROOT).contains(text);
	}

	private final class Model extends AbstractTableModel
	{
		private final String[] cols = {"Category", "Name", "Location (x, y, plane)", "Options", "Best suggestion", "Suggested destination"};
		List<Object> rows = new ArrayList<>();

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
		public Object getValueAt(int r, int col)
		{
			if (rows.get(r) instanceof TransportRecord)
			{
				TransportRecord t = (TransportRecord) rows.get(r);
				switch (col)
				{
					case 0:
						return TransportRecord.NEEDS_FIX.equals(t.category) ? "Needs fixing"
							: categoryLabel(t.category) + (t.network == null || t.network.isEmpty() ? "" : " (" + t.network + ")");
					case 1:
						return t.name;
					case 2:
						return t.hasStart() ? t.startX + ", " + t.startY + ", " + t.startPlane : "";
					case 3:
						return t.kind.getLabel();
					case 4:
						return "your transport (" + t.id + ")";
					default:
						return t.endX + ", " + t.endY + ", " + t.endPlane;
				}
			}
			TransportCandidate c = (TransportCandidate) rows.get(r);
			List<Suggestion> s = data.suggestionsFor(c);
			Suggestion best = s.isEmpty() ? null : s.get(0);
			switch (col)
			{
				case 0:
					return c.getCategory().getLabel();
				case 1:
					return c.getName();
				case 2:
					return c.getX() + ", " + c.getY() + ", " + c.getPlane();
				case 3:
					return String.join(", ", c.getOptions());
				case 4:
					return best == null ? "none" : best.getRule().getDescription();
				default:
					return best == null ? "" : best.getDestX() + ", " + best.getDestY() + ", " + best.getDestPlane();
			}
		}
	}
}
