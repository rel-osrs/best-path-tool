package com.bestpath.core.data;

import com.bestpath.core.sea.BoatType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the game's per-boat Sailing varbits mean: the optional {@value PluginData#BOATS} file, made from the game
 * cache's DB tables when the data is generated. Immutable.
 *
 * <p>Where each varbit's meaning comes from in the game cache (the client scripts that read it, and the tables
 * they look in):
 * <ul>
 * <li>{@code SAILING_BOAT_n_TYPE} = table 166 ({@code SailingBoat}) column 0, the boat kind's id (script 9010:
 * {@code db_find} on that column);</li>
 * <li>{@code _HULL}, {@code _SAIL}, {@code _KEEL}, {@code _STEERING}: an index into that kind's option list (table 166
 * columns 25, 26, 24, 28; scripts 9067 / 9069); the list's rows (tables 178, 179, 177, 181) are matched to
 * {@link com.bestpath.core.sea.Boat}'s part tiers by name and checked against their stats (table 164);</li>
 * <li>{@code _BRAZIER}: 0 = none, else value - 1 indexes column 31 (table 185 rows);</li>
 * <li>{@code _HOTSPOT_k}: 0 = nothing built, else value - 1 indexes the option list (table 175 column 2) of the
 * kind's hotspot k (table 166 column 32, the tuple's row); the options are facilities (table 176);</li>
 * <li>{@code _PORT}: a dock id (table 194 column 0; script 9094), except the values table 147 lists (253 Lost,
 * 254 Capsized, 255 Bottled), which are no dock (scripts 9094, 8973, 8997);</li>
 * <li>{@code _NAME_1..3}: the boat's name, one word per part from table 187 rows 8545-8547 (column 1, value - 1;
 * 0 = the row's column 0), joined with spaces (script 9085); a kind with a display name (column 3) shows that
 * instead (script 9086).</li>
 * </ul>
 *
 * <p>Text form, one record per line (tab separated, {@code #} comments): {@code type}, {@code port}, {@code name}
 * and {@code dock} records.
 */
public final class BoatTable
{
	/** No boat data (no {@value PluginData#BOATS} file): no boat can be read. */
	public static final BoatTable EMPTY = new BoatTable(Map.of(), Map.of(), List.of(), Map.of());

	/** One boat kind (a row of table 166). */
	public static final class Kind
	{
		final int id;
		final BoatType type;
		final String name;
		final int[] hulls;
		final int[] sails;
		final int[] keels;
		final int[] helms;
		final Set<Integer> braziers;
		final Map<Integer, Set<Integer>> inoculation;

		/**
		 * @param type        our boat kind, or null (a kind routes can't use, e.g. a temporary quest boat)
		 * @param hulls       part tier per varbit value (index = value; -1 = an option not matched to a tier)
		 * @param braziers    the {@code _BRAZIER} values that mean an Eternal brazier (icy seas)
		 * @param inoculation hotspot index -> the {@code _HOTSPOT_k} values that mean a fetid-water facility there
		 */
		public Kind(int id, BoatType type, String name, int[] hulls, int[] sails, int[] keels, int[] helms,
			Set<Integer> braziers, Map<Integer, Set<Integer>> inoculation)
		{
			this.id = id;
			this.type = type;
			this.name = name;
			this.hulls = hulls.clone();
			this.sails = sails.clone();
			this.keels = keels.clone();
			this.helms = helms.clone();
			this.braziers = Collections.unmodifiableSet(new TreeSet<>(braziers));
			Map<Integer, Set<Integer>> m = new TreeMap<>();
			inoculation.forEach((k, v) -> m.put(k, Collections.unmodifiableSet(new TreeSet<>(v))));
			this.inoculation = Collections.unmodifiableMap(m);
		}

		/** Our boat kind, or null when routes can't use this kind. */
		public BoatType getType()
		{
			return type;
		}

		/** The game's name ("Sloop", "Will and Anne's boat"). */
		public String getName()
		{
			return name;
		}

		/** Hull tier for a {@code _HULL} value, or -1 (unknown value). */
		public int hull(int value)
		{
			return tier(hulls, value);
		}

		public int sail(int value)
		{
			return tier(sails, value);
		}

		/** Keel tier, or -1 (unknown value, or a kind without keels). */
		public int keel(int value)
		{
			return tier(keels, value);
		}

		public int helm(int value)
		{
			return tier(helms, value);
		}

		public boolean hasKeels()
		{
			return keels.length > 0;
		}

		/** True if this {@code _BRAZIER} value is an Eternal brazier. */
		public boolean brazier(int value)
		{
			return braziers.contains(value);
		}

		/** True if the value of hotspot {@code k} is a facility that lets the boat into fetid water. */
		public boolean inoculation(int hotspot, int value)
		{
			Set<Integer> v = inoculation.get(hotspot);
			return v != null && v.contains(value);
		}

		private static int tier(int[] tiers, int value)
		{
			return value >= 0 && value < tiers.length ? tiers[value] : -1;
		}
	}

	final Map<Integer, Kind> kinds;
	final Map<Integer, String> ports;
	final List<List<String>> nameWords;
	final Map<Integer, String> docks;

	/**
	 * @param ports     {@code _PORT} values that are no dock, with the game's label ("Bottled")
	 * @param nameWords per name part (1-3): the word for value 0 first, then the words for values 1, 2, ...
	 * @param docks     dock id (table 194) -> name, every dock of the table
	 */
	public BoatTable(Map<Integer, Kind> kinds, Map<Integer, String> ports, List<List<String>> nameWords, Map<Integer, String> docks)
	{
		this.kinds = Collections.unmodifiableMap(new TreeMap<>(kinds));
		this.ports = Collections.unmodifiableMap(new TreeMap<>(ports));
		List<List<String>> w = new ArrayList<>();
		for (List<String> part : nameWords)
		{
			w.add(Collections.unmodifiableList(new ArrayList<>(part)));
		}
		this.nameWords = Collections.unmodifiableList(w);
		this.docks = Collections.unmodifiableMap(new TreeMap<>(docks));
	}

	/** The kind for a {@code _TYPE} value, or null (not in the table). */
	public Kind kind(int typeValue)
	{
		return kinds.get(typeValue);
	}

	/** The game's label for a {@code _PORT} value that is no dock ("Bottled", "Capsized", "Lost"), or null (a dock id). */
	public String portStatus(int portValue)
	{
		return ports.get(portValue);
	}

	/** A dock's name (table 194), or null. */
	public String dockName(int dockId)
	{
		return docks.get(dockId);
	}

	/** True if the table has nothing (no boat file): boats can't be read. */
	public boolean isEmpty()
	{
		return kinds.isEmpty();
	}

	/**
	 * The boat's name from its three {@code _NAME_n} values, as the game builds it (script 9085: each part's word,
	 * empty parts left out, joined with spaces). Values outside a list give an empty word.
	 */
	public String boatName(int n1, int n2, int n3)
	{
		int[] v = {n1, n2, n3};
		StringBuilder sb = new StringBuilder();
		for (int p = 0; p < 3 && p < nameWords.size(); p++)
		{
			List<String> words = nameWords.get(p);
			String w = v[p] >= 0 && v[p] < words.size() ? words.get(v[p]) : "";
			if (!w.isEmpty())
			{
				sb.append(sb.length() == 0 ? "" : " ").append(w);
			}
		}
		return sb.toString();
	}

	/** Reads the text form; unknown records are skipped (written by a newer version). */
	public static BoatTable parse(List<String> lines)
	{
		Map<Integer, Kind> kinds = new TreeMap<>();
		Map<Integer, String> ports = new TreeMap<>();
		Map<Integer, List<String>> names = new TreeMap<>();
		Map<Integer, String> docks = new TreeMap<>();
		for (String line : lines)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] w = line.split("\t", -1);
			switch (w[0])
			{
				case "type":
					BoatType type = w[2].equals("-") ? null : BoatType.valueOf(w[2]);
					Map<Integer, Set<Integer>> ino = new TreeMap<>();
					if (!w[9].equals("-"))
					{
						for (String kv : w[9].split(","))
						{
							String[] p = kv.split(":");
							ino.computeIfAbsent(Integer.parseInt(p[0]), k -> new TreeSet<>()).add(Integer.parseInt(p[1]));
						}
					}
					Set<Integer> braziers = new TreeSet<>();
					for (int v : ints(w[8]))
					{
						braziers.add(v);
					}
					int id = Integer.parseInt(w[1]);
					kinds.put(id, new Kind(id, type, w[3], ints(w[4]), ints(w[5]), ints(w[6]), ints(w[7]), braziers, ino));
					break;
				case "port":
					ports.put(Integer.parseInt(w[1]), w[2]);
					break;
				case "name":
					names.put(Integer.parseInt(w[1]), new ArrayList<>(Arrays.asList(w).subList(2, w.length)));
					break;
				case "dock":
					docks.put(Integer.parseInt(w[1]), w[2]);
					break;
				default:
					break;
			}
		}
		List<List<String>> words = new ArrayList<>();
		for (int p = 1; p <= 3; p++)
		{
			words.add(names.getOrDefault(p, List.of("")));
		}
		return new BoatTable(kinds, ports, words, docks);
	}

	private static int[] ints(String s)
	{
		if (s.equals("-") || s.isEmpty())
		{
			return new int[0];
		}
		String[] p = s.split(",");
		int[] out = new int[p.length];
		for (int i = 0; i < p.length; i++)
		{
			out[i] = Integer.parseInt(p[i].trim());
		}
		return out;
	}
}
