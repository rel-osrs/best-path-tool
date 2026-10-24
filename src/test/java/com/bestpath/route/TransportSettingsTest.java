package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class TransportSettingsTest
{
	@Test
	public void defaultsAllowEverything()
	{
		TransportSettings d = TransportSettings.defaults();
		for (TransportSettings.Category c : TransportSettings.Category.values())
		{
			assertTrue(d.isEnabled(c));
		}
		for (TransportSettings.Avoid a : TransportSettings.Avoid.values())
		{
			assertFalse(d.avoids(a));
		}
		assertTrue(d.getBlacklist().isEmpty());
	}

	@Test
	public void changesMakeNewCopies()
	{
		TransportSettings d = TransportSettings.defaults();
		TransportSettings s = d.withCategory(TransportSettings.Category.DOORS, false)
			.withAvoid(TransportSettings.Avoid.RUNES, true)
			.withBlacklisted("t-1", "Varrock teleport");
		assertTrue(d.isEnabled(TransportSettings.Category.DOORS));
		assertFalse(s.isEnabled(TransportSettings.Category.DOORS));
		assertTrue(s.avoids(TransportSettings.Avoid.RUNES));
		assertTrue(s.isBlacklisted("t-1"));
		assertFalse(s.withoutBlacklisted("t-1").isBlacklisted("t-1"));
		assertTrue(s.withCategory(TransportSettings.Category.DOORS, true).isEnabled(TransportSettings.Category.DOORS));
	}

	@Test
	public void encodeDecodeRoundTripKeepsBlacklistOrderAndOddNames()
	{
		TransportSettings s = TransportSettings.defaults()
			.withCategory(TransportSettings.Category.BOATS, false)
			.withAvoid(TransportSettings.Avoid.KNOWN_BUGS, true)
			.withBlacklisted("t-2", "Ladder = up, 50% \n odd & <name>")
			.withBlacklisted("t 1", "Gate");
		TransportSettings back = TransportSettings.decode(s.encode());
		assertEquals(s, back);
		assertEquals(List.of("t-2", "t 1"), List.copyOf(back.getBlacklist().keySet()));
		assertEquals("Ladder = up, 50% \n odd & <name>", back.getBlacklist().get("t-2"));
	}

	@Test
	public void decodeSkipsUnknownAndBrokenLines()
	{
		assertSame(TransportSettings.defaults(), TransportSettings.decode(null));
		assertSame(TransportSettings.defaults(), TransportSettings.decode(""));
		TransportSettings s = TransportSettings.decode("v9\noff=NOT_A_CATEGORY\nnonsense\navoid=COST\nnever=%ZZ x\n"
			+ "never=t-5\nfuture=1\n");
		assertTrue(s.avoids(TransportSettings.Avoid.COST));
		assertEquals(1, s.getBlacklist().size());
		assertEquals("t-5", s.getBlacklist().get("t-5"));
	}
}
