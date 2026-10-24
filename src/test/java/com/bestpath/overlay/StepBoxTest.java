package com.bestpath.overlay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStatus;
import com.bestpath.route.RouteStep;
import java.util.List;
import org.junit.Test;

/** What the small on-screen next-step box says. */
public class StepBoxTest
{
	@Test
	public void theBoxSaysTheNextStep()
	{
		RouteRequest to = new RouteRequest(3200, 3200, 0);
		RouteStep ladder = new RouteStep(RouteStep.Kind.TRAVERSAL, "Climb-up Ladder", "", 5, List.of(), "l", new int[0], -1, -1);
		RouteResult r = RouteResults.ready(1, to, List.of(ladder));
		// no "tiles away"
		assertArrayEquals(new String[]{"Step 2 of 5", "Climb-up Ladder"}, StepBoxOverlay.lines(r, ladder, 2, 5, List.of()));
		assertArrayEquals(new String[]{"Walk to the destination"}, StepBoxOverlay.lines(r, null, 0, 1, List.of()));
		// a bank step: every item, by name
		RouteStep bank = new RouteStep(RouteStep.Kind.BANK, "Bank at Varrock West: take out 12409", "", 30, List.of(), null,
			new int[0], -1, -1);
		assertArrayEquals(new String[]{"Step 1 of 3", "Take out:", "Spade", "Feather x5", "Coins x100", "Rope"},
			StepBoxOverlay.lines(r, bank, 1, 3, List.of("Spade", "Feather x5", "Coins x100", "Rope")));
		assertArrayEquals(new String[]{"Best Path", "Calculating..."},
			StepBoxOverlay.lines(RouteResult.status(2, to, RouteStatus.CALCULATING, "Calculating..."), null, 0, 0, List.of()));
		assertArrayEquals(new String[0], StepBoxOverlay.lines(RouteResult.idle(3), null, 0, 0, List.of()));
		// arrived: nothing left to show
		assertArrayEquals(new String[0],
			StepBoxOverlay.lines(RouteResult.status(5, null, RouteStatus.ARRIVED, "You have arrived."), null, 0, 0, List.of()));
		assertArrayEquals(new String[]{"No route", "No route to the destination."},
			StepBoxOverlay.lines(RouteResults.noRoute(4, to, "No route to the destination.", List.of(), null), null, 0, 0, List.of()));
		// "No route, finding out why..." while the explanation is being calculated
		assertArrayEquals(new String[]{"No route, finding out why...", "No route to the destination."},
			StepBoxOverlay.lines(RouteResult.noRouteFindingWhy(6, to, "No route to the destination.", List.of(), () -> null), null,
				0, 0, List.of()));
	}

	@Test
	public void bankWordsInTheWorldNameTheItems()
	{
		// no bank name, item names (not "12409"), more than three: where to see them all
		RouteStep bank = new RouteStep(RouteStep.Kind.BANK, "Bank at Varrock West: take out 12409", "", 30, List.of(), null,
			new int[0], -1, -1);
		assertEquals("Take out: Spade, Feather x5", StepTargets.words(bank, List.of("Spade", "Feather x5"), false));
		assertEquals("Take out 4 items (see the side panel)", StepTargets.words(bank, List.of("a", "b", "c", "d"), false));
		assertEquals("Take out 4 items (see the box)", StepTargets.words(bank, List.of("a", "b", "c", "d"), true));
		assertEquals("Use the bank", StepTargets.words(bank, List.of(), false));
		RouteStep ladder = new RouteStep(RouteStep.Kind.TRAVERSAL, "Climb-up Ladder", "", 5, List.of(), "l", new int[0], -1, -1);
		assertEquals("Climb-up Ladder", StepTargets.words(ladder, List.of(), false));
		// the STASH put-back in a few words
		RouteStep stash = new RouteStep(RouteStep.Kind.STASH, "Put the items back in the STASH unit (Draynor Village market)", "",
			0, List.of(), "stash-5229", new int[0], -1, -1);
		assertEquals("Put items back", StepTargets.words(stash, List.of(), false));
	}
}
