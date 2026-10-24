package com.bestpath.tool.wiki;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.tool.slayer.SlayerTables;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Reading the Wiki's Slayer task pages (made-up page text in the Wiki's style). */
class SlayerPagesTest
{
	static final String TASK_PAGE = "{{Infobox Slayer\n|name = Kalphites\n|turael = 15-30\n|spria = 15-30\n|konar = 120-170\n"
		+ "|otherreq = None\n}}\n"
		+ "==Monster variants==\n{|class = \"wikitable sortable\"\n!Monster\n!Combat level\n!Locations\n|-\n"
		+ "|[[Kalphite Worker|Kalphite worker]]\n|28\n|\n*[[Kalphite Lair]]\n|-\n|[[Kalphite Soldier#Lair|Kalphite soldier]]\n|85\n|\n"
		+ "*[[Kalphite Lair]]\n|}\n"
		+ "==Location comparison==\n{|class = \"wikitable sortable align-center-3\"\n!Location\n!Maplink\n!Amount\n!Multicombat\n"
		+ "!Cannonable\n!Safespottable\n!Notes\n|-\n"
		+ "|[[Kalphite Lair]]\n|{{Map\n|mapID=42\n|type=maplink\n|3177,9502,title:Kalphite Worker\n|3200,9490,title:Kalphite Soldier,icon:redPin\n}}\n"
		+ "|18 Workers</br>20 Soldiers\n|{{Yes}}\n|{{Yes}}\n|{{No}}\n|\n* A [[rope]] is needed.\n|-\n"
		+ "|[[Slayer Tower]] ({{FloorNumber|uk=2}})\n|{{Map|type=maplink\n|name = Kalphite soldier\n"
		+ "|location = [[Slayer Tower]] ({{FloorNumber|uk=2}})\n|plane = 2\n|mtype = pin\n}}\n|14\n|{{No}}\n|{{No}}\n|\n|Far.\n|}\n";

	@Test
	void readsTheLocationTable()
	{
		List<SlayerPages.Row> rows = SlayerPages.locationRows(TASK_PAGE);
		assertEquals(2, rows.size());
		SlayerPages.Row lair = rows.get(0);
		assertEquals("Kalphite Lair", lair.location);
		assertEquals("Kalphite Lair", lair.link);
		assertEquals(2, lair.pins.size());
		assertArrayEquals(new int[]{3200, 9490}, lair.pins.get(1));
		assertEquals("42", lair.map.get("mapid"));
		assertEquals("18 Workers, 20 Soldiers", lair.amount);
		assertEquals("yes", lair.multi);
		assertEquals("yes", lair.cannon);
		assertEquals("no", lair.safespot);
		assertEquals("* A rope is needed.", lair.notes);
		SlayerPages.Row tower = rows.get(1);
		assertEquals("Slayer Tower (floor 2)", tower.location);
		assertTrue(tower.pins.isEmpty());
		assertEquals("Kalphite soldier", tower.map.get("name"));
		assertEquals("2", tower.map.get("plane"));
		assertEquals("?", tower.safespot, "an empty cell is not known");
		assertEquals("Far.", tower.notes);
	}

	@Test
	void readsTheVariantsAndTheAmounts()
	{
		assertEquals(List.of("Kalphite Worker", "Kalphite Soldier"), SlayerPages.variants(TASK_PAGE));
		Map<String, int[]> a = SlayerPages.amounts(TASK_PAGE);
		assertEquals(List.of("turael", "spria", "konar"), new ArrayList<>(a.keySet()));
		assertArrayEquals(new int[]{120, 170}, a.get("konar"));
	}

	@Test
	void readsLocLinesWithBarePairsToo()
	{
		String page = "{{LocLine\n|name = Sourhog\n|location = [[Sourhog Cave]]\n|mapID = 12\n|plane = 0\n|3157,9698|3163,9699|mtype = pin\n}}\n"
			+ "{{LocLine|name=Goblin|location=[[Lumbridge]]|plane=1|x:3202,y:3253|x:3206,y:3252}}";
		List<WikiClueKillsCli.Spawn> s = SlayerPages.spawns(page);
		assertEquals(4, s.size());
		assertEquals("Sourhog Cave", s.get(0).location);
		assertEquals(3163, s.get(1).x);
		assertEquals("12", s.get(1).mapId);
		assertEquals("Lumbridge", s.get(2).location);
		assertEquals(1, s.get(3).plane);
		assertEquals(3252, s.get(3).y);
	}

	@Test
	void readsTheListOfAssignments()
	{
		String list = "{| class=\"wikitable\"\n!Task\n!Superiors and alternatives\n!Location\n|-\n"
			+ "|[[Slayer task/Kalphites|Kalphites]]\n|{{NA}}\n|\n* [[Kalphite Lair]]\n|-\n"
			+ "|[[Ice warrior]]s\n|\n* [[Icelord]]\n* {{plinkp|Superior slayer monster}} '''[[Ice demon]]'''\n|\n* [[Asgarnian Ice Caves]]\n|}\n";
		Map<String, String> pages = SlayerPages.taskPages(list);
		assertEquals("Slayer task/Kalphites", pages.get("kalphites"));
		Map<String, List<String>> monsters = SlayerPages.assignmentMonsters(list);
		assertEquals(List.of(), monsters.get("kalphites"), "a task page of its own names its monsters");
		assertEquals(List.of("Ice warrior", "Icelord"), monsters.get("ice warriors"), "superiors (in bold) are left out");
		assertEquals(List.of("Ice warrior", "Icelord"), WikiSlayerCli.listedMonsters("Ice Warriors", monsters));
		assertEquals(List.of("Iron dragon"), WikiSlayerCli.listedMonsters("Iron Dragons", monsters), "else the singular name");
		assertEquals("Wolf", WikiSlayerCli.singular("Wolves"));
	}

	@Test
	void namesAndAreasMatch()
	{
		assertTrue(SlayerPages.sameName("Ice warriors", "Ice Warrior"));
		assertTrue(SlayerPages.sameName("Dust devils", "dust devil"));
		assertFalse(SlayerPages.sameName("Rats", "Brine Rats"));
		assertTrue(WikiSlayerCli.areaMatches("task-only Kalphite Cave", List.of("Kalphite Cave")));
		assertTrue(WikiSlayerCli.areaMatches("Slayer Tower", List.of("Slayer Tower (floor 2)")));
		assertTrue(WikiSlayerCli.areaMatches("Stronghold Slayer Dungeon", List.of("Stronghold Slayer Cave")), "an alias");
		assertFalse(WikiSlayerCli.areaMatches("Kalphite Lair", List.of("Kalphite Cave")));
		assertEquals("Giant Mole", WikiSlayerCli.bossPage("The Giant Mole"));
		assertEquals("Kraken", WikiSlayerCli.bossPage("The Cave Kraken boss"));
		assertEquals("Slayer task/Hill giants", WikiSlayerCli.taskPage("Hill Giants", Map.of()));
	}

	@Test
	void placesComeFromPinsOrFromTheMonstersLocLines()
	{
		SlayerTables.Task task = SlayerTables.task(6266, 53, "Kalphites");
		Map<String, String[]> monsters = new LinkedHashMap<>();
		monsters.put("Kalphite soldier", new String[]{"Kalphite Soldier", "7", "{{LocLine|location=[[Slayer Tower]] ({{FloorNumber|uk=2}})"
			+ "|plane=2|x:3420,y:3560|x:3421,y:3561}}{{LocLine|location=[[Kalphite Lair]]|x:3210,y:9500}}"});
		List<String> problems = new ArrayList<>();
		List<WikiSlayerCli.Place> places = WikiSlayerCli.fromTable(task, SlayerPages.locationRows(TASK_PAGE),
			SlayerPages.variants(TASK_PAGE), monsters, new String[]{"Slayer task/Kalphites", "5", ""}, problems);
		assertEquals(2, places.size());
		assertEquals(2, places.get(0).tiles.size(), "the lair: its two pins");
		assertEquals(0, places.get(0).tiles.get(0)[2]);
		WikiSlayerCli.Place tower = places.get(1);
		assertEquals(2, tower.tiles.size(), "the tower: the LocLines at the map's location only");
		assertArrayEquals(new int[]{3420, 3560, 2}, tower.tiles.get(0));
		assertTrue(tower.tilePages.contains("Kalphite Soldier@7"));
		assertTrue(problems.isEmpty(), problems.toString());

		SlayerTables tables = SlayerTables.of(Map.of(6266, task), List.of(SlayerTables.assignment(8, 6266, 120, 170, List.of(6355))),
			Map.of(6355, SlayerTables.area(6355, 11, "Kalphite Lair")), Map.of());
		WikiSlayerCli.konarArea(places.get(0), tables);
		WikiSlayerCli.konarArea(tower, tables);
		assertEquals("11", places.get(0).konarArea);
		assertEquals("-", tower.konarArea);
		assertEquals(8, tables.areaMaster());
	}

	@Test
	void mastersAreNamedByTheirAmountsOnceEach()
	{
		SlayerTables.Task kal = SlayerTables.task(6266, 53, "Kalphites");
		SlayerTables tables = SlayerTables.of(Map.of(6266, kal), List.of(SlayerTables.assignment(1, 6266, 15, 30, List.of()),
			SlayerTables.assignment(8, 6266, 120, 170, List.of()), SlayerTables.assignment(9, 6266, 15, 30, List.of())), Map.of(), Map.of());
		Map<SlayerTables.Task, String> pageOf = Map.of(kal, "Slayer task/Kalphites");
		Map<String, String[]> pages = Map.of("Slayer task/Kalphites", new String[]{"Slayer task/Kalphites", "5", TASK_PAGE});
		List<String[]> m = WikiSlayerCli.masters(tables, pageOf, pages);
		assertEquals("Turael", m.get(0)[1]);
		assertEquals("Konar", m.get(1)[1]);
		assertEquals("Spria", m.get(2)[1], "the same amounts as Turael: the next master with them");
	}
}
