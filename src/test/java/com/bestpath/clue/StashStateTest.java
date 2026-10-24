package com.bestpath.clue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The STASH unit's state from the game's vars (cache 2735's client scripts 6472 and 6624), and which vars matter. */
public class StashStateTest
{
	private static final int ROW = 2086;

	@Test
	public void builtFromTheUnitsVarbit()
	{
		assertEquals(StashState.Built.YES, StashState.read(1, -1, 0, ROW, null).getBuilt());
		assertEquals(StashState.Built.NO, StashState.read(0, -1, 0, ROW, null).getBuilt());
		assertEquals(StashState.Built.UNKNOWN, StashState.read(-1, -1, 0, ROW, null).getBuilt());
	}

	@Test
	public void filledOnlyWhenTheGamesClueHelperTracksThisStep()
	{
		assertEquals(StashState.Filled.YES, StashState.read(1, ROW, 1, ROW, null).getFilled());
		assertTrue(StashState.read(1, ROW, 1, ROW, null).holdsOutfit());
		assertEquals(StashState.Filled.NO, StashState.read(1, ROW, 0, ROW, null).getFilled());
		// another clue in the helper: its "filled" bit says nothing about this unit
		assertEquals(StashState.Filled.UNKNOWN, StashState.read(1, 1234, 1, ROW, null).getFilled());
		assertFalse(StashState.read(1, 1234, 1, ROW, null).holdsOutfit());
		assertEquals(StashState.Filled.UNKNOWN, StashState.read(1, -1, 1, ROW, null).getFilled());
		// a unit that isn't built holds nothing
		assertEquals(StashState.Filled.NO, StashState.read(0, -1, 0, ROW, null).getFilled());
	}

	@Test
	public void rememberedValueFirst()
	{
		// remembered (the newest of clue helper, chart, deposits) wins over the clue helper's live bit
		assertEquals(StashState.Filled.YES, StashState.read(1, -1, 0, ROW, true).getFilled());
		assertEquals(StashState.Filled.NO, StashState.read(1, ROW, 1, ROW, false).getFilled());
		// nothing remembered: as before
		assertEquals(StashState.Filled.YES, StashState.read(1, ROW, 1, ROW, null).getFilled());
		assertEquals(StashState.Filled.UNKNOWN, StashState.read(1, -1, 1, ROW, null).getFilled());
		// not built: holds nothing, whatever was remembered
		assertEquals(StashState.Filled.NO, StashState.read(0, -1, 0, ROW, true).getFilled());
		// built not known (logged out): the remembered value still says it
		assertEquals(StashState.Filled.YES, StashState.read(-1, -1, 0, ROW, true).getFilled());
	}

	@Test
	public void onlyTheStashVarsMatter()
	{
		int stash = 8255; // HH_CONSTRUCTED_BEGINNER002
		assertTrue(StashVars.matters(stash, 2202, stash));
		assertTrue("any STASH tier's varp", StashVars.matters(-1, 1365, stash));
		assertTrue(StashVars.matters(-1, 2202, stash));
		assertTrue("the clue helper's filled bit", StashVars.matters(StashVars.FILLED, 2855, stash));
		assertTrue("the varp holding it", StashVars.matters(-1, 2855, stash));
		assertTrue("the clue helper's clue", StashVars.matters(-1, StashVars.HELPER_CLUE, stash));
		assertFalse("another varbit", StashVars.matters(4000, 1000, stash));
		assertFalse("another varp", StashVars.matters(-1, 1000, stash));
		assertEquals(3546, StashVars.HELPER_CLUE);
		assertEquals(14164, StashVars.FILLED);
	}
}
