package com.bestpath.player;

import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Detected unlocks: what is remembered per account, the chat message, the spirit tree patches. */
public class UnlockMemoryTest
{
	/** One saved value in memory. */
	private static final class Saved implements AccountSettings.Store
	{
		String text;
		int writes;

		@Override
		public String profileKey()
		{
			return "p";
		}

		@Override
		public String read()
		{
			return text;
		}

		@Override
		public boolean write(String t)
		{
			text = t;
			writes++;
			return true;
		}
	}

	@Test
	public void collectionLogItemsAreAddedSavedOnceAndLoadedAgain()
	{
		Saved log = new Saved();
		Saved trees = new Saved();
		UnlockMemory m = new UnlockMemory(log, trees);
		m.load();
		assertTrue(m.getLog().isEmpty());
		assertTrue(m.addToLog(Set.of(6570)));
		assertFalse("already known: nothing saved", m.addToLog(Set.of(6570)));
		assertEquals(1, log.writes);
		UnlockMemory again = new UnlockMemory(log, trees);
		again.load();
		assertEquals(Set.of(6570), again.getLog());
	}

	@Test
	public void spiritTreesRememberGrownAndNotGrown()
	{
		Saved log = new Saved();
		Saved trees = new Saved();
		UnlockMemory m = new UnlockMemory(log, trees);
		m.load();
		assertTrue(m.seeTree("Port Sarim", true));
		assertFalse(m.seeTree("Port Sarim", true));
		assertTrue(m.seeTree("Brimhaven", false));
		assertEquals(2, trees.writes);
		UnlockMemory again = new UnlockMemory(log, trees);
		again.load();
		assertEquals(Map.of("Port Sarim", true, "Brimhaven", false), again.getTrees());
		assertTrue("a tree can die or be cleared", again.seeTree("Port Sarim", false));
	}

	@Test
	public void theGamesCollectionLogMessage()
	{
		assertEquals("Fire cape", UnlockMemory.newLogItem("New item added to your collection log: Fire cape"));
		assertNull(UnlockMemory.newLogItem("You have a funny feeling like you're being followed."));
		assertNull(UnlockMemory.newLogItem(null));
	}

	@Test
	public void spiritTreePatchesFromTheCache()
	{
		assertEquals(5, SpiritTreePatches.PATCHES.size());
		assertTrue("object 8355, Travel", SpiritTreePatches.grown(20));
		assertFalse("object 8356, Check-health: not usable yet", SpiritTreePatches.grown(44));
		assertFalse(SpiritTreePatches.grown(0));
		assertEquals("Port Sarim", SpiritTreePatches.inRegion(12082).getPlace());
		assertEquals("Hosidius", SpiritTreePatches.inRegion(6711).getPlace());
		assertEquals("6967.7904", SpiritTreePatches.inRegion(6711).getTimeTrackingKey());
		assertNull(SpiritTreePatches.inRegion(12850));
		assertTrue(SpiritTreePatches.isPatchVarbit(4771));
		assertFalse(SpiritTreePatches.isPatchVarbit(4773));
		assertEquals(Boolean.TRUE, SpiritTreePatches.fromTimeTracking("20:1791999999"));
		assertEquals(Boolean.FALSE, SpiritTreePatches.fromTimeTracking("44:1791999999"));
		assertNull(SpiritTreePatches.fromTimeTracking("garbage"));
		assertNull(SpiritTreePatches.fromTimeTracking(null));
	}
}
