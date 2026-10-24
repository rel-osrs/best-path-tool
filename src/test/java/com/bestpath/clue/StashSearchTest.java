package com.bestpath.clue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.path.Tiles;
import java.util.Map;
import org.junit.Test;

/** "Search" on a STASH unit, then what the player carries and wears: put in, taken out, or something else. */
public class StashSearchTest
{
	private static final int UNIT = 5229;
	private static final int TILE = Tiles.pack(3100, 3200, 0);
	private static final int NEXT_TO = Tiles.pack(3101, 3200, 0);
	private static final int FAR = Tiles.pack(3120, 3200, 0);
	private static final Map<Integer, Integer> OUTFIT = Map.of(1101, 1, 1131, 1, 995, 500);

	@Test
	public void itemsGoneIsADeposit()
	{
		StashSearch s = new StashSearch();
		s.searched(UNIT, TILE, OUTFIT, 10);
		assertNull("nothing changed yet", s.onItems(OUTFIT, NEXT_TO, 11));
		StashSearch.Result r = s.onItems(Map.of(995, 500), NEXT_TO, 12);
		assertTrue(r.isDeposited());
		assertEquals(UNIT, r.getUnit());
		assertEquals(Map.of(1101, 1, 1131, 1), r.getItems());
		assertFalse("one search, one result", s.isWaiting());
	}

	@Test
	public void itemsComeIsATakeOut()
	{
		StashSearch s = new StashSearch();
		s.searched(UNIT, TILE, Map.of(995, 500), 10);
		StashSearch.Result r = s.onItems(OUTFIT, NEXT_TO, 11);
		assertFalse(r.isDeposited());
		assertEquals(Map.of(1101, 1, 1131, 1), r.getItems());
	}

	@Test
	public void wornItemsCountToo()
	{
		// carried and worn together: taking the hat off and stashing it is one change of -1 hat
		StashSearch s = new StashSearch();
		s.searched(UNIT, TILE, Map.of(1101, 1, 4000, 1), 10);
		StashSearch.Result r = s.onItems(Map.of(4000, 1), NEXT_TO, 11);
		assertTrue(r.isDeposited());
		assertEquals(Map.of(1101, 1), r.getItems());
	}

	@Test
	public void changesOnTheWayAreSomethingElse()
	{
		StashSearch s = new StashSearch();
		s.searched(UNIT, TILE, OUTFIT, 10);
		// a rune used up while still walking there
		assertNull(s.onItems(Map.of(1101, 1, 1131, 1, 995, 400), FAR, 12));
		assertTrue(s.isWaiting());
		StashSearch.Result r = s.onItems(Map.of(995, 400), NEXT_TO, 20);
		assertEquals("only what changed next to the unit", Map.of(1101, 1, 1131, 1), r.getItems());
		// another plane is not next to it
		s.searched(UNIT, TILE, OUTFIT, 30);
		assertNull(s.onItems(Map.of(), Tiles.pack(3100, 3200, 1), 31));
		assertNull(s.onItems(Map.of(), -1, 32));
	}

	@Test
	public void mixedChangeTimeoutAndCancel()
	{
		StashSearch s = new StashSearch();
		s.searched(UNIT, TILE, OUTFIT, 10);
		assertNull("items both gone and come: not a STASH search", s.onItems(Map.of(1101, 1, 1131, 1, 4000, 1), NEXT_TO, 11));
		assertFalse(s.isWaiting());

		s.searched(UNIT, TILE, OUTFIT, 10);
		assertNull(s.onItems(Map.of(995, 500), NEXT_TO, 10 + StashSearch.TIMEOUT_TICKS + 1));
		assertFalse("too late: forgotten", s.isWaiting());

		s.searched(UNIT, TILE, OUTFIT, 10);
		s.cancel();
		assertNull(s.onItems(Map.of(995, 500), NEXT_TO, 11));
	}

	/**
	 * A put-back stop was missed at Seers' Village (but not at Castle Wars); replayed from a RuneLite log: "Search" clicked from inside the courthouse, again on the way, then "You
	 * withdraw your items" one tick later while the player ran up to the unit. The tile the search is told must be the
	 * server's (beside the unit): the drawn player was still a few tiles behind, and a change seen "on the way" is taken
	 * as something else, so the take-out was lost and no put-back stop came.
	 */
	@Test
	public void takeOutRightAfterRunningUpIsSeenOnTheServerTile()
	{
		Map<Integer, Integer> before = Map.of(995, 500);
		int seersUnit = Tiles.pack(2731, 3475, 0);
		int drawn = Tiles.pack(2734, 3471, 0); // still running there on screen
		int server = Tiles.pack(2730, 3475, 0); // already beside the unit for the game
		StashSearch s = new StashSearch();
		s.searched(5241, seersUnit, before, 100);
		assertNull(s.onItems(before, Tiles.pack(2735, 3469, 0), 105));
		s.searched(5241, seersUnit, before, 105); // the second click
		Map<Integer, Integer> after = Map.of(995, 500, 1643, 1, 3200, 1, 4093, 1);
		StashSearch.Result r = s.onItems(after, server, 106);
		assertFalse(r.isDeposited());
		assertEquals(Map.of(1643, 1, 3200, 1, 4093, 1), r.getItems());
		// what happened before the fix: the drawn tile, more than 2 tiles away, hides the take-out for good
		StashSearch old = new StashSearch();
		old.searched(5241, seersUnit, before, 105);
		assertNull(old.onItems(after, drawn, 106));
		assertNull(old.onItems(after, server, 107));
	}
}
