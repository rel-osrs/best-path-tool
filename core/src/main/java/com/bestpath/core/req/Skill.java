package com.bestpath.core.req;

import java.util.Locale;

/**
 * The player's skills, as written in requirement text ("Agility 61"). Members skills (Wiki: Free-to-play
 * rev 15359656 lists the free ones) can only be used on a members world: a level in one needs members too.
 */
public enum Skill
{
	ATTACK("Attack", false),
	DEFENCE("Defence", false),
	STRENGTH("Strength", false),
	HITPOINTS("Hitpoints", false),
	RANGED("Ranged", false),
	PRAYER("Prayer", false),
	MAGIC("Magic", false),
	COOKING("Cooking", false),
	WOODCUTTING("Woodcutting", false),
	FLETCHING("Fletching", true),
	FISHING("Fishing", false),
	FIREMAKING("Firemaking", false),
	CRAFTING("Crafting", false),
	SMITHING("Smithing", false),
	MINING("Mining", false),
	HERBLORE("Herblore", true),
	AGILITY("Agility", true),
	THIEVING("Thieving", true),
	SLAYER("Slayer", true),
	FARMING("Farming", true),
	RUNECRAFT("Runecraft", false),
	HUNTER("Hunter", true),
	CONSTRUCTION("Construction", true),
	SAILING("Sailing", true);

	private final String label;
	private final boolean members;

	Skill(String label, boolean members)
	{
		this.label = label;
		this.members = members;
	}

	/** Only usable on a members world. */
	public boolean isMembers()
	{
		return members;
	}

	public String getLabel()
	{
		return label;
	}

	/** The skill with this name (any case), or null. */
	public static Skill byName(String name)
	{
		String n = name.trim().toLowerCase(Locale.ROOT);
		for (Skill s : values())
		{
			if (s.label.toLowerCase(Locale.ROOT).equals(n))
			{
				return s;
			}
		}
		return null;
	}
}
