package com.bestpath.tool.extract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.path.Tiles;
import com.bestpath.tool.ToolSettings;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The map icon kinds' names and the places table the plugin export writes. */
class MapPlacesTest
{
	@Test
	void namedKindsGetTheirPlacesAndPictures()
	{
		Map<Integer, String> names = MapPlaces.readNames(List.of("# comment", "icon\tkind\twhy", "21\tAltar\tpicture",
			"5\tBank\tbanks.tsv", "9\tFurnace\t", "99\tNowhere\tnot placed"));
		Map<Integer, int[]> found = Map.of(21, new int[]{Tiles.pack(3210, 3210, 0)}, 9, new int[]{Tiles.pack(3220, 3201, 1)},
			5, new int[]{Tiles.pack(1, 1, 0)}, 40, new int[]{Tiles.pack(2, 2, 0)});
		List<String> problems = new ArrayList<>();
		PlaceKinds k = MapPlaces.kinds(names, found, Map.of(21, 1467, 5, 1453, 9, 1457), problems);
		List<String> order = new ArrayList<>();
		k.getKinds().forEach(x -> order.add(x.getName()));
		assertEquals(List.of("Altar", "Bank", "Furnace"), order, "by name; icon 40 has no name, 99 no places");
		assertEquals(1467, k.find("Altar").getSprite());
		assertArrayEquals(new int[]{Tiles.pack(3220, 3201, 1)}, k.find("Furnace").getTiles());
		assertEquals(0, k.find("Bank").getTiles().length, "the banks come from banks.tsv");
		assertEquals(List.of("map icon 99 (Nowhere) is not placed anywhere in this cache: left out"), problems);
	}

	@Test
	void badNameLinesAreRefused()
	{
		assertThrows(IllegalArgumentException.class, () -> MapPlaces.readNames(List.of("21\t\tno name")));
		assertThrows(IllegalArgumentException.class, () -> MapPlaces.readNames(List.of("21\tAltar", "21\tShrine")));
	}

	@Test
	void theNamesFileReads() throws Exception
	{
		File f = new File(ToolSettings.findWorkspace(), MapPlaces.NAMES);
		Map<Integer, String> names = MapPlaces.readNames(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8));
		assertEquals(PlaceKinds.BANK, names.get(5));
		assertEquals("Altar", names.get(21));
		assertTrue(names.size() >= 60, "names: " + names.size());
		assertEquals(names.size(), names.values().stream().map(String::toLowerCase).distinct().count(), "no name twice");
	}

	@Test
	void tilesAreWrittenAsXYPlane()
	{
		assertEquals("3210,3210,0 3220,3201,1", MapPlaces.tiles(new int[]{Tiles.pack(3210, 3210, 0), Tiles.pack(3220, 3201, 1)}));
	}
}
