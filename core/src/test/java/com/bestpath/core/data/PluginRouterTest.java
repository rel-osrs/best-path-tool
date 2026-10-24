package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The plugin router keeps one search, for the world type of the latest route, its memory
 * budget, and the transport filter (Settings tab categories).
 */
class PluginRouterTest
{
	private static final PlayerState MEMBER = PlayerStateBuilder.fresh();
	private static final PlayerState FREE = new PlayerState.Builder("Free").members(false).build();

	/** One open region (3200-3263, 3200-3263) on plane 0; the west half (x < 3232) is free land; one teleport. */
	private static PluginRouter router(long budget)
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		TransportLine tele = TransportLine.builder("t-tele", TransportLine.Kind.TELEPORT).name("Test teleport")
			.category("TELEPORT").end(3250, 3250, 0).ticks(5).teleportCategory("spell").build();
		FreeLand free = new FreeLand.Builder().set(3200, 3200, 3231, 3263, true).build();
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(tele),
			Tables.empty(), free);
		return new PluginRouter(data, budget);
	}

	@Test
	void onlyTheSearchForTheLatestWorldTypeIsKept()
	{
		PluginRouter r = router(Long.MAX_VALUE);
		assertTrue(r.isMembersSearch(), "members to begin with: most players are members");
		Pathfinder members = PluginRouterDiagnostics.pathfinder(r);
		assertTrue(PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3260, 3205, 0, id -> false).getRoute().isFound());
		assertSame(members, PluginRouterDiagnostics.pathfinder(r), "a members route uses the search made at the start");

		Route f = PluginRouterDiagnostics.route(r, FREE, 3201, 3201, 0, 3230, 3260, 0, id -> false).getRoute();
		assertTrue(f.isFound());
		assertFalse(r.isMembersSearch());
		Pathfinder free = PluginRouterDiagnostics.pathfinder(r);
		assertNotSame(members, free, "the members search is let go, not kept alongside");

		// back on a members world: a new members search, the same routes as a fresh router
		Route again = PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3260, 3205, 0, id -> false).getRoute();
		assertTrue(r.isMembersSearch());
		assertNotSame(free, PluginRouterDiagnostics.pathfinder(r));
		assertNotSame(members, PluginRouterDiagnostics.pathfinder(r));
		assertEquals(PluginRouterDiagnostics.route(router(Long.MAX_VALUE), MEMBER, 3201, 3201, 0, 3260, 3205, 0, id -> false).getRoute().getTicks(),
			again.getTicks());
	}

	@Test
	void theTimeOfEachPartIsKept()
	{
		PluginRouter r = router(Long.MAX_VALUE);
		r.startTimes();
		PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3260, 3205, 0, id -> false);
		PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3250, 3240, 0, id -> false);
		String all = String.join(" | ", r.timeLines());
		assertTrue(all.contains("Your usable transports: "), all);
		assertTrue(all.contains("Destination tile: "), all);
		assertTrue(all.contains("Walking search: ") && all.contains("(2 times)"), all);
		assertTrue(all.contains("Walk tables (walks.bin): not in the data"), all);
		assertTrue(all.contains("Search caches: "), all);
		r.startTimes();
		assertTrue(r.getTimes().parts.isEmpty(), "a new breakdown for each route");
	}

		@Test
	void aFreeWorldPlayerGetsNoRouteToMembersLand()
	{
		PluginRouter r = router(Long.MAX_VALUE);
		// x 3232 and up is members land: walking there and the teleport (3250, 3250) are both out
		PluginRouter.Answer a = PluginRouterDiagnostics.route(r, FREE, 3201, 3201, 0, 3260, 3205, 0, id -> false);
		assertTrue(a.isFreeWorld());
		assertFalse(a.getRoute().isFound());
		assertTrue(PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3260, 3205, 0, id -> false).getRoute().isFound());
	}

	@Test
	void overTheBudgetTheCachesAreDropped()
	{
		PluginRouter r = router(1); // any cache at all is over budget
		PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3260, 3205, 0, id -> false);
		assertEquals(1, PluginRouterDiagnostics.cacheClears(r));
		assertEquals(1, fullClears(r), "even the small tables are over a budget this small");
		assertEquals(0, PluginRouterDiagnostics.cacheBytes(r));
		PluginRouter big = router(Long.MAX_VALUE);
		PluginRouterDiagnostics.route(big, MEMBER, 3201, 3201, 0, 3260, 3205, 0, id -> false);
		assertEquals(0, PluginRouterDiagnostics.cacheClears(big));
		assertTrue(PluginRouterDiagnostics.cacheBytes(big) > 0);
	}

	/** Two open regions side by side (3200-3327, 3200-3263) on plane 0, so routes cross an entrance between cells. */
	private static PluginRouter twoRegions(long budget)
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3328 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50, 51 << 8 | 50),
			0, 0);
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(),
			Tables.empty(), null);
		return new PluginRouter(data, budget);
	}

	@Test
	void overTheBudgetTheSmallSlowTablesAreKept()
	{
		// only the big caches that are quick to redo go
		PluginRouter all = twoRegions(Long.MAX_VALUE);
		int ticks = PluginRouterDiagnostics.route(all, MEMBER, 3201, 3201, 0, 3320, 3205, 0, id -> false).getRoute().getTicks();
		long slow = PluginRouterDiagnostics.pathfinder(all).slowToRedoBytes();
		assertTrue(slow > 0, "the entrance tables were made");
		assertTrue(PluginRouterDiagnostics.cacheBytes(all) > slow, "and big ones besides");

		PluginRouter r = twoRegions(slow); // room for the small tables only
		assertEquals(ticks, PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3320, 3205, 0, id -> false).getRoute().getTicks());
		assertEquals(1, PluginRouterDiagnostics.cacheClears(r));
		assertEquals(0, fullClears(r));
		assertEquals(slow, PluginRouterDiagnostics.cacheBytes(r), "only the small slow-to-redo tables are left");
		assertEquals(ticks, PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3320, 3205, 0, id -> false).getRoute().getTicks(),
			"the same route with the tables kept");
		assertEquals(slow, PluginRouterDiagnostics.pathfinder(r).slowToRedoBytes(), "made once, used again");
	}

	@Test
	void theTransportFilterLeavesTransportsOutByTheirLine()
	{
		PluginRouter r = router(Long.MAX_VALUE);
		PlayerState mage = new PlayerState.Builder("Mage").members(true).build();
		assertEquals(6, PluginRouterDiagnostics.route(r, mage, 3201, 3201, 0, 3251, 3250, 0, id -> false).getRoute().getTicks());
		PluginRouter.TransportFilter noSpells = (t, line) -> line != null && "spell".equals(line.getTeleportCategory());
		Route walk = PluginRouterDiagnostics.route(r, mage, 3201, 3201, 0, 3251, 3250, 0, id -> false, noSpells).getRoute();
		assertEquals(50, walk.getTicks());
		PluginRouter.NearestAnswer n = PluginRouterDiagnostics.routeNearest(r, mage, 3201, 3201, 0,
			new int[]{com.bestpath.core.path.Tiles.pack(3251, 3250, 0)}, id -> false, noSpells, 5);
		assertEquals(50, n.getAnswer().getRoute().getTicks());
	}

	/** How often the router's small slow-to-redo tables had to go too. */
	private static int fullClears(PluginRouter r)
	{
		synchronized (r)
		{
			return r.fullClears;
		}
	}
}
