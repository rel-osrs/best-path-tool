package com.bestpath.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.data.BoatTable;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatDiagnostics;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.SeaArea;
import com.bestpath.route.PlayerSnapshot;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/**
 * The player's boats from made-up boat varbit values (no client), with a boat table shaped like the export's for
 * cache #2735 (word lists and docks shortened).
 */
public class BoatReadingTest
{
	static final BoatTable TABLE = BoatTable.parse(Arrays.asList(
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
		"dock\t23\tRellekka"));

	/** A slot's varbit values: kind, dock, hull, sails, keel, helm (values = tiers here), name words 2 and 3. */
	private static void boat(BoatReading r, int slot, int type, int port, int hull, int sail, int keel, int helm, int name2, int name3)
	{
		int[] v = r.slots[slot - 1];
		v[BoatVars.OWNED] = 1;
		v[BoatVars.TYPE] = type;
		v[BoatVars.PORT] = port;
		v[BoatVars.HULL] = hull;
		v[BoatVars.SAIL] = sail;
		v[BoatVars.KEEL] = keel;
		v[BoatVars.STEERING] = helm;
		v[BoatVars.NAME_2] = name2;
		v[BoatVars.NAME_3] = name3;
	}

	private static Boat slot(PlayerBoats b, int slot)
	{
		for (Boat x : b.getBoats())
		{
			if (x.getSlot() == slot)
			{
				return x;
			}
		}
		return null;
	}

	private static BoatInfo row(PlayerBoats b, int slot)
	{
		for (BoatInfo x : b.getRows())
		{
			if (x.getSlot() == slot)
			{
				return x;
			}
		}
		return null;
	}

	@Test
	public void sloopAtPortSarimWithItsParts()
	{
		BoatReading r = new BoatReading();
		boat(r, 1, 2, 0, 2, 1, 4, 4, 3, 3); // teak hull, oak sails, adamant keel and helm; "Jolly Raven"
		PlayerBoats b = SnapshotMapper.boats(r, TABLE);
		assertSame(PlayerBoats.Status.READ, b.getStatus());
		Boat s = slot(b, 1);
		assertNotNull(s);
		assertSame(BoatType.SLOOP, s.getType());
		assertEquals(2.0, s.speed(), 1e-9);
		assertEquals(0, s.getDockId());
		assertTrue(s.canEnter(SeaArea.TEMPOR_TANTRUM));
		assertTrue(s.canEnter(SeaArea.CRYSTALLINE));
		assertTrue(s.canEnter(SeaArea.KELP));
		assertFalse(s.canEnter(SeaArea.FETID));
		assertFalse(s.canEnter(SeaArea.NORTHERN));
		assertSame(Boat.Focus.NONE, s.getFocus());
		BoatInfo row = row(b, 1);
		assertEquals("Sloop: Jolly Raven", row.getTitle());
		assertEquals("At Port Sarim", row.getWhere());
		assertTrue(row.isUsable());
		assertNull(b.getAboardNote());
	}

	@Test
	public void facilitiesBrazierAndTeleportFocus()
	{
		BoatReading r = new BoatReading();
		boat(r, 2, 1, 23, 0, 0, 0, 0, 0, 0);
		r.slots[1][BoatVars.HOTSPOT_0 + 5] = 1; // the skiff's hotspot 5, option 1: an Inoculation station
		r.slots[1][BoatVars.BRAZIER] = 1;
		r.slots[1][BoatVars.FOCUS] = 1;
		Boat s = slot(SnapshotMapper.boats(r, TABLE), 2);
		assertTrue(BoatDiagnostics.hasInoculation(s));
		assertTrue(s.canEnter(SeaArea.FETID));
		assertTrue(BoatDiagnostics.hasBrazier(s));
		assertTrue(s.canEnter(SeaArea.NORTHERN));
		assertSame(Boat.Focus.TELEPORT, s.getFocus());
		assertEquals("At Rellekka", row(SnapshotMapper.boats(r, TABLE), 2).getWhere());

		r.slots[1][BoatVars.HOTSPOT_0 + 5] = 0;
		r.slots[1][BoatVars.HOTSPOT_0 + 4] = 1; // another hotspot: not a fetid-water facility
		r.slots[1][BoatVars.BRAZIER] = 2; // not a brazier option: none
		r.slots[1][BoatVars.FOCUS] = 2;
		s = slot(SnapshotMapper.boats(r, TABLE), 2);
		assertFalse(BoatDiagnostics.hasInoculation(s));
		assertFalse(BoatDiagnostics.hasBrazier(s));
		assertSame(Boat.Focus.GREATER, s.getFocus());
	}

	@Test
	public void focusValuesAsTheGamesScriptReadsThem()
	{
		assertSame(Boat.Focus.NONE, SnapshotMapper.focus(0));
		assertSame(Boat.Focus.TELEPORT, SnapshotMapper.focus(1));
		assertSame(Boat.Focus.GREATER, SnapshotMapper.focus(2));
		assertSame(Boat.Focus.GREATER, SnapshotMapper.focus(3)); // script 8997: "below 2" = no greater focus
	}

	@Test
	public void emptySlotsAreSkippedAndRaftsHaveNoKeel()
	{
		BoatReading r = new BoatReading();
		boat(r, 3, 0, 0, 1, 0, 9, 0, 0, 0); // a raft: its keel value means nothing
		r.slots[0][BoatVars.TYPE] = 2; // not owned: skipped whatever else it says
		PlayerBoats b = SnapshotMapper.boats(r, TABLE);
		assertEquals(1, b.getRows().size());
		assertEquals(1, b.getBoats().size());
		Boat raft = slot(b, 3);
		assertSame(BoatType.RAFT, raft.getType());
		assertEquals(-1, raft.getKeel());
		assertEquals("Raft: Boat", row(b, 3).getTitle());
	}

	@Test
	public void capsizedLostAndBottledBoatsAreReadForRoutes()
	{
		// capsized and lost boats through a shipwright, bottled ones with their bottle carried; core
		// decides when (the place says which)
		BoatReading r = new BoatReading();
		boat(r, 1, 2, 255, 2, 1, 0, 0, 1, 1); // bottled
		boat(r, 2, 2, 254, 2, 1, 0, 0, 1, 1); // capsized
		boat(r, 3, 1, 253, 0, 0, 0, 0, 0, 0); // lost
		r.slots[1][BoatVars.FOCUS] = 1;
		PlayerBoats b = SnapshotMapper.boats(r, TABLE);
		assertEquals(3, b.getBoats().size());
		assertEquals(Boat.BOTTLED, slot(b, 1).getDockId());
		assertEquals(Boat.CAPSIZED, slot(b, 2).getDockId());
		assertEquals(Boat.LOST, slot(b, 3).getDockId());
		assertSame(Boat.Focus.TELEPORT, slot(b, 2).getFocus()); // kept; core doesn't use a focus for a capsized boat
		assertEquals("Bottled", row(b, 1).getWhere());
		assertEquals("Capsized", row(b, 2).getWhere());
		assertEquals("Lost", row(b, 3).getWhere());
		for (BoatInfo i : b.getRows())
		{
			assertTrue(i.toString(), i.isUsable());
		}
		assertTrue(row(b, 1).getDetails(), row(b, 1).getDetails().startsWith("Bottled: routes use it only while its full boat bottle"));
		assertTrue(row(b, 2).getDetails(), row(b, 2).getDetails().startsWith("Capsized: routes use it only when a shipwright"));
		assertTrue(row(b, 3).getDetails(), row(b, 3).getDetails().startsWith("Lost: "));
		assertTrue(row(b, 2).getDetails().contains("Sloop, Teak hull"));
		// a status label this plugin version doesn't know: listed, not used
		assertEquals(Boat.NOWHERE, SnapshotMapper.statusPlace("Sunk"));
		assertEquals(Boat.BOTTLED, SnapshotMapper.statusPlace("Bottled"));
	}

	@Test
	public void theBoatBottleIsRuneLitesItem()
	{
		// core's bottle id (from cache #2735) is the item RuneLite names for the full boat bottle
		assertEquals(net.runelite.api.gameval.ItemID.SAILING_BOAT_BOTTLE_FULL, Boat.BOTTLE_FULL);
	}

	@Test
	public void boatsRoutesCannotUseAreListedButNotUsed()
	{
		BoatReading r = new BoatReading();
		boat(r, 2, 7, 0, 0, 0, 0, 0, 0, 0); // a kind the table doesn't have
		boat(r, 3, 3, 0, 0, 0, 0, 0, 0, 0); // the temporary quest boat
		boat(r, 4, 1, 0, 9, 0, 0, 0, 0, 0); // a hull value the table doesn't know
		boat(r, 5, 1, 200, 0, 0, 0, 0, 0, 0); // a place that is neither a dock nor a status
		PlayerBoats b = SnapshotMapper.boats(r, TABLE);
		assertTrue(b.getBoats().isEmpty());
		assertEquals(4, b.getRows().size());
		for (BoatInfo i : b.getRows())
		{
			assertFalse(i.toString(), i.isUsable());
		}
		assertNull(row(b, 2).getType());
		assertEquals("Will and Anne's boat", row(b, 3).getTitle());
		assertTrue(row(b, 4).getDetails().contains("parts"));
		assertEquals("At an unknown place (200)", row(b, 5).getWhere());
	}

	@Test
	public void onTheirOwnBoatAtSeaTheRouteStartsFromIt()
	{
		BoatReading r = new BoatReading();
		boat(r, 1, 2, 0, 2, 1, 0, 0, 3, 3); // Sloop "Jolly Raven"
		boat(r, 2, 1, 255, 2, 1, 0, 0, 3, 3); // Skiff "Jolly Raven": PORT says bottled, but the player is on it
		r.aboard = true;
		r.ownBoat = true;
		r.boardedType = 1;
		r.boardedName[1] = 3;
		r.boardedName[2] = 3;
		PlayerBoats b = SnapshotMapper.boats(r, TABLE);
		assertNull(b.getAboardNote());
		assertEquals(Boat.AT_START, slot(b, 2).getDockId());
		assertEquals(0, slot(b, 1).getDockId());
		assertEquals("At sea, you are on it", row(b, 2).getWhere());
		assertTrue(row(b, 2).isUsable());
	}

	@Test
	public void onABoatThatIsNotKnownToBeTheirsRoutesDoNotSail()
	{
		BoatReading r = new BoatReading();
		boat(r, 1, 2, 0, 2, 1, 0, 0, 3, 3);
		r.aboard = true;
		r.ownBoat = false; // someone else's boat
		r.boardedType = 2;
		r.boardedName[1] = 3;
		r.boardedName[2] = 3;
		PlayerBoats b = SnapshotMapper.boats(r, TABLE);
		assertTrue(b.getBoats().isEmpty());
		assertNotNull(b.getAboardNote());
		assertEquals("At Port Sarim", row(b, 1).getWhere());

		r.ownBoat = true;
		boat(r, 4, 2, 23, 0, 0, 0, 0, 3, 3); // a second sloop with the same name: which one is it?
		b = SnapshotMapper.boats(r, TABLE);
		assertTrue(b.getBoats().isEmpty());
		assertTrue(b.getAboardNote().contains("Two"));

		r.boardedName[2] = 1; // the name matches none of the player's boats
		b = SnapshotMapper.boats(r, TABLE);
		assertTrue(b.getBoats().isEmpty());
		assertNotNull(b.getAboardNote());
	}

	@Test
	public void withoutTheBoatTableNothingIsRead()
	{
		BoatReading r = new BoatReading();
		boat(r, 1, 2, 0, 2, 1, 0, 0, 3, 3);
		assertSame(PlayerBoats.NO_DATA, SnapshotMapper.boats(r, BoatTable.EMPTY));
		assertSame(PlayerBoats.NO_DATA, SnapshotMapper.boats(r, null));
	}

	@Test
	public void theSnapshotCarriesTheBoats()
	{
		BoatReading br = new BoatReading();
		boat(br, 1, 2, 0, 2, 1, 0, 0, 3, 3);
		GameReading g = new GameReading();
		g.x = 3050;
		g.y = 3190;
		g.levels[Skill.SAILING.ordinal()] = 55;
		g.boats = SnapshotMapper.boats(br, TABLE).getBoats();
		PlayerSnapshot s = SnapshotMapper.toSnapshot(g);
		assertEquals(1, s.getBoats().size());
		assertEquals(0, s.getBoats().get(0).getDockId());
		assertEquals(55, s.getState().level(Skill.SAILING));
	}

	@Test
	public void theWatchedVarbitsAreTheBoatOnes()
	{
		// 5 slots x 25 fields, the boarded boat's kind and name, and SAILING_BOARDED_BOAT
		assertEquals(5 * BoatVars.FIELDS + 5, BoatVars.ALL.size());
		Set<Integer> seen = new HashSet<>();
		for (int s = 1; s <= BoatVars.SLOTS; s++)
		{
			for (int f = 0; f < BoatVars.FIELDS; f++)
			{
				assertTrue("varbit ids differ", seen.add(BoatVars.id(s, f)));
			}
		}
		assertEquals(VarbitID.SAILING_BOAT_3_PORT, BoatVars.id(3, BoatVars.PORT));
		assertEquals(VarbitID.SAILING_BOAT_5_HOTSPOT_12, BoatVars.id(5, BoatVars.HOTSPOT_0 + 12));
		assertTrue(BoatVars.isBoatVarbit(VarbitID.SAILING_BOAT_2_HULL));
		assertTrue(BoatVars.isBoatVarbit(VarbitID.SAILING_BOARDED_BOAT_TYPE));
		assertTrue(BoatVars.isBoatVarbit(VarbitID.SAILING_BOARDED_BOAT));
		assertFalse(BoatVars.isBoatVarbit(VarbitID.SPELLBOOK));
		assertFalse(BoatVars.isBoatVarbit(VarbitID.SAILING_BOAT_1_SAIL_PATTERN)); // looks only: not watched
		assertFalse(BoatVars.isBoatVarbit(-1));
	}

	/** The Settings choices are applied by the route engine; every boat read is offered to it. */
	@Test
	public void everyUsableBoatIsOffered()
	{
		BoatReading r = new BoatReading();
		boat(r, 1, 2, 0, 2, 1, 0, 0, 3, 3);
		boat(r, 2, 0, 23, 0, 0, 0, 0, 1, 1);
		List<Boat> boats = SnapshotMapper.boats(r, TABLE).getBoats();
		assertEquals(2, boats.size());
		assertEquals(1, boats.get(0).getSlot());
		assertEquals(2, boats.get(1).getSlot());
	}

	/**
	 * The docks' quests (cache #2735 table 194, as the export names them) are quests RuneLite knows, so the plugin
	 * reads their real state (docks check their quests). A name RuneLite did not know would count
	 * as not started and its docks would never be used.
	 */
	@Test
	public void dockQuestsAreQuestsRuneLiteKnows()
	{
		for (String q : new String[]{"Children of the Sun", "The Corsair Curse", "The Fremennik Trials", "Royal Trouble", "Regicide",
			"The Fremennik Isles", "Song of the Elves", "Swan Song", "Lunar Diplomacy", "Making Friends with My Arm"})
		{
			assertNotNull(q, QuestNames.find(q));
		}
	}
}
