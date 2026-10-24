package com.bestpath.tool.wiki;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Reading NPC places from {@code {{LocLine}}} templates (made-up page text in the Wiki's style). */
class WikiClueKillsTest
{
	@Test
	void readsEveryTileOfEveryLocLine()
	{
		String page = "==Locations==\n{{LocTableHead}}\n"
			+ "{{LocLine\n|name = Ankou\n|location = [[Stronghold of Security|Stronghold]]: [[Sepulchre of Death]]\n|levels = 75\n"
			+ "|members = Yes\n|mapID = 18\n|plane = 0\n|x:2000,y:5000|x:2001,y:5002\n|mtype = pin\n}}\n"
			+ "{{LocLine|name=Ankou|location=[[Slayer Tower]] ({{FloorNumber|uk=1}})<ref group=c name=t>Task only.</ref>|mapID=-1|plane=1|x:3400,y:3550}}\n"
			+ "{{LocTableBottom}}";
		List<WikiClueKillsCli.Spawn> s = WikiClueKillsCli.spawns(page);
		assertEquals(3, s.size());
		assertEquals("Stronghold: Sepulchre of Death", s.get(0).location);
		assertEquals("18", s.get(0).mapId);
		assertEquals(2001, s.get(1).x);
		assertEquals(5002, s.get(1).y);
		assertEquals(0, s.get(1).plane);
		assertEquals("Slayer Tower (floor 1)", s.get(2).location);
		assertEquals(1, s.get(2).plane);
		assertEquals("-1", s.get(2).mapId);
	}

	@Test
	void readsBarePairTilesToo()
	{
		String page = "{{LocLine|name=Cave crawler|location=[[Fremennik Slayer Dungeon]]|levels=23|mapID=-1|plane=0"
			+ "|3157,9698|3163,9699\n|x:3170,y:9700|mtype=pin}}";
		List<WikiClueKillsCli.Spawn> s = WikiClueKillsCli.spawns(page);
		assertEquals(3, s.size());
		assertEquals(3157, s.get(0).x);
		assertEquals(9698, s.get(0).y);
		assertEquals(3163, s.get(1).x);
		assertEquals(9699, s.get(1).y);
		assertEquals(3170, s.get(2).x);
		assertEquals("Fremennik Slayer Dungeon", s.get(0).location);
		// a named parameter holding two numbers is not a tile
		assertEquals(0, WikiClueKillsCli.spawns("{{LocLine|name=Rat|levels=1,2|location=Varrock}}").size());
	}

	@Test
	void pipesInsideLinksDoNotSplitParameters()
	{
		assertEquals(List.of("a=[[b|c]]", "d={{e|f}}", "g"), WikiClueKillsCli.topLevelParts("a=[[b|c]]|d={{e|f}}|g"));
	}

	@Test
	void aPageWithoutLocLinesGivesNothing()
	{
		assertEquals(0, WikiClueKillsCli.spawns("{{Map|x=3176|y=2477|caption=The entrance to the cave.}}").size());
	}
}
