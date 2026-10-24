package com.bestpath.tool.extract;

import static com.bestpath.core.collision.CollisionFlags.EAST;
import static com.bestpath.core.collision.CollisionFlags.FLOOR;
import static com.bestpath.core.collision.CollisionFlags.FLOOR_DECORATION;
import static com.bestpath.core.collision.CollisionFlags.NORTH;
import static com.bestpath.core.collision.CollisionFlags.NORTH_EAST;
import static com.bestpath.core.collision.CollisionFlags.NORTH_WEST;
import static com.bestpath.core.collision.CollisionFlags.OBJECT;
import static com.bestpath.core.collision.CollisionFlags.SOUTH;
import static com.bestpath.core.collision.CollisionFlags.SOUTH_EAST;
import static com.bestpath.core.collision.CollisionFlags.SOUTH_WEST;
import static com.bestpath.core.collision.CollisionFlags.WEST;

import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.region.Location;
import net.runelite.cache.region.Region;

/**
 * Derives per-tile movement flags from the cache: blocked floor tiles, walls,
 * solid objects and blocking floor decorations. Doors and gates are left open
 * (their places are kept, see {@link FlagGrid#addDoor}).
 *
 * <p>These rules describe how the game client builds its collision from
 * cache data. They are NOT stated in the cache itself, so they are treated
 * as unverified until compared against in-game captures. Rules, by object shape type:
 * <ul>
 * <li>Floor: tile setting bit 1 blocks the floor.</li>
 * <li>Bridges: if plane 1 has the bridge bit (2) at a tile, everything at
 * that x/y counts one plane lower.</li>
 * <li>0 straight wall, 2 corner (L) wall: block the wall's sides, on both
 * tiles they separate.</li>
 * <li>1 diagonal pillar, 3 square corner: block the corner, on both tiles
 * that share it.</li>
 * <li>9 diagonal wall, 10-11 scenery, 12-21 roofs: block the object's
 * footprint (size swapped when rotated 90 or 270 degrees).</li>
 * <li>22 floor decoration: blocks its tile only if its interact type is 1.</li>
 * <li>Walls and objects only block if their interact type is not 0.</li>
 * </ul>
 */
public final class CollisionExtractor
{
	private CollisionExtractor()
	{
	}

	/**
	 * Version of the extraction rules. Saved movement data made with another
	 * version is thrown away and extracted again: bump this whenever the
	 * rules in this class change.
	 */
	public static final int VERSION = 2; // 2: objects whose every state is a door count as doors

	/**
	 * The saved copy of the extracted grid: in a "bestpath-derived" folder beside
	 * the cache folder, never inside it, so the cache stays exactly as downloaded.
	 */
	public static java.io.File savedFile(LoadedCache cache)
	{
		java.io.File dir = new java.io.File(cache.getFolder().getAbsoluteFile().getParentFile(), "bestpath-derived");
		dir.mkdirs();
		return new java.io.File(dir, "collision-v" + VERSION + ".bin.gz");
	}

	/**
	 * The cache's movement data: read from the saved copy when there is a
	 * valid one (a second or two), otherwise extracted from the cache and
	 * saved for next time. The returned grid has none of the manual changes.
	 */
	public static FlagGrid loadOrExtract(LoadedCache cache) throws IOException
	{
		java.io.File f = savedFile(cache);
		int cacheId = cache.getInfo().getOpenrs2Id();
		if (f.isFile())
		{
			try (java.io.InputStream in = new java.io.FileInputStream(f))
			{
				FlagGrid g = FlagGrid.read(in, cache.getRegionIds(), VERSION, cacheId);
				if (g != null)
				{
					return g;
				}
			}
			catch (IOException ex)
			{
				// damaged: extract again below
			}
		}
		FlagGrid g = extract(cache);
		java.io.File tmp = new java.io.File(f.getPath() + ".tmp");
		try (java.io.OutputStream out = new java.io.FileOutputStream(tmp))
		{
			g.write(out, VERSION, cacheId);
		}
		catch (IOException ex)
		{
			tmp.delete();
			return g; // could not save: fine, extract again next time
		}
		java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		return g;
	}

	public static FlagGrid extract(LoadedCache cache) throws IOException
	{
		FlagGrid grid = new FlagGrid(cache.getRegionIds());
		cache.forEachRegion(region -> addRegion(cache, grid, region));
		grid.finishFloors();
		return grid;
	}

	private static void addRegion(LoadedCache cache, FlagGrid grid, Region r)
	{
		addTiles(grid, r);
		for (Location l : r.getLocations())
		{
			addObject(cache, grid, r, l);
		}
	}

	/** The region's floor tiles: which have a floor, and which block it (tile setting bit 1). */
	private static void addTiles(FlagGrid grid, Region r)
	{
		int bx = r.getBaseX();
		int by = r.getBaseY();
		for (int z = 0; z < 4; z++)
		{
			for (int lx = 0; lx < 64; lx++)
			{
				for (int ly = 0; ly < 64; ly++)
				{
					int plane = collisionPlane(r, z, lx, ly);
					if (plane < 0)
					{
						continue;
					}
					if (r.getUnderlayId(z, lx, ly) != 0 || r.getOverlayId(z, lx, ly) != 0)
					{
						grid.markFloor(bx + lx, by + ly, plane);
					}
					if ((r.getTileSetting(z, lx, ly) & 1) != 0)
					{
						grid.add(bx + lx, by + ly, plane, FLOOR);
					}
				}
			}
		}
	}

	/** One placed object: a door is remembered (and left open), anything else that blocks adds its flags. */
	private static void addObject(LoadedCache cache, FlagGrid grid, Region r, Location l)
	{
		ObjectDefinition def = cache.object(l.getId());
		if (def == null || def.getInteractType() == 0)
		{
			return;
		}
		int x = l.getPosition().getX();
		int y = l.getPosition().getY();
		int z = l.getPosition().getZ();
		int lx = x - r.getBaseX();
		int ly = y - r.getBaseY();
		int plane = (lx >= 0 && lx < 64 && ly >= 0 && ly < 64) ? collisionPlane(r, z, lx, ly) : z;
		if (plane < 0)
		{
			return;
		}
		int type = l.getType();
		int rot = l.getOrientation() & 3;
		if ((type <= 3 || type == 9) && (isDoor(def) || isStateDoor(def, cache::object)))
		{
			// Doors and gates count as open. Remember where they are, so they can be
			// reviewed and marked shut.
			grid.addDoor(x, y, plane, l.getId(), type, rot);
			return;
		}
		if (type == 22)
		{
			if (def.getInteractType() == 1)
			{
				grid.add(x, y, plane, FLOOR_DECORATION);
			}
		}
		else if (type >= 9)
		{
			addFootprint(grid, def, x, y, plane, rot);
		}
		else if (type == 0)
		{
			straightWall(grid, x, y, plane, rot);
		}
		else if (type == 2)
		{
			// an L-shaped corner wall: two straight walls
			straightWall(grid, x, y, plane, rot);
			straightWall(grid, x, y, plane, (rot + 1) & 3);
		}
		else if (type == 1 || type == 3)
		{
			cornerPillar(grid, x, y, plane, rot);
		}
	}

	/** Blocks every tile the object covers (its size swapped when turned 90 or 270 degrees). */
	private static void addFootprint(FlagGrid grid, ObjectDefinition def, int x, int y, int plane, int rot)
	{
		int w = def.getSizeX();
		int h = def.getSizeY();
		if (rot == 1 || rot == 3)
		{
			int t = w;
			w = h;
			h = t;
		}
		for (int dx = 0; dx < w; dx++)
		{
			for (int dy = 0; dy < h; dy++)
			{
				grid.add(x + dx, y + dy, plane, OBJECT);
			}
		}
	}

	/**
	 * A placed object the game always shows as one of its states (it switches by a varbit or varp), when every state is
	 * a door or gate (or nothing) and at least one is a door. The placed object itself has no menu, so {@link #isDoor}
	 * alone does not see it: e.g. the Al Kharid toll gate 44598 shows Gate 44052 (Open, Pay-toll(10gp)) below varp
	 * 273 = 100 and Gate 44050 (Open) from 100. Objects with any other state (a doorway that is sometimes a wall, the
	 * Barrows doors) stay walls.
	 */
	static boolean isStateDoor(ObjectDefinition def, java.util.function.IntFunction<ObjectDefinition> lookup)
	{
		int[] dest = def.getConfigChangeDest();
		if (dest == null)
		{
			return false;
		}
		boolean anyDoor = false;
		for (int id : dest)
		{
			if (id < 0)
			{
				continue; // nothing there in this state: no wall either
			}
			ObjectDefinition state = lookup.apply(id);
			if (state == null || !isDoor(state))
			{
				return false;
			}
			anyDoor = true;
		}
		return anyDoor;
	}

	/** A door or gate: a wall-shaped object with an "Open" option. */
	static boolean isDoor(ObjectDefinition def)
	{
		net.runelite.cache.EntityOpsDefinition ops = def.getOps();
		if (ops == null)
		{
			return false;
		}
		for (net.runelite.cache.EntityOpsDefinition.Op op : ops.ops)
		{
			if (op != null && op.text != null && op.text.equalsIgnoreCase("open"))
			{
				return true;
			}
		}
		for (java.util.List<net.runelite.cache.EntityOpsDefinition.ConditionalOp> list : ops.conditionalOps)
		{
			if (list == null)
			{
				continue;
			}
			for (net.runelite.cache.EntityOpsDefinition.ConditionalOp op : list)
			{
				if (op != null && op.text != null && op.text.equalsIgnoreCase("open"))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Puts back the walls of a door or gate that was marked shut. Uses the
	 * same rules as extraction; {@code door} is {x, y, plane, objectId, shapeType, orientation}.
	 */
	public static void addDoorWalls(FlagGrid g, int[] door)
	{
		int x = door[0];
		int y = door[1];
		int plane = door[2];
		int type = door[4];
		int rot = door[5];
		if (type == 0)
		{
			straightWall(g, x, y, plane, rot);
		}
		else if (type == 2)
		{
			straightWall(g, x, y, plane, rot);
			straightWall(g, x, y, plane, (rot + 1) & 3);
		}
		else if (type == 1 || type == 3)
		{
			cornerPillar(g, x, y, plane, rot);
		}
		else
		{
			g.add(x, y, plane, OBJECT);
		}
	}

	/** Plane used for collision: one lower under a bridge. */
	static int collisionPlane(Region r, int z, int lx, int ly)
	{
		return (r.getTileSetting(1, lx, ly) & 2) != 0 ? z - 1 : z;
	}

	/** Orientation 0 = west side, 1 = north, 2 = east, 3 = south. */
	static void straightWall(FlagGrid g, int x, int y, int plane, int rot)
	{
		switch (rot)
		{
			case 0:
				g.add(x, y, plane, WEST);
				g.add(x - 1, y, plane, EAST);
				break;
			case 1:
				g.add(x, y, plane, NORTH);
				g.add(x, y + 1, plane, SOUTH);
				break;
			case 2:
				g.add(x, y, plane, EAST);
				g.add(x + 1, y, plane, WEST);
				break;
			default:
				g.add(x, y, plane, SOUTH);
				g.add(x, y - 1, plane, NORTH);
		}
	}

	/** Orientation 0 = north-west corner, 1 = north-east, 2 = south-east, 3 = south-west. */
	static void cornerPillar(FlagGrid g, int x, int y, int plane, int rot)
	{
		switch (rot)
		{
			case 0:
				g.add(x, y, plane, NORTH_WEST);
				g.add(x - 1, y + 1, plane, SOUTH_EAST);
				break;
			case 1:
				g.add(x, y, plane, NORTH_EAST);
				g.add(x + 1, y + 1, plane, SOUTH_WEST);
				break;
			case 2:
				g.add(x, y, plane, SOUTH_EAST);
				g.add(x + 1, y - 1, plane, NORTH_WEST);
				break;
			default:
				g.add(x, y, plane, SOUTH_WEST);
				g.add(x - 1, y - 1, plane, NORTH_EAST);
		}
	}
}
