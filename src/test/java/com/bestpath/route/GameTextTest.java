package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.MenuText;
import com.bestpath.core.data.TransportLine;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/**
 * In-game text over 40 characters is shortened (the game's own menu texts excepted).
 */
public class GameTextTest
{
	private static String named(String name, boolean teleport, boolean forward)
	{
		String title = teleport ? StepTitles.teleportTitle(name) : name;
		return GameText.of(title, false, name, teleport, forward);
	}

	@Test
	public void ridesSayWhereTheyGoInTheDirectionUsed()
	{
		String boat = "Boat or ship: Port Sarim <-> Land's End (Veos / Captain Magoro)";
		assertEquals("Boat -> Land's End", named(boat, false, true));
		assertEquals("Boat -> Port Sarim", named(boat, false, false));
		assertEquals("Balloon -> Castle Wars", named("Hot air balloon: Crafting Guild to Castle Wars", false, true));
		assertEquals("Carpet -> Sophanem", named("Magic carpet: Pollnivneach (south) to Sophanem", false, true));
		assertEquals("Carpet -> Pollnivneach", named("Magic carpet: Shantay Pass to Pollnivneach (north)", false, true));
		assertEquals("Minecart -> White Wolf Mountain", named("Keldagrim minecart: Keldagrim to White Wolf Mountain", false, true));
		assertEquals("Eagle -> Feldip Hills", named("Eagle: Eagles' Peak <-> Feldip Hills (jungle eagle)", false, true));
		assertEquals("Guide -> Lumbridge Castle cellar", named("Dorgeshuun guide: Lumbridge Castle cellar (Kazgar)", false, true));
		assertEquals("Saradominist recruiter -> Castle Wars", named("Saradominist recruiter: Grand Exchange to Castle Wars", false, true));
	}

	@Test
	public void whoAndWhereInBracketsAreLeftOut()
	{
		assertEquals("Wilderness obelisk: Level 19", named("Wilderness obelisk: Level 19 (east of the Graveyard of Shadows)", false, true));
		assertEquals("House portal: Pollnivneach", named("House portal: Pollnivneach (into and out of your house)", false, true));
		assertEquals("Fairy ring: AIR-DLR-DJQ-AJS", named("Fairy ring: AIR-DLR-DJQ-AJS (Fairy Resistance Hideout)", false, true));
		assertEquals("Mythical cape -> Myths' Guild", named("House mounted mythical cape: Myths' Guild", false, true));
		// a name without "Kind: " stays
		assertEquals("Spirit tree", named("Spirit tree", false, true));
	}

	@Test
	public void teleportsAreWrittenLikeTheMenuTitles()
	{
		assertEquals("Ring of returning -> Civitas illa Fortis", named("Ring of returning: Civitas illa Fortis", true, true));
		assertEquals("Hunter cape -> Wilderness Hunter area", named("Hunter cape: Wilderness Hunter area", true, true));
		assertEquals("Revenant cave teleport (level 17)",
			named("Revenant cave teleport (scroll): Revenant Caves level 17 entrance", true, true));
		assertEquals("Teleport: Varrock", named("Varrock", true, true)); // nothing to shorten
	}

	@Test
	public void theGamesMenuTextStaysButForAFewLongItemNames()
	{
		assertEquals("Quetzal whistle -> Fortis Colosseum",
			GameText.of("Perfected quetzal whistle(i) -> Fortis Colosseum", true, "x", true, true));
		assertEquals("Quetzal whistle -> Hunter Guild", GameText.of("Basic quetzal whistle -> Hunter Guild", true, "x", true, true));
		assertEquals("Diary cape -> Lumbridge & Draynor", GameText.of("Achievement diary cape -> Lumbridge & Draynor", true, "x", true, true));
		assertEquals("Memoirs -> Lunch by the Lancalliums", GameText.of("Kharedst's memoirs -> Lunch by the Lancalliums", true, "x", true, true));
		assertEquals("Climb-up Ladder", GameText.of("Climb-up Ladder", true, "Ladder: up", false, true));
	}

	@Test
	public void bankWordsOver40CharactersGiveTheCount()
	{
		RouteStep bank = new RouteStep(RouteStep.Kind.BANK, "Bank at Varrock west bank", "", 30, List.of(), null, new int[0], -1);
		assertEquals("Take out: Spade, Feather x5",
			com.bestpath.overlay.StepTargets.words(bank, List.of("Spade", "Feather x5"), false));
		assertEquals("Take out 3 items (see the side panel)", com.bestpath.overlay.StepTargets.words(bank,
			List.of("Perfected quetzal whistle(i)", "Amulet of glory(4)", "Coins x30"), false));
		assertEquals("Take out 3 items (see the box)", com.bestpath.overlay.StepTargets.words(bank,
			List.of("Perfected quetzal whistle(i)", "Amulet of glory(4)", "Coins x30"), true));
		// any other step: its in-game words
		RouteStep boat = new RouteStep(RouteStep.Kind.BOAT, "Boat or ship: Port Sarim <-> Land's End (Veos)", "", 10, List.of(),
			"t", new int[0], -1).withGameText("Boat -> Land's End");
		assertEquals("Boat -> Land's End", com.bestpath.overlay.StepTargets.words(boat, List.of(), false));
		assertEquals("Boat or ship: Port Sarim <-> Land's End (Veos)", boat.getTitle());
		assertEquals("a new title drops the old words", "House portal", boat.withTitle("House portal").getGameText());
	}

	@Test
	public void sailingNamesTheDockNotTheBoat()
	{
		assertEquals("Sail -> Void Knights' Outpost", GameText.sail("Sail Jolly Raven to Void Knights' Outpost"));
		assertEquals("Sail -> Rellekka", GameText.sail("Sail to Rellekka"));
		assertEquals("Board Jolly Raven", GameText.sail("Board Jolly Raven"));
	}

	/**
	 * Every transport in the plugin data, both ways: the in-game words are at most 40 characters, but for the game's own
	 * menu texts, which are kept as they are (1-2 characters over).
	 */
	@Test
	public void everyTransportsInGameWordsAreAtMost40CharactersOnTheRealData() throws Exception
	{
		InputStream in = GameTextTest.class.getResourceAsStream("/com/bestpath/data/transports.tsv");
		if (in == null)
		{
			return; // no exported data
		}
		List<TransportLine> lines = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String s;
			while ((s = r.readLine()) != null)
			{
				if (!s.startsWith("#") && !s.isBlank())
				{
					lines.add(TransportLine.parse(s));
				}
			}
		}
		Set<String> over = new TreeSet<>();
		int n = 0;
		for (TransportLine l : lines)
		{
			boolean teleport = l.getKind() == TransportLine.Kind.TELEPORT;
			for (boolean forward : new boolean[]{true, false})
			{
				if (!forward && l.getKind() != TransportLine.Kind.TWO_WAY)
				{
					continue;
				}
				MenuText menu = forward ? l.getMenu() : l.getMenuBack();
				String title = StepTitles.transportTitle(com.bestpath.core.path.Transport.teleport(l.getId(), l.getName(), 0, 0, 0, 1, true),
					menu, teleport);
				String words = GameText.of(title, menu != null, l.getName(), teleport, forward);
				n++;
				if (words.length() > 40)
				{
					over.add(words);
				}
			}
		}
		assertTrue(n > 1000);
		assertEquals("the game's menu texts kept as they are", Set.of(
			"Amulet of eternal glory -> Draynor Village",
			"Cross Extra slippery basalt stepping stone",
			"Minigame Teleport -> Burthorpe Games Room",
			"Minigame Teleport -> Guardians of the Rift",
			"Slayer ring (eternal) -> Fremennik Dungeon",
			"Slayer ring (eternal) -> Wyrmscraig Cavern",
			"Spirit tree -> Battlefield of Khazard (3)"), over);
	}
}
