package com.bestpath.menu;

import com.bestpath.BestPathConfig;
import com.bestpath.core.data.WorldMaps;
import com.bestpath.core.path.Tiles;
import com.bestpath.overlay.MapView;
import com.bestpath.overlay.WorldMapRouteOverlay;
import com.bestpath.route.RouteRequest;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOpened;

/**
 * Adds Best Path's right-click entries when a menu opens (client thread):
 * "Set destination" on the world map (and, if switched on, on the game scene
 * next to "Walk here") and "Clear destination" on the world map. Rules: {@link
 * MenuChoice}. The entries are RuneLite-only ({@link MenuAction#RUNELITE}):
 * clicking them sends nothing to the game server; the click only asks for a
 * route, and the search runs on the route worker.
 *
 * <p>World map tile: the cursor through {@link MapView} (the map's middle tile
 * and zoom). The map gives no plane: the request is {@link RouteRequest#fromMap},
 * and the route search picks the plane with ground there (plane 0 when it has
 * ground near, else the plane whose ground is nearest), whatever plane the
 * player is on. A game tile keeps its own plane.
 *
 * <p>The spot on the map is turned into the game tile the open world map draws there (underground maps draw many
 * areas elsewhere; core {@code WorldMaps#mapClick}): when that spot shows one plane only, the request is that plane;
 * an empty spot whose own tiles the map draws elsewhere gets no "Set destination".
 */
public final class DestinationMenu
{
	static final String SET = "Set destination";
	static final String CLEAR = "Clear destination";

	/** What the entries do (client thread). */
	public interface Target
	{
		void setDestination(RouteRequest request);

		void clearDestination();

		boolean hasDestination();
	}

	private final Client client;
	private final BestPathConfig config;
	private final Target target;
	/** Where the game's world maps draw each area (NONE until the map data loads). */
	private final Supplier<WorldMaps> maps;
	/** The plane the open world map draws at the right-clicked spot, or -1: the route search picks it. */
	private int mapPlane = -1;

	public DestinationMenu(Client client, BestPathConfig config, Target target, Supplier<WorldMaps> maps)
	{
		this.client = client;
		this.config = config;
		this.target = target;
		this.maps = maps;
	}

	/** Client thread: a menu was opened (right click). */
	public void onMenuOpened(MenuOpened e)
	{
		// minimal mode has no side panel, so both right-click menus are how routes start
		boolean mapOption = config.worldMapMenu() || config.minimalMode();
		boolean sceneOption = config.sceneMenu() || config.minimalMode();
		if (!mapOption && !sceneOption)
		{
			return;
		}
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return;
		}
		WorldView top = client.getTopLevelWorldView();
		boolean onMap = WorldMapRouteOverlay.onMap(client, mouse.getX(), mouse.getY());
		int mapTile = -1;
		mapPlane = -1;
		if (onMap && mapOption)
		{
			MapView v = WorldMapRouteOverlay.view(client);
			if (v != null)
			{
				// the spot on the open map -> the game tile drawn there (underground maps draw many areas elsewhere)
				WorldMaps m = maps.get();
				int[] click = m.mapClick(WorldMapRouteOverlay.shownMap(client, m), v.tileX(mouse.getX()), v.tileY(mouse.getY()));
				if (click != null)
				{
					mapTile = MenuChoice.mapTile(click[0], click[1]);
					mapPlane = click[2];
				}
			}
		}
		int walk = -1;
		int sceneTile = -1;
		if (!onMap && sceneOption)
		{
			MenuEntry[] entries = e.getMenuEntries();
			walk = walkIndex(entries);
			if (walk >= 0)
			{
				sceneTile = selectedTile(top);
			}
		}
		MenuChoice c = MenuChoice.decide(mapOption, sceneOption, onMap, mapTile, walk, sceneTile,
			target.hasDestination());
		add(c);
	}

	private void add(MenuChoice c)
	{
		if (c.getPlace() == MenuChoice.Place.NONE)
		{
			return;
		}
		if (c.hasClear())
		{
			client.getMenu().createMenuEntry(-1)
				.setOption(CLEAR)
				.setTarget("")
				.setType(MenuAction.RUNELITE)
				.onClick(m -> target.clearDestination());
		}
		if (c.hasSet())
		{
			int t = c.getSetTile();
			RouteRequest request = c.getPlace() != MenuChoice.Place.WORLD_MAP ? new RouteRequest(Tiles.x(t), Tiles.y(t), Tiles.plane(t))
				: mapPlane >= 0 ? new RouteRequest(Tiles.x(t), Tiles.y(t), mapPlane) // the map draws only this plane there
				: RouteRequest.fromMap(Tiles.x(t), Tiles.y(t));
			client.getMenu().createMenuEntry(c.getPlace() == MenuChoice.Place.SCENE ? c.getInsertAt() : -1)
				.setOption(SET)
				.setTarget("") // no tile numbers: players don't read coordinates
				.setType(MenuAction.RUNELITE)
				.onClick(m -> target.setDestination(request));
		}
	}

	/** Index of the "Walk here" entry of the top-level scene (not a boat's deck), or -1. */
	private int walkIndex(MenuEntry[] entries)
	{
		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry m = entries[i];
			if (m.getType() == MenuAction.WALK)
			{
				WorldView wv = client.getWorldView(m.getWorldViewId());
				return wv != null && wv.isTopLevel() ? i : -1;
			}
		}
		return -1;
	}

	/** The right-clicked tile of the top-level scene as a real-world tile (instances mapped back), or -1. */
	private int selectedTile(WorldView top)
	{
		Tile t = top == null ? null : top.getSelectedSceneTile();
		if (t == null || t.getLocalLocation() == null)
		{
			return -1;
		}
		WorldPoint w = WorldPoint.fromLocalInstance(client, t.getLocalLocation());
		return w == null ? -1 : MenuChoice.tile(w.getX(), w.getY(), w.getPlane());
	}
}
