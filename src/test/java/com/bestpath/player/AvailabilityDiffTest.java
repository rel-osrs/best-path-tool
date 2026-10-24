package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.core.req.Skill;
import com.bestpath.core.req.Spellbook;
import com.bestpath.route.PlayerSnapshot;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Diffing player states: what is a reason to search the route again. */
public class AvailabilityDiffTest
{
	private static final RequirementText.ItemGroups GROUPS = new RequirementText.ItemGroups()
	{
		@Override
		public int[] ids(String name)
		{
			return null;
		}

		@Override
		public RequirementText.RuneSource rune(String name)
		{
			return "law".equals(name) ? new RequirementText.RuneSource(new int[]{563}, new int[0]) : null;
		}
	};

	private static RequirementInputs inputs(String... lines)
	{
		List<Requirement> rs = new ArrayList<>();
		for (String l : lines)
		{
			rs.add(RequirementText.parse(l, GROUPS));
		}
		return RequirementInputs.collect(rs);
	}

	private static PlayerState.Builder base()
	{
		return new PlayerState.Builder("p").members(true).level(Skill.AGILITY, 50).carry(995, 500);
	}

	private static boolean mentionsNothing(RequirementInputs in)
	{
		return in.getVarbits().isEmpty() && in.getVarps().isEmpty() && in.getQuests().isEmpty() && in.getItems().isEmpty();
	}

	/** As if the rest of the route used every cooldown. */
	private static boolean needsReroute(PlayerState before, PlayerState after, RequirementInputs in)
	{
		return AvailabilityDiff.needsReroute(before, after, in, EnumSet.allOf(Cooldown.class));
	}

	private static boolean needsReroute(PlayerSnapshot before, PlayerSnapshot after, RequirementInputs in)
	{
		return AvailabilityDiff.needsReroute(before, after, in, EnumSet.allOf(Cooldown.class));
	}

	@Test
	public void sameStateIsNoChange()
	{
		RequirementInputs in = inputs("Agility 51", "coins 10", "varbit 3337 >= 17", "quest Regicide");
		assertTrue(AvailabilityDiff.changes(base().build(), base().build(), in).isEmpty());
		assertFalse(needsReroute(base().build(), base().build(), in));
	}

	@Test
	public void alwaysComparedParts()
	{
		RequirementInputs none = RequirementInputs.NONE;
		PlayerState a = base().build();
		assertEquals(List.of("Agility 50 -> 51"),
			AvailabilityDiff.changes(a, base().level(Skill.AGILITY, 51).build(), none));
		assertEquals(1, AvailabilityDiff.changes(a, base().members(false).build(), none).size());
		assertEquals(1, AvailabilityDiff.changes(a, base().spellbook(Spellbook.LUNAR).build(), none).size());
		assertEquals(1, AvailabilityDiff.changes(a, base().house("Yanille").build(), none).size());
		assertEquals(1, AvailabilityDiff.changes(a, base().answer("yes", true).build(), none).size());
		assertEquals(1, AvailabilityDiff.changes(a, base().pouchInBank(true).build(), none).size());
		// no transport data: any of these still searches again (nothing to narrow down with)
		assertTrue(needsReroute(a, base().level(Skill.AGILITY, 51).build(), none));
	}

	@Test
	public void varsQuestsAndItemsOnlyWhenMentioned()
	{
		PlayerState a = base().varbit(3337, 16).varp(176, 6).quest("Regicide", QuestState.STARTED).carry(8007, 1).build();
		PlayerState b = base().varbit(3337, 17).varp(176, 7).quest("Regicide", QuestState.DONE).carry(8007, 2).build();
		assertTrue(AvailabilityDiff.changes(a, b, RequirementInputs.NONE).isEmpty());
		assertFalse(needsReroute(a, b, RequirementInputs.NONE));

		assertEquals(List.of("varbit 3337 16 -> 17"), AvailabilityDiff.changes(a, b, inputs("varbit 3337 >= 17")));
		assertEquals(List.of("varp 176 6 -> 7"), AvailabilityDiff.changes(a, b, inputs("varp 176 = 7")));
		assertEquals(List.of("quest regicide STARTED -> DONE"), AvailabilityDiff.changes(a, b, inputs("quest Regicide")));
		assertEquals(List.of("item 8007 carried 1 -> 2"), AvailabilityDiff.changes(a, b, inputs("item 8007")));
	}

	@Test
	public void eatingFoodOrAnUnmentionedVarbitIsNoReason()
	{
		RequirementInputs in = inputs("item 8007", "varbit 3337 >= 17");
		PlayerState a = base().carry(379, 10).varbit(99, 1).build(); // lobsters, some other varbit
		PlayerState b = base().carry(379, 9).varbit(99, 2).build();
		assertTrue(AvailabilityDiff.changes(a, b, in).isEmpty());
		assertFalse(needsReroute(a, b, in));
	}

	@Test
	public void aCountChangeMattersOnlyWhenAnOutcomeChanges()
	{
		RequirementInputs in = inputs("coins 10");
		PlayerState a = base().build(); // 500 coins
		PlayerState spent = new PlayerState.Builder("p").members(true).level(Skill.AGILITY, 50).carry(995, 499).build();
		assertEquals(1, AvailabilityDiff.changes(a, spent, in).size());
		assertFalse(needsReroute(a, spent, in));
		PlayerState broke = new PlayerState.Builder("p").members(true).level(Skill.AGILITY, 50).carry(995, 9).build();
		assertTrue(needsReroute(a, broke, in));
	}

	@Test
	public void bankAndPouchCounts()
	{
		RequirementInputs in = inputs("rune law x1", "item 8007");
		PlayerState a = base().build();
		PlayerState banked = base().bank(8007, 1).build();
		assertEquals(List.of("item 8007 banked 0 -> 1"), AvailabilityDiff.changes(a, banked, in));
		assertTrue(needsReroute(a, banked, in)); // not met -> needs the bank
		PlayerState pouch = base().pouch(563, 5).build();
		assertTrue(needsReroute(a, pouch, in));
	}

	@Test
	public void wearingTheItemChangesAToDo()
	{
		RequirementInputs in = inputs("item 2552 worn");
		PlayerState carried = base().carry(2552, 1).build();
		PlayerState worn = base().wear(EquipSlot.RING, 2552).build();
		// carried count is 1 both times; only the worn slots and the "wear" to-do differ
		assertEquals(List.of("item 2552 worn 0 -> " + (1 << EquipSlot.RING.ordinal())),
			AvailabilityDiff.changes(carried, worn, in));
		assertTrue(needsReroute(carried, worn, in));
	}

	@Test
	public void membersChangeAlwaysCounts()
	{
		RequirementInputs in = inputs("coins 10");
		assertTrue(needsReroute(base().build(), base().members(false).build(), in));
	}

	@Test
	public void noEarlierStateCounts()
	{
		assertTrue(needsReroute(null, base().build(), RequirementInputs.NONE));
	}

	@Test
	public void inputsCollectEverythingMentioned()
	{
		RequirementInputs in = inputs("Agility 56; one of (quest Regicide | varbit 1990 > 39); item 1755/1756",
			"not varp 176 = 7; coins 30", "rune law x1", "", "Agility 56; one of (quest Regicide | varbit 1990 > 39); item 1755/1756");
		assertEquals(Set.of(1990), in.getVarbits());
		assertEquals(Set.of(176), in.getVarps());
		assertEquals(Set.of("regicide"), in.getQuests());
		assertEquals(Set.of(1755, 1756, 995, 563), in.getItems());
		assertEquals(3, in.getRequirements().size()); // the empty line and the repeat are left out
		assertTrue(mentionsNothing(RequirementInputs.NONE));
		assertFalse(mentionsNothing(in));
	}

	private static com.bestpath.core.sea.Boat sloop(int hull, int sail, int keel, int dock)
	{
		return new com.bestpath.core.sea.Boat(1, com.bestpath.core.sea.BoatType.SLOOP, hull, sail, keel, 0, false, false,
			com.bestpath.core.sea.Boat.Focus.NONE, dock);
	}

	private static com.bestpath.route.PlayerSnapshot snap(com.bestpath.core.sea.Boat... boats)
	{
		return new com.bestpath.route.PlayerSnapshot(3050, 3190, 0, PlayerStateBuilder.maxed(), java.util.Arrays.asList(boats));
	}

	@Test
	public void boatsMatterOnlyForWhatRoutesUse()
	{
		RequirementInputs in = inputs("Agility 50");
		// the same boat: nothing to do
		assertFalse(needsReroute(snap(sloop(2, 1, 0, 0)), snap(sloop(2, 1, 0, 0)), in));
		// sails oak -> teak: the same waters (storm resistance from oak), the same speed
		assertTrue(AvailabilityDiff.boatChanges(List.of(sloop(2, 1, 0, 0)), List.of(sloop(2, 2, 0, 0))).isEmpty());
		assertFalse(needsReroute(snap(sloop(2, 1, 0, 0)), snap(sloop(2, 2, 0, 0)), in));
		// hull wooden -> teak: faster
		assertTrue(needsReroute(snap(sloop(0, 1, 0, 0)), snap(sloop(2, 1, 0, 0)), in));
		// keel mithril -> adamant: crystalline waters open
		assertTrue(needsReroute(snap(sloop(2, 1, 3, 0)), snap(sloop(2, 1, 4, 0)), in));
		// moored at another dock, or the player boarded it at sea
		assertEquals(List.of("boat 1 moved from dock 0 to dock 23"),
			AvailabilityDiff.boatChanges(List.of(sloop(2, 1, 0, 0)), List.of(sloop(2, 1, 0, 23))));
		assertTrue(needsReroute(snap(sloop(2, 1, 0, 0)),
			snap(sloop(2, 1, 0, com.bestpath.core.sea.Boat.AT_START)), in));
		// a boat bought or gone
		assertTrue(needsReroute(snap(), snap(sloop(2, 1, 0, 0)), in));
		assertTrue(needsReroute(snap(sloop(2, 1, 0, 0)), snap(), in));
		// a teleport focus built
		com.bestpath.core.sea.Boat focus = new com.bestpath.core.sea.Boat(1, com.bestpath.core.sea.BoatType.SLOOP, 2, 1, 0, 0, false,
			false, com.bestpath.core.sea.Boat.Focus.TELEPORT, 0);
		assertTrue(needsReroute(snap(sloop(2, 1, 0, 0)), snap(focus), in));
		// no earlier snapshot
		assertTrue(needsReroute((com.bestpath.route.PlayerSnapshot) null, snap(), in));
	}

	/** A snapshot whose player carries {@code bottles} full boat bottles. */
	private static com.bestpath.route.PlayerSnapshot withBottles(int bottles, com.bestpath.core.sea.Boat... boats)
	{
		PlayerState.Builder b = new PlayerState.Builder("Bottles").members(true).level(Skill.SAILING, 99);
		if (bottles > 0)
		{
			b.carry(com.bestpath.core.sea.Boat.BOTTLE_FULL, bottles);
		}
		return new com.bestpath.route.PlayerSnapshot(3050, 3190, 0, b.build(), java.util.Arrays.asList(boats));
	}

	@Test
	public void boatBottlesMatterOnlyForBottledBoats()
	{
		// a bottled boat only with its bottle in the inventory; one bottle per bottled boat
		RequirementInputs in = inputs("Agility 50");
		int bottled = com.bestpath.core.sea.Boat.BOTTLED;
		assertTrue(needsReroute(withBottles(0, sloop(2, 1, 0, bottled)), withBottles(1, sloop(2, 1, 0, bottled)), in));
		assertTrue(needsReroute(withBottles(1, sloop(2, 1, 0, bottled)), withBottles(0, sloop(2, 1, 0, bottled)), in));
		// no bottled boat: a bottle changes nothing; two bottles for one bottled boat: the second changes nothing
		assertFalse(needsReroute(withBottles(0, sloop(2, 1, 0, 0)), withBottles(1, sloop(2, 1, 0, 0)), in));
		assertFalse(needsReroute(withBottles(1, sloop(2, 1, 0, bottled)), withBottles(2, sloop(2, 1, 0, bottled)), in));
		// two bottled boats: the second bottle is the one that counts
		com.bestpath.core.sea.Boat two = new com.bestpath.core.sea.Boat(2, com.bestpath.core.sea.BoatType.SKIFF, 0, 0, 0, 0, false,
			false, com.bestpath.core.sea.Boat.Focus.NONE, bottled);
		assertFalse(needsReroute(withBottles(0, sloop(2, 1, 0, bottled), two), withBottles(1, sloop(2, 1, 0, bottled), two),
			in));
		assertTrue(needsReroute(withBottles(1, sloop(2, 1, 0, bottled), two), withBottles(2, sloop(2, 1, 0, bottled), two),
			in));
		// a boat capsizing or being bottled is a move
		assertEquals(List.of("boat 1 moved from dock 0 to capsized"),
			AvailabilityDiff.boatChanges(List.of(sloop(2, 1, 0, 0)), List.of(sloop(2, 1, 0, com.bestpath.core.sea.Boat.CAPSIZED))));
		assertEquals(List.of("boat 1 moved from bottled to dock 23"),
			AvailabilityDiff.boatChanges(List.of(sloop(2, 1, 0, bottled)), List.of(sloop(2, 1, 0, 23))));
	}

	@Test
	public void homeAndMinigameTeleportCooldowns()
	{
		// used only while ready
		RequirementInputs in = inputs("Agility 51");
		PlayerState ready = base().build();
		PlayerState homeUsed = base().coolingDown(com.bestpath.core.req.Cooldown.HOME, 29_000_030L).build();
		PlayerState laterUse = base().coolingDown(com.bestpath.core.req.Cooldown.HOME, 29_000_040L).build();
		assertEquals(List.of("Home teleport cooling down"), AvailabilityDiff.changes(ready, homeUsed, in));
		assertEquals(List.of("Home teleport ready again"), AvailabilityDiff.changes(homeUsed, ready, in));
		// ready again: always a reason (it may make a quicker route)
		assertTrue(needsReroute(homeUsed, ready, in));
		assertTrue(AvailabilityDiff.needsReroute(homeUsed, ready, in, Set.of()));
		// just used: a reason only when the rest of the route still needs it
		assertTrue(needsReroute(ready, homeUsed, in));
		assertTrue(AvailabilityDiff.needsReroute(ready, homeUsed, in, Set.of(com.bestpath.core.req.Cooldown.HOME)));
		assertFalse(AvailabilityDiff.needsReroute(ready, homeUsed, in, Set.of()));
		assertFalse(AvailabilityDiff.needsReroute(ready, homeUsed, in, Set.of(com.bestpath.core.req.Cooldown.MINIGAME)));
		// still cooling down, only the ready time moved: nothing
		assertTrue(AvailabilityDiff.changes(homeUsed, laterUse, in).isEmpty());
		assertFalse(needsReroute(homeUsed, laterUse, in));
		// another change beside it still counts on its own
		assertTrue(AvailabilityDiff.needsReroute(ready, base().level(Skill.AGILITY, 51)
			.coolingDown(com.bestpath.core.req.Cooldown.HOME, 29_000_030L).build(), in, Set.of()));
	}
}
