package com.bestpath.core.data;

import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The places by kind for "go to nearest": per kind of map icon (altar, anvil, furnace, ...) its name, its picture (the
 * game's sprite id) and every tile the game shows that icon on. The icons and their tiles come from the game cache;
 * their names are written by hand (the cache has none). Immutable.
 *
 * <p>A map icon often sits on a tile no one can stand on (a furnace, the middle of a building): the route search
 * already goes to the nearest tile that can be reached and has it in sight ({@link
 * com.bestpath.core.path.RouteDestination}).
 *
 * <p>The banks are not repeated here: the kind {@value #BANK} has no tiles in this table, and {@link #withBanks} takes
 * them from {@link BankList} (the tiles a player banks from).
 *
 * <p>Text form (the plugin's optional {@value #FILE}): {@code #} comment lines, the header {@value #HEADER}, then one
 * tab-separated line per kind: name, sprite id (-1: none), tiles ({@code x,y,plane} separated by spaces).
 */
public final class PlaceKinds
{
	/** The plugin's optional data file. */
	public static final String FILE = "places.tsv";
	public static final String HEADER = "kind\tsprite\ttiles";
	/** The kind whose places are the banks ({@link BankList}). */
	public static final String BANK = "Bank";

	/** One kind of place. */
	public static final class Kind
	{
		final String name;
		final int sprite;
		final int[] tiles;

		public Kind(String name, int sprite, int[] tiles)
		{
			if (name == null || name.isBlank())
			{
				throw new IllegalArgumentException("a kind needs a name");
			}
			this.name = name.trim();
			this.sprite = sprite;
			this.tiles = tiles.clone();
		}

		/** As the sidebar shows it ("Altar"). */
		public String getName()
		{
			return name;
		}

		/** The map icon's picture: a game sprite id (RuneLite SpriteID), -1 none. */
		public int getSprite()
		{
			return sprite;
		}

		/** Where it is, packed ({@link Tiles}); empty for {@link #BANK} until {@link #withBanks}. */
		public int[] getTiles()
		{
			return tiles.clone();
		}
	}

	public static final PlaceKinds NONE = new PlaceKinds(List.of());

	private final List<Kind> kinds;

	/** Kinds in the order given; two with the same name (ignoring case) are not allowed. */
	public PlaceKinds(List<Kind> kinds)
	{
		List<String> seen = new ArrayList<>();
		for (Kind k : kinds)
		{
			String n = k.name.toLowerCase(Locale.ROOT);
			if (seen.contains(n))
			{
				throw new IllegalArgumentException("two kinds named " + k.name);
			}
			seen.add(n);
		}
		this.kinds = Collections.unmodifiableList(new ArrayList<>(kinds));
	}

	public List<Kind> getKinds()
	{
		return kinds;
	}

	public boolean isEmpty()
	{
		return kinds.isEmpty();
	}

	/** The kind with this name (ignoring case), or null. */
	public Kind find(String name)
	{
		for (Kind k : kinds)
		{
			if (k.name.equalsIgnoreCase(name))
			{
				return k;
			}
		}
		return null;
	}

	/**
	 * The kinds a player can go to: those with places, the {@value #BANK} kind given every tile of {@code banks} a player
	 * banks from (each once; whether the player may use a bank is for the route search). A kind without places is left
	 * out (no banks: no {@value #BANK}).
	 */
	public PlaceKinds withBanks(BankList banks)
	{
		List<Kind> out = new ArrayList<>();
		for (Kind k : kinds)
		{
			if (k.name.equals(BANK))
			{
				LinkedHashSet<Integer> tiles = new LinkedHashSet<>();
				for (BankList.Bank b : banks.getBanks())
				{
					for (int t : b.getUse())
					{
						tiles.add(t);
					}
				}
				k = new Kind(k.name, k.sprite, tiles.stream().mapToInt(Integer::intValue).toArray());
			}
			if (k.tiles.length > 0)
			{
				out.add(k);
			}
		}
		return new PlaceKinds(out);
	}

	/** Reads the text form; a line that can't be read throws, naming its number. */
	public static PlaceKinds parse(String text)
	{
		List<Kind> out = new ArrayList<>();
		String[] lines = text.split("\r?\n");
		for (int i = 0; i < lines.length; i++)
		{
			String line = lines[i];
			if (line.isBlank() || line.startsWith("#") || line.startsWith(HEADER))
			{
				continue;
			}
			String[] c = line.split("\t", -1);
			if (c.length < 3)
			{
				throw new IllegalArgumentException("places line " + (i + 1) + ": 3 columns needed: " + line);
			}
			List<Integer> tiles = new ArrayList<>();
			for (String t : c[2].trim().split(" +"))
			{
				if (t.isEmpty())
				{
					continue;
				}
				String[] xyz = t.split(",");
				if (xyz.length != 3)
				{
					throw new IllegalArgumentException("places line " + (i + 1) + ": tile x,y,plane expected: " + t);
				}
				tiles.add(Tiles.pack(Integer.parseInt(xyz[0].trim()), Integer.parseInt(xyz[1].trim()), Integer.parseInt(xyz[2].trim())));
			}
			out.add(new Kind(c[0], Integer.parseInt(c[1].trim()), tiles.stream().mapToInt(Integer::intValue).toArray()));
		}
		return new PlaceKinds(out);
	}
}
