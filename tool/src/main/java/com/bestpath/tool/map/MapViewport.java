package com.bestpath.tool.map;

/**
 * Converts between world tile coordinates and screen pixels. North is up:
 * world y grows towards the top of the screen. Tile (x, y) covers world
 * coordinates [x, x+1) x [y, y+1).
 */
public final class MapViewport
{
	public static final double MIN_SCALE = 0.05;
	public static final double MAX_SCALE = 32;

	private double centerX;
	private double centerY;
	/** Screen pixels per tile. */
	private double scale;
	private int width;
	private int height;

	public MapViewport(double centerX, double centerY, double scale)
	{
		this.centerX = centerX;
		this.centerY = centerY;
		this.scale = clamp(scale);
	}

	public void setSize(int width, int height)
	{
		this.width = width;
		this.height = height;
	}

	public double screenX(double worldX)
	{
		return (worldX - centerX) * scale + width / 2.0;
	}

	public double screenY(double worldY)
	{
		return height / 2.0 - (worldY - centerY) * scale;
	}

	public double worldX(double screenX)
	{
		return (screenX - width / 2.0) / scale + centerX;
	}

	public double worldY(double screenY)
	{
		return centerY - (screenY - height / 2.0) / scale;
	}

	/** Moves the view by a mouse drag of (dx, dy) pixels. */
	public void panPixels(double dx, double dy)
	{
		centerX -= dx / scale;
		centerY += dy / scale;
	}

	/** Zooms by {@code factor}, keeping the world point under (sx, sy) fixed on screen. */
	public void zoomAt(double sx, double sy, double factor)
	{
		double wx = worldX(sx);
		double wy = worldY(sy);
		scale = clamp(scale * factor);
		centerX = wx - (sx - width / 2.0) / scale;
		centerY = wy + (sy - height / 2.0) / scale;
	}

	public void centerOn(double worldX, double worldY)
	{
		centerX = worldX;
		centerY = worldY;
	}

	public void setScale(double scale)
	{
		this.scale = clamp(scale);
	}

	private static double clamp(double s)
	{
		return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
	}

	public double getCenterX()
	{
		return centerX;
	}

	public double getCenterY()
	{
		return centerY;
	}

	public double getScale()
	{
		return scale;
	}

	public int getWidth()
	{
		return width;
	}

	public int getHeight()
	{
		return height;
	}
}
