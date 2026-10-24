package com.bestpath.tool.route;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.data.BankList;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.RequirementText;
import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.bank.BankExtractor;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.extract.FlagGrid;
import com.bestpath.tool.req.CacheNames;
import com.bestpath.tool.wiki.WikiBanks;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Developer aid: makes {@code data/banks/banks.tsv}, the banks a route may
 * begin at (a route may begin with a bank visit), from
 *
 * <ul>
 * <li>the cache: every placed bank booth and bank chest ({@link BankExtractor}; deposit boxes don't count);</li>
 * <li>the OSRS Wiki, gathered at development time ({@link WikiBanks}, made by {@code gradlew -p tool wikiBanks}):
 * the "List of banks" names the bank each booth or chest belongs to and what the bank needs (members, a quest,
 * a skill level), and gives the bankers' tiles at banks with no booth or chest.</li>
 * </ul>
 *
 * <p>Use tiles are the tiles a player stands on to use it, on the walking map the plugin gets (the export
 * map: no closed-off pockets, no hidden or ignored places): for a booth or chest, the tiles beside it on the
 * sides the cache lets it be used from, with no wall between; for a booth in a wall (the Grand Exchange's), its
 * own tile and the one across the wall; for a banker, the tiles beside it (or across a counter in front of it).
 *
 * <p>{@code gradlew -p tool banks --args="<cacheDir> [outFile]"}; prints what it found and what it could not place.
 * Each run is one object: it collects the lines and what to print while it goes.
 */
public final class BanksCli
{
	/** The Wiki page of the PvP worlds' bank chests: nothing from PvP worlds is used. */
	static final String PVP_WORLD_PAGE = "PvP world";

	public static final String FILE = "data/banks/banks.tsv";
	/** A booth or chest this far (tiles) outside a Wiki bank's map area still belongs to it. */
	static final int MATCH = 3;

	/** The Wiki's banks. */
	private final List<WikiBanks.Entry> wiki;
	private final Uses uses;
	private final int cacheId;
	/** The file's lines, in order: booths and chests, then bankers. */
	private final List<String[]> rows = new ArrayList<>();
	/** Booths, chests and bankers placed per Wiki bank. */
	private final Map<String, Integer> perEntry = new LinkedHashMap<>();
	private final Map<String, Integer> kinds = new TreeMap<>();
	private final List<String> unmatched = new ArrayList<>();
	private final List<String> noUse = new ArrayList<>();
	private final List<String> pvpLeftOut = new ArrayList<>();
	/** Booths and chests usable from some sides only (cache mask), and how many of them have a use tile. */
	private int maskChecked;
	private int maskOpenUsable;

	private BanksCli(List<WikiBanks.Entry> wiki, Uses uses, int cacheId)
	{
		this.wiki = wiki;
		this.uses = uses;
		this.cacheId = cacheId;
		for (WikiBanks.Entry e : wiki)
		{
			perEntry.put(e.name, 0);
		}
	}

	public static void main(String[] args) throws Exception
	{
		File ws = ToolSettings.findWorkspace();
		File cacheDir = args.length > 0 ? new File(args[0]) : com.bestpath.tool.cache.DataCache.folder(ws);
		// another output file (the preview of a new cache), else data/banks/banks.tsv
		File outFile = args.length > 1 ? new File(args[1]) : null;
		List<WikiBanks.Entry> wiki = WikiBanks.readBanks(new File(ws, WikiBanks.BANKS_FILE));
		List<WikiBanks.Banker> bankers = WikiBanks.readBankers(new File(ws, WikiBanks.BANKERS_FILE));
		PluginExportCli.Built b;
		List<BankExtractor.BankObject> objects;
		int cacheId;
		try (LoadedCache cache = LoadedCache.open(cacheDir))
		{
			CacheNames.load(cache);
			b = PluginExportCli.build(cache, ws);
			objects = BankExtractor.scan(cache);
			cacheId = cache.getInfo().getOpenrs2Id();
		}
		CoarseGraph graph = CoarseGraph.build(b.export);
		BanksCli run = new BanksCli(wiki, new Uses(b.grid, graph), cacheId);
		for (BankExtractor.BankObject o : objects)
		{
			run.addObject(o);
		}
		for (WikiBanks.Banker k : bankers)
		{
			run.addBanker(k);
		}
		String text = run.table();
		File out = outFile != null ? outFile : new File(ws, FILE);
		out.getParentFile().mkdirs();
		Files.writeString(out.toPath(), text, StandardCharsets.UTF_8);
		// read back with the plugin's reader
		BankList back = BankList.parse(text);
		run.printSummary(out, back);
	}

	/** A booth or chest's line (nothing for one in a PvP world). */
	private void addObject(BankExtractor.BankObject o)
	{
		String[] how = new String[1];
		WikiBanks.Entry e = match(wiki, o.x, o.y, o.plane, how);
		if (e != null && PVP_WORLD_PAGE.equals(e.page))
		{
			pvpLeftOut.add(e.name + " (" + o.objectId + ")");
			perEntry.remove(e.name);
			return; // nothing from PvP worlds
		}
		int[] use = uses.forObject(o);
		if (o.blockedSides != 0)
		{
			maskChecked++;
			maskOpenUsable += use.length > 0 ? 1 : 0;
		}
		String bank = e != null ? e.name : o.name + " near " + o.x + "," + o.y;
		String where = o.x + "," + o.y + "," + o.plane;
		if (e == null)
		{
			unmatched.add(o.name + " (" + o.objectId + ") at " + where);
		}
		else
		{
			perEntry.merge(e.name, 1, Integer::sum);
		}
		if (use.length == 0)
		{
			noUse.add(bank + ": " + o.name + " (" + o.objectId + ") at " + where);
		}
		List<String> notes = new ArrayList<>();
		String needs = join(o.condition, e == null ? "" : wikiNeeds(e, notes));
		kinds.merge(o.kind, 1, Integer::sum);
		rows.add(new String[]{bank, o.kind, needs, tiles(use), o.name, String.valueOf(o.x), String.valueOf(o.y),
			String.valueOf(o.plane), String.valueOf(o.objectId),
			"cache " + cacheId + " object " + o.objectId + (o.condition.isEmpty() ? "" : " (bank form: " + o.condition + ")")
				+ (e == null ? "" : "; Wiki " + e.page + " via List of banks rev " + e.revision + how[0]),
			String.join("; ", notes)});
	}

	/** A banker's line. */
	private void addBanker(WikiBanks.Banker k)
	{
		WikiBanks.Entry e = null;
		for (WikiBanks.Entry w : wiki)
		{
			if (w.name.equals(k.bank))
			{
				e = w;
			}
		}
		int[] use = uses.forBanker(k.x, k.y, k.plane);
		String where = k.x + "," + k.y + "," + k.plane;
		if (use.length == 0)
		{
			noUse.add(k.bank + ": banker " + k.npc + " at " + where);
		}
		List<String> notes = new ArrayList<>();
		String needs = e == null ? "" : wikiNeeds(e, notes);
		perEntry.merge(k.bank, 1, Integer::sum);
		kinds.merge("banker", 1, Integer::sum);
		rows.add(new String[]{k.bank, "banker", needs, tiles(use), k.npc, String.valueOf(k.x), String.valueOf(k.y),
			String.valueOf(k.plane), k.npc, "Wiki " + k.page + " rev " + k.revision + " (" + k.how + ")"
			+ (e == null ? "" : "; List of banks rev " + e.revision), String.join("; ", notes)});
	}

	/** The file's text: the header comments, the column names and every line. */
	private String table()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("# Banks a route may start at. GENERATED by `gradlew -p tool banks`; do not edit by hand.\n");
		sb.append("# Booths and chests: OSRS cache ").append(cacheId).append(" (placed objects whose menu has Bank, or Use with \"bank\" in the name;\n");
		sb.append("# deposit boxes left out). Bank names, bank requirements and bankers: OSRS Wiki at development time, CC BY-NC-SA 3.0\n");
		sb.append("# (data/wiki/LICENSE-WIKI.txt; data/wiki/banks.tsv, data/wiki/bankers.tsv), page and revision in the source column.\n");
		sb.append("# use: tiles a player stands on to use it (export walking map); needs: a requirement line (empty = nothing).\n");
		sb.append(BankList.HEADER).append("\tname\tx\ty\tplane\tobject\tsource\tnote\n");
		for (String[] r : rows)
		{
			sb.append(String.join("\t", r)).append('\n');
		}
		return sb.toString();
	}

	private void printSummary(File out, BankList back)
	{
		System.out.printf(Locale.ROOT, "%d lines -> %s (%s); read back %d%n", rows.size(), out, kinds, back.getBanks().size());
		System.out.printf(Locale.ROOT, "booths and chests usable from some sides only (cache mask): %d, with a use tile %d%n",
			maskChecked, maskOpenUsable);
		System.out.println("Wiki banks with nothing placed (no booth, chest or banker found):");
		perEntry.forEach((k, v) ->
		{
			if (v == 0)
			{
				WikiBanks.Entry e = wiki.stream().filter(w -> w.name.equals(k)).findFirst().orElse(null);
				System.out.printf(Locale.ROOT, "  %-45s %d,%d-%d,%d plane %d  %s%n", k, e.x1, e.y1, e.x2, e.y2, e.plane, e.requirements);
			}
		});
		System.out.println("Booths and chests outside every Wiki bank (" + unmatched.size() + "):");
		unmatched.forEach(s -> System.out.println("  " + s));
		System.out.println("Left out, PvP worlds (" + pvpLeftOut.size() + "): " + pvpLeftOut);
		System.out.println("No tile to use it from (" + noUse.size() + "):");
		noUse.forEach(s -> System.out.println("  " + s));
	}

	/** A second look: a booth or chest no Wiki area is near goes to the nearest one this close (a wide bank's far booths). */
	static final int MATCH_FAR = 12;

	/**
	 * Banks whose Wiki map marks the way in while the cache places the booth or chest in the area's own map
	 * (found by comparing the Wiki's {{Map}} with the cache objects): Wiki name -> the area {x1, y1, x2, y2}.
	 */
	static final Map<String, int[]> HAND_MATCHED = new LinkedHashMap<>();

	static
	{
		HAND_MATCHED.put("Mage Arena bank", new int[]{2530, 4707, 2538, 4715});
		HAND_MATCHED.put("Sophanem bank", new int[]{2796, 5168, 2803, 5172});
		HAND_MATCHED.put("Blast Furnace bank", new int[]{1946, 4954, 1950, 4958});
		HAND_MATCHED.put("Motherlode Mine chest", new int[]{3759, 5664, 3763, 5668});
		HAND_MATCHED.put("Giants' Foundry chest", new int[]{3373, 11491, 3378, 11496});
		HAND_MATCHED.put("Tombs of Amascut bank", new int[]{3350, 9117, 3355, 9122});
	}

	/**
	 * The Wiki bank a booth or chest belongs to: one whose map area is within {@link #MATCH} tiles, else one marked by
	 * hand ({@link #HAND_MATCHED}), else the nearest within {@link #MATCH_FAR}; null if none. {@code how[0]}: "" or
	 * how it was matched when not plainly.
	 */
	static WikiBanks.Entry match(List<WikiBanks.Entry> wiki, int x, int y, int plane, String[] how)
	{
		how[0] = "";
		WikiBanks.Entry e = nearest(wiki, x, y, plane, MATCH);
		if (e != null)
		{
			return e;
		}
		for (Map.Entry<String, int[]> h : HAND_MATCHED.entrySet())
		{
			int[] b = h.getValue();
			if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3])
			{
				for (WikiBanks.Entry w : wiki)
				{
					if (w.name.equals(h.getKey()))
					{
						how[0] = " (matched by hand: the Wiki map shows the way in)";
						return w;
					}
				}
			}
		}
		e = nearest(wiki, x, y, plane, MATCH_FAR);
		if (e != null)
		{
			how[0] = " (" + e.distance(x, y) + " tiles outside the Wiki map's area)";
		}
		return e;
	}

	/** The Wiki bank whose map area is nearest (within {@code within} tiles), same plane first; null if none. */
	static WikiBanks.Entry nearest(List<WikiBanks.Entry> wiki, int x, int y, int plane, int within)
	{
		WikiBanks.Entry best = null;
		int bestD = Integer.MAX_VALUE;
		for (WikiBanks.Entry e : wiki)
		{
			int d = e.distance(x, y) * 2 + (e.plane == plane ? 0 : 1);
			if (e.distance(x, y) <= within && d < bestD)
			{
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/**
	 * The Wiki's requirement as a requirement line where the model can say it (members, a quest the cache knows,
	 * a quest started, a skill level); anything else becomes a note (not used by routes).
	 */
	static String wikiNeeds(WikiBanks.Entry e, List<String> notes)
	{
		List<String> parts = new ArrayList<>();
		if (e.members)
		{
			parts.add("members");
		}
		String r = e.requirements;
		String part = null;
		if (r.startsWith("quest:") || r.startsWith("started:"))
		{
			String asked = r.substring(r.indexOf(':') + 1);
			String q = CacheNames.quest(asked);
			if (q == null)
			{
				q = CacheNames.quest("The " + asked); // the Wiki links "Fremennik Isles", the cache says "The Fremennik Isles"
			}
			if (q != null)
			{
				part = "quest " + q + (r.startsWith("started:") ? " started" : "");
			}
		}
		else if (r.startsWith("skill:"))
		{
			part = r.substring(6);
		}
		if (part != null)
		{
			try
			{
				RequirementText.parse(part, RequirementText.ItemGroups.NONE);
				parts.add(part);
			}
			catch (RequirementText.ParseException ex)
			{
				part = null;
			}
		}
		if (part == null && !r.equals("none"))
		{
			notes.add("Wiki requirement not used: " + r.substring(r.indexOf(':') + 1));
		}
		return String.join("; ", parts);
	}

	private static String join(String a, String b)
	{
		return a.isEmpty() ? b : b.isEmpty() ? a : a + "; " + b;
	}

	private static String tiles(int[] use)
	{
		StringBuilder sb = new StringBuilder();
		for (int t : use)
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Tiles.x(t)).append(',').append(Tiles.y(t)).append(',').append(Tiles.plane(t));
		}
		return sb.toString();
	}

	/** Use tiles on the walking map. */
	static final class Uses
	{
		private static final int[][] DIRS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
		/** Wall flag on a tile's side towards direction d (N, E, S, W), and on the far tile's side facing back. */
		private static final int[] WALL_OUT = {CollisionFlags.NORTH, CollisionFlags.EAST, CollisionFlags.SOUTH, CollisionFlags.WEST};
		private static final int[] WALL_IN = {CollisionFlags.SOUTH, CollisionFlags.WEST, CollisionFlags.NORTH, CollisionFlags.EAST};
		/** Side bits of the cache mask, in the order of {@link #DIRS}. */
		private static final int[] SIDE = {1, 2, 4, 8};

		private final FlagGrid grid;
		private final CoarseGraph graph;

		Uses(FlagGrid grid, CoarseGraph graph)
		{
			this.grid = grid;
			this.graph = graph;
		}

		boolean usable(int x, int y, int p)
		{
			return graph.componentAt(x, y, p) >= 0;
		}

		int[] forObject(BankExtractor.BankObject o)
		{
			if (o.type <= 8)
			{
				// in a wall: its own tile and the one across the wall (rotation 0 west, 1 north, 2 east, 3 south)
				int[][] across = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
				List<Integer> out = new ArrayList<>();
				if (usable(o.x, o.y, o.plane))
				{
					out.add(Tiles.pack(o.x, o.y, o.plane));
				}
				int ax = o.x + across[o.rotation][0];
				int ay = o.y + across[o.rotation][1];
				if (usable(ax, ay, o.plane))
				{
					out.add(Tiles.pack(ax, ay, o.plane));
				}
				return out.stream().mapToInt(Integer::intValue).toArray();
			}
			return forObjectSides(o, ~o.blockedSides & 0xF, 0);
		}

		/** Tiles beside the footprint on the {@code sides} given (mask bits), no wall between; the footprint itself when open. */
		int[] forObjectSides(BankExtractor.BankObject o, int sides, int ignored)
		{
			List<Integer> out = new ArrayList<>();
			int x0 = o.x;
			int y0 = o.y;
			int x1 = o.x + o.sizeX - 1;
			int y1 = o.y + o.sizeY - 1;
			int p = o.plane;
			for (int x = x0; x <= x1; x++)
			{
				for (int y = y0; y <= y1; y++)
				{
					if (usable(x, y, p) && (grid.get(x, y, p) & CollisionFlags.BLOCKED) == 0 && !out.contains(Tiles.pack(x, y, p)))
					{
						out.add(Tiles.pack(x, y, p)); // not solid: stand on it
					}
					for (int d = 0; d < 4; d++)
					{
						if ((sides & SIDE[d]) == 0)
						{
							continue;
						}
						int nx = x + DIRS[d][0];
						int ny = y + DIRS[d][1];
						if (nx >= x0 && nx <= x1 && ny >= y0 && ny <= y1)
						{
							continue;
						}
						int t = Tiles.pack(nx, ny, p);
						if ((grid.get(x, y, p) & WALL_OUT[d]) == 0 && (grid.get(nx, ny, p) & WALL_IN[d]) == 0 && usable(nx, ny, p)
							&& !out.contains(t))
						{
							out.add(t);
						}
					}
				}
			}
			return out.stream().mapToInt(Integer::intValue).toArray();
		}

		/** Beside the banker (no wall between); if none, across one solid counter tile in front of it. */
		int[] forBanker(int x, int y, int p)
		{
			List<Integer> out = new ArrayList<>();
			for (int d = 0; d < 4; d++)
			{
				int nx = x + DIRS[d][0];
				int ny = y + DIRS[d][1];
				if ((grid.get(x, y, p) & WALL_OUT[d]) == 0 && (grid.get(nx, ny, p) & WALL_IN[d]) == 0 && usable(nx, ny, p))
				{
					out.add(Tiles.pack(nx, ny, p));
				}
			}
			if (out.isEmpty())
			{
				for (int d = 0; d < 4; d++)
				{
					int cx = x + DIRS[d][0];
					int cy = y + DIRS[d][1];
					int nx = cx + DIRS[d][0];
					int ny = cy + DIRS[d][1];
					if ((grid.get(cx, cy, p) & CollisionFlags.BLOCKED) != 0 && (grid.get(x, y, p) & WALL_OUT[d]) == 0
						&& (grid.get(cx, cy, p) & WALL_IN[d]) == 0 && (grid.get(cx, cy, p) & WALL_OUT[d]) == 0
						&& (grid.get(nx, ny, p) & WALL_IN[d]) == 0 && usable(nx, ny, p))
					{
						out.add(Tiles.pack(nx, ny, p));
					}
				}
			}
			return out.stream().mapToInt(Integer::intValue).toArray();
		}
	}
}
