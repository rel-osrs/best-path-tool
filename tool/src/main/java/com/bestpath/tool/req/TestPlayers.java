package com.bestpath.tool.req;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The test players: the built-in Maxed and Fresh account, then the ones
 * made in the tool, saved in {@code data/manual/test-players.json} (pretty JSON, so changes
 * read well in git).
 */
public final class TestPlayers
{
	public static final File FILE = new File(com.bestpath.tool.ToolSettings.findWorkspace(), "data/manual/test-players.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private TestPlayers()
	{
	}

	/** Maxed, Fresh account, then the saved ones. */
	public static List<TestPlayer> load()
	{
		return load(FILE);
	}

	static List<TestPlayer> load(File file)
	{
		List<TestPlayer> all = new ArrayList<>();
		all.add(TestPlayer.maxed());
		all.add(TestPlayer.fresh());
		if (file.isFile())
		{
			try
			{
				List<TestPlayer> saved = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8),
					new TypeToken<List<TestPlayer>>()
					{
					}.getType());
				if (saved != null)
				{
					all.addAll(saved);
				}
			}
			catch (IOException | RuntimeException e)
			{
				System.err.println("test players: " + e.getMessage());
			}
		}
		return all;
	}

	/** Saves the players that are not built in. */
	public static void save(List<TestPlayer> players) throws IOException
	{
		save(FILE, players);
	}

	static void save(File file, List<TestPlayer> players) throws IOException
	{
		List<TestPlayer> own = new ArrayList<>();
		for (TestPlayer p : players)
		{
			if (!p.builtIn)
			{
				own.add(p);
			}
		}
		Files.writeString(file.toPath(), GSON.toJson(own) + "\n", StandardCharsets.UTF_8);
	}
}
