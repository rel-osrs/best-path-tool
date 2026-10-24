package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestpath.route.NoMapDataEngine;
import org.junit.Test;

/** The Route tab shows why the map data could not be loaded. */
public class RouteTabTextTest
{
	@Test
	public void aLoadFailureIsToldApartFromNoData()
	{
		assertTrue(RouteTab.isLoadFailure("The map data could not be loaded: map data file movement.bpcm does not match"));
		assertFalse(RouteTab.isLoadFailure(NoMapDataEngine.MESSAGE));
		assertFalse(RouteTab.isLoadFailure(""));
		assertFalse(RouteTab.isLoadFailure(null));
	}

	@Test
	public void theSearchTimeIsShownPartByPart()
	{
		com.bestpath.route.RouteResult r = com.bestpath.route.RouteResult.status(1, null, com.bestpath.route.RouteStatus.ERROR, "")
			.withSearchMillis(1200).withTimes(java.util.List.of("Search with sailing: 900 ms", "Walk tables (walks.bin): in use"), 1000);
		assertEquals(java.util.List.of("Search time by part (all: 1.2 s):", "- Search with sailing: 900 ms",
			"- Walk tables (walks.bin): in use", "- Other (waiting for the map data, choosing the plane, rough bounds): 200 ms"),
			RouteTab.timeLines(r));
		assertTrue("nothing measured: no lines", RouteTab.timeLines(com.bestpath.route.RouteResult.idle(1)).isEmpty());
	}
}
