package com.bestpath.player;

import java.util.Locale;
import java.util.Set;
import net.runelite.api.gameval.VarbitID;

/**
 * Whether an axe is stored at the canoe stations ("canoe axe stored" requirements; detected in play, never asked),
 * remembered per account (RuneLite account profile, like the spirit trees). Pure Java, one thread (the client
 * thread). Saved only when it changed.
 *
 * <p>What the game shows (nothing in the game cache holds it: no var, no client script, no station option): each
 * canoe station has an NPC with a "Store-axe" option (the ten below, names from the cache). The same option stores
 * the axe or hands it back: the NPC says {@value #STORED} or {@value #RETURNED} (an NPC dialogue chat message,
 * "name|words"). The storage is shared by every station. Chopping down or shaping a canoe with no axe carried or worn
 * also shows an axe is stored.
 */
public final class CanoeAxe
{
	/** The NPC's words when the player's axe was taken into storage. */
	public static final String STORED = "I'll hold onto that axe for you.";
	/** The NPC's words when the stored axe was handed back. */
	public static final String RETURNED = "Here's your axe.";

	/** The canoe station NPCs with the "Store-axe" option (NPC ids 1323-1326, 10370, 15601-15605). */
	public static final Set<String> NPCS = Set.of("tarquin", "sigurd", "hari", "barfy bill", "marten", "kaiquir",
		"amergin", "pete", "porkai", "lara");

	/**
	 * The canoe station state varbits (River Lum: Lumbridge, Champions' Guild, Barbarian Village, Edgeville, Ferox
	 * Enclave; River Dougne: Castle Wars, Tree Gnome Stronghold, Ardougne Zoo, Chaos Druid Tower, Tree Gnome Village).
	 */
	public static final Set<Integer> STATION_VARBITS = Set.of(VarbitID.CANOESTATION_STATE_LUMBRIDGE,
		VarbitID.CANOESTATION_STATE_CHAMPIONSGUILD, VarbitID.CANOESTATION_STATE_BARBARIANVILLAGE,
		VarbitID.CANOESTATION_STATE_EDGEVILLE, VarbitID.CANOESTATION_STATE_SANCTUARY,
		VarbitID.CANOESTATION_STATE_CASTLE_WARS, VarbitID.CANOESTATION_STATE_TREE_GNOME_STRONGHOLD,
		VarbitID.CANOESTATION_STATE_ARDOUGNE_ZOO, VarbitID.CANOESTATION_STATE_CHAOS_DRUID_TOWER,
		VarbitID.CANOESTATION_STATE_TREE_GNOME_VILLAGE);

	/** The station's menu options that need an axe (its tree and its log). */
	public static final Set<String> AXE_OPTIONS = Set.of("chop-down", "shape-canoe");

	private final AccountSettings.Store store;
	/** Last seen: stored, not stored, or null = never seen on this account. */
	private Boolean stored;

	public CanoeAxe(AccountSettings.Store store)
	{
		this.store = store;
	}

	/** Loads the account's saved value (startup, account switch); never seen = not known (null). */
	public void load()
	{
		String s = store.read();
		stored = "stored".equals(s) ? Boolean.TRUE : "not stored".equals(s) ? Boolean.FALSE : null;
	}

	/**
	 * Whether an axe was last seen stored at the canoe stations; null = not known (a question mark on the card, never
	 * assumed absent).
	 */
	public Boolean getStored()
	{
		return stored;
	}

	/**
	 * An axe was seen stored or taken back.
	 *
	 * @return true if that differs from what was known (then saved; routes may change)
	 */
	public boolean see(boolean nowStored)
	{
		if (Boolean.valueOf(nowStored).equals(stored))
		{
			return false;
		}
		stored = nowStored;
		store.write(nowStored ? "stored" : "not stored");
		return true;
	}

	/**
	 * A dialogue chat message ("name|words", tags removed): TRUE when a canoe station NPC took the axe, FALSE when it
	 * handed it back, null for anything else.
	 */
	public static Boolean fromDialogue(String message)
	{
		if (message == null)
		{
			return null;
		}
		int bar = message.indexOf('|');
		if (bar <= 0 || !NPCS.contains(message.substring(0, bar).trim().toLowerCase(Locale.ROOT)))
		{
			return null;
		}
		String words = message.substring(bar + 1).trim();
		if (words.equals(STORED))
		{
			return Boolean.TRUE;
		}
		return words.equals(RETURNED) ? Boolean.FALSE : null;
	}

	/** True if a menu option on a canoe station needs an axe ("Chop-down", "Shape-Canoe"). */
	public static boolean needsAxe(String option, String plainTarget)
	{
		return option != null && plainTarget != null && AXE_OPTIONS.contains(option.trim().toLowerCase(Locale.ROOT))
			&& plainTarget.trim().equalsIgnoreCase("Canoe Station");
	}

	/**
	 * True if a station's new state shows the axe was just used: the tree chopped down (9 falling, 10 fallen, as the
	 * station's multiloc forms) or the log shaped (1 log .. 4 waka canoe).
	 */
	public static boolean axeUsed(int stationState)
	{
		return stationState == 9 || stationState == 10 || (stationState >= 1 && stationState <= 4);
	}
}
