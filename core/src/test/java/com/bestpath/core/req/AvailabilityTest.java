package com.bestpath.core.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.req.Check.Outcome;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Availability predicates: requirement lines checked against hand-built players. Met / needs the bank / not
 * met, with the reasons and the things to do on the way. No data files.
 */
class AvailabilityTest
{
	/** Groups: axe (bronze 1351, iron 1349, steel 1353); runes: air (air 556, dust 4696, smoke 4697; staves 1381, 11998). */
	private static final RequirementText.ItemGroups GROUPS = new RequirementText.ItemGroups()
	{
		private final RequirementText.ItemGroups items = RequirementText.ItemGroups.of(Map.of("axe", new int[]{1351, 1349, 1353}));

		@Override
		public int[] ids(String name)
		{
			return items.ids(name);
		}

		@Override
		public RequirementText.RuneSource rune(String name)
		{
			return name.equalsIgnoreCase("air") ? new RequirementText.RuneSource(new int[]{556, 4696, 4697}, new int[]{1381, 11998}) : null;
		}
	};

	private static Check check(String req, PlayerState p)
	{
		return RequirementText.parse(req, GROUPS).check(p);
	}

	private static Outcome outcome(String req, PlayerState p)
	{
		return check(req, p).getOutcome();
	}

	private static PlayerState.Builder member()
	{
		return new PlayerState.Builder("member").members(true);
	}

	private static PlayerState.Builder f2p()
	{
		return new PlayerState.Builder("f2p");
	}

	// ---- skills (real levels; boosts are not modelled: PlayerState holds real levels only) ----

	@Test
	void everySkillLevelMetOrNot()
	{
		for (Skill s : Skill.values())
		{
			PlayerState p = member().level(s, 50).build();
			assertEquals(Outcome.MET, outcome(s.getLabel() + " 50", p), s.getLabel());
			assertEquals(Outcome.MET, outcome(s.getLabel() + " 1", p), s.getLabel());
			Check no = check(s.getLabel() + " 51", p);
			assertEquals(Outcome.NOT_MET, no.getOutcome(), s.getLabel());
			assertEquals(List.of(s.getLabel() + " 51 (has 50)"), no.getMissing());
			assertTrue(no.getTodo().isEmpty());
		}
	}

	@Test
	void membersSkillsNeedMembersEvenWhenTheLevelIsThere()
	{
		for (Skill s : Skill.values())
		{
			PlayerState free = f2p().level(s, 99).build();
			if (s.isMembers())
			{
				assertEquals(List.of("members (" + s.getLabel() + " is a members skill)"), check(s.getLabel() + " 1", free).getMissing());
			}
			else
			{
				assertEquals(Outcome.MET, outcome(s.getLabel() + " 99", free), s.getLabel());
			}
		}
		// the free skills (Wiki Free-to-play): the 15 of the original free game
		long free = java.util.Arrays.stream(Skill.values()).filter(s -> !s.isMembers()).count();
		assertEquals(15, free);
		// wherever a members skill appears: inside "one of" another alternative can still be met
		PlayerState miner = f2p().level(Skill.MINING, 30).build();
		assertEquals(Outcome.MET, outcome("one of (Agility 1 | Mining 30)", miner));
		assertEquals(List.of("one of: members (Agility is a members skill) or Mining 31 (has 30)"),
			check("one of (Agility 1 | Mining 31)", miner).getMissing());
	}

	@Test
	void combatLevelFromMeleeRangedOrMagic()
	{
		// base (Defence 1 + Hitpoints 10 + Prayer 0) / 4 = 2.75; ranged floor(99 * 1.5) = 148 -> 13/40 * 148 = 48.1 -> 50
		assertEquals(50, member().level(Skill.RANGED, 99).build().combatLevel());
		assertEquals(50, member().level(Skill.MAGIC, 99).build().combatLevel());
		// melee Attack 99 + Strength 99 = 198 -> 64.35 + 2.75 -> 67
		assertEquals(67, member().level(Skill.ATTACK, 99).level(Skill.STRENGTH, 99).build().combatLevel());
		PlayerState mage = member().level(Skill.MAGIC, 99).build();
		assertEquals(Outcome.MET, outcome("Combat 50", mage));
		assertEquals(Outcome.NOT_MET, outcome("Combat 51", mage));
		// combat level is not a members thing
		assertEquals(Outcome.MET, outcome("Combat 3", f2p().build()));
	}

	// ---- quests ----

	@Test
	void everyQuestStateAgainstEveryWantedState()
	{
		// rows: player's state NOT_STARTED, STARTED, DONE; columns: (done), started, not started, not done
		String[] wanted = {"", " started", " not started", " not done"};
		boolean[][] met = {
			{false, false, true, true},
			{false, true, false, true},
			{true, true, false, false},
		};
		for (QuestState s : QuestState.values())
		{
			PlayerState p = member().quest("Regicide", s).build();
			for (int w = 0; w < wanted.length; w++)
			{
				Outcome expected = met[s.ordinal()][w] ? Outcome.MET : Outcome.NOT_MET;
				assertEquals(expected, outcome("quest Regicide" + wanted[w], p), s + " vs 'quest Regicide" + wanted[w] + "'");
			}
		}
		// a quest not set on the player is not started; names ignore case and outer spaces
		PlayerState none = member().quest("  sea SLUG ", QuestState.DONE).build();
		assertEquals(Outcome.MET, outcome("quest Sea Slug", none));
		assertEquals(Outcome.MET, outcome("quest Cabin Fever not started", none));
		assertEquals(List.of("quest Cabin Fever started"), check("quest Cabin Fever started", none).getMissing());
	}

	// ---- varbits / varps ----

	@Test
	void everyVarOperatorBelowAtAndAbove()
	{
		// {operator, met when the value is 38, 39, 40} against 39
		Object[][] table = {
			{"=", false, true, false},
			{"!=", true, false, true},
			{">", false, false, true},
			{">=", false, true, true},
			{"<", true, false, false},
			{"<=", true, true, false},
		};
		for (Object[] row : table)
		{
			for (int i = 0; i < 3; i++)
			{
				int value = 38 + i;
				String line = "varbit 1990 " + row[0] + " 39";
				PlayerState p = member().varbit(1990, value).build();
				Outcome expected = (Boolean) row[1 + i] ? Outcome.MET : Outcome.NOT_MET;
				assertEquals(expected, outcome(line, p), line + " with " + value);
				PlayerState q = member().varp(1990, value).build();
				assertEquals(expected, outcome("varp 1990 " + row[0] + " 39", q), "varp " + row[0] + " with " + value);
			}
		}
		assertEquals(List.of("varbit 1990 >= 39 (is 38)"), check("varbit 1990 >= 39", member().varbit(1990, 38).build()).getMissing());
	}

	@Test
	void bitsAndNegativeNumbers()
	{
		PlayerState p = member().varbit(10, 0b1010).build();
		assertEquals(Outcome.MET, outcome("varbit 10 & 2", p));
		assertEquals(Outcome.MET, outcome("varbit 10 & 8", p));
		assertEquals(Outcome.MET, outcome("varbit 10 & 3", p), "any of the bits");
		assertEquals(Outcome.NOT_MET, outcome("varbit 10 & 5", p));
		assertEquals(Outcome.NOT_MET, outcome("varbit 10 & 0", p), "no bits asked: never met");
		assertEquals(Outcome.MET, outcome("varbit 11 > -1", p), "unset = 0");
		assertEquals(Outcome.MET, outcome("varp 7 = -2", member().varp(7, -2).build()));
	}

	@Test
	void varbitsAndVarpsAreSeparate()
	{
		PlayerState p = member().varbit(5, 3).varp(5, 9).build();
		assertEquals(Outcome.MET, outcome("varbit 5 = 3", p));
		assertEquals(Outcome.MET, outcome("varp 5 = 9", p));
		assertEquals(Outcome.NOT_MET, outcome("varp 5 = 3", p));
		assertEquals(Outcome.NOT_MET, outcome("varbit 5 = 9", p));
	}

	@Test
	void allUnlockedMeetsEveryVarQuestAnswerAndHouse()
	{
		PlayerState all = new PlayerState.Builder("unlocked").allUnlocked(true).build();
		for (String op : new String[]{"=", "!=", ">", ">=", "<", "<=", "&"})
		{
			assertEquals(Outcome.MET, outcome("varbit 1 " + op + " 5", all), op);
			assertEquals(Outcome.MET, outcome("varp 1 " + op + " 5", all), op);
		}
		assertEquals(Outcome.MET, outcome("quest Dragon Slayer II", all));
		assertEquals(Outcome.MET, outcome("answer anything at all", all));
		assertEquals(Outcome.MET, outcome("house at Prifddinas; house has fairy ring; spellbook lunar", all));
		// but not members or items: those are separate switches
		assertEquals(Outcome.NOT_MET, outcome("members", all));
		assertEquals(Outcome.NOT_MET, outcome("item axe", all));
		assertEquals(Outcome.NOT_MET, outcome("coins 1", all));
	}

	// ---- items: carried, worn, bank (the bank detour rule), groups ----

	@Test
	void itemsCarriedWornOrBanked()
	{
		PlayerState carried = member().carry(1351, 1).build();
		PlayerState worn = member().wear(EquipSlot.WEAPON, 1349).build();
		PlayerState banked = member().bank(1353, 1).build();
		PlayerState none = member().carry(995, 1000).build();
		assertSame(Check.MET, check("item axe", carried));
		assertSame(Check.MET, check("item axe", worn), "a worn item counts as carried");
		Check bank = check("item axe", banked);
		assertEquals(Outcome.NEEDS_BANK, bank.getOutcome());
		assertEquals(List.of("axe from the bank"), bank.getMissing());
		assertTrue(!bank.isMet());
		Check no = check("item axe", none);
		assertEquals(Outcome.NOT_MET, no.getOutcome());
		assertEquals(List.of("item axe"), no.getMissing());
		// ids in place of a group
		assertEquals(Outcome.MET, outcome("item 1351/1349", carried));
		assertEquals(Outcome.NOT_MET, outcome("item 1349/1353", carried));
		assertEquals(Outcome.NEEDS_BANK, outcome("item 1349/1353", banked));
	}

	@Test
	void countsAddUpOverTheGroupAndOverTheBank()
	{
		PlayerState two = member().carry(1351, 1).wear(EquipSlot.WEAPON, 1349).bank(1353, 2).build();
		assertEquals(Outcome.MET, outcome("item axe x2", two), "one bronze carried + one iron worn");
		Check fetch = check("item axe x3", two);
		assertEquals(Outcome.NEEDS_BANK, fetch.getOutcome(), "2 carried + 2 banked");
		assertEquals(List.of("axe x3 from the bank"), fetch.getMissing());
		assertEquals(Outcome.NEEDS_BANK, outcome("item axe x4", two));
		assertEquals(List.of("item axe x5"), check("item axe x5", two).getMissing());
		// a stack
		assertEquals(Outcome.MET, outcome("item 954 x3", member().carry(954, 2).carry(954, 1).build()));
	}

	@Test
	void usedUpChecksLikeAnyOtherItem()
	{
		PlayerState rope = member().carry(954, 1).build();
		assertEquals(Outcome.MET, outcome("item 954 used up", rope));
		assertEquals(Outcome.NEEDS_BANK, outcome("item 954 used up", member().bank(954, 1).build()));
		assertEquals(Outcome.NOT_MET, outcome("item 954 x2 used up", rope));
	}

	@Test
	void wornItemsSayWearItWhenOnlyCarried()
	{
		Check c = check("item axe worn", member().carry(1351, 1).build());
		assertEquals(Outcome.MET, c.getOutcome());
		assertEquals(List.of("wear axe"), c.getTodo());
		assertEquals(Outcome.NEEDS_BANK, outcome("item axe worn", member().bank(1351, 1).build()));
		assertEquals(Outcome.NOT_MET, outcome("item axe worn", member().build()));
	}

	/**
	 * Fixed: "item X worn" used to add the to-do "wear X" even when the player already
	 * wore it (carried includes worn). Now the to-do comes only when fewer than the count are worn.
	 */
	@Test
	void alreadyWornNeedsNoToDo()
	{
		// worn and also one in the inventory: still nothing to do
		assertSame(Check.MET, check("item axe worn", member().wear(EquipSlot.WEAPON, 1353).carry(1351, 1).build()));
		// x2 worn with one worn and one carried: wear the second
		assertEquals(List.of("wear 1351/1349"), check("item 1351/1349 x2 worn",
			member().wear(EquipSlot.WEAPON, 1351).carry(1349, 1).build()).getTodo());
		assertSame(Check.MET, check("item 1351/1349 x2 worn",
			member().wear(EquipSlot.WEAPON, 1351).wear(EquipSlot.SHIELD, 1349).build()));
		// all items, already worn: nothing to do either
		assertSame(Check.MET, check("item axe worn", PlayerStateBuilder.allItems(new PlayerState.Builder("all"), true).wear(EquipSlot.WEAPON, 1351).build()));
		Check c = check("item axe worn", member().wear(EquipSlot.WEAPON, 1351).build());
		assertEquals(Outcome.MET, c.getOutcome());
		assertEquals(List.of(), c.getTodo());
	}

	@Test
	void allItemsCarriesEverything()
	{
		PlayerState all = PlayerStateBuilder.allItems(new PlayerState.Builder("all items"), true).build();
		assertEquals(Outcome.MET, outcome("item axe x99; coins 100000000; rune air x1000", all));
		assertEquals(List.of("wear axe"), check("item axe worn", all).getTodo());
		// but not quests or members
		assertEquals(Outcome.NOT_MET, outcome("quest Regicide", all));
	}

	@Test
	void coinsCarriedBankedOrShort()
	{
		PlayerState p = member().carry(PlayerState.COINS, 30).bank(PlayerState.COINS, 70).build();
		assertSame(Check.MET, check("coins 30", p));
		Check fetch = check("coins 31", p);
		assertEquals(Outcome.NEEDS_BANK, fetch.getOutcome());
		assertEquals(List.of("coins 31 from the bank"), fetch.getMissing());
		assertEquals(Outcome.NEEDS_BANK, outcome("coins 100", p));
		assertEquals(List.of("coins 101 (has 100)"), check("coins 101", p).getMissing());
		assertEquals(Outcome.MET, outcome("coins 0", member().build()));
		// coins are just item 995
		assertEquals(Outcome.MET, outcome("item 995 x30", p));
	}

	// ---- members, slots, answers, house, spellbook, runes ----

	@Test
	void membersOnFreeAndMembersAccounts()
	{
		assertEquals(Outcome.MET, outcome("members", member().build()));
		Check no = check("members", f2p().build());
		assertEquals(Outcome.NOT_MET, no.getOutcome());
		assertEquals(List.of("members"), no.getMissing());
		// a free account can use what needs nothing members-only
		assertEquals(Outcome.MET, outcome("Mining 1; coins 0; quest Cook's Assistant not started", f2p().build()));
	}

	@Test
	void emptySlotIsAlwaysMetWithAToDo()
	{
		for (EquipSlot s : EquipSlot.values())
		{
			assertSame(Check.MET, check("empty " + s.getLabel(), member().build()));
			Check c = check("empty " + s.getLabel(), member().wear(s, 1).build());
			assertEquals(Outcome.MET, c.getOutcome());
			assertEquals(List.of("take off your " + s.getLabel() + " item"), c.getTodo());
		}
		// another slot worn: nothing to do
		assertSame(Check.MET, check("empty head", member().wear(EquipSlot.FEET, 1).build()));
	}

	@Test
	void answersIgnoreCaseAndOuterSpaces()
	{
		PlayerState p = member().answer("  Respawn point is PRIFDDINAS ", true).build();
		assertEquals(Outcome.MET, outcome("answer respawn point is Prifddinas", p));
		assertEquals(Outcome.NOT_MET, outcome("answer respawn point is Lumbridge", p));
		assertEquals(List.of("answer respawn point is Lumbridge"), check("answer respawn point is Lumbridge", p).getMissing());
	}

	@Test
	void houseNeedsAHouseAndThePlace()
	{
		PlayerState p = member().house(" Rimmington ").houseThing("Mounted glory").build();
		assertEquals(Outcome.MET, outcome("house; house at RIMMINGTON; house has mounted GLORY", p));
		assertEquals(Outcome.NOT_MET, outcome("house at Yanille", p));
		assertEquals(Outcome.NOT_MET, outcome("house", member().house("  ").build()), "a blank place is no house");
	}

	@Test
	void everySpellbook()
	{
		for (Spellbook on : Spellbook.values())
		{
			PlayerState p = member().spellbook(on).build();
			for (Spellbook asked : Spellbook.values())
			{
				Check c = check("spellbook " + asked.getLabel(), p);
				assertEquals(on == asked ? Outcome.MET : Outcome.NOT_MET, c.getOutcome(), on + " vs " + asked);
				if (on != asked)
				{
					assertEquals(List.of("spellbook " + asked.getLabel() + " (is " + on.getLabel() + ")"), c.getMissing());
				}
			}
		}
		assertEquals(Outcome.MET, outcome("spellbook standard", member().build()), "standard by default");
	}

	@Test
	void runesFromInventoryPouchCombinationsAndStaves()
	{
		// air runes and dust runes add up
		assertEquals(Outcome.MET, outcome("rune air x5", member().carry(556, 2).carry(4696, 3).build()));
		// inventory and a carried rune pouch add up
		assertEquals(Outcome.MET, outcome("rune air x5", member().carry(556, 2).pouch(4697, 3).build()));
		// a pouch in the bank: fetch it
		Check fetch = check("rune air x5", member().carry(556, 2).pouch(4697, 3).pouchInBank(true).build());
		assertEquals(Outcome.NEEDS_BANK, fetch.getOutcome());
		assertEquals(List.of("air runes x5 from the bank"), fetch.getMissing());
		// a worn staff covers any number, with no runes at all
		assertSame(Check.MET, check("rune air x1000", member().wear(EquipSlot.WEAPON, 1381).build()));
		// a staff only in the bank: fetch it
		assertEquals(Outcome.NEEDS_BANK, outcome("rune air x1", member().bank(11998, 1).build()));
		// enough runes carried: no need to wield the carried staff
		assertSame(Check.MET, check("rune air x1", member().carry(556, 1).carry(1381, 1).build()));
		// short of runes, nothing anywhere
		assertEquals(List.of("rune air x3 (has 2)"), check("rune air x3", member().carry(556, 2).build()).getMissing());
	}

	// ---- one of / all / not ----

	@Test
	void oneOfTakesTheBestAlternativeInAnyOrder()
	{
		PlayerState p = member().bank(1351, 1).quest("Regicide", QuestState.DONE).build();
		for (String line : new String[]{"one of (item axe | quest Regicide)", "one of (quest Regicide | item axe)"})
		{
			assertSame(Check.MET, check(line, p), line);
		}
		for (String line : new String[]{"one of (item axe | Agility 99)", "one of (Agility 99 | item axe)"})
		{
			Check c = check(line, p);
			assertEquals(Outcome.NEEDS_BANK, c.getOutcome(), line);
			assertEquals(List.of("axe from the bank"), c.getMissing(), line);
		}
	}

	@Test
	void oneOfSaysWhichChoiceItUses()
	{
		Requirement.AnyOf toll = (Requirement.AnyOf) RequirementText.parse("one of (coins 10 | quest Prince Ali Rescue)", GROUPS);
		assertEquals(0, toll.chosen(member().carry(995, 10).quest("Prince Ali Rescue", QuestState.DONE).build()),
			"both met: the first");
		assertEquals(1, toll.chosen(member().quest("Prince Ali Rescue", QuestState.DONE).build()));
		assertEquals(0, toll.chosen(member().carry(995, 50).build()));
		assertEquals(1, toll.chosen(member().bank(995, 10).quest("Prince Ali Rescue", QuestState.DONE).build()),
			"met now beats met with the bank, as check() decides");
		assertEquals(0, toll.chosen(member().bank(995, 10).build()), "only with the bank: still that one");
		assertEquals(-1, toll.chosen(member().build()), "none met");
		assertEquals(0, toll.chosen(PlayerStateBuilder.maxed()));
	}

	@Test
	void nestedOneOf()
	{
		String line = "one of (one of (Agility 50 | Strength 50) | quest Regicide; coins 10)";
		assertEquals(Outcome.MET, outcome(line, member().level(Skill.STRENGTH, 50).build()));
		assertEquals(Outcome.MET, outcome(line, member().level(Skill.AGILITY, 50).build()));
		assertEquals(Outcome.MET, outcome(line, member().quest("Regicide", QuestState.DONE).carry(995, 10).build()));
		assertEquals(Outcome.NEEDS_BANK, outcome(line, member().quest("Regicide", QuestState.DONE).bank(995, 10).build()));
		Check no = check(line, member().quest("Regicide", QuestState.DONE).build());
		assertEquals(Outcome.NOT_MET, no.getOutcome());
		assertEquals(List.of("one of: one of: Agility 50 (has 1) or Strength 50 (has 1) or coins 10 (has 0)"), no.getMissing());
		// an alternative of several parts lists everything it lacks
		assertEquals(List.of("one of: Agility 50 (has 1), quest Regicide or Strength 60 (has 1)"),
			check("one of (Agility 50; quest Regicide | Strength 60)", member().build()).getMissing());
	}

	@Test
	void allTakesTheWorstButKeepsEveryToDo()
	{
		PlayerState p = member().wear(EquipSlot.HEAD, 1).carry(1351, 1).bank(995, 50).build();
		Check c = check("empty head; item axe worn; coins 50", p);
		assertEquals(Outcome.NEEDS_BANK, c.getOutcome());
		assertEquals(List.of("coins 50 from the bank"), c.getMissing());
		assertEquals(List.of("take off your head item", "wear axe"), c.getTodo());
		Check worse = check("empty head; coins 50; Agility 99", p);
		assertEquals(Outcome.NOT_MET, worse.getOutcome());
		assertEquals(List.of("Agility 99 (has 1)"), worse.getMissing(), "only the worst reasons");
	}

	@Test
	void notTurnsMetAndNotMetAround()
	{
		assertEquals(Outcome.MET, outcome("not quest Regicide", member().build()));
		assertEquals(Outcome.NOT_MET, outcome("not quest Regicide", member().quest("Regicide", QuestState.DONE).build()));
		assertEquals(List.of("not quest Regicide"), check("not quest Regicide", member().quest("Regicide", QuestState.DONE).build()).getMissing());
		assertEquals(Outcome.MET, outcome("not not members", member().build()));
		// today: "not" of something only in the bank counts as not met (the item is the player's)
		assertEquals(Outcome.NOT_MET, outcome("not item axe", member().bank(1351, 1).build()));
		// a members skill on a free account is not met, so its "not" is met
		assertEquals(Outcome.MET, outcome("not Agility 1", f2p().level(Skill.AGILITY, 99).build()));
	}

	@Test
	void maxedAndFreshPlayers()
	{
		PlayerState max = PlayerStateBuilder.maxed();
		PlayerState fresh = PlayerStateBuilder.fresh();
		String everything = "members; Agility 99; Sailing 99; Combat 126; quest Regicide; varbit 1 = 7; item axe x5; coins 1000000; "
			+ "answer x; house has fairy ring; spellbook arceuus; rune air x50; empty head";
		assertSame(Check.MET, check(everything, max));
		assertEquals(Outcome.NOT_MET, outcome(everything, fresh));
		assertTrue(fresh.isMembers());
		assertEquals(Outcome.MET, outcome("members; Agility 1; Hitpoints 10; Combat 3; quest Regicide not started; varbit 1 = 0", fresh));
		// Maxed has every answer, so "not answer" is never met for it
		assertEquals(Outcome.NOT_MET, outcome("not answer respawn point is Prifddinas", max));
	}
}
