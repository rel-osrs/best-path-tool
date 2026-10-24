package com.bestpath.tool.cache;

import java.io.File;
import java.io.IOException;

/**
 * The game cache the data is made from: the one the plugin data's manifest names ("cache 2740 241"), found among the
 * caches in {@code cache/}. The command-line programs use it when no cache is given, so after moving to a new cache
 * (and "Update plugin data") they follow without a cache number written in each.
 */
public final class DataCache
{
	private DataCache()
	{
	}

	/**
	 * The cache folder the plugin data was made from. Without a manifest (or when that cache is not in {@code cache/}),
	 * the newest cache there.
	 *
	 * @throws IllegalStateException when {@code cache/} holds no game cache at all
	 */
	public static File folder(File ws)
	{
		try
		{
			File f = Backups.cacheFolder(ws, Backups.dataCacheId(ws));
			if (f != null)
			{
				return f;
			}
		}
		catch (IOException ignored)
		{
			// no readable manifest: the newest cache
		}
		File newest = newest(ws);
		if (newest == null)
		{
			throw new IllegalStateException("No game cache in " + new File(ws, "cache")
				+ ": download one first (the tool's \"Download cache...\")");
		}
		return newest;
	}

	/** The cache in {@code cache/} with the highest OpenRS2 id, or null when there is none. */
	static File newest(File ws)
	{
		File best = null;
		int bestId = Integer.MIN_VALUE;
		File[] all = new File(ws, "cache").listFiles(File::isDirectory);
		if (all != null)
		{
			for (File d : all)
			{
				CacheInfo info = new File(d, CacheInfo.FILE_NAME).isFile() ? CacheInfo.find(d) : null;
				if (info != null && info.getOpenrs2Id() > bestId)
				{
					best = d;
					bestId = info.getOpenrs2Id();
				}
			}
		}
		return best;
	}
}
