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
import com.bestpath.core.data.TickGroups;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.req.PlayerState;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** A GP category in the teleport and travel ticks section. */
public class CoinsTicksTest
{
	private static TransportLine line(String id, String requirement, int ticks)
	{
		return TransportLine.builder(id, TransportLine.Kind.ONE_WAY).name(id).category("TRAVEL")
			.start(3200, 3200, 0).end(3300, 3300, 0).ticks(ticks).requirements(requirement).build();
	}

	private static final List<TransportLine> LINES = List.of(line("ship", "coins 30", 10), line("carpet",
		"one of (coins 200 | varbit 4485 = 1)", 20), line("rocks", "Agility 10", 5));

	@Test
	public void theGpRowCoversEveryTransportThatCostsCoins()
	{
		TickGroups g = TickGroups.of(LINES, Tables.empty());
		TickGroups.Group gp = g.getGroups().get(g.getGroups().size() - 1);
		assertEquals(TickGroups.Kind.COINS, gp.getKind());
		assertEquals(2, gp.getCount());
		assertEquals(10, gp.lowest(0));
		assertEquals(20, gp.highest(0));
		assertTrue(TickGroups.costsCoins(LINES.get(1), Tables.empty()));
		assertFalse(TickGroups.costsCoins(LINES.get(2), Tables.empty()));
	}

	@Test
	public void itsChangeCountsForThemOnlyAndIsSaved()
	{
		TickGroups g = TickGroups.of(LINES, Tables.empty());
		TransportSettings s = TransportSettings.defaults().withGroupTickChange(TransportSettings.COINS_KEY, 5);
		assertEquals(15, g.ticks("ship", s.tickChanges()));
		assertEquals(25, g.ticks("carpet", s.tickChanges()));
		assertEquals(5, g.ticks("rocks", s.tickChanges()));
		// never below 0
		assertEquals(0, g.ticks("ship", TransportSettings.defaults().withGroupTickChange(TransportSettings.COINS_KEY, -50)
			.tickChanges()));
		// with the transport's own change on top
		assertEquals(17, g.ticks("ship", s.withTickChange("ship", 2, "Ship", 1L).tickChanges()));
		assertEquals(5, TransportSettings.decode(s.encode()).groupTickChange(TransportSettings.COINS_KEY));
	}

	/** One open region (3200-3263, 3200-3263) and a paid ship across it: 10 ticks for 30 coins. */
	private static RouteResult shipRoute(TransportSettings s)
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		List<TransportLine> lines = List.of(TransportLine.builder("ship", TransportLine.Kind.ONE_WAY).name("ship")
			.category("TRAVEL").start(3201, 3201, 0).end(3260, 3260, 0).ticks(10).requirements("coins 30").build());
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines,
			Tables.empty(), FreeLand.NONE);
		CoreRouteEngine e = new CoreRouteEngine(new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET));
		PlayerSnapshot me = new PlayerSnapshot(3200, 3200, 0, new PlayerState.Builder("Member").members(true)
			.carry(ItemID.COINS, 100).build(), List.of());
		return e.route(new RouteJob(1, new RouteRequest(3260, 3260, 0), me, s, Set.of()));
	}

	@Test
	public void routesUseTheGpRowsChange()
	{
		// one step to the ship and 10 ticks on it beat walking 60 tiles
		assertEquals(11, shipRoute(TransportSettings.defaults()).getTotalTicks());
		TransportSettings lowered = TransportSettings.defaults().withGroupTickChange(TransportSettings.COINS_KEY, -5);
		assertEquals(6, shipRoute(lowered).getTotalTicks());
		// raised past the walk: the route walks
		TransportSettings raised = TransportSettings.defaults().withGroupTickChange(TransportSettings.COINS_KEY, 60);
		RouteResult walk = shipRoute(raised);
		assertEquals(60, walk.getTotalTicks());
		assertTrue(walk.getSteps().stream().noneMatch(st -> "ship".equals(st.getTransportId())));
	}
}
