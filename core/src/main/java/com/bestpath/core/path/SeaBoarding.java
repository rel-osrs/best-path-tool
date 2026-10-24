package com.bestpath.core.path;

/**
 * One way onto a boat for a sailing route: the dock (-1 = at sea at the start), what bringing the boat there costs,
 * how, and the bringing step's transport id ("sail-bring:slot", or "sail-release:slot" for a bottle).
 */
final class SeaBoarding
{
	final int dock;
	final int extra;
	final String how;
	final String id;
	/** The dock's shipwright brings the boat (the player walks to the shipwright first, when its place is known). */
	final boolean wright;
	/**
	 * This route's ticks until the boat is ready here ({@link SailingSearch#readyToBoard}): {@link SailingSearch#UNSEARCHED}
	 * until needed, -1 none.
	 */
	int ready = SailingSearch.UNSEARCHED;

	SeaBoarding(int dock, int extra, String how, String id)
	{
		this(dock, extra, how, id, false);
	}

	SeaBoarding(int dock, int extra, String how, String id, boolean wright)
	{
		this.dock = dock;
		this.extra = extra;
		this.how = how;
		this.id = id;
		this.wright = wright;
	}
}
