package com.bestpath.tool.route;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapWriter;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.FreeLandDiagnostics;
import com.bestpath.core.collision.FreeLandWriter;
import com.bestpath.core.data.BankList;
import com.bestpath.core.data.BankListWriter;
import com.bestpath.core.data.BoatTableDiagnostics;
import com.bestpath.core.data.BoatTableWriter;
import com.bestpath.core.data.ClueDataWriter;
import com.bestpath.core.data.DoorBitsWriter;
import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.data.PlaceKindsWriter;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataWriter;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.PluginRouterDiagnostics;
import com.bestpath.core.data.SeaBitsWriter;
import com.bestpath.core.data.SlayerPlacesWriter;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.TransportLineBuilder;
import com.bestpath.core.data.TransportLineWriter;
import com.bestpath.core.data.WallBits;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.data.WallBitsDiagnostics;
import com.bestpath.core.data.WallBitsWriter;
import com.bestpath.core.data.WildernessZonesDiagnostics;
import com.bestpath.core.data.WildernessZonesWriter;
import com.bestpath.core.data.WorldMapsDiagnostics;
import com.bestpath.core.data.WorldMapsWriter;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Transport;
import com.bestpath.core.path.WalkTablesBuilder;
import com.bestpath.core.path.WalkTablesDiagnostics;
import com.bestpath.core.path.WalkTablesWriter;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.sea.SeaGridDiagnostics;
import com.bestpath.tool.edit.FreeLandFile;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.extract.MapPlaces;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes the plugin's data files for {@link PluginExportCli}: each file made in turn, in a fixed order with the
 * manifest last, while the manifest's counts are collected; then all of them are written to the output folder.
 *
 * <p>The files: the export movement table, door steps, line-of-sight walls, the confirmed transports with their menu
 * texts (no candidates, suggestions or Shortest Path rows), the hand-made tables (item groups, runes, networks,
 * teleport categories, item-group defaults), free land, the sea (main ocean, docks, channels), the banks, the places
 * for "go to nearest", the Slayer places, the boat table, the clue steps, the world maps, the Wilderness zones, the
 * sea charting tasks and the walking tables. The optional ones are left out when there is nothing for them. Not
 * written: the coarse graph, the sea's clearance and the sea graph (the plugin builds them at start) and house things.
 */
final class PluginExportWriter
{
	private final PluginExportCli.Built b;
	private final File ws;
	/** The files by name, in the order they are made and written. */
	private final Map<String, byte[]> files = new LinkedHashMap<>();
	/** What each file holds, for the manifest. */
	private final Map<String, String> counts = new LinkedHashMap<>();
	/** The confirmed transports by kind. */
	private final Map<String, Integer> byKind = new TreeMap<>();

	private PluginExportWriter(PluginExportCli.Built b, File ws)
	{
		this.b = b;
		this.ws = ws;
	}

	static void write(PluginExportCli.Built b, File ws, File outDir) throws IOException
	{
		PluginExportWriter w = new PluginExportWriter(b, ws);
		w.putMovement();
		w.putDoors();
		w.putWalls();
		w.putTransports();
		w.putManualTables();
		w.putFreeLand();
		w.putSea();
		w.putBanks();
		w.putPlaces();
		w.putSlayer();
		w.putBoats();
		w.putClues();
		w.putWorldMaps();
		w.putWilderness();
		w.putCharting();
		// the search's walking tables worked out ahead of time, from the files above read back as the plugin reads
		// them, so they are made for exactly the map the plugin searches
		byte[] walks = walkTables(w.files, b, w.counts);
		w.files.put(PluginData.WALKS, walks);
		String fingerprint = PluginData.fingerprint(w.files);
		w.files.put(PluginData.MANIFEST, PluginDataWriter.write(w.files, b.cacheId, b.build,
			Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), w.counts));
		w.writeAll(outDir, fingerprint);
	}

	private void putMovement() throws IOException
	{
		ByteArrayOutputStream mv = new ByteArrayOutputStream();
		CollisionMapWriter.write(b.export, mv);
		files.put(PluginData.MOVEMENT, mv.toByteArray());
		counts.put("cells", String.valueOf(b.export.size()));
	}

	private void putDoors() throws IOException
	{
		ByteArrayOutputStream dr = new ByteArrayOutputStream();
		int doorTiles = DoorBitsWriter.write(b.doors, b.export.cellKeys(), dr);
		files.put(PluginData.DOORS, dr.toByteArray());
		counts.put("door tiles", String.valueOf(doorTiles));
	}

	private void putWalls() throws IOException
	{
		WallBits walls = WallBitsBuilder.fromFlags(b.grid::get, wallCells(b.export, b.keptRegions));
		ByteArrayOutputStream wl = new ByteArrayOutputStream();
		WallBitsWriter.write(walls, wl);
		files.put(PluginData.WALLS, wl.toByteArray());
		counts.put("wall cells", String.valueOf(WallBitsDiagnostics.cellCount(walls)));
	}

	/** Cells with walls kept for line of sight: every cell of the movement table and its 8 neighbours (same plane), kept regions only. */
	static Set<Integer> wallCells(CollisionMap export, Set<Integer> keptRegions)
	{
		Set<Integer> cells = new TreeSet<>();
		for (int key : export.cellKeys())
		{
			int rx = key >> 10;
			int ry = (key >> 2) & 0xFF;
			int p = key & 3;
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					int nx = rx + dx;
					int ny = ry + dy;
					if (nx < 0 || ny < 0 || nx > 255 || ny > 255)
					{
						continue;
					}
					int region = nx << 8 | ny;
					if (keptRegions.contains(region))
					{
						cells.add(region << 2 | p);
					}
				}
			}
		}
		return cells;
	}

	private void putTransports()
	{
		StringBuilder tsv = new StringBuilder();
		tsv.append("# ").append(PluginDataWriter.header("confirmed transports")).append('\n');
		tsv.append(TransportLine.HEADER).append('\n');
		int cleaned = 0;
		for (TransportRecord r : b.records)
		{
			tsv.append(transportLine(r)).append('\n');
			cleaned += changedByCleaning(r) ? 1 : 0;
			byKind.merge(r.kind.name(), 1, Integer::sum);
		}
		files.put(PluginData.TRANSPORTS, tsv.toString().getBytes(StandardCharsets.UTF_8));
		counts.put("transports", String.valueOf(b.records.size()));
		byKind.forEach((k, v) -> counts.put("transports " + k, String.valueOf(v)));
		b.leftOut.forEach((k, v) -> counts.put("left out " + k, String.valueOf(v)));
		if (cleaned > 0)
		{
			System.out.printf(Locale.ROOT, "WARNING: %d transports had tabs or line breaks in a text field (written as spaces)%n", cleaned);
		}
	}

	/** A transport's line of the transports file, with its menu text; checked to read back the same. */
	private String transportLine(TransportRecord r)
	{
		TransportLine l = line(r);
		com.bestpath.tool.menu.MenuMatcher.Result menu = b.menus == null ? null : b.menus.get(r.id);
		if (menu != null)
		{
			l = TransportLineBuilder.withMenu(l, menu.there, menu.back);
		}
		String text = TransportLineWriter.write(l);
		if (!TransportLineWriter.write(TransportLine.parse(text)).equals(text))
		{
			throw new IllegalStateException("transport line does not read back the same: " + text);
		}
		return text;
	}

	static TransportLine line(TransportRecord r)
	{
		TransportLine.Kind kind = TransportLine.Kind.valueOf(r.kind.name());
		return TransportLine.builder(r.id, kind).name(r.name).category(r.category)
			.start(r.startX, r.startY, r.startPlane).end(r.endX, r.endY, r.endPlane).ticks(r.ticks)
			.ticksEstimate(r.ticksEstimate).questOnly(r.questOnly).canFail(r.canFail).cooldown(r.cooldown)
			.network(r.network).requirements(r.requirements).requirementsBack(r.requirementsBack)
			.teleportCategory(r.teleportCategory).wildernessLimit(r.wildernessLimit)
			.object(r.objectId, r.objectX, r.objectY, r.objectPlane).playerNote(r.playerNote).build().withBoats(r.largestBoat);
	}

	private static boolean changedByCleaning(TransportRecord r)
	{
		for (String s : new String[]{r.name, r.category, r.network, r.requirements, r.requirementsBack, r.teleportCategory, r.playerNote})
		{
			if (s != null && (s.indexOf('\t') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0))
			{
				return true;
			}
		}
		return false;
	}

	/** The hand-made tables copied as they are: item groups, runes, networks, teleport categories and item-group defaults. */
	private void putManualTables() throws IOException
	{
		for (String[] f : new String[][]{{PluginData.ITEM_GROUPS, "item-groups.tsv"}, {PluginData.RUNES, "runes.tsv"},
			{PluginData.NETWORKS, "networks.tsv"}, {PluginData.TELEPORT_CATEGORIES, "teleport-categories.tsv"}})
		{
			File src = new File(ws, "data/manual/" + f[1]);
			files.put(f[0], src.isFile() ? lf(Files.readAllBytes(src.toPath())) : new byte[0]);
		}
		// optional: each item group's default item (the sidebar's picture for a group), as it is
		File defaults = new File(ws, "data/manual/item-group-defaults.tsv");
		if (defaults.isFile())
		{
			files.put(PluginData.ITEM_GROUP_DEFAULTS, lf(Files.readAllBytes(defaults.toPath())));
		}
	}

	/**
	 * A table copied as it is, but with "\n" line ends: a Git checkout with autocrlf gives "\r\n", which would change
	 * the exported bytes (and the plugin's hashes) with the checkout, not the data.
	 */
	static byte[] lf(byte[] text)
	{
		return new String(text, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
	}

	private void putFreeLand()
	{
		FreeLand land = FreeLandFile.effective(b.edits.active());
		String fl = "# Free-to-play land with the manual changes (data/manual/free-land.tsv + changes.jsonl): <y> <x1>-<x2> ...\n"
			+ FreeLandWriter.write(land);
		files.put(PluginData.FREE_LAND, fl.getBytes(StandardCharsets.UTF_8));
		counts.put("free land tiles per plane", String.valueOf(FreeLandDiagnostics.tileCount(land)));
	}

	private void putSea() throws IOException
	{
		ByteArrayOutputStream se = new ByteArrayOutputStream();
		// with the channels (crooked water the sea graph's links miss): found here, read by the plugin; the ones the
		// deleted by hand are left out
		List<int[]> found = com.bestpath.core.sea.SeaChannels.find(com.bestpath.core.sea.SeaGraph.build(b.seaGrid, b.docks));
		b.channels = com.bestpath.core.sea.SeaChannelsBuilder.without(found, b.manual.getDeletedChannels());
		SeaBitsWriter.write(b.seaGrid, b.docks, b.channels, se);
		counts.put("sea channels (crooked water the links miss)", String.valueOf(b.channels.size()));
		if (b.channels.size() < found.size())
		{
			counts.put("sea channels you deleted", String.valueOf(found.size() - b.channels.size()));
		}
		files.put(PluginData.SEA, se.toByteArray());
		counts.put("sea tiles (main ocean)", String.valueOf(b.seaGrid.size()));
		counts.put("sea regions", String.valueOf(b.seaGrid.regionIds().length));
		counts.put("sea tiles with odd boat steps", String.valueOf(SeaGridDiagnostics.oddTiles(b.seaGrid).length));
		counts.put("docks", String.valueOf(b.docks.size()));
		counts.put("docks with a shipwright", String.valueOf(b.docks.stream().filter(d -> d.getShipwright() != null).count()));
	}

	/** Optional: the banks a route may start at, the lines with a tile to use them from. */
	private void putBanks()
	{
		BankList banks = com.bestpath.tool.bank.ToolBanks.list();
		if (banks.getBanks().isEmpty())
		{
			return;
		}
		List<BankList.Bank> usable = new ArrayList<>();
		for (BankList.Bank bk : banks.getBanks())
		{
			if (bk.getUse().length > 0)
			{
				usable.add(bk);
			}
		}
		byte[] bt = BankListWriter.write(new BankList(usable), PluginDataWriter.header("the banks a route may start at")
			+ "\nFrom data/banks/banks.tsv (gradlew -p tool banks): bank booths"
			+ " and chests from cache " + b.cacheId + ", bank names, needs and bankers\nfrom the OSRS Wiki at development time"
			+ " (CC BY-NC-SA 3.0; pages and revisions in data/banks/banks.tsv).").getBytes(StandardCharsets.UTF_8);
		files.put(PluginData.BANKS, bt);
		counts.put("bank lines", String.valueOf(usable.size()));
	}

	private void putPlaces()
	{
		if (b.places == null || b.places.isEmpty())
		{
			return;
		}
		files.put(PluginData.PLACES, PlaceKindsWriter.write(b.places, PluginDataWriter.header("the places by kind for \"go to nearest\"")
			+ "\nFrom cache " + b.cacheId + ": the map"
			+ " icons placed on the map (marker objects, config archive 35); names from " + MapPlaces.NAMES + ".\n"
			+ PlaceKinds.BANK + " has no tiles here: the plugin takes the banks from " + PluginData.BANKS + ".")
			.getBytes(StandardCharsets.UTF_8));
		int places = 0;
		for (PlaceKinds.Kind k : b.places.getKinds())
		{
			places += k.getTiles().length;
		}
		counts.put("place kinds (go to nearest)", String.valueOf(b.places.getKinds().size()));
		counts.put("places (map icons)", String.valueOf(places));
	}

	/** Optional: the Slayer tasks and their places. */
	private void putSlayer()
	{
		if (b.slayer == null)
		{
			return;
		}
		byte[] sl = SlayerPlacesWriter.write(b.slayer.data, PluginDataWriter.header("the Slayer tasks and where to kill them")
			+ "\nMasters' numbers, tasks, bosses and Konar areas from cache "
			+ b.cacheId + " DB tables 113-116; master names and places from the OSRS Wiki at development time\n(CC BY-NC-SA 3.0;"
			+ " data/wiki/slayer-places.tsv and slayer-masters.tsv, page and revision per place).").getBytes(StandardCharsets.UTF_8);
		files.put(PluginData.SLAYER, sl);
		counts.put("slayer places", String.valueOf(b.slayer.places));
		System.out.println(com.bestpath.tool.slayer.SlayerExport.describe(b.slayer, sl.length));
		for (String p : b.slayer.problems)
		{
			System.out.println("  SLAYER: " + p);
		}
	}

	private void putBoats()
	{
		if (b.boats == null)
		{
			return;
		}
		files.put(PluginData.BOATS, BoatTableWriter.write(b.boats, PluginDataWriter.header("what the game's boat varbits mean")
			+ "\nFrom cache " + b.cacheId + " DB tables 166 (boat kinds),"
			+ " 175-181 and 185 (parts, facilities), 164 (their stats), 147 (boat statuses), 187 (name words), 194 (docks).")
			.getBytes(StandardCharsets.UTF_8));
		counts.put("boat kinds", String.valueOf(BoatTableDiagnostics.kinds(b.boats).size()));
	}

	private void putClues()
	{
		if (b.clues == null || b.clues.steps <= 0)
		{
			return;
		}
		byte[] cl = ClueDataWriter.write(b.clues.data, PluginDataWriter.header("clue steps")
			+ "\nFrom data/clues/clue-steps.tsv (cache " + b.cacheId + ") with data/clues/clue-overrides.tsv"
			+ " (manual corrections, needs) and, for kill steps,"
			+ " data/wiki/clue-kill-spawns.tsv\n(OSRS Wiki, CC BY-NC-SA 3.0; pages in the wiki column).")
			.getBytes(StandardCharsets.UTF_8);
		files.put(PluginData.CLUES, cl);
		counts.put("clue steps", String.valueOf(b.clues.steps));
		counts.put("clue steps with a place", String.valueOf(b.clues.withPlace));
		counts.put("clue kill areas (Wiki)", String.valueOf(b.clues.killAreas));
		System.out.println(ClueExport.describe(b.clues, cl.length));
		for (String p : b.clues.problems)
		{
			System.out.println("  CLUE CHECK: " + p);
		}
	}

	private void putWorldMaps()
	{
		if (b.worldMaps == null)
		{
			return;
		}
		files.put(PluginData.WORLD_MAPS, WorldMapsWriter.write(b.worldMaps, PluginDataWriter.header("where the game's world maps draw each area")
			+ "\nFrom cache " + b.cacheId
			+ " index 19 (world map details and composite map). Per map: area = game x, y, width, height, shift x, y (drawn ="
			+ " game + shift), lowest and highest plane.").getBytes(StandardCharsets.UTF_8));
		int rects = 0;
		for (int m = 0; m < b.worldMaps.mapCount(); m++)
		{
			rects += WorldMapsDiagnostics.rectangleCount(b.worldMaps, m);
		}
		counts.put("world maps", String.valueOf(b.worldMaps.mapCount()));
		counts.put("world map areas", String.valueOf(rects));
	}

	private void putWilderness()
	{
		if (b.wilderness == null)
		{
			return;
		}
		files.put(com.bestpath.core.data.WildernessZones.FILE, WildernessZonesWriter.write(b.wilderness, PluginDataWriter.header(
			"the Wilderness zones (levels 1-20, 21-30, deeper)") + "\nFrom cache "
			+ b.cacheId + " client script " + com.bestpath.tool.extract.WildernessScript.LEVEL_SCRIPT + " (run for every tile of its"
			+ " boxes; any plane), safe areas (data/manual/wilderness-safe.tsv) left out.")
			.getBytes(StandardCharsets.UTF_8));
		counts.put("wilderness rectangles", String.valueOf(WildernessZonesDiagnostics.rectangleCount(b.wilderness)));
	}

	private void putCharting()
	{
		if (b.charting == null || b.charting.isEmpty())
		{
			return;
		}
		files.put(PluginData.CHARTING, b.chartingKept != null ? b.chartingKept
			: com.bestpath.core.data.ChartingTasksWriter.write(b.charting, com.bestpath.tool.extract.ChartingExtractor.comment(b.cacheId))
				.getBytes(StandardCharsets.UTF_8));
		counts.put("sea charting tasks", String.valueOf(b.charting.getTasks().size()));
	}

	/**
	 * The walks file ({@link com.bestpath.core.path.WalkTables}): the other files read back as the plugin reads them,
	 * the plugin's members search on them, and every table worked out for every transport in the data (each player's
	 * transports are some of these, whatever they have done; the tables never depend on the player).
	 */
	static byte[] walkTables(Map<String, byte[]> files, PluginExportCli.Built b, Map<String, String> counts) throws IOException
	{
		long t0 = System.nanoTime();
		Map<String, byte[]> withManifest = new LinkedHashMap<>(files);
		withManifest.put(PluginData.MANIFEST, PluginDataWriter.write(files, b.cacheId, b.build, "walk tables", new LinkedHashMap<>()));
		PluginData data = PluginData.read(name -> withManifest.containsKey(name) ? new java.io.ByteArrayInputStream(withManifest.get(name)) : null);
		PluginRouter router = new PluginRouter(data, PluginExportCli.CACHE_BUDGET);
		List<Transport> all = new ArrayList<>();
		for (com.bestpath.core.data.PlayerTransports.Candidate c : new com.bestpath.core.data.PlayerTransports(data.getTransports(), data.getTables())
			.everything(PlayerStateBuilder.maxed(), null, null).getCandidates())
		{
			all.add(c.getTransport());
		}
		Pathfinder pf = PluginRouterDiagnostics.pathfinder(router);
		com.bestpath.core.path.WalkTables tables = WalkTablesBuilder.build(pf, all);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		WalkTablesWriter.write(tables, out);
		System.out.printf(Locale.ROOT, "walk tables: %s for %,d transports; %,d bytes, made in %.1f s%n", WalkTablesDiagnostics.describe(tables), all.size(), out.size(),
			(System.nanoTime() - t0) / 1e9);
		counts.put("walk tables (sub-regions / end tiles)", WalkTablesDiagnostics.endCounts(tables));
		return out.toByteArray();
	}

	/** Writes every file to {@code outDir}, printing each one's size, the total and the transports by kind. */
	private void writeAll(File outDir, String fingerprint) throws IOException
	{
		long total = 0;
		for (Map.Entry<String, byte[]> e : files.entrySet())
		{
			Files.write(new File(outDir, e.getKey()).toPath(), e.getValue());
			total += e.getValue().length;
			System.out.printf(Locale.ROOT, "  wrote %-26s %,10d bytes%n", e.getKey(), e.getValue().length);
		}
		System.out.printf(Locale.ROOT, "wrote %,d bytes to %s (fingerprint %s)%n", total, outDir.getAbsolutePath(),
			fingerprint);
		System.out.printf(Locale.ROOT, "transports: %,d confirmed %s; left out %s%n", b.records.size(), byKind, b.leftOut);
	}
}
