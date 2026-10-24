package com.bestpath.route;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The "preferred" system for Slayer places: only among the preferred places, falling back to all; per task; at the top
 * of the list; saved per account.
 */
public class SlayerPreferredTest
{
	private static final int[] LAIR = sorted(new int[]{Tiles.pack(3200, 9490, 0), Tiles.pack(3226, 9500, 2)});
	private static final int[] CAVE = {Tiles.pack(3320, 9500, 0)};
	private static final int[] ALL = sorted(new int[]{LAIR[0], LAIR[1], CAVE[0]});

	/** Vannaka's Kalphites: the lair, the cave, a place off the map data. */
	private static SlayerTask kalphites()
	{
		return SlayerTask.of(SlayerTaskTest.DATA, SlayerTaskTest.WILD, 53, 0, 87, 0, 3);
	}

	/** Any place of the task is marked preferred. */
	private static boolean hasPreferred(SlayerTask t)
	{
		return t.getLocations().stream().anyMatch(SlayerTask.Location::isPreferred);
	}

	private static String key(SlayerTask t, String place)
	{
		for (SlayerTask.Location l : t.getLocations())
		{
			if (l.getPlace().getName().equals(place))
			{
				return l.key();
			}
		}
		throw new AssertionError(place);
	}

	private static List<String> names(SlayerTask t)
	{
		List<String> out = new ArrayList<>();
		for (SlayerTask.Location l : t.getLocations())
		{
			out.add(l.getPlace().getName() + (l.isPreferred() ? " *" : ""));
		}
		return out;
	}

	@Test
	public void thePreferredPlaceComesFirstAndTheNearestGoesOnlyThere()
	{
		SlayerTask t = kalphites();
		SlayerTask p = t.withPreferred(SlayerPreferred.NONE.with("Kalphites", key(t, "Kalphite Cave"), true));
		assertEquals(List.of("Kalphite Cave *", "Kalphite Lair", "Far away"), names(p));
		assertTrue(hasPreferred(p));
		RouteRequest nearest = p.nearest();
		assertArrayEquals("only the preferred place", CAVE, nearest.getTargets());
		assertEquals("Slayer: Kalphites (nearest preferred place)", nearest.getTitle());
		assertEquals(List.of(SlayerTask.PREFERRED_NOTE), nearest.getNotes());
		RouteRequest fallback = p.nearestFallback(l -> true);
		assertArrayEquals("when no route reaches it: every place", ALL, sorted(fallback.getTargets()));
		assertEquals(List.of(SlayerTask.FALLBACK_NOTE), fallback.getNotes());
		assertArrayEquals(ALL, sorted(p.nearest(l -> true, false, null).getTargets()));
	}

	@Test
	public void withoutPreferredPlacesTheNearestUsesThemAll()
	{
		SlayerTask t = kalphites();
		assertSame("nothing preferred: the same task", t, t.withPreferred(SlayerPreferred.NONE));
		assertFalse(hasPreferred(t));
		assertArrayEquals(ALL, sorted(t.nearest().getTargets()));
		assertEquals("Slayer: Kalphites (nearest place)", t.nearest().getTitle());
		assertNull("no fallback needed", t.nearestFallback(l -> true));
	}

	@Test
	public void unmarkingRestoresTheNearestOfAll()
	{
		SlayerTask t = kalphites();
		String cave = key(t, "Kalphite Cave");
		SlayerPreferred on = SlayerPreferred.NONE.with("Kalphites", cave, true);
		SlayerPreferred off = on.with("Kalphites", cave, false);
		assertEquals(SlayerPreferred.NONE, off);
		SlayerTask back = t.withPreferred(on).withPreferred(off);
		assertEquals(List.of("Kalphite Lair", "Kalphite Cave", "Far away"), names(back));
		assertArrayEquals(ALL, sorted(back.nearest().getTargets()));
		assertEquals(t, back);
		// the page's star, at once
		assertEquals(t, t.withPreferred(cave, true).withPreferred(cave, false));
		assertEquals(t.withPreferred(on), t.withPreferred(cave, true));
	}

	@Test
	public void aMarkIsForOneTaskOnly()
	{
		SlayerTask t = kalphites();
		SlayerPreferred ankou = SlayerPreferred.NONE.with("Ankou", key(t, "Kalphite Cave"), true);
		assertFalse(ankou.has("Kalphites", key(t, "Kalphite Cave")));
		assertSame("another task's mark changes nothing", t, t.withPreferred(ankou));
		assertArrayEquals(ALL, sorted(t.withPreferred(ankou).nearest().getTargets()));
	}

	@Test
	public void aPreferredPlaceWithoutARouteFallsBackToAllPlaces()
	{
		SlayerTask t = kalphites();
		String cave = key(t, "Kalphite Cave");
		SlayerTask p = t.withPreferred(SlayerPreferred.NONE.with("Kalphites", cave, true));
		// timed: no route reaches the cave from here
		SlayerTask timed = p.withTimes(Map.of(cave, SlayerTask.NO_ROUTE, key(t, "Kalphite Lair"), 300));
		assertTrue(timed.getLocations().get(0).isPreferred());
		assertArrayEquals(ALL, sorted(timed.nearest().getTargets()));
		assertEquals("Slayer: Kalphites (nearest place)", timed.nearest().getTitle());
		assertNull(timed.nearestFallback(l -> true));
		// a preferred place off the map data: no route there either
		SlayerTask far = t.withPreferred(SlayerPreferred.NONE.with("Kalphites", key(t, "Far away"), true));
		assertEquals("Far away *", names(far).get(0));
		assertArrayEquals(ALL, sorted(far.nearest().getTargets()));
		assertNull(far.nearestFallback(l -> true));
	}

	@Test
	public void theFiltersComeFirst()
	{
		SlayerTask t = kalphites();
		SlayerTask p = t.withPreferred(SlayerPreferred.NONE.with("Kalphites", key(t, "Kalphite Cave"), true));
		java.util.function.Predicate<SlayerTask.Location> noCave = l -> !l.getPlace().getName().equals("Kalphite Cave");
		assertArrayEquals("the preferred place is filtered out: among those shown", LAIR, sorted(p.nearest(noCave).getTargets()));
		assertNull(p.nearestFallback(noCave));
	}

	@Test
	public void preferredPlacesAreTimedFirst()
	{
		SlayerTask t = kalphites();
		SlayerTask p = t.withPreferred(SlayerPreferred.NONE.with("Kalphites", key(t, "Kalphite Lair"), true));
		List<String> searched = new ArrayList<>();
		PlayerSnapshot nearCave = new PlayerSnapshot(3300, 9500, 0, new PlayerState.Builder("Member").members(true).build(), List.of());
		SlayerTimes.work(p, nearCave, TransportSettings.defaults(), job ->
		{
			searched.add(job.getRequest().getTitle());
			return RouteResults.ready(0, job.getRequest(), List.of());
		});
		assertEquals("the lair first though the cave is nearer", List.of("Slayer: Kalphites - Kalphite Lair",
			"Slayer: Kalphites - Kalphite Cave"), searched);
	}

	/** Saved per account as text: logging out and in (or another account and back) reads the same marks. */
	@Test
	public void keptAcrossLogins()
	{
		SlayerTask t = kalphites();
		SlayerPreferred p = SlayerPreferred.NONE.with("Kalphites", key(t, "Kalphite Cave"), true)
			.with("Ankou", "Stronghold Slayer Cave@" + Tiles.pack(2470, 9800, 0), true);
		String saved = p.toText();
		SlayerPreferred read = SlayerPreferred.fromText(saved);
		assertEquals(p, read);
		assertEquals(saved, read.toText());
		// after the login the task is read again from the game's vars: the same place is preferred
		SlayerTask again = SlayerTask.of(SlayerTaskTest.DATA, SlayerTaskTest.WILD, 53, 0, 80, 0, 3).withPreferred(read);
		assertArrayEquals(CAVE, sorted(again.nearest().getTargets()));
		assertEquals(List.of("Ankou: Stronghold Slayer Cave", "Kalphites: Kalphite Cave"), labels(read));
		assertEquals(SlayerPreferred.NONE, SlayerPreferred.fromText(null));
		assertEquals(SlayerPreferred.NONE, SlayerPreferred.fromText(""));
		assertEquals("damaged lines are left out", p, SlayerPreferred.fromText("junk\n" + saved + "a\tb\tc\n\t\n"));
	}

	@Test
	public void namesAreKeptOnOneLine()
	{
		SlayerPreferred p = SlayerPreferred.NONE.with("Kal\tphites", "Lair\n@5", true);
		assertTrue(p.has("Kal phites", "Lair @5"));
		assertEquals(p, SlayerPreferred.fromText(p.toText()));
		assertSame("empty names are not kept", SlayerPreferred.NONE, SlayerPreferred.NONE.with(" ", "x", true));
		assertSame(SlayerPreferred.NONE, SlayerPreferred.NONE.with("Kalphites", "x", false));
	}

	/** The points in any order (the search picks the cheapest). */
	private static int[] sorted(int[] points)
	{
		int[] out = points.clone();
		java.util.Arrays.sort(out);
		return out;
	}

	private static List<String> labels(SlayerPreferred p)
	{
		List<String> out = new ArrayList<>();
		for (SlayerPreferred.Mark m : p.list())
		{
			out.add(m.getTask() + ": " + m.getPlaceName());
		}
		return out;
	}
}
