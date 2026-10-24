package com.bestpath.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.path.Tiles;
import org.junit.Test;

public class MenuChoiceTest
{
	private static final int MAP_TILE = Tiles.pack(3210, 3424, 0);
	private static final int SCENE_TILE = Tiles.pack(3222, 3218, 0);

	@Test
	public void worldMapGetsSetAndClearWhileARouteIsActive()
	{
		MenuChoice c = MenuChoice.decide(true, false, true, MAP_TILE, -1, -1, true);
		assertEquals(MenuChoice.Place.WORLD_MAP, c.getPlace());
		assertTrue(c.hasSet());
		assertEquals(MAP_TILE, c.getSetTile());
		assertTrue(c.hasClear());
		assertEquals(-1, c.getInsertAt()); // the top of the open menu
	}

	@Test
	public void aWorldMapClickIsAlwaysOnTheGroundFloor()
	{
		// the world map shows the ground floor: plane 0 whatever plane the player is on
		int t = MenuChoice.mapTile(3210, 3424);
		assertEquals(Tiles.pack(3210, 3424, 0), t);
		assertEquals(0, MenuChoice.MAP_PLANE);
		MenuChoice c = MenuChoice.decide(true, false, true, t, -1, -1, false);
		assertEquals(0, Tiles.plane(c.getSetTile()));
		assertEquals(-1, MenuChoice.mapTile(-1, 3424)); // off what a destination can be
	}

	@Test
	public void aGameTileKeepsItsOwnPlane()
	{
		int upstairs = MenuChoice.tile(3222, 3218, 2);
		MenuChoice c = MenuChoice.decide(true, true, false, -1, 3, upstairs, false);
		assertEquals(2, Tiles.plane(c.getSetTile()));
	}

	@Test
	public void worldMapWithoutARouteGetsOnlySet()
	{
		MenuChoice c = MenuChoice.decide(true, true, true, MAP_TILE, -1, -1, false);
		assertTrue(c.hasSet());
		assertFalse(c.hasClear());
	}

	@Test
	public void worldMapOptionOffAddsNothingThere()
	{
		assertSame(MenuChoice.NONE, MenuChoice.decide(false, true, true, MAP_TILE, -1, -1, true));
	}

	@Test
	public void aTileOffTheMapStillOffersClear()
	{
		MenuChoice c = MenuChoice.decide(true, false, true, -1, -1, -1, true);
		assertFalse(c.hasSet());
		assertTrue(c.hasClear());
		assertSame(MenuChoice.NONE, MenuChoice.decide(true, false, true, -1, -1, -1, false));
	}

	@Test
	public void sceneIsOffByDefaultAndOnlyNextToWalkHere()
	{
		// scene option off (the default): nothing in the scene
		assertSame(MenuChoice.NONE, MenuChoice.decide(true, false, false, -1, 3, SCENE_TILE, true));
		// on: just below "Walk here" (created at its index), never Clear in the scene
		MenuChoice c = MenuChoice.decide(true, true, false, -1, 3, SCENE_TILE, true);
		assertEquals(MenuChoice.Place.SCENE, c.getPlace());
		assertEquals(SCENE_TILE, c.getSetTile());
		assertEquals(3, c.getInsertAt());
		assertFalse(c.hasClear());
		// no "Walk here" (an interface, the minimap...) or no tile: nothing
		assertSame(MenuChoice.NONE, MenuChoice.decide(true, true, false, -1, -1, SCENE_TILE, false));
		assertSame(MenuChoice.NONE, MenuChoice.decide(true, true, false, -1, 3, -1, false));
	}

	@Test
	public void theMapWinsOverTheSceneBehindIt()
	{
		MenuChoice c = MenuChoice.decide(true, true, true, MAP_TILE, 3, SCENE_TILE, false);
		assertEquals(MenuChoice.Place.WORLD_MAP, c.getPlace());
		assertEquals(MAP_TILE, c.getSetTile());
	}

	@Test
	public void tilesOutsideTheWorldAreRefused()
	{
		assertEquals(Tiles.pack(3200, 3200, 2), MenuChoice.tile(3200, 3200, 2));
		assertEquals(-1, MenuChoice.tile(-1, 3200, 0));
		assertEquals(-1, MenuChoice.tile(3200, 0x4000, 0));
		assertEquals(-1, MenuChoice.tile(3200, 3200, 4));
	}
}
