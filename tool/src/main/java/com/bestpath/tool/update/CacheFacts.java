package com.bestpath.tool.update;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.extract.MapPlaces;
import com.bestpath.tool.transport.CandidateExtractor;
import com.bestpath.tool.transport.TransportCandidate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.region.Location;

/**
 * What one game cache says about everything the game update comparison looks at. Read from a cache by {@link #read}; the
 * comparison ({@link CacheCompare}) then needs no cache open, so the two caches are read one after the other. Tests
 * fill the fields by hand. Fixed rules only: the same cache always gives the same facts. Tool only.
 */
public final class CacheFacts
{
	/** One object definition, as far as the data uses it: its name, menu options (all its forms), size. */
	public static final class ObjectFact
	{
		public final String name;
		/** Lower case, as {@link CandidateExtractor#options}, sorted. */
		public final List<String> options;
		public final int sizeX;
		public final int sizeY;

		public ObjectFact(String name, Collection<String> options, int sizeX, int sizeY)
		{
			this.name = name == null ? "" : name;
			List<String> o = new ArrayList<>(options);
			Collections.sort(o);
			this.options = Collections.unmodifiableList(o);
			this.sizeX = sizeX;
			this.sizeY = sizeY;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof ObjectFact))
			{
				return false;
			}
			ObjectFact f = (ObjectFact) o;
			return name.equals(f.name) && options.equals(f.options) && sizeX == f.sizeX && sizeY == f.sizeY;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(name, options, sizeX, sizeY);
		}
	}

	public int cacheId = -1;
	public int build = -1;
	public String date = "";
	/** Index id, then archive id, then the archive's CRC ({@link LoadedCache#archiveCrcs}). */
	public Map<Integer, Map<Integer, Integer>> archives = new TreeMap<>();
	/** The walls and blocked tiles per region and plane ({@link FlagGrid#blockHashes}), from the cache alone. */
	public Map<Integer, Long> blocks = new HashMap<>();
	/** Movement flags ({@link CollisionFlags#MOVEMENT_MASK}) of the tiles the data uses, by {@link #tile}, cache alone. */
	public Map<Long, Integer> tileFlags = new HashMap<>();
	/** The doors and gates open by default, by {@link #door}. */
	public Set<Long> doors = new HashSet<>();
	/** Every placed object, packed by {@link #loc}, sorted. */
	public long[] locs = new long[0];
	public Map<Integer, ObjectFact> objects = new HashMap<>();
	/** Transport-like objects ({@link CandidateExtractor}), by {@link TransportCandidate#id()}. */
	public Map<String, TransportCandidate> candidates = new HashMap<>();
	public Map<Integer, String> items = new TreeMap<>();
	public Map<Integer, String> npcs = new TreeMap<>();
	/** Per map icon kind: the tiles it is placed on ({@link MapPlaces#find}). */
	public Map<Integer, int[]> mapIcons = new TreeMap<>();
	/**
	 * Every tile's movement flags in some regions (those whose walls changed, read in a second pass by
	 * {@link #readRegions}): region id, then plane * 4096 + (x & 63) * 64 + (y & 63).
	 */
	public Map<Integer, int[]> regionFlags = new TreeMap<>();

	/** A tile key: x, y and plane. */
	public static long tile(int x, int y, int plane)
	{
		return (long) x << 22 | (long) y << 2 | plane;
	}

	public static int tileX(long t)
	{
		return (int) (t >> 22);
	}

	public static int tileY(long t)
	{
		return (int) ((t >> 2) & 0xFFFFF);
	}

	public static int tilePlane(long t)
	{
		return (int) (t & 3);
	}

	/** A door key: the door's object id and tile. */
	public static long door(int x, int y, int plane, int objectId)
	{
		return (long) objectId << 40 | tile(x, y, plane);
	}

	/** A placed object, packed so that sorting groups an object id's placements by x, then y. */
	public static long loc(int id, int x, int y, int z, int type, int rot)
	{
		return (long) id << 39 | (long) x << 24 | (long) y << 9 | (long) z << 7 | (long) type << 2 | rot;
	}

	public static int locId(long l)
	{
		return (int) (l >>> 39);
	}

	public static int locX(long l)
	{
		return (int) ((l >>> 24) & 0x7FFF);
	}

	public static int locY(long l)
	{
		return (int) ((l >>> 9) & 0x7FFF);
	}

	public static int locZ(long l)
	{
		return (int) ((l >>> 7) & 3);
	}

	/** The placement's shape type (0-3 walls, 10 / 11 objects, 22 floor decoration). */
	public static int locType(long l)
	{
		return (int) ((l >>> 2) & 31);
	}

	public static int locRot(long l)
	{
		return (int) (l & 3);
	}

	/** The region id of a tile. */
	public static int region(int x, int y)
	{
		return (x >> 6) << 8 | (y >> 6);
	}

	/** The placements of object {@code id} with x in [x0, x1] (any y), packed as {@link #loc}, from {@link #locs}. */
	public long[] placements(int id, int x0, int x1)
	{
		long lo = loc(id, Math.max(0, x0), 0, 0, 0, 0);
		long hi = loc(id, Math.min(0x7FFF, x1), 0x7FFF, 3, 31, 3);
		int i = Arrays.binarySearch(locs, lo);
		int from = i < 0 ? -i - 1 : i;
		int to = from;
		while (to < locs.length && locs[to] <= hi)
		{
			to++;
		}
		return Arrays.copyOfRange(locs, from, to);
	}

	/** Reads every tile's movement flags in {@code regions} from the cache's own movement grid. */
	public void readRegions(FlagGrid base, Set<Integer> regions)
	{
		for (int region : regions)
		{
			int x0 = region >> 8 << 6;
			int y0 = (region & 0xFF) << 6;
			int[] flags = new int[4 * 4096];
			for (int p = 0; p < 4; p++)
			{
				for (int dx = 0; dx < 64; dx++)
				{
					for (int dy = 0; dy < 64; dy++)
					{
						flags[p * 4096 + dx * 64 + dy] = base.get(x0 + dx, y0 + dy, p) & CollisionFlags.MOVEMENT_MASK;
					}
				}
			}
			regionFlags.put(region, flags);
		}
	}

	/**
	 * Reads the facts from an open cache. {@code base} is the cache's own movement grid (no manual changes,
	 * {@code CollisionExtractor.loadOrExtract}); {@code tiles} the tiles the data uses.
	 */
	public static CacheFacts read(LoadedCache cache, FlagGrid base, Collection<Long> tiles, Consumer<String> log)
		throws IOException
	{
		CacheFacts f = new CacheFacts();
		f.cacheId = cache.getInfo().getOpenrs2Id();
		f.build = cache.getInfo().getBuild();
		f.date = cache.getInfo().getTimestamp();
		f.archives = cache.archiveCrcs();
		f.blocks = base.blockHashes();
		for (long t : tiles)
		{
			f.tileFlags.put(t, base.get(tileX(t), tileY(t), tilePlane(t)) & CollisionFlags.MOVEMENT_MASK);
		}
		for (int[] d : base.getDoors())
		{
			f.doors.add(door(d[0], d[1], d[2], d[3]));
		}
		log.accept("  objects ...");
		for (ObjectDefinition d : cache.objectDefinitions())
		{
			f.objects.put(d.getId(), new ObjectFact(CandidateExtractor.displayName(cache, d),
				CandidateExtractor.options(cache, d), d.getSizeX(), d.getSizeY()));
		}
		List<Long> locs = new ArrayList<>();
		cache.forEachRegion(r ->
		{
			for (Location l : r.getLocations())
			{
				locs.add(loc(l.getId(), l.getPosition().getX(), l.getPosition().getY(), l.getPosition().getZ(),
					l.getType(), l.getOrientation() & 3));
			}
		});
		f.locs = locs.stream().mapToLong(Long::longValue).sorted().toArray();
		log.accept("  " + f.locs.length + " placed objects; transport-like objects ...");
		for (TransportCandidate c : CandidateExtractor.extract(cache))
		{
			f.candidates.put(c.id(), c);
		}
		log.accept("  items, NPCs, map icons ...");
		f.items = new TreeMap<>(cache.itemNames());
		for (net.runelite.cache.definitions.NpcDefinition n : cache.npcDefinitions())
		{
			if (n.getName() != null && !n.getName().equals("null"))
			{
				f.npcs.put(n.getId(), n.getName());
			}
		}
		f.mapIcons = MapPlaces.find(cache);
		return f;
	}
}
