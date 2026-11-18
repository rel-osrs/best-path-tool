package com.bestpath.tool.wiki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Landing zones read from teleport pages (wikitext shaped like the real pages). */
class WikiTextTest
{
	private static final String CAMELOT = "{{Infobox Spell\n|name = Camelot Teleport\n}}\n"
		+ "==Teleportation options==\n"
		+ "{{TeleportLocationTableHead}}\n"
		+ "{{TeleportLocationLine|name=[[Camelot]]|x=2757|y=3479}}\n"
		+ "|[[Seers' Village bank]]{{Efn|If hard [[Kandarin Diary]] has been completed.|group=L}}\n"
		+ "|{{Map|text=Maplink|2724,3487|2729,3487|2729,3482|2728,3482|2728,3483|2727,3483|2727,3484|2724,3484"
		+ "|mtype=polygon|width=200|height=200}}\n"
		+ "{{TableBottom}}\n";

	@Test
	void aLineWithoutRadiusOrTypeIsNotAZoneButIsFlagged()
	{
		WikiTeleportsCli.Record camelot = WikiText.records("Camelot Teleport", 1, CAMELOT).get(0);
		assertEquals("Camelot", camelot.name);
		assertFalse(camelot.zone);
		assertTrue(camelot.templateDefaultZone);
		assertNull(camelot.zoneReach);
	}

	@Test
	void anOutlineOnAPageWithLinesIsReadWithItsPoints()
	{
		List<WikiTeleportsCli.Record> recs = WikiText.records("Camelot Teleport", 1, CAMELOT);
		assertEquals(2, recs.size());
		WikiTeleportsCli.Record seers = recs.get(1);
		assertTrue(seers.zone);
		assertEquals("polygon", seers.zoneShape);
		assertEquals(8, seers.zonePoints.size());
		// middle of the points: x (2724+2729+2729+2728+2728+2727+2727+2724)/8 = 2727, y 3484 (rounded)
		assertEquals(2727, seers.x);
		assertEquals(3484, seers.y);
		// farthest point: 2724,3487 is 3 tiles off in both directions
		assertEquals(3, seers.zoneReach);
	}

	@Test
	void aLinesOwnRadiusIsASquareZoneAndAPinIsExact()
	{
		String text = "{{Infobox Item\n}}\n==Teleport==\n"
			+ "{{TeleportLocationLine|name=[[Kalphite Cave]]|x=3322|y=3122|r=1}}\n"
			+ "{{TeleportLocationLine|name=[[Nardah]]|x=3426|y=2927|r=2.5}}\n"
			+ "{{TeleportLocationLine|name=[[Troll Stronghold (location)|Troll Stronghold]]|x=2845|y=3694|mtype=square|r=2}}\n"
			+ "{{TeleportLocationLine|name=[[Edgeville]]|x=3087|y=3496|mtype=pin}}\n"
			+ "{{Map|3087,3496|mtype=pin}}\n";
		List<WikiTeleportsCli.Record> recs = WikiText.records("Desert amulet 4", 1, text);
		assertEquals(4, recs.size(), "a pin map on a page with lines isn't another destination");
		assertTrue(recs.get(0).zone);
		assertEquals(1, recs.get(0).zoneReach);
		assertEquals(2.5, recs.get(1).zoneRadius);
		assertEquals(2, recs.get(1).zoneReach, "2.5 tiles can land 2 whole tiles off");
		assertEquals("Troll Stronghold", recs.get(2).name);
		assertTrue(recs.get(2).zone);
		WikiTeleportsCli.Record edgeville = recs.get(3);
		assertFalse(edgeville.zone);
		assertFalse(edgeville.templateDefaultZone);
	}

	@Test
	void aPageWithoutLinesStillReadsItsMaps()
	{
		String text = "{{Infobox Spell\n}}\n==Destination==\n{{Map|3221,3218|mtype=square|r=2}}\n{{Map|3000,3000}}\n";
		List<WikiTeleportsCli.Record> recs = WikiText.records("Some Teleport", 1, text);
		assertEquals(2, recs.size());
		assertTrue(recs.get(0).zone);
		assertEquals(2, recs.get(0).zoneReach);
		assertFalse(recs.get(1).zone);
	}
}
