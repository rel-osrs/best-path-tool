package com.bestpath.core.path;

import com.bestpath.core.sea.BoatType;

/**
 * A non-walking edge for the pathfinder: a ladder, shortcut, boat... from a
 * start tile to an end tile, or a teleport (no start: usable from anywhere).
 * Immutable.
 */
public final class Transport
{
	private final String id;
	private final String name;
	private final boolean hasStart;
	private final int startX;
	private final int startY;
	private final int startPlane;
	private final int endX;
	private final int endY;
	private final int endPlane;
	private final int ticks;
	private final boolean twoWay;
	private final boolean confirmed;
	private final boolean fromBeside;
	/** The player's own change of its ticks (0 = the data's ticks), see {@link #withTickChange(int)}. */
	private final int tickChange;
	/** Only a boat uses it: the widest boat that fits (a {@link BoatType}'s width: 1 raft, 2 skiff, 3 sloop); 0 = not boat-only. */
	private final int largestBoat;

	private Transport(String id, String name, boolean hasStart, int sx, int sy, int sp, int ex, int ey, int ep,
		int ticks, boolean twoWay, boolean confirmed)
	{
		this(id, name, hasStart, sx, sy, sp, ex, ey, ep, ticks, twoWay, confirmed, false, 0, 0);
	}

	private Transport(String id, String name, boolean hasStart, int sx, int sy, int sp, int ex, int ey, int ep,
		int ticks, boolean twoWay, boolean confirmed, boolean fromBeside, int tickChange, int largestBoat)
	{
		if (ticks < 0)
		{
			throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
		}
		this.id = id;
		this.name = name;
		this.hasStart = hasStart;
		this.startX = sx;
		this.startY = sy;
		this.startPlane = sp;
		this.endX = ex;
		this.endY = ey;
		this.endPlane = ep;
		this.ticks = ticks;
		this.twoWay = twoWay && hasStart;
		this.confirmed = confirmed;
		this.fromBeside = fromBeside && hasStart;
		this.tickChange = tickChange;
		this.largestBoat = hasStart ? Math.max(0, largestBoat) : 0;
	}

	/** A transport from a start tile to an end tile; {@code twoWay}: usable in both directions. */
	public static Transport between(String id, String name, int sx, int sy, int sp, int ex, int ey, int ep,
		int ticks, boolean twoWay, boolean confirmed)
	{
		return new Transport(id, name, true, sx, sy, sp, ex, ey, ep, ticks, twoWay, confirmed);
	}

	/**
	 * The same transport with its ends on objects (the middle of a fairy ring). Where an end's tile can't be
	 * walked on, the player uses the transport from a tile beside it (one of its four sides, no wall between)
	 * and, arriving there, steps off it onto a tile beside it (one step, as walking would allow if the
	 * object's tile were open). An end on a tile that can be walked on is used as usual. See {@link Beside}.
	 */
	public Transport usedFromBeside()
	{
		return new Transport(id, name, hasStart, startX, startY, startPlane, endX, endY, endPlane, ticks, twoWay, confirmed, true,
			tickChange, largestBoat);
	}

	/**
	 * The same transport for boats only (such as a sea cave): from water to water, the player staying on the boat.
	 * The walking search never uses it ({@link PathQuery} keeps it apart); the sea search does ({@link Sailing}), for
	 * boats no wider than {@code largestBoat} tiles (a {@link BoatType}'s width: 1 raft, 2 skiff, 3 sloop).
	 * 0 = not boat-only.
	 */
	public Transport forBoats(int largestBoat)
	{
		return largestBoat == this.largestBoat ? this : new Transport(id, name, hasStart, startX, startY, startPlane, endX, endY,
			endPlane, ticks, twoWay, confirmed, fromBeside, tickChange, largestBoat);
	}

	/** True when only a boat uses it ({@link #forBoats}). */
	public boolean isBoatOnly()
	{
		return largestBoat > 0;
	}

	/** Whether a boat of this kind may use it (a boat-only transport no narrower than the boat). */
	public boolean fits(BoatType type)
	{
		return largestBoat > 0 && type.getWidth() <= largestBoat;
	}

	/**
	 * The same transport with the player's own change of its ticks (set with - / + on its step card):
	 * {@link #cost(int)} is then the data's cost plus {@code change}, never below 0 (the change may also take
	 * off the plane-change penalty). {@link #getTicks()} stays the data's ticks. 0 = no change.
	 */
	public Transport withTickChange(int change)
	{
		return change == tickChange ? this : new Transport(id, name, hasStart, startX, startY, startPlane, endX, endY, endPlane,
			ticks, twoWay, confirmed, fromBeside, change, largestBoat);
	}

	/** The player's own change of its ticks (0 = none), see {@link #withTickChange(int)}. */
	public int getTickChange()
	{
		return tickChange;
	}

	/** A teleport: no start, usable from anywhere. */
	public static Transport teleport(String id, String name, int ex, int ey, int ep, int ticks, boolean confirmed)
	{
		return new Transport(id, name, false, 0, 0, 0, ex, ey, ep, ticks, false, confirmed);
	}

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public boolean hasStart()
	{
		return hasStart;
	}

	public int getStartX()
	{
		return startX;
	}

	public int getStartY()
	{
		return startY;
	}

	public int getStartPlane()
	{
		return startPlane;
	}

	public int getEndX()
	{
		return endX;
	}

	public int getEndY()
	{
		return endY;
	}

	public int getEndPlane()
	{
		return endPlane;
	}

	/** The transport's own tick cost (without the plane-change penalty). */
	public int getTicks()
	{
		return ticks;
	}

	public boolean isTwoWay()
	{
		return twoWay;
	}

	/** True when its ends are objects used from beside them (see {@link #usedFromBeside()}). */
	public boolean isUsedFromBeside()
	{
		return fromBeside;
	}

	/** False for an unconfirmed suggestion. */
	public boolean isConfirmed()
	{
		return confirmed;
	}

	/**
	 * Ticks to use it, with the plane-change penalty when start and end planes differ (teleports: none), and the
	 * player's own change if any (never below 0). Every search and lower bound reads a transport's cost here.
	 */
	public int cost(int planeChangePenalty)
	{
		int c = ticks + (hasStart && startPlane != endPlane ? planeChangePenalty : 0);
		return tickChange == 0 ? c : Math.max(0, c + tickChange);
	}

	@Override
	public String toString()
	{
		return name + " (" + id + ")";
	}
}
