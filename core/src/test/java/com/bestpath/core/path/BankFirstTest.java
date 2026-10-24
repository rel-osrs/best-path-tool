package com.bestpath.core.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.graph.CoarseGraph;
import com.bestpath.core.req.BankItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Going to a bank first: (b) only when strictly quicker than (a), forced when the destination
 * needs something only in the bank, the bank card's items, and the many-targets search to the banks against the
 * brute-force checker.
 */
class BankFirstTest
{
	private static final int BANK = 15;

	private static Pathfinder open()
	{
		CollisionMap m = new TestWorld().map();
		return new Pathfinder(m, CoarseGraph.build(m), null);
	}

	private static PathQuery query(int sx, int sy, int dx, int dy, List<Transport> transports)
	{
		return new PathQuery(sx, sy, 0, dx, dy, 0, transports, 2);
	}

	/** Bank items of the test transports: "law rune" for every transport whose id starts with "bank". */
	private static List<BankItem> items(Transport t)
	{
		return t.getId().startsWith("bank") ? List.of(item(t.getId().substring(5))) : List.of();
	}

	/** A made-up bank item: its words, its own made-up item, take out 1. */
	private static BankItem item(String label)
	{
		return new BankItem(label, new int[]{label.hashCode() & 0xffff}, 1); // each thing its own item
	}

	private static BankFirst.Plan plan(Pathfinder pf, int sx, int sy, int dx, int dy, List<Transport> carried, List<Transport> withBank,
		BankFirst.Banks banks, boolean prefer, List<BankItem> destItems)
	{
		PathQuery a = query(sx, sy, dx, dy, carried);
		return BankFirst.plan(pf, pf.find(a), a, query(sx, sy, dx, dy, withBank), banks, BANK, prefer, BankFirstTest::items,
			destItems);
	}

	private static String kinds(Route r)
	{
		StringBuilder sb = new StringBuilder();
		for (Route.Step s : r.getSteps())
		{
			sb.append(sb.length() == 0 ? "" : " ").append(s.getKind());
		}
		return sb.toString();
	}

	@Test
	void theBankIsUsedWhenItIsQuicker()
	{
		Pathfinder pf = open();
		Transport tele = Transport.teleport("bank law rune", "Teleport near the end", 3375, 3375, 0, 5, true);
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{TestWorld.tile(3215, 3210)}, new String[]{"Test bank"});
		BankFirst.Plan p = plan(pf, 3210, 3210, 3380, 3380, List.of(), List.of(tele), banks, false, List.of());
		assertEquals(170, p.getWithoutBank().getTicks(), "walking all the way");
		assertTrue(p.isBanked());
		Route r = p.getRoute();
		assertEquals("WALK BANK TELEPORT WALK", kinds(r));
		assertEquals(5 + BANK + 5 + 5, r.getTicks());
		Route.Step bank = r.getSteps().get(1);
		assertEquals("Test bank", bank.getBankName());
		assertEquals(List.of("law rune"), bank.getTakeOut());
		assertEquals(TestWorld.tile(3215, 3210), bank.getTiles()[0]);
		// each part is a normal search: the brute-force checker agrees part by part
		CollisionMap m = PathfinderDiagnostics.map(pf);
		int toBank = BruteForce.find(m, query(3210, 3210, 3215, 3210, List.of())).getTicks();
		int on = BruteForce.find(m, query(3215, 3210, 3380, 3380, List.of(tele))).getTicks();
		assertEquals(toBank + BANK + on, r.getTicks());
		int sum = 0;
		for (Route.Step s : r.getSteps())
		{
			sum += s.getTicks();
		}
		assertEquals(r.getTicks(), sum, "the steps add up");
	}

	@Test
	void theNormalRouteStaysWhenTheBankIsNotQuicker()
	{
		Pathfinder pf = open();
		Transport tele = Transport.teleport("bank law rune", "Teleport", 3375, 3375, 0, 5, true);
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{TestWorld.tile(3215, 3210)}, new String[]{"Test bank"});
		BankFirst.Plan p = plan(pf, 3210, 3210, 3230, 3210, List.of(), List.of(tele), banks, false, List.of());
		assertFalse(p.isBanked());
		assertEquals(20, p.getRoute().getTicks());
		assertEquals("WALK", kinds(p.getRoute()));
	}

	@Test
	void aTieKeepsTheRouteWithoutTheBank()
	{
		Pathfinder pf = open();
		// (a) walks 30; (b) banks where the player stands (15), teleports (5) and walks 10: also 30
		Transport tele = Transport.teleport("bank law rune", "Teleport", 3250, 3210, 0, 5, true);
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{TestWorld.tile(3210, 3210)}, new String[]{"Test bank"});
		BankFirst.Plan p = plan(pf, 3210, 3210, 3240, 3210, List.of(), List.of(tele), banks, false, List.of());
		assertEquals(30, p.getRoute().getTicks());
		assertFalse(p.isBanked());
		// one tick less for the bank and it wins
		Transport quicker = Transport.teleport("bank law rune", "Teleport", 3249, 3210, 0, 5, true);
		BankFirst.Plan q = plan(pf, 3210, 3210, 3240, 3210, List.of(), List.of(quicker), banks, false, List.of());
		assertTrue(q.isBanked());
		assertEquals(29, q.getRoute().getTicks());
		assertEquals("BANK TELEPORT WALK", kinds(q.getRoute()), "the player stands at the bank: no walk to it");
	}

	@Test
	void theBankRouteNeverTakesOutWhatACarriedTransportDoesAsQuickly()
	{
		// a house tablet taken out of the bank although the worn Construction cape teleports into
		// the house just as quickly; here a spade from the bank forces the bank-first route
		Pathfinder pf = open();
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{TestWorld.tile(3215, 3210)}, new String[]{"Test bank"});
		Transport tablet = Transport.teleport("bank house tablet", "Tablet", 3375, 3375, 0, 5, true);
		Transport cape = Transport.teleport("cape", "Cape", 3375, 3375, 0, 5, true);
		BankFirst.Plan p = plan(pf, 3210, 3210, 3380, 3380, List.of(cape), List.of(tablet, cape), banks, true, List.of(item("spade")));
		assertTrue(p.isBanked());
		assertEquals(5 + BANK + 5 + 5, p.getRoute().getTicks());
		assertEquals(List.of("spade"), p.getRoute().getSteps().get(1).getTakeOut());
		assertEquals("cape", p.getRoute().getSteps().get(2).getTransport().getId());
		// a quicker way from the bank's items is still taken
		Transport quick = Transport.teleport("bank house tablet", "Tablet", 3375, 3375, 0, 3, true);
		BankFirst.Plan q = plan(pf, 3210, 3210, 3380, 3380, List.of(cape), List.of(quick, cape), banks, true, List.of(item("spade")));
		assertEquals(List.of("house tablet", "spade"), q.getRoute().getSteps().get(1).getTakeOut());
		assertEquals(5 + BANK + 3 + 5, q.getRoute().getTicks());
	}

	@Test
	void aDestinationThatNeedsBankItemsAlwaysGoesToTheBank()
	{
		Pathfinder pf = open();
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{TestWorld.tile(3205, 3210), TestWorld.tile(3300, 3300)},
			new String[]{"West bank", "Far bank"});
		// a clue dig 20 tiles east needs a spade, which is in the bank: (a) is 20 ticks but not acceptable
		BankFirst.Plan p = plan(pf, 3210, 3210, 3230, 3210, List.of(), List.of(), banks, true, List.of(item("spade")));
		assertTrue(p.isBanked());
		assertEquals(20, p.getWithoutBank().getTicks());
		assertEquals(5 + BANK + 25, p.getRoute().getTicks());
		Route.Step bank = p.getRoute().getSteps().get(1);
		assertEquals("West bank", bank.getBankName());
		assertEquals(List.of("spade"), bank.getTakeOut());
	}

	/**
	 * The nearest bank's route (bank, teleport, walk: 30 ticks) is found first; a bank 50 ticks away that is still
	 * nearer than the route without the bank (170) can't beat it, so the search to the banks stops long before it.
	 */
	@Test
	void banksThatCantBeatTheNearestBanksRouteAreNotLookedFor()
	{
		Pathfinder pf = open();
		Transport tele = Transport.teleport("bank law rune", "Teleport near the end", 3375, 3375, 0, 5, true);
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{TestWorld.tile(3260, 3210), TestWorld.tile(3215, 3210)},
			new String[]{"Far bank", "Near bank"});
		BankFirst.Plan p = plan(pf, 3210, 3210, 3380, 3380, List.of(), List.of(tele), banks, false, List.of());
		assertEquals(170, p.getWithoutBank().getTicks());
		assertTrue(p.isBanked());
		assertEquals("Near bank", p.getRoute().getSteps().get(1).getBankName());
		assertEquals(5 + BANK + 5 + 5, p.getRoute().getTicks());
		assertEquals(1, p.getBanksReached(), "only the near bank started the route on");
		// the search to the banks as the route asks it: the far bank is never reached
		PathQuery a = query(3210, 3210, 3380, 3380, List.of());
		Pathfinder.StartBounds lb = pf.startBounds(a.withTransports(List.of(tele)), banks.tiles());
		int[] after = {lb.at(0) + BANK, lb.at(1) + BANK};
		int[] to = pf.ticksTo(a, banks.tiles(), after, lb.teleport + BANK, 31, new Route[2]);
		assertEquals(-1, to[0]);
		assertEquals(5, to[1]);
	}

	@Test
	void aBankOnTheWayBeatsTheNearestBank()
	{
		Pathfinder pf = open();
		// a shortcut that needs a rope from the bank, east of the player
		Transport rope = Transport.between("bank rope", "Rope swing", 3230, 3210, 0, 3350, 3210, 0, 5, false, true);
		BankFirst.Banks banks = new BankFirst.Banks(new int[]{TestWorld.tile(3205, 3210), TestWorld.tile(3218, 3210)},
			new String[]{"Behind bank", "On the way bank"});
		BankFirst.Plan p = plan(pf, 3210, 3210, 3360, 3210, List.of(), List.of(rope), banks, false, List.of());
		assertEquals(150, p.getWithoutBank().getTicks());
		assertTrue(p.isBanked());
		assertEquals("On the way bank", p.getRoute().getSteps().get(1).getBankName());
		assertEquals(8 + BANK + 12 + 5 + 10, p.getRoute().getTicks());
		assertEquals(List.of("rope"), p.getRoute().getSteps().get(1).getTakeOut());
	}

	@Test
	void noBankOrNothingNewKeepsTheNormalRoute()
	{
		Pathfinder pf = open();
		PathQuery a = query(3210, 3210, 3380, 3380, List.of());
		Route ra = pf.find(a);
		BankFirst.Plan none = BankFirst.plan(pf, ra, a, a, BankFirst.Banks.NONE, BANK, false, BankFirstTest::items, List.of());
		assertFalse(none.isBanked());
		assertEquals(ra.getTicks(), none.getRoute().getTicks());
		BankFirst.Plan off = BankFirst.plan(pf, ra, a, null, new BankFirst.Banks(new int[]{TestWorld.tile(3211, 3210)},
			new String[]{"Bank"}), BANK, false, BankFirstTest::items, List.of());
		assertFalse(off.isBanked());
		assertEquals(0, off.getBanksReached());
	}

	/** The ticks to every bank tile at once against the brute-force checker, on random worlds with walls, blocks and doors. */
	@Test
	void theTicksToEveryBankAreTheBruteForceTicks()
	{
		Random rnd = new Random(20261014);
		for (int world = 0; world < 6; world++)
		{
			TestWorld w = TestWorld.random(rnd, 1 + world % 3);
			CollisionMap m = w.map();
			Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
			List<Transport> transports = randomTransports(rnd, "t", 6);
			int[] tiles = new int[30];
			for (int i = 0; i < tiles.length; i++)
			{
				tiles[i] = TestWorld.tile(rx(rnd), ry(rnd));
			}
			for (int s = 0; s < 4; s++)
			{
				PathQuery q = new PathQuery(rx(rnd), ry(rnd), 0, 0, 0, 0, transports, 2, w.doorMap(), 2);
				int limit = s == 3 ? 60 : Integer.MAX_VALUE / 8;
				int[] got = pf.ticksTo(q, tiles, limit);
				for (int i = 0; i < tiles.length; i++)
				{
					Route b = BruteForce.find(m, q.withDestination(Tiles.x(tiles[i]), Tiles.y(tiles[i]), 0));
					int want = b.isFound() && b.getTicks() <= limit ? b.getTicks() : -1;
					assertEquals(want, got[i], "world " + world + " start " + s + " bank tile " + i);
				}
			}
		}
	}

	/**
	 * The search to the banks cut short (only what can still beat a route of {@code best} ticks), against the
	 * brute-force checker: every bank tile that can (its ticks plus its "after" under best) has its exact ticks and
	 * the way there, the nearest ones too while a teleport from them can, and nothing found is ever wrong.
	 */
	@Test
	void theSearchToTheBanksFindsEveryBankThatCanStillWin()
	{
		Random rnd = new Random(20261017);
		int cut = 0;
		for (int world = 0; world < 6; world++)
		{
			TestWorld w = TestWorld.random(rnd, 1 + world % 3);
			CollisionMap m = w.map();
			Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
			List<Transport> transports = randomTransports(rnd, "t", 6);
			transports.add(Transport.teleport("tele", "Teleport", rx(rnd), ry(rnd), 0, 5 + rnd.nextInt(30), true));
			int[] tiles = new int[20];
			int[] after = new int[tiles.length];
			for (int i = 0; i < tiles.length; i++)
			{
				tiles[i] = TestWorld.tile(rx(rnd), ry(rnd));
				after[i] = rnd.nextInt(8) == 0 ? Integer.MAX_VALUE / 4 : rnd.nextInt(150);
			}
			for (int s = 0; s < 4; s++)
			{
				PathQuery q = new PathQuery(rx(rnd), ry(rnd), 0, 0, 0, 0, transports, 2, w.doorMap(), 2);
				int best = 60 + rnd.nextInt(200);
				int anyAfter = rnd.nextInt(80);
				Route[] ways = new Route[tiles.length];
				int[] got = pf.ticksTo(q, tiles, after, anyAfter, best, ways);
				int[] want = new int[tiles.length];
				int nearest = Integer.MAX_VALUE;
				for (int i = 0; i < tiles.length; i++)
				{
					Route b = BruteForce.find(m, q.withDestination(Tiles.x(tiles[i]), Tiles.y(tiles[i]), 0));
					want[i] = b.isFound() ? b.getTicks() : -1;
					nearest = want[i] >= 0 ? Math.min(nearest, want[i]) : nearest;
				}
				for (int i = 0; i < tiles.length; i++)
				{
					String where = "world " + world + " start " + s + " bank tile " + i;
					boolean helps = want[i] >= 0 && (long) want[i] + after[i] < best;
					boolean near = want[i] == nearest && (long) nearest + anyAfter < best;
					if (helps || near)
					{
						assertEquals(want[i], got[i], where);
						assertEquals(want[i], ways[i].getTicks(), where + ": the way there");
						List<Route.Step> steps = ways[i].getSteps();
						int[] last = steps.get(steps.size() - 1).getTiles();
						assertEquals(tiles[i], last[last.length - 1], where + ": ends on the bank tile");
						assertEquals(want[i], steps.stream().mapToInt(Route.Step::getTicks).sum(), where + ": the steps add up");
					}
					else if (got[i] >= 0)
					{
						assertEquals(want[i], got[i], where + ": found anyway, so exact");
					}
					else if (want[i] >= 0)
					{
						cut++;
					}
				}
			}
		}
		assertTrue(cut > 20, "enough reachable bank tiles never looked for: " + cut);
	}

	/**
	 * The search to the banks aimed by the coarse bounds with the bank's items, against the
	 * brute-force checker: every bank tile through which a route of fewer than {@code best} ticks really goes (the way
	 * there, the stop, the way on without teleports) is found with its exact ticks and way; nothing found is wrong.
	 */
	@Test
	void theAimedSearchToTheBanksFindsEveryBankThatCanReallyWin()
	{
		Random rnd = new Random(20261018);
		int checked = 0;
		for (int world = 0; world < 6; world++)
		{
			TestWorld w = TestWorld.random(rnd, 1 + world % 3);
			CollisionMap m = w.map();
			Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
			List<Transport> carried = randomTransports(rnd, "t", 4);
			carried.add(Transport.teleport("tele", "Teleport", rx(rnd), ry(rnd), 0, 5 + rnd.nextInt(30), true));
			List<Transport> withBank = new ArrayList<>(carried);
			withBank.addAll(randomTransports(rnd, "b", 3));
			withBank.add(Transport.teleport("bank-tele", "Bank teleport", rx(rnd), ry(rnd), 0, 5 + rnd.nextInt(30), true));
			List<Transport> noTeleports = new ArrayList<>();
			for (Transport t : withBank)
			{
				if (t.hasStart())
				{
					noTeleports.add(t);
				}
			}
			int[] tiles = new int[16];
			for (int i = 0; i < tiles.length; i++)
			{
				tiles[i] = TestWorld.tile(rx(rnd), ry(rnd));
			}
			for (int s = 0; s < 4; s++)
			{
				int dx = rx(rnd);
				int dy = ry(rnd);
				PathQuery q = new PathQuery(rx(rnd), ry(rnd), 0, dx, dy, 0, carried, 2, w.doorMap(), 2);
				PathQuery onward = q.withTransports(withBank);
				Pathfinder.StartBounds lb = pf.startBounds(onward, tiles);
				if (lb == null)
				{
					continue;
				}
				int[] after = new int[tiles.length];
				for (int i = 0; i < tiles.length; i++)
				{
					after[i] = lb.at(i) >= FastSearch.NEVER ? Integer.MAX_VALUE / 4 : lb.at(i) + BANK;
				}
				int best = 40 + rnd.nextInt(200);
				Route[] ways = new Route[tiles.length];
				int[] got = pf.ticksTo(q, tiles, after, Integer.MAX_VALUE / 4, best, ways, lb, BANK);
				for (int i = 0; i < tiles.length; i++)
				{
					String where = "world " + world + " start " + s + " bank tile " + i;
					Route to = BruteForce.find(m, q.withDestination(Tiles.x(tiles[i]), Tiles.y(tiles[i]), 0));
					Route on = BruteForce.find(m, new PathQuery(Tiles.x(tiles[i]), Tiles.y(tiles[i]), 0, dx, dy, 0, noTeleports, 2, w.doorMap(), 2));
					if (to.isFound() && on.isFound() && to.getTicks() + BANK + on.getTicks() < best)
					{
						assertEquals(to.getTicks(), got[i], where);
						assertEquals(to.getTicks(), ways[i].getTicks(), where + ": the way there");
						checked++;
					}
					else if (got[i] >= 0)
					{
						assertEquals(to.getTicks(), got[i], where + ": found anyway, so exact");
					}
				}
			}
		}
		assertTrue(checked > 10, "bank tiles a route really goes through: " + checked);
	}

	/**
	 * The coarse bounds the bank-first route prunes with, against the brute-force checker: from the start, from each
	 * bank tile without a teleport, and through each teleport; and a search aimed by them (with fewer transports and
	 * another start) is as short as a normal search.
	 */
	@Test
	void theBoundsFromEachBankAreLowerBounds()
	{
		Random rnd = new Random(1017);
		for (int world = 0; world < 6; world++)
		{
			TestWorld w = TestWorld.random(rnd, 1 + world % 3);
			CollisionMap m = w.map();
			Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
			List<Transport> walking = randomTransports(rnd, "t", 6);
			List<Transport> transports = new ArrayList<>(walking);
			transports.add(Transport.teleport("tele 1", "Teleport", rx(rnd), ry(rnd), 0, 5 + rnd.nextInt(30), true));
			transports.add(Transport.teleport("tele 2", "Teleport", rx(rnd), ry(rnd), 0, 5 + rnd.nextInt(30), true));
			int[] tiles = new int[12];
			for (int i = 0; i < tiles.length; i++)
			{
				tiles[i] = TestWorld.tile(rx(rnd), ry(rnd));
			}
			for (int s = 0; s < 4; s++)
			{
				PathQuery q = new PathQuery(rx(rnd), ry(rnd), 0, rx(rnd), ry(rnd), 0, transports, 2, w.doorMap(), 2);
				String where = "world " + world + " route " + s;
				Route whole = BruteForce.find(m, q);
				Pathfinder.StartBounds lb = pf.startBounds(q, tiles);
				if (lb == null)
				{
					assertFalse(whole.isFound(), where + ": no bounds, no route");
					continue;
				}
				if (whole.isFound())
				{
					assertTrue(lb.whole <= whole.getTicks(), where + ": from the start");
				}
				for (int i = 0; i < tiles.length; i++)
				{
					Route b = BruteForce.find(m, PathQueryBuilder.withStart(q, Tiles.x(tiles[i]), Tiles.y(tiles[i]), 0).withTransports(walking));
					if (b.isFound())
					{
						assertTrue(lb.at(i) <= b.getTicks(), where + ": from bank tile " + i);
					}
				}
				for (Transport t : transports.subList(walking.size(), transports.size()))
				{
					Route b = BruteForce.find(m, PathQueryBuilder.withStart(q, t.getEndX(), t.getEndY(), 0).withTransports(walking));
					if (b.isFound())
					{
						assertTrue(lb.teleport <= t.cost(2) + b.getTicks(), where + ": through " + t.getId());
					}
				}
				// aimed by those bounds: another start, only the transports with a start
				PathQuery other = PathQueryBuilder.withStart(q, rx(rnd), ry(rnd), 0).withTransports(walking.subList(0, 4));
				Route normal = pf.find(other);
				Route aimed = pf.find(other, Integer.MAX_VALUE, lb);
				assertEquals(normal.isFound(), aimed.isFound(), where + ": aimed by the bounds");
				assertEquals(normal.getTicks(), aimed.getTicks(), where + ": aimed by the bounds");
			}
		}
	}

	/** A route of at most a limit: found as without the limit when it fits, else not found. */
	@Test
	void aRouteWithinALimit()
	{
		Random rnd = new Random(2026);
		for (int world = 0; world < 6; world++)
		{
			TestWorld w = TestWorld.random(rnd, 1 + world % 3);
			CollisionMap m = w.map();
			Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
			List<Transport> transports = randomTransports(rnd, "t", 6);
			transports.add(Transport.teleport("tele", "Teleport", rx(rnd), ry(rnd), 0, 5 + rnd.nextInt(30), true));
			for (int s = 0; s < 6; s++)
			{
				PathQuery q = new PathQuery(rx(rnd), ry(rnd), 0, rx(rnd), ry(rnd), 0, transports, 2, w.doorMap(), 2);
				Route full = pf.find(q);
				if (!full.isFound())
				{
					assertFalse(pf.find(q, 1000).isFound());
					continue;
				}
				int t = full.getTicks();
				assertEquals(t, pf.find(q, t).getTicks(), "world " + world + " route " + s);
				assertEquals(t, pf.find(q, t + 7).getTicks(), "world " + world + " route " + s);
				assertFalse(pf.find(q, t - 1).isFound(), "world " + world + " route " + s);
			}
		}
	}

	/**
	 * Bank-first against brute force on random worlds: the route shown is the cheaper of (a) the brute-force route
	 * with what the player carries and (b) the minimum over EVERY bank tile of (brute force there + the stop + brute
	 * force on from it with the bank's items), a tie keeping (a); with a destination that needs a bank item, (b).
	 * Every part of a route that banks is itself as short as brute force says.
	 */
	@Test
	void bankFirstIsTheBruteForceMinimumOverEveryBank()
	{
		Random rnd = new Random(7_2026_10_14L);
		int banked = 0;
		int checked = 0;
		for (int world = 0; world < 16; world++)
		{
			TestWorld w = TestWorld.random(rnd, 1 + world % 3);
			CollisionMap m = w.map();
			Pathfinder pf = new Pathfinder(m, CoarseGraph.build(m), null);
			List<Transport> carried = randomTransports(rnd, "t", 4);
			if (world % 2 == 1)
			{
				// a teleport the player carries: usable on the way to the bank, and from the bank on
				carried.add(Transport.teleport("home", "Home teleport", rx(rnd), ry(rnd), 0, 10 + rnd.nextInt(30), true));
			}
			List<Transport> withBank = new ArrayList<>(carried);
			withBank.addAll(randomTransports(rnd, "bank rope", 4)); // usable only with the bank's items
			withBank.add(Transport.teleport("bank law rune", "Bank teleport", rx(rnd), ry(rnd), 0, 5 + rnd.nextInt(20), true));
			int[] tiles = new int[12];
			String[] names = new String[tiles.length];
			for (int i = 0; i < tiles.length; i++)
			{
				tiles[i] = TestWorld.tile(rx(rnd), ry(rnd));
				names[i] = "Bank " + (i / 3); // three tiles per bank
			}
			BankFirst.Banks banks = new BankFirst.Banks(tiles, names);
			for (int s = 0; s < 8; s++)
			{
				int sx = rx(rnd);
				int sy = ry(rnd);
				int dx = rx(rnd);
				int dy = ry(rnd);
				boolean prefer = s == 7;
				PathQuery a = new PathQuery(sx, sy, 0, dx, dy, 0, carried, 2, w.doorMap(), 2);
				PathQuery on = a.withTransports(withBank);
				Route ra = pf.find(a);
				BankFirst.Plan p = BankFirst.plan(pf, ra, a, on, banks, BANK, prefer, BankFirstTest::items, prefer ? List.of(item("spade")) : List.of());
				// brute force
				Route ba = BruteForce.find(m, a);
				int bestB = Integer.MAX_VALUE;
				for (int t : banks.tiles())
				{
					Route to = BruteForce.find(m, a.withDestination(Tiles.x(t), Tiles.y(t), 0));
					Route from = BruteForce.find(m, PathQueryBuilder.withStart(on, Tiles.x(t), Tiles.y(t), 0));
					if (to.isFound() && from.isFound())
					{
						bestB = Math.min(bestB, to.getTicks() + BANK + from.getTicks());
					}
				}
				boolean bWins = bestB < Integer.MAX_VALUE && (prefer || !ba.isFound() || bestB < ba.getTicks());
				String where = "world " + world + " route " + s + " (" + sx + "," + sy + " -> " + dx + "," + dy + ")";
				assertEquals(ra.getTicks(), ba.getTicks(), where + ": (a)");
				assertEquals(bWins, p.isBanked(), where + ": banks");
				assertEquals(bWins ? bestB : ba.getTicks(), p.getRoute().getTicks(), where + ": ticks");
				checked++;
				if (!bWins)
				{
					continue;
				}
				banked++;
				// the parts: to the bank with what the player carries, the stop, on with the bank's items
				List<Route.Step> steps = p.getRoute().getSteps();
				int sum = 0;
				int beforeBank = 0;
				Route.Step bank = null;
				for (Route.Step st : steps)
				{
					sum += st.getTicks();
					if (st.getKind() == Route.Step.Kind.BANK)
					{
						assertTrue(bank == null, where + ": one bank step");
						bank = st;
						beforeBank = sum - st.getTicks();
					}
				}
				assertEquals(p.getRoute().getTicks(), sum, where + ": the steps add up");
				int t = bank.getTiles()[0];
				assertEquals(banks.bankAt(t), bank.getBankName(), where);
				assertEquals(BruteForce.find(m, a.withDestination(Tiles.x(t), Tiles.y(t), 0)).getTicks(), beforeBank, where + ": to the bank");
				assertEquals(BruteForce.find(m, PathQueryBuilder.withStart(on, Tiles.x(t), Tiles.y(t), 0)).getTicks(), sum - beforeBank - BANK,
					where + ": on from the bank");
				if (prefer)
				{
					assertTrue(bank.getTakeOut().contains("spade"), where);
				}
			}
		}
		assertTrue(banked >= 15, "enough routes that bank: " + banked + " of " + checked);
	}

	/** Random shortcuts with ids starting {@code id}. */
	private static List<Transport> randomTransports(Random rnd, String id, int n)
	{
		List<Transport> out = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			out.add(Transport.between(id + " " + i, "Shortcut " + i, rx(rnd), ry(rnd), 0, rx(rnd), ry(rnd), 0, 1 + rnd.nextInt(20),
				rnd.nextBoolean(), true));
		}
		return out;
	}

	private static int rx(Random rnd)
	{
		return TestWorld.X0 + 2 + rnd.nextInt(TestWorld.SIZE - 4);
	}

	private static int ry(Random rnd)
	{
		return TestWorld.Y0 + 2 + rnd.nextInt(TestWorld.SIZE - 4);
	}
}
