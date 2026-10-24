package com.bestpath.core.data;

import com.bestpath.core.req.RequirementText;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The small hand-written tables shipped with the plugin: item groups and runes
 * (for requirement lines), networks (a ride's extra requirement and ticks),
 * teleport categories (a teleport's ticks) and each item group's default item
 * (the picture the sidebar shows for a group). Immutable.
 */
public final class Tables
{
	/** One network: extra ticks per ride and a requirement every ride needs (may be empty). */
	public static final class Network
	{
		private final String name;
		private final int extraTicks;
		private final String requirement;

		Network(String name, int extraTicks, String requirement)
		{
			this.name = name;
			this.extraTicks = extraTicks;
			this.requirement = requirement == null ? "" : requirement;
		}

		public String getName()
		{
			return name;
		}

		public int getExtraTicks()
		{
			return extraTicks;
		}
	}

	private final Map<String, RequirementText.RuneSource> runes;
	private final Map<String, Network> networks;
	private final Map<String, Integer> teleportTicks;
	/** Item group name (lower case) -> the default item chosen for it. */
	final Map<String, Integer> groupDefaults;
	private final RequirementText.ItemGroups lookup;

	private Tables(Map<String, int[]> itemGroups, Map<String, RequirementText.RuneSource> runes,
		Map<String, Network> networks, Map<String, Integer> teleportTicks, Map<String, Integer> groupDefaults)
	{
		this.groupDefaults = Collections.unmodifiableMap(groupDefaults);
		this.runes = Collections.unmodifiableMap(runes);
		this.networks = Collections.unmodifiableMap(networks);
		this.teleportTicks = Collections.unmodifiableMap(teleportTicks);
		RequirementText.ItemGroups items = RequirementText.ItemGroups.of(itemGroups);
		Map<String, RequirementText.RuneSource> r = this.runes;
		this.lookup = new RequirementText.ItemGroups()
		{
			@Override
			public int[] ids(String name)
			{
				return items.ids(name);
			}

			@Override
			public RequirementText.RuneSource rune(String name)
			{
				return r.get(name.trim().toLowerCase(Locale.ROOT));
			}
		};
	}

	/** No tables. */
	public static Tables empty()
	{
		return new Tables(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
			new LinkedHashMap<>());
	}

	/**
	 * Reads the four tables (lines of item-groups.tsv, runes.tsv, networks.tsv,
	 * teleport-categories.tsv) and the item groups' default items (lines of
	 * item-group-defaults.tsv: {@code group <tab> item id <tab> ...}); a missing
	 * table is an empty list. Lines that can't be read are skipped.
	 */
	public static Tables parse(List<String> itemGroupLines, List<String> runeLines, List<String> networkLines,
		List<String> teleportCategoryLines, List<String> groupDefaultLines)
	{
		Map<String, int[]> groups = new LinkedHashMap<>();
		for (String line : itemGroupLines)
		{
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] p = line.split("\t");
			try
			{
				groups.put(p[0].trim(), ids(p.length > 1 ? p[1] : ""));
			}
			catch (RuntimeException e)
			{
				// skipped
			}
		}
		Map<String, RequirementText.RuneSource> runes = new LinkedHashMap<>();
		for (String line : runeLines)
		{
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] p = line.split("\t", -1);
			try
			{
				runes.put(p[0].trim().toLowerCase(Locale.ROOT), new RequirementText.RuneSource(ids(p[1]), ids(p[2])));
			}
			catch (RuntimeException e)
			{
				// skipped
			}
		}
		Map<String, Network> networks = new LinkedHashMap<>();
		for (String line : networkLines)
		{
			if (line.startsWith("#"))
			{
				continue;
			}
			String[] w = line.split("\t", -1);
			if (w.length < 2)
			{
				continue;
			}
			try
			{
				Network n = new Network(w[0].trim(), Integer.parseInt(w[1].trim()), w.length > 2 ? w[2].trim() : "");
				networks.put(n.name.toLowerCase(Locale.ROOT), n);
			}
			catch (NumberFormatException e)
			{
				// skipped
			}
		}
		Map<String, Integer> teleports = new LinkedHashMap<>();
		for (String line : teleportCategoryLines)
		{
			if (line.startsWith("#"))
			{
				continue;
			}
			String[] w = line.split("\t");
			if (w.length < 2)
			{
				continue;
			}
			try
			{
				teleports.put(w[0].trim().toLowerCase(Locale.ROOT), Integer.parseInt(w[1].trim()));
			}
			catch (NumberFormatException e)
			{
				// skipped
			}
		}
		Map<String, Integer> defaults = new LinkedHashMap<>();
		for (String line : groupDefaultLines)
		{
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] w = line.split("\t", -1);
			if (w.length < 2)
			{
				continue;
			}
			try
			{
				defaults.put(w[0].trim().toLowerCase(Locale.ROOT), Integer.parseInt(w[1].trim()));
			}
			catch (NumberFormatException e)
			{
				// skipped
			}
		}
		return new Tables(groups, runes, networks, teleports, defaults);
	}

	private static int[] ids(String list)
	{
		if (list.trim().isEmpty())
		{
			return new int[0];
		}
		String[] w = list.split(",");
		int[] v = new int[w.length];
		for (int i = 0; i < w.length; i++)
		{
			v[i] = Integer.parseInt(w[i].trim());
		}
		return v;
	}

	/** Item groups and runes, for {@link RequirementText#parse}. */
	public RequirementText.ItemGroups itemGroups()
	{
		return lookup;
	}

	/** The default item chosen for an item group (any case), or -1 (none chosen, or no such group). */
	public int itemGroupDefault(String group)
	{
		Integer id = group == null ? null : groupDefaults.get(group.trim().toLowerCase(Locale.ROOT));
		return id == null ? -1 : id;
	}

	/** The network (any case), or null. */
	public Network network(String name)
	{
		return name == null ? null : networks.get(name.trim().toLowerCase(Locale.ROOT));
	}

	/** {@code own} with the network's requirement in front ("a; b"); {@code own} alone without a known network. */
	public String networkRequirement(String network, String own)
	{
		Network n = network(network);
		String o = own == null ? "" : own.trim();
		if (n == null || n.requirement.isBlank())
		{
			return o;
		}
		return o.isEmpty() ? n.requirement : n.requirement + "; " + o;
	}

	/** The network's extra ticks (0 without a known network). */
	public int networkExtraTicks(String network)
	{
		Network n = network(network);
		return n == null ? 0 : n.extraTicks;
	}

	/** The teleport category's ticks (any case), or null without such a category. */
	public Integer teleportCategoryTicks(String category)
	{
		return category == null ? null : teleportTicks.get(category.trim().toLowerCase(Locale.ROOT));
	}

	/** The teleport categories (lower case, as {@link #teleportCategoryTicks} knows them), in the table's order. */
	public List<String> teleportCategoryNames()
	{
		return List.copyOf(teleportTicks.keySet());
	}

	/** The networks, in the table's order. */
	public List<Network> networks()
	{
		return List.copyOf(networks.values());
	}
}
