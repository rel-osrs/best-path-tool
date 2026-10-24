package com.bestpath.tool.cache;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;

/**
 * Fingerprints (CRC32 checksums) of what each region's map pictures are drawn from, so only regions whose
 * fingerprint changed are drawn again. Used by {@link LoadedCache}, which holds the cache lock meanwhile.
 */
final class MapFingerprints
{
	private MapFingerprints()
	{
	}

	/** See {@link LoadedCache#regionFingerprints()}. */
	static Map<Integer, Long> regions(Store store, RegionLoader regionLoader, Set<Integer> regionIds) throws IOException
	{
		Index configs = store.getIndex(IndexType.CONFIGS);
		long globalCrc = globalCrc(store, configs);
		Map<Integer, Integer> objectCrc = objectCrcs(store, configs);
		Index maps = store.getIndex(IndexType.MAPS);
		Map<Integer, Long> out = new HashMap<>();
		for (int id : regionIds)
		{
			CRC32 c = new CRC32();
			putLong(c, globalCrc);
			putInt(c, maps.getArchive(id).getCrc());
			Region r = regionLoader.findRegionForRegionCoordinates(id >> 8, id & 0xFF);
			if (r != null)
			{
				TreeSet<Integer> objs = new TreeSet<>();
				for (Location l : r.getLocations())
				{
					objs.add(l.getId());
				}
				for (int o : objs)
				{
					putInt(c, o);
					putInt(c, objectCrc.getOrDefault(o, 0));
				}
			}
			out.put(id, c.getValue());
		}
		return out;
	}

	/** The checksum of what every region shares: the render version, the floor definitions and the textures. */
	private static long globalCrc(Store store, Index configs)
	{
		CRC32 global = new CRC32();
		putInt(global, LoadedCache.RENDER_VERSION);
		putInt(global, configs.getArchive(ConfigType.UNDERLAY.getId()).getCrc());
		putInt(global, configs.getArchive(ConfigType.OVERLAY.getId()).getCrc());
		Index textures = store.getIndex(IndexType.TEXTURES);
		if (textures != null)
		{
			for (Archive a : textures.getArchives())
			{
				putInt(global, a.getCrc());
			}
		}
		return global.getValue();
	}

	/** The checksum of every object definition, by object id. */
	private static Map<Integer, Integer> objectCrcs(Store store, Index configs) throws IOException
	{
		Map<Integer, Integer> objectCrc = new HashMap<>();
		Archive objArchive = configs.getArchive(ConfigType.OBJECT.getId());
		for (FSFile f : objArchive.getFiles(store.getStorage().loadArchive(objArchive)).getFiles())
		{
			CRC32 c = new CRC32();
			c.update(f.getContents());
			objectCrc.put(f.getFileId(), (int) c.getValue());
		}
		return objectCrc;
	}

	/** See {@link LoadedCache#modelFingerprints()}; {@code base} is the world-map fingerprint of every region. */
	static Map<Integer, Long> models(Store store, RegionLoader regionLoader, Map<Integer, ObjectDefinition> objects,
		Map<Integer, Long> base)
	{
		Map<Integer, Long> own = ownModelPrints(store, regionLoader, objects, base);
		Map<Integer, Long> out = new HashMap<>();
		for (int id : own.keySet())
		{
			CRC32 c = new CRC32();
			putInt(c, LoadedCache.MODEL_RENDER_VERSION);
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					Long n = own.get((((id >> 8) + dx) << 8) | ((id & 0xFF) + dy));
					putLong(c, n == null ? 0 : n);
				}
			}
			out.put(id, c.getValue());
		}
		return out;
	}

	/** Per region: its world-map fingerprint with the 3D models of the objects placed in it. */
	private static Map<Integer, Long> ownModelPrints(Store store, RegionLoader regionLoader, Map<Integer, ObjectDefinition> objects,
		Map<Integer, Long> base)
	{
		Index models = store.getIndex(IndexType.MODELS);
		Map<Integer, Long> own = new HashMap<>();
		for (Map.Entry<Integer, Long> e : base.entrySet())
		{
			int id = e.getKey();
			CRC32 c = new CRC32();
			putLong(c, e.getValue());
			Region r = regionLoader.findRegionForRegionCoordinates(id >> 8, id & 0xFF);
			if (r != null)
			{
				TreeSet<Integer> modelIds = new TreeSet<>();
				for (Location l : r.getLocations())
				{
					ObjectDefinition d = objects.get(l.getId());
					if (d != null && d.getObjectModels() != null)
					{
						for (int m : d.getObjectModels())
						{
							modelIds.add(m);
						}
					}
				}
				for (int m : modelIds)
				{
					Archive a = models.getArchive(m);
					putInt(c, m);
					putInt(c, a == null ? 0 : a.getCrc());
				}
			}
			own.put(id, c.getValue());
		}
		return own;
	}

	private static void putInt(CRC32 c, int v)
	{
		c.update(v >>> 24);
		c.update(v >>> 16);
		c.update(v >>> 8);
		c.update(v);
	}

	private static void putLong(CRC32 c, long v)
	{
		putInt(c, (int) (v >>> 32));
		putInt(c, (int) v);
	}
}
