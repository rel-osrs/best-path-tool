package com.bestpath.ui;

import com.bestpath.BestPathConfig;
import com.bestpath.overlay.PathStyle;
import com.bestpath.overlay.RouteColours;
import com.bestpath.route.ArriveArea;
import com.bestpath.route.RouteProgress;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The RuneLite config panel's settings that the sidebar's Settings tab mirrors: every item of {@link BestPathConfig}
 * except the transport sections ({@link BestPathConfig#TYPES}, {@link BestPathConfig#AVOID},
 * {@link BestPathConfig#BANK}, {@link BestPathConfig#SAILING}), which the Settings tab already has as the account's
 * transport settings. Same names, descriptions, order and sections as the config panel; both read and write the same
 * RuneLite config values, so a change in one place shows in the other.
 * <p>
 * Written by hand (no reflection in the plugin), so every new config item needs a line here too. Values are kept as
 * RuneLite's ConfigManager stores them ({@link #toConfig}, {@link #fromConfig}). Pure: no Swing.
 */
final class ConfigList
{
	/** The "Routes from other plugins" setting's words, also on the Functions tab's Integrated plugins page. */
	static final String PLUGIN_ROUTES = "Routes from other plugins";
	static final String PLUGIN_ROUTES_TIP = "Let Quest Helper and other plugins set your route (Functions: Integrated plugins)";

	/** What kind of control an item gets: a tick box, a number, a colour or a choice. */
	enum Kind
	{
		BOOLEAN,
		INT,
		COLOUR,
		ENUM
	}

	/** A config panel section ({@code @ConfigSection}). */
	static final class Section
	{
		/** The section's key in BestPathConfig (the {@code String} constant the items name). */
		final String key;
		final String name;
		final String description;
		/** Folded unless the player opens it ({@code closedByDefault}). */
		final boolean closedByDefault;

		Section(String key, String name, String description, boolean closedByDefault)
		{
			this.key = key;
			this.name = name;
			this.description = description;
			this.closedByDefault = closedByDefault;
		}
	}

	/** One config item ({@code @ConfigItem}). */
	static final class Item
	{
		final String key;
		final String name;
		final String description;
		final Kind kind;
		/** Null: no section (the config panel's top). */
		final Section section;
		/** {@code @Range} for {@link Kind#INT}; 0 otherwise. */
		final int min;
		final int max;
		/** The choices of an {@link Kind#ENUM} item (shown by their toString); empty otherwise. */
		final List<Enum<?>> options;
		/** Boolean, Integer, Color or the enum constant: the value when nothing is stored. */
		final Object defaultValue;

		private Item(String key, String name, String description, Kind kind, Section section, int min, int max,
			List<Enum<?>> options, Object defaultValue)
		{
			this.key = key;
			this.name = name;
			this.description = description;
			this.kind = kind;
			this.section = section;
			this.min = min;
			this.max = max;
			this.options = options;
			this.defaultValue = defaultValue;
		}
	}

	/** When the sidebar opens by itself: a switch per case. */
	static final Section OPEN_SIDEBAR = new Section(BestPathConfig.OPEN_SIDEBAR, "Open the sidebar",
		"When the Best Path sidebar opens by itself", true);
	/** Route behaviour and the world map's right-click options, in one section. */
	static final Section ROUTES = new Section(BestPathConfig.ROUTES, "Routes and world map",
		"When routes start, update and end; right-click options",
		true); // every section starts folded
	static final Section GRAPHICS = new Section(BestPathConfig.GRAPHICS, "Graphics",
		"How the route looks in the game", true);
	static final Section ADVANCED = new Section(BestPathConfig.ADVANCED, "Advanced", "For testing", true);

	/** The sections, in config panel order (the transport sections between Graphics and Advanced are the sidebar's own). */
	static final List<Section> SECTIONS = List.of(OPEN_SIDEBAR, ROUTES, GRAPHICS, ADVANCED);

	/** Every mirrored item, in config panel order. */
	static final List<Item> ITEMS = List.of(
		bool("showSidebar", "Show sidebar", "Show Best Path in the RuneLite sidebar", null, true),
		bool("sidebarOnlyWithRoute", "Route-only sidebar", "Show the sidebar button only while a route is set", null, false),
		bool("minimalMode", "Minimal mode", "No sidebar: start routes by right-click or clues; the game shows each step", null, false),

		bool(BestPathConfig.OPEN_FOR_CLUE_KEY, "New clue route", "Open the sidebar when a clue route starts by itself", OPEN_SIDEBAR, true),
		bool(BestPathConfig.OPEN_FOR_SLAYER_KEY, "New Slayer task", "Open the Slayer page when you get a new task", OPEN_SIDEBAR, true),
		bool(BestPathConfig.OPEN_FOR_DESTINATION_KEY, "Right-click destination", "Open the sidebar when you pick 'Set destination'", OPEN_SIDEBAR,
			true),

		bool("allowClueRouting", "Automatic clue routes", "Route to each new clue step at once. Off: press 'Use clue'", ROUTES, true),
		bool(BestPathConfig.SLAYER_ROUTING_KEY, "Automatic Slayer routes", "Route to the nearest place for each new Slayer task. Off: use the Slayer page", ROUTES, false),
		bool(BestPathConfig.PLUGIN_ROUTES_KEY, PLUGIN_ROUTES, PLUGIN_ROUTES_TIP, ROUTES, true),
		bool(BestPathConfig.PREFER_SHORTEST_CLUE_KEY, "Use Shortest Clue for clues",
			"Let Shortest Clue route your clues (without key, STASH or bank stops)", ROUTES, false),
		bool(BestPathConfig.PREFER_SLAYER_OVERLAY_KEY, "Use Slayer Task Overlay for Slayer",
			"Let Configurable Slayer Task Overlay route your Slayer tasks", ROUTES, false),
		number(BestPathConfig.REROUTE_KEY, "Re-route when off by (tiles)", "Find a new route when you are this many tiles off it", ROUTES, 1, 50, RouteProgress.DEFAULT_STRAY_DISTANCE),
		number(BestPathConfig.ARRIVE_KEY, "Arrived within (steps)", "Arrived within this many steps of the end. 0: on the last tile", ROUTES, 0,
			ArriveArea.MAX_STEPS, RouteProgress.DEFAULT_ARRIVE_STEPS),
		bool(BestPathConfig.CLEAR_ON_ARRIVAL_KEY, "Clear route on arrival", "Clear the route when you arrive", ROUTES, true),
		bool(BestPathConfig.EXPLAIN_NO_ROUTE_KEY, "No-route explanations", "When there's no route, show what's missing", ROUTES, true),
		bool(BestPathConfig.REMIND_STASH_KEY, "Remind to put STASH items back", "Route back to a STASH unit to return the items you took out", ROUTES, true),

		bool("showRouteOnWorldMap", "Show route on world map", "Draw the route on the world map while it is open", ROUTES,
			true),
		bool("worldMapMenu", "Right-click world map", "Add 'Set destination' and 'Clear destination' to the world map's right-click", ROUTES, true),
		bool("sceneMenu", "Right-click game tiles", "Add 'Set destination' to a tile's right-click", ROUTES, false),

		colour("routeColour", "Route colour", "The route's colour in the game, minimap and world map", GRAPHICS,
			RouteColours.DEFAULT),
		choice("pathStyle", "Path style", "Tile squares, a line, or a line with arrows at turns", GRAPHICS, PathStyle.values(), PathStyle.TILES),
		number("pathThickness", "Path thickness", "How thick the path is. 3: normal", GRAPHICS, 1, 10, 3),
		bool("pathUnderObjects", "Path under objects", "Draw the path behind objects on it", GRAPHICS, false),
		bool("stepText", "Text in the game", "Show what to do next above the thing to click", GRAPHICS, true),
		colour("textColour", "Text colour", "Colour of the text above the thing to click", GRAPHICS,
			RouteColours.DEFAULT),
		colour("playerTextColour", "Text above you colour", "Colour of the text shown above you", GRAPHICS, Color.WHITE),
		number("textSize", "Text size", "Text size in the game. 16: normal", GRAPHICS, 8, 40, 16),
		bool("boldText", "Bold text", "Write the words in the game in bold", GRAPHICS, false),
		bool("glow", "Glow what to click", "Outline what to click next: object, person, item or spell", GRAPHICS, true),
		colour("glowColour", "Glow colour", "The colour of the glow", GRAPHICS, RouteColours.DEFAULT),
		number("glowThickness", "Glow thickness", "How thick the outline is. 3: normal", GRAPHICS, 1, 10, 3),
		number("uiThickness", "Item and UI glow thickness", "How thick item and spell outlines are. 1: normal", GRAPHICS, 1, 5, 1),
		bool("stepBox", "Next-step box", "A small box on screen with the next step", GRAPHICS, false),

		bool(BestPathConfig.DEVELOPER_KEY, "Developer details", "For testing: transport details on cards, your position on the Route tab", ADVANCED, false));

	private ConfigList()
	{
	}

	private static Item bool(String key, String name, String description, Section section, boolean dflt)
	{
		return new Item(key, name, description, Kind.BOOLEAN, section, 0, 0, List.of(), dflt);
	}

	private static Item number(String key, String name, String description, Section section, int min, int max, int dflt)
	{
		return new Item(key, name, description, Kind.INT, section, min, max, List.of(), dflt);
	}

	private static Item colour(String key, String name, String description, Section section, Color dflt)
	{
		return new Item(key, name, description, Kind.COLOUR, section, 0, 0, List.of(), dflt);
	}

	private static Item choice(String key, String name, String description, Section section, Enum<?>[] options, Enum<?> dflt)
	{
		return new Item(key, name, description, Kind.ENUM, section, 0, 0, List.of(options), dflt);
	}

	/** The items of a section ({@code null}: the ones at the top, with no section), in order. */
	static List<Item> in(Section section)
	{
		List<Item> out = new ArrayList<>();
		for (Item i : ITEMS)
		{
			if (i.section == section)
			{
				out.add(i);
			}
		}
		return out;
	}

	/**
	 * A value as RuneLite's ConfigManager stores it (objectToString): "true" / "false", a whole number, a colour's ARGB
	 * as a signed number ({@code String.valueOf(color.getRGB())}), an enum constant's {@code name()}.
	 */
	static String toConfig(Object value)
	{
		if (value instanceof Color)
		{
			return String.valueOf(((Color) value).getRGB());
		}
		if (value instanceof Enum)
		{
			return ((Enum<?>) value).name();
		}
		return String.valueOf(value);
	}

	/**
	 * A stored value read the way RuneLite's ConfigManager does (stringToObject): Boolean.parseBoolean,
	 * Integer.parseInt (then kept in the item's range, as the config panel's spinner does), ColorUtil.fromString
	 * ({@code new Color(Integer.decode(s), true)}), Enum.valueOf. Nothing stored, or a value RuneLite can't read: the
	 * item's default.
	 */
	static Object fromConfig(Item item, String stored)
	{
		if (stored == null)
		{
			return item.defaultValue;
		}
		try
		{
			switch (item.kind)
			{
				case BOOLEAN:
					return Boolean.parseBoolean(stored);
				case INT:
					return Math.max(item.min, Math.min(item.max, Integer.parseInt(stored)));
				case COLOUR:
					return new Color(Integer.decode(stored), true);
				case ENUM:
				default:
					for (Enum<?> e : item.options)
					{
						if (e.name().equals(stored))
						{
							return e;
						}
					}
					return item.defaultValue;
			}
		}
		catch (NumberFormatException e)
		{
			return item.defaultValue;
		}
	}
}
