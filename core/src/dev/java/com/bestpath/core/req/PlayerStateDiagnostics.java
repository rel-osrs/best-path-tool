package com.bestpath.core.req;

import java.util.Map;

/**
 * What development code reads from a {@link PlayerState} that the plugin itself never needs.
 */
public final class PlayerStateDiagnostics
{
	private PlayerStateDiagnostics()
	{
	}

	public static String name(PlayerState p)
	{
		return p.name;
	}

	/** The quests set on this player (lower-case names); others are not started. */
	public static Map<String, QuestState> quests(PlayerState p)
	{
		return p.quests;
	}

	public static Map<Integer, Integer> varbits(PlayerState p)
	{
		return p.varbits;
	}

	public static Map<Integer, Integer> varps(PlayerState p)
	{
		return p.varps;
	}

	/** The runes in the rune pouch (as the game reports them), item id to count. */
	public static Map<Integer, Integer> pouch(PlayerState p)
	{
		return p.pouch;
	}

	public static Map<Integer, Integer> bank(PlayerState p)
	{
		return p.bank;
	}

	/** True if the player said yes to this (with everything unlocked: always). */
	public static boolean hasAnswer(PlayerState p, String answer)
	{
		return p.allUnlocked || Boolean.TRUE.equals(p.answers.get(PlayerState.key(answer)));
	}
}
