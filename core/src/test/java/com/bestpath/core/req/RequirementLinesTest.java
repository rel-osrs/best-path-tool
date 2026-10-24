package com.bestpath.core.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.req.Check.Outcome;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reading requirement lines (docs/DATA_FORMATS.md, "Requirement line"): every kind of part, good and bad,
 * what each line reads back as, and the error message and position for each mistake. Hand-made item groups
 * and runes only (no data files).
 */
class RequirementLinesTest
{
	/** Groups: axe, light source; runes: air (air 556, dust 4696, smoke 4697; staves 1381, 11998), fire (554; staff 1387), nothing (no rune items). */
	private static final RequirementText.ItemGroups GROUPS = new RequirementText.ItemGroups()
	{
		private final RequirementText.ItemGroups items = RequirementText.ItemGroups.of(Map.of(
			"axe", new int[]{1351, 1349, 1353},
			"light source", new int[]{33, 4531}));

		@Override
		public int[] ids(String name)
		{
			return items.ids(name);
		}

		@Override
		public RequirementText.RuneSource rune(String name)
		{
			switch (name.trim().toLowerCase(java.util.Locale.ROOT))
			{
				case "air":
					return new RequirementText.RuneSource(new int[]{556, 4696, 4697}, new int[]{1381, 11998});
				case "fire":
					return new RequirementText.RuneSource(new int[]{554, 4697}, new int[]{1387, 11998});
				case "nothing":
					return new RequirementText.RuneSource(new int[0], new int[0]);
				default:
					return null;
			}
		}
	};

	private static Requirement parse(String s)
	{
		return RequirementText.parse(s, GROUPS);
	}

	/** Parses, checks the written form, and that the written form reads back as the same requirement. */
	private static void reads(String line, String written)
	{
		Requirement r = parse(line);
		assertEquals(written, r.toText(), line);
		assertEquals(r, parse(r.toText()), "read back: " + line);
		assertEquals(written, parse(written).toText(), "written form is stable: " + line);
	}

	private static RequirementText.ParseException bad(String line, String says)
	{
		RequirementText.ParseException e = assertThrows(RequirementText.ParseException.class, () -> parse(line), line);
		assertTrue(e.getMessage().contains(says), "'" + line + "' -> " + e.getMessage() + " (expected: " + says + ")");
		assertTrue(e.getMessage().endsWith("(at character " + (e.position + 1) + ")"), e.getMessage());
		return e;
	}

	// ---- every kind of part, good lines ----

	@Test
	void everySkillByItsName()
	{
		for (Skill s : Skill.values())
		{
			reads(s.getLabel() + " 61", s.getLabel() + " 61");
			reads(s.getLabel().toUpperCase(java.util.Locale.ROOT) + " 7", s.getLabel() + " 7");
			Requirement r = parse(s.getLabel().toLowerCase(java.util.Locale.ROOT) + " 1");
			assertInstanceOf(Requirement.SkillLevel.class, r);
			assertEquals(s, ((Requirement.SkillLevel) r).getSkill());
		}
		reads("Combat 40", "Combat 40");
		reads("combat 126", "Combat 126");
	}

	@Test
	void questsAndTheirStates()
	{
		reads("quest Regicide", "quest Regicide");
		reads("quest Regicide started", "quest Regicide started");
		reads("quest Regicide not started", "quest Regicide not started");
		reads("quest Regicide not done", "quest Regicide not done");
		reads("QUEST  Regicide   NOT DONE", "quest Regicide not done");
		reads("quest Recipe for Disaster (Lumbridge Guide)", "quest Recipe for Disaster (Lumbridge Guide)");
		Requirement.Quest q = (Requirement.Quest) parse("quest In Aid of the Myreque not started");
		assertEquals("In Aid of the Myreque", q.getQuest());
		assertEquals(Requirement.Quest.Wanted.NOT_STARTED, q.getWanted());
		// a quest name with brackets inside "one of"
		reads("one of (quest Recipe for Disaster (Lumbridge Guide) started | Agility 5)",
			"one of (quest Recipe for Disaster (Lumbridge Guide) started | Agility 5)");
	}

	@Test
	void varbitsAndVarpsWithEveryOperator()
	{
		String[][] ops = {{"=", "EQ"}, {"!=", "NE"}, {">", "GT"}, {">=", "GE"}, {"<", "LT"}, {"<=", "LE"}, {"&", "BITS"}};
		for (String[] o : ops)
		{
			for (String kind : new String[]{"varbit", "varp"})
			{
				String line = kind + " 1990 " + o[0] + " 39";
				reads(line, line);
				reads(kind + " 1990" + o[0] + "39", line);
				Requirement.Var v = (Requirement.Var) parse(line);
				assertEquals(kind.equals("varp"), v.isVarp());
				assertEquals(1990, v.getId());
				assertEquals(Requirement.Var.Op.valueOf(o[1]), v.getOp());
				assertEquals(39, v.getValue());
			}
		}
		reads("varbit 5 > -1", "varbit 5 > -1");
		reads("VARP 139 >= 7", "varp 139 >= 7");
	}

	@Test
	void itemsGroupsIdsAndOptions()
	{
		reads("item axe", "item axe");
		reads("item light source", "item light source");
		reads("item LIGHT SOURCE", "item LIGHT SOURCE");
		reads("item 1755", "item 1755");
		reads("item 1755/1756", "item 1755/1756");
		reads("item axe x2", "item axe x2");
		reads("item axe x1", "item axe");
		reads("item axe worn", "item axe worn");
		reads("item axe used up", "item axe used up");
		// options in any order are written in one order
		reads("item 954 used up worn x3", "item 954 x3 worn used up");
		reads("item light source worn x2", "item light source x2 worn");

		Requirement.Item i = (Requirement.Item) parse("item axe x2 worn");
		assertEquals("axe", i.getLabel());
		assertEquals(3, i.getItemIds().length);
		assertEquals(2, i.getCount());
		assertTrue(i.isWorn());
		Requirement.Item ids = (Requirement.Item) parse("item 1755/1756 used up");
		assertEquals(1755, ids.getItemIds()[0]);
		assertEquals(1756, ids.getItemIds()[1]);
		assertTrue(ids.isUsedUp());
	}

	@Test
	void coinsMembersSlotsAndAnswers()
	{
		reads("coins 200", "coins 200");
		reads("Coins 1,000,000", "coins 1000000");
		reads("coins 0", "coins 0");
		reads("members", "members");
		reads("MEMBERS", "members");
		for (EquipSlot s : EquipSlot.values())
		{
			reads("empty " + s.getLabel(), "empty " + s.getLabel());
			reads("Empty " + s.name(), "empty " + s.getLabel());
		}
		reads("answer battered door air rune", "answer battered door air rune");
		reads("answer   spaced out  ", "answer spaced out");
		assertEquals("battered door air rune", ((Requirement.Answer) parse("answer battered door air rune")).getAnswer());
	}

	@Test
	void houseSpellbookRunesCombatAndNot()
	{
		reads("house", "house");
		reads("house at Yanille", "house at Yanille");
		reads("House AT Rimmington", "house at Rimmington");
		reads("house has ornate jewellery box", "house has ornate jewellery box");
		reads("house has portal nexus: Varrock Teleport", "house has portal nexus: Varrock Teleport");
		for (Spellbook b : Spellbook.values())
		{
			reads("spellbook " + b.name(), "spellbook " + b.getLabel());
		}
		reads("rune air x3", "rune air x3");
		reads("Rune Fire x1", "rune Fire x1");
		Requirement.Runes r = (Requirement.Runes) parse("rune air x3");
		assertEquals(3, r.getCount());
		assertEquals(3, r.getRuneIds().length);
		assertEquals(2, r.getStaffIds().length);
		reads("not answer respawn point is Prifddinas", "not answer respawn point is Prifddinas");
		reads("NOT members", "not members");
		reads("not not members", "not not members");
		reads("not one of (Agility 50 | quest Regicide)", "not one of (Agility 50 | quest Regicide)");
	}

	@Test
	void allAndOneOfStructure()
	{
		reads("Agility 56; one of (quest Regicide | varbit 1990 > 39); item axe",
			"Agility 56; one of (quest Regicide | varbit 1990 > 39); item axe");
		reads("one of(Agility 5|Strength 6)", "one of (Agility 5 | Strength 6)");
		reads("ONE OF (Agility 5 | Strength 6)", "one of (Agility 5 | Strength 6)");
		// an alternative with several parts
		reads("one of (Agility 50; quest Regicide | Strength 60; coins 5)", "one of (Agility 50; quest Regicide | Strength 60; coins 5)");
		// nested one of
		reads("one of (one of (Agility 50 | Strength 50) | quest Regicide)", "one of (one of (Agility 50 | Strength 50) | quest Regicide)");
		reads("one of (Agility 5; one of (item axe | coins 5) | members)", "one of (Agility 5; one of (item axe | coins 5) | members)");
		// a single alternative is just that part
		reads("one of (Agility 5)", "Agility 5");
		// "all of" inside "all of" is flattened
		reads("one of (Agility 5; Strength 5); members", "Agility 5; Strength 5; members");
		Requirement.All all = (Requirement.All) parse("Agility 5; one of (members | coins 3); item axe");
		assertEquals(3, all.getParts().size());
		assertInstanceOf(Requirement.AnyOf.class, all.getParts().get(1));
		assertEquals(2, ((Requirement.AnyOf) all.getParts().get(1)).getAlternatives().size());
	}

	@Test
	void blankLinesNeedNothing()
	{
		for (String blank : new String[]{null, "", "   ", "\t"})
		{
			Requirement r = RequirementText.parse(blank, GROUPS);
			assertTrue(r.isNone());
			assertEquals(Outcome.MET, r.check(new PlayerState.Builder("f2p").build()).getOutcome());
		}
	}

	// ---- bad lines: each kind of mistake says what is wrong ----

	@Test
	void badSkillAndCombatLines()
	{
		bad("Agilty 61", "unknown part 'Agilty'");
		bad("Agility", "a skill needs a level, e.g. 'Agility 61'");
		bad("agility five", "a skill needs a level, e.g. 'Agility 61'");
		bad("Agility 5 6", "a skill needs a level");
		bad("Agility -5", "a skill needs a level");
		bad("Agility 99999999999", "number too big");
		bad("Combat", "combat needs a level");
		bad("Combat forty", "combat needs a level");
	}

	@Test
	void badQuestAndVarLines()
	{
		bad("quest", "quest needs a name");
		bad("quest    ", "quest needs a name");
		bad("varbit", "varbit needs '<id> <op> <number>', e.g. 'varbit 1990 > 39'");
		bad("varbit 1990", "varbit needs");
		bad("varbit 1990 39", "varbit needs");
		bad("varbit 1990 => 39", "varbit needs");
		bad("varbit 1990 == 39", "varbit needs");
		bad("varbit x > 39", "varbit needs");
		bad("varbit 1990 > x", "varbit needs");
		bad("varbit 1990 > 3 4", "varbit needs");
		bad("varp 139 7", "varp needs '<id> <op> <number>', e.g. 'varp 1990 > 39'");
		bad("varbit 99999999999 = 1", "number too big");
	}

	@Test
	void badItemLines()
	{
		bad("item", "item needs a group name or ids");
		bad("item x2", "item needs a group name or ids");
		bad("item worn", "item needs a group name or ids");
		bad("item spade", "unknown item group 'spade'");
		bad("item axe shiny", "unknown item group 'axe shiny'");
		bad("item axe x2 shiny", "unknown item option 'shiny' (x<count>, worn, used up)");
		bad("item axe used", "unknown item group 'axe used'");
		bad("item axe x0", "item count must be at least 1");
		bad("item 1755/", "unknown item group '1755/'");
		bad("item 1755//1756", "unknown item group");
		bad("item 99999999999", "number too big");
	}

	@Test
	void badCoinsMembersSlotAnswerHouseSpellbookRune()
	{
		bad("coins", "coins needs a number, e.g. 'coins 200'");
		bad("coins lots", "coins needs a number");
		bad("coins -5", "coins needs a number");
		bad("coins 5 gp", "coins needs a number");
		bad("members only", "'members' takes nothing after it");
		bad("empty", "unknown slot ''");
		bad("empty hat", "unknown slot 'hat' (head, cape, neck, weapon, body, shield, legs, hands, feet, ring, ammo)");
		bad("answer", "answer needs a name");
		bad("house of cards", "house needs nothing, 'at <place>' or 'has <thing>'");
		bad("house at", "house needs nothing");
		bad("house has", "house needs nothing");
		bad("spellbook", "unknown spellbook ''");
		bad("spellbook necromancy", "unknown spellbook 'necromancy' (standard, ancient, lunar, arceuus)");
		bad("rune air", "rune needs a kind and a count, e.g. 'rune air x3'");
		bad("rune x3", "rune needs a kind and a count");
		bad("rune air 3", "rune needs a kind and a count");
		bad("rune blood x1", "unknown rune 'blood'");
		bad("rune nothing x1", "unknown rune 'nothing'"); // a kind with no rune items
	}

	/**
	 * Fixed: "rune air x0" used to throw a plain IllegalArgumentException, which the
	 * Data Tool's requirement editor and RequirementFilter (they catch only ParseException) did not report as a
	 * mistake. Now a ParseException, like "item axe x0".
	 */
	@Test
	void runeCountZeroIsAParseError()
	{
		assertEquals(0, bad("rune air x0", "rune count must be at least 1").position);
		bad("members; rune air x00", "at least 1");
		bad("rune air x99999999999", "number too big");
	}

	@Test
	void badStructure()
	{
		bad(";", "empty part");
		bad("Agility 5;", "empty part");
		bad("; Agility 5", "empty part");
		bad("Agility 5;; members", "empty part");
		bad("Agility 5 | Strength 5", "unexpected '|'");
		bad("Agility 5)", "unexpected ')'");
		bad("one of", "'one of' needs '(' after it");
		bad("one of Agility 5", "'one of' needs '(' after it");
		bad("one of (", "empty part");
		bad("one of ()", "empty part");
		bad("one of (Agility 5 |)", "empty part");
		bad("one of (| Agility 5)", "empty part");
		bad("one of (Agility 5 | Strength 5", "'one of (' is not closed with ')'");
		bad("one of (Agility 5 | Strength 5; members", "not closed");
		bad("one of (Agility 5) )", "unexpected ')'");
		bad("not ", "empty part");
		bad("not", "unknown part 'not'");
	}

	@Test
	void errorPositionsPointAtTheBadPart()
	{
		assertEquals(0, bad("Agilty 5", "unknown part").position);
		assertEquals(11, bad("Agility 5; Stength 4", "unknown part 'Stength'").position);
		// "one of (Agility 5 | Stength 4)": the second alternative starts at character 21
		RequirementText.ParseException e = bad("one of (Agility 5 | Stength 4)", "unknown part 'Stength'");
		assertEquals(20, e.position);
		assertTrue(e.getMessage().endsWith("(at character 21)"), e.getMessage());
		// an unclosed "one of" points at the "one of"
		assertEquals(9, bad("members; one of (Agility 5", "not closed").position);
		// the stray character
		assertEquals(9, bad("Agility 5)", "unexpected ')'").position);
	}

	/**
	 * Fixed: "not one of (A; B)" means "not (A and B)" but used to be written back as
	 * "not A; B", which reads as "(not A) and B". The Data Tool tidies saved lines through toText(), so saving
	 * changed the meaning. A "not" of several parts is now written "not one of (A; B)".
	 */
	@Test
	void notOfSeveralPartsReadsBackTheSame()
	{
		Requirement r = parse("not one of (Agility 50; quest Regicide)");
		assertEquals("not one of (Agility 50; quest Regicide)", r.toText());
		Requirement back = parse(r.toText());
		// (equals() compares the written text, so it can't see this; compare the meaning)
		assertInstanceOf(Requirement.Not.class, back, "still one 'not' of both parts: " + r.toText());
		PlayerState agile = new PlayerState.Builder("agile").members(true).level(Skill.AGILITY, 50).build();
		assertEquals(Outcome.MET, r.check(agile).getOutcome(), "not (Agility 50 and Regicide): Regicide is missing, so met");
		assertEquals(r.check(agile).getOutcome(), back.check(agile).getOutcome());
		// a 'not' of one part, or of 'one of (A | B)', is written as before
		assertEquals("not quest Regicide", parse("not quest Regicide").toText());
		assertEquals("not one of (Agility 50 | quest Regicide)", parse("not one of (Agility 50 | quest Regicide)").toText());
		// 'not A; B' stays '(not A) and B'
		assertEquals("not Agility 50; quest Regicide", parse("not Agility 50; quest Regicide").toText());
		// a several-part 'not' inside an alternative also reads back the same
		String nested = "one of (members | not one of (Agility 50; quest Regicide))";
		assertEquals(nested, parse(nested).toText());
	}
}
