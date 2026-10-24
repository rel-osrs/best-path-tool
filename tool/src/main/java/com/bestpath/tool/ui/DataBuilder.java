package com.bestpath.tool.ui;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.CollisionMapWriter;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.BankList;
import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.DoorMapBuilder;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaChannels;
import com.bestpath.core.sea.SeaChannelsBuilder;
import com.bestpath.core.sea.SeaGraph;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaGridBuilder;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import com.bestpath.tool.bank.ToolBanks;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.extract.ChartingExtractor;
import com.bestpath.tool.extract.ChartingSpots;
import com.bestpath.tool.extract.CollisionExtractor;
import com.bestpath.tool.extract.DerivedFiles;
import com.bestpath.tool.extract.DockExtractor;
import com.bestpath.tool.extract.ExportBuilder;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.extract.MapPlaces;
import com.bestpath.tool.extract.SeaExtractor;
import com.bestpath.tool.transport.CacheTableTeleports;
import com.bestpath.tool.transport.CandidateExtractor;
import com.bestpath.tool.transport.SuggestionEngine;
import com.bestpath.tool.transport.TransportCandidate;
import com.bestpath.tool.transport.TransportData;
import com.bestpath.tool.wiki.Shipwrights;
import com.bestpath.tool.wiki.WikiTeleports;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Builds the tool's data for the open cache: the cache's own movement data, sea and transport candidates (read once per
 * cache) with all the manual changes on top. Runs on the main window's background thread only; each build redoes only
 * what the changes since the last build touched.
 */
final class DataBuilder
{
	/** What one build made, handed to the Swing thread to show. */
	static final class Result
	{
		final LoadedCache cache;
		FlagGrid grid;
		SeaMap sea;
		SeaPieces pieces;
		SeaGrid seaGrid;
		List<Dock> dockList;
		ManualState manual;
		CollisionMap compact;
		CoarseGraph cg;
		Pathfinder pf;
		Sailing sailing;
		DoorMap doors;
		/** Sub-regions hidden by hand. */
		boolean[] hidden;
		/** Small closed-off pockets, without the ones kept by hand. */
		boolean[] pocketFlags;
		/** Everything left out of the plugin's data: pockets and hidden sub-regions. */
		boolean[] grey;
		TransportData td;
		List<MapPlaces.Card> placeCards;
		List<ChartingSpots.Spot> chartSpots;
		/** The status line; its "..." is replaced by the plugin's data size once that is known. */
		String text;
		/** Where the movement data is saved for the next start, and under which fingerprint. */
		DerivedFiles derived;
		long fingerprint;
		/** True when the movement data came from the saved copy (no need to save it again). */
		boolean fromSavedCopy;

		Result(LoadedCache cache)
		{
			this.cache = cache;
		}
	}

	/** What the last build made, so the next one redoes only what an edit changed. */
	private static final class LastBuild
	{
		final Map<Integer, Long> hashes;
		final SeaPieces pieces;
		final SeaGrid seaGrid;
		final CollisionMap compact;
		/** The boat-only transports' ends the sea grid was built with ({@code SeaExtractor.boatEnds}). */
		final int[][] boatEnds;
		/** Every sea channel of {@link #seaGrid} (before deletions by hand): found again only when the sea changes. */
		final List<int[]> channels;

		LastBuild(Map<Integer, Long> hashes, SeaPieces pieces, SeaGrid seaGrid, CollisionMap compact, int[][] boatEnds,
			List<int[]> channels)
		{
			this.channels = channels;
			this.boatEnds = boatEnds;
			this.hashes = hashes;
			this.pieces = pieces;
			this.seaGrid = seaGrid;
			this.compact = compact;
		}
	}

	private final File workspace;

	/** The open cache's own movement data and transport candidates (no changes applied). */
	private LoadedCache baseFor;
	private FlagGrid baseGrid;
	private List<TransportCandidate> baseCands;
	private SeaMap baseSea;
	private DockExtractor.Result baseDocks;
	private LastBuild lastBuild;

	/** The sea charting tasks (with Quest Helper's places) for the Boat / Land cards, read once per cache. */
	private LoadedCache chartingFor;
	private ChartingTasks chartingTasks = ChartingTasks.NONE;

	/** The map icons' places and pictures for the place kind cards, worked out once per cache. */
	private LoadedCache placesFor;
	private Map<Integer, int[]> placesFound = Map.of();
	private Map<Integer, Integer> placeSprites = Map.of();
	private final Map<Integer, BufferedImage> placePictures = new HashMap<>();

	DataBuilder(File workspace)
	{
		this.workspace = workspace;
	}

	/**
	 * Builds the data for cache {@code c} with the changes {@code active}. {@code status} shows a progress message;
	 * {@code previousSailing} gives the last build's sailing data (its boat searches are reused).
	 */
	Result build(LoadedCache c, List<Edit> active, Consumer<String> status, Supplier<Sailing> previousSailing)
		throws Exception
	{
		long t0 = System.nanoTime();
		if (baseFor != c)
		{
			readBase(c, status);
		}
		Result r = new Result(c);
		r.grid = baseGrid.copy();
		r.sea = baseSea;
		// the manual changes on top of the cache
		r.manual = new ManualState(active, r.grid);
		// which regions changed since the last build (null: build everything)
		Map<Integer, Long> hashes = r.grid.blockHashes();
		Set<Integer> changed = lastBuild == null ? null : FlagGrid.changedRegions(lastBuild.hashes, hashes);
		int[][] boatEnds = buildSea(r, changed);
		buildMovement(r, hashes, changed);
		// the sea channels, found once per sea; the deleted ones left out
		List<int[]> allChannels = lastBuild != null && lastBuild.seaGrid == r.seaGrid && lastBuild.channels != null
			? lastBuild.channels : SeaChannels.find(SeaGraph.build(r.seaGrid, r.dockList));
		List<int[]> channels = SeaChannelsBuilder.without(allChannels, r.manual.getDeletedChannels());
		lastBuild = new LastBuild(hashes, r.pieces, r.seaGrid, r.compact, boatEnds, allChannels);
		r.cg = CoarseGraph.build(r.compact);
		r.pf = new Pathfinder(r.compact, r.cg, r.grid::get); // walls: fairy rings used from beside
		r.sailing = new Sailing(r.seaGrid, r.dockList, channels, r.cg, previousSailing.get());
		r.doors = doorMap(r.grid);
		int[] counts = markLeftOut(r);
		r.td = transportData(c, r);
		double secs = (System.nanoTime() - t0) / 1e9;
		// the "go to nearest" place kinds' Keep / Remove cards
		r.placeCards = placeCards(c);
		// and the sea charting spots for the Boat / Land cards
		r.chartSpots = chartingSpots(c, r.seaGrid, r.compact);
		r.text = String.format(Locale.ROOT, "Data ready (%.1f s)   Sub-regions %,d (%,d pockets, %,d hidden), ignored regions %,d, entrances %,d   Transports %,d   Docks %,d   Plugin movement data ...    ",
			secs, r.cg.componentCount(), counts[0], counts[1], r.manual.getIgnoredRegions().size(), r.cg.entranceCount(),
			r.td.getConfirmed().size(), r.dockList.size());
		return r;
	}

	/** Once per cache: the saved copy next to the cache, or a full extraction (then saved). */
	private void readBase(LoadedCache c, Consumer<String> status) throws Exception
	{
		baseGrid = null;
		baseCands = null;
		baseSea = null;
		baseDocks = null;
		lastBuild = null; // another cache: build everything
		status.accept("Reading movement data (first time for this cache: extracting, about 10 s) ...");
		baseGrid = CollisionExtractor.loadOrExtract(c);
		baseCands = CandidateExtractor.extract(c);
		baseSea = SeaExtractor.extract(c);
		baseDocks = DockExtractor.extract(c, baseGrid, baseSea);
		baseFor = c;
	}

	/**
	 * The sea for the boats (pieces, search grid and docks), then the sea closed to walking. Returns the boat-only
	 * transports' ends the sea grid was built with.
	 */
	private int[][] buildSea(Result r, Set<Integer> changed) throws Exception
	{
		FlagGrid grid = r.grid;
		SeaMap sea = r.sea;
		ManualState manual = r.manual;
		boolean seaChanged = changed == null || changed.stream().anyMatch(sea::hasSea);
		// the water boat-only transports lead to is the boats' too: a change there builds the grid again
		int[][] boatEnds = SeaExtractor.boatEnds(manual.getTransports().values());
		boolean boatsChanged = lastBuild == null || !Arrays.deepEquals(boatEnds, lastBuild.boatEnds);
		// the sea split along blocked tiles (boats cannot cross them), with the changes applied
		r.pieces = seaChanged ? SeaPieces.build(sea, grid.plane(0)) : lastBuild.pieces;
		// the main ocean as the boats' search grid (from the sea still open)
		r.seaGrid = seaChanged || boatsChanged
			? SeaGridBuilder.build(sea, r.pieces, grid.plane(0), boatEnds) : lastBuild.seaGrid;
		// then the sea is closed to walking: the player goes to sea only at a dock
		SeaExtractor.blockForWalking(grid, sea, (x, y) -> manual.isOpenedByOwner(x, y, 0));
		r.dockList = Shipwrights.assign(
			DockExtractor.withoutDuplicates(baseDocks.docks, r.pieces),
			new File(workspace, "data/wiki/shipwrights.json"), new ArrayList<>());
		return boatEnds;
	}

	/**
	 * The movement data: only the changed regions (and the ones around them) are worked out again; at the first build,
	 * the copy saved for exactly this data if there is one.
	 */
	private void buildMovement(Result r, Map<Integer, Long> hashes, Set<Integer> changed) throws Exception
	{
		LoadedCache c = r.cache;
		int cacheId = c.getInfo().getOpenrs2Id();
		int cacheBuild = c.getInfo().getBuild();
		r.derived = new DerivedFiles(c);
		r.fingerprint = DerivedFiles.fingerprint(hashes, r.grid.getDoors());
		CollisionMap saved = changed == null ? r.derived.loadMovement(r.fingerprint) : null;
		r.fromSavedCopy = saved != null;
		r.compact = saved != null ? saved : changed == null
			? ExportBuilder.fullCollision(r.grid, c.getRegionIds(), cacheId, cacheBuild)
			: ExportBuilder.fullCollision(r.grid, c.getRegionIds(), cacheId, cacheBuild,
				lastBuild.compact, CollisionMapBuilder.withNeighbours(changed));
	}

	/** Door and gate steps: possible with the doors open (as now), not with every door shut. */
	private static DoorMap doorMap(FlagGrid grid)
	{
		FlagGrid shutGrid = grid.copy();
		List<int[]> doorTiles = new ArrayList<>();
		for (int[] door : grid.getDoors())
		{
			CollisionExtractor.addDoorWalls(shutGrid, door);
			doorTiles.add(new int[]{door[0], door[1], door[2]});
		}
		MovementRules.Flags[] openPlanes = new MovementRules.Flags[4];
		MovementRules.Flags[] shutPlanes = new MovementRules.Flags[4];
		for (int p = 0; p < 4; p++)
		{
			openPlanes[p] = grid.plane(p);
			shutPlanes[p] = shutGrid.plane(p);
		}
		return DoorMapBuilder.build(openPlanes, shutPlanes, doorTiles);
	}

	/**
	 * Sub-regions hidden by hand, and everything left out of the export (pockets + hidden): fills {@code r.hidden},
	 * {@code r.pocketFlags} and {@code r.grey}. Returns the number of pockets and of hidden sub-regions.
	 */
	private static int[] markLeftOut(Result r)
	{
		CoarseGraph cg = r.cg;
		boolean[] hidden = new boolean[cg.componentCount()];
		for (int[] a : r.manual.getHiddenAnchors())
		{
			int comp = cg.componentAt(a[0], a[1], a[2]);
			if (comp >= 0)
			{
				hidden[comp] = true;
			}
		}
		boolean[] pocketFlags = cg.pockets();
		// pockets kept by hand (usable after all) stay in the data
		for (int[] a : r.manual.getKeptAnchors())
		{
			int comp = cg.componentAt(a[0], a[1], a[2]);
			if (comp >= 0)
			{
				pocketFlags[comp] = false;
			}
		}
		boolean[] grey = new boolean[hidden.length];
		int pocketCount = 0;
		int hiddenCount = 0;
		for (int i = 0; i < grey.length; i++)
		{
			grey[i] = pocketFlags[i] || hidden[i];
			pocketCount += pocketFlags[i] ? 1 : 0;
			hiddenCount += hidden[i] ? 1 : 0;
		}
		r.hidden = hidden;
		r.pocketFlags = pocketFlags;
		r.grey = grey;
		return new int[]{pocketCount, hiddenCount};
	}

	/** The transport candidates with their suggested ends, the teleports, and the manual transports. */
	private TransportData transportData(LoadedCache c, Result r) throws Exception
	{
		FlagGrid grid = r.grid;
		List<TransportCandidate> cands = baseCands;
		return new TransportData(cands,
			new SuggestionEngine(cands, SuggestionEngine.excluding(r.cg::componentAt, r.hidden),
				(x, y, p, dx, dy) -> MovementRules.canStep(grid.plane(p), x, y, dx, dy)).avoiding(r.pocketFlags).suggestAll(),
			WikiTeleports.withCache(CacheTableTeleports.read(c),
				new File(workspace, "data/wiki/teleports.json")), r.manual.getTransports(), r.manual.getRejected());
	}

	/** Saves the movement data next to the cache for the next start, unless it came from that saved copy. */
	void saveMovement(Result r) throws IOException
	{
		if (!r.fromSavedCopy)
		{
			r.derived.saveMovement(r.fingerprint, r.compact);
		}
	}

	/** The status line with the size the plugin's movement data would have. */
	String withPluginSize(Result r) throws IOException
	{
		LoadedCache c = r.cache;
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		CollisionMapWriter.write(
			ExportBuilder.exportCollision(r.grid, r.cg, r.grey, r.manual.getIgnoredRegions(), c.getRegionIds(),
				c.getInfo().getOpenrs2Id(), c.getInfo().getBuild()), bytes);
		return r.text.replace("...", String.format(Locale.ROOT, "%.2f MB", bytes.size() / 1e6));
	}

	/**
	 * Every sea charting task's spot, boat or land, for the Boat / Land cards (the automatic guess pre-set); none when
	 * the cache can't be read.
	 */
	private synchronized List<ChartingSpots.Spot> chartingSpots(LoadedCache c, SeaGrid seaGrid, CollisionMap compact)
	{
		try
		{
			if (chartingFor != c)
			{
				chartingTasks = ChartingExtractor.export(c, workspace);
				chartingFor = c;
			}
			return ChartingSpots.classify(chartingTasks, seaGrid::isOcean, ChartingSpots.ground(compact));
		}
		catch (Exception e)
		{
			System.err.println("sea charting cards: " + e);
			return List.of();
		}
	}

	/**
	 * One card's worth for every kind named in data/manual/map-icon-kinds.tsv, to decide whether to keep
	 * or remove each group; the names are read again each build, the places and pictures once per cache. Nothing when
	 * the names file is missing or the cache can't be read.
	 */
	private synchronized List<MapPlaces.Card> placeCards(LoadedCache c)
	{
		File names = new File(workspace, MapPlaces.NAMES);
		if (!names.isFile())
		{
			return List.of();
		}
		try
		{
			if (placesFor != c)
			{
				placesFound = MapPlaces.find(c);
				placeSprites = MapPlaces.sprites(c);
				placePictures.clear();
				placesFor = c;
			}
			List<String> lines = Files.readAllLines(names.toPath(), StandardCharsets.UTF_8);
			int[] bank = bankTiles();
			return MapPlaces.cards(MapPlaces.readNames(lines), MapPlaces.readWhy(lines), placesFound, placeSprites,
				bank, sprite -> placePictures.computeIfAbsent(sprite, id -> spriteOrNull(c, id)));
		}
		catch (Exception e)
		{
			System.err.println("place kind cards: " + e);
			return List.of();
		}
	}

	/** Every tile a bank is used from. */
	private static int[] bankTiles()
	{
		LinkedHashSet<Integer> bank = new LinkedHashSet<>();
		for (BankList.Bank b : ToolBanks.list().getBanks())
		{
			for (int t : b.getUse())
			{
				bank.add(t);
			}
		}
		return bank.stream().mapToInt(Integer::intValue).toArray();
	}

	private static BufferedImage spriteOrNull(LoadedCache c, int id)
	{
		try
		{
			return c.spriteImage(id, 0);
		}
		catch (IOException e)
		{
			return null;
		}
	}
}
