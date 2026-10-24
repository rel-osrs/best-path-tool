package com.bestpath.tool.menu;

import com.bestpath.core.data.MenuText;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.req.ItemGroupFile;
import com.bestpath.tool.transport.ShortestPathTransports;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The menu texts of the exported transports (plugin export, columns {@code menu} and {@code menu back}): made from the
 * cache on every export, nothing hand-made (docs/DATA_FORMATS.md). Also writes every transport's texts with the reason
 * for each unknown one to a table for checking ({@code tool/build/menu-texts.tsv}).
 */
public final class MenuExport
{
	private MenuExport()
	{
	}

	/** The menu texts of every record, by record id. */
	public static Map<String, MenuMatcher.Result> build(LoadedCache cache, List<TransportRecord> records, File ws) throws IOException
	{
		long t0 = System.nanoTime();
		MenuFacts facts = MenuFactsLoader.load(cache);
		// the keys of the lists a person confirmed (data/manual/list-keys.tsv); a written list also
		// names the choices the cache does not have
		ListKeys keys = ListKeys.load(ws);
		keys.lend(facts);
		List<MenuMatcher.Hint> hints = hints(new File(ws, "data/shortest-path"));
		MenuMatcher m = new MenuMatcher(facts, text ->
		{
			try
			{
				return ItemGroupFile.parse(text);
			}
			catch (Exception e)
			{
				return null;
			}
		}, hints, records);
		Map<String, MenuMatcher.Result> out = new LinkedHashMap<>();
		for (TransportRecord r : records)
		{
			MenuMatcher.Result res;
			try
			{
				res = m.match(r);
			}
			catch (IllegalArgumentException e)
			{
				// a name or option the file can't hold (a '|'): left unknown, with the reason
				res = new MenuMatcher.Result(null, null, "not written: " + e.getMessage(),
					r.kind == TransportRecord.Kind.TWO_WAY ? "not written: " + e.getMessage() : null, List.of(), "");
			}
			out.put(r.id, withKeys(res, keys, facts));
		}
		System.out.printf(Locale.ROOT, "menu texts made in %.1f s (%,d spells, %,d items, %,d Shortest Path hints; object texts"
				+ " cross-checked with Shortest Path's rows for the same way: %,d the same, %,d different)%n",
			(System.nanoTime() - t0) / 1e9, facts.spells.size(), facts.items.size(), hints.size(), m.getHintsAgree(), m.getHintsDiffer());
		return out;
	}

	/** The result with the confirmed lists' keys on its choices ({@link ListKeys}); the same result when none applies. */
	static MenuMatcher.Result withKeys(MenuMatcher.Result res, ListKeys keys, MenuFacts facts)
	{
		MenuText there = keys.apply(res.there, facts);
		MenuText back = keys.apply(res.back, facts);
		if (there == res.there && back == res.back)
		{
			return res;
		}
		return new MenuMatcher.Result(there, back, res.whyThere, res.whyBack, res.doubts, res.how + "; key from " + ListKeys.FILE);
	}

	/** Shortest Path rows with a menu column, as hints. */
	static List<MenuMatcher.Hint> hints(File folder) throws IOException
	{
		List<MenuMatcher.Hint> out = new ArrayList<>();
		for (ShortestPathTransports.Row row : ShortestPathTransports.load(folder))
		{
			MenuMatcher.Hint h = MenuMatcher.Hint.parse(row.file, row.origin, row.destination, row.columns.get("menuOption menuTarget objectID"));
			if (h != null)
			{
				out.add(h);
			}
		}
		return out;
	}

	/** "group: known/all" lines per transport kind and direction. */
	public static String summary(List<TransportRecord> records, Map<String, MenuMatcher.Result> menus)
	{
		Map<String, int[]> counts = new TreeMap<>();
		int choiceOnly = 0;
		for (TransportRecord r : records)
		{
			MenuMatcher.Result m = menus.get(r.id);
			boolean only = m != null && m.there != null && m.there.isChoiceOnly();
			choiceOnly += only ? 1 : 0;
			count(counts, r.kind.name(), m != null && m.there != null && !only);
			if (r.kind == TransportRecord.Kind.TWO_WAY)
			{
				count(counts, "TWO_WAY back", m != null && m.back != null);
			}
		}
		StringBuilder sb = new StringBuilder("menu texts known:");
		int known = 0;
		int all = 0;
		for (Map.Entry<String, int[]> e : counts.entrySet())
		{
			sb.append(String.format(Locale.ROOT, " %s %,d/%,d;", e.getKey(), e.getValue()[0], e.getValue()[1]));
			known += e.getValue()[0];
			all += e.getValue()[1];
		}
		return sb.append(String.format(Locale.ROOT, " all directions %,d/%,d (%.1f %%); network stops with only the choice that leads"
			+ " to them: %d", known, all, all == 0 ? 0 : 100.0 * known / all, choiceOnly)).toString();
	}

	private static void count(Map<String, int[]> counts, String key, boolean known)
	{
		int[] c = counts.computeIfAbsent(key, k -> new int[2]);
		c[0] += known ? 1 : 0;
		c[1]++;
	}

	/** Every record's texts, reasons and doubts, tab-separated (a developer's check list). */
	public static void writeTable(File file, List<TransportRecord> records, Map<String, MenuMatcher.Result> menus) throws IOException
	{
		List<String> lines = new ArrayList<>();
		lines.add("#id\tkind\tcategory\tnetwork\tteleport category\tname\tobject\tmenu\ttitle\tmenu back\ttitle back\twhy unknown"
			+ "\twhy back unknown\tdoubts\thow");
		for (TransportRecord r : records)
		{
			MenuMatcher.Result m = menus.get(r.id);
			lines.add(String.join("\t", r.id, r.kind.name(), r.category, r.network, r.teleportCategory == null ? "" : r.teleportCategory,
				clean(r.name), r.objectId < 0 ? "" : r.objectId + "@" + r.objectX + "," + r.objectY + "," + r.objectPlane,
				text(m.there), m.there == null ? "" : m.there.title(MenuText.ARROW), text(m.back), m.back == null ? "" : m.back.title(MenuText.ARROW),
				clean(m.whyThere), clean(m.whyBack), clean(String.join("; ", m.doubts)), clean(m.how)));
		}
		Files.createDirectories(file.getParentFile().toPath());
		Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
	}

	private static String text(MenuText t)
	{
		return t == null ? "" : t.format();
	}

	private static String clean(String s)
	{
		return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}
}
