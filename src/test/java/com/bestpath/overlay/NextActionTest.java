package com.bestpath.overlay;

import static org.junit.Assert.assertEquals;

import com.bestpath.route.Progress;
import com.bestpath.route.RouteRequest;
import com.bestpath.route.RouteResult;
import com.bestpath.route.RouteResults;
import com.bestpath.route.RouteStep;
import java.util.List;
import org.junit.Test;

/** The text is for the next step only, never a walk. */
public class NextActionTest
{
	private static RouteStep step(RouteStep.Kind k, String title)
	{
		return new RouteStep(k, title, "", 5, List.of(), k == RouteStep.Kind.WALK ? null : title, new int[0], -1, -1);
	}

	@Test
	public void theNextStepThatIsNotAWalk()
	{
		RouteResult r = RouteResults.ready(7, new RouteRequest(3200, 3200, 0), List.of(step(RouteStep.Kind.WALK, "Walk"),
			step(RouteStep.Kind.TRAVERSAL, "Climb-up Ladder"), step(RouteStep.Kind.WALK, "Walk"), step(RouteStep.Kind.TELEPORT, "Cast")));
		assertEquals(1, StepTargets.nextAction(r, Progress.NONE));
		assertEquals(1, StepTargets.nextAction(r, new Progress(r.getGeneration(), 0, 3)));
		assertEquals(1, StepTargets.nextAction(r, new Progress(r.getGeneration(), 1, 0)));
		assertEquals(3, StepTargets.nextAction(r, new Progress(r.getGeneration(), 2, 0)));
		// another route's progress: from the start
		assertEquals(1, StepTargets.nextAction(r, new Progress(99, 3, 0)));
		// only a walk left: nothing to say
		RouteResult walk = RouteResults.ready(8, new RouteRequest(3200, 3200, 0), List.of(step(RouteStep.Kind.WALK, "Walk")));
		assertEquals(-1, StepTargets.nextAction(walk, Progress.NONE));
	}
}
