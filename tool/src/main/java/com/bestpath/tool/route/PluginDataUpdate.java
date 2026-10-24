package com.bestpath.tool.route;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The Data Tool's "Update plugin data": what a fresh export ({@link PluginExportCli}, written to a folder of its own)
 * changes in the plugin's data, and putting it in place. Only after the export's own check passed: the plugin's files
 * are never half replaced by a failed export. The summary file's "made" line (the time of the export) does not count
 * as a change.
 */
public final class PluginDataUpdate
{
	/** The summary file every export writes (format, cache, time made, fingerprint, files). */
	static final String SUMMARY = "bestpath-data.txt";

	private PluginDataUpdate()
	{
	}

	/** What a fresh export changes. Immutable. */
	public static final class Report
	{
		private final List<String> changed;
		private final List<String> added;
		private final List<String> onlyOld;
		private final List<String> transportsAdded;
		private final List<String> transportsRemoved;
		private final List<String> transportsChanged;
		private final long newBytes;

		Report(List<String> changed, List<String> added, List<String> onlyOld, List<String> transportsAdded,
			List<String> transportsRemoved, List<String> transportsChanged, long newBytes)
		{
			this.changed = Collections.unmodifiableList(changed);
			this.added = Collections.unmodifiableList(added);
			this.onlyOld = Collections.unmodifiableList(onlyOld);
			this.transportsAdded = Collections.unmodifiableList(transportsAdded);
			this.transportsRemoved = Collections.unmodifiableList(transportsRemoved);
			this.transportsChanged = Collections.unmodifiableList(transportsChanged);
			this.newBytes = newBytes;
		}

		/** Files whose contents differ (the summary file only when more than its time changed). */
		public List<String> getChanged()
		{
			return changed;
		}

		/** Files the plugin does not have yet. */
		public List<String> getAdded()
		{
			return added;
		}

		/** Files the plugin has that the export did not write (left alone). */
		public List<String> getOnlyOld()
		{
			return onlyOld;
		}

		/** Transports new in the export ("id  name"). */
		public List<String> getTransportsAdded()
		{
			return transportsAdded;
		}

		public List<String> getTransportsRemoved()
		{
			return transportsRemoved;
		}

		/** Transports whose line changed (an end moved, its needs, ticks...). */
		public List<String> getTransportsChanged()
		{
			return transportsChanged;
		}

		/** True when nothing but the export's time differs: the plugin already has this data. */
		public boolean isNothingChanged()
		{
			return changed.isEmpty() && added.isEmpty();
		}

		/** The report in words, for the window: what changes for the plugin. */
		public List<String> lines()
		{
			List<String> out = new ArrayList<>();
			if (isNothingChanged())
			{
				out.add("Nothing changed: the plugin already has this data.");
				return out;
			}
			out.add("Files changed: " + (changed.isEmpty() ? "none" : String.join(", ", changed)));
			if (!added.isEmpty())
			{
				out.add("Files new: " + String.join(", ", added));
			}
			if (!onlyOld.isEmpty())
			{
				out.add("Left alone (not written by the export): " + String.join(", ", onlyOld));
			}
			out.add(String.format(java.util.Locale.ROOT, "Transports: %d new, %d removed, %d changed", transportsAdded.size(),
				transportsRemoved.size(), transportsChanged.size()));
			list(out, "  new: ", transportsAdded);
			list(out, "  removed: ", transportsRemoved);
			list(out, "  changed: ", transportsChanged);
			out.add(String.format(java.util.Locale.ROOT, "All data files: %,d bytes", newBytes));
			return out;
		}

		private static void list(List<String> out, String head, List<String> items)
		{
			int shown = Math.min(items.size(), 25);
			for (int i = 0; i < shown; i++)
			{
				out.add(head + items.get(i));
			}
			if (items.size() > shown)
			{
				out.add(head + "... and " + (items.size() - shown) + " more");
			}
		}
	}

	/** What the export in {@code fresh} changes compared with the plugin's data in {@code plugin}. */
	public static Report compare(File plugin, File fresh) throws IOException
	{
		List<String> changed = new ArrayList<>();
		List<String> added = new ArrayList<>();
		List<String> onlyOld = new ArrayList<>();
		long bytes = 0;
		TreeSet<String> names = new TreeSet<>(names(fresh));
		for (String n : names)
		{
			byte[] now = Files.readAllBytes(new File(fresh, n).toPath());
			bytes += now.length;
			File old = new File(plugin, n);
			if (!old.isFile())
			{
				added.add(n);
			}
			else if (!same(n, Files.readAllBytes(old.toPath()), now))
			{
				changed.add(n);
			}
		}
		for (String n : new TreeSet<>(names(plugin)))
		{
			if (!names.contains(n))
			{
				onlyOld.add(n);
			}
		}
		Map<String, String> before = transports(new File(plugin, "transports.tsv"));
		Map<String, String> after = transports(new File(fresh, "transports.tsv"));
		List<String> tAdded = new ArrayList<>();
		List<String> tRemoved = new ArrayList<>();
		List<String> tChanged = new ArrayList<>();
		for (Map.Entry<String, String> e : after.entrySet())
		{
			String was = before.get(e.getKey());
			if (was == null)
			{
				tAdded.add(describe(e.getValue()));
			}
			else if (!was.equals(e.getValue()))
			{
				tChanged.add(describe(e.getValue()));
			}
		}
		for (Map.Entry<String, String> e : before.entrySet())
		{
			if (!after.containsKey(e.getKey()))
			{
				tRemoved.add(describe(e.getValue()));
			}
		}
		return new Report(changed, added, onlyOld, tAdded, tRemoved, tChanged, bytes);
	}

	/** Copies the changed and new files of the export into the plugin's data folder (each file whole, then renamed in). */
	public static void install(File fresh, File plugin, Report r) throws IOException
	{
		Files.createDirectories(plugin.toPath());
		List<String> files = new ArrayList<>(r.getChanged());
		files.addAll(r.getAdded());
		// the summary last: it lists every file's size and hash, so it goes in once the files it describes are there
		files.sort((a, b) -> Boolean.compare(a.equals(SUMMARY), b.equals(SUMMARY)));
		for (String n : files)
		{
			File tmp = new File(plugin, n + ".new");
			Files.copy(new File(fresh, n).toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
			Files.move(tmp.toPath(), new File(plugin, n).toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static List<String> names(File dir)
	{
		String[] n = dir.list((d, name) -> new File(d, name).isFile() && !name.endsWith(".new"));
		return n == null ? List.of() : Arrays.asList(n);
	}

	/** The same contents; for the summary file, apart from its "made" line. */
	static boolean same(String name, byte[] a, byte[] b)
	{
		if (Arrays.equals(a, b))
		{
			return true;
		}
		if (!name.equals(SUMMARY))
		{
			return false;
		}
		return withoutMade(a).equals(withoutMade(b));
	}

	private static List<String> withoutMade(byte[] text)
	{
		List<String> out = new ArrayList<>();
		for (String line : new String(text, StandardCharsets.UTF_8).split("\r?\n"))
		{
			if (!line.startsWith("made\t"))
			{
				out.add(line);
			}
		}
		return out;
	}

	/** transports.tsv: id to its whole line (comment lines left out); empty when there is no file. */
	private static Map<String, String> transports(File f) throws IOException
	{
		Map<String, String> out = new LinkedHashMap<>();
		if (!f.isFile())
		{
			return out;
		}
		for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			int tab = line.indexOf('\t');
			out.put(tab < 0 ? line : line.substring(0, tab), line);
		}
		return out;
	}

	/** "Name (id)" from a transports.tsv line (columns: id, kind, name, ...). */
	private static String describe(String line)
	{
		String[] c = line.split("\t", -1);
		return (c.length > 2 ? c[2] : "?") + " (" + c[0] + ")";
	}
}
