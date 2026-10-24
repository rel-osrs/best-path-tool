package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.NoRouteReasons.Kind;
import com.bestpath.core.data.NoRouteReasons.Why;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.core.req.Skill;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * "No route" explains itself: each kind of reason, the way needing the fewest transports the player
 * can't use, and every reason a transport is out (requirements, the bank, the Settings filter, skipped / blacklisted,
 * cooling down). Small hand-made map, one region (3200-3263, 3200-3263), plane 0:
 * <pre>
 *   y 3240..3263: solid (nothing to stand on)
 *   y 3200..3239: four areas side by side, walls between them at x 3216, 3232, 3248:
 *     A 3200-3215 (the player)   B 3217-3231   C 3233-3247   D 3249-3263 (no way in at all)
 *   free-to-play land: A and B
 * </pre>
 */
class NoRouteReasonsTest
{
	private static final Predicate<String> NONE = id -> false;
	private static final int SX = 3205;
	private static final int SY = 3205;
	private static final PlayerState FRESH = PlayerStateBuilder.fresh();
	private static final PlayerState FREE = new PlayerState.Builder("Free").members(false).build();

	private static boolean open(int x, int y)
	{
		return x >= 3200 && x < 3264 && y >= 3200 && y < 3240 && x != 3216 && x != 3232 && x != 3248;
	}

	static PluginRouter router(List<TransportLine> lines)
	{
		return router(lines, Long.MAX_VALUE);
	}

	static PluginRouter router(List<TransportLine> lines, long budget)
	{
		MovementRules.Flags plane0 = (x, y) -> open(x, y) ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{plane0, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		FreeLand free = new FreeLand.Builder().set(3200, 3200, 3231, 3263, true).build();
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines, Tables.empty(), free);
		return new PluginRouter(data, budget);
	}

	static TransportLine oneWay(String id, int sx, int sy, int ex, int ey, String req)
	{
		return TransportLine.builder(id, TransportLine.Kind.ONE_WAY).name("Gate " + id).category("CUSTOM")
			.start(sx, sy, 0).end(ex, ey, 0).ticks(2).requirements(req).build();
	}

	static TransportLine twoWay(String id, int sx, int sy, int ex, int ey, String req, String back)
	{
		return TransportLine.builder(id, TransportLine.Kind.TWO_WAY).name("Door " + id).category("CUSTOM")
			.start(sx, sy, 0).end(ex, ey, 0).ticks(2).requirements(req).requirementsBack(back).build();
	}

	static TransportLine teleport(String id, int x, int y, int ticks, String req, String category, boolean cooldown)
	{
		return TransportLine.builder(id, TransportLine.Kind.TELEPORT).name("Teleport " + id).category("TELEPORT")
			.end(x, y, 0).ticks(ticks).cooldown(cooldown).requirements(req).teleportCategory(category).build();
	}

	static TransportLine stop(String id, String name, int x, int y, String req)
	{
		return TransportLine.builder(id, TransportLine.Kind.NETWORK).name(name).category("TRAVEL").end(x, y, 0)
			.ticks(4).network("Rings").requirements(req).build();
	}

	/** A -> B, needs Agility 30. */
	static final TransportLine AGILITY_GATE = oneWay("gate1", 3215, 3210, 3217, 3210, "Agility 30");
	/** B -> C, needs a quest. */
	static final TransportLine QUEST_LADDER = oneWay("ladderBC", 3231, 3210, 3233, 3210, "quest Dragon Slayer");
	/** Into C from anywhere, needs Magic 50: slow (300 ticks), but only one thing the player can't use. */
	static final TransportLine SLOW_TELEPORT = teleport("teleC", 3244, 3220, 300, "Magic 50", "spell", false);

	private static NoRouteReasons explain(PluginRouter r, PlayerState p, int dx, int dy, Predicate<String> excluded,
		PluginRouter.TransportFilter filter)
	{
		assertFalse(PluginRouterDiagnostics.route(r, p, SX, SY, 0, dx, dy, 0, excluded, filter).getRoute().isFound(), "the route must be 'no route' first");
		return PluginRouterDiagnostics.explainNoRoute(r, p, SX, SY, 0, dx, dy, 0, excluded, filter);
	}

	private static Set<Why> why(NoRouteReasons n, int blockedIndex)
	{
		return n.getBlocked().get(blockedIndex).getWhy();
	}

	/** Each way's blocked transport ids (the first way and the others). */
	private static Set<List<String>> ways(NoRouteReasons n)
	{
		Set<List<String>> out = new java.util.HashSet<>();
		out.add(ids(n.getBlocked()));
		for (List<NoRouteReasons.WayStep> w : n.getOtherWays())
		{
			List<NoRouteReasons.WayStep> blocked = new java.util.ArrayList<>();
			for (NoRouteReasons.WayStep st : w)
			{
				if (st.isBlocked())
				{
					blocked.add(st);
				}
			}
			out.add(ids(blocked));
		}
		return out;
	}

	@Test
	void aFreeWorldPlayerToMembersLand()
	{
		PluginRouter r = router(List.of(AGILITY_GATE, QUEST_LADDER, SLOW_TELEPORT));
		NoRouteReasons n = explain(r, FREE, 3244, 3220, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.MEMBERS_LAND, n.getKind());
		assertTrue(n.isFreeWorld());
		assertTrue(n.steps.isEmpty());
		assertEquals(-1, NoRouteReasonsDiagnostics.ticks(n));
		assertTrue(n.getReasons().get(0).startsWith("Members only"), n.getReasons().toString());
		assertFalse(r.isMembersSearch(), "the free-world search only: no members search is made to explain");
	}

	@Test
	void nowhereToStandIsNotTheSameAsUnreachable()
	{
		PluginRouter r = router(List.of());
		// 18 tiles into the solid part: nothing within 10 tiles can be walked on
		NoRouteReasons solid = explain(r, FRESH, 3210, 3257, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.NOT_STANDABLE, solid.getKind());
		assertFalse(solid.isAtSea(), "no sea in this data");
		assertTrue(solid.getReasons().get(0).startsWith("Nowhere to stand"), solid.getReasons().toString());
		// walkable but cut off: area D (no way in)
		NoRouteReasons cutOff = explain(r, FRESH, 3259, 3220, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.NO_KNOWN_WAY, cutOff.getKind());
		assertTrue(cutOff.getReasons().get(0).startsWith("No known way"), cutOff.getReasons().toString());
		// solid, but walkable tiles 6 away that the player reaches: there is a route (it stops there)
		assertTrue(PluginRouterDiagnostics.route(r, FRESH, SX, SY, 0, 3210, 3245, 0, NONE).getRoute().isFound());
		assertEquals(Kind.REACHABLE, PluginRouterDiagnostics.explainNoRoute(r, FRESH, SX, SY, 0, 3210, 3245, 0, NONE, PluginRouter.TransportFilter.NONE).getKind());
	}

	@Test
	void aRequirementNotMet()
	{
		PluginRouter r = router(List.of(AGILITY_GATE));
		NoRouteReasons n = explain(r, FRESH, 3228, 3210, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		assertEquals(1, n.getBlocked().size());
		NoRouteReasons.WayStep s = n.getBlocked().get(0);
		assertEquals("gate1", s.getTransport().getId());
		assertEquals("gate1", s.getLine().getId());
		assertEquals(EnumSet.of(Why.REQUIREMENTS), s.getWhy());
		assertEquals("Agility 30", s.getRequirement().toText());
		assertEquals(1, s.getUnmet().size());
		assertTrue(s.getUnmet().get(0) instanceof Requirement.SkillLevel);
		assertEquals(List.of("Agility 30 (has 1)"), s.unmetTexts);
		assertTrue(s.inBank.isEmpty());
		assertFalse(s.isReverse());
		assertEquals(2, s.getTicks(), "a route's ticks, never the search's extra ticks");
		assertEquals("Gate gate1: Agility 30 (has 1)", s.describe());
		assertEquals(List.of("The way there needs 1 thing you can't use yet:", "Gate gate1: Agility 30 (has 1)"), n.getReasons());
		// the way's ticks are those of the route a player who can use the gate gets
		PlayerState agile = new PlayerState.Builder("Agile").members(true).level(Skill.AGILITY, 30).build();
		assertEquals(PluginRouterDiagnostics.route(r, agile, SX, SY, 0, 3228, 3210, 0, NONE).getRoute().getTicks(), NoRouteReasonsDiagnostics.ticks(n));
		assertEquals(0, NoRouteReasonsDiagnostics.chosen(n));
		assertTrue(Arrays.equals(new int[]{3228, 3210, 0, 1}, stop(n)));
	}

	@Test
	void theWayNeedingTheFewestTransportsWinsOverAQuickerOne()
	{
		PluginRouter r = router(List.of(AGILITY_GATE, QUEST_LADDER, SLOW_TELEPORT));
		// the walk through the gate and up the ladder is quick, but needs 2 things; the teleport is slow and needs 1
		PlayerState both = new PlayerState.Builder("Agile quester").members(true).level(Skill.AGILITY, 30)
			.quest("Dragon Slayer", QuestState.DONE).build();
		int quick = PluginRouterDiagnostics.route(r, both, SX, SY, 0, 3244, 3220, 0, NONE).getRoute().getTicks();
		assertTrue(quick < 100, "the two-transport way is quick: " + quick);

		NoRouteReasons n = explain(r, FRESH, 3244, 3220, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		assertEquals(1, n.getBlocked().size());
		assertEquals("teleC", n.getBlocked().get(0).getTransport().getId());
		assertEquals(List.of("Magic 50 (has 1)"), n.getBlocked().get(0).unmetTexts);
		assertEquals(300, NoRouteReasonsDiagnostics.ticks(n));

		// with Magic 50 the teleport can be used: a route
		PlayerState mage = new PlayerState.Builder("Mage").members(true).level(Skill.MAGIC, 50).build();
		assertTrue(PluginRouterDiagnostics.route(r, mage, SX, SY, 0, 3244, 3220, 0, NONE).getRoute().isFound());
		// without the teleport: both the gate and the ladder, in the order they are used
		PluginRouter walk = router(List.of(AGILITY_GATE, QUEST_LADDER));
		NoRouteReasons two = explain(walk, FRESH, 3244, 3220, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, two.getKind());
		assertEquals(List.of("gate1", "ladderBC"), ids(two.getBlocked()));
		assertEquals(quick, NoRouteReasonsDiagnostics.ticks(two));
		assertEquals("The way there needs 2 things you can't use yet:", two.getReasons().get(0));
	}

	/**
	 * Requirements are counted: two gates needing Agility 30 are one thing. A card is one
	 * thing however many of its needs are missing: the teleport
	 * needing Magic 50 and a quest is one too, so both ways show, the quicker first.
	 */
	@Test
	void requirementsAreCountedNotTransports()
	{
		TransportLine gateBC = oneWay("gateBC", 3231, 3212, 3233, 3212, "Agility 30");
		TransportLine teleport = teleport("teleC2", 3244, 3220, 300, "Magic 50; quest Dragon Slayer", "spell", false);
		PluginRouter r = router(List.of(AGILITY_GATE, gateBC, teleport));
		NoRouteReasons n = explain(r, FRESH, 3244, 3220, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		assertEquals(1, n.getThings());
		assertEquals(Set.of(List.of("gate1", "gateBC"), List.of("teleC2")), ways(n), "two transports, one thing; one card, one thing");
		assertEquals("There are 2 ways there, each needing 1 thing you can't use yet:", n.getReasons().get(0));
	}

	/**
	 * Three gates all needing Agility 30 (one thing) against one teleport needing two things (one card: one thing):
	 * both ways show; the gates' way is found though three transports need its thing.
	 */
	@Test
	void aThingSharedByManyTransportsIsFound()
	{
		TransportLine gateBC = oneWay("gateBC", 3231, 3212, 3233, 3212, "Agility 30");
		TransportLine gateCD = oneWay("gateCD", 3247, 3212, 3249, 3212, "Agility 30");
		TransportLine teleport = teleport("teleD", 3255, 3220, 300, "Magic 50; Herblore 10", "spell", false);
		PluginRouter r = router(List.of(AGILITY_GATE, gateBC, gateCD, teleport));
		NoRouteReasons n = explain(r, FRESH, 3255, 3220, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		assertEquals(Set.of(List.of("gate1", "gateBC", "gateCD"), List.of("teleD")), ways(n));
		assertEquals(1, n.getThings());
		// with Agility 30 already: the way is open
		PlayerState agile = new PlayerState.Builder("Agile").members(true).level(Skill.AGILITY, 30).build();
		assertTrue(PluginRouterDiagnostics.route(r, agile, SX, SY, 0, 3255, 3220, 0, NONE).getRoute().isFound());
	}

	@Test
	void leftOutBySettingsOrById()
	{
		TransportLine gate = oneWay("gate2", 3215, 3212, 3217, 3212, "");
		PluginRouter r = router(List.of(gate));
		PluginRouter.TransportFilter off = (t, line) -> line != null && line.getId().equals("gate2");
		NoRouteReasons byFilter = explain(r, FRESH, 3228, 3210, NONE, off);
		assertEquals(Kind.BLOCKED, byFilter.getKind());
		assertEquals(EnumSet.of(Why.FILTER), why(byFilter, 0));
		assertTrue(byFilter.getBlocked().get(0).getUnmet().isEmpty());
		assertEquals("Gate gate2: turned off in Settings", byFilter.getBlocked().get(0).describe());

		NoRouteReasons byId = explain(r, FRESH, 3228, 3210, "gate2"::equals, PluginRouter.TransportFilter.NONE);
		assertEquals(EnumSet.of(Why.EXCLUDED), why(byId, 0));
		assertEquals("Gate gate2: skipped or blacklisted", byId.getBlocked().get(0).describe());

		NoRouteReasons bothWays = explain(r, FRESH, 3228, 3210, "gate2"::equals, off);
		assertEquals(EnumSet.of(Why.FILTER, Why.EXCLUDED), why(bothWays, 0));

		// one that needs Agility and is turned off: both reasons
		PluginRouter r2 = router(List.of(AGILITY_GATE));
		NoRouteReasons twoReasons = explain(r2, FRESH, 3228, 3210, NONE, (t, line) -> true);
		assertEquals(EnumSet.of(Why.REQUIREMENTS, Why.FILTER), why(twoReasons, 0));
	}

	/**
	 * "Skip" on a "no route" card shows the next way without that transport: a left-out transport is
	 * on no way at all (an excluded one is still shown, as skipped or blacklisted).
	 */
	@Test
	void leftOutTransportsAreOnNoWay()
	{
		PluginRouter r = router(List.of(AGILITY_GATE, QUEST_LADDER, SLOW_TELEPORT));
		int[] place = {Tiles.pack(3244, 3220, 0)};
		Predicate<String> tele = "teleC"::equals;
		assertFalse(PluginRouterDiagnostics.route(r, FRESH, SX, SY, 0, 3244, 3220, 0, tele, PluginRouter.TransportFilter.NONE).getRoute().isFound());
		// excluded only: the teleport is still the way (one thing), now also skipped
		NoRouteReasons shown = PluginRouterDiagnostics.explainNoRoute(r, FRESH, SX, SY, 0, place, tele, PluginRouter.TransportFilter.NONE);
		assertEquals(List.of("teleC"), ids(shown.getBlocked()));
		assertEquals(EnumSet.of(Why.REQUIREMENTS, Why.EXCLUDED), why(shown, 0));
		// left out: the next way, through the gate and up the ladder
		NoRouteReasons next = r.explainNoRoute(FRESH, SX, SY, 0, place, tele, tele, PluginRouter.TransportFilter.NONE,
			SailingSetup.NONE);
		assertEquals(Kind.BLOCKED, next.getKind());
		assertEquals(List.of("gate1", "ladderBC"), ids(next.getBlocked()));
		assertTrue(next.getOtherWays().isEmpty());
		// the gate left out too: no way left
		Predicate<String> both = id -> id.equals("teleC") || id.equals("gate1");
		assertEquals(Kind.NO_KNOWN_WAY, r.explainNoRoute(FRESH, SX, SY, 0, place, both, both, PluginRouter.TransportFilter.NONE,
			SailingSetup.NONE).getKind());
	}

	/** A card stands for every ride to its stop (fairy ring DLR from any ring): left out by its card key, all of them go. */
	@Test
	void aCardLeftOutTakesEveryRideToItsStop()
	{
		PluginRouter r = router(List.of(stop("ring-a", "Ring A", 3210, 3230, ""), stop("ring-a2", "Ring A2", 3205, 3235, ""),
			stop("ring-c", "Ring C", 3240, 3230, "quest Fairytale")));
		int[] place = {Tiles.pack(3244, 3220, 0)};
		NoRouteReasons n = explain(r, FRESH, 3244, 3220, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals("ring-c", n.getBlocked().get(0).getCardKey());
		String ride = n.getBlocked().get(0).getTransport().getId();
		// one ride left out: another ring's ride to the same stop is the way
		NoRouteReasons byRide = r.explainNoRoute(FRESH, SX, SY, 0, place, ride::equals, ride::equals,
			PluginRouter.TransportFilter.NONE, SailingSetup.NONE);
		assertEquals(Kind.BLOCKED, byRide.getKind());
		assertEquals("ring-c", byRide.getBlocked().get(0).getCardKey());
		assertFalse(ride.equals(byRide.getBlocked().get(0).getTransport().getId()));
		// the card left out: no ride to Ring C at all
		assertEquals(Kind.NO_KNOWN_WAY, r.explainNoRoute(FRESH, SX, SY, 0, place, NONE, "ring-c"::equals,
			PluginRouter.TransportFilter.NONE, SailingSetup.NONE).getKind());
	}

	@Test
	void aTeleportCoolingDown()
	{
		PluginRouter r = router(List.of(teleport("home", 3228, 3220, 5, "", "home teleport", true)));
		PlayerState used = new PlayerState.Builder("Home teleport used").members(true).coolingDown(Cooldown.HOME, 29_000_000L).build();
		NoRouteReasons n = explain(r, used, 3228, 3220, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		NoRouteReasons.WayStep s = n.getBlocked().get(0);
		assertEquals(EnumSet.of(Why.COOLDOWN), s.getWhy());
		assertEquals(Cooldown.HOME, s.cooldown);
		assertEquals(29_000_000L, s.getReadyAtMinute());
		assertEquals("Teleport home: Home teleport cooling down", s.describe());
		// ready: a route
		assertTrue(PluginRouterDiagnostics.route(r, FRESH, SX, SY, 0, 3228, 3220, 0, NONE).getRoute().isFound());
	}

	@Test
	void metOnlyWithTheBank()
	{
		PluginRouter r = router(List.of(oneWay("toll", 3215, 3210, 3217, 3210, "coins 100")));
		PlayerState banker = new PlayerState.Builder("Banker").members(true).bank(PlayerState.COINS, 1000).build();
		NoRouteReasons n = explain(r, banker, 3228, 3210, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		NoRouteReasons.WayStep s = n.getBlocked().get(0);
		assertEquals(EnumSet.of(Why.BANK), s.getWhy());
		assertTrue(s.getUnmet().isEmpty());
		assertEquals(1, s.inBank.size());
		assertTrue(s.inBank.get(0) instanceof Requirement.Coins);
		assertEquals(List.of("coins 100 from the bank"), s.inBankTexts);
		assertEquals("Gate toll: in the bank: coins 100", s.describe());
	}

	@Test
	void ofSeveralChoicesTheNearestToBeingMetIsShown()
	{
		PluginRouter r = router(List.of(oneWay("choice", 3215, 3210, 3217, 3210, "one of (Agility 70; quest Dragon Slayer | Agility 30)")));
		NoRouteReasons n = explain(r, FRESH, 3228, 3210, NONE, PluginRouter.TransportFilter.NONE);
		NoRouteReasons.WayStep s = n.getBlocked().get(0);
		assertEquals(List.of("Agility 30 (has 1)"), s.unmetTexts, "the second choice needs one thing, the first two");
		assertEquals("one of (Agility 70; quest Dragon Slayer | Agility 30)", s.getRequirement().toText());

		Requirement.AnyOf any = (Requirement.AnyOf) parse("one of (Agility 70; quest Dragon Slayer | Agility 30)");
		assertEquals(1, NoRouteReasons.closestChoice(any, FRESH));
		PlayerState quester = new PlayerState.Builder("Quester").members(true).level(Skill.AGILITY, 70)
			.quest("Dragon Slayer", QuestState.DONE).build();
		assertEquals(0, NoRouteReasons.closestChoice(any, quester), "one met: the one routes use");
		// equally near: the first
		assertEquals(0, NoRouteReasons.closestChoice((Requirement.AnyOf) parse("one of (Agility 30 | Magic 30)"), FRESH));
	}

	@Test
	void theWayBackHasItsOwnRequirement()
	{
		// free there, Agility 40 back: the player in B going to A needs the way back
		PluginRouter r = router(List.of(twoWay("door", 3215, 3215, 3217, 3215, "", "Agility 40")));
		PlayerState p = FRESH;
		assertFalse(PluginRouterDiagnostics.route(r, p, 3225, 3215, 0, 3205, 3215, 0, NONE).getRoute().isFound());
		NoRouteReasons n = PluginRouterDiagnostics.explainNoRoute(r, p, 3225, 3215, 0, 3205, 3215, 0, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		NoRouteReasons.WayStep s = n.getBlocked().get(0);
		assertEquals("door:back", s.getTransport().getId());
		assertTrue(s.isReverse());
		assertEquals("Agility 40", s.getRequirement().toText());
		assertEquals(List.of("Agility 40 (has 1)"), s.unmetTexts);
		// a whole two-way transport needing the same both ways, used backwards: its way-back line
		PluginRouter r2 = router(List.of(twoWay("door2", 3215, 3215, 3217, 3215, "quest Dragon Slayer", "quest Dragon Slayer; Agility 5")));
		NoRouteReasons n2 = PluginRouterDiagnostics.explainNoRoute(r2, p, 3225, 3215, 0, 3205, 3215, 0, NONE, PluginRouter.TransportFilter.NONE);
		NoRouteReasons.WayStep s2 = n2.getBlocked().get(0);
		assertEquals("door2", s2.getTransport().getId(), "both ways not met: kept whole, as routes keep it");
		assertTrue(s2.isReverse());
		assertEquals(List.of("quest Dragon Slayer", "Agility 5 (has 1)"), s2.unmetTexts);
	}

	@Test
	void aNetworkRideNeedsBothStops()
	{
		PluginRouter r = router(List.of(stop("ring-a", "Ring A", 3210, 3230, ""), stop("ring-b", "Ring B", 3225, 3230, "quest Fairytale")));
		NoRouteReasons n = explain(r, FRESH, 3228, 3225, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		NoRouteReasons.WayStep s = n.getBlocked().get(0);
		assertEquals("Rings:Ring A>Ring B", s.getTransport().getId());
		assertEquals("ring-b", s.getLine().getId());
		assertEquals("ring-a", s.getFromStop().getId());
		assertEquals("quest Fairytale", s.getRequirement().toText());
		assertEquals(List.of("quest Fairytale"), s.unmetTexts);
		// the stop left from needs something too: both stops' requirements, each part once
		PluginRouter r2 = router(List.of(stop("ring-a", "Ring A", 3210, 3230, "quest Fairytale; Agility 10"),
			stop("ring-b", "Ring B", 3225, 3230, "quest Fairytale")));
		NoRouteReasons.WayStep s2 = PluginRouterDiagnostics.explainNoRoute(r2, FRESH, SX, SY, 0, 3228, 3225, 0, NONE, PluginRouter.TransportFilter.NONE)
			.getBlocked().get(0);
		assertEquals("quest Fairytale; Agility 10", s2.getRequirement().toText());
		assertEquals(List.of("quest Fairytale", "Agility 10 (has 1)"), s2.unmetTexts);
	}

	@Test
	void severalPlacesTheOneWithAWayIsExplained()
	{
		PluginRouter r = router(List.of(SLOW_TELEPORT));
		int[] places = {Tiles.pack(3259, 3220, 0), Tiles.pack(3244, 3222, 0)};
		assertEquals(-1, PluginRouterDiagnostics.routeNearest(r, FRESH, SX, SY, 0, places, NONE, 5).getChosen());
		NoRouteReasons n = PluginRouterDiagnostics.explainNoRoute(r, FRESH, SX, SY, 0, places, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		assertEquals(1, NoRouteReasonsDiagnostics.chosen(n), "the place in C (the other has no way in)");
		assertEquals(2, NoRouteReasonsDiagnostics.placesLookedAt(n));
		assertEquals("The way to the nearest place needs 1 thing you can't use yet:", n.getReasons().get(0));
		// none with a way: about all of them
		NoRouteReasons none = PluginRouterDiagnostics.explainNoRoute(r, FRESH, SX, SY, 0, new int[]{Tiles.pack(3259, 3220, 0), Tiles.pack(3261, 3230, 0)}, NONE,
			PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.NO_KNOWN_WAY, none.getKind());
		assertEquals(-1, NoRouteReasonsDiagnostics.chosen(none));
		assertEquals("No known way there: the map data has no way to any of the 2 places, even with every transport.",
			none.getReasons().get(0));
		// on a free world: the places on members land are left out first
		NoRouteReasons free = PluginRouterDiagnostics.explainNoRoute(r, FREE, SX, SY, 0, new int[]{Tiles.pack(3259, 3220, 0), Tiles.pack(3244, 3222, 0)}, NONE,
			PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.MEMBERS_LAND, free.getKind());
	}

	@Test
	void aFreeWorldPlayerAndAMembersOnlyTransportOnFreeLand()
	{
		PluginRouter r = router(List.of(oneWay("members-gate", 3215, 3210, 3217, 3210, "members")));
		NoRouteReasons n = explain(r, FREE, 3228, 3210, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(Kind.BLOCKED, n.getKind());
		assertTrue(n.isFreeWorld());
		assertTrue(n.getBlocked().get(0).getUnmet().get(0) instanceof Requirement.Members);
		assertEquals(List.of("members"), n.getBlocked().get(0).unmetTexts);
	}

	@Test
	void everyTransportOfTheWayIsListedUsableOrNot()
	{
		// a free gate A -> B, then the quest ladder B -> C
		PluginRouter r = router(List.of(oneWay("free", 3215, 3210, 3217, 3210, ""), QUEST_LADDER));
		NoRouteReasons n = explain(r, FRESH, 3244, 3215, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(List.of("free", "ladderBC"), ids(n.steps));
		assertFalse(n.steps.get(0).isBlocked());
		assertTrue(n.steps.get(0).getWhy().isEmpty());
		assertTrue(n.steps.get(0).getUnmet().isEmpty());
		assertEquals(List.of("ladderBC"), ids(n.getBlocked()));
		int[] tiles = n.getBlocked().get(0).tiles.clone();
		assertEquals(Tiles.pack(3231, 3210, 0), tiles[0]);
		assertEquals(Tiles.pack(3233, 3210, 0), tiles[tiles.length - 1]);
	}

	@Test
	void nothingIsKeptOverTheBudget()
	{
		PluginRouter r = router(List.of(AGILITY_GATE, QUEST_LADDER, SLOW_TELEPORT), 1);
		PluginRouterDiagnostics.explainNoRoute(r, FRESH, SX, SY, 0, 3244, 3220, 0, NONE, PluginRouter.TransportFilter.NONE);
		assertEquals(0, PluginRouterDiagnostics.cacheBytes(r), "the search's caches go back within the budget, as after a route");
	}

	@Test
	void theTickChangesCountAndTheExtraTicksNever()
	{
		PluginRouter r = router(List.of(SLOW_TELEPORT));
		PluginRouter.TransportFilter cheaper = new PluginRouter.TransportFilter()
		{
			@Override
			public boolean excluded(Transport t, TransportLine line)
			{
				return false;
			}

			@Override
			public int tickChange(String id)
			{
				return id.equals("teleC") ? -100 : 0;
			}
		};
		NoRouteReasons n = explain(r, FRESH, 3244, 3220, NONE, cheaper);
		assertEquals(200, NoRouteReasonsDiagnostics.ticks(n));
		assertEquals(200, n.getBlocked().get(0).getTicks());
		assertEquals(-100, n.getBlocked().get(0).getTransport().getTickChange());
	}

	/** The search with large extra costs per transport finds the same shortest route with or without the extra unit. */
	@Test
	void theSearchsMarginLeavesTheRouteExact()
	{
		PluginRouter r = router(List.of());
		Pathfinder pf = PluginRouterDiagnostics.pathfinder(r);
		int big = NoRouteReasons.BLOCKED_EXTRA;
		List<Transport> list = new ArrayList<>();
		list.add(Transport.teleport("slow", "slow", 3240, 3220, 0, 300, true).withTickChange(big));
		list.add(Transport.between("gate", "gate", 3215, 3210, 0, 3217, 3210, 0, 2, false, true).withTickChange(big));
		list.add(Transport.between("ladder", "ladder", 3231, 3210, 0, 3233, 3210, 0, 2, false, true).withTickChange(big));
		for (int[] d : new int[][]{{3240, 3220}, {3225, 3210}, {3247, 3239}, {3233, 3200}})
		{
			PathQuery q = new PathQuery(SX, SY, 0, d[0], d[1], 0, list, PluginRouter.PLANE_CHANGE_PENALTY);
			int plain = pf.find(q).getTicks();
			assertEquals(plain, pf.find(q, Integer.MAX_VALUE, big).getTicks(), d[0] + "," + d[1]);
			assertTrue(plain > big, "the extra ticks are part of the search's cost");
		}
	}

	private static Requirement parse(String s)
	{
		try
		{
			return RequirementText.parse(s, RequirementText.ItemGroups.NONE);
		}
		catch (RequirementText.ParseException e)
		{
			throw new AssertionError(e);
		}
	}

	private static List<String> ids(List<NoRouteReasons.WayStep> steps)
	{
		List<String> out = new ArrayList<>();
		for (NoRouteReasons.WayStep s : steps)
		{
			out.add(s.getTransport().getId());
		}
		return out;
	}

	/** Where the way ends {x, y, plane, inSight 1/0}, or null (a copy). */
	private static int[] stop(NoRouteReasons n)
	{
		return n.stop == null ? null : n.stop.clone();
	}
}
