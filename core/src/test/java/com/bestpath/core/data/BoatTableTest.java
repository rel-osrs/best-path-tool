package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.bestpath.core.sea.BoatType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The boat table's text form (the export's boats.tsv) and its lookups. */
class BoatTableTest
{
	/** As the export writes it for cache #2735 (shortened word lists and docks). */
	static final List<String> SAMPLE = Arrays.asList(
		"# BestPath plugin data: boats",
		"type\t0\tRAFT\tRaft\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t-\t0,1,2,3,4,5,6\t1\t-",
		"type\t1\tSKIFF\tSkiff\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t1\t5:1",
		"type\t2\tSLOOP\tSloop\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t1\t9:1",
		"type\t3\t-\tWill and Anne's boat\t-1\t-1\t-1\t-1\t-\t-",
		"port\t253\tLost",
		"port\t254\tCapsized",
		"port\t255\tBottled",
		"name\t1\t",
		"name\t2\t\tAdamant\tAlmighty\tJolly",
		"name\t3\tBoat\tAegis\tAeon\tRaven",
		"dock\t0\tPort Sarim",
		"dock\t23\tRellekka");

	@Test
	void readsKindsAndTheirParts()
	{
		BoatTable t = BoatTable.parse(SAMPLE);
		assertFalse(t.isEmpty());
		BoatTable.Kind sloop = t.kind(2);
		assertSame(BoatType.SLOOP, sloop.getType());
		assertEquals("Sloop", sloop.getName());
		assertEquals(2, sloop.hull(2));
		assertEquals(-1, sloop.hull(7)); // a value beyond the list: unknown
		assertEquals(-1, sloop.hull(-1));
		assertEquals(4, sloop.keel(4));
		assertTrue(sloop.hasKeels());
		assertTrue(sloop.brazier(1));
		assertFalse(sloop.brazier(0));
		assertFalse(sloop.brazier(2));
		assertTrue(sloop.inoculation(9, 1));
		assertFalse(sloop.inoculation(5, 1));
		assertFalse(sloop.inoculation(9, 2));

		BoatTable.Kind raft = t.kind(0);
		assertFalse(raft.hasKeels());
		assertEquals(-1, raft.keel(0));
		assertTrue(t.kind(1).inoculation(5, 1));

		BoatTable.Kind quest = t.kind(3);
		assertNull(quest.getType());
		assertEquals("Will and Anne's boat", quest.getName());
		assertEquals(-1, quest.hull(0));
		assertNull(t.kind(4));
	}

	@Test
	void portsDocksAndNames()
	{
		BoatTable t = BoatTable.parse(SAMPLE);
		assertEquals("Bottled", t.portStatus(255));
		assertEquals("Lost", t.portStatus(253));
		assertNull(t.portStatus(0));
		assertEquals("Port Sarim", t.dockName(0));
		assertNull(t.dockName(1));
		// script 9085: the words for each part's value, empty ones left out, joined with spaces
		assertEquals("Jolly Raven", t.boatName(0, 3, 3));
		assertEquals("Boat", t.boatName(0, 0, 0));
		assertEquals("Adamant Boat", t.boatName(0, 1, 0));
		assertEquals("Raven", t.boatName(5, 99, 3)); // values outside a list give no word
	}

	@Test
	void textFormReadsBackTheSame()
	{
		BoatTable t = BoatTable.parse(SAMPLE);
		String text = BoatTableWriter.write(t, "test\nsecond line");
		assertTrue(text.startsWith("# test\n# second line\n"));
		BoatTable back = BoatTable.parse(PluginData.lines(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		assertEquals(text, BoatTableWriter.write(back, "test\nsecond line"));
		assertEquals(4, BoatTableDiagnostics.kinds(back).size());
		assertEquals("Jolly Raven", back.boatName(0, 3, 3));
	}

	@Test
	void emptyTableReadsNothing()
	{
		assertTrue(BoatTable.EMPTY.isEmpty());
		assertNull(BoatTable.EMPTY.kind(0));
		assertEquals("", BoatTable.EMPTY.boatName(1, 1, 1));
		assertTrue(BoatTable.parse(List.of("# nothing")).isEmpty());
	}
}
