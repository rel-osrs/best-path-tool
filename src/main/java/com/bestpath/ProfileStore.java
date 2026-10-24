package com.bestpath;

import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

/**
 * Writes this plugin's per-account values (RuneLite account profile config,
 * group {@value BestPathConfig#GROUP}). CLIENT THREAD ONLY.
 *
 * <p>Writing while logged in to an account that has no profile yet makes
 * RuneLite create one; RuneLite then posts {@code RuneScapeProfileChanged}
 * from inside the write. {@link #isWriting()} tells the plugin's handler that
 * this event comes from our own write (nothing to reload: the values in
 * memory already belong to this account).
 */
final class ProfileStore
{
	private final Client client;
	private final ConfigManager configManager;
	private boolean writing;

	ProfileStore(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	/** The account profile logged in, or null. */
	String profileKey()
	{
		return configManager.getRSProfileKey();
	}

	/** The logged-in account's value, or null. */
	String read(String key)
	{
		return configManager.getRSProfileConfiguration(BestPathConfig.GROUP, key);
	}

	/**
	 * The logged-in account's value in another config group (read only; e.g. RuneLite's Time Tracking plugin's farming
	 * patch records), or null.
	 */
	String readOther(String group, String key)
	{
		return configManager.getRSProfileConfiguration(group, key);
	}

	/**
	 * Writes a value for one account.
	 *
	 * @param profile the account profile it belongs to; null = the account logged in now (its profile is created if
	 *                it has none yet)
	 * @return false if it could not be written (no profile given and nobody logged in)
	 */
	boolean write(String profile, String key, String text)
	{
		if (profile != null)
		{
			configManager.setConfiguration(BestPathConfig.GROUP, profile, key, text);
			return true;
		}
		if (client.getAccountHash() == -1)
		{
			return false;
		}
		writing = true;
		try
		{
			configManager.setRSProfileConfiguration(BestPathConfig.GROUP, key, text);
		}
		finally
		{
			writing = false;
		}
		return configManager.getRSProfileKey() != null;
	}

	/** True while {@link #write} runs (a profile-changed event now comes from our own write). */
	boolean isWriting()
	{
		return writing;
	}
}
