package com.bestpath.tool.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.MenuTextBuilder;
import com.bestpath.core.data.MenuTextDiagnostics;
import com.bestpath.core.req.RequirementText;
import com.bestpath.tool.edit.TransportRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The menu text rules (the exact text of the menu click) on small made-up cache
 * records: objects by direction and partner, items by their options, spells, network stops and rides, house furniture.
 */
class MenuMatcherTest
{
	private final MenuFacts facts = new MenuFacts();
	private final List<MenuMatcher.Hint> hints = new ArrayList<>();
	private final List<TransportRecord> records = new ArrayList<>();

	private void object(int id, String name, int x, int y, int plane, String... ops)
	{
		facts.objects.put(id, new MenuFacts.Thing(id, List.of(new MenuFacts.Form(id, name, ops))));
		facts.objectsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(id);
		if (x >= 0)
		{
			facts.place(new MenuFacts.Placed(id, x, y, plane, 1, 1));
		}
	}

	private void item(int id, String name, String[] inv, Map<Integer, List<String>> invSubs, String[] worn)
	{
		List<List<String>> subs = new ArrayList<>();
		List<List<String>> wornSubs = new ArrayList<>();
		for (int i = 0; i < 5; i++)
		{
			subs.add(invSubs.get(i));
		}
		for (int i = 0; i < 8; i++)
		{
			wornSubs.add(null);
		}
		facts.items.put(id, new MenuFacts.Item(id, name, Arrays.copyOf(inv, 5), subs, Arrays.copyOf(worn, 8), wornSubs));
	}

	private TransportRecord record(TransportRecord.Kind kind, String name, String category, int[] start, int[] end, int objectId,
		int[] object)
	{
		TransportRecord r = new TransportRecord();
		r.id = "t-" + (records.size() + 1);
		r.kind = kind;
		r.name = name;
		r.category = category;
		if (start != null)
		{
			r.startX = start[0];
			r.startY = start[1];
			r.startPlane = start[2];
		}
		r.endX = end[0];
		r.endY = end[1];
		r.endPlane = end[2];
		r.objectId = objectId;
		if (object != null)
		{
			r.objectX = object[0];
			r.objectY = object[1];
			r.objectPlane = object[2];
		}
		records.add(r);
		return r;
	}

	private TransportRecord teleport(String name, String category, String requirements)
	{
		TransportRecord r = record(TransportRecord.Kind.TELEPORT, name, "TELEPORT", null, new int[]{3200, 3200, 0}, -1, null);
		r.teleportCategory = category;
		r.requirements = requirements;
		return r;
	}

	private static int[] t(int x, int y, int p)
	{
		return new int[]{x, y, p};
	}

	private MenuMatcher matcher()
	{
		return new MenuMatcher(facts, text -> RequirementText.parse(text, RequirementText.ItemGroups.NONE), hints, records);
	}

	@Test
	void laddersAndStairsUseTheOptionForTheWayAndTheOtherEndsObject()
	{
		object(100, "Ladder", 3200, 3202, 0, "Climb-up");
		object(101, "Ladder", 3200, 3202, 1, "Climb-down");
		object(102, "Door", 3201, 3203, 1, "Open"); // a door near the top is not the way back
		TransportRecord up = record(TransportRecord.Kind.TWO_WAY, "Ladder", "CLIMB_UP", t(3200, 3201, 0), t(3200, 3201, 1), 100,
			t(3200, 3202, 0));
		object(110, "Staircase", 3300, 3300, 1, "Climb-up", "Climb-down");
		object(111, "Staircase", 3300, 3300, 2, "Climb-down");
		object(112, "Staircase", 3300, 3300, 0, "Climb-up");
		TransportRecord middle = record(TransportRecord.Kind.TWO_WAY, "Staircase", "CLIMB_BOTH", t(3299, 3300, 1), t(3299, 3301, 2),
			110, t(3300, 3300, 1));
		TransportRecord middleDown = record(TransportRecord.Kind.TWO_WAY, "Staircase", "CLIMB_BOTH", t(3299, 3300, 1),
			t(3299, 3301, 0), 110, t(3300, 3300, 1));
		MenuMatcher m = matcher();

		MenuMatcher.Result a = m.match(up);
		assertEquals("Climb-up Ladder", a.there.title(MenuText.ARROW));
		assertEquals("Climb-down Ladder", a.back.title(MenuText.ARROW));
		assertTrue(a.doubts.isEmpty(), a.doubts.toString());
		assertEquals("Climb-up Staircase", m.match(middle).there.title(MenuText.ARROW));
		assertEquals("Climb-down Staircase", m.match(middle).back.title(MenuText.ARROW));
		assertEquals("Climb-down Staircase", m.match(middleDown).there.title(MenuText.ARROW));
		assertEquals("Climb-up Staircase", m.match(middleDown).back.title(MenuText.ARROW));
	}

	@Test
	void caveEntrancesExitAndCrossingsUseTheSameObjectBothWays()
	{
		object(120, "Cave entrance", 3220, 3200, 0, "Enter");
		object(121, "Cave exit", 3220, 9601, 0, "Exit");
		TransportRecord cave = record(TransportRecord.Kind.TWO_WAY, "Cave entrance", "ENTER", t(3220, 3199, 0), t(3220, 9600, 0), 120,
			t(3220, 3200, 0));
		object(130, "Stile", 3210, 3201, 0, "Climb-over");
		TransportRecord stile = record(TransportRecord.Kind.TWO_WAY, "Stile", "SHORTCUT", t(3210, 3200, 0), t(3210, 3202, 0), 130,
			t(3210, 3201, 0));
		// a gangplank up onto a ship: the same "Cross" both ways, not the ship's ladder beside it
		object(140, "Gangplank", 3030, 3215, 0, "Cross");
		object(141, "Gangplank", 3032, 3215, 1, "Cross");
		object(142, "Ship's ladder", 3033, 3216, 1, "Climb-down");
		TransportRecord plank = record(TransportRecord.Kind.TWO_WAY, "Gangplank", "SHORTCUT", t(3029, 3215, 0), t(3033, 3215, 1), 140,
			t(3030, 3215, 0));
		MenuMatcher m = matcher();

		assertEquals("Enter Cave entrance", m.match(cave).there.title(MenuText.ARROW));
		assertEquals("Exit Cave exit", m.match(cave).back.title(MenuText.ARROW));
		assertEquals("Climb-over Stile", m.match(stile).there.title(MenuText.ARROW));
		assertEquals("Climb-over Stile", m.match(stile).back.title(MenuText.ARROW));
		assertEquals("Cross Gangplank", m.match(plank).there.title(MenuText.ARROW));
		assertEquals("Cross Gangplank", m.match(plank).back.title(MenuText.ARROW));
	}

	@Test
	void anObjectsOnlyOptionWinsAndLookingIsNeverAClick()
	{
		// a ladder that "climbs up" into a lower plane of another region (an instance): its only option
		object(150, "Ladder", 2166, 4410, 2, "Climb-up");
		TransportRecord odd = record(TransportRecord.Kind.ONE_WAY, "Ladder", "CLIMB_UP", t(2166, 4411, 2), t(2800, 5160, 0), 150,
			t(2166, 4410, 2));
		// a mole hill is dug into with a spade: "Look-inside" is not the click
		object(160, "Mole hill", 2990, 3380, 0, "Look-inside");
		TransportRecord mole = record(TransportRecord.Kind.ONE_WAY, "Mole hill", "ENTER", t(2990, 3379, 0), t(1752, 5236, 0), 160,
			t(2990, 3380, 0));
		// a shut trapdoor above a ladder: the way back starts by opening it
		object(170, "Ladder", 3230, 9600, 0, "Climb-up");
		object(171, "Trapdoor", 3230, 3200, 0, "Open");
		TransportRecord down = record(TransportRecord.Kind.TWO_WAY, "Ladder", "CLIMB_UP", t(3230, 9601, 0), t(3230, 3201, 0), 170,
			t(3230, 9600, 0));
		MenuMatcher m = matcher();

		assertEquals("Climb-up Ladder", m.match(odd).there.title(MenuText.ARROW));
		MenuMatcher.Result r = m.match(mole);
		assertNull(r.there);
		assertTrue(r.whyThere.contains("Look-inside"), r.whyThere);
		assertEquals("Climb-up Ladder", m.match(down).there.title(MenuText.ARROW));
		assertEquals("Open Trapdoor", m.match(down).back.title(MenuText.ARROW));
		assertFalse(m.match(down).doubts.isEmpty());
	}

	@Test
	void customTransportsAndMissingObjectsStayUnknown()
	{
		TransportRecord custom = record(TransportRecord.Kind.TWO_WAY, "Custom transport", "CUSTOM", t(2209, 3420, 0), t(2209, 3424, 0),
			-1, null);
		TransportRecord missing = record(TransportRecord.Kind.TWO_WAY, "Ladder", "CLIMB_UP", t(1, 1, 0), t(1, 1, 1), 99999, t(1, 2, 0));
		MenuMatcher m = matcher();
		MenuMatcher.Result c = m.match(custom);
		assertNull(c.there);
		assertNull(c.back);
		assertTrue(c.whyThere.contains("custom"), c.whyThere);
		assertTrue(m.match(missing).whyThere.contains("not in the cache"));
	}

	@Test
	void shortestPathIdsAreUsedOnlyWhenTheCacheAgrees()
	{
		object(180, "Lever", 3090, 3475, 0, "Pull");
		TransportRecord lever = record(TransportRecord.Kind.ONE_WAY, "Lever", "TRAVEL", t(3090, 3474, 0), t(2539, 4712, 0), -1, null);
		TransportRecord wrong = record(TransportRecord.Kind.ONE_WAY, "Lever", "TRAVEL", t(3100, 3474, 0), t(2539, 4712, 0), -1, null);
		hints.add(new MenuMatcher.Hint("teleportation_levers.tsv", t(3090, 3474, 0), t(2539, 4712, 0), "Pull", "Lever", 180));
		// a row whose option the object does not have is not used
		hints.add(new MenuMatcher.Hint("teleportation_levers.tsv", t(3100, 3474, 0), t(2539, 4712, 0), "Yank", "Lever", 180));
		MenuMatcher m = matcher();
		assertEquals("Pull Lever", m.match(lever).there.title(MenuText.ARROW));
		assertNull(m.match(wrong).there);
		MenuMatcher.Hint h = MenuMatcher.Hint.parse("x.tsv", null, null, "Climb-down Ladder 16679");
		assertEquals("Climb-down", h.option);
		assertEquals("Ladder", h.target);
		assertEquals(16679, h.id);
		assertNull(MenuMatcher.Hint.parse("x.tsv", null, null, "Climb-down Ladder"));
	}

	@Test
	void itemsUseTheOptionThatNamesTheDestination()
	{
		Map<Integer, List<String>> rub = new HashMap<>();
		rub.put(3, List.of("Edgeville", "Karamja", "Draynor Village", "Al Kharid"));
		item(1712, "Amulet of glory(4)", new String[]{null, "Wear", null, "Rub", "Drop"}, rub,
			new String[]{"Edgeville", "Karamja", "Draynor Village", "Al Kharid"});
		Map<Integer, List<String>> tele = new HashMap<>();
		tele.put(2, List.of("Home", "Rimmington", "Taverley"));
		item(9789, "Construct. cape", new String[]{null, "Wear", "Teleport", "Tele to POH", "Drop"}, tele, new String[]{"Boost", "Teleport"});
		item(9790, "Construct. cape(t)", new String[]{null, "Wear", "Teleport", "Tele to POH", "Drop"}, tele, new String[]{"Boost", "Teleport"});
		item(8007, "Varrock teleport", new String[]{"Break", "Varrock", "Grand Exchange", "Toggle", "Drop"}, Map.of(), new String[0]);
		item(8013, "Teleport to house", new String[]{"Break", "Inside", "Outside", "Group", "Drop"}, Map.of(), new String[0]);
		item(19564, "Royal seed pod", new String[]{"Commune", null, null, null, "Destroy"}, Map.of(), new String[0]);
		item(2570, "Ring of returning(5)", new String[]{null, "Wear", null, "Rub", "Drop"}, Map.of(), new String[0]);
		item(3853, "Enchanted lyre(1)", new String[]{null, "Play", null, null, "Drop"},
			Map.of(1, List.of("Rellekka", "Jatiszo")), new String[0]);

		TransportRecord glory = teleport("Amulet of glory: Draynor Village", "jewellery (consumable)", "item 1712");
		TransportRecord cape = teleport("Construction cape: Rimmington house portal", "cape or diary item (infinite)", "item 9789/9790");
		TransportRecord ge = teleport("Varrock teleport (tablet): Grand Exchange", "tablet", "item 8007");
		TransportRecord outside = teleport("Teleport to house (tablet) (outside): Rimmington house portal", "tablet", "item 8013");
		TransportRecord pod = teleport("Royal seed pod: Grand Tree", "other item (infinite)", "item 19564");
		TransportRecord ret1 = teleport("Ring of returning: Lumbridge", "jewellery (consumable)", "item 2570");
		teleport("Ring of returning: Camelot", "jewellery (consumable)", "item 2570");
		TransportRecord lyre = teleport("Enchanted lyre: Jatizso", "other item (consumable)", "item 3853");
		MenuMatcher m = matcher();

		MenuText g = m.match(glory).there;
		assertEquals("Amulet of glory → Draynor Village", g.title(MenuText.ARROW)); // the charge count left out: any charge works
		assertEquals("Rub Amulet of glory", MenuTextDiagnostics.menuLine(g));
		assertEquals("Construct. cape → Rimmington", m.match(cape).there.title(MenuText.ARROW));
		assertEquals(MenuText.named("Grand Exchange", "Varrock teleport"), m.match(ge).there);
		assertEquals("Teleport to house → Outside", m.match(outside).there.title(MenuText.ARROW));
		assertEquals("Commune Royal seed pod", m.match(pod).there.title(MenuText.ARROW));
		// one item, several destinations, no option naming them: chosen in a dialogue the cache does not have
		MenuMatcher.Result r = m.match(ret1);
		assertNull(r.there);
		assertTrue(r.whyThere.contains("dialogue"), r.whyThere);
		// the cache's own spelling, with a doubt
		MenuMatcher.Result l = m.match(lyre);
		assertEquals("Enchanted lyre → Jatiszo", l.there.title(MenuText.ARROW));
		assertFalse(l.doubts.isEmpty());
	}

	@Test
	void diaryCapeRecordsNamedAfterMastersTakeTheCapesOptions()
	{
		// the menu's name wins ("Achievement diary cape: Two-pints" -> the cape's "Ardougne")
		Map<Integer, List<String>> tele = new HashMap<>();
		tele.put(2, List.of("Ardougne", "Desert", "Karamja", "Karamja (Shilo)", "Twiggy O'Korn"));
		item(19476, "Achievement diary cape", new String[]{null, "Wear", "Teleport", null, "Drop"}, tele, new String[]{"Teleport"});
		TransportRecord ardougne = teleport("Achievement diary cape: Two-pints", "cape or diary item (infinite)", "item 19476/13069");
		TransportRecord shilo = teleport("Achievement diary cape: Kaleb Paramaya", "cape or diary item (infinite)", "item 19476/13069");
		TransportRecord jackie = teleport("Achievement diary cape: Pirate Jackie the Fruit", "cape or diary item (infinite)",
			"item 19476/13069");
		TransportRecord twiggy = teleport("Achievement diary cape: Twiggy O'Korn", "cape or diary item (infinite)", "item 19476/13069");
		MenuMatcher m = matcher();
		assertEquals("Achievement diary cape → Ardougne", m.match(ardougne).there.title(MenuText.ARROW));
		assertEquals("Achievement diary cape → Karamja (Shilo)", m.match(shilo).there.title(MenuText.ARROW));
		assertEquals("Achievement diary cape → Karamja", m.match(jackie).there.title(MenuText.ARROW));
		assertTrue(m.match(jackie).doubts.isEmpty(), m.match(jackie).doubts.toString());
		assertEquals("Achievement diary cape → Twiggy O'Korn", m.match(twiggy).there.title(MenuText.ARROW));
		// another item with a master's name as its destination is not mapped
		assertNull(TeleportMenus.diaryCapeOption(new MenuFacts.Item(1, "Amulet of glory", new String[5], new ArrayList<>(),
			new String[8], new ArrayList<>()), "Two-pints"));
		assertEquals(15, TeleportMenus.DIARY_CAPE_OPTIONS.size());
	}

	@Test
	void spellsAndMinigames()
	{
		facts.addSpell("Varrock Teleport", List.of("Cast", "Grand Exchange", "Configure"));
		facts.addSpell("Teleport to House", List.of("Cast", "Outside"));
		facts.addSpell("Minigame Teleport", List.of("Cast"));
		facts.lists.put(MenuFacts.MINIGAMES, List.of("Castle Wars", "Pest Control"));
		item(8007, "Varrock teleport", new String[]{"Break", "Varrock", "Grand Exchange", "Toggle", "Drop"}, Map.of(), new String[0]);
		TransportRecord varrock = teleport("Varrock Teleport", "spell", "rune law x1");
		TransportRecord ge = teleport("Varrock Teleport: Grand Exchange", "spell", "rune law x1");
		TransportRecord house = teleport("Teleport to House (outside): Rimmington house portal", "spell", "");
		TransportRecord inside = teleport("Teleport to House (inside your house)", "spell", "");
		TransportRecord pest = teleport("Minigame Teleport: Pest Control", "grouping teleport", "");
		// a tablet of the same name is the tablet, not the spell
		TransportRecord tablet = teleport("Varrock teleport (tablet): Varrock centre", "tablet", "item 8007");
		MenuMatcher m = matcher();

		assertEquals("Cast Varrock Teleport", m.match(varrock).there.title(MenuText.ARROW));
		assertEquals("Varrock Teleport → Grand Exchange", m.match(ge).there.title(MenuText.ARROW));
		assertEquals("Teleport to House → Outside", m.match(house).there.title(MenuText.ARROW));
		assertEquals("Cast Teleport to House", m.match(inside).there.title(MenuText.ARROW));
		assertEquals("Minigame Teleport → Pest Control", m.match(pest).there.title(MenuText.ARROW));
		assertEquals("Varrock teleport → Varrock", m.match(tablet).there.title(MenuText.ARROW));
	}

	@Test
	void networkStopsKnowTheirClickAndTheChoiceThatLeadsThere()
	{
		object(200, "Fairy ring", 2996, 3114, 0, "Zanaris", "Configure", "Last-destination");
		facts.lists.put(MenuFacts.FAIRY_RINGS, List.of("AIQ", "CKQ"));
		facts.objects.put(210, new MenuFacts.Thing(210, List.of(new MenuFacts.Form(210, "Spirit tree", List.of("Travel", "Talk-to",
			"Last-destination"), List.of("Tree Gnome Village", "Grand Exchange", "Your house"), Map.of()))));
		facts.place(new MenuFacts.Placed(210, 3183, 3508, 0, 2, 2));
		TransportRecord aiq = stop("Fairy ring", "Fairy ring: AIQ (Mudskipper Point)", t(2996, 3114, 0));
		TransportRecord ckq = stop("Fairy ring", "Fairy ring: CKQ (Aldarin)", t(1359, 2941, 0)); // no ring placed there
		TransportRecord geTree = stop("Spirit tree", "Spirit tree: Grand Exchange", t(3185, 3508, 0));
		TransportRecord houseTree = stop("Spirit tree", "Spirit tree: your house (superior garden)", t(1857, 7065, 0));
		TransportRecord missing = stop("Fairy ring", "Fairy ring: ZZZ (nowhere)", t(2996, 3114, 0));
		MenuMatcher m = matcher();

		MenuText a = m.match(aiq).there;
		assertEquals(MenuTextBuilder.choice("Configure", "Fairy ring", "AIQ"), a);
		MenuText c = m.match(ckq).there;
		assertTrue(c.isChoiceOnly());
		assertEquals("Fairy ring → CKQ", MenuText.ride(a, c).title(MenuText.ARROW)); // a ride to CKQ still has its text
		assertEquals("Spirit tree → Grand Exchange", m.match(geTree).there.title(MenuText.ARROW));
		MenuMatcher.Result h = m.match(houseTree);
		assertEquals("Spirit tree → Your house", h.there.title(MenuText.ARROW));
		assertFalse(h.doubts.isEmpty());
		assertNull(m.match(missing).there);
	}

	private TransportRecord stop(String network, String name, int[] tile)
	{
		TransportRecord r = record(TransportRecord.Kind.NETWORK, name, "TRAVEL", tile, tile, -1, null);
		r.network = network;
		return r;
	}

	@Test
	void ridesAndHouseFurniture()
	{
		// a charter crew NPC: a multi-NPC whose shown form has the name and the option; its id from Shortest Path
		facts.npcs.put(1330, new MenuFacts.Thing(1330, List.of(new MenuFacts.Form(1330, null),
			new MenuFacts.Form(9324, "Trader Crewmember", "Talk-to", "Trade", "Charter"))));
		facts.lists.put(MenuFacts.CHARTER, List.of("Catherby", "Port Sarim"));
		hints.add(new MenuMatcher.Hint("charter_ships.tsv", t(3038, 3192, 0), t(2792, 3414, 0), "Charter", "Trader Crewmember", 1330));
		TransportRecord charter = record(TransportRecord.Kind.ONE_WAY, "Charter ship: Port Sarim to Catherby", "TRAVEL",
			t(3038, 3193, 0), t(2792, 3417, 1), -1, null);
		charter.network = "Charter ship";
		object(13523, "Amulet of Glory", -1, 0, 0, "Edgeville", "Karamja", "Draynor Village", "Al Kharid", "Remove");
		TransportRecord glory = record(TransportRecord.Kind.ONE_WAY, "House mounted glory: Karamja", "TRAVEL", t(1857, 7065, 0),
			t(2918, 3176, 0), -1, null);
		glory.network = "Player-owned house";
		TransportRecord box = record(TransportRecord.Kind.ONE_WAY, "House jewellery box: Castle Wars Arena", "TRAVEL", t(1857, 7065, 0),
			t(2440, 3090, 0), -1, null);
		box.network = "Player-owned house";
		MenuMatcher m = matcher();

		assertEquals(MenuTextBuilder.choice("Charter", "Trader Crewmember", "Catherby"), m.match(charter).there);
		assertEquals("Amulet of Glory → Karamja", m.match(glory).there.title(MenuText.ARROW));
		// a jewellery box ride without a box in its requirement: which list entries apply is not known
		MenuMatcher.Result b = m.match(box);
		assertNull(b.there);
		assertTrue(b.whyThere.contains("no box"), b.whyThere);
	}

	@Test
	void theJewelleryBoxAndThePortalNexusUseTheirListsWords()
	{
		// the list's own words ("Burthorpe"), with its key (script 1685)
		object(37496, "Basic Jewellery Box", -1, 0, 0, "Teleport Menu", "Burthorpe", "Upgrade", "Remove");
		object(37510, "Fancy Jewellery Box", -1, 0, 0, "Teleport Menu", "Warriors' Guild", "Upgrade", "Remove");
		object(33408, "Portal Nexus", -1, 0, 0, "Teleport", "Teleport Menu", "Configuration", "Upgrade", "Remove");
		facts.jewelleryBox.add(new MenuFacts.ListEntry("Burthorpe", "5", 1));
		facts.jewelleryBox.add(new MenuFacts.ListEntry("Monastery", "C", 2));
		facts.jewelleryBox.add(new MenuFacts.ListEntry("Cooking Guild", "H", 2));
		facts.jewelleryBox.add(new MenuFacts.ListEntry("Edgeville", "O", 3));
		facts.nexus.put("Varrock Teleport", List.of("Varrock", "Grand Exchange"));
		facts.nexus.put("Senntisten Teleport", List.of("Senntisten"));
		facts.nexus.put("Respawn Teleport", List.of("Respawn"));
		String any = "one of (house has basic jewellery box | house has fancy jewellery box | house has ornate jewellery box)";
		String fancy = "one of (house has fancy jewellery box | house has ornate jewellery box)";
		TransportRecord burthorpe = house("House jewellery box: Burthorpe Games Room", any);
		TransportRecord monastery = house("House jewellery box: Edgeville Monastery", fancy);
		TransportRecord cooks = house("House jewellery box: Cooks' Guild", fancy);
		TransportRecord senntisten = house("House portal (nexus or chamber): Senntisten Teleport",
			"one of (house has portal nexus: Senntisten Teleport | house has portal chamber: Senntisten Teleport)");
		TransportRecord ge = house("House portal (nexus or chamber): Varrock Teleport: Grand Exchange",
			"one of (house has portal nexus: Varrock Teleport | house has portal chamber: Varrock Teleport); varbit 4480 = 1");
		TransportRecord respawn = house("House portal (nexus or chamber): Respawn Teleport: Camelot",
			"one of (house has portal nexus: Respawn Teleport | house has portal chamber: Respawn Teleport)");
		MenuMatcher m = matcher();

		assertEquals(MenuTextBuilder.choice("Teleport Menu", "Jewellery Box", "Burthorpe", "5"), m.match(burthorpe).there);
		assertEquals("Jewellery Box → Burthorpe (5)", m.match(burthorpe).there.title(MenuText.ARROW));
		// the fancy box's entry, not the ornate box's "Edgeville"
		assertEquals("Jewellery Box → Monastery (C)", m.match(monastery).there.title(MenuText.ARROW));
		assertEquals("Jewellery Box → Cooking Guild (H)", m.match(cooks).there.title(MenuText.ARROW));
		// the nexus: its list's words, no key (it follows the player's own list)
		assertEquals(MenuTextBuilder.choice("Teleport Menu", "Portal Nexus", "Senntisten"), m.match(senntisten).there);
		assertEquals("Portal Nexus → Grand Exchange", m.match(ge).there.title(MenuText.ARROW));
		// "Camelot" is where the respawn point is, not a line of its own
		assertEquals("Portal Nexus → Respawn", m.match(respawn).there.title(MenuText.ARROW));
	}

	private TransportRecord house(String name, String requirements)
	{
		TransportRecord r = record(TransportRecord.Kind.ONE_WAY, name, "TRAVEL", t(1857, 7065, 0), t(2899, 3553, 0), -1, null);
		r.network = "Player-owned house";
		r.requirements = requirements;
		return r;
	}

	@Test
	void destinationWordsMatchOptionWords()
	{
		assertEquals(3, DestinationMatch.score("Draynor Village", "Draynor Village"));
		assertEquals(2, DestinationMatch.score("Rimmington house portal", "Rimmington"));
		assertEquals(2, DestinationMatch.score("Seers' Village", "Seers'"));
		assertEquals(1, DestinationMatch.score("Fenkenstrain's Castle", "Fenken' Castle"));
		assertEquals(1, DestinationMatch.score("Xeric's Lookout", "Look out"));
		assertEquals(1, DestinationMatch.score("Jatizso", "Jatiszo"));
		assertEquals(0, DestinationMatch.score("Port Sarim", "Port Khazard"));
		assertEquals(0, DestinationMatch.score("Teleport to house", "Teleport"));
		assertEquals(0, DestinationMatch.score("Cooks' Guild", "Cooking Guild"));
		List<String> doubts = new ArrayList<>();
		assertEquals("Karamja", DestinationMatch.best("Karamja", List.of("Edgeville", "Karamja"), doubts));
		assertTrue(doubts.isEmpty());
		assertNull(DestinationMatch.best("Lumbridge", List.of("Edgeville", "Karamja"), doubts));
		assertEquals("Amulet of glory", TeleportMenus.baseName("Amulet of glory(4)"));
		assertEquals("Construct. cape", TeleportMenus.baseName("Construct. cape(t)"));
		assertEquals("Slayer ring", TeleportMenus.baseName("Slayer ring (8)"));
		assertEquals("Ardougne cloak 1", TeleportMenus.baseName("Ardougne cloak 1"));
		assertEquals("Catherby", DestinationMatch.destination(charterRecord()));
	}

	private static TransportRecord charterRecord()
	{
		TransportRecord r = new TransportRecord();
		r.name = "Charter ship: Port Sarim to Catherby";
		return r;
	}

	@Test
	void directionsByPlaneAndUnderground()
	{
		assertEquals(OptionPicker.Dir.UP, OptionPicker.dir(3200, 0, 3200, 1));
		assertEquals(OptionPicker.Dir.DOWN, OptionPicker.dir(3200, 2, 3200, 1));
		assertEquals(OptionPicker.Dir.DOWN, OptionPicker.dir(3200, 0, 9600, 0));
		assertEquals(OptionPicker.Dir.UP, OptionPicker.dir(9600, 0, 3200, 0));
		assertEquals(OptionPicker.Dir.FLAT, OptionPicker.dir(3200, 0, 3210, 0));
		assertEquals(OptionPicker.Kind.UP, OptionPicker.kindOf("Climb-up"));
		assertEquals(OptionPicker.Kind.UP, OptionPicker.kindOf("Climb Up"));
		assertNull(OptionPicker.kindOf("Look-inside"));
	}
}
