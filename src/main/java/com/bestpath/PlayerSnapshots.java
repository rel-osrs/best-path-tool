package com.bestpath;

import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.Skill;
import net.runelite.api.EquipmentInventorySlot;

/**
 * Small mappings between RuneLite's and core's names for skills and
 * equipment slots, used when {@link PlayerTracker} reads the player.
 */
final class PlayerSnapshots
{
	private PlayerSnapshots()
	{
	}

	/** RuneLite's skill for ours (same names), or null if this RuneLite version lacks it. */
	static net.runelite.api.Skill runeliteSkill(Skill s)
	{
		for (net.runelite.api.Skill rs : net.runelite.api.Skill.values())
		{
			if (rs.name().equals(s.name()))
			{
				return rs;
			}
		}
		return null;
	}

	/** Our equipment slot for RuneLite's (null for slots no requirement uses: arms, hair, jaw). */
	static EquipSlot equipSlot(EquipmentInventorySlot slot)
	{
		switch (slot)
		{
			case HEAD:
				return EquipSlot.HEAD;
			case CAPE:
				return EquipSlot.CAPE;
			case AMULET:
				return EquipSlot.NECK;
			case WEAPON:
				return EquipSlot.WEAPON;
			case BODY:
				return EquipSlot.BODY;
			case SHIELD:
				return EquipSlot.SHIELD;
			case LEGS:
				return EquipSlot.LEGS;
			case GLOVES:
				return EquipSlot.HANDS;
			case BOOTS:
				return EquipSlot.FEET;
			case RING:
				return EquipSlot.RING;
			case AMMO:
				return EquipSlot.AMMO;
			default:
				return null;
		}
	}
}
