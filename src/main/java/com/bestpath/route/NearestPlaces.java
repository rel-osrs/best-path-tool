package com.bestpath.route;

import com.bestpath.core.data.PlaceKinds;
import com.bestpath.core.path.BankFirst;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Go to nearest": a route to the nearest place of a kind (bank, altar, furnace, anvil, ...). Every place of that kind
 * is given to the search, which goes to whichever is quickest ({@link RouteRequest#places}). The places come from the
 * plugin data's {@link PlaceKinds} (the game's map icons; the banks from banks.tsv).
 */
public final class NearestPlaces
{
	/** Every nearest route's key starts with this; then the kind in lower case. */
	public static final String KEY = "nearest ";
	/** The nearest bank's key: the route search keeps only the banks the player may use ({@link #usable}). */
	public static final String BANK_KEY = KEY + PlaceKinds.BANK.toLowerCase(Locale.ROOT);

	private NearestPlaces()
	{
	}

	/** The route to the nearest place of {@code kind} ("nearest altar", "Nearest altar"); the kind has places. */
	public static RouteRequest request(PlaceKinds.Kind kind)
	{
		return RouteRequest.places(KEY + kind.getName().toLowerCase(Locale.ROOT), title(kind.getName()), List.of(),
			kind.getTiles());
	}

	/**
	 * "Nearest altar", "Nearest general store"; a name with capitals after its first letter stays as it is
	 * ("Nearest Makeover Mage").
	 */
	static String title(String kind)
	{
		String rest = kind.length() > 1 ? kind.substring(1) : "";
		boolean sentence = rest.equals(rest.toLowerCase(Locale.ROOT));
		return "Nearest " + (sentence ? kind.toLowerCase(Locale.ROOT) : kind);
	}

	/**
	 * For the nearest bank: the indexes (in {@code targets}) of the tiles the player may bank from ({@code usable}: the
	 * banks whose needs they meet). Null when nothing is to be left out, or nothing would be left (then every bank is
	 * tried, and the route tells what it needs).
	 */
	public static int[] usable(int[] targets, BankFirst.Banks usable)
	{
		List<Integer> keep = new ArrayList<>();
		for (int i = 0; i < targets.length; i++)
		{
			if (usable.bankAt(targets[i]) != null)
			{
				keep.add(i);
			}
		}
		return keep.isEmpty() || keep.size() == targets.length ? null : keep.stream().mapToInt(Integer::intValue).toArray();
	}
}
