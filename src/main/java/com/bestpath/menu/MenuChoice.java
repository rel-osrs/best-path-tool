package com.bestpath.menu;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteRequest;

/**
 * Which Best Path entries a right-click menu gets, and where. Pure (numbers
 * in, a decision out):
 * <ul>
 * <li>On the open world map's picture (the mouse is on it): "Set destination" for the tile under the cursor
 * ({@link #mapTile}), if the config's world map option is on; "Clear destination" too while a destination is set.
 * Both at the top of the menu (it is already open, so the left-click option is not changed).</li>
 * <li>Elsewhere, on the game scene: "Set destination" for the right-clicked tile, just below "Walk here", only if
 * the config's scene option is on and the menu has a "Walk here" entry. Never "Clear" there (fewer entries in a
 * menu used all the time).</li>
 * </ul>
 */
public final class MenuChoice
{
	/** Where the entries go. */
	public enum Place
	{
		NONE,
		/** At the top of the world map's menu. */
		WORLD_MAP,
		/** Just below "Walk here" (inserted at that entry's index, so it shows under it). */
		SCENE
	}

	public static final MenuChoice NONE = new MenuChoice(Place.NONE, -1, false, -1);

	private final Place place;
	private final int setTile;
	private final boolean clear;
	private final int insertAt;

	private MenuChoice(Place place, int setTile, boolean clear, int insertAt)
	{
		this.place = place;
		this.setTile = setTile;
		this.clear = clear;
		this.insertAt = insertAt;
	}

	/**
	 * @param mapOption      config: the world map entries are on
	 * @param sceneOption    config: the scene entry is on
	 * @param mouseOnMap     the world map is open and the cursor is on its picture
	 * @param mapTile        the packed tile under the cursor on the map, or -1 (see {@link #tile})
	 * @param walkIndex      index of the menu's "Walk here" entry (top-level scene), or -1
	 * @param sceneTile      the packed right-clicked scene tile, or -1
	 * @param hasDestination a destination is set now
	 */
	public static MenuChoice decide(boolean mapOption, boolean sceneOption, boolean mouseOnMap, int mapTile,
		int walkIndex, int sceneTile, boolean hasDestination)
	{
		if (mouseOnMap)
		{
			// the click is on the map, never on the scene behind it
			if (!mapOption || (mapTile == -1 && !hasDestination))
			{
				return NONE;
			}
			return new MenuChoice(Place.WORLD_MAP, mapTile, hasDestination, -1);
		}
		if (sceneOption && walkIndex >= 0 && sceneTile != -1)
		{
			return new MenuChoice(Place.SCENE, sceneTile, false, walkIndex);
		}
		return NONE;
	}

	/**
	 * The plane of a world map click's tile here: the map gives none, so the request is a map tile whose plane the
	 * route search picks ({@link RouteRequest#fromMap}).
	 */
	public static final int MAP_PLANE = 0;

	/**
	 * The packed destination tile for a world map click at this x / y: always {@link #MAP_PLANE}, whatever plane the
	 * player is on; -1 when outside what a destination can be. (A game tile keeps its own plane: {@link #tile}.)
	 */
	public static int mapTile(int x, int y)
	{
		return tile(x, y, MAP_PLANE);
	}

	/** The packed tile, or -1 when it is outside what a destination can be (see {@link RouteRequest}). */
	public static int tile(int x, int y, int plane)
	{
		if (x < 0 || y < 0 || x > RouteRequest.MAX_COORD || y > RouteRequest.MAX_COORD || plane < 0 || plane > 3)
		{
			return -1;
		}
		return Tiles.pack(x, y, plane);
	}

	public Place getPlace()
	{
		return place;
	}

	/** True if "Set destination" is added. */
	public boolean hasSet()
	{
		return setTile != -1;
	}

	/** The packed tile "Set destination" routes to, or -1. */
	public int getSetTile()
	{
		return setTile;
	}

	/** True if "Clear destination" is added. */
	public boolean hasClear()
	{
		return clear;
	}

	/** For {@link Place#SCENE}: the menu index to create the entry at (below "Walk here"); else -1 (the top). */
	public int getInsertAt()
	{
		return insertAt;
	}

	@Override
	public String toString()
	{
		return place + (hasSet() ? " set " + Tiles.x(setTile) + "," + Tiles.y(setTile) + "," + Tiles.plane(setTile) : "")
			+ (clear ? " clear" : "") + (insertAt >= 0 ? " at " + insertAt : "");
	}
}
