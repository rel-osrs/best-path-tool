package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.data.MapDataLoader;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The Wyrmscraig cave on the REAL exported map data (only a boat can use this transport, it cannot
 * be sloop sized, 10 ticks; transport t-1791778058879-1927 "Cave" between the inlet at Wyrmscraig, 2559,2203, and the
 * Wyrmscraig Cavern pool, 2571,8606): a boat at sea in the inlet sails through it into the pool, a sloop does not fit.
 * Skipped when the export or its sea file is missing.
 */
public class WyrmscraigCaveRealDataTest
{
	/** In the inlet at Wyrmscraig, a few tiles from the cave. */
	private static final int[] INLET = {2552, 2198, 0};
	/** Open water in the cave pool, by the Wyrmscraig Cavern dock. */
	private static final RouteRequest POOL = new RouteRequest(2575, 8603, 0);

	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	private static void needSea()
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		assumeTrue("the export has no sea", ((CoreRouteEngine) engine).getRouter().getData().getSea() != null);
	}

	private static RouteResult fromTheInlet(BoatType type)
	{
		PlayerState me = new PlayerState.Builder("Sailor").members(true).level(Skill.SAILING, 99).build();
		Boat boat = new Boat(1, type, 2, 1, 0, 0, false, false, Boat.Focus.NONE, Boat.AT_START);
		PlayerSnapshot at = new PlayerSnapshot(INLET[0], INLET[1], INLET[2], me, List.of(boat));
		return engine.route(new RouteJob(1, POOL, at, TransportSettings.defaults(), Set.of()));
	}

	private static String describe(RouteResult r)
	{
		StringBuilder sb = new StringBuilder(r.getStatus() + " " + r.getMessage());
		for (RouteStep s : r.getSteps())
		{
			sb.append(" | ").append(s.getKind()).append(' ').append(s.getTitle()).append(" (").append(s.getTicks()).append(')');
		}
		return sb.toString();
	}

	@Test
	public void aSkiffSailsThroughTheCave()
	{
		needSea();
		RouteResult r = fromTheInlet(BoatType.SKIFF);
		assertEquals(describe(r), RouteStatus.READY, r.getStatus());
		List<RouteStep> s = r.getSteps();
		assertEquals(describe(r), 3, s.size());
		assertEquals(describe(r), RouteStep.Kind.SAIL, s.get(0).getKind());
		assertEquals(describe(r), "Cave", s.get(1).getTitle().replace("Enter ", ""));
		assertEquals(describe(r), 10, s.get(1).getTicks());
		assertEquals(describe(r), RouteStep.Kind.SAIL, s.get(2).getKind());
	}

	@Test
	public void fromTheWyrmscraigDockThroughTheCave()
	{
		// a skiff moored at Wyrmscraig (dock 59): board, sail to the cave, through it, on to the pool
		needSea();
		PlayerState me = new PlayerState.Builder("Sailor").members(true).level(Skill.SAILING, 99).build();
		Boat boat = new Boat(1, BoatType.SKIFF, 2, 1, 0, 0, false, false, Boat.Focus.NONE, 59);
		PlayerSnapshot at = new PlayerSnapshot(2568, 2295, 0, me, List.of(boat));
		RouteResult r = engine.route(new RouteJob(1, POOL, at, TransportSettings.defaults(), Set.of()));
		assertEquals(describe(r), RouteStatus.READY, r.getStatus());
		boolean cave = false;
		for (RouteStep s : r.getSteps())
		{
			cave |= s.getTitle().endsWith("Cave") && s.getKind() != RouteStep.Kind.SAIL;
		}
		assertEquals(describe(r), true, cave);
		assertEquals(describe(r), RouteStep.Kind.SAIL, r.getSteps().get(r.getSteps().size() - 1).getKind());
	}

	@Test
	public void aSloopDoesNotFit()
	{
		needSea();
		RouteResult r = fromTheInlet(BoatType.SLOOP);
		assertNotEquals(describe(r), RouteStatus.READY, r.getStatus());
	}
}
