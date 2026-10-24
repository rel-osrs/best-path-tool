package com.bestpath.player;

import com.bestpath.core.req.Cooldown;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The raw values read from the game for one player snapshot, before
 * {@link SnapshotMapper} turns them into core's player model. Plain data:
 * filled on the client thread, mapped at once, never shared between threads.
 */
public final class GameReading
{
	/** Real-world tile (instance-safe). */
	public int x;
	public int y;
	public int plane;
	/** Real (not boosted) level per {@link Skill} ordinal. */
	public final int[] levels = new int[Skill.values().length];
	/** On a members world. */
	public boolean members;
	/** Inventory: item id to count (stacks and repeated slots added up). */
	public final Map<Integer, Integer> inventory = new HashMap<>();
	/** Worn item per {@link EquipSlot} ordinal, -1 = empty. */
	public final int[] worn = new int[EquipSlot.values().length];
	/** Bank: item id to count; null = the bank was never seen on this account. */
	public Map<Integer, Integer> bank;
	/** Only the varbits / varps some requirement mentions. */
	public final Map<Integer, Integer> varbits = new HashMap<>();
	public final Map<Integer, Integer> varps = new HashMap<>();
	/** Only the quests some requirement mentions (any case); others count as not started. */
	public final Map<String, QuestState> quests = new HashMap<>();
	/** Raw value of varbit SPELLBOOK (4070). */
	public int spellbookValue;
	/** Raw value of varbit POH_HOUSE_LOCATION (2187); 0 = no house. */
	public int houseValue;
	/**
	 * The game's own name for {@link #houseValue} (enum {@link SnapshotMapper#HOUSE_PLACE_ENUM}), or null; used only for
	 * a value our table doesn't know (a new house place).
	 */
	public String houseName;
	/** What is built in the house ("house has <thing>" names), as the last scan of the player's own house found. */
	public final Set<String> houseThings = new TreeSet<>();
	/** The keyboard key of each line of the house portal nexus's list ("Senntisten" -> "M"), as the last scan found. */
	public final Map<String, String> nexusKeys = new LinkedHashMap<>();
	/** Rune pouch slots: rune item id (already looked up from the slot's rune type) and count; 0 = empty. */
	public final int[] pouchItems = new int[SnapshotMapper.POUCH_SLOTS];
	public final int[] pouchCounts = new int[SnapshotMapper.POUCH_SLOTS];
	/**
	 * Player answers saved for this account ("answer ..." requirements): the data's words -> yes (true) / no (false);
	 * not in it = not answered yet (asked on the step card).
	 */
	public final Map<String, Boolean> answers = new LinkedHashMap<>();
	/** Item ids seen in this account's collection log ("collection log ..." requirements). */
	public final Set<Integer> collectionLog = new TreeSet<>();
	/** Places whose spirit tree was last seen grown on this account ("spirit tree ..." requirements). */
	public final Set<String> spiritTrees = new TreeSet<>();
	/**
	 * Whether an axe was last seen stored at the canoe stations on this account ("canoe axe stored" requirements); null =
	 * not known yet (maybe stored).
	 */
	public Boolean canoeAxeStored;
	/**
	 * Sailing: the boats routes may use ({@link SnapshotMapper#boats}); the one the player stands on is
	 * {@link Boat#AT_START} and then x, y, plane are that boat's tile.
	 */
	public List<Boat> boats = List.of();
	/**
	 * Long-cooldown teleports: the minute (since 1970 UTC) each was last used, by {@link Cooldown} ordinal (varps 892
	 * and 888; 0 = never).
	 */
	public final int[] cooldownLastUse = new int[Cooldown.values().length];
	/** The game's minute clock (varp DATE_MINUTES, 3078); 0 = not sent. */
	public int gameMinute;
	/** The computer's clock when this was read (ms since 1970; {@link CooldownWatch#nowMinute}). */
	public long clockMillis;

	public GameReading()
	{
		Arrays.fill(levels, 1);
		levels[Skill.HITPOINTS.ordinal()] = 10;
		Arrays.fill(worn, -1);
	}
}
