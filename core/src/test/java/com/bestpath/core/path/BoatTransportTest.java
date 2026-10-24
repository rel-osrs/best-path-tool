package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaGridBuilder;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Boat-only transports (the Wyrmscraig cave: only a boat can use this transport, so the sailing
 * pather must be able to use transports; the cave cannot be sloop sized). A small world like the real one: a
 * west island (x 3200-3259) by the main sea (x 3260-3339), and far to the north (y 3880-3910) a cave pool cut off from
 * every sea, with the Cavern dock and a pocket of land (x 3371-3385) no one can walk to. The "cave" is a two-way
 * boat-only transport from the main sea (3335, 3300) to the pool (3345, 3895), 10 ticks, rafts and skiffs only.
 */
class BoatTransportTest
{
	private static final Transport CAVE = Transport.between("cave", "Cave", 3335, 3300, 0, 3345, 3895, 0, 10, true, true).forBoats(2);

	private static boolean mainSea(int x, int y)
	{
		return x >= 3260 && x <= 3339 && y >= 3200 && y < 3392;
	}

	private static boolean pool(int x, int y)
	{
		return x >= 3340 && x <= 3370 && y >= 3880 && y <= 3910;
	}

	private static boolean land(int x, int y)
	{
		return x >= 3200 && x <= 3259 && y >= 3200 && y < 3392 || x >= 3371 && x <= 3385 && y >= 3880 && y <= 3910;
	}

	private static final List<Dock> DOCKS = List.of(
		dock(0, "West", 3258, 3250, 3261, 3250, 3257),
		dock(1, "Cavern", 3371, 3895, 3366, 3895, 3372));

	private static Dock dock(int id, String name, int lx, int ly, int bx, int by, int standX)
	{
		List<int[]> stand = new ArrayList<>();
		stand.add(new int[]{standX, ly, 0});
		return new Dock(id, name, 1, null, Dock.Kind.GANGPLANK, lx, ly, 0, 1, bx, by, 2, stand);
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

	private static final class World
	{
		final Pathfinder fast;
		final CoarseGraph graph;
		final SeaGrid sea;
		final SeaGrid seaWithoutCave;

		World()
		{
			MovementRules.Flags walk = (x, y) -> land(x, y) ? 0 : CollisionFlags.BLOCKED;
			MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
			List<Integer> regions = new ArrayList<>();
			for (int rx = 50; rx <= 52; rx++)
			{
				for (int ry : new int[]{50, 51, 52, 60, 61})
				{
					regions.add(rx << 8 | ry);
				}
			}
			CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{walk, none, none, none}, regions, 0, 0);
			graph = CoarseGraph.build(m);
			fast = new Pathfinder(m, graph, null);
			SeaMap.Builder b = new SeaMap.Builder();
			for (int x = 3200; x < 3392; x++)
			{
				for (int y = 3200; y < 3392; y++)
				{
					if (mainSea(x, y))
					{
						b.set(x, y, 131);
					}
				}
				for (int y = 3880; y <= 3910; y++)
				{
					if (pool(x, y))
					{
						b.set(x, y, 131);
					}
				}
			}
			SeaMap map = b.build();
			MovementRules.Flags seaFlags = (x, y) -> buoy(x, y) ? CollisionFlags.OBJECT : 0;
			SeaPieces pieces = SeaPieces.build(map, seaFlags);
			sea = SeaGridBuilder.build(map, pieces, seaFlags, new int[][]{{3335, 3300}, {3345, 3895}});
			seaWithoutCave = SeaGridBuilder.build(map, pieces, seaFlags);
		}
	}

	private static final World WORLD = new World();

	private static Sailing sailing()
	{
		return new Sailing(WORLD.sea, DOCKS, null, WORLD.graph, null);
	}

	private static Boat boat(BoatType type, int dock)
	{
		return new Boat(1, type, 0, 0, 0, 0, false, false, Boat.Focus.NONE, dock);
	}

	private static Route route(Sailing s, int sx, int sy, int dx, int dy, Boat b, List<Transport> transports)
	{
		PathQuery q = new PathQuery(sx, sy, 0, dx, dy, 0, transports, 2);
		Route r = SailingDiagnostics.route(s, q, new Sailing.Options(99, null, List.of(b), 0, true, Sailing.DEFAULT_TURN_COST), WORLD.fast::find);
		if (r.isFound())
		{
			int total = 0;
			for (Route.Step st : r.getSteps())
			{
				total += st.getTicks();
			}
			assertEquals(r.getTicks(), total, "steps add up");
		}
		return r;
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
	void theGridHasThePoolOnlyWithTheCave()
	{
		assertTrue(WORLD.sea.isOcean(3350, 3890));
		assertFalse(WORLD.seaWithoutCave.isOcean(3350, 3890), "cut off from the main ocean: left out as before");
		assertTrue(WORLD.seaWithoutCave.isOcean(3300, 3300));
	}

	@Test
	void walkingNeverUsesABoatOnlyTransport()
	{
		Transport hop = Transport.between("hop", "Hop", 3210, 3210, 0, 3250, 3380, 0, 1, false, true).forBoats(3);
		PathQuery q = new PathQuery(3210, 3210, 0, 3250, 3380, 0, List.of(hop, CAVE), 2);
		assertTrue(q.getTransports().isEmpty(), "kept apart");
		assertEquals(List.of(hop, CAVE), q.getBoatTransports());
		assertEquals(2, PathQueryBuilder.withStart(q, 3211, 3211, 0).withTransports(List.of()).getBoatTransports().size(), "kept by every copy");
		Route r = WORLD.fast.find(q);
		assertTrue(r.isFound());
		assertEquals(170, r.getTicks(), "walked, not hopped");
		for (Route.Step s : r.getSteps())
		{
			assertNull(s.getTransport());
		}
	}

	@Test
	void sailsThroughTheCaveToTheCavernDock()
	{
		Route r = route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.RAFT, 0), List.of(CAVE));
		assertTrue(r.isFound());
		assertEquals("WALK TRANSPORT SAIL TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		Route.Step cave = r.getSteps().get(3);
		assertEquals("cave", cave.getTransport().getId(), "the transport's own step (it can be skipped)");
		assertEquals(10, cave.getTicks());
		assertEquals(Tiles.pack(3335, 3300, 0), cave.getTiles()[0]);
		assertEquals(Tiles.pack(3345, 3895, 0), cave.getTiles()[1]);
		assertTrue(r.getSteps().get(2).getTransport().getName().endsWith(" to Cave"));
		assertTrue(r.getSteps().get(4).getTransport().getName().endsWith(" to Cavern"));
		assertTrue(r.getSteps().get(5).getTransport().getName().startsWith("Leave Boat 1: Raft at Cavern"));
		// each piece of sailing starts and ends at the cave's ends
		int[] in = r.getSteps().get(2).getTiles();
		int[] out = r.getSteps().get(4).getTiles();
		assertEquals(Tiles.pack(3335, 3300, 0), in[in.length - 1]);
		assertEquals(Tiles.pack(3345, 3895, 0), out[0]);
	}

	@Test
	void noCaveNoRoute()
	{
		assertFalse(route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.RAFT, 0), List.of()).isFound());
		Transport walked = Transport.between("cave", "Cave", 3335, 3300, 0, 3345, 3895, 0, 10, true, true);
		assertFalse(route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.RAFT, 0), List.of(walked)).isFound(),
			"a walking transport between sea tiles is no way for a boat");
	}

	@Test
	void sloopsDoNotFit()
	{
		assertFalse(route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.SLOOP, 0), List.of(CAVE)).isFound());
		assertTrue(route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.SKIFF, 0), List.of(CAVE)).isFound());
		Transport any = Transport.between("cave", "Cave", 3335, 3300, 0, 3345, 3895, 0, 10, true, true).forBoats(3);
		assertTrue(route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.SLOOP, 0), List.of(any)).isFound());
	}

	@Test
	void anOpenSeaDestinationInThePool()
	{
		Route r = route(sailing(), 3230, 3250, 3355, 3900, boat(BoatType.RAFT, 0), List.of(CAVE));
		assertTrue(r.isFound());
		assertEquals("WALK TRANSPORT SAIL TRANSPORT SAIL", kinds(r));
		int[] last = r.getSteps().get(4).getTiles();
		assertEquals(Tiles.pack(3355, 3900, 0), last[last.length - 1]);
	}

	@Test
	void fromABoatInThePoolBackOut()
	{
		Route r = route(sailing(), 3350, 3890, 3230, 3250, boat(BoatType.SKIFF, Boat.AT_START), List.of(CAVE));
		assertTrue(r.isFound());
		assertEquals("SAIL TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		Route.Step cave = r.getSteps().get(1);
		assertEquals(Tiles.pack(3345, 3895, 0), cave.getTiles()[0], "the way back");
		assertEquals(Tiles.pack(3335, 3300, 0), cave.getTiles()[1]);
	}

	@Test
	void oneWayOnly()
	{
		Transport in = Transport.between("cave", "Cave", 3335, 3300, 0, 3345, 3895, 0, 10, false, true).forBoats(2);
		assertTrue(route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.RAFT, 0), List.of(in)).isFound());
		assertFalse(route(sailing(), 3350, 3890, 3230, 3250, boat(BoatType.SKIFF, Boat.AT_START), List.of(in)).isFound());
	}

	@Test
	void theLowerBoundsGoThroughTheCave()
	{
		// a 300-tick way on foot to the pocket: the dock 600 tiles away in a straight line (over 400 ticks of sailing)
		// must not be ruled out, as the cave makes it about 80 ticks
		Transport longWay = Transport.between("long", "Long way", 3230, 3300, 0, 3378, 3895, 0, 300, false, true);
		Route r = route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.RAFT, 0), List.of(longWay, CAVE));
		assertTrue(r.isFound());
		assertTrue(r.getTicks() < 300, "sailed: " + r.getTicks() + " " + kinds(r));
		assertEquals("WALK TRANSPORT SAIL TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		Route walked = route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.RAFT, 0), List.of(longWay));
		assertEquals(50 + 300, walked.getTicks(), "without the cave: the long way");
	}

	@Test
	void theTicksAreThePlayersOwnChangeToo()
	{
		Route plain = route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.RAFT, 0), List.of(CAVE));
		Route slower = route(sailing(), 3230, 3250, 3378, 3895, boat(BoatType.RAFT, 0), List.of(CAVE.withTickChange(15)));
		assertEquals(plain.getTicks() + 15, slower.getTicks());
	}
}
