package com.bestpath.core.data;

import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.RouteDestination;
import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The quickest of several places on foot with what the player carries ({@link PluginRouter#routeNearest}), each place
 * to its settled destination ({@link RouteSetup#destination}). A coarse search per place is slow with many places
 * (about a second for 403 bank spots). Instead the reachable place nearest as the crow flies is searched first; then
 * one search out from the player, up to that route's ticks, gives every place's exact ticks within it
 * ({@link Pathfinder#ticksTo}); the quickest of them are searched last. Same answer as a route to every place.
 * One search, used once.
 */
final class NearestOnFoot
{
	private final PluginRouter router;
	private final RouteSetup s;
	private final Pathfinder pf;
	private final int sx;
	private final int sy;
	private final int sp;
	/** Each place's settled destination; null: nothing near it can be reached. */
	private final RouteDestination[] ends;
	private final int maxSearches;
	/** {distance as the crow flies, index} of the places with a stop tile. */
	private final List<long[]> near = new ArrayList<>();
	/** Per place: the query to its stop tile, or null. */
	private final PathQuery[] used;
	private int[] firstStop;
	private Route best;
	private int bestIndex = -1;
	private int searched;
	/** Only a "no way" the coarse graph proves is left out of the places that can be reached. */
	private int reachable;
	private boolean capped;

	private NearestOnFoot(PluginRouter router, RouteSetup s, int sx, int sy, int sp, RouteDestination[] ends, int maxSearches)
	{
		this.router = router;
		this.s = s;
		this.pf = s.pf;
		this.sx = sx;
		this.sy = sy;
		this.sp = sp;
		this.ends = ends;
		this.maxSearches = maxSearches;
		this.used = new PathQuery[ends.length];
	}

	/** The quickest place on foot (see the class comment). */
	static PluginRouter.NearestAnswer search(PluginRouter router, RouteSetup s, int sx, int sy, int sp, RouteDestination[] ends,
		int maxSearches)
	{
		NearestOnFoot n = new NearestOnFoot(router, s, sx, sy, sp, ends, maxSearches);
		n.queries();
		n.nearestFirst();
		if (n.best != null)
		{
			n.quickerPlaces();
		}
		return n.answer();
	}

	/** A query per place that has a stop tile, and the places nearest as the crow flies first. */
	private void queries()
	{
		PluginData data = router.getData();
		for (int i = 0; i < ends.length; i++)
		{
			// on foot: a tile only a boat may bring the player to counts as its fallback
			RouteDestination e = ends[i] == null ? null : ends[i].onFoot();
			if (e == null)
			{
				continue;
			}
			int[] stop = e.getStop();
			firstStop = firstStop == null ? stop : firstStop;
			used[i] = new PathQuery(sx, sy, sp, stop[0], stop[1], stop[2], s.allowed, PluginRouter.PLANE_CHANGE_PENALTY,
				data.getDoors(), PluginRouter.DOOR_COST).withClosed(s.closed).withLimits(s.limits);
			near.add(new long[]{Math.max(Math.abs(stop[0] - sx), Math.abs(stop[1] - sy)) + (stop[2] == sp ? 0 : 1), i});
		}
		near.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
	}

	/** The nearest place as the crow flies that a route reaches: the first route found. */
	private void nearestFirst()
	{
		reachable = near.size();
		long t0 = System.nanoTime();
		for (long[] o : near)
		{
			if (searched >= maxSearches)
			{
				capped = true;
				break;
			}
			int i = (int) o[1];
			if (pf.lowerBounds(used[i])[0] < 0)
			{
				reachable--; // the coarse graph proves there is no way
				continue;
			}
			Route r = pf.find(used[i]);
			searched++;
			if (r.isFound())
			{
				best = r;
				bestIndex = i;
				break;
			}
		}
		router.addTime("Walking search (nearest place)", System.nanoTime() - t0);
	}

	/** Every place's exact ticks within the route found, by one search out from the player; the quicker ones searched. */
	private void quickerPlaces()
	{
		long tt = System.nanoTime();
		int[] stops = new int[ends.length];
		Arrays.fill(stops, -1);
		List<Integer> idx = new ArrayList<>();
		for (int i = 0; i < ends.length; i++)
		{
			if (used[i] != null)
			{
				stops[i] = Tiles.pack(used[i].getDestX(), used[i].getDestY(), used[i].getDestPlane());
				idx.add(i);
			}
		}
		int[] tiles = new int[idx.size()];
		for (int k = 0; k < tiles.length; k++)
		{
			tiles[k] = stops[idx.get(k)];
		}
		int[] ticks = pf.ticksTo(used[bestIndex], tiles, best.getTicks());
		router.addTime("Every place's ticks (nearest place)", System.nanoTime() - tt);
		List<long[]> order = new ArrayList<>(); // {ticks, index}
		for (int k = 0; k < tiles.length; k++)
		{
			if (ticks[k] >= 0 && idx.get(k) != bestIndex)
			{
				order.add(new long[]{ticks[k], idx.get(k)});
			}
		}
		order.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
		long t0 = System.nanoTime();
		for (long[] o : order)
		{
			int i = (int) o[1];
			if (o[0] > best.getTicks() || (o[0] == best.getTicks() && i > bestIndex))
			{
				break; // this and every later one can't be quicker (an equal one only from earlier in the list)
			}
			if (searched >= maxSearches)
			{
				capped = true;
				break;
			}
			Route r = pf.find(used[i]);
			searched++;
			if (r.isFound() && (r.getTicks() < best.getTicks() || (r.getTicks() == best.getTicks() && i < bestIndex)))
			{
				best = r;
				bestIndex = i;
			}
		}
		router.addTime("Walking search (nearest place)", System.nanoTime() - t0);
	}

	private PluginRouter.NearestAnswer answer()
	{
		if (best == null)
		{
			return new PluginRouter.NearestAnswer(new PluginRouter.Answer(Route.notFound(0, 0), firstStop, s.usable, !s.member), -1,
				searched, reachable, capped);
		}
		RouteDestination e = ends[bestIndex].onFoot();
		return new PluginRouter.NearestAnswer(new PluginRouter.Answer(best, e.getStop(), s.usable, !s.member), bestIndex,
			searched, reachable, capped);
	}
}
