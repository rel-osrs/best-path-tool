package com.bestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestpath.api.BestPathApi;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * The public API module (api/, docs/API.md) stays plain Java (it imports only JDK types), so
 * another plugin's developer can copy it into their plugin; and its names are the ones docs/API.md promises.
 */
public class ApiBoundaryTest
{
	@Test
	public void apiImportsOnlyJava() throws IOException
	{
		Path root = Paths.get("api", "src", "main", "java");
		assertTrue("api/ sources not found from " + Paths.get("").toAbsolutePath(), Files.isDirectory(root));
		List<String> bad = new ArrayList<>();
		List<Path> files;
		try (Stream<Path> s = Files.walk(root))
		{
			files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
		assertFalse(files.isEmpty());
		for (Path f : files)
		{
			for (String line : Files.readAllLines(f, StandardCharsets.UTF_8))
			{
				String t = line.trim();
				if (t.startsWith("import ") && !t.startsWith("import java.") && !t.startsWith("import static java."))
				{
					bad.add(f.getFileName() + ": " + t);
				}
			}
		}
		assertEquals(List.of(), bad);
	}

	/** Shortest Path's message names and keys (its plugin messages, read in reference/shortest-path 1898397). */
	@Test
	public void theNamesAreShortestPaths()
	{
		assertEquals("path", BestPathApi.PATH);
		assertEquals("clear", BestPathApi.CLEAR);
		assertEquals("query", BestPathApi.QUERY);
		assertEquals("result", BestPathApi.RESULT);
		assertEquals("getTarget", BestPathApi.GET_TARGET);
		assertEquals("currentTarget", BestPathApi.CURRENT_TARGET);
		assertEquals("target", BestPathApi.TARGET);
		assertEquals("start", BestPathApi.START);
		assertEquals("config", BestPathApi.CONFIG);
		assertEquals("id", BestPathApi.ID);
		assertEquals("bestpath", BestPathApi.NAMESPACE);
		assertEquals(1, BestPathApi.API_VERSION);
	}
}
