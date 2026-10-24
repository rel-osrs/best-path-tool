package com.bestpath.tool.update;

import com.bestpath.tool.ToolSettings;
import com.bestpath.tool.cache.Backups;
import com.bestpath.tool.cache.CacheInfo;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The preview of moving to a new cache from the command line, as the "Preview the plugin's data" button does:
 * {@code gradlew -p tool cacheMovePreview --args="<new cache folder> [check routes | report]"}
 * ("report": only the report again, from the preview already made). Runs {@link CacheMove}'s
 * three programs one after the other, then writes the report. Changes nothing in the data. Tool only.
 */
public final class CacheMovePreviewCli
{
	private CacheMovePreviewCli()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File newCache = new File(args[0]);
		boolean reportOnly = args.length > 1 && args[1].equals("report");
		int checks = args.length > 1 && !reportOnly ? Integer.parseInt(args[1]) : 100;
		File ws = ToolSettings.findWorkspace();
		CacheInfo info = CacheInfo.find(newCache);
		if (info == null || info.getOpenrs2Id() < 0)
		{
			throw new IllegalArgumentException("no cache-info.properties for " + newCache);
		}
		int from = Backups.dataCacheId(ws);
		int to = info.getOpenrs2Id();
		File work = CacheMove.work(ws, to);
		if (!reportOnly)
		{
			CacheMove.clear(work);
		}
		String java = new File(new File(System.getProperty("java.home"), "bin"),
			System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java").getPath();
		for (List<String> cmd : reportOnly ? Collections.<List<String>>emptyList()
			: CacheMove.commands(java, System.getProperty("java.class.path"), newCache, work, checks))
		{
			System.out.println("---- " + cmd.get(CacheMove.MAIN_CLASS_AT));
			int code = new ProcessBuilder(cmd).directory(ws).inheritIO().start().waitFor();
			if (code != 0)
			{
				System.out.println("Stopped (code " + code + "). Nothing was changed.");
				System.exit(code);
			}
		}
		File report = CacheMove.reportFile(ws, from, to);
		Files.createDirectories(report.getParentFile().toPath());
		Files.write(report.toPath(), CacheMove.report(ws, from, to, work).getBytes(StandardCharsets.UTF_8));
		System.out.println("Preview of #" + to + " (from #" + from + "): " + report + ". Nothing was changed.");
	}
}
