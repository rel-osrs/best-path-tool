package com.bestpath.route;

import com.bestpath.core.data.WildernessZones;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.ObjectID;

/**
 * The Wilderness on a route's cards: a "Go into the Wilderness" card where a walk crosses into it
 * ({@link #intoWilderness}), and the skull and red outline on a step that starts or ends there ({@link #inWilderness}).
 */
final class WildernessSteps
{
	private WildernessSteps()
	{
	}

	/** The card where a walk goes into the Wilderness. */
	static final String INTO_WILDERNESS = "Go into the Wilderness";
	/** The Wilderness Ditch (object 23271, option "Cross", 1x2 along y 3521-3522). */
	static final int DITCH_OBJECT = ObjectID.DITCH_WILDERNESS_COVER;

	/**
	 * Every walk that goes from outside the Wilderness into it, split there by a "Go into the Wilderness" card. The
	 * ditch is walked over (it is not a transport), so the walk alone would have no card. The card has the Wilderness
	 * skull and red outline ({@link #inWilderness}); in the game the ditch glows with "Cross" where there is one (where
	 * it has gaps, the text alone shows). Its tick is the walk's step over the line; the route's ticks stay the same.
	 */
	static List<RouteStep> intoWilderness(List<RouteStep> steps, WildernessZones zones)
	{
		if (zones.isEmpty())
		{
			return steps;
		}
		List<RouteStep> out = new ArrayList<>(steps.size() + 2);
		for (RouteStep s : steps)
		{
			int i = s.getKind() == RouteStep.Kind.WALK ? entersWilderness(s, zones) : -1;
			if (i < 0)
			{
				out.add(s);
				continue;
			}
			int n = s.tileCount();
			int from = s.tile(i - 1);
			int into = s.tile(i);
			int before = Math.min(s.getTicks(), i - 1);
			int over = Math.min(s.getTicks() - before, 1);
			if (i - 1 >= 1)
			{
				out.add(walkPart(s, 0, i, before));
			}
			RouteStep card = new RouteStep(RouteStep.Kind.TRAVERSAL, INTO_WILDERNESS, "", over, List.of(), null, new int[0], from, into)
				.withClick(new ClickHint("Cross", "Wilderness Ditch", null, DITCH_OBJECT, -1, new int[0], false, into));
			out.add(inWilderness(card, zones).withGameText(INTO_WILDERNESS));
			if (n - i >= 2)
			{
				out.add(walkPart(s, i, n, s.getTicks() - before - over));
			}
		}
		return out;
	}

	/** The index of the walk's first tile in the Wilderness right after one outside it (same plane), or -1. */
	static int entersWilderness(RouteStep walk, WildernessZones zones)
	{
		for (int i = 1; i < walk.tileCount(); i++)
		{
			int a = walk.tile(i - 1);
			int b = walk.tile(i);
			if (Tiles.plane(a) == Tiles.plane(b) && !zones.isWilderness(Tiles.x(a), Tiles.y(a)) && zones.isWilderness(Tiles.x(b), Tiles.y(b)))
			{
				return i;
			}
		}
		return -1;
	}

	/** The walk's tiles {@code from} (inclusive) to {@code to} (exclusive) as a walk of its own. */
	private static RouteStep walkPart(RouteStep walk, int from, int to, int ticks)
	{
		int[] tiles = new int[to - from];
		for (int k = from; k < to; k++)
		{
			tiles[k - from] = walk.tile(k);
		}
		String title = walk.getTitle();
		return new RouteStep(RouteStep.Kind.WALK, title, (tiles.length - 1) + " tiles", Math.max(0, ticks), walk.getNotes(), null,
			tiles, -1);
	}

	/**
	 * The step with the Wilderness among its needs (a skull and a red card outline) when it starts or ends there: any of
	 * its first tile, the tile where its transport is used, its last tile and where the player is after it. Walks have
	 * no card.
	 */
	static RouteStep inWilderness(RouteStep step, WildernessZones zones)
	{
		if (zones.isEmpty() || step.getKind() == RouteStep.Kind.WALK)
		{
			return step;
		}
		int n = step.tileCount();
		for (int t : new int[]{n > 0 ? step.tile(0) : -1, step.getMarkerTile(), n > 0 ? step.tile(n - 1) : -1, step.getEndTile()})
		{
			if (t != -1 && zones.isWilderness(Tiles.x(t), Tiles.y(t)))
			{
				return step.withInfo(step.getInfo().withWilderness());
			}
		}
		return step;
	}
}
