package com.bestpath.tool.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.path.Tiles;
import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.EditStore;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.extract.MapPlaces;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The "go to nearest" place kind cards: one Keep / Remove card per place kind, the answer a PLACE_KIND change
 * (undoable), a removed kind left out of the plugin export, a new name used.
 */
class PlaceKindCardsTest
{
	@TempDir
	Path dir;

	private static final int[] ALTARS = {Tiles.pack(3000, 3000, 0), Tiles.pack(3243, 3207, 0), Tiles.pack(3210, 3220, 1),
		Tiles.pack(2600, 3300, 0), Tiles.pack(3300, 3300, 0)};

	private static MapPlaces.Card altar(String why)
	{
		return new MapPlaces.Card(21, "Altar", why, 1467, ALTARS, null);
	}

	@Test
	void theCardPointsAtThePlaceNearestLumbridgeAndSaysWhatItIs()
	{
		QuestionCards.Question q = PlaceKindCards.question(altar("picture; beside: altar"));
		assertEquals("place-21", q.id);
		assertEquals(QuestionCards.Action.PLACE_KIND, q.action);
		assertEquals(3210, q.x, "the nearest to 3222,3218");
		assertEquals(1, q.plane);
		assertEquals(5, q.places.length, "every place dotted on the map");
		assertEquals("Go to nearest: Altar (map icon 21), 5 places. Why this name: picture; beside: altar. Places nearest"
			+ " Lumbridge: 3210,3220,1; 3243,3207; 3300,3300; 3000,3000. The dots in this card's colour are all its places."
			+ " Keep it in the plugin's Nearest list, or remove it?", q.text);
	}

	@Test
	void aNameThatWasNotSureOfIsFlagged()
	{
		QuestionCards.Question q = PlaceKindCards.question(altar(MapPlaces.NOT_SURE + " picture (ballot)"));
		assertTrue(q.text.contains("NAME NOT SURE: named from the picture only; type a better name below if it is"
			+ " wrong. Why this name: picture (ballot)."), q.text);
	}

	@Test
	void anAnswerIsAPlaceKindChange()
	{
		QuestionCards.Question q = PlaceKindCards.question(altar(""));
		Edit keep = PlaceKindCards.answer(q, "KEEP", "Altar");
		assertEquals(Edit.Type.PLACE_KIND, keep.type);
		assertEquals("21", keep.target);
		assertFalse(keep.on);
		assertNull(keep.name, "the name not changed");
		assertEquals("Card: kept", keep.note);
		Edit renamed = PlaceKindCards.answer(q, "KEEP", "  Prayer altar ");
		assertEquals("Prayer altar", renamed.name);
		assertEquals("Card: kept, renamed", renamed.note);
		Edit removed = PlaceKindCards.answer(q, "REMOVE", "Prayer altar");
		assertTrue(removed.on);
		assertNull(removed.name, "a removed kind keeps no name");
		assertEquals("Remove the nearest place kind of map icon 21", removed.describe());
		assertEquals("Keep the nearest place kind of map icon 21, named \"Prayer altar\"", renamed.describe());
	}

	@Test
	void theAnswersFoldTheLastOneDecides() throws Exception
	{
		QuestionCards.Question q = PlaceKindCards.question(altar(""));
		EditStore s = new EditStore(dir.resolve("changes.jsonl").toFile());
		s.setStamp(() -> "tester", () -> 7, () -> 241, ex ->
		{
			throw new AssertionError(ex);
		});
		s.add(PlaceKindCards.answer(q, "KEEP", "Prayer altar"));
		s.add(PlaceKindCards.answer(q, "REMOVE", ""));
		ManualState.PlaceKindAnswers a = ManualState.placeKinds(s.active());
		assertEquals(Set.of(21), a.decided);
		assertEquals(Set.of(21), a.removed);
		assertEquals("Prayer altar", a.names.get(21), "the name given stays");
		assertTrue(s.undo(), "Ctrl+Z takes the Remove back");
		a = ManualState.placeKinds(s.active());
		assertTrue(a.removed.isEmpty());
		assertEquals(Set.of(21), a.decided);
		assertTrue(s.undo());
		assertTrue(ManualState.placeKinds(s.active()).decided.isEmpty(), "no answer: the card comes back");
	}

	@Test
	void theExportLeavesOutRemovedKindsAndUsesNewNames()
	{
		Map<Integer, String> names = MapPlaces.readNames(List.of("21\tAltar\t", "5\tBank\t", "66\tPoll booth\t"));
		Map<Integer, String> out = MapPlaces.answered(names, Set.of(66), Map.of(21, "Prayer altar"));
		assertEquals(List.of(21, 5), List.copyOf(out.keySet()));
		assertEquals("Prayer altar", out.get(21));
		assertEquals("Bank", out.get(5));
	}

	@Test
	void oneCardPerNamedKindWithPlaces()
	{
		Map<Integer, String> names = MapPlaces.readNames(List.of("21\tAltar\tpicture", "5\tBank\tcoins", "99\tNowhere\t"));
		List<MapPlaces.Card> cards = MapPlaces.cards(names, MapPlaces.readWhy(List.of("21\tAltar\tpicture", "5\tBank\tcoins")),
			Map.of(21, ALTARS), Map.of(21, 1467, 5, 1453), new int[]{Tiles.pack(3208, 3220, 2)}, sprite -> null);
		assertEquals(2, cards.size(), "no places: no card");
		assertEquals("Altar", cards.get(0).name);
		assertEquals("Bank", cards.get(1).name);
		assertEquals(1, cards.get(1).tiles.length, "the bank's places are the tiles you bank from");
		assertEquals("coins", cards.get(1).why);
	}
}
