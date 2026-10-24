package com.bestpath.tool.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bestpath.core.data.WildernessZones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.script.Instructions;
import org.junit.jupiter.api.Test;

/**
 * Running the game's Wilderness level script: hand-made scripts in the same shape as the cache's 384 (boxes in
 * order, a level counted from y or a constant) and 247 (is a coordinate inside a box).
 */
class WildernessScriptTest
{
	private static final Instructions OPS = new Instructions();

	static
	{
		OPS.init();
	}

	/** A script from "name operand" lines; jumps take label numbers (the instruction index) and are made relative. */
	private static ScriptDefinition script(int id, int intArgs, int locals, String... lines)
	{
		int[] ops = new int[lines.length];
		int[] args = new int[lines.length];
		for (int i = 0; i < lines.length; i++)
		{
			String[] p = lines[i].trim().split(" +");
			Integer op = OPS.findOpcodeFromName(p[0]);
			if (op == null)
			{
				throw new IllegalArgumentException(p[0]);
			}
			ops[i] = op;
			int v = p.length > 1 ? Integer.parseInt(p[1]) : 0;
			args[i] = p[0].equals("jump") || p[0].startsWith("if_") ? v - i - 1 : v;
		}
		ScriptDefinition s = new ScriptDefinition();
		s.setId(id);
		s.setInstructions(ops);
		s.setIntOperands(args);
		s.setIntArgCount(intArgs);
		s.setLocalIntCount(locals);
		s.setStringOperands(new String[lines.length]);
		return s;
	}

	private static int coord(int plane, int x, int y)
	{
		return plane << 28 | x << 14 | y;
	}

	/** Script 247 as in the cache: x, then plane (coordz), then y (coordy) between two packed coordinates. */
	private static ScriptDefinition box()
	{
		return script(WildernessScript.BOX_SCRIPT, 3, 4,
			"iload 2", "coordx", "istore 3", "iload 3", "iload 0", "coordx", "if_icmplt 12", "iload 3", "iload 1", "coordx",
			"if_icmpgt 12", "jump 14", "iconst 0", "return",
			"iload 2", "coordz", "istore 3", "iload 3", "iload 0", "coordz", "if_icmplt 26", "iload 3", "iload 1", "coordz",
			"if_icmpgt 26", "jump 28", "iconst 0", "return",
			"iload 2", "coordy", "istore 3", "iload 3", "iload 0", "coordy", "if_icmplt 40", "iload 3", "iload 1", "coordy",
			"if_icmpgt 40", "jump 42", "iconst 0", "return",
			"iconst 1", "return");
	}

	/** Two boxes: x 100-119, y 200-239 at (y - 200) / 8 + 1, then x 120-123, y 200-203 at a constant 35 (planes 0-3). */
	private static ScriptDefinition levels()
	{
		return script(WildernessScript.LEVEL_SCRIPT, 0, 1,
			"iconst 0", "istore 0",
			"iconst " + coord(0, 100, 200), "iconst " + coord(3, 119, 239), "coord", "invoke 247", "iconst 1", "if_icmpeq 9",
			"jump 19",
			"coord", "coordy", "iconst 200", "sub", "iconst 8", "div", "iconst 1", "add", "istore 0", "jump 28",
			"iconst " + coord(0, 120, 200), "iconst " + coord(3, 123, 203), "coord", "invoke 247", "iconst 1", "if_icmpeq 26",
			"jump 28",
			"iconst 35", "istore 0",
			"iload 0", "return");
	}

	private static WildernessScript runner()
	{
		Map<Integer, ScriptDefinition> m = new HashMap<>();
		m.put(WildernessScript.BOX_SCRIPT, box());
		m.put(WildernessScript.LEVEL_SCRIPT, levels());
		return new WildernessScript(m::get);
	}

	@Test
	void theLevelScriptRunsForATile()
	{
		WildernessScript w = runner();
		assertEquals(1, w.level(100, 200, 0));
		assertEquals(1, w.level(119, 207, 2));
		assertEquals(2, w.level(110, 208, 0));
		assertEquals(5, w.level(110, 239, 3));
		assertEquals(0, w.level(110, 240, 0));
		assertEquals(0, w.level(99, 220, 0));
		assertEquals(35, w.level(121, 202, 1));
		assertEquals(0, w.level(121, 204, 1));
	}

	@Test
	void everyTileOfTheBoxesBecomesZonesSafeAreasOutside()
	{
		WildernessScript w = runner();
		List<int[]> boxes = w.boxes();
		assertEquals(2, boxes.size());
		assertEquals(List.of(100, 200, 119, 239), List.of(boxes.get(0)[0], boxes.get(0)[1], boxes.get(0)[2], boxes.get(0)[3]));
		// a safe area at x 110-111, y 220-221 (the walled ground around a tile, as Ferox Enclave)
		WildernessZones l = w.zones((x, y) -> x >= 110 && x <= 111 && y >= 220 && y <= 221);
		List<String> bad = new ArrayList<>();
		for (int y = 195; y < 245; y++)
		{
			for (int x = 95; x < 130; x++)
			{
				boolean safe = x >= 110 && x <= 111 && y >= 220 && y <= 221;
				if (l.zone(x, y) != (safe ? 0 : WildernessZones.zoneOfLevel(w.level(x, y, 0))))
				{
					bad.add(x + "," + y);
				}
			}
		}
		assertEquals(List.of(), bad);
	}

	@Test
	void anInstructionItDoesNotKnowStopsIt()
	{
		Map<Integer, ScriptDefinition> m = new HashMap<>();
		m.put(WildernessScript.BOX_SCRIPT, box());
		m.put(WildernessScript.LEVEL_SCRIPT, script(WildernessScript.LEVEL_SCRIPT, 0, 1, "sconst 0", "return"));
		assertThrows(IllegalStateException.class, () -> new WildernessScript(m::get).level(1, 1, 0));
		assertThrows(IllegalStateException.class, () -> new WildernessScript(id -> null).level(1, 1, 0));
	}
}
