package com.bestpath.tool.extract;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import net.runelite.cache.region.Region;

/**
 * Finds the open sea in the cache: plane-0 tiles whose floor overlay has one
 * of the sailing water textures.
 *
 * <p>Checked in cache #2735: sea tiles are NOT blocked in the
 * cache (tile setting 0), so the walking search could cross them. The sailing
 * seas use textures 130-189 (families of about five shades, one family per
 * sea area) and 208 (the dark water beyond the world edge), about 4.5 million
 * tiles, all on plane 0. Coasts of the main sea have a ring of shallows
 * (overlays 442 and 444, old water texture 1; about 180,000 of their tiles
 * are walkable today): sea too. Rivers and lakes use other overlays (mostly
 * blocked floor) and are not sea.
 */
public final class SeaExtractor
{
	private SeaExtractor()
	{
	}

	/** Texture recorded for the coastal shallows (their overlays have the old water texture). */
	public static final int SHALLOWS = 1;

	/** Whether a floor texture is sailing water. */
	public static boolean isSeaTexture(int texture)
	{
		return texture >= 130 && texture <= 189 || texture == 208;
	}

	/** Whether a floor overlay (id as a region stores it) is the coastal shallows. */
	public static boolean isShallows(int overlay)
	{
		return overlay == 442 || overlay == 444;
	}

	/** A test on a plane-0 tile. */
	public interface TileTest
	{
		boolean test(int x, int y);
	}

	/**
	 * Makes every sea tile unwalkable (blocked floor) in the walking data: the
	 * player walks on land only and goes to sea at a dock. Tiles for which
	 * {@code keepOpen} is true stay as they are: the sea tiles made walkable by
	 * hand (the wrecked ghost ship's beach path); boats still treat them as
	 * sea. Call after the manual changes are applied and after splitting
	 * the sea into {@link com.bestpath.core.sea.SeaPieces} (which needs the sea open).
	 * Returns how many walkable tiles became blocked.
	 */
	public static int blockForWalking(FlagGrid grid, SeaMap sea, TileTest keepOpen)
	{
		int[] count = {0};
		sea.forEachTile((x, y) ->
		{
			if (keepOpen.test(x, y))
			{
				return;
			}
			if ((grid.get(x, y, 0) & CollisionFlags.BLOCKED) == 0)
			{
				count[0]++;
			}
			grid.add(x, y, 0, CollisionFlags.FLOOR);
		});
		return count[0];
	}

	/**
	 * The plane-0 ends ({x, y}) of the boat-only transports ({@code TransportRecord#largestBoat}): the
	 * water they lead to is part of the boats' grid ({@code SeaGrid.build}) even when it is cut off from the main ocean
	 * (the Wyrmscraig cave pool). In a fixed order (by transport id), so equal sets compare equal.
	 */
	public static int[][] boatEnds(java.util.Collection<com.bestpath.tool.edit.TransportRecord> transports)
	{
		java.util.List<com.bestpath.tool.edit.TransportRecord> boat = new java.util.ArrayList<>();
		for (com.bestpath.tool.edit.TransportRecord r : transports)
		{
			if (r.largestBoat > 0 && r.hasStart())
			{
				boat.add(r);
			}
		}
		boat.sort(java.util.Comparator.comparing(r -> r.id));
		java.util.List<int[]> out = new java.util.ArrayList<>();
		for (com.bestpath.tool.edit.TransportRecord r : boat)
		{
			if (r.startPlane == 0)
			{
				out.add(new int[]{r.startX, r.startY});
			}
			if (r.endPlane == 0)
			{
				out.add(new int[]{r.endX, r.endY});
			}
		}
		return out.toArray(new int[0][]);
	}

	public static SeaMap extract(LoadedCache cache) throws IOException
	{
		int[] textures = cache.overlayTextures();
		SeaMap.Builder sea = new SeaMap.Builder();
		cache.forEachRegion(r -> addRegion(r, textures, sea));
		return sea.build();
	}

	private static void addRegion(Region r, int[] textures, SeaMap.Builder sea)
	{
		for (int z = 0; z < 4; z++)
		{
			for (int lx = 0; lx < 64; lx++)
			{
				for (int ly = 0; ly < 64; ly++)
				{
					if (CollisionExtractor.collisionPlane(r, z, lx, ly) != 0)
					{
						continue; // sea is on plane 0 (under a bridge it is stored one plane up)
					}
					int ov = r.getOverlayId(z, lx, ly) & 0xFFFF;
					int tex = ov > 0 && ov < textures.length ? textures[ov] : -1;
					if (isSeaTexture(tex) || isShallows(ov))
					{
						sea.set(r.getBaseX() + lx, r.getBaseY() + ly, isShallows(ov) ? SHALLOWS : tex);
					}
				}
			}
		}
	}
}
