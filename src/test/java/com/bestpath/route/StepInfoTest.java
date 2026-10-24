package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import com.bestpath.core.req.Skill;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** What a step's card shows as "needs" icons, read from the transport's requirement. */
public class StepInfoTest
{
	private static final int BRUMA_TORCH = 20720;
	private static final int AIR_STAFF = 1381;
	private static final int FIRE_STAFF = 1387;
	private static final int SMOKE_STAFF = 11998;
	private static final RequirementText.ItemGroups GROUPS = new RequirementText.ItemGroups()
	{
		private final RequirementText.ItemGroups groups = RequirementText.ItemGroups.of(
			Map.of("amulet of glory", new int[]{1706, 1708}, "light source", new int[]{33, 4531, BRUMA_TORCH}));

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
					return new RequirementText.RuneSource(new int[]{556, 4695}, new int[]{AIR_STAFF, SMOKE_STAFF});
				case "fire":
					return new RequirementText.RuneSource(new int[]{554, 4697}, new int[]{FIRE_STAFF, SMOKE_STAFF});
				case "law":
					return new RequirementText.RuneSource(new int[]{563}, new int[0]);
				default:
					return null;
			}
		}
	};

	private static Requirement parse(String text)
	{
		return RequirementText.parse(text, GROUPS);
	}

	/** The first item the requirement names (inside "one of": the first choice's, without a player), or -1. */
	private static int firstItem(Requirement r)
	{
		Requirement.Item leaf = StepInfo.firstItemLeaf(r, null);
		return leaf == null ? -1 : StepInfo.shownItem(leaf, null, null);
	}

	private static TransportLine teleport(String name, String requirement, String teleportCategory)
	{
		return TransportLine.builder("t-1", TransportLine.Kind.TELEPORT).name(name).category("TELEPORT")
			.end(3212, 3424, 0).ticks(4).requirements(requirement).teleportCategory(teleportCategory).build();
	}

	@Test
	public void aSpellNeedsItsLevelAndRunesInOrder()
	{
		StepInfo i = StepInfo.of(teleport("Varrock Teleport", "spellbook standard; Magic 25; rune air x3; rune law x1",
			"spell"), false, parse("spellbook standard; Magic 25; rune air x3; rune law x1"), null);
		assertEquals(-1, i.getItemId());
		assertEquals("spell", i.getTeleportCategory());
		List<StepInfo.Need> n = i.getNeeds();
		assertEquals(3, n.size());
		assertEquals(StepInfo.Need.Type.SKILL, n.get(0).getType());
		assertEquals(Skill.MAGIC, n.get(0).getSkill());
		assertEquals("Magic 25", n.get(0).getText());
		assertEquals(StepInfo.Need.Type.RUNE, n.get(1).getType());
		assertEquals(556, n.get(1).getItemId()); // the rune itself, not a combination rune
		assertEquals("x3", n.get(1).getText());
		assertEquals("3 air runes (or a staff that gives them)", n.get(1).getTooltip());
		assertEquals(563, n.get(2).getItemId());
	}

	@Test
	public void anItemTeleportShowsItsItemOnceAndOtherNeeds()
	{
		String req = "members; item amulet of glory; quest Plague City; coins 30";
		StepInfo i = StepInfo.of(teleport("Amulet of glory: Edgeville", req, "jewellery (consumable)"), false, parse(req),
			TransportSettings.Category.TELEPORT_ITEMS);
		assertEquals(1706, i.getItemId()); // the group's first item
		assertEquals(2, i.getNeeds().size()); // the amulet is the big icon already; members has no icon
		assertEquals(StepInfo.Need.Type.QUEST, i.getNeeds().get(0).getType());
		assertEquals("Plague City", i.getNeeds().get(0).getText());
		assertEquals(StepInfo.Need.Type.COINS, i.getNeeds().get(1).getType());
		assertEquals(StepInfo.COINS_ITEM, i.getNeeds().get(1).getItemId());
		assertEquals("30", i.getNeeds().get(1).getText());
	}

	@Test
	public void withoutAPlayerTheFirstChoiceIsShown()
	{
		Requirement r = parse("members; one of (item 13122; varbit 4459 = 1 | item 13123; varbit 4460 = 1)");
		List<StepInfo.Need> n = StepNeeds.needs(r, -1, null, null);
		assertEquals(1, n.size());
		assertEquals(13122, n.get(0).getItemId());
		assertEquals(13122, firstItem(r));
		assertEquals(-1, firstItem(parse("Agility 50")));
		// the item teleport's big icon is that choice's item, so it is not a need again
		assertTrue(StepNeeds.needs(r, 13122, null, null).isEmpty());
	}

	/** The Al Kharid toll gate's requirement. */
	private static final String TOLL = "one of (coins 10 | quest Prince Ali Rescue)";

	private static PlayerState.Builder member()
	{
		return new PlayerState.Builder("test").members(true);
	}

	@Test
	public void oneOfShowsOnlyTheChoiceThatAppliesToThePlayer()
	{
		Requirement r = parse(TOLL);
		String all = " (one of: 10 coins or Quest: Prince Ali Rescue)";

		List<StepInfo.Need> coins = StepNeeds.needs(r, -1, member().carry(995, 100).build(), null);
		assertEquals(1, coins.size());
		assertEquals(StepInfo.Need.Type.COINS, coins.get(0).getType());
		assertEquals("10", coins.get(0).getText());
		assertEquals("10 coins" + all, coins.get(0).getTooltip());

		// the quest done: met for good, so no need is shown; without a player it is shown
		assertTrue(StepNeeds.needs(r, -1, member().quest("Prince Ali Rescue", QuestState.DONE).build(), null).isEmpty());
		List<StepInfo.Need> quest = StepNeeds.needs(parse("quest Prince Ali Rescue"), -1, null, null);
		assertEquals(StepInfo.Need.Type.QUEST, quest.get(0).getType());
		assertEquals("Prince Ali Rescue", quest.get(0).getText());

		// both met: the first, as the router decides; coins only in the bank lose to the quest done
		assertEquals(StepInfo.Need.Type.COINS, StepNeeds.needs(r, -1, member().carry(995, 10)
			.quest("Prince Ali Rescue", QuestState.DONE).build(), null).get(0).getType());
		assertTrue(StepNeeds.needs(r, -1, member().bank(995, 10).quest("Prince Ali Rescue", QuestState.DONE).build(), null).isEmpty());
		assertEquals(StepInfo.Need.Type.COINS, StepNeeds.needs(r, -1, member().bank(995, 10).build(), null).get(0).getType());

		assertTrue("none met: nothing (the route would not use it)", StepNeeds.needs(r, -1, member().build(), null).isEmpty());
		assertEquals("no player: the first choice", StepInfo.Need.Type.COINS, StepNeeds.needs(r, -1, null, null).get(0).getType());
	}

	@Test
	public void aChoiceOfSeveralPartsShowsThemAllAndOtherNeedsStay()
	{
		Requirement r = parse("Agility 30; one of (quest Regicide; item amulet of glory | members; varbit 1 = 2)");
		PlayerState p = member().quest("Regicide", QuestState.DONE).carry(1708, 1).level(Skill.AGILITY, 30).build();
		List<StepInfo.Need> n = StepNeeds.needs(r, -1, p, null);
		// the level and the quest are met for good (left out); the item stays
		assertEquals(1, n.size());
		assertEquals(1708, n.get(0).getItemId()); // the one the player carries
		assertEquals("Item: amulet of glory (one of: Quest: Regicide and Item: amulet of glory or members and varbit 1 = 2)",
			n.get(0).getTooltip());
		// without a player every part shows
		List<StepInfo.Need> all = StepNeeds.needs(r, -1, null, null);
		assertEquals(3, all.size());
		assertEquals("Needs Agility 30", all.get(0).getTooltip());
		// the second choice has no icon, and the level is met: nothing to show
		PlayerState v = member().varbit(1, 2).level(Skill.AGILITY, 30).build();
		assertTrue(StepNeeds.needs(r, -1, v, null).isEmpty());
	}

	@Test
	public void anItemGroupShowsTheOwnersDefaultUnlessThePlayerHasOne()
	{
		Tables tables = Tables.parse(List.of("amulet of glory\t1706,1708,1712\tsrc\tnames"), List.of(), List.of(), List.of(),
			List.of("# defaults", "amulet of glory\t1712\tRel\t2026-10-13T07:21:55Z\tAmulet of glory(4)"));
		Requirement.Item glory = (Requirement.Item) RequirementText.parse("item amulet of glory", tables.itemGroups());
		assertEquals("no player: the default", 1712, StepInfo.shownItem(glory, null, tables));
		assertEquals("no tables: the first", 1706, StepInfo.shownItem(glory, null, null));
		assertEquals("carried", 1708, StepInfo.shownItem(glory, member().carry(1708, 1).build(), tables));
		assertEquals("worn", 1706, StepInfo.shownItem(glory, member().wear(EquipSlot.NECK, 1706).build(), tables));
		assertEquals("in the bank", 1708, StepInfo.shownItem(glory, member().bank(1708, 1).build(), tables));
		assertEquals("carried beats banked", 1706, StepInfo.shownItem(glory, member().bank(1708, 1).carry(1706, 1).build(), tables));
		assertEquals("has none: the default", 1712, StepInfo.shownItem(glory, member().build(), tables));
		assertEquals("every item (test player): the default", 1712, StepInfo.shownItem(glory, PlayerStateBuilder.maxed(), tables));

		String req = "members; item amulet of glory";
		StepInfo i = StepInfo.of(teleport("Amulet of glory: Edgeville", req, "jewellery (consumable)"), false,
			RequirementText.parse(req, tables.itemGroups()), TransportSettings.Category.TELEPORT_ITEMS, null, tables);
		assertEquals(1712, i.getItemId());
		assertTrue(i.getNeeds().isEmpty());
		// an item named by ids has no group: its first id
		Requirement.Item ids = (Requirement.Item) RequirementText.parse("item 1708/1712", tables.itemGroups());
		assertEquals(1708, StepInfo.shownItem(ids, member().build(), tables));
	}

	@Test
	public void itemsWithoutAGroupNameSayLittle()
	{
		List<StepInfo.Need> n = StepNeeds.needs(parse("item 8007 x2 used up; Agility 50; Agility 50"), -1, null, null);
		assertEquals(2, n.size()); // the same need twice is shown once
		assertEquals("x2", n.get(0).getText());
		assertEquals("An item x2, used up", n.get(0).getTooltip());
		assertEquals("Needs Agility 50", n.get(1).getTooltip());
		assertEquals("Item: amulet of glory", StepInfo.Need.item("amulet of glory", 1706, 1, false, false).getTooltip());
	}

	@Test
	public void questWantsAreSpelledOut()
	{
		assertEquals("Desert Treasure I (started)", StepInfo.Need.quest("Desert Treasure I",
			Requirement.Quest.Wanted.STARTED).getText());
		assertEquals("Quest: Desert Treasure I", StepInfo.Need.quest("Desert Treasure I",
			Requirement.Quest.Wanted.DONE).getTooltip());
	}

	@Test
	public void itemTeleportCategories()
	{
		assertTrue(StepInfo.isItemTeleportCategory("tablet"));
		assertTrue(StepInfo.isItemTeleportCategory("Jewellery (infinite)"));
		assertTrue(StepInfo.isItemTeleportCategory("cape or diary item (consumable)"));
		assertTrue(StepInfo.isItemTeleportCategory("other item (infinite)"));
		assertTrue(!StepInfo.isItemTeleportCategory("spell"));
		assertTrue(!StepInfo.isItemTeleportCategory("grouping teleport"));
	}

	@Test
	public void aHomeOrMinigameTeleportShowsReady()
	{
		// used only while ready, so its card says "Ready" (no countdown)
		TransportLine home = TransportLine.builder("t-2", TransportLine.Kind.TELEPORT)
			.name("Lumbridge Home Teleport").category("TELEPORT").end(3222, 3218, 0).ticks(124).cooldown(true)
			.requirements("spellbook standard").teleportCategory("home teleport").wildernessLimit(20).build();
		StepInfo i = StepInfo.of(home, false, parse("spellbook standard"), null);
		assertEquals(1, i.getNeeds().size());
		StepInfo.Need ready = i.getNeeds().get(0);
		assertEquals(StepInfo.Need.Type.COOLDOWN, ready.getType());
		assertEquals("Ready", ready.getText());
		assertEquals("Home teleport: ready (it can't be used again for 30 minutes after)", ready.getTooltip());
		assertEquals(com.bestpath.core.req.Cooldown.HOME, i.getCooldown());

		TransportLine castleWars = TransportLine.builder("t-3", TransportLine.Kind.TELEPORT)
			.name("Minigame Teleport: Castle Wars").category("TELEPORT").end(2440, 3090, 0).ticks(124)
			.cooldown(true).requirements("Fishing 15").teleportCategory("grouping teleport").wildernessLimit(0)
			.build();
		StepInfo m = StepInfo.of(castleWars, false, parse("Fishing 15"), null);
		assertEquals(List.of("Fishing 15", "Ready"), List.of(m.getNeeds().get(0).getText(), m.getNeeds().get(1).getText()));
		assertEquals(com.bestpath.core.req.Cooldown.MINIGAME, m.getCooldown());

		// an ordinary spell: no cooldown
		assertEquals(null, StepInfo.of(teleport("Varrock Teleport", "Magic 25", "spell"), false, parse("Magic 25"), null)
			.getCooldown());
	}

	/** A dark cave's way in: "item light source". */
	private static StepInfo darkCave(PlayerState p)
	{
		TransportLine cave = TransportLine.builder("t-2", TransportLine.Kind.ONE_WAY).name("Crypt Entrance")
			.category("ENTER").start(3200, 3200, 0).end(3200, 9600, 0).ticks(4).requirements("item light source")
			.build();
		return StepInfo.of(cave, false, parse("item light source"), null, p, null);
	}

	@Test
	public void theItemShownIsTheOneOnThePlayerWornFirst()
	{
		// a Bruma torch the player has is shown in a dark place, with a green check
		StepInfo.Need n = darkCave(member().wear(EquipSlot.WEAPON, BRUMA_TORCH).carry(33, 1).build()).getNeeds().get(0);
		assertEquals(BRUMA_TORCH, n.getItemId());
		assertEquals(NeedStatus.HAVE, n.getStatus());
		assertEquals(4531, darkCave(member().carry(4531, 1).bank(33, 1).build()).getNeeds().get(0).getItemId());
	}

	@Test
	public void cardsAreMadeAgainForTheItemsNow()
	{
		StepInfo before = darkCave(member().carry(4531, 1).build());
		StepInfo now = before.forPlayer(member().wear(EquipSlot.WEAPON, BRUMA_TORCH).build());
		assertEquals(BRUMA_TORCH, now.getNeeds().get(0).getItemId());
		assertTrue(!now.sameCard(before));
		assertTrue(before.forPlayer(member().carry(4531, 2).build()).sameCard(before));
		// a walk, a sail or a bank step stays as it is
		assertTrue(StepInfo.NONE.forPlayer(member().build()) == StepInfo.NONE);
	}

	private static final String SPELL = "Magic 25; rune air x3; rune fire x1; rune law x1";

	private static List<StepInfo.Need> spell(PlayerState p)
	{
		return StepInfo.of(teleport("Varrock Teleport", SPELL, "spell"), false, parse(SPELL), null, p, null).getNeeds();
	}

	@Test
	public void aWornStaffIsShownInsteadOfItsRunesOnce()
	{
		// a staff the player has that provides the runes in the card is shown instead of various runes
		List<StepInfo.Need> n = spell(member().level(Skill.MAGIC, 25).wear(EquipSlot.WEAPON, SMOKE_STAFF).carry(563, 1)
			.build());
		assertEquals(2, n.size()); // Magic 25 is met for good: left out
		assertEquals(SMOKE_STAFF, n.get(0).getItemId());
		assertEquals(StepInfo.Need.Type.ITEM, n.get(0).getType());
		assertEquals("", n.get(0).getText());
		assertEquals("Staff: gives the air and fire runes", n.get(0).getTooltip());
		assertEquals(NeedStatus.HAVE, n.get(0).getStatus());
		assertEquals(563, n.get(1).getItemId()); // the law rune itself
	}

	@Test
	public void theStaffOnlyWhenItIsWhatGivesTheRunes()
	{
		// enough air runes carried: the runes (a staff in the bag is not needed)
		List<StepInfo.Need> runes = spell(member().carry(556, 3).carry(AIR_STAFF, 1).build());
		assertEquals(556, runes.get(1).getItemId());
		// too few: the carried staff, to wield
		List<StepInfo.Need> carried = spell(member().carry(AIR_STAFF, 1).build());
		assertEquals(AIR_STAFF, carried.get(1).getItemId());
		assertEquals("Staff: gives the air runes (wield it)", carried.get(1).getTooltip());
		// only a staff in the bank: the staff with the bank's mark (what the bank step takes out)
		List<StepInfo.Need> banked = spell(member().bank(FIRE_STAFF, 1).build());
		assertEquals(FIRE_STAFF, banked.get(2).getItemId());
		assertEquals(NeedStatus.BANK, banked.get(2).getStatus());
		// nothing: the runes with their X
		assertEquals(554, spell(member().build()).get(2).getItemId());
	}
}
