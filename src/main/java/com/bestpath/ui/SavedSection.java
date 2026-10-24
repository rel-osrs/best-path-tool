package com.bestpath.ui;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.SavedLocations;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The Functions tab's "Saved locations": up to {@link SavedLocations#MAX} named places. One button per saved place (a
 * click routes there), an x to remove it, and a name + tile box with Save (an empty tile = where the player stands; a
 * tile already saved gets the new name). Swing thread only.
 */
final class SavedSection extends JPanel
{
	static final String NONE_SAVED = "No saved locations yet. Name a place and press Save (up to " + SavedLocations.MAX
		+ ").";
	static final String FULL = "You have " + SavedLocations.MAX + " saved locations: remove one (x) to save another.";
	static final String NOT_LOGGED_IN = "Log in to save where you stand, or type a tile.";

	private final PanelActions actions;
	private final Runnable showRoute;
	private final JPanel list = new JPanel();
	final JTextField name = new JTextField();
	final JTextField tile = new JTextField();
	final JButton save = Ui.button("Save", "Save this place under this name");
	final WrapLabel message = new WrapLabel("", Ui.CONTENT_WIDTH, Ui.small(), Ui.NOTE);
	private SavedLocations saved = SavedLocations.NONE;

	SavedSection(PanelActions actions, Runnable showRoute)
	{
		this.actions = actions;
		this.showRoute = showRoute;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		Ui.addRow(this, list);
		Ui.addRow(this, Box.createVerticalStrut(6));

		Ui.addRow(this, Ui.label("Name", Ui.small(), Ui.TEXT));
		name.putClientProperty("JTextField.placeholderText", "e.g. My farming patch");
		name.setToolTipText("What to call this place (up to " + SavedLocations.NAME_MAX + " letters)");
		name.addActionListener(e -> save());
		Ui.addRow(this, name);
		Ui.addRow(this, Box.createVerticalStrut(3));
		Ui.addRow(this, Ui.label("Tile (x, y, plane)", Ui.small(), Ui.TEXT));
		tile.putClientProperty("JTextField.placeholderText", "empty: where you stand");
		tile.setToolTipText("x, y and plane. Empty: where you stand");
		tile.addActionListener(e -> save());
		JPanel entry = new JPanel(new BorderLayout(4, 0));
		entry.setOpaque(false);
		entry.add(tile, BorderLayout.CENTER);
		save.addActionListener(e -> save());
		entry.add(save, BorderLayout.EAST);
		entry.setMaximumSize(new Dimension(Integer.MAX_VALUE, entry.getPreferredSize().height));
		Ui.addRow(this, entry);
		Ui.addRow(this, Box.createVerticalStrut(3));
		Ui.addRow(this, message);
		show(SavedLocations.NONE);
	}

	void show(SavedLocations s)
	{
		saved = s;
		list.removeAll();
		List<SavedLocations.Place> places = s.list();
		if (places.isEmpty())
		{
			Ui.addRow(list, Ui.wrapped(NONE_SAVED, Ui.CONTENT_WIDTH, Ui.small(), Ui.MUTED));
		}
		for (int i = 0; i < places.size(); i++)
		{
			SavedLocations.Place p = places.get(i);
			int index = i;
			JButton go = Ui.button(p.getName(), "Route to " + p.getName() + " (" + p.tileLabel() + ")");
			go.addActionListener(e ->
			{
				actions.findRoute(p.request());
				showRoute.run();
			});
			JButton remove = Ui.button("x", "Remove " + p.getName());
			remove.addActionListener(e -> actions.removeSavedLocation(index));
			JPanel line = Ui.buttonLine(go, remove);
			if (i > 0)
			{
				Ui.addRow(list, Box.createVerticalStrut(3));
			}
			Ui.addRow(list, line);
		}
		if (s.isFull() && message.getText().isEmpty())
		{
			message.setText(FULL);
		}
		else if (!s.isFull() && FULL.equals(message.getText()))
		{
			message.setText(""); // one was removed: there is room again
		}
		revalidate();
		repaint();
	}

	/** "3 of 10 saved" for the card. */
	String summary()
	{
		return saved.list().isEmpty() ? "None saved yet" : saved.list().size() + " of " + SavedLocations.MAX + " saved";
	}

	/** Save: checks the tile (typed, or where the player stands), then hands it to the plugin. */
	private void save()
	{
		int t;
		String typed = tile.getText().trim();
		if (typed.isEmpty())
		{
			t = actions.playerTile();
			if (t < 0)
			{
				message.setText(NOT_LOGGED_IN);
				return;
			}
		}
		else
		{
			try
			{
				RouteRequest r = RouteRequest.parse(typed);
				t = Tiles.pack(r.getX(), r.getY(), r.getPlane());
			}
			catch (IllegalArgumentException e)
			{
				message.setText(e.getMessage());
				return;
			}
		}
		if (saved.isFull() && saved.indexOf(t) < 0)
		{
			message.setText(FULL);
			return;
		}
		boolean renamed = saved.indexOf(t) >= 0;
		actions.saveLocation(name.getText(), t);
		String shown = RouteRequest.tileLabel(Tiles.x(t), Tiles.y(t), Tiles.plane(t));
		message.setText((renamed ? "Renamed " : "Saved ") + shown + ".");
		name.setText("");
		tile.setText("");
	}
}
