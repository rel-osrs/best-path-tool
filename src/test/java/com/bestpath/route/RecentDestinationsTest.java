package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.path.Tiles;
import java.util.List;
import org.junit.Test;

/** The list of recent destinations. */
public class RecentDestinationsTest
{
	private static final RouteRequest LUMBRIDGE = new RouteRequest(3222, 3218, 0);
	private static final RouteRequest VARROCK = new RouteRequest(3213, 3428, 0);

	@Test
	public void theNewestIsOnTopAndAPlaceIsListedOnce()
	{
		RecentDestinations d = RecentDestinations.NONE.with(LUMBRIDGE).with(VARROCK).with(LUMBRIDGE);
		assertEquals(List.of(LUMBRIDGE, VARROCK), d.list());
	}

	@Test
	public void onlyTheLastTenAreKept()
	{
		RecentDestinations d = RecentDestinations.NONE;
		for (int i = 0; i < 15; i++)
		{
			d = d.with(new RouteRequest(3200 + i, 3200, 0));
		}
		assertEquals(RecentDestinations.MAX, d.list().size());
		assertEquals(3214, d.list().get(0).getX());
		assertEquals(3205, d.list().get(RecentDestinations.MAX - 1).getX());
	}

	@Test
	public void clueRoutesAreNotKept()
	{
		RouteRequest clue = RouteRequest.places("clue 2677", "Cryptic clue (hard)", List.of(), new int[]{Tiles.pack(3222, 3218, 0)});
		assertFalse(RecentDestinations.keeps(clue));
		assertEquals(RecentDestinations.NONE, RecentDestinations.NONE.with(clue));
		assertTrue(RecentDestinations.keeps(LUMBRIDGE));
	}

	@Test
	public void onlyThePlaceIsKeptNotTheRoutesFirstStops()
	{
		RouteRequest withStop = VARROCK.withFirstStop(new RouteRequest.Stop("stash-1", Tiles.pack(3100, 3100, 0), "STASH", List.of()));
		RouteRequest kept = RecentDestinations.NONE.with(withStop).list().get(0);
		assertTrue(kept.getFirstStops().isEmpty());
		assertEquals(VARROCK, kept);
	}

	@Test
	public void savedAndReadBackAsText()
	{
		RouteRequest quest = RouteRequest.places(QuestHelperPath.KEY, QuestHelperPath.TITLE, List.of("note"),
			new int[]{Tiles.pack(3254, 3420, 1), Tiles.pack(3260, 3420, 1)});
		RecentDestinations d = RecentDestinations.NONE.with(LUMBRIDGE).with(RouteRequest.fromMap(2900, 3500)).with(quest);
		RecentDestinations back = RecentDestinations.fromText(d.toText());
		assertEquals(d, back);
		assertTrue(back.list().get(1).isPlaneFromMap());
		assertEquals(QuestHelperPath.TITLE, back.list().get(0).getTitle());
		assertEquals(2, back.list().get(0).targetCount());
	}

	@Test
	public void damagedLinesAreLeftOut()
	{
		RecentDestinations d = RecentDestinations.fromText("tile 3222 3218 0\ntile x\nnonsense\nmap 99999 1\n");
		assertEquals(List.of(LUMBRIDGE), d.list());
		assertEquals(RecentDestinations.NONE, RecentDestinations.fromText(null));
	}

	@Test
	public void removedOneByOne()
	{
		RecentDestinations d = RecentDestinations.NONE.with(LUMBRIDGE).with(VARROCK);
		assertEquals(List.of(LUMBRIDGE), d.without(0).list());
		assertEquals(d, d.without(5));
	}
}
