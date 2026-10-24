package com.bestpath;

import com.bestpath.overlay.PathStyle;
import com.bestpath.overlay.RouteColours;
import com.bestpath.route.ArriveArea;
import com.bestpath.route.OtherPathPlugins;
import com.bestpath.route.RouteProgress;
import com.bestpath.route.TransportSettings;
import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * The RuneLite config panel. Layout: the sidebar switches at the top with no heading, then Open the sidebar, Routes
 * and world map, Graphics, the transport sections and Advanced. Every section starts folded.
 * <p>
 * The panel and the sidebar's Settings tab mirror each other: the sidebar shows every setting here, and the transport
 * sections here ({@link #TYPES}, {@link #AVOID}, {@link #BANK}, {@link #SAILING}) show the current account's Settings
 * tab choices ({@link TransportConfig}); a change in either place shows in the other. Lists (blacklist, tick changes,
 * answers, house, each boat) are only in the sidebar: this panel can't show them.
 */
@ConfigGroup(BestPathConfig.GROUP)
public interface BestPathConfig extends Config
{
	String GROUP = "bestpath";
	/** The key of the STASH put-back reminder switch (the plugin listens for it). */
	String REMIND_STASH_KEY = "remindStashPutBack";
	String DEVELOPER_KEY = "developerDetails";
	String REROUTE_KEY = "rerouteDistance";
	String ARRIVE_KEY = "arriveDistance";
	String CLEAR_ON_ARRIVAL_KEY = "clearOnArrival";
	String EXPLAIN_NO_ROUTE_KEY = "explainNoRoute";
	String SLAYER_ROUTING_KEY = "allowSlayerRouting";
	String PLUGIN_ROUTES_KEY = "allowPluginRoutes";
	String PREFER_SHORTEST_CLUE_KEY = OtherPathPlugins.PREFER_SHORTEST_CLUE_KEY;
	String PREFER_SLAYER_OVERLAY_KEY = OtherPathPlugins.PREFER_SLAYER_OVERLAY_KEY;
	String OPEN_FOR_CLUE_KEY = "openForClue";
	String OPEN_FOR_SLAYER_KEY = "openForSlayer";
	String OPEN_FOR_DESTINATION_KEY = "openForDestination";

	@ConfigItem(
		keyName = "showSidebar",
		name = "Show sidebar",
		description = "Show Best Path in the RuneLite sidebar",
		position = 0
	)
	default boolean showSidebar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sidebarOnlyWithRoute",
		name = "Route-only sidebar",
		description = "Show the sidebar button only while a route is set",
		position = 1
	)
	default boolean sidebarOnlyWithRoute()
	{
		return false;
	}

	/** For players who don't want the side panel: also turns on the right-click options and the next-step box. */
	@ConfigItem(
		keyName = "minimalMode",
		name = "Minimal mode",
		description = "No sidebar: start routes by right-click or clues; the game shows each step",
		position = 2
	)
	default boolean minimalMode()
	{
		return false;
	}

	/** When the sidebar opens by itself: a new clue route, a new Slayer task, a right-click destination. */
	@ConfigSection(
		name = "Open the sidebar",
		description = "When the Best Path sidebar opens by itself",
		position = 5,
		closedByDefault = true
	)
	String OPEN_SIDEBAR = "openSidebar";

	@ConfigItem(
		keyName = OPEN_FOR_CLUE_KEY,
		name = "New clue route",
		description = "Open the sidebar when a clue route starts by itself",
		section = OPEN_SIDEBAR,
		position = 6
	)
	default boolean openForClue()
	{
		return true;
	}

	@ConfigItem(
		keyName = OPEN_FOR_SLAYER_KEY,
		name = "New Slayer task",
		description = "Open the Slayer page when you get a new task",
		section = OPEN_SIDEBAR,
		position = 7
	)
	default boolean openForSlayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = OPEN_FOR_DESTINATION_KEY,
		name = "Right-click destination",
		description = "Open the sidebar when you pick 'Set destination'",
		section = OPEN_SIDEBAR,
		position = 8
	)
	default boolean openForDestination()
	{
		return true;
	}

	@ConfigSection(
		name = "Routes and world map",
		description = "When routes start, update and end; right-click options",
		position = 10,
		closedByDefault = true
	)
	String ROUTES = "routes";

	@ConfigItem(
		keyName = "allowClueRouting",
		name = "Automatic clue routes",
		description = "Route to each new clue step at once. Off: press 'Use clue'",
		section = ROUTES,
		position = 11
	)
	default boolean allowClueRouting()
	{
		return true;
	}

	/** Routes to the nearest place for a new Slayer task at once. Off by default: it replaces the route shown. */
	@ConfigItem(
		keyName = SLAYER_ROUTING_KEY,
		name = "Automatic Slayer routes",
		description = "Route to the nearest place for each new Slayer task. Off: use the Slayer page",
		section = ROUTES,
		position = 13
	)
	default boolean allowSlayerRouting()
	{
		return false;
	}

	/**
	 * Routes other plugins ask for through the public API (docs/API.md), and the places they send to Shortest Path
	 * (Quest Helper's steps...). The newest destination wins.
	 */
	@ConfigItem(
		keyName = PLUGIN_ROUTES_KEY,
		name = "Routes from other plugins",
		description = "Let Quest Helper and other plugins set your route (Functions: Integrated plugins)",
		section = ROUTES,
		position = 14
	)
	default boolean allowPluginRoutes()
	{
		return true;
	}

	/**
	 * On: Shortest Clue is not warned about, Best Path starts no clue route by itself, and while a clue is carried the
	 * Shortest Path messages are followed instead of left alone ({@code QuestHelperPath.heldBack}).
	 */
	@ConfigItem(
		keyName = PREFER_SHORTEST_CLUE_KEY,
		name = "Use Shortest Clue for clues",
		description = "Let Shortest Clue route your clues (without key, STASH or bank stops)",
		section = ROUTES,
		position = 15
	)
	default boolean preferShortestClue()
	{
		return false;
	}

	/**
	 * On: Configurable Slayer Task Overlay is not warned about and "Automatic Slayer routes" starts no route (its route
	 * to the task's place comes as another plugin's route).
	 */
	@ConfigItem(
		keyName = PREFER_SLAYER_OVERLAY_KEY,
		name = "Use Slayer Task Overlay for Slayer",
		description = "Let Configurable Slayer Task Overlay route your Slayer tasks",
		section = ROUTES,
		position = 16
	)
	default boolean preferSlayerOverlay()
	{
		return false;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = REROUTE_KEY,
		name = "Re-route when off by (tiles)",
		description = "Find a new route when you are this many tiles off it",
		section = ROUTES,
		position = 17
	)
	default int rerouteDistance()
	{
		return RouteProgress.DEFAULT_STRAY_DISTANCE;
	}

	/** How close to the route's end counts as arrived, in steps along the route. */
	@Range(min = 0, max = ArriveArea.MAX_STEPS)
	@ConfigItem(
		keyName = ARRIVE_KEY,
		name = "Arrived within (steps)",
		description = "Arrived within this many steps of the end. 0: on the last tile",
		section = ROUTES,
		position = 18
	)
	default int arriveDistance()
	{
		return RouteProgress.DEFAULT_ARRIVE_STEPS;
	}

	@ConfigItem(
		keyName = CLEAR_ON_ARRIVAL_KEY,
		name = "Clear route on arrival",
		description = "Clear the route when you arrive",
		section = ROUTES,
		position = 19
	)
	default boolean clearOnArrival()
	{
		return true;
	}

	/** On: "No route, finding out why..." is shown at once, then why. */
	@ConfigItem(
		keyName = EXPLAIN_NO_ROUTE_KEY,
		name = "No-route explanations",
		description = "When there's no route, show what's missing",
		section = ROUTES,
		position = 20
	)
	default boolean explainNoRoute()
	{
		return true;
	}

	@ConfigItem(
		keyName = REMIND_STASH_KEY,
		name = "Remind to put STASH items back",
		description = "Route back to a STASH unit to return the items you took out",
		section = ROUTES,
		position = 21
	)
	default boolean remindStashPutBack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRouteOnWorldMap",
		name = "Show route on world map",
		description = "Draw the route on the world map while it is open",
		section = ROUTES,
		position = 22
	)
	default boolean showRouteOnWorldMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "worldMapMenu",
		name = "Right-click world map",
		description = "Add 'Set destination' and 'Clear destination' to the world map's right-click",
		section = ROUTES,
		position = 23
	)
	default boolean worldMapMenu()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sceneMenu",
		name = "Right-click game tiles",
		description = "Add 'Set destination' to a tile's right-click",
		section = ROUTES,
		position = 24
	)
	default boolean sceneMenu()
	{
		return false;
	}

	/** The path, the text and the glow. */
	@ConfigSection(
		name = "Graphics",
		description = "How the route looks in the game",
		position = 30,
		closedByDefault = true
	)
	String GRAPHICS = "graphics";

	@ConfigItem(
		keyName = "routeColour",
		name = "Route colour",
		description = "The route's colour in the game, minimap and world map",
		section = GRAPHICS,
		position = 31
	)
	default Color routeColour()
	{
		return RouteColours.DEFAULT;
	}

	@ConfigItem(
		keyName = "pathStyle",
		name = "Path style",
		description = "Tile squares, a line, or a line with arrows at turns",
		section = GRAPHICS,
		position = 32
	)
	default PathStyle pathStyle()
	{
		return PathStyle.TILES;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "pathThickness",
		name = "Path thickness",
		description = "How thick the path is. 3: normal",
		section = GRAPHICS,
		position = 33
	)
	default int pathThickness()
	{
		return 3;
	}

	/** The path is always drawn under the player; on, also under objects standing on it. */
	@ConfigItem(
		keyName = "pathUnderObjects",
		name = "Path under objects",
		description = "Draw the path behind objects on it",
		section = GRAPHICS,
		position = 34
	)
	default boolean pathUnderObjects()
	{
		return false;
	}

	@ConfigItem(
		keyName = "stepText",
		name = "Text in the game",
		description = "Show what to do next above the thing to click",
		section = GRAPHICS,
		position = 35
	)
	default boolean stepText()
	{
		return true;
	}

	/** Default: the glow's blue. */
	@ConfigItem(
		keyName = "textColour",
		name = "Text colour",
		description = "Colour of the text above the thing to click",
		section = GRAPHICS,
		position = 36
	)
	default Color textColour()
	{
		return RouteColours.DEFAULT;
	}

	@ConfigItem(
		keyName = "playerTextColour",
		name = "Text above you colour",
		description = "Colour of the text shown above you",
		section = GRAPHICS,
		position = 37
	)
	default Color playerTextColour()
	{
		return Color.WHITE;
	}

	/** Default 16, RuneLite's usual text size. */
	@Range(min = 8, max = 40)
	@ConfigItem(
		keyName = "textSize",
		name = "Text size",
		description = "Text size in the game. 16: normal",
		section = GRAPHICS,
		position = 38
	)
	default int textSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "boldText",
		name = "Bold text",
		description = "Write the words in the game in bold",
		section = GRAPHICS,
		position = 39
	)
	default boolean boldText()
	{
		return false;
	}

	@ConfigItem(
		keyName = "glow",
		name = "Glow what to click",
		description = "Outline what to click next: object, person, item or spell",
		section = GRAPHICS,
		position = 40
	)
	default boolean glow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "glowColour",
		name = "Glow colour",
		description = "The colour of the glow",
		section = GRAPHICS,
		position = 41
	)
	default Color glowColour()
	{
		return RouteColours.DEFAULT;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "glowThickness",
		name = "Glow thickness",
		description = "How thick the outline is. 3: normal",
		section = GRAPHICS,
		position = 42
	)
	default int glowThickness()
	{
		return 3;
	}

	@Range(min = 1, max = 5)
	@ConfigItem(
		keyName = "uiThickness",
		name = "Item and UI glow thickness",
		description = "How thick item and spell outlines are. 1: normal",
		section = GRAPHICS,
		position = 43
	)
	default int uiThickness()
	{
		return 1;
	}

	/** Always shown in minimal mode. */
	@ConfigItem(
		keyName = "stepBox",
		name = "Next-step box",
		description = "A small box on screen with the next step",
		section = GRAPHICS,
		position = 44
	)
	default boolean stepBox()
	{
		return false;
	}

	/**
	 * The sidebar's "Transport types", for the account logged in ({@link TransportConfig}). The names are shorter than
	 * the sidebar's (this panel has no second line).
	 */
	@ConfigSection(
		name = "Transport types",
		description = "Untick a type to never use it (per account)",
		position = 50,
		closedByDefault = true
	)
	String TYPES = "transportTypes";

	@ConfigItem(
		keyName = "typeTeleportSpells",
		name = "Spells, home, minigames",
		description = "Spells, home and minigame teleports",
		section = TYPES,
		position = 51
	)
	default boolean typeTeleportSpells()
	{
		return true;
	}

	@ConfigItem(
		keyName = "typeTeleportItems",
		name = "Teleport items, jewellery",
		description = "Teleport items, jewellery",
		section = TYPES,
		position = 52
	)
	default boolean typeTeleportItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "typeHouse",
		name = "House (POH)",
		description = "House (POH) teleports and portals",
		section = TYPES,
		position = 53
	)
	default boolean typeHouse()
	{
		return true;
	}

	@ConfigItem(
		keyName = "typeStairsLadders",
		name = "Stairs and ladders",
		description = "Stairs and ladders",
		section = TYPES,
		position = 54
	)
	default boolean typeStairsLadders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "typeShortcuts",
		name = "Shortcuts",
		description = "Shortcuts",
		section = TYPES,
		position = 55
	)
	default boolean typeShortcuts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "typeDoors",
		name = "Gates that need something",
		description = "Gates and barriers that need something",
		section = TYPES,
		position = 56
	)
	default boolean typeDoors()
	{
		return true;
	}

	@ConfigItem(
		keyName = "typeBoats",
		name = "Boats, fairy rings, rides",
		description = "Boats, fairy rings, carts, other rides",
		section = TYPES,
		position = 57
	)
	default boolean typeBoats()
	{
		return true;
	}

	/** The sidebar's "Avoid" (the rules with data), for the account logged in. */
	@ConfigSection(
		name = "Avoid",
		description = "Use these only when nothing else works (per account)",
		position = 60,
		closedByDefault = true
	)
	String AVOID = "avoid";

	@ConfigItem(
		keyName = "avoidCost",
		name = "Costs something",
		description = "Transports that cost something",
		section = AVOID,
		position = 61
	)
	default boolean avoidCost()
	{
		return false;
	}

	@ConfigItem(
		keyName = "avoidRunes",
		name = "Uses runes",
		description = "Teleports that use runes",
		section = AVOID,
		position = 62
	)
	default boolean avoidRunes()
	{
		return false;
	}

	@ConfigItem(
		keyName = "avoidCooldown",
		name = "Long cooldown",
		description = "Long-cooldown teleports",
		section = AVOID,
		position = 63
	)
	default boolean avoidCooldown()
	{
		return false;
	}

	@ConfigItem(
		keyName = "avoidWilderness",
		name = "The Wilderness",
		description = "The Wilderness (unless there's no other way)",
		section = AVOID,
		position = 64
	)
	default boolean avoidWilderness()
	{
		return false;
	}

	/** Per account, as in the sidebar. */
	@ConfigItem(
		keyName = "coinCap",
		name = "Don't use travel that costs over",
		description = "Never use a fare over the coin limit, even when nothing else works",
		section = AVOID,
		position = 65
	)
	default boolean coinCap()
	{
		return false;
	}

	@Range(max = TransportSettings.MAX_COIN_CAP)
	@ConfigItem(
		keyName = "coinCapAmount",
		name = "Coin limit",
		description = "The most coins one transport may cost (each fare on its own)",
		section = AVOID,
		position = 66
	)
	default int coinCapAmount()
	{
		return TransportSettings.DEFAULT_COIN_CAP;
	}

	/** The sidebar's "Bank" (the bank stop's ticks), for the account logged in. */
	@ConfigSection(
		name = "Bank",
		description = "Bank stops on routes (per account)",
		position = 70,
		closedByDefault = true
	)
	String BANK = "bank";

	@ConfigItem(
		keyName = "bankDetour",
		name = "Routes may start at a bank",
		description = "Stop at a bank first when quicker or needed. Off: use only what you carry",
		section = BANK,
		position = 71
	)
	default boolean bankDetour()
	{
		return true;
	}

	@Range(min = 0, max = TransportSettings.MAX_BANK_TICKS)
	@ConfigItem(
		keyName = "bankTicks",
		name = "Bank stop (ticks)",
		description = "Ticks counted for a bank stop",
		section = BANK,
		position = 72
	)
	default int bankTicks()
	{
		return TransportSettings.DEFAULT_BANK_TICKS;
	}

	/** The sidebar's "Sailing" switches (not each boat: a list), for the account logged in. */
	@ConfigSection(
		name = "Sailing",
		description = "Boat options (per account)",
		position = 80,
		closedByDefault = true
	)
	String SAILING = "sailing";

	@ConfigItem(
		keyName = "shipwrights",
		name = "Shipwright brings my boat",
		description = "Let a shipwright bring your boat (costs coins)",
		section = SAILING,
		position = 81
	)
	default boolean shipwrights()
	{
		return true;
	}

	@ConfigItem(
		keyName = "keepBoat",
		name = "Never leave my boat at sea",
		description = "On your boat, sail to a shipwright's dock first",
		section = SAILING,
		position = 82
	)
	default boolean keepBoat()
	{
		return false;
	}

	@Range(min = 0, max = TransportSettings.MAX_BANK_TICKS)
	@ConfigItem(
		keyName = "boardTicks",
		name = "Board or leave a boat (ticks)",
		description = "Ticks counted for boarding or leaving a boat",
		section = SAILING,
		position = 83
	)
	default int boardTicks()
	{
		return TransportSettings.DEFAULT_BOARD_TICKS;
	}

	@ConfigSection(
		name = "Advanced",
		description = "For testing",
		position = 90,
		closedByDefault = true
	)
	String ADVANCED = "advanced";

	/** Extra details for testing in the game. Shows tile numbers, which the sidebar otherwise never does. */
	@ConfigItem(
		keyName = DEVELOPER_KEY,
		name = "Developer details",
		description = "For testing: transport details on cards, your position on the Route tab",
		section = ADVANCED,
		position = 91
	)
	default boolean developerDetails()
	{
		return false;
	}

}
