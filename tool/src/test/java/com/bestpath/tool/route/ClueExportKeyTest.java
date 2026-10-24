package com.bestpath.tool.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bestpath.tool.clue.ClueStep;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A key step's export names the NPC that drops the key (the cache's tile) and the key. */
class ClueExportKeyTest
{
	private static ClueStep step(String action, List<ClueStep.Place> places, List<String> needs)
	{
		return new ClueStep("cryptic-2279", "cryptic", "medium", List.of(7296), "The dead, red dragon watches over this chest.", "key",
			List.of(375), "Closed chest", 3353, 3332, 0, "Kill a MALE Barbarian ...", places, needs, List.of(), "cache 2735", List.of(),
			action, "item 7297", "");
	}

	@Test
	void keyStepGivesItsNpcAndKey()
	{
		ClueStep.Place npc = new ClueStep.Place("key-npc", List.of(3055, 3056), "Barbarian / Hunding", 3082, 3419, 0);
		ClueStep s = step("key", List.of(new ClueStep.Place("stash", List.of(1), "Bush", 1, 2, 0), npc),
			List.of("item 7297 Key (medium) (the key)"));
		assertEquals(npc, ClueExport.keyNpc(s));
		assertEquals(7297, ClueExport.keyItem(s));
	}

	@Test
	void otherStepsHaveNone()
	{
		ClueStep.Place npc = new ClueStep.Place("key-npc", List.of(3055), "Barbarian", 3082, 3419, 0);
		assertNull(ClueExport.keyNpc(step("search", List.of(npc), List.of("item 7297 Key (medium) (the key)"))), "not a key step");
		assertNull(ClueExport.keyNpc(step("key", List.of(), List.of())), "no key-NPC place in the cache");
		assertEquals(-1, ClueExport.keyItem(step("key", List.of(npc), List.of("item 952 Spade"))), "no key among the needs");
	}
}
