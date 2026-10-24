package com.bestpath.core.path;

/** A tile packed into one int: 14 bits x, 14 bits y, 2 bits plane. */
public final class Tiles
{
	private Tiles()
	{
	}

	public static int pack(int x, int y, int plane)
	{
		return (x & 0x3FFF) << 16 | (y & 0x3FFF) << 2 | (plane & 3);
	}

	public static int x(int t)
	{
		return t >>> 16 & 0x3FFF;
	}

	public static int y(int t)
	{
		return t >>> 2 & 0x3FFF;
	}

	public static int plane(int t)
	{
		return t & 3;
	}

	/** The cell of a tile (its 64x64 square on its plane), as {@code regionId << 2 | plane}. */
	static int cell(int t)
	{
		return ((x(t) >> 6) << 8 | (y(t) >> 6)) << 2 | plane(t);
	}

	/** Index of a tile inside its cell: {@code localX << 6 | localY}. */
	static int local(int t)
	{
		return (x(t) & 63) << 6 | (y(t) & 63);
	}

	static int fromCell(int cell, int local)
	{
		int region = cell >> 2;
		return pack(((region >> 8) << 6) + (local >> 6), ((region & 0xFF) << 6) + (local & 63), cell & 3);
	}
}
