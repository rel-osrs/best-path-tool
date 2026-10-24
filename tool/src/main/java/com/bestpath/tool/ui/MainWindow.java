package com.bestpath.tool.ui;

import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaGraph;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.cache.Backups;
import com.bestpath.tool.cache.CacheInfo;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.editor.BlockTool;
import com.bestpath.tool.editor.DoorTool;
import com.bestpath.tool.editor.EditOverlay;
import com.bestpath.tool.editor.EditorContext;
import com.bestpath.tool.editor.EditorTool;
import com.bestpath.tool.editor.FreeLandTool;
import com.bestpath.tool.editor.IgnoreRegionTool;
import com.bestpath.tool.editor.ManualLayer;
import com.bestpath.tool.editor.RouteTool;
import com.bestpath.tool.editor.WallTool;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.geo.Regions;
import com.bestpath.tool.map.ChannelLayer;
import com.bestpath.tool.map.MapLibrary;
import com.bestpath.tool.map.MapTileStore;
import com.bestpath.tool.map.MapView;
import com.bestpath.tool.map.MapViewport;
import com.bestpath.tool.req.CacheNames;
import com.bestpath.tool.req.Networks;
import com.bestpath.tool.req.TestPlayer;
import com.bestpath.tool.req.TestPlayers;
import com.bestpath.tool.review.RegionReview;
import com.bestpath.tool.route.RouteController;
import com.bestpath.tool.route.RouteOverlay;
import com.bestpath.tool.transport.TransportData;
import com.bestpath.tool.update.CompareFile;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.JTextComponent;

/**
 * The BestPath Data Tool main window: the toolbar, the layers column, the main map with a second map view beside it,
 * the editing tools, the Inspector, the bottom tabs and the status bar. It opens caches and rebuilds the data in the
 * background whenever the manual changes move on.
 */
public final class MainWindow extends JFrame
{
	private final ToolSettings settings;
	private final File workspace;
	private final MapViewport viewport;
	private final MapView map;
	/** Second map view, right of the main map; shows the end of the selected transport. */
	private final MapView destView = new MapView(new MapViewport(3222.5, 3218.5, 8));
	private final JPanel destPanel = new JPanel(new BorderLayout());
	/** Main map | second view, with a draggable divider. */
	private final JSplitPane mapRow = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
	/** Width of the second view, remembered between uses (and between runs). */
	private int destWidth;
	private final InspectorPanel inspector = new InspectorPanel();
	private MainToolBar toolbar;
	private final StatusBar statusBar = new StatusBar();
	/** The main map's click handling, also used by {@link RemoteLink}. */
	private MapView.Listener mainListener;
	/** Opens caches and builds the data, off the Swing thread. */
	private final ExecutorService cacheExecutor = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "cache-open");
		t.setDaemon(true);
		return t;
	});

	private LoadedCache cache;
	private MapLibrary library;
	private volatile FlagGrid collision;
	private volatile CoarseGraph coarseGraph;
	/** The open sea of the open cache (no changes apply to it), or null while building. */
	private volatile SeaMap seaMap;
	private volatile SeaPieces seaPieces;
	/** Sailing docks of the open cache (land and sea side paired), or null while building. */
	private volatile List<Dock> docks;
	private volatile TransportData transportData;
	private TransportListPanel transportList;
	private TestListPanel testList;
	private NetworksPanel networksPanel;
	private JSplitPane bottomSplit;
	private LayersPanel layers;
	private final ManualLayer manualLayer;
	private final RouteController routeController;
	/** Manual changes (each saved at once) and the shared editing state of both views. */
	private final EditStore edits;
	private final EditorContext ctx;
	private ToolPalette palette;
	private ToolOptionsBar optionsBar;
	private JTabbedPane bottomTabs;
	/** Builds the data from the cache and the changes (on {@link #cacheExecutor}). */
	private final DataBuilder dataBuilder;
	/** Rebuilds the data from the changes once editing pauses. */
	private final Timer rebuildTimer = new Timer(1500, e -> rebuildData());
	/** True while a data build runs; another is started when it ends if the changes moved on. */
	private boolean building;
	/** The per-region checklist (Region review tab and map layer). */
	private final RegionReview regionReview;
	private final RegionReviewLayer reviewLayer;
	private RegionReviewPanel reviewPanel;
	private RoutePanel routePanel;
	private boolean destShown;
	/**
	 * Counts builds asked for (Swing thread); read by the worker to skip work a newer build makes useless, and to ignore
	 * a slow build for a cache no longer open.
	 */
	private volatile int generation;
	private MapTileStore tiles;
	/** 3D map pictures; started the first time the "3D map" layer is on for an open cache. */
	private MapTileStore modelTiles;
	private Set<Integer> regionIds = Set.of();
	/** The question cards on the main map (sea channel and other cards are made after each data build). */
	private QuestionCards questionCards;
	/** The game update comparison's changed regions on the main map. */
	private final CacheChangeLayer changeLayer = new CacheChangeLayer();
	/** The open "Compare with a new cache" window, or null. */
	private CacheCompareDialog cacheCompare;
	/** The open "Update plugin data" window, or null. */
	private PluginExportDialog pluginExport;

	public MainWindow(ToolSettings settings, File workspace)
	{
		super("BestPath Data Tool");
		this.settings = settings;
		this.workspace = workspace;
		this.viewport = new MapViewport(
			settings.getDouble("view.x", 3222.5),
			settings.getDouble("view.y", 3218.5),
			settings.getDouble("view.scale", 2));
		this.map = new MapView(viewport);
		this.edits = new EditStore(new File(workspace, "data/manual/changes.jsonl"));
		loadOrWarn(edits::load, "Could not read the change records:\n");
		this.regionReview = new RegionReview(new File(workspace, "data/manual/region-review.json"));
		loadOrWarn(regionReview::load, "Could not read the region review:\n");
		this.reviewLayer = new RegionReviewLayer(regionReview, () -> reviewPanel == null ? -1 : reviewPanel.getSelectedRegion());
		this.ctx = new EditorContext(edits);
		this.manualLayer = new ManualLayer(ctx);
		this.routeController = new RouteController(ctx);
		this.dataBuilder = new DataBuilder(workspace);
		ctx.addView(map);
		ctx.addView(destView);

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				requestClose();
			}
		});

		setLayout(new BorderLayout());
		add(buildToolbar(), BorderLayout.NORTH);
		layers = new LayersPanel(settings, this::forViews, ctx, manualLayer, reviewLayer, this::updateModelTiles,
			this::redrawAll);
		add(layers, BorderLayout.WEST);
		transportList = new TransportListPanel(ctx::showCandidate, ctx::showRecord, ctx::effectiveTransports);
		JPanel mapArea = buildMapArea();
		bottomTabs = new JTabbedPane();
		addEditTabs();
		RouteSettings.attach(settings, routeController); // before the Route tab shows them
		addRouteAndReviewTabs();
		watchEdits();
		installGestures();
		add(buildBottomSplit(mapArea), BorderLayout.CENTER);
		add(inspector, BorderLayout.EAST);
		add(statusBar, BorderLayout.SOUTH);
		listenToViews();
		installKeys();

		int plane = (int) settings.getDouble("view.plane", 0);
		map.setPlane(Math.max(0, Math.min(3, plane)));
		toolbar.showPlane(map.getPlane());

		new Timer(500, e -> updateProgress()).start();
		startQuestionCards();
		restoreWindowSize();

		String last = settings.get("cache.last", null);
		if (last != null && new File(last).isDirectory())
		{
			openCache(new File(last));
		}
	}

	/** Something that reads a file and may fail. */
	private interface Loader
	{
		void load() throws Exception;
	}

	/** Runs {@code loader}; a failure is shown in a message box, and the tool starts anyway. */
	private static void loadOrWarn(Loader loader, String message)
	{
		try
		{
			loader.load();
		}
		catch (Exception ex)
		{
			JOptionPane.showMessageDialog(null, message + DownloadDialog.rootMessage(ex),
				"BestPath Data Tool", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** The tool options above the maps, the tool palette left of them, the two maps and the decision bar below. */
	private JPanel buildMapArea()
	{
		// the second view sits beside the main map, so both have the same height
		destWidth = (int) settings.getDouble("dest.width", 420);
		mapRow.setLeftComponent(map);
		mapRow.setRightComponent(null);
		mapRow.setDividerSize(0);
		mapRow.setResizeWeight(1.0); // when the window grows, the main map gets the extra space
		mapRow.setBorder(null);
		mapRow.setContinuousLayout(true);
		buildDestPanel();
		palette = new ToolPalette(ctx, routeController, this::forViews, this::toolSelected);
		addOverlays();
		optionsBar = new ToolOptionsBar(ctx);
		JPanel mapArea = new JPanel(new BorderLayout());
		mapArea.add(optionsBar, BorderLayout.NORTH);
		palette.select(palette.getTools().get(0));
		mapArea.add(palette, BorderLayout.WEST);
		mapArea.add(mapRow, BorderLayout.CENTER);
		mapArea.add(optionsBar.getDecisionBar(), BorderLayout.SOUTH);
		return mapArea;
	}

	/** The layers both views draw over the map, lowest first. */
	private void addOverlays()
	{
		forViews(v -> v.addOverlay(reviewLayer)); // lowest: a tint over whole regions
		forViews(v -> v.addOverlay(manualLayer)); // under the edit overlay
		RouteOverlay routeOverlay = new RouteOverlay(routeController);
		forViews(v -> v.addOverlay(routeOverlay));
		EditOverlay overlay = new EditOverlay(ctx, palette.getTransportTool());
		forViews(v -> v.addOverlay(overlay));
	}

	/** The bottom tabs for checking the data: Transport candidates, Change log and Test in game. */
	private void addEditTabs()
	{
		bottomTabs.addTab("Transport candidates", transportList);
		bottomTabs.addTab("Change log", new ChangeLogPanel(edits, workspace, this::jumpMain, this::author, this::changeAuthorName));
		testList = new TestListPanel(ctx);
		bottomTabs.addTab("Test in game (0)", testList);
	}

	/** The Route, Region review and Networks tabs. */
	private void addRouteAndReviewTabs()
	{
		routePanel = new RoutePanel(routeController, this::jumpMain, this::showArea,
			new File(workspace, "data/route-examples.txt"));
		bottomTabs.addTab("Route", routePanel);
		reviewPanel = new RegionReviewPanel(ctx, regionReview, this::author, id ->
		{
			int bx = Regions.baseX(id);
			int by = Regions.baseY(id);
			showArea(bx, by, bx + 63, by + 63, map.getPlane());
		}, ctx::repaintAll);
		bottomTabs.addTab("Region review", reviewPanel);
		networksPanel = new NetworksPanel(ctx, routeController::recompute);
		bottomTabs.addTab("Networks", networksPanel);
		bottomTabs.addChangeListener(e ->
		{
			if (bottomTabs.getSelectedComponent() == networksPanel)
			{
				networksPanel.refresh(); // networks.tsv may have changed elsewhere
			}
			if (bottomTabs.getSelectedComponent() == reviewPanel && !reviewLayer.isShown())
			{
				// the tab works with its map layer: show it
				layers.showReviewLayer();
				ctx.repaintAll();
			}
		});
	}

	/** Stamps each change with its author and cache, and lets every view follow the changes. */
	private void watchEdits()
	{
		edits.setStamp(this::author,
			() -> cache != null ? cache.getInfo().getOpenrs2Id() : -1,
			() -> cache != null ? cache.getInfo().getBuild() : -1,
			ex -> JOptionPane.showMessageDialog(this, "Could not save the change:\n" + DownloadDialog.rootMessage(ex),
				"BestPath Data Tool", JOptionPane.ERROR_MESSAGE));
		edits.addListener(() ->
		{
			refreshEditViews();
			routeController.recompute(); // transports not built in yet count for the route check
			transportList.refresh(); // the "Needs fixing" list follows your transport changes
			ctx.repaintAll();
			if (edits.needsBuild())
			{
				rebuildTimer.restart(); // the data follows the changes, once editing pauses
			}
		});
	}

	/** The maps above, the bottom tabs below, with a draggable divider. */
	private JSplitPane buildBottomSplit(JPanel mapArea)
	{
		bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapArea, bottomTabs);
		bottomSplit.setResizeWeight(1.0); // a bigger window gives the maps the extra height
		bottomSplit.setOneTouchExpandable(true);
		// the bottom tabs start at a modest height (the table would otherwise ask for half the window)
		bottomTabs.setPreferredSize(new Dimension(400, (int) settings.getDouble("bottom.height", 300)));
		bottomTabs.setMinimumSize(new Dimension(100, 120));
		bottomSplit.addComponentListener(new ComponentAdapter()
		{
			private boolean placed;

			@Override
			public void componentResized(ComponentEvent e)
			{
				if (!placed && bottomSplit.getHeight() > 0)
				{
					// once, when the window first has its size: the maps get everything above the tabs
					placed = true;
					int bottom = (int) settings.getDouble("bottom.height", 300);
					bottomSplit.setDividerLocation(Math.max(200, bottomSplit.getHeight() - bottom - bottomSplit.getDividerSize()));
				}
			}
		});
		return bottomSplit;
	}

	/** Hover, click and selection handling of both views; the second view is placed once the window has a size. */
	private void listenToViews()
	{
		mainListener = viewListener(map);
		map.addListener(mainListener);
		destView.addListener(viewListener(destView));
		ctx.addSelectionListener(this::selectionChanged);
		mapRow.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				if (!destShown && mapRow.getWidth() > 0)
				{
					destShown = true;
					showDest();
				}
			}
		});
	}

	/** The question cards on the main map, and the link another program uses to show them and to drive the window. */
	private void startQuestionCards()
	{
		QuestionCards questions = new QuestionCards(ctx, map, new File(workspace, "cache/remote-link"), regionReview, this::author);
		questionCards = questions;
		map.addOverlay(changeLayer); // the game update's changed regions, under the cards' lines
		map.addOverlay(questions);
		new RemoteLink(workspace, new LinkWindow(questions)).start();
	}

	/** Maximized by default; otherwise the size the window had last time. */
	private void restoreWindowSize()
	{
		setSize(new Dimension((int) settings.getDouble("window.width", 1400), (int) settings.getDouble("window.height", 900)));
		setLocationRelativeTo(null);
		if (settings.get("window.maximized", "true").equals("true"))
		{
			setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
		}
	}

	/** What {@link RemoteLink} may see and do in this window. */
	private final class LinkWindow implements RemoteLink.Window
	{
		private final QuestionCards questions;

		LinkWindow(QuestionCards questions)
		{
			this.questions = questions;
		}

		@Override
		public QuestionCards questions()
		{
			return questions;
		}

		@Override
		public Set<Integer> regionIds()
		{
			return regionIds;
		}

		@Override
		public EditorContext ctx()
		{
			return ctx;
		}

		@Override
		public RegionReview review()
		{
			return regionReview;
		}

		@Override
		public void jump(int x, int y, int plane, double scale)
		{
			map.setPlane(plane);
			map.goTo(x, y, scale);
		}

		@Override
		public void showArea(int x0, int y0, int x1, int y1, int plane)
		{
			MainWindow.this.showArea(x0, y0, x1, y1, plane);
		}

		@Override
		public void clickTile(int x, int y, int plane)
		{
			map.setPlane(plane);
			mainListener.selected(x, y, plane);
		}

		@Override
		public void showReviewRegion(int regionId)
		{
			bottomTabs.setSelectedComponent(reviewPanel);
			reviewPanel.pickRegion(regionId);
		}

		@Override
		public void say(String text)
		{
			statusBar.say(text);
		}

		@Override
		public void toFrontNow()
		{
			toFront();
		}
	}

	private MainToolBar buildToolbar()
	{
		toolbar = new MainToolBar(map, () -> regionIds, List.of(
			MainToolBar.button("Download cache...", "Choose a cache from the OpenRS2 Archive and download it",
				() -> new DownloadDialog(this, new File(workspace, "cache"), this::openCache).setVisible(true)),
			MainToolBar.button("Open cache folder...", "Open a cache folder already on this computer", this::chooseFolder),
			MainToolBar.button("Item groups...",
				"Every item group with a picture of each item; click one to make it the group's default", this::openItemGroups),
			MainToolBar.button("Update plugin data...",
				"Make the plugin's data from this cache and every change here, check it, and put it into the plugin",
				this::openPluginExport),
			MainToolBar.button("Compare with a new cache...",
				"What a newer game cache changes for your data: a report, the changed regions on the map and a"
					+ " card for everything that needs a look", this::openCacheCompare),
			MainToolBar.button("Publish plugin...",
				"Copy only the plugin into the public plugin repo's folder, and build it there",
				() -> new PublishDialog(this, workspace, settings).setVisible(true))));
		return toolbar;
	}

	// --- hooks for UiCheck (developer screenshots); not used by the tool itself ---

	EditorContext devContext()
	{
		return ctx;
	}

	RouteController devRoute()
	{
		return routeController;
	}

	void devForViews(Consumer<MapView> change)
	{
		forViews(change);
	}

	MapView devMainMap()
	{
		return map;
	}

	QuestionCards devQuestionCards()
	{
		return questionCards;
	}

	void devSelectTab(int index)
	{
		bottomTabs.setSelectedIndex(index);
	}

	void devSelectTool(char key)
	{
		for (EditorTool t : palette.getTools())
		{
			if (t.getKey() == key)
			{
				palette.select(t);
			}
		}
	}

	RegionReviewPanel devReviewPanel()
	{
		return reviewPanel;
	}

	NetworksPanel devNetworksPanel()
	{
		return networksPanel;
	}

	RegionReview devRegionReview()
	{
		return regionReview;
	}

	String devStatus()
	{
		return statusBar.getDataText() + " | " + statusBar.getProgressText();
	}

	/** Phase of the 3D map pictures, or "OFF". */
	String devModelPhase()
	{
		return modelTiles == null ? "OFF" : modelTiles.getPhase().name();
	}

	/** The red layer, the test list and the other views follow every change, including those not built in yet. */
	private void refreshEditViews()
	{
		boolean[] hidden = ctx.effectiveHidden();
		boolean[] pockets = ctx.effectivePockets();
		Set<Integer> ignored = ctx.effectiveIgnoredRegions();
		forViews(v -> v.setHiddenData(ctx.graph, hidden, pockets, ignored));
		Set<String> decided = ctx.decidedSuggestions();
		forViews(v -> v.setDecidedSuggestions(decided)); // deleted or confirmed suggestions vanish at once
		testList.refresh();
		bottomTabs.setTitleAt(bottomTabs.indexOfComponent(testList), "Test in game (" + testList.itemCount() + ")");
		reviewPanel.refresh(); // ignored regions and "found here" follow the changes
		networksPanel.refresh(); // stops and rides follow the changes
		FreeLand free = ctx.freeLand();
		forViews(v -> v.setFreeLand(free)); // the green layer follows the Free land changes
		// deleted sea channels vanish at once (restored ones come back with the next build)
		Set<String> gone = ManualState.deletedChannels(ctx.edits.active());
		forViews(v -> v.setDeletedChannels(gone));
	}

	/** Applies a change to both the main map and the second view. */
	private void forViews(Consumer<MapView> change)
	{
		change.accept(map);
		change.accept(destView);
	}

	/** The second map view, always shown right of the main map; all tools work in it too. */
	private void buildDestPanel()
	{
		destPanel.add(destView, BorderLayout.CENTER);
		destPanel.setMinimumSize(new Dimension(150, 100));
		map.setMinimumSize(new Dimension(200, 100));
		destPanel.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Color.GRAY));
		destView.setTitleBar("Second view", null);
	}

	/** Hover and click handling, the same for both views. */
	private MapView.Listener viewListener(MapView view)
	{
		return new MapView.Listener()
		{
			@Override
			public void hovered(int x, int y, int plane)
			{
				int id = Regions.idOfTile(x, y);
				statusBar.showHover(String.format(Locale.ROOT, "Tile %d, %d, %d    Region %d    Local %d, %d    Zoom %.2f px/tile",
					x, y, plane, id, x & 63, y & 63, view.getViewport().getScale()));
			}

			@Override
			public void selected(int x, int y, int plane)
			{
				tileClicked(view, x, y, plane);
			}

			@Override
			public void planeChanged(int plane)
			{
				if (view == map)
				{
					toolbar.showPlane(plane);
				}
			}
		};
	}

	/**
	 * A click on a tile: the Inspector describes it, the Networks and Region review tabs follow it, and the transport
	 * or sea channel on it is selected.
	 */
	private void tileClicked(MapView view, int x, int y, int plane)
	{
		inspector.showTile(x, y, plane, regionIds, collision, seaMap, seaPieces, docks, coarseGraph, transportData, ctx.effectiveHidden(), ctx.effectivePockets());
		networksPanel.tileClicked(x, y, plane); // a new network stop starts on the tile clicked last
		if (bottomTabs.getSelectedComponent() == reviewPanel)
		{
			reviewPanel.pickRegion(Regions.idOfTile(x, y));
		}
		Object[] hit = ctx.hasData() ? ctx.hitTransport(x, y, plane) : null;
		if (hit != null)
		{
			ctx.select((EditorContext.Selection) hit[0], view, (Boolean) hit[1]);
		}
		else if (ctx.getSelection() != null)
		{
			ctx.select(null, view, false);
		}
		// a sea channel: highlighted and named
		SeaGraph sg = ctx.sailing == null ? null : ctx.sailing.getSeaGraph();
		int channel = hit == null && view.isShowChannels() ? ChannelLayer.channelAt(sg, x, y, plane) : -1;
		forViews(v -> v.setSelectedChannel(channel));
		ctx.selectChannel(channel >= 0 ? sg.channelPoints(channel) : null);
		if (channel >= 0)
		{
			optionsBar.showChannel(ChannelLayer.title(sg, channel) + "   (Delete: Del)");
		}
	}

	/** Keeps the second view's title in step with the selection. */
	private void selectionChanged()
	{
		EditorContext.Selection s = ctx.getSelection();
		if (s == null)
		{
			destView.setTitleBar("Second view", null);
			return;
		}
		int[] end = s.end();
		destView.setTitleBar(end == null ? s.name() + " (no end yet)"
			: String.format(Locale.ROOT, "%s: start/end at %d, %d, plane %d", s.name(), end[0], end[1], end[2]), null);
	}

	/** A tool was picked: its options show above the maps, and some tools switch on the layer or tab they work with. */
	private void toolSelected(EditorTool t)
	{
		optionsBar.showTool(t);
		if (t instanceof IgnoreRegionTool)
		{
			// this tool works on the red layer: show it (the Hide tool leaves the layer as it is)
			layers.showHiddenLayer();
		}
		if (t instanceof FreeLandTool)
		{
			// this tool works on the green layer: show it
			layers.showFreeLandLayer();
		}
		if (t instanceof RouteTool)
		{
			bottomTabs.setSelectedComponent(routePanel);
		}
		if (t instanceof WallTool || t instanceof BlockTool || t instanceof DoorTool)
		{
			// these tools make manual changes: show them hatched
			layers.showManualLayer();
		}
	}

	/** Tool shortcuts, Ctrl+Z (undo the last action) and Esc (cancel), unless typing in a text field. */
	private void installKeys()
	{
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e ->
		{
			if (e.getID() != KeyEvent.KEY_PRESSED || !isActive() || e.getComponent() instanceof JTextComponent)
			{
				return false;
			}
			if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_Z)
			{
				edits.undo();
				return true;
			}
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
			{
				ctx.creationStart = null;
				ctx.repaintAll();
				return true;
			}
			if (e.isControlDown() || e.isAltDown())
			{
				return false;
			}
			// whatever one key press changes is one undo step
			edits.beginGesture();
			SwingUtilities.invokeLater(edits::endGesture);
			return keyPressed(e);
		});
	}

	/** Del, a key for the selected transport, or a tool's shortcut; false when the key means nothing here. */
	private boolean keyPressed(KeyEvent e)
	{
		if (e.getKeyCode() == KeyEvent.VK_DELETE)
		{
			ctx.deleteSelected();
			return true;
		}
		// actions on the selected transport (the buttons above the maps)
		switch (Character.toLowerCase(e.getKeyChar()))
		{
			case 'n':
				ctx.nextSuggestion();
				return true;
			case 'c':
				ctx.confirmSelected();
				return true;
			case 's':
				optionsBar.skipNext();
				return true;
			case 'l':
				ctx.testLaterSelected();
				return true;
			case 'o':
				ctx.toggleTwoWaySelected();
				return true;
			default:
				break;
		}
		char c = Character.toLowerCase(e.getKeyChar());
		for (EditorTool t : palette.getTools())
		{
			if (t.getKey() == c)
			{
				palette.select(t);
				return true;
			}
		}
		return false;
	}

	/** Fits the main map to an area (tiles, inclusive) on a plane, with a margin. */
	private void showArea(int x0, int y0, int x1, int y1, int plane)
	{
		map.setPlane(plane);
		double sx = Math.max(100, map.getWidth() - 40) / (double) (x1 - x0 + 16);
		double sy = Math.max(100, map.getHeight() - 40) / (double) (y1 - y0 + 16);
		map.goTo((x0 + x1) / 2, (y0 + y1) / 2, Math.max(0.5, Math.min(16, Math.min(sx, sy))));
	}

	private void jumpMain(int x, int y, int plane)
	{
		map.setPlane(plane);
		map.goTo(x, y, Math.max(viewport.getScale(), 8));
	}

	/** The Item groups window (pictures need the open cache); returned for UiCheck. */
	ItemGroupsDialog openItemGroups()
	{
		if (cache == null)
		{
			JOptionPane.showMessageDialog(this, "Open a cache first: the item pictures are drawn from it.");
			return null;
		}
		ItemGroupsDialog d = new ItemGroupsDialog(this, cache, this::author, this::itemGroupUses, routeController::recompute);
		d.setVisible(true);
		return d;
	}

	/** The "Compare with a new cache" window; one at a time. Returned for UiCheck. */
	CacheCompareDialog openCacheCompare()
	{
		if (cache == null)
		{
			JOptionPane.showMessageDialog(this, "Open a cache first: it is compared with the new one.");
			return null;
		}
		if (cacheCompare != null && cacheCompare.isDisplayable())
		{
			cacheCompare.toFront();
			return cacheCompare;
		}
		cacheCompare = new CacheCompareDialog(this, workspace, cache.getFolder(), cache.getInfo().getOpenrs2Id(), changeLayer,
			this::syncCacheChecks, () ->
			{
				if (questionCards != null)
				{
					questionCards.showFirst();
				}
			}, this::openCache);
		cacheCompare.setVisible(true);
		return cacheCompare;
	}

	/**
	 * The newest game update comparison from the open cache on the map: its changed regions, and a card for each finding
	 * that needs a look and has no answer yet. After every data build and after a comparison.
	 */
	void syncCacheChecks()
	{
		if (cache == null || questionCards == null)
		{
			return;
		}
		CompareFile f = CompareFile.newest(workspace, cache.getInfo().getOpenrs2Id());
		changeLayer.set(f);
		questionCards.syncCacheChecks(f, ManualState.cacheChecks(edits.active()));
		map.repaint();
	}

	/** UiCheck: a comparison held in memory only, on the map and as cards, and the map at its first card. */
	void devCacheChecks(CompareFile f)
	{
		changeLayer.set(f);
		questionCards.syncCacheChecks(f, ManualState.cacheChecks(edits.active()));
		questionCards.showFirst(QuestionCards.Action.CACHE_CHECK);
		map.repaint();
	}

	/** The "Update plugin data" window; one at a time. Returned for UiCheck. */
	PluginExportDialog openPluginExport()
	{
		return openPluginExport(new File(workspace, "src/main/resources/com/bestpath/data"));
	}

	PluginExportDialog openPluginExport(File pluginDir)
	{
		if (cache == null)
		{
			JOptionPane.showMessageDialog(this, "Open a cache first: the plugin's data is made from it.");
			return null;
		}
		if (pluginExport != null && pluginExport.isDisplayable())
		{
			pluginExport.toFront();
			return pluginExport;
		}
		pluginExport = new PluginExportDialog(this, workspace, cache.getFolder(), pluginDir);
		pluginExport.setVisible(true);
		return pluginExport;
	}

	/** Where a requirement line names the item group ("item axe", "item axe worn"): transports, networks, test players. */
	private List<String> itemGroupUses(String group)
	{
		Pattern p = Pattern.compile("(?i)\\bitem\\s+" + Pattern.quote(group) + "(?=$|[\\s;|)])");
		List<String> out = new ArrayList<>();
		for (EditorContext.EffectiveTransport t : ctx.effectiveTransports())
		{
			TransportRecord r = t.record;
			if (!t.deleted && (p.matcher(r.requirements == null ? "" : r.requirements).find()
				|| p.matcher(r.requirementsBack == null ? "" : r.requirementsBack).find()))
			{
				out.add(r.name + " (" + r.id + ")");
			}
		}
		for (Networks.Network n : Networks.all())
		{
			if (p.matcher(n.requirement).find())
			{
				out.add("network " + n.name);
			}
		}
		for (TestPlayer tp : TestPlayers.load())
		{
			Set<String> keys = new HashSet<>();
			tp.inventory.keySet().forEach(k -> keys.add(k.trim().toLowerCase(Locale.ROOT)));
			tp.bank.keySet().forEach(k -> keys.add(k.trim().toLowerCase(Locale.ROOT)));
			if (keys.contains(group.toLowerCase(Locale.ROOT)))
			{
				out.add("test player " + tp.name + " (inventory or bank)");
			}
		}
		return out;
	}

	private String author()
	{
		return settings.get("author.name", "Rel");
	}

	/** Rebuilds the data from the changes (after editing paused); waits for a running build first. */
	private void rebuildData()
	{
		if (cache == null || !edits.needsBuild())
		{
			return;
		}
		if (building)
		{
			rebuildTimer.restart(); // look again once the running build is done
			return;
		}
		extractWalls(cache);
	}

	/**
	 * One undo step per mouse gesture: everything changed between a mouse
	 * button going down and coming up (a click, or a whole brush stroke).
	 * Seen before the clicked component handles the press.
	 */
	private void installGestures()
	{
		rebuildTimer.setRepeats(false);
		Toolkit.getDefaultToolkit().addAWTEventListener(ev ->
		{
			if (ev.getID() == MouseEvent.MOUSE_PRESSED)
			{
				edits.beginGesture();
			}
			else if (ev.getID() == MouseEvent.MOUSE_RELEASED)
			{
				SwingUtilities.invokeLater(edits::endGesture); // after the release itself is handled
			}
		}, AWTEvent.MOUSE_EVENT_MASK);
	}

	private void changeAuthorName()
	{
		String name = JOptionPane.showInputDialog(this, "Name recorded with your changes:", author());
		if (name != null && !name.isBlank())
		{
			settings.set("author.name", name.trim());
			settings.save();
		}
	}

	private void showDest()
	{
		if (mapRow.getRightComponent() == null)
		{
			mapRow.setRightComponent(destPanel);
			mapRow.setDividerSize(8);
			int w = Math.max(150, Math.min(destWidth, mapRow.getWidth() - 250));
			mapRow.setDividerLocation(Math.max(200, mapRow.getWidth() - w - mapRow.getDividerSize()));
		}
	}

	private void hideDest()
	{
		if (mapRow.getRightComponent() != null)
		{
			destWidth = mapRow.getWidth() - mapRow.getDividerLocation() - mapRow.getDividerSize();
			mapRow.setRightComponent(null);
			mapRow.setDividerSize(0);
		}
	}

	private void chooseFolder()
	{
		JFileChooser fc = new JFileChooser(new File(workspace, "cache"));
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setDialogTitle("Choose a folder containing an OSRS cache");
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			openCache(fc.getSelectedFile());
		}
	}

	/** Closes the open cache and opens the one in {@code dir} in the background. */
	private void openCache(File dir)
	{
		toolbar.setCacheText("Cache: opening " + dir.getName() + " ...");
		map.setEmptyMessage("Opening cache ...");
		closeCache();
		cacheExecutor.submit(() ->
		{
			try
			{
				backUpBefore(dir);
				LoadedCache c = LoadedCache.open(dir);
				CacheNames.load(c);
				MapLibrary lib = new MapLibrary(new File(workspace, "map-pictures"));
				lib.load(); // saved pictures, shown immediately
				SwingUtilities.invokeLater(() -> cacheOpened(c, lib));
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() ->
				{
					toolbar.setCacheText("Cache: none");
					map.setEmptyMessage("Could not open the cache:\n" + DownloadDialog.rootMessage(ex));
					JOptionPane.showMessageDialog(this, "Could not open the cache:\n" + DownloadDialog.rootMessage(ex),
						"Open cache", JOptionPane.ERROR_MESSAGE);
				});
			}
		});
	}

	/** A backup of the data before it meets another game cache; the cache is not opened when the backup fails. */
	private void backUpBefore(File dir) throws IOException
	{
		CacheInfo next = CacheInfo.find(dir);
		if (next != null)
		{
			try
			{
				Backups.beforeCache(workspace, next.getOpenrs2Id(),
					msg -> SwingUtilities.invokeLater(() -> toolbar.setCacheText("Cache: " + msg)));
			}
			catch (Exception ex)
			{
				throw new IOException("The backup before opening cache #" + next.getOpenrs2Id()
					+ " failed, so the cache was not opened: " + DownloadDialog.rootMessage(ex), ex);
			}
		}
	}

	private void cacheOpened(LoadedCache c, MapLibrary lib)
	{
		cache = c;
		library = lib;
		regionIds = c.getRegionIds();
		reviewPanel.setRegions(regionIds);
		String label = c.getInfo().getOpenrs2Id() >= 0 ? c.getInfo().label() : c.getFolder().getName();
		toolbar.setCacheText("Cache: " + label + " · " + regionIds.size() + " regions");
		tiles = new MapTileStore(c, lib, () ->
		{
			map.repaint();
			destView.repaint();
		});
		extractWalls(c);
		MapTileStore t = tiles;
		forViews(v -> v.setTiles(t, regionIds));
		updateModelTiles();
		settings.set("cache.last", c.getFolder().getAbsolutePath());
		settings.save();
	}

	/** Starts or stops the 3D map pictures to match the "3D map" layer box. */
	private void updateModelTiles()
	{
		if (layers.isModelsOn() && cache != null && modelTiles == null)
		{
			LoadedCache c = cache;
			cacheExecutor.submit(() ->
			{
				MapLibrary lib = new MapLibrary(new File(new File(workspace, "map-pictures"), "3d"));
				lib.load(); // saved pictures, shown immediately
				SwingUtilities.invokeLater(() ->
				{
					if (cache != c || !layers.isModelsOn() || modelTiles != null)
					{
						return; // cache closed or box turned off meanwhile
					}
					modelTiles = new MapTileStore(c, lib, () ->
					{
						map.repaint();
						destView.repaint();
					}, MapTileStore.Style.MODELS);
					MapTileStore m = modelTiles;
					forViews(v -> v.setModelTiles(m));
				});
			});
		}
		else if (!layers.isModelsOn() && modelTiles != null)
		{
			stopModelTiles();
		}
	}

	private void stopModelTiles()
	{
		MapTileStore m = modelTiles;
		modelTiles = null;
		forViews(v -> v.setModelTiles(null));
		if (m != null)
		{
			m.stop();
			try
			{
				m.join(); // saves what was drawn so far
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}
	}

	private void closeCache()
	{
		stopModelTiles();
		if (tiles != null)
		{
			tiles.stop();
			try
			{
				tiles.join();
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			tiles = null;
		}
		forViews(v -> v.setTiles(null, null));
		ctx.select(null, null, false);
		ctx.creationStart = null;
		clearData();
		if (cache != null)
		{
			try
			{
				cache.close();
			}
			catch (Exception ignored)
			{
				// closing a read-only store
			}
			cache = null;
		}
		regionIds = Set.of();
		reviewPanel.setRegions(regionIds);
	}

	/** Forgets the built data, in the views and in the editing state. */
	private void clearData()
	{
		collision = null;
		forViews(v -> v.setCollision(null));
		seaMap = null;
		seaPieces = null;
		forViews(v -> v.setSea(null, null));
		docks = null;
		forViews(v -> v.setDocks(null));
		coarseGraph = null;
		ctx.grid = null;
		ctx.graph = null;
		ctx.hidden = null;
		ctx.pockets = null;
		ctx.state = null;
		ctx.transports = null;
		ctx.pathfinder = null;
		ctx.sailing = null;
		forViews(v -> v.setCoarseGraph(null));
		forViews(v -> v.setHiddenData(null, null, null, null));
		transportData = null;
		forViews(v -> v.setTransportData(null));
		transportList.setData(null);
		statusBar.setDataText(" ");
	}

	/**
	 * Builds the data for the open cache in the background (see {@link DataBuilder}). Changes made meanwhile go on top
	 * until the next build (see rebuildData).
	 */
	private void extractWalls(LoadedCache c)
	{
		int gen = ++generation;
		building = true;
		statusBar.setDataText("Building data from the cache and your changes ...");
		// the change list is read here, on the Swing thread, and handed to the worker
		List<Edit> active = edits.active();
		Set<String> builtFrom = Set.copyOf(edits.activeIds());
		cacheExecutor.submit(() ->
		{
			try
			{
				DataBuilder.Result r = dataBuilder.build(c, active,
					msg -> SwingUtilities.invokeLater(() -> statusBar.setDataText(msg)), () -> ctx.sailing);
				SwingUtilities.invokeLater(() -> buildFinished(gen, r, builtFrom));
				dataBuilder.saveMovement(r); // for the next start
				// the plugin's data size last: the map is usable meanwhile (skipped when another build is waiting)
				if (gen != generation)
				{
					return;
				}
				String sized = dataBuilder.withPluginSize(r);
				SwingUtilities.invokeLater(() ->
				{
					if (gen == generation && cache == c)
					{
						statusBar.setDataText(sized);
					}
				});
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() ->
				{
					statusBar.setDataText("Walls failed: " + DownloadDialog.rootMessage(ex) + "    ");
					if (gen == generation)
					{
						building = false;
					}
				});
			}
		});
	}

	/** Swing thread: a build is done; its data is used when it is the latest build for the cache still open. */
	private void buildFinished(int gen, DataBuilder.Result r, Set<String> builtFrom)
	{
		if (gen == generation)
		{
			building = false; // the latest build is over (used or not)
		}
		if (gen != generation || cache != r.cache)
		{
			return;
		}
		showBuild(r);
		syncBuildCards(r);
		ctx.repaintAll();
		transportData = r.td;
		forViews(v -> v.setTransportData(r.td));
		transportList.setData(r.td);
		statusBar.setDataText(r.text);
		refreshEditViews();
		routeController.recompute(); // the data changed: search the route again
		edits.markBuilt(builtFrom); // changes made meanwhile stay on top, and start the next build
	}

	/** Hands a build's data to the views, the editing state and the Inspector. */
	private void showBuild(DataBuilder.Result r)
	{
		seaMap = r.sea;
		seaPieces = r.pieces;
		forViews(v -> v.setSea(r.sea, r.pieces));
		docks = r.dockList;
		forViews(v -> v.setDocks(r.dockList));
		collision = r.grid;
		forViews(v -> v.setCollision(r.grid));
		coarseGraph = r.cg;
		forViews(v -> v.setCoarseGraph(r.cg, null)); // pockets and hidden ones are on the "Ignored sub-regions" layer
		ctx.grid = r.grid;
		ctx.graph = r.cg;
		ctx.hidden = r.hidden;
		ctx.pockets = r.pocketFlags;
		ctx.state = r.manual;
		ctx.transports = r.td;
		ctx.pathfinder = r.pf;
		ctx.doors = r.doors;
		ctx.sailing = r.sailing;
		// the sea graph's channels, drawn on the maps
		forViews(v -> v.setSeaGraph(r.sailing.getSeaGraph()));
		inspector.setSeaGraph(r.sailing.getSeaGraph());
	}

	/** A question card for everything the build found that has no answer yet. */
	private void syncBuildCards(DataBuilder.Result r)
	{
		if (questionCards == null)
		{
			return;
		}
		// a Keep / Delete card for every sea channel
		questionCards.syncChannels(r.sailing.getSeaGraph(), ManualState.decidedChannels(edits.active()));
		// one Keep / Remove card per "go to nearest" place kind
		questionCards.syncPlaceKinds(r.placeCards, ManualState.placeKinds(edits.active()).decided);
		// one Boat / Land card per sea charting task off the open sea
		questionCards.syncChartingSpots(r.chartSpots, ManualState.chartingSpots(edits.active()).keySet());
		// one card per game update finding that needs a look
		syncCacheChecks();
	}

	private void updateProgress()
	{
		statusBar.showMapProgress(tiles, modelTiles, map);
	}

	private void redrawAll()
	{
		if (cache == null || library == null)
		{
			return;
		}
		int ok = JOptionPane.showConfirmDialog(this,
			"Delete the saved map pictures and draw them all again from this cache?\n"
				+ "This takes several minutes. Normally you never need this: changed regions are redrawn automatically.",
			"Redraw all map pictures", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (ok != JOptionPane.OK_OPTION)
		{
			return;
		}
		File folder = cache.getFolder();
		File libDir = library.getDir();
		closeCache();
		new File(libDir, "index.tsv").delete();
		new File(libDir, "thumbs-v2.bin.gz").delete();
		openCache(folder);
	}

	/** Closing: every change is already saved, so only the last notes need writing. */
	private void requestClose()
	{
		reviewPanel.saveNotes(); // notes typed in the last moment
		shutdown();
	}

	private void shutdown()
	{
		settings.set("view.x", Double.toString(viewport.getCenterX()));
		settings.set("view.y", Double.toString(viewport.getCenterY()));
		settings.set("view.scale", Double.toString(viewport.getScale()));
		settings.set("view.plane", Integer.toString(map.getPlane()));
		// window layout, for next time
		boolean maximized = (getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
		settings.set("window.maximized", Boolean.toString(maximized));
		if (!maximized)
		{
			settings.set("window.width", Integer.toString(getWidth()));
			settings.set("window.height", Integer.toString(getHeight()));
		}
		settings.set("bottom.height", Integer.toString(bottomSplit.getHeight() - bottomSplit.getDividerLocation() - bottomSplit.getDividerSize()));
		hideDest();
		settings.set("dest.width", Integer.toString(destWidth));
		settings.save();
		closeCache();
		cacheExecutor.shutdownNow();
		routeController.shutdown();
		dispose();
		System.exit(0);
	}
}
