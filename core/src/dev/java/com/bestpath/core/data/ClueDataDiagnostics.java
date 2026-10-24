package com.bestpath.core.data;

import java.util.Objects;

/**
 * What development code reads from {@link ClueData} that the plugin itself never needs.
 */
public final class ClueDataDiagnostics
{
	private ClueDataDiagnostics()
	{
	}

	/** The step with this key ({@code <type>-<cache row>}), or null; with the same key twice, the later one. */
	public static ClueData.Step byKey(ClueData data, String key)
	{
		ClueData.Step found = null;
		for (ClueData.Step s : data.getSteps())
		{
			if (Objects.equals(s.key, key))
			{
				found = s;
			}
		}
		return found;
	}

	/**
	 * What the player does there, as the game's clue helper says it ({@code dig}, {@code emote <id> <name> | ...},
	 * {@code talk}, ...); "" = not known.
	 */
	public static String action(ClueData.Step s)
	{
		return s.action;
	}

	/** Packed tiles: the target, then other targets (alternatives, kill areas). A copy. */
	public static int[] targets(ClueData.Step s)
	{
		return s.targets.clone();
	}
}
