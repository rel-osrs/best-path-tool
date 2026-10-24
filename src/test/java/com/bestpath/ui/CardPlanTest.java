package com.bestpath.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.RouteStep;
import java.util.List;
import org.junit.Test;

/** Walks get no card, cards are numbered 1..n, and the card the player walks to is "next". */
public class CardPlanTest
{
	private static RouteStep walk(int tiles)
	{
		int[] t = new int[tiles + 1];
		for (int i = 0; i <= tiles; i++)
		{
			t[i] = Tiles.pack(3200 + i, 3200, 0);
		}
		return new RouteStep(RouteStep.Kind.WALK, "Walk", tiles + " tiles", tiles, List.of(), null, t, -1);
	}

	/** Ticks spent walking over the whole route. */
	private static int walkTicks(List<RouteStep> steps)
	{
		int n = 0;
		for (RouteStep s : steps)
		{
			if (s.getKind() == RouteStep.Kind.WALK)
			{
				n += s.getTicks();
			}
		}
		return n;
	}

	private static RouteStep transport(RouteStep.Kind kind, String id)
	{
		return new RouteStep(kind, id, "", 2, List.of(), id, new int[0], Tiles.pack(3200, 3200, 0));
	}

	/** walk 10, door, teleport, walk 20, walk 5, ladder, walk 7 */
	private static final List<RouteStep> ROUTE = List.of(walk(10), transport(RouteStep.Kind.DOOR, "door"),
		transport(RouteStep.Kind.TELEPORT, "tele"), walk(20), walk(5), transport(RouteStep.Kind.TRAVERSAL, "ladder"),
		walk(7));

	@Test
	public void walksGetNoCard()
	{
		assertArrayEquals(new int[]{1, 2, 5}, CardPlan.cardSteps(ROUTE));
		assertArrayEquals(new int[0], CardPlan.cardSteps(List.of(walk(35))));
		// a sail is a card (it is not a plain walk)
		RouteStep sail = new RouteStep(RouteStep.Kind.SAIL, "Sail", "", 30, List.of(), null, new int[]{1, 2}, -1);
		assertArrayEquals(new int[]{1}, CardPlan.cardSteps(List.of(walk(3), sail)));
	}

	@Test
	public void walkingTotals()
	{
		assertEquals(42, CardPlan.walkTiles(ROUTE));
		assertEquals(42, walkTicks(ROUTE));
		assertEquals(0, CardPlan.walkTiles(List.of(transport(RouteStep.Kind.DOOR, "d"))));
	}

	@Test
	public void noProgressMarksNothing()
	{
		CardPlan.State[] s = CardPlan.states(ROUTE, CardPlan.cardSteps(ROUTE), -1);
		assertArrayEquals(new CardPlan.State[]{CardPlan.State.NONE, CardPlan.State.NONE, CardPlan.State.NONE}, s);
	}

	@Test
	public void walkingToATransportMarksItsCardNext()
	{
		int[] cards = CardPlan.cardSteps(ROUTE);
		// on the first walk: the door's card is next
		assertArrayEquals(new CardPlan.State[]{CardPlan.State.NEXT, CardPlan.State.NONE, CardPlan.State.NONE},
			CardPlan.states(ROUTE, cards, 0));
		assertEquals(10, CardPlan.walkToNext(ROUTE, 0));
		// at the door: current; nothing is "next"
		assertArrayEquals(new CardPlan.State[]{CardPlan.State.CURRENT, CardPlan.State.NONE, CardPlan.State.NONE},
			CardPlan.states(ROUTE, cards, 1));
		assertEquals(0, CardPlan.walkToNext(ROUTE, 1));
		// on the second of two walks after the teleport: the ladder is next, door and teleport done
		assertArrayEquals(new CardPlan.State[]{CardPlan.State.DONE, CardPlan.State.DONE, CardPlan.State.NEXT},
			CardPlan.states(ROUTE, cards, 4));
		// both walks before the ladder count from the first of them
		assertEquals(25, CardPlan.walkToNext(ROUTE, 3));
		assertEquals(5, CardPlan.walkToNext(ROUTE, 4));
	}

	@Test
	public void theLastWalkLeavesEveryCardDone()
	{
		CardPlan.State[] s = CardPlan.states(ROUTE, CardPlan.cardSteps(ROUTE), 6);
		assertArrayEquals(new CardPlan.State[]{CardPlan.State.DONE, CardPlan.State.DONE, CardPlan.State.DONE}, s);
		assertEquals(7, CardPlan.walkToNext(ROUTE, 6));
	}

	@Test
	public void summaryCountsCardsAndKeepsTheTotal()
	{
		// only the total, never broken down
		assertEquals("3 steps, 48 ticks (29 s)", RouteTab.summaryText(ROUTE, 48, -1));
		assertEquals("1 step, 2 ticks (1 s)", RouteTab.summaryText(List.of(transport(RouteStep.Kind.DOOR, "d")), 2, -1));
		assertEquals("Only walking, 35 ticks (21 s)", RouteTab.summaryText(List.of(walk(35)), 35, -1));
		// the search time by the travel time
		assertEquals("3 steps, 48 ticks (29 s)\nCalculated in 45 ms", RouteTab.summaryText(ROUTE, 48, 45));
		assertEquals("3 steps, 48 ticks (29 s)\nCalculated in 1.3 s", RouteTab.summaryText(ROUTE, 48, 1290));
		assertEquals("3 steps, 48 ticks (29 s)", RouteTab.summaryText(ROUTE, 48, -1));
		assertEquals("Walk there (35 tiles)", RouteTab.walkOnlyText(List.of(walk(35))));
		assertEquals("Walk there (1 tile)", RouteTab.walkOnlyText(List.of(walk(1))));
	}
}
