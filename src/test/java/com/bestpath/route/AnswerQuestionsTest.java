package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.player.AvailabilityDiff;
import com.bestpath.player.RequirementInputs;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Player answers asked on the step card: every answer in the exported data has a hand-written
 * question; the card's info lists the questions the route rests on and marks each answer need (check, X, or none while
 * not answered); an answer given makes the availability check search the route again.
 */
public class AnswerQuestionsTest
{
	private static final RequirementText.ItemGroups GROUPS = RequirementText.ItemGroups.of(Map.of());
	private static final String TREE = "Rope tied at the Lumbridge Swamp dark hole";
	private static final String PRIF = "Respawn point set to Prifddinas";
	private static final Set<Cooldown> ALL_COOLDOWNS = EnumSet.allOf(Cooldown.class);

	private static Requirement parse(String text)
	{
		return RequirementText.parse(text, GROUPS);
	}

	/** Every "answer ..." in the plugin's exported data (transports, networks, clues). */
	static TreeSet<String> answersInData() throws Exception
	{
		TreeSet<String> out = new TreeSet<>();
		Pattern p = Pattern.compile("answer ([^;|\\t]+)");
		for (String file : new String[]{"transports.tsv", "networks.tsv", "clues.tsv"})
		{
			InputStream in = AnswerQuestionsTest.class.getResourceAsStream("/com/bestpath/data/" + file);
			if (in == null)
			{
				continue;
			}
			try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				for (String line = r.readLine(); line != null; line = r.readLine())
				{
					Matcher m = p.matcher(line);
					while (m.find())
					{
						out.add(balanced(m.group(1).trim()));
					}
				}
			}
		}
		return out;
	}

	/** The words without the closing brackets of a "one of (...)" around them. */
	private static String balanced(String s)
	{
		String t = s;
		while (t.endsWith(")") && t.chars().filter(c -> c == ')').count() > t.chars().filter(c -> c == '(').count())
		{
			t = t.substring(0, t.length() - 1).trim();
		}
		return t;
	}

	@Test
	public void everyAnswerInTheDataHasAWrittenQuestion() throws Exception
	{
		TreeSet<String> answers = answersInData();
		assertTrue("the exported data has answers: " + answers, answers.size() >= 10); // 13: some unlocks are detected, not asked
		List<String> missing = new ArrayList<>();
		for (String a : answers)
		{
			if (!AnswerQuestions.QUESTIONS.containsKey(AnswerQuestions.key(a)))
			{
				missing.add(a);
			}
			assertTrue(a, AnswerQuestions.question(a).endsWith("?"));
		}
		assertEquals("answers without a hand-written question", List.of(), missing);
		assertTrue(answers.contains("Digsite pendant Fossil Island teleport unlocked (Strange Machine)"));
	}

	@Test
	public void questionsAndNeedWords()
	{
		assertEquals("Have you tied a rope at the dark hole in Lumbridge Swamp?", AnswerQuestions.question(TREE));
		assertEquals("Have you tied a rope at the dark hole in Lumbridge Swamp?",
			AnswerQuestions.question(" rope TIED at the Lumbridge Swamp dark hole "));
		// the canoe axe is detected: no written question any more
		assertEquals("Axe stored at the canoe stations: is that so for you?",
			AnswerQuestions.question("Axe stored at the canoe stations"));
		assertEquals("Is your respawn point set to Prifddinas?", AnswerQuestions.question(PRIF));
		assertEquals("Fed the cat: is that so for you?", AnswerQuestions.question("Fed the cat"));
		assertEquals(TREE, AnswerQuestions.needWords(TREE, false));
		assertEquals("Respawn point not set to Prifddinas", AnswerQuestions.needWords(PRIF, true));
		assertEquals("Not: Fed the cat", AnswerQuestions.needWords("Fed the cat", true));
	}

	private static TransportLine line(String name, String req)
	{
		return TransportLine.builder("t-" + name, TransportLine.Kind.TELEPORT).name(name).category("TELEPORT")
			.end(3221, 3219, 0).ticks(4).requirements(req).teleportCategory("spell").build();
	}

	private static PlayerState.Builder plugin()
	{
		return new PlayerState.Builder("p").members(true).askUnanswered(true);
	}

	private static StepInfo.Need answerNeed(StepInfo i)
	{
		for (StepInfo.Need n : i.getNeeds())
		{
			if (n.getType() == StepInfo.Need.Type.ANSWER)
			{
				return n;
			}
		}
		throw new AssertionError("no answer need in " + i.getNeeds());
	}

	@Test
	public void theCardAsksWhatTheRouteRestsOnAndMarksAnswers()
	{
		String req = "members; not answer " + PRIF;
		TransportLine lumbridge = line("Respawn Teleport: Lumbridge", req);
		// not answered: asked, no mark
		StepInfo open = StepInfo.of(lumbridge, false, parse(req), null, plugin().build(), null);
		assertEquals(List.of(PRIF), open.getQuestions());
		assertEquals("Respawn point not set to Prifddinas", answerNeed(open).getText());
		assertEquals(NeedStatus.UNKNOWN, answerNeed(open).getStatus());
		// answered no (as this one needs): a check, nothing asked
		StepInfo no = StepInfo.of(lumbridge, false, parse(req), null, plugin().answer(PRIF, false).build(), null);
		assertEquals(List.of(), no.getQuestions());
		assertEquals(NeedStatus.HAVE, answerNeed(no).getStatus());
		// answered yes: an X (the route would not use it)
		StepInfo yes = StepInfo.of(lumbridge, false, parse(req), null, plugin().answer(PRIF, true).build(), null);
		assertEquals(NeedStatus.MISSING, answerNeed(yes).getStatus());
		// the Lumbridge Swamp rope: the answer as it is; a one-time unlock, so left off the card once answered yes
		String tree = "answer " + TREE;
		StepInfo t = StepInfo.of(line("Dark hole", tree), false, parse(tree), null, plugin().build(), null);
		assertEquals(List.of(TREE), t.getQuestions());
		assertEquals(TREE, answerNeed(t).getText());
		StepInfo answered = StepInfo.of(line("Dark hole", tree), false, parse(tree), null,
			plugin().answer(TREE, true).build(), null);
		assertEquals(List.of(), answered.getQuestions());
		assertTrue(answered.getNeeds().stream().noneMatch(n -> n.getType() == StepInfo.Need.Type.ANSWER));
		// without a player (previews): nothing asked
		assertEquals(List.of(), StepInfo.of(lumbridge, false, parse(req), null).getQuestions());
	}

	@Test
	public void aCarriedRopeAsksNothing()
	{
		String req = "one of (item 954 used up | answer Rope tied at the Lumbridge Swamp dark hole)";
		TransportLine hole = line("Dark hole", req);
		assertEquals(List.of(), StepInfo.of(hole, false, parse(req), null, plugin().carry(954, 1).build(), null).getQuestions());
		assertEquals(List.of("Rope tied at the Lumbridge Swamp dark hole"),
			StepInfo.of(hole, false, parse(req), null, plugin().build(), null).getQuestions());
	}

	@Test
	public void anAnswerGivenSearchesAgain()
	{
		RequirementInputs in = RequirementInputs.collect(List.of(parse("answer " + TREE), parse("members; not answer " + PRIF)));
		PlayerState open = plugin().build();
		// Yes to a question the route rested on: the outcome stays "met", the card must stop asking: search again
		assertTrue(AvailabilityDiff.needsReroute(open, plugin().answer(TREE, true).build(), in, ALL_COOLDOWNS));
		// No: the transport is out: search again
		assertTrue(AvailabilityDiff.needsReroute(open, plugin().answer(TREE, false).build(), in, ALL_COOLDOWNS));
		// cleared again: asked again
		assertTrue(AvailabilityDiff.needsReroute(plugin().answer(TREE, true).build(), open, in, ALL_COOLDOWNS));
		// an answer no transport mentions changes nothing
		assertFalse(AvailabilityDiff.needsReroute(open, plugin().answer("Fed the cat", true).build(), in, ALL_COOLDOWNS));
		assertEquals(List.of("answers changed"), AvailabilityDiff.changes(open, plugin().answer(TREE, false).build(), in));
	}
}
