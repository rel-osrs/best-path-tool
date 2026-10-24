package com.bestpath.ui;

import com.bestpath.route.BoatMode;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.TransportSettings;
import java.awt.Color;
import java.awt.Component;
import java.util.function.Consumer;

/**
 * What the sidebar asks for. Called on the Swing thread; the plugin hops to
 * the client thread to snapshot the player.
 */
public interface PanelActions
{
	void findRoute(RouteRequest request);

	void clearRoute();

	/** "Use clue": route to the clue the player carries (only enabled when one was detected with a place). */
	void useClue();

	/**
	 * "Go to the last place sent" (Functions tab) or "Quest" (Route tab): route to the place another plugin, such as
	 * Quest Helper, sent last. Enabled only while one is known ({@link BestPathPanel#showQuestStep}).
	 */
	default void useQuestStep()
	{
	}

	/** The Functions tab's x on a recent destination (index in {@link com.bestpath.route.RecentDestinations#list}). */
	default void removeRecent(int index)
	{
	}

	/** The Functions tab's "Clear the list" (recent destinations). */
	default void clearRecent()
	{
	}

	/**
	 * "Save" in the Functions tab's saved locations: this name for this packed tile (already checked:
	 * a real tile, and room in the list or the tile already saved). The plugin saves it for the account and shows the
	 * list again ({@link BestPathPanel#showSaved}).
	 */
	default void saveLocation(String name, int tile)
	{
	}

	/** The x on a saved location (index in {@link com.bestpath.route.SavedLocations#list}). */
	default void removeSavedLocation(int index)
	{
	}

	/**
	 * The Slayer page's "Go to the nearest place": route to {@code nearest}; when it goes only to the player's preferred
	 * places, {@code ifNoRoute} (all places; null for none) is searched instead if no route reaches a preferred one.
	 */
	default void findSlayerNearest(RouteRequest nearest, RouteRequest ifNoRoute)
	{
		findRoute(nearest);
	}

	/**
	 * The star on a Slayer place's card, or "Unmark" in the Settings tab's "Preferred Slayer places": this place (its
	 * {@link com.bestpath.route.SlayerTask.Location#key}) of this task is preferred ({@code on}) or not. The plugin saves
	 * it for the account and shows the Slayer page and the list again.
	 */
	default void preferSlayerPlace(String task, String place, boolean on)
	{
	}

	/** The Functions tab's "Update times" (Slayer task): the travel times again, from where the player stands now. */
	default void updateSlayerTimes()
	{
	}

	/**
	 * A Wiki button: opens this OSRS Wiki page ("Slayer task/Kalphites") in the player's own browser. The plugin itself
	 * reads nothing from the Wiki.
	 */
	default void openWiki(String page)
	{
	}

	/** Session exclusion: skip this transport for this route, then reroute. */
	void skipForThisRoute(String transportId);

	/** Forget this route's skipped transports, then reroute. */
	void clearSkips();

	/**
	 * The Settings tab changed (a category, a rule, or the persistent blacklist: "Never use" on a step card adds
	 * to it, "Restore" in Settings removes from it). The plugin saves it per account profile and reroutes.
	 */
	void settingsChanged(TransportSettings settings);

	/**
	 * A display choice in the settings changed (the "Teleport and travel ticks" list folded or unfolded): saved per
	 * account like {@link #settingsChanged}, without searching the route again.
	 */
	default void settingsSaved(TransportSettings settings)
	{
		settingsChanged(settings);
	}

	/**
	 * A player answer: yes (true), no (false), or null to clear it (it is asked again). Given with
	 * Yes / No under a step card or changed in the Settings tab's "Your answers". The plugin saves it for the account,
	 * shows the answers again ({@link BestPathPanel#showAnswers}) and the route follows the new answer.
	 */
	default void answer(String answer, Boolean yes)
	{
	}

	/**
	 * A button on the route's boat card: "Park my boat" ({@link BoatMode#PARK}, this
	 * route only), "Ocean only path" ({@link BoatMode#OCEAN_ONLY}, until the player is off the boat), or back to the
	 * normal route. The plugin keeps it with the route and searches again.
	 */
	default void boatMode(BoatMode mode)
	{
	}

	/**
	 * A RuneLite config value of Best Path, as RuneLite stores it ({@link ConfigList#toConfig}), for the Settings tab's
	 * copy of the config panel; null = nothing stored (the item's default is shown).
	 */
	default String config(String key)
	{
		return null;
	}

	/**
	 * A config panel setting changed in the Settings tab: the plugin stores it in RuneLite's config (the config panel
	 * shows it, and the plugin hears it as from the config panel). {@code value} as RuneLite stores it.
	 */
	default void setConfig(String key, String value)
	{
	}

	/**
	 * Opens RuneLite's colour picker for a colour setting of the Settings tab ({@code owner}: the swatch clicked;
	 * {@code now}: the colour shown). {@code chosen} gets the colour picked when the picker closes (Swing thread).
	 */
	default void pickColour(Component owner, Color now, String title,
		Consumer<Color> chosen)
	{
	}

	/**
	 * Where the player stands (packed tile; -1: not known, e.g. logged out), for the Settings tab's "Transport ticks"
	 * ("Nearest to me", "12 tiles away") and saved locations.
	 */
	default int playerTile()
	{
		return -1;
	}
}
