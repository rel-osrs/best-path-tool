package com.bestpath.tool.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.tool.cache.Backups;
import com.bestpath.tool.cache.CacheInfo;
import com.bestpath.tool.cache.DataCache;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Moving the data to a new cache: the preview's report and the move. */
class CacheMoveTest
{
	@TempDir
	File ws;

	private void write(String path, String text) throws IOException
	{
		File f = new File(ws, path);
		Files.createDirectories(f.getParentFile().toPath());
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private String read(String path) throws IOException
	{
		return new String(Files.readAllBytes(new File(ws, path).toPath()), StandardCharsets.UTF_8);
	}

	@Test
	void tablesAreComparedByTheirFirstColumn()
	{
		List<String> before = Arrays.asList("# made from cache 2735", "a\t1", "b\t2", "c\t3");
		List<String> after = Arrays.asList("# made from cache 2740", "a\t1", "b\t5", "d\t4");
		assertEquals(Arrays.asList("changed: b\t2  ->  b\t5", "gone: c\t3", "new: d\t4"), CacheMove.keyed(before, after));
		assertEquals(Collections.emptyList(), CacheMove.keyed(before, Arrays.asList("# another comment", "a\t1", "b\t2", "c\t3")));
		// the source text's cache number alone is no change
		assertEquals(Collections.emptyList(), CacheMove.keyed(Arrays.asList("s\tdig\tcache 2735 table 4 row 1"),
			Arrays.asList("s\tdig\tcache 2740 table 4 row 1")));
	}

	@Test
	void thePreviewRunsThreeProgramsIntoItsFolder()
	{
		File work = CacheMove.work(ws, 2740);
		List<List<String>> cmds = CacheMove.commands("java", "cp", new File(ws, "cache/openrs2-2740"), work, 100);
		assertEquals(3, cmds.size());
		assertEquals("com.bestpath.tool.clue.ClueExtractCli", cmds.get(0).get(CacheMove.MAIN_CLASS_AT));
		assertEquals("com.bestpath.tool.route.BanksCli", cmds.get(1).get(CacheMove.MAIN_CLASS_AT));
		assertEquals("com.bestpath.tool.route.PluginExportCli", cmds.get(2).get(CacheMove.MAIN_CLASS_AT));
		assertEquals(new File(work, "plugin").getAbsolutePath(), cmds.get(2).get(CacheMove.MAIN_CLASS_AT + 2));
		assertEquals("100", cmds.get(2).get(CacheMove.MAIN_CLASS_AT + 3));
	}

	@Test
	void theReportListsTheClueStepAndPluginDataChanges() throws Exception
	{
		write(CacheMove.CLUES, "# cache 2735\nstep-1\tdig\n");
		write(CacheMove.BANKS, "# banks\nbank-1\tx\n");
		write(Backups.PLUGIN_DATA + "/bestpath-data.txt", "cache\t2735\t241\nmade\tx\n");
		write(Backups.PLUGIN_DATA + "/boats.tsv", "raft\t1\n");
		File work = CacheMove.work(ws, 2740);
		write("cache/cache-move/2740/clue-steps.tsv", "# cache 2740\nstep-1\tsearch\n");
		write("cache/cache-move/2740/banks.tsv", "# banks\nbank-1\tx\n");
		write("cache/cache-move/2740/plugin/bestpath-data.txt", "cache\t2740\t241\nmade\ty\n");
		write("cache/cache-move/2740/plugin/boats.tsv", "raft\t2\n");
		String md = CacheMove.report(ws, 2735, 2740, work);
		assertTrue(md.contains("changed: step-1 | dig  ->  step-1 | search"), md);
		assertTrue(md.contains("## Banks (data/banks/banks.tsv)\n\nNo change."), md);
		assertTrue(md.contains("### boats.tsv"), md);
		assertTrue(md.contains("changed: raft | 1  ->  raft | 2"), md);
	}

	@Test
	void moveBacksUpThenPutsTheNewClueStepsAndBanksIntoData() throws Exception
	{
		write(CacheMove.CLUES, "old clues\n");
		write(CacheMove.BANKS, "old banks\n");
		write(Backups.PLUGIN_DATA + "/bestpath-data.txt", "cache\t2735\t241\n");
		File work = CacheMove.work(ws, 2740);
		assertThrows(IOException.class, () -> CacheMove.move(ws, 2740, work, s -> { }), "no preview yet");
		write("cache/cache-move/2740/clue-steps.tsv", "new clues\n");
		write("cache/cache-move/2740/banks.tsv", "new banks\n");
		CacheMove.move(ws, 2740, work, s -> { });
		assertEquals("new clues\n", read(CacheMove.CLUES));
		assertEquals("new banks\n", read(CacheMove.BANKS));
		File[] backups = new File(ws, Backups.DIR).listFiles();
		assertNotNull(backups);
		assertEquals(1, backups.length);
		assertEquals("old clues\n", new String(Files.readAllBytes(new File(backups[0], "data/clues/clue-steps.tsv").toPath()),
			StandardCharsets.UTF_8));
	}

	@Test
	void theProgramsUseTheCacheThePluginDataWasMadeFrom() throws Exception
	{
		assertThrows(IllegalStateException.class, () -> DataCache.folder(ws), "no cache at all");
		File older = new File(ws, "cache/older");
		Files.createDirectories(older.toPath());
		new CacheInfo(2735, 241, "2026-10-07T10:00:00Z", 1, "runescape").save(older);
		File d = new File(ws, "cache/some-folder");
		Files.createDirectories(d.toPath());
		new CacheInfo(2740, 241, "2026-10-14T10:45:06Z", 1, "runescape").save(d);
		assertEquals(d, DataCache.folder(ws), "no plugin data yet: the newest cache");
		write(Backups.PLUGIN_DATA + "/bestpath-data.txt", "cache\t2735\t241\n");
		assertEquals(older, DataCache.folder(ws), "the cache the plugin data was made from");
	}
}
