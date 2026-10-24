package com.bestpath.tool.ui;

import com.bestpath.tool.geo.Regions;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.search.SearchParser;
import com.bestpath.tool.search.SearchTarget;
import java.awt.Font;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/**
 * The bar along the top of the main window: buttons that open a cache or one of the tool's windows, the open cache's
 * name, a button per plane and the search field (a tile or a region id).
 */
final class MainToolBar extends JToolBar
{
	private final MapView map;
	/** The open cache's regions (empty when none is open). */
	private final Supplier<Set<Integer>> regionIds;
	private final JLabel cacheLabel = new JLabel("Cache: none");
	private final JToggleButton[] planeButtons = new JToggleButton[4];
	private final JTextField search = new JTextField(18);

	/** {@code windowButtons} come first, left to right. */
	MainToolBar(MapView map, Supplier<Set<Integer>> regionIds, List<JButton> windowButtons)
	{
		this.map = map;
		this.regionIds = regionIds;
		setFloatable(false);
		setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		windowButtons.forEach(this::add);
		addSeparator();
		cacheLabel.setFont(cacheLabel.getFont().deriveFont(Font.BOLD));
		add(cacheLabel);
		add(Box.createHorizontalGlue());
		addPlaneButtons();
		addSeparator();
		addSearch();
	}

	/** A toolbar button with a tooltip. */
	static JButton button(String text, String tip, Runnable action)
	{
		JButton b = new JButton(text);
		b.setToolTipText(tip);
		b.addActionListener(e -> action.run());
		return b;
	}

	/** The text about the open cache ("Cache: ..."). */
	void setCacheText(String text)
	{
		cacheLabel.setText(text);
	}

	/** Presses the button of the plane the main map shows. */
	void showPlane(int plane)
	{
		planeButtons[plane].setSelected(true);
	}

	/** One button per plane, for the main map. */
	private void addPlaneButtons()
	{
		add(new JLabel("Plane: "));
		ButtonGroup group = new ButtonGroup();
		for (int p = 0; p < 4; p++)
		{
			int plane = p;
			JToggleButton b = new JToggleButton(Integer.toString(p));
			b.setToolTipText("Show plane " + p + " (PageUp / PageDown)");
			b.addActionListener(e -> map.setPlane(plane));
			group.add(b);
			planeButtons[p] = b;
			add(b);
		}
	}

	private void addSearch()
	{
		add(new JLabel("Search: "));
		search.setToolTipText("Coordinates like 3222 3218 0, or a region id like 12850");
		search.setMaximumSize(search.getPreferredSize());
		search.addActionListener(e -> doSearch());
		add(search);
		JButton go = new JButton("Go");
		go.addActionListener(e -> doSearch());
		add(go);
	}

	/** Moves the main map to the tile or region typed in the search field. */
	private void doSearch()
	{
		java.awt.Window window = SwingUtilities.getWindowAncestor(this);
		SearchTarget t = SearchParser.parse(search.getText());
		if (t == null)
		{
			JOptionPane.showMessageDialog(window, "Type coordinates (e.g. 3222 3218 0) or a region id (e.g. 12850).",
				"Search", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (t.getKind() == SearchTarget.Kind.TILE)
		{
			if (t.getPlane() != SearchTarget.ANY_PLANE)
			{
				map.setPlane(t.getPlane());
			}
			map.goTo(t.getX(), t.getY(), Math.max(map.getViewport().getScale(), 6));
		}
		else
		{
			int id = t.getRegionId();
			map.goTo(Regions.baseX(id) + 32, Regions.baseY(id) + 32, 8);
			Set<Integer> regions = regionIds.get();
			if (!regions.isEmpty() && !regions.contains(id))
			{
				JOptionPane.showMessageDialog(window, "Region " + id + " has no map data in this cache.",
					"Search", JOptionPane.INFORMATION_MESSAGE);
			}
		}
		map.requestFocusInWindow();
	}
}
