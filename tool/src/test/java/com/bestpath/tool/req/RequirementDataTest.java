package com.bestpath.tool.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.req.Check;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.tool.edit.TransportRecord;
import com.google.gson.Gson;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequirementDataTest
{
	@Test
	void oldRecordsHaveNoRequirements()
	{
		TransportRecord r = new Gson().fromJson("{\"id\":\"t-1\",\"kind\":\"TWO_WAY\",\"name\":\"Rocks\",\"ticks\":4}", TransportRecord.class);
		assertEquals("", r.requirements);
		assertNull(r.requirementsBack);
		assertFalse(r.questOnly);
		assertFalse(r.canFail);
		assertEquals("", r.requirementNote());
	}

	@Test
	void requirementsAreCopiedAndShown()
	{
		TransportRecord r = new TransportRecord();
		r.name = "Vines";
		r.requirements = "item axe; Woodcutting 22";
		r.requirementsBack = "";
		r.canFail = true;
		TransportRecord c = r.copy();
		assertEquals("item axe; Woodcutting 22", c.requirements);
		assertEquals("", c.requirementsEndToStart());
		assertTrue(c.canFail);
		assertEquals(" needs: item axe; Woodcutting 22 | back: nothing [can fail]", c.requirementNote());
		c.requirementsBack = null;
		assertEquals("item axe; Woodcutting 22", c.requirementsEndToStart());
		TransportRecord back = new Gson().fromJson(new Gson().toJson(r), TransportRecord.class);
		assertEquals("", back.requirementsBack);
	}

	@Test
	void lightSourcesFromTheGroupFile()
	{
		File f = new File("../data/manual/item-groups.tsv");
		if (!f.isFile())
		{
			f = new File("data/manual/item-groups.tsv");
		}
		Map<String, int[]> groups = ItemGroupFile.read(f);
		assertTrue(groups.containsKey("light source"), groups.keySet().toString());
		assertTrue(groups.containsKey("safe light source"));
		assertTrue(groups.get("light source").length > groups.get("safe light source").length);
		RequirementText.ItemGroups lookup = RequirementText.ItemGroups.of(groups);

		Requirement dark = RequirementText.parse("item light source", lookup);
		Requirement swamp = RequirementText.parse("item safe light source", lookup);
		PlayerState torch = new PlayerState.Builder("torch").carry(594, 1).build();
		PlayerState helmet = new PlayerState.Builder("helmet").wear(EquipSlot.HEAD, 5013).build();
		PlayerState nothing = new PlayerState.Builder("nothing").carry(596, 1).build(); // an unlit torch
		assertTrue(dark.check(torch).isMet());
		assertEquals(Check.Outcome.NOT_MET, swamp.check(torch).getOutcome());
		assertTrue(dark.check(helmet).isMet());
		assertTrue(swamp.check(helmet).isMet());
		assertEquals(Check.Outcome.NOT_MET, dark.check(nothing).getOutcome());
	}

	@Test
	void missingFileGivesNoGroups()
	{
		assertTrue(ItemGroupFile.read(new File("no-such-file.tsv")).isEmpty());
	}

	@Test
	void runesFromTheRuneFile()
	{
		assertTrue(ItemGroupFile.runeKinds().containsAll(java.util.List.of("air", "water", "earth", "fire", "law", "astral", "soul")));
		Requirement varrock = ItemGroupFile.parse("rune air x3; rune fire x1; rune law x1; Magic 25");
		PlayerState runes = new PlayerState.Builder("runes").level(com.bestpath.core.req.Skill.MAGIC, 25)
			.carry(556, 3).carry(554, 1).carry(563, 1).build();
		assertTrue(varrock.check(runes).isMet());
		// a smoke battlestaff (air and fire) worn, laws in the rune pouch
		PlayerState staff = new PlayerState.Builder("staff").level(com.bestpath.core.req.Skill.MAGIC, 25)
			.wear(EquipSlot.WEAPON, 11998).pouch(563, 10).build();
		assertTrue(varrock.check(staff).isMet());
		// steam runes give water and fire
		assertTrue(ItemGroupFile.parse("rune water x2; rune fire x2").check(new PlayerState.Builder("steam").carry(4694, 2).build()).isMet());
		assertEquals(Check.Outcome.NOT_MET, ItemGroupFile.parse("rune law x1").check(PlayerStateBuilder.fresh()).getOutcome());
	}
}
