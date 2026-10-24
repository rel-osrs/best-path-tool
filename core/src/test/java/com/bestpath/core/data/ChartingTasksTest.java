package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.path.Tiles;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The sea charting tasks file: written and read back the same; sets and oceans. */
class ChartingTasksTest
{
	static ChartingTasks sample()
	{
		return new ChartingTasks(List.of(
			new ChartingTasks.Set("Ardent Ocean", ChartingTasks.OCEAN, "", 5000),
			new ChartingTasks.Set("Kharidian Sea", ChartingTasks.SEA, "Ardent Ocean", 190),
			new ChartingTasks.Set("Bay of Sarim", ChartingTasks.SEA, "Ardent Ocean", 190),
			new ChartingTasks.Set("Miscellaneous", ChartingTasks.OCEAN, "", 15000),
			new ChartingTasks.Set("Bonus Currents", ChartingTasks.BONUS, "Miscellaneous", 510)),
			List.of(
				new ChartingTasks.Task(0, 18574, "generic", "Bay of Sarim", "", 1, 35, "", "", false,
					new int[]{Tiles.pack(3030, 3198, 0)}, "Look at the port task board in Port Sarim.", "", "", new int[0], -1, ""),
				new ChartingTasks.Task(3, 18577, "current", "Bonus Currents", "Bay of Sarim", 22, 125, "", "", true,
					new int[]{Tiles.pack(3082, 3201, 0), Tiles.pack(3033, 3152, 0)}, "Test the currents.", "Follow the duck.", "Current duck",
					new int[]{31805}, -1, ""),
				new ChartingTasks.Task(13, 18587, "spyglass", "Kharidian Sea", "", 78, 50, "Icy seas", "Port Roberts", false,
					new int[0], "Use your spyglass.", "", "Spyglass", new int[]{31803}, -1, "")));
	}

	@Test
	void textComesBackTheSame()
	{
		ChartingTasks t = sample();
		String text = ChartingTasksWriter.write(t, "a comment\nsecond line");
		assertTrue(text.startsWith("# a comment\n# second line\n" + ChartingTasks.SET_HEADER + "\nset\tArdent Ocean\tocean\t\t5000\n"));
		ChartingTasks back = ChartingTasks.parse(Arrays.asList(text.split("\n")));
		assertEquals(text, ChartingTasksWriter.write(back, "a comment\nsecond line"));
		assertEquals(5, back.getSets().size());
		assertEquals(510, back.set("Bonus Currents").getXp());
		assertEquals("Miscellaneous", back.set("Bonus Currents").getOcean());
		ChartingTasks.Task c = back.getTasks().get(1);
		assertEquals(3, c.getNumber());
		assertEquals(18577, c.getVarbit());
		assertEquals("Bonus Currents", c.getSet());
		assertEquals("Bay of Sarim", c.sea);
		assertTrue(ChartingTasksDiagnostics.isBonus(c));
		assertEquals(22, c.getLevel());
		assertEquals(125, c.getXp());
		assertTrue(c.repeat);
		assertEquals("Follow the duck.", c.getTip());
		assertEquals("Current duck", c.getTool());
		assertArrayEquals(new int[]{31805}, c.getToolItems());
		assertEquals("", back.getTasks().get(0).getTool());
		assertEquals(0, back.getTasks().get(0).getToolItems().length);
		assertArrayEquals(new int[]{Tiles.pack(3082, 3201, 0), Tiles.pack(3033, 3152, 0)}, c.places);
		assertEquals(Tiles.pack(3082, 3201, 0), c.getPlace(), "the start");
		ChartingTasks.Task board = back.getTasks().get(0);
		assertEquals("Bay of Sarim", board.sea, "a sea's task is in its set");
		assertFalse(ChartingTasksDiagnostics.isBonus(board));
		ChartingTasks.Task s = back.getTasks().get(2);
		assertFalse(s.hasPlace());
		assertEquals(-1, s.getPlace());
		assertEquals("Icy seas", s.getHazard());
		assertEquals("Port Roberts", s.dock);
	}

	@Test
	void anOceanHoldsItsSeasTasks()
	{
		ChartingTasks t = sample();
		assertEquals(List.of(0, 13), numbers(t.tasksIn("Ardent Ocean")));
		assertEquals(List.of(3), numbers(t.tasksIn("Miscellaneous")), "the bonus sets' ocean");
		assertEquals(List.of(0), numbers(t.tasksIn("Bay of Sarim")), "a bonus task counts in its bonus set only");
		assertEquals(List.of(3), numbers(t.tasksIn("Bonus Currents")));
		assertEquals(3, t.tasksIn(null).size());
		assertEquals("Ardent Ocean", t.oceanOf(t.getTasks().get(0)));
		Map<String, List<ChartingTasks.Task>> sets = ChartingTasksDiagnostics.bySet(t);
		assertEquals(List.of("Kharidian Sea", "Bay of Sarim", "Bonus Currents"), List.copyOf(sets.keySet()), "the sets' order");
	}

	@Test
	void varbitRangeCoversEveryTask()
	{
		assertArrayEquals(new int[]{18574, 18587}, sample().varbitRange());
		assertArrayEquals(new int[]{-1, -1}, ChartingTasks.NONE.varbitRange());
	}

	@Test
	void badLinesNameTheirNumber()
	{
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
			() -> ChartingTasks.parse(List.of(ChartingTasks.HEADER, "1\t2\tgeneric")));
		assertTrue(e.getMessage().contains("line 2"), e.getMessage());
		e = assertThrows(IllegalArgumentException.class,
			() -> ChartingTasks.parse(List.of("x\t18574\tgeneric\tSea\t\t1\t35\t\t\t\t\tText\t")));
		assertTrue(e.getMessage().contains("line 1"), e.getMessage());
		e = assertThrows(IllegalArgumentException.class, () -> ChartingTasks.parse(List.of("set\tSea\tsea")));
		assertTrue(e.getMessage().contains("line 1"), e.getMessage());
	}

	private static List<Integer> numbers(List<ChartingTasks.Task> l)
	{
		return l.stream().map(ChartingTasks.Task::getNumber).collect(java.util.stream.Collectors.toList());
	}

	/** A drink crate's drink is kept with its task. */
	@Test
	void aDrinkCratesDrinkIsReadAndWritten()
	{
		ChartingTasks t = new ChartingTasks(List.of(new ChartingTasks.Set("Bay of Sarim", ChartingTasks.SEA, "Ardent Ocean", 190)),
			List.of(new ChartingTasks.Task(38, 18612, "crate", "Bay of Sarim", "", 12, 75, "", "", false, new int[0],
				"Find a sealed crate.", "", "Crowbar", new int[]{31807}, 31831, "Smuggled rum")));
		ChartingTasks back = ChartingTasks.parse(java.util.Arrays.asList(ChartingTasksWriter.write(t, "c").split("\n")));
		assertEquals(31831, back.getTasks().get(0).getDrink());
		assertEquals("Smuggled rum", back.getTasks().get(0).getDrinkName());
		assertEquals(31831, ChartingTasksBuilder.withPlaces(back.getTasks().get(0), new int[0], "tip").getDrink());
		assertEquals(-1, ChartingTasks.parse(List.of("1\t18575\tgeneric\tBay of Sarim\t\t1\t35\t\t\t\t\tFind it.\t\t\t"))
			.getTasks().get(0).getDrink());
	}
}
