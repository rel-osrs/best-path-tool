package com.bestpath;

import com.bestpath.api.BestPathApi;
import com.bestpath.clue.ClueCard;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TickGroups;
import com.bestpath.core.data.WorldMaps;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.Requirement;
import com.bestpath.menu.DestinationMenu;
import com.bestpath.overlay.RouteColours;
import com.bestpath.overlay.RouteMinimapOverlay;
import com.bestpath.overlay.StepBoxOverlay;
import com.bestpath.overlay.StepHintOverlay;
import com.bestpath.overlay.StepItemOverlay;
import com.bestpath.overlay.StepSpellOverlay;
import com.bestpath.overlay.StepTargets;
import com.bestpath.overlay.WorldMapCheck;
import com.bestpath.overlay.RoutePathOverlay;
import com.bestpath.overlay.WorldMapRouteOverlay;
import com.bestpath.data.MapDataLoader;
import com.bestpath.player.AccountSettings;
import com.bestpath.player.HouseView;
import com.bestpath.player.LoginEdges;
import com.bestpath.player.PlayerBoats;
import com.bestpath.player.RequirementInputs;
import com.bestpath.route.AnswerQuestions;
import com.bestpath.route.BoatMode;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.LoadingEngine;
import com.bestpath.route.OtherPathPlugins;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.Progress;
import com.bestpath.route.QuestHelperPath;
import com.bestpath.route.RouteEngine;
import com.bestpath.route.RouteFollower;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteService;
import com.bestpath.route.TransportFinder;
import com.bestpath.route.TransportSettings;
import com.bestpath.ui.BestPathPanel;
import com.bestpath.ui.RuneLiteIcons;
import com.bestpath.ui.NavIcon;
import com.bestpath.ui.PanelActions;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Component;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.LinkBrowser;

/**
 * Best Path: routes to a destination (sidebar, path overlays).
 *
 * <p>Threads: the sidebar asks on the Swing thread;
 * the player snapshot is taken on the client thread; {@link RouteService}
 * searches on its own worker and publishes an immutable result; the sidebar
 * is updated with SwingUtilities.invokeLater; overlays only read the
 * published result.
 *
 * <p>The map data (the bundled data files) loads on the route worker at start
 * ({@link MapDataLoader}); without it every route answers "No map data yet".
 *
 * <p>This class starts and stops everything and hands RuneLite's events to the
 * parts: player state ({@link PlayerTracker}), clue scrolls ({@link ClueRoutes}),
 * the Slayer task ({@link SlayerRoutes}), Quest Helper's steps
 * ({@link QuestHelperSteps}), recent and saved destinations
 * ({@link SavedDestinations}), the overlays and the sidebar ({@link BestPathPanel}).
 * Each start makes new parts, so nothing of an earlier start is left over.
 */
@Slf4j
@PluginDescriptor(
	name = "Best Path",
	description = "Finds the fastest route to a destination, with teleports and shortcuts",
	tags = {"route", "path", "travel", "teleport", "shortcut", "clue"}
)
// the bank filter button shows the route's items through RuneLite's Bank Tags plugin
@PluginDependency(BankTagsPlugin.class)
public class BestPathPlugin extends Plugin
{
	/** Per-account (RS profile) config key for the sidebar Settings tab. */
	static final String SETTINGS_KEY = "transportSettings";
	/** Per-account keys (not config panel settings: changing them doesn't touch the sidebar button). */
	private static final Set<String> PROFILE_KEYS = Set.of(SETTINGS_KEY, HouseScanner.HOUSE_KEY, PlayerTracker.BANK_KEY,
		PlayerTracker.OWNED_KEY, PlayerTracker.ANSWERS_KEY, StashTracker.FILLED_KEY, StashTracker.TAKEN_KEY, HouseScanner.HOUSE_TIME_KEY,
		HouseScanner.NEXUS_KEYS_KEY, SavedDestinations.RECENT_KEY, SavedDestinations.SAVED_KEY, SlayerRoutes.STORED_KEY,
		SlayerRoutes.PREFERRED_KEY, UnlockWatch.LOG_KEY, UnlockWatch.TREES_KEY, UnlockWatch.CANOE_AXE_KEY);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	/** The public API's answers to other plugins (docs/API.md). */
	@Inject
	private EventBus eventBus;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	/** For the bank filter button: RuneLite's Bank Tags, its public service and tag manager. */
	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private TagManager tagManager;

	@Inject
	private PluginManager pluginManager;

	/** The bank's "Show route items" button (client thread). */
	private BankFilterButton bankButton;

	/** The Settings tab's colour settings open RuneLite's own colour picker, like the config panel. */
	@Inject
	private ColorPickerManager colorPickerManager;

	@Inject
	private BestPathConfig config;

	/** The step cards' pictures (items, skills, sprites): RuneLite's public managers. */
	@Inject
	private ItemManager itemManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private SpriteManager spriteManager;

	/** Outlines what to click next. */
	@Inject
	private ModelOutlineRenderer outlineRenderer;

	/** Client thread: the other plugins to turn off, as last shown ({@link OtherPathPlugins}). */
	private List<String> otherPluginWarnings = List.of();

	/** Read on the client, Swing and worker threads. */
	private volatile RouteService routes;
	/** Player state from game events (client thread). */
	private volatile PlayerTracker tracker;
	/** What the plugin's parts below may use of it. */
	private final PluginContext context = new Context();
	/** Clue scrolls: detection, the step's card, clue routes and STASH units (client thread). */
	private volatile ClueRoutes clueRoutes;
	/** The Slayer task, its places and routes (client thread). */
	private volatile SlayerRoutes slayerRoutes;
	/** Quest Helper's step (client thread). */
	private volatile QuestHelperSteps questSteps;
	/** The recent destinations and saved locations (client thread). */
	private volatile SavedDestinations destinations;
	/** The map data's tables (item groups) once it loaded (client thread only); null before. */
	private Tables mapTables;
	/** Sea charting and routes through a charting set (client thread). */
	private volatile ChartingRoutes charting;
	/** Route progress, rerouting and arrival (client thread, each game tick). */
	private volatile RouteFollower follower;
	/** The public API: other plugins' messages and Best Path's answers (docs/API.md). */
	private volatile ApiBridge api;
	private BestPathPanel panel;
	private NavigationButton navButton;
	/** Swing thread only. */
	private boolean navShown;
	private RoutePathOverlay pathOverlay;
	private RouteMinimapOverlay minimapOverlay;
	/** The next step found in the game (client thread), its text and glow. */
	private volatile StepTargets stepTargets;
	private StepHintOverlay hintOverlay;
	private StepItemOverlay itemOverlay;
	private StepSpellOverlay spellOverlay;
	private StepBoxOverlay boxOverlay;
	/** The route on the world map; its lines are built where results are published (the route worker). */
	private volatile WorldMapRouteOverlay worldMapOverlay;
	/** Where the game's world maps draw each area (from the map data; NONE until it loads). */
	private volatile WorldMaps worldMaps = WorldMaps.NONE;
	/** The Functions tab's "Nearest" kinds (made on the route worker when the map data loads); null until then. */
	private volatile PlaceKinds placeKinds;
	/** Right-click "Set destination" / "Clear destination" (client thread). */
	private volatile DestinationMenu destinationMenu;
	/** The Settings tab's choices in use (read by searches on any thread). */
	private volatile TransportSettings settings = TransportSettings.defaults();
	/** Per-account writes (client thread only). */
	private ProfileStore store;
	/** The Settings tab's choices per account, saving and the logged-out fallback (client thread only). */
	private AccountSettings accountSettings;
	/** Login / world hop vs scene load (client thread only). */
	private final LoginEdges loginEdges = new LoginEdges();
	/** The map data's teleport categories and networks for the Settings tab (made on the route worker once loaded). */
	private volatile TickGroups tickGroups;
	/** Every transport for the Settings tab's "Transport ticks", made with the tick groups. */
	private volatile TransportFinder transportFinder;
	/** Where the player stood on the last tick (packed tile, -1: not known), for "Transport ticks". */
	private volatile int playerTile = -1;
	/** Every question the map data asks (its answer words), for the Settings tab's "Your answers"; null until loaded. */
	private volatile List<String> answerQuestions;

	@Override
	protected void startUp()
	{
		ProfileStore ps = new ProfileStore(client, configManager);
		store = ps;
		accountSettings = new AccountSettings(new AccountSettings.Store()
		{
			@Override
			public String profileKey()
			{
				return ps.profileKey();
			}

			@Override
			public String read()
			{
				return ps.read(SETTINGS_KEY);
			}

			@Override
			public boolean write(String text)
			{
				return ps.write(ps.profileKey(), SETTINGS_KEY, text);
			}
		});
		// Everything the worker's closures touch belongs to THIS start: after a quick disable / enable, an old
		// search or load still finishing must not write into the new session.
		AtomicReference<RouteService> self = new AtomicReference<>();
		PlayerTracker t = new PlayerTracker(client, ps); // before the load starts, which reads it
		tracker = t;
		ClueRoutes cr = new ClueRoutes(client, config, ps, context); // before the map data (with the clue steps) can arrive
		clueRoutes = cr;
		SlayerRoutes sr = new SlayerRoutes(client, config, ps, context);
		slayerRoutes = sr;
		QuestHelperSteps qs = new QuestHelperSteps(config, context);
		questSteps = qs;
		destinations = new SavedDestinations(ps, context);
		ChartingRoutes cwatch = new ChartingRoutes(client, t, context);
		charting = cwatch;
		// the map data loads on the route worker (about a second), never on the client thread
		LoadingEngine engine = new LoadingEngine(() ->
		{
			RouteEngine loaded = MapDataLoader.engine();
			if (loaded instanceof CoreRouteEngine && routes == self.get())
			{
				mapDataLoaded((CoreRouteEngine) loaded, self.get(), t, cr, sr, cwatch);
			}
			return loaded;
		});
		RouteService service = new RouteService(engine, r -> onRouteResult(self.get(), r));
		service.setExplainNoRoute(config.explainNoRoute());
		self.set(service);
		RouteFollower f = new RouteFollower(service, () -> settings, t::getInputs, this::onProgress);
		f.setLimits(config.rerouteDistance(), config.arriveDistance());
		f.setClearOnArrival(config.clearOnArrival());
		follower = f;
		// the public API (docs/API.md): other plugins' routes go the way of every route here (requestRoute)
		ApiBridge bridge = new ApiBridge(clientThread, eventBus, () -> routes, t::snapshot,
			() -> settings, config::allowPluginRoutes, this::requestRoute);
		api = bridge;
		f.setArrivalListener(bridge::arrived);
		WorldMapRouteOverlay mapOverlay = new WorldMapRouteOverlay(client, f::getProgress, config::showRouteOnWorldMap,
			service::runOnWorker);
		worldMapOverlay = mapOverlay;
		// a walk redrawn from the player's tile (route worker): the world map rebuilds its lines there, never in render()
		f.setRedrawListener((r, d) ->
		{
			if (routes == self.get())
			{
				mapOverlay.lineChanged(r, d);
			}
		});
		destinationMenu = new DestinationMenu(client, config, new MenuTarget(), () -> worldMaps);
		routes = service;
		service.runOnWorker(engine::load);
		panel = new BestPathPanel(new Actions(), new RuneLiteIcons(itemManager, skillIconManager, spriteManager, clientThread));
		SwingUtilities.invokeLater(() ->
		{
			TickGroups g = tickGroups; // the map data may have loaded before the panel was made
			if (panel != null && g != null && routes == service)
			{
				panel.showTickGroups(g);
			}
			TransportFinder tf = transportFinder;
			if (panel != null && tf != null && routes == service)
			{
				panel.showTransports(tf);
			}
			List<String> q = answerQuestions;
			if (panel != null && q != null && routes == service)
			{
				panel.showQuestions(q);
			}
			PlaceKinds pk = placeKinds;
			if (panel != null && pk != null && routes == service)
			{
				panel.showPlaces(pk);
			}
			if (panel != null)
			{
				panel.setDeveloper(config.developerDetails());
			}
		});
		navButton = NavigationButton.builder()
			.tooltip("Best Path")
			.icon(NavIcon.create())
			.priority(7)
			.panel(panel)
			.build();
		addOverlays(service, f, t, mapOverlay);
		onClient(() ->
		{
			if (tracker == t)
			{
				loadAccount(t);
				qs.show();
			}
		});
		SwingUtilities.invokeLater(this::updateNavButton);
		onClient(this::checkOtherPlugins);
		log.debug("Best Path started");
	}

	/** Makes the overlays (the path, minimap and world map, the next step's text, glow and box) and adds them. */
	private void addOverlays(RouteService service, RouteFollower f, PlayerTracker t, WorldMapRouteOverlay mapOverlay)
	{
		// one colour the player picks, and the path style
		Supplier<RouteColours> colours =
			() -> RouteColours.of(config.routeColour());
		// the step finder first: the path overlay draws its walk inside the house
		StepTargets targets = new StepTargets(client, t.house().table()::thing);
		stepTargets = targets;
		bankButton = new BankFilterButton(client, bankTagsService, tagManager, service::getCurrent, itemManager::canonicalize,
			this::bankTagsOn);
		pathOverlay = new RoutePathOverlay(client, service::getCurrent, f::getProgress, f::getDrawn, colours, config::pathStyle,
			config::pathUnderObjects, targets::getHousePath, config::pathThickness);
		minimapOverlay = new RouteMinimapOverlay(client, service::getCurrent, f::getProgress, f::getDrawn, colours);
		mapOverlay.setColours(colours);
		overlayManager.add(pathOverlay);
		overlayManager.add(minimapOverlay);
		overlayManager.add(mapOverlay);
		// text above what to click next, and its glow
		hintOverlay = new StepHintOverlay(client, targets, outlineRenderer, config::stepText, config::glow,
			config::glowColour, config::textColour, config::playerTextColour, () -> config.stepBox() || config.minimalMode(),
			config::glowThickness, config::textSize, config::boldText);
		itemOverlay = new StepItemOverlay(itemManager, targets, config::glow, config::glowColour,
			config::uiThickness);
		spellOverlay = new StepSpellOverlay(targets, config::glow, config::glowColour, config::uiThickness);
		// the next-step box: its own switch, and always shown in minimal mode
		boxOverlay = new StepBoxOverlay(service::getCurrent, targets,
			() -> config.stepBox() || config.minimalMode(), config::routeColour);
		overlayManager.add(hintOverlay);
		overlayManager.add(itemOverlay);
		overlayManager.add(spellOverlay);
		overlayManager.add(boxOverlay);
	}

	/**
	 * Route worker: the map data of this start ({@code service}'s) loaded. Its parts go to the player state, the
	 * sidebar, and the clue, Slayer and charting parts.
	 */
	private void mapDataLoaded(CoreRouteEngine loaded, RouteService service, PlayerTracker t, ClueRoutes cr, SlayerRoutes sr,
		ChartingRoutes cwatch)
	{
		PluginData pd = loaded.getRouter().getData();
		ClueData clueData = pd.getClues();
		Tables tables = pd.getTables();
		// the transports' needs, and the quests the clue steps name (their state on the clue card)
		List<Requirement> needs = new ArrayList<>(loaded.getRouter().requirements());
		needs.addAll(ClueCard.questNeeds(clueData, tables.itemGroups()));
		// and the home / minigame teleports' last-use varps (used only while off cooldown)
		t.setInputs(RequirementInputs.collect(needs).withCooldowns(RequirementInputs.cooldownsOf(pd.getTransports())));
		// where each world map draws each area, for map clicks and the route drawn on the map
		worldMaps = pd.getWorldMaps();
		WorldMapRouteOverlay mo = worldMapOverlay;
		if (mo != null)
		{
			mo.setWorldMaps(pd.getWorldMaps());
		}
		// the Settings tab's "Teleport and travel ticks" (made here, on the route worker)
		TickGroups groups = TickGroups.of(pd.getTransports(), pd.getTables());
		tickGroups = groups;
		TransportFinder finder = TransportFinder.of(pd.getTransports(), pd.getTables(),
			groups, pd.getWilderness());
		transportFinder = finder;
		// and every question the data asks: "Your answers" lists the ones not answered yet
		List<String> questions = AnswerQuestions.answersIn(needs);
		answerQuestions = questions;
		// the Functions tab's "Nearest": the place kinds, the banks from banks.tsv
		PlaceKinds places = pd.getPlaces().withBanks(pd.getBanks());
		placeKinds = places;
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null && routes == service)
			{
				panel.showPlaces(places);
				panel.showTickGroups(groups);
				panel.showTransports(finder);
				panel.showQuestions(questions);
			}
		});
		onClient(() ->
		{
			if (routes != service)
			{
				return;
			}
			mapTables = tables;
			cr.setData(tables, clueData); // the clue card's item groups, the STASH units and where they are
			sr.setData(pd.getSlayer(), pd.getWilderness()); // the Slayer places: the task is read on the next tick
			cwatch.setTasks(pd.getCharting()); // the sea charting tasks: the Functions tab on the next tick
			cwatch.setSea(pd.getSea() == null ? null : pd.getSea().getGrid()); // for planning a set's order
			t.setBoatTable(pd.getBoats()); // the boats can be read now (Settings tab rows on the next tick)
			cr.detectClues(clueData);
			// a search asked for before the requirement inputs were known used a snapshot without quests or
			// varbits: search again with the full player state
			refreshRoute();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(pathOverlay);
		overlayManager.remove(minimapOverlay);
		overlayManager.remove(worldMapOverlay);
		overlayManager.remove(hintOverlay);
		overlayManager.remove(itemOverlay);
		overlayManager.remove(spellOverlay);
		overlayManager.remove(boxOverlay);
		stepTargets = null;
		BankFilterButton bb = bankButton;
		bankButton = null;
		if (bb != null)
		{
			onClient(bb::shutDown);
		}
		worldMapOverlay = null;
		destinationMenu = null;
		clientToolbar.removeNavigation(navButton);
		PlayerTracker t = tracker;
		if (t != null)
		{
			onClient(t::flushSaves); // the bank / ownership cache changes not written yet
		}
		ClueRoutes cr = clueRoutes;
		if (cr != null)
		{
			onClient(cr::flushSaves); // the STASH units not written yet
		}
		// values only the client thread uses are reset there (shutDown runs on the Swing thread)
		onClient(() -> mapTables = null);
		clueRoutes = null;
		slayerRoutes = null;
		questSteps = null;
		destinations = null;
		charting = null;
		routes.shutdown();
		routes = null;
		tracker = null;
		follower = null;
		api = null;
		tickGroups = null;
		transportFinder = null;
		answerQuestions = null;
		placeKinds = null;
		SwingUtilities.invokeLater(() -> navShown = false);
		panel = null;
		navButton = null;
		log.debug("Best Path stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (OtherPathPlugins.watches(e.getGroup()))
		{
			onClient(this::checkOtherPlugins); // another plugin's "Use 'Shortest Path'" setting
			return;
		}
		if (BestPathConfig.GROUP.equals(e.getGroup()) && TransportConfig.KEYS.contains(e.getKey()))
		{
			// a Settings tab switch changed in the config panel: this account's choices follow
			onClient(this::transportConfigChanged);
			return;
		}
		if (BestPathConfig.GROUP.equals(e.getGroup()) && !PROFILE_KEYS.contains(e.getKey()))
		{
			SwingUtilities.invokeLater(this::updateNavButton);
			// the Settings tab mirrors the config panel's settings
			toSidebar(sidebar -> sidebar.configChanged());
			if (BestPathConfig.DEVELOPER_KEY.equals(e.getKey()))
			{
				boolean on = config.developerDetails();
				onClient(() -> shownWhere = null); // the box is filled again on the next tick
				toSidebar(sidebar -> sidebar.setDeveloper(on));
			}
			RouteFollower f = follower;
			if (f != null && (BestPathConfig.REROUTE_KEY.equals(e.getKey()) || BestPathConfig.ARRIVE_KEY.equals(e.getKey())))
			{
				f.setLimits(config.rerouteDistance(), config.arriveDistance()); // from the next tick on
			}
			if (f != null && BestPathConfig.CLEAR_ON_ARRIVAL_KEY.equals(e.getKey()))
			{
				f.setClearOnArrival(config.clearOnArrival());
			}
			RouteService rs = routes;
			if (rs != null && BestPathConfig.EXPLAIN_NO_ROUTE_KEY.equals(e.getKey()))
			{
				rs.setExplainNoRoute(config.explainNoRoute()); // from the next "no route" on
			}
			if (BestPathConfig.PREFER_SHORTEST_CLUE_KEY.equals(e.getKey())
				|| BestPathConfig.PREFER_SLAYER_OVERLAY_KEY.equals(e.getKey()))
			{
				onClient(this::checkOtherPlugins); // preferred: no more warning about that plugin
			}
			if (BestPathConfig.PLUGIN_ROUTES_KEY.equals(e.getKey()))
			{
				onClient(() ->
				{
					QuestHelperSteps qs = questSteps;
					if (qs != null)
					{
						qs.show(); // the Functions tab says whether routes follow Quest Helper
					}
				});
			}
			if (BestPathConfig.REMIND_STASH_KEY.equals(e.getKey()))
			{
				// the STASH put-back reminder switched: the clue route gets or loses its first stop; a route to the
				// unit only is dropped when switched off
				onClient(() ->
				{
					ClueRoutes cr = clueRoutes;
					if (cr != null)
					{
						cr.firstStopsChanged();
					}
				});
			}
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		ProfileStore ps = store;
		PlayerTracker t = tracker;
		if (ps == null || t == null || ps.isWriting())
		{
			return; // stopped, or our own write just created this account's profile: nothing to reload
		}
		loadAccount(t);
	}

	/** Client thread: this account's saved choices and state (start, login, account switch). */
	private void loadAccount(PlayerTracker t)
	{
		loadSettings();
		t.loadProfile();
		showAnswers(t);
		ClueRoutes cr = clueRoutes;
		if (cr != null)
		{
			cr.loadProfile();
		}
		SavedDestinations d = destinations;
		if (d != null)
		{
			d.load();
		}
		SlayerRoutes sr = slayerRoutes;
		if (sr != null)
		{
			sr.loadProfile();
		}
	}

	/**
	 * Messages to Best Path's own namespace go to the public API; messages to the Shortest Path plugin carry Quest
	 * Helper's steps ({@link QuestHelperSteps}).
	 */
	@Subscribe
	public void onPluginMessage(PluginMessage m)
	{
		if (BestPathApi.NAMESPACE.equals(m.getNamespace()))
		{
			ApiBridge a = api;
			if (a != null)
			{
				a.onMessage(m); // the public API (docs/API.md)
			}
			return;
		}
		if (!QuestHelperPath.NAMESPACE.equals(m.getNamespace()))
		{
			return;
		}
		onClient(() -> shortestPathMessage(m));
	}

	/** Client thread: a message sent to the Shortest Path plugin (Quest Helper's steps, or another plugin's). */
	private void shortestPathMessage(PluginMessage m)
	{
		ClueRoutes cr = clueRoutes;
		QuestHelperSteps qs = questSteps;
		if (cr == null || qs == null)
		{
			return; // stopped
		}
		if (cr.holdsRoute())
		{
			// the Shortest Clue plugin sends the clue's place this way; its route straight there must not replace
			// Best Path's clue route (which may first kill for a key)
			log.debug("Shortest Path message '{}' left alone: a clue is carried and Best Path routes it", m.getName());
			return;
		}
		qs.onMessage(m);
	}

	/** Another plugin started or stopped: it may be one whose Shortest Path messages get in the way. */
	@Subscribe
	public void onPluginChanged(PluginChanged e)
	{
		onClient(this::checkOtherPlugins);
	}

	/**
	 * Client thread: the other plugins running now whose messages to Shortest Path replace Best Path's routes
	 * ({@link OtherPathPlugins}), shown above the sidebar's tabs while they run. Never in the game chat.
	 */
	private void checkOtherPlugins()
	{
		if (routes == null)
		{
			return; // stopped
		}
		Set<String> running = new HashSet<>();
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p != this && pluginManager.isPluginActive(p))
			{
				running.add(p.getName());
			}
		}
		List<String> w = OtherPathPlugins.warnings(running::contains, configManager::getConfiguration,
			key -> Boolean.parseBoolean(configManager.getConfiguration(BestPathConfig.GROUP, key)));
		if (!w.equals(otherPluginWarnings))
		{
			otherPluginWarnings = w;
			log.debug("Other plugins in the way: {}", w);
			toSidebar(sidebar -> sidebar.showOtherPluginWarnings(w));
		}
	}

	/**
	 * Opens the Best Path sidebar by itself when {@code wanted} (that case's switch in "Open the sidebar") and the
	 * sidebar button is shown (not with "Show sidebar" off or minimal mode; a route-only sidebar's button shows once
	 * the route is set). Opens the Slayer page ({@code slayer}) or the Route tab. Any thread.
	 */
	private void openSidebar(boolean wanted, boolean slayer)
	{
		if (!wanted)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			updateNavButton();
			BestPathPanel p = panel;
			if (!navShown || p == null || navButton == null)
			{
				return;
			}
			if (slayer)
			{
				p.selectFunctionsTab();
				p.openFunctionPage("Slayer task");
			}
			else
			{
				p.selectRouteTab();
			}
			clientToolbar.openPanel(navButton);
		});
	}

	/** Client thread: a destination the player picked goes to the top of the recent destinations (saved per account). */
	private void remember(RouteRequest request)
	{
		SavedDestinations d = destinations;
		if (d != null)
		{
			d.remember(request);
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		PlayerTracker t = tracker;
		RouteFollower f = follower;
		if (t == null || f == null)
		{
			return;
		}
		int tick = client.getTickCount();
		t.onTick(tick);
		SlayerRoutes sr = slayerRoutes;
		if (sr != null)
		{
			sr.onTick(); // the Slayer task, read once after one of its vars changed
		}
		if (t.house().onTick(tick))
		{
			inputsChanged(); // the house scan found other furniture: routes may use other house teleports
		}
		if (t.cooldownTurnedReady())
		{
			inputsChanged(); // a home or minigame teleport is ready again: the route is checked once, quietly
		}
		showBoats(t);
		showHouse(t);
		showWhere(t);
		ChartingRoutes ch = charting;
		if (ch != null)
		{
			ch.onTick();
		}
		WorldPoint w = t.routePosition(); // inside a house: the house spot (the step after a teleport into it)
		playerTile = w == null ? -1 : Tiles.pack(w.getX(), w.getY(), w.getPlane());
		if (t.unlocks().onTick(w))
		{
			inputsChanged(); // a spirit tree was seen grown (or not any more): routes may use it or not
		}
		// on a Sailing boat the tile is not a map tile: following pauses
		if (w != null && !t.isOnBoat())
		{
			f.onTick(tick, w.getX(), w.getY(), w.getPlane(), t.destination(), t::snapshot);
		}
		StepTargets targets = stepTargets;
		RouteService service = routes;
		if (targets != null && service != null)
		{
			targets.update(service.getCurrent(), f.getProgress()); // what to click next: found once a tick, drawn every frame
		}
		ClueRoutes cr = clueRoutes;
		if (cr != null)
		{
			cr.onTick(tick, t.serverPosition());
		}
	}

	/** The developer box's lines last given to the sidebar (client thread only); null = give them again. */
	private List<String> shownWhere;

	/**
	 * Client thread, each tick while "Developer details" is on: where the player stands, to the sidebar only when it
	 * changed.
	 */
	private void showWhere(PlayerTracker t)
	{
		if (!config.developerDetails())
		{
			return;
		}
		List<String> lines = t.whereLines();
		lines.addAll(WorldMapCheck.lines(client, worldMaps)); // only while the world map is open
		if (lines.equals(shownWhere))
		{
			return;
		}
		shownWhere = lines;
		toSidebar(sidebar -> sidebar.showWhere(lines));
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		ClueRoutes cr = clueRoutes;
		if (cr != null && e.getGroupId() == InterfaceID.TRAIL_CLUETEXT)
		{
			cr.clueInterfaceLoaded();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		PlayerTracker t = tracker;
		if (t != null && t.onItemContainerChanged(e))
		{
			inputsChanged();
		}
		int container = e.getContainerId();
		ChartingRoutes chw = charting;
		if (chw != null)
		{
			chw.onItemsChanged(container); // a charting tool carried, taken out or put away
		}
		ClueRoutes cr = clueRoutes;
		if (cr != null)
		{
			cr.onItemsChanged(e); // STASH deposits, the clue card's items, a clue picked up
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened e)
	{
		DestinationMenu m = destinationMenu;
		if (m != null)
		{
			m.onMenuOpened(e);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		ClueRoutes cr = clueRoutes;
		if (cr != null)
		{
			cr.onMenuOptionClicked(e); // "Search" on a STASH unit, or any other click
		}
		PlayerTracker t = tracker;
		if (t != null)
		{
			// a click that always leads to the own house
			t.house().onMenuClick(e.getMenuOption(), e.getMenuTarget(), e.getItemId());
			t.unlocks().onMenuClick(e.getMenuOption(), e.getMenuTarget()); // "Chop-down" on a canoe station
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired e)
	{
		ClueRoutes cr = clueRoutes;
		if (cr != null)
		{
			cr.onScriptPreFired(e); // the STASH chart may tell which units are filled
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired e)
	{
		BankFilterButton bb = bankButton;
		if (bb != null && e.getScriptId() == ScriptID.BANKMAIN_INIT)
		{
			bb.bankOpened(); // the bank's "Show route items" button
		}
		PlayerTracker t = tracker;
		if (t != null && t.unlocks().onScriptPostFired(e, t.getInputs().getCollectionLog(), t.position()))
		{
			inputsChanged(); // a collection log page showed an item that unlocks a transport
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		PlayerTracker t = tracker;
		if (t != null && t.unlocks().onChatMessage(e, t.getInputs().getCollectionLog()))
		{
			inputsChanged(); // "New item added to your collection log" for an item that unlocks a transport, or the
			// canoe station NPC took or handed back the axe
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		PlayerTracker t = tracker;
		if (t != null && t.onVarbitChanged(e))
		{
			inputsChanged();
		}
		if (t != null)
		{
			t.house().onVarbitChanged(e); // a nexus slot or building mode while in a house: scan again
			t.unlocks().onVarbitChanged(e); // a spirit tree patch: read on the next tick
			Tables tables = mapTables;
			if (t.unlocks().onCanoeStation(e, tables == null ? null : tables.itemGroups().ids("axe")))
			{
				inputsChanged(); // a canoe chopped with no axe on: an axe is stored, canoes need none
			}
		}
		ClueRoutes cr = clueRoutes;
		if (cr != null)
		{
			cr.onVarbitChanged(e); // the game's clue helper, the carried clue's STASH unit
		}
		SlayerRoutes sr = slayerRoutes;
		if (sr != null)
		{
			sr.onVarChanged(e.getVarbitId(), e.getVarpId()); // the Slayer task: read on the next tick
		}
		ChartingRoutes cw = charting;
		if (cw != null)
		{
			cw.onVarbitChanged(e); // a sea charting task done: read on the next tick
		}
	}

	// Objects spawned and despawned: the house scan (furniture in the scene, how the player entered a house) and the
	// path drawn under objects.
	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		objectChanged(e.getGameObject(), true);
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		objectChanged(e.getGameObject(), false);
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned e)
	{
		objectChanged(e.getWallObject(), true);
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned e)
	{
		objectChanged(e.getWallObject(), false);
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned e)
	{
		objectChanged(e.getDecorativeObject(), true);
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned e)
	{
		objectChanged(e.getDecorativeObject(), false);
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned e)
	{
		objectChanged(e.getGroundObject(), true);
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned e)
	{
		objectChanged(e.getGroundObject(), false);
	}

	/** Client thread: an object spawned or despawned: the house scan and the path drawn under objects follow it. */
	private void objectChanged(TileObject o, boolean spawned)
	{
		PlayerTracker t = tracker;
		if (t != null)
		{
			t.house().onObject(o, spawned);
		}
		RoutePathOverlay po = pathOverlay;
		if (po != null)
		{
			po.objectsChanged();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		PlayerTracker t = tracker;
		if (t != null && t.onStatChanged(e))
		{
			inputsChanged();
		}
		ChartingRoutes cw = charting;
		if (cw != null)
		{
			cw.onStatChanged(e); // the Sailing level: which charting tasks the player can do
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		PlayerTracker t = tracker;
		if (t != null)
		{
			t.house().onGameState(e.getGameState()); // entering / leaving a house
		}
		StepTargets targets = stepTargets;
		if (targets != null && e.getGameState() != GameState.LOGGED_IN)
		{
			targets.sceneChanged(); // the old scene's objects and NPCs are gone: never drawn again
		}
		if (e.getGameState() == GameState.LOGGED_IN && !otherPluginWarnings.isEmpty())
		{
			checkOtherPlugins(); // logged in: check again which plugins are still in the way
		}
		if (t != null && e.getGameState() != GameState.LOADING)
		{
			showBoats(t); // logged out: the Settings tab says "Log in to see your boats"
			showHouse(t); // the same for the house
			ClueRoutes cr = clueRoutes;
			if (cr != null)
			{
				cr.cardChanged(); // logged in or out: the clue card follows (hidden while logged out)
			}
			ChartingRoutes cw = charting;
			if (cw != null)
			{
				cw.onLoginChange(); // shown at once: logged out, no game tick follows
			}
		}
		SlayerRoutes sr = slayerRoutes;
		if (sr != null && (e.getGameState() == GameState.LOGGED_IN
			|| e.getGameState() == GameState.LOGIN_SCREEN))
		{
			sr.onLogin(e.getGameState() == GameState.LOGGED_IN);
		}
		// a login or world hop can change members; LOADING -> LOGGED_IN after a scene load changes nothing
		if (loginEdges.onState(e.getGameState()))
		{
			inputsChanged();
		}
	}

	private void inputsChanged()
	{
		RouteFollower f = follower;
		if (f != null)
		{
			f.inputsChanged(client.getTickCount());
		}
	}

	/**
	 * Client thread: the player's boats for the Settings tab, when what it shows changed (the boat varbits are read
	 * again only after one of them changed, or the player got on or off a boat).
	 */
	private void showBoats(PlayerTracker t)
	{
		PlayerBoats b = t.boatsForSidebar();
		if (b != null)
		{
			toSidebar(sidebar -> sidebar.showBoats(b));
		}
	}

	/**
	 * Client thread: the Settings tab's "House (POH)" section (where the house is, what the house scan found), when
	 * what it shows changed.
	 */
	private void showHouse(PlayerTracker t)
	{
		HouseView v = t.houseForSidebar();
		if (v != null)
		{
			toSidebar(sidebar -> sidebar.showHouse(v));
		}
	}

	/** Client thread: the account's player answers for the Settings tab's "Your answers". */
	private void showAnswers(PlayerTracker t)
	{
		Map<String, Boolean> a = t.getAnswers();
		toSidebar(sidebar -> sidebar.showAnswers(a));
	}

	/** Client thread: the current step changed; show it in the sidebar. */
	private void onProgress(Progress p)
	{
		ApiBridge a = api;
		if (a != null)
		{
			a.progress(p);
		}
		toSidebar(sidebar -> sidebar.showProgress(p));
	}

	/** Queues work for the client thread (game state is read and changed there only). */
	private void onClient(Runnable job)
	{
		clientThread.invokeLater(job);
	}

	/**
	 * Worker or calling thread: hand the result to Swing. {@code from} = the service that published it; results of
	 * a service from an earlier start (disable / enable while it searched) are dropped.
	 */
	private void onRouteResult(RouteService from, RouteResult r)
	{
		RouteService service = routes;
		if (service == null || service != from)
		{
			return;
		}
		ApiBridge a = api;
		if (a != null)
		{
			a.published(from, r); // tells the plugin whose route it is (docs/API.md)
		}
		SlayerRoutes sr = slayerRoutes;
		if (sr != null)
		{
			sr.routeFound(r);
		}
		WorldMapRouteOverlay mapOverlay = worldMapOverlay;
		if (mapOverlay != null)
		{
			mapOverlay.routeChanged(r); // builds the world map lines here (the route worker), never in render()
		}
		BankFilterButton bb = bankButton;
		if (bb != null)
		{
			onClient(bb::routeChanged); // the bank button's items (client thread)
		}
		Set<String> skipped = service.getSkipped();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.showResult(r, skipped);
				updateNavButton();
			}
		});
	}

	/** True if RuneLite's Bank Tags plugin is switched on (the bank filter button needs it). */
	private boolean bankTagsOn()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p instanceof BankTagsPlugin)
			{
				return pluginManager.isPluginEnabled(p);
			}
		}
		return false;
	}

	/** Swing thread. Shows or hides the sidebar button per the config panel's two sidebar settings. */
	private void updateNavButton()
	{
		RouteService service = routes;
		if (service == null || navButton == null)
		{
			return;
		}
		// minimal mode: no side panel at all
		boolean want = config.showSidebar() && !config.minimalMode() && (!config.sidebarOnlyWithRoute() || service.hasDestination());
		if (want != navShown)
		{
			if (want)
			{
				clientToolbar.addNavigation(navButton);
			}
			else
			{
				clientToolbar.removeNavigation(navButton);
			}
			navShown = want;
		}
	}

	/**
	 * Client thread: this account's Settings tab choices (start, login, logout, account switch). Logged out, the
	 * choices in use stay; see {@link AccountSettings}.
	 */
	private void loadSettings()
	{
		AccountSettings a = accountSettings;
		if (a == null)
		{
			return;
		}
		TransportSettings before = settings;
		TransportSettings s = a.onProfileChanged();
		settings = s;
		showInConfig(s);
		toSidebar(sidebar -> sidebar.showSettings(s));
		if (!s.equals(before))
		{
			refreshRoute();
		}
	}

	/**
	 * Client thread: the config panel mirrors these Settings tab choices ({@link TransportConfig}). Only values that
	 * differ are written.
	 */
	private void showInConfig(TransportSettings s)
	{
		for (Map.Entry<String, String> e : TransportConfig.values(s).entrySet())
		{
			if (!e.getValue().equals(configManager.getConfiguration(BestPathConfig.GROUP, e.getKey())))
			{
				configManager.setConfiguration(BestPathConfig.GROUP, e.getKey(), e.getValue());
			}
		}
	}

	/**
	 * Client thread: a Settings tab switch was changed in the config panel: this account's choices take it (saved), the
	 * sidebar shows it and the route is searched again, as for a change in the sidebar. No-op when nothing differs (the
	 * panel only showing this account's choices).
	 */
	private void transportConfigChanged()
	{
		AccountSettings a = accountSettings;
		if (a == null || routes == null)
		{
			return;
		}
		TransportSettings now = settings;
		TransportSettings s = TransportConfig.apply(now, key -> configManager.getConfiguration(BestPathConfig.GROUP, key));
		if (s.equals(now))
		{
			return;
		}
		settings = s;
		a.change(s);
		toSidebar(sidebar -> sidebar.showSettings(s));
		refreshRoute();
	}

	/**
	 * Client thread: searches again to the current destination with the newest player state and settings, quietly
	 * (the old route stays shown until the new one replaces it), the way an availability change does. No-op
	 * without a destination.
	 */
	private void refreshRoute()
	{
		RouteService service = routes;
		PlayerTracker t = tracker;
		if (service == null || t == null || !service.hasDestination())
		{
			return;
		}
		PlayerSnapshot snap = t.snapshot();
		if (snap != null)
		{
			service.refresh(snap, settings);
		}
	}

	/** Takes the snapshot on the client thread, then searches again to the current destination. */
	private void reroute()
	{
		onClient(() ->
		{
			RouteService service = routes;
			PlayerTracker t = tracker;
			PlayerSnapshot snap = t == null ? null : t.snapshot();
			if (service != null && snap != null)
			{
				service.reroute(snap, settings);
			}
		});
	}

	/**
	 * Client thread: a new destination (typed in the sidebar or picked with the right-click menu). Takes the player
	 * snapshot here; the search runs on the route worker.
	 */
	private void requestRoute(RouteRequest request)
	{
		RouteService service = routes;
		PlayerTracker t = tracker;
		if (service == null || t == null)
		{
			return;
		}
		PlayerSnapshot snap = t.snapshot();
		if (snap == null)
		{
			service.fail(request, "Log in to find a route.");
		}
		else
		{
			service.request(request, snap, settings);
		}
	}

	/** The right-click menu's actions (client thread: menu clicks run there). */
	private final class MenuTarget implements DestinationMenu.Target
	{
		@Override
		public void setDestination(RouteRequest request)
		{
			requestRoute(request);
			remember(request);
			openSidebar(config.openForDestination(), false); // the world map's or a game tile's "Set destination"
		}

		@Override
		public void clearDestination()
		{
			RouteService service = routes;
			if (service != null)
			{
				service.clear();
			}
		}

		@Override
		public boolean hasDestination()
		{
			RouteService service = routes;
			return service != null && service.hasDestination();
		}
	}

	/** "Slayer task/Kalphites" -> https://oldschool.runescape.wiki/w/Slayer_task/Kalphites */
	static String wikiUrl(String page)
	{
		StringBuilder sb = new StringBuilder("https://oldschool.runescape.wiki/w/");
		for (String part : page.trim().replace(' ', '_').split("/", -1))
		{
			if (sb.charAt(sb.length() - 1) != '/')
			{
				sb.append('/');
			}
			try
			{
				sb.append(URLEncoder.encode(part, "UTF-8").replace("+", "_").replace("%27", "'"));
			}
			catch (UnsupportedEncodingException e)
			{
				throw new IllegalStateException(e);
			}
		}
		return sb.toString();
	}

	/** Swing thread: runs {@code job} on the client thread with this start's clue part, if the plugin still runs. */
	private void onClues(Consumer<ClueRoutes> job)
	{
		onClient(() ->
		{
			ClueRoutes cr = clueRoutes;
			if (cr != null)
			{
				job.accept(cr);
			}
		});
	}

	/** Swing thread: runs {@code job} on the client thread with this start's Slayer part, if the plugin still runs. */
	private void onSlayer(Consumer<SlayerRoutes> job)
	{
		onClient(() ->
		{
			SlayerRoutes sr = slayerRoutes;
			if (sr != null)
			{
				job.accept(sr);
			}
		});
	}

	/** Swing thread: runs {@code job} on the client thread with this start's saved destinations, if the plugin still runs. */
	private void onDestinations(Consumer<SavedDestinations> job)
	{
		onClient(() ->
		{
			SavedDestinations d = destinations;
			if (d != null)
			{
				job.accept(d);
			}
		});
	}

	/** Any thread: runs {@code show} on the Swing thread with the sidebar, when there is one. */
	private void toSidebar(Consumer<BestPathPanel> show)
	{
		SwingUtilities.invokeLater(() ->
		{
			BestPathPanel p = panel;
			if (p != null)
			{
				show.accept(p);
			}
		});
	}

	/** What the plugin's parts may use of it ({@link PluginContext}). */
	private final class Context implements PluginContext
	{
		@Override
		public RouteService routes()
		{
			return routes;
		}

		@Override
		public PlayerTracker tracker()
		{
			return tracker;
		}

		@Override
		public TransportSettings settings()
		{
			return settings;
		}

		@Override
		public void toSidebar(Consumer<BestPathPanel> show)
		{
			BestPathPlugin.this.toSidebar(show);
		}

		@Override
		public void onClient(Runnable job)
		{
			BestPathPlugin.this.onClient(job);
		}

		@Override
		public void requestRoute(RouteRequest request)
		{
			BestPathPlugin.this.requestRoute(request);
		}

		@Override
		public void remember(RouteRequest request)
		{
			BestPathPlugin.this.remember(request);
		}

		@Override
		public void openSidebar(boolean wanted, boolean slayer)
		{
			BestPathPlugin.this.openSidebar(wanted, slayer);
		}
	}

	/** The sidebar's requests (Swing thread). */
	private final class Actions implements PanelActions
	{
		/** The Settings tab's copy of a config panel setting (the two mirror each other). */
		@Override
		public String config(String key)
		{
			return configManager.getConfiguration(BestPathConfig.GROUP, key);
		}

		/** A config panel setting changed in the Settings tab: stored as the config panel would (ConfigChanged follows). */
		@Override
		public void setConfig(String key, String value)
		{
			configManager.setConfiguration(BestPathConfig.GROUP, key, value);
		}

		@Override
		public int playerTile()
		{
			return playerTile;
		}

		/** RuneLite's own colour picker (as the config panel's); the colour is taken when the picker closes. */
		@Override
		public void pickColour(Component owner, Color now, String title,
			Consumer<Color> chosen)
		{
			RuneliteColorPicker picker = colorPickerManager.create(owner, now,
				title, true);
			picker.setLocationRelativeTo(owner);
			picker.setOnClose(chosen);
			picker.setVisible(true);
		}

		@Override
		public void findRoute(RouteRequest request)
		{
			onClient(() ->
			{
				requestRoute(request);
				remember(request); // typed in the Route tab, or a recent destination clicked
			});
		}

		@Override
		public void openWiki(String page)
		{
			if (page != null && !page.isEmpty())
			{
				// the player's own browser opens the page; the plugin reads nothing from the Wiki
				LinkBrowser.browse(wikiUrl(page));
			}
		}

		@Override
		public void findSlayerNearest(RouteRequest nearest, RouteRequest ifNoRoute)
		{
			onSlayer(sr -> sr.routeToNearest(nearest, ifNoRoute));
		}

		@Override
		public void preferSlayerPlace(String task, String place, boolean on)
		{
			onSlayer(sr -> sr.prefer(task, place, on));
		}

		@Override
		public void updateSlayerTimes()
		{
			onSlayer(SlayerRoutes::updateTimes); // times again from where the player stands now
		}

		@Override
		public void useQuestStep()
		{
			onClient(() ->
			{
				QuestHelperSteps qs = questSteps;
				if (qs != null)
				{
					qs.routeToStep();
				}
			});
		}

		@Override
		public void removeRecent(int index)
		{
			onDestinations(d -> d.removeRecent(index));
		}

		@Override
		public void clearRecent()
		{
			onDestinations(SavedDestinations::clearRecent);
		}

		@Override
		public void saveLocation(String name, int tile)
		{
			onDestinations(d -> d.saveLocation(name, tile));
		}

		@Override
		public void removeSavedLocation(int index)
		{
			onDestinations(d -> d.removeSavedLocation(index));
		}

		@Override
		public void clearRoute()
		{
			RouteService service = routes;
			if (service != null)
			{
				service.clear();
			}
		}

		@Override
		public void useClue()
		{
			onClues(ClueRoutes::routeToClue);
		}

		@Override
		public void skipForThisRoute(String transportId)
		{
			RouteService service = routes;
			if (transportId != null && transportId.startsWith(StashTracker.STOP_PREFIX))
			{
				// "Skip" on the put-back card: that reminder is forgotten for good (saved per account)
				onClues(cr -> cr.skipPutBack(transportId, client.getTickCount()));
				return;
			}
			if (service != null)
			{
				service.skipForThisRoute(transportId);
				reroute();
			}
		}

		@Override
		public void clearSkips()
		{
			RouteService service = routes;
			if (service != null)
			{
				service.clearSkips();
				reroute();
			}
		}

		@Override
		public void boatMode(BoatMode mode)
		{
			// the boat card's choice: kept with the route, then a new search
			RouteService service = routes;
			if (service != null)
			{
				service.setBoatMode(mode);
				reroute();
			}
		}

		@Override
		public void settingsChanged(TransportSettings s)
		{
			settings = s;
			onClient(() ->
			{
				AccountSettings a = accountSettings;
				if (a != null && routes != null)
				{
					a.change(s); // saved per account, or kept until an account logs in
					showInConfig(s); // the config panel shows the same
					refreshRoute(); // the route changes as for an availability change
				}
			});
		}

		@Override
		public void answer(String answer, Boolean yes)
		{
			// saved for the account; the route follows through the availability check (the answer is one of its
			// inputs), as for any other change of what the player can use
			onClient(() ->
			{
				PlayerTracker t = tracker;
				if (t != null && t.setAnswer(answer, yes))
				{
					showAnswers(t);
					inputsChanged();
				}
			});
		}

		@Override
		public void settingsSaved(TransportSettings s)
		{
			settings = s;
			onClient(() ->
			{
				AccountSettings a = accountSettings;
				if (a != null)
				{
					a.change(s); // a display choice: saved, the route stays as it is
					showInConfig(s);
				}
			});
		}
	}

	@Provides
	BestPathConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BestPathConfig.class);
	}
}
