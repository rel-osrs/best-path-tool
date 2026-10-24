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

/**
 * DEVELOPMENT-TIME ONLY (project rule 1: Wiki data is gathered only at development time): gathers teleport
 * destinations from the OSRS Wiki, because the game cache has only their
 * names. Never used by the plugin, never at plugin runtime.
 *
 * <p>Politeness (DATA_SOURCES.md §2): the MediaWiki API only, a descriptive
 * User-Agent, one request at a time with a pause, page text fetched 50 pages
 * per request, and every response kept in {@code cache/wiki/} so nothing is
 * fetched twice. Each record keeps the page and revision it came from.
 *
 * <p>Output: {@code data/wiki/teleports.json}. Wiki text is CC BY-NC-SA 3.0,
 * so this file is kept apart from our BSD-2 data, with its own notice.
 *
 * <p>{@code gradlew -p tool wikiTeleports} ({@code --args="refresh"} to fetch again).
 */
public final class WikiTeleportsCli
{
	private static final String API = "https://oldschool.runescape.wiki/api.php";
	private static final String USER_AGENT = "BestPathDataTool-dev/0.1 (development-time data check for a RuneLite route-finding plugin; "
		+ "teleport coordinates only, results cached locally)";
	private static final long PAUSE_MS = 2000;
	private static final String TEMPLATE = "TeleportLocationLine";
	private static final String[] CATEGORIES = {"Teleport spells", "Teleportation items"};

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
	private final File rawDir = new File("cache/wiki");
	private final boolean refresh;
	private long lastRequest;
	private int requests;

	private WikiTeleportsCli(boolean refresh)
	{
		this.refresh = refresh;
	}

	public static void main(String[] args) throws Exception
	{
		new WikiTeleportsCli(args.length > 0 && args[0].equals("refresh")).run();
	}

	/** One destination line from a page. */
	static final class Record
	{
		String group;
		/** "spell", "item" or "other" (an object or NPC network: usable only at its stops). */
		String groupKind;
		String name;
		int x;
		int y;
		int plane;
		boolean planeGiven;
		String note;
		String page;
		long revid;
		/** Which template it was read from. */
		String how;
	}

	private void run() throws Exception
	{
		rawDir.mkdirs();
		List<String> titles = titles();

		// 2) their text, 50 pages per request
		List<Record> out = new ArrayList<>();
		for (int i = 0; i < titles.size(); i += 50)
		{
			addRecords(titles.subList(i, Math.min(titles.size(), i + 50)), i, out);
		}

		// 3) the data file, apart from our BSD-2 data, with its licence notice
		File dir = new File("data/wiki");
		dir.mkdirs();
		JsonObject doc = new JsonObject();
		doc.addProperty("licence", "Derived from the Old School RuneScape Wiki (https://oldschool.runescape.wiki), "
			+ "CC BY-NC-SA 3.0. Each record names its page and revision. Not covered by the project's BSD-2 licence.");
		doc.addProperty("fetched", Instant.now().toString());
		doc.addProperty("template", TEMPLATE);
		Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		JsonArray recs = gson.toJsonTree(out).getAsJsonArray();
		doc.add("records", recs);
		Files.writeString(new File(dir, "teleports.json").toPath(), gson.toJson(doc), StandardCharsets.UTF_8);
		long spells = out.stream().filter(r -> r.groupKind.equals("spell")).count();
		long items = out.stream().filter(r -> r.groupKind.equals("item")).count();
		System.out.printf(Locale.ROOT, "%d destinations (%d spell, %d item, %d other/network) written to data/wiki/teleports.json; "
			+ "%d web requests%n", out.size(), spells, items, out.size() - spells - items, requests);
	}

	/** 1) Every page that uses the destination template, then every page in the teleport categories, each once. */
	private List<String> titles() throws IOException, InterruptedException
	{
		List<String> titles = new ArrayList<>();
		String cont = null;
		int part = 0;
		do
		{
			String url = API + "?action=query&format=json&list=embeddedin&eilimit=500&einamespace=0&eititle="
				+ enc("Template:" + TEMPLATE) + (cont != null ? "&eicontinue=" + enc(cont) : "");
			JsonObject o = new JsonParser().parse(get(url, "embeddedin-" + part++)).getAsJsonObject();
			for (JsonElement e : o.getAsJsonObject("query").getAsJsonArray("embeddedin"))
			{
				titles.add(e.getAsJsonObject().get("title").getAsString());
			}
			cont = o.has("continue") ? o.getAsJsonObject("continue").get("eicontinue").getAsString() : null;
		}
		while (cont != null);
		System.out.printf(Locale.ROOT, "%d pages use {{%s}}%n", titles.size(), TEMPLATE);
		// ...and every page in the teleport categories (spells keep their destination in a {{Map}})
		for (String cat : CATEGORIES)
		{
			int before = titles.size();
			addCategory(cat, titles);
			System.out.printf(Locale.ROOT, "Category:%s adds %d pages%n", cat, titles.size() - before);
		}
		return titles;
	}

	/** Adds the pages of one category not listed yet. */
	private void addCategory(String cat, List<String> titles) throws IOException, InterruptedException
	{
		String cont = null;
		int part = 0;
		do
		{
			String url = API + "?action=query&format=json&list=categorymembers&cmlimit=500&cmnamespace=0&cmtitle="
				+ enc("Category:" + cat) + (cont != null ? "&cmcontinue=" + enc(cont) : "");
			JsonObject o = new JsonParser().parse(get(url, "category-" + cat.replace(' ', '_') + "-" + part++)).getAsJsonObject();
			for (JsonElement e : o.getAsJsonObject("query").getAsJsonArray("categorymembers"))
			{
				String t = e.getAsJsonObject().get("title").getAsString();
				if (!titles.contains(t))
				{
					titles.add(t);
				}
			}
			cont = o.has("continue") ? o.getAsJsonObject("continue").get("cmcontinue").getAsString() : null;
		}
		while (cont != null);
	}

	/** Asks for one batch of pages ({@code first}: the batch's first index, for the saved response's name) and adds their destinations. */
	private void addRecords(List<String> batch, int first, List<Record> out) throws IOException, InterruptedException
	{
		String url = API + "?action=query&format=json&prop=revisions&rvprop=content%7Cids&rvslots=main&titles="
			+ enc(String.join("|", batch));
		JsonObject o = new JsonParser().parse(get(url, "pages-" + first)).getAsJsonObject();
		for (JsonElement pe : o.getAsJsonObject("query").getAsJsonObject("pages").entrySet().stream()
			.map(java.util.Map.Entry::getValue).toArray(JsonElement[]::new))
		{
			JsonObject page = pe.getAsJsonObject();
			if (!page.has("revisions"))
			{
				continue;
			}
			JsonObject rev = page.getAsJsonArray("revisions").get(0).getAsJsonObject();
			String text = rev.getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString();
			out.addAll(WikiText.records(page.get("title").getAsString(), rev.get("revid").getAsLong(), text));
		}
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
