package com.bestpath.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import com.bestpath.core.data.WorldMaps;
import com.bestpath.core.data.WorldMapsBuilder;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The route drawn on an underground world map that draws areas elsewhere (the line went into the
 * black void at the cave's real tiles): lines, marks, the destination and the player's spot where the open map draws
 * them, a walk cut where the map's shift changes, and what the map does not show left out.
 */
public class WorldMapPathPlacementTest
{
	/** Game x 2000-2063 drawn where it is, 2064-2127 drawn 200 tiles east (y 9000-9063, every plane). */
	private static final WorldMaps MAPS;
	private static final int MAP;

	static
	{
		WorldMapsBuilder b = new WorldMapsBuilder();
		MAP = b.addMap("Test Underground");
		b.add(MAP, 2000, 9000, 64, 2000, 9000, 0, 4);
		b.add(MAP, 2064, 9000, 64, 2264, 9000, 0, 4);
		MAPS = b.build();
	}

	private static RouteStep walk(int x0, int x1, int y)
	{
		List<Integer> tiles = new ArrayList<>();
		for (int x = x0; x <= x1; x++)
		{
			tiles.add(Tiles.pack(x, y, 0));
		}
		int[] t = tiles.stream().mapToInt(Integer::intValue).toArray();
		return new RouteStep(RouteStep.Kind.WALK, "Walk", "", t.length - 1, List.of(), null, t, -1);
	}

	private static WorldMapPath build(RouteRequest to, RouteStep... steps)
	{
		return WorldMapPath.of(RouteResults.ready(9, to, List.of(steps)), null, MAP, WorldMapPath.Placement.on(MAPS, MAP));
	}

	private static int[] xs(WorldMapPath.Line l)
	{
		int[] a = new int[l.pointCount()];
		for (int i = 0; i < a.length; i++)
		{
			a[i] = l.x(i);
		}
		return a;
	}

	@Test
	public void aWalkIsCutWhereTheMapDrawsTheNextPartElsewhere()
	{
		WorldMapPath p = build(new RouteRequest(2100, 9010, 0), walk(2050, 2100, 9010));
		assertEquals(MAP, p.getMap());
		assertEquals(2, p.lineCount());
		// 2050-2063 where they are, 2064-2100 drawn 200 east: no line across the gap
		assertArrayEquals(new int[] {2050, 2063}, xs(p.line(0)));
		assertArrayEquals(new int[] {2264, 2300}, xs(p.line(1)));
		assertEquals(9010, p.line(1).y(0));
		// the tile indexes still count the step's own tiles (the player's progress)
		assertEquals(14, p.line(1).tileIndex(0));
		// the destination where the map draws it
		assertEquals(Tiles.pack(2300, 9010, 0), p.getDestination());
		// the player's spot on the step, drawn where the map draws it
		assertEquals(Tiles.pack(2270, 9010, 0), p.stepTile(0, 20));
	}

	@Test
	public void whatTheMapDoesNotShowIsLeftOut()
	{
		// from the surface (not on this map) into the cave: only the cave part is drawn, no destination off the map
		WorldMapPath p = build(new RouteRequest(3222, 3218, 0), walk(1990, 2010, 9010));
		assertEquals(1, p.lineCount());
		assertArrayEquals(new int[] {2000, 2010}, xs(p.line(0)));
		assertEquals(-1, p.getDestination());
	}

	@Test
	public void marksAndHopsWhereTheMapDrawsThem()
	{
		RouteStep ladder = new RouteStep(RouteStep.Kind.TRAVERSAL, "Use", "", 2, List.of(), "t", new int[0],
			Tiles.pack(2070, 9010, 0), Tiles.pack(2072, 9012, 0));
		RouteStep teleport = new RouteStep(RouteStep.Kind.TELEPORT, "Use", "", 5, List.of(), "t", new int[0], -1,
			Tiles.pack(2080, 9020, 0));
		RouteStep outside = new RouteStep(RouteStep.Kind.TRAVERSAL, "Use", "", 2, List.of(), "t", new int[0],
			Tiles.pack(2100, 9020, 0), Tiles.pack(3200, 3200, 0));
		WorldMapPath p = build(new RouteRequest(2072, 9012, 0), ladder, teleport, outside);
		assertEquals(3, p.markCount());
		assertEquals(Tiles.pack(2270, 9010, 0), p.mark(0).getTile());
		assertEquals(Tiles.pack(2280, 9020, 0), p.mark(1).getTile());
		assertEquals(Tiles.pack(2300, 9020, 0), p.mark(2).getTile());
		// the near ladder's dotted hop, drawn shifted; the one landing off this map gets no line
		assertEquals(1, p.lineCount());
		assertArrayEquals(new int[] {2270, 2272}, xs(p.line(0)));
	}

	@Test
	public void anUnknownMapDrawsEverythingWhereItIs()
	{
		WorldMapPath p = WorldMapPath.of(RouteResults.ready(9, new RouteRequest(2100, 9010, 0), List.of(walk(2050, 2100, 9010))),
			null, -1, WorldMapPath.Placement.on(MAPS, -1));
		assertEquals(-1, p.getMap());
		assertEquals(1, p.lineCount());
		assertArrayEquals(new int[] {2050, 2100}, xs(p.line(0)));
		assertEquals(Tiles.pack(2100, 9010, 0), p.getDestination());
	}
}
