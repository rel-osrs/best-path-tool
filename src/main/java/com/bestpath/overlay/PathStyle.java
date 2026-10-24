package com.bestpath.overlay;

/**
 * How the walk is drawn in the game world: the "Path style" setting. Transport tiles are always drawn as tiles (where
 * to click); the boat's course is always lines with an arrow at each turn.
 */
public enum PathStyle
{
	/** A square on every tile. */
	TILES("Tiles"),
	/** One line through the middle of the tiles. */
	LINE("Line"),
	/** The line, with an arrow head only where the walk turns. */
	ARROWS("Arrows at turns");

	private final String label;

	PathStyle(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
