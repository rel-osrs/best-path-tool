package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.ClueData;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.SailingSetup;
import com.bestpath.core.data.SeaBits;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.WallBits;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.SeaGrid;
import com.bestpath.core.sea.SeaGridBuilder;
import com.bestpath.core.sea.SeaMap;
import com.bestpath.core.sea.SeaPieces;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Sailing routes through the plugin's engine: the player's boats on the snapshot, the Settings
 * choices, and the steps the sidebar and overlays get. World: two islands with sea between (x 3260-3339),
 * docks West (3258, 3250) and East (3341, 3250); a rock far out at sea at 3300,3300 and one by the east shore at
 * 3339,3290.
 */
public class SailingRouteTest
{
	private static boolean isSea(int x, int y)
	{
		return x >= 3260 && x <= 3339 && y >= 3200 && y < 3392;
	}

	private static Dock dock(int id, String name, int lx, int bx, int standX)
	{
		List<int[]> stand = new ArrayList<>();
		stand.add(new int[]{standX, 3250, 0});
		return new Dock(id, name, 1, null, Dock.Kind.GANGPLANK, lx, 3250, 0, 1, bx, 3250, 2, stand);
	}

	private static final List<Dock> DOCKS = List.of(dock(0, "West", 3258, 3261, 3257), dock(1, "East", 3341, 3337, 3342));

	private static CoreRouteEngine engine()
	{
		return engine(DOCKS);
	}

	private static CoreRouteEngine engine(List<Dock> docks)
	{
		MovementRules.Flags walk = (x, y) -> x < 3200 || y < 3200 || x >= 3392 || y >= 3392 || isSea(x, y) ? CollisionFlags.BLOCKED : 0;
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
		SeaMap.Builder b = new SeaMap.Builder();
		for (int x = 3260; x <= 3339; x++)
		{
			for (int y = 3200; y < 3392; y++)
			{
				if (!rock(x, y))
				{
					b.set(x, y, 131);
				}
			}
		}
		SeaMap sea = b.build();
		MovementRules.Flags f = (x, y) -> (x == 3261 || x == 3262 || x == 3337 || x == 3338) && (y == 3250 || y == 3251) || rock(x, y)
			? CollisionFlags.OBJECT : 0;
		SeaGrid grid = SeaGridBuilder.build(sea, SeaPieces.build(sea, f), f);
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(), Tables.empty(),
			new FreeLand.Builder().build(), ClueData.EMPTY, new SeaBits.Data(grid, docks, List.of()));
		return new CoreRouteEngine(new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET));
	}

	private static final Boat RAFT = new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, 0);

	/** Rocks in the water (not ocean, blocked): far out at 3300,3300; by the east shore at 3339,3290. */
	private static boolean rock(int x, int y)
	{
		return x == 3300 && y == 3300 || x == 3339 && y == 3290;
	}

	/**
	 * A boat ends only at a dock or on open sea. A rock far out at sea: on the boat on the nearest open
	 * water, the last card saying so; a rock by the coast: on the shore tile in sight of it,
	 * through the dock, never on the boat beside it.
	 */
	@Test
	public void boatsEndOnlyAtDocksOrOnOpenWater()
	{
		PlayerSnapshot me = new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(), List.of(RAFT));
		CoreRouteEngine e = engine();
		RouteResult far = e.route(new RouteJob(1, new RouteRequest(3300, 3300, 0), me, TransportSettings.defaults(), Set.of()))
			.explained();
		assertEquals(RouteStatus.READY, far.getStatus());
		RouteStep last = far.getSteps().get(far.getSteps().size() - 1);
		assertEquals(RouteStep.Kind.SAIL, last.getKind());
		assertEquals(Tiles.pack(3299, 3300, 0), last.getEndTile());
		assertTrue(last.getNotes().toString(), last.getNotes().contains("The spot itself is blocked: this stops 1 tile away, on open water."));
		RouteResult shore = e.route(new RouteJob(2, new RouteRequest(3339, 3290, 0), me, TransportSettings.defaults(), Set.of()))
			.explained();
		assertEquals(RouteStatus.READY, shore.getStatus());
		StringBuilder kinds = new StringBuilder();
		for (RouteStep s : shore.getSteps())
		{
			kinds.append(kinds.length() == 0 ? "" : " ").append(s.getKind());
		}
		assertEquals("WALK BOAT SAIL BOAT WALK", kinds.toString());
		RouteStep walk = shore.getSteps().get(shore.getSteps().size() - 1);
		assertEquals(Tiles.pack(3340, 3290, 0), walk.getEndTile());
		assertTrue(walk.getNotes().toString(), walk.getNotes().contains("The destination itself can't be reached: this stops 1 tile away, in sight of it."));
	}

	private static RouteResult route(CoreRouteEngine e, PlayerSnapshot me, TransportSettings settings)
	{
		return e.route(new RouteJob(1, new RouteRequest(3370, 3250, 0), me, settings, Set.of())).explained();
	}

	@Test
	public void aSailingRouteHasBoardSailAndLeaveSteps()
	{
		PlayerSnapshot me = new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(), List.of(RAFT));
		RouteResult r = route(engine(), me, TransportSettings.defaults());
		assertEquals(RouteStatus.READY, r.getStatus());
		StringBuilder kinds = new StringBuilder();
		for (RouteStep s : r.getSteps())
		{
			kinds.append(kinds.length() == 0 ? "" : " ").append(s.getKind());
		}
		assertEquals("WALK BOAT SAIL BOAT WALK", kinds.toString());
		RouteStep board = r.getSteps().get(1);
		RouteStep sail = r.getSteps().get(2);
		RouteStep leave = r.getSteps().get(3);
		// the game's click ("Board"), with the player's boat (no name read: "Boat 1: Raft")
		assertEquals("Board Boat 1: Raft", board.getTitle());
		assertEquals("Leave Boat 1: Raft at East", leave.getTitle());
		assertNull("sailing steps can't be skipped or blacklisted", board.getTransportId());
		assertEquals(Tiles.pack(3257, 3250, 0), board.getMarkerTile());
		assertEquals("Sail Boat 1: Raft to East", sail.getTitle());
		assertNull(sail.getTransportId());
		assertTrue("the boat's course", sail.tileCount() > 60);
		for (int i = 0; i < sail.tileCount(); i++)
		{
			assertTrue(isSea(Tiles.x(sail.tile(i)), Tiles.y(sail.tile(i))) && Tiles.plane(sail.tile(i)) == 0);
		}
		// straight legs in the boat's exact 16 headings (22.5 degrees apart), corner to corner (drawn
		// as arrows); the corners exactly, the first and last on the course's first and last tiles
		assertTrue(sail.cornerCount() >= 2);
		assertEquals(0, sail.corner(0));
		assertEquals(sail.tileCount() - 1, sail.corner(sail.cornerCount() - 1));
		assertEquals(Tiles.x(sail.tile(0)), sail.cornerX(0), 0);
		assertEquals(Tiles.y(sail.tileCount() - 1 >= 0 ? sail.tile(sail.tileCount() - 1) : 0),
			sail.cornerY(sail.cornerCount() - 1), 0);
		for (int c = 1; c < sail.cornerCount(); c++)
		{
			double dx = sail.cornerX(c) - sail.cornerX(c - 1);
			double dy = sail.cornerY(c) - sail.cornerY(c - 1);
			double compass = Math.toDegrees(Math.atan2(dx, dy));
			assertEquals("leg " + c + " at " + compass + " degrees", 0, Math.IEEEremainder(compass, 22.5), 1e-6);
		}
		assertEquals(Tiles.pack(3342, 3250, 0), leave.getEndTile());
		assertEquals(10, leave.getTicks());
		// the cards show the boat's picture: the steps know the boat's kind; walks don't
		assertEquals(BoatType.RAFT, board.getInfo().getBoat());
		assertEquals(BoatType.RAFT, sail.getInfo().getBoat());
		assertEquals(BoatType.RAFT, leave.getInfo().getBoat());
		assertNull(r.getSteps().get(0).getInfo().getBoat());
	}

	@Test
	public void aBottledBoatWithItsBottleIsReleasedAtTheDock()
	{
		// only with its bottle in the inventory; released at any dock (10 ticks, no coins)
		Boat skiff = new Boat(2, BoatType.SKIFF, 0, 0, 0, 0, false, false, Boat.Focus.NONE, Boat.BOTTLED);
		PlayerState none = new PlayerState.Builder("No bottle").members(true).allUnlocked(true)
			.level(com.bestpath.core.req.Skill.SAILING, 99).build();
		PlayerState carrying = new PlayerState.Builder("Bottle").members(true).allUnlocked(true)
			.level(com.bestpath.core.req.Skill.SAILING, 99).carry(Boat.BOTTLE_FULL, 1).build();
		CoreRouteEngine e = engine();
		assertEquals(RouteStatus.NO_ROUTE, route(e, new PlayerSnapshot(3230, 3250, 0, none, List.of(skiff)),
			TransportSettings.defaults()).getStatus());
		RouteResult r = route(e, new PlayerSnapshot(3230, 3250, 0, carrying, List.of(skiff)), TransportSettings.defaults());
		assertEquals(RouteStatus.READY, r.getStatus());
		RouteStep release = r.getSteps().get(1);
		assertEquals(RouteStep.Kind.BOAT, release.getKind());
		assertEquals("Unbottle-boat -> Boat 2: Skiff", release.getTitle());
		assertEquals(10, release.getTicks());
		assertNull(release.getTransportId());
		assertEquals(BoatType.SKIFF, release.getInfo().getBoat());
		assertEquals("Board Boat 2: Skiff", r.getSteps().get(2).getTitle());
	}

	/** "No route" by boat: what the boat needs (sailing items included), with its marks. */
	@Test
	public void noRouteSaysWhatTheBoatNeeds()
	{
		Boat skiff = new Boat(2, BoatType.SKIFF, 0, 0, 0, 0, false, false, Boat.Focus.NONE, Boat.BOTTLED);
		PlayerState none = new PlayerState.Builder("No bottle").members(true).allUnlocked(true)
			.level(com.bestpath.core.req.Skill.SAILING, 99).build();
		CoreRouteEngine e = engine();
		RouteResult r = route(e, new PlayerSnapshot(3230, 3250, 0, none, List.of(skiff)), TransportSettings.defaults());
		assertEquals(RouteStatus.NO_ROUTE, r.getStatus());
		NoRouteWhy why = r.getNoRouteWhy();
		assertEquals(com.bestpath.core.data.NoRouteReasons.Kind.BY_BOAT, why.kind);
		assertEquals("The way there is by boat, and needs 1 thing you can't use yet:", why.getHeadline());
		NoRouteWhy.Blocker b = why.blockers.get(0);
		assertEquals("Boat 2: Skiff", b.getStep().getTitle());
		assertEquals(BoatType.SKIFF, b.getStep().getInfo().getBoat());
		assertEquals(List.of("It is in a boat bottle: carry the full bottle in your inventory."), b.getReasons());
		StepInfo.Need bottle = b.getStep().getInfo().getNeeds().get(0);
		assertEquals(Boat.BOTTLE_FULL, bottle.getItemId());
		assertEquals(NeedStatus.MISSING, bottle.getStatus());

		// the boat turned off in Settings
		PlayerSnapshot me = new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(), List.of(RAFT));
		RouteResult off = route(e, me, TransportSettings.defaults().withBoatAllowed(1, false));
		assertEquals(List.of("Turned off in Settings (your boats)."), off.getNoRouteWhy().blockers.get(0).getReasons());
		assertEquals("Boat 1: Raft", off.getNoRouteWhy().blockers.get(0).getStep().getTitle());
		// the boat's Settings tick box on the card; ticking it allows the boat again
		NoRouteWhy.Toggle boatBox = off.getNoRouteWhy().blockers.get(0).getToggles().get(0);
		assertEquals(NoRouteWhy.Toggle.boat(1, "Boat 1: Raft", BoatType.RAFT), boatBox);
		assertFalse(boatBox.isOn(TransportSettings.defaults().withBoatAllowed(1, false)));
		assertEquals(RouteStatus.READY, route(e, me, boatBox.set(TransportSettings.defaults().withBoatAllowed(1, false), true)).getStatus());
		assertTrue("a bottle has no tick box", b.getToggles().isEmpty());

		// no boat, and no shipwright at these docks to bring one: no known way
		RouteResult noBoat = route(e, new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(), List.of()), TransportSettings.defaults());
		assertEquals(com.bestpath.core.data.NoRouteReasons.Kind.NO_KNOWN_WAY, noBoat.getNoRouteWhy().kind);
		assertEquals("There's no known way there in our map data.", noBoat.getNoRouteWhy().getHeadline());
	}

	/**
	 * When a shipwright is used the player is pathed to them. A shipwright at West stands
	 * at (3245, 3265); the capsized raft is brought by it: walk to the shipwright, its card there, walk to the dock.
	 */
	@Test
	public void aShipwrightIsWalkedToBeforeTheDock()
	{
		List<Dock> docks = List.of(DOCKS.get(0).withShipwright("Shipwright Test", 3245, 3265, 0), DOCKS.get(1));
		Boat capsized = new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, Boat.CAPSIZED);
		RouteResult r = route(engine(docks), new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(), List.of(capsized)),
			TransportSettings.defaults());
		assertEquals(RouteStatus.READY, r.getStatus());
		StringBuilder kinds = new StringBuilder();
		for (RouteStep s : r.getSteps())
		{
			kinds.append(kinds.length() == 0 ? "" : " ").append(s.getKind());
		}
		assertEquals("WALK BOAT WALK BOAT SAIL BOAT WALK", kinds.toString());
		int stand = Tiles.pack(3245, 3264, 0); // the tile next to the shipwright nearest the dock
		RouteStep toWright = r.getSteps().get(0);
		RouteStep recover = r.getSteps().get(1);
		RouteStep toDock = r.getSteps().get(2);
		// the walk is drawn up to the shipwright; the card is there
		assertEquals(stand, toWright.tile(toWright.tileCount() - 1));
		assertEquals(stand, recover.getMarkerTile());
		assertEquals(stand, recover.getEndTile());
		// the menu text, the option and the NPC's name ("Recover-boat Shipwright Sam")
		assertEquals("Recover-boat Shipwright Test -> Boat 1: Raft", recover.getTitle());
		assertEquals(10, recover.getTicks());
		assertNull("sailing steps can't be skipped or blacklisted", recover.getTransportId());
		assertEquals("the bring-the-boat card shows the boat", BoatType.RAFT, recover.getInfo().getBoat());
		// then on to the dock, boarded there
		assertEquals(stand, toDock.tile(0));
		assertEquals(Tiles.pack(3257, 3250, 0), toDock.tile(toDock.tileCount() - 1));
		assertEquals("Board Boat 1: Raft", r.getSteps().get(3).getTitle());
		assertEquals(Tiles.pack(3257, 3250, 0), r.getSteps().get(3).getMarkerTile());
	}

	/**
	 * "A shipwright may bring my boat" switched off when only a shipwright can bring the boat: the
	 * "no route" card has that Settings tick box (unticked); ticking it gives the route. A boat switched off as well:
	 * both tick boxes on the one card.
	 */
	@Test
	public void shipwrightsTurnedOffGetTheirTickBox()
	{
		List<Dock> docks = List.of(DOCKS.get(0).withShipwright("Shipwright Test", 3245, 3265, 0), DOCKS.get(1));
		Boat capsized = new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.NONE, Boat.CAPSIZED);
		CoreRouteEngine e = engine(docks);
		PlayerSnapshot me = new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(), List.of(capsized));
		TransportSettings off = TransportSettings.defaults().withShipwrights(false);
		RouteResult r = route(e, me, off);
		assertEquals(RouteStatus.NO_ROUTE, r.getStatus());
		NoRouteWhy.Blocker b = r.getNoRouteWhy().blockers.get(0);
		assertEquals(List.of("A shipwright must bring it, and shipwrights are turned off in Settings."), b.getReasons());
		assertEquals(List.of(NoRouteWhy.Toggle.shipwrights()), b.getToggles());
		NoRouteWhy.Toggle box = b.getToggles().get(0);
		assertEquals("A shipwright may bring my boat (costs coins)", box.getLabel());
		assertFalse(box.isOn(off));
		assertTrue(box.isOn(TransportSettings.defaults()));
		assertEquals(TransportSettings.defaults(), box.set(off, true));
		assertEquals(RouteStatus.READY, route(e, me, box.set(off, true)).getStatus());

		RouteResult both = route(e, me, off.withBoatAllowed(1, false));
		assertEquals(List.of(NoRouteWhy.Toggle.boat(1, "Boat 1: Raft", BoatType.RAFT), NoRouteWhy.Toggle.shipwrights()),
			both.getNoRouteWhy().blockers.get(0).getToggles());
	}

	/**
	 * Sailing cards that are one click in the game say that click, and every boat card names the
	 * player's own boat (else "Boat 1: Sloop"); sailing and leaving keep core's names (they name the boat already).
	 */
	@Test
	public void sailingTitlesFollowTheGamesClicks()
	{
		Dock sam = new Dock(5, "Port Sarim", 1, null, Dock.Kind.GANGPLANK, 3000, 3200, 0, 1, 2990, 3200, 2, List.of())
			.withShipwright("Shipwright Sam", 3024, 3204, 0);
		Dock moor = new Dock(6, "Rellekka", 1, null, Dock.Kind.MOORING_POINT, 2600, 3700, 0, 1, 2590, 3700, 2, List.of());
		Dock east = new Dock(7, "East Rellekka", 1, null, Dock.Kind.GANGPLANK, 2650, 3700, 0, 1, 2660, 3700, 2, List.of());
		List<Dock> docks = List.of(sam, moor, east);
		assertEquals("Recover-boat Shipwright Sam -> Boat 1: Sloop", StepTitles.sailingTitle(here("sail-bring:1",
			"Shipwright Sam brings Boat 1: Sloop to Port Sarim (Recover-boat, 50,000 coins)"), docks));
		assertEquals("Recover-boat Shipwright Sam -> Jolly Raven", StepTitles.sailingTitle(here("sail-bring:1",
			"Shipwright Sam brings Jolly Raven to Port Sarim (Recover-boat, 50,000 coins; it is capsized)"), docks));
		assertEquals("Board Boat 1: Sloop", StepTitles.sailingTitle(here("sail-board:1", "Board Boat 1: Sloop at Port Sarim"), docks));
		assertEquals("Board Jolly Raven", StepTitles.sailingTitle(here("sail-board:2", "Board Jolly Raven at Rellekka"), docks));
		assertEquals("Board Sea Wolf", StepTitles.sailingTitle(here("sail-board:2", "Board Sea Wolf at East Rellekka"), docks));
		assertEquals("Summon Boat -> Jolly Raven (teleport focus)", StepTitles.sailingTitle(here("sail-bring:1",
			"Teleport focus: Jolly Raven brought to Rellekka"), docks));
		assertEquals("Unbottle-boat -> Jolly Raven", StepTitles.sailingTitle(here("sail-release:1",
			"Release Jolly Raven from its bottle at Port Sarim (Unbottle-boat)"), docks));
		// kept: sailing and leaving (core's names say the boat), an unknown shipwright or dock
		assertNull(StepTitles.sailingTitle(here("sail-leave:1", "Leave Jolly Raven at Port Sarim"), docks));
		assertNull(StepTitles.sailingTitle(here("sail:1", "Sail Jolly Raven to Port Sarim"), docks));
		assertNull(StepTitles.sailingTitle(here("sail-bring:1", "Shipwright Bob brings Boat 1: Sloop to Nowhere"), docks));
		assertNull(StepTitles.sailingTitle(here("sail-board:1", "Board Boat 1: Sloop at Nowhere"), docks));
		// core names the boat by the player's own name when the game gave one
		Boat named = new Boat(1, BoatType.SLOOP, 2, 1, 0, 0, false, false, Boat.Focus.NONE, 0).withName("Jolly Raven");
		assertEquals("Jolly Raven", named.label());
		assertEquals("Boat 1: Sloop", named.withName(" ").label());
	}

	private static com.bestpath.core.path.Transport here(String id, String name)
	{
		return com.bestpath.core.path.Transport.between(id, name, 3000, 3200, 0, 3000, 3200, 0, 10, false, true);
	}

	@Test
	public void aCapsizedBoatNeedsAShipwright()
	{
		// no shipwright at these docks: a capsized boat can't be used (a shipwright is the only way)
		Boat capsized = new Boat(1, BoatType.RAFT, 0, 0, 0, 0, false, false, Boat.Focus.TELEPORT, Boat.CAPSIZED);
		assertEquals(RouteStatus.NO_ROUTE, route(engine(), new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(), List.of(capsized)),
			TransportSettings.defaults()).getStatus());
	}

	@Test
	public void theSettingsChooseTheBoats()
	{
		CoreRouteEngine e = engine();
		PlayerSnapshot me = new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(), List.of(RAFT));
		assertEquals(RouteStatus.NO_ROUTE, route(e, me, TransportSettings.defaults().withBoatAllowed(1, false)).getStatus());
		assertEquals(RouteStatus.READY, route(e, me, TransportSettings.defaults().withBoatAllowed(2, false)).getStatus());
		// no boats known (part 2 reads them from the game): walking only
		assertEquals(RouteStatus.NO_ROUTE, route(e, new PlayerSnapshot(3230, 3250, 0, PlayerStateBuilder.maxed(), List.of()), TransportSettings.defaults())
			.getStatus());
		SailingSetup s = CoreRouteEngine.sailing(me, TransportSettings.defaults().withShipwrights(false).withBoatAllowed(1, false));
		assertFalse(s.isShipwrights());
		assertTrue(s.isEmpty());
	}

	@Test
	public void theSailingChoicesAreSaved()
	{
		TransportSettings t = TransportSettings.defaults().withBoatAllowed(2, false).withBoatAllowed(4, false).withShipwrights(false);
		TransportSettings back = TransportSettings.decode(t.encode());
		assertEquals(t, back);
		assertFalse(back.isBoatAllowed(2));
		assertTrue(back.isBoatAllowed(3));
		assertFalse(back.isShipwrights());
		assertTrue(TransportSettings.decode(TransportSettings.defaults().encode()).isShipwrights());
		assertEquals(TransportSettings.defaults(), TransportSettings.defaults().withBoatAllowed(2, false).withBoatAllowed(2, true));
	}
}
