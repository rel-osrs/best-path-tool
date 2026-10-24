package com.bestpath.tool.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Requirement lines in plain words, as the Data Tool's Needs box shows them. */
class RequirementWordsTest
{
	@Test
	void eachNeededPartIsOneEntry() throws Exception
	{
		List<String> parts = RequirementWords.parts(ItemGroupFile.parse(
			"members; Agility 61; one of (quest Monkey Madness II | item monkey greegree); varbit 9653 >= 710; coins 200"));
		assertEquals(List.of("members world", "Agility level 61",
			"ONE OF: Monkey Madness II done  OR  carrying or wearing a monkey greegree (item group)",
			"game value varbit 9653 is at least 710", "200 coins"), parts);
	}

	@Test
	void questStatesNotAndNothing() throws Exception
	{
		assertTrue(RequirementWords.parts(ItemGroupFile.parse("")).isEmpty());
		assertEquals(List.of("Monkey Madness II not done yet", "NOT (the player says: no)"),
			RequirementWords.parts(ItemGroupFile.parse("quest Monkey Madness II not done; not answer no")));
		assertEquals(List.of("wearing item 772 or item 9084"), RequirementWords.parts(ItemGroupFile.parse("item 772/9084 worn")));
	}
}
