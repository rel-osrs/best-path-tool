package com.bestpath.tool.map;

import com.bestpath.tool.geo.Regions;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The big map. Draws the map pictures for the current plane (with plane 0
 * dimmed underneath upper planes for context), the layers that are switched
 * on, the overlays and the active tool's marks. Drag to pan, wheel to zoom,
 * PageUp/PageDown to change plane.
 */
public final class MapView extends JPanel
{
	public interface Listener
	{
		/** Tile under the mouse changed (x, y may be outside the map). */
		void hovered(int x, int y, int plane);

		/** User clicked a tile. */
		void selected(int x, int y, int plane);

		void planeChanged(int plane);
	}

	private static final Color BACKGROUND = new Color(0x1b1e23);
	private static final Color NO_DATA = new Color(0x23272e);
	private static final java.awt.TexturePaint NOT_DRAWN = hatch();
	private static final Color HOVER = new Color(0xffd166);
	private static final Color SELECT = new Color(0x4cc9f0);

	private final MapViewport vp;
	private final List<Listener> listeners = new ArrayList<>();
	private MapTileStore tiles;
	/** 3D map pictures, shown instead of the world map where drawn; null when that layer is off. */
	private MapTileStore modelTiles;
	private Set<Integer> regionIds = Collections.emptySet();
	private int plane;
	private boolean showMap = true;
	private boolean showRegionGrid;
	private boolean showTileGrid;
	private boolean showWalls;
	private com.bestpath.tool.extract.FlagGrid collision;
	private final CoarseLayer coarse = new CoarseLayer();
	private final HiddenLayer hiddenLayer = new HiddenLayer();
	private final SeaLayer seaLayer = new SeaLayer();
	private final FreeLandLayer freeLandLayer = new FreeLandLayer();
	private final DockLayer dockLayer = new DockLayer();
	/** The sea graph's channels, drawn with the Sea, Transports or Docks layer. */
	private final ChannelLayer channelLayer = new ChannelLayer();
	/** The "Banks" layer (data/banks/banks.tsv). */
	private final BankLayer bankLayer = new BankLayer();
	private boolean showBanks;
	private java.util.List<com.bestpath.core.sea.Dock> docks;
	private boolean showDocks;
	private com.bestpath.core.sea.SeaMap sea;
	private com.bestpath.core.sea.SeaPieces seaPieces;
	private boolean showSea;
	private boolean showFreeLand;
	private boolean showHidden;
	private final TransportLayer transports = new TransportLayer();
	private boolean showTransports;
	private boolean showConfirmed;
	private boolean showTeleports;
	private boolean showComponents;
	private boolean showEntrances;
	private String emptyMessage = "No cache loaded.\nUse \"Download cache...\" or \"Open cache folder...\" above.";
	private String banner;

	private int hoverX = Integer.MIN_VALUE;
	private int hoverY;
	private int selX = Integer.MIN_VALUE;
	private int selY;
	private int dragX;
	private int dragY;
	private Interaction interaction;
	private boolean toolDragging;
	/** A press and release this close (pixels) is a click, not a drag. */
	private static final int CLICK_SLOP = 5;
	private int pressX;
	private int pressY;
	private double pressWorldX;
	private double pressWorldY;
	private boolean pressTaken;
	private final JPanel planeControl = new JPanel();
	private final JLabel planeLabel = new JLabel();
	/** Optional floating title bar (text + Close button) at the top-left of the view. */
	private final JPanel titleBar = new JPanel(new java.awt.BorderLayout(8, 0));
	private final JLabel titleLabel = new JLabel();
	private Runnable onTitleClose;
	private final JButton titleClose = new JButton("Close");

	public MapView(MapViewport viewport)
	{
		this.vp = viewport;
		setBackground(BACKGROUND);
		setFocusable(true);
		setLayout(null); // only child: the floating plane control, placed in doLayout()
		buildPlaneControl();
		buildTitleBar();
		installMouseHandling();
		addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				onKeyPressed(e);
			}
		});
	}

	private void installMouseHandling()
	{
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onMousePressed(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				onMouseReleased(e);
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				onMouseDragged(e);
			}

			@Override
			public void mouseMoved(MouseEvent e)
			{
				onMouseMoved(e);
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e)
			{
				onMouseWheelMoved(e);
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);
	}

	private void onMousePressed(MouseEvent e)
	{
		requestFocusInWindow();
		dragX = e.getX();
		dragY = e.getY();
		// the left button belongs to the active tool (the right one too if it asks); otherwise dragging pans
		toolDragging = interaction != null
			&& (javax.swing.SwingUtilities.isLeftMouseButton(e)
			|| (javax.swing.SwingUtilities.isRightMouseButton(e) && interaction.usesRightButton()))
			&& interaction.pressed(this, vp.worldX(e.getX()), vp.worldY(e.getY()), e);
		pressX = e.getX();
		pressY = e.getY();
		pressWorldX = vp.worldX(e.getX());
		pressWorldY = vp.worldY(e.getY());
		pressTaken = toolDragging;
		repaint();
	}

	private void onMouseReleased(MouseEvent e)
	{
		if (toolDragging)
		{
			toolDragging = false;
			interaction.released(this, vp.worldX(e.getX()), vp.worldY(e.getY()), e);
			repaint();
		}
		// a click, even if the mouse moved a few pixels (Java's own "clicked" event needs no movement at all,
		// so it would miss a slightly shaky click)
		boolean click = isClick(e, MouseEvent.BUTTON1);
		if (click && (interaction == null || interaction.clickSelects()))
		{
			selX = (int) Math.floor(pressWorldX);
			selY = (int) Math.floor(pressWorldY);
			listeners.forEach(l -> l.selected(selX, selY, plane));
			repaint();
		}
		// a right click (no drag) the tool did not take: the tool may offer a menu
		boolean rightClick = isClick(e, MouseEvent.BUTTON3);
		if (rightClick && interaction != null)
		{
			interaction.rightClicked(this, pressWorldX, pressWorldY, e);
			repaint();
		}
	}

	/** True if this release of {@code button} ends a press the tool did not take, within {@link #CLICK_SLOP} pixels. */
	private boolean isClick(MouseEvent e, int button)
	{
		return e.getButton() == button && !pressTaken
			&& Math.abs(e.getX() - pressX) <= CLICK_SLOP && Math.abs(e.getY() - pressY) <= CLICK_SLOP;
	}

	private void onMouseDragged(MouseEvent e)
	{
		if (toolDragging)
		{
			interaction.dragged(this, vp.worldX(e.getX()), vp.worldY(e.getY()), e);
			updateHover(e.getX(), e.getY());
			repaint();
			return;
		}
		vp.panPixels(e.getX() - dragX, e.getY() - dragY);
		dragX = e.getX();
		dragY = e.getY();
		updateHover(e.getX(), e.getY());
		repaint();
	}

	private void onMouseMoved(MouseEvent e)
	{
		updateHover(e.getX(), e.getY());
		if (interaction != null)
		{
			interaction.moved(this, vp.worldX(e.getX()), vp.worldY(e.getY()));
			repaint();
		}
	}

	private void onMouseWheelMoved(MouseWheelEvent e)
	{
		double factor = Math.pow(1.25, -e.getPreciseWheelRotation());
		vp.zoomAt(e.getX(), e.getY(), factor);
		updateHover(e.getX(), e.getY());
		repaint();
	}

	/** Arrow keys pan, plus/minus zoom, PageUp/PageDown change the plane. */
	private void onKeyPressed(KeyEvent e)
	{
		int step = 80;
		switch (e.getKeyCode())
		{
			case KeyEvent.VK_LEFT:
				vp.panPixels(step, 0);
				break;
			case KeyEvent.VK_RIGHT:
				vp.panPixels(-step, 0);
				break;
			case KeyEvent.VK_UP:
				vp.panPixels(0, step);
				break;
			case KeyEvent.VK_DOWN:
				vp.panPixels(0, -step);
				break;
			case KeyEvent.VK_PLUS:
			case KeyEvent.VK_EQUALS:
			case KeyEvent.VK_ADD:
				vp.zoomAt(getWidth() / 2.0, getHeight() / 2.0, 1.5);
				break;
			case KeyEvent.VK_MINUS:
			case KeyEvent.VK_SUBTRACT:
				vp.zoomAt(getWidth() / 2.0, getHeight() / 2.0, 1 / 1.5);
				break;
			case KeyEvent.VK_PAGE_UP:
				setPlane(Math.min(3, plane + 1));
				break;
			case KeyEvent.VK_PAGE_DOWN:
				setPlane(Math.max(0, plane - 1));
				break;
			default:
				return;
		}
		repaint();
	}

	/**
	 * The active editing tool, which receives the left mouse button (world
	 * coordinates in tiles, fractional). Right/middle drag always pans.
	 */
	public interface Interaction
	{
		/** Return true to take the drag (no panning). */
		boolean pressed(MapView view, double wx, double wy, MouseEvent e);

		void dragged(MapView view, double wx, double wy, MouseEvent e);

		void released(MapView view, double wx, double wy, MouseEvent e);

		void moved(MapView view, double wx, double wy);

		/** Draws the tool's own marks (hover, drag preview) on top of the map. */
		void paint(Graphics2D g, MapView view);

		/** True if a plain click should still select and inspect the tile. */
		boolean clickSelects();

		/** True if the tool also takes the right button (then only the middle button pans). */
		default boolean usesRightButton()
		{
			return false;
		}

		/** A right click without dragging (only when {@link #usesRightButton()} is false; right-drag still pans). */
		default void rightClicked(MapView view, double wx, double wy, MouseEvent e)
		{
		}
	}

	public void setInteraction(Interaction i, java.awt.Cursor cursor)
	{
		interaction = i;
		toolDragging = false;
		setCursor(cursor);
		repaint();
	}

	public MapViewport getViewport()
	{
		return vp;
	}

	/** Extra drawing on top of the map (pending changes, confirmed transports). */
	public interface Overlay
	{
		void paint(Graphics2D g, MapView view);
	}

	private final List<Overlay> overlays = new ArrayList<>();

	public void addOverlay(Overlay o)
	{
		overlays.add(o);
	}

	public void addListener(Listener l)
	{
		listeners.add(l);
	}

	public void setTiles(MapTileStore tiles, Set<Integer> regionIds)
	{
		this.tiles = tiles;
		this.regionIds = regionIds == null ? Collections.emptySet() : regionIds;
		repaint();
	}

	public void setEmptyMessage(String message)
	{
		this.emptyMessage = message;
		repaint();
	}

	public void setPlane(int plane)
	{
		if (plane != this.plane)
		{
			this.plane = plane;
			updatePlaneControl();
			listeners.forEach(l -> l.planeChanged(plane));
			if (hoverX != Integer.MIN_VALUE)
			{
				listeners.forEach(l -> l.hovered(hoverX, hoverY, plane));
			}
			repaint();
		}
	}

	private void buildPlaneControl()
	{
		JButton down = new JButton("-");
		down.setToolTipText("Go down one plane (floor) - PageDown");
		down.addActionListener(e -> setPlane(Math.max(0, plane - 1)));
		JButton up = new JButton("+");
		up.setToolTipText("Go up one plane (floor) - PageUp");
		up.addActionListener(e -> setPlane(Math.min(3, plane + 1)));
		for (JButton b : new JButton[]{down, up})
		{
			b.setFocusable(false);
			b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
			b.setMargin(new java.awt.Insets(2, 10, 2, 10));
		}
		planeLabel.setForeground(Color.WHITE);
		planeLabel.setFont(planeLabel.getFont().deriveFont(Font.BOLD, 14f));
		planeControl.setBackground(new Color(0x202329));
		planeControl.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		planeControl.add(down);
		planeControl.add(planeLabel);
		planeControl.add(up);
		add(planeControl);
		updatePlaneControl();
	}

	private void updatePlaneControl()
	{
		planeLabel.setText(" Plane " + plane + " ");
		planeControl.setSize(planeControl.getPreferredSize());
		doLayout();
	}

	private void buildTitleBar()
	{
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
		titleClose.setFocusable(false);
		JButton close = titleClose;
		close.addActionListener(e ->
		{
			if (onTitleClose != null)
			{
				onTitleClose.run();
			}
		});
		titleBar.setBackground(new Color(0x202329));
		titleBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));
		titleBar.add(titleLabel, java.awt.BorderLayout.CENTER);
		titleBar.add(close, java.awt.BorderLayout.EAST);
		titleBar.setVisible(false);
		add(titleBar);
	}

	/** Shows a floating title with a Close button at the top of the view; null text hides it. */
	public void setTitleBar(String text, Runnable onClose)
	{
		onTitleClose = onClose;
		titleClose.setVisible(onClose != null);
		titleLabel.setText(text);
		titleBar.setVisible(text != null);
		doLayout();
		repaint();
	}

	@Override
	public void doLayout()
	{
		java.awt.Dimension d = planeControl.getPreferredSize();
		planeControl.setBounds(getWidth() - d.width - 12, getHeight() - d.height - 12, d.width, d.height);
		java.awt.Dimension t = titleBar.getPreferredSize();
		titleBar.setBounds(8, 8, Math.min(t.width, Math.max(0, getWidth() - 16)), t.height);
	}

	public int getPlane()
	{
		return plane;
	}

	/** Shows 3D map pictures from {@code store} (null: world map only). */
	public void setModelTiles(MapTileStore store)
	{
		modelTiles = store;
		repaint();
	}

	/**
	 * The 3D picture where that layer is on and the picture is drawn; else the
	 * world-map picture if that layer is on (null: nothing to show yet). Either
	 * layer works on its own.
	 */
	private BufferedImage picture(int id, int plane, double regionPx)
	{
		MapTileStore m = modelTiles;
		if (m != null)
		{
			BufferedImage img = m.get(id, plane, regionPx);
			if (img != null || !showMap)
			{
				return img;
			}
		}
		return showMap ? tiles.get(id, plane, regionPx) : null;
	}

	public void setShowMap(boolean show)
	{
		showMap = show;
		repaint();
	}

	public void setShowRegionGrid(boolean show)
	{
		showRegionGrid = show;
		repaint();
	}

	public void setShowWalls(boolean show)
	{
		showWalls = show;
		repaint();
	}

	/** Transport candidates, suggestions and cache-table destinations to draw, or null. */
	public void setTransportData(com.bestpath.tool.transport.TransportData data)
	{
		transports.setData(data);
		repaint();
	}

	/** Candidate and teleport ids deleted or confirmed since the data was built: hidden at once, before the rebuild. */
	public void setDecidedSuggestions(java.util.Set<String> ids)
	{
		transports.setDecided(ids);
		repaint();
	}

	public void setShowTransports(boolean show)
	{
		showTransports = show;
		repaint();
	}

	/** Whether confirmed transports are drawn (see EditOverlay). */
	public boolean isShowConfirmed()
	{
		return showConfirmed;
	}

	public void setShowConfirmed(boolean show)
	{
		showConfirmed = show;
		repaint();
	}

	/** Highlights one transport: a solid line from its start to the given end tile. Null clears it. */
	public void setSelectedTransport(com.bestpath.tool.transport.TransportCandidate c, com.bestpath.tool.transport.Suggestion end)
	{
		transports.setSelected(c, end);
		repaint();
	}

	public void setShowTeleports(boolean show)
	{
		showTeleports = show;
		repaint();
	}

	/** The coarse graph (sub-regions and entrances) to draw, or null. */
	public void setCoarseGraph(com.bestpath.core.graph.CoarseGraph graph)
	{
		setCoarseGraph(graph, null);
	}

	/** {@code grey}: sub-regions left out of the sub-region layer; null = the pockets. */
	public void setCoarseGraph(com.bestpath.core.graph.CoarseGraph graph, boolean[] grey)
	{
		coarse.setGraph(graph, grey);
		repaint();
	}

	/**
	 * Hidden sub-regions, closed-off pockets (null = all of them) and ignored regions, changes not yet built
	 * into the data included: shown on the "Ignored sub-regions" layer and left out of the sub-region layer.
	 */
	public void setHiddenData(com.bestpath.core.graph.CoarseGraph graph, boolean[] hidden, boolean[] pockets, java.util.Set<Integer> ignored)
	{
		hiddenLayer.setData(graph, hidden, pockets, ignored);
		coarse.setExcluded(hidden, pockets, ignored); // what is left out disappears from the sub-region layer
		repaint();
	}

	public void setShowHidden(boolean show)
	{
		showHidden = show;
		repaint();
	}

	public void setShowComponents(boolean show)
	{
		showComponents = show;
		repaint();
	}

	public void setShowEntrances(boolean show)
	{
		showEntrances = show;
		repaint();
	}

	/** Extracted movement flags to draw, or null. */
	public void setCollision(com.bestpath.tool.extract.FlagGrid grid)
	{
		collision = grid;
		seaLayer.setData(sea, seaPieces);
		repaint();
	}

	/** The sea and its pieces to draw on the "Sea (sailing)" layer, or nulls. */
	public void setSea(com.bestpath.core.sea.SeaMap sea, com.bestpath.core.sea.SeaPieces pieces)
	{
		this.sea = sea;
		this.seaPieces = pieces;
		seaLayer.setData(sea, pieces);
		dockLayer.setData(docks, pieces);
		repaint();
	}

	/** The sea graph whose channels (crooked water the boats' links miss) are drawn, or null. */
	public void setSeaGraph(com.bestpath.core.sea.SeaGraph graph)
	{
		channelLayer.setData(graph);
		repaint();
	}

	/** Sea channels deleted by changes ({@code SeaChannels.key}): not drawn, even before the data is built again. */
	public void setDeletedChannels(java.util.Set<String> keys)
	{
		channelLayer.setDeleted(keys);
		repaint();
	}

	/** Highlights a sea channel (its number in the sea graph), or none (-1). */
	public void setSelectedChannel(int channel)
	{
		channelLayer.setSelected(channel);
		repaint();
	}

	/** Whether the sea channels are drawn (with the Sea, Suggested transports or Docks layer): only then can one be clicked. */
	public boolean isShowChannels()
	{
		return showSea || showTransports || showDocks;
	}

	/** The sailing docks to draw on the "Docks" layer, or null. */
	public void setDocks(java.util.List<com.bestpath.core.sea.Dock> docks)
	{
		this.docks = docks;
		dockLayer.setData(docks, seaPieces);
		repaint();
	}

	public void setShowDocks(boolean show)
	{
		showDocks = show;
		repaint();
	}

	/** The "Banks" layer: booths, chests and bankers routes may start at, with the tiles they are used from. */
	public void setShowBanks(boolean show)
	{
		showBanks = show;
		repaint();
	}

	public void setShowSea(boolean show)
	{
		showSea = show;
		repaint();
	}

	/** Free-to-play land to draw on the "Free-to-play land" layer (the drawn file with the changes on top). */
	public void setFreeLand(com.bestpath.core.collision.FreeLand land)
	{
		freeLandLayer.setData(land);
		repaint();
	}

	public void setShowFreeLand(boolean show)
	{
		showFreeLand = show;
		repaint();
	}

	public void setShowTileGrid(boolean show)
	{
		showTileGrid = show;
		repaint();
	}

	/** Centres the view on a tile and marks it, WITHOUT telling listeners (no "selected" event). */
	public void showTile(int x, int y, double scale)
	{
		vp.centerOn(x + 0.5, y + 0.5);
		if (scale > 0)
		{
			vp.setScale(scale);
		}
		selX = x;
		selY = y;
		repaint();
	}

	/** Centres the view on a tile and marks it selected. */
	public void goTo(int x, int y, double scale)
	{
		vp.centerOn(x + 0.5, y + 0.5);
		if (scale > 0)
		{
			vp.setScale(scale);
		}
		selX = x;
		selY = y;
		listeners.forEach(l -> l.selected(x, y, plane));
		repaint();
	}

	private int tileX(int sx)
	{
		return (int) Math.floor(vp.worldX(sx));
	}

	private int tileY(int sy)
	{
		return (int) Math.floor(vp.worldY(sy));
	}

	private void updateHover(int sx, int sy)
	{
		int x = tileX(sx);
		int y = tileY(sy);
		if (x != hoverX || y != hoverY)
		{
			hoverX = x;
			hoverY = y;
			listeners.forEach(l -> l.hovered(x, y, plane));
			repaint();
		}
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		super.paintComponent(g0);
		Graphics2D g = (Graphics2D) g0;
		vp.setSize(getWidth(), getHeight());

		if (tiles == null)
		{
			drawCentredText(g, emptyMessage);
			return;
		}

		double s = vp.getScale();
		double regionPx = s * Regions.SIZE;
		int rx0 = (int) Math.floor(vp.worldX(0) / Regions.SIZE);
		int rx1 = (int) Math.floor(vp.worldX(getWidth()) / Regions.SIZE);
		int ry0 = (int) Math.floor(vp.worldY(getHeight()) / Regions.SIZE);
		int ry1 = (int) Math.floor(vp.worldY(0) / Regions.SIZE);
		rx0 = Math.max(rx0, 0);
		ry0 = Math.max(ry0, 0);
		rx1 = Math.min(rx1, 255);
		ry1 = Math.min(ry1, 255);

		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, s >= 4
			? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
			: RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		drawRegionPictures(g, rx0, rx1, ry0, ry1, regionPx);
		drawRegionLayers(g, rx0, rx1, ry0, ry1, s, regionPx);
		drawTileLayers(g);
		if (showTileGrid && s >= 12)
		{
			GridLayer.drawTileGrid(g, vp, getWidth(), getHeight());
		}
		if (showRegionGrid && regionPx >= 6)
		{
			GridLayer.drawRegionGrid(g, vp, getWidth(), getHeight(), getFont(), regionIds, rx0, rx1, ry0, ry1, regionPx);
		}
		drawTileMarker(g, selX, selY, SELECT);
		drawTileMarker(g, hoverX, hoverY, HOVER);
		for (Overlay o : overlays)
		{
			o.paint(g, this);
		}
		if (interaction != null)
		{
			interaction.paint(g, this);
		}
		drawBanner(g);
	}

	/** The map pictures of the visible regions (regions rx0..rx1, ry0..ry1). */
	private void drawRegionPictures(Graphics2D g, int rx0, int rx1, int ry0, int ry1, double regionPx)
	{
		for (int rx = rx0; rx <= rx1; rx++)
		{
			for (int ry = ry0; ry <= ry1; ry++)
			{
				int id = Regions.id(rx, ry);
				if (!regionIds.contains(id))
				{
					continue;
				}
				drawRegionPicture(g, id, rx, ry, regionPx);
			}
		}
	}

	private void drawRegionPicture(Graphics2D g, int id, int rx, int ry, double regionPx)
	{
		int x0 = (int) Math.floor(vp.screenX(rx * Regions.SIZE));
		int x1 = (int) Math.floor(vp.screenX((rx + 1) * Regions.SIZE));
		int yTop = (int) Math.floor(vp.screenY((ry + 1) * Regions.SIZE));
		int yBot = (int) Math.floor(vp.screenY(ry * Regions.SIZE));
		g.setColor(NO_DATA);
		g.fillRect(x0, yTop, x1 - x0, yBot - yTop);
		if (!showMap && modelTiles == null)
		{
			return; // neither map picture layer is on
		}
		if (plane > 0)
		{
			// plane 0 underneath, dimmed, for context
			BufferedImage base = picture(id, 0, regionPx);
			if (base != null && base != MapTileStore.EMPTY)
			{
				Composite c = g.getComposite();
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				g.drawImage(base, x0, yTop, x1 - x0, yBot - yTop, null);
				g.setComposite(c);
			}
		}
		BufferedImage img = picture(id, plane, regionPx);
		if (img == null)
		{
			// not drawn yet: hatched, so it looks different from "no map data"
			g.setPaint(NOT_DRAWN);
			g.fillRect(x0, yTop, x1 - x0, yBot - yTop);
		}
		else if (img != MapTileStore.EMPTY)
		{
			g.drawImage(img, x0, yTop, x1 - x0, yBot - yTop, null);
		}
	}

	/** The layers drawn per region or per tile area: sea, free land, sub-regions, hidden, walls and entrances. */
	private void drawRegionLayers(Graphics2D g, int rx0, int rx1, int ry0, int ry1, double s, double regionPx)
	{
		if (showSea)
		{
			seaLayer.draw(g, vp, plane, rx0, rx1, ry0, ry1, regionIds);
		}
		if (showFreeLand)
		{
			freeLandLayer.draw(g, vp, rx0, rx1, ry0, ry1);
		}
		if (showComponents)
		{
			coarse.drawComponents(g, vp, plane, rx0, rx1, ry0, ry1, regionIds);
		}
		if (showHidden)
		{
			hiddenLayer.draw(g, vp, plane, rx0, rx1, ry0, ry1, regionIds);
		}
		if (showWalls && collision != null && s >= WALLS_MIN_SCALE)
		{
			WallLayer.draw(g, vp, collision, plane, regionIds, getWidth(), getHeight());
		}
		if (showEntrances && regionPx >= 48)
		{
			coarse.drawEntrances(g, vp, plane, vp.worldX(0), vp.worldX(getWidth()), vp.worldY(getHeight()), vp.worldY(0));
		}
	}

	/** The layers of single places in the visible tiles: transports, teleports, docks, channels, banks, the selection. */
	private void drawTileLayers(Graphics2D g)
	{
		int tx0 = (int) Math.floor(vp.worldX(0));
		int tx1 = (int) Math.ceil(vp.worldX(getWidth()));
		int ty0 = (int) Math.floor(vp.worldY(getHeight()));
		int ty1 = (int) Math.ceil(vp.worldY(0));
		if (showTransports)
		{
			transports.drawCandidates(g, vp, plane, tx0, ty0, tx1, ty1);
		}
		if (showTeleports)
		{
			transports.drawDestinations(g, vp, plane, tx0, ty0, tx1, ty1);
		}
		if (showDocks)
		{
			dockLayer.draw(g, vp, plane, tx0, ty0, tx1, ty1);
		}
		if (showSea || showTransports || showDocks)
		{
			channelLayer.draw(g, vp, plane, tx0, ty0, tx1, ty1);
		}
		if (showBanks)
		{
			bankLayer.draw(g, vp, plane, tx0, ty0, tx1, ty1);
		}
		transports.drawSelected(g, vp, plane);
	}

	/** Walls and blocked tiles are drawn from this zoom (px per tile) upwards. */
	public static final double WALLS_MIN_SCALE = 3;

	private void drawTileMarker(Graphics2D g, int x, int y, Color c)
	{
		if (x == Integer.MIN_VALUE)
		{
			return;
		}
		int sx = (int) Math.floor(vp.screenX(x));
		int sy = (int) Math.floor(vp.screenY(y + 1));
		int size = Math.max(3, (int) Math.ceil(vp.getScale()));
		g.setColor(c);
		g.drawRect(sx, sy, size, size);
	}

	/** Shows a status message (e.g. map drawing progress) across the top of the map; null hides it. */
	public void setBanner(String text)
	{
		if (text == null ? banner != null : !text.equals(banner))
		{
			banner = text;
			repaint();
		}
	}

	private void drawBanner(Graphics2D g)
	{
		if (banner == null)
		{
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(getFont().deriveFont(Font.BOLD, 13f));
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(banner) + 24;
		int h = fm.getHeight() + 12;
		int x = (getWidth() - w) / 2;
		int y = 10;
		g.setColor(new Color(0, 0, 0, 190));
		g.fillRoundRect(x, y, w, h, 12, 12);
		g.setColor(new Color(0xffd166));
		g.drawRoundRect(x, y, w, h, 12, 12);
		g.setColor(Color.WHITE);
		g.drawString(banner, x + 12, y + 6 + fm.getAscent());
	}

	private static java.awt.TexturePaint hatch()
	{
		BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(0x2a2f37));
		g.fillRect(0, 0, 8, 8);
		g.setColor(new Color(0x3d4450));
		g.drawLine(0, 7, 7, 0);
		g.dispose();
		return new java.awt.TexturePaint(img, new java.awt.Rectangle(0, 0, 8, 8));
	}

	private void drawCentredText(Graphics2D g, String text)
	{
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0xc8ccd4));
		g.setFont(getFont().deriveFont(15f));
		FontMetrics fm = g.getFontMetrics();
		String[] lines = text.split("\n");
		int y = getHeight() / 2 - lines.length * fm.getHeight() / 2;
		for (String line : lines)
		{
			g.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, y + fm.getAscent());
			y += fm.getHeight();
		}
	}
}
