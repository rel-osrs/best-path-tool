package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Where the world maps draw areas ({@link WorldMaps}): both directions, planes drawn side by side, rectangles merged,
 * clashes left out, and the text form. The blocks are the cache's own (cache 2735) for two caves.
 */
class WorldMapsTest
{
	/** The surface (nothing moved) and the Unquiet Ocean caves. */
	private static WorldMaps caves()
	{
		WorldMapsBuilder b = new WorldMapsBuilder();
		int surface = b.addMap("Gielinor Surface");
		b.add(surface, 3200, 3200, 64, 3200, 3200, 0, 4); // Lumbridge's square
		int ocean = b.addMap("Unquiet Ocean Underground");
		// map squares: game 10374 / 10375 drawn 192 east, game 10634 / 10890 drawn 64 east and 64 south
		b.add(ocean, 2560, 8576, 64, 2752, 8576, 0, 4);
		b.add(ocean, 2560, 8640, 64, 2752, 8640, 0, 4);
		b.add(ocean, 2624, 8832, 64, 2688, 8768, 0, 4);
		b.add(ocean, 2688, 8832, 64, 2752, 8768, 0, 4);
		WorldMaps m = b.build();
		assertTrue(b.problems().isEmpty(), b.problems().toString());
		return m;
	}

	@Test
	void mapClickGivesTheRealTile()
	{
		WorldMaps m = caves();
		int ocean = m.mapByName("Unquiet Ocean Underground");
		// a click on the drawn lake cave (region 11142) is the real tile in region 10374, every plane
		assertArrayEquals(new int[] {2580, 8600, 0b1111}, m.toGame(ocean, 2772, 8600));
		// the drawn lava caves (10889 / 11145) are the real 10634 / 10890
		assertArrayEquals(new int[] {2650, 8860, 0b1111}, m.toGame(ocean, 2714, 8796));
		assertArrayEquals(new int[] {2750, 8894, 0b1111}, m.toGame(ocean, 2814, 8830));
		// where that map draws nothing: no tile (the real cave tiles themselves are not drawn there)
		assertNull(m.toGame(ocean, 2650, 8860));
		// the surface draws everything in its place
		assertArrayEquals(new int[] {3222, 3218, 0b1111}, m.toGame(m.mapByName("gielinor surface "), 3222, 3218));
	}

	@Test
	void routeTileIsDrawnWhereTheMapShowsIt()
	{
		WorldMaps m = caves();
		int ocean = m.mapByName("Unquiet Ocean Underground");
		int d = m.drawn(ocean, 2650, 8860, 0);
		assertEquals(2714, WorldMaps.x(d));
		assertEquals(8796, WorldMaps.y(d));
		d = m.drawn(ocean, 2580, 8600, 2);
		assertEquals(2772, WorldMaps.x(d));
		assertEquals(8600, WorldMaps.y(d));
		// a surface tile is not on the underground map, and the cave is not on the surface map
		assertEquals(-1, m.drawn(ocean, 3222, 3218, 0));
		assertEquals(-1, m.drawn(0, 2650, 8860, 0));
		assertEquals(WorldMaps.packed(3222, 3218), m.drawn(0, 3222, 3218, 0));
	}

	@Test
	void bothDirectionsAgreeEverywhere()
	{
		WorldMaps m = caves();
		int ocean = m.mapByName("Unquiet Ocean Underground");
		for (int x = 2600; x < 2900; x += 7)
		{
			for (int y = 8550; y < 8920; y += 5)
			{
				int[] g = m.toGame(ocean, x, y);
				if (g != null)
				{
					int d = m.drawn(ocean, g[0], g[1], 0);
					assertEquals(WorldMaps.packed(x, y), d, x + ", " + y);
				}
			}
		}
	}

	/** Dorgesh-Kaan: one game area drawn three times, a plane each (cache: shift 0, +64,+64 and +128,+128). */
	@Test
	void planesDrawnSideBySide()
	{
		WorldMapsBuilder b = new WorldMapsBuilder();
		int dk = b.addMap("Dorgesh-Kaan");
		b.add(dk, 2688, 5312, 64, 2688, 5312, 0, 1);
		b.add(dk, 2688, 5312, 64, 2752, 5376, 1, 1);
		b.add(dk, 2688, 5312, 64, 2816, 5440, 2, 1);
		WorldMaps m = b.build();
		assertTrue(b.problems().isEmpty(), b.problems().toString());
		assertArrayEquals(new int[] {2700, 5320, 0b001}, m.toGame(dk, 2700, 5320));
		assertArrayEquals(new int[] {2700, 5320, 0b010}, m.toGame(dk, 2764, 5384));
		assertArrayEquals(new int[] {2700, 5320, 0b100}, m.toGame(dk, 2828, 5448));
		assertEquals(WorldMaps.packed(2764, 5384), m.drawn(dk, 2700, 5320, 1));
		assertEquals(WorldMaps.packed(2828, 5448), m.drawn(dk, 2700, 5320, 2));
		assertEquals(-1, m.drawn(dk, 2700, 5320, 3)); // plane 3 is not on this map
	}

	@Test
	void whatAMapClickMeans()
	{
		WorldMaps m = caves();
		int ocean = m.mapByName("Unquiet Ocean Underground");
		// the drawn cave: its real tile, the route search picks the plane (all four drawn there)
		assertArrayEquals(new int[] {2650, 8860, -1}, m.mapClick(ocean, 2714, 8796));
		// the black void at the cave's real tiles (2623, 8606): nothing to set
		assertNull(m.mapClick(ocean, 2623, 8606));
		// a spot the map neither draws nor moves: as before
		assertArrayEquals(new int[] {3000, 9000, -1}, m.mapClick(ocean, 3000, 9000));
		// an unknown map: as before
		assertArrayEquals(new int[] {2714, 8796, -1}, m.mapClick(-1, 2714, 8796));
		assertArrayEquals(new int[] {2623, 8606, -1}, WorldMaps.NONE.mapClick(0, 2623, 8606));

		WorldMapsBuilder b = new WorldMapsBuilder();
		int dk = b.addMap("Dorgesh-Kaan");
		b.add(dk, 2688, 5312, 64, 2688, 5312, 0, 1);
		b.add(dk, 2688, 5312, 64, 2752, 5376, 1, 1);
		WorldMaps d = b.build();
		// one plane drawn there: that plane
		assertArrayEquals(new int[] {2700, 5320, 0}, d.mapClick(dk, 2700, 5320));
		assertArrayEquals(new int[] {2700, 5320, 1}, d.mapClick(dk, 2764, 5384));
	}

	@Test
	void neighbouringBlocksMergeIntoOneRectangle()
	{
		WorldMapsBuilder b = new WorldMapsBuilder();
		int map = b.addMap("Test");
		for (int x = 0; x < 64; x += 8)
		{
			for (int y = 0; y < 32; y += 8)
			{
				b.add(map, 1000 + x, 9000 + y, 8, 1104 + x, 9000 + y, 0, 1);
			}
		}
		b.add(map, 1000, 9032, 8, 1104, 9032, 0, 1); // one block more on the next row: a second rectangle
		b.add(map, 2000, 9000, 64, 2000, 9000, 0, 4);
		WorldMaps m = b.build();
		assertEquals(3, WorldMapsDiagnostics.rectangleCount(m, map));
		assertArrayEquals(new int[] {1063, 9031, 0b1}, m.toGame(map, 1167, 9031));
		assertArrayEquals(new int[] {1007, 9039, 0b1}, m.toGame(map, 1111, 9039));
		assertNull(m.toGame(map, 1112, 9039));
	}

	@Test
	void clashesWithinOneMapAreLeftOutAndReported()
	{
		WorldMapsBuilder b = new WorldMapsBuilder();
		int map = b.addMap("Test");
		b.add(map, 1000, 9000, 8, 1504, 9000, 0, 1);
		b.add(map, 2000, 9000, 8, 1504, 9000, 0, 1); // another place drawn on the same spot
		b.add(map, 1000, 9000, 8, 1600, 9000, 0, 1); // the first place's plane 0 drawn a second time
		WorldMaps m = b.build();
		assertEquals(2, b.problems().size(), b.problems().toString());
		assertEquals(1, WorldMapsDiagnostics.rectangleCount(m, map));
		assertArrayEquals(new int[] {1001, 9001, 0b1}, m.toGame(map, 1505, 9001));
		assertNull(m.toGame(map, 1601, 9001));
	}

	@Test
	void textFormRoundTrip()
	{
		WorldMaps m = caves();
		String text = WorldMapsWriter.write(m, "BestPath plugin data: world maps\nfrom cache 2735");
		WorldMaps back = WorldMaps.parse(Arrays.asList(text.split("\n")));
		assertEquals(text, WorldMapsWriter.write(back, "BestPath plugin data: world maps\nfrom cache 2735"));
		assertEquals(2, back.mapCount());
		assertEquals("Unquiet Ocean Underground", back.mapName(1));
		assertArrayEquals(new int[] {2650, 8860, 0b1111}, back.toGame(1, 2714, 8796));
		assertEquals(-1, WorldMaps.NONE.drawn(0, 1, 1, 0));
		assertEquals(-1, back.mapByName("Nowhere"));
	}
}
