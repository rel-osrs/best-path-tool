package com.bestpath.tool.update;

import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.cache.Backups;
import com.bestpath.tool.cache.CacheInfo;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.extract.CollisionExtractor;
import com.bestpath.tool.extract.FlagGrid;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

/**
 * The game update comparison from the command line:
 * {@code gradlew -p tool cacheCompare --args="<old cache folder> <new cache folder> [report file]"}. Reads the old cache,
 * then the new one (one open at a time), compares them with the hand-made data ({@link CacheCompare}) and writes the
 * report, by default {@code docs/reports/<date>-cache-<old>-to-<new>.md}. Changes nothing in the data. Tool only.
 */
public final class CacheCompareCli
{
	private CacheCompareCli()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			System.out.println("Usage: cacheCompare --args=\"<old cache folder> <new cache folder> [report file]\"");
			System.exit(1);
		}
		File oldDir = new File(args[0]);
		File newDir = new File(args[1]);
		File ws = ToolSettings.findWorkspace();
		// a backup before the data meets another game cache
		CacheInfo next = CacheInfo.find(newDir);
		if (next != null)
		{
			Backups.beforeCache(ws, next.getOpenrs2Id(), System.out::println);
		}
		EditStore edits = new EditStore(new File(ws, "data/manual/changes.jsonl"));
		edits.load();
		List<Edit> active = edits.active();

		long t0 = System.nanoTime();
		CacheFacts before;
		OurData data;
		try (LoadedCache cache = LoadedCache.open(oldDir))
		{
			System.out.println("Old cache " + cache.getInfo().label() + " ...");
			FlagGrid base = CollisionExtractor.loadOrExtract(cache);
			data = OurData.load(ws, active, new ManualState(active, base.copy()));
			before = CacheFacts.read(cache, base, data.tiles(), System.out::println);
		}
		CacheFacts after;
		try (LoadedCache cache = LoadedCache.open(newDir))
		{
			System.out.println("New cache " + cache.getInfo().label() + " ...");
			FlagGrid base = CollisionExtractor.loadOrExtract(cache);
			after = CacheFacts.read(cache, base, data.tiles(), System.out::println);
			after.readRegions(base, FlagGrid.changedRegions(before.blocks, after.blocks));
		}
		// the old cache's tiles in the regions whose walls changed (its movement grid is saved beside it: quick)
		try (LoadedCache cache = LoadedCache.open(oldDir))
		{
			before.readRegions(CollisionExtractor.loadOrExtract(cache), after.regionFlags.keySet());
		}
		CacheCompare.Report report = CacheCompare.compare(before, after, data);
		File out = args.length > 2 ? new File(args[2])
			: new File(ws, "docs/reports/" + LocalDate.now() + "-cache-" + before.cacheId + "-to-" + after.cacheId + ".md");
		Files.createDirectories(out.getAbsoluteFile().getParentFile().toPath());
		Files.write(out.toPath(), CacheCompare.markdown(report).getBytes(StandardCharsets.UTF_8));
		// the findings for the Data Tool's cards and map
		java.nio.file.Path w = ws.toPath().toAbsolutePath().normalize();
		java.nio.file.Path o = out.toPath().toAbsolutePath().normalize();
		String rel = o.startsWith(w) ? w.relativize(o).toString().replace('\\', '/') : o.toString();
		CompareFile.of(report, rel, java.time.Instant.now().toString()).write(CompareFile.file(ws, before.cacheId, after.cacheId));

		System.out.println();
		for (CacheCompare.Kind k : CacheCompare.Kind.values())
		{
			int n = report.of(k).size();
			if (n > 0)
			{
				System.out.println((k.look ? "LOOK  " : "      ") + k.title + ": " + n);
			}
		}
		System.out.println(report.lookCount() + " things need a look. Report: " + out
			+ String.format(" (%.0f s)", (System.nanoTime() - t0) / 1e9));
	}
}
