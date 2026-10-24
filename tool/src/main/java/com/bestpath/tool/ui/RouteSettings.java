package com.bestpath.tool.ui;

import com.bestpath.core.path.Sailing;
import com.bestpath.core.sea.Boat;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.route.BoatSettings;
import com.bestpath.tool.route.RouteController;
import java.util.ArrayList;
import java.util.List;

/** The route check's settings (sailing, boats, door cost ...), remembered between starts in the tool's settings. */
final class RouteSettings
{
	/** Boat slots 1 to this are saved. */
	private static final int BOAT_SLOTS = 5;

	private RouteSettings()
	{
	}

	/** Gives {@code route} the saved settings, and saves every later change. */
	static void attach(ToolSettings settings, RouteController route)
	{
		load(settings, route);
		route.addListener(() -> save(settings, route));
	}

	private static void load(ToolSettings settings, RouteController route)
	{
		route.setMainMapTopY((int) settings.getDouble("route.mainMapTopY", RouteController.DEFAULT_MAIN_MAP_TOP_Y));
		route.setNearRange((int) settings.getDouble("route.nearRange", RouteController.DEFAULT_NEAR_RANGE));
		route.setDoorCost((int) settings.getDouble("route.doorCost", RouteController.DEFAULT_DOOR_COST));
		route.setSailingOn(settings.get("sail.on", "false").equals("true"));
		route.setSailingLevel((int) settings.getDouble("sail.level", 99));
		route.setShipwrights(settings.get("sail.shipwrights", "true").equals("true"));
		route.setTurnCost((int) settings.getDouble("sail.turnCost", Sailing.DEFAULT_TURN_COST));
		List<Boat> savedBoats = new ArrayList<>();
		for (int slot = 1; slot <= BOAT_SLOTS; slot++)
		{
			Boat b = BoatSettings.decode(slot, settings.get("sail.boat" + slot, ""));
			if (b != null)
			{
				savedBoats.add(b);
			}
		}
		route.setBottles((int) settings.getDouble("sail.bottles", 0));
		route.setBoats(savedBoats);
	}

	private static void save(ToolSettings settings, RouteController route)
	{
		settings.set("sail.on", Boolean.toString(route.isSailingOn()));
		settings.set("sail.bottles", Integer.toString(route.getBottles()));
		settings.set("sail.level", Integer.toString(route.getSailingLevel()));
		settings.set("sail.shipwrights", Boolean.toString(route.isShipwrights()));
		settings.set("sail.turnCost", Integer.toString(route.getTurnCost()));
		for (int slot = 1; slot <= BOAT_SLOTS; slot++)
		{
			String line = "";
			for (Boat b : route.getBoats())
			{
				if (b.getSlot() == slot)
				{
					line = BoatSettings.encode(b);
				}
			}
			settings.set("sail.boat" + slot, line);
		}
		settings.set("route.mainMapTopY", Integer.toString(route.getMainMapTopY()));
		settings.set("route.nearRange", Integer.toString(route.getNearRange()));
		settings.set("route.doorCost", Integer.toString(route.getDoorCost()));
	}
}
