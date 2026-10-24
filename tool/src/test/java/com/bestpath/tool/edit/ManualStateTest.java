package com.bestpath.tool.edit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.tool.extract.FlagGrid;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManualStateTest
{
	private static Edit keep(int x, int y, boolean on)
	{
		Edit e = new Edit();
		e.type = Edit.Type.KEEP;
		e.x = x;
		e.y = y;
		e.plane = 1;
		e.on = on;
		return e;
	}

	private static FlagGrid grid()
	{
		return new FlagGrid(Set.of(50 << 8 | 50));
	}

	@Test
	void aKeptPocketIsListedAndTheLaterChangeWins()
	{
		ManualState kept = new ManualState(List.of(keep(3210, 3220, true)), grid());
		assertEquals(1, kept.getKeptAnchors().size());
		assertArrayEquals(new int[]{3210, 3220, 1}, kept.getKeptAnchors().get(0));
		assertTrue(kept.getHiddenAnchors().isEmpty(), "keeping a pocket does not hide anything");

		ManualState leftOut = new ManualState(List.of(keep(3210, 3220, true), keep(3210, 3220, false)), grid());
		assertTrue(leftOut.getKeptAnchors().isEmpty(), "leaving it out again takes the keep back");
	}

	private static Edit tile(int x, int y, boolean blocked)
	{
		Edit e = new Edit();
		e.type = Edit.Type.TILE;
		e.x = x;
		e.y = y;
		e.plane = 0;
		e.on = blocked;
		return e;
	}

	@Test
	void aTileTheOwnerMadeWalkableIsOpenedAndTheLaterChangeWins()
	{
		ManualState opened = new ManualState(List.of(tile(3210, 3220, false)), grid());
		assertTrue(opened.isOpenedByOwner(3210, 3220, 0), "made walkable: stays open over the sea layer");
		assertFalse(opened.isOpenedByOwner(3211, 3220, 0));

		ManualState blockedAgain = new ManualState(List.of(tile(3210, 3220, false), tile(3210, 3220, true)), grid());
		assertFalse(blockedAgain.isOpenedByOwner(3210, 3220, 0), "blocked again later: not opened");
	}
}
