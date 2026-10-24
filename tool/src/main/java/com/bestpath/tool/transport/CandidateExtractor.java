package com.bestpath.tool.transport;

import com.bestpath.tool.cache.LoadedCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.cache.EntityOpsDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.region.Location;

/**
 * Finds transport-like objects in the cache by their menu options. Objects
 * that change form with a varbit/varp ("multilocs") count if any of their
 * forms has a transport option. Doors and gates are not candidates: they
 * count as open.
 */
public final class CandidateExtractor
{
	private static final Set<String> CLIMB_UP = Set.of("climb-up", "go-up", "walk-up");
	private static final Set<String> CLIMB_DOWN = Set.of("climb-down", "go-down", "walk-down");
	private static final Set<String> CLIMB_BOTH = Set.of("climb");
	private static final Set<String> SHORTCUT = Set.of("squeeze-through", "jump", "jump-over", "jump-across", "jump-to",
		"jump-on", "jump-from", "cross", "balance", "balance-across", "swing", "swing-on", "swing-across", "crawl-through",
		"vault", "grapple", "hop", "hop-across", "step-over", "leap", "climb-over", "climb-through", "climb-across",
		"climb-on", "walk-across", "run-across", "tightrope", "teeth-grip", "squeeze-past", "pass-through");
	private static final Set<String> PASS = Set.of("pass");
	private static final Set<String> ENTER = Set.of("enter", "go-through", "walk-through", "climb-into",
		"go-in", "jump-in", "jump-into", "slide-down", "crawl-into", "squeeze-into", "descend");
	private static final Set<String> EXIT = Set.of("exit", "leave", "climb-out", "go-out", "ascend");
	private static final Set<String> TRAVEL = Set.of("board", "travel", "ride", "fly", "sail", "quick-travel",
		"last-destination", "teleport", "zanaris", "charter", "dock");

	private CandidateExtractor()
	{
	}

	public static List<TransportCandidate> extract(LoadedCache cache) throws IOException
	{
		List<TransportCandidate> out = new ArrayList<>();
		cache.forEachRegion(r ->
		{
			for (Location l : r.getLocations())
			{
				ObjectDefinition def = cache.object(l.getId());
				if (def == null)
				{
					continue;
				}
				Set<String> ops = options(cache, def);
				TransportCandidate.Category cat = categorise(ops, displayName(cache, def));
				if (cat == null)
				{
					continue;
				}
				int type = l.getType();
				if (type <= 3 && ops.contains("open"))
				{
					continue; // a door or gate: open by default
				}
				int rot = l.getOrientation() & 3;
				int sx = type >= 9 ? def.getSizeX() : 1;
				int sy = type >= 9 ? def.getSizeY() : 1;
				if (rot == 1 || rot == 3)
				{
					int t = sx;
					sx = sy;
					sy = t;
				}
				// the plane the object is used on: one lower on bridge tiles (same rule as collision)
				int lx = l.getPosition().getX() - r.getBaseX();
				int ly = l.getPosition().getY() - r.getBaseY();
				int plane = l.getPosition().getZ();
				if (lx >= 0 && lx < 64 && ly >= 0 && ly < 64 && (r.getTileSetting(1, lx, ly) & 2) != 0)
				{
					plane--;
				}
				if (plane < 0)
				{
					continue;
				}
				out.add(new TransportCandidate(l.getId(), displayName(cache, def), l.getPosition().getX(), l.getPosition().getY(),
					plane, type, rot, sx, sy, new ArrayList<>(ops), cat, def.getVarbitID(), def.getVarpID()));
			}
		});
		return out;
	}

	/** Lower-case menu options of an object and all its alternative forms. */
	public static Set<String> options(LoadedCache cache, ObjectDefinition def)
	{
		Set<String> ops = new LinkedHashSet<>();
		addOps(def, ops);
		int[] forms = def.getConfigChangeDest();
		if (forms != null)
		{
			for (int f : forms)
			{
				ObjectDefinition d = f >= 0 ? cache.object(f) : null;
				if (d != null)
				{
					addOps(d, ops);
				}
			}
		}
		return ops;
	}

	private static void addOps(ObjectDefinition def, Set<String> ops)
	{
		EntityOpsDefinition e = def.getOps();
		if (e == null)
		{
			return;
		}
		for (EntityOpsDefinition.Op op : e.ops)
		{
			if (op != null && op.text != null)
			{
				ops.add(op.text.toLowerCase(Locale.ROOT));
			}
		}
		for (List<EntityOpsDefinition.ConditionalOp> list : e.conditionalOps)
		{
			if (list != null)
			{
				for (EntityOpsDefinition.ConditionalOp op : list)
				{
					if (op != null && op.text != null)
					{
						ops.add(op.text.toLowerCase(Locale.ROOT));
					}
				}
			}
		}
	}

	/** Names of objects that lead to another plane when you "Climb" them. */
	private static final String[] VERTICAL_NAMES = {"ladder", "stair", "steps", "trapdoor", "trap door", "hatch", "manhole"};

	public static boolean verticalName(String name)
	{
		String n = name.toLowerCase(Locale.ROOT);
		for (String v : VERTICAL_NAMES)
		{
			if (n.contains(v))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * A plain "Climb" only means up/down for ladders, stairs, steps and
	 * trapdoors (by name) or when "Climb-up"/"Climb-down" is also offered;
	 * otherwise (rocks, rubble, stones) it is a traversal over the object.
	 */
	static TransportCandidate.Category categorise(Set<String> ops, String name)
	{
		boolean up = any(ops, CLIMB_UP);
		boolean down = any(ops, CLIMB_DOWN);
		boolean plainClimb = any(ops, CLIMB_BOTH);
		if ((up && down) || (plainClimb && (up || down || verticalName(name))))
		{
			return TransportCandidate.Category.CLIMB_BOTH;
		}
		if (up)
		{
			return TransportCandidate.Category.CLIMB_UP;
		}
		if (down)
		{
			return TransportCandidate.Category.CLIMB_DOWN;
		}
		if (plainClimb)
		{
			return TransportCandidate.Category.SHORTCUT; // "Climb" over rocks, rubble, a wall...
		}
		if (any(ops, SHORTCUT))
		{
			return TransportCandidate.Category.SHORTCUT;
		}
		if (any(ops, PASS))
		{
			return TransportCandidate.Category.PASS;
		}
		if (any(ops, ENTER))
		{
			return TransportCandidate.Category.ENTER;
		}
		if (any(ops, EXIT))
		{
			return TransportCandidate.Category.EXIT;
		}
		if (any(ops, TRAVEL))
		{
			return TransportCandidate.Category.TRAVEL;
		}
		return null;
	}

	private static boolean any(Set<String> ops, Set<String> words)
	{
		for (String o : ops)
		{
			if (words.contains(o))
			{
				return true;
			}
		}
		return false;
	}

	/** The object's name, or the first named alternative form's name. */
	public static String displayName(LoadedCache cache, ObjectDefinition def)
	{
		if (!"null".equals(def.getName()))
		{
			return def.getName();
		}
		int[] forms = def.getConfigChangeDest();
		if (forms != null)
		{
			for (int f : forms)
			{
				ObjectDefinition d = f >= 0 ? cache.object(f) : null;
				if (d != null && !"null".equals(d.getName()))
				{
					return d.getName();
				}
			}
		}
		return "(unnamed)";
	}
}
