package com.bestpath.tool.route;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.FreeLandDiagnostics;
import com.bestpath.core.collision.FreeLandWriter;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Skill;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.extract.ExportBuilder;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.extract.ToolMap;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Developer aid: draws free-to-play land, the tiles a player on a free world may walk on.
 * {@code gradlew -p tool freeLand --args="<cacheDir> <surface runs> <places file> <out free-land.tsv> <report>"}.
 * Surface runs: {@code F y x1 x2} lines (drawn from the Wiki's free-to-play map). Places file, one per line:
 * {@code fill x y plane [x1 y1 x2 y2] | name} (every tile walkable from there, inside the box if given, is free),
 * {@code cut x1 y1 x2 y2 | name} (members only), {@code rect x1 y1 x2 y2 | name} (free). The report lists every one
 * of our transports a free player could take from free land to land not marked free: each is a free place still
 * missing or the way into members land.
 */
public final class FreeLandCli
{
	private FreeLandCli()
	{
	}

	public static void main(String[] args) throws Exception
	{
		try (LoadedCache cache = LoadedCache.open(new File(args[0])))
		{
			// doors open: a shut door has a transport across it, and the free places behind it stay free
			ToolMap toolMap = ToolMap.walking(cache, ToolSettings.findWorkspace(), ed -> ed.type != com.bestpath.tool.edit.Edit.Type.DOOR);
			EditStore edits = toolMap.edits;
			FlagGrid grid = toolMap.grid;
			ManualState manual = toolMap.manual;
			CollisionMap map = ExportBuilder.fullCollision(grid, cache.getRegionIds(), 0, 0);

			FreeLand.Builder b = new FreeLand.Builder();
			addSurfaceRuns(new File(args[1]), b);
			List<String> placeLines = addPlaces(new File(args[2]), map, b);
			cutMembersPlaces(new File(args[2]), b);
			FreeLand land = b.build();
			writeLand(new File(args[3]), land, placeLines);
			System.out.printf(Locale.ROOT, "%,d free tiles -> %s%n", FreeLandDiagnostics.tileCount(land), args[3]);
			int exits = writeExits(new File(args[4]), manual, land, map);
			System.out.printf(Locale.ROOT, "%d ways off the free land -> %s%n", exits, args[4]);
		}
	}

	/** The surface runs: {@code F y x1 x2} lines. */
	private static void addSurfaceRuns(File runs, FreeLand.Builder b) throws IOException
	{
		for (String line : Files.readAllLines(runs.toPath(), StandardCharsets.UTF_8))
		{
			String[] w = line.trim().split("\\s+");
			if (w.length == 4 && w[0].equals("F"))
			{
				b.set(Integer.parseInt(w[2]), Integer.parseInt(w[1]), Integer.parseInt(w[3]), Integer.parseInt(w[1]), true);
			}
		}
	}

	/** The places file's fill and rect lines; returns a line per place for the file's header. */
	private static List<String> addPlaces(File places, CollisionMap map, FreeLand.Builder b) throws IOException
	{
		List<String> placeLines = new ArrayList<>();
		for (String line : Files.readAllLines(places.toPath(), StandardCharsets.UTF_8))
		{
			String s = line.trim();
			if (s.isEmpty() || s.startsWith("#"))
			{
				continue;
			}
			String name = s.contains("|") ? s.substring(s.indexOf('|') + 1).trim() : "";
			String[] w = (s.contains("|") ? s.substring(0, s.indexOf('|')) : s).trim().split("\\s+");
			int[] n = new int[w.length - 1];
			for (int i = 1; i < w.length; i++)
			{
				n[i - 1] = Integer.parseInt(w[i]);
			}
			switch (w[0])
			{
				case "fill":
				{
					int[] box = n.length >= 7 ? new int[]{n[3], n[4], n[5], n[6]} : null;
					int count = fill(map, b, n[0], n[1], n[2], box);
					placeLines.add(String.format(Locale.ROOT, "fill %d,%d,%d: %,d tiles | %s", n[0], n[1], n[2], count, name));
					break;
				}
				case "rect":
					b.set(n[0], n[1], n[2], n[3], true);
					placeLines.add("rect | " + name);
					break;
				case "cut":
					break; // after every fill (below)
				default:
					throw new IllegalArgumentException("unknown place line: " + line);
			}
		}
		return placeLines;
	}

	/** The places file's cut lines (members only), after every fill. */
	private static void cutMembersPlaces(File places, FreeLand.Builder b) throws IOException
	{
		for (String line : Files.readAllLines(places.toPath(), StandardCharsets.UTF_8))
		{
			String s = line.trim();
			if (s.startsWith("cut "))
			{
				String[] w = s.substring(0, s.contains("|") ? s.indexOf('|') : s.length()).trim().split("\\s+");
				b.set(Integer.parseInt(w[1]), Integer.parseInt(w[2]), Integer.parseInt(w[3]), Integer.parseInt(w[4]), false);
			}
		}
	}

	private static void writeLand(File file, FreeLand land, List<String> placeLines) throws IOException
	{
		try (PrintWriter out = new PrintWriter(file, "UTF-8"))
		{
			out.println("# Free-to-play land (the free areas are marked; the rest of the map is members only).");
			out.println("# Tiles a player on a free world can stand on, the same on every plane. One row per line: <y> <x1>-<x2> ...");
			out.println("# Changed in the Data Tool with the Free land tool (F); those changes are kept in changes.jsonl on top of this file.");
			out.println("# Drawn with the tool freeLand: the surface from the Wiki's \"Free-to-play world map.png\"");
			out.println("# (uploaded 2026-07-17; members land tinted red), compared tile by tile with our own map pictures; dungeons and");
			out.println("# other places reached from free land added from the Wiki (Free-to-play rev 15359656 and each place's page):");
			for (String p : placeLines)
			{
				out.println("#   " + p);
			}
			out.print(FreeLandWriter.write(land));
		}
	}

	/**
	 * The report: what could take a free player who has everything else off the free land (transports from free land
	 * to land not marked free, usable that way). Returns how many ways.
	 */
	private static int writeExits(File file, ManualState manual, FreeLand land, CollisionMap map) throws IOException
	{
		PlayerState.Builder pb = PlayerStateBuilder.allItems(new PlayerState.Builder("Free player").allUnlocked(true), true);
		for (Skill s : Skill.values())
		{
			pb.level(s, 99);
		}
		PlayerState free = pb.build();
		int exits = 0;
		try (PrintWriter rep = new PrintWriter(file, "UTF-8"))
		{
			for (TransportRecord r : manual.getTransports().values())
			{
				if (r.kind == TransportRecord.Kind.TELEPORT)
				{
					continue;
				}
				boolean s = land.isFree(r.startX, r.startY), e = land.isFree(r.endX, r.endY);
				String way = null;
				if (s && !e && usable(r.requirements, free))
				{
					way = "there";
				}
				else if (!s && e && r.kind == TransportRecord.Kind.TWO_WAY
					&& usable(r.requirementsBack != null ? r.requirementsBack : r.requirements, free))
				{
					way = "back";
				}
				if (way == null)
				{
					continue;
				}
				exits++;
				int[] from = way.equals("there") ? new int[]{r.startX, r.startY, r.startPlane} : new int[]{r.endX, r.endY, r.endPlane};
				int[] to = way.equals("there") ? new int[]{r.endX, r.endY, r.endPlane} : new int[]{r.startX, r.startY, r.startPlane};
				int size = fill(map, null, to[0], to[1], to[2], null);
				rep.printf(Locale.ROOT, "%s\t%s\t%d,%d,%d -> %d,%d,%d\tregion %d\treach %,d tiles\t%s\t%s%n", r.id, r.name, from[0], from[1], from[2],
					to[0], to[1], to[2], (to[0] >> 6) << 8 | (to[1] >> 6), size, r.requirements == null ? "" : r.requirements,
					r.network == null ? "" : r.network);
			}
		}
		return exits;
	}

	private static boolean usable(String line, PlayerState p)
	{
		if (line == null || line.isBlank())
		{
			return true;
		}
		try
		{
			return com.bestpath.tool.req.ItemGroupFile.parse(line).check(p).getOutcome() != Check.Outcome.NOT_MET;
		}
		catch (Exception e)
		{
			return true;
		}
	}

	/** Marks (if {@code b} is given) every tile walkable from (x, y, plane), inside the box if given; returns the count (at most 400,000). */
	static int fill(CollisionMap map, FreeLand.Builder b, int x, int y, int plane, int[] box)
	{
		java.util.HashSet<Long> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
		queue.add(new int[]{x, y});
		seen.add(key(x, y));
		while (!queue.isEmpty() && seen.size() < 400_000)
		{
			int[] t = queue.poll();
			if (b != null)
			{
				b.set(t[0], t[1], t[0], t[1], true);
			}
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					int nx = t[0] + dx, ny = t[1] + dy;
					if ((dx != 0 || dy != 0) && map.canStep(t[0], t[1], plane, dx, dy)
						&& (box == null || nx >= Math.min(box[0], box[2]) && nx <= Math.max(box[0], box[2])
						&& ny >= Math.min(box[1], box[3]) && ny <= Math.max(box[1], box[3]))
						&& seen.add(key(nx, ny)))
					{
						queue.add(new int[]{nx, ny});
					}
				}
			}
		}
		return seen.size();
	}

	private static long key(int x, int y)
	{
		return (long) x << 32 | (y & 0xffffffffL);
	}
}
