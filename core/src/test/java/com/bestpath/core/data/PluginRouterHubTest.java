package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Route;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Transports chaining on a tile off the walking map (the house in the plugin): the export leaves out cells
 * with no walkable tile, so the player-owned house spot (1857,7065,0) had no cell and every route through the house
 * (Teleport to House, then the jewellery box) was never found. The router now gives such hub tiles an empty cell.
 */
class PluginRouterHubTest
{
	private static final PlayerState MEMBER = PlayerStateBuilder.fresh();
	/** The house spot of the real data: off this test's map, as in the export. */
	private static final int HX = 1857;
	private static final int HY = 7065;
	private static final int REGION_A = 50 << 8 | 50; // 3200-3263, 3200-3263
	private static final int REGION_B = 52 << 8 | 50; // 3328-3391, 3200-3263 (not joined to A)

	private static TransportLine line(String id, TransportLine.Kind kind, int sx, int sy, int ex, int ey, int ticks)
	{
		return TransportLine.builder(id, kind).name(id)
			.category(kind == TransportLine.Kind.TELEPORT ? "TELEPORT" : "TRAVEL").start(sx, sy, 0).end(ex, ey, 0)
			.ticks(ticks).teleportCategory(kind == TransportLine.Kind.TELEPORT ? "spell" : null).build();
	}

	private static CollisionMap map()
	{
		MovementRules.Flags open = (x, y) -> (x >= 3200 && x < 3264 || x >= 3328 && x < 3392) && y >= 3200 && y < 3264 ? 0
			: CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		return CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(REGION_A, REGION_B), 0, 0);
	}

	/** A "house": a teleport and a two-way portal into the hub, a ride out of it to region B. */
	private static List<TransportLine> houseLines()
	{
		List<TransportLine> l = new ArrayList<>();
		l.add(line("tele-in", TransportLine.Kind.TELEPORT, 0, 0, HX, HY, 4));
		l.add(line("portal", TransportLine.Kind.TWO_WAY, 3210, 3210, HX, HY, 4));
		l.add(line("box", TransportLine.Kind.ONE_WAY, HX, HY, 3340, 3210, 14));
		return l;
	}

	private static PluginRouter router(List<TransportLine> lines)
	{
		PluginData data = PluginDataBuilder.of(map(), DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines,
			Tables.empty(), null);
		return new PluginRouter(data, Long.MAX_VALUE);
	}

	@Test
	void theHubTileGetsItsCell()
	{
		CollisionMap m = map();
		int houseCell = ((HX >> 6) << 8 | (HY >> 6)) << 2;
		assertEquals(Set.of(houseCell), SearchMaps.hubCells(m, houseLines()));
		// only the portal reaches the tile and only the portal leaves it: nothing to chain, no cell
		assertEquals(Set.of(), SearchMaps.hubCells(m, List.of(line("portal", TransportLine.Kind.TWO_WAY, 3210, 3210, HX, HY, 4))));
		// ends on the walking map are never hubs
		assertEquals(Set.of(), SearchMaps.hubCells(m, List.of(line("a", TransportLine.Kind.ONE_WAY, 3201, 3201, 3220, 3220, 4),
			line("b", TransportLine.Kind.ONE_WAY, 3220, 3220, 3240, 3240, 4))));
		CollisionMap more = m.withEmptyCells(Set.of(houseCell));
		assertEquals(m.size() + 1, more.size());
		assertFalse(more.canStep(HX, HY, 0, 1, 0), "the added cell has no step");
		assertTrue(more.canStep(3201, 3201, 0, 1, 0), "the stored cells keep their steps");
		assertTrue(m.withEmptyCells(Set.of()) == m);
	}

	@Test
	void routesChainThroughTheHouse()
	{
		PluginRouter r = router(houseLines());
		// teleport into the house (4), then the ride out (14), then walk 5 east and 5 north (5 ticks)
		Route a = PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3345, 3215, 0, id -> false).getRoute();
		assertTrue(a.isFound(), "region B is reached only through the house");
		assertEquals(4 + 14 + 5, a.getTicks());
		// without the teleport: walk to the portal (9), the portal (4), the ride (14), the walk (5)
		Route b = PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3345, 3215, 0, id -> id.equals("tele-in")).getRoute();
		assertTrue(b.isFound());
		assertEquals(9 + 4 + 14 + 5, b.getTicks());
		// standing in the house (the plugin routes a player inside the house from the house spot)
		Route c = PluginRouterDiagnostics.route(r, MEMBER, HX, HY, 0, 3345, 3215, 0, id -> false).getRoute();
		assertTrue(c.isFound());
		assertEquals(14 + 5, c.getTicks());
		// and out through the portal
		Route d = PluginRouterDiagnostics.route(r, MEMBER, HX, HY, 0, 3212, 3210, 0, id -> false).getRoute();
		assertTrue(d.isFound());
		assertEquals(4 + 2, d.getTicks());
	}
}
