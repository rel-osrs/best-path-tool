package com.bestpath.core.path;

import com.bestpath.core.req.BankItem;
import java.util.Collections;
import java.util.List;

/** The result of a search: the steps, the total ticks, and how much work it took. Immutable. */
public final class Route
{
	/** One part of a route: a walk over tiles, or one transport. Immutable. */
	public static final class Step
	{
		public enum Kind
		{
			WALK,
			TRANSPORT,
			TELEPORT,
			/** Sailing: the tiles are the boat's sea tiles (plane 0). */
			SAIL,
			/**
			 * Using a bank at the start of the route: the tile is where the player stands; see
			 * {@link #getBankName()} and {@link #getTakeOut()}.
			 */
			BANK
		}

		private final Kind kind;
		private final int[] tiles;
		final Transport transport;
		private final int ticks;
		private final String bankName;
		private final List<BankItem> takeOut;
		/** A sail step: indexes into {@link #tiles} where a straight leg starts or ends (see {@link #getCorners()}). */
		private final int[] corners;
		/** A sail step: the corners exactly (x, y pairs in tiles; see {@link #getCoursePoints()}). */
		private final double[] points;

		Step(Kind kind, int[] tiles, Transport transport, int ticks)
		{
			this(kind, tiles, transport, ticks, NO_CORNERS, NO_POINTS);
		}

		/**
		 * A sail step: the boat's tiles, where its straight legs meet ({@link #getCorners()}) and those corners exactly
		 * ({@link #getCoursePoints()}).
		 */
		Step(Kind kind, int[] tiles, Transport transport, int ticks, int[] corners, double[] points)
		{
			this.kind = kind;
			this.tiles = tiles;
			this.transport = transport;
			this.ticks = ticks;
			this.bankName = null;
			this.takeOut = List.of();
			this.corners = corners;
			this.points = points;
		}

		/** A bank step: {@code tile} where the player stands, what to take out (in the order the route needs it). */
		Step(int tile, int ticks, String bankName, List<BankItem> takeOut)
		{
			this.kind = Kind.BANK;
			this.tiles = new int[]{tile};
			this.transport = null;
			this.ticks = ticks;
			this.bankName = bankName;
			this.takeOut = List.copyOf(takeOut);
			this.corners = NO_CORNERS;
			this.points = NO_POINTS;
		}

		private static final int[] NO_CORNERS = new int[0];
		private static final double[] NO_POINTS = new double[0];

		/**
		 * A sail step, whose course is straight legs in the boat's 16 headings: the indexes into
		 * {@link #getTiles()} of the course's first tile, each turn and its last tile; leg i runs straight
		 * from tile {@code corners[i]} to tile {@code corners[i + 1]} (in order; a leg shorter than a tile may start and
		 * end on one tile). Empty for every other step.
		 */
		public int[] getCorners()
		{
			return corners.clone();
		}

		/**
		 * A sail step: the corners of {@link #getCorners()} exactly, as x, y pairs in tiles (a tile's middle is its
		 * whole x / y; the boat's headings are compass points 22.5 degrees apart, so a turn need not be on a whole
		 * tile). Leg i runs from point i to point i + 1 in one of the boat's exact headings; the tiles
		 * are those legs sampled at most a tile apart. Empty for every other step.
		 */
		public double[] getCoursePoints()
		{
			return points.clone();
		}

		/** A bank step's bank ("Varrock west bank"); null for other steps. */
		public String getBankName()
		{
			return bankName;
		}

		/**
		 * A bank step: what later steps and the destination need from the bank, in words ("coins 30", "law runes
		 * x2"); empty for other steps.
		 */
		public List<String> getTakeOut()
		{
			return BankItem.labels(takeOut);
		}

		/**
		 * A bank step: what later steps and the destination need from the bank, each with its items and how many to
		 * take out; empty for other steps.
		 */
		public List<BankItem> getTakeOutItems()
		{
			return takeOut;
		}

		public Kind getKind()
		{
			return kind;
		}

		/**
		 * The tiles of a walk, first to last, each packed with {@link Tiles#pack};
		 * for a transport its landing tile (and its start tile first, if it has one).
		 */
		public int[] getTiles()
		{
			return tiles.clone();
		}

		/** The transport used, or null for a walk. */
		public Transport getTransport()
		{
			return transport;
		}

		public int getTicks()
		{
			return ticks;
		}
	}

	private final boolean found;
	private final int ticks;
	private final List<Step> steps;
	private final int expanded;
	private final long nanos;

	Route(boolean found, int ticks, List<Step> steps, int expanded, long nanos)
	{
		this.found = found;
		this.ticks = ticks;
		this.steps = Collections.unmodifiableList(steps);
		this.expanded = expanded;
		this.nanos = nanos;
	}

	public static Route notFound(int expanded, long nanos)
	{
		return new Route(false, -1, Collections.emptyList(), expanded, nanos);
	}

	/** False if the destination cannot be reached. */
	public boolean isFound()
	{
		return found;
	}

	/** Total ticks, or -1 if not found. */
	public int getTicks()
	{
		return ticks;
	}

	public List<Step> getSteps()
	{
		return steps;
	}

	/** Tiles taken off the search queue (a measure of work). */
	public int getExpanded()
	{
		return expanded;
	}

	public long getNanos()
	{
		return nanos;
	}
}
