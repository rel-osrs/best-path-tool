package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.Skill;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import com.bestpath.route.TransportSettings;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.gameval.SpriteID;
import org.junit.Test;

/** Which picture each step card shows, and the picture cache (with a fake provider instead of RuneLite's managers). */
public class IconChooserTest
{
	private static RouteStep step(RouteStep.Kind kind, StepInfo info, int from, int to)
	{
		return new RouteStep(kind, "x", "", 2, List.of(), "t-x", new int[0], from, to).withInfo(info);
	}

	private static StepInfo teleport(String name, String teleportCategory, int item, TransportSettings.Category c)
	{
		return new StepInfo(name, c, "TELEPORT", false, teleportCategory, "", item, List.of());
	}

	private static StepInfo transport(String category, boolean reverse, String network, List<StepInfo.Need> needs)
	{
		return new StepInfo("x", null, category, reverse, "", network, -1, needs);
	}

	private static final int SURFACE = Tiles.pack(3237, 3458, 0);
	private static final int SEWER = Tiles.pack(3237, 9858, 0);
	private static final int UPSTAIRS = Tiles.pack(3237, 3458, 1);

	@Test
	public void spellsShowTheirOwnIconOrTheMagicIcon()
	{
		assertEquals(IconKey.sprite(SpriteID.Magicon.VARROCK_TELEPORT), IconChooser.main(step(RouteStep.Kind.TELEPORT,
			teleport("Varrock Teleport: Grand Exchange", "spell", -1, TransportSettings.Category.TELEPORT_SPELLS), -1, -1)));
		assertEquals(IconKey.sprite(SpriteID.Magicon2.TELEPORT_TO_HOUSE), IconChooser.main(step(RouteStep.Kind.TELEPORT,
			teleport("Teleport to House (outside): Rimmington house portal", "spell", -1, TransportSettings.Category.HOUSE),
			-1, -1)));
		assertEquals(IconKey.sprite(SpriteID.Magicon2.TELEPORT_TO_APE_ATOLL),
			IconChooser.main(step(RouteStep.Kind.TELEPORT, teleport("Ape Atoll Teleport (standard)", "spell", -1, null), -1, -1)));
		assertEquals(IconKey.sprite(SpriteID.MagicNecroOn.APE_ATOLL_TELEPORT),
			IconChooser.main(step(RouteStep.Kind.TELEPORT, teleport("Ape Atoll Teleport (Arceuus)", "spell", -1, null), -1, -1)));
		assertEquals(IconKey.sprite(SpriteID.MagicNecroOn.RESPAWN_TELEPORT),
			IconChooser.main(step(RouteStep.Kind.TELEPORT, teleport("Respawn Teleport: Ferox Enclave", "spell", -1, null), -1, -1)));
		// no named sprite: the Magic skill icon
		assertEquals(IconKey.skill(Skill.MAGIC), IconChooser.main(step(RouteStep.Kind.TELEPORT,
			teleport("Battlefront Teleport", "spell", -1, null), -1, -1)));
		assertEquals(IconKey.skill(Skill.MAGIC), IconChooser.main(step(RouteStep.Kind.TELEPORT,
			teleport("Edgeville Home Teleport", "home teleport", -1, null), -1, -1)));
	}

	@Test
	public void itemTeleportsShowTheItem()
	{
		assertEquals(IconKey.item(1706), IconChooser.main(step(RouteStep.Kind.TELEPORT,
			teleport("Amulet of glory: Edgeville", "jewellery (consumable)", 1706, TransportSettings.Category.TELEPORT_ITEMS),
			-1, -1)));
		assertEquals(IconKey.sprite(SpriteID.Mapfunction.MINIGAME), IconChooser.main(step(RouteStep.Kind.TELEPORT,
			teleport("Minigame Teleport: Barbarian Assault", "grouping teleport", -1, null), -1, -1)));
		// an item teleport whose item is unknown, without a house: the kind label
		assertNull(IconChooser.main(step(RouteStep.Kind.TELEPORT, teleport("Odd", "other item (infinite)", -1, null), -1, -1)));
		assertEquals(IconKey.sprite(SpriteID.Mapfunction.HOUSE_PORTAL), IconChooser.main(step(RouteStep.Kind.TELEPORT,
			teleport("House tablet", "tablet", -1, TransportSettings.Category.HOUSE), -1, -1)));
	}

	@Test
	public void skillGatedStepsShowTheSkill()
	{
		StepInfo pipe = transport("SHORTCUT", false, "", List.of(StepInfo.Need.skill(Skill.AGILITY, 51)));
		assertEquals(IconKey.skill(Skill.AGILITY), IconChooser.main(step(RouteStep.Kind.TRAVERSAL, pipe, SURFACE, SURFACE)));
		StepInfo cave = transport("ENTER", false, "", List.of(StepInfo.Need.skill(Skill.FARMING, 65)));
		assertEquals(IconKey.skill(Skill.FARMING), IconChooser.main(step(RouteStep.Kind.TRAVERSAL, cave, SURFACE, SEWER)));
		// a shortcut without a level: the map's shortcut icon
		assertEquals(IconKey.sprite(SpriteID.Mapfunction.AGILITY_SHORT_CUT), IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("SHORTCUT", false, "", List.of()), SURFACE, SURFACE)));
	}

	@Test
	public void laddersPointTheWayTheyAreUsed()
	{
		int up = SpriteID.Mapfunction.MAP_LINK_UPSTAIRS;
		int down = SpriteID.Mapfunction.MAP_LINK_DOWNSTAIRS;
		// the manhole: CLIMB_DOWN on one plane
		assertEquals(IconKey.sprite(down), IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("CLIMB_DOWN", false, "", List.of()), SURFACE, SEWER)));
		// a CLIMB_UP ladder used from its end goes down
		assertEquals(IconKey.sprite(down), IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("CLIMB_UP", true, "", List.of()), SEWER, SURFACE)));
		assertEquals(IconKey.sprite(up), IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("CLIMB_UP", false, "", List.of()), SEWER, SURFACE)));
		// a plane change says it whatever the category
		assertEquals(IconKey.sprite(up), IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("CLIMB_DOWN", false, "", List.of()), SURFACE, UPSTAIRS)));
		// CLIMB_BOTH on one plane: surface to underground is down, back is up; else can't tell
		assertEquals(IconKey.sprite(down), IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("CLIMB_BOTH", false, "", List.of()), SURFACE, SEWER)));
		assertEquals(IconKey.sprite(up), IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("CLIMB_BOTH", false, "", List.of()), SEWER, SURFACE)));
		assertNull(IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("CLIMB_BOTH", false, "", List.of()), SURFACE, SURFACE)));
	}

	@Test
	public void ridesCavesGatesAndTheRest()
	{
		assertEquals(IconKey.sprite(SpriteID.Mapfunction.TRANSPORTATION), IconChooser.main(step(RouteStep.Kind.BOAT,
			transport("TRAVEL", false, "Fairy ring", List.of(StepInfo.Need.quest("Fairytale II - Cure a Queen",
				com.bestpath.core.req.Requirement.Quest.Wanted.STARTED))), SURFACE, SURFACE)));
		assertEquals(IconKey.sprite(SpriteID.Mapfunction.HOUSE_PORTAL), IconChooser.main(step(RouteStep.Kind.BOAT,
			new StepInfo("Portal", TransportSettings.Category.HOUSE, "TRAVEL", false, "", "Player-owned house", -1, List.of()),
			SURFACE, SURFACE)));
		assertEquals(IconKey.sprite(SpriteID.Mapfunction.DUNGEON), IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("EXIT", false, "", List.of()), SEWER, SURFACE)));
		// a gate: what it needs
		assertEquals(IconKey.item(StepInfo.COINS_ITEM), IconChooser.main(step(RouteStep.Kind.TRAVERSAL,
			transport("PASS", false, "", List.of(StepInfo.Need.coins(10))), SURFACE, SURFACE)));
		assertNull(IconChooser.main(step(RouteStep.Kind.TRAVERSAL, transport("PASS", false, "", List.of()), SURFACE, SURFACE)));
		assertNull(IconChooser.main(step(RouteStep.Kind.TRAVERSAL, transport("CUSTOM", false, "", List.of()), SURFACE, SURFACE)));
		// a gate (kind Door) shows what it needs too
		assertEquals(IconKey.sprite(SpriteID.SideIcons.QUEST), IconChooser.main(step(RouteStep.Kind.DOOR,
			transport("PASS", false, "", List.of(StepInfo.Need.quest("Dragon Slayer II",
				com.bestpath.core.req.Requirement.Quest.Wanted.DONE))), SURFACE, SURFACE)));
		// doors, walks, sails and steps without data keep the kind label
		assertNull(IconChooser.main(step(RouteStep.Kind.DOOR, StepInfo.NONE, SURFACE, SURFACE)));
		assertNull(IconChooser.main(step(RouteStep.Kind.TRAVERSAL, StepInfo.NONE, SURFACE, SURFACE)));
		assertNull(IconChooser.main(new RouteStep(RouteStep.Kind.WALK, "w", "", 1, List.of(), null, new int[]{1, 2}, -1)));
	}

	@Test
	public void sailingStepsShowTheBoat()
	{
		// Board, Sail, Leave, bring the boat, release it: the boat's picture, as in the Settings tab
		for (RouteStep.Kind k : new RouteStep.Kind[]{RouteStep.Kind.BOAT, RouteStep.Kind.SAIL, RouteStep.Kind.TELEPORT})
		{
			assertEquals(IconKey.sprite(SpriteID.IconSailingBoats35x35._2), IconChooser.main(step(k,
				StepInfo.sailing(com.bestpath.core.sea.BoatType.SLOOP), SURFACE, SURFACE)));
		}
		assertEquals(IconKey.sprite(SpriteID.IconSailingBoats35x35._0), IconChooser.main(step(RouteStep.Kind.SAIL,
			StepInfo.sailing(com.bestpath.core.sea.BoatType.RAFT), SURFACE, SURFACE)));
		assertEquals(IconChooser.boat(com.bestpath.core.sea.BoatType.SKIFF), IconChooser.main(step(RouteStep.Kind.BOAT,
			StepInfo.sailing(com.bestpath.core.sea.BoatType.SKIFF), SURFACE, SURFACE)));
		// boat not known: the kind label
		assertTrue(StepInfo.sailing(null) == StepInfo.NONE);
		assertNull(IconChooser.main(step(RouteStep.Kind.SAIL, StepInfo.NONE, SURFACE, SURFACE)));
	}

	@Test
	public void needsIcons()
	{
		assertEquals(IconKey.smallSkill(Skill.AGILITY), IconChooser.need(StepInfo.Need.skill(Skill.AGILITY, 51)));
		assertEquals(IconKey.sprite(SpriteID.SideIcons.QUEST), IconChooser.need(StepInfo.Need.quest("Plague City",
			com.bestpath.core.req.Requirement.Quest.Wanted.DONE)));
		assertEquals(IconKey.item(563), IconChooser.need(StepInfo.Need.rune("law", 563, 1)));
		assertEquals(IconKey.item(net.runelite.api.gameval.ItemID.GIANT_STOPWATCH),
			IconChooser.need(StepInfo.Need.cooldown(com.bestpath.core.req.Cooldown.HOME)));
		assertEquals(IconKey.item(8007), IconChooser.need(StepInfo.Need.item("8007", 8007, 1, false, true)));
	}

	@Test
	public void settingsRowsUseTheCardsPictures()
	{
		assertEquals(IconKey.sprite(SpriteID.SideIcons.MAGIC), IconChooser.category(TransportSettings.Category.TELEPORT_SPELLS));
		assertEquals(IconKey.item(net.runelite.api.gameval.ItemID.AMULET_OF_GLORY_6),
			IconChooser.category(TransportSettings.Category.TELEPORT_ITEMS));
		assertEquals(IconKey.sprite(IconChooser.HOUSE), IconChooser.category(TransportSettings.Category.HOUSE));
		assertEquals(IconKey.sprite(IconChooser.UP), IconChooser.category(TransportSettings.Category.STAIRS_LADDERS));
		assertEquals(IconKey.sprite(IconChooser.SHORTCUT), IconChooser.category(TransportSettings.Category.SHORTCUTS));
		assertEquals(IconKey.sprite(SpriteID.PohDoors.CLOSED), IconChooser.category(TransportSettings.Category.DOORS));
		assertEquals(IconKey.sprite(IconChooser.TRANSPORT), IconChooser.category(TransportSettings.Category.BOATS));
		assertEquals(IconKey.item(StepInfo.COINS_ITEM), IconChooser.avoid(TransportSettings.Avoid.COST));
		assertEquals(IconKey.item(563), IconChooser.avoid(TransportSettings.Avoid.RUNES));
		assertEquals(IconKey.sprite(SpriteID.EXCLAMATION_MARK), IconChooser.avoid(TransportSettings.Avoid.KNOWN_BUGS));
		assertEquals(IconKey.item(net.runelite.api.gameval.ItemID.GIANT_STOPWATCH),
			IconChooser.avoid(TransportSettings.Avoid.COOLDOWN));
		// the preview draws every one of them
		for (TransportSettings.Category c : TransportSettings.Category.values())
		{
			IconKey k = IconChooser.category(c);
			assertTrue(c.name(), k.getType() == IconKey.Type.ITEM ? IconLists.allItems().contains(k.getId())
				: IconLists.allSprites().containsKey(k.getId()));
		}
		for (TransportSettings.Avoid a : TransportSettings.Avoid.values())
		{
			IconKey k = IconChooser.avoid(a);
			assertTrue(a.name(), k.getType() == IconKey.Type.ITEM ? IconLists.allItems().contains(k.getId())
				: IconLists.allSprites().containsKey(k.getId()));
		}
	}

	/** Every spell and home teleport in the plugin's data has its spell icon, except these (RuneLite names no sprite). */
	@Test
	public void everySpellInTheDataHasAnIconOrIsKnownNotTo() throws Exception
	{
		Set<String> noSprite = Set.of("Battlefront Teleport", "Edgeville Home Teleport", "Lunar Home Teleport",
			"Arceuus Home Teleport");
		List<String> spells = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
			IconChooserTest.class.getResourceAsStream("/com/bestpath/data/transports.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.startsWith("#"))
				{
					continue;
				}
				TransportLine t = TransportLine.parse(line);
				String tc = t.getTeleportCategory();
				if (t.getKind() == TransportLine.Kind.TELEPORT && (tc.equals("spell") || tc.equals("home teleport")))
				{
					spells.add(t.getName());
				}
			}
		}
		assertTrue(spells.size() > 60);
		List<String> missing = new ArrayList<>();
		for (String s : spells)
		{
			if (IconChooser.spellSprite(s) == null && !noSprite.contains(s))
			{
				missing.add(s);
			}
		}
		assertEquals(List.of(), missing);
	}

	/** A provider that hands pictures over only when told to (like ItemManager's, drawn a moment later). */
	private static final class LaterProvider implements IconProvider
	{
		final Map<IconKey, Integer> asked = new HashMap<>();
		final List<Runnable> pending = new ArrayList<>();

		@Override
		public void load(IconKey key, Consumer<BufferedImage> done)
		{
			asked.merge(key, 1, Integer::sum);
			pending.add(() -> done.accept(new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB)));
		}
	}

	@Test
	public void picturesArriveLaterAndAreAskedForOnce()
	{
		LaterProvider p = new LaterProvider();
		IconCache cache = new IconCache(p);
		int[] repaints = {0};
		IconKey glory = IconKey.item(1706);
		assertNull(cache.get(glory, 18, 16, () -> repaints[0]++));
		assertNull(cache.get(glory, 34, 30, () -> repaints[0]++));
		assertEquals(1, (int) p.asked.get(glory));
		p.pending.forEach(Runnable::run);
		assertEquals(2, repaints[0]);
		BufferedImage small = cache.get(glory, 18, 16, null);
		assertNotNull(small);
		assertEquals(18, small.getWidth());
		assertEquals(16, small.getHeight());
		// the same scaled copy is reused; the provider is not asked again
		assertTrue(small == cache.get(glory, 18, 16, null));
		assertEquals(1, (int) p.asked.get(glory));
		BufferedImage big = cache.get(glory, 34, 30, null);
		assertEquals(30, big.getHeight());
	}

	@Test
	public void picturesAreFittedWithoutBlur()
	{
		// a 15x15 map icon is doubled pixel for pixel in the card's 34x30 box; a 24x24 spell icon keeps its size
		assertEquals(30, IconCache.fit(new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB), 34, 30).getWidth());
		assertEquals(24, IconCache.fit(new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB), 34, 30).getWidth());
		BufferedImage item = IconCache.fit(new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB), 34, 30);
		assertEquals(34, item.getWidth());
		assertEquals(30, item.getHeight());
	}

	@Test
	public void withoutPicturesNothingIsWaitedForForever()
	{
		IconCache cache = new IconCache(IconProvider.NONE);
		for (int i = 0; i < 1000; i++)
		{
			assertNull(cache.get(IconKey.item(1), 18, 16, () ->
			{
			}));
		}
	}
}
