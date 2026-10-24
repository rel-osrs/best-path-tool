package com.bestpath.tool.transport;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the Shortest Path plugin's transport files ({@code data/shortest-path/*.tsv}, copied with its developers'
 * permission, see SOURCE.md there). Only their menu texts are used: as hints for matching our transports' menu
 * options ({@link com.bestpath.tool.menu.MenuExport}). They are never ground truth.
 *
 * <p>File layout (their format): a header line of tab-separated column names (may start with {@code #}), then rows;
 * other {@code #} lines and empty rows are skipped. Each row has an Origin, a Destination or both.
 */
public final class ShortestPathTransports
{
	/**
	 * Items whose rows are left out (their files stay unchanged). 28564 = Trinket of fairies, a Leagues
	 * item, in teleportation_items.tsv.
	 */
	static final java.util.Set<Integer> LEFT_OUT_ITEMS = java.util.Set.of(28564);

	/** One data row as written, with the file and line it came from. */
	public static final class Row
	{
		public final String file;
		public final int line;
		/** {x, y, plane}, or null when empty. */
		public final int[] origin;
		public final int[] destination;
		/** Every column by its header name, as written (requirements included). */
		public final Map<String, String> columns;

		Row(String file, int line, int[] origin, int[] destination, Map<String, String> columns)
		{
			this.file = file;
			this.line = line;
			this.origin = origin;
			this.destination = destination;
			this.columns = columns;
		}
	}

	private ShortestPathTransports()
	{
	}

	/** All rows of every {@code .tsv} file in the folder (files in name order), or none if the folder is missing. */
	public static List<Row> load(File folder) throws IOException
	{
		File[] files = folder.listFiles((d, n) -> n.endsWith(".tsv"));
		if (files == null)
		{
			return Collections.emptyList();
		}
		Arrays.sort(files);
		List<Row> rows = new ArrayList<>();
		for (File f : files)
		{
			rows.addAll(read(f.getName(), Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)));
		}
		return rows;
	}

	/** The rows of one file's lines. */
	static List<Row> read(String file, List<String> lines)
	{
		List<Row> rows = new ArrayList<>();
		String[] header = null;
		for (int i = 0; i < lines.size(); i++)
		{
			String text = lines.get(i).replace("\r", "");
			if (header == null)
			{
				String h = text.startsWith("#") ? text.substring(1) : text;
				header = h.trim().isEmpty() ? null : h.split("\t", -1);
				if (header != null)
				{
					header[0] = header[0].trim();
				}
				continue;
			}
			if (text.startsWith("#") || text.trim().isEmpty())
			{
				continue;
			}
			String[] cells = text.split("\t", -1);
			Map<String, String> cols = new LinkedHashMap<>();
			for (int c = 0; c < header.length && c < cells.length; c++)
			{
				cols.put(header[c].trim(), cells[c].trim());
			}
			int[] origin = tile(cols.get("Origin"));
			int[] dest = tile(cols.get("Destination"));
			if (origin == null && dest == null)
			{
				continue;
			}
			if (needsOnlyLeftOutItems(cols.getOrDefault("Items", "")))
			{
				continue;
			}
			rows.add(new Row(file, i + 1, origin, dest, cols));
		}
		return rows;
	}

	/**
	 * True if every way of meeting the row's Items column ({@code a|b|...}, each one item) is a
	 * {@link #LEFT_OUT_ITEMS} item. Rows that also accept another item, or need several, stay.
	 */
	static boolean needsOnlyLeftOutItems(String items)
	{
		if (items.isEmpty())
		{
			return false;
		}
		for (String alt : items.replace(" ", "").split("\\|+"))
		{
			int eq = alt.indexOf('=');
			String id = eq < 0 ? alt : alt.substring(0, eq);
			if (alt.contains("&") || !id.matches("\\d+") || !LEFT_OUT_ITEMS.contains(Integer.parseInt(id)))
			{
				return false;
			}
		}
		return true;
	}

	private static int[] tile(String s)
	{
		if (s == null || s.isEmpty())
		{
			return null;
		}
		String[] p = s.trim().split("\\s+");
		if (p.length != 3)
		{
			return null;
		}
		try
		{
			return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
