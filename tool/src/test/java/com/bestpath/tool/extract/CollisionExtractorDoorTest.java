package com.bestpath.tool.extract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import org.junit.jupiter.api.Test;

/** Which wall-shaped objects count as doors (doors and gates are open by default). */
class CollisionExtractorDoorTest
{
	private final Map<Integer, ObjectDefinition> defs = new HashMap<>();

	private ObjectDefinition object(int id, String... options)
	{
		ObjectDefinition d = new ObjectDefinition();
		d.setId(id);
		EntityOpsDefinition ops = new EntityOpsDefinition();
		for (int i = 0; i < options.length; i++)
		{
			ops.setOp(i, options[i]);
		}
		d.setOps(ops);
		defs.put(id, d);
		return d;
	}

	private ObjectDefinition parent(int id, int... states)
	{
		ObjectDefinition d = object(id);
		d.setConfigChangeDest(states);
		return d;
	}

	@Test
	void aDoorWithAnOpenOptionIsADoor()
	{
		assertTrue(CollisionExtractor.isDoor(object(1, "Open", null, null, null, "Examine")));
		assertFalse(CollisionExtractor.isDoor(object(2, "Pick-lock")));
	}

	@Test
	void theAlKharidTollGateIsADoorThroughItsStates()
	{
		// cache 2735: 44598 has no menu; varp 273 0-99 -> Gate 44052 (Open, Pay-toll(10gp)), 100 and up -> Gate 44050 (Open)
		object(44052, "Open", "Pay-toll(10gp)");
		object(44050, "Open");
		int[] states = new int[101];
		java.util.Arrays.fill(states, 44052);
		states[100] = 44050;
		ObjectDefinition gate = parent(44598, states);
		assertFalse(CollisionExtractor.isDoor(gate));
		assertTrue(CollisionExtractor.isStateDoor(gate, defs::get));
	}

	@Test
	void aStateWithNothingThereStillCountsAsADoor()
	{
		object(53921, "Open");
		object(53922, "Open");
		assertTrue(CollisionExtractor.isStateDoor(parent(54108, 53921, 53922, -1), defs::get));
	}

	@Test
	void anObjectThatIsSometimesNotADoorStaysAWall()
	{
		// like the Barrows doors (one state a door, the other no menu) or a door that is only a doorway in some stages
		object(20679, "Open");
		object(20680);
		assertFalse(CollisionExtractor.isStateDoor(parent(20681, 20679, 20680, -1), defs::get));
		object(58097, "Open");
		object(58096, "Picklock");
		assertFalse(CollisionExtractor.isStateDoor(parent(58095, 58097, 58096, 58097), defs::get));
	}

	@Test
	void noStatesOrOnlyNothingIsNotADoor()
	{
		assertFalse(CollisionExtractor.isStateDoor(object(5, "Open"), defs::get));
		assertFalse(CollisionExtractor.isStateDoor(parent(6, -1, -1), defs::get));
		assertFalse(CollisionExtractor.isStateDoor(parent(7, 99999), defs::get)); // unknown object
	}
}
