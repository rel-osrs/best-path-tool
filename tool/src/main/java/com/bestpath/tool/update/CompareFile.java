package com.bestpath.tool.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A game update comparison's result as the Data Tool reads it back: which caches, where the report is,
 * and every finding with its card key. Written by {@link CacheCompareCli} next to the report, into
 * {@code cache/cache-compare/<old>-to-<new>.json} (git-ignored: the report in docs/reports/ is the record). Plain data.
 */
public final class CompareFile
{
	/** Where the files go, in the workspace. */
	public static final String DIR = "cache/cache-compare";

	/** One finding, as {@link CacheCompare.Finding} plus its key and whether it needs a look. */
	public static final class Item
	{
		public String key;
		public String kind;
		public boolean look;
		public String title;
		public String text;
		public int x;
		public int y;
		public int plane;
		public String ref;
		public List<String> details = new ArrayList<>();
		/** Changed tiles ({@link CacheFacts#tile}). */
		public long[] tiles = new long[0];
	}

	public int from;
	public int to;
	public String made;
	/** The report's path, relative to the workspace. */
	public String report;
	public List<Item> items = new ArrayList<>();

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public static CompareFile of(CacheCompare.Report r, String reportPath, String made)
	{
		CompareFile f = new CompareFile();
		f.from = r.before.cacheId;
		f.to = r.after.cacheId;
		f.made = made;
		f.report = reportPath;
		for (CacheCompare.Finding x : r.findings)
		{
			Item i = new Item();
			i.key = x.key(f.to);
			i.kind = x.kind.name();
			i.look = x.kind.look;
			i.title = x.kind.title;
			i.text = x.text;
			i.x = x.x;
			i.y = x.y;
			i.plane = x.plane;
			i.ref = x.ref;
			i.details = new ArrayList<>(x.details);
			i.tiles = x.tiles.clone();
			f.items.add(i);
		}
		return f;
	}

	/** The file for a comparison of these two caches. */
	public static File file(File ws, int from, int to)
	{
		return new File(ws, DIR + "/" + from + "-to-" + to + ".json");
	}

	public void write(File f) throws IOException
	{
		Files.createDirectories(f.getAbsoluteFile().getParentFile().toPath());
		Files.write(f.toPath(), GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
	}

	public static CompareFile read(File f) throws IOException
	{
		return GSON.fromJson(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), CompareFile.class);
	}

	/** The newest comparison from cache {@code from} (by the time it was made), or null. */
	public static CompareFile newest(File ws, int from)
	{
		File[] all = new File(ws, DIR).listFiles((d, n) -> n.startsWith(from + "-to-") && n.endsWith(".json"));
		CompareFile best = null;
		if (all != null)
		{
			for (File f : all)
			{
				try
				{
					CompareFile c = read(f);
					if (c != null && c.from == from && (best == null || String.valueOf(c.made).compareTo(String.valueOf(best.made)) > 0))
					{
						best = c;
					}
				}
				catch (IOException | RuntimeException ignored)
				{
					// a broken file is left out
				}
			}
		}
		return best;
	}

	/** The findings that need a look. */
	public List<Item> looks()
	{
		List<Item> out = new ArrayList<>();
		for (Item i : items)
		{
			if (i.look)
			{
				out.add(i);
			}
		}
		return out;
	}
}
