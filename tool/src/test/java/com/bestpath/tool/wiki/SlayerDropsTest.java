package com.bestpath.tool.wiki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Wiki's drop tables, read for each Slayer place's own special loot. */
class SlayerDropsTest
{
	private static final String MAGE = "{{Infobox Monster|name=Spiritual mage}}\n"
		+ "==Drops==\n"
		+ "===Other===\n"
		+ "{{DropsTableHead}}\n"
		+ "{{DropsLine|name=Pure essence|quantity=90 (noted)|rarity=6/128}}\n"
		+ "{{DropsLine|name=Dragon boots|quantity=1|rarity=1/128}}\n"
		+ "{{DropsTableBottom}}\n"
		+ "===Tertiary===\n"
		+ "{{DropsLine|name=Looting bag|quantity=1|rarity=1/3|raritynotes=<ref group=d>Only dropped by those found in the "
		+ "[[Wilderness God Wars Dungeon]].</ref>}}\n"
		+ "{{RareDropTable|1/128}}\n"
		+ "==Drops (Ancient Prison)==\n"
		+ "{{DropsLine|name=Ancient ceremonial top|quantity=1|rarity={{#expr:1/640}}}}\n"
		+ "{{Uniques/Spiritual mage}}\n"
		+ "==Locations==\n"
		+ "{{DropsLine|name=Not a drop|rarity=1}}\n";

	@Test
	void everyDropsSectionAndItsVersion()
	{
		Map<String, String> templates = Map.of("Template:Uniques/Spiritual mage",
			"{{DropsLine|name=Nihil shard|rarity=1/128|raritynotes=<ref>Only dropped in [[Ancient Prison]]</ref>}}");
		List<SlayerDrops.Drop> d = SlayerDrops.parse(MAGE, templates::get);
		assertEquals("[Pure essence 6/128, Dragon boots 1/128, Looting bag 1/3, Ancient Prison: Ancient ceremonial top {{#expr:1/640}},"
			+ " Ancient Prison: Nihil shard 1/128]", d.toString(), "a later section that is not a drop table is not read");
		assertEquals("Wilderness God Wars Dungeon", d.get(2).onlyIn);
		assertEquals("", d.get(1).onlyIn);
		assertEquals("Ancient Prison", d.get(4).onlyIn);
		assertEquals(Set.of("Template:Uniques/Spiritual mage"), SlayerDrops.included(MAGE));
		assertEquals(4, SlayerDrops.parse(MAGE, t -> null).size(), "a table not fetched is left out");
	}

	@Test
	void versionsAndNotes()
	{
		assertEquals("", SlayerDrops.version("Drops"));
		assertEquals("Level 21", SlayerDrops.version("Drops (Level 21)"));
		assertNull(SlayerDrops.version("Locations"));
		assertEquals("Ruins of Tapoyauik", SlayerDrops.onlyIn("<ref name=row group=d>Only dropped in [[Ruins of Tapoyauik]]</ref>"));
		assertEquals("Ruins of Tapoyauik", SlayerDrops.onlyIn("Only dropped in Ruins of Tapoyauik."));
		assertEquals("", SlayerDrops.onlyIn("The rarity changes to 1/1,000 while on a Gryphon [[Slayer task]]."));
		List<String[]> lines = SlayerDrops.dropsLines("{{DropsLine|name=Dragon boots|quantity=[[x|y]]|rarity=1/128}} {{DropsLine|rarity=1}}");
		assertEquals(1, lines.size(), "a link's | inside an argument is not a new argument; a line without a name is left out");
		assertEquals("Dragon boots", lines.get(0)[0]);
		assertEquals("1/128", lines.get(0)[1]);
	}

	/** A place's own monsters: its amount and tile pages before the task's whole list in its monsters column. */
	@Test
	void theMonstersOfAPlace()
	{
		Map<String, String[]> saved = new HashMap<>();
		for (String t : List.of("Vampyre Juvinate", "Vyrewatch", "Vyrewatch Sentinel", "Basilisk", "Basilisk Knight", "Blue dragon",
			"Baby blue dragon", "Brutal blue dragon", "Zombie", "Chilled jelly", "Suqah"))
		{
			saved.put(t, new String[]{t, "1", "{{Infobox Monster|name=" + t + "}}"});
		}
		saved.put("Vyrewatch Sentinels", saved.get("Vyrewatch Sentinel"));
		assertEquals(Set.of("Vampyre Juvinate", "Vyrewatch", "Vyrewatch Sentinel"), WikiSlayerDropsCli.monstersOf("Vampyre Juvinate",
			"6 Vampyre Juvinates, 8 Vyrewatch, 19 Vyrewatch Sentinels", "-", "Vampyres", saved));
		assertEquals(Set.of("Basilisk Knight"), WikiSlayerDropsCli.monstersOf("Basilisk / Basilisk Knight", "20 Basilisk Knights", "-",
			"Basilisks", saved), "the place's own count, not the task's whole list");
		assertEquals(Set.of("Basilisk", "Basilisk Knight"), WikiSlayerDropsCli.monstersOf("Basilisk / Basilisk Knight", "?", "-",
			"Basilisks", saved), "nothing more: the monsters column");
		assertEquals(Set.of("Blue dragon", "Baby blue dragon"), WikiSlayerDropsCli.monstersOf("Blue dragon", "5 Adults 2 Babies", "-",
			"Blue Dragons", saved));
		assertEquals(Set.of("Brutal blue dragon"), WikiSlayerDropsCli.monstersOf("Blue dragon", "2 Brutals", "-", "Blue Dragons", saved));
		assertEquals(Set.of("Zombie"), WikiSlayerDropsCli.monstersOf("", "Many", "Zombie@15373734", "Zombies", saved));
		assertEquals(Set.of("Chilled jelly"), WikiSlayerDropsCli.monstersOf("Chilled Jelly", "10", "-", "Jellies", saved));
		assertEquals(Set.of("Suqah"), WikiSlayerDropsCli.monstersOf("Suqah (single-way combat)", "6", "-", "Suqahs", saved));
		assertEquals(Set.of(), WikiSlayerDropsCli.monstersOf("", "34", "-", "Goblins", saved));
	}
}
