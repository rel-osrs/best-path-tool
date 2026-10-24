package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.QuestState;
import com.bestpath.data.MapDataLoader;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A world map tile on the REAL exported map data gets the plane with ground there (setting a destination on the map
 * must not go to plane 0 when the ground is at plane 1, as in the Sisterhood Sanctuary under Slepe).
 * Skipped when the export is missing.
 */
public class MapPlaneRealDataTest
{
	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	/** From Lumbridge, Priest in Peril done (its progress varp 302 too: the gate under Paterdomus checks it). */
	private static RouteResult route(RouteRequest to)
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		PlayerState p = new PlayerState.Builder("priest in peril").members(true).quest("Priest in Peril", QuestState.DONE)
			.varp(302, 61).build();
		PlayerSnapshot me = new PlayerSnapshot(3222, 3218, 0, p, List.of());
		return engine.route(new RouteJob(1, to, me, TransportSettings.defaults(), Set.of()));
	}

	@Test
	public void theSanctuaryUnderSlepeIsOnPlaneOne()
	{
		RouteResult r = route(RouteRequest.fromMap(3738, 9709));
		assertEquals(r.getMessage(), RouteStatus.READY, r.getStatus());
		assertEquals(1, Tiles.plane(r.getDestination()));
		assertEquals("3738, 9709, 1", r.getRequest().label());
		boolean stairs = false;
		for (RouteStep s : r.getSteps())
		{
			stairs |= "Climb-down Stairs".equals(s.getTitle());
		}
		assertTrue("down the stairs at 3728, 3302", stairs);
	}

	@Test
	public void aTileWithGroundOnTheGroundFloorStaysThere()
	{
		// Lumbridge Castle: upper floors above, ground on plane 0 next to the tile
		RouteResult r = route(RouteRequest.fromMap(3208, 3220));
		assertEquals(r.getMessage(), RouteStatus.READY, r.getStatus());
		assertEquals(0, Tiles.plane(r.getDestination()));
	}
}
