package com.bestpath.tool.update;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.extract.FlagGrid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The game update comparison: what changed between two game caches, and which of the hand-made data it touches.
 * Fixed rules only, no guessing: the same two caches and the same data always give the same report. Nothing is
 * changed: a fact the data relied on that is different in the new cache is a finding for you to look at; a fact
 * that is the same carries over. Tool only.
 *
 * <p>This class holds the report's types, runs the checks ({@link #compare}) and writes the report as Markdown; the
 * checks themselves are in {@link CompareTransports}, {@link CompareOwnChanges}, {@link CompareRegions} and
 * {@link CompareGameData}.
 */
public final class CacheCompare
{
	/** How far a transport's object may have moved and still be found as moved (tiles, either way). */
	static final int MOVED_RANGE = 8;
	/** The most lines one list of the "other game data" section shows. */
	static final int OTHER_LIST_MAX = 300;
	/** The most changed tiles, or added / removed objects, shown under one region. */
	static final int REGION_LIST_MAX = 40;

	/** What a finding is about. {@link #look} findings need a look from you; the others are for information. */
	public enum Kind
	{
		OBJECT_GONE(true, "Transports whose object is gone"),
		OBJECT_MOVED(true, "Transports whose object moved"),
		OBJECT_REPLACED(true, "Transports whose object was replaced by another of the same name"),
		OPTION_LOST(true, "Transports whose object lost a menu option"),
		GROUND_CHANGED(true, "Transport ends on ground the game changed"),
		EDIT_TILE_CHANGED(true, "Your tile and wall changes on tiles the game changed"),
		SHUT_DOOR_GONE(true, "Doors you set shut that are no longer doors there"),
		AREA_REGION_CHANGED(true, "Hidden or kept areas in regions where walking changed"),
		REVIEWED_REGION_CHANGED(true, "Reviewed regions where walking changed"),
		PLACE_KIND_NEW(true, "New map icon kinds"),
		PLACE_KIND_GONE(true, "Map icon kinds offered to players that are gone"),
		NEW_CANDIDATE(false, "New transport-like objects"),
		OBJECT_RENAMED(false, "Transports whose object has a new name"),
		DOOR_ADDED(false, "New doors and gates (open by default)"),
		DOOR_REMOVED(false, "Doors and gates gone"),
		REGION_CHANGED(false, "Other regions whose walls changed (not reviewed, ignored, or walking the same)"),
		PLACE_COUNT_CHANGED(false, "Map icon kinds with more or fewer places");

		public final boolean look;
		public final String title;

		Kind(boolean look, String title)
		{
			this.look = look;
			this.title = title;
		}
	}

	/** One thing the comparison found. Immutable. */
	public static final class Finding
	{
		public final Kind kind;
		public final String text;
		/** The tile it is at, or -1 (x). */
		public final int x;
		public final int y;
		public final int plane;
		/** The data it is about: a transport id, change id or map icon kind; "" if none. */
		public final String ref;
		/** More lines about it (e.g. a region's changed tiles), shown under it; two leading spaces per level. */
		public final List<String> details;
		/** A region's changed tiles ({@link CacheFacts#tile}), marked on the Data Tool's map; empty for the others. */
		public final long[] tiles;

		Finding(Kind kind, String text, int x, int y, int plane, String ref, List<String> details, long[] tiles)
		{
			this.kind = kind;
			this.text = text;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.ref = ref == null ? "" : ref;
			this.details = Collections.unmodifiableList(new ArrayList<>(details));
			this.tiles = tiles.clone();
		}

		/**
		 * The finding's key for its card's answer ({@code CACHE_CHECK} change): the new cache, what, about what and where.
		 * The same two caches and data give the same keys, so an answered card is not made again.
		 */
		public String key(int toCache)
		{
			return toCache + "/" + kind + "/" + ref + "/" + x + "," + y + "," + plane;
		}
	}

	/** The comparison's result. */
	public static final class Report
	{
		public final CacheFacts before;
		public final CacheFacts after;
		public final List<Finding> findings = new ArrayList<>();
		/** What carried over: a line per kind of data, e.g. "4,512 transports: their object the same". */
		public final List<String> carried = new ArrayList<>();
		/** What could not be compared, and why. */
		public final List<String> notCompared = new ArrayList<>();
		/** Other game data that changed (not used by the data, or used through the export), by heading. */
		public final Map<String, List<String>> other = new LinkedHashMap<>();
		/** Which parts of the cache changed at all. */
		public final List<String> overview = new ArrayList<>();

		Report(CacheFacts before, CacheFacts after)
		{
			this.before = before;
			this.after = after;
		}

		public List<Finding> of(Kind k)
		{
			List<Finding> out = new ArrayList<>();
			for (Finding f : findings)
			{
				if (f.kind == k)
				{
					out.add(f);
				}
			}
			return out;
		}

		public int lookCount()
		{
			int n = 0;
			for (Finding f : findings)
			{
				if (f.kind.look)
				{
					n++;
				}
			}
			return n;
		}

		void add(Kind k, String text, int x, int y, int plane, String ref)
		{
			add(k, text, x, y, plane, ref, Collections.emptyList());
		}

		void add(Kind k, String text, int x, int y, int plane, String ref, List<String> details)
		{
			add(k, text, x, y, plane, ref, details, new long[0]);
		}

		void add(Kind k, String text, int x, int y, int plane, String ref, List<String> details, long[] tiles)
		{
			findings.add(new Finding(k, text, x, y, plane, ref, details, tiles));
		}
	}

	private CacheCompare()
	{
	}

	/** Compares cache {@code a} (old) with {@code b} (new) for the hand-made {@code data}. */
	public static Report compare(CacheFacts a, CacheFacts b, OurData data)
	{
		Report r = new Report(a, b);
		CompareGameData.overview(r, a, b);
		Set<Integer> wallRegions = FlagGrid.changedRegions(a.blocks, b.blocks);
		Map<Integer, List<Long>> changedTiles = CompareRegions.changedTiles(a, b, wallRegions);
		Set<Integer> walkRegions = CompareRegions.walkRegions(a, wallRegions, changedTiles, b);
		CompareTransports.objects(r, a, b, data);
		CompareTransports.ends(r, a, b, data);
		CompareOwnChanges.tileEdits(r, a, b, data);
		CompareOwnChanges.shutDoors(r, a, b, data);
		CompareOwnChanges.areas(r, walkRegions, changedTiles, data);
		CompareRegions.regions(r, a, b, wallRegions, walkRegions, changedTiles, data);
		CompareGameData.candidates(r, a, b, data);
		CompareGameData.doors(r, a, b);
		CompareGameData.placeKinds(r, a, b, data);
		CompareGameData.otherData(r, a, b);
		return r;
	}

	// ---- words (shared by the checks) ----

	static String tile(int x, int y, int plane)
	{
		return x + "," + y + "," + plane;
	}

	/** Movement flags in words: "open", or what blocks the tile and which sides have walls. */
	static String describe(Integer flags)
	{
		if (flags == null)
		{
			return "not read";
		}
		int f = flags;
		if (f == 0)
		{
			return "open";
		}
		List<String> out = new ArrayList<>();
		if ((f & CollisionFlags.NO_FLOOR) != 0)
		{
			out.add("no floor");
		}
		if ((f & CollisionFlags.FLOOR) != 0)
		{
			out.add("blocked floor");
		}
		if ((f & CollisionFlags.OBJECT) != 0)
		{
			out.add("blocked by an object");
		}
		if ((f & CollisionFlags.FLOOR_DECORATION) != 0)
		{
			out.add("blocked by floor decoration");
		}
		String[] sides = {"north-west", "north", "north-east", "east", "south-east", "south", "south-west", "west"};
		List<String> walls = new ArrayList<>();
		for (int i = 0; i < 8; i++)
		{
			if ((f & (1 << i)) != 0)
			{
				walls.add(sides[i]);
			}
		}
		if (!walls.isEmpty())
		{
			out.add("walls " + String.join(" ", walls));
		}
		return String.join(", ", out);
	}

	static String quoted(List<String> words)
	{
		if (words.isEmpty())
		{
			return "none";
		}
		StringBuilder sb = new StringBuilder();
		for (String w : words)
		{
			sb.append(sb.length() == 0 ? "" : ", ").append('"').append(w).append('"');
		}
		return sb.toString();
	}

	static List<TransportRecord> sorted(List<TransportRecord> list)
	{
		List<TransportRecord> out = new ArrayList<>(list);
		out.sort((p, q) -> p.id.compareTo(q.id));
		return out;
	}

	// ---- the report file ----

	/** The report as Markdown for you to read. */
	public static String markdown(Report r)
	{
		StringBuilder sb = new StringBuilder();
		CacheFacts a = r.before;
		CacheFacts b = r.after;
		sb.append("# Game update: cache #").append(a.cacheId).append(" -> #").append(b.cacheId).append("\n\n");
		sb.append("Made by the Data Tool's game update comparison (`gradlew -p tool cacheCompare`, `CacheCompare`): fixed")
			.append(" rules, no guessing; nothing in the data was changed.\n\n");
		sb.append("- Old: #").append(a.cacheId).append(", build ").append(a.build).append(", ").append(day(a.date)).append('\n');
		sb.append("- New: #").append(b.cacheId).append(", build ").append(b.build).append(", ").append(day(b.date)).append("\n\n");
		int look = r.lookCount();
		sb.append("**").append(look == 0 ? "Nothing needs a look" : look + " things need a look").append("**; ")
			.append(r.of(Kind.NEW_CANDIDATE).size()).append(" new transport-like objects.\n\n");

		sb.append("## What changed in the cache\n\n");
		for (String l : r.overview)
		{
			sb.append("- ").append(l).append('\n');
		}
		sb.append("\n## Needs a look\n\n");
		boolean any = false;
		for (Kind k : Kind.values())
		{
			if (k.look)
			{
				any |= section(sb, k, r.of(k));
			}
		}
		if (!any)
		{
			sb.append("Nothing.\n\n");
		}
		sb.append("## New in the game\n\n");
		if (!section(sb, Kind.NEW_CANDIDATE, r.of(Kind.NEW_CANDIDATE)))
		{
			sb.append("No new transport-like objects.\n\n");
		}
		sb.append("## Changed, used as it is (the next export takes it from the new cache)\n\n");
		any = false;
		for (Kind k : Kind.values())
		{
			if (!k.look && k != Kind.NEW_CANDIDATE)
			{
				any |= section(sb, k, r.of(k));
			}
		}
		if (!any)
		{
			sb.append("Nothing.\n\n");
		}
		sb.append("## Carried over unchanged\n\n");
		for (String l : r.carried)
		{
			sb.append("- ").append(l).append('\n');
		}
		if (!r.notCompared.isEmpty())
		{
			sb.append("\n## Not compared\n\n");
			for (String l : r.notCompared)
			{
				sb.append("- ").append(l).append('\n');
			}
		}
		sb.append("\n## Other game data\n\n");
		for (Map.Entry<String, List<String>> e : r.other.entrySet())
		{
			sb.append("### ").append(e.getKey()).append(" (").append(e.getValue().size()).append(")\n\n");
			list(sb, e.getValue(), OTHER_LIST_MAX);
		}
		return sb.toString();
	}

	private static boolean section(StringBuilder sb, Kind k, List<Finding> fs)
	{
		if (fs.isEmpty())
		{
			return false;
		}
		sb.append("### ").append(k.title).append(" (").append(fs.size()).append(")\n\n");
		for (Finding f : fs)
		{
			sb.append("- ").append(f.text).append('\n');
			for (String d : f.details)
			{
				int depth = 1;
				while (d.startsWith("  "))
				{
					d = d.substring(2);
					depth++;
				}
				for (int s = 0; s < depth; s++)
				{
					sb.append("  ");
				}
				sb.append("- ").append(d).append('\n');
			}
		}
		sb.append('\n');
		return true;
	}

	private static void list(StringBuilder sb, List<String> lines, int max)
	{
		if (lines.isEmpty())
		{
			sb.append("None.\n\n");
			return;
		}
		for (int i = 0; i < lines.size() && i < max; i++)
		{
			sb.append("- ").append(lines.get(i)).append('\n');
		}
		if (lines.size() > max)
		{
			sb.append("- ... and ").append(lines.size() - max).append(" more\n");
		}
		sb.append('\n');
	}

	private static String day(String timestamp)
	{
		return timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
	}
}
