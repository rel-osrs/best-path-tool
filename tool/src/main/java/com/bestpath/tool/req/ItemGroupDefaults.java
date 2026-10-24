package com.bestpath.tool.req;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Each item group's default item: the one whose picture the plugin shows when a step needs an
 * item from the group and the player has none of them. Chosen in the Data Tool's Item groups window and kept in
 * {@code data/manual/item-group-defaults.tsv}: {@code group <tab> item id <tab> chosen by <tab> time <tab> item
 * name (for reading only)}.
 */
public final class ItemGroupDefaults
{
	public static final File FILE = new File(com.bestpath.tool.ToolSettings.findWorkspace(), "data/manual/item-group-defaults.tsv");

	private static final String HEADER = "# Each item group's default item: its picture is shown when the player has none of the group.\n"
		+ "# group <tab> item id <tab> chosen by <tab> time <tab> item name (for reading only). Chosen in the Data Tool: Item groups...\n";

	/** One group's choice. */
	public static final class Choice
	{
		public final String group;
		public final int itemId;
		public final String author;
		public final String time;
		public final String itemName;

		public Choice(String group, int itemId, String author, String time, String itemName)
		{
			this.group = group;
			this.itemId = itemId;
			this.author = author;
			this.time = time;
			this.itemName = itemName;
		}
	}

	private ItemGroupDefaults()
	{
	}

	/** Group name to its choice (empty if the file is missing); bad lines are reported and skipped. */
	public static synchronized Map<String, Choice> all()
	{
		Map<String, Choice> m = new LinkedHashMap<>();
		if (!FILE.isFile())
		{
			return m;
		}
		try
		{
			for (String line : Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8))
			{
				if (line.isBlank() || line.startsWith("#"))
				{
					continue;
				}
				String[] p = line.split("\t", -1);
				try
				{
					m.put(p[0].trim(), new Choice(p[0].trim(), Integer.parseInt(p[1].trim()), p.length > 2 ? p[2] : "",
						p.length > 3 ? p[3] : "", p.length > 4 ? p[4] : ""));
				}
				catch (RuntimeException e)
				{
					System.err.println("item group defaults: bad line: " + line);
				}
			}
		}
		catch (IOException e)
		{
			System.err.println("item group defaults: " + e.getMessage());
		}
		return Collections.unmodifiableMap(m);
	}

	/** Sets (or replaces) one group's default and writes the file, keeping the groups in the item groups' order. */
	public static synchronized Choice set(String group, int itemId, String itemName, String author) throws IOException
	{
		Map<String, Choice> m = new LinkedHashMap<>(all());
		Choice c = new Choice(group, itemId, author, Instant.now().toString(), itemName == null ? "" : itemName);
		m.put(group, c);
		List<String> order = new ArrayList<>(ItemGroupFile.groups().keySet());
		StringBuilder sb = new StringBuilder(HEADER);
		for (String g : order)
		{
			if (m.containsKey(g))
			{
				append(sb, m.remove(g));
			}
		}
		for (Choice left : m.values()) // groups no longer in item-groups.tsv stay until someone removes them
		{
			append(sb, left);
		}
		File tmp = new File(FILE.getPath() + ".tmp");
		Files.write(tmp.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
		Files.move(tmp.toPath(), FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		return c;
	}

	/** Removes one group's default (nothing happens if it has none). */
	public static synchronized void remove(String group) throws IOException
	{
		Map<String, Choice> m = new LinkedHashMap<>(all());
		if (m.remove(group) == null)
		{
			return;
		}
		StringBuilder sb = new StringBuilder(HEADER);
		for (Choice c : m.values())
		{
			append(sb, c);
		}
		File tmp = new File(FILE.getPath() + ".tmp");
		Files.write(tmp.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
		Files.move(tmp.toPath(), FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	private static void append(StringBuilder sb, Choice c)
	{
		sb.append(c.group).append('\t').append(c.itemId).append('\t').append(clean(c.author)).append('\t').append(c.time)
			.append('\t').append(clean(c.itemName)).append('\n');
	}

	private static String clean(String s)
	{
		return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
	}
}
