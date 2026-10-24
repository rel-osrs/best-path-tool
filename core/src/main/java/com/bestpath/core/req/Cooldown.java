package com.bestpath.core.req;

import java.util.Locale;

/**
 * The teleports with a long cooldown, and how the game keeps it. Routes use them only while they are ready.
 * <ul>
 * <li>The game stores the minute (counted from 1 January 1970, UTC) the player last used one in a varp: 892 for
 * the home teleports (RuneLite gameval {@code VarPlayerID.AIDE_TELE_TIMER}, the older {@code
 * VarPlayer.LAST_HOME_TELEPORT}), 888 for the minigame (grouping) teleports ({@code VarPlayerID.SLUG2_REGIONUID},
 * the older {@code VarPlayer.LAST_MINIGAME_TELEPORT}). 0 = never used.</li>
 * <li>The game's client scripts: script 2614 greys out the four spellbooks' home teleports while
 * {@code varp 3078 - varp 892 < 30} and the four Minigame Teleport spells while {@code varp 3078 - varp 888 < 20};
 * script 9145 greys the Grouping tab's teleport button with the same minigame test; the buff bar's timers (script
 * 5923 with 5941) count down {@code 30 - (varp 3078 - varp 892)} and {@code 20 - (varp 3078 - varp 888)} minutes.
 * Varp 3078 is the game's own minute clock ({@code VarPlayerID.DATE_MINUTES}); no client script writes it (only
 * the server sets it).</li>
 * <li>OSRS Wiki (development time): "Home Teleport" rev 15348879: a 30-minute cooldown shared by every home
 * teleport, not shared with the minigame teleports; "Grouping" rev 15364976: minigame teleports have a 20-minute
 * cooldown shared with the Minigame Teleport spell.</li>
 * </ul>
 * Which teleports: those with the data's long-cooldown flag ({@code c}), by teleport category ("home teleport",
 * "grouping teleport"). A flagged teleport of another category has no known cooldown var and is treated as always
 * ready.
 *
 * <p>Not modelled: the game's own exception in script 9712 (inside the Vampyrium during a part of the quest with
 * varbit MYQ6 70..125 the home teleport is not greyed out).
 */
public enum Cooldown
{
	HOME("home teleport", "Home teleport", 892, 30),
	MINIGAME("grouping teleport", "Minigame teleport", 888, 20);

	/** The game's minute clock, minutes since 1970 UTC (RuneLite gameval {@code VarPlayerID.DATE_MINUTES}). */
	public static final int DATE_MINUTES_VARP = 3078;

	private final String teleportCategory;
	private final String label;
	private final int varp;
	private final int minutes;

	Cooldown(String teleportCategory, String label, int varp, int minutes)
	{
		this.teleportCategory = teleportCategory;
		this.label = label;
		this.varp = varp;
		this.minutes = minutes;
	}

	/** "Home teleport", "Minigame teleport". */
	public String getLabel()
	{
		return label;
	}

	/** The varp holding the minute it was last used (0 = never). */
	public int getVarp()
	{
		return varp;
	}

	/** How long it cools down after a use. */
	public int getMinutes()
	{
		return minutes;
	}

	/** The minute (since 1970 UTC) it is ready again after a use in minute {@code lastUse}. */
	public long readyAt(int lastUse)
	{
		return (long) lastUse + minutes;
	}

	/**
	 * True while it is cooling down: used in minute {@code lastUse} (0 = never) and {@code now} is less than its
	 * minutes later. The game's own test (script 2614: {@code now - lastUse < 30}).
	 */
	public boolean coolingDown(int lastUse, long now)
	{
		return lastUse > 0 && now < readyAt(lastUse);
	}

	/**
	 * The cooldown a transport of the data has: its long-cooldown flag set and its teleport category one of these;
	 * null otherwise (no cooldown, or one whose game var is not known: treated as always ready).
	 */
	public static Cooldown of(boolean cooldownFlag, String teleportCategory)
	{
		if (!cooldownFlag || teleportCategory == null)
		{
			return null;
		}
		String c = teleportCategory.trim().toLowerCase(Locale.ROOT);
		for (Cooldown d : values())
		{
			if (d.teleportCategory.equals(c))
			{
				return d;
			}
		}
		return null;
	}
}
