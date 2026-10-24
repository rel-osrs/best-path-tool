package com.bestpath.tool.req;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.cache.ItemManager;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.fs.Store;

/**
 * Lists every real item in the cache (no noted or placeholder copies) as
 * {@code id <tab> name <tab> members <tab> options <tab> params} (the cache's item
 * params as {@code key=value}, e.g. the equipment bonuses), so item groups ("light
 * source", "axe") are built from cache ids. Developer aid; output is
 * git-ignored. {@code gradlew -p tool items --args="<cache folder> <out.tsv>"}.
 */
public final class ItemsCli
{
	private ItemsCli()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			System.err.println("usage: items <cache folder (disk/cache)> <out.tsv>");
			System.exit(2);
		}
		List<ItemDefinition> items = new ArrayList<>();
		try (Store store = new Store(new File(args[0])))
		{
			store.load();
			ItemManager im = new ItemManager(store);
			im.load();
			for (ItemDefinition d : im.getItems())
			{
				if (d.getNotedTemplate() == -1 && d.getPlaceholderTemplateId() == -1 && d.getName() != null
					&& !d.getName().equals("null"))
				{
					items.add(d);
				}
			}
		}
		items.sort(Comparator.comparingInt(ItemDefinition::getId));
		File out = new File(args[1]);
		if (out.getParentFile() != null)
		{
			out.getParentFile().mkdirs();
		}
		try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8))
		{
			w.println("# id\tname\tmembers\toptions\tparams");
			for (ItemDefinition d : items)
			{
				List<String> ops = new ArrayList<>();
				if (d.getInterfaceOptions() != null)
				{
					for (String o : d.getInterfaceOptions())
					{
						if (o != null)
						{
							ops.add(o);
						}
					}
				}
				List<String> params = new ArrayList<>();
				if (d.getParams() != null)
				{
					new java.util.TreeMap<>(d.getParams()).forEach((k, v) -> params.add(k + "=" + v));
				}
				w.println(d.getId() + "\t" + d.getName() + "\t" + (d.isMembers() ? "members" : "free") + "\t" + String.join(",", ops)
					+ "\t" + String.join(",", params));
			}
		}
		System.out.println(items.size() + " items -> " + out);
	}
}
