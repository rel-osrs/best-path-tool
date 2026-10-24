package com.bestpath.core.path;

/**
 * Makes {@link PathQuery}s that only development code asks.
 */
public final class PathQueryBuilder
{
	private PathQueryBuilder()
	{
	}

	/** The same question from another start. */
	public static PathQuery withStart(PathQuery q, int x, int y, int plane)
	{
		return new PathQuery(x, y, plane, q.getDestX(), q.getDestY(), q.getDestPlane(), q.getTransports(), q.getBoatTransports(),
			q.getPlaneChangePenalty(), q.getDoors(), q.getDoorCost(), q.getClosed(), q.getLimits());
	}
}
