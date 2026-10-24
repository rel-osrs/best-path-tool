package com.bestpath.tool.cache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * A backup made automatically before the data meets another game cache. The plugin data's manifest names the cache
 * the data was last made from; when the tool opens, or exports from, a cache with another OpenRS2 id, everything a new cache can change
 * is copied first into {@code cache/backups/<date>-before-cache-<id>/}: the game cache the data was made from, the map
 * pictures (changed regions are redrawn over the old ones), {@code data/} (the hand-made changes), the plugin's data
 * files and the tool's settings. One backup per change of cache: a complete backup from the same cache to the same
 * cache is not made again, and a game cache already copied by an earlier backup is not copied again (a downloaded
 * cache never changes). A backup is written under a "-partial" name and renamed when complete. Tool only.
 */
public final class Backups
{
	/** Where backups go, inside the workspace (git-ignored with the rest of cache/). */
	public static final String DIR = "cache/backups";
	static final String INFO = "backup.properties";
	static final String README = "README.txt";
	/** The plugin's data files, in the workspace. */
	public static final String PLUGIN_DATA = "src/main/resources/com/bestpath/data";
	static final String MANIFEST = "bestpath-data.txt";

	private Backups()
	{
	}

	/**
	 * Backs up before the data meets cache {@code nextId}, when that is not the cache the plugin data was made from.
	 * Returns the new backup's folder, or null when none was needed (same cache, an unknown id, no plugin data yet, or
	 * this change of cache already backed up).
	 */
	public static File beforeCache(File workspace, int nextId, Consumer<String> log) throws IOException
	{
		return beforeCache(workspace, toolSettings(), nextId, log);
	}

	/** As {@link #beforeCache(File, int, Consumer)}, with the tool's settings folder given (tests). */
	static File beforeCache(File workspace, File settings, int nextId, Consumer<String> log) throws IOException
	{
		int dataId = dataCacheId(workspace);
		if (nextId < 0 || dataId < 0 || nextId == dataId)
		{
			return null;
		}
		File done = find(workspace, dataId, nextId);
		if (done != null)
		{
			log.accept("Backup: already made before moving from cache #" + dataId + " to #" + nextId + ": " + done);
			return null;
		}
		return make(workspace, settings, dataId, nextId, log);
	}

	/** The OpenRS2 id in the plugin data's manifest ("cache 2735 241"), or -1. */
	public static int dataCacheId(File workspace) throws IOException
	{
		File f = new File(workspace, PLUGIN_DATA + "/" + MANIFEST);
		return f.isFile() ? manifestCacheId(f) : -1;
	}

	/** The OpenRS2 id on a manifest's "cache" line, or -1. */
	public static int manifestCacheId(File manifest) throws IOException
	{
		for (String line : Files.readAllLines(manifest.toPath(), StandardCharsets.UTF_8))
		{
			String[] p = line.split("\t");
			if (p.length >= 2 && p[0].equals("cache"))
			{
				try
				{
					return Integer.parseInt(p[1].trim());
				}
				catch (NumberFormatException e)
				{
					return -1;
				}
			}
		}
		return -1;
	}

	static File make(File workspace, File settings, int fromId, int toId, Consumer<String> log) throws IOException
	{
		File root = new File(workspace, DIR);
		String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ROOT))
			+ "-before-cache-" + toId;
		File dest = new File(root, name);
		for (int n = 2; dest.exists() || new File(root, dest.getName() + "-partial").exists(); n++)
		{
			dest = new File(root, name + "-" + n);
		}
		File partial = new File(root, dest.getName() + "-partial");
		Files.createDirectories(partial.toPath());
		log.accept("Backup: copying before moving from cache #" + fromId + " to #" + toId + " into " + dest + " ...");

		List<String> lines = new ArrayList<>();
		File cacheDir = cacheFolder(workspace, fromId);
		File earlier = cacheDir == null ? null : findCacheCopy(workspace, fromId);
		String cacheCopy = "game-cache-" + (cacheDir == null ? "openrs2-" + fromId : cacheDir.getName());
		if (cacheDir == null)
		{
			lines.add("- The game cache #" + fromId + " was not found in cache/: not copied.");
		}
		else if (earlier != null)
		{
			lines.add("- The game cache #" + fromId + " is already in an earlier backup (a downloaded cache never changes): "
				+ earlier.getParentFile().getName() + "/" + earlier.getName());
		}
		else
		{
			copy(cacheDir, new File(partial, cacheCopy), log);
			lines.add("- " + cacheCopy + "/: the game cache #" + fromId + " (was " + rel(workspace, cacheDir) + "/)");
		}
		copyIfThere(new File(workspace, "map-pictures"), new File(partial, "map-pictures"), log, lines,
			"the Data Tool's map pictures (was map-pictures/)");
		copyIfThere(new File(workspace, "data"), new File(partial, "data"), log, lines,
			"the hand-made data and every change you made (was data/)");
		copyIfThere(new File(workspace, PLUGIN_DATA), new File(partial, "plugin-data"), log, lines,
			"the plugin's data files (was " + PLUGIN_DATA + "/)");
		copyIfThere(settings, new File(partial, "tool-settings"), log, lines,
			"the Data Tool's settings (was " + settings + ")");

		Properties p = new Properties();
		p.setProperty("from.cache", Integer.toString(fromId));
		p.setProperty("to.cache", Integer.toString(toId));
		p.setProperty("made", LocalDateTime.now().toString());
		p.setProperty("game.cache.copy", cacheDir != null && earlier == null ? cacheCopy : "");
		try (OutputStream out = Files.newOutputStream(new File(partial, INFO).toPath()))
		{
			p.store(out, "BestPath Data Tool backup");
		}
		Files.write(new File(partial, README).toPath(), readme(fromId, toId, lines).getBytes(StandardCharsets.UTF_8));
		Files.move(partial.toPath(), dest.toPath());
		log.accept("Backup: done, " + dest);
		return dest;
	}

	/** The Data Tool's settings folder (~/.bestpath-tool, see ToolSettings.forUser). */
	private static File toolSettings()
	{
		return new File(System.getProperty("user.home"), ".bestpath-tool");
	}

	private static String readme(int fromId, int toId, List<String> lines)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("BestPath Data Tool backup, made ").append(LocalDateTime.now().withNano(0))
			.append(" before the data met game cache #").append(toId).append(".\n");
		sb.append("Everything here is as it was with cache #").append(fromId).append(".\n\n");
		sb.append("What is in it:\n");
		for (String l : lines)
		{
			sb.append(l).append('\n');
		}
		sb.append("\nTo go back: close the Data Tool, then copy each folder back where it was (shown above),\n")
			.append("replacing what is there. data/ and the plugin's data files are also in git.\n");
		return sb.toString();
	}

	/** A complete backup from {@code fromId} to {@code toId}, or null. */
	static File find(File workspace, int fromId, int toId) throws IOException
	{
		for (File b : backups(workspace))
		{
			Properties p = info(b);
			if (p != null && p.getProperty("from.cache", "").equals(Integer.toString(fromId))
				&& p.getProperty("to.cache", "").equals(Integer.toString(toId)))
			{
				return b;
			}
		}
		return null;
	}

	/** An earlier backup's copy of game cache {@code id}, or null. */
	static File findCacheCopy(File workspace, int id) throws IOException
	{
		for (File b : backups(workspace))
		{
			Properties p = info(b);
			if (p != null && p.getProperty("from.cache", "").equals(Integer.toString(id)))
			{
				String copy = p.getProperty("game.cache.copy", "");
				if (!copy.isEmpty() && new File(b, copy).isDirectory())
				{
					return new File(b, copy);
				}
			}
		}
		return null;
	}

	/** The complete backups (one still being written, or cut off, keeps its "-partial" name and is left out). */
	private static List<File> backups(File workspace)
	{
		List<File> out = new ArrayList<>();
		File[] all = new File(workspace, DIR).listFiles(File::isDirectory);
		if (all != null)
		{
			for (File b : all)
			{
				if (!b.getName().endsWith("-partial") && new File(b, INFO).isFile())
				{
					out.add(b);
				}
			}
		}
		return out;
	}

	private static Properties info(File backup) throws IOException
	{
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(new File(backup, INFO).toPath()))
		{
			p.load(in);
		}
		return p;
	}

	/** The folder in cache/ holding cache-info.properties with OpenRS2 id {@code id}, or null. */
	static File cacheFolder(File workspace, int id)
	{
		File[] all = new File(workspace, "cache").listFiles(File::isDirectory);
		if (all != null)
		{
			for (File d : all)
			{
				if (new File(d, CacheInfo.FILE_NAME).isFile())
				{
					CacheInfo info = CacheInfo.find(d);
					if (info != null && info.getOpenrs2Id() == id)
					{
						return d;
					}
				}
			}
		}
		return null;
	}

	private static void copyIfThere(File from, File to, Consumer<String> log, List<String> lines, String what)
		throws IOException
	{
		if (from.isDirectory())
		{
			copy(from, to, log);
			lines.add("- " + to.getName() + "/: " + what);
		}
	}

	/** Copies a folder and everything in it, keeping file times. */
	static void copy(File from, File to, Consumer<String> log) throws IOException
	{
		log.accept("Backup: copying " + from + " ...");
		Path src = from.toPath();
		Path dst = to.toPath();
		Files.walkFileTree(src, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
			{
				Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
			{
				Files.copy(file, dst.resolve(src.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static String rel(File workspace, File f)
	{
		Path w = workspace.toPath().toAbsolutePath().normalize();
		Path p = f.toPath().toAbsolutePath().normalize();
		return p.startsWith(w) ? w.relativize(p).toString().replace('\\', '/') : p.toString();
	}
}
