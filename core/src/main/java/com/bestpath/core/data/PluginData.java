package com.bestpath.core.data;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapCodec;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.WalkTables;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The map data the plugin ships, as written by the data export (format {@value #FORMAT}).
 * Pure Java, so the export can read back what it wrote and the plugin can load it from its jar. Immutable.
 *
 * <p>Files (all in one folder):
 * <ul>
 * <li>{@value #MANIFEST}: format, cache, fingerprint, each file's size and hash;</li>
 * <li>{@value #MOVEMENT}: the movement table (CollisionMapCodec format 1);</li>
 * <li>{@value #DOORS}: door steps ({@link DoorBits});</li>
 * <li>{@value #WALLS}: line-of-sight walls ({@link WallBits});</li>
 * <li>{@value #TRANSPORTS}: confirmed transports ({@link TransportLine});</li>
 * <li>{@value #ITEM_GROUPS}, {@value #RUNES}, {@value #NETWORKS}, {@value #TELEPORT_CATEGORIES}: lookup tables
 * ({@link Tables});</li>
 * <li>{@value #FREE_LAND}: free-to-play land, with hand-made changes ({@link FreeLand} text form);</li>
 * <li>{@value #CLUES} (optional): the clue steps ({@link ClueData});</li>
 * <li>{@value #ITEM_GROUP_DEFAULTS} (optional): each item group's default item ({@link Tables#itemGroupDefault});</li>
 * <li>{@value #SEA} (optional): the main ocean and the docks ({@link SeaBits}); without it the plugin has no sea
 * routes;</li>
 * <li>{@value #BOATS} (optional): what the game's boat varbits mean ({@link BoatTable}); without it the plugin
 * can't read the player's boats;</li>
 * <li>{@value #BANKS} (optional): the banks a route may start at ({@link BankList}); without it routes never start
 * at a bank;</li>
 * <li>{@value #WALKS} (optional): the search's walking tables worked out ahead of time
 * ({@link com.bestpath.core.path.WalkTables}); without it the first routes through each part of the map work
 * them out;</li>
 * <li>{@value #WORLD_MAPS} (optional): where the game's world maps draw each area ({@link WorldMaps}); without it
 * the plugin takes world map spots as game tiles;</li>
 * <li>{@value #WILDERNESS} (optional): the Wilderness zones, with where its level-20 and level-30 lines are
 * ({@link WildernessZones}); without it no tile is in the Wilderness;</li>
 * <li>{@value #PLACES} (optional): the places by kind for "go to nearest" ({@link PlaceKinds}); without it the
 * Functions tab offers no nearest places;</li>
 * <li>{@value #SLAYER} (optional): the Slayer tasks and where to kill their monsters ({@link SlayerPlaces});
 * without it the Functions tab's Slayer part has no places;</li>
 * <li>{@value #CHARTING} (optional): the sea charting tasks ({@link ChartingTasks}); without it the Functions tab
 * has no sea charting.</li>
 * </ul>
 */
public final class PluginData
{
	public static final String FORMAT = "1";
	public static final String MANIFEST = "bestpath-data.txt";
	public static final String MOVEMENT = "movement.bpcm";
	public static final String DOORS = "doors.bin";
	public static final String WALLS = "walls.bin";
	public static final String TRANSPORTS = "transports.tsv";
	public static final String ITEM_GROUPS = "item-groups.tsv";
	public static final String RUNES = "runes.tsv";
	public static final String NETWORKS = "networks.tsv";
	public static final String TELEPORT_CATEGORIES = "teleport-categories.tsv";
	public static final String FREE_LAND = "free-land.tsv";
	public static final String CLUES = "clues.tsv";
	public static final String ITEM_GROUP_DEFAULTS = "item-group-defaults.tsv";
	public static final String SEA = "sea.bin";
	public static final String BOATS = "boats.tsv";
	public static final String BANKS = BankList.FILE;
	public static final String WALKS = "walks.bin";
	public static final String WORLD_MAPS = WorldMaps.FILE;
	public static final String WILDERNESS = WildernessZones.FILE;
	public static final String PLACES = PlaceKinds.FILE;
	public static final String SLAYER = SlayerPlaces.FILE;
	public static final String CHARTING = ChartingTasks.FILE;
	/** The data files in fingerprint order. */
	public static final List<String> FILES = List.of(MOVEMENT, DOORS, WALLS, TRANSPORTS, ITEM_GROUPS, RUNES, NETWORKS,
		TELEPORT_CATEGORIES, FREE_LAND);
	/**
	 * Files the data may have or not: when the manifest lists one it must be there and match; the fingerprint covers
	 * it after {@link #FILES}, only when present.
	 */
	public static final List<String> OPTIONAL_FILES = List.of(CLUES, ITEM_GROUP_DEFAULTS, SEA, BOATS, BANKS, WALKS, WORLD_MAPS,
		WILDERNESS, PLACES, SLAYER, CHARTING);

	/** Opens a data file by name; null if it does not exist. */
	public interface Source
	{
		InputStream open(String name) throws IOException;
	}

	final Map<String, String> manifest;
	final CollisionMap map;
	final DoorMap doors;
	final WallBits walls;
	final List<TransportLine> transports;
	final Tables tables;
	final FreeLand freeLand;
	final ClueData clues;
	final SeaBits.Data sea;
	final BoatTable boats;
	final BankList banks;
	final WalkTables walks;
	final WorldMaps worldMaps;
	final WildernessZones wilderness;
	final PlaceKinds places;
	final long bytes;
	/** Set once while loading, before the data is handed out (kept out of the constructor's long list). */
	SlayerPlaces slayer = SlayerPlaces.NONE;
	/** Set once by {@link #read} before the data is shared. */
	ChartingTasks charting = ChartingTasks.NONE;

	PluginData(Map<String, String> manifest, CollisionMap map, DoorMap doors, WallBits walls,
		List<TransportLine> transports, Tables tables, FreeLand freeLand, ClueData clues, SeaBits.Data sea, BoatTable boats,
		BankList banks, WalkTables walks, WorldMaps worldMaps, WildernessZones wilderness, long bytes,
		PlaceKinds places)
	{
		this.places = places == null ? PlaceKinds.NONE : places;
		this.worldMaps = worldMaps;
		this.wilderness = wilderness == null ? WildernessZones.NONE : wilderness;
		this.banks = banks;
		this.walks = walks;
		this.sea = sea;
		this.boats = boats;
		this.manifest = Collections.unmodifiableMap(manifest);
		this.map = map;
		this.doors = doors;
		this.walls = walls;
		this.transports = Collections.unmodifiableList(transports);
		this.tables = tables;
		this.freeLand = freeLand;
		this.clues = clues;
		this.bytes = bytes;
	}

	/**
	 * Reads the data, checking the format, every file's size and hash and
	 * the fingerprint. Returns null if there is no manifest (no data
	 * exported); throws if the data is there but broken or of another format.
	 */
	public static PluginData read(Source source) throws IOException
	{
		byte[] mf = readAll(source, MANIFEST);
		if (mf == null)
		{
			return null;
		}
		Map<String, String> manifest = new LinkedHashMap<>();
		Map<String, String[]> fileLines = new LinkedHashMap<>();
		for (String line : new String(mf, StandardCharsets.UTF_8).split("\n"))
		{
			String l = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
			if (l.isEmpty() || l.startsWith("#"))
			{
				continue;
			}
			String[] w = l.split("\t", -1);
			if (w[0].equals("file") && w.length == 4)
			{
				fileLines.put(w[1], w);
			}
			else if (w.length >= 2)
			{
				manifest.put(w[0].equals("count") && w.length >= 3 ? "count " + w[1] : w[0],
					w[0].equals("count") && w.length >= 3 ? w[2] : String.join("\t", Arrays.copyOfRange(w, 1, w.length)));
			}
		}
		String format = manifest.get("format");
		if (!FORMAT.equals(format))
		{
			throw new IOException("map data format '" + format + "' is not the one this version reads (" + FORMAT + ")");
		}
		Map<String, byte[]> files = new LinkedHashMap<>();
		long total = mf.length;
		for (String name : FILES)
		{
			byte[] b = readAll(source, name);
			if (b == null)
			{
				throw new IOException("map data file missing: " + name);
			}
			String[] w = fileLines.get(name);
			if (w == null)
			{
				throw new IOException("map data manifest does not list " + name);
			}
			if (Long.parseLong(w[2]) != b.length || !hash(b).equals(w[3]))
			{
				throw new IOException("map data file " + name + " does not match the manifest (changed or damaged)");
			}
			files.put(name, b);
			total += b.length;
		}
		for (String name : OPTIONAL_FILES)
		{
			String[] w = fileLines.get(name);
			if (w == null)
			{
				continue; // an export without this file
			}
			byte[] b = readAll(source, name);
			if (b == null)
			{
				throw new IOException("map data file missing: " + name);
			}
			if (Long.parseLong(w[2]) != b.length || !hash(b).equals(w[3]))
			{
				throw new IOException("map data file " + name + " does not match the manifest (changed or damaged)");
			}
			files.put(name, b);
			total += b.length;
		}
		String fp = fingerprint(files);
		if (!fp.equals(manifest.get("fingerprint")))
		{
			throw new IOException("map data fingerprint does not match (" + fp + " vs " + manifest.get("fingerprint") + ")");
		}
		CollisionMap map = CollisionMapCodec.read(new ByteArrayInputStream(files.get(MOVEMENT)));
		DoorMap doors = DoorBits.read(new ByteArrayInputStream(files.get(DOORS)));
		WallBits walls = WallBits.read(new ByteArrayInputStream(files.get(WALLS)));
		List<TransportLine> transports = new ArrayList<>();
		for (String line : lines(files.get(TRANSPORTS)))
		{
			if (!line.isEmpty() && !line.startsWith("#"))
			{
				transports.add(TransportLine.parse(line));
			}
		}
		Tables tables = Tables.parse(lines(files.get(ITEM_GROUPS)), lines(files.get(RUNES)), lines(files.get(NETWORKS)),
			lines(files.get(TELEPORT_CATEGORIES)), files.containsKey(ITEM_GROUP_DEFAULTS)
				? lines(files.get(ITEM_GROUP_DEFAULTS)) : List.of());
		FreeLand freeLand = FreeLand.parse(lines(files.get(FREE_LAND)));
		ClueData clues = files.containsKey(CLUES) ? ClueData.parse(lines(files.get(CLUES))) : ClueData.EMPTY;
		SeaBits.Data sea = files.containsKey(SEA) ? SeaBits.read(new ByteArrayInputStream(files.get(SEA))) : null;
		BoatTable boats = files.containsKey(BOATS) ? BoatTable.parse(lines(files.get(BOATS))) : BoatTable.EMPTY;
		BankList banks = files.containsKey(BANKS) ? BankList.parse(new String(files.get(BANKS), StandardCharsets.UTF_8)) : BankList.NONE;
		WalkTables walks = files.containsKey(WALKS)
			? WalkTables.read(new ByteArrayInputStream(files.get(WALKS))) : null;
		WorldMaps worldMaps = files.containsKey(WORLD_MAPS) ? WorldMaps.parse(lines(files.get(WORLD_MAPS))) : WorldMaps.NONE;
		WildernessZones wilderness = files.containsKey(WILDERNESS) ? WildernessZones.parse(lines(files.get(WILDERNESS)))
			: WildernessZones.NONE;
		PlaceKinds places = files.containsKey(PLACES) ? PlaceKinds.parse(new String(files.get(PLACES), StandardCharsets.UTF_8))
			: PlaceKinds.NONE;
		PluginData data = new PluginData(manifest, map, doors, walls, transports, tables, freeLand, clues, sea, boats, banks, walks,
			worldMaps, wilderness, total, places);
		data.slayer = files.containsKey(SLAYER) ? SlayerPlaces.parse(new String(files.get(SLAYER), StandardCharsets.UTF_8))
			: SlayerPlaces.NONE;
		data.charting = files.containsKey(CHARTING) ? ChartingTasks.parse(lines(files.get(CHARTING))) : ChartingTasks.NONE;
		return data;
	}

	/** The sea charting tasks ({@link ChartingTasks#NONE} for an export without {@value #CHARTING}). */
	public ChartingTasks getCharting()
	{
		return charting;
	}

	/** The Slayer tasks and their places ({@link SlayerPlaces#NONE} for an export without {@value #SLAYER}). */
	public SlayerPlaces getSlayer()
	{
		return slayer;
	}

	/** The Wilderness zones ({@link WildernessZones#NONE} for an export without {@value #WILDERNESS}). */
	public WildernessZones getWilderness()
	{
		return wilderness;
	}

	/** The lines of a UTF-8 text file (without line breaks). */
	public static List<String> lines(byte[] text)
	{
		List<String> out = new ArrayList<>();
		for (String line : new String(text, StandardCharsets.UTF_8).split("\n", -1))
		{
			out.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
		}
		if (!out.isEmpty() && out.get(out.size() - 1).isEmpty())
		{
			out.remove(out.size() - 1);
		}
		return out;
	}

	private static byte[] readAll(Source source, String name) throws IOException
	{
		try (InputStream in = source.open(name))
		{
			return in == null ? null : in.readAllBytes();
		}
	}

	/** SHA-256 of the bytes, first 16 hex digits. */
	public static String hash(byte[] b)
	{
		return hex(sha256().digest(b));
	}

	/**
	 * One number for all data files (in {@link #FILES} order, then the {@link #OPTIONAL_FILES} that are present):
	 * SHA-256 over names, sizes and bytes, first 16 hex digits.
	 */
	public static String fingerprint(Map<String, byte[]> files)
	{
		MessageDigest md = sha256();
		List<String> names = new ArrayList<>(FILES);
		for (String name : OPTIONAL_FILES)
		{
			if (files.containsKey(name))
			{
				names.add(name);
			}
		}
		for (String name : names)
		{
			byte[] b = files.get(name);
			md.update(name.getBytes(StandardCharsets.UTF_8));
			md.update((byte) 0);
			md.update(Long.toString(b == null ? -1 : b.length).getBytes(StandardCharsets.UTF_8));
			md.update((byte) 0);
			if (b != null)
			{
				md.update(b);
			}
		}
		return hex(md.digest());
	}

	private static MessageDigest sha256()
	{
		try
		{
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static String hex(byte[] d)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8; i++)
		{
			sb.append(String.format("%02x", d[i] & 0xFF));
		}
		return sb.toString();
	}

	public String getFingerprint()
	{
		return manifest.get("fingerprint");
	}

	public CollisionMap getMap()
	{
		return map;
	}

	public DoorMap getDoors()
	{
		return doors;
	}

	public WallBits getWalls()
	{
		return walls;
	}

	public List<TransportLine> getTransports()
	{
		return transports;
	}

	public Tables getTables()
	{
		return tables;
	}

	public FreeLand getFreeLand()
	{
		return freeLand;
	}

	/** The clue steps ({@link ClueData#EMPTY} for an export without clues.tsv). */
	public ClueData getClues()
	{
		return clues;
	}

	/** The main ocean and the docks, or null for an export without the sea file (no sea routes). */
	public SeaBits.Data getSea()
	{
		return sea;
	}

	/** What the game's boat varbits mean ({@link BoatTable#EMPTY} for an export without the boat file). */
	public BoatTable getBoats()
	{
		return boats;
	}

	/** The banks a route may start at; {@link BankList#NONE} when the export has none. */
	public BankList getBanks()
	{
		return banks;
	}

	/** The places by kind for "go to nearest" ({@link PlaceKinds#NONE} for an export without {@value #PLACES}). */
	public PlaceKinds getPlaces()
	{
		return places;
	}

	/** The search's walking tables made ahead of time, or null for an export without the walks file. */
	public WalkTables getWalks()
	{
		return walks;
	}

	/** Where the game's world maps draw each area ({@link WorldMaps#NONE} for an export without {@value #WORLD_MAPS}). */
	public WorldMaps getWorldMaps()
	{
		return worldMaps;
	}

	/** "format 1, cache 2740 build 241, fingerprint 0123456789abcdef, 2,223 cells, 4,621 transports" for logs. */
	public String describe()
	{
		return String.format(Locale.ROOT, "format %s, cache %s, fingerprint %s, %,d cells, %,d transports, %,d clue steps, %s",
			FORMAT, String.valueOf(manifest.get("cache")).replace('\t', ' ').replaceFirst(" ", " build "), getFingerprint(),
			map.size(), transports.size(), clues.size(), sea == null ? "no sea"
				: String.format(Locale.ROOT, "sea %,d tiles and %d docks", sea.getGrid().size(), sea.getDocks().size()));
	}
}
