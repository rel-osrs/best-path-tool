package com.bestpath.tool.clue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The clue check's per-location rules that need no cache: object ids, distances to a footprint. */
class ClueCheckCliTest
{
	private static ClueStep step(String target, List<Integer> ids, List<String> notes)
	{
		return new ClueStep("cryptic-1371", "cryptic", "hard", List.of(19858), "", target, ids, "Spirit tree", 2544, 3170, 0, "",
			Collections.emptyList(), Collections.emptyList(), notes, "test", Collections.emptyList());
	}

	@Test
	void objectIdsIncludeTheFallbackLocs()
	{
		assertEquals(List.of(1293, 26259), ClueCheckCli.objectIds(step("loc", List.of(1293), List.of("fallback loc 26259 Spirit tree"))));
		assertEquals(List.of(1, 2, 3), ClueCheckCli.objectIds(step("loc", List.of(1), List.of("fallback loc 2,3 Crate", "question: x = 1"))));
		assertEquals(List.of(375), ClueCheckCli.objectIds(step("key", List.of(375), Collections.emptyList())));
	}

	private static ClueCheckCli.Outcome stop(int x, int y, int plane)
	{
		ClueCheckCli.Outcome o = new ClueCheckCli.Outcome();
		o.found = true;
		o.stopX = x;
		o.stopY = y;
		o.stopPlane = plane;
		return o;
	}

	@Test
	void distanceIsToTheWholeFootprintOfABigObject()
	{
		ClueStep s = step("loc", List.of(1293), Collections.emptyList());
		ClueCheckCli.Location l = new ClueCheckCli.Location(s, "target", "loc", "Spirit tree", 2544, 3170, 0, List.of(1293));
		// without a footprint: the plain distance to the named tile (2 from the west side's free tile: too far for an object)
		assertEquals(2, l.distance(stop(2542, 3170, 0)));
		// with the 4x4 tree's footprint: next to it
		l.footprint = new int[]{2543, 3168, 2546, 3171};
		assertEquals(1, l.distance(stop(2542, 3170, 0)));
		assertEquals(1, l.distance(stop(2547, 3168, 0)));
		// another plane, or no route
		assertEquals(99, l.distance(stop(2542, 3170, 1)));
		assertEquals(-1, l.distance(new ClueCheckCli.Outcome()));
		assertEquals(-1, l.distance(null));
	}
}
