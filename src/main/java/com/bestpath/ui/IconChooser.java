package com.bestpath.ui;

import com.bestpath.core.data.TickGroups;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.BoatType;
import com.bestpath.player.HouseView;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import com.bestpath.route.TransportRules;
import com.bestpath.route.TransportSettings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;

/**
 * Which picture a step card shows, from what the step knows (its kind, the
 * transport's sort and what it needs). Pure: no pictures are loaded here.
 * Every sprite is a named constant of RuneLite's public
 * {@code net.runelite.api.gameval.SpriteID}; no sprite id is guessed.
 * Null = no picture: the card keeps its coloured kind label.
 */
public final class IconChooser
{
	/** Ladders and stairs going up (the world map's "upstairs" link icon). */
	static final int UP = SpriteID.Mapfunction.MAP_LINK_UPSTAIRS;
	/** Ladders, stairs and holes going down. */
	static final int DOWN = SpriteID.Mapfunction.MAP_LINK_DOWNSTAIRS;
	/** Shortcuts without a skill level (the world map's agility shortcut icon). */
	static final int SHORTCUT = SpriteID.Mapfunction.AGILITY_SHORT_CUT;
	/** Cave and dungeon entrances and exits. */
	static final int DUNGEON = SpriteID.Mapfunction.DUNGEON;
	/** Boats, fairy rings, carts and other rides (the world map's transportation icon). */
	static final int TRANSPORT = SpriteID.Mapfunction.TRANSPORTATION;
	/** The player's house (the world map's house portal icon). */
	static final int HOUSE = SpriteID.Mapfunction.HOUSE_PORTAL;
	/** The world map's bank symbol (bank steps). */
	static final int BANK = SpriteID.Mapfunction.BANK;
	/** Minigame (grouping) teleports. */
	static final int MINIGAME = SpriteID.Mapfunction.MINIGAME;
	/** A quest a step needs (the quest tab's icon). */
	static final int QUEST = SpriteID.SideIcons.QUEST;
	/** Settings tab: spells (the spell book, the Magic tab's icon). */
	static final int SPELLBOOK = SpriteID.SideIcons.MAGIC;
	/** Settings tab: gates and barriers (a door, the house viewer's closed-door icon). */
	static final int DOOR = SpriteID.PohDoors.CLOSED;
	/** Settings tab: known bugs (a red "!"). */
	static final int WARNING = SpriteID.EXCLAMATION_MARK;
	/** Settings tab: teleport items (Amulet of glory(6)). */
	static final int TELEPORT_ITEM = ItemID.AMULET_OF_GLORY_6;
	/** Settings tab: teleports that use runes (a law rune, as on the cards' rune needs). */
	static final int RUNE = ItemID.LAWRUNE;
	/** Settings tab: long-cooldown teleports (a stopwatch). */
	static final int COOLDOWN = ItemID.GIANT_STOPWATCH;
	/** The skull of the game's own Wilderness level display (cache: interface 90 component 46, sprite 1046). */
	static final int WILDERNESS_SKULL = SpriteID.PvpwIcons.PLAYER_KILLER_SKULL;
	/**
	 * Settings tab, Sailing, and the sailing step cards: the boat kinds' pictures, the sprites the game's boat table
	 * names for each kind (DB table 166 column 41: raft 7111, skiff 7112, sloop 7113), RuneLite's
	 * {@code IconSailingBoats35x35}.
	 */
	static final int RAFT = SpriteID.IconSailingBoats35x35._0;
	static final int SKIFF = SpriteID.IconSailingBoats35x35._1;
	static final int SLOOP = SpriteID.IconSailingBoats35x35._2;

	/**
	 * Spell names in the transport data (the part before ":" or " (") -> the spell's icon, enabled version. Spells
	 * missing here (Battlefront Teleport and the Ancient, Lunar and Arceuus home teleports: RuneLite names no sprite
	 * for them) get the Magic skill icon. Tests read it.
	 */
	static final Map<String, Integer> SPELLS;

	static
	{
		Map<String, Integer> m = new HashMap<>();
		// standard spellbook
		m.put("Varrock Teleport", SpriteID.Magicon.VARROCK_TELEPORT);
		m.put("Lumbridge Teleport", SpriteID.Magicon.LUMBRIDGE_TELEPORT);
		m.put("Falador Teleport", SpriteID.Magicon.FALADOR_TELEPORT);
		m.put("Camelot Teleport", SpriteID.Magicon.CAMELOT_TELEPORT);
		m.put("Ardougne Teleport", SpriteID.Magicon.ARDOUGNE_TELEPORT);
		m.put("Watchtower Teleport", SpriteID.Magicon.WATCHTOWER_TELEPORT);
		m.put("Trollheim Teleport", SpriteID.Magicon2.TROLLHEIM_TELEPORT);
		m.put("Kourend Castle Teleport", SpriteID.Magicon2.TELEPORT_TO_KOUREND);
		m.put("Civitas illa Fortis Teleport", SpriteID.Magicon2.CIVITAS_ILLA_FORTIS_TELEPORT);
		m.put("Ape Atoll Teleport (standard)", SpriteID.Magicon2.TELEPORT_TO_APE_ATOLL);
		m.put("Teleport to House", SpriteID.Magicon2.TELEPORT_TO_HOUSE);
		m.put("Lumbridge Home Teleport", SpriteID.Magicon2.LUMBRIDGE_HOME_TELEPORT);
		// ancient
		m.put("Paddewwa Teleport", SpriteID.Magicon2.PADDEWWA_TELEPORT);
		m.put("Senntisten Teleport", SpriteID.Magicon2.SENNTISTEN_TELEPORT);
		m.put("Kharyrll Teleport", SpriteID.Magicon2.KHARYRLL_TELEPORT);
		m.put("Lassar Teleport", SpriteID.Magicon2.LASSAR_TELEPORT);
		m.put("Dareeyak Teleport", SpriteID.Magicon2.DAREEYAK_TELEPORT);
		m.put("Carrallanger Teleport", SpriteID.Magicon2.CARRALLANGAR_TELEPORT); // the data's spelling
		m.put("Carrallangar Teleport", SpriteID.Magicon2.CARRALLANGAR_TELEPORT);
		m.put("Annakarl Teleport", SpriteID.Magicon2.ANNAKARL_TELEPORT);
		m.put("Ghorrock Teleport", SpriteID.Magicon2.GHORROCK_TELEPORT);
		// lunar
		m.put("Moonclan Teleport", SpriteID.LunarMagicOn.MOONCLAN_TELEPORT);
		m.put("Ourania Teleport", SpriteID.LunarMagicOn.OURANIA_TELEPORT);
		m.put("Waterbirth Teleport", SpriteID.LunarMagicOn.WATERBIRTH_TELEPORT);
		m.put("Barbarian Teleport", SpriteID.LunarMagicOn.BARBARIAN_TELEPORT);
		m.put("Khazard Teleport", SpriteID.LunarMagicOn.KHAZARD_TELEPORT);
		m.put("Fishing Guild Teleport", SpriteID.LunarMagicOn.FISHING_GUILD_TELEPORT);
		m.put("Catherby Teleport", SpriteID.LunarMagicOn.CATHERBY_TELEPORT);
		m.put("Ice Plateau Teleport", SpriteID.LunarMagicOn.ICE_PLATEAU_TELEPORT);
		m.put("Tele Group Moonclan", SpriteID.LunarMagicOn.TELE_GROUP_MOONCLAN);
		m.put("Tele Group Waterbirth", SpriteID.LunarMagicOn.TELE_GROUP_WATERBIRTH);
		m.put("Tele Group Barbarian", SpriteID.LunarMagicOn.TELE_GROUP_BARBARIAN);
		m.put("Tele Group Khazard", SpriteID.LunarMagicOn.TELE_GROUP_KHAZARD);
		m.put("Tele Group Fishing Guild", SpriteID.LunarMagicOn.TELE_GROUP_FISHING_GUILD);
		m.put("Tele Group Catherby", SpriteID.LunarMagicOn.TELE_GROUP_CATHERBY);
		m.put("Tele Group Ice Plateau", SpriteID.LunarMagicOn.TELE_GROUP_ICE_PLATEAU);
		// arceuus
		m.put("Arceuus Library Teleport", SpriteID.MagicNecroOn.ARCEUUS_LIBRARY_TELEPORT);
		m.put("Draynor Manor Teleport", SpriteID.MagicNecroOn.DRAYNOR_MANOR_TELEPORT);
		m.put("Mind Altar Teleport", SpriteID.MagicNecroOn.MIND_ALTAR_TELEPORT);
		m.put("Salve Graveyard Teleport", SpriteID.MagicNecroOn.SALVE_GRAVEYARD_TELEPORT);
		m.put("Fenkenstrain's Castle Teleport", SpriteID.MagicNecroOn.FENKENSTRAINS_CASTLE_TELEPORT);
		m.put("West Ardougne Teleport", SpriteID.MagicNecroOn.WEST_ARDOUGNE_TELEPORT);
		m.put("Harmony Island Teleport", SpriteID.MagicNecroOn.HARMONY_ISLAND_TELEPORT);
		m.put("Cemetery Teleport", SpriteID.MagicNecroOn.CEMETERY_TELEPORT);
		m.put("Barrows Teleport", SpriteID.MagicNecroOn.BARROWS_TELEPORT);
		m.put("Ape Atoll Teleport (Arceuus)", SpriteID.MagicNecroOn.APE_ATOLL_TELEPORT);
		m.put("Respawn Teleport", SpriteID.MagicNecroOn.RESPAWN_TELEPORT);
		SPELLS = Collections.unmodifiableMap(m);
	}

	private IconChooser()
	{
	}

	/** Settings tab, "House (POH)": the mounted items' pictures (the item each one is mounted from). */
	static final int MOUNTED_GLORY = ItemID.AMULET_OF_GLORY_INF;
	static final int MOUNTED_MYTHICAL_CAPE = ItemID.MYTHICAL_CAPE;
	static final int MOUNTED_DIGSITE_PENDANT = ItemID.NECKLACE_OF_DIGSITE_5;
	static final int MOUNTED_XERICS_TALISMAN = ItemID.XERIC_TALISMAN;

	/**
	 * The Settings tab's picture for one line of the "House (POH)" section: the mounted items' items, a portal's
	 * spell icon (when the spell has one), altars the spell book, the garden's spirit tree / fairy ring the rides'
	 * transportation icon, anything else (jewellery boxes, the nexus, the obelisk) the house portal icon.
	 */
	static IconKey houseRow(HouseView.Row row)
	{
		switch (row.getKind())
		{
			case MOUNTED_GLORY:
				return IconKey.item(MOUNTED_GLORY);
			case MOUNTED_MYTHICAL_CAPE:
				return IconKey.item(MOUNTED_MYTHICAL_CAPE);
			case MOUNTED_DIGSITE_PENDANT:
				return IconKey.item(MOUNTED_DIGSITE_PENDANT);
			case MOUNTED_XERICS_TALISMAN:
				return IconKey.item(MOUNTED_XERICS_TALISMAN);
			case PORTAL_CHAMBER:
			{
				Integer spell = row.getDestination() == null ? null : SPELLS.get(row.getDestination());
				return IconKey.sprite(spell != null ? spell : HOUSE);
			}
			case ALTAR:
				return IconKey.sprite(SPELLBOOK);
			case GARDEN:
				return IconKey.sprite(TRANSPORT);
			default:
				return IconKey.sprite(HOUSE);
		}
	}

	/**
	 * The Settings tab's icon for a transport type: the same pictures as the step cards (spells: the spell book;
	 * teleport items: an item; the house portal; ladders: the "upstairs" icon; shortcuts; doors; rides: the
	 * transportation icon).
	 */
	static IconKey category(TransportSettings.Category c)
	{
		switch (c)
		{
			case TELEPORT_SPELLS:
				return IconKey.sprite(SPELLBOOK);
			case TELEPORT_ITEMS:
				return IconKey.item(TELEPORT_ITEM);
			case HOUSE:
				return IconKey.sprite(HOUSE);
			case STAIRS_LADDERS:
				return IconKey.sprite(UP);
			case SHORTCUTS:
				return IconKey.sprite(SHORTCUT);
			case DOORS:
				return IconKey.sprite(DOOR);
			case BOATS:
			default:
				return IconKey.sprite(TRANSPORT);
		}
	}

	/**
	 * The Settings tab's icon for a teleport category's or network's tick row: the icon of the type row its
	 * transports are in (spells: the spell book; teleport items: the glory amulet; the house; rides: the
	 * transportation icon); null for a category in no type.
	 */
	static IconKey tickGroup(TickGroups.Group g)
	{
		// rows named in GROUP_ICONS get their own picture; the rest get their type's icon (rides such as the spirit
		// tree, gnome glider or charter ship: the transport icon)
		IconKey own = GROUP_ICONS.get(g.getName().trim().toLowerCase(Locale.ROOT));
		if (own != null)
		{
			return own;
		}
		if (g.getKind() == TickGroups.Kind.COINS)
		{
			return IconKey.item(StepInfo.COINS_ITEM);
		}
		TransportSettings.Category c = TransportRules.typeOf(g);
		return c == null ? null : category(c);
	}

	/** "Teleport and travel ticks" rows' pictures by name (lower case); the rest: their type's. */
	private static final Map<String, IconKey> GROUP_ICONS = new HashMap<>();

	static
	{
		GROUP_ICONS.put("spell", IconKey.sprite(SPELLBOOK));
		GROUP_ICONS.put("home teleport", IconKey.sprite(SpriteID.Magicon2.LUMBRIDGE_HOME_TELEPORT));
		GROUP_ICONS.put("grouping teleport", IconKey.sprite(MINIGAME));
		GROUP_ICONS.put("tablet", IconKey.item(ItemID.POH_TABLET_VARROCKTELEPORT)); // Varrock teleport
		GROUP_ICONS.put("scroll", IconKey.item(ItemID.TELEPORTSCROLL_DIGSITE)); // Digsite teleport
		GROUP_ICONS.put("jewellery (infinite)", IconKey.item(ItemID.AMULET_OF_GLORY_INF)); // Amulet of eternal glory
		GROUP_ICONS.put("jewellery (consumable)", IconKey.item(ItemID.NECKLACE_OF_MINIGAMES_8)); // Games necklace(8)
		GROUP_ICONS.put("cape or diary item (infinite)", IconKey.item(ItemID.SKILLCAPE_MAX)); // Max cape
		GROUP_ICONS.put("cape or diary item (consumable)", IconKey.item(ItemID.SKILLCAPE_AD)); // Achievement diary cape
		GROUP_ICONS.put("other item (infinite)", IconKey.item(ItemID.ECTOPHIAL)); // Ectophial
		GROUP_ICONS.put("other item (consumable)", IconKey.item(ItemID.MOURNING_TELEPORT_CRYSTAL_1)); // Teleport crystal (1)
		GROUP_ICONS.put("fairy ring", IconKey.item(ItemID.DRAMEN_STAFF)); // Dramen staff
		GROUP_ICONS.put("hot air balloon", IconKey.item(ItemID.LOGS)); // Logs (a balloon flight's fuel)
		GROUP_ICONS.put("canoe", IconKey.item(ItemID.BRONZE_AXE)); // Bronze axe (a canoe is cut with one)
		GROUP_ICONS.put("eagle", IconKey.item(ItemID.ROPE)); // Rope (the eagles need one)
		GROUP_ICONS.put("wilderness obelisk", IconKey.sprite(WILDERNESS_SKULL));
		GROUP_ICONS.put("lake molch boat", IconKey.sprite(RAFT));
		GROUP_ICONS.put("great conch rowboat", IconKey.sprite(RAFT));
		GROUP_ICONS.put("burgh de rott boat", IconKey.sprite(RAFT));
		GROUP_ICONS.put("player-owned house", IconKey.sprite(HOUSE));
	}

	/** The Settings tab's picture for a boat kind (null: a kind routes don't know, no picture). */
	static IconKey boat(BoatType type)
	{
		if (type == null)
		{
			return null;
		}
		switch (type)
		{
			case RAFT:
				return IconKey.sprite(RAFT);
			case SKIFF:
				return IconKey.sprite(SKIFF);
			case SLOOP:
			default:
				return IconKey.sprite(SLOOP);
		}
	}

	/** The Settings tab's picture for "a shipwright may bring my boat": coins (it costs coins). */
	static IconKey shipwright()
	{
		return IconKey.item(StepInfo.COINS_ITEM);
	}

	/** The Settings tab's icon for an "avoid" rule (cost: coins; runes: a rune; known bugs: "!"; cooldown: a stopwatch). */
	static IconKey avoid(TransportSettings.Avoid a)
	{
		switch (a)
		{
			case COST:
				return IconKey.item(StepInfo.COINS_ITEM);
			case RUNES:
				return IconKey.item(RUNE);
			case KNOWN_BUGS:
				return IconKey.sprite(WARNING);
			case WILDERNESS:
				return IconKey.sprite(WILDERNESS_SKULL);
			case COOLDOWN:
			default:
				return IconKey.item(COOLDOWN);
		}
	}

	/** The spell's icon for a teleport name in the data, or null (no named sprite for it). */
	static Integer spellSprite(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = name.trim();
		Integer s = SPELLS.get(n);
		int colon = n.indexOf(':');
		if (s == null && colon > 0)
		{
			n = n.substring(0, colon).trim();
			s = SPELLS.get(n);
		}
		int paren = n.indexOf(" (");
		if (s == null && paren > 0)
		{
			s = SPELLS.get(n.substring(0, paren).trim());
		}
		return s;
	}

	/** The card's big picture for a step, or null (keep the coloured kind label). */
	static IconKey main(RouteStep step)
	{
		StepInfo info = step.getInfo();
		// sailing steps (board, sail, leave, bring the boat, release it from its bottle): the boat's picture, as in the
		// Settings tab
		if (info.getBoat() != null && step.getKind() != RouteStep.Kind.WALK)
		{
			return boat(info.getBoat());
		}
		switch (step.getKind())
		{
			case WALK:
			case SAIL:
				return null;
			case TELEPORT:
				return teleport(info);
			case BANK:
				return IconKey.sprite(BANK);
			default:
				break;
		}
		if (info == StepInfo.NONE)
		{
			return null;
		}
		// "Go into the Wilderness": a walk over the line, no transport: the game's skull
		if (step.getTransportId() == null && info.isWilderness())
		{
			return IconKey.sprite(WILDERNESS_SKULL);
		}
		if (info.getSettingsCategory() == TransportSettings.Category.HOUSE)
		{
			return IconKey.sprite(HOUSE);
		}
		// agility shortcuts and other skill-gated steps: the skill icon
		for (StepInfo.Need n : info.getNeeds())
		{
			if (n.getType() == StepInfo.Need.Type.SKILL)
			{
				return IconKey.skill(n.getSkill());
			}
		}
		String category = info.getCategory();
		if (info.getSettingsCategory() == TransportSettings.Category.BOATS || !info.getNetwork().isEmpty()
			|| "TRAVEL".equals(category))
		{
			return IconKey.sprite(TRANSPORT);
		}
		switch (category)
		{
			case "SHORTCUT":
				return IconKey.sprite(SHORTCUT);
			case "ENTER":
			case "EXIT":
				return IconKey.sprite(DUNGEON);
			case "CLIMB_UP":
			case "CLIMB_DOWN":
			case "CLIMB_BOTH":
			{
				int dir = climbDirection(step, info);
				return dir > 0 ? IconKey.sprite(UP) : dir < 0 ? IconKey.sprite(DOWN) : null;
			}
			case "PASS":
				// a gate or barrier: what it needs (an item, coins, a quest), if anything
				for (StepInfo.Need n : info.getNeeds())
				{
					IconKey k = need(n);
					if (k != null)
					{
						return k;
					}
				}
				return null;
			default:
				return null;
		}
	}

	private static IconKey teleport(StepInfo info)
	{
		String tc = info.getTeleportCategory();
		if (tc.equals("spell") || tc.equals("home teleport"))
		{
			Integer s = spellSprite(info.getName());
			return s != null ? IconKey.sprite(s) : IconKey.skill(Skill.MAGIC);
		}
		if (tc.equals("grouping teleport"))
		{
			return IconKey.sprite(MINIGAME);
		}
		if (info.getItemId() >= 0)
		{
			return IconKey.item(info.getItemId());
		}
		if (info.getSettingsCategory() == TransportSettings.Category.HOUSE)
		{
			return IconKey.sprite(HOUSE);
		}
		return null;
	}

	/**
	 * Up (1) or down (-1) for a ladder or stairs: a change of plane says it; otherwise the data's category
	 * (CLIMB_UP / CLIMB_DOWN, swapped when used from its end); for CLIMB_BOTH on one plane, from the surface into the
	 * underground (y 6400 and more) is down and back is up; 0 = can't tell.
	 */
	static int climbDirection(RouteStep step, StepInfo info)
	{
		int from = step.getMarkerTile();
		int to = step.getEndTile();
		if (from != -1 && to != -1 && Tiles.plane(to) != Tiles.plane(from))
		{
			return Tiles.plane(to) > Tiles.plane(from) ? 1 : -1;
		}
		int sign = info.isReverse() ? -1 : 1;
		if ("CLIMB_UP".equals(info.getCategory()))
		{
			return sign;
		}
		if ("CLIMB_DOWN".equals(info.getCategory()))
		{
			return -sign;
		}
		if (from == -1 || to == -1)
		{
			return 0;
		}
		boolean fromUnder = Tiles.y(from) >= UNDERGROUND_Y;
		boolean toUnder = Tiles.y(to) >= UNDERGROUND_Y;
		return fromUnder == toUnder ? 0 : toUnder ? -1 : 1;
	}

	/** Underground areas sit at y 6400 and above (the game's map layout). */
	static final int UNDERGROUND_Y = 6400;

	/** Clue card: a level or quest row's icon. */
	static IconKey clueOther(String text)
	{
		if (text.startsWith("Quest: "))
		{
			return IconKey.sprite(QUEST);
		}
		int sp = text.indexOf(' ');
		Skill s = sp < 0 ? null : Skill.byName(text.substring(0, sp));
		return s == null ? null : IconKey.smallSkill(s);
	}

	/** The small picture before one thing a step needs. */
	static IconKey need(StepInfo.Need n)
	{
		switch (n.getType())
		{
			case SKILL:
				return IconKey.smallSkill(n.getSkill());
			case QUEST:
				return IconKey.sprite(QUEST);
			case ITEM:
			case RUNE:
			case COINS:
				return n.getItemId() >= 0 ? IconKey.item(n.getItemId()) : null;
			case COOLDOWN:
				return IconKey.item(COOLDOWN); // the stopwatch of the Settings tab's "Long-cooldown teleports"
			case WILDERNESS:
				return IconKey.sprite(WILDERNESS_SKULL);
			default:
				return null;
		}
	}
}
