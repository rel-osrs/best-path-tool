package com.bestpath.core.sea;

import com.bestpath.core.req.Requirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A place where the player switches between walking and sailing: the buoy the
 * boat docks at (sea side) and the gangplank or mooring point the player uses
 * (land side), with the dock's Sailing level and quest from the cache
 * (DB table 194). Immutable.
 *
 * <p>Tiles are collision tiles: plane is the plane the player walks on (a
 * gangplank stored on a bridged pier one plane up is on plane 0 here).
 */
public final class Dock
{
	/** Ticks to board the boat, and again to leave it (the same as a boat ride). */
	public static final int BOARD_TICKS = 10;

	public enum Kind
	{
		GANGPLANK("Gangplank"),
		MOORING_POINT("Mooring point");

		final String label;

		Kind(String label)
		{
			this.label = label;
		}
	}

	private final int id;
	private final String name;
	private final int level;
	private final String quest;
	final Kind kind;
	final int landX;
	final int landY;
	final int landPlane;
	final int landSize;
	private final int buoyX;
	private final int buoyY;
	private final int buoySize;
	private final List<int[]> landTiles;
	private final String shipwright;
	/** Where the shipwright stands ({x, y, plane}), or null (not known: the boat is brought at the land tile). */
	private final int[] shipwrightTile;
	private final Requirement questRequirement;

	public Dock(int id, String name, int level, String quest, Kind kind, int landX, int landY, int landPlane, int landSize,
		int buoyX, int buoyY, int buoySize, List<int[]> landTiles)
	{
		this.id = id;
		this.name = name;
		this.level = level;
		this.quest = quest;
		this.kind = kind;
		this.landX = landX;
		this.landY = landY;
		this.landPlane = landPlane;
		this.landSize = landSize;
		this.buoyX = buoyX;
		this.buoyY = buoyY;
		this.buoySize = buoySize;
		List<int[]> copy = new ArrayList<>();
		for (int[] t : landTiles)
		{
			copy.add(t.clone());
		}
		this.landTiles = Collections.unmodifiableList(copy);
		this.shipwright = null;
		this.shipwrightTile = null;
		this.questRequirement = quest == null ? Requirement.NONE : new Requirement.Quest(quest, Requirement.Quest.Wanted.DONE);
	}

	private Dock(Dock d, String shipwright, int[] shipwrightTile)
	{
		this.id = d.id;
		this.name = d.name;
		this.level = d.level;
		this.quest = d.quest;
		this.kind = d.kind;
		this.landX = d.landX;
		this.landY = d.landY;
		this.landPlane = d.landPlane;
		this.landSize = d.landSize;
		this.buoyX = d.buoyX;
		this.buoyY = d.buoyY;
		this.buoySize = d.buoySize;
		this.landTiles = d.landTiles;
		this.shipwright = shipwright;
		this.shipwrightTile = shipwrightTile == null ? null : shipwrightTile.clone();
		this.questRequirement = d.questRequirement;
	}

	/**
	 * The same dock with a shipwright (who can bring the player's boat here, "Recover-boat") whose place is not known:
	 * routes then get the boat brought at the dock's land tile.
	 */
	public Dock withShipwright(String name)
	{
		return new Dock(this, name, null);
	}

	/**
	 * The same dock with a shipwright standing at (x, y, plane): routes walk to the shipwright, then to the dock. The
	 * tile is the NPC's spawn tile from the OSRS Wiki; NPCs wander, so routes walk to a walkable tile next to it
	 * ({@link com.bestpath.core.path.Sailing}).
	 */
	public Dock withShipwright(String name, int x, int y, int plane)
	{
		return new Dock(this, name, new int[]{x, y, plane});
	}

	/** The shipwright at this dock (name), or null. */
	public String getShipwright()
	{
		return shipwright;
	}

	/** Where the shipwright stands ({x, y, plane}, a copy), or null when there is none or the place is not known. */
	public int[] getShipwrightTile()
	{
		return shipwright == null || shipwrightTile == null ? null : shipwrightTile.clone();
	}

	/** The dock id in the cache (column 0 of table 194). */
	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	/** Sailing level needed to dock here. */
	public int getLevel()
	{
		return level;
	}

	/** Quest needed to dock here (its name), or null. */
	public String getQuest()
	{
		return quest;
	}

	/**
	 * The quest a player must have finished to use this dock, as a requirement; {@link Requirement#NONE} for a dock
	 * without one. The game's own dock check (cache script 9137 over table 194 column 5) asks every listed quest to be
	 * finished (script 4136: quest state 2).
	 */
	public Requirement questRequirement()
	{
		return questRequirement;
	}

	/** South-west tile of the buoy (plane 0). */
	public int getBuoyX()
	{
		return buoyX;
	}

	public int getBuoyY()
	{
		return buoyY;
	}

	public int getBuoySize()
	{
		return buoySize;
	}

	/**
	 * Walkable land tiles (not sea) next to a side of the gangplank or mooring
	 * point: where the player stands to use it. {x, y, plane} each; read only.
	 */
	public List<int[]> getLandTiles()
	{
		return landTiles;
	}
}
