package com.bestpath.core.req;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unlocks the plugin detects instead of asking: an item in the collection log and a grown
 * spirit tree. Reading and writing the lines, and checking them against players.
 */
class DetectedUnlocksTest
{
	private static final RequirementText.ItemGroups GROUPS = RequirementText.ItemGroups.of(Map.of(
		"fire cape", new int[]{6570}));

	private static Requirement parse(String s)
	{
		return RequirementText.parse(s, GROUPS);
	}

	private static Check.Outcome check(String line, PlayerState p)
	{
		return parse(line).check(p).getOutcome();
	}

	@Test
	void linesReadAndWriteBack()
	{
		assertEquals("collection log 6570", parse("collection log 6570").toText());
		assertEquals("collection log 6570/21295", parse("Collection Log 6570/21295").toText());
		assertEquals("collection log fire cape", parse("collection log fire cape").toText());
		assertEquals("spirit tree Port Sarim", parse("spirit tree   Port Sarim").toText());
		assertEquals("one of (item 6570 | collection log 6570)", parse("one of (item 6570 | collection log 6570)").toText());
		Requirement.CollectionLog c = (Requirement.CollectionLog) parse("collection log 6570/21295");
		assertEquals(2, c.getItemIds().length);
		assertEquals("Port Sarim", RequirementDiagnostics.place(((Requirement.SpiritTree) parse("spirit tree Port Sarim"))));
	}

	@Test
	void badLines()
	{
		assertThrows(RequirementText.ParseException.class, () -> parse("collection log"));
		assertThrows(RequirementText.ParseException.class, () -> parse("collection 6570"));
		assertThrows(RequirementText.ParseException.class, () -> parse("collection log no such group"));
		assertThrows(RequirementText.ParseException.class, () -> parse("spirit tree"));
		assertThrows(RequirementText.ParseException.class, () -> parse("spirit Port Sarim"));
	}

	@Test
	void collectionLogIsMetOnlyWhenSeen()
	{
		PlayerState none = new PlayerState.Builder("none").members(true).build();
		PlayerState cape = new PlayerState.Builder("cape").members(true).collectionLog(6570).build();
		assertEquals(Check.Outcome.NOT_MET, check("collection log 6570", none));
		assertEquals(Check.Outcome.MET, check("collection log 6570", cape));
		assertEquals(Check.Outcome.MET, check("collection log 21295/6570", cape));
		assertEquals(Check.Outcome.MET, check("collection log 6570", PlayerStateBuilder.maxed()));
		// never asks: nothing rests on an answer, even for the plugin's player
		PlayerState asking = new PlayerState.Builder("asks").askUnanswered(true).build();
		Check c = parse("collection log 6570").check(asking);
		assertEquals(Check.Outcome.NOT_MET, c.getOutcome());
		assertTrue(c.getUnanswered().isEmpty());
	}

	@Test
	void spiritTreeIsMetOnlyWhenSeenGrown()
	{
		PlayerState none = new PlayerState.Builder("none").members(true).build();
		PlayerState sarim = new PlayerState.Builder("sarim").members(true).spiritTree("Port Sarim").build();
		assertEquals(Check.Outcome.NOT_MET, check("spirit tree Port Sarim", none));
		assertEquals(Check.Outcome.MET, check("spirit tree port sarim", sarim));
		assertEquals(Check.Outcome.NOT_MET, check("spirit tree Brimhaven", sarim));
		assertEquals(Check.Outcome.MET, check("spirit tree Brimhaven", PlayerStateBuilder.maxed()));
		assertEquals(Check.Outcome.MET, check("not spirit tree Brimhaven", sarim));
	}

	@Test
	void canoeAxeIsMetOnlyWhenSeenStored()
	{
		// detected (the station NPC takes or hands back the axe), never asked
		assertEquals("canoe axe stored", parse("Canoe  Axe STORED").toText());
		assertEquals("one of (item 1351 | canoe axe stored)", parse("one of (item 1351 | canoe axe stored)").toText());
		assertThrows(RequirementText.ParseException.class, () -> parse("canoe axe"));
		assertThrows(RequirementText.ParseException.class, () -> parse("canoe axe stored here"));
		PlayerState none = new PlayerState.Builder("none").build(); // test players: a known "no"
		PlayerState stored = new PlayerState.Builder("stored").canoeAxeStored(true).build();
		PlayerState notKnown = new PlayerState.Builder("?").canoeAxeStored(null).build();
		assertEquals(Check.Outcome.NOT_MET, check("canoe axe stored", none));
		assertEquals(Check.Outcome.MET, check("canoe axe stored", stored));
		// not known = maybe stored (a question mark on the card), never assumed absent
		assertEquals(Check.Outcome.MET, check("canoe axe stored", notKnown));
		assertEquals(Boolean.FALSE, none.getCanoeAxeStored());
		assertEquals(null, notKnown.getCanoeAxeStored());
		assertEquals(Check.Outcome.MET, check("canoe axe stored", PlayerStateBuilder.maxed()));
		assertEquals(Check.Outcome.MET, check("one of (item 1351 | canoe axe stored)", stored));
		assertEquals(Check.Outcome.MET, check("one of (item 1351 | canoe axe stored)",
			new PlayerState.Builder("axe").carry(1351, 1).build()));
		PlayerState asking = new PlayerState.Builder("asks").askUnanswered(true).build();
		assertTrue(parse("canoe axe stored").check(asking).getUnanswered().isEmpty());
	}
}
