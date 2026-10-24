package com.bestpath;

import com.bestpath.route.RecentDestinations;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.SavedLocations;

/**
 * The Functions tab's recent destinations and the player's saved locations, saved per account. Client thread only.
 */
final class SavedDestinations
{
	/** Per-account key: the Functions tab's recent destinations. */
	static final String RECENT_KEY = "recentDestinations";
	/** Per-account key: the player's saved locations. */
	static final String SAVED_KEY = "savedLocations";

	private final ProfileStore store;
	private final PluginContext plugin;
	private RecentDestinations recent = RecentDestinations.NONE;
	private SavedLocations saved = SavedLocations.NONE;

	SavedDestinations(ProfileStore store, PluginContext plugin)
	{
		this.store = store;
		this.plugin = plugin;
	}

	/** This account's recent destinations and saved locations (start, login, account switch). */
	void load()
	{
		recent = RecentDestinations.fromText(store.read(RECENT_KEY));
		showRecent();
		saved = SavedLocations.fromText(store.read(SAVED_KEY));
		showSaved();
	}

	/** A destination the player picked goes to the top of the recent destinations. */
	void remember(RouteRequest request)
	{
		if (RecentDestinations.keeps(request))
		{
			setRecent(recent.with(request));
		}
	}

	void removeRecent(int index)
	{
		setRecent(recent.without(index));
	}

	void clearRecent()
	{
		setRecent(RecentDestinations.NONE);
	}

	void saveLocation(String name, int tile)
	{
		setSaved(saved.with(name, tile));
	}

	void removeSavedLocation(int index)
	{
		setSaved(saved.without(index));
	}

	/** Saves (when changed) and shows the recent destinations. */
	private void setRecent(RecentDestinations d)
	{
		if (d.equals(recent))
		{
			return;
		}
		recent = d;
		store.write(null, RECENT_KEY, d.toText()); // logged out: kept until the next account loads its own
		showRecent();
	}

	/** Saves (when changed) and shows the saved locations. */
	private void setSaved(SavedLocations s)
	{
		if (s.equals(saved))
		{
			return;
		}
		saved = s;
		store.write(null, SAVED_KEY, s.toText()); // logged out: kept until the next account loads its own
		showSaved();
	}

	private void showRecent()
	{
		RecentDestinations d = recent;
		plugin.toSidebar(sidebar -> sidebar.showRecent(d));
	}

	private void showSaved()
	{
		SavedLocations s = saved;
		plugin.toSidebar(sidebar -> sidebar.showSaved(s));
	}
}
