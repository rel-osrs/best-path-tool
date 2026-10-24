package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.SlayerPlaces;
import com.bestpath.core.data.SlayerPlacesDiagnostics;
import com.bestpath.core.req.PlayerState;
import com.bestpath.data.MapDataLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * On the REAL exported map data: every master is named, Konar's Kalphite areas have places, and the
 * Slayer "Go" buttons find routes. Skipped when the export is missing.
 */
public class SlayerRealDataTest
{
	private static PluginData data(RouteEngine e)
	{
		assumeTrue("no exported map data", e instanceof CoreRouteEngine);
		return ((CoreRouteEngine) e).getRouter().getData();
	}

	@Test
	public void theTasksAndTheirPlacesAreThere()
	{
		PluginData d = data(MapDataLoader.engine());
		SlayerPlaces s = d.getSlayer();
		assumeTrue("an export without the Slayer file", !s.isEmpty());
		List<String> masters = new ArrayList<>();
		for (int m = 1; m <= 10; m++)
		{
			masters.add(s.masterName(m));
		}
		assertEquals(List.of("Turael", "Mazchna", "Vannaka", "Chaeldar", "Duradel", "Nieve", "Krystilia", "Konar", "Spria",
			"Mortimer"), masters);
		assertEquals("Kalphites", s.task(53, 0).getName());
		assertNotNull("a boss task names the boss (varbit 4723 = 7)", s.task(SlayerPlaces.BOSS_TASK, 7));
		assertEquals("Kalphite Lair", s.areaName(11));
		// Konar's two Kalphite areas each have a place a route can reach
		for (int area : new int[]{11, 12})
		{
			SlayerTask t = SlayerTask.of(s, d.getWilderness(), 53, 0, 150, area, 8);
			assertTrue(t.area, t.getLocations().get(0).isAssigned() && t.getLocations().get(0).canGo());
		}
		// at least 150 of the 163 tasks have places
		int with = 0;
		for (SlayerPlaces.Task t : SlayerPlacesDiagnostics.tasks(s))
		{
			with += s.places(t.getRow()).isEmpty() ? 0 : 1;
		}
		assertTrue(with + " tasks with places", with >= 150);
	}

	@Test
	public void goToTheNearestPlaceFindsARoute()
	{
		RouteEngine e = MapDataLoader.engine();
		PluginData d = data(e);
		assumeTrue("an export without the Slayer file", !d.getSlayer().isEmpty());
		PlayerState p = new PlayerState.Builder("Member").members(true).build();
		// hill giants from Lumbridge, cave crawlers from Taverley
		for (int[] job : new int[][]{{14, 3222, 3218}, {37, 2894, 3450}})
		{
			SlayerTask t = SlayerTask.of(d.getSlayer(), d.getWilderness(), job[0], 0, 50, 0, 3);
			RouteResult r = e.route(new RouteJob(1, t.nearest(), new PlayerSnapshot(job[1], job[2], 0, p, List.of()), TransportSettings.defaults(),
				Set.of()));
			assertEquals(t.getName() + ": " + r.getMessage(), RouteStatus.READY, r.getStatus());
		}
	}

	@Test
	public void travelTimesPutTheFastestFirst()
	{
		RouteEngine e = MapDataLoader.engine();
		PluginData d = data(e);
		assumeTrue("an export without the Slayer file", !d.getSlayer().isEmpty());
		PlayerState p = new PlayerState.Builder("Member").members(true).build();
		// abyssal demons (id 42) from Varrock, as Duradel gives them
		SlayerTask t = SlayerTask.of(d.getSlayer(), d.getWilderness(), 42, 0, 150, 0, 5);
		long start = System.nanoTime();
		java.util.Map<String, Integer> times = SlayerTimes.work(t, new PlayerSnapshot(3213, 3424, 0, p, List.of()), TransportSettings.defaults(),
			e::route);
		System.out.printf("Slayer travel times: %d places in %d ms: %s%n", times.size(), (System.nanoTime() - start) / 1_000_000, times);
		SlayerTask timed = t.withTimes(times);
		int first = timed.getLocations().get(0).getTicks();
		assertTrue("the first place has a route", first > 0);
		for (SlayerTask.Location l : timed.getLocations())
		{
			assertTrue(l.getPlace().getName(), l.getTicks() < 0 || l.getTicks() >= first);
		}
	}
}
