package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.path.Tiles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The plugin's Slayer file (tasks, bosses, Konar areas, places). */
class SlayerPlacesTest
{
	static final String TEXT = "# a comment\n"
		+ "master\t8\tKonar\n"
		+ "task\t6266\t53\tKalphites\n"
		+ "task\t6311\t98\tBoss\n"
		+ "task\t6165\t-1\tThe Kalphite Queen\n"
		+ "boss\t7\t6165\n"
		+ "area\t11\tKalphite Lair\n"
		+ "place\t6266\tKalphite Lair\t11\t43\tyes\tyes\tno\t3200,9490,0 3226,9500,2\tyes\tKalphite Worker\tSlayer task/Kalphites@1\tA rope.\n"
		+ "place\t6266\tKalphite Cave\t-1\t?\t?\t-\t?\t3320,9500,0\tno\t-\t-\t-\n";

	@Test
	void readsEveryKindOfLine()
	{
		SlayerPlaces s = SlayerPlaces.parse(TEXT);
		assertEquals("Konar", s.masterName(8));
		assertNull(s.masterName(1));
		assertEquals("Kalphites", s.task(53, 0).getName());
		assertEquals(6266, s.task(53, 0).getRow());
		assertEquals("The Kalphite Queen", s.task(98, 7).getName(), "a boss task names the boss");
		assertEquals("Boss", s.task(98, 99).getName(), "an unknown boss: the boss task itself");
		assertNull(s.task(0, 0), "no task");
		assertNull(s.task(54, 0), "a task the data doesn't know");
		assertEquals("Kalphite Lair", s.areaName(11));
		assertEquals(2, placeCount(s));
		List<SlayerPlaces.Place> p = s.places(6266);
		assertEquals("Kalphite Lair", p.get(0).getName());
		assertEquals(11, p.get(0).getArea());
		assertEquals("43", p.get(0).getAmount());
		assertEquals("yes", p.get(0).getMulti());
		assertEquals("no", p.get(0).getSafespot());
		assertArrayEquals(new int[]{Tiles.pack(3200, 9490, 0), Tiles.pack(3226, 9500, 2)}, p.get(0).getPoints());
		assertTrue(p.get(0).isOnMap());
		assertEquals("Slayer task/Kalphites@1", p.get(0).getSource());
		assertEquals("A rope.", p.get(0).getNotes());
		assertEquals(-1, p.get(1).getArea());
		assertEquals("?", p.get(1).getCannon(), "a dash is not known");
		assertFalse(p.get(1).isOnMap());
		assertEquals("", p.get(1).getMonsters());
		assertTrue(s.places(6165).isEmpty());
	}

	@Test
	void writesWhatItReads()
	{
		SlayerPlaces s = SlayerPlaces.parse(TEXT);
		String text = SlayerPlacesWriter.write(s, "made by a test");
		assertTrue(text.startsWith("# made by a test\n"));
		assertEquals(text, SlayerPlacesWriter.write(SlayerPlaces.parse(text), "made by a test"));
	}

	@Test
	void noneHasNothing()
	{
		assertTrue(SlayerPlaces.NONE.isEmpty());
		assertNull(SlayerPlaces.NONE.task(53, 0));
		assertTrue(new SlayerPlaces(Map.of(), List.of(), Map.of(), Map.of(), List.of()).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> SlayerPlaces.parse("nonsense\t1\n"));
	}

	/** The Slayer plugin's task pictures, special loot, and notes without links. */
	@Test
	void iconsLootAndPlainNotes()
	{
		String text = "master\t8\tKonar\n"
			+ "task\t6266\t53\tKalphites\n"
			+ "place\t6266\tCatacombs of Kourend: north\t-1\t6\tyes\tno\tno\t1636,10046,0\tyes\t-\tP@1\t-\n"
			+ "place\t6266\tKalphite Lair\t-1\t6\tyes\tno\tno\t3200,9490,0\tyes\t-\tP@1\t-\n"
			+ "icon\t6266\t4144\n"
			+ "loot\tarea\tCatacombs of Kourend\t19677 19679\tNot from ghosts.\n"
			+ "loot\tmaster\t8\t23083\t-\n";
		SlayerPlaces d = SlayerPlaces.parse(text);
		assertEquals(4144, d.icon(6266));
		assertEquals(-1, d.icon(1));
		SlayerPlaces.Place catacombs = d.places(6266).get(0);
		SlayerPlaces.Place lair = d.places(6266).get(1);
		assertEquals(1, d.lootFor(catacombs, 3).size());
		assertArrayEquals(new int[]{19677, 19679}, d.lootFor(catacombs, 3).get(0).getItems());
		assertEquals(2, d.lootFor(catacombs, 8).size());
		assertEquals(0, d.lootFor(lair, 3).size());
		assertEquals("", d.lootFor(lair, 8).get(0).getNote());
		assertEquals(text, SlayerPlacesWriter.write(SlayerPlaces.parse(SlayerPlacesWriter.write(d, "c")), "c").substring(4));
		assertEquals("Safespot here. Do not bring items.", SlayerPlaces.plainNotes("Safespot here. "
			+ "[https://oldschool.runescape.wiki/images/A.gif Click here for a picture guide.] '''Do not''' bring items."));
		assertEquals("", SlayerPlaces.plainNotes(null));
	}

	/** Some spiritual creatures drop dragon boots and some do not: a place's own loot line. */
	@Test
	void aPlacesOwnLoot()
	{
		String text = "master\t3\tVannaka\n"
			+ "task\t6266\t89\tSpiritual Creatures\n"
			+ "task\t6267\t90\tOther\n"
			+ "place\t6266\tGod Wars Dungeon\t-1\t6\tyes\tno\tno\t2870,5300,2\tyes\t-\tP@1\t-\n"
			+ "place\t6266\tGod Wars Dungeon (5)\t-1\t6\tyes\tno\tno\t2880,5300,2\tyes\t-\tP@1\t-\n"
			+ "place\t6267\tGod Wars Dungeon (5)\t-1\t6\tyes\tno\tno\t2880,5300,2\tyes\t-\tP@1\t-\n"
			+ "loot\tplace\t6266:God Wars Dungeon (5)\t11840\tFrom Spiritual mage.\n";
		SlayerPlaces d = SlayerPlaces.parse(text);
		SlayerPlaces.Place gwd = d.places(6266).get(0);
		SlayerPlaces.Place mages = d.places(6266).get(1);
		assertEquals("6266:God Wars Dungeon (5)", SlayerPlaces.placeKey(mages));
		assertEquals(0, d.lootFor(gwd, 3).size());
		assertEquals(1, d.lootFor(mages, 3).size());
		assertArrayEquals(new int[]{11840}, d.lootFor(mages, 3).get(0).getItems());
		assertEquals("From Spiritual mage.", d.lootFor(mages, 3).get(0).getNote());
		assertEquals(0, d.lootFor(d.places(6267).get(0), 3).size(), "the same place name in another task");
		assertEquals(text, SlayerPlacesWriter.write(SlayerPlaces.parse(SlayerPlacesWriter.write(d, "c")), "c").substring(4));
	}

	/** Every place of every task. */
	private static int placeCount(SlayerPlaces s)
	{
		int n = 0;
		for (List<SlayerPlaces.Place> list : s.places.values())
		{
			n += list.size();
		}
		return n;
	}
}
