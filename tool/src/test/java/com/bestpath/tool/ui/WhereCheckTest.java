package com.bestpath.tool.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** The "Where" row's check: typed tiles, a floor directly under each end only. */
public class WhereCheckTest
{
	/** A map whose only floor is plane 1 at (3738, 9703) and plane 0 at (3728, 3302). */
	private static final WhereCheck.Floor FLOOR = (x, y, p) ->
		x == 3738 && y == 9703 && p == 1 || x == 3728 && y == 3302 && p == 0 ? 7 : -1;

	@Test
	public void aTileWithAFloorOnItsPlaneIsFine()
	{
		assertNull(WhereCheck.problem(true, new int[]{3728, 3302, 0}, new int[]{3738, 9703, 1}, FLOOR));
	}

	@Test
	public void theSameTileOnAPlaneWithoutAFloorIsRefused()
	{
		assertEquals("no floor under the end (3738, 9703, plane 0)",
			WhereCheck.problem(true, new int[]{3728, 3302, 0}, new int[]{3738, 9703, 0}, FLOOR));
	}

	@Test
	public void onlyTheTileItselfIsLookedAt()
	{
		// the tile beside one with a floor has none of its own
		assertEquals("no floor under the end (3738, 9704, plane 1)",
			WhereCheck.problem(true, new int[]{3728, 3302, 0}, new int[]{3738, 9704, 1}, FLOOR));
		assertEquals("no floor under the start or the end",
			WhereCheck.problem(true, new int[]{3728, 3303, 0}, new int[]{3738, 9704, 1}, FLOOR));
	}

	@Test
	public void aTeleportHasNoStartToCheck()
	{
		assertNull(WhereCheck.problem(false, null, new int[]{3738, 9703, 1}, FLOOR));
	}

	@Test
	public void missingOrOutOfRangeNumbersAreRefused()
	{
		assertEquals("start: type x, y and a plane 0-3", WhereCheck.problem(true, null, new int[]{3738, 9703, 1}, FLOOR));
		assertEquals("end: type x, y and a plane 0-3", WhereCheck.problem(false, null, null, FLOOR));
		assertTrue(WhereCheck.inRange(3738, 9703, 3));
		assertFalse(WhereCheck.inRange(3738, 9703, 4));
		assertFalse(WhereCheck.inRange(-1, 9703, 0));
		assertFalse(WhereCheck.inRange(3738, WhereCheck.MAX_COORD + 1, 0));
	}

	@Test
	public void withoutAMapTheFloorIsNotChecked()
	{
		assertNull(WhereCheck.problem(true, new int[]{1, 1, 0}, new int[]{2, 2, 3}, null));
	}
}
