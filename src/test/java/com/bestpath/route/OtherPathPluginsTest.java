package com.bestpath.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * Plugins that talk to Shortest Path are checked for disruptive behaviour: Best Path tells the player to turn off the
 * disruptive ones.
 */
public class OtherPathPluginsTest
{
	private static List<String> warnings(Set<String> running, Map<String, String> config)
	{
		return OtherPathPlugins.warnings(running::contains, (group, key) -> config.get(group + "." + key),
			key -> "true".equals(config.get("bestpath." + key)));
	}

	@Test
	public void shortestClueIsAlwaysInTheWay()
	{
		List<String> w = warnings(Set.of("Shortest Clue", "Quest Helper"), Map.of());
		assertEquals(1, w.size());
		assertTrue(w.get(0).startsWith("Turn off Shortest Clue"));
	}

	@Test
	public void theSlayerOverlayOnlyWithItsShortestPathSettingOn()
	{
		Set<String> running = Set.of("Configurable Slayer Task Overlay");
		assertEquals("off by default", List.of(), warnings(running, Map.of()));
		assertEquals(List.of(), warnings(running, Map.of("Configurable Slayer Task Overlay.useShortestPath", "false")));
		List<String> w = warnings(running, Map.of("Configurable Slayer Task Overlay.useShortestPath", "true"));
		assertEquals(1, w.size());
		assertTrue(w.get(0).startsWith("In Configurable Slayer Task Overlay, turn off"));
		assertEquals("the setting on but the plugin off: nothing", List.of(),
			warnings(Set.of(), Map.of("Configurable Slayer Task Overlay.useShortestPath", "true")));
	}

	/**
	 * Settings let the user use Shortest Clue and the Slayer task plugin if they prefer: with Best Path's "Use ... for ..." setting on, that plugin is not warned about.
	 */
	@Test
	public void aPreferredPluginIsNotWarnedAbout()
	{
		Set<String> running = Set.of("Shortest Clue", "Configurable Slayer Task Overlay");
		Map<String, String> slayerOn = Map.of("Configurable Slayer Task Overlay.useShortestPath", "true");
		assertEquals(2, warnings(running, slayerOn).size());
		assertEquals(List.of(), warnings(running, Map.of("Configurable Slayer Task Overlay.useShortestPath", "true",
			"bestpath.preferShortestClue", "true", "bestpath.preferSlayerOverlay", "true")));
		List<String> w = warnings(running, Map.of("Configurable Slayer Task Overlay.useShortestPath", "true",
			"bestpath.preferShortestClue", "true"));
		assertEquals(1, w.size());
		assertTrue(w.get(0).startsWith("In Configurable Slayer Task Overlay"));
	}

	@Test
	public void theHarmlessOnesNeverWarn()
	{
		// Quest Helper is followed on purpose; Tasks Tracker and Watchdog send only what the player picked or made;
		// Hunter Rumours is not warned about: its functions are not disruptive during normal gameplay
		assertEquals(List.of(), warnings(Set.of("Quest Helper", "Tasks Tracker", "Watchdog", "Shortest Path", "Hunter Rumours"),
			Map.of("questhelper.useShortestPath", "true", "tasks-tracker.useShortestPath", "true",
				"hunterrumours.useShortestPath", "true")));
	}

	@Test
	public void watchedSettingGroups()
	{
		assertTrue(OtherPathPlugins.watches("Configurable Slayer Task Overlay"));
		assertFalse(OtherPathPlugins.watches("questhelper"));
		assertFalse(OtherPathPlugins.watches(null));
	}
}
