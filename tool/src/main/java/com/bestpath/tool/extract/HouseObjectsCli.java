package com.bestpath.tool.extract;

import com.bestpath.tool.cache.CacheInfo;
import com.bestpath.tool.cache.LoadedCache;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.runelite.cache.ConfigType;
import net.runelite.cache.IndexType;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.EnumDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.StructDefinition;
import net.runelite.cache.definitions.loaders.EnumLoader;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.definitions.loaders.StructLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.script.Opcodes;

/**
 * Developer aid for the plugin's house scan: which game objects are the built house things the requirement lines
 * name ({@code house has <thing>}, data/manual/house-things.tsv), read from the game cache. Writes {@code src/main/resources/com/bestpath/player/house-objects.tsv}:
 * <pre>
 * gradlew -p tool houseObjects --args="&lt;cacheDir&gt; [out.tsv] [house-things.tsv]"
 * </pre>
 *
 * <p>What the cache says (checked in cache #2735):
 * <ul>
 * <li>Built furniture has a "Remove" option (decorated Christmas copies "Remove-decorations"); the mounted capes
 * of the cape hanger have "Take". A thing is recognised by its name and options ({@link #thing}).</li>
 * <li>Some furniture is placed as a multi-state object (no name; a varbit picks the state): the jewellery boxes
 * 29154-29156 (varbit 2308, the left-click teleport), the toggling portals (Varrock / Grand Exchange ...). RuneLite
 * reports the placed id, so a parent whose every state is the same thing is that thing too.</li>
 * <li>The portal chamber's and portal nexus's destinations: enum 1377 (key -> struct; param 660 the destination's
 * name, param 680 on an alternate such as "Grand Exchange" = the main destination's struct). The nexus keeps one
 * destination key per slot in varbits POH_NEXUS_TELE_1..45; the game's script 2671 ("is this destination in my
 * nexus") reads exactly those 45 varbits, which this tool checks.</li>
 * </ul>
 */
public final class HouseObjectsCli
{
	/** Portal destinations: key -> struct (cache enum 1377, read by the nexus scripts 2273 / 2671). */
	static final int DESTINATION_ENUM = 1377;
	static final int PARAM_NAME = 660;
	/** On an alternate destination (Grand Exchange, Seers' Village, Yanille, Last Boat): the main one's struct. */
	static final int PARAM_MAIN = 680;
	/** On a main destination (Varrock): its alternate's struct (Grand Exchange), listed under it when unlocked. */
	static final int PARAM_ALTERNATE = 679;
	/** The skill (6 = Magic) and level a destination needs (script 2664 checks the base level). */
	static final int PARAM_SKILL = 672;
	static final int PARAM_LEVEL = 673;
	static final int SKILL_MAGIC = 6;
	/** Says whether a nexus destination is unlocked (returns 1 and its requirement text); the list asks it for alternates. */
	static final int NEXUS_UNLOCK_SCRIPT = 2664;
	/** Builds the nexus's Teleport Menu (interface 17): one line per slot, an unlocked alternate under its main one. */
	static final int NEXUS_LIST_SCRIPT = 2674;
	/** POH_NEXUS_ID: which nexus is built (the list shows more slots for a better one). */
	static final int VARBIT_NEXUS_ID = 6670;
	/** The game's "is destination X in the player's nexus" script: reads the 45 slot varbits. */
	static final int NEXUS_SLOT_SCRIPT = 2671;
	/** POH_NEXUS_TELE_1..45 in slot order (RuneLite gameval VarbitID); checked against script 2671. */
	static final int[] NEXUS_SLOT_VARBITS = slots();

	/**
	 * The destination's name in the cache (enum 1377, param 660) -> the destination name the requirement lines use
	 * (data/manual/house-things.tsv 'destination' rows: the Wiki's Portal nexus list). Matched by hand; where the
	 * names differ the cache's own data backs the match: Marim needs a banana (struct 460: law, fire, water, banana =
	 * the standard Ape Atoll Teleport), Ape Atoll Dungeon soul + blood + law runes (the Arceuus one), Lunar Isle astral
	 * runes (Moonclan Teleport), Troll Stronghold and Weiss use stony / icy basalt (param 2549 = items 22601 "Stony
	 * basalt" and 22599 "Icy basalt"). Civitas illa Fortis has no row in house-things.tsv yet.
	 */
	static final Map<String, String> DESTINATIONS = new LinkedHashMap<>();

	/** Portal object names ("X Portal") whose X differs from the enum's name. */
	static final Map<String, String> PORTAL_ALIASES = Map.of(
		"Fenkenstrain's Castle", "Fenken' Castle",
		"Kourend", "Kourend Castle",
		"Yanille Watchtower", "Watchtower");

	static
	{
		String[][] d = {
			{"Varrock", "Varrock Teleport"}, {"Lumbridge", "Lumbridge Teleport"}, {"Falador", "Falador Teleport"},
			{"Camelot", "Camelot Teleport"}, {"Ardougne", "Ardougne Teleport"}, {"Watchtower", "Watchtower Teleport"},
			{"Senntisten", "Senntisten Teleport"}, {"Marim", "Ape Atoll Teleport (standard)"},
			{"Kharyrll", "Kharyrll Teleport"}, {"Lunar Isle", "Moonclan Teleport"},
			{"Kourend Castle", "Kourend Castle Teleport"}, {"Civitas illa Fortis", "Civitas illa Fortis Teleport"},
			{"Waterbirth Island", "Waterbirth Teleport"}, {"Fishing Guild", "Fishing Guild Teleport"},
			{"Annakarl", "Annakarl Teleport"}, {"Troll Stronghold", "Stony basalt"}, {"Catherby", "Catherby Teleport"},
			{"Ghorrock", "Ghorrock Teleport"}, {"Carrallanger", "Carrallanger Teleport"}, {"Weiss", "Icy basalt"},
			{"Arceuus Library", "Arceuus Library Teleport"}, {"Draynor Manor", "Draynor Manor Teleport"},
			{"Battlefront", "Battlefront Teleport"}, {"Mind Altar", "Mind Altar Teleport"},
			{"Salve Graveyard", "Salve Graveyard Teleport"}, {"Fenken' Castle", "Fenkenstrain's Castle Teleport"},
			{"West Ardougne", "West Ardougne Teleport"}, {"Harmony Island", "Harmony Island Teleport"},
			{"Cemetery", "Cemetery Teleport"}, {"Barrows", "Barrows Teleport"},
			{"Ape Atoll Dungeon", "Ape Atoll Teleport (Arceuus)"}, {"Trollheim", "Trollheim Teleport"},
			{"Paddewwa", "Paddewwa Teleport"}, {"Lassar", "Lassar Teleport"}, {"Dareeyak", "Dareeyak Teleport"},
			{"Ourania", "Ourania Teleport"}, {"Barbarian Outpost", "Barbarian Teleport"},
			{"Port Khazard", "Khazard Teleport"}, {"Ice Plateau", "Ice Plateau Teleport"},
			{"Respawn", "Respawn Teleport"}, {"Boat", "Teleport to Boat"},
		};
		for (String[] p : d)
		{
			DESTINATIONS.put(p[0], p[1]);
		}
	}

	// What one run collects: built furniture, the destinations and the nexus's list, and the notes.
	private final Map<Integer, String> objectThings = new TreeMap<>();
	private final Map<Integer, String> objectNames = new HashMap<>();
	private final Map<Integer, String> destinationByKey = new TreeMap<>();
	private final Map<String, Integer> keyByCacheName = new HashMap<>();
	private final Map<Integer, String> nexusText = new TreeMap<>();
	private final Map<Integer, Integer> nexusAlternate = new TreeMap<>();
	private final Map<Integer, String> nexusUnlock = new TreeMap<>();
	private final Map<String, Integer> nexusSlots = new LinkedHashMap<>();
	private final Map<String, String> chamber = new TreeMap<>();
	private final List<String> problems = new ArrayList<>();

	private HouseObjectsCli()
	{
	}

	/** The requirement lines' name of a nexus destination the cache names so ("Lunar Isle" -> "Moonclan Teleport"), or null. */
	public static String destinationName(String cacheName)
	{
		return DESTINATIONS.get(cacheName);
	}

	private static int[] slots()
	{
		int[] s = new int[45];
		for (int i = 0; i < 15; i++)
		{
			s[i] = 6654 + i; // POH_NEXUS_TELE_1..15
		}
		for (int i = 0; i < 3; i++)
		{
			s[15 + i] = 6554 + i; // _16..18
		}
		for (int i = 0; i < 12; i++)
		{
			s[18 + i] = 10080 + i; // _19..30
		}
		for (int i = 0; i < 5; i++)
		{
			s[30 + i] = 10019 + i; // _31..35
		}
		for (int i = 0; i < 10; i++)
		{
			s[35 + i] = 20111 + i; // _36..45
		}
		return s;
	}

	/**
	 * The house thing a built piece of furniture is, from its cache name and options, or null. Portal-chamber
	 * portals give {@code "portal:<X>"} (X = the name without " Portal"; the destination is matched afterwards);
	 * the portal nexus gives {@code "portal nexus"} (its destinations are in varbits).
	 */
	static String thing(String name, List<String> ops)
	{
		if (name == null)
		{
			return null;
		}
		boolean remove = ops.contains("Remove") || ops.contains("Remove-decorations");
		switch (name)
		{
			case "Basic Jewellery Box":
			case "Fancy Jewellery Box":
			case "Ornate Jewellery Box":
				return remove && ops.contains("Teleport Menu") ? name.toLowerCase(Locale.ROOT) : null;
			case "Amulet of Glory":
				return remove && ops.contains("Edgeville") ? "mounted glory" : null;
			case "Mythical cape": // the quest hall's trophy space
			case "Mounted Mythical Cape": // the cape hanger (Take, like every mounted cape there)
				return ops.contains("Teleport") && (remove || ops.contains("Take")) ? "mounted mythical cape" : null;
			case "Digsite Pendant":
				return remove && ops.contains("Teleport menu") ? "mounted digsite pendant" : null;
			case "Xeric's Talisman":
				return remove && ops.contains("Teleport menu") ? "mounted xeric's talisman" : null;
			case "Portal Nexus":
				return remove && ops.contains("Teleport Menu") ? "portal nexus" : null;
			case "Obelisk":
				return remove && ops.contains("Teleport to destination") ? "obelisk" : null;
			case "Spirit Tree":
			case "Christmas-Spirit Tree":
				return remove && ops.contains("Travel") ? "spirit tree" : null;
			case "Fairy Ring":
				return remove && ops.contains("Configure") ? "fairy ring" : null;
			case "Spiritual Fairy Tree":
				return remove && ops.contains("Ring-configure") ? "spirit tree & fairy ring" : null;
			case "Ancient Altar":
			case "Lunar Altar":
			case "Dark Altar":
				return remove && ops.contains("Venerate") ? name.toLowerCase(Locale.ROOT) : null;
			case "Altar of the Occult":
				return remove && ops.contains("Venerate") ? "occult altar" : null;
			default:
				break;
		}
		if (name.endsWith(" Portal") && remove && (ops.contains("Enter") || ops.contains("Toggle")))
		{
			return "portal:" + name.substring(0, name.length() - " Portal".length());
		}
		return null;
	}

	public static void main(String[] args) throws Exception
	{
		File folder = LoadedCache.findCacheFolder(new File(args[0]));
		if (folder == null)
		{
			throw new IllegalArgumentException("no cache (main_file_cache.dat2) in " + args[0]);
		}
		File out = new File(args.length > 1 ? args[1] : "src/main/resources/com/bestpath/player/house-objects.tsv");
		File thingsFile = new File(args.length > 2 ? args[2] : "data/manual/house-things.tsv");
		String thingsName = thingsFile.getPath().replace(File.separatorChar, '/');
		CacheInfo info = CacheInfo.find(folder);
		String cacheName = info == null ? "cache ?" : "cache " + info.getOpenrs2Id();
		HouseObjectsCli scan = new HouseObjectsCli();
		try (Store store = new Store(folder))
		{
			store.load();
			scan.readDestinations(store);
			checkSlotScript(store, scan.problems);
			scan.nexusSlots.putAll(listedSlots(store, scan.problems));
			scan.readFurniture(store);
		}
		scan.checkNames(thingsFile, thingsName);
		write(out, cacheName, scan.objectThings, scan.objectNames, scan.destinationByKey, scan.problems);
		writeLists(out, scan.nexusText, scan.nexusAlternate, scan.nexusUnlock, scan.nexusSlots, scan.chamber);
		System.out.printf(Locale.ROOT, "%d objects, %d destinations -> %s%n", scan.objectThings.size(), scan.destinationByKey.size(), out);
		for (String p : scan.problems)
		{
			System.out.println("note: " + p);
		}
	}

	/** Reads the portal destinations (enum 1377 -> structs) and the nexus's list entries. */
	private void readDestinations(Store store) throws IOException
	{
		Map<Integer, byte[]> enums = configFiles(store, ConfigType.ENUM);
		Map<Integer, byte[]> structs = configFiles(store, ConfigType.STRUCT);
		EnumDefinition e = new EnumLoader().load(DESTINATION_ENUM, enums.get(DESTINATION_ENUM));
		Map<Integer, Integer> keyByStruct = new HashMap<>();
		Map<Integer, StructDefinition> structByKey = new LinkedHashMap<>();
		for (int k = 0; k < e.getSize(); k++)
		{
			int s = e.getIntVals()[k];
			keyByStruct.put(s, e.getKeys()[k]);
			structByKey.put(e.getKeys()[k], new StructLoader().load(s, structs.get(s)));
		}
		for (Map.Entry<Integer, StructDefinition> en : structByKey.entrySet())
		{
			addDestination(store, en.getKey(), en.getValue(), keyByStruct);
		}
	}

	/** One destination entry: its nexus text, its alternate or unlock, and (for a main one) the requirement name. */
	private void addDestination(Store store, int entryKey, StructDefinition struct, Map<Integer, Integer> keyByStruct)
		throws IOException
	{
		Map<Integer, Object> p = struct.getParams();
		String cache = String.valueOf(p.get(PARAM_NAME));
		Object main = p.get(PARAM_MAIN);
		int key = main == null ? entryKey : keyByStruct.getOrDefault(((Number) main).intValue(), -1);
		keyByCacheName.put(cache, key);
		// the nexus's list (for the keys in the step text): every entry's text, a main one's alternate, an alternate's unlock
		nexusText.put(entryKey, cache);
		Object alt = p.get(PARAM_ALTERNATE);
		if (main == null && alt instanceof Number && keyByStruct.containsKey(((Number) alt).intValue()))
		{
			nexusAlternate.put(entryKey, keyByStruct.get(((Number) alt).intValue()));
		}
		if (main != null)
		{
			addAlternateUnlock(store, entryKey, struct, cache);
			return; // an alternate (Grand Exchange...): its portal is the main destination's
		}
		String ours = DESTINATIONS.get(cache);
		if (ours == null)
		{
			problems.add("destination " + entryKey + " '" + cache + "' has no name in DESTINATIONS (left out)");
			return;
		}
		destinationByKey.put(entryKey, ours);
	}

	/** An alternate entry's unlock: its Magic level and the varbit / varp tests of script 2664. */
	private void addAlternateUnlock(Store store, int entryKey, StructDefinition struct, String cache) throws IOException
	{
		Map<Integer, Object> p = struct.getParams();
		Object skill = p.get(PARAM_SKILL);
		Object level = p.get(PARAM_LEVEL);
		int magic = skill instanceof Number && ((Number) skill).intValue() == SKILL_MAGIC && level instanceof Number
			? ((Number) level).intValue() : 1;
		String unlock = alternateUnlock(store, struct.getId());
		if (unlock == null)
		{
			problems.add("alternate '" + cache + "' (struct " + struct.getId() + "): its unlock is not in script "
				+ NEXUS_UNLOCK_SCRIPT + " (keys after it not known)");
		}
		else
		{
			nexusUnlock.put(entryKey, magic + "\t" + unlock);
		}
	}

	/** Reads every object, keeps the house things, then the multi-state parents of them. */
	private void readFurniture(Store store) throws IOException
	{
		ObjectManager om = new ObjectManager(store);
		om.load();
		Map<Integer, ObjectDefinition> defs = new HashMap<>();
		for (ObjectDefinition o : om.getObjects())
		{
			defs.put(o.getId(), o);
			addFurniture(o);
		}
		addMultiStateParents(defs.values());
	}

	/** Records the object if it is a house thing; a portal-chamber portal is matched to its destination first. */
	private void addFurniture(ObjectDefinition o)
	{
		List<String> ops = Arrays.asList(options(o).split(","));
		String t = thing(o.getName(), ops);
		if (t == null)
		{
			return;
		}
		objectNames.put(o.getId(), o.getName());
		if (t.startsWith("portal:"))
		{
			t = portalChamberThing(o, t.substring("portal:".length()), ops);
			if (t == null)
			{
				return;
			}
		}
		objectThings.put(o.getId(), t);
	}

	/** "portal chamber: <destination>" for a chamber portal named "<x> Portal", or null (noted) when none matches. */
	private String portalChamberThing(ObjectDefinition o, String x, List<String> ops)
	{
		Integer key = keyByCacheName.get(PORTAL_ALIASES.getOrDefault(x, x));
		String dest = key == null ? null : destinationByKey.get(key);
		if (dest == null)
		{
			problems.add("object " + o.getId() + " '" + o.getName() + "': no destination matches (left out)");
			return null;
		}
		addChamberOptions(o, PORTAL_ALIASES.getOrDefault(x, x), ops);
		return "portal chamber: " + dest;
	}

	/**
	 * What the chamber's portal is clicked by, by the nexus's text of the same place: "Enter", or on a portal that
	 * toggles (Varrock / Grand Exchange) the option naming each of its two places, the portal named as its first
	 * object (its name follows the toggle).
	 */
	private void addChamberOptions(ObjectDefinition o, String text, List<String> ops)
	{
		boolean named = false;
		for (String op : ops)
		{
			if (!op.equals("Enter") && keyByCacheName.containsKey(op))
			{
				chamber.putIfAbsent(op, op + "\t" + o.getName());
				named = true;
			}
		}
		if (ops.contains("Enter"))
		{
			chamber.putIfAbsent(text, "Enter\t" + o.getName());
		}
		else if (!named)
		{
			problems.add("object " + o.getId() + " '" + o.getName() + "' has no 'Enter' (" + ops + ")");
		}
	}

	/** Multi-state parents whose every state is the same thing are that thing too. */
	private void addMultiStateParents(Collection<ObjectDefinition> defs)
	{
		for (ObjectDefinition o : defs)
		{
			int[] states = o.getConfigChangeDest();
			if (states == null || objectThings.containsKey(o.getId()))
			{
				continue;
			}
			String same = sameThing(states);
			if (same != null)
			{
				objectThings.put(o.getId(), same);
				objectNames.put(o.getId(), "(states of " + objectNames.get(firstState(states)) + ")");
			}
		}
	}

	/** The thing every state (ignoring -1) is, or null when a state is none or they differ. */
	private String sameThing(int[] states)
	{
		String same = null;
		for (int s : states)
		{
			if (s < 0)
			{
				continue;
			}
			String t = objectThings.get(s);
			if (t == null || same != null && !same.equals(t))
			{
				return null;
			}
			same = t;
		}
		return same;
	}

	/** Every name must be one the requirement lines use: notes each mismatch with house-things.tsv. */
	private void checkNames(File thingsFile, String thingsName) throws IOException
	{
		TreeSet<String> things = new TreeSet<>();
		TreeSet<String> destinations = new TreeSet<>();
		for (String line : Files.readAllLines(thingsFile.toPath(), StandardCharsets.UTF_8))
		{
			String[] c = line.split("\t");
			if (c.length >= 2 && c[0].equals("thing"))
			{
				things.add(c[1]);
			}
			else if (c.length >= 2 && c[0].equals("destination"))
			{
				destinations.add(c[1]);
			}
		}
		TreeSet<String> foundThings = new TreeSet<>(objectThings.values());
		checkThings(things, foundThings, thingsName);
		checkDestinations(destinations, foundThings, thingsName);
		for (String text : nexusText.values())
		{
			if (!chamber.containsKey(text) && !text.startsWith("Last "))
			{
				problems.add("nexus entry '" + text + "' has no portal chamber portal of that name");
			}
		}
	}

	private void checkThings(TreeSet<String> things, TreeSet<String> foundThings, String thingsName)
	{
		for (String t : foundThings)
		{
			if (!t.startsWith("portal chamber: ") && !t.equals("portal nexus") && !things.contains(t))
			{
				problems.add("thing '" + t + "' is not in " + thingsName + " (kept: what is built)");
			}
		}
		for (String t : things)
		{
			if (!foundThings.contains(t))
			{
				problems.add("house-things.tsv thing '" + t + "' has no object (stays unknown = not built)");
			}
		}
	}

	private void checkDestinations(TreeSet<String> destinations, TreeSet<String> foundThings, String thingsName)
	{
		for (String d : destinations)
		{
			if (!destinationByKey.containsValue(d))
			{
				problems.add("house-things.tsv destination '" + d + "' is not a nexus destination in the cache");
			}
			else if (!foundThings.contains("portal chamber: " + d))
			{
				problems.add("destination '" + d + "' has no portal chamber portal in the cache (nexus only)");
			}
		}
		for (String d : destinationByKey.values())
		{
			if (!destinations.contains(d))
			{
				problems.add("nexus destination '" + d + "' has no 'destination' row in " + thingsName);
			}
		}
	}

	private static int firstState(int[] states)
	{
		for (int s : states)
		{
			if (s >= 0)
			{
				return s;
			}
		}
		return -1;
	}

	/** Checks that the game's slot script reads exactly {@link #NEXUS_SLOT_VARBITS}, in that order. */
	private static void checkSlotScript(Store store, List<String> problems) throws IOException
	{
		Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.CLIENTSCRIPT);
		Archive a = index.getArchive(NEXUS_SLOT_SCRIPT);
		byte[] data = a == null ? null : a.decompress(storage.loadArchive(a));
		if (data == null)
		{
			problems.add("script " + NEXUS_SLOT_SCRIPT + " not found: nexus slot varbits NOT checked");
			return;
		}
		ScriptDefinition s = new ScriptLoader().load(NEXUS_SLOT_SCRIPT, data);
		List<Integer> read = new ArrayList<>();
		for (int k = 0; k < s.getInstructions().length; k++)
		{
			if (s.getInstructions()[k] == Opcodes.GET_VARBIT)
			{
				read.add(s.getIntOperands()[k]);
			}
		}
		List<Integer> want = new ArrayList<>();
		for (int v : NEXUS_SLOT_VARBITS)
		{
			want.add(v);
		}
		if (!read.equals(want))
		{
			problems.add("script " + NEXUS_SLOT_SCRIPT + " reads " + read + ", not POH_NEXUS_TELE_1..45: CHECK the plugin's slots");
		}
		else
		{
			System.out.println("script " + NEXUS_SLOT_SCRIPT + " reads POH_NEXUS_TELE_1..45 in order: ok");
		}
	}

	private static ScriptDefinition script(Store store, int id) throws IOException
	{
		Index index = store.getIndex(IndexType.CLIENTSCRIPT);
		Archive a = index.getArchive(id);
		byte[] data = a == null ? null : a.decompress(store.getStorage().loadArchive(a));
		return data == null ? null : new ScriptLoader().configureForRevision(index.getRevision()).load(id, data);
	}

	/**
	 * What unlocks an alternate nexus entry besides its Magic level, as script {@value #NEXUS_UNLOCK_SCRIPT} checks it:
	 * the script switches on the struct id; from that case to its first text the tests are {@code get_varbit V;
	 * iconst C; if_icmpeq} ("varbit V = C") or {@code get_varp V; iconst C; if_icmpge} ("varp V >= C"), any one
	 * enough (cache 2735: Grand Exchange the Varrock diary's medium, hard or elite reward, varbits 4520-4522). Null
	 * when the script has no case for it.
	 */
	static String alternateUnlock(Store store, int structId) throws IOException
	{
		ScriptDefinition s = script(store, NEXUS_UNLOCK_SCRIPT);
		if (s == null || s.getSwitches() == null)
		{
			return null;
		}
		int[] op = s.getInstructions();
		int[] arg = s.getIntOperands();
		String[] str = s.getStringOperands();
		for (int i = 0; i < op.length; i++)
		{
			if (op[i] != Opcodes.SWITCH)
			{
				continue;
			}
			Integer offset = s.getSwitches()[arg[i]].get(structId);
			if (offset == null)
			{
				continue;
			}
			List<String> tests = new ArrayList<>();
			for (int k = i + 1 + offset; k + 2 < op.length && (str == null || str[k] == null); k++)
			{
				boolean varbit = op[k] == Opcodes.GET_VARBIT;
				boolean varp = op[k] == Opcodes.GET_VARP;
				if ((varbit || varp) && op[k + 1] == Opcodes.ICONST)
				{
					if (varbit && op[k + 2] == Opcodes.IF_ICMPEQ)
					{
						tests.add("varbit " + arg[k] + " = " + arg[k + 1]);
					}
					else if (varp && op[k + 2] == Opcodes.IF_ICMPGE)
					{
						tests.add("varp " + arg[k] + " >= " + arg[k + 1]);
					}
					else
					{
						return null; // a test this reader does not know
					}
				}
			}
			return tests.isEmpty() ? null : String.join(" | ", tests);
		}
		return null;
	}

	/**
	 * How many slots the nexus's Teleport Menu lists, by POH_NEXUS_ID, from script {@value #NEXUS_LIST_SCRIPT}: a
	 * default ({@code iconst N; istore} right before the first {@code get_varbit 6670}), then {@code get_varbit 6670;
	 * iconst X; if_icmpeq; jump; iconst N} for each other nexus (cache 2735: 4, 8 for 2, 41 for 3). Key "other" is the
	 * default.
	 */
	static Map<String, Integer> listedSlots(Store store, List<String> problems) throws IOException
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		ScriptDefinition s = script(store, NEXUS_LIST_SCRIPT);
		if (s != null)
		{
			int[] op = s.getInstructions();
			int[] arg = s.getIntOperands();
			for (int i = 0; i + 4 < op.length; i++)
			{
				if (op[i] != Opcodes.GET_VARBIT || arg[i] != VARBIT_NEXUS_ID)
				{
					continue;
				}
				if (out.isEmpty() && i >= 2 && op[i - 2] == Opcodes.ICONST && op[i - 1] == Opcodes.ISTORE)
				{
					out.put("other", arg[i - 2]);
				}
				if (op[i + 1] == Opcodes.ICONST && op[i + 2] == Opcodes.IF_ICMPEQ && op[i + 3] == Opcodes.JUMP && op[i + 4] == Opcodes.ICONST)
				{
					out.put(String.valueOf(arg[i + 1]), arg[i + 4]);
				}
			}
		}
		if (!out.containsKey("other") || out.size() < 2)
		{
			problems.add("script " + NEXUS_LIST_SCRIPT + ": how many slots the nexus lists is not found (keys not known)");
			out.clear();
		}
		return out;
	}

	/** The rows for the step texts: the nexus's list and the portal chamber's portals. */
	private static void writeLists(File out, Map<Integer, String> text, Map<Integer, Integer> alternate, Map<Integer, String> unlock,
		Map<String, Integer> slots, Map<String, String> chamber) throws IOException
	{
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8,
			java.nio.file.StandardOpenOption.APPEND)))
		{
			w.print("# The portal nexus's Teleport Menu (interface 17, script 2674), for the step text and its key.\n");
			w.print("# Slot by slot, a line per destination keyed 1-9, then A-Z, and an alternate's line\n");
			w.print("# right under its main one when unlocked (script 2664); empty slots get no line, so the keys follow the\n");
			w.print("# player's own list. The menu reads the *_TEMP copies of the slot varbits; the plugin reads the saved\n");
			w.print("# slots (POH_NEXUS_TELE_1..45), taken to be what the menu copies (not seen in the game yet). Rows:\n");
			w.print("#   nexus <slot value> <text> <alternate's slot value, -1 none>\n");
			w.print("#   nexus-alternate <slot value> <text> <base Magic level> <unlock: any of>\n");
			w.print("#   nexus-slots <POH_NEXUS_ID value, other> <slots listed>\n");
			w.print("#   chamber <nexus text> <option> <the portal chamber portal's name>\n");
			for (Map.Entry<Integer, String> en : text.entrySet())
			{
				if (unlock.containsKey(en.getKey()))
				{
					w.print("nexus-alternate\t" + en.getKey() + "\t" + en.getValue() + "\t" + unlock.get(en.getKey()) + "\n");
				}
				else if (!alternate.containsValue(en.getKey()))
				{
					w.print("nexus\t" + en.getKey() + "\t" + en.getValue() + "\t" + alternate.getOrDefault(en.getKey(), -1) + "\n");
				}
			}
			for (Map.Entry<String, Integer> en : slots.entrySet())
			{
				w.print("nexus-slots\t" + en.getKey() + "\t" + en.getValue() + "\n");
			}
			for (Map.Entry<String, String> en : chamber.entrySet())
			{
				w.print("chamber\t" + en.getKey() + "\t" + en.getValue() + "\n");
			}
		}
	}

	private static void write(File out, String cacheName, Map<Integer, String> objectThings, Map<Integer, String> names,
		Map<Integer, String> destinations, List<String> problems) throws IOException
	{
		File dir = out.getAbsoluteFile().getParentFile();
		if (dir != null)
		{
			dir.mkdirs();
		}
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8)))
		{
			w.print("# House furniture -> the house thing it is ('house has <thing>' in requirement lines), for the plugin's\n");
			w.print("# house scan. GENERATED by `gradlew -p tool houseObjects` (tool HouseObjectsCli) from the game " + cacheName + ";\n");
			w.print("# do not edit by hand. Names: data/manual/house-things.tsv. Rows:\n");
			w.print("#   object <id> <thing> <cache name>   (thing 'portal nexus': its destinations are in the slot varbits)\n");
			w.print("#   destination <key> <destination>    (portal nexus slot value -> destination; cache enum 1377)\n");
			for (String p : problems)
			{
				w.print("# note: " + p + "\n");
			}
			for (Map.Entry<Integer, String> en : objectThings.entrySet())
			{
				w.print("object\t" + en.getKey() + "\t" + en.getValue() + "\t" + names.get(en.getKey()) + "\n");
			}
			for (Map.Entry<Integer, String> en : destinations.entrySet())
			{
				w.print("destination\t" + en.getKey() + "\t" + en.getValue() + "\n");
			}
		}
	}

	/** The files of one config archive, by file id. */
	private static Map<Integer, byte[]> configFiles(Store store, ConfigType type) throws IOException
	{
		Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.CONFIGS);
		Archive archive = index.getArchive(type.getId());
		ArchiveFiles files = archive.getFiles(storage.loadArchive(archive));
		Map<Integer, byte[]> map = new HashMap<>();
		for (FSFile f : files.getFiles())
		{
			map.put(f.getFileId(), f.getContents());
		}
		return map;
	}

	/** The object's menu options, comma-separated ("Open,Knock-at"). */
	private static String options(ObjectDefinition def)
	{
		List<String> list = new ArrayList<>();
		net.runelite.cache.EntityOpsDefinition ops = def.getOps();
		if (ops != null)
		{
			for (net.runelite.cache.EntityOpsDefinition.Op op : ops.ops)
			{
				if (op != null && op.text != null)
				{
					list.add(op.text);
				}
			}
		}
		return String.join(",", list);
	}
}
