package com.bestpath.tool.publish;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Copies the plugin, and only the plugin, from the workspace into the public plugin repo's folder. The workspace is
 * the private home of the plugin, the Data Tool, the data and the notes; the public repo is a copy made by this.
 * <p>
 * Published: the plugin's code and data ({@code src/main}), the pure-Java logic it compiles in ({@code core/src/main/java})
 * and the API names ({@code api/src/main/java}), the RuneLite template's build files ({@code build.gradle} up to its
 * "workspace only" line, so without the developer tasks in {@code dev.gradle}), the launcher
 * {@code BestPathPluginTest}, the licence, README, third-party notices and {@code docs/API.md}. Nothing else: no Data
 * Tool, no data/ or docs/ notes, no tests (they need the workspace), no private notes.
 * <p>
 * The public folder ends up holding exactly that: a file there that is not published is deleted, except git's own
 * folder and build output. So never change files in the public folder by hand: change them here and publish again.
 */
public final class PluginPublisher
{
	/** The line in build.gradle after which nothing is published. */
	public static final String MARKER = "// ---- workspace only below this line";
	/** Single files, published as they are. */
	static final List<String> FILES = List.of(
		"settings.gradle", "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
		"gradle/wrapper/gradle-wrapper.properties", "LICENSE", "runelite-plugin.properties", "README.md",
		"THIRD_PARTY_NOTICES.md", "docs/API.md", ".gitattributes", "src/test/java/com/bestpath/BestPathPluginTest.java");
	/** Published when there (the Plugin Hub's picture, made later). */
	static final List<String> OPTIONAL = List.of("icon.png");
	/** Whole folders. */
	static final List<String> FOLDERS = List.of("src/main", "core/src/main/java", "api/src/main/java");
	/** Top-level names in the public folder that publishing never touches: git's own, and build output (ignored there). */
	static final Set<String> LEFT_ALONE = Set.of(".git", ".gradle", "build", "out", ".idea");
	static final String GITIGNORE = String.join("\n", ".gradle", "build", "out/", ".idea/", "*.iml", ".project",
		".settings/", ".classpath", "*.class", "*.tmp", "") ;

	private PluginPublisher()
	{
	}

	/** Where the public repo's folder goes unless you pick another: beside the workspace, "best-path". */
	public static File defaultFolder(File workspace)
	{
		return new File(workspace.getAbsoluteFile().getParentFile(), "best-path");
	}

	/** What publishing changed in the public folder (paths relative to it, with / between folders). */
	public static final class Result
	{
		public final List<String> added = new ArrayList<>();
		public final List<String> changed = new ArrayList<>();
		public final List<String> removed = new ArrayList<>();
		public int unchanged;

		public String summary()
		{
			return added.size() + " new, " + changed.size() + " changed, " + removed.size() + " removed, " + unchanged
				+ " unchanged";
		}
	}

	/** Every published file and its contents, by path in the public repo. */
	static Map<String, byte[]> contents(File workspace) throws IOException
	{
		Map<String, byte[]> out = new TreeMap<>();
		for (String f : FILES)
		{
			File src = new File(workspace, f);
			if (!src.isFile())
			{
				throw new IOException("Missing in the workspace: " + f);
			}
			out.put(f, Files.readAllBytes(src.toPath()));
		}
		for (String f : OPTIONAL)
		{
			File src = new File(workspace, f);
			if (src.isFile())
			{
				out.put(f, Files.readAllBytes(src.toPath()));
			}
		}
		for (String dir : FOLDERS)
		{
			Path root = new File(workspace, dir).toPath();
			if (!Files.isDirectory(root))
			{
				throw new IOException("Missing in the workspace: " + dir);
			}
			try (Stream<Path> files = Files.walk(root))
			{
				for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator)
				{
					out.put(dir + "/" + root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
				}
			}
		}
		out.put("build.gradle", publicBuild(new File(workspace, "build.gradle")).getBytes(StandardCharsets.UTF_8));
		out.put(".gitignore", GITIGNORE.getBytes(StandardCharsets.UTF_8));
		return out;
	}

	/** build.gradle up to the "workspace only" line (the developer tasks after it stay in the workspace). */
	static String publicBuild(File build) throws IOException
	{
		String text = new String(Files.readAllBytes(build.toPath()), StandardCharsets.UTF_8);
		int at = text.indexOf(MARKER);
		if (at < 0)
		{
			throw new IOException("build.gradle has no line \"" + MARKER + " ...\": nothing published");
		}
		String nl = text.contains("\r\n") ? "\r\n" : "\n";
		return text.substring(0, at).replaceAll("\\s+$", "") + nl;
	}

	/**
	 * Makes {@code target} hold exactly the published files. Refuses a folder inside the workspace (or around it), and
	 * a folder with other things in it that isn't already a Best Path public copy, so a wrong choice deletes nothing.
	 */
	public static Result publish(File workspace, File target, Consumer<String> log) throws IOException
	{
		Path to = checkTarget(workspace, target);
		Map<String, byte[]> files = contents(workspace);
		Result r = new Result();
		writeFiles(target, files, r);
		removeOthers(to, files, r);
		log.accept("Published to " + to + ": " + r.summary() + ".");
		r.added.forEach(f -> log.accept("  new: " + f));
		r.changed.forEach(f -> log.accept("  changed: " + f));
		r.removed.forEach(f -> log.accept("  removed: " + f));
		return r;
	}

	/** The public folder's path, after checking it is outside the workspace and empty or already a public copy. */
	private static Path checkTarget(File workspace, File target) throws IOException
	{
		Path ws = workspace.getCanonicalFile().toPath();
		Path to = target.getCanonicalFile().toPath();
		if (to.startsWith(ws) || ws.startsWith(to))
		{
			throw new IOException("The public folder must be outside the workspace: " + to);
		}
		String[] there = target.list();
		if (there != null && Arrays.stream(there).anyMatch(n -> !LEFT_ALONE.contains(n)) && !isPublicCopy(target))
		{
			throw new IOException(to + " has other files in it and is not a Best Path plugin copy: nothing changed."
				+ " Pick an empty folder.");
		}
		return to;
	}

	/** Writes each published file that is new or different, counting them in {@code r}. */
	private static void writeFiles(File target, Map<String, byte[]> files, Result r) throws IOException
	{
		for (Map.Entry<String, byte[]> e : files.entrySet())
		{
			File f = new File(target, e.getKey());
			if (!f.isFile())
			{
				r.added.add(e.getKey());
			}
			else if (!Arrays.equals(Files.readAllBytes(f.toPath()), e.getValue()))
			{
				r.changed.add(e.getKey());
			}
			else
			{
				r.unchanged++;
				continue;
			}
			Files.createDirectories(f.getParentFile().toPath());
			Files.write(f.toPath(), e.getValue());
		}
	}

	/** Deletes the files in the public folder that are not published, then the folders left empty. */
	private static void removeOthers(Path to, Map<String, byte[]> files, Result r) throws IOException
	{
		List<Path> stale = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(to))
		{
			walk.filter(p -> !p.equals(to) && !LEFT_ALONE.contains(to.relativize(p).getName(0).toString()))
				.forEach(stale::add);
		}
		stale.sort(Comparator.reverseOrder()); // files before their folders
		for (Path p : stale)
		{
			String rel = to.relativize(p).toString().replace('\\', '/');
			if (Files.isRegularFile(p) && !files.containsKey(rel))
			{
				Files.delete(p);
				r.removed.add(rel);
			}
			else if (Files.isDirectory(p))
			{
				try (Stream<Path> in = Files.list(p))
				{
					if (!in.findAny().isPresent())
					{
						Files.delete(p);
					}
				}
			}
		}
	}

	/** True when the folder holds a Best Path plugin copy (its plugin properties name Best Path's plugin class). */
	static boolean isPublicCopy(File folder) throws IOException
	{
		File props = new File(folder, "runelite-plugin.properties");
		return props.isFile() && Files.readAllLines(props.toPath(), StandardCharsets.UTF_8).stream()
			.anyMatch(l -> l.trim().equals("plugins=com.bestpath.BestPathPlugin"));
	}

	/**
	 * Builds the public copy on its own ({@code gradlew build}, with this Java), the way the Plugin Hub builds it.
	 * Returns the exit code (0 = it builds). Tool only: the plugin never starts programs.
	 */
	public static int buildCheck(File target, Consumer<String> log) throws IOException, InterruptedException
	{
		boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
		// the wrapper by its full path: cmd may be set not to look in the current folder
		List<String> cmd = windows
			? List.of("cmd", "/c", new File(target, "gradlew.bat").getAbsolutePath(), "build", "--console=plain")
			: List.of("sh", new File(target, "gradlew").getAbsolutePath(), "build", "--console=plain");
		ProcessBuilder pb = new ProcessBuilder(cmd).directory(target).redirectErrorStream(true);
		pb.environment().put("JAVA_HOME", System.getProperty("java.home"));
		log.accept("Building " + target + " on its own (gradlew build) ...");
		Process p = pb.start();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				log.accept(line);
			}
		}
		if (!p.waitFor(15, TimeUnit.MINUTES))
		{
			p.destroyForcibly();
			log.accept("The build took over 15 minutes: stopped.");
			return -1;
		}
		int exit = p.exitValue();
		log.accept(exit == 0 ? "The public copy builds on its own." : "The build FAILED (exit " + exit + ").");
		return exit;
	}
}
