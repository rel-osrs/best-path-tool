package com.bestpath.tool.route;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.extract.ExportBuilder;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.extract.ToolMap;
import com.bestpath.tool.geo.Regions;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Developer check that transports land on real ground (not on the wrong plane):
 * every live transport's start and end, on the tool's map with all changes. An end is bad when the tile has no floor
 * on its plane, or its floor is hidden, a closed-off pocket (not kept) or in an ignored region (the plugin leaves those
 * out). For each bad end it lists the planes with good floor on the same x, y and the nearest good tile on the same
 * plane within 3 tiles. Writes {@code cache/ground-check/ground-check.tsv}; changes nothing. Ends checked by hand and
 * found fine ({@code data/manual/ground-ok.tsv}) are counted, not listed, and so are ends between two transports.
 *
 * <p>{@code gradlew -p tool groundCheck --args="<cacheDir> [x,y,plane ...]"}: each extra tile is printed with its
 * collision flags and sub-region, to see why it is not ground.
 */
public final class GroundCheckCli
{
	/** The report's columns. */
	private static final String HEADER = "id\tname\tcategory\tkind\tend\ttile\tproblem\tgood planes here\tnearest good tile same plane"
		+ "\tother end\tlast author\tlast edit\tsource";

	private final Ground g;
	/** The latest change of each transport, by id. */
	private final Map<String, Edit> last;
	/** The ends checked by hand and found fine. */
	private final Set<String> checkedOk;
	/**
	 * Per tile: the transports that land there and those that leave from there (a two-way one does both at
	 * each end). The search goes from one transport straight on to another on the same tile, ground or not.
	 */
	private final Map<String, Set<String>> arrive = new HashMap<>();
	private final Map<String, Set<String>> leave = new HashMap<>();
	/** The bad ends counted by end and problem. */
	private final Map<String, Integer> counts = new TreeMap<>();

	private GroundCheckCli(Ground g, Map<String, Edit> last, Set<String> checkedOk)
	{
		this.g = g;
		this.last = last;
		this.checkedOk = checkedOk;
	}

	public static void main(String[] args) throws Exception
	{
		try (LoadedCache cache = LoadedCache.open(new File(args[0])))
		{
			ToolMap toolMap = ToolMap.walking(cache, ToolSettings.findWorkspace());
			EditStore edits = toolMap.edits;
			FlagGrid grid = toolMap.grid;
			ManualState manual = toolMap.manual;
			CollisionMap map = ExportBuilder.fullCollision(grid, cache.getRegionIds(), 0, 0);
			CoarseGraph graph = CoarseGraph.build(map);
			Ground g = new Ground(graph, hidden(graph, manual), keptPockets(graph, manual), manual.getIgnoredRegions());
			GroundCheckCli check = new GroundCheckCli(g, lastEdits(edits), checkedByOwner(new File("data/manual/ground-ok.tsv")));
			check.addMeetings(manual.getTransports().values());
			for (int a = 1; a < args.length; a++)
			{
				// extra arguments x,y,p: that tile's collision flags and sub-region, to see why it is not ground
				String[] t = args[a].split(",");
				int x = Integer.parseInt(t[0]);
				int y = Integer.parseInt(t[1]);
				int p = Integer.parseInt(t[2]);
				System.out.printf(Locale.ROOT, "%s: flags 0x%x, opened by a manual edit %b, sub-region %d, %s%n", args[a], grid.get(x, y, p),
					manual.isOpenedByOwner(x, y, p), graph.componentAt(x, y, p), g.problem(x, y, p));
			}

			File out = new File("cache/ground-check/ground-check.tsv");
			out.getParentFile().mkdirs();
			int checked = 0;
			try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8))
			{
				w.println(HEADER);
				for (TransportRecord r : manual.getTransports().values())
				{
					checked++;
					boolean hasStart = r.kind != TransportRecord.Kind.TELEPORT;
					String[] ends = hasStart && !(r.kind == TransportRecord.Kind.NETWORK) ? new String[]{"start", "end"} : new String[]{"end"};
					for (String end : ends)
					{
						check.checkEnd(w, r, end);
					}
				}
			}
			System.out.printf(Locale.ROOT, "%,d transports checked; bad ends: %s%nwritten %s%n", checked, check.counts, out);
		}
	}

	/** The hidden sub-regions. */
	private static boolean[] hidden(CoarseGraph graph, ManualState manual)
	{
		boolean[] hidden = new boolean[graph.componentCount()];
		for (int[] a : manual.getHiddenAnchors())
		{
			int comp = graph.componentAt(a[0], a[1], a[2]);
			if (comp >= 0)
			{
				hidden[comp] = true;
			}
		}
		return hidden;
	}

	/** The closed-off pockets, without the ones kept by manual edits. */
	private static boolean[] keptPockets(CoarseGraph graph, ManualState manual)
	{
		boolean[] pockets = graph.pockets();
		for (int[] a : manual.getKeptAnchors())
		{
			int comp = graph.componentAt(a[0], a[1], a[2]);
			if (comp >= 0)
			{
				pockets[comp] = false;
			}
		}
		return pockets;
	}

	private static Map<String, Edit> lastEdits(EditStore edits)
	{
		Map<String, Edit> last = new HashMap<>();
		for (Edit e : edits.active())
		{
			if (e.type == Edit.Type.TRANSPORT)
			{
				last.put(e.transport.id, e);
			}
		}
		return last;
	}

	/** Fills {@link #arrive} and {@link #leave}. */
	private void addMeetings(Iterable<TransportRecord> records)
	{
		for (TransportRecord r : records)
		{
			String s = r.startX + "," + r.startY + "," + r.startPlane;
			String e = r.endX + "," + r.endY + "," + r.endPlane;
			arrive.computeIfAbsent(e, k -> new HashSet<>()).add(r.id);
			if (r.kind != TransportRecord.Kind.TELEPORT)
			{
				leave.computeIfAbsent(s, k -> new HashSet<>()).add(r.id);
			}
			if (r.kind == TransportRecord.Kind.TWO_WAY)
			{
				arrive.computeIfAbsent(s, k -> new HashSet<>()).add(r.id);
				leave.computeIfAbsent(e, k -> new HashSet<>()).add(r.id);
			}
		}
	}

	/** Checks one end ("start" or "end") of a transport: a bad one is counted and, unless known to be fine, written as a line. */
	private void checkEnd(PrintWriter w, TransportRecord r, String end)
	{
		boolean hasStart = r.kind != TransportRecord.Kind.TELEPORT;
		int x = end.equals("start") ? r.startX : r.endX;
		int y = end.equals("start") ? r.startY : r.endY;
		int p = end.equals("start") ? r.startPlane : r.endPlane;
		String problem = g.problem(x, y, p);
		if (problem == null)
		{
			return;
		}
		if (checkedOk.contains(r.id + "\t" + end))
		{
			// these tiles were checked by hand and found fine
			counts.merge(end + ": checked by hand (fine)", 1, Integer::sum);
			return;
		}
		if (betweenTransports(r, end, x + "," + y + "," + p) && !problem.equals("ignored region"))
		{
			// a tile between two transports works without ground (e.g. the dead tree rope lands on the Waterfall
			// door's tile); counted, not listed
			counts.merge(end + ": between transports (fine)", 1, Integer::sum);
			return;
		}
		String other = end.equals("start") ? r.endX + "," + r.endY + "," + r.endPlane
			: hasStart ? r.startX + "," + r.startY + "," + r.startPlane : "";
		Edit e = last.get(r.id);
		w.println(String.join("\t", r.id, r.name, r.category, r.kind.name(), end, x + "," + y + "," + p, problem,
			goodPlanes(x, y, p), nearestGood(x, y, p), other, e == null ? "" : e.author, e == null ? "" : e.time,
			r.source == null ? "" : r.source.replace('\t', ' ')));
		counts.merge(end + ": " + problem, 1, Integer::sum);
	}

	/**
	 * Whether another transport goes on from this end: arriving here (the end, or either end of a two-way) and going
	 * on with another transport, or the other way round.
	 */
	private boolean betweenTransports(TransportRecord r, String end, String here)
	{
		Set<String> others = new HashSet<>();
		if (end.equals("end") || r.kind == TransportRecord.Kind.TWO_WAY)
		{
			others.addAll(leave.getOrDefault(here, Set.of()));
		}
		if (end.equals("start") || r.kind == TransportRecord.Kind.TWO_WAY)
		{
			others.addAll(arrive.getOrDefault(here, Set.of()));
		}
		others.remove(r.id);
		return !others.isEmpty();
	}

	/** The other planes with good ground on the same x, y ("1,2"). */
	private String goodPlanes(int x, int y, int p)
	{
		StringBuilder planes = new StringBuilder();
		for (int q = 0; q < 4; q++)
		{
			if (q != p && g.problem(x, y, q) == null)
			{
				planes.append(planes.length() == 0 ? "" : ",").append(q);
			}
		}
		return planes.toString();
	}

	/** The nearest good tile on the same plane within 3 tiles ("x,y,p (distance)"), or "". */
	private String nearestGood(int x, int y, int p)
	{
		String near = "";
		for (int d = 1; d <= 3 && near.isEmpty(); d++)
		{
			for (int dx = -d; dx <= d && near.isEmpty(); dx++)
			{
				for (int dy = -d; dy <= d && near.isEmpty(); dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) == d && g.problem(x + dx, y + dy, p) == null)
					{
						near = (x + dx) + "," + (y + dy) + "," + p + " (" + d + ")";
					}
				}
			}
		}
		return near;
	}

	/** "id<TAB>end" of each end in the checked list (comment lines and the why column left out); none: empty. */
	static Set<String> checkedByOwner(File f) throws java.io.IOException
	{
		Set<String> ok = new HashSet<>();
		if (!f.isFile())
		{
			return ok;
		}
		for (String line : java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
		{
			String[] c = line.split("\t");
			if (!line.startsWith("#") && c.length >= 2)
			{
				ok.add(c[0].trim() + "\t" + c[1].trim());
			}
		}
		return ok;
	}

	/** Usable ground as the plugin sees it. */
	private static final class Ground
	{
		private final CoarseGraph graph;
		private final boolean[] hidden;
		private final boolean[] pockets;
		private final Set<Integer> ignored;

		Ground(CoarseGraph graph, boolean[] hidden, boolean[] pockets, Set<Integer> ignored)
		{
			this.graph = graph;
			this.hidden = hidden;
			this.pockets = pockets;
			this.ignored = ignored;
		}

		/** Why the tile is not usable ground, or null when it is. */
		String problem(int x, int y, int p)
		{
			int comp = graph.componentAt(x, y, p);
			if (comp < 0)
			{
				return "no floor";
			}
			if (ignored.contains(Regions.idOfTile(x, y)))
			{
				return "ignored region";
			}
			if (hidden[comp])
			{
				return "hidden";
			}
			return pockets[comp] ? "pocket" : null;
		}
	}
}
