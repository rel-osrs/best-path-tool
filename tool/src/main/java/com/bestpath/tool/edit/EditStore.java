package com.bestpath.tool.edit;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The manual changes. Every change takes effect at once: it is appended to
 * {@code data/manual/changes.jsonl} (one JSON object per line, never
 * rewritten) the moment it is made, with who, when and which cache. Undoing
 * a change is itself a change (type REVERT); a REVERT can be undone too.
 * <p>
 * The tool's data (walls, sub-regions, transports) is rebuilt from the
 * changes in the background; changes made since the last rebuild are the
 * "unbuilt" ones, which the map shows on top of the built data until the
 * next rebuild takes them in.
 * <p>
 * {@link #undo()} (Ctrl+Z) takes back the last action since the tool was opened: one
 * click, or one whole mouse drag (see {@link #beginGesture()}). Used from the
 * Swing thread.
 */
public final class EditStore
{
	private static final Gson GSON = new Gson();
	private static final AtomicInteger COUNTER = new AtomicInteger();

	private final File file;
	/** Every record in the file, oldest first, REVERTs included. */
	private final List<Edit> records = new ArrayList<>();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	/** Ids of the records each undo step added, newest step last. */
	private final Deque<List<String>> undoSteps = new ArrayDeque<>();
	/** The step being collected during a mouse drag, or null. */
	private List<String> gesture;
	/** Ids of the active changes the current data was built from. */
	private Set<String> builtIds = Collections.emptySet();

	private Supplier<String> author = () -> "Rel";
	private IntSupplier cacheId = () -> -1;
	private IntSupplier cacheBuild = () -> -1;
	private Consumer<Exception> onError = ex ->
	{
		throw new IllegalStateException(ex);
	};

	/** Derived from {@link #records}; null when it must be worked out again. */
	private List<Edit> activeCache;
	private Set<String> activeIdCache;
	private List<Edit> unbuiltCache;

	public EditStore(File file)
	{
		this.file = file;
	}

	public File getFile()
	{
		return file;
	}

	public void load() throws IOException
	{
		records.clear();
		undoSteps.clear();
		changedRecords();
		if (!file.isFile())
		{
			return;
		}
		try (BufferedReader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (!line.isBlank())
				{
					records.add(GSON.fromJson(line, Edit.class));
				}
			}
		}
		changedRecords();
		// the data is always built from the file as loaded (when a cache opens), so nothing is "unbuilt" yet
		builtIds = Set.copyOf(activeIds());
	}

	/**
	 * Reads the file again after it was replaced (compacted, {@link ChangesCompactor}): the same changes in effect, so
	 * nothing needs building; the undo history is gone.
	 */
	public void reload() throws IOException
	{
		load();
		changed();
	}

	/** Who and which cache each new record is stamped with, and where a failed save is reported. */
	public void setStamp(Supplier<String> author, IntSupplier cacheId, IntSupplier cacheBuild, Consumer<Exception> onError)
	{
		this.author = author;
		this.cacheId = cacheId;
		this.cacheBuild = cacheBuild;
		this.onError = onError;
	}

	public void addListener(Runnable r)
	{
		listeners.add(r);
	}

	private void changed()
	{
		listeners.forEach(Runnable::run);
	}

	private void changedRecords()
	{
		activeCache = null;
		activeIdCache = null;
		unbuiltCache = null;
	}

	public static String newId(String prefix)
	{
		return prefix + "-" + System.currentTimeMillis() + "-" + COUNTER.incrementAndGet();
	}

	// --- making changes ---

	/** Makes a change: saved at once, and one undo step (or part of the current mouse drag's step). */
	public void add(Edit e)
	{
		if (append(e))
		{
			step(e.id);
			changed();
		}
	}

	/**
	 * Takes back the unbuilt changes to the same thing as {@code like} (toggling
	 * back, e.g. clicking a wall again). Returns false if there was none.
	 */
	public boolean cancel(Edit like)
	{
		List<Edit> same = new ArrayList<>();
		for (Edit u : getUnbuilt())
		{
			if (sameTarget(u, like))
			{
				same.add(u);
			}
		}
		if (same.isEmpty())
		{
			return false;
		}
		same.forEach(this::revertOne);
		changed();
		return true;
	}

	/** Takes back one change (by adding a REVERT for it), as one undo step. */
	public void revert(Edit target)
	{
		if (revertOne(target))
		{
			changed();
		}
	}

	private boolean revertOne(Edit target)
	{
		if (!isActive(target))
		{
			return false;
		}
		Edit r = revertOf(target);
		if (append(r))
		{
			step(r.id);
			return true;
		}
		return false;
	}

	private static Edit revertOf(Edit target)
	{
		Edit r = new Edit();
		r.type = Edit.Type.REVERT;
		r.target = target.id;
		r.x = target.x;
		r.y = target.y;
		r.plane = target.plane;
		return r;
	}

	/** The latest unbuilt change to the same thing as {@code like}, or null. */
	public Edit findUnbuilt(Edit like)
	{
		List<Edit> unbuilt = getUnbuilt();
		for (int i = unbuilt.size() - 1; i >= 0; i--)
		{
			if (sameTarget(unbuilt.get(i), like))
			{
				return unbuilt.get(i);
			}
		}
		return null;
	}

	private boolean append(Edit e)
	{
		if (e.id == null)
		{
			e.id = newId("c");
		}
		e.author = author.get();
		e.time = Instant.now().toString();
		e.cacheId = cacheId.getAsInt();
		e.cacheBuild = cacheBuild.getAsInt();
		try
		{
			File dir = file.getAbsoluteFile().getParentFile();
			if (dir != null)
			{
				dir.mkdirs();
			}
			try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND))
			{
				w.write(GSON.toJson(e));
				w.write('\n');
			}
		}
		catch (IOException ex)
		{
			onError.accept(ex);
			return false;
		}
		records.add(e);
		changedRecords();
		return true;
	}

	// --- undo ---

	/** Starts collecting one undo step (a mouse button went down). */
	public void beginGesture()
	{
		if (gesture == null)
		{
			gesture = new ArrayList<>();
		}
	}

	/** Ends the step collected since {@link #beginGesture()} (the mouse button came up). */
	public void endGesture()
	{
		if (gesture != null && !gesture.isEmpty())
		{
			undoSteps.addLast(gesture);
		}
		gesture = null;
	}

	private void step(String id)
	{
		if (gesture != null)
		{
			gesture.add(id);
		}
		else
		{
			undoSteps.addLast(new ArrayList<>(List.of(id)));
		}
	}

	/**
	 * Undo (Ctrl+Z): takes back the last action since the tool was opened by adding a
	 * REVERT for each change it made, newest first. Returns false if there is
	 * nothing to undo. Not itself undoable (no redo).
	 */
	public boolean undo()
	{
		endGesture();
		List<String> last = undoSteps.pollLast();
		if (last == null)
		{
			return false;
		}
		for (int i = last.size() - 1; i >= 0; i--)
		{
			Edit target = byId(last.get(i));
			if (target != null && isActive(target))
			{
				append(revertOf(target));
			}
		}
		changed();
		return true;
	}

	private Edit byId(String id)
	{
		for (int i = records.size() - 1; i >= 0; i--)
		{
			if (records.get(i).id.equals(id))
			{
				return records.get(i);
			}
		}
		return null;
	}

	// --- reading ---

	/** Every record, including REVERTs and changes that were undone, oldest first. */
	public List<Edit> getApplied()
	{
		return Collections.unmodifiableList(records);
	}

	/**
	 * Changes in effect, oldest first, REVERT records left out. A change is
	 * undone by a later REVERT, unless that REVERT was itself undone.
	 */
	public List<Edit> active()
	{
		if (activeCache == null)
		{
			Set<String> undone = new HashSet<>();
			for (int i = records.size() - 1; i >= 0; i--)
			{
				Edit e = records.get(i);
				if (e.type == Edit.Type.REVERT && !undone.contains(e.id))
				{
					undone.add(e.target);
				}
			}
			List<Edit> out = new ArrayList<>();
			for (Edit e : records)
			{
				if (e.type != Edit.Type.REVERT && !undone.contains(e.id))
				{
					out.add(e);
				}
			}
			activeCache = Collections.unmodifiableList(out);
			Set<String> ids = new HashSet<>();
			out.forEach(a -> ids.add(a.id));
			activeIdCache = ids;
		}
		return activeCache;
	}

	private boolean isActive(Edit e)
	{
		if (e.type == Edit.Type.REVERT)
		{
			// a REVERT is in effect unless a later one undid it
			for (Edit r : records)
			{
				if (r.type == Edit.Type.REVERT && e.id.equals(r.target))
				{
					return !isActive(r);
				}
			}
			return records.contains(e);
		}
		return activeIds().contains(e.id);
	}

	/** True if a change (not a REVERT) is no longer in effect. */
	public boolean isReverted(Edit e)
	{
		return e.type != Edit.Type.REVERT && !activeIds().contains(e.id);
	}

	/** Changes in effect that the current data was not built from yet, oldest first. */
	public List<Edit> getUnbuilt()
	{
		if (unbuiltCache == null)
		{
			List<Edit> out = new ArrayList<>();
			for (Edit e : active())
			{
				if (!builtIds.contains(e.id))
				{
					out.add(e);
				}
			}
			unbuiltCache = Collections.unmodifiableList(out);
		}
		return unbuiltCache;
	}

	/** Ids of the changes in effect now (handed to a data rebuild). */
	public Set<String> activeIds()
	{
		active();
		return Collections.unmodifiableSet(activeIdCache);
	}

	/** The data now in use was built from these changes. */
	public void markBuilt(Set<String> ids)
	{
		builtIds = Set.copyOf(ids);
		unbuiltCache = null;
		changed();
	}

	/** True if the changes in effect differ from the ones the data was built from. */
	public boolean needsBuild()
	{
		return !activeIds().equals(builtIds);
	}

	static boolean sameTarget(Edit a, Edit b)
	{
		if (a.type != b.type)
		{
			boolean transports = (a.type == Edit.Type.TRANSPORT || a.type == Edit.Type.TRANSPORT_DELETE)
				&& (b.type == Edit.Type.TRANSPORT || b.type == Edit.Type.TRANSPORT_DELETE);
			return transports && Objects.equals(transportId(a), transportId(b));
		}
		switch (a.type)
		{
			case WALL:
				return a.x == b.x && a.y == b.y && a.plane == b.plane && a.side == b.side;
			case TILE:
			case HIDE:
			case KEEP:
				return a.x == b.x && a.y == b.y && a.plane == b.plane;
			case DOOR:
				return a.x == b.x && a.y == b.y && a.plane == b.plane && a.objectId == b.objectId;
			case TRANSPORT:
			case TRANSPORT_DELETE:
				return Objects.equals(transportId(a), transportId(b));
			case REJECT:
			case REVERT:
			case TEST_LATER:
			case SEA_CHANNEL:
			case PLACE_KIND:
			case CHARTING_SPOT:
			case CACHE_CHECK:
				return Objects.equals(a.target, b.target);
			case IGNORE_REGION:
				return a.regionId() == b.regionId();
			default:
				return false;
		}
	}

	private static String transportId(Edit e)
	{
		return e.type == Edit.Type.TRANSPORT ? e.transport.id : e.target;
	}
}
