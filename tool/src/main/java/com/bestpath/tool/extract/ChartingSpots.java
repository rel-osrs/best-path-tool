package com.bestpath.tool.extract;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.data.ChartingTasksBuilder;
import com.bestpath.core.data.ChartingTasksDiagnostics;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Whether each sea charting task is done from a boat or on land. Places on open sea are boat spots; every other task
 * gets a Boat / Land card in the Data Tool with the automatic guess pre-set, and the guess is used until the card is
 * answered.
 * <ul>
 * <li>Quest Helper's place on main ocean water: a boat spot, no card.</li>
 * <li>Otherwise a card; until it is answered, the guess: land when there is ground to stand on within
 * {@value #LAND_REACH} tiles of the place (the object can be reached on foot), else boat.</li>
 * </ul>
 * A boat spot's place becomes the nearest main ocean tile (a route to open sea ends on the player's boat there, core
 * RouteDestination); a land spot keeps Quest Helper's place (the route walks to the nearest tile it can reach).
 */
public final class ChartingSpots
{
	/** How near ground must be to Quest Helper's place for the guess "land" (tiles, any direction). */
	public static final int LAND_REACH = 2;

	/** Ground the player can stand on: a tile from which a step is possible (the collision map's {@code canStep}). */
	public interface Ground
	{
		boolean standable(int x, int y, int plane);
	}

	/** One task's spot. */
	public static final class Spot
	{
		public final ChartingTasks.Task task;
		/** Quest Helper's place (packed tile). */
		public final int place;
		/** On main ocean water: a boat spot without a card. */
		public final boolean onSea;
		/** The guess when not on the sea: boat (true) or land. */
		public final boolean guessBoat;
		/** The nearest main ocean tile (packed), or -1 for none within {@link ChartingExtractor#BOAT_REACH}. */
		public final int sea;
		/** Why the guess: "ground to stand on next to it", "no ground to stand on within 2 tiles". */
		public final String why;

		Spot(ChartingTasks.Task task, int place, boolean onSea, boolean guessBoat, int sea, String why)
		{
			this.task = task;
			this.place = place;
			this.onSea = onSea;
			this.guessBoat = guessBoat;
			this.sea = sea;
			this.why = why;
		}

		/** Boat or land by the card's answer ({@code answers}: task number -> boat), else the guess. */
		public boolean boat(Map<Integer, Boolean> answers)
		{
			if (onSea)
			{
				return true;
			}
			Boolean a = answers.get(task.getNumber());
			return a != null ? a : guessBoat;
		}
	}

	private ChartingSpots()
	{
	}

	/** Every task with a place, classified (tasks without a place are left out). */
	public static List<Spot> classify(ChartingTasks tasks, ChartingExtractor.Ocean ocean, Ground ground)
	{
		List<Spot> out = new ArrayList<>();
		for (ChartingTasks.Task t : tasks.getTasks())
		{
			if (!t.hasPlace())
			{
				continue;
			}
			int place = t.getPlace();
			int x = Tiles.x(place);
			int y = Tiles.y(place);
			int plane = Tiles.plane(place);
			boolean onSea = plane == 0 && ocean.at(x, y);
			int[] sea = plane == 0 ? ChartingExtractor.nearestOcean(x, y, ocean) : null;
			boolean land = !onSea && groundNear(x, y, plane, ground);
			String why = onSea ? "Quest Helper's spot is on open sea" : land ? "there is ground to stand on next to Quest Helper's spot"
				: "no ground to stand on within " + LAND_REACH + " tiles of Quest Helper's spot";
			out.add(new Spot(t, place, onSea, !land, sea == null ? -1 : Tiles.pack(sea[0], sea[1], 0), why));
		}
		return out;
	}

	/**
	 * The tasks with their places set by the spots: a boat spot's place the nearest main ocean tile (none near: left as
	 * it is, with a line in {@code notes}), a land spot's Quest Helper's place.
	 */
	public static ChartingTasks apply(ChartingTasks tasks, List<Spot> spots, Map<Integer, Boolean> answers, List<String> notes)
	{
		Map<Integer, Spot> byTask = new java.util.HashMap<>();
		for (Spot s : spots)
		{
			byTask.put(s.task.getNumber(), s);
		}
		List<ChartingTasks.Task> out = new ArrayList<>();
		for (ChartingTasks.Task t : tasks.getTasks())
		{
			Spot s = byTask.get(t.getNumber());
			if (s == null || !s.boat(answers) || s.onSea)
			{
				out.add(t);
				continue;
			}
			if (s.sea < 0)
			{
				notes.add("task " + t.getNumber() + " (" + ChartingTasksDiagnostics.sea(t) + "): a boat spot with no open sea within "
					+ ChartingExtractor.BOAT_REACH + " tiles of " + Tiles.x(s.place) + "," + Tiles.y(s.place) + ","
					+ Tiles.plane(s.place) + ", left as it is");
				out.add(t);
				continue;
			}
			int[] places = ChartingTasksDiagnostics.places(t);
			places[0] = s.sea;
			out.add(ChartingTasksBuilder.withPlaces(t, places, t.getTip()));
		}
		return new ChartingTasks(tasks.getSets(), out);
	}

	private static boolean groundNear(int x, int y, int plane, Ground ground)
	{
		for (int dx = -LAND_REACH; dx <= LAND_REACH; dx++)
		{
			for (int dy = -LAND_REACH; dy <= LAND_REACH; dy++)
			{
				if (ground.standable(x + dx, y + dy, plane))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Ground from a collision map: a tile with at least one step out of it. */
	public static Ground ground(com.bestpath.core.collision.CollisionMap map)
	{
		return (x, y, plane) ->
		{
			if (x < 0 || y < 0 || x > 0x3FFF || y > 0x3FFF || plane < 0 || plane > 3)
			{
				return false;
			}
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if ((dx != 0 || dy != 0) && map.canStep(x, y, plane, dx, dy))
					{
						return true;
					}
				}
			}
			return false;
		};
	}
}
