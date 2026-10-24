package com.bestpath.tool.extract;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.DockDiagnostics;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.region.Location;

/**
 * Finds the sailing docks in the cache and pairs each dock's buoy with its
 * gangplank or mooring point.
 *
 * <p>Checked in cache #2735: DB table 194 (SailingDock) lists 61
 * docks (id, name, Sailing level, quest as a row of table 0) but no places.
 * The objects carry no dock id either, so the pairing uses the id pattern:
 * buoy = 59769 + dock id and land object (a multiloc that shows a gangplank
 * or a mooring point) = 59835 + dock id for docks 0-58; buoys 62395/62396 and
 * land objects 62397/62398 for docks 59/60. Every land object found lies
 * within 12 tiles of its buoy. Gangplanks stand on piers stored one plane up
 * over the sea (bridged), with one walkable pier tile next to them; mooring
 * points are 3x3 objects on the shore, partly on sea tiles.
 */
public final class DockExtractor
{
	private DockExtractor()
	{
	}

	/** Pairs further apart than this are not trusted (the furthest in cache #2735 is about 12 tiles). */
	static final int MAX_PAIR_DISTANCE = 20;

	/** What was found: docks (one per placed gangplank or mooring point) and anything odd. */
	public static final class Result
	{
		public final List<Dock> docks;
		public final List<String> notes;

		Result(List<Dock> docks, List<String> notes)
		{
			this.docks = docks;
			this.notes = notes;
		}
	}

	static int buoyId(int dock)
	{
		return dock <= 58 ? 59769 + dock : 62395 + dock - 59;
	}

	static int landId(int dock)
	{
		return dock <= 58 ? 59835 + dock : 62397 + dock - 59;
	}

	/** Where an object is placed: south-west tile and collision plane. */
	private static final class Placed
	{
		final int x;
		final int y;
		final int plane;

		Placed(int x, int y, int plane)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
		}
	}

	/**
	 * @param grid the cache's movement data (sea not blocked for walking yet)
	 */
	public static Result extract(LoadedCache cache, FlagGrid grid, SeaMap sea) throws IOException
	{
		Map<Integer, Object[][]> quests = cache.dbRowsById(0);
		List<Object[][]> rows = cache.dbRows(194);
		Map<Integer, List<Placed>> buoys = new HashMap<>();
		Map<Integer, List<Placed>> lands = new HashMap<>();
		findPlaced(cache, rows, buoys, lands);
		List<Dock> docks = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		for (Object[][] row : rows)
		{
			addDocks(cache, grid, sea, row, quests, buoys.getOrDefault((Integer) row[0][0], List.of()),
				lands.getOrDefault((Integer) row[0][0], List.of()), docks, notes);
		}
		docks.sort((a, b) -> a.getId() != b.getId() ? Integer.compare(a.getId(), b.getId())
			: DockDiagnostics.landY(a) != DockDiagnostics.landY(b) ? Integer.compare(DockDiagnostics.landY(a), DockDiagnostics.landY(b)) : Integer.compare(DockDiagnostics.landX(a), DockDiagnostics.landX(b)));
		return new Result(docks, notes);
	}

	/** Every placed buoy and land object of the listed docks, by dock id. */
	private static void findPlaced(LoadedCache cache, List<Object[][]> rows, Map<Integer, List<Placed>> buoys,
		Map<Integer, List<Placed>> lands) throws IOException
	{
		Map<Integer, Integer> dockOfBuoy = new HashMap<>();
		Map<Integer, Integer> dockOfLand = new HashMap<>();
		for (Object[][] row : rows)
		{
			int id = (Integer) row[0][0];
			dockOfBuoy.put(buoyId(id), id);
			dockOfLand.put(landId(id), id);
		}
		cache.forEachRegion(r ->
		{
			for (Location l : r.getLocations())
			{
				Integer buoy = dockOfBuoy.get(l.getId());
				Integer land = dockOfLand.get(l.getId());
				if (buoy == null && land == null)
				{
					continue;
				}
				int x = l.getPosition().getX();
				int y = l.getPosition().getY();
				int plane = CollisionExtractor.collisionPlane(r, l.getPosition().getZ(), x - r.getBaseX(), y - r.getBaseY());
				if (plane < 0)
				{
					continue;
				}
				(buoy != null ? buoys : lands).computeIfAbsent(buoy != null ? buoy : land, k -> new ArrayList<>()).add(new Placed(x, y, plane));
			}
		});
	}

	/** One dock row: a dock for each placed land object, paired with the nearest buoy (or a note why not). */
	private static void addDocks(LoadedCache cache, FlagGrid grid, SeaMap sea, Object[][] row, Map<Integer, Object[][]> quests,
		List<Placed> bs, List<Placed> ls, List<Dock> docks, List<String> notes)
	{
		int id = (Integer) row[0][0];
		String name = String.valueOf(row[1][0]);
		int level = row[4] == null ? 1 : (Integer) row[4][0];
		String quest = null;
		if (row[5] != null)
		{
			Object[][] q = quests.get((Integer) row[5][0]);
			quest = q != null && q[2] != null ? String.valueOf(q[2][0]) : "quest row " + row[5][0];
		}
		ObjectDefinition landDef = cache.object(landId(id));
		ObjectDefinition buoyDef = cache.object(buoyId(id));
		Dock.Kind kind = kindOf(cache, landDef);
		if (ls.isEmpty() || bs.isEmpty() || kind == null)
		{
			notes.add("dock " + id + " " + name + ": " + (kind == null ? "land object is not a gangplank or mooring point" : ls.isEmpty() ? "no gangplank or mooring point placed" : "no buoy placed"));
			return;
		}
		int landSize = landDef.getSizeX();
		for (Placed l : ls)
		{
			Placed best = nearest(l, bs);
			if (dist(l, best) > MAX_PAIR_DISTANCE)
			{
				notes.add("dock " + id + " " + name + ": land object at " + l.x + ", " + l.y + " has no buoy within " + MAX_PAIR_DISTANCE + " tiles");
				continue;
			}
			List<int[]> landTiles = landTiles(grid, sea, l.x, l.y, l.plane, landSize);
			if (landTiles.isEmpty())
			{
				notes.add("dock " + id + " " + name + ": no walkable land tile next to the land object at " + l.x + ", " + l.y);
			}
			docks.add(new Dock(id, name, level, quest, kind, l.x, l.y, l.plane, landSize, best.x, best.y, buoyDef.getSizeX(), landTiles));
		}
	}

	/** The nearest buoy of the same dock (Prifddinas has a second copy far away). */
	private static Placed nearest(Placed l, List<Placed> bs)
	{
		Placed best = null;
		for (Placed b : bs)
		{
			if (best == null || dist(l, b) < dist(l, best))
			{
				best = b;
			}
		}
		return best;
	}

	/**
	 * Drops copies of a dock that are off the main ocean when the same dock is
	 * also on it (the Prifddinas copy far north, in the
	 * duplicated water of the dungeon area, cannot be reached). A dock whose
	 * only copy is off the main ocean (Wyrmscraig Cavern) stays.
	 */
	public static List<Dock> withoutDuplicates(List<Dock> docks, com.bestpath.core.sea.SeaPieces pieces)
	{
		java.util.Set<Integer> onMain = new java.util.HashSet<>();
		for (Dock d : docks)
		{
			if (DockDiagnostics.onMainOcean(d, pieces))
			{
				onMain.add(d.getId());
			}
		}
		List<Dock> out = new ArrayList<>();
		for (Dock d : docks)
		{
			if (DockDiagnostics.onMainOcean(d, pieces) || !onMain.contains(d.getId()))
			{
				out.add(d);
			}
		}
		return out;
	}

	private static Dock.Kind kindOf(LoadedCache cache, ObjectDefinition multi)
	{
		if (multi == null || multi.getConfigChangeDest() == null)
		{
			return null;
		}
		for (int child : multi.getConfigChangeDest())
		{
			ObjectDefinition d = child >= 0 ? cache.object(child) : null;
			if (d != null && "Gangplank".equals(d.getName()))
			{
				return Dock.Kind.GANGPLANK;
			}
			if (d != null && "Mooring point".equals(d.getName()))
			{
				return Dock.Kind.MOORING_POINT;
			}
		}
		return null;
	}

	private static int dist(Placed a, Placed b)
	{
		return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
	}

	/** Walkable, non-sea tiles touching a side (not a corner) of the size x size object at (x, y). */
	private static List<int[]> landTiles(FlagGrid grid, SeaMap sea, int x, int y, int plane, int size)
	{
		List<int[]> out = new ArrayList<>();
		for (int i = 0; i < size; i++)
		{
			int[][] sides = {{x + i, y - 1}, {x + i, y + size}, {x - 1, y + i}, {x + size, y + i}};
			for (int[] t : sides)
			{
				if ((grid.get(t[0], t[1], plane) & CollisionFlags.BLOCKED) == 0 && !sea.isSea(t[0], t[1], plane))
				{
					out.add(new int[]{t[0], t[1], plane});
				}
			}
		}
		out.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0]));
		return out;
	}
}
