package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.data.NoRouteReasons.Kind;
import com.bestpath.core.data.NoRouteReasons.SailStep;
import com.bestpath.core.data.NoRouteReasons.SailStep.SailWhy;
import com.bestpath.core.data.NoRouteReasons.SailStep.What;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * "No route" by boat, on {@link PluginSailingTest}'s world: two
 * islands with sea between, dock West (Sailing 1, a shipwright) and dock East (Sailing 50, quest "Test Quest").
 * The player stands on the west island and wants to go to the east one (no way on land).
 */
class NoRouteSailingTest
{
	private static final int EAST_X = 3370;
	private static final int Y = 3250;

	private static NoRouteReasons explain(PluginRouter r, PlayerState p, int dx, int dy, SailingSetup boats)
	{
		assertFalse(PluginRouterDiagnostics.route(r, p, 3230, Y, 0, dx, dy, 0, id -> false, PluginRouter.TransportFilter.NONE, boats).getRoute().isFound(),
			"no route first");
		return r.explainNoRoute(p, 3230, Y, 0, new int[]{Tiles.pack(dx, dy, 0)}, id -> false, PluginRouter.TransportFilter.NONE, boats);
	}

	private static PlayerState sailor(int level, boolean quest)
	{
		PlayerState.Builder b = new PlayerState.Builder("Sailor").members(true).level(Skill.SAILING, level);
		if (quest)
		{
			b.quest("Test Quest", QuestState.DONE);
		}
		return b.build();
	}

	@Test
	void noBoatAndTheDockOnTheOtherSide() throws IOException
	{
		PluginRouter r = PluginSailingTest.router(true);
		NoRouteReasons n = explain(r, sailor(1, false), EAST_X, Y, SailingSetup.NONE);
		assertEquals(Kind.BY_BOAT, n.getKind(), n.toString());
		List<SailStep> s = n.getSailSteps();
		assertEquals(2, s.size(), s.toString());
		assertEquals(What.BOAT, s.get(0).getWhat());
		assertNull(s.get(0).getBoat());
		assertEquals(EnumSet.of(SailWhy.NO_BOAT), s.get(0).getWhy());
		assertEquals(What.LEAVE, s.get(1).getWhat());
		assertEquals("East", s.get(1).getDock());
		assertEquals(List.of("Sailing 50 (has 1)", "quest Test Quest"), s.get(1).getUnmetTexts());
		assertEquals(3, n.getThings(), "no boat, Sailing 50, the quest");
		assertEquals("A way there by boat needs 3 things you can't use yet:", n.getReasons().get(0));
		assertEquals("A boat: you have none", n.getReasons().get(1));
		// the player meeting the dock: only the boat is missing
		NoRouteReasons ok = explain(r, sailor(50, true), EAST_X, Y, SailingSetup.NONE);
		assertEquals(1, ok.getThings());
		assertEquals(1, ok.getSailSteps().size());
		// on land with no sea in the data: no known way, as before
		assertEquals(Kind.NO_KNOWN_WAY, explain(PluginSailingTest.router(false), sailor(1, false), EAST_X, Y, SailingSetup.NONE).getKind());
	}

	@Test
	void theBoatTurnedOffInSettings() throws IOException
	{
		PluginRouter r = PluginSailingTest.router(true);
		SailingSetup off = new SailingSetup(List.of(PluginSailingTest.boat(1, 0)), Set.of(), true);
		NoRouteReasons n = explain(r, sailor(50, true), EAST_X, Y, off);
		assertEquals(Kind.BY_BOAT, n.getKind());
		assertEquals(1, n.getSailSteps().size());
		SailStep b = n.getSailSteps().get(0);
		assertEquals(EnumSet.of(SailWhy.BOAT_OFF), b.getWhy());
		assertEquals(1, b.getBoat().getSlot());
		assertEquals("Boat 1: Raft: turned off in Settings", b.describe());
		assertEquals(1, n.getThings());
	}

	@Test
	void theShipwrightTurnedOff() throws IOException
	{
		PluginRouter r = PluginSailingTest.router(true);
		// moored at East: only a shipwright brings it to West
		SailingSetup noWright = new SailingSetup(List.of(PluginSailingTest.boat(1, 1)), null, false);
		NoRouteReasons n = explain(r, sailor(50, true), EAST_X, Y, noWright);
		assertEquals(Kind.BY_BOAT, n.getKind());
		assertEquals(EnumSet.of(SailWhy.SHIPWRIGHTS_OFF), n.getSailSteps().get(0).getWhy());
	}

	@Test
	void theDocksLevelAndQuest() throws IOException
	{
		PluginRouter r = PluginSailingTest.router(true);
		SailingSetup mine = new SailingSetup(List.of(PluginSailingTest.boat(1, 0)), null, true);
		NoRouteReasons n = explain(r, sailor(12, false), EAST_X, Y, mine);
		assertEquals(Kind.BY_BOAT, n.getKind());
		assertEquals(1, n.getSailSteps().size(), "the boat is fine: only the dock it is left at");
		SailStep d = n.getSailSteps().get(0);
		assertEquals(What.LEAVE, d.getWhat());
		assertEquals(EnumSet.of(SailWhy.REQUIREMENTS), d.getWhy());
		assertTrue(d.unmet.get(0) instanceof Requirement.SkillLevel);
		assertEquals("Sailing 50; quest Test Quest", d.getRequirement().toText());
		assertEquals("Leave at East: Sailing 50 (has 12); quest Test Quest", d.describe());
		assertEquals(2, n.getThings());
	}

	@Test
	void aBottledBoatWithoutItsBottle() throws IOException
	{
		PluginRouter r = PluginSailingTest.router(true);
		SailingSetup bottled = new SailingSetup(List.of(PluginSailingTest.boat(1, Boat.BOTTLED)), null, true);
		NoRouteReasons n = explain(r, sailor(50, true), EAST_X, Y, bottled);
		assertEquals(Kind.BY_BOAT, n.getKind());
		SailStep b = n.getSailSteps().get(0);
		assertEquals(EnumSet.of(SailWhy.BOTTLE), b.getWhy());
		assertTrue(b.unmet.get(0) instanceof Requirement.Item);
		assertEquals(Boat.BOTTLE_FULL, ((Requirement.Item) b.unmet.get(0)).getItemIds()[0]);
		assertEquals(1, n.getThings());
	}

	@Test
	void outAtSea() throws IOException
	{
		PluginRouter r = PluginSailingTest.router(true);
		NoRouteReasons n = explain(r, sailor(1, false), 3300, Y, SailingSetup.NONE);
		assertEquals(Kind.BY_BOAT, n.getKind(), n.toString());
		assertTrue(n.isAtSea());
		assertEquals(1, n.getSailSteps().size(), "the boat only: nothing is left at a dock");
		assertEquals(EnumSet.of(SailWhy.NO_BOAT), n.getSailSteps().get(0).getWhy());
		// a free-world player: members land first, never boats
		PlayerState free = new PlayerState.Builder("Free").members(false).build();
		assertFalse(PluginRouterDiagnostics.route(r, free, 3230, Y, 0, 3300, Y, 0, id -> false).getRoute().isFound());
		NoRouteReasons f = r.explainNoRoute(free, 3230, Y, 0, new int[]{Tiles.pack(3300, Y, 0)}, id -> false,
			PluginRouter.TransportFilter.NONE, SailingSetup.NONE);
		assertTrue(f.getSailSteps().isEmpty());
	}
}
