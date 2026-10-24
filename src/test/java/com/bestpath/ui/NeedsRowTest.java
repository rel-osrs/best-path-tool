package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.req.Requirement;
import com.bestpath.core.req.Skill;
import com.bestpath.route.NeedStatus;
import com.bestpath.route.StepInfo;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** A card's needs with their marks: the mouse-over says the mark in words, and the chips still wrap at the card width. */
public class NeedsRowTest
{
	@Test
	public void marksAreSaidInWordsAndChipsWrapAtTheCardWidth() throws Exception
	{
		List<StepInfo.Need> needs = List.of(
			StepInfo.Need.skill(Skill.AGILITY, 51).withStatus(NeedStatus.HAVE),
			StepInfo.Need.rune("law", 563, 2).withStatus(NeedStatus.BANK),
			StepInfo.Need.coins(30).withStatus(NeedStatus.MISSING),
			StepInfo.Need.quest("Recipe for Disaster - Freeing King Awowogei", Requirement.Quest.Wanted.STARTED)
				.withStatus(NeedStatus.MISSING),
			StepInfo.Need.skill(Skill.AGILITY, 51));
		SwingUtilities.invokeAndWait(() ->
		{
			NeedsRow row = new NeedsRow(needs, new IconCache(IconProvider.NONE), StepCard.TEXT_W);
			assertEquals(List.of("Needs Agility 51: you have it", "2 law runes (or a staff that gives them): in your bank",
				"30 coins: you don't have it", "Quest: Recipe for Disaster - Freeing King Awowogei (started): you don't have it",
				"Needs Agility 51"), PanelDriver.tooltips(row));
			List<Rectangle> chips = PanelDriver.chipBounds(row);
			for (Rectangle r : chips)
			{
				assertTrue(r.toString(), r.x >= 0 && r.x + r.width <= StepCard.TEXT_W);
			}
			// the long quest wraps onto its own lines; a chip without a mark is narrower by the mark
			assertTrue(chips.get(3).height > chips.get(0).height);
			assertEquals(NeedMark.SIZE + NeedMark.GAP, chips.get(0).width - chips.get(4).width);
		});
	}

	@Test
	public void theMarksTooltipWords()
	{
		assertEquals("Agility 51: you have it", NeedMark.tooltip("Agility 51", NeedStatus.HAVE));
		assertEquals("Spade: in your bank", NeedMark.tooltip("Spade", NeedStatus.BANK));
		assertEquals("Spade: you don't have it", NeedMark.tooltip("Spade", NeedStatus.MISSING));
		assertEquals("Spade", NeedMark.tooltip("Spade", NeedStatus.UNKNOWN));
	}
}
