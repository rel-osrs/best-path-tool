package com.bestpath.core.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One route question: from the player's tile to a destination tile, with
 * the transports that may be used and the plane-change penalty. Immutable.
 *
 * <p>Boat-only transports ({@link Transport#forBoats}) are kept apart: {@link #getTransports()}
 * never has them, so no walking search, bound or check uses one; the sea search reads them from
 * {@link #getBoatTransports()}. Every way of making a question keeps them (a new list given to
 * {@link #withTransports} replaces the walking transports only).
 */
public final class PathQuery
{
	/**
	 * Tiles a search may not enter, walking or landing (for example the Wilderness, when the route avoids it). Asked
	 * per 64x64 cell first, per tile only for a cell that is partly closed.
	 */
	public interface Closed
	{
		/** {@link #OPEN}, {@link #SHUT} or {@link #PART} for the cell whose lowest tile is (x0, y0) (any plane). */
		int cell(int x0, int y0);

		/** True when this tile may not be entered (asked only in a {@link #PART} cell). */
		boolean tile(int x, int y);

		int OPEN = 0;
		int SHUT = 1;
		int PART = 2;
	}

	/**
	 * Teleports that work only up to some Wilderness depth. A teleport the start is too deep for is not used from the
	 * start: the search offers it from every tile it reaches that is shallow enough (walked to, or reached by any
	 * transport, such as a lever out of the Wilderness), and keeps the best.
	 */
	public interface Limits
	{
		/** The tile's depth (0 = outside: every teleport works). */
		int zone(int x, int y);

		/** The depth of every tile of the 64x64 cell whose lowest tile is (x0, y0), or -1 when they differ. */
		int cellZone(int x0, int y0);

		/** The deepest zone this teleport works from (a large number: no limit). */
		int deepest(Transport teleport);
	}


	private final int startX;
	private final int startY;
	private final int startPlane;
	private final int destX;
	private final int destY;
	private final int destPlane;
	private final List<Transport> transports;
	private final List<Transport> boatTransports;
	private final int planeChangePenalty;
	private final DoorMap doors;
	private final int doorCost;
	private final Closed closed;
	private final Limits limits;

	public PathQuery(int startX, int startY, int startPlane, int destX, int destY, int destPlane,
		List<Transport> transports, int planeChangePenalty)
	{
		this(startX, startY, startPlane, destX, destY, destPlane, transports, planeChangePenalty, DoorMap.none(), 0);
	}

	/** With door crossings: each step through a door costs {@code doorCost} extra ticks. */
	public PathQuery(int startX, int startY, int startPlane, int destX, int destY, int destPlane,
		List<Transport> transports, int planeChangePenalty, DoorMap doors, int doorCost)
	{
		this(startX, startY, startPlane, destX, destY, destPlane, transports, List.of(), planeChangePenalty, doors, doorCost, null, null);
	}

	/** {@code transports} may hold boat-only ones: they join {@code boats}. */
	PathQuery(int startX, int startY, int startPlane, int destX, int destY, int destPlane,
		List<Transport> transports, List<Transport> boats, int planeChangePenalty, DoorMap doors, int doorCost, Closed closed,
		Limits limits)
	{
		this.closed = closed;
		this.limits = limits;
		this.startX = startX;
		this.startY = startY;
		this.startPlane = startPlane;
		this.destX = destX;
		this.destY = destY;
		this.destPlane = destPlane;
		boolean any = false;
		for (Transport t : transports)
		{
			if (t.isBoatOnly())
			{
				any = true;
				break;
			}
		}
		if (any)
		{
			List<Transport> walk = new ArrayList<>(transports.size());
			List<Transport> boat = new ArrayList<>(boats);
			for (Transport t : transports)
			{
				if (!t.isBoatOnly())
				{
					walk.add(t);
				}
				else if (!boat.contains(t))
				{
					boat.add(t);
				}
			}
			this.transports = Collections.unmodifiableList(walk);
			this.boatTransports = List.copyOf(boat);
		}
		else
		{
			this.transports = Collections.unmodifiableList(List.copyOf(transports));
			this.boatTransports = boats;
		}
		this.planeChangePenalty = planeChangePenalty;
		this.doors = doors;
		this.doorCost = doors.isEmpty() ? 0 : doorCost;
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

	public int getDestX()
	{
		return destX;
	}

	public int getDestY()
	{
		return destY;
	}

	public int getDestPlane()
	{
		return destPlane;
	}

	public List<Transport> getTransports()
	{
		return transports;
	}

	public int getPlaneChangePenalty()
	{
		return planeChangePenalty;
	}

	public DoorMap getDoors()
	{
		return doors;
	}

	/** Extra ticks for a step through a door (0 = none). */
	public int getDoorCost()
	{
		return doorCost;
	}

	/** The tiles the search may not enter, or null (none). */
	public Closed getClosed()
	{
		return closed;
	}

	/** The same question with these tiles closed (null: none). */
	public PathQuery withClosed(Closed c)
	{
		return c == closed ? this : new PathQuery(startX, startY, startPlane, destX, destY, destPlane, transports, boatTransports,
			planeChangePenalty, doors, doorCost, c, limits);
	}

	/** The teleports' Wilderness limits, or null (every teleport works from the start). */
	public Limits getLimits()
	{
		return limits;
	}

	/** The same question with these Wilderness limits (null: none). */
	public PathQuery withLimits(Limits l)
	{
		return l == limits ? this : new PathQuery(startX, startY, startPlane, destX, destY, destPlane, transports, boatTransports,
			planeChangePenalty, doors, doorCost, closed, l);
	}

	/** The same question with another destination. */
	public PathQuery withDestination(int x, int y, int plane)
	{
		return new PathQuery(startX, startY, startPlane, x, y, plane, transports, boatTransports, planeChangePenalty, doors, doorCost,
			closed, limits);
	}

	/**
	 * The same question with other walking transports (boat-only ones in {@code list} join this question's own,
	 * which stay).
	 */
	public PathQuery withTransports(List<Transport> list)
	{
		return new PathQuery(startX, startY, startPlane, destX, destY, destPlane, list, boatTransports, planeChangePenalty, doors,
			doorCost, closed, limits);
	}

	/**
	 * The boat-only transports that may be used ({@link Transport#forBoats}): only the sea search uses them, never a
	 * walking search.
	 */
	public List<Transport> getBoatTransports()
	{
		return boatTransports;
	}

	/** The same question with these boat-only transports instead of its own. */
	public PathQuery withBoatTransports(List<Transport> boats)
	{
		List<Transport> only = new ArrayList<>(boats.size());
		for (Transport t : boats)
		{
			if (t.isBoatOnly())
			{
				only.add(t);
			}
		}
		return new PathQuery(startX, startY, startPlane, destX, destY, destPlane, transports, List.copyOf(only), planeChangePenalty, doors,
			doorCost, closed, limits);
	}
}
