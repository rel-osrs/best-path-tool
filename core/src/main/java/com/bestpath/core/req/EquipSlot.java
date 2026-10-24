package com.bestpath.core.req;

import java.util.Locale;

/** Equipment slots, as written in requirement text ("empty head"). */
public enum EquipSlot
{
	HEAD,
	CAPE,
	NECK,
	WEAPON,
	BODY,
	SHIELD,
	LEGS,
	HANDS,
	FEET,
	RING,
	AMMO;

	public String getLabel()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	/** The slot with this name (any case), or null. */
	public static EquipSlot byName(String name)
	{
		for (EquipSlot s : values())
		{
			if (s.getLabel().equals(name.trim().toLowerCase(Locale.ROOT)))
			{
				return s;
			}
		}
		return null;
	}
}
