package com.bestpath.core.data;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapDiagnostics;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.path.DoorMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes {@link PluginData} in memory for the tests and the developer tools (the plugin reads its data from the
 * exported files): no manifest, fingerprint "none".
 */
public final class PluginDataBuilder
{
	private PluginDataBuilder()
	{
	}

	/** Data made in memory, without clue steps or the sea. */
	public static PluginData of(CollisionMap map, DoorMap doors, WallBits walls, List<TransportLine> transports, Tables tables,
		FreeLand freeLand)
	{
		return of(map, doors, walls, transports, tables, freeLand, ClueData.EMPTY, null);
	}

	/** Data made in memory, with clue steps and the sea (null: none). */
	public static PluginData of(CollisionMap map, DoorMap doors, WallBits walls, List<TransportLine> transports, Tables tables,
		FreeLand freeLand, ClueData clues, SeaBits.Data sea)
	{
		Map<String, String> m = new LinkedHashMap<>();
		m.put("format", PluginData.FORMAT);
		m.put("cache", CollisionMapDiagnostics.cacheId(map) + "\t" + CollisionMapDiagnostics.cacheBuild(map));
		m.put("fingerprint", "none");
		return new PluginData(m, map, doors, walls, new ArrayList<>(transports), tables, freeLand, clues, sea, BoatTable.EMPTY,
			BankList.NONE, null, WorldMaps.NONE, WildernessZones.NONE, 0, PlaceKinds.NONE);
	}

	/** The same data with these banks. */
	public static PluginData withBanks(PluginData d, BankList list)
	{
		PluginData out = new PluginData(new LinkedHashMap<>(d.manifest), d.map, d.doors, d.walls, d.transports, d.tables, d.freeLand,
			d.clues, d.sea, d.boats, list, d.walks, d.worldMaps, d.wilderness, d.bytes, d.places);
		out.slayer = d.slayer;
		out.charting = d.charting;
		return out;
	}

	/** The same data with these Wilderness levels. */
	public static PluginData withWilderness(PluginData d, WildernessZones levels)
	{
		PluginData out = new PluginData(new LinkedHashMap<>(d.manifest), d.map, d.doors, d.walls, d.transports, d.tables, d.freeLand,
			d.clues, d.sea, d.boats, d.banks, d.walks, d.worldMaps, levels, d.bytes, d.places);
		out.slayer = d.slayer;
		out.charting = d.charting;
		return out;
	}
}
