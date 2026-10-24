package com.bestpath.tool.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.data.ChartingTasks;
import com.bestpath.core.path.Tiles;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.extract.ChartingExtractor;
import com.bestpath.tool.extract.ChartingSpots;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Boat / Land cards for the sea charting tasks off the open sea, the guess pre-set. */
class ChartingSpotCardsTest
{
	private static ChartingSpots.Spot spot()
	{
		ChartingTasks tasks = new ChartingTasks(List.of(), List.of(new ChartingTasks.Task(0, 18574, "generic", "Bay of Sarim", "", 1,
			35, "", "", false, new int[]{Tiles.pack(98, 50, 0)}, "Look at the port task board in Port Sarim.", "", "", new int[0], -1, "")));
		ChartingExtractor.Ocean ocean = (x, y) -> x >= 100;
		return ChartingSpots.classify(tasks, ocean, (x, y, p) -> x < 100).get(0);
	}

	@Test
	void theCardSaysTheGuessAndPointsAtTheSpot()
	{
		QuestionCards.Question q = ChartingSpotCards.question(spot());
		assertEquals("chart-0", q.id);
		assertEquals(QuestionCards.Action.CHARTING_SPOT, q.action);
		assertFalse(q.guessBoat);
		assertEquals(98, q.x);
		assertArrayEquals(new int[]{Tiles.pack(98, 50, 0), Tiles.pack(100, 50, 0)}, q.places, "the spot and the nearest sea");
		assertTrue(q.text.startsWith("Sea charting task 0 (Bay of Sarim, find): Look at the port task board in Port Sarim."));
		assertTrue(q.text.contains("Guess: land (there is ground to stand on next to Quest Helper's spot)."));
	}

	@Test
	void theAnswerIsAChangeThatCountsForTheTask()
	{
		QuestionCards.Question q = ChartingSpotCards.question(spot());
		Edit boat = ChartingSpotCards.answer(q, "BOAT");
		assertEquals(Edit.Type.CHARTING_SPOT, boat.type);
		assertEquals("0", boat.target);
		assertTrue(boat.on);
		assertEquals("Card: boat (not the guess)", boat.note);
		Edit land = ChartingSpotCards.answer(q, "LAND");
		assertEquals("Card: land (as guessed)", land.note);
		assertEquals(Boolean.FALSE, ManualState.chartingSpots(List.of(boat, land)).get(0), "the last answer counts");
		assertEquals("Sea charting task 0 is done from a boat", boat.describe());
	}
}
