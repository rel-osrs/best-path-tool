package com.bestpath.tool.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestpath.core.path.Transport;
import com.bestpath.tool.edit.TransportRecord;
import com.bestpath.tool.route.RouteTransports;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A stop you add in the Networks tab ("Add a stop..."): the record it makes, and that the route search
 * gets a ride to and from every other stop of the network, used from beside the stop's tile (fairy rings).
 */
class NetworkStopTest
{
	@Test
	void newStopIsANetworkStopOnOneTile()
	{
		TransportRecord r = NetworksPanel.newStop("Fairy ring", " Fairy ring: XYZ (test place) ", new int[]{3200, 3300, 0}, 5,
			"quest Lost City", "seen in game");
		assertEquals(TransportRecord.Kind.NETWORK, r.kind);
		assertEquals("Fairy ring", r.network);
		assertEquals("Fairy ring: XYZ (test place)", r.name);
		assertEquals("TRAVEL", r.category);
		assertEquals(3200, r.startX);
		assertEquals(3300, r.startY);
		assertEquals(0, r.startPlane);
		assertEquals(3200, r.endX);
		assertEquals(3300, r.endY);
		assertEquals(5, r.ticks);
		assertFalse(r.ticksEstimate);
		assertEquals("quest Lost City", r.requirements);
		assertTrue(r.source.startsWith("manual: network stop added in the Networks tab"));
		assertTrue(r.source.endsWith("seen in game"));
		assertTrue(r.id.startsWith("t-"));
	}

	@Test
	void stopsOfANetworkBecomeRidesUsedFromBeside()
	{
		TransportRecord a = NetworksPanel.newStop("Fairy ring", "Fairy ring: AAA", new int[]{3200, 3300, 0}, 5, "", "");
		TransportRecord b = NetworksPanel.newStop("Fairy ring", "Fairy ring: BBB", new int[]{3400, 3500, 0}, 5, "", "");
		List<Transport> t = RouteTransports.build(List.of(a, b), Collections.emptyList(), null, false, c -> false, id -> false,
			(c, x, y) -> null);
		assertEquals(2, t.size(), "a ride each way");
		for (Transport tr : t)
		{
			assertTrue(tr.isUsedFromBeside(), "boarded from beside the ring's middle");
			assertFalse(tr.isTwoWay());
		}
		assertEquals(3400, t.get(0).getEndX());
		assertEquals(3200, t.get(1).getEndX());
	}
}
