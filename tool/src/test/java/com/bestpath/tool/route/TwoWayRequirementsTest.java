package com.bestpath.tool.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.path.Transport;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Skill;
import com.bestpath.tool.edit.TransportRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Which transports one player can use (RequirementFilter + RouteTransports): a two-way transport's
 * {@code requirements} (start to end, and back unless {@code requirementsBack} is set) against
 * {@code requirementsBack} (none = "", or different), items only in the bank (usable, with a note), quest-only
 * transports, teleports and their Wilderness limit, and network stops. Item ids only (no item group file needed).
 */
class TwoWayRequirementsTest
{
	private static final int AXE = 1351;

	private static TransportRecord rec(String id, TransportRecord.Kind kind, String there, String back)
	{
		TransportRecord r = new TransportRecord();
		r.id = id;
		r.name = id;
		r.kind = kind;
		r.startX = 3200;
		r.startY = 3200;
		r.endX = 3205;
		r.endY = 3200;
		r.ticks = 4;
		r.requirements = there;
		r.requirementsBack = back;
		return r;
	}

	private static List<Transport> build(RequirementFilter needs, TransportRecord... records)
	{
		return RouteTransports.build(List.of(records), Collections.emptyList(), null, false, c -> true, id -> true, (c, x, y) -> null, needs);
	}

	private static Transport find(List<Transport> list, String id)
	{
		for (Transport t : list)
		{
			if (t.getId().equals(id))
			{
				return t;
			}
		}
		return null;
	}

	private static List<String> ids(List<Transport> list)
	{
		List<String> out = new ArrayList<>();
		for (Transport t : list)
		{
			out.add(t.getId() + (t.isTwoWay() ? " <->" : " ->"));
		}
		Collections.sort(out);
		return out;
	}

	private static PlayerState.Builder member()
	{
		return new PlayerState.Builder("p").members(true);
	}

	@Test
	void backAbsentMeansTheSameBothWays()
	{
		TransportRecord wall = rec("wall", TransportRecord.Kind.TWO_WAY, "Agility 50", null);
		assertEquals(List.of("wall <->"), ids(build(new RequirementFilter(member().level(Skill.AGILITY, 50).build()), wall)));
		RequirementFilter low = new RequirementFilter(member().level(Skill.AGILITY, 49).build());
		assertEquals(List.of(), ids(build(low, wall)), "neither way");
		assertEquals("for p: 1 transport left out (requirements)", low.summary());
	}

	@Test
	void backNoneMeansOnlyTheWayBackIsFree()
	{
		// vines: an axe to cut through from the start; nothing needed from the end
		TransportRecord vines = rec("vines", TransportRecord.Kind.TWO_WAY, "item " + AXE, "");
		RequirementFilter none = new RequirementFilter(member().build());
		List<Transport> l = build(none, vines);
		assertEquals(List.of("vines:back ->"), ids(l));
		Transport back = find(l, "vines:back");
		assertEquals(3205, back.getStartX());
		assertEquals(3200, back.getEndX());
		assertEquals(4, back.getTicks());
		assertEquals("for p: 0 transports left out (requirements), 1 usable one way only", none.summary());
		// with the axe: both ways, one transport
		assertEquals(List.of("vines <->"), ids(build(new RequirementFilter(member().carry(AXE, 1).build()), vines)));
	}

	@Test
	void backDifferentCanLeaveOnlyTheWayThere()
	{
		// a ledge: Agility 30 to climb down, Agility 60 to climb back up
		TransportRecord ledge = rec("ledge", TransportRecord.Kind.TWO_WAY, "Agility 30", "Agility 60");
		RequirementFilter mid = new RequirementFilter(member().level(Skill.AGILITY, 45).build());
		List<Transport> l = build(mid, ledge);
		assertEquals(List.of("ledge ->"), ids(l));
		assertEquals(3200, find(l, "ledge").getStartX());
		assertTrue(mid.summary().endsWith("1 usable one way only"), mid.summary());
		assertEquals(List.of("ledge <->"), ids(build(new RequirementFilter(member().level(Skill.AGILITY, 60).build()), ledge)));
		assertEquals(List.of(), ids(build(new RequirementFilter(member().level(Skill.AGILITY, 29).build()), ledge)));
		// only the harder way met (odd data, still handled): only the way back
		TransportRecord odd = rec("odd", TransportRecord.Kind.TWO_WAY, "Agility 60", "Agility 30");
		assertEquals(List.of("odd:back ->"), ids(build(mid, odd)));
	}

	@Test
	void bankOnlyOneWaySplitsTheTransportAndMarksTheBankWay()
	{
		// there: nothing; back: the axe, which is only in the bank
		TransportRecord vines = rec("vines", TransportRecord.Kind.TWO_WAY, "", "item " + AXE);
		RequirementFilter banked = new RequirementFilter(member().bank(AXE, 1).build());
		List<Transport> l = build(banked, vines);
		assertEquals(List.of("vines ->", "vines:back ->"), ids(l));
		assertEquals("vines", find(l, "vines").getName());
		assertEquals("vines", find(l, "vines:back").getName());
		assertEquals(List.of(String.valueOf(AXE)), banked.itemsFromBank(find(l, "vines:back")));
		assertEquals(List.of(), banked.itemsFromBank(find(l, "vines")));
		assertEquals(List.of("vines ->"), ids(banked.carried(l)), "with what the player carries: one way only");
		assertFalse(banked.summary().contains("one way only"), "both ways are usable: " + banked.summary());
		// both ways need the banked axe: one two-way transport, after a bank visit only
		TransportRecord both = rec("both", TransportRecord.Kind.TWO_WAY, "item " + AXE, null);
		List<Transport> b = build(banked, both);
		assertEquals(List.of("both <->"), ids(b));
		assertEquals("both", find(b, "both").getName());
		assertEquals(List.of(String.valueOf(AXE)), banked.itemsFromBank(find(b, "both")));
		assertEquals(List.of(), ids(banked.carried(b)));
	}

	@Test
	void oneWayTransportsIgnoreTheBackLine()
	{
		TransportRecord drop = rec("drop", TransportRecord.Kind.ONE_WAY, "Agility 50", "");
		assertEquals(List.of(), ids(build(new RequirementFilter(member().build()), drop)));
		assertEquals(List.of("drop ->"), ids(build(new RequirementFilter(member().level(Skill.AGILITY, 50).build()), drop)));
	}

	@Test
	void questOnlyTransportsAreLeftOutForEveryPlayerButKeptWithoutAFilter()
	{
		TransportRecord battle = rec("battle", TransportRecord.Kind.ONE_WAY, "", null);
		battle.questOnly = true;
		RequirementFilter maxed = new RequirementFilter(PlayerStateBuilder.maxed());
		assertEquals(List.of(), ids(build(maxed, battle)));
		assertTrue(maxed.summary().contains("1 quest only"), maxed.summary());
		assertEquals(List.of("battle ->"), ids(build(null, battle)), "no player chosen: everything, as before requirements");
	}

	@Test
	void membersOnlyRequirementsOnAFreeAccount()
	{
		TransportRecord shortcut = rec("shortcut", TransportRecord.Kind.TWO_WAY, "Agility 1", null);
		TransportRecord boat = rec("boat", TransportRecord.Kind.ONE_WAY, "members; coins 30", null);
		TransportRecord ferry = rec("ferry", TransportRecord.Kind.ONE_WAY, "coins 30", null);
		PlayerState free = new PlayerState.Builder("free").level(Skill.AGILITY, 99).carry(PlayerState.COINS, 30).build();
		RequirementFilter f = new RequirementFilter(free);
		assertEquals(List.of("ferry ->"), ids(build(f, shortcut, boat, ferry)));
		assertEquals("for free: 2 transports left out (requirements)", f.summary());
	}

	@Test
	void teleportsNeedTheirRequirementAndKnowWhatTheBankGives()
	{
		TransportRecord tab = rec("tab", TransportRecord.Kind.TELEPORT, "item 8007 used up", null);
		RequirementFilter none = new RequirementFilter(member().build());
		assertEquals(List.of(), ids(build(none, tab)));
		RequirementFilter bank = new RequirementFilter(member().bank(8007, 3).build());
		List<Transport> banked = build(bank, tab);
		assertEquals("tab", find(banked, "tab").getName());
		assertEquals(List.of("8007"), bank.itemsFromBank(find(banked, "tab")));
		assertFalse(find(banked, "tab").hasStart());
		assertEquals("tab", find(build(new RequirementFilter(member().carry(8007, 1).build()), tab), "tab").getName());
	}

	@Test
	void wildernessLimitEdges()
	{
		TransportRecord tele = rec("tele", TransportRecord.Kind.TELEPORT, "", null);
		tele.wildernessLimit = 20;
		TransportRecord walkable = rec("ladder", TransportRecord.Kind.ONE_WAY, "", null);
		walkable.wildernessLimit = 0; // only teleports have a limit; ignored elsewhere
		TransportRecord free = rec("free", TransportRecord.Kind.TELEPORT, "", null); // no limit
		for (int level : new int[]{0, 1, 20, 21, 56})
		{
			RequirementFilter f = new RequirementFilter(PlayerStateBuilder.maxed(), level);
			List<String> got = ids(build(f, tele, walkable, free));
			List<String> want = new ArrayList<>(List.of("free ->", "ladder ->"));
			if (level <= 20)
			{
				want.add("tele ->");
			}
			Collections.sort(want);
			assertEquals(want, got, "Wilderness level " + level);
		}
		TransportRecord home = rec("home", TransportRecord.Kind.TELEPORT, "", null);
		home.wildernessLimit = 0;
		assertEquals(1, build(new RequirementFilter(PlayerStateBuilder.maxed(), 0), home).size(), "limit 0 works outside the Wilderness");
		assertEquals(0, build(new RequirementFilter(PlayerStateBuilder.maxed(), 1), home).size());
	}

	@Test
	void networkStopsLeaveOutTheStopsAPlayerCannotUse()
	{
		TransportRecord a = rec("a", TransportRecord.Kind.NETWORK, "", null);
		TransportRecord b = rec("b", TransportRecord.Kind.NETWORK, "", null);
		TransportRecord c = rec("c", TransportRecord.Kind.NETWORK, "quest Regicide", null);
		a.network = b.network = c.network = "No such network in the file";
		a.endX = 3200;
		b.endX = 3210;
		c.endX = 3220;
		assertEquals(6, build(new RequirementFilter(PlayerStateBuilder.maxed()), a, b, c).size(), "every stop to every other");
		RequirementFilter f = new RequirementFilter(member().build());
		List<Transport> two = build(f, a, b, c);
		assertEquals(2, two.size(), "a <-> b only");
		for (Transport t : two)
		{
			assertFalse(t.getId().endsWith(">c") || t.getId().contains(":c>"), t.getId());
			assertEquals(4, t.getTicks(), "an unknown network adds no extra ticks");
		}
	}

	@Test
	void checksAreRememberedAndUnreadableLinesCountedOnce()
	{
		RequirementFilter f = new RequirementFilter(member().build());
		Check first = f.check("Agility 5");
		assertTrue(first == f.check("Agility 5"), "the same line is checked once");
		assertEquals(Check.Outcome.NOT_MET, first.getOutcome());
		assertTrue(f.check(null).isMet());
		assertTrue(f.check("").isMet());
		assertTrue(f.check("Agilty 5").isMet(), "unreadable: counted as met");
		f.check("Agilty 5");
		assertTrue(f.summary().endsWith("1 unreadable requirement line ignored"), f.summary());
		f.check("item");
		assertTrue(f.summary().endsWith("2 unreadable requirement lines ignored"), f.summary());
		assertTrue(RequirementFilter.usable(Check.MET));
		assertFalse(RequirementFilter.usable(f.check("Agility 5")));
		assertEquals(List.of(), RequirementFilter.bankItems(Check.MET));
	}

	@Test
	void theMarksDoNotChangeWhatIsUsable()
	{
		TransportRecord rocks = rec("rocks", TransportRecord.Kind.TWO_WAY, "Agility 10", null);
		rocks.canFail = true;
		rocks.cooldown = true;
		rocks.playerNote = "slippery";
		assertNotNull(find(build(new RequirementFilter(member().level(Skill.AGILITY, 10).build()), rocks), "rocks"));
		assertNull(find(build(new RequirementFilter(member().build()), rocks), "rocks"));
	}
}
