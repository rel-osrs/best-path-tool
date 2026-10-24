package com.bestpath.tool.menu;

import com.bestpath.tool.edit.TransportRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds the menu text that names a destination: the destination from a record's name ({@link #destination}) and how
 * well an option text names it ({@link #score}, {@link #best}).
 */
final class DestinationMatch
{
	private DestinationMatch()
	{
	}

	/** Words that never decide a match on their own. */
	private static final Set<String> STOP_WORDS = Set.of("the", "to", "of", "a", "an", "teleport", "tele", "travel", "and", "in", "on");

	/** "Teleport to House (outside): Rimmington house portal" -> "Rimmington house portal"; null without ": ". */
	static String afterColon(String name)
	{
		int i = name.indexOf(": ");
		return i < 0 ? null : name.substring(i + 2).trim();
	}

	/** A ride's destination: "Charter ship: Port Sarim to Catherby" -> "Catherby"; else the part after ": ". */
	static String destination(TransportRecord r)
	{
		String after = afterColon(r.name);
		if (after == null)
		{
			return null;
		}
		int to = after.lastIndexOf(" to ");
		int both = after.indexOf(" <-> ");
		return both >= 0 ? after.substring(both + 5) : to >= 0 ? after.substring(to + 4) : after;
	}

	static List<String> tokens(String s)
	{
		String n = s.toLowerCase(Locale.ROOT).replace("&", " and ").replaceAll("<[^>]*>", " ").replaceAll("[^a-z0-9]+", " ").trim();
		List<String> out = new ArrayList<>();
		for (String w : n.split(" "))
		{
			if (!w.isEmpty())
			{
				out.add(w);
			}
		}
		return out;
	}

	/**
	 * How well an option text names a destination: 3 the same words, 2 every word of one (stop words aside) is in
	 * the other ("Seers'" and "Seers' Village", "Rimmington" and "Rimmington house portal"), 1 a looser match (words
	 * written apart, one long word spelt a little differently, or a shortening: "Fenken' Castle" and "Fenkenstrain's
	 * Castle"), 0 no match.
	 */
	static int score(String dest, String option)
	{
		List<String> a = tokens(dest);
		List<String> b = tokens(option);
		if (a.isEmpty() || b.isEmpty())
		{
			return 0;
		}
		if (a.equals(b))
		{
			return 3;
		}
		List<String> small = a.size() <= b.size() ? a : b;
		List<String> big = small == a ? b : a;
		List<String> meaningful = new ArrayList<>(small);
		meaningful.removeAll(STOP_WORDS);
		if (meaningful.isEmpty())
		{
			return 0;
		}
		if (big.containsAll(meaningful))
		{
			return 2;
		}
		// words written apart ("Look out" and "Xeric's Lookout")
		String joined = String.join("", meaningful);
		if (joined.length() >= 5 && String.join("", big).contains(joined))
		{
			return 1;
		}
		// one long word spelt a little differently ("Jatizso" and the cache's "Jatiszo")
		if (a.size() == 1 && b.size() == 1 && a.get(0).length() >= 6 && b.get(0).length() >= 6
			&& a.get(0).charAt(0) == b.get(0).charAt(0) && distance(a.get(0), b.get(0)) <= 2)
		{
			return 1;
		}
		return abbreviationScore(meaningful, big);
	}

	/** Abbreviations: each word of the shorter starts a word of the longer, at least one word exactly: 1, else 0. */
	private static int abbreviationScore(List<String> meaningful, List<String> big)
	{
		int exact = 0;
		for (String w : meaningful)
		{
			boolean found = false;
			for (String v : big)
			{
				if (v.equals(w))
				{
					exact++;
					found = true;
					break;
				}
				if (v.startsWith(w) || w.startsWith(v) && v.length() >= 4)
				{
					found = true;
				}
			}
			if (!found)
			{
				return 0;
			}
		}
		return exact > 0 && meaningful.size() > 1 ? 1 : 0;
	}

	/** Edit distance (insert, delete, change, swap of neighbours). */
	static int distance(String s, String t)
	{
		int[][] d = new int[s.length() + 1][t.length() + 1];
		for (int i = 0; i <= s.length(); i++)
		{
			d[i][0] = i;
		}
		for (int j = 0; j <= t.length(); j++)
		{
			d[0][j] = j;
		}
		for (int i = 1; i <= s.length(); i++)
		{
			for (int j = 1; j <= t.length(); j++)
			{
				int c = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
				d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + c);
				if (i > 1 && j > 1 && s.charAt(i - 1) == t.charAt(j - 2) && s.charAt(i - 2) == t.charAt(j - 1))
				{
					d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
				}
			}
		}
		return d[s.length()][t.length()];
	}

	/**
	 * The option that best names the destination; null if none; a doubt when two different options tie or the match
	 * is only by a shortening or spelling.
	 */
	static String best(String dest, Collection<String> options, List<String> doubts)
	{
		String best = null;
		int bestScore = 0;
		Set<String> tied = new LinkedHashSet<>();
		for (String o : options)
		{
			int s = score(dest, o);
			if (s > bestScore)
			{
				bestScore = s;
				best = o;
				tied.clear();
				tied.add(o);
			}
			else if (s == bestScore && s > 0)
			{
				tied.add(o);
			}
		}
		if (tied.size() > 1 && doubts != null)
		{
			doubts.add("'" + dest + "' matches several options: " + String.join(" / ", tied) + " (first taken)");
		}
		if (bestScore == 1 && doubts != null)
		{
			doubts.add("'" + dest + "' taken as '" + best + "' (shortened or spelt differently)");
		}
		return best;
	}
}
