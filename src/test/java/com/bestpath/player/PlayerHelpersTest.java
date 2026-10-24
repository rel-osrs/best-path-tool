package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.Quest;
import org.junit.Test;

/** Debouncing, the per-account texts and quest name matching. */
public class PlayerHelpersTest
{
	@Test
	public void debouncerWaitsForQuiet()
	{
		Debouncer d = new Debouncer(2, 10);
		assertFalse(d.ready(100));
		d.mark(100);
		assertFalse(d.ready(100));
		assertFalse(d.ready(101));
		d.mark(101); // still changing
		assertFalse(d.ready(102));
		assertTrue(d.ready(103));
		d.clear();
		assertFalse(d.ready(104));
	}

	@Test
	public void debouncerActsAtTheLatestAfterMaxWait()
	{
		Debouncer d = new Debouncer(2, 10);
		for (int t = 0; t < 10; t++)
		{
			d.mark(t); // a change every tick
			assertFalse("tick " + t, d.ready(t));
		}
		d.mark(10);
		assertTrue(d.ready(10));
		d.clear();
		d.mark(11);
		assertFalse(d.ready(11)); // the clock restarts at the next mark
	}

	@Test(expected = IllegalArgumentException.class)
	public void debouncerChecksItsLimits()
	{
		new Debouncer(5, 2);
	}

	@Test
	public void bankTextIsSortedAndReadsBack()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(8007, 10);
		bank.put(995, 1_000_000);
		bank.put(563, 0); // placeholder: left out
		String t = ProfileText.encodeCounts(bank);
		assertEquals("995:1000000,8007:10", t);
		assertEquals(Map.of(995, 1_000_000, 8007, 10), ProfileText.decodeCounts(t));
		assertEquals(Map.of(), ProfileText.decodeCounts(""));
		assertNull(ProfileText.decodeCounts(null)); // never saved = unknown
		assertEquals(Map.of(1, 2), ProfileText.decodeCounts("x:1,1:2,3,:4,5:-1"));
		// the same contents give the same text: nothing is written again
		Map<Integer, Integer> again = new java.util.TreeMap<>(Map.of(8007, 10, 995, 1_000_000));
		assertEquals(t, ProfileText.encodeCounts(again));
	}

	@Test
	public void ownedItemsKeepOnlyTransportItems()
	{
		Set<Integer> owned = new TreeSet<>();
		Set<Integer> transportItems = Set.of(8007, 2552);
		assertTrue(ProfileText.addOwned(owned, List.of(8007, 379), transportItems));
		assertEquals(Set.of(8007), owned);
		assertFalse(ProfileText.addOwned(owned, List.of(8007, 379), transportItems)); // nothing new: not saved
		assertTrue(ProfileText.addOwned(owned, List.of(2552), transportItems));
		assertEquals("2552,8007", ProfileText.encodeIds(owned));
		assertEquals(Set.of(2552, 8007), ProfileText.decodeIds("2552,8007,bad,-3"));
		assertEquals(Set.of(), ProfileText.decodeIds(null));
	}

	@Test
	public void answersOnePerLine()
	{
		assertEquals(Set.of("a", "b c"), ProfileText.decodeLines("a\n\n  b c  \n"));
		assertEquals(Set.of(), ProfileText.decodeLines(null));
	}

	/** "No" answers are saved too; a file from before (yes answers, one per line) loads unchanged. */
	@Test
	public void answersYesAndNoRoundTrip()
	{
		java.util.Map<String, Boolean> old = ProfileText.decodeAnswers("Spirit tree planted at Port Sarim\n\n  Axe stored at the canoe stations \n");
		assertEquals(java.util.Map.of("Spirit tree planted at Port Sarim", true, "Axe stored at the canoe stations", true), old);
		java.util.Map<String, Boolean> both = new java.util.LinkedHashMap<>(old);
		both.put("Respawn point set to Prifddinas", false);
		String text = ProfileText.encodeAnswers(both);
		assertEquals("Axe stored at the canoe stations\nSpirit tree planted at Port Sarim\nno\tRespawn point set to Prifddinas", text);
		assertEquals(both, ProfileText.decodeAnswers(text));
		// the same words twice (any case): the last line wins
		assertEquals(java.util.Map.of("axe stored", false), ProfileText.decodeAnswers("Axe stored\nno\taxe stored"));
		assertEquals(java.util.Map.of(), ProfileText.decodeAnswers(null));
		assertEquals("", ProfileText.encodeAnswers(java.util.Map.of()));
		// the yes lines read by the old reader are the same words as before
		assertEquals(Set.of("Axe stored at the canoe stations", "Spirit tree planted at Port Sarim", "no\tRespawn point set to Prifddinas"),
			ProfileText.decodeLines(text));
	}

	@Test
	public void questNamesMatchRuneLite()
	{
		assertEquals(Quest.PRINCE_ALI_RESCUE, QuestNames.find("Prince Ali Rescue"));
		assertEquals(Quest.PRINCE_ALI_RESCUE, QuestNames.find("prince ali rescue"));
		assertEquals(Quest.THE_DIG_SITE, QuestNames.find("The Dig Site"));
		assertEquals(Quest.THE_DIG_SITE, QuestNames.find("Dig Site, The"));
		assertEquals(Quest.THE_DIG_SITE, QuestNames.find("Digsite"));
		assertEquals(Quest.THE_GREAT_BRAIN_ROBBERY, QuestNames.find("Great Brain Robbery"));
		assertEquals(Quest.ICTHLARINS_LITTLE_HELPER, QuestNames.find("Icthlarin's Little Helper"));
		assertEquals(Quest.DESERT_TREASURE_I, QuestNames.find("Desert Treasure I"));
		assertEquals(Quest.DESERT_TREASURE_II__THE_FALLEN_EMPIRE, QuestNames.find("Desert Treasure II"));
		assertNull(QuestNames.find("Recipe for")); // starts more than one quest name: not picked
		assertNull(QuestNames.find("No Such Quest"));
		assertNull(QuestNames.find(""));
	}
}
