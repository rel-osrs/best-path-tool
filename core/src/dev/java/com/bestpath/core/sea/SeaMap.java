package com.bestpath.core.sea;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Which tiles are open sea (sailing water), and each sea tile's water
 * texture. Sea is on plane 0 only. The texture tells the sea areas apart: the
 * cache gives each area its own family of water textures ({@link SeaArea}).
 * Whether a sea tile is blocked (rocks, area border lines) is in the movement
 * data, not here; {@link SeaPieces} splits the sea along those.
 *
 * <p>Immutable. Regions that are one texture throughout share one array.
 */
public final class SeaMap
{
	/** Region ids with sea tiles, ascending. */
	private final int[] keys;
	/** Per region: texture + 1 per tile (0 = not sea), index localX << 6 | localY. */
	private final byte[][] tiles;
	private final int tileCount;

	private SeaMap(int[] keys, byte[][] tiles, int tileCount)
	{
		this.keys = keys;
		this.tiles = tiles;
		this.tileCount = tileCount;
	}

	/** An empty map (no sea anywhere). */
	public static SeaMap empty()
	{
		return new SeaMap(new int[0], new byte[0][], 0);
	}

	/** The area of a sea tile's water texture (as {@link #texture} gives it), or null when not sea. */
	public static SeaArea areaOf(int texture)
	{
		if (texture < 0)
		{
			return null;
		}
		if (texture == 208)
		{
			return SeaArea.WORLD_EDGE;
		}
		if (texture < 130 || texture > 189)
		{
			return SeaArea.OPEN; // the coastal shallows
		}
		switch ((texture - 130) / 5)
		{
			case 1: // 135-139
				return SeaArea.TEMPOR_TANTRUM;
			case 6: // 160-164
				return SeaArea.NORTHERN;
			case 8: // 170-174
				return SeaArea.CRYSTALLINE;
			case 9: // 175-179
				return SeaArea.FETID;
			case 10: // 180-184
				return SeaArea.KELP;
			default:
				return SeaArea.OPEN;
		}
	}

	/** The water texture of the tile, or -1 when it is not sea. */
	public int texture(int x, int y, int plane)
	{
		if (plane != 0 || x < 0 || y < 0)
		{
			return -1;
		}
		int i = Arrays.binarySearch(keys, (x >> 6) << 8 | (y >> 6));
		if (i < 0)
		{
			return -1;
		}
		return (tiles[i][(x & 63) << 6 | (y & 63)] & 0xFF) - 1;
	}

	public boolean isSea(int x, int y, int plane)
	{
		return texture(x, y, plane) >= 0;
	}

	/** Whether the region (64x64) has any sea tile. */
	public boolean hasSea(int regionId)
	{
		return Arrays.binarySearch(keys, regionId) >= 0;
	}

	public int regionCount()
	{
		return keys.length;
	}

	/** Receives a sea tile's x and y (plane 0). */
	public interface TileVisitor
	{
		void visit(int x, int y);
	}

	/** Calls the visitor for every sea tile. */
	public void forEachTile(TileVisitor visitor)
	{
		for (int k = 0; k < keys.length; k++)
		{
			int bx = (keys[k] >> 8) << 6;
			int by = (keys[k] & 0xFF) << 6;
			byte[] t = tiles[k];
			for (int i = 0; i < 4096; i++)
			{
				if (t[i] != 0)
				{
					visitor.visit(bx + (i >> 6), by + (i & 63));
				}
			}
		}
	}

	/** Region ids with sea tiles, ascending (do not change the array). */
	int[] regionIds()
	{
		return keys;
	}

	public int tileCount()
	{
		return tileCount;
	}

	/** Collects sea tiles, then {@link #build()}. Not thread-safe. */
	public static final class Builder
	{
		private final Map<Integer, byte[]> regions = new HashMap<>();

		/** Marks a plane-0 tile as sea with this water texture (0-254). */
		public Builder set(int x, int y, int texture)
		{
			if (texture < 0 || texture > 254)
			{
				throw new IllegalArgumentException("texture must be 0-254: " + texture);
			}
			regions.computeIfAbsent((x >> 6) << 8 | (y >> 6), k -> new byte[4096])[(x & 63) << 6 | (y & 63)] = (byte) (texture + 1);
			return this;
		}

		public SeaMap build()
		{
			int[] keys = regions.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
			byte[][] tiles = new byte[keys.length][];
			Map<Byte, byte[]> uniform = new HashMap<>();
			int count = 0;
			for (int i = 0; i < keys.length; i++)
			{
				byte[] t = regions.get(keys[i]);
				boolean same = true;
				for (byte b : t)
				{
					count += b != 0 ? 1 : 0;
					same &= b == t[0];
				}
				tiles[i] = same ? uniform.computeIfAbsent(t[0], k -> t) : t;
			}
			return new SeaMap(keys, tiles, count);
		}
	}
}
