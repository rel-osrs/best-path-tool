package com.bestpath.tool.wiki;

import com.bestpath.core.sea.Dock;
import com.bestpath.core.sea.DockDiagnostics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives each dock its shipwright: the NPCs with "Recover-boat" (cache) at the
 * tiles their Wiki pages show ({@code data/wiki/shipwrights.json}, made by
 * {@link WikiShipwrightsCli}). A shipwright belongs to the nearest dock
 * within {@link #MAX_DISTANCE} tiles of any of its tiles, standing at its
 * Wiki tile nearest that dock (routes walk to the shipwright; Junior Jim's
 * Wiki page shows two Pandemonium tiles and one far away). Checked in cache
 * #2735: 16 such NPCs, each by one of the 16 docks with a
 * "Shipwrights' Workbench" (Shipwright Seb only repairs boats).
 */
public final class Shipwrights
{
	public static final int MAX_DISTANCE = 40;
	public static final String OPTION = "Recover-boat";

	private Shipwrights()
	{
	}

	/** The docks with their shipwrights set; {@code notes} gets anything that did not fit. Missing file: docks unchanged. */
	public static List<Dock> assign(List<Dock> docks, File json, List<String> notes) throws IOException
	{
		if (json == null || !json.isFile())
		{
			notes.add("no " + (json == null ? "shipwrights file" : json.getPath()) + ": no dock has a shipwright");
			return docks;
		}
		JsonObject doc = new JsonParser().parse(Files.readString(json.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
		Map<Integer, String> byDock = new HashMap<>();
		Map<Integer, int[]> tileByDock = new HashMap<>();
		for (JsonElement e : doc.getAsJsonArray("records"))
		{
			JsonObject r = e.getAsJsonObject();
			String name = r.get("name").getAsString();
			boolean recovers = false;
			for (JsonElement o : r.getAsJsonArray("options"))
			{
				recovers |= OPTION.equals(o.getAsString());
			}
			if (!recovers)
			{
				continue;
			}
			JsonArray tiles = r.getAsJsonArray("tiles");
			Dock best = null;
			int bestD = Integer.MAX_VALUE;
			int[] bestTile = null;
			for (JsonElement te : tiles)
			{
				JsonArray t = te.getAsJsonArray();
				int x = t.get(0).getAsInt();
				int y = t.get(1).getAsInt();
				int plane = t.size() > 2 ? t.get(2).getAsInt() : 0;
				for (Dock d : docks)
				{
					int dist = distance(d, x, y);
					if (dist < bestD)
					{
						best = d;
						bestD = dist;
						bestTile = new int[]{x, y, plane};
					}
				}
			}
			if (best == null || bestD > MAX_DISTANCE)
			{
				notes.add(name + ": no dock within " + MAX_DISTANCE + " tiles");
				continue;
			}
			String old = byDock.put(best.getId(), name);
			tileByDock.put(best.getId(), bestTile);
			if (old != null)
			{
				notes.add(best.getName() + " has two shipwrights: " + old + " and " + name);
			}
		}
		List<Dock> out = new ArrayList<>();
		for (Dock d : docks)
		{
			String s = byDock.get(d.getId());
			int[] t = tileByDock.get(d.getId());
			out.add(s == null ? d : d.withShipwright(s, t[0], t[1], t[2]));
		}
		return out;
	}

	/** Tiles (Chebyshev) from (x, y) to the dock's gangplank or mooring point. */
	static int distance(Dock d, int x, int y)
	{
		int dx = x < DockDiagnostics.landX(d) ? DockDiagnostics.landX(d) - x : Math.max(0, x - (DockDiagnostics.landX(d) + DockDiagnostics.landSize(d) - 1));
		int dy = y < DockDiagnostics.landY(d) ? DockDiagnostics.landY(d) - y : Math.max(0, y - (DockDiagnostics.landY(d) + DockDiagnostics.landSize(d) - 1));
		return Math.max(dx, dy);
	}
}
