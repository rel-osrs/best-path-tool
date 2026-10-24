package com.bestpath.tool.req;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Player-owned house places and the things built in it that move the player,
 * from {@code data/manual/house-things.tsv} ({@code kind <tab> name <tab>
 * source}; kinds place, thing, destination). A portal destination D gives two
 * things: "portal chamber: D" and "portal nexus: D". Read again when the file
 * changes.
 */
public final class HouseThings
{
	public static final File FILE = new File(com.bestpath.tool.ToolSettings.findWorkspace(), "data/manual/house-things.tsv");

	private static long loadedStamp = Long.MIN_VALUE;
	private static List<String> places = Collections.emptyList();
	private static List<String> things = Collections.emptyList();

	private HouseThings()
	{
	}

	public static synchronized List<String> places()
	{
		reload();
		return places;
	}

	/** Every built thing, portals included. */
	public static synchronized List<String> things()
	{
		reload();
		return things;
	}

	public static boolean isPlace(String name)
	{
		return contains(places(), name);
	}

	public static boolean isThing(String name)
	{
		return contains(things(), name);
	}

	/** The name as the file writes it (any case), or the name itself. */
	public static String canonical(String name)
	{
		for (List<String> l : List.of(places(), things()))
		{
			for (String s : l)
			{
				if (s.equalsIgnoreCase(name.trim()))
				{
					return s;
				}
			}
		}
		return name.trim();
	}

	private static boolean contains(List<String> list, String name)
	{
		for (String s : list)
		{
			if (s.equalsIgnoreCase(name.trim()))
			{
				return true;
			}
		}
		return false;
	}

	private static void reload()
	{
		long stamp = FileStamp.of(FILE);
		if (stamp == loadedStamp)
		{
			return;
		}
		loadedStamp = stamp;
		List<String> p = new ArrayList<>();
		List<String> t = new ArrayList<>();
		List<String> portals = new ArrayList<>();
		if (FILE.isFile())
		{
			try
			{
				for (String line : Files.readAllLines(FILE.toPath(), StandardCharsets.UTF_8))
				{
					String[] w = line.split("\t");
					if (line.startsWith("#") || w.length < 2)
					{
						continue;
					}
					switch (w[0].trim().toLowerCase(Locale.ROOT))
					{
						case "place":
							p.add(w[1].trim());
							break;
						case "thing":
							t.add(w[1].trim());
							break;
						case "destination":
							portals.add(w[1].trim());
							break;
						default:
							break;
					}
				}
			}
			catch (IOException e)
			{
				System.err.println("house things: " + e.getMessage());
			}
		}
		for (String d : portals)
		{
			t.add("portal chamber: " + d);
		}
		for (String d : portals)
		{
			t.add("portal nexus: " + d);
		}
		places = Collections.unmodifiableList(p);
		things = Collections.unmodifiableList(t);
	}
}
