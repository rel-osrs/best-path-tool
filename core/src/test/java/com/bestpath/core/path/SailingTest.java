package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
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
 * Routes that mix walking and sailing on a small world: two islands
 * (x 3200-3259 and 3340-3391) with sea between them (x 3260-3339), and a
 * band of fetid water across the sea (y 3300-3305). Docks: West (3258,
 * 3250), East (3341, 3250) and East North (3341, 3360, Sailing level 50).
 */
class SailingTest
{
	private static final int X0 = 3200;
	private static final int Y0 = 3200;
	private static final int SIZE = 192;
	private static final int SEA_X0 = 3260;
	private static final int SEA_X1 = 3339;

	private static boolean isSea(int x, int y)
	{
		return x >= SEA_X0 && x <= SEA_X1 && y >= Y0 && y < Y0 + SIZE;
	}

	private static boolean inside(int x, int y)
	{
		return x >= X0 && x < X0 + SIZE && y >= Y0 && y < Y0 + SIZE;
	}

	private static final List<Dock> DOCKS = List.of(
		dock(0, "West", 1, 3258, 3250, 3261, 3250, 3257),
		dock(1, "East", 1, 3341, 3250, 3337, 3250, 3342),
		dock(2, "East North", 50, 3341, 3360, 3337, 3360, 3342));

	private static Dock dock(int id, String name, int level, int lx, int ly, int bx, int by, int standX)
	{
		List<int[]> stand = new ArrayList<>();
		stand.add(new int[]{standX, ly, 0});
		return new Dock(id, name, level, null, Dock.Kind.GANGPLANK, lx, ly, 0, 1, bx, by, 2, stand);
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

		World()
		{
			// walking: the sea is closed (as the tool does), everything else open
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
			CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{walk, none, none, none}, regions, 0, 0);
			graph = CoarseGraph.build(m);
			fast = new Pathfinder(m, graph, null);
			// the sea: open water with a fetid band; buoys are solid
			SeaMap.Builder b = new SeaMap.Builder();
			for (int x = SEA_X0; x <= SEA_X1; x++)
			{
				for (int y = Y0; y < Y0 + SIZE; y++)
				{
					b.set(x, y, y >= 3300 && y <= 3305 ? 176 : 131);
				}
			}
			SeaMap map = b.build();
			MovementRules.Flags seaFlags = (x, y) -> buoy(x, y) ? CollisionFlags.OBJECT : 0;
			sea = SeaGridBuilder.build(map, SeaPieces.build(map, seaFlags), seaFlags);
		}
	}

	private static final World WORLD = new World();

	private static Boat boat(int dock, Boat.Focus focus, boolean inoculation)
	{
		return new Boat(1, BoatType.RAFT, 0, 0, 0, 0, inoculation, false, focus, dock); // 1.5 tiles per tick
	}

	private static Sailing sailing(List<Dock> docks)
	{
		return new Sailing(WORLD.sea, docks, null, WORLD.graph, null);
	}

	/** Finds the route with the sailing transports added and expanded; checks the ticks add up. */
	private static Route route(Sailing s, int sx, int sy, int dx, int dy, Sailing.Options o)
	{
		PathQuery q = new PathQuery(sx, sy, 0, dx, dy, 0, List.of(), 2);
		Route r = SailingDiagnostics.route(s, q, o, WORLD.fast::find);
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

	private static Sailing.Options options(Boat b)
	{
		return new Sailing.Options(99, null, List.of(b), 0, true, Sailing.DEFAULT_TURN_COST);
	}

	@Test
	void sailsFromTheDockWhereTheBoatIs()
	{
		Route r = route(sailing(DOCKS), 3230, 3250, 3370, 3250, options(boat(0, Boat.Focus.NONE, false)));
		assertTrue(r.isFound());
		assertEquals("WALK TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		// walk 27, board 10, sail about 74 tiles at 1.5 a tick, leave 10, walk 28
		int sail = r.getSteps().get(2).getTicks();
		assertTrue(sail >= 49 && sail <= 52, "sail " + sail);
		assertEquals(27 + 10 + sail + 10 + 28, r.getTicks());
		assertTrue(r.getSteps().get(1).getTransport().getName().startsWith("Board Boat 1: Raft at West"));
		assertTrue(r.getSteps().get(3).getTransport().getName().startsWith("Leave Boat 1: Raft at East"));
		// the sea tiles: from beside the West buoy to beside the East one, each step one tile
		int[] t = r.getSteps().get(2).getTiles();
		assertTrue(Tiles.x(t[0]) >= 3260 && Tiles.x(t[0]) <= 3263 && Tiles.x(t[t.length - 1]) >= 3336, "from buoy to buoy");
		for (int i = 1; i < t.length; i++)
		{
			assertTrue(Math.max(Math.abs(Tiles.x(t[i]) - Tiles.x(t[i - 1])), Math.abs(Tiles.y(t[i]) - Tiles.y(t[i - 1]))) == 1);
			assertTrue(isSea(Tiles.x(t[i]), Tiles.y(t[i])));
		}
	}

	@Test
	void boardingTicksAreThePlayersChoice()
	{
		// boarding (and leaving) 10 by default, player adjustable
		Sailing.Options o = options(boat(0, Boat.Focus.NONE, false));
		Route r = route(sailing(DOCKS), 3230, 3250, 3370, 3250, o.withBoardTicks(25));
		assertEquals("WALK TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		assertEquals(25, r.getSteps().get(1).getTicks(), "board");
		assertEquals(25, r.getSteps().get(3).getTicks(), "leave");
		assertEquals(fromWest() + 2 * (25 - com.bestpath.core.sea.Dock.BOARD_TICKS), r.getTicks());
		assertEquals(fromWest(), route(sailing(DOCKS), 3230, 3250, 3370, 3250, o.withBoardTicks(com.bestpath.core.sea.Dock.BOARD_TICKS))
			.getTicks());
	}

	/** Ticks of the plain route West island -> East island with the boat at West. */
	private static int fromWest()
	{
		return route(sailing(DOCKS), 3230, 3250, 3370, 3250, options(boat(0, Boat.Focus.NONE, false))).getTicks();
	}

	@Test
	void noBoatHereMeansNoSailingUnlessItCanBeBrought()
	{
		// the boat is at East; the player is on the West island
		Sailing plain = sailing(DOCKS);
		assertFalse(route(plain, 3230, 3250, 3370, 3250, options(boat(1, Boat.Focus.NONE, false))).isFound());
		// a shipwright at West brings it: 10 ticks more (and the raft's 250 coins)
		List<Dock> withWright = new ArrayList<>(DOCKS);
		withWright.set(0, DOCKS.get(0).withShipwright("Shipwright Test"));
		Route r = route(sailing(withWright), 3230, 3250, 3370, 3250, options(boat(1, Boat.Focus.NONE, false)));
		assertEquals("WALK TRANSPORT TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		assertEquals(fromWest() + Sailing.RECOVER_TICKS, r.getTicks());
		String bring = r.getSteps().get(1).getTransport().getName();
		assertTrue(bring.contains("Shipwright Test") && bring.contains("250 coins"), bring);
		// ... unless shipwrights are switched off
		assertFalse(route(sailing(withWright), 3230, 3250, 3370, 3250,
			new Sailing.Options(99, null, List.of(boat(1, Boat.Focus.NONE, false)), 0, false, Sailing.DEFAULT_TURN_COST)).isFound());
	}

	@Test
	void aCapsizedOrLostBoatComesWithAShipwright()
	{
		// like a normal retrieval (same coins, 10 ticks more), only where shipwrights are allowed
		List<Dock> withWright = new ArrayList<>(DOCKS);
		withWright.set(0, DOCKS.get(0).withShipwright("Shipwright Test"));
		Sailing s = sailing(withWright);
		for (int place : new int[]{Boat.CAPSIZED, Boat.LOST})
		{
			Route r = route(s, 3230, 3250, 3370, 3250, options(boat(place, Boat.Focus.NONE, false)));
			assertEquals("WALK TRANSPORT TRANSPORT SAIL TRANSPORT WALK", kinds(r));
			assertEquals(fromWest() + Sailing.RECOVER_TICKS, r.getTicks());
			Transport bring = r.getSteps().get(1).getTransport();
			assertEquals("sail-bring:1", bring.getId());
			assertTrue(bring.getName().contains("Shipwright Test") && bring.getName().contains("250 coins")
				&& bring.getName().contains(place == Boat.CAPSIZED ? "capsized" : "lost"), bring.getName());
			// shipwrights switched off: no way to get it
			assertFalse(route(s, 3230, 3250, 3370, 3250, new Sailing.Options(99, null, List.of(boat(place, Boat.Focus.NONE, false)), 0, false, Sailing.DEFAULT_TURN_COST))
				.isFound());
			// a teleport focus can't summon it (the game's summon list leaves such boats out): only the shipwright
			for (Boat.Focus f : new Boat.Focus[]{Boat.Focus.TELEPORT, Boat.Focus.GREATER})
			{
				assertFalse(route(s, 3230, 3250, 3370, 3250, new Sailing.Options(99, null, List.of(boat(place, f, false)), 0, false, Sailing.DEFAULT_TURN_COST)).isFound(),
					f.name());
				assertEquals(fromWest() + Sailing.RECOVER_TICKS, route(s, 3230, 3250, 3370, 3250, options(boat(place, f, false))).getTicks());
			}
		}
		// no shipwright at any dock: never used
		assertFalse(route(sailing(DOCKS), 3230, 3250, 3370, 3250, options(boat(Boat.CAPSIZED, Boat.Focus.NONE, false))).isFound());
	}

	/** Maxed (every quest done) with {@code bottles} full boat bottles in the inventory and {@code banked} in the bank. */
	private static PlayerState withBottles(int bottles, int banked)
	{
		PlayerState.Builder b = new PlayerState.Builder("Bottles").members(true).allUnlocked(true);
		if (bottles > 0)
		{
			b.carry(Boat.BOTTLE_FULL, bottles);
		}
		if (banked > 0)
		{
			b.bank(Boat.BOTTLE_FULL, banked);
		}
		return b.build();
	}

	@Test
	void aBottledBoatOnlyWithItsBottleInTheInventory()
	{
		Sailing s = sailing(DOCKS); // no shipwrights at all: a bottle doesn't need one
		List<Boat> bottled = List.of(boat(Boat.BOTTLED, Boat.Focus.NONE, false));
		// no bottle, a bottle in the bank only, or no player: not used
		assertFalse(route(s, 3230, 3250, 3370, 3250, new Sailing.Options(99, withBottles(0, 0), bottled, 0, true, Sailing.DEFAULT_TURN_COST)).isFound());
		assertFalse(route(s, 3230, 3250, 3370, 3250, new Sailing.Options(99, withBottles(0, 1), bottled, 0, true, Sailing.DEFAULT_TURN_COST)).isFound());
		assertFalse(route(s, 3230, 3250, 3370, 3250, new Sailing.Options(99, null, bottled, 0, true, Sailing.DEFAULT_TURN_COST)).isFound());
		// carried: released at the West dock (like boarding, 10 ticks, no coins), boarded, sailed
		Route r = route(s, 3230, 3250, 3370, 3250, new Sailing.Options(99, withBottles(1, 0), bottled, 0, false, Sailing.DEFAULT_TURN_COST));
		assertEquals("WALK TRANSPORT TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		assertEquals(fromWest() + Sailing.RELEASE_TICKS, r.getTicks());
		Transport release = r.getSteps().get(1).getTransport();
		assertEquals("sail-release:1", release.getId());
		assertTrue(release.getName().startsWith("Release Boat 1: Raft from its bottle at West"), release.getName());
		assertFalse(release.getName().contains("coins"));
		assertEquals("sail-board:1", r.getSteps().get(2).getTransport().getId());
		// at any dock: from the East island back west, released at East
		Route back = route(s, 3370, 3250, 3230, 3250, new Sailing.Options(99, withBottles(1, 0), bottled, 0, false, Sailing.DEFAULT_TURN_COST));
		assertTrue(back.getSteps().get(1).getTransport().getName().contains("at East"), describe(back));
		// a focus doesn't change that (the game's summon list leaves bottled boats out)
		assertEquals(r.getTicks(), route(s, 3230, 3250, 3370, 3250,
			new Sailing.Options(99, withBottles(1, 0), List.of(boat(Boat.BOTTLED, Boat.Focus.GREATER, false)), 0, false, Sailing.DEFAULT_TURN_COST)).getTicks());
	}

	@Test
	void severalBottledBoatsNeedABottleEach()
	{
		// every full bottle is the same item: with fewer bottles than bottled boats, which boat a bottle holds is unknown
		Sailing s = sailing(DOCKS);
		Boat one = boat(Boat.BOTTLED, Boat.Focus.NONE, false);
		Boat two = new Boat(2, BoatType.SKIFF, 0, 0, 0, 0, false, false, Boat.Focus.NONE, Boat.BOTTLED);
		assertFalse(route(s, 3230, 3250, 3370, 3250, new Sailing.Options(99, withBottles(1, 0), List.of(one, two), 0, true, Sailing.DEFAULT_TURN_COST)).isFound());
		assertTrue(route(s, 3230, 3250, 3370, 3250, new Sailing.Options(99, withBottles(2, 0), List.of(one, two), 0, true, Sailing.DEFAULT_TURN_COST)).isFound());
		// boat 2 not for routes (Settings) but still bottled: one bottle could hold either, so boat 1 is not used either
		assertFalse(route(s, 3230, 3250, 3370, 3250,
			new Sailing.Options(99, withBottles(1, 0), List.of(one), 2, true, Sailing.DEFAULT_TURN_COST)).isFound());
		assertTrue(route(s, 3230, 3250, 3370, 3250,
			new Sailing.Options(99, withBottles(2, 0), List.of(one), 2, true, Sailing.DEFAULT_TURN_COST)).isFound());
		// the rule itself
		assertFalse(Sailing.bottlesInHand(withBottles(3, 0), 0));
		assertTrue(Sailing.bottlesInHand(withBottles(3, 0), 3));
		assertFalse(Sailing.bottlesInHand(withBottles(2, 5), 3));
		assertFalse(Sailing.bottlesInHand(null, 1));
		assertEquals(2, Sailing.bottled(List.of(one, two, boat(0, Boat.Focus.NONE, false))));
	}

	@Test
	void aStartAtSeaIsNotOnACapsizedOrBottledBoat()
	{
		// slot 1 capsized, slot 2 at West: the player at sea is on boat 2 (the first that can be anywhere)
		Boat second = new Boat(2, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, 0);
		Route r = route(sailing(DOCKS), 3300, 3280, 3300, 3220,
			new Sailing.Options(99, withBottles(1, 0), List.of(boat(Boat.CAPSIZED, Boat.Focus.NONE, false), second), 0, true, Sailing.DEFAULT_TURN_COST));
		assertEquals("SAIL", kinds(r));
		assertEquals("sail:2", r.getSteps().get(0).getTransport().getId());
		// only a bottled boat (bottle carried): it can't be the one at sea, and no dock can be walked to from the sea
		assertFalse(route(sailing(DOCKS), 3300, 3280, 3300, 3220,
			new Sailing.Options(99, withBottles(1, 0), List.of(boat(Boat.BOTTLED, Boat.Focus.NONE, false)), 0, true, Sailing.DEFAULT_TURN_COST)).isFound());
	}

	@Test
	void sailingStepIdsNameTheBoat()
	{
		Route r = route(sailing(DOCKS), 3230, 3250, 3370, 3250, options(new Boat(3, BoatType.RAFT, 0, 0, 0, 0, false, false,
			Boat.Focus.NONE, 0)));
		List<String> ids = new ArrayList<>();
		for (Route.Step st : r.getSteps())
		{
			if (st.getTransport() != null)
			{
				ids.add(st.getTransport().getId());
				assertEquals(3, Sailing.boatSlot(st.getTransport().getId()));
			}
		}
		assertEquals(List.of("sail-board:3", "sail:3", "sail-leave:3"), ids);
		assertEquals(2, Sailing.boatSlot("sail-release:2"));
		assertEquals(4, Sailing.boatSlot("sail-teleport:4"));
		assertEquals(-1, Sailing.boatSlot("sail-arrive:4"));
		assertEquals(-1, Sailing.boatSlot("sailor:1"));
		assertEquals(-1, Sailing.boatSlot("Fairy ring:1"));
		assertEquals(-1, Sailing.boatSlot(null));
	}

	@Test
	void aTeleportFocusBringsTheBoatToAnyDock()
	{
		Route r = route(sailing(DOCKS), 3230, 3250, 3370, 3250, options(boat(1, Boat.Focus.TELEPORT, false)));
		assertEquals(fromWest() + Sailing.TELEPORT_TICKS, r.getTicks());
	}

	@Test
	void aGreaterFocusAlsoTakesThePlayerToTheBoat()
	{
		// boat at East: teleport to it (5) and walk 28, no sailing at all
		Route r = route(sailing(DOCKS), 3230, 3250, 3370, 3250, options(boat(1, Boat.Focus.GREATER, false)));
		assertEquals("TELEPORT WALK", kinds(r));
		assertEquals(Sailing.TELEPORT_TICKS + 28, r.getTicks());
	}

	@Test
	void restrictedWaterAndDockLevels()
	{
		Sailing s = sailing(DOCKS);
		// to the north of the East island: without an inoculation station the boat cannot cross the fetid band,
		// so it docks at East and the player walks north
		Route plain = route(s, 3230, 3250, 3370, 3365, options(boat(0, Boat.Focus.NONE, false)));
		assertTrue(plain.getSteps().get(3).getTransport().getName().contains("at East"), kinds(plain));
		assertFalse(plain.getSteps().get(3).getTransport().getName().contains("East North"));
		Route inoculated = route(s, 3230, 3250, 3370, 3365, options(boat(0, Boat.Focus.NONE, true)));
		assertTrue(inoculated.getSteps().get(3).getTransport().getName().contains("East North"));
		assertTrue(inoculated.getTicks() < plain.getTicks());
		// East North needs Sailing 50
		Route low = route(s, 3230, 3250, 3370, 3365, new Sailing.Options(40, null, List.of(boat(0, Boat.Focus.NONE, true)), 0, true, Sailing.DEFAULT_TURN_COST));
		assertEquals(plain.getTicks(), low.getTicks());
	}

	@Test
	void aDockWhoseQuestIsNotFinishedIsSkipped()
	{
		// docks check their quests. East North needs "Swan Song" (and no level) here
		List<int[]> stand = new ArrayList<>();
		stand.add(new int[]{3342, 3360, 0});
		Sailing s = sailing(List.of(DOCKS.get(0), DOCKS.get(1),
			new Dock(2, "East North", 1, "Swan Song", Dock.Kind.GANGPLANK, 3341, 3360, 0, 1, 3337, 3360, 2, stand)));
		List<Boat> inoculated = List.of(boat(0, Boat.Focus.NONE, true));
		PlayerState none = new PlayerState.Builder("No quests").members(true).level(Skill.SAILING, 99).build();
		PlayerState started = new PlayerState.Builder("Started").members(true).level(Skill.SAILING, 99)
			.quest("Swan Song", QuestState.STARTED).build();
		PlayerState done = new PlayerState.Builder("Done").members(true).level(Skill.SAILING, 99)
			.quest("Swan Song", QuestState.DONE).build();
		// without the quest finished the boat docks at East and the player walks north, as without the right boat
		for (PlayerState p : List.of(none, started))
		{
			Route r = route(s, 3230, 3250, 3370, 3365, new Sailing.Options(99, p, inoculated, 0, true, Sailing.DEFAULT_TURN_COST));
			assertTrue(r.getSteps().get(3).getTransport().getName().contains("at East"), kinds(r));
			assertFalse(r.getSteps().get(3).getTransport().getName().contains("East North"), p.toString());
		}
		Route ok = route(s, 3230, 3250, 3370, 3365, new Sailing.Options(99, done, inoculated, 0, true, Sailing.DEFAULT_TURN_COST));
		assertTrue(ok.getSteps().get(3).getTransport().getName().contains("East North"), kinds(ok));
		// the maxed test player has every quest; no player given: quests count as done (hand-made tests)
		assertEquals(ok.getTicks(), route(s, 3230, 3250, 3370, 3365, new Sailing.Options(99, PlayerStateBuilder.maxed(), inoculated, 0, true, Sailing.DEFAULT_TURN_COST))
			.getTicks());
		assertEquals(ok.getTicks(), route(s, 3230, 3250, 3370, 3365, new Sailing.Options(99, null, inoculated, 0, true, Sailing.DEFAULT_TURN_COST)).getTicks());
	}

	@Test
	void aDestinationAtSeaEndsOnTheBoat()
	{
		Route r = route(sailing(DOCKS), 3230, 3250, 3300, 3280, options(boat(0, Boat.Focus.NONE, false)));
		assertEquals("WALK TRANSPORT SAIL", kinds(r));
		int[] t = r.getSteps().get(2).getTiles();
		assertEquals(Tiles.pack(3300, 3280, 0), t[t.length - 1]);
	}

	@Test
	void aStartAtSeaIsOnTheFirstBoatWhenNoneIsMarkedAtSea()
	{
		// the boat is set as moored at West, but the route starts in the water: the player is on it
		Route r = route(sailing(DOCKS), 3300, 3280, 3300, 3220, options(boat(0, Boat.Focus.NONE, false)));
		assertEquals("SAIL", kinds(r));
		// 60 tiles at 1.5 a tick; the sea graph's waypoints are 8 tiles apart, so the course may bend a little
		assertTrue(r.getTicks() >= 40 && r.getTicks() <= 43, "ticks " + r.getTicks());
		// a second boat moored at East still works from its dock; the one at sea is not at West any more
		Boat second = new Boat(2, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, 1);
		Route back = route(sailing(DOCKS), 3300, 3280, 3230, 3250,
			new Sailing.Options(99, null, List.of(boat(0, Boat.Focus.NONE, false), second), 0, true, Sailing.DEFAULT_TURN_COST));
		assertEquals("SAIL TRANSPORT WALK", kinds(back));
		assertTrue(back.getSteps().get(0).getTransport().getName().contains("Boat 1"));
		// no boats at all: nothing at sea can be reached
		assertFalse(route(sailing(DOCKS), 3300, 3280, 3300, 3220, new Sailing.Options(99, null, List.of(), 0, true, Sailing.DEFAULT_TURN_COST)).isFound());
	}

	@Test
	void startingOnTheBoatAtSea()
	{
		Route r = route(sailing(DOCKS), 3300, 3280, 3370, 3250, options(boat(Boat.AT_START, Boat.Focus.NONE, false)));
		assertEquals("SAIL TRANSPORT WALK", kinds(r));
		assertEquals(Tiles.pack(3300, 3280, 0), r.getSteps().get(0).getTiles()[0]);
		// at sea to at sea
		Route s = route(sailing(DOCKS), 3300, 3280, 3300, 3220, options(boat(Boat.AT_START, Boat.Focus.NONE, false)));
		assertEquals("SAIL", kinds(s));
		assertTrue(s.getTicks() >= 40 && s.getTicks() <= 43, "ticks " + s.getTicks()); // 60 tiles at 1.5 a tick, a little bent
	}

	@Test
	void theBoatLeftAtSeaIsLostAndRecoverable()
	{
		// when the route's first step is not sailing the boat the player is on, that boat is left at
		// sea: lost, so a shipwright may bring it back (as any lost boat), never a teleport focus
		List<Dock> withWright = new ArrayList<>(DOCKS);
		withWright.set(0, DOCKS.get(0).withShipwright("Shipwright Test"));
		Sailing s = sailing(withWright);
		// far north at sea; the destination is on the East island's south end; a teleport lands on the West dock
		Transport tele = Transport.teleport("tele", "Teleport to West", 3258, 3250, 0, 5, true);
		for (Boat.Focus f : Boat.Focus.values())
		{
			Sailing.Options o = options(boat(Boat.AT_START, f, false));
			Route r = SailingDiagnostics.route(s, new PathQuery(3300, 3390, 0, 3370, 3205, 0, List.of(tele), 2), o, WORLD.fast::find);
			assertTrue(r.isFound(), f.name());
			assertEquals("tele", r.getSteps().get(0).getTransport().getId(), f.name());
			Transport bring = null;
			for (Route.Step st : r.getSteps())
			{
				if (st.getTransport() != null && st.getTransport().getId().startsWith("sail-bring"))
				{
					bring = st.getTransport();
				}
			}
			assertTrue(bring != null, f.name() + ": " + kinds(r));
			assertEquals("sail-bring:1", bring.getId(), f.name());
			assertTrue(bring.getName().contains("Shipwright Test") && bring.getName().contains("it is lost"), bring.getName());
			// sailing it from where it is: the only other way
			Route stay = SailingDiagnostics.route(s, new PathQuery(3300, 3390, 0, 3370, 3205, 0, List.of(tele), 2),
				new Sailing.Options(99, null, List.of(boat(Boat.AT_START, f, false)), 0, false, Sailing.DEFAULT_TURN_COST), WORLD.fast::find);
			assertEquals("SAIL TRANSPORT WALK", kinds(stay), f.name());
			assertTrue(stay.getTicks() > r.getTicks(), stay.getTicks() + " vs " + r.getTicks());
			// (Settings "Never leave my boat at sea"): the route sails it first, though slower, and leaves
			// it only at a dock with a shipwright: East has none here, so no route (West's leads nowhere on foot)...
			Route kept = SailingDiagnostics.route(s, new PathQuery(3300, 3390, 0, 3370, 3205, 0, List.of(tele), 2), o.withKeepBoat(true), WORLD.fast::find);
			assertFalse(kept.isFound(), f.name());
			// ... with a shipwright at every dock: the same as sailing it from where it is
			List<Dock> both = new ArrayList<>(withWright);
			both.set(1, DOCKS.get(1).withShipwright("Shipwright East"));
			both.set(2, DOCKS.get(2).withShipwright("Shipwright East North"));
			Route keptEast = SailingDiagnostics.route(sailing(both), new PathQuery(3300, 3390, 0, 3370, 3205, 0, List.of(tele), 2), o.withKeepBoat(true),
				WORLD.fast::find);
			assertEquals(describe(stay), describe(keptEast), f.name());
		}
		// kept on the boat, the way on from the dock may still teleport (the boat is moored): to the West island's far
		// side, sail to a dock first; never a teleport straight from the boat
		Sailing.Options keep = options(boat(Boat.AT_START, Boat.Focus.NONE, false)).withKeepBoat(true);
		// the raft can't cross the fetid band to West, and East North (north of it) has no shipwright here: no route
		assertFalse(SailingDiagnostics.route(s, new PathQuery(3300, 3390, 0, 3205, 3250, 0, List.of(tele), 2), keep, WORLD.fast::find).isFound());
		// with a shipwright at East North: sail there, leave the boat, then the teleport
		List<Dock> north = new ArrayList<>(withWright);
		north.set(2, DOCKS.get(2).withShipwright("Shipwright East North"));
		Route west = SailingDiagnostics.route(sailing(north), new PathQuery(3300, 3390, 0, 3205, 3250, 0, List.of(tele), 2), keep, WORLD.fast::find);
		assertTrue(west.isFound());
		assertEquals(Route.Step.Kind.SAIL, west.getSteps().get(0).getKind(), kinds(west));
		assertTrue(west.getSteps().get(1).getTransport().getName().startsWith("Leave Boat 1: Raft at East North"), kinds(west));
		assertEquals("tele", west.getSteps().get(2).getTransport().getId(), kinds(west));
		Route free = SailingDiagnostics.route(s, new PathQuery(3300, 3390, 0, 3205, 3250, 0, List.of(tele), 2),
			options(boat(Boat.AT_START, Boat.Focus.NONE, false)), WORLD.fast::find);
		assertEquals("tele", free.getSteps().get(0).getTransport().getId(), kinds(free));
		assertTrue(west.getTicks() > free.getTicks());
		// not on a boat: the setting changes nothing
		assertEquals(describe(route(s, 3230, 3250, 3370, 3250, options(boat(0, Boat.Focus.NONE, false)))),
			describe(SailingDiagnostics.route(s, new PathQuery(3230, 3250, 0, 3370, 3250, 0, List.of(), 2),
				options(boat(0, Boat.Focus.NONE, false)).withKeepBoat(true), WORLD.fast::find)));
	}

	@Test
	void oceanOnlyStaysOnTheBoat()
	{
		// "Ocean only" keeps the player on the boat to a dock or open sea within 15 tiles
		Sailing s = sailing(DOCKS);
		Sailing.Options o = options(boat(Boat.AT_START, Boat.Focus.NONE, false));
		// a destination on the East island 5 tiles from its dock: sail there, no leaving step, no walk
		PathQuery east = new PathQuery(3300, 3280, 0, 3345, 3255, 0, List.of(), 2);
		assertTrue(s.oceanOnlyNear(east, o));
		Route r = s.oceanOnly(east, o);
		assertEquals("SAIL", kinds(r));
		assertTrue(r.getSteps().get(0).getTransport().getName().startsWith("Sail Boat 1: Raft to East"), r.getSteps().get(0).getTransport().getName());
		int[] t = r.getSteps().get(0).getTiles();
		assertTrue(Tiles.x(t[t.length - 1]) >= 3336, "by the East buoy");
		assertEquals("SAIL TRANSPORT WALK", kinds(route(s, 3300, 3280, 3345, 3255, o)), "the normal route leaves the boat");
		// a destination on open sea: the boat over it
		Route sea = s.oceanOnly(new PathQuery(3300, 3280, 0, 3300, 3220, 0, List.of(), 2), o);
		assertEquals("SAIL", kinds(sea));
		int[] st = sea.getSteps().get(0).getTiles();
		assertEquals(Tiles.pack(3300, 3220, 0), st[st.length - 1]);
		// no dock within 15 tiles (East is 20 away): the open sea nearest the destination (the shore, 11 tiles away)
		Route shore = s.oceanOnly(new PathQuery(3300, 3280, 0, 3350, 3230, 0, List.of(), 2), o);
		assertEquals("SAIL", kinds(shore));
		int[] sh = shore.getSteps().get(0).getTiles();
		int end = sh[sh.length - 1];
		assertTrue(isSea(Tiles.x(end), Tiles.y(end)) && Math.max(Math.abs(Tiles.x(end) - 3350), Math.abs(Tiles.y(end) - 3230)) <= 15,
			Tiles.x(end) + "," + Tiles.y(end));
		assertTrue(shore.getSteps().get(0).getTransport().getName().endsWith("open sea near the destination"));
		// far inland (no dock or sea within 15 tiles): not tried
		PathQuery inland = new PathQuery(3300, 3280, 0, 3380, 3250, 0, List.of(), 2);
		assertFalse(s.oceanOnlyNear(inland, o));
		assertFalse(s.oceanOnly(inland, o).isFound());
		// near the sea, but the raft can't cross the fetid band from the north: no route
		PathQuery north = new PathQuery(3300, 3390, 0, 3345, 3255, 0, List.of(), 2);
		assertTrue(s.oceanOnlyNear(north, o));
		assertFalse(s.oceanOnly(north, o).isFound());
		// not on a boat: nothing
		PathQuery onLand = new PathQuery(3230, 3250, 0, 3345, 3255, 0, List.of(), 2);
		assertFalse(s.oceanOnlyNear(onLand, options(boat(0, Boat.Focus.NONE, false))));
		assertFalse(s.oceanOnly(onLand, options(boat(0, Boat.Focus.NONE, false))).isFound());
	}

	/** Steps as text: kind, ticks and tiles of each (for comparing two routes exactly). */
	private static String describe(Route r)
	{
		if (!r.isFound())
		{
			return "no route";
		}
		StringBuilder sb = new StringBuilder().append(r.getTicks());
		for (Route.Step st : r.getSteps())
		{
			sb.append(" | ").append(st.getKind()).append(' ').append(st.getTicks()).append(' ')
				.append(java.util.Arrays.toString(st.getTiles()));
			if (st.getTransport() != null)
			{
				sb.append(' ').append(st.getTransport().getName());
			}
		}
		return sb.toString();
	}

	@Test
	void theWorkSavedChangesNoRoute()
	{
		// with the Fast search itself (dock searches ruled out by lower bounds, docks the destination can't be
		// reached from left out, a shorter sea search) against the plain form (every dock searched)
		java.util.Random rnd = new java.util.Random(11);
		List<Dock> wright = new ArrayList<>(DOCKS);
		wright.set(1, DOCKS.get(1).withShipwright("Shipwright Test"));
		// routes walk to a shipwright whose place is known (here the one at West)
		wright.set(0, DOCKS.get(0).withShipwright("Shipwright West", 3245, 3265, 0));
		Sailing s = sailing(wright);
		int sailed = 0;
		for (int k = 0; k < 120; k++)
		{
			int sx = X0 + rnd.nextInt(SIZE);
			int sy = Y0 + rnd.nextInt(SIZE);
			int dx = X0 + rnd.nextInt(SIZE);
			int dy = Y0 + rnd.nextInt(SIZE);
			int dp = k % 10 == 9 ? 1 : 0; // now and then a destination nobody can reach
			Boat.Focus focus = Boat.Focus.values()[k % 3];
			// now and then a capsized boat, or a bottled one with its bottle carried
			int place = k % 4 == 3 ? Boat.AT_START : k % 11 == 4 ? Boat.CAPSIZED : k % 11 == 6 ? Boat.BOTTLED : k % 3;
			Boat b = new Boat(1, BoatType.values()[k % 3], k % 7, 1, 0, 0, k % 2 == 0, false, focus, place);
			Sailing.Options o = new Sailing.Options(k % 5 == 4 ? 40 : 99, place == Boat.BOTTLED ? withBottles(1, 0) : null, List.of(b), 0,
				k % 6 != 5, Sailing.DEFAULT_TURN_COST);
			PathQuery q = new PathQuery(sx, sy, 0, dx, dy, dp, List.of(), 2);
			Route plain = SailingDiagnostics.route(s, q, o, WORLD.fast::find);
			int[] work = SailingDiagnostics.lastDockSearches(s);
			Route lean = s.route(q, o, WORLD.fast);
			assertEquals(describe(plain), describe(lean), "query " + k);
			sailed += describe(lean).contains("SAIL") ? 1 : 0;
			assertTrue(SailingDiagnostics.lastDockSearches(s)[0] <= work[0]);
		}
		assertTrue(sailed > 20, "routes that sail: " + sailed);
	}

	/** Teleports and a ship of its own on the two islands, for the routes below. */
	private static List<Transport> islandTransports(java.util.Random rnd)
	{
		List<Transport> out = new ArrayList<>();
		out.add(Transport.teleport("tele-west", "Teleport west", 3205 + rnd.nextInt(50), 3205 + rnd.nextInt(180), 0, 5 + rnd.nextInt(20), true));
		out.add(Transport.teleport("tele-east", "Teleport east", 3345 + rnd.nextInt(40), 3205 + rnd.nextInt(180), 0, 5 + rnd.nextInt(20), true));
		out.add(Transport.between("ship", "Ship", 3240, 3380, 0, 3360, 3205, 0, 20 + rnd.nextInt(40), true, true));
		return out;
	}

	/** Two or three boats at docks, brought by a focus or a shipwright, or lost. */
	private static List<Boat> someBoats(int k)
	{
		List<Boat> out = new ArrayList<>();
		out.add(new Boat(1, BoatType.SLOOP, k % 7, 2, 2, 2, true, false, Boat.Focus.values()[k % 3], k % 2));
		out.add(new Boat(2, BoatType.RAFT, 3, 3, 0, 3, true, false, Boat.Focus.NONE, k % 5 == 0 ? Boat.LOST : 1));
		if (k % 3 == 0)
		{
			out.add(new Boat(3, BoatType.SKIFF, 1, 1, 1, 1, false, false, Boat.Focus.GREATER, 0));
		}
		return out;
	}

	/**
	 * The walk on from a dock without teleports, and each boat's sea
	 * searched again only when its start points moved, change no route, also with teleports and several boats.
	 */
	@Test
	void teleportsAndSeveralBoatsChangeNoRoute()
	{
		java.util.Random rnd = new java.util.Random(20261018);
		List<Dock> wright = new ArrayList<>(DOCKS);
		wright.set(1, DOCKS.get(1).withShipwright("Shipwright Test"));
		wright.set(0, DOCKS.get(0).withShipwright("Shipwright West", 3245, 3265, 0));
		Sailing s = sailing(wright);
		int sailed = 0;
		for (int k = 0; k < 120; k++)
		{
			PathQuery q = new PathQuery(X0 + rnd.nextInt(SIZE), Y0 + rnd.nextInt(SIZE), 0, X0 + rnd.nextInt(SIZE), Y0 + rnd.nextInt(SIZE), 0,
				islandTransports(rnd), 2);
			Sailing.Options o = new Sailing.Options(99, null, someBoats(k), 0, k % 4 != 3, Sailing.DEFAULT_TURN_COST);
			Route plain = SailingDiagnostics.route(s, q, o, WORLD.fast::find);
			Route lean = s.route(q, o, WORLD.fast);
			assertEquals(describe(plain), describe(lean), "query " + k);
			sailed += describe(lean).contains("SAIL") ? 1 : 0;
		}
		assertTrue(sailed > 15, "routes that sail: " + sailed);
	}

	/**
	 * The bank start weighed first and sailing then only looking for what beats it gives the same
	 * route as sailing first and then the bank start ({@link BankFirst#withSailing}), also when the destination needs
	 * something from the bank; but for a bank start that sails, or uses a greater teleport focus,
	 * found when strictly quicker and checked against every bank by brute force.
	 */
	@Test
	void theBankRouteFirstChangesNoRoute()
	{
		java.util.Random rnd = new java.util.Random(181018);
		Sailing s = sailing(DOCKS);
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{Tiles.pack(3230, 3300, 0), Tiles.pack(3250, 3215, 0), Tiles.pack(3370, 3330, 0)},
			new String[]{"West bank", "South-west bank", "East bank"});
		int[] counts = new int[3];
		for (int k = 0; k < 120; k++)
		{
			int dx = X0 + rnd.nextInt(SIZE);
			int dy = Y0 + rnd.nextInt(SIZE);
			// one slow teleport (boats win more often), and the bank's teleport
			List<Transport> carried = List.of(Transport.teleport("tele-west", "Teleport west", 3205 + rnd.nextInt(50), 3205 + rnd.nextInt(180), 0,
				30 + rnd.nextInt(40), true));
			PathQuery q = new PathQuery(X0 + rnd.nextInt(SIZE), Y0 + rnd.nextInt(SIZE), 0, dx, dy, 0, carried, 2);
			Transport fromBank = Transport.teleport("bank-tele", "Tablet", 3345 + rnd.nextInt(40), 3205 + rnd.nextInt(180), 0, 5, true);
			weighAgainstBrute(s, k, q, fromBank, banks, 5 + rnd.nextInt(30), k % 5 == 0, new Sailing.Options(99, null, someBoats(k), 0, true, Sailing.DEFAULT_TURN_COST), counts);
		}
		assertTrue(counts[0] > 10, "routes that sail: " + counts[0]);
		assertTrue(counts[1] > 10, "routes that start at a bank: " + counts[1]);
	}

	/**
	 * (Wintumber Island) The bank start whose route on sails, on routes where it often wins (a bank by
	 * the start, its teleport by a dock), against every bank by brute force.
	 */
	@Test
	void aBankStartThatSailsIsTheQuickestOfEveryBank()
	{
		java.util.Random rnd = new java.util.Random(20261018);
		Sailing s = sailing(DOCKS);
		int[][] lands = {{3257, 3250}, {3342, 3250}, {3342, 3360}};
		int[] counts = new int[3];
		for (int k = 0; k < 120; k++)
		{
			boolean west = rnd.nextBoolean();
			int sx = west ? 3205 + rnd.nextInt(50) : 3345 + rnd.nextInt(45);
			int sy = Y0 + rnd.nextInt(SIZE);
			// banks near the start and one further off, the destination on the other island
			BankFirst.Banks banks = new BankFirst.Banks(new int[]{Tiles.pack(sx, Math.min(Y0 + SIZE - 1, sy + 3 + rnd.nextInt(10)), 0),
				Tiles.pack(sx, Math.max(Y0, sy - 20 - rnd.nextInt(40)), 0), Tiles.pack(west ? 3240 : 3380, 3200 + rnd.nextInt(190), 0)},
				new String[]{"Near bank", "Further bank", "Far bank"});
			int dx = west ? 3345 + rnd.nextInt(45) : 3205 + rnd.nextInt(50);
			PathQuery q = new PathQuery(sx, sy, 0, dx, Y0 + rnd.nextInt(SIZE), 0,
				k % 3 == 0 ? List.of(Transport.teleport("tele-slow", "Slow teleport", 3205 + rnd.nextInt(50), 3205 + rnd.nextInt(180), 0,
					40 + rnd.nextInt(60), true)) : List.of(), 2);
			int[] land = lands[rnd.nextInt(lands.length)];
			Transport fromBank = Transport.teleport("bank-tele", "Tablet", land[0] + rnd.nextInt(5) * (land[0] < 3300 ? -1 : 1), land[1] - 2 + rnd.nextInt(5),
				0, 4 + rnd.nextInt(20), true);
			weighAgainstBrute(s, k, q, fromBank, banks, 5 + rnd.nextInt(60), k % 7 == 0, new Sailing.Options(k % 4 == 1 ? 40 : 99, null, someBoats(k), 0, k % 2 == 0, Sailing.DEFAULT_TURN_COST),
				counts);
		}
		assertTrue(counts[2] > 10, "routes that start at a bank and sail: " + counts[2]);
	}

	/**
	 * {@link BankFirst#withSailing} on one route against the old order (sailing, then the bank start against it) and the
	 * brute force bank start ({@link #bankThenSailBrute}): its ticks are the quickest of the old route and the brute
	 * force's (a tie keeps the old route; when the destination needs the bank and the old route doesn't start at one,
	 * the brute force's when found); a route other than the old one starts at a bank and sails or uses a greater teleport
	 * focus. Counts: the old route sails, the old route starts at a bank, the new route starts at a bank and sails.
	 */
	private static void weighAgainstBrute(Sailing s, int k, PathQuery q, Transport fromBank, BankFirst.Banks banks, int ticks, boolean need,
		Sailing.Options o, int[] counts)
	{
		com.bestpath.core.req.BankItem tablet = new com.bestpath.core.req.BankItem("Tablet", new int[]{8008}, 1);
		List<Transport> withBank = new ArrayList<>(q.getTransports());
		withBank.add(fromBank);
		java.util.function.Function<Transport, List<com.bestpath.core.req.BankItem>> itemsOf = t -> t == fromBank ? List.of(tablet) : List.of();
		List<com.bestpath.core.req.BankItem> needs = need ? List.of(tablet) : null;
		int[] stop = {q.getDestX(), q.getDestY(), 0, 1};
		// the old order: sailing, then the bank start against the sailing route
		Route r = s.route(q, o, WORLD.fast, false);
		BankFirst.Plan p = BankFirst.tryBank(WORLD.fast, q, r, stop, withBank, banks, ticks, needs, itemsOf);
		Route old = p != null ? p.getRoute() : r;
		boolean oldBanked = p != null && p.isBanked();
		BankFirst.Weighed w = BankFirst.withSailing(WORLD.fast, s, o, q, false, stop, withBank, banks, ticks, needs, itemsOf);
		int brute = bankThenSailBrute(s, o, q, withBank, banks, ticks);
		int was = old.isFound() ? old.getTicks() : -1;
		int want = needs != null && !oldBanked ? (brute >= 0 ? brute : was) : brute >= 0 && (was < 0 || brute < was) ? brute : was;
		Route got = w.getRoute();
		assertEquals(want, got.isFound() ? got.getTicks() : -1, "query " + k + ": " + describe(got) + " / old " + describe(old));
		if (describe(old).equals(describe(got)))
		{
			assertEquals(oldBanked, w.getPlan() != null && w.getPlan().isBanked(), "query " + k + ": banked");
		}
		else
		{
			assertTrue(w.getPlan().isBanked() && kinds(got).contains("BANK"), "query " + k + ": " + describe(got));
			assertTrue(kinds(got).contains("SAIL") || describe(got).contains("Greater teleport focus"), "query " + k + ": " + describe(got));
			assertStepsAddUp(got);
			counts[2] += kinds(got).contains("SAIL") ? 1 : 0;
		}
		counts[0] += kinds(old).contains("SAIL") ? 1 : 0;
		counts[1] += oldBanked ? 1 : 0;
	}

	/**
	 * The quickest route that starts at a bank, by brute force (no lower bounds): per bank tile, the route there with what
	 * is carried, the stop, and the best route on from it with the bank's items, sailing too ({@link Sailing#route}). -1:
	 * none. A teleport from another bank than the nearest is never quicker than from the nearest, so this is the rule's
	 * least.
	 */
	private static int bankThenSailBrute(Sailing s, Sailing.Options o, PathQuery q, List<Transport> withBank, BankFirst.Banks banks, int stop)
	{
		int best = -1;
		// what is carried, and the greater teleport focus teleports (as the walk has them)
		List<Transport> carried = new ArrayList<>(q.getTransports());
		carried.addAll(s.focusTeleports(q, o));
		for (int i = 0; i < BankFirstDiagnostics.tileCount(banks); i++)
		{
			int t = banks.tiles()[i];
			Route to = WORLD.fast.find(new PathQuery(q.getStartX(), q.getStartY(), 0, Tiles.x(t), Tiles.y(t), 0, carried, 2));
			if (!to.isFound())
			{
				continue;
			}
			Route on = s.route(new PathQuery(Tiles.x(t), Tiles.y(t), 0, q.getDestX(), q.getDestY(), 0, withBank, 2), o, WORLD.fast, false);
			if (on.isFound() && (best < 0 || to.getTicks() + stop + on.getTicks() < best))
			{
				best = to.getTicks() + stop + on.getTicks();
			}
		}
		return best;
	}

	private static void assertStepsAddUp(Route r)
	{
		int total = 0;
		for (Route.Step st : r.getSteps())
		{
			total += st.getTicks();
		}
		assertEquals(r.getTicks(), total, "steps add up");
	}

	/**
	 * (Wintumber Island, reachable only by boat) The boat is moored far away, a teleport from the
	 * bank lands by its dock. "Bank, take out the tablet, teleport, board, sail" beats walking to the boat and sailing.
	 */
	@Test
	void aBankThenATeleportThenSailingBeatsSailingAlone()
	{
		Sailing s = sailing(DOCKS);
		// the player on the East island far south of the boat (moored at East North); the destination on the West island
		PathQuery q = new PathQuery(3370, 3210, 0, 3230, 3250, 0, List.of(), 2);
		Sailing.Options o = new Sailing.Options(99, null, List.of(boat(2, Boat.Focus.NONE, true)), 0, false, Sailing.DEFAULT_TURN_COST); // inoculated: through the fetid band
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{Tiles.pack(3375, 3215, 0)}, new String[]{"East bank"});
		com.bestpath.core.req.BankItem tablet = new com.bestpath.core.req.BankItem("Tablet", new int[]{8008}, 1);
		Transport fromBank = Transport.teleport("bank-tele", "Tablet to East North", 3343, 3360, 0, 20, true);
		List<Transport> withBank = List.of(fromBank);
		java.util.function.Function<Transport, List<com.bestpath.core.req.BankItem>> itemsOf = t -> t == fromBank ? List.of(tablet) : List.of();
		int[] stop = {3230, 3250, 0, 1};
		Route alone = s.route(q, o, WORLD.fast, false);
		assertTrue(alone.isFound() && !kinds(alone).contains("BANK"), describe(alone));
		BankFirst.Weighed w = BankFirst.withSailing(WORLD.fast, s, o, q, false, stop, withBank, banks, 50, null, itemsOf);
		Route r = w.getRoute();
		assertTrue(w.getPlan().isBanked(), describe(r));
		assertEquals("WALK BANK TELEPORT WALK TRANSPORT SAIL TRANSPORT WALK", kinds(r));
		assertEquals(List.of(tablet), r.getSteps().get(1).getTakeOutItems());
		assertEquals(fromBank, r.getSteps().get(2).getTransport());
		assertEquals(20, r.getSteps().get(2).getTicks()); // the teleport's own ticks, not its head start
		assertEquals(5 + 50, r.getSteps().get(0).getTicks() + r.getSteps().get(1).getTicks()); // 5 tiles to the bank, the stop
		assertStepsAddUp(r);
		assertTrue(r.getTicks() < alone.getTicks(), describe(r) + " vs " + describe(alone));
		assertEquals(bankThenSailBrute(s, o, q, withBank, banks, 50), r.getTicks());
		// a bank stop so long that walking to the boat is quicker: the route without the bank, as before
		BankFirst.Weighed slow = BankFirst.withSailing(WORLD.fast, s, o, q, false, stop, withBank, banks, alone.getTicks(), null, itemsOf);
		assertFalse(slow.getPlan().isBanked());
		assertEquals(describe(alone), describe(slow.getRoute()));
	}

	/** West with a shipwright standing at (3245, 3265) on the West island. */
	private static List<Dock> westWright(int x, int y)
	{
		List<Dock> out = new ArrayList<>(DOCKS);
		out.set(0, DOCKS.get(0).withShipwright("Shipwright Test", x, y, 0));
		return out;
	}

	@Test
	void theShipwrightStandsNextToItsPlaceNearestTheDock()
	{
		// the tiles W, E, S, N of the Wiki tile that can be walked on; the nearest the dock's land tile (3257, 3250)
		Sailing s = sailing(westWright(3245, 3265));
		assertArrayEquals(new int[]{3245, 3264, 0}, SailingDiagnostics.shipwrightStand(s, 0)); // south: 14 tiles from the dock (east: 15)
		assertEquals(null, SailingDiagnostics.shipwrightStand(s, 1)); // no shipwright at East
		// a place in the sea: the land tile next to it
		assertArrayEquals(new int[]{3259, 3250, 0}, SailingDiagnostics.shipwrightStand(sailing(westWright(3260, 3250)), 0));
		// far out at sea (no tile next to it on land): not known, the boat is brought at the dock as before
		assertEquals(null, SailingDiagnostics.shipwrightStand(sailing(westWright(3300, 3250)), 0));
		// a shipwright without a known place (old sea files): none
		List<Dock> nameOnly = new ArrayList<>(DOCKS);
		nameOnly.set(0, DOCKS.get(0).withShipwright("Shipwright Test"));
		assertEquals(null, SailingDiagnostics.shipwrightStand(sailing(nameOnly), 0));
	}

	@Test
	void theRouteWalksToTheShipwrightThenToTheDock()
	{
		// when a shipwright is used the player is pathed to them. The boat is at East
		Sailing s = sailing(westWright(3245, 3265));
		Route r = route(s, 3230, 3250, 3370, 3250, options(boat(1, Boat.Focus.NONE, false)));
		assertEquals("WALK TRANSPORT WALK TRANSPORT SAIL TRANSPORT WALK", kinds(r), describe(r));
		Route.Step toWright = r.getSteps().get(0);
		Route.Step bring = r.getSteps().get(1);
		Route.Step toDock = r.getSteps().get(2);
		int stand = Tiles.pack(3245, 3264, 0);
		// the walk ends at the shipwright; Recover-boat there (10 ticks); the walk on ends at the dock; boarded there
		int[] t0 = toWright.getTiles();
		assertEquals(stand, t0[t0.length - 1]);
		assertEquals(15, toWright.getTicks()); // (3230, 3250) to (3245, 3264)
		assertEquals("sail-bring:1", bring.getTransport().getId());
		assertArrayEquals(new int[]{stand, stand}, bring.getTiles());
		assertEquals(Sailing.RECOVER_TICKS, bring.getTicks());
		assertTrue(bring.getTransport().getName().startsWith("Shipwright Test brings Boat 1: Raft to West (Recover-boat, 250 coins"),
			bring.getTransport().getName());
		int[] t2 = toDock.getTiles();
		assertEquals(stand, t2[0]);
		assertEquals(Tiles.pack(3257, 3250, 0), t2[t2.length - 1]);
		assertEquals(14, toDock.getTicks());
		assertEquals("sail-board:1", r.getSteps().get(3).getTransport().getId());
		// the costs: the real walks plus Recover-boat's 10 ticks (the straight walk to the dock is 27)
		assertEquals(fromWest() - 27 + 15 + Sailing.RECOVER_TICKS + 14, r.getTicks());
		// a capsized boat the same way
		Route wreck = route(s, 3230, 3250, 3370, 3250, options(boat(Boat.CAPSIZED, Boat.Focus.NONE, false)));
		assertEquals(r.getTicks(), wreck.getTicks());
		assertEquals("WALK TRANSPORT WALK TRANSPORT SAIL TRANSPORT WALK", kinds(wreck));
		// starting at the shipwright: no first walk
		Route here = route(s, 3245, 3264, 3370, 3250, options(boat(1, Boat.Focus.NONE, false)));
		assertEquals("TRANSPORT WALK TRANSPORT SAIL TRANSPORT WALK", kinds(here), describe(here));
		// the shipwright's place not usable: brought at the dock, as before
		Route old = route(sailing(westWright(3300, 3250)), 3230, 3250, 3370, 3250, options(boat(1, Boat.Focus.NONE, false)));
		assertEquals("WALK TRANSPORT TRANSPORT SAIL TRANSPORT WALK", kinds(old));
		assertEquals(fromWest() + Sailing.RECOVER_TICKS, old.getTicks());
		// the boat at West already: no shipwright, no detour
		assertEquals(fromWest(), route(s, 3230, 3250, 3370, 3250, options(boat(0, Boat.Focus.NONE, false))).getTicks());
		// a teleport focus brings it to West (5 ticks) without the detour: cheaper, so used instead
		Route focus = route(s, 3230, 3250, 3370, 3250, options(boat(1, Boat.Focus.TELEPORT, false)));
		assertEquals(fromWest() + Sailing.TELEPORT_TICKS, focus.getTicks());
	}
}
