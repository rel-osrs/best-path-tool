package com.bestpath.core.req;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayerState#sameAs}: the transports a
 * player can use are worked out again only when the state changed, so every field must take part.
 */
class PlayerStateTest
{
	private static PlayerState.Builder base()
	{
		return new PlayerState.Builder("P").members(true).level(Skill.AGILITY, 50).quest("Priest in Peril", QuestState.DONE)
			.varbit(1, 2).varp(3, 4).carry(563, 10).wear(EquipSlot.CAPE, 9790).bank(995, 100).answer("Has a boat", true)
			.house("Rimmington").houseThing("spirit tree").spellbook(Spellbook.ANCIENT).pouch(1, 2).coolingDown(Cooldown.HOME, 5)
			.collectionLog(4151).spiritTree("Brimhaven");
	}

	@Test
	void theSameContentsAreTheSame()
	{
		assertTrue(base().build().sameAs(base().build()));
		PlayerState p = base().build();
		assertTrue(p.sameAs(p));
		assertFalse(p.sameAs(null));
	}

	@Test
	void anyDifferenceCounts()
	{
		PlayerState p = base().build();
		java.util.List<UnaryOperator<PlayerState.Builder>> changes = java.util.List.of(
			b -> b.level(Skill.AGILITY, 51),
			b -> b.quest("Priest in Peril", QuestState.STARTED),
			b -> b.varbit(1, 3),
			b -> b.varp(3, 5),
			b -> b.carry(563, 11),
			b -> b.wear(EquipSlot.CAPE, 0),
			b -> b.bank(995, 101),
			b -> b.members(false),
			b -> b.answer("Has a boat", false),
			b -> b.askUnanswered(true),
			b -> b.allUnlocked(true),
			b -> PlayerStateBuilder.allItems(b, true),
			b -> b.house("Yanille"),
			b -> b.houseThing("ornate jewellery box"),
			b -> b.spellbook(Spellbook.LUNAR),
			b -> b.pouch(1, 3),
			b -> b.pouchInBank(true),
			b -> b.coolingDown(Cooldown.MINIGAME, 5),
			b -> b.collectionLog(4152),
			b -> b.spiritTree("Farming Guild"),
			b -> b.canoeAxeStored(true));
		for (UnaryOperator<PlayerState.Builder> c : changes)
		{
			PlayerState q = c.apply(base()).build();
			assertFalse(p.sameAs(q), "a change counts: " + c);
			assertFalse(q.sameAs(p));
		}
		assertFalse(p.sameAs(new PlayerState.Builder("Q").members(true).build()));
	}

	@Test
	void everyFieldIsInTheComparison()
	{
		// a new field of PlayerState must be added to sameAs (and to this list): else a changed player could keep the
		// transports worked out for the old one
		Set<String> fields = new TreeSet<>();
		for (Field f : PlayerState.class.getDeclaredFields())
		{
			if (!Modifier.isStatic(f.getModifiers()))
			{
				fields.add(f.getName());
			}
		}
		assertEquals(new TreeSet<>(Set.of("name", "levels", "quests", "varbits", "varps", "carried", "coinCap", "worn", "bank", "members",
			"answers", "askUnanswered", "allUnlocked", "allItems", "house", "houseThings", "spellbook",
			"pouch", "pouchInBank", "coolingDown", "collectionLog", "spiritTrees", "canoeAxeStored")), fields);
	}
}
