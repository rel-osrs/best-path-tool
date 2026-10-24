package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.req.BankItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The route's real destination, settled once before any search (the real destination tile is
 * worked out before a pathfind starts), and the bank-first route going to that same tile.
 */
class RouteDestinationTest
{
	private static final int RANGE = 10;

	/** A closed room 3300..3304 x 3300..3304 (walls all round) and a rock at 3250,3250. */
	private static TestWorld world()
	{
		TestWorld w = new TestWorld();
		for (int i = 3300; i <= 3304; i++)
		{
			w.wallEast(3299, i);
			w.wallEast(3304, i);
			w.wallNorth(i, 3299);
			w.wallNorth(i, 3304);
		}
		w.block(3250, 3250);
		return w;
	}

	private static PathQuery query(int dx, int dy, List<Transport> transports)
	{
		return new PathQuery(3210, 3210, 0, dx, dy, 0, transports, 2);
	}

	@Test
	void aTileThatCanBeReachedIsItselfAndOneThatCannotIsTheNearestInSight()
	{
		TestWorld w = world();
		CollisionMap m = w.map();
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		LineOfSight.WallFlags walls = (x, y, p) -> w.get(x, y);
		assertArrayEquals(new int[]{3240, 3240, 0, 1}, RouteDestination.resolve(pf, null, null, query(3240, 3240, List.of()), RANGE, walls, false)
			.getStop());
		// the rock: the nearest tile that sees it, the same as the walking rule (routeEnd), never at sea without sailing
		RouteDestination d = RouteDestination.resolve(pf, null, null, query(3250, 3250, List.of()), RANGE, walls, false);
		int[] rock = d.getStop();
		assertArrayEquals(pf.routeEnd(query(3250, 3250, List.of()), RANGE, walls, false), rock);
		assertFalse(d.isAtSea());
		assertFalse(d.isBySea());
		assertEquals(1, Math.max(Math.abs(rock[0] - 3250), Math.abs(rock[1] - 3250)));
	}

	@Test
	void theBankFirstRouteGoesToTheSameSettledTile()
	{
		TestWorld w = world();
		CollisionMap m = w.map();
		Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
		LineOfSight.WallFlags walls = (x, y, p) -> w.get(x, y);
		// only a teleport whose rune is in the bank lands in the closed room
		Transport tele = Transport.teleport("bank law rune", "Teleport into the room", 3301, 3301, 0, 5, true);
		int[] carried = RouteDestination.resolve(pf, null, null, query(3302, 3302, List.of()), RANGE, walls, false).getStop();
		assertFalse(carried[0] == 3302 && carried[1] == 3302, "with what the player carries: outside the room");
		int[] wide = RouteDestination.resolve(pf, null, null, query(3302, 3302, List.of(tele)), RANGE, walls, false).getStop();
		assertArrayEquals(new int[]{3302, 3302, 0, 1}, wide, "with the bank's transports too: the tile itself");
		// the route searches go to the settled tile: without the bank there is none, so the bank-first route is taken
		PathQuery q = query(wide[0], wide[1], List.of());
		Route a = pf.find(q);
		assertFalse(a.isFound());
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{TestWorld.tile(3215, 3210)}, new String[]{"Test bank"});
		BankFirst.Plan p = BankFirst.tryBank(pf, q, a, wide, List.of(tele), banks, 15, null,
			t -> t.getId().startsWith("bank") ? List.of(new BankItem("law rune", new int[]{1}, 1)) : List.of());
		assertNotNull(p);
		assertTrue(p.isBanked());
		int[] last = p.getRoute().getSteps().get(p.getRoute().getSteps().size() - 1).getTiles();
		assertEquals(TestWorld.tile(3302, 3302), last[last.length - 1]);
		assertArrayEquals(wide, p.getStop());
	}
}
