package com.bestpath.player;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The stored canoe axe (detected, never asked). The words are the ones the game showed with
 * the dev-only canoe probe ("Barfy Bill|I'll hold onto that axe for you.", "Barfy Bill|Here's your axe.").
 */
public class CanoeAxeTest
{
	@Test
	public void theNpcsWords()
	{
		assertEquals(Boolean.TRUE, CanoeAxe.fromDialogue("Barfy Bill|I'll hold onto that axe for you."));
		assertEquals(Boolean.FALSE, CanoeAxe.fromDialogue("Barfy Bill|Here's your axe."));
		assertEquals(Boolean.TRUE, CanoeAxe.fromDialogue("Lara|I'll hold onto that axe for you."));
		assertNull(CanoeAxe.fromDialogue("Hans|Here's your axe.")); // not a canoe station NPC
		assertNull(CanoeAxe.fromDialogue("Barfy Bill|Hello there."));
		assertNull(CanoeAxe.fromDialogue("I'll hold onto that axe for you."));
		assertNull(CanoeAxe.fromDialogue(null));
	}

	@Test
	public void stationClicksAndStates()
	{
		assertTrue(CanoeAxe.needsAxe("Chop-down", "Canoe Station"));
		assertTrue(CanoeAxe.needsAxe("Shape-Canoe", "Canoe Station"));
		assertFalse(CanoeAxe.needsAxe("Chop-down", "Tree"));
		assertFalse(CanoeAxe.needsAxe("Paddle Canoe", "Canoe Station"));
		// cache #2735 station forms: 0 tree, 9 / 10 chopped, 1..4 shaped, 5.. floated or paddling
		assertTrue(CanoeAxe.axeUsed(9));
		assertTrue(CanoeAxe.axeUsed(10));
		assertTrue(CanoeAxe.axeUsed(1));
		assertTrue(CanoeAxe.axeUsed(4));
		assertFalse(CanoeAxe.axeUsed(0));
		assertFalse(CanoeAxe.axeUsed(5));
		assertFalse(CanoeAxe.axeUsed(12));
	}

	@Test
	public void rememberedPerAccountAndSavedOnlyWhenChanged()
	{
		Saved saved = new Saved();
		CanoeAxe a = new CanoeAxe(saved);
		a.load();
		assertNull(a.getStored()); // never seen: not known (a question mark on cards)
		assertTrue(a.see(true));
		assertFalse(a.see(true));
		assertEquals(List.of("stored"), saved.writes);
		CanoeAxe again = new CanoeAxe(saved);
		again.load();
		assertEquals(Boolean.TRUE, again.getStored());
		assertTrue(again.see(false));
		assertEquals(List.of("stored", "not stored"), saved.writes);
		again.load();
		assertEquals(Boolean.FALSE, again.getStored());
	}

	private static final class Saved implements AccountSettings.Store
	{
		String text;
		final List<String> writes = new ArrayList<>();

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
			writes.add(t);
			return true;
		}
	}
}
