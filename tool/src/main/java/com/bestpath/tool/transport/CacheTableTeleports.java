package com.bestpath.tool.transport;

import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Destinations stated directly in cache DB tables. Table
 * ids and column numbers come from RuneLite's public gameval names
 * (DBTableID); the cache itself stores only numbers. Whether each coordinate
 * is the exact arrival tile is not stated, so these are only suggestions:
 * a person confirms each one.
 */
public final class CacheTableTeleports
{
	/** One suggested teleport destination. Immutable. */
	public static final class Destination
	{
		/** The network or teleport group, e.g. "Fairy ring", "Pendant of Ates". */
		public final String network;
		public final String name;
		public final int x;
		public final int y;
		public final int plane;
		/**
		 * True for a network (usable only at one of its stops, to any other stop);
		 * false for a teleport usable from anywhere (with its item or spell).
		 */
		public final boolean isNetwork;
		/** Where it came from, e.g. "cache table 89". */
		public final String source;

		public Destination(String network, String name, int x, int y, int plane, boolean isNetwork, String source)
		{
			this.network = network;
			this.name = name;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.isNetwork = isNetwork;
			this.source = source;
		}

		/** "Network: name", or just the name when they are the same. */
		public String label()
		{
			return name.equals(network) ? name : network + ": " + name;
		}

		/** Stable id (by network and name), used for "not a transport" and the test list. */
		public String id()
		{
			return "teleport:" + network + ":" + name;
		}
	}

	/** {table id, name column, coordinate column, label, network?} per RuneLite gameval names. */
	private static final Object[][] TABLES = {
		{89, 3, 2, "Fairy ring", true}, // Fairyring: CODE, DEST_COORD
		{64, 1, 2, "Quetzal", true}, // Quetzal: NAME, COORD
		{72, 1, 2, "Pendant of Ates", false}, // PendantOfAtesTeleports: TELEPORT_NAME, TELEPORT_COORD
		{206, 1, 3, "Charter ship", true}, // CharteringDestinations: NAME, PORT_COORD
	};

	private CacheTableTeleports()
	{
	}

	public static List<Destination> read(LoadedCache cache) throws IOException
	{
		List<Destination> out = new ArrayList<>();
		for (Object[] t : TABLES)
		{
			int table = (Integer) t[0];
			int nameCol = (Integer) t[1];
			int coordCol = (Integer) t[2];
			String label = (String) t[3];
			boolean network = (Boolean) t[4];
			for (Object[][] row : cache.dbRows(table))
			{
				Object[] coord = coordCol < row.length ? row[coordCol] : null;
				Object[] name = nameCol < row.length ? row[nameCol] : null;
				if (coord == null || coord.length == 0 || !(coord[0] instanceof Integer))
				{
					continue;
				}
				int packed = (Integer) coord[0];
				int x = (packed >> 14) & 0x3FFF;
				int y = packed & 0x3FFF;
				int plane = (packed >>> 28) & 3;
				if (x < 1000 || y < 1000)
				{
					continue; // placeholder rows (e.g. a fairy ring with coordinate 65,65)
				}
				String n = name != null && name.length > 0 && name[0] != null ? name[0].toString() : "?";
				out.add(new Destination(label, n, x, y, plane, network, "cache table " + table));
			}
		}
		return out;
	}
}
