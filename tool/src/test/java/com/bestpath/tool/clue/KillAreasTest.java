package com.bestpath.tool.clue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Kill steps: Wiki spawn tiles in groups (one route point per group), task-only areas skipped, planes fixed. */
class KillAreasTest
{
	private static String[] line(String step, String npc, String location, String map, int x, int y, int plane)
	{
		return new String[]{step, npc, npc, "100", location, map, String.valueOf(x), String.valueOf(y), String.valueOf(plane)};
	}

	@Test
	void tilesWithinTheGapChainIntoOneGroup()
	{
		List<List<int[]>> g = KillAreas.group(List.of(new int[]{0, 0}, new int[]{50, 50}, new int[]{12, 0}, new int[]{24, 5}, new int[]{62, 50}),
			KillAreas.GAP);
		assertEquals(2, g.size());
		assertEquals(3, g.get(0).size(), "0,0 - 12,0 - 24,5 chained");
		assertEquals(2, g.get(1).size(), "50,50 and 62,50");
		assertEquals(50, g.get(1).get(0)[0], "groups in the order of their first tile");
	}

	@Test
	void groupsAreSplitWhenFartherThanTheGap()
	{
		List<List<int[]>> g = KillAreas.group(List.of(new int[]{0, 0}, new int[]{13, 0}), 12);
		assertEquals(2, g.size());
		assertEquals(1, KillAreas.group(List.of(new int[]{0, 0}, new int[]{12, 12}), 12).size(), "diagonal 12 is within the gap");
	}

	@Test
	void twoGroupsOfDogsAreTwoPointsAndTaskOnlyAreasAreLeftOut()
	{
		List<String[]> lines = List.of(
			line("cryptic-1464", "Hellhound", "Taverley Dungeon", "20", 2860, 9840, 0),
			line("cryptic-1464", "Hellhound", "Taverley Dungeon", "20", 2862, 9842, 0),
			line("cryptic-1464", "Hellhound", "Taverley Dungeon", "20", 2900, 9800, 0),
			line("cryptic-1464", "Hellhound", "Stronghold Slayer Cave", "19", 2430, 9780, 0),
			line("cryptic-1460", "Bloodveld", "God Wars Dungeon", "7", 2880, 5320, 0));
		ClueOverrides o = ClueOverrides.parse(List.of("key\tchange\tvalue\tsource", "cryptic-1464\tskip-area\tStronghold Slayer Cave\tWiki",
			"cryptic-1460\tarea-plane\t2 God Wars Dungeon\tcheck"));
		Map<String, List<KillAreas.Group>> g = KillAreas.groups(lines, o);
		List<KillAreas.Group> dogs = g.get("cryptic-1464");
		assertEquals(2, dogs.size(), "two groups in Taverley Dungeon, the task-only cave left out");
		assertEquals("Hellhound - Taverley Dungeon (Wiki map 20) group 1 of 2", dogs.get(0).label());
		assertEquals(2, dogs.get(0).tiles.size());
		assertTrue(dogs.get(0).wiki.contains("Hellhound@100"));
		for (KillAreas.Group d : dogs)
		{
			assertFalse(d.location.equals("Stronghold Slayer Cave"));
		}
		KillAreas.Group gwd = g.get("cryptic-1460").get(0);
		assertEquals(2, gwd.plane, "the Wiki layer's plane 0 is plane 2 in the game");
		assertEquals("Bloodveld - God Wars Dungeon (Wiki map 7)", gwd.label());
		// without overrides: three areas, Wiki planes
		assertEquals(3, KillAreas.groups(lines, ClueOverrides.NONE).get("cryptic-1464").size());
		assertEquals(0, KillAreas.groups(lines, ClueOverrides.NONE).get("cryptic-1460").get(0).plane);
	}

	@Test
	void anAreaShiftMovesTheWikiTilesOntoTheGamesMap()
	{
		List<String[]> lines = List.of(
			line("cryptic-1455", "Ankou", "Stronghold of Security: Sepulchre of Death", "18", 1963, 4941, 0),
			line("cryptic-1455", "Ankou", "Stronghold of Security: Sepulchre of Death", "18", 1965, 4938, 0),
			line("cryptic-1455", "Ankou", "Stronghold of Security: Sepulchre of Death", "18", 2003, 4953, 0),
			line("cryptic-1455", "Ankou", "Other place", "0", 3000, 3000, 0));
		ClueOverrides o = ClueOverrides.parse(List.of("key\tchange\tvalue\tsource",
			"cryptic-1455\tarea-shift\t352,288,1 Stronghold of Security: Sepulchre of Death\tcache fit"));
		List<KillAreas.Group> g = KillAreas.groups(lines, o).get("cryptic-1455");
		assertEquals(3, g.size(), "the moved area keeps its two groups (the shift moves every tile alike)");
		assertArrayEquals(new int[]{2315, 5229}, g.get(0).tiles.get(0), "1963+352, 4941+288");
		assertEquals(1, g.get(0).plane, "the shift's plane");
		assertArrayEquals(new int[]{352, 288}, g.get(0).shift);
		assertEquals("Ankou - Stronghold of Security: Sepulchre of Death (Wiki map 18, moved 352,288) group 1 of 2", g.get(0).label());
		KillAreas.Group other = g.get(2);
		assertArrayEquals(new int[]{3000, 3000}, other.tiles.get(0), "other areas stay");
		assertNull(other.shift);
	}

	@Test
	void middleIsARealSpawnTile()
	{
		List<String[]> lines = List.of(
			line("s", "N", "A", "0", 10, 10, 0),
			line("s", "N", "A", "0", 14, 10, 0),
			line("s", "N", "A", "0", 13, 11, 0),
			line("s", "N", "A", "0", 20, 10, 0));
		KillAreas.Group g = KillAreas.groups(lines, ClueOverrides.NONE).get("s").get(0);
		assertArrayEquals(new int[]{14, 10}, g.middle(), "mean 14.25,10.25: the nearest spawn tile");
	}
}
