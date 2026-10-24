package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The plugin's clue steps: reading and writing clues.tsv, finding a step by item, cache row and clue text, and
 * routing to the cheapest of several places ({@link PluginRouter#routeNearest}). Hand-made steps and a small map.
 */
class ClueDataTest
{
	private static ClueData.Step step(String key, String type, String tier, int[] items, String text, int... targets)
	{
		return new ClueData.Step(key, type, tier, "npc", items, targets, -1, text, "desc of " + key, "", "", "", "", -1, "", -1, "", -1);
	}

	private static final ClueData DATA = new ClueData(List.of(
		step("anagram-1499", "anagram", "beginner", new int[0], "An Earl", Tiles.pack(3315, 3163, 0)),
		step("anagram-1503", "anagram", "beginner", new int[0], "In Bar", Tiles.pack(3026, 3246, 0)),
		step("cryptic-1233", "cryptic", "beginner", new int[0], "Always walking around the castle grounds and somehow knows"
			+ " everyone's age.", Tiles.pack(3221, 3218, 0)),
		step("cryptic-1300", "cryptic", "easy", new int[]{2677, 2678}, "Talk to Charlie the Tramp in Varrock.", Tiles.pack(3209, 3390, 0)),
		step("cryptic-1236", "cryptic", "beginner", new int[0], "Talk to Charlie the Tramp in Varrock.", Tiles.pack(3209, 3390, 0)),
		step("cryptic-1476", "cryptic", "master", new int[0], "Shhhh!", Tiles.pack(1633, 3808, 0)),
		step("cryptic-1495", "cryptic", "master", new int[0], "Come brave adventurer, your sense is on fire.",
			Tiles.pack(3293, 3933, 1), Tiles.pack(3448, 3549, 1)),
		step("cryptic-1496", "cryptic", "master", new int[0], "Come brave adventurer, your sense is on fire.",
			Tiles.pack(1000, 1000, 0)),
		step("emote-2084", "emote", "beginner", new int[0], "Blow a raspberry at Aris in her tent. Equip a gold ring and a gold necklace.",
			Tiles.pack(3203, 3424, 0)),
		step("emote-2085", "emote", "beginner", new int[0], "Bow to Brugsen Bursen at the Grand Exchange.", Tiles.pack(3164, 3477, 0)),
		step("coordinate-2056", "coordinate", "master", new int[0], "01.30N 08.11W", Tiles.pack(2178, 3209, 0)),
		step("coordinate-1923", "coordinate", "medium", new int[]{2801}, "00.05S 01.13E", Tiles.pack(2479, 3158, 0)),
		step("hotcold-685", "hotcold", "beginner", new int[0], "", Tiles.pack(3110, 3150, 0)),
		step("hotcold-686", "hotcold", "beginner", new int[0], "", Tiles.pack(3200, 3300, 0)),
		step("hotcold-700", "hotcold", "master", new int[0], "", Tiles.pack(2000, 3000, 0)),
		step("skillchallenge-1868", "skillchallenge", "master", new int[0], "Combine the torn clue scroll parts.")));

	private static List<String> keys(List<ClueData.Step> steps)
	{
		List<String> out = new ArrayList<>();
		for (ClueData.Step s : steps)
		{
			out.add(s.getKey());
		}
		return out;
	}

	@Test
	void fileReadsBackTheSame()
	{
		ClueData.Step withStash = new ClueData.Step("emote-2086", "emote", "beginner", "coord", new int[]{1, 2},
			new int[]{Tiles.pack(3205, 3416, 0), Tiles.pack(3206, 3417, 1)}, Tiles.pack(3209, 3416, 0), "Cheer at Iffie\tNitter.",
			"Iffie's shop.", "Page@123,Other@4", "", "", "", -1, "", -1, "", -1);
		List<ClueData.Step> all = new ArrayList<>(DATA.getSteps());
		all.add(withStash);
		ClueData d = new ClueData(all);
		String text = ClueDataWriter.write(d, "a comment\nsecond line");
		ClueData back = ClueData.parse(Arrays.asList(text.split("\n")));
		assertEquals(d.size(), back.size());
		for (int i = 0; i < d.size(); i++)
		{
			assertEquals(ClueDataWriter.write(d.getSteps().get(i)), ClueDataWriter.write(back.getSteps().get(i)));
		}
		ClueData.Step s = ClueDataDiagnostics.byKey(back, "emote-2086");
		assertEquals(Tiles.pack(3209, 3416, 0), s.getStash());
		assertEquals(2, s.targetCount());
		assertEquals("Cheer at Iffie Nitter.", s.getText(), "tabs inside text are written as spaces");
		assertEquals(0, ClueDataDiagnostics.byKey(back, "skillchallenge-1868").targetCount(), "a step without a place");
		assertThrows(IllegalArgumentException.class, () -> ClueData.parse(List.of("key\ttype")), "wrong header");
		assertThrows(IllegalArgumentException.class, () -> ClueData.parse(List.of(ClueData.HEADER, "a\tb")), "too few columns");
	}

	@Test
	void needsColumnReadsBackAndOldFilesStillRead()
	{
		ClueData.Step dig = new ClueData.Step("coordinate-1", "coordinate", "hard", "coord", new int[]{7}, new int[]{Tiles.pack(3000, 3000, 0)},
			-1, "00.05S 01.13E", "A dig spot.", "", "item 952", "", "", -1, "", -1, "", -1);
		ClueData.Step none = new ClueData.Step("cryptic-2", "cryptic", "easy", "npc", new int[0], new int[0], -1, "Talk.", "Someone.", "", "", "", "", -1, "", -1, "", -1);
		assertEquals("item 952", dig.getNeeds());
		assertEquals("", none.getNeeds(), "the old constructor needs nothing");
		ClueData back = ClueData.parse(Arrays.asList(ClueDataWriter.write(new ClueData(List.of(dig, none)), "c").split("\n")));
		assertEquals("item 952", ClueDataDiagnostics.byKey(back, "coordinate-1").getNeeds());
		assertEquals("", ClueDataDiagnostics.byKey(back, "cryptic-2").getNeeds());
		// an export made before the needs column (10 columns) still reads, with no needs
		ClueData old = ClueData.parse(List.of("# old", ClueData.HEADER_V1,
			"cryptic-2\tcryptic\teasy\tnpc\t\t3000,3000,0\t\tTalk.\tSomeone.\t"));
		assertEquals(1, old.size());
		assertEquals("", ClueDataDiagnostics.byKey(old, "cryptic-2").getNeeds());
		assertThrows(IllegalArgumentException.class, () -> ClueData.parse(List.of(ClueData.HEADER_V1,
			"cryptic-2\tcryptic\teasy\tnpc\t\t3000,3000,0\t\tTalk.\tSomeone.\t\titem 952")), "11 cells under the old header");
	}

	@Test
	void noteColumnReadsBackAndFilesWithoutItStillRead()
	{
		ClueData.Step kill = new ClueData.Step("cryptic-1458", "cryptic", "elite", "kill", new int[]{7}, new int[]{Tiles.pack(1766, 5344, 0)},
			-1, "Kill.", "", "", "", "Kill the barbarian spirit here", "", -1, "", -1, "", -1);
		ClueData.Step dig = new ClueData.Step("coordinate-1", "coordinate", "hard", "coord", new int[0], new int[0], -1, "00.05S 01.13E", "",
			"", "item 952", "", "", -1, "", -1, "", -1);
		assertEquals("Kill the barbarian spirit here", kill.getNote());
		assertEquals("", dig.getNote(), "the constructor without a note has none");
		String text = ClueDataWriter.write(new ClueData(List.of(kill, dig)), "c");
		assertTrue(text.contains(ClueData.HEADER + "\n"));
		ClueData back = ClueData.parse(Arrays.asList(text.split("\n")));
		assertEquals("Kill the barbarian spirit here", ClueDataDiagnostics.byKey(back, "cryptic-1458").getNote());
		assertEquals("", ClueDataDiagnostics.byKey(back, "coordinate-1").getNote());
		assertEquals("item 952", ClueDataDiagnostics.byKey(back, "coordinate-1").getNeeds());
		// an export made before the note column (11 columns) still reads, with no note
		ClueData v2 = ClueData.parse(List.of("# v2", ClueData.HEADER_V2,
			"coordinate-1\tcoordinate\thard\tcoord\t\t3000,3000,0\t\t00.05S 01.13E\t\t\titem 952"));
		assertEquals("item 952", ClueDataDiagnostics.byKey(v2, "coordinate-1").getNeeds());
		assertEquals("", ClueDataDiagnostics.byKey(v2, "coordinate-1").getNote());
		assertThrows(IllegalArgumentException.class, () -> ClueData.parse(List.of(ClueData.HEADER_V2,
			"coordinate-1\tcoordinate\thard\tcoord\t\t3000,3000,0\t\tText\t\t\titem 952\tnote")), "12 cells under the v2 header");
		assertThrows(IllegalArgumentException.class, () -> ClueData.parse(List.of(ClueData.HEADER,
			"coordinate-1\tcoordinate\thard\tcoord\t\t3000,3000,0\t\tText\t\t\titem 952")), "11 cells under the new header");
	}

	@Test
	void clueCardColumnsReadBackAndOlderExportsStillRead()
	{
		ClueData.Step emote = new ClueData.Step("emote-2086", "emote", "beginner", "coord", new int[0], new int[]{Tiles.pack(3205, 3416, 0)},
			Tiles.pack(3209, 3416, 0), "Cheer at Iffie Nitter.", "Fine clothes in Varrock.", "", "item 1949 worn; item 1007 worn",
			"Use the bush.", "emote 7 Cheer | 2 Bow", 8255, "Fine clothes shop in Varrock", -1, "", -1);
		assertEquals("emote", actionKind(emote));
		assertEquals(List.of("Cheer", "Bow"), emotes(emote));
		ClueData back = ClueData.parse(Arrays.asList(ClueDataWriter.write(new ClueData(List.of(emote)), "c").split("\n")));
		ClueData.Step b = ClueDataDiagnostics.byKey(back, "emote-2086");
		assertEquals("emote 7 Cheer | 2 Bow", ClueDataDiagnostics.action(b));
		assertEquals(8255, b.getStashVarbit());
		assertEquals("Fine clothes shop in Varrock", b.getStashPlace());
		assertEquals("item 1949 worn; item 1007 worn", b.getNeeds());
		assertEquals("Use the bush.", b.getNote(), "the note stays in its own column, before the clue-card columns");
		assertEquals(ClueDataWriter.write(emote), ClueDataWriter.write(b));
		// an export made before the note (11 columns) or before the clue cards (12 columns) still reads, without them
		ClueData v2 = ClueData.parse(List.of("# v2", ClueData.HEADER_V2,
			"cryptic-2\tcryptic\teasy\tnpc\t\t3000,3000,0\t\tTalk.\tSomeone.\t\titem 952"));
		assertEquals("", ClueDataDiagnostics.action(ClueDataDiagnostics.byKey(v2, "cryptic-2")));
		assertEquals(-1, ClueDataDiagnostics.byKey(v2, "cryptic-2").getStashVarbit());
		assertEquals("item 952", ClueDataDiagnostics.byKey(v2, "cryptic-2").getNeeds());
		assertEquals(List.of(), emotes(ClueDataDiagnostics.byKey(v2, "cryptic-2")));
		ClueData v3 = ClueData.parse(List.of("# v3", ClueData.HEADER_V3,
			"cryptic-2\tcryptic\teasy\tnpc\t\t3000,3000,0\t\tTalk.\tSomeone.\t\titem 952\tA note."));
		assertEquals("A note.", ClueDataDiagnostics.byKey(v3, "cryptic-2").getNote());
		assertEquals("", ClueDataDiagnostics.action(ClueDataDiagnostics.byKey(v3, "cryptic-2")));
		assertEquals("", ClueDataDiagnostics.byKey(v3, "cryptic-2").getStashPlace());
		assertThrows(IllegalArgumentException.class, () -> ClueData.parse(List.of(ClueData.HEADER,
			"cryptic-2\tcryptic\teasy\tnpc\t\t3000,3000,0\t\tTalk.\tSomeone.\t\titem 952\tA note.")), "12 cells under the new header");
		ClueData.Step dig = new ClueData.Step("coordinate-1", "coordinate", "hard", "coord", new int[0], new int[0], -1, "", "", "", "", "",
			"dig", -1, "", -1, "", -1);
		assertEquals("dig", actionKind(dig));
		assertEquals("", actionTarget(dig));
		assertEquals("", actionTarget(emote), "emotes have names, not a target");
		ClueData.Step talk = new ClueData.Step("anagram-1499", "anagram", "beginner", "npc", new int[0], new int[0], -1, "An Earl", "",
			"", "", "", "talk Ranael", -1, "", -1, "", -1);
		assertEquals("talk", actionKind(talk));
		assertEquals("Ranael", actionTarget(talk));
	}

	/** A key step knows where the NPC that drops the key is, and the key. */
	@Test
	void keyNpcColumnsReadBackAndOlderExportsStillRead()
	{
		ClueData.Step key = new ClueData.Step("cryptic-2279", "cryptic", "medium", "key", new int[]{7296},
			new int[]{Tiles.pack(3353, 3332, 0)}, -1, "The dead, red dragon watches over this chest.", "Kill a MALE Barbarian.", "",
			"item 7297", "", "key Closed chest", -1, "", Tiles.pack(3082, 3419, 0), "Barbarian", 7297);
		ClueData.Step b = ClueDataDiagnostics.byKey(ClueData.parse(Arrays.asList(ClueDataWriter.write(new ClueData(List.of(key)), "c").split("\n"))), "cryptic-2279");
		assertEquals(Tiles.pack(3082, 3419, 0), b.getKeyNpc());
		assertEquals("Barbarian", b.getKeyNpcName());
		assertEquals(7297, b.getKeyItem());
		assertEquals(ClueDataWriter.write(key), ClueDataWriter.write(b));
		assertTrue(ClueDataWriter.write(key).endsWith("\t3082,3419,0\tBarbarian\t7297"));
		// other steps: no key NPC, three empty cells
		ClueData.Step talk = new ClueData.Step("anagram-1499", "anagram", "beginner", "npc", new int[0], new int[0], -1, "An Earl", "",
			"", "", "", "talk Ranael", -1, "", -1, "", -1);
		assertEquals(-1, talk.getKeyNpc());
		assertEquals(-1, talk.getKeyItem());
		assertTrue(ClueDataWriter.write(talk).endsWith("talk Ranael\t\t\t\t\t"));
		// an export made before the key-NPC columns (15 columns) still reads, without them
		ClueData v4 = ClueData.parse(List.of("# v4", ClueData.HEADER_V4,
			"cryptic-2279\tcryptic\tmedium\tkey\t7296\t3353,3332,0\t\tText.\tKill.\t\titem 7297\t\tkey Closed chest\t\t"));
		assertEquals(-1, ClueDataDiagnostics.byKey(v4, "cryptic-2279").getKeyNpc());
		assertEquals("", ClueDataDiagnostics.byKey(v4, "cryptic-2279").getKeyNpcName());
		assertEquals(-1, ClueDataDiagnostics.byKey(v4, "cryptic-2279").getKeyItem());
		assertThrows(IllegalArgumentException.class, () -> ClueData.parse(List.of(ClueData.HEADER,
			"cryptic-2279\tcryptic\tmedium\tkey\t7296\t3353,3332,0\t\tText.\tKill.\t\titem 7297\t\tkey Closed chest\t\t")),
			"15 cells under the new header");
	}

	@Test
	void stepsByItemAndByCacheRow()
	{
		assertEquals("cryptic-1300", DATA.byItem(2677).getKey());
		assertEquals("cryptic-1300", DATA.byItem(2678).getKey());
		assertNull(DATA.byItem(23182), "the shared beginner item names no step");
		assertTrue(DATA.isClueItem(2801));
		assertEquals("coordinate-1923", DATA.byRow(1923).getKey());
		assertEquals(1923, DATA.byRow(1923).getRow());
		assertNull(DATA.byRow(5));
		assertEquals(List.of("hotcold-685", "hotcold-686"), keys(DATA.hotCold("beginner")));
		assertEquals(List.of("hotcold-700"), keys(DATA.hotCold("master")));
	}

	@Test
	void normalizedTextIgnoresTagsCaseAndPunctuation()
	{
		assertEquals("always walking around the castle grounds", ClueData.normalize("<col=000080>Always walking<br>around the"
			+ " castle   grounds...</col>"));
		assertEquals("champions guild", ClueData.normalize("Champions' Guild"));
		assertEquals("", ClueData.normalize(null));
	}

	@Test
	void coordinatesInBothWordings()
	{
		assertEquals("1.30N 8.11W", ClueData.coordinateKey("01.30N 08.11W"));
		assertEquals("1.30N 8.11W", ClueData.coordinateKey("01 degrees 30 minutes north<br>08 degrees 11 minutes west"));
		assertEquals("1.30N 8.11W", ClueData.coordinateKey("1 degree 30 minutes north, 8 degrees 11 minutes west"));
		assertEquals("0.05S 1.13E", ClueData.coordinateKey("00 degrees 05 minutes south 01 degree 13 minutes east"));
		assertNull(ClueData.coordinateKey("Talk to Charlie the Tramp in Varrock."));
	}

	@Test
	void stepFoundByTheTextOnTheScroll()
	{
		// the same text, written differently
		assertEquals(List.of("cryptic-1233"), keys(DATA.matchText("Always walking around the castle grounds<br>and somehow knows"
			+ " everyone's age.", "beginner")));
		// an anagram with words around it: the step text found inside, as whole words
		assertEquals(List.of("anagram-1499"), keys(DATA.matchText("This anagram reveals<br>who to speak to next:<br>AN EARL",
			"beginner")));
		assertEquals(List.of("anagram-1503"), keys(DATA.matchText("This anagram reveals who to speak to next: IN BAR", "beginner")));
		// not inside another word ("in bar" is not in "cabin barrel")
		assertTrue(DATA.matchText("Search the cabin barrel", "beginner").isEmpty());
		// tier decides between equal texts of different tiers
		assertEquals(List.of("cryptic-1236"), keys(DATA.matchText("Talk to Charlie the Tramp in Varrock.", "beginner")));
		assertEquals(List.of("cryptic-1300"), keys(DATA.matchText("Talk to Charlie the Tramp in Varrock.", "easy")));
		assertEquals(2, DATA.matchText("Talk to Charlie the Tramp in Varrock.", null).size());
		// coordinates as the master scroll words them
		assertEquals(List.of("coordinate-2056"), keys(DATA.matchText("01 degrees 30 minutes north<br>08 degrees 11 minutes west",
			"master")));
		assertTrue(DATA.matchText("01 degrees 30 minutes north 08 degrees 11 minutes west", "beginner").isEmpty());
		// two steps with the same text: both (their places are alternatives)
		assertEquals(List.of("cryptic-1495", "cryptic-1496"), keys(DATA.matchText("Come brave adventurer, your sense is on fire.",
			"master")));
		// an emote whose outfit line is worded differently: found by its first sentence
		assertEquals(List.of("emote-2084"), keys(DATA.matchText("Blow a raspberry at Aris in her tent.<br>Equip:<br>Gold ring<br>"
			+ "Gold necklace", "beginner")));
		// the longest text wins over a short one inside it
		assertEquals(List.of("cryptic-1476"), keys(DATA.matchText("Shhhh!", "master")));
		assertTrue(DATA.matchText("Something no clue says.", null).isEmpty());
		assertTrue(DATA.matchText("", null).isEmpty());
		assertTrue(DATA.matchText(null, null).isEmpty());
	}

	// ------------------------------------------------------------------------------------- several destinations

	/** One open region (3200-3263, 3200-3263) on plane 0, with a wall line x = 3230 (gap at y 3260) and a teleport. */
	private static PluginRouter router()
	{
		MovementRules.Flags open = (x, y) ->
		{
			if (x < 3200 || x >= 3264 || y < 3200 || y >= 3264)
			{
				return CollisionFlags.BLOCKED;
			}
			if (x == 3230 && y < 3260)
			{
				return CollisionFlags.BLOCKED;
			}
			return 0;
		};
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		TransportLine tele = TransportLine.builder("t-tele", TransportLine.Kind.TELEPORT).name("Test teleport")
			.category("TRAVEL").end(3250, 3205, 0).ticks(5).build();
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(tele),
			Tables.empty(), FreeLand.NONE);
		return new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
	}

	@Test
	void cheapestOfSeveralPlacesIsChosen()
	{
		PluginRouter r = router();
		PlayerState p = PlayerStateBuilder.fresh();
		int near = Tiles.pack(3210, 3210, 0);
		int behindWall = Tiles.pack(3240, 3210, 0); // 30 tiles away, but around the wall; the teleport lands 10 away
		int far = Tiles.pack(3225, 3255, 0);
		PluginRouter.NearestAnswer a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, new int[]{far, near, behindWall}, id -> false, 10);
		assertEquals(1, a.getChosen());
		assertEquals(5, a.getAnswer().getRoute().getTicks());
		assertEquals(3, a.getReachable());
		assertTrue(a.getSearched() < 3, "the others' lower bounds are above 5 ticks: not searched");
		assertFalse(a.isCapped());

		// the teleport makes the place behind the wall cheaper than walking to the far one
		a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, new int[]{far, behindWall}, id -> false, 10);
		assertEquals(1, a.getChosen());
		assertEquals(PluginRouterDiagnostics.route(r, p, 3205, 3205, 0, 3240, 3210, 0, id -> false).getRoute().getTicks(), a.getAnswer().getRoute().getTicks());
		// skipping the teleport changes the choice
		a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, new int[]{far, behindWall}, "t-tele"::equals, 10);
		int toFar = PluginRouterDiagnostics.route(r, p, 3205, 3205, 0, 3225, 3255, 0, "t-tele"::equals).getRoute().getTicks();
		int toBehind = PluginRouterDiagnostics.route(r, p, 3205, 3205, 0, 3240, 3210, 0, "t-tele"::equals).getRoute().getTicks();
		assertEquals(toFar <= toBehind ? 0 : 1, a.getChosen());
		assertEquals(Math.min(toFar, toBehind), a.getAnswer().getRoute().getTicks());
	}

	@Test
	void equallyCheapPlacesKeepTheirOrderAndUnreachableOnesAreLeftOut()
	{
		PluginRouter r = router();
		PlayerState p = PlayerStateBuilder.fresh();
		int east = Tiles.pack(3215, 3205, 0);
		int north = Tiles.pack(3205, 3215, 0);
		int nowhere = Tiles.pack(3100, 3100, 0); // off the map, nothing within 10 tiles
		int upstairs = Tiles.pack(3210, 3210, 2); // no floor on plane 2
		PluginRouter.NearestAnswer a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, new int[]{nowhere, north, upstairs, east}, id -> false, 10);
		assertEquals(1, a.getChosen(), "north and east are both 10 ticks: the earlier one");
		assertEquals(2, a.getReachable());
		a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, new int[]{nowhere, east, north}, id -> false, 10);
		assertEquals(1, a.getChosen());

		a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, new int[]{nowhere, upstairs}, id -> false, 10);
		assertEquals(-1, a.getChosen());
		assertFalse(a.getAnswer().getRoute().isFound());
		assertNull(a.getAnswer().getStop());
		assertEquals(0, a.getReachable());

		a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, new int[0], id -> false, 10);
		assertEquals(-1, a.getChosen());
	}

	@Test
	void searchLimitIsReported()
	{
		PluginRouter r = router();
		PlayerState p = PlayerStateBuilder.fresh();
		// three equally far places (the same tile): only the first is searched; every place's exact ticks are known after it, so the other two can't be quicker and the
		// search limit is not reached
		int[] t = {Tiles.pack(3220, 3220, 0), Tiles.pack(3220, 3220, 0), Tiles.pack(3220, 3220, 0)};
		PluginRouter.NearestAnswer a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, t, id -> false, 1);
		assertEquals(1, a.getSearched());
		assertEquals(0, a.getChosen());
		assertTrue(a.getAnswer().getRoute().isFound());
		assertFalse(a.isCapped());
		// a limit of 0: nothing searched, and that is said
		a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, t, id -> false, 0);
		assertEquals(0, a.getSearched());
		assertTrue(a.isCapped());
		a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, t, id -> false, 10);
		assertFalse(a.isCapped());
		assertEquals(0, a.getChosen());
	}

	@Test
	void alwaysTheCheapestOfRandomPlaces()
	{
		PluginRouter r = router();
		PlayerState p = PlayerStateBuilder.fresh();
		Random rnd = new Random(11);
		for (int round = 0; round < 30; round++)
		{
			int n = 1 + rnd.nextInt(6);
			int[] t = new int[n];
			int best = Integer.MAX_VALUE;
			for (int i = 0; i < n; i++)
			{
				int x = 3200 + rnd.nextInt(64);
				int y = 3200 + rnd.nextInt(64);
				t[i] = Tiles.pack(x, y, 0);
				PluginRouter.Answer one = PluginRouterDiagnostics.route(r, p, 3205, 3205, 0, x, y, 0, id -> false);
				if (one.getRoute().isFound())
				{
					best = Math.min(best, one.getRoute().getTicks());
				}
			}
			PluginRouter.NearestAnswer a = PluginRouterDiagnostics.routeNearest(r, p, 3205, 3205, 0, t, id -> false, 10);
			assertEquals(best, a.getAnswer().getRoute().isFound() ? a.getAnswer().getRoute().getTicks() : Integer.MAX_VALUE,
				"round " + round);
			assertSame(a.getAnswer().getRoute().isFound(), a.getChosen() >= 0);
		}
	}

	/** The first word of a step's action ("emote" for "emote 19 Raspberry"), or "". */
	private static String actionKind(ClueData.Step s)
	{
		int sp = s.action.indexOf(' ');
		return sp < 0 ? s.action : s.action.substring(0, sp);
	}

	/** What a step's action is done to, after its first word ("Ranael" for "talk Ranael"); "" for dig and emotes. */
	private static String actionTarget(ClueData.Step s)
	{
		int sp = s.action.indexOf(' ');
		return sp < 0 || s.action.startsWith("emote ") ? "" : s.action.substring(sp + 1).trim();
	}

	/** For an emote step: the emotes' names in order ("Clap", "Spin"); else empty. */
	private static List<String> emotes(ClueData.Step s)
	{
		List<String> out = new ArrayList<>();
		if (s.action.startsWith("emote "))
		{
			for (String e : s.action.substring(6).split("\\|"))
			{
				String t = e.trim();
				int sp = t.indexOf(' ');
				out.add(sp < 0 ? t : t.substring(sp + 1).trim());
			}
		}
		return out;
	}
}
