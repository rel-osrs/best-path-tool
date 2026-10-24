package com.bestpath.tool.slayer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.WorldMaps;
import com.bestpath.core.data.WorldMapsBuilder;
import com.bestpath.core.path.Tiles;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A place's Wiki tiles become route points on the map data's ground. */
class SlayerExportTest
{
	/** Region 3200-3263 x 3200-3263: plane 0 open west of x 3230, plane 2 open everywhere, planes 1 and 3 blocked. */
	private static CollisionMap map()
	{
		MovementRules.Flags west = (x, y) -> x < 3230 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags open = (x, y) -> 0;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		return CollisionMapBuilder.build(new MovementRules.Flags[]{west, none, open, none}, List.of(50 << 8 | 50), 2735, 241);
	}

	@Test
	void tilesOnGroundAreGroupedIntoPoints()
	{
		SlayerExport.Result r = new SlayerExport.Result();
		// two groups more than 12 tiles apart: one point each, the tile nearest each group's middle
		int[] p = SlayerPoints.points("3201,3201,0 3203,3203,0 3205,3205,0 3220,3240,0", map(), r);
		assertArrayEquals(new int[]{Tiles.pack(3203, 3203, 0), Tiles.pack(3220, 3240, 0)}, p);
		assertEquals(0, r.tilesMoved + r.tilesDropped + r.tilesOtherPlane);
	}

	@Test
	void tilesOffTheGroundAreMovedPutOnAnotherPlaneOrDropped()
	{
		SlayerExport.Result r = new SlayerExport.Result();
		int moved = SlayerPoints.usable(map(), 3231, 3210, 0);
		assertEquals(3229, Tiles.x(moved), "moved two tiles west onto ground");
		assertEquals(0, Tiles.plane(moved));
		assertEquals(Tiles.pack(3250, 3210, 2), SlayerPoints.usable(map(), 3250, 3210, 0), "no ground on plane 0: plane 2 has");
		assertEquals(-1, SlayerPoints.usable(map(), 3000, 3000, 0), "outside the map data");
		int[] p = SlayerPoints.points("3250,3210,0 3000,3000,0", map(), r);
		assertArrayEquals(new int[]{Tiles.pack(3250, 3210, 2)}, p);
		assertEquals(1, r.tilesOtherPlane);
		assertEquals(1, r.tilesDropped);
		assertEquals(0, SlayerPoints.points("-", map(), r).length);
		assertArrayEquals(new int[]{Tiles.pack(3000, 3000, 0)}, SlayerPoints.points("3000,3000,0", null, r), "without a map: as they are");
	}

	/** A map "Cave" drawing game region 3200-3263 x 3200-3263 (planes 0-2) 128 tiles west, as the game's underground maps do. */
	private static WorldMaps drawnWest()
	{
		WorldMapsBuilder b = new WorldMapsBuilder();
		b.addMap("Gielinor Surface");
		int cave = b.addMap("Cave");
		b.add(cave, 3200, 3200, 64, 3072, 3200, 0, 3);
		return b.build();
	}

	@Test
	void tilesGivenWhereAWorldMapDrawsThemArePutOnTheirGameTile()
	{
		SlayerExport.Result r = new SlayerExport.Result();
		// drawn at 3122,3210: game tile 3250,3210; plane 0 is walled there, plane 2 (drawn there too) has ground
		assertEquals(Tiles.pack(3250, 3210, 2), SlayerPoints.drawnBack(map(), drawnWest(), 3122, 3210, 0));
		// the Wiki's plane is used when the map draws it and it has ground
		assertEquals(Tiles.pack(3210, 3210, 0), SlayerPoints.drawnBack(map(), drawnWest(), 3082, 3210, 0));
		assertEquals(-1, SlayerPoints.drawnBack(map(), drawnWest(), 2000, 2000, 0), "no map draws anything there");
		assertEquals(-1, SlayerPoints.drawnBack(map(), WorldMaps.NONE, 3122, 3210, 0), "no world maps");
		int[] p = SlayerPoints.points("3122,3210,0 3000,3000,0", map(), drawnWest(), r);
		assertArrayEquals(new int[]{Tiles.pack(3250, 3210, 2)}, p);
		assertEquals(1, r.tilesDrawnBack);
		assertEquals(0, r.tilesOtherPlane + r.tilesMoved);
		assertEquals(1, r.tilesDropped);
	}

	@Test
	void twoMapsDrawingDifferentGroundOnOneSpotAreLeftAlone()
	{
		WorldMapsBuilder b = new WorldMapsBuilder();
		b.addMap("Gielinor Surface");
		b.add(b.addMap("Cave"), 3200, 3200, 64, 3072, 3200, 0, 3);
		b.add(b.addMap("Other cave"), 3200, 3200, 64, 3080, 3200, 0, 3);
		// 3122,3210 is game 3250,3210 on "Cave" and 3242,3210 on "Other cave": both ground (plane 2), which one is unknown
		assertEquals(-1, SlayerPoints.drawnBack(map(), b.build(), 3122, 3210, 0));
	}

	@Test
	void aPlaceWithOneDrawnTileIsDrawnBackWhole()
	{
		// regions x 3072-3135 (ground west of x 3100 only) and 3200-3263 (ground everywhere), plane 0; "Cave" draws the
		// east region 128 tiles west, over the west region
		MovementRules.Flags f = (x, y) -> x < 3100 || x >= 3200 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap two = CollisionMapBuilder.build(new MovementRules.Flags[]{f, none, none, none}, List.of(48 << 8 | 50, 50 << 8 | 50), 2735, 241);
		SlayerExport.Result r = new SlayerExport.Result();
		// 3110,3210 has no ground (drawn: game 3238,3210); 3090,3210 has ground of the west region, but is drawn too: 3218,3210
		int[] p = SlayerPoints.points("3110,3210,0 3090,3210,0", two, drawnWest(), r);
		assertEquals(2, r.tilesDrawnBack);
		assertEquals(0, r.tilesMoved + r.tilesOtherPlane + r.tilesDropped);
		for (int t : p)
		{
			assertEquals(true, Tiles.x(t) >= 3200, "every point on the drawn area's game tiles: " + Tiles.x(t));
		}
		// a place with no tile that needs drawing back keeps its tiles as given
		SlayerExport.Result r2 = new SlayerExport.Result();
		assertArrayEquals(new int[]{Tiles.pack(3090, 3210, 0)}, SlayerPoints.points("3090,3210,0", two, drawnWest(), r2));
		assertEquals(0, r2.tilesDrawnBack);
	}

	@Test
	void aPlaceShiftMovesEveryTileOntoItsPlane()
	{
		assertEquals("1434,10079,2 1420,10087,2", SlayerExport.shifted("1370,10079,0 1356,10087,0", "64,0,2"));
		assertEquals("1420,10087,1", SlayerExport.shifted(" 1356,9991,0 ", "64, 96, 1"));
	}

	/** Places added by hand (the Wilderness spider places) are read after the Wiki's and placed the same way. */
	@Test
	void placesAddedByHandFollowTheWikisPlaces(@TempDir File ws) throws Exception
	{
		String header = "task\ttask_id\ttask_name\tplace\tkonar_area\tamount\tmulti\tcannon\tsafespot\tmonsters\ttiles\tpage\trevision"
			+ "\ttile_pages\tnotes\n";
		new File(ws, "data/wiki").mkdirs();
		new File(ws, "data/manual").mkdirs();
		Files.write(new File(ws, SlayerExport.PLACES).toPath(), (header
			+ "6217\t4\tSpiders\tGnome Maze\t-\t9\tno\tno\tno\tSpider\t3201,3201,0\tSlayer task/Spiders\t1\t-\t-\n")
			.getBytes(StandardCharsets.UTF_8));
		Files.write(new File(ws, SlayerExport.EXTRA_PLACES).toPath(), ("# by hand\n" + header
			+ "6217\t4\tSpiders\tSpider Hill\t-\t29\t?\tyes\t?\tSpider\t3220,3240,0\tSlayer task/Spiders\t1\tSpider@2\tA cannon.\n")
			.getBytes(StandardCharsets.UTF_8));
		SlayerTables tables = SlayerTables.of(Map.of(6217, SlayerTables.task(6217, 4, "Spiders")), List.of(), Map.of(), Map.of());
		SlayerExport.Result r = SlayerExport.build(tables, ws, map());
		assertEquals(2, r.places);
		assertEquals(1, r.placesAdded);
		com.bestpath.core.data.SlayerPlaces.Place hill = r.data.places(6217).get(1);
		assertEquals("Spider Hill", hill.getName());
		assertEquals("yes", hill.getCannon());
		assertArrayEquals(new int[]{Tiles.pack(3220, 3240, 0)}, hill.getPoints());
	}
}
