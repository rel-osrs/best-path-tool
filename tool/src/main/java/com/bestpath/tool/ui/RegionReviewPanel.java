package com.bestpath.tool.ui;

import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.geo.Regions;
import com.bestpath.tool.review.RegionReview;
import com.bestpath.tool.review.RegionReview.Category;
import com.bestpath.tool.review.RegionReview.Overall;
import com.bestpath.tool.review.RegionReview.Status;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

/**
 * Bottom panel: the manual check of each 64x64 region. Left, every region
 * with its four checks (transports, varbits, items, sub-regions); right, the
 * selected region: what the tool found there, a status and a note per
 * category. Everything is saved at once (notes after a short pause in typing).
 * Clicking the map while this tab is shown picks the region under the mouse.
 */
final class RegionReviewPanel extends JPanel
{
	/** Colours for the statuses, shared with the map layer. */
	static final Color NEEDS_WORK = new Color(235, 140, 30);
	static final Color COMPLETE = new Color(60, 170, 75);
	static final Color IN_PROGRESS = new Color(220, 200, 40);

	private enum Filter
	{
		ALL("All regions"),
		UNFINISHED("Not complete"),
		NOT_STARTED("Not started"),
		IN_PROGRESS("In progress"),
		NEEDS_WORK("Needs work"),
		COMPLETE("Complete"),
		NOTES("Has notes"),
		NOT_HUMAN("Not reviewed by a person"),
		HUMAN("Reviewed by a person");

		private final String label;

		Filter(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final EditorContext ctx;
	private final RegionReview review;
	private final Supplier<String> author;
	/** Moves the main map to show a region. */
	private final IntConsumer showOnMap;
	private final Runnable repaintMaps;

	private final Model model = new Model();
	private final JTable table = new JTable(model);
	private final TableRowSorter<Model> sorter = new TableRowSorter<>(model);
	private final JComboBox<Filter> filter = new JComboBox<>(Filter.values());
	private final JCheckBox hideIgnored = new JCheckBox("Hide ignored regions", true);
	private final JCheckBox hideEmpty = new JCheckBox("Hide regions with nowhere to walk", true);
	private final JLabel progress = new JLabel(" ");

	private final JLabel title = new JLabel("Pick a region in the list, or click one on the map while this tab is shown");
	private final JCheckBox humanBox = new JCheckBox("Reviewed by a person");
	private final Map<Category, JRadioButton[]> statusButtons = new EnumMap<>(Category.class);
	private final Map<Category, JTextArea> notes = new EnumMap<>(Category.class);
	private final Map<Category, JTextArea> facts = new EnumMap<>(Category.class);
	private final Map<Category, TitledBorder> titles = new EnumMap<>(Category.class);
	private final List<JButton> regionButtons = new ArrayList<>();
	/** Saves the notes shortly after typing stops. */
	private final Timer noteTimer = new Timer(700, e -> saveNotes());

	private Set<Integer> cacheRegions = Set.of();
	private int selected = -1;
	/** True while the panel itself changes the controls (no saving, no map moves). */
	private boolean updating;
	/** True while this panel writes to the review itself (its own changes need no reload). */
	private boolean writing;
	/** Regions with at least one walkable sub-region, for the graph it was worked out from. */
	private Set<Integer> walkable = Set.of();
	private CoarseGraph walkableFor;

	RegionReviewPanel(EditorContext ctx, RegionReview review, Supplier<String> author, IntConsumer showOnMap, Runnable repaintMaps)
	{
		super(new BorderLayout());
		this.ctx = ctx;
		this.review = review;
		this.author = author;
		this.showOnMap = showOnMap;
		this.repaintMaps = repaintMaps;
		noteTimer.setRepeats(false);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildList(), buildDetails());
		split.setResizeWeight(0.42);
		split.setBorder(null);
		add(split, BorderLayout.CENTER);
		review.addListener(() ->
		{
			// the rows stay put (a region does not vanish from a filtered list while you work on it)
			if (!model.rows.isEmpty())
			{
				model.fireTableRowsUpdated(0, model.rows.size() - 1);
			}
			updateProgress();
			repaintMaps.run();
			if (!writing)
			{
				reloadSelected();
			}
		});
		showRegion(-1, false);
	}

	private JPanel buildList()
	{
		JPanel p = new JPanel(new BorderLayout(4, 4));
		p.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));
		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		top.add(new JLabel("Show:"));
		top.add(filter);
		hideIgnored.setToolTipText("Leave out regions marked with the Ignore region tool (they are not exported)");
		hideEmpty.setToolTipText("Leave out regions with no walkable sub-region on any plane (open sea, empty map squares)");
		top.add(hideIgnored);
		top.add(hideEmpty);
		filter.addActionListener(e -> refresh());
		hideIgnored.addActionListener(e -> refresh());
		hideEmpty.addActionListener(e -> refresh());
		p.add(top, BorderLayout.NORTH);

		table.setRowSorter(sorter);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setDefaultRenderer(Status.class, new StatusRenderer());
		table.setDefaultRenderer(Overall.class, new StatusRenderer());
		table.getColumnModel().getColumn(0).setPreferredWidth(60);
		table.getColumnModel().getColumn(1).setPreferredWidth(90);
		table.getSelectionModel().addListSelectionListener(e ->
		{
			int row = table.getSelectedRow();
			if (!updating && !e.getValueIsAdjusting() && row >= 0)
			{
				showRegion(model.rows.get(table.convertRowIndexToModel(row)), true);
			}
		});
		p.add(new JScrollPane(table), BorderLayout.CENTER);
		progress.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));
		p.add(progress, BorderLayout.SOUTH);
		return p;
	}

	private JScrollPane buildDetails()
	{
		JPanel p = new FitWidthPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		p.add(buildHead());
		p.add(buildRegionButtons());

		// two by two, so all four fit the bottom panel's height
		JPanel grid = new JPanel(new GridLayout(2, 2, 6, 4));
		grid.setAlignmentX(LEFT_ALIGNMENT);
		for (Category cat : Category.values())
		{
			grid.add(buildCategory(cat));
		}
		p.add(Box.createVerticalStrut(2));
		p.add(grid);
		p.add(Box.createVerticalGlue());
		JScrollPane scroll = new JScrollPane(p, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setMinimumSize(new Dimension(300, 100));
		return scroll;
	}

	/** The region's title and the "Reviewed by a person" tick box. */
	private JPanel buildHead()
	{
		JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		head.add(title);
		humanBox.setToolTipText("Tick once you have looked at this region yourself (checks filled in by a script still need a person)");
		humanBox.addActionListener(e ->
		{
			if (!updating && selected >= 0)
			{
				saveNotes();
				try
				{
					review.setHumanReviewed(selected, humanBox.isSelected(), author.get());
				}
				catch (Exception ex)
				{
					JOptionPane.showMessageDialog(this, "Could not save the region review:\n" + DownloadDialog.rootMessage(ex),
						"BestPath Data Tool", JOptionPane.ERROR_MESSAGE);
				}
				showWho();
			}
		});
		head.add(humanBox);
		head.setAlignmentX(LEFT_ALIGNMENT);
		return head;
	}

	/** Show on map, Previous, Next, Next not complete, Mark all complete. */
	private JPanel buildRegionButtons()
	{
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		JButton show = new JButton("Show on map");
		show.setToolTipText("Move the main map to this region");
		show.addActionListener(e -> showOnMap.accept(selected));
		JButton prev = new JButton("< Previous");
		prev.setToolTipText("The region above this one in the list");
		prev.addActionListener(e -> step(-1, false));
		JButton next = new JButton("Next >");
		next.setToolTipText("The region below this one in the list");
		next.addActionListener(e -> step(1, false));
		JButton nextOpen = new JButton("Next not complete >>");
		nextOpen.setToolTipText("The next region down the list that is not complete yet");
		nextOpen.addActionListener(e -> step(1, true));
		JButton allDone = new JButton("Mark all complete");
		allDone.setToolTipText("Set all four checks of this region to Complete (notes are kept)");
		allDone.addActionListener(e -> markAll(Status.COMPLETE));
		for (JButton b : new JButton[]{show, prev, next, nextOpen, allDone})
		{
			buttons.add(b);
			regionButtons.add(b);
		}
		return buttons;
	}

	private JPanel buildCategory(Category cat)
	{
		JPanel box = new JPanel(new BorderLayout(6, 2));
		box.setAlignmentX(LEFT_ALIGNMENT);
		TitledBorder titled = BorderFactory.createTitledBorder(cat.getLabel());
		titles.put(cat, titled);
		box.setBorder(BorderFactory.createCompoundBorder(titled, BorderFactory.createEmptyBorder(0, 4, 4, 4)));

		JPanel radios = buildStatusButtons(cat);
		box.add(radios, BorderLayout.NORTH);

		JTextArea found = new JTextArea(2, 20);
		found.setEditable(false);
		found.setFocusable(false);
		found.setLineWrap(true);
		found.setWrapStyleWord(true);
		found.setOpaque(false);
		found.setFont(radios.getFont());
		found.setForeground(Color.DARK_GRAY);
		facts.put(cat, found);
		JTextArea area = buildNotes(cat);
		JPanel right = new JPanel(new BorderLayout(0, 2));
		right.add(found, BorderLayout.NORTH);
		right.add(new JScrollPane(area), BorderLayout.CENTER);
		box.add(right, BorderLayout.CENTER);
		return box;
	}

	/** One radio button per status of the category. */
	private JPanel buildStatusButtons(Category cat)
	{
		JPanel radios = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		ButtonGroup group = new ButtonGroup();
		JRadioButton[] rs = new JRadioButton[Status.values().length];
		for (Status s : Status.values())
		{
			JRadioButton r = new JRadioButton(s.getLabel());
			r.setForeground(s == Status.NEEDS_WORK ? NEEDS_WORK.darker() : s == Status.COMPLETE ? COMPLETE.darker() : r.getForeground());
			r.addActionListener(e -> setStatus(cat, s));
			group.add(r);
			radios.add(r);
			rs[s.ordinal()] = r;
		}
		statusButtons.put(cat, rs);
		return radios;
	}

	/** The category's notes box (saved shortly after typing stops). */
	private JTextArea buildNotes(Category cat)
	{
		JTextArea area = new JTextArea(3, 20);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setToolTipText("Notes about the " + cat.getLabel().toLowerCase(Locale.ROOT) + " of this region (saved as you type)");
		area.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				typed();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				typed();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				typed();
			}
		});
		notes.put(cat, area);
		return area;
	}

	// --- called by the main window ---

	/** The cache's regions (the list shows these, plus any region with a recorded check). */
	void setRegions(Set<Integer> regionIds)
	{
		cacheRegions = regionIds;
		refresh();
	}

	/** Rebuilds the list and the "found here" lines (after a cache opens or a change). */
	void refresh()
	{
		Set<Integer> ignored = hideIgnored.isSelected() ? ctx.effectiveIgnoredRegions() : Set.of();
		boolean onlyWalkable = hideEmpty.isSelected() && ctx.graph != null;
		if (onlyWalkable && walkableFor != ctx.graph)
		{
			walkable = walkableRegions(ctx.graph, cacheRegions);
			walkableFor = ctx.graph;
		}
		Filter f = (Filter) filter.getSelectedItem();
		Set<Integer> all = new TreeSet<>(cacheRegions);
		List<Integer> rows = new ArrayList<>();
		int total = 0;
		for (int id : all)
		{
			if (ignored.contains(id) || (onlyWalkable && !walkable.contains(id)))
			{
				continue;
			}
			total++;
			if (matches(f, id))
			{
				rows.add(id);
			}
		}
		updating = true;
		model.rows = rows;
		model.total = total;
		model.fireTableDataChanged();
		selectInTable(selected);
		updating = false;
		updateProgress();
		showFacts();
	}

	/** Picks a region (from a map click while this tab is shown); the map stays where it is. */
	void pickRegion(int regionId)
	{
		showRegion(regionId, false);
		updating = true;
		selectInTable(regionId);
		updating = false;
	}

	int getSelectedRegion()
	{
		return selected;
	}

	// --- internals ---

	private boolean matches(Filter f, int id)
	{
		switch (f)
		{
			case UNFINISHED:
				return review.overall(id) != Overall.COMPLETE;
			case NOT_STARTED:
				return review.overall(id) == Overall.NOT_STARTED;
			case IN_PROGRESS:
				return review.overall(id) == Overall.IN_PROGRESS;
			case NEEDS_WORK:
				return review.overall(id) == Overall.NEEDS_WORK;
			case COMPLETE:
				return review.overall(id) == Overall.COMPLETE;
			case NOTES:
				return review.hasNotes(id);
			case NOT_HUMAN:
				return !review.isHumanReviewed(id);
			case HUMAN:
				return review.isHumanReviewed(id);
			default:
				return true;
		}
	}

	private static Set<Integer> walkableRegions(CoarseGraph graph, Set<Integer> regionIds)
	{
		Set<Integer> out = new HashSet<>();
		for (int id : regionIds)
		{
			for (int p = 0; p < Regions.PLANES && !out.contains(id); p++)
			{
				if (graph.cellComponentBase(id, p) >= 0 && RegionReviewFacts.maxLabel(graph.cellLabels(id, p)) > 0)
				{
					out.add(id);
				}
			}
		}
		return out;
	}

	private void selectInTable(int regionId)
	{
		int row = model.rows.indexOf(regionId);
		if (row < 0)
		{
			table.clearSelection();
			return;
		}
		int view = table.convertRowIndexToView(row);
		table.setRowSelectionInterval(view, view);
		table.scrollRectToVisible(table.getCellRect(view, 0, true));
	}

	private void showRegion(int regionId, boolean moveMap)
	{
		saveNotes();
		selected = regionId;
		boolean has = regionId >= 0;
		updating = true;
		for (Category cat : Category.values())
		{
			RegionReview.Check c = has ? review.get(regionId, cat) : new RegionReview.Check();
			JRadioButton[] rs = statusButtons.get(cat);
			rs[c.status.ordinal()].setSelected(true);
			for (JRadioButton r : rs)
			{
				r.setEnabled(has);
			}
			JTextArea area = notes.get(cat);
			area.setText(c.note);
			area.setCaretPosition(0);
			area.setEnabled(has);
		}
		humanBox.setSelected(has && review.isHumanReviewed(regionId));
		humanBox.setEnabled(has);
		updating = false;
		regionButtons.forEach(b -> b.setEnabled(has));
		if (has)
		{
			int bx = Regions.baseX(regionId);
			int by = Regions.baseY(regionId);
			boolean ignored = ctx.effectiveIgnoredRegions().contains(regionId);
			title.setText(String.format(Locale.ROOT, "Region %d   (tiles %d-%d, %d-%d)%s", regionId, bx, bx + 63, by, by + 63,
				ignored ? "   - ignored, not exported" : ""));
		}
		showWho();
		showFacts();
		repaintMaps.run();
		if (has && moveMap)
		{
			showOnMap.accept(regionId);
		}
	}

	/**
	 * Shows what someone else (the remote link) just wrote for the selected region, so the
	 * boxes do not save their old text over it later. A note being typed (not yet saved) is kept.
	 */
	private void reloadSelected()
	{
		if (selected < 0)
		{
			return;
		}
		updating = true;
		for (Category cat : Category.values())
		{
			RegionReview.Check c = review.get(selected, cat);
			statusButtons.get(cat)[c.status.ordinal()].setSelected(true);
			JTextArea area = notes.get(cat);
			if (!noteTimer.isRunning() && !area.getText().equals(c.note))
			{
				area.setText(c.note);
				area.setCaretPosition(0);
			}
		}
		humanBox.setSelected(review.isHumanReviewed(selected));
		updating = false;
		showWho();
	}

	/** Who ticked the region, and who set each check (in the check boxes' titles). */
	private void showWho()
	{
		RegionReview.Human h = selected >= 0 ? review.human(selected) : null;
		humanBox.setText(h == null ? "Reviewed by a person" : "Reviewed by a person (" + h.by + ")");
		for (Category cat : Category.values())
		{
			RegionReview.Check c = selected >= 0 ? review.get(selected, cat) : null;
			String by = c != null && !c.author.isBlank() && c.status != Status.NOT_CHECKED ? "   - set by " + c.author : "";
			titles.get(cat).setTitle(cat.getLabel() + by);
		}
		repaint();
	}

	/** What the tool itself found in the selected region, per category (a help for checking, not a verdict). */
	private void showFacts()
	{
		if (selected < 0)
		{
			facts.values().forEach(l -> l.setText(" "));
			return;
		}
		if (ctx.transports == null || ctx.graph == null)
		{
			facts.values().forEach(l -> l.setText("Found here: open a cache to see what the tool found in this region."));
			facts.get(Category.ITEMS).setText(RegionReviewFacts.items());
			return;
		}
		facts.get(Category.TRANSPORTS).setText(RegionReviewFacts.transports(ctx, selected));
		facts.get(Category.VARBITS).setText(RegionReviewFacts.varbits(ctx, selected));
		facts.get(Category.ITEMS).setText(RegionReviewFacts.items());
		facts.get(Category.SUBREGIONS).setText(RegionReviewFacts.subRegions(ctx, selected));
	}

	private void typed()
	{
		if (!updating)
		{
			noteTimer.restart();
		}
	}

	private void setStatus(Category cat, Status s)
	{
		if (updating || selected < 0)
		{
			return;
		}
		saveNotes();
		write(cat, s, null);
	}

	private void markAll(Status s)
	{
		saveNotes();
		for (Category cat : Category.values())
		{
			write(cat, s, null);
		}
		showRegion(selected, false);
	}

	/** Saves the notes now instead of after the typing pause. */
	void saveNotes()
	{
		noteTimer.stop();
		if (selected < 0)
		{
			return;
		}
		for (Category cat : Category.values())
		{
			write(cat, null, notes.get(cat).getText());
		}
	}

	private void write(Category cat, Status s, String note)
	{
		writing = true;
		try
		{
			review.set(selected, cat, s, note, author.get());
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(this, "Could not save the region review:\n" + DownloadDialog.rootMessage(ex),
				"BestPath Data Tool", JOptionPane.ERROR_MESSAGE);
		}
		finally
		{
			writing = false;
		}
	}

	/** Moves up or down the list (as sorted on screen); {@code openOnly}: skip complete regions. */
	private void step(int dir, boolean openOnly)
	{
		saveNotes();
		int n = table.getRowCount();
		if (n == 0)
		{
			return;
		}
		int model0 = model.rows.indexOf(selected);
		int view = model0 < 0 ? (dir > 0 ? -1 : n) : table.convertRowIndexToView(model0);
		for (int i = view + dir; i >= 0 && i < n; i += dir)
		{
			int id = model.rows.get(table.convertRowIndexToModel(i));
			if (!openOnly || review.overall(id) != Overall.COMPLETE)
			{
				table.setRowSelectionInterval(i, i); // shows it and moves the map
				table.scrollRectToVisible(table.getCellRect(i, 0, true));
				return;
			}
		}
		JOptionPane.showMessageDialog(this, openOnly ? "No region further down the list is left to check." : "That is the end of the list.",
			"Region review", JOptionPane.INFORMATION_MESSAGE);
	}

	private void updateProgress()
	{
		int complete = 0;
		int needs = 0;
		int started = 0;
		int people = 0;
		List<Integer> counted = new ArrayList<>();
		Set<Integer> ignored = hideIgnored.isSelected() ? ctx.effectiveIgnoredRegions() : Set.of();
		boolean onlyWalkable = hideEmpty.isSelected() && ctx.graph != null;
		for (int id : cacheRegions)
		{
			if (!ignored.contains(id) && (!onlyWalkable || walkable.contains(id)))
			{
				counted.add(id);
			}
		}
		for (int id : counted)
		{
			Overall o = review.overall(id);
			complete += o == Overall.COMPLETE ? 1 : 0;
			needs += o == Overall.NEEDS_WORK ? 1 : 0;
			started += o == Overall.IN_PROGRESS ? 1 : 0;
			people += review.isHumanReviewed(id) ? 1 : 0;
		}
		int total = counted.size();
		progress.setText(String.format(Locale.ROOT, "Complete %,d of %,d regions (%.1f%%)   ·   needs work %,d   ·   in progress %,d"
				+ "   ·   reviewed by a person %,d   ·   showing %,d",
			complete, total, total == 0 ? 0.0 : 100.0 * complete / total, needs, started, people, model.rows.size()));
	}

	private final class Model extends AbstractTableModel
	{
		private final String[] cols = {"Region", "Tiles (x, y)", "Transports", "Varbits", "Items", "Sub-regions", "Overall", "Notes", "Person"};
		List<Integer> rows = new ArrayList<>();
		int total;

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
		public Class<?> getColumnClass(int c)
		{
			switch (c)
			{
				case 0:
					return Integer.class;
				case 2:
				case 3:
				case 4:
				case 5:
					return Status.class;
				case 6:
					return Overall.class;
				default:
					return String.class;
			}
		}

		@Override
		public Object getValueAt(int row, int col)
		{
			int id = rows.get(row);
			switch (col)
			{
				case 0:
					return id;
				case 1:
					return Regions.baseX(id) + ", " + Regions.baseY(id);
				case 2:
					return review.status(id, Category.TRANSPORTS);
				case 3:
					return review.status(id, Category.VARBITS);
				case 4:
					return review.status(id, Category.ITEMS);
				case 5:
					return review.status(id, Category.SUBREGIONS);
				case 6:
					return review.overall(id);
				case 7:
					return review.hasNotes(id) ? "yes" : "";
				default:
					return review.isHumanReviewed(id) ? "✓" : "";
			}
		}
	}

	/** Coloured status text; "Not checked" stays grey so the done work stands out. */
	private static final class StatusRenderer extends DefaultTableCellRenderer
	{
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			// unchecked shows as a dash, so the columns stay narrow and the done work stands out
			boolean blank = value == Status.NOT_CHECKED || value == Overall.NOT_STARTED;
			super.getTableCellRendererComponent(table, blank ? "-" : value, isSelected, hasFocus, row, column);
			if (!isSelected)
			{
				setForeground(color(value));
			}
			return this;
		}

		private static Color color(Object v)
		{
			if (v == Status.COMPLETE || v == Overall.COMPLETE)
			{
				return COMPLETE.darker();
			}
			if (v == Status.NEEDS_WORK || v == Overall.NEEDS_WORK)
			{
				return NEEDS_WORK.darker();
			}
			if (v == Overall.IN_PROGRESS)
			{
				return new Color(150, 130, 0);
			}
			return Color.GRAY;
		}
	}

	/** Always as wide as the visible area (long lines wrap instead of scrolling sideways); scrolls up and down only. */
	private static final class FitWidthPanel extends JPanel implements javax.swing.Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction)
		{
			return Math.max(16, visible.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
