package com.bestpath.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.path.Tiles;
import com.bestpath.route.Progress;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import com.bestpath.route.TransportSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The Route tab itself: no cards for walks, numbers 1..n, the "next" card while walking, the walk-only line. */
public class RouteTabCardsTest
{
	private static RouteStep walk(int x0, int tiles)
	{
		int[] t = new int[tiles + 1];
		for (int i = 0; i <= tiles; i++)
		{
			t[i] = Tiles.pack(x0 + i, 3200, 0);
		}
		return new RouteStep(RouteStep.Kind.WALK, "Walk", tiles + " tiles", tiles, List.of(), null, t, -1);
	}

	private static RouteTab tab()
	{
		PanelActions none = new PanelActions()
		{
			@Override
			public void findRoute(RouteRequest request)
			{
			}

			@Override
			public void clearRoute()
			{
			}

			@Override
			public void useClue()
			{
			}

			@Override
			public void skipForThisRoute(String transportId)
			{
			}

			@Override
			public void clearSkips()
			{
			}

			@Override
			public void settingsChanged(TransportSettings settings)
			{
			}
		};
		return new RouteTab(none, TransportSettings::defaults, new IconCache(IconProvider.NONE));
	}

	@Test
	public void developerDetailsOnTheCardsAndWhereThePlayerStands() throws Exception
	{
		// a developer mode shows more details (RuneLite config, off by default)
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			RouteStep ladder = new RouteStep(RouteStep.Kind.TRAVERSAL, "Ladder", "", 2, List.of(), "t-ladder", new int[0],
				Tiles.pack(3210, 3200, 0), Tiles.pack(3210, 3200, 1));
			t.show(RouteResults.ready(4, new RouteRequest(3210, 3200, 1), List.of(walk(3200, 10), ladder)), Set.of());
			assertTrue(t.cards.get(0).developerLines.isEmpty());
			t.showWhere(List.of("Game tile: 1, 2, 0 (region 0)"));
			t.setDeveloper(true);
			// the cards are made again with their details
			assertEquals(List.of("transport t-ladder", "TRAVERSAL, 2 ticks, ends 3210, 3200, 1"),
				t.cards.get(0).developerLines);
			assertEquals(List.of("Game tile: 1, 2, 0 (region 0)"), t.whereLines);
			t.setDeveloper(false);
			assertTrue(t.cards.get(0).developerLines.isEmpty());
		});
	}

	@Test
	public void walksHaveNoCardsAndTheNextCardIsMarked() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			RouteStep ladder = new RouteStep(RouteStep.Kind.TRAVERSAL, "Ladder", "", 2, List.of(), "t-ladder", new int[0],
				Tiles.pack(3210, 3200, 0), Tiles.pack(3210, 3200, 1));
			RouteStep door = new RouteStep(RouteStep.Kind.DOOR, "Door", "", 2, List.of(), "t-door", new int[0],
				Tiles.pack(3215, 3200, 1));
			RouteResult r = RouteResults.ready(4, new RouteRequest(3220, 3200, 1),
				List.of(walk(3200, 10), ladder, walk(3210, 5), door, walk(3215, 5)));
			t.show(r, Set.of());
			List<Integer> numbers = new ArrayList<>();
			for (StepCard c : t.cards)
			{
				numbers.add(c.number);
			}
			assertEquals(List.of(1, 2), numbers);
			assertEquals("Ladder", t.cards.get(0).getStep().getTitle());

			// walking to the ladder: its card is "next", with the walk's length
			t.showProgress(new Progress(4, 0, 3));
			assertEquals(CardPlan.State.NEXT, t.cards.get(0).state);
			assertEquals("Next, after walking 10 tiles", t.cards.get(0).nextLine.getText());
			assertEquals(CardPlan.State.NONE, t.cards.get(1).state);
			// on the ladder: current, the line goes
			t.showProgress(new Progress(4, 1, 0));
			assertEquals(CardPlan.State.CURRENT, t.cards.get(0).state);
			assertEquals("", t.cards.get(0).nextLine.getText());
			// walking to the door
			t.showProgress(new Progress(4, 2, 0));
			assertEquals(CardPlan.State.DONE, t.cards.get(0).state);
			assertEquals(CardPlan.State.NEXT, t.cards.get(1).state);
			// another route's progress changes nothing
			t.showProgress(new Progress(5, 0, 0));
			assertEquals(CardPlan.State.NONE, t.cards.get(1).state);
		});
	}

	@Test
	public void aWalkOnlyRouteHasNoCards() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RouteTab t = tab();
			t.show(RouteResults.ready(1, new RouteRequest(3235, 3200, 0), List.of(walk(3200, 35))), Set.of());
			assertTrue(t.cards.isEmpty());
		});
	}
}
