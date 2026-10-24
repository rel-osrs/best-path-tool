package com.bestpath.core.data;

import com.bestpath.core.path.PathQuery;
import com.bestpath.core.path.Pathfinder;
import com.bestpath.core.path.Route;
import com.bestpath.core.path.RouteDestination;
import com.bestpath.core.path.Tiles;
import com.bestpath.core.path.Transport;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The searches behind {@link NoRouteReasons.Kind#BLOCKED}: every transport is allowed, those the player can't use
 * now cost extra ticks per thing they need, so the way found needs the fewest distinct things the player can't use
 * ({@link #fewestThings}); and the other ways needing as many things ({@link #otherWays}).
 */
final class FewestThings
{
	/** At most this many more searches for other ways (each up to {@value #MAX_ROUNDS} tile searches). */
	static final int MAX_OTHER_SEARCHES = 4;
	/** At most this many searches per place for the way needing the fewest distinct things (see {@link #fewestThings}). */
	static final int MAX_ROUNDS = 3;
	/** The extra ticks are made of these units: {@value NoRouteReasons#BLOCKED_EXTRA} per thing is {@value #THING_UNITS} units. */
	static final int EXTRA_UNIT = NoRouteReasons.BLOCKED_EXTRA / 20;
	private static final int THING_UNITS = NoRouteReasons.BLOCKED_EXTRA / EXTRA_UNIT;
	/** One transport's extra is capped (a transport needing very many things): keeps the search's sums in range. */
	private static final int MAX_EXTRA = 8 * NoRouteReasons.BLOCKED_EXTRA;
	/** {@link #fewestThings}'s {@code banned}: this prefix and a line id leave that transport out. */
	private static final String LINE = "line:";

	private FewestThings()
	{
	}

	/** One place's way: the route found with every transport, the stop, and the number of blocked steps. */
	static final class Way
	{
		final int place;
		final Route route;
		/** The search's transports to their candidates' index. */
		final Map<Transport, Integer> index;
		final int[] stop;
		/** The distinct things the player can't use now ({@link WaySteps#things}), and how many. */
		final Set<String> things;
		final int blocked;
		final int ticks;

		Way(int place, Route route, Map<Transport, Integer> index, int[] stop, Set<String> things, int ticks)
		{
			this.things = things;
			int blocked = things.size();
			this.place = place;
			this.route = route;
			this.index = index;
			this.stop = stop;
			this.blocked = blocked;
			this.ticks = ticks;
		}

		boolean betterThan(Way o)
		{
			return o == null || blocked < o.blocked || blocked == o.blocked && ticks < o.ticks;
		}
	}

	/**
	 * Other ways to the best way's place needing as many things the player can't use, but other things: searched
	 * again with one of a way's things left out ({@link #fewestThings}'s {@code banned}), then for each way found with
	 * one of its things left out too, until {@value NoRouteReasons#MAX_WAYS} ways or {@value #MAX_OTHER_SEARCHES}
	 * searches. Two ways needing the same things are one. Quickest first.
	 */
	static List<Way> otherWays(Way best, Pathfinder pf, PluginData data, List<PlayerTransports.Candidate> cands,
		boolean[] blocked, List<Set<String>> things, Map<String, Integer> shared, int sx, int sy, int sp, int target, int penalty)
	{
		List<Way> out = new ArrayList<>();
		// each transport is shown once: the blocked transports of the ways found are left out of the next searches, so
		// they never find a way that only repeats one (e.g. fairy ring DLR reached from other rings)
		Set<String> shownLines = blockedLines(best, cands, blocked);
		Set<Set<String>> seen = new HashSet<>();
		seen.add(best.things);
		ArrayDeque<Set<String>> queue = new ArrayDeque<>();
		for (String t : best.things)
		{
			queue.add(Set.of(t));
		}
		Set<Set<String>> tried = new HashSet<>();
		int searches = 0;
		while (!queue.isEmpty() && out.size() + 1 < NoRouteReasons.MAX_WAYS && searches < MAX_OTHER_SEARCHES)
		{
			Set<String> banned = queue.poll();
			if (!tried.add(banned))
			{
				continue;
			}
			searches++;
			Set<String> leftOut = new HashSet<>(banned);
			leftOut.addAll(shownLines);
			Way w = fewestThings(pf, data, cands, blocked, things, shared, best.place, sx, sy, sp, target, penalty, leftOut);
			if (w == null || w.blocked != best.blocked || !seen.add(w.things))
			{
				continue;
			}
			out.add(w);
			shownLines.addAll(blockedLines(w, cands, blocked));
			for (String t : w.things)
			{
				Set<String> more = new LinkedHashSet<>(banned);
				more.add(t);
				queue.add(more);
			}
		}
		out.sort((a, b) -> Integer.compare(a.ticks, b.ticks));
		return out;
	}

	/** {@link #LINE} keys of the transports of a way that the player can't use. */
	private static Set<String> blockedLines(Way w, List<PlayerTransports.Candidate> cands, boolean[] blocked)
	{
		Set<String> out = new HashSet<>();
		for (Route.Step s : w.route.getSteps())
		{
			Integer k = s.getTransport() == null ? null : w.index.get(s.getTransport());
			if (k != null && blocked[k])
			{
				out.add(LINE + cands.get(k).getLine().getId());
			}
		}
		return out;
	}

	/**
	 * The way to one place needing the fewest distinct things the player can't use (e.g. the Agility Pyramid's
	 * obstacles all needing Agility 30 are one thing), then the fewest ticks; null when there is none. A cost per
	 * transport can't count distinct things exactly, so up to {@value #MAX_ROUNDS} searches are made, each counting the
	 * things of the way it finds exactly, and the best is kept:
	 * <ol>
	 * <li>{@value NoRouteReasons#BLOCKED_EXTRA} extra ticks per thing each transport the player can't use needs;</li>
	 * <li>a thing many such transports share costs less in each ({@value NoRouteReasons#BLOCKED_EXTRA} divided by how
	 * many need it, up to {@value #THING_UNITS}): ways reusing one thing again and again are found;</li>
	 * <li>the things of the best way so far are free, every other thing costs {@value NoRouteReasons#BLOCKED_EXTRA}
	 * (stopped when that way needs at most one thing, or the second search found the same things).</li>
	 * </ol>
	 * A first way needing at most one thing ends it at once. Among ways with the same number of things the quicker
	 * one is not always found (a heuristic, not exact). {@code banned}: things left out (transports the player can't use
	 * that need one of them are not searched), and {@link #LINE} + a line id: that transport left out (a card another
	 * way shows already), to find another way ({@link #otherWays}).
	 */
	static Way fewestThings(Pathfinder pf, PluginData data, List<PlayerTransports.Candidate> cands, boolean[] blocked,
		List<Set<String>> things, Map<String, Integer> shared, int place, int sx, int sy, int sp, int target, int penalty,
		Set<String> banned)
	{
		int dx = Tiles.x(target);
		int dy = Tiles.y(target);
		int dp = Tiles.plane(target);
		int[] stop = null;
		Way best = null;
		Set<String> seenBefore = null;
		for (int round = 0; round < MAX_ROUNDS; round++)
		{
			Set<String> free = round == 2 ? best.things : Set.of();
			List<Transport> search = new ArrayList<>(cands.size());
			Map<Transport, Integer> index = new IdentityHashMap<>();
			for (int i = 0; i < cands.size(); i++)
			{
				Transport t = cands.get(i).getTransport();
				if (blocked[i] && !banned.isEmpty() && (!Collections.disjoint(things.get(i), banned)
					|| banned.contains(LINE + cands.get(i).getLine().getId())))
				{
					continue; // looking for another way (otherWays): transports needing a left-out thing are not used
				}
				int units = 0;
				if (blocked[i])
				{
					for (String th : things.get(i))
					{
						if (!free.contains(th))
						{
							units += round == 1 ? Math.max(1, THING_UNITS / Math.min(THING_UNITS, shared.getOrDefault(th, 1))) : THING_UNITS;
						}
					}
				}
				int extra = Math.min(units * EXTRA_UNIT, MAX_EXTRA);
				Transport s = extra == 0 ? t : t.withTickChange(t.getTickChange() + extra);
				search.add(s);
				index.put(s, i);
			}
			PathQuery q = new PathQuery(sx, sy, sp, dx, dy, dp, search, penalty, data.getDoors(), PluginRouter.DOOR_COST);
			if (round == 0)
			{
				// the same rule as the route's settled destination (RouteDestination), with every transport
				RouteDestination end = RouteDestination.resolve(pf, null, null, q,
					PluginRouter.NEAR_RANGE, data.getWalls(), dy >= PluginRouter.MAIN_MAP_TOP_Y);
				stop = end == null ? null : end.getStop();
				if (stop == null)
				{
					return null; // nothing near it can be reached, even with every transport
				}
			}
			PathQuery used = stop[0] == dx && stop[1] == dy && stop[2] == dp ? q : q.withDestination(stop[0], stop[1], stop[2]);
			// the extras are whole things (BLOCKED_EXTRA) but in the second search (shared things: units)
			Route r = pf.find(used, NoRouteReasons.SEARCH_LIMIT, round == 1 ? EXTRA_UNIT : NoRouteReasons.BLOCKED_EXTRA);
			if (!r.isFound())
			{
				if (round == 0)
				{
					return null;
				}
				continue;
			}
			Set<String> seen = new LinkedHashSet<>();
			int real = r.getTicks();
			for (Route.Step s : r.getSteps())
			{
				Integer k = s.getTransport() == null ? null : index.get(s.getTransport());
				if (k != null && blocked[k])
				{
					seen.addAll(things.get(k));
					real -= s.getTransport().cost(penalty) - cands.get(k).getTransport().cost(penalty);
				}
			}
			Way w = new Way(place, r, index, stop, seen, real);
			if (w.betterThan(best))
			{
				best = w;
			}
			if (round == 0 && seen.size() <= 1)
			{
				break; // one thing (or none: usable now) can't be beaten on the count; saves the other searches
			}
			if (round == 1 && (best.blocked <= 1 || seen.equals(seenBefore)))
			{
				break;
			}
			seenBefore = seen;
		}
		return best;
	}
}
