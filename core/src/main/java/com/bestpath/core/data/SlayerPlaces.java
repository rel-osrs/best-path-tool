package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Slayer tasks of every Slayer master and the places to kill their monsters, so the player can compare places.
 * Masters, tasks, bosses and Konar's areas come from the game cache's Slayer DB tables (113-116), the master names and
 * the places from the OSRS Wiki at development time. A place is one group of a task's monsters (one entry per group):
 * its name, what the Wiki's comparison says about it and a few points to route to (the route goes to the cheapest).
 * Immutable.
 *
 * <p>Text form (the plugin's optional {@value #FILE}): {@code #} comment lines, then tab-separated lines, each starting
 * with its kind:
 * <ul>
 * <li>{@code master <number> <name>}: varbit 4067's value;</li>
 * <li>{@code task <row> <id> <name>}: a task (DB row of table 113); id = varp 395's value, -1 for a boss;</li>
 * <li>{@code boss <number> <row>}: varbit 4723's value (the boss of a "boss" task, varp 395 = {@value #BOSS_TASK})
 * and its task row;</li>
 * <li>{@code area <id> <name>}: varp 2096's value (the area Konar named);</li>
 * <li>{@code place <row> <name> <area> <amount> <multi> <cannon> <safespot> <points> <map> <monsters> <source>
 * <notes>}: a place of the task with that row; area = the Konar area it is in or -1; multi / cannon / safespot
 * "yes", "no", "?" or the Wiki's words; points "x,y,plane" separated by spaces; map "yes" when the points are on the
 * map data's ground (a route can go there), "no" when the map data has no ground there yet; source the Wiki page and
 * revision ("page@revision").</li>
 * <li>{@code icon <row> <item>}: the item picture shown for the task with that row (the same as RuneLite's Slayer
 * plugin uses for the monster);</li>
 * <li>{@code loot area <place name> <items> <note>} / {@code loot master <number> <items> <note>}: special loot (e.g.
 * the items from the Kourend catacombs): for every place whose name starts with that name, or for every task from that
 * master; items = item numbers separated by spaces;</li>
 * <li>{@code loot place <task row>:<place name> <items> <note>}: the collection log items the monsters of that place of
 * that task drop that not every place of the task has (made at export time from the Wiki's drop tables), note
 * "From Spiritual mage.".</li>
 * </ul>
 */
public final class SlayerPlaces
{
	public static final String FILE = "slayer.tsv";
	/** Varp 395's value for "a boss": the boss is in varbit 4723. */
	public static final int BOSS_TASK = 98;
	public static final SlayerPlaces NONE = new SlayerPlaces(Map.of(), List.of(), Map.of(), Map.of(), List.of());
	public static final String LOOT_AREA = "area";
	public static final String LOOT_MASTER = "master";
	/** Loot of one place of one task (key {@code <task row>:<place name>}). */
	public static final String LOOT_PLACE = "place";

	/** Special loot: items a place's monsters (or a master's tasks) give that they don't elsewhere. */
	public static final class Loot
	{
		final String kind;
		final String key;
		final int[] items;
		final String note;

		/**
		 * {@code kind} {@link #LOOT_AREA} (key: the start of a place's name), {@link #LOOT_MASTER} (key: its number) or
		 * {@link #LOOT_PLACE} (key: {@link #placeKey}).
		 */
		public Loot(String kind, String key, int[] items, String note)
		{
			this.kind = kind;
			this.key = key;
			this.items = items.clone();
			this.note = note == null ? "" : note;
		}

		public int[] getItems()
		{
			return items.clone();
		}

		/** "Ghosts don't drop the totem pieces." (empty: none) */
		public String getNote()
		{
			return note;
		}
	}

	/** One task (DB row of table 113). */
	public static final class Task
	{
		final int row;
		final int id;
		final String name;

		public Task(int row, int id, String name)
		{
			this.row = row;
			this.id = id;
			this.name = name;
		}

		public int getRow()
		{
			return row;
		}

		/** As the game writes it ("Kalphites", "The Kalphite Queen"). */
		public String getName()
		{
			return name;
		}
	}

	/** One place to kill a task's monsters: one group of monsters. */
	public static final class Place
	{
		final int task;
		final String name;
		final int area;
		final String amount;
		final String multi;
		final String cannon;
		final String safespot;
		final int[] points;
		final boolean onMap;
		final String monsters;
		final String source;
		final String notes;

		public Place(int task, String name, int area, String amount, String multi, String cannon, String safespot, int[] points,
			boolean onMap, String monsters, String source, String notes)
		{
			this.task = task;
			this.name = name;
			this.area = area;
			this.amount = word(amount);
			this.multi = word(multi);
			this.cannon = word(cannon);
			this.safespot = word(safespot);
			this.points = points.clone();
			this.onMap = onMap;
			this.monsters = monsters == null ? "" : monsters;
			this.source = source == null ? "" : source;
			this.notes = notes == null ? "" : notes;
		}

		/** The task's row. */
		public int getTask()
		{
			return task;
		}

		/** The Wiki's name for the place ("Kalphite Lair", "Slayer Tower (floor 2)"). */
		public String getName()
		{
			return name;
		}

		/** The Konar area it is in (varp 2096's value), or -1. */
		public int getArea()
		{
			return area;
		}

		/** How many monsters, as the Wiki says ("13", "18 Workers, 20 Soldiers"); "?" when not known. */
		public String getAmount()
		{
			return amount;
		}

		/** "yes", "no", "?" or the Wiki's words. */
		public String getMulti()
		{
			return multi;
		}

		public String getCannon()
		{
			return cannon;
		}

		public String getSafespot()
		{
			return safespot;
		}

		/** Packed tiles ({@link Tiles}) to route to, at least one. */
		public int[] getPoints()
		{
			return points.clone();
		}

		/** The points are on the map data's ground: a route can go there (false: the map data has no ground there yet). */
		public boolean isOnMap()
		{
			return onMap;
		}

		/** The monsters there ("Kalphite Worker / Kalphite Soldier"). */
		public String getMonsters()
		{
			return monsters;
		}

		/** The Wiki page and revision ("Slayer task/Kalphites@15344284"). */
		public String getSource()
		{
			return source;
		}

		/** The Wiki's notes; empty for none. */
		public String getNotes()
		{
			return notes;
		}
	}

	final Map<Integer, String> masters;
	final Map<Integer, Task> tasksByRow;
	private final Map<Integer, Task> tasksById;
	final Map<Integer, Integer> bosses;
	final Map<Integer, String> areas;
	final Map<Integer, List<Place>> places;
	final Map<Integer, Integer> icons;
	final List<Loot> loot;

	public SlayerPlaces(Map<Integer, String> masters, List<Task> tasks, Map<Integer, Integer> bosses, Map<Integer, String> areas,
		List<Place> places)
	{
		this(masters, tasks, bosses, areas, places, Map.of(), List.of());
	}

	/** {@code icons}: task row -> item; {@code loot}: the special loot. */
	public SlayerPlaces(Map<Integer, String> masters, List<Task> tasks, Map<Integer, Integer> bosses, Map<Integer, String> areas,
		List<Place> places, Map<Integer, Integer> icons, List<Loot> loot)
	{
		this.icons = Collections.unmodifiableMap(new LinkedHashMap<>(icons));
		this.loot = Collections.unmodifiableList(new ArrayList<>(loot));
		this.masters = Collections.unmodifiableMap(new LinkedHashMap<>(masters));
		Map<Integer, Task> byRow = new LinkedHashMap<>();
		Map<Integer, Task> byId = new LinkedHashMap<>();
		for (Task t : tasks)
		{
			byRow.put(t.row, t);
			if (t.id >= 0)
			{
				byId.put(t.id, t);
			}
		}
		this.tasksByRow = Collections.unmodifiableMap(byRow);
		this.tasksById = Collections.unmodifiableMap(byId);
		this.bosses = Collections.unmodifiableMap(new LinkedHashMap<>(bosses));
		this.areas = Collections.unmodifiableMap(new LinkedHashMap<>(areas));
		Map<Integer, List<Place>> byTask = new LinkedHashMap<>();
		for (Place p : places)
		{
			byTask.computeIfAbsent(p.task, k -> new ArrayList<>()).add(p);
		}
		byTask.replaceAll((k, v) -> Collections.unmodifiableList(v));
		this.places = Collections.unmodifiableMap(byTask);
	}

	/**
	 * The task the game's vars name: varp 395 ({@code id}) and, for a boss task, varbit 4723 ({@code bossId}); null for
	 * none (0) or a task this data doesn't know.
	 */
	public Task task(int id, int bossId)
	{
		if (id == BOSS_TASK)
		{
			Integer row = bosses.get(bossId);
			return row == null ? tasksById.get(id) : tasksByRow.get(row);
		}
		return id <= 0 ? null : tasksById.get(id);
	}

	/** The places of the task with this row, in the Wiki's order; empty for none. */
	public List<Place> places(int taskRow)
	{
		return places.getOrDefault(taskRow, List.of());
	}

	/** The item picture for the task with this row (the RuneLite Slayer plugin's), or -1 for none. */
	public int icon(int taskRow)
	{
		return icons.getOrDefault(taskRow, -1);
	}

	/**
	 * The special loot at this place ({@link #LOOT_AREA} by its name, {@link #LOOT_PLACE} its own) and for this master's
	 * tasks ({@code master}: varbit 4067).
	 */
	public List<Loot> lootFor(Place p, int master)
	{
		List<Loot> out = new ArrayList<>();
		String own = placeKey(p);
		for (Loot l : loot)
		{
			if (LOOT_AREA.equals(l.kind) ? p.getName().startsWith(l.key)
				: LOOT_PLACE.equals(l.kind) ? own.equals(l.key) : String.valueOf(master).equals(l.key))
			{
				out.add(l);
			}
		}
		return out;
	}

	/** A {@link #LOOT_PLACE} line's key for the place: "6247:Darkmeyer". */
	public static String placeKey(Place p)
	{
		return p.getTask() + ":" + p.getName();
	}

	/**
	 * The Wiki's notes as plain text, without links: links in brackets ("[https://... Click here for a picture guide
	 * ...]") are taken out whole, as are bare links and the Wiki's bold / italic quotes.
	 */
	public static String plainNotes(String notes)
	{
		if (notes == null)
		{
			return "";
		}
		String s = notes.replaceAll("\\[https?://[^\\]]*\\]", "").replaceAll("https?://\\S+", "").replace("'''", "")
			.replace("''", "");
		return s.replaceAll("[ \\t]{2,}", " ").replaceAll("\\s+([.,;:])", "$1").trim();
	}

	/** The name of the area Konar named (varp 2096's value), or null. */
	public String areaName(int id)
	{
		return areas.get(id);
	}

	/** The master's name (varbit 4067's value), or null. */
	public String masterName(int number)
	{
		return masters.get(number);
	}

	public boolean isEmpty()
	{
		return tasksByRow.isEmpty();
	}

	public static SlayerPlaces parse(String text)
	{
		Map<Integer, String> masters = new LinkedHashMap<>();
		List<Task> tasks = new ArrayList<>();
		Map<Integer, Integer> bosses = new LinkedHashMap<>();
		Map<Integer, String> areas = new LinkedHashMap<>();
		List<Place> places = new ArrayList<>();
		Map<Integer, Integer> icons = new LinkedHashMap<>();
		List<Loot> loot = new ArrayList<>();
		for (String line : text.split("\r?\n"))
		{
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			String[] w = line.split("\t", -1);
			switch (w[0])
			{
				case "master":
					masters.put(Integer.parseInt(w[1]), w[2]);
					break;
				case "task":
					tasks.add(new Task(Integer.parseInt(w[1]), Integer.parseInt(w[2]), w[3]));
					break;
				case "boss":
					bosses.put(Integer.parseInt(w[1]), Integer.parseInt(w[2]));
					break;
				case "area":
					areas.put(Integer.parseInt(w[1]), w[2]);
					break;
				case "icon":
					icons.put(Integer.parseInt(w[1]), Integer.parseInt(w[2]));
					break;
				case "loot":
					loot.add(new Loot(w[1], w[2], points0(w[3]), undash(w[4])));
					break;
				case "place":
					places.add(new Place(Integer.parseInt(w[1]), w[2], Integer.parseInt(w[3]), w[4], w[5], w[6], w[7], points(w[8]),
						!w[9].equals("no"), undash(w[10]), undash(w[11]), undash(w[12])));
					break;
				default:
					throw new IllegalArgumentException("unknown Slayer line: " + line);
			}
		}
		return new SlayerPlaces(masters, tasks, bosses, areas, places, icons, loot);
	}

	/** Item numbers separated by spaces. */
	private static int[] points0(String s)
	{
		String t = s.trim();
		if (t.isEmpty())
		{
			return new int[0];
		}
		String[] parts = t.split(" +");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			out[i] = Integer.parseInt(parts[i]);
		}
		return out;
	}

	private static int[] points(String s)
	{
		String[] parts = s.trim().split(" +");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			String[] c = parts[i].split(",");
			out[i] = Tiles.pack(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
		}
		return out;
	}

	private static String word(String s)
	{
		return s == null || s.isBlank() || s.equals("-") ? "?" : s;
	}

	private static String undash(String s)
	{
		return s == null || s.equals("-") ? "" : s;
	}
}
