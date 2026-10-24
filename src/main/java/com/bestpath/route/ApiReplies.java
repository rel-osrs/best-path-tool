package com.bestpath.route;

import com.bestpath.api.BestPathApi;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * The data of the messages Best Path sends through the public API ({@link BestPathApi}, docs/API.md), made from its
 * routes. The route keys are Shortest Path's ({@code reached}, {@code start}, {@code target}, {@code path},
 * {@code transports}, {@code closest}, {@code cost}, {@code reason}) plus Best Path's {@code steps}. Pure: maps of
 * RuneLite {@link WorldPoint}s, text, numbers and lists, which any plugin can read.
 */
public final class ApiReplies
{
	private ApiReplies()
	{
	}

	/** A {@code result} for a query: the search's answer, searched from {@code start} (packed, core Tiles). */
	public static Map<String, Object> result(Object id, RouteResult r, int start)
	{
		Map<String, Object> m = base(id);
		switch (r.getStatus())
		{
			case READY:
				route(m, r, start);
				m.put(BestPathApi.REASON, BestPathApi.REASON_TARGET_REACHED);
				return m;
			case NO_ROUTE:
				return failure(id, BestPathApi.REASON_SEARCH_EXHAUSTED, start, r.getMessage());
			case NO_DATA:
				return failure(id, BestPathApi.REASON_NO_DATA, start, r.getMessage());
			default:
				return failure(id, BestPathApi.REASON_ERROR, start, r.getMessage());
		}
	}

	/** A {@code result} without a route (a bad query, no data, busy...); {@code start} -1 when not known. */
	public static Map<String, Object> failure(Object id, String reason, int start, String message)
	{
		Map<String, Object> m = base(id);
		m.put(BestPathApi.REACHED, false);
		m.put(BestPathApi.START, start == -1 ? null : point(start));
		m.put(BestPathApi.TARGET, null);
		m.put(BestPathApi.PATH_TILES, List.of());
		m.put(BestPathApi.TRANSPORTS, List.of());
		m.put(BestPathApi.STEPS, List.of());
		m.put(BestPathApi.CLOSEST, null);
		m.put(BestPathApi.COST, -1);
		m.put(BestPathApi.REASON, reason);
		m.put(BestPathApi.MESSAGE, message == null ? "" : message);
		return m;
	}

	/**
	 * A {@code status} about a route a plugin asked for: {@code state} is a STATUS_ value; a found route
	 * ({@code r} READY) also has the route keys.
	 */
	public static Map<String, Object> status(Object id, String source, String state, RouteResult r, int start)
	{
		Map<String, Object> m = base(id);
		m.put(BestPathApi.SOURCE, source);
		m.put(BestPathApi.STATUS_KEY, state);
		m.put(BestPathApi.MESSAGE, r == null ? "" : r.getMessage());
		if (r != null && r.getStatus() == RouteStatus.READY && BestPathApi.STATUS_FOUND.equals(state))
		{
			route(m, r, start);
		}
		return m;
	}

	/** A {@code status} with a text of its own (INVALID, DISABLED). */
	public static Map<String, Object> status(Object id, String source, String state, String message)
	{
		Map<String, Object> m = base(id);
		m.put(BestPathApi.SOURCE, source);
		m.put(BestPathApi.STATUS_KEY, state);
		m.put(BestPathApi.MESSAGE, message);
		return m;
	}

	/** A {@code progress}: the player is on step {@code step} of that plugin's route. */
	public static Map<String, Object> progress(Object id, String source, int step)
	{
		Map<String, Object> m = base(id);
		m.put(BestPathApi.SOURCE, source);
		m.put(BestPathApi.STEP_INDEX, step);
		return m;
	}

	/**
	 * A {@code currentTarget}: the route shown ({@code r}'s request; none when null), searched from {@code start}
	 * (packed, -1: not known).
	 */
	public static Map<String, Object> currentTarget(Object id, RouteRequest r, int start)
	{
		Map<String, Object> m = base(id);
		m.put(BestPathApi.SET, r != null);
		if (r != null)
		{
			m.put(BestPathApi.START, start == -1 ? null : point(start));
			List<WorldPoint> targets = new ArrayList<>();
			for (int t : r.getTargets())
			{
				targets.add(point(t));
			}
			m.put(BestPathApi.TARGET, targets);
		}
		return m;
	}

	/** A {@code pong}. */
	public static Map<String, Object> pong(Object id)
	{
		return base(id);
	}

	private static Map<String, Object> base(Object id)
	{
		Map<String, Object> m = new HashMap<>();
		m.put(BestPathApi.ID, id);
		m.put(BestPathApi.API_VERSION_KEY, BestPathApi.API_VERSION);
		return m;
	}

	/** The found route's keys. */
	private static void route(Map<String, Object> m, RouteResult r, int start)
	{
		List<WorldPoint> path = new ArrayList<>();
		List<Map<String, Object>> transports = new ArrayList<>();
		List<Map<String, Object>> steps = new ArrayList<>();
		int at = start;
		if (start != -1)
		{
			path.add(point(start));
		}
		for (RouteStep s : r.getSteps())
		{
			for (int i = 0; i < s.tileCount(); i++)
			{
				add(path, s.tile(i));
			}
			int end = s.getEndTile();
			if (s.getKind().isTravel())
			{
				int origin = s.getMarkerTile() != -1 ? s.getMarkerTile() : at;
				Map<String, Object> t = new HashMap<>();
				t.put(BestPathApi.ORIGIN, origin == -1 ? null : point(origin));
				t.put(BestPathApi.DESTINATION, end == -1 ? null : point(end));
				t.put(BestPathApi.OBJECT_INFO, objectInfo(s.getClick()));
				t.put(BestPathApi.DISPLAY_INFO, s.getTitle());
				transports.add(t);
				if (origin != -1)
				{
					add(path, origin);
				}
			}
			if (end != -1)
			{
				add(path, end);
				at = end;
			}
			Map<String, Object> step = new HashMap<>();
			step.put(BestPathApi.STEP_KIND, s.getKind().name());
			step.put(BestPathApi.STEP_TITLE, s.getTitle());
			step.put(BestPathApi.STEP_TICKS, s.getTicks());
			step.put(BestPathApi.STEP_END, end == -1 ? null : point(end));
			steps.add(step);
		}
		int destination = r.getDestination();
		m.put(BestPathApi.REACHED, true);
		m.put(BestPathApi.START, start == -1 ? null : point(start));
		m.put(BestPathApi.TARGET, destination == -1 ? null : point(destination));
		m.put(BestPathApi.PATH_TILES, path);
		m.put(BestPathApi.TRANSPORTS, transports);
		m.put(BestPathApi.STEPS, steps);
		m.put(BestPathApi.CLOSEST, destination == -1 ? null : point(destination));
		m.put(BestPathApi.COST, r.getTotalTicks());
		m.put(BestPathApi.MESSAGE, r.getMessage());
	}

	/** "Climb-up Ladder 16683" (Shortest Path's form), or null when the step isn't an object's option. */
	static String objectInfo(ClickHint c)
	{
		if (c == null || c.getObjectId() < 0 || c.getOption().isEmpty())
		{
			return null;
		}
		return c.getOption() + " " + c.getTarget() + " " + c.getObjectId();
	}

	/** Adds a tile unless it is the last one already. */
	private static void add(List<WorldPoint> path, int tile)
	{
		WorldPoint p = point(tile);
		if (path.isEmpty() || !path.get(path.size() - 1).equals(p))
		{
			path.add(p);
		}
	}

	static WorldPoint point(int tile)
	{
		return new WorldPoint(Tiles.x(tile), Tiles.y(tile), Tiles.plane(tile));
	}
}
