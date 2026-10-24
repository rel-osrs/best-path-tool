package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.data.ClueData;
import com.bestpath.core.data.ClueDataDiagnostics;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.data.MapDataLoader;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * The anagram clue "HEROIC" wanted the ground floor when the person is on the upper floor. On the real exported map,
 * the routes to Eohric and the other NPCs the cache put on the ground floor end on their upper floor.
 */
public class ClueFloorTest
{
	private static void endsOn(String key, int x, int y, int plane, int fromX, int fromY)
	{
		RouteEngine e = MapDataLoader.engine();
		assumeTrue("no exported map data", e instanceof CoreRouteEngine);
		ClueData.Step s = ClueDataDiagnostics.byKey(((CoreRouteEngine) e).getRouter().getData().getClues(), key);
		assertEquals(key, Tiles.pack(x, y, plane), s.target(0));
		RouteRequest r = RouteRequest.places("clue test", key, List.of(), ClueDataDiagnostics.targets(s));
		PlayerSnapshot me = new PlayerSnapshot(fromX, fromY, 0, PlayerStateBuilder.maxed(), List.of());
		RouteResult res = e.route(new RouteJob(1, r, me, TransportSettings.defaults(), Set.of()));
		assertEquals(key + ": " + res.getMessage(), RouteStatus.READY, res.getStatus());
		assertEquals(key, plane, Tiles.plane(res.getDestination()));
		RouteStep last = res.getSteps().get(res.getSteps().size() - 1);
		assertEquals(key + ": the route ends on that floor", plane, Tiles.plane(last.getEndTile()));
	}

	@Test
	public void eohricIsUpstairs()
	{
		endsOn("anagram-1527", 2900, 3565, 1, 2899, 3550);
	}

	@Test
	public void otherUpperFloorNpcs()
	{
		endsOn("cipher-2267", 3112, 3162, 1, 3109, 3170); // Wizard Traiborn
		endsOn("cryptic-1358", 2743, 3578, 1, 2740, 3555); // Donovan
		endsOn("cryptic-1431", 2845, 3539, 2, 2878, 3546); // Kamfreena
		endsOn("cryptic-1249", 2983, 3337, 1, 2965, 3345); // Ambassador Spanfipple
		endsOn("cryptic-1250", 3046, 3383, 1, 3045, 3372); // Lucy
		endsOn("anagram-1558", 3821, 9778, 2, 3720, 3300); // Daer Krand, from Slepe
	}

	/** A clue location on the wrong plane (2490, 3488, 0) goes to plane 1. */
	@Test
	public void wrongPlaneNpcsFromTheWiki()
	{
		endsOn("cryptic-1373", 2490, 3488, 1, 2465, 3489); // Heckel Funch, Grand Tree
		endsOn("anagram-1555", 2529, 3162, 1, 2524, 3170); // Bolkoy, Tree Gnome Village
		endsOn("cryptic-1383", 2983, 3338, 2, 2965, 3345); // Sir Vyvin, White Knights' Castle
	}
}
