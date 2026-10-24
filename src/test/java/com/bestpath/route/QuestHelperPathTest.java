package com.bestpath.route;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.path.Tiles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** Listening to Quest Helper's path requests. */
public class QuestHelperPathTest
{
	@Test
	public void questHelpersTargetBecomesARoute()
	{
		// as Quest Helper sends it: "start" (where the player is) and "target" (the step's place)
		Map<String, Object> data = new HashMap<>();
		data.put("start", new WorldPoint(3222, 3218, 0));
		data.put("target", new WorldPoint(3208, 3220, 2));
		RouteRequest r = QuestHelperPath.request(data);
		assertArrayEquals(new int[]{Tiles.pack(3208, 3220, 2)}, r.getTargets());
		assertEquals(QuestHelperPath.TITLE, r.getTitle());
		assertTrue(QuestHelperPath.isQuestRoute(r));
		assertEquals(List.of("Sent by another plugin: 3208, 3220, 2."), r.getNotes());
	}

	@Test
	public void severalTargetsMeanTheNearest()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("target", List.of(new WorldPoint(3200, 3200, 0), new WorldPoint(3300, 3300, 0), new WorldPoint(3200, 3200, 0)));
		RouteRequest r = QuestHelperPath.request(data);
		assertEquals(2, r.targetCount());
	}

	@Test
	public void leftAloneWhileBestPathRoutesAClue()
	{
		// Shortest Clue sends the clue's place the same way as Quest Helper
		assertTrue(QuestHelperPath.heldBack(true, true));
		assertFalse("no clue carried: followed", QuestHelperPath.heldBack(false, true));
		assertFalse("Best Path doesn't route the clue: followed", QuestHelperPath.heldBack(true, false));
	}

	@Test
	public void nothingToRouteTo()
	{
		assertNull(QuestHelperPath.request(null));
		assertNull(QuestHelperPath.request(new HashMap<>()));
		Map<String, Object> packed = new HashMap<>();
		packed.put("target", 12345); // Shortest Path's own packed tiles are not read
		assertNull(QuestHelperPath.request(packed));
		Map<String, Object> outside = new HashMap<>();
		outside.put("target", new WorldPoint(-1, 3200, 0));
		assertNull(QuestHelperPath.request(outside));
		assertFalse(QuestHelperPath.isQuestRoute(new RouteRequest(3200, 3200, 0)));
	}
}
