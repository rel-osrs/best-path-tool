package com.bestpath.tool.extract;

import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.data.WildernessZonesBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.script.Instructions;

/**
 * The Wilderness level of every tile, worked out by running the game's own client script (from the cache, not
 * guessed). Script {@value #LEVEL_SCRIPT} returns the level the game shows for the
 * player's tile ({@code coord}), 0 outside: nine boxes in order, each tested by script {@value #BOX_SCRIPT} (x, plane,
 * y between two packed coordinates), with a constant level or one counted from the tile's y. Only the zones are kept
 * (enough to tell where the level 20 and 30 lines are), and the safe areas ({@link SafeAreas}: Ferox Enclave) are
 * outside.
 *
 * <p>Runs only the few instructions those two scripts use (an unknown one stops the export, so a changed script is
 * noticed): int constants, locals and arguments, {@code coord} / {@code coordx} / {@code coordy} (the north
 * coordinate) / {@code coordz} (the plane), {@code invoke}, compare-and-jump, {@code jump}, add, sub, multiply, div,
 * mod, {@code scale} and {@code return}. Every tile inside the boxes (the packed constant pairs right before
 * {@code coord; invoke}) is run on all four planes; a level that differs by plane stops the export.
 * {@code scale(a, b, c)} is taken as {@code a * c / b} (used by one box only, a 64-tile band whose levels then run
 * 33 to 40; not checked against the game).
 */
public final class WildernessScript
{
	public static final int LEVEL_SCRIPT = 384;
	public static final int BOX_SCRIPT = 247;
	private static final int MAX_STEPS = 10_000;

	private final IntFunction<ScriptDefinition> scripts;
	/** The scripts read so far (each is read from the cache once: a million tiles run them). */
	private final java.util.Map<Integer, ScriptDefinition> loaded = new java.util.HashMap<>();
	private final java.util.Map<Integer, String> opNames = new java.util.HashMap<>();
	private final Instructions names = new Instructions();

	/** {@code scripts}: a client script by id (null when the cache has none). */
	public WildernessScript(IntFunction<ScriptDefinition> scripts)
	{
		this.scripts = scripts;
		names.init();
	}

	/** The level script's result for this tile (0 = not in the Wilderness). */
	public int level(int x, int y, int plane)
	{
		int coord = plane << 28 | x << 14 | y;
		return run(LEVEL_SCRIPT, new int[0], coord, 0);
	}

	/** Every box the level script tests: {x1, y1, x2, y2} (any plane). */
	public List<int[]> boxes()
	{
		ScriptDefinition s = script(LEVEL_SCRIPT);
		int[] ops = s.getInstructions();
		int[] arg = s.getIntOperands();
		List<int[]> out = new ArrayList<>();
		for (int i = 2; i + 1 < ops.length; i++)
		{
			if ("coord".equals(name(ops[i])) && "invoke".equals(name(ops[i + 1])) && arg[i + 1] == BOX_SCRIPT
				&& "iconst".equals(name(ops[i - 2])) && "iconst".equals(name(ops[i - 1])))
			{
				int a = arg[i - 2];
				int b = arg[i - 1];
				out.add(new int[]{a >>> 14 & 0x3fff, a & 0x3fff, b >>> 14 & 0x3fff, b & 0x3fff});
			}
		}
		if (out.isEmpty())
		{
			throw new IllegalStateException("script " + LEVEL_SCRIPT + " tests no boxes: it changed, look at it again");
		}
		return out;
	}

	/** A tile that is not the Wilderness although the level script says so (a safe area). */
	public interface Safe
	{
		boolean test(int x, int y);
	}

	/** Runs the level script for every tile of its boxes and returns the zones as rectangles (safe tiles outside). */
	public WildernessZones zones(Safe safe)
	{
		List<int[]> boxes = boxes();
		int x0 = Integer.MAX_VALUE;
		int y0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int y1 = Integer.MIN_VALUE;
		for (int[] b : boxes)
		{
			x0 = Math.min(x0, b[0]);
			y0 = Math.min(y0, b[1]);
			x1 = Math.max(x1, b[2]);
			y1 = Math.max(y1, b[3]);
		}
		WildernessZonesBuilder grid = new WildernessZonesBuilder(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
		boolean[] done = new boolean[(x1 - x0 + 1) * (y1 - y0 + 1)];
		for (int[] b : boxes)
		{
			for (int y = b[1]; y <= b[3]; y++)
			{
				for (int x = b[0]; x <= b[2]; x++)
				{
					int k = (y - y0) * (x1 - x0 + 1) + (x - x0);
					if (done[k])
					{
						continue;
					}
					done[k] = true;
					int lv = level(x, y, 0);
					for (int p = 1; p < 4; p++)
					{
						if (level(x, y, p) != lv)
						{
							throw new IllegalStateException("Wilderness level differs by plane at " + x + "," + y);
						}
					}
					grid.set(x, y, safe.test(x, y) ? 0 : WildernessZones.zoneOfLevel(lv));
				}
			}
		}
		return grid.build();
	}

	private ScriptDefinition script(int id)
	{
		ScriptDefinition s = loaded.computeIfAbsent(id, scripts::apply);
		if (s == null)
		{
			throw new IllegalStateException("the cache has no client script " + id);
		}
		return s;
	}

	private String name(int opcode)
	{
		return opNames.computeIfAbsent(opcode, o ->
		{
			String n = names.findNameFromOpcode(o);
			return n == null ? "op" + o : n;
		});
	}

	private int run(int id, int[] args, int coord, int depth)
	{
		if (depth > 4)
		{
			throw new IllegalStateException("scripts call each other too deep");
		}
		ScriptDefinition s = script(id);
		int[] ops = s.getInstructions();
		int[] arg = s.getIntOperands();
		int[] locals = new int[Math.max(s.getLocalIntCount(), args.length) + 1];
		System.arraycopy(args, 0, locals, 0, args.length);
		int[] stack = new int[64];
		int sp = 0;
		int pc = 0;
		for (int steps = 0; steps < MAX_STEPS; steps++)
		{
			if (pc < 0 || pc >= ops.length)
			{
				throw new IllegalStateException("script " + id + " ran off its end");
			}
			String op = name(ops[pc]);
			switch (op)
			{
				case "iconst":
					stack[sp++] = arg[pc];
					break;
				case "istore":
					locals[arg[pc]] = stack[--sp];
					break;
				case "iload":
					stack[sp++] = locals[arg[pc]];
					break;
				case "coord":
					stack[sp++] = coord;
					break;
				case "jump":
					pc += arg[pc] + 1;
					continue;
				case "if_icmpeq":
				case "if_icmpne":
				case "if_icmplt":
				case "if_icmpgt":
				case "if_icmple":
				case "if_icmpge":
				{
					int b = stack[--sp];
					int a = stack[--sp];
					pc += jumps(op, a, b) ? arg[pc] + 1 : 1;
					continue;
				}
				case "invoke":
				{
					ScriptDefinition callee = script(arg[pc]);
					int n = callee.getIntArgCount();
					int[] in = new int[n];
					sp -= n;
					System.arraycopy(stack, sp, in, 0, n);
					stack[sp++] = run(arg[pc], in, coord, depth + 1);
					break;
				}
				case "return":
					return sp > 0 ? stack[sp - 1] : 0;
				default:
					sp = calculate(id, op, stack, sp);
					break;
			}
			pc++;
		}
		throw new IllegalStateException("script " + id + " did not finish");
	}

	/** Whether a compare-and-jump instruction jumps for these two values. */
	private static boolean jumps(String op, int a, int b)
	{
		return op.equals("if_icmpeq") ? a == b : op.equals("if_icmpne") ? a != b
			: op.equals("if_icmplt") ? a < b : op.equals("if_icmpgt") ? a > b : op.equals("if_icmple") ? a <= b : a >= b;
	}

	/** Runs one coordinate or arithmetic instruction on the stack and returns the new stack size. */
	private static int calculate(int id, String op, int[] stack, int sp)
	{
		int b;
		switch (op)
		{
			case "coordx":
				stack[sp - 1] = stack[sp - 1] >>> 14 & 0x3fff;
				return sp;
			case "coordy":
				stack[sp - 1] = stack[sp - 1] & 0x3fff;
				return sp;
			case "coordz":
				stack[sp - 1] = stack[sp - 1] >>> 28 & 0x3;
				return sp;
			case "add":
				b = stack[--sp];
				stack[sp - 1] += b;
				return sp;
			case "sub":
				b = stack[--sp];
				stack[sp - 1] -= b;
				return sp;
			case "multiply":
				b = stack[--sp];
				stack[sp - 1] *= b;
				return sp;
			case "div":
				b = stack[--sp];
				stack[sp - 1] /= b;
				return sp;
			case "mod":
				b = stack[--sp];
				stack[sp - 1] %= b;
				return sp;
			case "scale":
			{
				int c = stack[--sp];
				b = stack[--sp];
				int a = stack[--sp];
				stack[sp++] = (int) ((long) a * c / b);
				return sp;
			}
			default:
				throw new IllegalStateException("script " + id + ": instruction " + op + " is not known here; the script changed,"
					+ " look at it again");
		}
	}
}
