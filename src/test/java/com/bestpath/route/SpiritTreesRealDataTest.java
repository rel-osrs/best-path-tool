package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import com.bestpath.core.req.PlayerState;
import com.bestpath.data.MapDataLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Spirit trees on the REAL exported map data (detected, never asked): a planted tree the plugin has
 * not seen grown is not used and nothing is asked; with both trees seen grown the route rides them. Skipped when the
 * export is missing.
 */
public class SpiritTreesRealDataTest
{
	/** In Port Sarim, a short walk from its spirit tree; Brimhaven's spirit tree. */
	private static final int[] FROM = {3048, 3245, 0};
	private static final RouteRequest TO = new RouteRequest(2801, 3203, 0);

	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	private static RouteResult route(PlayerState state)
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		PlayerSnapshot me = new PlayerSnapshot(FROM[0], FROM[1], FROM[2], state, List.of());
		return engine.route(new RouteJob(1, TO, me, TransportSettings.defaults(), Set.of()));
	}

	private static PlayerState.Builder player()
	{
		return new PlayerState.Builder("Plugin player").members(true).askUnanswered(true)
			.quest("Tree Gnome Village", com.bestpath.core.req.QuestState.DONE);
	}

	private static List<String> questions(RouteResult r)
	{
		List<String> out = new ArrayList<>();
		for (RouteStep s : r.getSteps())
		{
			out.addAll(s.getInfo().getQuestions());
		}
		return out;
	}

	private static boolean usesNetwork(RouteResult r, String network)
	{
		for (RouteStep s : r.getSteps())
		{
			if (network.equals(s.getInfo().getNetwork()))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void spiritTreesAreUsedOnlyWhenSeenGrownAndNeverAsked()
	{
		RouteResult unseen = route(player().build());
		assertFalse("not seen grown: not used: " + unseen.getSteps(), usesNetwork(unseen, "Spirit tree"));
		assertEquals(List.of(), questions(unseen));

		RouteResult grown = route(player().spiritTree("Port Sarim").spiritTree("Brimhaven").build());
		assertEquals(RouteStatus.READY, grown.getStatus());
		assertTrue("both seen grown: rides them: " + grown.getSteps(), usesNetwork(grown, "Spirit tree"));
		assertEquals(List.of(), questions(grown));
		assertTrue(unseen.getStatus() != RouteStatus.READY || unseen.getTotalTicks() > grown.getTotalTicks());

		RouteResult oneOnly = route(player().spiritTree("Port Sarim").build());
		assertFalse("Brimhaven's tree not seen: " + oneOnly.getSteps(), usesNetwork(oneOnly, "Spirit tree"));
	}
}
