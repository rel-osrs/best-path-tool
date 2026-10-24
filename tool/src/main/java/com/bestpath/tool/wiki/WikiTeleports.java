package com.bestpath.tool.wiki;

import com.bestpath.tool.transport.CacheTableTeleports;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads {@code data/wiki/teleports.json} (made at development time by
 * {@link WikiTeleportsCli}) as suggested teleport destinations. Spell and
 * item pages become teleports usable from anywhere; other pages (objects,
 * NPCs) become networks. Groups the cache already provides are left out.
 */
public final class WikiTeleports
{
	private WikiTeleports()
	{
	}

	/** The cache's table destinations plus the Wiki file's (if present). */
	public static List<CacheTableTeleports.Destination> withCache(List<CacheTableTeleports.Destination> cache, File wikiFile)
		throws IOException
	{
		Set<String> groups = new HashSet<>();
		for (CacheTableTeleports.Destination d : cache)
		{
			groups.add(d.network);
		}
		List<CacheTableTeleports.Destination> all = new ArrayList<>(cache);
		all.addAll(load(wikiFile, groups));
		return all;
	}

	/** The suggestions, or none if the file is missing. {@code cacheGroups}: networks already from the cache. */
	public static List<CacheTableTeleports.Destination> load(File file, Collection<String> cacheGroups) throws IOException
	{
		List<CacheTableTeleports.Destination> out = new ArrayList<>();
		if (!file.isFile())
		{
			return out;
		}
		JsonObject doc = new JsonParser().parse(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
		Set<String> seen = new HashSet<>();
		for (JsonElement e : doc.getAsJsonArray("records"))
		{
			JsonObject r = e.getAsJsonObject();
			String group = r.get("group").getAsString();
			if (cacheGroups.contains(group))
			{
				continue; // the cache table is the better source
			}
			String name = r.get("name").getAsString();
			int x = r.get("x").getAsInt();
			int y = r.get("y").getAsInt();
			int plane = r.get("plane").getAsInt();
			if (!seen.add(group + "|" + name + "|" + x + "," + y + "," + plane))
			{
				continue;
			}
			StringBuilder source = new StringBuilder("Wiki: ").append(r.get("page").getAsString())
				.append(" (revision ").append(r.get("revid").getAsLong()).append(", CC BY-NC-SA)");
			String note = r.has("note") ? r.get("note").getAsString() : "";
			if (!note.isEmpty())
			{
				source.append("; ").append(note);
			}
			if (!r.get("planeGiven").getAsBoolean())
			{
				source.append("; plane not stated, 0 assumed");
			}
			boolean network = "other".equals(r.get("groupKind").getAsString());
			out.add(new CacheTableTeleports.Destination(group, name.equals(group) ? group : name, x, y, plane, network,
				source.toString()));
		}
		return out;
	}
}
