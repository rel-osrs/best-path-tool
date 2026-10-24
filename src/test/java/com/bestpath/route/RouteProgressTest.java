package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteProgress.Event;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Progress tracking and stray detection on hand-made routes. */
public class RouteProgressTest
{
	/** A straight walk on one plane (x first, then y), tile by tile. */
	static RouteStep walk(int x0, int y0, int x1, int y1, int plane)
	{
		List<Integer> t = new ArrayList<>();
		int x = x0;
		int y = y0;
		t.add(Tiles.pack(x, y, plane));
		while (x != x1)
		{
			x += Integer.signum(x1 - x);
			t.add(Tiles.pack(x, y, plane));
		}
		while (y != y1)
		{
			y += Integer.signum(y1 - y);
			t.add(Tiles.pack(x, y, plane));
		}
		int[] a = t.stream().mapToInt(Integer::intValue).toArray();
		return new RouteStep(RouteStep.Kind.WALK, "Walk", "", a.length - 1, List.of(), null, a, -1);
	}

	static RouteStep transport(String id, int sx, int sy, int sp, int ex, int ey, int ep, int ticks)
	{
		return new RouteStep(RouteStep.Kind.TRAVERSAL, id, "", ticks, List.of(), id, new int[0], Tiles.pack(sx, sy, sp),
			Tiles.pack(ex, ey, ep));
	}

	static RouteResult route(RouteRequest to, RouteStep... steps)
	{
		return RouteResults.ready(7, to, List.of(steps));
	}

	/** Walk east 10 tiles, climb a ladder, walk north 10 tiles upstairs. */
	private static RouteResult ladderRoute()
	{
		return route(new RouteRequest(3211, 3210, 1),
			walk(3200, 3200, 3210, 3200, 0),
			transport("ladder", 3211, 3200, 0, 3211, 3200, 1, 2),
			walk(3211, 3200, 3211, 3210, 1));
	}

	@Test
	public void followsWalkTransportWalkAndArrives()
	{
		RouteProgress p = new RouteProgress(ladderRoute());
		p.setLimits(RouteProgress.DEFAULT_STRAY_DISTANCE, 1); // a short route: arriving next to the end only
		assertEquals(0, p.getStep());
		assertEquals(Event.NONE, p.update(3200, 3200, 0, null));
		assertEquals(Event.STEP_CHANGED, p.update(3204, 3200, 0, null)); // walked part grows
		assertEquals(0, p.getStep());
		assertEquals(4, p.getTileIndex());
		assertEquals(Event.NONE, p.update(3204, 3201, 0, null)); // a tile beside the path: same place along it
		assertEquals(4, p.getTileIndex());
		// next to the walk's last tile: the ladder is next
		assertEquals(Event.STEP_CHANGED, p.update(3209, 3200, 0, null));
		assertEquals(1, p.getStep());
		assertEquals(Event.NONE, p.update(3210, 3200, 0, null)); // waiting at the ladder
		assertEquals(1, p.getStep());
		// upstairs on the landing: the walk up there is current
		assertEquals(Event.STEP_CHANGED, p.update(3211, 3200, 1, null));
		assertEquals(2, p.getStep());
		assertEquals(0, p.getTileIndex());
		assertEquals(Event.STEP_CHANGED, p.update(3211, 3205, 1, null));
		assertEquals(5, p.getTileIndex());
		assertEquals(Event.ARRIVED, p.update(3211, 3209, 1, null)); // within 1 tile of the destination
	}

	@Test
	public void sameTileOnAnotherPlaneIsNotOnTheRoute()
	{
		RouteProgress p = new RouteProgress(ladderRoute());
		assertEquals(Event.NONE, p.update(3205, 3200, 2, null));
		assertEquals(0, p.getStep());
		assertEquals(Event.NONE, p.update(3205, 3200, 2, null));
		assertEquals(Event.STRAYED, p.update(3205, 3200, 2, null)); // 3 ticks off the route
	}

	@Test
	public void straysAfterThreeTicksFarAway()
	{
		RouteProgress p = new RouteProgress(ladderRoute());
		p.update(3203, 3200, 0, null);
		assertEquals(Event.NONE, p.update(3203, 3211, 0, null)); // 11 tiles north of the path
		assertEquals(Event.NONE, p.update(3203, 3211, 0, null));
		assertEquals(Event.STRAYED, p.update(3203, 3211, 0, null));
		// reported once; counting starts again
		assertEquals(Event.NONE, p.update(3203, 3211, 0, null));
		assertEquals(Event.NONE, p.update(3203, 3211, 0, null));
		assertEquals(Event.STRAYED, p.update(3203, 3211, 0, null));
	}

	/**
	 * The route is recalculated if the player walks away from where it was last calculated by the set amount: off the
	 * route and the re-route distance from where the player was last on it, even while still near the route ahead.
	 */
	@Test
	public void walkingAwayFromTheRouteByTheSetDistanceStrays()
	{
		RouteProgress p = new RouteProgress(ladderRoute());
		p.setOrigin(Tiles.pack(3200, 3200, 0));
		assertEquals(Event.NONE, p.update(3200, 3200, 0, null));
		p.update(3205, 3200, 0, null); // on the walk: measured from here now
		// 11 tiles east of 3205 on the ground floor, past the ladder (5 from it): near the route ahead, off it
		assertEquals(Event.NONE, p.update(3216, 3200, 0, null));
		assertEquals(Event.NONE, p.update(3216, 3200, 0, null));
		assertEquals(Event.STRAYED, p.update(3216, 3200, 0, null));
		// 10 tiles away is not yet away
		RouteProgress q = new RouteProgress(ladderRoute());
		q.setOrigin(Tiles.pack(3200, 3200, 0));
		q.update(3205, 3200, 0, null);
		for (int i = 0; i < 20; i++)
		{
			assertEquals(Event.NONE, q.update(3215, 3200, 0, null));
		}
	}

	/** Waiting at the end of a walk for a teleport is not walking away; walking off from there is. */
	@Test
	public void aTeleportAfterAWalkStraysOnlyWhenWalkingAwayFromWhereTheWalkEnded()
	{
		RouteStep tele = new RouteStep(RouteStep.Kind.TELEPORT, "Varrock Teleport", "", 5, List.of(), "tele",
			new int[0], -1, Tiles.pack(3212, 3424, 0));
		RouteProgress p = new RouteProgress(route(new RouteRequest(3220, 3424, 0), walk(3200, 3200, 3215, 3200, 0), tele,
			walk(3212, 3424, 3220, 3424, 0)));
		p.setOrigin(Tiles.pack(3200, 3200, 0));
		for (int x = 3200; x <= 3215; x++)
		{
			p.update(x, 3200, 0, null);
		}
		for (int i = 0; i < 20; i++)
		{
			assertEquals(Event.NONE, p.update(3215, 3201, 0, null)); // 15 from where the route was found: waiting to cast
		}
		assertEquals(Event.NONE, p.update(3215, 3212, 0, null));
		assertEquals(Event.NONE, p.update(3215, 3212, 0, null));
		assertEquals(Event.STRAYED, p.update(3215, 3212, 0, null)); // 12 from where the walk ended
	}

	@Test
	public void aFewTilesOffIsNotStraying()
	{
		RouteProgress p = new RouteProgress(ladderRoute());
		for (int i = 0; i < 20; i++)
		{
			// 10 tiles off the path (the default re-route distance): neither on the step nor strayed
			assertEquals(Event.NONE, p.update(3203, 3200 + RouteProgress.DEFAULT_STRAY_DISTANCE, 0, null));
		}
		assertEquals(0, p.getStep());
	}

	@Test
	public void theRerouteDistanceIsThePlayersSetting()
	{
		RouteProgress far = new RouteProgress(ladderRoute());
		far.setLimits(20, RouteProgress.DEFAULT_ARRIVE_STEPS);
		for (int i = 0; i < 20; i++)
		{
			assertEquals(Event.NONE, far.update(3203, 3215, 0, null)); // 15 off, allowed 20
		}
		RouteProgress near = new RouteProgress(ladderRoute());
		near.setLimits(3, RouteProgress.DEFAULT_ARRIVE_STEPS);
		assertEquals(Event.NONE, near.update(3203, 3204, 0, null)); // 4 off, allowed 3
		assertEquals(Event.NONE, near.update(3203, 3204, 0, null));
		assertEquals(Event.STRAYED, near.update(3203, 3204, 0, null));
		// a setting below 1 counts as 1
		RouteProgress least = new RouteProgress(ladderRoute());
		least.setLimits(0, RouteProgress.DEFAULT_ARRIVE_STEPS);
		for (int i = 0; i < 5; i++)
		{
			assertNotEquals(Event.STRAYED, least.update(3203, 3201, 0, null)); // beside the path: on it
		}
	}

	@Test
	public void theArrivalRangeIsThePlayersSetting()
	{
		// routes without known arrive tiles: that many tiles from the destination while the last step is current
		RouteProgress three = new RouteProgress(ladderRoute());
		three.setLimits(RouteProgress.DEFAULT_STRAY_DISTANCE, 3);
		three.update(3211, 3205, 1, null);
		assertEquals(Event.ARRIVED, three.update(3211, 3207, 1, null)); // 3 short of 3211,3210
		RouteProgress two = new RouteProgress(ladderRoute());
		two.setLimits(RouteProgress.DEFAULT_STRAY_DISTANCE, 2);
		two.update(3211, 3205, 1, null);
		assertEquals(Event.STEP_CHANGED, two.update(3211, 3207, 1, null)); // 3 short: not yet
		assertEquals(Event.ARRIVED, two.update(3211, 3208, 1, null));
		// the default (10): on the landing upstairs, 10 tiles from 3211,3210, once the last step is current
		RouteProgress ten = new RouteProgress(ladderRoute());
		assertEquals(Event.STEP_CHANGED, ten.update(3211, 3200, 1, null));
		assertEquals(Event.ARRIVED, ten.update(3211, 3200, 1, null));
		RouteProgress eleven = new RouteProgress(ladderRoute());
		eleven.update(3211, 3200, 1, null);
		assertNotEquals(Event.ARRIVED, eleven.update(3211, 3199, 1, null)); // 11 tiles: not yet
		// more than the most allowed counts as the most allowed (20)
		RouteProgress most = new RouteProgress(ladderRoute());
		most.setLimits(RouteProgress.DEFAULT_STRAY_DISTANCE, 99);
		most.update(3211, 3200, 1, null);
		assertNotEquals(Event.ARRIVED, most.update(3211, 3189, 1, null)); // 21 tiles
		assertEquals(Event.ARRIVED, most.update(3211, 3190, 1, null)); // 20 tiles
		// 0: only the destination or the route's last tile
		RouteProgress zero = new RouteProgress(ladderRoute());
		zero.setLimits(RouteProgress.DEFAULT_STRAY_DISTANCE, 0);
		zero.update(3211, 3205, 1, null);
		assertEquals(Event.STEP_CHANGED, zero.update(3211, 3209, 1, null));
		assertEquals(Event.ARRIVED, zero.update(3211, 3210, 1, null));
	}

	@Test
	public void comingBackStopsTheCount()
	{
		RouteProgress p = new RouteProgress(ladderRoute());
		assertEquals(Event.STEP_CHANGED, p.update(3203, 3200, 0, null));
		assertEquals(Event.NONE, p.update(3203, 3215, 0, null));
		assertEquals(Event.NONE, p.update(3203, 3215, 0, null));
		assertEquals(Event.NONE, p.update(3203, 3201, 0, null)); // back beside the path (no further along)
		assertEquals(Event.NONE, p.update(3203, 3215, 0, null));
		assertEquals(Event.NONE, p.update(3203, 3215, 0, null));
		assertEquals(Event.STRAYED, p.update(3203, 3215, 0, null));
	}

	@Test
	public void longerGraceWhileATransportIsCurrent()
	{
		RouteProgress p = new RouteProgress(ladderRoute());
		p.update(3210, 3200, 0, null);
		assertEquals(1, p.getStep());
		int limit = RouteProgress.STRAY_TICKS + RouteProgress.TRANSPORT_GRACE_TICKS + 2; // + the ladder's 2 ticks
		for (int i = 1; i < limit; i++)
		{
			assertEquals("tick " + i, Event.NONE, p.update(2000, 2000, 0, null)); // e.g. a cutscene somewhere else
		}
		assertEquals(Event.STRAYED, p.update(2000, 2000, 0, null));
	}

	@Test
	public void jumpsAheadButNeverBack()
	{
		RouteProgress p = new RouteProgress(ladderRoute());
		assertEquals(Event.STEP_CHANGED, p.update(3211, 3204, 1, null)); // e.g. took another way up
		assertEquals(2, p.getStep());
		assertEquals(4, p.getTileIndex());
		// back downstairs on the first walk: not "on the route" any more (it is behind)
		assertEquals(Event.NONE, p.update(3202, 3200, 0, null));
		assertEquals(Event.NONE, p.update(3202, 3200, 0, null));
		assertEquals(Event.STRAYED, p.update(3202, 3200, 0, null));
		assertEquals(2, p.getStep());
	}

	@Test
	public void teleportFirstNeverStrays()
	{
		// a teleport (usable anywhere: no start tile) then a walk from its landing
		RouteStep tele = new RouteStep(RouteStep.Kind.TELEPORT, "Varrock Teleport", "", 5, List.of(), "tele",
			new int[0], -1, Tiles.pack(3212, 3424, 0));
		RouteProgress p = new RouteProgress(route(new RouteRequest(3220, 3424, 0), tele, walk(3212, 3424, 3220, 3424, 0)));
		for (int i = 0; i < 50; i++)
		{
			assertEquals(Event.NONE, p.update(3222 + i % 3, 3218, 0, null)); // pottering about before casting
		}
		assertEquals(Event.STEP_CHANGED, p.update(3212, 3424, 0, null)); // landed
		assertEquals(1, p.getStep());
	}

	@Test
	public void transportWithoutKnownLandingMovesOnWhenALaterStepIsReached()
	{
		RouteStep door = new RouteStep(RouteStep.Kind.DOOR, "Open the door", "", 2, List.of(), "door", new int[0],
			Tiles.pack(3211, 3200, 0));
		RouteProgress p = new RouteProgress(route(new RouteRequest(3220, 3200, 0),
			walk(3200, 3200, 3210, 3200, 0), door, walk(3212, 3200, 3220, 3200, 0)));
		p.update(3210, 3200, 0, null);
		assertEquals(1, p.getStep());
		p.update(3211, 3200, 0, null); // in the doorway
		assertEquals(1, p.getStep());
		assertEquals(Event.STEP_CHANGED, p.update(3213, 3200, 0, null)); // through: 2 tiles from the door, 1 into the walk
		assertEquals(2, p.getStep());
	}

	@Test
	public void doorLandingNextToItsStartMovesOnOnlyFromTheFarSide()
	{
		RouteProgress p = new RouteProgress(route(new RouteRequest(3220, 3200, 0),
			walk(3200, 3200, 3210, 3200, 0), transport("door", 3210, 3200, 0, 3211, 3200, 0, 2),
			walk(3211, 3200, 3220, 3200, 0)));
		p.update(3210, 3200, 0, null);
		assertEquals(1, p.getStep()); // standing at the door: it is current
		p.update(3210, 3200, 0, null);
		assertEquals(1, p.getStep());
		p.update(3211, 3200, 0, null); // on the landing (closer to it than to the start)
		assertEquals(2, p.getStep());
	}

	@Test
	public void arrivesOnTheLastTileWhenTheDestinationItselfCannotBeReached()
	{
		RouteProgress p = new RouteProgress(route(new RouteRequest(3213, 3200, 0), walk(3200, 3200, 3210, 3200, 0)));
		p.setLimits(RouteProgress.DEFAULT_STRAY_DISTANCE, 1);
		assertEquals(Event.STEP_CHANGED, p.update(3209, 3200, 0, null));
		assertEquals(Event.ARRIVED, p.update(3210, 3200, 0, null));
	}

	@Test(expected = IllegalArgumentException.class)
	public void needsAFoundRoute()
	{
		new RouteProgress(RouteResult.status(1, new RouteRequest(1, 1, 0), RouteStatus.NO_ROUTE, "no"));
	}

	@Test
	public void progressValueNamesTheRoute()
	{
		RouteResult r = ladderRoute();
		RouteProgress p = new RouteProgress(r);
		p.update(3205, 3200, 0, null);
		Progress v = p.progress();
		assertEquals(7, v.getGeneration());
		assertEquals(0, v.getStep());
		assertEquals(5, v.getTileIndex());
		assertEquals(true, v.isFor(r));
		assertEquals(false, Progress.NONE.isFor(r));
	}

	@Test
	public void resultKnowsWhichStepEachSegmentAndMarkerIs()
	{
		RouteResult r = ladderRoute();
		assertEquals(2, r.segmentCount());
		assertEquals(0, r.segmentStep(0));
		assertEquals(2, r.segmentStep(1));
		assertEquals(1, r.markerCount());
		assertEquals(1, r.markerStep(0));
		assertEquals(Tiles.pack(3211, 3200, 1), r.getSteps().get(1).getEndTile());
		assertEquals(Tiles.pack(3210, 3200, 0), r.getSteps().get(0).getEndTile()); // a walk ends on its last tile
	}
}
