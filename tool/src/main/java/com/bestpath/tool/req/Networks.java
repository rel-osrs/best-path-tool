package com.bestpath.tool.req;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Transport networks (fairy rings, spirit trees, charter ships, canoes...) from
 * {@code data/manual/networks.tsv}: {@code network <tab> extra ticks <tab>
 * requirement <tab> description}. Every transport of a network needs the
 * network's requirement as well as its own, and costs its own ride ticks plus
 * the network's adjustable extra. Read again when
 * the file changes.
 */
public final class Networks
{
	public static final File FILE = new File(com.bestpath.tool.ToolSettings.findWorkspace(), "data/manual/networks.tsv");

	/** One network. */
	public static final class Network
	{
		public final String name;
		public final int extraTicks;
		/** Needed for every ride of the network (a requirement line, may be empty). */
		public final String requirement;
		public final String description;

		public Network(String name, int extraTicks, String requirement, String description)
		{
			this.name = name;
			this.extraTicks = extraTicks;
			this.requirement = requirement == null ? "" : requirement;
			this.description = description == null ? "" : description;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private static File file = FILE;
	private static long loadedStamp = Long.MIN_VALUE;
	private static Map<String, Network> byName = Collections.emptyMap();
	private static List<String> header = Collections.emptyList();

	private Networks()
	{
	}

	/** Tests only: read and write another file instead. */
	public static synchronized void useFile(File f)
	{
		file = f;
		loadedStamp = Long.MIN_VALUE;
	}

	public static synchronized List<Network> all()
	{
		reload();
		return new ArrayList<>(byName.values());
	}

	/** The network (any case), or null. */
	public static synchronized Network get(String name)
	{
		reload();
		return name == null ? null : byName.get(name.trim().toLowerCase(Locale.ROOT));
	}

	/** {@code own} with the network's requirement in front ("a; b"); {@code own} alone without a known network. */
	public static String requirement(String network, String own)
	{
		Network n = get(network);
		String o = own == null ? "" : own.trim();
		if (n == null || n.requirement.isBlank())
		{
			return o;
		}
		return o.isEmpty() ? n.requirement : n.requirement + "; " + o;
	}

	/** The network's extra ticks (0 without a known network). */
	public static int extraTicks(String network)
	{
		Network n = get(network);
		return n == null ? 0 : n.extraTicks;
	}

	/** Writes the networks, keeping the file's comments; the order given is kept. */
	public static synchronized void save(List<Network> networks) throws IOException
	{
		reload();
		List<String> lines = new ArrayList<>(header);
		for (Network n : networks)
		{
			lines.add(n.name + "\t" + n.extraTicks + "\t" + n.requirement + "\t" + n.description);
		}
		Files.write(file.toPath(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
		loadedStamp = Long.MIN_VALUE;
	}

	/** Adds the network or replaces the one with its name (same place in the file). */
	public static synchronized void put(Network n) throws IOException
	{
		List<Network> list = all();
		boolean replaced = false;
		for (int i = 0; i < list.size(); i++)
		{
			if (list.get(i).name.equalsIgnoreCase(n.name))
			{
				list.set(i, n);
				replaced = true;
			}
		}
		if (!replaced)
		{
			list.add(n);
		}
		save(list);
	}

	private static void reload()
	{
		long stamp = FileStamp.of(file) ^ file.hashCode();
		if (stamp == loadedStamp)
		{
			return;
		}
		loadedStamp = stamp;
		Map<String, Network> m = new LinkedHashMap<>();
		List<String> h = new ArrayList<>();
		if (file.isFile())
		{
			try
			{
				for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
				{
					if (line.startsWith("#"))
					{
						h.add(line);
						continue;
					}
					String[] w = line.split("\t", -1);
					if (w.length < 2)
					{
						continue;
					}
					try
					{
						Network n = new Network(w[0].trim(), Integer.parseInt(w[1].trim()), w.length > 2 ? w[2].trim() : "",
							w.length > 3 ? w[3].trim() : "");
						m.put(n.name.toLowerCase(Locale.ROOT), n);
					}
					catch (NumberFormatException e)
					{
						System.err.println("networks: bad line: " + line);
					}
				}
			}
			catch (IOException e)
			{
				System.err.println("networks: " + e.getMessage());
			}
		}
		byName = Collections.unmodifiableMap(m);
		header = Collections.unmodifiableList(h);
	}
}
