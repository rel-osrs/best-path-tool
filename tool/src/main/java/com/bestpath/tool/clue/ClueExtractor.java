package com.bestpath.tool.clue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the cache's clue-helper DB tables (see {@link ClueTables}, docs/CLUES.md)
 * into {@link ClueStep}s: one per row of the clue tables 4-14, with the target
 * row resolved to a tile, the STASH unit and key-drop places, and the step's
 * requirements in words. Nothing is guessed: a value the cache does not give
 * is left out and noted in the step's checks.
 *
 * <p>Column numbers per table come from RuneLite's public gameval names
 * ({@code DBTableID.Cluehelper*}); the cache stores numbers only.
 */
public final class ClueExtractor
{
	/** What the extractor reads; the real cache in {@link ClueExtractCli}, small fixtures in the tests. */
	public interface Source
	{
		/** Column values of every row of a table, by row id (each column an array, or null). */
		Map<Integer, Object[][]> rows(int table);

		/** Any DB row by id (also outside the clue tables, e.g. a music track), or null. */
		Object[][] row(int rowId);

		/** Clue step row id -> items whose param 623 names that row. */
		Map<Integer, List<Integer>> itemsByRow();

		String npcName(int id);

		String objectName(int id);

		String itemName(int id);

		/** Items whose int param {@code param} is set: param value -> item ids, in id order (empty if none). */
		default Map<Integer, List<Integer>> itemsByParam(int param)
		{
			return Collections.emptyMap();
		}

		/** The varbit that switches an object between its forms (a "multiloc"), or -1. */
		default int objectVarbit(int objectId)
		{
			return -1;
		}

		/** A varbit's definition {varp, lowest bit, highest bit}, or null. */
		default int[] varbit(int id)
		{
			return null;
		}

		/** An enum's int values by key (empty if there is no such enum). */
		default Map<Integer, Integer> enumInts(int id)
		{
			return Collections.emptyMap();
		}

		/** An enum's string values by key (empty if there is no such enum). */
		default Map<Integer, String> enumStrings(int id)
		{
			return Collections.emptyMap();
		}
	}

	/** Param on clue items naming their step's DB row (checked: every value is a clue-table row). */
	public static final int PARAM_CLUE_ROW = 623;
	/**
	 * Item param holding the "trail item group" the outfit table's PARAM columns and table 22 name (the game's own
	 * outfit check, client script 6484, compares {@code oc_param 258} of the player's items with those columns).
	 */
	public static final int PARAM_TRAIL_GROUP = 258;
	/** Enum emote number -> the emote's name (client script 6640 shows a clue's emotes with it). */
	public static final int ENUM_EMOTE_NAMES = 1000;
	/**
	 * Enum STASH object -> packed (tier code, bit): client scripts 6472 and 1479 read "built" as that bit of the tier's
	 * varp (1 easy 1365, 2 medium 1366, 3 hard 1367, 4 elite 1368, 5 master 1369, 6 beginner 2202).
	 */
	public static final int ENUM_STASH_BITS = 1368;
	/** Enum STASH object -> its place name, as the game's STASH list (client script 1478) shows it. */
	public static final int ENUM_STASH_NAMES = 1531;
	/** The varps of {@link #ENUM_STASH_BITS}'s tier codes 1-6 (RuneLite gameval VarPlayerID.HH_CONSTRUCTED_*). */
	static final int[] STASH_VARPS = {-1, 1365, 1366, 1367, 1368, 1369, 2202};
	static final String[] STASH_TIERS = {"", "easy", "medium", "hard", "elite", "master", "beginner"};
	/** The quest DB table (RuneLite gameval DBTableID.Quest) and its display-name column. */
	static final int QUEST_NAME_COLUMN = 2;
	/** The quest state "finished" in the requirement table's state column (client script 4136: state 2). */
	static final int QUEST_DONE = 2;

	/** Cache difficulty number -> tier (from table 3: its columns are quest, beginner, easy ... master clues). */
	static final String[] TIERS = {"beginner", "easy", "medium", "hard", "elite", "master", "quest"};

	/** Columns of one clue table; -1 when the table has no such column. */
	static final class Spec
	{
		final int table;
		final String type;
		final int text;
		final int target;
		final int reqs;
		final int challenge;
		final int encounter;
		final int outfit;
		final int outfitText;
		final int stashLoc;
		final int stashCoord;
		final int emote;
		final int restricted;
		/** Music: the track row; fairy ring: the ring number. */
		final int special;

		Spec(int table, String type, int text, int target, int reqs, int challenge, int encounter, int outfit, int outfitText,
			int stashLoc, int stashCoord, int emote, int restricted, int special)
		{
			this.table = table;
			this.type = type;
			this.text = text;
			this.target = target;
			this.reqs = reqs;
			this.challenge = challenge;
			this.encounter = encounter;
			this.outfit = outfit;
			this.outfitText = outfitText;
			this.stashLoc = stashLoc;
			this.stashCoord = stashCoord;
			this.emote = emote;
			this.restricted = restricted;
			this.special = special;
		}
	}

	// table, type, text, target, reqs, challenge, encounter, outfit, outfitText, stashLoc, stashCoord, emote, restricted, special
	static final Spec[] SPECS = {
		new Spec(ClueTables.ANAGRAM, "anagram", 2, 3, 6, 4, -1, -1, -1, -1, -1, -1, 8, -1),
		new Spec(ClueTables.MAP, "map", -1, 2, 3, -1, -1, -1, -1, -1, -1, -1, 6, -1),
		new Spec(ClueTables.CIPHER, "cipher", 2, 3, 5, 4, -1, -1, -1, -1, -1, -1, 8, -1),
		new Spec(ClueTables.COORDINATE, "coordinate", 2, 3, 4, -1, 5, -1, -1, -1, -1, -1, 8, -1),
		new Spec(ClueTables.CRYPTIC, "cryptic", 2, 3, 4, 5, -1, 7, 6, 8, 9, -1, 12, -1),
		new Spec(ClueTables.EMOTE, "emote", 2, 3, 10, -1, 9, 6, 5, 7, 8, 4, 13, -1),
		new Spec(ClueTables.FAIRYRING, "fairyring", 2, 5, 6, -1, -1, -1, -1, -1, -1, -1, 9, 3),
		new Spec(ClueTables.FALO, "falo", 2, 3, 4, -1, -1, -1, -1, -1, -1, -1, 7, -1),
		new Spec(ClueTables.HOTCOLD, "hotcold", -1, 2, 3, -1, 4, -1, -1, -1, -1, -1, 7, -1),
		new Spec(ClueTables.MUSIC, "music", 3, 4, 5, -1, -1, -1, -1, -1, -1, -1, 8, 2),
		new Spec(ClueTables.SKILLCHALLENGE, "skillchallenge", 2, 4, 3, -1, -1, -1, -1, -1, -1, -1, 7, -1),
	};

	/** Equipment slot names of the outfit table's columns 1-11. */
	private static final String[] SLOTS = {"hat", "back", "front", "right hand", "torso", "left hand", "legs", "hands", "feet", "ring", "quiver"};
	/**
	 * The same slots as the requirement lines name them ({@code EquipSlot}: head, cape, neck, weapon, body, shield, legs,
	 * hands, feet, ring, ammo; the same order).
	 */
	private static final String[] EQUIP_SLOTS = {"head", "cape", "neck", "weapon", "body", "shield", "legs", "hands", "feet", "ring", "ammo"};
	/**
	 * The game's "nothing" item (RuneLite gameval {@code ItemID.BLANKOBJECT}, no name in the cache; game scripts draw
	 * empty item slots with it): an outfit slot holding only it means nothing may be worn there ("Have nothing equipped
	 * at all", "no boots", "no jewelry"), shown as "nothing" rather than "Item 6512".
	 */
	static final int BLANK_ITEM = 6512;

	private final Source src;
	private final String cacheName;
	private final Map<Integer, Integer> tableOfRow = new HashMap<>();
	private final Map<Integer, Map<Integer, Object[][]>> tables = new HashMap<>();
	private final Map<Integer, List<Integer>> trailGroups;
	private final Map<Integer, String> emoteNames;
	private final Map<Integer, Integer> stashBits;
	private final Map<Integer, String> stashNames;

	/** @param cacheName for the source text, e.g. "cache 2735" */
	public ClueExtractor(Source src, String cacheName)
	{
		this.src = src;
		this.cacheName = cacheName;
		this.trailGroups = src.itemsByParam(PARAM_TRAIL_GROUP);
		this.emoteNames = src.enumStrings(ENUM_EMOTE_NAMES);
		this.stashBits = src.enumInts(ENUM_STASH_BITS);
		this.stashNames = src.enumStrings(ENUM_STASH_NAMES);
		for (int t : ClueTables.NAMES.keySet())
		{
			Map<Integer, Object[][]> rows = src.rows(t);
			tables.put(t, rows == null ? Collections.emptyMap() : rows);
			for (int r : tables.get(t).keySet())
			{
				tableOfRow.put(r, t);
			}
		}
	}

	/** Every clue step, in table order then row order. */
	public List<ClueStep> extract()
	{
		List<ClueStep> out = new ArrayList<>();
		Map<Integer, List<Integer>> items = src.itemsByRow();
		for (Spec s : SPECS)
		{
			for (Map.Entry<Integer, Object[][]> e : tables.get(s.table).entrySet())
			{
				out.add(step(s, e.getKey(), e.getValue(), items.getOrDefault(e.getKey(), Collections.emptyList())));
			}
		}
		return out;
	}

	/** What one step collects while it is read. */
	private static final class Parts
	{
		final List<String> needs = new ArrayList<>();
		final List<String> notes = new ArrayList<>();
		final List<String> checks;
		final List<ClueStep.Place> places = new ArrayList<>();
		/** The same needs as a requirement line (docs/DATA_FORMATS.md), item ids only. */
		final List<String> req = new ArrayList<>();

		Parts(List<String> checks)
		{
			this.checks = checks;
		}
	}

	private ClueStep step(Spec s, int rowId, Object[][] row, List<Integer> items)
	{
		Parts p = new Parts(new ArrayList<>());
		int difficulty = integer(row, 1, -1);
		String tier = difficulty >= 0 && difficulty < TIERS.length ? TIERS[difficulty] : "difficulty " + difficulty;
		if (difficulty < 0 || difficulty >= TIERS.length)
		{
			p.checks.add("unknown difficulty " + difficulty);
		}
		String text = s.text >= 0 ? clean(string(row, s.text)) : "";
		if (s.type.equals("music"))
		{
			String track = label(integer(row, s.special, -1));
			p.notes.add("unlocked at: " + text);
			text = track;
		}
		if (s.type.equals("fairyring"))
		{
			p.notes.add("fairy ring " + integer(row, s.special, -1) + ", steps " + joinInts(ints(row, 4)));
		}
		List<Integer> emotes = s.emote >= 0 ? ints(row, s.emote) : List.of();
		if (s.emote >= 0)
		{
			p.notes.add("emote " + (emotes.isEmpty() ? "-1" : joinInts(emotes)));
		}
		StringBuilder source = new StringBuilder(cacheName).append(" table ").append(s.table).append(" row ").append(rowId);
		Target main = targets(ints(row, s.target), source, p);
		String stashState = s.stashLoc >= 0 ? stash(row, s, tier, p) : "";
		addOutfits(s, row, p);
		addRequirements(s, row, p);
		addChallengesAndFights(s, row, p);
		if (integer(row, s.restricted, 0) != 0)
		{
			p.checks.add("the cache marks this step restricted content (" + integer(row, s.restricted, 0) + "; meaning unknown)");
		}
		int[] tile = main.tile == null ? new int[]{-1, -1, -1} : main.tile;
		String action = action(s.type, rowId, main.kind, emotes, p.checks);
		return new ClueStep(s.type + "-" + rowId, s.type, tier, items, text, main.kind, main.ids, main.name, tile[0], tile[1], tile[2],
			main.description, p.places, p.needs, p.notes, source.toString(), p.checks, action, String.join("; ", distinct(p.req)), stashState);
	}

	/**
	 * The step's target rows: the first is the target, more are alternatives (e.g. the NPC stands elsewhere after a
	 * quest), each added to {@code source}; a "none" target when there is none.
	 */
	private Target targets(List<Integer> targets, StringBuilder source, Parts p)
	{
		Target main = null;
		if (targets.isEmpty())
		{
			p.checks.add("no target in the cache");
		}
		for (int i = 0; i < targets.size(); i++)
		{
			int tr = targets.get(i);
			Integer tt = tableOfRow.get(tr);
			source.append(i == 0 ? " -> " : " / ").append("table ").append(tt).append(" row ").append(tr);
			Target t = target(tr, i == 0 ? p : new Parts(p.checks));
			if (i == 0)
			{
				main = t;
			}
			else if (t.tile != null)
			{
				p.places.add(new ClueStep.Place("alt-target", t.ids, t.name, t.tile[0], t.tile[1], t.tile[2]));
				p.notes.add("alternative target: " + t.description);
			}
		}
		if (main == null)
		{
			main = new Target("none");
		}
		if (main.tile == null && !main.kind.equals("kill") && !targets.isEmpty())
		{
			p.checks.add("the target has no tile in the cache");
		}
		return main;
	}

	/** The STASH unit ("hidey hole"): adds its place and returns its state ({@link #stashState}); "" without one. */
	private String stash(Object[][] row, Spec s, String tier, Parts p)
	{
		List<Integer> stash = ints(row, s.stashLoc);
		int[] st = coord(row, s.stashCoord);
		if (stash.isEmpty() && st == null)
		{
			return "";
		}
		if (st == null)
		{
			p.checks.add("STASH unit without a tile");
		}
		else
		{
			p.places.add(new ClueStep.Place("stash", stash, names(stash, false), st[0], st[1], st[2]));
		}
		return stash.isEmpty() ? "" : stashState(stash.get(0), tier, p.checks);
	}

	/** The outfit to wear, and the table's own outfit text as a note. */
	private void addOutfits(Spec s, Object[][] row, Parts p)
	{
		if (s.outfit < 0)
		{
			return;
		}
		for (int o : ints(row, s.outfit))
		{
			outfit(o, p.needs, p.req, p.checks);
		}
		String fallback = clean(string(row, s.outfitText));
		if (!fallback.isEmpty())
		{
			p.notes.add("outfit text: " + fallback);
		}
	}

	private void addRequirements(Spec s, Object[][] row, Parts p)
	{
		if (s.reqs >= 0)
		{
			for (int r : ints(row, s.reqs))
			{
				requirement(r, p.needs, p.req, p.checks);
			}
		}
	}

	/** The step's challenges (puzzles, questions) and fights, as notes. */
	private void addChallengesAndFights(Spec s, Object[][] row, Parts p)
	{
		if (s.challenge >= 0)
		{
			for (int c : ints(row, s.challenge))
			{
				p.notes.add(challenge(c));
			}
		}
		if (s.encounter >= 0)
		{
			for (int c : ints(row, s.encounter))
			{
				Object[][] e = tables.get(ClueTables.COMBAT_ENCOUNTER).get(c);
				p.notes.add(e == null ? "fight: row " + c : "fight: " + clean(string(e, 0)) + " (npc " + joinInts(ints(e, 1)) + ")");
			}
		}
	}

	private static List<String> distinct(List<String> v)
	{
		return new ArrayList<>(new java.util.LinkedHashSet<>(v));
	}

	/**
	 * What the player does at the place, as the game's own clue helper says it (client scripts of cache 2735): "dig"
	 * by script 6651's rule (fairy ring, coordinate and hot/cold steps; map, cryptic and cipher steps whose target is a
	 * tile; the anagram row 2299), "emote" with the emotes in order and their names (scripts 6640, enum 1000),
	 * "music" (script 6637: play the track near the NPC), "talk" (an NPC target, script 6627 "Talk-to"), "search" (an
	 * object target: map clues "search the marked location", script 6644), "key" (kill for the key, then search, script
	 * 6627), "kill"; "none" without a target.
	 */
	String action(String type, int rowId, String target, List<Integer> emotes, List<String> checks)
	{
		switch (type)
		{
			case "fairyring":
			case "coordinate":
			case "hotcold":
				return "dig";
			case "map":
			case "cryptic":
			case "cipher":
				if (target.equals("coord"))
				{
					return "dig";
				}
				break;
			case "anagram":
				if (rowId == 2299)
				{
					return "dig"; // the game's script 6651 names this one row
				}
				break;
			case "emote":
			{
				List<String> each = new ArrayList<>();
				for (int e : emotes)
				{
					String name = e == -1 ? "Bull roarer" : emoteNames.get(e); // script 6640 shows -1 as "Bull roarer"
					if (name == null)
					{
						checks.add("emote " + e + " has no name in enum " + ENUM_EMOTE_NAMES);
						name = "emote " + e;
					}
					each.add(e + " " + name);
				}
				return "emote " + String.join(" | ", each);
			}
			case "music":
				return "music";
			default:
				break;
		}
		switch (target)
		{
			case "npc":
				return "talk";
			case "loc":
				return "search";
			case "key":
				return "key";
			case "kill":
				return "kill";
			case "none":
				return "none";
			default:
				checks.add("no action known for a " + type + " step with a " + target + " target");
				return "go";
		}
	}

	/**
	 * The STASH unit's state as the game reads it: "varbit V ; tier ; place". V is the object's own form varbit (built
	 * = 1: form 0 offers "Build", form 1 is the "STASH" with "Search"); the tier and the varp bit come from enum 1368
	 * (client script 6472), the place from enum 1531. A mismatch between the two is put in the checks.
	 */
	private String stashState(int loc, String tier, List<String> checks)
	{
		int varbit = src.objectVarbit(loc);
		Integer packed = stashBits.get(loc);
		String stashTier = "";
		if (packed == null)
		{
			checks.add("STASH unit " + loc + " is not in enum " + ENUM_STASH_BITS);
		}
		else
		{
			int code = (packed >> 14) & 0x3FFF;
			int bit = packed & 0x3FFF;
			stashTier = code >= 1 && code < STASH_TIERS.length ? STASH_TIERS[code] : "";
			int[] def = varbit < 0 ? null : src.varbit(varbit);
			if (stashTier.isEmpty() || def == null || def[0] != STASH_VARPS[code] || def[1] != bit || def[2] != bit)
			{
				checks.add("STASH unit " + loc + ": its varbit " + varbit + " is not bit " + bit + " of the tier's varp (enum "
					+ ENUM_STASH_BITS + " code " + code + ")");
			}
			else if (!stashTier.equals(tier))
			{
				checks.add("STASH unit " + loc + " is a " + stashTier + " STASH on a " + tier + " step");
			}
		}
		String place = stashNames.getOrDefault(loc, "");
		if (place.isEmpty())
		{
			checks.add("STASH unit " + loc + " has no place name in enum " + ENUM_STASH_NAMES);
		}
		return "varbit " + varbit + " ; " + (stashTier.isEmpty() ? "?" : stashTier) + " ; " + clean(place).replace(";", ",");
	}

	/** Every item of a trail item group (item param 258), or an empty list. */
	private List<Integer> trailGroup(int group)
	{
		return trailGroups.getOrDefault(group, List.of());
	}

	/** "item 1/2/3" with options; null if there are no ids. */
	private static String itemPart(List<Integer> ids, int count, boolean worn)
	{
		if (ids.isEmpty())
		{
			return null;
		}
		StringBuilder sb = new StringBuilder("item ");
		for (int i = 0; i < ids.size(); i++)
		{
			sb.append(i == 0 ? "" : "/").append(ids.get(i));
		}
		if (count > 1)
		{
			sb.append(" x").append(count);
		}
		if (worn)
		{
			sb.append(" worn");
		}
		return sb.toString();
	}

	/** One resolved target row. */
	private static final class Target
	{
		final String kind;
		List<Integer> ids = new ArrayList<>();
		String name = "";
		int[] tile;
		String description = "";

		Target(String kind)
		{
			this.kind = kind;
		}
	}

	/** Resolves a target row; its key drop, fallback ids and places go into {@code p}. */
	private Target target(int tr, Parts p)
	{
		Integer tt = tableOfRow.get(tr);
		Object[][] t = tt == null ? null : tables.get(tt).get(tr);
		if (t == null)
		{
			p.checks.add("target row " + tr + " is not in a clue table");
			return new Target("none");
		}
		switch (tt)
		{
			case ClueTables.TARGET_NPC:
				return placedTarget("npc", t, true, p);
			case ClueTables.TARGET_LOC:
				return placedTarget("loc", t, false, p);
			case ClueTables.TARGET_COORD:
				return coordTarget(t, p);
			case ClueTables.TARGET_KEY:
				return keyTarget(t, p);
			case ClueTables.TARGET_KILL:
				return killTarget(t, p);
			default:
				p.checks.add("target row " + tr + " is in table " + tt + ", not a target table");
				return new Target("none");
		}
	}

	/** An NPC or object target: its ids, tile and description; its fallback ids as a note. */
	private Target placedTarget(String kind, Object[][] t, boolean npc, Parts p)
	{
		Target out = new Target(kind);
		out.ids.addAll(ints(t, 0));
		out.name = names(out.ids, npc);
		out.tile = coord(t, 2);
		out.description = clean(string(t, 3));
		List<Integer> fallback = ints(t, 1);
		if (!fallback.isEmpty())
		{
			p.notes.add("fallback " + kind + " " + joinInts(fallback) + " " + names(fallback, npc));
		}
		return out;
	}

	private Target coordTarget(Object[][] t, Parts p)
	{
		Target out = new Target("coord");
		out.tile = coord(t, 0);
		out.description = clean(string(t, 1));
		String area = clean(string(t, 2));
		if (!area.isEmpty())
		{
			p.notes.add("area: " + area);
		}
		return out;
	}

	/** A key target: the object to search, the key the player needs and the place of the NPCs that drop it. */
	private Target keyTarget(Object[][] t, Parts p)
	{
		Target out = new Target("key");
		out.ids.addAll(ints(t, 0));
		out.name = names(out.ids, false);
		out.tile = coord(t, 1);
		out.description = clean(string(t, 7));
		List<Integer> npcs = ints(t, 2);
		int[] keyTile = coord(t, 4);
		int key = integer(t, 3, -1);
		if (key >= 0)
		{
			int count = integer(t, 6, 1);
			p.needs.add("item " + key + " " + nameOr(src.itemName(key)) + (count > 1 ? " x" + count : "") + " (the key)");
			p.req.add(itemPart(List.of(key), count, false));
		}
		if (keyTile != null)
		{
			p.places.add(new ClueStep.Place("key-npc", npcs, names(npcs, true), keyTile[0], keyTile[1], keyTile[2]));
		}
		else
		{
			p.checks.add("no tile for the key's NPCs");
		}
		return out;
	}

	private Target killTarget(Object[][] t, Parts p)
	{
		Target out = new Target("kill");
		out.ids.addAll(ints(t, 0));
		out.name = names(out.ids, true);
		out.tile = coord(t, 1);
		out.description = clean(string(t, 2));
		if (out.tile == null)
		{
			p.checks.add("kill step: the cache gives no tile (NPC spawns are not in the cache)");
		}
		return out;
	}

	/**
	 * An outfit row: in words for {@code needs}, and as requirement parts: one "item ... worn" per slot whose ids are
	 * the slot's items and the items of its trail item group (alternatives: any one of them), and one for the
	 * "any slot" group (column 23, RuneLite's COL_WEARPOS_PARAM_ANY).
	 */
	private void outfit(int rowId, List<String> needs, List<String> req, List<String> checks)
	{
		Object[][] o = tables.get(ClueTables.OUTFIT).get(rowId);
		if (o == null)
		{
			needs.add("outfit row " + rowId + " (not found)");
			checks.add("outfit row " + rowId + " not found");
			return;
		}
		for (int i = 0; i < SLOTS.length; i++)
		{
			List<Integer> slot = ints(o, 1 + i);
			if (slot.size() == 1 && slot.get(0) == BLANK_ITEM && integer(o, 12 + i, -1) < 0)
			{
				// nothing may be worn in this slot
				needs.add("wear nothing (" + SLOTS[i] + ")");
				req.add("empty " + EQUIP_SLOTS[i]);
				continue;
			}
			List<Integer> ids = new ArrayList<>(slot);
			if (!slot.isEmpty())
			{
				// several items in one slot are alternatives
				List<String> each = new ArrayList<>();
				for (int item : slot)
				{
					each.add(item + " " + nameOr(src.itemName(item)));
				}
				needs.add("wear " + (slot.size() > 1 ? "one of " : "") + String.join(" / ", each) + " (" + SLOTS[i] + ")");
			}
			int group = integer(o, 12 + i, -1);
			if (group >= 0)
			{
				needs.add("wear any trail item of group " + group + " (" + SLOTS[i] + ")");
				ids.addAll(groupOrCheck(group, checks));
			}
			String part = itemPart(distinctInts(ids), 1, true);
			if (part != null)
			{
				req.add(part);
			}
		}
		int any = integer(o, 23, -1);
		if (any >= 0)
		{
			needs.add("wear any trail item of group " + any);
			String part = itemPart(groupOrCheck(any, checks), 1, true);
			if (part != null)
			{
				req.add(part);
			}
		}
	}

	private List<Integer> groupOrCheck(int group, List<String> checks)
	{
		List<Integer> ids = trailGroup(group);
		if (ids.isEmpty())
		{
			checks.add("trail item group " + group + " has no items (item param " + PARAM_TRAIL_GROUP + ")");
		}
		return ids;
	}

	private static List<Integer> distinctInts(List<Integer> v)
	{
		return new ArrayList<>(new java.util.LinkedHashSet<>(v));
	}

	private void requirement(int rowId, List<String> needs, List<String> req, List<String> checks)
	{
		Integer t = tableOfRow.get(rowId);
		Object[][] r = t == null ? null : tables.get(t).get(rowId);
		if (r == null)
		{
			checks.add("requirement row " + rowId + " not found");
			return;
		}
		switch (t)
		{
			case ClueTables.REQ_OBJ:
			{
				int count = integer(r, 3, 1);
				needs.add("item " + joinInts(ints(r, 1)) + (count > 1 ? " x" + count : "") + " (" + clean(string(r, 0)) + ")");
				String part = itemPart(ints(r, 1), count, false);
				if (part == null)
				{
					checks.add("requirement row " + rowId + " names no item");
				}
				else
				{
					req.add(part);
				}
				break;
			}
			case ClueTables.REQ_OBJ_PARAM:
			{
				int group = integer(r, 1, -1);
				needs.add("item: " + clean(string(r, 0)) + " (trail item group " + group + ")");
				String part = itemPart(groupOrCheck(group, checks), 1, false);
				if (part != null)
				{
					req.add(part);
				}
				break;
			}
			case ClueTables.REQ_QUEST:
			{
				int questRow = integer(r, 1, -1);
				int state = integer(r, 2, -1);
				needs.add("quest: " + clean(string(r, 0)) + " (quest row " + questRow + ", state " + state + ")");
				Object[][] q = questRow < 0 ? null : src.row(questRow);
				String name = clean(string(q, QUEST_NAME_COLUMN));
				if (name.isEmpty() || state != QUEST_DONE)
				{
					checks.add("quest requirement row " + rowId + ": quest row " + questRow + " state " + state + " not turned into a requirement");
				}
				else
				{
					req.add("quest " + name);
				}
				break;
			}
			case ClueTables.REQ_STAT:
			{
				int stat = integer(r, 0, -1);
				com.bestpath.core.req.Skill[] skills = com.bestpath.core.req.Skill.values();
				String name = stat >= 0 && stat < skills.length ? skills[stat].getLabel() : "stat " + stat;
				needs.add(name + " " + integer(r, 1, -1));
				if (stat >= 0 && stat < skills.length)
				{
					req.add(name + " " + integer(r, 1, -1));
				}
				else
				{
					checks.add("stat " + stat + " is not a known skill");
				}
				break;
			}
			default:
				checks.add("requirement row " + rowId + " is in table " + t);
		}
	}

	private String challenge(int rowId)
	{
		Integer t = tableOfRow.get(rowId);
		Object[][] r = t == null ? null : tables.get(t).get(rowId);
		if (r == null)
		{
			return "challenge row " + rowId;
		}
		if (t == ClueTables.CHALLENGE_BOX)
		{
			return "puzzle: " + clean(string(r, 0));
		}
		if (t == ClueTables.CHALLENGE_QUESTION)
		{
			// (question, answer) pairs; several pairs are variants of one question
			Object[] v = r[0];
			StringBuilder sb = new StringBuilder("question: ");
			for (int i = 0; v != null && i + 1 < v.length; i += 2)
			{
				if (i > 0)
				{
					sb.append(" / ");
				}
				sb.append(clean(String.valueOf(v[i]))).append(" = ").append(v[i + 1]);
			}
			return sb.toString();
		}
		return "challenge row " + rowId + " in table " + t;
	}

	/** First string value of any DB row (a music track's name), or "row N". */
	private String label(int rowId)
	{
		Object[][] r = rowId < 0 ? null : src.row(rowId);
		if (r != null)
		{
			for (Object[] col : r)
			{
				if (col != null)
				{
					for (Object v : col)
					{
						if (v instanceof String)
						{
							return clean((String) v);
						}
					}
				}
			}
		}
		return "row " + rowId;
	}

	private String names(List<Integer> ids, boolean npc)
	{
		List<String> n = new ArrayList<>();
		for (int id : ids)
		{
			String s = nameOr(npc ? src.npcName(id) : src.objectName(id));
			if (!n.contains(s))
			{
				n.add(s);
			}
		}
		return String.join(" / ", n);
	}

	private static String nameOr(String s)
	{
		return s == null || s.isEmpty() || s.equals("null") ? "?" : s;
	}

	/** Packed coordgrid -> {x, y, plane}, or null. */
	static int[] coord(Object[][] row, int col)
	{
		int v = integer(row, col, Integer.MIN_VALUE);
		if (v == Integer.MIN_VALUE || v == -1)
		{
			return null;
		}
		return unpack(v);
	}

	/** The game's packed tile: plane in bits 28-29, x in bits 14-27, y in bits 0-13. */
	static int[] unpack(int packed)
	{
		return new int[]{(packed >> 14) & 0x3FFF, packed & 0x3FFF, (packed >>> 28) & 3};
	}

	static int integer(Object[][] row, int col, int otherwise)
	{
		if (col < 0 || row == null || col >= row.length || row[col] == null || row[col].length == 0 || !(row[col][0] instanceof Number))
		{
			return otherwise;
		}
		return ((Number) row[col][0]).intValue();
	}

	static List<Integer> ints(Object[][] row, int col)
	{
		List<Integer> out = new ArrayList<>();
		if (col >= 0 && row != null && col < row.length && row[col] != null)
		{
			for (Object v : row[col])
			{
				if (v instanceof Number)
				{
					out.add(((Number) v).intValue());
				}
			}
		}
		return out;
	}

	static String string(Object[][] row, int col)
	{
		if (col < 0 || row == null || col >= row.length || row[col] == null || row[col].length == 0 || row[col][0] == null)
		{
			return "";
		}
		return String.valueOf(row[col][0]);
	}

	/** One line of plain text: the game's {@code <br>} and tabs become spaces. */
	static String clean(String s)
	{
		return s == null ? "" : s.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("[\\t\\r\\n]+", " ").replaceAll(" {2,}", " ").trim();
	}

	static String joinInts(List<Integer> v)
	{
		StringBuilder sb = new StringBuilder();
		for (int i : v)
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(i);
		}
		return sb.toString();
	}

	/** Steps per type and tier, for the CLI summary. */
	public static String summary(List<ClueStep> steps)
	{
		Map<String, int[]> per = new java.util.TreeMap<>();
		for (ClueStep s : steps)
		{
			int[] c = per.computeIfAbsent(s.type, k -> new int[TIERS.length]);
			for (int i = 0; i < TIERS.length; i++)
			{
				if (TIERS[i].equals(s.tier))
				{
					c[i]++;
				}
			}
		}
		StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-15s", "type"));
		for (String t : TIERS)
		{
			sb.append(String.format(Locale.ROOT, "%9s", t));
		}
		sb.append('\n');
		for (Map.Entry<String, int[]> e : per.entrySet())
		{
			sb.append(String.format(Locale.ROOT, "%-15s", e.getKey()));
			for (int n : e.getValue())
			{
				sb.append(String.format(Locale.ROOT, "%9d", n));
			}
			sb.append('\n');
		}
		return sb.toString();
	}
}
