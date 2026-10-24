package com.bestpath.core.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.req.Check.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequirementTest
{
	private static final RequirementText.ItemGroups GROUPS = RequirementText.ItemGroups.of(Map.of(
		"axe", new int[]{1351, 1349, 1353},
		"light source", new int[]{33, 4531}));

	private static Requirement parse(String s)
	{
		return RequirementText.parse(s, GROUPS);
	}

	private static Outcome outcome(String req, PlayerState p)
	{
		return parse(req).check(p).getOutcome();
	}

	@Test
	void linesReadBackTheSame()
	{
		String[] lines = {
			"Agility 61",
			"quest Regicide",
			"quest In Aid of the Myreque started",
			"quest Recipe for Disaster (Lumbridge Guide) not done",
			"varbit 1990 > 39",
			"varp 139 >= 7",
			"varbit 4458 & 4",
			"item axe",
			"item 1755/1756 x2 worn used up",
			"item light source",
			"coins 100000",
			"members",
			"empty head",
			"answer battered door air rune",
			"house",
			"house at Yanille",
			"house has ornate jewellery box",
			"Agility 56; one of (quest Regicide | varbit 1990 > 39); item axe",
			"one of (Agility 50; quest Regicide | Strength 60) ; coins 30",
		};
		for (String s : lines)
		{
			Requirement r = parse(s);
			assertEquals(r, parse(r.toText()), s);
		}
		assertEquals("Agility 56; one of (quest Regicide | varbit 1990 > 39); item axe", parse(lines[17]).toText());
		assertEquals("one of (Agility 50; quest Regicide | Strength 60); coins 30", parse(lines[18]).toText());
		assertEquals("coins 100000", parse("Coins 100,000").toText());
		assertEquals("Agility 61", parse("  agility   61 ").toText());
	}

	@Test
	void blankIsNone()
	{
		assertSame(Requirement.NONE, parse(""));
		assertSame(Requirement.NONE, RequirementText.parse(null, GROUPS));
		assertTrue(Requirement.NONE.isNone());
		assertEquals("", Requirement.NONE.toText());
		assertTrue(Requirement.NONE.check(PlayerStateBuilder.fresh()).isMet());
	}

	@Test
	void badLinesSayWhy()
	{
		String[][] bad = {
			{"Agilty 61", "unknown part 'Agilty'"},
			{"Agility", "a skill needs a level"},
			{"varbit 1990 39", "varbit needs"},
			{"item spade", "unknown item group 'spade'"},
			{"item axe x0", "at least 1"},
			{"item axe x2 shiny", "unknown item option"},
			{"one of Agility 5", "needs '('"},
			{"one of (Agility 5 | Strength 5", "not closed"},
			{"Agility 5;", "empty part"},
			{"empty hat", "unknown slot"},
			{"quest", "quest needs a name"},
			{"Agility 5)", "unexpected ')'"},
			{"coins lots", "coins needs a number"},
			{"Agility 99999999999", "number too big"},
			{"house of cards", "house needs nothing"},
		};
		for (String[] b : bad)
		{
			RequirementText.ParseException e = assertThrows(RequirementText.ParseException.class, () -> parse(b[0]), b[0]);
			assertTrue(e.getMessage().contains(b[1]), b[0] + " -> " + e.getMessage());
		}
		assertEquals(11, assertThrows(RequirementText.ParseException.class, () -> parse("Agility 5; Stength 4")).position);
	}

	@Test
	void skillsUseTheRealLevel()
	{
		PlayerState p = new PlayerState.Builder("p").members(true).level(Skill.AGILITY, 60).build();
		assertEquals(Outcome.NOT_MET, outcome("Agility 61", p));
		assertEquals(Outcome.MET, outcome("Agility 60", p));
		assertEquals(List.of("Agility 61 (has 60)"), parse("Agility 61").check(p).getMissing());
		assertEquals(Outcome.MET, outcome("Hitpoints 10", PlayerStateBuilder.fresh()));
	}

	@Test
	void membersSkillsNeedMembers()
	{
		PlayerState f2p = new PlayerState.Builder("f2p").level(Skill.AGILITY, 99).level(Skill.MINING, 60).build();
		assertEquals(List.of("members (Agility is a members skill)"), parse("Agility 1").check(f2p).getMissing());
		assertEquals(Outcome.MET, outcome("Mining 60", f2p));
		assertEquals(Outcome.MET, outcome("Agility 1", PlayerStateBuilder.fresh()));
	}

	@Test
	void questStates()
	{
		PlayerState p = new PlayerState.Builder("p").quest("regicide", QuestState.STARTED).quest("Sea Slug", QuestState.DONE).build();
		assertEquals(Outcome.NOT_MET, outcome("quest Regicide", p));
		assertEquals(Outcome.MET, outcome("quest Regicide started", p));
		assertEquals(Outcome.MET, outcome("quest Regicide not done", p));
		assertEquals(Outcome.NOT_MET, outcome("quest Regicide not started", p));
		assertEquals(Outcome.MET, outcome("quest sea slug", p));
		assertEquals(Outcome.MET, outcome("quest Cabin Fever not started", p));
		assertEquals(Outcome.MET, outcome("quest Cabin Fever", PlayerStateBuilder.maxed()));
		assertEquals(Outcome.NOT_MET, outcome("quest Cabin Fever not started", PlayerStateBuilder.maxed()));
	}

	@Test
	void varsCompareAndMaxedHasEveryUnlock()
	{
		PlayerState p = new PlayerState.Builder("p").varbit(1990, 40).varp(139, 6).varbit(4458, 6).build();
		assertEquals(Outcome.MET, outcome("varbit 1990 > 39", p));
		assertEquals(Outcome.NOT_MET, outcome("varp 139 > 6", p));
		assertEquals(Outcome.MET, outcome("varp 139 <= 6", p));
		assertEquals(Outcome.MET, outcome("varbit 4458 & 4", p));
		assertEquals(Outcome.NOT_MET, outcome("varbit 4458 & 1", p));
		assertEquals(Outcome.MET, outcome("varbit 777 = 0", p));
		assertEquals(Outcome.NOT_MET, outcome("varbit 777 != 0", p));
		assertEquals(Outcome.MET, outcome("varbit 777 = 5", PlayerStateBuilder.maxed()));
		assertEquals(Outcome.NOT_MET, outcome("varbit 777 = 5", PlayerStateBuilder.fresh()));
	}

	@Test
	void itemsCarriedWornBankedOrMissing()
	{
		PlayerState p = new PlayerState.Builder("p").carry(1349, 1).bank(4531, 1).bank(33, 1).wear(EquipSlot.RING, 6465).build();
		assertEquals(Outcome.MET, outcome("item axe", p));
		assertEquals(Outcome.NEEDS_BANK, outcome("item light source", p));
		assertEquals(List.of("light source from the bank"), parse("item light source").check(p).getMissing());
		assertEquals(Outcome.NEEDS_BANK, outcome("item light source x2", p));
		assertEquals(Outcome.NOT_MET, outcome("item light source x3", p));
		assertEquals(Outcome.MET, outcome("item 6465 worn", p));
		assertEquals(List.of("wear axe"), parse("item axe worn").check(p).getTodo());
		assertEquals(Outcome.NOT_MET, outcome("item 954", p));
		assertEquals(Outcome.MET, outcome("item 954 x5", PlayerStateBuilder.maxed()));
	}

	@Test
	void coinsCarriedOrBanked()
	{
		PlayerState p = new PlayerState.Builder("p").carry(PlayerState.COINS, 100).bank(PlayerState.COINS, 1000).build();
		assertEquals(Outcome.MET, outcome("coins 30", p));
		assertEquals(Outcome.NEEDS_BANK, outcome("coins 200", p));
		assertEquals(Outcome.NOT_MET, outcome("coins 100000", p));
		assertEquals(List.of("coins 100000 (has 1100)"), parse("coins 100000").check(p).getMissing());
	}

	@Test
	void membersEmptySlotAndAnswers()
	{
		PlayerState f2p = new PlayerState.Builder("f2p").wear(EquipSlot.HEAD, 1153).answer("Battered door AIR rune", true).build();
		assertEquals(Outcome.NOT_MET, outcome("members", f2p));
		assertEquals(Outcome.MET, outcome("members", PlayerStateBuilder.fresh()));
		Check slot = parse("empty head; empty cape").check(f2p);
		assertEquals(Outcome.MET, slot.getOutcome());
		assertEquals(List.of("take off your head item"), slot.getTodo());
		assertEquals(Outcome.MET, outcome("answer battered door air rune", f2p));
		assertEquals(Outcome.NOT_MET, outcome("answer battered door fire rune", f2p));
		assertEquals(Outcome.MET, outcome("answer anything", PlayerStateBuilder.maxed()));
	}

	@Test
	void allTakesTheWorstAndOneOfTheBest()
	{
		PlayerState p = new PlayerState.Builder("p").members(true).level(Skill.AGILITY, 56).bank(1351, 1).quest("Regicide", QuestState.STARTED)
			.varbit(1990, 40).build();
		Check c = parse("Agility 56; one of (quest Regicide | varbit 1990 > 39); item axe").check(p);
		assertEquals(Outcome.NEEDS_BANK, c.getOutcome());
		assertEquals(List.of("axe from the bank"), c.getMissing());

		Check no = parse("Agility 60; item axe; one of (quest Regicide | varbit 1990 > 45)").check(p);
		assertEquals(Outcome.NOT_MET, no.getOutcome());
		assertEquals(List.of("Agility 60 (has 56)", "one of: quest Regicide or varbit 1990 > 45 (is 40)"), no.getMissing());

		assertEquals(Outcome.NEEDS_BANK, outcome("one of (item 954 | item axe)", p));
		assertEquals(Outcome.MET, outcome("one of (item axe | Agility 1)", p));
	}

	@Test
	void visitSeesEveryPart()
	{
		List<String> seen = new ArrayList<>();
		parse("Agility 56; one of (quest Regicide | item axe)").visit(r ->
		{
			if (r instanceof Requirement.Item)
			{
				seen.add("item " + ((Requirement.Item) r).getItemIds().length);
			}
			else if (r instanceof Requirement.Quest)
			{
				seen.add(((Requirement.Quest) r).getQuest());
			}
		});
		assertEquals(List.of("Regicide", "item 3"), seen);
	}

	@Test
	void stateIsACopy()
	{
		PlayerState.Builder b = new PlayerState.Builder("p").carry(1351, 1);
		PlayerState p = b.build();
		b.carry(1351, 5);
		assertEquals(1, p.carried(1351));
		assertEquals(10, p.level(Skill.HITPOINTS));
	}

	@Test
	void theHouse()
	{
		PlayerState none = PlayerStateBuilder.fresh();
		PlayerState p = new PlayerState.Builder("p").house("Yanille").houseThing("Ornate jewellery box").houseThing("fairy ring").build();
		assertEquals(Outcome.NOT_MET, outcome("house", none));
		assertEquals(Outcome.NOT_MET, outcome("house has fairy ring", none));
		assertEquals(Outcome.MET, outcome("house", p));
		assertEquals(Outcome.MET, outcome("house at yanille", p));
		assertEquals(Outcome.NOT_MET, outcome("house at Rimmington", p));
		assertEquals(Outcome.MET, outcome("house has ornate jewellery box", p));
		assertEquals(Outcome.NOT_MET, outcome("house has spirit tree", p));
		assertEquals(List.of("house has spirit tree"), parse("house has spirit tree").check(p).getMissing());
		assertEquals(Outcome.MET, outcome("house at Rimmington; house has spirit tree", PlayerStateBuilder.maxed()));
		// things built count only with a house
		PlayerState noHouse = new PlayerState.Builder("q").houseThing("fairy ring").build();
		assertEquals(Outcome.NOT_MET, outcome("house has fairy ring", noHouse));
		assertEquals("Yanille", p.getHouse());
	}

	/** Air: air rune 556, dust 4696, smoke 4697; staves: air staff 1381, smoke battlestaff 11998 (test ids). */
	private static final RequirementText.ItemGroups RUNES = new RequirementText.ItemGroups()
	{
		@Override
		public int[] ids(String name)
		{
			return null;
		}

		@Override
		public RequirementText.RuneSource rune(String name)
		{
			return name.equalsIgnoreCase("air") ? new RequirementText.RuneSource(new int[]{556, 4696, 4697}, new int[]{1381, 11998}) : null;
		}
	};

	@Test
	void spellbookPouchAndRunes()
	{
		assertEquals("spellbook ancient; rune air x3", RequirementText.parse("Spellbook Ancient; rune air x3", RUNES).toText());
		assertThrows(RequirementText.ParseException.class, () -> RequirementText.parse("rune fire x1", RUNES));
		assertThrows(RequirementText.ParseException.class, () -> RequirementText.parse("spellbook necromancy", RUNES));
		assertThrows(RequirementText.ParseException.class, () -> RequirementText.parse("rune air", RUNES));

		Requirement air3 = RequirementText.parse("rune air x3", RUNES);
		Requirement ancient = RequirementText.parse("spellbook ancient", RUNES);
		assertEquals(Outcome.NOT_MET, ancient.check(PlayerStateBuilder.fresh()).getOutcome());
		assertEquals(Outcome.MET, ancient.check(new PlayerState.Builder("a").spellbook(Spellbook.ANCIENT).build()).getOutcome());
		assertEquals(Outcome.MET, ancient.check(PlayerStateBuilder.maxed()).getOutcome());

		assertEquals(Outcome.MET, air3.check(new PlayerState.Builder("combo").carry(556, 1).carry(4697, 2).build()).getOutcome());
		assertEquals(Outcome.MET, air3.check(new PlayerState.Builder("staff").wear(EquipSlot.WEAPON, 11998).build()).getOutcome());
		Check carriedStaff = air3.check(new PlayerState.Builder("staff in bag").carry(1381, 1).build());
		assertEquals(Outcome.MET, carriedStaff.getOutcome());
		assertEquals(List.of("wield a staff for air runes"), carriedStaff.getTodo());
		assertEquals(Outcome.NEEDS_BANK, air3.check(new PlayerState.Builder("bank").bank(556, 10).build()).getOutcome());
		assertEquals(Outcome.NOT_MET, air3.check(new PlayerState.Builder("two").carry(556, 2).build()).getOutcome());

		// the rune pouch: carried with the player, or in the bank
		PlayerState pouch = new PlayerState.Builder("pouch").pouch(4696, 50).build();
		assertEquals(Outcome.MET, air3.check(pouch).getOutcome());
		assertEquals(50, pouch.carried(4696));
		PlayerState pouchBanked = new PlayerState.Builder("pouch banked").pouch(4696, 50).pouchInBank(true).build();
		assertEquals(Outcome.NEEDS_BANK, air3.check(pouchBanked).getOutcome());
		assertEquals(0, pouchBanked.carried(4696));
		assertEquals(50, pouchBanked.banked(4696));
	}

	@Test
	void combatLevelAndNot()
	{
		assertEquals(3, PlayerStateBuilder.fresh().combatLevel());
		assertEquals(126, PlayerStateBuilder.maxed().combatLevel());
		// Wiki example: Hitpoints 60, Defence 70, Prayer 43 -> base 37.75; Attack 60 + Strength 60 -> 39; total 76
		PlayerState p = new PlayerState.Builder("p").level(Skill.HITPOINTS, 60).level(Skill.DEFENCE, 70).level(Skill.PRAYER, 43)
			.level(Skill.ATTACK, 60).level(Skill.STRENGTH, 60).build();
		assertEquals(76, p.combatLevel());
		assertEquals(Outcome.MET, outcome("Combat 76", p));
		assertEquals(Outcome.NOT_MET, outcome("Combat 77", p));
		assertEquals(List.of("Combat 77 (has 76)"), parse("Combat 77").check(p).getMissing());

		PlayerState prif = new PlayerState.Builder("prif").answer("respawn point is Prifddinas", true).build();
		assertEquals(Outcome.NOT_MET, outcome("not answer respawn point is Prifddinas", prif));
		assertEquals(Outcome.MET, outcome("not answer respawn point is Prifddinas", PlayerStateBuilder.fresh()));
		assertEquals(Outcome.MET, outcome("not one of (Agility 50 | quest Regicide)", PlayerStateBuilder.fresh()));
		assertEquals("varbit 668 = 0; not answer respawn point is Prifddinas",
			parse("varbit 668 = 0; not answer respawn point is Prifddinas").toText());
		assertEquals("not one of (Agility 50 | quest Regicide)", parse("not one of (Agility 50 | quest Regicide)").toText());
		assertThrows(RequirementText.ParseException.class, () -> parse("Combat"));
	}
}
