package com.bestpath.route;


/**
 * The walk tiles the overlays draw for one published route: at first the
 * route's own tiles; a walk's line is replaced when the player's tile shows
 * the game would walk it another, equally short way ({@link RouteRedraw}).
 * Each walk keeps its length (tile i is i steps from the walk's start), so
 * route progress indexes it like the walk itself. Immutable; published
 * through an atomic reference that the overlays (client thread) read. The
 * route's steps, ticks and sidebar cards are never changed by it.
 */
public final class DrawPath
{
	public static final DrawPath NONE = new DrawPath(-1, new int[0][], 0);

	private final long generation;
	/** Per step of the route: the tiles to draw (walk and sail steps), null for the others. */
	private final int[][] tiles;
	/** How many times a line was redrawn (0 = the route's own tiles). */
	private final int version;

	private DrawPath(long generation, int[][] tiles, int version)
	{
		this.generation = generation;
		this.tiles = tiles;
		this.version = version;
	}

	/** The route's own tiles. */
	public static DrawPath of(RouteResult r)
	{
		if (r == null || r.getStatus() != RouteStatus.READY)
		{
			return NONE;
		}
		int[][] t = new int[r.getSteps().size()][];
		for (int s = 0; s < r.segmentCount(); s++)
		{
			int[] seg = new int[r.segmentLength(s)];
			for (int i = 0; i < seg.length; i++)
			{
				seg[i] = r.segmentTile(s, i);
			}
			t[r.segmentStep(s)] = seg;
		}
		return new DrawPath(r.getGeneration(), t, 0);
	}

	/** True if this is the draw list of that result. */
	public boolean isFor(RouteResult r)
	{
		return r != null && generation == r.getGeneration() && generation != -1;
	}

	/** The {@link RouteResult#getGeneration()} it belongs to; -1 for {@link #NONE}. */
	public long getGeneration()
	{
		return generation;
	}

	/** How many redraws made it (0 = the route's own tiles). */
	public int getVersion()
	{
		return version;
	}

	/** True if step {@code step} has tiles to draw. */
	public boolean hasTiles(int step)
	{
		return step >= 0 && step < tiles.length && tiles[step] != null;
	}

	/** Number of tiles drawn for that step (0 for steps without tiles). */
	public int tileCount(int step)
	{
		return hasTiles(step) ? tiles[step].length : 0;
	}

	/** Tile i (packed, core Tiles) drawn for that step. */
	public int tile(int step, int i)
	{
		return tiles[step][i];
	}

	/** A copy of the tiles drawn for that step (null if none). */
	public int[] tiles(int step)
	{
		return hasTiles(step) ? tiles[step].clone() : null;
	}

	/** This draw list with the step's line replaced (same length); the arrays are copied, nothing shared is changed. */
	public DrawPath withTiles(int step, int[] line)
	{
		if (!hasTiles(step) || line.length != tiles[step].length)
		{
			throw new IllegalArgumentException("a redrawn line keeps its step's length");
		}
		int[][] t = tiles.clone();
		t[step] = line.clone();
		return new DrawPath(generation, t, version + 1);
	}
}
