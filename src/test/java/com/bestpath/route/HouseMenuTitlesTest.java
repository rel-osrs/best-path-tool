package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.req.PlayerState;
import com.bestpath.data.MapDataLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * On the REAL exported data: a house step says what the player selects in the game's list, with its key:
 * "Jewellery Box -> Burthorpe (5)"; a house portal follows what the player's house has: "Portal Nexus -> Senntisten (M)" or "Enter Senntisten Portal".
 */
public class HouseMenuTitlesTest
{
	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	/** In the house (the data's house spot) to (dx, dy, 0): the step that leaves the house. */
	private static RouteStep fromHouse(PlayerState p, Map<String, String> nexusKeys, int dx, int dy)
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		RouteResult r = engine.route(new RouteJob(1, new RouteRequest(dx, dy, 0),
			new PlayerSnapshot(1857, 7065, 0, p, List.of()).withNexusKeys(nexusKeys), TransportSettings.defaults(), Set.of()));
		assertEquals(RouteStatus.READY, r.getStatus());
		for (RouteStep s : r.getSteps())
		{
			if (!s.getClick().getHouseThings().isEmpty())
			{
				return s;
			}
		}
		throw new AssertionError("no house step: " + r.getSteps());
	}

	private static PlayerState.Builder builder()
	{
		return new PlayerState.Builder("Builder").members(true).house("Rimmington");
	}

	@Test
	public void theJewelleryBoxSaysTheListsWordAndKey()
	{
		// the option the player selects just says "burthorpe", so the title must not say "burthorpe games room"
		RouteStep s = fromHouse(builder().houseThing("basic jewellery box").build(), Map.of(), 2899, 3553);
		assertEquals("Jewellery Box -> Burthorpe (5)", s.getTitle());
		assertEquals("Teleport Menu", s.getClick().getOption());
		assertEquals("Burthorpe", s.getClick().getChoice());
	}

	@Test
	public void theNexusSaysTheDestinationWithThePlayersKey()
	{
		// "it should know if its the nexus or chamber and it should say something like "Nexus: Senntisten (M)""
		PlayerState p = builder().houseThing("portal nexus: Senntisten Teleport").build();
		RouteStep s = fromHouse(p, Map.of("Senntisten", "M"), 3320, 3337);
		assertEquals("Portal Nexus -> Senntisten (M)", s.getTitle());
		assertEquals(Set.of("portal nexus: senntisten teleport"), s.getClick().getHouseThings());
		// the key not known (the house not scanned since): the words without it
		assertEquals("Portal Nexus -> Senntisten", fromHouse(p, Map.of(), 3320, 3337).getTitle());
	}

	@Test
	public void thePortalChamberSaysItsPortal()
	{
		RouteStep s = fromHouse(builder().houseThing("portal chamber: Senntisten Teleport").build(), Map.of("Senntisten", "M"),
			3320, 3337);
		assertEquals("Enter Senntisten Portal", s.getTitle());
		assertEquals("Enter", s.getClick().getOption());
		assertEquals("Senntisten Portal", s.getClick().getTarget());
		assertEquals(Set.of("portal chamber: senntisten teleport"), s.getClick().getHouseThings());
		assertTrue(s.getClick().usesFurniture("portal chamber: Senntisten Teleport"));
	}

	@Test
	public void bothBuiltTheNexusIsUsed()
	{
		RouteStep s = fromHouse(builder().houseThing("portal nexus: Senntisten Teleport")
			.houseThing("portal chamber: Senntisten Teleport").build(), Map.of("Senntisten", "3"), 3320, 3337);
		assertNotNull(s);
		assertEquals("Portal Nexus -> Senntisten (3)", s.getTitle());
	}
}
