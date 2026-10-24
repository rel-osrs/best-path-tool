package com.bestpath.clue;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

/**
 * The STASH chart read like its script 1479 (cache 2735), on made-up enums, varps, arguments and inventory 576 (the
 * object ids of the special cases are the real ones, the rest are made up).
 */
public class StashChartTest
{
	private static final int EASY_A = 900001;
	private static final int EASY_B = 900002;
	private static final int MASTER_INV = 900003;
	private static final int NOT_BUILT = 900004;
	private static final int TROLLHEIM = 29032; // inventory unit with bit 31 of the second int
	private static final int WATCHTOWER = 29057;

	private static int coord(int x, int y)
	{
		return (x << 14) | y;
	}

	private final Map<Integer, Integer> tier = new HashMap<>();
	private final Map<Integer, Integer> inv = new HashMap<>();
	private final Map<Integer, Integer> bits = new HashMap<>();
	private final Map<Integer, Integer> varps = new HashMap<>();

	public StashChartTest()
	{
		tier.put(EASY_A, coord(1, 0)); // easy, bit 0
		tier.put(EASY_B, coord(1, 1));
		tier.put(MASTER_INV, coord(5, 3));
		tier.put(NOT_BUILT, coord(1, 2));
		tier.put(TROLLHEIM, coord(4, 8));
		tier.put(WATCHTOWER, coord(5, 17));
		bits.put(EASY_A, coord(1, 4)); // first int, bit 4
		bits.put(EASY_B, coord(3, 30)); // third int, bit 30
		inv.put(MASTER_INV, coord(7, 9)); // slots 7-9 of inventory 576
		inv.put(TROLLHEIM, coord(10, 12));
		varps.put(1365, 0b011); // easy units 0 and 1 built, 2 not
		varps.put(1368, 1 << 8); // elite bit 8: Trollheim built
		varps.put(1369, (1 << 3) | (1 << 17)); // master bits 3, 17
	}

	private Map<Integer, Boolean> read(int[] args, IntUnaryOperator invSlot)
	{
		return StashChart.read(args, tier, inv, bits, v -> varps.getOrDefault(v, 0), invSlot);
	}

	@Test
	public void builtVarpsAreTheGamesOnes()
	{
		assertArrayEquals(StashVars.BUILT_VARPS, StashChart.TIER_VARPS);
		assertEquals(1365, VarPlayerID.HH_CONSTRUCTED_EASY);
		assertEquals(2202, VarPlayerID.HH_CONSTRUCTED_BEGINNER);
	}

	@Test
	public void bitsOfTheThreeInts()
	{
		Map<Integer, Boolean> m = read(new int[]{1 << 4, 0, 1 << 30}, slot -> -1);
		assertTrue(m.get(EASY_A));
		assertTrue(m.get(EASY_B));
		m = read(new int[]{0, 1 << 4, 0}, slot -> -1);
		assertFalse("bit 4 of the second int is not this unit's", m.get(EASY_A));
		assertFalse(m.get(EASY_B));
	}

	@Test
	public void notBuiltHoldsNothing()
	{
		bits.put(NOT_BUILT, coord(1, 5));
		assertFalse(read(new int[]{-1, -1, -1}, slot -> 1).get(NOT_BUILT));
	}

	@Test
	public void inventoryUnits()
	{
		assertTrue(read(new int[3], slot -> slot == 7 ? 1234 : -1).get(MASTER_INV));
		assertFalse("only the unit's first slot counts", read(new int[3], slot -> slot == 8 ? 1234 : -1).get(MASTER_INV));
		assertNull("inventory 576 never sent: not known", read(new int[3], null).get(MASTER_INV));
	}

	@Test
	public void inventoryUnitWithItsOwnBit()
	{
		assertTrue(read(new int[]{0, 1 << 31, 0}, slot -> -1).get(TROLLHEIM));
		assertTrue("the bit counts even without inventory 576", read(new int[]{0, 1 << 31, 0}, null).get(TROLLHEIM));
		assertFalse(read(new int[]{1 << 31, 0, 1 << 31}, slot -> -1).get(TROLLHEIM));
	}

	@Test
	public void watchtowerHasTwoBits()
	{
		assertTrue(read(new int[]{0, 0, 1 << 10}, slot -> -1).get(WATCHTOWER));
		assertTrue(read(new int[]{0, 0, 1 << 12}, slot -> -1).get(WATCHTOWER));
		assertFalse(read(new int[]{0, 0, 1 << 11}, slot -> -1).get(WATCHTOWER));
	}

	@Test
	public void unknownObjectsAndBadArguments()
	{
		tier.put(900099, coord(1, 0)); // in no filled enum (and not the watchtower)
		assertNull(read(new int[3], slot -> -1).get(900099));
		assertTrue(read(null, slot -> -1).isEmpty());
		assertNull(StashChart.args(new Object[]{1475, 1, 2}));
		assertNull(StashChart.args(new Object[]{1475, 1, "x", 3}));
		assertArrayEquals(new int[]{1, 2, 3}, StashChart.args(new Object[]{1475, 1, 2, 3}));
	}

	@Test
	public void coordsAsTheScriptReadsThem()
	{
		// enum 1525's first unit in cache 2735: value 2 = slots 0-2
		assertEquals(0, StashChart.coordX(2));
		assertEquals(2, StashChart.coordY(2));
		// enum 1440's word 3 bit 23 (Brittle Isle): 49175
		assertEquals(3, StashChart.coordX(49175));
		assertEquals(23, StashChart.coordY(49175));
	}
}
