package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WallBits;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/** The plugin's route engine on one open region (3200-3263, 3200-3263, planes 0 and 1) with made-up transports. */
public class CoreRouteEngineTest
{
	private static PluginRouter router;

	private static TransportLine line(String id, TransportLine.Kind kind, String name, String category, int[] start, int[] end,
		int ticks, boolean canFail, String requirements, String note)
	{
		return TransportLine.builder(id, kind).name(name).category(category).start(start[0], start[1], start[2])
			.end(end[0], end[1], end[2]).ticks(ticks).canFail(canFail).requirements(requirements).playerNote(note)
			.build();
	}

	@BeforeClass
	public static void map()
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, open, none, none}, List.of(50 << 8 | 50), 0, 0);
		List<TransportLine> lines = List.of(
			line("t-tele", TransportLine.Kind.TELEPORT, "Test teleport", "TRAVEL", new int[3], new int[]{3259, 3259, 0}, 5,
				false, "", ""),
			line("t-ladder", TransportLine.Kind.TWO_WAY, "Ladder", "CLIMB_BOTH", new int[]{3201, 3200, 0},
				new int[]{3201, 3200, 1}, 2, true, "", "Mind your head"),
			line("t-magic", TransportLine.Kind.TELEPORT, "Magic 50 teleport", "TRAVEL", new int[3], new int[]{3240, 3240, 1}, 5,
				false, "Magic 50", ""));
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines,
			Tables.empty(), FreeLand.NONE);
		router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
	}

	private static RouteResult route(int dx, int dy, int dp, Set<String> skipped, TransportSettings s, PlayerState who)
	{
		CoreRouteEngine e = new CoreRouteEngine(router);
		PlayerSnapshot me = new PlayerSnapshot(3200, 3200, 0, who, List.of());
		return e.route(new RouteJob(9, new RouteRequest(dx, dy, dp), me, s, skipped));
	}

	private static RouteResult route(int dx, int dy, int dp, Set<String> skipped, TransportSettings s)
	{
		return route(dx, dy, dp, skipped, s, PlayerStateBuilder.fresh());
	}

	@Test
	public void walkingOnlyRouteBecomesOneWalkCard()
	{
		RouteResult r = route(3210, 3200, 0, Set.of(), TransportSettings.defaults());
		assertEquals(RouteStatus.READY, r.getStatus());
		assertEquals(9, r.getGeneration());
		assertEquals(10, r.getTotalTicks());
		assertEquals(1, r.getSteps().size());
		RouteStep s = r.getSteps().get(0);
		assertEquals(RouteStep.Kind.WALK, s.getKind());
		assertEquals("Walk to 3210, 3200, 0", s.getTitle());
		assertEquals("10 tiles", s.getDetail());
		assertNull(s.getTransportId());
		assertEquals(1, r.segmentCount());
		assertEquals(11, r.segmentLength(0));
	}

	@Test
	public void teleportUsedUnlessSkippedOrBlacklisted()
	{
		RouteResult r = route(3260, 3260, 0, Set.of(), TransportSettings.defaults());
		assertEquals(RouteStep.Kind.TELEPORT, r.getSteps().get(0).getKind());
		assertEquals("t-tele", r.getSteps().get(0).getTransportId());
		assertEquals("Test teleport", r.getSteps().get(0).getTitle());
		assertEquals("Teleport: Ectophial", StepTitles.teleportTitle("Ectophial"));
		assertEquals(6, r.getTotalTicks());
		assertEquals(Tiles.pack(3259, 3259, 0), r.getSteps().get(0).getEndTile()); // progress moves on at the landing

		assertEquals(60, route(3260, 3260, 0, Set.of("t-tele"), TransportSettings.defaults()).getTotalTicks());
		TransportSettings never = TransportSettings.defaults().withBlacklisted("t-tele", "Test teleport");
		assertEquals(60, route(3260, 3260, 0, Set.of(), never).getTotalTicks());
	}

	@Test
	public void transportStepMarksWhereItIsUsedWithItsNotes()
	{
		RouteResult r = route(3201, 3201, 1, Set.of(), TransportSettings.defaults());
		assertEquals(RouteStatus.READY, r.getStatus());
		RouteStep climb = null;
		for (RouteStep s : r.getSteps())
		{
			if (s.getKind() == RouteStep.Kind.TRAVERSAL)
			{
				climb = s;
			}
		}
		assertEquals("Ladder", climb.getTitle());
		assertEquals("t-ladder", climb.getTransportId());
		assertEquals(Tiles.pack(3201, 3200, 0), climb.getMarkerTile());
		assertEquals(Tiles.pack(3201, 3200, 1), climb.getEndTile());
		assertTrue(climb.getNotes().contains("Mind your head"));
		assertTrue(climb.getNotes().contains("You can fail this; you then try again."));
		assertEquals(1, r.markerCount());
		// 1 tile walk, ladder 2 + plane change, 1 tile walk
		assertEquals(1 + 2 + PluginRouter.PLANE_CHANGE_PENALTY + 1, r.getTotalTicks());
	}

	@Test
	public void requirementsDecideWhichTransportsAreUsed()
	{
		// up the ladder and walk 40 tiles (1 + ladder 2 + plane change + 40) without Magic 50; the teleport (5) with it
		assertEquals(1 + 2 + PluginRouter.PLANE_CHANGE_PENALTY + 40, route(3240, 3240, 1, Set.of(), TransportSettings.defaults()).getTotalTicks());
		PlayerState mage = new PlayerState.Builder("Mage").members(true).level(Skill.MAGIC, 50).build();
		RouteResult r = route(3240, 3240, 1, Set.of(), TransportSettings.defaults(), mage);
		assertEquals(5, r.getTotalTicks());
		assertEquals("t-magic", r.getSteps().get(0).getTransportId());
	}

	@Test
	public void transportStepsCarryWhatTheSidebarIconsNeed()
	{
		RouteResult up = route(3201, 3201, 1, Set.of(), TransportSettings.defaults());
		RouteStep climb = up.getSteps().get(1);
		assertEquals("t-ladder", climb.getTransportId());
		assertEquals("CLIMB_BOTH", climb.getInfo().getCategory());
		assertEquals("Ladder", climb.getInfo().getName());
		assertEquals(TransportSettings.Category.STAIRS_LADDERS, climb.getInfo().getSettingsCategory());
		assertTrue(!climb.getInfo().isReverse());
		assertTrue(climb.getInfo().getNeeds().isEmpty());
		// walks have none
		assertEquals(StepInfo.NONE, up.getSteps().get(0).getInfo());

		PlayerState mage = new PlayerState.Builder("Mage").members(true).level(Skill.MAGIC, 50).build();
		RouteStep tele = route(3240, 3240, 1, Set.of(), TransportSettings.defaults(), mage).getSteps().get(0);
		// a level the player has stays theirs, so no green check for it on the card
		assertTrue(tele.getInfo().getNeeds().isEmpty());
		PlayerState low = new PlayerState.Builder("Low").members(true).level(Skill.MAGIC, 49).build();
		assertEquals(List.of(StepInfo.Need.skill(Skill.MAGIC, 50).withStatus(NeedStatus.MISSING)),
			StepNeeds.needs(com.bestpath.core.req.RequirementText.parse("Magic 50", com.bestpath.core.req.RequirementText.ItemGroups.NONE), -1, low, null));
		assertEquals("Magic 50", StepNeeds.needs(com.bestpath.core.req.RequirementText.parse("Magic 50", com.bestpath.core.req.RequirementText.ItemGroups.NONE), -1, null, null).get(0).getText());
	}

	@Test
	public void cardsShowTheChoiceTheRouterUsedAndTheGroupsDefaultItem()
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		List<TransportLine> lines = List.of(
			TransportLine.builder("t-toll", TransportLine.Kind.TELEPORT).name("Toll teleport").category("TRAVEL")
				.end(3259, 3259, 0).ticks(5).requirements("one of (coins 10 | quest Prince Ali Rescue)").build(),
			TransportLine.builder("t-glory", TransportLine.Kind.TELEPORT).name("Amulet of glory: somewhere")
				.category("TELEPORT").end(3200, 3259, 0).ticks(5).requirements("item amulet of glory")
				.teleportCategory("jewellery (consumable)").build());
		Tables tables = Tables.parse(List.of("amulet of glory\t1706,1708,1712\tsrc\tnames"), List.of(), List.of(), List.of(),
			List.of("amulet of glory\t1712\tRel\t2026-10-13T07:21:55Z\tAmulet of glory(4)"));
		PluginRouter r = new PluginRouter(PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines,
			tables, FreeLand.NONE), PluginRouter.DEFAULT_CACHE_BUDGET);
		CoreRouteEngine e = new CoreRouteEngine(r);
		java.util.function.Function<PlayerState, RouteStep> first = who -> e.route(new RouteJob(1,
			new RouteRequest(3260, 3260, 0), new PlayerSnapshot(3200, 3200, 0, who, List.of()), TransportSettings.defaults(), Set.of()))
			.getSteps().get(0);

		RouteStep paid = first.apply(new PlayerState.Builder("coins").members(true).carry(995, 10).build());
		assertEquals("t-toll", paid.getTransportId());
		assertEquals(List.of(StepInfo.Need.Type.COINS), types(paid.getInfo().getNeeds()));
		assertTrue(paid.getInfo().getNeeds().get(0).getTooltip().contains("one of: 10 coins or Quest: Prince Ali Rescue"));
		RouteStep quest = first.apply(new PlayerState.Builder("quest").members(true)
			.quest("Prince Ali Rescue", QuestState.DONE).build());
		assertEquals("t-toll", quest.getTransportId());
		// the quest is done for good: no green check for it
		assertEquals(List.of(), types(quest.getInfo().getNeeds()));

		java.util.function.Function<PlayerState, RouteStep> glory = who -> e.route(new RouteJob(2,
			new RouteRequest(3200, 3260, 0), new PlayerSnapshot(3200, 3200, 0, who, List.of()), TransportSettings.defaults(), Set.of()))
			.getSteps().get(0);
		assertEquals("the group's default", 1712, glory.apply(PlayerStateBuilder.maxed()).getInfo().getItemId());
		RouteStep own = glory.apply(new PlayerState.Builder("own").members(true).carry(1708, 1).build());
		assertEquals("t-glory", own.getTransportId());
		assertEquals("the one the player carries", 1708, own.getInfo().getItemId());
	}

	private static List<StepInfo.Need.Type> types(List<StepInfo.Need> needs)
	{
		List<StepInfo.Need.Type> t = new java.util.ArrayList<>();
		for (StepInfo.Need n : needs)
		{
			t.add(n.getType());
		}
		return t;
	}

	@Test
	public void gatesAreDoorsRidesAreBoatsTheRestClimbs()
	{
		int[] a = {3201, 3200, 0};
		int[] b = {3202, 3200, 0};
		assertEquals(RouteStep.Kind.DOOR, StepTitles.kind(line("g", TransportLine.Kind.TWO_WAY, "Gate", "PASS", a, b, 2,
			false, "coins 10", "")));
		assertEquals(RouteStep.Kind.BOAT, StepTitles.kind(line("r", TransportLine.Kind.ONE_WAY, "Boat", "TRAVEL", a, b, 2,
			false, "", "")));
		assertEquals(RouteStep.Kind.TRAVERSAL, StepTitles.kind(line("s", TransportLine.Kind.TWO_WAY, "Rocks", "SHORTCUT",
			a, b, 2, false, "", "")));
	}

	@Test
	public void aTwoWayTransportUsedBackwardsIsMarkedReverse()
	{
		RouteEngine e = new CoreRouteEngine(router);
		PlayerSnapshot upstairs = new PlayerSnapshot(3202, 3201, 1, PlayerStateBuilder.fresh(), List.of());
		RouteResult r = e.route(new RouteJob(3, new RouteRequest(3203, 3201, 0), upstairs, TransportSettings.defaults(),
			Set.of()));
		RouteStep down = null;
		for (RouteStep s : r.getSteps())
		{
			if ("t-ladder".equals(s.getTransportId()))
			{
				down = s;
			}
		}
		assertTrue(down.getInfo().isReverse());
	}

	@Test
	public void unreachableIsNoRoute()
	{
		RouteResult r = route(3300, 3300, 0, Set.of(), TransportSettings.defaults());
		assertEquals(RouteStatus.NO_ROUTE, r.getStatus());
		assertTrue(r.getSteps().isEmpty());
	}

	private static RouteResult places(Set<String> skipped, int... targets)
	{
		CoreRouteEngine e = new CoreRouteEngine(router);
		PlayerSnapshot me = new PlayerSnapshot(3200, 3200, 0, PlayerStateBuilder.fresh(), List.of());
		RouteRequest req = RouteRequest.places("clue test", "Test clue (easy)", List.of("a note"), targets);
		return e.route(new RouteJob(4, req, me, TransportSettings.defaults(), skipped));
	}

	@Test
	public void severalPlacesRouteToTheCheapest()
	{
		int walkable = Tiles.pack(3230, 3230, 0); // 30 ticks on foot
		int byTeleport = Tiles.pack(3255, 3255, 0); // teleport (5) to 3259,3259 then 4 tiles
		int nowhere = Tiles.pack(3300, 3300, 0);
		RouteResult r = places(Set.of(), walkable, byTeleport, nowhere);
		assertEquals(RouteStatus.READY, r.getStatus());
		assertEquals(9, r.getTotalTicks());
		assertEquals(byTeleport, r.getDestination());
		assertEquals(List.of("Nearest of 3 places.", "1 place can't be reached at all."), r.getNotes());
		assertEquals("a note", r.getRequest().getNotes().get(0));

		// progress arrives at the place chosen, not at the request's first place
		RouteProgress p = new RouteProgress(r);
		assertEquals(RouteProgress.Event.ARRIVED, p.update(3255, 3255, 0, null));
		assertTrue(new RouteProgress(r).update(3230, 3230, 0, null) != RouteProgress.Event.ARRIVED);

		// without the teleport the walkable one is nearer
		r = places(Set.of("t-tele"), walkable, byTeleport);
		assertEquals(walkable, r.getDestination());
		assertEquals(30, r.getTotalTicks());

		// one place only: as a typed tile, no notes from the search
		r = places(Set.of(), walkable);
		assertEquals(30, r.getTotalTicks());
		assertTrue(r.getNotes().isEmpty());
	}

	@Test
	public void noPlaceReachableSaysSoPlainly()
	{
		RouteResult r = places(Set.of(), Tiles.pack(3300, 3300, 0), Tiles.pack(3100, 3100, 0));
		assertEquals(RouteStatus.NO_ROUTE, r.getStatus());
		assertEquals("No route: nothing within 10 tiles of any of the 2 places can be reached.", r.getMessage());
		assertEquals(List.of("2 places can't be reached at all."), r.getNotes());
	}

	/** No bank in this data: the route goes straight there and its header says so (BankStopTest: with a bank). */
	@Test
	public void destinationNeedsOnlyInTheBankWithNoBankToReach()
	{
		DestinationNeeds needs = new DestinationNeeds(List.of(new DestinationNeeds.Item("Gold ring", new int[]{1635}, 1),
			new DestinationNeeds.Item("Spade", new int[]{952}, 1)));
		RouteRequest req = RouteRequest.places("clue test", "Emote clue (easy)", List.of(), new int[]{Tiles.pack(3210, 3200, 0)})
			.withNeeds(needs);
		CoreRouteEngine e = new CoreRouteEngine(router);
		PlayerState banked = new PlayerState.Builder("p").members(true).bank(1635, 1).carry(952, 1).build();
		RouteResult r = e.route(new RouteJob(9, req, new PlayerSnapshot(3200, 3200, 0, banked, List.of()), TransportSettings.defaults(), Set.of()));
		assertEquals(RouteStatus.READY, r.getStatus());
		assertEquals("No bank can be reached first; only in the bank: Gold ring.", r.getNotes().get(0));
		assertEquals(10, r.getTotalTicks());
		PlayerState carried = new PlayerState.Builder("p").members(true).carry(1635, 1).carry(952, 1).build();
		RouteResult c = e.route(new RouteJob(10, req, new PlayerSnapshot(3200, 3200, 0, carried, List.of()), TransportSettings.defaults(), Set.of()));
		assertTrue(c.getNotes().stream().noneMatch(n -> n.startsWith(CoreRouteEngine.NO_BANK_NOTE)));
	}
}
