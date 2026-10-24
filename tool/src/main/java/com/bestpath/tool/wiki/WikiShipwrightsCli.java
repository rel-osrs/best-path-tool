package com.bestpath.tool.wiki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import net.runelite.cache.NpcManager;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.fs.Store;

/**
 * DEVELOPMENT-TIME ONLY (project rule 1: Wiki data is gathered only at development time): where the shipwrights stand.
 * The cache names them (NPCs "Shipwright ..." and any other NPC with "Recover-boat") but has no NPC places, so
 * their tiles come from their Wiki pages. Never used by the plugin.
 *
 * <p>Same politeness as {@link WikiTeleportsCli}: the MediaWiki API only, a
 * descriptive User-Agent, a pause between requests, every response kept in
 * {@code cache/wiki/}. One request fetches all pages (50 per request).
 *
 * <p>Output: {@code data/wiki/shipwrights.json} (CC BY-NC-SA 3.0, page and
 * revision per record). {@code gradlew -p tool wikiShipwrights --args="<cache folder> [refresh]"}.
 */
public final class WikiShipwrightsCli
{
	private static final String API = "https://oldschool.runescape.wiki/api.php";
	private static final String USER_AGENT = "BestPathDataTool-dev/0.1 (development-time data check for a RuneLite route-finding plugin; "
		+ "NPC locations only, results cached locally)";
	private static final long PAUSE_MS = 2000;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
	private final File rawDir = new File("cache/wiki");
	private final boolean refresh;
	private long lastRequest;
	private int requests;

	private WikiShipwrightsCli(boolean refresh)
	{
		this.refresh = refresh;
	}

	/** One shipwright: the cache NPC and the tiles its Wiki page gives. */
	static final class Record
	{
		int npcId;
		String name;
		/** The NPC's options in the cache, e.g. "Recover-boat". */
		List<String> options = new ArrayList<>();
		/** {x, y, plane} per place the page shows. */
		List<int[]> tiles = new ArrayList<>();
		String page;
		long revid;
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 1)
		{
			System.err.println("usage: wikiShipwrights <cache folder (disk/cache)> [refresh]");
			System.exit(2);
		}
		new WikiShipwrightsCli(args.length > 1 && args[1].equals("refresh")).run(new File(args[0]));
	}

	private void run(File cacheDir) throws Exception
	{
		rawDir.mkdirs();
		List<Record> out = shipwrights(cacheDir);
		TreeSet<String> titles = new TreeSet<>();
		out.forEach(r -> titles.add(r.name));
		List<String> list = new ArrayList<>(titles);
		for (int i = 0; i < list.size(); i += 50)
		{
			addTiles(list.subList(i, Math.min(list.size(), i + 50)), out);
		}
		File dir = new File("data/wiki");
		dir.mkdirs();
		JsonObject doc = new JsonObject();
		doc.addProperty("licence", "Tiles derived from the Old School RuneScape Wiki (https://oldschool.runescape.wiki), "
			+ "CC BY-NC-SA 3.0. Each record names its page and revision. NPC ids, names and options are from the game cache. "
			+ "Not covered by the project's BSD-2 licence.");
		doc.addProperty("fetched", Instant.now().toString());
		Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		JsonArray recs = gson.toJsonTree(out).getAsJsonArray();
		doc.add("records", recs);
		Files.writeString(new File(dir, "shipwrights.json").toPath(), gson.toJson(doc), StandardCharsets.UTF_8);
		for (Record r : out)
		{
			StringBuilder sb = new StringBuilder();
			r.tiles.forEach(t -> sb.append(" ").append(t[0]).append(",").append(t[1]).append(",").append(t[2]));
			System.out.printf(Locale.ROOT, "%d %-22s %s ->%s%n", r.npcId, r.name, r.options, sb.length() == 0 ? " (no tile on the page)" : sb);
		}
		System.out.printf(Locale.ROOT, "%d shipwrights written to data/wiki/shipwrights.json; %d web requests%n", out.size(), requests);
	}

	/** The cache's shipwrights: NPCs named "Shipwright..." or with a "Recover-boat" option, with their options. */
	private static List<Record> shipwrights(File cacheDir) throws IOException
	{
		List<Record> out = new ArrayList<>();
		try (Store store = new Store(cacheDir))
		{
			store.load();
			NpcManager npcs = new NpcManager(store);
			npcs.load();
			for (NpcDefinition n : npcs.getNpcs())
			{
				if (n.getName() != null && (n.getName().startsWith("Shipwright") || hasOption(n, "Recover-boat")))
				{
					Record r = new Record();
					r.npcId = n.getId();
					r.name = n.getName();
					if (n.getOps() != null && n.getOps().getOps() != null)
					{
						n.getOps().getOps().forEach(o ->
						{
							if (o != null && o.text != null)
							{
								r.options.add(o.text);
							}
						});
					}
					out.add(r);
				}
			}
		}
		return out;
	}

	/** Asks for one batch of pages (following redirects) and gives each record named by one its page and the page's tiles. */
	private void addTiles(List<String> batch, List<Record> out) throws IOException, InterruptedException
	{
		String url = API + "?action=query&format=json&redirects=1&prop=revisions&rvprop=content%7Cids&rvslots=main&titles="
			+ enc(String.join("|", batch));
		JsonObject o = new JsonParser().parse(get(url, "shipwrights-" + Integer.toHexString(String.join("|", batch).hashCode()))).getAsJsonObject();
		JsonObject q = o.getAsJsonObject("query");
		java.util.Map<String, String> redirected = new java.util.HashMap<>();
		for (String key : new String[]{"normalized", "redirects"})
		{
			if (q.has(key))
			{
				for (JsonElement e : q.getAsJsonArray(key))
				{
					redirected.put(e.getAsJsonObject().get("from").getAsString(), e.getAsJsonObject().get("to").getAsString());
				}
			}
		}
		for (java.util.Map.Entry<String, JsonElement> pe : q.getAsJsonObject("pages").entrySet())
		{
			JsonObject page = pe.getValue().getAsJsonObject();
			if (!page.has("revisions"))
			{
				continue;
			}
			String title = page.get("title").getAsString();
			JsonObject rev = page.getAsJsonArray("revisions").get(0).getAsJsonObject();
			String text = rev.getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString();
			for (Record r : out)
			{
				String t = redirected.getOrDefault(r.name, r.name);
				t = redirected.getOrDefault(t, t);
				if (t.equals(title))
				{
					r.page = title;
					r.revid = rev.get("revid").getAsLong();
					r.tiles.addAll(tiles(text));
				}
			}
		}
	}

	private static boolean hasOption(NpcDefinition n, String option)
	{
		return n.getOps() != null && n.getOps().getOps() != null
			&& n.getOps().getOps().stream().anyMatch(o -> o != null && option.equals(o.text));
	}

	/** Tiles from the page's {@code |x = 1234 |y = 5678 |plane = 0} or {@code x:1234,y:5678} map marks. */
	static List<int[]> tiles(String text)
	{
		List<int[]> out = new ArrayList<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\|\\s*x\\s*=\\s*(\\d+)\\s*\\|\\s*y\\s*=\\s*(\\d+)(?:\\s*\\|\\s*plane\\s*=\\s*(\\d))?").matcher(text);
		while (m.find())
		{
			add(out, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), m.group(3) == null ? 0 : Integer.parseInt(m.group(3)));
		}
		m = java.util.regex.Pattern.compile("x:(\\d+),y:(\\d+)").matcher(text);
		while (m.find())
		{
			add(out, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), 0);
		}
		// bare "x,y" pins inside {{Map|...}}
		java.util.regex.Matcher map = java.util.regex.Pattern.compile("\\{\\{Map\\|([^}]*)\\}\\}").matcher(text);
		while (map.find())
		{
			for (String part : map.group(1).split("\\|"))
			{
				java.util.regex.Matcher p = java.util.regex.Pattern.compile("\\s*(\\d{3,5}),(\\d{3,5})\\s*").matcher(part);
				if (p.matches())
				{
					add(out, Integer.parseInt(p.group(1)), Integer.parseInt(p.group(2)), 0);
				}
			}
		}
		return out;
	}

	private static void add(List<int[]> out, int x, int y, int plane)
	{
		for (int[] t : out)
		{
			if (t[0] == x && t[1] == y && t[2] == plane)
			{
				return;
			}
		}
		out.add(new int[]{x, y, plane});
	}

	/** A cached response, or one polite request (saved for next time). */
	private String get(String url, String cacheName) throws IOException, InterruptedException
	{
		File f = new File(rawDir, cacheName + ".json");
		if (f.isFile() && !refresh)
		{
			return Files.readString(f.toPath(), StandardCharsets.UTF_8);
		}
		long wait = lastRequest + PAUSE_MS - System.currentTimeMillis();
		if (wait > 0)
		{
			Thread.sleep(wait);
		}
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT)
			.timeout(Duration.ofSeconds(60)).GET().build();
		HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		lastRequest = System.currentTimeMillis();
		requests++;
		if (resp.statusCode() != 200)
		{
			throw new IOException("HTTP " + resp.statusCode() + " for " + url);
		}
		Files.writeString(f.toPath(), resp.body(), StandardCharsets.UTF_8);
		return resp.body();
	}

	private static String enc(String s)
	{
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
