package com.bestpath.tool.extract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.data.ChartingTasksDiagnostics;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.script.Opcodes;
import org.junit.jupiter.api.Test;

/** The sea charting tasks and sets from the cache's DB tables (rows shaped as in cache #2735). */
class ChartingExtractorTest
{
	private static Object[][] row(Object... cols)
	{
		Object[][] r = new Object[cols.length][];
		for (int i = 0; i < cols.length; i++)
		{
			r[i] = cols[i] == null ? null : new Object[]{cols[i]};
		}
		return r;
	}

	/** The game's rules as in cache 2735 (script 9001's levels, enums 5868-5870 for the values used here). */
	private static ChartingExtractor.Rules rules()
	{
		return new ChartingExtractor.Rules(Map.of(158, 12, 157, 22, 160, 38, 159, 57),
			Map.of(9270, 24, 9271, 40, 9273, 66, 9276, 72, 9277, 78),
			kind -> kind == 155 ? 350 : kind == 157 ? 1250 : 250,
			level -> level == 5 ? 1900 : level == 22 ? 5100 : 1700,
			row -> row == 9281 ? 50000 : row == 9287 ? 150000 : 250);
	}

	private static ChartingTasks sample()
	{
		Map<Integer, Object[][]> seas = new TreeMap<>();
		seas.put(9281, row(0, 0, "Ardent Ocean", 1, null, 38, null, null));
		seas.put(9289, row(2, null, "Bay of Sarim", 0, 9281, 1, 5, 50302057));
		seas.put(9287, row(76, 6, "Miscellaneous", 3, null, 57, null, null));
		seas.put(9359, row(78, null, "Bonus Currents", 2, 9287, 22, null, null));
		Map<Integer, Object[][]> hazards = new TreeMap<>();
		hazards.put(9277, row(5, "Icy seas"));
		Map<Integer, Object[][]> docks = new TreeMap<>();
		docks.put(8604, row(30, "Port Roberts", "in Port Roberts", null, 50));
		Map<Integer, Object[][]> core = new TreeMap<>();
		// a bonus task: the "Bonus Currents" set first, the sea it is in second
		core.put(7356, row(3, "Test the currents south of the small island in the Bay of Sarim.", 9359, 9289, 1, 157, null, null));
		core.put(7594, row(0, "Look at the port task board in Port Sarim.", 9289, null, null, 155, 9277, 8604));
		core.put(7600, row(9, "Find a rock.", 9289, null, null, 155, null, 8604));
		return ChartingExtractor.build(core, seas, hazards, docks, rules(), tools());
	}

	/** DB table 161 as in cache 2735. */
	private static Map<Integer, Object[][]> tools()
	{
		Map<Integer, Object[][]> t = new TreeMap<>();
		t.put(8009, row("Captain's log", null, 31986, 0));
		t.put(8010, row("Spyglass", null, 31803, 1));
		t.put(8011, row("Current duck", null, 31805, 2));
		t.put(8012, row("Crowbar", null, 31807, 3));
		Object[][] diving = row("Diving gear", null, null, 4);
		diving[2] = new Object[]{7534, 7535};
		t.put(8013, diving);
		return t;
	}

	@Test
	void tasksComeInNumberOrderWithVarbitLevelAndXp()
	{
		List<ChartingTasks.Task> l = sample().getTasks();
		assertEquals(3, l.size());
		ChartingTasks.Task board = l.get(0);
		assertEquals(0, board.getNumber());
		assertEquals(18574, board.getVarbit(), "SAILING_CHARTING_GENERIC_BOARD_PORT_SARIM_COMPLETE");
		assertEquals("generic", board.getKind());
		assertEquals("Bay of Sarim", board.getSet());
		assertEquals("Bay of Sarim", ChartingTasksDiagnostics.sea(board));
		assertEquals("Icy seas", board.getHazard());
		assertEquals("Port Roberts", ChartingTasksDiagnostics.dock(board));
		assertEquals(78, board.getLevel(), "the highest of kind 1, Icy seas 78, dock 50");
		assertEquals(35, board.getXp(), "enum 5868 / 10");
		assertFalse(ChartingTasksDiagnostics.repeat(board));
		assertFalse(board.hasPlace(), "no tiles in the cache");
		assertEquals(50, l.get(2).getLevel(), "the dock's level");
	}

	@Test
	void aBonusTaskCountsInItsBonusSet()
	{
		ChartingTasks.Task duck = sample().getTasks().get(1);
		assertEquals(18577, duck.getVarbit(), "SAILING_CHARTING_CURRENT_DUCK_FAIRY_RING_COMPLETE");
		assertEquals("current", duck.getKind());
		assertEquals("Bonus Currents", duck.getSet());
		assertEquals("Bay of Sarim", ChartingTasksDiagnostics.sea(duck));
		assertEquals(22, duck.getLevel());
		assertEquals(125, duck.getXp());
		assertTrue(ChartingTasksDiagnostics.repeat(duck));
		assertEquals("Current duck", duck.getTool(), "client script 8706's tool for kind 157");
		assertArrayEquals(new int[]{31805}, duck.getToolItems());
		assertEquals("", sample().getTasks().get(0).getTool(), "a generic task needs no tool");
	}

	@Test
	void divingGearIsNamedButNotChecked()
	{
		assertEquals("Diving gear", ChartingExtractor.toolName(160));
		Map<Integer, Object[][]> core = new TreeMap<>();
		core.put(1, row(7, "Dive with a mermaid.", 9289, null, null, 160, null, null));
		Map<Integer, Object[][]> seas = new TreeMap<>();
		seas.put(9289, row(2, null, "Bay of Sarim", 0, 9281, 1, 5, 50302057));
		ChartingTasks.Task dive = ChartingExtractor.build(core, seas, Map.of(), Map.of(), rules(), tools()).getTasks().get(0);
		assertEquals("Diving gear", dive.getTool());
		assertEquals(0, dive.getToolItems().length);
		assertEquals(38, dive.getLevel());
	}

	@Test
	void setsComeInTheGamesOrderWithCompletionXp()
	{
		List<ChartingTasks.Set> sets = sample().getSets();
		List<String> names = new ArrayList<>();
		sets.forEach(s -> names.add(s.getName()));
		assertEquals(List.of("Ardent Ocean", "Bay of Sarim", "Miscellaneous", "Bonus Currents"), names);
		assertEquals(5000, sets.get(0).getXp(), "an ocean: enum 5869 by its row / 10");
		assertEquals(ChartingTasks.OCEAN, sets.get(0).getType());
		assertEquals(190, sets.get(1).getXp(), "a sea: enum 5870 at its experience level 5 / 10");
		assertEquals("Ardent Ocean", sets.get(1).getOcean());
		assertEquals(ChartingTasks.BONUS, sets.get(3).getType());
		assertEquals(510, sets.get(3).getXp(), "at its level 22");
	}

	@Test
	void placesAndTipsAreAdded()
	{
		ChartingTasks t = ChartingExtractor.withPlaces(sample(), List.of("# where each task is", "task\tplaces\tsource\ttip",
			"3\t3082,3201,0 3033,3152,0\tQuest Helper ChartingTasksData.java a526461\tFollow the duck."));
		assertFalse(t.getTasks().get(0).hasPlace(), "not listed: left as it was");
		ChartingTasks.Task duck = t.getTasks().get(1);
		assertArrayEquals(new int[]{Tiles.pack(3082, 3201, 0), Tiles.pack(3033, 3152, 0)}, ChartingTasksDiagnostics.places(duck));
		assertEquals("Follow the duck.", duck.getTip());
		assertEquals(22, duck.getLevel(), "the rest kept");
		assertEquals(4, t.getSets().size());
		assertThrows(IllegalArgumentException.class, () -> ChartingExtractor.withPlaces(sample(), List.of("3\t3082,3201")));
	}

	@Test
	void anUnknownKindIsAnError()
	{
		Map<Integer, Object[][]> core = new TreeMap<>();
		core.put(1, row(0, "text", 1, null, null, 999, null, null));
		assertThrows(IllegalStateException.class, () -> ChartingExtractor.build(core, Map.of(), Map.of(), Map.of(), rules(), tools()));
	}

	/** Script 9001's shape: two switches, each case setting the level with a constant. */
	@Test
	void theLevelSwitchesAreReadFromTheScript()
	{
		ScriptDefinition s = new ScriptDefinition();
		// 0 switch(table 0) 1 jump 2 iconst 12 3 switch(table 1) 4 jump 5 iconst 78
		s.setInstructions(new int[]{Opcodes.SWITCH, Opcodes.JUMP, Opcodes.ICONST, Opcodes.SWITCH, Opcodes.JUMP, Opcodes.ICONST});
		s.setIntOperands(new int[]{0, 0, 12, 1, 0, 78});
		@SuppressWarnings("unchecked")
		Map<Integer, Integer>[] sw = new Map[]{Map.of(158, 1), Map.of(9277, 1)};
		s.setSwitches(sw);
		Map<Integer, Integer>[] levels = ChartingExtractor.levelSwitches(s);
		assertEquals(Map.of(158, 12), levels[0]);
		assertEquals(Map.of(9277, 78), levels[1]);
		s.setIntOperands(new int[]{0, 0, 12, 1, 0, 78});
		@SuppressWarnings("unchecked")
		Map<Integer, Integer>[] wrong = new Map[]{Map.of(158, 0), Map.of(9277, 1)};
		s.setSwitches(wrong);
		assertThrows(IllegalStateException.class, () -> ChartingExtractor.levelSwitches(s), "a case landing on a jump");
	}

	@Test
	void questHelperEntriesGiveTipsAndKeepTheLongerOne()
	{
		String src = "new ChartingTaskDefinition(ChartingType.CURRENT, \"Test the currents south of the small island in the Bay of"
			+ " Sarim.\", new WorldPoint(3082, 3201, 0), \"\", 22, VarbitID.A_COMPLETE),\n"
			+ "new ChartingTaskDefinition(ChartingType.CURRENT, \"Test the currents south of the small island in the Bay of"
			+ " Sarim.\", new WorldPoint(3082, 3201, 0), new WorldPoint(3033, 3152, 0), \"\", 22, VarbitID.A_COMPLETE),\n"
			+ "new ChartingTaskDefinition(ChartingType.GENERIC, \"[Bonus] Look at the port task board in Port Sarim. You'll get"
			+ " a tip.\", new WorldPoint(3030, 3198, 0), \"Ardent Ocean\", 1, VarbitID.B_COMPLETE)";
		List<ChartingPlacesCli.Entry> entries = ChartingPlacesCli.entries(src);
		assertEquals(3, entries.size());
		List<String> notes = new ArrayList<>();
		List<String> lines = ChartingPlacesCli.lines(entries, sample(),
			n -> n.equals("A_COMPLETE") ? 18577 : n.equals("B_COMPLETE") ? 18574 : -1, "Quest Helper x", notes);
		assertEquals(List.of("0\t3030,3198,0\tQuest Helper x\tYou'll get a tip.",
			"3\t3082,3201,0 3033,3152,0\tQuest Helper x\t"), lines);
		assertTrue(notes.contains("task 0: Quest Helper's level 1, the game's rule 78 (the game's is used)"), notes.toString());
		assertTrue(notes.contains("task 9 is not in Quest Helper's list (no place)"), notes.toString());
	}

	/** Each drink crate gets its drink (an item the cache doesn't name is left out). */
	@Test
	void drinkCratesGetTheirDrinks()
	{
		ChartingTasks t = new ChartingTasks(List.of(), List.of(
			new ChartingTasks.Task(38, 18612, "crate", "Bay of Sarim", "", 12, 75, "", "", false, new int[0], "Crate.", "", "", new int[0], -1, ""),
			new ChartingTasks.Task(39, 18613, "crate", "Bay of Sarim", "", 12, 75, "", "", false, new int[0], "Crate.", "", "", new int[0], -1, ""),
			new ChartingTasks.Task(1, 18575, "generic", "Bay of Sarim", "", 1, 35, "", "", false, new int[0], "Find.", "", "", new int[0], -1, "")));
		ChartingTasks d = ChartingExtractor.withDrinks(t, List.of("# comment", "varbit\titem\tname", "18612\t31831\tX",
			"18613\t99999\tY"), id -> id == 31831 ? "Smuggled rum" : null);
		assertEquals(31831, d.getTasks().get(0).getDrink());
		assertEquals("Smuggled rum", d.getTasks().get(0).getDrinkName());
		assertEquals(-1, d.getTasks().get(1).getDrink());
		assertEquals(-1, d.getTasks().get(2).getDrink());
	}
}
