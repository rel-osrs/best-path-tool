package com.bestpath.overlay;

import com.bestpath.core.data.WorldMaps;
import com.bestpath.route.DrawPath;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteResult;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapData;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the route on the game's world map (public RuneLite API only: the map
 * widget {@code InterfaceID.Worldmap.MAP_CONTAINER}, {@code WorldMap}'s middle
 * tile and zoom). It is hooked to the map widget's layer, so it is only drawn
 * while the world map is open; render() also checks the widget.
 *
 * <p>Threads: {@link #routeChanged} runs where the route service publishes (the
 * route worker for found routes) and builds the {@link WorldMapPath} there;
 * render() (client thread) only reads that immutable path and the immutable
 * {@link Progress}, then transforms and draws ({@link WorldMapPainter}).
 *
 * <p>Planes: the world map is flat, so every line and mark
 * of the route is drawn at its x / y whatever its plane; the player's own plane
 * at full strength, the others a little fainter ({@link WorldMapPainter}).
 *
 * <p>World maps: the game has several (the surface and one per underground area), and many
 * underground maps draw areas elsewhere than their game tiles. render() reads which map is open ({@link #shownMap});
 * when it changes, the lines are built again on the route worker for that map ({@link WorldMapPath.Placement}), and
 * render() draws only lines built for the open map.
 *
 * <p>The config box "Show route on world map" ({@code enabled}) switches it
 * off: render() then draws nothing and touches nothing.
 */
public class WorldMapRouteOverlay extends Overlay
{

	/** Boxes the game draws over the map picture. */
	private static final int[] OVER_MAP = {InterfaceID.Worldmap.OVERVIEW_CONTAINER, InterfaceID.Worldmap.MAPLIST_BOX_GRAPHIC0};

	private final Client client;
	private final Supplier<Progress> progress;
	private final BooleanSupplier enabled;
	/** Runs the line building off the client thread (the route worker). */
	private final Consumer<Runnable> worker;
	private final AtomicReference<WorldMapPath> path = new AtomicReference<>(WorldMapPath.EMPTY);
	private final WorldMapPainter painter = new WorldMapPainter();
	/** The route's colours, from the one colour the player picks; the default light blue until set. */
	private volatile Supplier<RouteColours> colours = RouteColours::defaults;
	private final WorldMapPainter.MapArea onShownMap;
	/** Where the game's world maps draw each area (from the map data; NONE until it loads). */
	private volatile WorldMaps maps = WorldMaps.NONE;
	/** The latest result and its redrawn walks (route worker writes; the worker builds the lines from them). */
	private volatile RouteResult result;
	private volatile DrawPath drawnWalks;
	/** The world map the lines are (to be) built for: -1 unknown. Set on the client thread, read on the worker. */
	private volatile int wantedMap = -1;

	/**
	 * @param enabled the config box "Show route on world map" (read on every frame; RuneLite's config proxy keeps
	 *                the value in memory)
	 * @param worker  runs a rebuild of the lines when another world map is opened (the route worker, never the client
	 *                thread)
	 */
	public WorldMapRouteOverlay(Client client, Supplier<Progress> progress, BooleanSupplier enabled, Consumer<Runnable> worker)
	{
		this.client = client;
		this.progress = progress;
		this.enabled = enabled;
		this.worker = worker;
		this.onShownMap = (x, y) ->
		{
			WorldMap map = client.getWorldMap();
			WorldMapData data = map == null ? null : map.getWorldMapData();
			return data == null || data.surfaceContainsPosition(x, y);
		};
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.MANUAL);
		setPriority(Overlay.PRIORITY_LOW);
		// drawn right after the map picture, under the map's own boxes (overview, map list) and RuneLite's icons
		drawAfterLayer(InterfaceID.Worldmap.MAP_CONTAINER);
	}

	/** Where the route's colours come from (the config's "Route colour"). */
	public void setColours(Supplier<RouteColours> c)
	{
		colours = c == null ? RouteColours::defaults : c;
	}

	/** The map data loaded (route worker): where the game's world maps draw each area. */
	public void setWorldMaps(WorldMaps m)
	{
		maps = m == null ? WorldMaps.NONE : m;
	}

	/** A result was published (any thread; normally the route worker): builds its world map lines there. */
	public void routeChanged(RouteResult r)
	{
		result = r;
		drawnWalks = null;
		int map = wantedMap;
		path.set(WorldMapPath.of(r, null, map, WorldMapPath.Placement.on(maps, map)));
	}

	/**
	 * A walk of this result was redrawn from the player's tile (route worker): rebuilds the lines there. Ignored if
	 * another result is shown by now.
	 */
	public void lineChanged(RouteResult r, DrawPath drawn)
	{
		if (result == r)
		{
			drawnWalks = drawn;
		}
		int map = wantedMap;
		WorldMapPath p = WorldMapPath.of(r, drawn, map, WorldMapPath.Placement.on(maps, map));
		path.updateAndGet(old -> old.getGeneration() == p.getGeneration() ? p : old);
	}

	/** Route worker: the lines again, for the world map open now (another map was opened). */
	private void rebuild()
	{
		int map = wantedMap;
		path.set(WorldMapPath.of(result, drawnWalks, map, WorldMapPath.Placement.on(maps, map)));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!enabled.getAsBoolean())
		{
			return null;
		}
		MapView view = view(client);
		if (view == null)
		{
			return null; // the world map is closed
		}
		int shown = shownMap(client, maps);
		if (shown != wantedMap)
		{
			wantedMap = shown;
			worker.accept(this::rebuild); // the lines for this map are built on the route worker, not here
		}
		WorldMapPath p = path.get();
		if (p.getMap() != shown || p.lineCount() == 0 && p.markCount() == 0 && p.getDestination() == -1)
		{
			return null; // nothing to draw, or the lines for this map are still being built
		}
		Shape oldClip = g.getClip();
		g.setClip(clipArea(client));
		painter.setColours(colours.get());
		painter.paint(g, p, view, client.getTopLevelWorldView().getPlane(), progress.get(), onShownMap);
		g.setClip(oldClip);
		return null;
	}

	/**
	 * Client thread: what the open world map shows, or null when it is closed (or not loaded). Also used by the
	 * right-click menu to turn the cursor into a tile.
	 */
	public static MapView view(Client client)
	{
		Widget w = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		WorldMap map = client.getWorldMap();
		if (w == null || w.isHidden() || map == null)
		{
			return null;
		}
		Rectangle b = w.getBounds();
		Point mid = map.getWorldMapPosition();
		float zoom = map.getWorldMapZoom();
		if (b == null || mid == null || !(zoom > 0) || b.width <= 0 || b.height <= 0)
		{
			return null;
		}
		return new MapView(b.x, b.y, b.width, b.height, mid.getX(), mid.getY(), zoom);
	}

	/**
	 * Client thread: which of the game's world maps is open, or -1 (closed, or its name not found). The map list
	 * button ({@code InterfaceID.Worldmap.MAPLIST_DISPLAY}) shows the open map's name in one of its texts (e.g.
	 * "Unquiet Ocean Underground"); the name is looked up in the cache's names.
	 */
	public static int shownMap(Client client, WorldMaps maps)
	{
		if (maps.mapCount() == 0)
		{
			return -1;
		}
		Widget w = client.getWidget(InterfaceID.Worldmap.MAPLIST_DISPLAY);
		if (w == null || w.isHidden())
		{
			return -1;
		}
		int own = maps.mapByName(w.getText());
		if (own >= 0)
		{
			return own;
		}
		Widget[] texts = w.getDynamicChildren();
		if (texts != null)
		{
			for (Widget t : texts)
			{
				int m = t == null ? -1 : maps.mapByName(t.getText());
				if (m >= 0)
				{
					return m;
				}
			}
		}
		return -1;
	}

	/**
	 * Client thread: the map's rectangle without the boxes drawn over it (the overview and the map list), or null
	 * when the map is closed.
	 */
	public static Shape clipArea(Client client)
	{
		Widget w = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (w == null)
		{
			return null;
		}
		Rectangle map = w.getBounds();
		Area area = null;
		for (int id : OVER_MAP)
		{
			Widget over = client.getWidget(id);
			if (over != null && !over.isHidden())
			{
				if (area == null)
				{
					area = new Area(map);
				}
				area.subtract(new Area(over.getBounds()));
			}
		}
		return area == null ? map : area;
	}

	/** Client thread: true if the canvas point is on the open world map's visible picture. */
	public static boolean onMap(Client client, int x, int y)
	{
		Shape s = clipArea(client);
		return s != null && s.contains(x, y);
	}
}
