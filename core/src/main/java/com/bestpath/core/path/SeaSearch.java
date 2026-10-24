package com.bestpath.core.path;

import com.bestpath.core.sea.SeaRouter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One boat's sea search in {@link SailingSearch#route}, through boat-only transports too: per end its least cost, the boarding
 * it starts at and its way. The search is done in stages, which is exact: first from the boardings to the ends and
 * to the start of every boat-only transport; then, quickest first, from the end of each transport reached (its cost
 * so far plus the transport's ticks) to the same ends and transports (a Dijkstra over the transports: a cost never
 * drops on the way). Each stage's search ({@link SeaRouter}) sees only the ends in the graph piece it starts in.
 */
final class SeaSearch
{
	/** The real ends (the search's own ends come first, then one per transport's start). */
	final int n;
	final List<SeaLink> links;
	final List<SeaBoarding> used;
	final int[] cost;
	final int[] stageOf;
	final int[] fromOf;
	final List<SeaRouter.Result> stages = new ArrayList<>();
	/** Per stage: the transport it starts from the end of (-1: from the boardings). */
	final List<Integer> stageLink = new ArrayList<>();
	final long[] exitCost;
	final int[] exitStage;
	final int[] origin;
	final boolean[] done;

	SeaSearch(int n, List<SeaLink> links, List<SeaBoarding> used)
	{
		this.n = n;
		this.links = links;
		this.used = used;
		cost = new int[n];
		stageOf = new int[n];
		fromOf = new int[n];
		Arrays.fill(cost, -1);
		exitCost = new long[links.size()];
		Arrays.fill(exitCost, Long.MAX_VALUE);
		exitStage = new int[links.size()];
		origin = new int[links.size()];
		done = new boolean[links.size()];
	}

	/** No start point at all. */
	boolean none()
	{
		return stages.isEmpty();
	}

	/** Best cost to the end (hundredths, start cost included), or -1. */
	int cost(int end)
	{
		return cost[end];
	}

	/** The boarding the best way to the end starts at. */
	SeaBoarding from(int end)
	{
		return used.get(fromOf[end]);
	}

	void record(int link, SeaRouter.Result r)
	{
		int st = stages.size();
		stages.add(r);
		stageLink.add(link);
		for (int e = 0; e < n; e++)
		{
			int c = r.cost(e);
			if (c >= 0 && (cost[e] < 0 || c < cost[e]))
			{
				cost[e] = c;
				stageOf[e] = st;
				fromOf[e] = link < 0 ? r.startOf(e) : origin[link];
			}
		}
		for (int l = 0; l < links.size(); l++)
		{
			int c = done[l] ? -1 : r.cost(n + l);
			if (c < 0)
			{
				continue;
			}
			long ec = (long) c + links.get(l).cost;
			if (ec < exitCost[l])
			{
				exitCost[l] = ec;
				exitStage[l] = st;
				origin[l] = link < 0 ? r.startOf(n + l) : origin[link];
			}
		}
	}

	/** The transport to search on from next: the quickest reached and not searched from, within the bound; or -1. */
	int next(long bound)
	{
		int best = -1;
		for (int l = 0; l < links.size(); l++)
		{
			if (!done[l] && exitCost[l] <= bound && (best < 0 || exitCost[l] < exitCost[best]))
			{
				best = l;
			}
		}
		return best;
	}

	/** The way to the end: the course of each stretch of water, and the transports between them. */
	void pieces(int end, List<SeaRouter.Course> courses, List<SeaLink> through)
	{
		int st = stageOf[end];
		int e = end;
		while (true)
		{
			courses.add(0, stages.get(st).course(e));
			int l = stageLink.get(st);
			if (l < 0)
			{
				return;
			}
			through.add(0, links.get(l));
			e = n + l;
			st = exitStage[l];
		}
	}
}
