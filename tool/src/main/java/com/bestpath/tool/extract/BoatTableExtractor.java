package com.bestpath.tool.extract;

import com.bestpath.core.data.BoatTable;
import com.bestpath.core.data.BoatTableDiagnostics;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.BoatTypeDiagnostics;
import com.bestpath.core.sea.SeaArea;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the plugin's {@link BoatTable} (what the game's per-boat varbits mean) from the game cache's DB tables.
 * How each varbit is used was read from the client scripts in cache #2735 (see {@link BoatTable}); the tables are
 * read here, so a new cache gives a new table on the next export.
 *
 * <p>Part tiers: each option row is matched to {@link Boat}'s tier by the first word of its name ({@link Boat#WOODS}
 * for hulls and sails, {@link Boat#METALS} for keels and helms), then checked against its stats row (table 164):
 * the hull's base speed (column 24 / 128) must be {@link Boat#speed()} for that tier; the sail's storm resistance
 * (column 21), the keel's crystal-flecked resistance (column 30) and the helm's tangled-kelp resistance (column 31)
 * must give the same restricted waters as {@link Boat#canEnter}. An option that does not match is written as -1
 * (the plugin then does not use a boat with it) and reported.
 */
public final class BoatTableExtractor
{
	/** Table 187 rows with the three name parts' words (client script 9085, cache #2735). */
	static final int[] NAME_ROWS = {8545, 8546, 8547};

	/** DB rows by table (the cache, or a test's made-up rows). */
	public interface Rows
	{
		Map<Integer, Object[][]> table(int tableId) throws IOException;
	}

	/** The table and anything odd found. */
	public static final class Result
	{
		public final BoatTable table;
		public final List<String> notes;

		Result(BoatTable table, List<String> notes)
		{
			this.table = table;
			this.notes = notes;
		}
	}

	private BoatTableExtractor()
	{
	}

	public static Result extract(Rows db) throws IOException
	{
		List<String> notes = new ArrayList<>();
		Map<Integer, BoatTable.Kind> kinds = kinds(db, notes);
		Map<Integer, String> ports = ports(db);
		List<List<String>> nameWords = nameWords(db, notes);
		Map<Integer, String> docks = new TreeMap<>();
		for (Object[][] row : db.table(194).values())
		{
			docks.put(integer(row, 0, 0, -1), string(row, 1));
		}
		return new Result(new BoatTable(kinds, ports, nameWords, docks), notes);
	}

	/** Every boat kind (table 166) with its part tiers, brazier values and inoculation stations. */
	private static Map<Integer, BoatTable.Kind> kinds(Rows db, List<String> notes) throws IOException
	{
		Map<Integer, Object[][]> boats = db.table(166);
		Map<Integer, Object[][]> hotspots = db.table(175);
		Map<Integer, Object[][]> facilities = db.table(176);
		Map<Integer, Object[][]> keels = db.table(177);
		Map<Integer, Object[][]> hulls = db.table(178);
		Map<Integer, Object[][]> sails = db.table(179);
		Map<Integer, Object[][]> helms = db.table(181);
		Map<Integer, Object[][]> braziers = db.table(185);
		Map<Integer, Object[][]> stats = db.table(164);

		Map<Integer, BoatTable.Kind> kinds = new TreeMap<>();
		for (Object[][] row : boats.values())
		{
			int id = integer(row, 0, 0, -1);
			String name = string(row, 1);
			BoatType type = type(row, name, notes);
			int[] hullTiers = tiers(row, 25, hulls, 13, Boat.WOODS, (t, s) -> speedOk(type, t, s), stats, label(type, name) + " hull", notes);
			int[] sailTiers = tiers(row, 26, sails, 13, Boat.WOODS, (t, s) -> areaOk(type, 1, t, SeaArea.TEMPOR_TANTRUM, s, 21), stats,
				label(type, name) + " sails", notes);
			int[] keelTiers = tiers(row, 24, keels, 12, Boat.METALS, (t, s) -> areaOk(type, 2, t, SeaArea.CRYSTALLINE, s, 30), stats,
				label(type, name) + " keel", notes);
			int[] helmTiers = tiers(row, 28, helms, 15, Boat.METALS, (t, s) -> areaOk(type, 3, t, SeaArea.KELP, s, 31), stats,
				label(type, name) + " helm", notes);
			Set<Integer> brazierValues = brazierValues(row, braziers, stats);
			Map<Integer, Set<Integer>> inoculation = inoculation(row, hotspots, facilities, stats);
			kinds.put(id, new BoatTable.Kind(id, type, name, hullTiers, sailTiers, keelTiers, helmTiers, brazierValues, inoculation));
		}
		return kinds;
	}

	/** The kind's brazier varbit values whose brazier has the stats value (column 32) set. */
	private static Set<Integer> brazierValues(Object[][] row, Map<Integer, Object[][]> braziers, Map<Integer, Object[][]> stats)
	{
		Set<Integer> brazierValues = new TreeSet<>();
		Object[] bz = row.length > 31 ? row[31] : null;
		for (int i = 0; bz != null && i < bz.length; i++)
		{
			Object[][] b = braziers.get((Integer) bz[i]);
			if (b != null && stat(stats, b, 13, 32) != 0)
			{
				brazierValues.add(i + 1); // _BRAZIER: 0 = none, else value - 1 indexes the list
			}
		}
		return brazierValues;
	}

	/** Per hotspot of the kind: the facility varbit values whose facility has the stats value (column 29) set. */
	private static Map<Integer, Set<Integer>> inoculation(Object[][] row, Map<Integer, Object[][]> hotspots, Map<Integer, Object[][]> facilities,
		Map<Integer, Object[][]> stats)
	{
		Map<Integer, Set<Integer>> inoculation = new TreeMap<>();
		Object[] hs = row.length > 32 ? row[32] : null;
		for (int k = 0; hs != null && 4 * k + 3 < hs.length; k++)
		{
			Object[][] h = hotspots.get((Integer) hs[4 * k + 3]);
			Object[] options = h == null || h.length <= 2 ? null : h[2];
			for (int i = 0; options != null && i < options.length; i++)
			{
				Object[][] f = facilities.get((Integer) options[i]);
				if (f != null && stat(stats, f, 20, 29) != 0)
				{
					inoculation.computeIfAbsent(k, x -> new TreeSet<>()).add(i + 1); // 0 = nothing built
				}
			}
		}
		return inoculation;
	}

	/** Port status value -> its name (table 147). */
	private static Map<Integer, String> ports(Rows db) throws IOException
	{
		Map<Integer, String> ports = new TreeMap<>();
		for (Object[][] row : db.table(147).values())
		{
			int value = integer(row, 0, 0, -1);
			if (value >= 0)
			{
				ports.put(value, string(row, 2));
			}
		}
		return ports;
	}

	/** The words of each boat name part (table 187, {@link #NAME_ROWS}): the part's label first, then its words. */
	private static List<List<String>> nameWords(Rows db, List<String> notes) throws IOException
	{
		Map<Integer, Object[][]> words = db.table(187);
		List<List<String>> nameWords = new ArrayList<>();
		for (int rowId : NAME_ROWS)
		{
			Object[][] row = words.get(rowId);
			List<String> part = new ArrayList<>();
			part.add(row == null ? "" : string(row, 0));
			if (row != null && row.length > 1 && row[1] != null)
			{
				for (Object w : row[1])
				{
					part.add(String.valueOf(w));
				}
			}
			if (row == null)
			{
				notes.add("boat name words: table 187 has no row " + rowId);
			}
			nameWords.add(part);
		}
		return nameWords;
	}

	/** The name used in notes; "-" first for a kind routes don't use (its parts are not reported). */
	private static String label(BoatType type, String name)
	{
		return type == null ? "-" + name : name;
	}

	/** Our kind for a table 166 row: the same name, size, level and retrieval cost, and not a temporary boat. */
	private static BoatType type(Object[][] row, String name, List<String> notes)
	{
		if (integer(row, 40, 0, 0) == 1)
		{
			return null; // a temporary boat (column 40), e.g. Will and Anne's: routes don't use it
		}
		for (BoatType t : BoatType.values())
		{
			if (t.getLabel().equalsIgnoreCase(name))
			{
				String size = string(row, 7);
				boolean same = integer(row, 4, 0, -1) == BoatTypeDiagnostics.level(t) && integer(row, 18, 0, -1) == t.getRetrievalCost()
					&& size.contains("(" + t.getWidth() + "x" + BoatTypeDiagnostics.length(t) + ")");
				if (!same)
				{
					notes.add("boat kind " + name + ": level, retrieval cost or size differ from BoatType " + t + " (" + size + ")");
					return null;
				}
				return t;
			}
		}
		notes.add("boat kind " + name + " (type " + integer(row, 0, 0, -1) + ") is not one routes know");
		return null;
	}

	private interface Check
	{
		/** True if the option's stats agree with what {@link Boat} assumes for this tier. */
		boolean ok(int tier, Object[][] stats);
	}

	/** Tier per varbit value: the kind's option list (column {@code col}) matched by name, checked by stats. */
	private static int[] tiers(Object[][] row, int col, Map<Integer, Object[][]> parts, int statsCol, String[] words, Check check,
		Map<Integer, Object[][]> stats, String what, List<String> notes)
	{
		boolean report = !what.startsWith("-");
		Object[] options = row.length > col ? row[col] : null;
		if (options == null)
		{
			return new int[0];
		}
		int[] out = new int[options.length];
		for (int i = 0; i < options.length; i++)
		{
			Object[][] part = parts.get((Integer) options[i]);
			String name = part == null ? "?" : string(part, 0);
			int tier = -1;
			String first = name.split(" ")[0];
			for (int t = 0; t < words.length; t++)
			{
				if (words[t].equalsIgnoreCase(first))
				{
					tier = t;
				}
			}
			Object[][] s = part == null || part.length <= statsCol || part[statsCol] == null ? null : stats.get((Integer) part[statsCol][0]);
			if (tier >= 0 && (s == null || !check.ok(tier, s)))
			{
				notes.add(String.format(Locale.ROOT, "%s option %d (%s): stats differ from what routes assume; not used", what, i, name));
				tier = -1;
			}
			else if (tier < 0 && report)
			{
				notes.add(String.format(Locale.ROOT, "%s option %d (%s): not one of the tiers routes know; not used", what, i, name));
			}
			out[i] = tier;
		}
		return out;
	}

	/** Hull: the stats row's base speed (column 24, / 128 tiles per tick) is the speed {@link Boat} gives the tier. */
	private static boolean speedOk(BoatType type, int tier, Object[][] s)
	{
		Integer base = s.length > 24 && s[24] != null ? (Integer) s[24][0] : null;
		return base != null && Math.abs(base / 128.0 - boat(type, 0, tier).speed()) < 1e-9;
	}

	/** Sail / keel / helm (part 1 / 2 / 3): the stats column's resistance lets it in exactly when {@link Boat#canEnter} does. */
	private static boolean areaOk(BoatType type, int part, int tier, SeaArea area, Object[][] s, int col)
	{
		int v = s.length > col && s[col] != null ? (Integer) s[col][0] : 0;
		return (v != 0) == boat(type, part, tier).canEnter(area);
	}

	/** A boat with one part at {@code tier} (part 0 hull, 1 sails, 2 keel, 3 helm), the others at the lowest tier. */
	private static Boat boat(BoatType type, int part, int tier)
	{
		BoatType t = type == null ? BoatType.SLOOP : type;
		if (part == 2 && !t.hasKeel())
		{
			t = BoatType.SLOOP; // checking a keel row: any kind with keels
		}
		return new Boat(1, t, part == 0 ? tier : 0, part == 1 ? tier : 0, part == 2 ? tier : 0, part == 3 ? tier : 0, false, false,
			Boat.Focus.NONE, Boat.NOWHERE);
	}

	/** A part's stats value: its stats row (column {@code statsCol}) in table 164, column {@code col}; 0 if absent. */
	private static int stat(Map<Integer, Object[][]> stats, Object[][] part, int statsCol, int col)
	{
		if (part.length <= statsCol || part[statsCol] == null)
		{
			return 0;
		}
		Object[][] s = stats.get((Integer) part[statsCol][0]);
		return s == null || s.length <= col || s[col] == null ? 0 : (Integer) s[col][0];
	}

	private static int integer(Object[][] row, int col, int i, int fallback)
	{
		return row.length > col && row[col] != null && row[col].length > i && row[col][i] instanceof Integer ? (Integer) row[col][i] : fallback;
	}

	private static String string(Object[][] row, int col)
	{
		return row.length > col && row[col] != null && row[col].length > 0 ? String.valueOf(row[col][0]) : "";
	}

	/** For logs: "Raft: hull 0-6, sails 0-6, ..." */
	public static String describe(BoatTable t)
	{
		StringBuilder sb = new StringBuilder();
		for (BoatTable.Kind k : BoatTableDiagnostics.kinds(t).values())
		{
			sb.append(sb.length() == 0 ? "" : "; ").append(k.getName()).append(" (type ").append(BoatTableDiagnostics.id(k)).append(", ")
				.append(k.getType() == null ? "not for routes" : k.getType().name()).append(')');
		}
		return sb + "; port statuses " + BoatTableDiagnostics.ports(t) + "; " + BoatTableDiagnostics.docks(t).size() + " docks";
	}

}
