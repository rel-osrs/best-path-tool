package com.bestpath.tool.req;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Teleport categories and their adjustable tick cost, from
 * {@code data/manual/teleport-categories.tsv}: {@code category <tab> ticks <tab>
 * description}. A teleport with a category costs its category's ticks. Read
 * again when the file changes.
 */
public final class TeleportCategories
{
	public static final File FILE = new File(com.bestpath.tool.ToolSettings.findWorkspace(), "data/manual/teleport-categories.tsv");

	/** One category. */
	public static final class Category
	{
		public final String name;
		public final int ticks;
		public final String description;

		public Category(String name, int ticks, String description)
		{
			this.name = name;
			this.ticks = ticks;
			this.description = description;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private static long loadedStamp = Long.MIN_VALUE;
	private static Map<String, Category> byName = Collections.emptyMap();
	private static List<String> header = Collections.emptyList();

	private TeleportCategories()
	{
	}

	public static synchronized List<Category> all()
	{
		reload();
		return new ArrayList<>(byName.values());
	}

	/** The category (any case), or null. */
	public static synchronized Category get(String name)
	{
		reload();
		return name == null ? null : byName.get(name.trim().toLowerCase(Locale.ROOT));
	}

	/** Writes new tick costs, keeping the file's comments and order. */
	public static synchronized void save(List<Category> categories) throws IOException
	{
		reload();
		List<String> lines = new ArrayList<>(header);
		for (Category c : categories)
		{
			lines.add(c.name + "\t" + c.ticks + "\t" + c.description);
		}
		Files.write(FILE.toPath(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
		loadedStamp = Long.MIN_VALUE;
	}

	private static void reload()
	{
		long stamp = FileStamp.of(FILE);
		if (stamp == loadedStamp)
		{
			return;
		}
		loadedStamp = stamp;
		Map<String, Category> m = new LinkedHashMap<>();
		List<String> h = new ArrayList<>();
		if (FILE.isFile())
		{
			try
			{
				for (String line : Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8))
				{
					if (line.startsWith("#"))
					{
						h.add(line);
						continue;
					}
					String[] w = line.split("\t");
					if (w.length < 2)
					{
						continue;
					}
					try
					{
						Category c = new Category(w[0].trim(), Integer.parseInt(w[1].trim()), w.length > 2 ? w[2].trim() : "");
						m.put(c.name.toLowerCase(Locale.ROOT), c);
					}
					catch (NumberFormatException e)
					{
						System.err.println("teleport categories: bad line: " + line);
					}
				}
			}
			catch (IOException e)
			{
				System.err.println("teleport categories: " + e.getMessage());
			}
		}
		byName = Collections.unmodifiableMap(m);
		header = Collections.unmodifiableList(h);
	}
}
