package com.bestpath.core.sea;

/**
 * The kinds of boat a player can own. From the cache's DB table 166
 * (SailingBoat): name, size ("Tiny (1x3)" etc., width x length in tiles),
 * Sailing level, and the retrieval cost a shipwright charges to bring the
 * boat to the player's dock ("Recover-boat"). Rafts have no keel option.
 */
public enum BoatType
{
	RAFT("Raft", 1, 3, 1, 250, false),
	SKIFF("Skiff", 2, 5, 15, 3750, true),
	SLOOP("Sloop", 3, 10, 50, 50000, true);

	private final String label;
	private final int width;
	final int length;
	final int level;
	private final int retrievalCost;
	private final boolean hasKeel;

	BoatType(String label, int width, int length, int level, int retrievalCost, boolean hasKeel)
	{
		this.label = label;
		this.width = width;
		this.length = length;
		this.level = level;
		this.retrievalCost = retrievalCost;
		this.hasKeel = hasKeel;
	}

	public String getLabel()
	{
		return label;
	}

	/** Width in tiles. */
	public int getWidth()
	{
		return width;
	}

	/** Coins a shipwright charges to bring the boat to the player's dock. */
	public int getRetrievalCost()
	{
		return retrievalCost;
	}

	public boolean hasKeel()
	{
		return hasKeel;
	}

	/**
	 * Clearance the boat needs at all: its centre tile must be at least this
	 * many tiles (Chebyshev, 1 = the next tile is an obstacle) from the
	 * nearest obstacle, so that half its width fits beside the centre:
	 * clearance - 0.5 >= width / 2. Raft 1, skiff 2, sloop 2.
	 */
	public int hardClearance()
	{
		return (int) Math.ceil(width / 2.0 + 0.5);
	}

	/**
	 * Clearance the boat keeps when it can (it may come closer only to pass a
	 * chokepoint): the boat's side stays half its width from obstacles, so
	 * clearance - 0.5 >= width. Raft 2, skiff 3, sloop 4.
	 */
	public int preferredClearance()
	{
		return width + 1;
	}
}
