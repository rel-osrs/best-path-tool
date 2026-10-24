package com.bestpath.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.req.BankItem;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.RequirementText;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Routes that begin at a bank in the plugin's router: the player's transports split into what
 * they carry and what the bank adds, the bank stop's ticks, a destination that needs a bank item, and the banks'
 * own needs.
 */
class PluginBankTest
{
	private static final int LAW = 563;
	private static final int SPADE = 952;

	/** One open region (3200-3263, 3200-3263); a teleport near the north-east corner that needs a law rune; one bank. */
	private static PluginRouter router(String bankLines)
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		TransportLine tele = TransportLine.builder("t-tele", TransportLine.Kind.TELEPORT).name("Test teleport")
			.category("TELEPORT").end(3250, 3250, 0).ticks(5).requirements("item " + LAW + " used up")
			.teleportCategory("spell").build();
		FreeLand free = new FreeLand.Builder().set(3200, 3200, 3263, 3263, true).build();
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(tele),
			Tables.empty(), free);
		return new PluginRouter(PluginDataBuilder.withBanks(data, BankList.parse(BankList.HEADER + "\n" + bankLines)), Long.MAX_VALUE);
	}

	private static final String ONE_BANK = "Test bank\tbooth\t\t3205,3201,0\n";

	private static PlayerState.Builder member()
	{
		return new PlayerState.Builder("p").members(true);
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
	void carriedItemsNeedNoBank()
	{
		PluginRouter.Answer a = PluginRouterDiagnostics.route(router(ONE_BANK), member().carry(LAW, 1).build(), 3201, 3201, 0, 3251, 3250, 0, id -> false);
		assertEquals(6, a.getRoute().getTicks());
		assertEquals("TELEPORT WALK", kinds(a.getRoute()));
		assertNull(a.getBankPlan(), "nothing in the bank: not even tried");
		assertEquals(1, a.getTransports().getTransports().size());
		assertFalse(a.getTransports().bankMatters());
	}

	@Test
	void anItemInTheBankMakesTheRouteBeginAtTheBank()
	{
		PlayerState p = member().bank(LAW, 10).build();
		PluginRouter.Answer a = PluginRouterDiagnostics.route(router(ONE_BANK), p, 3201, 3201, 0, 3251, 3250, 0, id -> false);
		Route r = a.getRoute();
		assertTrue(a.getBankPlan().isBanked());
		assertEquals(50, a.getBankPlan().getWithoutBank().getTicks(), "walking all the way");
		assertEquals("WALK BANK TELEPORT WALK", kinds(r));
		assertEquals(4 + com.bestpath.core.path.BankFirst.DEFAULT_TICKS + 5 + 1, r.getTicks());
		Route.Step bank = r.getSteps().get(1);
		assertEquals("Test bank", bank.getBankName());
		assertEquals(List.of(String.valueOf(LAW)), bank.getTakeOut());
		// the bank card's box shows each with its picture and count: the items and how many
		assertEquals(List.of(new com.bestpath.core.req.BankItem(String.valueOf(LAW), new int[]{LAW}, 1, true)), bank.getTakeOutItems());
		assertEquals(Tiles.pack(3205, 3201, 0), bank.getTiles()[0]);
		assertEquals(0, a.getTransports().getTransports().size(), "with what the player carries: no teleport");
		assertEquals(1, a.getTransports().getWithBank().size());
		assertEquals(List.of(String.valueOf(LAW)), BankItem.labels(a.getTransports().takeOut(a.getTransports().getWithBank().get(0))));
	}

	@Test
	void theBankStopsTicksDecide()
	{
		PlayerState p = member().bank(LAW, 10).build();
		PluginRouter r = router(ONE_BANK);
		// 4 + 40 + 5 + 1 = 50: no quicker than walking, so no bank
		PluginRouter.Answer tie = r.route(p, 3201, 3201, 0, 3251, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE,
			SailingSetup.NONE, new PluginRouter.BankSetup(40, null));
		assertEquals(50, tie.getRoute().getTicks());
		assertEquals("WALK", kinds(tie.getRoute()));
		assertFalse(tie.getBankPlan().isBanked());
		PluginRouter.Answer quick = r.route(p, 3201, 3201, 0, 3251, 3250, 0, id -> false, PluginRouter.TransportFilter.NONE,
			SailingSetup.NONE, new PluginRouter.BankSetup(39, null));
		assertEquals(49, quick.getRoute().getTicks());
		assertTrue(quick.getBankPlan().isBanked());
	}

	@Test
	void noBankDataNoBankRoute()
	{
		PlayerState p = member().bank(LAW, 10).build();
		PluginRouter.Answer a = PluginRouterDiagnostics.route(router(""), p, 3201, 3201, 0, 3251, 3250, 0, id -> false);
		assertEquals(50, a.getRoute().getTicks());
		assertNull(a.getBankPlan());
	}

	@Test
	void aDestinationThatNeedsABankItemAlwaysStartsAtTheBank()
	{
		PluginRouter r = router(ONE_BANK);
		PluginRouter.BankSetup spade = new PluginRouter.BankSetup(15, RequirementText.parse("item " + SPADE, RequirementText.ItemGroups.NONE));
		// a dig 10 tiles away; the spade is in the bank: the bank first, though walking straight there is quicker
		PluginRouter.Answer a = r.route(member().bank(SPADE, 1).build(), 3201, 3201, 0, 3211, 3201, 0, id -> false,
			PluginRouter.TransportFilter.NONE, SailingSetup.NONE, spade);
		assertEquals("WALK BANK WALK", kinds(a.getRoute()));
		assertEquals(4 + 15 + 6, a.getRoute().getTicks());
		assertEquals(10, a.getBankPlan().getWithoutBank().getTicks());
		assertEquals(List.of(String.valueOf(SPADE)), a.getRoute().getSteps().get(1).getTakeOut());
		// carried: no bank
		PluginRouter.Answer carried = r.route(member().carry(SPADE, 1).build(), 3201, 3201, 0, 3211, 3201, 0, id -> false,
			PluginRouter.TransportFilter.NONE, SailingSetup.NONE, spade);
		assertEquals(10, carried.getRoute().getTicks());
		assertNull(carried.getBankPlan());
		// the needs worked out by the caller (the plugin's clue card): the same
		PluginRouter.Answer listed = r.route(member().build(), 3201, 3201, 0, 3211, 3201, 0, id -> false,
			PluginRouter.TransportFilter.NONE, SailingSetup.NONE, PluginRouter.BankSetup.fromBank(15,
				List.of(new com.bestpath.core.req.BankItem("Spade", new int[]{SPADE}, 1))));
		assertEquals("WALK BANK WALK", kinds(listed.getRoute()));
		assertEquals(List.of("Spade"), listed.getRoute().getSteps().get(1).getTakeOut());
		assertEquals(SPADE, listed.getRoute().getSteps().get(1).getTakeOutItems().get(0).getItemIds()[0]);
		PluginRouter.Answer none = r.route(member().build(), 3201, 3201, 0, 3211, 3201, 0, id -> false,
			PluginRouter.TransportFilter.NONE, SailingSetup.NONE, PluginRouter.BankSetup.fromBank(15, List.of()));
		assertEquals("WALK", kinds(none.getRoute()));
		// several places (a clue's): the same
		PluginRouter.NearestAnswer n = r.routeNearest(member().bank(SPADE, 1).build(), 3201, 3201, 0,
			new int[]{Tiles.pack(3211, 3201, 0), Tiles.pack(3260, 3260, 0)}, id -> false, PluginRouter.TransportFilter.NONE, 5,
			SailingSetup.NONE, spade);
		assertEquals(0, n.getChosen());
		assertEquals("WALK BANK WALK", kinds(n.getAnswer().getRoute()));
	}

	@Test
	void aBankWhoseNeedsAreNotMetIsNotUsed()
	{
		String lines = "Locked bank\tchest\tvarbit 1234 = 1\t3205,3201,0\nFar bank\tbooth\tmembers\t3201,3220,0\n";
		PluginRouter r = router(lines);
		// a 15-tick bank stop (the default's 30 makes walking quicker here)
		PluginRouter.BankSetup quick = new PluginRouter.BankSetup(15, null);
		PluginRouter.Answer a = r.route(member().bank(LAW, 10).build(), 3201, 3201, 0, 3251, 3250, 0, id -> false,
			PluginRouter.TransportFilter.NONE, SailingSetup.NONE, quick);
		assertEquals("Far bank", a.getRoute().getSteps().get(1).getBankName(), "the locked bank is skipped");
		assertEquals(19 + 15 + 5 + 1, a.getRoute().getTicks());
		PluginRouter.Answer open = r.route(member().bank(LAW, 10).varbit(1234, 1).build(), 3201, 3201, 0, 3251, 3250, 0, id -> false,
			PluginRouter.TransportFilter.NONE, SailingSetup.NONE, quick);
		assertEquals("Locked bank", open.getRoute().getSteps().get(1).getBankName());
		// the plugin watches the banks' varbits too
		assertTrue(r.requirements().stream().anyMatch(q -> q.toText().equals("varbit 1234 = 1")));
	}

	@Test
	void bankLinesReadBackTheSame()
	{
		BankList l = BankList.parse(BankList.HEADER + "\tname\tx\n" + "A bank\tbooth\tmembers\t3205,3201,0 3205,3202,0\tBank booth\t3206\n"
			+ "B bank\tbanker\t\t\textra\n");
		assertEquals(2, l.getBanks().size());
		assertEquals(2, l.getBanks().get(0).getUse().length);
		assertEquals(0, l.getBanks().get(1).getUse().length);
		BankList back = BankList.parse(BankListWriter.write(l, "comment"));
		assertEquals(BankListWriter.write(l, null), BankListWriter.write(back, null));
		assertTrue(l.usable(n -> true, null).bankAt(Tiles.pack(3205, 3202, 0)).equals("A bank"));
		assertTrue(l.usable(n -> !n.equals("members"), null).isEmpty());
	}
}
