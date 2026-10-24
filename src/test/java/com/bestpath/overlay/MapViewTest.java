package com.bestpath.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MapViewTest
{
	/** A 400 x 300 map at (100, 50) on the canvas, Lumbridge-ish in the middle, 4 pixels per tile. */
	private static final MapView VIEW = new MapView(100, 50, 400, 300, 3200, 3200, 4);

	@Test
	public void middleTileIsNearTheMiddleOfTheMap()
	{
		// 100 tiles wide: the middle tile's column starts 50 tiles (200 px) from the left edge, its point is 2 px in
		assertEquals(302, VIEW.screenX(3200), 1e-9);
		// 75 tiles high: 37 rows below the middle one, so its point is 37 * 4 + 2 px up from the bottom edge (350)
		assertEquals(200, VIEW.screenY(3200), 1e-9);
		assertEquals(3200, VIEW.tileX(302));
		assertEquals(3200, VIEW.tileY(200));
	}

	@Test
	public void northIsUpAndEastIsRight()
	{
		assertEquals(VIEW.screenX(3200) + 4, VIEW.screenX(3201), 1e-9);
		assertEquals(VIEW.screenY(3200) - 4, VIEW.screenY(3201), 1e-9);
		// one pixel inside a tile's square still reads as that tile
		assertEquals(3201, VIEW.tileX(304)); // 3201's column is 304..307
		assertEquals(3200, VIEW.tileX(303));
		assertEquals(3201, VIEW.tileY(197)); // 3201's row is 194..197
		assertEquals(3200, VIEW.tileY(198));
	}

	@Test
	public void visibleTilesAreTheMapsEdges()
	{
		assertEquals(3150, VIEW.minTileX());
		assertEquals(3249, VIEW.maxTileX());
		assertEquals(3163, VIEW.minTileY());
		assertEquals(3237, VIEW.maxTileY());
		assertTrue(VIEW.overlaps(3240, 3230, 3300, 3300, 0));
		assertFalse(VIEW.overlaps(3260, 3100, 3300, 3150, 0));
		assertTrue(VIEW.overlaps(3260, 3100, 3300, 3150, 15)); // with a margin
		assertTrue(VIEW.contains(100, 50));
		assertFalse(VIEW.contains(500, 50)); // the right edge is outside
	}

	@Test
	public void tileToScreenAndBackForEveryZoom()
	{
		double[] zooms = {1, 1.5, 2, 3, 4, 6, 8};
		for (double z : zooms)
		{
			MapView v = new MapView(13, 27, 765, 503, 2900, 3400, z);
			for (int x = v.minTileX() + 1; x < v.maxTileX(); x += 7)
			{
				for (int y = v.minTileY() + 1; y < v.maxTileY(); y += 11)
				{
					int px = (int) Math.floor(v.screenX(x));
					int py = (int) Math.floor(v.screenY(y));
					assertEquals("x at zoom " + z, x, v.tileX(px));
					assertEquals("y at zoom " + z, y, v.tileY(py));
				}
			}
		}
	}

	@Test
	public void screenToTileAndBackStaysWithinHalfATile()
	{
		MapView v = new MapView(0, 0, 800, 600, 3000, 3000, 3);
		for (int px = 0; px < 800; px += 13)
		{
			int tx = v.tileX(px);
			assertTrue(Math.abs(v.screenX(tx) - px) <= 2 + 1e-9);
		}
		for (int py = 0; py < 600; py += 17)
		{
			int ty = v.tileY(py);
			assertTrue(Math.abs(v.screenY(ty) - py) <= 2 + 1e-9);
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void zeroZoomIsRefused()
	{
		new MapView(0, 0, 10, 10, 0, 0, 0);
	}
}
