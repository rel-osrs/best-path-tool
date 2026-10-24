package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.bestpath.clue.ClueCard;
import com.bestpath.core.req.PlayerState;
import com.bestpath.data.MapDataLoader;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** "Take me there" on a clue card with an unknown STASH unit: the route to Watson's noticeboard. */
public class WatsonRouteRealDataTest
{
	@Test
	public void aRouteReachesTheNoticeboard()
	{
		RouteEngine e = MapDataLoader.engine();
		assumeTrue("no exported map data", e instanceof CoreRouteEngine);
		PlayerState p = new PlayerState.Builder("Member").members(true).build();
		RouteResult r = e.route(new RouteJob(1, ClueCard.watsonRequest(), new PlayerSnapshot(1650, 3600, 0, p, List.of()),
			TransportSettings.defaults(), Set.of()));
		assertEquals(r.getMessage(), RouteStatus.READY, r.getStatus());
	}
}
