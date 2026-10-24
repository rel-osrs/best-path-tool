package com.bestpath.tool.slayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.core.data.SlayerPlacesDiagnostics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Each place's own special loot: the collection log items that differ between a task's places (some spiritual
 * creatures drop dragon boots, some do not), none on boss cards, long / curved bones and champion scrolls left out.
 */
class SlayerPlaceLootTest
{
	static final int BOOTS = 11840;
	static final int SHARD = 24777;
	static final int CEREMONIAL = 26221;
	static final int TEAR = 27352;
	static final int LONG_BONE = 10976;
	static final int PET = 1;
	static final int SPIRITUAL = 1;
	static final int VAMPYRES = 2;
	static final int BOSS = 3;

	private static final CollectionLog LOG = new CollectionLog(Map.of(
		"Other / Slayer", List.of(BOOTS, SHARD, CEREMONIAL, TEAR),
		"Other / Miscellaneous", List.of(LONG_BONE),
		"Bosses / Callisto and Artio", List.of(PET)));

	private static final Map<String, List<Integer>> IDS = Map.of("dragon boots", List.of(34621, BOOTS), "blood shard", List.of(SHARD),
		"ancient ceremonial top", List.of(CEREMONIAL), "frozen tear", List.of(TEAR), "long bone", List.of(LONG_BONE),
		"callisto cub", List.of(PET));

	private static SlayerTables tables()
	{
		Map<Integer, SlayerTables.Task> t = new LinkedHashMap<>();
		t.put(SPIRITUAL, SlayerTables.task(SPIRITUAL, 89, "Spiritual Creatures"));
		t.put(VAMPYRES, SlayerTables.task(VAMPYRES, 34, "Vampyres"));
		t.put(BOSS, SlayerTables.task(BOSS, -1, "Callisto"));
		return SlayerTables.of(t, List.of(), Map.of(), Map.of());
	}

	private static SlayerPlaces.Place place(int task, String name)
	{
		return new SlayerPlaces.Place(task, name, -1, "?", "?", "?", "?", new int[]{0}, true, "", "", "");
	}

	private static Map<String, String> row(String... kv)
	{
		Map<String, String> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put(kv[i], kv[i + 1]);
		}
		return m;
	}

	private static Map<String, String> monsters(int task, String place, String monsters)
	{
		return row("task", String.valueOf(task), "place", place, "monsters", monsters);
	}

	private static Map<String, String> drop(String monster, String version, String item, String plain, String onlyIn)
	{
		return row("monster", monster, "revision", "1", "version", version, "item", item, "rarity", "1/128", "plain_drops", plain,
			"only_in", onlyIn);
	}

	private static Map<String, String> loot(List<SlayerPlaces.Loot> lines)
	{
		Map<String, String> out = new LinkedHashMap<>();
		for (SlayerPlaces.Loot l : lines)
		{
			out.merge(SlayerPlacesDiagnostics.key(l), java.util.Arrays.toString(l.getItems()) + " " + l.getNote(), (a, b) -> a + "; " + b);
		}
		return out;
	}

	@Test
	void onlyTheDifferencesBetweenATasksPlaces()
	{
		List<SlayerPlaces.Place> places = List.of(place(SPIRITUAL, "God Wars Dungeon"), place(SPIRITUAL, "God Wars Dungeon (5)"),
			place(VAMPYRES, "Meiyerditch"), place(VAMPYRES, "Darkmeyer"), place(VAMPYRES, "Ungael"), place(BOSS, "Callisto's Den"));
		List<Map<String, String>> at = List.of(monsters(SPIRITUAL, "God Wars Dungeon", "Spiritual ranger"),
			monsters(SPIRITUAL, "God Wars Dungeon (5)", "Spiritual mage"),
			monsters(VAMPYRES, "Meiyerditch", "Vyrewatch"),
			monsters(VAMPYRES, "Darkmeyer", "Vampyre Juvinate / Vyrewatch Sentinel / Callisto"),
			monsters(VAMPYRES, "Ungael", "Artio"),
			monsters(BOSS, "Callisto's Den", "Callisto"));
		List<Map<String, String>> drops = List.of(
			drop("Spiritual mage", "-", "Dragon boots", "yes", "-"),
			drop("Spiritual mage", "Ancient Prison", "Ancient ceremonial top", "yes", "-"),
			drop("Spiritual ranger", "Ancient Prison", "Ancient ceremonial top", "yes", "-"),
			drop("Spiritual ranger", "-", "Long bone", "yes", "-"),
			drop("Vyrewatch Sentinel", "-", "Blood shard", "yes", "-"),
			drop("Vyrewatch Sentinel", "-", "Frozen tear", "yes", "Ruins of Tapoyauik"),
			drop("Callisto", "-", "Callisto cub", "yes", "-"),
			drop("Artio", "-", "Callisto cub", "yes", "-"));
		SlayerExport.Result r = new SlayerExport.Result();
		Map<String, String> got = loot(SlayerPlaceLoot.placeLoot(tables(), places, at, drops, LOG, IDS, List.of(), r));
		assertEquals(Map.of(
			"1:God Wars Dungeon (5)", "[" + BOOTS + "] From Spiritual mage.",
			"2:Darkmeyer", "[" + SHARD + "] From Vyrewatch Sentinel."), got,
			"dragon boots only where spiritual mages are (the log's number, not the cosmetic copy); the Ancient Prison's table at"
				+ " no place; long bones left out; the frozen tear only in the Ruins of Tapoyauik; no boss's drop (Callisto, Artio"
				+ " of the log's \"Callisto and Artio\"), no boss task, a place with only a boss not compared");
		assertTrue(r.problems.isEmpty(), r.problems.toString());
	}

	@Test
	void everyPlaceHavingItShowsNothingAndAnAreaLineIsNotRepeated()
	{
		List<SlayerPlaces.Place> places = List.of(place(SPIRITUAL, "Catacombs of Kourend"), place(SPIRITUAL, "Slayer Tower"),
			place(SPIRITUAL, "Somewhere"));
		List<Map<String, String>> at = List.of(monsters(SPIRITUAL, "Catacombs of Kourend", "Spiritual mage"),
			monsters(SPIRITUAL, "Slayer Tower", "Spiritual mage / Vyrewatch Sentinel"), monsters(SPIRITUAL, "Somewhere", "-"));
		List<Map<String, String>> drops = List.of(drop("Spiritual mage", "-", "Dragon boots", "yes", "-"),
			drop("Vyrewatch Sentinel", "-", "Blood shard", "yes", "-"), drop("Vyrewatch Sentinel", "-", "Frozen tear", "yes", "-"));
		List<SlayerPlaces.Loot> area = List.of(new SlayerPlaces.Loot(SlayerPlaces.LOOT_AREA, "Catacombs of Kourend", new int[]{TEAR}, ""));
		Map<String, String> got = loot(SlayerPlaceLoot.placeLoot(tables(), places, at, drops, LOG, IDS, area, new SlayerExport.Result()));
		assertEquals(Map.of("1:Slayer Tower", "[" + SHARD + ", " + TEAR + "] From Vyrewatch Sentinel."), got,
			"the boots are at every place whose monsters are known (the third's aren't: not compared)");
		// the frozen tear is on the Catacombs' area line: not repeated there
		places = List.of(place(SPIRITUAL, "Catacombs of Kourend"), place(SPIRITUAL, "Slayer Tower"));
		at = List.of(monsters(SPIRITUAL, "Catacombs of Kourend", "Vyrewatch Sentinel"), monsters(SPIRITUAL, "Slayer Tower", "Spiritual mage"));
		got = loot(SlayerPlaceLoot.placeLoot(tables(), places, at, drops, LOG, IDS, area, new SlayerExport.Result()));
		assertEquals("[" + SHARD + "] From Vyrewatch Sentinel.", got.get("1:Catacombs of Kourend"));
	}

	@Test
	void whereADropTablesVersionCounts()
	{
		List<String> mages = List.of("God Wars Dungeon", "Wilderness Slayer Cave");
		assertTrue(SlayerPlaceLoot.versionAt("-", true, "God Wars Dungeon", mages));
		assertTrue(SlayerPlaceLoot.versionAt("Wilderness Slayer Cave", true, "Wilderness Slayer Cave", mages));
		assertFalse(SlayerPlaceLoot.versionAt("Wilderness Slayer Cave", false, "God Wars Dungeon", mages), "another place's table");
		assertFalse(SlayerPlaceLoot.versionAt("Ancient Prison", true, "God Wars Dungeon", mages), "a place not in the data, beside a plain table");
		assertTrue(SlayerPlaceLoot.versionAt("Level 21", false, "God Wars Dungeon", mages), "levels: a page with no plain table");
		assertTrue(SlayerPlaceLoot.leftOut("Skeleton champion scroll"));
		assertTrue(SlayerPlaceLoot.leftOut("Curved bone"));
		assertFalse(SlayerPlaceLoot.leftOut("Dragon boots"));
		assertEquals("Skeleton", SlayerPlaceLoot.monsterName("Skeleton (Ape Atoll)"));
	}

	@Test
	void anItemNotInTheLogIsReported()
	{
		List<Map<String, String>> drops = new ArrayList<>(List.of(drop("Spiritual mage", "-", "Rune boots", "yes", "-")));
		SlayerExport.Result r = new SlayerExport.Result();
		SlayerPlaceLoot.placeLoot(tables(), List.of(place(SPIRITUAL, "A")), List.of(monsters(SPIRITUAL, "A", "Spiritual mage")), drops, LOG,
			IDS, List.of(), r);
		assertEquals(List.of("special loot: Rune boots is not in this cache's collection log"), r.problems);
		assertEquals(List.of("Callisto and Artio"), LOG.entryNames(CollectionLog.BOSSES_TAB));
		assertTrue(LOG.has(BOOTS));
		assertFalse(LOG.has(34621));
	}
}
