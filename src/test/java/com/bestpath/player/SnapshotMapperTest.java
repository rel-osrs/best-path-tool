package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateDiagnostics;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.RequirementText;
import com.bestpath.core.req.Skill;
import com.bestpath.core.req.Spellbook;
import com.bestpath.route.PlayerSnapshot;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Snapshot mapping with made-up game readings (no client). */
public class SnapshotMapperTest
{
	private static final int LAW = 563;
	private static final int AIR = 556;
	private static final int FIRE = 554;
	private static final int VARROCK_TAB = 8007;

	private static GameReading reading()
	{
		GameReading r = new GameReading();
		r.x = 3222;
		r.y = 3218;
		r.plane = 1;
		return r;
	}

	private static Check check(PlayerState p, String line)
	{
		return RequirementText.parse(line, RequirementText.ItemGroups.NONE).check(p);
	}

	@Test
	public void positionLevelsAndMembersAreCopied()
	{
		GameReading r = reading();
		r.levels[Skill.AGILITY.ordinal()] = 61;
		r.levels[Skill.SAILING.ordinal()] = 12;
		r.members = true;
		PlayerSnapshot s = SnapshotMapper.toSnapshot(r);
		assertEquals(3222, s.getX());
		assertEquals(3218, s.getY());
		assertEquals(1, s.getPlane());
		assertEquals(61, s.getState().level(Skill.AGILITY));
		assertEquals(12, s.getState().level(Skill.SAILING));
		assertEquals(10, s.getState().level(Skill.HITPOINTS));
		assertEquals(1, s.getState().level(Skill.MAGIC));
		assertTrue(s.getState().isMembers());
		assertTrue(check(s.getState(), "Agility 61").isMet());
		assertFalse(check(s.getState(), "Agility 62").isMet());
	}

	@Test
	public void freeWorldMakesMembersSkillsUnmet()
	{
		GameReading r = reading();
		r.levels[Skill.AGILITY.ordinal()] = 99;
		r.members = false;
		PlayerState p = SnapshotMapper.toSnapshot(r).getState();
		assertFalse(p.isMembers());
		assertFalse(check(p, "Agility 10").isMet());
		assertFalse(check(p, "members").isMet());
	}

	@Test
	public void inventoryWornAndBank()
	{
		GameReading r = reading();
		r.inventory.put(VARROCK_TAB, 3);
		r.inventory.put(995, 25);
		r.inventory.put(0, 5); // an empty slot never counts
		r.worn[EquipSlot.RING.ordinal()] = 2552; // ring of dueling(8)
		r.bank = new HashMap<>(Map.of(VARROCK_TAB, 10, 995, 1000));
		PlayerState p = SnapshotMapper.toSnapshot(r).getState();
		assertEquals(3, p.carried(VARROCK_TAB));
		assertEquals(10, p.banked(VARROCK_TAB));
		assertEquals(2552, p.worn(EquipSlot.RING));
		assertEquals(-1, p.worn(EquipSlot.HEAD));
		assertEquals(1, p.carried(2552));
		assertTrue(check(p, "coins 20").isMet());
		assertEquals(Check.Outcome.NEEDS_BANK, check(p, "coins 500").getOutcome());
		assertEquals(Check.Outcome.NEEDS_BANK, check(p, "item 8007 x5").getOutcome());
		assertTrue(check(p, "item 2552 worn").isMet());
	}

	@Test
	public void bankNeverSeenIsEmpty()
	{
		GameReading r = reading();
		r.bank = null;
		PlayerState p = SnapshotMapper.toSnapshot(r).getState();
		assertTrue(PlayerStateDiagnostics.bank(p).isEmpty());
		assertEquals(Check.Outcome.NOT_MET, check(p, "item 8007").getOutcome());
	}

	@Test
	public void onlyTheGivenVarsAndQuests()
	{
		GameReading r = reading();
		r.varbits.put(3337, 17);
		r.varps.put(176, 7);
		r.quests.put("Prince Ali Rescue", QuestState.DONE);
		r.quests.put("dragon slayer i", QuestState.STARTED);
		PlayerState p = SnapshotMapper.toSnapshot(r).getState();
		assertEquals(17, p.varbit(3337));
		assertEquals(0, p.varbit(3338));
		assertEquals(7, p.varp(176));
		assertEquals(QuestState.DONE, p.quest("prince ali rescue"));
		assertEquals(QuestState.STARTED, p.quest("Dragon Slayer I"));
		assertEquals(QuestState.NOT_STARTED, p.quest("Regicide"));
		assertTrue(check(p, "varbit 3337 >= 17; varp 176 = 7; quest Prince Ali Rescue").isMet());
	}

	@Test
	public void spellbookValues()
	{
		assertEquals(Spellbook.STANDARD, SnapshotMapper.spellbook(0));
		assertEquals(Spellbook.ANCIENT, SnapshotMapper.spellbook(1));
		assertEquals(Spellbook.LUNAR, SnapshotMapper.spellbook(2));
		assertEquals(Spellbook.ARCEUUS, SnapshotMapper.spellbook(3));
		assertEquals(Spellbook.STANDARD, SnapshotMapper.spellbook(7));
		GameReading r = reading();
		r.spellbookValue = 2;
		PlayerState p = SnapshotMapper.toSnapshot(r).getState();
		assertTrue(check(p, "spellbook lunar").isMet());
		assertFalse(check(p, "spellbook ancient").isMet());
	}

	@Test
	public void houseLocationValues()
	{
		assertNull(SnapshotMapper.housePlace(0));
		assertEquals("Rimmington", SnapshotMapper.housePlace(1));
		assertEquals("Taverley", SnapshotMapper.housePlace(2));
		assertEquals("Yanille", SnapshotMapper.housePlace(6));
		assertNull(SnapshotMapper.housePlace(7)); // not in the table: unknown
		assertEquals("Hosidius", SnapshotMapper.housePlace(8));
		assertEquals("Aldarin", SnapshotMapper.housePlace(13));
		assertNull(SnapshotMapper.housePlace(14));
		assertNull(SnapshotMapper.housePlace(-1));
		GameReading r = reading();
		r.houseValue = 6;
		PlayerState p = SnapshotMapper.toSnapshot(r).getState();
		assertTrue(check(p, "house at Yanille").isMet());
		assertTrue(check(p, "house").isMet());
		assertFalse(check(p, "house has ornate jewellery box").isMet()); // what is built is not known
		r.houseValue = 0;
		assertFalse(check(SnapshotMapper.toSnapshot(r).getState(), "house").isMet());
	}

	@Test
	public void runePouchCountsOnlyWhileCarried()
	{
		GameReading r = reading();
		r.pouchItems[0] = LAW;
		r.pouchCounts[0] = 50;
		r.pouchItems[1] = AIR;
		r.pouchCounts[1] = 0; // an emptied slot
		r.pouchItems[3] = FIRE;
		r.pouchCounts[3] = 200;
		r.inventory.put(ItemID.DIVINE_RUNE_POUCH, 1);
		PlayerState p = SnapshotMapper.toSnapshot(r).getState();
		assertFalse(p.isPouchInBank());
		assertEquals(50, p.carried(LAW));
		assertEquals(0, p.carried(AIR));
		assertEquals(200, p.carried(FIRE));
		assertEquals(Map.of(LAW, 50, FIRE, 200), PlayerStateDiagnostics.pouch(p));

		r.inventory.clear(); // the pouch is in the bank (its varbits still say what is in it)
		PlayerState banked = SnapshotMapper.toSnapshot(r).getState();
		assertTrue(banked.isPouchInBank());
		assertEquals(0, banked.carried(LAW));
		assertEquals(50, banked.banked(LAW));
	}

	@Test
	public void everyRunePouchKindCounts()
	{
		for (int pouch : SnapshotMapper.RUNE_POUCHES)
		{
			GameReading r = reading();
			r.pouchItems[0] = LAW;
			r.pouchCounts[0] = 1;
			r.inventory.put(pouch, 1);
			assertEquals("pouch " + pouch, 1, SnapshotMapper.toSnapshot(r).getState().carried(LAW));
		}
		assertTrue(SnapshotMapper.RUNE_POUCHES.contains(12791)); // the plain rune pouch
	}

	@Test
	public void answersAreCopied()
	{
		GameReading r = reading();
		r.answers.put("Spoke to the Oracle", true);
		r.answers.put("Planted a tree", false);
		PlayerState p = SnapshotMapper.toSnapshot(r).getState();
		assertTrue(check(p, "answer spoke to the oracle").isMet());
		assertTrue(check(p, "answer spoke to the oracle").getUnanswered().isEmpty());
		assertFalse(check(p, "answer planted a tree").isMet());
		assertTrue(check(p, "not answer planted a tree").isMet());
		// not answered yet does not stop a route; the check says the card must ask it
		assertTrue(p.isAskUnanswered());
		assertTrue(check(p, "answer something else").isMet());
		assertEquals(java.util.List.of("something else"), check(p, "answer something else").getUnanswered());
		// not answered counts as yes until the player says no
		assertFalse(check(p, "not answer something else").isMet());
	}

	@Test
	public void questStatesFromRuneLite()
	{
		assertEquals(QuestState.DONE, SnapshotMapper.questState(net.runelite.api.QuestState.FINISHED));
		assertEquals(QuestState.STARTED, SnapshotMapper.questState(net.runelite.api.QuestState.IN_PROGRESS));
		assertEquals(QuestState.NOT_STARTED, SnapshotMapper.questState(net.runelite.api.QuestState.NOT_STARTED));
		assertEquals(QuestState.NOT_STARTED, SnapshotMapper.questState(null));
	}
}
