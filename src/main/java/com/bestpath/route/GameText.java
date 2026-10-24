package com.bestpath.route;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words written in the game world for a step. Shorter than the step's card title, which keeps the full name: the
 * glow already shows who or what to click.
 * <ul>
 * <li>The game's menu text stays as it is, but for a few long item names ({@link #ITEMS}: "Quetzal whistle -> Fortis
 * Colosseum").</li>
 * <li>A transport named by us (the game's menu text not known): "(...)" left out (who to talk to, where it is);
 * rides say where they go, in the direction used ("Boat or ship: Port Sarim &lt;-&gt; Land's End (Veos)" -> "Boat -&gt;
 * Land's End"), with short words for the kind of ride ({@link #RIDES}); teleports as the menu titles are written
 * ("Teleport: Ring of returning: Kourend Castle" -> "Ring of returning -> Kourend Castle"); a house's mounted item as
 * the item ("Mythical cape -> Myths' Guild"); the Revenant cave scroll by its level.</li>
 * </ul>
 * Pure.
 */
public final class GameText
{
	/** In-game names for long item names in menu titles (the glow marks the real item). */
	static final Map<String, String> ITEMS = new LinkedHashMap<>();
	/** Short words for the kind of ride ("Boat or ship: ..." -> "Boat -> ..."). */
	static final Map<String, String> RIDES = new LinkedHashMap<>();

	static
	{
		ITEMS.put("Perfected quetzal whistle(i)", "Quetzal whistle");
		ITEMS.put("Perfected quetzal whistle", "Quetzal whistle");
		ITEMS.put("Enhanced quetzal whistle", "Quetzal whistle");
		ITEMS.put("Basic quetzal whistle", "Quetzal whistle");
		ITEMS.put("Achievement diary cape", "Diary cape");
		ITEMS.put("Kharedst's memoirs", "Memoirs");
		RIDES.put("Boat or ship", "Boat");
		RIDES.put("Hot air balloon", "Balloon");
		RIDES.put("Magic carpet", "Carpet");
		RIDES.put("Keldagrim minecart", "Minecart");
		RIDES.put("Dorgeshuun guide", "Guide");
	}

	private static final Pattern PARENS = Pattern.compile("\\s*\\([^)]*\\)");
	private static final Pattern REVENANT = Pattern.compile("Revenant Caves level (\\d+) entrance");
	private static final String MOUNTED = "House mounted ";
	/**
	 * Between a ride and where it goes, in words we write: "Boat -> Land's End". The game's own text keeps its "to".
	 */
	static final String TO = " " + CoreRouteEngine.ARROW + " ";

	private GameText()
	{
	}

	/**
	 * @param title    the step's card title
	 * @param fromMenu the title is the game's menu text
	 * @param name     the transport's name (null: none)
	 * @param teleport the step is a teleport
	 * @param forward  a two-way transport used from its start to its end (else end to start)
	 * @return the in-game words, never longer than the title
	 */
	public static String of(String title, boolean fromMenu, String name, boolean teleport, boolean forward)
	{
		String s = fromMenu ? items(title) : name == null || name.isEmpty() ? title : fromName(name, teleport, forward, title);
		return s.length() <= title.length() ? s : title;
	}

	/**
	 * A menu title with a long item name shortened ("Basic quetzal whistle -> Hunter Guild" ->
	 * "Quetzal whistle -> ...").
	 */
	static String items(String title)
	{
		for (Map.Entry<String, String> e : ITEMS.entrySet())
		{
			if (title.startsWith(e.getKey() + " "))
			{
				return e.getValue() + title.substring(e.getKey().length());
			}
		}
		return title;
	}

	/** The words for a transport named by us, "Kind: what" ({@code title} when the name has no "Kind: "). */
	static String fromName(String name, boolean teleport, boolean forward, String title)
	{
		int c = name.indexOf(": ");
		if (c <= 0)
		{
			return title;
		}
		String head = PARENS.matcher(name.substring(0, c)).replaceAll("").trim();
		String rawTail = name.substring(c + 2);
		String tail = PARENS.matcher(rawTail).replaceAll("").trim();
		if (head.isEmpty() || tail.isEmpty())
		{
			return title;
		}
		if (teleport)
		{
			Matcher m = REVENANT.matcher(rawTail);
			if (m.matches())
			{
				return head + " (level " + m.group(1) + ")";
			}
			return head + " " + CoreRouteEngine.ARROW + " " + tail;
		}
		if (head.startsWith(MOUNTED) && head.length() > MOUNTED.length())
		{
			String item = head.substring(MOUNTED.length());
			return Character.toUpperCase(item.charAt(0)) + item.substring(1) + " " + CoreRouteEngine.ARROW + " " + tail;
		}
		String ride = RIDES.get(head);
		String kind = ride == null ? head : ride;
		int both = tail.indexOf(" <-> ");
		if (both > 0)
		{
			return kind + TO + (forward ? tail.substring(both + 5) : tail.substring(0, both));
		}
		int to = tail.indexOf(" to ");
		if (to > 0)
		{
			return kind + TO + tail.substring(to + 4);
		}
		return ride != null ? ride + TO + tail : head + ": " + tail;
	}

	/**
	 * A sail step's words: "Sail Jolly Raven to Rellekka" and "Sail to Rellekka" -> "Sail -> Rellekka" (the card keeps
	 * the boat's name).
	 */
	static String sail(String title)
	{
		int to = title.lastIndexOf(" to ");
		return title.startsWith("Sail ") && to > 3 ? "Sail" + TO + title.substring(to + 4) : title;
	}
}
