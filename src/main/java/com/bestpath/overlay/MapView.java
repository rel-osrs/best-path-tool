package com.bestpath.overlay;

/**
 * What the world map shows right now, as plain numbers: the map's rectangle on
 * the game canvas (RuneLite widget {@code InterfaceID.Worldmap.MAP_CONTAINER}),
 * the world tile at its middle ({@code WorldMap.getWorldMapPosition()}) and the
 * zoom in pixels per tile ({@code WorldMap.getWorldMapZoom()}). Converts world
 * tiles to canvas pixels and back. Pure and immutable (no RuneLite classes), so
 * it is unit tested with made-up bounds and zooms.
 *
 * <p>The layout follows how RuneLite places its own world map icons: the map is
 * {@code ceil(width / zoom)} tiles wide, the middle tile's column is half of
 * that (rounded down) from the left edge, north is up, and a tile's point is the
 * middle of its square.
 */
public final class MapView
{
	private final int x;
	private final int y;
	private final int width;
	private final int height;
	private final double zoom;
	/** The world tile in the map's leftmost column and in its bottom row. */
	private final int leftTile;
	private final int bottomTile;

	/**
	 * @param x       the map's left edge on the canvas (pixels)
	 * @param y       the map's top edge on the canvas
	 * @param width   the map's width in pixels
	 * @param height  the map's height in pixels
	 * @param centerX the world tile x at the map's middle
	 * @param centerY the world tile y at the map's middle
	 * @param zoom    pixels per tile (more than 0)
	 */
	public MapView(int x, int y, int width, int height, int centerX, int centerY, double zoom)
	{
		if (!(zoom > 0) || width <= 0 || height <= 0)
		{
			throw new IllegalArgumentException("empty map view");
		}
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.zoom = zoom;
		int widthTiles = (int) Math.ceil(width / zoom);
		int heightTiles = (int) Math.ceil(height / zoom);
		this.leftTile = centerX - widthTiles / 2;
		this.bottomTile = centerY - heightTiles / 2;
	}

	public double getZoom()
	{
		return zoom;
	}

	/** Canvas x of the middle of tile column {@code tileX}. */
	public double screenX(int tileX)
	{
		return screenX((double) tileX);
	}

	/** Canvas y of the middle of tile row {@code tileY} (north is up). */
	public double screenY(int tileY)
	{
		return screenY((double) tileY);
	}

	/** Canvas x of a point in world tiles (a tile's middle is its whole x; a boat's corner need not be one). */
	public double screenX(double tileX)
	{
		return x + (tileX - leftTile) * zoom + zoom / 2;
	}

	/** Canvas y of a point in world tiles (north is up). */
	public double screenY(double tileY)
	{
		return y + height - ((tileY - bottomTile) * zoom + zoom / 2);
	}

	/** The world tile x under canvas pixel column {@code px} (its middle). */
	public int tileX(int px)
	{
		return leftTile + (int) Math.floor((px + 0.5 - x) / zoom);
	}

	/** The world tile y under canvas pixel row {@code py} (its middle). */
	public int tileY(int py)
	{
		return bottomTile + (int) Math.floor((y + height - py - 0.5) / zoom);
	}

	/** True if the canvas point is inside the map's rectangle. */
	public boolean contains(int px, int py)
	{
		return px >= x && px < x + width && py >= y && py < y + height;
	}

	/** The smallest world tile x the map shows (partly). */
	public int minTileX()
	{
		return tileX(x);
	}

	/** The largest world tile x the map shows (partly). */
	public int maxTileX()
	{
		return tileX(x + width - 1);
	}

	/** The smallest world tile y the map shows (its bottom row). */
	public int minTileY()
	{
		return tileY(y + height - 1);
	}

	/** The largest world tile y the map shows (its top row). */
	public int maxTileY()
	{
		return tileY(y);
	}

	/** True if a box of tiles (inclusive) overlaps what the map shows, with {@code margin} tiles to spare. */
	public boolean overlaps(int minX, int minY, int maxX, int maxY, int margin)
	{
		return maxX >= minTileX() - margin && minX <= maxTileX() + margin
			&& maxY >= minTileY() - margin && minY <= maxTileY() + margin;
	}
}
