package com.bestpath.player;

import com.bestpath.route.TransportSettings;

/**
 * The Settings tab's choices for the account logged in, saved in its
 * RuneLite account profile. Pure Java; one thread (the plugin's client
 * thread).
 *
 * <p>Without an account profile nothing is lost:
 * <ul>
 * <li>logged out: the settings in use stay as they were (not reset to the
 * defaults); a change made now is kept and saved to the next account that
 * logs in (it replaces that account's saved settings, because it is the
 * newest choice);</li>
 * <li>logged in to an account that has no profile yet: saving creates the
 * profile (RuneLite does that when asked to write while logged in).</li>
 * </ul>
 */
public final class AccountSettings
{
	/** Where the text is kept (the plugin: RuneLite's ConfigManager). */
	public interface Store
	{
		/** The account profile logged in, or null (logged out, or the account has no profile yet). */
		String profileKey();

		/** This account's saved text, or null. */
		String read();

		/** Saves for the account logged in (creating its profile if needed); false if nobody is logged in. */
		boolean write(String text);
	}

	private final Store store;
	/** The settings in use (tests read it). */
	TransportSettings current = TransportSettings.defaults();
	/** The text in the profile now (null = none or not known). */
	private String saved;
	/** A change not saved yet (made while logged out; tests read it). */
	TransportSettings pending;

	public AccountSettings(Store store)
	{
		this.store = store;
	}

	/**
	 * The account profile changed (start, login, logout, account switch).
	 *
	 * @return the settings now in use
	 */
	public TransportSettings onProfileChanged()
	{
		if (store.profileKey() == null)
		{
			saved = null; // nothing known about where they'd go: keep what is in use
			return current;
		}
		saved = store.read();
		if (pending != null)
		{
			TransportSettings p = pending;
			pending = null;
			change(p);
			return current;
		}
		current = TransportSettings.decode(saved);
		return current;
	}

	/** The user changed a setting: use it now and save it (or keep it until an account is logged in). */
	public void change(TransportSettings s)
	{
		current = s;
		String text = s.encode();
		if (text.equals(saved))
		{
			pending = null;
			return;
		}
		if (store.write(text))
		{
			saved = text;
			pending = null;
		}
		else
		{
			pending = s;
		}
	}
}
