package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sea charting tasks and the sets the game counts them in (completing a set gives bonus experience). From the game
 * cache: DB table 154 ({@code SailingChartingCore}: each task's text, set, sea, kind, hazard, needed dock), the seas
 * and oceans of table 201, the "done" varbit (18574 + the task's number), the Sailing level (client script 9001's rule)
 * and the experience the game's Captain's Log shows (enums 5868, 5869 and 5870). Where each task is, and its tip, comes
 * from Quest Helper's charting list; a task without a place can't be routed to. Immutable.
 *
 * <p><b>Sets</b> are what the Captain's Log counts and rewards on completion: a sea, a bonus group (the game's
 * "Bonus Drinks", "Bonus Currents", "Bonus Dives", "Bonus Weather": their tasks show "(Bonus)" and count there, not in
 * the sea they are in) or an ocean (every task of its seas and groups).
 *
 * <p>Text form (the plugin's optional {@value #FILE}): {@code #} comment lines; {@code set} lines
 * ({@value #SET_HEADER}); the header {@value #HEADER}; then one tab-separated line per task. Columns after the last are
 * ignored.
 */
public final class ChartingTasks
{
	/** The plugin's optional data file. */
	public static final String FILE = "charting.tsv";
	public static final String SET_HEADER = "set\tname\ttype\tocean\txp";
	public static final String HEADER = "task\tvarbit\tkind\tset\tsea\tlevel\txp\thazard\tdock\trepeat\tplaces\ttext\ttip\ttool"
		+ "\ttool items\tdrink\tdrink name";
	/** No tasks (no {@value #FILE}). */
	public static final ChartingTasks NONE = new ChartingTasks(List.of(), List.of());

	public static final String SEA = "sea";
	public static final String BONUS = "bonus";
	public static final String OCEAN = "ocean";
	/** Task kinds with a second place: a current duck (let go, then picked up) and a weather task (measure, come back). */
	public static final String CURRENT = "current";
	public static final String WEATHER = "weather";

	/** A sea, a bonus group or an ocean, with the experience the game gives for completing it. */
	public static final class Set
	{
		final String name;
		final String type;
		final String ocean;
		final int xp;

		public Set(String name, String type, String ocean, int xp)
		{
			this.name = name;
			this.type = type;
			this.ocean = ocean == null ? "" : ocean;
			this.xp = xp;
		}

		public String getName()
		{
			return name;
		}

		/** {@link #SEA}, {@link #BONUS} or {@link #OCEAN}. */
		public String getType()
		{
			return type;
		}

		/** The ocean a sea or bonus group belongs to; empty for an ocean. */
		public String getOcean()
		{
			return ocean;
		}

		/** Experience for completing every task of the set (the Captain's Log's "Sea / Ocean Completion"). */
		public int getXp()
		{
			return xp;
		}

	}

	/** One charting task. */
	public static final class Task
	{
		final int number;
		final int varbit;
		final String kind;
		final String set;
		final String sea;
		final int level;
		final int xp;
		final String hazard;
		final String dock;
		final boolean repeat;
		final int[] places;
		final String text;
		final String tip;
		final String tool;
		final int[] toolItems;
		/** A drink crate's drink (item number, -1: none) and its name. */
		final int drink;
		final String drinkName;

		/** {@code drink}: a drink crate task's drink (item number, -1: none), {@code drinkName} its name. */
		public Task(int number, int varbit, String kind, String set, String sea, int level, int xp, String hazard, String dock,
			boolean repeat, int[] places, String text, String tip, String tool, int[] toolItems, int drink, String drinkName)
		{
			this.drink = drink;
			this.drinkName = drinkName == null ? "" : drinkName;
			this.tool = tool == null ? "" : tool;
			this.toolItems = toolItems.clone();
			this.number = number;
			this.varbit = varbit;
			this.kind = kind;
			this.set = set;
			this.sea = sea == null || sea.isEmpty() ? set : sea;
			this.level = level;
			this.xp = xp;
			this.hazard = hazard == null ? "" : hazard;
			this.dock = dock == null ? "" : dock;
			this.repeat = repeat;
			this.places = places.clone();
			this.text = text;
			this.tip = tip == null ? "" : tip;
		}

		/** A drink crate's drink: the item the crate gives, which the player then drinks; -1 for none. */
		public int getDrink()
		{
			return drink;
		}

		/** The drink's name ("Smuggled rum"); empty for none. */
		public String getDrinkName()
		{
			return drinkName;
		}

		/** The game's task number (DB table 154 column 0), 0 up. */
		public int getNumber()
		{
			return number;
		}

		/** The varbit that is 1 once the task is done. */
		public int getVarbit()
		{
			return varbit;
		}

		/** "generic", "spyglass", "current", "crate", "weather" or "mermaid". */
		public String getKind()
		{
			return kind;
		}

		/** The set the game counts it in: its sea, or a bonus group ("Bonus Drinks"). */
		public String getSet()
		{
			return set;
		}


		/** The Sailing level it needs (the game's rule: the highest of its kind's, its hazard's and its dock's). */
		public int getLevel()
		{
			return level;
		}

		/** The experience the game gives for it (the Captain's Log's "XP awarded"). */
		public int getXp()
		{
			return xp;
		}

		/** The sea hazard there ("Icy seas"); empty = none. */
		public String getHazard()
		{
			return hazard;
		}

		public boolean hasPlace()
		{
			return places.length > 0;
		}

		/** Where the task starts (packed); -1 if not known. */
		public int getPlace()
		{
			return places.length == 0 ? -1 : places[0];
		}

		/** A current duck's pick-up spot or a weather task's measuring spot (packed); -1 for none. */
		public int getSecondPlace()
		{
			return places.length < 2 ? -1 : places[1];
		}

		/**
		 * Where the player is when the task is done (packed; -1 if not known): a current duck's pick-up spot (the duck is
		 * let go at the start and picked up there), else the start (a weather task ends back at the meteorologist).
		 */
		public int getEndPlace()
		{
			return CURRENT.equals(kind) && places.length > 1 ? places[1] : getPlace();
		}

		public boolean isCurrentDuck()
		{
			return CURRENT.equals(kind);
		}

		public boolean isWeather()
		{
			return WEATHER.equals(kind);
		}

		/** The game's text for the task ("Find a crashed glider south of the Karamja Shipyard."). */
		public String getText()
		{
			return text;
		}

		/** The tool the game's Captain's Log says the task needs ("Spyglass"); empty = none. */
		public String getTool()
		{
			return tool;
		}

		/**
		 * The tool's item ids (any one will do); empty for no tool, and for a tool the plugin doesn't check (diving
		 * equipment: the game checks several outfits).
		 */
		public int[] getToolItems()
		{
			return toolItems.clone();
		}

		/** A tip from Quest Helper's list ("This will teleport you to Witchaven, ..."); empty = none. */
		public String getTip()
		{
			return tip;
		}

	}

	final List<Set> sets;
	private final Map<String, Set> setsByName;
	final List<Task> tasks;

	public ChartingTasks(List<Set> sets, List<Task> tasks)
	{
		this.sets = Collections.unmodifiableList(new ArrayList<>(sets));
		Map<String, Set> m = new LinkedHashMap<>();
		for (Set s : sets)
		{
			m.put(s.name, s);
		}
		this.setsByName = Collections.unmodifiableMap(m);
		this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
	}

	/** The sets in the game's order: each ocean, then its seas and groups. */
	public List<Set> getSets()
	{
		return sets;
	}

	/** A set by name; null if there is none. */
	public Set set(String name)
	{
		return setsByName.get(name);
	}

	public List<Task> getTasks()
	{
		return tasks;
	}

	public boolean isEmpty()
	{
		return tasks.isEmpty();
	}

	/** The ocean a task counts in (its set's ocean); empty if not known. */
	public String oceanOf(Task t)
	{
		Set s = setsByName.get(t.set);
		return s == null ? "" : s.ocean;
	}

	/** The tasks of a set (an ocean: every task of its seas and groups); null: every task. */
	public List<Task> tasksIn(String setName)
	{
		if (setName == null)
		{
			return tasks;
		}
		Set s = setsByName.get(setName);
		boolean ocean = s != null && OCEAN.equals(s.type);
		List<Task> out = new ArrayList<>();
		for (Task t : tasks)
		{
			if (ocean ? setName.equals(oceanOf(t)) : setName.equals(t.set))
			{
				out.add(t);
			}
		}
		return out;
	}

	/** The lowest and highest task varbit, or {-1, -1} with no tasks: a quick test for "is this a charting varbit". */
	public int[] varbitRange()
	{
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (Task t : tasks)
		{
			lo = Math.min(lo, t.varbit);
			hi = Math.max(hi, t.varbit);
		}
		return tasks.isEmpty() ? new int[]{-1, -1} : new int[]{lo, hi};
	}

	/** Reads the text form; a line that can't be read throws, naming its number. */
	public static ChartingTasks parse(List<String> lines)
	{
		List<Set> sets = new ArrayList<>();
		List<Task> out = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++)
		{
			String line = lines.get(i);
			if (line.isBlank() || line.startsWith("#") || line.startsWith(HEADER) || line.startsWith(SET_HEADER))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			try
			{
				if (c[0].equals("set"))
				{
					if (c.length < 5)
					{
						throw new IllegalArgumentException("charting line " + (i + 1) + ": 5 columns needed: " + line);
					}
					sets.add(new Set(c[1], c[2].trim(), c[3], Integer.parseInt(c[4].trim())));
					continue;
				}
				if (c.length < 15)
				{
					throw new IllegalArgumentException("charting line " + (i + 1) + ": 15 columns needed: " + line);
				}
				List<Integer> items = new ArrayList<>();
				for (String id : c[14].trim().split(" +"))
				{
					if (!id.isEmpty())
					{
						items.add(Integer.parseInt(id));
					}
				}
				int drink = c.length > 15 && !c[15].trim().isEmpty() ? Integer.parseInt(c[15].trim()) : -1;
				out.add(new Task(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), c[2].trim(), c[3], c[4],
					Integer.parseInt(c[5].trim()), Integer.parseInt(c[6].trim()), c[7], c[8], c[9].trim().equals("yes"),
					tiles(c[10], i), c[11], c[12], c[13], items.stream().mapToInt(Integer::intValue).toArray(), drink,
					c.length > 16 ? c[16] : ""));
			}
			catch (NumberFormatException e)
			{
				throw new IllegalArgumentException("charting line " + (i + 1) + ": a number expected: " + line, e);
			}
		}
		return new ChartingTasks(sets, out);
	}

	/** Tiles written {@code x,y,plane} separated by spaces. */
	public static int[] tiles(String text, int lineIndex)
	{
		List<Integer> places = new ArrayList<>();
		for (String t : text.trim().split(" +"))
		{
			if (t.isEmpty())
			{
				continue;
			}
			String[] xyz = t.split(",");
			if (xyz.length != 3)
			{
				throw new IllegalArgumentException("charting line " + (lineIndex + 1) + ": tile x,y,plane expected: " + t);
			}
			places.add(Tiles.pack(Integer.parseInt(xyz[0].trim()), Integer.parseInt(xyz[1].trim()),
				Integer.parseInt(xyz[2].trim())));
		}
		return places.stream().mapToInt(Integer::intValue).toArray();
	}

}
