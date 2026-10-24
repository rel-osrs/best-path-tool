package com.bestpath.core.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.bestpath.core.data.PlayerTransports;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.Check.Outcome;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Player answers asked on the step card: for the plugin's player an answer not given yet does not
 * stop a route; it counts as yes until the player says no, so "answer X" is met and "not answer X" is not, each
 * resting on the answer (the card asks it); an answer given is exact. The Prifddinas respawn point is the other way
 * round (it defaults to no for all players). The Data Tool's
 * test players are unchanged: not given = no.
 */
class PlayerAnswersTest
{
	private static final String PRIF = "Respawn point set to Prifddinas";
	private static final String ROPE = "Rope tied at the Lumbridge Swamp dark hole";

	private static Requirement parse(String line)
	{
		try
		{
			return RequirementText.parse(line, RequirementText.ItemGroups.of(Map.of()));
		}
		catch (RequirementText.ParseException e)
		{
			throw new AssertionError(e);
		}
	}

	private static Check check(String line, PlayerState p)
	{
		return parse(line).check(p);
	}

	private static PlayerState.Builder asking()
	{
		return new PlayerState.Builder("Plugin player").members(true).askUnanswered(true);
	}

	@Test
	void unansweredCountsAsYesRestingOnTheAnswer()
	{
		PlayerState p = asking().build();
		Check yes = check("answer " + ROPE, p);
		Check not = check("not answer " + ROPE, p);
		assertEquals(Outcome.MET, yes.getOutcome());
		assertEquals(List.of(ROPE), yes.getUnanswered());
		assertEquals(Outcome.NOT_MET, not.getOutcome());
		assertEquals(List.of(ROPE), not.getUnanswered());
		assertNull(p.answer(ROPE));
		assertFalse(PlayerStateDiagnostics.hasAnswer(p, ROPE));
	}

	@Test
	void thePrifddinasRespawnPointIsNoUntilAnswered()
	{
		PlayerState p = asking().build();
		assertTrue(PlayerState.isNoUntilAnswered(PRIF.toUpperCase()));
		assertFalse(PlayerState.isNoUntilAnswered(ROPE));
		Check yes = check("answer " + PRIF, p);
		Check not = check("not answer " + PRIF, p);
		assertEquals(Outcome.NOT_MET, yes.getOutcome());
		assertEquals(List.of(PRIF), yes.getUnanswered(), "the card can still ask it");
		assertEquals(Outcome.MET, not.getOutcome());
		assertEquals(List.of(PRIF), not.getUnanswered());
		assertNull(p.answer(PRIF));
	}

	@Test
	void answeredIsExact()
	{
		PlayerState yes = asking().answer(PRIF.toUpperCase(), true).build();
		assertEquals(Outcome.MET, check("answer " + PRIF, yes).getOutcome());
		assertTrue(check("answer " + PRIF, yes).getUnanswered().isEmpty());
		assertEquals(Outcome.NOT_MET, check("not answer " + PRIF, yes).getOutcome());
		assertTrue(check("not answer " + PRIF, yes).getUnanswered().isEmpty());

		PlayerState no = asking().answer(PRIF, false).build();
		assertEquals(Outcome.NOT_MET, check("answer " + PRIF, no).getOutcome());
		assertEquals(List.of("answer " + PRIF), check("answer " + PRIF, no).getMissing());
		assertEquals(Outcome.MET, check("not answer " + PRIF, no).getOutcome());
		assertTrue(check("not answer " + PRIF, no).getUnanswered().isEmpty());
		assertEquals(Boolean.FALSE, no.answer(PRIF));
		assertEquals(Map.of(PRIF.toLowerCase(), false), no.getAnswerMap());
		assertTrue(answers(no).isEmpty(), "getAnswers lists the yes answers only");
	}

	@Test
	void testPlayersAreUnchangedNotGivenMeansNo()
	{
		PlayerState p = new PlayerState.Builder("Test player").members(true).build();
		assertFalse(p.isAskUnanswered());
		assertEquals(Outcome.NOT_MET, check("answer " + PRIF, p).getOutcome());
		assertEquals(Outcome.MET, check("not answer " + PRIF, p).getOutcome());
		assertTrue(check("not answer " + PRIF, p).getUnanswered().isEmpty());
		// a "no" given to a test player is the same as not given
		PlayerState no = new PlayerState.Builder("Test player").answer(PRIF, false).build();
		assertEquals(Outcome.NOT_MET, check("answer " + PRIF, no).getOutcome());
		// Maxed says yes to everything
		assertEquals(Outcome.MET, check("answer " + PRIF, PlayerStateBuilder.maxed()).getOutcome());
		assertEquals(Outcome.NOT_MET, check("not answer " + PRIF, PlayerStateBuilder.maxed()).getOutcome());
	}

	@Test
	void oneOfPrefersAChoiceWithoutAQuestion()
	{
		String line = "one of (item 954 used up | answer " + ROPE + ")";
		Check carried = check(line, asking().carry(954, 1).build());
		assertEquals(Outcome.MET, carried.getOutcome());
		assertTrue(carried.getUnanswered().isEmpty(), "a carried rope: nothing to ask");
		Check none = check(line, asking().build());
		assertEquals(Outcome.MET, none.getOutcome());
		assertEquals(List.of(ROPE), none.getUnanswered());
		// a rope only in the bank: asked first (met now if yes); after a no, the bank
		Check banked = check(line, asking().bank(954, 1).build());
		assertEquals(Outcome.MET, banked.getOutcome());
		assertEquals(List.of(ROPE), banked.getUnanswered());
		Check bankedNo = check(line, asking().bank(954, 1).answer(ROPE, false).build());
		assertEquals(Outcome.NEEDS_BANK, bankedNo.getOutcome());
		assertTrue(bankedNo.getUnanswered().isEmpty());
		assertEquals(Outcome.NOT_MET, check(line, asking().answer(ROPE, false).build()).getOutcome());
		// the choice shown on the card is the one the check used
		assertEquals(0, ((Requirement.AnyOf) parse(line)).chosen(asking().carry(954, 1).build()));
		assertEquals(1, ((Requirement.AnyOf) parse(line)).chosen(asking().bank(954, 1).build()));
	}

	@Test
	void allCollectsEveryPartsQuestion()
	{
		Check c = check("answer A; members; answer B", asking().build());
		assertEquals(Outcome.MET, c.getOutcome());
		assertEquals(List.of("A", "B"), c.getUnanswered());
		// not met whatever is answered: nothing to ask
		Check out = check("Agility 99; answer A", asking().build());
		assertEquals(Outcome.NOT_MET, out.getOutcome());
		assertTrue(out.getUnanswered().isEmpty());
	}

	@Test
	void notOfSeveralPartsAsksOnlyWhenTheAnswerDecides()
	{
		// "not (Agility 99 and A)": met whatever A is, for a level-1 player
		Requirement r = new Requirement.Not(Requirement.all(List.of(new Requirement.SkillLevel(Skill.AGILITY, 99),
			new Requirement.Answer("A"))));
		Check c = r.check(asking().build());
		assertEquals(Outcome.MET, c.getOutcome());
		assertTrue(c.getUnanswered().isEmpty(), c.toString());
		// "not (members and A)": met only if A is no (not answered: taken as yes, so not met until a no)
		Requirement m = new Requirement.Not(Requirement.all(List.of(new Requirement.Answer("A"), parse("members"))));
		Check d = m.check(asking().build());
		assertEquals(Outcome.NOT_MET, d.getOutcome());
		assertEquals(List.of("A"), d.getUnanswered());
		assertEquals(Outcome.NOT_MET, m.check(asking().answer("A", true).build()).getOutcome());
		assertEquals(Outcome.MET, m.check(asking().answer("A", false).build()).getOutcome());
		// "not not A" is "A" (yes until answered)
		Requirement nn = new Requirement.Not(new Requirement.Not(new Requirement.Answer("A")));
		assertEquals(Outcome.MET, nn.check(asking().build()).getOutcome());
		assertEquals(List.of("A"), nn.check(asking().build()).getUnanswered());
		assertEquals(Outcome.NOT_MET, nn.check(asking().answer("A", false).build()).getOutcome());
	}

	private static TransportLine teleport(String id, int x, String req)
	{
		return TransportLine.builder(id, TransportLine.Kind.TELEPORT).name("Respawn " + id).category("TELEPORT")
			.end(x, 3219, 0).ticks(4).requirements(req).teleportCategory("spell").build();
	}

	private static List<String> ids(List<Transport> ts)
	{
		List<String> out = new ArrayList<>();
		for (Transport t : ts)
		{
			out.add(t.getId());
		}
		return out;
	}

	/**
	 * The route search and the "no route" explanation (the same checks): while unanswered the Prifddinas respawn point
	 * counts as no, so only the Lumbridge respawn teleport; a yes gives the Prifddinas one.
	 */
	@Test
	void unansweredRespawnIsTakenAsNoUntilAYes()
	{
		List<TransportLine> lines = List.of(teleport("lumbridge", 3221, "members; not answer " + PRIF),
			teleport("prifddinas", 3263, "members; answer " + PRIF));
		PlayerTransports pt = new PlayerTransports(lines, Tables.empty());
		assertEquals(List.of("lumbridge"), ids(pt.build(asking().build(), 0, null).getTransports()));
		assertEquals(List.of("prifddinas"), ids(pt.build(asking().answer(PRIF, true).build(), 0, null).getTransports()));
		assertEquals(List.of("lumbridge"), ids(pt.build(asking().answer(PRIF, false).build(), 0, null).getTransports()));
		// a test player (Data Tool): not given = no, as before
		assertEquals(List.of("lumbridge"), ids(pt.build(new PlayerState.Builder("t").members(true).build(), 0, null)
			.getTransports()));
		List<String> usable = new ArrayList<>();
		for (PlayerTransports.Candidate c : pt.everything(asking().build(), null, null).getCandidates())
		{
			if (c.isUsableNow())
			{
				usable.add(c.getTransport().getId());
			}
		}
		assertEquals(List.of("lumbridge"), usable, "the explanation's transports usable now");
	}

	/** The answers the player said yes to (lower case). */
	private static Set<String> answers(PlayerState p)
	{
		Set<String> yes = new HashSet<>();
		p.answers.forEach((k, v) ->
		{
			if (v)
			{
				yes.add(k);
			}
		});
		return Collections.unmodifiableSet(yes);
	}
}
