package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Menu texts (the step card says what the player clicks) and the transports.tsv menu columns. */
class MenuTextTest
{
	@Test
	void titlesFollowTheThreeForms()
	{
		assertEquals("Climb-up Ladder", MenuText.verb("Climb-up", "Ladder").title(MenuText.ARROW));
		assertEquals("Amulet of glory → Edgeville", MenuText.named("Edgeville", "Amulet of glory").title(MenuText.ARROW));
		assertEquals("Construct. cape → Rimmington", MenuTextBuilder.choice("Teleport", "Construct. cape", "Rimmington").title(MenuText.ARROW));
		assertEquals("Construct. cape -> Rimmington", MenuTextBuilder.choice("Teleport", "Construct. cape", "Rimmington").title("->"));
		// the game's menu line is always option then target
		assertEquals("Edgeville Amulet of glory", MenuTextDiagnostics.menuLine(MenuText.named("Edgeville", "Amulet of glory")));
		assertEquals("Teleport Construct. cape", MenuTextDiagnostics.menuLine(MenuTextBuilder.choice("Teleport", "Construct. cape", "Rimmington")));
	}

	@Test
	void formatReadsBackTheSame()
	{
		for (MenuText m : new MenuText[]{MenuText.verb("Climb-up", "Ladder"), MenuText.named("Grand Exchange", "Varrock Teleport"),
			MenuTextBuilder.choice("Configure", "Fairy ring", "CKQ"), MenuTextBuilder.choiceOnly("BLQ")})
		{
			assertEquals(m, MenuText.parse(m.format()));
		}
		assertEquals("v|Climb-up|Ladder", MenuText.verb("Climb-up", "Ladder").format());
		assertEquals("c|Teleport|Construct. cape|Rimmington", MenuTextBuilder.choice("Teleport", "Construct. cape", "Rimmington").format());
		assertEquals("c|||BLQ", MenuTextBuilder.choiceOnly("BLQ").format());
		assertNull(MenuText.parse(""));
		assertNull(MenuText.parse(null));
	}

	@Test
	void aChoiceCanCarryItsKeyboardKey()
	{
		// the key that picks the entry in the game's list (jewellery box: fixed, from the cache)
		MenuText burthorpe = MenuTextBuilder.choice("Teleport Menu", "Jewellery Box", "Burthorpe", "5");
		assertEquals("Jewellery Box -> Burthorpe (5)", burthorpe.title("->"));
		assertEquals("5", MenuTextDiagnostics.key(burthorpe));
		assertEquals("c|Teleport Menu|Jewellery Box|Burthorpe|5", burthorpe.format());
		assertEquals(burthorpe, MenuText.parse(burthorpe.format()));
		assertEquals("Teleport Menu Jewellery Box", MenuTextDiagnostics.menuLine(burthorpe));
		// the portal nexus's keys depend on the player's own list: added later, or taken off
		MenuText senntisten = MenuTextBuilder.choice("Teleport Menu", "Portal Nexus", "Senntisten");
		assertNull(MenuTextDiagnostics.key(senntisten));
		assertEquals("Portal Nexus -> Senntisten (M)", senntisten.withKey("M").title("->"));
		assertEquals(senntisten, senntisten.withKey("M").withKey(null));
		assertTrue(!senntisten.equals(senntisten.withKey("M")));
		// only a choice has a key
		MenuText ladder = MenuText.verb("Climb-up", "Ladder");
		assertEquals(ladder, ladder.withKey("1"));
		assertNull(MenuTextDiagnostics.key(ladder));
		assertThrows(IllegalArgumentException.class, () -> MenuText.parse("c|a|b|c|d|e"));
		assertThrows(IllegalArgumentException.class, () -> MenuText.parse("v|a|b|c"));
	}

	@Test
	void badTextsAreRefused()
	{
		assertThrows(IllegalArgumentException.class, () -> MenuText.parse("x|a|b"));
		assertThrows(IllegalArgumentException.class, () -> MenuText.parse("v|a"));
		assertThrows(IllegalArgumentException.class, () -> MenuText.parse("c|a|b"));
		assertThrows(IllegalArgumentException.class, () -> MenuText.parse("v||Ladder"));
		assertThrows(IllegalArgumentException.class, () -> MenuText.parse("c|Travel||Etceteria"));
		assertThrows(IllegalArgumentException.class, () -> MenuText.verb("Climb|up", "Ladder"));
		assertThrows(IllegalArgumentException.class, () -> MenuText.verb("", "Ladder"));
	}

	@Test
	void networkRidesJoinTheBoardingClickWithTheLandingChoice()
	{
		MenuText grandExchange = MenuTextBuilder.choice("Travel", "Spirit tree", "Grand Exchange");
		MenuText portSarim = MenuTextBuilder.choice("Travel", "Spirit Tree", "Port Sarim");
		MenuText ride = MenuText.ride(grandExchange, portSarim);
		assertEquals("Spirit tree → Port Sarim", ride.title(MenuText.ARROW));
		assertEquals("Travel Spirit tree", MenuTextDiagnostics.menuLine(ride));
		// a stop with only its choice: rides to it have a text, rides from it do not
		MenuText yubiusk = MenuTextBuilder.choiceOnly("BLQ");
		assertTrue(yubiusk.isChoiceOnly());
		assertEquals("Fairy ring → BLQ", MenuText.ride(MenuTextBuilder.choice("Configure", "Fairy ring", "CKQ"), yubiusk).title(MenuText.ARROW));
		assertNull(MenuText.ride(yubiusk, grandExchange));
		assertNull(MenuText.ride(null, grandExchange));
		assertNull(MenuText.ride(grandExchange, MenuText.verb("Climb-up", "Ladder")));
	}

	private static TransportLine ladder(MenuText there, MenuText back)
	{
		return TransportLine.builder("t-1", TransportLine.Kind.TWO_WAY).name("Ladder").category("CLIMB_UP")
			.start(3200, 3201, 0).end(3200, 3201, 1).ticks(2).ticksEstimate(true).object(16683, 3200, 3202, 0)
			.menu(there, back).build();
	}

	@Test
	void transportLinesKeepTheirMenuTexts()
	{
		TransportLine a = ladder(MenuText.verb("Climb-up", "Ladder"), MenuText.verb("Climb-down", "Ladder"));
		String text = TransportLineWriter.write(a);
		assertEquals(TransportLine.COLUMNS, text.split("\t", -1).length);
		assertTrue(text.endsWith("\tv|Climb-up|Ladder\tv|Climb-down|Ladder"));
		TransportLine b = TransportLine.parse(text);
		assertEquals(text, TransportLineWriter.write(b));
		assertEquals(MenuText.verb("Climb-up", "Ladder"), b.getMenu());
		assertEquals(MenuText.verb("Climb-down", "Ladder"), b.getMenuBack());
		assertEquals(b.getMenu(), b.menuFor(true));
		assertEquals(b.getMenuBack(), b.menuFor(false));
		// unknown texts are empty columns
		TransportLine c = TransportLine.parse(TransportLineWriter.write(ladder(null, MenuText.verb("Climb-down", "Ladder"))));
		assertNull(c.getMenu());
		assertEquals("Climb-down Ladder", c.getMenuBack().title(MenuText.ARROW));
		// only a two-way transport keeps a way back
		TransportLine t = TransportLine.builder("t-2", TransportLine.Kind.TELEPORT).name("Varrock Teleport")
			.category("TELEPORT").end(3212, 3424, 0).ticks(5).ticksEstimate(true).teleportCategory("spell")
			.menu(MenuText.verb("Cast", "Varrock Teleport"), MenuText.verb("x", "y")).build();
		assertNull(TransportLine.parse(TransportLineWriter.write(t)).getMenuBack());
		assertEquals("Cast Varrock Teleport", TransportLine.parse(TransportLineWriter.write(t)).getMenu().title(MenuText.ARROW));
		assertEquals(MenuText.verb("Open", "Door"), TransportLineBuilder.withMenu(a, MenuText.verb("Open", "Door"), null).getMenu());
	}

	private static TransportLine stop(String id, String name, int x, int y, MenuText menu)
	{
		return TransportLine.builder(id, TransportLine.Kind.NETWORK).name(name).category("TRAVEL").start(x, y, 0)
			.end(x, y, 0).ticks(3).ticksEstimate(true).network("Spirit tree").menu(menu, null).build();
	}

	@Test
	void aStepsMenuTextFollowsTheWayItIsUsed()
	{
		TransportLine ladder = ladder(MenuText.verb("Climb-up", "Ladder"), MenuText.verb("Climb-down", "Ladder"));
		com.bestpath.core.path.Transport both = com.bestpath.core.path.Transport.between("t-1", "Ladder", 3200, 3201, 0, 3200, 3201, 1,
			2, true, true);
		assertEquals("Climb-up Ladder", TransportMenus.of(both, ladder, List.of(ladder), 3200, 3201, 0).title(MenuText.ARROW));
		assertEquals("Climb-down Ladder", TransportMenus.of(both, ladder, List.of(ladder), 3200, 3201, 1).title(MenuText.ARROW));
		// a two-way transport split per direction: its way back has its own id
		com.bestpath.core.path.Transport back = com.bestpath.core.path.Transport.between("t-1:back", "Ladder", 3200, 3201, 1, 3200,
			3201, 0, 2, false, true);
		assertEquals("Climb-down Ladder", TransportMenus.of(back, ladder, List.of(ladder), 3200, 3201, 1).title(MenuText.ARROW));
		// a network ride: the boarding stop's click, the landing stop's choice
		TransportLine ge = stop("s-1", "Spirit tree: Grand Exchange", 3185, 3508, MenuTextBuilder.choice("Travel", "Spirit tree", "Grand Exchange"));
		TransportLine sarim = stop("s-2", "Spirit tree: Port Sarim", 3060, 3256, MenuTextBuilder.choice("Travel", "Spirit Tree", "Port Sarim"));
		com.bestpath.core.path.Transport ride = com.bestpath.core.path.Transport.between("Spirit tree:a>b", "Spirit tree: Port Sarim",
			3185, 3508, 0, 3060, 3256, 0, 3, false, true).usedFromBeside();
		assertEquals("Spirit tree → Port Sarim", TransportMenus.of(ride, sarim, List.of(ge, sarim), 3185, 3508, 0).title(MenuText.ARROW));
		assertNull(TransportMenus.of(ride, sarim, List.of(sarim), 3185, 3508, 0)); // boarding stop not found
		assertNull(TransportMenus.of(null, sarim, List.of(sarim), 0, 0, 0));
	}

	@Test
	void linesWithoutTheMenuColumnsStillRead()
	{
		// an older export: 15 columns
		String old = String.join("\t", "t-1", "TWO_WAY", "Ladder", "CLIMB_UP", "3200,3201,0", "3200,3201,1", "2", "e", "", "", "", "",
			"", "16683@3200,3202,0", "");
		TransportLine l = TransportLine.parse(old);
		assertEquals("Ladder", l.getName());
		assertEquals(16683, l.getObjectId());
		assertNull(l.getMenu());
		assertNull(l.getMenuBack());
		// written again it has the (empty) menu columns
		assertEquals(old + "\t\t", TransportLineWriter.write(l));
		assertThrows(IllegalArgumentException.class, () -> TransportLine.parse(old + "\tv|Climb-up|Ladder"));
	}
}
