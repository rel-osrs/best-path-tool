package com.bestpath.tool.update;

import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.transport.TransportCandidate;
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

/**
 * The {@link CacheCompare} parts about the game's own data: which archives changed, new and gone transport-like
 * objects, doors, map icons, and object, item and NPC definitions.
 */
final class CompareGameData
{
	private CompareGameData()
	{
	}

	/** New transport-like objects no transport uses and that were not rejected; gone ones go to the other data. */
	static void candidates(Report r, CacheFacts a, CacheFacts b, OurData data)
	{
		Set<String> used = new HashSet<>();
		for (TransportRecord t : data.transports)
		{
			if (t.objectId >= 0)
			{
				used.add(t.objectId + "@" + t.objectX + "," + t.objectY + "," + t.objectPlane);
			}
		}
		List<TransportCandidate> fresh = new ArrayList<>();
		for (TransportCandidate c : b.candidates.values())
		{
			if (!a.candidates.containsKey(c.id()) && !data.rejected.contains(c.id()) && !used.contains(c.id()))
			{
				fresh.add(c);
			}
		}
		fresh.sort((p, q) ->
		{
			int c = Integer.compare(CacheFacts.region(p.getX(), p.getY()), CacheFacts.region(q.getX(), q.getY()));
			return c != 0 ? c : p.id().compareTo(q.id());
		});
		for (TransportCandidate c : fresh)
		{
			r.add(Kind.NEW_CANDIDATE, "Region " + CacheFacts.region(c.getX(), c.getY()) + ": \"" + c.getName() + "\" ("
				+ c.getObjectId() + ", " + c.getCategory().getLabel() + ") at " + CacheCompare.tile(c.getX(), c.getY(), c.getPlane())
				+ ", options " + CacheCompare.quoted(c.getOptions()), c.getX(), c.getY(), c.getPlane(), c.id());
		}
		List<String> gone = new ArrayList<>();
		for (String id : new TreeSet<>(a.candidates.keySet()))
		{
			if (!b.candidates.containsKey(id) && !used.contains(id))
			{
				TransportCandidate c = a.candidates.get(id);
				gone.add("\"" + c.getName() + "\" (" + c.getObjectId() + ") at " + CacheCompare.tile(c.getX(), c.getY(), c.getPlane())
					+ (data.rejected.contains(id) ? " (you had rejected it)" : ""));
			}
		}
		r.other.put("Transport-like objects gone that no transport used", gone);
	}

	/** Doors and gates added and removed. */
	static void doors(Report r, CacheFacts a, CacheFacts b)
	{
		for (long k : new TreeSet<>(b.doors))
		{
			if (!a.doors.contains(k))
			{
				door(r, Kind.DOOR_ADDED, b, k);
			}
		}
		for (long k : new TreeSet<>(a.doors))
		{
			if (!b.doors.contains(k))
			{
				door(r, Kind.DOOR_REMOVED, a, k);
			}
		}
	}

	private static void door(Report r, Kind kind, CacheFacts f, long k)
	{
		int id = (int) (k >>> 40);
		long t = k & ((1L << 40) - 1);
		CacheFacts.ObjectFact o = f.objects.get(id);
		int x = CacheFacts.tileX(t);
		int y = CacheFacts.tileY(t);
		int p = CacheFacts.tilePlane(t);
		r.add(kind, "\"" + (o == null ? "?" : o.name) + "\" (" + id + ") at " + CacheCompare.tile(x, y, p), x, y, p, "");
	}

	// ---- map icons ----

	/** New map icon kinds, named kinds that are gone, and named kinds with more or fewer places. */
	static void placeKinds(Report r, CacheFacts a, CacheFacts b, OurData data)
	{
		for (Map.Entry<Integer, int[]> e : b.mapIcons.entrySet())
		{
			int kind = e.getKey();
			int[] was = a.mapIcons.get(kind);
			if (was == null)
			{
				int first = e.getValue().length == 0 ? -1 : e.getValue()[0];
				r.add(Kind.PLACE_KIND_NEW, "Map icon kind " + kind + ": " + e.getValue().length + " places"
					+ (data.placeKinds.containsKey(kind) ? " (named \"" + data.placeKinds.get(kind) + "\")"
					: "; not named yet, so not offered to players"), placeX(first), placeY(first), placeP(first),
					Integer.toString(kind));
			}
			else if (was.length != e.getValue().length && data.placeKinds.containsKey(kind))
			{
				r.add(Kind.PLACE_COUNT_CHANGED, "\"" + data.placeKinds.get(kind) + "\" (" + kind + "): " + was.length + " -> "
					+ e.getValue().length + " places", -1, -1, -1, Integer.toString(kind));
			}
		}
		for (int kind : a.mapIcons.keySet())
		{
			if (!b.mapIcons.containsKey(kind) && data.placeKinds.containsKey(kind))
			{
				int[] was = a.mapIcons.get(kind);
				int first = was.length == 0 ? -1 : was[0];
				r.add(Kind.PLACE_KIND_GONE, "\"" + data.placeKinds.get(kind) + "\" (" + kind + "): no places in the new cache",
					placeX(first), placeY(first), placeP(first), Integer.toString(kind));
			}
		}
	}

	/** A map icon place ({@code Tiles.pack}) as x, y, plane; -1 for none. */
	private static int placeX(int t)
	{
		return t < 0 ? -1 : com.bestpath.core.path.Tiles.x(t);
	}

	private static int placeY(int t)
	{
		return t < 0 ? -1 : com.bestpath.core.path.Tiles.y(t);
	}

	private static int placeP(int t)
	{
		return t < 0 ? -1 : com.bestpath.core.path.Tiles.plane(t);
	}

	// ---- other game data ----

	/** Object, item and NPC definitions that are new, removed or renamed (objects: or with other menu options). */
	static void otherData(Report r, CacheFacts a, CacheFacts b)
	{
		List<String> objects = new ArrayList<>();
		for (Map.Entry<Integer, CacheFacts.ObjectFact> e : new TreeMap<>(b.objects).entrySet())
		{
			CacheFacts.ObjectFact was = a.objects.get(e.getKey());
			CacheFacts.ObjectFact now = e.getValue();
			if (was == null)
			{
				objects.add(e.getKey() + " new: \"" + now.name + "\" " + CacheCompare.quoted(now.options));
			}
			else if (!was.name.equals(now.name) || !was.options.equals(now.options))
			{
				objects.add(e.getKey() + " \"" + was.name + "\" " + CacheCompare.quoted(was.options) + " -> \"" + now.name + "\" "
					+ CacheCompare.quoted(now.options));
			}
		}
		for (int id : new TreeSet<>(a.objects.keySet()))
		{
			if (!b.objects.containsKey(id))
			{
				objects.add(id + " removed: \"" + a.objects.get(id).name + "\"");
			}
		}
		r.other.put("Object definitions: new, removed, renamed or with other menu options (other changes, e.g. models, are"
			+ " not compared)", objects);
		r.other.put("Items: new, removed or renamed (names only)", names(a.items, b.items));
		r.other.put("NPCs: new, removed or renamed (names only)", names(a.npcs, b.npcs));
	}

	private static List<String> names(Map<Integer, String> a, Map<Integer, String> b)
	{
		List<String> out = new ArrayList<>();
		Set<Integer> ids = new TreeSet<>(a.keySet());
		ids.addAll(b.keySet());
		for (int id : ids)
		{
			String x = a.get(id);
			String y = b.get(id);
			if (x == null)
			{
				out.add(id + " new: \"" + y + "\"");
			}
			else if (y == null)
			{
				out.add(id + " removed: \"" + x + "\"");
			}
			else if (!x.equals(y))
			{
				out.add(id + " renamed: \"" + x + "\" -> \"" + y + "\"");
			}
		}
		return out;
	}

	/** Which archives of each index changed (config archives by name). */
	static void overview(Report r, CacheFacts a, CacheFacts b)
	{
		Set<Integer> indexes = new TreeSet<>(a.archives.keySet());
		indexes.addAll(b.archives.keySet());
		for (int index : indexes)
		{
			Map<Integer, Integer> x = a.archives.getOrDefault(index, Collections.emptyMap());
			Map<Integer, Integer> y = b.archives.getOrDefault(index, Collections.emptyMap());
			int changed = 0;
			int added = 0;
			int removed = 0;
			List<String> names = new ArrayList<>();
			Set<Integer> ids = new TreeSet<>(x.keySet());
			ids.addAll(y.keySet());
			for (int id : ids)
			{
				Integer p = x.get(id);
				Integer q = y.get(id);
				if (p == null)
				{
					added++;
				}
				else if (q == null)
				{
					removed++;
				}
				else if (!p.equals(q))
				{
					changed++;
				}
				else
				{
					continue;
				}
				if (index == 2)
				{
					names.add(configName(id));
				}
			}
			if (changed + added + removed == 0)
			{
				continue;
			}
			r.overview.add(indexName(index) + ": " + changed + " changed, " + added + " new, " + removed + " removed"
				+ (names.isEmpty() ? "" : " (" + String.join(", ", names) + ")"));
		}
		if (r.overview.isEmpty())
		{
			r.overview.add("Nothing: every archive has the same checksum");
		}
	}

	static String indexName(int index)
	{
		for (net.runelite.cache.IndexType t : net.runelite.cache.IndexType.values())
		{
			if (t.getNumber() == index)
			{
				return "index " + index + " " + t.name().toLowerCase(java.util.Locale.ROOT);
			}
		}
		return "index " + index;
	}

	static String configName(int archive)
	{
		for (net.runelite.cache.ConfigType t : net.runelite.cache.ConfigType.values())
		{
			if (t.getId() == archive)
			{
				return t.name().toLowerCase(java.util.Locale.ROOT);
			}
		}
		return "config " + archive;
	}
}
