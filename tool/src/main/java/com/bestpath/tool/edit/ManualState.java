package com.bestpath.tool.edit;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.tool.extract.CollisionExtractor;
import com.bestpath.tool.extract.FlagGrid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The combined effect of the applied changes (oldest first, later wins):
 * walls, tiles and doors are written into the flag grid; hidden and kept
 * sub-regions, transports, rejected candidates, ignored regions, the test
 * list and deleted sea channels are collected. Immutable after construction
 * (the flag grid passed in is modified).
 */
public final class ManualState
{
	private final List<int[]> hiddenAnchors = new ArrayList<>();
	private final List<int[]> keptAnchors = new ArrayList<>();
	private final Map<String, TransportRecord> transports = new LinkedHashMap<>();
	private final Set<String> rejected = new HashSet<>();
	/** Deleted sea channels, by their ends. */
	private final Set<String> deletedChannels = new java.util.TreeSet<>();
	private final Set<Long> shutDoors = new HashSet<>();
	private final Set<Integer> ignoredRegions = new HashSet<>();
	private final Map<String, TransportRecord> testLater = new LinkedHashMap<>();
	/** Tiles made walkable by hand (the last Tile change on them is off), as {@link #tileKey}s. */
	private final Set<Long> opened = new HashSet<>();

	/** Applies {@code edits} (applied and in effect, oldest first) to {@code grid}. */
	public ManualState(Collection<Edit> edits, FlagGrid grid)
	{
		Map<Long, Boolean> hide = new LinkedHashMap<>();
		Map<Long, Boolean> keep = new LinkedHashMap<>();
		Map<Long, Boolean> doors = new LinkedHashMap<>();
		Map<Long, Boolean> tiles = new LinkedHashMap<>();
		for (Edit e : edits)
		{
			switch (e.type)
			{
				case WALL:
					applyWall(grid, e);
					break;
				case TILE:
					applyTile(grid, e, tiles);
					break;
				case DOOR:
					doors.put(doorKey(e.x, e.y, e.plane, e.objectId), e.on);
					break;
				case HIDE:
					hide.put(tileKey(e.x, e.y, e.plane), e.on);
					break;
				case KEEP:
					keep.put(tileKey(e.x, e.y, e.plane), e.on);
					break;
				case TRANSPORT:
					transports.put(e.transport.id, e.transport.copy());
					break;
				case TRANSPORT_DELETE:
					transports.remove(e.target);
					break;
				case REJECT:
					toggle(rejected, e.target, e.on);
					break;
				case IGNORE_REGION:
					toggle(ignoredRegions, e.regionId(), e.on);
					break;
				case TEST_LATER:
					applyTestLater(e);
					break;
				case SEA_CHANNEL:
					toggle(deletedChannels, e.target, e.on);
					break;
				default:
					break;
			}
		}
		closeShutDoors(grid, doors);
		tiles.forEach((k, on) ->
		{
			if (!on)
			{
				opened.add(k);
			}
		});
		addAnchors(hide, hiddenAnchors);
		addAnchors(keep, keptAnchors);
	}

	/** A Tile change: blocks the tile (on) or makes it walkable (off), and remembers the last change per tile. */
	private static void applyTile(FlagGrid grid, Edit e, Map<Long, Boolean> tiles)
	{
		tiles.put(tileKey(e.x, e.y, e.plane), e.on);
		if (e.on)
		{
			grid.add(e.x, e.y, e.plane, CollisionFlags.OBJECT);
		}
		else
		{
			grid.clear(e.x, e.y, e.plane, CollisionFlags.BLOCKED);
		}
	}

	private void applyTestLater(Edit e)
	{
		if (e.on)
		{
			testLater.put(e.target, e.transport == null ? null : e.transport.copy());
		}
		else
		{
			testLater.remove(e.target);
		}
	}

	/** Adds {@code value} to {@code set} when {@code on}, else removes it. */
	private static <T> void toggle(Set<T> set, T value, boolean on)
	{
		if (on)
		{
			set.add(value);
		}
		else
		{
			set.remove(value);
		}
	}

	/** Closes every door whose last Door change shut it: adds its walls to the grid. */
	private void closeShutDoors(FlagGrid grid, Map<Long, Boolean> doors)
	{
		for (int[] door : grid.getDoors())
		{
			long k = doorKey(door[0], door[1], door[2], door[3]);
			if (Boolean.TRUE.equals(doors.get(k)))
			{
				CollisionExtractor.addDoorWalls(grid, door);
				shutDoors.add(k);
			}
		}
	}

	/** Adds the tile of every key whose last change was "on" to {@code anchors}. */
	private static void addAnchors(Map<Long, Boolean> lastChange, List<int[]> anchors)
	{
		lastChange.forEach((k, on) ->
		{
			if (on)
			{
				anchors.add(tile(k));
			}
		});
	}

	private static int[] tile(long key)
	{
		return new int[]{(int) (key >> 22), (int) ((key >> 2) & 0xFFFFF), (int) (key & 3)};
	}

	static void applyWall(FlagGrid grid, Edit e)
	{
		int[] own = {CollisionFlags.NORTH, CollisionFlags.EAST, CollisionFlags.SOUTH, CollisionFlags.WEST};
		int[] opposite = {CollisionFlags.SOUTH, CollisionFlags.WEST, CollisionFlags.NORTH, CollisionFlags.EAST};
		int[] dx = {0, 1, 0, -1};
		int[] dy = {1, 0, -1, 0};
		int nx = e.x + dx[e.side];
		int ny = e.y + dy[e.side];
		if (e.on)
		{
			grid.add(e.x, e.y, e.plane, own[e.side]);
			grid.add(nx, ny, e.plane, opposite[e.side]);
		}
		else
		{
			grid.clear(e.x, e.y, e.plane, own[e.side]);
			grid.clear(nx, ny, e.plane, opposite[e.side]);
		}
	}

	static long tileKey(int x, int y, int plane)
	{
		return (long) x << 22 | (long) y << 2 | plane;
	}

	static long doorKey(int x, int y, int plane, int objectId)
	{
		return ((long) objectId << 40) ^ tileKey(x, y, plane);
	}

	/**
	 * Whether this tile was made walkable by hand (the last Tile change on it).
	 * Such a tile stays open even where the sea layer would close it (e.g. the
	 * beach path by the wrecked ghost ship).
	 */
	public boolean isOpenedByOwner(int x, int y, int plane)
	{
		return opened.contains(tileKey(x, y, plane));
	}

	/** Tiles inside hidden sub-regions: {x, y, plane}. */
	public List<int[]> getHiddenAnchors()
	{
		return Collections.unmodifiableList(hiddenAnchors);
	}

	/** Tiles inside closed-off pockets kept in the data: {x, y, plane}. */
	public List<int[]> getKeptAnchors()
	{
		return Collections.unmodifiableList(keptAnchors);
	}

	/** The deleted sea channels ({@code SeaChannels.key}: their ends). */
	public Set<String> getDeletedChannels()
	{
		return deletedChannels;
	}

	/** The sea channels deleted by these changes (a fold of {@link Edit.Type#SEA_CHANNEL}), without building anything. */
	public static Set<String> deletedChannels(List<Edit> edits)
	{
		Set<String> out = new java.util.TreeSet<>();
		for (Edit e : edits)
		{
			if (e.type == Edit.Type.SEA_CHANNEL)
			{
				if (e.on)
				{
					out.add(e.target);
				}
				else
				{
					out.remove(e.target);
				}
			}
		}
		return out;
	}

	/**
	 * The answers on the "go to nearest" place kind cards, a fold of {@link Edit.Type#PLACE_KIND}
	 * by map icon id: the last answer decides keep or remove; the last name given stays.
	 */
	public static final class PlaceKindAnswers
	{
		/** Icon ids with a Keep or a Remove (their cards are answered). */
		public final Set<Integer> decided = new java.util.TreeSet<>();
		/** Icon ids removed: left out of the plugin export. */
		public final Set<Integer> removed = new java.util.TreeSet<>();
		/** New names by icon id. */
		public final Map<Integer, String> names = new java.util.TreeMap<>();
	}

	/**
	 * The Boat / Land answers on the sea charting cards, a fold of {@link Edit.Type#CHARTING_SPOT}:
	 * task number -> boat (the last answer counts). Use the active changes: an undone answer counts as none.
	 */
	public static Map<Integer, Boolean> chartingSpots(List<Edit> edits)
	{
		Map<Integer, Boolean> out = new java.util.TreeMap<>();
		for (Edit e : edits)
		{
			if (e.type != Edit.Type.CHARTING_SPOT || e.target == null)
			{
				continue;
			}
			try
			{
				out.put(Integer.parseInt(e.target.trim()), e.on);
			}
			catch (NumberFormatException ex)
			{
				// not a task number: left out
			}
		}
		return out;
	}

	/** The place kind answers in these changes (use the active changes: an undone answer counts as none). */
	public static PlaceKindAnswers placeKinds(List<Edit> edits)
	{
		PlaceKindAnswers out = new PlaceKindAnswers();
		for (Edit e : edits)
		{
			if (e.type != Edit.Type.PLACE_KIND || e.target == null)
			{
				continue;
			}
			int icon;
			try
			{
				icon = Integer.parseInt(e.target.trim());
			}
			catch (NumberFormatException ex)
			{
				continue;
			}
			out.decided.add(icon);
			if (e.on)
			{
				out.removed.add(icon);
			}
			else
			{
				out.removed.remove(icon);
			}
			if (e.name != null && !e.name.isBlank())
			{
				out.names.put(icon, e.name.trim());
			}
		}
		return out;
	}

	/** The game update cards answered: their finding keys. */
	public static Set<String> cacheChecks(List<Edit> edits)
	{
		Set<String> out = new java.util.TreeSet<>();
		for (Edit e : edits)
		{
			if (e.type == Edit.Type.CACHE_CHECK)
			{
				out.add(e.target);
			}
		}
		return out;
	}

	/** The sea channels with a Keep or a Delete in these changes (their cards are answered). */
	public static Set<String> decidedChannels(List<Edit> edits)
	{
		Set<String> out = new java.util.TreeSet<>();
		for (Edit e : edits)
		{
			if (e.type == Edit.Type.SEA_CHANNEL)
			{
				out.add(e.target);
			}
		}
		return out;
	}

	/** Transports created or confirmed, by id. */
	public Map<String, TransportRecord> getTransports()
	{
		return Collections.unmodifiableMap(transports);
	}

	/** Candidate ids marked "not a transport". */
	public Set<String> getRejected()
	{
		return Collections.unmodifiableSet(rejected);
	}

	/** 64x64 regions left out of the export entirely (all planes). */
	public Set<Integer> getIgnoredRegions()
	{
		return Collections.unmodifiableSet(ignoredRegions);
	}

	/** The "test in game" list: candidate or transport id -> the transport to test (may be null). */
	public Map<String, TransportRecord> getTestLater()
	{
		return Collections.unmodifiableMap(testLater);
	}

	public boolean isDoorShut(int x, int y, int plane, int objectId)
	{
		return shutDoors.contains(doorKey(x, y, plane, objectId));
	}
}
