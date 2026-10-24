package com.bestpath.player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The small texts the plugin keeps per account (RuneLite RS profile config):
 * the bank as "id:count,id:count" sorted by id, the ownership cache as
 * "id,id", and player answers one per line (a yes: the answer's words;
 * a no: "no", a tab, the words). Sorted, so the same contents
 * always give the same text and "diff before persisting" is a string compare.
 * Bad parts are skipped (a hand-edited or damaged value never stops the
 * plugin). Pure.
 */
public final class ProfileText
{
	private ProfileText()
	{
	}

	public static String encodeCounts(Map<Integer, Integer> counts)
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Integer> e : new TreeMap<>(counts).entrySet())
		{
			if (e.getKey() > 0 && e.getValue() > 0)
			{
				if (sb.length() > 0)
				{
					sb.append(',');
				}
				sb.append(e.getKey()).append(':').append(e.getValue());
			}
		}
		return sb.toString();
	}

	/** Null text = nothing saved = null (unknown); a saved empty text = an empty map. */
	public static Map<Integer, Integer> decodeCounts(String text)
	{
		if (text == null)
		{
			return null;
		}
		Map<Integer, Integer> out = new TreeMap<>();
		for (String part : text.split(","))
		{
			int colon = part.indexOf(':');
			if (colon <= 0)
			{
				continue;
			}
			try
			{
				int id = Integer.parseInt(part.substring(0, colon).trim());
				int n = Integer.parseInt(part.substring(colon + 1).trim());
				if (id > 0 && n > 0)
				{
					out.merge(id, n, Integer::sum);
				}
			}
			catch (NumberFormatException e)
			{
				// skip a damaged part
			}
		}
		return out;
	}

	public static String encodeIds(Collection<Integer> ids)
	{
		StringBuilder sb = new StringBuilder();
		for (int id : new TreeSet<>(ids))
		{
			if (id > 0)
			{
				if (sb.length() > 0)
				{
					sb.append(',');
				}
				sb.append(id);
			}
		}
		return sb.toString();
	}

	public static Set<Integer> decodeIds(String text)
	{
		Set<Integer> out = new TreeSet<>();
		if (text == null)
		{
			return out;
		}
		for (String part : text.split(","))
		{
			try
			{
				int id = Integer.parseInt(part.trim());
				if (id > 0)
				{
					out.add(id);
				}
			}
			catch (NumberFormatException e)
			{
				// skip
			}
		}
		return out;
	}

	/** Starts a line of a "no" answer (a tab can't be part of an answer: the data is tab-separated). */
	public static final String NO_PREFIX = "no\t";

	/**
	 * Player answers: one per line, a yes as its words, a no as {@link #NO_PREFIX} + words; blank lines skipped.
	 * Of the same words twice (any case), the last line wins.
	 */
	public static Map<String, Boolean> decodeAnswers(String text)
	{
		Map<String, Boolean> out = new LinkedHashMap<>();
		if (text == null)
		{
			return out;
		}
		Map<String, String> byKey = new HashMap<>();
		for (String line : text.split("\n"))
		{
			boolean no = line.startsWith(NO_PREFIX);
			String words = (no ? line.substring(NO_PREFIX.length()) : line).trim();
			if (words.isEmpty())
			{
				continue;
			}
			String old = byKey.put(words.toLowerCase(Locale.ROOT), words);
			if (old != null)
			{
				out.remove(old);
			}
			out.put(words, !no);
		}
		return out;
	}

	/** The saved text of the answers: one per line (see {@link #decodeAnswers}), sorted, so equal answers give equal text. */
	public static String encodeAnswers(Map<String, Boolean> answers)
	{
		Set<String> lines = new TreeSet<>();
		for (Map.Entry<String, Boolean> e : answers.entrySet())
		{
			String words = e.getKey() == null ? "" : e.getKey().replace('\n', ' ').replace('\t', ' ').trim();
			if (!words.isEmpty() && e.getValue() != null)
			{
				lines.add(e.getValue() ? words : NO_PREFIX + words);
			}
		}
		return String.join("\n", lines);
	}

	/** Lines of text (such as the house things), trimmed, in order, blank lines skipped. */
	public static Set<String> decodeLines(String text)
	{
		if (text == null)
		{
			return Collections.emptySet();
		}
		Set<String> out = new LinkedHashSet<>();
		for (String line : text.split("\n"))
		{
			if (!line.isBlank())
			{
				out.add(line.trim());
			}
		}
		return out;
	}

	/**
	 * Adds the requirement items among {@code seen} to {@code owned} (the ownership cache).
	 *
	 * @return true if something was added (then the cache is saved)
	 */
	public static boolean addOwned(Set<Integer> owned, Collection<Integer> seen, Set<Integer> transportItems)
	{
		boolean added = false;
		for (int id : seen)
		{
			if (transportItems.contains(id))
			{
				added |= owned.add(id);
			}
		}
		return added;
	}
}
