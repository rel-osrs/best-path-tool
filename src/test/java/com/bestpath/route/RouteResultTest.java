package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import com.bestpath.core.path.Tiles;
import java.util.List;
import org.junit.Test;

public class RouteResultTest
{
	private static RouteStep walk(int... xs)
	{
		int[] t = new int[xs.length];
		for (int i = 0; i < xs.length; i++)
		{
			t[i] = Tiles.pack(xs[i], 3200, 0);
		}
		return new RouteStep(RouteStep.Kind.WALK, "Walk", "", xs.length - 1, List.of(), null, t, -1);
	}

	@Test
	public void readySumsTicksAndBuildsDrawLists()
	{
		int ladder = Tiles.pack(3203, 3200, 0);
		RouteStep climb = new RouteStep(RouteStep.Kind.TRAVERSAL, "Climb up", "", 2, List.of("Needs nothing"),
			"t-1", new int[]{ladder, Tiles.pack(3203, 3200, 1)}, ladder);
		RouteStep tele = new RouteStep(RouteStep.Kind.TELEPORT, "Teleport", "", 5, List.of(), "t-2",
			new int[]{Tiles.pack(3210, 3424, 0)}, -1);
		RouteResult r = RouteResults.ready(7, new RouteRequest(3210, 3420, 0),
			List.of(walk(3200, 3201, 3202, 3203), climb, tele, walk(3210, 3211)));
		assertEquals(RouteStatus.READY, r.getStatus());
		assertEquals(7, r.getGeneration());
		assertEquals(3 + 2 + 5 + 1, r.getTotalTicks());
		assertNotNull(r.getRequest());
		// transports are not drawn as path lines
		assertEquals(2, r.segmentCount());
		assertEquals(4, r.segmentLength(0));
		assertEquals(Tiles.pack(3211, 3200, 0), r.segmentTile(1, 1));
		assertEquals(1, r.markerCount());
		assertEquals(ladder, r.marker(0));
	}

	@Test
	public void statusResultsHaveNoSteps()
	{
		RouteResult idle = RouteResult.idle(3);
		assertNull(idle.getRequest());
		assertEquals(-1, idle.getTotalTicks());
		RouteResult none = RouteResult.status(4, new RouteRequest(1, 2, 0), RouteStatus.NO_ROUTE, "nope");
		assertEquals("nope", none.getMessage());
		assertEquals(0, none.segmentCount());
		assertEquals(0, none.getSteps().size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void readyNeedsSteps()
	{
		RouteResult.status(1, new RouteRequest(1, 2, 0), RouteStatus.READY, "");
	}

	@Test
	public void stepsCopyTheirTiles()
	{
		int[] t = {Tiles.pack(1, 1, 0)};
		RouteStep s = new RouteStep(RouteStep.Kind.WALK, "Walk", null, 0, List.of(), null, t, -1);
		t[0] = 0;
		assertEquals(Tiles.pack(1, 1, 0), s.tile(0));
		assertEquals("", s.getDetail());
	}

	@Test
	public void durationsInMinutesAndSeconds()
	{
		assertEquals("0 s", RouteResult.duration(0));
		assertEquals("1 s", RouteResult.duration(1));
		assertEquals("6 s", RouteResult.duration(10));
		assertEquals("1 min", RouteResult.duration(100));
		assertEquals("4 min 7 s", RouteResult.duration(412));
	}
}
