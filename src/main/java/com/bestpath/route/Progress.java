package com.bestpath.route;

/**
 * How far the player is along the published route: which step is current
 * and, within a walk, the nearest tile so far. Immutable; published through
 * an atomic reference that overlays (client thread) and the sidebar (Swing)
 * read.
 */
public final class Progress
{
	/** No route being followed. */
	public static final Progress NONE = new Progress(-1, -1, 0);

	private final long generation;
	private final int step;
	private final int tileIndex;

	public Progress(long generation, int step, int tileIndex)
	{
		this.generation = generation;
		this.step = step;
		this.tileIndex = tileIndex;
	}

	/** The {@link RouteResult#getGeneration()} this progress belongs to; -1 for none. */
	public long getGeneration()
	{
		return generation;
	}

	/** Index of the current step in {@link RouteResult#getSteps()}; -1 for none. */
	public int getStep()
	{
		return step;
	}

	/** For a walk / sail step: index of the step's tile the player has got to (tiles before it are walked). */
	public int getTileIndex()
	{
		return tileIndex;
	}

	/** True if this progress is about this result. */
	public boolean isFor(RouteResult r)
	{
		return r != null && step >= 0 && r.getGeneration() == generation;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof Progress))
		{
			return false;
		}
		Progress p = (Progress) o;
		return generation == p.generation && step == p.step && tileIndex == p.tileIndex;
	}

	@Override
	public int hashCode()
	{
		return Long.hashCode(generation) * 31 * 31 + step * 31 + tileIndex;
	}

	@Override
	public String toString()
	{
		return "route " + generation + " step " + step + " tile " + tileIndex;
	}
}
