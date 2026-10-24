package com.bestpath.ui;

import com.bestpath.core.data.PlaceKinds;
import com.bestpath.route.NearestPlaces;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

/**
 * The Functions tab's "Nearest" part: a drop-down of place kinds (bank, altar, furnace, anvil, ... with their map
 * icons) and Go, which routes to whichever place of that kind is quickest to reach ({@link NearestPlaces}) and shows
 * the Route tab. Every kind the map data has is offered. Swing thread only.
 */
final class NearestSection extends JPanel
{
	static final String LOADING = "Loading the map data...";
	static final String NO_PLACES = "The map data has no places to go to.";
	static final String HINT = "Goes to the quickest one to reach.";
	private static final int ICON = 15;

	final JComboBox<PlaceKinds.Kind> kinds = new JComboBox<>();
	final JButton go = Ui.button("Go", "Route to the nearest place of this kind");
	final WrapLabel text = new WrapLabel(LOADING, Ui.CONTENT_WIDTH, Ui.small(), Ui.MUTED);

	/** {@code showRoute}: shows the Route tab (after Go starts a route); {@code icons}: the kinds' map icons. */
	NearestSection(PanelActions actions, Runnable showRoute, IconCache icons)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		kinds.setFont(Ui.small());
		kinds.setFocusable(false);
		kinds.setToolTipText("The kind of place to go to");
		kinds.setMaximumRowCount(16);
		kinds.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
				if (value instanceof PlaceKinds.Kind)
				{
					PlaceKinds.Kind k = (PlaceKinds.Kind) value;
					l.setText(k.getName());
					BufferedImage img = k.getSprite() < 0 ? null
						: icons.get(IconKey.sprite(k.getSprite()), ICON, ICON, () ->
						{
							list.repaint();
							kinds.repaint();
						});
					l.setIcon(img == null ? null : new ImageIcon(img));
				}
				return l;
			}
		});
		go.addActionListener(e ->
		{
			PlaceKinds.Kind k = (PlaceKinds.Kind) kinds.getSelectedItem();
			if (k != null)
			{
				actions.findRoute(NearestPlaces.request(k));
				showRoute.run();
			}
		});

		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		line.add(kinds, BorderLayout.CENTER);
		line.add(go, BorderLayout.EAST);
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		add(line);
		add(Box.createVerticalStrut(3));
		text.setAlignmentX(LEFT_ALIGNMENT);
		add(text);
		showKinds(PlaceKinds.NONE, false);
	}

	/**
	 * The kinds to offer (each with its places; the banks already in, see {@link PlaceKinds#withBanks}); {@code loaded}
	 * false: the map data is still loading. The kind picked stays picked when it is still there; at first the bank.
	 */
	void showKinds(PlaceKinds k, boolean loaded)
	{
		PlaceKinds.Kind was = (PlaceKinds.Kind) kinds.getSelectedItem();
		kinds.removeAllItems();
		for (PlaceKinds.Kind kind : k.getKinds())
		{
			kinds.addItem(kind);
		}
		PlaceKinds.Kind pick = was == null ? k.find(PlaceKinds.BANK) : k.find(was.getName());
		if (pick != null)
		{
			kinds.setSelectedItem(pick);
		}
		boolean any = !k.isEmpty();
		kinds.setEnabled(any);
		go.setEnabled(any);
		text.setText(any ? HINT : loaded ? NO_PLACES : LOADING);
		revalidate();
		repaint();
	}
}
