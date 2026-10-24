package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.PluginRouterDiagnostics;
import com.bestpath.core.data.SailingSetup;
import com.bestpath.core.path.PathfinderDiagnostics;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Sailing;
import com.bestpath.core.req.BankItem;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.QuestState;
import com.bestpath.core.req.Skill;
import com.bestpath.core.sea.Boat;
import com.bestpath.core.sea.BoatType;
import com.bestpath.core.sea.Dock;
import com.bestpath.data.MapDataLoader;
import com.bestpath.player.GameReading;
import com.bestpath.player.SnapshotMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A member wearing a Construction cape, with a spirit tree in the house, a house tablet only in the bank, the bank stop
 * set to 50 ticks, standing on their boat, asked for a tile beside the Brimhaven spirit tree that no one can stand on.
 * The route went cape -> house -> spirit tree -> Farming Guild -> bank (take out the tablet) -> tablet -> house -> spirit
 * tree -> Brimhaven, 72 ticks, instead of cape -> house -> spirit tree -> Brimhaven, 7 ticks.
 *
 * <p>Cause: with boats the sailing search took the tile as "at sea" (an unwalkable tile near the ocean) and sailed
 * beside it (189 ticks from Port Sarim's buoy), while the bank-first route went to the nearest tile on land that sees it;
 * the two different ends were compared as if they were one. Fixed by settling the real destination once, before any
 * search (core RouteDestination), and the bank-first route never takes out of the bank what a carried transport does
 * as quickly (core BankFirst). Skipped when the export is missing.
 */
public class BankHouseRealDataTest
{
	/** Beside the Brimhaven spirit tree (2800,3203): a tile no one can stand on. */
	private static final RouteRequest BESIDE_THE_TREE = new RouteRequest(2799, 3203, 0);
	private static final int TABLET = 8013;
	private static final int CONSTRUCTION_CAPE = 9790;

	private static RouteEngine engine;

	@BeforeClass
	public static void load()
	{
		engine = MapDataLoader.engine();
	}

	private static PluginRouter router()
	{
		assumeTrue("no exported map data", engine instanceof CoreRouteEngine);
		PluginRouter r = ((CoreRouteEngine) engine).getRouter();
		assumeTrue("no sea data", r.getData().getSea() != null);
		return r;
	}

	/** The player: the Construction cape worn, the house's spirit tree, a house tablet only in the bank. */
	private static PlayerState owner()
	{
		GameReading r = new GameReading();
		r.members = true;
		r.worn[EquipSlot.CAPE.ordinal()] = CONSTRUCTION_CAPE;
		r.bank = new HashMap<>();
		r.bank.put(TABLET, 5);
		r.houseValue = 1; // Rimmington
		r.houseThings.add("spirit tree");
		r.quests.put("Tree Gnome Village", QuestState.DONE);
		r.spiritTrees.add("Brimhaven");
		r.spiritTrees.add("Farming Guild");
		r.levels[Skill.FARMING.ordinal()] = 85;
		r.levels[Skill.CONSTRUCTION.ordinal()] = 99;
		r.levels[Skill.SAILING.ordinal()] = 99;
		return SnapshotMapper.toSnapshot(r).getState();
	}

	private static Boat sloop(int dockId)
	{
		return new Boat(1, BoatType.SLOOP, 2, 1, 1, 1, false, false, Boat.Focus.NONE, dockId);
	}

	private static RouteResult route(int[] from, RouteRequest to, PlayerState p, List<Boat> boats)
	{
		router();
		PlayerSnapshot me = new PlayerSnapshot(from[0], from[1], from[2], p, boats);
		return engine.route(new RouteJob(1, to, me, TransportSettings.defaults().withBankTicks(50), Set.of()));
	}

	private static List<String> titles(RouteResult r)
	{
		List<String> out = new ArrayList<>();
		for (RouteStep s : r.getSteps())
		{
			out.add(s.getTitle() + " (" + s.getTicks() + ")");
		}
		return out;
	}

	/** Port Sarim's dock: its buoy (a boat moored there) and its id. */
	private static Dock portSarim()
	{
		for (Dock d : router().getData().getSea().getDocks())
		{
			if (d.getName().contains("Port Sarim"))
			{
				return d;
			}
		}
		throw new AssertionError("no Port Sarim dock");
	}

	@Test
	public void theOwnersRouteGoesStraightThereWithTheCape()
	{
		Dock sarim = portSarim();
		List<int[]> onBoat = List.of(new int[]{sarim.getBuoyX(), sarim.getBuoyY(), 0}, new int[]{3000, 3150, 0}, new int[]{2880, 3330, 0});
		for (int[] at : onBoat)
		{
			// standing on the boat (moored at Port Sarim, or out at sea): the route starts from the boat's tile
			RouteResult r = route(at, BESIDE_THE_TREE, owner(), List.of(sloop(Boat.AT_START)));
			assertEquals(RouteStatus.READY, r.getStatus());
			String why = at[0] + "," + at[1] + ": " + titles(r);
			assertTrue(why, r.getTotalTicks() <= 22); // two 10-tick teleports and a short walk
			assertTrue(why, r.getSteps().get(0).getTitle().startsWith("Construct. cape"));
			for (RouteStep s : r.getSteps())
			{
				assertFalse(why, s.getKind() == RouteStep.Kind.BANK);
			}
		}
		// on land with the boat moored at Port Sarim, and with no boat at all: the same
		for (List<Boat> boats : List.of(List.of(sloop(sarim.getId())), List.<Boat>of()))
		{
			RouteResult r = route(new int[]{3222, 3218, 0}, BESIDE_THE_TREE, owner(), boats);
			assertEquals(titles(r).toString(), 10 + 10, r.getTotalTicks());
		}
	}

	/**
	 * Every search of a route goes to the same settled tile: the route with what the player carries and the bank-first
	 * route end on the same tile, the bank-first one is only taken when strictly quicker, and it never takes out of the
	 * bank what a carried transport already does as quickly. Unwalkable tiles by the coast near Port Sarim and the
	 * unwalkable tile beside the Brimhaven spirit tree, from the boat and from land.
	 */
	@Test
	public void bothRoutesGoToTheSameTileAndTheBankOnlyWhenQuicker()
	{
		PluginRouter router = router();
		Sailing sea = router.getSailing();
		assumeTrue(sea != null);
		List<int[]> places = new ArrayList<>();
		places.add(new int[]{2799, 3203, 0});
		for (int x = 3030; x < 3060 && places.size() < 4; x++)
		{
			for (int y = 3170; y < 3200 && places.size() < 4; y++)
			{
				if (sea.atSea(x, y, 0) && !sea.isOpenSea(x, y, 0)
					&& PathfinderDiagnostics.besideTiles(PluginRouterDiagnostics.pathfinder(router), x, y, 0)[0].length > 0)
				{
					places.add(new int[]{x, y, 0});
				}
			}
		}
		assertTrue("unwalkable tiles by the coast: " + places.size(), places.size() >= 2);
		Dock sarim = portSarim();
		PlayerState p = owner();
		int[][] starts = {{sarim.getBuoyX(), sarim.getBuoyY(), 0}, {3222, 3218, 0}, {1249, 3740, 0}};
		for (int[] d : places)
		{
			for (int[] s : starts)
			{
				for (List<Boat> boats : List.of(List.of(sloop(Boat.AT_START)), List.of(sloop(sarim.getId()))))
				{
					boolean aboard = boats.get(0).getDockId() == Boat.AT_START;
					if (aboard != (s[0] == sarim.getBuoyX() && s[1] == sarim.getBuoyY()))
					{
						continue;
					}
					PluginRouter.Answer a = router.route(p, s[0], s[1], s[2], d[0], d[1], d[2], id -> false, PluginRouter.TransportFilter.NONE,
						new SailingSetup(boats, Set.of(1), true), new PluginRouter.BankSetup(50, null));
					String why = s[0] + "," + s[1] + " -> " + d[0] + "," + d[1];
					if (a.getBankPlan() == null || !a.getRoute().isFound())
					{
						continue;
					}
					Route without = a.getBankPlan().getWithoutBank();
					if (a.getBankPlan().isBanked())
					{
						assertTrue(why, !without.isFound() || a.getRoute().getTicks() < without.getTicks());
						assertEquals(why, lastTile(without), lastTile(a.getRoute()));
						assertNoRebuy(why, a.getRoute());
					}
				}
			}
		}
	}

	/** The setting to allow or disallow bank detouring: off, no route begins at a bank. */
	@Test
	public void withBankDetoursOffNoRouteBeginsAtABank()
	{
		PluginRouter router = router();
		BankItem spade = new BankItem("Spade", new int[]{952}, 1);
		PluginRouter.Answer on = router.route(owner(), 3222, 3218, 0, 2799, 3203, 0, id -> false, PluginRouter.TransportFilter.NONE,
			SailingSetup.NONE, PluginRouter.BankSetup.fromBank(15, List.of(spade)));
		assertTrue(on.getBankPlan() != null && on.getBankPlan().isBanked());
		PluginRouter.Answer off = router.route(owner(), 3222, 3218, 0, 2799, 3203, 0, id -> false, PluginRouter.TransportFilter.NONE,
			SailingSetup.NONE, PluginRouter.BankSetup.fromBank(15, List.of(spade)).withDetour(false));
		assertTrue(off.getRoute().isFound());
		assertTrue(off.getBankPlan() == null || !off.getBankPlan().isBanked());
		for (Route.Step s : off.getRoute().getSteps())
		{
			assertTrue(s.getKind() != Route.Step.Kind.BANK);
		}
		// saved with the account's settings, and shown in the config panel
		TransportSettings t = TransportSettings.defaults().withBankDetour(false);
		assertFalse(TransportSettings.decode(t.encode()).isBankDetour());
		assertTrue(TransportSettings.defaults().isBankDetour());
	}

	/** A route that must begin at a bank (the destination needs a spade from it) still teleports with the worn cape. */
	@Test
	public void aBankFirstRouteNeverTakesOutWhatTheCapeAlreadyDoes()
	{
		PluginRouter router = router();
		BankItem spade = new BankItem("Spade", new int[]{952}, 1);
		PluginRouter.Answer a = router.route(owner(), 3222, 3218, 0, 2799, 3203, 0, id -> false, PluginRouter.TransportFilter.NONE,
			SailingSetup.NONE, PluginRouter.BankSetup.fromBank(15, List.of(spade)));
		assertTrue(a.getRoute().isFound());
		assertTrue(a.getBankPlan().isBanked());
		Route.Step bank = null;
		for (Route.Step s : a.getRoute().getSteps())
		{
			if (s.getKind() == Route.Step.Kind.BANK)
			{
				bank = s;
			}
		}
		assertNotNull(bank);
		assertEquals("only the spade, never the house tablet", List.of("Spade"), bank.getTakeOut());
		assertNoRebuy("Lumbridge", a.getRoute());
	}

	private static int lastTile(Route r)
	{
		List<Route.Step> steps = r.getSteps();
		int[] t = steps.get(steps.size() - 1).getTiles();
		return t[t.length - 1];
	}

	/** No step after the bank uses a house tablet while the Construction cape is worn. */
	private static void assertNoRebuy(String why, Route r)
	{
		for (Route.Step s : r.getSteps())
		{
			assertFalse(why, s.getTransport() != null && s.getTransport().getName().startsWith("Teleport to house (tablet)"));
		}
	}
}
