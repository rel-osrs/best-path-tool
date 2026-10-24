package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.BankFirstDiagnostics;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The places by kind for "go to nearest", and routes to the nearest place of a kind. */
class PlaceKindsTest
{
	private static final String TEXT = "# a comment\n" + PlaceKinds.HEADER + "\n"
		+ "Altar\t1467\t3210,3210,0 3250,3250,0\n"
		+ "Bank\t1453\t\n"
		+ "Furnace\t1457\t3220,3201,1\n";

	@Test
	void theTextFormReadsAndWritesBack()
	{
		PlaceKinds k = PlaceKinds.parse(TEXT);
		assertEquals(3, k.getKinds().size());
		PlaceKinds.Kind altar = k.find("altar");
		assertEquals("Altar", altar.getName());
		assertEquals(1467, altar.getSprite());
		assertArrayEquals(new int[]{Tiles.pack(3210, 3210, 0), Tiles.pack(3250, 3250, 0)}, altar.getTiles());
		assertEquals(0, k.find("Bank").getTiles().length, "the banks come from banks.tsv");
		assertEquals(Tiles.pack(3220, 3201, 1), k.find("FURNACE").getTiles()[0]);
		assertNull(k.find("Anvil"));
		assertEquals("# made\n" + PlaceKinds.HEADER + "\nAltar\t1467\t3210,3210,0 3250,3250,0\nBank\t1453\t\nFurnace\t1457\t3220,3201,1\n",
			PlaceKindsWriter.write(k, "made"));
		assertEquals(PlaceKindsWriter.write(k, null), PlaceKindsWriter.write(PlaceKinds.parse(PlaceKindsWriter.write(k, "x")), null));
	}

	@Test
	void badLinesAndTwinNamesAreRefused()
	{
		assertThrows(IllegalArgumentException.class, () -> PlaceKinds.parse("Altar\t1\n"));
		assertThrows(IllegalArgumentException.class, () -> PlaceKinds.parse("Altar\t1\t3210,3210\n"));
		assertThrows(IllegalArgumentException.class, () -> PlaceKinds.parse("Altar\t1\t1,1,0\naltar\t2\t2,2,0\n"));
		assertThrows(IllegalArgumentException.class, () -> PlaceKinds.parse("\t1\t1,1,0\n"));
	}

	@Test
	void theBanksComeFromTheBankList()
	{
		BankList banks = BankList.parse(BankList.HEADER + "\nA bank\tbooth\t\t3205,3201,0 3206,3201,0\n"
			+ "B bank\tchest\tquest Dragon Slayer I\t3205,3201,0 3240,3240,0\n");
		PlaceKinds k = PlaceKinds.parse(TEXT).withBanks(banks);
		assertArrayEquals(new int[]{Tiles.pack(3205, 3201, 0), Tiles.pack(3206, 3201, 0), Tiles.pack(3240, 3240, 0)},
			k.find(PlaceKinds.BANK).getTiles(), "every tile once, needs or not (the search keeps the usable ones)");
		assertEquals(List.of("Altar", "Bank", "Furnace"), names(k));
		assertEquals(List.of("Altar", "Furnace"), names(PlaceKinds.parse(TEXT).withBanks(BankList.NONE)), "no banks: no Bank");
	}

	private static List<String> names(PlaceKinds k)
	{
		List<String> out = new java.util.ArrayList<>();
		k.getKinds().forEach(x -> out.add(x.getName()));
		return out;
	}

	/** One open region (3200-3263, 3200-3263) with one blocked tile at 3210,3210 (a furnace no one can stand on). */
	private static PluginRouter router(String bankLines)
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 && !(x == 3210 && y == 3210)
			? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		FreeLand free = new FreeLand.Builder().set(3200, 3200, 3231, 3263, true).build();
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(),
			Tables.empty(), free);
		return new PluginRouter(PluginDataBuilder.withBanks(data, BankList.parse(BankList.HEADER + "\n" + bankLines)), Long.MAX_VALUE);
	}

	@Test
	void theNearestPlaceIsReachedBesideATileNoOneCanStandOn()
	{
		PlaceKinds.Kind furnace = new PlaceKinds.Kind("Furnace", -1, new int[]{Tiles.pack(3250, 3250, 0), Tiles.pack(3210, 3210, 0)});
		PlayerState p = new PlayerState.Builder("p").members(true).build();
		PluginRouter.NearestAnswer n = PluginRouterDiagnostics.routeNearest(router(""), p, 3201, 3201, 0, furnace.getTiles(), id -> false, 24);
		assertEquals(1, n.getChosen(), "the near furnace");
		Route r = n.getAnswer().getRoute();
		assertTrue(r.isFound());
		int[] stop = n.getAnswer().getStop();
		assertTrue(Math.max(Math.abs(stop[0] - 3210), Math.abs(stop[1] - 3210)) == 1, "a tile beside the furnace: " + stop[0]
			+ "," + stop[1]);
		assertEquals(Math.max(stop[0] - 3201, stop[1] - 3201), r.getTicks(), "walked straight to that tile");
	}

	@Test
	void onlyTheBanksAPlayerMayUse()
	{
		PluginRouter r = router("Near bank\tbooth\tquest Dragon Slayer I\t3205,3201,0\nFree bank\tbooth\t\t3220,3220,0\n"
			+ "Members bank\tbooth\tmembers\t3240,3240,0\n");
		PlayerState member = new PlayerState.Builder("p").members(true).build();
		com.bestpath.core.path.BankFirst.Banks b = r.usableBanks(member);
		assertNull(b.bankAt(Tiles.pack(3205, 3201, 0)), "the quest isn't done");
		assertEquals("Free bank", b.bankAt(Tiles.pack(3220, 3220, 0)));
		assertEquals("Members bank", b.bankAt(Tiles.pack(3240, 3240, 0)));
		PlayerState free = new PlayerState.Builder("p").build();
		assertNull(r.usableBanks(free).bankAt(Tiles.pack(3240, 3240, 0)), "a free world: members only and not on free land");
		assertEquals(1, BankFirstDiagnostics.tileCount(r.usableBanks(free)));
	}
}
