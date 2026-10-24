package com.bestpath.ui;

import com.bestpath.clue.ClueCard;
import com.bestpath.clue.DetectedClue;
import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.data.TickGroups;
import com.bestpath.player.HouseView;
import com.bestpath.player.PlayerBoats;
import com.bestpath.route.BoatMode;
import com.bestpath.route.ChartingProgress;
import com.bestpath.route.Progress;
import com.bestpath.route.RecentDestinations;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.SavedLocations;
import com.bestpath.route.SlayerPreferred;
import com.bestpath.route.SlayerTask;
import com.bestpath.route.TransportFinder;
import com.bestpath.route.TransportSettings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * The Best Path sidebar: the Route, Functions and Settings tabs. All methods run on
 * the Swing thread (callers use SwingUtilities.invokeLater). It holds no game
 * state and never touches the client, so it also runs without a game client.
 */
public class BestPathPanel extends PluginPanel
{
	/** The tabs and their buttons (tests and the UI previews drive them too). */
	final RouteTab routeTab;
	final FunctionsTab functionsTab;
	final SettingsTab settingsTab;
	final MaterialTabGroup tabs;
	private final MaterialTab routeButton;
	private final MaterialTab functionsButton;
	final MaterialTab settingsButton;
	/** Above the tabs: other plugins to turn off ({@link #showOtherPluginWarnings}); hidden when none. */
	private final JPanel warnings = new JPanel();

	/** {@code icons}: where the step cards' pictures come from (RuneLite's managers in the plugin). */
	public BestPathPanel(PanelActions actions, IconProvider icons)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(Ui.BACKGROUND);

		// settings changes from either tab go through the Settings tab first, so it always shows the latest
		PanelActions wrapped = new SettingsFirstActions(actions);

		IconCache pictures = new IconCache(icons); // one cache for both tabs: each picture is asked for once
		settingsTab = new SettingsTab(wrapped, pictures);
		routeTab = new RouteTab(wrapped, settingsTab::getSettings, pictures);
		functionsTab = new FunctionsTab(wrapped, this::selectRouteTab, pictures);

		JPanel display = new JPanel();
		tabs = new MaterialTabGroup(display);
		tabs.setBorder(Ui.padding(Ui.PAD, 0, 2, 0));
		routeButton = new MaterialTab("Route", tabs, routeTab);
		functionsButton = new MaterialTab("Functions", tabs, functionsTab);
		settingsButton = new MaterialTab("Settings", tabs, settingsTab);
		tabs.addTab(routeButton);
		tabs.addTab(functionsButton);
		tabs.addTab(settingsButton);
		tabs.select(routeButton);

		warnings.setLayout(new BoxLayout(warnings, BoxLayout.Y_AXIS));
		warnings.setBackground(Ui.CARD);
		warnings.setBorder(Ui.padding(Ui.PAD / 2, Ui.PAD, Ui.PAD / 2, Ui.PAD));
		warnings.setVisible(false);
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(Ui.BACKGROUND);
		warnings.setAlignmentX(LEFT_ALIGNMENT);
		tabs.setAlignmentX(LEFT_ALIGNMENT);
		top.add(warnings);
		top.add(tabs);
		add(top, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}

	/**
	 * Other plugins whose messages to Shortest Path replace Best Path's routes, one line each, above the tabs, so the
	 * player can turn them off. An empty list hides the box.
	 */
	public void showOtherPluginWarnings(List<String> lines)
	{
		warnings.removeAll();
		if (!lines.isEmpty())
		{
			warnings.add(Ui.wrapped("Other plugins in the way", Ui.CONTENT_WIDTH, Ui.bold(), Ui.BAD));
			for (String line : lines)
			{
				warnings.add(Box.createVerticalStrut(4));
				warnings.add(Ui.wrapped(line, Ui.CONTENT_WIDTH, Ui.small(), Ui.TEXT));
			}
		}
		warnings.setVisible(!lines.isEmpty());
		revalidate();
		repaint();
	}

	/** Shows a published route result; {@code skipped} = this route's session exclusions. */
	public void showResult(RouteResult result, Set<String> skipped)
	{
		routeTab.show(result, skipped);
	}

	/** The carried clue changed: updates the clue line and the "Use clue" button. */
	public void showClue(DetectedClue clue)
	{
		routeTab.showClue(clue);
	}

	/**
	 * The carried clue's own card (made on the client thread; null = no clue): shown as the last card of that clue's
	 * route, and kept after arriving there.
	 */
	public void showClueCard(ClueCard card)
	{
		routeTab.showClueCard(card);
	}

	/** Highlights the player's current step (route progress). */
	public void showProgress(Progress progress)
	{
		routeTab.showProgress(progress);
	}

	/** Shows saved settings (account switch); does not report them back as a change. */
	public void showSettings(TransportSettings settings)
	{
		settingsTab.show(settings);
	}

	/**
	 * The map data's teleport categories and networks (once it has loaded): the Settings tab's "Teleport and travel
	 * ticks" rows and the totals in "Your tick changes".
	 */
	public void showTickGroups(TickGroups groups)
	{
		settingsTab.showTickGroups(groups);
	}

	/** The plugin data's transports for the Settings tab's "Transport ticks" (once the map data has loaded). */
	public void showTransports(TransportFinder transports)
	{
		settingsTab.showTransports(transports);
	}

	/**
	 * The player's answers (the data's answer words -> yes / no) for the Settings tab's "Your answers". Called on
	 * account switch and after each answer.
	 */
	public void showAnswers(Map<String, Boolean> answers)
	{
		settingsTab.showAnswers(answers);
	}

	/**
	 * Every question the map data asks (its answer words, once it has loaded). "Your answers" lists the ones not
	 * answered yet with Yes / No, so they can be answered in advance.
	 */
	public void showQuestions(List<String> questions)
	{
		settingsTab.showQuestions(questions);
	}

	/** The player's boats (read from the game) for the Settings tab's Sailing rows. */
	public void showBoats(PlayerBoats boats)
	{
		settingsTab.showBoats(boats);
	}

	/** The player's house (place from the game, things from the house scan) for the Settings tab's "House (POH)". */
	public void showHouse(HouseView house)
	{
		settingsTab.showHouse(house);
	}

	/**
	 * A Best Path config value changed, in the config panel or in the Settings tab (the two mirror each other). The
	 * Settings tab re-reads every config setting it shows, without reporting them back.
	 */
	public void configChanged()
	{
		settingsTab.configChanged();
	}

	/** Developer details on or off (RuneLite config "Developer details"). */
	public void setDeveloper(boolean on)
	{
		routeTab.setDeveloper(on);
	}

	/** Where the player stands, for the developer details box (client thread makes the lines). */
	public void showWhere(List<String> lines)
	{
		routeTab.showWhere(lines);
	}

	public void selectRouteTab()
	{
		tabs.select(routeButton);
	}

	public void selectFunctionsTab()
	{
		tabs.select(functionsButton);
	}

	/**
	 * Quest Helper's current step (null: none) and whether routes follow each new step (config "Routes from other plugins"):
	 * the Functions tab's Quest Helper part and the Route tab's "Quest" button.
	 */
	public void showQuestStep(RouteRequest step, boolean following)
	{
		functionsTab.showQuestStep(step, following);
		routeTab.showQuestStep(step != null);
	}

	/** The place kinds (banks among them) for the Functions tab's "Nearest", once the map data has loaded. */
	public void showPlaces(PlaceKinds kinds)
	{
		functionsTab.showPlaces(kinds, true);
	}

	/** The account's recent destinations (Functions tab). */
	public void showRecent(RecentDestinations recent)
	{
		functionsTab.showRecent(recent);
	}

	/** The account's saved locations (Functions tab). */
	public void showSaved(SavedLocations saved)
	{
		functionsTab.showSaved(saved);
	}

	/** Opens the Functions tab page of the function with this title, as on its card (screenshots and tests). */
	public void openFunctionPage(String title)
	{
		functionsTab.openPage(title);
	}

	/** The player's Slayer task and its places (Functions tab). */
	public void showSlayer(SlayerTask task)
	{
		functionsTab.showSlayer(task);
	}

	/** The account's preferred Slayer places, listed in the Settings tab. */
	public void showSlayerPreferred(SlayerPreferred preferred)
	{
		settingsTab.showSlayerPreferred(preferred);
	}

	/** The player's sea charting (Functions tab). */
	public void showCharting(ChartingProgress progress)
	{
		functionsTab.showCharting(progress);
	}

	/**
	 * The actions the tabs are given: each one is handed on to the plugin's, but a settings change from either tab
	 * is shown in the Settings tab first, so it always shows the latest settings. Every method of
	 * {@link PanelActions} must be handed on here: one left out would run the interface's default instead of the plugin's.
	 */
	private final class SettingsFirstActions implements PanelActions
	{
		private final PanelActions actions;

		SettingsFirstActions(PanelActions actions)
		{
			this.actions = actions;
		}

		@Override
		public void findRoute(RouteRequest request)
		{
			actions.findRoute(request);
		}

		@Override
		public void clearRoute()
		{
			actions.clearRoute();
		}

		@Override
		public void useClue()
		{
			actions.useClue();
		}

		@Override
		public void useQuestStep()
		{
			actions.useQuestStep();
		}

		@Override
		public void removeRecent(int index)
		{
			actions.removeRecent(index);
		}

		@Override
		public void clearRecent()
		{
			actions.clearRecent();
		}

		@Override
		public void saveLocation(String name, int tile)
		{
			actions.saveLocation(name, tile);
		}

		@Override
		public void findSlayerNearest(RouteRequest nearest, RouteRequest ifNoRoute)
		{
			actions.findSlayerNearest(nearest, ifNoRoute);
		}

		@Override
		public void preferSlayerPlace(String task, String place, boolean on)
		{
			actions.preferSlayerPlace(task, place, on);
		}

		@Override
		public void updateSlayerTimes()
		{
			actions.updateSlayerTimes();
		}

		@Override
		public void openWiki(String page)
		{
			actions.openWiki(page);
		}

		@Override
		public void removeSavedLocation(int index)
		{
			actions.removeSavedLocation(index);
		}

		@Override
		public void skipForThisRoute(String transportId)
		{
			actions.skipForThisRoute(transportId);
		}

		@Override
		public void clearSkips()
		{
			actions.clearSkips();
		}

		@Override
		public void settingsChanged(TransportSettings settings)
		{
			if (settingsTab != null && !settings.equals(settingsTab.getSettings()))
			{
				settingsTab.show(settings);
			}
			actions.settingsChanged(settings);
		}

		@Override
		public void settingsSaved(TransportSettings settings)
		{
			actions.settingsSaved(settings);
		}

		@Override
		public void answer(String answer, Boolean yes)
		{
			actions.answer(answer, yes);
		}

		@Override
		public void boatMode(BoatMode mode)
		{
			actions.boatMode(mode);
		}

		@Override
		public String config(String key)
		{
			return actions.config(key);
		}

		@Override
		public void setConfig(String key, String value)
		{
			actions.setConfig(key, value);
		}

		@Override
		public void pickColour(Component owner, Color now, String title,
			Consumer<Color> chosen)
		{
			actions.pickColour(owner, now, title, chosen);
		}

		@Override
		public int playerTile()
		{
			return actions.playerTile();
		}
	}
}
