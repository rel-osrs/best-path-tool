package com.bestpath.tool.route;

import com.bestpath.core.data.ClueData;
import com.bestpath.core.path.Tiles;
import com.bestpath.tool.cache.LoadedCache;
import com.bestpath.tool.clue.ClueFile;
import com.bestpath.tool.clue.ClueOverrides;
import com.bestpath.tool.clue.ClueStep;
import com.bestpath.tool.clue.KillAreas;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The clue part of the plugin export (docs/DATA_FORMATS.md):
 * {@code data/clues/clue-steps.tsv} (generated from the cache) plus the
 * Wiki places of the kill steps ({@code data/wiki/clue-kill-spawns.tsv}),
 * cut down to what the plugin needs to find a step and route to it, with the
 * manual corrections of {@code data/clues/clue-overrides.tsv} applied
 * ({@link ClueOverrides}: retired steps left out, fixed tiles, needs, notes,
 * task-only kill areas left out, kill-area planes and moved Wiki layers).
 *
 * <p>Per step: every target tile (the target, alternative targets, one tile
 * per group of Wiki kill spawns ({@link KillAreas}): the group's tile nearest
 * its middle), the STASH unit, the clue items that name it, the text (beginner
 * and master clues are found by their text), the cache's description and what
 * the step needs (a requirement line) and a note for the player. Key-chest steps keep the chest as the
 * target; the NPC that drops the key (the cache's key-NPC tile, its first name) and the key item go in their own
 * columns (the plugin routes to the NPC first while the player has no key).
 */
final class ClueExport
{
	/** Item param naming a clue item's step (RuneLite's ParamID.CLUE_SCROLL; docs/CLUES.md). */
	static final int CLUE_PARAM = 623;

	private ClueExport()
	{
	}

	/** What {@link #build} made, and what it noticed. */
	static final class Result
	{
		ClueData data;
		int steps;
		int withPlace;
		int killAreas;
		int itemsChecked;
		/** Steps left out as retired, steps whose tile was replaced, steps with needs, steps with a note (clue-overrides.tsv). */
		int retired;
		int tilesFixed;
		int withNeeds;
		int withNote;
		/** Steps with a STASH unit whose built varbit the plugin can read. */
		int withStash;
		/** Key steps with the NPC that drops the key (its tile and the key). */
		int withKeyNpc;
		final List<String> problems = new ArrayList<>();
	}

	/** Reads the clue steps, the Wiki kill spawns and the manual corrections (no steps file: no steps, a problem noted). */
	static Result build(File ws) throws IOException
	{
		File steps = new File(ws, "data/clues/clue-steps.tsv");
		File kills = new File(ws, "data/wiki/clue-kill-spawns.tsv");
		Result res = new Result();
		if (!steps.isFile())
		{
			res.data = ClueData.EMPTY;
			res.problems.add("no " + steps + ": no clue steps exported");
			return res;
		}
		ClueOverrides overrides = ClueOverrides.read(ClueOverrides.file(ws));
		List<String[]> spawnLines = KillAreas.readLines(kills);
		List<ClueStep> parsed = ClueFile.read(steps);
		for (String p : overrides.problems(parsed, KillAreas.locations(spawnLines)))
		{
			res.problems.add("clue-overrides " + p);
		}
		Map<String, ClueStep> byKey = new HashMap<>();
		for (ClueStep s : parsed)
		{
			byKey.put(s.key, s);
		}
		Map<String, List<KillAreas.Group>> groups = KillAreas.groups(spawnLines, overrides);
		List<ClueData.Step> out = new ArrayList<>();
		for (Map<String, String> row : rows(steps))
		{
			String key = row.get("key");
			ClueStep step = byKey.get(key);
			if (step == null || overrides.retired(key))
			{
				res.retired += step == null ? 0 : 1;
				continue;
			}
			out.add(step(row, step, overrides, groups.getOrDefault(key, List.of()), res));
		}
		res.data = new ClueData(out);
		return res;
	}

	/** One step of clue-steps.tsv ({@code row}) as the plugin gets it; counts what it has on {@code res}. */
	private static ClueData.Step step(Map<String, String> row, ClueStep step, ClueOverrides overrides, List<KillAreas.Group> groups,
		Result res)
	{
		String key = row.get("key");
		Set<Integer> targets = new LinkedHashSet<>();
		int[] fixed = overrides.tile(key);
		Integer main = fixed != null ? Integer.valueOf(Tiles.pack(fixed[0], fixed[1], fixed[2])) : tile(row.get("x"), row.get("y"), row.get("plane"));
		res.tilesFixed += fixed != null ? 1 : 0;
		if (main != null)
		{
			targets.add(main);
		}
		int stash = addPlaces(row.get("places"), targets);
		Set<String> wiki = new LinkedHashSet<>();
		for (KillAreas.Group g : groups)
		{
			int[] m = g.middle();
			targets.add(Tiles.pack(m[0], m[1], g.plane));
			res.killAreas++;
			wiki.addAll(g.wiki);
		}
		int[] tg = targets.stream().mapToInt(Integer::intValue).toArray();
		String needs = needs(step, overrides);
		res.withNeeds += needs.isEmpty() ? 0 : 1;
		String note = overrides.note(key);
		res.withNote += note.isEmpty() ? 0 : 1;
		res.withStash += step.stashVarbit() >= 0 ? 1 : 0;
		ClueStep.Place keyNpc = keyNpc(step);
		int keyItem = keyNpc == null ? -1 : keyItem(step);
		if (keyNpc != null && keyItem < 0)
		{
			res.problems.add(key + ": the key NPC is known but not the key item");
			keyNpc = null;
		}
		res.withKeyNpc += keyNpc == null ? 0 : 1;
		ClueData.Step s = new ClueData.Step(key, row.get("type"), row.get("tier"), dash(row.get("target")),
			ints(dash(row.get("items"))), tg, stash, dash(row.get("text")), dash(row.get("description")), String.join(",", wiki), needs,
			note, action(step), step.stashVarbit(), step.stashPlace(), keyNpc == null ? -1 : Tiles.pack(keyNpc.x, keyNpc.y, keyNpc.plane),
			keyNpc == null ? "" : firstName(keyNpc.name), keyItem);
		res.steps++;
		res.withPlace += tg.length > 0 ? 1 : 0;
		return s;
	}

	/** Adds the step's alternative targets of its places column to {@code targets}; returns its STASH unit's tile, or -1. */
	private static int addPlaces(String places, Set<Integer> targets)
	{
		int stash = -1;
		for (String place : list(places))
		{
			String role = place.substring(0, Math.max(0, place.indexOf(':')));
			int at = place.lastIndexOf('@');
			Integer t = at < 0 ? null : tile(place.substring(at + 1));
			if (t == null)
			{
				continue;
			}
			if (role.equals("alt-target"))
			{
				targets.add(t);
			}
			else if (role.equals("stash") && stash == -1)
			{
				stash = t;
			}
		}
		return stash;
	}

	/**
	 * The step's action for the plugin: the action of clue-steps.tsv, and for talk, search, key, kill and music the
	 * target's name from the cache after it ("talk Ranael", "search Drawers", "music Cecilia"; the first of several
	 * names, none when the cache has no name).
	 */
	static String action(ClueStep step)
	{
		switch (step.action)
		{
			case "talk":
			case "search":
			case "key":
			case "kill":
			case "music":
			{
				String name = step.targetName == null ? "" : step.targetName.split(" / ")[0].trim();
				return name.isEmpty() || name.equals("?") ? step.action : step.action + " " + name;
			}
			default:
				return step.action;
		}
	}

	/**
	 * A key step's "key-npc" place: where the NPC that drops the key is (cache table TargetKey's key-NPC tile; the
	 * route goes there first), or null.
	 */
	static ClueStep.Place keyNpc(ClueStep step)
	{
		if (!"key".equals(step.action))
		{
			return null;
		}
		for (ClueStep.Place p : step.places)
		{
			if (p.role.equals("key-npc"))
			{
				return p;
			}
		}
		return null;
	}

	/** A key step's key: the item of its "item N ... (the key)" need (cache table TargetKey), or -1. */
	static int keyItem(ClueStep step)
	{
		for (String n : step.needs)
		{
			if (n.startsWith("item ") && n.endsWith("(the key)"))
			{
				String[] w = n.split(" ");
				try
				{
					return Integer.parseInt(w[1]);
				}
				catch (NumberFormatException e)
				{
					return -1;
				}
			}
		}
		return -1;
	}

	/** The first of the cache's names ("Barbarian" of "Barbarian / Hunding"), "" for none. */
	private static String firstName(String names)
	{
		String n = names == null ? "" : names.split(" / ")[0].trim();
		return n.equals("?") ? "" : n;
	}

	/**
	 * The step's needs as one requirement line: what the cache's clue tables say (the step's {@code requirement}:
	 * outfit items worn, items to bring, skill levels, quests, the key) and then the manual {@code needs} lines of
	 * clue-overrides.tsv (spade, light), each part once.
	 */
	static String needs(ClueStep step, ClueOverrides overrides)
	{
		Set<String> parts = new LinkedHashSet<>();
		if (!step.requirement.isEmpty())
		{
			parts.add(step.requirement);
		}
		parts.addAll(overrides.needs(step));
		return String.join("; ", parts);
	}

	/**
	 * Checks the clue items against the cache: each item listed for a step must carry param 623 = the step's row
	 * (the plugin also reads that param for items it does not know), and the beginner / master clue items and the
	 * hot/cold devices the plugin uses (RuneLite gameval ItemID names) must be what their names say.
	 */
	static void checkItems(Result res, LoadedCache cache) throws IOException
	{
		for (ClueData.Step s : res.data.getSteps())
		{
			for (int id : s.getItems())
			{
				Integer v = cache.itemIntParam(id, CLUE_PARAM);
				res.itemsChecked++;
				if (v == null || v != s.getRow())
				{
					res.problems.add("item " + id + " of " + s.getKey() + ": param " + CLUE_PARAM + " is " + v);
				}
			}
		}
		Object[][] shared = {{19835, "Clue scroll (master)"}, {23182, "Clue scroll (beginner)"}, {19939, "Strange device"},
			{23183, "Strange device"}};
		for (Object[] s : shared)
		{
			String name = cache.itemName((Integer) s[0]);
			if (!s[1].equals(name))
			{
				res.problems.add("item " + s[0] + " is '" + name + "', the plugin expects '" + s[1] + "'");
			}
		}
	}

	/** The rows of a generated TSV file as column name -> value (comment lines skipped, first other line = names). */
	static List<Map<String, String>> rows(File f) throws IOException
	{
		List<Map<String, String>> out = new ArrayList<>();
		String[] names = null;
		for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] w = line.split("\t", -1);
			if (names == null)
			{
				names = w;
				continue;
			}
			Map<String, String> m = new HashMap<>();
			for (int i = 0; i < names.length && i < w.length; i++)
			{
				m.put(names[i], w[i]);
			}
			out.add(m);
		}
		return out;
	}

	private static String dash(String s)
	{
		return s == null || s.equals("-") ? "" : s;
	}

	private static List<String> list(String s)
	{
		List<String> out = new ArrayList<>();
		for (String p : dash(s).split(" ; "))
		{
			if (!p.trim().isEmpty())
			{
				out.add(p.trim());
			}
		}
		return out;
	}

	private static int[] ints(String s)
	{
		if (s.isEmpty())
		{
			return new int[0];
		}
		String[] w = s.split(",");
		int[] out = new int[w.length];
		for (int i = 0; i < w.length; i++)
		{
			out[i] = Integer.parseInt(w[i].trim());
		}
		return out;
	}

	private static Integer tile(String xyz)
	{
		String[] c = xyz.split(",");
		return c.length == 3 ? tile(c[0], c[1], c[2]) : null;
	}

	private static Integer tile(String x, String y, String p)
	{
		if (x == null || y == null || p == null || dash(x).isEmpty() || dash(y).isEmpty() || dash(p).isEmpty())
		{
			return null;
		}
		try
		{
			return Tiles.pack(Integer.parseInt(x.trim()), Integer.parseInt(y.trim()), Integer.parseInt(p.trim()));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** One line for the export's output. */
	static String describe(Result r, int bytes)
	{
		return String.format(Locale.ROOT, "clues: %,d steps (%,d with a place, %,d Wiki kill spawn groups, %,d with needs, %,d with a note,"
			+ " %,d with a STASH unit, %,d with a key NPC; overrides: %,d retired left out, %,d tiles fixed), %,d clue items checked against"
			+ " the cache, %,d bytes",
			r.steps, r.withPlace, r.killAreas, r.withNeeds, r.withNote, r.withStash, r.withKeyNpc, r.retired, r.tilesFixed, r.itemsChecked,
			bytes);
	}
}
