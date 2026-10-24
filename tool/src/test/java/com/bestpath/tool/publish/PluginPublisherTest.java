package com.bestpath.tool.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginPublisherTest
{
	@TempDir
	Path dir;

	private static void write(File f, String text) throws IOException
	{
		Files.createDirectories(f.getParentFile().toPath());
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	/** A small workspace with every published file, plus things that must stay private. */
	private File workspace() throws IOException
	{
		File ws = dir.resolve("ws").toFile();
		for (String f : PluginPublisher.FILES)
		{
			write(new File(ws, f), f.equals("runelite-plugin.properties") ? "plugins=com.bestpath.BestPathPlugin\n" : f);
		}
		write(new File(ws, "build.gradle"), "plugins {}\n\n" + PluginPublisher.MARKER + " ----\napply from: 'dev.gradle'\n");
		write(new File(ws, "src/main/java/com/bestpath/A.java"), "class A {}");
		write(new File(ws, "src/main/resources/com/bestpath/data/x.tsv"), "x");
		write(new File(ws, "core/src/main/java/com/bestpath/core/C.java"), "class C {}");
		write(new File(ws, "core/src/test/java/CT.java"), "private");
		write(new File(ws, "api/src/main/java/com/bestpath/api/Api.java"), "class Api {}");
		write(new File(ws, "src/test/java/com/bestpath/dev/UiPreview.java"), "private");
		write(new File(ws, "tool/build.gradle"), "private");
		write(new File(ws, "NOTES.md"), "private");
		write(new File(ws, "dev.gradle"), "private");
		return ws;
	}

	@Test
	void onlyThePluginIsPublished() throws Exception
	{
		File ws = workspace();
		File pub = dir.resolve("public").toFile();
		PluginPublisher.Result r = PluginPublisher.publish(ws, pub, s -> { });
		assertTrue(new File(pub, "src/main/java/com/bestpath/A.java").isFile());
		assertTrue(new File(pub, "core/src/main/java/com/bestpath/core/C.java").isFile());
		assertTrue(new File(pub, "api/src/main/java/com/bestpath/api/Api.java").isFile());
		assertTrue(new File(pub, ".gitignore").isFile());
		for (String priv : List.of("core/src/test", "src/test/java/com/bestpath/dev", "tool", "NOTES.md", "dev.gradle"))
		{
			assertFalse(new File(pub, priv).exists(), priv);
		}
		String build = new String(Files.readAllBytes(new File(pub, "build.gradle").toPath()), StandardCharsets.UTF_8);
		assertEquals("plugins {}\n", build, "build.gradle stops before the workspace only line");
		assertEquals(0, r.changed.size());
		assertTrue(r.added.size() > 10);
	}

	@Test
	void publishingAgainUpdatesAndRemovesButLeavesGitAlone() throws Exception
	{
		File ws = workspace();
		File pub = dir.resolve("public").toFile();
		PluginPublisher.publish(ws, pub, s -> { });
		write(new File(pub, ".git/HEAD"), "ref");
		write(new File(pub, "build/libs/x.jar"), "jar");
		write(new File(pub, "src/main/java/com/bestpath/Hand.java"), "edited by hand");
		write(new File(ws, "src/main/java/com/bestpath/A.java"), "class A { int x; }");
		Files.delete(new File(ws, "src/main/resources/com/bestpath/data/x.tsv").toPath());

		PluginPublisher.Result r = PluginPublisher.publish(ws, pub, s -> { });
		assertEquals(List.of("src/main/java/com/bestpath/A.java"), r.changed);
		assertTrue(r.removed.contains("src/main/java/com/bestpath/Hand.java"));
		assertTrue(r.removed.contains("src/main/resources/com/bestpath/data/x.tsv"));
		assertFalse(new File(pub, "src/main/resources/com/bestpath/data").exists(), "empty folders go too");
		assertTrue(new File(pub, ".git/HEAD").isFile());
		assertTrue(new File(pub, "build/libs/x.jar").isFile());
	}

	@Test
	void aWrongFolderIsRefused() throws Exception
	{
		File ws = workspace();
		File other = dir.resolve("my-documents").toFile();
		write(new File(other, "letter.txt"), "keep me");
		assertThrows(IOException.class, () -> PluginPublisher.publish(ws, other, s -> { }));
		assertTrue(new File(other, "letter.txt").isFile());
		assertThrows(IOException.class, () -> PluginPublisher.publish(ws, new File(ws, "public"), s -> { }),
			"not inside the workspace");
	}
}
