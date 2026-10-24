package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.data.WildernessZonesBuilder;
import com.bestpath.core.path.Tiles;
import java.util.List;
import org.junit.Test;

/**
 * Every card that starts or ends in the Wilderness shows a Wilderness skull like the requirements, with the text
 * WILDERNESS!, and a red outline.
 */
public class WildernessCardTest
{
	/** The surface Wilderness as the game's script counts it, cut down: x 2944-3391, y 3520-3567 (levels 1-6). */
	static WildernessZones surface()
	{
		WildernessZonesBuilder b = new WildernessZonesBuilder(2944, 3520, 448, 48);
		for (int y = 3520; y < 3568; y++)
		{
			for (int x = 2944; x <= 3391; x++)
			{
				b.set(x, y, WildernessZones.zoneOfLevel((y - 3520) / 8 + 1));
			}
		}
		return b.build();
	}

	private static RouteStep step(RouteStep.Kind kind, int[] from, int[] to)
	{
		int a = Tiles.pack(from[0], from[1], from[2]);
		int b = Tiles.pack(to[0], to[1], to[2]);
		return new RouteStep(kind, "Lever", "", 5, List.of(), "t-lever", new int[]{a, b}, a, b);
	}

	@Test
	public void aStepThatStartsOrEndsInTheWildernessSaysSo()
	{
		WildernessZones w = surface();
		// ends in the Wilderness (y 3555)
		RouteStep in = WildernessSteps.inWilderness(step(RouteStep.Kind.TRAVERSAL, new int[]{3090, 3475, 0},
			new int[]{3154, 3555, 0}), w);
		assertTrue(in.getInfo().isWilderness());
		StepInfo.Need n = in.getInfo().getNeeds().get(0);
		assertEquals(StepInfo.Need.Type.WILDERNESS, n.getType());
		assertEquals("WILDERNESS!", n.getText());
		assertEquals("no level", "The Wilderness: other players can attack you here", n.getTooltip());
		// starts there
		RouteStep out = WildernessSteps.inWilderness(step(RouteStep.Kind.TELEPORT, new int[]{3100, 3530, 0},
			new int[]{3222, 3218, 0}), w);
		assertTrue(out.getInfo().isWilderness());
		assertEquals(StepInfo.Need.Type.WILDERNESS, out.getInfo().getNeeds().get(0).getType());
		// neither end: unchanged; walks have no card
		RouteStep far = step(RouteStep.Kind.TRAVERSAL, new int[]{3222, 3218, 0}, new int[]{3222, 3219, 1});
		assertSame(far, WildernessSteps.inWilderness(far, w));
		RouteStep walk = step(RouteStep.Kind.WALK, new int[]{3100, 3510, 0}, new int[]{3100, 3540, 0});
		assertSame(walk, WildernessSteps.inWilderness(walk, w));
		// without the data nothing is the Wilderness
		assertFalse(WildernessSteps.inWilderness(step(RouteStep.Kind.TRAVERSAL, new int[]{3100, 3530, 0},
			new int[]{3100, 3540, 0}), WildernessZones.NONE).getInfo().isWilderness());
		// once only, first among the needs
		RouteStep twice = in.withInfo(in.getInfo().withWilderness());
		assertEquals(1, twice.getInfo().getNeeds().size());
	}
}
