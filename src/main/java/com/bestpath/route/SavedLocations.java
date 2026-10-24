package com.bestpath.route;

import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The player's own named places, up to {@link #MAX}. Immutable; in the order saved; saved per account as text
 * ({@link #toText}). Saving a tile already in the list gives it the new name.
 */
public final class SavedLocations
{
	/** How many the player may keep. */
	public static final int MAX = 10;
	/** The longest name kept (longer ones are cut). */
	public static final int NAME_MAX = 40;
	public static final SavedLocations NONE = new SavedLocations(List.of());

	/** One saved place. */
	public static final class Place
	{
		private final String name;
		private final int tile;

		Place(String name, int tile)
		{
			this.name = name;
			this.tile = tile;
		}

		public String getName()
		{
			return name;
		}

		/** "3222, 3218, 0" */
		public String tileLabel()
		{
			return RouteRequest.tileLabel(Tiles.x(tile), Tiles.y(tile), Tiles.plane(tile));
		}

		/** The route there: titled with the name (the recent destinations keep it under that name). */
		public RouteRequest request()
		{
			return RouteRequest.places("saved " + tile, name, List.of("Your saved location: " + tileLabel() + "."), new int[]{tile});
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Place && ((Place) o).tile == tile && ((Place) o).name.equals(name);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(name, tile);
		}
	}

	private final List<Place> places;

	private SavedLocations(List<Place> places)
	{
		this.places = Collections.unmodifiableList(places);
	}

	public List<Place> list()
	{
		return places;
	}

	public boolean isFull()
	{
		return places.size() >= MAX;
	}

	/** The index of the place saved at this tile, or -1. */
	public int indexOf(int tile)
	{
		for (int i = 0; i < places.size(); i++)
		{
			if (places.get(i).tile == tile)
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * With this place: a tile already saved is renamed in place; a new one goes at the end. Unchanged when the list is
	 * full and the tile is new. An empty name becomes the tile ("3222, 3218, 0").
	 */
	public SavedLocations with(String name, int tile)
	{
		Place p = new Place(cleanName(name, tile), tile);
		List<Place> l = new ArrayList<>(places);
		int at = indexOf(tile);
		if (at >= 0)
		{
			l.set(at, p);
		}
		else if (l.size() < MAX)
		{
			l.add(p);
		}
		else
		{
			return this;
		}
		return new SavedLocations(l);
	}

	public SavedLocations without(int index)
	{
		if (index < 0 || index >= places.size())
		{
			return this;
		}
		List<Place> l = new ArrayList<>(places);
		l.remove(index);
		return new SavedLocations(l);
	}

	/** The name as kept: one line, no tabs, at most {@link #NAME_MAX} characters; empty = the tile. */
	static String cleanName(String name, int tile)
	{
		String n = name == null ? "" : name.replaceAll("[\\t\\r\\n]+", " ").trim();
		if (n.length() > NAME_MAX)
		{
			n = n.substring(0, NAME_MAX).trim();
		}
		return n.isEmpty() ? RouteRequest.tileLabel(Tiles.x(tile), Tiles.y(tile), Tiles.plane(tile)) : n;
	}

	/** One line per place: {@code <x> <y> <plane> TAB <name>}. */
	public String toText()
	{
		StringBuilder b = new StringBuilder();
		for (Place p : places)
		{
			b.append(Tiles.x(p.tile)).append(' ').append(Tiles.y(p.tile)).append(' ').append(Tiles.plane(p.tile)).append('\t')
				.append(p.name).append('\n');
		}
		return b.toString();
	}

	/** Reads {@link #toText}; lines it cannot read are left out (null or empty: none). */
	public static SavedLocations fromText(String text)
	{
		SavedLocations s = NONE;
		if (text == null)
		{
			return s;
		}
		for (String line : text.split("\n"))
		{
			int tab = line.indexOf('\t');
			if (tab < 0)
			{
				continue;
			}
			try
			{
				String[] xyz = line.substring(0, tab).trim().split(" ");
				// the RouteRequest checks the numbers are a real tile (a damaged line throws and is left out)
				RouteRequest r = new RouteRequest(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
				s = s.with(line.substring(tab + 1), Tiles.pack(r.getX(), r.getY(), r.getPlane()));
			}
			catch (RuntimeException e)
			{
				// left out
			}
		}
		return s;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof SavedLocations && places.equals(((SavedLocations) o).places);
	}

	@Override
	public int hashCode()
	{
		return places.hashCode();
	}
}
