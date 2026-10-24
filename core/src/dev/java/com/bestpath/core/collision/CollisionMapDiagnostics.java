package com.bestpath.core.collision;

/**
 * What development code reads from a {@link CollisionMap} that the plugin itself never needs.
 */
public final class CollisionMapDiagnostics
{
	private CollisionMapDiagnostics()
	{
	}

	/** The OpenRS2 id of the game cache the map was made from. */
	public static int cacheId(CollisionMap map)
	{
		return map.cacheId;
	}

	/** The game build of that cache. */
	public static int cacheBuild(CollisionMap map)
	{
		return map.cacheBuild;
	}
}
