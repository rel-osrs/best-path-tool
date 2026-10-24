package com.bestpath.player;

import com.bestpath.core.req.Cooldown;
import java.util.Arrays;

/**
 * Follows the long-cooldown teleports (home, minigame) for the plugin, to notice the moment one turns ready again or
 * starts cooling down; routes use them only while ready ({@link Cooldown} has what the game keeps).
 *
 * <p>"Now" is the game's own minute clock (varp DATE_MINUTES, the clock the game's own scripts compare against),
 * but never more than a minute behind the computer's clock, in case the game stops sending it
 * ({@link #nowMinute}). The player snapshot decides "ready" with the same rule ({@link SnapshotMapper}).
 *
 * <p>No polling: it is fed the game's values when they change (the last-use varps and the minute clock, from
 * VarbitChanged) and asked once per game tick whether the stored ready time has passed on the computer's clock, a
 * single comparison. It answers "changed" only when a teleport turned ready or started cooling down, so the route is
 * checked once at that moment, not every minute. Pure (no client). Client thread only.
 */
public final class CooldownWatch
{
	private static final long MINUTE_MS = 60_000L;

	/** The minute each was last used (0 = never / not seen yet), by {@link Cooldown} ordinal. */
	private final int[] lastUse = new int[Cooldown.values().length];
	/** The game's minute clock (0 = not sent yet). */
	private int gameMinute;
	/** The cooldowns cooling down at the last decision, one bit per {@link Cooldown} ordinal (tests read it). */
	int cooling;
	/**
	 * When to decide again on the computer's clock (ms since 1970), or {@link Long#MAX_VALUE} (nothing cooling; tests
	 * read it).
	 */
	long lookAgainAt = Long.MAX_VALUE;

	/**
	 * The minute (since 1970 UTC) "now" is for the cooldowns: the game's minute clock, or the computer's clock less a
	 * minute when that is later (the game's clock not sent or not moving).
	 */
	public static long nowMinute(int gameMinute, long clockMillis)
	{
		return Math.max(gameMinute, Math.floorDiv(clockMillis, MINUTE_MS) - 1);
	}

	/**
	 * A last-use varp changed (the player used a home or minigame teleport, or logged in).
	 *
	 * @return true if a teleport turned ready or started cooling down
	 */
	public boolean lastUse(Cooldown c, int minute, long clockMillis)
	{
		lastUse[c.ordinal()] = minute;
		return decide(clockMillis);
	}

	/**
	 * The game's minute clock moved on.
	 *
	 * @return true if a teleport turned ready (or started cooling down)
	 */
	public boolean gameMinute(int minute, long clockMillis)
	{
		gameMinute = minute;
		return decide(clockMillis);
	}

	/**
	 * Once per game tick: decides again only when the stored ready time has passed on the computer's clock (one
	 * comparison otherwise).
	 *
	 * @return true if a teleport turned ready
	 */
	public boolean tick(long clockMillis)
	{
		return clockMillis >= lookAgainAt && decide(clockMillis);
	}

	/**
	 * Everything read at once (the player snapshot reads the values fresh from the game).
	 *
	 * @param lastUses by {@link Cooldown} ordinal
	 * @return true if a teleport turned ready or started cooling down since the last decision
	 */
	public boolean read(int[] lastUses, int gameMinute, long clockMillis)
	{
		System.arraycopy(lastUses, 0, lastUse, 0, lastUse.length);
		this.gameMinute = gameMinute;
		return decide(clockMillis);
	}

	/** Forgets everything (another account): nothing cooling until the game's values arrive. */
	public void clear()
	{
		Arrays.fill(lastUse, 0);
		gameMinute = 0;
		cooling = 0;
		lookAgainAt = Long.MAX_VALUE;
	}

	private boolean decide(long clockMillis)
	{
		long now = nowMinute(gameMinute, clockMillis);
		int bits = 0;
		long earliest = Long.MAX_VALUE;
		for (Cooldown c : Cooldown.values())
		{
			int last = lastUse[c.ordinal()];
			if (c.coolingDown(last, now))
			{
				bits |= 1 << c.ordinal();
				earliest = Math.min(earliest, c.readyAt(last));
			}
		}
		// on the computer's clock "now" reaches the ready minute at the latest one minute after that minute starts
		lookAgainAt = earliest == Long.MAX_VALUE ? Long.MAX_VALUE : (earliest + 1) * MINUTE_MS;
		boolean changed = bits != cooling;
		cooling = bits;
		return changed;
	}
}
