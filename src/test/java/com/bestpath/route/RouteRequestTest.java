package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class RouteRequestTest
{
	@Test
	public void parsesCommasSpacesAndOptionalPlane()
	{
		assertEquals(new RouteRequest(3222, 3218, 0), RouteRequest.parse("3222, 3218, 0"));
		assertEquals(new RouteRequest(3222, 3218, 0), RouteRequest.parse("  3222 3218 "));
		assertEquals(new RouteRequest(3222, 3218, 2), RouteRequest.parse("3222,3218,2"));
		assertEquals("3222, 3218, 1", RouteRequest.parse("3222 , 3218 ,1").label());
	}

	@Test
	public void severalPlacesWithATitle()
	{
		int a = com.bestpath.core.path.Tiles.pack(3209, 3390, 0);
		int b = com.bestpath.core.path.Tiles.pack(3000, 3000, 1);
		RouteRequest r = RouteRequest.places("clue x", "Cryptic clue (easy)", java.util.List.of("note"), new int[]{a, b});
		assertEquals(3209, r.getX());
		assertEquals(2, r.targetCount());
		assertEquals("Cryptic clue (easy)", r.label());
		assertTrue(r.hasTitle());
		assertEquals(r, RouteRequest.places("clue x", "Other title", java.util.List.of(), new int[]{a, b}));
		assertTrue(!r.equals(RouteRequest.places("clue y", "Cryptic clue (easy)", java.util.List.of(), new int[]{a, b})));
		assertTrue("a typed tile is not a clue request", !new RouteRequest(3209, 3390, 0).equals(
			RouteRequest.places("clue x", "t", java.util.List.of(), new int[]{a})));
		try
		{
			RouteRequest.places("k", "t", java.util.List.of(), new int[0]);
			fail();
		}
		catch (IllegalArgumentException e)
		{
			assertTrue(e.getMessage().contains("No place"));
		}
	}

	@Test
	public void rejectsBadInputWithPlayerMessages()
	{
		expectMessage("", "Type a tile");
		expectMessage("3222", "x, y");
		expectMessage("1 2 3 4", "x, y");
		expectMessage("abc, 3218", "Not a whole number");
		expectMessage("3222, 3218, 4", "Plane");
		expectMessage("-1, 3218", "between");
		expectMessage("20000, 3218", "between");
	}

	@Test
	public void aMapTileHasItsPlanePickedLater()
	{
		// the world map gives no plane; the route search picks it
		RouteRequest map = RouteRequest.fromMap(3738, 9709);
		assertTrue(map.isPlaneFromMap());
		assertEquals("3738, 9709", map.label());
		assertTrue(!map.equals(new RouteRequest(3738, 9709, 0)));
		assertEquals(RouteRequest.fromMap(3738, 9709), map);
		RouteRequest picked = map.onPlane(1);
		assertEquals(new RouteRequest(3738, 9709, 1), picked);
		assertTrue(!picked.isPlaneFromMap());
	}

	private static void expectMessage(String text, String part)
	{
		try
		{
			RouteRequest.parse(text);
			fail("accepted: " + text);
		}
		catch (IllegalArgumentException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains(part));
		}
	}
}
