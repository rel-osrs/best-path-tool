package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The clue steps the plugin ships ({@code clues.tsv}) and the lookups the plugin's
 * clue detection needs: by clue item, by cache row, by clue text, and the
 * hot/cold spots of a tier. Pure Java, immutable.
 *
 * <p>Where the steps come from: the game cache's clue-helper tables; the places
 * of the "kill" steps from the OSRS Wiki at development time (one tile per
 * area); for key steps, the NPC that drops the key from the cache (its tile in
 * the clue-helper table TargetKey).
 */
public final class ClueData
{
	/** The column names, in order (first non-comment line of clues.tsv). */
	public static final String HEADER = "key\ttype\ttier\ttarget\titems\ttargets\tstash\ttext\tdescription\twiki\tneeds\tnote"
		+ "\taction\tstashVarbit\tstashPlace\tkeyNpc\tkeyNpcName\tkeyItem";
	/** An older header without the key-NPC columns (15 columns); still read. */
	public static final String HEADER_V4 = "key\ttype\ttier\ttarget\titems\ttargets\tstash\ttext\tdescription\twiki\tneeds\tnote"
		+ "\taction\tstashVarbit\tstashPlace";
	/** An older header without the clue-card columns (12 columns, ending with {@code note}); still read. */
	public static final String HEADER_V3 = "key\ttype\ttier\ttarget\titems\ttargets\tstash\ttext\tdescription\twiki\tneeds\tnote";
	/** An older header without the {@code note} column (11 columns); still read. */
	public static final String HEADER_V2 = "key\ttype\ttier\ttarget\titems\ttargets\tstash\ttext\tdescription\twiki\tneeds";
	/** An older header without the {@code needs} column (10 columns); still read. */
	public static final String HEADER_V1 = "key\ttype\ttier\ttarget\titems\ttargets\tstash\ttext\tdescription\twiki";

	/** No clue data (data without a clues file). */
	public static final ClueData EMPTY = new ClueData(List.of());

	/** One clue step. Immutable. */
	public static final class Step
	{
		final String key;
		final String type;
		final String tier;
		final String target;
		final int[] items;
		final int[] targets;
		final int stash;
		final String text;
		final String description;
		final String wiki;
		final String needs;
		final String note;
		final String action;
		final int stashVarbit;
		final String stashPlace;
		final int keyNpc;
		final String keyNpcName;
		final int keyItem;
		/** The text as matching compares it ({@link #normalize}); "" without text. */
		private final String norm;
		/** For coordinate steps: the coordinates ({@link #coordinateKey}); else null. */
		private final String coords;

		/**
		 * @param targets     packed tiles ({@link Tiles#pack}): the target, then other targets (alternatives, kill areas)
		 * @param stash       packed tile of the STASH unit, or -1
		 * @param needs       what the player needs for the step besides the clue, as a requirement line
		 *                    (e.g. "item 952" for a spade); "" or null for nothing
		 * @param note        a short hand-written note for the player about the step (e.g. "Kill the barbarian spirit
		 *                    here"); "" or null for none
		 * @param action      what the player does there, as the game's clue helper says it: {@code dig}, {@code emote
		 *                    <id> <name> | <id> <name>} (in order), {@code music}, {@code talk}, {@code search}, {@code key},
		 *                    {@code kill}, {@code none}; "" = not known
		 * @param stashVarbit the STASH unit's form varbit (1 = built; RuneLite VarbitID.HH_CONSTRUCTED_*), or -1
		 * @param stashPlace  the STASH unit's place as the game names it, or ""
		 * @param keyNpc      for a key step: where the NPC that drops the key is (packed tile; the cache's key-NPC tile),
		 *                    or -1
		 * @param keyNpcName  that NPC's name from the cache (the first of several), or ""
		 * @param keyItem     for a key step: the key's item id, or -1
		 */
		public Step(String key, String type, String tier, String target, int[] items, int[] targets, int stash, String text,
			String description, String wiki, String needs, String note, String action, int stashVarbit, String stashPlace,
			int keyNpc, String keyNpcName, int keyItem)
		{
			this.key = key;
			this.type = type;
			this.tier = tier;
			this.target = target == null ? "" : target;
			this.items = items.clone();
			this.targets = targets.clone();
			this.stash = stash;
			this.text = text == null ? "" : text;
			this.description = description == null ? "" : description;
			this.wiki = wiki == null ? "" : wiki;
			this.needs = needs == null ? "" : needs;
			this.note = note == null ? "" : note;
			this.action = action == null ? "" : action;
			this.stashVarbit = stashVarbit;
			this.stashPlace = stashPlace == null ? "" : stashPlace;
			this.keyNpc = keyNpc;
			this.keyNpcName = keyNpcName == null ? "" : keyNpcName;
			this.keyItem = keyItem;
			this.norm = normalize(this.text);
			this.coords = "coordinate".equals(type) ? coordinateKey(this.text) : null;
		}

		/** {@code <type>-<cache row>}, e.g. {@code cryptic-1233}. */
		public String getKey()
		{
			return key;
		}

		/** The cache row id (the number after the last '-'), or -1. */
		public int getRow()
		{
			int i = key.lastIndexOf('-');
			try
			{
				return i < 0 ? -1 : Integer.parseInt(key.substring(i + 1));
			}
			catch (NumberFormatException e)
			{
				return -1;
			}
		}

		/** anagram, cipher, coordinate, cryptic, emote, fairyring, falo, hotcold, map, music, skillchallenge. */
		public String getType()
		{
			return type;
		}

		/** beginner, easy, medium, hard, elite, master, quest. */
		public String getTier()
		{
			return tier;
		}

		/** What the step points at: npc, loc, coord, key, kill, none (or "" if not known). */
		public String getTarget()
		{
			return target;
		}

		/** Clue item ids that name this step (empty for beginner, master and hot/cold). */
		public int[] getItems()
		{
			return items.clone();
		}

		/** Packed tiles to go to (any one of them will do); empty if the step has no known place. */
		public int targetCount()
		{
			return targets.length;
		}

		public int target(int i)
		{
			return targets[i];
		}

		/** Packed tile of the step's STASH unit, or -1. */
		public int getStash()
		{
			return stash;
		}

		/** The clue text as the cache has it ("" for map and hot/cold steps). */
		public String getText()
		{
			return text;
		}

		/** The cache's description of the target ("Ranael, at the Al Kharid skirt shop."). */
		public String getDescription()
		{
			return description;
		}

		/**
		 * What the player needs for the step besides the clue, as a requirement line
		 * (a spade for dig steps, a light in dark places; hand-written), or "" for
		 * nothing.
		 */
		public String getNeeds()
		{
			return needs;
		}

		/**
		 * A short hand-written note for the player about the step (what to do there, e.g. that the boss is inside
		 * the cave), or "" for none.
		 */
		public String getNote()
		{
			return note;
		}

		/** The STASH unit's form varbit (1 = built), or -1 when the step has no STASH unit (or the export doesn't say). */
		public int getStashVarbit()
		{
			return stashVarbit;
		}

		/** The STASH unit's place as the game names it ("Aris's tent"), or "". */
		public String getStashPlace()
		{
			return stashPlace;
		}

		/**
		 * For a key step ("kill a guard for the key, then search the drawers"): where the NPC that drops the key is
		 * (packed tile, from the cache's clue-helper table), or -1.
		 */
		public int getKeyNpc()
		{
			return keyNpc;
		}

		/** For a key step: the name of the NPC that drops the key ("Barbarian"), or "". */
		public String getKeyNpcName()
		{
			return keyNpcName;
		}

		/** For a key step: the key's item id, or -1. */
		public int getKeyItem()
		{
			return keyItem;
		}

		@Override
		public String toString()
		{
			return key;
		}
	}

	private final List<Step> steps;
	private final Map<Integer, Step> byItem = new HashMap<>();
	private final Map<Integer, Step> byRow = new HashMap<>();

	public ClueData(List<Step> steps)
	{
		this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
		for (Step s : steps)
		{
			byRow.put(s.getRow(), s);
			for (int id : s.items)
			{
				byItem.putIfAbsent(id, s);
			}
		}
	}

	/**
	 * Reads clues.tsv (comment lines start with '#'; the header line is checked). Files with an older header
	 * ({@link #HEADER_V1} to {@link #HEADER_V4}) are read too: 10, 11, 12, 15 or 18 columns.
	 */
	public static ClueData parse(List<String> lines)
	{
		List<Step> out = new ArrayList<>();
		boolean header = false;
		int columns = 18;
		int n = 0;
		for (String line : lines)
		{
			n++;
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			if (!header)
			{
				if (line.equals(HEADER_V1))
				{
					columns = 10;
				}
				else if (line.equals(HEADER_V2))
				{
					columns = 11;
				}
				else if (line.equals(HEADER_V3))
				{
					columns = 12;
				}
				else if (line.equals(HEADER_V4))
				{
					columns = 15;
				}
				else if (!line.equals(HEADER))
				{
					throw new IllegalArgumentException("clues.tsv: unexpected header in line " + n + ": " + line);
				}
				header = true;
				continue;
			}
			String[] w = line.split("\t", -1);
			if (w.length != columns)
			{
				throw new IllegalArgumentException("clues.tsv line " + n + ": " + w.length + " columns, expected " + columns);
			}
			out.add(new Step(w[0], w[1], w[2], w[3], ints(w[4]), tiles(w[5]), w[6].isEmpty() ? -1 : tiles(w[6])[0], w[7], w[8],
				w[9], columns > 10 ? w[10] : "", columns > 11 ? w[11] : "", columns > 12 ? w[12] : "",
				columns > 13 && !w[13].isEmpty() ? Integer.parseInt(w[13].trim()) : -1, columns > 14 ? w[14] : "",
				columns > 15 && !w[15].isEmpty() ? tiles(w[15])[0] : -1, columns > 16 ? w[16] : "",
				columns > 17 && !w[17].isEmpty() ? Integer.parseInt(w[17].trim()) : -1));
		}
		return new ClueData(out);
	}

	public List<Step> getSteps()
	{
		return steps;
	}

	public int size()
	{
		return steps.size();
	}

	/** The step this clue item names (easy to elite and the quest clues), or null. */
	public Step byItem(int itemId)
	{
		return byItem.get(itemId);
	}

	/** The step of a cache row (the value of item param 623), or null. */
	public Step byRow(int row)
	{
		return byRow.get(row);
	}

	/** True if some step lists this item. */
	public boolean isClueItem(int itemId)
	{
		return byItem.containsKey(itemId);
	}

	/** Every hot/cold step of a tier (its possible dig spots), in file order. */
	public List<Step> hotCold(String tier)
	{
		List<Step> out = new ArrayList<>();
		for (Step s : steps)
		{
			if ("hotcold".equals(s.type) && s.tier.equals(tier))
			{
				out.add(s);
			}
		}
		return out;
	}

	/**
	 * The steps whose text the clue scroll shows, from the text read off the
	 * clue interface. In order: coordinates (both "01.30N 08.11W" and the
	 * "degrees / minutes north" wording); the same text (after
	 * {@link #normalize}); the longest step text found inside it as whole
	 * words; the first sentence of a step's text found inside it (only if one
	 * step has it). Several steps are returned only when they have the same
	 * text (alternatives). Empty if nothing matches.
	 *
	 * @param tier only steps of this tier; null = any tier
	 */
	public List<Step> matchText(String shown, String tier)
	{
		List<Step> out = new ArrayList<>();
		if (shown == null)
		{
			return out;
		}
		String ck = coordinateKey(shown);
		if (ck != null)
		{
			for (Step s : steps)
			{
				if (ck.equals(s.coords) && (tier == null || tier.equals(s.tier)))
				{
					out.add(s);
				}
			}
			if (!out.isEmpty())
			{
				return out;
			}
		}
		String n = normalize(shown);
		if (n.isEmpty())
		{
			return out;
		}
		for (Step s : steps)
		{
			if (!s.norm.isEmpty() && s.norm.equals(n) && (tier == null || tier.equals(s.tier)))
			{
				out.add(s);
			}
		}
		if (!out.isEmpty())
		{
			return out;
		}
		String padded = " " + n + " ";
		String best = "";
		for (Step s : steps)
		{
			if (!s.norm.isEmpty() && (tier == null || tier.equals(s.tier)) && s.norm.length() > best.length()
				&& padded.contains(" " + s.norm + " "))
			{
				best = s.norm;
			}
		}
		if (!best.isEmpty())
		{
			for (Step s : steps)
			{
				if (s.norm.equals(best) && (tier == null || tier.equals(s.tier)))
				{
					out.add(s);
				}
			}
			return out;
		}
		// the first sentence only (e.g. an emote clue whose outfit is worded differently on the scroll)
		Step only = null;
		for (Step s : steps)
		{
			String first = normalize(firstSentence(s.text));
			if (first.split(" ").length >= 3 && (tier == null || tier.equals(s.tier)) && padded.contains(" " + first + " "))
			{
				if (only != null && !only.norm.equals(s.norm))
				{
					return out; // two different steps: don't guess
				}
				only = only == null ? s : only;
			}
		}
		if (only != null)
		{
			for (Step s : steps)
			{
				if (s.norm.equals(only.norm) && (tier == null || tier.equals(s.tier)))
				{
					out.add(s);
				}
			}
		}
		return out;
	}

	private static String firstSentence(String t)
	{
		for (int i = 0; i < t.length(); i++)
		{
			char c = t.charAt(i);
			if (c == '.' || c == '!' || c == '?')
			{
				return t.substring(0, i);
			}
		}
		return t;
	}

	/**
	 * Text as matching compares it: tags such as {@code <br>} or
	 * {@code <col=ff0000>} become spaces, lower case, everything but letters
	 * and digits a space, single spaces, trimmed.
	 */
	public static String normalize(String text)
	{
		if (text == null)
		{
			return "";
		}
		String t = text.replaceAll("<[^>]*>", " ").toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder(t.length());
		boolean space = true;
		for (int i = 0; i < t.length(); i++)
		{
			char c = t.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))
			{
				sb.append(c);
				space = false;
			}
			else if (c == '\'' || c == '’')
			{
				// "Champions' Guild" and "Champions Guild" read the same
			}
			else if (!space)
			{
				sb.append(' ');
				space = true;
			}
		}
		int len = sb.length();
		return len > 0 && sb.charAt(len - 1) == ' ' ? sb.substring(0, len - 1) : sb.toString();
	}

	private static final Pattern SHORT_COORDS = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\s*([ns])\\s+(\\d{1,2})\\.(\\d{1,2})\\s*([ew])");
	private static final Pattern LONG_COORDS = Pattern.compile(
		"(\\d{1,2}) degrees? (\\d{1,2}) minutes? (north|south) (\\d{1,2}) degrees? (\\d{1,2}) minutes? (east|west)");

	/**
	 * The coordinates in a coordinate clue's text as "1.30N 8.11W" (no zero
	 * padding), from either "01.30N 08.11W" (the cache) or "01 degrees 30
	 * minutes north 08 degrees 11 minutes west" (wording with degrees and
	 * minutes); null if the text has none.
	 */
	public static String coordinateKey(String text)
	{
		if (text == null)
		{
			return null;
		}
		Matcher m = SHORT_COORDS.matcher(text.toLowerCase(Locale.ROOT));
		if (m.find())
		{
			return key(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6));
		}
		m = LONG_COORDS.matcher(normalize(text));
		if (m.find())
		{
			return key(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6));
		}
		return null;
	}

	private static String key(String d1, String m1, String ns, String d2, String m2, String ew)
	{
		return String.format(Locale.ROOT, "%d.%02d%s %d.%02d%s", Integer.parseInt(d1), Integer.parseInt(m1),
			ns.substring(0, 1).toUpperCase(Locale.ROOT), Integer.parseInt(d2), Integer.parseInt(m2),
			ew.substring(0, 1).toUpperCase(Locale.ROOT));
	}

	private static int[] ints(String s)
	{
		if (s.isEmpty())
		{
			return new int[0];
		}
		String[] w = s.split(",");
		int[] out = new int[w.length];
		for (int i = 0; i < w.length; i++)
		{
			out[i] = Integer.parseInt(w[i].trim());
		}
		return out;
	}

	private static int[] tiles(String s)
	{
		if (s.isEmpty())
		{
			return new int[0];
		}
		String[] w = s.split(";");
		int[] out = new int[w.length];
		for (int i = 0; i < w.length; i++)
		{
			String[] c = w[i].trim().split(",");
			if (c.length != 3)
			{
				throw new IllegalArgumentException("not a tile x,y,plane: " + w[i]);
			}
			out[i] = Tiles.pack(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim()));
		}
		return out;
	}
}
