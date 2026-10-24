package com.bestpath.tool.extract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.data.ChartingTasksDiagnostics;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Boat or land spot for each charting task: open-sea places are boat spots, the others get Boat / Land cards in the Data
 * Tool with the automatic guess pre-set, and the guess is used until the card is answered.
 */
class ChartingSpotsTest
{
	/** The ocean: x 100 and up. Ground: x 90 to 97 (a beach), and a cave at y 9000 and up. */
	private static final ChartingExtractor.Ocean OCEAN = (x, y) -> x >= 100 && y < 9000;
	private static final ChartingSpots.Ground GROUND = (x, y, plane) -> x >= 90 && x <= 97 || y >= 9000;

	private static ChartingTasks.Task task(int n, int x, int y, int plane)
	{
		return new ChartingTasks.Task(n, 18574 + n, "generic", "Bay", "", 1, 35, "", "", false, new int[]{Tiles.pack(x, y, plane)},
			"Task " + n + ".", "", "", new int[0], -1, "");
	}

	private static final ChartingTasks TASKS = new ChartingTasks(List.of(), List.of(
		task(0, 120, 50, 0), // at sea
		task(1, 98, 50, 0), // an object on the shore, ground 1 tile away
		task(2, 99, 70, 0), // ... and ground not within 2 tiles? 99 - 97 = 2: within
		task(3, 50, 50, 0), // far inland, no ground near (a cliff top): guess boat, no sea near
		task(4, 120, 9400, 1))); // a cave

	@Test
	void openSeaIsABoatSpotAndTheOthersAreGuessed()
	{
		List<ChartingSpots.Spot> spots = ChartingSpots.classify(TASKS, OCEAN, GROUND);
		assertTrue(spots.get(0).onSea);
		assertTrue(spots.get(0).boat(Map.of()));
		assertFalse(spots.get(1).onSea);
		assertFalse(spots.get(1).guessBoat, "ground next to it: land");
		assertFalse(spots.get(2).guessBoat, "ground 2 tiles away: land");
		assertTrue(spots.get(3).guessBoat, "no ground near: boat");
		assertEquals(-1, spots.get(3).sea);
		assertFalse(spots.get(4).guessBoat, "a cave: ground");
		assertEquals(Tiles.pack(100, 50, 0), spots.get(1).sea);
	}

	@Test
	void boatSpotsGoOntoTheSeaAndAnswersWin()
	{
		List<ChartingSpots.Spot> spots = ChartingSpots.classify(TASKS, OCEAN, GROUND);
		List<String> notes = new ArrayList<>();
		ChartingTasks guessed = ChartingSpots.apply(TASKS, spots, Map.of(), notes);
		assertArrayEquals(new int[]{Tiles.pack(120, 50, 0)}, ChartingTasksDiagnostics.places(guessed.getTasks().get(0)));
		assertArrayEquals(new int[]{Tiles.pack(98, 50, 0)}, ChartingTasksDiagnostics.places(guessed.getTasks().get(1)), "land: Quest Helper's place");
		assertArrayEquals(new int[]{Tiles.pack(50, 50, 0)}, ChartingTasksDiagnostics.places(guessed.getTasks().get(3)), "boat, no sea near: as it is");
		assertEquals(1, notes.size());
		// the card answers: task 1 is done from a boat; task 0 can't be answered (it's on the sea)
		ChartingTasks answered = ChartingSpots.apply(TASKS, spots, Map.of(1, true, 0, false), new ArrayList<>());
		assertArrayEquals(new int[]{Tiles.pack(100, 50, 0)}, ChartingTasksDiagnostics.places(answered.getTasks().get(1)));
		assertArrayEquals(new int[]{Tiles.pack(120, 50, 0)}, ChartingTasksDiagnostics.places(answered.getTasks().get(0)));
		assertTrue(spots.get(0).boat(Map.of(0, false)), "open sea stays a boat spot");
	}
}
