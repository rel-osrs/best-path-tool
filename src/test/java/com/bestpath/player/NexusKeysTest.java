package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.data.MenuTextDiagnostics;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

/**
 * The keyboard key for each list ("Nexus: Senntisten (M)"): the portal nexus's keys
 * as the game's scripts 2674 / 2675 / 2664 give them, and the portal chamber's words, on the shipped house table
 * (generated from cache 2735).
 */
public class NexusKeysTest
{
	private static final HouseFurniture TABLE = HouseFurniture.load();
	/** Slot values (cache enum 1377): 1 Varrock, 4 Camelot, 7 Senntisten, 2 Lumbridge. */
	private static final int VARROCK = 1;
	private static final int LUMBRIDGE = 2;
	private static final int CAMELOT = 4;
	private static final int SENNTISTEN = 7;
	/** Varrock achievement diary medium reward (the Grand Exchange line's unlock, script 2664). */
	private static final int VARROCK_MEDIUM_REWARD = 4520;
	/** POH_NEXUS_ID of the crystalline nexus: 41 slots listed (script 2674); any other value 4, 2 lists 8. */
	private static final int CRYSTALLINE = 3;

	private static IntUnaryOperator varbits(int... setToOne)
	{
		return id ->
		{
			for (int v : setToOne)
			{
				if (v == id)
				{
					return 1;
				}
			}
			return 0;
		};
	}

	private static final IntUnaryOperator NONE = id -> 0;

	@Test
	public void keysFollowTheSlotsAndSkipEmptyOnes()
	{
		int[] slots = new int[45];
		slots[0] = LUMBRIDGE;
		slots[2] = SENNTISTEN; // slot 2 empty: no line
		slots[3] = CAMELOT;
		Map<String, String> k = TABLE.nexusKeys(slots, CRYSTALLINE, 99, NONE, NONE);
		assertEquals(Map.of("Lumbridge", "1", "Senntisten", "2", "Camelot", "3"), k);
	}

	@Test
	public void anUnlockedAlternateTakesTheNextKey()
	{
		int[] slots = new int[45];
		slots[0] = VARROCK;
		slots[1] = SENNTISTEN;
		// the Varrock diary's reward and Magic 25: "Grand Exchange" right under "Varrock"
		Map<String, String> k = TABLE.nexusKeys(slots, CRYSTALLINE, 25, varbits(VARROCK_MEDIUM_REWARD), NONE);
		assertEquals("1", k.get("Varrock"));
		assertEquals("2", k.get("Grand Exchange"));
		assertEquals("3", k.get("Senntisten"));
		// without the diary, or below Magic 25, no Grand Exchange line
		assertEquals("2", TABLE.nexusKeys(slots, CRYSTALLINE, 99, NONE, NONE).get("Senntisten"));
		assertEquals("2", TABLE.nexusKeys(slots, CRYSTALLINE, 24, varbits(VARROCK_MEDIUM_REWARD), NONE).get("Senntisten"));
		assertNull(TABLE.nexusKeys(slots, CRYSTALLINE, 24, varbits(VARROCK_MEDIUM_REWARD), NONE).get("Grand Exchange"));
	}

	@Test
	public void afterNineComeLettersAndASmallNexusListsFewerSlots()
	{
		int[] slots = new int[45];
		for (int i = 0; i < 25; i++)
		{
			slots[i] = 10 + i; // 25 destinations without alternates (enum 1377 keys 10-34)
		}
		Map<String, String> k = TABLE.nexusKeys(slots, CRYSTALLINE, 99, NONE, NONE);
		assertEquals(25, k.size());
		assertTrue(k.containsValue("9"));
		assertTrue(k.containsValue("A"));
		assertTrue(k.containsValue("P")); // the 25th line
		// line 22 is "M" ("Senntisten (M)")
		assertEquals("M", k.values().toArray()[21]);
		// a marble nexus (any other POH_NEXUS_ID) lists 4 slots, a gilded one (2) 8
		assertEquals(4, TABLE.nexusKeys(slots, 1, 99, NONE, NONE).size());
		assertEquals(8, TABLE.nexusKeys(slots, 2, 99, NONE, NONE).size());
	}

	@Test
	public void aSlotTheTableDoesNotKnowStopsTheKeys()
	{
		int[] slots = new int[45];
		slots[0] = LUMBRIDGE;
		slots[1] = 999; // a destination newer than the table: where the lines after it stand is not known
		slots[2] = SENNTISTEN;
		assertEquals(Map.of("Lumbridge", "1"), TABLE.nexusKeys(slots, CRYSTALLINE, 99, NONE, NONE));
	}

	@Test
	public void thePortalChamberSaysItsOwnPortal()
	{
		assertEquals("Enter Senntisten Portal", TABLE.chamberMenu("Senntisten").title("->"));
		assertEquals("Enter Kourend Portal", TABLE.chamberMenu("Kourend Castle").title("->"));
		// a portal that toggles names its two places as options
		assertEquals("Varrock Portal -> Grand Exchange", TABLE.chamberMenu("Grand Exchange").title("->"));
		assertEquals("Grand Exchange Varrock Portal", MenuTextDiagnostics.menuLine(TABLE.chamberMenu("Grand Exchange")));
		assertNull(TABLE.chamberMenu("Last Boat"));
		assertNull(TABLE.chamberMenu(null));
	}

	/**
	 * The portal nexus must keep showing the key to press. The client logs showed a crystalline nexus
	 * scanned as only its first 4 slots on some house visits (POH_NEXUS_ID not read yet),
	 * which took Senntisten's "M" out of the saved keys.
	 */
	@Test
	public void aScanWithoutTheNexusIdKeepsTheFullerSavedList()
	{
		int[] slots = new int[45];
		slots[0] = VARROCK;
		slots[1] = LUMBRIDGE;
		slots[2] = CAMELOT;
		slots[3] = SENNTISTEN;
		slots[4] = 5;
		Map<String, String> full = TABLE.nexusKeys(slots, CRYSTALLINE, 99, NONE, NONE);
		assertEquals("4", full.get("Senntisten"));
		assertTrue(TABLE.knowsNexusId(CRYSTALLINE));
		assertTrue(!TABLE.knowsNexusId(0));
		slots[3] = 0;
		slots[4] = 0;
		slots[5] = SENNTISTEN; // past the default's 4 slots
		Map<String, String> early = TABLE.nexusKeys(slots, 0, 99, NONE, NONE);
		assertEquals(Map.of("Varrock", "1", "Lumbridge", "2", "Camelot", "3"), early);
		slots[3] = SENNTISTEN;
		slots[5] = 0;
		early = TABLE.nexusKeys(slots, 0, 99, NONE, NONE);
		assertTrue("the default's lines agree with the saved list: kept", HouseFurniture.keepSaved(early, false, full));
		assertTrue("the nexus id known: the scan is taken", !HouseFurniture.keepSaved(early, true, full));
		assertTrue("nothing saved yet: the scan is taken", !HouseFurniture.keepSaved(early, false, Map.of()));
		assertTrue("a line with another key (slots changed): the scan is taken",
			!HouseFurniture.keepSaved(Map.of("Varrock", "2"), false, full));
	}
}
