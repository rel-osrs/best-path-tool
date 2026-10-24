package com.bestpath.route;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Other plugins whose messages to the Shortest Path plugin get in Best Path's way; Best Path tells the player to turn
 * them off. Best Path reads every {@value QuestHelperPath#NAMESPACE} "path" message as a Quest Helper step
 * ({@link QuestHelperPath}): the messages carry no sender, so a plugin that sends places the player did not ask for
 * replaces Best Path's own route.
 *
 * <p>Known senders:
 * <ul>
 * <li>Disruptive: Shortest Clue (the clue's place on every clue: Best Path routes clues itself, with the kill for a
 * key and the STASH and bank stops; no setting turns its messages off); Configurable Slayer Task Overlay with "Use
 * 'Shortest Path' plugin" on (each task's place: Best Path has its own Slayer routes; off unless the player turned it
 * on).</li>
 * <li>Not disruptive: Quest Helper (followed on purpose: "Routes from other plugins"); Hunter Rumours with "Use
 * 'Shortest Path' Plugin" on (the rumour's place; not in the way in normal play); Tasks Tracker (the next task's place
 * while the player follows one of its task routes); Watchdog (only from an alert the player made); Shortest Grave
 * (once after a death, to the grave). Shortest Path and GPS receive these messages and send none. The Functions tab's
 * "Integrated plugins" lists the ones that work with Best Path.</li>
 * </ul>
 */
public final class OtherPathPlugins
{
	/**
	 * A known plugin, the setting that makes it send (null: the plugin itself), Best Path's setting that says the
	 * player prefers that plugin (then no warning), and what to tell the player.
	 */
	static final class Known
	{
		final String plugin;
		final String group;
		final String key;
		final String preferKey;
		final String advice;

		Known(String plugin, String group, String key, String preferKey, String advice)
		{
			this.plugin = plugin;
			this.group = group;
			this.key = key;
			this.preferKey = preferKey;
			this.advice = advice;
		}
	}

	/** Best Path's settings "Use Shortest Clue for clues" and "Use Slayer Task Overlay for Slayer" (BestPathConfig). */
	public static final String PREFER_SHORTEST_CLUE_KEY = "preferShortestClue";
	public static final String PREFER_SLAYER_OVERLAY_KEY = "preferSlayerOverlay";

	static final List<Known> KNOWN = List.of(
		new Known("Shortest Clue", null, null, PREFER_SHORTEST_CLUE_KEY,
			"Turn off Shortest Clue (it replaces Best Path's clue routes), or tick \"Use Shortest Clue for clues\"."),
		new Known("Configurable Slayer Task Overlay", "Configurable Slayer Task Overlay", "useShortestPath",
			PREFER_SLAYER_OVERLAY_KEY, "In Configurable Slayer Task Overlay, turn off \"Use 'Shortest Path' plugin\" (it"
				+ " replaces Best Path's routes), or tick \"Use Slayer Task Overlay for Slayer\"."));

	private OtherPathPlugins()
	{
	}

	/** The config groups whose changes may change the warnings. */
	public static boolean watches(String group)
	{
		for (Known k : KNOWN)
		{
			if (k.group != null && k.group.equals(group))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * What to tell the player, one line per disruptive plugin that is on (empty: none).
	 *
	 * @param running   a plugin, by its name in the plugin list, is running
	 * @param config    a config value (group, key), null when not set (both settings are off by default)
	 * @param preferred one of Best Path's "Use ... for ..." settings ({@link #PREFER_SHORTEST_CLUE_KEY}...) is on
	 */
	public static List<String> warnings(Predicate<String> running, BiFunction<String, String, String> config,
		Predicate<String> preferred)
	{
		List<String> out = new ArrayList<>();
		for (Known k : KNOWN)
		{
			if (running.test(k.plugin) && !preferred.test(k.preferKey)
				&& (k.key == null || "true".equals(config.apply(k.group, k.key))))
			{
				out.add(k.advice);
			}
		}
		return out;
	}
}
