package com.bestpath.tool.update;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.update.CacheCompare.Kind;
import com.bestpath.tool.update.CacheCompare.Report;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** The {@link CacheCompare} part about regions: where walls and placed objects changed, and whether walking did. */
final class CompareRegions
{
	private CompareRegions()
	{
	}

	/** Per region whose walls changed: the tiles whose movement flags differ (when both caches' tiles were read). */
	static Map<Integer, List<Long>> changedTiles(CacheFacts a, CacheFacts b, Set<Integer> wallRegions)
	{
		Map<Integer, List<Long>> out = new TreeMap<>();
		for (int region : wallRegions)
		{
			int[] x = a.regionFlags.get(region);
			int[] y = b.regionFlags.get(region);
			if (x == null || y == null)
			{
				continue;
			}
			List<Long> tiles = new ArrayList<>();
			for (int i = 0; i < x.length; i++)
			{
				if (x[i] != y[i])
				{
					tiles.add(CacheFacts.tile((region >> 8 << 6) + (i % 4096) / 64, ((region & 0xFF) << 6) + i % 64, i / 4096));
				}
			}
			out.put(region, tiles);
		}
		return out;
	}

	/**
	 * Whether a tile's change can change walking: not when the tile is blocked (no floor, an object, water ...) in both
	 * caches, since a blocked tile can't be stepped onto either way, whatever walls it has.
	 */
	static boolean walkChange(int before, int after)
	{
		return before != after && ((before & CollisionFlags.BLOCKED) == 0 || (after & CollisionFlags.BLOCKED) == 0);
	}

	/** The regions whose walls changed on a tile that can be walked on in either cache (or whose tiles were not read). */
	static Set<Integer> walkRegions(CacheFacts a, Set<Integer> wallRegions, Map<Integer, List<Long>> changedTiles,
		CacheFacts b)
	{
		Set<Integer> out = new TreeSet<>();
		for (int region : wallRegions)
		{
			List<Long> tiles = changedTiles.get(region);
			if (tiles == null)
			{
				out.add(region);
				continue;
			}
			for (long t : tiles)
			{
				int i = flagIndex(t);
				if (walkChange(a.regionFlags.get(region)[i], b.regionFlags.get(region)[i]))
				{
					out.add(region);
					break;
				}
			}
		}
		return out;
	}

	/** A tile's place in its region's flags (plane * 4096 + x * 64 + y within the region). */
	static int flagIndex(long t)
	{
		return CacheFacts.tilePlane(t) * 4096 + (CacheFacts.tileX(t) & 63) * 64 + (CacheFacts.tileY(t) & 63);
	}

	/**
	 * A finding per region whose walls changed (one that needs a look when walking changed in a reviewed region the
	 * you do not ignore), with its changed tiles and objects; regions where only objects changed go to the other
	 * data.
	 */
	static void regions(Report r, CacheFacts a, CacheFacts b, Set<Integer> wallRegions, Set<Integer> walkRegions,
		Map<Integer, List<Long>> changedTiles, OurData data)
	{
		Map<Integer, int[]> objects = new TreeMap<>(); // region -> {added, removed}
		Map<Integer, List<String>> objectLines = new TreeMap<>();
		objectChanges(a, b, objects, objectLines);
		for (int region : new TreeSet<>(wallRegions))
		{
			int[] n = objects.getOrDefault(region, new int[2]);
			boolean walk = walkRegions.contains(region);
			String text = "Region " + region + " (" + (region >> 8 << 6) + "," + ((region & 0xFF) << 6) + "): walls changed"
				+ (walk ? "" : " only on tiles that can't be walked on before or after (walking is the same)") + "; objects +"
				+ n[0] + " -" + n[1] + (data.ignoredRegions.contains(region) ? " (a region you ignore)" : "");
			boolean look = walk && data.reviewedRegions.contains(region) && !data.ignoredRegions.contains(region);
			List<Long> tiles = changedTiles.get(region);
			List<String> details = new ArrayList<>();
			if (tiles != null)
			{
				tileLines(a, b, region, tiles, details);
			}
			objectLines(objectLines.getOrDefault(region, Collections.emptyList()), n, details);
			long at = cardSpot(a, b, region, tiles);
			int ax = at < 0 ? (region >> 8 << 6) + 32 : CacheFacts.tileX(at);
			int ay = at < 0 ? ((region & 0xFF) << 6) + 32 : CacheFacts.tileY(at);
			int ap = at < 0 ? 0 : CacheFacts.tilePlane(at);
			long[] marks = tiles == null ? new long[0] : tiles.stream().mapToLong(Long::longValue).toArray();
			r.add(look ? Kind.REVIEWED_REGION_CHANGED : Kind.REGION_CHANGED, text, ax, ay, ap, Integer.toString(region), details,
				marks);
		}
		List<String> objectsOnly = new ArrayList<>();
		objects.forEach((region, n) ->
		{
			if (!wallRegions.contains(region))
			{
				objectsOnly.add("Region " + region + ": objects +" + n[0] + " -" + n[1] + " (walls the same)");
			}
		});
		r.other.put("Regions where only objects changed (the walls are the same)", objectsOnly);
		r.carried.add((regionCount(a, b) - wallRegions.size()) + " regions: walls the same");
	}

	/** The placements added and removed, counted and listed per region (both caches' placements are sorted). */
	private static void objectChanges(CacheFacts a, CacheFacts b, Map<Integer, int[]> objects, Map<Integer, List<String>> objectLines)
	{
		long[] x = a.locs;
		long[] y = b.locs;
		int i = 0;
		int j = 0;
		while (i < x.length || j < y.length)
		{
			if (j >= y.length || (i < x.length && x[i] < y[j]))
			{
				objectLine(objects, objectLines, a, x[i], "removed", 1);
				i++;
			}
			else if (i >= x.length || y[j] < x[i])
			{
				objectLine(objects, objectLines, b, y[j], "added", 0);
				j++;
			}
			else
			{
				i++;
				j++;
			}
		}
	}

	/** A region's detail lines for its changed tiles (at most {@link CacheCompare#REGION_LIST_MAX}). */
	private static void tileLines(CacheFacts a, CacheFacts b, int region, List<Long> tiles, List<String> details)
	{
		details.add(tiles.size() + " tiles with other walls or blocking:");
		for (int k = 0; k < tiles.size() && k < CacheCompare.REGION_LIST_MAX; k++)
		{
			long t = tiles.get(k);
			int tx = CacheFacts.tileX(t);
			int ty = CacheFacts.tileY(t);
			int tp = CacheFacts.tilePlane(t);
			int idx = flagIndex(t);
			int fa = a.regionFlags.get(region)[idx];
			int fb = b.regionFlags.get(region)[idx];
			details.add("  " + CacheCompare.tile(tx, ty, tp) + ": " + CacheCompare.describe(fa) + " -> " + CacheCompare.describe(fb)
				+ (walkChange(fa, fb) ? "" : " (blocked before and after)"));
		}
		if (tiles.size() > CacheCompare.REGION_LIST_MAX)
		{
			details.add("  ... and " + (tiles.size() - CacheCompare.REGION_LIST_MAX) + " more");
		}
	}

	/** A region's detail lines for its added and removed objects ({@code n}: {added, removed}). */
	private static void objectLines(List<String> objs, int[] n, List<String> details)
	{
		if (objs.isEmpty())
		{
			return;
		}
		details.add("Objects:");
		for (String o : objs)
		{
			details.add("  " + o);
		}
		if (n[0] + n[1] > objs.size())
		{
			details.add("  ... and " + (n[0] + n[1] - objs.size()) + " more");
		}
	}

	/** The card's spot: the first tile where walking changed, else the first changed tile; -1 for none (use the middle). */
	private static long cardSpot(CacheFacts a, CacheFacts b, int region, List<Long> tiles)
	{
		long at = -1;
		for (long t : tiles == null ? Collections.<Long>emptyList() : tiles)
		{
			if (at < 0 || walkChange(a.regionFlags.get(region)[flagIndex(t)], b.regionFlags.get(region)[flagIndex(t)])
				&& !walkChange(a.regionFlags.get(region)[flagIndex(at)], b.regionFlags.get(region)[flagIndex(at)]))
			{
				at = t;
			}
		}
		return at;
	}

	private static void objectLine(Map<Integer, int[]> counts, Map<Integer, List<String>> lines, CacheFacts f, long l,
		String what, int slot)
	{
		int region = CacheFacts.region(CacheFacts.locX(l), CacheFacts.locY(l));
		counts.computeIfAbsent(region, k -> new int[2])[slot]++;
		List<String> list = lines.computeIfAbsent(region, k -> new ArrayList<>());
		if (list.size() < CacheCompare.REGION_LIST_MAX)
		{
			CacheFacts.ObjectFact o = f.objects.get(CacheFacts.locId(l));
			list.add(what + " \"" + (o == null ? "?" : o.name) + "\" (" + CacheFacts.locId(l) + ") at "
				+ CacheCompare.tile(CacheFacts.locX(l), CacheFacts.locY(l), CacheFacts.locZ(l)));
		}
	}

	private static int regionCount(CacheFacts a, CacheFacts b)
	{
		Set<Integer> all = new HashSet<>();
		for (int k : a.blocks.keySet())
		{
			all.add(k >> 2);
		}
		for (int k : b.blocks.keySet())
		{
			all.add(k >> 2);
		}
		return all.size();
	}
}
