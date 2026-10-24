package com.bestpath.tool.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.tool.edit.Edit;
import com.bestpath.tool.edit.ManualState;
import com.bestpath.tool.update.CompareFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The game update cards' buttons and answers. */
class CacheCheckCardsTest
{
	private static CompareFile.Item item(String kind, String ref)
	{
		CompareFile.Item i = new CompareFile.Item();
		i.key = "2740/" + kind + "/" + ref + "/3200,3200,0";
		i.kind = kind;
		i.look = true;
		i.title = "Title";
		i.text = "What changed";
		i.x = 3200;
		i.y = 3200;
		i.ref = ref;
		return i;
	}

	private static List<String> words(String kind)
	{
		List<String> out = new ArrayList<>();
		for (CacheCheckCards.Button b : CacheCheckCards.buttons(kind))
		{
			out.add(b.word);
		}
		return out;
	}

	@Test
	void eachKindHasItsButtons()
	{
		assertEquals(Arrays.asList("KEEP", "DELETE"), words("OBJECT_GONE"));
		assertEquals(Arrays.asList("KEEP", "DELETE"), words("GROUND_CHANGED"));
		assertEquals(Arrays.asList("KEEP", "TAKE_BACK"), words("EDIT_TILE_CHANGED"));
		assertEquals(Arrays.asList("KEEP", "TAKE_BACK"), words("SHUT_DOOR_GONE"));
		assertEquals(Arrays.asList("KEEP", "REMOVE"), words("PLACE_KIND_GONE"));
		assertEquals(Arrays.asList("CHECKED"), words("REVIEWED_REGION_CHANGED"));
		assertEquals(Arrays.asList("CHECKED"), words("AREA_REGION_CHANGED"));
	}

	@Test
	void theCardSaysWhichUpdateAndShowsSomeDetails()
	{
		CompareFile.Item i = item("REVIEWED_REGION_CHANGED", "12850");
		i.details = Arrays.asList("1", "  2", "3", "4", "5", "6", "7");
		QuestionCards.Question q = CacheCheckCards.question(i, 2735, 2740);
		assertEquals("cc-" + i.key, q.id);
		assertTrue(q.text.startsWith("Game update #2735 -> #2740. Title: What changed"), q.text);
		assertTrue(q.text.contains("\n - 2"), q.text);
		assertTrue(q.text.endsWith("(more in the report)"), q.text);
	}

	@Test
	void keepSavesOnlyTheAnswer()
	{
		QuestionCards.Question q = CacheCheckCards.question(item("OBJECT_MOVED", "t-1"), 2735, 2740);
		List<Edit> edits = CacheCheckCards.answer(q, "KEEP");
		assertEquals(1, edits.size());
		assertEquals(Edit.Type.CACHE_CHECK, edits.get(0).type);
		assertEquals("2740/OBJECT_MOVED/t-1/3200,3200,0", edits.get(0).target);
		assertEquals("KEEP", edits.get(0).name);
		assertEquals(java.util.Collections.singleton(edits.get(0).target), ManualState.cacheChecks(edits));
	}

	@Test
	void deleteAlsoDeletesTheTransportAndRemoveTheKind()
	{
		List<Edit> del = CacheCheckCards.answer(CacheCheckCards.question(item("OBJECT_GONE", "t-7"), 2735, 2740), "DELETE");
		assertEquals(2, del.size());
		assertEquals(Edit.Type.TRANSPORT_DELETE, del.get(1).type);
		assertEquals("t-7", del.get(1).target);
		List<Edit> rem = CacheCheckCards.answer(CacheCheckCards.question(item("PLACE_KIND_GONE", "45"), 2735, 2740), "REMOVE");
		assertEquals(Edit.Type.PLACE_KIND, rem.get(1).type);
		assertEquals("45", rem.get(1).target);
		assertTrue(rem.get(1).on);
		assertTrue(ManualState.placeKinds(rem).removed.contains(45));
	}

	@Test
	void theReportShowsAsHtml()
	{
		String h = CacheCompareDialog.html("# Game update\n\n**2 things** need a `look`\n\n## A\n\n- one\n  - two <x>\n- three\n");
		assertTrue(h.contains("<h2>Game update</h2>"), h);
		assertTrue(h.contains("<p><b>2 things</b> need a <code>look</code></p>"), h);
		assertTrue(h.contains("<ul><li>one</li><ul><li>two &lt;x&gt;</li></ul><li>three</li></ul>"), h);
	}
}
