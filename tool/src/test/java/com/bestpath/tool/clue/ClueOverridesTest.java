package com.bestpath.tool.clue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.req.RequirementText;
import com.bestpath.tool.req.ItemGroupFile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** data/clues/clue-overrides.tsv: the loader, each change, and the real file against the real data. */
class ClueOverridesTest
{
	private static final String HEAD = "key\tchange\tvalue\tsource";

	private static ClueStep step(String key, String type, String target, int x, int y, int plane)
	{
		return new ClueStep(key, type, "hard", List.of(), "text", target, List.of(), "", x, y, plane, "desc", Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), "cache 2735 table 8 row 1", Collections.emptyList());
	}

	private static ClueOverrides parse(String... lines)
	{
		List<String> all = new ArrayList<>(List.of("# comment", HEAD));
		all.addAll(List.of(lines));
		return ClueOverrides.parse(all);
	}

	@Test
	void tileReplacesTheTargetAndNotesItInTheSource()
	{
		ClueOverrides o = parse("cryptic-1252\ttile\t3147,9914,0\tWiki Vannaka rev 1");
		List<ClueStep> out = o.apply(List.of(step("cryptic-1252", "cryptic", "npc", 3164, 9913, 0), step("cryptic-9", "cryptic", "npc", 1, 2, 0)));
		assertEquals(2, out.size());
		ClueStep v = out.get(0);
		assertEquals(3147, v.x);
		assertEquals(9914, v.y);
		assertEquals(0, v.plane);
		assertTrue(v.source.endsWith("; override tile 3147,9914,0 (was 3164,9913,0): Wiki Vannaka rev 1"), v.source);
		assertEquals(1, out.get(1).x, "other steps unchanged");
		assertArrayEquals(new int[]{3147, 9914, 0}, o.tile("cryptic-1252"));
		assertNull(o.tile("cryptic-9"));
	}

	@Test
	void retiredStepsAreDropped()
	{
		ClueOverrides o = parse("map-2260\tretired\t-\towner");
		List<ClueStep> out = o.apply(List.of(step("map-2260", "map", "coord", 1, 2, 0), step("map-2", "map", "coord", 1, 2, 0)));
		assertEquals(1, out.size());
		assertEquals("map-2", out.get(0).key);
		assertTrue(o.retired("map-2260"));
		assertFalse(o.retired("map-2"));
	}

	@Test
	void needsForOneStepAndForEveryDigStep()
	{
		ClueOverrides o = parse("*dig\tneeds\titem 952\towner", "hotcold-1\tneeds\titem light source\towner", "hotcold-1\tneeds\titem 952\tdup");
		ClueStep hotcold = step("hotcold-1", "hotcold", "coord", 1, 2, 0);
		assertEquals(List.of("item 952", "item light source"), o.needs(hotcold), "the rule first, no duplicates");
		assertEquals("item 952; item light source", o.needsLine(hotcold));
		assertEquals(List.of("item 952"), o.needs(step("coordinate-1", "coordinate", "coord", 1, 2, 0)));
		assertEquals("", o.needsLine(step("emote-1", "emote", "coord", 1, 2, 0)), "nobody digs at an emote spot");
		assertEquals("", o.needsLine(step("cryptic-1", "cryptic", "npc", 1, 2, 0)));
	}

	@Test
	void digStepRule()
	{
		assertTrue(ClueOverrides.isDigStep(step("coordinate-1", "coordinate", "coord", 1, 2, 0)));
		assertTrue(ClueOverrides.isDigStep(step("hotcold-1", "hotcold", "coord", 1, 2, 0)));
		assertTrue(ClueOverrides.isDigStep(step("fairyring-1", "fairyring", "coord", 1, 2, 0)));
		assertTrue(ClueOverrides.isDigStep(step("map-1", "map", "coord", 1, 2, 0)));
		assertFalse(ClueOverrides.isDigStep(step("map-2", "map", "loc", 1, 2, 0)), "a map to a crate is searched");
		assertTrue(ClueOverrides.isDigStep(step("cryptic-1", "cryptic", "coord", 1, 2, 0)));
		assertFalse(ClueOverrides.isDigStep(step("cryptic-2", "cryptic", "npc", 1, 2, 0)));
		assertFalse(ClueOverrides.isDigStep(step("emote-1", "emote", "coord", 1, 2, 0)));
		assertFalse(ClueOverrides.isDigStep(step("anagram-1", "anagram", "coord", 1, 2, 0)), "not known to be a dig");
	}

	@Test
	void digStepRuleFollowsTheStepsActionWhenItHasOne()
	{
		// the game's clue helper (script 6651) says the quest cipher to a tile and anagram row 2299 are digs
		ClueStep cipher = new ClueStep("cipher-2262", "cipher", "quest", List.of(), "t", "coord", List.of(), "", 1, 2, 0, "", List.of(),
			List.of(), List.of(), "src", List.of(), "dig", "", "");
		assertTrue(ClueOverrides.isDigStep(cipher));
		ClueStep emote = new ClueStep("emote-1", "emote", "easy", List.of(), "t", "coord", List.of(), "", 1, 2, 0, "", List.of(),
			List.of(), List.of(), "src", List.of(), "emote 7 Cheer", "", "");
		assertFalse(ClueOverrides.isDigStep(emote));
		assertEquals("item 952", parse("*dig\tneeds\titem 952\towner").needsLine(cipher));
	}

	@Test
	void everyRealDigStepHasTheGamesDigActionAndOnlyThose() throws Exception
	{
		java.io.File f = new java.io.File("../data/clues/clue-steps.tsv");
		if (!f.isFile())
		{
			return; // not in the repository layout
		}
		int digs = 0;
		for (ClueStep s : ClueFile.read(f))
		{
			ClueStep old = new ClueStep(s.key, s.type, s.tier, s.items, s.text, s.target, s.targetIds, s.targetName, s.x, s.y, s.plane,
				s.description, s.places, s.needs, s.notes, s.source, s.checks);
			boolean special = s.key.equals("anagram-2299") || s.key.equals("cipher-2262");
			// the rule for steps without an action agrees with the game's, except for the two quest steps the game names
			assertEquals(ClueOverrides.isDigStep(s), ClueOverrides.isDigStep(old) || special, s.key);
			digs += ClueOverrides.isDigStep(s) ? 1 : 0;
		}
		assertEquals(412, digs, "dig steps (cache 2735, before retired steps are dropped)");
	}

	@Test
	void killAreaSkipAndPlane()
	{
		ClueOverrides o = parse("cryptic-1464\tskip-area\tStronghold Slayer Cave\tWiki", "cryptic-1460\tarea-plane\t2 God Wars Dungeon\tcheck");
		assertTrue(o.skipArea("cryptic-1464", "Stronghold Slayer Cave"));
		assertTrue(o.skipArea("cryptic-1464", "stronghold slayer cave "), "any case");
		assertFalse(o.skipArea("cryptic-1454", "Stronghold Slayer Cave"), "only for that step");
		assertEquals(2, o.areaPlane("cryptic-1460", "God Wars Dungeon", 0));
		assertEquals(0, o.areaPlane("cryptic-1460", "Slayer Tower (basement)", 0));
		assertEquals(0, o.areaPlane("cryptic-1461", "God Wars Dungeon", 0));
	}

	@Test
	void areaShiftAndNotes()
	{
		ClueOverrides o = parse("cryptic-1461\tarea-shift\t80,40,0 Wilderness God Wars Dungeon\tcache fit",
			"cryptic-1458\tnote\tKill the barbarian spirit here\towner", "cryptic-1458\tnote\tBring food.\towner",
			"cryptic-1458\tnote\tBring food.\tdup");
		assertArrayEquals(new int[]{80, 40, 0}, o.areaShift("cryptic-1461", "wilderness god wars dungeon "), "any case");
		assertNull(o.areaShift("cryptic-1460", "Wilderness God Wars Dungeon"), "only for that step");
		assertNull(o.areaShift("cryptic-1461", "God Wars Dungeon"));
		assertEquals("Kill the barbarian spirit here Bring food.", o.note("cryptic-1458"), "note lines joined, no duplicates");
		assertEquals("", o.note("cryptic-1"));
		List<String> p = o.problems(List.of(step("cryptic-1461", "cryptic", "kill", -1, -1, -1), step("cryptic-1458", "cryptic", "kill", -1, -1, -1)),
			Map.of("cryptic-1461", Set.of("God Wars Dungeon")));
		assertEquals(1, p.size(), p.toString());
		assertTrue(p.get(0).contains("no Wiki area 'Wilderness God Wars Dungeon'"));
	}

	@Test
	void badLinesAreRefused()
	{
		assertThrows(IllegalArgumentException.class, () -> parse("a\tarea-shift\t80,40 Wilderness God Wars Dungeon\tsrc"), "shift needs dx,dy,plane");
		assertThrows(IllegalArgumentException.class, () -> parse("a\tarea-shift\t80,40,0\tsrc"), "shift needs a location");
		assertThrows(IllegalArgumentException.class, () -> parse("a\tarea-shift\t80,40,5 Somewhere\tsrc"), "shift plane 0-3");
		assertThrows(IllegalArgumentException.class, () -> parse("a\tnote\t-\tsrc"), "a note needs text");
		assertThrows(IllegalArgumentException.class, () -> parse("*dig\tnote\tDig.\tsrc"), "the dig rule only takes needs");
		assertThrows(IllegalArgumentException.class, () -> ClueOverrides.parse(List.of("key\tchange")), "wrong header");
		assertThrows(IllegalArgumentException.class, () -> parse("a\ttile\t1,2\tsrc"), "tile needs x,y,plane");
		assertThrows(IllegalArgumentException.class, () -> parse("a\ttile\t1,2,4\tsrc"), "plane 0-3");
		assertThrows(IllegalArgumentException.class, () -> parse("a\tmove\t1,2,0\tsrc"), "unknown change");
		assertThrows(IllegalArgumentException.class, () -> parse("a\tneeds\t-\tsrc"), "needs without a line");
		assertThrows(IllegalArgumentException.class, () -> parse("a\tneeds\titem 952\t-"), "no source");
		assertThrows(IllegalArgumentException.class, () -> parse("a\tretired\tyes\tsrc"), "retired takes no value");
		assertThrows(IllegalArgumentException.class, () -> parse("a\tarea-plane\tGod Wars Dungeon\tsrc"), "area-plane without a plane");
		assertThrows(IllegalArgumentException.class, () -> parse("*dig\ttile\t1,2,0\tsrc"), "the dig rule only takes needs");
		assertThrows(IllegalArgumentException.class, () -> parse("a\tneeds\titem 952"), "too few cells");
	}

	@Test
	void problemsNameLinesThatDontFit()
	{
		ClueOverrides o = parse("cryptic-1\ttile\t1,2,0\tsrc", "cryptic-404\tretired\t-\tsrc", "cryptic-2\tskip-area\tNowhere\tsrc",
			"cryptic-2\tskip-area\tTaverley Dungeon\tsrc", "*dig\tneeds\titem 952\tsrc");
		List<String> p = o.problems(List.of(step("cryptic-1", "cryptic", "npc", 1, 2, 0), step("cryptic-2", "cryptic", "kill", -1, -1, -1)),
			Map.of("cryptic-2", Set.of("Taverley Dungeon")));
		assertEquals(3, p.size(), p.toString());
		assertTrue(p.get(0).contains("already has tile"));
		assertTrue(p.get(1).contains("no step cryptic-404"));
		assertTrue(p.get(2).contains("no Wiki area 'Nowhere'"));
	}

	@Test
	void missingFileIsNoOverrides() throws Exception
	{
		assertSame(ClueOverrides.NONE, ClueOverrides.read(new File("no-such-overrides.tsv")));
		assertTrue(ClueOverrides.NONE.entries().isEmpty());
	}

	private static File repo(String path)
	{
		File f = new File("../" + path);
		return f.exists() ? f : new File(path);
	}

	/** The real file: every line reads, fits the real steps and Wiki areas, and every needs line is a valid requirement line. */
	@Test
	void theRealFileFitsTheData() throws Exception
	{
		File steps = repo("data/clues/clue-steps.tsv");
		if (!steps.isFile())
		{
			return;
		}
		ClueOverrides o = ClueOverrides.read(repo("data/clues/clue-overrides.tsv"));
		assertFalse(o.entries().isEmpty());
		List<ClueStep> all = ClueFile.read(steps);
		List<String[]> spawns = KillAreas.readLines(repo("data/wiki/clue-kill-spawns.tsv"));
		assertEquals(List.of(), o.problems(all, KillAreas.locations(spawns)));
		RequirementText.ItemGroups groups = ItemGroupFile.lookup();
		for (ClueStep s : o.apply(all))
		{
			String line = o.needsLine(s);
			RequirementText.parse(line, groups); // throws on a bad line or an unknown item group
		}
		assertEquals(all.size() - 5, o.apply(all).size(), "the five retired steps");
	}

	/** Every clue spot (target or STASH unit) inside a dark area box has that area's light requirement. */
	@Test
	void everySpotInADarkAreaNeedsItsLight() throws Exception
	{
		File steps = repo("data/clues/clue-steps.tsv");
		File dark = repo("data/clues/dark-areas.tsv");
		if (!steps.isFile() || !dark.isFile())
		{
			return;
		}
		ClueOverrides o = ClueOverrides.read(repo("data/clues/clue-overrides.tsv"));
		List<String[]> boxes = new ArrayList<>();
		boolean header = true;
		for (String line : Files.readAllLines(dark.toPath(), StandardCharsets.UTF_8))
		{
			if (line.startsWith("#") || line.isBlank())
			{
				continue;
			}
			if (header)
			{
				header = false;
				continue;
			}
			boxes.add(line.split("\t", -1));
		}
		assertEquals(2, boxes.size());
		int inside = 0;
		for (ClueStep s : o.apply(ClueFile.read(steps)))
		{
			List<int[]> tiles = new ArrayList<>();
			if (s.hasTile())
			{
				tiles.add(new int[]{s.x, s.y, s.plane});
			}
			for (ClueStep.Place p : s.places)
			{
				tiles.add(new int[]{p.x, p.y, p.plane});
			}
			for (String[] b : boxes)
			{
				for (int[] t : tiles)
				{
					if (t[0] >= Integer.parseInt(b[1]) && t[1] >= Integer.parseInt(b[2]) && t[0] <= Integer.parseInt(b[3])
						&& t[1] <= Integer.parseInt(b[4]) && t[2] == Integer.parseInt(b[5]))
					{
						inside++;
						assertTrue(o.needs(s).contains(b[6]), s.key + " is in " + b[0] + " but does not need " + b[6]);
					}
				}
			}
		}
		assertTrue(inside >= 3, "the swamp caves emote, its STASH unit and the genie");
	}
}
