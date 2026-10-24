package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import java.util.List;
import org.junit.Test;

/** A Wilderness card has a red outline and the game's skull before "WILDERNESS!". */
public class WildernessOutlineTest
{
	private static RouteStep step(RouteStep.Kind kind)
	{
		int a = Tiles.pack(3090, 3475, 0);
		return new RouteStep(kind, "Lever", "", 5, List.of(), "t-lever", new int[]{a}, a, a);
	}

	@Test
	public void redOverEverythingElse()
	{
		assertNull(StepCard.outline(step(RouteStep.Kind.TRAVERSAL)));
		assertEquals(Ui.OUTLINE_BANK, StepCard.outline(step(RouteStep.Kind.BANK)));
		RouteStep wild = step(RouteStep.Kind.TRAVERSAL);
		wild = wild.withInfo(wild.getInfo().withWilderness());
		assertEquals(Ui.OUTLINE_BAD, StepCard.outline(wild));
		RouteStep bank = step(RouteStep.Kind.BANK);
		bank = bank.withInfo(bank.getInfo().withWilderness());
		assertEquals("red over yellow (no bank is in the Wilderness)", Ui.OUTLINE_BAD, StepCard.outline(bank));
	}

	@Test
	public void theSkullIsTheGamesWildernessSkull()
	{
		StepInfo.Need n = StepInfo.Need.wilderness();
		assertEquals(IconKey.sprite(1046), IconChooser.need(n));
	}
}
