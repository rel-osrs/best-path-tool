package com.bestpath.tool.route;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.BoatTable;
import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.WildernessZonesDiagnostics;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.DoorMapBuilder;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import com.bestpath.tool.extract.BoatTableExtractor;
import com.bestpath.tool.extract.CollisionExtractor;
import com.bestpath.tool.extract.ToolMap;
import com.bestpath.tool.extract.ExportBuilder;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.extract.MapPlaces;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Developer aid ("Update plugin data" without the window): writes the plugin's data files (format
 * {@value PluginData#FORMAT}, docs/DATA_FORMATS.md) from the cache plus the manual changes, built the same way as
 * the tool's own data build, then reads them back and checks that the plugin's routes on them are exactly the routes
 * the tool's own search finds on the same data.
 *
 * <p>Three steps, one class each: this class builds the data ({@link #build}, then what else the export reads from
 * the cache); {@link PluginExportWriter} makes and writes the files; {@link PluginRouteCheck} (walking) and
 * {@link PluginSailingCheck} (sailing) check the routes.
 *
 * <p>{@code gradlew -p tool exportPlugin --args="<cacheDir> <outDir> [checkRoutes] [sailingRoutesPerBoat]"}
 */
public final class PluginExportCli
{
	private PluginExportCli()
	{
	}

	/** The cache's item numbers by lower-case name (the Slayer places' loot names its items). */
	private static Map<String, List<Integer>> itemIds(com.bestpath.tool.cache.LoadedCache cache) throws IOException
	{
		Map<String, List<Integer>> out = new java.util.HashMap<>();
		for (Map.Entry<Integer, String> e : cache.itemNames().entrySet())
		{
			out.computeIfAbsent(e.getValue().toLowerCase(java.util.Locale.ROOT), k -> new java.util.ArrayList<>()).add(e.getKey());
		}
		return out;
	}

	/** The search caches' budget, as in the plugin. */
	static final long CACHE_BUDGET = PluginRouter.DEFAULT_CACHE_BUDGET;

	public static void main(String[] args) throws Exception
	{
		File ws = ToolSettings.findWorkspace();
		// no cache given: the one the plugin data was made from (its manifest)
		File cacheDir = args.length > 0 ? new File(args[0]) : com.bestpath.tool.cache.DataCache.folder(ws);
		File outDir = new File(args.length > 1 ? args[1] : new File(ws, "src/main/resources/com/bestpath/data").getPath());
		int checkRoutes = args.length > 2 ? Integer.parseInt(args[2]) : 100;
		int sailRoutes = args.length > 3 ? Integer.parseInt(args[3]) : 24;
		backUpBeforeAnotherCache(ws, cacheDir, outDir);
		Files.createDirectories(outDir.toPath());
		Built b;
		try (LoadedCache cache = LoadedCache.open(cacheDir))
		{
			b = buildAll(cache, ws);
		}
		PluginExportWriter.write(b, ws, outDir);
		PluginRouteCheck.check(b, outDir, checkRoutes, sailRoutes);
	}

	/** When the files go into the plugin's own data folder: a backup before its data is made from another game cache. */
	private static void backUpBeforeAnotherCache(File ws, File cacheDir, File outDir) throws IOException
	{
		com.bestpath.tool.cache.CacheInfo info = com.bestpath.tool.cache.CacheInfo.find(cacheDir);
		if (info != null && outDir.getCanonicalFile().equals(new File(ws, com.bestpath.tool.cache.Backups.PLUGIN_DATA).getCanonicalFile()))
		{
			com.bestpath.tool.cache.Backups.beforeCache(ws, info.getOpenrs2Id(), System.out::println);
		}
	}

	/** The map data ({@link #build}) and everything else the export takes from the cache, printing what it found. */
	private static Built buildAll(LoadedCache cache, File ws) throws IOException
	{
		Built b = build(cache, ws);
		b.clues = ClueExport.build(ws);
		ClueExport.checkItems(b.clues, cache);
		// what the game's boat varbits mean, from the cache's DB tables
		BoatTableExtractor.Result boats = BoatTableExtractor.extract(cache::dbRowsById);
		b.boats = boats.table;
		// where the game's world maps draw each area (underground maps draw areas somewhere else than the game tiles)
		com.bestpath.core.data.WorldMapsBuilder wm = cache.worldMaps();
		b.worldMaps = wm.build();
		// the places by kind for "go to nearest": the cache's map icons, named by hand
		b.places = placeKinds(cache, ws, ManualState.placeKinds(b.edits.active()));
		addWilderness(b, cache, ws);
		System.out.println("world maps: " + b.worldMaps.mapCount() + " maps");
		addChartingTasks(b, cache, ws);
		for (String p : wm.problems())
		{
			System.out.println("  WORLD MAP: " + p);
		}
		System.out.println("boat table: " + BoatTableExtractor.describe(b.boats));
		for (String n : boats.notes)
		{
			System.out.println("  BOAT TABLE: " + n);
		}
		// what the player clicks for each transport: the exact menu text, from the cache
		b.menus = com.bestpath.tool.menu.MenuExport.build(cache, b.records, ws);
		b.slayer = slayer(cache, ws, b);
		printMenuTexts(b, ws);
		return b;
	}

	/**
	 * The Wilderness and its level 20 and 30 lines: the game's own level script run for every tile, with the safe
	 * areas (Ferox Enclave: the walled ground around its anchor tile) left out.
	 */
	private static void addWilderness(Built b, LoadedCache cache, File ws) throws IOException
	{
		com.bestpath.tool.extract.SafeAreas safe = com.bestpath.tool.extract.SafeAreas.of(b.export,
			com.bestpath.tool.extract.SafeAreas.read(new File(ws, com.bestpath.tool.extract.SafeAreas.FILE)));
		b.wilderness = new com.bestpath.tool.extract.WildernessScript(cache::script).zones(safe::contains);
		System.out.println("wilderness: " + WildernessZonesDiagnostics.rectangleCount(b.wilderness) + " rectangles (client script "
			+ com.bestpath.tool.extract.WildernessScript.LEVEL_SCRIPT + "); safe areas: " + safe.describe());
	}

	/**
	 * The sea charting tasks from the cache's DB tables, with their places. Each place is a boat spot or a land spot:
	 * one on open sea is a boat spot; the others are as answered on their cards, else guessed (ground to
	 * stand on near it: land). A boat spot is moved onto the open sea.
	 */
	private static void addChartingTasks(Built b, LoadedCache cache, File ws) throws IOException
	{
		try
		{
			b.charting = com.bestpath.tool.extract.ChartingExtractor.export(cache, ws);
		}
		catch (IllegalStateException gameChanged)
		{
			keepLastCharting(b, ws, gameChanged);
			return;
		}
		List<String> seaNotes = new ArrayList<>();
		Map<Integer, Boolean> spotAnswers = ManualState.chartingSpots(b.edits.active());
		List<com.bestpath.tool.extract.ChartingSpots.Spot> spots = com.bestpath.tool.extract.ChartingSpots.classify(b.charting,
			b.seaGrid::isOcean, com.bestpath.tool.extract.ChartingSpots.ground(b.export));
		b.charting = com.bestpath.tool.extract.ChartingSpots.apply(b.charting, spots, spotAnswers, seaNotes);
		int boatSpots = 0;
		int answered = 0;
		int offSea = 0;
		for (com.bestpath.tool.extract.ChartingSpots.Spot s : spots)
		{
			boatSpots += s.boat(spotAnswers) ? 1 : 0;
			answered += !s.onSea && spotAnswers.containsKey(s.task.getNumber()) ? 1 : 0;
			offSea += s.onSea ? 0 : 1;
		}
		System.out.println("sea charting: " + com.bestpath.tool.extract.ChartingExtractor.describe(b.charting) + "; " + boatSpots
			+ " boat spots, " + (spots.size() - boatSpots) + " land spots (" + (spots.size() - offSea) + " on open sea; of the "
			+ offSea + " others " + answered + " answered on their cards, the rest guessed); " + seaNotes.size() + " boat spots with no open sea near");
		for (String n : seaNotes)
		{
			System.out.println("  SEA CHARTING: " + n);
		}
	}

	/**
	 * The Slayer tasks (cache DB tables 113-116) and where to kill them (data/wiki/slayer-places.tsv). Wiki tiles
	 * given where a world map draws an area are put back on the game tile ({@code b.worldMaps}).
	 */
	private static com.bestpath.tool.slayer.SlayerExport.Result slayer(LoadedCache cache, File ws, Built b) throws IOException
	{
		return com.bestpath.tool.slayer.SlayerExport.build(com.bestpath.tool.slayer.SlayerTables.read(cache), ws, b.export, b.worldMaps, id ->
		{
			try
			{
				return cache.itemName(id);
			}
			catch (IOException e)
			{
				return null;
			}
		}, com.bestpath.tool.slayer.CollectionLog.read(cache), itemIds(cache)); // each place's own loot
	}

	/** Prints how many transports got a menu text, and writes every transport's menu text to tool/build/menu-texts.tsv. */
	private static void printMenuTexts(Built b, File ws) throws IOException
	{
		System.out.println(com.bestpath.tool.menu.MenuExport.summary(b.records, b.menus));
		File table = new File(ws, "tool/build/menu-texts.tsv");
		if (table.getParentFile().isDirectory())
		{
			com.bestpath.tool.menu.MenuExport.writeTable(table, b.records, b.menus);
			System.out.println("every transport's menu text: " + table.getAbsolutePath());
		}
	}

	/** The data as the tool builds it, and what the export takes from it. */
	/**
	 * The cache's sea charting tables or scripts are not shaped the way the import reads them (a game update changed
	 * them): the plugin keeps the charting file it has, unchanged, and the export warns (the export window shows the
	 * warnings in its result line).
	 */
	static void keepLastCharting(Built b, File ws, IllegalStateException why) throws IOException
	{
		System.out.println("WARNING: sea charting could not be read from this cache: " + why.getMessage());
		File last = new File(ws, com.bestpath.tool.cache.Backups.PLUGIN_DATA + "/" + com.bestpath.core.data.PluginData.CHARTING);
		if (!last.isFile())
		{
			System.out.println("WARNING: the plugin has no earlier sea charting file, so this data has no sea charting.");
			return;
		}
		b.chartingKept = java.nio.file.Files.readAllBytes(last.toPath());
		b.charting = com.bestpath.core.data.ChartingTasks.parse(
			java.util.Arrays.asList(new String(b.chartingKept, java.nio.charset.StandardCharsets.UTF_8).split("\r?\n")));
		System.out.println("WARNING: kept the plugin's earlier sea charting file (" + b.charting.getTasks().size()
			+ " tasks); the charting import (ChartingExtractor) needs a look.");
	}

	static final class Built
	{
		FlagGrid grid;
		ManualState manual;
		EditStore edits;
		CollisionMap export;
		DoorMap doors;
		List<TransportRecord> records;
		int cacheId;
		int build;
		Set<Integer> keptRegions;
		Map<String, Integer> leftOut = new TreeMap<>();
		/** The clue steps (null: none exported). */
		ClueExport.Result clues;
		/** The sea, its pieces, the main ocean grid (as the tool builds them) and the docks with shipwrights. */
		SeaMap sea;
		SeaPieces pieces;
		SeaGrid seaGrid;
		List<Dock> docks;
		/** The sea graph's channels, without the ones deleted by hand (null until the sea file is made). */
		List<int[]> channels;
		/** What the game's boat varbits mean (null: not exported). */
		BoatTable boats;
		/** Where the game's world maps draw each area (null: not exported). */
		com.bestpath.core.data.WorldMaps worldMaps;
		/** The Wilderness level of every tile, from the game's client script (null: not exported). */
		com.bestpath.core.data.WildernessZones wilderness;
		/** The places by kind for "go to nearest" (null: not exported). */
		com.bestpath.core.data.PlaceKinds places;
		/** The sea charting tasks, boat spots on open sea (null: not exported). */
		com.bestpath.core.data.ChartingTasks charting;
		/** The plugin's earlier charting file, written unchanged when this cache's charting could not be read; else null. */
		byte[] chartingKept;
		/** The menu texts by transport id (null: none, the columns stay empty). */
		Map<String, com.bestpath.tool.menu.MenuMatcher.Result> menus;
		/** The Slayer tasks and their places (null: not exported). */
		com.bestpath.tool.slayer.SlayerExport.Result slayer;
	}

	/**
	 * The cache's map icons with the names in {@link MapPlaces#NAMES} (no names file: null), with the card
	 * answers: a removed kind left out, a new name used; prints what was left out.
	 */
	static PlaceKinds placeKinds(LoadedCache cache, File ws, ManualState.PlaceKindAnswers answers) throws IOException
	{
		File names = new File(ws, MapPlaces.NAMES);
		if (!names.isFile())
		{
			return null;
		}
		List<String> problems = new ArrayList<>();
		java.util.Map<Integer, int[]> found = MapPlaces.find(cache);
		PlaceKinds kinds = MapPlaces.kinds(MapPlaces.answered(MapPlaces.readNames(Files.readAllLines(names.toPath(),
			StandardCharsets.UTF_8)), answers.removed, answers.names), found, MapPlaces.sprites(cache), problems);
		if (!answers.removed.isEmpty())
		{
			System.out.println("places by kind: " + answers.removed.size() + " kinds removed on their cards: icons " + answers.removed);
		}
		System.out.println("places by kind: " + kinds.getKinds().size() + " kinds named of " + found.size() + " map icon kinds placed");
		for (String p : problems)
		{
			System.out.println("  PLACES: " + p);
		}
		return kinds;
	}

	/** The map data as the tool's data build makes it: the export map, doors, confirmed transports and the sea. */
	static Built build(LoadedCache cache, File ws) throws IOException
	{
		long t0 = System.nanoTime();
		Built b = new Built();
		// the map as the tool builds it: the manual changes, the sea closed for walking, the boats' sea grid and docks
		ToolMap map = ToolMap.withSea(cache, ws);
		b.edits = map.edits;
		b.grid = map.grid;
		ManualState manual = map.manual;
		b.manual = manual;
		b.sea = map.sea;
		b.pieces = map.pieces;
		b.seaGrid = map.seaGrid;
		b.docks = map.docks;
		b.cacheId = cache.getInfo().getOpenrs2Id();
		b.build = cache.getInfo().getBuild();
		Set<Integer> regionIds = cache.getRegionIds();
		CollisionMap full = ExportBuilder.fullCollision(b.grid, regionIds, b.cacheId, b.build);
		CoarseGraph fullGraph = CoarseGraph.build(full);
		boolean[] grey = greyComponents(fullGraph, manual);
		Set<Integer> ignored = manual.getIgnoredRegions();
		b.export = ExportBuilder.exportCollision(b.grid, fullGraph, grey, ignored, regionIds, b.cacheId, b.build);
		b.keptRegions = new TreeSet<>(regionIds);
		b.keptRegions.removeAll(ignored);
		b.doors = doorMap(b.grid);
		b.records = confirmedTransports(manual, b.leftOut);
		System.out.printf(Locale.ROOT, "data built in %.1f s: %,d cache regions, %,d ignored; export map %,d cells (tool map %,d)%n",
			(System.nanoTime() - t0) / 1e9, regionIds.size(), ignored.size(), b.export.size(), full.size());
		return b;
	}

	/** The door steps: open with the doors open and blocked with every door shut (as in the tool). */
	private static DoorMap doorMap(FlagGrid grid)
	{
		FlagGrid shutGrid = grid.copy();
		List<int[]> doorTiles = new ArrayList<>();
		for (int[] door : grid.getDoors())
		{
			CollisionExtractor.addDoorWalls(shutGrid, door);
			doorTiles.add(new int[]{door[0], door[1], door[2]});
		}
		MovementRules.Flags[] open = new MovementRules.Flags[4];
		MovementRules.Flags[] shut = new MovementRules.Flags[4];
		for (int p = 0; p < 4; p++)
		{
			open[p] = grid.plane(p);
			shut[p] = shutGrid.plane(p);
		}
		return DoorMapBuilder.build(open, shut, doorTiles);
	}

	/** The confirmed transports only (not NEEDS_FIX, not on the test list); counts the others in {@code leftOut} by why. */
	private static List<TransportRecord> confirmedTransports(ManualState manual, Map<String, Integer> leftOut)
	{
		Set<String> testLater = manual.getTestLater().keySet();
		List<TransportRecord> records = new ArrayList<>();
		for (TransportRecord r : manual.getTransports().values())
		{
			if (TransportRecord.NEEDS_FIX.equals(r.category))
			{
				leftOut.merge("NEEDS_FIX", 1, Integer::sum);
			}
			else if (testLater.contains(r.id))
			{
				leftOut.merge("on the test list", 1, Integer::sum);
			}
			else
			{
				records.add(r);
			}
		}
		return records;
	}

	/** The sub-regions the export leaves out: closed-off pockets (unless kept) and hidden ones (as the tool's data build). */
	private static boolean[] greyComponents(CoarseGraph cg, ManualState manual)
	{
		boolean[] hidden = new boolean[cg.componentCount()];
		for (int[] a : manual.getHiddenAnchors())
		{
			int comp = cg.componentAt(a[0], a[1], a[2]);
			if (comp >= 0)
			{
				hidden[comp] = true;
			}
		}
		boolean[] pockets = cg.pockets();
		for (int[] a : manual.getKeptAnchors())
		{
			int comp = cg.componentAt(a[0], a[1], a[2]);
			if (comp >= 0)
			{
				pockets[comp] = false;
			}
		}
		boolean[] grey = new boolean[hidden.length];
		for (int i = 0; i < grey.length; i++)
		{
			grey[i] = pockets[i] || hidden[i];
		}
		return grey;
	}

	/** A random tile of a component (also used by the speed test). */
	public static int[] randomTile(CoarseGraph g, int comp, Random rnd)
	{
		int cell = g.componentCell(comp);
		int region = cell >> 2;
		int plane = cell & 3;
		short[] labels = g.cellLabels(region, plane);
		int base = g.cellComponentBase(region, plane);
		int want = comp - base + 1;
		int pick = rnd.nextInt(g.componentSize(comp));
		for (int l = 0; l < 4096; l++)
		{
			if (labels[l] == want && pick-- == 0)
			{
				return new int[]{((region >> 8) << 6) + (l >> 6), ((region & 0xFF) << 6) + (l & 63), plane};
			}
		}
		throw new IllegalStateException("component " + comp + " has no tile");
	}
}
