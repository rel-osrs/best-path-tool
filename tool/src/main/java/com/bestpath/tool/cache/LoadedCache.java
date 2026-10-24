package com.bestpath.tool.cache;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.cache.IndexType;
import net.runelite.cache.MapImageDumper;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;

/**
 * An opened OSRS cache. NOT thread-safe: open it and use it from one thread
 * (the tool's cache worker).
 */
public final class LoadedCache implements Closeable
{
	private final File folder;
	private final CacheInfo info;
	private final Store store;
	private final Set<Integer> regionIds;
	private MapImageDumper dumper;
	private RegionLoader regionLoader;
	private volatile Map<Integer, ObjectDefinition> objects;
	private ModelMapRenderer modelRenderer;
	private net.runelite.cache.ItemManager items;
	private net.runelite.cache.SpriteManager sprites;
	private net.runelite.cache.TextureManager textures;

	private LoadedCache(File folder, CacheInfo info, Store store, Set<Integer> regionIds)
	{
		this.folder = folder;
		this.info = info;
		this.store = store;
		this.regionIds = regionIds;
	}

	/**
	 * Opens the cache in {@code dir} (or a sub-folder of it containing
	 * main_file_cache.dat2).
	 */
	public static LoadedCache open(File dir) throws IOException
	{
		File folder = findCacheFolder(dir);
		if (folder == null)
		{
			throw new IOException("No OSRS cache (main_file_cache.dat2) found in " + dir);
		}
		Store store = new Store(folder);
		store.load();
		Index maps = store.getIndex(IndexType.MAPS);
		if (maps == null)
		{
			store.close();
			throw new IOException("Cache has no map index");
		}
		if (maps.isNamed())
		{
			// Older caches encrypt map locations with per-region XTEA keys, which the tool does not support.
			store.close();
			throw new IOException("This cache uses the older encrypted map format, which the tool does not support yet");
		}
		Set<Integer> ids = new TreeSet<>();
		for (Archive a : maps.getArchives())
		{
			ids.add(a.getArchiveId());
		}
		CacheInfo info = CacheInfo.find(folder);
		if (info == null)
		{
			info = new CacheInfo(-1, -1, "", 0, "runescape");
		}
		return new LoadedCache(folder, info, store, Collections.unmodifiableSet(ids));
	}

	/** Finds a folder at or below {@code dir} (max 3 levels) containing main_file_cache.dat2. */
	public static File findCacheFolder(File dir)
	{
		return findCacheFolder(dir, 3);
	}

	private static File findCacheFolder(File dir, int depth)
	{
		if (dir == null || !dir.isDirectory())
		{
			return null;
		}
		if (new File(dir, "main_file_cache.dat2").isFile())
		{
			return dir;
		}
		if (depth == 0)
		{
			return null;
		}
		File[] children = dir.listFiles(File::isDirectory);
		if (children != null)
		{
			for (File c : children)
			{
				File f = findCacheFolder(c, depth - 1);
				if (f != null)
				{
					return f;
				}
			}
		}
		return null;
	}

	/** Prepares the map renderer (loads definitions and all regions; a few seconds). */
	public synchronized void prepareRenderer() throws IOException
	{
		if (dumper != null)
		{
			return;
		}
		regionLoader = new RegionLoader(store, region -> null);
		MapImageDumper d = new MapImageDumper(store, regionLoader);
		d.setRenderIcons(false);
		d.setRenderLabels(false);
		// Opaque rendering: in transparent mode the renderer writes floor colours with
		// zero alpha, so grass, paths and water vanish. We make only "no data" black
		// transparent afterwards (see renderRegion).
		d.setTransparency(false);
		d.load();
		dumper = d;
		ObjectManager om = new ObjectManager(store);
		om.load();
		Map<Integer, ObjectDefinition> objs = new HashMap<>();
		for (ObjectDefinition od : om.getObjects())
		{
			objs.put(od.getId(), od);
		}
		objects = objs;
	}

	/**
	 * Renders one region on one plane at 4 px per tile (256x256), with a
	 * transparent background. Returns null if the region has no map data.
	 */
	public synchronized BufferedImage renderRegion(int regionId, int plane) throws IOException
	{
		prepareRenderer();
		Region r = regionLoader.findRegionForRegionCoordinates(regionId >> 8, regionId & 0xFF);
		if (r == null)
		{
			return null;
		}
		BufferedImage rgb = dumper.drawRegion(r, plane);
		return blackToTransparent(rgb);
	}

	/**
	 * Renders one region on one plane as seen from straight above, with every
	 * placed object's 3D model (roofs left out), at {@code pxPerTile} pixels
	 * per tile. Transparent where nothing is drawn; null if the region has no
	 * map data.
	 */
	public synchronized BufferedImage renderRegionModels(int regionId, int plane, int pxPerTile) throws IOException
	{
		prepareRenderer();
		Region r = regionLoader.findRegionForRegionCoordinates(regionId >> 8, regionId & 0xFF);
		if (r == null)
		{
			return null;
		}
		if (modelRenderer == null)
		{
			modelRenderer = new ModelMapRenderer(store, regionLoader, objects);
		}
		// floor colours only: the world-map picture without walls and map icons
		dumper.setRenderObjects(false);
		dumper.setRenderWalls(false);
		BufferedImage floor;
		BufferedImage underlay;
		try
		{
			floor = blackToTransparent(dumper.drawRegion(r, plane));
			dumper.setRenderOverlays(false);
			underlay = blackToTransparent(dumper.drawRegion(r, plane));
		}
		finally
		{
			dumper.setRenderObjects(true);
			dumper.setRenderWalls(true);
			dumper.setRenderOverlays(true);
		}
		return modelRenderer.render(r, plane, pxPerTile, floor, underlay);
	}

	/**
	 * Version of our map-picture settings. Bump it when rendering settings
	 * change, so every saved picture is redrawn.
	 */
	public static final int RENDER_VERSION = 2;

	/**
	 * Fingerprint of everything a region's pictures depend on: the region's
	 * own map data, the definitions of every object placed in it, and the
	 * floor colours and textures. Requires {@link #prepareRenderer()}.
	 *
	 * Known limitation: a change to a map-scene sprite alone is not detected;
	 * use "Redraw all map pictures" if that ever matters.
	 */
	public synchronized Map<Integer, Long> regionFingerprints() throws IOException
	{
		prepareRenderer();
		return MapFingerprints.regions(store, regionLoader, regionIds);
	}

	/**
	 * Version of the 3D map drawing ({@link ModelMapRenderer}). Bump it when
	 * the drawing changes, so every saved 3D picture is redrawn.
	 */
	public static final int MODEL_RENDER_VERSION = 1;

	/**
	 * Fingerprint of everything a region's 3D map pictures depend on: the
	 * world-map fingerprint (map data, object definitions, floors, textures),
	 * the 3D models of the objects placed in it, and the same for its eight
	 * neighbours (their objects can overhang, their heights shape the edges).
	 */
	public synchronized Map<Integer, Long> modelFingerprints() throws IOException
	{
		Map<Integer, Long> base = regionFingerprints();
		return MapFingerprints.models(store, regionLoader, objects, base);
	}

	/**
	 * Cheap check whether a plane of a region can have anything to draw:
	 * floor tiles or placed objects on it, or bridges above it.
	 */
	public synchronized boolean planeHasContent(int regionId, int plane) throws IOException
	{
		prepareRenderer();
		Region r = regionLoader.findRegionForRegionCoordinates(regionId >> 8, regionId & 0xFF);
		if (r == null)
		{
			return false;
		}
		for (Location l : r.getLocations())
		{
			if (l.getPosition().getZ() == plane)
			{
				return true;
			}
		}
		for (int x = 0; x < Region.X; x++)
		{
			for (int y = 0; y < Region.Y; y++)
			{
				if (r.getOverlayId(plane, x, y) != 0 || r.getUnderlayId(plane, x, y) != 0)
				{
					return true;
				}
				if (plane < 3 && (r.getTileSetting(plane + 1, x, y) & 2) != 0)
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Copies an opaque picture, turning pure black (no map data) transparent. */
	static BufferedImage blackToTransparent(BufferedImage rgb)
	{
		int w = rgb.getWidth();
		int h = rgb.getHeight();
		int[] px = rgb.getRGB(0, 0, w, h, null, 0, w);
		for (int i = 0; i < px.length; i++)
		{
			int c = px[i] & 0xFFFFFF;
			px[i] = c == 0 ? 0 : 0xFF000000 | c;
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		out.setRGB(0, 0, w, h, px, 0, w);
		return out;
	}

	/** Runs {@code action} for every region (terrain and placed objects loaded). Holds the cache lock meanwhile. */
	public synchronized void forEachRegion(java.util.function.Consumer<Region> action) throws IOException
	{
		prepareRenderer();
		for (Region r : regionLoader.getRegions())
		{
			action.accept(r);
		}
	}

	/** Names of the real items (no noted or placeholder copies), by id. */
	public synchronized Map<Integer, String> itemNames() throws IOException
	{
		net.runelite.cache.ItemManager items = new net.runelite.cache.ItemManager(store);
		items.load();
		Map<Integer, String> out = new java.util.TreeMap<>();
		for (net.runelite.cache.definitions.ItemDefinition d : items.getItems())
		{
			if (d.getNotedTemplate() == -1 && d.getPlaceholderTemplateId() == -1 && d.getName() != null && !d.getName().equals("null"))
			{
				out.put(d.getId(), d.getName());
			}
		}
		return out;
	}

	/** Column values of every row of a DB table (each column is an array, possibly null). */
	public synchronized java.util.List<Object[][]> dbRows(int tableId) throws IOException
	{
		net.runelite.cache.DBRowManager rows = new net.runelite.cache.DBRowManager(store);
		rows.load();
		java.util.List<Object[][]> out = new java.util.ArrayList<>();
		for (net.runelite.cache.definitions.DBRowDefinition r : rows.getRows())
		{
			if (r.getTableId() == tableId && r.getColumnValues() != null)
			{
				out.add(r.getColumnValues());
			}
		}
		return out;
	}

	/** Column values of every row of a DB table, by row id (dbrow references point at these ids). */
	public synchronized Map<Integer, Object[][]> dbRowsById(int tableId) throws IOException
	{
		net.runelite.cache.DBRowManager rows = new net.runelite.cache.DBRowManager(store);
		rows.load();
		Map<Integer, Object[][]> out = new java.util.TreeMap<>();
		for (net.runelite.cache.definitions.DBRowDefinition r : rows.getRows())
		{
			if (r.getTableId() == tableId && r.getColumnValues() != null)
			{
				out.put(r.getId(), r.getColumnValues());
			}
		}
		return out;
	}

	/**
	 * The game's world maps and where each draws its areas (index 19 WORLDMAP: archive "details" names the maps,
	 * "compositemap" lists per map its 64x64 map squares and 8x8 zones, each with game place, drawn place and planes),
	 * in the cache's map order, for the plugin's {@link com.bestpath.core.data.WorldMaps}. Build the result and print its
	 * {@code problems()}.
	 */
	public synchronized com.bestpath.core.data.WorldMapsBuilder worldMaps() throws IOException
	{
		net.runelite.cache.fs.Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.WORLDMAP);
		if (index == null)
		{
			throw new IOException("Cache has no world map index");
		}
		Archive details = index.isNamed() ? index.findArchiveByName("details") : index.getArchive(0);
		Archive composite = index.isNamed() ? index.findArchiveByName("compositemap") : index.getArchive(1);
		if (details == null || composite == null)
		{
			throw new IOException("Cache has no world map details or composite map");
		}
		Map<Integer, String> names = new java.util.HashMap<>();
		for (net.runelite.cache.fs.FSFile f : details.getFiles(storage.loadArchive(details)).getFiles())
		{
			names.put(f.getFileId(), new net.runelite.cache.definitions.loaders.WorldMapLoader().load(f.getContents(), f.getFileId()).name);
		}
		net.runelite.cache.definitions.loaders.WorldMapCompositeLoader loader =
			new net.runelite.cache.definitions.loaders.WorldMapCompositeLoader().configureForRevision(index.getRevision());
		com.bestpath.core.data.WorldMapsBuilder b = new com.bestpath.core.data.WorldMapsBuilder();
		java.util.List<net.runelite.cache.fs.FSFile> files = new java.util.ArrayList<>(composite.getFiles(storage.loadArchive(composite)).getFiles());
		files.sort(java.util.Comparator.comparingInt(net.runelite.cache.fs.FSFile::getFileId));
		for (net.runelite.cache.fs.FSFile f : files)
		{
			net.runelite.cache.definitions.WorldMapCompositeDefinition c = loader.load(f.getContents());
			int map = b.addMap(names.getOrDefault(f.getFileId(), "map " + f.getFileId()));
			for (net.runelite.cache.definitions.MapSquareDefinition s : c.getMapSquareDefinitions())
			{
				b.add(map, s.getSourceSquareX() * 64, s.getSourceSquareZ() * 64, 64, s.getDisplaySquareX() * 64,
					s.getDisplaySquareZ() * 64, s.getMinLevel(), s.getLevels());
			}
			for (net.runelite.cache.definitions.ZoneDefinition z : c.getZoneDefinitions())
			{
				b.add(map, z.getSourceSquareX() * 64 + z.getSourceZoneX() * 8, z.getSourceSquareZ() * 64 + z.getSourceZoneZ() * 8, 8,
					z.getDisplaySquareX() * 64 + z.getDisplayZoneX() * 8, z.getDisplaySquareZ() * 64 + z.getDisplayZoneZ() * 8,
					z.getMinLevel(), z.getLevels());
			}
		}
		return b;
	}

	/**
	 * The texture of every floor overlay: index = overlay id as a region stores
	 * it (1-based; 0 = no overlay), value = texture id or -1 (plain colour).
	 */
	public synchronized int[] overlayTextures() throws IOException
	{
		net.runelite.cache.OverlayManager overlays = new net.runelite.cache.OverlayManager(store);
		overlays.load();
		int max = 0;
		for (net.runelite.cache.definitions.OverlayDefinition d : overlays.getOverlays())
		{
			max = Math.max(max, d.getId());
		}
		int[] out = new int[max + 2];
		java.util.Arrays.fill(out, -1);
		for (net.runelite.cache.definitions.OverlayDefinition d : overlays.getOverlays())
		{
			out[d.getId() + 1] = d.getTexture();
		}
		return out;
	}

	/** Object definition by id, or null. Requires {@link #prepareRenderer()} (done by {@link #forEachRegion}). */
	public ObjectDefinition object(int id)
	{
		return objects == null ? null : objects.get(id);
	}

	public File getFolder()
	{
		return folder;
	}

	public CacheInfo getInfo()
	{
		return info;
	}

	/** Region ids that have map data in this cache. */
	public Set<Integer> getRegionIds()
	{
		return regionIds;
	}

	/**
	 * The item's inventory picture (36x32, transparent background, with the game's black outline and
	 * shadow), drawn from its 3D model by RuneLite's cache library; null if the item has none.
	 * The first call loads the item, sprite and texture definitions (a second or two).
	 */
	public synchronized BufferedImage itemIcon(int itemId) throws IOException
	{
		return itemIcon(itemId, 1);
	}

	/** As {@link #itemIcon(int)}, drawn as a stack of {@code quantity} (its stack look, e.g. coins; no number). */
	public synchronized BufferedImage itemIcon(int itemId, int quantity) throws IOException
	{
		loadItems();
		if (items.getItem(itemId) == null)
		{
			return null;
		}
		loadSprites();
		Index models = store.getIndex(IndexType.MODELS);
		net.runelite.cache.definitions.providers.ModelProvider modelProvider = id ->
		{
			Archive a = models.getArchive(id);
			if (a == null)
			{
				return null;
			}
			return new net.runelite.cache.definitions.loaders.ModelLoader().load(id, a.decompress(store.getStorage().loadArchive(a)));
		};
		try
		{
			// outline 1 and shadow 0x302020: as the game draws an inventory item
			return net.runelite.cache.item.ItemSpriteFactory.createSprite(items, modelProvider, sprites, textures, itemId, quantity, 1,
				0x302020, false);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private void loadSprites() throws IOException
	{
		if (sprites == null)
		{
			sprites = new net.runelite.cache.SpriteManager(store);
			sprites.load();
			textures = new net.runelite.cache.TextureManager(store);
			textures.load();
		}
	}

	/**
	 * A sprite's picture (archive {@code spriteId}, frame {@code frame}; RuneLite's gameval SpriteID numbers), with a
	 * transparent background; null if the cache has none.
	 */
	public synchronized BufferedImage spriteImage(int spriteId, int frame) throws IOException
	{
		loadSprites();
		net.runelite.cache.definitions.SpriteDefinition d = sprites.findSprite(spriteId, frame);
		return d == null || d.getWidth() <= 0 || d.getHeight() <= 0 ? null : sprites.getSpriteImage(d);
	}

	/** The item's name in the cache, or null. */
	public synchronized String itemName(int itemId) throws IOException
	{
		loadItems();
		net.runelite.cache.definitions.ItemDefinition d = items.getItem(itemId);
		return d == null ? null : d.getName();
	}

	/** An item's integer param (e.g. 623, the clue step row), or null if the item or the param is not there. */
	public synchronized Integer itemIntParam(int itemId, int paramId) throws IOException
	{
		loadItems();
		net.runelite.cache.definitions.ItemDefinition d = items.getItem(itemId);
		if (d == null || d.getParams() == null)
		{
			return null;
		}
		Object v = d.getParams().get(paramId);
		return v instanceof Integer ? (Integer) v : null;
	}

	/** One client script (index 12), or null when the cache has none with this id (e.g. the Wilderness levels). */
	public synchronized net.runelite.cache.definitions.ScriptDefinition script(int id)
	{
		try
		{
			Index index = store.getIndex(IndexType.CLIENTSCRIPT);
			Archive a = index.getArchive(id);
			if (a == null)
			{
				return null;
			}
			return new net.runelite.cache.definitions.loaders.ScriptLoader().configureForRevision(index.getRevision())
				.load(id, a.decompress(store.getStorage().loadArchive(a)));
		}
		catch (IOException e)
		{
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** Every map icon definition (config archive 35 "area": icon sprite, name, category), by id (for the "go to nearest" places). */
	public synchronized Map<Integer, net.runelite.cache.definitions.AreaDefinition> mapAreas() throws IOException
	{
		net.runelite.cache.AreaManager areas = new net.runelite.cache.AreaManager(store);
		areas.load();
		Map<Integer, net.runelite.cache.definitions.AreaDefinition> out = new java.util.TreeMap<>();
		for (net.runelite.cache.definitions.AreaDefinition a : areas.getAreas())
		{
			out.put(a.getId(), a);
		}
		return out;
	}

	/** One enum (config archive 8), or null when the cache has none with this id (e.g. menu keys). */
	public synchronized net.runelite.cache.definitions.EnumDefinition enumDefinition(int id)
	{
		byte[] b = configFile(net.runelite.cache.ConfigType.ENUM, id);
		return b == null ? null : new net.runelite.cache.definitions.loaders.EnumLoader().load(id, b);
	}

	/** One struct (config archive 34), or null when the cache has none with this id. */
	public synchronized net.runelite.cache.definitions.StructDefinition struct(int id)
	{
		byte[] b = configFile(net.runelite.cache.ConfigType.STRUCT, id);
		return b == null ? null : new net.runelite.cache.definitions.loaders.StructLoader().load(id, b);
	}

	private byte[] configFile(net.runelite.cache.ConfigType type, int id)
	{
		try
		{
			Index index = store.getIndex(IndexType.CONFIGS);
			Archive a = index.getArchive(type.getId());
			if (a == null)
			{
				return null;
			}
			for (net.runelite.cache.fs.FSFile f : a.getFiles(store.getStorage().loadArchive(a)).getFiles())
			{
				if (f.getFileId() == id)
				{
					return f.getContents();
				}
			}
			return null;
		}
		catch (IOException e)
		{
			throw new java.io.UncheckedIOException(e);
		}
	}

	/**
	 * The text constants of some client scripts, in order, without pure numbers and markup ("&lt;str&gt;"), each once;
	 * a missing script adds nothing.
	 */
	public synchronized java.util.List<String> scriptStrings(int... scriptIds) throws IOException
	{
		Index index = store.getIndex(IndexType.CLIENTSCRIPT);
		net.runelite.cache.definitions.loaders.ScriptLoader loader =
			new net.runelite.cache.definitions.loaders.ScriptLoader().configureForRevision(index.getRevision());
		java.util.Set<String> out = new java.util.LinkedHashSet<>();
		for (int id : scriptIds)
		{
			Archive a = index.getArchive(id);
			if (a == null)
			{
				continue;
			}
			net.runelite.cache.definitions.ScriptDefinition s = loader.load(id, a.decompress(store.getStorage().loadArchive(a)));
			if (s.getStringOperands() != null)
			{
				for (String t : s.getStringOperands())
				{
					if (t != null && !t.isEmpty() && !t.matches("\\d+") && !t.matches("</?\\w+>"))
					{
						out.add(t);
					}
				}
			}
		}
		return new java.util.ArrayList<>(out);
	}

	/** Every item definition, loaded once (menu texts: names, inventory and worn options). */
	public synchronized java.util.Collection<net.runelite.cache.definitions.ItemDefinition> itemDefinitions() throws IOException
	{
		loadItems();
		return items.getItems();
	}

	/** Every NPC definition (loaded on each call). */
	public synchronized java.util.Collection<net.runelite.cache.definitions.NpcDefinition> npcDefinitions() throws IOException
	{
		net.runelite.cache.NpcManager npcs = new net.runelite.cache.NpcManager(store);
		npcs.load();
		return npcs.getNpcs();
	}

	/** Every object definition (loaded with the map, see {@link #prepareRenderer()}). */
	public synchronized java.util.Collection<ObjectDefinition> objectDefinitions() throws IOException
	{
		prepareRenderer();
		return Collections.unmodifiableCollection(objects.values());
	}

	/**
	 * The checksum the cache keeps for each of its archives: index id, then archive id, then the archive's CRC. Two
	 * caches with the same CRC for an archive have the same contents there (the game update comparison).
	 */
	public synchronized Map<Integer, Map<Integer, Integer>> archiveCrcs()
	{
		Map<Integer, Map<Integer, Integer>> out = new java.util.TreeMap<>();
		for (Index index : store.getIndexes())
		{
			Map<Integer, Integer> crcs = new java.util.TreeMap<>();
			for (Archive a : index.getArchives())
			{
				crcs.put(a.getArchiveId(), a.getCrc());
			}
			out.put(index.getId(), crcs);
		}
		return out;
	}

	/** The interface definitions (loaded on each call): group, then component. */
	public synchronized net.runelite.cache.definitions.InterfaceDefinition[][] interfaceDefinitions() throws IOException
	{
		net.runelite.cache.InterfaceManager m = new net.runelite.cache.InterfaceManager(store);
		m.load();
		return m.getInterfaces();
	}

	private void loadItems() throws IOException
	{
		if (items == null)
		{
			net.runelite.cache.ItemManager m = new net.runelite.cache.ItemManager(store);
			m.load();
			m.link();
			items = m;
		}
	}

	@Override
	public void close() throws IOException
	{
		store.close();
	}
}
