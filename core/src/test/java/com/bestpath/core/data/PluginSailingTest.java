package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaGridBuilder;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Sailing in the plugin's router: the sea file read back, the boats and Settings choices, a start
 * on the boat at sea. World: two islands (x 3200-3259 and 3340-3391) with sea between (x 3260-3339), docks
 * West (3258, 3250, with a shipwright) and East (3341, 3250, Sailing 50).
 */
class PluginSailingTest
{
	private static final int SEA_X0 = 3260;
	private static final int SEA_X1 = 3339;

	private static boolean isSea(int x, int y)
	{
		return x >= SEA_X0 && x <= SEA_X1 && y >= 3200 && y < 3392;
	}

	private static boolean inside(int x, int y)
	{
		return x >= 3200 && x < 3392 && y >= 3200 && y < 3392;
	}

	private static final List<Dock> DOCKS = List.of(
		dock(0, "West", 1, 3258, 3250, 3261, 3250, 3257).withShipwright("Shipwright Test"),
		dock(1, "East", 50, 3341, 3250, 3337, 3250, 3342));

	private static Dock dock(int id, String name, int level, int lx, int ly, int bx, int by, int standX)
	{
		List<int[]> stand = new ArrayList<>();
		stand.add(new int[]{standX, ly, 0});
		return new Dock(id, name, level, id == 1 ? "Test Quest" : null, Dock.Kind.GANGPLANK, lx, ly, 0, 1, bx, by, 2, stand);
	}

	private static boolean buoy(int x, int y)
	{
		for (Dock d : DOCKS)
		{
			if (x >= d.getBuoyX() && x < d.getBuoyX() + 2 && y >= d.getBuoyY() && y < d.getBuoyY() + 2)
			{
				return true;
			}
		}
		return false;
	}

	private static SeaGrid seaGrid()
	{
		SeaMap.Builder b = new SeaMap.Builder();
		for (int x = SEA_X0; x <= SEA_X1; x++)
		{
			for (int y = 3200; y < 3392; y++)
			{
				b.set(x, y, 131);
			}
		}
		SeaMap map = b.build();
		MovementRules.Flags f = (x, y) -> buoy(x, y) ? CollisionFlags.OBJECT : 0;
		return SeaGridBuilder.build(map, SeaPieces.build(map, f), f);
	}

	private static CollisionMap map()
	{
		MovementRules.Flags walk = (x, y) -> !inside(x, y) || isSea(x, y) ? CollisionFlags.BLOCKED : 0;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		List<Integer> regions = new ArrayList<>();
		for (int rx = 50; rx <= 52; rx++)
		{
			for (int ry = 50; ry <= 52; ry++)
			{
				regions.add(rx << 8 | ry);
			}
		}
		return CollisionMapBuilder.build(new MovementRules.Flags[]{walk, none, none, none}, regions, 0, 0);
	}

	private static SeaBits.Data roundTrip(SeaGrid grid, List<Dock> docks) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SeaBitsWriter.write(grid, docks, out);
		return SeaBits.read(new ByteArrayInputStream(out.toByteArray()));
	}

	static PluginRouter router(boolean withSea) throws IOException
	{
		CollisionMap m = map();
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(), Tables.empty(),
			new FreeLand.Builder().set(3200, 3200, 3391, 3391, true).build(), ClueData.EMPTY, withSea ? roundTrip(seaGrid(), DOCKS) : null);
		return new PluginRouter(data, Long.MAX_VALUE);
	}

	static Boat boat(int slot, int dock)
	{
		return new Boat(slot, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, dock);
	}

	private static String kinds(Route r)
	{
		StringBuilder sb = new StringBuilder();
		for (Route.Step s : r.getSteps())
		{
			sb.append(sb.length() == 0 ? "" : " ").append(s.getKind());
		}
		return sb.toString();
	}

	@Test
	void theSeaFileReadsBackTheSame() throws IOException
	{
		SeaGrid grid = seaGrid();
		SeaBits.Data back = roundTrip(grid, DOCKS);
		assertTrue(back.getGrid().sameAs(grid));
		assertEquals(grid.size(), back.getGrid().size());
		assertEquals(2, back.getDocks().size());
		Dock w = back.getDocks().get(0);
		assertEquals("West", w.getName());
		assertEquals("Shipwright Test", w.getShipwright());
		assertNull(w.getQuest());
		assertEquals("Test Quest", back.getDocks().get(1).getQuest());
		assertEquals(50, back.getDocks().get(1).getLevel());
		assertArrayEquals(new int[]{3342, 3250, 0}, back.getDocks().get(1).getLandTiles().get(0));
		for (int x = SEA_X0 - 2; x <= SEA_X1 + 2; x++)
		{
			assertEquals(grid.clearanceAt(x, 3300), back.getGrid().clearanceAt(x, 3300));
		}
	}

	@Test
	void theShipwrightsPlaceReadsBackAndOldFilesStillLoad() throws IOException
	{
		// routes walk to the shipwright; the sea file (version 1) carries where it stands
		SeaGrid grid = seaGrid();
		List<Dock> docks = List.of(DOCKS.get(0).withShipwright("Shipwright Placed", 3245, 3265, 0), DOCKS.get(1));
		SeaBits.Data back = roundTrip(grid, docks);
		assertEquals("Shipwright Placed", back.getDocks().get(0).getShipwright());
		assertArrayEquals(new int[]{3245, 3265, 0}, back.getDocks().get(0).getShipwrightTile());
		assertNull(back.getDocks().get(1).getShipwrightTile());
		// a shipwright without a place stays without one
		assertNull(roundTrip(grid, DOCKS).getDocks().get(0).getShipwrightTile());
		// a version 0 file (older): read, its shipwrights without a place (brought at the dock, as then)
		ByteArrayOutputStream old = new ByteArrayOutputStream();
		SeaBitsWriter.write(grid, docks, old, 0);
		SeaBits.Data v0 = SeaBits.read(new ByteArrayInputStream(old.toByteArray()));
		assertEquals("Shipwright Placed", v0.getDocks().get(0).getShipwright());
		assertNull(v0.getDocks().get(0).getShipwrightTile());
		assertEquals("Test Quest", v0.getDocks().get(1).getQuest());
		assertTrue(v0.getGrid().sameAs(grid));
	}

	@Test
	void routesSailLikeTheCoreSearch() throws IOException
	{
		PluginRouter r = router(true);
		assertTrue(r.getSailing() != null);
		Boat b = boat(1, 0);
		SailingSetup setup = new SailingSetup(List.of(b), null, true);
		PluginRouter.Answer a = PluginRouterDiagnostics.route(r, PlayerStateBuilder.maxed(), 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, setup);
		assertEquals("WALK TRANSPORT SAIL TRANSPORT WALK", kinds(a.getRoute()));
		// the same as the core sailing search with the player's Sailing level (and the clearance table dropped: same answers)
		Sailing s = new Sailing(seaGrid(), DOCKS, null, PluginRouterDiagnostics.pathfinder(r).getGraph(), null);
		Route core = s.route(new PathQuery(3230, 3250, 0, 3370, 3250, 0, List.of(), PluginRouter.PLANE_CHANGE_PENALTY,
			DoorMap.none(), PluginRouter.DOOR_COST), new Sailing.Options(99, null, List.of(b), 0, true, Sailing.DEFAULT_TURN_COST), PluginRouterDiagnostics.pathfinder(r));
		assertEquals(core.getTicks(), a.getRoute().getTicks());
		assertArrayEquals(core.getSteps().get(2).getTiles(), a.getRoute().getSteps().get(2).getTiles());
		assertArrayEquals(new int[]{3370, 3250, 0, 1}, a.getStop());
	}

	/** Developer details shows the walking search apart from sailing's own work. */
	@Test
	void theWalkingSearchIsTimedApartFromSailing() throws IOException
	{
		PluginRouter r = router(true);
		SailingSetup setup = new SailingSetup(List.of(boat(1, 0)), null, true);
		r.startTimes();
		PluginRouterDiagnostics.route(r, PlayerStateBuilder.maxed(), 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, setup);
		String all = String.join(" | ", r.timeLines());
		assertTrue(all.contains("Walking search: "), all);
		assertTrue(all.contains(PluginRouter.SAILING_PART + ": "), all);
		assertFalse(all.contains("Search with sailing"), all);
	}

	@Test
	void theDockLevelComesFromThePlayer() throws IOException
	{
		PluginRouter r = router(true);
		SailingSetup setup = new SailingSetup(List.of(boat(1, 0)), null, true);
		PlayerState low = new PlayerState.Builder("Low").members(true).level(Skill.SAILING, 49).build();
		// East needs Sailing 50: no other way across
		assertFalse(PluginRouterDiagnostics.route(r, low, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, setup).getRoute().isFound());
		PlayerState ok = new PlayerState.Builder("Ok").members(true).level(Skill.SAILING, 50)
			.quest("Test Quest", com.bestpath.core.req.QuestState.DONE).build();
		assertTrue(PluginRouterDiagnostics.route(r, ok, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, setup).getRoute().isFound());
	}

	@Test
	void aDockWhoseQuestIsNotFinishedIsNotUsed() throws IOException
	{
		// docks check their quests. East needs "Test Quest": no other way across without it
		PluginRouter r = router(true);
		SailingSetup setup = new SailingSetup(List.of(boat(1, 0)), null, true);
		for (com.bestpath.core.req.QuestState q : com.bestpath.core.req.QuestState.values())
		{
			PlayerState p = new PlayerState.Builder("Quest " + q).members(true).level(Skill.SAILING, 99).quest("Test Quest", q).build();
			assertEquals(q == com.bestpath.core.req.QuestState.DONE,
				PluginRouterDiagnostics.route(r, p, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, setup).getRoute().isFound(),
				"quest " + q);
		}
		// the plugin reads the docks' quests: they are among the requirements it collects its inputs from
		boolean listed = false;
		for (com.bestpath.core.req.Requirement q : r.requirements())
		{
			listed |= q instanceof com.bestpath.core.req.Requirement.Quest
				&& ((com.bestpath.core.req.Requirement.Quest) q).getQuest().equals("Test Quest");
		}
		assertTrue(listed);
	}

	@Test
	void theSettingsChooseBoatsAndShipwrights() throws IOException
	{
		PluginRouter r = router(true);
		PlayerState me = PlayerStateBuilder.maxed();
		// the boat is at East; the shipwright at West brings it (10 ticks more) unless switched off
		SailingSetup wright = new SailingSetup(List.of(boat(1, 1)), null, true);
		Route brought = PluginRouterDiagnostics.route(r, me, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, wright).getRoute();
		assertEquals("WALK TRANSPORT TRANSPORT SAIL TRANSPORT WALK", kinds(brought));
		SailingSetup noWright = new SailingSetup(List.of(boat(1, 1)), null, false);
		assertFalse(PluginRouterDiagnostics.route(r, me, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, noWright).getRoute().isFound());
		// two boats; only boat 2 (at East) allowed: as above
		SailingSetup onlyTwo = new SailingSetup(List.of(boat(1, 0), boat(2, 1)), Set.of(2), false);
		assertFalse(PluginRouterDiagnostics.route(r, me, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, onlyTwo).getRoute().isFound());
		SailingSetup both = new SailingSetup(List.of(boat(1, 0), boat(2, 1)), null, false);
		assertTrue(PluginRouterDiagnostics.route(r, me, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, both).getRoute().isFound());
		// no boat allowed at all: walking only
		assertTrue(new SailingSetup(List.of(boat(1, 0)), Set.of(), true).isEmpty());
	}

	@Test
	void capsizedAndBottledBoats() throws IOException
	{
		// a capsized boat through the shipwright at West (like a normal retrieval) when allowed
		PluginRouter r = router(true);
		PlayerState me = PlayerStateBuilder.maxed();
		Route capsized = PluginRouterDiagnostics.route(r, me, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE,
			new SailingSetup(List.of(boat(1, Boat.CAPSIZED)), null, true)).getRoute();
		assertEquals("WALK TRANSPORT TRANSPORT SAIL TRANSPORT WALK", kinds(capsized));
		assertTrue(capsized.getSteps().get(1).getTransport().getName().contains("Shipwright Test brings Boat 1: Raft to West (Recover-boat"));
		assertFalse(PluginRouterDiagnostics.route(r, me, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE,
			new SailingSetup(List.of(boat(1, Boat.CAPSIZED)), null, false)).getRoute().isFound());
		// a bottled boat: only with its bottle in the inventory (the maxed player has every item, but no bottle in hand)
		SailingSetup bottled = new SailingSetup(List.of(boat(1, Boat.BOTTLED)), null, false);
		assertFalse(PluginRouterDiagnostics.route(r, me, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, bottled).getRoute().isFound());
		PlayerState carrying = new PlayerState.Builder("Bottle").members(true).allUnlocked(true).level(Skill.SAILING, 99)
			.carry(Boat.BOTTLE_FULL, 1).build();
		Route released = PluginRouterDiagnostics.route(r, carrying, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, bottled)
			.getRoute();
		assertEquals("WALK TRANSPORT TRANSPORT SAIL TRANSPORT WALK", kinds(released));
		assertEquals("sail-release:1", released.getSteps().get(1).getTransport().getId());
		// a second bottled boat the Settings don't allow: the one bottle could hold it, so neither is used
		SailingSetup oneOfTwo = new SailingSetup(List.of(boat(1, Boat.BOTTLED), boat(2, Boat.BOTTLED)), Set.of(1), false);
		assertFalse(PluginRouterDiagnostics.route(r, carrying, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, oneOfTwo)
			.getRoute().isFound());
		// the bottle is among the inputs the plugin watches
		boolean listed = false;
		for (com.bestpath.core.req.Requirement q : r.requirements())
		{
			listed |= q instanceof com.bestpath.core.req.Requirement.Item
				&& ((com.bestpath.core.req.Requirement.Item) q).getItemIds()[0] == Boat.BOTTLE_FULL;
		}
		assertTrue(listed);
	}

	@Test
	void aRouteStartsFromTheBoatAtSea() throws IOException
	{
		PluginRouter r = router(true);
		SailingSetup aboard = new SailingSetup(List.of(boat(1, Boat.AT_START)), null, true);
		PluginRouter.Answer a = PluginRouterDiagnostics.route(r, PlayerStateBuilder.maxed(), 3300, 3280, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE,
			aboard);
		assertEquals("SAIL TRANSPORT WALK", kinds(a.getRoute()));
		assertEquals(Tiles.pack(3300, 3280, 0), a.getRoute().getSteps().get(0).getTiles()[0]);
		// to a tile at sea: ends on the boat there
		PluginRouter.Answer sea = PluginRouterDiagnostics.route(r, PlayerStateBuilder.maxed(), 3300, 3280, 0, 3300, 3220, 0, id -> false,
			PluginRouter.TransportFilter.NONE, aboard);
		assertEquals("SAIL", kinds(sea.getRoute()));
		assertArrayEquals(new int[]{3300, 3220, 0, 1}, sea.getStop());
	}

	@Test
	void noSeaNoBoatsOrAFreeWorldMeansWalkingOnly() throws IOException
	{
		SailingSetup setup = new SailingSetup(List.of(boat(1, 0)), null, true);
		PluginRouter land = router(false);
		assertNull(land.getSailing());
		assertFalse(PluginRouterDiagnostics.route(land, PlayerStateBuilder.maxed(), 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, setup)
			.getRoute().isFound());
		PluginRouter r = router(true);
		assertFalse(PluginRouterDiagnostics.route(r, PlayerStateBuilder.maxed(), 3230, 3250, 0, 3370, 3250, 0, id -> false).getRoute().isFound());
		PlayerState free = new PlayerState.Builder("Free").members(false).level(Skill.SAILING, 99).build();
		assertFalse(PluginRouterDiagnostics.route(r, free, 3230, 3250, 0, 3370, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE, setup).getRoute().isFound());
	}

	@Test
	void severalPlacesSailToTheChosenOne() throws IOException
	{
		PluginRouter r = router(true);
		SailingSetup setup = new SailingSetup(List.of(boat(1, 0)), null, true);
		// both places are on the far island: none can be reached on foot, the boat takes the player to the nearer
		int[] targets = {Tiles.pack(3380, 3380, 0), Tiles.pack(3370, 3250, 0)};
		PluginRouter.NearestAnswer n = PluginRouterDiagnostics.routeNearest(r, PlayerStateBuilder.maxed(), 3230, 3250, 0, targets, id -> false,
			PluginRouter.TransportFilter.NONE, 24, setup);
		assertEquals(1, n.getChosen());
		assertTrue(kinds(n.getAnswer().getRoute()).contains("SAIL"));
	}
}
