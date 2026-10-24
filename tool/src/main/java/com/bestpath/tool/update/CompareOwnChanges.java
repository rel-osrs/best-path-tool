package com.bestpath.tool.update;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.update.CacheCompare.Kind;
import com.bestpath.tool.update.CacheCompare.Report;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The {@link CacheCompare} part about your own changes: tile and wall changes, shut doors, hidden and kept areas. */
final class CompareOwnChanges
{
	private CompareOwnChanges()
	{
	}

	/** A finding for each tile or wall change of yours on a tile the game changed too. */
	static void tileEdits(Report r, CacheFacts a, CacheFacts b, OurData data)
	{
		int same = 0;
		for (Edit e : data.tileEdits)
		{
			List<int[]> tiles = new ArrayList<>();
			tiles.add(new int[]{e.x, e.y});
			if (e.type == Edit.Type.WALL)
			{
				tiles.add(OurData.wallNeighbour(e));
			}
			StringBuilder changed = new StringBuilder();
			for (int[] t : tiles)
			{
				long k = CacheFacts.tile(t[0], t[1], e.plane);
				Integer fa = a.tileFlags.get(k);
				Integer fb = b.tileFlags.get(k);
				if (!Objects.equals(fa, fb))
				{
					changed.append(changed.length() == 0 ? "" : "; ").append(CacheCompare.tile(t[0], t[1], e.plane)).append(": ")
						.append(CacheCompare.describe(fa)).append(" -> ").append(CacheCompare.describe(fb));
				}
			}
			if (changed.length() == 0)
			{
				same++;
				continue;
			}
			String what = e.type == Edit.Type.WALL
				? (e.on ? "added a wall" : "removed a wall") + " on the " + new String[]{"north", "east", "south", "west"}[e.side & 3]
				+ " side of " + CacheCompare.tile(e.x, e.y, e.plane)
				: (e.on ? "blocked " : "opened ") + CacheCompare.tile(e.x, e.y, e.plane);
			r.add(Kind.EDIT_TILE_CHANGED, "You " + what + " (change " + e.id + "); the game changed it too: " + changed,
				e.x, e.y, e.plane, e.id);
		}
		r.carried.add(same + " of your tile and wall changes: the game's tiles are as they were");
	}

	/** A finding for each door set shut by you that is no longer a door there. */
	static void shutDoors(Report r, CacheFacts a, CacheFacts b, OurData data)
	{
		int same = 0;
		for (Edit e : data.shutDoors)
		{
			long k = CacheFacts.door(e.x, e.y, e.plane, e.objectId);
			if (!a.doors.contains(k) || b.doors.contains(k))
			{
				same++;
				continue;
			}
			CacheFacts.ObjectFact f = a.objects.get(e.objectId);
			r.add(Kind.SHUT_DOOR_GONE, "You set the " + (f == null ? "door" : "\"" + f.name + "\"") + " (" + e.objectId
				+ ") at " + CacheCompare.tile(e.x, e.y, e.plane) + " shut (change " + e.id + "); it is no longer a door there", e.x, e.y,
				e.plane, e.id);
		}
		r.carried.add(same + " doors you set shut: still there");
	}

	/** A finding for each hidden or kept area in a region where walking changed. */
	static void areas(Report r, Set<Integer> walkRegions, Map<Integer, List<Long>> changedTiles, OurData data)
	{
		int same = 0;
		for (Map.Entry<int[], Boolean> e : data.anchors.entrySet())
		{
			int[] t = e.getKey();
			int region = CacheFacts.region(t[0], t[1]);
			if (!walkRegions.contains(region))
			{
				same++;
				continue;
			}
			r.add(Kind.AREA_REGION_CHANGED, "The area you " + (e.getValue() ? "hid" : "kept") + " at " + CacheCompare.tile(t[0], t[1], t[2])
				+ " is in region " + region + ", whose walls changed" + nearestChange(r, t, region, changedTiles)
				+ ": check it still covers what it did", t[0], t[1], t[2], "");
		}
		r.carried.add(same + " hidden or kept areas: walking in their regions is as it was");
	}

	/** "; the nearest changed tile is ..." for an area's anchor tile {@code t}: the nearest tile where walking changed; "" if none. */
	private static String nearestChange(Report r, int[] t, int region, Map<Integer, List<Long>> changedTiles)
	{
		long best = -1;
		int bestD = Integer.MAX_VALUE;
		for (long c : changedTiles.getOrDefault(region, Collections.emptyList()))
		{
			int ci = CompareRegions.flagIndex(c);
			if (!CompareRegions.walkChange(r.before.regionFlags.get(region)[ci], r.after.regionFlags.get(region)[ci]))
			{
				continue;
			}
			int d = Math.max(Math.abs(CacheFacts.tileX(c) - t[0]), Math.abs(CacheFacts.tileY(c) - t[1]))
				+ (CacheFacts.tilePlane(c) == t[2] ? 0 : 1000);
			if (d < bestD)
			{
				bestD = d;
				best = c;
			}
		}
		if (best < 0)
		{
			return "";
		}
		return "; the nearest changed tile is " + CacheCompare.tile(CacheFacts.tileX(best), CacheFacts.tileY(best),
			CacheFacts.tilePlane(best)) + (CacheFacts.tilePlane(best) == t[2] ? " (" + bestD + " tiles away)"
			: " (on another plane)");
	}
}
