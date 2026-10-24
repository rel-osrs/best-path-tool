package com.bestpath.tool.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** "Update plugin data": what a fresh export changes, and putting it into the plugin. */
class PluginDataUpdateTest
{
	@TempDir
	Path tmp;

	private static void write(File dir, String name, String text) throws IOException
	{
		Files.createDirectories(dir.toPath());
		Files.write(new File(dir, name).toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private static String read(File dir, String name) throws IOException
	{
		return new String(Files.readAllBytes(new File(dir, name).toPath()), StandardCharsets.UTF_8);
	}

	private static final String HEAD = "# comment\n#id\tkind\tname\n";

	@Test
	void onlyTheTimeOfTheExportChangedIsNothingChanged() throws IOException
	{
		File plugin = tmp.resolve("plugin").toFile();
		File fresh = tmp.resolve("fresh").toFile();
		write(plugin, "bestpath-data.txt", "format\tv0\nmade\t2026-10-17T17:23:54Z\nfingerprint\tabc\n");
		write(fresh, "bestpath-data.txt", "format\tv0\nmade\t2026-10-17T21:00:00Z\nfingerprint\tabc\n");
		write(plugin, "transports.tsv", HEAD + "t-1\tTWO_WAY\tGate\n");
		write(fresh, "transports.tsv", HEAD + "t-1\tTWO_WAY\tGate\n");
		PluginDataUpdate.Report r = PluginDataUpdate.compare(plugin, fresh);
		assertTrue(r.isNothingChanged());
		assertEquals(List.of("Nothing changed: the plugin already has this data."), r.lines());
	}

	@Test
	void changedFilesAndTransportsAreListedAndPutInPlace() throws IOException
	{
		File plugin = tmp.resolve("plugin").toFile();
		File fresh = tmp.resolve("fresh").toFile();
		write(plugin, "bestpath-data.txt", "made\t1\nfingerprint\told\n");
		write(fresh, "bestpath-data.txt", "made\t2\nfingerprint\tnew\n");
		write(plugin, "transports.tsv", HEAD + "t-1\tTWO_WAY\tGate\t1,1,0\nt-2\tTELEPORT\tDrakan's medallion: Slepe\t3808,9700,0\nt-3\tONE_WAY\tOld rope\n");
		write(fresh, "transports.tsv", HEAD + "t-1\tTWO_WAY\tGate\t1,1,0\nt-2\tTELEPORT\tDrakan's medallion: Slepe\t3808,9700,1\nt-4\tONE_WAY\tNew rope\n");
		write(plugin, "walls.bin", "same");
		write(fresh, "walls.bin", "same");
		write(fresh, "sea.bin", "new file");
		write(plugin, "old-only.tsv", "kept");

		PluginDataUpdate.Report r = PluginDataUpdate.compare(plugin, fresh);
		assertFalse(r.isNothingChanged());
		assertEquals(List.of("bestpath-data.txt", "transports.tsv"), r.getChanged());
		assertEquals(List.of("sea.bin"), r.getAdded());
		assertEquals(List.of("old-only.tsv"), r.getOnlyOld());
		assertEquals(List.of("New rope (t-4)"), r.getTransportsAdded());
		assertEquals(List.of("Old rope (t-3)"), r.getTransportsRemoved());
		assertEquals(List.of("Drakan's medallion: Slepe (t-2)"), r.getTransportsChanged());
		assertTrue(r.lines().contains("Transports: 1 new, 1 removed, 1 changed"));

		PluginDataUpdate.install(fresh, plugin, r);
		assertEquals(read(fresh, "transports.tsv"), read(plugin, "transports.tsv"));
		assertEquals(read(fresh, "bestpath-data.txt"), read(plugin, "bestpath-data.txt"));
		assertEquals("new file", read(plugin, "sea.bin"));
		assertEquals("kept", read(plugin, "old-only.tsv"), "a file the export did not write is left alone");
		assertFalse(new File(plugin, "transports.tsv.new").exists());
		assertTrue(PluginDataUpdate.compare(plugin, fresh).isNothingChanged(), "afterwards the plugin has the export's data");
	}

	@Test
	void aFirstExportHasEveryFileNew() throws IOException
	{
		File plugin = tmp.resolve("none").toFile();
		File fresh = tmp.resolve("fresh").toFile();
		write(fresh, "transports.tsv", HEAD + "t-1\tTWO_WAY\tGate\n");
		PluginDataUpdate.Report r = PluginDataUpdate.compare(plugin, fresh);
		assertEquals(List.of("transports.tsv"), r.getAdded());
		assertEquals(List.of("Gate (t-1)"), r.getTransportsAdded());
		PluginDataUpdate.install(fresh, plugin, r);
		assertTrue(new File(plugin, "transports.tsv").isFile());
	}
}
