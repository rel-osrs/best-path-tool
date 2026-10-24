package com.bestpath.route;

import com.bestpath.core.req.Requirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The yes/no question asked under a step card for each player answer, and the short words of the answer as a need on
 * the card. The data's answers are statements ("Rope tied at the Lumbridge Swamp dark hole"); their questions are
 * written here by hand, one per answer in the data, because questions made from the words by rule read badly ("Is
 * rope tied at the Lumbridge Swamp dark hole?"). An answer not in the table (new data) is asked as "<words>: is that
 * so for you?". Pure.
 */
public final class AnswerQuestions
{
	/** Lower-case answer -> its question (tests read it). */
	static final Map<String, String> QUESTIONS = new HashMap<>();
	/** Lower-case answer -> its words as a need when the requirement wants the answer to be no ("not answer X"). */
	private static final Map<String, String> NOT_WORDS = new HashMap<>();

	static
	{
		q("Ancient tablet used on Xeric's talisman", "Have you used an ancient tablet on Xeric's talisman?");
		q("Digsite pendant Fossil Island teleport unlocked (Strange Machine)",
			"Have you unlocked the digsite pendant's Fossil Island teleport (at the Strange Machine)?");
		q("Dragontooth Island free travel bought (500 ecto-tokens)",
			"Have you bought free travel to Dragontooth Island (500 ecto-tokens)?");
		q("Feldip Hills eagle vine grown", "Have you grown the young vine by the Feldip Hills eagle?");
		q("Long rope tied to the rocks on the Viyeldi caves cliff",
			"Have you tied a long rope to the rocks on the Viyeldi caves cliff?");
		q("Magic mushtree House on the Hill unlocked", "Have you unlocked the House on the Hill magic mushtree?");
		q("Magic mushtree Mushroom Meadow unlocked", "Have you unlocked the Mushroom Meadow magic mushtree?");
		q("Magic mushtree Sticky Swamp unlocked", "Have you unlocked the Sticky Swamp magic mushtree?");
		q("Magic mushtree Verdant Valley unlocked", "Have you unlocked the Verdant Valley magic mushtree?");
		q("Respawn point set to Prifddinas", "Is your respawn point set to Prifddinas?");
		NOT_WORDS.put(key("Respawn point set to Prifddinas"), "Respawn point not set to Prifddinas");
		q("Rope tied at the Lumbridge Swamp dark hole", "Have you tied a rope at the dark hole in Lumbridge Swamp?");
	}

	private AnswerQuestions()
	{
	}

	private static void q(String answer, String question)
	{
		QUESTIONS.put(key(answer), question);
	}

	static String key(String answer)
	{
		return answer == null ? "" : answer.trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Every answer these requirements ask about (the data's words, each once whatever its case, sorted by its
	 * question), so the Settings tab's "Your answers" can list the ones not answered yet too.
	 */
	public static List<String> answersIn(Iterable<Requirement> requirements)
	{
		Map<String, String> byKey = new TreeMap<>();
		for (Requirement top : requirements)
		{
			if (top == null)
			{
				continue;
			}
			top.visit(r ->
			{
				if (r instanceof Requirement.Answer)
				{
					String a = ((Requirement.Answer) r).getAnswer();
					byKey.putIfAbsent(key(a), a);
				}
			});
		}
		List<String> out = new ArrayList<>(byKey.values());
		out.sort((a, b) ->
		{
			int c = question(a).compareToIgnoreCase(question(b));
			return c != 0 ? c : a.compareTo(b);
		});
		return Collections.unmodifiableList(out);
	}

	/** True when two answers' words are the same question (any case, spaces around ignored). */
	public static boolean same(String a, String b)
	{
		return key(a).equals(key(b));
	}

	/**
	 * The question for an answer (the data's words, any case): "Have you tied a rope at the dark hole in Lumbridge
	 * Swamp?"; an answer not in the table: "<words>: is that so for you?".
	 */
	public static String question(String answer)
	{
		String q = QUESTIONS.get(key(answer));
		if (q != null)
		{
			return q;
		}
		String a = answer == null ? "" : answer.trim();
		return a + ": is that so for you?";
	}

	/**
	 * Answers that can change in the game: the respawn point. These keep their green check on cards; every other answer
	 * is a one-time unlock, left off a card once met.
	 */
	private static final Set<String> CAN_CHANGE = Set.of(key("Respawn point set to Prifddinas"));

	/** True if the answer can change back in the game (its check stays on cards). */
	public static boolean canChange(String answer)
	{
		return CAN_CHANGE.contains(key(answer));
	}

	/**
	 * The answer's words as a need on a card: the data's words; for "not answer X", e.g. "Respawn point not set to
	 * ...".
	 */
	public static String needWords(String answer, boolean not)
	{
		String a = answer == null ? "" : answer.trim();
		if (!not)
		{
			return a;
		}
		String w = NOT_WORDS.get(key(a));
		return w != null ? w : "Not: " + a;
	}
}
