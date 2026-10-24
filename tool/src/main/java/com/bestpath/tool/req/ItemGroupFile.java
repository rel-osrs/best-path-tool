package com.bestpath.tool.req;

import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The item groups requirement lines name ("item light source"), read from
 * {@code data/manual/item-groups.tsv}: {@code name <tab> ids <tab> source
 * <tab> names}. Read again whenever the file changes.
 */
public final class ItemGroupFile
{
	public static final File FILE = new File(com.bestpath.tool.ToolSettings.findWorkspace(), "data/manual/item-groups.tsv");
	/** Rune kinds: {@code kind <tab> rune ids <tab> staff ids <tab> source <tab> names}. */
	public static final File RUNES = new File(com.bestpath.tool.ToolSettings.findWorkspace(), "data/manual/runes.tsv");
	private static Map<String, RequirementText.RuneSource> runes = Collections.emptyMap();

	private static long loadedStamp = Long.MIN_VALUE;
	private static Map<String, int[]> groups = Collections.emptyMap();
	private static RequirementText.ItemGroups lookup = RequirementText.ItemGroups.NONE;

	private ItemGroupFile()
	{
	}

	/** Group names (as written) to item ids. */
	public static synchronized Map<String, int[]> groups()
	{
		reloadIfChanged();
		return groups;
	}

	public static synchronized RequirementText.ItemGroups lookup()
	{
		reloadIfChanged();
		return lookup;
	}

	/** Reads a requirement line with the current groups; throws {@link RequirementText.ParseException}. */
	public static Requirement parse(String text)
	{
		return RequirementText.parse(text, lookup());
	}

	private static void reloadIfChanged()
	{
		long stamp = FileStamp.of(FILE) * 31 + FileStamp.of(RUNES);
		if (stamp == loadedStamp)
		{
			return;
		}
		loadedStamp = stamp;
		Map<String, int[]> m = read(FILE);
		groups = Collections.unmodifiableMap(m);
		runes = Collections.unmodifiableMap(readRunes(RUNES));
		RequirementText.ItemGroups items = RequirementText.ItemGroups.of(m);
		Map<String, RequirementText.RuneSource> r = runes;
		lookup = new RequirementText.ItemGroups()
		{
			@Override
			public int[] ids(String name)
			{
				return items.ids(name);
			}

			@Override
			public RequirementText.RuneSource rune(String name)
			{
				return r.get(name.trim().toLowerCase(java.util.Locale.ROOT));
			}
		};
	}

	/** Rune kind names, e.g. "air", "law". */
	public static synchronized java.util.Set<String> runeKinds()
	{
		reloadIfChanged();
		return runes.keySet();
	}

	static Map<String, RequirementText.RuneSource> readRunes(File file)
	{
		Map<String, RequirementText.RuneSource> m = new LinkedHashMap<>();
		if (!file.isFile())
		{
			return m;
		}
		try
		{
			for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
			{
				if (line.isBlank() || line.startsWith("#"))
				{
					continue;
				}
				String[] p = line.split("\t", -1);
				try
				{
					m.put(p[0].trim().toLowerCase(java.util.Locale.ROOT), new RequirementText.RuneSource(ids(p[1]), ids(p[2])));
				}
				catch (RuntimeException e)
				{
					System.err.println("runes: bad line: " + line);
				}
			}
		}
		catch (IOException e)
		{
			System.err.println("runes: " + e.getMessage());
		}
		return m;
	}

	private static int[] ids(String list)
	{
		if (list.trim().isEmpty())
		{
			return new int[0];
		}
		String[] w = list.split(",");
		int[] v = new int[w.length];
		for (int i = 0; i < w.length; i++)
		{
			v[i] = Integer.parseInt(w[i].trim());
		}
		return v;
	}

	/** One group's line in the file. */
	public static final class Line
	{
		public final String name;
		public final int[] ids;
		/** Where the group came from, plus a note of each change made in the Data Tool. */
		public final String source;
		/** Item names, for reading only: "Name id; Name id". */
		public final String names;

		public Line(String name, int[] ids, String source, String names)
		{
			this.name = name;
			this.ids = ids;
			this.source = source == null ? "" : source;
			this.names = names == null ? "" : names;
		}
	}

	/** Every group line in file order (empty if the file is missing). */
	public static synchronized java.util.List<Line> lines() throws IOException
	{
		java.util.List<Line> out = new java.util.ArrayList<>();
		if (!FILE.isFile())
		{
			return out;
		}
		for (String line : Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8))
		{
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] p = line.split("\t", -1);
			out.add(new Line(p[0].trim(), ids(p.length > 1 ? p[1] : ""), p.length > 2 ? p[2] : "", p.length > 3 ? p[3] : ""));
		}
		return out;
	}

	/** Writes the groups (the comment lines at the top are kept); everything reads the new groups at once. */
	public static synchronized void write(java.util.List<Line> groupLines) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		if (FILE.isFile())
		{
			for (String line : Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8))
			{
				if (!line.startsWith("#"))
				{
					break;
				}
				sb.append(line).append('\n');
			}
		}
		for (Line l : groupLines)
		{
			StringBuilder ids = new StringBuilder();
			for (int id : l.ids)
			{
				ids.append(ids.length() == 0 ? "" : ",").append(id);
			}
			sb.append(clean(l.name)).append('\t').append(ids).append('\t').append(clean(l.source)).append('\t').append(clean(l.names)).append('\n');
		}
		File tmp = new File(FILE.getPath() + ".tmp");
		Files.write(tmp.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
		Files.move(tmp.toPath(), FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		loadedStamp = Long.MIN_VALUE; // the disk stamp is remembered for 2 s: read the new file now
	}

	private static String clean(String s)
	{
		return s.replace('\t', ' ').replace('\n', ' ');
	}

	/** The groups in a file (empty if it is missing); bad lines are reported and skipped. */
	static Map<String, int[]> read(File file)
	{
		Map<String, int[]> m = new LinkedHashMap<>();
		if (!file.isFile())
		{
			return m;
		}
		try
		{
			for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
			{
				if (line.isBlank() || line.startsWith("#"))
				{
					continue;
				}
				String[] p = line.split("\t");
				try
				{
					m.put(p[0].trim(), ids(p.length > 1 ? p[1] : "")); // a group may be empty while it is being made
				}
				catch (RuntimeException e)
				{
					System.err.println("item groups: bad line: " + line);
				}
			}
		}
		catch (IOException e)
		{
			System.err.println("item groups: " + e.getMessage());
		}
		return m;
	}
}
