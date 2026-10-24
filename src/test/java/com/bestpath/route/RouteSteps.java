package com.bestpath.route;

import com.bestpath.core.path.Tiles;

/** Tests and previews: route steps made directly. */
public final class RouteSteps
{
	private RouteSteps()
	{
	}

	/**
	 * This sail step with its course's corners: indexes into its tiles (in order, the first 0 and the last the last
	 * tile's), each leg between two of them straight; the corners exactly are the corner tiles' middles. Invalid
	 * lists are refused ({@link RouteStep#withCourse}).
	 */
	public static RouteStep withCorners(RouteStep s, int[] cornerIndexes)
	{
		double[] p = new double[2 * cornerIndexes.length];
		for (int i = 0; i < cornerIndexes.length; i++)
		{
			int t = cornerIndexes[i] >= 0 && cornerIndexes[i] < s.tileCount() ? s.tile(cornerIndexes[i]) : 0;
			p[2 * i] = Tiles.x(t);
			p[2 * i + 1] = Tiles.y(t);
		}
		return s.withCourse(cornerIndexes, p);
	}
}
