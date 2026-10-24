package com.bestpath.overlay;

import com.bestpath.route.DrawPath;
import com.bestpath.route.RouteResult;

/** Tests and previews: a result's world map draw lists with every tile where it is (no world map's shift). */
public final class WorldMapPaths
{
	private WorldMapPaths()
	{
	}

	public static WorldMapPath of(RouteResult r)
	{
		return of(r, null);
	}

	/** With the walks as {@code drawn} has them (when it is for this result). */
	public static WorldMapPath of(RouteResult r, DrawPath drawn)
	{
		return WorldMapPath.of(r, drawn, -1, WorldMapPath.Placement.AS_IS);
	}
}
