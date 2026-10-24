package com.bestpath.core.sea;

/**
 * The sea areas: open water any boat can sail, five restricted areas that
 * need a boat part, and
 * the water beyond the world edge. Each area is a family of water textures in
 * the cache (130-134, 135-139, ...); the coastal shallows count as open water.
 * Seas have no entry level: only the boat's parts limit where it may go.
 */
public enum SeaArea
{
	OPEN("Open water", null),
	TEMPOR_TANTRUM("Tempor Tantrum", "an oak mast or better (oak to mahogany pass with a warning)"),
	NORTHERN("Fremennik / northern waters", "a torch"),
	CRYSTALLINE("Crystalline waters", "an adamant keel"),
	FETID("Fetid water", "an inoculation station"),
	KELP("Kelp area", "an adamant helm"),
	/** Texture 208; cut off from the rest of the sea by a blocked border line. */
	WORLD_EDGE("Beyond the world edge", null);

	private final String label;
	private final String needs;

	SeaArea(String label, String needs)
	{
		this.label = label;
		this.needs = needs;
	}

	public String getLabel()
	{
		return label;
	}

	/** What the boat needs to enter, or null when nothing is needed. */
	public String getNeeds()
	{
		return needs;
	}
}
