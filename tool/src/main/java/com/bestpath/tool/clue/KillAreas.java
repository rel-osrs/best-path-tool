package com.bestpath.tool.clue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a kill step's NPCs are: the Wiki spawn tiles of
 * {@code data/wiki/clue-kill-spawns.tsv} as groups (one point per group of
 * spawns; the route search picks the fastest). A group is spawn
 * tiles of one Wiki area (location, map layer, plane) that lie within
 * {@link #GAP} tiles of each other, chained. The overrides drop task-only areas
 * ({@code skip-area}), put an area's tiles on the game plane
 * ({@code area-plane}) or move them onto the game's tiles ({@code area-shift}).
 * Used by the clue check and the plugin export.
 */
public final class KillAreas
{
	/** Spawn tiles at most this far apart (larger of dx, dy) belong to one group. */
	public static final int GAP = 12;

	/** One group of spawn tiles. */
	public static final class Group
	{
		public final String step;
		/** The Wiki location name ("Taverley Dungeon"). */
		public final String location;
		public final String mapId;
		/** Game plane (after an area-plane or area-shift override). */
		public final int plane;
		/** The group's spawn tiles {x, y} on the game's map (moved by an area-shift override), in file order. */
		public final List<int[]> tiles;
		/** "page@revision" of the Wiki pages behind the tiles. */
		public final Set<String> wiki;
		/** NPC names of the tiles. */
		public final Set<String> npcs;
		/** 1-based number of this group within its area, and the area's group count. */
		public final int index;
		public final int of;
		/** {dx, dy} the Wiki's tiles were moved by (area-shift), or null. */
		public final int[] shift;

		Group(String step, String location, String mapId, int plane, List<int[]> tiles, Set<String> wiki, Set<String> npcs, int index, int of,
			int[] shift)
		{
			this.step = step;
			this.location = location;
			this.mapId = mapId;
			this.plane = plane;
			this.tiles = Collections.unmodifiableList(tiles);
			this.wiki = Collections.unmodifiableSet(wiki);
			this.npcs = Collections.unmodifiableSet(npcs);
			this.index = index;
			this.of = of;
			this.shift = shift;
		}

		/** "Hellhound - Taverley Dungeon (Wiki map 20)", plus " group 2 of 3" when the area has several. */
		public String label()
		{
			return String.join(" / ", npcs) + " - " + location + " (Wiki map " + mapId + (shift != null ? ", moved " + shift[0] + "," + shift[1] : "")
				+ ")" + (of > 1 ? " group " + index + " of " + of : "");
		}

		/** The group's spawn tile nearest the middle of its tiles (a real spawn tile, never an averaged one), {x, y}. */
		public int[] middle()
		{
			double mx = 0;
			double my = 0;
			for (int[] t : tiles)
			{
				mx += t[0];
				my += t[1];
			}
			mx /= tiles.size();
			my /= tiles.size();
			int[] best = null;
			double bestD = Double.MAX_VALUE;
			for (int[] t : tiles)
			{
				double d = (t[0] - mx) * (t[0] - mx) + (t[1] - my) * (t[1] - my);
				if (d < bestD)
				{
					bestD = d;
					best = t;
				}
			}
			return best;
		}
	}

	private KillAreas()
	{
	}

	/** One spawn line: {step, npc, page, revision, location, mapID, x, y, plane}. */
	public static List<String[]> readLines(File f) throws IOException
	{
		List<String[]> out = new ArrayList<>();
		if (!f.isFile())
		{
			return out;
		}
		String[] names = null;
		for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
		{
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] w = line.split("\t", -1);
			if (names == null)
			{
				names = w;
				continue;
			}
			Map<String, String> m = new HashMap<>();
			for (int i = 0; i < names.length && i < w.length; i++)
			{
				m.put(names[i], w[i].trim());
			}
			out.add(new String[]{m.get("step"), m.get("npc"), m.get("page"), m.get("revision"), m.get("location"), m.get("mapID"), m.get("x"),
				m.get("y"), m.get("plane")});
		}
		return out;
	}

	/** step key -> its Wiki location names (for {@link ClueOverrides#problems}). */
	public static Map<String, Set<String>> locations(List<String[]> lines)
	{
		Map<String, Set<String>> out = new HashMap<>();
		for (String[] l : lines)
		{
			out.computeIfAbsent(l[0], k -> new LinkedHashSet<>()).add(l[4]);
		}
		return out;
	}

	/** step key -> its groups, overrides applied (skipped areas left out, planes fixed, areas moved). Lines without a tile are ignored. */
	public static Map<String, List<Group>> groups(List<String[]> lines, ClueOverrides overrides)
	{
		Map<String, Map<String, List<String[]>>> byArea = new LinkedHashMap<>();
		for (String[] l : lines)
		{
			if (l[0] == null || l[4] == null || !isInt(l[6]) || !isInt(l[7]) || !isInt(l[8]))
			{
				continue;
			}
			if (overrides.skipArea(l[0], l[4]))
			{
				continue;
			}
			byArea.computeIfAbsent(l[0], k -> new LinkedHashMap<>()).computeIfAbsent(l[4] + "\t" + l[5] + "\t" + l[8], k -> new ArrayList<>()).add(l);
		}
		Map<String, List<Group>> out = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, List<String[]>>> s : byArea.entrySet())
		{
			List<Group> list = new ArrayList<>();
			for (List<String[]> area : s.getValue().values())
			{
				String[] first = area.get(0);
				int plane = overrides.areaPlane(s.getKey(), first[4], Integer.parseInt(first[8]));
				int[] shift = overrides.areaShift(s.getKey(), first[4]);
				int dx = shift == null ? 0 : shift[0];
				int dy = shift == null ? 0 : shift[1];
				plane = shift == null ? plane : shift[2];
				List<int[]> tiles = new ArrayList<>();
				for (String[] l : area)
				{
					tiles.add(new int[]{Integer.parseInt(l[6]) + dx, Integer.parseInt(l[7]) + dy, tiles.size()});
				}
				List<List<int[]>> groups = group(tiles, GAP);
				int i = 0;
				for (List<int[]> g : groups)
				{
					i++;
					Set<String> wiki = new LinkedHashSet<>();
					Set<String> npcs = new LinkedHashSet<>();
					List<int[]> xy = new ArrayList<>();
					for (int[] t : g)
					{
						String[] l = area.get(t[2]);
						wiki.add(l[2] + "@" + l[3]);
						npcs.add(l[1]);
						xy.add(new int[]{t[0], t[1]});
					}
					list.add(new Group(s.getKey(), first[4], first[5], plane, xy, wiki, npcs, i, groups.size(),
						shift == null ? null : new int[]{dx, dy}));
				}
			}
			out.put(s.getKey(), list);
		}
		return out;
	}

	/**
	 * Tiles {x, y, ...} in groups: a tile joins every group it is within
	 * {@code gap} of (larger of dx, dy), merging them. Groups keep the order of
	 * their first tile; tiles keep file order within a group.
	 */
	public static List<List<int[]>> group(List<int[]> tiles, int gap)
	{
		int n = tiles.size();
		int[] parent = new int[n];
		for (int i = 0; i < n; i++)
		{
			parent[i] = i;
		}
		for (int i = 0; i < n; i++)
		{
			for (int j = 0; j < i; j++)
			{
				int[] a = tiles.get(i);
				int[] b = tiles.get(j);
				if (Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1])) <= gap)
				{
					parent[find(parent, i)] = find(parent, j);
				}
			}
		}
		Map<Integer, List<int[]>> groups = new LinkedHashMap<>();
		for (int i = 0; i < n; i++)
		{
			groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(tiles.get(i));
		}
		return new ArrayList<>(groups.values());
	}

	private static int find(int[] parent, int i)
	{
		while (parent[i] != i)
		{
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private static boolean isInt(String s)
	{
		if (s == null || s.isEmpty())
		{
			return false;
		}
		try
		{
			Integer.parseInt(s);
			return true;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	}
}
