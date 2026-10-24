package com.bestpath.route;

import com.bestpath.api.BestPathApi;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * A {@code path} or {@code query} message of the public API ({@link BestPathApi}, docs/API.md), read: who sent it,
 * where to, and the header to show. Places are RuneLite {@link WorldPoint}s or whole numbers packed as
 * {@link BestPathApi#pack} does (Shortest Path's layout), alone or in a collection; any place that isn't one makes
 * the whole message invalid (nothing is half done). Immutable.
 */
public final class ApiRequest
{
	/** The source of a message without one. */
	public static final String NO_SOURCE = "another plugin";
	/** Longest source, title or note kept (longer text is cut). */
	static final int MAX_TEXT = 100;
	/** Most notes kept. */
	static final int MAX_NOTES = 5;
	/** Route keys of API routes start with this (then the source). */
	static final String KEY_PREFIX = "api: ";

	private final Object id;
	private final String source;
	/** Packed (core Tiles); empty when the message had no target (tests read it). */
	final int[] targets;
	/** Packed (core Tiles), or -1: where the player stands. */
	private final int start;
	private final String title;
	private final List<String> notes;
	/** Why the message is invalid, or null when it is fine. */
	private final String problem;

	private ApiRequest(Object id, String source, int[] targets, int start, String title, List<String> notes, String problem)
	{
		this.id = id;
		this.source = source;
		this.targets = targets;
		this.start = start;
		this.title = title;
		this.notes = notes;
		this.problem = problem;
	}

	/**
	 * Reads a message's data (null: an empty message). Never throws: a bad message comes back
	 * {@link #isValid() invalid}.
	 */
	public static ApiRequest read(Map<String, Object> data)
	{
		Map<String, Object> d = data == null ? Map.of() : data;
		Object id = d.get(BestPathApi.ID);
		String source = text(d.get(BestPathApi.SOURCE));
		source = source == null ? NO_SOURCE : source;
		String title = text(d.get(BestPathApi.TITLE));
		List<String> notes = notes(d.get(BestPathApi.NOTES));
		List<Integer> tiles = new ArrayList<>();
		String problem = places(d.get(BestPathApi.TARGET), tiles);
		int start = -1;
		Object s = d.get(BestPathApi.START);
		if (problem == null && s != null)
		{
			List<Integer> one = new ArrayList<>();
			problem = s instanceof Collection ? "The start must be one place" : places(s, one);
			start = problem == null ? one.get(0) : -1;
		}
		int[] targets = problem != null ? new int[0] : tiles.stream().mapToInt(Integer::intValue).toArray();
		return new ApiRequest(id, source, targets, start, title, notes, problem);
	}

	/** Adds the place(s) to {@code out} (duplicates once); returns why they are not places, or null. */
	private static String places(Object o, List<Integer> out)
	{
		if (o == null)
		{
			return null;
		}
		if (o instanceof Collection)
		{
			Collection<?> c = (Collection<?>) o;
			if (c.size() > BestPathApi.MAX_TARGETS)
			{
				return "More than " + BestPathApi.MAX_TARGETS + " places";
			}
			for (Object e : c)
			{
				if (e instanceof Collection)
				{
					return "A place inside a place list is a list";
				}
				String p = places(e, out);
				if (p != null)
				{
					return p;
				}
			}
			return null;
		}
		int x;
		int y;
		int plane;
		if (o instanceof WorldPoint)
		{
			WorldPoint p = (WorldPoint) o;
			x = p.getX();
			y = p.getY();
			plane = p.getPlane();
		}
		else if (o instanceof Integer && (Integer) o != BestPathApi.UNDEFINED)
		{
			int packed = (Integer) o;
			x = BestPathApi.unpackX(packed);
			y = BestPathApi.unpackY(packed);
			plane = BestPathApi.unpackPlane(packed);
		}
		else
		{
			return "Not a place: " + (o instanceof Integer ? "-1" : o.getClass().getSimpleName());
		}
		if (x < 0 || x > RouteRequest.MAX_COORD || y < 0 || y > RouteRequest.MAX_COORD || plane < 0 || plane > 3)
		{
			return "Not a place in the game world: " + x + ", " + y + ", " + plane;
		}
		int t = Tiles.pack(x, y, plane);
		if (!out.contains(t))
		{
			out.add(t);
		}
		return null;
	}

	/** Text (trimmed, cut to {@link #MAX_TEXT}), or null when not text or empty. */
	private static String text(Object o)
	{
		if (!(o instanceof String))
		{
			return null;
		}
		String s = ((String) o).trim();
		if (s.isEmpty())
		{
			return null;
		}
		return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT - 3) + "..." : s;
	}

	private static List<String> notes(Object o)
	{
		List<String> out = new ArrayList<>();
		if (o instanceof Collection)
		{
			for (Object e : (Collection<?>) o)
			{
				String s = text(e);
				if (s != null && out.size() < MAX_NOTES)
				{
					out.add(s);
				}
			}
		}
		else if (text(o) != null)
		{
			out.add(text(o));
		}
		return List.copyOf(out);
	}

	/** True when the message could be read (it may still have no target: {@link #hasTargets}). */
	public boolean isValid()
	{
		return problem == null;
	}

	/** Why the message is invalid ("Not a place: String"), or null. */
	public String getProblem()
	{
		return problem;
	}

	public boolean hasTargets()
	{
		return targets.length > 0;
	}

	/** The caller's id, as sent (any value, or null). */
	public Object getId()
	{
		return id;
	}

	/** The caller's name ({@link #NO_SOURCE} when it sent none). */
	public String getSource()
	{
		return source;
	}

	/** Packed tile (core Tiles) to search from, or -1: where the player stands. */
	public int getStart()
	{
		return start;
	}

	/**
	 * The route key of this source's routes: a new route from the same source with the same places is the same route.
	 */
	public String routeKey()
	{
		return KEY_PREFIX + source;
	}

	/**
	 * The route to show: the header is the title, or "Destination from" the source. Only for a valid message with
	 * targets.
	 */
	public RouteRequest toRoute()
	{
		if (!isValid() || !hasTargets())
		{
			throw new IllegalStateException("No place to go to");
		}
		String header = title != null ? title : "Destination from " + source;
		List<String> lines = new ArrayList<>(notes);
		if (lines.isEmpty())
		{
			lines.add("Set by " + source + ": " + (targets.length == 1
				? RouteRequest.tileLabel(Tiles.x(targets[0]), Tiles.y(targets[0]), Tiles.plane(targets[0]))
				: "the nearest of " + targets.length + " places") + ".");
		}
		return RouteRequest.places(routeKey(), header, lines, targets);
	}
}
