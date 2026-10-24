package com.bestpath.clue;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteRequest;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

/**
 * The plugin's own clue detection with made-up inventories, item params and clue texts (no game client):
 * which step a clue item or a clue text means, when a detection is new, and what the sidebar says about it.
 */
public class ClueWatcherTest
{
	private static final int EASY_ITEM = 2677;
	private static final int ELITE_ITEM = 12073;
	/** An item the exported list doesn't have, whose param 623 names a step (e.g. a clue added after the export). */
	private static final int NEW_ITEM = 99001;
	private static final int UNKNOWN_CLUE_ITEM = 99002;
	private static final int BREAD = 2309;

	private static ClueData.Step step(String key, String type, String tier, String target, int[] items, String text, int stash,
		int... targets)
	{
		return new ClueData.Step(key, type, tier, target, items, targets, stash, text, "Description of " + key + ".", "", "", "", "", -1, "", -1, "", -1);
	}

	private static final ClueData DATA = new ClueData(List.of(
		step("cryptic-1300", "cryptic", "easy", "npc", new int[]{EASY_ITEM}, "Talk to Charlie the Tramp in Varrock.", -1,
			Tiles.pack(3209, 3390, 0)),
		step("cryptic-1455", "cryptic", "elite", "kill", new int[]{ELITE_ITEM}, "Kill an Ankou.", -1,
			Tiles.pack(3000, 3000, 0), Tiles.pack(2000, 2000, 0), Tiles.pack(2500, 2500, 1)),
		step("emote-2085", "emote", "medium", "coord", new int[0], "Bow at the Grand Exchange. Equip a cape.", Tiles.pack(3166, 3478, 0),
			Tiles.pack(3164, 3477, 0)),
		step("anagram-1499", "anagram", "beginner", "npc", new int[0], "An Earl", -1, Tiles.pack(3315, 3163, 0)),
		step("cryptic-1236", "cryptic", "beginner", "npc", new int[0], "Talk to Charlie the Tramp in Varrock.", -1,
			Tiles.pack(3209, 3390, 0)),
		step("cryptic-1496", "cryptic", "master", "npc", new int[0], "A man called Jorral may have a clue.", -1,
			Tiles.pack(2437, 3347, 0)),
		step("skillchallenge-1868", "skillchallenge", "master", "none", new int[0], "Combine the torn clue scroll parts.", -1),
		step("hotcold-685", "hotcold", "beginner", "coord", new int[0], "", -1, Tiles.pack(3110, 3150, 0)),
		step("hotcold-700", "hotcold", "master", "coord", new int[0], "", -1, Tiles.pack(2000, 3000, 0)),
		step("hotcold-701", "hotcold", "master", "coord", new int[0], "", -1, Tiles.pack(2100, 3100, 0))));

	/** Item param 623 as the game would give it: the step's row for clue items, -1 for everything else. */
	private static final IntUnaryOperator PARAM = id ->
	{
		Map<Integer, Integer> rows = Map.of(EASY_ITEM, 1300, ELITE_ITEM, 1455, NEW_ITEM, 2085, UNKNOWN_CLUE_ITEM, 77777);
		return rows.getOrDefault(id, -1);
	};

	private static List<String> keys(DetectedClue d)
	{
		return d.getSteps().stream().map(ClueData.Step::getKey).collect(java.util.stream.Collectors.toList());
	}

	@Test
	public void easyToEliteClueItemsNameTheirStep()
	{
		ClueWatcher w = new ClueWatcher(DATA);
		assertNull("no clue: nothing to say", w.onInventory(List.of(BREAD), PARAM));
		DetectedClue d = w.onInventory(List.of(BREAD, EASY_ITEM), PARAM);
		assertNotNull(d);
		assertEquals(DetectedClue.Kind.STEP, d.getKind());
		assertEquals(List.of("cryptic-1300"), keys(d));
		assertEquals("easy", d.getTier());
		assertEquals("Cryptic clue (easy)", d.title());
		assertTrue(d.isRoutable());
		assertNull("same inventory again: no new detection", w.onInventory(List.of(EASY_ITEM, BREAD), PARAM));
		assertNull("other items come and go", w.onInventory(List.of(EASY_ITEM), PARAM));

		// the step done: the old item goes, the next step's item comes in the same change
		d = w.onInventory(List.of(ELITE_ITEM), PARAM);
		assertEquals(List.of("cryptic-1455"), keys(d));
		assertEquals(3, d.getTargets().length);

		// an item the export doesn't list: its param names the row
		d = w.onInventory(List.of(ELITE_ITEM, NEW_ITEM), PARAM);
		assertEquals("the newest clue item wins", List.of("emote-2085"), keys(d));
		// a clue item whose row isn't in the data
		d = w.onInventory(List.of(UNKNOWN_CLUE_ITEM), PARAM);
		assertEquals(DetectedClue.Kind.NOT_FOUND, d.getKind());
		assertFalse(d.isRoutable());
		assertTrue(d.lines().get(0).contains("item " + UNKNOWN_CLUE_ITEM));

		// no clue left
		d = w.onInventory(List.of(BREAD), PARAM);
		assertEquals(DetectedClue.Kind.NONE, d.getKind());
		assertNull(w.onInventory(List.of(), PARAM));
	}

	@Test
	public void droppingOneOfTwoCluesFallsBackToTheOther()
	{
		ClueWatcher w = new ClueWatcher(DATA);
		w.onInventory(List.of(EASY_ITEM), PARAM);
		assertEquals(List.of("cryptic-1455"), keys(w.onInventory(List.of(EASY_ITEM, ELITE_ITEM), PARAM)));
		DetectedClue d = w.onInventory(List.of(EASY_ITEM), PARAM);
		assertEquals(List.of("cryptic-1300"), keys(d));
	}

	@Test
	public void beginnerAndMasterCluesNeedTheirText()
	{
		ClueWatcher w = new ClueWatcher(DATA);
		DetectedClue d = w.onInventory(List.of(ClueWatcher.BEGINNER_CLUE), PARAM);
		assertEquals(DetectedClue.Kind.NEEDS_TEXT, d.getKind());
		assertEquals("Beginner clue", d.title());
		assertFalse(d.isRoutable());
		assertTrue(d.lines().get(0).startsWith("Read the clue scroll"));
		assertTrue("beginner maps can't be told apart", d.lines().get(1).contains("Map clues"));

		// the scroll is read: the text names the step (only beginner steps are looked at)
		d = w.onClueText("Talk to Charlie the Tramp<br>in Varrock.");
		assertEquals(List.of("cryptic-1236"), keys(d));
		assertEquals(ClueWatcher.BEGINNER_CLUE, d.getItemId());
		assertTrue(d.isRoutable());
		assertNull("read again: nothing new", w.onClueText("Talk to Charlie the Tramp in Varrock."));
		assertNull("other items change: the scroll keeps its step", w.onInventory(List.of(ClueWatcher.BEGINNER_CLUE, BREAD), PARAM));

		// next step, same scroll item: a new text
		d = w.onClueText("This anagram reveals who to speak to next: AN EARL");
		assertEquals(List.of("anagram-1499"), keys(d));
		// a text Best Path doesn't know
		d = w.onClueText("Something new Jagex added.");
		assertEquals(DetectedClue.Kind.NOT_FOUND, d.getKind());
		assertTrue(d.lines().get(0).contains("Something new Jagex added."));

		// a master scroll: master steps only
		w = new ClueWatcher(DATA);
		w.onInventory(List.of(ClueWatcher.MASTER_CLUE), PARAM);
		d = w.onClueText("Buried beneath the ground, who knows where it's found. Lucky for you, A man called Jorral may have a clue.");
		assertEquals(List.of("cryptic-1496"), keys(d));
		d = w.onClueText("Combine the torn clue scroll parts.");
		assertEquals(DetectedClue.Kind.STEP, d.getKind());
		assertFalse("a step without a place", d.isRoutable());
		assertTrue(String.join(" ", d.lines()).contains("no place"));
		assertNull(d.request());
	}

	@Test
	public void textOfAnEasyClueAddsNothing()
	{
		ClueWatcher w = new ClueWatcher(DATA);
		w.onInventory(List.of(EASY_ITEM), PARAM);
		assertNull(w.onClueText("Talk to Charlie the Tramp in Varrock."));
		assertNull("not a clue we carry", new ClueWatcher(DATA).onClueText("Nothing to do with clues."));
	}

	@Test
	public void strangeDeviceMeansHotCold()
	{
		ClueWatcher w = new ClueWatcher(DATA);
		w.onInventory(List.of(ClueWatcher.MASTER_CLUE), PARAM);
		w.onClueText("A man called Jorral may have a clue.");
		DetectedClue d = w.onInventory(List.of(ClueWatcher.MASTER_CLUE, ClueWatcher.MASTER_DEVICE), PARAM);
		assertEquals(DetectedClue.Kind.HOT_COLD, d.getKind());
		assertEquals("Hot/cold clue (master)", d.title());
		assertArrayEquals(new int[]{Tiles.pack(2000, 3000, 0), Tiles.pack(2100, 3100, 0)}, d.getTargets());
		assertTrue(d.isRoutable());
		assertTrue(d.lines().get(0).contains("nearest of the 2 possible spots"));
		// the device goes (dug up): back to the scroll, whose text must be read again
		d = w.onInventory(List.of(ClueWatcher.MASTER_CLUE), PARAM);
		assertEquals(DetectedClue.Kind.NEEDS_TEXT, d.getKind());

		d = new ClueWatcher(DATA).onInventory(List.of(ClueWatcher.BEGINNER_DEVICE), PARAM);
		assertArrayEquals(new int[]{Tiles.pack(3110, 3150, 0)}, d.getTargets());
	}

	@Test
	public void withoutClueDataItSaysSoThenLooksAgain()
	{
		ClueWatcher w = new ClueWatcher(null);
		DetectedClue d = w.onInventory(List.of(BREAD, EASY_ITEM), PARAM);
		assertEquals("found by its param even without data", DetectedClue.Kind.NO_DATA, d.getKind());
		assertFalse(d.isRoutable());
		d = w.setData(DATA, PARAM);
		assertEquals(List.of("cryptic-1300"), keys(d));
		assertNull("no clue carried: nothing to say", new ClueWatcher(null).setData(DATA, PARAM));
	}

	@Test
	public void sidebarTextAndRouteRequest()
	{
		ClueWatcher w = new ClueWatcher(DATA);
		DetectedClue d = w.onInventory(List.of(NEW_ITEM), PARAM);
		List<String> lines = d.lines();
		assertEquals("\"Bow at the Grand Exchange. Equip a cape.\"", lines.get(0));
		assertEquals("Description of emote-2085.", lines.get(1));
		assertEquals("This step has a STASH unit (built or not, and your outfit: on the clue's card at the end).", lines.get(2));
		RouteRequest r = d.request();
		assertEquals("Emote clue (medium)", r.getTitle());
		assertEquals(lines, r.getNotes());
		assertEquals(1, r.targetCount());
		assertEquals(3164, r.getX());

		d = w.onInventory(List.of(ELITE_ITEM), PARAM);
		assertTrue(String.join(" ", d.lines()).contains("found in 3 areas (places from the OSRS Wiki)"));
		r = d.request();
		assertEquals(3, r.targetCount());
		assertEquals("same clue, same request (skips are kept)", r, w.getCurrent().request());
		assertFalse(r.equals(new RouteRequest(3000, 3000, 0)));
	}
}
