package com.bestpath.tool.wiki;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DEVELOPMENT-TIME ONLY: a polite reader of Old School RuneScape Wiki pages
 * (the plugin never contacts the Wiki). The MediaWiki API only, a descriptive
 * User-Agent, at least 3 s between requests, up to 50 pages per request,
 * every response kept in {@code <wikiCache>/<folder>/}; real requests count
 * against the shared daily budget in {@code <wikiCache>/review/_state.json}
 * (the region-review wiki.py budget, 1000 a day).
 */
final class WikiApi
{
	private static final String API = "https://oldschool.runescape.wiki/api.php";
	private static final String USER_AGENT = "BestPathDataTool-dev/0.1 (development-time data check for a RuneLite route-finding plugin; "
		+ "%s, results cached locally)";
	private static final long PAUSE_MS = 3000;
	private static final int BUDGET = 1000;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
	private final File wikiCache;
	private final String folder;
	private final String userAgent;
	private int requests;

	WikiApi(File wikiCache, String folder)
	{
		this(wikiCache, folder, "bank places");
	}

	/** {@code purpose}: what the pages are read for, named in the User-Agent ("Slayer monster places"). */
	WikiApi(File wikiCache, String folder, String purpose)
	{
		this.wikiCache = wikiCache;
		this.folder = folder;
		this.userAgent = String.format(USER_AGENT, purpose);
	}

	/** Real web requests made so far. */
	int requests()
	{
		return requests;
	}

	/** Pages by the title asked for: {page title, revision id, wikitext}; missing pages are left out. */
	Map<String, String[]> pages(List<String> titles) throws IOException, InterruptedException
	{
		Map<String, String[]> out = new LinkedHashMap<>();
		for (int i = 0; i < titles.size(); i += 50)
		{
			List<String> batch = titles.subList(i, Math.min(titles.size(), i + 50));
			String url = API + "?action=query&format=json&redirects=1&prop=revisions&rvprop=content%7Cids&rvslots=main&titles="
				+ URLEncoder.encode(String.join("|", batch), StandardCharsets.UTF_8);
			JsonObject q = new JsonParser().parse(get(url, Integer.toHexString(String.join("|", batch).hashCode())))
				.getAsJsonObject().getAsJsonObject("query");
			Map<String, String> moved = new HashMap<>();
			for (String key : new String[]{"normalized", "redirects"})
			{
				if (q.has(key))
				{
					for (JsonElement e : q.getAsJsonArray(key))
					{
						moved.put(e.getAsJsonObject().get("from").getAsString(), e.getAsJsonObject().get("to").getAsString());
					}
				}
			}
			Map<String, String[]> byTitle = new HashMap<>();
			for (Map.Entry<String, JsonElement> pe : q.getAsJsonObject("pages").entrySet())
			{
				JsonObject page = pe.getValue().getAsJsonObject();
				if (page.has("revisions"))
				{
					JsonObject rev = page.getAsJsonArray("revisions").get(0).getAsJsonObject();
					String title = page.get("title").getAsString();
					byTitle.put(title, new String[]{title, rev.get("revid").getAsString(),
						rev.getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString()});
				}
			}
			for (String t : batch)
			{
				String to = moved.getOrDefault(t, t);
				to = moved.getOrDefault(to, to);
				if (byTitle.containsKey(to))
				{
					out.put(t, byTitle.get(to));
				}
			}
		}
		return out;
	}

	/**
	 * Every page already saved in this folder (no request): {page title, revision id, wikitext} by its title and by
	 * each name that led to it (redirects, normalised titles); a page saved twice keeps its newest revision.
	 */
	Map<String, String[]> savedPages() throws IOException
	{
		Map<String, String[]> out = new HashMap<>();
		Map<String, String> moved = new HashMap<>();
		File[] files = new File(wikiCache, folder).listFiles((d, n) -> n.endsWith(".json"));
		for (File f : files == null ? new File[0] : files)
		{
			JsonObject root = new JsonParser().parse(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject q = root.has("query") ? root.getAsJsonObject("query") : null;
			if (q == null || !q.has("pages"))
			{
				continue;
			}
			for (String key : new String[]{"normalized", "redirects"})
			{
				if (q.has(key))
				{
					for (JsonElement e : q.getAsJsonArray(key))
					{
						moved.put(e.getAsJsonObject().get("from").getAsString(), e.getAsJsonObject().get("to").getAsString());
					}
				}
			}
			for (Map.Entry<String, JsonElement> pe : q.getAsJsonObject("pages").entrySet())
			{
				JsonObject page = pe.getValue().getAsJsonObject();
				if (!page.has("revisions"))
				{
					continue;
				}
				JsonObject rev = page.getAsJsonArray("revisions").get(0).getAsJsonObject();
				String title = page.get("title").getAsString();
				String[] p = {title, rev.get("revid").getAsString(), rev.getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString()};
				String[] had = out.get(title);
				if (had == null || Long.parseLong(had[1]) < Long.parseLong(p[1]))
				{
					out.put(title, p);
				}
			}
		}
		for (Map.Entry<String, String> m : moved.entrySet())
		{
			String to = moved.getOrDefault(m.getValue(), m.getValue());
			if (out.containsKey(to) && !out.containsKey(m.getKey()))
			{
				out.put(m.getKey(), out.get(to));
			}
		}
		return out;
	}

	/** A cached response, or one polite request (saved for next time, counted in the shared budget). */
	private String get(String url, String cacheName) throws IOException, InterruptedException
	{
		File dir = new File(wikiCache, folder);
		dir.mkdirs();
		File f = new File(dir, cacheName + ".json");
		if (f.isFile())
		{
			return Files.readString(f.toPath(), StandardCharsets.UTF_8);
		}
		File state = new File(wikiCache, "review/_state.json");
		JsonObject s = state.isFile() ? new JsonParser().parse(Files.readString(state.toPath(), StandardCharsets.UTF_8)).getAsJsonObject()
			: new JsonObject();
		String today = LocalDate.now().toString();
		if (!today.equals(s.has("day") ? s.get("day").getAsString() : ""))
		{
			s.addProperty("day", today);
			s.addProperty("used", 0);
		}
		int used = s.has("used") ? s.get("used").getAsInt() : 0;
		if (used >= BUDGET)
		{
			throw new IOException("the shared wiki budget for today is used up (" + used + " requests)");
		}
		double last = s.has("last") ? s.get("last").getAsDouble() : 0;
		long wait = (long) (last * 1000) + PAUSE_MS - System.currentTimeMillis();
		if (wait > 0)
		{
			Thread.sleep(wait);
		}
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", userAgent)
			.timeout(Duration.ofSeconds(60)).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		requests++;
		s.addProperty("used", used + 1);
		s.addProperty("last", System.currentTimeMillis() / 1000.0);
		if (state.getParentFile().isDirectory())
		{
			Files.writeString(state.toPath(), s.toString(), StandardCharsets.UTF_8);
		}
		if (resp.statusCode() != 200)
		{
			throw new IOException("HTTP " + resp.statusCode() + " for " + url);
		}
		Files.writeString(f.toPath(), resp.body(), StandardCharsets.UTF_8);
		return resp.body();
	}
}
