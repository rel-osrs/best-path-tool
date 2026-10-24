package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WildernessZones;
import com.bestpath.data.MapDataLoader;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.Skill;
import com.bestpath.player.GameReading;
import com.bestpath.player.SnapshotMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/** The Wilderness on the REAL exported data: the game's zones, and a route's cards that go there. */
public class WildernessRealDataTest
{
	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	private static WildernessZones zones()
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		return ((CoreRouteEngine) engine).getRouter().getData().getWilderness();
	}

	@Test
	public void theGamesZones()
	{
		WildernessZones w = zones();
		assertEquals("Edgeville, by the lever", 0, w.zone(3090, 3475));
		assertEquals("just south of the ditch", 0, w.zone(3100, 3519));
		assertEquals("level 1", WildernessZones.TO_20, w.zone(3100, 3520));
		assertEquals("level 20", WildernessZones.TO_20, w.zone(3100, 3679));
		assertEquals("level 21", WildernessZones.TO_30, w.zone(3100, 3680));
		assertEquals("level 30", WildernessZones.TO_30, w.zone(3100, 3759));
		assertEquals("level 31", WildernessZones.DEEP, w.zone(3100, 3760));
		assertEquals("the level 13 obelisk", WildernessZones.TO_20, w.zone(3156, 3620));
		assertEquals("the Deserted Keep (level 51)", WildernessZones.DEEP, w.zone(3154, 3924));
		assertEquals("the Wilderness dungeons below", WildernessZones.TO_20, w.zone(3000, 9925));
		assertEquals("Lumbridge", 0, w.zone(3222, 3218));
		// Ferox Enclave is a safe area; there are no banks in the Wilderness
		assertEquals("Ferox Enclave's bank chest", 0, w.zone(3131, 3632));
		assertEquals("inside Ferox Enclave's walls", 0, w.zone(3140, 3630));
	}

	/**
	 * When the player walks across the Wilderness ditch it is marked as Wilderness (a "go into the Wilderness" card).
	 */
	@Test
	public void walkingOverTheDitchHasAGoIntoTheWildernessCard()
	{
		WildernessZones w = zones();
		PlayerState member = new PlayerState.Builder("Member").members(true).build();
		RouteResult r = engine.route(new RouteJob(1, new RouteRequest(3094, 3540, 0),
			new PlayerSnapshot(3094, 3505, 0, member, List.of()), TransportSettings.defaults(), Set.of()));
		assertEquals(RouteStatus.READY, r.getStatus());
		RouteStep card = null;
		int at = -1;
		for (int i = 0; i < r.getSteps().size(); i++)
		{
			if (WildernessSteps.INTO_WILDERNESS.equals(r.getSteps().get(i).getTitle()))
			{
				card = r.getSteps().get(i);
				at = i;
			}
		}
		assertTrue(r.getSteps().toString(), card != null);
		assertEquals(RouteStep.Kind.TRAVERSAL, card.getKind());
		assertTrue("the skull and red outline", card.getInfo().isWilderness());
		assertEquals(WildernessSteps.DITCH_OBJECT, card.getClick().getObjectId());
		assertEquals("Cross", card.getClick().getOption());
		int from = card.getMarkerTile();
		int into = card.getEndTile();
		assertTrue(!w.isWilderness(Tiles.x(from), Tiles.y(from)) && w.isWilderness(Tiles.x(into), Tiles.y(into)));
		// a walk before it up to the line and after it on to the destination; the route's ticks are the walk's
		assertEquals(RouteStep.Kind.WALK, r.getSteps().get(at - 1).getKind());
		assertEquals(RouteStep.Kind.WALK, r.getSteps().get(at + 1).getKind());
		int sum = 0;
		for (RouteStep s : r.getSteps())
		{
			sum += s.getTicks();
		}
		assertEquals(r.getTotalTicks(), sum);
		// only once, and a walk that stays out of the Wilderness has none
		RouteResult out = engine.route(new RouteJob(2, new RouteRequest(3094, 3510, 0),
			new PlayerSnapshot(3094, 3500, 0, member, List.of()), TransportSettings.defaults(), Set.of()));
		for (RouteStep s : out.getSteps())
		{
			assertTrue(!WildernessSteps.INTO_WILDERNESS.equals(s.getTitle()));
		}
	}

	@Test
	public void cardsGoingIntoTheWildernessHaveTheSkull()
	{
		zones();
		PlayerState member = new PlayerState.Builder("Member").members(true).build();
		RouteResult r = engine.route(new RouteJob(1, new RouteRequest(3154, 3923, 0),
			new PlayerSnapshot(3093, 3475, 0, member, List.of()), TransportSettings.defaults(), Set.of()));
		assertEquals(RouteStatus.READY, r.getStatus());
		WildernessZones w = zones();
		int wild = 0;
		for (RouteStep s : r.getSteps())
		{
			if (s.getKind() == RouteStep.Kind.WALK)
			{
				continue;
			}
			int end = s.getEndTile();
			boolean there = end != -1 && w.isWilderness(Tiles.x(end), Tiles.y(end));
			if (there)
			{
				assertTrue(s.getTitle(), s.getInfo().isWilderness());
				wild++;
			}
		}
		assertTrue("the route has a card that ends in the Wilderness: " + r.getSteps(), wild > 0);
	}

	/**
	 * Over level 20 Wilderness, the route goes to level 20 Wilderness before using the Construction cape. The house teleports (spell, tablet, Construction and Max cape) had no Wilderness
	 * limit, so they counted as working at any depth. Every teleport needs one.
	 */
	@Test
	public void everyTeleportHasAWildernessLimit()
	{
		zones();
		List<String> none = new ArrayList<>();
		for (TransportLine t : ((CoreRouteEngine) engine).getRouter().getData().getTransports())
		{
			if (t.getKind() == TransportLine.Kind.TELEPORT && t.getWildernessLimit() == null)
			{
				none.add(t.getName());
			}
		}
		assertEquals("teleports without a Wilderness limit", List.of(), none);
	}

	/** At level 25 wearing the Construction cape, the route walks to level 20 before teleporting. */
	@Test
	public void theConstructionCapeWaitsForLevel20()
	{
		WildernessZones w = zones();
		GameReading g = new GameReading();
		g.members = true;
		g.worn[EquipSlot.CAPE.ordinal()] = 9790; // Construction cape (t)
		g.houseValue = 1; // Rimmington
		g.levels[Skill.CONSTRUCTION.ordinal()] = 99;
		PlayerState p = SnapshotMapper.toSnapshot(g).getState();
		int[] start = {3100, 3720, 0}; // level 25
		assertEquals(WildernessZones.TO_30, w.zone(start[0], start[1]));
		RouteResult r = engine.route(new RouteJob(1, new RouteRequest(2954, 3224, 0),
			new PlayerSnapshot(start[0], start[1], start[2], p, List.of()), TransportSettings.defaults(), Set.of()));
		assertEquals(RouteStatus.READY, r.getStatus());
		int at = Tiles.pack(start[0], start[1], start[2]);
		boolean cape = false;
		for (RouteStep s : r.getSteps())
		{
			if (s.getKind() == RouteStep.Kind.TELEPORT)
			{
				assertTrue(s.getTitle() + " used at level 21+: " + r.getSteps(),
					w.zone(Tiles.x(at), Tiles.y(at)) <= WildernessZones.TO_20);
				cape |= s.getTitle().contains("Construct");
			}
			if (s.getEndTile() != -1)
			{
				at = s.getEndTile();
			}
		}
		assertTrue("the route uses the cape: " + r.getSteps(), cape);
	}
}
