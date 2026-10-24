package com.bestpath.tool.clue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** data/clues/clue-steps.tsv: written and read back unchanged. */
class ClueFileTest
{
	@TempDir
	Path dir;

	private static ClueStep sample()
	{
		return new ClueStep("emote-2084", "emote", "beginner", List.of(), "Blow a raspberry at Aris; equip a ring.", "coord", List.of(), "",
			3203, 3424, 0, "Aris' tent.",
			List.of(new ClueStep.Place("stash", List.of(34736), "Inconspicuous bush (beginner)", 3206, 3422, 0),
				new ClueStep.Place("key-npc", List.of(), "?", 1, 2, 3)),
			List.of("wear 1654 Gold necklace (front)", "wear 1635 Gold ring (ring)"), List.of("emote 19"),
			"cache 2735 table 9 row 2084 -> table 18 row 347", List.of(), "emote 19 Raspberry", "item 1654 worn; item 1635 worn",
			"varbit 8254 ; beginner ; Aris's tent");
	}

	@Test
	void roundTrip() throws Exception
	{
		File f = dir.resolve("clue-steps.tsv").toFile();
		ClueStep kill = new ClueStep("cryptic-1455", "cryptic", "elite", List.of(12073, 12074), "Ghostly bones.", "kill", List.of(2514, 2515),
			"Ankou", -1, -1, -1, "Kill an Ankou.", List.of(), List.of(), List.of(), "cache 2735 table 8 row 1455 -> table 20 row 819",
			List.of("kill step: no tile"));
		ClueFile.write(f, List.of(sample(), kill), List.of("header line"));
		List<String> lines = Files.readAllLines(f.toPath());
		assertEquals("# header line", lines.get(0));
		List<ClueStep> back = ClueFile.read(f);
		assertEquals(2, back.size());
		ClueStep e = back.get(0);
		assertEquals("emote-2084", e.key);
		assertEquals("Blow a raspberry at Aris; equip a ring.", e.text); // ; inside a single cell stays
		assertEquals(3203, e.x);
		assertEquals(2, e.places.size());
		assertEquals("Inconspicuous bush (beginner)", e.places.get(0).name);
		assertEquals(List.of(34736), e.places.get(0).ids);
		assertEquals(List.of(), e.places.get(1).ids);
		assertEquals(3, e.places.get(1).plane);
		assertEquals(2, e.needs.size());
		assertEquals(List.of(), e.items);
		assertEquals("emote 19 Raspberry", e.action);
		assertEquals("item 1654 worn; item 1635 worn", e.requirement, "a requirement line keeps its own semicolons");
		assertEquals(8254, e.stashVarbit());
		assertEquals("Aris's tent", e.stashPlace());
		ClueStep k = back.get(1);
		assertFalse(k.hasTile());
		assertEquals(List.of(2514, 2515), k.targetIds);
		assertEquals(List.of(12073, 12074), k.items);
		assertEquals(List.of("kill step: no tile"), k.checks);
		assertEquals(ClueFile.line(sample()), ClueFile.line(e));
	}

	@Test
	void listEntriesNeverSplitOnTheirOwnSemicolons()
	{
		ClueStep s = new ClueStep("x-1", "x", "easy", List.of(), "", "none", List.of(), "", -1, -1, -1, "", List.of(),
			List.of("one; two"), List.of(), "", List.of());
		List<ClueStep> back = ClueFile.parse(List.of(String.join("\t", ClueFile.COLUMNS), ClueFile.line(s)));
		assertEquals(List.of("one, two"), back.get(0).needs);
	}

	@Test
	void wrongColumnsAreRefused()
	{
		assertThrows(IllegalArgumentException.class, () -> ClueFile.parse(List.of("key\ttype")));
	}
}
