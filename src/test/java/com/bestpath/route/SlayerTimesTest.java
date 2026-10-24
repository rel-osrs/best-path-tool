package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.req.PlayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Comparing Slayer places: "Travel time" on each place, "Fastest first". */
public class SlayerTimesTest
{
	private static final PlayerSnapshot PLAYER = new PlayerSnapshot(3300, 9500, 0, new PlayerState.Builder("Member").members(true).build(), List.of());

	private static List<String> order(SlayerTask t)
	{
		List<String> out = new ArrayList<>();
		for (SlayerTask.Location l : t.getLocations())
		{
			out.add(l.getPlace().getName() + " " + l.getTicks());
		}
		return out;
	}

	@Test
	public void everyReachablePlaceIsSearchedNearestFirst()
	{
		SlayerTask t = SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3);
		List<String> searched = new ArrayList<>();
		Map<String, Integer> times = SlayerTimes.work(t, PLAYER, TransportSettings.defaults(), job ->
		{
			searched.add(job.getRequest().getTitle());
			return job.getRequest().getTitle().endsWith("Cave") ? RouteResults.ready(0, job.getRequest(), List.of())
				: RouteResults.noRoute(0, job.getRequest(), "no", List.of(), null);
		});
		assertEquals("the cave is nearer as the crow flies; the place off the map data is not searched",
			List.of("Slayer: Kalphites - Kalphite Cave", "Slayer: Kalphites - Kalphite Lair"), searched);
		assertEquals(2, times.size());
		SlayerTask timed = t.withTimes(times);
		assertEquals("fastest first, then no route, then the place off the map data",
			List.of("Kalphite Cave 0", "Kalphite Lair -1", "Far away -2"), order(timed));
		assertEquals("fastest first, then no route, then the place off the map data", "Kalphite Cave",
			timed.getLocations().get(0).getPlace().getName());
		assertTrue(timed.getLocations().get(0).getTicks() >= 0);
	}

	@Test
	public void fastestFirstButKonarsAreaStaysOnTop()
	{
		SlayerTask t = SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3);
		String lair = t.getLocations().get(0).key();
		String cave = t.getLocations().get(1).key();
		assertEquals(List.of("Kalphite Cave 50", "Kalphite Lair 200", "Far away -2"), order(t.withTimes(Map.of(lair, 200, cave, 50))));
		SlayerTask konar = SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 120, 11, 8);
		assertEquals("only Konar's area is shown", List.of("Kalphite Lair 200"), order(konar.withTimes(Map.of(lair, 200, cave, 50))));
	}

	@Test
	public void timesAreKeptForTheTaskNotForEachKill()
	{
		SlayerTimes times = new SlayerTimes();
		SlayerTask t = SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3);
		assertTrue(times.needs(t));
		assertFalse("nothing to time", times.needs(SlayerTask.NO_TASK));
		assertFalse(times.needs(SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 120, 30, 8)));
		assertSame("no times yet", t, times.apply(t));
		assertEquals(SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 86, 0, 3).identity(), t.identity());
		assertFalse(SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 86, 11, 8).identity().equals(t.identity()));
		times.forget();
		assertTrue(times.needs(t));
	}

	@Test
	public void anUnknownPlaceHasNoTimesAndNoRouteStaysMarked()
	{
		SlayerPlaces.Place p = SlayerTaskTest.DATA.places(6266).get(1);
		assertEquals("Kalphite Lair@" + p.getPoints()[0], SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3)
			.getLocations().get(0).key());
		assertEquals(SlayerTask.NOT_TIMED, SlayerTask.of(SlayerTaskTest.DATA, WildernessZones.NONE, 53, 0, 87, 0, 3).getLocations().get(0)
			.getTicks());
	}
}
