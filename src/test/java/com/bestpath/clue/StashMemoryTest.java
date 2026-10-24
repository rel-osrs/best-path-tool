package com.bestpath.clue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.player.DelayedSave;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.path.Tiles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/** STASH units remembered per account: the newest news wins, the items to put back, and the saved texts. */
public class StashMemoryTest
{
	private static final int UNIT = 5229;
	private static final int OTHER = 5230;
	private static final String STEP = "STEP:medium:emote-1234,";

	@Test
	public void newestNewsWins()
	{
		StashMemory m = new StashMemory();
		assertNull(m.filled(UNIT));
		assertTrue(m.set(UNIT, true)); // the clue helper
		assertTrue(m.filled(UNIT));
		assertFalse("the same news again changes nothing", m.set(UNIT, true));
		assertTrue(m.fromChart(Map.of(UNIT, false, OTHER, true))); // the chart, later
		assertFalse(m.filled(UNIT));
		assertTrue(m.filled(OTHER));
		assertTrue(m.deposited(UNIT)); // the player put the items in
		assertTrue(m.filled(UNIT));
	}

	@Test
	public void chartIsNotBelievedWhenItHidesFilled()
	{
		int brittle = VarbitID.HH_CONSTRUCTED_MASTER_SAIL;
		StashMemory m = new StashMemory();
		m.set(brittle, true);
		assertFalse(m.fromChart(Map.of(brittle, false)));
		assertTrue(m.filled(brittle));
		m.set(brittle, false);
		assertTrue("its \"filled\" is believed", m.fromChart(Map.of(brittle, true)));
		assertTrue(m.filled(brittle));
	}

	@Test
	public void takenOutForAStepUntilPutBack()
	{
		StashMemory m = new StashMemory();
		m.set(UNIT, true);
		assertTrue(m.takenOut(UNIT, STEP, Map.of(1101, 1, 1131, 1)));
		assertFalse(m.filled(UNIT));
		StashMemory.Taken t = m.getTaken();
		assertEquals(UNIT, t.getUnit());
		assertFalse("still the same step: not due", t.isDue(STEP));
		assertTrue("another step", t.isDue("STEP:hard:emote-99,"));
		assertTrue("no clue (done)", t.isDue(null));
		// the chart says another unit is filled: still waiting
		m.fromChart(Map.of(OTHER, true));
		assertEquals(t, m.getTaken());
		// put back (a deposit, or any source saying filled)
		assertTrue(m.deposited(UNIT));
		assertNull(m.getTaken());
	}

	@Test
	public void takenOutWithoutAStepIsNotRemembered()
	{
		StashMemory m = new StashMemory();
		m.takenOut(UNIT, STEP, Map.of(1101, 1));
		assertTrue("a later take-out of the same unit without a clue drops it", m.takenOut(UNIT, null, Map.of(1101, 1)));
		assertNull(m.getTaken());
		assertFalse(m.filled(UNIT));
		m.takenOut(UNIT, STEP, Map.of(1101, 1));
		m.takenOut(OTHER, null, Map.of(1, 1));
		assertEquals("another unit's take-out leaves it", UNIT, m.getTaken().getUnit());
		assertTrue(m.forgetTaken(UNIT));
		assertNull(m.getTaken());
	}

	/** "Skip" on the put-back card forgets that reminder for good (saved), only for its unit. */
	@Test
	public void skipForgetsTheReminderForGood()
	{
		StashMemory m = new StashMemory();
		m.takenOut(UNIT, STEP, Map.of(1101, 1));
		assertFalse("another unit's card: nothing", m.forgetTaken(OTHER));
		assertEquals(UNIT, m.getTaken().getUnit());
		assertTrue(m.forgetTaken(UNIT));
		assertNull(m.getTaken());
		assertFalse("the items are still out: the unit stays empty", m.filled(UNIT));
		assertFalse("nothing left to forget", m.forgetTaken(UNIT));
		// saved: after a restart (load of the saved texts) it doesn't come back
		assertEquals("", m.encodeTaken());
		StashMemory back = new StashMemory();
		back.load(m.encodeFilled(), m.encodeTaken());
		assertNull(back.getTaken());
		// a later take-out for a clue step starts a new reminder
		assertTrue(back.takenOut(UNIT, STEP, Map.of(1101, 1)));
		assertEquals(UNIT, back.getTaken().getUnit());
	}

	/** The reminder only with the config switch on, once its step is over, for a known unit. */
	@Test
	public void dueOnlyWithTheSwitchOnAndTheStepOver()
	{
		ClueData.Step step = new ClueData.Step("emote-2086", "emote", "beginner", "coord", new int[0],
			new int[]{Tiles.pack(3205, 3416, 0)}, Tiles.pack(3209, 3416, 0), "Cheer at Iffie Nitter.", "", "", "", "",
			"emote 7 Cheer", UNIT, "Thessalia's shop", -1, "", -1);
		DetectedClue carried = new DetectedClue(DetectedClue.Kind.STEP, "beginner", 23182, List.of(step), "");
		ClueData.Step next = new ClueData.Step("emote-2087", "emote", "beginner", "coord", new int[0],
			new int[]{Tiles.pack(3200, 3400, 0)}, -1, "Dance somewhere.", "", "", "", "", "emote 1 Dance", -1, "", -1, "", -1);
		DetectedClue nextStep = new DetectedClue(DetectedClue.Kind.STEP, "beginner", 23182, List.of(next), "");
		StashMemory m = new StashMemory();
		m.takenOut(UNIT, carried.identity(), Map.of(1101, 1));
		StashMemory.Taken t = m.getTaken();

		assertNull("still the step they were taken out for", StashMemory.due(t, true, true, carried));
		assertEquals("the next step: the clue route's first stop", t, StashMemory.due(t, true, true, nextStep));
		assertEquals("the clue is done (no next step): a route to the unit only", t,
			StashMemory.due(t, true, true, DetectedClue.NONE));
		assertEquals(t, StashMemory.due(t, true, true, null));
		assertNull("switch off: no stop", StashMemory.due(t, false, true, nextStep));
		assertNull("switch off: no STASH-only route", StashMemory.due(t, false, true, DetectedClue.NONE));
		assertNull("the unit's place not known", StashMemory.due(t, true, false, nextStep));
		assertNull("nothing taken out", StashMemory.due(null, true, true, DetectedClue.NONE));
	}

	@Test
	public void savedTextsRoundTrip()
	{
		StashMemory m = new StashMemory();
		m.set(OTHER, false);
		m.set(UNIT, true);
		m.takenOut(UNIT, STEP, Map.of(1131, 1, 1101, 2));
		assertEquals("sorted", "5229:0,5230:0", m.encodeFilled());
		assertEquals("unit=5229\nstep=" + STEP + "\nitems=1101:2,1131:1", m.encodeTaken());
		StashMemory back = new StashMemory();
		back.load(m.encodeFilled(), m.encodeTaken());
		assertEquals(m.encodeFilled(), back.encodeFilled());
		assertEquals(m.getTaken(), back.getTaken());
		assertEquals("", new StashMemory().encodeTaken());
	}

	@Test
	public void damagedTextsAreSkipped()
	{
		StashMemory m = new StashMemory();
		m.load("5229:1,x:1,5230:7,,5231:0,5232", "unit=abc\nstep=s\nitems=1:1");
		assertEquals("5229:1,5231:0", m.encodeFilled());
		assertNull(m.getTaken());
		m.load(null, "step=s\nitems=1:1");
		assertNull("no unit", m.getTaken());
		m.load(null, "unit=5229\nstep=s\nitems=x");
		assertNull("no items", m.getTaken());
		assertNull(m.filled(UNIT));
	}

	/** Two accounts: each loads its own texts, and a change waits to be written to the account it belongs to. */
	@Test
	public void perAccount()
	{
		Map<String, String> profiles = new HashMap<>();
		profiles.put("acc-a", "5229:1");
		profiles.put("acc-b", "5230:0");
		StashMemory m = new StashMemory();
		DelayedSave save = new DelayedSave();

		m.load(profiles.get("acc-a"), null);
		save.loaded(profiles.get("acc-a"));
		assertTrue(m.filled(UNIT));
		m.set(OTHER, true);
		save.changed("acc-a", m.encodeFilled(), 100);
		assertNotNull(save.pending());

		// account switch: what waits is written to account A first, then B is loaded
		DelayedSave.Write w = save.pending();
		profiles.put(w.getProfile(), w.getText());
		save.written();
		m.load(profiles.get("acc-b"), null);
		save.loaded(profiles.get("acc-b"));
		assertEquals("5229:1,5230:1", profiles.get("acc-a"));
		assertNull("A's unit is not B's", m.filled(UNIT));
		assertFalse(m.filled(OTHER));

		// diff before persisting: the same text again waits for nothing
		save.changed("acc-b", m.encodeFilled(), 200);
		assertNull(save.pending());
	}
}
