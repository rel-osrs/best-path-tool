package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
 * Two Sailing needs, checked on the REAL exported map data
 * (src/main/resources/com/bestpath/data, cache #2735): the existing sea model (dock levels from the cache, restricted
 * sea areas per boat part) already expresses both, so these routes only confirm it. Skipped when the export or its
 * sea file is missing.
 * <ul>
 *   <li>Sunbleak Island (dock 49): Sailing 72 to moor (cache table 194; Wiki "Sunbleak Island" rev 15356377) and an
 *   adamant helm for the tangled kelp around it (its buoy is in the Kelp area, water textures 180-184).</li>
 *   <li>Ynysdail (dock 54): Sailing 73 (cache) and an adamant keel or better for the crystal-flecked waters
 *   (Wiki "Ynysdail" rev 15356378; its buoy is in the Crystalline waters, textures 170-174).</li>
 * </ul>
 * The player has no quests, so the Gwenith rowboat to Ynysdail (Song of the Elves) is not a way there.
 */
public class SailingNeedsRealDataTest
{
	/** Deepfin Point's gangplank tile (dock 26, Sailing 67, no quest). */
	private static final int[] DEEPFIN = {1923, 2759, 0};
	/** Port Piscarilius's gangplank tile (dock 7, Sailing 15, no quest). */
	private static final int[] PISCARILIUS = {1845, 3688, 0};
	/** A tile beside Sunbleak Island's mooring point. */
	private static final RouteRequest SUNBLEAK = new RouteRequest(2190, 2325, 0);
	/** A tile beside Ynysdail's mooring point. */
	private static final RouteRequest YNYSDAIL = new RouteRequest(2223, 3467, 0);
	/** A tile beside Lledrith Island's mooring point. */
	private static final RouteRequest LLEDRITH = new RouteRequest(2097, 3186, 0);
	private static final int STEEL = 2;
	private static final int ADAMANT = 4;

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

	private static PlayerState sailor(int level)
	{
		return new PlayerState.Builder("Sailor " + level).members(true).level(Skill.SAILING, level).build();
	}

	/** A teak sloop with oak sails moored at the dock, keel and helm as given. */
	private static Boat sloop(int keel, int helm, int dockId)
	{
		return new Boat(1, BoatType.SLOOP, 2, 1, keel, helm, false, false, Boat.Focus.NONE, dockId);
	}

	private static RouteResult route(int[] from, RouteRequest to, PlayerState state, Boat boat)
	{
		PlayerSnapshot me = new PlayerSnapshot(from[0], from[1], from[2], state, List.of(boat));
		return engine.route(new RouteJob(1, to, me, TransportSettings.defaults(), Set.of())).explained();
	}

	/** Whether the route sails and then leaves the boat at the named dock. */
	private static boolean leavesAt(RouteResult r, String dock)
	{
		if (r.getStatus() != RouteStatus.READY)
		{
			return false;
		}
		for (RouteStep s : r.getSteps())
		{
			if (s.getKind() == RouteStep.Kind.BOAT && s.getTitle().startsWith("Leave") && s.getTitle().contains(dock))
			{
				return true;
			}
		}
		return false;
	}

	private static String describe(RouteResult r)
	{
		StringBuilder sb = new StringBuilder(r.getStatus() + " " + r.getMessage());
		for (RouteStep s : r.getSteps())
		{
			sb.append(" | ").append(s.getKind()).append(' ').append(s.getTitle());
		}
		return sb.toString();
	}

	@Test
	public void sunbleakIslandNeedsAnAdamantHelm()
	{
		needSea();
		RouteResult steel = route(DEEPFIN, SUNBLEAK, sailor(99), sloop(ADAMANT, STEEL, 26));
		assertFalse(describe(steel), leavesAt(steel, "Sunbleak Island"));
		RouteResult adamant = route(DEEPFIN, SUNBLEAK, sailor(99), sloop(ADAMANT, ADAMANT, 26));
		assertTrue(describe(adamant), leavesAt(adamant, "Sunbleak Island"));
	}

	@Test
	public void sunbleakIslandNeedsSailing72ToMoor()
	{
		needSea();
		RouteResult low = route(DEEPFIN, SUNBLEAK, sailor(71), sloop(ADAMANT, ADAMANT, 26));
		assertFalse(describe(low), leavesAt(low, "Sunbleak Island"));
		RouteResult enough = route(DEEPFIN, SUNBLEAK, sailor(72), sloop(ADAMANT, ADAMANT, 26));
		assertTrue(describe(enough), leavesAt(enough, "Sunbleak Island"));
	}

	@Test
	public void lledrithIslandNeedsAnAdamantKeel()
	{
		// the keel rule on the real map: Lledrith Island (dock 53, Sailing 66; Wiki "Lledrith Island" rev 15356380)
		// lies in the same crystal-flecked waters as Ynysdail and its water is wide open
		needSea();
		RouteResult steel = route(PISCARILIUS, LLEDRITH, sailor(99), sloop(STEEL, ADAMANT, 7));
		assertFalse(describe(steel), leavesAt(steel, "Lledrith Island"));
		RouteResult adamant = route(PISCARILIUS, LLEDRITH, sailor(99), sloop(ADAMANT, ADAMANT, 7));
		assertTrue(describe(adamant), leavesAt(adamant, "Lledrith Island"));
	}

	/**
	 * Ynysdail's water joins the ocean only through a crooked channel among the rocks west of it (about 2139-2199,
	 * 3452-3455) where the water is at most 2 tiles from an obstacle: a skiff or sloop fits tile by tile (hard clearance
	 * 2), but no straight link of the sea graph threads it. Passed thanks to the
	 * sea graph's channels (core SeaChannels).
	 */
	@Test
	public void ynysdailNeedsAnAdamantKeel()
	{
		needSea();
		RouteResult steel = route(PISCARILIUS, YNYSDAIL, sailor(99), sloop(STEEL, ADAMANT, 7));
		assertFalse(describe(steel), leavesAt(steel, "Ynysdail"));
		RouteResult adamant = route(PISCARILIUS, YNYSDAIL, sailor(99), sloop(ADAMANT, ADAMANT, 7));
		assertTrue(describe(adamant), leavesAt(adamant, "Ynysdail"));
		RouteResult low = route(PISCARILIUS, YNYSDAIL, sailor(72), sloop(ADAMANT, ADAMANT, 7));
		assertFalse("Ynysdail needs Sailing 73: " + describe(low), leavesAt(low, "Ynysdail"));
	}

	/** Open water in the Ynysdail bay: instead of "no route", the boat is taken in there if it can fit. */
	private static final RouteRequest YNYSDAIL_BAY = new RouteRequest(2220, 3452, 0);

	@Test
	public void aBoatThatFitsSailsIntoTheYnysdailBay()
	{
		needSea();
		for (BoatType type : new BoatType[]{BoatType.SKIFF, BoatType.SLOOP})
		{
			Boat boat = new Boat(1, type, 2, 1, ADAMANT, ADAMANT, false, false, Boat.Focus.NONE, 7);
			RouteResult r = route(PISCARILIUS, YNYSDAIL_BAY, sailor(99), boat);
			assertEquals(type + ": " + describe(r), RouteStatus.READY, r.getStatus());
			RouteStep last = r.getSteps().get(r.getSteps().size() - 1);
			assertEquals(type + ": " + describe(r), RouteStep.Kind.SAIL, last.getKind());
		}
	}

	@Test
	public void aKeelBelowAdamantIsToldWhyNotJustNoRoute()
	{
		// the same boat with a steel keel: no route, and the card says the Crystalline waters need an adamant keel
		needSea();
		RouteResult r = route(PISCARILIUS, YNYSDAIL_BAY, sailor(99), sloop(STEEL, ADAMANT, 7));
		assertFalse(describe(r), r.getStatus() == RouteStatus.READY);
		StringBuilder why = new StringBuilder();
		if (r.getNoRouteWhy() != null)
		{
			for (NoRouteWhy.Blocker b : r.getNoRouteWhy().blockers)
			{
				why.append(String.join(" ", b.getReasons())).append(' ');
			}
		}
		assertTrue(describe(r) + " / " + why, why.toString().contains("Crystalline waters (an adamant keel)"));
	}

	@Test
	public void theDocksCarryTheirLevelsFromTheCache()
	{
		needSea();
		int found = 0;
		for (com.bestpath.core.sea.Dock d : ((CoreRouteEngine) engine).getRouter().getData().getSea().getDocks())
		{
			if (d.getId() == 49)
			{
				assertEquals("Sunbleak Island", d.getName());
				assertEquals(72, d.getLevel());
				found++;
			}
			else if (d.getId() == 54)
			{
				assertEquals("Ynysdail", d.getName());
				assertEquals(73, d.getLevel());
				found++;
			}
		}
		assertEquals(2, found);
	}
}
