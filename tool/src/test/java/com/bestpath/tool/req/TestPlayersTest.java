package com.bestpath.tool.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateDiagnostics;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestPlayersTest
{
	@Test
	void savedPlayersComeBackAfterTheBuiltInOnes() throws Exception
	{
		File f = File.createTempFile("test-players", ".json");
		f.deleteOnExit();
		TestPlayer p = new TestPlayer();
		p.name = "Mid account";
		p.levels.put("Agility", 61);
		p.quests.put("Regicide", "started");
		p.varbits.put("1990", 40);
		p.inventory.put("1351", 1);
		p.worn.put("head", 5013);
		p.bank.put("995", 5000);
		p.answers.add("battered door air rune");
		p.house = "Yanille";
		p.houseThings.add("ornate jewellery box");
		p.houseThings.add("portal nexus: Varrock Teleport");
		p.spellbook = "lunar";
		p.pouch.put("9075", 100);
		List<TestPlayer> all = TestPlayers.load(f);
		assertEquals(2, all.size());
		all.add(p);
		TestPlayers.save(f, all);
		assertTrue(Files.readString(f.toPath()).contains("\"Mid account\""));
		assertTrue(!Files.readString(f.toPath()).contains("\"Maxed\""));

		List<TestPlayer> back = TestPlayers.load(f);
		assertEquals(List.of("Maxed", "Fresh account", "Mid account"), List.of(back.get(0).name, back.get(1).name, back.get(2).name));
		PlayerState s = back.get(2).toState();
		assertEquals(61, s.level(Skill.AGILITY));
		assertEquals(10, s.level(Skill.HITPOINTS));
		assertEquals(QuestState.STARTED, s.quest("regicide"));
		assertEquals(40, s.varbit(1990));
		assertEquals(1, s.carried(1351));
		assertEquals(5013, s.worn(EquipSlot.HEAD));
		assertEquals(5000, s.banked(995));
		assertTrue(PlayerStateDiagnostics.hasAnswer(s, "Battered door air rune"));
		assertTrue(s.isMembers());
		assertTrue(s.houseAt("Yanille"));
		assertTrue(s.houseHas("Portal nexus: Varrock Teleport"));
		assertTrue(!s.houseHas("fairy ring"));
		assertEquals(com.bestpath.core.req.Spellbook.LUNAR, s.getSpellbook());
		assertEquals(100, s.carried(9075));
		assertTrue(back.get(0).toState().isAllUnlocked());
		assertEquals(99, back.get(0).toState().level(Skill.SAILING));
	}

	@Test
	void badEntriesAreRefused()
	{
		TestPlayer p = new TestPlayer();
		p.levels.put("Agilty", 5);
		assertThrows(IllegalArgumentException.class, p::toState);
		TestPlayer q = new TestPlayer();
		q.quests.put("Regicide", "half");
		assertThrows(IllegalArgumentException.class, q::toState);
	}

	@Test
	void houseListFromTheDataFile()
	{
		assertEquals(9, HouseThings.places().size());
		assertTrue(HouseThings.isPlace("prifddinas"));
		assertTrue(HouseThings.isThing("Ornate jewellery box"));
		assertTrue(HouseThings.isThing("portal chamber: Varrock Teleport"));
		assertTrue(HouseThings.isThing("portal nexus: Ghorrock Teleport"));
		assertEquals("ornate jewellery box", HouseThings.canonical("ORNATE jewellery box"));
		assertEquals(List.of("house place 'Yanile'", "house thing 'gold toilet'"),
			CacheNames.unknownNames(ItemGroupFile.parse("house at Yanile; house has gold toilet; house has fairy ring")));
	}
}
