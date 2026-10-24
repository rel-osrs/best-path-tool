package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.req.BankItem;
import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.core.req.Skill;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The marks beside a card's needs (a green check when the player has it, the bank symbol when it is
 * in their bank, a red X when they don't have it), worked out with the requirement model's own checks: items carried,
 * worn, banked or missing, runes (a worn staff, combination runes, the rune pouch), coins, levels, quests, the
 * cooldown, and the step's own item.
 */
public class NeedStatusTest
{
	private static final int LAW = 563;
	private static final int AIR = 556;
	private static final int DUST = 4696;
	private static final int AIR_STAFF = 1381;
	private static final int GLORY_4 = 1712;
	private static final int[] GLORIES = {1706, 1708, 1710, GLORY_4};

	private static final RequirementText.ItemGroups GROUPS = new RequirementText.ItemGroups()
	{
		private final RequirementText.ItemGroups groups = RequirementText.ItemGroups.of(Map.of("amulet of glory", GLORIES));

		@Override
		public int[] ids(String name)
		{
			return groups.ids(name);
		}

		@Override
		public RequirementText.RuneSource rune(String name)
		{
			switch (name)
			{
				case "air":
					return new RequirementText.RuneSource(new int[]{AIR, DUST}, new int[]{AIR_STAFF});
				case "law":
					return new RequirementText.RuneSource(new int[]{LAW}, new int[0]);
				default:
					return null;
			}
		}
	};

	private static PlayerState.Builder player()
	{
		return new PlayerState.Builder("p").members(true);
	}

	/** The marks of a requirement line's needs for this player, in order. */
	private static List<NeedStatus> marks(String line, PlayerState p)
	{
		List<NeedStatus> out = new ArrayList<>();
		for (StepInfo.Need n : StepNeeds.needs(RequirementText.parse(line, GROUPS), -1, p, null))
		{
			out.add(n.getStatus());
		}
		return out;
	}

	private static NeedStatus mark(String line, PlayerState p)
	{
		List<NeedStatus> m = marks(line, p);
		assertEquals(line, 1, m.size());
		return m.get(0);
	}

	@Test
	public void theStoredCanoeAxe()
	{
		// if the plugin doesn't know, it is a question mark like stored clue items
		String line = "canoe axe stored";
		assertEquals(NeedStatus.MAYBE, mark(line, player().canoeAxeStored(null).build()));
		assertEquals(NeedStatus.HAVE, mark(line, player().canoeAxeStored(true).build()));
		assertEquals(NeedStatus.MISSING, mark(line, player().canoeAxeStored(false).build()));
		assertEquals(NeedStatus.HAVE, mark(line, PlayerStateBuilder.maxed()));
		assertEquals(NeedStatus.UNKNOWN, mark(line, null));
		StepInfo.Need n = StepNeeds.needs(RequirementText.parse(line, GROUPS), -1, player().build(), null).get(0);
		assertEquals("Stored", n.getText());
		assertEquals(StepNeeds.BRONZE_AXE, n.getItemId());
		// the canoe network: a carried axe is shown first; without one, the maybe-stored axe with its question mark
		String canoe = "one of (item 1351 | canoe axe stored)";
		assertEquals(NeedStatus.HAVE, mark(canoe, player().canoeAxeStored(null).carry(1351, 1).build()));
		assertEquals(NeedStatus.MAYBE, mark(canoe, player().canoeAxeStored(null).build()));
		assertEquals(List.of(), marks(canoe, player().canoeAxeStored(false).build())); // not usable: nothing shown
	}

	@Test
	public void itemsCarriedWornBankedMissing()
	{
		String line = "item 8007";
		assertEquals(NeedStatus.HAVE, mark(line, player().carry(8007, 1).build()));
		assertEquals(NeedStatus.HAVE, mark(line, player().wear(EquipSlot.WEAPON, 8007).build()));
		assertEquals(NeedStatus.BANK, mark(line, player().bank(8007, 3).build()));
		assertEquals(NeedStatus.MISSING, mark(line, player().build()));
		// a count: 1 carried and 4 banked of 5 is "in your bank"; 1 + 1 of 5 is missing
		assertEquals(NeedStatus.BANK, mark("item 8007 x5", player().carry(8007, 1).bank(8007, 4).build()));
		assertEquals(NeedStatus.MISSING, mark("item 8007 x5", player().carry(8007, 1).bank(8007, 1).build()));
		// an item group: any of its items
		assertEquals(NeedStatus.HAVE, mark("item amulet of glory", player().wear(EquipSlot.NECK, GLORY_4).build()));
		assertEquals(NeedStatus.BANK, mark("item amulet of glory", player().bank(1708, 1).build()));
		// to be worn, but carried: the player has it (the card says to wear it)
		assertEquals(NeedStatus.HAVE, mark("item 8007 worn", player().carry(8007, 1).build()));
		// "every item" (the Data Tool's Maxed)
		assertEquals(NeedStatus.HAVE, mark(line, PlayerStateBuilder.maxed()));
	}

	@Test
	public void runesCountAStaffCombinationRunesAndThePouch()
	{
		String line = "rune air x3";
		assertEquals(NeedStatus.HAVE, mark(line, player().carry(AIR, 3).build()));
		assertEquals(NeedStatus.HAVE, mark(line, player().carry(DUST, 3).build()));
		assertEquals("a worn staff gives them", NeedStatus.HAVE, mark(line, player().wear(EquipSlot.WEAPON, AIR_STAFF).build()));
		assertEquals("a carried staff: wield it on the way", NeedStatus.HAVE, mark(line, player().carry(AIR_STAFF, 1).build()));
		assertEquals(NeedStatus.HAVE, mark(line, player().pouch(AIR, 50).build()));
		assertEquals(NeedStatus.BANK, mark(line, player().pouch(AIR, 50).pouchInBank(true).build()));
		assertEquals(NeedStatus.BANK, mark(line, player().bank(AIR, 100).build()));
		assertEquals("a staff in the bank", NeedStatus.BANK, mark(line, player().bank(AIR_STAFF, 1).build()));
		assertEquals(NeedStatus.MISSING, mark(line, player().carry(AIR, 2).build()));
	}

	@Test
	public void coinsLevelsAndQuests()
	{
		assertEquals(NeedStatus.HAVE, mark("coins 30", player().carry(StepInfo.COINS_ITEM, 30).build()));
		assertEquals(NeedStatus.BANK, mark("coins 30", player().carry(StepInfo.COINS_ITEM, 10).bank(StepInfo.COINS_ITEM, 20).build()));
		assertEquals(NeedStatus.MISSING, mark("coins 30", player().carry(StepInfo.COINS_ITEM, 29).build()));

		// a level or quest the player has stays theirs: no need (no green check) on the card
		assertEquals(List.of(), marks("Agility 51", player().level(Skill.AGILITY, 51).build()));
		assertEquals(NeedStatus.MISSING, mark("Agility 51", player().level(Skill.AGILITY, 50).build()));
		assertEquals("a members skill on a free account", NeedStatus.MISSING,
			mark("Agility 51", new PlayerState.Builder("f2p").level(Skill.AGILITY, 99).build()));

		assertEquals(List.of(), marks("quest Plague City", player().quest("Plague City", QuestState.DONE).build()));
		assertEquals(NeedStatus.MISSING, mark("quest Plague City", player().quest("Plague City", QuestState.STARTED).build()));
		assertEquals(List.of(), marks("quest Plague City started", player().quest("Plague City", QuestState.STARTED).build()));

		// each need its own mark, in the line's order (the level met: left out)
		PlayerState mixed = player().level(Skill.MAGIC, 60).bank(LAW, 5).build();
		assertEquals(List.of(NeedStatus.BANK, NeedStatus.MISSING), marks("Magic 50; rune law x1; quest Plague City", mixed));
		PlayerState low = player().level(Skill.MAGIC, 40).carry(LAW, 5).build();
		assertEquals(List.of(NeedStatus.MISSING, NeedStatus.HAVE, NeedStatus.MISSING),
			marks("Magic 50; rune law x1; quest Plague City", low));
	}

	@Test
	public void answersThatCanChangeKeepTheirCheck()
	{
		// one-time unlocks are left out once answered yes; the respawn point can change back (the stored canoe axe is
		// detected, no answer)
		assertEquals(List.of(), marks("answer Magic mushtree Sticky Swamp unlocked",
			player().answer("Magic mushtree Sticky Swamp unlocked", true).build()));
		assertEquals(List.of(NeedStatus.HAVE), marks("answer Respawn point set to Prifddinas",
			player().answer("Respawn point set to Prifddinas", true).build()));
		assertEquals(List.of(NeedStatus.MISSING), marks("answer Magic mushtree Sticky Swamp unlocked",
			player().answer("Magic mushtree Sticky Swamp unlocked", false).build()));
	}

	@Test
	public void withoutAPlayerNoMark()
	{
		assertEquals(List.of(NeedStatus.UNKNOWN, NeedStatus.UNKNOWN), marks("Agility 51; item 8007", null));
		StepInfo.Need n = StepInfo.Need.skill(Skill.AGILITY, 51);
		assertEquals(NeedStatus.UNKNOWN, n.getStatus());
		// the mark is part of the need: the same need with another mark is another need
		assertNotEquals(n, n.withStatus(NeedStatus.HAVE));
		assertEquals(n.withStatus(NeedStatus.HAVE), StepInfo.Need.skill(Skill.AGILITY, 51).withStatus(NeedStatus.HAVE));
		assertEquals(n.withStatus(NeedStatus.HAVE).hashCode(), StepInfo.Need.skill(Skill.AGILITY, 51).withStatus(NeedStatus.HAVE).hashCode());
	}

	@Test
	public void theStepsOwnItemAndTheCooldown()
	{
		TransportLine glory = TransportLine.builder("t-g", TransportLine.Kind.TELEPORT)
			.name("Amulet of glory: Edgeville").category("TELEPORT").end(3087, 3496, 0).ticks(4)
			.requirements("item amulet of glory").teleportCategory("jewellery (charged)").build();
		Requirement req = RequirementText.parse("item amulet of glory", GROUPS);
		StepInfo worn = StepInfo.of(glory, false, req, null, player().wear(EquipSlot.NECK, GLORY_4).build(), null);
		assertEquals(GLORY_4, worn.getItemId());
		assertEquals(NeedStatus.HAVE, worn.getItemStatus());
		assertEquals("the item is the big picture, not a need", 0, worn.getNeeds().size());
		StepInfo banked = StepInfo.of(glory, false, req, null, player().bank(1708, 1).build(), null);
		assertEquals(1708, banked.getItemId());
		assertEquals(NeedStatus.BANK, banked.getItemStatus());
		assertEquals(NeedStatus.UNKNOWN, StepInfo.of(glory, false, req, null).getItemStatus());

		TransportLine home = TransportLine.builder("t-h", TransportLine.Kind.TELEPORT)
			.name("Lumbridge Home Teleport").category("TELEPORT").end(3222, 3218, 0).ticks(124).cooldown(true)
			.requirements("spellbook standard").teleportCategory("home teleport").wildernessLimit(20).build();
		Requirement standard = RequirementText.parse("spellbook standard", GROUPS);
		StepInfo ready = StepInfo.of(home, false, standard, null, player().build(), null);
		assertEquals(NeedStatus.HAVE, ready.getNeeds().get(0).getStatus());
		StepInfo cooling = StepInfo.of(home, false, standard, null, player().coolingDown(Cooldown.HOME, 0).build(), null);
		assertEquals(NeedStatus.MISSING, cooling.getNeeds().get(0).getStatus());
	}

	@Test
	public void bankStepItemsShowWhatThePlayerBanks()
	{
		BankItem glories = new BankItem("amulet of glory", GLORIES, 1);
		StepInfo.TakeOut t = StepInfo.TakeOut.of(glories, player().bank(1710, 1).build());
		assertEquals("the picture of the one in the bank", 1710, t.getItemId());
		assertEquals(NeedStatus.BANK, t.getStatus());
		assertEquals(1, t.getCount());
		assertEquals("Amulet of glory", t.fallbackName());
		StepInfo.TakeOut laws = StepInfo.TakeOut.of(new BankItem("law runes x3", new int[]{LAW}, 2), player().bank(LAW, 9).build());
		assertEquals(LAW, laws.getItemId());
		assertEquals(2, laws.getCount());
		assertEquals("Law runes", laws.fallbackName());
		assertEquals("Item 8007", StepInfo.TakeOut.of(new BankItem("8007", new int[]{8007}, 1), null).fallbackName());
		assertEquals("Coins", StepInfo.TakeOut.of(new BankItem("coins 30", new int[]{995}, 20), null).fallbackName());
		assertEquals(NeedStatus.BANK, StepInfo.TakeOut.of(new BankItem("8007", new int[]{8007}, 1), null).getStatus());
	}
}
