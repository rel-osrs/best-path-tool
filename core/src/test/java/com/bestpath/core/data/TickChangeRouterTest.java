package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.BruteForce;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Skill;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The player's own tick changes on the plugin's route path: they come with the transport filter,
 * the player's transport list is built with them ({@link PlayerTransports}), and routes, totals and the several
 * places search's lower bounds use them. Each route is checked against the brute-force checker on the same
 * transports.
 */
class TickChangeRouterTest
{
	private static final PlayerState MEMBER = new PlayerState.Builder("Member").members(true).level(Skill.AGILITY, 1).build();

	private static TransportLine line(String id, TransportLine.Kind kind, String name, String category, String network,
		int[] start, int[] end, int ticks, String requirements, String back)
	{
		return TransportLine.builder(id, kind).name(name).category(category).start(start[0], start[1], start[2])
			.end(end[0], end[1], end[2]).ticks(ticks).network(network).requirements(requirements)
			.requirementsBack(back).build();
	}

	/** One open region (3200-3263, 3200-3263) on planes 0 and 1. */
	private static PluginRouter router()
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, open, none, none}, List.of(50 << 8 | 50), 0, 0);
		List<TransportLine> lines = List.of(
			line("t-near", TransportLine.Kind.TELEPORT, "Near teleport", "TRAVEL", "", new int[3], new int[]{3240, 3240, 0}, 5,
				"", null),
			line("t-far", TransportLine.Kind.TELEPORT, "Far teleport", "TRAVEL", "", new int[3], new int[]{3255, 3255, 0}, 30,
				"", null),
			// way up for everyone, way down only with Agility 99: two transports, the way back is "t-ladder:back"
			line("t-ladder", TransportLine.Kind.TWO_WAY, "Ladder", "CLIMB_BOTH", "", new int[]{3210, 3210, 0},
				new int[]{3210, 3210, 1}, 3, "", "Agility 99"),
			line("t-stairs", TransportLine.Kind.TWO_WAY, "Stairs", "CLIMB_BOTH", "", new int[]{3230, 3210, 1},
				new int[]{3230, 3210, 0}, 3, "", null),
			line("b-a", TransportLine.Kind.NETWORK, "Port A", "TRAVEL", "Test boats", new int[3], new int[]{3205, 3230, 0}, 20,
				"", null),
			line("b-b", TransportLine.Kind.NETWORK, "Port B", "TRAVEL", "Test boats", new int[3], new int[]{3260, 3230, 0}, 20,
				"", null));
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines,
			Tables.empty(), FreeLand.NONE);
		return new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
	}

	/** Leaves nothing out; the given tick changes by transport id. */
	private static PluginRouter.TransportFilter ticks(Map<String, Integer> changes)
	{
		return new PluginRouter.TransportFilter()
		{
			@Override
			public boolean excluded(Transport t, TransportLine line)
			{
				return false;
			}

			@Override
			public int tickChange(String transportId)
			{
				return changes.getOrDefault(transportId, 0);
			}
		};
	}

	/** The route, checked against the brute-force checker on the very transports it was searched with. */
	private static Route route(PluginRouter r, int sx, int sy, int sp, int dx, int dy, int dp, Map<String, Integer> changes)
	{
		PluginRouter.Answer a = PluginRouterDiagnostics.route(r, MEMBER, sx, sy, sp, dx, dy, dp, id -> false, ticks(changes));
		PathQuery q = new PathQuery(sx, sy, sp, dx, dy, dp, a.getTransports().getTransports(), PluginRouter.PLANE_CHANGE_PENALTY,
			DoorMap.none(), PluginRouter.DOOR_COST);
		Route brute = BruteForce.find(r.getData().getMap(), q);
		Route found = a.getRoute();
		assertEquals(brute.isFound(), found.isFound());
		assertEquals(brute.getTicks(), found.getTicks(), "same as the brute-force checker");
		int sum = 0;
		for (Route.Step s : found.getSteps())
		{
			sum += s.getTicks();
		}
		assertEquals(found.getTicks(), sum, "the total is the steps' ticks");
		return found;
	}

	private static String transportOf(Route r)
	{
		for (Route.Step s : r.getSteps())
		{
			if (s.getTransport() != null)
			{
				return s.getTransport().getId();
			}
		}
		return null;
	}

	@Test
	void aTeleportLoweredToZeroIsPickedWhenItIsThenCheapest()
	{
		PluginRouter r = router();
		Route data = route(r, 3201, 3201, 0, 3256, 3256, 0, Map.of());
		assertEquals(5 + 16, data.getTicks(), "the near teleport and 16 tiles");
		assertEquals("t-near", transportOf(data));
		Route lowered = route(r, 3201, 3201, 0, 3256, 3256, 0, Map.of("t-far", -30));
		assertEquals(0 + 1, lowered.getTicks(), "the far teleport at 0 ticks and one tile");
		assertEquals("t-far", transportOf(lowered));
		assertEquals(30, findStep(lowered, "t-far").getTransport().getTicks(), "the data's ticks are kept");
		assertEquals(0, findStep(lowered, "t-far").getTicks(), "the step costs the changed ticks");
		Route raised = route(r, 3201, 3201, 0, 3256, 3256, 0, Map.of("t-near", 20));
		assertEquals(30 + 1, raised.getTicks(), "near teleport now 25 + 16: the far one wins");
		assertEquals("t-far", transportOf(raised));
		// same router again without changes: the same route as at first (nothing kept from the changed searches)
		assertEquals(data.getTicks(), route(r, 3201, 3201, 0, 3256, 3256, 0, Map.of()).getTicks());
	}

	@Test
	void aPlaneChangeCanBeLoweredToZeroWithItsPenalty()
	{
		PluginRouter r = router();
		Route data = route(r, 3210, 3211, 0, 3210, 3211, 1, Map.of());
		assertEquals(1 + 3 + PluginRouter.PLANE_CHANGE_PENALTY + 1, data.getTicks(), "walk, ladder 3 + plane change, walk");
		Route zero = route(r, 3210, 3211, 0, 3210, 3211, 1, Map.of("t-ladder", -3 - PluginRouter.PLANE_CHANGE_PENALTY));
		assertEquals(1 + 0 + 1, zero.getTicks());
		Route below = route(r, 3210, 3211, 0, 3210, 3211, 1, Map.of("t-ladder", -50));
		assertEquals(2, below.getTicks(), "never below 0");
	}

	@Test
	void aSplitTwoWaysWayBackAndNetworkRidesHaveTheirOwnIds()
	{
		PluginRouter r = router();
		PlayerTransports.Result built = PluginRouterDiagnostics.route(r, MEMBER, 3201, 3201, 0, 3202, 3202, 0, id -> false,
			ticks(Map.of("t-ladder", -1, "Test boats:Port A>Port B", -15))).getTransports();
		Transport up = byId(built, "t-ladder");
		assertNotNull(up);
		assertEquals(2 + PluginRouter.PLANE_CHANGE_PENALTY, up.cost(PluginRouter.PLANE_CHANGE_PENALTY));
		assertTrue(byId(built, "t-ladder:back") == null, "the way down needs Agility 99");
		Transport ride = byId(built, "Test boats:Port A>Port B");
		assertEquals(5, ride.cost(PluginRouter.PLANE_CHANGE_PENALTY));
		assertEquals(20, byId(built, "Test boats:Port B>Port A").cost(PluginRouter.PLANE_CHANGE_PENALTY));
		assertEquals("b-b", built.lineOf(ride).getId(), "the changed ride keeps its line");
		assertEquals(20 + 2, route(r, 3205, 3230, 0, 3260, 3232, 0, Map.of("Test boats:Port A>Port B", 0)).getTicks());
		assertEquals(5 + 2, route(r, 3205, 3230, 0, 3260, 3232, 0, Map.of("Test boats:Port A>Port B", -15)).getTicks());
	}

	@Test
	void severalPlacesCompareTheChangedTicks()
	{
		PluginRouter r = router();
		int walkNear = Tiles.pack(3215, 3201, 0); // 14 tiles away on foot
		int farOff = Tiles.pack(3255, 3254, 0); // the far teleport's landing is 1 tile away
		PluginRouter.NearestAnswer data = PluginRouterDiagnostics.routeNearest(r, MEMBER, 3201, 3201, 0, new int[]{farOff, walkNear}, id -> false,
			ticks(Map.of()), 10);
		assertEquals(1, data.getChosen());
		assertEquals(14, data.getAnswer().getRoute().getTicks());
		PluginRouter.NearestAnswer lowered = PluginRouterDiagnostics.routeNearest(r, MEMBER, 3201, 3201, 0, new int[]{farOff, walkNear}, id -> false,
			ticks(Map.of("t-far", -28)), 10);
		assertEquals(0, lowered.getChosen(), "the lowered teleport makes the far place the nearest");
		assertEquals(2 + 1, lowered.getAnswer().getRoute().getTicks());
		assertEquals(3, route(r, 3201, 3201, 0, 3255, 3254, 0, Map.of("t-far", -28)).getTicks(), "same as one search");
	}

	private static Route.Step findStep(Route r, String id)
	{
		for (Route.Step s : r.getSteps())
		{
			if (s.getTransport() != null && s.getTransport().getId().equals(id))
			{
				return s;
			}
		}
		throw new AssertionError("no step " + id);
	}

	private static Transport byId(PlayerTransports.Result r, String id)
	{
		for (Transport t : r.getTransports())
		{
			if (t.getId().equals(id))
			{
				return t;
			}
		}
		return null;
	}
}
