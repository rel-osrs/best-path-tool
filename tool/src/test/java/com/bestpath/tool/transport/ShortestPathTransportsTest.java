package com.bestpath.tool.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShortestPathTransportsTest
{
	@Test
	void rowsKeepTheirEndsAndEveryColumn()
	{
		List<ShortestPathTransports.Row> rows = ShortestPathTransports.read("x.tsv", Arrays.asList(
			"# Origin\tDestination\tSkills\tQuests",
			"# a comment",
			"",
			"3 4 0\t1 2 0\t25 Magic\tPlague City",
			"\t5 6 1\t\t"));
		assertEquals(2, rows.size());
		assertArrayEquals(new int[]{3, 4, 0}, rows.get(0).origin);
		assertArrayEquals(new int[]{1, 2, 0}, rows.get(0).destination);
		assertEquals("25 Magic", rows.get(0).columns.get("Skills"));
		assertEquals("Plague City", rows.get(0).columns.get("Quests"));
		assertEquals(4, rows.get(0).line);
		assertNull(rows.get(1).origin);
		assertArrayEquals(new int[]{5, 6, 1}, rows.get(1).destination);
	}

	@Test
	void trinketOfFairiesRowsAreLeftOut()
	{
		List<ShortestPathTransports.Row> rows = ShortestPathTransports.read("teleportation_items.tsv", Arrays.asList(
			"# Destination\tItems\tDuration\tDisplay info",
			"2996 3114 0\t28564=1\t4\tTrinket of fairies: Fairy Ring - AIQ",
			"3212 3424 0\t28564=1||1234=1\t4\tAlso another item: kept",
			"3221 3218 0\t\t0\tNo items: kept"));
		assertEquals(2, rows.size());
		assertEquals("Also another item: kept", rows.get(0).columns.get("Display info"));
	}

	@Test
	void theCopiedFilesLoad() throws Exception
	{
		File dir = new File("../data/shortest-path");
		if (!dir.isDirectory())
		{
			return; // run from elsewhere
		}
		List<ShortestPathTransports.Row> rows = ShortestPathTransports.load(dir);
		assertTrue(rows.size() > 8000, rows.size() + " rows");
	}
}
