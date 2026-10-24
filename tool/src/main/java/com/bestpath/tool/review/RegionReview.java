package com.bestpath.tool.review;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The manual check of each 64x64 region: for every region and
 * category (transports, varbits, items, sub-regions) a status and a note.
 * Bookkeeping only (the export never reads it), so it is not a change record:
 * it is saved at once to {@code data/manual/region-review.json}, sorted by
 * region id and pretty-printed so Git diffs stay readable. A region with
 * nothing recorded is not written. Used from the Swing thread.
 * <p>
 * Besides the four checks a region can carry a "reviewed by a person" tick
 * (key {@code HUMAN_REVIEWED}, who and when): checks may be filled in by
 * a script through the remote link, and every region still needs a person to look at it.
 */
public final class RegionReview
{
	public enum Category
	{
		TRANSPORTS("Transports"),
		VARBITS("Varbits"),
		ITEMS("Items"),
		SUBREGIONS("Sub-regions");

		private final String label;

		Category(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	public enum Status
	{
		NOT_CHECKED("Not checked"),
		NEEDS_WORK("Needs work"),
		COMPLETE("Complete");

		private final String label;

		Status(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** Overall state of a region, from its four checks. */
	public enum Overall
	{
		NOT_STARTED("Not started"),
		IN_PROGRESS("In progress"),
		NEEDS_WORK("Needs work"),
		COMPLETE("Complete");

		private final String label;

		Overall(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** One category of one region. Plain data, serialised to JSON. */
	public static final class Check
	{
		public Status status = Status.NOT_CHECKED;
		public String note = "";
		/** ISO time of the last change, and who made it. */
		public String updated = "";
		public String author = "";

		boolean isEmpty()
		{
			return status == Status.NOT_CHECKED && note.isBlank();
		}
	}

	/** The "reviewed by a person" tick of a region. Plain data, serialised to JSON. */
	public static final class Human
	{
		/** Who ticked it, and the ISO time. */
		public String by = "";
		public String updated = "";
	}

	/** The key of the person tick inside a region's entry (next to the category names). */
	static final String HUMAN_KEY = "HUMAN_REVIEWED";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final File file;
	private final TreeMap<Integer, TreeMap<Category, Check>> regions = new TreeMap<>();
	private final TreeMap<Integer, Human> humans = new TreeMap<>();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	/** The file's text as last read or written, so an unchanged state is not written again. */
	private String saved = "";

	public RegionReview(File file)
	{
		this.file = file;
	}

	public File getFile()
	{
		return file;
	}

	public void load() throws IOException
	{
		regions.clear();
		humans.clear();
		if (!file.isFile())
		{
			saved = toJson();
			return;
		}
		try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			JsonElement root = new JsonParser().parse(r);
			if (root != null && root.isJsonObject())
			{
				for (Map.Entry<String, JsonElement> region : root.getAsJsonObject().entrySet())
				{
					if (!region.getValue().isJsonObject())
					{
						continue;
					}
					readRegion(Integer.parseInt(region.getKey()), region.getValue().getAsJsonObject());
				}
			}
		}
		saved = toJson();
	}

	/** One region's entry of the file: its checks (unknown categories are skipped) and its person tick. */
	private void readRegion(int id, JsonObject entry)
	{
		TreeMap<Category, Check> checks = new TreeMap<>();
		for (Map.Entry<String, JsonElement> e : entry.entrySet())
		{
			if (e.getValue() == null || !e.getValue().isJsonObject())
			{
				continue;
			}
			if (e.getKey().equals(HUMAN_KEY))
			{
				Human h = GSON.fromJson(e.getValue(), Human.class);
				h.by = h.by == null ? "" : h.by;
				h.updated = h.updated == null ? "" : h.updated;
				humans.put(id, h);
				continue;
			}
			Category cat;
			try
			{
				cat = Category.valueOf(e.getKey());
			}
			catch (IllegalArgumentException unknown)
			{
				continue;
			}
			Check c = GSON.fromJson(e.getValue(), Check.class);
			c.status = c.status == null ? Status.NOT_CHECKED : c.status;
			c.note = c.note == null ? "" : c.note;
			c.updated = c.updated == null ? "" : c.updated;
			c.author = c.author == null ? "" : c.author;
			checks.put(cat, c);
		}
		if (!checks.isEmpty())
		{
			regions.put(id, checks);
		}
	}

	/** The file's text: per region its checks, then the person tick if set. */
	private String toJson()
	{
		TreeSet<Integer> ids = new TreeSet<>(regions.keySet());
		ids.addAll(humans.keySet());
		JsonObject root = new JsonObject();
		for (int id : ids)
		{
			JsonObject region = new JsonObject();
			regions.getOrDefault(id, new TreeMap<>()).forEach((cat, c) -> region.add(cat.name(), GSON.toJsonTree(c)));
			Human h = humans.get(id);
			if (h != null)
			{
				region.add(HUMAN_KEY, GSON.toJsonTree(h));
			}
			root.add(Integer.toString(id), region);
		}
		return GSON.toJson(root);
	}

	public void addListener(Runnable r)
	{
		listeners.add(r);
	}

	/** The check of a region's category; a fresh "not checked" one if nothing is recorded. Do not modify. */
	public Check get(int regionId, Category category)
	{
		TreeMap<Category, Check> checks = regions.get(regionId);
		Check c = checks == null ? null : checks.get(category);
		return c != null ? c : new Check();
	}

	public Status status(int regionId, Category category)
	{
		return get(regionId, category).status;
	}

	public boolean hasNotes(int regionId)
	{
		TreeMap<Category, Check> checks = regions.get(regionId);
		return checks != null && checks.values().stream().anyMatch(c -> !c.note.isBlank());
	}

	/** True if a person ticked this region as reviewed. */
	public boolean isHumanReviewed(int regionId)
	{
		return humans.containsKey(regionId);
	}

	/** The person tick of a region, or null. Do not modify. */
	public Human human(int regionId)
	{
		return humans.get(regionId);
	}

	/** Sets or clears the "reviewed by a person" tick and saves at once if it changed. */
	public void setHumanReviewed(int regionId, boolean on, String author) throws IOException
	{
		if (on == humans.containsKey(regionId))
		{
			return;
		}
		if (on)
		{
			Human h = new Human();
			h.by = author;
			h.updated = Instant.now().toString();
			humans.put(regionId, h);
		}
		else
		{
			humans.remove(regionId);
		}
		save();
		listeners.forEach(Runnable::run);
	}

	public Overall overall(int regionId)
	{
		int complete = 0;
		boolean any = false;
		for (Category cat : Category.values())
		{
			Check c = get(regionId, cat);
			if (c.status == Status.NEEDS_WORK)
			{
				return Overall.NEEDS_WORK;
			}
			complete += c.status == Status.COMPLETE ? 1 : 0;
			any |= !c.isEmpty();
		}
		if (complete == Category.values().length)
		{
			return Overall.COMPLETE;
		}
		return any ? Overall.IN_PROGRESS : Overall.NOT_STARTED;
	}

	/** Sets a status and/or note (null = keep) and saves at once if anything changed. */
	public void set(int regionId, Category category, Status status, String note, String author) throws IOException
	{
		Check old = get(regionId, category);
		Status newStatus = status != null ? status : old.status;
		String newNote = note != null ? note : old.note;
		if (newStatus == old.status && newNote.equals(old.note))
		{
			return;
		}
		Check c = new Check();
		c.status = newStatus;
		c.note = newNote;
		c.updated = Instant.now().toString();
		c.author = author;
		TreeMap<Category, Check> checks = regions.computeIfAbsent(regionId, k -> new TreeMap<>());
		if (c.isEmpty())
		{
			checks.remove(category);
			if (checks.isEmpty())
			{
				regions.remove(regionId);
			}
		}
		else
		{
			checks.put(category, c);
		}
		save();
		listeners.forEach(Runnable::run);
	}

	private void save() throws IOException
	{
		String json = toJson();
		if (json.equals(saved))
		{
			return;
		}
		file.getParentFile().mkdirs();
		// write a temporary file first, so a crash never leaves half a file
		File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
		try (Writer w = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8))
		{
			w.write(json);
			w.write('\n');
		}
		Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		saved = json;
	}
}
