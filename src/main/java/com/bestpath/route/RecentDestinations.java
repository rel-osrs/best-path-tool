package com.bestpath.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The places the player routed to last, newest first: the Functions tab lists them, a click routes there again.
 * Immutable; saved per account as text ({@link #toText}). Clue routes are not kept (the "Use clue" button covers
 * those, and the clue is gone once done).
 */
public final class RecentDestinations
{
	/** How many are kept. */
	public static final int MAX = 10;
	public static final RecentDestinations NONE = new RecentDestinations(List.of());

	private final List<RouteRequest> places;

	private RecentDestinations(List<RouteRequest> places)
	{
		this.places = Collections.unmodifiableList(places);
	}

	/** Newest first. */
	public List<RouteRequest> list()
	{
		return places;
	}

	/**
	 * With {@code r} at the top (moved there when it is in the list already), the oldest dropped past {@link #MAX}.
	 * Only the place is kept: a request's needs and first stops belong to that route.
	 */
	public RecentDestinations with(RouteRequest r)
	{
		RouteRequest place = placeOnly(r);
		if (place == null)
		{
			return this;
		}
		List<RouteRequest> l = new ArrayList<>();
		l.add(place);
		for (RouteRequest p : places)
		{
			if (!p.equals(place) && l.size() < MAX)
			{
				l.add(p);
			}
		}
		return new RecentDestinations(l);
	}

	/** Without the one at {@code index} (the Functions tab's x). */
	public RecentDestinations without(int index)
	{
		if (index < 0 || index >= places.size())
		{
			return this;
		}
		List<RouteRequest> l = new ArrayList<>(places);
		l.remove(index);
		return new RecentDestinations(l);
	}

	/** True for a request worth keeping: a typed or picked tile, or a titled place that is not a clue's. */
	public static boolean keeps(RouteRequest r)
	{
		// a sea charting set's route changes as its tasks are done: the player uses "Chart this set" again instead
		return r != null && (r.getKey() == null || !r.getKey().startsWith("clue ") && !r.getKey().startsWith(ChartingProgress.SET_KEY))
			&& !r.isStopOnly();
	}

	private static RouteRequest placeOnly(RouteRequest r)
	{
		if (!keeps(r))
		{
			return null;
		}
		if (!r.hasTitle())
		{
			return r.isPlaneFromMap() ? RouteRequest.fromMap(r.getX(), r.getY()) : new RouteRequest(r.getX(), r.getY(), r.getPlane());
		}
		return RouteRequest.places(r.getKey(), r.getTitle(), r.getNotes(), r.getTargets());
	}

	/**
	 * One line per place: {@code tile <x> <y> <plane>}, {@code map <x> <y>}, or
	 * {@code place <key> TAB <title> TAB <packed tiles, comma separated>}.
	 */
	public String toText()
	{
		StringBuilder b = new StringBuilder();
		for (RouteRequest r : places)
		{
			if (!r.hasTitle())
			{
				b.append(r.isPlaneFromMap() ? "map " + r.getX() + " " + r.getY() : "tile " + r.getX() + " " + r.getY() + " " + r.getPlane());
			}
			else
			{
				StringBuilder tiles = new StringBuilder();
				for (int t : r.getTargets())
				{
					tiles.append(tiles.length() == 0 ? "" : ",").append(t);
				}
				b.append("place ").append(clean(r.getKey())).append('\t').append(clean(r.getTitle())).append('\t').append(tiles);
			}
			b.append('\n');
		}
		return b.toString();
	}

	/** Reads {@link #toText}; lines it cannot read are left out (null or empty: none). */
	public static RecentDestinations fromText(String text)
	{
		if (text == null || text.isEmpty())
		{
			return NONE;
		}
		RecentDestinations d = NONE;
		List<RouteRequest> read = new ArrayList<>();
		for (String line : text.split("\n"))
		{
			try
			{
				RouteRequest r = readLine(line.trim());
				if (r != null)
				{
					read.add(r);
				}
			}
			catch (RuntimeException e)
			{
				// a damaged line: left out
			}
		}
		for (int i = read.size() - 1; i >= 0; i--)
		{
			d = d.with(read.get(i)); // oldest first, so the newest ends on top
		}
		return d;
	}

	private static RouteRequest readLine(String line)
	{
		if (line.startsWith("tile "))
		{
			String[] p = line.substring(5).split(" ");
			return new RouteRequest(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
		}
		if (line.startsWith("map "))
		{
			String[] p = line.substring(4).split(" ");
			return RouteRequest.fromMap(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
		}
		if (line.startsWith("place "))
		{
			String[] p = line.substring(6).split("\t");
			String[] tiles = p[2].split(",");
			int[] targets = new int[tiles.length];
			for (int i = 0; i < tiles.length; i++)
			{
				targets[i] = Integer.parseInt(tiles[i]);
			}
			return RouteRequest.places(p[0], p[1], List.of(), targets);
		}
		return null;
	}

	private static String clean(String s)
	{
		return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof RecentDestinations && places.equals(((RecentDestinations) o).places);
	}

	@Override
	public int hashCode()
	{
		return places.hashCode();
	}
}
