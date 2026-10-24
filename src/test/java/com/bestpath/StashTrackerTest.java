package com.bestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Method;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

/** The put-back card's id names its unit ("Skip" forgets that unit's reminder), and the config switch. */
public class StashTrackerTest
{
	@Test
	public void skipNamesTheUnitOfTheCard()
	{
		assertEquals(5229, StashTracker.unitOfStop(StashTracker.STOP_PREFIX + 5229));
		assertEquals(-1, StashTracker.unitOfStop("t-123"));
		assertEquals(-1, StashTracker.unitOfStop(StashTracker.STOP_PREFIX + "x"));
		assertEquals(-1, StashTracker.unitOfStop(null));
	}

	/** A RuneLite config panel switch, on by default, its key as the plugin listens for it. */
	@Test
	public void reminderSwitchIsOnByDefault() throws Exception
	{
		BestPathConfig defaults = new BestPathConfig()
		{
		};
		assertTrue(defaults.remindStashPutBack());
		Method m = BestPathConfig.class.getMethod("remindStashPutBack");
		assertEquals(BestPathConfig.REMIND_STASH_KEY, m.getAnnotation(ConfigItem.class).keyName());
	}
}
