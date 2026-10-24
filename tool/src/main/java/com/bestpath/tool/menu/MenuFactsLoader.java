package com.bestpath.tool.menu;

import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.definitions.EnumDefinition;
import net.runelite.cache.definitions.InterfaceDefinition;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.StructDefinition;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;
import net.runelite.cache.script.Instructions;

/**
 * Reads {@link MenuFacts} from the game cache (checked in cache #2735; docs/DATA_FORMATS.md "menu"):
 * <ul>
 * <li>objects and NPCs: name and options of every form ({@code EntityOpsDefinition}: plain options, conditional
 * options, sub-menus); the placed objects with a transport-like option (map locations; one plane lower on bridge
 * tiles, as the transport candidates);</li>
 * <li>items: name, inventory options and their sub-menus, worn options (item params 451-458, RuneLite
 * {@code ParamID.OC_ITEM_OP1..8}) and their sub-menus ({@code ParamID.OC_ITEM_OPn_SUBOPm}: 661-663 and 2069-2073 for
 * the first, then 2074 + 8 (m - 2) + (n - 1));</li>
 * <li>spells: the spell objs' name (param 601, {@code ParamID.SPELL_NAME}) and the options of their spellbook button
 * (param 596, {@code ParamID.SPELL_BUTTON}); a {@code *} option is "Cast" (set by client script 2615);</li>
 * <li>destination lists: fairy ring codes (DB table 89 column 3), quetzal sites (table 64 column 1), charter ship
 * ports (table 206 column 1), minigame teleports (table 214 column 1), gnome glider buttons (interface 138).</li>
 * <li>the house jewellery box's list (interface 590, built by client script {@value #SCRIPT_JEWELLERY_BOX}): each
 * entry's text and its fixed keyboard key ({@link #jewelleryBox});</li>
 * <li>the house portal nexus's list (interface 17): each destination's text (enum 1377 -> struct param 660, an
 * alternate such as "Grand Exchange" in param 679); its keys follow the player's own list ({@link #nexus}).</li>
 * </ul>
 */
public final class MenuFactsLoader
{
	private MenuFactsLoader()
	{
	}

	static final int PARAM_SPELL_BUTTON = 596;
	static final int PARAM_SPELL_NAME = 601;
	/** Worn options 1-8 (ParamID.OC_ITEM_OP1..8). */
	static final int PARAM_WORN_OP1 = 451;
	/** Worn sub-menu option 1 of worn options 1-8 (ParamID.OC_ITEM_OPn_SUBOP1). */
	static final int[] PARAM_WORN_SUBOP1 = {661, 662, 663, 2069, 2070, 2071, 2072, 2073};
	/** Worn sub-menu options 2-20: 2074 + 8 (m - 2) + (n - 1) (ParamID.OC_ITEM_OPn_SUBOPm). */
	static final int PARAM_WORN_SUBOP2 = 2074;
	static final int WORN_SUBOPS = 20;

	static final int TABLE_FAIRY_RINGS = 89;
	static final int TABLE_QUETZALS = 64;
	static final int TABLE_CHARTER = 206;
	static final int TABLE_MINIGAMES = 214;
	static final int INTERFACE_GLIDERS = 138;
	/** Builds the jewellery box's list: one call of {@value #SCRIPT_JEWELLERY_ENTRY} per entry (cache #2735). */
	static final int SCRIPT_JEWELLERY_BOX = 1685;
	/** One entry of the jewellery box's list: (layer, box tier, text, ..., entry number); sets its key. */
	static final int SCRIPT_JEWELLERY_ENTRY = 1688;
	/** Keys after "9": A-Z (enum 1401, upper case; enum 1402 the lower case a key press also matches). */
	static final int ENUM_KEYS = 1401;
	/** The portal nexus's destinations: key -> struct (param 660 the list text, 679 the alternate's struct). */
	static final int ENUM_NEXUS = 1377;
	static final int PARAM_NEXUS_NAME = 660;
	static final int PARAM_NEXUS_ALTERNATE = 679;
	static final int PARAM_NEXUS_MAIN = 680;
	/** The client scripts that fill the canoe map with its destinations (cache #2735). */
	static final int[] SCRIPTS_CANOES = {7884, 7134};

	/** The worn sub-menu param of worn option {@code n} (1-8), sub-option {@code m} (1-20). */
	static int wornSubOpParam(int n, int m)
	{
		return m == 1 ? PARAM_WORN_SUBOP1[n - 1] : PARAM_WORN_SUBOP2 + 8 * (m - 2) + (n - 1);
	}

	public static MenuFacts load(LoadedCache cache) throws IOException
	{
		MenuFacts f = new MenuFacts();
		// objects and their placements (the object definitions are loaded with the regions)
		cache.forEachRegion(r ->
		{
			for (Location l : r.getLocations())
			{
				placeObject(cache, f, r, l);
			}
		});
		f.objectSource = id ->
		{
			ObjectDefinition def = cache.object(id);
			return def == null ? null : object(cache, def);
		};
		indexObjectNames(cache, f);
		// the canoe stations' destination lists (client scripts 7884, River Lum, and 7134, River Dougne: each
		// destination's name passed to the canoe map, with its number)
		f.lists.put(MenuFacts.CANOES, cache.scriptStrings(SCRIPTS_CANOES));
		addNpcs(cache, f);
		// items and spells (spell objs carry the spell's name and button)
		InterfaceDefinition[][] ifs = cache.interfaceDefinitions();
		addItemsAndSpells(cache, ifs, f);
		// destination lists
		f.lists.put(MenuFacts.FAIRY_RINGS, column(cache, TABLE_FAIRY_RINGS, 3, true));
		f.lists.put(MenuFacts.QUETZALS, column(cache, TABLE_QUETZALS, 1, false));
		f.lists.put(MenuFacts.CHARTER, column(cache, TABLE_CHARTER, 1, false));
		f.lists.put(MenuFacts.MINIGAMES, column(cache, TABLE_MINIGAMES, 1, false));
		f.lists.put(MenuFacts.GLIDERS, gliders(ifs));
		f.jewelleryBox.addAll(jewelleryBox(cache));
		f.nexus.putAll(nexus(cache));
		f.listKeys.addAll(listKeys(cache));
		f.spiritTrees.addAll(spiritTrees(cache, f.objectsByName));
		return f;
	}

	/** Adds a placed object's facts, and its placement when it has a transport-like option. */
	private static void placeObject(LoadedCache cache, MenuFacts f, Region r, Location l)
	{
		ObjectDefinition def = cache.object(l.getId());
		if (def == null)
		{
			return;
		}
		MenuFacts.Thing t = f.objects.computeIfAbsent(l.getId(), id -> object(cache, def));
		if (!transportLike(t))
		{
			return;
		}
		int type = l.getType();
		int rot = l.getOrientation() & 3;
		int sx = type >= 9 ? def.getSizeX() : 1;
		int sy = type >= 9 ? def.getSizeY() : 1;
		if (rot == 1 || rot == 3)
		{
			int s = sx;
			sx = sy;
			sy = s;
		}
		int lx = l.getPosition().getX() - r.getBaseX();
		int ly = l.getPosition().getY() - r.getBaseY();
		int plane = l.getPosition().getZ();
		if (lx >= 0 && lx < 64 && ly >= 0 && ly < 64 && (r.getTileSetting(1, lx, ly) & 2) != 0)
		{
			plane--;
		}
		if (plane >= 0)
		{
			f.place(new MenuFacts.Placed(l.getId(), l.getPosition().getX(), l.getPosition().getY(), plane, sx, sy));
		}
	}

	/** Every named object's id under its name (also objects never placed on the map, such as house furniture). */
	private static void indexObjectNames(LoadedCache cache, MenuFacts f)
	{
		for (int id = 0, missing = 0; missing < 20000; id++)
		{
			ObjectDefinition def = cache.object(id);
			missing = def == null ? missing + 1 : 0;
			if (def != null && def.getName() != null && !"null".equals(def.getName()))
			{
				f.objectsByName.computeIfAbsent(def.getName(), k -> new ArrayList<>()).add(id);
			}
		}
	}

	/** NPCs with their forms. */
	private static void addNpcs(LoadedCache cache, MenuFacts f) throws IOException
	{
		Map<Integer, NpcDefinition> npcDefs = new java.util.HashMap<>();
		for (NpcDefinition n : cache.npcDefinitions())
		{
			npcDefs.put(n.getId(), n);
		}
		for (NpcDefinition n : npcDefs.values())
		{
			List<MenuFacts.Form> forms = new ArrayList<>();
			forms.add(form(n.getId(), n.getName(), n.getOps()));
			if (n.getConfigs() != null)
			{
				for (int c : n.getConfigs())
				{
					NpcDefinition d = c >= 0 ? npcDefs.get(c) : null;
					if (d != null)
					{
						forms.add(form(d.getId(), d.getName(), d.getOps()));
					}
				}
			}
			f.npcs.put(n.getId(), new MenuFacts.Thing(n.getId(), forms));
		}
	}

	/** Items (not noted or placeholders) and spells (spell objs carry the spell's name and button). */
	private static void addItemsAndSpells(LoadedCache cache, InterfaceDefinition[][] ifs, MenuFacts f) throws IOException
	{
		for (ItemDefinition d : cache.itemDefinitions())
		{
			Map<Integer, Object> params = d.getParams();
			Object spellName = params == null ? null : params.get(PARAM_SPELL_NAME);
			if (spellName instanceof String && params.get(PARAM_SPELL_BUTTON) instanceof Integer)
			{
				f.addSpell((String) spellName, spellOps(ifs, (Integer) params.get(PARAM_SPELL_BUTTON)));
			}
			if (d.getNotedTemplate() != -1 || d.getPlaceholderTemplateId() != -1 || d.getName() == null || "null".equals(d.getName()))
			{
				continue;
			}
			f.items.put(d.getId(), item(d));
		}
	}

	/** The gnome glider buttons' texts (interface {@value #INTERFACE_GLIDERS}), each once. */
	private static List<String> gliders(InterfaceDefinition[][] ifs)
	{
		Set<String> gliders = new LinkedHashSet<>();
		if (ifs.length > INTERFACE_GLIDERS && ifs[INTERFACE_GLIDERS] != null)
		{
			for (InterfaceDefinition c : ifs[INTERFACE_GLIDERS])
			{
				if (c != null && c.getActions() != null)
				{
					for (String a : c.getActions())
					{
						if (a != null && !a.isEmpty())
						{
							gliders.add(a);
						}
					}
				}
			}
		}
		return new ArrayList<>(gliders);
	}

	/** A list's keys by place (client script 218: "1".."9", then enum {@value #ENUM_KEYS} from the 10th line). */
	static List<String> listKeys(LoadedCache cache)
	{
		List<String> out = new ArrayList<>();
		for (int n = 1; n <= 9; n++)
		{
			out.add(String.valueOf(n));
		}
		EnumDefinition keys = cache.enumDefinition(ENUM_KEYS);
		if (keys == null)
		{
			throw new IllegalStateException("enum " + ENUM_KEYS + " (the list keys after 9) is missing: look again (MenuFactsLoader.listKeys)");
		}
		Map<Integer, String> letters = new java.util.TreeMap<>();
		for (int i = 0; i < keys.getSize(); i++)
		{
			letters.put(keys.getKeys()[i], keys.getStringVals()[i]);
		}
		for (int n = 1; letters.containsKey(n); n++)
		{
			out.add(letters.get(n));
		}
		return out;
	}

	/** The spirit trees' "Last-destination" option, whose texts follow varbit {@value #VARBIT_SPIRIT_TREE_LAST}. */
	static final String SPIRIT_TREE_LAST = "Last-destination";
	static final int VARBIT_SPIRIT_TREE_LAST = 20252;

	/**
	 * The spirit trees in the game's order (checked in cache #2735: object 8355's "Last-destination" texts
	 * for varbit 20252 = 1..14, the same order as the Wiki's picture of the spirit tree list). Every spirit tree with
	 * that option must give the same list; the export stops if they differ or the option's shape changed.
	 */
	static List<String> spiritTrees(LoadedCache cache, Map<String, List<Integer>> objectsByName)
	{
		List<String> found = null;
		for (Map.Entry<String, List<Integer>> e : objectsByName.entrySet())
		{
			if (!e.getKey().equalsIgnoreCase("Spirit tree"))
			{
				continue;
			}
			for (int id : e.getValue())
			{
				ObjectDefinition d = cache.object(id);
				List<String> l = d == null ? null : lastDestinations(d.getOps());
				if (l == null)
				{
					continue;
				}
				if (found != null && !found.equals(l))
				{
					throw new IllegalStateException("spirit tree " + id + " lists " + l + ", another " + found
						+ ": look again (MenuFactsLoader.spiritTrees)");
				}
				found = l;
			}
		}
		if (found == null)
		{
			throw new IllegalStateException("no spirit tree has a '" + SPIRIT_TREE_LAST + "' option on varbit "
				+ VARBIT_SPIRIT_TREE_LAST + ": look again (MenuFactsLoader.spiritTrees)");
		}
		return found;
	}

	/** The "Last-destination" texts for varbit 20252 = 1, 2, 3...; null when the object has no such option. */
	static List<String> lastDestinations(EntityOpsDefinition e)
	{
		if (e == null || e.getOps() == null || e.getConditionalOps() == null)
		{
			return null;
		}
		for (int i = 0; i < e.getOps().size() && i < e.getConditionalOps().size(); i++)
		{
			EntityOpsDefinition.Op op = e.getOps().get(i);
			if (op == null || !SPIRIT_TREE_LAST.equals(op.text) || e.getConditionalOps().get(i) == null)
			{
				continue;
			}
			Map<Integer, String> byValue = new java.util.TreeMap<>();
			for (EntityOpsDefinition.ConditionalOp c : e.getConditionalOps().get(i))
			{
				if (c == null || c.varbitID != VARBIT_SPIRIT_TREE_LAST || c.minValue != c.maxValue || !text(c.text))
				{
					return null;
				}
				byValue.put(c.minValue, c.text);
			}
			List<String> out = new ArrayList<>();
			for (int n = 1; byValue.containsKey(n); n++)
			{
				out.add(byValue.get(n));
			}
			return out.size() == byValue.size() && !out.isEmpty() ? out : null;
		}
		return null;
	}

	/**
	 * The house jewellery box's list, from the client script that builds it (interface 590). Each entry is a call
	 * {@code iconst layer; iconst tier; oload text (or sconst text); ...; iconst n; invoke 1688}, its text the last string constant
	 * before it ({@code <str>} marks only strike it through, for a destination not unlocked: still listed). Script
	 * 1688 leaves out an entry whose tier is above the box's, and keys entry {@code n}: "n + 1" for n below 9
	 * ("1".."9"), else enum 1401's value for {@code n - 8} ("A" for 9); the list line is "key: text", its menu option
	 * the text. So each key is fixed: the same in every house. Stops the export when the scripts changed.
	 */
	static List<MenuFacts.ListEntry> jewelleryBox(LoadedCache cache)
	{
		ScriptDefinition box = cache.script(SCRIPT_JEWELLERY_BOX);
		ScriptDefinition entry = cache.script(SCRIPT_JEWELLERY_ENTRY);
		EnumDefinition keys = cache.enumDefinition(ENUM_KEYS);
		if (box == null || entry == null || keys == null || !keysAfterNine(entry))
		{
			throw new IllegalStateException("jewellery box scripts " + SCRIPT_JEWELLERY_BOX + " / " + SCRIPT_JEWELLERY_ENTRY
				+ " or enum " + ENUM_KEYS + " changed: look at them again (MenuFactsLoader.jewelleryBox)");
		}
		Map<Integer, String> letters = new java.util.HashMap<>();
		for (int i = 0; i < keys.getSize(); i++)
		{
			letters.put(keys.getKeys()[i], keys.getStringVals()[i]);
		}
		Instructions names = new Instructions();
		names.init();
		int[] op = box.getInstructions();
		int[] arg = box.getIntOperands();
		String[] str = box.getStringOperands();
		List<MenuFacts.ListEntry> out = new ArrayList<>();
		String text = null;
		for (int i = 0; i < op.length; i++)
		{
			String name = names.findNameFromOpcode(op[i]);
			if ("sconst".equals(name) && str[i] != null && !str[i].matches("</?str>"))
			{
				text = str[i];
			}
			else if ("invoke".equals(name) && arg[i] == SCRIPT_JEWELLERY_ENTRY)
			{
				if (i < 8 || text == null || !"iconst".equals(names.findNameFromOpcode(op[i - 1]))
					|| !"iconst".equals(names.findNameFromOpcode(op[i - 7]))
					|| !List.of("oload", "sconst").contains(names.findNameFromOpcode(op[i - 6])))
				{
					throw new IllegalStateException("script " + SCRIPT_JEWELLERY_BOX + " calls " + SCRIPT_JEWELLERY_ENTRY
						+ " differently at " + i + ": look at it again");
				}
				int n = arg[i - 1];
				String key = n < 9 ? String.valueOf(n + 1) : letters.get(n - 8);
				out.add(new MenuFacts.ListEntry(text, key, arg[i - 7]));
			}
		}
		if (out.isEmpty())
		{
			throw new IllegalStateException("script " + SCRIPT_JEWELLERY_BOX + " has no list entries: look at it again");
		}
		return out;
	}

	/** Script 1688 keys entries 0-8 "1".."9" and from 9 on enum 1401 at n - 8 ({@code iconst 1401; iload; iconst 8; sub; enum}). */
	static boolean keysAfterNine(ScriptDefinition s)
	{
		Instructions names = new Instructions();
		names.init();
		int[] op = s.getInstructions();
		int[] arg = s.getIntOperands();
		boolean nine = false;
		boolean letters = false;
		for (int i = 0; i + 4 < op.length; i++)
		{
			String n = names.findNameFromOpcode(op[i]);
			if ("iconst".equals(n) && arg[i] == 9 && "if_icmplt".equals(names.findNameFromOpcode(op[i + 1])))
			{
				nine = true;
			}
			if ("iconst".equals(n) && arg[i] == ENUM_KEYS && "iconst".equals(names.findNameFromOpcode(op[i + 2])) && arg[i + 2] == 8
				&& "sub".equals(names.findNameFromOpcode(op[i + 3])) && "enum".equals(names.findNameFromOpcode(op[i + 4])))
			{
				letters = true;
			}
		}
		return nine && letters;
	}

	/**
	 * The portal nexus's list texts by the requirement lines' destination (enum 1377: param 660 the text the list
	 * shows; param 679 an alternate's struct, listed under its main one when unlocked: "Grand Exchange" under
	 * "Varrock"). Destination names as the house scan's ({@code HouseObjectsCli.destinationName}).
	 */
	static Map<String, List<String>> nexus(LoadedCache cache)
	{
		EnumDefinition e = cache.enumDefinition(ENUM_NEXUS);
		Map<String, List<String>> out = new LinkedHashMap<>();
		if (e == null)
		{
			return out;
		}
		for (int i = 0; i < e.getSize(); i++)
		{
			StructDefinition s = cache.struct(e.getIntVals()[i]);
			Map<Integer, Object> p = s == null ? null : s.getParams();
			if (p == null || p.get(PARAM_NEXUS_MAIN) != null || !(p.get(PARAM_NEXUS_NAME) instanceof String))
			{
				continue; // an alternate: listed with its main one
			}
			String text = (String) p.get(PARAM_NEXUS_NAME);
			String ours = com.bestpath.tool.extract.HouseObjectsCli.destinationName(text);
			if (ours == null)
			{
				continue;
			}
			List<String> texts = new ArrayList<>();
			texts.add(text);
			Object alt = p.get(PARAM_NEXUS_ALTERNATE);
			StructDefinition a = alt instanceof Integer ? cache.struct((Integer) alt) : null;
			if (a != null && a.getParams() != null && a.getParams().get(PARAM_NEXUS_NAME) instanceof String)
			{
				texts.add((String) a.getParams().get(PARAM_NEXUS_NAME));
			}
			out.put(ours, texts);
		}
		return out;
	}

	/** True when any option of any form is transport-like ({@link OptionPicker#kindOf}). */
	static boolean transportLike(MenuFacts.Thing t)
	{
		for (String op : t.ops())
		{
			if (OptionPicker.kindOf(op) != null)
			{
				return true;
			}
		}
		for (String op : t.conditionalOps())
		{
			if (OptionPicker.kindOf(op) != null)
			{
				return true;
			}
		}
		return false;
	}

	private static MenuFacts.Thing object(LoadedCache cache, ObjectDefinition def)
	{
		List<MenuFacts.Form> forms = new ArrayList<>();
		forms.add(form(def.getId(), def.getName(), def.getOps()));
		int[] dest = def.getConfigChangeDest();
		if (dest != null)
		{
			for (int d : dest)
			{
				ObjectDefinition o = d >= 0 ? cache.object(d) : null;
				if (o != null)
				{
					forms.add(form(o.getId(), o.getName(), o.getOps()));
				}
			}
		}
		return new MenuFacts.Thing(def.getId(), forms);
	}

	static MenuFacts.Form form(int id, String name, EntityOpsDefinition e)
	{
		List<String> ops = new ArrayList<>();
		List<String> cond = new ArrayList<>();
		Map<String, List<String>> subs = new LinkedHashMap<>();
		if (e != null)
		{
			List<EntityOpsDefinition.Op> plain = e.getOps();
			for (int i = 0; plain != null && i < plain.size(); i++)
			{
				EntityOpsDefinition.Op op = plain.get(i);
				if (op == null || !text(op.text))
				{
					continue;
				}
				ops.add(op.text);
				List<List<EntityOpsDefinition.SubOp>> all = e.getSubOps();
				List<EntityOpsDefinition.SubOp> s = all != null && i < all.size() ? all.get(i) : null;
				if (s != null)
				{
					List<String> texts = new ArrayList<>();
					for (EntityOpsDefinition.SubOp so : s)
					{
						if (so != null && text(so.text))
						{
							texts.add(so.text);
						}
					}
					if (!texts.isEmpty())
					{
						subs.put(op.text, texts);
					}
				}
			}
			if (e.getConditionalOps() != null)
			{
				for (List<EntityOpsDefinition.ConditionalOp> l : e.getConditionalOps())
				{
					if (l != null)
					{
						for (EntityOpsDefinition.ConditionalOp c : l)
						{
							if (c != null && text(c.text))
							{
								cond.add(c.text);
							}
						}
					}
				}
			}
		}
		return new MenuFacts.Form(id, name, ops, cond, subs);
	}

	private static boolean text(String s)
	{
		return s != null && !s.isEmpty() && !"null".equals(s) && !"hidden".equalsIgnoreCase(s);
	}

	static MenuFacts.Item item(ItemDefinition d)
	{
		String[] inv = new String[5];
		List<List<String>> invSubs = new ArrayList<>();
		String[] io = d.getInterfaceOptions();
		String[][] subops = d.getSubops();
		for (int i = 0; i < 5; i++)
		{
			inv[i] = io != null && i < io.length && text(io[i]) ? io[i] : null;
			List<String> s = new ArrayList<>();
			if (subops != null && i < subops.length && subops[i] != null)
			{
				for (String t : subops[i])
				{
					if (text(t))
					{
						s.add(t);
					}
				}
			}
			invSubs.add(s.isEmpty() ? null : s);
		}
		String[] worn = new String[8];
		List<List<String>> wornSubs = new ArrayList<>();
		Map<Integer, Object> params = d.getParams();
		for (int n = 1; n <= 8; n++)
		{
			Object w = params == null ? null : params.get(PARAM_WORN_OP1 + n - 1);
			worn[n - 1] = w instanceof String && text((String) w) ? (String) w : null;
			List<String> s = new ArrayList<>();
			for (int m = 1; params != null && m <= WORN_SUBOPS; m++)
			{
				Object v = params.get(wornSubOpParam(n, m));
				if (v instanceof String && text((String) v))
				{
					s.add((String) v);
				}
			}
			wornSubs.add(s.isEmpty() ? null : s);
		}
		return new MenuFacts.Item(d.getId(), d.getName(), inv, invSubs, worn, wornSubs);
	}

	/** The options of a spell's spellbook button; "*" (set at run time by client script 2615) is "Cast"; none = "Cast". */
	static List<String> spellOps(InterfaceDefinition[][] ifs, int component)
	{
		int group = component >>> 16;
		int child = component & 0xFFFF;
		List<String> ops = new ArrayList<>();
		if (group < ifs.length && ifs[group] != null && child < ifs[group].length && ifs[group][child] != null)
		{
			String[] actions = ifs[group][child].getActions();
			if (actions != null)
			{
				for (String a : actions)
				{
					if (a != null && !a.isEmpty())
					{
						ops.add("*".equals(a) ? "Cast" : a);
					}
				}
			}
		}
		if (!ops.contains("Cast"))
		{
			ops.add(0, "Cast");
		}
		return ops;
	}

	/** One string column of a DB table, every row, each once; {@code noSpaces}: "C K Q" -> "CKQ". */
	private static List<String> column(LoadedCache cache, int table, int col, boolean noSpaces) throws IOException
	{
		Set<String> out = new LinkedHashSet<>();
		for (Object[][] row : cache.dbRows(table))
		{
			if (row.length > col && row[col] != null && row[col].length > 0 && row[col][0] instanceof String)
			{
				String s = (String) row[col][0];
				out.add(noSpaces ? s.replace(" ", "") : s);
			}
		}
		return new ArrayList<>(out);
	}
}
