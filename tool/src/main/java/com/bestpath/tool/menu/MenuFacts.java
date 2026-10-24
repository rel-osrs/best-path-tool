package com.bestpath.tool.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the game cache says about the things a player clicks to use a transport: names and menu options of objects,
 * NPCs, items and spells, where transport-like objects are placed, and the destination lists the cache has (fairy ring
 * codes, quetzal landing sites, charter ship ports, minigame teleports, glider names). Filled by
 * {@link MenuFactsLoader} from the real cache, or by hand in tests. Read by {@link MenuMatcher}.
 */
public final class MenuFacts
{
	/** One form of an object or NPC (a multiloc / multi-NPC has several): its name and menu options as the game shows them. */
	public static final class Form
	{
		public final int id;
		/** The name, or null when this form has none ("null" in the cache). */
		public final String name;
		/** Plain menu options, in option order (empty slots left out). */
		public final List<String> ops;
		/** Options shown only under a varbit / varp condition (e.g. a spirit tree's "Last-destination" texts). */
		public final List<String> conditionalOps;
		/** Sub-menu options, by the option text they belong to. */
		public final Map<String, List<String>> subOps;

		public Form(int id, String name, List<String> ops, List<String> conditionalOps, Map<String, List<String>> subOps)
		{
			this.id = id;
			this.name = name == null || name.isEmpty() || "null".equals(name) ? null : name;
			this.ops = List.copyOf(ops);
			this.conditionalOps = List.copyOf(conditionalOps);
			this.subOps = Collections.unmodifiableMap(new LinkedHashMap<>(subOps));
		}

		public Form(int id, String name, String... ops)
		{
			this(id, name, List.of(ops), List.of(), Map.of());
		}
	}

	/** An object or NPC: its own form first, then the forms it can change into (varbit / varp). */
	public static final class Thing
	{
		public final int id;
		public final List<Form> forms;

		public Thing(int id, List<Form> forms)
		{
			this.id = id;
			this.forms = List.copyOf(forms);
		}

		/** The first name of any form, or null. */
		public String name()
		{
			for (Form f : forms)
			{
				if (f.name != null)
				{
					return f.name;
				}
			}
			return null;
		}

		/** Every plain option of every form, in order, each once. */
		public List<String> ops()
		{
			Set<String> out = new LinkedHashSet<>();
			for (Form f : forms)
			{
				out.addAll(f.ops);
			}
			return new ArrayList<>(out);
		}

		/** Every conditional option text of every form, each once. */
		public List<String> conditionalOps()
		{
			Set<String> out = new LinkedHashSet<>();
			for (Form f : forms)
			{
				out.addAll(f.conditionalOps);
			}
			return new ArrayList<>(out);
		}

		/** The sub-menu options of an option (any form), each once. */
		public List<String> subOps(String op)
		{
			Set<String> out = new LinkedHashSet<>();
			for (Form f : forms)
			{
				List<String> s = f.subOps.get(op);
				if (s != null)
				{
					out.addAll(s);
				}
			}
			return new ArrayList<>(out);
		}

		/** The name of the first named form that offers {@code op} (plain or conditional), else {@link #name()}. */
		public String nameFor(String op)
		{
			for (Form f : forms)
			{
				if (f.name != null && (f.ops.contains(op) || f.conditionalOps.contains(op)))
				{
					return f.name;
				}
			}
			return name();
		}

	}

	/** An item: its name, inventory options (and their sub-menus) and worn options (and their sub-menus). */
	public static final class Item
	{
		public final int id;
		public final String name;
		/** Inventory options by slot 0-4 (null = none). */
		public final String[] invOps;
		/** Inventory sub-menu options by slot (null = none). */
		public final List<List<String>> invSubOps;
		/** Worn options by slot 0-7 (item params 451-458, RuneLite ParamID.OC_ITEM_OP1..8; null = none). */
		public final String[] wornOps;
		/** Worn sub-menu options by slot (params OC_ITEM_OPn_SUBOPm; null = none). */
		public final List<List<String>> wornSubOps;

		public Item(int id, String name, String[] invOps, List<List<String>> invSubOps, String[] wornOps, List<List<String>> wornSubOps)
		{
			this.id = id;
			this.name = name;
			this.invOps = invOps.clone();
			this.invSubOps = invSubOps;
			this.wornOps = wornOps.clone();
			this.wornSubOps = wornSubOps;
		}
	}

	/** A spell: its name (spell obj param 601, ParamID.SPELL_NAME) and its spellbook button's options ("Cast", "Grand Exchange"). */
	public static final class Spell
	{
		public final String name;
		public final List<String> ops;

		public Spell(String name, List<String> ops)
		{
			this.name = name;
			this.ops = List.copyOf(ops);
		}
	}

	/** A placed object with a transport-like option: id, south-west tile (plane as used: one lower on bridge tiles), size. */
	public static final class Placed
	{
		public final int id;
		public final int x;
		public final int y;
		public final int plane;
		public final int sizeX;
		public final int sizeY;

		public Placed(int id, int x, int y, int plane, int sizeX, int sizeY)
		{
			this.id = id;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
		}

		/** Tiles from (x, y) to the nearest tile of the footprint (Chebyshev). */
		public int distance(int tx, int ty)
		{
			int dx = tx < x ? x - tx : tx >= x + sizeX ? tx - (x + sizeX - 1) : 0;
			int dy = ty < y ? y - ty : ty >= y + sizeY ? ty - (y + sizeY - 1) : 0;
			return Math.max(dx, dy);
		}
	}

	/** Objects by id (the loader fills the placed ones; {@link #object(int)} asks {@link #objectSource} for others). */
	public final Map<Integer, Thing> objects = new HashMap<>();
	/** Where {@link #object(int)} finds objects not in {@link #objects} (null = nowhere). */
	public java.util.function.IntFunction<Thing> objectSource;
	/** Object ids by exact name (every object definition, placed or not: house furniture is never placed). */
	public final Map<String, List<Integer>> objectsByName = new HashMap<>();
	public final Map<Integer, Thing> npcs = new HashMap<>();
	public final Map<Integer, Item> items = new HashMap<>();
	/** Spells by lower-case name (several spellbooks may share a name: options joined). */
	public final Map<String, Spell> spells = new HashMap<>();
	/** Placed transport-like objects by {@link #cell(int, int, int)}. */
	private final Map<Long, List<Placed>> placed = new HashMap<>();
	/** One entry of a list whose keyboard key the cache fixes (the house jewellery box's list). */
	public static final class ListEntry
	{
		/** The entry's text without its key ("Burthorpe"). */
		public final String name;
		/** The key that picks it ("5", "A"). */
		public final String key;
		/** The furniture it needs: 1 basic, 2 fancy, 3 ornate jewellery box. */
		public final int tier;

		public ListEntry(String name, String key, int tier)
		{
			this.name = name;
			this.key = key;
			this.tier = tier;
		}
	}

	/** The house jewellery box's list ("Teleport Menu"): every entry with its fixed key, in the game's order. */
	public final List<ListEntry> jewelleryBox = new ArrayList<>();

	/**
	 * The spirit trees in the game's order: the spirit tree's "Last-destination" texts by the value of varbit 20252
	 * (1 = "Tree Gnome Village" ... 14 = "Laguna Aurorae"); empty when the cache has no such list.
	 */
	public final List<String> spiritTrees = new ArrayList<>();

	/** The keys of a list's lines by place: "1".."9", then enum 1401's letters "A".."Z" (client script 218). */
	public final List<String> listKeys = new ArrayList<>();

	/**
	 * The house portal nexus's list ("Teleport Menu"), by the destination the requirement lines name ("Varrock
	 * Teleport"): its main entry's text, then the alternate's when it has one ({"Varrock", "Grand Exchange"},
	 * {"Senntisten"}). Keys: none here (they follow the player's own list).
	 */
	public final Map<String, List<String>> nexus = new LinkedHashMap<>();

	/**
	 * Lists the game server sends after a click, written by hand in {@code data/manual/list-keys.tsv} ({@link
	 * ListKeys#lend}), by {@link #clickKey}: the lines in the game's order.
	 */
	public final Map<String, List<String>> listed = new HashMap<>();

	/** The key of a click in {@link #listed}: "travel|minecart". */
	public static String clickKey(String option, String target)
	{
		return (option + "|" + target).toLowerCase(java.util.Locale.ROOT);
	}

	/** The written list a click opens, or empty. */
	public List<String> listed(String option, String target)
	{
		return listed.getOrDefault(clickKey(option, target), List.of());
	}

	/** Destination lists, by list name ({@link #FAIRY_RINGS}, ...): the game's text for each entry. */
	public final Map<String, List<String>> lists = new HashMap<>();

	/** Fairy ring codes ("CKQ"), from DB table 89 (RuneLite DBTableID.Fairyring, COL_CODE "C K Q"). */
	public static final String FAIRY_RINGS = "fairy rings";
	/** Quetzal landing sites, DB table 64 (DBTableID.Quetzal, COL_NAME). */
	public static final String QUETZALS = "quetzals";
	/** Charter ship ports, DB table 206 (DBTableID.CharteringDestinations, COL_CHARTERING_DESTINATION_NAME). */
	public static final String CHARTER = "charter ships";
	/** Minigame teleports, DB table 214 (DBTableID.MinigameTeleport, COL_NAME). */
	public static final String MINIGAMES = "minigame teleports";
	/** Gnome glider destinations: the options of the glider map's buttons (interface 138). */
	public static final String GLIDERS = "gnome gliders";
	/** Canoe destinations: the names the canoe map's client scripts show (7884 River Lum, 7134 River Dougne). */
	public static final String CANOES = "canoes";

	/** An object by id, or null. */
	public Thing object(int id)
	{
		Thing t = objects.get(id);
		if (t == null && objectSource != null)
		{
			t = objectSource.apply(id);
			if (t != null)
			{
				objects.put(id, t);
			}
		}
		return t;
	}

	private static long cell(int x, int y, int plane)
	{
		return ((long) plane << 40) | ((long) (x >> 3) << 20) | (y >> 3);
	}

	public void place(Placed p)
	{
		placed.computeIfAbsent(cell(p.x, p.y, p.plane), k -> new ArrayList<>()).add(p);
	}

	/** Placed transport-like objects on {@code plane} whose footprint is within {@code radius} tiles of (x, y). */
	public List<Placed> near(int x, int y, int plane, int radius)
	{
		List<Placed> out = new ArrayList<>();
		int reach = radius + 8; // objects up to 8 tiles wide may start in a neighbouring cell
		for (int cx = (x - reach) >> 3; cx <= (x + reach) >> 3; cx++)
		{
			for (int cy = (y - reach) >> 3; cy <= (y + reach) >> 3; cy++)
			{
				List<Placed> l = placed.get(((long) plane << 40) | ((long) cx << 20) | cy);
				if (l != null)
				{
					for (Placed p : l)
					{
						if (p.distance(x, y) <= radius)
						{
							out.add(p);
						}
					}
				}
			}
		}
		return out;
	}

	/** The entries of a destination list (empty if the cache has none). */
	public List<String> list(String name)
	{
		return lists.getOrDefault(name, List.of());
	}

	/** Adds a spell; a second spell of the same name (another spellbook) adds its options. */
	public void addSpell(String name, List<String> ops)
	{
		String key = name.toLowerCase(Locale.ROOT);
		Spell old = spells.get(key);
		if (old == null)
		{
			spells.put(key, new Spell(name, ops));
			return;
		}
		Set<String> all = new LinkedHashSet<>(old.ops);
		all.addAll(ops);
		spells.put(key, new Spell(old.name, new ArrayList<>(all)));
	}
}
