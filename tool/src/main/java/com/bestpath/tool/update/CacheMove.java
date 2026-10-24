package com.bestpath.tool.update;

import com.bestpath.tool.cache.Backups;
import com.bestpath.tool.route.PluginDataUpdate;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Moving the data to a new game cache. First a preview, which changes nothing: the
 * clue steps and the banks (the two files in data/ made from the cache) and the plugin's data are made from the new
 * cache into {@code cache/cache-move/<new>/}, each by its own program ({@link #commands}), and {@link #report} lists
 * what would change, line by line. Then, on your click, {@link #move} puts the new clue steps and banks into
 * data/ (after the backup); the tool opens the new cache and "Update plugin data" makes the plugin's data from it.
 * Fixed rules only. Tool only.
 */
public final class CacheMove
{
	/** Where the previews go, in the workspace (git-ignored). */
	public static final String DIR = "cache/cache-move";
	static final String CLUES = "data/clues/clue-steps.tsv";
	static final String BANKS = "data/banks/banks.tsv";
	/** The most lines one list of the report shows. */
	static final int LIST_MAX = 200;
	/** Where each of {@link #commands} names its program (after java, the heap, two settings, -cp and the class path). */
	static final int MAIN_CLASS_AT = 6;

	private CacheMove()
	{
	}

	/** The preview's folder for cache {@code to}. */
	public static File work(File ws, int to)
	{
		return new File(ws, DIR + "/" + to);
	}

	/**
	 * The three programs of the preview, in order: the clue steps, the banks, the plugin's data (with its route check,
	 * {@code checks} random routes), each from {@code newCache} into {@code work}.
	 */
	public static List<List<String>> commands(String java, String classpath, File newCache, File work, int checks)
	{
		List<String> head = Arrays.asList(java, "-Xmx4g", "-Dorg.slf4j.simpleLogger.defaultLogLevel=error",
			"-Djava.awt.headless=true", "-cp", classpath);
		List<List<String>> out = new ArrayList<>();
		out.add(with(head, "com.bestpath.tool.clue.ClueExtractCli", newCache.getAbsolutePath(),
			new File(work, "clue-steps.tsv").getAbsolutePath()));
		out.add(with(head, "com.bestpath.tool.route.BanksCli", newCache.getAbsolutePath(),
			new File(work, "banks.tsv").getAbsolutePath()));
		out.add(with(head, "com.bestpath.tool.route.PluginExportCli", newCache.getAbsolutePath(),
			new File(work, "plugin").getAbsolutePath(), Integer.toString(checks)));
		return out;
	}

	private static List<String> with(List<String> head, String... rest)
	{
		List<String> out = new ArrayList<>(head);
		out.addAll(Arrays.asList(rest));
		return out;
	}

	/** Empties the preview's folder (made again by the programs). */
	public static void clear(File work) throws IOException
	{
		if (work.isDirectory())
		{
			try (java.util.stream.Stream<java.nio.file.Path> s = Files.walk(work.toPath()))
			{
				for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) s.sorted(java.util.Comparator.reverseOrder())::iterator)
				{
					Files.delete(p);
				}
			}
		}
		Files.createDirectories(work.toPath());
	}

	/** The preview's report (Markdown): what moving from cache {@code from} to {@code to} changes, file by file. */
	public static String report(File ws, int from, int to, File work) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		sb.append("# Moving to cache #").append(to).append(": what changes (from #").append(from).append(")\n\n");
		sb.append("Made by the Data Tool's preview (`CacheMove`): the clue steps, the banks and the plugin's data made from")
			.append(" cache #").append(to).append(" with every change you made, compared with what is in use now. Nothing")
			.append(" was changed.\n\n");
		sb.append("## Clue steps (").append(CLUES).append(")\n\n");
		list(sb, keyed(lines(new File(ws, CLUES)), lines(new File(work, "clue-steps.tsv"))));
		sb.append("## Banks (").append(BANKS).append(")\n\n");
		list(sb, keyed(lines(new File(ws, BANKS)), lines(new File(work, "banks.tsv"))));
		sb.append("## The plugin's data\n\n");
		sb.append("Made with the clue steps and banks now in data/: the two lists above reach the plugin's clues.tsv and")
			.append(" banks.tsv after moving and \"Update plugin data\".\n\n");
		File plugin = new File(ws, Backups.PLUGIN_DATA);
		File fresh = new File(work, "plugin");
		PluginDataUpdate.Report r = PluginDataUpdate.compare(plugin, fresh);
		for (String l : r.lines())
		{
			sb.append(l.isEmpty() ? "" : "    " + l).append('\n');
		}
		sb.append('\n');
		List<String> numberOnly = new ArrayList<>();
		List<String> binary = new ArrayList<>();
		for (String name : r.getChanged())
		{
			if (name.equals("transports.tsv") || name.equals("bestpath-data.txt"))
			{
				continue;
			}
			if (!name.endsWith(".tsv"))
			{
				binary.add(name);
				continue;
			}
			List<String> diff = keyed(lines(new File(plugin, name)), lines(new File(fresh, name)));
			if (diff.isEmpty())
			{
				numberOnly.add(name);
				continue;
			}
			sb.append("### ").append(name).append("\n\n");
			list(sb, diff);
		}
		if (!numberOnly.isEmpty())
		{
			sb.append("Only the cache number changed in: ").append(String.join(", ", numberOnly)).append(".\n\n");
		}
		if (!binary.isEmpty())
		{
			sb.append("Made again from the new cache (map files, not listed line by line; what changed on the map is in the")
				.append(" comparison report): ").append(String.join(", ", binary)).append(".\n");
		}
		return sb.toString();
	}

	/** Where the preview's report goes. */
	public static File reportFile(File ws, int from, int to)
	{
		return new File(ws, "docs/reports/" + LocalDate.now() + "-cache-" + from + "-to-" + to + "-plugin-data.md");
	}

	/**
	 * The differences of two tables. Lines are compared whole, with the cache number of their source text left out
	 * ("cache 2735 table 4 row 1499"), and comment lines (#) ignored; what is left over is "gone" or "new", and a gone and
	 * a new line with the same first column (the key, e.g. a clue step's id) make one "changed" line. In key order.
	 */
	static List<String> keyed(List<String> before, List<String> after)
	{
		Map<String, List<String>> a = byMasked(before);
		Map<String, List<String>> b = byMasked(after);
		Map<String, List<String>> gone = new java.util.TreeMap<>();
		Map<String, List<String>> fresh = new java.util.TreeMap<>();
		leftOver(a, b, gone);
		leftOver(b, a, fresh);
		TreeSet<String> keys = new TreeSet<>(gone.keySet());
		keys.addAll(fresh.keySet());
		List<String> out = new ArrayList<>();
		for (String k : keys)
		{
			List<String> x = gone.getOrDefault(k, new ArrayList<>());
			List<String> y = fresh.getOrDefault(k, new ArrayList<>());
			if (x.size() == 1 && y.size() == 1)
			{
				out.add("changed: " + x.get(0) + "  ->  " + y.get(0));
				continue;
			}
			for (String l : x)
			{
				out.add("gone: " + l);
			}
			for (String l : y)
			{
				out.add("new: " + l);
			}
		}
		return out;
	}

	/** The lines of {@code from} not matched by a line of {@code other} (same masked text, each used once), by key. */
	private static void leftOver(Map<String, List<String>> from, Map<String, List<String>> other, Map<String, List<String>> out)
	{
		from.forEach((masked, lines) ->
		{
			int matched = other.getOrDefault(masked, new ArrayList<>()).size();
			for (int i = matched; i < lines.size(); i++)
			{
				String l = lines.get(i);
				out.computeIfAbsent(l.split("\t", 2)[0], k -> new ArrayList<>()).add(l);
			}
		});
	}

	private static String mask(String l)
	{
		return l.replaceAll("cache #?\\d+", "cache #");
	}

	/** The table's lines (comments left out) by their masked text, in order. */
	private static Map<String, List<String>> byMasked(List<String> lines)
	{
		Map<String, List<String>> out = new LinkedHashMap<>();
		for (String l : lines)
		{
			if (!l.isEmpty() && !l.startsWith("#"))
			{
				out.computeIfAbsent(mask(l), k -> new ArrayList<>()).add(l);
			}
		}
		return out;
	}

	private static List<String> lines(File f) throws IOException
	{
		return f.isFile() ? Files.readAllLines(f.toPath(), StandardCharsets.UTF_8) : new ArrayList<>();
	}

	private static void list(StringBuilder sb, List<String> lines)
	{
		if (lines.isEmpty())
		{
			sb.append("No change.\n\n");
			return;
		}
		sb.append(lines.size()).append(" lines differ:\n\n");
		for (int i = 0; i < lines.size() && i < LIST_MAX; i++)
		{
			sb.append("- `").append(lines.get(i).replace("`", "'").replace("\t", " | ")).append("`\n");
		}
		if (lines.size() > LIST_MAX)
		{
			sb.append("- ... and ").append(lines.size() - LIST_MAX).append(" more\n");
		}
		sb.append('\n');
	}

	/**
	 * Moves the data to cache {@code to}: the backup (if not made yet), then the preview's clue steps and banks into
	 * data/. The tool then opens the new cache; "Update plugin data" makes the plugin's data from it.
	 */
	public static void move(File ws, int to, File work, java.util.function.Consumer<String> log) throws IOException
	{
		File clues = new File(work, "clue-steps.tsv");
		File banks = new File(work, "banks.tsv");
		if (!clues.isFile() || !banks.isFile())
		{
			throw new IOException("the preview for cache #" + to + " is not complete: make it first");
		}
		Backups.beforeCache(ws, to, log);
		Files.copy(clues.toPath(), new File(ws, CLUES).toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(banks.toPath(), new File(ws, BANKS).toPath(), StandardCopyOption.REPLACE_EXISTING);
		log.accept("Clue steps and banks from cache #" + to + " put into data/.");
	}
}
