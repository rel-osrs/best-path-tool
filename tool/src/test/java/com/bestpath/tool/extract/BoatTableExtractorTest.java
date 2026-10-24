package com.bestpath.tool.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.bestpath.core.data.BoatTable;
import com.bestpath.core.data.BoatTableWriter;
import com.bestpath.core.sea.BoatType;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The boat table from made-up DB rows shaped like cache #2735's: kinds matched to our boat kinds, part options
 * matched to tiers by name and checked by their stats, braziers and fetid-water facilities found by their stats.
 */
class BoatTableExtractorTest
{
	private final Map<Integer, Map<Integer, Object[][]>> tables = new HashMap<>();

	/** A row: column, values, column, values, ... */
	private static Object[][] row(Object... colValues)
	{
		int max = 0;
		for (int i = 0; i < colValues.length; i += 2)
		{
			max = Math.max(max, (Integer) colValues[i]);
		}
		Object[][] r = new Object[max + 1][];
		for (int i = 0; i < colValues.length; i += 2)
		{
			r[(Integer) colValues[i]] = (Object[]) colValues[i + 1];
		}
		return r;
	}

	private void put(int table, int rowId, Object[][] row)
	{
		tables.computeIfAbsent(table, k -> new HashMap<>()).put(rowId, row);
	}

	private static Object[] v(Object... values)
	{
		return values;
	}

	private BoatTableExtractor.Result extract() throws IOException
	{
		return BoatTableExtractor.extract(id -> tables.getOrDefault(id, Map.of()));
	}

	private void sample()
	{
		// stats (table 164): hull base speeds, a storm-resistant sail, crystal / kelp resistance, fetid water, icy seas
		put(164, 1, row(24, v(192)));
		put(164, 2, row(24, v(256)));
		put(164, 3, row(24, v(999))); // not what routes assume for its tier
		put(164, 10, row(21, v(0)));
		put(164, 11, row(21, v(1)));
		put(164, 20, row(30, v(0)));
		put(164, 21, row(30, v(1)));
		put(164, 30, row(31, v(0)));
		put(164, 31, row(31, v(1)));
		put(164, 40, row(29, v(1)));
		put(164, 41, row(32, v(1)));
		// parts
		put(178, 100, row(0, v("Wooden hull"), 13, v(1)));
		put(178, 101, row(0, v("Teak hull"), 13, v(2)));
		put(178, 102, row(0, v("Camphor hull"), 13, v(3)));
		put(178, 103, row(0, v("Pirate hull"), 13, v(1)));
		put(179, 110, row(0, v("Wooden mast and linen sails"), 13, v(10)));
		put(179, 111, row(0, v("Oak mast and linen sails"), 13, v(11)));
		put(177, 120, row(0, v("Bronze keel"), 12, v(20)));
		put(177, 121, row(0, v("Adamant keel"), 12, v(21)));
		put(181, 130, row(0, v("Bronze helm"), 15, v(30)));
		put(181, 131, row(0, v("Rune helm"), 15, v(31)));
		put(185, 140, row(0, v("Eternal brazier"), 13, v(41)));
		put(176, 150, row(0, v("Bronze cannon")));
		put(176, 151, row(0, v("Inoculation station"), 20, v(40)));
		put(175, 160, row(2, v(150)));
		put(175, 161, row(2, v(150, 151)));
		// kinds (table 166)
		put(166, 1, row(0, v(2), 1, v("Sloop"), 4, v(50), 7, v("Medium (3x10)"), 18, v(50000), 24, v(120, 121),
			25, v(100, 101, 102, 103), 26, v(110, 111), 28, v(130, 131), 31, v(140), 32, v(1, 0, 2, 160, 2, 1, 2, 161)));
		put(166, 2, row(0, v(0), 1, v("Raft"), 4, v(1), 7, v("Tiny (1x3)"), 18, v(250), 25, v(100), 26, v(110), 28, v(130)));
		put(166, 3, row(0, v(1), 1, v("Skiff"), 4, v(16), 7, v("Small (2x5)"), 18, v(3750), 25, v(100)));
		put(166, 4, row(0, v(3), 1, v("Will and Anne's boat"), 4, v(1), 7, v("Small (2x5)"), 25, v(100), 40, v(1)));
		// statuses, name words, docks
		put(147, 1, row(0, v(254), 1, v(7175), 2, v("Capsized")));
		put(147, 2, row(0, v(-1), 1, v(7115), 2, v("Unknown")));
		put(187, 8545, row(0, v("")));
		put(187, 8546, row(0, v(""), 1, v("Jolly", "Merry")));
		put(187, 8547, row(0, v("Boat"), 1, v("Raven", "Wave")));
		put(194, 1, row(0, v(0), 1, v("Port Sarim")));
		put(194, 2, row(0, v(23), 1, v("Rellekka")));
	}

	@Test
	void kindsAndPartsFollowTheTables() throws IOException
	{
		sample();
		BoatTableExtractor.Result r = extract();
		BoatTable t = r.table;
		BoatTable.Kind sloop = t.kind(2);
		assertSame(BoatType.SLOOP, sloop.getType());
		assertEquals(0, sloop.hull(0), "Wooden hull, base speed 192 / 128 = 1.5");
		assertEquals(2, sloop.hull(1), "Teak hull, 2.0");
		assertEquals(-1, sloop.hull(2), "Camphor hull whose stats say 999: not used");
		assertEquals(-1, sloop.hull(3), "a name that is no tier: not used");
		assertEquals(1, sloop.sail(1));
		assertEquals(4, sloop.keel(1));
		assertEquals(5, sloop.helm(1));
		assertTrue(sloop.brazier(1), "_BRAZIER 1 = the first brazier option (icy seas)");
		assertFalse(sloop.brazier(0));
		assertTrue(sloop.inoculation(1, 2), "hotspot 1, its second option is the fetid-water facility");
		assertFalse(sloop.inoculation(0, 1));
		assertFalse(sloop.inoculation(1, 1));
		assertTrue(r.notes.stream().anyMatch(n -> n.contains("Camphor hull") && n.contains("stats")), r.notes.toString());
		assertTrue(r.notes.stream().anyMatch(n -> n.contains("Pirate hull") && n.contains("not one of the tiers")), r.notes.toString());

		assertSame(BoatType.RAFT, t.kind(0).getType());
		assertFalse(t.kind(0).hasKeels());
		assertNull(t.kind(1).getType(), "a skiff whose level is not BoatType's: not used");
		assertTrue(r.notes.stream().anyMatch(n -> n.contains("Skiff")), r.notes.toString());
		assertNull(t.kind(3).getType(), "a temporary boat: not used");
		assertEquals("Will and Anne's boat", t.kind(3).getName());
	}

	@Test
	void statusesNamesAndDocks() throws IOException
	{
		sample();
		BoatTable t = extract().table;
		assertEquals("Capsized", t.portStatus(254));
		assertNull(t.portStatus(-1), "no varbit value can be -1");
		assertEquals("Jolly Wave", t.boatName(0, 1, 2));
		assertEquals("Boat", t.boatName(0, 0, 0));
		assertEquals("Rellekka", t.dockName(23));
		BoatTable back = BoatTable.parse(java.util.Arrays.asList(BoatTableWriter.write(t, "x").split("\n")));
		assertEquals(BoatTableWriter.write(t, "x"), BoatTableWriter.write(back, "x"));
	}
}
