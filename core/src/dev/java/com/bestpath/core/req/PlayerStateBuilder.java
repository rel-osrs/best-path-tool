package com.bestpath.core.req;

/**
 * Makes the standard players the Data Tool and the tests route for.
 */
public final class PlayerStateBuilder
{
	private PlayerStateBuilder()
	{
	}

	/** Every level 99, members, every quest and unlock done, every item carried. */
	public static PlayerState maxed()
	{
		PlayerState.Builder b = allItems(new PlayerState.Builder("Maxed").members(true).allUnlocked(true), true);
		for (Skill s : Skill.values())
		{
			b.level(s, 99);
		}
		return b.build();
	}

	/** Sets whether every item counts as carried (a test player's setting; the plugin's player never has it). */
	public static PlayerState.Builder allItems(PlayerState.Builder b, boolean on)
	{
		b.allItems = on;
		return b;
	}

	/** A new members account: every level 1 (Hitpoints 10), nothing done, nothing carried. */
	public static PlayerState fresh()
	{
		return new PlayerState.Builder("Fresh account").members(true).build();
	}
}
