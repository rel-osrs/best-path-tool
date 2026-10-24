package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TickGroups;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The player can filter and search transports so they can change the tick timing of them, filter by what they have
 * changed and sort by how recently they made the change, along with the other usual ways to filter and sort.
 */
public class TransportFinderTest
{
	private static TransportLine line(String id, TransportLine.Kind kind, String name, String category, int x, int y, int ticks,
		String network)
	{
		return TransportLine.builder(id, kind).name(name).category(category).start(x, y, 0)
			.end(x, y + 1, kind == TransportLine.Kind.TWO_WAY ? 1 : 0).ticks(ticks).network(network).build();
	}

	private static final List<TransportLine> LINES = List.of(
		line("ladder-a", TransportLine.Kind.TWO_WAY, "Ladder", "CLIMB_BOTH", 3200, 3200, 5, ""),
		line("ladder-b", TransportLine.Kind.TWO_WAY, "Ladder", "CLIMB_BOTH", 3300, 3300, 5, ""),
		line("rocks", TransportLine.Kind.ONE_WAY, "Rocks", "SHORTCUT", 3210, 3200, 8, ""),
		line("boat", TransportLine.Kind.ONE_WAY, "Boat or ship: Port Sarim to Karamja", "TRAVEL", 3000, 3200, 30, ""),
		line("ring-a", TransportLine.Kind.NETWORK, "AJR", "", 2700, 3200, 4, "Fairy ring"));

	private static final TransportFinder FINDER = finder();

	private static TransportFinder finder()
	{
		TickGroups g = TickGroups.of(LINES, Tables.empty());
		return TransportFinder.of(LINES, Tables.empty(), g, WildernessZones.NONE);
	}

	private static List<String> ids(List<TransportFinder.Entry> found)
	{
		List<String> out = new ArrayList<>();
		for (TransportFinder.Entry e : found)
		{
			out.add(e.getId());
		}
		return out;
	}

	private static List<String> find(String text, TransportFinder.Show show, TransportSettings.Category type, TransportFinder.Sort sort,
		TransportSettings s, int me)
	{
		return ids(FINDER.find(new TransportFinder.Query(text, show, type, null, sort, me), s));
	}

	@Test
	public void networkStopsAreLeftOutTheRestListedOnce()
	{
		assertEquals(List.of("boat", "ladder-a", "ladder-b", "rocks"), find("", TransportFinder.Show.ALL, null,
			TransportFinder.Sort.NAME, TransportSettings.defaults(), -1));
	}

	@Test
	public void searchMatchesEveryWordInTheName()
	{
		TransportSettings s = TransportSettings.defaults();
		assertEquals(List.of("ladder-a", "ladder-b"), find("LADDER", TransportFinder.Show.ALL, null, TransportFinder.Sort.NAME, s, -1));
		assertEquals(List.of("boat"), find("sarim karamja", TransportFinder.Show.ALL, null, TransportFinder.Sort.NAME, s, -1));
		assertEquals(List.of("rocks"), find("rock", TransportFinder.Show.ALL, null, TransportFinder.Sort.NAME, s, -1));
		assertEquals("a type's name is the Type filter's", List.of(), find("shortcuts", TransportFinder.Show.ALL, null,
			TransportFinder.Sort.NAME, s, -1));
		assertEquals(List.of(), find("sarim ladder", TransportFinder.Show.ALL, null, TransportFinder.Sort.NAME, s, -1));
	}

	@Test
	public void filterByWhatWasChangedAndSortByHowRecently()
	{
		TransportSettings s = TransportSettings.defaults().withTickChange("rocks", 2, "Rocks", 1000L)
			.withTickChange("ladder-b", -1, "Ladder", 3000L).withTickChange("boat", 5, "Boat", 2000L);
		assertEquals(List.of("ladder-b", "boat", "rocks"), find("", TransportFinder.Show.CHANGED, null, TransportFinder.Sort.RECENT,
			s, -1));
		assertEquals(List.of("ladder-a"), find("", TransportFinder.Show.NOT_CHANGED, null, TransportFinder.Sort.RECENT, s, -1));
		// "recently changed" over everything: the changes first, newest first, then the rest by name
		assertEquals(List.of("ladder-b", "boat", "rocks", "ladder-a"), find("", TransportFinder.Show.ALL, null,
			TransportFinder.Sort.RECENT, s, -1));
		// changed again: first again
		TransportSettings again = s.withTickChange("rocks", 3, "Rocks", 4000L);
		assertEquals("rocks", find("", TransportFinder.Show.CHANGED, null, TransportFinder.Sort.RECENT, again, -1).get(0));
		// changes saved before times were kept: after the timed ones, the last made first
		TransportSettings old = TransportSettings.decode("v1\nticks=rocks 2 Rocks\nticks=boat 1 Boat\n");
		assertEquals(List.of("boat", "rocks"), find("", TransportFinder.Show.CHANGED, null, TransportFinder.Sort.RECENT, old, -1));
	}

	@Test
	public void otherSortsAndTheTypeFilter()
	{
		TransportSettings s = TransportSettings.defaults().withTickChange("boat", -25, "Boat", 1L).withTickChange("rocks", 1, "R", 2L);
		assertEquals(List.of("rocks", "ladder-b", "ladder-a", "boat"), find("", TransportFinder.Show.ALL, null,
			TransportFinder.Sort.NAME_DESC, s, -1));
		// ticks with the player's changes (a ladder changes plane: the penalty counts)
		List<String> fewest = find("", TransportFinder.Show.ALL, null, TransportFinder.Sort.FEWEST_TICKS, s, -1);
		assertEquals("boat", fewest.get(0)); // 30 - 25
		assertEquals(List.of("boat", "rocks"), find("", TransportFinder.Show.ALL, null, TransportFinder.Sort.BIGGEST_CHANGE, s, -1)
			.subList(0, 2));
		assertEquals(List.of("ladder-a", "ladder-b"), find("", TransportFinder.Show.ALL, TransportSettings.Category.STAIRS_LADDERS,
			TransportFinder.Sort.NAME, s, -1));
		assertEquals(List.of("boat"), find("", TransportFinder.Show.ALL, TransportSettings.Category.BOATS, TransportFinder.Sort.NAME,
			s, -1));
	}

	@Test
	public void nearestToMe()
	{
		TransportSettings s = TransportSettings.defaults();
		int me = Tiles.pack(3299, 3299, 0);
		assertEquals(List.of("ladder-b", "ladder-a", "rocks", "boat"), find("", TransportFinder.Show.ALL, null,
			TransportFinder.Sort.NEAREST, s, me));
		assertEquals(1L, TransportFinder.distance(FINDER.getEntries().get(1), me));
		// not known where the player is: by name
		assertEquals(List.of("boat", "ladder-a", "ladder-b", "rocks"), find("", TransportFinder.Show.ALL, null,
			TransportFinder.Sort.NEAREST, s, -1));
	}

	@Test
	public void aChangedRideOrAGoneTransportIsListedByItsSavedName()
	{
		TransportSettings s = TransportSettings.defaults().withTickChange("Fairy ring:AJR>DKS", 3, "Fairy ring: DKS", 1L)
			.withTickChange("gone", 1, "Old boat", 2L);
		List<TransportFinder.Entry> found = FINDER.find(new TransportFinder.Query("", TransportFinder.Show.CHANGED, null, null,
			TransportFinder.Sort.RECENT, -1), s);
		assertEquals(2, found.size());
		assertEquals("Old boat", found.get(0).getTitle());
		assertEquals("Changed", found.get(0).getKind());
		assertEquals("Ride", found.get(1).getKind());
		assertTrue(FINDER.find(new TransportFinder.Query("dks", TransportFinder.Show.ALL, null, null, TransportFinder.Sort.NAME, -1), s)
			.size() == 1);
	}

	@Test
	public void theNeverUsedFilterShowsTheBlacklist()
	{
		TransportSettings s = TransportSettings.defaults().withBlacklisted("rocks", "Rocks").withBlacklisted("ladder-a:back", "Ladder");
		assertEquals(List.of("ladder-a", "rocks"), find("", TransportFinder.Show.NEVER_USED, null, TransportFinder.Sort.NAME, s, -1));
	}
}
