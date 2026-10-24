package com.bestpath.route;

import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.PlayerState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * What the worker publishes: the status, the steps and the draw lists the
 * overlays read every frame (precomputed here, so render() only projects
 * tiles). Immutable; shared between the worker, the client thread (overlays)
 * and the Swing thread (sidebar).
 */
public final class RouteResult
{
	private static final int[][] NO_SEGMENTS = new int[0][];
	private static final int[] NO_TILES = new int[0];

	private final long generation;
	private final RouteStatus status;
	private final String message;
	private final RouteRequest request;
	private final int totalTicks;
	private final List<RouteStep> steps;
	/** Path lines for the overlays: one array of packed tiles per walk/sail step, in route order. */
	private final int[][] segments;
	/** Packed tiles where transports are used (ladders, doors...), in route order. */
	private final int[] markers;
	/** Which step each segment / marker belongs to (overlays highlight the current step's). */
	private final int[] segmentSteps;
	private final int[] markerSteps;
	/** The packed tile the route goes to (for several places: the one chosen); -1 without a request. */
	private final int destination;
	/** What the search adds to the request's own notes (e.g. which of several places it chose). */
	private final List<String> notes;
	/**
	 * The tiles that count as arrived, each with its legal steps (walls, no corner cutting) to the route's end; null =
	 * not known (routes made without the map).
	 */
	private final ArriveArea arrive;
	/** How long the search took in milliseconds (measured by the route service), or -1 when not measured. */
	private final long searchMillis;
	/** Why there is no route (a NO_ROUTE result of the plugin's engine), or null. */
	private final NoRouteWhy noRouteWhy;
	/** Where the search time went, a line per part (Developer details); empty when not measured. */
	private final List<String> timeLines;
	/** The parts of {@link #timeLines} together, in milliseconds (the rest of {@link #searchMillis} is "other"). */
	private final long timedMillis;
	/** The card at the top of a route started on a boat, or null. */
	private final BoatCard boatCard;
	/**
	 * A "no route" whose why is still to be worked out: the route service publishes the result at once ("No route,
	 * finding out why..."), then works this out on the worker ({@link #explained()}) and publishes again. Null when
	 * nothing is left to work out.
	 */
	private final Supplier<NoRouteWhy> pendingWhy;

	private RouteResult(long generation, RouteStatus status, String message, RouteRequest request, int totalTicks,
		List<RouteStep> steps, int destination, List<String> notes, ArriveArea arrive)
	{
		this(generation, status, message, request, totalTicks, steps, destination, notes, arrive, -1, null);
	}

	private RouteResult(long generation, RouteStatus status, String message, RouteRequest request, int totalTicks,
		List<RouteStep> steps, int destination, List<String> notes, ArriveArea arrive, long searchMillis, NoRouteWhy noRouteWhy)
	{
		this(generation, status, message, request, totalTicks, steps, destination, notes, arrive, searchMillis, noRouteWhy,
			List.of(), 0, null, null);
	}

	private RouteResult(long generation, RouteStatus status, String message, RouteRequest request, int totalTicks,
		List<RouteStep> steps, int destination, List<String> notes, ArriveArea arrive, long searchMillis, NoRouteWhy noRouteWhy,
		List<String> timeLines, long timedMillis, BoatCard boatCard, Supplier<NoRouteWhy> pendingWhy)
	{
		this.generation = generation;
		this.pendingWhy = pendingWhy;
		this.boatCard = boatCard;
		this.timeLines = List.copyOf(timeLines);
		this.timedMillis = timedMillis;
		this.noRouteWhy = noRouteWhy;
		this.searchMillis = searchMillis;
		this.arrive = arrive;
		this.destination = destination != -1 || request == null ? destination
			: Tiles.pack(request.getX(), request.getY(), request.getPlane());
		this.notes = Collections.unmodifiableList(List.copyOf(notes));
		this.status = status;
		this.message = message == null ? "" : message;
		this.request = request;
		this.totalTicks = totalTicks;
		this.steps = Collections.unmodifiableList(List.copyOf(steps));

		List<int[]> segs = new ArrayList<>();
		List<Integer> segSteps = new ArrayList<>();
		int markerCount = 0;
		for (int si = 0; si < steps.size(); si++)
		{
			RouteStep s = steps.get(si);
			if (!s.getKind().isTransport() && s.tileCount() > 0)
			{
				int[] t = new int[s.tileCount()];
				for (int i = 0; i < t.length; i++)
				{
					t[i] = s.tile(i);
				}
				segs.add(t);
				segSteps.add(si);
			}
			if (s.getMarkerTile() != -1)
			{
				markerCount++;
			}
		}
		this.segments = segs.isEmpty() ? NO_SEGMENTS : segs.toArray(new int[0][]);
		int[] ss = segSteps.isEmpty() ? NO_TILES : new int[segSteps.size()];
		for (int i = 0; i < ss.length; i++)
		{
			ss[i] = segSteps.get(i);
		}
		this.segmentSteps = ss;
		int[] m = markerCount == 0 ? NO_TILES : new int[markerCount];
		int[] ms = markerCount == 0 ? NO_TILES : new int[markerCount];
		int k = 0;
		for (int si = 0; si < steps.size(); si++)
		{
			RouteStep s = steps.get(si);
			if (s.getMarkerTile() != -1)
			{
				ms[k] = si;
				m[k++] = s.getMarkerTile();
			}
		}
		this.markers = m;
		this.markerSteps = ms;
	}

	/** Nothing asked for. */
	public static RouteResult idle(long generation)
	{
		return new RouteResult(generation, RouteStatus.IDLE, "", null, -1, List.of(), -1, List.of(), null);
	}

	/** A status without steps (calculating, no route, no data, error) with the line the sidebar shows. */
	public static RouteResult status(long generation, RouteRequest request, RouteStatus status, String message)
	{
		if (status == RouteStatus.READY)
		{
			throw new IllegalArgumentException("use ready() for a found route");
		}
		return new RouteResult(generation, status, message, request, -1, List.of(), -1, List.of(), null);
	}

	/**
	 * "No route", its why worked out afterwards ({@link #isFindingWhy()}): the route service publishes this at once
	 * ("No route, finding out why..."), then {@link #explained()}.
	 */
	public static RouteResult noRouteFindingWhy(long generation, RouteRequest request, String message, List<String> notes,
		Supplier<NoRouteWhy> why)
	{
		return new RouteResult(generation, RouteStatus.NO_ROUTE, message, request, -1, List.of(), -1, notes, null, -1, null,
			List.of(), 0, null, why);
	}

	/** Why there is no route (NO_ROUTE from the plugin's engine), or null when not known (or not worked out yet). */
	public NoRouteWhy getNoRouteWhy()
	{
		return noRouteWhy;
	}

	/** True while the why of this "no route" is still to be worked out ("No route, finding out why..."). */
	public boolean isFindingWhy()
	{
		return pendingWhy != null;
	}

	/**
	 * The same result with its why worked out (route worker only: it runs core's diagnosis; null when it could not be
	 * worked out: the engine's own line is shown). Nothing to work out: this result.
	 */
	RouteResult explained()
	{
		if (pendingWhy == null)
		{
			return this;
		}
		return new RouteResult(generation, status, message, request, totalTicks, steps, destination, notes, arrive,
			searchMillis, pendingWhy.get(), timeLines, timedMillis, boatCard, null);
	}

	/** The same result with no why to work out (no-route explanations switched off): the engine's own line. */
	RouteResult withoutWhy()
	{
		return pendingWhy == null && noRouteWhy == null ? this : new RouteResult(generation, status, message, request,
			totalTicks, steps, destination, notes, arrive, searchMillis, null, timeLines, timedMillis, boatCard, null);
	}

	/**
	 * A found route to one of the request's places; the total is the sum of the steps' ticks.
	 *
	 * @param destination the packed tile chosen (-1 = the request's first)
	 * @param notes       for the header, after the request's own notes
	 * @param arrive      the tiles that count as arrived, each with its legal steps to the route's end (null = not known)
	 */
	public static RouteResult ready(long generation, RouteRequest request, List<RouteStep> steps, int destination,
		List<String> notes, ArriveArea arrive)
	{
		int total = 0;
		for (RouteStep s : steps)
		{
			total += s.getTicks();
		}
		return new RouteResult(generation, RouteStatus.READY, "", request, total, steps, destination, notes, arrive);
	}

	/** The same result with how long its search took (shown at the top, by the travel time). */
	public RouteResult withSearchMillis(long millis)
	{
		return new RouteResult(generation, status, message, request, totalTicks, steps, destination, notes, arrive,
			millis, noRouteWhy, timeLines, timedMillis, boatCard, pendingWhy);
	}

	/**
	 * The same route (same generation, steps and tiles) with its step cards made again for the items the player has
	 * now: the item pictures, staves and marks ({@link StepInfo#forPlayer}). Itself when no card would look different.
	 */
	public RouteResult withPlayer(PlayerState player)
	{
		if (status != RouteStatus.READY || player == null)
		{
			return this;
		}
		List<RouteStep> fresh = new ArrayList<>(steps.size());
		boolean changed = false;
		for (RouteStep s : steps)
		{
			StepInfo info = s.getInfo().forPlayer(player);
			if (info.sameCard(s.getInfo()))
			{
				fresh.add(s);
			}
			else
			{
				fresh.add(s.withInfo(info));
				changed = true;
			}
		}
		return !changed ? this : new RouteResult(generation, status, message, request, totalTicks, fresh, destination, notes,
			arrive, searchMillis, noRouteWhy, timeLines, timedMillis, boatCard, pendingWhy);
	}

	/**
	 * The same result with where its search time went (Developer details): a line per part, and those parts together
	 * in milliseconds.
	 */
	public RouteResult withTimes(List<String> lines, long millis)
	{
		return new RouteResult(generation, status, message, request, totalTicks, steps, destination, notes, arrive,
			searchMillis, noRouteWhy, lines, millis, boatCard, pendingWhy);
	}

	/** The same result with the boat card at its top (null: none). */
	public RouteResult withBoatCard(BoatCard card)
	{
		return new RouteResult(generation, status, message, request, totalTicks, steps, destination, notes, arrive,
			searchMillis, noRouteWhy, timeLines, timedMillis, card, pendingWhy);
	}

	/** The card at the top of a route started on a boat ("Park my boat", "Ocean only path"), or null. */
	public BoatCard getBoatCard()
	{
		return boatCard;
	}

	/** Where the search time went, a line per part; empty when not measured. */
	public List<String> getTimeLines()
	{
		return timeLines;
	}

	/** The parts of {@link #getTimeLines()} together, in milliseconds. */
	public long getTimedMillis()
	{
		return timedMillis;
	}

	/**
	 * The same result for another request (a route with a first stop is searched in parts, and each part's result
	 * names that part's request), with these header notes put first.
	 */
	RouteResult withRequest(RouteRequest other, List<String> notesFirst)
	{
		List<String> n = new ArrayList<>(notesFirst);
		n.addAll(notes);
		return new RouteResult(generation, status, message, other, totalTicks, steps, destination, n, arrive,
			searchMillis, noRouteWhy, timeLines, timedMillis, boatCard, pendingWhy);
	}

	/** The tiles that count as arrived (immutable), or null when not known. */
	ArriveArea arrive()
	{
		return arrive;
	}

	/** How long the search took in milliseconds, or -1 when not measured. */
	public long getSearchMillis()
	{
		return searchMillis;
	}

	/** "45 ms", "1.3 s": a search time for the sidebar. */
	public static String searchTime(long millis)
	{
		return millis < 1000 ? millis + " ms" : String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
	}

	/** True if the tiles that count as arrived are known ({@link ArriveArea}). */
	public boolean knowsArriveArea()
	{
		return arrive != null;
	}

	/** Legal steps from this packed tile to the route's end, or -1 when further or not known. */
	public int arriveSteps(int packed)
	{
		return arrive == null ? -1 : arrive.steps(packed);
	}

	/** Which request this answers; results of older requests are dropped. */
	public long getGeneration()
	{
		return generation;
	}

	public RouteStatus getStatus()
	{
		return status;
	}

	/** The status line's text for non-ready states (may be empty). */
	public String getMessage()
	{
		return message;
	}

	/** The destination, or null when idle. */
	public RouteRequest getRequest()
	{
		return request;
	}

	/** The packed tile the route goes to (for several places: the one chosen); -1 when idle. */
	public int getDestination()
	{
		return destination;
	}

	/** The search's notes for the header (e.g. "Nearest of 4 places"); may be empty. */
	public List<String> getNotes()
	{
		return notes;
	}

	/** Total ticks of a found route, -1 otherwise. */
	public int getTotalTicks()
	{
		return totalTicks;
	}

	public List<RouteStep> getSteps()
	{
		return steps;
	}

	public int segmentCount()
	{
		return segments.length;
	}

	public int segmentLength(int segment)
	{
		return segments[segment].length;
	}

	public int segmentTile(int segment, int i)
	{
		return segments[segment][i];
	}

	/** The index (in {@link #getSteps}) of the step this segment draws. */
	public int segmentStep(int segment)
	{
		return segmentSteps[segment];
	}

	/** The index of the step this marker belongs to. */
	public int markerStep(int i)
	{
		return markerSteps[i];
	}

	public int markerCount()
	{
		return markers.length;
	}

	public int marker(int i)
	{
		return markers[i];
	}

	/** "4 min 7 s" for a tick count (1 tick = 0.6 s). */
	public static String duration(int ticks)
	{
		int tenths = ticks * 6;
		int seconds = (tenths + 5) / 10;
		if (seconds < 60)
		{
			return seconds + " s";
		}
		int minutes = seconds / 60;
		int rest = seconds % 60;
		return rest == 0 ? minutes + " min" : minutes + " min " + rest + " s";
	}
}
