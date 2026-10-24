package com.bestpath.core.req;

import java.util.Locale;

/** The player's spellbook, as written in requirement text ("spellbook ancient"). */
public enum Spellbook
{
	STANDARD,
	ANCIENT,
	LUNAR,
	ARCEUUS;

	public String getLabel()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	/** The spellbook with this name (any case), or null. */
	public static Spellbook byName(String name)
	{
		for (Spellbook s : values())
		{
			if (s.getLabel().equals(name.trim().toLowerCase(Locale.ROOT)))
			{
				return s;
			}
		}
		return null;
	}
}
