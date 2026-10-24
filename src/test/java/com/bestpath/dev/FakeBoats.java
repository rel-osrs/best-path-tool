package com.bestpath.dev;

import com.bestpath.core.data.BoatTable;
import com.bestpath.player.BoatReading;
import com.bestpath.player.BoatVars;
import com.bestpath.player.PlayerBoats;
import com.bestpath.player.SnapshotMapper;
import java.util.Arrays;

/**
 * DEV ONLY: made-up boat varbit values for the UI preview, turned into boats the plugin's way
 * ({@link SnapshotMapper#boats}) with a boat table: the real export's ({@code -PrealData}) or a copy of its first
 * lines for cache #2735 (word lists and docks shortened).
 */
final class FakeBoats
{
	/** Like the export's boats.tsv for cache #2735, shortened. */
	static final BoatTable SAMPLE_TABLE = BoatTable.parse(Arrays.asList(
		"type\t0\tRAFT\tRaft\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t-\t0,1,2,3,4,5,6\t1\t-",
		"type\t1\tSKIFF\tSkiff\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t1\t5:1",
		"type\t2\tSLOOP\tSloop\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t0,1,2,3,4,5,6\t1\t9:1",
		"port\t253\tLost",
		"port\t254\tCapsized",
		"port\t255\tBottled",
		"name\t1\t",
		"name\t2\t\tAdamant\tAlmighty\tJolly\tMerry",
		"name\t3\tBoat\tAegis\tAeon\tRaven\tWave",
		"dock\t0\tPort Sarim",
		"dock\t23\tRellekka"));

	private FakeBoats()
	{
	}

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

	/**
	 * Three boats: a sloop "Jolly Raven" at Port Sarim (teak hull, oak sails, adamant keel and helm, a teleport
	 * focus), a skiff "Merry Wave" at Rellekka (wooden, with an Inoculation station) and a bottled raft.
	 */
	static PlayerBoats sample(BoatTable table)
	{
		BoatReading r = new BoatReading();
		boat(r, 1, 2, 0, 2, 1, 4, 4, 3, 3);
		r.slots[0][BoatVars.FOCUS] = 1;
		boat(r, 2, 1, 23, 0, 0, 0, 0, 4, 4);
		r.slots[1][BoatVars.HOTSPOT_0 + 5] = 1;
		boat(r, 4, 0, 255, 0, 0, 0, 0, 0, 0);
		return SnapshotMapper.boats(r, table);
	}

	/** The preview's test sloop (RealData.TEST_BOATS) as varbit values: teak hull, oak sails, moored at Port Sarim. */
	static PlayerBoats testSloop(BoatTable table)
	{
		return testSloop(table, 0);
	}

	/** The test sloop with another {@code _PORT} value (a dock id, or 254 capsized, 253 lost, 255 bottled). */
	static PlayerBoats testSloop(BoatTable table, int port)
	{
		BoatReading r = new BoatReading();
		boat(r, 1, 2, port, 2, 1, 0, 0, 3, 3);
		return SnapshotMapper.boats(r, table);
	}
}
