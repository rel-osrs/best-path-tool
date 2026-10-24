package com.bestpath.core.sea;

import com.bestpath.core.path.RadixHeap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REFERENCE COPY (tests only): the SeaRouter as it was before the compact forms, kept so the tests can
 * check the compact form gives exactly the same answers on hand-made seas. Not used by the tool or the plugin.
 *
 * <p>One boat's search over the {@link ReferenceSeaGraph}: from several start points at
 * once (each with its own starting cost, e.g. the ticks to walk to a dock),
 * to several end points. The state is a waypoint and the boat's heading, so
 * turning can cost extra (turning is punished).
 *
 * <p>Costs are in hundredths of a tick: a link takes its length / the boat's
 * speed; a link through tight water (below the boat's preferred clearance)
 * counts {@link #TIGHT_FACTOR} times; each 22.5-degree step of turning adds
 * the turn cost. Links the boat may not use (an area it may not enter,
 * narrower than its hard clearance outside harbours) are skipped. Besides
 * the cost the search keeps the real sailing time (length / speed, no extras)
 * for the route's ticks.
 *
 * <p>Not thread-safe; the work arrays are kept between searches.
 */
public final class ReferenceSeaRouter
{
	public static final int TIGHT_FACTOR = 3;
	/** The heading "none yet" (a start point: the first link turns for free). */
	private static final int ANY = ReferenceSeaGraph.HEADINGS;
	private static final int STATES = ReferenceSeaGraph.HEADINGS + 1;

	private final ReferenceSeaGraph g;
	private final int[] cost;
	/** Real length sailed so far, hundredths of a tile. */
	private final int[] real;
	private final int[] parent;
	private final int[] stamp;
	private int gen;

	public ReferenceSeaRouter(ReferenceSeaGraph g)
	{
		this.g = g;
		int n = g.waypointCount() * STATES;
		cost = new int[n];
		real = new int[n];
		parent = new int[n];
		stamp = new int[n];
	}

	public ReferenceSeaGraph getGraph()
	{
		return g;
	}

	/** What the search needs to know about the boat. */
	public static final class Rules
	{
		final int hard;
		final int preferred;
		final int areaMask;
		final double speed;
		final int turnCost;

		/** @param turnCost hundredths of a tick per 22.5 degrees of turning */
		public Rules(Boat b, int turnCost)
		{
			this.hard = b.getType().hardClearance();
			this.preferred = b.getType().preferredClearance();
			this.areaMask = b.areaMask();
			this.speed = b.speed();
			this.turnCost = turnCost;
		}

		boolean allowed(int clearance, int areas, boolean harbour)
		{
			return (areas & ~areaMask) == 0 && (clearance >= hard || harbour);
		}

		/** Time of a stretch (hundredths of a tick, rounded). */
		int time(double tiles)
		{
			return (int) Math.round(tiles * 100 / speed);
		}

		static int hundredths(double tiles)
		{
			return (int) Math.round(tiles * 100);
		}

		int cost(double tiles, int clearance)
		{
			return time(tiles) * (clearance < preferred ? TIGHT_FACTOR : 1);
		}
	}

	/** A start: joins into the graph, and the cost already spent before sailing (hundredths of a tick). */
	public static final class Start
	{
		final List<ReferenceSeaGraph.Join> joins;
		final int cost;

		public Start(List<ReferenceSeaGraph.Join> joins, int cost)
		{
			this.joins = joins;
			this.cost = cost;
		}
	}

	/** The result of {@link #search}: per end point the best cost, the real time, and where it came from. */
	public final class Result
	{
		private final int[] endCost;
		private final int[] endReal;
		private final int[] endState;
		private final ReferenceSeaGraph.Join[] endJoin;
		private final Map<Integer, Integer> startOfState;
		private final Map<Integer, ReferenceSeaGraph.Join> joinOfState;
		private final int search;
		private final double rulesSpeed;

		Result(int ends, Map<Integer, Integer> startOfState, Map<Integer, ReferenceSeaGraph.Join> joinOfState, int search, double speed)
		{
			this.rulesSpeed = speed;
			endCost = new int[ends];
			endReal = new int[ends];
			endState = new int[ends];
			endJoin = new ReferenceSeaGraph.Join[ends];
			Arrays.fill(endCost, -1);
			this.startOfState = startOfState;
			this.joinOfState = joinOfState;
			this.search = search;
		}

		/** Best cost to the end point (start cost included), or -1. */
		public int cost(int end)
		{
			return endCost[end];
		}

		/** Real sailing time to the end point (without the start cost and extras): length / speed, hundredths of a tick. */
		public int realTime(int end)
		{
			return (int) Math.ceil(endReal[end] / rulesSpeed);
		}

		/** Which start the best way to the end point began at. */
		public int startOf(int end)
		{
			return startOfState.get(first(end));
		}

		private int first(int end)
		{
			int st = endState[end];
			while (parent[st] >= 0)
			{
				st = parent[st];
			}
			return st;
		}

		/**
		 * The tiles the boat's centre passes (packed x << 14 | y) from the start
		 * to the end point. Valid until the next search.
		 */
		public int[] tiles(int end)
		{
			if (search != gen)
			{
				throw new IllegalStateException("another search ran since");
			}
			List<Integer> states = new ArrayList<>();
			for (int st = endState[end]; st >= 0; st = parent[st])
			{
				states.add(0, st);
			}
			List<int[]> parts = new ArrayList<>();
			ReferenceSeaGraph.Join in = joinOfState.get(states.get(0));
			int w0 = states.get(0) / STATES;
			parts.add(g.lineTiles(in.fromX, in.fromY, g.x(w0), g.y(w0)));
			for (int k = 1; k < states.size(); k++)
			{
				int a = states.get(k - 1) / STATES;
				int b = states.get(k) / STATES;
				parts.add(g.lineTiles(g.x(a), g.y(a), g.x(b), g.y(b)));
			}
			ReferenceSeaGraph.Join out = endJoin[end];
			int wl = states.get(states.size() - 1) / STATES;
			parts.add(g.lineTiles(g.x(wl), g.y(wl), out.fromX, out.fromY));
			List<Integer> all = new ArrayList<>();
			for (int[] p : parts)
			{
				for (int t : p)
				{
					if (all.isEmpty() || all.get(all.size() - 1) != t)
					{
						all.add(t);
					}
				}
			}
			return all.stream().mapToInt(Integer::intValue).toArray();
		}
	}

	/**
	 * From the starts to the ends (each end: its joins into the graph). Costs
	 * above {@code bound} (hundredths of a tick) are not searched.
	 */
	public Result search(Rules r, List<Start> starts, List<List<ReferenceSeaGraph.Join>> ends, int bound)
	{
		gen++;
		if (gen == Integer.MAX_VALUE)
		{
			Arrays.fill(stamp, 0);
			gen = 1;
		}
		Map<Integer, Integer> startOfState = new HashMap<>();
		Map<Integer, ReferenceSeaGraph.Join> joinOfState = new HashMap<>();
		Result res = new Result(ends.size(), startOfState, joinOfState, gen, r.speed);
		// end points by waypoint
		Map<Integer, List<int[]>> endsAt = new HashMap<>(); // {end, join index}
		for (int e = 0; e < ends.size(); e++)
		{
			for (int j = 0; j < ends.get(e).size(); j++)
			{
				ReferenceSeaGraph.Join jn = ends.get(e).get(j);
				if (r.allowed(jn.clearance, jn.areas, jn.harbour))
				{
					endsAt.computeIfAbsent(jn.waypoint, k -> new ArrayList<>()).add(new int[]{e, j});
				}
			}
		}
		RadixHeap heap = new RadixHeap();
		for (int s = 0; s < starts.size(); s++)
		{
			Start st = starts.get(s);
			for (ReferenceSeaGraph.Join jn : st.joins)
			{
				if (!r.allowed(jn.clearance, jn.areas, jn.harbour))
				{
					continue;
				}
				int state = jn.waypoint * STATES + ANY;
				int c = st.cost + r.cost(jn.length, jn.clearance);
				if (c <= bound && (stamp[state] != gen || c < cost[state]))
				{
					stamp[state] = gen;
					cost[state] = c;
					real[state] = Rules.hundredths(jn.length);
					parent[state] = -1;
					startOfState.put(state, s);
					joinOfState.put(state, jn);
					heap.push((long) c << 32 | state);
				}
			}
		}
		while (!heap.isEmpty())
		{
			long key = heap.pop();
			int state = (int) key;
			int c = (int) (key >>> 32);
			if (c != cost[state])
			{
				continue;
			}
			int w = state / STATES;
			int h = state % STATES;
			List<int[]> here = endsAt.get(w);
			if (here != null)
			{
				for (int[] ej : here)
				{
					ReferenceSeaGraph.Join jn = ends.get(ej[0]).get(ej[1]);
					int ec = c + r.cost(jn.length, jn.clearance);
					if (res.endCost[ej[0]] < 0 || ec < res.endCost[ej[0]])
					{
						res.endCost[ej[0]] = ec;
						res.endReal[ej[0]] = real[state] + Rules.hundredths(jn.length);
						res.endState[ej[0]] = state;
						res.endJoin[ej[0]] = jn;
					}
				}
			}
			for (int d = 0; d < ReferenceSeaGraph.HEADINGS; d++)
			{
				if (!g.hasLink(w, d) || !r.allowed(g.linkClearance(w, d), g.linkAreas(w, d), g.linkHarbour(w, d)))
				{
					continue;
				}
				double len = ReferenceSeaGraph.linkLength(d);
				int turn = h == ANY ? 0 : Math.min(Math.abs(d - h), ReferenceSeaGraph.HEADINGS - Math.abs(d - h));
				int nc = c + r.cost(len, g.linkClearance(w, d)) + turn * r.turnCost;
				if (nc > bound)
				{
					continue;
				}
				int ns = g.linkTarget(w, d) * STATES + d;
				if (stamp[ns] != gen || nc < cost[ns])
				{
					stamp[ns] = gen;
					cost[ns] = nc;
					real[ns] = real[state] + Rules.hundredths(len);
					parent[ns] = state;
					heap.push((long) nc << 32 | ns);
				}
			}
		}
		return res;
	}
}
