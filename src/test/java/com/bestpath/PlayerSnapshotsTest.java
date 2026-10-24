package com.bestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import com.bestpath.core.req.EquipSlot;
import com.bestpath.core.req.Skill;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;
import org.junit.Test;

public class PlayerSnapshotsTest
{
	@Test
	public void everyOneOfOurSkillsExistsInRuneLite()
	{
		for (Skill s : Skill.values())
		{
			assertNotNull(s.name(), PlayerSnapshots.runeliteSkill(s));
		}
	}

	@Test
	public void everyOneOfOurEquipSlotsIsFilledFromExactlyOneRuneLiteSlot()
	{
		Set<EquipSlot> seen = EnumSet.noneOf(EquipSlot.class);
		for (EquipmentInventorySlot rl : EquipmentInventorySlot.values())
		{
			EquipSlot ours = PlayerSnapshots.equipSlot(rl);
			if (ours != null)
			{
				assertEquals("twice: " + ours, true, seen.add(ours));
			}
		}
		assertEquals(EnumSet.allOf(EquipSlot.class), seen);
		assertEquals(EquipSlot.NECK, PlayerSnapshots.equipSlot(EquipmentInventorySlot.AMULET));
		assertEquals(EquipSlot.HANDS, PlayerSnapshots.equipSlot(EquipmentInventorySlot.GLOVES));
		assertEquals(EquipSlot.FEET, PlayerSnapshots.equipSlot(EquipmentInventorySlot.BOOTS));
		assertNull(PlayerSnapshots.equipSlot(EquipmentInventorySlot.ARMS));
	}
}
