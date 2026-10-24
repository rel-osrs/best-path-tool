package com.bestpath.tool.ui;

import com.bestpath.core.path.Tiles;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.extract.MapPlaces;
import java.util.ArrayList;
import java.util.List;

/**
 * The "go to nearest" place kind cards: one Keep / Remove card per kind, with its map icon, a box to correct the
 * name, and every place of the kind dotted on the map. The answer is saved as a {@code PLACE_KIND} change.
 */
final class PlaceKindCards
{
	/** Card ids of place kinds start with this, then the map icon id. */
	static final String ID = "place-";
	/** Place kind cards point at the kind's place nearest to this tile (Lumbridge), where most cards then sit together. */
	private static final int[] HOME = {3222, 3218};

	private PlaceKindCards()
	{
	}

	/** The card for a kind: where it points, its words, its places. */
	static QuestionCards.Question question(MapPlaces.Card k)
	{
		int[] near = MapPlaces.nearest(k.tiles, HOME[0], HOME[1], 4);
		QuestionCards.Question q = new QuestionCards.Question();
		q.id = ID + k.icon;
		q.action = QuestionCards.Action.PLACE_KIND;
		q.icon = k.icon;
		q.kindName = k.name;
		q.places = k.tiles;
		q.picture = k.picture;
		q.x = Tiles.x(near[0]);
		q.y = Tiles.y(near[0]);
		q.plane = Tiles.plane(near[0]);
		q.text = text(k, near);
		return q;
	}

	/** "Go to nearest: Altar (map icon 21), 79 places. ..." with the name's reason and a few places, nearest Lumbridge first. */
	static String text(MapPlaces.Card k, int[] near)
	{
		List<String> examples = new ArrayList<>();
		for (int t : near)
		{
			examples.add(Tiles.x(t) + "," + Tiles.y(t) + (Tiles.plane(t) > 0 ? "," + Tiles.plane(t) : ""));
		}
		boolean unsure = k.why.startsWith(MapPlaces.NOT_SURE);
		String why = unsure ? k.why.substring(MapPlaces.NOT_SURE.length()).trim() : k.why;
		return "Go to nearest: " + k.name + " (map icon " + k.icon + "), " + k.tiles.length + (k.tiles.length == 1 ? " place" : " places")
			+ (k.name.equals(com.bestpath.core.data.PlaceKinds.BANK) ? " (the tiles you bank from)" : "") + "."
			+ (unsure ? " NAME NOT SURE: named from the picture only; type a better name below if it is wrong." : "")
			+ (why.isEmpty() ? "" : " Why this name: " + why + ".")
			+ " Places nearest Lumbridge: " + String.join("; ", examples) + ". The dots in this card's colour are all its"
			+ " places. Keep it in the plugin's Nearest list, or remove it?";
	}

	/**
	 * The change a place kind card's answer makes ({@code word} KEEP or REMOVE, {@code typed} the name box): a
	 * {@code PLACE_KIND} change either way, so the card is not made again; the name only when it was changed.
	 */
	static Edit answer(QuestionCards.Question q, String word, String typed)
	{
		Edit e = new Edit();
		e.type = Edit.Type.PLACE_KIND;
		e.target = String.valueOf(q.icon);
		e.on = word.equals("REMOVE");
		e.x = q.x;
		e.y = q.y;
		e.plane = q.plane;
		String name = typed == null ? "" : typed.trim().replace('\t', ' ');
		e.name = !e.on && !name.isEmpty() && !name.equals(q.kindName) ? name : null;
		e.note = e.on ? "Card: removed" : e.name != null ? "Card: kept, renamed" : "Card: kept";
		return e;
	}
}
