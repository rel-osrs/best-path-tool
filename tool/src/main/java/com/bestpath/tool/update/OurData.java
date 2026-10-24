package com.bestpath.tool.update;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.extract.MapPlaces;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The hand-made data the game update comparison checks against the two caches: the transports in use, the manual
 * tile and wall changes, the doors set shut, the hidden and kept areas, the rejected transport objects, the
 * ignored and reviewed regions and the named map icon kinds. Tool only.
 */
public final class OurData
{
	public final List<TransportRecord> transports = new ArrayList<>();
	/** The manual Tile and Wall changes in effect (the last one per tile / wall side). */
	public final List<Edit> tileEdits = new ArrayList<>();
	/** The doors set shut (the last Door change per door is "shut"). */
	public final List<Edit> shutDoors = new ArrayList<>();
	/** Hidden ({@code true}) and kept ({@code false}) areas: their anchor tiles. */
	public final Map<int[], Boolean> anchors = new LinkedHashMap<>();
	public final Set<String> rejected = new TreeSet<>();
	public final Set<Integer> ignoredRegions = new TreeSet<>();
	/** Regions with any area marked in the region review. */
	public final Set<Integer> reviewedRegions = new TreeSet<>();
	/** Map icon kinds offered to players: named in map-icon-kinds.tsv and not removed by hand, with the name. */
	public final Map<Integer, String> placeKinds = new TreeMap<>();

	/** Reads the data from the workspace and the changes in effect ({@code manual} built from {@code active}). */
	public static OurData load(File ws, List<Edit> active, ManualState manual) throws IOException
	{
		OurData d = new OurData();
		d.transports.addAll(manual.getTransports().values());
		Map<String, Edit> tiles = new LinkedHashMap<>();
		Map<String, Edit> doors = new LinkedHashMap<>();
		for (Edit e : active)
		{
			if (e.type == Edit.Type.TILE)
			{
				tiles.put("t" + e.x + "," + e.y + "," + e.plane, e);
			}
			else if (e.type == Edit.Type.WALL)
			{
				tiles.put("w" + e.x + "," + e.y + "," + e.plane + "," + e.side, e);
			}
			else if (e.type == Edit.Type.DOOR)
			{
				doors.put(e.x + "," + e.y + "," + e.plane + "," + e.objectId, e);
			}
		}
		d.tileEdits.addAll(tiles.values());
		for (Edit e : doors.values())
		{
			if (e.on)
			{
				d.shutDoors.add(e);
			}
		}
		for (int[] a : manual.getHiddenAnchors())
		{
			d.anchors.put(a, true);
		}
		for (int[] a : manual.getKeptAnchors())
		{
			d.anchors.put(a, false);
		}
		d.rejected.addAll(manual.getRejected());
		d.ignoredRegions.addAll(manual.getIgnoredRegions());
		d.reviewedRegions.addAll(reviewed(new File(ws, "data/manual/region-review.json")));
		File names = new File(ws, MapPlaces.NAMES);
		if (names.isFile())
		{
			Map<Integer, String> named = MapPlaces.readNames(Files.readAllLines(names.toPath(), StandardCharsets.UTF_8));
			ManualState.PlaceKindAnswers answers = ManualState.placeKinds(active);
			named.forEach((id, name) ->
			{
				if (!answers.removed.contains(id))
				{
					d.placeKinds.put(id, answers.names.getOrDefault(id, name));
				}
			});
		}
		return d;
	}

	/** The region ids in region-review.json (each has at least one area marked). */
	static Set<Integer> reviewed(File f) throws IOException
	{
		Set<Integer> out = new LinkedHashSet<>();
		if (!f.isFile())
		{
			return out;
		}
		try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8))
		{
			JsonObject o = new Gson().fromJson(r, JsonObject.class);
			for (Map.Entry<String, JsonElement> e : o.entrySet())
			{
				try
				{
					out.add(Integer.parseInt(e.getKey()));
				}
				catch (NumberFormatException ignored)
				{
					// not a region
				}
			}
		}
		return out;
	}

	/** Every tile the data uses, by {@link CacheFacts#tile}: transport ends, changed tiles and walls, shut doors, areas. */
	public Collection<Long> tiles()
	{
		Set<Long> out = new TreeSet<>();
		for (TransportRecord t : transports)
		{
			if (t.hasStart())
			{
				out.add(CacheFacts.tile(t.startX, t.startY, t.startPlane));
			}
			out.add(CacheFacts.tile(t.endX, t.endY, t.endPlane));
		}
		for (Edit e : tileEdits)
		{
			out.add(CacheFacts.tile(e.x, e.y, e.plane));
			if (e.type == Edit.Type.WALL)
			{
				int[] n = wallNeighbour(e);
				out.add(CacheFacts.tile(n[0], n[1], e.plane));
			}
		}
		for (Edit e : shutDoors)
		{
			out.add(CacheFacts.tile(e.x, e.y, e.plane));
		}
		for (int[] a : anchors.keySet())
		{
			out.add(CacheFacts.tile(a[0], a[1], a[2]));
		}
		return out;
	}

	/** The tile on the other side of a Wall change's wall (side 0 north, 1 east, 2 south, 3 west). */
	static int[] wallNeighbour(Edit e)
	{
		int[] dx = {0, 1, 0, -1};
		int[] dy = {1, 0, -1, 0};
		return new int[]{e.x + dx[e.side], e.y + dy[e.side]};
	}
}
