package com.bestpath.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import com.bestpath.core.path.Tiles;
import java.awt.Color;
import java.util.Arrays;
import org.junit.Test;

/** The "Arrows at turns" style and the route's one colour. */
public class PathTurnsTest
{
	private static int[] walk(int... xy)
	{
		int[] t = new int[xy.length / 2];
		for (int i = 0; i < t.length; i++)
		{
			t[i] = Tiles.pack(3200 + xy[2 * i], 3200 + xy[2 * i + 1], 0);
		}
		return t;
	}

	private static int[] ends(int[] tiles, int from)
	{
		int[] out = new int[16];
		return Arrays.copyOf(out, PathTurns.runEnds(tiles, from, tiles.length, out));
	}

	@Test
	public void anArrowOnlyWhereTheWalkTurns()
	{
		// straight: one arrow, at the end
		assertArrayEquals(new int[]{4}, ends(walk(0, 0, 1, 0, 2, 0, 3, 0, 4, 0), 0));
		// east, then north: at the corner and at the end
		int[] corner = walk(0, 0, 1, 0, 2, 0, 2, 1, 2, 2, 2, 3);
		assertArrayEquals(new int[]{2, 5}, ends(corner, 0));
		// from part-way (the walked part left out): only the end
		assertArrayEquals(new int[]{5}, ends(corner, 2));
		// a staircase (east, north-east, east...) is no turn
		assertArrayEquals(new int[]{5}, ends(walk(0, 0, 1, 0, 2, 1, 3, 1, 4, 2, 5, 2), 0));
		// a one-tile wobble is no turn either
		assertArrayEquals(new int[]{5}, ends(walk(0, 0, 1, 0, 2, 0, 3, 1, 4, 1, 5, 1), 0));
		// nothing to point along
		assertArrayEquals(new int[0], ends(walk(0, 0), 0));
	}

	@Test
	public void oneColourEverywhere()
	{
		RouteColours d = RouteColours.defaults();
		assertEquals(new Color(0, 200, 255), d.base);
		// the light blue walking tiles as before
		assertEquals(new Color(0, 200, 255, 40), d.pathFill);
		assertEquals(new Color(0, 200, 255, 150), d.pathLine);
		// transport tiles and the current step: the same colour (the current step lighter), no orange or yellow
		assertEquals(d.base.getRGB() & 0xFFFFFF, d.markerLine.getRGB() & 0xFFFFFF);
		assertEquals(new Color(102, 222, 255), new Color(d.currentLine.getRGB() & 0xFFFFFF));
		// a colour the player picks
		RouteColours green = RouteColours.of(Color.GREEN);
		assertEquals(Color.GREEN.getRGB() & 0xFFFFFF, green.mapAhead.getRGB() & 0xFFFFFF);
		assertSame(green, RouteColours.of(new Color(0, 255, 0)));
		assertNotEquals(green, RouteColours.of(null));
	}
}
