package com.bestpath.tool.transport;

import com.bestpath.tool.transport.TransportCandidate.Category;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Suggests where each transport candidate leads. Suggestions are ranked and
 * explained, never applied: a person accepts or rejects each one.
 *
 * <p>Rules (patterns seen in the game's layout, not facts):
 * <ul>
 * <li>Climbing up/down: a matching object directly above/below, or within 4 tiles.</li>
 * <li>Surface/underground: dungeons sit about 6,400 tiles north of the surface;
 * a matching object near (x, y +/- 6400) on the same plane.</li>
 * <li>Shortcuts and barriers: over the object to the first walkable tile beyond it.</li>
 * <li>Shortcuts may also change plane: a shortcut-like object on the plane
 * above/below nearby, or else the first walkable tile beyond the object on
 * that plane. Same plane, up and down are all offered; the user picks.</li>
 * </ul>
 */
public final class SuggestionEngine
{
	/** Returns a component id for walkable tiles, or -1. */
	public interface Walkable
	{
		int componentAt(int x, int y, int plane);
	}

	/**
	 * The same walkable tiles minus the excluded sub-regions (the ones hidden in
	 * the tool): a transport never starts or lands there. Closed-off pockets are
	 * only avoided, see {@link #avoiding}.
	 */
	public static Walkable excluding(Walkable walkable, boolean[] excluded)
	{
		return (x, y, plane) ->
		{
			int c = walkable.componentAt(x, y, plane);
			return c >= 0 && c < excluded.length && excluded[c] ? -1 : c;
		};
	}

	/** Dungeons are generally this many tiles north of their surface area. */
	public static final int UNDERGROUND_OFFSET = 6400;
	private static final int STACK_NEAR = 4;
	private static final int UNDERGROUND_RADIUS = 16;
	/** How far beyond an obstacle a traversal may land (tiles). */
	private static final int ACROSS_MAX = 14;

	private static final Set<Category> GOES_UP = EnumSet.of(Category.CLIMB_UP, Category.CLIMB_BOTH);
	private static final Set<Category> GOES_DOWN = EnumSet.of(Category.CLIMB_DOWN, Category.CLIMB_BOTH);

	private final List<TransportCandidate> candidates;
	private final Walkable walkable;
	private final Stepper stepper;
	/** Candidates by (64x64 cell, plane) for nearby searches. */
	private final Map<Integer, List<TransportCandidate>> byCell = new HashMap<>();

	/** Can the player take one step (dx, dy) from (x, y) on a plane, by the movement rules? */
	public interface Stepper
	{
		boolean canStep(int x, int y, int plane, int dx, int dy);
	}

	public SuggestionEngine(List<TransportCandidate> candidates, Walkable walkable, Stepper stepper)
	{
		this.stepper = stepper;
		this.candidates = candidates;
		this.walkable = walkable;
		for (TransportCandidate c : candidates)
		{
			byCell.computeIfAbsent(cell(c.getX(), c.getY(), c.getPlane()), k -> new ArrayList<>()).add(c);
		}
	}

	/** Per component: avoid starting or landing there if any other tile will do (closed-off pockets). */
	private boolean[] avoid = new boolean[0];
	/** True while a rule is first tried without the avoided components. */
	private boolean strict;

	/**
	 * Prefer tiles outside these components (closed-off pockets, such as the
	 * gap behind a staircase). A rule only uses them
	 * when it finds nothing elsewhere: a small room reached only by a ladder is
	 * also a "pocket", and there it is the right place.
	 */
	public SuggestionEngine avoiding(boolean[] components)
	{
		this.avoid = components;
		return this;
	}

	/** The component at a tile for the rules: -1 if blocked, or avoided while {@link #strict}. */
	private int at(int x, int y, int plane)
	{
		int c = walkable.componentAt(x, y, plane);
		return strict && c >= 0 && c < avoid.length && avoid[c] ? -1 : c;
	}

	/** Runs a rule without the avoided components; if it suggests nothing, again with them. */
	private void preferring(List<Suggestion> out, Runnable rule)
	{
		int before = out.size();
		strict = true;
		rule.run();
		strict = false;
		if (out.size() == before)
		{
			rule.run();
		}
	}

	private static int cell(int x, int y, int plane)
	{
		return ((x >> 6) << 8 | (y >> 6)) << 2 | plane;
	}

	/** Suggestions for every candidate, best first per candidate. */
	public Map<TransportCandidate, List<Suggestion>> suggestAll()
	{
		Map<TransportCandidate, List<Suggestion>> out = new LinkedHashMap<>();
		for (TransportCandidate c : candidates)
		{
			out.put(c, suggest(c));
		}
		return out;
	}

	public List<Suggestion> suggest(TransportCandidate c)
	{
		List<Suggestion> s = new ArrayList<>();
		Category cat = c.getCategory();
		if (GOES_UP.contains(cat))
		{
			preferring(s, () -> stacked(c, +1, GOES_DOWN, s));
		}
		if (GOES_DOWN.contains(cat))
		{
			preferring(s, () -> stacked(c, -1, GOES_UP, s));
		}
		boolean underground = c.getY() >= UNDERGROUND_OFFSET;
		if (!underground && (cat == Category.CLIMB_DOWN || cat == Category.CLIMB_BOTH || cat == Category.ENTER))
		{
			preferring(s, () -> farAway(c, +UNDERGROUND_OFFSET, EnumSet.of(Category.CLIMB_UP, Category.CLIMB_BOTH, Category.EXIT), s));
		}
		if (underground && (cat == Category.CLIMB_UP || cat == Category.CLIMB_BOTH || cat == Category.EXIT))
		{
			preferring(s, () -> farAway(c, -UNDERGROUND_OFFSET, EnumSet.of(Category.CLIMB_DOWN, Category.CLIMB_BOTH, Category.ENTER), s));
		}
		if (cat == Category.SHORTCUT || cat == Category.PASS)
		{
			preferring(s, () -> across(c, s));
		}
		if (cat == Category.SHORTCUT)
		{
			// a shortcut may also take you up or down (a wall onto a roof, a gap down to the ground; often more than one plane)
			for (int tp = 0; tp <= 3; tp++)
			{
				if (tp != c.getPlane())
				{
					int plane = tp;
					preferring(s, () -> otherPlane(c, plane, s));
				}
			}
		}
		s.sort(Comparator.comparingInt(Suggestion::getScore).reversed());
		return s;
	}

	/** A matching object one plane up (dz = +1) or down (dz = -1). */
	private void stacked(TransportCandidate c, int dz, Set<Category> partnerKinds, List<Suggestion> out)
	{
		int plane = c.getPlane() + dz;
		if (plane < 0 || plane > 3)
		{
			return;
		}
		for (TransportCandidate p : near(c.getX(), c.getY(), plane, STACK_NEAR + 4))
		{
			if (!partnerKinds.contains(p.getCategory()))
			{
				continue;
			}
			int d = c.distanceTo(p);
			if (d > STACK_NEAR)
			{
				continue;
			}
			int[] land = landing(p, c.getX(), c.getY());
			if (land == null)
			{
				continue;
			}
			List<String> ev = new ArrayList<>();
			ev.add(String.format("'%s' (%s) %s, %s", p.getName(), String.join("/", p.getOptions()),
				dz > 0 ? "on the plane above" : "on the plane below", d == 0 ? "directly over/under it" : d + " tiles away"));
			ev.add("landing tile is walkable");
			int score = d == 0 ? 100 : 80 - d * 3;
			out.add(new Suggestion(land[0], land[1], plane, d == 0 ? Suggestion.Rule.STACKED : Suggestion.Rule.STACKED_NEAR,
				p.id(), ev, score));
		}
	}

	/** A matching object near (x, y + dy) on the same plane (surface/underground). */
	private void farAway(TransportCandidate c, int dy, Set<Category> partnerKinds, List<Suggestion> out)
	{
		int tx = c.getX();
		int ty = c.getY() + dy;
		for (TransportCandidate p : near(tx, ty, c.getPlane(), UNDERGROUND_RADIUS))
		{
			if (!partnerKinds.contains(p.getCategory()))
			{
				continue;
			}
			int d = p.distanceTo(tx, ty);
			if (d > UNDERGROUND_RADIUS)
			{
				continue;
			}
			int[] land = landing(p, tx, ty);
			if (land == null)
			{
				continue;
			}
			List<String> ev = new ArrayList<>();
			ev.add(String.format("'%s' (%s) %d tiles from the spot %s", p.getName(), String.join("/", p.getOptions()), d,
				dy > 0 ? "6,400 tiles north (underground)" : "6,400 tiles south (surface)"));
			ev.add("landing tile is walkable");
			out.add(new Suggestion(land[0], land[1], c.getPlane(), Suggestion.Rule.UNDERGROUND, p.id(), ev, 70 - d * 2));
		}
	}

	private static final int[][] DIRS8 = {{1, 0}, {0, 1}, {1, 1}, {1, -1}, {-1, 0}, {0, -1}, {-1, -1}, {-1, 1}};

	/**
	 * Traversals (shortcuts, barriers, "Climb" over rocks): in each of 8
	 * directions, start on a walkable tile just before the object, cross the
	 * object and anything blocked after it (water, more stepping stones, a
	 * wall), and land on the first walkable tile that can NOT simply be walked
	 * to in a straight line. Mirror-image directions describe the same
	 * crossing, so only the better of each pair is kept; the best two
	 * crossings are suggested.
	 */
	private void across(TransportCandidate c, List<Suggestion> out)
	{
		int plane = c.getPlane();
		int cx = c.getX() + (c.getSizeX() - 1) / 2;
		int cy = c.getY() + (c.getSizeY() - 1) / 2;
		Map<Integer, Suggestion> bestPerAxis = new HashMap<>();
		for (int[] d : DIRS8)
		{
			int dx = d[0];
			int dy = d[1];
			int[] start = startTile(c, dx, dy);
			if (start == null)
			{
				continue;
			}
			// far side: leave the object forwards, then scan for the landing tile
			int[] t = beyond(c, cx, cy, dx, dy);
			int blocked = 0;
			for (int k = 0; k <= ACROSS_MAX; k++)
			{
				int x = t[0] + k * dx;
				int y = t[1] + k * dy;
				int comp = at(x, y, plane);
				if (comp < 0)
				{
					blocked++;
					continue;
				}
				if (straightWalk(start, x, y, dx, dy))
				{
					break; // nothing to cross in this direction
				}
				int startComp = at(start[0], start[1], plane);
				int span = Math.max(Math.abs(x - start[0]), Math.abs(y - start[1]));
				List<String> ev = new ArrayList<>();
				ev.add(String.format("start %d, %d (just before the object), land %d, %d: %d tiles across",
					start[0], start[1], x, y, span));
				ev.add(blocked > 0 ? "crosses " + blocked + " blocked tile(s) (water, rocks, more of the obstacle)"
					: "crosses a wall or the object itself");
				ev.add(startComp != comp ? "lands in a different sub-region" : "same sub-region, but no straight walk");
				// straight crossings are preferred over diagonal ones when both work
				int score = 60 + (startComp != comp ? 15 : 0) - span - (dx != 0 && dy != 0 ? 10 : 0);
				Suggestion s = new Suggestion(start, x, y, plane, Suggestion.Rule.ACROSS, null, ev, score);
				int axis = axisKey(dx, dy);
				Suggestion prev = bestPerAxis.get(axis);
				if (prev == null || s.getScore() > prev.getScore())
				{
					bestPerAxis.put(axis, s);
				}
				break;
			}
		}
		List<Suggestion> found = new ArrayList<>(bestPerAxis.values());
		found.sort(Comparator.comparingInt(Suggestion::getScore).reversed());
		out.addAll(found.subList(0, Math.min(2, found.size())));
	}

	/** Near side of a crossing in direction (dx, dy): the first walkable tile (within 2) behind the object, or null. */
	private int[] startTile(TransportCandidate c, int dx, int dy)
	{
		if (dx == 0 && dy == 0)
		{
			return null; // no direction: the player stands next to the object
		}
		int plane = c.getPlane();
		int sx = c.getX() + (c.getSizeX() - 1) / 2;
		int sy = c.getY() + (c.getSizeY() - 1) / 2;
		while (inside(c, sx, sy))
		{
			sx -= dx;
			sy -= dy;
		}
		for (int k = 0; k < 2; k++)
		{
			if (at(sx - k * dx, sy - k * dy, plane) >= 0)
			{
				return new int[]{sx - k * dx, sy - k * dy, plane};
			}
		}
		return null;
	}

	/** How near the next shortcut on a higher plane must be (tiles). */
	private static final int PARTNER_NEAR = 6;

	/**
	 * A shortcut to another plane {@code tp} (rooftop courses climb from
	 * plane 0 straight to plane 3 and jump back down). Stronger evidence: a
	 * shortcut on a higher plane within a few tiles (the next
	 * obstacle). Weaker: the first walkable tile on that plane beyond the
	 * object, in the best direction. At most one suggestion per plane.
	 */
	private void otherPlane(TransportCandidate c, int tp, List<Suggestion> out)
	{
		int dz = tp - c.getPlane();
		int cx = c.getX() + (c.getSizeX() - 1) / 2;
		int cy = c.getY() + (c.getSizeY() - 1) / 2;
		// only upwards: a shortcut on the plane above is the next obstacle (a wall climbed onto a roof);
		// the reverse (that roof obstacle "leading down" to the wall) is not evidence of anything
		Suggestion best = dz > 0 ? planePartner(c, tp, cx, cy) : null;
		if (best == null)
		{
			best = firstTileBeyond(c, tp, cx, cy);
		}
		if (best != null)
		{
			out.add(best);
		}
	}

	/** The best landing next to a shortcut on the higher plane {@code tp} within {@link #PARTNER_NEAR} tiles, or null. */
	private Suggestion planePartner(TransportCandidate c, int tp, int cx, int cy)
	{
		int dz = tp - c.getPlane();
		Suggestion best = null;
		for (TransportCandidate p : near(cx, cy, tp, PARTNER_NEAR + 4))
		{
			int d = c.distanceTo(p);
			if (p.getCategory() != Category.SHORTCUT || d > PARTNER_NEAR)
			{
				continue;
			}
			int[] land = landing(p, cx, cy);
			if (land == null)
			{
				continue;
			}
			int[] start = startTile(c, Integer.signum(land[0] - cx), Integer.signum(land[1] - cy));
			List<String> ev = new ArrayList<>();
			ev.add(String.format("'%s' (%s) on %s, %d tiles away: likely the other end",
				p.getName(), String.join("/", p.getOptions()), "plane " + tp, d));
			ev.add("landing tile is walkable");
			Suggestion s = new Suggestion(start, land[0], land[1], tp, Suggestion.Rule.PLANE_PARTNER, p.id(), ev,
				80 - d * 3 - (dz - 1));
			if (best == null || s.getScore() > best.getScore())
			{
				best = s;
			}
		}
		return best;
	}

	/** The best first walkable tile on plane {@code tp} beyond the object, over the 8 directions, or null. */
	private Suggestion firstTileBeyond(TransportCandidate c, int tp, int cx, int cy)
	{
		int dz = tp - c.getPlane();
		String where = "plane " + tp;
		Suggestion best = null;
		for (int[] d : DIRS8)
		{
			int dx = d[0];
			int dy = d[1];
			int[] start = startTile(c, dx, dy);
			if (start == null)
			{
				continue;
			}
			int[] t = beyond(c, cx, cy, dx, dy);
			// up: from the object's last tile (a climb onto it); down: from beside it, never straight under it
			for (int k = dz > 0 ? -1 : 0; k <= ACROSS_MAX; k++)
			{
				int x = t[0] + k * dx;
				int y = t[1] + k * dy;
				if (at(x, y, tp) < 0)
				{
					continue;
				}
				int span = Math.max(Math.abs(x - start[0]), Math.abs(y - start[1]));
				List<String> ev = new ArrayList<>();
				ev.add(String.format("start %d, %d, land %d, %d on %s: the first walkable tile there beyond the object",
					start[0], start[1], x, y, where));
				ev.add("no matching object on " + where + ": weaker evidence");
				int score = (dz > 0 ? 50 : 38) - 3 * (Math.abs(dz) - 1) - span - (dx != 0 && dy != 0 ? 10 : 0);
				Suggestion s = new Suggestion(start, x, y, tp, Suggestion.Rule.OTHER_PLANE, null, ev, score);
				if (best == null || s.getScore() > best.getScore())
				{
					best = s;
				}
				break;
			}
		}
		return best;
	}

	/** The first tile outside the object going from (cx, cy) in steps (dx, dy). */
	private static int[] beyond(TransportCandidate c, int cx, int cy, int dx, int dy)
	{
		int tx = cx;
		int ty = cy;
		while (inside(c, tx, ty))
		{
			tx += dx;
			ty += dy;
		}
		return new int[]{tx, ty};
	}

	/** Same key for a direction and its mirror image. */
	private static int axisKey(int dx, int dy)
	{
		if (dx < 0 || (dx == 0 && dy < 0))
		{
			dx = -dx;
			dy = -dy;
		}
		return (dx + 1) * 3 + (dy + 1);
	}

	private static boolean inside(TransportCandidate c, int x, int y)
	{
		return x >= c.getX() && x < c.getX() + c.getSizeX() && y >= c.getY() && y < c.getY() + c.getSizeY();
	}

	/** Can you walk from start to (x, y) by repeated steps (dx, dy)? */
	private boolean straightWalk(int[] start, int x, int y, int dx, int dy)
	{
		int px = start[0];
		int py = start[1];
		for (int i = 0; i < ACROSS_MAX + 8 && (px != x || py != y); i++)
		{
			if (!stepper.canStep(px, py, start[2], dx, dy))
			{
				return false;
			}
			px += dx;
			py += dy;
		}
		return px == x && py == y;
	}

	/** Walkable tile on or next to the object, nearest to (px, py); null if none. */
	private int[] landing(TransportCandidate p, int px, int py)
	{
		int[] best = null;
		int bestD = Integer.MAX_VALUE;
		for (int x = p.getX() - 1; x <= p.getX() + p.getSizeX(); x++)
		{
			for (int y = p.getY() - 1; y <= p.getY() + p.getSizeY(); y++)
			{
				if (at(x, y, p.getPlane()) < 0)
				{
					continue;
				}
				int d = Math.max(Math.abs(x - px), Math.abs(y - py));
				if (d < bestD)
				{
					bestD = d;
					best = new int[]{x, y};
				}
			}
		}
		return best;
	}

	private List<TransportCandidate> near(int x, int y, int plane, int radius)
	{
		List<TransportCandidate> out = new ArrayList<>();
		for (int cx = (x - radius) >> 6; cx <= (x + radius) >> 6; cx++)
		{
			for (int cy = (y - radius) >> 6; cy <= (y + radius) >> 6; cy++)
			{
				out.addAll(byCell.getOrDefault((cx << 8 | cy) << 2 | plane, Collections.emptyList()));
			}
		}
		return out;
	}
}
