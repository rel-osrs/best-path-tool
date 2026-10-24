package com.bestpath.overlay;

import com.bestpath.core.data.WorldMaps;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;

/**
 * Developer details while the world map is open: which of the game's world maps the plugin sees open (by the name on
 * its map list button) and, under the mouse, the spot on that map and the game tile drawn there
 * ({@link WorldMaps#mapClick}, what "Set destination" would ask for). Client thread only.
 */
public final class WorldMapCheck
{
	private WorldMapCheck()
	{
	}

	/** The lines, or none while the world map is closed. */
	public static List<String> lines(Client client, WorldMaps maps)
	{
		List<String> out = new ArrayList<>();
		MapView view = WorldMapRouteOverlay.view(client);
		if (view == null)
		{
			return out;
		}
		int map = WorldMapRouteOverlay.shownMap(client, maps);
		out.add("World map: " + (map < 0 ? "not known (spots taken as game tiles)" : maps.mapName(map) + " (map " + map + ")"));
		Point mouse = client.getMouseCanvasPosition();
		if (mouse != null && view.contains(mouse.getX(), mouse.getY()))
		{
			int x = view.tileX(mouse.getX());
			int y = view.tileY(mouse.getY());
			int[] c = maps.mapClick(map, x, y);
			out.add("Map spot " + x + ", " + y + " -> " + (c == null ? "nothing (this area is drawn elsewhere on this map)"
				: "game " + c[0] + ", " + c[1] + (c[2] < 0 ? ", plane picked by the search" : ", plane " + c[2])));
		}
		return out;
	}
}
