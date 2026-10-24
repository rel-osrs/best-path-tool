package com.bestpath.dev;

import com.bestpath.BestPathPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * The workspace's {@code gradlew run} (dev.gradle): the game with Best Path and the dev-only canoe axe probe
 * (temporary; remove with {@link CanoeAxeProbePlugin} and this class, and
 * dev.gradle's run line). The public plugin repo runs {@code BestPathPluginTest}: Best Path alone.
 */
public final class DevRun
{
	private DevRun()
	{
	}

	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BestPathPlugin.class, CanoeAxeProbePlugin.class);
		RuneLite.main(args);
	}
}
