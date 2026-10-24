package com.bestpath.core.data;

/** Changed copies of a sea charting task, used when the data is made (places and tips, a drink crate's drink). */
public final class ChartingTasksBuilder
{
	private ChartingTasksBuilder()
	{
	}

	/** The task with these places and tip (the rest kept). */
	public static ChartingTasks.Task withPlaces(ChartingTasks.Task t, int[] places, String tip)
	{
		return new ChartingTasks.Task(t.number, t.varbit, t.kind, t.set, t.sea, t.level, t.xp, t.hazard, t.dock, t.repeat, places,
			t.text, tip, t.tool, t.toolItems, t.drink, t.drinkName);
	}

	/** The task with this drink (a drink crate's: the item the crate gives, which the player then drinks). */
	public static ChartingTasks.Task withDrink(ChartingTasks.Task t, int item, String name)
	{
		return new ChartingTasks.Task(t.number, t.varbit, t.kind, t.set, t.sea, t.level, t.xp, t.hazard, t.dock, t.repeat,
			t.places, t.text, t.tip, t.tool, t.toolItems, item, name);
	}
}
