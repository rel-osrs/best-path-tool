package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.bestpath.core.collision.CollisionMapBuilder;
import com.bestpath.core.collision.FreeLand;
import com.bestpath.core.data.PluginData;
import com.bestpath.core.data.PluginDataBuilder;
import com.bestpath.core.data.PluginRouter;
import com.bestpath.core.data.Tables;
import com.bestpath.core.data.TransportLine;
import com.bestpath.core.collision.CollisionFlags;
import com.bestpath.core.collision.CollisionMap;
import com.bestpath.core.collision.MovementRules;
import com.bestpath.core.data.WallBitsBuilder;
import com.bestpath.core.path.DoorMap;
import com.bestpath.core.req.PlayerState;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * On network cards the +/- changes the whole network, each member defaulting to 0. A network ride's step carries its network's key and how much the network's change moved its ticks.
 */
public class NetworkCardTicksTest
{
	static final String RING = "Test ring";
	private static PluginRouter router;

	private static TransportLine line(String id, TransportLine.Kind kind, String network, int[] start, int[] end, int ticks)
	{
		return TransportLine.builder(id, kind).name(id).category("TRAVEL").start(start[0], start[1], start[2])
			.end(end[0], end[1], end[2]).ticks(ticks).network(network).build();
	}

	/** One open region (3200-3263, 3200-3263); a three-stop network and a plain one-way ride. */
	@BeforeClass
	public static void map()
	{
		MovementRules.Flags open = (x, y) -> x >= 3200 && x < 3264 && y >= 3200 && y < 3264 ? 0 : CollisionFlags.BLOCKED;
		MovementRules.Flags none = (x, y) -> CollisionFlags.BLOCKED;
		CollisionMap m = CollisionMapBuilder.build(new MovementRules.Flags[]{open, none, none, none}, List.of(50 << 8 | 50), 0, 0);
		List<TransportLine> lines = List.of(
			line("r1", TransportLine.Kind.NETWORK, RING, new int[3], new int[]{3201, 3201, 0}, 5),
			line("r2", TransportLine.Kind.NETWORK, RING, new int[3], new int[]{3259, 3259, 0}, 5),
			line("r3", TransportLine.Kind.NETWORK, RING, new int[3], new int[]{3201, 3259, 0}, 5),
			line("t-cart", TransportLine.Kind.ONE_WAY, "", new int[]{3259, 3201, 0}, new int[]{3205, 3205, 0}, 4));
		Tables tables = Tables.parse(List.of(), List.of(), List.of("# network\textra ticks\trequirement\twhat", RING + "\t0\t\tstops"),
			List.of(), List.of());
		PluginData data = PluginDataBuilder.of(m, DoorMap.none(), WallBitsBuilder.fromFlags((x, y, p) -> 0, List.of()), lines, tables,
			FreeLand.NONE);
		router = new PluginRouter(data, PluginRouter.DEFAULT_CACHE_BUDGET);
	}

	private static RouteResult route(int sx, int sy, int dx, int dy, TransportSettings s)
	{
		CoreRouteEngine e = new CoreRouteEngine(router);
		PlayerSnapshot me = new PlayerSnapshot(sx, sy, 0, new PlayerState.Builder("Member").members(true).build(), List.of());
		return e.route(new RouteJob(3, new RouteRequest(dx, dy, 0), me, s, Set.of()));
	}

	private static RouteStep ride(RouteResult r)
	{
		for (RouteStep s : r.getSteps())
		{
			if (s.getTransportId() != null && s.getTransportId().startsWith(RING + ":"))
			{
				return s;
			}
		}
		return null;
	}

	@Test
	public void aNetworkRidesCardChangesTheWholeNetwork()
	{
		RouteStep data = ride(route(3201, 3201, 3259, 3259, TransportSettings.defaults()));
		assertNotNull(data);
		assertTrue(data.isGroupTicks());
		assertEquals(TransportSettings.networkKey(RING), data.getTickKey());
		assertEquals(5, data.getTicks());
		assertEquals(0, data.getTickChange());
		// the network's change (the card's - / +, the same as its Settings row) counts on every ride
		TransportSettings slower = TransportSettings.defaults().withGroupTickChange(TransportSettings.networkKey(RING), 3);
		RouteStep ab = ride(route(3201, 3201, 3259, 3259, slower));
		assertEquals(8, ab.getTicks());
		assertEquals("the card shows the network's change", 3, ab.getTickChange());
		RouteStep other = ride(route(3259, 3259, 3201, 3259, slower));
		assertEquals(RING + ":r2>r3", other.getTransportId());
		assertEquals(8, other.getTicks());
		assertEquals(3, other.getTickChange());
		// never below 0 ticks: the card's change is what the network's change really took off
		RouteStep free = ride(route(3201, 3201, 3259, 3259,
			TransportSettings.defaults().withGroupTickChange(TransportSettings.networkKey(RING), -9)));
		assertEquals(0, free.getTicks());
		assertEquals(-5, free.getTickChange());
	}

	@Test
	public void aRideWithoutANetworkKeepsItsOwnChange()
	{
		TransportSettings s = TransportSettings.defaults().withTickChange("t-cart", 2, "Cart");
		RouteResult r = route(3259, 3201, 3205, 3205, s);
		RouteStep cart = null;
		for (RouteStep st : r.getSteps())
		{
			cart = "t-cart".equals(st.getTransportId()) ? st : cart;
		}
		assertNotNull(cart);
		assertFalse(cart.isGroupTicks());
		assertEquals("t-cart", cart.getTickKey());
		assertEquals(4 + 2, cart.getTicks());
		assertEquals(2, cart.getTickChange());
	}
}
