package com.bestpath.clue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.data.ClueData;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteStep;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * A key step without its key first routes to the NPC to kill for it, then to the clue location: it gets a kill stop
 * at the key NPC.
 */
public class KeyStopTest
{
	private static final int NPC = Tiles.pack(3082, 3419, 0);
	private static final ClueData.Step KEY_STEP = new ClueData.Step("cryptic-2279", "cryptic", "medium", "key", new int[]{7296},
		new int[]{Tiles.pack(3353, 3332, 0)}, -1, "The dead, red dragon watches over this chest. He must really dig the view.",
		"Search the chest underneath the Red Dragon's head in the Exam Centre. Kill a MALE Barbarian in Barbarian Village or"
			+ " Barbarian Outpost to receive the key.", "", "item 7297", "", "key Closed chest", -1, "", NPC, "Barbarian", 7297);

	private static PlayerState.Builder player()
	{
		return new PlayerState.Builder("test").members(true);
	}

	@Test
	public void noKeyKillTheNpcFirst()
	{
		RouteRequest.Stop s = KeyStop.stop(DetectedClueTestAccess.step(KEY_STEP), player().build(), id -> "Key (medium)");
		assertNotNull(s);
		assertEquals(RouteStep.Kind.KILL, s.getKind());
		assertEquals(NPC, s.getTile());
		assertEquals("Kill for the key: Barbarian", s.getTitle());
		assertEquals(KeyStop.STOP_PREFIX + "cryptic-2279", s.getId());
		assertEquals(List.of(KEY_STEP.getDescription(), "Pick up the Key (medium) it drops; then the route goes on to the clue."),
			s.getLines());
	}

	@Test
	public void keyCarriedOrBankedNoStop()
	{
		DetectedClue d = DetectedClueTestAccess.step(KEY_STEP);
		assertNull("key in the inventory: straight to the chest", KeyStop.stop(d, player().carry(7297, 1).build(), null));
		assertNull("key in the bank: the bank stop for the needs gets it", KeyStop.stop(d, player().bank(7297, 1).build(), null));
		assertNull("nothing known about the player", KeyStop.stop(d, null, null));
	}

	@Test
	public void otherStepsNoStop()
	{
		ClueData.Step talk = new ClueData.Step("anagram-1499", "anagram", "beginner", "npc", new int[0],
			new int[]{Tiles.pack(3315, 3163, 0)}, -1, "An Earl", "", "", "", "", "talk Ranael", -1, "", -1, "", -1);
		assertNull(KeyStop.stop(DetectedClueTestAccess.step(talk), player().build(), null));
		assertNull(KeyStop.stop(DetectedClue.NONE, player().build(), null));
		// an older export without the key-NPC columns: no stop (the route goes to the chest as before)
		ClueData.Step old = new ClueData.Step("cryptic-2279", "cryptic", "medium", "key", new int[]{7296},
			new int[]{Tiles.pack(3353, 3332, 0)}, -1, "", "", "", "item 7297", "", "key Closed chest", -1, "", -1, "", -1);
		assertNull(KeyStop.stop(DetectedClueTestAccess.step(old), player().build(), null));
	}

	@Test
	public void noNameNoDescription()
	{
		ClueData.Step bare = new ClueData.Step("cryptic-9", "cryptic", "medium", "key", new int[0],
			new int[]{Tiles.pack(3353, 3332, 0)}, -1, "", "", "", "", "", "key Drawers", -1, "", NPC, "", 7297);
		RouteRequest.Stop s = KeyStop.stop(DetectedClueTestAccess.step(bare), player().build(), id -> null);
		assertEquals("Kill for the key: the monster", s.getTitle());
		assertEquals(List.of("Pick up the key it drops; then the route goes on to the clue."), s.getLines());
	}

	/**
	 * All clues of this type work: every key step in the plugin's data,
	 * found from its clue item as in the game, gets the kill stop at its key NPC while the key is not owned.
	 */
	@Test
	public void everyKeyStepInTheDataGetsItsKillStop() throws Exception
	{
		InputStream in = KeyStopTest.class.getResourceAsStream("/com/bestpath/data/clues.tsv");
		if (in == null)
		{
			return; // no exported data
		}
		ClueData data;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			data = ClueData.parse(r.lines().collect(Collectors.toList()));
		}
		int keySteps = 0;
		for (ClueData.Step s : data.getSteps())
		{
			if (!"key".equals(s.getTarget()))
			{
				continue;
			}
			keySteps++;
			assertTrue(s.getKey() + ": a clue item", s.getItems().length > 0);
			DetectedClue d = new ClueWatcher(data).onInventory(List.of(s.getItems()[0]), id -> -1);
			assertNotNull(s.getKey(), d);
			assertEquals(s.getKey(), DetectedClue.Kind.STEP, d.getKind());
			assertEquals(s.getKey(), List.of(s), d.getSteps());
			RouteRequest.Stop stop = KeyStop.stop(d, player().build(), id -> null);
			assertNotNull(s.getKey() + ": kill stop", stop);
			assertEquals(s.getKey(), s.getKeyNpc(), stop.getTile());
			assertEquals(s.getKey(), RouteStep.Kind.KILL, stop.getKind());
			assertNull(s.getKey() + ": key carried", KeyStop.stop(d, player().carry(s.getKeyItem(), 1).build(), null));
		}
		assertEquals("key steps in clues.tsv", 12, keySteps);
	}
}
