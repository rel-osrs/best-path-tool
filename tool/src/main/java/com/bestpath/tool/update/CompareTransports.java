package com.bestpath.tool.update;

import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.update.CacheCompare.Kind;
import com.bestpath.tool.update.CacheCompare.Report;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The {@link CacheCompare} part about transports: their game objects and the ground at their ends. */
final class CompareTransports
{
	private CompareTransports()
	{
	}

	/**
	 * Each transport's game object: still placed where it was with the same menu options and name, else a finding
	 * (moved, replaced, gone, a menu option lost, a new name).
	 */
	static void objects(Report r, CacheFacts a, CacheFacts b, OurData data)
	{
		int same = 0;
		int noObject = 0;
		int notInOld = 0;
		List<String> notInOldList = new ArrayList<>();
		Map<Long, List<Long>> atTiles = locsAt(b, objectTiles(data));
		for (TransportRecord t : CacheCompare.sorted(data.transports))
		{
			if (t.objectId < 0)
			{
				noObject++;
				continue;
			}
			int id = t.objectId;
			String who = "\"" + t.name + "\" (" + t.id + ")";
			CacheFacts.ObjectFact was = a.objects.get(id);
			String what = (was == null ? "object" : was.name) + " (" + id + ")";
			// the placement the record means: on the object's footprint or next to it (a door's record names the tile
			// beside it; a big object is placed on its south-west tile)
			long oldP = match(a, id, t.objectX, t.objectY, t.objectPlane, 1);
			if (oldP < 0)
			{
				notInOld++;
				long seen = match(a, id, t.objectX, t.objectY, -1, 64);
				notInOldList.add(who + ": " + what + " at " + CacheCompare.tile(t.objectX, t.objectY, t.objectPlane) + (seen < 0 ? "; not within 64 tiles"
					: "; nearest in the old cache " + CacheCompare.tile(CacheFacts.locX(seen), CacheFacts.locY(seen), CacheFacts.locZ(seen))));
				continue;
			}
			String at = CacheCompare.tile(CacheFacts.locX(oldP), CacheFacts.locY(oldP), CacheFacts.locZ(oldP));
			if (java.util.Arrays.binarySearch(b.locs, oldP) >= 0)
			{
				same += sameObject(r, b, t, who, what, at, was) ? 1 : 0;
				continue;
			}
			objectNotThere(r, b, t, who, what, at, was, oldP, atTiles);
		}
		r.carried.add(same + " transports: their object is where it was, with the same menu options");
		if (noObject > 0)
		{
			r.notCompared.add(noObject + " transports have no game object (drawn by hand, teleports, networks): only their"
				+ " ends are compared");
		}
		if (notInOld > 0)
		{
			r.notCompared.add(notInOld + " transports name an object that is not on their tile in the old cache either:"
				+ " not a change of this update (listed under Other game data)");
		}
		r.other.put("Transports whose object was not on its tile in the old cache either", notInOldList);
	}

	/**
	 * The object is still placed where it was: true when its menu options and name are the same too, else adds a
	 * finding (a menu option lost, or a new name) and returns false.
	 */
	private static boolean sameObject(Report r, CacheFacts b, TransportRecord t, String who, String what, String at,
		CacheFacts.ObjectFact was)
	{
		CacheFacts.ObjectFact now = b.objects.get(t.objectId);
		if (was == null || now == null)
		{
			return true;
		}
		List<String> lost = new ArrayList<>(was.options);
		lost.removeAll(now.options);
		if (!lost.isEmpty())
		{
			r.add(Kind.OPTION_LOST, who + ": its " + what + " at " + at + " lost the menu option"
				+ (lost.size() > 1 ? "s " : " ") + CacheCompare.quoted(lost) + " (now " + CacheCompare.quoted(now.options) + ")",
				t.objectX, t.objectY, t.objectPlane, t.id);
			return false;
		}
		if (!was.name.equals(now.name))
		{
			r.add(Kind.OBJECT_RENAMED, who + ": its object " + t.objectId + " at " + at + " is now called \""
				+ now.name + "\" (was \"" + was.name + "\"); the step card takes the new name at the next export",
				t.objectX, t.objectY, t.objectPlane, t.id);
			return false;
		}
		return true;
	}

	/** The object is no longer placed where it was ({@code oldP}): moved, replaced by one of the same name, or gone. */
	private static void objectNotThere(Report r, CacheFacts b, TransportRecord t, String who, String what, String at,
		CacheFacts.ObjectFact was, long oldP, Map<Long, List<Long>> atTiles)
	{
		int ox = t.objectX;
		int oy = t.objectY;
		int op = t.objectPlane;
		long moved = match(b, t.objectId, CacheFacts.locX(oldP), CacheFacts.locY(oldP), CacheFacts.locZ(oldP), CacheCompare.MOVED_RANGE);
		if (moved >= 0)
		{
			int mx = CacheFacts.locX(moved);
			int my = CacheFacts.locY(moved);
			int d = Math.max(Math.abs(mx - CacheFacts.locX(oldP)), Math.abs(my - CacheFacts.locY(oldP)));
			r.add(Kind.OBJECT_MOVED, who + ": its " + what + " at " + at + (d == 0 && CacheFacts.locZ(moved) == CacheFacts.locZ(oldP)
				? " is turned or shaped differently (" + shape(oldP) + " -> " + shape(moved) + ")"
				: " is now at " + CacheCompare.tile(mx, my, CacheFacts.locZ(moved)) + " (" + d + " tiles away)"), ox, oy, op, t.id);
			return;
		}
		String replaced = sameName(b, atTiles.get(CacheFacts.tile(CacheFacts.locX(oldP), CacheFacts.locY(oldP), 0)), was,
			CacheFacts.locZ(oldP));
		if (replaced != null)
		{
			r.add(Kind.OBJECT_REPLACED, who + ": its " + what + " at " + at + " is now " + replaced, ox, oy, op, t.id);
			return;
		}
		r.add(Kind.OBJECT_GONE, who + ": its " + what + " at " + at + " is gone (no object " + t.objectId + " within "
			+ CacheCompare.MOVED_RANGE + " tiles, none of the same name on the tile)", ox, oy, op, t.id);
	}

	/** The tiles of the transports' objects and the tiles around them (a record names the object's tile or one beside it). */
	private static Set<Long> objectTiles(OurData data)
	{
		Set<Long> out = new HashSet<>();
		for (TransportRecord t : data.transports)
		{
			if (t.objectId >= 0)
			{
				for (int dx = -1; dx <= 1; dx++)
				{
					for (int dy = -1; dy <= 1; dy++)
					{
						out.add(CacheFacts.tile(t.objectX + dx, t.objectY + dy, 0));
					}
				}
			}
		}
		return out;
	}

	/** The placed objects on the given tiles (by x, y; plane 0 in the key), from one pass over every placement. */
	private static Map<Long, List<Long>> locsAt(CacheFacts f, Set<Long> tiles)
	{
		Map<Long, List<Long>> out = new HashMap<>();
		for (long l : f.locs)
		{
			long k = CacheFacts.tile(CacheFacts.locX(l), CacheFacts.locY(l), 0);
			if (tiles.contains(k))
			{
				out.computeIfAbsent(k, x -> new ArrayList<>()).add(l);
			}
		}
		return out;
	}

	/**
	 * The placement of {@code id} whose footprint is nearest x, y, at most {@code range} tiles from it, on the plane or
	 * the one above (a bridge tile; any plane when {@code plane} is -1); -1 if none. Ties: the first in sort order.
	 */
	static long match(CacheFacts f, int id, int x, int y, int plane, int range)
	{
		CacheFacts.ObjectFact o = f.objects.get(id);
		int big = o == null ? 1 : Math.max(o.sizeX, o.sizeY);
		long best = -1;
		int bestD = Integer.MAX_VALUE;
		for (long l : f.placements(id, x - range - big, x + range))
		{
			int z = CacheFacts.locZ(l);
			if (plane >= 0 && z != plane && z != plane + 1)
			{
				continue;
			}
			int[] size = footprint(o, l);
			int px = CacheFacts.locX(l);
			int py = CacheFacts.locY(l);
			int dx = Math.max(0, Math.max(px - x, x - (px + size[0] - 1)));
			int dy = Math.max(0, Math.max(py - y, y - (py + size[1] - 1)));
			int d = Math.max(dx, dy);
			if (d <= range && d < bestD)
			{
				best = l;
				bestD = d;
			}
		}
		return best;
	}

	/** The tiles a placement covers: one for walls and floor things, the object's size (turned) for the others. */
	static int[] footprint(CacheFacts.ObjectFact o, long l)
	{
		int type = CacheFacts.locType(l);
		if (o == null || type < 9 || type > 11)
		{
			return new int[]{1, 1};
		}
		boolean turned = (CacheFacts.locRot(l) & 1) == 1;
		return turned ? new int[]{o.sizeY, o.sizeX} : new int[]{o.sizeX, o.sizeY};
	}

	private static String shape(long l)
	{
		return "shape " + CacheFacts.locType(l) + ", turned " + CacheFacts.locRot(l);
	}

	/** An object with the old one's name placed on the same tile now, in words; null if none. */
	private static String sameName(CacheFacts b, List<Long> locs, CacheFacts.ObjectFact was, int plane)
	{
		if (locs == null || was == null || was.name.isEmpty() || was.name.equals("null"))
		{
			return null;
		}
		for (long l : locs)
		{
			int z = CacheFacts.locZ(l);
			CacheFacts.ObjectFact f = b.objects.get(CacheFacts.locId(l));
			if ((z == plane || z == plane + 1) && f != null && f.name.equals(was.name))
			{
				return "\"" + f.name + "\" object " + CacheFacts.locId(l) + " (menu options " + CacheCompare.quoted(f.options) + ")";
			}
		}
		return null;
	}

	/** A finding for each transport end whose ground changed. */
	static void ends(Report r, CacheFacts a, CacheFacts b, OurData data)
	{
		int same = 0;
		for (TransportRecord t : CacheCompare.sorted(data.transports))
		{
			String who = "\"" + t.name + "\" (" + t.id + ")";
			if (t.hasStart())
			{
				same += ground(r, a, b, who + ": its start", t.startX, t.startY, t.startPlane, t.id);
			}
			same += ground(r, a, b, who + ": its end", t.endX, t.endY, t.endPlane, t.id);
		}
		r.carried.add(same + " transport ends: the ground is as it was");
	}

	/** Adds a finding when the tile's movement flags differ; 1 when they are the same. */
	private static int ground(Report r, CacheFacts a, CacheFacts b, String who, int x, int y, int plane, String ref)
	{
		long k = CacheFacts.tile(x, y, plane);
		Integer fa = a.tileFlags.get(k);
		Integer fb = b.tileFlags.get(k);
		if (Objects.equals(fa, fb))
		{
			return 1;
		}
		r.add(Kind.GROUND_CHANGED, who + " at " + CacheCompare.tile(x, y, plane) + ": " + CacheCompare.describe(fa) + " -> "
			+ CacheCompare.describe(fb), x, y, plane, ref);
		return 0;
	}
}
