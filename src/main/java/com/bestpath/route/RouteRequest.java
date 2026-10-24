package com.bestpath.route;

import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Where the player wants to go: one destination tile, or (for a clue step)
 * several places of which any one will do, with a title and notes for the
 * route's header, and what the player needs there ({@link DestinationNeeds},
 * e.g. a clue's outfit: items only in the bank make the route start at a bank).
 * It may have first stops ({@link Stop}): the route goes to each in turn, shows
 * each stop as its own card, then goes on (e.g. put the items taken out of a
 * STASH unit back before the next clue step, or kill the NPC that drops a key
 * clue's key before going to the chest).
 * Immutable.
 */
public final class RouteRequest
{
	/** Largest x or y a packed tile can hold (14 bits, see core Tiles). */
	public static final int MAX_COORD = 0x3FFF;

	private final int x;
	private final int y;
	private final int plane;
	/** Packed tiles; the first is (x, y, plane). */
	private final int[] targets;
	/** What the places are for ("Cryptic clue (beginner)"); null for a typed tile. */
	private final String title;
	private final List<String> notes;
	/** Tells two place requests apart (e.g. "clue cryptic-1233"); null for a typed tile. */
	private final String key;
	/**
	 * Items needed at the destination (not part of {@link #equals}: the same place with other needs is the same
	 * request).
	 */
	private final DestinationNeeds needs;
	/** Where to go first, in order; empty for none (not part of {@link #equals}, like the needs). */
	private final List<Stop> firstStops;
	/** A world-map tile: the map gives no plane, the route search picks it ({@link #fromMap}). */
	private final boolean planeFromMap;

	/**
	 * A stop the route makes before its destination: a STASH unit to put the items back in, or the NPC to kill for a
	 * key clue's key. Its card has the title and lines given here, and its id: "Skip" on the card skips the stop.
	 * Immutable.
	 */
	public static final class Stop
	{
		private final String id;
		private final int tile;
		private final String title;
		private final List<String> lines;
		private final RouteStep.Kind kind;

		/** A STASH put-back stop ({@link RouteStep.Kind#STASH}). */
		public Stop(String id, int tile, String title, List<String> lines)
		{
			this(id, tile, title, lines, RouteStep.Kind.STASH);
		}

		/**
		 * @param id    the card's id for Skip (like a transport id; never a real transport's)
		 * @param tile  where to go (packed, core Tiles); the search stops next to it when it is blocked
		 * @param title the card's title ("Put the items back in the STASH unit (Draynor Village market)")
		 * @param lines the card's notes (the items)
		 * @param kind  the card's kind: {@link RouteStep.Kind#STASH} or {@link RouteStep.Kind#KILL}
		 */
		public Stop(String id, int tile, String title, List<String> lines, RouteStep.Kind kind)
		{
			this.id = Objects.requireNonNull(id);
			check(Tiles.x(tile), Tiles.y(tile), Tiles.plane(tile));
			if (!kind.isStop())
			{
				throw new IllegalArgumentException("Not a stop kind: " + kind);
			}
			this.tile = tile;
			this.title = Objects.requireNonNull(title);
			this.lines = Collections.unmodifiableList(List.copyOf(lines));
			this.kind = kind;
		}

		public String getId()
		{
			return id;
		}

		public RouteStep.Kind getKind()
		{
			return kind;
		}

		public int getTile()
		{
			return tile;
		}

		public String getTitle()
		{
			return title;
		}

		public List<String> getLines()
		{
			return lines;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Stop && ((Stop) o).id.equals(id) && ((Stop) o).tile == tile && ((Stop) o).title.equals(title)
				&& ((Stop) o).lines.equals(lines) && ((Stop) o).kind == kind;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(id, tile, title, lines, kind);
		}
	}

	public RouteRequest(int x, int y, int plane)
	{
		this(x, y, plane, false);
	}

	private RouteRequest(int x, int y, int plane, boolean planeFromMap)
	{
		check(x, y, plane);
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.targets = new int[]{Tiles.pack(x, y, plane)};
		this.title = null;
		this.notes = List.of();
		this.key = null;
		this.needs = DestinationNeeds.NONE;
		this.firstStops = List.of();
		this.planeFromMap = planeFromMap;
	}

	/**
	 * A tile clicked on the world map, which gives no plane: the route search picks it (core
	 * {@code PluginRouter.mapPlane}), since the ground is not always on plane 0. Until then the plane is 0.
	 */
	public static RouteRequest fromMap(int x, int y)
	{
		return new RouteRequest(x, y, 0, true);
	}

	/** True for a {@link #fromMap} tile, whose plane the route search picks. */
	public boolean isPlaneFromMap()
	{
		return planeFromMap;
	}

	/** This map tile on the plane picked for it: a plain one-tile request. */
	public RouteRequest onPlane(int pickedPlane)
	{
		return new RouteRequest(x, y, pickedPlane);
	}

	private RouteRequest(String key, String title, List<String> notes, int[] targets)
	{
		this(key, title, notes, targets, DestinationNeeds.NONE);
	}

	private RouteRequest(String key, String title, List<String> notes, int[] targets, DestinationNeeds needs)
	{
		this.targets = targets.clone();
		this.x = Tiles.x(targets[0]);
		this.y = Tiles.y(targets[0]);
		this.plane = Tiles.plane(targets[0]);
		for (int t : targets)
		{
			check(Tiles.x(t), Tiles.y(t), Tiles.plane(t));
		}
		this.title = title;
		this.notes = Collections.unmodifiableList(List.copyOf(notes));
		this.key = key;
		this.needs = needs == null ? DestinationNeeds.NONE : needs;
		this.firstStops = List.of();
		this.planeFromMap = false;
	}

	private RouteRequest(RouteRequest r, DestinationNeeds needs)
	{
		this(r, needs, r.firstStops);
	}

	private RouteRequest(RouteRequest r, DestinationNeeds needs, List<Stop> firstStops)
	{
		this.x = r.x;
		this.y = r.y;
		this.plane = r.plane;
		this.targets = r.targets;
		this.title = r.title;
		this.notes = r.notes;
		this.key = r.key;
		this.needs = needs == null ? DestinationNeeds.NONE : needs;
		this.firstStops = List.copyOf(firstStops);
		this.planeFromMap = r.planeFromMap;
	}

	/** This request with a stop before its destination (null: none). */
	public RouteRequest withFirstStop(Stop stop)
	{
		return new RouteRequest(this, needs, stop == null ? List.of() : List.of(stop));
	}

	/** This request with these stops before its destination, in order (nulls left out; empty: none). */
	public RouteRequest withFirstStops(List<Stop> stops)
	{
		List<Stop> out = new ArrayList<>();
		for (Stop s : stops)
		{
			if (s != null)
			{
				out.add(s);
			}
		}
		return new RouteRequest(this, needs, out);
	}

	/** This request without its first stops (itself when it has none). */
	public RouteRequest withoutFirstStop()
	{
		return firstStops.isEmpty() ? this : new RouteRequest(this, needs, List.of());
	}

	/** Where the route goes first, or null. */
	public Stop getFirstStop()
	{
		return firstStops.isEmpty() ? null : firstStops.get(0);
	}

	/** Where the route goes first, in order (empty: nowhere). */
	public List<Stop> getFirstStops()
	{
		return firstStops;
	}

	/** True when the only place is the one first stop itself: the route ends with the stop's card. */
	public boolean isStopOnly()
	{
		return firstStops.size() == 1 && targets.length == 1 && targets[0] == firstStops.get(0).tile;
	}

	/** This request with these items needed at the destination (a clue step's needs). */
	public RouteRequest withNeeds(DestinationNeeds destinationNeeds)
	{
		return new RouteRequest(this, destinationNeeds);
	}

	/** What the player needs at the destination ({@link DestinationNeeds#NONE} for a typed tile). */
	public DestinationNeeds getNeeds()
	{
		return needs;
	}

	/**
	 * Several places; the route goes to whichever is cheapest to reach.
	 *
	 * @param key     tells this request apart from others (same key and places = the same destination)
	 * @param title   shown as the route's header
	 * @param notes   shown under the header
	 * @param targets packed tiles ({@link Tiles#pack}), at least one
	 */
	public static RouteRequest places(String key, String title, List<String> notes, int[] targets)
	{
		if (targets == null || targets.length == 0)
		{
			throw new IllegalArgumentException("No place to go to");
		}
		return new RouteRequest(Objects.requireNonNull(key), Objects.requireNonNull(title), notes, targets);
	}

	private static void check(int x, int y, int plane)
	{
		if (x < 0 || x > MAX_COORD || y < 0 || y > MAX_COORD)
		{
			throw new IllegalArgumentException("X and y must be between 0 and " + MAX_COORD);
		}
		if (plane < 0 || plane > 3)
		{
			throw new IllegalArgumentException("Plane must be 0, 1, 2 or 3");
		}
	}

	/**
	 * Reads what the player typed: "x, y" or "x, y, plane" (commas and/or spaces).
	 *
	 * @throws IllegalArgumentException with a message meant for the player
	 */
	public static RouteRequest parse(String text)
	{
		String t = text == null ? "" : text.trim();
		if (t.isEmpty())
		{
			throw new IllegalArgumentException("Type a tile: x, y, plane");
		}
		String[] parts = t.split("[\\s,]+");
		if (parts.length < 2 || parts.length > 3)
		{
			throw new IllegalArgumentException("Use x, y or x, y, plane");
		}
		int[] v = new int[3];
		for (int i = 0; i < parts.length; i++)
		{
			try
			{
				v[i] = Integer.parseInt(parts[i]);
			}
			catch (NumberFormatException e)
			{
				throw new IllegalArgumentException("Not a whole number: " + parts[i]);
			}
		}
		return new RouteRequest(v[0], v[1], v[2]);
	}

	/** The (first) destination tile. */
	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getPlane()
	{
		return plane;
	}

	/** Every place (packed tiles); one for a typed tile. */
	public int[] getTargets()
	{
		return targets.clone();
	}

	public int targetCount()
	{
		return targets.length;
	}

	/** True for a request made with {@link #places} (it has a title and notes). */
	public boolean hasTitle()
	{
		return title != null;
	}

	/** The header ("Cryptic clue (beginner)"), or null for a typed tile. */
	public String getTitle()
	{
		return title;
	}

	public List<String> getNotes()
	{
		return notes;
	}

	public String getKey()
	{
		return key;
	}

	/** "3222, 3218, 0" ("3222, 3218" for a map tile, whose plane is not picked yet), or the title. */
	public String label()
	{
		return title != null ? title : planeFromMap ? x + ", " + y : tileLabel(x, y, plane);
	}

	/** "3222, 3218, 0" */
	public static String tileLabel(int x, int y, int plane)
	{
		return x + ", " + y + ", " + plane;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof RouteRequest))
		{
			return false;
		}
		RouteRequest r = (RouteRequest) o;
		return Objects.equals(key, r.key) && Arrays.equals(targets, r.targets) && planeFromMap == r.planeFromMap;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(key, Arrays.hashCode(targets), planeFromMap);
	}

	@Override
	public String toString()
	{
		return label();
	}
}
