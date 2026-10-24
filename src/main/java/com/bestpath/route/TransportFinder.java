package com.bestpath.route;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.PlayerTransports;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TickGroups;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every transport the player can change the ticks of, to search, filter and sort. One entry per transport line (a
 * two-way transport's both ways share one tick change, {@link TransportSettings#tickKey}). Network stops are left out:
 * their rides are changed as a whole in "Teleport and travel ticks", or one ride on its step card, and a ride changed
 * that way is listed. Quest-only lines are left out: routes never use them. Immutable; any thread.
 */
public final class TransportFinder
{
	/** Which transports to show. */
	public enum Show
	{
		ALL("All transports"),
		CHANGED("Changed by me"),
		NOT_CHANGED("Not changed"),
		NEVER_USED("Never used (blacklist)");

		private final String label;

		Show(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** The order of the list. */
	public enum Sort
	{
		RECENT("Recently changed"),
		NAME("Name A-Z"),
		NAME_DESC("Name Z-A"),
		FEWEST_TICKS("Fewest ticks"),
		MOST_TICKS("Most ticks"),
		BIGGEST_CHANGE("Biggest change"),
		NEAREST("Nearest to me");

		private final String label;

		Sort(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** One transport. */
	public static final class Entry
	{
		private final String id;
		private final String title;
		private final String backTitle;
		private final String kind;
		private final String group;
		private final TransportSettings.Category category;
		private final Set<TransportSettings.Avoid> avoids;
		private final int start;
		private final int end;
		private final String search;
		/**
		 * Its data line and the tables, for the step its card would be. The card is made when a row shows and never kept:
		 * thousands of cards would cost megabytes of memory. Null for a changed ride or a gone transport.
		 */
		private final TransportLine line;
		private final Tables tables;

		Entry(String id, String title, String backTitle, String kind, String group, TransportSettings.Category category,
			Set<TransportSettings.Avoid> avoids, int start, int end, String name, TransportLine line, Tables tables)
		{
			this.line = line;
			this.tables = tables;
			this.id = id;
			this.title = title;
			this.backTitle = backTitle;
			this.kind = kind;
			this.group = group;
			this.category = category;
			this.avoids = avoids;
			this.start = start;
			this.end = end;
			// not the type's label: "ladder" would find every staircase ("Stairs and ladders"); the Type filter does that
			this.search = String.join("\n", title, backTitle == null ? "" : backTitle, name, group).toLowerCase(Locale.ROOT);
		}

		/** The id its tick change is saved under ({@link TransportSettings#tickKey}). */
		public String getId()
		{
			return id;
		}

		/** As a step card names it ("Climb-up Ladder", "Teleport: Varrock", "Boat or ship: ..."). */
		public String getTitle()
		{
			return title;
		}

		/** A two-way transport's way back, when it is named differently ("Climb-down Ladder"); else null. */
		public String getBackTitle()
		{
			return backTitle;
		}

		/** "Teleport", "Two-way", "One-way" or "Ride". */
		public String getKind()
		{
			return kind;
		}

		/** Its teleport category or network ("spell", "Fairy ring"); "" if none. */
		public String getGroup()
		{
			return group;
		}

		/** Its Settings type (null: in none). */
		public TransportSettings.Category getCategory()
		{
			return category;
		}

		/**
		 * The step its card would be, used from its start (the list shows the card's icon); null for a changed ride or a
		 * transport the data no longer has.
		 */
		public RouteStep getCard()
		{
			return line == null ? null : card(line, title, category, start, end, tables);
		}

		/** Where it is used (packed tile), -1 for a teleport (used anywhere) or not known. */
		public int getStart()
		{
			return start;
		}

		boolean matches(String[] words)
		{
			for (String w : words)
			{
				if (!search.contains(w))
				{
					return false;
				}
			}
			return true;
		}
	}

	/** What the player chose: the search words and the filters. */
	public static final class Query
	{
		final String text;
		final Show show;
		final TransportSettings.Category type;
		final TransportSettings.Avoid tag;
		final Sort sort;
		final int playerTile;

		/**
		 * @param type       only this Settings type (null: every type)
		 * @param tag        only transports an Avoid rule covers (null: any)
		 * @param playerTile where the player stands (packed tile; -1: not known, "Nearest to me" then sorts by name)
		 */
		public Query(String text, Show show, TransportSettings.Category type, TransportSettings.Avoid tag, Sort sort, int playerTile)
		{
			this.text = text == null ? "" : text;
			this.show = show == null ? Show.ALL : show;
			this.type = type;
			this.tag = tag;
			this.sort = sort == null ? Sort.RECENT : sort;
			this.playerTile = playerTile;
		}
	}

	/** No transports (no map data yet). */
	public static final TransportFinder EMPTY = new TransportFinder(List.of(), TickGroups.EMPTY);

	private final List<Entry> entries;
	private final Set<String> ids;
	private final TickGroups groups;

	private TransportFinder(List<Entry> entries, TickGroups groups)
	{
		this.entries = Collections.unmodifiableList(entries);
		this.groups = groups;
		Set<String> s = new HashSet<>();
		for (Entry e : entries)
		{
			s.add(e.id);
		}
		this.ids = s;
	}

	/**
	 * Every transport of the plugin data but network stops and quest-only lines, in the data's order. Tags: the Avoid
	 * rules the transport falls under, and "The Wilderness" when it starts or ends there (the transport data does not
	 * mark the Wilderness; it comes from the area's zones).
	 */
	public static TransportFinder of(List<TransportLine> lines, Tables tables, TickGroups groups,
		WildernessZones zones)
	{
		TransportRules rules = new TransportRules(lines, tables);
		List<Entry> out = new ArrayList<>();
		for (TransportLine l : lines)
		{
			if (l.getKind() == TransportLine.Kind.NETWORK || l.isQuestOnly())
			{
				continue;
			}
			boolean teleport = l.getKind() == TransportLine.Kind.TELEPORT;
			String title = title(l.getName(), l.getMenu(), teleport);
			String back = null;
			if (l.getKind() == TransportLine.Kind.TWO_WAY)
			{
				String b = title(l.getName(), l.getMenuBack(), false);
				back = b.equals(title) ? null : b;
			}
			String kind = teleport ? "Teleport" : l.getKind() == TransportLine.Kind.TWO_WAY ? "Two-way" : "One-way";
			String group = teleport ? l.getTeleportCategory().trim() : l.getNetwork().trim();
			Set<TransportSettings.Avoid> avoids = EnumSet.noneOf(TransportSettings.Avoid.class);
			avoids.addAll(rules.avoidRules(l));
			if (l.hasStart() && zones.isWilderness(l.getStartX(), l.getStartY()) || zones.isWilderness(l.getEndX(), l.getEndY()))
			{
				avoids.add(TransportSettings.Avoid.WILDERNESS);
			}
			int start = l.hasStart() ? Tiles.pack(l.getStartX(), l.getStartY(), l.getStartPlane()) : -1;
			int end = Tiles.pack(l.getEndX(), l.getEndY(), l.getEndPlane());
			TransportSettings.Category category = rules.category(null, l);
			out.add(new Entry(l.getId(), title, back, kind, group, category,
				avoids.isEmpty() ? Set.of() : Collections.unmodifiableSet(avoids), start, end, l.getName(), l, tables));
		}
		return new TransportFinder(out, groups);
	}

	/** The step its card would be, from its start, with what it needs (no player: nothing marked met or not). */
	private static RouteStep card(TransportLine l, String title, TransportSettings.Category category, int start, int end,
		Tables tables)
	{
		boolean teleport = l.getKind() == TransportLine.Kind.TELEPORT;
		Requirement req;
		try
		{
			req = RequirementText.parse(tables.networkRequirement(l.getNetwork(), l.getRequirements()),
				tables.itemGroups());
		}
		catch (RequirementText.ParseException e)
		{
			req = null;
		}
		return new RouteStep(teleport ? RouteStep.Kind.TELEPORT : StepTitles.kind(l), title, "", l.getTicks(), List.of(),
			l.getId(), new int[0], start, end).withInfo(StepInfo.of(l, false, req, category));
	}

	/** The title a step card gives it: the game's menu text, else its name ("Teleport: " before a teleport's). */
	static String title(String name, MenuText menu, boolean teleport)
	{
		if (menu != null)
		{
			return menu.title(CoreRouteEngine.ARROW);
		}
		return teleport ? StepTitles.teleportTitle(name) : name;
	}

	public List<Entry> getEntries()
	{
		return entries;
	}

	/** The ticks routes use for it with the player's changes (-1: not known). */
	public int ticks(Entry e, TransportSettings s)
	{
		return groups.ticks(e.id, s.tickChanges());
	}

	/**
	 * The transports to show, in order. Transports changed that the data no longer has, or network rides changed on a
	 * step card, are listed by the name saved with the change (kind "Ride", or "Changed").
	 */
	public List<Entry> find(Query q, TransportSettings s)
	{
		String[] words = q.text.trim().toLowerCase(Locale.ROOT).split("\\s+");
		if (words.length == 1 && words[0].isEmpty())
		{
			words = new String[0];
		}
		List<Entry> all = new ArrayList<>(entries);
		for (String key : s.getTickChanges().keySet())
		{
			if (!ids.contains(key))
			{
				all.add(new Entry(key, s.tickName(key), null, key.contains(">") ? "Ride" : "Changed", "", null, Set.of(), -1, -1,
					key, null, null));
			}
		}
		List<Entry> out = new ArrayList<>();
		for (Entry e : all)
		{
			boolean changed = s.tickChange(e.id) != 0;
			if (q.show == Show.CHANGED && !changed || q.show == Show.NOT_CHANGED && changed
				|| q.show == Show.NEVER_USED && !s.isBlacklisted(e.id) && !s.isBlacklisted(e.id + ":back"))
			{
				continue;
			}
			if (q.type != null && e.category != q.type || q.tag != null && !e.avoids.contains(q.tag) || !e.matches(words))
			{
				continue;
			}
			out.add(e);
		}
		Map<Entry, Integer> ticks = new HashMap<>();
		if (q.sort == Sort.FEWEST_TICKS || q.sort == Sort.MOST_TICKS)
		{
			PlayerTransports.TickChanges tc = s.tickChanges();
			for (Entry e : out)
			{
				ticks.put(e, groups.ticks(e.id, tc));
			}
		}
		out.sort(order(q, s, ticks));
		return out;
	}

	private static Comparator<Entry> order(Query q, TransportSettings s, Map<Entry, Integer> ticks)
	{
		Comparator<Entry> byName = Comparator.comparing((Entry e) -> e.title, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(e -> e.id);
		switch (q.sort)
		{
			case RECENT:
				// changed first, the newest first (changes with no saved time after them, latest first);
				// then the rest by name
				List<String> made = new ArrayList<>(s.getTickChanges().keySet());
				return Comparator.comparingInt((Entry e) -> s.tickChange(e.id) != 0 ? 0 : 1)
					.thenComparing((Entry e) -> -s.tickTime(e.id))
					.thenComparingInt(e -> -made.indexOf(e.id))
					.thenComparing(byName);
			case NAME_DESC:
				return byName.reversed();
			case FEWEST_TICKS:
				return Comparator.comparingInt((Entry e) -> unknownLast(ticks.get(e))).thenComparing(byName);
			case MOST_TICKS:
				return Comparator.comparingInt((Entry e) -> -ticks.get(e)).thenComparing(byName);
			case BIGGEST_CHANGE:
				return Comparator.comparingInt((Entry e) -> -Math.abs(s.tickChange(e.id))).thenComparing(byName);
			case NEAREST:
				if (q.playerTile == -1)
				{
					return byName;
				}
				return Comparator.comparingLong((Entry e) -> distance(e, q.playerTile)).thenComparing(byName);
			case NAME:
			default:
				return byName;
		}
	}

	private static int unknownLast(int ticks)
	{
		return ticks < 0 ? Integer.MAX_VALUE : ticks;
	}

	/**
	 * Tiles from the player to where it is used (a teleport, a ride: where it goes); another plane counts after every
	 * transport on the player's plane; not known last.
	 */
	public static long distance(Entry e, int playerTile)
	{
		int at = e.start != -1 ? e.start : e.end;
		if (at == -1 || playerTile == -1)
		{
			return Long.MAX_VALUE;
		}
		int d = Math.max(Math.abs(Tiles.x(at) - Tiles.x(playerTile)), Math.abs(Tiles.y(at) - Tiles.y(playerTile)));
		return Tiles.plane(at) == Tiles.plane(playerTile) ? d : 100_000L + d;
	}
}
