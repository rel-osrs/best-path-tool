package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.req.Check;
import com.bestpath.core.req.PlayerState;
import com.bestpath.core.req.PlayerStateBuilder;
import com.bestpath.core.req.RequirementText;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * A player-adjustable coin cost cap in the "avoid" section: a tick box to enable it and an amount; travel that costs
 * over it is not used.
 */
public class CoinCapTest
{
	private static final int COINS = PlayerState.COINS;
	private static final int TICKET = 621;

	/** Open ground 3200-3263; a 500-coin teleport to 3250,3250 and one paid with coins or a ticket to 3240,3240. */
	private static CoreRouteEngine engine()
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		TransportLine fare = TransportLine.builder("t-fare", TransportLine.Kind.TELEPORT).name("Pricey teleport")
			.category("TELEPORT").end(3250, 3250, 0).ticks(5).requirements("coins 500")
			.teleportCategory("other item (infinite)").build();
		TransportLine choice = TransportLine.builder("t-choice", TransportLine.Kind.TELEPORT)
			.name("Coins or ticket").category("TELEPORT").end(3240, 3240, 0).ticks(5)
			.requirements("one of (coins 500 | item " + TICKET + ")").teleportCategory("other item (infinite)")
			.build();
		FreeLand free = new FreeLand.Builder().set(3200, 3200, 3263, 3263, true).build();
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), List.of(fare, choice),
			Tables.empty(), free);
		return new CoreRouteEngine(new PluginRouter(data, Long.MAX_VALUE));
	}

	private static RouteResult route(CoreRouteEngine e, PlayerState p, TransportSettings s, int x, int y)
	{
		return e.route(new RouteJob(1, new RouteRequest(x, y, 0), new PlayerSnapshot(3201, 3201, 0, p, List.of()), s, java.util.Set.of()));
	}

	private static boolean uses(RouteResult r, String id)
	{
		return r.getSteps().stream().anyMatch(s -> id.equals(s.getTransportId()));
	}

	@Test
	public void aFareOverTheLimitIsNeverUsed()
	{
		CoreRouteEngine e = engine();
		PlayerState rich = new PlayerState.Builder("p").members(true).carry(COINS, 10_000).build();
		assertTrue("no limit: the fare is quicker", uses(route(e, rich, TransportSettings.defaults(), 3251, 3250), "t-fare"));
		TransportSettings capped = TransportSettings.defaults().withCoinCapOn(true).withCoinCap(100);
		RouteResult walk = route(e, rich, capped, 3251, 3250);
		assertTrue(walk.getSteps().size() > 0);
		assertFalse("over the limit: walks", uses(walk, "t-fare"));
		assertTrue("the amount alone does nothing until ticked", uses(route(e, rich,
			TransportSettings.defaults().withCoinCap(100), 3251, 3250), "t-fare"));
		assertTrue("a fare equal to the limit is fine", uses(route(e, rich,
			TransportSettings.defaults().withCoinCapOn(true).withCoinCap(500), 3251, 3250), "t-fare"));
	}

	@Test
	public void aChoiceOfCoinsOrATicketStillWorksWithTheTicket()
	{
		CoreRouteEngine e = engine();
		TransportSettings capped = TransportSettings.defaults().withCoinCapOn(true).withCoinCap(100);
		PlayerState coinsOnly = new PlayerState.Builder("p").members(true).carry(COINS, 10_000).build();
		PlayerState ticket = new PlayerState.Builder("p").members(true).carry(COINS, 10_000).carry(TICKET, 1).build();
		assertFalse(uses(route(e, coinsOnly, capped, 3241, 3240), "t-choice"));
		assertTrue(uses(route(e, ticket, capped, 3241, 3240), "t-choice"));
	}

	@Test
	public void theFareCheckSaysWhy()
	{
		Map<String, int[]> none = new HashMap<>();
		PlayerState p = new PlayerState.Builder("p").carry(COINS, 10_000).build().withCoinCap(100);
		Check c = RequirementText.parse("coins 500", RequirementText.ItemGroups.of(none)).check(p);
		assertEquals(Check.Outcome.NOT_MET, c.getOutcome());
		assertEquals(List.of("coins 500 (over your limit of 100)"), c.getMissing());
		assertEquals(Check.Outcome.MET, RequirementText.parse("coins 100", RequirementText.ItemGroups.of(none)).check(p).getOutcome());
		assertEquals("even an every-item player keeps to the limit", Check.Outcome.NOT_MET,
			RequirementText.parse("coins 500", RequirementText.ItemGroups.of(none)).check(PlayerStateBuilder.maxed().withCoinCap(100)).getOutcome());
		assertTrue(PlayerStateBuilder.maxed().withCoinCap(-1).sameAs(PlayerStateBuilder.maxed()));
		assertFalse(PlayerStateBuilder.maxed().withCoinCap(100).sameAs(PlayerStateBuilder.maxed()));
	}

	@Test
	public void theLimitIsSavedPerAccountAndMirroredInTheConfigPanel()
	{
		TransportSettings s = TransportSettings.defaults().withCoinCapOn(true).withCoinCap(2500);
		assertEquals(s, TransportSettings.decode(s.encode()));
		assertFalse(TransportSettings.defaults().encode().contains("coin"));
		assertEquals(TransportSettings.DEFAULT_COIN_CAP, TransportSettings.decode("v1\n").getCoinCap());
		Map<String, String> v = com.bestpath.TransportConfig.values(s);
		assertEquals("true", v.get("coinCap"));
		assertEquals("2500", v.get("coinCapAmount"));
		Map<String, String> panel = new HashMap<>(v);
		panel.put("coinCap", "false");
		panel.put("coinCapAmount", "300");
		TransportSettings back = com.bestpath.TransportConfig.apply(s, panel::get);
		assertFalse(back.isCoinCapOn());
		assertEquals(300, back.getCoinCap());
	}
}
