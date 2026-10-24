package com.bestpath.tool.geo;

/**
 * World tile and region arithmetic. A region (also our pathfinder "cell") is a
 * 64x64 block of tiles; its id packs the region coordinates as (rx << 8) | ry,
 * the same layout the game cache uses.
 */
public final class Regions
{
	public static final int SIZE = 64;
	public static final int PLANES = 4;

	private Regions()
	{
	}

	public static int id(int regionX, int regionY)
	{
		return (regionX << 8) | regionY;
	}

	public static int regionX(int regionId)
	{
		return regionId >> 8;
	}

	public static int regionY(int regionId)
	{
		return regionId & 0xFF;
	}

	public static int idOfTile(int x, int y)
	{
		return id(x >> 6, y >> 6);
	}

	public static int baseX(int regionId)
	{
		return regionX(regionId) * SIZE;
	}

	public static int baseY(int regionId)
	{
		return regionY(regionId) * SIZE;
	}
}
