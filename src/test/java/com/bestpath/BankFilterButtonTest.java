package com.bestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.bestpath.core.req.BankItem;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import com.bestpath.route.StepInfo;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * The bank filter button: shows only the items the route asks for from the bank, and sits where Quest Helper's own
 * button does not.
 */
public class BankFilterButtonTest
{
	@Test
	public void theRouteItemsAreEveryItemOfItsBankSteps()
	{
		StepInfo bank = StepInfo.bank("Varrock west bank", List.of(
			StepInfo.TakeOut.of(new BankItem("Bullseye lantern", new int[]{4550, 4548}, 1), null),
			StepInfo.TakeOut.of(new BankItem("law runes x2", new int[]{563}, 2, true), null)));
		RouteStep b = new RouteStep(RouteStep.Kind.BANK, "Bank at Varrock west bank", "", 30, List.of(), null, new int[0], -1)
			.withInfo(bank);
		RouteStep walk = new RouteStep(RouteStep.Kind.WALK, "Walk", "", 5, List.of(), null, new int[]{1, 2}, -1);
		RouteResult r = RouteResults.ready(1, new RouteRequest(3200, 3200, 0), List.of(b, walk));
		assertEquals(Set.of(4550, 4548, 563), BankFilterButton.routeItems(r));
		assertTrue("a route without a bank step: none (the button hides)", BankFilterButton.routeItems(
			RouteResults.ready(2, new RouteRequest(3200, 3200, 0), List.of(walk))).isEmpty());
		assertTrue(BankFilterButton.routeItems(null).isEmpty());
	}

	@Test
	public void theButtonIsNotWhereQuestHelpersIs()
	{
		// the bank's top bar (cache 2735, interface 12): Quest Helper's button at x 408-433, ours right of the bank's two
		// small buttons (x 34-55, 58-79)
		assertTrue(BankFilterButton.X >= 80);
		assertTrue(BankFilterButton.X + BankFilterButton.SIZE < 408);
	}
}
