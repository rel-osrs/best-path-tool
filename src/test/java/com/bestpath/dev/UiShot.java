package com.bestpath.dev;

import com.bestpath.clue.ClueWatcher;
import com.bestpath.clue.DetectedClue;
import com.bestpath.clue.StashState;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.data.ClueDataDiagnostics;
import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.data.PluginRouterDiagnostics;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerStateDiagnostics;
import com.bestpath.route.CoreRouteEngine;
import com.bestpath.route.NearestPlaces;
import com.bestpath.route.NoMapDataEngine;
import com.bestpath.route.PlayerSnapshot;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteEngine;
import com.bestpath.route.RouteJob;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.TransportSettings;
import com.bestpath.ui.BestPathPanel;
import com.bestpath.ui.PanelDriver;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * DEV ONLY: renders the sidebar in its main states, off screen (headless, no
 * window, no game client), to PNG files: {@code gradlew uiShot} writes
 * build/ui-shots/*.png in a few seconds. See docs/PLUGIN_UI.md.
 *
 * <p>{@code gradlew uiShot -PrealData} also saves real-*.png: routes searched
 * for real on the exported map data (gradlew -p tool exportPlugin) with the
 * plugin's own engine, for the Data Tool's test players.
 */
public final class UiShot
{
	/** RuneLite's sidebar width with its scrollbar (225 + 17). */
	static final int WIDTH = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;
	/** About the sidebar's height on a 1080p screen. */
	static final int HEIGHT = 800;
	/** East Ardougne market and Draynor Village bank: the home-teleport pictures' start and destination. */
	private static final int[] ARDOUGNE = {2662, 3305};
	private static final RouteRequest DRAYNOR = new RouteRequest(3093, 3244, 0);
	/** Edgeville bank: the item-teleport marks pictures' destination (an amulet of glory's landing is nearby). */
	private static final RouteRequest EDGEVILLE_BANK = new RouteRequest(3094, 3491, 0);

	private UiShot()
	{
	}

	public static void main(String[] args) throws Exception
	{
		long t0 = System.nanoTime();
		boolean real = false;
		File chosen = null;
		for (String a : args)
		{
			if ("--real-data".equals(a))
			{
				real = true;
			}
			else
			{
				chosen = new File(a);
			}
		}
		File dir = chosen != null ? chosen : new File("build/ui-shots");
		boolean useReal = real;
		if (!dir.isDirectory() && !dir.mkdirs())
		{
			throw new IOException("Cannot create " + dir);
		}
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				shots(dir);
				if (useReal)
				{
					realShots(dir);
				}
			}
			catch (IOException e)
			{
				throw new IllegalStateException(e);
			}
		});
		System.out.printf("UI pictures in %s (%d ms; JVM up %d ms)%n", dir.getAbsolutePath(),
			(System.nanoTime() - t0) / 1_000_000,
			java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
		System.exit(0);
	}

	private static void shots(File dir) throws IOException
	{
		PreviewHarness.setupLookAndFeel();
		PlayerSnapshot mid = FakeRoutes.players().values().iterator().next();

		PreviewHarness h = harness(mid);
		save(dir, "1-empty", h.panel, HEIGHT);

		h = harness(mid);
		h.panel.showResult(RouteResult.status(1, FakeRoutes.DESTINATION, RouteStatus.CALCULATING, "Calculating..."),
			Set.of());
		save(dir, "2-calculating", h.panel, HEIGHT);

		h = harness(mid);
		h.findRoute(FakeRoutes.DESTINATION);
		save(dir, "3-route-8-steps", h.panel, HEIGHT);
		save(dir, "3-route-8-steps-full", h.panel, 1400);

		// other plugins whose Shortest Path messages replace Best Path's routes, above the tabs
		PreviewHarness other = harness(mid);
		other.findRoute(FakeRoutes.DESTINATION);
		other.panel.showOtherPluginWarnings(com.bestpath.route.OtherPathPlugins.warnings(
			name -> name.equals("Shortest Clue") || name.equals("Configurable Slayer Task Overlay"), (group, key) -> "true",
			key -> false));
		save(dir, "3b-other-plugins-in-the-way", other.panel, HEIGHT);

		// press "Skip" on the teleport card, as a player would
		clickButton(h.panel, "Skip", 1);
		save(dir, "4-route-skipped", h.panel, HEIGHT);

		// the player's own tick changes: + three times on the teleport card, - three times on the manhole card (2 ticks:
		// down to 0, "-" then greyed and the third click does nothing); the cards show their total above - / + at once
		// (never broken down), the route and its total (235 + 3 - 2 = 236 ticks) follow when the clicks are sent
		h = harness(mid);
		h.findRoute(FakeRoutes.DESTINATION);
		clickButton(h.panel, "+", 1);
		clickButton(h.panel, "+", 1);
		clickButton(h.panel, "+", 1);
		clickButton(h.panel, "-", 2);
		clickButton(h.panel, "-", 2);
		clickButton(h.panel, "-", 2);
		save(dir, "4c-ticks-clicked", h.panel, HEIGHT);
		PanelDriver.sendWaitingChanges(h.panel);
		save(dir, "4d-ticks-rerouted", h.panel, HEIGHT);
		PanelDriver.selectSettingsTab(h.panel);
		save(dir, "4e-ticks-settings-full", h.panel, 1250 + CONFIG_PART);

		// "Teleport and travel ticks" in the Settings tab: - twice on Spell (4 -> 2 ticks), + once on
		// Fairy ring; the rows show it at once; sent, the route's spell teleport takes 2 ticks (total 233)
		h = harness(mid);
		h.findRoute(FakeRoutes.DESTINATION);
		PanelDriver.selectSettingsTab(h.panel);
		// no "Transport ticks" rows here: their - / + would come before the types' and move the clicks below
		h.panel.showTransports(com.bestpath.route.TransportFinder.EMPTY);
		// folded unless the player opens it; opened, as a click on its header does
		save(dir, "4f0-category-ticks-folded-full", h.panel, 1250 + CONFIG_PART);
		h.panel.showSettings(PanelDriver.settings(h.panel).withTicksListOpen(true));
		save(dir, "4f-category-ticks-settings-full", h.panel, SETTINGS_FULL);
		// the teleport types come right after "Transport ticks", before the bank stop's - / +
		int spell = groupIndex("spell");
		int ring = groupIndex("Fairy ring");
		clickButton(h.panel, "-", spell);
		clickButton(h.panel, "-", spell);
		clickButton(h.panel, "+", ring);
		layout(h.panel, WIDTH, HEIGHT);
		PanelDriver.scrollSettingsToTickGroups(h.panel);
		save(dir, "4g-category-ticks-clicked", h.panel, HEIGHT, false);
		PanelDriver.sendWaitingChanges(h.panel);
		save(dir, "4h-category-ticks-sent-full", h.panel, SETTINGS_FULL);
		h.panel.selectRouteTab();
		save(dir, "4i-category-ticks-rerouted", h.panel, HEIGHT);

		// a typed total (0 to 10000 ticks, click the total to type it): the teleport card's total
		// clicked, 200 typed (the box), Enter (the card shows 200 ticks at once), sent (the route follows)
		h = harness(mid);
		h.findRoute(FakeRoutes.DESTINATION);
		javax.swing.JTextField box = openTypingBox(h.panel, 0);
		box.setText("200");
		save(dir, "4j-ticks-typing", h.panel, HEIGHT, false);
		box.postActionEvent();
		save(dir, "4k-ticks-typed", h.panel, HEIGHT);
		PanelDriver.sendWaitingChanges(h.panel);
		save(dir, "4l-ticks-typed-rerouted", h.panel, HEIGHT);

		h = harness(mid);
		h.panel.showResult(FakeRoutes.walkOnly(1), Set.of());
		save(dir, "4b-walk-only", h.panel, HEIGHT);

		h = harness(mid);
		h.panel.showResult(FakeRoutes.noRoute(1), Set.of());
		save(dir, "5-no-route", h.panel, HEIGHT);

		h = harness(mid);
		RouteJob job = new RouteJob(1, FakeRoutes.DESTINATION, mid, TransportSettings.defaults(), Set.of());
		h.panel.showResult(new NoMapDataEngine().route(job), Set.of());
		save(dir, "6-no-map-data", h.panel, HEIGHT);

		h = harness(mid);
		PanelDriver.destinationField(h.panel).setText("3222, 3218, 7");
		PanelDriver.destinationField(h.panel).postActionEvent();
		save(dir, "7-bad-input", h.panel, HEIGHT);

		h = harness(mid);
		h.panel.showResult(RouteResult.status(1, FakeRoutes.DESTINATION, RouteStatus.ERROR,
			"Log in to find a route."), Set.of());
		save(dir, "8-not-logged-in", h.panel, HEIGHT);

		h = harness(mid);
		h.findRoute(FakeRoutes.DESTINATION);
		clickButton(h.panel, "Never use", 1);
		clickButton(h.panel, "Never use", 1);
		h.panel.showSettings(PanelDriver.settings(h.panel)
			.withCategory(TransportSettings.Category.HOUSE, false)
			.withAvoid(TransportSettings.Avoid.KNOWN_BUGS, true));
		PanelDriver.selectSettingsTab(h.panel);
		save(dir, "9-settings", h.panel, HEIGHT);
		save(dir, "9-settings-full", h.panel, 1100 + CONFIG_PART);
		// as the tab starts: all settings collapsed
		PanelDriver.setSettingsSectionsOpen(h.panel, false);
		save(dir, "9-settings-start-folded", h.panel, HEIGHT);
		PanelDriver.setSettingsSectionsOpen(h.panel, true);

		// Sailing: three made-up boats read the plugin's way (a sloop, a skiff switched off, a bottled raft)
		h = harness(mid);
		h.panel.showBoats(FakeBoats.sample(FakeBoats.SAMPLE_TABLE));
		h.panel.showSettings(PanelDriver.settings(h.panel).withBoatAllowed(2, false));
		PanelDriver.selectSettingsTab(h.panel);
		save(dir, "9b-settings-sailing-full", h.panel, 1250 + CONFIG_PART);

		// the config panel's settings in the Settings tab (the two mirror each other): the top with a
		// few values set as from the config panel (minimal mode, re-route at 15 tiles, arrows, orange text), then the
		// whole tab with Advanced unfolded (developer details on)
		h = harness(mid);
		h.setConfigs(java.util.Map.of("minimalMode", "true", "rerouteDistance", "15", "pathStyle", "ARROWS", "textColour",
			String.valueOf(java.awt.Color.ORANGE.getRGB()), "developerDetails", "true"));
		PanelDriver.selectSettingsTab(h.panel);
		save(dir, "9c-settings-config", h.panel, HEIGHT);
		PanelDriver.unfoldAdvanced(h.panel);
		save(dir, "9c-settings-config-full", h.panel, SETTINGS_FULL);

		// the Functions tab: the function select screen (a titled card per function), empty
		// and with saved locations, Quest Helper's step and recent destinations; then those functions' own pages
		h = harness(mid);
		h.panel.selectFunctionsTab();
		save(dir, "9d-functions-empty", h.panel, HEIGHT);
		RouteRequest questStep = RouteRequest.places(com.bestpath.route.QuestHelperPath.KEY,
			com.bestpath.route.QuestHelperPath.TITLE, List.of(), new int[]{Tiles.pack(3208, 3220, 2)});
		h.panel.showQuestStep(questStep, true);
		h.panel.showRecent(com.bestpath.route.RecentDestinations.NONE.with(RouteRequest.fromMap(2900, 3500))
			.with(EDGEVILLE_BANK).with(DRAYNOR).with(questStep));
		h.panel.showSaved(com.bestpath.route.SavedLocations.NONE.with("Home", Tiles.pack(3222, 3218, 0))
			.with("Herb patch", Tiles.pack(3058, 3311, 0)).with("Fishing guild", Tiles.pack(2611, 3393, 0)));
		save(dir, "9d-functions", h.panel, HEIGHT);
		h.panel.openFunctionPage("Saved locations");
		save(dir, "9d-functions-saved", h.panel, HEIGHT);
		h.panel.openFunctionPage("Recent destinations");
		save(dir, "9d-functions-recent", h.panel, HEIGHT);
		h.panel.openFunctionPage("Integrated plugins");
		save(dir, "9d-functions-integrated", h.panel, HEIGHT);
		save(dir, "9d-functions-integrated-full", h.panel, 1200);
		PanelDriver.showFunctionMenu(h.panel);
		nearestShots(dir, mid);

		// the Functions tab's Slayer task: a task while its travel times are worked out, with them
		// (fastest first), then Konar's (her area first)
		h = harness(mid);
		h.panel.selectFunctionsTab();
		h.panel.openFunctionPage("Slayer task");
		com.bestpath.route.SlayerTask slayer = com.bestpath.route.SlayerTask.of(com.bestpath.route.SlayerTaskTest.DATA,
			com.bestpath.core.data.WildernessZones.NONE, 53, 0, 87, 0, 3);
		h.panel.showSlayer(slayer);
		save(dir, "9e-slayer-timing", h.panel, HEIGHT);
		java.util.Map<String, Integer> slayerTimes = java.util.Map.of(slayer.getLocations().get(0).key(), 412,
			slayer.getLocations().get(1).key(), 236);
		h.panel.showSlayer(slayer.withTimes(slayerTimes));
		save(dir, "9e-slayer", h.panel, HEIGHT);
		// the stored task's card (no Go), then the filters (safespot ticked: the cave only)
		h.panel.showSlayer(slayer.withTimes(slayerTimes).withStored(com.bestpath.route.SlayerTask.Stored.of(
			com.bestpath.route.SlayerTaskTest.DATA, 79, 0, 12, 3)));
		save(dir, "9e-slayer-stored", h.panel, HEIGHT);
		clickButton(h.panel, "Only places with a safespot", 0);
		save(dir, "9e-slayer-filtered", h.panel, HEIGHT);
		clickButton(h.panel, "Only places with a safespot", 0);
		// the "preferred" system: the lair starred, at the top
		h.panel.showSlayer(slayer.withTimes(slayerTimes).withPreferred(slayer.getLocations().get(0).key(), true));
		save(dir, "9e-slayer-preferred", h.panel, HEIGHT);
		h.panel.showSlayer(com.bestpath.route.SlayerTask.of(com.bestpath.route.SlayerTaskTest.DATA,
			com.bestpath.core.data.WildernessZones.NONE, 53, 0, 120, 12, 8).withTimes(slayerTimes));
		save(dir, "9e-slayer-konar", h.panel, HEIGHT);

		h = harness(mid);
		h.panel.selectFunctionsTab();
		h.panel.openFunctionPage("Sea charting");
		// sea charting: Sailing 20, one task done: the three big routes, "All oceans"
		// (a row per ocean, red when it can't be completed now); then the Ardent Ocean with the Bay of Sarim opened (a Go
		// per sea, none per task, the rock needing Sailing 24 in red)
		h.panel.showCharting(com.bestpath.route.ChartingProgress.read(com.bestpath.route.ChartingProgressTest.tasks(),
			v -> v == 18574 ? 1 : 0, 20, null, null, com.bestpath.route.ChartingProgress.ANY_BOAT));
		save(dir, "9e-functions-charting", h.panel, 1000);
		choose(h.panel, "Ardent Ocean");
		clickLabel(h.panel, "Bay of Sarim");
		save(dir, "9e-functions-charting-sea", h.panel, 1000);
		choose(h.panel, "Everything"); // every sea and bonus set under their oceans
		save(dir, "9e-functions-charting-everything", h.panel, 1000);

		walkingShots(dir, mid);
		clueShots(dir, mid);
	}

	/** A made-up clue step at the sample destination, for the clue pictures without real data. */
	private static final ClueData SAMPLE_CLUES = new ClueData(List.of(
		new ClueData.Step("cryptic-1", "cryptic", "medium", "coord", new int[]{90001}, new int[]{Tiles.pack(
			FakeRoutes.DESTINATION.getX(), FakeRoutes.DESTINATION.getY(), 0)}, -1, "A sample clue for the preview: dig by the"
			+ " ladder in the Varrock sewers.", "Sample place (not a real clue step).", "", "item 952", "", "dig", -1, "", -1, "", -1)));

	/** The clue states: a clue detected (Use clue on), its route with the header, a beginner clue not read yet. */
	private static void clueShots(File dir, PlayerSnapshot mid) throws IOException
	{
		PreviewHarness h = harness(mid);
		ClueWatcher w = new ClueWatcher(SAMPLE_CLUES);
		h.showClue(w.onInventory(List.of(90001), id -> -1));
		save(dir, "12-clue-detected", h.panel, HEIGHT);
		clickButton(h.panel, "Use clue", 0);
		save(dir, "13-clue-route", h.panel, HEIGHT);
		save(dir, "13-clue-route-full", h.panel, 1500); // the clue step's own card at the end

		h = harness(mid);
		h.showClue(new ClueWatcher(SAMPLE_CLUES).onInventory(List.of(ClueWatcher.BEGINNER_CLUE), id -> -1));
		save(dir, "14-clue-needs-text", h.panel, HEIGHT);
	}

	/**
	 * Real clue steps on the exported data, detected the plugin's way (made-up inventories and a made-up read of a
	 * master scroll), then routed with the plugin's engine from Lumbridge as the Maxed test player.
	 */
	private static void realClueShots(File dir, RouteEngine engine, PlayerSnapshot maxed) throws IOException
	{
		ClueData data = ((CoreRouteEngine) engine).getRouter().getData().getClues();
		if (data.size() == 0)
		{
			System.out.println("  (the export has no clues.tsv: run gradlew -p tool exportPlugin again)");
			return;
		}
		Object[][] cases = {
			{"real-5-clue-emote", ClueDataDiagnostics.byKey(data, "emote-2092").getItems()[0], null},
			{"real-6-clue-kill", ClueDataDiagnostics.byKey(data, "cryptic-1455").getItems()[0], null},
			{"real-7-clue-master-text", ClueWatcher.MASTER_CLUE, "12 degrees 35 minutes north<br>36 degrees 22 minutes east"},
			{"real-8-clue-hotcold", ClueWatcher.MASTER_DEVICE, null}};
		for (Object[] c : cases)
		{
			PreviewHarness h = new PreviewHarness(maxed, engine, true);
			ClueWatcher w = new ClueWatcher(data);
			DetectedClue d = w.onInventory(List.of((Integer) c[1]), id -> -1);
			if (c[2] != null)
			{
				h.showClue(d);
				save(dir, c[0] + "-0-read-me", h.panel, HEIGHT);
				d = w.onClueText((String) c[2]);
			}
			h.showClue(d);
			save(dir, c[0] + "-1-detected", h.panel, HEIGHT);
			long t0 = System.nanoTime();
			clickButton(h.panel, "Use clue", 0);
			long ms = (System.nanoTime() - t0) / 1_000_000;
			report(c[0] + " (" + d + ", " + d.getTargets().length + " places, " + ms + " ms)", h);
			save(dir, c[0] + "-2-route", h.panel, HEIGHT);
		}
		realClueCardShots(dir, engine, maxed, data);
	}

	/**
	 * The clue step's own card (the last card of a clue route) in its states, on real clue steps routed with the
	 * plugin's engine from Lumbridge by made-up players (every quest and unlock, only the items named): an emote
	 * clue whose outfit waits in a filled STASH unit, the same with the outfit in the bank (the route begins at a
	 * bank whose card says to take the outfit out), a dig with the spade carried, one item missing, a key step without
	 * its key, and the outfit in the bank for a player without every unlock (an ordinary bank on the way).
	 */
	private static void realClueCardShots(File dir, RouteEngine engine, PlayerSnapshot maxed, ClueData data) throws IOException
	{
		// "Clap on the causeway to the Wizards' Tower. Equip an iron medium helmet, emerald ring and a white apron."
		ClueData.Step emote = ClueDataDiagnostics.byKey(data, "emote-2098");
		ClueData.Step dig = ClueDataDiagnostics.byKey(data, "coordinate-1923");
		ClueData.Step key = ClueDataDiagnostics.byKey(data, "cryptic-2278");
		if (emote == null || dig == null || key == null || ClueDataDiagnostics.action(emote).isEmpty())
		{
			System.out.println("  (the export has no clue-card columns: run gradlew -p tool exportPlugin again)");
			return;
		}
		int helmet = 1137;
		int apron = 1005;
		int ring = 1639;
		Object[][] cases = {
			{"real-13-clue-card-stash-filled", emote, new com.bestpath.core.req.PlayerState.Builder("Outfit in the STASH unit"),
				new StashState(StashState.Built.YES, StashState.Filled.YES)},
			{"real-14-clue-card-bank", emote, new com.bestpath.core.req.PlayerState.Builder("Outfit in the bank").bank(helmet, 1)
				.bank(apron, 1).bank(ring, 1), new StashState(StashState.Built.YES, StashState.Filled.UNKNOWN)},
			{"real-15-clue-card-dig", dig, new com.bestpath.core.req.PlayerState.Builder("Spade carried").carry(952, 1),
				StashState.UNKNOWN},
			{"real-16-clue-card-missing", emote, new com.bestpath.core.req.PlayerState.Builder("Ring missing")
				.wear(com.bestpath.core.req.EquipSlot.HEAD, helmet).carry(apron, 1), new StashState(StashState.Built.NO,
				StashState.Filled.NO)},
			// a STASH unit built but not known to be filled: the Watson box with "Take me there"
			{"real-16b-clue-card-stash-unknown", emote, new com.bestpath.core.req.PlayerState.Builder("Outfit maybe in the STASH unit"),
				new StashState(StashState.Built.YES, StashState.Filled.UNKNOWN)},
			{"real-17-clue-card-key-missing", key, new com.bestpath.core.req.PlayerState.Builder("No key"), StashState.UNKNOWN},
			// a step with a note (clue-overrides.tsv): the ferocious barbarian spirit
			{"real-18-clue-card-note", ClueDataDiagnostics.byKey(data, "cryptic-1458"), new com.bestpath.core.req.PlayerState.Builder("Note"),
				StashState.UNKNOWN},
			// the outfit only in the bank for a player without "every unlock" (whose unlocks would also open the PvP-world
			// chest beside Lumbridge, varbit 3984): the route begins at an ordinary bank on the way
			{"real-20-clue-bank-first", emote, new com.bestpath.core.req.PlayerState.Builder("Outfit in the bank, no unlocks")
				.bank(helmet, 1).bank(apron, 1).bank(ring, 1), new StashState(StashState.Built.NO, StashState.Filled.NO), false},
			// the marks: the helmet worn (check), the apron in the bank (bank symbol), the ring missing (X);
			// the route begins at a bank whose card's box lists the apron
			{"real-23-clue-card-marks", emote, new com.bestpath.core.req.PlayerState.Builder("Helmet worn, apron banked, no ring")
				.wear(com.bestpath.core.req.EquipSlot.HEAD, helmet).bank(apron, 1), new StashState(StashState.Built.YES,
				StashState.Filled.NO), false}};
		for (Object[] c : cases)
		{
			ClueData.Step step = (ClueData.Step) c[1];
			boolean unlocked = c.length < 5 || (Boolean) c[4];
			com.bestpath.core.req.PlayerState state = ((com.bestpath.core.req.PlayerState.Builder) c[2]).members(true)
				.allUnlocked(unlocked).build();
			PlayerSnapshot who = new PlayerSnapshot(maxed.getX(), maxed.getY(), maxed.getPlane(), state, List.of());
			PreviewHarness h = new PreviewHarness(who, engine, true);
			h.setStash((StashState) c[3]);
			h.showClue(new ClueWatcher(data).onInventory(List.of(step.getItems()[0]), id -> -1));
			clickButton(h.panel, "Use clue", 0);
			report(c[0] + " (" + step.getKey() + ", " + h.clueCard() + ")", h);
			save(dir, (String) c[0], h.panel, HEIGHT);
			save(dir, c[0] + "-full", h.panel, 1500);
		}
	}

	/** A fake player walks the sample route: the current step highlighted, then arrival. */
	/**
	 * The Functions tab's "Nearest" with every place kind of the exported data (three sample
	 * kinds without it), a sheet of every kind with its map icon and number of places (to pick which kinds
	 * get a button), and with the exported data the route to the nearest anvil from Lumbridge.
	 */
	private static void nearestShots(File dir, PlayerSnapshot mid) throws IOException
	{
		RouteEngine engine = RealData.engine();
		PlaceKinds kinds;
		if (engine instanceof CoreRouteEngine)
		{
			com.bestpath.core.data.PluginData pd = ((CoreRouteEngine) engine).getRouter().getData();
			kinds = pd.getPlaces().withBanks(pd.getBanks());
		}
		else
		{
			kinds = PlaceKinds.parse(PlaceKinds.HEADER + "\nAltar\t1467\t3243,3207,0\nBank\t1453\t3208,3220,2\n"
				+ "Furnace\t1457\t3226,3256,0\n");
		}
		PreviewHarness h = harness(mid);
		h.panel.selectFunctionsTab();
		h.panel.openFunctionPage("Nearest");
		h.panel.showPlaces(kinds);
		save(dir, "9e-functions-nearest", h.panel, HEIGHT);
		kindsSheet(new File(dir, "9e-nearest-kinds.png"), kinds);
		if (engine instanceof CoreRouteEngine && kinds.find("Anvil") != null)
		{
			PreviewHarness r = new PreviewHarness(RealData.players().get("Fresh account (test player)"), engine, true);
			r.findRoute(NearestPlaces.request(kinds.find("Anvil")));
			r.panel.selectRouteTab();
			save(dir, "9e-nearest-anvil-route", r.panel, HEIGHT);
		}
	}

	/** Every kind with its map icon (from gradlew previewIcons) and how many places it has, in two columns. */
	private static void kindsSheet(File f, PlaceKinds kinds) throws IOException
	{
		int rows = (kinds.getKinds().size() + 1) / 2;
		int cw = 250;
		int rh = 26;
		BufferedImage img = new BufferedImage(cw * 2, rows * rh + 8, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new java.awt.Color(40, 40, 40));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
		for (int i = 0; i < kinds.getKinds().size(); i++)
		{
			PlaceKinds.Kind k = kinds.getKinds().get(i);
			int x = (i / rows) * cw + 6;
			int y = (i % rows) * rh + 4;
			File icon = new File(PreviewIcons.DIR, "sprite-" + k.getSprite() + ".png");
			if (icon.isFile())
			{
				g.drawImage(ImageIO.read(icon), x, y + 2, 20, 20, null);
			}
			g.setColor(java.awt.Color.WHITE);
			g.drawString(k.getName(), x + 26, y + 17);
			g.setColor(java.awt.Color.LIGHT_GRAY);
			g.drawString(String.valueOf(k.getTiles().length), x + cw - 50, y + 17);
		}
		g.dispose();
		ImageIO.write(img, "png", f);
		System.out.println("  " + f.getName());
	}

	private static void walkingShots(File dir, PlayerSnapshot mid) throws IOException
	{
		PreviewHarness h = harness(mid);
		h.findRoute(FakeRoutes.DESTINATION);
		layout(h.panel, WIDTH, HEIGHT);
		List<Integer> walk = FakeWalk.positions(h.service.getCurrent());
		int i = 0;
		for (; i < walk.size(); i++)
		{
			h.tick(walk.get(i));
			Progress p = h.follower.getProgress();
			if (p.getStep() == 3 && p.getTileIndex() >= 12)
			{
				break; // after the teleport, partway along the walk to the manhole
			}
		}
		save(dir, "10-walking-step-4", h.panel, HEIGHT);
		for (i++; i < walk.size() && h.service.getCurrent().getStatus() == RouteStatus.READY; i++)
		{
			h.tick(walk.get(i));
		}
		save(dir, "11-arrived", h.panel, HEIGHT);
	}

	/** Real routes on the exported map data (the plugin's own engine), from Lumbridge. */
	private static void realShots(File dir) throws IOException
	{
		RouteEngine engine = RealData.engine();
		java.util.Map<String, PlayerSnapshot> players = RealData.players();
		PlayerSnapshot maxed = players.get("Maxed (test player)");
		if (!(engine instanceof CoreRouteEngine))
		{
			PreviewHarness h = new PreviewHarness(maxed, engine, true);
			h.findRoute(RealData.DESTINATION);
			save(dir, "real-0-no-map-data", h.panel, HEIGHT);
			System.out.println("  (no exported map data: run gradlew -p tool exportPlugin first)");
			return;
		}
		PreviewHarness h = new PreviewHarness(maxed, engine, true);
		h.findRoute(RealData.DESTINATION);
		report("real-1-maxed", h);
		save(dir, "real-1-maxed", h.panel, HEIGHT);
		save(dir, "real-1-maxed-full", h.panel, 1400);

		h = new PreviewHarness(players.get("Fresh account (test player)"), engine, true);
		h.findRoute(RealData.DESTINATION);
		report("real-2-fresh", h);
		save(dir, "real-2-fresh", h.panel, HEIGHT);
		save(dir, "real-2-fresh-full", h.panel, 1400);

		// Draynor Village bank for a free-to-play player (free land only)
		h = new PreviewHarness(players.get("Free-to-play, Magic 30"), engine, true);
		h.findRoute(new RouteRequest(3093, 3244, 0));
		report("real-3-f2p", h);
		save(dir, "real-3-f2p", h.panel, HEIGHT);

		// a destination nobody can stand on: the route stops nearby
		int[] blocked = blockedTileNear(PluginRouterDiagnostics.pathfinder(((CoreRouteEngine) engine).getRouter()).getGraph(), 3208, 3220);
		h = new PreviewHarness(maxed, engine, true);
		h.findRoute(new RouteRequest(blocked[0], blocked[1], 0));
		report("real-4-unreachable " + blocked[0] + "," + blocked[1], h);
		save(dir, "real-4-unreachable", h.panel, HEIGHT);

		// the Al Kharid toll gate: its card shows only the way through that applies (coins, or the quest)
		h = new PreviewHarness(players.get(RealData.TOLL_COINS), engine, true);
		h.findRoute(RealData.AL_KHARID_BANK);
		report("real-9-toll-coins", h);
		save(dir, "real-9-toll-coins", h.panel, HEIGHT);
		h = new PreviewHarness(players.get(RealData.TOLL_QUEST), engine, true);
		h.findRoute(RealData.AL_KHARID_BANK);
		report("real-9-toll-quest", h);
		save(dir, "real-9-toll-quest", h.panel, HEIGHT);

		// a route that begins at a bank: the player keeps the runes for Varrock Teleport in the bank
		h = new PreviewHarness(players.get(RealData.BANK_PLAYER), engine, true);
		h.findRoute(RealData.VARROCK_SQUARE);
		report("real-19-bank-first", h);
		save(dir, "real-19-bank-first", h.panel, HEIGHT);
		save(dir, "real-19-bank-first-full", h.panel, 1400);
		// developer details: the where box and the detail lines on every card
		h.panel.setDeveloper(true);
		h.panel.showWhere(java.util.List.of("Game tile: 3222, 3218, 0 (region 12850)", "Map tile: 3222, 3218, 0 (region 12850)",
			"World 301"));
		save(dir, "real-19-bank-first-developer", h.panel, 1400);

		// home and minigame teleports only while ready: from East Ardougne to Draynor Village the
		// mid-level player's route casts Lumbridge Home Teleport (its card says "Ready"); the same player having just
		// used both teleports gets a route without them
		h = new PreviewHarness(new PlayerSnapshot(ARDOUGNE[0], ARDOUGNE[1], 0, players.get(RealData.MID_LEVEL).getState(), List.of()),
			engine, true);
		h.findRoute(DRAYNOR);
		report("real-21-home-teleport-ready", h);
		save(dir, "real-21-home-teleport-ready", h.panel, HEIGHT);
		h = new PreviewHarness(new PlayerSnapshot(ARDOUGNE[0], ARDOUGNE[1], 0, players.get(RealData.COOLDOWNS_USED).getState(), List.of()),
			engine, true);
		h.findRoute(DRAYNOR);
		report("real-22-cooldowns-used", h);
		save(dir, "real-22-cooldowns-used", h.panel, HEIGHT);

		// the marks on the step's own item: from Lumbridge to Edgeville bank with an amulet of glory
		// worn (a check on the teleport card's glory), then with the glory only in the bank (the route begins at a bank
		// whose box lists it; the teleport card's glory has the bank symbol)
		int glory = 11978; // Amulet of glory(6), the group's default picture (drawn by previewIcons)
		Object[][] glories = {
			{"real-24-item-teleport-worn", new com.bestpath.core.req.PlayerState.Builder("Glory worn").members(true)
				.wear(com.bestpath.core.req.EquipSlot.NECK, glory)},
			{"real-25-item-teleport-banked", new com.bestpath.core.req.PlayerState.Builder("Glory in the bank").members(true)
				.bank(glory, 1)}};
		for (Object[] c : glories)
		{
			com.bestpath.core.req.PlayerState st = ((com.bestpath.core.req.PlayerState.Builder) c[1]).build();
			h = new PreviewHarness(new PlayerSnapshot(maxed.getX(), maxed.getY(), maxed.getPlane(), st, List.of()), engine, true);
			h.findRoute(EDGEVILLE_BANK);
			report((String) c[0], h);
			save(dir, (String) c[0], h.panel, HEIGHT);
		}

		// the cards show what the player has on them: a worn staff instead of its runes (Varrock
		// Teleport with a staff of air, then a smoke staff), and the bruma torch for the Lumbridge Swamp dark hole
		Object[][] onPlayer = {
			{"real-45-staff-of-air", RealData.VARROCK_SQUARE, new com.bestpath.core.req.PlayerState.Builder("Staff of air worn")
				.members(true).level(com.bestpath.core.req.Skill.MAGIC, 25)
				.wear(com.bestpath.core.req.EquipSlot.WEAPON, 1381).carry(554, 1).carry(563, 1)},
			{"real-46-smoke-staff", RealData.VARROCK_SQUARE, new com.bestpath.core.req.PlayerState.Builder("Smoke staff worn")
				.members(true).level(com.bestpath.core.req.Skill.MAGIC, 25)
				.wear(com.bestpath.core.req.EquipSlot.WEAPON, 11998).carry(563, 1)},
			{"real-47-bruma-torch", new RouteRequest(3165, 9573, 0), new com.bestpath.core.req.PlayerState.Builder("Bruma torch")
				.members(true).wear(com.bestpath.core.req.EquipSlot.WEAPON, 20720).carry(954, 1)}};
		for (Object[] c : onPlayer)
		{
			com.bestpath.core.req.PlayerState st = ((com.bestpath.core.req.PlayerState.Builder) c[2]).build();
			h = new PreviewHarness(new PlayerSnapshot(maxed.getX(), maxed.getY(), maxed.getPlane(), st, List.of()), engine, true);
			h.findRoute((RouteRequest) c[1]);
			report((String) c[0], h);
			save(dir, (String) c[0], h.panel, HEIGHT);
		}

		realTitleShots(dir, engine);
		realHouseShots(dir, engine);
		realAnswerShots(dir, engine);
		realTransportTickShots(dir, engine);
		realSailingShots(dir, engine, maxed);
		realClueShots(dir, engine, maxed);
		realNoRouteShots(dir, (CoreRouteEngine) engine, players);
		// the Slayer task on the real data: Duradel's abyssal demons, Konar's in the Catacombs (last, and
		// in the harness already made: the pictures run in 256 MB)
		com.bestpath.core.data.PluginData pd = ((CoreRouteEngine) engine).getRouter().getData();
		h.panel.selectFunctionsTab();
		h.panel.openFunctionPage("Slayer task");
		com.bestpath.route.SlayerTask demons = com.bestpath.route.SlayerTask.of(pd.getSlayer(), pd.getWilderness(), 42, 0, 160, 0, 5);
		h.panel.showSlayer(demons.withTimes(com.bestpath.route.SlayerTimes.work(demons, maxed,
			com.bestpath.route.TransportSettings.defaults(), engine::route)));
		save(dir, "real-slayer", h.panel, HEIGHT);
		h.panel.showSlayer(com.bestpath.route.SlayerTask.of(pd.getSlayer(), pd.getWilderness(), 42, 0, 130, 1, 8));
		save(dir, "real-slayer-konar", h.panel, HEIGHT);
	}

	/**
	 * The player-owned house, the
	 * plugin's way: a member with a Rimmington house (varbit value 1) and house tablets, read through SnapshotMapper.
	 * The Settings tab's "House (POH)" section before the house was looked at, then after a scan found an ornate
	 * jewellery box, a portal nexus (Varrock, Camelot), a mounted glory and a spirit tree &amp; fairy ring; then the route
	 * from Lumbridge to Castle Wars (tablet into the house, the jewellery box out) and from inside the house.
	 */
	private static void realHouseShots(File dir, RouteEngine engine) throws IOException
	{
		Set<String> things = new java.util.TreeSet<>(List.of("ornate jewellery box", "portal nexus: Varrock Teleport",
			"portal nexus: Camelot Teleport", "mounted glory", "spirit tree & fairy ring"));
		com.bestpath.player.GameReading r = new com.bestpath.player.GameReading();
		r.x = RealData.START[0];
		r.y = RealData.START[1];
		r.members = true;
		r.inventory.put(8013, 5); // Teleport to house tablets
		r.houseValue = 1; // Rimmington
		PlayerSnapshot before = com.bestpath.player.SnapshotMapper.toSnapshot(r);
		r.houseThings.addAll(things);
		PlayerSnapshot after = com.bestpath.player.SnapshotMapper.toSnapshot(r);
		String place = com.bestpath.player.SnapshotMapper.housePlace(r.houseValue, null);

		PreviewHarness h = new PreviewHarness(before, engine, true);
		h.panel.showHouse(com.bestpath.player.HouseView.of(place, Set.of(), 0));
		PanelDriver.selectSettingsTab(h.panel);
		layout(h.panel, WIDTH, HEIGHT);
		PanelDriver.scrollSettingsToHouse(h.panel);
		save(dir, "real-50-house-settings-not-looked", h.panel, HEIGHT, false);

		h = new PreviewHarness(after, engine, true);
		h.panel.showHouse(com.bestpath.player.HouseView.of(place, things, System.currentTimeMillis()));
		PanelDriver.selectSettingsTab(h.panel);
		layout(h.panel, WIDTH, HEIGHT);
		PanelDriver.scrollSettingsToHouse(h.panel);
		save(dir, "real-51-house-settings-scanned", h.panel, HEIGHT, false);
		h.panel.selectRouteTab();
		h.findRoute(new RouteRequest(2440, 3090, 0)); // Castle Wars
		report("real-52-house-jewellery-box", h);
		save(dir, "real-52-house-jewellery-box", h.panel, HEIGHT);

		// standing inside the house (a house room tile): the route starts at the house spot, the box is the first step
		int[] in = com.bestpath.player.HouseFurniture.routeTile(1864, 7070, 0);
		h = new PreviewHarness(new PlayerSnapshot(in[0], in[1], in[2], after.getState(), List.of()), engine, true);
		h.findRoute(new RouteRequest(3213, 3424, 0)); // Varrock square: the nexus's Varrock portal
		report("real-53-house-inside-nexus", h);
		save(dir, "real-53-house-inside-nexus", h.panel, HEIGHT);

		// a past bug: on a boat at Port Sarim, the Construction cape worn, a spirit tree in the house,
		// a house tablet only in the bank, to a tile beside the Brimhaven spirit tree no one can stand on. Was 72 ticks
		// through the Farming Guild bank (take out the tablet); now the cape, the house's spirit tree, Brimhaven: 7 ticks
		com.bestpath.player.GameReading o = new com.bestpath.player.GameReading();
		o.members = true;
		o.worn[com.bestpath.core.req.EquipSlot.CAPE.ordinal()] = 9790;
		o.bank = new java.util.HashMap<>();
		o.bank.put(8013, 5);
		o.houseValue = 1;
		o.houseThings.add("spirit tree");
		o.quests.put("Tree Gnome Village", com.bestpath.core.req.QuestState.DONE);
		o.spiritTrees.add("Brimhaven");
		o.spiritTrees.add("Farming Guild");
		o.levels[com.bestpath.core.req.Skill.FARMING.ordinal()] = 85;
		o.levels[com.bestpath.core.req.Skill.SAILING.ordinal()] = 99;
		List<com.bestpath.core.sea.Boat> aboard = List.of(new com.bestpath.core.sea.Boat(1, com.bestpath.core.sea.BoatType.SLOOP, 2, 1, 1, 1,
			false, false, com.bestpath.core.sea.Boat.Focus.NONE, com.bestpath.core.sea.Boat.AT_START));
		h = new PreviewHarness(new PlayerSnapshot(3048, 3186, 0, com.bestpath.player.SnapshotMapper.toSnapshot(o).getState(), aboard), engine,
			true);
		h.findRoute(new RouteRequest(2799, 3203, 0));
		report("real-54-house-not-via-a-bank", h);
		save(dir, "real-54-house-not-via-a-bank", h.panel, HEIGHT);
	}

	/**
	 * Card titles in the game's menu words (e.g. "construction cape -> rimmington"): an item teleport (the
	 * construction cape worn, Lumbridge to Rimmington), a spirit tree ride (beside the Grand Exchange tree to the
	 * Battlefield of Khazard) and a fairy ring ride (Mudskipper Point to the Ardougne Zoo ring).
	 */
	private static void realTitleShots(File dir, RouteEngine engine) throws IOException
	{
		com.bestpath.core.req.PlayerState cape = new com.bestpath.core.req.PlayerState.Builder("Construction cape worn").members(true)
			.wear(com.bestpath.core.req.EquipSlot.CAPE, 9789).build();
		com.bestpath.core.req.PlayerState trees = new com.bestpath.core.req.PlayerState.Builder("Tree Gnome Village done")
			.members(true).quest("Tree Gnome Village", com.bestpath.core.req.QuestState.DONE).build();
		com.bestpath.core.req.PlayerState rings = new com.bestpath.core.req.PlayerState.Builder("Fairy rings, dramen staff")
			.members(true).quest("Fairytale I - Growing Pains", com.bestpath.core.req.QuestState.DONE)
			.quest("Fairytale II - Cure a Queen", com.bestpath.core.req.QuestState.DONE)
			.wear(com.bestpath.core.req.EquipSlot.WEAPON, 772).build();
		Object[][] cases = {
			{"real-34-title-item-teleport", new PlayerSnapshot(RealData.START[0], RealData.START[1], 0, cape, List.of()), new RouteRequest(2954, 3222, 0)},
			{"real-35-title-spirit-tree", new PlayerSnapshot(3186, 3506, 0, trees, List.of()), new RouteRequest(2557, 3257, 0)},
			{"real-36-title-fairy-ring", new PlayerSnapshot(2997, 3116, 0, rings, List.of()), new RouteRequest(2636, 3268, 0)},
			// a diary cape record named after the diary's master (Two-pints) says the cape's option
			{"real-37-title-diary-cape", new PlayerSnapshot(RealData.START[0], RealData.START[1], 0,
				new com.bestpath.core.req.PlayerState.Builder("Diary cape worn").members(true)
					.wear(com.bestpath.core.req.EquipSlot.CAPE, 19476).build(), List.of()), new RouteRequest(2574, 3323, 0)}};
		for (Object[] c : cases)
		{
			PreviewHarness h = new PreviewHarness((PlayerSnapshot) c[1], engine, true);
			h.findRoute((RouteRequest) c[2]);
			report((String) c[0], h);
			save(dir, (String) c[0], h.panel, HEIGHT);
		}

		// "Turned off in Settings: <type>" on a "no route" card has the type's tick box: a fresh account
		// to the Varrock sewers with stairs and ladders switched off, then after ticking it
		PreviewHarness h = new PreviewHarness(RealData.players().get("Fresh account (test player)"), engine, true);
		useSettings(h, com.bestpath.route.TransportSettings.defaults()
			.withCategory(com.bestpath.route.TransportSettings.Category.STAIRS_LADDERS, false));
		h.findRoute(RealData.DESTINATION);
		reportWhy("real-38-no-route-turn-on", h);
		save(dir, "real-38-no-route-turn-on", h.panel, HEIGHT);
		clickToggle(h.panel, 0);
		report("real-39-after-turn-on", h);
		save(dir, "real-39-after-turn-on", h.panel, HEIGHT);
	}

	/**
	 * "No route" explains itself, one picture per kind of reason: members land on a free world,
	 * nowhere to stand, open sea without a boat, a way needing things the player can't use, a transport the player
	 * chose never to use (with Restore), no known way, and a way by boat.
	 */
	private static void realNoRouteShots(File dir, CoreRouteEngine engine, java.util.Map<String, PlayerSnapshot> players)
		throws IOException
	{
		PlayerSnapshot maxed = players.get("Maxed (test player)");
		PlayerSnapshot fresh = players.get("Fresh account (test player)");
		PlayerSnapshot free = players.get("Free-to-play, Magic 30");
		Object[][] cases = {
			// East Ardougne market: members land
			{"real-26-no-route-members", free, new RouteRequest(2662, 3305, 0)},
			// far off the map: nothing to stand on
			{"real-27-no-route-nowhere", maxed, new RouteRequest(2000, 2000, 0)},
			// Poison Waste behind fairy ring DLR (core's slowest explanation): things a fresh account can't use
			{"real-29-no-route-blocked", fresh, new RouteRequest(2212, 3099, 0)},
			// a place with no way in in the map data
			{"real-31-no-route-no-known-way", maxed, new RouteRequest(2761, 5139, 0)}};
		for (Object[] c : cases)
		{
			PreviewHarness h = new PreviewHarness((PlayerSnapshot) c[1], engine, true);
			h.findRoute((RouteRequest) c[2]);
			reportWhy((String) c[0], h);
			save(dir, (String) c[0], h.panel, HEIGHT);
		}
		// "Skip" on the first card of real-29: no way without fairy ring DLR, so its card says "skipped"
		PreviewHarness skip = new PreviewHarness(fresh, engine, true);
		skip.findRoute(new RouteRequest(2212, 3099, 0));
		clickButton(skip.panel, "Skip", 0);
		reportWhy("real-44-no-route-skip", skip);
		save(dir, "real-44-no-route-skip", skip.panel, HEIGHT);
		// the Shaman Caves case: two ways needing one thing each, "or" between their cards
		com.bestpath.core.req.PlayerState shamanState = new com.bestpath.core.req.PlayerState.Builder("Legends' Quest done")
			.members(true).quest("Legends' Quest", com.bestpath.core.req.QuestState.DONE).varp(139, 180)
			.level(com.bestpath.core.req.Skill.AGILITY, 70).level(com.bestpath.core.req.Skill.MAGIC, 80)
			.level(com.bestpath.core.req.Skill.THIEVING, 70).level(com.bestpath.core.req.Skill.STRENGTH, 70)
			.level(com.bestpath.core.req.Skill.MINING, 60).carry(564, 3).carry(555, 30).carry(1265, 1).carry(1523, 1).build();
		PreviewHarness ways = new PreviewHarness(new PlayerSnapshot(2781, 9337, 0, shamanState, java.util.List.of()), engine, true);
		ways.findRoute(new RouteRequest(2420, 4694, 0));
		reportWhy("real-43-no-route-two-ways", ways);
		save(dir, "real-43-no-route-two-ways", ways.panel, HEIGHT);
		// "Skip" on the first of them: the ways without it
		clickButton(ways.panel, "Skip", 0);
		reportWhy("real-45-no-route-two-ways-skip", ways);
		save(dir, "real-45-no-route-two-ways-skip", ways.panel, HEIGHT);
		// a card that ends in the Wilderness has the game's skull, "WILDERNESS!" and a red outline
		PreviewHarness wild = new PreviewHarness(new PlayerSnapshot(3093, 3475, 0,
			new com.bestpath.core.req.PlayerState.Builder("Member").members(true).build(), java.util.List.of()), engine, true);
		wild.findRoute(new RouteRequest(3154, 3923, 0));
		save(dir, "real-46-wilderness", wild.panel, HEIGHT);
		// walking over the Wilderness Ditch has a "Go into the Wilderness" card
		PreviewHarness ditch = new PreviewHarness(new PlayerSnapshot(3094, 3505, 0,
			new com.bestpath.core.req.PlayerState.Builder("Member").members(true).build(), java.util.List.of()), engine, true);
		ditch.findRoute(new RouteRequest(3094, 3540, 0));
		save(dir, "real-47-wilderness-ditch", ditch.panel, HEIGHT);

		// a fresh account to the Varrock sewers, pressing "Never use" on the route's first card until there is no route:
		// the way needs a transport the player chose never to use (with Restore)
		PreviewHarness h = new PreviewHarness(fresh, engine, true);
		h.findRoute(RealData.DESTINATION);
		for (int i = 0; i < 6 && h.service.getCurrent().getStatus() == RouteStatus.READY; i++)
		{
			clickButton(h.panel, "Never use", 0);
		}
		reportWhy("real-30-no-route-never-use", h);
		save(dir, "real-30-no-route-never-use", h.panel, HEIGHT);

		// the members search again (the free-world pictures let it go), with its sea routes
		PluginRouterDiagnostics.route(engine.getRouter(), maxed.getState(), maxed.getX(), maxed.getY(), maxed.getPlane(), 3210, 3220, 0, id -> false);
		com.bestpath.core.path.Sailing sailing = engine.getRouter().getSailing();
		if (sailing != null)
		{
			// open sea with nothing to stand on within 10 tiles, west of Port Sarim; no boat
			com.bestpath.core.graph.CoarseGraph g = PluginRouterDiagnostics.pathfinder(engine.getRouter()).getGraph();
			int[] sea = null;
			for (int x = 2950; x > 2700 && sea == null; x -= 5)
			{
				if (sailing.atSea(x, 3200, 0) && nothingWithin(g, x, 3200, 12))
				{
					sea = new int[]{x, 3200};
				}
			}
			if (sea != null)
			{
				h = new PreviewHarness(maxed, engine, true);
				h.findRoute(new RouteRequest(sea[0], sea[1], 0));
				reportWhy("real-28-no-route-sea " + sea[0] + "," + sea[1], h);
				save(dir, "real-28-no-route-sea", h.panel, HEIGHT);
			}
		}

		// a way by boat: the first dock whose island has no way on land for the Maxed player without a boat
		if (sailing == null)
		{
			System.out.println("  (no sea data: no by-boat picture)");
			return;
		}
		int tried = 0;
		for (com.bestpath.core.sea.Dock d : sailing.getDocks())
		{
			int[] t = d.getLandTiles().get(0);
			if (tried >= 12)
			{
				break;
			}
			if (PluginRouterDiagnostics.route(engine.getRouter(), maxed.getState(), maxed.getX(), maxed.getY(), maxed.getPlane(), t[0], t[1], t[2], id -> false)
				.getRoute().isFound())
			{
				continue;
			}
			tried++;
			h = new PreviewHarness(maxed, engine, true);
			h.findRoute(new RouteRequest(t[0], t[1], t[2]));
			RouteResult r = h.service.getCurrent();
			if (r.getNoRouteWhy() != null && com.bestpath.route.NoRouteWhyTestAccess.kind(r.getNoRouteWhy()) == com.bestpath.core.data.NoRouteReasons.Kind.BY_BOAT)
			{
				reportWhy("real-32-no-route-by-boat (" + d.getName() + ")", h);
				save(dir, "real-32-no-route-by-boat", h.panel, HEIGHT);
				// a low Sailing level with a boat: the docks' levels and quests
				com.bestpath.core.req.PlayerState low = new com.bestpath.core.req.PlayerState.Builder("Sailing 1 with a raft")
					.members(true).build();
				h = new PreviewHarness(new PlayerSnapshot(maxed.getX(), maxed.getY(), maxed.getPlane(), low,
					List.of(new com.bestpath.core.sea.Boat(1, com.bestpath.core.sea.BoatType.RAFT, 0, 0, 0, 0, false, false,
						com.bestpath.core.sea.Boat.Focus.NONE, 0))), engine, true);
				h.findRoute(new RouteRequest(t[0], t[1], t[2]));
				reportWhy("real-33-no-route-by-boat-docks (" + d.getName() + ")", h);
				save(dir, "real-33-no-route-by-boat-docks", h.panel, HEIGHT);
				return;
			}
		}
		System.out.println("  (no dock found reachable only by boat in " + tried + " tries)");
	}

	/** True when nothing within {@code r} tiles of (x, y), plane 0, can be walked on. */
	private static boolean nothingWithin(com.bestpath.core.graph.CoarseGraph g, int x0, int y0, int r)
	{
		for (int x = x0 - r; x <= x0 + r; x++)
		{
			for (int y = y0 - r; y <= y0 + r; y++)
			{
				if (g.componentAt(x, y, 0) >= 0)
				{
					return false;
				}
			}
		}
		return true;
	}

	private static void reportWhy(String name, PreviewHarness h)
	{
		RouteResult r = h.service.getCurrent();
		report(name, h);
		System.out.printf("    %d ms: %s%n", r.getSearchMillis(), r.getNoRouteWhy());
	}

	/**
	 * "Transport ticks": three tick changes (the newest first), a search for "glory" by fewest ticks,
	 * and the ladders nearest to a player in Lumbridge castle.
	 */
	private static void realTransportTickShots(File dir, RouteEngine engine) throws IOException
	{
		PreviewHarness h = new PreviewHarness(new PlayerSnapshot(3208, 3210, 0, com.bestpath.core.req.PlayerStateBuilder.maxed(), List.of()), engine, true);
		java.util.List<com.bestpath.route.TransportFinder.Entry> all = com.bestpath.route.TransportFinder.of(
			((com.bestpath.route.CoreRouteEngine) engine).getRouter().getData().getTransports(),
			((com.bestpath.route.CoreRouteEngine) engine).getRouter().getData().getTables(), com.bestpath.core.data.TickGroups.EMPTY, WildernessZones.NONE)
			.getEntries();
		com.bestpath.route.TransportSettings s = PanelDriver.settings(h.panel);
		long t = 1_000L;
		for (String want : new String[]{"Rocks", "Teleport: Varrock Teleport", "Ladder"})
		{
			for (com.bestpath.route.TransportFinder.Entry e : all)
			{
				if (e.getTitle().contains(want.replace("Teleport: ", "")))
				{
					s = s.withTickChange(e.getId(), want.equals("Rocks") ? 4 : -2, e.getTitle(), t++);
					break;
				}
			}
		}
		h.panel.showSettings(s);
		PanelDriver.selectSettingsTab(h.panel);
		layout(h.panel, WIDTH, HEIGHT);
		PanelDriver.previewTransportTicks(h.panel, "", com.bestpath.route.TransportFinder.Show.ALL,
			com.bestpath.route.TransportFinder.Sort.RECENT);
		layout(h.panel, WIDTH, HEIGHT);
		PanelDriver.scrollSettingsToTransportTicks(h.panel);
		save(dir, "real-70-transport-ticks-recent", h.panel, HEIGHT, false);
		layout(h.panel, WIDTH, HEIGHT);
		PanelDriver.previewTransportTicks(h.panel, "glory", com.bestpath.route.TransportFinder.Show.ALL,
			com.bestpath.route.TransportFinder.Sort.FEWEST_TICKS);
		layout(h.panel, WIDTH, HEIGHT);
		PanelDriver.scrollSettingsToTransportTicks(h.panel);
		save(dir, "real-71-transport-ticks-glory", h.panel, HEIGHT, false);
		layout(h.panel, WIDTH, HEIGHT);
		PanelDriver.previewTransportTicks(h.panel, "ladder", com.bestpath.route.TransportFinder.Show.ALL,
			com.bestpath.route.TransportFinder.Sort.NEAREST);
		layout(h.panel, WIDTH, HEIGHT);
		PanelDriver.scrollSettingsToTransportTicks(h.panel);
		save(dir, "real-72-transport-ticks-nearest-ladders", h.panel, HEIGHT, false);
	}

	/** Routes that may ask a question: a member with Woodcutting 57 and no axe (canoes), then Fossil Island's mushtrees. */
	private static final int[][][] QUESTION_ROUTES = {
		{{3241, 3238, 0}, {3131, 3508, 0}}, // Lumbridge canoe station -> Edgeville canoe station
		{{3764, 3879, 1}, {3757, 3756, 0}}, // House on the Hill -> Verdant Valley (magic mushtrees)
	};

	/**
	 * Player answers asked on the step card: the first of {@link #QUESTION_ROUTES} whose route asks a
	 * question, for a member with no answers given (the plugin's player); then Yes to the first question (the route is
	 * searched again); then the Settings tab's "Your answers" (unfolded) with that Yes and a No. Spirit trees no longer
	 * ask (detected); skipped with a note when no route asks.
	 */
	private static void realAnswerShots(File dir, RouteEngine engine) throws IOException
	{
		com.bestpath.core.req.PlayerState st = new com.bestpath.core.req.PlayerState.Builder("Woodcutting 57, Bone Voyage")
			.members(true).askUnanswered(true).level(com.bestpath.core.req.Skill.WOODCUTTING, 57)
			.quest("Bone Voyage", com.bestpath.core.req.QuestState.DONE).build();
		for (int[][] route : QUESTION_ROUTES)
		{
			PreviewHarness h = new PreviewHarness(new PlayerSnapshot(route[0][0], route[0][1], route[0][2], st, List.of()), engine, true);
			h.findRoute(new RouteRequest(route[1][0], route[1][1], route[1][2]));
			layout(h.panel, WIDTH, HEIGHT);
			if (find(h.panel, "Yes", 0, new int[]{0}) == null)
			{
				continue;
			}
			report("real-26-answer-question", h);
			save(dir, "real-26-answer-question", h.panel, HEIGHT);
			clickButton(h.panel, "Yes", 0);
			report("real-27-answer-after-yes", h);
			save(dir, "real-27-answer-after-yes", h.panel, HEIGHT);
			h.answer("Respawn point set to Prifddinas", false);
			PanelDriver.selectSettingsTab(h.panel);
			PanelDriver.unfoldAnswers(h.panel);
			save(dir, "real-28-answers-settings-full", h.panel, 1250 + CONFIG_PART);
			return;
		}
		System.out.println("  (real-26..28 skipped: none of the question routes asks a question)");
	}

	/**
	 * A route that sails, as the Maxed player with the preview's test sloop moored at Port Sarim, read the plugin's way:
	 * made-up boat varbit values through the real export's boat table (boats.tsv). From Lumbridge to the first of a
	 * few island docks the route sails to (with every teleport, some islands are quicker without the boat).
	 */
	private static void realSailingShots(File dir, RouteEngine engine, PlayerSnapshot maxed) throws IOException
	{
		com.bestpath.core.data.PluginData data = ((CoreRouteEngine) engine).getRouter().getData();
		if (data.getBoats().isEmpty() || data.getSea() == null)
		{
			System.out.println("  (the export has no boats.tsv or sea.bin: run gradlew -p tool exportPlugin again)");
			return;
		}
		com.bestpath.player.PlayerBoats sloop = FakeBoats.testSloop(data.getBoats());
		com.bestpath.core.sea.Boat b = sloop.getBoats().get(0);
		com.bestpath.core.sea.Boat want = RealData.TEST_BOATS.get(0);
		if (b.getType() != want.getType() || b.getHull() != want.getHull() || b.getSail() != want.getSail()
			|| b.getDockId() != want.getDockId())
		{
			throw new IllegalStateException("the test sloop does not read as RealData.TEST_BOATS: " + b.describe());
		}
		PlayerSnapshot me = new PlayerSnapshot(maxed.getX(), maxed.getY(), maxed.getPlane(), maxed.getState(), sloop.getBoats());
		PreviewHarness h = null;
		String name = null;
		RouteRequest chosen = null;
		// Cairn Isle, Remote Island, Vatrachos Island, Isle of Bones, Wintumber Island, Chinchompa Island, The Pandemonium
		for (int id : new int[]{15, 36, 41, 44, 46, 52, 1})
		{
			for (com.bestpath.core.sea.Dock d : data.getSea().getDocks())
			{
				if (d.getId() != id || h != null && sails(h))
				{
					continue;
				}
				int[] to = d.getLandTiles().get(0);
				h = new PreviewHarness(me, engine, true);
				h.panel.showBoats(sloop);
				chosen = new RouteRequest(to[0], to[1], to[2]);
				h.findRoute(chosen);
				name = d.getName() + " " + to[0] + "," + to[1] + "," + to[2];
				report("real-10-sailing to " + name + (sails(h) ? "" : " (does not sail)"), h);
			}
		}
		save(dir, "real-10-sailing", h.panel, HEIGHT);
		save(dir, "real-10-sailing-full", h.panel, 1400);
		PanelDriver.selectSettingsTab(h.panel);
		save(dir, "real-10-sailing-settings-full", h.panel, 1250 + CONFIG_PART);

		// the same sloop capsized (a shipwright brings it), and bottled with its bottle carried
		com.bestpath.player.PlayerBoats capsized = FakeBoats.testSloop(data.getBoats(), 254);
		h = new PreviewHarness(new PlayerSnapshot(me.getX(), me.getY(), me.getPlane(), me.getState(), capsized.getBoats()), engine, true);
		h.panel.showBoats(capsized);
		h.findRoute(chosen);
		report("real-11-sailing-capsized to " + name + (sails(h) ? "" : " (does not sail)"), h);
		save(dir, "real-11-sailing-capsized", h.panel, HEIGHT);
		save(dir, "real-11-sailing-capsized-full", h.panel, 1400);
		// the same with "A shipwright may bring my boat" switched off: the "no route" card has that
		// tick box; then ticked on the card (the route through the shipwright again); then the moored sloop switched off
		h = new PreviewHarness(new PlayerSnapshot(me.getX(), me.getY(), me.getPlane(), me.getState(), capsized.getBoats()), engine, true);
		h.panel.showBoats(capsized);
		useSettings(h, com.bestpath.route.TransportSettings.defaults().withShipwrights(false));
		h.findRoute(chosen);
		reportWhy("real-40-no-route-shipwrights-off to " + name, h);
		save(dir, "real-40-no-route-shipwrights-off", h.panel, HEIGHT);
		if (h.service.getCurrent().getNoRouteWhy() != null && !h.service.getCurrent().getNoRouteWhy().getWays().isEmpty())
		{
			clickToggle(h.panel, 0);
			report("real-41-after-shipwright-tick", h);
			save(dir, "real-41-after-shipwright-tick", h.panel, HEIGHT);
		}
		h = new PreviewHarness(me, engine, true);
		h.panel.showBoats(sloop);
		useSettings(h, com.bestpath.route.TransportSettings.defaults().withBoatAllowed(b.getSlot(), false));
		h.findRoute(chosen);
		reportWhy("real-42-no-route-boat-off to " + name, h);
		save(dir, "real-42-no-route-boat-off", h.panel, HEIGHT);
		com.bestpath.player.PlayerBoats bottled = FakeBoats.testSloop(data.getBoats(), 255);
		h = new PreviewHarness(new PlayerSnapshot(me.getX(), me.getY(), me.getPlane(), withBottle(me.getState()), bottled.getBoats()),
			engine, true);
		h.panel.showBoats(bottled);
		h.findRoute(chosen);
		report("real-12-sailing-bottled to " + name + (sails(h) ? "" : " (does not sail)"), h);
		save(dir, "real-12-sailing-bottled", h.panel, HEIGHT);
		save(dir, "real-12-sailing-bottled-full", h.panel, 1400);
		PanelDriver.selectSettingsTab(h.panel);
		save(dir, "real-12-sailing-bottled-settings-full", h.panel, 1250 + CONFIG_PART);

		// on the sloop at sea south of Port Sarim, a route that teleports away first: the
		// boat card ("Park my boat", "Ocean only path"), then each of them pressed
		List<com.bestpath.core.sea.Boat> aboard = List.of(new com.bestpath.core.sea.Boat(1, com.bestpath.core.sea.BoatType.SLOOP, 2, 1, 1,
			1, false, false, com.bestpath.core.sea.Boat.Focus.NONE, com.bestpath.core.sea.Boat.AT_START));
		PlayerSnapshot atSea = new PlayerSnapshot(3048, 3186, 0, maxed.getState(), aboard);
		for (RouteRequest to : new RouteRequest[]{new RouteRequest(2851, 3430, 0), new RouteRequest(2805, 3434, 0),
			new RouteRequest(3093, 3243, 0)})
		{
			h = new PreviewHarness(atSea, engine, true);
			h.findRoute(to);
			com.bestpath.route.BoatCard card = h.service.getCurrent().getBoatCard();
			report("real-60-boat-card to " + to.label() + (card == null ? " (no boat card)" : " (" + card.getMode()
				+ (card.isOceanOnlyNear() ? ", ocean only possible" : ", ocean only greyed out") + ")"), h);
			if (card != null)
			{
				save(dir, "real-60-boat-card", h.panel, HEIGHT);
				h.boatMode(com.bestpath.route.BoatMode.PARK);
				report("real-61-boat-park", h);
				save(dir, "real-61-boat-park", h.panel, HEIGHT);
				h.boatMode(com.bestpath.route.BoatMode.OCEAN_ONLY);
				report("real-62-boat-ocean-only", h);
				save(dir, "real-62-boat-ocean-only", h.panel, HEIGHT);
				break;
			}
		}
	}

	/** The same player with one full boat bottle in the inventory (a bottled boat can then be used). */
	private static com.bestpath.core.req.PlayerState withBottle(com.bestpath.core.req.PlayerState p)
	{
		com.bestpath.core.req.PlayerState.Builder b = com.bestpath.core.req.PlayerStateBuilder.allItems(
			new com.bestpath.core.req.PlayerState.Builder(PlayerStateDiagnostics.name(p) + " + boat bottle").members(p.isMembers()).allUnlocked(p.isAllUnlocked()),
			p.isAllItems());
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
		p.getAnswerMap().forEach(b::answer);
		b.askUnanswered(p.isAskUnanswered());
		p.getHouseThings().forEach(b::houseThing);
		for (com.bestpath.core.req.EquipSlot s : com.bestpath.core.req.EquipSlot.values())
		{
			if (p.worn(s) > 0)
			{
				b.wear(s, p.worn(s));
			}
		}
		return b.house(p.getHouse()).spellbook(p.getSpellbook()).pouchInBank(p.isPouchInBank())
			.carry(com.bestpath.core.sea.Boat.BOTTLE_FULL, 1).build();
	}

	private static boolean sails(PreviewHarness h)
	{
		for (com.bestpath.route.RouteStep s : h.service.getCurrent().getSteps())
		{
			if (s.getKind() == com.bestpath.route.RouteStep.Kind.SAIL)
			{
				return true;
			}
		}
		return false;
	}

	private static void report(String name, PreviewHarness h)
	{
		RouteResult r = h.service.getCurrent();
		System.out.printf("  %s: %s, %d ticks, %d steps %s%n", name, r.getStatus(), r.getTotalTicks(), r.getSteps().size(),
			r.getMessage());
		// the cards' titles (the game's menu words where known)
		java.util.List<String> titles = new java.util.ArrayList<>();
		for (com.bestpath.route.RouteStep s : r.getSteps())
		{
			if (s.getKind() != com.bestpath.route.RouteStep.Kind.WALK)
			{
				titles.add(s.getTitle());
			}
		}
		if (r.getNoRouteWhy() != null)
		{
			for (List<com.bestpath.route.NoRouteWhy.Blocker> way : r.getNoRouteWhy().getWays())
			{
				for (com.bestpath.route.NoRouteWhy.Blocker b : way)
				{
					titles.add("[no route] " + b.getStep().getTitle());
				}
			}
		}
		if (!titles.isEmpty())
		{
			System.out.println("    cards: " + String.join(" | ", titles));
		}
	}

	/** The first tile no one can walk on (no sub-region) at or around (x, y), plane 0, whose neighbour is walkable. */
	private static int[] blockedTileNear(com.bestpath.core.graph.CoarseGraph g, int x0, int y0)
	{
		for (int r = 0; r < 30; r++)
		{
			for (int x = x0 - r; x <= x0 + r; x++)
			{
				for (int y = y0 - r; y <= y0 + r; y++)
				{
					if (g.componentAt(x, y, 0) < 0 && (g.componentAt(x + 1, y, 0) >= 0 || g.componentAt(x - 1, y, 0) >= 0))
					{
						return new int[]{x, y};
					}
				}
			}
		}
		return new int[]{x0, y0};
	}

	private static PreviewHarness harness(PlayerSnapshot p)
	{
		return new PreviewHarness(p, 0, true);
	}

	/** Clicks the n-th (0-based) visible button with this text. */
	/** Clicks the n-th total above - / + (as the player does) and returns the box that opens to type it. */
	private static javax.swing.JTextField openTypingBox(Component root, int n)
	{
		layout(root, WIDTH, HEIGHT);
		List<Container> steppers = new ArrayList<>();
		collectSteppers(root, steppers);
		Container stepper = steppers.get(n);
		javax.swing.JLabel total = first(stepper, javax.swing.JLabel.class);
		total.dispatchEvent(new java.awt.event.MouseEvent(total, java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
			2, 2, 1, false));
		layout(root, WIDTH, HEIGHT);
		return first(stepper, javax.swing.JTextField.class);
	}

	private static void collectSteppers(Component c, List<Container> out)
	{
		if (c.getClass().getSimpleName().equals("TickStepper") && c.isVisible())
		{
			out.add((Container) c);
			return;
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				collectSteppers(k, out);
			}
		}
	}

	private static <T> T first(Component c, Class<T> type)
	{
		if (type.isInstance(c))
		{
			return type.cast(c);
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				T t = first(k, type);
				if (t != null)
				{
					return t;
				}
			}
		}
		return null;
	}

	private static void clickButton(Component root, String text, int n)
	{
		layout(root, WIDTH, HEIGHT);
		int[] seen = {0};
		AbstractButton b = find(root, text, n, seen);
		if (b == null)
		{
			throw new IllegalStateException("No button '" + text + "' #" + n);
		}
		b.doClick(0);
	}

	/** The player's settings, in the Settings tab too (as the plugin loads them; the cards' tick boxes read them there). */
	private static void useSettings(PreviewHarness h, TransportSettings s)
	{
		h.panel.showSettings(s);
		h.settingsChanged(s);
	}

	/** Clicks the n-th Settings tick box on the "no route" cards (BlockedCard.TOGGLE_NAME). */
	private static void clickToggle(Component root, int n)
	{
		layout(root, WIDTH, HEIGHT);
		List<AbstractButton> boxes = new java.util.ArrayList<>();
		named(root, "blocked-toggle", boxes);
		if (boxes.size() <= n)
		{
			throw new IllegalStateException("No tick box #" + n + " on the no-route cards");
		}
		boxes.get(n).doClick(0);
	}

	private static void named(Component c, String name, List<AbstractButton> out)
	{
		if (c instanceof AbstractButton && name.equals(c.getName()) && c.isVisible())
		{
			out.add((AbstractButton) c);
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				named(k, name, out);
			}
		}
	}

	private static AbstractButton find(Component c, String text, int n, int[] seen)
	{
		if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText()) && c.isVisible())
		{
			if (seen[0]++ == n)
			{
				return (AbstractButton) c;
			}
		}
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				AbstractButton b = find(k, text, n, seen);
				if (b != null)
				{
					return b;
				}
			}
		}
		return null;
	}

	static void layout(Component c, int w, int h)
	{
		c.setSize(w, h);
		invalidateTree(c); // no window: revalidate() can't reach the layouts' cached sizes, so clear them here
		layoutTree(c);
		layoutTree(c); // twice: wrapped text heights settle after the first pass
	}

	private static void invalidateTree(Component c)
	{
		c.invalidate();
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				invalidateTree(child);
			}
		}
	}

	private static void layoutTree(Component c)
	{
		if (c instanceof Container)
		{
			Container k = (Container) c;
			k.doLayout();
			for (Component child : k.getComponents())
			{
				layoutTree(child);
			}
		}
	}

	/** The whole Settings tab with "Teleport and travel ticks" (a long list at its end). */
	/** The config panel's settings at the top of the Settings tab make each Settings picture this much taller. */
	static final int CONFIG_PART = 750;
	static final int SETTINGS_FULL = 2250 + CONFIG_PART;

	/** The n-th (0-based) "Teleport and travel ticks" row of the sample data: its - / + is the n-th on the tab. */
	private static int groupIndex(String name)
	{
		List<com.bestpath.core.data.TickGroups.Group> g = FakeRoutes.tickGroups().getGroups();
		for (int i = 0; i < g.size(); i++)
		{
			if (g.get(i).getName().equals(name))
			{
				return i;
			}
		}
		throw new IllegalStateException("no teleport category or network " + name);
	}

	/** Picks this item in the first drop-down list that has it, as the player would. */
	private static boolean choose(java.awt.Container c, String item)
	{
		for (java.awt.Component k : c.getComponents())
		{
			if (k instanceof javax.swing.JComboBox)
			{
				javax.swing.JComboBox<?> box = (javax.swing.JComboBox<?>) k;
				for (int i = 0; i < box.getItemCount(); i++)
				{
					if (item.equals(String.valueOf(box.getItemAt(i))))
					{
						box.setSelectedIndex(i);
						return true;
					}
				}
			}
			if (k instanceof java.awt.Container && choose((java.awt.Container) k, item))
			{
				return true;
			}
		}
		return false;
	}

	/** Clicks the row holding the label with this text, as the player would (a fold heading's row listens). */
	private static boolean clickLabel(java.awt.Container c, String text)
	{
		for (java.awt.Component k : c.getComponents())
		{
			if (k instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) k).getText()))
			{
				java.awt.Component row = k.getParent();
				for (java.awt.event.MouseListener l : row.getMouseListeners())
				{
					l.mouseClicked(new java.awt.event.MouseEvent(row, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
				}
				return true;
			}
			if (k instanceof java.awt.Container && clickLabel((java.awt.Container) k, text))
			{
				return true;
			}
		}
		return false;
	}

	private static void save(File dir, String name, BestPathPanel panel, int height) throws IOException
	{
		save(dir, name, panel, height, true);
	}

	/** {@code relayout} false: as laid out (and scrolled) now. */
	private static void save(File dir, String name, BestPathPanel panel, int height, boolean relayout) throws IOException
	{
		if (relayout)
		{
			layout(panel, WIDTH, height);
		}
		BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		panel.paint(g);
		g.dispose();
		File f = new File(dir, name + ".png");
		ImageIO.write(img, "png", f);
		System.out.println("  " + f.getName());
		runPendingEvents();
	}

	/**
	 * Lets Swing handle the events waiting in its queue, then carries on (the -PrealData run ran out of its
	 * 256 MB): every picture is made inside one task on the Swing thread, so the queue never ran and the resize events
	 * each panel's layout posts piled up, every one holding its whole panel (and the panel its list of every transport)
	 * in memory. A secondary loop (as a modal dialog uses) handles what is queued now; the exit queued last ends it.
	 */
	private static void runPendingEvents()
	{
		java.awt.SecondaryLoop loop = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
		java.awt.EventQueue.invokeLater(loop::exit);
		loop.enter();
	}
}
