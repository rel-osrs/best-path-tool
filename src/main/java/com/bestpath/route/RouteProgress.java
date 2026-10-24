package com.bestpath.route;

import com.bestpath.core.path.Tiles;
import java.util.List;

/**
 * Follows the player along one found route, one game tick at a time: which
 * step is current, whether the player strayed off the route, and arrival.
 * Pure (positions in, events out), so it can be tested on hand-made routes.
 * One thread only (the plugin calls it on the client thread at each game tick;
 * a few thousand tile comparisons, never in render()).
 *
 * <p>Rules (distances are in tiles, the larger of the x and y difference,
 * same plane only):
 * <ul>
 * <li>On a walk: the step stays current while the player is within
 * {@link #NEAR} of its tiles; within {@link #AT_TRANSPORT} of its last tile, a
 * following transport becomes current ("now climb the ladder").</li>
 * <li>On a transport: it stays current while the player is within
 * {@link #AT_TRANSPORT} of where it is used; standing on (or next to) its
 * landing, closer to the landing than to the start, moves on to the next
 * step.</li>
 * <li>Anywhere on a later step (teleported, took a shortcut the route had
 * later): that step becomes current. Progress never goes back.</li>
 * <li>Strayed: off the route and either further than the re-route distance
 * (a player setting, {@link #DEFAULT_STRAY_DISTANCE}) from everything still
 * ahead (not while a teleport, usable anywhere, is current), or that far from
 * where they were last on the route (at first where it was found,
 * {@link #setOrigin}), for {@link #STRAY_TICKS} ticks in a row (more while a
 * transport is current: boats and cutscenes move the player around).</li>
 * <li>Arrived: on the destination or the route's last tile, or at most the
 * arrival range (a player setting, {@link #DEFAULT_ARRIVE_STEPS}) of legal
 * steps from either ({@link ArriveArea}: never through a wall; for routes
 * that don't know those tiles: that many tiles from the destination while
 * the last step is current). Range 0 = only those two tiles.</li>
 * </ul>
 */
public final class RouteProgress
{
	/** Within this many tiles of a walk's tiles counts as on that walk. */
	public static final int NEAR = 2;
	/** Within this many tiles of where a transport is used (or of a walk's last tile) counts as there. */
	public static final int AT_TRANSPORT = 1;
	/** Further than this from every tile still ahead counts as off the route, unless the player set another. */
	public static final int DEFAULT_STRAY_DISTANCE = 10;
	/** Off the route this many ticks in a row = strayed (search again from here). */
	public static final int STRAY_TICKS = 3;
	/** While a transport step is current, extra ticks (plus the step's own ticks) before straying counts. */
	public static final int TRANSPORT_GRACE_TICKS = 10;
	/**
	 * Within this many legal steps of the destination (or the route's last tile) = arrived, unless the player set
	 * another.
	 */
	public static final int DEFAULT_ARRIVE_STEPS = 10;

	private static final int FAR = Integer.MAX_VALUE;

	/** What one update found. */
	public enum Event
	{
		NONE,
		/** The current step changed (or, in a walk, the walked part grew: see {@link #getTileIndex}). */
		STEP_CHANGED,
		/** Off the route long enough: search again from the player's tile. Reported once, then counting restarts. */
		STRAYED,
		/** At the destination. */
		ARRIVED
	}

	private final RouteResult route;
	private final List<RouteStep> steps;
	private final int destination;
	private final int lastTile;
	private int step;
	private int tileIndex;
	private int offTicks;
	private int strayDistance = DEFAULT_STRAY_DISTANCE;
	private int arriveSteps = DEFAULT_ARRIVE_STEPS;
	/**
	 * Where the player was last on the route (packed tile): at first where the route was found ({@link #setOrigin}),
	 * then every tick the player is on it. -1 = not known.
	 */
	private int origin = -1;
	/** The walk lines as drawn (redrawn from the player's tile, {@link RouteRedraw}); null = the steps' own tiles. */
	private DrawPath drawn;

	/** @param route a READY result with at least one step */
	public RouteProgress(RouteResult route)
	{
		if (route.getStatus() != RouteStatus.READY || route.getSteps().isEmpty() || route.getRequest() == null)
		{
			throw new IllegalArgumentException("progress needs a found route with steps");
		}
		this.route = route;
		this.steps = route.getSteps();
		// for several places (a clue step), the one this route goes to
		this.destination = route.getDestination();
		int last = -1;
		for (int i = steps.size() - 1; i >= 0 && last == -1; i--)
		{
			last = steps.get(i).getEndTile();
		}
		this.lastTile = last;
	}

	public RouteResult getRoute()
	{
		return route;
	}

	/**
	 * Where the player stood when this route was found (packed tile; -1 = not known). Off the route, walking the
	 * re-route distance away from where the player was last on it (at first this tile) searches again.
	 */
	public void setOrigin(int tile)
	{
		this.origin = tile;
	}

	/**
	 * The player's settings: off the route further than {@code strayDistance} tiles (at least 1) means search again;
	 * within {@code arriveSteps} legal steps of the end (0 to {@link ArriveArea#MAX_STEPS}) means arrived.
	 */
	public void setLimits(int strayDistance, int arriveSteps)
	{
		this.strayDistance = Math.max(1, strayDistance);
		this.arriveSteps = Math.max(0, Math.min(ArriveArea.MAX_STEPS, arriveSteps));
	}

	public int getStep()
	{
		return step;
	}

	public int getTileIndex()
	{
		return tileIndex;
	}

	/** The current progress as the immutable value overlays and the sidebar read. */
	public Progress progress()
	{
		return new Progress(route.getGeneration(), step, tileIndex);
	}

	/**
	 * One game tick with the player at this real-world tile, following the walks as they are drawn now ({@code drawn},
	 * when it is for this route; null: the steps' own tiles): a redrawn line has the walk's length, so tile indexes stay
	 * the same, and the player who walks the drawn line is on the walk.
	 */
	public Event update(int x, int y, int plane, DrawPath drawn)
	{
		this.drawn = drawn != null && drawn.isFor(route) ? drawn : null;
		int p = Tiles.pack(x, y, plane);
		if (arrived(p))
		{
			offTicks = 0;
			return Event.ARRIVED;
		}

		int target = locate(p);
		if (target == -1)
		{
			int limit = STRAY_TICKS;
			RouteStep cur = steps.get(step);
			// a teleport (usable anywhere) is next: wherever the player waits near here is fine
			boolean teleportNext = cur.getKind().isTransport() && cur.getMarkerTile() == -1;
			if (cur.getKind().isTransport() && !teleportNext)
			{
				limit += TRANSPORT_GRACE_TICKS + Math.max(0, cur.getTicks());
			}
			boolean walkedAway = origin != -1 && dist(p, origin) > strayDistance;
			if (walkedAway || !teleportNext && distanceAhead(p) > strayDistance)
			{
				if (++offTicks >= limit)
				{
					offTicks = 0;
					return Event.STRAYED;
				}
			}
			else
			{
				offTicks = 0;
			}
			return Event.NONE;
		}
		offTicks = 0;
		origin = p; // on the route: walking away is measured from here

		boolean changed = target != step;
		int idx = nearestTile(target, p);
		if (changed)
		{
			step = target;
			tileIndex = Math.max(0, idx);
			return Event.STEP_CHANGED;
		}
		if (idx > tileIndex)
		{
			tileIndex = idx;
			return Event.STEP_CHANGED;
		}
		return Event.NONE;
	}

	/**
	 * On the destination or the route's last tile; or at most the arrival range of legal steps from either, never
	 * through a wall (when the route knows those tiles), else that many tiles from the destination while the last step
	 * is current.
	 */
	private boolean arrived(int p)
	{
		if (p == destination || (lastTile != -1 && p == lastTile))
		{
			return true;
		}
		if (arriveSteps == 0)
		{
			return false;
		}
		if (route.knowsArriveArea())
		{
			int s = route.arriveSteps(p);
			return s >= 0 && s <= arriveSteps;
		}
		return dist(p, destination) <= arriveSteps && step == steps.size() - 1;
	}

	/** The step the player is on now (never before the current one), or -1 if on none of them. */
	private int locate(int p)
	{
		for (int j = step; j < steps.size(); j++)
		{
			RouteStep s = steps.get(j);
			if (s.getKind().isTransport())
			{
				if (pastTransport(s, p))
				{
					return Math.min(j + 1, steps.size() - 1);
				}
				if (s.getMarkerTile() != -1 && dist(p, s.getMarkerTile()) <= AT_TRANSPORT)
				{
					return j;
				}
			}
			else if (tilesDistance(j, p) <= NEAR)
			{
				return walkEndReached(j, p) ? j + 1 : j;
			}
		}
		return -1;
	}

	/** On (or next to) a transport's landing, and closer to it than to where it is used. */
	private static boolean pastTransport(RouteStep s, int p)
	{
		int end = s.getEndTile();
		if (end == -1)
		{
			return false;
		}
		int toEnd = dist(p, end);
		int toStart = s.getMarkerTile() == -1 ? FAR : dist(p, s.getMarkerTile());
		return toEnd <= AT_TRANSPORT && toEnd < toStart;
	}

	/** Near the last tile of walk j, and a transport comes next. */
	private boolean walkEndReached(int j, int p)
	{
		return j + 1 < steps.size() && steps.get(j + 1).getKind().isTransport() && tileCount(j) > 0
			&& dist(p, tile(j, tileCount(j) - 1)) <= AT_TRANSPORT;
	}

	/** Tiles of step j as drawn now (a walk may be redrawn), else as the step has them. */
	private int tileCount(int j)
	{
		return drawn != null && drawn.hasTiles(j) ? drawn.tileCount(j) : steps.get(j).tileCount();
	}

	private int tile(int j, int i)
	{
		return drawn != null && drawn.hasTiles(j) ? drawn.tile(j, i) : steps.get(j).tile(i);
	}

	/** Distance to everything still ahead: the current and later steps, and where the step before ended. */
	private int distanceAhead(int p)
	{
		int best = FAR;
		if (step > 0 && steps.get(step - 1).getEndTile() != -1)
		{
			best = dist(p, steps.get(step - 1).getEndTile());
		}
		for (int j = step; j < steps.size(); j++)
		{
			RouteStep s = steps.get(j);
			best = Math.min(best, tilesDistance(j, p));
			if (s.getMarkerTile() != -1)
			{
				best = Math.min(best, dist(p, s.getMarkerTile()));
			}
			if (s.getEndTile() != -1)
			{
				best = Math.min(best, dist(p, s.getEndTile()));
			}
		}
		return best;
	}

	private int tilesDistance(int j, int p)
	{
		int best = FAR;
		for (int i = 0; i < tileCount(j); i++)
		{
			best = Math.min(best, dist(p, tile(j, i)));
		}
		return best;
	}

	/** Index of the step's tile nearest to p (the first of equally near ones), or -1 if it has no tiles. */
	private int nearestTile(int j, int p)
	{
		int best = FAR;
		int at = -1;
		for (int i = 0; i < tileCount(j); i++)
		{
			int d = dist(p, tile(j, i));
			if (d < best)
			{
				best = d;
				at = i;
			}
		}
		return at;
	}

	/** Tiles apart (the larger of the x and y difference), or a huge number on different planes. */
	static int dist(int a, int b)
	{
		if (Tiles.plane(a) != Tiles.plane(b))
		{
			return FAR;
		}
		return Math.max(Math.abs(Tiles.x(a) - Tiles.x(b)), Math.abs(Tiles.y(a) - Tiles.y(b)));
	}
}
