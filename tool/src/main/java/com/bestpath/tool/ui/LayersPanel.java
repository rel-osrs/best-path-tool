package com.bestpath.tool.ui;

import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.editor.ManualLayer;
import com.bestpath.tool.map.MapView;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The "Layers" column left of the maps: a tick box for each thing the maps can draw, the "Redraw all map pictures"
 * button and a short list of the controls. Each tick is remembered between starts.
 */
final class LayersPanel extends JPanel
{
	private final ToolSettings settings;
	/** Applies a change to both map views. */
	private final Consumer<Consumer<MapView>> forViews;
	private final EditorContext ctx;
	private final ManualLayer manualLayer;
	private final RegionReviewLayer reviewLayer;
	private final JCheckBox modelBox = new JCheckBox("3D map", false);
	private final JCheckBox freeBox = new JCheckBox("Free-to-play land", false);
	private final JCheckBox hiddenBox = new JCheckBox("Ignored sub-regions", false);
	private final JCheckBox manualBox = new JCheckBox("Manual changes (hatched)", false);
	private final JCheckBox reviewBox = new JCheckBox("Region review", false);

	/**
	 * {@code modelsChanged} runs when the "3D map" box is ticked or cleared; {@code redrawAll} is the "Redraw all map
	 * pictures" button.
	 */
	LayersPanel(ToolSettings settings, Consumer<Consumer<MapView>> forViews, EditorContext ctx, ManualLayer manualLayer,
		RegionReviewLayer reviewLayer, Runnable modelsChanged, Runnable redrawAll)
	{
		this.settings = settings;
		this.forViews = forViews;
		this.ctx = ctx;
		this.manualLayer = manualLayer;
		this.reviewLayer = reviewLayer;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setPreferredSize(new Dimension(190, 100));

		JLabel title = new JLabel("Layers");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		add(title);
		add(Box.createVerticalStrut(6));
		addMapBoxes(modelsChanged);
		addSailingBoxes();
		addDataBoxes();
		addTransportBoxes();

		add(Box.createVerticalStrut(10));
		JButton redraw = new JButton("Redraw all map pictures...");
		redraw.setToolTipText("Throw away the saved map pictures and draw them again from the open cache");
		redraw.addActionListener(e -> redrawAll.run());
		add(redraw);

		add(Box.createVerticalStrut(16));
		add(controlsHelp());
		add(Box.createVerticalGlue());
		rememberLayers();
	}

	/** True while the "3D map" layer is ticked. */
	boolean isModelsOn()
	{
		return modelBox.isSelected();
	}

	/** Ticks and shows the "Ignored sub-regions" layer (the Ignore tool works on it). */
	void showHiddenLayer()
	{
		hiddenBox.setSelected(true);
		forViews.accept(v -> v.setShowHidden(true));
	}

	/** Ticks and shows the "Free-to-play land" layer (the Free land tool works on it). */
	void showFreeLandLayer()
	{
		freeBox.setSelected(true);
		forViews.accept(v -> v.setShowFreeLand(true));
	}

	/** Ticks and shows the "Manual changes" layer (the Wall, Block and Door tools make such changes). */
	void showManualLayer()
	{
		manualBox.setSelected(true);
		manualLayer.setShow(true);
	}

	/** Ticks and shows the "Region review" layer (the Region review tab works with it). */
	void showReviewLayer()
	{
		reviewBox.setSelected(true);
		reviewLayer.setShow(true);
	}

	/** A layer box that switches one thing on or off in both views. */
	private JCheckBox viewBox(String text, String tip, BiConsumer<MapView, Boolean> show)
	{
		JCheckBox box = new JCheckBox(text, false);
		if (tip != null)
		{
			box.setToolTipText(tip);
		}
		box.addActionListener(e -> forViews.accept(v -> show.accept(v, box.isSelected())));
		return box;
	}

	/** The world map, the 3D map and the grids. */
	private void addMapBoxes(Runnable modelsChanged)
	{
		JCheckBox mapLayer = new JCheckBox("World map", true);
		mapLayer.addActionListener(e -> forViews.accept(v -> v.setShowMap(mapLayer.isSelected())));
		add(mapLayer);
		modelBox.setSelected(settings.get("layer.models", "false").equals("true"));
		modelBox.setToolTipText("<html>Every object's 3D model and the ground, seen from straight above (roofs left out).<br>"
			+ "Drawn once for the whole map (in the background, the first time you turn this on) and saved<br>"
			+ "in map-pictures/3d; after a game update only changed regions are redrawn.<br>"
			+ "Regions not drawn yet show the world map.</html>");
		modelBox.addActionListener(e ->
		{
			settings.set("layer.models", Boolean.toString(modelBox.isSelected()));
			settings.save();
			modelsChanged.run();
		});
		add(modelBox);
		add(viewBox("Region grid (64x64)", null, MapView::setShowRegionGrid));
		add(viewBox("Tile grid (zoomed in)", "Lines around every tile, shown when zoomed in far enough",
			MapView::setShowTileGrid));
		add(viewBox("Walls & blocked tiles", "<html>Our movement data, extracted from the cache (zoom in to see it):<br>"
			+ "yellow lines = walls and closed doors, small squares = wall corners,<br>"
			+ "red = solid objects, blue = blocked floor (e.g. water), orange = blocking floor decoration</html>",
			MapView::setShowWalls));
	}

	/** The sea, free-to-play land, docks and banks. */
	private void addSailingBoxes()
	{
		add(viewBox("Sea (sailing)", "<html>Open sea from the cache (plane 0), coloured by sea area:<br>"
			+ "blue = open water (pale = coastal shallows), amber = Tempor Tantrum (oak mast or better),<br>"
			+ "purple = Fremennik / northern waters (torch), pink = Crystalline waters (adamant keel),<br>"
			+ "green = Fetid water (inoculation station), dark green = Kelp area (adamant helm),<br>"
			+ "dark navy = water beyond the world edge. White = blocked sea tile (rocks, border lines).<br>"
			+ "Dark stripes = sea a boat cannot reach from the main ocean (cut off by blocked tiles).<br>"
			+ "Click a tile: the Inspector shows its water texture, area and piece. Sea tiles are closed to walking.<br>"
			+ "Dashed yellow lines = sea channels: crooked water the boats' straight links miss (orange = rafts only);<br>"
			+ "also drawn with the Docks and Suggested transports layers. Click one: the Inspector describes it.</html>",
			MapView::setShowSea));
		freeBox.setToolTipText("<html>Free-to-play land (green, every plane): where a player on a free world can go.<br>"
			+ "The rest of the map is members only. Change it with the Free land tool (F).<br>"
			+ "Routes for a test player without members stay on it.</html>");
		freeBox.addActionListener(e -> forViews.accept(v -> v.setShowFreeLand(freeBox.isSelected())));
		add(freeBox);
		add(viewBox("Docks (sailing)", "<html>Sailing docks from the cache: where the player goes from land to sea and back.<br>"
			+ "Cyan = the buoy the boat docks at, orange = the gangplank or mooring point,<br>"
			+ "green = the land tiles the player stands on to use it; a line joins each pair.<br>"
			+ "Grey = a dock whose buoy is not on the main ocean. Zoom in for names, levels and quests.<br>"
			+ "Click a dock tile: the Inspector shows its details.</html>", MapView::setShowDocks));
		add(viewBox("Banks", "<html>Banks a route may start at (data/banks/banks.tsv, made by gradlew -p tool banks):<br>"
			+ "gold square = bank booth, circle = bank chest (both from the cache), diamond = banker (OSRS Wiki, for banks with no booth).<br>"
			+ "Green = the tiles a player stands on to use it; red = no tile to use it from (never used).<br>"
			+ "Zoom in for the bank's name and what it needs.</html>", MapView::setShowBanks));
	}

	/** Sub-regions, what is left out of the plugin's data, manual changes and entrances. */
	private void addDataBoxes()
	{
		add(viewBox("Sub-regions", "<html>Each colour is one sub-region (component): tiles you can walk between<br>"
			+ "without leaving the 64x64 region. Doors count as open, stairs are not used.<br>"
			+ "Pockets and hidden sub-regions are on the \"Ignored sub-regions\" layer instead.</html>",
			MapView::setShowComponents));
		hiddenBox.setToolTipText("<html>Everything left out of the plugin's data:<br>"
			+ "red = sub-regions you hid, grey = small closed-off pockets (left out automatically;<br>right-click one with the Hide tool to keep it), hatched red = ignored 64x64 regions (all planes).<br>"
			+ "Changes show at once. Switched on by the Ignore tool.</html>");
		hiddenBox.addActionListener(e -> forViews.accept(v -> v.setShowHidden(hiddenBox.isSelected())));
		add(hiddenBox);
		manualBox.setToolTipText("<html>Everything you changed by hand, hatched, to tell it apart from the cache data:<br>"
			+ "orange = blocking you added (tile, wall, shut door), cyan = blocking you removed,<br>"
			+ "grey = marked \"not a transport\". (Transports are on the Confirmed transports layer.)<br>"
			+ "Every change in effect. Switched on by the Wall, Block and Door tools.</html>");
		manualBox.addActionListener(e -> manualLayer.setShow(manualBox.isSelected()));
		add(manualBox);
		add(viewBox("Entrances", "White bars on region borders: where a sub-region connects to the next region",
			MapView::setShowEntrances));
	}

	/** Suggested and confirmed transports, teleport destinations and the region review. */
	private void addTransportBoxes()
	{
		add(viewBox("Suggested transports", "<html>Ladders, stairs, caves, shortcuts... found in the cache (zoom in).<br>"
			+ "Dashed arrow = suggested destination (NOT accepted). Orange outline with ? = no suggestion.</html>",
			MapView::setShowTransports));
		add(viewBox("Confirmed transports", "<html>All transports in use: confirmed suggestions and ones you drew yourself (green; red = category Needs fixing).<br>"
			+ "Ones not built into the data yet are always shown (yellow, for a few seconds). Zoomed out: a dot each.<br>"
			+ "Listed in the bottom panel under \"Your transports (all)\".</html>", MapView::setShowConfirmed));
		JCheckBox tele = new JCheckBox("Teleport destinations", false);
		tele.setToolTipText("<html>Pink dots: suggested teleport destinations, from cache tables (fairy rings, quetzals,<br>"
			+ "Pendant of Ates, charter ports) and from the Wiki (spells, jewellery...). Click one to confirm it.</html>");
		tele.addActionListener(e ->
		{
			ctx.teleportsShown = tele.isSelected(); // its dots can be clicked only while shown
			forViews.accept(v -> v.setShowTeleports(tele.isSelected()));
		});
		add(tele);
		reviewBox.setToolTipText("<html>Your region-by-region check (Region review tab): green = all four checks complete,<br>"
			+ "orange = something needs work, yellow = in progress. Zoomed in, boxes T V I S show<br>"
			+ "transports, varbits, items and sub-regions. Click a region while the tab is shown to pick it.</html>");
		reviewBox.addActionListener(e ->
		{
			reviewLayer.setShow(reviewBox.isSelected());
			ctx.repaintAll();
		});
		add(reviewBox);
	}

	private static JLabel controlsHelp()
	{
		return new JLabel("<html><b>Controls</b><br>"
			+ "Left button: the active tool<br>"
			+ "Right-drag: move map (middle-drag<br>with the Block brush)<br>"
			+ "Wheel or +/-: zoom<br>"
			+ "PageUp/PageDown: plane<br>"
			+ "Tool keys: V T W B D H I R M<br>"
			+ "Selected transport: N next suggestion,<br>C confirm, Del delete, S skip,<br>L test in game later,<br>O one-way / two-way<br>"
			+ "Ctrl+Z: undo (one click or one drag)<br>"
			+ "Esc: cancel a half-made transport<br><br>"
			+ "<span style='color:gray'>Every change is saved at once; "
			+ "the data catches up in the background a moment later.</span></html>");
	}

	/**
	 * Layer ticks are remembered between starts: each box gets its saved state back by a click (so its own action
	 * runs) and saves every change. "3D map" keeps its own setting.
	 */
	private void rememberLayers()
	{
		for (Component c : getComponents())
		{
			if (!(c instanceof JCheckBox) || c == modelBox)
			{
				continue;
			}
			JCheckBox box = (JCheckBox) c;
			String key = "layer." + box.getText().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
			String saved = settings.get(key, null);
			if (saved != null && Boolean.parseBoolean(saved) != box.isSelected())
			{
				box.doClick();
			}
			box.addActionListener(e -> settings.set(key, Boolean.toString(box.isSelected())));
		}
	}
}
