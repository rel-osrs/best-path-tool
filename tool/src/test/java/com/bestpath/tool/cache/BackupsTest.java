package com.bestpath.tool.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The backup before the data meets another game cache. */
class BackupsTest
{
	@TempDir
	File ws;

	@TempDir
	File settings;

	private final List<String> log = new ArrayList<>();

	private void write(String path, String text) throws IOException
	{
		File f = new File(ws, path);
		Files.createDirectories(f.getParentFile().toPath());
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private void dataFrom(int cacheId) throws IOException
	{
		write(Backups.PLUGIN_DATA + "/bestpath-data.txt", "# comment\nformat\tv0\ncache\t" + cacheId + "\t241\nmade\tx\n");
	}

	private void gameCache(int id) throws IOException
	{
		new CacheInfo(id, 241, "2026-10-07T10:45:05Z", 1, "runescape").save(mkdir("cache/openrs2-" + id));
		write("cache/openrs2-" + id + "/disk/cache/main_file_cache.dat2", "cache " + id);
	}

	private File mkdir(String path) throws IOException
	{
		File d = new File(ws, path);
		Files.createDirectories(d.toPath());
		return d;
	}

	private static String read(File f) throws IOException
	{
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
	}

	@Test
	void theSameCacheNeedsNoBackup() throws Exception
	{
		dataFrom(2735);
		gameCache(2735);
		assertNull(Backups.beforeCache(ws, settings, 2735, log::add));
		assertFalse(new File(ws, Backups.DIR).exists());
	}

	@Test
	void anUnknownCacheOrNoPluginDataNeedsNoBackup() throws Exception
	{
		assertNull(Backups.beforeCache(ws, settings, 2740, log::add), "no plugin data yet");
		dataFrom(2735);
		assertNull(Backups.beforeCache(ws, settings, -1, log::add), "a cache without cache-info.properties");
	}

	@Test
	void anotherCacheIsBackedUpFirstWithEverythingItCanChange() throws Exception
	{
		dataFrom(2735);
		gameCache(2735);
		write("data/manual/changes.jsonl", "{\"id\":\"e1\"}\n");
		write("map-pictures/index.tsv", "12850\tabc\n");
		Files.write(new File(settings, "settings.properties").toPath(), "cache.last=x\n".getBytes(StandardCharsets.UTF_8));

		File b = Backups.beforeCache(ws, settings, 2740, log::add);
		assertNotNull(b);
		assertTrue(b.getName().endsWith("-before-cache-2740"), b.getName());
		assertEquals("{\"id\":\"e1\"}\n", read(new File(b, "data/manual/changes.jsonl")));
		assertEquals("12850\tabc\n", read(new File(b, "map-pictures/index.tsv")));
		assertTrue(read(new File(b, "plugin-data/bestpath-data.txt")).contains("cache\t2735\t241"));
		assertEquals("cache 2735", read(new File(b, "game-cache-openrs2-2735/disk/cache/main_file_cache.dat2")));
		assertEquals("cache.last=x\n", read(new File(b, "tool-settings/settings.properties")));
		assertTrue(read(new File(b, "README.txt")).contains("To go back"));
		File[] left = new File(ws, Backups.DIR).listFiles((d, n) -> n.endsWith("-partial"));
		assertEquals(0, left.length, "no partial backup is left");
	}

	@Test
	void oneBackupPerChangeOfCache() throws Exception
	{
		dataFrom(2735);
		gameCache(2735);
		write("data/manual/changes.jsonl", "a\n");
		assertNotNull(Backups.beforeCache(ws, settings, 2740, log::add));
		// the tool opened again on the new cache before the plugin data was made from it: nothing more
		assertNull(Backups.beforeCache(ws, settings, 2740, log::add));
		assertEquals(1, new File(ws, Backups.DIR).listFiles().length);
	}

	@Test
	void aGameCacheAlreadyBackedUpIsNotCopiedAgain() throws Exception
	{
		dataFrom(2735);
		gameCache(2735);
		write("data/manual/changes.jsonl", "a\n");
		File first = Backups.beforeCache(ws, settings, 2740, log::add);
		File second = Backups.beforeCache(ws, settings, 2741, log::add);
		assertNotNull(second);
		assertFalse(new File(second, "game-cache-openrs2-2735").exists());
		assertTrue(read(new File(second, "README.txt")).contains(first.getName() + "/game-cache-openrs2-2735"));
		assertTrue(new File(second, "data/manual/changes.jsonl").isFile(), "the data is copied every time");
	}

	@Test
	void aBackupCutOffHalfwayDoesNotCount() throws Exception
	{
		dataFrom(2735);
		mkdir(Backups.DIR + "/2026-10-19-1200-before-cache-2740-partial");
		assertNotNull(Backups.beforeCache(ws, settings, 2740, log::add));
	}

	@Test
	void theManifestsCacheLineIsRead() throws Exception
	{
		dataFrom(2740);
		assertEquals(2740, Backups.dataCacheId(ws));
	}
}
