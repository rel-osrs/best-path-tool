package com.bestpath.ui;

import com.bestpath.route.StepInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Tests and the icon previews: every picture the sidebar can show. */
public final class IconLists
{
	private IconLists()
	{
	}

	/** Every sprite the sidebar can show, with a label saying what it should look like. */
	public static Map<Integer, String> allSprites()
	{
		Map<Integer, String> m = new LinkedHashMap<>();
		m.put(IconChooser.UP, "Mapfunction.MAP_LINK_UPSTAIRS (ladders/stairs up)");
		m.put(IconChooser.DOWN, "Mapfunction.MAP_LINK_DOWNSTAIRS (ladders/stairs down)");
		m.put(IconChooser.SHORTCUT, "Mapfunction.AGILITY_SHORT_CUT (shortcuts)");
		m.put(IconChooser.DUNGEON, "Mapfunction.DUNGEON (caves in/out)");
		m.put(IconChooser.TRANSPORT, "Mapfunction.TRANSPORTATION (boats, rides)");
		m.put(IconChooser.HOUSE, "Mapfunction.HOUSE_PORTAL (house)");
		m.put(IconChooser.BANK, "Mapfunction.BANK (a route that begins at a bank)");
		m.put(IconChooser.MINIGAME, "Mapfunction.MINIGAME (minigame teleports)");
		m.put(IconChooser.QUEST, "SideIcons.QUEST (quests needed)");
		m.put(IconChooser.SPELLBOOK, "SideIcons.MAGIC (Settings: spells)");
		m.put(IconChooser.DOOR, "PohDoors.CLOSED (Settings: gates and barriers)");
		m.put(IconChooser.WARNING, "EXCLAMATION_MARK (Settings: known bugs)");
		m.put(IconChooser.RAFT, "IconSailingBoats35x35._0 (raft: Settings, sailing steps)");
		m.put(IconChooser.SKIFF, "IconSailingBoats35x35._1 (skiff: Settings, sailing steps)");
		m.put(IconChooser.SLOOP, "IconSailingBoats35x35._2 (sloop: Settings, sailing steps)");
		new TreeMap<>(IconChooser.SPELLS).forEach((name, id) -> m.putIfAbsent(id, "spell: " + name));
		m.put(IconChooser.WILDERNESS_SKULL, "PvpwIcons.PLAYER_KILLER_SKULL (cards in the Wilderness)");
		return m;
	}

	/** The items the Settings tab shows. */
	public static Set<Integer> allItems()
	{
		return Set.of(IconChooser.TELEPORT_ITEM, IconChooser.RUNE, IconChooser.COOLDOWN, StepInfo.COINS_ITEM,
			IconChooser.MOUNTED_GLORY, IconChooser.MOUNTED_MYTHICAL_CAPE, IconChooser.MOUNTED_DIGSITE_PENDANT,
			IconChooser.MOUNTED_XERICS_TALISMAN);
	}
}
