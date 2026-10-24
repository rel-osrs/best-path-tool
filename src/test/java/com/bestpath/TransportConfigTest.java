package com.bestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bestpath.route.TransportSettings;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

/** The config panel and the sidebar's Settings tab mirror each other. */
public class TransportConfigTest
{
	@Test
	public void theSidebarsChoicesAreWrittenAsTheConfigPanelStoresThem()
	{
		TransportSettings s = TransportSettings.defaults().withCategory(TransportSettings.Category.SHORTCUTS, false)
			.withAvoid(TransportSettings.Avoid.WILDERNESS, true).withBankTicks(45).withKeepBoat(true);
		Map<String, String> v = TransportConfig.values(s);
		assertEquals("false", v.get("typeShortcuts"));
		assertEquals("true", v.get("typeBoats"));
		assertEquals("true", v.get("avoidWilderness"));
		assertEquals("false", v.get("avoidCost"));
		assertEquals("45", v.get("bankTicks"));
		assertEquals("true", v.get("keepBoat"));
		assertEquals("true", v.get("shipwrights"));
		assertEquals(String.valueOf(TransportSettings.DEFAULT_BOARD_TICKS), v.get("boardTicks"));
	}

	@Test
	public void aChangeInTheConfigPanelComesBack()
	{
		TransportSettings s = TransportSettings.defaults().withBlacklisted("t1", "Some boat");
		Map<String, String> config = new HashMap<>(TransportConfig.values(s));
		assertSame("the same values: nothing changes", s, TransportConfig.apply(s, config::get));
		config.put("typeHouse", "false");
		config.put("avoidRunes", "true");
		config.put("boardTicks", "4");
		config.put("shipwrights", "false");
		TransportSettings out = TransportConfig.apply(s, config::get);
		assertFalse(out.isEnabled(TransportSettings.Category.HOUSE));
		assertTrue(out.avoids(TransportSettings.Avoid.RUNES));
		assertEquals(4, out.getBoardTicks());
		assertFalse(out.isShipwrights());
		assertTrue("the lists stay", out.isBlacklisted("t1"));
		assertEquals(config, TransportConfig.values(out));
	}

	@Test
	public void theConfigPanelsResetGivesTheDefaults()
	{
		TransportSettings s = TransportSettings.defaults().withCategory(TransportSettings.Category.DOORS, false).withBankTicks(99);
		TransportSettings out = TransportConfig.apply(s, key -> null);
		assertTrue(out.isEnabled(TransportSettings.Category.DOORS));
		assertEquals(TransportSettings.DEFAULT_BANK_TICKS, out.getBankTicks());
		// a value that can't be read keeps the choice
		assertSame(s, TransportConfig.apply(s, key -> key.equals("bankTicks") ? "lots" : TransportConfig.values(s).get(key)));
	}

	/** Every mirrored key is a config panel item in a transport section, and every item there is mirrored. */
	@Test
	public void theKeysAreTheConfigPanelsTransportSections()
	{
		Set<String> sections = Set.of(BestPathConfig.TYPES, BestPathConfig.AVOID, BestPathConfig.BANK, BestPathConfig.SAILING);
		Set<String> inSections = new HashSet<>();
		for (Method m : BestPathConfig.class.getMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item != null && sections.contains(item.section()))
			{
				inSections.add(item.keyName());
			}
		}
		assertEquals(inSections, TransportConfig.KEYS);
	}
}
