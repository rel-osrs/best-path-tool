package com.bestpath.player;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * What is built in the player's house, remembered per account (RuneLite account profile, like the bank): the result
 * of the last scan of the player's own house, and when that was (the Settings tab's "House (POH)" section). Until a
 * first scan nothing is known, which counts as nothing built. Pure Java, one thread (the client thread).
 *
 * <p>Saved only when a scan found something different (diff before saving): one sorted thing per line. The time of
 * the last scan is saved apart, once per visit of the own house (the scanner calls {@link #scanned} once per visit).
 */
public final class HouseMemory
{
	private final AccountSettings.Store store;
	/** Where the time of the last scan is saved (epoch milliseconds as text); null = not saved. */
	private final AccountSettings.Store timeStore;
	private Set<String> things = Collections.emptySet();
	/** When the own house was last scanned (epoch milliseconds), 0 = never on this account. */
	private long scannedAt;

	public HouseMemory(AccountSettings.Store store)
	{
		this(store, null);
	}

	public HouseMemory(AccountSettings.Store store, AccountSettings.Store timeStore)
	{
		this.store = store;
		this.timeStore = timeStore;
	}

	/** Loads the account's saved things and scan time (startup, account switch); nothing saved = nothing built. */
	public void load()
	{
		things = Collections.unmodifiableSet(new TreeSet<>(ProfileText.decodeLines(store.read())));
		scannedAt = timeStore == null ? 0 : parseTime(timeStore.read());
	}

	static long parseTime(String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return 0;
		}
		try
		{
			return Math.max(0, Long.parseLong(text.trim()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** The things built (immutable, sorted). */
	public Set<String> get()
	{
		return things;
	}

	/** When the own house was last scanned (epoch milliseconds), 0 = never (not looked at yet on this account). */
	public long getScannedAt()
	{
		return scannedAt;
	}

	/**
	 * A scan of the player's own house found these things.
	 *
	 * @return true if that differs from what was known (then it is saved, and routes may change)
	 */
	public boolean update(Set<String> found)
	{
		Set<String> now = Collections.unmodifiableSet(new TreeSet<>(found));
		if (now.equals(things))
		{
			return false;
		}
		things = now;
		store.write(encode(now));
		return true;
	}

	/**
	 * The own house was scanned at this time (once per visit): remembered and saved for the Settings tab. Even a scan
	 * that found nothing counts ("looked, nothing built" is not "not looked at yet").
	 */
	public void scanned(long epochMillis)
	{
		scannedAt = Math.max(0, epochMillis);
		if (timeStore != null)
		{
			timeStore.write(String.valueOf(scannedAt));
		}
	}

	/** One thing per line, sorted ("" = scanned, nothing built). */
	public static String encode(Set<String> things)
	{
		return String.join("\n", new TreeSet<>(things));
	}
}
