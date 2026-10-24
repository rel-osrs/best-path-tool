package com.bestpath.dev;

import com.bestpath.clue.ClueCard;
import com.bestpath.clue.DetectedClue;
import com.bestpath.clue.StashState;
import com.bestpath.core.req.PlayerStateDiagnostics;
import com.bestpath.core.req.RequirementText;
import com.bestpath.core.path.Tiles;
import com.bestpath.player.RequirementInputs;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteEngine;
import com.bestpath.route.RouteFollower;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteService;
import com.bestpath.route.TransportSettings;
import com.bestpath.ui.BestPathPanel;
import com.bestpath.ui.PanelActions;
import com.bestpath.ui.PanelDriver;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.laf.RuneLiteLAF;

/**
 * DEV ONLY: the real sidebar ({@link BestPathPanel}) and the real
 * {@link RouteService}, wired to the fake engine and a fake player instead of
 * the game client. Used by {@link UiPreview} (window) and {@link UiShot}
 * (pictures). Create on the Swing thread.
 */
final class PreviewHarness implements PanelActions
{
	final BestPathPanel panel;
	final RouteService service;
	/** The real route follower, fed with the fake player's tile ({@link #tick}). */
	final RouteFollower follower;
	private volatile PlayerSnapshot player;
	private int tickCount;
	/** The pictures (and, for the clue card, item names) drawn from the cache by gradlew previewIcons. */
	private final PreviewIcons icons = new PreviewIcons(PreviewIcons.DIR);
	/** The exported tables when the engine is the real one (item groups for the clue card), else null. */
	private final com.bestpath.core.data.Tables tables;
	/** The made-up STASH unit state of the fake player's clue (the plugin reads the game's vars). */
	private volatile StashState stash = StashState.UNKNOWN;
	private volatile TransportSettings settings = TransportSettings.defaults();

	/**
	 * @param delayMillis fake search time (0 = instant)
	 * @param inline      run searches on the calling thread (pictures) instead of the worker (window)
	 */
	PreviewHarness(PlayerSnapshot player, long delayMillis, boolean inline)
	{
		this(player, FakeRoutes.engine(delayMillis), inline);
	}

	/** With another engine (the real one on the exported map data: {@link RealData#engine()}). */
	PreviewHarness(PlayerSnapshot player, RouteEngine engine, boolean inline)
	{
		this.player = player;
		this.panel = new BestPathPanel(this, icons);
		// the Settings tab's sections start folded; the pictures show what is in them
		PanelDriver.setSettingsSectionsOpen(panel, true);
		this.tables = engine instanceof com.bestpath.route.CoreRouteEngine
			? ((com.bestpath.route.CoreRouteEngine) engine).getRouter().getData().getTables() : null;
		this.service = inline
			? new RouteService(engine, this::show, new InlineExecutor())
			: new RouteService(engine, this::show);
		this.follower = new RouteFollower(service, () -> settings, () -> RequirementInputs.NONE, this::showProgress);
		// the Settings tab's "Teleport and travel ticks": the real data's, else the real tables with the sample route
		panel.showTickGroups(engine instanceof com.bestpath.route.CoreRouteEngine
			? com.bestpath.core.data.TickGroups.of(((com.bestpath.route.CoreRouteEngine) engine).getRouter().getData().getTransports(),
			((com.bestpath.route.CoreRouteEngine) engine).getRouter().getData().getTables())
			: FakeRoutes.tickGroups());
		// "Transport ticks": every transport of the real data
		if (engine instanceof com.bestpath.route.CoreRouteEngine)
		{
			com.bestpath.core.data.PluginData pd = ((com.bestpath.route.CoreRouteEngine) engine).getRouter().getData();
			panel.showTransports(com.bestpath.route.TransportFinder.of(pd.getTransports(), pd.getTables(),
				com.bestpath.core.data.TickGroups.of(pd.getTransports(), pd.getTables()), pd.getWilderness()));
		}
		// "Your answers": every question the real data asks (the ones not answered yet with Yes / No), as the plugin does
		if (engine instanceof com.bestpath.route.CoreRouteEngine)
		{
			panel.showQuestions(com.bestpath.route.AnswerQuestions.answersIn(
				((com.bestpath.route.CoreRouteEngine) engine).getRouter().requirements()));
		}
	}

	/** One fake game tick with the player on this packed tile (Swing thread here, the client thread in RuneLite). */
	void tick(int tile)
	{
		PlayerSnapshot p = player;
		player = new PlayerSnapshot(Tiles.x(tile), Tiles.y(tile), Tiles.plane(tile), p.getState(), p.getBoats());
		follower.onTick(++tickCount, Tiles.x(tile), Tiles.y(tile), Tiles.plane(tile), -1, () -> player);
	}

	PlayerSnapshot getPlayer()
	{
		return player;
	}

	private void showProgress(Progress p)
	{
		Runnable update = () -> panel.showProgress(p);
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	/** RuneLite's own look and feel, fonts and tooltip timing (as ClientUI sets them up). */
	static void setupLookAndFeel()
	{
		RuneLiteLAF.setup();
		ToolTipManager.sharedInstance().setInitialDelay(300);
		ToolTipManager.sharedInstance().setDismissDelay(10_000);
	}

	private void show(RouteResult r)
	{
		Runnable update = () -> panel.showResult(r, service.getSkipped());
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	void setPlayer(PlayerSnapshot p)
	{
		player = p;
		service.reroute(p, settings);
	}

	@Override
	public void findRoute(RouteRequest request)
	{
		service.request(request, player, settings);
	}

	@Override
	public int playerTile()
	{
		return player == null ? -1 : Tiles.pack(player.getX(), player.getY(), player.getPlane());
	}

	@Override
	public void clearRoute()
	{
		service.clear();
	}

	/** The clue the fake player carries (as the plugin's clue detection would report it). */
	private volatile DetectedClue clue = DetectedClue.NONE;

	/** Shows a detected clue in the sidebar (as the plugin does on the Swing thread). */
	void showClue(DetectedClue c)
	{
		clue = c == null ? DetectedClue.NONE : c;
		panel.showClue(clue);
		panel.showClueCard(clueCard());
	}

	/** The STASH unit's state for the fake player's clue (made up: the plugin reads the game's vars). */
	void setStash(StashState s)
	{
		stash = s == null ? StashState.UNKNOWN : s;
		panel.showClueCard(clueCard());
	}

	/** The clue step's card for the fake player, made the plugin's way (null without a clue). */
	ClueCard clueCard()
	{
		return ClueCard.build(clue, player.getState(), stash, tables == null ? RequirementText.ItemGroups.NONE : tables.itemGroups(),
			tables, icons::itemName);
	}

	@Override
	public void useClue()
	{
		DetectedClue c = clue;
		if (c.isRoutable())
		{
			// as the plugin: the clue step's needs go with the request (bank first when they are only in the bank)
			ClueCard card = clueCard();
			service.request(card == null ? c.request() : c.request().withNeeds(card.destinationNeeds()), player, settings);
		}
	}

	@Override
	public void skipForThisRoute(String transportId)
	{
		service.skipForThisRoute(transportId);
		service.reroute(player, settings);
	}

	@Override
	public void clearSkips()
	{
		service.clearSkips();
		service.reroute(player, settings);
	}

	@Override
	public void boatMode(com.bestpath.route.BoatMode mode)
	{
		service.setBoatMode(mode);
		service.reroute(player, settings);
	}

	@Override
	public void settingsChanged(TransportSettings s)
	{
		settings = s;
		service.reroute(player, s);
	}

	/** The RuneLite config values (as RuneLite stores them; the plugin's are in RuneLite's ConfigManager). */
	final java.util.Map<String, String> config = new java.util.HashMap<>();

	@Override
	public String config(String key)
	{
		return config.get(key);
	}

	/** A config panel setting changed in the Settings tab: stored, and the tab reads it again (as the plugin's ConfigChanged). */
	@Override
	public void setConfig(String key, String value)
	{
		config.put(key, value);
		panel.configChanged();
	}

	/** The preview window has no RuneLite colour picker: Swing's own. */
	@Override
	public void pickColour(java.awt.Component owner, java.awt.Color now, String title,
		java.util.function.Consumer<java.awt.Color> chosen)
	{
		java.awt.Color c = javax.swing.JColorChooser.showDialog(owner, title, now);
		if (c != null)
		{
			chosen.accept(c);
		}
	}

	/** Sets config values (as the config panel would) and shows them in the Settings tab. */
	void setConfigs(java.util.Map<String, String> values)
	{
		config.putAll(values);
		panel.configChanged();
	}

	/** The fake player's answers in the data's words (the plugin saves them per account). */
	private final java.util.Map<String, Boolean> answers = new java.util.LinkedHashMap<>();

	/**
	 * Yes / No under a card or in "Your answers": the fake player gets the answer, the Settings tab
	 * lists it and the route is searched again quietly, as the plugin's availability check does.
	 */
	@Override
	public void answer(String answer, Boolean yes)
	{
		answers.keySet().removeIf(k -> k.equalsIgnoreCase(answer.trim()));
		if (yes != null)
		{
			answers.put(answer.trim(), yes);
		}
		PlayerSnapshot p = player;
		player = new PlayerSnapshot(p.getX(), p.getY(), p.getPlane(), withAnswers(p.getState(), answers), p.getBoats());
		panel.showAnswers(new java.util.LinkedHashMap<>(answers));
		service.refresh(player, settings);
	}

	/** A copy of {@code p} whose answers are exactly {@code given}, unanswered ones asked (the plugin's player). */
	static com.bestpath.core.req.PlayerState withAnswers(com.bestpath.core.req.PlayerState p, java.util.Map<String, Boolean> given)
	{
		com.bestpath.core.req.PlayerState.Builder b = com.bestpath.core.req.PlayerStateBuilder.allItems(
			new com.bestpath.core.req.PlayerState.Builder(PlayerStateDiagnostics.name(p)).members(p.isMembers()).allUnlocked(p.isAllUnlocked()), p.isAllItems())
			.askUnanswered(true);
		for (com.bestpath.core.req.Skill s : com.bestpath.core.req.Skill.values())
		{
			b.level(s, p.level(s));
		}
		PlayerStateDiagnostics.quests(p).forEach(b::quest);
		PlayerStateDiagnostics.varbits(p).forEach(b::varbit);
		PlayerStateDiagnostics.varps(p).forEach(b::varp);
		p.getInventory().forEach(b::carry);
		PlayerStateDiagnostics.bank(p).forEach(b::bank);
		PlayerStateDiagnostics.pouch(p).forEach(b::pouch);
		p.getHouseThings().forEach(b::houseThing);
		p.getCoolingDown().forEach(b::coolingDown);
		given.forEach(b::answer);
		for (com.bestpath.core.req.EquipSlot s : com.bestpath.core.req.EquipSlot.values())
		{
			if (p.worn(s) > 0)
			{
				b.wear(s, p.worn(s));
			}
		}
		return b.house(p.getHouse()).spellbook(p.getSpellbook()).pouchInBank(p.isPouchInBank()).build();
	}

	/** Runs each task at once on the calling thread (for pictures: no waiting). */
	private static final class InlineExecutor extends AbstractExecutorService
	{
		private volatile boolean shut;

		@Override
		public void execute(Runnable command)
		{
			command.run();
		}

		@Override
		public void shutdown()
		{
			shut = true;
		}

		@Override
		public List<Runnable> shutdownNow()
		{
			shut = true;
			return List.of();
		}

		@Override
		public boolean isShutdown()
		{
			return shut;
		}

		@Override
		public boolean isTerminated()
		{
			return shut;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit)
		{
			return true;
		}
	}
}
