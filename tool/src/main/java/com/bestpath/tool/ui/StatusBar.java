package com.bestpath.tool.ui;

import com.bestpath.tool.map.MapTileStore;
import com.bestpath.tool.map.MapView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 * The line along the bottom of the main window: the tile under the mouse on the left, a message from the remote link in the
 * middle, and on the right the state of the data build and of the map pictures being drawn.
 */
final class StatusBar extends JPanel
{
	private final JLabel hoverLabel = new JLabel(" ");
	/** A message from the remote link (see {@link RemoteLink}); hidden when empty. */
	private final JLabel linkLabel = new JLabel();
	private final JLabel wallsLabel = new JLabel(" ");
	private final JLabel progressLabel = new JLabel(" ");
	private final JProgressBar progressBar = new JProgressBar(0, 100);

	StatusBar()
	{
		super(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
		add(hoverLabel, BorderLayout.WEST);
		linkLabel.setOpaque(true);
		linkLabel.setBackground(new Color(255, 236, 140));
		linkLabel.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
		linkLabel.setFont(linkLabel.getFont().deriveFont(Font.BOLD));
		linkLabel.setVisible(false);
		add(linkLabel, BorderLayout.CENTER);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		progressLabel.setForeground(Color.DARK_GRAY);
		wallsLabel.setForeground(Color.DARK_GRAY);
		right.add(wallsLabel);
		right.add(progressLabel);
		progressBar.setPreferredSize(new Dimension(220, 16));
		progressBar.setStringPainted(true);
		progressBar.setVisible(false);
		right.add(progressBar);
		add(right, BorderLayout.EAST);
	}

	/** The tile under the mouse. */
	void showHover(String text)
	{
		hoverLabel.setText(text);
	}

	/** The remote link's message; an empty text hides it. */
	void say(String text)
	{
		linkLabel.setText(text.isEmpty() ? "" : "Remote link: " + text);
		linkLabel.setVisible(!text.isEmpty());
	}

	/** The state of the data build. */
	void setDataText(String text)
	{
		wallsLabel.setText(text);
	}

	String getDataText()
	{
		return wallsLabel.getText();
	}

	String getProgressText()
	{
		return progressLabel.getText();
	}

	/**
	 * Shows how far the map pictures are drawn: the world map first; once it is up to date, the 3D map (when that
	 * layer is on). A banner on {@code map} tells about the world map while it is not ready.
	 */
	void showMapProgress(MapTileStore tiles, MapTileStore modelTiles, MapView map)
	{
		if (tiles == null)
		{
			progressLabel.setText(" ");
			progressBar.setVisible(false);
			map.setBanner(null);
			return;
		}
		MapTileStore s = tiles;
		if (modelTiles != null && tiles.getPhase() == MapTileStore.Phase.READY)
		{
			s = modelTiles;
		}
		boolean models = s != tiles;
		String name = s.getStyle().getName();
		String err = s.getLastError() == null ? "" : "   (last problem: " + s.getLastError() + ")";
		MapTileStore.Phase phase = s.getPhase();
		switch (phase)
		{
			case PREPARING:
			case CHECKING:
				progressBar.setVisible(true);
				progressBar.setIndeterminate(true);
				progressBar.setString("");
				progressLabel.setText(name + ": " + phase.getText().toLowerCase(Locale.ROOT) + " ...");
				// the 3D map fills in quietly; regions without it show the world map
				map.setBanner(models ? null : phase.getText() + " ...  (saved pictures are shown meanwhile)");
				break;
			case DRAWING:
				showDrawing(s, models, err, map);
				break;
			case READY:
				progressBar.setVisible(false);
				progressLabel.setText(String.format(Locale.ROOT, "%s up to date (%,d regions reused, %,d redrawn)%s",
					models ? "Map pictures and 3D map" : "Map pictures", s.getRegionsReused(), s.getRegionsChanged(), err));
				map.setBanner(null);
				break;
			default:
				progressBar.setVisible(false);
				progressLabel.setText(name + " failed" + err);
				map.setBanner(name + " failed: " + s.getLastError());
		}
	}

	private void showDrawing(MapTileStore s, boolean models, String err, MapView map)
	{
		int toDraw = Math.max(1, s.getToDraw());
		int drawn = Math.min(s.getDrawn(), toDraw);
		int pct = drawn * 100 / toDraw;
		progressBar.setVisible(true);
		progressBar.setIndeterminate(false);
		progressBar.setValue(pct);
		progressBar.setString(pct + "%");
		String eta = formatDuration(s.getSecondsLeft());
		progressLabel.setText(String.format(Locale.ROOT, "Drawing %s: %,d / %,d   about %s left   "
			+ "(%,d regions changed, %,d reused)%s", models ? "the 3D map" : "map pictures", drawn, toDraw, eta,
			s.getRegionsChanged(), s.getRegionsReused(), err));
		map.setBanner(models ? null : String.format(Locale.ROOT, "Drawing map pictures: %d%%, about %s left. "
			+ "You can keep using the map; hatched squares are not drawn yet.", pct, eta));
	}

	static String formatDuration(long seconds)
	{
		if (seconds < 60)
		{
			return "less than a minute";
		}
		long m = (seconds + 30) / 60;
		return m == 1 ? "1 minute" : m + " minutes";
	}
}
