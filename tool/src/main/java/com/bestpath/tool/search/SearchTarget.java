package com.bestpath.tool.search;

/** Where a search should move the map to. Immutable. */
public final class SearchTarget
{
	public static final int ANY_PLANE = -1;

	public enum Kind
	{
		TILE, REGION
	}

	private final Kind kind;
	private final int x;
	private final int y;
	private final int plane;
	private final int regionId;

	private SearchTarget(Kind kind, int x, int y, int plane, int regionId)
	{
		this.kind = kind;
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.regionId = regionId;
	}

	public static SearchTarget tile(int x, int y, int plane)
	{
		return new SearchTarget(Kind.TILE, x, y, plane, -1);
	}

	public static SearchTarget region(int regionId)
	{
		return new SearchTarget(Kind.REGION, -1, -1, ANY_PLANE, regionId);
	}

	public Kind getKind()
	{
		return kind;
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	/** The requested plane, or {@link #ANY_PLANE} to keep the current one. */
	public int getPlane()
	{
		return plane;
	}

	public int getRegionId()
	{
		return regionId;
	}
}
