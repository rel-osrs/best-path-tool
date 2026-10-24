package com.bestpath.tool.extract;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.MovementRules;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Full per-tile movement flags for the whole world (client bit layout), as
 * extracted from the cache. Used for display, review
 * and to build the compact {@link com.bestpath.core.collision.CollisionMap}.
 */
public final class FlagGrid
{
	private final Map<Integer, int[]> blocks = new HashMap<>();
	private final Set<Integer> regionIds;
	/** Fast "is this region in the cache" lookup, indexed by region id. */
	private final boolean[] present = new boolean[1 << 16];
	/** Internal marker while extracting: this tile has a floor. Replaced by NO_FLOOR where absent. */
	private static final int HAS_FLOOR = 0x20000000;
	/** True once extraction has finished: new blocks then start as "no floor". */
	private boolean finished;
	/** Doors/gates treated as open: {x, y, plane, objectId, shapeType, orientation}. */
	private final java.util.List<int[]> doors = new java.util.ArrayList<>();

	public FlagGrid(Set<Integer> regionIds)
	{
		this.regionIds = regionIds;
		for (int id : regionIds)
		{
			present[id] = true;
		}
	}

	private static int key(int x, int y, int plane)
	{
		return ((x >> 6) << 8 | (y >> 6)) << 2 | plane;
	}

	/** Flags of a tile; tiles outside any cache region read as fully blocked. */
	public int get(int x, int y, int plane)
	{
		if (x < 0 || y < 0 || !present[((x >> 6) & 0xFF) << 8 | ((y >> 6) & 0xFF)] || x >= 16384 || y >= 16384)
		{
			return CollisionFlags.BLOCKED;
		}
		int[] b = blocks.get(key(x, y, plane));
		return b == null ? CollisionFlags.NO_FLOOR : b[(x & 63) << 6 | (y & 63)];
	}

	/** Adds flags to a tile (ignored outside the cache's regions). */
	public void add(int x, int y, int plane, int flags)
	{
		if (x < 0 || y < 0 || plane < 0 || plane > 3 || !present[((x >> 6) & 0xFF) << 8 | ((y >> 6) & 0xFF)] || x >= 16384 || y >= 16384)
		{
			return;
		}
		block(x, y, plane)[(x & 63) << 6 | (y & 63)] |= flags;
	}

	void markFloor(int x, int y, int plane)
	{
		add(x, y, plane, HAS_FLOOR);
	}

	/** Removes flags from a tile. */
	public void clear(int x, int y, int plane, int flags)
	{
		if (x < 0 || y < 0 || plane < 0 || plane > 3 || x >= 16384 || y >= 16384 || !present[((x >> 6) & 0xFF) << 8 | ((y >> 6) & 0xFF)])
		{
			return;
		}
		block(x, y, plane)[(x & 63) << 6 | (y & 63)] &= ~flags;
	}

	/** The flag block of a cell, created on demand (after extraction: as "no floor" everywhere). */
	private int[] block(int x, int y, int plane)
	{
		return blocks.computeIfAbsent(key(x, y, plane), k ->
		{
			int[] b = new int[4096];
			if (finished)
			{
				java.util.Arrays.fill(b, CollisionFlags.NO_FLOOR);
			}
			return b;
		});
	}

	/** After extraction: tiles without any floor become NO_FLOOR (not walkable). */
	void finishFloors()
	{
		finished = true;
		for (int[] b : blocks.values())
		{
			for (int i = 0; i < b.length; i++)
			{
				b[i] = (b[i] & HAS_FLOOR) != 0 ? b[i] & ~HAS_FLOOR : b[i] | CollisionFlags.NO_FLOOR;
			}
		}
	}

	void addDoor(int x, int y, int plane, int objectId, int type, int rot)
	{
		doors.add(new int[]{x, y, plane, objectId, type, rot});
	}

	/** Doors and gates treated as open: {x, y, plane, objectId, shapeType, orientation}. */
	public java.util.List<int[]> getDoors()
	{
		return java.util.Collections.unmodifiableList(doors);
	}

	/**
	 * A fingerprint per stored block (key regionId << 2 | plane), to find the
	 * regions an edit changed by comparing two builds (a few tens of ms).
	 */
	public Map<Integer, Long> blockHashes()
	{
		Map<Integer, Long> out = new HashMap<>();
		blocks.forEach((k, b) ->
		{
			long h = 1125899906842597L;
			for (int v : b)
			{
				h = 31 * h + v;
				h ^= h >>> 29;
			}
			out.put(k, h);
		});
		return out;
	}

	/** Region ids whose blocks differ between two fingerprints of {@link #blockHashes} (added and removed blocks too). */
	public static Set<Integer> changedRegions(Map<Integer, Long> before, Map<Integer, Long> after)
	{
		Set<Integer> out = new java.util.HashSet<>();
		after.forEach((k, h) ->
		{
			if (!h.equals(before.get(k)))
			{
				out.add(k >> 2);
			}
		});
		before.keySet().forEach(k ->
		{
			if (!after.containsKey(k))
			{
				out.add(k >> 2);
			}
		});
		return out;
	}

	/** A deep copy (the manual changes are applied to a copy; the cache's own grid stays untouched). */
	public FlagGrid copy()
	{
		FlagGrid g = new FlagGrid(regionIds);
		blocks.forEach((k, b) -> g.blocks.put(k, b.clone()));
		g.finished = finished;
		for (int[] d : doors)
		{
			g.doors.add(d.clone());
		}
		return g;
	}

	private static final int MAGIC = 0x42504647; // "BPFG"

	/** Saves the grid (gzip), so the next start can skip reading the cache. {@code version}: the extractor's. */
	public void write(java.io.OutputStream out, int version, int cacheId) throws java.io.IOException
	{
		java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(out, 1 << 16);
		java.io.DataOutputStream d = new java.io.DataOutputStream(new java.io.BufferedOutputStream(gz, 1 << 16));
		d.writeInt(MAGIC);
		d.writeInt(version);
		d.writeInt(cacheId);
		d.writeInt(regionIds.size());
		d.writeBoolean(finished);
		d.writeInt(blocks.size());
		java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(4096 * 4);
		for (Map.Entry<Integer, int[]> e : blocks.entrySet())
		{
			d.writeInt(e.getKey());
			buf.clear();
			buf.asIntBuffer().put(e.getValue());
			d.write(buf.array());
		}
		d.writeInt(doors.size());
		for (int[] door : doors)
		{
			for (int v : door)
			{
				d.writeInt(v);
			}
		}
		d.flush();
		gz.finish();
	}

	/** Reads a grid saved by {@link #write}; null if it was made by another extractor version or for another cache. */
	public static FlagGrid read(java.io.InputStream in, Set<Integer> regionIds, int version, int cacheId) throws java.io.IOException
	{
		java.io.DataInputStream d = new java.io.DataInputStream(new java.io.BufferedInputStream(
			new java.util.zip.GZIPInputStream(in, 1 << 16), 1 << 16));
		if (d.readInt() != MAGIC || d.readInt() != version || d.readInt() != cacheId || d.readInt() != regionIds.size())
		{
			return null;
		}
		FlagGrid g = new FlagGrid(regionIds);
		g.finished = d.readBoolean();
		int n = d.readInt();
		byte[] raw = new byte[4096 * 4];
		for (int i = 0; i < n; i++)
		{
			int key = d.readInt();
			d.readFully(raw);
			int[] b = new int[4096];
			java.nio.ByteBuffer.wrap(raw).asIntBuffer().get(b);
			g.blocks.put(key, b);
		}
		int doors = d.readInt();
		for (int i = 0; i < doors; i++)
		{
			int[] door = new int[6];
			for (int j = 0; j < 6; j++)
			{
				door[j] = d.readInt();
			}
			g.doors.add(door);
		}
		return g;
	}

	public MovementRules.Flags plane(int plane)
	{
		return (x, y) -> get(x, y, plane);
	}
}
