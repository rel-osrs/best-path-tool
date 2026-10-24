package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.RequirementText;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** A player's note for a varbit / varp need on a "no route" card. */
public class VarNotesTest
{
	private static final Map<String, String> QUESTS = Map.of("varbit 9649", "Twilight's Promise", "varp 328", "Regicide");

	private static String note(String text)
	{
		return VarNotes.of(RequirementText.parse(text, RequirementText.ItemGroups.NONE), QUESTS);
	}

	@Test
	public void aQuestsOwnVarIsPartialProgressThroughIt()
	{
		assertEquals("Partial progress through Twilight's Promise", note("varbit 9649 >= 38"));
		assertEquals("Partial progress through Twilight's Promise", note("varbit 9649 > 0"));
		assertEquals("Partial progress through Regicide", note("varp 328 = 15"));
		// it must not be that far yet
		assertEquals("Only before a certain point in Regicide", note("varp 328 < 15"));
		assertEquals("Only before a certain point in Regicide", note("not varp 328 >= 15"));
	}

	@Test
	public void anyOtherVarIsAnUnlock()
	{
		assertEquals(VarNotes.LOCKED, note("varbit 4897 > 0"));
		assertEquals(VarNotes.LOCKED, note("varbit 4897 & 4"));
		assertEquals(VarNotes.RULED_OUT, note("varbit 621 = 0"));
		assertEquals(VarNotes.LOCKED, note("not varbit 621 = 0"));
	}

	@Test
	public void noVarNoNote()
	{
		assertNull(note("members"));
		assertNull(note("Agility 30"));
	}

	@Test
	public void questNamesAsPlayersSeeThem()
	{
		assertEquals("Children of the Sun", VarNotes.questName("CHILDREN_OF_THE_SUN"));
		assertEquals("Some New Quest", VarNotes.questName("SOME_NEW_QUEST"));
		Map<String, String> m = VarNotes.questsOf(Map.of("REGICIDE", List.of(new int[]{1, 328})));
		assertEquals(Map.of("varp 328", "Regicide"), m);
	}
}
