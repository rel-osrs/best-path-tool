package com.bestpath.tool.wiki;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.sea.Dock;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShipwrightsTest
{
	private static Dock dock(int id, String name, int lx, int ly)
	{
		return new Dock(id, name, 1, null, Dock.Kind.GANGPLANK, lx, ly, 0, 1, lx + 3, ly, 2, List.of());
	}

	@Test
	void eachRecoveringShipwrightGoesToTheNearestDock(@TempDir File dir) throws Exception
	{
		File f = new File(dir, "shipwrights.json");
		Files.writeString(f.toPath(), "{\"records\":["
			+ "{\"npcId\":1,\"name\":\"Shipwright Sam\",\"options\":[\"Talk-to\",\"Recover-boat\"],\"tiles\":[[3024,3204,0]]},"
			+ "{\"npcId\":2,\"name\":\"Shipwright Seb\",\"options\":[\"Talk-to\",\"Repair-boat\"],\"tiles\":[[3100,3100,0]]},"
			+ "{\"npcId\":3,\"name\":\"Junior Jim\",\"options\":[\"Recover-boat\"],\"tiles\":[[2018,2665,0],[3058,2981,0]]},"
			+ "{\"npcId\":4,\"name\":\"Far Away\",\"options\":[\"Recover-boat\"],\"tiles\":[[1000,1000,0]]}]}", StandardCharsets.UTF_8);
		List<Dock> docks = List.of(dock(0, "Port Sarim", 3051, 3193), dock(1, "The Pandemonium", 3070, 2987), dock(2, "Musa Point", 2961, 3146));
		List<String> notes = new ArrayList<>();
		List<Dock> out = Shipwrights.assign(docks, f, notes);
		assertEquals("Shipwright Sam", out.get(0).getShipwright());
		assertEquals("Junior Jim", out.get(1).getShipwright()); // its Pandemonium tile, not the far one
		// where they stand (routes walk there): the Wiki tile nearest the dock
		assertArrayEquals(new int[]{3024, 3204, 0}, out.get(0).getShipwrightTile());
		assertArrayEquals(new int[]{3058, 2981, 0}, out.get(1).getShipwrightTile());
		assertNull(out.get(2).getShipwright()); // Seb only repairs
		assertEquals(1, notes.size());
		assertTrue(notes.get(0).startsWith("Far Away"));
	}

	@Test
	void noFileMeansNoShipwrights(@TempDir File dir) throws Exception
	{
		List<Dock> docks = List.of(dock(0, "Port Sarim", 3051, 3193));
		List<String> notes = new ArrayList<>();
		assertEquals(docks, Shipwrights.assign(docks, new File(dir, "missing.json"), notes));
		assertEquals(1, notes.size());
	}

	@Test
	void theCheckedInFileGivesSixteenDocksAShipwright() throws Exception
	{
		// data/wiki/shipwrights.json against the dock places of cache #2735 (land objects, from the docks layer)
		File f = new File(com.bestpath.tool.ToolSettings.findWorkspace(), "data/wiki/shipwrights.json");
		int[][] places = {{0, 3051, 3193}, {1, 3070, 2987}, {2, 1507, 3403}, {3, 2961, 3146}, {6, 2796, 3412}, {7, 1845, 3687},
			{8, 2758, 3230}, {10, 2686, 3162}, {13, 1775, 3142}, {14, 2580, 2844}, {18, 1452, 2970}, {21, 1861, 3307},
			{22, 2809, 2509}, {23, 2630, 3705}, {26, 1923, 2758}, {29, 2158, 3324}};
		List<Dock> docks = new ArrayList<>();
		for (int[] p : places)
		{
			docks.add(dock(p[0], "dock " + p[0], p[1], p[2]));
		}
		List<String> notes = new ArrayList<>();
		List<Dock> out = Shipwrights.assign(docks, f, notes);
		assertTrue(notes.isEmpty(), notes.toString());
		for (Dock d : out)
		{
			assertTrue(d.getShipwright() != null, d.getName());
			assertTrue(d.getShipwrightTile() != null, d.getName());
		}
	}
}
