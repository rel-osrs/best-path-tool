package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Where a boat's course may end: a boat only paths to places you can get off, or the open
 * ocean; if the destination is on land, the boat is only considered if the search finds a docking point
 * near it: at a dock (then walking on) or on a destination of open sea; and a rock out at sea with nothing to stand
 * on in sight of it: the nearest open water (never "no route").
 *
 * <p>World: two islands (x 3200-3259 and 3340-3391, y 3200-3391) with sea between (x 3260-3339); docks West (land
 * 3258,3250) and East (land 3341,3250). Rocks (not ocean, blocked): one far out at 3300,3300; one by the west shore
 * at 3260,3230; one by the east shore at 3339,3290. A rock on the west island at 3230,3280.
 */
class BoatEndsTest
{
	private static final int RANGE = 10;
	private static final int[] OCEAN_ROCK = {3300, 3300};
	private static final int[] WEST_ROCK = {3260, 3230};
	private static final int[] EAST_ROCK = {3339, 3290};
	private static final int[] LAND_ROCK = {3230, 3280};

	private static boolean rock(int x, int y)
	{
		return x == OCEAN_ROCK[0] && y == OCEAN_ROCK[1] || x == WEST_ROCK[0] && y == WEST_ROCK[1] || x == EAST_ROCK[0] && y == EAST_ROCK[1];
	}

	private static boolean isSea(int x, int y)
	{
		return x >= 3260 && x <= 3339 && y >= 3200 && y < 3392 && !rock(x, y);
	}

	private static boolean inside(int x, int y)
	{
		return x >= 3200 && x < 3392 && y >= 3200 && y < 3392;
	}

	private static final List<Dock> DOCKS = List.of(dock(0, "West", 3258, 3261, 3257), dock(1, "East", 3341, 3337, 3342));

	private static Dock dock(int id, String name, int lx, int bx, int standX)
	{
		List<int[]> stand = new ArrayList<>();
		stand.add(new int[]{standX, 3250, 0});
		return new Dock(id, name, 1, null, Dock.Kind.GANGPLANK, lx, 3250, 0, 1, bx, 3250, 2, stand);
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
		final Pathfinder pf;
		final Sailing sail;

		World()
		{
			// walking: the sea and the rocks closed (as the tool does), everything else open
			MovementRules.Flags walk = (x, y) -> !inside(x, y) || x >= 3260 && x <= 3339 || x == LAND_ROCK[0] && y == LAND_ROCK[1]
				? CollisionFlags.BLOCKED : 0;
			MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
			List<Integer> regions = new ArrayList<>();
			for (int rx = 50; rx <= 52; rx++)
			{
				for (int ry = 50; ry <= 52; ry++)
				{
					regions.add(rx << 8 | ry);
				}
			}
			CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{walk, none, none, none}, regions, 0, 0);
			CoarseGraph graph = CoarseGraph.build(m);
			pf = new Pathfinder(m, graph, null);
			SeaMap.Builder b = new SeaMap.Builder();
			for (int x = 3260; x <= 3339; x++)
			{
				for (int y = 3200; y < 3392; y++)
				{
					if (isSea(x, y))
					{
						b.set(x, y, 131);
					}
				}
			}
			SeaMap map = b.build();
			MovementRules.Flags seaFlags = (x, y) -> buoy(x, y) || rock(x, y) ? CollisionFlags.OBJECT : 0;
			SeaGrid grid = SeaGridBuilder.build(map, SeaPieces.build(map, seaFlags), seaFlags);
			sail = new Sailing(grid, DOCKS, null, graph, null);
		}
	}

	private static final World WORLD = new World();
	/** Only walls block the view; this world has none. */
	private static final LineOfSight.WallFlags NO_WALLS = (x, y, p) -> 0;

	private static Sailing.Options raftAt(int dock)
	{
		return new Sailing.Options(99, null, List.of(new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, dock)), 0, true, Sailing.DEFAULT_TURN_COST);
	}

	private static PathQuery query(int sx, int sy, int dx, int dy)
	{
		return new PathQuery(sx, sy, 0, dx, dy, 0, List.of(), 2);
	}

	private static RouteDestination settle(int sx, int sy, int dx, int dy, Sailing.Options o)
	{
		return RouteDestination.resolve(WORLD.pf, WORLD.sail, o, query(sx, sy, dx, dy), RANGE, NO_WALLS, false);
	}

	/** The route to the settled destination (and its fallbacks, as the plugin and the Data Tool do). */
	private static Route route(int sx, int sy, RouteDestination end, Sailing.Options o)
	{
		for (RouteDestination e = end; e != null; e = e.getFallback())
		{
			Route r = WORLD.sail.route(query(sx, sy, e.getX(), e.getY()), o, WORLD.pf, e.isAtSea());
			if (r.isFound() || !e.isBySea())
			{
				return r;
			}
		}
		return Route.notFound(0, 0);
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

	private static int lastTile(Route r)
	{
		int[] t = r.getSteps().get(r.getSteps().size() - 1).getTiles();
		return t[t.length - 1];
	}

	/** Every sail step ends at a dock's water (followed by leaving the boat there) or on open sea at the route's end. */
	private static void assertEndsAtADockOrOnOpenSea(Route r)
	{
		List<Route.Step> steps = r.getSteps();
		for (int i = 0; i < steps.size(); i++)
		{
			if (steps.get(i).getKind() != Route.Step.Kind.SAIL)
			{
				continue;
			}
			boolean last = i == steps.size() - 1;
			if (last)
			{
				int t = lastTile(r);
				assertTrue(WORLD.sail.isOpenSea(Tiles.x(t), Tiles.y(t), 0), "the boat ends on open sea");
			}
			else
			{
				assertTrue(steps.get(i + 1).getTransport().getId().startsWith("sail-leave:"), "the boat is left at a dock");
			}
		}
	}

	@Test
	void aDestinationOfOpenSeaEndsOnTheBoatOverIt()
	{
		RouteDestination d = settle(3230, 3250, 3300, 3280, raftAt(0));
		assertArrayEquals(new int[]{3300, 3280, 0, 1}, d.getStop());
		assertTrue(d.isAtSea());
		Route r = route(3230, 3250, d, raftAt(0));
		assertEquals("WALK TRANSPORT SAIL", kinds(r));
		assertEquals(Tiles.pack(3300, 3280, 0), lastTile(r));
		// a sloop (3 wide) needs 2 tiles of clearance: 1 tile from the rock it stops 1 tile further out, its hull over
		// the destination; it never stops further than half its width away
		Sailing.Options sloop = new Sailing.Options(99, null, List.of(new Boat(1, BoatType.SLOOP, 0, 0, 0, 0, false, false, Boat.Focus.NONE, 0)), 0, true, Sailing.DEFAULT_TURN_COST);
		RouteDestination byRock = settle(3230, 3250, 3301, 3300, sloop);
		assertArrayEquals(new int[]{3301, 3300, 0, 1}, byRock.getStop());
		Route s = route(3230, 3250, byRock, sloop);
		assertTrue(s.isFound());
		assertEndsAtADockOrOnOpenSea(s);
		int end = lastTile(s);
		assertTrue(Math.hypot(Tiles.x(end) - 3301, Tiles.y(end) - 3300) <= 1.5, "the hull covers the destination: " + Tiles.x(end) + "," + Tiles.y(end));
	}

	@Test
	void aRockFarOutAtSeaEndsOnTheNearestOpenWater()
	{
		RouteDestination d = settle(3230, 3250, OCEAN_ROCK[0], OCEAN_ROCK[1], raftAt(0));
		assertNotNull(d, "not 'no route'");
		// the nearest open water (straight distance 1; ties: lower x first), flagged as open water by a blocked spot
		assertArrayEquals(new int[]{3299, 3300, 0, RouteDestination.OPEN_WATER}, d.getStop());
		assertTrue(d.isAtSea());
		assertNull(d.getFallback(), "nothing on land near it");
		Route r = route(3230, 3250, d, raftAt(0));
		assertEquals("WALK TRANSPORT SAIL", kinds(r));
		assertEquals(Tiles.pack(3299, 3300, 0), lastTile(r));
		// without a boat, or without sailing: nothing near it can be reached, no route
		assertNull(settle(3230, 3250, OCEAN_ROCK[0], OCEAN_ROCK[1], new Sailing.Options(99, null, List.of(), 0, true, Sailing.DEFAULT_TURN_COST)));
		assertNull(RouteDestination.resolve(WORLD.pf, null, null, query(3230, 3250, OCEAN_ROCK[0], OCEAN_ROCK[1]), RANGE, NO_WALLS, false));
	}

	@Test
	void aRockByTheCoastWithLandInSightEndsOnLandNeverBesideIt()
	{
		// the west shore: walked to, the boat is not used at all
		RouteDestination w = settle(3230, 3250, WEST_ROCK[0], WEST_ROCK[1], raftAt(0));
		assertArrayEquals(new int[]{3259, 3230, 0, 1}, w.getStop());
		assertFalse(w.isAtSea());
		assertFalse(w.isBySea());
		Route r = route(3230, 3250, w, raftAt(0));
		assertEquals("WALK", kinds(r));
		assertEquals(Tiles.pack(3259, 3230, 0), lastTile(r));
		// previously the same from the boat at sea ended on the boat beside the rock; now it sails to the West
		// dock and walks to the shore tile
		Sailing.Options aboard = raftAt(Boat.AT_START);
		RouteDestination fromSea = settle(3290, 3232, WEST_ROCK[0], WEST_ROCK[1], aboard);
		assertArrayEquals(new int[]{3259, 3230, 0, 1}, fromSea.getStop());
		Route s = route(3290, 3232, fromSea, aboard);
		assertEquals("SAIL TRANSPORT WALK", kinds(s));
		assertEndsAtADockOrOnOpenSea(s);
		assertEquals(Tiles.pack(3259, 3230, 0), lastTile(s));
	}

	@Test
	void aRockByAnIslandIsReachedThroughTheDockTheSearchFinds()
	{
		// from the west island the east shore tile beside the rock is only reached by boat: through the East dock
		RouteDestination d = settle(3230, 3250, EAST_ROCK[0], EAST_ROCK[1], raftAt(0));
		assertArrayEquals(new int[]{3340, 3290, 0, 1}, d.getStop());
		assertFalse(d.isAtSea(), "never on the boat beside it");
		assertTrue(d.isBySea());
		assertNull(d.getFallback(), "nothing within range on the west island");
		Route r = route(3230, 3250, d, raftAt(0));
		assertEquals("WALK TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		assertEndsAtADockOrOnOpenSea(r);
		assertTrue(r.getSteps().get(3).getTransport().getName().contains("East"));
		assertEquals(Tiles.pack(3340, 3290, 0), lastTile(r));
		// without sailing: nothing near it can be reached
		assertNull(RouteDestination.resolve(WORLD.pf, null, null, query(3230, 3250, EAST_ROCK[0], EAST_ROCK[1]), RANGE, NO_WALLS, false));
	}

	@Test
	void aWalkableTileAcrossTheSeaUsesTheDockWhenQuickest()
	{
		RouteDestination d = settle(3230, 3250, 3370, 3260, raftAt(0));
		assertArrayEquals(new int[]{3370, 3260, 0, 1}, d.getStop());
		Route r = route(3230, 3250, d, raftAt(0));
		assertEquals("WALK TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		assertEndsAtADockOrOnOpenSea(r);
		// a rock on the player's own island: walked to, the boat never considered beside it
		RouteDestination land = settle(3230, 3250, LAND_ROCK[0], LAND_ROCK[1], raftAt(0));
		assertFalse(land.isAtSea());
		assertFalse(land.isBySea());
		assertEquals("WALK", kinds(route(3230, 3250, land, raftAt(0))));
	}
}
