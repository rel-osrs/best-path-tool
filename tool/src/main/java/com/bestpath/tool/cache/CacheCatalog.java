package com.bestpath.tool.cache;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lists Old School live caches available from the OpenRS2 Archive
 * (archive.openrs2.org). Only contacted when the user asks.
 */
public final class CacheCatalog
{
	public static final String BASE = "https://archive.openrs2.org";
	static final String USER_AGENT = "BestPath-Data-Tool/0.1 (+https://github.com/)";

	private CacheCatalog()
	{
	}

	/** Downloads the catalogue and returns OSRS live caches, newest first. */
	public static List<CacheInfo> fetch() throws IOException
	{
		HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/caches.json").openConnection();
		c.setRequestProperty("User-Agent", USER_AGENT);
		c.setConnectTimeout(15_000);
		c.setReadTimeout(60_000);
		if (c.getResponseCode() != 200)
		{
			throw new IOException("OpenRS2 returned HTTP " + c.getResponseCode());
		}
		try (InputStream in = c.getInputStream(); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return parse(r);
		}
	}

	/** Parses caches.json, keeping oldschool/live entries with a timestamp, newest first. */
	public static List<CacheInfo> parse(Reader json)
	{
		JsonArray arr = new JsonParser().parse(json).getAsJsonArray();
		List<CacheInfo> out = new ArrayList<>();
		for (JsonElement e : arr)
		{
			JsonObject o = e.getAsJsonObject();
			if (!"oldschool".equals(str(o, "game")) || !"live".equals(str(o, "environment")))
			{
				continue;
			}
			String ts = str(o, "timestamp");
			if (ts == null || ts.isEmpty())
			{
				continue;
			}
			int build = -1;
			JsonElement builds = o.get("builds");
			if (builds != null && builds.isJsonArray())
			{
				for (JsonElement b : builds.getAsJsonArray())
				{
					JsonElement major = b.getAsJsonObject().get("major");
					if (major != null && !major.isJsonNull())
					{
						build = Math.max(build, major.getAsInt());
					}
				}
			}
			long size = o.has("size") && !o.get("size").isJsonNull() ? o.get("size").getAsLong() : 0;
			String scope = str(o, "scope");
			out.add(new CacheInfo(o.get("id").getAsInt(), build, ts, size, scope == null ? "runescape" : scope));
		}
		out.sort(Comparator.comparing(CacheInfo::getTimestamp).reversed());
		return out;
	}

	private static String str(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	static String diskZipUrl(CacheInfo info)
	{
		return BASE + "/caches/" + info.getScope() + "/" + info.getOpenrs2Id() + "/disk.zip";
	}
}
