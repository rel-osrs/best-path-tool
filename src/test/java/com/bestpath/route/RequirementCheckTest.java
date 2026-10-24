package com.bestpath.route;

import static org.junit.Assert.assertEquals;

import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.core.req.Skill;
import java.util.List;
import org.junit.Test;

/** Developer details: a requirement part by part with the player's result. */
public class RequirementCheckTest
{
	private static Requirement parse(String text)
	{
		return RequirementText.parse(text, RequirementText.ItemGroups.NONE);
	}

	@Test
	public void eachPartWithItsResult()
	{
		// the Shaman Caves' third Ancient Gate: which part holds it back
		Requirement gate = parse("members; Magic 56; item 567 used up; coins 30; one of (quest Legends' Quest | Agility 99)");
		PlayerState p = new PlayerState.Builder("p").members(true).level(Skill.MAGIC, 60).bank(995, 30)
			.quest("Legends' Quest", QuestState.DONE).build();
		assertEquals(List.of(
			"[ok] members",
			"[ok] Magic 56",
			"[no] item 567 used up",
			"[bank] coins 30",
			"[ok] one of:",
			"  [ok] quest Legends' Quest",
			"  [no] Agility 99"), RequirementCheck.lines(gate, p));
	}

	@Test
	public void choicesOfSeveralPartsAreGrouped()
	{
		Requirement r = parse("one of (Agility 30; quest Regicide | coins 10)");
		PlayerState p = new PlayerState.Builder("p").members(true).level(Skill.AGILITY, 30).carry(995, 10).build();
		assertEquals(List.of(
			"[ok] one of:",
			"  [no] all of:",
			"    [ok] Agility 30",
			"    [no] quest Regicide",
			"  [ok] coins 10"), RequirementCheck.lines(r, p));
	}

	@Test
	public void withoutAPlayerOrARequirement()
	{
		assertEquals(List.of("[?] Magic 56"), RequirementCheck.lines(parse("Magic 56"), null));
		assertEquals(List.of(), RequirementCheck.lines(Requirement.NONE, null));
		assertEquals(List.of(), RequirementCheck.lines(null, null));
	}

	@Test
	public void anOpenQuestionIsSaid()
	{
		PlayerState p = new PlayerState.Builder("p").members(true).askUnanswered(true).build();
		List<String> l = RequirementCheck.lines(parse("answer Respawn point set to Prifddinas"), p);
		assertEquals(1, l.size());
		assertEquals(true, l.get(0).endsWith("(not answered yet)"));
	}
}
