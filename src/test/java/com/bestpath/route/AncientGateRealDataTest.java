package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import com.bestpath.data.MapDataLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * "No route" with every way needing as few things, on the REAL exported map data: all the paths with the same number
 * of limiting factors show up, with an "or" between the cards. From the Shaman Caves to the far side of the third
 * Ancient Gate: the Crevice needs Agility 96, the gate an unpowered orb. Skipped when the export is missing.
 */
public class AncientGateRealDataTest
{
	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	private static RouteResult route(PlayerState.Builder p)
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		PlayerSnapshot me = new PlayerSnapshot(2781, 9337, 0, p.build(), List.of());
		return engine.route(new RouteJob(1, new RouteRequest(2420, 4694, 0), me, TransportSettings.defaults(), Set.of()))
			.explained();
	}

	/** Legends' Quest done, Agility 70, the runes for Charge Water Orb and a pickaxe; no unpowered orb. */
	private static PlayerState.Builder player()
	{
		return new PlayerState.Builder("mid-level").members(true).quest("Legends' Quest", QuestState.DONE).varp(139, 180)
			.level(Skill.AGILITY, 70).level(Skill.MAGIC, 80).level(Skill.THIEVING, 70).level(Skill.STRENGTH, 70)
			.level(Skill.MINING, 60).carry(564, 3).carry(555, 30).carry(1265, 1);
	}

	private static List<List<String>> titles(NoRouteWhy why)
	{
		List<List<String>> out = new ArrayList<>();
		for (List<NoRouteWhy.Blocker> way : why.getWays())
		{
			List<String> t = new ArrayList<>();
			for (NoRouteWhy.Blocker b : way)
			{
				t.add(b.getStep().getInfo().getName());
			}
			out.add(t);
		}
		return out;
	}

	@Test
	public void bothWaysNeedingOneThingAreShown()
	{
		// with a lockpick for the first gate, the Ancient Gate's way needs only the orb: one thing, as the Crevice
		RouteResult r = route(player().carry(1523, 1));
		assertEquals(RouteStatus.NO_ROUTE, r.getStatus());
		NoRouteWhy why = r.getNoRouteWhy();
		assertEquals(why.toString(), List.of(List.of("Crevice"), List.of("Ancient Gate")), titles(why));
		assertEquals("There are 2 ways there, each needing 1 thing you can't use yet:", why.getHeadline());
	}

	@Test
	public void aCardCountsAsOneHoweverManyOfItsNeedsAreMissing()
	{
		// the card itself counts as 1: no runes, so the gate misses the orb, the cosmic and the elemental runes, still
		// one card, as the Crevice
		RouteResult r = route(new PlayerState.Builder("no runes").members(true).quest("Legends' Quest", QuestState.DONE)
			.varp(139, 180).level(Skill.AGILITY, 70).level(Skill.MAGIC, 80).level(Skill.THIEVING, 70).level(Skill.STRENGTH, 70)
			.level(Skill.MINING, 60).carry(1265, 1).carry(1523, 1));
		NoRouteWhy why = r.getNoRouteWhy();
		assertEquals(why.toString(), List.of(List.of("Crevice"), List.of("Ancient Gate")), titles(why));
	}

	@Test
	public void aWayNeedingMoreThingsIsNotShown()
	{
		// no lockpick: the gate's way needs 2 things, the Crevice's 1: only the Crevice
		NoRouteWhy why = route(player()).getNoRouteWhy();
		assertEquals(why.toString(), List.of(List.of("Crevice")), titles(why));
		assertTrue(why.getHeadline().startsWith("The way there needs 1 thing"));
	}

	@Test
	public void waysRepeatingAShownTransportAreLeftOut()
	{
		// when several ways are blocked by the same transport (like fairy ring DLR reached different ways), that
		// transport is shown once: a fresh account from Lumbridge to the Poison Waste (fairy ring DLR)
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		PlayerSnapshot me = new PlayerSnapshot(3222, 3218, 0, PlayerStateBuilder.fresh(), List.of());
		NoRouteWhy why = engine.route(new RouteJob(1, new RouteRequest(2212, 3099, 0), me, TransportSettings.defaults(), Set.of()))
			.explained().getNoRouteWhy();
		List<String> seen = new ArrayList<>();
		for (List<NoRouteWhy.Blocker> way : why.getWays())
		{
			List<String> titles = new ArrayList<>();
			for (NoRouteWhy.Blocker b : way)
			{
				titles.add(b.getStep().getTitle());
			}
			for (String t : titles)
			{
				assertTrue(why.toString(), !seen.contains(t));
			}
			seen.addAll(titles);
		}
		assertTrue(why.toString(), seen.contains("Fairy ring -> DLR"));
	}
}
