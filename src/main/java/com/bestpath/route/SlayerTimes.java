package com.bestpath.route;

import com.bestpath.core.path.Tiles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Travel times to the Slayer places ("Travel time", "Fastest first"): one search per place a route can reach, on the
 * route worker, from where the player stood when the task showed (or when "Update times" was pressed). At most
 * {@link #MAX_TIMED} places, so the worker stays free for the player's own routes: the player's preferred places
 * first, then the nearest as the crow flies. The current route is not touched. The times are kept for the task (its
 * {@link SlayerTask#identity}: a kill doesn't time them again) until another task shows or the player asks again.
 * Thread-safe: the client thread asks, the worker answers.
 */
public final class SlayerTimes
{
	public static final int MAX_TIMED = 12;

	private String identity;
	private Map<String, Integer> times = Map.of();

	/** The task with its travel times when they are known for it, else the task as it is. */
	public synchronized SlayerTask apply(SlayerTask t)
	{
		return t.getState() == SlayerTask.State.TASK && t.identity().equals(identity) && !times.isEmpty() ? t.withTimes(times) : t;
	}

	/** Whether this task's places need timing: a task with somewhere to go, not timed (or being timed) already. */
	public synchronized boolean needs(SlayerTask t)
	{
		return t.getState() == SlayerTask.State.TASK && t.nearest() != null && !t.identity().equals(identity);
	}

	/** Forgets the times ("Update times"; new map data). */
	public synchronized void forget()
	{
		identity = null;
		times = Map.of();
	}

	/**
	 * Client thread: times the task's places on the route worker from this player snapshot; {@code done} runs on the
	 * worker once the times are known (not when another task was timed meanwhile).
	 */
	public void start(SlayerTask t, RouteService service, PlayerSnapshot player, TransportSettings settings, Runnable done)
	{
		String id = t.identity();
		synchronized (this)
		{
			identity = id;
			times = Map.of();
		}
		service.runOnWorker(() ->
		{
			Map<String, Integer> found = work(t, player, settings, service::searchQuietly);
			synchronized (this)
			{
				if (!id.equals(identity))
				{
					return;
				}
				times = found;
			}
			done.run();
		});
	}

	/**
	 * Worker: the ticks to each of the first {@link #MAX_TIMED} places a route can reach, or
	 * {@link SlayerTask#NO_ROUTE}.
	 */
	public static Map<String, Integer> work(SlayerTask t, PlayerSnapshot player, TransportSettings settings,
		Function<RouteJob, RouteResult> search)
	{
		List<SlayerTask.Location> go = new ArrayList<>();
		for (SlayerTask.Location l : t.getLocations())
		{
			if (l.canGo())
			{
				go.add(l);
			}
		}
		// the player's preferred places first, so they are timed even when further away
		go.sort(Comparator.comparingInt((SlayerTask.Location l) -> l.isPreferred() ? 0 : 1).thenComparingInt(l -> crowFlies(l, player)));
		Map<String, Integer> out = new LinkedHashMap<>();
		for (SlayerTask.Location l : go.subList(0, Math.min(MAX_TIMED, go.size())))
		{
			RouteResult r = search.apply(new RouteJob(0, t.request(l), player, settings, Set.of()));
			out.put(l.key(), r.getStatus() == RouteStatus.READY ? r.getTotalTicks() : SlayerTask.NO_ROUTE);
		}
		return out;
	}

	/** Tiles from the player to the place's nearest point, as the crow flies (planes ignored). */
	private static int crowFlies(SlayerTask.Location l, PlayerSnapshot p)
	{
		int best = Integer.MAX_VALUE;
		for (int t : l.getPoints())
		{
			best = Math.min(best, Math.max(Math.abs(Tiles.x(t) - p.getX()), Math.abs(Tiles.y(t) - p.getY())));
		}
		return best;
	}
}
