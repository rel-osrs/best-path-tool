package com.bestpath.tool.edit;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.extract.FlagGrid;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Makes {@code data/manual/changes.jsonl} smaller (the file only ever grows).
 * Takes out:
 * <ul>
 * <li>every undo (REVERT) and the change it undid;</li>
 * <li>every change that a later change to the same thing replaced (a transport saved again, a tile blocked and then
 * made walkable, a card answered twice ...).</li>
 * </ul>
 * What is kept is the original lines, word for word (same ids, who, when, cache and notes), so what the tool and the
 * plugin data are built from is exactly the same as before: {@link #verify} checks that before anything is written,
 * and the compaction stops if anything would differ. The whole old file is copied first into
 * {@code cache/backups/<date>-before-compact/} (git keeps every earlier version too).
 * <p>
 * Lost by design: the Change log's rows for undone and replaced changes, and the notes written on replaced versions.
 * <p>
 * Where a kept line goes: in the place of the FIRST line about the same thing (for a transport or a "test in game"
 * entry: the first line since it was last deleted or taken off), because the order things were first added decides
 * the order of the exported transports (which of two equally quick routes wins), the hidden and kept areas and the
 * test list. Free-to-play land and game update card answers are never compacted.
 */
public final class ChangesCompactor
{
	private static final Gson GSON = new Gson();
	/** Inside the workspace's git-ignored cache/ folder, beside the cache backups. */
	public static final String BACKUP_DIR = "cache/backups";

	private ChangesCompactor()
	{
	}

	/** What a compaction does: the lines kept (unchanged text, in their new order) and what was taken out. */
	public static final class Plan
	{
		/** The lines to write, in order (each exactly as it was in the file). */
		public final List<String> kept = new ArrayList<>();
		public int linesBefore;
		/** Undo lines and the changes they undid. */
		public int undone;
		/** Changes a later change to the same thing replaced, by kind. */
		public final Map<Edit.Type, Integer> replaced = new EnumMap<>(Edit.Type.class);
		public long bytesBefore;
		public long bytesAfter;

		public int replacedTotal()
		{
			return replaced.values().stream().mapToInt(Integer::intValue).sum();
		}

		public boolean nothingToDo()
		{
			return kept.size() == linesBefore;
		}

		public String summary()
		{
			StringBuilder s = new StringBuilder();
			s.append(String.format(Locale.ROOT, "%,d lines -> %,d lines; %.1f MB -> %.1f MB.%n", linesBefore, kept.size(),
				bytesBefore / 1e6, bytesAfter / 1e6));
			s.append(String.format(Locale.ROOT, "Taken out: %,d undo lines and undone changes; %,d replaced changes",
				undone, replacedTotal()));
			if (!replaced.isEmpty())
			{
				s.append(" (");
				String sep = "";
				for (Map.Entry<Edit.Type, Integer> e : replaced.entrySet())
				{
					s.append(sep).append(String.format(Locale.ROOT, "%s %,d", e.getKey().toString().toLowerCase(Locale.ROOT), e.getValue()));
					sep = ", ";
				}
				s.append(")");
			}
			return s.append(".").toString();
		}
	}

	private static final class Line
	{
		final int index;
		final String raw;
		final Edit edit;

		Line(int index, String raw, Edit edit)
		{
			this.index = index;
			this.raw = raw;
			this.edit = edit;
		}
	}

	/** Works out the compacted file from the file's lines (blank lines are dropped, as {@link EditStore#load} skips them). */
	public static Plan plan(List<String> lines)
	{
		Plan p = new Plan();
		List<Line> all = readLines(lines, p);
		p.linesBefore = all.size();
		List<Line> active = activeLines(all);
		p.undone = all.size() - active.size();
		List<long[]> slots = keptSlots(active, p);
		for (long[] s : slots)
		{
			String raw = all.get((int) s[2]).raw;
			p.kept.add(raw);
			p.bytesAfter += raw.getBytes(StandardCharsets.UTF_8).length + 1;
		}
		return p;
	}

	/** The file's non-blank lines with their changes; adds their size to {@code p.bytesBefore}. */
	private static List<Line> readLines(List<String> lines, Plan p)
	{
		List<Line> all = new ArrayList<>();
		for (String raw : lines)
		{
			if (!raw.isBlank())
			{
				all.add(new Line(all.size(), raw, GSON.fromJson(raw, Edit.class)));
				p.bytesBefore += raw.getBytes(StandardCharsets.UTF_8).length + 1;
			}
		}
		return all;
	}

	/** The lines whose change is in effect: no undo lines and nothing undone. */
	private static List<Line> activeLines(List<Line> all)
	{
		// undone: as EditStore.active (newest first; an undo that was itself undone undoes nothing)
		Set<String> undone = new HashSet<>();
		for (int i = all.size() - 1; i >= 0; i--)
		{
			Edit e = all.get(i).edit;
			if (e.type == Edit.Type.REVERT && !undone.contains(e.id))
			{
				undone.add(e.target);
			}
		}
		List<Line> active = new ArrayList<>();
		for (Line l : all)
		{
			if (l.edit.type != Edit.Type.REVERT && !undone.contains(l.edit.id))
			{
				active.add(l);
			}
		}
		return active;
	}

	/**
	 * The lines to keep, in file order, as {slot, order within the slot, line index}; counts the replaced
	 * changes in {@code p.replaced}.
	 */
	private static List<long[]> keptSlots(List<Line> active, Plan p)
	{
		// the changes to each thing, oldest first; changes that are never compacted keep their own place
		Map<String, List<Line>> groups = new LinkedHashMap<>();
		List<long[]> slots = new ArrayList<>(); // {slot, order within the slot, line index}
		for (Line l : active)
		{
			String k = key(l.edit);
			if (k == null)
			{
				slots.add(new long[]{l.index, 0, l.index});
			}
			else
			{
				groups.computeIfAbsent(k, x -> new ArrayList<>()).add(l);
			}
		}
		for (List<Line> g : groups.values())
		{
			List<Line> keep = new ArrayList<>();
			int slot = keepOf(g, keep);
			for (int i = 0; i < keep.size(); i++)
			{
				slots.add(new long[]{slot, i, keep.get(i).index});
			}
			for (Line l : g)
			{
				if (!keep.contains(l))
				{
					p.replaced.merge(l.edit.type, 1, Integer::sum);
				}
			}
		}
		slots.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
		return slots;
	}

	/**
	 * What a change is about: two changes with the same key change the same thing, and the later one replaces the
	 * earlier. Null: never compacted (free-to-play land rectangles overlap; game update answers are kept as a record).
	 */
	static String key(Edit e)
	{
		switch (e.type)
		{
			case TRANSPORT:
				return e.transport == null || e.transport.id == null ? null : "transport:" + e.transport.id;
			case TRANSPORT_DELETE:
				return e.target == null ? null : "transport:" + e.target;
			case TILE:
			case HIDE:
			case KEEP:
				return e.type + ":" + e.x + "," + e.y + "," + e.plane;
			case DOOR:
				return e.type + ":" + e.x + "," + e.y + "," + e.plane + ":" + e.objectId;
			case WALL:
				// as written (not joined with the same wall seen from the next tile), like the map layer and OurData
				return e.type + ":" + e.x + "," + e.y + "," + e.plane + ":" + e.side;
			case IGNORE_REGION:
				return e.type + ":" + e.regionId();
			case REJECT:
			case TEST_LATER:
			case SEA_CHANNEL:
			case PLACE_KIND:
			case CHARTING_SPOT:
				return e.target == null ? null : e.type + ":" + e.target;
			default:
				return null;
		}
	}

	/** True for a change that takes the thing away again (it is added at the end of the list when it comes back). */
	private static boolean removes(Edit e)
	{
		return e.type == Edit.Type.TRANSPORT_DELETE || e.type == Edit.Type.TEST_LATER && !e.on;
	}

	/** Fills {@code keep} with the lines of one thing's changes that are kept; returns the place they go. */
	private static int keepOf(List<Line> g, List<Line> keep)
	{
		Line last = g.get(g.size() - 1);
		Edit.Type type = last.edit.type;
		if (type == Edit.Type.TRANSPORT || type == Edit.Type.TRANSPORT_DELETE || type == Edit.Type.TEST_LATER)
		{
			keep.add(last);
			if (removes(last.edit))
			{
				return last.index; // gone in the end: the place doesn't matter
			}
			int start = 0;
			for (int i = 0; i < g.size(); i++)
			{
				if (removes(g.get(i).edit))
				{
					start = i + 1;
				}
			}
			return g.get(start).index; // where it was (last) added
		}
		if (type == Edit.Type.TILE && last.edit.on)
		{
			// "walkable" clears more than "blocked" adds: blocked after walkable keeps the walkable too
			for (int i = g.size() - 2; i >= 0; i--)
			{
				if (!g.get(i).edit.on)
				{
					keep.add(g.get(i));
					break;
				}
			}
		}
		if (type == Edit.Type.PLACE_KIND && (last.edit.name == null || last.edit.name.isBlank()))
		{
			// the last name given stays: keep the change that gave it
			for (int i = g.size() - 2; i >= 0; i--)
			{
				String n = g.get(i).edit.name;
				if (n != null && !n.isBlank())
				{
					keep.add(g.get(i));
					break;
				}
			}
		}
		keep.add(last);
		return g.get(0).index;
	}

	// --- the safety check ---

	/**
	 * Throws if the changes in effect {@code before} and the compacted {@code after} would build anything differently:
	 * the walls, tiles and doors, transports (and their order), the hidden and kept areas, rejected candidates, ignored
	 * regions, the test list, deleted sea channels, every card answer, free-to-play land, and the latest change of each
	 * thing that the map and the game update comparison show.
	 */
	public static void verify(List<Edit> before, List<Edit> after)
	{
		Set<String> ids = new HashSet<>();
		before.forEach(e -> ids.add(e.id));
		Set<String> seen = new HashSet<>();
		for (Edit e : after)
		{
			check(e.type != Edit.Type.REVERT, "an undo line was kept");
			check(ids.contains(e.id), "kept a change that was not in effect: " + e.id);
			check(seen.add(e.id), "a change twice: " + e.id);
		}
		ManualState a = new ManualState(before, new FlagGrid(Set.of()));
		ManualState b = new ManualState(after, new FlagGrid(Set.of()));
		check(json(a.getTransports()).equals(json(b.getTransports())), "the transports or their order");
		check(json(a.getTestLater()).equals(json(b.getTestLater())), "the test in game list");
		check(a.getRejected().equals(b.getRejected()), "the rejected candidates");
		check(a.getIgnoredRegions().equals(b.getIgnoredRegions()), "the ignored regions");
		check(a.getDeletedChannels().equals(b.getDeletedChannels()), "the deleted sea channels");
		check(anchors(a.getHiddenAnchors()).equals(anchors(b.getHiddenAnchors())), "the hidden areas");
		check(anchors(a.getKeptAnchors()).equals(anchors(b.getKeptAnchors())), "the kept pockets");
		check(gridEffect(before).equals(gridEffect(after)), "the walls and blocked tiles");
		check(lastOfEach(before).equals(lastOfEach(after)), "the latest change of each thing");
		ManualState.PlaceKindAnswers pa = ManualState.placeKinds(before);
		ManualState.PlaceKindAnswers pb = ManualState.placeKinds(after);
		check(pa.decided.equals(pb.decided) && pa.removed.equals(pb.removed) && pa.names.equals(pb.names),
			"the place kind answers");
		check(ManualState.chartingSpots(before).equals(ManualState.chartingSpots(after)), "the sea charting answers");
		check(ManualState.decidedChannels(before).equals(ManualState.decidedChannels(after)), "the sea channel answers");
		check(ManualState.cacheChecks(before).equals(ManualState.cacheChecks(after)), "the game update answers");
		check(idsOf(before, Edit.Type.FREE_LAND).equals(idsOf(after, Edit.Type.FREE_LAND)), "the free-to-play land");
		check(idsOf(before, Edit.Type.CACHE_CHECK).equals(idsOf(after, Edit.Type.CACHE_CHECK)), "the game update answers");
	}

	private static void check(boolean ok, String what)
	{
		if (!ok)
		{
			throw new IllegalStateException("Compacting would change " + what + ": nothing was written.");
		}
	}

	private static String json(Map<String, TransportRecord> m)
	{
		StringBuilder s = new StringBuilder();
		m.forEach((k, v) -> s.append(k).append('=').append(GSON.toJson(v)).append('\n'));
		return s.toString();
	}

	private static List<String> anchors(List<int[]> list)
	{
		List<String> out = new ArrayList<>();
		list.forEach(t -> out.add(Arrays.toString(t)));
		return out;
	}

	private static List<String> idsOf(List<Edit> edits, Edit.Type type)
	{
		List<String> out = new ArrayList<>();
		for (Edit e : edits)
		{
			if (e.type == type)
			{
				out.add(e.id);
			}
		}
		return out;
	}

	/**
	 * Per tile, what the Wall and Tile changes do to its flags whatever they were before: {flags added, flags cleared}
	 * (as {@link ManualState} applies them, in order).
	 */
	static Map<String, List<Integer>> gridEffect(List<Edit> edits)
	{
		int[] own = {CollisionFlags.NORTH, CollisionFlags.EAST, CollisionFlags.SOUTH, CollisionFlags.WEST};
		int[] opposite = {CollisionFlags.SOUTH, CollisionFlags.WEST, CollisionFlags.NORTH, CollisionFlags.EAST};
		int[] dx = {0, 1, 0, -1};
		int[] dy = {1, 0, -1, 0};
		Map<String, int[]> out = new TreeMap<>();
		for (Edit e : edits)
		{
			if (e.type == Edit.Type.TILE)
			{
				apply(out, e.x, e.y, e.plane, e.on, CollisionFlags.OBJECT, CollisionFlags.BLOCKED);
			}
			else if (e.type == Edit.Type.WALL)
			{
				apply(out, e.x, e.y, e.plane, e.on, own[e.side], own[e.side]);
				apply(out, e.x + dx[e.side], e.y + dy[e.side], e.plane, e.on, opposite[e.side], opposite[e.side]);
			}
		}
		Map<String, List<Integer>> result = new TreeMap<>();
		// flags added at the end are set whatever was cleared before
		out.forEach((k, v) -> result.put(k, List.of(v[0], v[1] & ~v[0])));
		return result;
	}

	private static void apply(Map<String, int[]> out, int x, int y, int plane, boolean on, int add, int clear)
	{
		int[] v = out.computeIfAbsent(x + "," + y + "," + plane, k -> new int[2]);
		if (on)
		{
			v[0] |= add; // (f & ~c) | a, then | add
		}
		else
		{
			v[0] &= ~clear; // then & ~clear
			v[1] |= clear;
		}
	}

	/** The latest change of each thing, in the order the things first appear (as the map layer and OurData read them). */
	static List<String> lastOfEach(List<Edit> edits)
	{
		Map<String, String> last = new LinkedHashMap<>();
		Set<String> gone = new HashSet<>();
		for (Edit e : edits)
		{
			String k = key(e);
			if (k != null)
			{
				if (removes(e) || gone.contains(k))
				{
					last.remove(k); // taken away, or back after that: at the end
				}
				last.put(k, e.id);
				if (removes(e))
				{
					gone.add(k);
				}
				else
				{
					gone.remove(k);
				}
			}
		}
		List<String> out = new ArrayList<>();
		last.forEach((k, v) -> out.add(k + "=" + v));
		return out;
	}

	// --- doing it ---

	/**
	 * Compacts {@code changes} (the workspace's changes.jsonl): backup, plan, safety check, then the file replaced in
	 * one step, but only if nobody added a change to it in the meantime. Returns the plan (nothing written when
	 * {@link Plan#nothingToDo()}). Throws, with the file untouched, when anything is wrong.
	 */
	public static Plan compact(File workspace, File changes, Consumer<String> log) throws IOException
	{
		long size = changes.length();
		FileTime time = Files.getLastModifiedTime(changes.toPath());
		List<String> lines = Files.readAllLines(changes.toPath(), StandardCharsets.UTF_8);
		Plan p = plan(lines);
		log.accept(p.summary());
		if (p.nothingToDo())
		{
			log.accept("Nothing to take out: the file is unchanged.");
			return p;
		}
		EditStore before = new EditStore(changes);
		before.load();
		verify(before.active(), parse(p.kept));
		log.accept("Checked: the compacted changes build exactly the same data.");

		File backup = backup(workspace, changes, p, log);

		File tmp = new File(changes.getParentFile(), changes.getName() + ".compacting");
		Files.write(tmp.toPath(), (String.join("\n", p.kept) + "\n").getBytes(StandardCharsets.UTF_8));
		EditStore written = new EditStore(tmp);
		written.load();
		verify(before.active(), written.active());
		if (changes.length() != size || !Files.getLastModifiedTime(changes.toPath()).equals(time))
		{
			Files.delete(tmp.toPath());
			throw new IOException("changes.jsonl changed while compacting (a change was made): nothing was replaced. Try again.");
		}
		Files.move(tmp.toPath(), changes.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		log.accept("Done. The old file is in " + backup.getPath());
		return p;
	}

	private static List<Edit> parse(List<String> lines)
	{
		List<Edit> out = new ArrayList<>();
		for (String l : lines)
		{
			out.add(GSON.fromJson(l, Edit.class));
		}
		return out;
	}

	/** Copies the whole file to cache/backups/<date>-before-compact/ (written as "-partial", renamed when complete). */
	static File backup(File workspace, File changes, Plan p, Consumer<String> log) throws IOException
	{
		File root = new File(workspace, BACKUP_DIR);
		String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ROOT)) + "-before-compact";
		File dest = new File(root, name);
		for (int n = 2; dest.exists() || new File(root, dest.getName() + "-partial").exists(); n++)
		{
			dest = new File(root, name + "-" + n);
		}
		File partial = new File(root, dest.getName() + "-partial");
		Files.createDirectories(partial.toPath());
		Files.copy(changes.toPath(), new File(partial, changes.getName()).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
		Files.write(new File(partial, "README.txt").toPath(), List.of(
			"The Data Tool's change list (data/manual/changes.jsonl) before it was compacted.",
			p.summary().replace(System.lineSeparator(), " "),
			"To go back: close the Data Tool and copy changes.jsonl from here over data/manual/changes.jsonl."),
			StandardCharsets.UTF_8);
		Files.move(partial.toPath(), dest.toPath());
		log.accept("Backup: " + dest.getPath());
		return dest;
	}
}
